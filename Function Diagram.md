### 1. High-Level Overview [G: Guest, B: Buyer, S: Seller, A: Admin] 
``` mermaid
flowchart TD
    ROOT(["HỆ THỐNG GIẢI CỨU NÔNG SẢN"]):::rootClass

    M1["1.0 Quản trị Tài khoản & Hồ sơ"]:::modClass
    M2["2.0 Khám phá & Bản tin Cứu trợ"]:::modClass
    M3["3.0 Danh mục & Quản lý Nông sản"]:::modClass
    M4["4.0 Giỏ hàng & Xử lý Đơn hàng"]:::modClass
    M5["5.0 Định vị GPS & Vận chuyển"]:::modClass
    M6["6.0 Đánh giá & Báo cáo Vi phạm"]:::modClass
    M7["7.0 Quản trị Admin & Thống kê"]:::modClass

    ROOT --> M1
    ROOT --> M2
    ROOT --> M3
    ROOT --> M4
    ROOT --> M5
    ROOT --> M6
    ROOT --> M7

    classDef rootClass fill:#1B5E20,stroke:#0D3810,stroke-width:2px,color:#fff,font-weight:bold;
    classDef modClass fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#1B5E20,font-weight:bold;
```
### 2. Quản trị Tài khoản và Định vị GPS 
``` mermaid
flowchart TD
    M1["1.0 Quản trị Tài khoản & Hồ sơ"]:::headerClass

    subgraph SUB_AUTH ["Xác thực & Phiên làm việc"]
        F11["1.1 Đăng ký & Chọn vai trò <br/><b>[B, S]</b>"]
        F12["1.2 Đăng nhập (OTP / Google / Email) <br/><b>[B, S, A]</b>"]
        F13["1.3 Chế độ Guest (UUID SharedPreferences) <br/><b>[G]</b>"]
    end

    subgraph SUB_PROFILE ["Hồ sơ & Định vị"]
        F14["1.4 Tích hợp GPS Nông trại / Vị trí <br/><b>[B, S]</b>"]
        F15["1.5 Quản lý Sổ địa chỉ giao hàng riêng <br/><b>[B]</b>"]
        F16["1.6 Xem hồ sơ công khai & Độ uy tín <br/><b>[G, B, S, A]</b>"]
    end

    M1 --> SUB_AUTH
    M1 --> SUB_PROFILE

    classDef headerClass fill:#1E88E5,stroke:#0D47A1,stroke-width:2px,color:#fff,font-weight:bold;
    classDef default fill:#E3F2FD,stroke:#1976D2,stroke-width:1px,color:#0D47A1;
```
