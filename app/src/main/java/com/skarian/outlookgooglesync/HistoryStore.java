package com.skarian.outlookgooglesync;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class HistoryStore {
    static final String STATUS_SUCCESS = "Success";
    static final String STATUS_FAILED = "Failed";
    static final String STATUS_COMPLETE = "Complete";
    static final String STATUS_WAITING = "Waiting";
    static final String STATUS_STILL_WAITING = "Still waiting";
    static final String STATUS_PENDING = "Pending";

    private static final String FILE_NAME = "sync-history.jsonl";
    private static final int MAX_ENTRIES = 120;

    private HistoryStore() {}

    static synchronized void recordSync(Context context, String label, SyncResult result, long startedAt,
                                        long finishedAt, long targetCalendarId, UploadState uploadState) {
        try {
            JSONObject json = base(HistoryEntry.TYPE_SYNC, label, finishedAt);
            json.put("status", result.applied ? STATUS_SUCCESS : STATUS_COMPLETE);
            json.put("applied", result.applied);
            json.put("startedAt", startedAt);
            json.put("finishedAt", finishedAt);
            json.put("durationMs", Math.max(0L, finishedAt - startedAt));
            json.put("sourceOccurrences", result.sourceOccurrences);
            json.put("existingMirroredOccurrences", result.existingMirroredOccurrences);
            json.put("created", result.created);
            json.put("updated", result.updated);
            json.put("deletedFuture", result.deletedFuture);
            json.put("keptPastStale", result.keptPastStale);
            json.put("deleteBlockedReason", result.deleteBlockedReason);
            json.put("horizonStart", result.horizonStart);
            json.put("horizonEnd", result.horizonEnd);
            json.put("targetCalendarId", targetCalendarId);
            if (uploadState != null) {
                json.put("uploadStatus", uploadState.isComplete() ? STATUS_COMPLETE : STATUS_PENDING);
                putUploadState(json, uploadState);
            }
            append(context, json);
        } catch (Exception ignored) {
        }
    }

    static synchronized void recordSyncFailure(Context context, String label, long startedAt, Throwable throwable) {
        try {
            long finishedAt = System.currentTimeMillis();
            JSONObject json = base(HistoryEntry.TYPE_SYNC, label, finishedAt);
            json.put("status", STATUS_FAILED);
            json.put("applied", false);
            json.put("startedAt", startedAt);
            json.put("finishedAt", finishedAt);
            json.put("durationMs", Math.max(0L, finishedAt - startedAt));
            json.put("errorType", throwable.getClass().getSimpleName());
            json.put("errorMessage", safeMessage(throwable));
            append(context, json);
        } catch (Exception ignored) {
        }
    }

    static synchronized void recordUpload(Context context, String label, long syncFinishedAt,
                                          String status, String detail, int attempt, UploadState state) {
        try {
            long now = System.currentTimeMillis();
            JSONObject json = base(HistoryEntry.TYPE_UPLOAD, "Google Calendar upload", now);
            json.put("status", status);
            json.put("syncLabel", label);
            json.put("parentSyncFinishedAt", syncFinishedAt);
            json.put("durationMs", Math.max(0L, now - syncFinishedAt));
            json.put("attempt", attempt);
            json.put("detail", detail == null ? "" : detail);
            if (state != null) {
                putUploadState(json, state);
            }
            append(context, json);
        } catch (Exception ignored) {
        }
    }

    static synchronized List<HistoryEntry> entries(Context context) {
        List<HistoryEntry> entries = read(context);
        Collections.sort(entries, (left, right) -> Long.compare(right.timestamp(), left.timestamp()));
        return entries;
    }

    static synchronized HistoryEntry latestSync(Context context) {
        for (HistoryEntry entry : entries(context)) {
            if (countsAsActivitySync(entry)) {
                return entry;
            }
        }
        return null;
    }

    static synchronized HistoryEntry latestUpload(Context context) {
        for (HistoryEntry entry : entries(context)) {
            if (entry.isUpload()) {
                return entry;
            }
        }
        return null;
    }

    static synchronized void clear(Context context) {
        File file = file(context);
        if (file.exists()) {
            file.delete();
        }
    }

    static String syncSummary(HistoryEntry entry) {
        if (entry == null) {
            return "Never";
        }
        if (STATUS_FAILED.equals(entry.status())) {
            return AppLog.blockTimestamp(entry.timestamp()) + " - Failed";
        }
        String label = entry.label().isEmpty() ? "Sync" : entry.label();
        return friendlyTimestamp(entry.timestamp()) + " - " + label;
    }

    static String uploadSummary(HistoryEntry entry) {
        if (entry == null) {
            return "Not started";
        }
        String status = entry.status();
        if (STATUS_COMPLETE.equals(status)) {
            return "Complete";
        }
        if (STATUS_WAITING.equals(status) || STATUS_STILL_WAITING.equals(status) || STATUS_PENDING.equals(status)) {
            return "Waiting";
        }
        return status.isEmpty() ? "Not started" : status;
    }

    static String collapsedTitle(HistoryEntry entry) {
        String icon = iconFor(entry);
        if (entry.isSync()) {
            if (STATUS_FAILED.equals(entry.status())) {
                return icon + " " + friendlyTimestamp(entry.timestamp()) + " - " + entry.label() + " failed";
            }
            String kind = entry.json.optBoolean("applied", false) ? entry.label() : "Preview";
            return icon + " " + friendlyTimestamp(entry.timestamp()) + " - " + kind
                    + ": +" + entry.json.optInt("created", 0)
                    + " / " + entry.json.optInt("updated", 0)
                    + " / -" + entry.json.optInt("deletedFuture", 0);
        }
        return icon + " " + friendlyTimestamp(entry.timestamp()) + " - Google Calendar upload: " + uploadSummary(entry);
    }

    static String syncDetails(HistoryEntry entry, List<HistoryEntry> uploadChildren) {
        StringBuilder out = new StringBuilder();
        if (STATUS_FAILED.equals(entry.status())) {
            out.append("Result: Failed after ").append(formatDuration(entry.json.optLong("durationMs", 0L))).append('\n');
            out.append("Reason: ").append(entry.json.optString("errorType", "Error"));
            String message = entry.json.optString("errorMessage", "");
            if (!message.isEmpty()) {
                out.append(" - ").append(message);
            }
            out.append('\n');
            out.append("Google Calendar upload: Not started");
            return out.toString();
        }

        out.append("Result: ").append(entry.status())
                .append(" in ").append(formatDuration(entry.json.optLong("durationMs", 0L))).append('\n');
        out.append("Outlook events read: ").append(entry.json.optInt("sourceOccurrences", 0)).append('\n');
        out.append("Mirrored calendar events before sync: ").append(entry.json.optInt("existingMirroredOccurrences", 0)).append('\n');
        out.append("Added: ").append(entry.json.optInt("created", 0)).append('\n');
        out.append("Updated: ").append(entry.json.optInt("updated", 0)).append('\n');
        out.append("Removed canceled upcoming meetings: ").append(entry.json.optInt("deletedFuture", 0)).append('\n');
        out.append("Kept completed meetings for history: ").append(entry.json.optInt("keptPastStale", 0)).append('\n');
        out.append("Selected Google Calendar: #").append(entry.json.optLong("targetCalendarId", -1L)).append('\n');
        out.append("Sync range: ").append(AppLog.blockTimestamp(entry.json.optLong("horizonStart", 0L)))
                .append(" to ").append(AppLog.blockTimestamp(entry.json.optLong("horizonEnd", 0L))).append('\n');
        String blocked = entry.json.optString("deleteBlockedReason", "");
        if (!blocked.isEmpty()) {
            out.append("Skipped removals: ").append(blocked).append('\n');
        }
        if (entry.json.optBoolean("applied", false)) {
            out.append('\n');
            out.append(uploadLine(entry));
            if (uploadChildren != null && !uploadChildren.isEmpty()) {
                for (HistoryEntry upload : uploadChildren) {
                    out.append("\n\n").append(uploadDetails(upload));
                }
            }
        }
        return out.toString();
    }

    static String uploadDetails(HistoryEntry entry) {
        StringBuilder out = new StringBuilder();
        out.append("Google Calendar upload: ").append(uploadSummary(entry)).append('\n');
        String detail = entry.json.optString("detail", "");
        if (!detail.isEmpty()) {
            out.append("Detail: ").append(detail).append('\n');
        }
        int attempt = entry.json.optInt("attempt", 0);
        if (attempt > 0) {
            out.append("Check: ").append(attempt).append('\n');
        }
        out.append(localUploadState(entry.json));
        return out.toString();
    }

    static String uploadLine(HistoryEntry entry) {
        String status = entry.json.optString("uploadStatus", "");
        if (status.isEmpty()) {
            status = uploadSummary(entry);
        }
        return "Google Calendar upload: " + status + "\n" + localUploadState(entry.json);
    }

    static String changeCounts(SyncResult result) {
        return "Added: " + result.created
                + "\nUpdated: " + result.updated
                + "\nRemoved canceled upcoming meetings: " + result.deletedFuture
                + "\nKept completed meetings for history: " + result.keptPastStale;
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

    private static JSONObject base(String type, String label, long timestamp) throws Exception {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("label", label == null ? "" : label);
        json.put("timestamp", timestamp);
        return json;
    }

    private static void putUploadState(JSONObject json, UploadState state) throws Exception {
        json.put("uploadActive", state.active);
        json.put("uploadCleanActive", state.cleanActive);
        json.put("uploadDirtyActive", state.dirtyActive);
        json.put("uploadDirtyDeleted", state.dirtyDeleted);
    }

    private static String localUploadState(JSONObject json) {
        return "Local upload state: "
                + json.optInt("uploadCleanActive", 0) + " uploaded, "
                + json.optInt("uploadDirtyActive", 0) + " waiting, "
                + json.optInt("uploadDirtyDeleted", 0) + " removals waiting";
    }

    private static String iconFor(HistoryEntry entry) {
        if (STATUS_FAILED.equals(entry.status())) {
            return "[!]";
        }
        if (entry.isUpload() && !STATUS_COMPLETE.equals(entry.status())) {
            return "[.]";
        }
        return "[check]";
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "" : throwable.getMessage();
    }

    private static boolean countsAsActivitySync(HistoryEntry entry) {
        if (!entry.isSync()) {
            return false;
        }
        if (entry.json.optBoolean("applied", false)) {
            return true;
        }
        return STATUS_FAILED.equals(entry.status()) && !isPreview(entry);
    }

    private static boolean isPreview(HistoryEntry entry) {
        return entry.label().toLowerCase(Locale.US).contains("preview");
    }

    private static String friendlyTimestamp(long millis) {
        return AppLog.blockTimestamp(millis);
    }

    private static void append(Context context, JSONObject json) throws Exception {
        List<JSONObject> all = readJson(context);
        all.add(json);
        Collections.sort(all, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left, JSONObject right) {
                return Long.compare(right.optLong("timestamp", 0L), left.optLong("timestamp", 0L));
            }
        });
        while (all.size() > MAX_ENTRIES) {
            all.remove(all.size() - 1);
        }
        Collections.reverse(all);

        try (FileOutputStream out = new FileOutputStream(file(context), false)) {
            for (JSONObject entry : all) {
                out.write(entry.toString().getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
        }
    }

    private static List<HistoryEntry> read(Context context) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (JSONObject json : readJson(context)) {
            entries.add(new HistoryEntry(json));
        }
        return entries;
    }

    private static List<JSONObject> readJson(Context context) {
        List<JSONObject> entries = new ArrayList<>();
        File file = file(context);
        if (!file.exists()) {
            return entries;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    entries.add(new JSONObject(trimmed));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return entries;
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
