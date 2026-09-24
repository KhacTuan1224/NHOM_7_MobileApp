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


### 4. Thương mại,Đặt hàng và Vận chuyển
``` mermaid
    flowchart TD
    ROOT_M45["THƯƠNG MẠI VÀ VẬN CHUYỂN"]:::headerClass

    M4["4.0 Giỏ hàng và Xử lý Đơn hàng"]:::parentClass
    M5["5.0 Định vị GPS và Vận chuyển"]:::parentClass

    ROOT_M45 --> M4
    ROOT_M45 --> M5

    subgraph SUB_CHECKOUT ["Quy trình Mua và Thanh toán"]
        F41["4.1 Giỏ hàng đa nhà vườn (Room DB) <br/><b>[G, B]</b>"]
        F42["4.2 Checkout Tài khoản (Lấy địa chỉ sẵn) <br/><b>[B]</b>"]
        F43["4.3 Checkout Guest (Form thông tin) <br/><b>[G]</b>"]
        F44["4.4 Thanh toán COD / Chuyển khoản VietQR <br/><b>[G, B]</b>"]
    end

    subgraph SUB_ORDER_LOGISTICS ["Theo dõi & Vận chuyển"]
        F45["4.5 Lịch sử đơn và Trạng thái mua hàng <br/><b>[B]</b>"]
        F46["4.6 Quản lý và Xác nhận đơn phía vườn <br/><b>[S]</b>"]
        F51["5.1 Cập nhật mã vận đơn và Trạng thái <br/><b>[S]</b>"]
        F52["5.2 Theo dõi lộ trình Google Maps <br/><b>[G, B]</b>"]
    end

    M4 --> SUB_CHECKOUT
    M4 --> F45
    M4 --> F46
    M5 --> SUB_ORDER_LOGISTICS

    classDef headerClass fill:#43A047,stroke:#1B5E20,stroke-width:2px,color:#fff,font-weight:bold;
    classDef parentClass fill:#E8F5E9,stroke:#43A047,stroke-width:2px,color:#1B5E20,font-weight:bold;
    classDef default fill:#F1F8E9,stroke:#689F38,stroke-width:1px,color:#33691E;
```

