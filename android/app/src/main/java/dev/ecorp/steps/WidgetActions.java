package dev.ecorp.steps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class WidgetActions extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Widgets.update(context);
        SyncJob.now(context);
        Toast.makeText(context, Store.connected(context) ? "Refresh requested" : "Open Steps to connect your account", Toast.LENGTH_SHORT).show();
    }
}
