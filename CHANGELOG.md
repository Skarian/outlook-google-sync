# Changelog

## 0.0.1

- First public release candidate.
- Prepared the repo for public review.
- Removed local APKs, logs, and signing keys from the source tree.
- Added privacy, support, security, release, and troubleshooting docs.
- Added release signing configuration through environment variables and GitHub
  Secrets.
- Added GitHub Actions for CI and tag-driven release builds.
- Separated debug builds from the release package ID with `.debug`.
- Added the polished main-screen layout with selected calendars, sync actions,
  and activity.
- Added preview, manual sync progress, hourly sync, and structured sync logs.
- Mirrored recurring Outlook meetings as individual Google Calendar events.
- Copied up to 30 participants into the mirrored event description.
- Kept completed meetings for history and removed canceled upcoming meetings.
- Added upload monitoring for local Google Calendar sync state.
