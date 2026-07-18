# Support

Open a GitHub issue for reproducible bugs. For setup questions, include enough
detail for someone else to follow the same path.

## Include this

- Android version and device model.
- Outlook Google Sync version.
- Outlook for Android version, if the issue involves missing Outlook events.
- Google Calendar version, if the issue involves upload or calendar visibility.
- Whether Outlook calendar sync is enabled in Outlook for Android.
- Whether the selected Google Calendar is visible and syncing in Google
  Calendar on the phone.
- What you expected to happen.
- What happened instead.
- The smallest set of steps that reproduces the problem.

## Logs

The app's sync log is useful, but review it before posting. `Export Sync Log`
saves recent structured sync history and excludes the private raw debug log,
raw JSON, and stack traces. Exported logs can still contain timestamps, counts,
Android error messages, and sometimes account names from Android calendar
labels.

Do not post screenshots or logs that show private meeting titles, descriptions,
participant names, participant emails, company names, or account names you do
not want public.

## Useful troubleshooting checks

Read [docs/troubleshooting.md](docs/troubleshooting.md) before filing an issue.
Many problems come from Android calendar sync settings, not from this app.
