const express = require("express");
const admin = require("firebase-admin");

const serviceAccount = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
if (!serviceAccount) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is required (set it as a Render secret)");
admin.initializeApp({credential: admin.credential.cert(JSON.parse(serviceAccount))});
const db = admin.firestore();
const {isQuotaError, createQuotaGuard, createQuotaMiddleware} = require('./read-cache');
const quotaGuard = createQuotaGuard();
let migrationsReady = false;
function noteQuota(error) {
  if (quotaGuard.trip()) console.warn('[firestore-quota] Tạm dừng truy vấn và tác vụ nền trong 5 phút:', error.message);
}
const app = express();
app.use(express.json({limit: "256kb", verify: (req, res, bytes) => {
  if (req.originalUrl.startsWith('/webhooks/sepay/')) req.rawBody = Buffer.from(bytes);
}}));
app.use(require('./sepay').createSePayApi({db, admin}).router);
// SePay keeps its existing verification/retry path above. This gate only pauses
// other API work; never acknowledge a payment without the existing transaction.
app.use(createQuotaMiddleware(quotaGuard, () => migrationsReady));

async function migrateSellerPaymentProfiles() {
  const marker = db.collection("systemMigrations").doc("sellerPaymentProfilesV1");
  if ((await marker.get()).exists) return;
  let cursor = null;
  while (true) {
    let query = db.collection("sellerPublicProfiles")
      .orderBy(admin.firestore.FieldPath.documentId()).limit(200);
    if (cursor) query = query.startAfter(cursor);
    const snapshot = await query.get();
    if (snapshot.empty) break;
    const privateSnapshots = await Promise.all(snapshot.docs.map(doc =>
      db.collection("sellerPaymentProfiles").doc(doc.id).get()));
    const batch = db.batch();
    snapshot.docs.forEach((doc, index) => {
      const privateProfile = privateSnapshots[index];
      const privateData = privateProfile.exists ? privateProfile.data() : {};
      const paymentData = {
        sellerId: doc.id,
        bankCode: str(privateData.bankCode) || str(doc.get("bankCode")),
        bankAccountName: str(privateData.bankAccountName) || str(doc.get("bankAccountName")),
        bankAccountNumber: str(privateData.bankAccountNumber) || str(doc.get("bankAccountNumber")),
        qrUrl: str(privateData.qrUrl) || str(doc.get("qrUrl")),
        updatedAt: privateData.updatedAt || doc.get("updatedAt")
          || admin.firestore.FieldValue.serverTimestamp()
      };
      if (paymentData.bankCode || paymentData.bankAccountName
          || paymentData.bankAccountNumber || paymentData.qrUrl) {
        batch.set(db.collection("sellerPaymentProfiles").doc(doc.id), paymentData, {merge: true});
      }
      batch.update(doc.ref, {
        bankCode: admin.firestore.FieldValue.delete(),
        bankAccountName: admin.firestore.FieldValue.delete(),
        bankAccountNumber: admin.firestore.FieldValue.delete(),
        qrUrl: admin.firestore.FieldValue.delete()
      });
    });
    await batch.commit();
    cursor = snapshot.docs[snapshot.docs.length - 1];
  }
  await marker.set({completedAt: admin.firestore.FieldValue.serverTimestamp()});
}
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

app.get('/', (req, res) => {
  res.status(200).send('Backend của ứng dụng đang chạy =))))');
});


