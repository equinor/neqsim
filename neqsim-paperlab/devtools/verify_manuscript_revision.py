#!/usr/bin/env python3
"""Verify the TPG4230 manuscript revision quality gates."""

from __future__ import annotations

import json
import re
from pathlib import Path

import yaml


BOOK_ROOT = Path(
    "c:/Users/ESOL/Documents/GitHub/neqsim/neqsim-paperlab/books/"
    "tpg4230_field_development_and_operations_2026"
)


def load_book() -> dict:
    """Load book.yaml."""
    return yaml.safe_load((BOOK_ROOT / "book.yaml").read_text(encoding="utf-8"))


def chapter_entries(config: dict) -> list[dict]:
    """Return all chapter entries from book.yaml."""
    entries: list[dict] = []
    for part in config.get("parts", []):
        entries.extend(part.get("chapters", []))
    return entries


def check_chapter_sequence(entries: list[dict]) -> list[str]:
    """Check for exactly chapters 1-30 with no duplicate numbers."""
    errors: list[str] = []
    numbers = []
    for entry in entries:
        match = re.match(r"ch(\d+)_", entry["dir"])
        if not match:
            errors.append(f"Chapter dir does not start with chNN_: {entry['dir']}")
            continue
        numbers.append(int(match.group(1)))
        path = BOOK_ROOT / "chapters" / entry["dir"] / "chapter.md"
        if not path.exists():
            errors.append(f"Missing chapter file: {path.relative_to(BOOK_ROOT)}")
    if numbers != list(range(1, 31)):
        errors.append(f"Chapter sequence is {numbers}, expected 1..30")
    if len(numbers) != len(set(numbers)):
        errors.append(f"Duplicate chapter numbers found: {numbers}")
    return errors


def check_backmatter(config: dict) -> list[str]:
    """Check all backmatter files referenced by book.yaml exist."""
    errors: list[str] = []
    for key in config.get("backmatter", []):
        path = BOOK_ROOT / "backmatter" / f"{key}.md"
        if not path.exists():
            errors.append(f"Missing backmatter file: {path.relative_to(BOOK_ROOT)}")
    return errors


def check_chapter_pedagogy(entries: list[dict]) -> list[str]:
    """Check chapter objectives, lifecycle anchors, exercises, and figure discussions."""
    errors: list[str] = []
    for entry in entries:
        path = BOOK_ROOT / "chapters" / entry["dir"] / "chapter.md"
        text = path.read_text(encoding="utf-8")
        if not re.search(r"(?im)^## Learning objectives", text):
            errors.append(f"Missing learning objectives: {path.relative_to(BOOK_ROOT)}")
        if "## Where We Are in the Field-Development Lifecycle" not in text:
            errors.append(f"Missing lifecycle anchor: {path.relative_to(BOOK_ROOT)}")
        if not re.search(r"(?im)^## .*Exercises", text):
            errors.append(f"Missing exercise section: {path.relative_to(BOOK_ROOT)}")
        lines = text.splitlines()
        for index, line in enumerate(lines):
            if line.strip().startswith("!["):
                window = "\n".join(lines[index + 1 : index + 6])
                if "**Discussion" not in window:
                    errors.append(f"Figure lacks nearby discussion: {path.relative_to(BOOK_ROOT)} line {index + 1}")
    return errors


def check_exercises_are_final(entries: list[dict]) -> list[str]:
    """Check that no chapter content appears after the exercise section."""
    errors: list[str] = []
    markdown_files = [BOOK_ROOT / "backmatter" / "review_exam_preparation.md"]
    for entry in entries:
        markdown_files.append(BOOK_ROOT / "chapters" / entry["dir"] / "chapter.md")
    exercise_re = re.compile(r"^#{2,4}\s+.*Exercises", re.IGNORECASE)
    heading_re = re.compile(r"^#{1,6}\s+")
    for path in markdown_files:
        if not path.exists():
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        exercise_line = None
        for index, line in enumerate(lines):
            if exercise_re.match(line.strip()):
                exercise_line = index
                break
        if exercise_line is None:
            continue
        for index, line in enumerate(lines[exercise_line + 1 :], start=exercise_line + 2):
            stripped = line.strip()
            if stripped.startswith("![") or heading_re.match(stripped):
                errors.append(
                    "Content after exercises: "
                    f"{path.relative_to(BOOK_ROOT)} line {index}: {stripped}")
    return errors


