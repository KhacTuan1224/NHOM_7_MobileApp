# Trích đoạn code bổ sung ở 5.2 và thay hình 5.3.X

Mục 5.1 giữ nguyên và không thêm code. Mục 5.2 giữ đủ ảnh, thêm code bên dưới; mục 5.3 giữ nguyên phần chữ và bảng. Mỗi phần giải thích là một đoạn.

**Mã nguồn 5.2.1. Firebase Authentication tiếp nhận Google credential**

```java
AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
firebaseAuth.signInWithCredential(credential)
    .addOnSuccessListener(result -> {
        FirebaseUser firebaseUser = result.getUser();
        ...
        loadOrCreateGoogleProfile(firebaseUser, callback);
    })
    .addOnFailureListener(exception -> notifyFailure(exception, callback));
```

Nguồn: data/remote/firebase/FirebaseAuthRepository.java, signInWithGoogleIdToken(), từ dòng 104.

Google ID token được đổi thành Firebase credential để tạo phiên xác thực. Sau khi thành công, Repository đọc hoặc tạo hồ sơ ứng dụng để lấy vai trò và trạng thái; nhánh lỗi trả thông tin qua callback. Xác thực danh tính không thay thế kiểm tra quyền tại Rules hoặc backend.

**Mã nguồn 5.2.2. Truy vấn sản phẩm công khai có giới hạn trang**

```java
return firestore.collection(PRODUCTS)
    .whereEqualTo("status", ProductStatus.ACTIVE.name())
    .orderBy(com.google.firebase.firestore.FieldPath.documentId())
    .limit(publicLimit + 1L);
```

Nguồn: data/remote/firebase/FirebaseProductRepository.java, publicQuery(), dòng 70–73.

Repository chỉ lấy sản phẩm ACTIVE và sắp theo document ID. Query lấy nhiều hơn giới hạn hiển thị một document để xác định còn dữ liệu cho lần tải tiếp theo; kết quả được ánh xạ về model rồi cung cấp cho ViewModel.

**Mã nguồn 5.2.3. Đăng nhập Firebase và đọc hồ sơ ứng dụng**

```java
firebaseAuth.signInWithEmailAndPassword(email, password)
    .addOnSuccessListener(result ->
        loadRequiredProfile(result.getUser(), callback))
    .addOnFailureListener(exception -> notifyFailure(exception, callback));
```

Nguồn: data/remote/firebase/FirebaseAuthRepository.java, signIn(), dòng 61–65.

Firebase Authentication kiểm tra thông tin đăng nhập và trả FirebaseUser khi thành công. Repository tiếp tục đọc hồ sơ ứng dụng để xác định vai trò và trạng thái; nếu xác thực thất bại, lỗi được chuyển qua callback thay vì coi tài khoản đã đăng nhập hợp lệ.

**Mã nguồn 5.2.4. Firestore Rules kiểm soát quyền đọc sản phẩm**

```text
match /products/{productId} {
  allow get, list: if resource.data.status == 'ACTIVE'
      || (isSignedIn() && resource.data.sellerId == request.auth.uid)
      || isAdmin();
  ...
}
```

Nguồn: firestore.rules, match /products/{productId}, từ dòng 707.

Quy tắc cho phép đọc sản phẩm đang ACTIVE, sản phẩm thuộc tài khoản Seller đăng nhập hoặc dữ liệu được Admin phép truy cập. Các điều kiện tạo và cập nhật nằm ở phần mã được lược; Rules này áp dụng cho đường truy cập SDK client, còn API dùng Admin SDK phải tự kiểm tra quyền.

**Mã nguồn 5.3.1. HomeViewModel nhận dữ liệu và phát kết quả tìm kiếm**

```java
homeState.addSource(productRepository.observePublicProductPage(), values -> {
    products = safe(values);
    ...
    publish();
});
...
private void publishSearch() {
    searchResults.postValue(discoveryEngine.search(
        products, campaigns, productBatches, sellerCoordinates, query,
        latitude, longitude, new Date()));
}
```

Nguồn: ui/home/HomeViewModel.java, từ dòng 97 và publishSearch() từ dòng 431.

Repository phát danh sách sản phẩm; ViewModel cập nhật dữ liệu rồi dựng lại trạng thái. Khi tìm kiếm, DiscoveryEngine nhận tập dữ liệu đã tải, bộ lọc và tọa độ, sau đó LiveData phát kết quả cho màn hình. Hai đoạn thuộc các vị trí khác nhau trong cùng file; dấu ba chấm giữa chúng lược phần mã không liên quan.

