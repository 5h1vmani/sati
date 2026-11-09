# Sati

Sati is an Android app that rings a bell now and then during your waking hours. Each bell is a moment to come back to the present. The name is the Pali word for mindfulness.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="180" alt="The Bell tab: Sati is on, with the next bell time in large type and pause buttons">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="180" alt="A running sadhana: a countdown to the next bell inside a progress ring, with the stage name">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="180" alt="The Sadhana tab: saved sadhanas with Start buttons, and the sadhana bell settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width="180" alt="The sadhana editor: a name, two 30-minute stages with labels, and a settling time">
</p>
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width="180" alt="The Log tab, Today view: a summary card, and each practice in a row with dots for the last 7 days">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width="180" alt="Logging a practice: minutes and seconds fields, focus buttons from 1 to 10, and a Not done button">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width="180" alt="Manage practices: each practice with its measure, target and repeat schedule, and a handle to drag it">
</p>
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width="180" alt="The 12 weeks view: two sentences about the trend, and a row of 12 weekly squares for each practice">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width="180" alt="The history of one practice: a summary card and a month calendar of done and missed days">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" width="180" alt="The weekly charts of one practice: minutes per week and average focus over 12 weeks">
</p>

## Features

- Random rhythm, so the mind does not learn to ignore the bell, or a fixed rhythm on the clock.
- An interval from 15 minutes to 2 hours.
- Separate waking hours for weekdays and weekends.
- Pause for one or two hours. The bell starts again by itself.
- On silent, the phone vibrates instead. During Do Not Disturb or a phone call, Sati stays quiet.
- A Test bell button rings a real bell 10 seconds later, so you can check what your phone does with it.
- The built-in bell or your own sound, with its own volume.
- A Quick Settings tile to turn the bell on or off.
- It keeps ringing after a restart, even before the first unlock.

Sadhana is a timer for a timed practice, such as breathing exercises, chanting or yoga.

- Split a practice into up to 10 stages, each with an optional label.
- The bell rings once at the start, twice when a stage changes and three times at the end, so you can tell them apart with your eyes closed.
- Sadhana bells ring through Do Not Disturb and silent mode, at your alarm volume, with their own volume slider. If Do Not Disturb blocks alarms, they play at your media volume instead.
- An optional settling time before the start bell, and a vibrate-only mode for shared rooms.
- A lock-screen countdown to the next bell, with a Pause button.
- The hourly bell stays quiet while a sadhana runs.

The practice log records every practice, not only the timed ones.

- Keep a list of practices, each counted as a duration (minutes and seconds), cycles, done, percent or a scale of 1 to 10. Durations and cycles can have a daily target. Drag practices into order, or let one repeat every 2 to 7 days.
- Today shows each practice and its progress. A done practice takes one tap, and a cross button marks any due practice not done. Other entries take two taps. A tap on a logged row edits its entry.
- A focus score from 1 to 10 is optional for each practice. A switch in the practice turns it on or off.
- Each row on Today shows the last 7 days as dots, and two short sentences sum up the week. There is no streak counter.
- The 12 weeks view shows every practice week by week, and names a practice that dropped or rose in the last 4 weeks.
- A practice's screen has a month calendar, a 30-day count, the weakest weekday, weekly totals and the weekly focus or score.
- Add entries for past days, so paper records can be typed in. Any entry can be edited or deleted, with Undo.
- Export the log and the saved sadhanas to one ZIP file, and import it back. Import also reads a hand-made CSV with the columns date, practice, value and focus in percent.
- The log tab reminds you to export after 30 days, because the export is the only backup.

Sati has no internet permission. It collects nothing. [PRIVACY.md](docs/PRIVACY.md) says what Sati stores, and [PERMISSIONS.md](docs/PERMISSIONS.md) says why it needs each permission. [EXPORT_FORMAT.md](docs/EXPORT_FORMAT.md) describes the export file, so other tools can read your data.

## Install

Sati needs Android 12 (API 31) or later.

