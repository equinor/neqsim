#!/usr/bin/env python3
"""Check NeqSim agent and skill catalogs across sibling repositories.

The check is intentionally repository-layout based and does not fetch network
content. It verifies that catalog paths exist, agent required skills resolve to
core/community/enterprise skills, and agent names do not collide across public
and enterprise catalogs.
"""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import sys

try:
    import yaml
except ImportError:  # pragma: no cover - exercised only in minimal environments
    yaml = None


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_WORKSPACE_ROOT = REPO_ROOT.parent


CATALOGS = {
    "community_agents": ("neqsim-community-agents", "community-agents.yaml", "agents"),
    "community_skills": ("neqsim-community-skills", "community-skills.yaml", "skills"),
    "enterprise_agents": ("neqsim-enterprise-agents", "enterprise-agents.yaml", "agents"),
    "enterprise_skills": ("neqsim-enterprise-skills", "enterprise-skills.yaml", "skills"),
}


def _load_yaml(path: Path) -> dict:
    """Load a YAML catalog file.

    @param path catalog file path
    @return decoded YAML mapping
    @raises RuntimeError if PyYAML is unavailable
    """
    if yaml is None:
        raise RuntimeError("PyYAML is required for catalog integrity checks")
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def _catalog_items(catalog: dict, kind: str) -> list[dict]:
    """Return catalog items for a kind.

    @param catalog decoded catalog mapping
    @param kind either agents or skills
    @return list of catalog entries
    """
    return [item for item in catalog.get(kind, []) if isinstance(item, dict)]


def _core_skill_names() -> set[str]:
    """Return skill names available in the core NeqSim repository.

    @return core skill folder names under .github/skills
    """
    skills_dir = REPO_ROOT / ".github" / "skills"
    if not skills_dir.exists():
        return set()
    return {path.name for path in skills_dir.iterdir() if path.is_dir()}


def check_catalogs(workspace_root: Path) -> int:
    """Check sibling agent/skill catalogs.

    @param workspace_root parent directory containing the NeqSim catalog repos
    @return process exit code, 0 when all checks pass
    """
    errors: list[str] = []
    counts: dict[str, int] = {}
    skill_names = _core_skill_names()
    agent_names: list[tuple[str, str]] = []
    required_skills: list[tuple[str, str, str]] = []

    for label, (folder, catalog_name, kind) in CATALOGS.items():
        repo_dir = workspace_root / folder
        catalog_path = repo_dir / catalog_name
        if not catalog_path.exists():
            errors.append(f"{label}: catalog missing: {catalog_path}")
            continue

        catalog = _load_yaml(catalog_path)
        items = _catalog_items(catalog, kind)
        counts[label] = len(items)

        for item in items:
            name = item.get("name", "")
            if not name:
                errors.append(f"{label}: catalog entry missing name")
                continue
            relative_path = item.get("path", "")
            if not relative_path:
                errors.append(f"{label}: {name} missing path")
            elif not (repo_dir / relative_path).exists():
                errors.append(f"{label}: {name} points to missing path: {relative_path}")

            if kind == "skills":
                skill_names.add(name)
            else:
                agent_names.append((label, name))
                for skill_name in item.get("required_skills") or []:
                    required_skills.append((label, name, str(skill_name)))

    for label, agent_name, skill_name in required_skills:
        if skill_name not in skill_names:
            errors.append(
                f"{label}: {agent_name} requires missing skill: {skill_name}"
            )

    duplicate_names = sorted(
        name for name, count in Counter(name for _, name in agent_names).items() if count > 1
    )
    for name in duplicate_names:
        scopes = sorted(label for label, agent_name in agent_names if agent_name == name)
        errors.append(f"agent name is duplicated across catalogs: {name} ({', '.join(scopes)})")

    print("Catalog counts:")
    for label in sorted(counts):
        print(f"  {label}: {counts[label]}")

    if errors:
        print("\nErrors:")
        for error in errors:
            print(f"  ERROR {error}")
        return 1

    print("\nResult: PASS")
    return 0


def main() -> int:
    """Run the catalog checker from the command line.

    @return process exit code
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=DEFAULT_WORKSPACE_ROOT,
        help="Parent directory containing neqsim-* catalog repositories",
    )
    args = parser.parse_args()
    try:
        return check_catalogs(args.workspace_root.resolve())
    except RuntimeError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())