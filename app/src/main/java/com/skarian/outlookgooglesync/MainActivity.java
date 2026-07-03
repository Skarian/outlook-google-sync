package com.skarian.outlookgooglesync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int REQUEST_CALENDAR_PERMISSION = 10;

    private static final int BASE = Color.rgb(30, 30, 46);
    private static final int TRANSPARENT = Color.TRANSPARENT;
    private static final int CRUST = Color.rgb(17, 17, 27);
    private static final int SURFACE_0 = Color.rgb(49, 50, 68);
    private static final int SURFACE_1 = Color.rgb(69, 71, 90);
    private static final int CARD_BORDER = Color.rgb(58, 59, 78);
    private static final int TEXT = Color.rgb(205, 214, 244);
    private static final int SUBTEXT_0 = Color.rgb(166, 173, 200);
    private static final int SUBTEXT_1 = Color.rgb(186, 194, 222);
    private static final int BLUE = Color.rgb(137, 180, 250);
    private static final int LAVENDER = Color.rgb(180, 190, 254);
    private static final int GREEN = Color.rgb(166, 227, 161);
    private static final int YELLOW = Color.rgb(249, 226, 175);
    private static final int RED = Color.rgb(243, 139, 168);
    private static final int SAPPHIRE = Color.rgb(116, 199, 236);

    private boolean working;
    private String progressMessage = "";
    private int progressPercent;
    private ProgressBar progressBar;
    private TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BASE);
        getWindow().setNavigationBarColor(BASE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BASE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, statusBarHeight() + dp(18), pad, navigationBarHeight() + dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Outlook Google Sync", 20, false, TEXT);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title);

        root.addView(calendarsCard());
        root.addView(syncCard());
        root.addView(activityCard());

        setContentView(scroll);
        updateProgress(progressMessage, progressPercent);
    }

    private View calendarsCard() {
        LinearLayout card = card("Selected Calendars", true);
        if (!hasCalendarPermission()) {
            card.addView(row(YELLOW, "Calendar access", "Grant calendar access to sync.", "Grant", v -> requestCalendarPermission()));
        }
        card.addView(row(
                sourceConfigured() ? GREEN : YELLOW,
                "Outlook",
                sourceSummary(),
                sourceConfigured() ? "Change" : "Select",
                v -> pickSourceCalendars()
        ));
        card.addView(row(
                targetConfigured() ? GREEN : YELLOW,
                "Google",
                targetSummary(),
                targetConfigured() ? "Change" : "Select",
                v -> pickTargetCalendar()
        ));
        return card;
    }

    private View syncCard() {
        LinearLayout card = card("Sync", false);
        String blocker = setupBlocker();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(stackActions() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(6), 0, dp(10));

        Button preview = button("Preview Changes", v -> runSync(false), ButtonStyle.PRIMARY);
        preview.setEnabled(!working && blocker == null);
        preview.setContentDescription("Preview changes");

        Button syncNow = button("Sync Now", v -> runSync(true), ButtonStyle.TONAL);
        syncNow.setEnabled(!working && blocker == null);
        syncNow.setContentDescription("Sync now");

        addActionButton(actions, preview);
        addActionButton(actions, syncNow);
        card.addView(actions);

        if (blocker != null) {
            TextView blockerView = text(blocker, 13, false, YELLOW);
            blockerView.setPadding(0, dp(2), 0, dp(10));
            card.addView(blockerView);
        } else {
            TextView ready = text(syncReadyMessage(), 13, false, GREEN);
            ready.setPadding(0, dp(2), 0, dp(10));
            card.addView(ready);
        }

        if (working) {
            progressText = text(progressMessage.isEmpty() ? "Working..." : progressMessage, 13, false, SAPPHIRE);
            progressText.setPadding(0, dp(2), 0, dp(6));
            card.addView(progressText);
            progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(progressPercent);
            card.addView(progressBar, matchWrap());
        } else {
            progressText = null;
            progressBar = null;
        }

        card.addView(hourlyRow(blocker));
        return card;
    }

    private View hourlyRow(String blocker) {
        boolean scheduled = AppConfig.scheduled(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(2));

        row.addView(statusDot(scheduled ? GREEN : SUBTEXT_0, "Hourly sync " + (scheduled ? "on" : "off")));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(10), 0);
        row.addView(labels, weightWrap(1f));

        labels.addView(text("Hourly sync", 14, true, TEXT));
        labels.addView(text(scheduled ? "On" : "Off", 13, false, SUBTEXT_1));

        Button toggle = button(scheduled ? "Turn Off" : "Turn On", v -> toggleHourly(), ButtonStyle.TONAL);
        toggle.setEnabled(!working && (scheduled || blocker == null));
        toggle.setContentDescription(scheduled ? "Turn off hourly sync" : "Turn on hourly sync");
        row.addView(toggle);
        return row;
    }

    private View activityCard() {
        LinearLayout card = card("Activity", false);
        card.addView(readOnlyRow("Last sync", HistoryStore.syncSummary(HistoryStore.latestSync(this)), lastSyncColor()));
        card.addView(readOnlyRow("Last upload", HistoryStore.uploadSummary(HistoryStore.latestUpload(this)), lastUploadColor()));
        card.addView(readOnlyRow("History policy", "Completed meetings kept for history", SUBTEXT_1));

        Button log = button("View Sync Log", v -> showLogSurface(), ButtonStyle.LINK);
        log.setContentDescription("View sync log");
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.START);
        actions.setPadding(0, dp(8), 0, 0);
        actions.addView(log, wrapContent());
        card.addView(actions, matchWrap());
        return card;
    }

    private void requestCalendarPermission() {
        if (hasCalendarPermission()) {
            buildUi();
            return;
        }
        requestPermissions(new String[]{
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        }, REQUEST_CALENDAR_PERMISSION);
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALENDAR_PERMISSION) {
            buildUi();
        }
    }

    private void pickSourceCalendars() {
        if (!hasCalendarPermission()) {
            showMessage("Calendar Access", "This app needs calendar access before you can select Outlook calendars.");
            return;
        }

        List<CalendarInfo> calendars = outlookCalendars();
        if (calendars.isEmpty()) {
            showMessage("Select Outlook Calendars", "No Outlook calendars are currently visible in the local Android calendar.");
            return;
        }

        Set<String> selectedIds = AppConfig.sourceCalendarIds(this);
        boolean[] checked = new boolean[calendars.size()];
        CharSequence[] labels = new CharSequence[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            CalendarInfo calendar = calendars.get(i);
            labels[i] = calendar.toString();
            checked[i] = selectedIds.contains(Long.toString(calendar.id));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select Outlook Calendars")
                .setMultiChoiceItems(labels, checked, (dialogInterface, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (dialogInterface, which) -> {
                    Set<String> next = new HashSet<>();
                    for (int i = 0; i < calendars.size(); i++) {
                        if (checked[i]) {
                            next.add(Long.toString(calendars.get(i).id));
                        }
                    }
                    AppConfig.setSourceCalendarIds(this, next);
                    buildUi();
                })
                .setNegativeButton("Cancel", null)
                .create();
        showStyled(dialog);
    }

    private void pickTargetCalendar() {
        if (!hasCalendarPermission()) {
            showMessage("Calendar Access", "This app needs calendar access before you can select a Google Calendar.");
            return;
        }

        List<CalendarInfo> calendars = AndroidCalendarReader.targetCalendars(this);
        if (calendars.isEmpty()) {
            showMessage("Select Google Calendar", "No writable Google calendars are currently visible in the local Android calendar.");
            return;
        }

        long currentId = AppConfig.targetCalendarId(this);
        int currentIndex = -1;
        CharSequence[] labels = new CharSequence[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            CalendarInfo calendar = calendars.get(i);
            labels[i] = calendar.toString();
            if (calendar.id == currentId) {
                currentIndex = i;
            }
        }

        final int[] selectedIndex = {currentIndex};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select Google Calendar")
                .setSingleChoiceItems(labels, currentIndex, (dialogInterface, which) -> selectedIndex[0] = which)
                .setPositiveButton("Save", (dialogInterface, which) -> {
                    if (selectedIndex[0] >= 0) {
                        AppConfig.setTargetCalendarId(this, calendars.get(selectedIndex[0]).id);
                    }
                    buildUi();
                })
                .setNegativeButton("Cancel", null)
                .create();
        showStyled(dialog);
    }

    private void toggleHourly() {
        if (AppConfig.scheduled(this)) {
            SyncScheduler.cancel(this);
        } else if (setupBlocker() == null) {
            SyncScheduler.schedule(this);
        }
        buildUi();
    }

    private void runSync(boolean apply) {
        String blocker = setupBlocker();
        if (blocker != null || working) {
            buildUi();
            return;
        }

        String label = apply ? "Manual sync" : "Manual preview";
        working = true;
        progressMessage = apply ? "Reading Outlook calendar events" : "Planning preview";
        progressPercent = 0;
        buildUi();

        long startedAt = System.currentTimeMillis();
        new Thread(() -> {
            try {
                SyncResult result = SyncEngine.run(this, apply, label,
                        (message, percent) -> runOnUiThread(() -> updateProgress(message, percent)));
                runOnUiThread(() -> {
                    working = false;
                    progressMessage = "";
                    progressPercent = 0;
                    buildUi();
                    if (apply) {
                        showSyncResult(result);
                    } else {
                        showPreview(result);
                    }
                });
            } catch (Exception e) {
                AppLog.logBlock(this, SyncLogFormatter.syncFailed(label, startedAt, e));
                AppLog.log(this, e);
                HistoryStore.recordSyncFailure(this, label, startedAt, e);
                runOnUiThread(() -> {
                    working = false;
                    progressMessage = "";
                    progressPercent = 0;
                    buildUi();
                    showMessage("Sync Failed", "No changes were applied.\n\n" + e.getMessage());
                });
            }
        }, "manual-sync").start();
    }

    private void updateProgress(String message, int percent) {
        progressMessage = message == null ? "" : message;
        progressPercent = Math.max(0, Math.min(100, percent));
        if (progressText != null) {
            progressText.setText(progressMessage);
        }
        if (progressBar != null) {
            progressBar.setProgress(progressPercent);
        }
    }

    private void showHowThisWorks() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(stepRow("1", "Outlook syncs to Android Calendar",
                "Enable calendar sync in the Outlook Android app first."));
        body.addView(stepRow("2", "This app copies events to Google",
                "This app reads those local Android calendar events and copies them into the selected Google Calendar."));
        body.addView(requirementRow());
        showContentDialog("How it works", body);
    }

    private void showPreview(SyncResult result) {
        String body = result.preview.isEmpty()
                ? "No changes found. No changes were applied."
                : result.preview;
        showMessage("Preview Changes", body);
    }

    private void showSyncResult(SyncResult result) {
        String upload = result.uploadState != null && result.uploadState.isComplete() ? "Complete" : "Waiting";
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(summaryBanner("Success", syncSummaryLine(result)));
        body.addView(metricGrid(
                "Outlook events", Integer.toString(result.sourceOccurrences),
                "Added", Integer.toString(result.created),
                "Updated", Integer.toString(result.updated),
                "Removed", Integer.toString(result.deletedFuture),
                "History kept", Integer.toString(result.keptPastStale),
                "Google upload", upload
        ));
        showActionDialog("Sync Complete", body, "View Log", v -> showLogSurface());
    }

    private void showLogSurface() {
        BottomSheetDialog dialog = bottomSheetDialog(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), statusBarHeight() + dp(14), dp(16), navigationBarHeight() + dp(12));
        root.setBackgroundColor(BASE);
        root.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        TextView title = text("Sync Log", 20, false, TEXT);
        header.addView(title, weightWrap(1f));

        Button done = button("Done", v -> dialog.dismiss(), ButtonStyle.PRIMARY);
        header.addView(done, wrapContent());
        root.addView(header, matchWrap());

        TextView helper = text("Newest first. Tap an entry to expand details.", 13, false, SUBTEXT_0);
        helper.setPadding(0, 0, 0, dp(12));
        root.addView(helper, matchWrap());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        List<HistoryEntry> entries = HistoryStore.entries(this);
        if (entries.isEmpty()) {
            TextView empty = dialogBodyText("No sync log yet.");
            content.addView(empty);
        } else {
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
                content.addView(logEntry(entry, children, first));
                first = false;
            }
            for (HistoryEntry entry : entries) {
                if (entry.isUpload() && !groupedUploads.contains(entry)) {
                    content.addView(logEntry(entry, null, first));
                    first = false;
                }
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(10), 0, 0);
        Button clear = button("Clear Sync Log", v -> confirmClearLog(dialog), ButtonStyle.LINK);
        clear.setTextColor(RED);
        footer.addView(clear, wrapContent());
        root.addView(footer, matchWrap());

        dialog.setContentView(root);
        dialog.show();
    }

    private View logEntry(HistoryEntry entry, List<HistoryEntry> uploadChildren, boolean expanded) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(rounded(SURFACE_0, CARD_BORDER, 8));
        LinearLayout.LayoutParams boxParams = matchWrap();
        boxParams.setMargins(0, 0, 0, dp(10));
        box.setLayoutParams(boxParams);

        TextView headline = text(logHeadline(entry), 13, true, logStatusColor(entry));
        headline.setSingleLine(false);
        headline.setMaxLines(2);
        box.addView(headline, matchWrap());

        TextView meta = text(logMeta(entry), 12, false, SUBTEXT_0);
        meta.setPadding(0, dp(2), 0, 0);
        box.addView(meta, matchWrap());

        LinearLayout details = entry.isSync()
                ? syncLogDetails(entry, uploadChildren)
                : uploadLogDetails(entry);
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        details.setPadding(0, dp(10), 0, 0);

        box.addView(details, matchWrap());
        box.setOnClickListener(v -> details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return box;
    }

    private View stepRow(String number, String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, dp(14));

        TextView badge = text(number, 12, true, CRUST);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(24));
        badge.setMinHeight(dp(24));
        badge.setBackground(oval(SAPPHIRE));
        row.addView(badge);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        row.addView(labels, weightWrap(1f));

        labels.addView(text(title, 14, true, TEXT));
        TextView detailView = text(detail, 13, false, SUBTEXT_1);
        detailView.setLineSpacing(dp(2), 1.0f);
        labels.addView(detailView);
        return row;
    }

    private View requirementRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, 0);

        boolean ready = sourceConfigured();
        row.addView(statusDot(ready ? GREEN : YELLOW, ready ? "Outlook calendar visible" : "Outlook calendar needs attention"));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        row.addView(labels, weightWrap(1f));

        labels.addView(text("Outlook calendar", 13, true, TEXT));
        labels.addView(text(ready ? "Visible in the local Android calendar" : "Open Outlook and enable calendar sync", 12, false, SUBTEXT_1));
        return row;
    }

    private View summaryBanner(String title, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(rounded(CRUST, CARD_BORDER, 8));

        box.addView(text(title, 14, true, GREEN));
        TextView detailView = text(detail, 13, false, SUBTEXT_1);
        detailView.setPadding(0, dp(2), 0, 0);
        box.addView(detailView);

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(params);
        return box;
    }

    private LinearLayout syncLogDetails(HistoryEntry entry, List<HistoryEntry> uploadChildren) {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        if (HistoryStore.STATUS_FAILED.equals(entry.status())) {
            details.addView(metricGrid(
                    "Result", "Failed",
                    "Duration", HistoryStore.formatDuration(entry.json.optLong("durationMs", 0L)),
                    "Reason", entry.json.optString("errorMessage", entry.json.optString("errorType", "Error"))
            ));
        } else {
            boolean applied = entry.json.optBoolean("applied", false);
            if (applied) {
                details.addView(metricGrid(
                        "Result", entry.status(),
                        "Duration", HistoryStore.formatDuration(entry.json.optLong("durationMs", 0L)),
                        "Outlook events", Integer.toString(entry.json.optInt("sourceOccurrences", 0)),
                        "Mirrored before", Integer.toString(entry.json.optInt("existingMirroredOccurrences", 0)),
                        "Added", Integer.toString(entry.json.optInt("created", 0)),
                        "Updated", Integer.toString(entry.json.optInt("updated", 0)),
                        "Removed", Integer.toString(entry.json.optInt("deletedFuture", 0)),
                        "History kept", Integer.toString(entry.json.optInt("keptPastStale", 0)),
                        "Google upload", uploadStatus(entry, uploadChildren)
                ));
            } else {
                details.addView(metricGrid(
                        "Result", "Preview",
                        "Duration", HistoryStore.formatDuration(entry.json.optLong("durationMs", 0L)),
                        "Outlook events", Integer.toString(entry.json.optInt("sourceOccurrences", 0)),
                        "Mirrored before", Integer.toString(entry.json.optInt("existingMirroredOccurrences", 0)),
                        "Added", Integer.toString(entry.json.optInt("created", 0)),
                        "Updated", Integer.toString(entry.json.optInt("updated", 0)),
                        "Removed", Integer.toString(entry.json.optInt("deletedFuture", 0)),
                        "History kept", Integer.toString(entry.json.optInt("keptPastStale", 0))
                ));
            }
        }

        details.addView(rawDetailsToggle(entry.isSync()
                ? HistoryStore.syncDetails(entry, uploadChildren)
                : HistoryStore.uploadDetails(entry)));
        return details;
    }

    private LinearLayout uploadLogDetails(HistoryEntry entry) {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(metricGrid(
                "Result", HistoryStore.uploadSummary(entry),
                "Duration", HistoryStore.formatDuration(entry.json.optLong("durationMs", 0L)),
                "Uploaded", Integer.toString(entry.json.optInt("uploadCleanActive", 0)),
                "Waiting", Integer.toString(entry.json.optInt("uploadDirtyActive", 0)),
                "Removals waiting", Integer.toString(entry.json.optInt("uploadDirtyDeleted", 0))
        ));
        details.addView(rawDetailsToggle(HistoryStore.uploadDetails(entry)));
        return details;
    }

    private View rawDetailsToggle(String raw) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, 0);

        TextView rawText = text(raw, 12, false, SUBTEXT_0);
        rawText.setTypeface(Typeface.MONOSPACE);
        rawText.setTextIsSelectable(true);
        rawText.setLineSpacing(dp(2), 1.0f);
        rawText.setPadding(dp(10), dp(8), dp(10), dp(8));
        rawText.setBackground(rounded(CRUST, CRUST, 6));
        rawText.setVisibility(View.GONE);

        Button toggle = button("Raw details", v -> rawText.setVisibility(rawText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE), ButtonStyle.LINK);
        toggle.setTextColor(SUBTEXT_0);
        toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        box.addView(toggle, wrapContent());
        box.addView(rawText, matchWrap());
        return box;
    }

    private LinearLayout metricGrid(String... values) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(4), 0, 0);
        for (int i = 0; i + 1 < values.length; i += 2) {
            grid.addView(metricRow(values[i], values[i + 1]));
        }
        return grid;
    }

    private View metricRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView labelView = text(label, 12, false, SUBTEXT_0);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.46f));

        TextView valueView = text(value == null || value.isEmpty() ? "-" : value, 12, true, TEXT);
        valueView.setGravity(Gravity.END);
        valueView.setSingleLine(false);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.54f));
        return row;
    }

    private String logHeadline(HistoryEntry entry) {
        if (entry.isUpload()) {
            return "Google upload " + HistoryStore.uploadSummary(entry).toLowerCase(Locale.US) + " - "
                    + entry.json.optInt("uploadCleanActive", 0) + " uploaded";
        }
        if (HistoryStore.STATUS_FAILED.equals(entry.status())) {
            String message = entry.json.optString("errorMessage", "");
            return message.isEmpty() ? "Failed" : "Failed - " + message;
        }

        int checked = entry.json.optInt("sourceOccurrences", 0);
        int changed = entry.json.optInt("created", 0)
                + entry.json.optInt("updated", 0)
                + entry.json.optInt("deletedFuture", 0);
        if (!entry.json.optBoolean("applied", false)) {
            return "Preview - " + checked + " events checked, no changes applied";
        }
        return entry.status() + " - " + checked + " events checked, "
                + (changed == 0 ? "no changes needed" : changeSummary(entry));
    }

    private String logMeta(HistoryEntry entry) {
        String label = entry.isUpload() ? entry.json.optString("syncLabel", "Upload") : entry.label();
        return AppLog.blockTimestamp(entry.timestamp()) + " - " + label;
    }

    private int logStatusColor(HistoryEntry entry) {
        if (HistoryStore.STATUS_FAILED.equals(entry.status())) {
            return RED;
        }
        if (entry.isUpload() && !HistoryStore.STATUS_COMPLETE.equals(entry.status())) {
            return YELLOW;
        }
        if (entry.isSync() && !entry.json.optBoolean("applied", false)) {
            return SAPPHIRE;
        }
        return GREEN;
    }

    private String syncSummaryLine(SyncResult result) {
        int changed = result.created + result.updated + result.deletedFuture;
        return result.sourceOccurrences + " events checked, "
                + (changed == 0 ? "no changes needed" : changed + " changes applied");
    }

    private String changeSummary(HistoryEntry entry) {
        return entry.json.optInt("created", 0) + " added, "
                + entry.json.optInt("updated", 0) + " updated, "
                + entry.json.optInt("deletedFuture", 0) + " removed";
    }

    private String uploadStatus(HistoryEntry entry, List<HistoryEntry> uploadChildren) {
        if (uploadChildren != null && !uploadChildren.isEmpty()) {
            return HistoryStore.uploadSummary(uploadChildren.get(0));
        }
        String status = entry.json.optString("uploadStatus", "");
        return status.isEmpty() ? "Not started" : status;
    }

    private void confirmClearLog(BottomSheetDialog parent) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(dialogBodyText("Clear the structured sync history and raw debug log?"));

        final BottomSheetDialog dialog = bottomSheetDialog(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackground(rounded(SURFACE_0, CARD_BORDER, 8));
        TextView title = text("Clear Sync Log", 18, false, TEXT);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title, matchWrap());
        root.addView(body, matchWrap());

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        footer.setPadding(0, dp(12), 0, 0);
        footer.addView(button("Cancel", v -> dialog.dismiss(), ButtonStyle.LINK), wrapContent());
        Button clear = button("Clear", v -> {
            HistoryStore.clear(this);
            AppLog.clear(this);
            dialog.dismiss();
            parent.dismiss();
            buildUi();
        }, ButtonStyle.TONAL);
        clear.setTextColor(RED);
        LinearLayout.LayoutParams params = wrapContent();
        params.leftMargin = dp(8);
        footer.addView(clear, params);
        root.addView(footer, matchWrap());

        dialog.setContentView(root);
        dialog.show();
    }

    private String setupBlocker() {
        if (!hasCalendarPermission()) {
            return "Grant calendar access to sync.";
        }
        Set<String> selectedSourceIds = AppConfig.sourceCalendarIds(this);
        if (selectedSourceIds.isEmpty()) {
            return "Select at least one Outlook calendar.";
        }
        List<CalendarInfo> sources = AndroidCalendarReader.sourceCalendars(this);
        if (sources.isEmpty()) {
            return "Selected Outlook calendars are not available.";
        }
        for (CalendarInfo source : sources) {
            if (!source.syncEvents) {
                return "Enable sync for the selected Outlook calendar.";
            }
        }
        if (AppConfig.targetCalendarId(this) < 0L) {
            return "Select a Google Calendar.";
        }
        if (!targetConfigured()) {
            return "Selected Google Calendar is not available for sync.";
        }
        return null;
    }

    private boolean sourceConfigured() {
        return hasCalendarPermission()
                && !AppConfig.sourceCalendarIds(this).isEmpty()
                && !AndroidCalendarReader.sourceCalendars(this).isEmpty();
    }

    private boolean targetConfigured() {
        if (!hasCalendarPermission()) {
            return false;
        }
        long targetId = AppConfig.targetCalendarId(this);
        if (targetId < 0L) {
            return false;
        }
        for (CalendarInfo calendar : AndroidCalendarReader.targetCalendars(this)) {
            if (calendar.id == targetId) {
                return true;
            }
        }
        return false;
    }

    private String sourceSummary() {
        if (!hasCalendarPermission()) {
            return "Calendar access needed";
        }
        Set<String> selected = AppConfig.sourceCalendarIds(this);
        if (selected.isEmpty()) {
            return "Not selected";
        }
        List<CalendarInfo> calendars = AndroidCalendarReader.sourceCalendars(this);
        if (calendars.isEmpty()) {
            return "Selected calendars unavailable";
        }
        List<String> names = new ArrayList<>();
        for (CalendarInfo calendar : calendars) {
            names.add(calendar.name);
        }
        return join(names);
    }

    private String targetSummary() {
        if (!hasCalendarPermission()) {
            return "Calendar access needed";
        }
        long targetId = AppConfig.targetCalendarId(this);
        if (targetId < 0L) {
            return "Not selected";
        }
        for (CalendarInfo calendar : AndroidCalendarReader.targetCalendars(this)) {
            if (calendar.id == targetId) {
                return calendar.name;
            }
        }
        return "Selected calendar unavailable";
    }

    private String syncReadyMessage() {
        return HistoryStore.latestSync(this) == null
                ? "Ready to sync. No sync has run yet."
                : "Ready to sync.";
    }

    private int lastSyncColor() {
        HistoryEntry latest = HistoryStore.latestSync(this);
        if (latest == null) {
            return SUBTEXT_1;
        }
        return HistoryStore.STATUS_FAILED.equals(latest.status()) ? RED : GREEN;
    }

    private int lastUploadColor() {
        HistoryEntry latest = HistoryStore.latestUpload(this);
        if (latest == null) {
            return SUBTEXT_1;
        }
        return HistoryStore.STATUS_COMPLETE.equals(latest.status()) ? GREEN : YELLOW;
    }

    private List<CalendarInfo> outlookCalendars() {
        List<CalendarInfo> calendars = new ArrayList<>();
        for (CalendarInfo calendar : AndroidCalendarReader.listCalendars(this)) {
            if (AppConfig.OUTLOOK_ACCOUNT_TYPE.equals(calendar.accountType)) {
                calendars.add(calendar);
            }
        }
        return calendars;
    }

    private LinearLayout card(String title, boolean info) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(16), dp(14), dp(16), dp(16));
        outer.setBackground(rounded(SURFACE_0, CARD_BORDER, 8));

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(14));
        outer.setLayoutParams(params);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        TextView heading = text(title, 16, true, TEXT);
        header.addView(heading, weightWrap(1f));
        if (info) {
            TextView link = linkText("How it works", v -> showHowThisWorks());
            link.setContentDescription("How this works");
            header.addView(link);
        }
        outer.addView(header);
        return outer;
    }

    private View row(int color, String label, String value, String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        row.addView(statusDot(color, label + " " + value));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(10), 0);
        row.addView(labels, weightWrap(1f));

        labels.addView(text(label, 14, true, TEXT));
        TextView valueView = text(value, 13, false, SUBTEXT_1);
        valueView.setSingleLine(false);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(valueView);

        Button button = button(action, listener, ButtonStyle.TONAL);
        button.setEnabled(!working);
        button.setContentDescription(action + " " + label);
        row.addView(button, actionButtonParams());
        return row;
    }

    private View readOnlyRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView labelView = text(label, 13, false, SUBTEXT_0);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f));

        TextView valueView = text(value, 13, true, valueColor);
        valueView.setGravity(Gravity.END);
        valueView.setSingleLine(false);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.58f));
        return row;
    }

    private View statusDot(int color, String description) {
        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
        dot.setBackground(oval(color));
        dot.setContentDescription(description);
        return dot;
    }

    private TextView linkText(String label, View.OnClickListener listener) {
        TextView view = text(label, 13, false, SAPPHIRE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), dp(6), 0, dp(6));
        view.setMinHeight(dp(40));
        view.setClickable(true);
        view.setOnClickListener(listener);
        return view;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private Button button(String label, View.OnClickListener listener, ButtonStyle style) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setMinWidth(dp(82));
        button.setMinimumWidth(dp(82));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextSize(14);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setGravity(Gravity.CENTER);
        applyButtonStyle(button, style);
        if (listener != null) {
            button.setOnClickListener(listener);
        }
        return button;
    }

    private void applyButtonStyle(Button button, ButtonStyle style) {
        if (style == ButtonStyle.PRIMARY) {
            button.setTextColor(CRUST);
            button.setBackground(rounded(BLUE, BLUE, 8));
        } else if (style == ButtonStyle.LINK) {
            button.setTextColor(SAPPHIRE);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setBackground(rounded(TRANSPARENT, TRANSPARENT, 8));
        } else {
            button.setTextColor(LAVENDER);
            button.setBackground(rounded(SURFACE_1, SURFACE_1, 8));
        }
    }

    private void addActionButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = stackActions() ? matchWrap() : weightWrap(1f);
        if (stackActions()) {
            params.setMargins(0, parent.getChildCount() == 0 ? 0 : dp(8), 0, 0);
        } else {
            params.setMargins(parent.getChildCount() == 0 ? 0 : dp(8), 0, 0, 0);
        }
        parent.addView(button, params);
    }

    private boolean stackActions() {
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        float fontScale = getResources().getConfiguration().fontScale;
        return widthDp < 380f || fontScale > 1.2f;
    }

    private void showMessage(String title, String message) {
        showContentDialog(title, dialogBodyText(message));
    }

    private void showContentDialog(String title, View body) {
        showActionDialog(title, body, null, null);
    }

    private void showActionDialog(String title, View body, String secondaryLabel, View.OnClickListener secondaryAction) {
        final BottomSheetDialog dialog = bottomSheetDialog(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackground(rounded(SURFACE_0, CARD_BORDER, 8));

        TextView titleView = text(title, 18, false, TEXT);
        titleView.setPadding(0, 0, 0, dp(12));
        root.addView(titleView, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body);
        root.addView(scroll, matchWrap());

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        footer.setPadding(0, dp(12), 0, 0);
        if (secondaryLabel != null && secondaryAction != null) {
            Button secondary = button(secondaryLabel, v -> {
                dialog.dismiss();
                secondaryAction.onClick(v);
            }, ButtonStyle.LINK);
            footer.addView(secondary, wrapContent());
        }
        Button done = button("Done", v -> dialog.dismiss(), ButtonStyle.PRIMARY);
        LinearLayout.LayoutParams doneParams = wrapContent();
        doneParams.leftMargin = dp(8);
        footer.addView(done, doneParams);
        root.addView(footer, matchWrap());

        dialog.setContentView(root);
        dialog.show();
    }

    private void showStyled(AlertDialog dialog) {
        dialog.setOnShowListener(dialogInterface -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(BLUE);
            }
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(LAVENDER);
            }
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) {
                neutral.setTextColor(LAVENDER);
            }
        });
        dialog.show();
    }

    private TextView dialogBodyText(String message) {
        TextView view = text(message, 13, false, SUBTEXT_1);
        view.setTextIsSelectable(true);
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private BottomSheetDialog bottomSheetDialog(boolean fullHeight) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setDismissWithAnimation(true);
        dialog.setOnShowListener(dialogInterface -> configureBottomSheet(dialog, fullHeight));
        return dialog;
    }

    private void configureBottomSheet(BottomSheetDialog dialog, boolean fullHeight) {
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) {
            return;
        }
        if (fullHeight) {
            sheet.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(values.get(i));
        }
        return out.toString();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams actionButtonParams() {
        return new LinearLayout.LayoutParams(
                dp(88),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable oval(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    private int statusBarHeight() {
        return systemDimension("status_bar_height");
    }

    private int navigationBarHeight() {
        return systemDimension("navigation_bar_height");
    }

    private int systemDimension(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private enum ButtonStyle {
        PRIMARY,
        TONAL,
        LINK
    }
}
