"""Preserve the user's report text/figures in 5.1-5.2; replace only figures 5.3.X."""
from pathlib import Path
from copy import deepcopy
from io import BytesIO
import re, json, zipfile, hashlib
from lxml import html, etree
from docx import Document
from code_excerpts import EXCERPTS

PART=Path(__file__).resolve().parents[1]
REPO=PART.parents[2]
INPUT=Path(r'C:\Users\THANH DAT\Downloads\MOBILE FINAL PROJECT.zip')
ASSETS=PART/'assets/original-report'
SOURCE=PART/'source'

def clean(text):return re.sub(r'\s+',' ',text.replace('\xa0',' ')).strip()

EXTRA={
 'public-query':dict(title='Truy vấn sản phẩm công khai có giới hạn trang',
 source='data/remote/firebase/FirebaseProductRepository.java, publicQuery(), dòng 70–73',
 code='''return firestore.collection(PRODUCTS)
    .whereEqualTo("status", ProductStatus.ACTIVE.name())
    .orderBy(com.google.firebase.firestore.FieldPath.documentId())
    .limit(publicLimit + 1L);''',
 explanation='Repository chỉ lấy sản phẩm ACTIVE và sắp theo document ID. Query lấy nhiều hơn giới hạn hiển thị một document để xác định còn dữ liệu cho lần tải tiếp theo; kết quả được ánh xạ về model rồi cung cấp cho ViewModel.'),
 'account-profile':dict(title='Đăng nhập Firebase và đọc hồ sơ ứng dụng',
 source='data/remote/firebase/FirebaseAuthRepository.java, signIn(), dòng 61–65',
 code='''firebaseAuth.signInWithEmailAndPassword(email, password)
    .addOnSuccessListener(result ->
        loadRequiredProfile(result.getUser(), callback))
    .addOnFailureListener(exception -> notifyFailure(exception, callback));''',
 explanation='Firebase Authentication kiểm tra thông tin đăng nhập và trả FirebaseUser khi thành công. Repository tiếp tục đọc hồ sơ ứng dụng để xác định vai trò và trạng thái; nếu xác thực thất bại, lỗi được chuyển qua callback thay vì coi tài khoản đã đăng nhập hợp lệ.'),
 'rules':dict(title='Firestore Rules kiểm soát quyền đọc sản phẩm',
 source='firestore.rules, match /products/{productId}, từ dòng 707',
 code='''match /products/{productId} {
  allow get, list: if resource.data.status == 'ACTIVE'
      || (isSignedIn() && resource.data.sellerId == request.auth.uid)
      || isAdmin();
  ...
}''',
 explanation='Quy tắc cho phép đọc sản phẩm đang ACTIVE, sản phẩm thuộc tài khoản Seller đăng nhập hoặc dữ liệu được Admin phép truy cập. Các điều kiện tạo và cập nhật nằm ở phần mã được lược; Rules này áp dụng cho đường truy cập SDK client, còn API dùng Admin SDK phải tự kiểm tra quyền.'),
 'discovery-filter':dict(title='Lọc trạng thái, danh mục và giá trên dữ liệu đã tải',
 source='ui/home/DiscoveryEngine.java, matchesProductTextAndPrice(), từ dòng 96',
 code='''if (!value.isAvailable()) return false;
if (!query.getCategoryId().isEmpty()
        && !query.getCategoryId().equals(value.getCategoryId())) return false;
if (value.getRescuePrice() < query.getMinPrice()
        || value.getRescuePrice() > query.getMaxPrice()) return false;
...''',
 explanation='DiscoveryEngine loại sản phẩm không khả dụng hoặc không thỏa danh mục và khoảng giá trước khi kiểm tra từ khóa. Các điều kiện dùng query và danh sách model đã có trong bộ nhớ; thay đổi bộ lọc không tự chứng minh ứng dụng đã tìm trên toàn bộ Firestore.'),
 'batch-sellable':dict(title='Kiểm tra lô đủ điều kiện bán',
 source='domain/model/ProductBatch.java, isSellable(), dòng 80–83',
 code='''public boolean isSellable(Date now) {
    return now != null && expiryDate != null && !isExpired(now)
        && availableQuantity > QUANTITY_TOLERANCE
        && (status == BatchStatus.AVAILABLE
            || status == BatchStatus.RESERVED_PARTIAL);
}''',
 explanation='Model chỉ coi lô có thể bán khi còn hạn, còn lượng khả dụng và trạng thái cho phép. Phép kiểm tra giúp giao diện chọn đúng lô; khi tạo đơn, backend vẫn phải kiểm tra lại lô trong transaction vì tồn và điều kiện bán có thể đã thay đổi.'),
 'map-sse':dict(title='Backend phát cập nhật bản đồ qua SSE',
 source='functions/rescue-map.js, createRescueMapApi(), từ dòng 84',
 code='''const send = value => {
  ...
  if (!res.write('data: ' + JSON.stringify(value) + '\\n\\n')
      || !value.live) finish();
};
release = hub.subscribe(send);
res.status(200).set({
  'Content-Type': 'text/event-stream; charset=utf-8',
  ...
});''',
 explanation='Kết nối người xem đăng ký với hub dùng chung; mỗi bản cập nhật được ghi ra response theo định dạng SSE. Content-Type giữ đường truyền sự kiện cho bản đồ, còn việc lấy GPS thực hiện ở thiết bị Seller; stream chỉ truyền những điểm mà backend đã lọc đủ điều kiện công khai.'),
}

