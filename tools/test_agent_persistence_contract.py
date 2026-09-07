from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    target = ROOT / path
    assert target.is_file(), f"missing required persistence file: {path}"
    return target.read_text(encoding="utf-8")


def require(text: str, needle: str, path: str) -> None:
    assert needle in text, f"{path} must contain: {needle}"


def main() -> None:
    agents = read("AGENTS.md")
    skill = read(".agents/skills/repo-install-persistence/SKILL.md")
    index = read(".agents/skills/repo-skills/SKILL.md")
    script = read("scripts/agent_persistence.ps1")
    read(".github/workflows/agent-persistence-gate.yml")

    require(agents, ".agents/skills/repo-install-persistence/SKILL.md", "AGENTS.md")
    require(agents, "Local-only fixes are not completion.", "AGENTS.md")
    require(agents, "merge it into `main` without asking again", "AGENTS.md")
    require(index, "repo-install-persistence", ".agents/skills/repo-skills/SKILL.md")

    require(skill, "jeromej94td-bit/Stromruf-Google-Ai-studio-", "repo-install-persistence/SKILL.md")
    require(skill, "merge the PR into `main` without asking again", "repo-install-persistence/SKILL.md")
    require(skill, "built from merged `main`", "repo-install-persistence/SKILL.md")

    for command in ("git diff --check", "fetch", "merge-base", "--is-ancestor"):
        require(script, command, "scripts/agent_persistence.ps1")

    print("Agent persistence contract: OK")


if __name__ == "__main__":
    main()
