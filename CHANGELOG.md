# Changelog

## 0.1.3

- First release for Google Play: store listing, graphics and screenshots in
  `fastlane/metadata/android`, privacy policy published with the guide.
- Removed the `FOREGROUND_SERVICE` permission added by WorkManager: the app
  never runs work in the foreground.

## 0.1.2

- Release workflow: read the signing certificate with the output of both
  apksigner 36 and 37. The 0.1.1 release stopped at that check and was not
  published; this release contains everything listed for 0.1.1.

## 0.1.1

- First release built and signed by GitHub Actions: APK, AAB for Google Play,
  `SHA256SUMS` and build provenance attestation.
- Library updates: androidx.fragment 1.9.1, androidx.work 2.12.0.
- README: how to build the APK and publish a release; testers register at
  app@ninux-nnxx.it.

## 0.1.0

First release.

- Devices with monitoring status, search and filters; device page with info,
  Wi-Fi interfaces and monitoring charts (1, 3, 7, 30 days).
- Actions with confirmation: reboot, shell commands, firmware upgrade, LuCI
  ([openwisp-luci](https://github.com/mikysal78/openwisp-luci) 0.3.1 or later)
  and netconsole logs
  ([openwisp-netconsole](https://github.com/mikysal78/openwisp-netconsole)
  0.2.0 or later).
- Wi-Fi (openwisp-radius): users online, recent sessions, block and unblock
  users.
- Notifications checked every 15 minutes, without push services.
- Only what the server has is shown: monitoring, firmware upgrader, radius,
  luci and netconsole are detected.
- Token encrypted with an Android Keystore key, HTTPS only, app lock with
  fingerprint or screen lock. English and Italian.
