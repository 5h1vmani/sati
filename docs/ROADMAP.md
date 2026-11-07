# Sati roadmap

This file is the plan for the next releases of Sati. It records what each version contains, the decisions already made, and the questions still open. Update it when a decision changes or a version ships.

## How versions are numbered

Sati has two version numbers, and each has its own rules.

- **versionCode** is a whole number that only machines read. It goes up by exactly 1 for every published build. A number is never reused, even for a build only testers received.
- **versionName** is the label people see. It has three parts, MAJOR.MINOR.PATCH.
  - PATCH goes up for bug fixes only.
  - MINOR goes up when a feature is added or the app behaves differently.
  - MAJOR goes up when something people rely on breaks, for example settings that do not carry over.
- **The git tag** matches the versionName with a "v" in front, for example `v1.1.0`. F-Droid finds new releases by reading tags. A tag never moves after it is pushed.
- **The changelog** is one file per versionCode, in `fastlane/metadata/android/en-US/changelogs/`.
- **The bump** happens in the last commit before a release, not when work starts.

## Release plan

A release comes out about every 3 to 4 weeks. This gives testers time to try each version. It also shows the steady development that F-Droid asks for.

| Version | versionCode | Target | Contents | Status |
|---|---|---|---|---|
| 1.0 | 1 | 30 Oct 2026 | First release | Done |
| 1.1.0 | 2 | 31 Oct 2026 | Quiet during Do Not Disturb, tests for the bell decision, instant notices, release signing | Done |
| 1.2.0 | 3 | 5 Nov 2026 | Sadhana timer, quiet during phone calls, code clean-up | Done |
| — | — | 5 Nov 2026 | Check that F-Droid can rebuild the same APK, on 1.2.0 | Done, one fix |
| 1.3.0 | 4 | Early Dec 2026 | Practice log, Today, export and import | Done, 6 Nov 2026 |
| — | — | Early Dec 2026 | Repeat the rebuild check on 1.3.0, the first build with a database | Done, 6 Nov 2026, identical |
| 1.4.0 | 5 | Late Dec 2026 | Timer link, evening reminder, launcher shortcuts | Planned |
| 1.5.0 | 6 | Late Jan 2027 | Analytics and streaks | Planned |
| — | — | After 30 Jan 2027 | Resubmit to F-Droid | Planned |
| 1.6.0 | 7 | Feb 2027 | Telugu and Hindi translations | Planned |

## 1.1.0

1. **Quiet during Do Not Disturb.** During Do Not Disturb, the hourly bell makes no sound and no vibration. On silent or vibrate mode, it still vibrates. This is built and tested on a Pixel 3 XL.
2. **Tests for the bell decision.** The choice between "stay quiet", "vibrate" and "play" lives in `BellAction`, which has no Android code. Unit tests cover every case. This is built.
3. **Instant notices.** The app listens for changes to Do Not Disturb and the ringer mode. The notices on screen then update at once, instead of within 15 seconds. This is built.
4. **Release signing.** The release key was created on 31 October 2026. Its SHA-256 certificate fingerprint is `6C:F8:5B:0E:54:C9:1A:E1:84:64:E9:B3:C9:EB:15:C6:9B:CB:0C:5D:4D:85:CF:F8:2F:AF:CC:E6:68:DC:9D:3C`. `tools/release.sh` signs with it. Every APK from this version on is signed with it. If the key is lost, nobody can ever publish an update under the same app ID, so it must be backed up off the Mac before the first release.
5. **First signed GitHub Release.** The signed APK is uploaded to GitHub Releases. Testers start with this version.

The version label is now "1.1.0".

## 1.2.0: Sadhana timer

The timer is for a timed practice, such as breathing exercises, chanting or yoga. It is a separate tab next to the hourly bell.

### Decisions made

- **Name.** The timer is called "Sadhana". The two tabs are "Bell" and "Sadhana".
- **Spelling.** The label is written "Sadhana", with no accent marks, to match "Sati". Decided on 5 November 2026.
- **Stages.** A sadhana is a list of 1 to 10 stages. Each stage has a length in minutes and an optional label, such as "Breathing". A "Split evenly" button divides a total length into 2, 3 or 4 equal stages.
- **Strike counts.** The bell sound plays a different number of times for each event. The user can tell what happened without opening their eyes.

  | Event | Strikes |
  |---|---|
  | Start | 1 |
  | Stage change | 2 |
  | End | 3 |

  Strikes are about 4 seconds apart. All strikes use the chosen bell sound.
