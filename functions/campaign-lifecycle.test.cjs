const {test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const {MemoryFirestore, Timestamp, admin} = require('./test-support/memory-firestore.cjs');
const {createCampaignLifecycle, readAllocations} = require('./campaign-lifecycle');
const {createCommerce} = require('./commerce');
const {createBatchExpiry} = require('./batch-expiry');
const ts = delta => new Timestamp(Date.now() + delta);
const seller = {uid: 'seller', role: 'SELLER'}, moderator = {uid: 'admin', role: 'ADMIN'};
function setup(overrides = {}) {
  const campaign = {id: 'campaign', sellerId: 'seller', status: 'PENDING_APPROVAL', title: 'Rescue',
    batchTargets: {a: 10, b: 90}, targetQuantity: 100, reservedQuantity: 0, rescuedQuantity: 0,
    rescueMode: 'FIXED_POINT', rescueReason: 'OVER_SUPPLY', startDate: ts(-10000), endDate: ts(86400000), locationSharingEnabled: false};
  const seed = {'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'}, 'campaigns/campaign': campaign};
  for (const id of ['a', 'b']) {
    seed[`products/p-${id}`] = {name: id, sellerId: 'seller', status: 'ACTIVE', unit: 'kg', originalPrice: 2000, rescuePrice: 1000};
    seed[`productBatches/${id}`] = {id, productId: `p-${id}`, activeCampaignId: null, status: 'AVAILABLE', initialQuantity: 200,
      availableQuantity: 200, reservedQuantity: 0, soldQuantity: 0, inventoryVersion: 1, expiryDate: ts(864000000)};
  }
  const db = new MemoryFirestore({...seed, ...overrides});
  const lifecycle = createCampaignLifecycle({db, admin, authenticate: async () => seller});
  const commerce = createCommerce({db, admin, authenticate: async () => seller, campaignLifecycle: lifecycle});
  return {db, lifecycle, commerce};
}
const data = (batch = 'a', quantity = 10) => ({requestId: crypto.randomUUID(), items: [{productId: `p-${batch}`, batchId: batch, sellerId: 'seller', quantity}],
  receiverName: 'Buyer', receiverPhone: '0912345678', receiverAddress: '', latitude: 0, longitude: 0,
  fulfillmentType: 'PICKUP', paymentMethod: 'CASH_ON_SITE', note: ''});
const guestToken = 'a'.repeat(64);
const buyer = {role: 'GUEST', accessToken: guestToken};
const identity = {ownerType: 'GUEST', ownerId: 'guest_11111111-1111-1111-1111-111111111111', tokenHash: crypto.createHash('sha256').update(guestToken).digest('hex')};
async function activate(fixture) { await fixture.lifecycle.moderate(moderator, {campaignId: 'campaign', targetStatus: 'ACTIVE'}); }
// Historical manual orders remain readable/processable; new checkout requires SePay.
async function legacyBankCheckout(f, request) {
  await f.commerce.checkout({...request, paymentMethod: 'CASH_ON_SITE'}, identity);
  const op = 'orders/' + request.requestId, pp = 'payments/' + request.requestId;
  f.db.documents.set(op, {...f.db.snapshot(op).data(), paymentMethod: 'BANK_TRANSFER', paymentStatus: 'PENDING'});
  f.db.documents.set(pp, {...f.db.snapshot(pp).data(), method: 'BANK_TRANSFER', status: 'PENDING', verificationProvider: 'MANUAL'});
}
async function finish(fixture, id) {
  for (const targetStatus of ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'DELIVERED'])
    await fixture.lifecycle.transitionOrder(targetStatus === 'DELIVERED' ? buyer : seller, {orderId: id, targetStatus});
}

function expireA(f) {
  f.db.documents.set('productBatches/a', {...f.db.snapshot('productBatches/a').data(), expiryDate: ts(-1000)});
}

const resolution = (orderId, mode = 'REFUND_CANCEL') => ({orderId, mode, requestId: 'resolution-1', reason: 'Đã đối chiếu chứng từ thực tế',
  evidenceUrl: 'https://example.test/proof.png', refundReference: 'REFUND-001', confirmRefunded: true});
