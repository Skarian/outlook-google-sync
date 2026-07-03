package com.skarian.outlookgooglesync;

import org.json.JSONObject;

final class HistoryEntry {
    static final String TYPE_SYNC = "sync";
    static final String TYPE_UPLOAD = "upload";

    final JSONObject json;

    HistoryEntry(JSONObject json) {
        this.json = json;
    }

    String type() {
        return json.optString("type", "");
    }

    String label() {
        return json.optString("label", "");
    }

    String status() {
        return json.optString("status", "");
    }

    long timestamp() {
        return json.optLong("timestamp", 0L);
    }

    long startedAt() {
        return json.optLong("startedAt", 0L);
    }

    long finishedAt() {
        return json.optLong("finishedAt", timestamp());
    }

    long parentSyncFinishedAt() {
        return json.optLong("parentSyncFinishedAt", 0L);
    }

    boolean isSync() {
        return TYPE_SYNC.equals(type());
    }

    boolean isUpload() {
        return TYPE_UPLOAD.equals(type());
    }
}
