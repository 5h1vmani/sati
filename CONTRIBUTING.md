# Contributing to Sati

Thank you for helping with Sati. This file explains how to build the app, the rules for code and text, and how to send a change.

## Build and test

You need JDK 17 or later and the Android SDK. Run these commands from the root of the repository:

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
python3 tools/contrast.py
python3 tools/check_permissions.py
```

- The Gradle command runs the unit tests and the lint checks, and builds an unsigned release APK.
- `tools/contrast.py` checks that every colour pair in the theme meets WCAG AA contrast.
- `tools/check_permissions.py` checks that `docs/PERMISSIONS.md` lists the same permissions as the manifest. It also checks that no new component is exported.

## Rules for code

- Pure logic files import nothing from `android.*`. They have unit tests. [docs/DECISIONS.md](docs/DECISIONS.md) explains why.
- All screen text goes into string resources. Write whole sentences, with plural forms where a number appears.
- A change to the permissions in `app/src/main/AndroidManifest.xml` needs a matching change to [docs/PERMISSIONS.md](docs/PERMISSIONS.md).
- A change to the export or import code needs a matching change to [docs/EXPORT_FORMAT.md](docs/EXPORT_FORMAT.md).

## Rules for text

Every text in the repo follows one writing rule. This covers code comments, documents, commit messages and pull request text. In short:

- Write plain English at CEFR level B2. Readers include people whose English is not strong.
- Write one idea per sentence. Most sentences have 8 to 16 words. None has more than 25.
- Give every sentence a verb. Use common words, except for technical terms.
- Use every word in its literal sense. Do not use metaphors or idioms. Do not use em dashes.
- Use the exact technical term that the code uses, and use the same word every time. Name a thing only by an identifier you have seen in the code.

## How to send a change

1. For anything larger than a small fix, open an issue first and describe the change.
2. Make one topic per pull request.
3. CI must pass.

## Changes written with AI tools

Contributions written with AI tools are welcome. The contributor must:

- say so, in the commit with a `Co-Authored-By` line or in the pull request;
- read every line of the change;
- test the change.

Sati's own code is written with Claude. See [docs/PROCESS.md](docs/PROCESS.md).

## Licence

Sati is licensed under GPL-3.0-or-later. Contributions are accepted under the same licence. There is no contributor licence agreement (CLA).

## Releases

Only the maintainer signs and publishes releases.

## Security problems

Report a security problem as described in [SECURITY.md](SECURITY.md). Do not open a public issue for it.

## Translations

Translations are not open yet. They are planned for version 1.7.0. See the [roadmap](docs/ROADMAP.md).
