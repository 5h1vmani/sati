# Instructions for AI coding agents

This file is for AI coding agents that work on Sati. Humans should read [CONTRIBUTING.md](CONTRIBUTING.md) first. This file only adds the rules an agent needs beyond it.

## Read first

- [CONTRIBUTING.md](CONTRIBUTING.md) explains the build, the tests and how to send a change.
- [docs/PROCESS.md](docs/PROCESS.md) explains how Sati is built with an AI tool and which checks guard it.
- [docs/DECISIONS.md](docs/DECISIONS.md) records decisions. Do not reopen a decision there without a new reason.
- [docs/ROADMAP.md](docs/ROADMAP.md) holds the plan, the version rules and the release checklist.

## Rules for code

- Pure logic files import nothing from `android.*`. Each one has unit tests in `app/src/test`.
- All screen text goes into string resources, as whole sentences with plural forms.
- A change to the manifest permissions needs a matching change to `docs/PERMISSIONS.md`. `tools/check_permissions.py` checks this in CI.
- A change to the export or import code needs a matching change to `docs/EXPORT_FORMAT.md`.
- Sati does not ask for the INTERNET permission. Do not add it, and do not add a library that needs the network.

## Rules for git and GitHub

- Work on a branch and open a pull request. Direct pushes to `main` are blocked.
- The CI job named `build` is a required check. Renaming it blocks every merge until the ruleset is updated.
- Never create, move or delete a `v*` tag. Never sign or publish a release. Only the maintainer does that.
- A commit subject says what changed in plain words. A reader must understand it without opening the diff.
- Every commit an agent writes ends with a `Co-Authored-By` line that names the tool.
- A new decision is a new entry at the end of `docs/DECISIONS.md`. Never edit an old entry.

## Writing rule

This rule binds every file, commit message and pull request text in this repo.

Write plain English at CEFR level B2. Readers include people whose English is not strong.

1. One idea per sentence. Most sentences are 8 to 16 words. None is over 25.
2. Every sentence has a verb. No fragments. No label-and-colon in place of a sentence.
3. Use common words for everything that is not a technical term.
4. Use every word in its literal sense. No metaphor, no idiom, no clever phrasing.
5. A parenthesis becomes a second sentence. A short citation is fine. No em dashes.
6. Technical terms are exact. Use the word the code, the vendor or the standard uses, and use the same word every time.
7. Name a thing only by the identifier you have seen in the code. If you have not seen it, say so instead of guessing.
8. Define a technical term in plain words once, the first time, if the reader may not know it.
