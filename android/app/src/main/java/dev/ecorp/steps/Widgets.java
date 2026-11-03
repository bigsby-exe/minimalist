package dev.ecorp.steps;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class Widgets {
    static PendingIntent activity(Context context, Class<?> target, int id) {
        return PendingIntent.getActivity(context, id, new Intent(context, target), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void update(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        JSONObject state = Store.read(context);
        JSONObject workspace = state.optJSONObject("workspace");
        boolean connected = !state.optString("token").isEmpty();
        String status = connected ? "Refresh to load tasks" : "Tap Starred to connect Steps";
        if (state.optLong("last_sync") > 0) {
            status = "Updated " + DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(state.optLong("last_sync")));
        }
        if (!state.optString("error").isEmpty()) status += " · " + state.optString("error");

        RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2);
        int count = 0;
        if (connected && workspace != null) {
            JSONArray tags = workspace.optJSONArray("tags");
            JSONArray tasks = workspace.optJSONArray("tasks");
            if (tags != null && tasks != null) for (int t = 0; t < tags.length(); t++) {
                JSONObject tag = tags.optJSONObject(t);
                boolean headingAdded = false;
                for (int i = 0; i < tasks.length(); i++) {
                    JSONObject task = tasks.optJSONObject(i);
                    if (task.optLong("tag_id") != tag.optLong("id") || !TaskDates.active(task.optString("starred_until"), Instant.now())) continue;
                    count++;
                    // Bound the Binder payload; the web view always contains the full list.
                    if (count > 100) continue;
                    if (!headingAdded) {
                        RemoteViews heading = new RemoteViews(context.getPackageName(), R.layout.widget_heading);
                        heading.setTextViewText(R.id.heading, tag.optString("emoji") + " " + tag.optString("name"));
                        items.addItem(-tag.optLong("id"), heading);
                        headingAdded = true;
                    }
                    RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_task);
                    row.setTextViewText(R.id.name, task.optString("description"));
                    String date = TaskDates.label(task.optString("due_date"), workspace.optString("time_zone", "Etc/UTC"), Instant.now());
                    row.setTextViewText(R.id.date, date);
                    row.setViewVisibility(R.id.date, date.isEmpty() ? View.GONE : View.VISIBLE);
                    row.setInt(R.id.row, "setBackgroundColor", Color.parseColor(tag.optString("color", "#dcecff")));
                    row.setContentDescription(R.id.star, "Unstar " + task.optString("description"));
                    row.setOnClickFillInIntent(R.id.star, new Intent().putExtra("task_id", task.optLong("id")).putExtra("unstar", true));
                    row.setOnClickFillInIntent(R.id.task_link, new Intent().putExtra("task_id", task.optLong("id")));
                    items.addItem(task.optLong("id"), row);
                }
            }
        }
        if (count > 100) status += " · First 100 shown. Open Starred for all " + count + ".";
        RemoteViews.RemoteCollectionItems collection = items.build();
        for (int id : manager.getAppWidgetIds(new ComponentName(context, StarredWidget.class))) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_starred);
            views.setTextViewText(R.id.status, status);
            views.setTextViewText(R.id.empty, connected ? "No starred tasks" : "Connect your account in Steps");
            views.setEmptyView(R.id.tasks, R.id.empty);
            views.setRemoteAdapter(R.id.tasks, collection);
            Intent taskIntent = new Intent(context, TaskActivity.class);
            // Mutable only for the launcher's collection fill-in extras; target is explicit.
            views.setPendingIntentTemplate(R.id.tasks, PendingIntent.getActivity(context, id, taskIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
            views.setOnClickPendingIntent(R.id.title, connected ? activity(context, TaskActivity.class, 0) : activity(context, MainActivity.class, 0));
            views.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(context, 0, new Intent(context, WidgetActions.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, views);
        }
        for (int id : manager.getAppWidgetIds(new ComponentName(context, AddWidget.class))) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_add);
            views.setOnClickPendingIntent(R.id.add, activity(context, connected ? QuickAddActivity.class : MainActivity.class, 1));
            manager.updateAppWidget(id, views);
        }
    }
}
