"""Editable diagram specification -> SVG and high-resolution PNG, no UI mockups."""
from pathlib import Path
import html, math, json
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'assets/diagrams'; OUT.mkdir(parents=True,exist_ok=True)
FONT='C:/Windows/Fonts/arial.ttf'; BOLD='C:/Windows/Fonts/arialbd.ttf'
def diagram(name,title,nodes,edges,groups=(),size=(1400,1000),note=''):
    scale=2; im=Image.new('RGB',(size[0]*scale,size[1]*scale),'white'); draw=ImageDraw.Draw(im)
    svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{size[0]}" height="{size[1]}" viewBox="0 0 {size[0]} {size[1]}"><rect width="100%" height="100%" fill="white"/><defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8" fill="none" stroke="#333"/></marker></defs>']
    def text(x,y,t,sz=23,bold=False,center=True):
        f=ImageFont.truetype(BOLD if bold else FONT,sz*scale)
        for i,line in enumerate(t.split('\n')):
            yy=y+i*(sz+8);draw.text((x*scale,yy*scale),line,font=f,fill='#172b31',anchor='mt' if center else 'lt')
            svg.append(f'<text x="{x}" y="{yy+sz}" font-family="Arial" font-size="{sz}" font-weight="{"bold" if bold else "normal"}" text-anchor="{"middle" if center else "start"}" fill="#172b31">{html.escape(line)}</text>')
    text(size[0]/2,18,title,30,True)
    for x,y,w,h,label in groups:
        draw.rounded_rectangle((x*scale,y*scale,(x+w)*scale,(y+h)*scale),radius=12*scale,outline='#59747b',width=2*scale)
        svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="none" stroke="#59747b" stroke-width="2"/>');text(x+15,y+10,label,21,True,False)
    for a,b,label,*extra in edges:
        na,nb=nodes[a],nodes[b];x1,y1,w1,h1=na[:4];x2,y2,w2,h2=nb[:4]
        c1=(x1+w1/2,y1+h1/2);c2=(x2+w2/2,y2+h2/2);dx=c2[0]-c1[0];dy=c2[1]-c1[1]
        r1=min((w1/2)/max(abs(dx),.01),(h1/2)/max(abs(dy),.01));r2=min((w2/2)/max(abs(dx),.01),(h2/2)/max(abs(dy),.01))
        p1=(c1[0]+dx*r1,c1[1]+dy*r1);p2=(c2[0]-dx*r2,c2[1]-dy*r2)
        route=extra[0] if extra and isinstance(extra[0],dict) else {}
        if route.get('points'):points=route['points'];p1=points[0];p2=points[-1]
        else:
            if na[-1]=='actor':p1=(x1+w1-15,y1+42)
            points=[p1,p2]
        arrow=not(extra and extra[0]=='association' or route.get('association'))
        draw.line([(x*scale,y*scale) for x,y in points],fill='#333333',width=2*scale)
        if arrow:
            angle=math.atan2(p2[1]-points[-2][1],p2[0]-points[-2][0]);q=[p2,(p2[0]-13*math.cos(angle-.4),p2[1]-13*math.sin(angle-.4)),(p2[0]-13*math.cos(angle+.4),p2[1]-13*math.sin(angle+.4))]
            draw.polygon([(int(x*scale),int(y*scale)) for x,y in q],fill='#333333')
        svg.append('<path d="'+' '.join(('M' if i==0 else 'L')+str(x)+' '+str(y) for i,(x,y) in enumerate(points))+'" stroke="#333" stroke-width="2" fill="none"'+(' marker-end="url(#arrow)"' if arrow else '')+'/>')
        if label:
            mx,my=route.get('labelAt',((p1[0]+p2[0])/2,(p1[1]+p2[1])/2-22))
            width=max(map(len,label.split('\n')))*12+12;height=len(label.split('\n'))*29
            draw.rectangle(((mx-width/2)*scale,my*scale,(mx+width/2)*scale,(my+height)*scale),fill='white')
            svg.append(f'<rect x="{mx-width/2}" y="{my}" width="{width}" height="{height}" fill="white"/>');text(mx,my,label,20)
    for key,node in nodes.items():
        x,y,w,h,label,*kind=node;kind=kind[0] if kind else 'box'
        if kind=='actor':
            draw.ellipse(((x+w/2-12)*scale,y*scale,(x+w/2+12)*scale,(y+24)*scale),outline='#172b31',width=2*scale)
            for line in [(x+w/2,y+24,x+w/2,y+65),(x+15,y+42,x+w-15,y+42),(x+w/2,y+65,x+20,y+87),(x+w/2,y+65,x+w-20,y+87)]:draw.line(tuple(v*scale for v in line),fill='#172b31',width=2*scale)
            svg.append(f'<circle cx="{x+w/2}" cy="{y+12}" r="12" fill="white" stroke="#172b31"/><path d="M{x+w/2} {y+24}v41 M{x+15} {y+42}h{w-30} M{x+w/2} {y+65}l{-w/2+20} 22 M{x+w/2} {y+65}l{w/2-20} 22" fill="none" stroke="#172b31" stroke-width="2"/>');text(x+w/2,y+90,label,22,True);continue
        fill='#eef4f4' if kind!='snapshot' else '#f4f1e9'
        if kind=='usecase':
            draw.ellipse((x*scale,y*scale,(x+w)*scale,(y+h)*scale),fill=fill,outline='#345961',width=2*scale)
            svg.append(f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}" fill="{fill}" stroke="#345961" stroke-width="2"/>')
        else:
            draw.rounded_rectangle((x*scale,y*scale,(x+w)*scale,(y+h)*scale),radius=9*scale,fill=fill,outline='#345961',width=2*scale)
            svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="9" fill="{fill}" stroke="#345961" stroke-width="2"/>')
        lines=label.split('\n');text(x+w/2,y+(h-len(lines)*31)/2,label,23,False)
    if note:text(size[0]/2,size[1]-70,note,20)
    svg.append('</svg>');(OUT/(name+'.svg')).write_text('\n'.join(svg),encoding='utf-8');im.save(OUT/(name+'.png'))
    (OUT/(name+'.json')).write_text(json.dumps(dict(title=title,nodes=nodes,edges=edges,groups=groups,size=size,note=note),ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    n={};e=[]
    rows=[('Guest',['Khám phá và tìm kiếm','Giỏ cục bộ và checkout','Xem và ủng hộ Hũ']),('Customer',['Đơn hàng và nhận hàng','Đánh giá khi đủ điều kiện','Yêu thích và tương tác']),('Seller',['Sản phẩm và lô hàng','Chiến dịch và gói giải cứu','Đơn và chuyến thiện nguyện']),('Admin',['Duyệt Seller và nội dung','Đối soát và cấu hình','Xác minh giao và ghi chi'])]
    for i,(actor,ucs) in enumerate(rows):
        y=120+i*205;n['a'+str(i)]=(35,y,130,125,actor,'actor')
        for j,uc in enumerate(ucs):
            k=f'u{i}{j}';n[k]=(285+j*345,y+10,300,100,uc.replace(' và ',' và\n'),'usecase')
            if j==0:e.append(('a'+str(i),k,'','association'))
            else:
                gutter=215 if j==1 else 190;bottom=y+145 if j==1 else y+175;center=435+j*345
                e.append(('a'+str(i),k,'',{'association':True,'points':[(150,y+42),(gutter,y+42),(gutter,bottom),(center,bottom),(center,y+110)]}))
    diagram('use-cases','Use case tổng thể RescueFarm',n,e,[(235,80,1110,850,'RescueFarm')],size=(1400,1040),note='Customer cũng dùng các use case công khai của Guest. Quyền ghi do Rules và API kiểm tra.\nLiên kết là association; không dùng include hoặc extend khi không cần thiết.')
    n={'ui':(65,150,285,110,'Fragment và XML Views\nMainActivity / Navigation'),'vm':(65,350,285,100,'ViewModel và LiveData\ntrạng thái theo vòng đời'),'repo':(460,350,300,110,'Repository và policy\nkiểm tra nguồn / quyền'),'room':(460,650,300,110,'Room v11 trên SQLite\ncache đọc và giỏ Guest'),'worker':(65,650,285,110,'WorkManager\nread sync và retry'),'fire':(910,150,360,110,'Firebase Auth + Firestore\nRules cho Android SDK'),'http':(910,350,360,110,'Express trên Render\nAdmin SDK / transaction'),'media':(910,560,360,110,'Cloudflare Worker\nCloudinary / ký media'),'bank':(910,760,360,110,'SePay webhook → backend\nFCM qua outbox')}
    n['bank']=(910,760,170,110,'SePay\nwebhook');n['fcm']=(1100,760,170,110,'FCM\npush')
    e=[('ui','vm',''),('vm','repo',''),('repo','room','đọc cache'),('worker','room',''),('worker','repo','đồng bộ'),('repo','fire','SDK'),('repo','http','HTTP online'),
       ('ui','media','upload\nonline',{'points':[(350,205),(815,205),(815,615),(910,615)],'labelAt':(815,520)}),
       ('bank','http','SePay vào',{'points':[(910,815),(870,815),(870,405),(910,405)],'labelAt':(860,705)}),
       ('http','fcm','outbox',{'points':[(1270,405),(1320,405),(1320,815),(1270,815)],'labelAt':(1320,690)}),
       ('media','http',''),('http','fire','Admin SDK')]
    diagram('architecture','Kiến trúc và ranh giới dữ liệu',n,e,[(30,80,770,770,'Android client'),(855,80,510,820,'Dịch vụ bên ngoài thiết bị')],size=(1400,1030),note='Local: danh mục / sản phẩm / lô / chiến dịch / đơn đã cache.\nOnline bắt buộc: checkout, thanh toán, duyệt, cập nhật tồn và chuyển trạng thái.')
    n={'users':(45,140,280,110,'users/{uid}\nrole / status / sellerStatus'),'prod':(430,140,280,110,'products/{productId}\nsellerId / categoryId'),'batch':(825,140,320,110,'productBatches/{batchId}\nexpiryDate / stock counters'),'campaign':(825,400,320,120,'campaigns/{campaignId}\nbatchTargets\nreservedByBatch / rescuedByBatch'),'orders':(430,400,280,120,'orders/{orderId}\nowner / seller / totals\nitems/{batchId} snapshot','snapshot'),'payments':(45,400,280,120,'payments / shipments\norderId + snapshot','snapshot'),'fund':(825,700,320,120,'rescueFundDeliveries\nbatchId / sellerId\ndeadline + snapshot','snapshot'),'ledger':(430,700,280,120,'rescueFunds / Donations\nLedger / References\nRuns / PayoutReferences'),'room':(45,700,280,120,'SQLite cache\n9 Room tables\nkhông phải dữ liệu chuẩn')}
    n['campaign']=(825,400,320,150,'campaigns/{campaignId}\nbatchTargets\nreservedByBatch\nrescuedByBatch')
    e=[('users','prod','1 : n'),('prod','batch','1 : n'),('batch','campaign','tham chiếu lô'),('batch','orders','giữ / chốt kho'),('orders','payments','1 : 1'),('users','orders','owner / Seller'),('batch','fund','giữ / chốt kho',{'points':[(1145,195),(1170,195),(1170,760),(1145,760)],'labelAt':(1085,590)}),('fund','ledger','ledger + số dư',{'points':[(825,760),(790,760),(790,620),(570,620),(570,700)],'labelAt':(675,590)}),('orders','room','cache theo user')]
    diagram('data-model','Mô hình collection và snapshot giao dịch',n,e,size=(1200,1000),note='Mũi tên biểu diễn tham chiếu logic do code kiểm tra, không phải khóa ngoại Firestore.\nSnapshot màu nhạt lưu dữ liệu tại thời điểm giao dịch; Room là bản sao có thể cũ.')
    n={'pick':(70,130,340,100,'Chọn ProductBatch\ncòn hạn và đủ tồn'),'cart':(480,130,340,100,'Giỏ Guest / Customer\nsố lượng + giá tham khảo'),'quote':(890,130,340,100,'Backend quote / checkout\nkiểm tra lại giá, quyền, tồn'),'fail':(890,350,340,100,'Sai giá / hết tồn / hết hạn\ntrả lỗi để người mua sửa'),'reserve':(480,350,340,100,'Transaction giữ kho\nrequestId chống lặp'),'pay':(70,350,340,100,'BANK_TRANSFER → QR\nhoặc COD / tiền tại điểm'),'hook':(70,590,340,110,'Webhook hợp lệ\nkiểm mã, tiền, ngân hàng\nchống lặp giao dịch'),'deliver':(480,590,340,110,'Seller xử lý và giao nhận\nkiểm tra transition hợp lệ'),'end':(890,590,340,110,'Hoàn tất → chốt kho\nHủy / quá hạn → hoàn giữ\ngiữ lịch sử đơn')}
    e=[('pick','cart',''),('cart','quote',''),('quote','fail','không đạt'),('quote','reserve','hợp lệ'),('reserve','pay',''),('pay','hook','QR'),('pay','deliver','COD'),('hook','deliver',''),('deliver','end','')]
    diagram('order-flow','Hoạt động đặt hàng và giữ tồn kho',n,e,size=(1320,860),note='Client không tự đánh dấu đã nhận tiền. Webhook retry không tạo thêm khoản thu.\nĐơn giao nhận và trạng thái thanh toán là hai chiều trạng thái cần đối chiếu.')
    n={'don':(60,120,330,110,'Guest / Customer / Seller\nQR và mã riêng\nPENDING donation'),'hook':(480,120,330,110,'SePay hợp lệ → PAID\ncộng available một lần'),'select':(900,120,330,110,'Chọn lô tại thời điểm t\n> t + 24h + 1 phút\n≤ t + 48h'),'reserve':(900,350,330,115,'RESERVED trong transaction\ngiữ cả tiền và hàng\ndeadline = t + 24h'),'accept':(480,350,330,115,'Seller ACCEPTED\nkhông đặt lại deadline'),'proof':(60,350,330,115,'DELIVERED trước hạn\nảnh gửi lên kịp thời'),'verify':(60,610,330,115,'Admin VERIFIED sau kiểm tra\nchốt kho; tiền còn reserved\ncó thể duyệt sau deadline'),'paid':(480,610,330,115,'Chuyển tiền ngoài app\nPAY lưu mã + ảnh + số tiền\nreserved → spent'),'cancel':(900,610,330,115,'Chưa có ảnh khi quá hạn\nRESERVED / ACCEPTED\nCANCELLED và giải phóng')}
    e=[('don','hook',''),('hook','select',''),('select','reserve','đủ điều kiện'),('reserve','accept',''),('accept','proof',''),('proof','verify','ảnh đúng hạn'),('verify','paid',''),('reserve','cancel','quá hạn'),('accept','cancel','quá hạn')]
    diagram('fund-flow','Luồng tổng hợp Hũ giải cứu chính sách phiên bản 2',n,e,size=(1320,900),note='DELIVERED đúng hạn không bị worker hủy vì Admin duyệt muộn.\nGiải phóng tiền giữ về available không phải hoàn tiền ngân hàng cho người ủng hộ.')
    n={'root':(460,100,360,100,'RescueFarm\nthương mại điện tử giải cứu'),'browse':(45,340,285,130,'Khám phá và nội dung\nHome / tìm kiếm / Campaign\ncomment / reaction / report'),'trade':(375,340,285,130,'Thương mại\nProduct / Batch / Cart\nCheckout / Order / Shipment'),'rescue':(705,340,285,130,'Giải cứu mở rộng\nFlash / Mystery Box\nHũ / chuyến / ledger'),'admin':(1035,340,285,130,'Quản trị trên mobile\nduyệt và kiểm duyệt\nđối soát / cấu hình')}
    n['browse']=(45,340,285,130,'Khám phá và nội dung\nHome / tìm kiếm\nCampaign và tương tác')
    n['trade']=(375,340,285,130,'Thương mại\nProduct / Batch / Cart\nCheckout và giao nhận')
    diagram('functions','Phân rã chức năng theo nghiệp vụ',n,[('root',k,'')for k in ['browse','trade','rescue','admin']],size=(1380,650),note='Chức năng cũ promotions, wallet ưu đãi và bài đăng tổng quát không được khôi phục\nchỉ vì tên collection hoặc tài liệu lịch sử còn tồn tại.')
    n={'user':(60,100,300,110,'User trừu tượng\nCustomer / Seller / Admin'),'product':(540,100,300,110,'Product\ngiá / danh mục / mô tả'),'batch':(1020,100,300,110,'ProductBatch\nreserve / release / finalize'),'cart':(60,370,300,110,'Cart / CartItem\nowner + số lượng chọn'),'order':(540,370,300,110,'Order / OrderItem\ntrạng thái + snapshot'),'campaign':(1020,370,300,110,'RescueCampaign\nquota theo từng Batch'),'repo':(60,650,300,110,'Repository interface\nFirebase / Offline / Guest'),'service':(540,650,300,110,'Domain và service\nInventory / policy / pricing'),'vm':(1020,650,300,110,'ViewModel\nFragment quan sát LiveData')}
    n['vm']=(1020,650,300,110,'ViewModel\nFragment quan sát\nLiveData')
    e=[('user','product','Seller sở hữu'),('product','batch','1 : n'),('user','cart','owner'),('cart','order','checkout'),('batch','campaign','phân bổ'),('order','service','invariant'),('repo','service','phối hợp'),('vm','repo','phụ thuộc interface',{'points':[(1170,760),(1170,825),(210,825),(210,760)],'labelAt':(690,797)})]
    diagram('oop','Quan hệ lớp và trách nhiệm chủ đạo',n,e,size=(1380,940),note='Sơ đồ lớp rút gọn cho phần giải thích hướng đối tượng; không thay thế toàn bộ source.\nBackend vẫn kiểm tra invariant giao dịch, không tin riêng đối tượng ở Android client.')
if __name__=='__main__':main()
