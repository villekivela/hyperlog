# Backups

Your data can leave the phone and come back in two ways. Both carry everything the app stores.

| | Manual backup | Android Auto Backup |
| --- | --- | --- |
| What | One JSON file you save where you like | The app's database, in your Google account |
| When | When you tap **Export backup** | By Android, about once a day |
| Restore | **Settings → Restore from a backup** | When Hyperlog is installed on a phone signed in to the account |
| Reliable for | Any phone, any install route, keeping copies | A new or reset phone, without thinking about it |

The manual file is the one to trust: keep a recent one somewhere off the phone (Drive, a computer)
before switching phones. Auto Backup is the safety net.

## What's in a backup

Everything you made, as stored on the phone:

- **Programs** with their position, and every **Change** accepted on them (reverted ones too).
- **Workouts** from the coach: the one waiting on Today, and each one a session trained.
- **Sessions**: finished ones with every set, drop, mini-set and hold, the edits made during them
  (swaps, added and removed sets, techniques), and **the running session** with the set half-typed on
  the pad, the exercise on screen and the rest timer.
- **Training days** (the weekday plan), each with its gym.
- **Gyms and equipment**: gyms, equipment and their weight ladders, the shared
  dumbbells and plates, the machine remembered for each exercise, and the exercises a gym is
  missing.
- **Exercise library**: the shipped exercises, your edits, hidden exercises and the ones you added.
- **Starting weights** from the old app, **lift goals** and **bodyweight**.
- **Settings**: "Check for updates" and the update banner you dismissed.

Left out: the last update check (`update_check`), only a cache of GitHub's answer; a restore leaves
it as it is. A test (`BackupTablesTest`) fails if a table is added to the database without being
listed in the backup or excluded with a reason (`app/src/main/java/com/hyperlog/app/backup/BackupFormat.kt`).

Not in a backup: things that aren't stored. That means words typed for Claude but not sent, a
correction you started but didn't save, notification permission, and the rest alarm, which is set
again from the restored rest timer.

## Manual backup

- **Settings → Export backup** opens the system's save dialog with `hyperlog-backup-2026-10-13.json`.
  Pick Drive, Downloads or anywhere else. The row then reads `Saved Tue 13 Oct, 14:05`.
- **Settings → Restore from a backup** opens the system's file picker. A Hyperlog backup asks
  first: "Replace everything on this phone?" with the backup's date. **Restore** replaces all the data
  on the phone with the backup's, a running session included, and the app starts again on it.
- Restoring is all or nothing. A file that isn't a Hyperlog backup, a damaged one or one made by a
  newer Hyperlog says so under the row, and nothing on the phone changes.

### The file

```json
{
  "format": "hyperlog-backup",
  "version": 1,
  "database": 23,
  "exportedAt": 1791889500000,
  "tables": {
    "gym": [ { "id": 1, "name": "Central", "archived": 0, … } ],
    "weekday_plan": [ { "weekday": 1, "gym_id": 1 }, { "weekday": 4, "gym_id": 1 } ],
    "logged_set": [ { "id": 1, "session_exercise_id": 1, "position": 0, "weight_kg": 80.0, "full_reps": 8, … } ],
    …
  }
}
```

- `format` names the file; `version` is the layout of the file itself; `database` is the Room
  schema version its tables follow; `exportedAt` is when it was made (epoch ms).
- `tables` holds every table row for row, with column names and values exactly as stored (ids
  included), so a restore puts back the same rows.
- A restore checks the file first (format, versions, known tables and columns), and refuses it as
  damaged when a table or a column is missing that its database version has: a missing table
  would otherwise be wiped. Then it deletes every
  row and inserts the backup's in one transaction. References between rows are checked before it
  commits; any problem rolls it back.

### For later database versions

The backup follows the database. When a ticket bumps the Room version:

1. Bump `BackupFormat.DATABASE`; `BackupTablesTest` checks it matches.
2. A new table goes into `BackupFormat.Tables` or `Excluded` with a reason.
3. Backups from older versions must still restore: `BackupsTest` restores the kept file
   `app/src/test/resources/backup/hyperlog-backup-db14.json` and one for each later version (`-db16.json` to `-db22.json`). Add an upgrade step for older files
   in `Backups` (new tables and new columns with defaults need none: list them in
   `BackupFormat.Added` under the version that added them, so an older file may lack them; a
   renamed or reshaped column needs a step), and keep a fixture for each version. A dropped
   column needs a step that reads it out of an older file before the rows go in: a file from
   before v23 has each gym's `weekdays`, which become `weekday_plan` rows as the v23 migration
   moves them (`Migration22To23.planFrom`).

## Android Auto Backup

The app turns on Android's Auto Backup (`android:allowBackup`). The rules in
`app/src/main/res/xml/data_extraction_rules.xml` (Android 12 and later) and `backup_rules.xml`
(Android 11 and earlier) include the database (`hyperlog.db` with its `-wal` and `-shm` files),
which holds all of the above. The app stores nothing else.

- **Where it goes:** the Google account's backup (Settings → Google → Backup on the phone, "Backup
  by Google One" must be on). It doesn't count against Drive storage; the limit is 25 MB an app,
  far above Hyperlog's size. Android 9 and later encrypt it with the phone's screen lock.
- **When:** about once every 24 hours, when the phone is idle, charging and on Wi-Fi. A backup made
  before yesterday's session won't have it: export a manual backup before a planned switch.
- **Restoring on a new phone:** sign in to the same Google account during setup and restore from
  its backup, then install Hyperlog from the latest release of `villekivela/hyperlog` in the phone's browser. Android
  restores the data when the app is installed, before it first opens.
- **It only restores into the same app:** the same package (`com.hyperlog.app`; the debug build
  `com.hyperlog.app.dev` has its own backup) signed with the same release key. Keep the release
  key's backup (SPEC, Distribution).
- **Device-to-device transfer** (cable or Wi-Fi during setup, Android 12+) uses the same rules, but
  only for apps the transfer installs; an app installed outside the Play Store may not be one of
  them. Use the manual file then.

### Checking it with adb

`bmgr` drives a backup and restore by hand (debug build shown; use `com.hyperlog.app` for a release):

```sh
adb shell bmgr enabled                          # "Backup Manager currently enabled"
adb shell bmgr list transports                  # the Google transport is marked with *
adb shell bmgr backupnow com.hyperlog.app.dev   # "Package com.hyperlog.app.dev with result: Success"
adb shell dumpsys backup | grep -A3 hyperlog    # shows the package with a recent backup
# Restore: clear the data (or uninstall and reinstall), then restore the current set.
adb shell dumpsys backup | grep Current:        # the current restore set's token, e.g. 3c07c604f1d31717
adb shell pm clear com.hyperlog.app.dev
adb shell bmgr restore <token> com.hyperlog.app.dev
```

Android 17 needs the token: `bmgr restore <package>` answers "no longer supported". Always name
the package after it, since a token on its own restores every app on the phone.

Export a manual backup first: `pm clear` deletes the data on the phone.
