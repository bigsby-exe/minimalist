package dev.ecorp.steps;

import android.content.Context;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Repository {
    // Network writes are serialized, including refreshes triggered by multiple widgets.
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static boolean sameAccount(Context context, JSONObject snapshot) {
        String token = snapshot.optString("token");
        return !token.isEmpty() && token.equals(Store.read(context).optString("token"));
    }

    static void refresh(Context context) throws Exception {
        JSONObject snapshot = Store.read(context);
        if (snapshot.optString("token").isEmpty()) return;
        try {
            JSONObject workspace = new Api(snapshot).call("GET", "/mobile/workspace", null);
            synchronized (Store.class) {
                if (!sameAccount(context, snapshot)) return;
                Store.put(context, "workspace", workspace);
                Store.put(context, "last_sync", System.currentTimeMillis());
                Store.put(context, "error", "");
            }
        } catch (Exception error) {
            recordFailure(context, snapshot, error);
            throw error;
        } finally { Widgets.update(context); }
    }

    static void recordFailure(Context context, JSONObject snapshot, Exception error) {
        synchronized (Store.class) {
            if (!sameAccount(context, snapshot)) return;
            if (error instanceof Api.Failure && ((Api.Failure) error).status == 401) {
                Store.clear(context);
                Store.put(context, "error", "Phone disconnected. Open Steps to connect again.");
            } else {
                Store.put(context, "error", message(error));
            }
        }
        Widgets.update(context);
    }

    static String message(Exception error) {
        if (error instanceof Api.Failure) return error.getMessage();
        return "Could not connect. Check your internet connection and try again.";
    }
}
