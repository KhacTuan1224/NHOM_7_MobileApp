import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, setDoc,
  deleteDoc, deleteField, runTransaction, serverTimestamp, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';

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
    'users/seller': { id: 'seller', email: 'seller@example.com', fullName: 'Test seller', phone: '', avatarUrl: '',
      role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED', latitude: 0, longitude: 0, createdAt: past, updatedAt: past },
    'users/admin': { role: 'ADMIN', status: 'ACTIVE' },
    'sellerPublicProfiles/seller': { sellerId: 'seller', shopName: 'Test', shippingFee: 15000, latitude: 10.8, longitude: 106.7 },
    'appConfig/shipping': { baseFeeVnd: 15000, perKmFeeVnd: 0, freeDistanceKm: 0 },
    'products/product-a': { sellerId: 'seller', status: 'ACTIVE', rescuePrice: 30000, reviewCount: 0, averageRating: 0 },
    'products/hidden': { sellerId: 'seller', status: 'HIDDEN', rescuePrice: 30000 },
    'productBatches/batch-a': {
      id: 'batch-a', productId: 'product-a', activeCampaignId: '',
      harvestDate: past, expiryDate: future, initialQuantity: 10,
      availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0,
      inventoryVersion: 0, status: 'AVAILABLE', createdAt: past, updatedAt: past
    },
    'orders/existing': { ownerType: 'CUSTOMER', ownerId: 'customer-a', sellerId: 'seller', status: 'DELIVERED' },
    'orders/existing/items/batch-a': { productId: 'product-a' },
    'orders/other/items/batch-b': { productId: 'product-a' },
    'orders/other': { ownerType: 'CUSTOMER', ownerId: 'customer-b', sellerId: 'seller', status: 'DELIVERED' },
    'customerCarts/customer-a': { id: 'customer-a', ownerType: 'CUSTOMER', ownerKey: 'customer-a', updatedAt: past,
      items: [
        { id: 'product-a::batch-a', productId: 'product-a', batchId: 'batch-a', sellerId: 'seller', quantity: 3, unitPrice: 30000, selected: true },
        { id: 'keep', productId: 'product-b', batchId: 'batch-b', sellerId: 'other-seller', quantity: 2, unitPrice: 10000, selected: false }
      ] }
  });
});

async function seed(values) {
  await env.withSecurityRulesDisabled(async context => {
    await Promise.all(Object.entries(values).map(([path, value]) =>
      setDoc(doc(context.firestore(), path), value)));
  });
}

function addressData(owner, id = 'home', isDefault = false) {
  return { id, customerId: owner, receiverName: 'Người nhận', receiverPhone: '0912345678',
    province: 'Thành phố Hà Nội', district: 'Quận Ba Đình', ward: 'Phường Phúc Xá',
    street: '12 Đường Test', latitude: 21.04, longitude: 105.84, type: 'HOME',
    isDefault, updatedAt: serverTimestamp() };
}

