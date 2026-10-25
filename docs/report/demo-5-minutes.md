# Kịch bản demo RescueFarm trong 5 phút

Môi trường: APK reportDemo; emulator-5580; project demo-rescuefarm; Firestore8088/Auth9098; backend3008. Dữ liệu mang tên DEMO. Không chuyển tiền, gửi email/push hoặc gọi ngân hàng thật. Mở sẵn backend/emulator, cache catalog trước khi trình bày. Phân vai người nói/người thao tác do nhóm xác nhận; không gán nhiệm vụ học thuật theo alias Git.

| Mốc | Người phụ trách | Màn/thao tác | Luận điểm và bằng chứng |
|---|---|---|---|
| 0:00–0:30 | Người trình bày 1, chờ xác nhận | Trang chủ Guest, một sản phẩm DEMO | Bài toán TMĐT; Product là nội dung, Batch là hạn/tồn. Không công bố tác động chưa đo. |
| 0:30–1:15 | Người thao tác, chờ xác nhận | Khám phá → chi tiết → chọn quantity → thêm giỏ | Màn thật, bộ lọc và số lượng hiện hành; giỏ không giữ tồn. |
| 1:15–2:00 | Người thao tác | Customer cart → checkout form; mở sẵn kết quả API đã kiểm thử | Giá/tồn revalidate trên backend; requestId chống retry. Nếu chưa có QR UI hoàn chỉnh, nói rõ và dùng test log, không giả ảnh. |
| 2:00–2:30 | Người thao tác | Seller product/batch list | Hạn theo lô, tồn vật lý khác khả dụng; lịch sử không bị xóa. Không chờ upload. |
| 2:30–3:20 | Người trình bày 2, chờ xác nhận | Admin dashboard/resources; Hũ trạng thái đã chuẩn bị | Admin mobile. Hũ có 24 giờ từ tạo; DELIVERED đúng hạn chờ Admin; PAY ghi sau chuyển ngoài app. HEAD ảnh test stub, không tiền thật. |
| 3:20–3:45 | Người trình bày kỹ thuật | Sơ đồ kiến trúc và một ca Rules/API | Android SDK chịu Rules; Admin SDK API kiểm token/owner; transaction bảo vệ tiền/kho. |
| 3:45–4:10 | Người thao tác | Login email đã nhập, xoay ngang rồi dọc | Cùng dữ liệu, recreate không mất email; không chứng nhận mọi form. |
| 4:10–4:30 | Người thao tác | Catalog offline sau process restart được chuẩn bị từ test | Room khác cache SDK; persistence SDK off; chỉ chứng minh catalog chữ, không ảnh offline. |
| 4:30–5:00 | Người trình bày 1 | Bảng kết quả hiện hành và Git merges | 253 JVM;183 backend;9 Worker;173 Firestore/API;11 instrumentation; giới hạn OAuth/media/SePay live/UEH và phân công chờ xác nhận. Không cộng test thành % hoàn thiện. |

## Chuẩn bị trước giờ bảo vệ

1. Chạy lại dịch vụ local và kiểm tra `/health`; giữ manifest/APK SHA đúng bản đang trình bày.
2. Không chạy seed đè quỹ đã có. `fund-demo setup` cố ý từ chối quỹ tồn tại; dùng project demo tách biệt nếu cần diễn tập mới. Không xóa rescueFundRuns để ép cùng ngày.
3. Mở sẵn các màn quan trọng và ảnh bằng chứng trong PDF. Nếu mạng yếu, dùng emulator và fixture local, công khai đây là dữ liệu test. Không đưa screenshot vào app để giả đang hoạt động.
4. Đăng nhập các danh tính giả lập trước hoặc dùng các lượt instrumentation có kiểm soát; không nói OAuth thật đã qua khi chỉ dùng Auth Emulator.
5. Dành người bấm riêng, tập 2 lần bằng đồng hồ. Nếu luồng kéo dài, bỏ upload và giải thích qua test đã ghi nhận; không cắt bỏ giới hạn.

## Dự phòng

- Backend lỗi: trình bày catalog Room offline, sơ đồ và log; nói checkout cần online.
- Emulator chậm: dùng ảnh thật đã nhúng với manifest và nêu ảnh chụp trước; không gọi đó là thao tác live.
- Hỏi QR/SePay: mở test webhook/replay, giải thích tiền giả trong demo. Không thực hiện chuyển khoản thử trên tài khoản thật.
- Hỏi chứng từ Hũ: nhấn rõ HEAD ảnh stub trong harness, chưa chứng minh upload và bàn giao thật.
