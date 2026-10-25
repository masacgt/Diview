# Diview development rules

- The current source of truth is `kurogane-dividend/`.
- Make Android app changes and create APKs from `kurogane-dividend/android/`.
- Use `kurogane-dividend/src/` as the reference for app behavior when the native implementation needs it; do not treat it as the Android build source.
- Do not use implementations, APKs, HTML, Flutter materials, or screenshots under `archive/` as current design references or sources for restoring old UI.
- Change the UI design only when the user explicitly requests a design change.
- Determine screen behavior from the current code under `kurogane-dividend/` and the user's latest instructions.
- Keep `archive/` as historical material. Do not delete or restore its contents unless the user asks.

## Work location and directories

- Work only within the working folder supplied for the task; do not create or write task files elsewhere.
- Do not create new folders anywhere under `C:\`, at any depth. Reuse existing folders inside the supplied working folder.
- If no suitable existing folder is available, ask for a destination before writing files.

## Git and generated files

- Keep app source, scripts, documentation, and required bundled data in Git.
- Do not commit local environment files, signing credentials, Gradle caches, IDE state, or generated build outputs/APKs.
- Refresh the bundled TSE 33-industry mapping with `python scripts/sync_tse33_industry_master.py` from `kurogane-dividend/` and review the data changes before committing.

## AI-maintained project notes

- Keep project-level notes in `ai_docs/` at the repository root. Create the directory and files when needed; preserve useful existing content when updating them.
- `ai_docs/todo.md` tracks active work, completed work, and next steps. Mark an item complete (`[x]`) only after its implementation runs/builds successfully, relevant existing behavior has been checked for regressions, and the stated requirements are met. Add one concise evidence sentence beside each completed item describing the files or checks that support completion. If any relevant case remains unverified or uncertain, leave the item open and add a separate `[ ]` follow-up for that verification.
- `ai_docs/architecture.md` records the current technology stack, source and directory layout, data/storage design, and important project rules. Update it when those facts or rules change; do not speculate about unverified behavior.
- `ai_docs/trouble.md` records significant errors, bugs, environment setup pitfalls, and their confirmed resolutions. Add an entry when it is likely to prevent the same investigation from recurring.
- Update the relevant note when code or documentation changes materially affect it, or when a task reaches a meaningful milestone. Keep notes concise and factual; do not create routine entries that add no lasting project information.

## Safe changes and reporting

- Preserve existing behavior, configuration, and unrelated working-tree changes. Before editing, inspect the relevant files and current changes; after editing, report the changed files and summarize the diff. Do not stage, commit, or revert unrelated work.
- Do not make unrequested product, behavior, or design changes based on personal preference. Follow the user's explicit requirements and existing project rules; raise a concrete proposal when a genuine ambiguity or conflict prevents a safe implementation.
- If the same error remains unresolved after three consecutive focused attempts, or an implementation decision cannot be resolved from the requirements and current code, pause that dependent work and report the observed state and viable options with trade-offs. Continue independent work where possible.
- For ambiguous requests, identify the plausible interpretations and ask a concise clarifying question before taking an irreversible or materially different path. When the ambiguity does not prevent safe progress, proceed on the narrowest reasonable interpretation and state the assumption.

## Build, device, and publication guidance

- For Diview Android build, install, or emulator instructions, explicitly select JDK 17 (for example, set `JAVA_HOME` to the configured JDK 17 installation before invoking Gradle or `adb`). Use `kurogane-dividend/android/` as the Android build root.
- Before a device build, confirm the checked-out branch and `HEAD` with `git status --short --branch` and `git rev-parse HEAD`, and confirm the intended change exists in that checkout. A push updates the remote only; it does not update another worktree or an installed app. Preserve dirty shared work and use an isolated task worktree under `C:\Codex` when the shared checkout is on a different branch.
- Build and install from the same worktree, using the explicit APK path produced there; never fall back to a generic or pre-existing `app-debug.apk` from another checkout. After installation, compare the built APK's SHA-256 with the installed package's `base.apk` and verify a UI element unique to the requested change.
- Run builds, device installs, emulator checks, deployments, or other external side effects when they are requested or clearly necessary and the required target and access are available. Do not claim that a change was installed, deployed, or verified unless that action actually succeeded.
- When the user asks for publication or a push is otherwise explicitly authorized, provide or perform the appropriate commit and push steps with a change-specific commit message. Inspect the branch, remote, and diff first; stage only the intended files. Do not push automatically merely because a local edit was made.
- After a code change, provide the relevant build and device-install commands when applicable. For Android, include JDK 17 selection and the correct project paths; tailor the device command to the available device and build variant. Clearly label commands that require the user to choose a device, credentials, or deployment target.
- Do not treat documentation-only changes as requiring an Android build or device install. For documentation changes, report the diff and provide publication steps only when requested or authorized.
- Group related, ready-to-run commands in one code block when practical. Never present a commit or push command that could accidentally include unrelated modifications.