test('all three active roles can manage only their own private addresses', async () => {
  for (const owner of ['customer-a', 'seller', 'admin']) {
    const db = env.authenticatedContext(owner).firestore();
    const ref = doc(db, `users/${owner}/addresses/home`);
    await assertSucceeds(setDoc(ref, addressData(owner)));
    await assertSucceeds(getDoc(ref));
    assert.equal((await assertSucceeds(getDocs(collection(db, `users/${owner}/addresses`)))).size, 1);
    await assertSucceeds(updateDoc(ref, { street: '34 Đường Mới', updatedAt: serverTimestamp() }));
    for (const other of ['customer-a', 'seller', 'admin'].filter(id => id !== owner)) {
      const foreign = env.authenticatedContext(other).firestore();
      const foreignRef = doc(foreign, `users/${owner}/addresses/home`);
      await assertFails(getDoc(foreignRef));
      await assertFails(getDocs(collection(foreign, `users/${owner}/addresses`)));
      await assertFails(updateDoc(foreignRef, { street: 'Unauthorized', updatedAt: serverTimestamp() }));
      await assertFails(setDoc(doc(foreign, `users/${owner}/addresses/other`), addressData(owner, 'other')));
      await assertFails(deleteDoc(foreignRef));
    }
    await assertFails(updateDoc(ref, { customerId: 'customer-b', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(ref));
  }
});

test('address permissions reject guests, inactive users and forged roles', async () => {
  for (const role of ['CUSTOMER', 'SELLER', 'ADMIN', 'UNKNOWN']) {
    const owner = `disabled-${role}`;
    await seed({ [`users/${owner}`]: { role, status: role === 'UNKNOWN' ? 'ACTIVE' : 'BLOCKED' } });
    const db = env.authenticatedContext(owner).firestore();
    await assertFails(setDoc(doc(db, `users/${owner}/addresses/home`), addressData(owner)));
    await assertFails(getDocs(collection(db, `users/${owner}/addresses`)));
  }
  const guest = env.unauthenticatedContext().firestore();
  await assertFails(setDoc(doc(guest, 'users/customer-a/addresses/home'), addressData('customer-a')));
  await assertFails(getDocs(collection(guest, 'users/customer-a/addresses')));
});

test('default addresses can be switched atomically by every role and cannot be deleted', async () => {
  for (const [owner, role] of [['customer-a', 'CUSTOMER'], ['seller', 'SELLER'], ['admin', 'ADMIN']]) {
    await seed({ [`users/${owner}`]: {
      id: owner, email: `${owner}@example.com`, fullName: 'Test Owner', phone: '', avatarUrl: '',
      role, status: 'ACTIVE', ...(role === 'SELLER' ? { sellerStatus: 'APPROVED' } : {}),
      latitude: 0, longitude: 0, createdAt: past, updatedAt: past
    } });
    const db = env.authenticatedContext(owner).firestore();
    const user = doc(db, `users/${owner}`);
    const home = doc(db, `users/${owner}/addresses/home`);
    const work = doc(db, `users/${owner}/addresses/work`);
    const first = writeBatch(db);
    first.set(home, addressData(owner, 'home', true));
    first.update(user, { defaultAddressId: 'home', updatedAt: serverTimestamp() });
    await assertSucceeds(first.commit());
    await assertSucceeds(setDoc(work, addressData(owner, 'work')));
    await assertFails(deleteDoc(home));
    await assertSucceeds(runTransaction(db, async tx => {
      await tx.get(user); await tx.get(work);
      tx.update(home, { isDefault: false, updatedAt: serverTimestamp() });
      tx.update(work, { isDefault: true, updatedAt: serverTimestamp() });
      tx.update(user, { defaultAddressId: 'work', updatedAt: serverTimestamp() });
    }));
    assert.equal((await getDoc(home)).data().isDefault, false);
    assert.equal((await getDoc(work)).data().isDefault, true);
    await assertSucceeds(deleteDoc(home));
    await assertFails(deleteDoc(work));
  }
});

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
async function checkout(db, overrideFee = null) {
  return runTransaction(db, async tx => {
    const orderRef = doc(db, 'orders/new-order');
    if ((await tx.get(orderRef)).exists()) return;
    const profile = (await tx.get(doc(db, 'sellerPublicProfiles/seller'))).data();
    const cart = (await tx.get(doc(db, 'customerCarts/customer-a'))).data();
    const fee = overrideFee ?? profile.shippingFee;
    await tx.get(doc(db, 'products/product-a'));
    const batch = (await tx.get(doc(db, 'productBatches/batch-a'))).data();
    await tx.get(doc(db, 'promotions/product-a'));
    const order = {
      id: 'new-order', requestId: 'new-order', ownerType: 'CUSTOMER', ownerId: 'customer-a',
      sellerId: 'seller', campaignId: '', orderCode: 'RF-TEST', fulfillmentType: 'DELIVERY',
      receiverName: 'Test Customer', receiverPhone: '0912345678', receiverAddress: 'Test address',
      receiverLatitude: 10.8, receiverLongitude: 106.7, subtotal: 90000, quantityDiscount: 0,
      shippingFee: fee, totalAmount: 90000 + fee, paymentMethod: 'COD', paymentStatus: 'UNPAID',
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
      id: 'new-order', orderId: 'new-order', method: 'COD', status: 'UNPAID', amount: 90000 + fee,
      referenceCode: '', bankCode: '', bankAccountName: '', bankAccountNumber: '', qrUrl: '',
      paidAt: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    });
    tx.set(doc(db, 'shipments/new-order'), {
      id: 'new-order', orderId: 'new-order', carrierName: '', trackingCode: '',
      status: 'PENDING', distanceKm: 0, shippingFee: fee,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    });
    tx.update(doc(db, 'productBatches/batch-a'), {
      availableQuantity: batch.availableQuantity - 3, reservedQuantity: batch.reservedQuantity + 3,
      status: 'RESERVED_PARTIAL', inventoryVersion: batch.inventoryVersion + 1,
      lastReservationOrderId: 'new-order', lastReservationQuantity: 3, updatedAt: serverTimestamp()
    });
    tx.update(doc(db, 'customerCarts/customer-a'), {
      items: cart.items.filter(item => item.id !== 'product-a::batch-a'), updatedAt: serverTimestamp()
    });
    tx.set(doc(db, 'notifications/new-order_PENDING'), {
      id: 'new-order_PENDING', userId: 'customer-a', type: 'ORDER', title: 'Order created', body: 'Pending',
      referenceId: 'new-order', isRead: false, createdAt: serverTimestamp()
    });
  });
}

for (const promotionState of ['missing', 'expired', 'inactive']) {
  test(`direct client checkout is denied even with ${promotionState} promotion`, async () => {
    if (promotionState !== 'missing') await seed({'promotions/product-a': {
      productId: 'product-a', sellerId: 'seller', active: promotionState !== 'inactive',
      startDate: past, endDate: promotionState === 'expired' ? past : future
    }});
    const db = env.authenticatedContext('customer-a').firestore();
    await assertFails(checkout(db));
    assert.equal((await getDoc(doc(db, 'orders/new-order'))).exists(), false);
    const batch = (await getDoc(doc(db, 'productBatches/batch-a'))).data();
    assert.equal(batch.availableQuantity, 10); assert.equal(batch.reservedQuantity, 0);
    assert.equal((await getDoc(doc(db, 'customerCarts/customer-a'))).data().items.length, 2);
  });
}


test('a different customer cannot create an order for customer-a', async () => {
  await assertFails(checkout(env.authenticatedContext('customer-b').firestore()));
  const db = env.authenticatedContext('customer-a').firestore();
  assert.equal((await getDoc(doc(db, 'orders/new-order'))).exists(), false);
  assert.equal((await getDoc(doc(db, 'productBatches/batch-a'))).data().availableQuantity, 10);
});

async function reviewChange(db, { uid = 'customer-a', orderId = 'existing', itemId = 'batch-a', rating = 5, status = 'PUBLISHED', summary = true, admin = false } = {}) {
  const id = `${uid}_${orderId}_${itemId}`;
  return runTransaction(db, async tx => {
    const ref = doc(db, `reviews/${id}`), productRef = doc(db, 'products/product-a');
    const old = await tx.get(ref), product = (await tx.get(productRef)).data();
    const previous = old.exists() && old.data().status === 'PUBLISHED' ? old.data().rating : 0;
    const next = status === 'PUBLISHED' ? rating : 0;
    const count = product.reviewCount + (next > 0 ? 1 : 0) - (previous > 0 ? 1 : 0);
    const sum = (product.ratingSum ?? product.averageRating * product.reviewCount) + next - previous;
    if (admin) tx.update(ref, { status, moderatedBy: 'admin', moderatedAt: serverTimestamp(), moderationReason: 'Test moderation', updatedAt: serverTimestamp() });
    else tx.set(ref, {
      id, customerId: uid, productId: 'product-a', orderId, orderItemId: itemId, rating,
      content: 'Test review', imageUrls: [], imagePublicIds: [], status,
      createdAt: old.exists() ? old.data().createdAt : serverTimestamp(), updatedAt: serverTimestamp()
    }, { merge: true });
    if (summary) tx.update(productRef, { reviewCount: count, ratingSum: sum,
      averageRating: count === 0 ? 0 : sum / count, lastReviewId: id, updatedAt: serverTimestamp() });
  });
}

test('new review, edits and withdrawal update summary atomically; withdrawn review can be reposted', async () => {
  const a = env.authenticatedContext('customer-a').firestore(), b = env.authenticatedContext('customer-b').firestore();
  await assertSucceeds(reviewChange(a));
  await assertSucceeds(reviewChange(a, { rating: 3 }));
  await assertSucceeds(reviewChange(b, { uid: 'customer-b', orderId: 'other', itemId: 'batch-b', rating: 4 }));
  let product = (await getDoc(doc(a, 'products/product-a'))).data();
  assert.equal(product.reviewCount, 2); assert.equal(product.averageRating, 3.5);
  await assertSucceeds(reviewChange(a, { rating: 3, status: 'DELETED' }));
  product = (await getDoc(doc(a, 'products/product-a'))).data();
  assert.equal(product.reviewCount, 1); assert.equal(product.averageRating, 4);
  assert.equal((await getDoc(doc(a, 'reviews/customer-a_existing_batch-a'))).data().status, 'DELETED');
  await assertSucceeds(reviewChange(a, { rating: 5 }));
  assert.equal((await getDoc(doc(a, 'products/product-a'))).data().averageRating, 4.5);
});

test('reviews require delivered ownership and summary; customers cannot forge summary or read private reviews', async () => {
  const a = env.authenticatedContext('customer-a').firestore(), b = env.authenticatedContext('customer-b').firestore();
  assert.equal((await assertSucceeds(getDoc(doc(a, 'reviews/customer-a_existing_batch-a')))).exists(), false);
  await assertFails(reviewChange(a, { summary: false }));
  await assertFails(reviewChange(b));
  await assertFails(updateDoc(doc(a, 'products/product-a'), { reviewCount: 100, averageRating: 5 }));
  await assertSucceeds(reviewChange(a));
  await assertSucceeds(reviewChange(a, { status: 'DELETED' }));
  await assertFails(getDoc(doc(b, 'reviews/customer-a_existing_batch-a')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'reviews/missing')));
});

