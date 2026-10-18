// Metadata only. This tool never creates, modifies or deletes Firestore documents.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const admin=require('../functions/node_modules/firebase-admin');
const {v1:{FirestoreAdminClient}}=require('../functions/node_modules/@google-cloud/firestore');
const {GoogleAuth}=require('../functions/node_modules/google-auth-library');
const root=path.resolve(__dirname,'..'),keyFile=path.join(root,'serviceAccountKey.json');
const credentials=JSON.parse(fs.readFileSync(keyFile,'utf8'));
if(credentials.project_id!=='nhom7-rescuefarm')throw Error('Unexpected project');
if(process.env.FIRESTORE_EMULATOR_HOST)throw Error('Live audit cannot use emulator');
const project=credentials.project_id,database=`projects/${project}/databases/(default)`;
const directory=path.join(root,'build','schema-audit',new Date().toISOString().replace(/[:.]/g,'-'));
fs.mkdirSync(directory,{recursive:true});
const write=(name,value)=>fs.writeFileSync(path.join(directory,name),JSON.stringify(value,null,2));
const hash=s=>crypto.createHash('sha256').update(s.replace(/\r\n/g,'\n').trim()).digest('hex');
async function main(){
  admin.initializeApp({credential:admin.credential.cert(credentials)});
  const db=admin.firestore(),client=new FirestoreAdminClient({keyFilename:keyFile,projectId:project});
  try{
    const [databaseInfo]=await client.getDatabase({name:database});
    const refs=await db.listCollections();
    const collections=await Promise.all(refs.map(async c=>({name:c.id,documents:(await c.count().get()).data().count})));
    const [indexes]=await client.listIndexes({parent:database+'/collectionGroups/-'});
    const [commentIdField]=await client.getField({name:database+'/collectionGroups/comments/fields/id'});
    const auth=new GoogleAuth({keyFilename:keyFile,scopes:['https://www.googleapis.com/auth/cloud-platform','https://www.googleapis.com/auth/firebase']});
    const http=await auth.getClient();
    const {data:release}=await http.request({url:`https://firebaserules.googleapis.com/v1/projects/${project}/releases/cloud.firestore`});
    const {data:ruleset}=await http.request({url:`https://firebaserules.googleapis.com/v1/${release.rulesetName}`});
    const files=ruleset.source.files;
    const rule=files.find(f=>f.name==='firestore.rules')||(files.length===1?files[0]:null);
    if(rule)fs.writeFileSync(path.join(directory,'deployed.rules'),rule.content);
    const result={project,database:{name:databaseInfo.name,type:databaseInfo.type,locationId:databaseInfo.locationId},auditedAt:new Date().toISOString(),collections:collections.sort((a,b)=>a.name.localeCompare(b.name)),indexes,commentIdField,rules:{release:release.name,ruleset:release.rulesetName,deployedHash:rule?hash(rule.content):null,localHash:hash(fs.readFileSync(path.join(root,'firestore.rules'),'utf8'))},documentsWritten:0};
    write('metadata.json',result);
    console.log(JSON.stringify({directory,database:result.database,collections:result.collections,indexCount:indexes.length,indexStates:indexes.reduce((a,i)=>(a[i.state]=(a[i.state]||0)+1,a),{}),rules:result.rules,documentsWritten:0},null,2));
  }finally{await client.close();await admin.app().delete();}
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
