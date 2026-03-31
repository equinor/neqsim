"""
Claim Tracer — Ensures every quantitative claim in a manuscript is backed
by evidence from benchmark results and approved by the validation agent.

Usage:
    python claim_tracer.py audit papers/tpflash_algorithms_2026/
    python claim_tracer.py report papers/tpflash_algorithms_2026/
"""

import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional
from dataclasses import dataclass, asdict


@dataclass
class Claim:
    """A quantitative claim found in the manuscript."""
    claim_id: str
    text: str
    location: str
    number_value: Optional[float]
    unit: Optional[str]
    linked_evidence: Optional[str]
    status: str  # LINKED, UNLINKED, REJECTED


def extract_numbers_from_text(text):
    """Find all numbers in text (percentages, iterations, times, etc.)."""
    # Match patterns like "15.2%", "8 iterations", "1.2 ms", "0.0012"
    patterns = [
        r'(\d+\.?\d*)\s*%',           # percentages
        r'(\d+\.?\d*)\s*iterations?',  # iteration counts
        r'(\d+\.?\d*)\s*ms',           # milliseconds
        r'p\s*[<=]\s*(\d+\.?\d*)',     # p-values
        r'(\d+\.?\d*)\s*bar',          # pressures
        r'(\d+\.?\d*)\s*K\b',          # temperatures
    ]
    numbers = []
    for pattern in patterns:
        matches = re.finditer(pattern, text, re.IGNORECASE)
        for m in matches:
            numbers.append({
                "value": float(m.group(1)),
                "context": text[max(0, m.start()-30):m.end()+30].strip(),
                "pattern": pattern,
            })
    return numbers


def find_claim_references(text):
    """Find [Claim C1] style references in text."""
    pattern = r'\[Claim\s+(C\d+)[^\]]*\]'
    return re.findall(pattern, text)


