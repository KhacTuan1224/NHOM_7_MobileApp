const express = require('express');
const crypto = require('node:crypto');
const fail = (status, message) => Object.assign(new Error(message), {status, expose: true});
const text = value => typeof value === 'string' ? value.trim() : '';
const bankFields = ['bankCode', 'bankAccountName', 'bankAccountNumber'];

function createPaymentQrApi({db, admin, authenticate, env = process.env, fetchImpl = fetch}) {
  const router = express.Router();
  let bankList, bankListExpires = 0;
  async function json(url, options) {
    const result = await fetchImpl(url, {...options, signal: AbortSignal.timeout(30000)});
    if (!result.ok) throw fail(502, 'Dịch vụ tạo/lưu QR tạm thời không khả dụng. Hãy thử lại.');
    return result.json();
  }
  async function generate(req) {
    const caller = await authenticate(req, ['SELLER']);
    if (!env.VIETQR_CLIENT_ID || !env.VIETQR_API_KEY)
      throw fail(503, 'Backend chưa cấu hình VIETQR_CLIENT_ID và VIETQR_API_KEY.');
    if (!/^https:\/\//.test(env.CLOUDINARY_SIGNING_URL || '') || !/^[\w-]+$/.test(env.CLOUDINARY_CLOUD_NAME || ''))
      throw fail(503, 'Backend chưa cấu hình dịch vụ lưu ảnh Cloudinary.');
    const reference = db.collection('sellerPaymentProfiles').doc(caller.uid);
    const profile = await reference.get();
    if (!profile.exists || bankFields.some(key => !text(profile.get(key))))
      throw fail(409, 'Hãy lưu đủ mã ngân hàng, chủ tài khoản và số tài khoản trong Hồ sơ người bán trước.');
    const bank = Object.fromEntries(bankFields.map(key => [key, text(profile.get(key))]));
    if (!/^\d{6,19}$/.test(bank.bankAccountNumber))
      throw fail(400, 'VietQR yêu cầu số tài khoản gồm 6–19 chữ số. Hãy kiểm tra lại hồ sơ ngân hàng.');
    const accountName = bank.bankAccountName.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'D')
      .toUpperCase().replace(/\s+/g, ' ');
    if (!/^[A-Z0-9 ]{5,50}$/.test(accountName))
      throw fail(400, 'VietQR yêu cầu tên chủ tài khoản từ 5–50 ký tự, không có ký tự đặc biệt.');
    let bin = bank.bankCode;
    if (!/^\d{6}$/.test(bin)) {
      if (!bankList || bankListExpires < Date.now()) {
        const response = await json('https://api.vietqr.io/v2/banks');
        if (response.code !== '00' || !Array.isArray(response.data)) throw fail(502, 'Không tải được danh sách ngân hàng VietQR.');
        bankList = response.data; bankListExpires = Date.now() + 3600000;
      }
      const found = bankList.find(item => [item.code, item.shortName, item.bin]
        .some(value => String(value || '').toUpperCase() === bin.toUpperCase()));
      if (!found || !/^\d{6}$/.test(String(found.bin))) throw fail(400, 'Mã ngân hàng không có trong danh sách VietQR.');
      bin = String(found.bin);
    }
    const generated = await json('https://api.vietqr.io/v2/generate', {
      method: 'POST', headers: {'Content-Type': 'application/json',
        'x-client-id': env.VIETQR_CLIENT_ID, 'x-api-key': env.VIETQR_API_KEY},
      body: JSON.stringify({accountNo: bank.bankAccountNumber, accountName, acqId: Number(bin), template: 'compact2'})
    });
    if (generated.code !== '00') throw fail(502, 'VietQR không tạo được mã. Hãy kiểm tra lại thông tin ngân hàng.');
    const dataUrl = generated.data && generated.data.qrDataURL;
    if (typeof dataUrl !== 'string' || !/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(dataUrl) || dataUrl.length > 2800000)
      throw fail(502, 'VietQR trả về ảnh QR không hợp lệ.');
    const bytes = Buffer.from(dataUrl.substring(dataUrl.indexOf(',') + 1), 'base64');
    if (!bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])))
      throw fail(502, 'VietQR trả về ảnh QR không hợp lệ.');
    const version = crypto.randomUUID();
    const signed = await json(env.CLOUDINARY_SIGNING_URL.replace(/\/$/, '') + '/sign-upload', {
      method: 'POST', headers: {'Content-Type': 'application/json', Authorization: req.get('authorization')},
      body: JSON.stringify({assetType: 'paymentQr', resourceId: caller.uid, slot: 0, version})
    });
    const expectedId = `rescuefarm/sellers/${caller.uid}/payment-qr-${version}`;
    if (signed.public_id !== expectedId || signed.overwrite !== 'false')
      throw fail(502, 'Signing Worker chưa hỗ trợ phiên tạo QR mới. Hãy cập nhật Worker.');
    const form = new FormData();
    for (const key of ['api_key', 'signature', 'timestamp', 'public_id', 'asset_folder', 'overwrite', 'invalidate'])
      if (signed[key] !== undefined) form.set(key, String(signed[key]));
    form.set('file', new Blob([bytes], {type: 'image/png'}), 'payment-qr.png');
    const upload = await json(`https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`, {method: 'POST', body: form});
    if (upload.public_id !== expectedId || !String(upload.secure_url || '').startsWith(
      `https://res.cloudinary.com/${env.CLOUDINARY_CLOUD_NAME}/image/upload/`))
      throw fail(502, 'Cloudinary không trả về ảnh QR hợp lệ.');
    await db.runTransaction(async tx => {
      const current = await tx.get(reference);
      if (!current.exists || bankFields.some(key => text(current.get(key)) !== bank[key]))
        throw fail(409, 'Thông tin ngân hàng vừa thay đổi. Hãy tạo lại QR cho tài khoản mới.');
      tx.update(reference, {qrUrl: upload.secure_url, updatedAt: admin.firestore.FieldValue.serverTimestamp()});
    });
    return {ok: true, qrUrl: upload.secure_url};
  }
  router.post('/seller/payment-qr', async (req, res, next) => {
    try { res.json(await generate(req)); } catch (error) { next(error); }
  });
  return {router, generate};
}
module.exports = {createPaymentQrApi};
