# Stromruf Agent Instructions

## Repo Skills

Repository-specific reusable workflows are indexed in:

`.agents/skills/repo-skills/SKILL.md`

## Persistent repo/install workflow is mandatory

When a user mentions Stromruf, this repository, `main`, an APK, ADB, installation/update, debugging, testing, building, fixing, pushing, or a PR in connection with this app, you MUST read and follow:

`.agents/skills/repo-install-persistence/SKILL.md`

**Local-only fixes are not completion.** A code or test fix discovered while debugging/building/installing is not finished while it exists only in a working tree, worktree, APK, patch, or local commit.

The user has granted standing authorization for agent-created fixes that were successfully relevant-tested and, when applicable, successfully installed/launch-checked: create/update the PR, merge it into `main` without asking again, verify the merge on remote `main`, and use the merged `main` as the durable source of truth.

Never report repository/install work as complete until every intended agent change is either on `main` or explicitly reported as blocked. Never commit secrets, `.env`, signing keys/keystores, local SDK configuration, generated APKs, or unrelated local files.

## Android update safety

The Stromruf Android package is `com.aistudio.stromruf.gkrfws`.

- Preserve the installed app's signing identity when performing updates.
- Do not silently uninstall the app to solve a signing mismatch; protect user data and investigate the signing source first.
- Do not bypass unexpected Android/Play Protect security warnings.
- For repository-backed installation work, record the merged `main` SHA that produced the final verified APK.
