const express = require('express');
const text = value => typeof value === 'string' ? value.trim() : '';
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
const https = value => /^https:\/\//i.test(text(value)) ? text(value) : '';

// Explicit public projection. Never return account avatars, application documents,
// contacts, banking details or private profile names to Guest/Customer clients.
function createCampaignCardsApi({db, now = Date.now}) {
  const router = express.Router();
  router.post('/campaigns/cards', async (req, res, next) => {
    try {
      const ids = req.body?.campaignIds;
      if (!Array.isArray(ids) || ids.length > 12 || ids.some(id =>
        typeof id !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(id)))
        throw Object.assign(new Error('Chọn tối đa 12 chiến dịch hợp lệ.'), {status: 400});
      const sellers = new Map(), cards = Object.create(null);
      function author(id) {
        if (!sellers.has(id)) sellers.set(id, (async () => {
          const [user, profile, application] = await Promise.all([
            db.collection('users').doc(id).get(), db.collection('sellerPublicProfiles').doc(id).get(),
            db.collection('sellerApplications').doc(id).get()
          ]);
          const active = user.exists && user.get('role') === 'SELLER' && user.get('status') === 'ACTIVE';
          return {
            shopName: active && profile.exists ? text(profile.get('shopName')) : '',
            avatarUrl: active && profile.exists ? https(profile.get('shopAvatarUrl')) : '',
            address: active && profile.exists ? text(profile.get('address')) : '',
            verified: !!(active && user.get('sellerStatus') === 'APPROVED' && application.exists
              && application.get('sellerId') === id && application.get('status') === 'APPROVED'
              && https(application.get('proofImageUrl')))
          };
        })());
        return sellers.get(id);
      }
      await Promise.all([...new Set(ids)].map(async id => {
        const snapshot = await db.collection('campaigns').doc(id).get();
        if (!snapshot.exists || snapshot.get('status') !== 'ACTIVE'
            || millis(snapshot.get('startDate')) > now() || millis(snapshot.get('endDate')) <= now()) return;
        const sellerId = text(snapshot.get('sellerId'));
        if (!sellerId || sellerId.includes('/')) return;
        const seller = await author(sellerId), products = new Map();
        const batchIds = Object.keys(snapshot.get('batchTargets') || {}).slice(0, 3);
        for (const batchId of batchIds) {
          if (!/^[A-Za-z0-9_-]{1,128}$/.test(batchId)) continue;
          const batch = await db.collection('productBatches').doc(batchId).get();
          const productId = batch.exists ? text(batch.get('productId')) : '';
          if (!productId || productId.includes('/')) continue;
          const product = await db.collection('products').doc(productId).get();
          if (product.exists && product.get('sellerId') === sellerId && product.get('status') === 'ACTIVE')
            products.set(productId, {name: text(product.get('name')), unit: text(product.get('unit')),
              rescuePrice: Number.isFinite(product.get('rescuePrice')) ? product.get('rescuePrice') : 0});
        }
        cards[id] = {...seller,
          postedAt: millis(snapshot.get('publishedAt')) || millis(snapshot.get('moderatedAt')) || millis(snapshot.get('createdAt')),
          location: text(snapshot.get('locationName')) || seller.address, products: [...products.values()]};
        delete cards[id].address;
      }));
      res.json({ok: true, cards});
    } catch (error) { next(error); }
  });
  return {router};
}
module.exports = {createCampaignCardsApi};