def check_notebooks(entries: list[dict]) -> list[str]:
    """Check chapter-listed notebooks exist and target repaired notebooks executed."""
    errors: list[str] = []
    for entry in entries:
        chapter_path = BOOK_ROOT / "chapters" / entry["dir"] / "chapter.md"
        text = chapter_path.read_text(encoding="utf-8")
        frontmatter = text.split("---", 2)
        if len(frontmatter) >= 3:
            for match in re.finditer(r"^-\s+(notebooks/[^\n]+)", frontmatter[1], re.MULTILINE):
                notebook_path = chapter_path.parent / match.group(1)
                if not notebook_path.exists():
                    errors.append(f"Notebook missing: {notebook_path.relative_to(BOOK_ROOT)}")
    repaired = [
        "chapters/ch11_field_development_building_blocks/notebooks/ch11_01_digital_twin_and_lifecycle.ipynb",
        "chapters/ch11_field_development_building_blocks/notebooks/ch11_02_concept_evaluation_framework.ipynb",
        "chapters/ch11_field_development_building_blocks/notebooks/ch11_03_screening_and_feasibility.ipynb",
        "chapters/ch13_subsea_surf_systems/notebooks/ch13_02_surf_equipment_design_cost.ipynb",
        "chapters/ch20_production_optimisation/notebooks/ch20_03_cash_flow_engine_and_economics.ipynb",
        "chapters/ch20_production_optimisation/notebooks/ch20_04_network_optimization_gas_lift.ipynb",
        "chapters/ch24_computational_tools_neqsim/notebooks/ch24_02_field_development_api_mastery.ipynb",
    ]
    for rel in repaired:
        path = BOOK_ROOT / rel
        data = json.loads(path.read_text(encoding="utf-8"))
        for index, cell in enumerate(data.get("cells", [])):
            if cell.get("cell_type") == "code" and not "".join(cell.get("source") or []).strip():
                errors.append(f"Empty code cell: {rel} cell {index}")
            for output in cell.get("outputs", []):
                if output.get("output_type") == "error":
                    errors.append(f"Notebook error output: {rel} cell {index} {output.get('ename')}")
        if not any(c.get("cell_type") == "code" and c.get("execution_count") is not None for c in data.get("cells", [])):
            errors.append(f"Notebook not executed: {rel}")
    return errors


def check_figure_paths(entries: list[dict]) -> list[str]:
    """Check markdown figure paths exist."""
    errors: list[str] = []
    image_re = re.compile(r"!\[[^\]]*\]\(([^)]+)\)")
    markdown_files = [BOOK_ROOT / "backmatter" / "review_exam_preparation.md"]
    for entry in entries:
        markdown_files.append(BOOK_ROOT / "chapters" / entry["dir"] / "chapter.md")
    for path in markdown_files:
        text = path.read_text(encoding="utf-8")
        for match in image_re.finditer(text):
            target = match.group(1).split("#", 1)[0]
            if target.startswith("http"):
                continue
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                errors.append(f"Missing figure path: {path.relative_to(BOOK_ROOT)} -> {target}")
    return errors


def main() -> int:
    """Run all checks and write a report."""
    config = load_book()
    entries = chapter_entries(config)
    sections = {
        "chapter_sequence": check_chapter_sequence(entries),
        "backmatter": check_backmatter(config),
        "chapter_pedagogy": check_chapter_pedagogy(entries),
        "exercises_are_final": check_exercises_are_final(entries),
        "notebooks": check_notebooks(entries),
        "figure_paths": check_figure_paths(entries),
    }
    total_errors = sum(len(items) for items in sections.values())
    report = ["# Manuscript Revision Verification Report", ""]
    report.append(f"Total errors: {total_errors}")
    report.append("")
    for name, errors in sections.items():
        report.append(f"## {name}")
        if errors:
            report.extend(f"- {error}" for error in errors)
        else:
            report.append("- PASS")
        report.append("")
    (BOOK_ROOT / "MANUSCRIPT_REVISION_VERIFICATION.md").write_text("\n".join(report), encoding="utf-8")
    print(f"Verification errors: {total_errors}")
    for name, errors in sections.items():
        print(f"{name}: {len(errors)}")
    return 1 if total_errors else 0


if __name__ == "__main__":
    raise SystemExit(main())