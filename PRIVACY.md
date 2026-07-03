# Privacy

Outlook Google Sync works through Android's Calendar Provider. It does not have
an `INTERNET` permission and does not connect to Microsoft, Google, or any app
server directly.

## What the app reads

With calendar permission, the app reads:

- the list of local Android calendars on your phone;
- events and recurring event instances from the Outlook calendars you select;
- event title, time, location, description, time zone, availability, visibility,
  status, and attendee rows;
- local upload state for copied Google Calendar events, using Android calendar
  row flags such as `dirty` and `deleted`.

## What the app writes

The app writes copied events only to the selected Google Calendar. It writes:

- title, time, location, description, time zone, availability, visibility, and
  event status;
- a `Participants:` block in the copied event description, capped at 30 people;
- Android calendar metadata that marks the event as created by this app.

The app does not create actual Google guests. Participant names and email
addresses are plain text inside the copied event description.

## What reaches Google

After the app writes copied events into a Google Calendar on your phone,
Google's normal Android calendar sync can upload those events to Google
Calendar. That is the intended behavior.

Use a dedicated Google calendar such as `Outlook Mirror` if you want to keep the
copied events separate from your primary calendar.

## What the app stores locally

The app stores this data in app-private Android storage:

- selected Outlook calendar IDs;
- selected Google Calendar ID;
- whether hourly sync is enabled;
- sync history entries with timestamps, counts, duration, upload state, and
  error messages;
- a bounded debug log, capped at 256 KB.

Normal sync history does not list event titles or participant names. Preview
screens can show sample event titles because they are shown to you before a
manual sync.

Stack traces and Android error messages can include account names, calendar IDs,
or other local identifiers. Read logs before sharing them.

## What the app does not do

- It does not use Microsoft Graph.
- It does not use the Google Calendar API.
- It does not run a cloud backend.
- It does not upload diagnostics automatically.
- It does not read email, contacts, files, SMS, or location.

## Removing data

To remove copied calendar events, delete the selected Google Calendar or delete
the copied events from that calendar.

To remove local settings and logs, clear the app's storage from Android app
settings or uninstall the app.
