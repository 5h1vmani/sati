# Permissions

This file lists every permission that Sati asks for in `app/src/main/AndroidManifest.xml`. It also lists the receivers and services in that manifest. Sati does not ask for the INTERNET permission.

A script, `tools/check_permissions.py`, checks that this file lists the same permissions as the manifest. Change both together.

## Permissions

| Permission | What Sati uses it for | Used in |
|---|---|---|
| `android.permission.RECEIVE_BOOT_COMPLETED` | Sati sets its alarms again after the phone restarts. | `RescheduleReceiver.kt` |
| `android.permission.VIBRATE` | A bell vibrates the phone when it is set to vibrate instead of ringing. | `Ringer.kt` |
| `android.permission.WAKE_LOCK` | Sati keeps the processor awake while a bell rings. | `Ringer.kt`, `BellService.kt` |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Sati opens the Android screen where the user can allow background running. | `MainActivity.kt` |
| `android.permission.SCHEDULE_EXACT_ALARM` (`maxSdkVersion` 32) | Sati sets exact alarms for bells on Android 12. | `Alarms.kt` |
| `android.permission.USE_EXACT_ALARM` | Sati sets exact alarms for bells on Android 13 and later. | `Alarms.kt` |
| `android.permission.POST_NOTIFICATIONS` | Sati shows the sadhana countdown notification. On Android 13 and later it asks before the first sadhana. | `MainActivity.kt`, `Notifier.kt` |
| `android.permission.FOREGROUND_SERVICE` | Sati runs `BellService`, a foreground service, while a bell rings. | `BellService.kt` |
| `android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | The foreground service has the type `systemExempted`. | `BellService.kt` |

The manifest comments give these reasons:

- Android 12 grants `SCHEDULE_EXACT_ALARM` at install. Android 13 and later grant `USE_EXACT_ALARM` instead.
- Bells ring inside a short foreground service, because Android 17 mutes sound from apps in the background without one.
- The type `systemExempted` is allowed because the app holds an exact-alarm permission.

The code passes the type `systemExempted` only on Android 14 and later (`BellService.kt`). On those versions, Android grants `USE_EXACT_ALARM` at install.

The manifest also has a `<queries>` entry for the `AUTO_REVOKE_PERMISSIONS` action. It lets Sati check that the "unused apps" settings screen exists. Android 11 and later hide other apps' screens unless an app declares this. It is not a permission.

## Receivers

A receiver is a part of the app that Android calls when an event happens. None of Sati's receivers is exported, so no other app can send to them. All four are `directBootAware`. This means they can run after a restart, before the first unlock.

- `BellReceiver` receives the alarm for the hourly bell. It rings the bell and sets the next alarm.
- `SadhanaReceiver` receives the alarm for a sadhana bell and rings the bell that is due.
- `SadhanaActionReceiver` receives the Pause and Resume buttons on the sadhana notification.
- `RescheduleReceiver` has an intent filter for these system events: `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Android clears alarms on restart, and a clock or time zone change makes a set alarm wrong. On each event it sets the next bell and the next sadhana bell again.

## Services

- `BellService` is a foreground service of the type `systemExempted`. It is not exported and is `directBootAware`. It rings one bell, then stops itself.
- `BellTileService` is the Quick Settings tile that turns the bell on or off. It is exported, because the system must bind to it. Its `android:permission` attribute limits binding to the system. A long press on the tile opens `MainActivity`.
