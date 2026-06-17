#!/usr/bin/env python3
"""Check field-development notebooks for executable content and figure cells."""

from __future__ import annotations

import json
from pathlib import Path


BOOK_ROOT = Path(
    "c:/Users/ESOL/Documents/GitHub/neqsim/neqsim-paperlab/books/"
    "tpg4230_field_development_and_operations_2026"
)
TARGETS = [
    "chapters/ch11_field_development_building_blocks/notebooks/ch11_01_digital_twin_and_lifecycle.ipynb",
    "chapters/ch11_field_development_building_blocks/notebooks/ch11_02_concept_evaluation_framework.ipynb",
    "chapters/ch11_field_development_building_blocks/notebooks/ch11_03_screening_and_feasibility.ipynb",
    "chapters/ch13_subsea_surf_systems/notebooks/ch13_02_surf_equipment_design_cost.ipynb",
    "chapters/ch20_production_optimisation/notebooks/ch20_03_cash_flow_engine_and_economics.ipynb",
    "chapters/ch20_production_optimisation/notebooks/ch20_04_network_optimization_gas_lift.ipynb",
    "chapters/ch24_computational_tools_neqsim/notebooks/ch24_02_field_development_api_mastery.ipynb",
]


def main() -> int:
    """Return non-zero if any target notebook has empty code cells or weak figure traceability."""
    failures = 0
    for rel in TARGETS:
        path = BOOK_ROOT / rel
        if not path.exists():
            print(f"MISSING {rel}")
            failures += 1
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        code_cells = [c for c in data.get("cells", []) if c.get("cell_type") == "code"]
        markdown_cells = [c for c in data.get("cells", []) if c.get("cell_type") == "markdown"]
        empty_code = [c for c in code_cells if not "".join(c.get("source") or []).strip()]
        figure_saves = sum("savefig" in "".join(c.get("source") or []) for c in code_cells)
        discussions = sum("**Discussion" in "".join(c.get("source") or []) for c in markdown_cells)
        status = "OK" if not empty_code and figure_saves >= 2 and discussions >= figure_saves else "WARN"
        if status != "OK":
            failures += 1
        print(
            f"{status:4} {rel} | code={len(code_cells)} empty={len(empty_code)} "
            f"figures={figure_saves} discussions={discussions}"
        )
    return failures


if __name__ == "__main__":
    raise SystemExit(main())