function markPaid(f, id) {
  for (const path of ['orders/' + id, 'payments/' + id]) f.db.documents.set(path,
    {...f.db.snapshot(path).data(), ...(path.startsWith('orders') ? {paymentStatus: 'PAID', expiryReviewRequired: true} : {status: 'PAID'})});
}
test('admin full-refund reconciliation returns stock and campaign allocation once and preserves payment proof', async () => {
  const f = setup(); await activate(f); const request = data(); await f.commerce.checkout(request, identity);
  markPaid(f, request.requestId); expireA(f);
  const command = {...resolution(request.requestId), refundAmount: f.db.snapshot('orders/' + request.requestId).get('totalAmount')};
  await assert.rejects(f.lifecycle.resolveOrder(seller, command), e => e.status === 403);
  await assert.rejects(f.lifecycle.resolveOrder(moderator, {...command, refundAmount: 1}));
  assert.equal(f.db.snapshot('productBatches/a').get('reservedQuantity'), 10);
  await f.lifecycle.resolveOrder(moderator, command);
  assert.equal((await f.lifecycle.resolveOrder(moderator, command)).replay, true);
  await assert.rejects(f.lifecycle.resolveOrder(moderator, {...command, reason: 'changed'}), e => e.status === 409);
  assert.equal(f.db.snapshot('orders/' + request.requestId).get('status'), 'CANCELLED');
  assert.equal(f.db.snapshot('orders/' + request.requestId).get('expiryReviewRequired'), false);
  assert.equal(f.db.snapshot('payments/' + request.requestId).get('status'), 'REFUNDED');
  assert.equal(f.db.snapshot('productBatches/a').get('availableQuantity'), 200);
  assert.equal(f.db.snapshot('productBatches/a').get('reservedQuantity'), 0);
  assert.equal(f.db.snapshot('campaigns/campaign').get('reservedByBatch').a, 0);
});
test('late receipt requires server-recorded pre-expiry handover and buyer acknowledgment; no backdating', async () => {
  const f = setup(), request = data(); await f.commerce.checkout(request, identity); markPaid(f, request.requestId); expireA(f);
  const command = resolution(request.requestId, 'LATE_RECEIPT');
  await assert.rejects(f.lifecycle.resolveOrder(moderator, command));
  const orderPath = 'orders/' + request.requestId;
  f.db.documents.set(orderPath, {...f.db.snapshot(orderPath).data(), handoverRecordedAt: ts(-5000), handoverEvidenceUrl: 'https://example.test/handover', buyerReceiptAcknowledgedAt: ts(-1)});
  await f.lifecycle.resolveOrder(moderator, command);
  assert.equal(f.db.snapshot(orderPath).get('status'), 'DELIVERED');
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 10);
  assert.equal(f.db.snapshot('productBatches/a').get('reservedQuantity'), 0);
});
test('shipped or handed-over stock cannot be released by refund resolution without recorded physical return', async () => {
  const f = setup(), request = data(); await f.commerce.checkout(request, identity); markPaid(f, request.requestId); expireA(f);
  const orderPath = 'orders/' + request.requestId;
  f.db.documents.set(orderPath, {...f.db.snapshot(orderPath).data(), handoverRecordedAt: ts(-5000)});
  const command = {...resolution(request.requestId), refundAmount: f.db.snapshot(orderPath).get('totalAmount'), confirmReturned: true};
  await assert.rejects(f.lifecycle.resolveOrder(moderator, command));
  f.db.documents.set(orderPath, {...f.db.snapshot(orderPath).data(), returnRecordedAt: ts(-100), returnEvidenceUrl: 'https://example.test/returned'});
  await assert.rejects(f.lifecycle.resolveOrder(moderator, {...command, mode: 'LATE_RECEIPT'}), e => e.status === 409);
  await f.lifecycle.resolveOrder(moderator, command);
  assert.equal(f.db.snapshot(orderPath).get('status'), 'CANCELLED');
});
test('reconciliation never reports success for a legacy terminal order without a matching resolution record', async () => {
  const f = setup(), request = data(); await f.commerce.checkout(request, identity); await finish(f, request.requestId);
  await assert.rejects(f.lifecycle.resolveOrder(moderator, resolution(request.requestId, 'LATE_RECEIPT')), e => e.status === 409);
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 10);
  assert.equal((await f.db.collection('orderReconciliationEvents').get()).size, 0);
});

