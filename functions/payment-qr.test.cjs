const {test} = require('node:test');
const assert = require('node:assert/strict');
const {MemoryFirestore, admin} = require('./test-support/memory-firestore.cjs');
const {createPaymentQrApi} = require('./payment-qr');
const png = 'data:image/png;base64,' + Buffer.from([137,80,78,71,13,10,26,10,0,0,0,0]).toString('base64');

function fixture(options = {}) {
  const original = {sellerId: 'seller', bankCode: 'MB', bankAccountName: 'Nguyễn Văn An',
    bankAccountNumber: '123456789', qrUrl: 'https://res.cloudinary.com/cloud/image/upload/old.png'};
  const db = new MemoryFirestore({'sellerPaymentProfiles/seller': original});
  const calls = []; let publicId;
  const env = {VIETQR_CLIENT_ID: 'test-client', VIETQR_API_KEY: 'test-secret',
    CLOUDINARY_CLOUD_NAME: 'cloud', CLOUDINARY_SIGNING_URL: 'https://worker.example'};
  const fetchImpl = async (url, request = {}) => {
    calls.push(String(url));
    if (options.failure && String(url).includes(options.failure)) return Response.json({}, {status: 503});
    if (url.endsWith('/banks')) return Response.json({code: '00', data: [{code: 'MB', shortName: 'MBBank', bin: '970422'}]});
    if (url.endsWith('/generate')) {
      const body = JSON.parse(request.body);
      assert.equal(body.accountNo, original.bankAccountNumber); assert.equal(body.acqId, 970422);
      assert.equal(body.accountName, 'NGUYEN VAN AN');
      assert.equal(body.amount, undefined); assert.equal(body.addInfo, undefined);
      assert.equal(request.headers['x-api-key'], env.VIETQR_API_KEY);
      assert.equal(request.headers['x-client-id'], env.VIETQR_CLIENT_ID);
      return Response.json({code: '00', data: {qrDataURL: options.badImage ? 'data:image/png;base64,YmFk' : png}});
    }
    if (url.endsWith('/sign-upload')) {
      const body = JSON.parse(request.body);
      assert.equal(request.headers.Authorization, 'Bearer seller-token');
      assert.equal(body.resourceId, 'seller'); assert.equal(body.assetType, 'paymentQr');
      publicId = `rescuefarm/sellers/seller/payment-qr-${body.version}`;
      return Response.json({public_id: publicId, overwrite: 'false', timestamp: '123', signature: 'signature',
        api_key: 'cloud-key', invalidate: 'true', asset_folder: 'rescuefarm_assets/payment_qr/seller'});
    }
    assert.equal(String(url), 'https://api.cloudinary.com/v1_1/cloud/image/upload');
    assert.equal(request.body.get('public_id'), publicId); assert.equal(request.body.get('overwrite'), 'false');
    assert.equal(request.body.get('file').type, 'image/png');
    if (options.changeBank) db.documents.set('sellerPaymentProfiles/seller', {...original, bankAccountNumber: '987654321'});
    return Response.json({public_id: publicId, secure_url: `https://res.cloudinary.com/cloud/image/upload/v1/${publicId}.png`});
  };
  const authenticate = async (req, roles) => {
    assert.deepEqual(roles, ['SELLER']);
    if (options.denied) throw Object.assign(new Error('Forbidden'), {status: 403});
    return {uid: 'seller', role: 'SELLER'};
  };
  const api = createPaymentQrApi({db, admin, authenticate, env, fetchImpl});
  return {db, env, calls, original, generate: () => api.generate({get: () => 'Bearer seller-token',
    body: {sellerId: 'other', bankAccountNumber: 'forged'}})};
}

test('generates from saved owner bank details, uploads immutable PNG and saves Cloudinary URL automatically', async () => {
  const f = fixture(); const result = await f.generate();
  assert.equal(result.ok, true);
  assert.match(result.qrUrl, /^https:\/\/res.cloudinary.com\/cloud\/image\/upload\/v1\/rescuefarm\/sellers\/seller\/payment-qr-/);
  assert.equal(f.db.snapshot('sellerPaymentProfiles/seller').get('qrUrl'), result.qrUrl);
  assert.equal(f.calls.length, 4); assert.equal(JSON.stringify(result).includes('test-secret'), false);
});

test('missing VietQR keys reports configuration error without calling external services', async () => {
  const f = fixture(); delete f.env.VIETQR_API_KEY;
  await assert.rejects(f.generate, error => error.status === 503); assert.equal(f.calls.length, 0);
});

test('unauthorized caller cannot generate or upload QR', async () => {
  const f = fixture({denied: true}); await assert.rejects(f.generate, error => error.status === 403);
  assert.equal(f.calls.length, 0);
});

test('missing saved bank fields cannot use client supplied details', async () => {
  const f = fixture(); f.db.documents.set('sellerPaymentProfiles/seller', {bankCode: 'MB'});
  await assert.rejects(f.generate, error => error.status === 409); assert.equal(f.calls.length, 0);
});

for (const failure of ['/generate', '/image/upload']) test('external failure preserves saved QR: ' + failure, async () => {
  const f = fixture({failure}); await assert.rejects(f.generate, error => error.status === 502);
  assert.equal(f.db.snapshot('sellerPaymentProfiles/seller').get('qrUrl'), f.original.qrUrl);
});

test('invalid VietQR image cannot reach Cloudinary', async () => {
  const f = fixture({badImage: true}); await assert.rejects(f.generate, error => error.status === 502);
  assert.equal(f.calls.length, 2);
});

test('bank changed during generation cannot receive an obsolete QR', async () => {
  const f = fixture({changeBank: true}); await assert.rejects(f.generate, error => error.status === 409);
  assert.equal(f.db.snapshot('sellerPaymentProfiles/seller').get('qrUrl'), f.original.qrUrl);
});
