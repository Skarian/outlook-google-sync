package com.skarian.outlookgooglesync;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class SyncJobService extends JobService {
    private Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        long startedAt = System.currentTimeMillis();
        worker = new Thread(() -> {
            try {
                SyncEngine.run(getApplicationContext(), true, "Scheduled sync", null);
            } catch (Exception e) {
                AppLog.logBlock(getApplicationContext(), SyncLogFormatter.syncFailed("Scheduled sync", startedAt, e));
                AppLog.log(getApplicationContext(), e);
                HistoryStore.recordSyncFailure(getApplicationContext(), "Scheduled sync", startedAt, e);
            } finally {
                jobFinished(params, false);
            }
        }, "calendar-sync-job");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (worker != null) {
            worker.interrupt();
        }
        return true;
    }
}