- **Do Not Disturb.** Sadhana bells ring during Do Not Disturb and on silent mode. They play on the alarm channel for this reason.
- **Volume.** Sadhana bells follow the phone's alarm volume. They have their own volume slider, stored apart from the hourly bell's volume, with a button to test the level. The slider can only go up to the phone's alarm volume. The app never changes the phone's own volume settings.
- **Zero alarm volume.** If the phone's alarm volume is zero, the app shows a warning before the sadhana starts. During the sadhana, each bell vibrates instead of playing.
- **Alarms turned off in Do Not Disturb.** Some people block alarms in their Do Not Disturb settings. Then the bells play on the media channel, if Do Not Disturb allows media and the media volume is not zero. If neither can be heard, the Sadhana tab and the running screen show a notice with a button that opens the Do Not Disturb settings. Sati never changes Do Not Disturb itself. Adding Sati as a Do Not Disturb exception would not help, because exceptions cover notifications and the bells are played as sound. Found in the phone test on 5 November 2026, on a phone with alarms blocked in Do Not Disturb.
- **Phone calls.** During a call, a sadhana bell vibrates instead of playing.
- **Hourly bell.** The hourly bell is muted while a sadhana runs. It resumes when the sadhana ends.

### Other features

- **Settling time.** An optional pause of 0 to 2 minutes comes before the start bell. It gives time to put the phone down.
- **Saved sadhanas.** Saved setups show as cards with a Start button. Users name them. Three come with the app: 20 minutes in one stage, 60 minutes as 30 + 30, and 45 minutes with a bell every 15 minutes.
- **Vibrate only.** Each sadhana can be set to vibrate instead of ringing, for shared rooms.
- **Running screen.** The screen is dark and shows the time left, the stage label and a thin progress ring. The screen may turn off, and the bells do not depend on it.
- **Notification.** A lock-screen notification counts down to the next bell and has a Pause or Resume button. It has no Stop button, so a stray tap with closed eyes cannot end the sadhana.
- **Safe stop.** End is in the app only, and it asks for confirmation.
- **Summary.** When the sadhana ends, the app shows how long it lasted.
- **Reliability.** Each bell is an alarm-clock alarm, which Android delivers on time even in deep sleep. Times are stored as UTC clock time, so a time-zone change does not move them. A manual clock change does move them; the uptime clock was dropped because it resets on restart. After a restart, the remaining bells are set again from the stored start time, and a sadhana whose time ran out while the phone was off ends quietly.

### Also in 1.2.0

- **Quiet during phone calls.** The hourly bell makes no sound and no vibration during a call.
- **Code clean-up.** Changes from an audit for single source of truth, repetition and single responsibility:
  - `BellController` and `SadhanaController` are the one place each that change the bell and the timer. The screen, the tile, the notification and the alarms all call them.
  - `Ringer` takes the channel, volume and strike count as inputs. `PhoneState` reads the phone's sound state.
  - `Alarms` keeps one alarm per slot, so a sadhana bell never replaces the hourly bell.
  - Screen text that names a limit, such as 30 seconds or 20 MB, is built from the limit itself.
  - `HourField`, `TimeText` and the sadhana timeline are pure Kotlin with unit tests. The test count went from 31 to 70.
  - The screen is split into a Bell tab, a Sadhana tab, the editor and shared components.
  - A sound import survives a screen rotation.


## Practice log plan

The practice log records every practice, not only the ones timed in Sati, such as Hatha Yoga and Kriya Yoga. It is a third tab, "Log", next to "Bell" and "Sadhana". It is built across three releases: 1.3.0, 1.4.0 and 1.5.0. Decided on 5 November 2026, then corrected the same day after two independent audits of the plan.

### Rules for all three releases

- **Text.** All new screen text goes into string resources from the start, as whole sentences with plural forms. Pure Kotlin functions return numbers and dates, never English sentences. This keeps the translation in 1.6.0 to new text only.
- **One owner.** `LogController` is the one place that writes the log. Today, the entry form, the timer link, import and delete all call it, as the screen calls `BellController` and `SadhanaController`.
- **Pure logic.** The day boundary, Today, the export format, the import rules and every analytics view are pure Kotlin functions with unit tests, like `Sadhana.kt`.

## 1.3.0: Practice log

### Rebuild check on 1.2.0

Done on 5 November 2026 with `apksigcopier compare`, the check F-Droid runs.

