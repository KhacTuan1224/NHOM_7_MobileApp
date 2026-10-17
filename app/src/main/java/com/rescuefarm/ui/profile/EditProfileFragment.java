package com.rescuefarm.ui.profile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.text.TextWatcher;
import android.text.Editable;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.rescuefarm.ui.navigation.ActorUiPolicy;
import com.bumptech.glide.Glide;
import java.util.Map;
import java.util.Locale;

public class EditProfileFragment extends Fragment {
    private ProfileViewModel viewModel;
    private TextInputEditText nameInput, phoneInput, avatarInput;
    private TextView locationView, sellerLocationTitle, sellerLocationDescription, mapCaption;
    private View openGoogleMapsButton;
    private View businessSection;
    private android.widget.EditText companyInput, taxInput;
    private boolean businessCustomer;
    private View mapContainer;
    private WebView locationMap;
    private TextView mapStatus;
    private final Handler mapHandler = new Handler(Looper.getMainLooper());
    private int mapGeneration;
    private double mappedLatitude = Double.NaN, mappedLongitude = Double.NaN;
    private ImageView avatarPreview;
    private TextView avatarStatus;
    private View uploadAvatarButton;
    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::uploadProfileAvatar);
    private double latitude, longitude;
    private boolean dirty, binding;
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        nameInput = view.findViewById(R.id.editFullNameInput);
        phoneInput = view.findViewById(R.id.editPhoneInput);
        avatarInput = view.findViewById(R.id.editAvatarInput);
        avatarPreview = view.findViewById(R.id.editAvatarPreview);
        avatarStatus = view.findViewById(R.id.avatarUploadStatus);
        uploadAvatarButton = view.findViewById(R.id.uploadAvatarButton);
        locationView = view.findViewById(R.id.profileLocationText);
        sellerLocationTitle = view.findViewById(R.id.sellerLocationTitle);
        sellerLocationDescription = view.findViewById(R.id.sellerLocationDescription);
        mapCaption = view.findViewById(R.id.profileMapCaption);
        openGoogleMapsButton = view.findViewById(R.id.openGoogleMapsButton);
        boolean sellerMap = ActorUiPolicy.isSeller(this);
        sellerLocationTitle.setVisibility(View.GONE);
        sellerLocationDescription.setVisibility(View.GONE);
        mapCaption.setVisibility(View.GONE);
        openGoogleMapsButton.setVisibility(sellerMap ? View.GONE : View.VISIBLE);
        locationView.setVisibility(sellerMap ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.useProfileLocationButton).setVisibility(sellerMap ? View.GONE : View.VISIBLE);
        openGoogleMapsButton.setOnClickListener(v -> openGoogleMaps());
        mapContainer = view.findViewById(R.id.profileMapContainer);
        locationMap = view.findViewById(R.id.profileLocationMap);
        mapStatus = view.findViewById(R.id.profileMapStatus);
        mapStatus.setOnClickListener(v -> {
            mappedLatitude = Double.NaN;
            showMap(latitude, longitude);
        });
        configureMap();
        android.widget.LinearLayout business = new android.widget.LinearLayout(requireContext()); business.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView heading = new android.widget.TextView(requireContext()); heading.setText("Thông tin khách hàng doanh nghiệp"); business.addView(heading);
        companyInput = new android.widget.EditText(requireContext()); companyInput.setHint("Tên doanh nghiệp");
        companyInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        taxInput = new android.widget.EditText(requireContext()); taxInput.setHint("Mã số thuế");
        taxInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        business.addView(companyInput); business.addView(taxInput); businessSection = business; business.setVisibility(View.GONE);
        android.widget.LinearLayout form = (android.widget.LinearLayout)view.findViewById(R.id.saveProfileButton).getParent();
        form.addView(business, 1);
        if(state!=null && state.containsKey("profileDirty")) {
            binding=true; dirty=state.getBoolean("profileDirty"); businessCustomer=state.getBoolean("businessCustomer");
            companyInput.setText(state.getString("companyName","")); taxInput.setText(state.getString("taxCode",""));
            nameInput.setText(state.getString("fullName","")); phoneInput.setText(state.getString("phone","")); avatarInput.setText(state.getString("avatar",""));
            latitude=state.getDouble("latitude"); longitude=state.getDouble("longitude");
            businessSection.setVisibility(businessCustomer?View.VISIBLE:View.GONE); binding=false; renderAvatar(avatarInput.getText().toString()); showCoordinates();
        }
        viewModel = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext()))
                .get(ProfileViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        nameInput.addTextChangedListener(watcher);
        phoneInput.addTextChangedListener(watcher);
        avatarInput.addTextChangedListener(watcher); companyInput.addTextChangedListener(watcher); taxInput.addTextChangedListener(watcher);
        viewModel.observeProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                businessCustomer=user instanceof com.rescuefarm.domain.model.Customer && ((com.rescuefarm.domain.model.Customer)user).getCustomerType()==com.rescuefarm.domain.enums.CustomerType.BUSINESS;
                businessSection.setVisibility(businessCustomer?View.VISIBLE:View.GONE);
                if(!dirty)populate(user);
            }
        });
        view.findViewById(R.id.useProfileLocationButton).setOnClickListener(v -> requestLocation());
        uploadAvatarButton.setOnClickListener(v -> avatarPicker.launch("image/*"));
        view.findViewById(R.id.saveProfileButton).setOnClickListener(v -> {
            if (businessCustomer) viewModel.updateBusinessProfile(text(nameInput), text(phoneInput), text(avatarInput), latitude, longitude,
                    companyInput.getText().toString().trim(), taxInput.getText().toString().trim());
            else viewModel.updateProfile(text(nameInput), text(phoneInput), text(avatarInput), latitude, longitude);
        });
        viewModel.loadProfile();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean("profileDirty",dirty); out.putBoolean("businessCustomer",businessCustomer);
        if(companyInput!=null)out.putString("companyName",companyInput.getText().toString()); if(taxInput!=null)out.putString("taxCode",taxInput.getText().toString());
        if(nameInput!=null)out.putString("fullName",text(nameInput)); if(phoneInput!=null)out.putString("phone",text(phoneInput)); if(avatarInput!=null)out.putString("avatar",text(avatarInput));
        out.putDouble("latitude",latitude);out.putDouble("longitude",longitude);
    }

    private void render(ProfileScreenState state) {
        if (state.getStatus() == ProfileScreenState.Status.PROFILE && !dirty) {
            populate(state.getUser());
        } else if (state.getStatus() == ProfileScreenState.Status.LOCATION) {
            latitude = state.getLatitude(); longitude = state.getLongitude(); showCoordinates();
        } else if (state.getStatus() == ProfileScreenState.Status.SAVED) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
        } else if (state.getStatus() == ProfileScreenState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void populate(User user) {
        binding = true;
        nameInput.setText(user.getFullName()); phoneInput.setText(user.getPhone());
        avatarInput.setText(user.getAvatarUrl()); latitude = user.getLatitude(); longitude = user.getLongitude();
        businessCustomer = user instanceof com.rescuefarm.domain.model.Customer
                && ((com.rescuefarm.domain.model.Customer)user).getCustomerType() == com.rescuefarm.domain.enums.CustomerType.BUSINESS;
        businessSection.setVisibility(businessCustomer ? View.VISIBLE : View.GONE);
        if (businessCustomer) {
            com.rescuefarm.domain.model.Customer customer = (com.rescuefarm.domain.model.Customer)user;
            companyInput.setText(customer.getCompanyName()); taxInput.setText(customer.getTaxCode());
        }
        renderAvatar(user.getAvatarUrl());
        binding = false;
        showCoordinates();
    }

    private void requestLocation() {
        if (hasLocationPermission()) viewModel.requestCurrentLocation(false);
        else permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void onPermissionResult(Map<String, Boolean> result) {
        if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            viewModel.requestCurrentLocation(false);
        } else viewModel.onLocationPermissionDenied();
    }
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void showCoordinates() {
        if (ActorUiPolicy.isSeller(this)) { mapContainer.setVisibility(View.GONE); return; }
        locationView.setText(getString(R.string.coordinates_format, latitude, longitude));
        if (ActorUiPolicy.isSeller(this)) {
            mapCaption.setText("Vị trí GPS hồ sơ" + System.lineSeparator()
                    + "Vĩ độ " + String.format(Locale.US, "%.6f", latitude)
                    + " · Kinh độ " + String.format(Locale.US, "%.6f", longitude));
        }
        openGoogleMapsButton.setEnabled(Double.isFinite(latitude) && Double.isFinite(longitude)
                && (latitude != 0D || longitude != 0D));
        showMap(latitude, longitude);
    }

    private void openGoogleMaps() {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || (latitude == 0D && longitude == 0D)) return;
        Uri location = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                + latitude + "%2C" + longitude);
        startActivity(new Intent(Intent.ACTION_VIEW, location));
    }
    private void configureMap() {
        WebSettings settings = locationMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        String defaultUserAgent = settings.getUserAgentString();
        settings.setUserAgentString(defaultUserAgent + " RescueFarmAndroidApp/1.0 (com.example.nhom_7_mobileapp)");
        locationMap.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        locationMap.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())) {
                    String path = uri.getPath();
                    if ("/maps/leaflet.js".equals(path) || "/maps/leaflet.css".equals(path)) {
                        try {
                            return new WebResourceResponse(path.endsWith(".js") ? "text/javascript" : "text/css",
                                    "UTF-8", view.getContext().getAssets().open("maps/leaflet/" + uri.getLastPathSegment()));
                        } catch (java.io.IOException error) {
                            Log.e("ProfileMap", "Missing bundled map library", error);
                            return new WebResourceResponse("text/plain", "UTF-8",
                                    new java.io.ByteArrayInputStream(new byte[0]));
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.w("ProfileMap", "Resource failed: " + request.getUrl().getHost() + ", code=" + error.getErrorCode());
            }
        });
        locationMap.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    Log.e("ProfileMap", message.message());
                return true;
            }
        });
    }

    private void showMap(double lat, double lon) {
        if (ActorUiPolicy.isSeller(this)) { mapContainer.setVisibility(View.GONE); return; }
        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90D || lat > 90D || lon < -180D || lon > 180D
                || (lat == 0D && lon == 0D)) {
            mapContainer.setVisibility(View.GONE);
            mapGeneration++;
            mapHandler.removeCallbacksAndMessages(null);
            mappedLatitude = Double.NaN;
            return;
        }
        mapContainer.setVisibility(View.VISIBLE);
        if (Double.compare(mappedLatitude, lat) == 0 && Double.compare(mappedLongitude, lon) == 0) return;
        mappedLatitude = lat;
        mappedLongitude = lon;
        mapHandler.removeCallbacksAndMessages(null);
        int generation = ++mapGeneration;
        mapStatus.setText(R.string.profile_map_loading);
        mapStatus.setVisibility(View.VISIBLE);
        mapStatus.setEnabled(false);
        String html = mapHtml(lat, lon);
        locationMap.loadDataWithBaseURL("https://appassets.androidplatform.net/maps/", html,
                "text/html", "UTF-8", null);
        long deadline = SystemClock.uptimeMillis() + 12000L;
        mapHandler.postDelayed(() -> checkMapReady(generation, deadline), 300L);
    }

    private void checkMapReady(int generation, long deadline) {
        if (locationMap == null || generation != mapGeneration) return;
        locationMap.evaluateJavascript("Boolean(window.rescueMapReady)", ready -> {
            if (locationMap == null || generation != mapGeneration) return;
            if ("true".equals(ready)) {
                mapStatus.setVisibility(View.GONE);
            } else if (SystemClock.uptimeMillis() >= deadline) {
                mapStatus.setText(R.string.profile_map_retry);
                mapStatus.setEnabled(true);
            } else {
                mapHandler.postDelayed(() -> checkMapReady(generation, deadline), 300L);
            }
        });
    }

    private String mapHtml(double lat, double lon) {
        String latitudeText = String.format(Locale.US, "%.6f", lat);
        String longitudeText = String.format(Locale.US, "%.6f", lon);
        boolean sellerMap = ActorUiPolicy.isSeller(this);
        String tileUrl = sellerMap
                ? "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
                : "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}";
        String attribution = sellerMap
                ? "Sources: Esri, HERE, Garmin, USGS, Intermap, INCREMENT P, NRCan, Esri Japan, METI, Mapwithyou, NOSTRA, &copy; OpenStreetMap contributors"
                : "Sources: <a href=\"https://www.esri.com/\">Esri</a>, HERE, Garmin, USGS, Intermap, INCREMENT P, NRCan, Esri Japan, METI, Mapwithyou, NOSTRA, "
                        + "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>, and the GIS User Community";
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">"
                + "<link rel=\"stylesheet\" href=\"leaflet.css\">"
                + "<style>html,body,#map{height:100%;width:100%;margin:0;background:#f8faf6}"
                + (sellerMap ? ".leaflet-tile{filter:grayscale(.94) brightness(1.08) contrast(.92)}.map-pin{width:26px;height:26px;position:relative;transform:rotate(-45deg);border-radius:50% 50% 50% 0;background:#1769ff;border:3px solid #fff;box-shadow:0 2px 8px #0004}.map-pin span{position:absolute;width:8px;height:8px;left:6px;top:6px;border-radius:50%;background:#fff;transform:rotate(45deg)}" : "")
                + "#status{position:absolute;z-index:1000;inset:0;display:flex;align-items:center;justify-content:center;"
                + "padding:16px;text-align:center;font:14px sans-serif;color:#586159;background:#f8faf6}"
                + ".leaflet-popup-content{font:13px sans-serif}</style></head><body><div id=\"map\"></div>"
                + "<div id=\"status\">" + getString(R.string.profile_map_loading) + "</div>"
                + "<script src=\"leaflet.js\"></script><script>"
                + "const p=[" + latitudeText + "," + longitudeText + "],sellerMap=" + sellerMap + ",statusElement=document.getElementById('status');"
                + "try{const map=L.map('map',{scrollWheelZoom:false}).setView(p,15);"
                + "const pin=L.divIcon({className:'',html:'<div class=map-pin><span></span></div>',iconSize:[32,42],iconAnchor:[16,40]});const marker=sellerMap?L.marker(p,{icon:pin}):L.circleMarker(p,{radius:9,color:'#fff',weight:3,fillColor:'#2e6b45',fillOpacity:1});marker.addTo(map);"
                + "if(!sellerMap)marker.bindPopup('Vĩ độ " + latitudeText + "<br>Kinh độ " + longitudeText + "').openPopup();"
                + "const tiles=L.tileLayer('" + tileUrl + "',{maxZoom:19,"
                + "attribution:'" + attribution + "'}).addTo(map);"
                + "let ready=false;tiles.once('tileload',()=>{ready=true;window.rescueMapReady=true;statusElement.style.display='none'});"
                + "tiles.on('tileerror',()=>{if(!ready)statusElement.textContent='"
                + getString(R.string.profile_map_unavailable) + "'});"
                + "setTimeout(()=>{if(statusElement.style.display!=='none')statusElement.textContent='"
                + getString(R.string.profile_map_unavailable) + "';},10000);"
                + "}catch(e){statusElement.textContent='" + getString(R.string.profile_map_unavailable) + "';}</script></body></html>";
    }
    private void renderAvatar(String url) {
        if (url == null || url.trim().isEmpty()) {
            Glide.with(this).clear(avatarPreview);
            avatarPreview.setImageResource(R.drawable.ic_nav_profile);
            avatarStatus.setText(R.string.no_avatar_selected);
        } else {
            Glide.with(this).load(url.trim()).circleCrop().placeholder(R.drawable.ic_nav_profile)
                    .error(R.drawable.ic_nav_profile).into(avatarPreview);
            avatarStatus.setText(R.string.account_avatar_saved_status);
        }
    }

    private void uploadProfileAvatar(Uri uri) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        CloudinaryUploadUi.upload(this, uri, "avatar", user.getUid(), 0, uploadAvatarButton,
                avatarStatus, avatarPreview, url -> avatarInput.setText(url));
    }

    @Override public void onDestroyView() {
        mapGeneration++;
        mapHandler.removeCallbacksAndMessages(null);
        mappedLatitude = Double.NaN;
        mappedLongitude = Double.NaN;
        if (locationMap != null) {
            locationMap.stopLoading();
            locationMap.loadUrl("about:blank");
            locationMap.setWebViewClient(null);
            locationMap.setWebChromeClient(null);
            locationMap.destroy();
            locationMap = null;
        }
        mapContainer = null;
        mapStatus = null;
        super.onDestroyView();
    }
    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
