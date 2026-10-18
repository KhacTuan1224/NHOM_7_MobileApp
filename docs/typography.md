# Font chữ RescueFarm

- Tiêu đề màn hình, tiêu đề mục và tên trong thẻ: IBM Plex Sans.
- Nội dung, số liệu, nhãn, nút, ô nhập, menu và chữ trong biểu đồ: Barlow.
- Font được đóng gói trong res/font, không cần tải từ Internet khi chạy app. Các file đã được kiểm tra có glyph tiếng Việt.
- Family XML khai báo normal/italic và các trọng lượng 400/500/600/700. Các cỡ chữ, màu sắc và quy tắc bố cục được giữ theo giao diện hiện tại.

Theme sáng và tối cùng kế thừa Theme.RescueFarm.Foundation trong values/typography.xml. Các TextAppearance Display/Headline/Title dùng IBM Plex Sans; Body/Label dùng Barlow. Các kiểu TextAppearance.RescueFarm.* trong styles.xml khai báo font riêng để không bị font mặc định của Material ghi đè.

Với tiêu đề mới trong XML, dùng TextAppearance.RescueFarm.SectionTitle, TitleMedium hoặc style tiêu đề khác đã có. Với TextView tạo bằng Java, gọi AppTypography.title(view). Nội dung dùng theme Barlow; khi cần đặt trực tiếp cho TextView/Canvas dùng AppTypography.body(view) hoặc AppTypography.bodyTypeface(context). Không ép Typeface.DEFAULT hay sans-serif vì sẽ làm mất font của app. Chữ in đậm trong nội dung vẫn dùng Barlow Bold, không tự biến thành tiêu đề.

Nguồn font chính thức:

- IBM Plex Sans: https://github.com/google/fonts/tree/main/ofl/ibmplexsans
- Barlow: https://github.com/google/fonts/tree/main/ofl/barlow

Giấy phép SIL Open Font License đi kèm tại docs/licenses/fonts và được đóng gói trong assets/licenses/fonts của APK.
