package com.rescuefarm.data.repository;

import com.rescuefarm.service.seller.SellerStatisticsSnapshot;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;

/** Statistics are aggregated by the backend independently of the order list's pagination/cache. */
public class SellerStatisticsRepository {
    public interface Callback { void onSuccess(SellerStatisticsSnapshot snapshot); void onError(String message); }
    public void load(int days, Callback callback) {
        try {
            RenderBackendClient.post("/seller/statistics", new JSONObject().put("days", days), new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject body) {
                    try { callback.onSuccess(new SellerStatisticsSnapshot(body)); }
                    catch (RuntimeException error) { callback.onError("Dữ liệu thống kê không hợp lệ. Hãy thử tải lại."); }
                }
                @Override public void onError(String message) { callback.onError(message); }
            });
        } catch (org.json.JSONException error) { callback.onError("Kỳ thống kê không hợp lệ."); }
    }
}