test('expired batches block forward order transitions atomically including pickup receipt', async () => {
  const f = setup(), request = data();
  await f.commerce.checkout(request, identity);
  for (const targetStatus of ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP'])
    await f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus});
  expireA(f);
  await assert.rejects(() => f.lifecycle.transitionOrder(buyer, {orderId: request.requestId, targetStatus: 'DELIVERED'}), /hết hạn/);
  assert.equal(f.db.snapshot('productBatches/a').get('reservedQuantity'), 10);
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 0);
  assert.equal(f.db.snapshot(`orders/${request.requestId}`).get('status'), 'READY_FOR_PICKUP');
});

test('expiry sweep cancels unpaid cash orders once, records detection and preserves physical stock', async () => {
  const f = setup(); await activate(f); const request = data();
  await f.commerce.checkout(request, identity); expireA(f);
  const worker = createBatchExpiry({db: f.db, admin, campaignLifecycle: f.lifecycle});
  await worker.reconcile(); await worker.reconcile();
  const batch = f.db.snapshot('productBatches/a').data();
  assert.equal(batch.status, 'EXPIRED'); assert.equal(batch.availableQuantity, 200);
  assert.equal(batch.reservedQuantity, 0); assert.equal(batch.soldQuantity, 0);
  assert.equal(batch.initialQuantity, batch.availableQuantity + batch.reservedQuantity + batch.soldQuantity);
  assert.equal(f.db.snapshot(`orders/${request.requestId}`).get('cancellationReason'), 'BATCH_EXPIRED');
  assert.equal(f.db.snapshot('campaigns/campaign').get('reservedByBatch').a, 0);
  assert.equal((await f.db.collection('batchExpiryEvents').get()).size, 1);
  assert.equal(f.db.snapshot('batchExpiryEvents/a').get('availableQuantityAtDetection'), 190);
});

test('expiry sweep retains paid orders for review without fabricated refunds or consumed stock', async () => {
  const f = setup(), request = data(); await legacyBankCheckout(f, request);
  await f.lifecycle.confirmBankTransfer(seller, {orderId: request.requestId, referenceCode: 'BANK-EXPIRY'});
  expireA(f);
  const worker = createBatchExpiry({db: f.db, admin, campaignLifecycle: f.lifecycle});
  await worker.reconcile(); await worker.reconcile();
  const order = f.db.snapshot(`orders/${request.requestId}`).data();
  assert.equal(order.paymentStatus, 'PAID'); assert.equal(order.status, 'CONFIRMED');
  assert.equal(order.expiryReviewRequired, true);
  assert.equal(f.db.snapshot('productBatches/a').get('reservedQuantity'), 10);
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 0);
  await assert.rejects(() => f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus: 'PREPARING'}), /đối soát/);
});
test('expiry sweep skips finalized order history and avoids rereading already-audited batches every minute',async()=>{
  const f=setup();
  f.db.documents.set('productBatches/a',{...f.db.snapshot('productBatches/a').data(),status:'EXPIRED',expiryDate:ts(-1000),expiredAt:ts(-500)});
  f.db.documents.set('orders/done',{status:'DELIVERED',batchReservations:{a:1},inventoryFinalized:true});
  let transactions=0;const run=f.db.runTransaction.bind(f.db);
  f.db.runTransaction=async callback=>{transactions++;return run(callback);};
  const worker=createBatchExpiry({db:f.db,admin,campaignLifecycle:f.lifecycle});
  const first=await worker.reconcile(),second=await worker.reconcile();
  assert.equal(first.orders,0);assert.equal(second.orders,0);assert.equal(transactions,0);
});
test('quota failure never advances the historical expiry cursor past unfinished audit work',async()=>{
  const f=setup();
  f.db.documents.set('productBatches/a',{...f.db.snapshot('productBatches/a').data(),status:'EXPIRED',expiryDate:ts(-1000)});
  let fail=true;const run=f.db.runTransaction.bind(f.db);
  f.db.runTransaction=async callback=>{if(fail){fail=false;throw Object.assign(Error('Quota exceeded'),{code:8});}return run(callback);};
  const worker=createBatchExpiry({db:f.db,admin,campaignLifecycle:f.lifecycle});
  await assert.rejects(()=>worker.reconcile(),e=>e.code===8);
  await worker.reconcile();assert.equal(f.db.snapshot('batchExpiryEvents/a').exists,true);
});

