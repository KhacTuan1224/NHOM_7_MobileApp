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
function roundVnd(v) { return Math.max(0, Math.round(v)); }
function distanceKm(aLat, aLng, bLat, bLng) {
  const r = Math.PI / 180, dLat = (bLat - aLat) * r, dLng = (bLng - aLng) * r;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * r) * Math.cos(bLat * r) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
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
function quotePrice(product, promotion, quantity, now) {
  const rescue = num(product.rescuePrice), original = num(product.originalPrice);
  let promoDiscount = 0, volumeDiscount = 0;
  if (promotion && promotion.active && millis(promotion.startDate) <= now && millis(promotion.endDate) > now) {
    if (promotion.type === "PERCENT") promoDiscount = rescue * num(promotion.value) / 100;
    if (promotion.type === "FIXED") promoDiscount = num(promotion.value);
    promoDiscount = Math.min(rescue, promoDiscount);
    for (const [threshold, discount] of Object.entries(promotion.quantityDiscountTiers || {}))
      if (quantity >= Number(threshold)) volumeDiscount = Math.max(volumeDiscount, num(discount));
  }
  const unit = roundVnd(Math.max(0, rescue - promoDiscount) * (1 - volumeDiscount / 100));
  const subtotal = roundVnd(unit * quantity);
  return {unit, subtotal, originalSubtotal: roundVnd(original * quantity), promoDiscount, volumeDiscount};
}

