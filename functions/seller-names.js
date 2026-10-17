const express = require('express');

function createSellerNamesApi({db}) {
  const router = express.Router();
  // These names are displayed alongside public products, including in guest carts.
  router.post('/sellers/names', async (req, res, next) => {
    try {
      const ids = req.body && req.body.sellerIds;
      if (!Array.isArray(ids) || ids.length > 50 || ids.some(id =>
        typeof id !== 'string' || !id.trim() || id.length > 128 || id.includes('/'))) {
        const error = new Error('sellerIds must contain at most 50 valid seller IDs');
        error.status = 400;
        throw error;
      }
      const names = Object.create(null);
      await Promise.all([...new Set(ids)].map(async id => {
        const seller = await db.collection('users').doc(id).get();
        if (seller.exists && seller.get('role') === 'SELLER'
            && seller.get('status') === 'ACTIVE' && seller.get('sellerStatus') === 'APPROVED') {
          const name = seller.get('fullName');
          if (typeof name === 'string' && name.trim()) names[id] = name.trim();
        }
      }));
      res.json({ok: true, sellerNames: names});
    } catch (error) { next(error); }
  });
  return {router};
}

module.exports = {createSellerNamesApi};
