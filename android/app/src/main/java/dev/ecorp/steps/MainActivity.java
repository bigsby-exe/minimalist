package dev.ecorp.steps;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import com.google.androidbrowserhelper.trusted.TwaLauncher;
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
    private boolean openingWeb;
    private TwaLauncher web;
    private boolean webLaunched;
    private boolean webDeparted;
    private final Runnable poll = this::checkConnection;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new TwaLauncher(this);
        handleLaunch();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        webLaunched = false;
        webDeparted = false;
        handleLaunch();
    }

    private void handleLaunch() {
        Uri uri = getIntent().getData();
        String action = uri != null && "steps".equals(uri.getScheme()) && "phone".equals(uri.getHost()) ? uri.getPath() : "";
        render();
        if (!Store.connected(this)) return;
        if ("/signout".equals(action)) confirmSignOut();
        else if (!"/settings".equals(action)) {
            LinearLayout loading = Ui.column(this);
            status = Ui.text(this, loading, "Opening your tasks…", 18);
            setContentView(loading);
            openWorkspace();
        }
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this).setMessage("Sign out of Steps and disconnect this phone’s widgets?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign out", (dialog, which) -> openWeb(true)).show();
    }

    private void openWorkspace() { openWeb(false); }

    private void openWeb(boolean signOut) {
        if (openingWeb) return;
        openingWeb = true;
        status.setText(signOut ? "Signing out…" : "Opening your tasks…");
        JSONObject snapshot = Store.read(this);
        Repository.IO.execute(() -> {
            try {
                String destination = getIntent().getStringExtra("destination");
                JSONObject body = new JSONObject().put("purpose", signOut ? "sign_out" : "open")
                    .put("destination", destination == null ? "/dashboard" : destination);
                JSONObject result = new Api(snapshot).call("POST", "/mobile/browser_handoff", body);
                synchronized (Store.class) {
                    if (!Repository.sameAccount(this, snapshot)) return;
                    if (signOut) {
                        Store.clear(this);
                        getSystemService(android.app.job.JobScheduler.class).cancelAll();
                        Widgets.update(this);
                    }
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    webLaunched = true;
                    webDeparted = false;
                    web.launch(Uri.parse(BuildConfig.SERVER_URL + result.optString("path")));
                });
            } catch (Exception error) {
                Repository.recordFailure(this, snapshot, error);
                runOnUiThread(() -> { if (!isDestroyed()) { render(); status.setText(Repository.message(error)); } });
            } finally {
                runOnUiThread(() -> openingWeb = false);
            }
        });
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
        Ui.text(this, column, Store.connected(this) ? "Phone and widgets" : "Steps", 28);
        Ui.text(this, column, "steps.ecorp.dev", 14);
        status = Ui.text(this, column, state.optString("error"), 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        if (Store.connected(this)) {
            Ui.text(this, column, "Connected as " + state.optString("email"), 16);
            Ui.button(this, column, "Back to tasks", view -> openWorkspace());
            Ui.text(this, column, "Add widgets by long-pressing an empty area of your home screen, choosing Widgets, then Steps. Widgets refresh automatically.", 16);
            Ui.button(this, column, "Sign out", view -> confirmSignOut());
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
        Ui.text(this, column, "Version " + BuildConfig.VERSION_NAME, 12);
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
                    runOnUiThread(() -> { if (!isDestroyed()) { render(); openWorkspace(); } });
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

    @Override public void onResume() {
        super.onResume();
        if (webDeparted) { finish(); return; }
        foreground = true;
        checkConnection();
        Widgets.update(this);
        if (Store.connected(this)) { SyncJob.schedule(this); SyncJob.now(this); }
    }

    @Override public void onStop() {
        super.onStop();
        if (webLaunched) webDeparted = true;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(poll);
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override public void onPause() {
        foreground = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }
}
