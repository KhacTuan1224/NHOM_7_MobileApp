# Đóng gói RescueFarm bằng Docker và phát hành GitHub Packages

Dockerfile ở root đóng gói **HTTP backend trong `functions`**. APK Android được tải ở GitHub Releases; Firestore/Auth/FCM vẫn dùng Firebase, media Worker vẫn triển khai trên Cloudflare. Image không chứa APK, cơ sở dữ liệu Firestore hay thông tin đăng nhập dịch vụ.

## Build và chạy tại máy

Cần Docker Engine/Docker Desktop đang chạy ở chế độ Linux containers. Tại root repo:

```powershell
docker build -t rescuefarm-backend:1.0.0 .
```

Nạp service account từ file riêng bên ngoài repo rồi truyền biến lúc chạy; các biến tích hợp khác dùng đúng tên trong README và tài liệu setup hiện có:

```powershell
$env:FIREBASE_SERVICE_ACCOUNT_JSON = Get-Content -LiteralPath 'C:\secure\service-account.json' -Raw
docker run --detach --name rescuefarm-backend --init --restart unless-stopped -p 3000:3000 --env FIREBASE_SERVICE_ACCOUNT_JSON rescuefarm-backend:1.0.0
Invoke-RestMethod http://localhost:3000/health
```

Ví dụ trên chỉ truyền cấu hình Firebase tối thiểu. Khi cần media, thanh toán hoặc scheduler, truyền thêm cấu hình runtime tương ứng như `MEDIA_WORKER_SECRET`, `NOTIFICATION_WORKER_SECRET`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_SIGNING_URL` và `SEPAY_MERCHANTS_JSON`. Dùng cùng Firebase project với APK/Worker. Khởi động backend sẽ thực hiện startup migration/reconciliation hiện có trong mã nguồn; xem [vận hành backend](../functions/README.md).

Image chạy bằng user `node`, mặc định cổng `3000`, hỗ trợ đổi cổng qua `PORT`, có healthcheck `/health` và chỉ cài production dependencies bằng lockfile. `.dockerignore` chỉ cho phép source backend và manifest dependency vào build context; service account, `.env`, cache, APK và tài liệu không đi vào image. Healthcheck phản ánh HTTP endpoint, không thay kiểm tra từng tích hợp thanh toán/FCM.

## Tự động đưa image lên mục Packages

Workflow [Docker package](../.github/workflows/docker-package.yml) thực hiện kiểm tra cú pháp backend/Worker, build image `linux/amd64` và đăng nhập GHCR bằng `GITHUB_TOKEN` có quyền `packages: write`.

- Push `main` có thay đổi Docker/backend/Worker/workflow: build và push tag `main`, `latest`, `sha-<commit>`.
- Push tag `v1.0.0`: build và push tag `1.0.0`, `1.0`, `latest`, `sha-<commit>`.
- Pull request: chỉ kiểm tra cú pháp và build image; không push package.
- Actions → **Docker package** → **Run workflow** trên `main`: có thể tạo package đầu tiên mà không cần đổi source.

Tên image tự lấy từ repository chạy workflow và chuyển thành chữ thường:

```text
ghcr.io/<owner>/<repository>-backend:1.0.0
```

Ví dụ repository `LaiThanhDat-glitch/NHOM_7_MobileApp` tạo `ghcr.io/laithanhdat-glitch/nhom_7_mobileapp-backend:1.0.0`; workflow chạy trên fork `datweb07` sẽ dùng owner `datweb07`. Label `org.opencontainers.image.source` liên kết package với đúng repository.

Package chỉ xuất hiện sau lần push image thành công. Package mới mặc định private; nếu muốn mọi người pull không cần đăng nhập, mở package → **Package settings** → **Change visibility** → **Public**. Nếu giữ private, người tải cần quyền đọc package và đăng nhập GHCR. Không cần tạo Docker Hub account hoặc thêm PAT chỉ để workflow này push image. Nếu package cùng tên đã tồn tại dưới tài khoản, cấp quyền Actions của repository cho package đó khi cần.

## Phát hành v1.0.0

Nội dung để dán vào ô mô tả Release ở [v1.0.0.md](releases/v1.0.0.md). Tiêu đề: **RescueFarm v1.0.0 — Giải cứu nông sản, kết nối cộng đồng**. Tag: **`v1.0.0`**. `versionName` hiện tại của APK vẫn là `1.0`.

Sau khi commit/push các thay đổi lên `main`, tạo và push tag trên đúng commit cần phát hành:

```powershell
git tag -a v1.0.0 -m 'RescueFarm v1.0.0'
git push origin v1.0.0
```

Workflow Android dùng nội dung đã soạn để tạo Release và đính kèm APK/checksum; workflow Docker đăng image lên Packages độc lập. Cần secret `GOOGLE_SERVICES_JSON` và các biến endpoint Android trong Actions để APK kết nối đúng môi trường. Hai workflow có thể hoàn thành ở thời điểm khác nhau; kiểm tra trạng thái cả hai trước khi thông báo phát hành. Nếu tự tạo Release trước, dán nội dung Markdown vào mô tả; workflow sẽ giữ mô tả và bổ sung APK khi build xong.

Tham khảo chính thức: [GitHub Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry), [xuất bản Docker image bằng Actions](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images), [Docker build best practices](https://docs.docker.com/build/building/best-practices/).
