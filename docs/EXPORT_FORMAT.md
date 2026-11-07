# Export and import format

This format has no version number yet. It may change between Sati versions. Each change will be listed in the release notes.

This file describes what Sati writes when the user exports the practice log, and what Sati accepts when the user imports. The code is in `app/src/main/java/com/padakanti/sati/log/` (`LogZip.kt`, `LogExport.kt`, `LogImport.kt`, `Csv.kt`) and in `app/src/main/java/com/padakanti/sati/SadhanaJson.kt`. A change to that code needs a matching change to this file.

## The ZIP file

The export is one ZIP file. Its name is `sati-log-yyyy-mm-dd.zip`, where the date is the log day of the export. The user chooses where Sati saves it.

The ZIP holds three files, in this order, with no folders:

| File | Content |
|---|---|
| `practices.csv` | The practices and their archive periods |
| `entries.csv` | The logged entries |
| `sadhanas.json` | The saved sadhanas, with their stages |

All three files are UTF-8 text.

## CSV rules

- A field is wrapped in double quotes when it contains a comma, a double quote or a line break. A double quote inside a field is written twice.
- Sati writes each row with the line ending CR LF, including the last row.
- Sati reads a line ending of LF, CR LF or CR. It ignores a byte order mark at the start. It skips blank lines and rows with only blank fields.
- A quote that is never closed, or a character after a closing quote, is an error.
- A spreadsheet runs a cell that starts with `=`, `+`, `-` or `@` as a formula. So Sati puts one apostrophe in front of some names and notes. These are the ones that start with `=`, `+`, `-`, `@`, a tab, a carriage return or an apostrophe. On import, Sati removes exactly one leading apostrophe from the `name`, `practice` and `note` columns of an exported file.
- A date is written `yyyy-mm-dd`, for example `2026-11-05`.
- Length limits on this page count UTF-16 code units, not characters. Most letters count as 1 unit. Many emoji count as 2.
- A day ends at 3 AM local time. A sitting at 1 AM counts for the day before. The `date`, `created` and `schedule_start` columns hold these days.

## practices.csv

The first row is this header:

`id,name,measure,target,created,archived,archive_periods,ask_focus,every_days,schedule_start`

| Column | Type | Meaning |
|---|---|---|
| `id` | text | 1 to 64 characters: ASCII letters, digits, `-` and `_`. Unique in the file. |
| `name` | text | 1 to 60 characters after trimming. Unique in the file, ignoring case. |
| `measure` | text | One of `duration`, `count`, `done`, `percent`, `scale`. |
| `target` | whole number or empty | Only `duration` and `count` practices have a target. A duration target is in seconds, from 1 to 86400. A count target is from 1 to 99999. |
| `created` | date | The day the practice was created. |
| `archived` | `true` or `false` | Whether the practice is archived now. |
| `archive_periods` | text or empty | Periods joined by `;`. Each period is `from/to`, both dates. An open period has an empty `to`, for example `2026-12-01/`. |
| `ask_focus` | `true` or `false` | Whether the entry form asks for a focus score. |
| `every_days` | whole number | The practice is due every 1 to 7 days. |
| `schedule_start` | date | The first due day. |

Example:

```
id,name,measure,target,created,archived,archive_periods,ask_focus,every_days,schedule_start
p-1,Kriya Yoga,duration,1800,2026-11-05,false,,true,1,2026-11-05
p-2,"Hatha, morning",done,,2026-11-05,true,2026-11-20/2026-11-25;2026-12-01/,false,2,2026-11-05
```

### Older practices.csv files

Import also reads two earlier headers. Both are the header above with the last columns cut off.

- Without `every_days` and `schedule_start`: the practice repeats every day and starts on its `created` day.
- Without `ask_focus`, `every_days` and `schedule_start`: the focus switch takes the default for the measure. The default is on for `duration` and `count`, and off for the others. The practice also repeats every day and starts on its `created` day.
- An older file may write the measure `minutes`. Import reads it as `duration`, and multiplies the target and the values of its entries by 60.

## entries.csv

The first row is this header:

`id,practice_id,practice,date,value,focus,note,created_at`

| Column | Type | Meaning |
|---|---|---|
| `id` | text or empty | Same rules as the practice `id`. |
| `practice_id` | text or empty | The `id` of the practice. Required when `id` is not empty. |
| `practice` | text | The practice name. Sati uses it only when `id` is empty. |
| `date` | date | The log day. It may not be after today. |
| `value` | whole number | From 0 to 99999, and limited by the measure (next table). 0 means not done. |
| `focus` | whole number or empty | A focus score from 1 to 10. An entry with value 0 may not have one. |
| `note` | text or empty | At most 1000 characters. New lines are allowed. |
| `created_at` | whole number or empty | Milliseconds since 1 January 1970 UTC. If empty, Sati uses the time of the import. |

The value depends on the measure of the practice:

| Measure | Value |
|---|---|
| `duration` | Seconds, 0 to 86400 |
| `count` | Number of cycles, 0 to 99999 |
| `done` | 1 for done, 0 for not done |
| `percent` | 0 to 100 |
| `scale` | 0 to 10, where 0 means not done |

Example:

