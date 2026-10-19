const express = require('express');
const text = value => typeof value === 'string' ? value.trim() : '';
const identifier = value => /^[A-Za-z0-9_-]{1,128}$/.test(text(value));
const millis = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
const fail = (status, message) => Object.assign(new Error(message), {status});
const walletItem = (db, uid, productId) => db.collection('customerPromotionWallets').doc(uid).collection('items').doc(productId);

function available(promotion, product, seller, now = Date.now()) {
  return !!promotion && !!product && !!seller && product.status === 'ACTIVE'
    && seller.role === 'SELLER' && seller.status === 'ACTIVE' && seller.sellerStatus === 'APPROVED'
    && promotion.sellerId === product.sellerId && promotion.active === true
    && millis(promotion.startDate) <= now && millis(promotion.endDate) > now;
}

function createPromotionWallet({db, admin, authenticate, limit}) {
  const router = express.Router();
  const route = (path, handler) => router.post(path, async (req, res, next) => {
    try { res.json(await handler(req)); } catch (error) { next(error); }
  });
  async function card(productId, uid, savedDocument = null) {
    const [p, promo, saved] = await Promise.all([db.collection('products').doc(productId).get(),
      db.collection('promotions').doc(productId).get(),
      savedDocument || (uid ? walletItem(db, uid, productId).get() : Promise.resolve(null))]);
    const product = p.exists ? p.data() : null, promotion = promo.exists ? promo.data() : null;
    const seller = product && product.sellerId ? await db.collection('users').doc(product.sellerId).get() : null;
    const eligible = available(promotion, product, seller?.exists ? seller.data() : null);
    const data = saved?.exists ? saved.data() : {};
    return {productId, productName: text(product?.name) || text(data.productName) || 'Sản phẩm không còn khả dụng',
      sellerName: seller?.exists ? text(seller.get('fullName')) : '', unit: text(product?.unit) || 'sản phẩm',
      imageUrl: Array.isArray(product?.imageUrls) ? text(product.imageUrls[0]) : '',
      type: text(promotion?.type), value: promotion?.value || 0, quantityDiscountTiers: promotion?.quantityDiscountTiers || {},
      startDate: millis(promotion?.startDate), endDate: millis(promotion?.endDate),
      saved: !!saved?.exists, eligible, productAvailable: product?.status === 'ACTIVE'};
  }
  route('/promotions/catalog', async req => {
    await limit(req, 'promotionCatalog', 120);
    const body = req.body || {}, mine = body.mine === true;
    let uid = '';
    if (mine || req.get('authorization')) {
      const caller = await authenticate(req, mine ? ['CUSTOMER'] : ['CUSTOMER', 'SELLER', 'ADMIN']);
      if (caller.role === 'CUSTOMER') uid = caller.uid;
    }
    const cursor = text(body.cursor), productId = text(body.productId);
    if ((cursor && !identifier(cursor)) || (productId && !identifier(productId))) throw fail(400, 'Bộ lọc khuyến mãi không hợp lệ.');
    if (productId && !mine) {
      const value = await card(productId, uid);
      return {items: value.eligible ? [value] : [], hasMore: false, cursor: ''};
    }
    let query = mine ? db.collection('customerPromotionWallets').doc(uid).collection('items') : db.collection('promotions').where('active', '==', true);
    query = query.orderBy(admin.firestore.FieldPath.documentId());
    if (cursor) query = query.startAfter(cursor);
    const page = await query.limit(25).get(), documents = page.docs.slice(0, 24);
    const cards = await Promise.all(documents.map(d => card(d.id, uid, mine ? d : null)));
    return {items: mine ? cards : cards.filter(c => c.eligible), hasMore: page.size > 24,
      cursor: documents.length ? documents[documents.length - 1].id : ''};
  });
  route('/promotions/save', async req => {
    const caller = await authenticate(req, ['CUSTOMER']);
    await limit(req, 'promotionSave', 60, 600000, caller.uid);
    const productId = text(req.body?.productId);
    if (!identifier(productId)) throw fail(400, 'Sản phẩm không hợp lệ.');
    await db.runTransaction(async tx => {
      const p = await tx.get(db.collection('products').doc(productId));
      const promo = await tx.get(db.collection('promotions').doc(productId));
      const seller = p.exists && identifier(p.get('sellerId')) ? await tx.get(db.collection('users').doc(p.get('sellerId'))) : null;
      if (!available(promo.exists ? promo.data() : null, p.exists ? p.data() : null, seller?.exists ? seller.data() : null))
        throw fail(409, 'Khuyến mãi đã hết hạn, bị tắt hoặc sản phẩm không còn bán.');
      const item = walletItem(db, caller.uid, productId), existing = await tx.get(item);
      if (!existing.exists) tx.create(item, {customerId: caller.uid, productId, sellerId: p.get('sellerId'),
        productName: text(p.get('name')), savedAt: admin.firestore.FieldValue.serverTimestamp()});
      tx.set(db.collection('customerPromotionWallets').doc(caller.uid), {customerId: caller.uid,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()});
    });
    return {saved: true, productId};
  });
  route('/promotions/remove', async req => {
    const caller = await authenticate(req, ['CUSTOMER']);
    await limit(req, 'promotionRemove', 60, 600000, caller.uid);
    const productId = text(req.body?.productId);
    if (!identifier(productId)) throw fail(400, 'Sản phẩm không hợp lệ.');
    await walletItem(db, caller.uid, productId).delete();
    return {saved: false, productId};
  });
  return {router};
}
module.exports = {createPromotionWallet, walletItem, available};
