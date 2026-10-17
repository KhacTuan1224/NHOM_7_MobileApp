package com.rescuefarm.data.remote.firebase;
import androidx.lifecycle.*;import com.google.firebase.Timestamp;import com.google.firebase.auth.FirebaseAuth;import com.google.firebase.firestore.*;import com.rescuefarm.data.repository.EngagementRepository;import com.rescuefarm.domain.enums.*;import com.rescuefarm.domain.model.*;import com.rescuefarm.service.engagement.ReviewEligibilityService;import com.rescuefarm.service.network.NetworkStatusProvider;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
public class FirebaseEngagementRepository implements EngagementRepository{
 private static final String FAVORITES="favorites",REVIEWS="reviews",REPORTS="reports",NOTIFICATIONS="notifications",ORDERS="orders",ITEMS="items",USERS="users",DEVICES="devices";
 private final FirebaseFirestore db=FirebaseFirestore.getInstance();private final FirebaseAuth auth=FirebaseAuth.getInstance();private final NetworkStatusProvider network;private final ReviewEligibilityService eligibility=new ReviewEligibilityService();
 private final MutableLiveData<List<Favorite>> favorites=new MutableLiveData<>(new ArrayList<>());private final MutableLiveData<List<Review>> reviews=new MutableLiveData<>(new ArrayList<>());private final MutableLiveData<List<Report>> reports=new MutableLiveData<>(new ArrayList<>());private final MutableLiveData<List<Notification>> notifications=new MutableLiveData<>(new ArrayList<>());
 public FirebaseEngagementRepository(NetworkStatusProvider n){network=n;}public LiveData<List<Favorite>> observeFavorites(){return favorites;}public LiveData<List<Review>> observeProductReviews(){return reviews;}public LiveData<List<Report>> observeReports(){return reports;}public LiveData<List<Notification>> observeNotifications(){return new LiveData<List<Notification>>() {
  private ListenerRegistration listener;
  protected void onActive(){String uid=auth.getCurrentUser()==null?"":auth.getCurrentUser().getUid();
   if(uid.isEmpty()){postValue(Collections.emptyList());return;}
   listener=db.collection(NOTIFICATIONS).whereEqualTo("userId",uid).addSnapshotListener((snapshot,error)->{
    if(error!=null||snapshot==null)return; List<Notification> values=new ArrayList<>();
    for(DocumentSnapshot d:snapshot.getDocuments())values.add(mapNotification(d));
    values.sort((a,b)->Long.compare(time(b.getCreatedAt()),time(a.getCreatedAt())));postValue(values);
   });
  }
  protected void onInactive(){if(listener!=null)listener.remove();listener=null;}
 };}
 public LiveData<Boolean> observeFavorite(String customerId,String productId){return new LiveData<Boolean>(){private ListenerRegistration registration;@Override protected void onActive(){if(clean(customerId).isEmpty()||clean(productId).isEmpty()){postValue(false);return;}String id=Favorite.key(customerId,productId);registration=db.collection(FAVORITES).document(id).addSnapshotListener((snapshot,error)->{if(error!=null||snapshot==null){postValue(null);return;}postValue(snapshot.exists()&&customerId.equals(snapshot.getString("customerId")));});}@Override protected void onInactive(){if(registration!=null){registration.remove();registration=null;}}};}
 public void refreshFavorites(String uid,ActionCallback cb){query(FAVORITES,"customerId",uid,s->{List<Favorite> out=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())out.add(mapFavorite(d));favorites.setValue(out);cb.onSuccess();},cb);}
 public void toggleFavorite(String uid,String productId,FavoriteCallback cb){if(!online(cb)||!session(uid,cb))return;Favorite f;try{f=Favorite.create(uid,productId,new Date());}catch(Exception e){cb.onError(ErrorCode.VALIDATION,e.getMessage());return;}DocumentReference ref=db.collection(FAVORITES).document(f.getId());db.runTransaction(tx->{DocumentSnapshot d=tx.get(ref);if(d.exists()){tx.delete(ref);return false;}tx.set(ref,favoriteMap(f));return true;}).addOnSuccessListener(cb::onSuccess).addOnFailureListener(e->fail(e,cb));}
 public void refreshProductReviews(String productId,ActionCallback cb){if(!online(cb))return;db.collection(REVIEWS).whereEqualTo("productId",productId).whereEqualTo("status",ReviewStatus.PUBLISHED.name()).get().addOnSuccessListener(s->{List<Review> out=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())out.add(mapReview(d));reviews.setValue(out);cb.onSuccess();}).addOnFailureListener(e->fail(e,cb));}
 public void saveReview(Review r, boolean update, ReviewCallback cb) {
  if(!online(cb))return;
  if(r==null){cb.onError(ErrorCode.VALIDATION,"Review không hợp lệ.");return;}
  if(!session(r.getCustomerId(),cb))return;
  DocumentReference ref=db.collection(REVIEWS).document(r.getId());
  List<String> previousImages=new ArrayList<>();
  db.runTransaction(tx->{
   DocumentSnapshot existing=tx.get(ref);
   DocumentSnapshot order=tx.get(db.collection(ORDERS).document(r.getOrderId()));
   DocumentSnapshot item=tx.get(order.getReference().collection(ITEMS).document(r.getOrderItemId()));
   DocumentSnapshot product=tx.get(db.collection("products").document(r.getProductId()));
   previousImages.clear(); if(existing.exists()) {
    Object old=existing.get("imageUrls"); if(old instanceof List<?>)for(Object value:(List<?>)old)
     if(value instanceof String)previousImages.add((String)value);
   }
   if(!order.exists()||!item.exists())throw new IllegalStateException("ORDER_ITEM_NOT_FOUND");
   eligibility.requireEligible(r.getCustomerId(),order.getString("ownerId"),
    enumValue(OrderOwnerType.class,order.getString("ownerType"),OrderOwnerType.GUEST),
    enumValue(OrderStatus.class,order.getString("status"),OrderStatus.PENDING),r.getProductId(),item.getString("productId"));
   boolean withdrawn=existing.exists()&&"DELETED".equals(existing.getString("status"));
   if(!update&&existing.exists()&&!withdrawn)throw new IllegalStateException("DUPLICATE_REVIEW");
   if(update&&!existing.exists())throw new IllegalStateException("REVIEW_NOT_FOUND");
   tx.set(ref,reviewMap(r,existing),SetOptions.merge());
   FirebaseReviewSummary.update(tx,product,existing,r.getId(),r.getRating());
   return r;
  }).addOnSuccessListener(saved->{
   com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(previousImages,saved.getImageUrls()); cb.onSuccess(saved);
  }).addOnFailureListener(e->fail(e,cb));
 }
 public void deleteReview(String id, ActionCallback cb) {
  if(!online(cb))return; List<String> previousImages=new ArrayList<>();
  db.runTransaction(tx->{
   DocumentSnapshot review=tx.get(db.collection(REVIEWS).document(id));
   if(!review.exists())throw new IllegalStateException("REVIEW_NOT_FOUND");
   String uid=auth.getCurrentUser()==null?"":auth.getCurrentUser().getUid();
   if(!uid.equals(review.getString("customerId")))throw new IllegalStateException("REVIEW_FORBIDDEN");
   if("DELETED".equals(review.getString("status")))return false;
   DocumentSnapshot product=tx.get(db.collection("products").document(review.getString("productId")));
   previousImages.clear(); Object raw=review.get("imageUrls");
   if(raw instanceof List<?>)for(Object value:(List<?>)raw)if(value instanceof String)previousImages.add((String)value);
   tx.update(review.getReference(),"status","DELETED","imageUrls",Collections.emptyList(),"imagePublicIds",Collections.emptyList(),"updatedAt",FieldValue.serverTimestamp());
   FirebaseReviewSummary.update(tx,product,review,id,0); return true;
  }).addOnSuccessListener(v->{
   com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(previousImages,Collections.emptyList());cb.onSuccess();
  }).addOnFailureListener(e->fail(e,cb));
 }
 public void createReport(String uid,ReportTargetType type,String targetId,String reason,String description,ReportCallback cb){if(!online(cb)||!session(uid,cb))return;DocumentReference ref=db.collection(REPORTS).document();try{String path="",parentType="",parentId="",canonicalId=targetId;
if(type==ReportTargetType.COMMENT){String[] parts=targetId.split("/",-1);if(parts.length!=4||!(parts[0].equals("campaigns")||parts[0].equals("posts"))||!parts[2].equals("comments")||parts[1].isEmpty()||parts[3].isEmpty())throw new IllegalArgumentException("Cần đường dẫn campaigns/{id}/comments/{id} hoặc posts/{id}/comments/{id}.");path=targetId;parentType=parts[0].equals("campaigns")?"CAMPAIGN":"POST";parentId=parts[1];canonicalId=parts[3];}
Report r=Report.create(ref.getId(),uid,type,canonicalId,reason,description,new Date());Map<String,Object> data=reportMap(r);if(type==ReportTargetType.COMMENT){data.put("targetPath",path);data.put("parentType",parentType);data.put("parentId",parentId);}com.google.firebase.firestore.WriteBatch batch=db.batch();batch.set(ref,data);com.rescuefarm.service.notification.NotificationOutbox.enqueue(batch,db,"ADMINS","REPORT",r.getId());batch.commit().addOnSuccessListener(v->{cb.onSuccess(r);}).addOnFailureListener(e->fail(e,cb));}catch(Exception e){cb.onError(ErrorCode.VALIDATION,e.getMessage());}}
 public void refreshReports(ActionCallback cb){if(!online(cb))return;db.collection(REPORTS).get().addOnSuccessListener(s->{List<Report> out=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())out.add(mapReport(d));reports.setValue(out);cb.onSuccess();}).addOnFailureListener(e->fail(e,cb));}
 public void transitionReport(String id,ReportStatus target,String note,ReportCallback cb){if(!online(cb))return;if(target==null){cb.onError(ErrorCode.VALIDATION,"Trạng thái report không hợp lệ.");return;}String admin=auth.getCurrentUser()==null?"":auth.getCurrentUser().getUid();DocumentReference ref=db.collection(REPORTS).document(id);db.runTransaction(tx->{DocumentSnapshot d=tx.get(ref);if(!d.exists())throw new IllegalStateException("REPORT_NOT_FOUND");Report r=mapReport(d);if(target==ReportStatus.IN_REVIEW)r.startReview(admin);else if(target==ReportStatus.RESOLVED)r.resolve(admin,note);else if(target==ReportStatus.REJECTED)r.reject(admin,note);else throw new IllegalStateException("REPORT_TRANSITION_INVALID");tx.update(ref,"status",r.getStatus().name(),"resolvedBy",r.getResolvedBy(),"resolutionNote",clean(r.getResolutionNote()),"updatedAt",FieldValue.serverTimestamp());java.util.Map<String,Object> audit=new java.util.HashMap<>();audit.put("adminId",admin);audit.put("resourceType","REPORTS");audit.put("resourceId",id);audit.put("fromState",d.getString("status"));audit.put("toState",r.getStatus().name());audit.put("reason",clean(note));audit.put("createdAt",FieldValue.serverTimestamp());tx.set(db.collection("adminAuditLogs").document(),audit);return r;}).addOnSuccessListener(cb::onSuccess).addOnFailureListener(e->fail(e,cb));}
 public void refreshNotifications(String uid,ActionCallback cb){query(NOTIFICATIONS,"userId",uid,s->{List<Notification> out=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())out.add(mapNotification(d));out.sort((a,b)->Long.compare(time(b.getCreatedAt()),time(a.getCreatedAt())));notifications.setValue(out);cb.onSuccess();},cb);}
 public void markNotificationRead(String id,ActionCallback cb){if(!online(cb))return;DocumentReference ref=db.collection(NOTIFICATIONS).document(id);db.runTransaction(tx->{DocumentSnapshot d=tx.get(ref);if(!d.exists())throw new IllegalStateException("NOTIFICATION_NOT_FOUND");if(Boolean.TRUE.equals(d.getBoolean("isRead")))return true;tx.update(ref,"isRead",true,"readAt",FieldValue.serverTimestamp());return false;}).addOnSuccessListener(v->cb.onSuccess()).addOnFailureListener(e->fail(e,cb));}
 public void registerFcmToken(String uid,String token,ActionCallback cb){if(!online(cb))return;if(!session(uid,cb))return;if(clean(token).isEmpty()){cb.onError(ErrorCode.TOKEN,"FCM không trả về token thiết bị hợp lệ.");return;}Map<String,Object> m=new HashMap<>();m.put("userId",uid);m.put("token",token);m.put("platform","ANDROID");m.put("updatedAt",FieldValue.serverTimestamp());db.collection(USERS).document(uid).collection(DEVICES).document(hash(token)).set(m).addOnSuccessListener(v->cb.onSuccess()).addOnFailureListener(e->fail(e,cb));}
 private void query(String collection,String field,String value,com.google.android.gms.tasks.OnSuccessListener<QuerySnapshot> ok,ActionCallback cb){if(!online(cb))return;db.collection(collection).whereEqualTo(field,value).get().addOnSuccessListener(ok).addOnFailureListener(e->fail(e,cb));}
 private Map<String,Object> favoriteMap(Favorite f){Map<String,Object>m=new HashMap<>();m.put("id",f.getId());m.put("customerId",f.getCustomerId());m.put("productId",f.getProductId());m.put("createdAt",FieldValue.serverTimestamp());return m;}
 private Map<String,Object> reviewMap(Review r,DocumentSnapshot old){Map<String,Object>m=new HashMap<>();m.put("id",r.getId());m.put("customerId",r.getCustomerId());m.put("productId",r.getProductId());m.put("orderId",r.getOrderId());m.put("orderItemId",r.getOrderItemId());m.put("rating",r.getRating());m.put("content",r.getContent());m.put("imageUrls",r.getImageUrls());m.put("status",ReviewStatus.PUBLISHED.name());m.put("createdAt",old!=null&&old.exists()?old.get("createdAt"):FieldValue.serverTimestamp());m.put("updatedAt",FieldValue.serverTimestamp());return m;}
 private Map<String,Object> reportMap(Report r){Map<String,Object>m=new HashMap<>();m.put("id",r.getId());m.put("reporterId",r.getReporterId());m.put("targetType",r.getTargetType().name());m.put("targetId",r.getTargetId());m.put("reason",r.getReason());m.put("description",r.getDescription());m.put("status",r.getStatus().name());m.put("resolvedBy","");m.put("resolutionNote","");m.put("createdAt",FieldValue.serverTimestamp());m.put("updatedAt",FieldValue.serverTimestamp());return m;}
 private Favorite mapFavorite(DocumentSnapshot d){return Favorite.restore(d.getId(),d.getString("customerId"),d.getString("productId"),date(d,"createdAt"));}private Review mapReview(DocumentSnapshot d){List<String>images=new ArrayList<>();Object raw=d.get("imageUrls");if(raw instanceof List<?>)for(Object x:(List<?>)raw)if(x instanceof String)images.add((String)x);return Review.restore(d.getId(),d.getString("customerId"),d.getString("productId"),d.getString("orderId"),d.getString("orderItemId"),(int)lng(d,"rating"),d.getString("content"),images,enumValue(ReviewStatus.class,d.getString("status"),ReviewStatus.PUBLISHED),date(d,"createdAt"));}
 private Report mapReport(DocumentSnapshot d){return Report.restore(d.getId(),d.getString("reporterId"),enumValue(ReportTargetType.class,d.getString("targetType"),ReportTargetType.USER),d.getString("targetId"),d.getString("reason"),d.getString("description"),enumValue(ReportStatus.class,d.getString("status"),ReportStatus.PENDING),d.getString("resolvedBy"),d.getString("resolutionNote"),date(d,"createdAt"));}private Notification mapNotification(DocumentSnapshot d){return Notification.restore(d.getId(),d.getString("userId"),enumValue(NotificationType.class,d.getString("type"),NotificationType.SYSTEM),d.getString("title"),d.getString("body"),d.getString("referenceId"),Boolean.TRUE.equals(d.getBoolean("isRead")),date(d,"createdAt"));}
 private boolean session(String uid,Object callback){if(auth.getCurrentUser()!=null&&uid.equals(auth.getCurrentUser().getUid()))return true;error(callback,ErrorCode.UNAUTHENTICATED,"Phiên đăng nhập không hợp lệ.");return false;}private boolean online(Object cb){if(network.isOnline())return true;error(cb,ErrorCode.NETWORK,"Cần kết nối mạng.");return false;}private void fail(Exception e,Object cb){String m=e.getMessage()==null?"":e.getMessage();if(m.contains("DUPLICATE"))error(cb,ErrorCode.DUPLICATE,"Bạn đã thực hiện thao tác này trước đó.");else if(m.contains("NOT_FOUND"))error(cb,ErrorCode.NOT_FOUND,"Không tìm thấy dữ liệu liên quan.");else if(m.contains("FORBIDDEN")||m.contains("DELIVERED")||m.contains("MISMATCH"))error(cb,ErrorCode.FORBIDDEN,"Chỉ khách đã nhận đúng sản phẩm mới được review.");else if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED)error(cb,ErrorCode.FORBIDDEN,"Firestore Rules từ chối thao tác.");else error(cb,ErrorCode.UNKNOWN,"Không thể hoàn tất thao tác.");}
 private void error(Object cb,ErrorCode c,String m){if(cb instanceof ActionCallback)((ActionCallback)cb).onError(c,m);else if(cb instanceof FavoriteCallback)((FavoriteCallback)cb).onError(c,m);else if(cb instanceof ReviewCallback)((ReviewCallback)cb).onError(c,m);else if(cb instanceof ReportCallback)((ReportCallback)cb).onError(c,m);}
 private static Date date(DocumentSnapshot d,String f){Timestamp t=d.getTimestamp(f);return t==null?null:t.toDate();}private static long lng(DocumentSnapshot d,String f){Long x=d.getLong(f);return x==null?0:x;}private static long time(Date d){return d==null?0:d.getTime();}private static String clean(String x){return x==null?"":x.trim();}private static <T extends Enum<T>>T enumValue(Class<T>c,String v,T f){try{return Enum.valueOf(c,v==null?"":v);}catch(Exception e){return f;}}private static String hash(String s){try{byte[]b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format("%02x",v));return x.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
}