test('admin hide and restore also update the published review summary', async () => {
  const a = env.authenticatedContext('customer-a').firestore(), admin = env.authenticatedContext('admin').firestore();
  await assertSucceeds(reviewChange(a));
  await assertSucceeds(reviewChange(admin, { status: 'HIDDEN', admin: true }));
  assert.equal((await getDoc(doc(a, 'products/product-a'))).data().reviewCount, 0);
  await assertFails(reviewChange(a, { rating: 4 }));
  await assertSucceeds(reviewChange(admin, { status: 'PUBLISHED', admin: true }));
  assert.equal((await getDoc(doc(a, 'products/product-a'))).data().reviewCount, 1);
});

test('seller config supports fixed shipping and QR, stays private for writes, and merges existing data', async () => {
  const seller = env.authenticatedContext('seller').firestore(), customer = env.authenticatedContext('customer-a').firestore();
  const config = {
    sellerId: 'seller', shopName: 'Shop', shopDescription: '', shopAvatarUrl: '', address: 'Farm', latitude: 0, longitude: 0,
    shippingFee: 20000, updatedAt: serverTimestamp()
  };
  const payment = {sellerId: 'seller', bankCode: 'VCB', bankAccountName: 'Test seller',
    bankAccountNumber: '123456', qrUrl: 'https://example.com/qr.png', updatedAt: serverTimestamp()};
  const batch = writeBatch(seller);
  batch.set(doc(seller, 'sellerPublicProfiles/seller'), config, { merge: true });
  batch.set(doc(seller, 'sellerPaymentProfiles/seller'), payment, { merge: true });
  batch.update(doc(seller, 'users/seller'), {
    shopName: config.shopName, shopDescription: config.shopDescription, address: config.address,
    bankCode: payment.bankCode, bankAccountName: payment.bankAccountName, bankAccountNumber: payment.bankAccountNumber,
    updatedAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(seller, 'users/seller'))).data().bankAccountNumber, '123456');
  await assertFails(updateDoc(doc(customer, 'sellerPublicProfiles/seller'), { shippingFee: 0, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(seller, 'sellerPublicProfiles/seller'), { shopName: 'Updated', updatedAt: serverTimestamp() }));
  const profile = (await getDoc(doc(customer, 'sellerPublicProfiles/seller'))).data();
  assert.equal(profile.shippingFee, 20000); assert.equal(profile.bankAccountNumber, undefined);
  await assertFails(getDoc(doc(customer, 'sellerPaymentProfiles/seller')));
  assert.equal((await getDoc(doc(seller, 'sellerPaymentProfiles/seller'))).data().qrUrl, 'https://example.com/qr.png');
});

