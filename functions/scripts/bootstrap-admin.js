const admin = require("firebase-admin");
const crypto = require("node:crypto");

function argument(name) {
  const index = process.argv.indexOf(name);
  return index < 0 ? "" : String(process.argv[index + 1] || "").trim();
}

async function main() {
  const projectId = String(process.env.FIREBASE_PROJECT_ID || "").trim();
  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON || "";
  const uid = argument("--uid");
  const email = argument("--email");
  const reason = argument("--reason");
  const operator = String(process.env.ADMIN_BOOTSTRAP_OPERATOR || "").trim();
  if (!process.argv.includes("--confirm") || !projectId || !serviceAccountJson || !operator
      || (!uid && !email) || (uid && email) || reason.length < 10) {
    throw new Error("Require --confirm, one of --uid/--email, --reason (10+ chars), FIREBASE_PROJECT_ID, FIREBASE_SERVICE_ACCOUNT_JSON and ADMIN_BOOTSTRAP_OPERATOR.");
  }
  const serviceAccount = JSON.parse(serviceAccountJson);
  if (serviceAccount.project_id !== projectId) throw new Error("FIREBASE_PROJECT_ID does not match the service account project_id.");
  admin.initializeApp({credential: admin.credential.cert(serviceAccount), projectId});

  const authUser = uid ? await admin.auth().getUser(uid) : await admin.auth().getUserByEmail(email);
  if (authUser.disabled || !authUser.emailVerified) throw new Error("Target Auth user must be enabled and have a verified email.");
  const userRef = admin.firestore().collection("users").doc(authUser.uid);
  const auditRef = admin.firestore().collection("adminBootstrapAudit").doc(crypto.randomUUID());
  const result = await admin.firestore().runTransaction(async tx => {
    const user = await tx.get(userRef);
    if (!user.exists) throw new Error("Target users/{uid} Firestore profile does not exist. Create and verify a normal account first.");
    const previousRole = String(user.get("role") || "").trim();
    if (user.get("status") !== "ACTIVE") throw new Error("Target Firestore account must already be ACTIVE; this script does not unlock accounts.");
    if (previousRole === "ADMIN") return {alreadyAdmin: true, previousRole};
    if (previousRole !== "CUSTOMER") throw new Error(`Only an active CUSTOMER can be bootstrapped; found role ${previousRole || "<empty>"}.`);
    tx.update(userRef, {role: "ADMIN", updatedAt: admin.firestore.FieldValue.serverTimestamp()});
    tx.create(auditRef, {targetUid: authUser.uid, targetEmail: authUser.email || "", previousRole,
      newRole: "ADMIN", projectId, operator, reason,
      createdAt: admin.firestore.FieldValue.serverTimestamp()});
    return {alreadyAdmin: false, previousRole};
  });
  console.log(JSON.stringify({ok: true, projectId, uid: authUser.uid, email: authUser.email || "",
    previousRole: result.previousRole, role: "ADMIN", alreadyAdmin: result.alreadyAdmin}));
}

main().catch(error => {
  console.error(`Admin bootstrap failed: ${error.message}`);
  process.exitCode = 1;
}).finally(() => admin.apps.length ? admin.app().delete().catch(() => {}) : undefined);
