import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, setDoc,
  runTransaction, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
const now = Date.now();
const past = Timestamp.fromMillis(now - 86400000);
const future = Timestamp.fromMillis(now + 7 * 86400000);
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-rescuefarm',
    firestore: { host: '127.0.0.1', port: 8088,
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') }
  });
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed({
    'users/customer-a': { role: 'CUSTOMER', status: 'ACTIVE' },
    'users/customer-b': { role: 'CUSTOMER', status: 'ACTIVE' },
    'users/blocked': { role: 'CUSTOMER', status: 'BLOCKED' },
    'users/seller': { role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED' },
    'products/product-a': { sellerId: 'seller', status: 'ACTIVE', rescuePrice: 30000 },
    'products/hidden': { sellerId: 'seller', status: 'HIDDEN', rescuePrice: 30000 },
    'productBatches/batch-a': {
      id: 'batch-a', productId: 'product-a', activeCampaignId: '',
      harvestDate: past, expiryDate: future, initialQuantity: 10,
      availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0,
      inventoryVersion: 0, status: 'AVAILABLE', createdAt: past, updatedAt: past
    },
    'orders/existing': { ownerType: 'CUSTOMER', ownerId: 'customer-a', sellerId: 'seller' }
  });
});

async function seed(values) {
  await env.withSecurityRulesDisabled(async context => {
    await Promise.all(Object.entries(values).map(([path, value]) =>
      setDoc(doc(context.firestore(), path), value)));
  });
}

test('active customer can check a missing order; guest and blocked customer cannot', async () => {
  const db = env.authenticatedContext('customer-a').firestore();
  const snapshot = await assertSucceeds(getDoc(doc(db, 'orders/new-order')));
  assert.equal(snapshot.exists(), false);
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'orders/new-order')));
  await assertFails(getDoc(doc(env.authenticatedContext('blocked').firestore(), 'orders/new-order')));
});

test('existing orders remain private, including collection queries', async () => {
  const owner = env.authenticatedContext('customer-a').firestore();
  const other = env.authenticatedContext('customer-b').firestore();
  await assertSucceeds(getDoc(doc(owner, 'orders/existing')));
  await assertFails(getDoc(doc(other, 'orders/existing')));
  await assertSucceeds(getDocs(query(collection(owner, 'orders'), where('ownerId', '==', 'customer-a'),
    where('ownerType', '==', 'CUSTOMER'))));
  await assertFails(getDocs(collection(other, 'orders')));
});

test('promotion fallback is limited to active customers and active products', async () => {
  const customer = env.authenticatedContext('customer-a').firestore();
  assert.equal((await assertSucceeds(getDoc(doc(customer, 'promotions/product-a')))).exists(), false);
  await assertFails(getDoc(doc(customer, 'promotions/hidden')));
  await assertFails(getDoc(doc(customer, 'promotions/nonexistent-product')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'promotions/product-a')));
  await assertFails(getDoc(doc(env.authenticatedContext('blocked').firestore(), 'promotions/product-a')));
});

// Mirrors the Android transaction's initial read, promotion read and atomic writes.
async function checkout(db) {
  return runTransaction(db, async tx => {
    const orderRef = doc(db, 'orders/new-order');
    if ((await tx.get(orderRef)).exists()) return;
    await tx.get(doc(db, 'products/product-a'));
    const batch = (await tx.get(doc(db, 'productBatches/batch-a'))).data();
    await tx.get(doc(db, 'promotions/product-a'));
    const order = {
      id: 'new-order', requestId: 'new-order', ownerType: 'CUSTOMER', ownerId: 'customer-a',
      sellerId: 'seller', campaignId: '', orderCode: 'RF-TEST', fulfillmentType: 'DELIVERY',
      receiverName: 'Test Customer', receiverPhone: '0912345678', receiverAddress: 'Test address',
      receiverLatitude: 0, receiverLongitude: 0, subtotal: 90000, quantityDiscount: 0,
      shippingFee: 0, totalAmount: 90000, paymentMethod: 'COD', paymentStatus: 'UNPAID',
      status: 'PENDING', note: '', campaignReservedQuantity: 0,
      batchReservations: { 'batch-a': 3 }, inventoryFinalized: false, finalizedStatus: '',
      createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    };
    tx.set(orderRef, order);
    tx.set(doc(db, 'orders/new-order/items/batch-a'), {
      id: 'batch-a', orderId: 'new-order', batchId: 'batch-a', productId: 'product-a',
      productNameSnapshot: 'Test product', productImageSnapshot: '', unitSnapshot: 'kg',
      originalPriceSnapshot: 40000, rescuePriceSnapshot: 30000, finalPriceSnapshot: 30000,
      quantity: 3, subtotal: 90000
    });
    tx.set(doc(db, 'payments/new-order'), {
      id: 'new-order', orderId: 'new-order', method: 'COD', status: 'UNPAID', amount: 90000,
      referenceCode: '', paidAt: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    });
    tx.set(doc(db, 'shipments/new-order'), {
      id: 'new-order', orderId: 'new-order', carrierName: '', trackingCode: '',
      status: 'PENDING', distanceKm: 0, shippingFee: 0,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    });
    tx.update(doc(db, 'productBatches/batch-a'), {
      availableQuantity: batch.availableQuantity - 3, reservedQuantity: batch.reservedQuantity + 3,
      status: 'RESERVED_PARTIAL', inventoryVersion: batch.inventoryVersion + 1,
      lastReservationOrderId: 'new-order', lastReservationQuantity: 3, updatedAt: serverTimestamp()
    });
  });
}

for (const promotionState of ['missing', 'expired', 'inactive']) {
  test(`DELIVERY/COD checkout succeeds with ${promotionState} promotion and reserves once`, async () => {
    if (promotionState !== 'missing') {
      await seed({ 'promotions/product-a': {
        productId: 'product-a', sellerId: 'seller', active: promotionState !== 'inactive',
        startDate: Timestamp.fromMillis(now - 2 * 86400000),
        endDate: promotionState === 'expired' ? past : future
      } });
    }
    const db = env.authenticatedContext('customer-a').firestore();
    await assertSucceeds(checkout(db));
    await assertSucceeds(checkout(db));
    const batch = (await getDoc(doc(db, 'productBatches/batch-a'))).data();
    assert.equal(batch.availableQuantity, 7);
    assert.equal(batch.reservedQuantity, 3);
    assert.equal(batch.soldQuantity, 0);
    assert.equal((await getDoc(doc(db, 'orders/new-order'))).data().status, 'PENDING');
    assert.equal((await getDoc(doc(db, 'payments/new-order'))).data().status, 'UNPAID');
    assert.equal((await getDoc(doc(db, 'shipments/new-order'))).data().status, 'PENDING');
  });
}

test('a different customer cannot create an order for customer-a', async () => {
  await assertFails(checkout(env.authenticatedContext('customer-b').firestore()));
  const db = env.authenticatedContext('customer-a').firestore();
  assert.equal((await getDoc(doc(db, 'orders/new-order'))).exists(), false);
  assert.equal((await getDoc(doc(db, 'productBatches/batch-a'))).data().availableQuantity, 10);
});
