package dev.ecorp.steps;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceStateTest {
    private Context context;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSystemService(android.app.job.JobScheduler.class).cancelAll();
        Store.clear(context);
    }
    @After public void cleanup() {
        Store.clear(context);
        context.getSystemService(android.app.job.JobScheduler.class).cancelAll();
    }

    @Test public void credentialsAndDraftsAreEncryptedAndClearedTogether() throws Exception {
        Store.put(context, "token", "private-device-token");
        Store.put(context, "draft", new JSONObject().put("description", "Private medical task"));
        String disk = context.getSharedPreferences("vault", Context.MODE_PRIVATE).getString("state", "");
        assertFalse(disk.contains("private-device-token"));
        assertFalse(disk.contains("Private medical task"));
        assertEquals("Private medical task", Store.read(context).getJSONObject("draft").getString("description"));
        JSONObject snapshot = Store.read(context);
        Repository.recordFailure(context, snapshot, new Api.Failure(401, "Revoked"));
        assertFalse(Store.connected(context));
        assertFalse(Store.read(context).has("draft"));
    }

    @Test public void staleNetworkResponsesCannotClearANewAccount() {
        Store.put(context, "token", "old-account");
        JSONObject old = Store.read(context);
        Store.put(context, "token", "new-account");
        Repository.recordFailure(context, old, new Api.Failure(401, "Revoked"));
        assertEquals("new-account", Store.read(context).optString("token"));
    }

    @Test public void dismissedQuickAddRetainsTextAndWidgetRefreshLeavesTheEditorAlone() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Store.put(context, "token", "offline-draft-test");
        Store.put(context, "workspace", new JSONObject().put("tags", new JSONArray()
            .put(new JSONObject().put("id", 1).put("name", "Home").put("emoji", "🏠"))));
        QuickAddActivity activity = (QuickAddActivity) instrumentation.startActivitySync(
            new Intent(context, QuickAddActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            EditText editor = findEditor(activity.getWindow().getDecorView());
            assertNotNull(editor);
            editor.setText("A draft that survives midnight");
            Widgets.update(context);
            assertEquals("A draft that survives midnight", editor.getText().toString());
        });
        assertEquals("offline-draft-test", Store.read(context).optString("token"));
        finishAndWait(activity);
        assertEquals("A draft that survives midnight", Store.read(context).getJSONObject("draft").getString("description"));
        QuickAddActivity reopened = (QuickAddActivity) instrumentation.startActivitySync(
            new Intent(context, QuickAddActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            assertEquals("A draft that survives midnight", findEditor(reopened.getWindow().getDecorView()).getText().toString());
        });
        finishAndWait(reopened);
    }

    @Test public void externalSignoutLinkRequiresConfirmation() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Store.put(context, "token", "offline-confirmation-test");
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
            new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("steps://phone/signout"), context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        assertTrue(Store.connected(context));
        var root = instrumentation.getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);
        var cancel = root.findAccessibilityNodeInfosByText("Cancel");
        assertFalse(cancel.isEmpty());
        cancel.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        instrumentation.waitForIdleSync();
        assertTrue(Store.connected(context));
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private void finishAndWait(android.app.Activity activity) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var destroyed = new java.util.concurrent.CountDownLatch(1);
        androidx.test.runner.lifecycle.ActivityLifecycleCallback callback = (candidate, stage) -> {
            if (candidate == activity && stage == androidx.test.runner.lifecycle.Stage.DESTROYED) destroyed.countDown();
        };
        var monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance();
        instrumentation.runOnMainSync(() -> { monitor.addLifecycleCallback(callback); activity.finish(); });
        try { assertTrue("Activity destruction timed out", destroyed.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
        finally { instrumentation.runOnMainSync(() -> monitor.removeLifecycleCallback(callback)); }
    }

    private EditText findEditor(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditor(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void widgetRendersTagsAndFiltersExpiredStarsWithoutTouchingDraft() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
        AppWidgetHost host = new AppWidgetHost(context, 42);
        int id = host.allocateAppWidgetId();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        try {
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, new ComponentName(context, StarredWidget.class)));
            instrumentation.waitForIdleSync();
            JSONObject workspace = new JSONObject().put("time_zone", "Europe/London")
                .put("tags", new JSONArray().put(new JSONObject().put("id", 1).put("name", "Home").put("emoji", "🏠").put("color", "#dcecff")))
                .put("tasks", new JSONArray()
                    .put(new JSONObject().put("id", 1).put("tag_id", 1).put("description", "Visible task").put("starred_until", Instant.now().plusSeconds(3600).toString()))
                    .put(new JSONObject().put("id", 2).put("tag_id", 1).put("description", "Expired task").put("starred_until", Instant.now().minusSeconds(1).toString())));
            Store.put(context, "token", "widget-fixture");
            Store.put(context, "workspace", workspace);
            Store.put(context, "draft", new JSONObject().put("description", "Unfinished draft"));
            AtomicReference<AppWidgetHostView> view = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                host.startListening();
                view.set(host.createView(context, id, manager.getAppWidgetInfo(id)));
                Widgets.update(context);
            });
            long deadline = System.currentTimeMillis() + 5000;
            AtomicReference<Integer> rows = new AtomicReference<>(0);
            while (System.currentTimeMillis() < deadline) {
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> {
                    ListView list = view.get().findViewById(R.id.tasks);
                    rows.set(list == null || list.getAdapter() == null ? 0 : list.getAdapter().getCount());
                });
                if (rows.get() == 2) break;
                Thread.sleep(50);
            }
            assertEquals("One tag heading and one unexpired task", 2, rows.get().intValue());
            assertEquals("Unfinished draft", Store.read(context).getJSONObject("draft").getString("description"));
        } finally {
            host.stopListening();
            host.deleteAppWidgetId(id);
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
