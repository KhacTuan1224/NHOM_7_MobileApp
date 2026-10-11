package com.rescuefarm.ui.profile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.bumptech.glide.Glide;
import java.util.Map;
import java.util.Locale;

public class EditProfileFragment extends Fragment {
    private ProfileViewModel viewModel;
    private TextInputEditText nameInput, phoneInput, avatarInput;
    private TextView locationView;
    private View mapContainer;
    private WebView locationMap;
    private ImageView avatarPreview;
    private TextView avatarStatus;
    private View uploadAvatarButton;
    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
                    "avatar", com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(), 0, uploadAvatarButton, avatarStatus, avatarPreview,
                    url -> avatarInput.setText(url)));
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
        mapContainer = view.findViewById(R.id.profileMapContainer);
        locationMap = view.findViewById(R.id.profileLocationMap);
        configureMap();
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
        avatarInput.addTextChangedListener(watcher);
        viewModel.observeProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null && !dirty) populate(user);
        });
        view.findViewById(R.id.useProfileLocationButton).setOnClickListener(v -> requestLocation());
        uploadAvatarButton.setOnClickListener(v -> avatarPicker.launch("image/*"));
        view.findViewById(R.id.saveProfileButton).setOnClickListener(v -> viewModel.updateProfile(
                text(nameInput), text(phoneInput), text(avatarInput), latitude, longitude));
        viewModel.loadProfile();
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
        locationView.setText(getString(R.string.coordinates_format, latitude, longitude));
        showMap(latitude, longitude);
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
        locationMap.setWebViewClient(new WebViewClient());
    }

    private void showMap(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90D || lat > 90D || lon < -180D || lon > 180D
                || (lat == 0D && lon == 0D)) {
            mapContainer.setVisibility(View.GONE);
            return;
        }
        mapContainer.setVisibility(View.VISIBLE);
        String html = mapHtml(lat, lon);
        locationMap.loadDataWithBaseURL("https://www.openstreetmap.org/", html,
                "text/html", "UTF-8", null);
    }

    private String mapHtml(double lat, double lon) {
        String latitudeText = String.format(Locale.US, "%.6f", lat);
        String longitudeText = String.format(Locale.US, "%.6f", lon);
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">"
                + "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\">"
                + "<style>html,body,#map{height:100%;width:100%;margin:0;background:#f8faf6}"
                + "#status{position:absolute;z-index:1000;inset:0;display:flex;align-items:center;justify-content:center;"
                + "padding:16px;text-align:center;font:14px sans-serif;color:#586159;background:#f8faf6}"
                + ".leaflet-popup-content{font:13px sans-serif}</style></head><body><div id=\"map\"></div>"
                + "<div id=\"status\">" + getString(R.string.profile_map_loading) + "</div>"
                + "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script><script>"
                + "const p=[" + latitudeText + "," + longitudeText + "],status=document.getElementById('status');"
                + "try{const map=L.map('map',{scrollWheelZoom:false}).setView(p,15);"
                + "const marker=L.circleMarker(p,{radius:9,color:'#fff',weight:3,fillColor:'#2e6b45',fillOpacity:1}).addTo(map);"
                + "marker.bindPopup('Vĩ độ " + latitudeText + "<br>Kinh độ " + longitudeText + "').openPopup();"
                + "const tiles=L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',{maxZoom:19,"
                + "attribution:'Sources: <a href=\"https://www.esri.com/\">Esri</a>, HERE, Garmin, USGS, Intermap, INCREMENT P, NRCan, Esri Japan, METI, Mapwithyou, NOSTRA, "
                + "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>, and the GIS User Community'}).addTo(map);"
                + "let ready=false;tiles.once('tileload',()=>{ready=true;status.style.display='none'});"
                + "tiles.on('tileerror',()=>{if(!ready)status.textContent='"
                + getString(R.string.profile_map_unavailable) + "'});"
                + "setTimeout(()=>{if(status.style.display!=='none')status.textContent='"
                + getString(R.string.profile_map_unavailable) + "';},10000);"
                + "}catch(e){status.textContent='" + getString(R.string.profile_map_unavailable) + "';}</script></body></html>";
    }
    private void renderAvatar(String url) {
        if (url == null || url.trim().isEmpty()) {
            Glide.with(this).clear(avatarPreview);
            avatarPreview.setImageResource(R.drawable.ic_nav_profile);
            avatarStatus.setText(R.string.no_avatar_selected);
        } else {
            Glide.with(this).load(url.trim()).circleCrop().placeholder(R.drawable.ic_nav_profile)
                    .error(R.drawable.ic_nav_profile).into(avatarPreview);
            avatarStatus.setText(R.string.avatar_uploaded_status);
        }
    }

    @Override public void onDestroyView() {
        if (locationMap != null) {
            locationMap.stopLoading();
            locationMap.loadUrl("about:blank");
            locationMap.setWebViewClient(null);
            locationMap.destroy();
            locationMap = null;
        }
        mapContainer = null;
        super.onDestroyView();
    }
    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