// Mirrors FirebaseUserRepository.updateSellerProfile: all three writes must succeed together.
async function saveSellerProfile(db, sellerId = 'seller', overrides = {}) {
  const profile = {
    representativeName: 'Test representative', shopName: 'Updated shop', shopDescription: 'Fresh produce',
    shopAvatarUrl: '', address: '273 Pham Phu Thu', bankCode: 'MB',
    bankAccountName: 'TEST SELLER', bankAccountNumber: '0123456789',
    latitude: 10.7894883, longitude: 106.6472817, updatedAt: serverTimestamp(), ...overrides
  };
  const batch = writeBatch(db);
  batch.update(doc(db, `users/${sellerId}`), profile);
  batch.set(doc(db, `sellerPublicProfiles/${sellerId}`), {
    sellerId, shopName: profile.shopName, shopDescription: profile.shopDescription,
    shopAvatarUrl: profile.shopAvatarUrl, address: profile.address,
    latitude: profile.latitude, longitude: profile.longitude,
    bankCode: deleteField(), bankAccountName: deleteField(), bankAccountNumber: deleteField(),
    qrUrl: deleteField(), updatedAt: serverTimestamp()
  }, { merge: true });
  batch.set(doc(db, `sellerPaymentProfiles/${sellerId}`), {
    sellerId, bankCode: profile.bankCode, bankAccountName: profile.bankAccountName,
    bankAccountNumber: profile.bankAccountNumber, updatedAt: serverTimestamp()
  }, { merge: true });
  return batch.commit();
}

