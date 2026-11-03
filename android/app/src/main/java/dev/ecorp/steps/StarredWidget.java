package dev.ecorp.steps;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class StarredWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Widgets.update(context);
        SyncJob.schedule(context);
        SyncJob.now(context);
    }
}
