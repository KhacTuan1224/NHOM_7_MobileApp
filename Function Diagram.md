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
