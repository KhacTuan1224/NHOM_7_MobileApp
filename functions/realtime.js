const crypto = require('node:crypto');
const express = require('express');
const {invalidateReadCaches} = require('./read-cache');
const text = value => typeof value === 'string' ? value.trim() : '';
const id = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const fail = (status, message) => Object.assign(new Error(message), {status});
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;

/** Push invalidations only. Never publishes business documents or bypasses API authorization. */
function createRealtimeApi({db, admin, authenticate, now = Date.now, maxSessions = 128,
    leaseMs = 120000, debounceMs = 100, onError = () => {}}) {
  const router = express.Router(), sessions = new Map(), watches = new Map();
  const doc = (collection, key) => ({collection, id: key});
  const where = (collection, field, value) => ({collection, field, value});
  const all = collection => ({collection});

  async function plan(body, caller) {
    const scope = text(body.scope), uid = caller && caller.uid, role = caller && caller.role;
    const result = [];
    if (scope === 'rescue') {
      result.push(where('rescueOffers', 'status', 'OPEN'), where('products', 'status', 'ACTIVE'),
        where('users', 'role', 'SELLER'));
      if (id(body.offerId)) result.push(doc('rescueOffers', body.offerId));
      if (role === 'SELLER') result.push(where('rescueOffers', 'sellerId', uid),
        where('rescueClaims', 'sellerId', uid), where('products', 'sellerId', uid));
      else if (role === 'CUSTOMER') result.push(where('rescueClaims', 'customerId', uid));
      else if (role === 'ADMIN') result.push(all('rescueOffers'), all('rescueClaims'));
      if (id(body.sellerId)) result.push(doc('rescueImpact', body.sellerId),
        {collection: 'rescueClaims', filters: [['sellerId', '==', body.sellerId], ['status', '==', 'COMPLETED']]});
    } else if (scope === 'campaign_cards') {
      result.push(where('campaigns', 'status', 'ACTIVE'), where('products', 'status', 'ACTIVE'),
        where('users', 'role', 'SELLER'), where('sellerApplications', 'status', 'APPROVED'), all('sellerPublicProfiles'));
    } else if (scope === 'checkout') {
      if (!Array.isArray(body.lines) || body.lines.length > 50) throw fail(400, 'Giỏ hàng không hợp lệ.');
      result.push(doc('appConfig', 'shipping'));
      const sellers = new Set(), products = new Set(), batches = new Set(), campaigns = new Set();
      for (const line of body.lines) {
        if (!line || !id(line.productId) || !id(line.batchId)) throw fail(400, 'Dòng giỏ hàng không hợp lệ.');
        products.add(line.productId); batches.add(line.batchId);
      }
      const rows = await Promise.all([...products].map(key => db.collection('products').doc(key).get()));
      const stock = await Promise.all([...batches].map(key => db.collection('productBatches').doc(key).get()));
      for (const row of stock) if (row.exists && id(row.get('activeCampaignId'))) campaigns.add(row.get('activeCampaignId'));
      for (const row of rows) if (row.exists && id(row.get('sellerId'))) sellers.add(row.get('sellerId'));
      for (const key of products) result.push(doc('products', key));
      for (const key of batches) result.push(doc('productBatches', key));
      for (const key of campaigns) result.push(doc('campaigns', key));
      for (const key of sellers) result.push(doc('sellerPublicProfiles', key), doc('sellerPaymentProfiles', key), doc('users', key));
    } else if (scope === 'reports') {
      if (!uid) throw fail(401, 'Cần đăng nhập để theo dõi báo cáo.');
      result.push(where('reports', 'reporterId', uid));
    } else if (scope === 'guest_order') {
      if (!id(body.orderId) || !/^[a-f0-9]{64}$/i.test(text(body.accessToken))) throw fail(403, 'Cần tra cứu lại đơn Guest.');
      const digest = hash(body.accessToken);
      const [receipt, grant] = await Promise.all([db.collection('checkoutReceipts').doc(body.orderId).get(),
        db.collection('guestOrderGrants').doc(digest).get()]);
      const owner = receipt.exists && receipt.get('ownerType') === 'GUEST' && receipt.get('tokenHash') === digest;
      const lookup = grant.exists && grant.get('orderId') === body.orderId && millis(grant.get('expiresAt')) > now();
      if (!owner && !lookup) throw fail(403, 'Cần tra cứu lại đơn Guest.');
      result.push(doc('orders', body.orderId), doc('payments', body.orderId), doc('shipments', body.orderId),
        {collection: `orders/${body.orderId}/items`});
    } else if (scope === 'seller_statistics') {
      if (role !== 'SELLER') throw fail(403, 'Cần quyền người bán.');
      result.push({...where('orders', 'sellerId', uid), children: 'items'}, where('campaigns', 'sellerId', uid),
        where('products', 'sellerId', uid), doc('users', uid));
    } else if (scope === 'admin_dashboard') {
      if (role !== 'ADMIN') throw fail(403, 'Cần quyền quản trị.');
      for (const collection of ['users', 'products', 'orders', 'campaigns', 'reports', 'sellerApplications',
        'categories', 'banners', 'reviews', 'productBatches']) result.push(all(collection));
    } else throw fail(400, 'Khu vực theo dõi không hợp lệ.');
    return result;
  }

  function release(session) {
    if (!sessions.delete(session.id)) return;
    clearTimeout(session.timer);
    for (const key of [...session.keys]) releaseKey(key, session);
    db.collection('realtimeSubscriptions').doc(session.id).delete().catch(onError);
  }
  function releaseKey(key, session) {
    session.keys.delete(key);
    const entry = watches.get(key); if (!entry) return;
    entry.subscribers.delete(session.id);
    if (!entry.subscribers.size) { entry.unsubscribe(); watches.delete(key); }
  }
  function changed(session, error = false) {
    if (!sessions.has(session.id)) return;
    session.dirty = true; session.failed = session.failed || error;
    if (session.timer || session.writing) return;
    session.timer = setTimeout(async () => {
      session.timer = null;
      if (!sessions.has(session.id)) return;
      session.writing = true; session.dirty = false;
      try {
        await db.collection('realtimeSubscriptions').doc(session.id).update({revision: crypto.randomUUID(),
          unavailable: session.failed, updatedAt: admin.firestore.FieldValue.serverTimestamp()});
      } catch (failure) { onError(failure); }
      finally {
        session.writing = false;
        if (session.dirty && sessions.has(session.id)) changed(session, session.failed);
      }
    }, debounceMs);
    session.timer.unref?.();
  }
  function acquire(spec, session) {
    const key = JSON.stringify(spec);
    if (session.keys.has(key)) return;
    let entry = watches.get(key);
    if (!entry) {
      let target = db.collection(spec.collection);
      if (spec.id) target = target.doc(spec.id);
      if (spec.field) target = target.where(spec.field, '==', spec.value);
      for (const [field, operator, value] of spec.filters || []) target = target.where(field, operator, value);
      entry = {subscribers: new Set(), children: new Map(), failed: false, initialized: false, unsubscribe: () => {}}; watches.set(key, entry);
      entry.unsubscribe = target.onSnapshot(snapshot => {
        // The initial snapshot establishes a baseline, not a database mutation.
        // Opening an unrelated screen must not evict every warm API cache.
        if (entry.initialized) invalidateReadCaches();
        entry.initialized = true;
        if (spec.children) {
          const children = new Map((snapshot.docs || []).map(row => {
            const child = {collection: spec.collection + '/' + row.id + '/' + spec.children};
            return [JSON.stringify(child), child];
          }));
          for (const subscriber of entry.subscribers) {
            const active = sessions.get(subscriber); if (!active) continue;
            for (const previous of entry.children.keys()) if (!children.has(previous)) releaseKey(previous, active);
            for (const child of children.values()) acquire(child, active);
          }
          entry.children = children;
        }
        for (const subscriber of entry.subscribers) { const active = sessions.get(subscriber); if (active) changed(active); }
      }, error => {
        entry.failed = true;
        onError(error);
        for (const subscriber of entry.subscribers) { const active = sessions.get(subscriber); if (active) changed(active, true); }
      });
    }
    entry.subscribers.add(session.id); session.keys.add(key);
    for (const child of entry.children.values()) acquire(child, session);
    if (entry.failed) changed(session, true);
  }
  const route = (path, action) => router.post(path, async (req, res, next) => {
    try { res.set('Cache-Control', 'no-store').json(await action(req)); } catch (error) { next(error); }
  });
  route('/realtime/watch', async req => {
    const body = req.body || {}, caller = req.get('authorization') ? await authenticate(req) : null;
    const subject = caller ? 'user:' + caller.uid : 'ip:' + (req.ip || 'unknown');
    function capacity() {
      if (sessions.size >= maxSessions || [...sessions.values()].filter(s => s.subject === subject).length >= 8)
        throw fail(429, 'Đã có quá nhiều màn hình đang theo dõi.');
    }
    capacity();
    const specs = await plan(body, caller);
    capacity();
    const session = {id: crypto.randomUUID(), token: crypto.randomBytes(32).toString('hex'), subject,
      caller, keys: new Set(), expiresAt: now() + leaseMs, timer: null, dirty: false, writing: false, failed: false};
    session.authorization = text(body.scope) === 'guest_order' ? {orderId: body.orderId, accessToken: body.accessToken} : null;
    sessions.set(session.id, session);
    try {
      await db.collection('realtimeSubscriptions').doc(session.id).set({revision: crypto.randomUUID(),
        unavailable: false, updatedAt: admin.firestore.FieldValue.serverTimestamp()});
      for (const spec of specs) acquire(spec, session);
    } catch (error) { release(session); throw error; }
    return {subscriptionId: session.id, leaseToken: session.token, leaseMs};
  });
  function requireLease(req) {
    const body = req.body || {}, session = sessions.get(text(body.subscriptionId));
    if (!session || session.token !== body.leaseToken || session.expiresAt <= now()) throw fail(410, 'Phiên theo dõi đã hết hạn.');
    return session;
  }
  route('/realtime/keepalive', async req => {
    const session = requireLease(req);
    if (session.authorization) {
      try { await plan({scope: 'guest_order', ...session.authorization}, null); }
      catch (error) { release(session); throw error; }
    }
    if (session.caller) {
      const user = await db.collection('users').doc(session.caller.uid).get();
      if (!user.exists || user.get('status') !== 'ACTIVE' || user.get('role') !== session.caller.role) {
        release(session); throw fail(403, 'Tài khoản đã thay đổi.');
      }
    }
    if (session.failed) { release(session); throw fail(503, 'Cần kết nối lại phiên theo dõi.'); }
    session.expiresAt = now() + leaseMs;
    return {ok: true, leaseMs};
  });
  route('/realtime/close', async req => { release(requireLease(req)); return {ok: true}; });
  function sweep() { for (const session of sessions.values()) if (session.expiresAt <= now()) release(session); }
  const timer = setInterval(sweep, Math.min(30000, leaseMs)); timer.unref?.();
  function close() { clearInterval(timer); for (const session of [...sessions.values()]) release(session); }
  return {router, close, sweep, plan, diagnostics: () => ({sessions: sessions.size, watches: watches.size})};
}
module.exports = {createRealtimeApi};
