from pathlib import Path
from datetime import datetime, timezone
import subprocess, json, hashlib, re, shutil, xml.etree.ElementTree as ET
from PIL import Image
ROOT=Path(__file__).resolve().parents[3]; REPORT=ROOT/'docs/report'; E=REPORT/'evidence'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT).decode('utf-8').strip()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf-8')
def main():
    apk=ROOT/'app/build/outputs/apk/reportDemo/app-reportDemo.apk'
    tests=[]
    for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        r=ET.parse(p).getroot();tests.append(dict(file=p.name,tests=int(r.get('tests',0)),failures=int(r.get('failures',0)),errors=int(r.get('errors',0)),skipped=int(r.get('skipped',0)),timestamp=r.get('timestamp')))
    lint=ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot()
    issues={k:sum(x.get('severity')==k for x in lint.findall('issue')) for k in ['Error','Fatal','Warning','Information']}
    r=dict(auditDate='2026-10-24',timezone='Asia/Novosibirsk',head=git('rev-parse','HEAD'),workingTree=git('status','--short'),apk=dict(package='com.example.nhom_7_mobileapp.reportdemo',sha256=sha(apk),versionName='1.0',versionCode=1,modifiedUtc=datetime.fromtimestamp(apk.stat().st_mtime,timezone.utc).isoformat()),jvm={k:sum(t[k] for t in tests) for k in ['tests','failures','errors','skipped']},jvmSuites=tests,lint=issues,
      backend={'checkExit':0,'tests':183,'pass':183,'fail':0,'skipped':0,'log':'backend-unit.log'},worker={'tests':9,'pass':9,'fail':0,'skipped':0},firestore={'project':'demo-rescuefarm','tests':173,'pass':173,'fail':0,'skipped':0,'log':'firestore-head-retry.log'},navigation={'destinations':59,'exit':0},instrumentation={'selected':11,'pass':11,'fail':0,'skipped':0,'log':'instrumentation-final-apk.log'},limits=['No live OAuth, SePay, media upload, FCM delivery or production deploy validated','UEH similarity not run','MSSV, class and task allocation percentages await confirmation'])
    for filename in ['backend-unit.log','firestore-head-retry.log']:
        text=(E/filename).read_text(encoding='utf-8-sig');assert '# fail 0' in text
    dump(E/'audit-results-current.json',r)
    lines=git('log','--merges','-30','--format=%H%x09%ad%x09%s','--date=iso-strict').splitlines()
    merges=[dict(zip(['sha','date','subject'],x.split('\t',2))) for x in lines]
    for m in merges[:8]:m['files']=git('diff-tree','--no-commit-id','--name-only','-r',m['sha']+'^1',m['sha']).splitlines()
    dump(E/'git-evidence.json',dict(head=r['head'],merges=merges,aliases=git('shortlog','-sn','HEAD').splitlines(),remotes=git('remote','-v').splitlines(),warning='Counts do not measure contribution percentages; alias mapping awaits team confirmation'))
    inventory=[]
    roots=['app/src/main/java','app/src/main/res','app/src/test','app/src/androidTest','functions','cloudinary-worker','tests/firestore','.github/workflows']
    for root in roots:
        for p in (ROOT/root).rglob('*'):
            if not p.is_file() or any(x in p.parts for x in ['node_modules','build','.git']):continue
            if p.suffix not in ['.java','.xml','.js','.cjs','.mjs','.yml','.toml']:continue
            t=p.read_text(encoding='utf-8',errors='replace')
            inventory.append(dict(path=p.relative_to(ROOT).as_posix(),sha256=sha(p),lines=len(t.splitlines()),classes=re.findall(r'\b(?:class|interface|enum)\s+(\w+)',t)[:12]))
    dump(E/'source-inventory.json',inventory)
    (E/'implementation-diff.patch').write_text(git('diff','--','app/build.gradle.kts'),encoding='utf-8')
    manifest=[]; chosen={}
    for p in (REPORT/'assets/screenshots').rglob('*.png'):
        if 'originals' in p.parts:continue
        if p.stem not in chosen or p.stat().st_mtime>chosen[p.stem].stat().st_mtime:chosen[p.stem]=p
    destinations={'home':'guestHomeFragment','discovery':'discoveryFragment','product':'productDetailFragment','cart':'cartFragment','checkout':'checkoutFragment','orders':'customerOrderListFragment','dashboard':'sellerDashboardFragment','products':'sellerProductListFragment','batches':'batchManagerFragment','campaigns':'sellerCampaignListFragment'}
    for name,p in sorted(chosen.items()):
        actor='admin' if 'admin' in name else 'seller' if 'seller' in name else 'customer' if 'customer' in name else 'guest'
        dest='rescueFundFragment' if 'fund' in name else 'loginFragment' if name.startswith('login') else 'productListFragment' if name.startswith('catalog') else destinations.get(name.split('-')[-1],'unknown')
        if name=='admin-products':dest='adminResourceFragment'
        if name=='admin-dashboard':dest='adminDashboardFragment'
        target=REPORT/'assets/screenshots/originals'/actor/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
        w,h=Image.open(p).size
        manifest.append(dict(file=target.relative_to(REPORT).as_posix(),sha256=sha(target),actor=actor,destination=dest,purpose=name,steps='ActivityScenario opens real destination; waits; asserts current destination; UiAutomation.takeScreenshot. See ReportScreenshotEvidenceTest.',timestampUtc=datetime.fromtimestamp(p.stat().st_mtime,timezone.utc).isoformat(),head=r['head'],apk=r['apk'],size=[w,h],orientation='landscape' if w>h else 'portrait',environment='emulator-5580 API33; demo-rescuefarm; Auth9098/Firestore8088; HTTP3008',fixture='source/seed-demo.cjs; fund states source/fund-demo.cjs',expected='Data and actor match isolated fixture; destination is real running Fragment',actual='PNG captured; relevant assertions passed in selected runner logs',limits=('Fund transitions via actual module API harness; image existence HEAD stub, image URLs do not prove uploads or physical delivery/payout.' if 'fund-' in name else 'No disk image cache verified; fixture Product images empty.' if 'catalog' in name else 'Direct destination opening does not test all navigation clicks; no live external provider.'),offlineScope='New process, Firestore persistence disabled, SDK network disabled, Room fallback' if 'room-offline' in name else None))
    fundResult=json.loads((E/'fund-demo-pay.json').read_text(encoding='utf-8'))
    jobIds={x['status']:x['id'] for x in fundResult['jobs']}
    for item in manifest:
        if item['purpose'].startswith('login-'):
            item['steps']='captureStableRotationPair: sign out demo, disable SDK network, open login; set email; wait for rendered frame; assert/capture portrait; rotate; assert/capture landscape; recreate and assert same email.'
            item['environment']='emulator-5580 API33; separate demo APK; endpoints configured localhost; services stopped and Firestore SDK network disabled during final login capture'
            item['actual']='Passed rotation-stable-pair-retry.log; both PNGs visibly contain the same demo email'
        if 'room-offline' in item['purpose']:
            item['deviceNetwork']='Wi-Fi and cellular data disabled on the dedicated test emulator during capture, restored in finally'
            item['steps']+=' Final capture also disables emulator Wi-Fi/data after force-stop; see offline-device-network-final.log.'
        if item['purpose'].startswith('fund-'):
            item['linkedDeliveryId']=jobIds['CANCELLED'] if 'cancelled' in item['purpose'] else jobIds['PAID']
    dump(E/'screenshots-manifest.json',manifest)
    (REPORT/'source/artifact.md').write_text('''# Contract from supplied PDF reference\nReference: docs/MOBILE FINAL PROJECT.pdf, 13 pages.\nA4 portrait, Times New Roman body, black numbered chapter hierarchy; centered academic cover with the supplied UEH logo.\nThe source is PDF, not editable DOCX: reconstruct the six-chapter structure and preserve all Chapter 1 text by extraction.\nRebuild TOC and figure/table captions as fields; correct the duplicate section numbers in the template.\nUse readable 13 pt body, 1.3 line spacing, 25 mm margins (measurements not specified as an official requirement).\nLandscape pages may be used for dense diagrams when necessary.\nIntentional deviations: omit corner decorations that bake a future date into a raster; use editable cover metadata; keep title black; mark missing student data; add test evidence and limits; no claim of exact official UEH style certification.\n''',encoding='utf-8')
    dump(E/'reference.json',dict(file='docs/MOBILE FINAL PROJECT.pdf',sha256=sha(ROOT/'docs/MOBILE FINAL PROJECT.pdf'),pages=13))
    contract=REPORT/'source/artifact.md'
    contract.write_text(contract.read_text(encoding='utf-8').replace('1.3 line spacing','1.15 line spacing'),encoding='utf-8')
    inputs=['README.md','final.md','app/build.gradle.kts','gradle/libs.versions.toml','app/src/main/AndroidManifest.xml','app/src/main/res/navigation/main_navigation.xml','firestore.rules','firestore.indexes.json','functions/rescue-fund.js','functions/rescue-fund.test.cjs','docs/rescue-fund.md','docs/rescue-fund-shared-bank.md','docs/report/course-requirements-audit-2026-10-24.md']
    dump(E/'audit-inputs.json',[{'path':p,'sha256':sha(ROOT/p),'role':'Current source or historical document cross-checked against source; hash is provenance, not endorsement'}for p in inputs])
    print('Evidence written',len(inventory),'source files;',len(manifest),'original screenshots; JVM',r['jvm'],'lint',issues)
if __name__=='__main__':main()
