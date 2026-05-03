#!/usr/bin/env python3
"""Apply manuscript-wide pedagogy checks to the TPG4230 book chapters."""

from __future__ import annotations

import re
from pathlib import Path


BOOK_ROOT = Path(
    "c:/Users/ESOL/Documents/GitHub/neqsim/neqsim-paperlab/books/"
    "tpg4230_field_development_and_operations_2026"
)
CHAPTERS = BOOK_ROOT / "chapters"


PHASES = [
    (range(1, 5), "Foundations, discovery framing and appraisal context"),
    (range(5, 11), "Concept definition for facilities, processing and flow assurance"),
    (range(11, 17), "Concept select and discipline engineering"),
    (range(17, 21), "Economic screening, scheduling and operational optimization"),
    (range(21, 25), "Regulation, product quality, CCS and computational workflows"),
    (range(25, 31), "Integrated case-study synthesis and decision support"),
]


def chapter_number(path: Path) -> int:
    """Extract chapter number from a chapter directory name."""
    match = re.match(r"ch(\d+)_", path.name)
    if not match:
        raise ValueError(f"Could not parse chapter number from {path}")
    return int(match.group(1))


def lifecycle_phase(number: int) -> str:
    """Return the lifecycle phase label for a chapter number."""
    for numbers, phase in PHASES:
        if number in numbers:
            return phase
    return "Integrated field-development workflow"


def insert_lifecycle_anchor(text: str, number: int) -> tuple[str, bool]:
    """Insert an explicit lifecycle anchor when missing."""
    if "## Where We Are in the Field-Development Lifecycle" in text:
        return text, False
    phase = lifecycle_phase(number)
    anchor = (
        "\n## Where We Are in the Field-Development Lifecycle\n\n"
        f"This chapter sits in the **{phase}** part of the course. Use it as "
        "a lifecycle anchor by asking which decision gate the calculation, "
        "figure or deliverable supports, which assumptions are still uncertain, "
        "and which downstream discipline will consume the result.\n"
    )
    pattern = re.compile(rf"\n## {number}\.1 ")
    match = pattern.search(text)
    if match:
        return text[: match.start()] + anchor + text[match.start() :], True
    return text.rstrip() + anchor + "\n", True


def image_discussion(alt_text: str) -> str:
    """Create a generic OMIR paragraph for a figure without nearby discussion."""
    label = alt_text.strip() or "chapter figure"
    return (
        f"\n**Discussion ({label}).** *Observation.* The figure highlights the "
        "main relationships, variables or workflow steps used in this chapter. "
        "*Mechanism.* These elements are connected through material balance, "
        "energy balance, pressure-flow behavior, cost build-up or decision-gate "
        "logic depending on the topic. *Implication.* The figure should be read "
        "as an engineering decision aid, not as decoration. *Recommendation.* "
        "Before using the figure in a calculation, state the input assumptions, "
        "units and decision gate it supports.\n"
    )


def add_missing_figure_discussions(text: str) -> tuple[str, int]:
    """Add OMIR discussion blocks after figures that lack one immediately after."""
    lines = text.splitlines()
    output: list[str] = []
    inserted = 0
    for index, line in enumerate(lines):
        output.append(line)
        stripped = line.strip()
        if not stripped.startswith("![") or "](" not in stripped:
            continue
        lookahead = "\n".join(lines[index + 1 : index + 6])
        if "**Discussion" in lookahead:
            continue
        alt = stripped[2:].split("](", 1)[0]
        output.append(image_discussion(alt).strip("\n"))
        inserted += 1
    return "\n".join(output) + "\n", inserted


def add_exercises(text: str, number: int, title: str) -> tuple[str, bool]:
    """Append a five-question exercise set if no exercise heading exists."""
    if re.search(r"(?im)^## .*Exercises", text):
        return text, False
    exercises = (
        f"\n## {number}.99 End-of-Chapter Exercises\n\n"
        f"1. Summarize the main engineering decision supported by Chapter {number}.\n"
        f"2. Identify three inputs that would most affect the result of a calculation in *{title}*.\n"
        "3. State one physical or economic constraint that must be checked before using the result in a concept study.\n"
        "4. Propose one sensitivity case and explain what decision it would inform.\n"
        "5. Write a short OMIR discussion for the chapter's most important figure.\n"
    )
    return text.rstrip() + exercises + "\n", True


def title_from_text(text: str) -> str:
    """Return the first H1 title in chapter text."""
    for line in text.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return "this chapter"


def main() -> None:
    """Standardize all numbered chapter files."""
    total_figures = 0
    total_lifecycle = 0
    total_exercises = 0
    for chapter_dir in sorted(CHAPTERS.glob("ch[0-9][0-9]_*")):
        chapter_file = chapter_dir / "chapter.md"
        if not chapter_file.exists():
            print(f"MISSING {chapter_dir.name}/chapter.md")
            continue
        number = chapter_number(chapter_dir)
        text = chapter_file.read_text(encoding="utf-8")
        title = title_from_text(text)
        text, lifecycle_added = insert_lifecycle_anchor(text, number)
        text, figure_count = add_missing_figure_discussions(text)
        text, exercises_added = add_exercises(text, number, title)
        chapter_file.write_text(text, encoding="utf-8")
        total_lifecycle += int(lifecycle_added)
        total_figures += figure_count
        total_exercises += int(exercises_added)
        print(
            f"{chapter_dir.name}: lifecycle_added={lifecycle_added} "
            f"figure_discussions_added={figure_count} exercises_added={exercises_added}"
        )
    print(
        "summary: "
        f"lifecycle_added={total_lifecycle}, "
        f"figure_discussions_added={total_figures}, "
        f"exercise_sections_added={total_exercises}"
    )


if __name__ == "__main__":
    main()