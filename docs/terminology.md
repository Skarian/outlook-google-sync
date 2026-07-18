# Terminology

This file keeps the app's user-facing language consistent.

## Product scope

Outlook Google Sync is Android-only. To copy Outlook events into Google
Calendar, first enable calendar sync in Outlook for Android. Outlook then writes
your Outlook calendar into Android's local calendar storage.

This app reads those local Android calendar events and copies them into the
selected Google Calendar that is also syncing on your phone.

Do not describe the app as two-way sync.

## Terms

### Local Android calendar

Android's local calendar storage. Calendar apps and sync adapters use it to
share calendar data on the phone.

### Selected Google Calendar

The Google Calendar chosen in this app to receive copied Outlook events.

Use "selected Google Calendar" in UI and docs. Avoid "target calendar" outside
developer-only code.

### Mirrored calendar events

Events created by this app in the selected Google Calendar.

Use "mirrored calendar events" when you need a precise term. Use "copied
events" when explaining the pipeline in plainer language.

### Sync range

The dates the app checks each time it runs.

The current sync range is one year ago through one year from now. Events inside
that range are created, updated, or removed according to the sync rules.

### Keep completed meetings for history

Meetings that already happened stay in the selected Google Calendar for
historical reference, even if the meeting or meeting series later disappears
from Outlook.

## Approved UI language

Calendar setup:

- `Selected Calendars`
- `Outlook`
- `Google`
- `Select Outlook Calendars`
- `Select Google Calendar`

Main actions:

- `Preview Changes`
- `Sync Now`
- `Hourly sync`
- `Turn On`
- `Turn Off`
- `View Sync Log`
- `Export Sync Log`
- `Clear Sync Log`

Sync and log labels:

- `Outlook events read`
- `Mirrored calendar events before sync`
- `Added`
- `Updated`
- `Removed canceled upcoming meetings`
- `Kept completed meetings for history`
- `Sync range`
- `Google Calendar upload`
- `Local upload state`

Avoid these in user-facing text:

- source calendar
- target calendar
- future stale
- past stale
- dirty rows
- window
- horizon

## Fixed behavior

The app does not offer a cleanup checkbox. Completed meetings are kept for
history. Canceled upcoming meetings are removed from the selected Google
Calendar.
