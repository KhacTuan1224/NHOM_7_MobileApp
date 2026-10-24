# Câu hỏi bảo vệ và câu trả lời bám code

1. **Vì sao tách Product và Batch?** Product giữ tên, mô tả, giá/đơn vị; ProductBatch giữ hạn và tồn của từng đợt. Một Product có hai lô hạn khác nhau nên không thể dùng một expiryDate chung. Tra domain/model/ProductBatch.java.

2. **Còn availableQuantity có luôn bán được không?** Không. getSellableQuantity còn kiểm tra thời điểm và status. Lô hết hạn giữ tồn vật lý để đối soát nhưng lượng bán được bằng 0; hủy giữ không hồi sinh quyền bán.

3. **Hai người mua cùng lượng tồn cuối thì sao?** commerce.js đọc và giữ trong Firestore transaction; khi cạnh tranh, điều kiện được kiểm tra lại. Không lấy tồn trên ViewModel làm nguồn quyết định. Test concurrency chỉ bao phủ ca đã viết.

4. **Retry checkout có tạo hai đơn?** CheckoutRequestIdentity giữ requestId qua SavedStateHandle; backend checkoutReceipts đối chiếu fingerprint. Replay đúng nội dung trả kết quả cũ, mã dùng lại cho nội dung khác bị coi xung đột. Client identity một mình chưa đủ.

5. **Rules có bảo vệ Admin SDK không?** Client SDK chịu firestore.rules. Admin SDK là đường tin cậy, nên server.auth kiểm ID token, users ACTIVE, role và endpoint kiểm owner. Test Rules và API phải tách hai đường này.

6. **Room khác Firestore cache thế nào?** Room là chín bảng SQLite do app định nghĩa, có entity/DAO/mapper/migration. SDK có persistence riêng. Lượt UI mạnh đã tắt SDK disk cache, disable network và force-stop app demo trước khi catalog đọc lại từ Room. Không suy ảnh offline vì URL không phải file ảnh.

7. **Offline có checkout được không?** Không. CriticalActionPolicy chỉ queue READ_SYNC. Giá/tồn cache có thể cũ; checkout, payment, approval, inventory và transition cần online, backend kiểm tra lại.

8. **TTL và WorkManager bảo đảm gì?** Policy có catalog30m, campaign/order10m, banner1h, hard expiry7d; scheduler periodic6h và startup unique work khi CONNECTED. Không bảo đảm đúng giây và không mọi DAO đều tự lọc hard expiry. Worker có success/retry và metadata.

9. **Xoay màn đã test đến đâu?** 9 layout test và một CourseRotationEvidenceTest giữ email qua landscape/recreate đã qua trên demo cô lập; cặp ảnh login cùng dữ liệu có thật. Không khẳng định mọi form/keyboard/màn ngắn đã qua.

10. **Google auth xin những quyền gì?** GoogleCredentialService dùng Credential Manager/GetSignInWithGoogleOption lấy ID token cho Firebase Auth. Không suy ra quyền đọc Gmail/Drive. OAuth provider thật chưa được kiểm chứng trong lượt này; Auth Emulator khác Google Sign-In thật.

11. **Ảnh được lưu ở đâu và ký thế nào?** Cloudinary qua Cloudflare Worker. Worker kiểm token/hồ sơ và resource/owner, giữ secret phía server, bảo vệ asset đang được Campaign tham chiếu. Không gọi là Firebase Storage. 9 test Worker không chứng minh upload live.

12. **Ứng dụng có dùng Google Maps SDK hoặc AI không?** Không có chứng cứ cho hai khẳng định đó. Vị trí dùng Fused/Geocoder và tính khoảng cách; xem điểm nhận mở geo intent sang app bản đồ. Bộ lọc/sắp xếp không tự trở thành AI.

13. **SePay ngăn xác nhận tiền hai lần thế nào?** Callback được xác thực, đối chiếu account/amount/code/time, lưu event và bank transaction lock. Hũ và đơn có mapping riêng nhưng chung sepayBankTransactions để không ghi chéo. Probe id0 không cộng tiền. Demo dùng payload giả local, không tiền thật.

14. **Hũ 24 giờ có bắt đầu từ lúc Seller nhận push không?** Không, deadline = allocatedAt +24h. Chọn Batch >24h+1m và ≤48h; Campaign end đủ dài. Seller phải nhận, giao, gửi ảnh trong cùng24h. DELIVERED đúng hạn chờ Admin; VERIFY chốt kho, PAY ghi chi sau chuyển ngoài app. Quá hạn chưa ảnh thì CANCELLED/RELEASE về available, không hoàn ngân hàng cho donor. Chuyến cũ không đổi deadline. RescueFundTime cố định giờ VN.

15. **Có thể kết luận hoàn thiện, đóng góp nhóm và dưới20% tương đồng không?** Không suy phần trăm từ commit/LOC hoặc test count. Git evidence lưu merge thật và alias chờ xác nhận; nhóm xác nhận MSSV/nhiệm vụ/tỷ lệ. UEH chưa chạy nên không cam kết20%. Build thành công không chứng minh production deploy, OAuth, ngân hàng, ảnh hoặc FCM thật.