FIGURE_CODES={
 '5.3.1':'home-search', '5.3.2':'public-query', '5.3.3':'discovery-filter',
 '5.3.4':'batch-sellable', '5.3.5':'commerce', '5.3.6':'checkout',
 '5.3.7':'campaign-map', '5.3.8':'map-sse', '5.3.9':'comments',
 '5.3.10':'seller-admin', '5.3.11':'rescue-fund',
}

def code_nodes(key,number):
 item={**EXCERPTS,**EXTRA}[key]
 block=html.Element('div',attrib={'class':'source-excerpt','data-code-number':number})
 title=etree.SubElement(block,'p',style='font-family:Times New Roman;font-size:11pt;font-weight:bold;margin-top:8pt;margin-bottom:3pt;page-break-after:avoid')
 title.text=f'Mã nguồn {number}. {item["title"]}'
 pre=etree.SubElement(block,'pre',style='font-family:Consolas;font-size:9pt;line-height:110%;background:#f3f5f6;padding:6pt;white-space:pre-wrap;page-break-inside:avoid;margin-top:0pt;margin-bottom:3pt')
 pre.text=item['code']
 source=etree.SubElement(block,'p',style='font-family:Times New Roman;font-size:9pt;font-style:italic;margin-top:0pt;margin-bottom:3pt;page-break-after:avoid')
 source.text='Nguồn: '+item['source']+'.'
 explanation=etree.SubElement(block,'p',attrib={'class':'code-explanation'},style='font-family:Times New Roman;font-size:12pt;line-height:115%;text-align:justify;margin-top:0pt;margin-bottom:8pt')
 explanation.text=item['explanation']
 return block

