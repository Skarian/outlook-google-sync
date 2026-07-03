# Reference review

These projects shaped the publishing pass.

## Projects checked

- [SufficientlySecure/calendar-import-export](https://github.com/SufficientlySecure/calendar-import-export)
  at `1ee2ba8`
- [bitfireAT/icsx5](https://github.com/bitfireAT/icsx5) at `e642701`
- [Etar-Group/Etar-Calendar](https://github.com/Etar-Group/Etar-Calendar)
  at `db1dff8`
- [bitfireAT/davx5-ose](https://github.com/bitfireAT/davx5-ose),
  reviewed by subagent for README and support patterns

Reference clones live outside this repo under:

```text
~/.skills/reference-module/
```

## What carried over

Calendar Import-Export keeps a narrow scope, separate changelog, release notes,
and license. This repo now follows that split instead of using the README as a
scratchpad.

ICSx5 explains one-way calendar ingestion clearly. This repo uses the same kind
of scope language: Android-only, one-way, local calendar storage, no cloud API.

Etar explains Android calendar providers in plain terms. This repo now explains
that Outlook and Google must both expose calendars through Android's local
calendar storage.

DAVx5 has a stronger support and security surface. This repo now separates
support requests, security reports, and privacy notes.

## What did not carry over

- Play Store, F-Droid, and donation badges. The app is not published there.
- Website-first docs. This repo does not have a website.
- Heavy contribution process. This is still a small utility.
- Old Android SDK Manager build instructions. The repo uses Gradle wrapper
  commands instead.
