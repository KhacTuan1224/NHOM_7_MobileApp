// Isolated test launcher. Production server source remains unchanged.
const fs = require('node:fs'), path = require('node:path'), Module = require('node:module');
if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8088' || process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9098') throw Error('Local emulators required');
process.env.GCLOUD_PROJECT = 'demo-rescuefarm'; process.env.PORT = '3008';
const filename = path.resolve(__dirname, '../../../functions/server.js');
let source = fs.readFileSync(filename, 'utf8');
const original = 'admin.initializeApp({credential: admin.credential.cert(JSON.parse(serviceAccount))});';
if (!source.includes(original)) throw Error('Audit launcher must be reviewed for this source revision');
source = source.replace(original, "admin.initializeApp({projectId: 'demo-rescuefarm'});");
process.env.FIREBASE_SERVICE_ACCOUNT_JSON = '{}';
const mod = new Module(filename, module); mod.filename = filename; mod.paths = Module._nodeModulePaths(path.dirname(filename));
// Never send real push messages, even if a fixture accidentally contains a token.
const admin = require(path.resolve(__dirname, '../../../functions/node_modules/firebase-admin'));
const messaging = admin.messaging;
Object.defineProperty(admin, 'messaging', {value: () => ({sendEachForMulticast: async () => ({successCount: 0, failureCount: 0, responses: []}), send: async () => 'demo-disabled'})});
mod._compile(source, filename);
