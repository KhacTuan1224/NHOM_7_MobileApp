const express = require('express');
const text = value => typeof value === 'string' ? value.trim() : '';
const fulfillmentTypes = ['DELIVERY', 'PICKUP', 'ON_SITE_RESCUE'];

function availablePaymentMethods(fulfillmentType, profile) {
  const methods = [fulfillmentType === 'DELIVERY' ? 'COD' : 'CASH_ON_SITE'];
  if (profile && ['bankCode', 'bankAccountName', 'bankAccountNumber'].every(key => text(profile[key])))
    methods.push('BANK_TRANSFER');
  return methods;
}

function createSellerPaymentOptionsApi({db}) {
  const router = express.Router();
  // Public capabilities only. Customers and guests never read private bank documents.
  router.post('/sellers/payment-options', async (req, res, next) => {
    try {
      const {sellerId, fulfillmentType} = req.body || {};
      if (typeof sellerId !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(sellerId)
          || !fulfillmentTypes.includes(fulfillmentType)) {
        throw Object.assign(new Error('Thông tin người bán hoặc hình thức nhận hàng không hợp lệ.'), {status: 400});
      }
      const seller = await db.collection('users').doc(sellerId).get();
      if (!seller.exists || seller.get('role') !== 'SELLER' || seller.get('status') !== 'ACTIVE'
          || seller.get('sellerStatus') !== 'APPROVED')
        throw Object.assign(new Error('Cửa hàng hiện chưa thể nhận đơn hàng.'), {status: 409});
      const payment = await db.collection('sellerPaymentProfiles').doc(sellerId).get();
      res.json({ok: true, availablePaymentMethods: availablePaymentMethods(
        fulfillmentType, payment.exists ? payment.data() : null)});
    } catch (error) { next(error); }
  });
  return {router};
}

module.exports = {createSellerPaymentOptionsApi, availablePaymentMethods};
