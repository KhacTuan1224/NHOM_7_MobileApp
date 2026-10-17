import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, setDoc,
  collectionGroup, deleteDoc, deleteField, runTransaction, serverTimestamp, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';

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

test('Seller profile save commits text address, public GPS and private payment together', async () => {
  const db = env.authenticatedContext('seller').firestore();
  const business = {representativeName: 'Nguyễn Văn An', shopName: 'Vườn nhà', shopDescription: 'Nông sản',
    shopAvatarUrl: '', address: '12 đường Vườn, tỉnh Đồng Tháp', bankCode: 'MB', bankAccountName: 'NGUYEN VAN AN',
    bankAccountNumber: '123456789', latitude: 10.8, longitude: 106.7, updatedAt: serverTimestamp()};
  const batch = writeBatch(db);
  batch.update(doc(db, 'users/seller'), business);
  batch.set(doc(db, 'sellerPublicProfiles/seller'), {sellerId: 'seller', shopName: business.shopName,
    shopDescription: business.shopDescription, shopAvatarUrl: '', address: business.address,
    latitude: 10.8, longitude: 106.7, updatedAt: serverTimestamp(),
    bankCode: deleteField(), bankAccountName: deleteField(), bankAccountNumber: deleteField(), qrUrl: deleteField()}, {merge: true});
  batch.set(doc(db, 'sellerPaymentProfiles/seller'), {sellerId: 'seller', bankCode: 'MB',
    bankAccountName: 'NGUYEN VAN AN', bankAccountNumber: '123456789', updatedAt: serverTimestamp()}, {merge: true});
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(db, 'users/seller'))).data().address, business.address);
  assert.equal((await getDoc(doc(db, 'sellerPublicProfiles/seller'))).data().latitude, 10.8);
  assert.equal((await getDoc(doc(db, 'sellerPaymentProfiles/seller'))).data().bankCode, 'MB');
});

