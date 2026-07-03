package com.skarian.outlookgooglesync;

import java.text.SimpleDateFormat;
import java.util.Locale;

final class SyncResult {
    final int sourceOccurrences;
    final int existingMirroredOccurrences;
    final int created;
    final int updated;
    final int deletedFuture;
    final int keptPastStale;
    final String deleteBlockedReason;
    final boolean applied;
    final long horizonStart;
    final long horizonEnd;
    final String preview;
    final long targetCalendarId;
    final UploadState uploadState;

    SyncResult(int sourceOccurrences, int existingMirroredOccurrences, int created, int updated,
               int deletedFuture, int keptPastStale, String deleteBlockedReason, boolean applied,
               long horizonStart, long horizonEnd, String preview, long targetCalendarId,
               UploadState uploadState) {
        this.sourceOccurrences = sourceOccurrences;
        this.existingMirroredOccurrences = existingMirroredOccurrences;
        this.created = created;
        this.updated = updated;
        this.deletedFuture = deletedFuture;
        this.keptPastStale = keptPastStale;
        this.deleteBlockedReason = deleteBlockedReason == null ? "" : deleteBlockedReason;
        this.applied = applied;
        this.horizonStart = horizonStart;
        this.horizonEnd = horizonEnd;
        this.preview = preview == null ? "" : preview;
        this.targetCalendarId = targetCalendarId;
        this.uploadState = uploadState;
    }

    String summary() {
        String mode = applied ? "Sync complete" : "Preview changes";
        return mode
                + "\nSync range: " + format(horizonStart) + " to " + format(horizonEnd)
                + "\nOutlook events read: " + sourceOccurrences
                + "\nMirrored calendar events before sync: " + existingMirroredOccurrences
                + "\n" + HistoryStore.changeCounts(this)
                + (deleteBlockedReason.isEmpty() ? "" : "\nSkipped removals: " + deleteBlockedReason)
                + (preview.isEmpty() ? "" : "\n\n" + preview);
    }

    String oneLineSummary() {
        return (applied ? "Sync" : "Preview")
                + " finished: source=" + sourceOccurrences
                + " existing=" + existingMirroredOccurrences
                + " create=" + created
                + " update=" + updated
                + " deleteFuture=" + deletedFuture
                + " keepPast=" + keptPastStale
                + (deleteBlockedReason.isEmpty() ? "" : " skippedDeletes=\"" + deleteBlockedReason + "\"");
    }

    static String format(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(millis);
    }
}
