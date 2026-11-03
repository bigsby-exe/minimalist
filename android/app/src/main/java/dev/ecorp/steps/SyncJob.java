package dev.ecorp.steps;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import org.json.JSONObject;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SyncJob extends JobService {
    private final Set<JobParameters> stopped = ConcurrentHashMap.newKeySet();

    static void schedule(Context context) {
        if (!Store.connected(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        ComponentName service = new ComponentName(context, SyncJob.class);
        if (jobs.getPendingJob(1) == null) {
            jobs.schedule(new JobInfo.Builder(1, service).setPeriodic(30 * 60 * 1000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build());
        }
        JSONObject workspace = Store.read(context).optJSONObject("workspace");
        ZoneId zone = ZoneId.of(workspace == null ? "Etc/UTC" : workspace.optString("time_zone", "Etc/UTC"));
        ZonedDateTime now = ZonedDateTime.now(zone);
        long delay = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toMillis();
        jobs.schedule(new JobInfo.Builder(3, service).setMinimumLatency(delay).setOverrideDeadline(delay + 5 * 60 * 1000L).setPersisted(true).build());
    }

    static void now(Context context) {
        if (Store.connected(context)) context.getSystemService(JobScheduler.class).schedule(
            new JobInfo.Builder(2, new ComponentName(context, SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).build());
    }

    @Override public boolean onStartJob(JobParameters parameters) {
        Widgets.update(this); // Expire cached stars even if the network is unavailable.
        Repository.IO.execute(() -> {
            try { Repository.refresh(this); } catch (Exception ignored) { /* Visible in widget status. */ }
            if (!stopped.remove(parameters)) jobFinished(parameters, false);
            if (parameters.getJobId() == 3) schedule(this);
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        stopped.add(parameters);
        return true;
    }
}
