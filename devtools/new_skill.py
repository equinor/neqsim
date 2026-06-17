"""
new_skill.py - Scaffold a new NeqSim skill for the agent system.

Creates a skill folder under .github/skills/ with a SKILL.md template
following the established format (YAML frontmatter + structured sections).

Usage:
    neqsim new-skill "neqsim-my-topic"
    neqsim new-skill "neqsim-my-topic" --description "Short description"
    neqsim new-skill --list              # list existing skills

Inspired by OpenClaw's skill-creator pattern: make it trivial for domain
experts to package their workflows as reusable knowledge for AI agents.
"""
import os
import sys
from datetime import date


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
SKILLS_DIR = os.path.join(PROJECT_ROOT, ".github", "skills")
AGENTS_DIR = os.path.join(PROJECT_ROOT, ".github", "agents")
COPILOT_INSTRUCTIONS = os.path.join(
    PROJECT_ROOT, ".github", "copilot-instructions.md"
)


SKILL_TEMPLATE = '''---
name: {name}
version: "1.0.0"
description: "{description}"
last_verified: "{today}"
requires:
  # python_packages: []   # e.g. [tagreader, pandas]
  # java_packages: []     # e.g. [commons-math3]
  # env: []               # e.g. [PI_WEB_API_URL]
  # network: []           # e.g. [equinor-vpn]
---

# {title}

<!-- One-paragraph summary of what this skill covers and when agents should load it. -->

{summary}

## When to Use This Skill

<!-- Bullet list of trigger conditions — when should an agent load this skill? -->

- When the user asks about {topic_placeholder}
- When a task involves {topic_placeholder}

## Key Concepts

<!-- Brief explanation of the domain concepts an agent needs to understand. -->

### Concept 1

Explanation here.

### Concept 2

Explanation here.

## NeqSim Code Patterns

<!-- Copy-paste Java and/or Python code patterns that agents should use. -->

### Pattern: Basic Setup

```java
// Java 8 compatible — no var, List.of(), etc.
// TODO: Add your NeqSim code pattern here
```

### Pattern: Python (Jupyter)

```python
from neqsim import jneqsim

# TODO: Add your Python pattern here
```

## Common Mistakes

<!-- What goes wrong and how to fix it. Agents reference this for troubleshooting. -->

| Mistake | Fix |
|---------|-----|
| Example mistake | How to fix it |

## Validation Checklist

<!-- How to verify the skill's patterns produce correct results. -->

- [ ] Code compiles with Java 8
- [ ] Flash + `initProperties()` called before reading transport properties
- [ ] Mixing rule set before flash
- [ ] Results validated against reference data

## References

<!-- Standards, textbooks, papers that inform this skill. -->

- Reference 1
- Reference 2
'''


def list_skills():
    """List all existing skills."""
    if not os.path.isdir(SKILLS_DIR):
        print("No skills directory found at", SKILLS_DIR)
        return

    skills = sorted(
        d
        for d in os.listdir(SKILLS_DIR)
        if os.path.isdir(os.path.join(SKILLS_DIR, d))
    )
    print("Existing skills ({count}):".format(count=len(skills)))
    for s in skills:
        skill_file = os.path.join(SKILLS_DIR, s, "SKILL.md")
        desc = ""
        if os.path.isfile(skill_file):
            with open(skill_file, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip().startswith("description:"):
                        desc = line.split(":", 1)[1].strip().strip('"').strip("'")
                        break
        print("  {name:<40s} {desc}".format(name=s, desc=desc[:80]))


def create_skill(name, description=None):
    """Create a new skill folder with SKILL.md template."""
    # Normalize name
    name = name.strip().lower().replace(" ", "-")
    if not name.startswith("neqsim-"):
        name = "neqsim-" + name

    skill_dir = os.path.join(SKILLS_DIR, name)
    skill_file = os.path.join(skill_dir, "SKILL.md")

    if os.path.exists(skill_dir):
        print("ERROR: Skill '{name}' already exists at {path}".format(
            name=name, path=skill_dir
        ))
        print("Edit the existing SKILL.md instead.")
        sys.exit(1)

    # Derive title from name
    title = name.replace("neqsim-", "NeqSim ").replace("-", " ").title()
    title = title.replace("Neqsim", "NeqSim")

    if description is None:
        description = (
            "{title} patterns and guidance for NeqSim. "
            "USE WHEN: working with {topic}."
        ).format(
            title=title,
            topic=name.replace("neqsim-", "").replace("-", " "),
        )

    topic_placeholder = name.replace("neqsim-", "").replace("-", " ")
    summary = (
        "This skill provides patterns, code templates, and domain knowledge "
        "for {topic} in NeqSim."
    ).format(topic=topic_placeholder)

    content = SKILL_TEMPLATE.format(
        name=name,
        description=description,
        today=date.today().isoformat(),
        title=title,
        summary=summary,
        topic_placeholder=topic_placeholder,
    )

    # Also generate a CHANGELOG.md for version tracking
    changelog_file = os.path.join(skill_dir, "CHANGELOG.md")
    changelog = "# {name} Changelog\n\n## 1.0.0 ({today})\n\n- Initial release\n".format(
        name=name, today=date.today().isoformat()
    )

    os.makedirs(skill_dir, exist_ok=True)
    with open(skill_file, "w", encoding="utf-8") as f:
        f.write(content)
    with open(changelog_file, "w", encoding="utf-8") as f:
        f.write(changelog)

    print("Created skill: {name}".format(name=name))
    print("  Folder: {path}".format(path=skill_dir))
    print("  File:   {file}".format(file=skill_file))
    print("  Changelog: {file}".format(file=changelog_file))
    print()
    print("Next steps:")
    print("  1. Edit {file} with your domain knowledge".format(file=skill_file))
    print("  2. Add code patterns, common mistakes, and validation steps")
    print("  3. Register the skill in .github/copilot-instructions.md")
    print("     (add to the <skills> section and the AGENTS.md skills table)")
    print("  4. Test by asking an agent a question in the skill's domain")
    print()
    print("Tip: Look at existing skills for examples:")
    print("  .github/skills/neqsim-api-patterns/SKILL.md")
    print("  .github/skills/neqsim-flow-assurance/SKILL.md")


def print_usage():
    """Print usage information."""
    print("Usage:")
    print('  neqsim new-skill "neqsim-my-topic"')
    print('  neqsim new-skill "neqsim-my-topic" --description "Short desc"')
    print("  neqsim new-skill --list")
    print()
    print("Examples:")
    print('  neqsim new-skill "neqsim-lng-operations"')
    print('  neqsim new-skill "pump-design" --description "Pump sizing and design per API 610"')
    print('  neqsim new-skill "dehydration" --description "TEG/DEG dehydration patterns"')


def main():
    args = sys.argv[1:]

    if not args or args[0] in ("-h", "--help"):
        print_usage()
        sys.exit(0)

    if args[0] == "--list":
        list_skills()
        sys.exit(0)

    name = args[0]
    description = None

    if "--description" in args:
        idx = args.index("--description")
        if idx + 1 < len(args):
            description = args[idx + 1]

    create_skill(name, description)


if __name__ == "__main__":
    main()
