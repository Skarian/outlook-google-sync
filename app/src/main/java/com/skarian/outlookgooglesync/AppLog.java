package com.skarian.outlookgooglesync;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppLog {
    private static final String FILE_NAME = "sync.log";
    private static final int MAX_BYTES = 256 * 1024;
    private static final int KEEP_BYTES = 128 * 1024;

    private AppLog() {}

    static synchronized void log(Context context, String message) {
        try {
            File file = logFile(context);
            trimIfNeeded(file);
            String line = timestamp() + " " + message + "\n";
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized void logBlock(Context context, String message) {
        try {
            File file = logFile(context);
            trimIfNeeded(file);
            String block = message.trim() + "\n\n";
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(block.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    static String blockTimestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    static synchronized void log(Context context, Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        log(context, writer.toString());
    }

    static synchronized String read(Context context) {
        try {
            File file = logFile(context);
            if (!file.exists()) {
                return "(no log yet)";
            }
            return new String(readAll(file), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Could not read log: " + e.getMessage();
        }
    }

    static synchronized void clear(Context context) {
        File file = logFile(context);
        if (file.exists()) {
            file.delete();
        }
        log(context, "Log cleared.");
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void trimIfNeeded(File file) throws Exception {
        if (!file.exists() || file.length() <= MAX_BYTES) {
            return;
        }
        byte[] all = readAll(file);
        int keep = Math.min(KEEP_BYTES, all.length);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(all, all.length - keep, keep);
        }
    }

    private static byte[] readAll(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }
        return out.toByteArray();
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
