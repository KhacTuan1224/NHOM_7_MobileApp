const express = require('express');
const crypto = require('node:crypto');
const {enqueueNotification} = require('./notification-outbox');
const text = value => typeof value === 'string' ? value.trim() : '';
const fail = (status, message) => Object.assign(new Error(message), {status, ...(status === 503 ? {expose: true} : {})});
const digest = value => crypto.createHash('sha256').update(value).digest('hex');
const bankAliases = {
  ICB: '970415', VIETINBANK: '970415', VCB: '970436', VIETCOMBANK: '970436', MB: '970422', MBBANK: '970422',
  BIDV: '970418', VBA: '970405', AGRIBANK: '970405', ACB: '970416', TCB: '970407', TECHCOMBANK: '970407',
  OCB: '970448', VPB: '970432', VPBANK: '970432', TPB: '970423', TPBANK: '970423',
  STB: '970403', SACOMBANK: '970403', MSB: '970426', KLB: '970452', KIENLONGBANK: '970452'
};
const bankBin = value => {
  const code = text(value).toUpperCase();
  return /^970\d{3}$/.test(code) ? code : bankAliases[code] || '';
};

function merchants(env = process.env) {
  if (!text(env.SEPAY_MERCHANTS_JSON)) return Object.create(null);
  let entries;
  try { entries = JSON.parse(env.SEPAY_MERCHANTS_JSON); } catch (_) { throw fail(503, 'Cấu hình SePay chưa hợp lệ.'); }
  if (!entries || Array.isArray(entries) || typeof entries !== 'object') throw fail(503, 'Cấu hình SePay chưa hợp lệ.');
  const result = Object.create(null), accounts = new Set();
  for (const [sellerId, entry] of Object.entries(entries)) {
    if (entry && entry.enabled === false) continue;
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(sellerId) || !entry || entry.enabled !== true
        || !bankBin(entry.bankCode) || !/^\d{6,30}$/.test(text(entry.accountNumber))
        || text(entry.webhookSecret).length < 32 || !['hmac', 'apikey'].includes(entry.authMode || 'hmac'))
      throw fail(503, 'Cấu hình SePay chưa hợp lệ.');
    const account = bankBin(entry.bankCode) + ':' + text(entry.accountNumber);
    if (accounts.has(account)) throw fail(503, 'Một tài khoản SePay không được gán cho nhiều seller.');
    accounts.add(account);
    result[sellerId] = {...entry, accountNumber: text(entry.accountNumber), bankBin: bankBin(entry.bankCode), authMode: entry.authMode || 'hmac'};
  }
  return result;
}

function paymentDetails(sellerId, bank, env = process.env) {
  const merchant = merchants(env)[sellerId];
  if (merchant && (!bank || bankBin(bank.bankCode) !== merchant.bankBin || text(bank.bankAccountNumber) !== merchant.accountNumber))
    throw fail(409, 'Tài khoản ngân hàng của seller không khớp kết nối SePay. Hãy cập nhật cấu hình nhận tiền.');
  // 25 characters, starts with SEVQR as required for VietinBank personal API connections.
  // QR content must not depend on whether webhook verification has been enabled yet.
  return {verificationProvider: merchant ? 'SEPAY' : 'MANUAL',
    transferContent: 'SEVQR' + crypto.randomBytes(10).toString('hex').toUpperCase()};
}

function bankTransferCapability(sellerId, bank, env = process.env) {
  let merchant;
  try { merchant = merchants(env)[sellerId]; }
  catch (error) {
    if (error.status !== 503) throw error;
    // Invalid payment integration must disable transfers, not cash/COD checkout.
    return {available: false, provider: '', reason: 'SEPAY_INVALID_CONFIGURATION'};
  }
  if (!merchant) return {available: false, provider: '', reason: 'SEPAY_NOT_CONNECTED'};
  if (!bank || bankBin(bank.bankCode) !== merchant.bankBin
      || text(bank.bankAccountNumber) !== merchant.accountNumber)
    return {available: false, provider: '', reason: 'BANK_ACCOUNT_MISMATCH'};
  return {available: true, provider: 'SEPAY', reason: ''};
}