- The published 1.2.0 APK and a fresh build of the v1.2.0 tag differ in one file only, `META-INF/version-control-info.textproto`. AGP writes the git commit into it. The published APK records commit `b814805`, the commit before the release, because `tools/release.sh` builds before the release commit is made.
- The fix sets `vcsInfo.include = false` for release builds. Every other file was identical.
- 1.2.0 itself cannot be reproduced, so the F-Droid resubmission must start from 1.3.0 or later.
- `tools/repro_check.sh` builds the working tree in two different folders and checks that the APKs are identical. Given a signed APK, it also runs `apksigcopier compare`. It runs before every release.

### First step: prove the database

The log is planned as a Room database. Room is Android's standard library for SQLite. It needs KSP, a code generator, and the build has no KSP yet. Nobody has checked that KSP works with the built-in Kotlin of AGP 9.4.

- On a branch, Room and KSP are added with pinned versions, and the app is built twice from clean. The two APKs must be identical. Done on 5 November 2026: Room 2.8.5 and KSP 2.3.13 build with AGP 9.4's built-in Kotlin, and two builds in different folders were identical. The check runs again on the finished 1.3.0 build, because R8 removes Room while no code uses it.
- If either check fails, the log uses Android's built-in SQLite classes instead, with no code generator.
- Room's schema file is committed. The first change to the tables comes with a migration and a test on the phone.

### Practices

- The user keeps a list of practices, such as Sukha Kriya, AUM Chanting, Hatha Yoga and Kriya Yoga. A practice is expected every day, unless it repeats on a schedule.
- **Order.** In the practices list the user presses and holds a row, then drags it to a new place. The list scrolls when the row nears an edge. Screen reader users get Move up and Move down actions on each row. Today follows that order.
- **Repeat.** A practice can repeat every 2 to 7 days from a start day, which can be in the past or the future. It is due on the start day and then every N days. A due day that is missed does not move the schedule. The user can still log on a day that is not due. That entry counts, but it does not change the schedule. Today lists the due practices first. The others wait below "Not due today", with their next due day.
- Each practice has one measure: duration, a count of cycles, done, percent or scale. A duration is stored in seconds, up to one day, and the form has a Minutes field and a Seconds field. Percent is a value from 1 to 100. Scale is a score from 1 to 10. The measure can change only while the practice has no entries, because old entries would no longer match.
- **Not done.** Any practice can be marked not done. The mark is an entry with value 0 and no focus, for every measure. A done entry has value 1.
- A practice can have an optional daily target, such as 108 cycles or 30 minutes. Only durations and cycles have a target. The target changes only what the screen shows. Any entry counts as done, so 80 of 108 counts and still shows as 80 of 108.
- **Ask for focus.** Each practice has a switch for the focus score. It starts on for durations and cycles, and off for done, percent and scale. The user can change it. A practice that is switched off keeps the focus its old entries already have.
- **Archive.** An archived practice leaves Today and keeps its history. It can be brought back. Each archive and return is stored with its date, because the analytics in 1.5.0 skip the archived days.
- **Delete.** Deleting a practice also deletes its entries. The app first says how many entries will go and which sadhanas link to the practice, and it asks for confirmation.

### Entries

- An entry has a practice, a day, a value, an optional focus score and an optional note. Each entry has a unique ID.
- **Day.** The day is stored on the entry as a date when the entry is written. It is never worked out again later, so travel or a time-zone change does not move old entries. A new entry gets today. A day ends at 3 AM local time, so a sitting at 1 AM belongs to the day before. The 3 AM rule is fixed, not a setting.
- **Past days.** The entry form can choose any past day, so paper records can be typed in.
- **Time.** The moment of writing is stored for sorting only. The screen does not ask for a time.
- **Focus.** Focus is a score from 1 to 10, shown as a row of ten buttons. It is optional, and the form shows it only for practices that ask for focus. A not-done entry has no focus. Percentages from the paper notebook convert by dividing by 10 and rounding, with 1 as the lowest score.
- **Several entries a day.** For durations and cycles the values add up. For percent and scale the day's value is the average, rounded half up, never the sum. A not-done entry counts as 0 in that average. The day's focus is the average of the entries that have a score.
- **Edit and delete.** Any entry can be changed or deleted. A delete can be undone for a few seconds.

### Today

The Log tab opens on Today. A menu button at the right of the heading holds Manage practices, Export, Import and the date of the last export. Under the heading, a summary card shows the log day, "6 of 15 done" for the practices due today, and a bar of done, not done and left.

