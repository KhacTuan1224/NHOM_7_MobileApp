const express = require('express');
const {createReadCache} = require('./read-cache');
const {normalizeUnit} = require('./quantity-units');
const {projectCampaign} = require('./campaign-projection');
const {orderAllocations} = require('./campaign-lifecycle');
const time = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
const number = value => Number.isFinite(value) ? value : 0;

async function allPages(query, admin) {
  const docs = []; let cursor = null;
  while (true) {
    let page = query.orderBy(admin.firestore.FieldPath.documentId()).limit(200);
    if (cursor) page = page.startAfter(cursor);
    const snapshot = await page.get();
    docs.push(...snapshot.docs);
    if (snapshot.size < 200) break;
    cursor = snapshot.docs[snapshot.docs.length - 1];
  }
  return docs;
}

function aggregateSellerStatistics({orders, campaigns, itemsByOrder, days, now = Date.now()}) {
  const campaignById = new Map(campaigns.map(c => [c.id, c]));
  const cutoff = days ? now - days * 86400000 : 0;
  const created = orders.filter(o => time(o.createdAt) >= cutoff && time(o.createdAt) <= now);
  const deliveredTime = order => time(order.deliveredAt) || time(order.updatedAt) || time(order.createdAt);
  const delivered = orders.filter(o => o.status === 'DELIVERED' && deliveredTime(o) >= cutoff && deliveredTime(o) <= now);
  const first = days ? cutoff : delivered.reduce((earliest, order) => Math.min(earliest, deliveredTime(order)), now);
  const start = first === now ? now - 86400000 : first;
  const buckets = Array.from({length: 7}, (_, index) => ({at: Math.round(start + (now - start) * index / 7), revenue: 0}));
  const quantities = {}, products = new Map(), reasons = new Map();
  let revenue = 0, rescuedOrders = 0, approximateDates = false;
  for (const order of delivered) {
    const amount = Math.max(0, number(order.subtotal) - number(order.quantityDiscount));
    revenue += amount;
    const bucket = Math.min(6, Math.max(0, Math.floor((deliveredTime(order) - start) * 7 / Math.max(1, now - start))));
    buckets[bucket].revenue += amount;
    if (!time(order.deliveredAt)) approximateDates = true;
    const campaign = campaignById.get(order.campaignId);
    const allocations = campaign ? orderAllocations(order, campaign.batchTargets || {}) : {};
    if (Object.keys(allocations).length) rescuedOrders++;
    for (const item of itemsByOrder.get(order.id) || []) {
      const unit = normalizeUnit(item.unitSnapshot) || 'đơn vị chưa xác minh';
      const key = `${item.productId}|${unit}`, existing = products.get(key) || {label: item.productNameSnapshot || item.productId, unit, quantity: 0};
      existing.quantity += number(item.quantity); products.set(key, existing);
      const quantity = number(allocations[item.batchId]);
      if (quantity > 0 && campaign.unitStatus === 'VERIFIED' && unit === normalizeUnit(campaign.quantityUnit)) {
        quantities[unit] = (quantities[unit] || 0) + quantity;
        const reason = campaign.rescueReason || 'OTHER', key = `${reason}|${unit}`;
        const entry = reasons.get(key) || {label: reason, unit, quantity: 0};
        entry.quantity += quantity; reasons.set(key, entry);
      }
    }
  }
  const completed = campaigns.filter(c => c.status === 'COMPLETED'
    && (time(c.completedAt) || time(c.updatedAt)) >= cutoff && (time(c.completedAt) || time(c.updatedAt)) <= now);
  // Average percentages instead of summing quantities with potentially different units.
  const overlapping = campaigns.filter(c => c.unitStatus === 'VERIFIED' && ['ACTIVE', 'COMPLETED', 'EXPIRED', 'STOPPED'].includes(c.status)
    && number(c.targetQuantity) > 0 && time(c.startDate) <= now && time(c.endDate) >= cutoff);
  const rescueRate = overlapping.length ? overlapping.reduce((total, c) => total
    + Math.min(1, Math.max(0, number(c.rescuedQuantity) / c.targetQuantity)), 0) * 100 / overlapping.length : 0;
  const ranking = entries => [...entries.values()].sort((a, b) => a.unit.localeCompare(b.unit)
    || b.quantity - a.quantity || a.label.localeCompare(b.label));
  return {days, calculatedAt: now, revenue, orders: created.length, deliveredOrders: delivered.length, rescuedOrders,
    unverifiedCampaigns: campaigns.filter(c => c.unitStatus !== 'VERIFIED' && number(c.rescuedQuantity) > 0).length,
    completedCampaigns: completed.length, rescuedByUnit: quantities, rescueRate, rescueRateCampaigns: overlapping.length,
    pendingOrders: orders.filter(o => !['DELIVERED', 'CANCELLED'].includes(o.status)).length,
    topProducts: ranking(products), topReasons: ranking(reasons), revenueTrend: buckets,
    approximateDates: approximateDates || completed.some(c => !time(c.completedAt)),
    hasData: created.length > 0 || delivered.length > 0 || completed.length > 0 || overlapping.length > 0,
    timeBasis: 'Đơn hàng theo ngày tạo; doanh thu và giải cứu theo ngày giao. Không gồm phí giao hàng.'};
}

