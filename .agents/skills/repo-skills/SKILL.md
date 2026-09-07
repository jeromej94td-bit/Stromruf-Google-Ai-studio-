---
name: repo-skills
description: Use when working in the Stromruf repository and a repo-specific workflow, convention, or reusable project rule may apply.
---

# Repo Skills

Repository-specific workflows live as separate skills under `.agents/skills/`.

## Available Repo Skills

- `repo-install-persistence` — mandatory for Stromruf install/update/APK/ADB/build/debug/test/fix/PR work where an agent change could otherwise remain only local.

## Rule for future additions

1. Create each reusable workflow under `.agents/skills/<repo-skill-name>/SKILL.md`.
2. Add it to this index.
3. Reference it from `AGENTS.md` when its trigger must be mandatory.
4. Keep implementation details in the individual skill rather than this index.
