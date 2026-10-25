# Changelog

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