test('expiry pagination eventually visits later batches, preserves archived status and completed history', async () => {
  const f = setup(), request = data(); await f.commerce.checkout(request, identity); await finish(f, request.requestId);
  expireA(f);
  f.db.documents.set('productBatches/b', {...f.db.snapshot('productBatches/b').data(), expiryDate: ts(-500), status: 'ARCHIVED'});
  let clock = Date.now();
  const worker = createBatchExpiry({db: f.db, admin, campaignLifecycle: f.lifecycle, pageSize: 1, now: () => clock});
  await worker.reconcile(); clock += 3600001; await worker.reconcile(); clock += 3600001; await worker.reconcile();
  assert.equal((await f.db.collection('batchExpiryEvents').get()).size, 2);
  assert.equal(f.db.snapshot('productBatches/b').get('status'), 'ARCHIVED');
  assert.equal(f.db.snapshot(`orders/${request.requestId}`).get('status'), 'DELIVERED');
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 10);
});

test('approval links batches atomically, initializes allocation maps, and is idempotent', async () => {
  const f = setup(); await activate(f);
  assert.equal(f.db.snapshot('productBatches/a').get('activeCampaignId'), 'campaign');
  assert.equal(f.db.snapshot('campaigns/campaign').get('allocationVersion'), 1);
  await activate(f);
  assert.equal((await f.db.collection('adminAuditLogs').get()).size, 1);
  const audit = (await f.db.collection('adminAuditLogs').get()).docs[0].data();
  assert.equal(audit.resourceType, 'CAMPAIGNS'); assert.equal(audit.fromState, 'PENDING_APPROVAL');
  assert.equal(audit.toState, 'ACTIVE');
});

for (const kind of ['expired-batch', 'expired-campaign', 'seller-blocked', 'seller-unapproved', 'wrong-owner', 'product-hidden', 'insufficient', 'conflict', 'invalid-target']) {
  test('approval revalidates and rolls back: ' + kind, async () => {
    const f = setup();
    const mutate = (path, fields) => f.db.documents.set(path, {...f.db.snapshot(path).data(), ...fields});
    if (kind === 'expired-batch') mutate('productBatches/a', {expiryDate: ts(-1)});
    if (kind === 'expired-campaign') mutate('campaigns/campaign', {endDate: ts(-1)});
    if (kind === 'seller-blocked') mutate('users/seller', {status: 'BLOCKED'});
    if (kind === 'seller-unapproved') mutate('users/seller', {sellerStatus: 'REJECTED'});
    if (kind === 'wrong-owner') mutate('products/p-a', {sellerId: 'other'});
    if (kind === 'product-hidden') mutate('products/p-a', {status: 'HIDDEN'});
    if (kind === 'insufficient') mutate('productBatches/a', {availableQuantity: 5, initialQuantity: 5});
    if (kind === 'invalid-target') mutate('campaigns/campaign', {targetQuantity: 101});
    if (kind === 'conflict') { mutate('productBatches/a', {activeCampaignId: 'other'}); f.db.documents.set('campaigns/other', {...f.db.snapshot('campaigns/campaign').data(), id: 'other', status: 'ACTIVE'}); }
    await assert.rejects(() => activate(f));
    assert.equal(f.db.snapshot('campaigns/campaign').get('status'), 'PENDING_APPROVAL');
    assert.notEqual(f.db.snapshot('productBatches/b').get('activeCampaignId'), 'campaign');
    assert.equal((await f.db.collection('adminAuditLogs').get()).size, 0);
  });
}

test('two buyers cannot exceed the same batch allocation even with global capacity', async () => {
  const f = setup(); await activate(f);
  const results = await Promise.allSettled([f.commerce.checkout(data(), identity), f.commerce.checkout(data(), identity)]);
  assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(f.db.snapshot('campaigns/campaign').get('reservedByBatch').a, 10);
  assert.equal(f.db.snapshot('productBatches/a').get('availableQuantity'), 190);
  assert.equal((await f.db.collection('orders').get()).size, 1);
});

test('cancel returns exactly that batch allocation and replay does not release twice', async () => {
  const f = setup(); await activate(f); const request = data();
  await f.commerce.checkout(request, identity);
  const command = {orderId: request.requestId, targetStatus: 'CANCELLED'};
  await f.lifecycle.transitionOrder(seller, command); await f.lifecycle.transitionOrder(seller, command);
  assert.equal(f.db.snapshot('campaigns/campaign').get('reservedByBatch').a, 0);
  assert.equal(f.db.snapshot('productBatches/a').get('availableQuantity'), 200);
  await f.commerce.checkout(data(), identity);
  assert.equal(f.db.snapshot('campaigns/campaign').get('reservedByBatch').a, 10);
});

