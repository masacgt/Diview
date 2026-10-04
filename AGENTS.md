# Diview development rules

- The current source of truth is `kurogane-dividend/`.
- Make Android app changes and create APKs from `kurogane-dividend/android/`.
- Use `kurogane-dividend/src/` as the reference for app behavior when the native implementation needs it; do not treat it as the Android build source.
- Do not use implementations, APKs, HTML, Flutter materials, or screenshots under `archive/` as current design references or sources for restoring old UI.
- Change the UI design only when the user explicitly requests a design change.
- Determine screen behavior from the current code under `kurogane-dividend/` and the user's latest instructions.
- Keep `archive/` as historical material. Do not delete or restore its contents unless the user asks.

## Git and generated files

- Keep app source, scripts, documentation, and required bundled data in Git.
- Do not commit local environment files, signing credentials, Gradle caches, IDE state, or generated build outputs/APKs.
- Refresh the bundled TSE 33-industry mapping with `python scripts/sync_tse33_industry_master.py` from `kurogane-dividend/` and review the data changes before committing.