for (const sellerStatus of ['PENDING_APPROVAL', 'APPROVED', 'REJECTED']) {
  for (const moderated of [false, true]) {
    test(`seller profile saves atomically for ${sellerStatus}, moderation metadata: ${moderated}`, async () => {
      await env.withSecurityRulesDisabled(async context => {
        await updateDoc(doc(context.firestore(), 'users/seller'), {
          sellerStatus,
          ...(moderated ? { moderatedBy: 'admin', moderatedAt: past, moderationReason: 'Reviewed' } : {})
        });
      });
      const seller = env.authenticatedContext('seller').firestore();
      // First save creates the private payment profile.
      await assertSucceeds(saveSellerProfile(seller));
      const saved = (await getDoc(doc(seller, 'users/seller'))).data();
      assert.equal(saved.sellerStatus, sellerStatus);
      assert.equal(saved.bankAccountNumber, '0123456789');
      assert.equal(saved.latitude, 10.7894883);
      assert.equal(saved.moderatedBy, moderated ? 'admin' : undefined);
      assert.equal(saved.moderationReason, moderated ? 'Reviewed' : undefined);
      if (moderated) assert.ok(saved.moderatedAt.isEqual(past));

      // Later saves preserve the configured QR and shipping fee.
      await assertSucceeds(updateDoc(doc(seller, 'sellerPaymentProfiles/seller'), {
        qrUrl: 'https://example.com/qr.png', updatedAt: serverTimestamp()
      }));
      await assertSucceeds(saveSellerProfile(seller, 'seller', { shopName: 'Saved again' }));
      const payment = (await getDoc(doc(seller, 'sellerPaymentProfiles/seller'))).data();
      assert.equal(payment.bankAccountNumber, '0123456789');
      assert.equal(payment.qrUrl, 'https://example.com/qr.png');
      const publicData = (await getDoc(doc(env.unauthenticatedContext().firestore(),
        'sellerPublicProfiles/seller'))).data();
      assert.equal(publicData.shopName, 'Saved again');
      assert.equal(publicData.shippingFee, 15000);
      for (const key of ['bankCode', 'bankAccountName', 'bankAccountNumber', 'qrUrl']) {
        assert.equal(publicData[key], undefined);
      }
      await assertFails(getDoc(doc(env.authenticatedContext('customer-a').firestore(),
        'sellerPaymentProfiles/seller')));
    });
  }
}