The practices are in one compact list with three sections: to do, logged today, and not due today. Each row has a round indicator: an empty ring, a ring filled toward the target, a filled check, or a red cross. Tapping a practice in the practices list opens its editor, where Archive and Delete are.

- A done practice that does not ask for focus is logged with one tap on its row. A cross button on every row that is due and has nothing logged marks it not done with one tap, and the snackbar offers Undo. A row that is not due has no cross button.
- A tap on a row that already has an entry opens today's newest entry to edit, whatever the measure. The edit form has an "Add another entry" button. The list button on each row opens all entries of the practice.
- The row shows a check for done, a red cross when it has only not-done entries, and an empty circle for nothing logged.
- A done practice that asks for focus opens the short form on a tap. A focus button saves it as done, and "No score" saves it as done without focus. A practice that does not ask for focus has a Done button.
- A percent practice has a number field. A scale practice has a row of ten buttons for its value. Both then show the focus buttons if the practice asks for focus. Today shows the day's value as "72%" or "7 of 10". An entry above 0 counts as done.
- Every form has an outlined "Not done" button. It saves value 0 with no focus. Editing a not-done entry opens the form as for a new entry, so it can become a real entry.
- For a duration, the form has a Minutes field and a Seconds field. Typing 30 in Minutes alone is 30 minutes. The values show as "45 s", "1 min 30 s" or "30 min".
- For cycles, a tap opens a short form. The value is filled in with the target, or with the last value if there is no target. A tap on a focus button saves the entry. A "No score" button saves it without focus. The note is hidden behind "Add note".
- The usual entry therefore takes two taps.

### Export and import

The app sets `allowBackup="false"` and has no internet permission, so the export is the only backup.

- **Export.** One ZIP file holds the practices, the entries and the saved sadhanas, including their stage links. The practices and entries are CSV files, so they open in a spreadsheet. The practices file ends with the columns ask_focus, every_days and schedule_start, and a duration is written as "duration" in seconds. Import also reads older files. Without the new columns, a practice gets the default focus switch for its measure, repeats every day and starts on the day it was created. An older file that says "minutes" is read as a duration, and its targets and values are multiplied by 60. The user chooses where the file is saved.
- **Import.** The whole file is checked before anything changes, and the import runs as one transaction. A file with an error changes nothing and the app says which line failed. The saved sadhanas live in the settings, not in the database, so they are written right after the transaction. If the app is killed between the two, a second import adds the missing sadhanas.
- **No duplicates.** Rows with an ID merge by that ID. A row whose ID already exists is skipped, so importing the same file twice changes nothing.
- **Paper records.** Import also reads a CSV made by hand with four columns: date, practice, value and focus in percent. These rows have no ID. A value may end in a percent sign for a percent practice. For a duration, a plain number is minutes, "1:30" is minutes and seconds, and "45s" is seconds. Every value is checked against the measure of its practice, and one wrong row stops the whole import. A value of 0 means not done. For a done practice, any value of 1 or more means done. A focus in a row is kept as it is. A row with the same practice, day and value as an existing entry is skipped. A practice name the app does not know creates a new practice, and the app asks for its measure.
- **Export reminder.** The menu shows when the last export was made. After 30 days the top of the tab asks for a new one. The first ask, before any export, is a note at the bottom of the list, so the rows do not jump after the first entry.

## 1.4.0: Timer link, reminder and shortcuts

### Timer link

- In the sadhana editor, each stage can be linked to a practice from the list. Only duration or done practices can be linked, because a stage knows only time.
- **Values.** A duration practice gets the seconds actually sat in that stage. Pauses do not count. A stage with no time logs nothing. A done practice is logged as done when its stage is reached.
- **Safe writing.** When a sadhana ends, `finish()` saves the stage results in device-protected storage, next to the summary. `finish()` can run at boot before the first unlock, when the log cannot be opened. The next time the app opens, `LogController` moves the results into the log. Each result has an ID, so it is never added twice. A new sadhana start does not delete results that have not moved yet.
- **Focus.** The summary asks for the focus of each new entry, and the user can skip it. The answer only updates the entries already in the log, so closing the app loses nothing.
- **Deleted or archived practices.** Deleting or archiving a practice removes its stage links. The confirmation names the sadhanas affected.
- **Saved data.** The new link field is read as optional, so sadhanas saved by 1.2.0 still load, and so does a sadhana running during the update. A unit test loads saved data in the 1.2.0 format. It needs `org.json:json` as a test dependency, because Android's own classes do not run in plain unit tests.

### Evening reminder

