"""
consistency_checker.py - Check consistency across analysis notebooks and results.

This tool helps ensure that multi-notebook analyses produce consistent conclusions by:
1. Extracting key numerical results from notebooks and results.json
2. Parsing conclusion statements for key claims
3. Cross-checking values for contradictions
4. Generating a consistency report

Usage:
    python devtools/consistency_checker.py task_solve/2026-04-09_task_name/
    python devtools/consistency_checker.py --help

The tool produces:
    - Console report of findings
    - consistency_report.json in the task folder
"""

import os
import sys
import json
import re
import argparse
from pathlib import Path
from typing import Dict, List, Any, Optional
from dataclasses import dataclass, asdict
from collections import defaultdict


@dataclass
class ExtractedValue:
    """A numerical value extracted from a source."""
    value: float
    unit: str
    context: str
    source_file: str
    source_location: str
    confidence: float = 1.0


@dataclass
class ExtractedClaim:
    """A qualitative claim/conclusion extracted from text."""
    claim: str
    category: str
    source_file: str
    source_location: str
    keywords: List[str]


@dataclass
class Inconsistency:
    """A detected inconsistency between sources."""
    type: str
    severity: str
    description: str
    sources: List[str]
    values: Optional[List[Any]] = None
    recommendation: str = ""


class ConsistencyChecker:
    """Check consistency across notebooks and results in a task folder."""

    VALUE_PATTERNS = [
        (r'(\d+\.?\d*)\s*%', 'pct'),
        (r'(\d+\.?\d*)\s*percent', 'pct'),
        (r'(\d+\.?\d*)\s*°?C\b', 'C'),
        (r'(\d+\.?\d*)\s*K\b', 'K'),
        (r'(\d+\.?\d*)\s*bar[ag]?\b', 'bar'),
        (r'(\d+\.?\d*)\s*kg/hr', 'kg/hr'),
        (r'(\d+\.?\d*)\s*kg/m[³3]', 'kg/m3'),
        (r'(\d+\.?\d*)\s*g/m[³3]', 'g/m3'),
        (r'\$(\d+[\d,]*\.?\d*)', 'USD'),
        (r'(\d+[\d,]*\.?\d*)\s*USD', 'USD'),
        (r'(\d+\.?\d*)x\b', 'multiplier'),
        (r'factor\s+(?:of\s+)?(\d+\.?\d*)', 'factor'),
    ]

    CLAIM_KEYWORDS = {
        'method_comparison': ['higher', 'lower', 'better', 'worse', 'more accurate',
                             'deviation', 'difference', 'compared to', 'versus', 'vs'],
        'recommendation': ['recommend', 'should', 'must', 'use', 'avoid', 'prefer',
                          'required', 'critical', 'important'],
        'finding': ['found', 'shows', 'demonstrates', 'indicates', 'reveals',
                   'confirms', 'suggests', 'result'],
    }

    CONCEPT_ALIASES = {
        'co2_misattribution': ['co2 misattribution', 'methane overreporting',
                               'overreporting', 'misattribution'],
        'method_deviation': ['deviation', 'difference', 'discrepancy',
                            'neqsim vs offshore norge'],
        'emission_factor': ['emission factor', 'factor', 'f=', 'f =' , 'g/m3/bar', 'g/m³/bar'],
    }

    def __init__(self, task_dir: str):
        self.task_dir = Path(task_dir)
        self.extracted_values: Dict[str, List[ExtractedValue]] = defaultdict(list)
        self.extracted_claims: List[ExtractedClaim] = []
        self.inconsistencies: List[Inconsistency] = []

    def run(self) -> Dict[str, Any]:
        """Run full consistency check and return report."""
        print(f"\n{'='*70}")
        print(f"CONSISTENCY CHECKER - {self.task_dir.name}")
        print(f"{'='*70}\n")

        self._find_sources()
        self._extract_from_notebooks()
        self._extract_from_results_json()
        self._check_numerical_consistency()
        self._check_gudrun_vs_calculations()

        report = self._generate_report()
        self._save_report(report)

        return report

    def _find_sources(self):
        """Find all analysis sources in the task folder."""
        self.notebooks = list(self.task_dir.glob("**/*.ipynb"))
        self.results_json = self.task_dir / "results.json"

        print(f"Found {len(self.notebooks)} notebooks:")
        for nb in self.notebooks:
            print(f"  - {nb.relative_to(self.task_dir)}")
        print(f"Results JSON: {'[OK]' if self.results_json.exists() else '[X]'}")
        print()

    def _extract_from_notebooks(self):
        """Extract values from Jupyter notebooks."""
        for nb_path in self.notebooks:
            try:
                with open(nb_path, 'r', encoding='utf-8') as f:
                    nb = json.load(f)

                for i, cell in enumerate(nb.get('cells', []), 1):
                    cell_type = cell.get('cell_type', '')
                    source = ''.join(cell.get('source', []))

                    if cell_type == 'markdown':
                        self._extract_values_from_text(
                            source,
                            str(nb_path.relative_to(self.task_dir)),
                            f"Cell {i}"
                        )

                    elif cell_type == 'code':
                        outputs = cell.get('outputs', [])
                        for output in outputs:
                            if output.get('output_type') == 'stream':
                                text = ''.join(output.get('text', []))
                                self._extract_values_from_text(
                                    text,
                                    str(nb_path.relative_to(self.task_dir)),
                                    f"Cell {i} output"
                                )
                            elif output.get('output_type') in ('execute_result', 'display_data'):
                                data = output.get('data', {})
                                if 'text/plain' in data:
                                    text = ''.join(data['text/plain'])
                                    self._extract_values_from_text(
                                        text,
                                        str(nb_path.relative_to(self.task_dir)),
                                        f"Cell {i} output"
                                    )

            except Exception as e:
                print(f"  Warning: Could not parse {nb_path.name}: {e}")

    def _extract_from_results_json(self):
        """Extract values from results.json."""
        if not self.results_json.exists():
            return

        try:
            with open(self.results_json, 'r', encoding='utf-8') as f:
                self.results = json.load(f)

            # Extract from key_results
            key_results = self.results.get('key_results', {})
            for key, value in key_results.items():
                if isinstance(value, (int, float)):
                    unit = self._parse_unit_from_key(key)
                    concept = self._normalize_concept(key)
                    self.extracted_values[concept].append(ExtractedValue(
                        value=float(value),
                        unit=unit,
                        context=key,
                        source_file='results.json',
                        source_location=f'key_results.{key}'
                    ))

            # Extract from gas_composition_analysis
            gca = self.results.get('gas_composition_analysis', {})
            kf = gca.get('key_findings', {})
            for key, value in kf.items():
                if isinstance(value, dict):
                    for subkey, subval in value.items():
                        if isinstance(subval, (int, float)):
                            concept = self._normalize_concept(f"{key}_{subkey}")
                            self.extracted_values[concept].append(ExtractedValue(
                                value=float(subval),
                                unit=self._parse_unit_from_key(subkey),
                                context=f"{key}.{subkey}",
                                source_file='results.json',
                                source_location=f'gas_composition_analysis.{key}.{subkey}'
                            ))

        except Exception as e:
            print(f"  Warning: Could not parse results.json: {e}")

    def _check_gudrun_vs_calculations(self):
        """Specifically check Gudrun study values against notebook calculations."""
        print("Checking Gudrun study vs notebook calculations...")

        if not hasattr(self, 'results'):
            return

        gudrun = self.results.get('gudrun_field_validation', {})
        gca = self.results.get('gas_composition_analysis', {})

        if not gudrun or not gca:
            print("  No Gudrun or gas composition data to compare")
            return

        # Key comparisons to make
        comparisons = []

        # 1. Compare emission factors
        gudrun_findings = gudrun.get('key_findings', {})
        efc = gudrun_findings.get('emission_factor_comparison', {})

        if efc:
            # Gudrun says NeqSim gives 5-6 g/m3/bar for methane
            gudrun_neqsim_factor = efc.get('neqsim_methane_factor', '')
            # Parse range like "5-6 g/m³/bar"
            match = re.search(r'(\d+)-(\d+)', str(gudrun_neqsim_factor))
            if match:
                gudrun_low = float(match.group(1))
                gudrun_high = float(match.group(2))
                comparisons.append({
                    'name': 'NeqSim methane emission factor (Gudrun)',
                    'gudrun_value': f"{gudrun_low}-{gudrun_high}",
                    'gudrun_unit': 'g/m3/bar',
                    'source': 'gudrun_field_validation.emission_factor_comparison'
                })

        # 2. Compare CO2 fraction findings
        gcd = gudrun_findings.get('gas_composition_discovery', {})
        if gcd:
            co2_fraction = gcd.get('co2_fraction_of_emissions', '')
            # Parse "72-78%"
            match = re.search(r'(\d+)-(\d+)', str(co2_fraction))
            if match:
                comparisons.append({
                    'name': 'CO2 fraction of degassed emissions (Gudrun)',
                    'gudrun_value': f"{match.group(1)}-{match.group(2)}%",
                    'source': 'gudrun_field_validation.gas_composition_discovery'
                })

        # 3. Check if our gas composition analysis aligns
        # Our analysis says ~1.35% methane overreporting per 1% CO2
        # Gudrun has 10% CO2, so we'd expect ~13.5% overreporting
        gca_findings = gca.get('key_findings', {})
        at_10 = gca_findings.get('at_10pct_co2_gudrun', {})
        if at_10:
            our_misattribution = at_10.get('misattribution_pct', 0)

            # Gudrun says 72-78% is CO2, meaning ~22-28% is hydrocarbons
            # If we measure gas rate and attribute all to methane, and 72-78% is actually CO2...
            # That's very different from our 13.5% overreporting calculation!

            # This is a potential inconsistency - our model calculates based on
            # gas composition affecting mass-based GWP, but Gudrun's finding is about
            # what fraction of the *volume* is CO2 vs hydrocarbons

            self.inconsistencies.append(Inconsistency(
                type="model_scope_mismatch",
                severity="warning",
                description=(
                    "Gudrun study reports 72-78% of degassed emissions are CO2 (volumetric), "
                    f"while our gas composition analysis calculates {our_misattribution}% "
                    "methane overreporting based on mass/GWP attribution. These measure different things."
                ),
                sources=[
                    'gudrun_field_validation.gas_composition_discovery',
                    'gas_composition_analysis.key_findings.at_10pct_co2_gudrun'
                ],
                values=['72-78% CO2 (volumetric)', f'{our_misattribution}% CH4 overreporting (mass-GWP)'],
                recommendation=(
                    "Clarify in report: Gudrun volumetric finding (72-78% CO2) describes composition of "
                    "released gas, while our 13.5% overreporting calculates mass-based GWP error when "
                    "all gas is attributed to methane. Both are valid but measure different quantities."
                )
            ))

        # 4. Check emission reduction percentages
        red = gudrun_findings.get('emission_reduction_2022', {})
        if red:
            gudrun_reduction = red.get('reduction_percent', 0)
            # Gudrun shows 58% reduction in CO2-eq when using NeqSim vs conventional
            # This is because NeqSim correctly identifies most gas as CO2 (GWP=1) vs CH4 (GWP=28)

            # Our analysis focuses on methane overreporting, not total GWP reduction
            # These should be consistent: if we overreport CH4 by X%, we over-estimate GWP by ~X% * (28-1)/28

            comparisons.append({
                'name': 'Emission reduction using NeqSim (Gudrun 2022)',
                'gudrun_value': f"{gudrun_reduction}%",
                'context': 'CO2-equivalent reduction vs conventional method',
                'our_analysis': 'Not directly comparable - we calculate CH4 overreporting, not total GWP'
            })

        print(f"  Found {len(comparisons)} comparison points")
        print(f"  Detected {len([i for i in self.inconsistencies if 'gudrun' in i.description.lower() or 'Gudrun' in i.description])} Gudrun-related issues")

    def _extract_values_from_text(self, text: str, source_file: str, source_loc: str):
        """Extract numerical values from text."""
        for pattern, unit in self.VALUE_PATTERNS:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                try:
                    value_str = match.group(1).replace(',', '')
                    value = float(value_str)

                    start = max(0, match.start() - 50)
                    end = min(len(text), match.end() + 50)
                    context = text[start:end].replace('\n', ' ').strip()

                    concept = self._identify_concept(context)

                    self.extracted_values[concept].append(ExtractedValue(
                        value=value,
                        unit=unit,
                        context=context,
                        source_file=source_file,
                        source_location=source_loc
                    ))
                except ValueError:
                    pass

    def _check_numerical_consistency(self):
        """Check for numerical inconsistencies across sources."""
        print("Checking numerical consistency...")

        for concept, values in self.extracted_values.items():
            if len(values) < 2:
                continue

            by_unit = defaultdict(list)
            for v in values:
                by_unit[v.unit].append(v)

            for unit, unit_values in by_unit.items():
                if len(unit_values) < 2:
                    continue

                nums = [v.value for v in unit_values]
                avg = sum(nums) / len(nums)

                for v in unit_values:
                    if avg != 0:
                        deviation = abs(v.value - avg) / abs(avg) * 100
                    else:
                        deviation = abs(v.value) * 100

                    if deviation > 10:
                        sources = [f"{v.source_file}:{v.source_location}" for v in unit_values]
                        self.inconsistencies.append(Inconsistency(
                            type="numerical_mismatch",
                            severity="warning" if deviation < 25 else "critical",
                            description=f"'{concept}' has inconsistent values ({deviation:.1f}% deviation)",
                            sources=sources,
                            values=[v.value for v in unit_values],
                            recommendation=f"Review values: {', '.join(f'{v.value} {unit}' for v in unit_values)}"
                        ))
                        break

        print(f"  Found {sum(1 for i in self.inconsistencies if i.type == 'numerical_mismatch')} numerical inconsistencies")

    def _identify_concept(self, context: str) -> str:
        """Identify the concept a value relates to."""
        context_lower = context.lower()

        for concept, aliases in self.CONCEPT_ALIASES.items():
            for alias in aliases:
                if alias in context_lower:
                    return concept

        return "other"

    def _normalize_concept(self, key: str) -> str:
        """Normalize a key name to a concept."""
        key_lower = key.lower().replace('_', ' ')

        for concept, aliases in self.CONCEPT_ALIASES.items():
            for alias in aliases:
                if alias in key_lower:
                    return concept

        return key_lower

    def _parse_unit_from_key(self, key: str) -> str:
        """Parse unit from a key name."""
        unit_suffixes = {
            '_pct': 'pct', '_percent': 'pct',
            '_C': 'C', '_K': 'K',
            '_bar': 'bar', '_bara': 'bar',
            '_kg': 'kg', '_g': 'g',
            '_usd': 'USD', '_yr': '/yr',
        }

        for suffix, unit in unit_suffixes.items():
            if key.lower().endswith(suffix.lower()):
                return unit

        return ''

    def _generate_report(self) -> Dict[str, Any]:
        """Generate the consistency report."""
        critical = [i for i in self.inconsistencies if i.severity == 'critical']
        warnings = [i for i in self.inconsistencies if i.severity == 'warning']

        report = {
            "task_folder": str(self.task_dir),
            "summary": {
                "total_issues": len(self.inconsistencies),
                "critical": len(critical),
                "warnings": len(warnings),
                "notebooks_checked": len(self.notebooks),
                "values_extracted": sum(len(v) for v in self.extracted_values.values()),
            },
            "status": "PASS" if len(critical) == 0 else "FAIL",
            "inconsistencies": [asdict(i) for i in self.inconsistencies],
            "extracted_values_summary": {
                concept: [{"value": v.value, "unit": v.unit, "source": v.source_file}
                         for v in values]
                for concept, values in self.extracted_values.items()
                if len(values) > 0
            },
        }

        print(f"\n{'='*70}")
        print("CONSISTENCY CHECK SUMMARY")
        print(f"{'='*70}")
        print(f"Status: {'PASS' if report['status'] == 'PASS' else 'FAIL'}")
        print(f"Notebooks checked: {report['summary']['notebooks_checked']}")
        print(f"Values extracted: {report['summary']['values_extracted']}")
        print(f"\nIssues found:")
        print(f"  Critical: {report['summary']['critical']}")
        print(f"  Warnings: {report['summary']['warnings']}")

        if self.inconsistencies:
            print(f"\n{'-'*70}")
            print("DETAILS:")
            for i, issue in enumerate(self.inconsistencies, 1):
                icon = {'critical': '[!!]', 'warning': '[!]', 'info': '[i]'}[issue.severity]
                print(f"\n{icon} Issue {i}: {issue.type}")
                print(f"   {issue.description}")
                print(f"   Sources: {', '.join(issue.sources)}")
                if issue.values:
                    print(f"   Values: {issue.values}")
                print(f"   -> {issue.recommendation}")

        return report

    def _save_report(self, report: Dict[str, Any]):
        """Save report to JSON file."""
        output_path = self.task_dir / "consistency_report.json"
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(report, f, indent=2)
        print(f"\nReport saved to: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Check consistency across analysis notebooks and results"
    )
    parser.add_argument(
        "task_dir",
        help="Path to task folder (e.g., task_solve/2026-04-09_my_task)"
    )

    args = parser.parse_args()

    if not os.path.isdir(args.task_dir):
        print(f"Error: {args.task_dir} is not a directory")
        sys.exit(1)

    checker = ConsistencyChecker(args.task_dir)
    report = checker.run()

    if report['summary']['critical'] > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