test('delivery consumes allocation permanently, completion unlinks residual stock', async () => {
  const f = setup(); await activate(f);
  const a = data(), b = data('b', 90);
  await f.commerce.checkout(a, identity); await finish(f, a.requestId);
  await assert.rejects(() => f.commerce.checkout(data(), identity), /phân bổ/);
  await f.commerce.checkout(b, identity); await finish(f, b.requestId);
  const campaign = f.db.snapshot('campaigns/campaign').data();
  assert.equal(campaign.status, 'COMPLETED'); assert.equal(campaign.rescuedByBatch.a, 10); assert.equal(campaign.rescuedByBatch.b, 90);
  assert.equal(campaign.reservedQuantity, 0);
  assert.equal(f.db.snapshot('productBatches/a').get('activeCampaignId'), null);
  const regular = await f.commerce.checkout(data('a', 20), identity);
  assert.equal(regular.order.campaignId, '');
});

for (const status of ['PENDING_APPROVAL', 'REJECTED', 'COMPLETED', 'EXPIRED', 'STOPPED']) {
  test('legacy ' + status + ' link no longer blocks ordinary checkout', async () => {
    const f = setup();
    f.db.documents.set('campaigns/campaign', {...f.db.snapshot('campaigns/campaign').data(), status});
    f.db.documents.set('productBatches/a', {...f.db.snapshot('productBatches/a').data(), activeCampaignId: 'campaign'});
    const result = await f.commerce.checkout(data(), identity);
    assert.equal(result.order.campaignId, '');
    assert.equal(f.db.snapshot('productBatches/a').get('activeCampaignId'), null);
  });
}

test('expired active campaign is closed on quote/checkout and scheduled reconciliation', async () => {
  const f = setup(); await activate(f);
  f.db.documents.set('campaigns/campaign', {...f.db.snapshot('campaigns/campaign').data(), endDate: ts(-1)});
  await f.lifecycle.reconcileExpired();
  assert.equal(f.db.snapshot('campaigns/campaign').get('status'), 'EXPIRED');
  assert.equal(f.db.snapshot('productBatches/b').get('activeCampaignId'), null);
  assert.equal((await f.commerce.checkout(data(), identity)).order.campaignId, '');
});

for (const targetStatus of ['DELIVERED', 'CANCELLED']) {
  test('old order ' + targetStatus + ' preserves a newer campaign link and allocations', async () => {
    const f = setup(); await activate(f); const request = data(); await f.commerce.checkout(request, identity);
    await f.lifecycle.moderate(moderator, {campaignId: 'campaign', targetStatus: 'STOPPED', reason: 'Stop old campaign'});
    const next = {...f.db.snapshot('campaigns/campaign').data(), id: 'next', status: 'PENDING_APPROVAL', reservedQuantity: 0, rescuedQuantity: 0, reservedByBatch: {}, rescuedByBatch: {}};
    f.db.documents.set('campaigns/next', next);
    await f.lifecycle.moderate(moderator, {campaignId: 'next', targetStatus: 'ACTIVE'});
    if (targetStatus === 'DELIVERED') await finish(f, request.requestId);
    else await f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus});
    assert.equal(f.db.snapshot('productBatches/a').get('activeCampaignId'), 'next');
    assert.equal(f.db.snapshot('campaigns/next').get('reservedQuantity'), 0);
    assert.equal(f.db.snapshot('campaigns/campaign').get('reservedQuantity'), 0);
    assert.equal(f.db.snapshot('campaigns/campaign').get('rescuedQuantity'), targetStatus === 'DELIVERED' ? 10 : 0);
    const batch = f.db.snapshot('productBatches/a').data();
    assert.equal(batch.availableQuantity + batch.reservedQuantity + batch.soldQuantity, batch.initialQuantity);
  });
}

test('legacy allocation is reconstructed from orders and ambiguous counters block safely', async () => {
  const f = setup(); await activate(f); const request = data(); await f.commerce.checkout(request, identity);
  const campaign = f.db.snapshot('campaigns/campaign').data();
  delete campaign.allocationVersion; delete campaign.reservedByBatch; delete campaign.rescuedByBatch;
  f.db.documents.set('campaigns/campaign', campaign);
  const progress = await readAllocations(ref => ref.get(), f.db, 'campaign', campaign);
  assert.equal(progress.reserved.a, 10);
  await assert.rejects(() => readAllocations(ref => ref.get(), f.db, 'campaign', {...campaign, reservedQuantity: 11}), /đối soát/);
});

