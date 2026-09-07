"""Update comp_known_issues.tsv from the last ComponentDatabaseIntegrityTest run.

The gate reports two lists: baseline entries whose underlying defect has been fixed,
and findings not yet in the baseline. This applies both, so the baseline stays an
exact record of what is currently accepted.

Usage:
    python devtools/sync_comp_baseline.py
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "target" / "surefire-reports" / "neqsim.thermo.component.ComponentDatabaseIntegrityTest.txt"
BASELINE = ROOT / "src" / "test" / "resources" / "data" / "comp_known_issues.tsv"

STALE_MARKER = "accepts inconsistencies that no longer exist"
NEW_MARKER = "gained inconsistencies that are not in"

STOP_PREFIXES = ("Run devtools", "Fix the data", "at ", "data/comp_known_issues", "COMP.csv gained")


def extract(text: str, marker: str) -> list:
    for line_number, line in enumerate(text.splitlines()):
        if marker in line:
            collected = []
            for candidate in text.splitlines()[line_number + 1:]:
                stripped = candidate.strip()
                if not stripped or stripped.startswith(STOP_PREFIXES):
                    break
                collected.append(stripped)
            return collected
    return []


def normalise(entry: str) -> str:
    return re.sub(r"\s+", " ", entry.replace("\t", " ")).strip()


def main() -> None:
    if not REPORT.exists():
        raise SystemExit(f"no surefire report at {REPORT}; run the test first")

    text = REPORT.read_text(encoding="utf-8", errors="replace")
    stale = extract(text, STALE_MARKER)
    new = extract(text, NEW_MARKER)
    print(f"stale entries reported : {len(stale)}")
    print(f"new findings reported  : {len(new)}")

    lines = [line for line in BASELINE.read_text(encoding="utf-8").splitlines() if line.strip()]
    print(f"baseline before        : {len(lines)}")

    stale_normalised = {normalise(entry) for entry in stale}
    kept = [line for line in lines if normalise(line) not in stale_normalised]
    removed = len(lines) - len(kept)

    kept_normalised = {normalise(line) for line in kept}
    added = 0
    for finding in new:
        parts = finding.split()
        entry = parts[0] + "\t" + " ".join(parts[1:])
        if normalise(entry) not in kept_normalised:
            kept.append(entry)
            kept_normalised.add(normalise(entry))
            added += 1

    kept = sorted(set(kept))
    BASELINE.write_bytes(("\n".join(kept) + "\n").encode("utf-8"))

    print(f"removed, now fixed     : {removed}")
    print(f"added                  : {added}")
    print(f"baseline after         : {len(kept)}")
    if stale:
        print("\nfixed by this change:")
        for entry in stale[:30]:
            print(f"  {entry}")
    if new:
        print("\nnewly accepted:")
        for entry in new:
            print(f"  {entry}")


if __name__ == "__main__":
    main()
