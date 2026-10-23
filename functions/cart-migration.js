const express = require('express');
const crypto = require('node:crypto');
const fail = (status, message) => Object.assign(new Error(message), {status});
const id = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
function createCartMigration({db, admin, authenticate}) {
  async function migrate(caller, body) {
    if (caller.role !== 'CUSTOMER' || body.targetUserId !== caller.uid || !id(body.requestId) || !Array.isArray(body.items) || !body.items.length || body.items.length > 100)
      throw fail(400, 'Yêu cầu chuyển giỏ không hợp lệ.');
    const seen = new Set();
    const items = body.items.map(row => {
      if (!/^[A-Za-z0-9_:-]{1,300}$/.test(row.id || '') || ![row.productId, row.batchId, row.sellerId].every(id) || seen.has(row.id)
          || !Number.isFinite(row.quantity) || row.quantity <= 0 || row.quantity > 1e6
          || !Number.isFinite(row.unitPrice) || row.unitPrice < 0 || typeof row.selected !== 'boolean') throw fail(400, 'Dòng giỏ không hợp lệ.');
      seen.add(row.id);
      return {id: row.id, productId: row.productId, batchId: row.batchId, sellerId: row.sellerId,
        quantity: row.quantity, unitPrice: row.unitPrice, selected: row.selected};
    }).sort((a, b) => a.id.localeCompare(b.id));
    const fingerprint = crypto.createHash('sha256').update(JSON.stringify(items)).digest('hex');
    return db.runTransaction(async tx => {
      const marker = db.collection('cartMigrationReceipts').doc(caller.uid + '_' + body.requestId);
      const receipt = await tx.get(marker), user = await tx.get(db.collection('users').doc(caller.uid));
      if (!user.exists || user.get('role') !== 'CUSTOMER' || user.get('status') !== 'ACTIVE') throw fail(403, 'Customer không hoạt động.');
      if (receipt.exists) {
        if (receipt.get('fingerprint') !== fingerprint) throw fail(409, 'Mã chuyển giỏ đã dùng cho nội dung khác.');
        return {ok: true, replay: true};
      }
      const cartRef = db.collection('customerCarts').doc(caller.uid), cart = await tx.get(cartRef);
      const merged = new Map((cart.exists ? cart.get('items') || [] : []).map(row => [row.id, row]));
      for (const row of items) {
        const old = merged.get(row.id);
        if (old && (old.batchId !== row.batchId || old.productId !== row.productId || old.sellerId !== row.sellerId)) throw fail(409, 'Mã dòng giỏ xung đột.');
        const quantity = (old?.quantity || 0) + row.quantity;
        if (!Number.isFinite(quantity) || quantity > 1e6) throw fail(400, 'Số lượng vượt giới hạn.');
        merged.set(row.id, {...row, quantity});
      }
      if (merged.size > 100) throw fail(409, 'Giỏ Customer tối đa 100 dòng; giỏ Guest đang được giữ trong hàng đợi, không bị xóa.');
      tx.set(cartRef, {id: caller.uid, ownerType: 'CUSTOMER', ownerKey: caller.uid, items: [...merged.values()], updatedAt: admin.firestore.FieldValue.serverTimestamp()});
      tx.create(marker, {userId: caller.uid, fingerprint, createdAt: admin.firestore.FieldValue.serverTimestamp()});
      return {ok: true, replay: false};
    });
  }
  const router = express.Router();
  router.post('/cart/migrate-guest', async (req, res, next) => {
    try { res.json(await migrate(await authenticate(req, ['CUSTOMER']), req.body || {})); } catch (error) { next(error); }
  });
  return {router, migrate};
}
module.exports = {createCartMigration};
