package com.rescuefarm.data.repository;
import androidx.lifecycle.LiveData;import com.rescuefarm.domain.enums.*;import com.rescuefarm.domain.model.*;import java.util.List;
public interface EngagementRepository{
 enum ErrorCode{UNAUTHENTICATED,FORBIDDEN,NOT_FOUND,DUPLICATE,VALIDATION,NETWORK,TOKEN,UNKNOWN}
 interface ActionCallback{void onSuccess();void onError(ErrorCode code,String message);}
 interface FavoriteCallback{void onSuccess(boolean favorite);void onError(ErrorCode code,String message);}
 interface ReviewCallback{void onSuccess(Review review);void onError(ErrorCode code,String message);}
 interface ReportCallback{void onSuccess(Report report);void onError(ErrorCode code,String message);}
 LiveData<List<Favorite>> observeFavorites();LiveData<Boolean> observeFavorite(String customerId,String productId);LiveData<List<Review>> observeProductReviews();LiveData<List<Report>> observeReports();LiveData<List<Notification>> observeNotifications();
 void refreshFavorites(String customerId,ActionCallback callback);void toggleFavorite(String customerId,String productId,FavoriteCallback callback);
 void refreshProductReviews(String productId,ActionCallback callback);void saveReview(Review review,boolean update,ReviewCallback callback);void deleteReview(String reviewId,ActionCallback callback);
 void createReport(String reporterId,ReportTargetType type,String targetId,String reason,String description,ReportCallback callback);
 void refreshReports(ActionCallback callback);void transitionReport(String reportId,ReportStatus target,String resolutionNote,ReportCallback callback);
 void refreshNotifications(String userId,ActionCallback callback);void markNotificationRead(String notificationId,ActionCallback callback);void registerFcmToken(String userId,String token,ActionCallback callback);
 default void close(){}
}
