"""Extract text from TPG4230 course PDFs into a single notes file."""
from __future__ import annotations
import sys
from pathlib import Path

import fitz  # PyMuPDF

ROOT = Path(r"C:\Users\ESOL\OneDrive - Equinor\NTNU-LT-112664\TPG4230\2026")

TARGETS = [
    ROOT / "exercises" / "ex1" / "Exercise set 01_v2.pdf",
    ROOT / "exercises" / "p2" / "Exercise set 02_v1.pdf",
    ROOT / "exercises" / "p2" / "Snøhvit_Field_Development_Strategy_solution.pdf",
    ROOT / "exercises" / "p2" / "Snøhvit_Production_Strategy.pdf",
    ROOT / "exercises" / "p2" / "solution.pdf",
    ROOT / "exercises" / "p3" / "Exercise_set_03_v1.pdf",
    ROOT / "exercises" / "p3" / "Report.docx",  # will skip if not pdf
    ROOT / "exercises" / "ex2" / "Ultima Thule FD case.pdf",
    ROOT / "exercises" / "ex2" / "Ultima_Thule_Field_Development - Excercise Introduction.pdf",
    ROOT / "exercises" / "ex2" / "Deliverable List for Investment Projects — TPG4230 Field Development.pdf",
    ROOT / "exercises" / "ex2" / "Technical Requirements for Offshore Process Design — TPG4230 Field Development.pdf",
    ROOT / "Abbreviations and Acronyms_.pdf",
]


def extract(p: Path) -> str:
    if not p.exists():
        return f"### MISSING: {p}\n"
    if p.suffix.lower() != ".pdf":
        return f"### SKIP (not pdf): {p.name}\n"
    try:
        doc = fitz.open(str(p))
        chunks = []
        for i, page in enumerate(doc):
            t = page.get_text("text").strip()
            if t:
                chunks.append(f"--- page {i+1} ---\n{t}")
        doc.close()
        body = "\n\n".join(chunks)
        return f"\n\n=========================================\n# {p.name}\n=========================================\n\n{body}\n"
    except Exception as exc:
        return f"### ERROR reading {p.name}: {exc}\n"


def main() -> None:
    out = Path("neqsim-paperlab/books/tpg4230_field_development_and_operations_2026/_course_pdf_extract.md")
    parts = []
    for t in TARGETS:
        print("reading:", t.name)
        parts.append(extract(t))
    out.write_text("\n".join(parts), encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