function timingEqual(a, b) {
  const left = Buffer.from(a), right = Buffer.from(b);
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function verifyWebhook(req, merchant, now) {
  if (merchant.authMode === 'apikey') {
    if (!timingEqual(req.get('authorization') || '', 'Apikey ' + merchant.webhookSecret))
      throw fail(401, 'Webhook SePay không hợp lệ.');
    return;
  }
  const timestamp = req.get('x-sepay-timestamp') || '', signature = req.get('x-sepay-signature') || '';
  if (!/^\d{10}$/.test(timestamp) || Math.abs(now() / 1000 - Number(timestamp)) > 300
      || !Buffer.isBuffer(req.rawBody) || !/^sha256=[a-f0-9]{64}$/i.test(signature))
    throw fail(401, 'Chữ ký webhook SePay không hợp lệ hoặc đã hết hạn.');
  const expected = 'sha256=' + crypto.createHmac('sha256', merchant.webhookSecret)
    .update(timestamp + '.').update(req.rawBody).digest('hex');
  if (!timingEqual(signature.toLowerCase(), expected)) throw fail(401, 'Chữ ký webhook SePay không hợp lệ.');
}

function paymentCode(payload) {
  const content = text(payload.content).toUpperCase();
  const matches = [...content.matchAll(/(?:^|[^A-Z0-9])(SEVQR[A-F0-9]{20})(?=$|[^A-Z0-9])/g)].map(match => match[1]);
  const unique = [...new Set(matches)], supplied = text(payload.code).toUpperCase();
  return unique.length === 1 && (!supplied || supplied === unique[0]) ? unique[0] : '';
}

function createSePayApi({db, admin, env = process.env, now = Date.now}) {
  const router = express.Router(), stamp = () => admin.firestore.FieldValue.serverTimestamp();
  async function receive(req) {
    const sellerId = text(req.params && req.params.sellerId), merchant = merchants(env)[sellerId];
    if (!merchant) throw fail(503, 'Seller chưa bật kết nối SePay.');
    verifyWebhook(req, merchant, now);
    const payload = req.body;
    if (!payload || !Number.isSafeInteger(payload.id) || payload.id < 0 || !['in', 'out'].includes(payload.transferType)
        || !Number.isSafeInteger(payload.transferAmount) || payload.transferAmount <= 0
        || !text(payload.accountNumber) || text(payload.content).length > 2048 || text(payload.referenceCode).length > 120)
      throw fail(400, 'Dữ liệu giao dịch SePay không hợp lệ.');
    // Dashboard "Send test" uses id 0. A probe must never pay an actual order.
    if (payload.id === 0) return {success: true};
    const code = paymentCode(payload), bin = bankBin(payload.gateway);
    const transactionTime = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(text(payload.transactionDate))
      ? Date.parse(payload.transactionDate.replace(' ', 'T') + '+07:00') : NaN;
    const financial = {id: payload.id, bank: bin, account: payload.accountNumber, code,
      content: text(payload.content), amount: payload.transferAmount, direction: payload.transferType,
      reference: text(payload.referenceCode), transactionDate: text(payload.transactionDate)};
    const fingerprint = digest(JSON.stringify(financial));
    const eventRef = db.collection('sepayWebhookEvents').doc(digest(sellerId + ':' + payload.id));
    await db.runTransaction(async tx => {
      const previous = await tx.get(eventRef);
      if (previous.exists) {
        if (previous.get('fingerprint') !== fingerprint) throw fail(409, 'Mã giao dịch SePay đã tồn tại với dữ liệu khác.');
        return;
      }
      const event = {sellerId, transactionId: payload.id, fingerprint, transferContent: code,
        amount: payload.transferAmount, bankReference: financial.reference, receivedAt: stamp()};
      const review = reason => tx.create(eventRef, {...event, status: 'REVIEW', reason});
      if (payload.transferType !== 'in') { tx.create(eventRef, {...event, status: 'IGNORED', reason: 'OUTGOING'}); return; }
      if (bin !== merchant.bankBin || payload.accountNumber !== merchant.accountNumber) { review('ACCOUNT_MISMATCH'); return; }
      if (!code) { review('MISSING_OR_AMBIGUOUS_CODE'); return; }
      if (!financial.reference) { review('MISSING_BANK_REFERENCE'); return; }
      if (!Number.isFinite(transactionTime) || transactionTime > now() + 300000) { review('INVALID_TRANSACTION_TIME'); return; }
      const mapping = await tx.get(db.collection('bankTransferReferences').doc(code));
      if (!mapping.exists || mapping.get('sellerId') !== sellerId) { review('UNKNOWN_PAYMENT_CODE'); return; }
      const id = mapping.get('orderId'), orderRef = db.collection('orders').doc(id), paymentRef = db.collection('payments').doc(id);
      const orderSnap = await tx.get(orderRef), paymentSnap = await tx.get(paymentRef);
      if (!orderSnap.exists || !paymentSnap.exists) { review('ORDER_NOT_FOUND'); return; }
      const order = orderSnap.data(), payment = paymentSnap.data();
      const bankTransactionRef = db.collection('sepayBankTransactions').doc(digest(bin + ':' + payload.accountNumber + ':' + financial.reference));
      const used = await tx.get(bankTransactionRef);
      if (used.exists) { review('BANK_TRANSACTION_ALREADY_USED'); return; }
      // An old pending order may have been created before this seller connected SePay.
      // Only authenticated webhooks with the original immutable bank/code binding may upgrade it.
      if (order.sellerId !== sellerId || payment.orderId !== id || !['SEPAY', 'MANUAL'].includes(payment.verificationProvider)
          || order.paymentMethod !== 'BANK_TRANSFER' || payment.method !== 'BANK_TRANSFER'
          || payment.transferContent !== code || bankBin(payment.bankCode) !== bin
          || payment.bankAccountNumber !== payload.accountNumber) { review('PAYMENT_BINDING_MISMATCH'); return; }
      if (!Number.isSafeInteger(order.totalAmount) || order.totalAmount !== payload.transferAmount
          || payment.amount !== payload.transferAmount) { review('AMOUNT_MISMATCH'); return; }
      if (order.paymentStatus === 'PAID' || payment.status === 'PAID') { review('ORDER_ALREADY_PAID'); return; }
      if (order.status === 'CANCELLED' || order.inventoryFinalized || order.paymentStatus !== 'PENDING'
          || payment.status !== 'PENDING' || !['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'SHIPPING'].includes(order.status)) {
        review('ORDER_NOT_PAYABLE'); return;
      }
      const created = order.createdAt && typeof order.createdAt.toMillis === 'function' ? order.createdAt.toMillis() : NaN;
      if (!Number.isFinite(created) || transactionTime + 2000 < created) { review('TRANSACTION_BEFORE_ORDER'); return; }
      tx.create(eventRef, {...event, orderId: id, status: 'APPLIED'});
      tx.create(bankTransactionRef, {sellerId, orderId: id, transactionId: payload.id, createdAt: stamp()});
      tx.update(paymentRef, {status: 'PAID', verificationProvider: 'SEPAY', referenceCode: financial.reference, verificationSource: 'SEPAY_WEBHOOK',
        providerTransactionId: String(payload.id), paidAt: stamp(), updatedAt: stamp()});
      tx.update(orderRef, {paymentStatus: 'PAID', status: order.status === 'PENDING' ? 'CONFIRMED' : order.status,
        ...(order.status === 'PENDING' ? {confirmedAt: stamp()} : {}), updatedAt: stamp()});
      const recipients = [...new Set([sellerId, ...(order.ownerType === 'CUSTOMER' ? [order.ownerId] : [])])];
      for (const recipientUid of recipients) {
        const inboxId = `${id}_SEPAY_PAID_${recipientUid}`;
        const publicMessage = 'Hệ thống đã xác minh chuyển khoản thành công và xác nhận đơn hàng.';
        tx.set(db.collection('notifications').doc(inboxId), {id: inboxId, userId: recipientUid, type: 'ORDER',
          referenceId: id, title: 'Thanh toán thành công', body: publicMessage, isRead: false, createdAt: stamp()});
        enqueueNotification(tx, db, admin, `sepay_${inboxId}`, {kind: 'DIRECT', resourceType: 'ORDER', resourceId: id,
          recipientUid, inboxId, publicMessage, title: 'Thanh toán thành công'});
      }
    });
    return {success: true};
  }
  router.post('/webhooks/sepay/:sellerId', async (req, res, next) => {
    try { res.json(await receive(req)); } catch (error) { next(error); }
  });
  return {router, receive};
}

module.exports = {createSePayApi, paymentDetails, merchants, bankBin, paymentCode, bankTransferCapability, verifyWebhook};