app.get("/health", (_, res) => {
  let sepayStatus;
  try { sepayStatus = Object.keys(require('./sepay').merchants()).length ? 'CONFIGURED' : 'NOT_CONFIGURED'; }
  catch (_) { sepayStatus = 'INVALID_CONFIGURATION'; }
  res.json({ok: true, paymentFlowVersion: 2, sepayStatus, pricingVersion: 3,
    rescueProgramsVersion: 1,
    rescuePaymentPolicy: 'PICKUP_CASH_NO_PREPAYMENT', migrationsReady,
    firestoreReadStatus: quotaGuard.remainingSeconds() ? 'QUOTA_COOLDOWN' : 'NOT_IN_COOLDOWN',
    retryAfterSeconds: quotaGuard.remainingSeconds()});
});
app.use(require('./payment-qr').createPaymentQrApi({db, admin, authenticate: auth}).router);
app.use(require('./seller-names').createSellerNamesApi({db}).router);
app.use(require('./campaign-cards').createCampaignCardsApi({db}).router);
app.use(require('./seller-payment-options').createSellerPaymentOptionsApi({db}).router);
const campaignLifecycle = require('./campaign-lifecycle').createCampaignLifecycle({db, admin, authenticate: auth});
const batchExpiry = require('./batch-expiry').createBatchExpiry({db, admin, campaignLifecycle});
app.use(campaignLifecycle.router);
app.use(require('./cart-migration').createCartMigration({db, admin, authenticate: auth}).router);
app.use(require('./order-reconciliation').createOrderReconciliation({db, admin, authenticate: auth, campaignLifecycle}).router);
app.use(require('./seller-analytics').createSellerAnalytics({db, admin, authenticate: auth}).router);
const campaignMedia = require('./campaign-media').createCampaignMedia({db, admin, authenticate: auth});
app.use(campaignMedia.router);
const worker = require('./notification-outbox').createNotificationWorker({db, admin});
const rescuePrograms = require('./rescue-programs').createRescuePrograms({db, admin, authenticate: auth});
app.use(rescuePrograms.router);
app.use(require('./notifications-api').createNotificationsApi({db, admin, authenticate: auth}).router);
app.post('/internal/notifications/run', async (req, res, next) => {
  try {
    const secret = process.env.NOTIFICATION_WORKER_SECRET || '';
    const expected = Buffer.from(secret), actual = Buffer.from(req.get('X-Worker-Secret') || '');
    if (expected.length < 32 || actual.length !== expected.length || !require('node:crypto').timingSafeEqual(expected, actual))
      throw httpError(403, 'Worker credentials required');
    await campaignLifecycle.reconcileExpired();
    const rescueResult = await rescuePrograms.reconcile();
    const result = await worker.runOnce();
    result.rescuePrograms = rescueResult;
    result.mediaCleanup = await campaignMedia.reconcile();
    if (result.mediaCleanup.errors) result.attentionRequired = true;
    if (result.busy) {
      const health = await db.collection('notificationWorkerState').doc('health').get();
      result.attentionRequired = result.attentionRequired === true || (health.exists ? health.get('attentionRequired') === true
        || Date.now() - millis(health.get('lastSuccessAt')) > 600000 : process.uptime() > 600);
    }
    res.json({ok: true, ...result});
  } catch (error) { next(error); }
});
const commerce = require('./commerce').createCommerce({db, admin, authenticate: auth, campaignLifecycle});
app.use(commerce.router);
app.post("/admin/dashboard", async (req, res, next) => {
  try {
    await auth(req, ["ADMIN"]);
    await campaignLifecycle.reconcileExpired();
    const now = Date.now();
    const count = query => query.count().get().then(result => result.data().count || 0);
    const sum = async (query, field) => {
      const result = await query.aggregate({value: admin.firestore.AggregateField.sum(field)}).get();
      return num(result.data().value);
    };
    const names = ["sellerApplications", "users", "products", "campaigns",
      "categories", "banners", "reviews", "reports", "orders"];
    const totalValues = await Promise.all(names.map(name => count(db.collection(name))));
    const totalKeys = ["SELLER_APPLICATIONS", "USERS", "PRODUCTS", "CAMPAIGNS",
      "CATEGORIES", "BANNERS", "REVIEWS", "REPORTS", "ORDERS"];
    const totals = Object.fromEntries(totalKeys.map((key, index) => [key, totalValues[index]]));
    const {allPages} = require('./seller-analytics');
    const [projectionCampaigns, projectionBatches, projectionProducts] = await Promise.all([
      allPages(db.collection('campaigns').where('status', '==', 'ACTIVE'), admin),
      allPages(db.collection('productBatches'), admin),
      allPages(db.collection('products').where('status', '==', 'ACTIVE'), admin)]);
    const publicProducts = new Set(projectionProducts.map(d => d.id));
    const batches = projectionBatches.map(d => ({...d.data(), id: d.id})).filter(b => publicProducts.has(b.productId));
    const activeProjection = projectionCampaigns.map(d => require('./campaign-projection').projectCampaign({...d.data(), id: d.id}, batches, now)).filter(c => c.active);
    const approvedCampaigns = db.collection("campaigns").where("status", "in", ["ACTIVE", "COMPLETED", "EXPIRED", "STOPPED"]);
    const kgProgress = async () => {
      const campaigns = await require('./seller-analytics').allPages(approvedCampaigns, admin);
      return campaigns.filter(c => require('./quantity-units').kilograms(c.data())).reduce((total, c) => ({
        rescued: total.rescued + num(c.get('rescuedQuantity')), target: total.target + num(c.get('targetQuantity'))}), {rescued: 0, target: 0});
    };
    const recentMobileLocations = db.collection("campaigns")
      .where("locationUpdatedAt", ">=", admin.firestore.Timestamp.fromMillis(now - 10 * 60 * 1000))
      .where("locationUpdatedAt", "<=", admin.firestore.Timestamp.fromMillis(now)).get();
    const [pendingSellers, pendingCampaigns, pendingReviews, pendingReport,
      inReviewReport, deliveredRevenue, activeCampaigns, criticalCampaigns, recentLocations,
      kgTotals] = await Promise.all([
      count(db.collection("users").where("role", "==", "SELLER").where("sellerStatus", "==", "PENDING_APPROVAL")),
      count(db.collection("campaigns").where("status", "==", "PENDING_APPROVAL")),
      count(db.collection("reviews").where("status", "==", "PENDING")),
      count(db.collection("reports").where("status", "==", "PENDING")),
      count(db.collection("reports").where("status", "==", "IN_REVIEW")),
      sum(db.collection("orders").where("status", "==", "DELIVERED"), "totalAmount"),
      Promise.resolve(activeProjection.length),
      Promise.resolve(activeProjection.filter(c => c.urgencyLevel === 'CRITICAL').length),
      recentMobileLocations,
      kgProgress()]);
    const totalRescuedQuantity = kgTotals.rescued, approvedTargetQuantity = kgTotals.target;
    const liveMobilePoints = recentLocations.docs.filter(campaign =>
      activeProjection.some(c => c.id === campaign.id)
      && str(campaign.get("rescueMode")) === "MOBILE_POINT"
      && campaign.get("locationSharingEnabled") === true
      && Number.isFinite(campaign.get("currentLatitude"))
      && Number.isFinite(campaign.get("currentLongitude"))
      && Math.abs(campaign.get("currentLatitude")) <= 90
      && Math.abs(campaign.get("currentLongitude")) <= 180
      && !(campaign.get("currentLatitude") === 0 && campaign.get("currentLongitude") === 0)).length;
    res.json({ok: true, totals,
      pendingSellers, pendingCampaigns, pendingReviews,
      openReports: pendingReport + inReviewReport,
      deliveredRevenue, activeCampaigns, criticalCampaigns, liveMobilePoints,
      totalRescuedQuantity,
      completionRate: approvedTargetQuantity > 0 ? Math.min(100, totalRescuedQuantity * 100 / approvedTargetQuantity) : 0,
      calculatedAt: new Date(now).toISOString()});
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

app.use((error, req, res, __) => {
  if (isQuotaError(error) && !req.path.startsWith('/webhooks/sepay/')) {
    noteQuota(error);
    const retryAfterSeconds = quotaGuard.remainingSeconds();
    return res.set('Retry-After', String(retryAfterSeconds)).status(503).json({
      code: 'FIRESTORE_QUOTA_EXCEEDED', retryAfterSeconds,
      error: 'Firestore đang vượt quota. Vui lòng chờ quota khả dụng trở lại; tải lại liên tục sẽ không khắc phục được.'
    });
  }
  const status = error.status || 500;
  if (status >= 500) console.error("[api]", req.method, req.originalUrl,
    "code=", error.code || "", "message=", error.message || "", error.stack || "");
  res.status(status).json({error: status >= 500 && error.expose !== true ? "Internal server error" : error.message});
});
async function prepareBackend() {
  await migrateSellerPaymentProfiles(); await campaignMedia.migrateReferences(); migrationsReady = true;
}
prepareBackend().catch(error => {
  if (!isQuotaError(error)) throw error;
  noteQuota(error); // Keep health alive; retry migrations after the cooldown.
}).then(() => {
  app.listen(port, "0.0.0.0", () => console.log(`RescueFarm API listening on ${port}`));
  let reconciling = false;
  const reconcile = async () => {
    if (reconciling || quotaGuard.remainingSeconds()) return;
    reconciling = true;
    try {
      if (!migrationsReady) await prepareBackend();
      const jobs = [() => campaignLifecycle.reconcileExpired(), () => batchExpiry.reconcile(),
        () => worker.runOnce(), () => campaignMedia.reconcile(), () => rescuePrograms.reconcile()];
      for (const job of jobs) {
        try { await job(); }
        catch (error) {
          if (isQuotaError(error)) { noteQuota(error); break; }
          console.error('Backend reconciliation:', error.message);
        }
      }
    }
    catch (error) {
      if (isQuotaError(error)) noteQuota(error);
      else console.error('Campaign reconciliation failed:', error.message);
    }
    finally { reconciling = false; }
  };
  // Legacy repair is a maintenance operation, not a scan to repeat on every deploy.
  if (process.env.REPAIR_LEGACY_CAMPAIGN_LINKS === 'true' && !quotaGuard.remainingSeconds() && migrationsReady)
    campaignLifecycle.reconcileAll().catch(error => {
      if (isQuotaError(error)) noteQuota(error);
      else console.error('Legacy Campaign link repair failed:', error.message);
    });
  reconcile();
  setInterval(reconcile, 60000).unref();
}).catch(error => {
  console.error("Startup migration failed; API will not start:", error);
  process.exit(1);
});
