# Agent persistence workflow

Stromruf repository-backed fixes are only complete when the verified change is durable on GitHub `main`.

The required flow is:

1. start from the current remote `main` in an isolated branch/worktree;
2. run the relevant regression checks and Android build;
3. commit and push every successful agent-discovered fix;
4. verify the remote branch SHA and open a PR with `<!-- agent-persist: verified -->` only after relevant local verification succeeds;
5. let the persistence gate and PR Android build complete successfully;
6. let the guarded GitHub workflow squash-merge the verified owner-created same-repo PR;
7. explicitly dispatch `android-debug-build.yml` on `main` after that token-driven merge;
8. require the dispatched `main` build to succeed and verify the merge SHA is contained in remote `main`.

Local-only changes, APK-only fixes, unpushed commits, and unmerged PRs are not completion. Never commit secrets, `.env`, local SDK configuration, keystores/signing material, or generated APKs.
