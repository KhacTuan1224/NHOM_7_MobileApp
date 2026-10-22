const express = require('express');
const crypto = require('node:crypto');
const fail = (status, message) => Object.assign(new Error(message), {status});
const text = value => typeof value === 'string' ? value.trim() : '';
const time = value => value?.toMillis?.() || 0;
const validId = value => /^[A-Za-z0-9_-]{1,128}$/.test(text(value));
const proof = value => { try { const u = new URL(text(value)); return u.protocol === 'https:' && !u.username && !u.password && text(value).length <= 2048; } catch { return false; } };
function resolutionFingerprint(body) {
  return crypto.createHash('sha256').update(JSON.stringify([body.mode, text(body.reason), text(body.evidenceUrl),
    text(body.refundReference), body.refundAmount, body.confirmRefunded === true, body.confirmReturned === true, body.confirmNoTransfer === true])).digest('hex');
}
function validateResolution(order, payment, batches, body) {
  if (!validId(body.requestId) || text(body.reason).length < 3 || text(body.reason).length > 1000 || !proof(body.evidenceUrl))
    throw fail(400, 'Nhập mã yêu cầu, lý do và đường dẫn HTTPS tới bằng chứng đã đối chiếu.');
  if (!order.expiryReviewRequired && !batches.some(b => time(b.get('expiryDate')) <= Date.now()))
    throw fail(409, 'Đơn chưa thuộc diện đối soát hết hạn.');
  if (order.inventoryFinalized || ['DELIVERED', 'CANCELLED'].includes(order.status)) throw fail(409, 'Đơn đã kết thúc.');
  if (body.mode === 'LATE_RECEIPT') {
    if (order.returnRecordedAt) throw fail(409, 'Hàng đã ghi nhận thu hồi; cần chốt hủy/hoàn tiền thay vì hoàn tất nhận hàng.');
    const handover = time(order.handoverRecordedAt);
    if (!handover || !time(order.buyerReceiptAcknowledgedAt) || !order.handoverEvidenceUrl
        || batches.some(b => handover >= time(b.get('expiryDate')) || b.get('status') === 'ARCHIVED'))
      throw fail(409, 'Cần bằng chứng bàn giao được ghi trước hạn và xác nhận nhận hàng của người mua.');
    if (order.paymentMethod === 'BANK_TRANSFER' && payment.status !== 'PAID') throw fail(409, 'Chuyển khoản phải được SePay xác minh trước.');
    return {target: 'DELIVERED', paymentStatus: 'PAID'};
  }
  if (!['REFUND_CANCEL', 'CANCEL_UNPAID'].includes(body.mode)) throw fail(400, 'Kết quả đối soát không hợp lệ.');
  if ((order.status === 'SHIPPING' || order.handoverRecordedAt) && (!time(order.returnRecordedAt)
      || !proof(order.returnEvidenceUrl) || time(order.returnRecordedAt) < time(order.handoverRecordedAt) || body.confirmReturned !== true))
    throw fail(409, 'Hàng đang giao phải có xác nhận đã thu hồi thực tế và bằng chứng trả hàng.');
  if (payment.status === 'PAID') {
    if (body.mode !== 'REFUND_CANCEL' || body.confirmRefunded !== true || text(body.refundReference).length < 3
        || text(body.refundReference).length > 120 || !Number.isFinite(body.refundAmount)
        || body.refundAmount !== order.totalAmount) throw fail(400, 'Cần xác nhận hoàn đủ tiền thực tế, mã giao dịch và số tiền khớp đơn.');
    return {target: 'CANCELLED', paymentStatus: 'REFUNDED'};
  }
  if (body.mode !== 'CANCEL_UNPAID') throw fail(409, 'Kết quả không khớp trạng thái thanh toán.');
  if (order.paymentMethod === 'BANK_TRANSFER') {
    if (payment.status !== 'PENDING' || body.confirmNoTransfer !== true)
      throw fail(409, 'Admin phải đối chiếu sao kê và xác nhận chưa có tiền vào trước khi hủy.');
  } else if (payment.status !== 'UNPAID') throw fail(409, 'Trạng thái tiền chưa rõ.');
  return {target: 'CANCELLED', paymentStatus: 'FAILED'};
}
function createOrderReconciliation({db, admin, authenticate, campaignLifecycle}) {
  const router = express.Router(), stamp = () => admin.firestore.FieldValue.serverTimestamp();
  const route = (path, roles, fn) => router.post('/orders/reconciliation/' + path, async (req, res, next) => {
    try {
      const caller = !req.get('Authorization') && ['get', 'evidence'].includes(path)
        ? {role: 'GUEST', accessToken: text(req.body?.accessToken)} : await authenticate(req, roles);
      caller.accessToken = text(req.body?.accessToken);
      res.json(await fn(caller, req.body || {}));
    } catch (error) { next(error); }
  });
  const owns = (caller, order) => caller.role === 'ADMIN' || caller.role === 'SELLER' && order.sellerId === caller.uid
    || caller.role === 'CUSTOMER' && order.ownerType === 'CUSTOMER' && order.ownerId === caller.uid;
  async function canAccess(read, caller, snapshot) {
    if (!snapshot.exists) return false;
    if (caller.role !== 'GUEST' && owns(caller, snapshot.data())) return true;
    if (snapshot.get('ownerType') !== 'GUEST' || !/^[a-f0-9]{64}$/i.test(caller.accessToken || '')) return false;
    const hash = crypto.createHash('sha256').update(caller.accessToken).digest('hex');
    const [receipt, grant] = await Promise.all([read(db.collection('checkoutReceipts').doc(snapshot.id)), read(db.collection('guestOrderGrants').doc(hash))]);
    const allowed = receipt.exists && receipt.get('ownerId') === snapshot.get('ownerId') && receipt.get('tokenHash') === hash
      || grant.exists && grant.get('orderId') === snapshot.id && time(grant.get('expiresAt')) > Date.now();
    if (allowed) { caller.uid = snapshot.get('ownerId'); caller.role = 'GUEST'; }
    return allowed;
  }
  route('list', ['ADMIN', 'SELLER', 'CUSTOMER'], async (caller, body) => {
    let query = db.collection('orders').where('expiryReviewRequired', '==', true);
    if (caller.role !== 'ADMIN') query = query.where(caller.role === 'SELLER' ? 'sellerId' : 'ownerId', '==', caller.uid);
    query = query.orderBy(admin.firestore.FieldPath.documentId()).limit(31);
    if (body.cursor) {
      if (!validId(body.cursor)) throw fail(400, 'Con trỏ không hợp lệ.');
      query = query.startAfter(body.cursor);
    }
    const snapshot = await query.get(), docs = snapshot.docs.slice(0, 30);
    return {items: docs.filter(d => owns(caller, d.data())).map(d => ({id: d.id,
      orderCode: d.get('orderCode'), status: d.get('status'), paymentStatus: d.get('paymentStatus')})),
      hasMore: snapshot.docs.length > 30, cursor: docs.length ? docs[docs.length - 1].id : ''};
  });
  route('get', ['ADMIN', 'SELLER', 'CUSTOMER'], async (caller, body) => {
    if (!validId(body.orderId)) throw fail(400, 'Mã đơn không hợp lệ.');
    const snapshot = await db.collection('orders').doc(body.orderId).get();
    if (!await canAccess(ref => ref.get(), caller, snapshot)) throw fail(403, 'Không có quyền xem đơn.');
    const o = snapshot.data();
    return {role: caller.role, order: {id: snapshot.id, orderCode: o.orderCode, status: o.status, paymentStatus: o.paymentStatus,
      paymentMethod: o.paymentMethod, totalAmount: o.totalAmount, expiryReviewRequired: o.expiryReviewRequired === true,
      handoverRecordedAt: time(o.handoverRecordedAt), handoverEvidenceUrl: o.handoverEvidenceUrl || '',
      buyerReceiptAcknowledgedAt: time(o.buyerReceiptAcknowledgedAt), returnRecordedAt: time(o.returnRecordedAt),
      returnEvidenceUrl: o.returnEvidenceUrl || '', reconciliationResolution: o.reconciliationResolution || null}};
  });
  route('evidence', ['SELLER', 'CUSTOMER'], async (caller, body) => {
    if (!validId(body.orderId) || !['HANDOVER', 'RETURNED', 'ACK_RECEIVED', 'REQUEST'].includes(body.action)) throw fail(400, 'Yêu cầu không hợp lệ.');
    return db.runTransaction(async tx => {
      const ref = db.collection('orders').doc(body.orderId), snapshot = await tx.get(ref);
      if (!await canAccess(ref => tx.get(ref), caller, snapshot)) throw fail(403, 'Không có quyền xử lý đơn.');
      const order = snapshot.data();
      if (order.inventoryFinalized || ['CANCELLED', 'DELIVERED'].includes(order.status)) throw fail(409, 'Đơn đã kết thúc.');
      const changes = {updatedAt: stamp()};
      if (body.action === 'HANDOVER') {
        if (caller.role === 'SELLER') {
          const seller = await tx.get(db.collection('users').doc(caller.uid));
          if (!seller.exists || seller.get('sellerStatus') !== 'APPROVED') throw fail(403, 'Seller chưa được duyệt.');
        }
        if (caller.role !== 'SELLER' || !['PREPARING', 'READY_FOR_PICKUP', 'SHIPPING'].includes(order.status)
            || !proof(body.evidenceUrl)) throw fail(400, 'Seller ghi bàn giao với bằng chứng HTTPS khi đơn đã được chuẩn bị.');
        if (order.paymentMethod === 'BANK_TRANSFER' && order.paymentStatus !== 'PAID') throw fail(409, 'Chờ SePay xác minh tiền.');
        if (order.handoverRecordedAt) return {ok: true, replay: true};
        const batches = await Promise.all(Object.keys(order.batchReservations || {}).map(id => tx.get(db.collection('productBatches').doc(id))));
        if (!batches.length || batches.some(b => !b.exists || time(b.get('expiryDate')) <= Date.now()
            || ['EXPIRED', 'ARCHIVED'].includes(b.get('status')))) throw fail(409, 'Không ghi lùi ngày bàn giao cho lô đã hết hạn.');
        Object.assign(changes, {handoverRecordedAt: stamp(), handoverEvidenceUrl: text(body.evidenceUrl)});
      } else if (body.action === 'RETURNED') {
        if (caller.role !== 'SELLER' || !proof(body.evidenceUrl) || !order.handoverRecordedAt && order.status !== 'SHIPPING')
          throw fail(400, 'Seller ghi thu hồi hàng đã bàn giao/đang giao với bằng chứng HTTPS.');
        if (order.returnRecordedAt) return {ok: true, replay: true};
        Object.assign(changes, {returnRecordedAt: stamp(), returnEvidenceUrl: text(body.evidenceUrl), expiryReviewRequired: true});
      } else if (body.action === 'ACK_RECEIVED') {
        if (!['CUSTOMER', 'GUEST'].includes(caller.role) || !order.handoverRecordedAt) throw fail(409, 'Cần có bản ghi bàn giao của Seller.');
        if (order.paymentMethod !== 'BANK_TRANSFER' && body.confirmPaid !== true) throw fail(400, 'Cần xác nhận đã thanh toán tiền mặt khi nhận hàng.');
        if (order.buyerReceiptAcknowledgedAt) return {ok: true, replay: true};
        changes.buyerReceiptAcknowledgedAt = stamp();
      } else changes.expiryReviewRequired = true;
      tx.update(ref, changes);
      tx.create(db.collection('orderReconciliationEvents').doc(), {orderId: snapshot.id, actorId: caller.uid,
        action: body.action, evidenceUrl: text(body.evidenceUrl), createdAt: stamp()});
      return {ok: true};
    });
  });
  route('resolve', ['ADMIN'], (caller, body) => campaignLifecycle.resolveOrder(caller, body));
  return {router};
}
module.exports = {createOrderReconciliation, validateResolution, resolutionFingerprint};