- One notification comes at a time the user chooses when today's list is not complete. It names what is left.
- It has its own alarm slot, its own notification channel at default importance, its own notification ID and its own request code. It never replaces the hourly bell, a sadhana bell or the sadhana notification.
- Notification permission is asked when the reminder is switched on, not only at the first sadhana.
- The receiver reads the log with `goAsync`. If the phone has not been unlocked since a restart, the reminder is skipped that day.
- `RescheduleReceiver` sets the reminder again after a restart, an update or a clock change.
- If a sadhana runs at the reminder time, the reminder waits and comes when the sadhana ends.
- A notification follows Do Not Disturb on its own, so no extra code is needed for it.

### Launcher shortcuts

- A long press on the Sati icon shows "Today" and the three sadhanas started most recently. "Log a practice" was merged into "Today", because both opened the same screen. The app stores when each sadhana was last started.
- Most launchers show 4 shortcuts. This is checked on the Pixel launcher only.
- A sadhana shortcut opens the app and starts the sadhana through the same path as the Start button. The zero-volume warning and the permission request then still apply. If a sadhana is already running, the shortcut opens the running screen instead.
- The shortcuts are updated when a sadhana is renamed or deleted.

## 1.5.0: Analytics

Analytics come after the log has collected about two months of real entries. The views are designed against that data, not against invented data.

### Definitions

- **Active day.** A day counts for a practice when the practice was active. It is active from the earlier of its creation and its first entry, except while it is archived.
- **Done.** A practice is done on a day when it has at least one entry above 0 on that day. A not-done entry counts as a missed day, even though it is an entry. A day with both marks counts as done. Percent and scale days count as done with any entry above 0. Their daily value is the average of the day's entries, and a not-done entry counts as 0 in trends.
- **Due days.** Streaks and missed days count only the days a practice is due. A not-done entry, or no entry, on a due day is a missed day. An entry on a day that is not due is extra. It neither breaks nor extends a streak.
- **Today.** Today never counts as missed. It counts as done once it has an entry.

### Views

- **Week strip.** Seven dots at the top of Today. Each dot shows the share of practices done that day, out of the practices active that day.
- **Calendar.** A month grid per practice marks each day the practice was done.
- **Streak.** The number of active days in a row that the practice was done, up to today. Archived days neither break the streak nor add to it.
- **Last 30 days.** "Done on N of M days", where M is the number of active days in the last 30. For a practice older than 30 days, M is 30.
- **Weekday pattern.** The share of days done for each weekday. It appears once the practice has 4 weeks of active days. One sentence names the weakest weekday only when it is at least 15 percentage points below the practice's average. If there is a tie, or every day is done, there is no sentence.
- **Focus trend.** The weekly average focus per practice over the last 12 weeks. It covers only practices that ask for focus. Each scored entry counts equally. A week with no scored entries shows a gap. Weekly averages are used because daily scores change too much to read.
- **Technical.** The database only loads the entries for a date range. Each definition above has unit tests for these edge cases.

### Cut after the audits

- **Focus patterns** by time of day and session length. With one person's fixed routine, most groups would never collect enough entries, and the entry time does not record when the practice happened.
- **Month review.** It repeated what the calendar, the streak and the 30-day count already show.

## 1.6.0: Translations

- About 100 English texts are written directly in the Kotlin code, and strings.xml holds only 2. These existing texts move into string resources first.
- Then come Telugu and Hindi versions of every string in the app and the store listing.
- A native speaker must review each translation before release.
- This was planned for 1.3.0. It moved after the log and analytics, so that their new text is translated once.

## Technical direction

Decided on 5 November 2026.

- **Kotlin only.** Sati has no server, so it has no backend. A Rust core would add a second language, the Android NDK, native libraries for each processor type and a harder reproducible build for F-Droid. Sati does no heavy computation, so Rust would bring no benefit.
- **Pure logic stays free of Android code.** The timer model, the bell decisions, the time text and the analytics import nothing from `android.*`. This makes them testable on the Mac.
- **An iOS version, if it comes,** would use Kotlin Multiplatform. The pure logic would move to a shared module. Some work comes with that move:
  - The pure files use `java.time`, which exists only on the JVM. They would move to kotlinx-datetime.
  - `SadhanaJson.kt` uses org.json, a JVM library that iOS lacks. It would move to kotlinx.serialization.
  - Room supports iOS from version 2.7, with its bundled SQLite driver and suspend functions. The log in 1.3.0 uses only those parts, so it can move unchanged.
  - Compose Multiplatform can share the screens. The screens use Android types such as `Context`, so this needs some changes.
  - The alarms, sound, notifications, tile and shortcuts would be rewritten for iOS in any case.
