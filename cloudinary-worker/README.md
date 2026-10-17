# Cập nhật A17: upload Campaign an toàn

**Trước khi deploy bản hiện tại, đọc [cấu hình A13–A17](../docs/a13-a17-implementation.md#cấu-hình-bắt-buộc-trước-khi-dùng-bản-mới).** Worker cần MEDIA_BACKEND_URL và shared secret MEDIA_WORKER_SECRET ở cả Worker/Render. Campaign không dùng sign-upload nữa; các asset khác cũng kiểm tra tham chiếu Campaign trước khi ký/xóa. Backend, Worker, Rules và APK cần được cập nhật đồng bộ.

### BƯỚC 1: Lấy API Key & Secret từ Cloudinary Console

1. Truy cập [Cloudinary Console](https://console.cloudinary.com/).
2. Tại trang **Dashboard** (Product Environment Credentials), chuẩn bị sẵn 3 thông tin:
* **Cloud name** (Ví dụ: `dmahghpku`)


* **API Key** (Dãy số)
* **API Secret** (Chuỗi ký tự bảo mật - *Không dán vào chat hay commit code*)



---

### BƯỚC 2: Cấu hình và Deploy Cloudflare Worker

Mở PowerShell tại thư mục `cloudinary-worker` (`D:\NHOM_7_MobileApp\cloudinary-worker`):

1. **Chỉnh sửa tệp `wrangler.toml`:**
   Mở tệp `cloudinary-worker/wrangler.toml` và điền thông tin **Cloud Name** và **API Key**:
```toml
name = "rescuefarm-cloudinary"
main = "src/index.js"
compatibility_date = "2024-01-01"

[vars]
CLOUDINARY_CLOUD_NAME = "dmahghpku"
CLOUDINARY_API_KEY = "API_KEY_CUA_BAN"
FIREBASE_PROJECT_ID = "nhom7-rescuefarm"

```


2. **Đăng nhập và Deploy Worker:**
* Chạy lệnh đăng nhập Cloudflare (sẽ mở trình duyệt để xác thực tài khoản Cloudflare miễn phí):
```powershell
npx wrangler login

```


* Deploy Worker lần đầu:
```powershell
npx wrangler deploy

```


* *Sau khi deploy xong, Terminal sẽ in ra đường dẫn Worker dạng:*
  `https://rescuefarm-cloudinary.<ten-subdomain-cua-ban>.workers.dev`


3. **Lưu API Secret vào Cloudflare Environment Secret:**
   Chạy lệnh sau để nhập API Secret an toàn trực tiếp vào Cloudflare:
```powershell
npx wrangler secret put CLOUDINARY_API_SECRET

```


*(Nhập/dán chuỗi **API Secret** lấy từ Cloudinary ở Bước 1 vào dòng nhắc của Wrangler và nhấn Enter).*

---

### BƯỚC 3: Cấu hình URL Worker vào Android App (`gradle.properties`)

Mở tệp `gradle.properties` ở thư mục gốc của dự án (`D:\NHOM_7_MobileApp\gradle.properties`) hoặc tệp cá nhân `%USERPROFILE%\.gradle\gradle.properties` và điền các biến cấu hình:

```properties
CLOUDINARY_CLOUD_NAME=dmahghpku
CLOUDINARY_SIGNING_URL=https://rescuefarm-cloudinary.<ten-subdomain-cua-ban>.workers.dev

```

*(Lưu ý: Không để dấu gạch chéo `/` ở cuối đường dẫn `CLOUDINARY_SIGNING_URL`).*

---

### BƯỚC 4: Sync & Rebuild Dự án Android

1. Mở **Android Studio**.
2. Chọn **File** $\rightarrow$ **Sync Project with Gradle Files**.


3. Tiến hành Rebuild dự án (**Build** $\rightarrow$ **Rebuild Project**).



---

### BƯỚC 5: Dọn dẹp tệp rác ngẫu nhiên cũ (Chạy 1 lần)

Do các ảnh upload theo cơ chế Unsigned cũ có tên ngẫu nhiên không thể xác định chủ sở hữu an toàn từ Firestore, bạn chỉ cần thực hiện dọn dẹp thủ công 1 lần cuối:

1. Vào **Cloudinary Console** $\rightarrow$ **Media Library**.


2. Chọn thư mục `rescuefarm_assets` (hoặc các ảnh rác tên ngẫu nhiên cũ) và chọn **Delete**.

### Ghi chú về thư mục Media Library

Worker đặt `public_id` trong cây `rescuefarm/...` để dùng làm ID ổn định, đồng thời đặt `asset_folder` thành `rescuefarm_assets/...` để ảnh xuất hiện dưới thư mục `rescuefarm_assets` trong **Folders**. Sau khi cập nhật Worker, chạy `npx wrangler deploy` lại. `asset_folder` chỉ sắp xếp asset trong Media Library; `public_id` là ID được dùng để ghi đè và xóa.

Lưu ý: thư mục được gán lúc upload. Ảnh đã upload trước bản Worker này có thể vẫn nằm ở thư mục cũ hoặc chỉ hiện trong **Assets**; việc deploy lại không tự di chuyển ảnh cũ.
