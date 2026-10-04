# Diview workspace

## Active project

`kurogane-dividend/` is the current source of truth. Android application work and APK builds belong in `kurogane-dividend/android/`. Follow [`AGENTS.md`](./AGENTS.md) before making changes.

## Workspace layout

- `kurogane-dividend/` — current app source and design; Android implementation, React behavior reference, and project scripts.
- `DiviewApp/` and `dividend/` — older working copies retained until their contents are reviewed and safely archived.
- `docs/` — product notes and specifications.
- `scripts/`, `data/`, `irbankMCP/` — supporting data and tooling.
- `archive/legacy-projects/` — older implementations and UI proposals, retained only for history; do not use as design or implementation sources.
- `archive/legacy-flutter-scaffold/` — former root-level Flutter starter project and generated files; not the current application.
- `archive/visual-qa-captures/` — old UI captures and inspection output; not current design references.
- `tools/offline-installers/` — local Gradle and JDK installer archives.

The extracted local runtimes (`jdk17/`, `gradle-dist/`) and Gradle/Wrangler caches remain at the root because existing local tooling uses those paths. They are not application source.

The archived folders are preserved; they have not been deleted. New app work should stay within `kurogane-dividend/`.
