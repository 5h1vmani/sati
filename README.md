# Sati

Sati is an Android app that rings a bell now and then during your waking hours. Each bell is a moment to come back to the present. The name is the Pali word for mindfulness.

## Features

- Random rhythm, so the mind does not learn to ignore the bell, or a fixed rhythm on the clock.
- An interval from 15 minutes to 2 hours.
- Separate waking hours for weekdays and weekends.
- Pause for one or two hours. The bell starts again by itself.
- On silent or Do Not Disturb, the phone vibrates instead.
- The built-in bell or your own sound, with its own volume.
- A Quick Settings tile to turn the bell on or off.
- It keeps ringing after a restart, even before the first unlock.

Sati has no internet permission. It collects nothing.

## Requirements

Android 12 (API 31) or later.

## Build

The build needs JDK 17 or later and the Android SDK.

```bash
./gradlew testDebugUnitTest assembleRelease
```

Without a `keystore.properties` file, the release APK is unsigned. The file is ignored by git.

## Project layout

- `app/src/main/java/com/padakanti/sati/Scheduler.kt` picks the next bell time. It has no Android code and is unit-tested.
- `Alarms.kt` keeps exactly one exact alarm set for the next bell.
- `Ringer.kt` plays the bell, or vibrates when the phone is silenced.
- `ui/` holds the Compose screen and the theme.
- `tools/lucide_to_vector.py` converts Lucide icons into Android vector drawables.
- `tools/contrast.py` checks that every colour pair in the theme meets WCAG AA contrast.

## Credits

- Icons: [Lucide](https://lucide.dev), ISC license.
- Bell sound: a ghanta (Tibetan hand bell) recorded by [the_very_Real_Horst](https://freesound.org/people/the_very_Real_Horst/sounds/274316/), CC0, via [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:274316_the-very-real-horst_ghanta-leather-mallet-2015-05-16.wav). Trimmed to one strike and faded out.

## License

Sati is free software under the GNU General Public License, version 3 or any later version. See [LICENSE](LICENSE).