test('seller profile keeps moderation fields and approval protected', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await updateDoc(doc(context.firestore(), 'users/seller'), {
      sellerStatus: 'PENDING_APPROVAL', moderatedBy: 'admin', moderatedAt: past, moderationReason: 'Reviewed'
    });
  });
  const seller = env.authenticatedContext('seller').firestore();
  for (const override of [
    { sellerStatus: 'APPROVED' }, { role: 'ADMIN' }, { status: 'BLOCKED' },
    { moderatedBy: 'seller' }, { moderatedAt: serverTimestamp() }, { moderationReason: 'Changed' },
    { moderatedBy: deleteField() }, { moderatedAt: deleteField() }, { moderationReason: deleteField() }
  ]) {
    await assertFails(saveSellerProfile(seller, 'seller', override));
  }
  assert.equal((await getDoc(doc(seller, 'users/seller'))).data().shopName, undefined);
  assert.equal((await getDoc(doc(seller, 'sellerPublicProfiles/seller'))).data().shopName, 'Test');
  assert.equal((await getDoc(doc(seller, 'sellerPaymentProfiles/seller'))).exists(), false);
});

test('seller profile cannot forge moderation metadata or be saved by another account', async () => {
  const seller = env.authenticatedContext('seller').firestore();
  for (const override of [{ moderatedBy: 'admin' }, { moderatedAt: serverTimestamp() },
    { moderationReason: 'Approved' }]) {
    await assertFails(saveSellerProfile(seller, 'seller', override));
  }
  for (const actor of ['customer-a', 'other-seller']) {
    if (actor === 'other-seller') await seed({ 'users/other-seller': {
      role: 'SELLER', status: 'ACTIVE', sellerStatus: 'APPROVED'
    } });
    const other = env.authenticatedContext(actor).firestore();
    await assertFails(saveSellerProfile(other));
    await assertFails(setDoc(doc(other, 'sellerPaymentProfiles/seller'), {
      sellerId: 'seller', bankCode: 'MB', bankAccountName: 'OTHER', bankAccountNumber: '999',
      updatedAt: serverTimestamp()
    }));
  }
  await assertFails(saveSellerProfile(env.unauthenticatedContext().firestore()));
  for (const status of ['BLOCKED', 'INACTIVE']) {
    await env.withSecurityRulesDisabled(async context => {
      await updateDoc(doc(context.firestore(), 'users/seller'), { status });
    });
    await assertFails(saveSellerProfile(seller));
    await assertFails(setDoc(doc(seller, 'sellerPaymentProfiles/seller'), {
      sellerId: 'seller', bankCode: 'MB', bankAccountName: 'TEST', bankAccountNumber: '123',
      updatedAt: serverTimestamp()
    }));
  }
});

test('wrong fixed shipping fee rolls back order, cart and inventory together', async () => {
  const customer = env.authenticatedContext('customer-a').firestore();
  await assertFails(checkout(customer, 0));
  assert.equal((await getDoc(doc(customer, 'orders/new-order'))).exists(), false);
  assert.equal((await getDoc(doc(customer, 'customerCarts/customer-a'))).data().items.length, 2);
  assert.equal((await getDoc(doc(customer, 'productBatches/batch-a'))).data().availableQuantity, 10);
});