**Mã nguồn 5.3.2. Truy vấn sản phẩm công khai có giới hạn trang**

```java
return firestore.collection(PRODUCTS)
    .whereEqualTo("status", ProductStatus.ACTIVE.name())
    .orderBy(com.google.firebase.firestore.FieldPath.documentId())
    .limit(publicLimit + 1L);
```

Nguồn: data/remote/firebase/FirebaseProductRepository.java, publicQuery(), dòng 70–73.

Repository chỉ lấy sản phẩm ACTIVE và sắp theo document ID. Query lấy nhiều hơn giới hạn hiển thị một document để xác định còn dữ liệu cho lần tải tiếp theo; kết quả được ánh xạ về model rồi cung cấp cho ViewModel.

**Mã nguồn 5.3.3. Lọc trạng thái, danh mục và giá trên dữ liệu đã tải**

```java
if (!value.isAvailable()) return false;
if (!query.getCategoryId().isEmpty()
        && !query.getCategoryId().equals(value.getCategoryId())) return false;
if (value.getRescuePrice() < query.getMinPrice()
        || value.getRescuePrice() > query.getMaxPrice()) return false;
...
```

Nguồn: ui/home/DiscoveryEngine.java, matchesProductTextAndPrice(), từ dòng 96.

DiscoveryEngine loại sản phẩm không khả dụng hoặc không thỏa danh mục và khoảng giá trước khi kiểm tra từ khóa. Các điều kiện dùng query và danh sách model đã có trong bộ nhớ; thay đổi bộ lọc không tự chứng minh ứng dụng đã tìm trên toàn bộ Firestore.

**Mã nguồn 5.3.4. Kiểm tra lô đủ điều kiện bán**

```java
public boolean isSellable(Date now) {
    return now != null && expiryDate != null && !isExpired(now)
        && availableQuantity > QUANTITY_TOLERANCE
        && (status == BatchStatus.AVAILABLE
            || status == BatchStatus.RESERVED_PARTIAL);
}
```

Nguồn: domain/model/ProductBatch.java, isSellable(), dòng 80–83.

Model chỉ coi lô có thể bán khi còn hạn, còn lượng khả dụng và trạng thái cho phép. Phép kiểm tra giúp giao diện chọn đúng lô; khi tạo đơn, backend vẫn phải kiểm tra lại lô trong transaction vì tồn và điều kiện bán có thể đã thay đổi.

**Mã nguồn 5.3.5. Chọn nguồn lưu giỏ theo phiên người dùng**

```java
AuthRepository auth = AuthRepositoryFactory.create(context);
if (auth.isAuthenticated() && auth.getCurrentUserId() != null) {
    return new FirebaseCartRepository(auth.getCurrentUserId(), ...);
}
...
return new GuestCartRepository(
    RescueFarmDatabase.getInstance(context).guestCartDao(), ...);
```

Nguồn: data/repository/CartRepositoryFactory.java, create(), từ dòng 13.

Factory chọn giỏ tài khoản qua FirebaseCartRepository hoặc giỏ Guest qua DAO của Room. Định danh Guest và các tham số executor được lược để tập trung vào việc chọn nguồn dữ liệu. Giỏ lưu lựa chọn mua, không phải một lần giữ tồn kho.

**Mã nguồn 5.3.6. Tạo đơn và chuyển lượng khả dụng sang đang giữ**

```javascript
tx.create(orderRef, order);
...
tx.update(row.batchRef, {
  availableQuantity: batch.availableQuantity - item.quantity,
  reservedQuantity: batch.reservedQuantity + item.quantity,
  status: 'RESERVED_PARTIAL',
  ...
});
```

Nguồn: functions/commerce.js, writeOrder(), dòng 222 và 243; checkout() mở transaction ở dòng 295.

Đoạn ghi nằm trong writeOrder(tx, ...), được checkout() gọi bên trong db.runTransaction. Sau khi kiểm tra lại dữ liệu, backend tạo đơn và chuyển lượng khách đặt từ khả dụng sang đang giữ trong cùng giao dịch. Nếu transaction thất bại thì không commit một phần; lượng đang giữ chưa được coi là đã bán hoặc đã giao.

**Mã nguồn 5.3.7. Foreground service gửi vị trí điểm giải cứu di động**

