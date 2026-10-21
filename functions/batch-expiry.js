const {enqueueNotification} = require('./notification-outbox');
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
const terminal = new Set(['DELIVERED', 'CANCELLED']);

// Expiration is a sales restriction, not a physical disposal or an automatic refund.
function createBatchExpiry({db, admin, campaignLifecycle, now = Date.now, pageSize = 200}) {
  let batchCursor = null, orderCursor = null;
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
      if (!['EXPIRED', 'ARCHIVED'].includes(batch.get('status'))) tx.update(ref, {
        status: 'EXPIRED', expiredAt: stamp(), updatedAt: stamp(),
        inventoryVersion: (batch.get('inventoryVersion') || 0) + 1
      });
    });
  }

  async function reviewOrder(ref) {
    const action = await db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      if (!snap.exists || terminal.has(snap.get('status')) || snap.get('inventoryFinalized')) return;
      const order = snap.data(), ids = Object.keys(order.batchReservations || {});
      if (!ids.length) return;
      const batches = await Promise.all(ids.map(id => tx.get(db.collection('productBatches').doc(id))));
      if (!batches.some(batch => batch.exists && millis(batch.get('expiryDate')) > 0
          && millis(batch.get('expiryDate')) <= now())) return;
      const canCancel = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP'].includes(order.status)
        && ['COD', 'CASH_ON_SITE'].includes(order.paymentMethod)
        && order.paymentStatus === 'UNPAID';
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
    let query = db.collection('productBatches').where('expiryDate', '<=', admin.firestore.Timestamp.fromMillis(now()))
      .orderBy('expiryDate').orderBy(idField).limit(pageSize);
    if (batchCursor) query = query.startAfter(...batchCursor);
    const batches = await query.get();
    const batchResults = await Promise.allSettled(batches.docs.map(batch => expire(batch.ref)));
    const last = batches.docs[batches.docs.length - 1];
    batchCursor = batches.docs.length === pageSize ? [last.get('expiryDate'), last.id] : null;
    let ordersQuery = db.collection('orders').orderBy(idField).limit(pageSize);
    if (orderCursor) ordersQuery = ordersQuery.startAfter(orderCursor);
    const orders = await ordersQuery.get();
    const orderResults = await Promise.allSettled(orders.docs.map(order => reviewOrder(order.ref)));
    orderCursor = orders.docs.length === pageSize ? orders.docs[orders.docs.length - 1].id : null;
    const failures = [...batchResults, ...orderResults].filter(result => result.status === 'rejected');
    if (failures.length) throw new Error(`Batch expiry: ${failures.length} failures; ${failures[0].reason.message}`);
    return {batches: batches.docs.length, orders: orders.docs.length};
  }
  return {reconcile};
}
module.exports = {createBatchExpiry};