test('notifications cannot be forged outside an atomic order status change', async () => {
  const a = env.authenticatedContext('customer-a').firestore();
  await assertFails(setDoc(doc(a, 'notifications/existing_DELIVERED'), {
    id: 'existing_DELIVERED', userId: 'customer-a', type: 'ORDER', title: 'Fake', body: '',
    referenceId: 'existing', isRead: false, createdAt: serverTimestamp()
  }));
});

test('seller status transition creates a notification visible only to the order customer', async () => {
  const customer = env.authenticatedContext('customer-a').firestore();
  await seed({'orders/new-order': {id: 'new-order', ownerType: 'CUSTOMER', ownerId: 'customer-a', sellerId: 'seller', status: 'PENDING', paymentMethod: 'COD', paymentStatus: 'UNPAID', fulfillmentType: 'DELIVERY', inventoryFinalized: false, finalizedStatus: ''}});
  const seller = env.authenticatedContext('seller').firestore();
  await assertSucceeds(runTransaction(seller, async tx => {
    const order = await tx.get(doc(seller, 'orders/new-order'));
    tx.update(order.ref, { status: 'CONFIRMED', updatedAt: serverTimestamp() });
    tx.set(doc(seller, 'notifications/new-order_CONFIRMED'), {
      id: 'new-order_CONFIRMED', userId: 'customer-a', type: 'ORDER', title: 'Order confirmed', body: '',
      referenceId: 'new-order', isRead: false, createdAt: serverTimestamp()
    });
  }));
  await assertSucceeds(getDoc(doc(customer, 'notifications/new-order_CONFIRMED')));
  await assertFails(getDoc(doc(env.authenticatedContext('customer-b').firestore(), 'notifications/new-order_CONFIRMED')));
});

test('customer cannot create an unreserved order or append items/payments/shipments to an existing order', async () => {
  const db = env.authenticatedContext('customer-a').firestore();
  await assertFails(setDoc(doc(db, 'orders/unreserved'), {
    id: 'unreserved', requestId: 'unreserved', ownerType: 'CUSTOMER', ownerId: 'customer-a',
    sellerId: 'seller', campaignId: '', orderCode: 'RF-AUDIT', fulfillmentType: 'PICKUP',
    receiverName: 'Customer', receiverPhone: '0912345678', receiverAddress: '',
    receiverLatitude: 0, receiverLongitude: 0, subtotal: 1, quantityDiscount: 0, shippingFee: 0,
    totalAmount: 1, paymentMethod: 'CASH_ON_SITE', paymentStatus: 'UNPAID', status: 'PENDING', note: '',
    campaignReservedQuantity: 0, batchReservations: {'batch-a': 1000}, inventoryFinalized: false,
    finalizedStatus: '', createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, 'orders/existing/items/new'), {id: 'new', quantity: 1000}));
  await assertFails(setDoc(doc(db, 'payments/existing'), {id: 'existing', amount: 1}));
  await assertFails(setDoc(doc(db, 'shipments/existing'), {id: 'existing', shippingFee: 0}));
  await assertFails(updateDoc(doc(db, 'productBatches/batch-a'), {availableQuantity: 0, reservedQuantity: 10}));
  assert.equal((await getDoc(doc(db, 'productBatches/batch-a'))).data().availableQuantity, 10);
});

