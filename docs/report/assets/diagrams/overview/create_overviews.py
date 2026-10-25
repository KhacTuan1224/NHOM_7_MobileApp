"""Two report figures, editable SVG and high-resolution PNG. No app UI mockups."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import html, math, json

OUT=Path(__file__).resolve().parent
FONT='C:/Windows/Fonts/arial.ttf'; BOLD='C:/Windows/Fonts/arialbd.ttf'
INK='#19372d'; BODY='#42524c'; MUTED='#65756e'; GREEN='#2d704b'

class Canvas:
    def __init__(self,w,h):
        self.w=w;self.h=h;self.s=2
        self.im=Image.new('RGB',(w*2,h*2),'white');self.d=ImageDraw.Draw(self.im)
        self.svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="100%" height="100%" fill="white"/>']
    def rect(self,x,y,w,h,fill='white',stroke=None,r=18,sw=2):
        self.d.rounded_rectangle((x*2,y*2,(x+w)*2,(y+h)*2),radius=r*2,fill=fill,outline=stroke,width=sw*2)
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke or "none"}" stroke-width="{sw}"/>')
    def text(self,x,y,t,size=30,color=BODY,bold=False,center=False):
        f=ImageFont.truetype(BOLD if bold else FONT,size*2)
        anchor='mt' if center else 'lt'
        # Measure text before export; labels must stay within the canvas.
        width=self.d.textlength(t,font=f)/2
        left=x-width/2 if center else x
        assert left>=-1 and left+width<=self.w+1,(t,left,width,self.w)
        self.d.text((x*2,y*2),t,font=f,fill=color,anchor=anchor)
        self.svg.append(f'<text x="{x}" y="{y+size*.88}" font-family="Arial" font-size="{size}" font-weight="{700 if bold else 400}" text-anchor="{"middle" if center else "start"}" fill="{color}">{html.escape(t)}</text>')
    def path(self,points,color=GREEN,width=3,arrow=False,dash=False):
        self.d.line([(x*2,y*2) for x,y in points],fill=color,width=width*2,joint='curve')
        self.svg.append('<path d="'+' '.join(('M' if i==0 else 'L')+f'{x} {y}' for i,(x,y) in enumerate(points))+f'" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"'+(' stroke-dasharray="8 8"' if dash else '')+'/>')
        if arrow:
            (x0,y0),(x,y)=points[-2:];a=math.atan2(y-y0,x-x0)
            tri=[(x,y),(x-16*math.cos(a-.4),y-16*math.sin(a-.4)),(x-16*math.cos(a+.4),y-16*math.sin(a+.4))]
            self.d.polygon([(px*2,py*2) for px,py in tri],fill=color)
            self.svg.append('<polygon points="'+' '.join(f'{px},{py}' for px,py in tri)+f'" fill="{color}"/>')
    def curve(self,start,end,color=GREEN,width=4):
        x1,y1=start;x2,y2=end;xm=(x1+x2)/2
        pts=[]
        for i in range(61):
            t=i/60;u=1-t
            pts.append((u**3*x1+3*u*u*t*xm+3*u*t*t*xm+t**3*x2,u**3*y1+3*u*u*t*y1+3*u*t*t*y2+t**3*y2))
        self.d.line([(x*2,y*2) for x,y in pts],fill=color,width=width*2)
        self.svg.append(f'<path d="M{x1} {y1} C{xm} {y1} {xm} {y2} {x2} {y2}" fill="none" stroke="{color}" stroke-width="{width}"/>')
    def leaf(self,x,y,size=60,color=GREEN):
        pts=[]
        # An original leaf outline; no emoji or borrowed trademark.
        for i in range(61):
            t=i/60;u=1-t
            pts.append((u**3*(x-size*.4)+3*u*u*t*(x-size*.65)+3*u*t*t*(x+size*.5)+t**3*(x+size*.48),u**3*(y+size*.4)+3*u*u*t*(y-size*.6)+3*u*t*t*(y-size*.6)+t**3*(y-size*.5)))
        for i in range(61):
            t=i/60;u=1-t
            pts.append((u**3*(x+size*.48)+3*u*u*t*(x+size*.75)+3*u*t*t*(x+size*.5)+t**3*(x-size*.4),u**3*(y-size*.5)+3*u*u*t*(y+size*.5)+3*u*t*t*(y+size*.6)+t**3*(y+size*.4)))
        self.d.polygon([(a*2,b*2) for a,b in pts],fill=color)
        self.svg.append('<polygon points="'+' '.join(f'{a},{b}' for a,b in pts)+f'" fill="{color}"/>')
        self.path([(x-size*.36,y+size*.36),(x+size*.23,y-size*.24)],'white',3)
    def title(self,kicker,title,subtitle):
        self.text(70,38,kicker,24,GREEN,True)
        self.text(70,85,title,48,INK,True)
        self.text(70,153,subtitle,28,MUTED)
    def save(self,name):
        self.svg.append('</svg>');(OUT/(name+'.svg')).write_text('\n'.join(self.svg),encoding='utf-8')
        self.im.save(OUT/(name+'.png'),dpi=(300,300))
        preview=self.im.copy();preview.thumbnail((1500,1100));preview.save(OUT/(name+'-preview.jpg'),quality=94)

def mindmap():
    c=Canvas(2200,1440)
    c.title('RESCUEFARM  /  GIẢI PHÁP','Kết nối người bán và người mua','Sơ đồ tư duy về giải pháp thương mại điện tử giải cứu nông sản')
    nodes=[
      (70,260,'01','Người bán','#2d704b','#edf5ef',[
        'Đăng sản phẩm và quản lý từng lô',
        'Cập nhật giá, tồn kho và hạn sử dụng',
        'Tổ chức chiến dịch giải cứu']),
      (70,640,'02','Thông tin minh bạch','#a46b20','#fcf5e8',[
        'Ảnh, mô tả và nguồn gốc sản phẩm',
        'Địa điểm nhận và thời gian chiến dịch',
        'Phân biệt hàng còn bán và hàng đang giữ']),
      (70,1020,'03','Vận hành tin cậy','#526d95','#eff3f9',[
        'Admin duyệt Seller và nội dung',
        'Kiểm tra vai trò, quyền sở hữu dữ liệu',
        'Tiếp nhận báo cáo và xử lý vi phạm']),
      (1480,260,'04','Người mua','#2d704b','#edf5ef',[
        'Khám phá, tìm kiếm và lọc nông sản',
        'Chọn lô, số lượng và thêm vào giỏ',
        'Đặt hàng, theo dõi và đánh giá hợp lệ']),
      (1480,640,'05','Kết nối đúng nhu cầu','#2c7882','#edf6f7',[
        'Gợi ý nội dung theo bộ lọc và vị trí',
        'Bản đồ điểm cố định và điểm di động',
        'Chiến dịch gắn với nguồn hàng thực tế']),
      (1480,1020,'06','Hoàn tất giao dịch','#8a6657','#f7f1ee',[
        'Kiểm tra lại giá và tồn khi checkout',
        'QR qua SePay hoặc thanh toán COD',
        'Giao nhận và phản hồi sau mua'])]
    for x,y,n,title,color,fill,lines in nodes:
        end=(x+650,y+133) if x<1000 else (x,y+133)
        start=(900,780) if x<1000 else (1300,780)
        c.curve(start,end,color,5)
    for x,y,n,title,color,fill,lines in nodes:
        c.rect(x,y,650,266,fill,color,22,2)
        c.text(x+30,y+26,n,24,color,True)
        c.text(x+82,y+22,title,35,INK,True)
        for i,t in enumerate(lines):
            c.text(x+32,y+100+i*46,'•  '+t,28,BODY)
    c.rect(900,638,400,284,'#245d3d',None,40)
    c.leaf(1100,696,46,'#bbdf91')
    c.text(1100,749,'RescueFarm',43,'white',True,True)
    c.text(1100,814,'Sàn nông sản giải cứu',25,'#e6f2e5',False,True)
    c.text(1100,857,'Kết nối cung và cầu',25,'#e6f2e5',False,True)
    c.text(1100,1353,'Nguồn hàng cần tiêu thụ  →  Giao dịch có kiểm soát  →  Người mua phù hợp',30,INK,True,True)
    c.save('rescuefarm-giai-phap-ket-noi')

def tech():
    c=Canvas(2400,1620)
    c.title('RESCUEFARM  /  CÔNG NGHỆ','Tổng quan các công nghệ cốt lõi','Kiến trúc Android, dữ liệu Firebase, backend và dịch vụ hình ảnh')
    # Edges are drawn in reserved gutters, never over the content of a card.
    c.path([(690,430),(900,430)],'#2d704b',4,True)
    c.text(795,380,'SDK',26,GREEN,True,True)
    c.path([(690,325),(780,325),(780,218),(2040,218),(2040,270)],'#426e9b',4,True)
    c.text(1520,184,'HTTPS API',25,'#426e9b',True,True)
    c.path([(1720,530),(1500,530)],'#426e9b',4,True)
    c.text(1610,475,'Admin SDK',25,'#426e9b',True,True)
    c.path([(690,1030),(900,1030)],'#98703f',4,True)
    c.text(795,977,'DAO',26,'#98703f',True,True)
    c.path([(690,1158),(780,1158),(780,1325),(2040,1325),(2040,1200)],'#86629c',4,True)
    c.text(1400,1280,'Upload có chữ ký  /  tải ảnh qua CDN',27,'#86629c',True,True)
    c.path([(2040,820),(2040,700)],'#86629c',4,True)
    c.text(2040,749,'Kiểm tra quyền media',26,'#86629c',True,True)

    # Client card.
    c.rect(70,270,620,930,'#edf5ef','#2d704b',24)
    c.text(102,305,'ỨNG DỤNG ANDROID',26,GREEN,True)
    c.text(102,360,'Java và XML Views',39,INK,True)
    c.text(102,424,'AndroidX • Material Components',29,BODY)
    c.path([(102,485),(650,485)],'#c6d9cb',2)
    c.text(102,525,'Tổ chức mã',31,INK,True)
    for i,t in enumerate(['Fragment và ViewModel','MVVM • Repository • LiveData']):c.text(102,582+i*46,t,29,BODY)
    c.text(102,715,'Trải nghiệm trên mobile',31,INK,True)
    for i,t in enumerate(['Guest • Customer • Seller • Admin','Điều hướng và giao diện responsive','Glide tải và hiển thị ảnh']):c.text(102,772+i*46,t,29,BODY)
    c.text(102,974,'Công cụ phát triển',31,INK,True)
    for i,t in enumerate(['Android Studio • Gradle','Android SDK • Emulator']):c.text(102,1031+i*46,t,29,BODY)

    # Firebase's products remain grouped; no fictional Storage or Functions.
    c.rect(900,270,600,430,'#fff8e9','#b78026',24)
    c.text(934,304,'DỮ LIỆU VÀ DANH TÍNH',26,'#9d6b1e',True)
    c.text(934,359,'Firebase',41,INK,True)
    for i,(a,b) in enumerate([
        ('Authentication','Đăng ký, đăng nhập và UID'),
        ('Cloud Firestore','Collection, document và listener'),
        ('FCM và App Check','Thông báo và xác thực ứng dụng')]):
        c.text(934,429+i*82,a,29,INK,True);c.text(934,468+i*82,b,27,BODY)

    c.rect(1720,270,610,430,'#eff4fa','#426e9b',24)
    c.text(1754,304,'BACKEND TIN CẬY',26,'#426e9b',True)
    c.text(1754,359,'Node.js và Express',39,INK,True)
    c.text(1754,422,'Triển khai trên Render',31,'#426e9b',True)
    for i,t in enumerate(['HTTPS API và Firebase Admin SDK','Báo giá, checkout và giữ tồn kho','Thanh toán, Hũ và kiểm tra quyền','Transaction và chống xử lý trùng']):c.text(1754,481+i*45,t,28,BODY)

    c.rect(900,820,600,380,'#fbf5ec','#98703f',24)
    c.text(934,854,'DỮ LIỆU CỤC BỘ',26,'#98703f',True)
    c.text(934,909,'Room và SQLite',40,INK,True)
    for i,t in enumerate(['Cache một phần nội dung đã xem','Giỏ khách và dữ liệu theo phiên','WorkManager hỗ trợ đồng bộ','SharedPreferences lưu tùy chọn']):c.text(934,985+i*45,t,28,BODY)

    c.rect(1720,820,610,380,'#f5f0f9','#86629c',24)
    c.text(1754,854,'HÌNH ẢNH VÀ MEDIA',26,'#86629c',True)
    c.text(1754,909,'Cloudinary',40,INK,True)
    c.text(1754,976,'Cloudflare Worker ký upload',30,'#86629c',True)
    for i,t in enumerate(['Ảnh sản phẩm, chiến dịch, bình luận','Firestore lưu URL và metadata','Khóa ký được giữ ở phía server']):c.text(1754,1038+i*44,t,28,BODY)

    c.rect(70,1400,2260,153,'#f4f7f5',None,20)
    c.text(104,1425,'API VÀ THÀNH PHẦN HỖ TRỢ',25,GREEN,True)
    c.text(104,1471,'Google Credential Manager  •  GPS Fused Location Provider  •  Leaflet và OpenStreetMap  •  QR / webhook SePay',30,INK)
    c.text(104,1514,'Room hỗ trợ đọc cache; checkout và các nghiệp vụ kho / tiền vẫn cần kiểm tra online.',27,MUTED)
    c.save('rescuefarm-cong-nghe-cot-loi')

if __name__=='__main__':
    mindmap();tech()
    (OUT/'sources.json').write_text(json.dumps({
      'auditDate':'2026-10-25','commit':'0d0f0c72e1b93defcfb720af5b4ad4bc9f3b5ad1',
      'type':'Source-based explanatory figures; not screenshots or measured impact claims',
      'evidence':['app/build.gradle.kts','app/src/main/java/com/rescuefarm/data/local/database/RescueFarmDatabase.java','app/src/main/java/com/rescuefarm/data/repository/CartRepositoryFactory.java','app/src/main/assets/maps/rescue-map.html','functions/package.json','functions/server.js','functions/commerce.js','functions/campaign-comments.js','cloudinary-worker/src/index.js'],
      'output':{'mindmapPixels':[4400,2880],'technologiesPixels':[4800,3240],'formats':['PNG 300 dpi','editable SVG']}
    },ensure_ascii=False,indent=2),encoding='utf-8')
    print('Created two PNG figures and two editable SVGs.')
