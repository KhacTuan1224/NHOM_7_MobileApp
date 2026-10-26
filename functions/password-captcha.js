const express = require('express');
const {createHash, randomBytes} = require('node:crypto');
const fail = (status, message) => Object.assign(new Error(message), {status});
const TEST_SITE_KEY = '6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI';

function createPasswordCaptcha({env = process.env, fetchImpl = global.fetch, now = Date.now} = {}) {
  const router = express.Router(), used = new Map(), limits = new Map();
  const key = (env.PASSWORD_RECAPTCHA_SITE_KEY || '').trim();
  const secret = (env.PASSWORD_RECAPTCHA_SECRET_KEY || '').trim();
  const hosts = new Set((env.PASSWORD_RECAPTCHA_HOSTNAMES || '').split(',').map(x => x.trim().toLowerCase()).filter(Boolean));
  const configured = /^[A-Za-z0-9_-]{15,200}$/.test(key) && key !== TEST_SITE_KEY && !!secret && hosts.size > 0
    && [...hosts].every(h => /^[a-z0-9.-]+$/.test(h));
  const requireConfig = () => { if (!configured) throw fail(503, 'Chưa cấu hình reCAPTCHA đăng nhập trên backend.'); };
  function throttle(req) {
    const t = now();
    for (const [k, v] of limits) if (v.until <= t) limits.delete(k);
    const ip = req.ip || req.socket?.remoteAddress || 'unknown';
    let bucket = limits.get(ip);
    if (!bucket) {
      if (limits.size >= 1000) throw fail(429, 'Có quá nhiều yêu cầu xác minh. Vui lòng thử lại sau.');
      limits.set(ip, bucket = {until: t + 60000, count: 0});
    }
    if (++bucket.count > 120) throw fail(429, 'Có quá nhiều yêu cầu xác minh. Vui lòng thử lại sau.');
  }
  router.get('/auth/password-captcha', (req, res, next) => { try {
    requireConfig(); throttle(req);
    const nonce = randomBytes(18).toString('base64');
    res.set({'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff', 'Referrer-Policy':'no-referrer',
      'Content-Security-Policy': `default-src 'none'; script-src 'nonce-${nonce}' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; connect-src https://www.google.com/recaptcha/; style-src 'unsafe-inline'; img-src https: data:; base-uri 'none'; frame-ancestors 'none'`});
    res.type('html').send(`<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Xác minh reCAPTCHA</title>
<style>body{margin:0;padding:16px 8px;background:#fafcf9;color:#245d3d;font:16px sans-serif}#captcha{display:flex;justify-content:center}p{text-align:center;line-height:1.5}#status{font-size:14px;color:#526158}</style></head>
<body><p>Hoàn thành reCAPTCHA để tiếp tục đăng nhập bằng mật khẩu.</p><div id="captcha"></div><p id="status" role="status">Đang tải reCAPTCHA…</p>
<script nonce="${nonce}">function update(message){document.getElementById('status').textContent=message;}function done(token){if(window.RescueFarmCaptcha){window.RescueFarmCaptcha.complete(token);}else{update('Không kết nối được ứng dụng. Hãy đóng và thử lại.');}}function ready(){grecaptcha.render('captcha',{sitekey:${JSON.stringify(key)},size:'normal',callback:done,'expired-callback':function(){update('reCAPTCHA đã hết hạn. Vui lòng xác minh lại.');},'error-callback':function(){update('Không tải được reCAPTCHA. Kiểm tra mạng rồi mở lại.');}});update('Chọn ô xác minh và làm theo hướng dẫn của Google.');}</script>
<script nonce="${nonce}" src="https://www.google.com/recaptcha/api.js?onload=ready&render=explicit&hl=vi" async defer></script></body></html>`);
  } catch (e) { next(e); }});
  router.post('/auth/password-captcha/verify', async (req, res, next) => { try {
    requireConfig(); throttle(req);
    const b = req.body || {}, token = b.responseToken;
    if (Object.keys(b).some(k => k !== 'responseToken') || typeof token !== 'string' || token.length < 10 || token.length > 8192 || token !== token.trim())
      throw fail(400, 'Phản hồi reCAPTCHA không hợp lệ.');
    const t = now(), hash = createHash('sha256').update(token).digest('hex');
    for (const [k, expires] of used) if (expires <= t) used.delete(k);
    if (used.has(hash)) throw fail(409, 'reCAPTCHA đã được sử dụng. Vui lòng xác minh lại.');
    if (used.size >= 2000) throw fail(429, 'Máy chủ đang bận. Vui lòng thử lại sau.');
    used.set(hash, t + 180000); // Reserve before await; no concurrent reuse in this process.
    let response, result;
    try {
      response = await fetchImpl('https://www.google.com/recaptcha/api/siteverify', {method:'POST',
        body: new URLSearchParams({secret, response:token}), signal:AbortSignal.timeout(10000)});
      if (!response.ok) throw new Error('provider unavailable');
      result = await response.json();
    } catch (_) { throw fail(503, 'Không kết nối được dịch vụ reCAPTCHA. Vui lòng xác minh lại.'); }
    // Google enforces its two-minute, single-use token lifetime. Never trust the UI callback alone.
    const challenge = Date.parse(result.challenge_ts);
    if (result.success !== true || !hosts.has(String(result.hostname || '').toLowerCase())
        || !Number.isFinite(challenge) || challenge > now() + 10000)
      throw fail(403, 'reCAPTCHA không hợp lệ hoặc đã hết hạn. Vui lòng xác minh lại.');
    res.set('Cache-Control','no-store').json({ok:true});
  } catch (e) { next(e); }});
  return {router, configured};
}
module.exports = {createPasswordCaptcha};
