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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HistoryStore {
    static final String STATUS_SUCCESS = "Success";
    static final String STATUS_FAILED = "Failed";
    static final String STATUS_COMPLETE = "Complete";
    static final String STATUS_WAITING = "Waiting";
    static final String STATUS_STILL_WAITING = "Still waiting";
    static final String STATUS_PENDING = "Pending";

    private static final String FILE_NAME = "sync-history.jsonl";
    private static final int MAX_ENTRIES = 12000;
    private static final long RETENTION_DAYS = 30L;
    private static final long RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L;

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
        prune(context);
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

    static synchronized String exportText(Context context) {
        List<HistoryEntry> entries = entries(context);
        if (entries.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append("Outlook Google Sync Log\n");
        out.append("Exported: ").append(AppLog.blockTimestamp(System.currentTimeMillis())).append('\n');
        out.append("Entries: last ").append(RETENTION_DAYS).append(" days\n\n");

        Map<Long, List<HistoryEntry>> uploadsBySync = uploadChildren(entries);
        Set<HistoryEntry> groupedUploads = new HashSet<>();
        boolean first = true;
        for (HistoryEntry entry : entries) {
            if (!entry.isSync()) {
                continue;
            }
            List<HistoryEntry> children = uploadsBySync.get(entry.finishedAt());
            if (children != null) {
                groupedUploads.addAll(children);
            }
            if (!first) {
                out.append('\n');
            }
            appendSyncExport(out, entry, children);
            first = false;
        }
        for (HistoryEntry entry : entries) {
            if (entry.isUpload() && !groupedUploads.contains(entry)) {
                if (!first) {
                    out.append('\n');
                }
                appendUploadExport(out, entry);
                first = false;
            }
        }
        return out.toString();
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
        writeJson(context, retainedEntries(all, System.currentTimeMillis()));
    }

    private static Map<Long, List<HistoryEntry>> uploadChildren(List<HistoryEntry> entries) {
        Map<Long, List<HistoryEntry>> uploadsBySync = new HashMap<>();
        for (HistoryEntry entry : entries) {
            if (!entry.isUpload()) {
                continue;
            }
            long parent = entry.parentSyncFinishedAt();
            if (parent <= 0L) {
                continue;
            }
            List<HistoryEntry> children = uploadsBySync.get(parent);
            if (children == null) {
                children = new ArrayList<>();
                uploadsBySync.put(parent, children);
            }
            children.add(entry);
        }
        return uploadsBySync;
    }

    private static void appendSyncExport(StringBuilder out, HistoryEntry entry, List<HistoryEntry> uploadChildren) {
        out.append('[').append(AppLog.blockTimestamp(entry.timestamp())).append("] ")
                .append(entry.label().isEmpty() ? "Sync" : safeLine(entry.label())).append('\n');
        if (STATUS_FAILED.equals(entry.status())) {
            out.append("Result: Failed\n");
            out.append("Duration: ").append(formatDuration(entry.json.optLong("durationMs", 0L))).append('\n');
            String reason = failureReason(entry);
            if (!reason.isEmpty()) {
                out.append("Reason: ").append(reason).append('\n');
            }
            out.append("Google Calendar upload: Not started\n");
            return;
        }

        boolean applied = entry.json.optBoolean("applied", false);
        out.append("Result: ").append(applied ? entry.status() : "Preview").append('\n');
        out.append("Duration: ").append(formatDuration(entry.json.optLong("durationMs", 0L))).append('\n');
        out.append("Outlook events read: ").append(entry.json.optInt("sourceOccurrences", 0)).append('\n');
        out.append("Mirrored calendar events before sync: ")
                .append(entry.json.optInt("existingMirroredOccurrences", 0)).append('\n');
        out.append("Added: ").append(entry.json.optInt("created", 0)).append('\n');
        out.append("Updated: ").append(entry.json.optInt("updated", 0)).append('\n');
        out.append("Removed canceled upcoming meetings: ").append(entry.json.optInt("deletedFuture", 0)).append('\n');
        out.append("Kept completed meetings for history: ").append(entry.json.optInt("keptPastStale", 0)).append('\n');
        String blocked = safeLine(entry.json.optString("deleteBlockedReason", ""));
        if (!blocked.isEmpty()) {
            out.append("Skipped removals: ").append(blocked).append('\n');
        }
        if (applied) {
            HistoryEntry upload = latestUpload(uploadChildren);
            JSONObject state = upload == null ? entry.json : upload.json;
            out.append("Google Calendar upload: ").append(uploadStatus(entry, upload)).append('\n');
            appendUploadState(out, state);
        }
    }

    private static void appendUploadExport(StringBuilder out, HistoryEntry entry) {
        out.append('[').append(AppLog.blockTimestamp(entry.timestamp())).append("] Google Calendar upload\n");
        String label = safeLine(entry.json.optString("syncLabel", ""));
        if (!label.isEmpty()) {
            out.append("For: ").append(label).append('\n');
        }
        out.append("Result: ").append(uploadSummary(entry)).append('\n');
        out.append("Duration: ").append(formatDuration(entry.json.optLong("durationMs", 0L))).append('\n');
        appendUploadState(out, entry.json);
    }

    private static void appendUploadState(StringBuilder out, JSONObject json) {
        out.append("Uploaded: ").append(json.optInt("uploadCleanActive", 0)).append('\n');
        out.append("Waiting: ").append(json.optInt("uploadDirtyActive", 0)).append('\n');
        out.append("Removals waiting: ").append(json.optInt("uploadDirtyDeleted", 0)).append('\n');
    }

    private static HistoryEntry latestUpload(List<HistoryEntry> uploadChildren) {
        return uploadChildren == null || uploadChildren.isEmpty() ? null : uploadChildren.get(0);
    }

    private static String uploadStatus(HistoryEntry sync, HistoryEntry upload) {
        if (upload != null) {
            return uploadSummary(upload);
        }
        String status = sync.json.optString("uploadStatus", "");
        return status.isEmpty() ? "Not started" : status;
    }

    private static String failureReason(HistoryEntry entry) {
        String message = safeLine(entry.json.optString("errorMessage", ""));
        if (!message.isEmpty()) {
            return message;
        }
        return safeLine(entry.json.optString("errorType", ""));
    }

    private static String safeLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void prune(Context context) {
        try {
            List<JSONObject> all = readJson(context);
            List<JSONObject> retained = retainedEntries(all, System.currentTimeMillis());
            if (!sameJson(all, retained)) {
                writeJson(context, retained);
            }
        } catch (Exception ignored) {
        }
    }

    private static List<JSONObject> retainedEntries(List<JSONObject> entries, long now) {
        List<JSONObject> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left, JSONObject right) {
                return Long.compare(right.optLong("timestamp", 0L), left.optLong("timestamp", 0L));
            }
        });

        long cutoff = now - RETENTION_MS;
        List<JSONObject> retained = new ArrayList<>();
        for (JSONObject entry : sorted) {
            if (entry.optLong("timestamp", 0L) >= cutoff) {
                retained.add(entry);
            }
        }
        while (retained.size() > MAX_ENTRIES) {
            retained.remove(retained.size() - 1);
        }
        Collections.reverse(retained);
        return retained;
    }

    private static boolean sameJson(List<JSONObject> left, List<JSONObject> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).toString().equals(right.get(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private static void writeJson(Context context, List<JSONObject> entries) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file(context), false)) {
            for (JSONObject entry : entries) {
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
