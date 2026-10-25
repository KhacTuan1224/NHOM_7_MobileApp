from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from pypdf import PdfReader
from lxml import etree
import json,zipfile,re
R=Path(__file__).resolve().parents[1];OUT=R/'evidence/render-verified';OUT.mkdir(parents=True,exist_ok=True)
reader=PdfReader(R/'report.pdf');pages=[]
for i,p in enumerate(reader.pages):
    text=p.extract_text();pages.append(dict(page=i+1,width=float(p.mediabox.width),height=float(p.mediabox.height),characters=len(text),text=text))
assert 55<=len(pages)<=65
full='\n'.join(p['text'] for p in pages)
assert not any(x in full for x in ['Error! Reference','Error! Bookmark','Error! No table','Mục lục sẽ được cập nhật'])
with zipfile.ZipFile(R/'report.docx') as z:
    root=etree.fromstring(z.read('word/document.xml'))
    ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main','a':'http://schemas.openxmlformats.org/drawingml/2006/main','r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
    embeds=root.xpath('//a:blip/@r:embed',namespaces=ns);fields=root.xpath('//w:instrText/text()',namespaces=ns)
    external=[]
    for name in z.namelist():
        if name.endswith('.rels'):
            external += [x.get('Target') for x in etree.fromstring(z.read(name)) if x.get('TargetMode')=='External']
    assert not any(re.match(r'^(file:|[A-Z]:|\\)',x) for x in external)
    stats=dict(pdfPages=len(pages),landscapePages=[p['page']for p in pages if p['width']>p['height']],imageInstances=len(embeds),uniqueEmbeddedImages=len(set(embeds)),tocFields=sum('TOC 'in f for f in fields),figureSequenceFields=sum('SEQ Figure'in f for f in fields),tableSequenceFields=sum('SEQ Table'in f for f in fields),crossReferenceFields=sum('REF 'in f for f in fields),externalRelationships=external)
assert stats['imageInstances']==30
stats['pagesWithShortText']=[p['page']for p in pages if p['characters']<500]
stats['shortTextExplanation']='Landscape diagrams contain embedded graphics; review portrait short-text pages visually.'
original=PdfReader(R.parents[1]/'docs/MOBILE FINAL PROJECT.pdf')
normalize=lambda t:re.sub(r'\s+',' ',t).strip()
chapter=normalize(' '.join(original.pages[i].extract_text() for i in [3,4,5])).replace('CHƯƠNG 1. TỔNG QUAN ','')
from docx import Document
stats['chapter1Preserved']=chapter in normalize(' '.join(p.text for p in Document(R/'report.docx').paragraphs))
assert stats['chapter1Preserved']
for number in range(0,len(pages),12):
    subset=pages[number:number+12];sheet=Image.new('RGB',(1500,2240),'#d8dce0');d=ImageDraw.Draw(sheet);f=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
    for j,p in enumerate(subset):
        file=OUT/f"page-{p['page']:02}.png"
        if not file.exists():continue
        im=Image.open(file).convert('RGB');im.thumbnail((475,515));x=(j%3)*500+(500-im.width)//2;y=(j//3)*560+30
        sheet.paste(im,(x,y));d.text(((j%3)*500+15,(j//3)*560+5),f"Page {p['page']}",font=f,fill='black')
    sheet.save(OUT/f'contact-{number//12+1}.jpg',quality=92)
(R/'evidence/pdf-pages.json').write_text(json.dumps(pages,ensure_ascii=False,indent=2),encoding='utf-8')
(R/'evidence/document-qa.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(stats,ensure_ascii=False))
