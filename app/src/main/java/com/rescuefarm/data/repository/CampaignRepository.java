package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.model.Banner;

import java.util.List;
import java.util.Collections;

public interface CampaignRepository {
    enum ErrorCode { NOT_CONFIGURED, NOT_FOUND, FORBIDDEN, VALIDATION, NETWORK, CONFLICT, UNKNOWN }
    enum ReservationMutation { RESERVE, COMMIT, RELEASE }

    interface ActionCallback {
        void onSuccess();
        void onError(ErrorCode error, String message);
    }
    interface CampaignCallback {
        void onSuccess(RescueCampaign campaign);
        void onError(ErrorCode error, String message);
    }

    LiveData<List<RescueCampaign>> observeActiveCampaigns();
    default LiveData<List<Banner>> observeActiveBanners() {
        return new MutableLiveData<>(Collections.emptyList());
    }
    LiveData<List<RescueCampaign>> observeSellerCampaigns(String sellerId);
    default LiveData<RescueCampaign> observeCampaign(String campaignId) {
        return new androidx.lifecycle.MutableLiveData<>();
    }
    void refreshActiveCampaigns(ActionCallback callback);
    default void refreshBanners(ActionCallback callback) { callback.onSuccess(); }
    void refreshSellerCampaigns(String sellerId, ActionCallback callback);
    default void refreshSellerCampaignPage(String sellerId, boolean reset, ActionCallback callback) {
        refreshSellerCampaigns(sellerId, callback);
    }
    default boolean hasMoreSellerCampaigns(String sellerId) { return false; }
    void getCampaign(String campaignId, CampaignCallback callback);
    void saveCampaign(RescueCampaign campaign, boolean submitForApproval,
            CampaignCallback callback);
    void updateMobileLocation(String campaignId, double latitude, double longitude,
            boolean sharingEnabled, CampaignCallback callback);
    void mutateReservation(String campaignId, String batchId, long expectedInventoryVersion,
            ReservationMutation mutation, double quantity, CampaignCallback callback);
    default void close() { }
}