test('moderation and order commands reject unauthorized actors', async () => {
  const f = setup();
  await assert.rejects(() => f.lifecycle.moderate(seller, {campaignId: 'campaign', targetStatus: 'ACTIVE'}), e => e.status === 403);
  await activate(f); const request = data(); await f.commerce.checkout(request, identity);
  await assert.rejects(() => f.lifecycle.transitionOrder({role: 'SELLER', uid: 'other'}, {orderId: request.requestId, targetStatus: 'CANCELLED'}), e => e.status === 403);
  await assert.rejects(() => f.lifecycle.transitionOrder({role: 'CUSTOMER', uid: 'customer'}, {orderId: request.requestId, targetStatus: 'CANCELLED'}), e => e.status === 403);
});

for (const fulfillmentType of ['DELIVERY', 'ON_SITE_RESCUE']) {
  test(fulfillmentType + ' delivery finalizes batch allocations and payment atomically', async () => {
    const f = setup(); await activate(f);
    f.db.documents.set('sellerPublicProfiles/seller', {latitude: 10.8, longitude: 106.7});
    f.db.documents.set('appConfig/shipping', {baseFeeVnd: 1000, perKmFeeVnd: 100, freeDistanceKm: 0});
    const request = {...data(), fulfillmentType, latitude: 10.8, longitude: 106.7,
      paymentMethod: fulfillmentType === 'DELIVERY' ? 'COD' : 'CASH_ON_SITE', receiverAddress: 'Test address'};
    await f.commerce.checkout(request, identity);
    const states = fulfillmentType === 'DELIVERY' ? ['CONFIRMED', 'PREPARING', 'SHIPPING', 'DELIVERED'] : ['CONFIRMED', 'PREPARING', 'DELIVERED'];
    for (const targetStatus of states) await f.lifecycle.transitionOrder(targetStatus === 'DELIVERED' ? buyer : seller, {orderId: request.requestId, targetStatus});
    assert.equal(f.db.snapshot('campaigns/campaign').get('rescuedByBatch').a, 10);
    assert.equal(f.db.snapshot(`payments/${request.requestId}`).get('status'), 'PAID');
    if (fulfillmentType === 'DELIVERY') assert.equal(f.db.snapshot(`shipments/${request.requestId}`).get('status'), 'DELIVERED');
  });
}

test('legacy bank transfer atomically confirms payment and order before preparation; only buyer completes receipt', async () => {
  const f = setup(); await activate(f);
  f.db.documents.set('sellerPaymentProfiles/seller', {bankCode: 'VCB', bankAccountName: 'Test', bankAccountNumber: '123456'});
  const request = {...data(), paymentMethod: 'BANK_TRANSFER'}; await legacyBankCheckout(f, request);
  const id = request.requestId;
  await assert.rejects(() => f.lifecycle.transitionOrder(seller, {orderId: id, targetStatus: 'CONFIRMED'}), /mã giao dịch/);
  await assert.rejects(() => f.lifecycle.confirmBankTransfer(seller, {orderId: id, referenceCode: ''}), e => e.status === 400);
  assert.equal(f.db.snapshot(`orders/${id}`).get('status'), 'PENDING');
  await f.lifecycle.confirmBankTransfer(seller, {orderId: id, referenceCode: 'BANK-123'});
  assert.equal(f.db.snapshot(`orders/${id}`).get('status'), 'CONFIRMED');
  assert.equal(f.db.snapshot(`orders/${id}`).get('paymentStatus'), 'PAID');
  assert.equal(f.db.snapshot(`payments/${id}`).get('referenceCode'), 'BANK-123');
  assert.equal((await f.lifecycle.confirmBankTransfer(seller, {orderId: id, referenceCode: 'BANK-123'})).replay, true);
  await assert.rejects(() => f.lifecycle.confirmBankTransfer(seller, {orderId: id, referenceCode: 'OTHER'}), /khác/);
  await assert.rejects(() => f.lifecycle.transitionOrder(buyer, {orderId: id, targetStatus: 'DELIVERED'}), e => e.status === 409);
  for (const targetStatus of ['PREPARING', 'READY_FOR_PICKUP']) await f.lifecycle.transitionOrder(seller, {orderId: id, targetStatus});
  await assert.rejects(() => f.lifecycle.transitionOrder(seller, {orderId: id, targetStatus: 'CANCELLED'}), /hoàn tiền/);
  await assert.rejects(() => f.lifecycle.transitionOrder(seller, {orderId: id, targetStatus: 'DELIVERED'}), e => e.status === 403);
  await f.lifecycle.transitionOrder(buyer, {orderId: id, targetStatus: 'DELIVERED'});
  assert.equal(f.db.snapshot('campaigns/campaign').get('rescuedByBatch').a, 10);
  assert.equal(f.db.snapshot(`orders/${id}`).get('receivedBy'), identity.ownerId);
});

