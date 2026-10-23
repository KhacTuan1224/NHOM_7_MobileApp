package com.rescuefarm.ui.fund;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuefarm.R;
import com.rescuefarm.service.backend.RenderBackendClient;
import com.rescuefarm.ui.common.CustomerUi;
import com.rescuefarm.ui.common.AppTypography;
import com.rescuefarm.ui.profile.*;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import org.json.*;
import java.util.UUID;
import java.util.Locale;
import java.util.function.Consumer;
import static com.rescuefarm.ui.common.CustomerUi.*;

/** Public donations and role-scoped charity deliveries, backed by the fund API. */
public final class RescueFundFragment extends Fragment {
    private LinearLayout body, jobs;
    private TextView status;
    private String role = "GUEST", focus = "", uploadResource = "", uploadType = "", proofUrl = "";
    private JSONObject fund = new JSONObject();
    private SharedPreferences prefs;
    private boolean busy;
    private boolean showingReceipt;
    private final android.os.Handler poller = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!showingReceipt || getView() == null || !isResumed()) return;
            if (!busy) receipt();
            poller.postDelayed(this, 15000);
        }
    };
    private int generation;
    private View uploadButton;
    private ImageView preview;
    private TextView uploadStatus;
    private Uri cameraUri;
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
        if (success && cameraUri != null) uploadEvidence(cameraUri);
    });
    private void uploadEvidence(Uri uri) {
        if (uri != null && uploadButton != null) CloudinaryUploadUi.upload(this, uri, uploadType,
                uploadResource, 0, uploadButton, uploadStatus, preview, url -> proofUrl = url);
    }
    private final ActivityResultLauncher<String> picker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        uploadEvidence(uri);
    });
    private static final int NUMBER = InputType.TYPE_CLASS_NUMBER;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle saved) {
        LinearLayout root = column(requireContext()), bar = column(requireContext());
        bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL); root.addView(bar);
        com.rescuefarm.ui.common.AppBackButton back = new com.rescuefarm.ui.common.AppBackButton(requireContext());
        back.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack()); bar.addView(back);
        TextView title = text(bar, "Hũ giải cứu nông sản"); AppTypography.title(title); title.setTextSize(22);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        status = text(root, "Đang tải…"); body = column(requireContext()); root.addView(body);
        return screen(this, root);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        busy = false; proofUrl = "";
        prefs = requireContext().getSharedPreferences("rescue_fund_receipts", 0);
        focus = getArguments() == null ? "" : getArguments().getString("deliveryId", "");
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { role = "GUEST"; view.post(this::load); return; }
        ProfileViewModel vm = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext())).get(ProfileViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), this::profileState); vm.loadProfile();
    }
    private void profileState(ProfileScreenState state) {
        if (state.getStatus() == ProfileScreenState.Status.PROFILE && state.getUser() != null) {
            role = state.getUser().getRole().name(); load();
        } else if (state.getStatus() == ProfileScreenState.Status.ERROR) status.setText(state.getMessage());
    }
    @Override public void onResume() { super.onResume(); if (showingReceipt) poller.postDelayed(poll, 15000); }
    @Override public void onPause() { poller.removeCallbacks(poll); super.onPause(); }
    @Override public void onDestroyView() { generation++; busy = false; showingReceipt = false; poller.removeCallbacks(poll); uploadButton = null; super.onDestroyView(); }
    private JSONObject json(Object... pairs) {
        JSONObject value = new JSONObject(); try { for (int i=0; i<pairs.length; i+=2) value.put((String)pairs[i], pairs[i+1]); }
        catch (JSONException error) { throw new IllegalArgumentException(error); } return value;
    }
    private void request(String path, JSONObject payload, Consumer<JSONObject> done) {
        if (busy) return; busy = true; int expected = generation; status.setText("Đang xử lý…");
        RenderBackendClient.Callback callback = new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject value) {
                if (expected != generation || getView() == null) return;
                busy = false; status.setText(""); done.accept(value);
            }
            public void onError(String error) {
                if (expected != generation || getView() == null) return;
                busy = false; status.setText(error);
            }
        };
        if (path.startsWith("admin/") || path.equals("deliveries") || path.equals("delivery/action"))
            RenderBackendClient.post("/rescue-fund/" + path, payload, callback);
        else RenderBackendClient.postGuest("/rescue-fund/" + path, payload, callback);
    }
    private String money(double amount) { return String.format(Locale.forLanguageTag("vi-VN"), "%,.0f đ", amount); }
    private String date(long value) { return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(new java.util.Date(value)); }
    private void heading(LinearLayout target, String value) { TextView title = text(target, value); AppTypography.title(title); title.setTextSize(20); }
    private void load() {
        if (getView() == null) return;
        showingReceipt = false; poller.removeCallbacks(poll);
        request(role.equals("ADMIN") ? "admin/get" : "public", json(), value -> {
            fund = value.optJSONObject("fund"); if (fund == null) fund = new JSONObject();
            body.removeAllViews(); heading(body, fund.optString("name", "Hũ giải cứu nông sản"));
            text(body, "Có thể phân bổ: " + money(fund.optDouble("available")) + "\nĐang giữ cho chuyến giao: "
                    + money(fund.optDouble("reserved")) + "\nĐã chi: " + money(fund.optDouble("spent")));
            text(body, "Nơi nhận: " + fund.optString("charityName") + "\n" + fund.optString("address"));
            text(body, "Phân bổ lúc 00:00 giờ Việt Nam. Chỉ giải cứu hàng còn hạn và cần giao trước hạn sử dụng.");
            if (role.equals("ADMIN")) {
                button(body, "Cấu hình hũ và điểm thiện nguyện", v -> config());
                button(body, "Lịch sử biến động", v -> ledger(""));
                button(body, "Giao dịch SePay cần đối soát", v -> events());
                button(body, "Điều chỉnh sau đối soát ngân hàng", v -> adjust());
            } else {
                com.google.android.material.button.MaterialButton donate = button(body, "Ủng hộ vào hũ", v -> donateForm());
                donate.setIconResource(R.drawable.ic_rescue_fund); donate.setEnabled(fund.optBoolean("active"));
                if (!fund.optBoolean("active")) text(body, "Hũ đang tạm ngừng nhận ủng hộ.");
                if (!prefs.getString("receipt", "").isEmpty()) button(body, "Biên nhận chuyển khoản gần nhất", v -> receipt());
            }
            button(body, "Làm mới", v -> load());
            if (role.equals("ADMIN") || role.equals("SELLER")) {
                heading(body, "Chuyến giao thiện nguyện"); jobs = column(requireContext()); body.addView(jobs);
                if (!focus.isEmpty()) button(body, "Xem tất cả chuyến giao", v -> { focus = ""; load(); });
                loadJobs("");
            }
        });
    }
    private String token() {
        String token = prefs.getString("token", "");
        if (token.isEmpty()) { token = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""); prefs.edit().putString("token", token).apply(); }
        return token;
    }
    private void donateForm() {
        body.removeAllViews(); heading(body, "Số tiền ủng hộ");
        EditText amount = input(body, "Số tiền (VND, từ 10.000đ)", NUMBER);
        text(body, "Chuyển đúng số tiền và nội dung trên QR. SePay tự xác nhận; không có nút xác nhận chuyển tiền thủ công.");
        String key = UUID.randomUUID().toString();
        button(body, "Xác nhận và xem QR", v -> {
            try { long total = Long.parseLong(value(amount));
                request("donate", json("amount", total, "requestId", key, "accessToken", token()), result -> {
                    JSONObject d = result.optJSONObject("donation"); if (d == null) return;
                    prefs.edit().putString("receipt", d.optString("id")).apply(); showReceipt(d);
                });
            } catch (NumberFormatException error) { amount.setError("Nhập số tiền nguyên hợp lệ."); }
        }); button(body, "Quay về hũ", v -> load());
    }
    private void receipt() {
        request("receipt", json("donationId", prefs.getString("receipt", ""), "accessToken", token()), result -> showReceipt(result.optJSONObject("donation")));
    }
    private void showReceipt(JSONObject d) {
        if (d == null) return;
        showingReceipt = !d.optString("status").equals("PAID") && d.optLong("expiresAt") > System.currentTimeMillis();
        poller.removeCallbacks(poll); if (showingReceipt && isResumed()) poller.postDelayed(poll, 15000);
        body.removeAllViews(); heading(body, d.optString("status").equals("PAID") ? "Đã nhận ủng hộ" : "Chờ SePay xác nhận");
        text(body, money(d.optDouble("amount")) + "\n" + d.optString("accountName") + "\nSTK: " + d.optString("accountNumber")
                + "\nNội dung: " + d.optString("transferContent"));
        if (!d.optString("status").equals("PAID") && d.optLong("expiresAt") > System.currentTimeMillis()) {
            ImageView qr = new ImageView(requireContext()); body.addView(qr, new LinearLayout.LayoutParams(-1, dp(requireContext(), 300)));
            qr.setContentDescription("QR chuyển khoản ủng hộ"); Glide.with(this).load(d.optString("qrUrl")).fitCenter().into(qr);
            text(body, "QR dùng đến " + date(d.optLong("expiresAt")) + ". Đừng chuyển lại nếu đã chuyển mà còn chờ xác nhận.");
        } else if (!d.optString("status").equals("PAID")) text(body, "QR đã hết thời gian sử dụng. Nếu đã chuyển, giữ mã giao dịch để đối soát, không chuyển lần nữa.");
        button(body, "Làm mới trạng thái", v -> receipt()); button(body, "Về hũ", v -> load());
    }
    private void config() {
        body.removeAllViews(); heading(body, "Cấu hình hũ");
        EditText name = field("Tên hũ", "name", TEXT), charity = field("Tổ chức nhận", "charityName", TEXT), address = field("Địa chỉ", "address", TEXT), phone = field("Điện thoại nơi nhận", "phone", InputType.TYPE_CLASS_PHONE);
        EditText lat = field("Vĩ độ", "latitude", NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED), lon = field("Kinh độ", "longitude", NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText daily = field("Ngân sách mỗi ngày (VND)", "dailyBudget", NUMBER), seller = field("Tối đa mỗi Seller/ngày (VND)", "perSellerBudget", NUMBER);
        if (value(daily).isEmpty()) daily.setText("500000"); if (value(seller).isEmpty()) seller.setText("100000");
        Spinner idle = hours("Không có giao dịch mua thành công trong", fund.optInt("idleHours", 12));
        Spinner expiry = hours("Còn hạn sử dụng tối đa", fund.optInt("expiryHours", 24));
        Switch active = new Switch(requireContext()); active.setText("Mở hũ nhận ủng hộ và phân bổ"); active.setChecked(fund.optBoolean("active")); body.addView(active);
        text(body, "Tài khoản nhận và kết nối SePay được cấu hình bảo mật trên backend. Thay đổi địa điểm không sửa chuyến đã phân bổ.");
        button(body, "Lưu cấu hình", v -> {
            try { request("admin/save", json("name", value(name), "charityName", value(charity), "address", value(address), "phone", value(phone),
                    "latitude", Double.parseDouble(value(lat)), "longitude", Double.parseDouble(value(lon)),
                    "dailyBudget", Long.parseLong(value(daily)), "perSellerBudget", Long.parseLong(value(seller)),
                    "idleHours", idle.getSelectedItemPosition() == 0 ? 12 : 24, "expiryHours", expiry.getSelectedItemPosition() == 0 ? 12 : 24,
                    "active", active.isChecked()), result -> load());
            } catch (NumberFormatException error) { status.setText("Kiểm tra tọa độ và số tiền."); }
        }); button(body, "Hủy chỉnh sửa", v -> load());
    }
    private EditText field(String hint, String key, int type) { EditText field = input(body, hint, type); field.setText(fund.optString(key, "")); return field; }
    private Spinner hours(String label, int selected) {
        text(body, label); Spinner spinner = new Spinner(requireContext()); spinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"12 giờ", "24 giờ"}));
        spinner.setSelection(selected == 24 ? 1 : 0); body.addView(spinner); return spinner;
    }
    private void ledger(String cursor) {
        request("admin/ledger", json("cursor", cursor), result -> {
            if (cursor.isEmpty()) { body.removeAllViews(); heading(body, "Sổ biến động hũ"); }
            JSONArray rows = result.optJSONArray("items");
            if (rows != null) for (int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row == null) continue;
                text(body, date(row.optLong("createdAt")) + " · " + ledgerLabel(row.optString("kind")) + "\n" + money(row.optDouble("amount")) + "\n" + row.optString("reason")); }
            if (!result.optString("cursor").isEmpty()) button(body, "Xem thêm biến động", v -> {v.setEnabled(false); ledger(result.optString("cursor"));});
            button(body, "Về hũ", v -> load());
        });
    }
    private void events() { events(""); }
    private void events(String cursor) {
        request("admin/events", json("cursor", cursor), result -> { if(cursor.isEmpty()){ body.removeAllViews(); heading(body, "Giao dịch cần đối soát"); }
            text(body, "Không tự cộng vào hũ khi sai số tiền, nội dung hoặc thời gian. Đối chiếu sao kê; nếu hoàn trả hoặc điều chỉnh thì lưu bằng chứng ngân hàng.");
            JSONArray rows = result.optJSONArray("items"); if (rows != null) for (int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row!=null) text(body, "SePay #"+row.optString("id")+" · "+money(row.optDouble("amount"))+"\n"+row.optString("reason")+"\nMã ngân hàng: "+row.optString("bankReference")+"\nNội dung: "+row.optString("content")+"\n"+row.optString("transactionDate")); }
            if(!result.optString("cursor").isEmpty())button(body,"Xem thêm giao dịch",v->{v.setEnabled(false);events(result.optString("cursor"));});
            button(body, "Về hũ", v -> load()); });
    }
    private void upload(String type, String resource) {
        proofUrl = ""; uploadType = type; uploadResource = resource;
        uploadStatus = text(body, "Chưa có ảnh bằng chứng"); preview = new ImageView(requireContext());
        body.addView(preview, new LinearLayout.LayoutParams(-1, dp(requireContext(),180)));
        uploadButton = button(body, "Chọn ảnh bằng chứng", v -> picker.launch("image/*"));
        if (type.equals("fundDelivery")) button(body, "Chụp ảnh bàn giao", v -> {
            try {
                java.io.File folder = new java.io.File(requireContext().getCacheDir(), "fund-evidence");
                if (!folder.exists() && !folder.mkdirs()) throw new java.io.IOException();
                java.io.File photo = java.io.File.createTempFile("handover-", ".jpg", folder);
                cameraUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fund-evidence", photo);
                camera.launch(cameraUri);
            } catch (java.io.IOException | android.content.ActivityNotFoundException error) {
                status.setText("Không mở được máy ảnh. Bạn có thể chọn ảnh đã chụp từ thư viện.");
            }
        });
    }
    private void adjust() {
        body.removeAllViews(); heading(body, "Đối soát số dư thực tế"); String key=UUID.randomUUID().toString();
        text(body, "Đây là bút toán đối soát với ngân hàng, không phải xác nhận tiền ủng hộ thủ công. Số tiền dương để tăng, âm để giảm số dư có thể phân bổ.");
        EditText amount=input(body,"Chênh lệch (VND)",NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED), reason=input(body,"Lý do đối soát (ít nhất 10 ký tự)",TEXT);
        upload("fundReceipt",key);
        button(body,"Ghi bút toán đối soát",v -> confirm("Ghi điều chỉnh đúng với sao kê ngân hàng?", () -> {
            try { request("admin/adjust",json("requestId",key,"amount",Long.parseLong(value(amount)),"reason",value(reason),"proofUrl",proofUrl), r->load()); }
            catch(NumberFormatException error){amount.setError("Nhập số tiền nguyên.");}
        })); button(body,"Hủy",v->load());
    }
    private void loadJobs(String cursor) {
        request("deliveries", json("cursor",cursor,"deliveryId",focus), result -> {
            JSONArray rows=result.optJSONArray("items"); if(rows!=null) for(int i=0;i<rows.length();i++){JSONObject j=rows.optJSONObject(i);if(j!=null)job(j);}
            if(rows==null||rows.length()==0)text(jobs,"Chưa có chuyến giao.");
            if(!result.optString("cursor").isEmpty())button(jobs,"Xem thêm chuyến",v->{v.setEnabled(false);loadJobs(result.optString("cursor"));});
        });
    }
    private String state(String state) {
        switch(state){case "RESERVED":return "Đang giữ tiền và hàng";case "ACCEPTED":return "Seller đã nhận chuyến";
            case "DELIVERED":return "Chờ kiểm tra ảnh giao";case "VERIFIED":return "Đã xác minh giao — chờ chi tiền";
            case "PAID":return "Admin đã ghi chuyển tiền";case "CANCELLED":return "Đã hủy và giải phóng tiền";default:return state;}
    }
    private String ledgerLabel(String kind) {
        switch(kind){case "SETTINGS":return "Đổi cấu hình hũ";case "DONATION":return "Nhận ủng hộ qua SePay";
            case "RESERVE":return "Giữ tiền cho chuyến";case "RELEASE":return "Hoàn phần tiền đang giữ";
            case "ACCEPT":return "Seller nhận chuyến";case "DELIVER":return "Seller gửi ảnh giao hàng";
            case "VERIFY_DELIVERY":return "Đã xác minh giao";case "PAYOUT":return "Ghi khoản đã chuyển cho Seller";
            case "BANK_RECONCILIATION":return "Điều chỉnh sau đối soát ngân hàng";default:return "Biến động hũ";}
    }
    private void job(JSONObject j) {
        LinearLayout box=column(requireContext()); jobs.addView(box);
        heading(box,j.optString("productName")); text(box,state(j.optString("status"))+"\n"+j.optDouble("quantity")+" "+j.optString("unit")+" · "+money(j.optDouble("amount"))
            +"\n"+j.optString("fundName")+"\n"+j.optString("campaignTitle")+"\nGiao trước: "+date(j.optLong("deadline"))
            +"\n"+j.optString("charityName")+"\n"+j.optString("address")+"\n"+j.optString("phone"));
        button(box,"Xem vị trí nhận",v->{Intent map=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+j.optDouble("latitude")+","+j.optDouble("longitude")+"?q="+j.optDouble("latitude")+","+j.optDouble("longitude")+"("+Uri.encode(j.optString("charityName"))+")"));
            try{startActivity(map);}catch(android.content.ActivityNotFoundException error){status.setText("Thiết bị chưa có ứng dụng bản đồ.");}});
        if(!j.optString("proofUrl").isEmpty()){ImageView image=new ImageView(requireContext());box.addView(image,new LinearLayout.LayoutParams(-1,dp(requireContext(),180)));Glide.with(this).load(j.optString("proofUrl")).fitCenter().into(image);}
        String s=j.optString("status");
        if(role.equals("SELLER")) {
            if(s.equals("RESERVED"))button(box,"Nhận chuyến giao",v->act(j,"ACCEPT",json()));
            if(s.equals("ACCEPTED"))button(box,"Gửi ảnh xác nhận giao",v->{body.removeAllViews();heading(body,"Bằng chứng giao hàng");upload("fundDelivery",j.optString("id"));EditText note=input(body,"Ghi chú bàn giao",TEXT);
                button(body,"Gửi bằng chứng",x->act(j,"DELIVER",json("proofUrl",proofUrl,"note",value(note))));button(body,"Hủy",x->load());});
        } else if(role.equals("ADMIN")) {
            if(s.equals("DELIVERED"))button(box,"Xác minh đã giao trước hạn",v->confirm("Đã kiểm tra ảnh và xác nhận với nơi nhận?",()->act(j,"VERIFY",json())));
            if(s.equals("VERIFIED"))button(box,"Ghi tiền đã chuyển cho Seller",v->payout(j));
        }
        if(s.equals("RESERVED")||s.equals("ACCEPTED")||role.equals("ADMIN")&&s.equals("DELIVERED"))button(box,"Hủy chuyến có lý do",v->{EditText reason=new EditText(requireContext());
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Lý do hủy chuyến").setView(reason).setNegativeButton("Đóng",null)
                .setPositiveButton("Hủy chuyến",(d,w)->act(j,"CANCEL",json("note",value(reason)))).show();});
        if(s.equals("VERIFIED")||s.equals("PAID"))text(box,"TK Seller: "+j.optString("sellerBankCode")+" · "+j.optString("sellerBankAccountNumber")+"\n"+j.optString("sellerBankAccountName"));
        if(s.equals("PAID")){text(box,"Mã chi: "+j.optString("bankReference"));ImageView receipt=new ImageView(requireContext());box.addView(receipt,new LinearLayout.LayoutParams(-1,dp(requireContext(),180)));Glide.with(this).load(j.optString("payoutProofUrl")).fitCenter().into(receipt);}
    }
    private void act(JSONObject j,String action,JSONObject b){try{b.put("deliveryId",j.optString("id")).put("action",action);}catch(JSONException ignored){}request("delivery/action",b,r->load());}
    private void payout(JSONObject j){body.removeAllViews();heading(body,"Ghi khoản đã chuyển ngân hàng");text(body,"Chuyển ngoài app đúng "+money(j.optDouble("amount"))+" tới "+j.optString("sellerBankCode")+" · "+j.optString("sellerBankAccountNumber")+" · "+j.optString("sellerBankAccountName")+". Chỉ ghi sau khi ngân hàng báo thành công.");
        EditText reference=input(body,"Mã giao dịch ngân hàng",TEXT);upload("fundReceipt",j.optString("id"));
        button(body,"Lưu bằng chứng đã chuyển",v->confirm("Xác nhận đã chuyển đúng số tiền cho Seller?",()->act(j,"PAY",json("amount",j.optLong("amount"),"bankReference",value(reference),"proofUrl",proofUrl))));button(body,"Hủy",v->load());}
    private void confirm(String question,Runnable action){new MaterialAlertDialogBuilder(requireContext()).setMessage(question).setNegativeButton("Đóng",null).setPositiveButton("Xác nhận",(d,w)->action.run()).show();}
}
