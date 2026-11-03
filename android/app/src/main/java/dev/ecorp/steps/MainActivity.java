package dev.ecorp.steps;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private boolean foreground;
    private boolean polling;
    private final Runnable poll = this::checkConnection;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        render();
        if (saved == null && Store.connected(this)) Browser.open(this, "/dashboard");
    }

    private void render() {
        JSONObject state = Store.read(this);
        LinearLayout column = Ui.column(this);
        column.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            int padding = Ui.dp(this, 20);
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        Ui.text(this, column, "Steps", 28);
        Ui.text(this, column, "steps.ecorp.dev", 14);
        status = Ui.text(this, column, state.optString("error"), 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        if (Store.connected(this)) {
            Ui.text(this, column, "Connected as " + state.optString("email"), 16);
            Ui.button(this, column, "Open tasks", view -> Browser.open(this, "/dashboard"));
            Ui.button(this, column, "Add a task", view -> startActivity(new Intent(this, QuickAddActivity.class)));
            Ui.button(this, column, "Add Starred widget", view -> pin(StarredWidget.class));
            Ui.button(this, column, "Add quick-add widget", view -> pin(AddWidget.class));
            Ui.button(this, column, "Refresh widgets", view -> refresh());
            Ui.button(this, column, "Disconnect this phone", view -> new AlertDialog.Builder(this)
                .setMessage("Revoke widget access and clear this phone’s cached tasks, credentials and draft? Website sign-in stays separate.")
                .setNegativeButton("Cancel", null).setPositiveButton("Disconnect", (dialog, which) -> disconnect(false)).show());
        } else if (!state.optString("pending_token").isEmpty()) {
            Ui.text(this, column, "Connection code: " + state.optString("user_code"), 22);
            Ui.text(this, column, "Sign in in your browser, confirm this same code, then return here. The code expires after ten minutes.", 16);
            Ui.button(this, column, "Continue browser sign-in", view -> Browser.open(this, "/device/connect?code=" + Store.read(this).optString("user_code")));
            Ui.button(this, column, "Check connection", view -> checkConnection());
            Ui.button(this, column, "Cancel connection", view -> {
                Store.put(this, "pending_token", null);
                Store.put(this, "user_code", null);
                render();
            });
        }

        if (!Store.connected(this) && state.optString("pending_token").isEmpty()) {
            Ui.button(this, column, "Connect with Google", view -> beginConnection(view));
        }
        Ui.text(this, column, "Widgets refresh when Android allows it. Website sign-out does not disconnect widgets. Version " + BuildConfig.VERSION_NAME, 12);
    }

    private void beginConnection(View button) {
        button.setEnabled(false);
        status.setText("Starting connection…");
        Repository.IO.execute(() -> {
            try {
                JSONObject result = new Api("").call("POST", "/mobile/authorization", new JSONObject().put("name", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL));
                Store.put(this, "pending_token", result.getString("device_token"));
                Store.put(this, "user_code", result.getString("user_code"));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    render();
                    Browser.open(this, "/device/connect?code=" + result.optString("user_code"));
                });
            } catch (Exception error) {
                runOnUiThread(() -> { button.setEnabled(true); status.setText(Repository.message(error)); });
            }
        });
    }

    private void checkConnection() {
        handler.removeCallbacks(poll);
        JSONObject state = Store.read(this);
        String token = state.optString("pending_token");
        if (!foreground || polling || token.isEmpty()) return;
        polling = true;
        Repository.IO.execute(() -> {
            try {
                JSONObject result = new Api(token).call("GET", "/mobile/authorization", null);
                if (result.optString("status").equals("authorized")) {
                    synchronized (Store.class) {
                        if (token.equals(Store.read(this).optString("pending_token"))) {
                            Store.put(this, "token", token);
                            Store.put(this, "email", result.getString("email"));
                            Store.put(this, "pending_token", null);
                            Store.put(this, "user_code", null);
                            Store.put(this, "error", "");
                        }
                    }
                    try { Repository.refresh(this); } catch (Exception ignored) { }
                    SyncJob.schedule(this);
                    runOnUiThread(() -> { if (!isDestroyed()) render(); });
                }
            } catch (Exception error) {
                if (error instanceof Api.Failure && ((Api.Failure) error).status == 410) {
                    synchronized (Store.class) {
                        if (token.equals(Store.read(this).optString("pending_token"))) Store.put(this, "pending_token", null);
                    }
                    runOnUiThread(() -> { if (!isDestroyed()) render(); });
                }
                runOnUiThread(() -> status.setText(Repository.message(error)));
            } finally {
                runOnUiThread(() -> { polling = false; if (foreground) handler.postDelayed(poll, 5000); });
            }
        });
    }

    private void pin(Class<?> widget) {
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this, widget), null, null);
        else status.setText("Long-press an empty area on your home screen, choose Widgets, then Steps.");
    }

    private void refresh() {
        status.setText("Refreshing…");
        Repository.IO.execute(() -> {
            try {
                Repository.refresh(this);
                SyncJob.schedule(this);
                runOnUiThread(() -> status.setText("Widgets updated."));
            } catch (Exception error) { runOnUiThread(() -> status.setText(Repository.message(error))); }
        });
    }

    private void disconnect(boolean localOnly) {
        JSONObject state = Store.read(this);
        Repository.IO.execute(() -> {
            try {
                if (!localOnly) new Api(state).call("DELETE", "/mobile/device", null);
            } catch (Exception error) {
                if (!(error instanceof Api.Failure && ((Api.Failure) error).status == 401)) {
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setMessage("Could not revoke access on the server. Retry when connected, or clear this phone now and revoke it later in Profile.")
                        .setNegativeButton("Keep connection", null)
                        .setPositiveButton("Clear this phone", (dialog, which) -> disconnect(true)).show());
                    return;
                }
            }
            Store.clear(this);
            getSystemService(android.app.job.JobScheduler.class).cancelAll();
            Widgets.update(this);
            runOnUiThread(this::render);
        });
    }

    @Override public void onResume() {
        super.onResume();
        foreground = true;
        checkConnection();
        Widgets.update(this);
        if (Store.connected(this)) { SyncJob.schedule(this); SyncJob.now(this); }
    }

    @Override public void onPause() {
        foreground = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }
}