test('COD receipt requires the actual customer and finalizes payment and inventory exactly once', async () => {
  const f = setup(); await activate(f);
  f.db.documents.set('sellerPublicProfiles/seller', {latitude: 10.8, longitude: 106.7});
  f.db.documents.set('appConfig/shipping', {baseFeeVnd: 1000, perKmFeeVnd: 100, freeDistanceKm: 0});
  const request = {...data(), fulfillmentType: 'DELIVERY', paymentMethod: 'COD',
    latitude: 10.8, longitude: 106.7, receiverAddress: '12 đường A'};
  f.db.documents.set('users/customer', {role: 'CUSTOMER', status: 'ACTIVE'});
  await f.commerce.checkout(request, {ownerType: 'CUSTOMER', ownerId: 'customer'});
  const command = {orderId: request.requestId, targetStatus: 'DELIVERED'};
  const customer = {uid: 'customer', role: 'CUSTOMER'};
  await assert.rejects(() => f.lifecycle.transitionOrder(customer, command), e => e.status === 409);
  for (const targetStatus of ['CONFIRMED', 'PREPARING', 'SHIPPING'])
    await f.lifecycle.transitionOrder(seller, {...command, targetStatus});
  for (const actor of [seller, moderator, {uid: 'other', role: 'CUSTOMER'},
    {role: 'GUEST', accessToken: guestToken}])
    await assert.rejects(() => f.lifecycle.transitionOrder(actor, command), e => e.status === 403);
  assert.equal(f.db.snapshot(`payments/${request.requestId}`).get('status'), 'UNPAID');
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 0);
  await f.lifecycle.transitionOrder(customer, command);
  assert.equal(f.db.snapshot(`payments/${request.requestId}`).get('status'), 'PAID');
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 10);
  assert.equal(f.db.snapshot(`notifications/${request.requestId}_DELIVERED`).get('body'),
    'Trạng thái: Đã thanh toán và nhận đơn');
  assert.equal(f.db.snapshot(`notifications/${request.requestId}_DELIVERED_seller`).get('userId'), 'seller');
  assert.equal((await f.lifecycle.transitionOrder(customer, command)).replay, true);
  assert.equal(f.db.snapshot('productBatches/a').get('soldQuantity'), 10);
});

test('guest receipt requires its matching checkout token and bank confirmation requires the owning seller', async () => {
  const f = setup(); await activate(f);
  f.db.documents.set('sellerPaymentProfiles/seller', {bankCode: 'MB', bankAccountName: 'Test', bankAccountNumber: '123456'});
  const request = {...data(), paymentMethod: 'BANK_TRANSFER'}, command = {orderId: request.requestId, referenceCode: 'TX123'};
  await legacyBankCheckout(f, request);
  for (const actor of [moderator, {role: 'CUSTOMER', uid: 'customer'}, {role: 'SELLER', uid: 'other'}])
    await assert.rejects(() => f.lifecycle.confirmBankTransfer(actor, command), e => e.status === 403);
  await f.lifecycle.confirmBankTransfer(seller, command);
  for (const targetStatus of ['PREPARING', 'READY_FOR_PICKUP'])
    await f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus});
  for (const accessToken of ['', 'b'.repeat(64)])
    await assert.rejects(() => f.lifecycle.transitionOrder({role: 'GUEST', accessToken},
      {orderId: request.requestId, targetStatus: 'DELIVERED'}), e => e.status === 403);
  await f.lifecycle.transitionOrder(buyer, {orderId: request.requestId, targetStatus: 'DELIVERED'});
  assert.equal(f.db.snapshot(`payments/${request.requestId}`).get('referenceCode'), 'TX123');
});

