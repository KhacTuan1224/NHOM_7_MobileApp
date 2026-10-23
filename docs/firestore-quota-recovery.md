# Sửa lỗi Firestore quota — 23/10/2026

## Nguyên nhân và giới hạn

`8 RESOURCE_EXHAUSTED: Quota exceeded` là Firestore từ chối thao tác do hạn mức tài nguyên. Log này chưa cho biết chính xác loại hạn mức nào đã cạn. Kiểm tra Firebase Console → Firestore → Usage và Google Cloud Quotas; không suy ra lỗi Rules hay Seller chưa duyệt từ mã này.

`/health` trả `ok: true` chỉ xác nhận tiến trình API đang hoạt động. Bản sửa bổ sung `migrationsReady`, `firestoreReadStatus` và `retryAfterSeconds`. `NOT_IN_COOLDOWN` nghĩa là chưa bị chặn bởi bộ đếm chờ trong tiến trình, không phải kết quả kiểm tra kết nối Firestore.

Code không reset quota. Nếu hết quota ngày, cần chờ hạn mức được cấp lại hoặc xử lý hạn mức trong Console. Redeploy và tải lại liên tục không làm tăng quota. Tài liệu chính thức: https://firebase.google.com/docs/firestore/quotas.

## Thay đổi

- `/campaigns/cards`: cache tài liệu 30 giây và gộp truy vấn đồng thời theo collection/id, dùng chung dữ liệu Seller/sản phẩm giữa các card. Vẫn chỉ trả projection công khai; không dùng ảnh hồ sơ riêng tư làm ảnh cửa hàng.
- Android: giữ metadata card trong 60 giây, không gửi trùng request đang chạy, chờ ít nhất 60 giây trước khi thử lại cùng chiến dịch, kể cả lần trước thất bại.
- `/seller/statistics`: cache kết quả theo Seller/kỳ trong 45 giây; xác thực và kiểm tra phê duyệt vẫn chạy mỗi request. Thống kê có thể chậm tối đa 45 giây.
- Khi API/tác vụ nền gặp quota, trả `503 FIRESTORE_QUOTA_EXCEEDED` kèm `Retry-After`, dừng phát thêm công việc nền trong 5 phút. Sau đó thử lại; nếu quota vẫn hết sẽ tiếp tục chờ. Không ghi nhận thông báo là đã gửi khi chưa gửi thành công.
- Migration bắt buộc vẫn phải hoàn thành trước khi phục vụ API thông thường. Nếu startup gặp quota, tiến trình vẫn phục vụ health và thử lại migration sau thời gian chờ.
- Worker hết hạn chỉ quét lô có trạng thái đang bán và đơn chưa kết thúc; không quét lại toàn bộ đơn đã giao/hủy mỗi phút. Dữ liệu lô cũ được kiểm tra tối đa một trang 200 lô mỗi giờ với cursor; trạng thái hết hạn vẫn được kiểm tra ở luồng mua hàng, không phụ thuộc việc worker đã gắn nhãn hay chưa.
- Không tự quét sửa toàn bộ liên kết chiến dịch cũ mỗi lần khởi động. `REPAIR_LEGACY_CAMPAIGN_LINKS=true` chỉ dùng khi chủ động chạy bảo trì; bình thường để unset.
- Thanh tiến độ chiến dịch ở card, Home và chi tiết: dưới 50% đỏ; từ 50% xanh lá. Quy tắc dùng chung, có test cho mốc biên.

SePay giữ nguyên đường webhook/xác minh và xử lý lỗi của nó; cache và cổng chờ mới không áp dụng lên webhook. Không thay đổi giá, kho thực tế, lịch sử đơn hoặc quy trình thanh toán.

## Áp dụng

Đã thêm index duy nhất `productBatches: status ASC, expiryDate ASC` vào database `(default)` của project `nhom7-rescuefarm` bằng Firebase CLI, chế độ non-interactive, không dùng force. Log `build/quota-index-deploy.log` ghi rõ giữ lại 23 index cũ và field override hiện có. Kiểm tra metadata sau triển khai thấy index mới. Không sửa Rules, billing hoặc document nghiệp vụ.

Source backend cần deploy lên Render; lượt sửa này chưa deploy Render. Sau đó cài APK debug mới tại `app/build/outputs/apk/debug/app-debug.apk` để nhận thay đổi Android. Cache là bộ nhớ riêng của mỗi tiến trình, không lưu bền qua restart và không phải cam kết đủ quota ở mọi mức tải.

## Kiểm thử mềm

- Android: 245 test/74 class qua, `assembleDebug` và `lintDebug` thành công; Lint còn 656 warning, 0 error. Lượt Gradle đầu bị daemon dừng, chạy lại riêng với `--max-workers=2` thành công.
- Backend: syntax check qua; 165 unit test và 8 Cloudinary Worker test qua.
- Firestore Emulator: 124/124 test qua, không test ghi vào database thật.
- Regression mới: gộp request/cached TTL, cách ly cache Seller và từ chối Seller bị thu hồi duyệt, trả 503/Retry-After, giữ health hoạt động, dừng truy vấn/ghi tiếp sau lỗi quota, bỏ quét đơn kết thúc và không bỏ qua audit lô khi retry quota, màu tiến độ tại 50%.

Log: `build/quota-android.log`, `build/quota-backend-check.log`, `build/quota-backend-unit.log`, `build/quota-firestore.log`. Không thực hiện giao dịch hoặc webhook SePay thật. Chưa kiểm chứng quota production đã phục hồi.
