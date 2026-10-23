const {enqueueNotification} = require('./notification-outbox');
const {isQuotaError} = require('./read-cache');
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
const terminal = new Set(['DELIVERED', 'CANCELLED']);

// Expiration is a sales restriction, not a physical disposal or an automatic refund.
function createBatchExpiry({db, admin, campaignLifecycle, now = Date.now, pageSize = 200}) {
  let batchCursor = null, orderCursor = null;
  let historicalCursor = null, nextHistoricalScan = 0;
  const stamp = () => admin.firestore.FieldValue.serverTimestamp();
  const idField = admin.firestore.FieldPath.documentId();

  async function expire(ref) {
    return db.runTransaction(async tx => {
      const batch = await tx.get(ref);
      if (!batch.exists || !millis(batch.get('expiryDate')) || millis(batch.get('expiryDate')) > now()) return;
      const eventRef = db.collection('batchExpiryEvents').doc(batch.id);
      const event = await tx.get(eventRef);
      const product = await tx.get(db.collection('products').doc(batch.get('productId')));
      if (!event.exists) tx.create(eventRef, {
        batchId: batch.id, productId: batch.get('productId'), sellerId: product.get('sellerId') || '',
        unit: product.get('unit') || '', expiryDate: batch.get('expiryDate'), detectedAt: stamp(),
        // Detection snapshots are not write-offs. Released reservations remain physical expired stock.
        availableQuantityAtDetection: batch.get('availableQuantity'),
        reservedQuantityAtDetection: batch.get('reservedQuantity'), soldQuantityAtDetection: batch.get('soldQuantity')
      });
      if (!['EXPIRED', 'ARCHIVED'].includes(batch.get('status')) || !batch.get('expiredAt')) tx.update(ref, {
        status: batch.get('status') === 'ARCHIVED' ? 'ARCHIVED' : 'EXPIRED', expiredAt: stamp(), updatedAt: stamp(),
        inventoryVersion: (batch.get('inventoryVersion') || 0) + 1
      });
    });
  }

  async function reviewOrder(ref) {
    const action = await db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      if (!snap.exists || terminal.has(snap.get('status')) || snap.get('inventoryFinalized')) return;
      const order = snap.data(), ids = Object.keys(order.batchReservations || {});
      const canCancel = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP'].includes(order.status)
        && ['COD', 'CASH_ON_SITE'].includes(order.paymentMethod) && order.paymentStatus === 'UNPAID';
      if (order.expiryReviewRequired && !canCancel) return;
      if (!ids.length) return;
      const batches = await Promise.all(ids.map(id => tx.get(db.collection('productBatches').doc(id))));
      if (!batches.some(batch => batch.exists && millis(batch.get('expiryDate')) > 0
          && millis(batch.get('expiryDate')) <= now())) return;
      if (!order.expiryReviewRequired) {
        tx.update(ref, {expiryReviewRequired: true, expiryDetectedAt: stamp(), updatedAt: stamp()});
        const recipients = [...new Set([order.sellerId, ...(order.ownerType === 'CUSTOMER' ? [order.ownerId] : [])].filter(Boolean))];
        for (const uid of recipients) {
          const inboxId = `expired_${snap.id}_${uid}`;
          const body = 'Lô hàng đã hết hạn. Kiểm tra trạng thái đơn; đơn đã thanh toán/đang giao cần đối soát với cửa hàng, chưa tự hoàn tiền.';
          tx.set(db.collection('notifications').doc(inboxId), {id: inboxId, userId: uid, type: 'ORDER',
            referenceId: snap.id, title: 'Lô hàng của đơn đã hết hạn', body, isRead: false, createdAt: stamp()});
          enqueueNotification(tx, db, admin, inboxId, {kind: 'DIRECT', resourceType: 'ORDER', resourceId: snap.id,
            recipientUid: uid, inboxId, publicMessage: body});
        }
      }
      return canCancel;
    });
    // transitionOrder rechecks payment, state, stock and expiry atomically after this inspection.
    if (action) await campaignLifecycle.transitionOrder({role: 'ADMIN', uid: 'system-batch-expiry'},
      {orderId: ref.id, targetStatus: 'CANCELLED', cancelReason: 'BATCH_EXPIRED'});
  }

  async function reconcile() {
    let query = db.collection('productBatches').where('status', 'in', ['AVAILABLE', 'RESERVED_PARTIAL', 'SOLD_OUT'])
      .where('expiryDate', '<=', admin.firestore.Timestamp.fromMillis(now()))
      .orderBy('expiryDate').orderBy(idField).limit(pageSize);
    if (batchCursor) query = query.startAfter(...batchCursor);
    const batches = await query.get();
    const candidates = new Map(batches.docs.map(batch => [batch.id, batch]));
    let historicalAdvance = null;
    // Compatibility audit of old EXPIRED/ARCHIVED records is bounded and hourly,
    // rather than rereading every historical batch every minute.
    if (now() >= nextHistoricalScan) {
      let historical = db.collection('productBatches').where('expiryDate', '<=', admin.firestore.Timestamp.fromMillis(now()))
        .orderBy('expiryDate').orderBy(idField).limit(pageSize);
      if (historicalCursor) historical = historical.startAfter(...historicalCursor);
      const page = await historical.get();
      for (const batch of page.docs) if (!batch.get('expiredAt')) candidates.set(batch.id, batch);
      const tail = page.docs[page.docs.length - 1];
      historicalAdvance = {cursor: page.size === pageSize ? [tail.get('expiryDate'), tail.id] : null,
        nextAt: now() + 3600000};
    }
    async function process(documents, action) {
      const results = [];
      for (let offset = 0; offset < documents.length; offset += 10) {
        const chunk = await Promise.allSettled(documents.slice(offset, offset + 10).map(doc => action(doc.ref)));
        const quota = chunk.find(result => result.status === 'rejected' && isQuotaError(result.reason));
        if (quota) throw quota.reason;
        results.push(...chunk);
      }
      return results;
    }
    const batchResults = await process([...candidates.values()], expire);
    if (historicalAdvance && !batchResults.some(result => result.status === 'rejected')) {
      historicalCursor = historicalAdvance.cursor; nextHistoricalScan = historicalAdvance.nextAt;
    }
    const last = batches.docs[batches.docs.length - 1];
    batchCursor = batches.docs.length === pageSize ? [last.get('expiryDate'), last.id] : null;
    let ordersQuery = db.collection('orders').where('status', 'in',
      ['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'SHIPPING']).orderBy(idField).limit(pageSize);
    if (orderCursor) ordersQuery = ordersQuery.startAfter(orderCursor);
    const orders = await ordersQuery.get();
    const orderResults = await process(orders.docs, reviewOrder);
    orderCursor = orders.docs.length === pageSize ? orders.docs[orders.docs.length - 1].id : null;
    const failures = [...batchResults, ...orderResults].filter(result => result.status === 'rejected');
    if (failures.length) throw new Error(`Batch expiry: ${failures.length} failures; ${failures[0].reason.message}`);
    return {batches: candidates.size, orders: orders.docs.length};
  }
  return {reconcile};
}
module.exports = {createBatchExpiry};