test('active customers can manage only their own receiver addresses', async () => {
  for (const owner of ['customer-a', 'customer-b']) {
    const db = env.authenticatedContext(owner).firestore();
    const ref = doc(db, `users/${owner}/addresses/home`);
    await assertSucceeds(setDoc(ref, addressData(owner)));
    await assertSucceeds(getDoc(ref));
    assert.equal((await assertSucceeds(getDocs(collection(db, `users/${owner}/addresses`)))).size, 1);
    await assertSucceeds(updateDoc(ref, { street: '34 Đường Mới', updatedAt: serverTimestamp() }));
    for (const other of ['customer-a', 'customer-b', 'seller', 'admin'].filter(id => id !== owner)) {
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

test('sellers and admins do not have customer receiver address books', async () => {
  for (const owner of ['seller', 'admin']) {
    const db = env.authenticatedContext(owner).firestore();
    await assertFails(setDoc(doc(db, `users/${owner}/addresses/home`), addressData(owner)));
    await assertFails(getDocs(collection(db, `users/${owner}/addresses`)));
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

test('customer default addresses switch atomically and cannot be deleted while default', async () => {
  for (const [owner, role] of [['customer-a', 'CUSTOMER']]) {
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

test('clients cannot bypass backend order transitions; backend notifications stay private', async () => {
  const customer = env.authenticatedContext('customer-a').firestore();
  await seed({'orders/new-order': {id: 'new-order', ownerType: 'CUSTOMER', ownerId: 'customer-a', sellerId: 'seller', status: 'PENDING', paymentMethod: 'COD', paymentStatus: 'UNPAID', fulfillmentType: 'DELIVERY', inventoryFinalized: false, finalizedStatus: ''}});
  const seller = env.authenticatedContext('seller').firestore();
  await assertFails(runTransaction(seller, async tx => {
    const order = await tx.get(doc(seller, 'orders/new-order'));
    tx.update(order.ref, { status: 'CONFIRMED', updatedAt: serverTimestamp() });
    tx.set(doc(seller, 'notifications/new-order_CONFIRMED'), {
      id: 'new-order_CONFIRMED', userId: 'customer-a', type: 'ORDER', title: 'Order confirmed', body: '',
      referenceId: 'new-order', isRead: false, createdAt: serverTimestamp()
    });
  }));
  await seed({'notifications/new-order_CONFIRMED': {id: 'new-order_CONFIRMED', userId: 'customer-a', type: 'ORDER', referenceId: 'new-order', isRead: false}});
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

test('Campaign comment owner can edit and soft-delete; others, timestamp forgery and edits after deletion are denied', async () => {
  await seed({'campaigns/active': {status: 'ACTIVE'}});
  const owner = env.authenticatedContext('customer-a').firestore(), other = env.authenticatedContext('customer-b').firestore();
  const path = 'campaigns/active/comments/comment-a';
  await assertSucceeds(setDoc(doc(owner, path), {id: 'comment-a', campaignId: 'active', userId: 'customer-a',
    content: 'Bình luận ban đầu', status: 'ACTIVE', createdAt: past, updatedAt: past}));
  await assertFails(updateDoc(doc(other, path), {content: 'Mạo danh', status: 'EDITED', updatedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(owner, path), {content: 'Giả ngày tạo', status: 'EDITED', createdAt: future, updatedAt: serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(owner, path), {content: 'Nội dung đã sửa', status: 'EDITED', updatedAt: serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(owner, path), {content: '', status: 'DELETED', updatedAt: serverTimestamp()}));
  assert.equal((await getDoc(doc(owner, path))).data().content, '');
  await assertFails(updateDoc(doc(owner, path), {content: 'Khôi phục trái phép', status: 'EDITED', updatedAt: serverTimestamp()}));
});

test('Admin can moderate Campaign comments while Customer cannot bypass moderation or forge its fields', async () => {
  const path = 'campaigns/active/comments/moderated';
  await seed({
    'campaigns/active': {status: 'ACTIVE'},
    [path]: {id: 'moderated', campaignId: 'active', userId: 'customer-a', content: 'Original',
      status: 'ACTIVE', createdAt: past, updatedAt: past}
  });
  const owner = env.authenticatedContext('customer-a').firestore();
  const admin = env.authenticatedContext('admin').firestore();
  const moderation = {status: 'HIDDEN', moderatedBy: 'admin', moderatedAt: serverTimestamp(),
    moderationReason: 'Inappropriate content', updatedAt: serverTimestamp()};
  await assertFails(updateDoc(doc(owner, path), moderation));
  await assertSucceeds(updateDoc(doc(admin, path), moderation));
  await assertFails(updateDoc(doc(owner, path), {status: 'EDITED', content: 'Restored', updatedAt: serverTimestamp()}));
  await seed({'campaigns/active': {status: 'STOPPED'}});
  await assertSucceeds(getDoc(doc(admin, path)));
  await assertFails(getDoc(doc(owner, path)));
});

test('legacy Post comments cannot be created or edited, including by Admin', async () => {
  const path = 'posts/legacy/comments/legacy-comment';
  const value = {id: 'legacy-comment', postId: 'legacy', userId: 'customer-a', content: 'Historical',
    status: 'ACTIVE', createdAt: past, updatedAt: past};
  await seed({'posts/legacy': {status: 'PUBLISHED'}, [path]: value});
  for (const uid of ['customer-a', 'admin']) {
    const db = env.authenticatedContext(uid).firestore();
    await assertSucceeds(getDoc(doc(db, path)));
    await assertFails(updateDoc(doc(db, path), {content: 'Changed', status: 'EDITED', updatedAt: serverTimestamp()}));
    await assertFails(setDoc(doc(db, 'posts/legacy/comments/new'), {...value, id: 'new'}));
  }
});

test('only active Admin can query comments across Campaign and legacy Post parents', async () => {
  await seed({
    'campaigns/closed': {status: 'STOPPED'}, 'posts/hidden': {status: 'HIDDEN'},
    'campaigns/closed/comments/campaign-comment': {id: 'reported', campaignId: 'closed', userId: 'customer-a', content: 'Campaign', status: 'HIDDEN'},
    'posts/hidden/comments/post-comment': {id: 'reported', postId: 'hidden', userId: 'customer-a', content: 'Legacy', status: 'ACTIVE'}
  });
  const db = env.authenticatedContext('admin').firestore();
  const found = await assertSucceeds(getDocs(query(collectionGroup(db, 'comments'), where('id', '==', 'reported'))));
  assert.equal(found.size, 2);
  for (const uid of ['customer-a', 'seller', 'blocked']) {
    const other = env.authenticatedContext(uid).firestore();
    await assertFails(getDocs(query(collectionGroup(other, 'comments'), where('id', '==', 'reported'))));
  }
  const guest = env.unauthenticatedContext().firestore();
  await assertFails(getDocs(query(collectionGroup(guest, 'comments'), where('id', '==', 'reported'))));
  await seed({'users/admin': {role: 'ADMIN', status: 'BLOCKED'}});
  await assertFails(getDocs(query(collectionGroup(db, 'comments'), where('id', '==', 'reported'))));
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

function outboxData(actorId, resourceType, resourceId, kind = 'ADMINS') {
  return {actorId, resourceType, resourceId, kind, status: 'PENDING', retryCount: 0,
    nextAttemptAt: serverTimestamp(), createdAt: serverTimestamp()};
}
function newReport(id) {
  return {id, reporterId: 'customer-a', targetType: 'PRODUCT', targetId: 'product-a', reason: 'Cần kiểm tra', description: '',
    status: 'PENDING', resolvedBy: '', resolutionNote: '', createdAt: serverTimestamp(), updatedAt: serverTimestamp()};
}
test('report and outbox event commit atomically; replay, forged recipients and delivery-state edits are denied', async () => {
  const db = env.authenticatedContext('customer-a').firestore(), batch = writeBatch(db);
  batch.set(doc(db, 'reports/new'), newReport('new'));
  batch.set(doc(db, 'notificationOutbox/new'), outboxData('customer-a', 'REPORT', 'new'));
  await assertSucceeds(batch.commit());
  await assertFails(setDoc(doc(db, 'notificationOutbox/replay'), outboxData('customer-a', 'REPORT', 'new')));
  await assertFails(getDoc(doc(db, 'notificationOutbox/new')));
  await assertFails(updateDoc(doc(db, 'notificationOutbox/new'), {status: 'EXPANDED'}));
  const forged = writeBatch(db);
  forged.set(doc(db, 'reports/forged'), newReport('forged'));
  forged.set(doc(db, 'notificationOutbox/forged'), {...outboxData('customer-a', 'REPORT', 'forged'), recipientUid: 'customer-b'});
  await assertFails(forged.commit());
  await env.withSecurityRulesDisabled(async context => {
    assert.equal((await getDoc(doc(context.firestore(), 'reports/forged'))).exists(), false);
  });
  await assertFails(setDoc(doc(db, 'notificationDeliveries/fake'), {userId: 'customer-b', status: 'SENT'}));
});
test('reporters cannot read internal resolution notes directly; the public projection uses the backend', async () => {
  await seed({'reports/result': {...newReport('result'), resolutionNote: 'INTERNAL', status: 'RESOLVED'}});
  for (const uid of ['customer-a', 'customer-b', 'seller'])
    await assertFails(getDoc(doc(env.authenticatedContext(uid).firestore(), 'reports/result')));
  await assertSucceeds(getDoc(doc(env.authenticatedContext('admin').firestore(), 'reports/result')));
});
test('Campaign draft declaration and its submission event are allowed; client unit verification is denied', async () => {
  const db = env.authenticatedContext('seller').firestore();
  const campaign = {id: 'draft', sellerId: 'seller', title: 'Campaign thử nghiệm', description: 'Nội dung thử nghiệm',
    rescueReason: 'OVER_SUPPLY', urgencyLevel: 'NORMAL', rescueMode: 'FIXED_POINT', batchTargets: {'batch-a': 5},
    targetQuantity: 5, reservedQuantity: 0, rescuedQuantity: 0, startDate: past, endDate: future,
    latitude: 10.8, longitude: 106.7, currentLatitude: 0, currentLongitude: 0, locationUpdatedAt: null,
    locationSharingEnabled: false, locationName: 'Test', status: 'PENDING_APPROVAL', quantityUnit: 'bó', unitStatus: 'DECLARED',
    reservedByBatch: {}, rescuedByBatch: {}, allocationVersion: 0, createdAt: serverTimestamp(), updatedAt: serverTimestamp()};
  const batch = writeBatch(db); batch.set(doc(db, 'campaigns/draft'), campaign);
  batch.set(doc(db, 'notificationOutbox/campaign'), outboxData('seller', 'CAMPAIGN', 'draft'));
  await assertSucceeds(batch.commit());
  await assertFails(setDoc(doc(db, 'campaigns/forged-unit'), {...campaign, id: 'forged-unit', unitStatus: 'VERIFIED'}));
});

test('Seller application submission and its Admin event are committed together', async () => {
  const db = env.authenticatedContext('seller').firestore(), batch = writeBatch(db);
  batch.set(doc(db, 'sellerApplications/seller'), {id: 'seller', sellerId: 'seller', representativeName: 'Test Seller',
    shopName: 'Test shop', address: '', proofImageUrl: '', rejectionReason: '', status: 'PENDING',
    submittedAt: serverTimestamp(), updatedAt: serverTimestamp()});
  batch.set(doc(db, 'notificationOutbox/application'), outboxData('seller', 'SELLER_APPLICATION', 'seller'));
  await assertSucceeds(batch.commit());
});
test('future Promotion can persist its scheduled notification event atomically', async () => {
  const db = env.authenticatedContext('seller').firestore(), batch = writeBatch(db);
  batch.set(doc(db, 'promotions/product-a'), {id: 'product-a', productId: 'product-a', sellerId: 'seller', type: 'PERCENT',
    value: 10, quantityDiscountTiers: {}, active: true, startDate: Timestamp.fromMillis(now + 86400000),
    endDate: future, createdAt: serverTimestamp(), updatedAt: serverTimestamp()});
  batch.set(doc(db, 'notificationOutbox/promotion'), outboxData('seller', 'PROMOTION', 'product-a', 'PROMOTION'));
  await assertSucceeds(batch.commit());
});
test('confirming bank payment can enqueue an event only with the actual payment change', async () => {
  await seed({'orders/bank': {id: 'bank', ownerType: 'CUSTOMER', ownerId: 'customer-a', sellerId: 'seller',
    status: 'PENDING', paymentMethod: 'BANK_TRANSFER', paymentStatus: 'PENDING', updatedAt: past},
    'payments/bank': {id: 'bank', orderId: 'bank', method: 'BANK_TRANSFER', amount: 1000, status: 'PENDING', referenceCode: '', updatedAt: past}});
  const db = env.authenticatedContext('seller').firestore(), batch = writeBatch(db);
  batch.update(doc(db, 'orders/bank'), {paymentStatus: 'PAID', updatedAt: serverTimestamp()});
  batch.update(doc(db, 'payments/bank'), {status: 'PAID', referenceCode: 'BANK123', paidAt: serverTimestamp(), updatedAt: serverTimestamp()});
  batch.set(doc(db, 'notificationOutbox/payment'), outboxData('seller', 'ORDER', 'bank', 'ORDER_PAYMENT'));
  await assertSucceeds(batch.commit());
  await assertFails(setDoc(doc(db, 'notificationOutbox/payment-replay'), outboxData('seller', 'ORDER', 'bank', 'ORDER_PAYMENT')));
});


test('COMMENT reports bind an exact existing Campaign or legacy Post parent; forged paths are rejected', async () => {
  const common = {id: 'same', userId: 'customer-b', content: 'Same ID in different parents', status: 'ACTIVE', createdAt: past, updatedAt: past};
  await seed({'campaigns/c': {status: 'ACTIVE'}, 'posts/p': {status: 'PUBLISHED'},
    'campaigns/c/comments/same': {...common, campaignId: 'c'}, 'posts/p/comments/same': {...common, postId: 'p'}});
  const db = env.authenticatedContext('customer-a').firestore();
  for (const [parentType, parentId, collectionName] of [['CAMPAIGN', 'c', 'campaigns'], ['POST', 'p', 'posts']]) {
    const id = 'report-' + parentType, targetPath = `${collectionName}/${parentId}/comments/same`, batch = writeBatch(db);
    batch.set(doc(db, 'reports/' + id), {...newReport(id), targetType: 'COMMENT', targetId: 'same', parentType, parentId, targetPath});
    batch.set(doc(db, 'notificationOutbox/' + id), outboxData('customer-a', 'REPORT', id));
    await assertSucceeds(batch.commit());
  }
  const valid = {...newReport('forged-context'), targetType: 'COMMENT', targetId: 'same', parentType: 'CAMPAIGN', parentId: 'c', targetPath: 'posts/p/comments/same'};
  await assertFails(setDoc(doc(db, 'reports/forged-context'), valid));
  await assertFails(setDoc(doc(db, 'reports/missing-context'), {...newReport('missing-context'), targetType: 'COMMENT', targetId: 'same'}));
  await assertFails(setDoc(doc(db, 'reports/missing-comment'), {...valid, id: 'missing-comment', targetId: 'absent', targetPath: 'campaigns/c/comments/absent'}));
});
test('Admin can hide the exact legacy comment and audit its parent without changing another same-ID comment', async () => {
  const common = {id: 'same', userId: 'customer-a', content: 'Comment', status: 'ACTIVE', createdAt: past, updatedAt: past};
  await seed({'posts/p': {status: 'PUBLISHED'}, 'campaigns/c': {status: 'ACTIVE'},
    'posts/p/comments/same': {...common, postId: 'p'}, 'campaigns/c/comments/same': {...common, campaignId: 'c'}});
  const db = env.authenticatedContext('admin').firestore(), batch = writeBatch(db);
  batch.update(doc(db, 'posts/p/comments/same'), {status: 'HIDDEN', moderatedBy: 'admin', moderatedAt: serverTimestamp(),
    moderationReason: 'Violation', updatedAt: serverTimestamp()});
  batch.set(doc(db, 'adminAuditLogs/legacy-hide'), {adminId: 'admin', resourceType: 'COMMENTS', resourceId: 'same',
    resourcePath: 'posts/p/comments/same', fromState: 'ACTIVE', toState: 'HIDDEN', reason: 'Violation', createdAt: serverTimestamp()});
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(db, 'posts/p/comments/same'))).data().status, 'HIDDEN');
  assert.equal((await getDoc(doc(db, 'campaigns/c/comments/same'))).data().status, 'ACTIVE');
});
test('Guest and other Customer can view published Review images but not hidden Review media', async () => {
  await seed({'reviews/images': {productId: 'product-a', customerId: 'customer-a', status: 'PUBLISHED',
    imageUrls: ['https://example.com/1', 'https://example.com/2', 'https://example.com/3']},
    'reviews/hidden-images': {productId: 'product-a', customerId: 'customer-a', status: 'HIDDEN', imageUrls: ['https://example.com/private']}});
  for (const context of [env.unauthenticatedContext(), env.authenticatedContext('customer-b')]) {
    const db = context.firestore();
    const review = await assertSucceeds(getDoc(doc(db, 'reviews/images')));
    assert.equal(review.data().imageUrls.length, 3);
    await assertFails(getDoc(doc(db, 'reviews/hidden-images')));
  }
});


test('Business Customer registration and profile edits persist private company fields without role escalation', async () => {
  const db=env.authenticatedContext('business').firestore();
  const profile={id:'business',email:'business@example.com',fullName:'Business Customer',phone:'0912345678',avatarUrl:'',
    role:'CUSTOMER',status:'ACTIVE',customerType:'BUSINESS',companyName:'Company Original',taxCode:'0123456789',
    latitude:10,longitude:106,createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  await assertSucceeds(setDoc(doc(db,'users/business'),profile));
  await assertSucceeds(updateDoc(doc(db,'users/business'),{companyName:'Company Updated',taxCode:'0123456789-001',updatedAt:serverTimestamp()}));
  const saved=(await getDoc(doc(db,'users/business'))).data();assert.equal(saved.companyName,'Company Updated');assert.equal(saved.customerType,'BUSINESS');assert.equal(saved.role,'CUSTOMER');
  await assertFails(updateDoc(doc(db,'users/business'),{role:'ADMIN',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db,'users/business'),{status:'INACTIVE',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db,'users/business'),{companyName:'',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db,'users/business'),{customerType:'INDIVIDUAL',updatedAt:serverTimestamp()}));
  await assertFails(getDoc(doc(env.authenticatedContext('customer-b').firestore(),'users/business')));
});
test('Individual Customer can edit basic profile without supplying enterprise information',async()=>{
  await seed({'users/individual':{id:'individual',email:'i@example.com',fullName:'Individual Customer',phone:'0912345678',avatarUrl:'',role:'CUSTOMER',status:'ACTIVE',
    customerType:'INDIVIDUAL',companyName:'',taxCode:'',latitude:0,longitude:0,createdAt:past,updatedAt:past}});
  const db=env.authenticatedContext('individual').firestore();
  await assertSucceeds(updateDoc(doc(db,'users/individual'),{fullName:'Individual Updated',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db,'users/individual'),{companyName:'Unauthorized company',updatedAt:serverTimestamp()}));
});
test('Review and public Seller reports carry the selected target and commit their Admin event',async()=>{
  await seed({'reviews/context':{customerId:'customer-b',productId:'product-a',status:'PUBLISHED'}});
  const db=env.authenticatedContext('customer-a').firestore();
  for(const [type,targetId] of [['REVIEW','context'],['USER','seller']]) {
    const key='context-'+type,batch=writeBatch(db);
    batch.set(doc(db,'reports/'+key),{...newReport(key),targetType:type,targetId});
    batch.set(doc(db,'notificationOutbox/'+key),outboxData('customer-a','REPORT',key));await assertSucceeds(batch.commit());
    const adminDb=env.authenticatedContext('admin').firestore();const saved=(await getDoc(doc(adminDb,'reports/'+key))).data();
    assert.equal(saved.targetType,type);assert.equal(saved.targetId,targetId);
  }
});
test('Campaign image references and operational leases cannot bypass the trusted media command',async()=>{
  const campaign={id:'media',sellerId:'seller',title:'Campaign images',description:'Story',rescueReason:'OVER_SUPPLY',urgencyLevel:'NORMAL',rescueMode:'FIXED_POINT',
    batchTargets:{'batch-a':5},targetQuantity:5,reservedQuantity:0,rescuedQuantity:0,startDate:past,endDate:future,latitude:10,longitude:106,currentLatitude:0,currentLongitude:0,
    locationUpdatedAt:null,locationSharingEnabled:false,locationName:'Farm',status:'DRAFT',quantityUnit:'kg',unitStatus:'DECLARED',imageUrls:[],imagePublicIds:[],createdAt:past,updatedAt:past};
  await seed({'campaigns/media':campaign,'campaignMediaUploads/lease':{ownerUid:'seller',campaignId:'media',status:'COMMITTED'}});
  const db=env.authenticatedContext('seller').firestore();
  await assertFails(updateDoc(doc(db,'campaigns/media'),{imageUrls:['https://example.com/replacement.jpg'],imagePublicIds:['forged'],updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(db,'campaigns/bypass'),{...campaign,id:'bypass',imageUrls:['https://example.com/new.jpg'],imagePublicIds:['forged'],createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  for(const uid of ['seller','customer-a','admin']) {
    const client=env.authenticatedContext(uid).firestore();
    await assertFails(getDoc(doc(client,'campaignMediaUploads/lease')));
    await assertFails(setDoc(doc(client,'campaignMediaDrafts/new'),{ownerUid:uid}));
  }
  await seed({'campaigns/media':{...campaign,status:'ACTIVE'}});
  await assertFails(updateDoc(doc(db,'campaigns/media'),{imageUrls:[],imagePublicIds:[],description:'Edited active story',updatedAt:serverTimestamp()}));
});
