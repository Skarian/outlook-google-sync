package com.skarian.outlookgooglesync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CalendarContract;

final class UploadTracker {
    private static final String CUSTOM_PACKAGE = "customAppPackage";
    private static final int JOB_ID = 204732;
    private static final long CHECK_DELAY_MS = 60L * 1000L;
    private static final int MAX_CHECKS = 60;
    private static final String KEY_PENDING = "upload_pending";
    private static final String KEY_LABEL = "upload_label";
    private static final String KEY_SYNC_FINISHED_AT = "upload_sync_finished_at";
    private static final String KEY_TARGET_CALENDAR_ID = "upload_target_calendar_id";
    private static final String KEY_ATTEMPT = "upload_attempt";

    private UploadTracker() {}

    static UploadState queryState(Context context, long targetCalendarId) {
        return new UploadState(
                count(context, targetCalendarId, "deleted=0"),
                count(context, targetCalendarId, "deleted=0 AND dirty=0"),
                count(context, targetCalendarId, "deleted=0 AND dirty=1"),
                count(context, targetCalendarId, "deleted=1 AND dirty=1")
        );
    }

    static void begin(Context context, String label, long syncFinishedAt,
                      long targetCalendarId, UploadState state) {
        if (state.isComplete()) {
            HistoryStore.recordUpload(context, label, syncFinishedAt, HistoryStore.STATUS_COMPLETE,
                    "Complete immediately", 0, state);
            clearPending(context);
            return;
        }
        HistoryStore.recordUpload(context, label, syncFinishedAt, HistoryStore.STATUS_WAITING,
                "Waiting for Google Calendar to upload local changes", 0, state);
        AppConfig.prefs(context).edit()
                .putBoolean(KEY_PENDING, true)
                .putString(KEY_LABEL, label)
                .putLong(KEY_SYNC_FINISHED_AT, syncFinishedAt)
                .putLong(KEY_TARGET_CALENDAR_ID, targetCalendarId)
                .putInt(KEY_ATTEMPT, 0)
                .apply();
        schedule(context);
    }

    static void checkPending(Context context) {
        SharedPreferences prefs = AppConfig.prefs(context);
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            return;
        }

        String label = prefs.getString(KEY_LABEL, "Sync");
        long syncFinishedAt = prefs.getLong(KEY_SYNC_FINISHED_AT, System.currentTimeMillis());
        long targetCalendarId = prefs.getLong(KEY_TARGET_CALENDAR_ID, -1L);
        int attempt = prefs.getInt(KEY_ATTEMPT, 0) + 1;
        if (targetCalendarId < 0L) {
            clearPending(context);
            return;
        }

        UploadState state = queryState(context, targetCalendarId);
        prefs.edit().putInt(KEY_ATTEMPT, attempt).apply();

        if (state.isComplete()) {
            AppLog.logBlock(context, SyncLogFormatter.uploadComplete(label, syncFinishedAt, state));
            HistoryStore.recordUpload(context, label, syncFinishedAt, HistoryStore.STATUS_COMPLETE,
                    "Complete after " + SyncLogFormatter.formatDuration(System.currentTimeMillis() - syncFinishedAt),
                    attempt, state);
            clearPending(context);
            return;
        }

        if (attempt >= MAX_CHECKS) {
            AppLog.logBlock(context, SyncLogFormatter.uploadGaveUp(label, syncFinishedAt, attempt, state));
            HistoryStore.recordUpload(context, label, syncFinishedAt, HistoryStore.STATUS_STILL_WAITING,
                    "Still pending after " + SyncLogFormatter.formatDuration(System.currentTimeMillis() - syncFinishedAt),
                    attempt, state);
            clearPending(context);
            return;
        }

        if (attempt == 1 || attempt % 5 == 0) {
            AppLog.logBlock(context, SyncLogFormatter.uploadPending(label, syncFinishedAt, attempt, state));
            HistoryStore.recordUpload(context, label, syncFinishedAt, HistoryStore.STATUS_STILL_WAITING,
                    "Still waiting after " + SyncLogFormatter.formatDuration(System.currentTimeMillis() - syncFinishedAt),
                    attempt, state);
        }
        schedule(context);
    }

    private static void clearPending(Context context) {
        AppConfig.prefs(context).edit()
                .remove(KEY_PENDING)
                .remove(KEY_LABEL)
                .remove(KEY_SYNC_FINISHED_AT)
                .remove(KEY_TARGET_CALENDAR_ID)
                .remove(KEY_ATTEMPT)
                .apply();
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }

    private static void schedule(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UploadMonitorJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(CHECK_DELAY_MS)
                .setOverrideDeadline(CHECK_DELAY_MS * 2L)
                .setPersisted(true)
                .build();
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.schedule(job);
        }
    }

    private static int count(Context context, long targetCalendarId, String extraSelection) {
        String selection = "calendar_id=? AND " + CUSTOM_PACKAGE + "=? AND " + extraSelection;
        String[] args = {Long.toString(targetCalendarId), context.getPackageName()};
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{"_id"},
                selection,
                args,
                null
        )) {
            return cursor == null ? 0 : cursor.getCount();
        }
    }
}
