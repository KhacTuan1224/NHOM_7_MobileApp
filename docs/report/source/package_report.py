from pathlib import Path
import zipfile,json,hashlib
R=Path(__file__).resolve().parents[1];ROOT=R.parents[1]
files=[]
for n in ['report.docx','report.pdf','audit-matrix.md','demo-5-minutes.md','defense-questions.md','handoff.md']:
    files.append(R/n)
for folder in ['source','assets/diagrams','assets/screenshots/originals']:
    for p in (R/folder).rglob('*'):
        if not p.is_file() or '__pycache__' in p.parts:continue
        if p.name.startswith('template-X') and p.name!='template-X10.png':continue
        files.append(p)
for p in (R/'evidence').iterdir():
    if not p.is_file():continue
    if p.name in ['course-audit-results.json','course-emulator-final.log','delivery-files.json']:continue
    if p.suffix in ['.json','.log','.txt','.patch','.md']:files.append(p)
files+=list((R/'evidence/render-verified').glob('contact-*.jpg'))
for n in ['app/build.gradle.kts','firebase.report-demo.json','docs/MOBILE FINAL PROJECT.pdf']:
    files.append(ROOT/n)
files+=list(p for p in (ROOT/'app/src/reportDemo').rglob('*') if p.is_file())
files=sorted(set(files))
manifest=[dict(path=p.relative_to(ROOT).as_posix(),size=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())for p in files]
(R/'evidence/delivery-files.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
files.append(R/'evidence/delivery-files.json')
target=R/'RescueFarm-report-bundle.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=7)as z:
    for p in files:z.write(p,p.relative_to(ROOT).as_posix())
with zipfile.ZipFile(target)as z:
    assert z.testzip() is None
    assert not any('report-private' in n or 'node_modules' in n or n.endswith('.apk') for n in z.namelist())
print('BUNDLE',len(files),'files;',round(target.stat().st_size/1048576,2),'MiB')
