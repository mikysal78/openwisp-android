# openwisp-android

🇬🇧 English · [🇮🇹 Italiano](README.it.md)

An Android app to manage an [OpenWISP](https://openwisp.org) server from the
phone, through the REST API of OpenWISP: devices and their status, actions on
the routers, Wi-Fi users and sessions, notifications in the background.

It is a small, independent app born for the OpenWISP instance of NINUX
Basilicata. It works with any OpenWISP server; it is not an official OpenWISP
project.

## 🧪 Become a tester

The app is young and we would like it to be tested on as many OpenWISP
servers and phones as possible. It is coming to **Google Play** too, and
Google asks for a closed test with registered testers before publishing it.

- **Try it**: install the APK from the
  [releases](https://github.com/mikysal78/openwisp-android/releases) and use
  it with your OpenWISP.
- **Register as a tester** for the Google Play version: write to
  [admin@ninux-nnxx.it](mailto:admin@ninux-nnxx.it?subject=openwisp-android%20tester)
  with the Google account you use on the phone. The link to the testers group
  will appear here as soon as it is open.
- **Report** problems and ideas in the
  [issues](https://github.com/mikysal78/openwisp-android/issues) (security
  issues privately, see [SECURITY.md](SECURITY.md)).

## Features

- **Devices**: list with monitoring status (online, problem, critical), search
  by name, IP, MAC or model, filters by status, devices with problems first.
- **Device page**: management and last IP, model, firmware, uptime, load,
  memory, disk, configuration status; Wi-Fi interfaces with their clients;
  the monitoring charts (ping, RTT, packet loss, Wi-Fi clients, traffic, CPU,
  memory, disk) for 1, 3, 7 or 30 days.
- **Actions**, each with a confirmation: reboot, shell command (output shown
  when the command ends), firmware upgrade with the firmware upgrader (newer
  builds of the same category with an image of the same type), **LuCI** of
  the router in the browser and **netconsole** logs.
- **Wi-Fi** (openwisp-radius): users online, recent sessions with router,
  IP, MAC, duration and traffic; Wi-Fi users can be blocked and unblocked
  (staff and superusers cannot be blocked from the app).
- **Notifications**: the list of OpenWISP notifications, mark as read; a
  check every 15 minutes shows the new ones as Android notifications, and
  tapping one opens the device.
- **App lock** with fingerprint or screen lock, English and Italian, light and
  dark theme (Material You colors on Android 12+).

## Requirements

- Android 8.0 (API 26) or later.
- An OpenWISP server on **HTTPS** with a certificate from a public CA, and a
  user with access to the admin (staff). The app can do exactly what this
  user can do in the OpenWISP admin.
- The app checks which modules the server has and shows only what is there:
  without openwisp-monitoring the devices have no status and charts, without
  openwisp-firmware-upgrader there is no firmware section, without
  openwisp-radius there is no Wi-Fi tab.
- Optional, for the LuCI and Netconsole buttons:
  [openwisp-luci](https://github.com/mikysal78/openwisp-luci) 0.3.0 or later
  and [openwisp-netconsole](https://github.com/mikysal78/openwisp-netconsole)
  0.2.0 or later, with their `api_urls` included under `api/v1/`. Without them
  the buttons are not shown.

## Installation

Download the APK from the
[releases](https://github.com/mikysal78/openwisp-android/releases) and open it
on the phone (allow the installation from that app when Android asks).

Every release is signed with the same key; the SHA-256 fingerprint of the
signing certificate is:

```
d2:88:0d:23:b5:75:8a:4b:60:57:bf:b4:d2:8f:0e:df:36:cd:81:0a:97:2d:5e:58:65:29:c6:b5:a3:16:4d:fe
```

You can check it with `apksigner verify --print-certs openwisp-android-*.apk`,
and the file with `SHA256SUMS` in the release.

With adb:

```bash
adb install openwisp-android-0.1.0.apk
```

## Use

Enter the host name of the server (e.g. `openwisp.example.com`), your
username and password. The password is sent once to
`/api/v1/users/token/` to get the API token and is never stored.

## Security

- **Token**: OpenWISP gives one API token per user. The app stores it
  encrypted with AES-GCM and a key of the Android Keystore that cannot be
  exported; app data is excluded from backups and device transfers.
- **Signing out** removes the token from the phone but does not revoke it on
  the server, because other tools may use the same token of the same user
  (for example a CI that uploads firmware). To revoke it, delete it in the
  OpenWISP admin (Auth Token).
- **Network**: HTTPS only, cleartext traffic disabled, only the system CAs
  (certificates added by the user are not trusted), redirects are not
  followed, and the token is sent only to the configured server (pagination
  links pointing elsewhere are ignored).
- **Actions**: reboot, commands, upgrades and blocking users always ask for a
  confirmation. Commands run as root on the router: the app shows the command
  again before running it.
- **App lock**: on by default when the phone has a screen lock; it asks again
  after two minutes in the background.
- **Intents**: the only exported component is the launcher activity; device
  ids received from notifications are accepted only as UUIDs, and API paths
  never contain `.` or `..` segments.
- **Notifications** do not use Google or other push services: the phone asks
  the OpenWISP server, with the token, every 15 minutes.

Report vulnerabilities privately: see [SECURITY.md](SECURITY.md).

## Development

Requirements: JDK 17 or later and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest lintDebug   # tests and lint
./gradlew assembleDebug                 # app/build/outputs/apk/debug/
```

Release build: create `keystore.properties` (outside git) with `storeFile`,
`storePassword`, `keyAlias` and `keyPassword`, then `./gradlew assembleRelease`.

The client of the API is in `app/src/main/java/.../data/Api.kt`, the screens
in `ui/`, the background check of the notifications in
`work/NotificationWorker.kt`.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). The OpenWISP logo used as the app
icon comes from [openwisp-utils](https://github.com/openwisp/openwisp-utils)
(BSD-3-Clause); OpenWISP is a project of its community, and this app is not
affiliated with it.
