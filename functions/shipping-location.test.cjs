const {test} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const {MemoryFirestore, Timestamp, admin} = require('./test-support/memory-firestore.cjs');
const {createCommerce} = require('./commerce');

function fixture() {
  const db = new MemoryFirestore({
    'users/seller': {role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED', fullName: 'Nguyễn An',
      latitude: 21.0285, longitude: 105.8542, address: 'Hồ sơ cá nhân khác vị trí cửa hàng'},
    'users/buyer': {role: 'CUSTOMER', status: 'ACTIVE'},
    'sellerPublicProfiles/seller': {address: '10 đường A, Đồng Tháp', latitude: 10.8, longitude: 106.7},
    'appConfig/shipping': {baseFeeVnd: 15000, perKmFeeVnd: 1000, freeDistanceKm: 2},
    'products/product': {sellerId: 'seller', status: 'ACTIVE', name: 'Rau', unit: 'kg', originalPrice: 40000, rescuePrice: 30000},
    'productBatches/batch': {id: 'batch', productId: 'product', activeCampaignId: '', status: 'AVAILABLE',
      initialQuantity: 10, availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0,
      expiryDate: new Timestamp(Date.now() + 86400000), inventoryVersion: 1}
  });
  const api = createCommerce({db, admin, authenticate: async () => ({uid: 'buyer', role: 'CUSTOMER'})});
  const data = {requestId: crypto.randomUUID(), receiverName: 'Người nhận', receiverPhone: '0912345678',
    receiverAddress: '20 đường B, Đồng Tháp', latitude: 10.8, longitude: 106.7,
    fulfillmentType: 'DELIVERY', paymentMethod: 'COD', note: '',
    items: [{productId: 'product', batchId: 'batch', sellerId: 'seller', quantity: 1}]};
  return {db, api, data, quote: overrides => api.quote(ref => ref.get(), {...data, ...overrides})};
}

test('shipping origin uses the public store GPS, ignoring personal GPS and caller origin fields', async () => {
  const f = fixture();
  const result = (await f.quote({sellerLatitude: 21, sellerLongitude: 105, sellerAddress: 'Forged'})).public;
  assert.equal(result.distanceKm, 0); assert.equal(result.shippingFee, 15000);
  assert.equal(result.sellerAddress, '10 đường A, Đồng Tháp'); assert.equal(result.sellerName, 'Nguyễn An');
});

test('textual administrative addresses do not alter the GPS distance or fee', async () => {
  const f = fixture(); const first = (await f.quote()).public;
  f.db.documents.set('sellerPublicProfiles/seller', {address: 'Địa chỉ hiển thị mới', latitude: 10.8, longitude: 106.7});
  const second = (await f.quote({receiverAddress: 'Tên tỉnh, huyện, phường khác'})).public;
  assert.equal(second.shippingFee, first.shippingFee); assert.equal(second.distanceKm, first.distanceKm);
});

test('missing store GPS never falls back to the seller personal location', async () => {
  const f = fixture(); f.db.documents.set('sellerPublicProfiles/seller', {address: 'Store', latitude: 0, longitude: 0});
  await assert.rejects(() => f.quote(), error => error.status === 409 && /GPS cửa hàng/.test(error.message));
});

test('delivery requires receiver GPS while store pickup remains free without receiver coordinates', async () => {
  const f = fixture();
  await assert.rejects(() => f.quote({latitude: 0, longitude: 0}), error => /nhận hàng chưa có GPS/.test(error.message));
  const result = (await f.quote({latitude: 0, longitude: 0, fulfillmentType: 'PICKUP', paymentMethod: 'CASH_ON_SITE'})).public;
  assert.equal(result.shippingFee, 0); assert.equal(result.sellerAddress, '10 đường A, Đồng Tháp');
});

test('customer and guest quotes use the same origin and fee policy', async () => {
  const f = fixture();
  function call(path) {
    const handle = f.api.router.stack.find(layer => layer.route && layer.route.path === path).route.stack[0].handle;
    return new Promise((resolve, reject) => handle({body: f.data, ip: '127.0.0.1'}, {json: resolve}, reject));
  }
  assert.deepEqual(await call('/quoteCustomerCheckout'), await call('/quoteGuestCheckout'));
});

test('shipment records store and receiver GPS snapshots when the order is placed', async () => {
  const f = fixture();
  await f.api.checkout(f.data, {ownerType: 'CUSTOMER', ownerId: 'buyer'});
  const shipment = f.db.snapshot('shipments/' + f.data.requestId).data();
  assert.equal(shipment.pickupAddress, '10 đường A, Đồng Tháp');
  assert.equal(shipment.pickupLatitude, 10.8); assert.equal(shipment.pickupLongitude, 106.7);
  assert.equal(shipment.receiverLatitude, f.data.latitude); assert.equal(shipment.shippingFee, 15000);
});
