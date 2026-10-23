// Process-local, bounded cache for read-only projections. Never use for auth,
// checkout, payment decisions, stock reservations or transaction reads.
function createReadCache({ttlMs = 30000, maxEntries = 500, now = Date.now} = {}) {
  const values = new Map(), pending = new Map();
  async function get(key, loader) {
    const cached = values.get(key);
    if (cached && cached.expiresAt > now()) return cached.value;
    values.delete(key);
    if (pending.has(key)) return pending.get(key);
    const task = Promise.resolve().then(loader).then(value => {
      if (values.size >= maxEntries) values.delete(values.keys().next().value);
      values.set(key, {value, expiresAt: now() + ttlMs});
      return value;
    }).finally(() => pending.delete(key));
    pending.set(key, task); return task;
  }
  return {get};
}
function isQuotaError(error) {
  return !!(error && (error.code === 8 || error.code === 'RESOURCE_EXHAUSTED'
    || error.code === 'resource-exhausted' || /RESOURCE_EXHAUSTED|Quota exceeded/i.test(error.message || '')
    || error.cause && isQuotaError(error.cause)));
}
function createQuotaGuard({now = Date.now, cooldownMs = 300000} = {}) {
  let until = 0;
  return {
    remainingSeconds: () => Math.max(0, Math.ceil((until - now()) / 1000)),
    trip() { const opened = until <= now(); if (opened) until = now() + cooldownMs; return opened; }
  };
}
function createQuotaMiddleware(guard, ready) {
  return (req, res, next) => {
    if (req.path === '/health' || req.path === '/') return next();
    const retryAfter = guard.remainingSeconds();
    if (!retryAfter && ready()) return next();
    return res.set('Retry-After', String(retryAfter || 60)).status(503).json({
      code: retryAfter ? 'FIRESTORE_QUOTA_EXCEEDED' : 'BACKEND_INITIALIZING', retryAfterSeconds: retryAfter || 60,
      error: retryAfter ? 'Firestore đang vượt quota. Vui lòng thử lại sau, không gửi yêu cầu liên tục.' : 'Backend đang chuẩn bị dữ liệu. Vui lòng thử lại sau.'
    });
  };
}
module.exports = {createReadCache, isQuotaError, createQuotaGuard, createQuotaMiddleware};
