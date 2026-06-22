import fitz
from pathlib import Path

REF = Path("task_solve/2026-06-22_jsv_inlet_separator_diameter/step1_scope_and_research/references")

# Mechanical datasheet page 3 (process design data), process datasheet relevant pages
for fname, pages in [("07636509.PDF", [2, 3]), ("07316655.PDF", [1, 2])]:
    doc = fitz.open(REF / fname)
    for p in pages:
        if p < doc.page_count:
            print(f"\n===== {fname} page {p+1} =====")
            print(doc[p].get_text())
    doc.close()
