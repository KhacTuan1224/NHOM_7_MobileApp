const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/auth/password-captcha-inline.js'),'utf8');
function fixture(){
  let observer,resets=0;const queue=[],layouts=[],styles=[];
  const anchor={getAttribute:k=>k==='src'?'https://www.google.com/recaptcha/api2/anchor?k=public':'reCAPTCHA',style:{visibility:'visible',display:'block'},getBoundingClientRect:()=>({width:304,height:78,bottom:78})};
  const challenge={getAttribute:k=>k==='src'?'https://www.google.com/recaptcha/api2/bframe?k=public':'recaptcha challenge',style:{visibility:'hidden',display:'block'},getBoundingClientRect:()=>({width:300,height:580,bottom:360})};
  const captcha={getBoundingClientRect:()=>({bottom:78}),querySelectorAll:()=>[anchor],contains:f=>f===anchor};
  const status={textContent:'Chọn ô xác minh và làm theo hướng dẫn của Google.',style:{},getBoundingClientRect:()=>({bottom:200})};
  const window={scrollY:0,RescueFarmCaptcha:{layout:(...args)=>layouts.push(args)},getComputedStyle:f=>f.style,
    requestAnimationFrame:cb=>queue.push(cb),addEventListener(){},setInterval(){},scrollTo(){},grecaptcha:{reset(){resets++;}}};
  const document={head:{appendChild:e=>styles.push(e)},body:{},createElement:()=>({}),getElementById:id=>id==='captcha'?captcha:status,
    querySelectorAll:()=>[anchor,challenge]};
  const ctx=vm.createContext({window,document,MutationObserver:class{constructor(cb){observer=cb;}observe(){}}});
  vm.runInContext(source,ctx);
  function flush(){while(queue.length)queue.shift()();return layouts.at(-1);}
  return {window,challenge,status,styles,captcha,flush,change(){observer();return flush();},resets:()=>resets,ctx};
}
test('inline widget reports the normal horizontal checkbox height without an app dialog',()=>{
  const f=fixture(),last=f.flush();assert.equal(last[0],86);assert.equal(last[1],false);assert.equal(last[2],true);
  assert.equal(last[3],'');assert.ok(f.styles[0].textContent.includes('body>p:not(#status){display:none'));
});
test('visible image challenge expands to fit its full height and collapses when closed',()=>{
  const f=fixture();f.flush();f.challenge.style.visibility='visible';let size=f.change();
  assert.equal(size[1],true);assert.ok(size[0]>=596); // Full frame even when centered partly above the compact viewport.
  f.challenge.style.visibility='hidden';size=f.change();assert.equal(size[1],false);assert.equal(size[0],86);
});

test('challenge nested within the widget is not mistaken for the anchor',()=>{
  const f=fixture();f.flush();f.captcha.contains=()=>true;f.challenge.style.visibility='visible';
  const size=f.change();assert.equal(size[1],true);assert.ok(size[0]>=596);
});
test('expiry and provider errors remain visible beside the embedded widget',()=>{
  const f=fixture();f.flush();f.status.textContent='reCAPTCHA đã hết hạn. Vui lòng xác minh lại.';const last=f.change();
  assert.equal(last[3],f.status.textContent);assert.equal(f.status.style.display,'block');assert.ok(last[0]>=208);
});
test('reset uses the existing Google widget and repeated adapter injection does not recreate it',()=>{
  const f=fixture();f.flush();f.window.rescueFarmResetCaptcha();f.flush();assert.equal(f.resets(),1);
  vm.runInContext(source,f.ctx);assert.equal(f.styles.length,1);
});
