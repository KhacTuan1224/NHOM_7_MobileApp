const {test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const {MemoryFirestore, Timestamp, admin} = require('./test-support/memory-firestore.cjs');
const {createCampaignLifecycle, readAllocations} = require('./campaign-lifecycle');
const {createCommerce} = require('./commerce');
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
const identity = {ownerType: 'GUEST', ownerId: 'guest_11111111-1111-1111-1111-111111111111', tokenHash: 'hash'};
async function activate(fixture) { await fixture.lifecycle.moderate(moderator, {campaignId: 'campaign', targetStatus: 'ACTIVE'}); }
async function finish(fixture, id) {
  for (const targetStatus of ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'DELIVERED'])
    await fixture.lifecycle.transitionOrder(seller, {orderId: id, targetStatus});
}

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
    for (const targetStatus of states) await f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus});
    assert.equal(f.db.snapshot('campaigns/campaign').get('rescuedByBatch').a, 10);
    assert.equal(f.db.snapshot(`payments/${request.requestId}`).get('status'), 'PAID');
    if (fulfillmentType === 'DELIVERY') assert.equal(f.db.snapshot(`shipments/${request.requestId}`).get('status'), 'DELIVERED');
  });
}

test('bank transfer must be confirmed before delivery and paid orders cannot be cancelled', async () => {
  const f = setup(); await activate(f);
  f.db.documents.set('sellerPaymentProfiles/seller', {bankCode: 'VCB', bankAccountName: 'Test', bankAccountNumber: '123456'});
  const request = {...data(), paymentMethod: 'BANK_TRANSFER'}; await f.commerce.checkout(request, identity);
  for (const targetStatus of ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP'])
    await f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus});
  await assert.rejects(() => f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus: 'DELIVERED'}), /chuyển khoản/);
  for (const path of [`orders/${request.requestId}`, `payments/${request.requestId}`]) {
    const value = f.db.snapshot(path).data();
    f.db.documents.set(path, {...value, ...(path.startsWith('orders/') ? {paymentStatus: 'PAID'} : {status: 'PAID'})});
  }
  await assert.rejects(() => f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus: 'CANCELLED'}), /hoàn tiền/);
  await f.lifecycle.transitionOrder(seller, {orderId: request.requestId, targetStatus: 'DELIVERED'});
  assert.equal(f.db.snapshot('campaigns/campaign').get('rescuedByBatch').a, 10);
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
