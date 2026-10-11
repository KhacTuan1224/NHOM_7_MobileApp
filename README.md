# NHOM_7_MobileApp

## Development Team

| Member                                                                         | Role        |
| ------------------------------------------------------------------------------ | ----------- |
| Lại Thành Đạt ([LaiThanhDat-glitch](https://github.com/LaiThanhDat-glitch))    | Team Leader |
| Trương Thành Đạt ([datweb07](https://github.com/datweb07))                     | Member      |
| Nguyễn Phương Chinh ([chinhngprit](https://github.com/chinhngprit))            | Member      |
| Phan Khắc Anh Tuấn ([KhacTuan1224](https://github.com/KhacTuan1224))           | Member      |
| Nguyễn Tấn Khiêm ([nguyentankhiem1610](https://github.com/nguyentankhiem1610)) | Member      |
# Cloudinary image storage

Android uses the `cloudinary-worker` Cloudflare Worker for signed uploads and deletes. The Worker verifies Firebase ID tokens, keeps the Cloudinary API secret server-side, assigns deterministic public IDs and Media Library folders, and only permits deletes inside the caller's own asset namespace (category/banner assets are admin-only). Uploading an existing ID enables Cloudinary overwrite/invalidation; Firestore stores the URL, from which the public ID is derived. When a saved profile/product/post/application removes an old URL, the app requests deletion after the Firestore write succeeds.

Follow [`cloudinary-worker/README.md`](cloudinary-worker/README.md) to configure and deploy the Worker. Set `CLOUDINARY_CLOUD_NAME` and `CLOUDINARY_SIGNING_URL` in root or user Gradle properties. This does not use Firebase Functions, Firebase Blaze, or a credit card. Never store `CLOUDINARY_API_SECRET` in Android, Gradle properties, or the repository.

The canonical IDs are `rescuefarm/users/{uid}/avatar`, `rescuefarm/sellers/{uid}/shop-avatar`, `rescuefarm/sellers/{uid}/application-proof`, `rescuefarm/products/{uid}/{productId}/image-{slot}`, `rescuefarm/posts/{uid}/{postId}/image-{slot}`, `rescuefarm/reviews/{uid}/{reviewId}/image-{slot}`, and `rescuefarm/categories/{categoryId}/image` / `rescuefarm/banners/{bannerId}/image`. Previous unsigned uploads used random IDs and are not eligible for automatic deletion by this Worker; remove those legacy assets manually once from Cloudinary Media Library.

# Profile map

The profile location preview uses Leaflet in Android WebView with Esri World Street Map tiles and does not require an API key. The map displays Esri and data-provider attribution and appends the app identifier to WebView's default User-Agent. Esri's public tile service is subject to its current service terms and availability.

The current app has upload controls on user profile (including customer/guest/admin accounts), seller shop profile, seller application proof, product, post, review, and existing category/banner records in the admin lists. Category/banner creation is not currently exposed in the app, so this adds image replacement for existing records rather than new category/banner CRUD screens.
