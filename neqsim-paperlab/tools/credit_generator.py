"""
CRediT Generator — Generates CRediT (Contributor Roles Taxonomy) author
contribution statements for scientific papers per the NISO standard.

The 14 CRediT roles: Conceptualization, Data curation, Formal analysis,
Funding acquisition, Investigation, Methodology, Project administration,
Resources, Software, Supervision, Validation, Visualization,
Writing – original draft, Writing – review & editing.

Usage::

    from tools.credit_generator import generate_credit, print_credit_report

    report = generate_credit("papers/my_paper/", contributors={
        "Alice Smith": ["Conceptualization", "Methodology", "Writing – original draft"],
        "Bob Jones": ["Software", "Validation", "Visualization"],
    })
    print_credit_report(report)
"""

import json
from pathlib import Path
from typing import Dict, List, Optional


# Official CRediT roles per NISO Z39.104-2022
CREDIT_ROLES = [
    "Conceptualization",
    "Data curation",
    "Formal analysis",
    "Funding acquisition",
    "Investigation",
    "Methodology",
    "Project administration",
    "Resources",
    "Software",
    "Supervision",
    "Validation",
    "Visualization",
    "Writing – original draft",
    "Writing – review & editing",
]

# Short descriptions for each role
_ROLE_DESCRIPTIONS = {
    "Conceptualization": "Ideas; formulation of overarching research goals and aims",
    "Data curation": "Annotation, scrubbing, and maintenance of research data",
    "Formal analysis": "Application of statistical, mathematical, or computational techniques",
    "Funding acquisition": "Acquisition of financial support for the project",
    "Investigation": "Conducting the research and investigation process; running experiments",
    "Methodology": "Development or design of methodology; creation of models",
    "Project administration": "Management and coordination of the research activity",
    "Resources": "Provision of study materials, computing resources, or other tools",
    "Software": "Programming, software development; designing computer programs",
    "Supervision": "Oversight and leadership responsibility for the research activity",
    "Validation": "Verification of the overall replication/reproducibility of results",
    "Visualization": "Preparation, creation, and/or presentation of data visualizations",
    "Writing – original draft": "Preparation and creation of the published work",
    "Writing – review & editing": "Critical review, commentary, or revision of the work",
}


def _validate_roles(roles):
    """Validate that all roles are official CRediT roles.

    Args:
        roles: List of role strings.

    Returns:
        Tuple of (valid_roles, invalid_roles).
    """
    valid = []
    invalid = []
    role_map = {r.lower(): r for r in CREDIT_ROLES}
    for r in roles:
        canonical = role_map.get(r.lower().strip())
        if canonical:
            valid.append(canonical)
        else:
            invalid.append(r)
    return valid, invalid


def generate_credit(paper_dir, contributors=None):
    """Generate a CRediT author contribution statement.

    If contributors is None, reads from plan.json or creates a template.

    Args:
        paper_dir: Path to the paper directory.
        contributors: Dict mapping author name to list of CRediT roles.

    Returns:
        Report dict with formatted statements and validation.
    """
    paper_dir = Path(paper_dir)

    # Try loading from plan.json if not provided
    if contributors is None:
        plan_file = paper_dir / "plan.json"
        if plan_file.exists():
            with open(plan_file) as f:
                plan = json.load(f)
            contributors = plan.get("credit_contributions", {})

    if not contributors:
        return {
            "status": "template",
            "message": "No contributors specified. Add to plan.json under 'credit_contributions'.",
            "template": {
                "Author Name 1": ["Conceptualization", "Methodology", "Writing – original draft"],
                "Author Name 2": ["Software", "Validation", "Writing – review & editing"],
            },
            "available_roles": CREDIT_ROLES,
        }

    # Validate all roles
    all_invalid = []
    validated = {}
    for author, roles in contributors.items():
        valid, invalid = _validate_roles(roles)
        validated[author] = valid
        for inv in invalid:
            all_invalid.append({"author": author, "role": inv})

    # Build per-author statements
    author_statements = []
    for author, roles in validated.items():
        if roles:
            roles_str = ", ".join(roles)
            author_statements.append(f"**{author}**: {roles_str}")

    # Build per-role statements (alternative format used by some journals)
    role_authors = {}
    for author, roles in validated.items():
        for role in roles:
            role_authors.setdefault(role, []).append(author)

    role_statements = []
    for role in CREDIT_ROLES:
        if role in role_authors:
            authors_str = ", ".join(role_authors[role])
            role_statements.append(f"**{role}**: {authors_str}")

    # Coverage check: are all 14 roles assigned?
    covered_roles = set()
    for roles in validated.values():
        covered_roles.update(roles)
    uncovered = [r for r in CREDIT_ROLES if r not in covered_roles]

    # Format for paper.md section
    paper_section = "## CRediT Author Statement\n\n"
    paper_section += "\n\n".join(author_statements)

    report = {
        "status": "generated",
        "author_count": len(validated),
        "author_statements": author_statements,
        "role_statements": role_statements,
        "paper_section": paper_section,
        "roles_covered": len(covered_roles),
        "roles_total": len(CREDIT_ROLES),
        "uncovered_roles": uncovered,
        "invalid_roles": all_invalid,
    }

    # Save to paper directory
    credit_file = paper_dir / "credit_statement.json"
    with open(credit_file, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    return report


def print_credit_report(report):
    """Print a formatted CRediT report.

    Args:
        report: Report dict from generate_credit().
    """
    if report.get("status") == "template":
        print("=" * 60)
        print("CRediT AUTHOR STATEMENT — TEMPLATE")
        print("=" * 60)
        print(f"\n  {report['message']}\n")
        print("  Available roles:")
        for role in report.get("available_roles", []):
            desc = _ROLE_DESCRIPTIONS.get(role, "")
            print(f"    - {role}: {desc}")
        print()
        return

    print("=" * 60)
    print("CRediT AUTHOR STATEMENT")
    print("=" * 60)
    print(f"  Authors: {report['author_count']}")
    print(f"  Roles covered: {report['roles_covered']}/{report['roles_total']}")
    print()

    for stmt in report.get("author_statements", []):
        print(f"  {stmt}")
    print()

    uncovered = report.get("uncovered_roles", [])
    if uncovered:
        print("  Unassigned roles:")
        for r in uncovered:
            print(f"    [ ] {r}")
        print()

    invalid = report.get("invalid_roles", [])
    if invalid:
        print("  Invalid roles (not in CRediT taxonomy):")
        for inv in invalid:
            print(f"    [!!] {inv['author']}: '{inv['role']}'")
        print()

    print(f"  Saved to: credit_statement.json")
    print()
