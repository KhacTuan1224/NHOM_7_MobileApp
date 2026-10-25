const express = require('express');
const ms = value => value && typeof value.toMillis === 'function' ? value.toMillis() : 0;
const text = value => typeof value === 'string' ? value.slice(0, 250) : '';
const coordinates = (lat, lng) => Number.isFinite(lat) && Number.isFinite(lng)
  && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && (lat !== 0 || lng !== 0);

// Public, allowlisted display data only. Never use this projection for checkout.
function projectPoints(rows, now) {
  return rows.flatMap(({id, data: c}) => {
    const mobile = c.rescueMode === 'MOBILE_POINT';
    if (c.status !== 'ACTIVE' || !['FIXED_POINT', 'MOBILE_POINT'].includes(c.rescueMode)
        || ms(c.startDate) > now || ms(c.endDate) <= now) return [];
    const remaining = Number(c.targetQuantity || 0) - Number(c.reservedQuantity || 0) - Number(c.rescuedQuantity || 0);
    if (!(remaining > 0.000001)) return [];
    const lat = mobile ? c.currentLatitude : c.latitude, lng = mobile ? c.currentLongitude : c.longitude;
    const updatedAt = ms(c.locationUpdatedAt);
    if (!coordinates(lat, lng) || mobile && (c.locationSharingEnabled !== true
        || updatedAt <= 0 || updatedAt > now || now - updatedAt > 600000)) return [];
    return [{id, title: text(c.title), locationName: text(c.locationName), mode: c.rescueMode,
      latitude: lat, longitude: lng, updatedAt: mobile ? updatedAt : 0,
      endAt: ms(c.endDate), remaining, unit: text(c.quantityUnit)}];
  });
}

// One source subscription per backend process, shared by all HTTP viewers.
// A short idle grace period avoids paying a new initial query on every rotation.
function createMapHub({watch, now = Date.now, idleMs = 60000, tickMs = 10000,
    maxViewers = 128, onError = () => {}}) {
  const subscribers = new Set();
  let unsubscribe, idleTimer, tick, rows = [], live = false, signature = '', truncated = false;
  const snapshot = () => ({points: projectPoints(rows, now()), live,
    generatedAt: now(), truncated});
  function publish(force = false) {
    const value = snapshot(), next = JSON.stringify([value.points, value.live, value.truncated]);
    if (!force && next === signature) return;
    signature = next;
    for (const listener of subscribers) listener(value);
  }
  function close() {
    clearTimeout(idleTimer); clearInterval(tick); idleTimer = tick = null;
    unsubscribe?.(); unsubscribe = null; live = false;
  }
  function subscribe(listener) {
    if (subscribers.size >= maxViewers) throw Object.assign(new Error('Bản đồ đang bận.'), {status: 503});
    clearTimeout(idleTimer); subscribers.add(listener);
    if (!unsubscribe) {
      live = false;
      unsubscribe = watch((values, limited = false) => {
        rows = values; truncated = limited; live = true; publish(true);
      }, error => {
        live = false; publish(true); onError(error);
        // Keep failed listeners closed until all clients back off and reconnect.
      });
      // The expiry clock must not publish an "offline" snapshot while the
      // first Firestore snapshot is still loading (especially on cold starts).
      tick = setInterval(() => { if (live) publish(); }, tickMs); tick.unref?.();
    }
    listener(snapshot());
    let released = false;
    return () => {
      if (released) return; released = true; subscribers.delete(listener);
      if (!subscribers.size) {
        if (!live) close();
        else { idleTimer = setTimeout(close, idleMs); idleTimer.unref?.(); }
      }
    };
  }
  return {subscribe, snapshot, close};
}

function createRescueMapApi({db, now = Date.now, onError, idleMs, tickMs}) {
  const router = express.Router();
  const hub = createMapHub({now, idleMs, tickMs, onError, watch: (next, error) =>
    // Firestore Listen does not support projection/select queries. Apply the
    // explicit public field allowlist in projectPoints before any HTTP output.
    db.collection('campaigns').where('status', '==', 'ACTIVE').limit(500)
      .onSnapshot(snap => next(snap.docs.map(doc => ({id: doc.id, data: doc.data()})), snap.size === 500), error)});
  router.get('/rescue-map/events', (req, res, next) => {
    let release = () => {}, heartbeat, ended = false;
    const finish = () => { if (ended) return; ended = true; clearInterval(heartbeat); release(); res.end(); };
    try {
      // Acquire before headers so overload can return a normal 503.
      const initial = [];
      let ready = false;
      const send = value => {
        if (!ready) { initial.push(value); return; }
        if (ended) return;
        if (!res.write('data: ' + JSON.stringify(value) + '\n\n') || !value.live) finish();
      };
      release = hub.subscribe(send);
      res.status(200).set({'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform', 'X-Accel-Buffering': 'no'});
      res.flushHeaders(); ready = true;
      // A cold hub is waiting for the first Firestore snapshot, not offline yet.
      for (const value of initial) if (value.live) send(value);
      if (!ended) {
        heartbeat = setInterval(() => { if (!res.write(': heartbeat\n\n')) finish(); }, 15000);
        heartbeat.unref?.();
      }
      // IncomingMessage 'close' can mean GET request completion, while its
      // streamed response is still open. Own the response/socket lifetime.
      res.on('close', finish);
    } catch (error) { clearInterval(heartbeat); release(); if (res.headersSent) finish(); else next(error); }
  });
  return {router, hub};
}
module.exports = {projectPoints, createMapHub, createRescueMapApi};