test('batch referenced by a cancelled order cannot be deleted; archiving preserves the reference', async () => {
  await seed({'orders/cancelled': {ownerType: 'CUSTOMER', ownerId: 'customer-a', sellerId: 'seller', status: 'CANCELLED'},
    'orders/cancelled/items/batch-a': {batchId: 'batch-a', productId: 'product-a'},
    'productBatches/batch-a': {id: 'batch-a', productId: 'product-a', activeCampaignId: '', harvestDate: past,
      expiryDate: future, initialQuantity: 10, availableQuantity: 10, reservedQuantity: 0, soldQuantity: 0,
      inventoryVersion: 1, status: 'AVAILABLE', createdAt: past, updatedAt: past, everOrdered: true}});
  const db = env.authenticatedContext('seller').firestore(), remove = writeBatch(db);
  remove.delete(doc(db, 'productBatches/batch-a')); await assertFails(remove.commit());
  await assertFails(updateDoc(doc(db, 'productBatches/batch-a'), {expiryDate: Timestamp.fromMillis(now + 864000000),
    inventoryVersion: 2, updatedAt: serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db, 'productBatches/batch-a'), {status: 'ARCHIVED', inventoryVersion: 2, updatedAt: serverTimestamp()}));
  assert.equal((await getDoc(doc(db, 'productBatches/batch-a'))).data().status, 'ARCHIVED');
  assert.equal((await getDoc(doc(db, 'orders/cancelled/items/batch-a'))).exists(), true);
});

test('comment owner can edit and soft-delete; others, timestamp forgery and edits after deletion are denied', async () => {
  await seed({'posts/published': {status: 'PUBLISHED'}});
  const owner = env.authenticatedContext('customer-a').firestore(), other = env.authenticatedContext('customer-b').firestore();
  const path = 'posts/published/comments/comment-a';
  await assertSucceeds(setDoc(doc(owner, path), {id: 'comment-a', postId: 'published', userId: 'customer-a',
    content: 'Bình luận ban đầu', status: 'ACTIVE', createdAt: past, updatedAt: past}));
  await assertFails(updateDoc(doc(other, path), {content: 'Mạo danh', status: 'EDITED', updatedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(owner, path), {content: 'Giả ngày tạo', status: 'EDITED', createdAt: future, updatedAt: serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(owner, path), {content: 'Nội dung đã sửa', status: 'EDITED', updatedAt: serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(owner, path), {content: '', status: 'DELETED', updatedAt: serverTimestamp()}));
  assert.equal((await getDoc(doc(owner, path))).data().content, '');
  await assertFails(updateDoc(doc(owner, path), {content: 'Khôi phục trái phép', status: 'EDITED', updatedAt: serverTimestamp()}));
});

test('Admin can moderate comments while Customer cannot bypass moderation or forge its fields', async () => {
  const path = 'posts/published/comments/moderated';
  await seed({
    'posts/published': {status: 'PUBLISHED'},
    [path]: {id: 'moderated', postId: 'published', userId: 'customer-a', content: 'Original',
      status: 'ACTIVE', createdAt: past, updatedAt: past}
  });
  const owner = env.authenticatedContext('customer-a').firestore();
  const admin = env.authenticatedContext('admin').firestore();
  const moderation = {status: 'HIDDEN', moderatedBy: 'admin', moderatedAt: serverTimestamp(),
    moderationReason: 'Inappropriate content', updatedAt: serverTimestamp()};
  await assertFails(updateDoc(doc(owner, path), moderation));
  await assertSucceeds(updateDoc(doc(admin, path), moderation));
  await assertFails(updateDoc(doc(owner, path), {status: 'EDITED', content: 'Restored', updatedAt: serverTimestamp()}));
  await seed({'posts/published': {status: 'HIDDEN'}});
  await assertSucceeds(getDoc(doc(admin, path)));
  await assertFails(getDoc(doc(owner, path)));
});

test('Guest cannot read order collections or backend capabilities and clients cannot forge viewCount', async () => {
  const guest = env.unauthenticatedContext().firestore();
  await assertFails(getDocs(collection(guest, 'orders')));
  await assertFails(getDoc(doc(guest, 'checkoutReceipts/receipt')));
  await assertFails(getDoc(doc(guest, 'guestOrderGrants/grant')));
  await assertFails(getDoc(doc(guest, 'guestOrderCodes/RF-TEST')));
  await seed({'posts/published': {status: 'PUBLISHED', viewCount: 0}});
  await assertFails(updateDoc(doc(guest, 'posts/published'), {viewCount: 999}));
  await assertFails(updateDoc(doc(env.authenticatedContext('customer-a').firestore(), 'posts/published'), {viewCount: 999}));
});
