package dev.ecorp.steps;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    static LinearLayout column(Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        column.setPadding(padding, padding, padding, padding);
        return column;
    }

    static TextView text(Activity activity, LinearLayout column, String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(0xff25372e);
        text.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
        column.addView(text);
        return text;
    }

    static Button button(Activity activity, LinearLayout column, String label, View.OnClickListener click) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(click);
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
}
