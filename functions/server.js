const express = require("express");
const admin = require("firebase-admin");

const serviceAccount = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
if (!serviceAccount) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is required (set it as a Render secret)");
admin.initializeApp({credential: admin.credential.cert(JSON.parse(serviceAccount))});
const db = admin.firestore();
const app = express();
app.use(express.json({limit: "256kb"}));
const port = Number(process.env.PORT || 3000);
const allowedOrigins = (process.env.ALLOWED_ORIGINS || "").split(",").map(x => x.trim()).filter(Boolean);
app.use((req, res, next) => {
  const origin = req.get("origin");
  if (origin && allowedOrigins.includes(origin)) res.set("Access-Control-Allow-Origin", origin);
  res.set("Vary", "Origin");
  res.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

function httpError(status, message) { const e = new Error(message); e.status = status; return e; }
function str(v) { return typeof v === "string" ? v.trim() : ""; }
function num(v, fallback = 0) { return typeof v === "number" && Number.isFinite(v) ? v : fallback; }
function millis(v) { return v && typeof v.toMillis === "function" ? v.toMillis() : 0; }
async function auth(req, roles = []) {
  const match = /^Bearer\s+(.+)$/i.exec(req.get("authorization") || "");
  if (!match) throw httpError(401, "Missing Firebase ID token");
  let decoded;
  try { decoded = await admin.auth().verifyIdToken(match[1], true); }
  catch (_) { throw httpError(401, "Invalid or revoked Firebase ID token"); }
  const snap = await db.collection("users").doc(decoded.uid).get();
  if (!snap.exists || snap.get("status") !== "ACTIVE") throw httpError(403, "Active account required");
  const role = str(snap.get("role"));
  if (roles.length && !roles.includes(role)) throw httpError(403, "Role is not allowed");
  return {uid: decoded.uid, role};
}
app.get("/health", (_, res) => res.json({ok: true}));
const commerce = require('./commerce').createCommerce({db, admin, authenticate: auth});
app.use(commerce.router);

app.post("/recalculate-rating", async (req, res, next) => {
  try {
    const user = await auth(req, ["CUSTOMER"]), body = req.body || {}, reviewId = str(body.reviewId);
    let productId = str(body.productId);
    if (reviewId) {
      const review = await db.collection("reviews").doc(reviewId).get();
      if (!review.exists || review.get("customerId") !== user.uid) throw httpError(403, "Review is not owned by this customer");
      if (review.get("status") !== "PUBLISHED") throw httpError(409, "Review is not published");
      productId = str(review.get("productId"));
    } else {
      const orderId = str(body.orderId), itemId = str(body.orderItemId);
      if (!productId || !orderId || !itemId) throw httpError(400, "reviewId or productId/orderId/orderItemId required");
      const orderRef = db.collection("orders").doc(orderId);
      const [order, item] = await Promise.all([orderRef.get(), orderRef.collection("items").doc(itemId).get()]);
      if (!order.exists || order.get("ownerId") !== user.uid || order.get("status") !== "DELIVERED"
          || !item.exists || item.get("productId") !== productId) throw httpError(403, "Delivered customer order item required");
    }
    await db.runTransaction(async tx => {
      const productRef = db.collection("products").doc(productId);
      const product = await tx.get(productRef);
      if (!product.exists) throw httpError(404, "Product not found");
      const result = await tx.get(db.collection("reviews").where("productId", "==", productId).where("status", "==", "PUBLISHED"));
      const count = result.size, average = count ? result.docs.reduce((sum, d) => sum + num(d.get("rating")), 0) / count : 0;
      tx.update(productRef, {averageRating: average, reviewCount: count});
    });
    res.json({ok: true, productId});
  } catch (error) { next(error); }
});

async function notifyCustomer(userId, type, title, message, referenceId, eventKey) {
  const notificationId = `${type}_${eventKey}_${userId}`.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 120);
  const notificationRef = db.collection("notifications").doc(notificationId);
  const created = await db.runTransaction(async tx => {
    if ((await tx.get(notificationRef)).exists) return false;
    tx.create(notificationRef, {id: notificationId, userId, type, title, body: message, referenceId,
      isRead: false, createdAt: admin.firestore.FieldValue.serverTimestamp()});
    return true;
  });
  if (!created) return {duplicate: true, sent: 0};
  const devices = await db.collection("users").doc(userId).collection("devices").get();
  const tokens = devices.docs.map(d => str(d.get("token"))).filter(Boolean);
  let sent = 0;
  for (let i = 0; i < tokens.length; i += 500) {
    const chunk = tokens.slice(i, i + 500);
    const result = await admin.messaging().sendEachForMulticast({tokens: chunk,
      data: {title, body: message, type, referenceId}, android: {priority: "high"}});
    sent += result.successCount;
    const stale = result.responses.map((r, j) => !r.success
      && ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(r.error && r.error.code)
      ? chunk[j] : null).filter(Boolean);
    if (stale.length) await Promise.all(stale.map(token => db.collection("users").doc(userId)
      .collection("devices").doc(require("crypto").createHash("sha256").update(token).digest("hex")).delete().catch(() => {})));
  }
  return {duplicate: false, sent};
}

app.post("/send-notification", async (req, res, next) => {
  try {
    const caller = await auth(req, ["CUSTOMER", "SELLER", "ADMIN"]), body = req.body || {};
    const type = str(body.type), referenceId = str(body.referenceId);
    if (!["ORDER", "CAMPAIGN", "PROMOTION", "MODERATION", "SYSTEM"].includes(type)) throw httpError(400, "Invalid notification type");
    if (!referenceId || referenceId.length > 128) throw httpError(400, "referenceId required");
    let recipients = [], title = str(body.title), message = str(body.body), eventKey = `${referenceId}_${Date.now()}`;
    if (type === "ORDER") {
      const order = await db.collection("orders").doc(referenceId).get();
      if (!order.exists) throw httpError(404, "Order not found");
      if (caller.role === "SELLER" && order.get("sellerId") !== caller.uid) throw httpError(403, "Order is not owned by seller");
      if (caller.role === "CUSTOMER" && order.get("ownerId") !== caller.uid) throw httpError(403, "Order is not owned by customer");
      if (order.get("ownerType") !== "CUSTOMER") throw httpError(409, "Guest order has no Customer recipient");
      recipients = [str(order.get("ownerId"))];
      const status = str(order.get("status")), paymentStatus = str(order.get("paymentStatus"));
      const messages = {PENDING: ["Đã tiếp nhận đơn hàng", "Đơn hàng đang chờ cửa hàng xác nhận."],
        CONFIRMED: ["Đơn hàng đã xác nhận", "Cửa hàng đã xác nhận đơn hàng của bạn."],
        PREPARING: ["Đơn hàng đang chuẩn bị", "Cửa hàng đang chuẩn bị sản phẩm."],
        SHIPPING: ["Đơn hàng đang giao", "Đơn hàng đã được bàn giao cho vận chuyển."],
        READY_FOR_PICKUP: ["Đơn hàng sẵn sàng nhận", "Bạn có thể đến điểm nhận hàng."],
        DELIVERED: ["Đơn hàng hoàn tất", "Đơn hàng đã được giao/nhận thành công."],
        CANCELLED: ["Đơn hàng đã hủy", "Đơn hàng đã hủy và phần tồn giữ chỗ đã được hoàn trả."]};
      if (paymentStatus === "PAID") { title = "Thanh toán đã xác nhận"; message = "Cửa hàng đã xác nhận thanh toán đơn hàng."; }
      else [title, message] = messages[status] || ["Đơn hàng đã cập nhật", `Trạng thái: ${status || "UNKNOWN"}.`];
      eventKey = `${referenceId}_${status}_${paymentStatus}`;
    } else if (type === "PROMOTION" && caller.role === "SELLER") {
      const promo = await db.collection("promotions").doc(referenceId).get();
      if (!promo.exists || promo.get("sellerId") !== caller.uid) throw httpError(403, "Promotion is not owned by seller");
      if (promo.get("active") !== true || millis(promo.get("startDate")) > Date.now() || millis(promo.get("endDate")) <= Date.now())
        return res.json({ok: true, recipients: 0, skipped: true});
      const favorites = await db.collection("favorites").where("productId", "==", referenceId).get();
      recipients = [...new Set(favorites.docs.map(d => str(d.get("customerId"))).filter(Boolean))];
      title = "Sản phẩm yêu thích có ưu đãi"; message = "Một sản phẩm bạn yêu thích đang có chương trình ưu đãi.";
      eventKey = `${referenceId}_${millis(promo.get("updatedAt")) || Date.now()}`;
    } else if (type === "CAMPAIGN" && (caller.role === "SELLER" || caller.role === "ADMIN")) {
      const campaign = await db.collection("campaigns").doc(referenceId).get();
      if (!campaign.exists || (caller.role === "SELLER" && campaign.get("sellerId") !== caller.uid))
        throw httpError(403, "Campaign is not available to caller");
      if (campaign.get("status") !== "ACTIVE") return res.json({ok: true, recipients: 0, skipped: true});
      const batchIds = Object.keys(campaign.get("batchTargets") || {});
      const batches = await Promise.all(batchIds.map(id => db.collection("productBatches").doc(id).get()));
      const productIds = [...new Set(batches.filter(b => b.exists).map(b => str(b.get("productId"))).filter(Boolean))];
      const favorites = await Promise.all(productIds.map(id => db.collection("favorites").where("productId", "==", id).get()));
      recipients = [...new Set(favorites.flatMap(s => s.docs.map(d => str(d.get("customerId")))).filter(Boolean))];
      title = "Campaign giải cứu đang hoạt động"; message = str(campaign.get("title")) || "Một Campaign mới phù hợp với sản phẩm bạn theo dõi.";
      eventKey = `${referenceId}_${millis(campaign.get("updatedAt")) || Date.now()}`;
    } else if (caller.role === "ADMIN") recipients = [str(body.userId)].filter(Boolean);
    else throw httpError(403, "Caller cannot send this notification type");
    if (!title || title.length > 120 || message.length > 1000) throw httpError(400, "Invalid notification content");
    const results = await Promise.all(recipients.map(async recipientId => {
      const target = await db.collection("users").doc(recipientId).get();
      if (!target.exists || target.get("role") !== "CUSTOMER" || target.get("status") !== "ACTIVE") return null;
      return notifyCustomer(recipientId, type, title, message, referenceId, eventKey);
    }));
    res.json({ok: true, recipients: results.filter(Boolean).length,
      sent: results.reduce((sum, value) => sum + (value ? value.sent : 0), 0),
      duplicates: results.filter(value => value && value.duplicate).length});
  } catch (error) { next(error); }
});

app.use((error, _, res, __) => res.status(error.status || 500).json({error: error.status ? error.message : "Internal server error"}));
app.listen(port, "0.0.0.0", () => console.log(`RescueFarm API listening on ${port}`));
