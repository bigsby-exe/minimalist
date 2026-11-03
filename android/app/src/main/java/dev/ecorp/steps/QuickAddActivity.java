package dev.ecorp.steps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

public class QuickAddActivity extends Activity {
    private EditText text;
    private Spinner tags;
    private CheckBox star;
    private Button dateButton, save, discard, clearDate;
    private TextView status;
    private JSONArray tagData = new JSONArray();
    private String date = "";
    private long chosenTag;
    private JSONObject pending;
    private boolean busy, completed;
    private String accountToken;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        JSONObject state = Store.read(this);
        accountToken = state.optString("token");
        if (accountToken.isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        JSONObject draft = state.optJSONObject("draft");
        if (draft == null) draft = new JSONObject();
        pending = draft.optJSONObject("pending");
        date = draft.optString("due_date");
        chosenTag = draft.optLong("tag_id", state.optLong("last_tag"));
        LinearLayout column = Ui.column(this);
        Ui.text(this, column, "Add a task", 22);
        text = new EditText(this);
        text.setHint("What needs doing?");
        text.setSingleLine(true);
        text.setFilters(new InputFilter[] { new InputFilter.LengthFilter(255) });
        text.setImeOptions(EditorInfo.IME_ACTION_DONE);
        text.setText(draft.optString("description"));
        column.addView(text);
        tags = new Spinner(this);
        tags.setContentDescription("Task tag");
        column.addView(tags, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        dateButton = Ui.button(this, column, date.isEmpty() ? "Choose date (optional)" : date, view -> chooseDate());
        clearDate = Ui.button(this, column, "Clear date", view -> { date = ""; dateButton.setText("Choose date (optional)"); });
        star = new CheckBox(this);
        star.setText("Star for today");
        star.setChecked(draft.optBoolean("starred", state.optBoolean("last_star", false)));
        column.addView(star);
        status = Ui.text(this, column, "", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        save = Ui.button(this, column, "Add task", view -> submit());
        discard = Ui.button(this, column, "Discard draft", view -> new AlertDialog.Builder(this)
            .setMessage(pending == null ? "Discard this unfinished task?" : "Discard this draft? An unconfirmed submission may already exist on the server.")
            .setNegativeButton("Keep draft", null).setPositiveButton("Discard", (dialog, which) -> {
                completed = true;
                Store.put(this, "draft", null);
                finish();
            }).show());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24), Ui.dp(this, 460)), WindowManager.LayoutParams.WRAP_CONTENT);
        text.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE || event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                submit();
                return true;
            }
            return false;
        });
        loadTags(state.optJSONObject("workspace"));
        updateControls();
        text.requestFocus();
        Repository.IO.execute(() -> {
            try { Repository.refresh(this); }
            catch (Exception error) { runOnUiThread(() -> status.setText(Repository.message(error))); }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                chosenTag = selectedTag();
                loadTags(Store.read(this).optJSONObject("workspace"));
                updateControls();
            });
        });
    }

    private void loadTags(JSONObject workspace) {
        if (workspace == null || workspace.optJSONArray("tags") == null) return;
        tagData = workspace.optJSONArray("tags");
        ArrayList<String> names = new ArrayList<>();
        int selection = -1;
        for (int i = 0; i < tagData.length(); i++) {
            JSONObject tag = tagData.optJSONObject(i);
            names.add(tag.optString("emoji") + " " + tag.optString("name"));
            if (tag.optLong("id") == chosenTag) selection = i;
        }
        if (chosenTag > 0 && selection < 0) {
            names.add("Previous tag removed. Choose another tag.");
            selection = names.size() - 1;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tags.setAdapter(adapter);
        tags.setSelection(Math.max(0, selection));
        if (names.isEmpty()) status.setText("Create a tag in your web Profile before adding tasks.");
    }

    private long selectedTag() {
        JSONObject tag = tagData.optJSONObject(tags.getSelectedItemPosition());
        return tag == null ? chosenTag : tag.optLong("id");
    }

    private void chooseDate() {
        LocalDate initial = date.isEmpty() ? LocalDate.now() : LocalDate.parse(date);
        new DatePickerDialog(this, (picker, year, month, day) -> {
            date = LocalDate.of(year, month + 1, day).toString();
            dateButton.setText(date);
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void updateControls() {
        boolean editable = pending == null && !busy;
        text.setEnabled(editable);
        tags.setEnabled(editable);
        dateButton.setEnabled(editable);
        clearDate.setEnabled(editable);
        star.setEnabled(editable);
        save.setEnabled(!busy && (pending != null || tagData.length() > 0));
        discard.setEnabled(!busy);
        save.setText(pending == null ? "Add task" : "Retry submission");
        if (pending != null && !busy) status.setText("Submission not confirmed. Retry safely, or discard this draft before starting another task.");
    }

    private boolean saveDraft() {
        if (completed || text == null || !accountToken.equals(Store.read(this).optString("token"))) return false;
        try {
            Store.put(this, "draft", new JSONObject().put("description", text.getText().toString())
                .put("tag_id", selectedTag()).put("due_date", date).put("starred", star.isChecked()).put("pending", pending));
            return true;
        } catch (Exception error) {
            status.setText("Could not save the draft on this phone.");
            return false;
        }
    }

    private void submit() {
        if (busy || completed) return;
        if (!accountToken.equals(Store.read(this).optString("token"))) {
            status.setText("Phone disconnected. Open Steps to connect again.");
            return;
        }
        try {
            if (pending == null) {
                if (text.getText().toString().trim().isEmpty()) { text.setError("Enter a task name"); return; }
                if (tags.getSelectedItemPosition() < 0 || tags.getSelectedItemPosition() >= tagData.length()) {
                    status.setText("Choose an available tag."); return;
                }
                JSONObject task = new JSONObject().put("description", text.getText().toString().trim())
                    .put("tag_id", selectedTag()).put("due_date", date);
                pending = new JSONObject().put("request_id", UUID.randomUUID().toString()).put("task", task).put("starred", star.isChecked());
            }
            // Persist the exact request before sending; never submit if durable storage fails.
            if (!saveDraft()) return;
            JSONObject payload = new JSONObject(pending.toString());
            JSONObject snapshot = Store.read(this);
            busy = true;
            updateControls();
            status.setText("Adding…");
            Repository.IO.execute(() -> {
                try {
                    new Api(snapshot).call("POST", "/mobile/tasks", payload);
                    synchronized (Store.class) {
                        if (Repository.sameAccount(this, snapshot)) {
                            Store.put(this, "draft", null);
                            Store.put(this, "last_tag", payload.getJSONObject("task").getLong("tag_id"));
                            Store.put(this, "last_star", payload.getBoolean("starred"));
                        }
                    }
                    runOnUiThread(() -> { completed = true; finish(); });
                    try { Repository.refresh(this); } catch (Exception ignored) { }
                    SyncJob.schedule(this);
                } catch (Exception error) {
                    Repository.recordFailure(this, snapshot, error);
                    runOnUiThread(() -> {
                        busy = false;
                        if (error instanceof Api.Failure && ((Api.Failure) error).status == 422) pending = null;
                        updateControls();
                        status.setText(Repository.message(error));
                        saveDraft();
                    });
                }
            });
        } catch (Exception error) { status.setText("Could not prepare this task. Try again."); }
    }

    @Override public void onPause() { saveDraft(); super.onPause(); }
}
