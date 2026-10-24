const {test} = require('node:test');
const assert = require('node:assert/strict');
const {projectPoints, createMapHub} = require('./rescue-map');
const time = n => ({toMillis: () => n});
const row = (id, extra = {}) => ({id, data: {title: 'Điểm demo', status: 'ACTIVE', rescueMode: 'FIXED_POINT',
  latitude: 10.8, longitude: 106.7, startDate: time(100), endDate: time(1000000), targetQuantity: 10,
  bankAccountNumber: 'MUST_NOT_ESCAPE', sellerId: 'PRIVATE_OWNER', ...extra}});
test('public map allowlists display fields and excludes invalid, expired, reserved-out and stale points', () => {
  const values = projectPoints([row('fixed'),row('invalid',{latitude: 91}),row('ended',{endDate:time(1000)}),
    row('empty',{reservedQuantity:10}),row('future',{startDate:time(2000)}),row('hidden',{status:'DRAFT'}),
    row('mobile',{rescueMode:'MOBILE_POINT',locationSharingEnabled:true,currentLatitude:10.9,currentLongitude:106.8,locationUpdatedAt:time(900)}),
    row('stopped',{rescueMode:'MOBILE_POINT',locationSharingEnabled:false,locationUpdatedAt:time(900)})],1000);
  assert.deepEqual(values.map(p=>p.id),['fixed','mobile']);
  assert.equal(values[1].latitude,10.9);
  assert.ok(!JSON.stringify(values).includes('MUST_NOT_ESCAPE'));
  assert.ok(!JSON.stringify(values).includes('PRIVATE_OWNER'));
});
test('mobile freshness exact boundary, future fix and no fallback to fixed coordinates', () => {
  const mobile = extra => row('m',{rescueMode:'MOBILE_POINT',locationSharingEnabled:true,
    currentLatitude:10.9,currentLongitude:106.8,locationUpdatedAt:time(1000),endDate:time(2000000),...extra});
  assert.equal(projectPoints([mobile()],601000).length,1);
  assert.equal(projectPoints([mobile()],601001).length,0);
  assert.equal(projectPoints([mobile({locationUpdatedAt:time(700000)})],601000).length,0);
  assert.equal(projectPoints([mobile({currentLatitude:undefined})],601000).length,0);
});
test('many viewers reuse one watch and warm snapshot; disconnect one does not stop other', () => {
  let watches=0,stops=0,next;const receivedA=[],receivedB=[];
  const hub=createMapHub({now:()=>1000,idleMs:10000,watch:cb=>{watches++;next=cb;return()=>stops++;}});
  const offA=hub.subscribe(v=>receivedA.push(v));next([row('one')]);
  const offB=hub.subscribe(v=>receivedB.push(v));
  assert.equal(watches,1);assert.equal(receivedB.at(-1).points[0].id,'one');
  offA();next([row('two')]);assert.equal(receivedB.at(-1).points[0].id,'two');assert.equal(stops,0);
  offB();hub.close();assert.equal(stops,1);
});
test('clock expiry removes a moving point without another database read', async () => {
  let now=1000,next,watches=0;const received=[];
  const hub=createMapHub({now:()=>now,tickMs:5,watch:cb=>{watches++;next=cb;return()=>{};}});
  const off=hub.subscribe(v=>received.push(v));
  next([row('m',{rescueMode:'MOBILE_POINT',locationSharingEnabled:true,currentLatitude:10.9,currentLongitude:106.8,locationUpdatedAt:time(1000)})]);
  now=601001;await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(received.at(-1).points.length,0);assert.equal(watches,1);off();hub.close();
});
test('cold source waits for first snapshot; expiry ticks do not close a loading stream', async () => {
  let next;const received=[];
  const hub=createMapHub({tickMs:5,watch:cb=>{next=cb;return()=>{};}});
  const off=hub.subscribe(value=>received.push(value));await new Promise(r=>setTimeout(r,20));
  assert.equal(received.length,1);next([]);assert.equal(received.at(-1).live,true);off();hub.close();
});
test('failed watch becomes stale and a later viewer can reconnect; cap does not create extra watches', () => {
  let error,watches=0,stops=0;const received=[];
  const hub=createMapHub({maxViewers:1,watch:(_,fail)=>{watches++;error=fail;return()=>stops++;}});
  const off=hub.subscribe(v=>received.push(v));assert.throws(()=>hub.subscribe(()=>{}),/bận/);
  error(new Error('offline'));assert.equal(received.at(-1).live,false);off();assert.equal(stops,1);
  const off2=hub.subscribe(()=>{});assert.equal(watches,2);off2();hub.close();
});