function createSellerAnalytics({db, admin, authenticate}) {
  const router = express.Router();
  const cache = createReadCache({ttlMs: 45000, maxEntries: 50});
  router.post('/seller/statistics', async (req, res, next) => {
    try {
      const caller = await authenticate(req, ['SELLER']);
      const seller = await db.collection('users').doc(caller.uid).get();
      if (!seller.exists || seller.get('sellerStatus') !== 'APPROVED')
        throw Object.assign(new Error('Seller phải được duyệt để xem thống kê.'), {status: 403});
      const days = req.body && req.body.days;
      if (![0, 7, 30, 90].includes(days)) throw Object.assign(new Error('Kỳ thống kê không hợp lệ.'), {status: 400});
      const response = await cache.get(caller.uid + "|" + days, async () => {
      const [orderDocs, campaignDocs] = await Promise.all([
        allPages(db.collection('orders').where('sellerId', '==', caller.uid), admin),
        allPages(db.collection('campaigns').where('sellerId', '==', caller.uid), admin)]);
      const orders = orderDocs.map(doc => ({...doc.data(), id: doc.id}));
      const now = Date.now(), cutoff = days ? now - days * 86400000 : 0;
      const delivered = orders.filter(o => o.status === 'DELIVERED'
        && (time(o.deliveredAt) || time(o.updatedAt) || time(o.createdAt)) >= cutoff);
      const itemsByOrder = new Map();
      for (let offset = 0; offset < delivered.length; offset += 10) {
        await Promise.all(delivered.slice(offset, offset + 10).map(async order => {
          const items = await db.collection('orders').doc(order.id).collection('items').get();
          itemsByOrder.set(order.id, items.docs.map(doc => doc.data()));
        }));
      }
      const productDocs = await allPages(db.collection('products').where('sellerId', '==', caller.uid), admin);
      const ids = [...new Set(campaignDocs.flatMap(doc => Object.keys(doc.get('batchTargets') || {})))];
      const batches = [];
      for (let offset = 0; offset < ids.length; offset += 20) {
        const documents = await Promise.all(ids.slice(offset, offset + 20).map(id => db.collection('productBatches').doc(id).get()));
        batches.push(...documents.filter(d => d.exists).map(d => ({...d.data(), id: d.id})));
      }
      const publicProducts = new Set(productDocs.filter(d => d.get('status') === 'ACTIVE').map(d => d.id));
      const publicBatches = batches.filter(b => publicProducts.has(b.productId));
      const campaigns = campaignDocs.map(doc => projectCampaign({...doc.data(), id: doc.id}, publicBatches, now));
      const result = aggregateSellerStatistics({orders, campaigns, itemsByOrder, days, now});
      const pending = orders.filter(o => !['DELIVERED', 'CANCELLED'].includes(o.status));
      result.inventory = {products: productDocs.length, activeProducts: productDocs.filter(d => d.get('status') === 'ACTIVE').length,
        campaigns: campaigns.length, activeCampaigns: campaigns.filter(c => c.active).length,
        mobileCampaigns: campaigns.filter(c => c.active && c.rescueMode === 'MOBILE_POINT').length,
        deliveryOrders: pending.filter(o => o.fulfillmentType === 'DELIVERY').length};
      result.recentOrders = [...orders].sort((a, b) => time(b.createdAt) - time(a.createdAt) || a.id.localeCompare(b.id)).slice(0, 5)
        .map(o => ({id: o.id, orderCode: o.orderCode, ownerId: o.ownerId, ownerType: o.ownerType, sellerId: o.sellerId,
          campaignId: o.campaignId, receiverAddress: o.receiverAddress, receiverLatitude: o.receiverLatitude, receiverLongitude: o.receiverLongitude,
          subtotal: o.subtotal, quantityDiscount: o.quantityDiscount, shippingFee: o.shippingFee, note: o.note,
          status: o.status, receiverName: o.receiverName, receiverPhone: o.receiverPhone, fulfillmentType: o.fulfillmentType, paymentMethod: o.paymentMethod, paymentStatus: o.paymentStatus, totalAmount: o.totalAmount, createdAt: time(o.createdAt), updatedAt: time(o.updatedAt)}));
      result.totalOrders = orders.length;
      result.generatedAt = now; result.cacheTtlSeconds = 45;
      return result;
      });
      res.json(response);
    } catch (error) { next(error); }
  });
  return {router};
}

module.exports = {createSellerAnalytics, aggregateSellerStatistics, allPages};
