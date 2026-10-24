import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,updateDoc,serverTimestamp,Timestamp} from 'firebase/firestore';
const require=createRequire(import.meta.url),admin=require('../../functions/node_modules/firebase-admin');
const express=require('../../functions/node_modules/express');
const {createRescueMapApi}=require('../../functions/rescue-map');
if(!/^(127\.0\.0\.1|localhost):\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST||''))throw Error('Local emulator required');
test('Rules-owned location changes stream to two public clients, stop removes marker, no private fields escape',async t=>{
  const env=await initializeTestEnvironment({projectId:'demo-rescuefarm',firestore:{rules:fs.readFileSync(new URL('../../firestore.rules',import.meta.url),'utf8')}});
  const app=admin.initializeApp({projectId:'demo-rescuefarm'},'map-integration');const db=app.firestore();
  const api=createRescueMapApi({db,idleMs:5,tickMs:10,onError:e=>console.error('MAP_WATCH_ERROR',e.code,e.message)}),server=express().use(api.router).listen(0,'127.0.0.1');
  const a=new AbortController(),b=new AbortController();
  t.after(async()=>{a.abort();b.abort();api.hub.close();await new Promise(r=>server.close(r));await env.cleanup();await app.delete();});
  await env.clearFirestore();
  const at=Date.now(),stamp=n=>admin.firestore.Timestamp.fromMillis(n);
  await db.doc('users/map-seller').set({role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED'});
  await db.doc('users/other').set({role:'SELLER',status:'ACTIVE',sellerStatus:'APPROVED'});
  const data={id:'map-mobile',sellerId:'map-seller',title:'Xe giải cứu TEST',description:'',rescueReason:'OVER_SUPPLY',urgencyLevel:'NORMAL',rescueMode:'MOBILE_POINT',
    batchTargets:{batch:10},targetQuantity:10,reservedQuantity:0,rescuedQuantity:0,startDate:stamp(at-60000),endDate:stamp(at+3600000),
    latitude:10,longitude:106,currentLatitude:0,currentLongitude:0,locationUpdatedAt:null,locationSharingEnabled:false,locationName:'DEMO',status:'ACTIVE',quantityUnit:'kg',unitStatus:'VERIFIED',
    createdAt:stamp(at),updatedAt:stamp(at),reservedByBatch:{},rescuedByBatch:{},allocationVersion:1};
  await db.doc('campaigns/map-mobile').set(data);
  const owner=env.authenticatedContext('map-seller').firestore(),other=env.authenticatedContext('other').firestore();
  const update={currentLatitude:10.1,currentLongitude:106.1,locationSharingEnabled:true,locationUpdatedAt:serverTimestamp(),updatedAt:serverTimestamp()};
  await assertFails(updateDoc(doc(other,'campaigns/map-mobile'),update));
  await assertFails(updateDoc(doc(owner,'campaigns/map-mobile'),{...update,locationUpdatedAt:Timestamp.fromMillis(at-60000)}));
  const url=`http://127.0.0.1:${server.address().port}/rescue-map/events`;
  async function client(signal){const response=await fetch(url,{signal});assert.equal(response.status,200);const reader=response.body.getReader();let buffer='';
    return async predicate=>{const until=Date.now()+15000;while(Date.now()<until){const next=await Promise.race([reader.read(),new Promise((_,reject)=>{const timer=setTimeout(()=>reject(Error('SSE timeout')),15000);timer.unref();})]);assert.ok(!next.done);buffer+=new TextDecoder().decode(next.value);let end;while((end=buffer.indexOf('\n\n'))>=0){const event=buffer.slice(0,end);buffer=buffer.slice(end+2);if(event.startsWith('data: ')){const value=JSON.parse(event.slice(6));if(predicate(value))return value;}}}throw Error('No matching snapshot');};}
  const readA=await client(a.signal),readB=await client(b.signal);
  await readA(v=>v.live&&v.points.length===0);await readB(v=>v.live&&v.points.length===0);
  await assertSucceeds(updateDoc(doc(owner,'campaigns/map-mobile'),update));
  const [first,second]=await Promise.all([readA(v=>v.points.some(p=>p.latitude===10.1)),readB(v=>v.points.some(p=>p.latitude===10.1))]);
  assert.equal(first.points[0].id,'map-mobile');assert.equal(second.points[0].id,'map-mobile');assert.ok(!JSON.stringify(first).includes('map-seller'));
  await assertSucceeds(updateDoc(doc(owner,'campaigns/map-mobile'),{...update,currentLatitude:10.2}));
  await Promise.all([readA(v=>v.points.some(p=>p.latitude===10.2)),readB(v=>v.points.some(p=>p.latitude===10.2))]);
  await assertSucceeds(updateDoc(doc(owner,'campaigns/map-mobile'),{currentLatitude:0,currentLongitude:0,locationUpdatedAt:null,locationSharingEnabled:false,updatedAt:serverTimestamp()}));
  await Promise.all([readA(v=>v.points.length===0),readB(v=>v.points.length===0)]);
});
