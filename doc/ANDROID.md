# Android app and widgets

The Android project lives in `android/` alongside its Rails backend. Steps connects
only to `https://steps.ecorp.dev`. It requires Android 12 or newer. The dashboard
uses the existing website in a browser-backed tab; Google authentication stays in
the browser. The two home-screen widgets use the native `/mobile` API.

## Prepare the server

1. Set `MINIMALIST_ALLOWED_EMAILS` in the server's `.env` to the Google account
   emails you want to allow, separated by commas. For example:
   `MINIMALIST_ALLOWED_EMAILS=you@example.com,friend@example.com`.
   Matching ignores case and surrounding spaces. An empty list denies everyone.
2. Deploy the Rails changes and run `RAILS_ENV=production bundle exec rails db:prepare`
   using [DEPLOYMENT.md](DEPLOYMENT.md). The migration adds device authorizations
   and submission receipts without changing existing tasks. Recreate the web
   container when changing environment settings; `bin/deploy` does this.
3. Confirm that an approved Google account can sign in and an unapproved account
   cannot. Only then remove the Cloudflare Access requirement for `steps.ecorp.dev`,
   including any separate `/mobile/*` application or matching wildcard application.
   The website, OAuth callback and mobile API must reach Rails without an Access
   login page or service token. Cloudflare DNS, proxy and Tunnel can remain in use.
   Keep HTTPS and the existing origin firewall/proxy configuration.
4. Retain the existing Google OAuth web client and authorized callback URL,
   `https://steps.ecorp.dev/users/auth/google_oauth2/callback`.
   Google client credentials stay on the server. No Android OAuth client or
   Cloudflare service token is required. Revoke any old service tokens created
   solely for these phones once the switch is complete.

Rails restricts Google sign-in to approved verified addresses and checks account
approval on browser and mobile requests. Removing an email and recreating the web
container blocks that account's existing sessions and phone credentials on their
next request. It does not delete tasks or erase an offline phone's cache.

The app starts a short-lived pairing request, opens Google sign-in in the browser,
and asks the user to approve the matching code. Widgets then authenticate with
that phone's revocable Rails credential. Pairing endpoints are public and rate
limited; task endpoints require a valid credential for an approved account.
HTTP redirects never receive forwarded device credentials.

