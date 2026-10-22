package com.rescuefarm.ui.order;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.ui.common.EnumLabels;
import com.rescuefarm.ui.common.OrderLabels;
import java.text.NumberFormat;
import java.util.Locale;

/** Shared order summary: readable information on the left, outlined action on the right. */
public final class OrderListRow {
    private OrderListRow() { }

    public static View create(ViewGroup parent, Order order, boolean showPaymentStatus,
            @ColorRes int actionColor, View.OnClickListener open) {
        Context context = parent.getContext();
        View row = LayoutInflater.from(context).inflate(R.layout.item_order_row, parent, false);
        ((TextView) row.findViewById(R.id.orderRowCode)).setText(order.getOrderCode());
        String status = OrderLabels.status(order.getStatus(), order.getPaymentMethod());
        if (order.isExpiryReviewRequired()) status += "\nCần đối soát";
        if (showPaymentStatus) status += "\n" + context.getString(R.string.order_row_payment_status,
                EnumLabels.label(order.getPaymentStatus()));
        ((TextView) row.findViewById(R.id.orderRowStatus)).setText(status);
        ((TextView) row.findViewById(R.id.orderRowAmount)).setText(
                NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(order.getTotalAmount()));
        MaterialButton button = row.findViewById(R.id.orderRowDetailButton);
        int color = ContextCompat.getColor(context, actionColor);
        button.setTextColor(color);
        button.setStrokeColor(ColorStateList.valueOf(color));
        button.setContentDescription(context.getString(R.string.order_view_detail_description, order.getOrderCode()));
        button.setOnClickListener(open);
        row.setOnClickListener(open);
        return row;
    }
}
