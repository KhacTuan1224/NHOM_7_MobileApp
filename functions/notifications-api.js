const express = require('express');
const {enqueueNotification, digest} = require('./notification-outbox');
const text = v => typeof v === 'string' ? v.trim() : '';
const fail = (status, message) => Object.assign(Error(message), {status});
const collections = {ORDER: 'orders', SELLER_APPLICATION: 'sellerApplications', CAMPAIGN: 'campaigns', REPORT: 'reports', PRODUCT: 'products', PROMOTION: 'products', REVIEW: 'reviews', POST: 'posts', USER: 'users'};
const sections = {ORDER: 'ORDERS', SELLER_APPLICATION: 'SELLER_APPLICATIONS', CAMPAIGN: 'CAMPAIGNS', REPORT: 'REPORTS', PRODUCT: 'PRODUCTS', REVIEW: 'REVIEWS', POST: 'POSTS', USER: 'USERS'};
function createNotificationsApi({db, admin, authenticate}) {
  const router = express.Router();
  async function resolve(caller, id) {
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(id)) throw fail(400, 'Thông báo không hợp lệ.');
    const inbox = await db.collection('notifications').doc(id).get();
    if (!inbox.exists || inbox.get('userId') !== caller.uid) throw fail(403, 'Thông báo không thuộc tài khoản hiện tại.');
    const n = inbox.data(), referenceId = text(n.referenceId);
    let type = text(n.resourceType) || ({ORDER: 'ORDER', CAMPAIGN: 'CAMPAIGN', PROMOTION: 'PROMOTION'})[n.type];
    if (!type && caller.role === 'ADMIN' && n.type === 'MODERATION') {
      const candidates = await Promise.all(['CAMPAIGN', 'REPORT', 'SELLER_APPLICATION'].map(async type =>
        ({type, exists: (await db.collection(collections[type]).doc(referenceId).get()).exists})));
      const found = candidates.filter(c => c.exists);
      if (found.length === 1) type = found[0].type;
    }
    if (!type || !referenceId) return {destination: 'notificationFragment', args: {}, message: 'Thông báo cũ chưa có thông tin điều hướng cụ thể.'};
    if (!Object.hasOwn(collections, type)) throw fail(409, 'Loại nội dung thông báo chưa được hỗ trợ.');
    const source = await db.collection(collections[type]).doc(referenceId).get();
    if (!source.exists && type === 'SELLER_APPLICATION' && caller.role === 'SELLER' && referenceId === caller.uid)
      return {destination: 'sellerProfileFragment', args: {}, message: 'Kết quả được cập nhật trên hồ sơ tài khoản người bán.'};
    if (!source.exists) throw fail(404, 'Nội dung không còn tồn tại.');
    if (caller.role === 'ADMIN' && sections[type]) return {destination: 'adminDetailFragment', args: {section: sections[type], itemId: referenceId,
      itemTitle: source.get('title') || source.get('fullName') || 'Chi tiết quản trị', itemSubtitle: referenceId,
      itemStatus: source.get('status') || '', itemActive: false, itemImageUrl: ''}};
    if (type === 'REPORT') {
      if (source.get('reporterId') !== caller.uid) throw fail(403, 'Bạn không có quyền xem kết quả báo cáo này.');
      return {destination: 'reportResultFragment', args: {reportId: referenceId}};
    }
    if (type === 'SELLER_APPLICATION') {
      if (caller.role !== 'SELLER' || referenceId !== caller.uid) throw fail(403, 'Hồ sơ không thuộc tài khoản hiện tại.');
      return {destination: 'sellerApplicationFragment', args: {}};
    }
    if (type === 'ORDER') {
      if (!(caller.role === 'SELLER' && source.get('sellerId') === caller.uid)
          && !(caller.role === 'CUSTOMER' && source.get('ownerId') === caller.uid && source.get('ownerType') === 'CUSTOMER')) throw fail(403, 'Bạn không có quyền xem đơn.');
      return {destination: 'orderDetailFragment', args: {orderId: referenceId}};
    }
    const owner = source.get('sellerId') === caller.uid;
    if (type === 'CAMPAIGN' && (owner || source.get('status') === 'ACTIVE')) return {destination: 'campaignDetailFragment', args: {campaignId: referenceId}};
    if (['PRODUCT', 'PROMOTION'].includes(type) && (owner || source.get('status') === 'ACTIVE')) return {destination: 'productDetailFragment', args: {productId: referenceId}};
    if (type === 'USER' && referenceId === caller.uid) return {destination: 'profileFragment', args: {}};
    if (type === 'REVIEW' && source.get('customerId') === caller.uid) return {destination: 'reviewFragment', args: {productId: source.get('productId')}};
    if (type === 'POST' && (owner || source.get('status') === 'PUBLISHED')) return {destination: 'postDetailFragment', args: {postId: referenceId}};
    throw fail(403, 'Nội dung không khả dụng với tài khoản hiện tại.');
  }
  router.post('/notifications/resolve', async (req, res, next) => {
    try { res.json(await resolve(await authenticate(req, ['ADMIN', 'SELLER', 'CUSTOMER']), text((req.body || {}).notificationId))); } catch (e) { next(e); }
  });
  router.post('/notifications/read', async (req, res, next) => {
    try {
      const caller = await authenticate(req, ['ADMIN', 'SELLER', 'CUSTOMER']);
      const ref = db.collection('notifications').doc(text((req.body || {}).notificationId));
      await db.runTransaction(async tx => {
        const doc = await tx.get(ref);
        if (!doc.exists || doc.get('userId') !== caller.uid) throw fail(403, 'Thông báo không thuộc tài khoản hiện tại.');
        if (!doc.get('isRead')) tx.update(ref, {isRead: true, readAt: admin.firestore.FieldValue.serverTimestamp()});
      }); res.json({ok: true});
    } catch (e) { next(e); }
  });
  router.post('/reports/mine', async (req, res, next) => {
    try {
      const caller = await authenticate(req, ['CUSTOMER', 'SELLER', 'ADMIN']);
      const id = text((req.body || {}).reportId);
      let docs;
      if (id) {
        const doc = await db.collection('reports').doc(id).get();
        if (!doc.exists || doc.get('reporterId') !== caller.uid) throw fail(403, 'Không có quyền xem báo cáo.');
        docs = [doc];
      } else {
        let query = db.collection('reports').where('reporterId', '==', caller.uid).orderBy('createdAt', 'desc').limit(50);
        if (text((req.body || {}).cursor)) {
          const cursor = await db.collection('reports').doc(text((req.body || {}).cursor)).get();
          if (!cursor.exists || cursor.get('reporterId') !== caller.uid) throw fail(403, 'Phân trang không hợp lệ.');
          query = query.startAfter(cursor);
        }
        docs = (await query.get()).docs;
      }
      res.json({nextCursor: !id && docs.length === 50 ? docs[docs.length - 1].id : '', reports: docs.map(doc => ({id: doc.id, targetType: doc.get('targetType'), status: doc.get('status'),
        reason: doc.get('reason'), description: doc.get('description'), targetId: doc.get('targetId'),
        parentType: doc.get('parentType') || '', parentId: doc.get('parentId') || '', targetPath: doc.get('targetPath') || '',
        publicResult: doc.get('status') === 'RESOLVED' ? 'Báo cáo đã được xử lý. Cảm ơn bạn đã góp phần bảo vệ cộng đồng.'
          : doc.get('status') === 'REJECTED' ? 'Báo cáo chưa đủ cơ sở để áp dụng xử lý.' : 'Báo cáo đang được tiếp nhận và xem xét.'}))});
    } catch (e) { next(e); }
  });
  // Backward compatible retries only enqueue; they never perform FCM in the HTTP mutation request.
  router.post('/notify-admins', async (req, res, next) => {
    try {
      const caller = await authenticate(req, ['SELLER', 'CUSTOMER']), id = text(req.body.referenceId);
      const type = req.body.eventType === 'REPORT' ? 'REPORT' : req.body.eventType === 'SELLER_APPLICATION' ? 'SELLER_APPLICATION' : text(req.body.resourceType);
      if (!['REPORT', 'SELLER_APPLICATION', 'CAMPAIGN', 'POST'].includes(type)) throw fail(400, 'Loại nội dung không hợp lệ.');
      const source = await db.collection(collections[type]).doc(id).get();
      if (!source.exists || (source.get('sellerId') || source.get('reporterId') || source.id) !== caller.uid
          || !['PENDING', 'PENDING_APPROVAL'].includes(source.get('status'))) throw fail(403, 'Nội dung không thuộc người gửi hoặc không chờ duyệt.');
      const version = source.get('updatedAt') || source.get('submittedAt');
      if (!version) throw fail(409, 'Nội dung cũ thiếu ngày gửi; cần đối soát.');
      const existing = await db.collection('notificationOutbox').where('resourceId', '==', id).get();
      if (existing.docs.some(event => event.get('resourceType') === type && event.get('kind') === 'ADMINS'
          && (() => { const at = event.get('sourceUpdatedAt') || event.get('createdAt'); return at && at.seconds === version.seconds && at.nanoseconds === version.nanoseconds; })()))
        return res.json({ok: true, queued: true, duplicate: true});
      const eventId = 'legacy_' + digest(`${collections[type]}|${id}|${version.seconds ?? version.toMillis()}|${version.nanoseconds ?? 0}`);
      await db.runTransaction(async tx => {
        const ref = db.collection('notificationOutbox').doc(eventId); if ((await tx.get(ref)).exists) return;
        enqueueNotification(tx, db, admin, eventId, {kind: 'ADMINS', resourceType: type, resourceId: id, sourceUpdatedAt: version});
      }); res.json({ok: true, queued: true});
    } catch (e) { next(e); }
  });
  router.post('/send-notification', async (req, res, next) => {
    try {
      const caller = await authenticate(req, ['SELLER', 'CUSTOMER', 'ADMIN']);
      const type = text(req.body.type), id = text(req.body.referenceId);
      if (!['ORDER', 'CAMPAIGN', 'PROMOTION'].includes(type)) throw fail(400, 'Thông báo hệ thống phải xuất phát từ sự kiện nghiệp vụ.');
      const name = type === 'PROMOTION' ? 'promotions' : collections[type];
      const source = await db.collection(name).doc(id).get();
      if (!source.exists || caller.role !== 'ADMIN' && ![source.get('sellerId'), source.get('ownerId')].includes(caller.uid)) throw fail(403, 'Không có quyền gửi thông báo này.');
      const version = source.get('updatedAt') || source.get('createdAt');
      if (!version) throw fail(409, 'Nội dung cũ thiếu ngày cập nhật; cần đối soát.');
      if (type === 'PROMOTION' && (!source.get('active') || source.get('endDate').toMillis() <= Date.now())
          || type === 'CAMPAIGN' && source.get('status') !== 'ACTIVE') return res.json({ok: true, skipped: true});
      if (type === 'ORDER' && source.get('ownerType') !== 'CUSTOMER') return res.json({ok: true, skipped: true});
      const payment = type === 'ORDER' && source.get('paymentMethod') === 'BANK_TRANSFER' && source.get('paymentStatus') === 'PAID';
      const decision = payment ? 'PAYMENT_PAID' : source.get('status');
      const events = await db.collection('notificationOutbox').where('resourceId', '==', id).get();
      const existing = events.docs.some(event => event.get('resourceType') === type && (type === 'CAMPAIGN' && event.get('kind') === 'CAMPAIGN'
        || type === 'ORDER' && (payment ? event.get('kind') === 'ORDER_PAYMENT' || event.get('decision') === decision
          : event.get('decision') === decision && event.get('recipientUid') === source.get('ownerId'))
        || type === 'PROMOTION' && (() => { const at = event.get('sourceUpdatedAt') || event.get('createdAt');
          return at && at.seconds === version.seconds && at.nanoseconds === version.nanoseconds; })()));
      if (!existing) {
        const eventId = 'compat_' + digest(`${type}|${id}|${version.seconds}|${version.nanoseconds}|${decision || ''}`);
        await db.runTransaction(async tx => {
          const ref = db.collection('notificationOutbox').doc(eventId); if ((await tx.get(ref)).exists) return;
          const data = {kind: type === 'ORDER' ? 'DIRECT' : type, resourceType: type, resourceId: id, sourceUpdatedAt: version};
          if (type === 'ORDER') Object.assign(data, {recipientUid: source.get('ownerId'), decision,
            ...(payment ? {title: 'Thanh toán đã xác nhận', publicMessage: 'Cửa hàng đã xác nhận thanh toán chuyển khoản.'} : {inboxId: `${id}_${decision}`})});
          enqueueNotification(tx, db, admin, eventId, data);
        });
      }
      res.json({ok: true, queued: true, duplicate: existing});
    } catch (e) { next(e); }
  });
  return {router, resolve};
}
module.exports = {createNotificationsApi};
