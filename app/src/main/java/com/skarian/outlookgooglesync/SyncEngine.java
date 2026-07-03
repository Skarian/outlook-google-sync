package com.skarian.outlookgooglesync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SyncEngine {
    private static final String CUSTOM_PACKAGE = "customAppPackage";
    private static final String CUSTOM_URI = "customAppUri";
    private static final int MAX_DESCRIPTION_PARTICIPANTS = 30;
    private static final String FINGERPRINT_SEPARATOR = "|fp:";
    private static final String[] COMPARE_COLUMNS = {
            "title",
            "description",
            "eventLocation",
            "dtstart",
            "dtend",
            "eventTimezone",
            "allDay",
            "availability",
            "accessLevel",
            "eventStatus",
            "rrule",
            "rdate",
            "exrule",
            "exdate",
            "duration"
    };

    private SyncEngine() {}

    static SyncResult run(Context context, boolean apply, SyncProgress progress) throws Exception {
        return run(context, apply, apply ? "Manual sync" : "Manual preview", progress);
    }

    static SyncResult run(Context context, boolean apply, String triggerLabel, SyncProgress progress) throws Exception {
        long startedAt = System.currentTimeMillis();
        long targetCalendarId = AppConfig.targetCalendarId(context);
        if (targetCalendarId < 0) {
            throw new IllegalStateException("Select a Google Calendar first.");
        }

        Horizon horizon = horizon(startedAt);
        report(progress, "Reading Outlook calendar events", 10);

        List<SourceOccurrence> sourceOccurrences = AndroidCalendarReader.querySourceOccurrences(context, horizon.start, horizon.end);
        Map<String, SourceOccurrence> desired = new HashMap<>();
        for (SourceOccurrence occurrence : sourceOccurrences) {
            desired.put(occurrence.sourceKey, occurrence);
        }
        if (desired.size() != sourceOccurrences.size()) {
            AppLog.log(context, "Warning: " + (sourceOccurrences.size() - desired.size())
                    + " source occurrences shared a mirror key.");
        }

        report(progress, "Reading mirrored calendar events", 35);
        Map<String, MirroredOccurrence> existing = mirroredTargetOccurrences(context, targetCalendarId);

        report(progress, "Planning changes", 55);
        Plan plan = plan(context, targetCalendarId, horizon, desired, existing, startedAt);
        String preview = preview(horizon, desired, existing, plan);

        if (apply) {
            applyPlan(context, targetCalendarId, desired, existing, plan, progress);
            AppConfig.setLastSourceOccurrenceCount(context, sourceOccurrences.size());
        }

        long finishedAt = System.currentTimeMillis();
        UploadState uploadState = apply
                ? UploadTracker.queryState(context, targetCalendarId)
                : new UploadState(existing.size(), existing.size(), 0, 0);
        SyncResult result = new SyncResult(
                sourceOccurrences.size(),
                existing.size(),
                plan.toCreate.size(),
                plan.toUpdate.size(),
                plan.toDeleteFuture.size(),
                plan.keepPastStale,
                plan.deleteBlockedReason,
                apply,
                horizon.start,
                horizon.end,
                preview,
                targetCalendarId,
                uploadState
        );
        AppLog.logBlock(context, SyncLogFormatter.syncFinished(
                triggerLabel,
                result,
                startedAt,
                finishedAt,
                targetCalendarId,
                uploadState
        ));
        HistoryStore.recordSync(context, triggerLabel, result, startedAt, finishedAt, targetCalendarId, uploadState);
        if (apply) {
            UploadTracker.begin(context, triggerLabel, finishedAt, targetCalendarId, uploadState);
        }
        report(progress, "Done", 100);
        return result;
    }

    private static Plan plan(Context context, long targetCalendarId, Horizon horizon,
                             Map<String, SourceOccurrence> desired, Map<String, MirroredOccurrence> existing,
                             long now) {
        Set<String> desiredKeys = desired.keySet();
        Set<String> existingKeys = existing.keySet();

        Plan plan = new Plan();
        plan.toCreate.addAll(desiredKeys);
        plan.toCreate.removeAll(existingKeys);

        for (String key : desiredKeys) {
            MirroredOccurrence mirrored = existing.get(key);
            if (mirrored != null && !mirrored.matches(valuesFor(context, targetCalendarId, desired.get(key)))) {
                plan.toUpdate.add(key);
            }
        }

        for (String key : existingKeys) {
            if (desiredKeys.contains(key)) {
                continue;
            }
            MirroredOccurrence mirrored = existing.get(key);
            if (mirrored.start <= now) {
                plan.keepPastStale++;
                continue;
            }
            if (mirrored.start > horizon.end) {
                plan.keepFutureOutsideWindow++;
                continue;
            }
            plan.toDeleteFuture.add(key);
        }

        if (desired.isEmpty() && futureMirrorsInWindow(existing, now, horizon.end) > 0) {
            plan.toDeleteFuture.clear();
            plan.deleteBlockedReason = "Skipped future deletes because Outlook returned zero occurrences while future mirrored events exist.";
        }
        return plan;
    }

    private static int futureMirrorsInWindow(Map<String, MirroredOccurrence> existing, long now, long horizonEnd) {
        int count = 0;
        for (MirroredOccurrence occurrence : existing.values()) {
            if (occurrence.start > now && occurrence.start <= horizonEnd) {
                count++;
            }
        }
        return count;
    }

    private static void applyPlan(Context context, long targetCalendarId, Map<String, SourceOccurrence> desired,
                                  Map<String, MirroredOccurrence> existing, Plan plan, SyncProgress progress) {
        ContentResolver resolver = context.getContentResolver();
        int total = plan.toCreate.size() + plan.toUpdate.size() + plan.toDeleteFuture.size();
        int done = 0;

        for (String key : sorted(plan.toCreate)) {
            resolver.insert(CalendarContract.Events.CONTENT_URI, valuesFor(context, targetCalendarId, desired.get(key)));
            done = reportApply(progress, "Updating mirrored calendar events", done, total);
        }
        for (String key : sorted(plan.toUpdate)) {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.get(key).id);
            resolver.update(uri, valuesFor(context, targetCalendarId, desired.get(key)), null, null);
            done = reportApply(progress, "Updating mirrored calendar events", done, total);
        }
        for (String key : sorted(plan.toDeleteFuture)) {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.get(key).id);
            resolver.delete(uri, null, null);
            done = reportApply(progress, "Updating mirrored calendar events", done, total);
        }
        if (total == 0) {
            report(progress, "No changes to apply", 90);
        }
    }

    private static int reportApply(SyncProgress progress, String message, int done, int total) {
        int next = done + 1;
        int percent = total <= 0 ? 90 : 60 + (int) ((next * 35L) / total);
        report(progress, message + " " + next + "/" + total, Math.min(95, percent));
        return next;
    }

    private static Map<String, MirroredOccurrence> mirroredTargetOccurrences(Context context, long targetCalendarId) {
        String[] projection = new String[COMPARE_COLUMNS.length + 4];
        projection[0] = "_id";
        projection[1] = CUSTOM_URI;
        projection[2] = "dtstart";
        projection[3] = "dtend";
        System.arraycopy(COMPARE_COLUMNS, 0, projection, 4, COMPARE_COLUMNS.length);

        String selection = "calendar_id=? AND deleted=0 AND " + CUSTOM_PACKAGE + "=?";
        String[] args = {Long.toString(targetCalendarId), context.getPackageName()};
        Map<String, MirroredOccurrence> out = new HashMap<>();
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                null
        )) {
            if (cursor == null) {
                return out;
            }
            while (cursor.moveToNext()) {
                String rawCustomUri = cursor.getString(1);
                String key = stableCustomUri(rawCustomUri);
                if (key != null && key.startsWith(AppConfig.OCCURRENCE_TAG_PREFIX)) {
                    out.put(key, new MirroredOccurrence(
                            cursor.getLong(0),
                            key,
                            fingerprintFromCustomUri(rawCustomUri),
                            cursor.isNull(2) ? 0L : cursor.getLong(2),
                            cursor.isNull(3) ? 0L : cursor.getLong(3),
                            valuesFromCursor(cursor)
                    ));
                }
            }
        }
        return out;
    }

    private static Map<String, String> valuesFromCursor(Cursor cursor) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < COMPARE_COLUMNS.length; i++) {
            int column = i + 4;
            values.put(COMPARE_COLUMNS[i], cursor.isNull(column) ? "" : cursor.getString(column));
        }
        return values;
    }

    private static ContentValues valuesFor(Context context, long targetCalendarId, SourceOccurrence occurrence) {
        ContentValues values = new ContentValues();
        values.put("calendar_id", targetCalendarId);
        values.put("title", titleForTarget(occurrence.title));
        values.put("description", descriptionFor(occurrence));
        values.put("eventLocation", occurrence.location);
        values.put("dtstart", occurrence.begin);
        values.put("dtend", occurrence.end);
        values.put("eventTimezone", timezoneForTarget(occurrence.timezone));
        values.put("allDay", occurrence.allDay ? 1 : 0);
        values.put("availability", availabilityForTarget(occurrence.availability));
        values.put("accessLevel", occurrence.accessLevel);
        values.put("eventStatus", occurrence.status);
        values.putNull("rrule");
        values.putNull("rdate");
        values.putNull("exrule");
        values.putNull("exdate");
        values.putNull("duration");
        values.put(CUSTOM_PACKAGE, context.getPackageName());
        values.put(CUSTOM_URI, customUriFor(occurrence.sourceKey, values));
        return values;
    }

    private static String customUriFor(String sourceKey, ContentValues values) {
        return sourceKey + FINGERPRINT_SEPARATOR + fingerprint(values);
    }

    private static String stableCustomUri(String customUri) {
        if (customUri == null) {
            return null;
        }
        int index = customUri.indexOf(FINGERPRINT_SEPARATOR);
        return index < 0 ? customUri : customUri.substring(0, index);
    }

    private static String fingerprintFromCustomUri(String customUri) {
        if (customUri == null) {
            return "";
        }
        int index = customUri.indexOf(FINGERPRINT_SEPARATOR);
        return index < 0 ? "" : customUri.substring(index + FINGERPRINT_SEPARATOR.length());
    }

    private static String fingerprint(ContentValues values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String column : COMPARE_COLUMNS) {
                digest.update(column.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(normalizeValue(values.get(column)).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = alphabet[value >>> 4];
            out[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(out);
    }

    private static String normalizeValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String titleForTarget(String title) {
        return title == null || title.isEmpty() ? "(no title)" : title;
    }

    private static String timezoneForTarget(String timezone) {
        if ("US/Eastern".equals(timezone)) {
            return "America/New_York";
        }
        if ("US/Pacific".equals(timezone) || "US/Pacific-New".equals(timezone)) {
            return "America/Los_Angeles";
        }
        if ("Zulu".equals(timezone) || "Etc/UTC".equals(timezone)) {
            return "UTC";
        }
        return timezone == null || timezone.isEmpty() ? "UTC" : timezone;
    }

    private static int availabilityForTarget(int availability) {
        return availability == CalendarContract.Events.AVAILABILITY_TENTATIVE
                ? CalendarContract.Events.AVAILABILITY_BUSY
                : availability;
    }

    private static String descriptionFor(SourceOccurrence occurrence) {
        if (occurrence.participants.isEmpty()) {
            return occurrence.description;
        }

        StringBuilder out = new StringBuilder();
        String base = occurrence.description.trim();
        if (!base.isEmpty()) {
            out.append(base).append("\n\n");
        }
        int shown = Math.min(MAX_DESCRIPTION_PARTICIPANTS, occurrence.participants.size());
        out.append("Participants");
        if (occurrence.participants.size() > shown) {
            out.append(" (").append(shown).append(" of ").append(occurrence.participants.size()).append(" shown)");
        }
        out.append(":\n");
        for (int i = 0; i < shown; i++) {
            out.append("- ").append(participantLabel(occurrence.participants.get(i))).append('\n');
        }
        int hidden = occurrence.participants.size() - shown;
        if (hidden > 0) {
            out.append("- ...and ").append(hidden).append(" more participants\n");
        }
        return out.toString().trim();
    }

    private static String participantLabel(Participant participant) {
        StringBuilder out = new StringBuilder();
        if (!participant.name.isEmpty()) {
            out.append(participant.name);
        }
        if (!participant.email.isEmpty() && !participant.email.equalsIgnoreCase(participant.name)) {
            if (out.length() > 0) {
                out.append(" - ");
            }
            out.append(participant.email);
        }
        if (out.length() == 0) {
            out.append("(unknown)");
        }

        String type = attendeeTypeLabel(participant.type);
        String status = attendeeStatusLabel(participant.status);
        if (!type.isEmpty() || !status.isEmpty()) {
            out.append(" (");
            if (!type.isEmpty()) {
                out.append(type);
            }
            if (!type.isEmpty() && !status.isEmpty()) {
                out.append(", ");
            }
            if (!status.isEmpty()) {
                out.append(status);
            }
            out.append(')');
        }
        return out.toString();
    }

    private static String attendeeTypeLabel(int type) {
        switch (type) {
            case CalendarContract.Attendees.TYPE_REQUIRED:
                return "required";
            case CalendarContract.Attendees.TYPE_OPTIONAL:
                return "optional";
            case CalendarContract.Attendees.TYPE_RESOURCE:
                return "resource";
            default:
                return "";
        }
    }

    private static String attendeeStatusLabel(int status) {
        switch (status) {
            case CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED:
                return "accepted";
            case CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED:
                return "declined";
            case CalendarContract.Attendees.ATTENDEE_STATUS_INVITED:
                return "invited";
            case CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE:
                return "tentative";
            default:
                return "";
        }
    }

    private static String preview(Horizon horizon, Map<String, SourceOccurrence> desired,
                                  Map<String, MirroredOccurrence> existing, Plan plan) {
        StringBuilder out = new StringBuilder();
        out.append("Sync range: ").append(formatMillis(horizon.start)).append(" to ").append(formatMillis(horizon.end)).append('\n');
        out.append("No changes were applied.\n");
        if (plan.deleteBlockedReason != null && !plan.deleteBlockedReason.isEmpty()) {
            out.append("Skipped removals: ").append(plan.deleteBlockedReason).append('\n');
        }
        out.append('\n');
        out.append("Added: ").append(plan.toCreate.size()).append('\n');
        out.append("Updated: ").append(plan.toUpdate.size()).append('\n');
        out.append("Removed canceled upcoming meetings: ").append(plan.toDeleteFuture.size()).append('\n');
        out.append("Kept completed meetings for history: ").append(plan.keepPastStale).append('\n');
        out.append('\n');
        appendSourceSection(out, "Examples to add", desired, plan.toCreate);
        appendSourceSection(out, "Examples to update", desired, plan.toUpdate);
        appendTargetSection(out, "Examples to remove", existing, plan.toDeleteFuture);
        if (plan.keepPastStale > 0) {
            out.append("Meetings that already happened are kept for historical reference: ")
                    .append(plan.keepPastStale).append('\n');
        }
        if (plan.keepFutureOutsideWindow > 0) {
            out.append("Outside sync range: ").append(plan.keepFutureOutsideWindow).append('\n');
        }
        if (plan.toCreate.isEmpty() && plan.toUpdate.isEmpty() && plan.toDeleteFuture.isEmpty()) {
            out.append("No changes found. No changes were applied.\n");
        }
        return out.toString().trim();
    }

    private static void appendSourceSection(StringBuilder out, String label, Map<String, SourceOccurrence> desired, Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        out.append(label).append(":\n");
        int count = 0;
        for (String key : sorted(keys)) {
            if (++count > 25) {
                out.append("  ...and ").append(keys.size() - 25).append(" more\n");
                break;
            }
            SourceOccurrence occurrence = desired.get(key);
            out.append("  ").append(formatMillis(occurrence.begin)).append(" - ").append(occurrence.title).append('\n');
        }
    }

    private static void appendTargetSection(StringBuilder out, String label, Map<String, MirroredOccurrence> existing, Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        out.append(label).append(":\n");
        int count = 0;
        for (String key : sorted(keys)) {
            if (++count > 25) {
                out.append("  ...and ").append(keys.size() - 25).append(" more\n");
                break;
            }
            MirroredOccurrence occurrence = existing.get(key);
            out.append("  ").append(formatMillis(occurrence.start)).append(" - target #").append(occurrence.id).append('\n');
        }
    }

    private static List<String> sorted(Set<String> keys) {
        List<String> out = new ArrayList<>(keys);
        Collections.sort(out);
        return out;
    }

    private static Horizon horizon(long now) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now);
        start.add(Calendar.DAY_OF_YEAR, -AppConfig.HORIZON_PAST_DAYS);
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(now);
        end.add(Calendar.DAY_OF_YEAR, AppConfig.HORIZON_FUTURE_DAYS);
        return new Horizon(start.getTimeInMillis(), end.getTimeInMillis());
    }

    private static String formatMillis(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(millis);
    }

    private static void report(SyncProgress progress, String message, int percent) {
        if (progress != null) {
            progress.update(message, percent);
        }
    }

    private static final class Horizon {
        final long start;
        final long end;

        Horizon(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class Plan {
        final Set<String> toCreate = new HashSet<>();
        final Set<String> toUpdate = new HashSet<>();
        final Set<String> toDeleteFuture = new HashSet<>();
        int keepPastStale;
        int keepFutureOutsideWindow;
        String deleteBlockedReason;
    }

    private static final class MirroredOccurrence {
        final long id;
        final String key;
        final String fingerprint;
        final long start;
        final long end;
        final Map<String, String> values;

        MirroredOccurrence(long id, String key, String fingerprint, long start, long end, Map<String, String> values) {
            this.id = id;
            this.key = key;
            this.fingerprint = fingerprint;
            this.start = start;
            this.end = end;
            this.values = values;
        }

        boolean matches(ContentValues desired) {
            String desiredFingerprint = SyncEngine.fingerprint(desired);
            if (!fingerprint.isEmpty()) {
                return fingerprint.equals(desiredFingerprint);
            }
            for (String column : COMPARE_COLUMNS) {
                String left = values.get(column);
                String right = normalizeValue(desired.get(column));
                if (!right.equals(left == null ? "" : left)) {
                    return false;
                }
            }
            return false;
        }
    }
}
