# Security

Please do not open public issues that contain private calendar data.

This app reads and writes local Android calendar data. Reports can easily expose
meeting titles, participant email addresses, account names, or internal company
details if logs or screenshots are posted without review.

## Reporting a security issue

If this repo has GitHub private vulnerability reporting enabled, use that.
Otherwise, open a minimal public issue that says you have a security report and
avoid posting sensitive details. A maintainer can then choose a private channel.

## Signing keys

Release signing keys must stay outside the repository. The repo ignores
keystores, APKs, AABs, and local build output.

Debug builds use a `.debug` package ID suffix. They should not be distributed as
the public release package.
