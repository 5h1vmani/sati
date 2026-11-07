# Privacy

Sati collects nothing. It has no account and no server. Sati does not ask for the INTERNET permission, so it cannot send data over the network. See [PERMISSIONS.md](PERMISSIONS.md).

## What Sati stores

| Data | Where it is stored |
|---|---|
| Settings: bell on or off, interval, waking hours, pause time, volumes, the saved sadhanas, the running sadhana, the time of the last log export | SharedPreferences file `bell`, in device-protected storage (`Store.kt`) |
| The practice log: practices, entries and archive periods | Room database `log.db`, in normal app storage (`log/LogDb.kt`) |
| A bell sound the user chooses | The file `custom_sound`, in device-protected storage (`Sounds.kt`) |

An entry in the log holds a day, a value, an optional focus score and an optional note. Sati keeps the chosen sound as a copy, so it still works if the original file moves.

Sati uses no DataStore. Sati writes no other files that the code shows.

## What leaves the phone

Data leaves the phone only when the user starts an export. The user picks where the ZIP file is saved, through the Android file picker. Sati does not send the file anywhere. See [EXPORT_FORMAT.md](EXPORT_FORMAT.md).

An import reads a file that the user picks. It does not go online.

## Android backup

The manifest sets `android:allowBackup="false"`, so Android does not back up Sati's data to the cloud. The manifest has no `dataExtractionRules` attribute. On Android 12 and later, Android may therefore still copy Sati's data during a transfer to a new phone. Do not rely on that transfer. The export is the only backup that Sati controls.

## Analytics and network libraries

`app/build.gradle.kts` and `gradle/libs.versions.toml` list no analytics library, no crash reporting library and no network library. The libraries are Compose (activity, UI, Material 3), Room, and org.json for tests. The Room compiler runs through KSP. The instrumented tests also use AndroidX Test, room-testing and kotlinx-serialization.

## Uninstalling

Uninstalling Sati removes the settings, the log database and the chosen sound, because Android deletes an app's private storage when the app is removed. A ZIP file that the user saved with an export stays where the user saved it.
