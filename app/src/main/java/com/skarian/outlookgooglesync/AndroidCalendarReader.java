package com.skarian.outlookgooglesync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AndroidCalendarReader {
    private AndroidCalendarReader() {}

    static List<CalendarInfo> listCalendars(Context context) {
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };
        List<CalendarInfo> calendars = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars.ACCOUNT_TYPE + " ASC, " + CalendarContract.Calendars.ACCOUNT_NAME + " ASC"
        )) {
            if (cursor == null) {
                return calendars;
            }
            while (cursor.moveToNext()) {
                calendars.add(new CalendarInfo(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getInt(4) != 0,
                        cursor.getInt(5) != 0,
                        cursor.getInt(6)
                ));
            }
        }
        return calendars;
    }

    static List<CalendarInfo> sourceCalendars(Context context) {
        Set<String> selectedIds = AppConfig.sourceCalendarIds(context);
        if (selectedIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<CalendarInfo> matches = new ArrayList<>();
        for (CalendarInfo calendar : listCalendars(context)) {
            if (!AppConfig.OUTLOOK_ACCOUNT_TYPE.equals(calendar.accountType)) {
                continue;
            }
            if (!selectedIds.contains(Long.toString(calendar.id))) {
                continue;
            }
            matches.add(calendar);
        }
        return matches;
    }

    static List<CalendarInfo> targetCalendars(Context context) {
        List<CalendarInfo> matches = new ArrayList<>();
        for (CalendarInfo calendar : listCalendars(context)) {
            if (AppConfig.OUTLOOK_ACCOUNT_TYPE.equals(calendar.accountType)) {
                continue;
            }
            if (!calendar.accountType.toLowerCase().contains("google")) {
                continue;
            }
            if (!calendar.syncEvents) {
                continue;
            }
            if (calendar.accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                continue;
            }
            matches.add(calendar);
        }
        return matches;
    }

    static List<SourceOccurrence> querySourceOccurrences(Context context, long horizonStart, long horizonEnd) throws Exception {
        List<CalendarInfo> calendars = sourceCalendars(context);
        if (calendars.isEmpty()) {
            throw new IllegalStateException("No Outlook-exported Android calendars found.");
        }
        for (CalendarInfo calendar : calendars) {
            if (!calendar.syncEvents) {
                throw new IllegalStateException("Source calendar is not synced on this device: " + calendar);
            }
        }

        StringBuilder selection = new StringBuilder("calendar_id IN (");
        String[] args = new String[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            if (i > 0) {
                selection.append(',');
            }
            selection.append('?');
            args[i] = Long.toString(calendars.get(i).id);
        }
        selection.append(") AND deleted=0 AND (eventStatus IS NULL OR eventStatus!=")
                .append(CalendarContract.Events.STATUS_CANCELED)
                .append(')');

        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                "calendar_id",
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                "title",
                "eventLocation",
                "description",
                "allDay",
                "availability",
                "eventStatus",
                "accessLevel",
                "eventTimezone",
                "rrule",
                "rdate",
                "original_id",
                "originalInstanceTime"
        };

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, horizonStart);
        ContentUris.appendId(builder, horizonEnd);

        ContentResolver resolver = context.getContentResolver();
        Map<Long, List<Participant>> participantsByEventId = queryParticipantsByEventId(
                resolver,
                querySourceEventIds(resolver, calendars)
        );
        List<SourceOccurrence> occurrences = new ArrayList<>();
        try (Cursor cursor = resolver.query(
                builder.build(),
                projection,
                selection.toString(),
                args,
                CalendarContract.Instances.BEGIN + " ASC"
        )) {
            if (cursor == null) {
                throw new IllegalStateException("Android Calendar returned no occurrence cursor.");
            }
            while (cursor.moveToNext()) {
                long eventId = cursor.getLong(0);
                long calendarId = cursor.getLong(1);
                long begin = cursor.getLong(2);
                long end = cursor.isNull(3) ? begin + 60_000L : cursor.getLong(3);
                long originalId = cursor.isNull(15) ? 0L : cursor.getLong(15);
                long originalInstanceTime = cursor.isNull(16) ? 0L : cursor.getLong(16);
                long ownerEventId = originalId > 0L ? originalId : eventId;
                long occurrenceIdentity = originalInstanceTime > 0L ? originalInstanceTime : begin;
                String sourceKey = AppConfig.OCCURRENCE_TAG_PREFIX + calendarId + ":" + ownerEventId + ":" + occurrenceIdentity;
                occurrences.add(new SourceOccurrence(
                        sourceKey,
                        eventId,
                        ownerEventId,
                        calendarId,
                        begin,
                        end,
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getInt(7) != 0,
                        cursor.isNull(8) ? 0 : cursor.getInt(8),
                        cursor.isNull(9) ? CalendarContract.Events.STATUS_CONFIRMED : cursor.getInt(9),
                        cursor.isNull(10) ? CalendarContract.Events.ACCESS_DEFAULT : cursor.getInt(10),
                        cursor.getString(11),
                        participantsByEventId.get(eventId)
                ));
            }
        }
        return occurrences;
    }

    private static Set<Long> querySourceEventIds(ContentResolver resolver, List<CalendarInfo> calendars) {
        StringBuilder selection = new StringBuilder("calendar_id IN (");
        String[] args = new String[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            if (i > 0) {
                selection.append(',');
            }
            selection.append('?');
            args[i] = Long.toString(calendars.get(i).id);
        }
        selection.append(") AND deleted=0");

        Set<Long> eventIds = new HashSet<>();
        try (Cursor cursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID},
                selection.toString(),
                args,
                null
        )) {
            if (cursor == null) {
                return eventIds;
            }
            while (cursor.moveToNext()) {
                eventIds.add(cursor.getLong(0));
            }
        }
        return eventIds;
    }

    private static Map<Long, List<Participant>> queryParticipantsByEventId(ContentResolver resolver, Set<Long> eventIds) {
        Map<Long, List<Participant>> participantsByEventId = new HashMap<>();
        if (eventIds.isEmpty()) {
            return participantsByEventId;
        }

        List<Long> ids = new ArrayList<>(eventIds);
        Collections.sort(ids);
        for (int start = 0; start < ids.size(); start += 500) {
            int end = Math.min(ids.size(), start + 500);
            queryParticipantChunk(resolver, ids.subList(start, end), participantsByEventId);
        }

        for (List<Participant> participants : participantsByEventId.values()) {
            Collections.sort(participants, new Comparator<Participant>() {
                @Override
                public int compare(Participant left, Participant right) {
                    return left.sortKey().compareTo(right.sortKey());
                }
            });
        }
        return participantsByEventId;
    }

    private static void queryParticipantChunk(ContentResolver resolver, List<Long> eventIds,
                                              Map<Long, List<Participant>> participantsByEventId) {
        StringBuilder selection = new StringBuilder(CalendarContract.Attendees.EVENT_ID + " IN (");
        String[] args = new String[eventIds.size()];
        for (int i = 0; i < eventIds.size(); i++) {
            if (i > 0) {
                selection.append(',');
            }
            selection.append('?');
            args[i] = Long.toString(eventIds.get(i));
        }
        selection.append(')');

        String[] projection = {
                CalendarContract.Attendees.EVENT_ID,
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.ATTENDEE_STATUS
        };

        try (Cursor cursor = resolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                projection,
                selection.toString(),
                args,
                null
        )) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                long eventId = cursor.getLong(0);
                List<Participant> participants = participantsByEventId.get(eventId);
                if (participants == null) {
                    participants = new ArrayList<>();
                    participantsByEventId.put(eventId, participants);
                }
                participants.add(new Participant(
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.isNull(3) ? CalendarContract.Attendees.TYPE_NONE : cursor.getInt(3),
                        cursor.isNull(4) ? CalendarContract.Attendees.ATTENDEE_STATUS_NONE : cursor.getInt(4)
                ));
            }
        }
    }
}
