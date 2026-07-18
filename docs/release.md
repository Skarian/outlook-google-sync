# Release process

This file is for maintainers. Users should install APKs from GitHub Releases
once releases exist.

## Versioning

Update the version in [app/build.gradle](../app/build.gradle):

```gradle
versionCode 2
versionName "0.0.2"
```

Then update [CHANGELOG.md](../CHANGELOG.md).

## Build a debug APK

```sh
./gradlew clean assembleDebug
```

Debug builds use `applicationIdSuffix ".debug"` and should not replace a release
install.

## GitHub release build

GitHub Actions builds releases when a `v*` tag is pushed. The workflow expects
these repository secrets:

```text
OGS_RELEASE_STORE_BASE64
OGS_RELEASE_STORE_PASSWORD
OGS_RELEASE_KEY_ALIAS
OGS_RELEASE_KEY_PASSWORD
```

`OGS_RELEASE_STORE_BASE64` is the release keystore encoded as one base64 string.
On Linux:

```sh
base64 -w 0 /absolute/path/to/release.jks
```

On macOS:

```sh
base64 -i /absolute/path/to/release.jks
```

Create a release by pushing a tag:

```sh
git tag v0.0.2
git push origin main
git push origin v0.0.2
```

The workflow builds a signed release APK, writes a SHA-256 file, and attaches
both files to the GitHub Release.

## Build a signed release APK locally

Create a release keystore outside the repo. Do not commit it.

Set the signing environment variables:

```sh
export OGS_RELEASE_STORE_FILE=/absolute/path/to/release.jks
export OGS_RELEASE_STORE_PASSWORD=...
export OGS_RELEASE_KEY_ALIAS=...
export OGS_RELEASE_KEY_PASSWORD=...
```

Build:

```sh
./gradlew clean assembleRelease
```

The signed APK is written under:

```text
app/build/outputs/apk/release/
```

Record the SHA-256 hash before publishing:

```sh
sha256sum app/build/outputs/apk/release/*.apk
```

## Test before publishing

- Install the release APK on a phone that has Outlook and Google Calendar sync
  set up.
- Confirm calendar permission is requested.
- Select at least one Outlook calendar.
- Select a dedicated Google Calendar.
- Run `Preview Changes` and check the counts.
- Run `Sync Now`.
- Confirm copied events only appear in the selected Google Calendar.
- Confirm a completed meeting remains if it later disappears from Outlook.
- Confirm a future canceled meeting is removed.
- Turn on hourly sync and confirm a scheduled run appears in the sync log.
- Export the sync log and confirm the saved text contains structured entries
  only, not raw debug log content, stack traces, or internal calendar IDs.

## Publish manually

- Attach the signed release APK to a GitHub Release.
- Include the SHA-256 hash in the release notes.
- Copy the relevant entries from [CHANGELOG.md](../CHANGELOG.md).
- Do not upload debug APKs, keystores, local logs, or screenshots that expose
  private calendar data.
