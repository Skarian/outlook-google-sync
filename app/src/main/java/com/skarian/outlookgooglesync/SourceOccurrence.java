package com.skarian.outlookgooglesync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SourceOccurrence {
    final String sourceKey;
    final long eventId;
    final long ownerEventId;
    final long calendarId;
    final long begin;
    final long end;
    final String title;
    final String location;
    final String description;
    final boolean allDay;
    final int availability;
    final int status;
    final int accessLevel;
    final String timezone;
    final List<Participant> participants;

    SourceOccurrence(String sourceKey, long eventId, long ownerEventId, long calendarId, long begin, long end,
                     String title, String location, String description, boolean allDay, int availability,
                     int status, int accessLevel, String timezone, List<Participant> participants) {
        this.sourceKey = sourceKey;
        this.eventId = eventId;
        this.ownerEventId = ownerEventId;
        this.calendarId = calendarId;
        this.begin = begin;
        this.end = end > begin ? end : begin + 60_000L;
        this.title = title == null || title.isEmpty() ? "(no title)" : title;
        this.location = location == null ? "" : location;
        this.description = description == null ? "" : description;
        this.allDay = allDay;
        this.availability = availability;
        this.status = status;
        this.accessLevel = accessLevel;
        this.timezone = allDay ? "UTC" : (timezone == null || timezone.isEmpty() ? "UTC" : timezone);
        this.participants = participants == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(participants));
    }
}
