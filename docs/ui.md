# UI notes

This file describes the app UI we are building toward. Keep it practical. The
app is a small settings-and-sync utility, not a landing page.

## Main screen

Use three cards:

1. `Selected Calendars`
2. `Sync`
3. `Activity`

Spacing:

- screen padding: `16dp`
- top padding: include the status bar height plus breathing room
- card gap: `14dp`
- card padding: `16dp`
- card radius: `8dp`
- button height: at least `44dp`
- control gap: `8dp`

Text:

- app title: `20sp`, regular
- card title: `16sp`, bold
- row label: `14sp`, medium
- row value: `13sp`
- helper text: `13sp`

Colors use Catppuccin Mocha:

- background: `#1e1e2e`
- card: `#313244`
- subtle border: `#45475a`
- primary text: `#cdd6f4`
- secondary text: `#bac2de`
- muted text: `#a6adc8`
- primary action: `#89b4fa`
- secondary action: `#b4befe`
- success: `#a6e3a1`
- warning: `#f9e2af`
- error: `#f38ba8`
- progress: `#74c7ec`

## State rules

Do not add a separate status card. Show state in the row that owns it.

Use a small circular dot or icon plus text:

- green: configured
- amber: needs attention
- blue: running
- red: failed or unavailable

Color is not enough by itself. Every state also needs readable text and an
accessibility label.

If setup is incomplete, show one blocker line in the `Sync` card:

- `Grant calendar access to sync.`
- `Select Outlook calendars to sync.`
- `Select a Google Calendar to sync.`

Do not repeat the same blocker in multiple places.

## Selected calendars

Rows:

- `Outlook`
- `Google`

Only show `Calendar access` when permission is missing. Hide it once permission
is granted.

Configured row:

```text
[green dot] Outlook
            Calendar, Birthdays
            [Change]
```

Missing row:

```text
[amber dot] Google
            Not selected
            [Select]
```

Rules:

- the row itself is not tappable;
- only `Select` or `Change` opens a picker;
- calendar names may wrap to two lines, then ellipsize;
- account and local IDs stay out of the main row unless we add a diagnostics
  surface.

The card header includes a small `How it works` text link. It opens a sheet:

```text
How it works

1  Outlook syncs to Android Calendar
   Enable calendar sync in Outlook for Android first.

2  This app copies events to Google
   This app reads those local Android calendar events and copies them into the
   selected Google Calendar.
```

## Sync card

Actions:

```text
[Preview Changes] [Sync Now]
```

- `Preview Changes` is the primary action.
- `Sync Now` is secondary.
- Buttons have the same height.
- On narrow screens or large font settings, stack the buttons.

When setup is complete:

```text
Ready to sync.
```

If no sync has run:

```text
Ready to sync. No sync has run yet.
```

Hourly sync row:

```text
[clock] Hourly sync
        On
        [Turn Off]
```

If setup later becomes invalid, leave hourly sync on. Show the blocker line and
keep `Turn Off` available.

## Running state

While sync or preview is running:

- disable `Preview Changes`;
- disable `Sync Now`;
- disable hourly sync controls;
- show a progress bar inside the `Sync` card;
- show one short step line.

Step text:

- `Reading Outlook calendar events`
- `Reading mirrored calendar events`
- `Planning changes`
- `Updating mirrored calendar events`
- `Done`

## Preview sheet

`Preview Changes` opens a Material bottom sheet. It should look like a review,
not a debug log.

Show:

- sync range;
- added count;
- updated count;
- removed canceled upcoming meetings count;
- kept completed meetings count;
- sample changes;
- `No changes were applied.`

If there are no changes, say:

```text
No changes found.
No changes were applied.
```

## Manual sync result

Manual `Sync Now` shows a result sheet when it finishes. Background sync does
not interrupt the user; it updates the activity card and sync log.

Example:

```text
Sync Complete

Success
711 events checked, no changes needed

Outlook events  711
Added           0
Updated         0
Removed         0
History kept    0
Google upload   Waiting

[View Log] [Done]
```

## Activity card

Rows:

- `Last sync`
- `Last upload`
- `History policy`

Examples:

```text
Last sync        Today 4:07 PM - Manual sync
Last upload      Complete
History policy   Completed meetings kept for history
```

Preview entries appear in the sync log, but they do not count as `Last sync`
because no calendar changes were applied.

`View Sync Log` is a quiet text-style action, not a full-width primary button.

## Sheets and modals

Use Material Components bottom sheets for app-owned modal surfaces. Do not
hand-roll Android `Dialog` windows or resize/restyle windows after `show()`;
that causes visible layout shifts.

Use sheets for:

- preview changes;
- manual sync result;
- sync log;
- how it works;
- clear-log confirmation.

Use Android picker dialogs for calendar selection.

Sheet rules:

- use a strong scrim;
- align title, body, and actions to the same horizontal padding;
- keep body text around `13sp`;
- make `Done` the primary action;
- make destructive actions quieter and confirm them.

## Sync log

`View Sync Log` opens a full-height bottom sheet with newest entries first.

The footer has quiet text-style actions:

- `Export Sync Log`;
- `Clear Sync Log`.

`Export Sync Log` opens Android's document save flow and writes a text file with
the retained structured sync history from the last 30 days. It must not export
the private raw debug log, raw JSONL, hidden raw details, stack traces, event
titles, descriptions, participant names, participant emails, or internal
calendar IDs.

Each entry is collapsible:

- collapsed row: result summary and timestamp;
- expanded row: structured key/value details;
- raw details: hidden behind a `Raw details` expander.

Do not show event titles, descriptions, participant names, participant emails,
or internal calendar IDs in the default structured view. Raw details may include
local IDs for debugging, so users should review them before sharing.