- The project is not converted now, because nobody uses an iOS version yet.

## Later ideas

These are not scheduled yet.

- **Open sadhana.** No end time, an optional bell every N minutes, and the total time shown when it stops.
- **Spoken stage names.** The phone says the stage label after the stage-change strikes.
- **Breath pacer, japa counter and tanpura drone.** Suggested on 5 November 2026 for breathing and chanting stages.

## F-Droid resubmission

F-Droid closed the first request on 30 October 2026, because its policy asks for 3 months of active development. See [merge request 54974](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/54974).

- The resubmission uses reproducible builds. F-Droid builds the app from source, checks that the result matches the developer-signed APK, and then ships the developer's signature. Users can then move between GitHub, F-Droid and other sources without reinstalling.
- A test checks that the build is reproducible: first on 1.2.0 in mid November, as a baseline, and again on 1.3.0, the first build with a database. Some Android build outputs can differ between builds. A failure found in late January would push the resubmission past 30 January.
- The metadata draft in `fdroid/` needs the newest version, its commit and the signing details before resubmission.

### AI-written code

Sati's code was written with Claude, an AI tool. Every commit says so in a `Co-Authored-By` line. The first merge request did not say so.

- F-Droid's Inclusion Policy says nothing about AI-written code, as checked on 31 October 2026.
- An open proposal would add a "VibeCoded" anti-feature for apps whose code is mostly written by AI. See [fdroid/admin#710](https://gitlab.com/fdroid/admin/-/work_items/710). An anti-feature is a warning label shown to users. It does not block the app.
- A related open proposal would tag every app that uses AI. See [fdroid/admin#641](https://gitlab.com/fdroid/admin/-/work_items/641).
- The resubmission states openly that the code is AI-written, and these proposals are checked again first.
- IzzyOnDroid is not a route. Its [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/) rejects apps whose code is fully or partly written by generative AI.

## Sharing with testers

Testers install from GitHub Releases, starting with the first signed version.

- The recommended route is Obtainium, a free app that installs and updates apps from GitHub. Testers add `https://github.com/5h1vmani/sati` in Obtainium.
- The simpler route is the APK link on the Releases page. That route gives no update alerts.
- Sati needs Android 12 or newer.

## Release checklist

Every release follows these steps in order.

1. Unit tests, lint and a release build pass.
1. `tools/repro_check.sh` passes.
2. The build is tested on a real phone.
3. The changelog file for the new versionCode is written.
4. versionCode and versionName are raised.
5. The release commit is tagged and pushed.
6. The signed APK is uploaded to GitHub Releases.
7. Testers are told about the new version.
8. This file is updated.

## To check

### Target Android 17

- Android 17 mutes sound from apps in the background unless a foreground service runs. Sati rings every bell in `BellService` for this, from 1.3.1 on.
- Apps that target Android 17 (API 37) also need a foreground service with "while-in-use" capability. A service started from an alarm usually lacks it.
- Google waives this only for sound with `USAGE_ALARM` from an app with the exact-alarm permission. The hourly bell uses `USAGE_NOTIFICATION_EVENT`, and sadhana bells can fall back to `USAGE_MEDIA`.
- Before targetSdk moves to 37, test both on an Android 17 emulator with `adb shell cmd audio set-enable-hardening enable`, which applies the API 37 rules to every app.

### Google developer verification

Checked on 5 November 2026 from Google's own pages, plus press where marked.

- Google requires apps on certified Android devices to come from verified developers. Enforcement began on 30 September 2026 in Brazil, Indonesia, Singapore and Thailand only. Google says it expands globally "in 2027" and gives no date for India.
- It covers devices on Android 7 and later, through Google Play services, so a Pixel 3 XL on Android 12 is in scope once India is included.
- Users can still install unregistered apps through ADB, or through an "advanced flow": a one-time setup with developer options, a restart, a one-day wait, then a PIN or fingerprint.
- Students and hobbyists can use a free limited account with no government ID. It allows a limited number of devices; Google's pages say 20 in one place and 50 in another.
- Google says only package names "never seen before on Android" can be registered. `com.padakanti.sati` is already public, so it is unknown whether it can be registered.
- F-Droid "unequivocally" advises developers against signing up for the program.
- Decision needed before India's date is announced: register Sati with Google, or leave it unregistered and rely on the advanced flow. Friends installing now are not affected.
