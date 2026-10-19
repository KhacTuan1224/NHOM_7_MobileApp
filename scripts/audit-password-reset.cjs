// Read-only Auth/Firestore audit. Never log account addresses, hashes, action links or SMTP credentials.
const fs = require('node:fs'), path = require('node:path');
const admin = require('../functions/node_modules/firebase-admin');
const {GoogleAuth} = require('../functions/node_modules/google-auth-library');
const root = path.resolve(__dirname, '..'), keyFile = path.join(root, 'serviceAccountKey.json');
const credentials = JSON.parse(fs.readFileSync(keyFile, 'utf8'));
if (credentials.project_id !== 'nhom7-rescuefarm' || process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_AUTH_EMULATOR_HOST)
  throw Error('Unexpected project/environment');
async function main() {
  admin.initializeApp({credential: admin.credential.cert(credentials)});
  try {
    const db = admin.firestore();
    const profiles = await db.collection('users').select('email', 'role', 'status', 'password', 'passwordHash',
      'resetToken', 'passwordResetToken', 'resetCode', 'passwordResetCode').get();
    const summary = {project: credentials.project_id, at: new Date().toISOString(), documentWrites: 0,
      profiles: profiles.size, authAccounts: 0, passwordAccounts: 0, nonPasswordAccounts: 0,
      authAccountsWithoutProfile: 0, profilesWithoutAuthAccount: 0, profileEmailMismatch: 0, forbiddenCredentialFieldOccurrences: 0};
    const profileMap = new Map(profiles.docs.map(d => [d.id, d]));
    const seen = new Set(); let page;
    do {
      const result = await admin.auth().listUsers(1000, page);
      for (const user of result.users) {
        seen.add(user.uid); summary.authAccounts++;
        if (user.providerData.some(p => p.providerId === 'password')) summary.passwordAccounts++;
        else summary.nonPasswordAccounts++;
        const profile = profileMap.get(user.uid);
        if (!profile) summary.authAccountsWithoutProfile++;
        else if (String(profile.get('email') || '').trim().toLowerCase() !== String(user.email || '').trim().toLowerCase()) summary.profileEmailMismatch++;
      }
      page = result.pageToken;
    } while (page);
    for (const doc of profiles.docs) {
      if (!seen.has(doc.id)) summary.profilesWithoutAuthAccount++;
      for (const field of ['password', 'passwordHash', 'resetToken', 'passwordResetToken', 'resetCode', 'passwordResetCode'])
        if (Object.hasOwn(doc.data(), field)) summary.forbiddenCredentialFieldOccurrences++;
    }
    summary.resetRelatedCollections = (await db.listCollections()).map(c => c.id).filter(n => /password|reset|mail/i.test(n));
    try {
      const http = await new GoogleAuth({keyFilename: keyFile, scopes: ['https://www.googleapis.com/auth/cloud-platform']}).getClient();
      const {data} = await http.request({url: `https://identitytoolkit.googleapis.com/admin/v2/projects/${credentials.project_id}/config`, timeout: 30000});
      summary.authConfig = {
        emailPasswordEnabled: data.signIn?.email?.enabled === true,
        passwordRequired: data.signIn?.email?.passwordRequired === true,
        emailEnumerationProtection: data.emailPrivacyConfig?.enableImprovedEmailPrivacy === true,
        firebaseActionDomainAuthorized: (data.authorizedDomains || []).includes(`${credentials.project_id}.firebaseapp.com`),
        deliveryMethod: data.notification?.sendEmail?.method || 'DEFAULT',
        usesDefaultFirebaseActionHandler: !data.notification?.sendEmail?.callbackUri
          || data.notification.sendEmail.callbackUri.split('?')[0] === `https://${credentials.project_id}.firebaseapp.com/__/auth/action`,
        resetTemplateConfigured: !!data.notification?.sendEmail?.resetPasswordTemplate,
        passwordPolicyEnabled: data.passwordPolicyConfig?.passwordPolicyEnforcementState || 'DEFAULT'
      };
    } catch (error) { summary.authConfigRead = {available: false, status: error.response?.status || null}; }
    const destination = path.join(root, 'build/password-reset'); fs.mkdirSync(destination, {recursive: true});
    fs.writeFileSync(path.join(destination, 'live-audit.json'), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify(summary));
  } finally { await admin.app().delete(); }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
