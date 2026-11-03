package dev.ecorp.steps;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

final class Browser {
    static void open(Activity activity, String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SERVER_URL + path));
        Bundle extras = new Bundle();
        // Standard Custom Tabs extras; browsers without support open an ordinary tab.
        extras.putBinder("android.support.customtabs.extra.SESSION", null);
        intent.putExtras(extras);
        intent.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1);
        activity.startActivity(intent);
    }
}
