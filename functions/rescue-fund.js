const express = require('express');
const crypto = require('node:crypto');
const {bankBin, paymentCode, merchants, verifyWebhook} = require('./sepay');
const {normalizeUnit} = require('./quantity-units');
const {createReadCache} = require('./read-cache');
const {enqueueNotification} = require('./notification-outbox');
const text = v => typeof v === 'string' ? v.trim() : '';
const ms = v => v && typeof v.toMillis === 'function' ? v.toMillis() : 0;
const hash = v => crypto.createHash('sha256').update(v).digest('hex');
const fail = (status, message) => Object.assign(new Error(message), {status, expose: true});
const requireValue = (condition, message, status = 400) => { if (!condition) throw fail(status, message); };
const money = v => Number.isSafeInteger(v) && v >= 0 && v <= 100000000000;
const id = v => /^[A-Za-z0-9_-]{1,128}$/.test(text(v));
const round = v => Math.round(v * 1000) / 1000;
const dayKey = time => new Date(time + 7 * 3600000).toISOString().slice(0, 10);
const DELIVERY_WINDOW_MS = 24 * 3600000;
const EXPIRY_WINDOW_MS = 48 * 3600000;
const EXPIRY_BUFFER_MS = 60000;
function bankConfig(env) {
  let b; try { b = JSON.parse(env.RESCUE_FUND_BANK_JSON || '{}'); } catch (_) { b = {}; }
  requireValue(bankBin(b.bankCode) && /^\d{6,19}$/.test(text(b.accountNumber))
    && text(b.accountName).length >= 2 && text(b.webhookSecret).length >= 32,
  'Hũ chưa cấu hình tài khoản nhận tiền và webhook SePay.', 503);
  return {...b, bankBin: bankBin(b.bankCode), accountNumber: text(b.accountNumber), accountName: text(b.accountName), authMode: 'apikey'};
}
function settings(input) {
  const value = Object.fromEntries(['name', 'charityName', 'address', 'phone'].map(k => [k, text(input[k])]));
  requireValue(value.name.length >= 3 && value.name.length <= 120 && value.charityName.length >= 3
    && value.charityName.length <= 160 && value.address.length >= 5 && value.address.length <= 500
    && value.phone.length <= 30, 'Nhập đủ tên hũ, tổ chức và địa chỉ nhận.');
  requireValue([12, 24].includes(input.idleHours) && [12, 24, 48].includes(input.expiryHours)
    && money(input.dailyBudget) && input.dailyBudget >= 10000
    && money(input.perSellerBudget) && input.perSellerBudget >= 10000, 'Hạn mức hoặc khung giờ không hợp lệ.');
  return {...value, latitude: 0, longitude: 0, idleHours: input.idleHours,
    // Accept old clients' 12/24-hour setting, but new allocations always allow a full day to deliver.
    expiryHours: 48, deliveryWindowHours: 24, dailyBudget: input.dailyBudget, perSellerBudget: input.perSellerBudget,
    active: input.active === true};
}
function createRescueFund({db, admin, authenticate, env = process.env, now = Date.now, fetchImpl = fetch}) {
  const router = express.Router(), webhookRouter = express.Router();
  const ref = (c, key) => db.collection(c).doc(key), fundRef = ref('rescueFunds', 'main');
  const stamp = () => admin.firestore.FieldValue.serverTimestamp(), ts = n => admin.firestore.Timestamp.fromMillis(n);
  const data = s => s.exists ? s.data() : {};
  const cache = createReadCache({ttlMs: 30000, maxEntries: 2, now});
  let publicRevision = 0;
  const receiptCache = createReadCache({ttlMs: 10000, maxEntries: 500, now});
  function sharedMerchantId() {
    const selected = text(env.RESCUE_FUND_SELLER_ID);
    // Explicit legacy configuration remains supported. Remove it to use the
    // single connected Seller automatically; no new bank/webhook is needed.
    if (!selected && text(env.RESCUE_FUND_BANK_JSON)) return '';
    const entries = merchants(env), keys = Object.keys(entries);
    if (selected) {
      requireValue(id(selected) && entries[selected], 'RESCUE_FUND_SELLER_ID chưa kết nối SePay.', 503);
      return selected;
    }
    requireValue(keys.length === 1, 'Chọn UID Seller đang kết nối SePay bằng RESCUE_FUND_SELLER_ID khi có nhiều tài khoản.', 503);
    return keys[0];
  }
  async function resolveBank() {
    const sellerId = sharedMerchantId();
    if (!sellerId) return bankConfig(env);
    const merchant = merchants(env)[sellerId];
    const [profile, user] = await Promise.all([ref('sellerPaymentProfiles', sellerId).get(), ref('users', sellerId).get()]);
    requireValue(user.get('role') === 'SELLER' && user.get('status') === 'ACTIVE' && user.get('sellerStatus') === 'APPROVED',
      'Seller giữ tài khoản nhận của hũ phải đang hoạt động và được duyệt.', 409);
    requireValue(profile.exists && bankBin(profile.get('bankCode')) === merchant.bankBin
      && text(profile.get('bankAccountNumber')) === merchant.accountNumber && text(profile.get('bankAccountName')).length >= 2,
    'Hồ sơ ngân hàng Seller không khớp tài khoản SePay đang kết nối.', 409);
    return {...merchant, accountName: text(profile.get('bankAccountName')), sharedSellerId: sellerId};
  }
  function ledger(tx, key, kind, amount, actor, detail = {}) {
    tx.create(ref('rescueFundLedger', key), {fundId: 'main', kind, amount, actor, ...detail, createdAt: stamp()});
  }
  function notify(tx, key, uid, deliveryId, title, body) {
    const inboxId = 'fund_' + key;
    tx.set(ref('notifications', inboxId), {id: inboxId, userId: uid, type: 'SYSTEM', resourceType: 'RESCUE_FUND',
      referenceId: deliveryId, title, body, isRead: false, createdAt: stamp()});
    enqueueNotification(tx, db, admin, inboxId, {kind: 'DIRECT', resourceType: 'RESCUE_FUND',
      resourceId: deliveryId, recipientUid: uid, inboxId, title, publicMessage: body});
  }
  function proof(url, role, uid, deliveryId) {
    const cloud = text(env.CLOUDINARY_CLOUD_NAME);
    const path = role === 'SELLER' ? `fund-deliveries/${uid}/${deliveryId}/` : `fund-receipts/${uid}/${deliveryId}/`;
    let parsed; try { parsed = new URL(text(url)); } catch (_) { /* validation below */ }
    requireValue(cloud && parsed && parsed.origin === 'https://res.cloudinary.com'
      && parsed.pathname.startsWith(`/${cloud}/image/upload/`) && !parsed.search && !parsed.hash
      && parsed.pathname.includes('/rescuefarm/' + path)
      && /\/image-[a-f0-9-]{36}\.(jpe?g|png|webp|gif)$/i.test(parsed.pathname) && text(url).length <= 2048,
    'Hãy tải ảnh bằng nút chụp/chọn ảnh của giao dịch này.');
    return text(url);
  }
  async function evidence(url, role, uid, resourceId) {
    const value = proof(url, role, uid, resourceId);
    let response;
    try { response = await fetchImpl(value, {method: 'HEAD', redirect: 'error', signal: AbortSignal.timeout(10000)}); }
    catch (_) { throw fail(502, 'Chưa kiểm tra được ảnh Cloudinary. Hãy thử lại.'); }
    requireValue(response.ok && /^image\//i.test(response.headers.get('content-type') || ''), 'Ảnh bằng chứng chưa tồn tại trên Cloudinary.');
    return value;
  }
  const api = (path, action, roles) => router.post('/rescue-fund/' + path, async (req, res, next) => {
    try { const caller = roles ? await authenticate(req, roles) : null;
      res.json({ok: true, ...await action(req.body || {}, caller, req)});
    } catch (error) { next(error); }
  });
  api('public', async () => cache.get('public:' + publicRevision, async () => {
    const f = data(await fundRef.get());
    return {fund: {name: f.name || 'Hũ giải cứu nông sản', charityName: f.charityName || '', address: f.address || '',
      latitude: f.latitude || 0, longitude: f.longitude || 0, active: f.active === true,
      available: f.available || 0, reserved: f.reserved || 0, spent: f.spent || 0, donated: f.donated || 0}};
  }));
  api('donations', async () => {
    const rows = await db.collection('rescueFundDonations').where('status', '==', 'PAID')
      .orderBy('paidAt', 'desc').limit(50).get();
    return {items: rows.docs.map(s => ({name: text(s.get('supporterName')) || 'Người ủng hộ ẩn danh',
      paidAt: ms(s.get('paidAt'))}))};
  });
  api('ranking', async () => cache.get('ranking:' + publicRevision, async () => {
    const rows = await db.collection('rescueFundDonations').where('status', '==', 'PAID')
      .orderBy('amount', 'desc').orderBy('paidAt', 'asc').limit(50).get();
    return {items: rows.docs.map((s, index) => ({rank: index + 1,
      name: text(s.get('supporterName')) || 'Người ủng hộ ẩn danh',
      amount: s.get('amount'), paidAt: ms(s.get('paidAt'))}))};
  }));
  // Opaque bearer token is the receipt credential for Guest and Customer alike.
  const buckets = new Map();
  api('donate', async (b, _, req) => {
    requireValue(money(b.amount) && b.amount >= 10000 && b.amount <= 10000000 && id(b.requestId)
      && /^[a-f0-9]{64}$/.test(b.accessToken || ''), 'Số tiền từ 10.000đ đến 10.000.000đ; thiếu mã phiên hợp lệ.');
    const bank = await resolveBank(), key = hash(b.accessToken + ':' + b.requestId), donationRef = ref('rescueFundDonations', key);
    const shareName = b.shareName === true;
    const supporterName = shareName ? text(b.supporterName).replace(/[\u0000-\u001f\u007f]/g, '').slice(0, 40) : '';
    requireValue(!shareName || supporterName.length >= 2,
      'Nhập tên hiển thị (ít nhất 2 ký tự) hoặc bỏ chọn công khai tên.');
    // Include the device receipt token: a reverse proxy may give many genuine
    // guests the same req.ip. This limiter never grants financial authority.
    const bucketKey = hash((req.ip || '') + ':' + b.accessToken), previous = buckets.get(bucketKey);
    if (!previous || previous.until < now()) {
      if (buckets.size >= 2000) buckets.delete(buckets.keys().next().value);
      buckets.set(bucketKey, {count: 1, until: now() + 3600000});
    } else { requireValue(previous.count++ < 30, 'Đã tạo quá nhiều QR. Hãy thử lại sau.', 429); }
    const donation = await db.runTransaction(async tx => {
      const old = await tx.get(donationRef), f = await tx.get(fundRef);
      if (old.exists) { requireValue(old.get('amount') === b.amount, 'Mã yêu cầu đã dùng cho số tiền khác.', 409); return old.data(); }
      requireValue(f.exists && f.get('active') === true, 'Hũ đang tạm ngừng nhận ủng hộ.', 409);
      const code = 'SEVQR' + crypto.randomBytes(10).toString('hex').toUpperCase();
      requireValue(!(await tx.get(ref('bankTransferReferences', code))).exists, 'Mã chuyển khoản trùng, hãy tạo yêu cầu khác.', 409);
      const value = {id: key, fundId: 'main', amount: b.amount, tokenHash: hash(b.accessToken),
        supporterName,
        transferContent: code, bankBin: bank.bankBin, accountNumber: bank.accountNumber,
        sharedSellerId: bank.sharedSellerId || '',
        accountName: bank.accountName, status: 'PENDING', createdAt: ts(now()), expiresAt: ts(now() + 1800000)};
      tx.create(donationRef, value); tx.create(ref('rescueFundReferences', code), {donationId: key}); return value;
    });
    return {donation: receipt(donation)};
  });
  function receipt(d) {
    const qrUrl = `https://img.vietqr.io/image/${d.bankBin}-${d.accountNumber}-compact2.png?amount=${d.amount}`
      + `&addInfo=${encodeURIComponent(d.transferContent)}&accountName=${encodeURIComponent(d.accountName)}`;
    return {id: d.id, amount: d.amount, status: d.status, transferContent: d.transferContent,
      accountNumber: d.accountNumber, accountName: d.accountName, qrUrl, expiresAt: ms(d.expiresAt), paidAt: ms(d.paidAt)};
  }
  api('receipt', async b => {
    requireValue(id(b.donationId) && /^[a-f0-9]{64}$/.test(b.accessToken || ''), 'Thiếu mã biên nhận.');
    const d = await receiptCache.get(b.donationId, () => ref('rescueFundDonations', b.donationId).get());
    requireValue(d.exists && d.get('tokenHash') === hash(b.accessToken), 'Không có quyền xem biên nhận.', 403);
    return {donation: receipt(d.data())};
  });
  async function receive(req, selectedBank) {
    const bank = selectedBank || await resolveBank();
    verifyWebhook(req, bank, now);
    const p = req.body || {};
    requireValue(Number.isSafeInteger(p.id) && p.id >= 0 && ['in', 'out'].includes(p.transferType)
      && money(p.transferAmount) && p.transferAmount > 0 && text(p.content).length <= 2048,
    'Giao dịch không hợp lệ.');
    if (p.id === 0) return {success: true};
    const fingerprint = hash(JSON.stringify([p.id, p.gateway, p.accountNumber, p.transferAmount, p.transferType,
      p.content, p.code || '', p.referenceCode, p.transactionDate]));
    const eventRef = ref('rescueFundWebhookEvents', String(p.id)), code = paymentCode(p);
    await db.runTransaction(async tx => {
      const old = await tx.get(eventRef);
      if (old.exists) { requireValue(old.get('fingerprint') === fingerprint, 'Giao dịch trùng nhưng dữ liệu khác.', 409); return; }
      const event = {fingerprint, amount: p.transferAmount, receivedAt: stamp(), transactionId: p.id,
        bankReference: text(p.referenceCode).slice(0, 120), content: text(p.content),
        transactionDate: text(p.transactionDate).slice(0, 40)};
      const review = reason => tx.create(eventRef, {...event, status: 'REVIEW', reason});
      if (p.transferType === 'out') { tx.create(eventRef, {...event, status: 'IGNORED'}); return; }
      if (bankBin(p.gateway) !== bank.bankBin || p.accountNumber !== bank.accountNumber) { review('ACCOUNT_MISMATCH'); return; }
      const mapping = code ? await tx.get(ref('rescueFundReferences', code)) : null;
      if (!mapping || !mapping.exists) { review('UNKNOWN_CODE'); return; }
      const donationRef = ref('rescueFundDonations', mapping.get('donationId'));
      const d = await tx.get(donationRef), f = await tx.get(fundRef);
      const date = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(text(p.transactionDate))
        ? Date.parse(p.transactionDate.replace(' ', 'T') + '+07:00') : NaN;
      if (!d.exists || !f.exists || d.get('status') !== 'PENDING' || p.transferAmount !== d.get('amount')
        || d.get('bankBin') !== bank.bankBin || d.get('accountNumber') !== bank.accountNumber
        || (d.get('sharedSellerId') || '') !== (bank.sharedSellerId || '')) { review('DONATION_MISMATCH'); return; }
      if (!Number.isFinite(date) || date < ms(d.get('createdAt')) - 2000 || date > ms(d.get('expiresAt'))
        || date > now() + 300000 || !text(p.referenceCode) || text(p.referenceCode).length > 120) { review('TIME_OR_REFERENCE_MISMATCH'); return; }
      const bankRef = ref('rescueFundBankTransactions', hash(bank.bankBin + ':' + bank.accountNumber + ':' + p.referenceCode));
      if ((await tx.get(bankRef)).exists) { review('BANK_REFERENCE_USED'); return; }
      // This is the same transaction lock the existing order webhook reads.
      // A bank transfer cannot credit both a fund and an order, even concurrently.
      const commonBankRef = ref('sepayBankTransactions', hash(bank.bankBin + ':' + bank.accountNumber + ':' + p.referenceCode));
      const commonBank = await tx.get(commonBankRef), orderCode = await tx.get(ref('bankTransferReferences', code));
      if (commonBank.exists || orderCode.exists) { review('BANK_TRANSACTION_OR_CODE_ALREADY_USED_BY_ORDER'); return; }
      const available = (f.get('available') || 0) + p.transferAmount, donated = (f.get('donated') || 0) + p.transferAmount;
      requireValue(money(available) && money(donated), 'Vượt giới hạn số dư.', 409);
      tx.create(eventRef, {...event, donationId: d.id, status: 'APPLIED'});
      tx.create(bankRef, {donationId: d.id, transactionId: p.id, createdAt: stamp()});
      tx.create(commonBankRef, {resourceType: 'RESCUE_FUND', fundDonationId: d.id,
        sellerId: bank.sharedSellerId || '', transactionId: p.id, createdAt: stamp()});
      tx.update(donationRef, {status: 'PAID', paidAt: stamp(), bankReference: p.referenceCode});
      tx.update(fundRef, {available, donated, updatedAt: stamp()});
      ledger(tx, 'donation_' + d.id, 'DONATION', p.transferAmount, 'SEPAY', {donationId: d.id});
    });
    publicRevision++; // A confirmed top-up must not keep showing the pre-payment balance.
    return {success: true};
  }
  webhookRouter.post('/webhooks/rescue-fund/sepay', async (req, res, next) => {
    try { res.json(await receive(req)); } catch (error) { next(error); }
  });
  async function receiveShared(req) {
    // Authenticate using the existing Seller's exact API key/HMAC contract.
    // Only a code actually owned by the fund is intercepted; orders fall through.
    if (!text(env.RESCUE_FUND_SELLER_ID) && text(env.RESCUE_FUND_BANK_JSON)) return null;
    const entries = merchants(env), keys = Object.keys(entries);
    const sellerId = text(env.RESCUE_FUND_SELLER_ID) || (keys.length === 1 ? keys[0] : '');
    if (!sellerId || req.params.sellerId !== sellerId || !entries[sellerId]) return null;
    verifyWebhook(req, entries[sellerId], now);
    const code = paymentCode(req.body || {});
    if (!code || !(await ref('rescueFundReferences', code).get()).exists) return null;
    return receive(req, await resolveBank());
  }
  webhookRouter.post('/webhooks/sepay/:sellerId', async (req, res, next) => {
    try { const result = await receiveShared(req); if (result === null) return next(); res.json(result); }
    catch (error) { next(error); }
  });
  api('admin/save', async (b, caller) => {
    const config = settings(b);
    if (config.active) await resolveBank();
    await db.runTransaction(async tx => {
      const old = await tx.get(fundRef);
      tx.set(fundRef, {...(old.exists ? {} : {available: 0, reserved: 0, spent: 0, donated: 0}),
        ...config, updatedAt: stamp(), updatedBy: caller.uid}, {merge: true});
      ledger(tx, crypto.randomUUID(), 'SETTINGS', 0, caller.uid, {before: data(old), after: config});
    }); return {};
  }, ['ADMIN']);
  api('admin/get', async () => ({fund: data(await fundRef.get())}), ['ADMIN']);
  api('admin/ledger', async b => {
    let q = db.collection('rescueFundLedger').orderBy('createdAt', 'desc').orderBy(admin.firestore.FieldPath.documentId(), 'desc').limit(30);
    if (b.cursor) {
      requireValue(id(b.cursor), 'Con trỏ lịch sử không hợp lệ.');
      const cursor = await ref('rescueFundLedger', b.cursor).get();
      requireValue(cursor.exists, 'Không tìm thấy mốc lịch sử.'); q = q.startAfter(cursor);
    }
    const rows = await q.get(); return {items: rows.docs.map(s => ({id: s.id, ...s.data(), createdAt: ms(s.get('createdAt'))})),
      cursor: rows.size === 30 ? rows.docs[29].id : ''};
  }, ['ADMIN']);
  api('admin/events', async b => {
    let q = db.collection('rescueFundWebhookEvents').where('status', '==', 'REVIEW').orderBy(admin.firestore.FieldPath.documentId()).limit(30);
    if (id(b.cursor)) q = q.startAfter(b.cursor);
    const rows = await q.get();
    return {items: rows.docs.map(s => ({id: s.id, ...s.data(), receivedAt: ms(s.get('receivedAt'))})), cursor: rows.size === 30 ? rows.docs[29].id : ''};
  }, ['ADMIN']);
  api('deliveries', async (b, caller) => {
    if (b.deliveryId) {
      requireValue(id(b.deliveryId), 'Mã chuyến không hợp lệ.');
      const s = await ref('rescueFundDeliveries', b.deliveryId).get();
      requireValue(s.exists && (caller.role === 'ADMIN' || s.get('sellerId') === caller.uid), 'Không có quyền xem chuyến.', 403);
      return {items: [{id: s.id, ...s.data(), deadline: ms(s.get('deadline')), createdAt: ms(s.get('createdAt'))}], cursor: ''};
    }
    let q = db.collection('rescueFundDeliveries');
    if (caller.role === 'SELLER') q = q.where('sellerId', '==', caller.uid);
    q = q.orderBy(admin.firestore.FieldPath.documentId()).limit(30);
    if (id(b.cursor)) q = q.startAfter(b.cursor);
    const rows = await q.get(); return {items: rows.docs.map(s => ({id: s.id, ...s.data(),
      deadline: ms(s.get('deadline')), createdAt: ms(s.get('createdAt'))})), cursor: rows.size === 30 ? rows.docs[29].id : ''};
  }, ['ADMIN', 'SELLER']);
  api('admin/adjust', async (b, caller) => {
    requireValue(id(b.requestId) && Number.isSafeInteger(b.amount) && b.amount !== 0 && money(Math.abs(b.amount))
      && text(b.reason).length >= 10 && text(b.reason).length <= 500, 'Cần số tiền và lý do đối soát ít nhất 10 ký tự.');
    const url = await evidence(b.proofUrl, 'ADMIN', caller.uid, b.requestId), key = 'adjust_' + hash(caller.uid + ':' + b.requestId);
    await db.runTransaction(async tx => {
      const old = await tx.get(ref('rescueFundLedger', key)), f = await tx.get(fundRef);
      if (old.exists) { requireValue(old.get('amount') === b.amount && old.get('proofUrl') === url && old.get('reason') === b.reason, 'Mã đối soát đã dùng.', 409); return; }
      const balance = (f.get('available') || 0) + b.amount;
      requireValue(f.exists && money(balance), 'Điều chỉnh làm số dư âm hoặc vượt giới hạn.', 409);
      tx.update(fundRef, {available: balance, updatedAt: stamp()});
      ledger(tx, key, 'BANK_RECONCILIATION', b.amount, caller.uid, {reason: b.reason, proofUrl: url});
    }); return {};
  }, ['ADMIN']);

  async function allocation(batchId, day) {
    const jobRef = ref('rescueFundDeliveries', day + '_' + hash(batchId).slice(0, 32)), runRef = ref('rescueFundRuns', day);
    await db.runTransaction(async tx => {
      const old = await tx.get(jobRef), f = await tx.get(fundRef), run = await tx.get(runRef);
      if (old.exists || !f.exists || !f.get('active')) return;
      const allocationTime = now(), deadline = allocationTime + DELIVERY_WINDOW_MS;
      const config = f.data(), batchRef = ref('productBatches', batchId), batchSnap = await tx.get(batchRef), batch = data(batchSnap);
      if (!batchSnap.exists || !['AVAILABLE', 'RESERVED_PARTIAL'].includes(batch.status)
        || ms(batch.expiryDate) <= deadline + EXPIRY_BUFFER_MS || ms(batch.expiryDate) > allocationTime + EXPIRY_WINDOW_MS) return;
      const product = await tx.get(ref('products', batch.productId)), p = data(product);
      if (!product.exists || p.status !== 'ACTIVE' || !id(p.sellerId) || !money(p.rescuePrice) || p.rescuePrice <= 0 || !normalizeUnit(p.unit)) return;
      const seller = await tx.get(ref('users', p.sellerId)), payment = await tx.get(ref('sellerPaymentProfiles', p.sellerId));
      if (seller.get('role') !== 'SELLER' || seller.get('status') !== 'ACTIVE' || seller.get('sellerStatus') !== 'APPROVED'
        || !bankBin(payment.get('bankCode')) || !/^\d{6,30}$/.test(text(payment.get('bankAccountNumber')))) return;
      const cutoff = ts(now() - config.idleHours * 3600000);
      if (Math.max(ms(batch.createdAt), ms(p.createdAt), ms(seller.get('createdAt'))) > ms(cutoff)) return;
      for (const [collection, field, value] of [['orders', 'paymentStatus', 'PAID'], ['orders', 'status', 'DELIVERED'], ['rescueClaims', 'status', 'COMPLETED']]) {
        const sales = await tx.get(db.collection(collection).where('sellerId', '==', p.sellerId)
          .where(field, '==', value).where('updatedAt', '>=', cutoff).limit(1));
        if (!sales.empty) return;
      }
      const sellerTotals = {...(run.get('sellerTotals') || {})}, remaining = Math.min(config.available,
        config.dailyBudget - (run.get('allocated') || 0), config.perSellerBudget - (sellerTotals[p.sellerId] || 0));
      let stock = batch.availableQuantity, campaign = null, campaignRef = null;
      if (batch.activeCampaignId) {
        campaignRef = ref('campaigns', batch.activeCampaignId); campaign = data(await tx.get(campaignRef));
        if (campaign.status !== 'ACTIVE' || ms(campaign.endDate) <= deadline + EXPIRY_BUFFER_MS
          || ms(campaign.startDate) > now() || campaign.sellerId !== p.sellerId || normalizeUnit(campaign.quantityUnit) !== normalizeUnit(p.unit)) return;
        stock = Math.min(stock, (campaign.batchTargets?.[batchId] || 0) - (campaign.reservedByBatch?.[batchId] || 0)
          - (campaign.rescuedByBatch?.[batchId] || 0));
      }
      // Weight/volume may be fractional; countable boxes, bags and fruit stay whole.
      const scale = ['kg', 'g', 'gram', 'l', 'lít'].includes(normalizeUnit(p.unit)) ? 1000 : 1;
      const quantity = Math.floor(Math.min(stock, remaining / p.rescuePrice) * scale) / scale;
      const amount = Math.ceil(quantity * p.rescuePrice);
      if (!(quantity > 0) || !money(amount) || amount <= 0 || amount > remaining) return;
      const job = {fundId: 'main', fundName: config.name, sellerId: p.sellerId, batchId, productId: product.id,
        productName: text(p.name), unit: text(p.unit), campaignId: batch.activeCampaignId || '', campaignTitle: campaign ? text(campaign.title) : '',
        quantity, amount, unitPrice: p.rescuePrice, status: 'RESERVED', deadline: ts(deadline),
        deliveryWindowHours: 24, allocationPolicyVersion: 2, allocatedAt: ts(allocationTime), createdAt: stamp(), updatedAt: stamp(),
        charityName: config.charityName, address: config.address, latitude: config.latitude, longitude: config.longitude, phone: config.phone,
        sellerBankCode: payment.get('bankCode'), sellerBankAccountNumber: payment.get('bankAccountNumber'),
        sellerBankAccountName: text(payment.get('bankAccountName'))};
      tx.create(jobRef, job);
      tx.update(batchRef, {availableQuantity: round(batch.availableQuantity - quantity), reservedQuantity: round(batch.reservedQuantity + quantity),
        status: batch.availableQuantity - quantity > .000001 ? 'RESERVED_PARTIAL' : 'SOLD_OUT',
        inventoryVersion: (batch.inventoryVersion || 0) + 1, updatedAt: stamp()});
      if (campaign) tx.update(campaignRef, {reservedQuantity: round((campaign.reservedQuantity || 0) + quantity),
        reservedByBatch: {...campaign.reservedByBatch, [batchId]: round((campaign.reservedByBatch?.[batchId] || 0) + quantity)},
        allocationVersion: (campaign.allocationVersion || 0) + 1, updatedAt: stamp()});
      tx.update(fundRef, {available: config.available - amount, reserved: (config.reserved || 0) + amount, updatedAt: stamp()});
      sellerTotals[p.sellerId] = (sellerTotals[p.sellerId] || 0) + amount;
      tx.set(runRef, {allocated: (run.get('allocated') || 0) + amount, sellerTotals, updatedAt: stamp()}, {merge: true});
      ledger(tx, 'reserve_' + jobRef.id, 'RESERVE', amount, 'SYSTEM', {deliveryId: jobRef.id});
      notify(tx, jobRef.id + '_reserved', p.sellerId, jobRef.id, 'Hũ giải cứu hỗ trợ giao thiện nguyện',
        `${config.name} giữ ${amount}đ để giải cứu ${p.name}${campaign ? ' — ' + campaign.title : ''}. Bạn có 24 giờ kể từ khi tạo chuyến để nhận chuyến, giao và gửi ảnh bằng chứng. Xem hạn giao và nơi nhận trong Hũ giải cứu. Chưa chuyển tiền cho cửa hàng.`);
    });
  }
  async function action(b, caller, system = false) {
    requireValue(id(b.deliveryId), 'Thiếu mã chuyến giao.');
    if (b.action === 'DELIVER' || b.action === 'PAY') {
      requireValue(caller.role === (b.action === 'PAY' ? 'ADMIN' : 'SELLER'), 'Vai trò không hợp lệ.', 403);
      await evidence(b.proofUrl, caller.role, caller.uid, b.deliveryId);
    }
    const jobRef = ref('rescueFundDeliveries', b.deliveryId);
    await db.runTransaction(async tx => {
      const snap = await tx.get(jobRef); requireValue(snap.exists, 'Không tìm thấy chuyến giao.', 404);
      const job = snap.data(), a = b.action;
      if (caller.role === 'SELLER') requireValue(job.sellerId === caller.uid, 'Không có quyền với chuyến giao.', 403);
      const adminAction = caller.role === 'ADMIN';
      if (a === 'ACCEPT' || a === 'DELIVER') {
        requireValue(caller.role === 'SELLER' && job.sellerId === caller.uid, 'Chỉ Seller phụ trách thực hiện.', 403);
        if (a === 'ACCEPT' && job.status === 'ACCEPTED') return;
        if (a === 'DELIVER' && job.status === 'DELIVERED') return;
        requireValue(ms(job.deadline) > now() && (a === 'ACCEPT' ? job.status === 'RESERVED' : job.status === 'ACCEPTED'), 'Chuyến đã hết hạn hoặc trạng thái thay đổi.', 409);
        const batch = await tx.get(ref('productBatches', job.batchId));
        requireValue(ms(batch.get('expiryDate')) > now(), 'Lô hàng đã hết hạn.', 409);
        const admins = a === 'DELIVER' ? await tx.get(db.collection('users').where('role', '==', 'ADMIN').where('status', '==', 'ACTIVE').limit(50)) : null;
        tx.update(jobRef, a === 'ACCEPT' ? {status: 'ACCEPTED', acceptedAt: stamp(), updatedAt: stamp()}
          : {status: 'DELIVERED', deliveredAt: stamp(), proofUrl: proof(b.proofUrl, 'SELLER', caller.uid, snap.id),
            deliveryNote: text(b.note).slice(0, 500), updatedAt: stamp()});
        ledger(tx, a.toLowerCase() + '_' + snap.id, a, 0, caller.uid, {deliveryId: snap.id});
        if (admins) for (const account of admins.docs) notify(tx, snap.id + '_delivered_' + account.id, account.id,
          snap.id, 'Có ảnh giao thiện nguyện cần kiểm tra', `${job.productName}: Seller đã gửi ảnh bàn giao cho ${job.charityName}.`);
        return;
      }
      if (a === 'PAY') {
        requireValue(adminAction, 'Chỉ Admin ghi khoản chi.', 403);
        if (job.status === 'PAID') { requireValue(job.bankReference === text(b.bankReference), 'Khoản chi đã chốt.', 409); return; }
        requireValue(job.status === 'VERIFIED' && text(b.bankReference).length >= 3 && text(b.bankReference).length <= 120
          && b.amount === job.amount, 'Chỉ ghi chi đúng số tiền cho chuyến đã xác minh giao.', 409);
        const url = proof(b.proofUrl, 'ADMIN', caller.uid, snap.id), f = await tx.get(fundRef);
        const bankRef = ref('rescueFundPayoutReferences', hash('main:' + text(b.bankReference).toUpperCase()));
        requireValue(!(await tx.get(bankRef)).exists, 'Mã chuyển khoản đã dùng cho khoản chi khác.', 409);
        requireValue(f.get('reserved') >= job.amount, 'Số dư giữ không đủ; cần đối soát.', 409);
        tx.create(bankRef, {deliveryId: snap.id, createdAt: stamp()});
        tx.update(fundRef, {reserved: f.get('reserved') - job.amount, spent: (f.get('spent') || 0) + job.amount, updatedAt: stamp()});
        tx.update(jobRef, {status: 'PAID', payoutProofUrl: url, bankReference: text(b.bankReference), paidAt: stamp(), paidBy: caller.uid, updatedAt: stamp()});
        ledger(tx, 'pay_' + snap.id, 'PAYOUT', job.amount, caller.uid, {deliveryId: snap.id, proofUrl: url, bankReference: text(b.bankReference)});
        notify(tx, snap.id + '_paid', job.sellerId, snap.id, 'Đã ghi khoản chi từ hũ', `${job.fundName}: Admin ghi đã chuyển ${job.amount}đ cho chuyến ${job.productName}. Mã: ${text(b.bankReference)}.`); return;
      }
      const cancel = a === 'CANCEL', verify = a === 'VERIFY';
      requireValue(cancel || verify, 'Thao tác không hợp lệ.');
      requireValue(adminAction || (cancel && caller.role === 'SELLER' && ['RESERVED', 'ACCEPTED'].includes(job.status)), 'Không có quyền.', 403);
      if (cancel && job.status === 'CANCELLED' || verify && ['VERIFIED', 'PAID'].includes(job.status)) return;
      requireValue(verify ? job.status === 'DELIVERED' : ['RESERVED', 'ACCEPTED', ...(adminAction && !system ? ['DELIVERED'] : [])].includes(job.status), 'Không thể đổi trạng thái chuyến này.', 409);
      requireValue(verify || text(b.note).length >= 3 && text(b.note).length <= 500, 'Cần lý do hủy.');
      if (system) requireValue(ms(job.deadline) <= now(), 'Chưa hết hạn.');
      const batchRef = ref('productBatches', job.batchId), batchSnap = await tx.get(batchRef), batch = data(batchSnap);
      const f = await tx.get(fundRef), campaignRef = job.campaignId ? ref('campaigns', job.campaignId) : null;
      const campaign = campaignRef ? data(await tx.get(campaignRef)) : null;
      requireValue(batchSnap.exists && batch.reservedQuantity + .000001 >= job.quantity && f.get('reserved') >= job.amount, 'Kho hoặc tiền giữ cần đối soát.', 409);
      if (verify) requireValue(job.proofUrl && ms(job.deliveredAt) > 0 && ms(job.deliveredAt) <= ms(job.deadline), 'Thiếu bằng chứng giao trước hạn.', 409);
      const available = round(batch.availableQuantity + (cancel ? job.quantity : 0)), reserved = round(batch.reservedQuantity - job.quantity);
      tx.update(batchRef, {availableQuantity: available, reservedQuantity: reserved,
        soldQuantity: round(batch.soldQuantity + (verify ? job.quantity : 0)),
        status: batch.status === 'ARCHIVED' ? 'ARCHIVED' : ms(batch.expiryDate) <= now() ? 'EXPIRED'
          : available <= .000001 ? 'SOLD_OUT' : reserved > .000001 ? 'RESERVED_PARTIAL' : 'AVAILABLE',
        inventoryVersion: (batch.inventoryVersion || 0) + 1, updatedAt: stamp()});
      if (campaign && campaignRef) {
        requireValue((campaign.reservedByBatch?.[job.batchId] || 0) + .000001 >= job.quantity, 'Chiến dịch cần đối soát.', 409);
        tx.update(campaignRef, {reservedQuantity: round(campaign.reservedQuantity - job.quantity),
          reservedByBatch: {...campaign.reservedByBatch, [job.batchId]: round(campaign.reservedByBatch[job.batchId] - job.quantity)},
          rescuedQuantity: round((campaign.rescuedQuantity || 0) + (verify ? job.quantity : 0)),
          rescuedByBatch: {...campaign.rescuedByBatch, [job.batchId]: round((campaign.rescuedByBatch?.[job.batchId] || 0) + (verify ? job.quantity : 0))},
          allocationVersion: (campaign.allocationVersion || 0) + 1, updatedAt: stamp()});
      }
      if (cancel) tx.update(fundRef, {available: (f.get('available') || 0) + job.amount, reserved: f.get('reserved') - job.amount, updatedAt: stamp()});
      tx.update(jobRef, {status: cancel ? 'CANCELLED' : 'VERIFIED', note: text(b.note), reviewedBy: caller.uid, reviewedAt: stamp(), updatedAt: stamp()});
      ledger(tx, (cancel ? 'release_' : 'verify_') + snap.id, cancel ? 'RELEASE' : 'VERIFY_DELIVERY', cancel ? job.amount : 0, caller.uid, {deliveryId: snap.id, reason: text(b.note)});
    }); return {};
  }
  api('delivery/action', (b, caller) => action(b, caller), ['ADMIN', 'SELLER']);
  let nextScan = 0, busy = false, completedDay = '';
  async function reconcile() {
    if (busy || now() < nextScan) return;
    busy = true;
    try {
      const expired = await db.collection('rescueFundDeliveries').where('status', 'in', ['RESERVED', 'ACCEPTED'])
        .where('deadline', '<=', ts(now())).limit(50).get();
      for (const job of expired.docs) await action({deliveryId: job.id, action: 'CANCEL', note: 'Hết thời hạn giao hàng'}, {role: 'ADMIN', uid: 'system-fund-expiry'}, true);
      const f = await fundRef.get(); if (!f.exists || !f.get('active')) { nextScan = now() + 300000; return; }
      const day = dayKey(now());
      if (completedDay === day) { nextScan = now() + 300000; return; }
      const stateRef = ref('rescueFundRuns', day), state = await stateRef.get();
      if (state.get('complete')) { completedDay = day; nextScan = now() + 300000; return; }
      let q = db.collection('productBatches').where('status', 'in', ['AVAILABLE', 'RESERVED_PARTIAL'])
        .where('expiryDate', '>', ts(now() + DELIVERY_WINDOW_MS + EXPIRY_BUFFER_MS)).where('expiryDate', '<=', ts(now() + EXPIRY_WINDOW_MS))
        .orderBy('expiryDate').orderBy(admin.firestore.FieldPath.documentId()).limit(50);
      if (state.get('cursorId')) q = q.startAfter(state.get('cursorExpiry'), state.get('cursorId'));
      const page = await q.get();
      for (const batch of page.docs) await allocation(batch.id, day);
      const last = page.docs[page.size - 1];
      await stateRef.set({complete: page.size < 50, ...(last ? {cursorId: last.id, cursorExpiry: last.get('expiryDate')} : {}), updatedAt: stamp()}, {merge: true});
      if (page.size < 50) completedDay = day;
      nextScan = now() + (page.size === 50 ? 60000 : 300000);
    } finally { busy = false; }
  }
  return {router, webhookRouter, receive, receiveShared, resolveBank, allocation, action, reconcile};
}
module.exports = {createRescueFund, settings, dayKey, bankConfig};
