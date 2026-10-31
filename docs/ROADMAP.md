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
| 1.2.0 | 3 | Mid Dec 2026 | Sadhana timer, quiet during phone calls | Planned |
| 1.3.0 | 4 | Early Jan 2027 | Telugu and Hindi translations | Planned |
| — | — | Mid Jan 2027 | Check that F-Droid can rebuild the same APK | Planned |
| — | — | After 30 Jan 2027 | Resubmit to F-Droid | Planned |

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
- **Alarms turned off in Do Not Disturb.** Some people block alarms in their Do Not Disturb settings. The app shows a warning before the sadhana starts if this is the case.
- **Phone calls.** During a call, a sadhana bell vibrates instead of playing.
- **Hourly bell.** The hourly bell is muted while a sadhana runs. It resumes when the sadhana ends.

### Other features

- **Settling time.** An optional pause of 0 to 2 minutes comes before the start bell. It gives time to put the phone down.
- **Saved sadhanas.** Saved setups show as cards with a Start button. Users name them. Three come with the app: 20 minutes in one stage, 60 minutes as 30 + 30, and 45 minutes with a bell every 15 minutes.
- **Vibrate only.** Each sadhana can be set to vibrate instead of ringing, for shared rooms.
- **Running screen.** The screen is dark and shows the time left, the stage label and a thin progress ring. The screen may turn off, and the bells do not depend on it.
- **Notification.** A lock-screen notification shows a live countdown with Pause and Stop buttons.
- **Safe stop.** Stop asks for confirmation, so a stray tap does not end the sadhana.
- **Summary.** When the sadhana ends, the app shows how long it lasted.
- **Reliability.** Each bell is an exact alarm based on the phone's uptime clock, so a clock change does not move it. After a restart, the remaining bells are set again from the stored start time.

### Also in 1.2.0

- **Quiet during phone calls.** The hourly bell makes no sound and no vibration during a call. Today it vibrates.

### Open question

- Should the label be written "Sadhana" or "Sādhana"? Recommendation: write "Sadhana". It is easier to type and search for, and it matches "Sati", which also has no accent marks.

## 1.3.0: Translations

- Telugu and Hindi versions of every string in the app and the store listing.
- A native speaker must review each translation before release.

## Later ideas

These are not scheduled yet.

- **Open sadhana.** No end time, an optional bell every N minutes, and the total time shown when it stops.
- **Launcher shortcuts.** A long press on the Sati icon starts a saved sadhana.
- **Practice log.** Date, length and total minutes per week. It will have no streaks and no badges, because those turn practice into a score.

## F-Droid resubmission

F-Droid closed the first request on 30 October 2026, because its policy asks for 3 months of active development. See [merge request 54974](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/54974).

- The resubmission uses reproducible builds. F-Droid builds the app from source, checks that the result matches the developer-signed APK, and then ships the developer's signature. Users can then move between GitHub, F-Droid and other sources without reinstalling.
- In mid January, a test checks that the build is reproducible. Some Android build outputs can differ between builds, so this needs checking early.
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
2. The build is tested on a real phone.
3. The changelog file for the new versionCode is written.
4. versionCode and versionName are raised.
5. The release commit is tagged and pushed.
6. The signed APK is uploaded to GitHub Releases.
7. Testers are told about the new version.
8. This file is updated.

## To check

- Google announced that apps installed from outside Google Play will need a verified developer. It starts in a few countries in 2026 and widens in 2027. Its effect on GitHub, Obtainium and F-Droid installs in India needs checking before testers start and before January.