test('guest receipt confirms successfully using lookup grant access token and rejects invalid or expired grants', async () => {
  const f = setup(); await activate(f);
  const request = {...data(), fulfillmentType: 'PICKUP', paymentMethod: 'CASH_ON_SITE'};
  await f.commerce.checkout(request, identity);
  const orderId = request.requestId;
  for (const targetStatus of ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP'])
    await f.lifecycle.transitionOrder(seller, {orderId, targetStatus});

  const lookupToken = 'c'.repeat(64);
  const lookupHash = crypto.createHash('sha256').update(lookupToken).digest('hex');
  f.db.documents.set(`guestOrderGrants/${lookupHash}`, {orderId, expiresAt: ts(86400000)});

  const expiredToken = 'd'.repeat(64);
  const expiredHash = crypto.createHash('sha256').update(expiredToken).digest('hex');
  f.db.documents.set(`guestOrderGrants/${expiredHash}`, {orderId, expiresAt: ts(-1000)});
  await assert.rejects(() => f.lifecycle.transitionOrder({role: 'GUEST', accessToken: expiredToken},
    {orderId, targetStatus: 'DELIVERED'}), e => e.status === 403);

  const wrongOrderToken = 'e'.repeat(64);
  const wrongOrderHash = crypto.createHash('sha256').update(wrongOrderToken).digest('hex');
  f.db.documents.set(`guestOrderGrants/${wrongOrderHash}`, {orderId: 'other-order', expiresAt: ts(86400000)});
  await assert.rejects(() => f.lifecycle.transitionOrder({role: 'GUEST', accessToken: wrongOrderToken},
    {orderId, targetStatus: 'DELIVERED'}), e => e.status === 403);

  await f.lifecycle.transitionOrder({role: 'GUEST', accessToken: lookupToken},
    {orderId, targetStatus: 'DELIVERED'});
  assert.equal(f.db.snapshot(`orders/${orderId}`).get('status'), 'DELIVERED');
  assert.equal(f.db.snapshot(`orders/${orderId}`).get('paymentStatus'), 'PAID');
  assert.equal(f.db.snapshot(`orders/${orderId}`).get('receivedBy'), identity.ownerId);
});

test('mixed-unit approval is rejected atomically even when numeric targets fit', async () => {
  const f = setup(); f.db.documents.set('products/p-b', {...f.db.snapshot('products/p-b').data(), unit: 'bó'});
  await assert.rejects(() => activate(f), /cùng một đơn vị/);
  assert.equal(f.db.snapshot('campaigns/campaign').get('status'), 'PENDING_APPROVAL');
  assert.equal(f.db.snapshot('productBatches/a').get('activeCampaignId'), null);
});
test('approval cannot silently relabel an explicitly declared campaign unit', async () => {
  const f = setup(); f.db.documents.set('campaigns/campaign', {...f.db.snapshot('campaigns/campaign').data(), quantityUnit: 'bó', unitStatus: 'DECLARED'});
  await assert.rejects(() => activate(f), /Đơn vị khai báo/);
});
test('legacy Campaign migration flags mixed units instead of labelling their sums kg', async () => {
  const f = setup(); f.db.documents.set('products/p-b', {...f.db.snapshot('products/p-b').data(), unit: 'bó'});
  await f.lifecycle.reconcileCampaign('campaign');
  assert.equal(f.db.snapshot('campaigns/campaign').get('unitStatus'), 'REVIEW_REQUIRED');
});
test('legacy campaign verifies actual order item units before becoming kg analytics data', async () => {
  const f = setup(); await activate(f); const request = data(); await f.commerce.checkout(request, identity);
  const campaign = f.db.snapshot('campaigns/campaign').data(); delete campaign.unitStatus; delete campaign.quantityUnit;
  f.db.documents.set('campaigns/campaign', campaign);
  f.db.documents.set(`orders/${request.requestId}/items/a`, {...f.db.snapshot(`orders/${request.requestId}/items/a`).data(), unitSnapshot: 'bao'});
  await f.lifecycle.reconcileCampaign('campaign');
  assert.equal(f.db.snapshot('campaigns/campaign').get('unitStatus'), 'REVIEW_REQUIRED');
  await assert.rejects(() => f.commerce.checkout(data(), identity), /xác minh/);
});
