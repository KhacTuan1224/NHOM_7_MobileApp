const crypto = require('node:crypto');
const {isQuotaError} = require('./read-cache');
const digest = value => crypto.createHash('sha256').update(value).digest('hex');
const time = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
function compareTimestamp(a, b) {
  if (a && b && Number.isFinite(a.seconds) && Number.isFinite(b.seconds))
    return a.seconds - b.seconds || a.nanoseconds - b.nanoseconds;
  return time(a) - time(b);
}
const text = value => typeof value === 'string' ? value.trim() : '';
const decisionLabel = value => ({PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị', SHIPPING: 'Đang giao',
  READY_FOR_PICKUP: 'Sẵn sàng nhận', DELIVERED: 'Đã nhận hàng', CANCELLED: 'Đã hủy', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối',
  ACTIVE: 'Đang hoạt động', STOPPED: 'Đã dừng', HIDDEN: 'Đã ẩn', BLOCKED: 'Đã khóa'})[value] || value;

function enqueueNotification(tx, db, admin, id, event) {
  tx.set(db.collection('notificationOutbox').doc(id), {...event, status: 'PENDING', retryCount: 0,
    nextAttemptAt: admin.firestore.FieldValue.serverTimestamp(), createdAt: admin.firestore.FieldValue.serverTimestamp()});
}

