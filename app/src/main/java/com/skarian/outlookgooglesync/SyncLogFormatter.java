package com.skarian.outlookgooglesync;

import java.util.Locale;

final class SyncLogFormatter {
    private SyncLogFormatter() {}

    static String syncFinished(String label, SyncResult result, long startedAt,
                               long finishedAt, long targetCalendarId, UploadState uploadState) {
        StringBuilder out = new StringBuilder();
        out.append('[').append(AppLog.blockTimestamp(finishedAt)).append("] ").append(label).append('\n');
        out.append("Result: ").append(result.applied ? "Success" : "Complete")
                .append(" in ").append(formatDuration(finishedAt - startedAt)).append("\n\n");
        out.append("Outlook events read: ").append(result.sourceOccurrences).append('\n');
        out.append("Mirrored calendar events before sync: ").append(result.existingMirroredOccurrences).append('\n');
        out.append("Added: ").append(result.created).append('\n');
        out.append("Updated: ").append(result.updated).append('\n');
        out.append("Removed canceled upcoming meetings: ").append(result.deletedFuture).append('\n');
        out.append("Kept completed meetings for history: ").append(result.keptPastStale).append('\n');
        out.append("Selected Google Calendar: #").append(targetCalendarId).append('\n');
        out.append("Sync range: ").append(SyncResult.format(result.horizonStart))
                .append(" to ").append(SyncResult.format(result.horizonEnd)).append('\n');
        if (!result.deleteBlockedReason.isEmpty()) {
            out.append("Skipped removals: ").append(result.deleteBlockedReason).append('\n');
        }
        if (result.applied) {
            out.append('\n');
            out.append("Google Calendar upload: ")
                    .append(uploadState.isComplete() ? "Complete" : "Pending")
                    .append('\n');
            out.append("Local upload state: ").append(uploadState.cleanActive).append(" uploaded, ")
                    .append(uploadState.dirtyActive).append(" waiting, ")
                    .append(uploadState.dirtyDeleted).append(" removals waiting");
        }
        return out.toString();
    }

    static String syncFailed(String label, long startedAt, Throwable throwable) {
        long finishedAt = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append('[').append(AppLog.blockTimestamp(finishedAt)).append("] ").append(label).append('\n');
        out.append("Result: Failed after ").append(formatDuration(finishedAt - startedAt)).append('\n');
        out.append("Reason: ").append(throwable.getClass().getSimpleName());
        if (throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
            out.append(" - ").append(throwable.getMessage());
        }
        out.append('\n');
        out.append("Changes: none applied\n");
        out.append("Google Calendar upload: Not started");
        return out.toString();
    }

    static String uploadComplete(String label, long syncFinishedAt, UploadState state) {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append('[').append(AppLog.blockTimestamp(now)).append("] Google Calendar upload\n");
        out.append("Result: Complete after ").append(formatDuration(now - syncFinishedAt)).append('\n');
        out.append("For: ").append(label).append(" at ").append(AppLog.blockTimestamp(syncFinishedAt)).append('\n');
        out.append("Local upload state: ").append(state.cleanActive).append(" uploaded, 0 waiting, 0 removals waiting");
        return out.toString();
    }

    static String uploadPending(String label, long syncFinishedAt, int attempt, UploadState state) {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append('[').append(AppLog.blockTimestamp(now)).append("] Google Calendar upload\n");
        out.append("Result: Still waiting after ").append(formatDuration(now - syncFinishedAt)).append('\n');
        out.append("For: ").append(label).append(" at ").append(AppLog.blockTimestamp(syncFinishedAt)).append('\n');
        out.append("Check: ").append(attempt).append('\n');
        out.append("Local upload state: ").append(state.cleanActive).append(" uploaded, ")
                .append(state.dirtyActive).append(" waiting, ")
                .append(state.dirtyDeleted).append(" removals waiting");
        return out.toString();
    }

    static String uploadGaveUp(String label, long syncFinishedAt, int attempt, UploadState state) {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append('[').append(AppLog.blockTimestamp(now)).append("] Google Calendar upload\n");
        out.append("Result: Still pending after ").append(formatDuration(now - syncFinishedAt)).append('\n');
        out.append("For: ").append(label).append(" at ").append(AppLog.blockTimestamp(syncFinishedAt)).append('\n');
        out.append("Checks: ").append(attempt).append('\n');
        out.append("Local upload state: ").append(state.cleanActive).append(" uploaded, ")
                .append(state.dirtyActive).append(" waiting, ")
                .append(state.dirtyDeleted).append(" removals waiting");
        return out.toString();
    }

    static String formatDuration(long millis) {
        long seconds = Math.max(0L, Math.round(millis / 1000.0));
        if (seconds < 60L) {
            return String.format(Locale.US, "%.1fs", millis / 1000.0);
        }
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        if (minutes < 60L) {
            return remainder == 0L ? minutes + "m" : minutes + "m " + remainder + "s";
        }
        long hours = minutes / 60L;
        long minuteRemainder = minutes % 60L;
        return minuteRemainder == 0L ? hours + "h" : hours + "h " + minuteRemainder + "m";
    }
}
