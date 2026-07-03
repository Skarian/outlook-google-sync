package com.skarian.outlookgooglesync;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class AppConfig {
    static final String OUTLOOK_ACCOUNT_TYPE = "com.microsoft.office.outlook.USER_ACCOUNT";
    static final String SOURCE_TAG = "android-outlook-device";
    static final String OCCURRENCE_TAG_PREFIX = SOURCE_TAG + ":occ:v1:";
    static final int HORIZON_PAST_DAYS = 365;
    static final int HORIZON_FUTURE_DAYS = 365;

    private static final String PREFS = "sync_config";
    private static final String KEY_TARGET_CALENDAR_ID = "target_calendar_id";
    private static final String KEY_SOURCE_CALENDAR_IDS = "source_calendar_ids";
    private static final String KEY_SCHEDULED = "scheduled";
    private static final String KEY_LAST_SOURCE_OCCURRENCE_COUNT = "last_source_occurrence_count";

    private AppConfig() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static long targetCalendarId(Context context) {
        return prefs(context).getLong(KEY_TARGET_CALENDAR_ID, -1L);
    }

    static void setTargetCalendarId(Context context, long value) {
        prefs(context).edit().putLong(KEY_TARGET_CALENDAR_ID, value).apply();
    }

    static Set<String> sourceCalendarIds(Context context) {
        Set<String> ids = prefs(context).getStringSet(KEY_SOURCE_CALENDAR_IDS, Collections.emptySet());
        return new HashSet<>(ids);
    }

    static void setSourceCalendarIds(Context context, Set<String> ids) {
        prefs(context).edit().putStringSet(KEY_SOURCE_CALENDAR_IDS, new HashSet<>(ids)).apply();
    }

    static int lastSourceOccurrenceCount(Context context) {
        return prefs(context).getInt(KEY_LAST_SOURCE_OCCURRENCE_COUNT, 0);
    }

    static void setLastSourceOccurrenceCount(Context context, int value) {
        prefs(context).edit().putInt(KEY_LAST_SOURCE_OCCURRENCE_COUNT, value).apply();
    }

    static boolean scheduled(Context context) {
        return prefs(context).getBoolean(KEY_SCHEDULED, false);
    }

    static void setScheduled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SCHEDULED, value).apply();
    }

}
