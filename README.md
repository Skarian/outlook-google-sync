# Outlook Google Sync

Outlook Google Sync is a small Android app that copies Outlook calendar events
from Android's local calendar storage into one Google Calendar that is already
syncing on your phone.

It is Android-only and one-way:

```text
Outlook for Android -> local Android calendar -> this app -> selected Google Calendar
```

The app does not use Microsoft Graph, the Google Calendar API, OAuth clients,
service accounts, Google Cloud projects, or a hosted backend. It reads and
writes through Android's Calendar Provider.

For the safest setup, create a dedicated Google calendar, for example
`Outlook Mirror`, and select that calendar in the app.

## At a glance

- Copies events from selected Outlook calendars into one selected Google
  Calendar.
- Uses the local Android calendar as the bridge between Outlook and Google.
- Keeps meetings that already happened for historical reference, even if they
  later disappear from Outlook.
- Removes canceled upcoming meetings from the selected Google Calendar.
- Runs manually from the app or roughly hourly through Android `JobScheduler`.
- Has no `INTERNET` permission. Google's own Android calendar sync uploads the
  copied events like any other local Google Calendar change.

## Requirements

- Android 8.0 or newer.
- Outlook for Android with calendar sync enabled.
- A writable Google Calendar that is visible and syncing on the same phone.
- Calendar permission for this app: `READ_CALENDAR` and `WRITE_CALENDAR`.

If you created a calendar on calendar.google.com and it does not appear in the
app, open Google Calendar on your phone and make sure that calendar is enabled
for sync.

## Setup

1. Install an APK from the GitHub Releases page or build one locally.
2. In Outlook for Android, enable calendar sync.
3. Open Outlook Google Sync.
4. Grant calendar access when Android asks.
5. In `Selected Calendars`, choose the Outlook calendars to copy from.
6. Choose the Google Calendar that should receive the copied events.
7. Tap `Preview Changes` to review the counts before writing anything.
8. Tap `Sync Now` to apply the copy.
9. Turn on `Hourly sync` if you want Android to run it in the background.

Preview is a dry run. It reads the same data and builds the same plan, but it
does not write to any calendar.

## Sync behavior

- The app reads Android `CalendarContract.Instances`, so recurring Outlook
  meetings are copied as individual occurrences.
- Each copied occurrence becomes a non-recurring event in the selected Google
  Calendar.
- Copied events are tagged with this app's package and a stable internal key.
  The app only updates or removes events it created in the selected Google
  Calendar.
- The sync range is fixed at one year ago through one year from now.
- Event title, time, location, description, visibility, availability, and status
  are copied.
- Attendees are copied into a `Participants:` block in the event description.
  The list is capped at 30 people.
- If Outlook returns zero events while upcoming copied events still exist, the
  app skips removals for that run. This avoids wiping the selected Google
  Calendar during a bad Outlook or Android calendar read.

## Background sync

Hourly sync uses Android `JobScheduler`. Android decides the exact run time, so
background sync can drift under battery saver, app standby, poor network
conditions, or manufacturer battery restrictions.

If you rely on hourly sync, set Outlook Google Sync to unrestricted battery
usage in Android settings.

## Privacy

The app stores its settings, recent sync history, and debug log in app-private
Android storage. It does not send data to its own server because there is no
server. Copied events do sync to Google through the normal Google Calendar sync
adapter once they are written into the selected Google Calendar.

Read [PRIVACY.md](PRIVACY.md) before sharing logs or publishing screenshots.

## Build

Use the Gradle wrapper:

```sh
./gradlew assembleDebug
```

The debug build uses `applicationIdSuffix ".debug"` so it does not share the
release package ID.

Release signing is documented in [docs/release.md](docs/release.md). Do not
commit keystores, APKs, app logs, or personal calendar data.

## More docs

- [Troubleshooting](docs/troubleshooting.md)
- [Release process](docs/release.md)
- [Reference review](docs/reference-review.md)
- [Terminology](docs/terminology.md)
- [UI notes](docs/ui.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Security](SECURITY.md)

## License

MIT. See [LICENSE](LICENSE).