app.get("/health", (_, res) => res.json({ok: true}));
app.post("/admin/dashboard", async (req, res, next) => {
  try {
    await auth(req, ["ADMIN"]);
    const now = Date.now();
    const count = query => query.count().get().then(result => result.data().count || 0);
    const sum = async (query, field) => {
      const result = await query.aggregate({value: admin.firestore.AggregateField.sum(field)}).get();
      return num(result.data().value);
    };
    const names = ["sellerApplications", "users", "posts", "products", "campaigns",
      "categories", "banners", "reviews", "reports", "orders"];
    const totalValues = await Promise.all(names.map(name => count(db.collection(name))));
    const totalKeys = ["SELLER_APPLICATIONS", "USERS", "POSTS", "PRODUCTS", "CAMPAIGNS",
      "CATEGORIES", "BANNERS", "REVIEWS", "REPORTS", "ORDERS"];
    const totals = Object.fromEntries(totalKeys.map((key, index) => [key, totalValues[index]]));
    const activeCampaignsQuery = db.collection("campaigns").where("status", "==", "ACTIVE");
    const approvedCampaigns = db.collection("campaigns").where("status", "in", ["ACTIVE", "COMPLETED", "EXPIRED", "STOPPED"]);
    const recentMobileLocations = db.collection("campaigns")
      .where("locationUpdatedAt", ">=", admin.firestore.Timestamp.fromMillis(now - 10 * 60 * 1000))
      .where("locationUpdatedAt", "<=", admin.firestore.Timestamp.fromMillis(now)).get();
    const [pendingSellers, pendingPosts, pendingCampaigns, pendingReviews, pendingReport,
      inReviewReport, deliveredRevenue, activeCampaigns, criticalCampaigns, recentLocations,
      totalRescuedQuantity, approvedTargetQuantity] = await Promise.all([
      count(db.collection("sellerApplications").where("status", "==", "PENDING")),
      count(db.collection("posts").where("status", "==", "PENDING_APPROVAL")),
      count(db.collection("campaigns").where("status", "==", "PENDING_APPROVAL")),
      count(db.collection("reviews").where("status", "==", "PENDING")),
      count(db.collection("reports").where("status", "==", "PENDING")),
      count(db.collection("reports").where("status", "==", "IN_REVIEW")),
      sum(db.collection("orders").where("status", "==", "DELIVERED"), "totalAmount"),
      count(activeCampaignsQuery),
      count(activeCampaignsQuery.where("urgencyLevel", "==", "CRITICAL")),
      recentMobileLocations,
      sum(approvedCampaigns, "rescuedQuantity"), sum(approvedCampaigns, "targetQuantity")]);
    const liveMobilePoints = recentLocations.docs.filter(campaign =>
      str(campaign.get("status")) === "ACTIVE"
      && str(campaign.get("rescueMode")) === "MOBILE_POINT"
      && campaign.get("locationSharingEnabled") === true
      && Number.isFinite(campaign.get("currentLatitude"))
      && Number.isFinite(campaign.get("currentLongitude"))
      && Math.abs(campaign.get("currentLatitude")) <= 90
      && Math.abs(campaign.get("currentLongitude")) <= 180
      && !(campaign.get("currentLatitude") === 0 && campaign.get("currentLongitude") === 0)).length;
    res.json({ok: true, totals,
      pendingSellers, pendingPosts, pendingCampaigns, pendingReviews,
      openReports: pendingReport + inReviewReport,
      deliveredRevenue, activeCampaigns, criticalCampaigns, liveMobilePoints,
      totalRescuedQuantity,
      completionRate: approvedTargetQuantity > 0 ? Math.min(100, totalRescuedQuantity * 100 / approvedTargetQuantity) : 0,
      calculatedAt: new Date(now).toISOString()});
  } catch (error) { next(error); }
});
app.post("/quoteCustomerCheckout", async (req, res, next) => {
  try {
    const user = await auth(req, ["CUSTOMER"]), data = req.body || {}, items = data.items;
    if (!Array.isArray(items) || !items.length || items.length > 20) throw httpError(400, "Checkout accepts 1–20 items");
    const fulfillment = str(data.fulfillmentType), payment = str(data.paymentMethod);
    if (!["DELIVERY", "PICKUP", "ON_SITE_RESCUE"].includes(fulfillment)) throw httpError(400, "Invalid fulfillment type");
    if (!["COD", "BANK_TRANSFER", "CASH_ON_SITE"].includes(payment)) throw httpError(400, "Invalid payment method");
    if ((payment === "COD") !== (fulfillment === "DELIVERY") && payment !== "BANK_TRANSFER")
      throw httpError(400, "Payment method does not match fulfillment");
    if (items.some(i => !i || !str(i.productId) || !str(i.batchId) || !str(i.sellerId)
        || !Number.isFinite(Number(i.quantity)) || Number(i.quantity) <= 0)) throw httpError(400, "Invalid cart item");
    const itemKeys = items.map(i => `${str(i.productId)}::${str(i.batchId)}`);
    if (new Set(itemKeys).size !== itemKeys.length) throw httpError(400, "Duplicate product batch in checkout request");
    const sellers = new Set(items.map(i => str(i.sellerId)));
    if (sellers.size !== 1) throw httpError(400, "Split cart by seller before checkout");
    const sellerId = [...sellers][0], now = Date.now();
    const docs = await Promise.all(items.map(async item => {
      const [p, b, promo] = await Promise.all([
        db.collection("products").doc(str(item.productId)).get(),
        db.collection("productBatches").doc(str(item.batchId)).get(),
        db.collection("promotions").doc(str(item.productId)).get()
      ]);
      if (!p.exists || p.get("sellerId") !== sellerId || p.get("status") !== "ACTIVE") throw httpError(409, "Product unavailable");
      const qty = Number(item.quantity), available = num(b.get("availableQuantity"));
      if (!b.exists || b.get("productId") !== p.id || !["AVAILABLE", "RESERVED_PARTIAL"].includes(str(b.get("status")))
          || available < qty || millis(b.get("expiryDate")) <= now) throw httpError(409, "Batch unavailable or stock changed");
      const product = p.data(), promotion = promo.exists ? promo.data() : null;
      return {item, p: product, batch: b.data(), price: quotePrice(product, promotion, qty, now)};
    }));
    const campaignIds = new Set(docs.map(x => str(x.batch.activeCampaignId)).filter(Boolean));
    if (campaignIds.size > 1) throw httpError(409, "Split cart by campaign before checkout");
    if (fulfillment === "ON_SITE_RESCUE" && campaignIds.size !== 1) throw httpError(409, "On-site rescue requires an active campaign batch");
    let campaign = null;
    if (campaignIds.size) {
      const c = await db.collection("campaigns").doc([...campaignIds][0]).get();
      if (!c.exists || c.get("sellerId") !== sellerId || c.get("status") !== "ACTIVE" || millis(c.get("endDate")) <= now)
        throw httpError(409, "Campaign is closed");
      campaign = {id: c.id, ...c.data()};
      const targetMap = campaign.batchTargets || {};
      for (const row of docs) if (str(row.batch.activeCampaignId)
          && num(targetMap[row.item.batchId]) < Number(row.item.quantity)) throw httpError(409, "Campaign batch allocation is insufficient");
      const targetLeft = num(campaign.targetQuantity) - num(campaign.reservedQuantity) - num(campaign.rescuedQuantity);
      const requested = docs.reduce((n, row) => n + (str(row.batch.activeCampaignId) ? Number(row.item.quantity) : 0), 0);
      if (targetLeft < requested) throw httpError(409, "Campaign remaining quantity is insufficient");
      if (fulfillment === "ON_SITE_RESCUE" && campaign.rescueMode === "MOBILE_POINT"
          && (campaign.locationSharingEnabled !== true || now - millis(campaign.locationUpdatedAt) > 600000
            || millis(campaign.locationUpdatedAt) > now)) throw httpError(409, "Mobile campaign location is stale");
    }
    let shippingFee = 0, distance = 0;
    if (fulfillment === "DELIVERY") {
      const lat = Number(data.latitude), lng = Number(data.longitude);
      if (!Number.isFinite(lat) || lat < -90 || lat > 90 || !Number.isFinite(lng) || lng < -180 || lng > 180
          || (lat === 0 && lng === 0)) throw httpError(400, "Delivery address coordinates required");
      const [seller, config] = await Promise.all([
        db.collection("sellerPublicProfiles").doc(sellerId).get(), db.collection("appConfig").doc("shipping").get()
      ]);
      if (!seller.exists) throw httpError(409, "Seller public profile is unavailable");
      if (!config.exists) throw httpError(409, "Global shipping rate is not configured");
      const sellerLat = num(seller.get("latitude"), NaN), sellerLng = num(seller.get("longitude"), NaN);
      const baseFee = num(config.get("baseFeeVnd"), NaN), perKmFee = num(config.get("perKmFeeVnd"), NaN);
      const freeDistance = num(config.get("freeDistanceKm"), NaN);
      if (!Number.isFinite(sellerLat) || sellerLat < -90 || sellerLat > 90
          || !Number.isFinite(sellerLng) || sellerLng < -180 || sellerLng > 180
          || (sellerLat === 0 && sellerLng === 0)) {
        throw httpError(409, "Seller GPS coordinates are not configured");
      }
      if (!Number.isFinite(baseFee) || baseFee < 0 || baseFee > 1000000
          || !Number.isFinite(perKmFee) || perKmFee < 0 || perKmFee > 1000000
          || !Number.isFinite(freeDistance) || freeDistance < 0 || freeDistance > 20040) {
        throw httpError(409, "Global shipping rate is invalid");
      }
      distance = distanceKm(sellerLat, sellerLng, lat, lng);
      shippingFee = baseFee + Math.ceil(Math.max(0, distance - freeDistance) * perKmFee);
      if (!Number.isFinite(shippingFee) || shippingFee > 1000000)
        throw httpError(409, "Calculated shipping fee exceeds the allowed limit");
    }
    if (payment === "BANK_TRANSFER") {
      const bank = await db.collection("sellerPublicProfiles").doc(sellerId).get();
      if (!["bankCode", "bankAccountName", "bankAccountNumber"].every(k => str(bank.get(k)))) throw httpError(409, "Seller bank details unavailable");
    }
    const lines = docs.map(({item, p, price}) => ({productId: item.productId, batchId: item.batchId,
      productName: str(p.name), unit: str(p.unit), quantity: Number(item.quantity), subtotal: price.subtotal,
      finalUnitPrice: price.unit, originalSubtotal: price.originalSubtotal}));
    const subtotal = lines.reduce((n, line) => n + line.subtotal, 0);
    const originalSubtotal = lines.reduce((n, line) => n + line.originalSubtotal, 0);
    res.json({lines, subtotal, originalSubtotal, totalDiscount: Math.max(0, originalSubtotal - subtotal),
      shippingFee, distanceKm: distance, total: subtotal + shippingFee,
      campaignId: campaign ? campaign.id : "", campaignTitle: campaign ? str(campaign.title) : "",
      campaignLocationName: campaign ? str(campaign.locationName) : "", rescueMode: campaign ? str(campaign.rescueMode) : "",
      campaignLatitude: campaign ? num(campaign.currentLatitude || campaign.latitude) : 0,
      campaignLongitude: campaign ? num(campaign.currentLongitude || campaign.longitude) : 0, quoteIsEstimate: true});
  } catch (error) { next(error); }
});

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
