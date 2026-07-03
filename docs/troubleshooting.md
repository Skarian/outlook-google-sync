# Troubleshooting

Most failures come from Android calendar visibility or sync state. This app can
only read and write calendars that Android exposes through the Calendar Provider.

## A Google calendar created on the web does not appear

Open Google Calendar on your phone. Make sure the calendar is listed, visible,
and enabled for sync under that Google account.

The app only lists writable Google calendars that are already syncing on the
phone.

## Outlook calendars do not appear

Open Outlook for Android and enable calendar sync for the account. After that,
give Android a minute to expose the Outlook calendars through local calendar
storage.

If the app still does not show them, restart Outlook and Google Calendar, then
open Outlook Google Sync again.

## Preview shows zero Outlook events

Check these first:

- Calendar permission is granted.
- At least one Outlook calendar is selected.
- Outlook calendar sync is enabled.
- The meetings fall inside the sync range: one year ago through one year from
  now.
- The selected Outlook calendar is visible in another Android calendar app.

When Outlook returns zero events while future copied events exist, the app skips
removals for that run. That guard prevents a bad read from wiping the selected
Google Calendar.

## Upload stays pending

The app writes to the local Google Calendar on your phone. Google's Android
calendar sync uploads those changes later.

Check that:

- the phone has network access;
- Google Calendar sync is enabled for the Google account;
- battery saver is not blocking calendar sync;
- Google Calendar has had time to upload.

Pending upload state is based on Android calendar row flags. It is a local
signal, not a direct Google Calendar API response.

## Hourly sync does not run exactly every hour

Hourly sync uses Android `JobScheduler`. Android may delay background work,
especially under battery saver, app standby, low battery, or manufacturer power
management.

Set Outlook Google Sync to unrestricted battery usage if you rely on background
sync.

## Updating an older debug APK fails

Older local builds may have been signed with a different debug key. Uninstall
the old debug build, then install the new one.

Public release builds should use a private release signing key and should not
share the debug package ID.
