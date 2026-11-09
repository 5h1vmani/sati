# Decisions

This file records decisions. Entries are only ever added. A decision is never edited. A newer entry replaces it and says which entry it replaces.

The first 14 entries moved here from `docs/ROADMAP.md` on 7 November 2026, with their text unchanged. The roadmap gave the decision and its reason together, so each entry keeps them together.

## Sadhana timer, version 1.2.0

### D-01: Name of the timer

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Name.** The timer is called "Sadhana". The two tabs are "Bell" and "Sadhana".

### D-02: Spelling of the label

Date decided: 5 November 2026

Decision and reason, as the roadmap gives them:

**Spelling.** The label is written "Sadhana", with no accent marks, to match "Sati". Decided on 5 November 2026.

### D-03: Stages

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Stages.** A sadhana is a list of 1 to 10 stages. Each stage has a length in minutes and an optional label, such as "Breathing". A "Split evenly" button divides a total length into 2, 3 or 4 equal stages.

### D-04: Strike counts

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Strike counts.** The bell sound plays a different number of times for each event. The user can tell what happened without opening their eyes.

| Event | Strikes |
|---|---|
| Start | 1 |
| Stage change | 2 |
| End | 3 |

Strikes are about 4 seconds apart. All strikes use the chosen bell sound.

### D-05: Do Not Disturb

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Do Not Disturb.** Sadhana bells ring during Do Not Disturb and on silent mode. They play on the alarm channel for this reason.

### D-06: Volume

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Volume.** Sadhana bells follow the phone's alarm volume. They have their own volume slider, stored apart from the hourly bell's volume, with a button to test the level. The slider can only go up to the phone's alarm volume. The app never changes the phone's own volume settings.

### D-07: Zero alarm volume

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Zero alarm volume.** If the phone's alarm volume is zero, the app shows a warning before the sadhana starts. During the sadhana, each bell vibrates instead of playing.

### D-08: Alarms turned off in Do Not Disturb

Date decided: Date not recorded. The phone test that found the problem was on 5 November 2026.

Decision and reason, as the roadmap gives them:

**Alarms turned off in Do Not Disturb.** Some people block alarms in their Do Not Disturb settings. Then the bells play on the media channel, if Do Not Disturb allows media and the media volume is not zero. If neither can be heard, the Sadhana tab and the running screen show a notice with a button that opens the Do Not Disturb settings. Sati never changes Do Not Disturb itself. Adding Sati as a Do Not Disturb exception would not help, because exceptions cover notifications and the bells are played as sound. Found in the phone test on 5 November 2026, on a phone with alarms blocked in Do Not Disturb.

### D-09: Phone calls

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Phone calls.** During a call, a sadhana bell vibrates instead of playing.

### D-10: Hourly bell during a sadhana

Date decided: Date not recorded

Decision and reason, as the roadmap gives them:

**Hourly bell.** The hourly bell is muted while a sadhana runs. It resumes when the sadhana ends.

## Technical direction

The roadmap says: Decided on 5 November 2026.

### D-11: Kotlin only

Date decided: 5 November 2026

Decision and reason, as the roadmap gives them:

**Kotlin only.** Sati has no server, so it has no backend. A Rust core would add a second language, the Android NDK, native libraries for each processor type and a harder reproducible build for F-Droid. Sati does no heavy computation, so Rust would bring no benefit.

### D-12: Pure logic stays free of Android code

Date decided: 5 November 2026

Decision and reason, as the roadmap gives them:

**Pure logic stays free of Android code.** The timer model, the bell decisions, the time text and the analytics import nothing from `android.*`. This makes them testable on the Mac.

### D-13: An iOS version would use Kotlin Multiplatform

Date decided: 5 November 2026

Decision and reason, as the roadmap gives them:

**An iOS version, if it comes,** would use Kotlin Multiplatform. The pure logic would move to a shared module. Some work comes with that move:
- The pure files use `java.time`, which exists only on the JVM. They would move to kotlinx-datetime.
- `SadhanaJson.kt` uses org.json, a JVM library that iOS lacks. It would move to kotlinx.serialization.
- Room supports iOS from version 2.7, with its bundled SQLite driver and suspend functions. The log in 1.3.0 uses only those parts, so it can move unchanged.
- Compose Multiplatform can share the screens. The screens use Android types such as `Context`, so this needs some changes.
- The alarms, sound, notifications, tile and shortcuts would be rewritten for iOS in any case.

### D-14: The project is not converted now

Date decided: 5 November 2026

Decision and reason, as the roadmap gives them:

The project is not converted now, because nobody uses an iOS version yet.

### D-15: The Test bell ships as 1.4.0, and the later plans move one version

Date decided: 7 November 2026

The Test bell button is a new feature, so the release with it is a MINOR release, 1.4.0. It ships now with the 1-second vibration, because the old vibration went unnoticed on a real phone. Waiting for the three planned 1.4.0 features would have held back that fix for weeks. So the timer link, the evening reminder and the launcher shortcuts move to 1.5.0. Analytics move to 1.6.0, and translations move to 1.7.0.

### D-16: A vibrating bell is one vibration of 1 second per strike

Date decided: 7 November 2026

The first pattern was two pulses of 0.3 seconds. Android ran it in full on the Pixel 3 XL, but the maintainer did not feel it. Three pulses of 0.5 seconds felt too long and unusual. One vibration of 1.5 seconds also felt too long. One vibration of 1 second felt right. A sadhana bell keeps its count of strikes, because the count tells the start, a stage change and the end apart.

### D-17: CI runs once per pull request, without Scorecard or library updates from Dependabot

Date decided: 9 November 2026

CI ran on every push and on every pull request, so each pull request ran it twice. Pushes now run it only on `main`. OpenSSF Scorecard scored the repo setup every week, and nobody acted on the score, so it is removed. Dependabot no longer proposes Gradle library updates. Each update can change the release APK, so it needs the reproducibility check and a phone check. Libraries are updated on purpose, once per release cycle. Dependabot still proposes updates to the GitHub Actions in CI, because those touch CI only.
