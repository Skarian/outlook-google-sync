package com.skarian.outlookgooglesync;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class UploadMonitorJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                UploadTracker.checkPending(getApplicationContext());
            } catch (Exception e) {
                AppLog.log(getApplicationContext(), "Google upload check failed: " + e.getMessage());
                AppLog.log(getApplicationContext(), e);
            } finally {
                jobFinished(params, false);
            }
        }, "upload-monitor-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
