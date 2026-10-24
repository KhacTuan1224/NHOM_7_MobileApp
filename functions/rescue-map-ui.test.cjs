// Exercises the actual bundled HTML logic; this is not an Android/WebView UI test.
const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
function fixture(options={}){
  let now=1000000;
  class Element{constructor(tag){this.tag=tag;this.children=[];this.textContent='';}appendChild(child){this.children.push(child);}setAttribute(){}}
  class Marker{constructor(at){this.at={lat:at[0],lng:at[1]};}addTo(){return this;}getLatLng(){return this.at;}setLatLng(at){this.at={lat:at[0],lng:at[1]};return this;}bindPopup(p){this.popup=p;return this;}on(){return this;}}
  const map={setView(){return this;},fitBounds(){},removeLayer(){},invalidateSize(){},on(){return this;}};
  const ctx={Date:class extends Date{static now(){return now;}},Map,Set,JSON,Number,Math,URLSearchParams,encodeURIComponent,
    location:{search:'?expanded=1'},document:{body:{classList:{add(){}}},querySelectorAll:()=>[],getElementById:()=>new Element('div'),createElement:t=>new Element(t)},
    L:{map:()=>map,tileLayer:()=>({on(){return this;},addTo(){}}),marker:at=>new Marker(at),divIcon:x=>x},
    performance:{now:()=>0},requestAnimationFrame:cb=>{cb(1000);return 1;},cancelAnimationFrame(){},setInterval(){},window:{rescueMapOptions:options,addEventListener(){}}};
  vm.createContext(ctx);
  const html=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/maps/rescue-map.html'),'utf8');
  vm.runInContext(html.split('<script>')[1].split('</script>')[0],ctx);
  return {ctx,run:s=>vm.runInContext(s,ctx),at:n=>{now=n;},update:(rows,live=true)=>ctx.window.updateRescuePoints(rows,live)};
}
const fixed=(id,extra={})=>({id,title:'<script>unsafe</script>',mode:'FIXED_POINT',latitude:10,longitude:106,endAt:3000000,remaining:5,unit:'kg',...extra});
test('co-located fixed campaigns group; popups use text and correct navigation IDs',()=>{
  const f=fixture();f.update([fixed('a'),fixed('b')]);assert.equal(f.run('markers.size'),1);
  assert.equal(f.run("markers.get('a').popup.children[0].textContent"),'Điểm có 2 chiến dịch');
  assert.equal(f.run("markers.get('a').popup.children.filter(e=>e.tag==='a').map(e=>e.href).join(',')"),'rescuefarm-map://campaign/a,rescuefarm-map://campaign/b');
});
test('mobile reuses its marker, moves to an observed fix, and hides on stale/offline/stop',()=>{
  const f=fixture(),m=fixed('mobile',{mode:'MOBILE_POINT',updatedAt:1000000});f.update([m]);
  const marker=f.run("markers.get('mobile')");f.update([{...m,latitude:10.01}]);
  assert.equal(f.run("markers.get('mobile')"),marker);assert.equal(marker.getLatLng().lat,10.01);
  f.at(1600001);f.run('render()');assert.equal(f.run('markers.size'),0);
  f.at(1000000);f.update([m],false);assert.equal(f.run('markers.size'),0);
  f.update([m]);assert.equal(f.run('markers.size'),1);f.update([]);assert.equal(f.run('markers.size'),0);
});
test('local filtering does not trigger requests or change source points',()=>{
  const f=fixture();f.update([fixed('a'),fixed('mobile',{mode:'MOBILE_POINT',updatedAt:1000000,latitude:11})]);
  f.ctx.window.filterRescuePoints('MOBILE_POINT');assert.equal(f.run('markers.size'),1);assert.equal(f.run('last.length'),2);
});
test('profile mode renders only the saved coordinate and ignores rescue feeds',()=>{
  const f=fixture({profile:true,expanded:true});f.ctx.window.setProfilePoint(10.76,106.67);
  const marker=f.run('profileMarker');assert.equal(marker.getLatLng().lat,10.76);
  f.update([fixed('rescue-campaign')]);assert.equal(f.run('markers.size'),0);assert.equal(f.run('last.length'),0);
  f.ctx.window.setProfilePoint(10.77,106.68);assert.equal(f.run('profileMarker'),marker);
  assert.equal(marker.getLatLng().lat,10.77);
  assert.equal(marker.popup.children[0].textContent,'Vị trí hồ sơ');
});