Download the APK from the [latest release](https://github.com/5h1vmani/sati/releases/latest).

To get updates automatically, use [Obtainium](https://github.com/ImranR98/Obtainium):

1. Install Obtainium.
2. In Obtainium, tap Add App and enter `https://github.com/5h1vmani/sati`.
3. Obtainium installs Sati and tells you when a new version is out.

Android asks once whether Obtainium may install apps. Allow it.

Every release is signed with the same key. Its SHA-256 certificate fingerprint is:

```
6C:F8:5B:0E:54:C9:1A:E1:84:64:E9:B3:C9:EB:15:C6:9B:CB:0C:5D:4D:85:CF:F8:2F:AF:CC:E6:68:DC:9D:3C
```

## Verify a release

You can check that a release APK was built from the source code at its tag.

GitHub Actions does this for every new release. The [Verify release](https://github.com/5h1vmani/sati/actions/workflows/verify-release.yml) workflow rebuilds the release from its tag. It then checks that the signature from the published APK fits the new build. F-Droid runs the same check.

To check it yourself, you need JDK 21, the Android SDK and [apksigcopier](https://github.com/obfusk/apksigcopier). Replace `v1.3.0` with the release you want to check.

1. Check out the tag with `git checkout v1.3.0`.
2. Build the unsigned APK with `./gradlew assembleRelease`.
3. Download the APK from the release page.
4. Run `apksigcopier compare sati-1.3.0.apk --unsigned app/build/outputs/apk/release/app-release-unsigned.apk`.

The command fails with an error if the APKs do not match.

## Build

The build needs JDK 17 or later and the Android SDK.

```bash
./gradlew testDebugUnitTest assembleRelease
```

This builds an unsigned release APK, which is how F-Droid builds it. The maintainer signs official releases with `tools/release.sh`. That script reads the key from outside the repo and its password from the macOS Keychain.

## Project layout

- `app/src/main/java/com/padakanti/sati/Scheduler.kt` picks the next bell time. It has no Android code and is unit-tested.
- `Sadhana.kt` holds the timer's stages, its bell timeline and a running session. It has no Android code and is unit-tested.
- `BellAction.kt` decides whether a bell plays, vibrates or stays quiet. `HourField.kt` and `TimeText.kt` hold hour editing and the words for times. All three are unit-tested.
- `BellController.kt` and `SadhanaController.kt` are the one place each that change the hourly bell and the timer.
- `Alarms.kt` keeps one alarm per slot: the next hourly bell, and the next sadhana bell.
- `PhoneState.kt` reads Do Not Disturb, the ringer mode and the volumes. `Ringer.kt` plays the bell or vibrates.
- `Notifier.kt` shows the sadhana countdown on the lock screen.
- `log/` holds the practice log. `LogDb.kt` is the Room database. `LogController.kt` is the one place that writes it. The day rule, Today, the analytics, the CSV and ZIP formats and the import rules have no Android code and are unit-tested.
- `SadhanaCheck.kt` decides whether a sadhana is safe to run and valid to save or import.
- `ui/` holds the Bell, Sadhana and Log tabs, the editors and the theme.
- `tools/lucide_to_vector.py` converts Lucide icons into Android vector drawables.
- `tools/contrast.py` checks that every colour pair in the theme meets WCAG AA contrast.
- `tools/release.sh` builds and signs a release APK.
- `tools/repro_check.sh` checks that the release build is reproducible.
- `tools/check_permissions.py` checks that `docs/PERMISSIONS.md` explains every permission in the manifest, and that no new component is exported.
- `docs/ROADMAP.md` holds the plan for the next versions. `docs/DECISIONS.md` records the decisions and their reasons.

## How it is made

Sati's code is written with Claude, an AI coding tool made by Anthropic. Every commit says so in a `Co-Authored-By` line. Each release is tested on a real phone before it is published. [PROCESS.md](docs/PROCESS.md) explains who decides what, and which checks guard each change and each release.

## Contributing

Bug reports and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first. Report security problems privately, as [SECURITY.md](SECURITY.md) explains.

## Credits

- Icons: [Lucide](https://lucide.dev), ISC license; a few icons derive from Feather, MIT license. Full texts are in [NOTICE](NOTICE).
- Bell sound: a ghanta (Tibetan hand bell) recorded by [the_very_Real_Horst](https://freesound.org/people/the_very_Real_Horst/sounds/274316/), CC0, via [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:274316_the-very-real-horst_ghanta-leather-mallet-2015-05-16.wav). Trimmed to one strike and faded out.

## License

Sati is free software under the GNU General Public License, version 3 or any later version. See [LICENSE](LICENSE).
