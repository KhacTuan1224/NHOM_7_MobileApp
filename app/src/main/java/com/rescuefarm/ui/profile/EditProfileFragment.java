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
    private android.app.Dialog mapDialog;
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
        configureMap(locationMap,false);
    }
    private void configureMap(WebView target,boolean expanded) {
        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        String defaultUserAgent = settings.getUserAgentString();
        settings.setUserAgentString(defaultUserAgent + " RescueFarmAndroidApp/1.0 (com.example.nhom_7_mobileapp)");
        target.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        target.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                Uri uri=request.getUrl();
                if("rescuefarm-map".equals(uri.getScheme())) {
                    if(!expanded&&"expand".equals(uri.getHost()))openProfileMap();
                    else if(expanded&&"back".equals(uri.getHost())&&mapDialog!=null)mapDialog.dismiss();
                }
                return true;
            }
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
        target.setWebChromeClient(new WebChromeClient() {
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
        return mapHtml(lat,lon,false);
    }
    private String mapHtml(double lat,double lon,boolean expanded) {
        // Shared renderer only: profile maps never subscribe to rescue data.
        // Coordinates stay in the local document, not a tile URL or backend request.
        try(java.io.InputStream input=requireContext().getAssets().open("maps/rescue-map.html")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            String html=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            html=html.replace("<script src=\"leaflet.js\"></script>",
                    "<script>window.rescueMapOptions={profile:true,expanded:"+expanded+"};</script><script src=\"leaflet.js\"></script>");
            return html.replace("</body>","<script>window.setProfilePoint("
                    +String.format(Locale.US,"%.6f",lat)+","+String.format(Locale.US,"%.6f",lon)
                    +");</script></body>");
        } catch(java.io.IOException error) {
            Log.e("ProfileMap","Missing bundled map",error);
            return "<html><body>"+getString(R.string.profile_map_unavailable)+"</body></html>";
        }
    }
    private void openProfileMap() {
        if(!isAdded()||mapContainer==null||mapContainer.getVisibility()!=View.VISIBLE
                ||mapDialog!=null&&mapDialog.isShowing())return;
        android.app.Dialog dialog=new android.app.Dialog(requireContext(),android.R.style.Theme_Material_Light_NoActionBar);
        WebView fullMap=new WebView(requireContext());configureMap(fullMap,true);
        mapDialog=dialog;dialog.setContentView(fullMap);
        dialog.setOnDismissListener(unused->{
            fullMap.stopLoading();fullMap.setWebViewClient(null);fullMap.setWebChromeClient(null);fullMap.destroy();
            if(mapDialog==dialog)mapDialog=null;
        });
        fullMap.loadDataWithBaseURL("https://appassets.androidplatform.net/maps/",mapHtml(latitude,longitude,true),"text/html","UTF-8",null);
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
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
        if(mapDialog!=null)mapDialog.dismiss();
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