```java
private void publish(Location fix) {
    ...
    if (System.currentTimeMillis() >= endAt) {
        stopSharing(); return;
    }
    ...
    repository.updateMobileLocation(
        campaignId, fix.getLatitude(), fix.getLongitude(), true,
        new CampaignRepository.CampaignCallback() {
            ...
        });
}
```

Nguồn: service/location/MobileLocationService.java, publish(), từ dòng 78; lời gọi ở dòng 86.

Service kiểm tra điều kiện chia sẻ rồi gửi tọa độ hiện tại của Campaign qua Repository. Các kiểm tra chất lượng, tuổi fix và tiết chế tần suất được lược trong trích đoạn. Backend bản đồ phát điểm đủ điều kiện qua SSE; vị trí này thuộc điểm giải cứu, không tự trở thành GPS vận chuyển của từng đơn.

**Mã nguồn 5.3.8. Backend phát cập nhật bản đồ qua SSE**

```javascript
const send = value => {
  ...
  if (!res.write('data: ' + JSON.stringify(value) + '\n\n')
      || !value.live) finish();
};
release = hub.subscribe(send);
res.status(200).set({
  'Content-Type': 'text/event-stream; charset=utf-8',
  ...
});
```

Nguồn: functions/rescue-map.js, createRescueMapApi(), từ dòng 84.

Kết nối người xem đăng ký với hub dùng chung; mỗi bản cập nhật được ghi ra response theo định dạng SSE. Content-Type giữ đường truyền sự kiện cho bản đồ, còn việc lấy GPS thực hiện ở thiết bị Seller; stream chỉ truyền những điểm mà backend đã lọc đủ điều kiện công khai.

**Mã nguồn 5.3.9. Bình luận lấy danh tính từ hồ sơ và lưu qua transaction**

```javascript
const author = identity(profile.data(), b.anonymous === true),
      ref = db.collection('campaigns').doc(b.campaignId)
              .collection('comments').doc(b.commentId);
...
tx.set(ref, {
  id: b.commentId, campaignId: b.campaignId, userId: caller.uid,
  status: 'ACTIVE', content: b.content.trim(),
  senderName: author.name, senderAvatarUrl: author.avatarUrl,
  anonymous: author.anonymous, imageUrls: images,
  ...
});
```

Nguồn: functions/campaign-comments.js, endpoint /campaigns/comments/add, từ dòng 32 và 42.

Backend lấy tên và avatar từ hồ sơ đã đọc, áp dụng lựa chọn ẩn danh rồi ghi bình luận theo commentId. UID người gửi vẫn được giữ để kiểm tra sở hữu và kiểm duyệt. Các bước kiểm tra độ dài nội dung, tối đa ba ảnh, quyền và chống ghi lặp nằm trước hoặc bao quanh đoạn ghi được trích.

**Mã nguồn 5.3.10. Kiểm duyệt Campaign theo chuyển trạng thái được phép**

```java
case CAMPAIGNS:
    if (status.equals("PENDING_APPROVAL"))
        return list("ACTIVE", "REJECTED");
    return status.equals("ACTIVE") ? list("STOPPED") : none();
```

Nguồn: service/admin/AdminTransitionPolicy.java, allowedTargets(), từ dòng 25.

Policy xác định trạng thái đích hợp lệ: Campaign chờ duyệt có thể được duyệt hoặc từ chối; Campaign đang hoạt động có thể bị dừng. Đây là bước kiểm tra quy tắc, không phải lệnh ghi Firestore. Repository/API còn phải xác minh quyền Admin và yêu cầu lý do ở hành động tương ứng.

**Mã nguồn 5.3.11. Hũ phân bổ chuyến và giữ đồng thời tiền với hàng**

```javascript
tx.create(jobRef, job);
tx.update(batchRef, {
  availableQuantity: round(batch.availableQuantity - quantity),
  reservedQuantity: round(batch.reservedQuantity + quantity),
  ...
});
tx.update(fundRef, {
  available: config.available - amount,
  reserved: (config.reserved || 0) + amount,
  ...
});
```

Nguồn: functions/rescue-fund.js, allocation(), transaction từ dòng 296, phần ghi từ dòng 337.

Sau khi kiểm tra lô và ngân sách, transaction tạo chuyến rồi giảm lượng hàng/tiền khả dụng, tăng phần đang giữ tương ứng. quantity là lượng hàng phân bổ và amount là ngân sách giữ cho chuyến. Bước này chưa trả tiền Seller; VERIFY và PAY thực hiện sau theo bằng chứng và khoản chuyển ngoài ứng dụng.
