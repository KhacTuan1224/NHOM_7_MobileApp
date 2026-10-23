'use strict';
// Back up, publish and read back the exact local rules. Does not write documents.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const admin=require('../functions/node_modules/firebase-admin');
const root=path.resolve(__dirname,'..');
const digest=s=>crypto.createHash('sha256').update(s.replace(/\r\n/g,'\n').trim()).digest('hex');
async function main(){
  if(process.env.FIRESTORE_EMULATOR_HOST)throw Error('Live Rules publishing refuses an emulator override');
  const key=JSON.parse(fs.readFileSync(path.join(root,'serviceAccountKey.json'),'utf8'));
  if(key.project_id!=='nhom7-rescuefarm')throw Error('Unexpected project');
  admin.initializeApp({credential:admin.credential.cert(key)});
  try {
    const rules=admin.securityRules(),source=fs.readFileSync(path.join(root,'firestore.rules'),'utf8');
    const before=await rules.getFirestoreRuleset();
    const directory=path.join(root,'build','rules-publish',new Date().toISOString().replace(/[:.]/g,'-'));
    fs.mkdirSync(directory,{recursive:true});
    fs.writeFileSync(path.join(directory,'previous-ruleset.json'),JSON.stringify(before,null,2));
    fs.writeFileSync(path.join(directory,'local.rules'),source);
    if(!process.argv.includes('--apply')){
      console.log(JSON.stringify({project:key.project_id,applied:false,localHash:digest(source),backup:directory}));return;
    }
    const created=await rules.releaseFirestoreRulesetFromSource(source);
    const after=await rules.getFirestoreRuleset();
    const actual=after.source.find(f=>f.name==='firestore.rules')||after.source[0];
    if(after.name!==created.name||digest(actual.content)!==digest(source))throw Error('Published Rules do not match local source');
    const result={project:key.project_id,applied:true,ruleset:after.name,hash:digest(actual.content),backup:directory};
    fs.writeFileSync(path.join(directory,'published.json'),JSON.stringify(result,null,2));
    console.log(JSON.stringify(result,null,2));
  } finally { await admin.app().delete(); }
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
