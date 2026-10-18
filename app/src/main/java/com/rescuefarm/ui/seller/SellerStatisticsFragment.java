package com.rescuefarm.ui.seller;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.rescuefarm.R;
import com.rescuefarm.service.seller.SellerStatisticsSnapshot;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SellerStatisticsFragment extends Fragment {
    private final android.os.Handler timeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeRefresh = new Runnable() {
        @Override public void run() {
            if (getView() != null && viewModel != null && (viewModel.getState().getValue() == null
                    || viewModel.getState().getValue().status != SellerStatisticsViewModel.Status.LOADING))
                viewModel.load(viewModel.getDays(), true);
            timeHandler.postDelayed(this, 60000L);
        }
    };
    private SellerStatisticsViewModel viewModel;
    private LinearLayout content;
    private TextView message;
    private View progress;
    private final int[] periods = {7, 30, 90, 0};
    private final Locale locale = Locale.forLanguageTag("vi-VN");
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_statistics, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SellerUiStyles.styleActions(view, SellerUiStyles.Section.ANALYTICS);
        viewModel = new ViewModelProvider(this).get(SellerStatisticsViewModel.class);
        content = view.findViewById(R.id.sellerStatisticsContent); message = view.findViewById(R.id.sellerStatisticsMessage);
        progress = view.findViewById(R.id.sellerStatisticsProgress);
        Spinner period = view.findViewById(R.id.sellerStatisticsPeriod);
        period.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"7 ngày gần đây", "30 ngày gần đây", "90 ngày gần đây", "Toàn thời gian"}));
        int selected = 1; for (int i = 0; i < periods.length; i++) if (periods[i] == viewModel.getDays()) selected = i;
        period.setSelection(selected);
        period.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) { viewModel.load(periods[position], false); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        view.findViewById(R.id.sellerStatisticsBack).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.sellerStatisticsRefresh).setOnClickListener(v -> viewModel.load(periods[period.getSelectedItemPosition()], true));
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }
    private void render(SellerStatisticsViewModel.State state) {
        content.removeAllViews(); message.setText(state.message);
        progress.setVisibility(state.status == SellerStatisticsViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
        SellerStatisticsSnapshot value = state.snapshot; if (value == null) return;
        text(value.timeBasis, 14, false);
        if (value.unverifiedCampaigns > 0) text(value.unverifiedCampaigns + " chiến dịch cũ chưa xác minh đơn vị; chưa cộng vào lượng giải cứu.", 14, false);
        if (value.approximateDates) text("Một số giao dịch cũ chưa có ngày giao/hoàn thành riêng; thống kê dùng ngày cập nhật gần nhất của bản ghi đó.", 14, false);
        text("Doanh thu: " + NumberFormat.getCurrencyInstance(locale).format(value.revenue), 22, true);
        String[] dates = new String[value.trendDates.size()]; double[] revenue = new double[dates.length];
        SimpleDateFormat date = new SimpleDateFormat("dd/MM", locale);
        for (int i = 0; i < dates.length; i++) { dates[i] = date.format(new Date(value.trendDates.get(i))); revenue[i] = value.trendRevenue.get(i); }
        chart(dates, revenue, true, "Doanh thu đơn đã giao theo thời gian");
        chart(new String[]{"Đơn tạo", "Đã nhận"}, new double[]{value.orders, value.deliveredOrders}, false,
                "Đơn hàng trong kỳ");
        text("Đơn tạo tính theo ngày tạo; đơn đã nhận tính theo ngày nhận. Hai cột có thể thuộc các nhóm đơn khác nhau.", 14, false);
        text("Đơn đang xử lý toàn bộ cửa hàng: " + value.pendingOrders, 16, false);
        text("Chiến dịch hoàn thành trong kỳ: " + value.completedCampaigns, 16, false);
        chart(new String[]{"Có chiến dịch giải cứu", "Đơn khác"}, new double[]{value.rescuedOrders,
                Math.max(0, value.deliveredOrders - value.rescuedOrders)}, SellerStatisticsChart.Kind.RING,
                "Cơ cấu đơn đã nhận trong kỳ");
        text("Lượng đã giải cứu trong kỳ", 18, true);
        if (value.rescued.isEmpty()) text("Chưa có đơn giải cứu đã nhận trong kỳ.", 16, false);
        for (SellerStatisticsSnapshot.Quantity quantity : value.rescued) text(format(quantity.value) + " " + quantity.unit, 22, true);
        if (value.rescueRateCampaigns > 0) {
            double rate = Math.max(0, Math.min(100, value.rescueRate));
            chart(new String[]{"Đã hoàn thành", "Còn lại"}, new double[]{rate, 100-rate},
                    SellerStatisticsChart.Kind.PROGRESS, "Mức hoàn thành mục tiêu trung bình");
            text("Tiến độ lũy kế của " + value.rescueRateCampaigns + " chiến dịch có thời gian giao với kỳ đã chọn. Trung bình từng chiến dịch, không cộng lẫn các đơn vị hàng hóa.", 14, false);
        } else text("Chưa có chiến dịch đủ điều kiện tính tiến độ trong kỳ.", 16, false);
        ranked("Sản phẩm đã giao nhiều nhất", value.products, false);
        ranked("Lý do giải cứu theo sản lượng", value.reasons, true);
        if (value.dashboard != null) {
            chart(new String[]{"Đang hoạt động", "Trạng thái khác"},
                    new double[]{value.dashboard.getActiveProducts(), Math.max(0,value.dashboard.getProducts()-value.dashboard.getActiveProducts())},
                    SellerStatisticsChart.Kind.RING, "Sản phẩm hiện tại của cửa hàng");
            chart(new String[]{"Đang hoạt động", "Trạng thái khác"},
                    new double[]{value.dashboard.getActiveCampaigns(), Math.max(0,value.dashboard.getCampaigns()-value.dashboard.getActiveCampaigns())},
                    SellerStatisticsChart.Kind.RING, "Chiến dịch hiện tại của cửa hàng");
            text("Hai biểu đồ hiện trạng phản ánh toàn bộ cửa hàng tại lúc tải dữ liệu, không giới hạn theo kỳ đã chọn.", 14, false);
        }
        text("Cập nhật: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(new Date(value.calculatedAt)), 14, false);
    }
    private void ranked(String title, List<SellerStatisticsSnapshot.Quantity> values, boolean reasons) {
        if (values.isEmpty()) { text(title, 18, true); text("Chưa có dữ liệu trong kỳ.", 16, false); return; }
        java.util.Map<String, java.util.List<SellerStatisticsSnapshot.Quantity>> groups = new java.util.LinkedHashMap<>();
        for (SellerStatisticsSnapshot.Quantity value : values) groups.computeIfAbsent(value.unit, key -> new java.util.ArrayList<>()).add(value);
        for (java.util.Map.Entry<String, java.util.List<SellerStatisticsSnapshot.Quantity>> entry : groups.entrySet()) {
            java.util.List<SellerStatisticsSnapshot.Quantity> group = entry.getValue();
            group.sort((a,b) -> Double.compare(b.value,a.value));
            int count = Math.min(6, group.size()); String[] labels = new String[count]; double[] quantities = new double[count];
            for (int i=0; i<count; i++) {
                labels[i]=group.get(i).label; quantities[i]=group.get(i).value;
                if (reasons) try { labels[i]=reasonLabel(com.rescuefarm.domain.enums.RescueReason.valueOf(labels[i])); } catch(IllegalArgumentException ignored) { }
            }
            chart(labels, quantities, SellerStatisticsChart.Kind.HORIZONTAL, title + " (" + entry.getKey() + ")");
            text("Tối đa 6 mục theo sản lượng; chỉ so sánh cùng đơn vị " + entry.getKey() + ".", 14, false);
        }
    }
    private String reasonLabel(com.rescuefarm.domain.enums.RescueReason reason) {
        switch (reason) {
            case OVER_SUPPLY: return "Dư cung"; case NEAR_EXPIRY: return "Gần hết hạn";
            case SEASONAL_HARVEST: return "Thu hoạch theo mùa"; case WEATHER_IMPACT: return "Ảnh hưởng thời tiết";
            case ORDER_CANCELLED: return "Đơn hàng bị hủy"; case LOGISTICS_DISRUPTION: return "Gián đoạn vận chuyển";
            case DEMAND_DROP: return "Nhu cầu giảm"; default: return "Nông sản loại B";
        }
    }
    private String format(double value) { return NumberFormat.getNumberInstance(locale).format(value); }
    private void chart(String[] labels, double[] values, boolean line, String title) {
        chart(labels, values, line ? SellerStatisticsChart.Kind.LINE : SellerStatisticsChart.Kind.COLUMN, title);
    }
    private void chart(String[] labels, double[] values, SellerStatisticsChart.Kind kind, String title) {
        text(title, 18, true);
        content.addView(new SellerStatisticsChart(requireContext(), labels, values, kind), new LinearLayout.LayoutParams(-1, -2));
    }
    private void text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(requireContext().getColor(R.color.rescue_on_surface));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Math.round(12 * getResources().getDisplayMetrics().density); content.addView(view, params);
    }
    @Override public void onResume() {
        super.onResume(); if (viewModel != null) viewModel.load(viewModel.getDays(), true);
        timeHandler.removeCallbacks(timeRefresh); timeHandler.postDelayed(timeRefresh, 60000L);
    }
    @Override public void onPause() { timeHandler.removeCallbacks(timeRefresh); super.onPause(); }
    @Override public void onDestroyView() { content = null; message = null; progress = null; super.onDestroyView(); }
}