function createNotificationWorker({db, admin, messaging = () => admin.messaging(), now = Date.now}) {
  const stamp = () => admin.firestore.FieldValue.serverTimestamp();
  const ts = value => admin.firestore.Timestamp.fromMillis(value);
  const update = (ref, data) => db.runTransaction(async tx => { await tx.get(ref); tx.update(ref, data); });
  const admins = async () => (await db.collection('users').where('role', '==', 'ADMIN').get()).docs
    .filter(doc => doc.get('status') === 'ACTIVE').map(doc => doc.id);

  async function nearbyAllowed(uid, offerId) {
    const [preference, offer] = await Promise.all([db.collection('rescueNearbyPreferences').doc(uid).get(), db.collection('rescueOffers').doc(offerId).get()]);
    if (!preference.exists || !preference.get('enabled') || time(preference.get('expiresAt')) <= now()
        || !offer.exists || offer.get('status') !== 'OPEN' || offer.get('closesAt') <= now() || offer.get('availableUnits') <= 0) return false;
    const seller = await db.collection('users').doc(offer.get('sellerId')).get();
    return seller.exists && seller.get('status') === 'ACTIVE' && seller.get('sellerStatus') === 'APPROVED'
      && require('./rescue-policy').distanceKm(preference.data(), offer.data()) <= preference.get('radiusKm');
  }

  async function audience(event) {
    if (event.resourceType === 'RESCUE_OFFER' && !text(event.resourceId)) return [];
    if (event.kind === 'RESCUE_NEARBY') return await nearbyAllowed(event.recipientUid, event.resourceId) ? [event.recipientUid] : [];
    if (event.kind === 'DIRECT') return [event.recipientUid];
    if (event.kind === 'ORDER_PAYMENT') {
      const order = await db.collection('orders').doc(event.resourceId).get();
      return order.exists && order.get('ownerType') === 'CUSTOMER' ? [order.get('ownerId')] : [];
    }
    if (event.kind === 'ADMINS') {
      const name = {CAMPAIGN: 'campaigns', REPORT: 'reports', SELLER_APPLICATION: 'sellerApplications'}[event.resourceType];
      if (name) {
        const source = await db.collection(name).doc(event.resourceId).get();
        if (!source.exists || !['PENDING', 'PENDING_APPROVAL'].includes(source.get('status'))
            || compareTimestamp(source.get('updatedAt'), event.sourceUpdatedAt || event.createdAt) !== 0) return [];
      }
      return admins();
    }
    if (event.kind === 'CAMPAIGN') {
      const doc = await db.collection('campaigns').doc(event.resourceId).get();
      if (!doc.exists || doc.get('status') !== 'ACTIVE' || time(doc.get('endDate')) <= now()) return [];
      if (time(doc.get('startDate')) > now()) throw Object.assign(new Error('Campaign scheduled'), {nextAt: time(doc.get('startDate'))});
      const batches = await Promise.all(Object.keys(doc.get('batchTargets') || {}).map(id => db.collection('productBatches').doc(id).get()));
      const favorites = await Promise.all([...new Set(batches.filter(b => b.exists).map(b => b.get('productId')))]
        .map(id => db.collection('favorites').where('productId', '==', id).get()));
      return [...new Set(favorites.flatMap(s => s.docs.map(d => d.get('customerId'))))];
    }
    return [];
  }

  function payload(event) {
    const resourceType = event.resourceType, referenceId = event.resourceId;
    const titles = {ORDER: 'Cập nhật đơn hàng', CAMPAIGN: 'Cập nhật chiến dịch',
      SELLER_APPLICATION: 'Kết quả hồ sơ người bán', REPORT: 'Cập nhật báo cáo', PRODUCT: 'Kết quả kiểm duyệt sản phẩm', REVIEW: 'Kết quả kiểm duyệt đánh giá', USER: 'Cập nhật tài khoản'};
    const pending = event.kind === 'ADMINS';
    return {type: resourceType === 'RESCUE_OFFER' ? 'SYSTEM' : resourceType === 'ORDER' ? 'ORDER' : event.kind === 'CAMPAIGN' ? 'CAMPAIGN' : 'MODERATION',
      resourceType, referenceId, action: pending ? 'REVIEW' : event.kind === 'CAMPAIGN' ? 'VIEW' : 'RESULT',
      title: text(event.title) || (pending ? 'Có nội dung mới cần xử lý' : titles[resourceType] || 'Cập nhật RescueFarm'),
      body: text(event.publicMessage) || (event.kind === 'ORDER_PAYMENT' ? 'Cửa hàng đã xác nhận thanh toán chuyển khoản.' : pending ? 'Mở để xem và xử lý nội dung.' : `Trạng thái: ${decisionLabel(text(event.decision)) || 'Đã cập nhật'}`)};
  }

  async function expand(doc) {
    const event = doc.data();
    try {
      const recipients = (await audience(event)).filter(Boolean), data = payload(event);
      for (const uid of recipients) {
        const user = await db.collection('users').doc(uid).get();
        if (!user.exists || ((event.kind === 'CAMPAIGN') && user.get('role') !== 'CUSTOMER')) continue;
        const id = digest(doc.id + '|' + uid), ref = db.collection('notificationDeliveries').doc(id);
        await db.runTransaction(async tx => {
          if ((await tx.get(ref)).exists) return;
          tx.create(ref, {...data, audiencePolicy: event.kind === 'RESCUE_NEARBY' ? 'RESCUE_NEARBY' : '', id: event.inboxId || id, eventId: doc.id, sourceUpdatedAt: event.sourceUpdatedAt || event.createdAt, userId: uid, status: 'PENDING', inboxCreated: false,
            pushAttempted: false, pushDelivered: false, deliveredTokenIds: [], retryCount: 0, nextAttemptAt: ts(now()), createdAt: stamp()});
        });
      }
      await update(doc.ref, {status: 'EXPANDED', recipientCount: recipients.length, expandedAt: stamp()});
      return {expanded: true};
    } catch (error) {
      if (isQuotaError(error)) throw error;
      await update(doc.ref, {retryCount: (event.retryCount || 0) + (error.nextAt ? 0 : 1), lastError: String(error.message).slice(0, 500),
        nextAttemptAt: ts(error.nextAt || now() + Math.min(3600000, 10000 * 2 ** Math.min(event.retryCount || 0, 8)))});
      return {attentionRequired: !error.nextAt && (event.retryCount || 0) + 1 >= 5};
    }
  }

  async function deliver(ref) {
    const leaseId = crypto.randomUUID();
    const delivery = await db.runTransaction(async tx => {
      const doc = await tx.get(ref); if (!doc.exists) return null;
      const data = doc.data();
      if (['SENT', 'SKIPPED'].includes(data.status) || time(data.nextAttemptAt) > now() || time(data.leaseUntil) > now()) return null;
      tx.update(ref, {leaseId, leaseUntil: ts(now() + 180000)}); return data;
    });
    if (!delivery) return;
    const ownedUpdate = data => db.runTransaction(async tx => {
      const current = await tx.get(ref);
      if (!current.exists || current.get('leaseId') !== leaseId) return false;
      tx.update(ref, data); return true;
    });
    const inbox = db.collection('notifications').doc(delivery.id);
    try {
      if (delivery.resourceType === 'RESCUE_OFFER' && !text(delivery.referenceId)) {
        await ownedUpdate({status: 'SKIPPED', lastError: 'Thông báo không còn gói giải cứu để mở.', leaseUntil: ts(0)});
        return {skipped: true};
      }
      if (delivery.audiencePolicy === 'RESCUE_NEARBY' && !await nearbyAllowed(delivery.userId, delivery.referenceId)) {
        await ownedUpdate({status: 'SKIPPED', lastError: 'Đã tắt nhắc, hết hạn vị trí hoặc gói không còn phù hợp.', leaseUntil: ts(0)});
        return {skipped: true};
      }
      await db.runTransaction(async tx => {
        const [existing, current] = await Promise.all([tx.get(inbox), tx.get(ref)]);
        if (current.get('leaseId') !== leaseId) throw Error('Delivery lease changed');
        if (!existing.exists) tx.create(inbox, {id: delivery.id, userId: delivery.userId, type: delivery.type,
          title: delivery.title, body: delivery.body, referenceId: delivery.referenceId, resourceType: delivery.resourceType,
          action: delivery.action, eventId: delivery.eventId, isRead: false, createdAt: delivery.createdAt});
        else tx.update(inbox, {resourceType: delivery.resourceType, action: delivery.action, eventId: delivery.eventId});
        tx.update(ref, {inboxCreated: true});
      });
      if (delivery.action === 'VIEW' && delivery.resourceType === 'CAMPAIGN') {
        const source = await db.collection('campaigns').doc(delivery.referenceId).get();
        if (!source.exists || time(source.get('endDate')) <= now()
            || source.get('status') !== 'ACTIVE') {
          await ownedUpdate({status: 'SKIPPED', lastError: 'Nội dung ưu đãi/chiến dịch đã kết thúc trước khi gửi push.', leaseUntil: ts(0)});
          return {skipped: true};
        }
        if (time(source.get('startDate')) > now()) {
          await ownedUpdate({status: 'PENDING', nextAttemptAt: source.get('startDate'), leaseUntil: ts(0)});
          return {scheduled: true};
        }
      }
      const user = await db.collection('users').doc(delivery.userId).get();
      const devices = await db.collection('users').doc(delivery.userId).collection('devices').get();
      const delivered = new Set(delivery.deliveredTokenIds || []);
      const candidates = devices.docs.filter(d => text(d.get('token')) && !delivered.has(digest(d.get('token'))));
      if (user.get('status') !== 'ACTIVE' || !candidates.length && !delivered.size)
        throw Object.assign(Error('Waiting for an active account/device'), {wait: true});
      let transient = false;
      for (let i = 0; i < candidates.length; i += 500) {
        const group = candidates.slice(i, i + 500);
        if (!await ownedUpdate({pushAttempted: true, lastAttemptAt: stamp(), leaseUntil: ts(now() + 180000)})) return;
        let timer;
        const payload = {tokens: group.map(d => d.get('token')),
          data: {notificationId: delivery.id, recipientUid: delivery.userId, title: delivery.title, body: delivery.body,
            type: delivery.type, resourceType: delivery.resourceType, referenceId: delivery.referenceId, action: delivery.action}, android: {priority: 'high'}};
        let result;
        try {
          result = await Promise.race([messaging().sendEachForMulticast(payload),
            new Promise((_, reject) => { timer = setTimeout(() => reject(Error('FCM attempt timed out')), 30000); })]);
        } finally { clearTimeout(timer); }
        for (let j = 0; j < group.length; j++) {
          const response = result.responses[j], device = group[j];
          if (response.success) delivered.add(digest(device.get('token')));
          else if (['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(response.error && response.error.code))
            await device.ref.delete();
          else transient = true;
        }
        if (!await ownedUpdate({deliveredTokenIds: [...delivered]})) return;
      }
      if (transient || !delivered.size) throw Error('FCM delivery needs retry');
      await ownedUpdate({status: 'SENT', pushDelivered: true, deliveredAt: stamp(), lastError: '', leaseUntil: ts(0)});
      return {sent: true};
    } catch (error) {
      if (isQuotaError(error)) throw error;
      await ownedUpdate({status: 'PENDING', retryCount: (delivery.retryCount || 0) + (error.wait ? 0 : 1),
        lastError: String(error.message).slice(0, 500), leaseUntil: ts(0),
        nextAttemptAt: ts(now() + (error.wait ? 3600000 : Math.min(3600000, 10000 * 2 ** Math.min(delivery.retryCount || 0, 8))))});
      return {attentionRequired: !error.wait && (delivery.retryCount || 0) + 1 >= 5};
    }
  }

  // Audit records are immutable events committed in the same transaction as Admin decisions.
  async function auditDecisions() {
    const cursorRef = db.collection('notificationWorkerState').doc('auditCursor');
    const cursor = await cursorRef.get();
    let query = db.collection('adminAuditLogs').orderBy('createdAt').orderBy(admin.firestore.FieldPath.documentId()).limit(100);
    if (cursor.exists) query = query.startAfter(cursor.get('at'), cursor.get('id'));
    const page = await query.get();
    for (const doc of page.docs) {
      const value = doc.data(), types = {SELLER_APPLICATIONS: 'SELLER_APPLICATION', CAMPAIGNS: 'CAMPAIGN', PRODUCTS: 'PRODUCT', REVIEWS: 'REVIEW', REPORTS: 'REPORT', USERS: 'USER'};
      const resourceType = types[value.resourceType], collection = {SELLER_APPLICATION: 'sellerApplications', CAMPAIGN: 'campaigns', PRODUCT: 'products', REVIEW: 'reviews', REPORT: 'reports', USER: 'users'}[resourceType];
      const source = collection ? await db.collection(collection).doc(value.resourceId).get() : null;
      const data = source && source.exists ? source.data() : {};
      const uid = resourceType === 'SELLER_APPLICATION' || resourceType === 'USER' ? value.resourceId
        : resourceType === 'REPORT' ? data.reporterId : data.sellerId || data.customerId;
      await db.runTransaction(async tx => {
        const current = await tx.get(cursorRef);
        if (current.exists && (compareTimestamp(current.get('at'), value.createdAt) > 0
            || compareTimestamp(current.get('at'), value.createdAt) === 0 && current.get('id') >= doc.id)) return;
        if (uid && value.fromState !== value.toState && (!['REPORT'].includes(resourceType) || ['RESOLVED', 'REJECTED'].includes(value.toState)))
          enqueueNotification(tx, db, admin, 'audit_' + doc.id, {kind: 'DIRECT', resourceType, resourceId: value.resourceId,
            recipientUid: uid, decision: value.toState,
            publicMessage: resourceType === 'REPORT' ? `Báo cáo của bạn đã được ${value.toState === 'RESOLVED' ? 'xử lý' : 'từ chối'}. Mở để xem kết quả công khai.`
              : text(value.reportId) ? `Kết quả: ${decisionLabel(value.toState)}. Nội dung đã được xử lý theo chính sách cộng đồng.`
              : `Kết quả: ${decisionLabel(value.toState)}${text(value.reason) ? '. Lý do: ' + text(value.reason).slice(0, 500) : ''}`});
        tx.set(cursorRef, {at: value.createdAt, id: doc.id, updatedAt: stamp()});
      });
    }
  }

  // One-time compatibility migration for pending moderation work.
  async function backfillSources() {
    const marker = db.collection('notificationWorkerState').doc('sourceBackfillV1');
    if ((await marker.get()).exists) return;
    for (const [name, resourceType] of [['sellerApplications', 'SELLER_APPLICATION'], ['campaigns', 'CAMPAIGN'], ['reports', 'REPORT']]) {
      let cursor = null;
      while (true) {
        let query = db.collection(name).orderBy(admin.firestore.FieldPath.documentId()).limit(100);
        if (cursor) query = query.startAfter(cursor);
        const page = await query.get(); if (page.empty) break;
        for (const source of page.docs) {
          const version = source.get('updatedAt');
          const pending = ['PENDING', 'PENDING_APPROVAL'].includes(source.get('status'));
          if (!pending) continue;
          if (!version) { console.error('Notification source needs date reconciliation:', name, source.id); continue; }
          const existingEvents = await db.collection('notificationOutbox').where('resourceId', '==', source.id).get();
          if (existingEvents.docs.some(event => event.get('resourceType') === resourceType
              && compareTimestamp(event.get('sourceUpdatedAt') || event.get('createdAt'), version) === 0)) continue;
          const id = 'legacy_' + digest(`${name}|${source.id}|${version.seconds ?? time(version)}|${version.nanoseconds ?? 0}`);
          await db.runTransaction(async tx => {
            const event = await tx.get(db.collection('notificationOutbox').doc(id));
            const current = await tx.get(source.ref);
            if (event.exists || !current.exists || compareTimestamp(current.get('updatedAt'), version) !== 0) return;
            enqueueNotification(tx, db, admin, id, {kind: 'ADMINS', resourceType,
              resourceId: source.id, sourceUpdatedAt: version});
          });
        }
        cursor = page.docs[page.docs.length - 1];
      }
    }
    await db.runTransaction(async tx => { tx.set(marker, {completedAt: stamp()}); });
  }

  let running = false;
  async function runOnce() {
    if (running) return {busy: true}; running = true;
    try {
      let attentionRequired = false;
      try { await backfillSources(); await auditDecisions(); }
      catch (error) {
        if (isQuotaError(error)) throw error;
        attentionRequired = true; console.error('Audit notification stream needs retry:', error.message);
      }
      const events = await db.collection('notificationOutbox').where('status', '==', 'PENDING').where('nextAttemptAt', '<=', ts(now())).limit(25).get();
      for (const doc of events.docs) { const result = await expand(doc); attentionRequired = attentionRequired || !!(result && result.attentionRequired); }
      const deliveries = await db.collection('notificationDeliveries').where('status', '==', 'PENDING').where('nextAttemptAt', '<=', ts(now())).limit(20).get();
      for (let offset = 0; offset < deliveries.docs.length; offset += 5) {
        const results = await Promise.all(deliveries.docs.slice(offset, offset + 5).map(async doc => {
          try { return await deliver(doc.ref); }
          catch (error) {
            if (isQuotaError(error)) throw error;
            console.error('Notification delivery needs retry:', doc.id, error.message); return {attentionRequired: true};
          }
        }));
        attentionRequired = attentionRequired || results.some(result => result && result.attentionRequired);
      }
      const stalled = await Promise.all(['notificationOutbox', 'notificationDeliveries'].map(name => db.collection(name)
        .where('status', '==', 'PENDING').where('retryCount', '>=', 5).limit(1).get()));
      attentionRequired = attentionRequired || stalled.some(snapshot => !snapshot.empty);
      await updateHeartbeat(attentionRequired); return {events: events.size, deliveries: deliveries.size, attentionRequired};
    } finally { running = false; }
  }
  async function updateHeartbeat(attentionRequired) {
    await db.runTransaction(async tx => { tx.set(db.collection('notificationWorkerState').doc('health'), {lastSuccessAt: stamp(), attentionRequired}); });
  }
  return {runOnce, expand, deliver, auditDecisions, backfillSources};
}
module.exports = {enqueueNotification, createNotificationWorker, digest};
