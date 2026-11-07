# Security policy

## Supported versions

Only the latest release of Sati gets security fixes. Please update before you report a problem.

## How to report a problem

Report security problems privately through [GitHub private vulnerability reporting](https://github.com/5h1vmani/sati/security/advisories/new). Do not open a public issue for a security problem.

Please include the Sati version, the Android version and the steps that show the problem.

## What happens next

- The maintainer aims to reply within one week.
- A confirmed problem is fixed in the next release. The release notes describe the fix.
- You are credited in the release notes, unless you ask not to be.

## What counts as a security problem

Sati does not ask for the INTERNET permission, so it cannot send data over the network. Security problems are still possible. Some examples follow.

- Another app can read Sati's data or start Sati's components without permission.
- An import file can crash Sati, corrupt the log or change data it should not change.
- An export file contains data the user did not choose to export.
- A release APK does not match the source code at its tag.

## Check a release

Every release is signed with the same key, and every release can be rebuilt from its tag. The README explains how to [verify a release](README.md#verify-a-release).
