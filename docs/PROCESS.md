# How Sati is made

Sati's code is written with Claude, an AI coding tool made by Anthropic. This page explains who decides what, and which checks stand between the code and your phone. Most of the checks below are public, and you can run them yourself.

## Who does what

The maintainer, Shiv Padakanti, makes every decision about the app.

- He decides what Sati does and what it does not do. Each decision is written down with its reason in [DECISIONS.md](DECISIONS.md). The date is there too, where it was recorded.
- He plans each version in the [roadmap](ROADMAP.md) before work starts.
- He merges each change through a pull request.
- He signs every release with a key that is kept outside the repo.

Claude writes the code, the tests and most of the text. Every commit that Claude wrote says so in a `Co-Authored-By` line. You can check this with `git log`.

## Checks on every change

GitHub Actions runs these checks on every pull request and every push to `main`. A pull request cannot be merged into `main` until they pass.

- **Unit tests.** The app logic lives in files with no Android code, so it can be tested on any computer. These files decide when a bell rings, whether it plays, vibrates or stays quiet, and how the log counts a day. They have unit tests in `app/src/test`.
- **Android lint.** It finds common Android mistakes in the code and the resources.
- **Colour contrast.** `tools/contrast.py` checks that every colour pair in the theme meets WCAG AA. WCAG is the main web standard for accessibility.
- **Permissions.** `tools/check_permissions.py` checks that [PERMISSIONS.md](PERMISSIONS.md) explains every permission the app asks for. It also checks that no new component is exported to other apps.
- **Release build.** CI builds an unsigned release APK.

## Audits

Plans and larger changes get an audit before they ship. An audit is a separate review in a fresh context, with numbered questions and file and line evidence for each answer. The git history shows the results. For example, commit `04192d5` fixes four problems that an audit found in the bell service.

## Checks on every release

- **Reproducible build.** Anyone can rebuild a release from its git tag and get the same APK. The only difference is the signature. `tools/repro_check.sh` checks this before each release.
- **Public verification.** When a release is published, GitHub Actions rebuilds it from its tag. It then checks that the signature from the published APK fits the new build. The result is public on the repo's Actions page.
- **Supply chain.** Every action in CI is pinned to a commit SHA. Dependabot proposes updates to these actions once a month. Libraries are updated on purpose, once per release cycle, because an update can change the APK.

## What is not automated

`tools/emulator_check.sh` runs on Android 12, 16 and 17 emulators before each release, not in CI. On each one it does three things:

- It runs the instrumented tests. An instrumented test is a test that runs inside Android itself. `app/src/androidTest` holds `MigrationTest`, which checks the database migrations.
- It turns the hourly bell on and taps "Test bell", with Sati in the background. The bell must ring, and Android must not mute it.
- It restarts the emulator. The hourly bell's alarm must be set again.

Vibration, notifications and the lock screen have no automated tests. Each release is tested on a real phone for these.

## Why this page exists

Some app stores and users are wary of code written by AI. That concern is fair. The answer here is evidence: written decisions, tests, audits and builds that anyone can check.