def audit_manuscript(paper_dir):
    """Audit a manuscript for unsupported claims.

    Args:
        paper_dir: Path to the paper directory

    Returns:
        Audit report dict
    """
    paper_dir = Path(paper_dir)

    # Load manuscript
    paper_file = paper_dir / "paper.md"
    if not paper_file.exists():
        return {"error": f"No paper.md found in {paper_dir}"}

    paper_text = paper_file.read_text(encoding="utf-8")

    # Load approved claims
    claims_file = paper_dir / "approved_claims.json"
    approved_claims = {}
    if claims_file.exists():
        with open(claims_file) as f:
            data = json.load(f)
            for claim in data.get("claims", []):
                approved_claims[claim["claim_id"]] = claim

    # Load claims manifest
    manifest_file = paper_dir / "claims_manifest.json"
    manifest_claims = {}
    if manifest_file.exists():
        with open(manifest_file) as f:
            data = json.load(f)
            for claim in data.get("claims", []):
                manifest_claims[claim["claim_id"]] = claim

    # Find all numbers in manuscript
    numbers_found = extract_numbers_from_text(paper_text)

    # Find all claim references
    claim_refs = find_claim_references(paper_text)

    # Check each referenced claim is approved
    issues = []
    for ref in claim_refs:
        if ref not in approved_claims:
            issues.append({
                "type": "UNLINKED_CLAIM",
                "severity": "HIGH",
                "claim_id": ref,
                "message": f"Claim {ref} referenced in text but not in approved_claims.json",
            })
        elif approved_claims[ref].get("status") == "REJECTED":
            issues.append({
                "type": "REJECTED_CLAIM_USED",
                "severity": "CRITICAL",
                "claim_id": ref,
                "message": f"Claim {ref} was REJECTED but is still in the manuscript",
            })
        elif approved_claims[ref].get("status") == "INSUFFICIENT_EVIDENCE":
            issues.append({
                "type": "INSUFFICIENT_CLAIM_USED",
                "severity": "HIGH",
                "claim_id": ref,
                "message": f"Claim {ref} has INSUFFICIENT_EVIDENCE but is in the manuscript",
            })

    # Check for numbers not linked to any claim
    # This is heuristic — not every number needs a claim reference,
    # but numbers in Results/Discussion sections should
    sections_needing_claims = ["results", "discussion", "abstract"]
    for section in sections_needing_claims:
        # Simple section detection
        section_pattern = rf'##\s*\d*\.?\d*\s*{section}'
        section_match = re.search(section_pattern, paper_text, re.IGNORECASE)
        if section_match:
            # Find next section header
            next_section = re.search(r'\n##\s', paper_text[section_match.end():])
            end = section_match.end() + next_section.start() if next_section else len(paper_text)
            section_text = paper_text[section_match.start():end]

            section_numbers = extract_numbers_from_text(section_text)
            section_refs = find_claim_references(section_text)

            if section_numbers and not section_refs:
                issues.append({
                    "type": "UNLINKED_NUMBERS",
                    "severity": "MEDIUM",
                    "section": section,
                    "message": (
                        f"Section '{section}' contains {len(section_numbers)} "
                        f"quantitative statements but no [Claim Cx] references"
                    ),
                    "numbers": section_numbers[:5],  # First 5 examples
                })

    # Check approved claims that are NOT referenced in the manuscript
    for claim_id, claim in approved_claims.items():
        if claim.get("status") == "APPROVED" and claim_id not in claim_refs:
            issues.append({
                "type": "UNUSED_APPROVED_CLAIM",
                "severity": "LOW",
                "claim_id": claim_id,
                "message": f"Claim {claim_id} is APPROVED but not referenced in manuscript",
            })

    report = {
        "paper_dir": str(paper_dir),
        "manuscript_exists": True,
        "approved_claims_count": len(approved_claims),
        "claim_references_in_text": len(claim_refs),
        "unique_claims_referenced": len(set(claim_refs)),
        "numbers_in_text": len(numbers_found),
        "issues": issues,
        "issues_by_severity": {
            "CRITICAL": len([i for i in issues if i["severity"] == "CRITICAL"]),
            "HIGH": len([i for i in issues if i["severity"] == "HIGH"]),
            "MEDIUM": len([i for i in issues if i["severity"] == "MEDIUM"]),
            "LOW": len([i for i in issues if i["severity"] == "LOW"]),
        },
        "verdict": "PASS" if not any(
            i["severity"] in ("CRITICAL", "HIGH") for i in issues
        ) else "FAIL",
    }

    return report


def print_report(report):
    """Pretty-print an audit report."""
    print("=" * 60)
    print("CLAIM TRACER AUDIT REPORT")
    print("=" * 60)
    print(f"Paper: {report['paper_dir']}")
    print(f"Verdict: {report['verdict']}")
    print()
    print(f"Approved claims: {report['approved_claims_count']}")
    print(f"Claims referenced in text: {report['unique_claims_referenced']}")
    print(f"Quantitative statements: {report['numbers_in_text']}")
    print()

    if report["issues"]:
        print(f"Issues found: {len(report['issues'])}")
        for sev in ["CRITICAL", "HIGH", "MEDIUM", "LOW"]:
            count = report["issues_by_severity"][sev]
            if count > 0:
                print(f"  {sev}: {count}")
        print()

        for issue in report["issues"]:
            marker = {"CRITICAL": "!!!", "HIGH": "!!", "MEDIUM": "!", "LOW": "."}
            print(f"  [{issue['severity']:8s}] {marker[issue['severity']]} {issue['message']}")
    else:
        print("No issues found. All claims are properly linked.")

    print("=" * 60)


def main():
    if len(sys.argv) < 3:
        print("Usage: python claim_tracer.py <audit|report> <paper_dir>")
        sys.exit(1)

    command = sys.argv[1]
    paper_dir = sys.argv[2]

    report = audit_manuscript(paper_dir)

    if command == "audit":
        print_report(report)
        sys.exit(0 if report["verdict"] == "PASS" else 1)

    elif command == "report":
        print(json.dumps(report, indent=2))

    else:
        print(f"Unknown command: {command}")
        sys.exit(1)


if __name__ == "__main__":
    main()
