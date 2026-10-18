import {beforeEach, test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';

const host = process.env.FIREBASE_AUTH_EMULATOR_HOST;
if (!/^(localhost|127\.0\.0\.1):\d+$/.test(host || '')) throw Error('Local Auth Emulator required; never runs on production');
const project = 'demo-rescuefarm-reset', base = `http://${host}`;
async function api(method, body) {
  const response = await fetch(`${base}/identitytoolkit.googleapis.com/v1/accounts:${method}?key=fake-emulator-key`,
    {method: 'POST', headers: {'Content-Type': 'application/json', 'X-Firebase-Locale': 'vi'}, body: JSON.stringify(body)});
  return {status: response.status, data: await response.json()};
}
async function account() {
  const email = `${randomUUID()}@example.test`;
  const result = await api('signUp', {email, password: 'OldPassword123!', returnSecureToken: true});
  assert.equal(result.status, 200); return {email, ...result.data};
}
async function resetCode(email) {
  const sent = await api('sendOobCode', {requestType: 'PASSWORD_RESET', email});
  assert.equal(sent.status, 200);
  const records = await (await fetch(`${base}/emulator/v1/projects/${project}/oobCodes`)).json();
  const value = records.oobCodes.filter(code => code.email === email && code.requestType === 'PASSWORD_RESET').at(-1);
  assert.ok(value?.oobCode); return value.oobCode;
}
beforeEach(async () => {
  const result = await fetch(`${base}/emulator/v1/projects/${project}/accounts`, {method: 'DELETE'});
  assert.equal(result.status, 200);
});
test('email reset verifies a code, changes password, preserves UID and rejects old password', async () => {
  const user = await account(), oobCode = await resetCode(user.email);
  const verification = await api('resetPassword', {oobCode});
  assert.equal(verification.status, 200); assert.equal(verification.data.email, user.email);
  const reset = await api('resetPassword', {oobCode, newPassword: 'NewPassword456!'});
  assert.equal(reset.status, 200);
  assert.notEqual((await api('signInWithPassword', {email: user.email, password: 'OldPassword123!', returnSecureToken: true})).status, 200);
  const login = await api('signInWithPassword', {email: user.email, password: 'NewPassword456!', returnSecureToken: true});
  assert.equal(login.status, 200); assert.equal(login.data.localId, user.localId);
});
test('invalid and previously consumed reset codes cannot change the password', async () => {
  const user = await account();
  assert.notEqual((await api('resetPassword', {oobCode: 'invalid-code', newPassword: 'WrongAttempt123!'})).status, 200);
  const oobCode = await resetCode(user.email);
  assert.equal((await api('resetPassword', {oobCode, newPassword: 'NewPassword456!'})).status, 200);
  assert.notEqual((await api('resetPassword', {oobCode, newPassword: 'ReplayPassword789!'})).status, 200);
});
test('a password reset link changes only its recipient account', async () => {
  const first = await account(), second = await account(), oobCode = await resetCode(first.email);
  assert.equal((await api('resetPassword', {oobCode, newPassword: 'NewPassword456!'})).status, 200);
  assert.equal((await api('signInWithPassword', {email: second.email, password: 'OldPassword123!', returnSecureToken: true})).status, 200);
  assert.notEqual((await api('signInWithPassword', {email: second.email, password: 'NewPassword456!', returnSecureToken: true})).status, 200);
});
test('weak passwords are rejected without consuming a valid reset link', async () => {
  const user = await account(), oobCode = await resetCode(user.email);
  assert.notEqual((await api('resetPassword', {oobCode, newPassword: '1'})).status, 200);
  assert.equal((await api('resetPassword', {oobCode, newPassword: 'NewPassword456!'})).status, 200);
});
test('requesting reset does not change credentials until the email link is completed', async () => {
  const user = await account(); await resetCode(user.email);
  const login = await api('signInWithPassword', {email: user.email, password: 'OldPassword123!', returnSecureToken: true});
  assert.equal(login.status, 200); assert.equal(login.data.localId, user.localId);
});