Google sign-in uses the browser as required by
[Google's secure browser policy](https://developers.google.com/identity/protocols/oauth2/policies#use-secure-browsers).

## Release signing and GitHub Actions

The `Android APK` workflow runs manually on the default branch. It tests, lints,
builds and verifies a signed release APK, then retains the downloadable artifact
for 30 days. It does not deploy Rails, configure Cloudflare, or publish to a store.
The workflow file must reach the default branch before GitHub offers **Run workflow**.

Create a persistent signing key on a trusted computer with Java's `keytool`:

```sh
mkdir -p "$HOME/.local/share/steps-signing"
chmod 700 "$HOME/.local/share/steps-signing"
keytool -genkeypair -keystore "$HOME/.local/share/steps-signing/steps-release.jks" \
  -storetype JKS -alias steps -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$HOME/.local/share/steps-signing/steps-release.jks"
```

`keytool` prompts for passwords and certificate details. Keep an offline backup of
both the keystore and passwords. Losing the key prevents updates to existing
installations. Reuse the key for every build; do not generate one in Actions.

Add these repository Actions secrets through GitHub's settings or `gh secret set`:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the complete `.jks` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `steps` if using the command above |
| `ANDROID_KEY_PASSWORD` | Password for that key, often the same as the keystore password |

To upload the keystore without printing it, run from the repository:

```sh
base64 < "$HOME/.local/share/steps-signing/steps-release.jks" | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

For the remaining commands, enter each value at the prompt. Use GitHub's secret
settings if your installed CLI does not hide interactive input. Never put passwords
in shell command arguments or share them in chat.

Run **Actions → Android APK → Run workflow** on the default branch. Download the
`steps-android-N` artifact from the completed run, unzip it, and install
`app-release.apk`. Android may ask you to allow installation from the browser or
file manager you use. Future workflow runs increase `versionCode` using the workflow
run number. Rerunning an existing run rebuilds the same version. Keep this workflow's
run-number sequence when moving repositories, or set a higher version code before
building updates.

## First use

1. Open Steps and choose **Connect with Google**. Sign in with an approved Google
   account in the browser, compare the connection code with the app, and approve.
2. Return to Steps. It checks for approval while open and downloads tags and starred tasks.
3. Choose **Add Starred widget** and **Add quick-add widget**, accepting the launcher's
   prompts. Alternatively add them through the launcher's widget picker.

The app opens the web dashboard on subsequent launches. Close the browser tab to
return to native settings. Opening the dashboard does not share browser cookies
with the widget API.

The Starred widget groups tasks by tag in profile order and preserves the server's
within-tag date ordering. Tap a name to open its location in the web Starred view;
tap its star to unstar it. Binning and editing remain in the web app. To keep Android's
widget payload within its IPC size limit, it displays the first 100 active tasks
and explicitly directs you to the full list if there are more.

Quick-add opens a small window with the keyboard, task name, tag, optional calendar
date, and **Star for today**. It remembers the last successful tag and star setting.
It clears text/date after creation and preserves dismissed or failed drafts. An
unconfirmed submission locks its details until retry or discard so retries cannot
accidentally create a second task. A validation rejection unlocks the draft for
correction. Discarding a draft does not bin a task that already reached the server.

## Refresh, privacy and disconnection

Native changes refresh widgets. Other devices' changes appear on manual refresh or
Android's periodic background refresh. Android can defer work during sleep, battery
savings, force-stop, or while offline. A best-effort midnight job and every widget
render filter cached stars against server-provided expiration instants. Visible
clearing is not guaranteed at precisely midnight; server reads always enforce expiry.
The cache timestamp identifies the last successful fetch. Dates use the profile's
timezone, including daylight-saving changes.

Credentials, cached tasks and drafts are encrypted with an Android Keystore key and
excluded from cloud backup and device transfer. Widgets intentionally show task text
on the home screen. Device-held secrets are not impossible to extract from a compromised
phone, which is why each installation has independently revocable credentials.

**Disconnect this phone** revokes its Rails token and clears local credentials,
cache and drafts. If the network is unavailable, the app offers local clearing with
an explicit reminder to revoke the phone later in web Profile. Website sign-out
only ends the browser session. **Profile → Connected phones → Disconnect** revokes
server access immediately; an offline phone can still display its cache until it
next contacts the server. A Rails unauthorized response clears local account data.
Network or proxy failures preserve the cache for retry.

## Local builds and verification

Install Java 17 and the Android SDK, set `ANDROID_HOME`, and install platform 36
and build tools 36.0.0. The Gradle wrapper pins Gradle 8.13 with a SHA-256 checksum.

```sh
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # with a running emulator/device
```

The debug APK has application ID `dev.ecorp.steps.debug`, so it can coexist with the
release APK. Production uses `dev.ecorp.steps`. Release builds use the four signing
environment variables shown in `app/build.gradle`; the workflow supplies them.

Run the Rails checks from AGENTS.md against an isolated MySQL test database as well.
`test/integration/mobile_workspace_test.rb` covers ownership, pairing, revocation,
expiry, and retry safety. Android tests cover date/expiry calculations and encrypted
state/widget rendering on a device. Before distributing, test on the actual Nothing
and Pixel phones: setup through real Google sign-in, widget placement/resizing,
quick-add keyboard and date selection, tag deletion, airplane-mode retries, process
death with a draft, revocation, and a midnight refresh with an unfinished draft.

## Mobile protocol

- `POST /mobile/authorization` accepts a device `name` and returns a random 256-bit
  `device_token`, a 12-character display code, and a ten-minute expiry.
- `GET /mobile/authorization` with that bearer token returns `pending` or `authorized`.
  Poll no more than once every five seconds, only while setup is visible.
- The authenticated browser explicitly approves the display code with a CSRF-protected
  POST. Approval binds the token digest to that user. Only digests are stored server-side;
  the original secret becomes usable only after approval. Expired requests cannot be approved.
- `GET /mobile/workspace` returns owned tags and active starred tasks with expiration
  instants, profile timezone and fetch timestamp.
- `POST /mobile/tasks` accepts `task`, boolean `starred`, and a UUID `request_id`.
  The same request ID/payload returns the original task; changed payloads return 409.
  Receipts survive binning, so retries after deletion return 410 instead of recreating.
- `DELETE /mobile/tasks/:id/star` idempotently unstars an owned task.
- `DELETE /mobile/device` revokes the caller's credential.

Pairing limits use bounded per-process memory. For a multi-worker deployment needing
a global limit, additionally rate-limit these endpoints at your reverse proxy or edge service. The mobile
controllers accept bearer credentials only; Rails cookies and legacy user API tokens
do not authorize native requests. Native APIs provide no task deletion endpoint.