def prepare():
 ASSETS.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(INPUT) as z:
  original=html.fromstring(z.read('MOBILEFINALPROJECT.html'))
  body=original.find('body');nodes=list(body)
  start=next(i for i,n in enumerate(nodes) if n.tag=='h1' and clean(n.text_content()).startswith('CHƯƠNG 5.'))
  end=next(i for i,n in enumerate(nodes[start+1:],start+1) if n.tag=='h1' and clean(n.text_content()).startswith('CHƯƠNG 6.'))
  baseline=nodes[start:end]
  doc=html.Element('html');doc.set('lang','vi')
  head=deepcopy(original.find('head'))
  for st in head.xpath('.//style'):
   st.text=re.sub(r'@import[^;]+;','',st.text or '')
  etree.SubElement(head,'meta',charset='utf-8')
  page=etree.SubElement(head,'style');page.text='@page Section1 {size:595.3pt 841.9pt;margin:51pt 42.5pt 51pt 56.7pt;} div.Section1 {page:Section1;}'
  doc.append(head);newbody=etree.SubElement(doc,'body');section=etree.SubElement(newbody,'div',attrib={'class':'Section1'})
  part='5.1';firebase_index=0;kept_text=[];removed=[];restored_images=[]
  for original_node in baseline:
   node=deepcopy(original_node);t=clean(node.text_content());images=node.xpath('.//img')
   if node.tag=='h2' and t.startswith('5.2.'):part='5.2'
   if node.tag=='h2' and t.startswith('5.3.'):part='5.3'
   match=re.match(r'^Hình\s+(5\.3\.\d+)\b',t)
   if part=='5.3' and images and not t:
    removed.extend(im.get('src') for im in images);continue
   if part=='5.3' and match:
    section.append(code_nodes(FIGURE_CODES[match.group(1)],match.group(1)));continue
   section.append(node)
   if t:kept_text.append(t)
   if part in ('5.1','5.2'):
    restored_images.extend(im.get('src') for im in images)
   if part=='5.2' and re.match(r'^Hình\s+5\.2\.\d+',t):
    keys=['firebase-connection','public-query','account-profile','rules']
    section.append(code_nodes(keys[firebase_index],f'5.2.{firebase_index+1}'));firebase_index+=1
  assert firebase_index==4
  assert len(section.xpath('.//div[@class="source-excerpt"]'))==15
  assert len(section.xpath('.//p[@class="code-explanation"]'))==15
  # Keep all original image bytes; mathematical inline images in 5.3 stay in place.
  images=[]
  for img in section.xpath('.//img'):
   rel=img.get('src');assert re.fullmatch(r'images/[A-Za-z0-9_.-]+',rel),rel
   dest=ASSETS/Path(rel).name;data=z.read(rel);dest.write_bytes(data)
   img.set('src','../assets/original-report/'+dest.name)
   images.append({'original':rel,'file':str(dest.relative_to(REPO)),'sha256':hashlib.sha256(data).hexdigest()})
  source_html=SOURCE/'chapter5-preserved.html'
  source_html.write_text('<!DOCTYPE html>\n'+html.tostring(doc,encoding='unicode',method='html'),encoding='utf-8')
  # Preserve a Markdown version with original text/tables, figure placement and new excerpts.
  md=[];code_catalog=[]
  for node in section:
   if node.get('class')=='source-excerpt':
    number=node.get('data-code-number');key=FIGURE_CODES.get(number)
    if not key:key=['firebase-connection','public-query','account-profile','rules'][int(number.split('.')[-1])-1]
    item={**EXCERPTS,**EXTRA}[key];lang='javascript' if item['source'].startswith('functions/') else 'java'
    if item['source'].startswith('firestore.rules'):lang='text'
    block=[f'**Mã nguồn {number}. {item["title"]}**',f'```{lang}\n{item["code"]}\n```','Nguồn: '+item['source']+'.',item['explanation']]
    md.extend(block);code_catalog.extend(block);continue
   t=clean(node.text_content())
   if node.tag in ('h1','h2','h3') and t:md.append('#'*int(node.tag[1])+' '+t)
   elif node.tag=='table':
    rows=[[clean(c.text_content()).replace('|','\\|') for c in row.xpath('./td|./th')] for row in node.xpath('.//tr')]
    if rows:
     md.append('| '+' | '.join(rows[0])+' |\n| '+' | '.join(['---']*len(rows[0]))+' |\n'+'\n'.join('| '+' | '.join(r)+' |' for r in rows[1:]))
   else:
    if t:md.append(t)
    for img in node.xpath('.//img'):md.append('![Hình gốc trong báo cáo]('+img.get('src')+')')
  markdown='\n\n'.join(md)+'\n';(SOURCE/'chapter5-preserved.md').write_text(markdown,encoding='utf-8')
  (SOURCE/'code-excerpts.md').write_text('# Trích đoạn code bổ sung ở 5.2 và thay hình 5.3.X\n\nMục 5.1 giữ nguyên và không thêm code. Mục 5.2 giữ đủ ảnh, thêm code bên dưới; mục 5.3 giữ nguyên phần chữ và bảng. Mỗi phần giải thích là một đoạn.\n\n'+'\n\n'.join(code_catalog)+'\n',encoding='utf-8')
  report_md=REPO/'docs/report/source/report-content.md';report=report_md.read_text(encoding='utf-8')
  a=report.index('# CHƯƠNG 5');b=report.index('# CHƯƠNG 6',a)
  embedded=markdown.replace('../assets/original-report/','../part5/assets/original-report/')
  report_md.write_text(report[:a]+embedded+'\n'+report[b:],encoding='utf-8')
  record={'source_zip':str(INPUT),'source_pdf':r'C:\Users\THANH DAT\Downloads\MOBILE FINAL PROJECT (1).pdf',
          'preserved_original_text':kept_text,'restored_images_5_1_5_2':restored_images,
          'removed_5_3_image_files':removed,'replaced_figure_numbers':list(FIGURE_CODES),
          'code_blocks':15,'explanation_paragraphs':15,'original_image_hashes':images,
          'rule':'5.1 unchanged; 5.2 original text and images plus code underneath; 5.3 original text/tables, replace only numbered figure slots; keep mathematical inline images.'}
  (SOURCE/'preservation-checks.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
 print('Prepared chapter: 5 original figures retained in 5.1/5.2; 11 figure slots replaced; 15 one-paragraph explanations.')
 return source_html

def copy_chapter(target, include_title=True):
 """Copy editable OOXML and original image relationships into a report document."""
 source=Document(PART/'Phan-5-Xay-dung-ung-dung.docx')
 existing={s.style_id for s in target.styles}
 for style in source.styles:
  if style.style_id not in existing:target.styles.element.append(deepcopy(style.element))
 relation_map={}
 for rid,rel in source.part.rels.items():
  if rel.is_external:relation_map[rid]=target.part.relate_to(rel.target_ref,rel.reltype,is_external=True)
  elif rel.reltype.endswith('/image'):
   new_id,_=target.part.get_or_add_image(BytesIO(rel.target_part.blob));relation_map[rid]=new_id
 count=0
 for el in source.element.body:
  if el.tag.endswith('}sectPr'):continue
  clone=deepcopy(el)
  text=''.join(n.text or '' for n in clone.iter() if n.tag.endswith('}t'))
  if not include_title and text.startswith('CHƯƠNG 5.'):continue
  for n in clone.iter():
   for key,value in list(n.attrib.items()):
    if key.startswith('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}') and value in relation_map:n.set(key,relation_map[value])
  for sect in list(clone.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}sectPr')):sect.getparent().remove(sect)
  target.element.body.insert(len(target.element.body)-1,clone);count+=1
 return count

def sync_full_report():
 path=REPO/'docs/report/report.docx';doc=Document(path);children=list(doc.element.body)
 def heading(el,num):
  return el.xpath('./w:pPr/w:pStyle/@w:val')==['Heading1'] and ''.join(n.text or '' for n in el.iter() if n.tag.endswith('}t')).startswith(f'CHƯƠNG {num}')
 a=next(i for i,e in enumerate(children) if heading(e,5));b=next(i for i,e in enumerate(children[a+1:],a+1) if heading(e,6))
 outside_before=[e.xml for e in children[:a]];outside_after=[e.xml for e in children[b:]]
 # Use target's relationship IDs: append first, then move only the appended elements.
 before_ids={id(el) for el in doc.element.body};length=len(doc.element.body)
 count=copy_chapter(doc);added=list(doc.element.body)[length-1:length-1+count]
 anchor=children[a]
 for el in added:anchor.addprevious(el)
 for el in children[a:b]:doc.element.body.remove(el)
 now=list(doc.element.body)
 assert [e.xml for e in now[:a]]==outside_before
 assert [e.xml for e in now[a+count:]]==outside_after
 doc.save(path)
 print('Synchronized full-report chapter 5; other chapter body elements unchanged.')

if __name__=='__main__':
 import sys
 if '--sync-report' in sys.argv:sync_full_report()
 else:prepare()