```
id,practice_id,practice,date,value,focus,note,created_at
e-1,p-1,Kriya Yoga,2026-11-06,1500,8,"Calm, then restless",1793941800000
e-2,p-2,"Hatha, morning",2026-11-06,1,,,1793945400000
```

## sadhanas.json

A JSON list of sadhanas. The order of the keys does not matter.

| Key | Type | Meaning |
|---|---|---|
| `id` | text | Not blank, at most 64 characters. |
| `name` | text | Not blank, at most 60 characters. |
| `settleSec` | whole number | The settling time before the start bell. One of 0, 10, 30, 60, 120. If missing, 0. |
| `vibrateOnly` | `true` or `false` | If missing, `false`. |
| `stages` | list | 1 to 10 stages. Each stage has `minutes`, a whole number from 1 to 240, and `label`, text of at most 60 characters. If `label` is missing, it is empty. |

Example:

```json
[{"id":"starter-60","name":"30 + 30","settleSec":0,"vibrateOnly":false,"stages":[{"minutes":30,"label":"Breathing"},{"minutes":30,"label":""}]}]
```

## Import

Import accepts two kinds of file. If the file starts with the bytes `PK` followed by 3 and 4, Sati reads it as an export ZIP. Any other file is read as a hand-made CSV, in UTF-8.

### Checks before anything changes

Sati checks the whole file first. The import then runs as one database transaction. A file with an error changes nothing, and Sati reports the file and the line of the first error.

- The file may not be larger than 16 MiB (16 \* 1024 \* 1024 bytes). A ZIP may not unpack to more than 16 MiB in all, and may not have more than 10 entries.
- A ZIP must contain `practices.csv` and `entries.csv`. A folder name in front of a file name is ignored. `sadhanas.json` is optional.
- The header row of each CSV file must match one of the headers above, ignoring case and spaces around the names. Every row must have as many fields as the header.
- A file may have at most 200 practices and at most 100000 entries. After the merge, the log may hold at most 200 practices.
- A name may not be empty, longer than 60 characters, or contain control or format characters. The line separator U+2028 and the paragraph separator U+2029 are refused too. The zero-width joiner and non-joiner are allowed. Sati saves names in NFC form.
- A note may not contain control or format characters, except new lines. It may not contain U+2028 or U+2029.
- `sadhanas.json` must be a JSON list. If it is not, the file fails. Sati skips a single sadhana and counts it in these cases. It cannot be read, it repeats an earlier `id` in the file, it has hidden characters, or it breaks a limit above. The others are kept. Sati saves the names and labels in NFC form.

### How the merge works

- A practice whose `id` already exists in the log is kept as it is. Its `measure` in the file must be the same, or the import fails.
- A practice whose name already exists in the log, ignoring case, is joined to that practice. Its `measure` must be the same, or the import fails. The existing practice is not changed.
- Any other practice is added, with its archive periods.
- An entry with an `id` is skipped when that `id` is already in the log, or earlier in the file. Its `practice_id` must name a practice in the file or in the log, or the import fails.
- An entry with an empty `id` names its practice by `practice`. It is skipped when the log already has an entry with the same practice, day and value. Sati gives it a new id.
- Each value and focus is checked against the measure of its practice.
- Importing the same file twice changes nothing the second time.
- Sadhanas merge by `id`. A sadhana whose `id` exists stays as it is. A new one is added while the list has fewer than 50 sadhanas. Sati saves the sadhanas right after the database transaction. They are not part of it.

### Hand-made CSV

A hand-made CSV has four columns: `date`, `practice`, `value` and `focus`. A first row whose first field is `date` is a header and is skipped. Every row must have exactly 4 fields.

| Column | Rule |
|---|---|
| `date` | `yyyy-mm-dd`. It may not be after today. |
| `practice` | A practice name. |
| `value` | See the next table. |
| `focus` | Empty, or a percent from 0 to 100. A trailing `%` is allowed. Sati turns it into a score from 1 to 10: it adds 5, divides by 10, rounds down and keeps the result from 1 to 10. |

The value depends on the measure of the practice:

| Measure | Accepted value |
|---|---|
| `duration` | A plain number is minutes. `1:30` is minutes and seconds, and the seconds may be at most 59. `45s` is seconds. The total may not be more than 86400 seconds. |
| `percent` | A whole number, with or without a trailing `%`. |
| `count`, `scale` | A whole number. |
| `done` | A whole number. Any value of 1 or more means done. 0 means not done. |

Rules for these rows:

- These rows have no `id`, and Sati gives each a new one. The `created_at` time is the time of the import.
- One wrong row stops the whole import.
- A row with the same practice, day and value as an entry in the log is skipped.
- A practice name that is not in the log creates a new practice. The app asks the user for its measure. Without a choice, the measure is `duration`. At most 50 new names are allowed in one file.
- A new practice has no target, repeats every day and is created on the day of its earliest row. Its focus switch is on if any row has a focus, or if the measure is `duration` or `count`.
- Sati does not remove a leading apostrophe from a practice name in a hand-made CSV. The apostrophe stays part of the name.

Example:

```
date,practice,value,focus
2026-10-01,Kriya Yoga,1:30,80
2026-10-02,Kriya Yoga,45s,
2026-10-02,Pranayama,20,
```
