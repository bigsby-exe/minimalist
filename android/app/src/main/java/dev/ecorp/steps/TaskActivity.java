package dev.ecorp.steps;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONObject;

public class TaskActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!Store.connected(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        long id = getIntent().getLongExtra("task_id", 0);
        if (!getIntent().getBooleanExtra("unstar", false) || id <= 0) {
            startActivity(new Intent(this, MainActivity.class).putExtra("destination", "/dashboard?view=starred" + (id > 0 ? "#task-" + id : "")));
            SyncJob.now(this);
            finish();
            return;
        }
        LinearLayout content = Ui.column(this);
        Ui.text(this, content, "Removing star…", 18);
        setContentView(content);
        JSONObject snapshot = Store.read(this);
        Repository.IO.execute(() -> {
            try {
                new Api(snapshot).call("DELETE", "/mobile/tasks/" + id + "/star", null);
                // Reflect a successful mutation even if the following refresh loses network.
                synchronized (Store.class) {
                    if (Repository.sameAccount(this, snapshot)) {
                        JSONObject state = Store.read(this);
                        JSONObject workspace = state.optJSONObject("workspace");
                        if (workspace != null) {
                            var tasks = workspace.optJSONArray("tasks");
                            for (int i = 0; tasks != null && i < tasks.length(); i++) {
                                if (tasks.getJSONObject(i).optLong("id") == id) tasks.getJSONObject(i).put("starred_until", JSONObject.NULL);
                            }
                            Store.put(this, "workspace", workspace);
                        }
                    }
                }
                Widgets.update(this);
                try { Repository.refresh(this); } catch (Exception ignored) { }
            } catch (Exception error) {
                Repository.recordFailure(this, snapshot, error);
                runOnUiThread(() -> Toast.makeText(this, Repository.message(error), Toast.LENGTH_LONG).show());
            }
            runOnUiThread(this::finish);
        });
    }
}
