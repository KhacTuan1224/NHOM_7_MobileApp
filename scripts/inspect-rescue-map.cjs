// Read-only campaign aggregates. Never outputs names, document IDs, coordinates or credentials.
const fs = require('node:fs');
const path = require('node:path');
const admin = require('../functions/node_modules/firebase-admin');
const root = path.resolve(__dirname, '..');
async function main() {
  if (process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Refusing an ambiguous database target');
  const credentials = JSON.parse(fs.readFileSync(path.join(root, 'serviceAccountKey.json'), 'utf8'));
  if (credentials.project_id !== 'nhom7-rescuefarm') throw new Error('Unexpected project');
  admin.initializeApp({credential: admin.credential.cert(credentials)});
  try {
    const snapshot = await admin.firestore().collection('campaigns').select(
      'rescueMode', 'status', 'locationSharingEnabled', 'locationUpdatedAt',
      'latitude', 'longitude', 'currentLatitude', 'currentLongitude',
      'startDate', 'endDate', 'targetQuantity', 'reservedQuantity', 'rescuedQuantity'
    ).get();
    const now = Date.now();
    const time = value => value && typeof value.toMillis === 'function' ? value.toMillis() : NaN;
    const coordinate = (lat, lng) => Number.isFinite(lat) && Number.isFinite(lng)
      && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && (lat !== 0 || lng !== 0);
    const result = {auditedAt: new Date(now).toISOString(), project: credentials.project_id,
      collection: 'campaigns', documentsRead: snapshot.size, documentsWritten: 0,
      byMode: {}, byModeAndStatus: {}, mobile: {total: 0, active: 0, sharingEnabled: 0,
        freshLocation: 0, activeFreshInScheduleWithRemaining: 0},
      fixed: {total: 0, active: 0, validCoordinates: 0, activeInScheduleWithRemaining: 0},
      excludes: ['names', 'document IDs', 'coordinate values', 'credentials']};
    for (const doc of snapshot.docs) {
      const c = doc.data(), mode = c.rescueMode || 'MISSING', status = c.status || 'MISSING';
      result.byMode[mode] = (result.byMode[mode] || 0) + 1;
      const key = mode + '/' + status;
      result.byModeAndStatus[key] = (result.byModeAndStatus[key] || 0) + 1;
      const scheduled = (!c.startDate || time(c.startDate) <= now) && time(c.endDate) > now;
      const remaining = Number(c.targetQuantity || 0) - Number(c.reservedQuantity || 0) - Number(c.rescuedQuantity || 0) > 0;
      if (mode === 'MOBILE_POINT') {
        const age = now - time(c.locationUpdatedAt);
        const fresh = c.locationSharingEnabled === true && age >= 0 && age <= 600000
          && coordinate(c.currentLatitude, c.currentLongitude);
        result.mobile.total++;
        if (status === 'ACTIVE') result.mobile.active++;
        if (c.locationSharingEnabled === true) result.mobile.sharingEnabled++;
        if (fresh) result.mobile.freshLocation++;
        if (status === 'ACTIVE' && scheduled && remaining && fresh) result.mobile.activeFreshInScheduleWithRemaining++;
      } else if (mode === 'FIXED_POINT') {
        result.fixed.total++;
        if (status === 'ACTIVE') result.fixed.active++;
        if (coordinate(c.latitude, c.longitude)) result.fixed.validCoordinates++;
        if (status === 'ACTIVE' && scheduled && remaining && coordinate(c.latitude, c.longitude)) result.fixed.activeInScheduleWithRemaining++;
      }
    }
    const out = path.join(root, 'docs', 'audits', 'rescue-map-live-2026-10-24.json');
    fs.mkdirSync(path.dirname(out), {recursive: true});
    fs.writeFileSync(out, JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify(result, null, 2));
  } finally { await admin.app().delete(); }
}
main().catch(error => { console.error(error.code || error.name, error.message); process.exitCode = 1; });
