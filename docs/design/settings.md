# Settings

A flat list behind Today's settings icon. Backups (#21) join it as a group of two rows.

## Principles

- One list, no cards or dividers: rows are Material list items, groups are set apart by 16 dp.
- Each row says what it holds or what it last did in its supporting line; errors replace that
  line in `error`, never a snackbar or toast.
- Anything that replaces data asks first, in a dialog naming what will be replaced.
- Symbols sit in the 24 dp margin column; text lines up at 64 dp (ListItem's 16 dp inset + 8 dp).

## Layout

```
←  Settings
[fitness]   Gyms and equipment               >
            2 gyms · dumbbells and plates
[list]      Exercise library                 >
            173 exercises
[history]   Starting weights from the old app
            80 exercises · tap to import again
                                                  16 dp
[download]  Export backup
            Everything on this phone, in one file | Saved Tue 13 Oct, 14:05 | Couldn't save the file
[restore]   Restore from a backup
            Replaces everything on this phone | <why it was refused> · nothing changed
            Android also keeps a copy in your Google account, …   (bodySmall, onSurfaceVariant)
```

## States and rules

- **Export**: tap → the system save dialog with `hyperlog-backup-<date>.json` → `Saved <EEE d MMM, HH:mm>`.
- **Restore**: tap → the system file picker → a readable backup opens the dialog
  (`restore_page` symbol, "Replace everything on this phone?", the backup's date, Cancel / Restore).
  Restore → the app starts again on the backup's data (Today, or the restored session).
- A refused file shows its reason in the restore row: not a Hyperlog backup, made by a newer
  Hyperlog (update first), too old, damaged. Nothing on the phone changes.
- The starting-weights row now says "the old app's backup" so it can't be mistaken for a Hyperlog
  backup.

Screens: `screens/settings-01-backup.png`, `settings-02-exported.png`, `settings-03-refused.png`
(Robolectric renders, dark, w410dp). The dialog doesn't render in Robolectric; check it on the phone.
