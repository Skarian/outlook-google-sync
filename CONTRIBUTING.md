# Contributing

Small, focused changes are easiest to review.

Before opening a pull request:

- build the app with `./gradlew assembleDebug`;
- keep private calendar data out of screenshots, logs, commits, and tests;
- update docs when behavior or user-facing language changes;
- keep the app Android-only and one-way unless the project scope changes first.

For larger behavior changes, open an issue first. Calendar sync bugs can be hard
to reason about after the fact, so describe the source calendar, selected Google
Calendar, sync range, and expected event lifecycle.

Do not commit APKs, AABs, keystores, app-private logs, or generated build
output.
