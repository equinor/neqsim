"""
Result Evaluator -- Automated consistency and quality checks for numerical results.

Validates that:
  - Figures and text agree on numerical values
  - Tables in markdown are internally consistent
  - results.json matches claims in the manuscript
  - Key results have proper units, ranges, and significance
  - Cross-references between sections are consistent

Three modes:
  1. Consistency check: numbers in text vs figures vs results.json
  2. LLM result critique: evaluate whether results are scientifically reasonable
  3. Context generator: produce figure discussions, captions, and claim links

Usage::

    from tools.result_evaluator import (
        check_result_consistency,
        evaluate_results_quality,
        generate_figure_context,
    )

    # Check consistency across artifacts
    issues = check_result_consistency("papers/my_paper/")

    # LLM-powered quality assessment
    quality = evaluate_results_quality("papers/my_paper/",
                                       provider="openai", model="gpt-4o")

    # Auto-generate discussion context for figures
    contexts = generate_figure_context("papers/my_paper/",
                                        provider="openai", model="gpt-4o")
"""

import json
import re
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


# ── Data classes ─────────────────────────────────────────────────────────

@dataclass
class ConsistencyIssue:
    """A detected inconsistency in results."""
    severity: str        # CRITICAL, HIGH, MEDIUM, LOW, INFO
    category: str        # number_mismatch, missing_reference, unit_error, range_error
    location: str        # where the issue was found
    message: str
    value_a: Any = None  # value in source A
    value_b: Any = None  # value in source B
    suggestion: str = ""


@dataclass
class ResultQuality:
    """LLM-assessed quality of a result set."""
    completeness_score: int = 0     # 1-5
    reproducibility_score: int = 0  # 1-5
    significance_score: int = 0     # 1-5
    presentation_score: int = 0     # 1-5
    overall_score: float = 0.0
    missing_results: List[str] = field(default_factory=list)
    questionable_values: List[str] = field(default_factory=list)
    suggestions: List[str] = field(default_factory=list)
    raw_response: str = ""


@dataclass
class FigureContext:
    """Auto-generated contextual information for a figure."""
    figure_name: str
    generated_caption: str = ""
    observation: str = ""      # what the figure shows
    mechanism: str = ""        # why it happens
    implication: str = ""      # what it means for design/theory
    recommendation: str = ""   # action to take
    linked_claims: List[str] = field(default_factory=list)
    linked_results: List[str] = field(default_factory=list)
    raw_response: str = ""


@dataclass
class ConsistencyReport:
    """Full consistency check report."""
    paper_dir: str
    issues: List[ConsistencyIssue] = field(default_factory=list)
    numbers_found: int = 0
    tables_checked: int = 0
    figures_checked: int = 0
    results_json_keys: int = 0
    verdict: str = "PENDING"

    def to_dict(self):
        return asdict(self)


# ── Number Extraction ────────────────────────────────────────────────────

_NUMBER_PATTERNS = [
    # Percentages: "15.2%", "15.2 %"
    (r'(\d+\.?\d*)\s*%', "percent"),
    # Temperatures: "25.0 °C", "298.15 K", "-18.5 C"
    (r'(-?\d+\.?\d*)\s*°?[CcKk]\b', "temperature"),
    # Pressures: "60 bar", "5.0 MPa", "100 bara"
    (r'(\d+\.?\d*)\s*(?:bar[ag]?|[MmKk]?[Pp]a|psi|atm)\b', "pressure"),
    # Flows: "100 kg/hr", "50 MMSCFD"
    (r'(\d+\.?\d*)\s*(?:kg/h|kg/s|m3/h|MMSCFD|Sm3/d)', "flow"),
    # Speedups: "2.5×", "2.5x"
    (r'(\d+\.?\d*)\s*[×xX]', "speedup"),
    # Generic decimals with context: "= 0.85", ": 3.14"
    (r'[=:]\s*(-?\d+\.?\d+)', "value"),
    # Iterations: "8 iterations"
    (r'(\d+)\s*iterations?', "iterations"),
    # Times: "1.2 ms", "45 s"
    (r'(\d+\.?\d*)\s*(?:ms|μs|ns|s)\b', "time"),
    # AAD/error: "AAD 0.5%", "RMSE 2.3"
    (r'(?:AAD|RMSE|MAE|MAD|MAPE)\s*[=:]?\s*(\d+\.?\d*)', "error_metric"),
]


def extract_numbers(text: str) -> List[Dict[str, Any]]:
    """Extract all quantitative values from text with context."""
    numbers = []
    for pattern, category in _NUMBER_PATTERNS:
        for m in re.finditer(pattern, text):
            start = max(0, m.start() - 50)
            end = min(len(text), m.end() + 50)
            numbers.append({
                "value": float(m.group(1)),
                "category": category,
                "raw_match": m.group(0),
                "context": text[start:end].strip().replace("\n", " "),
                "position": m.start(),
            })
    return numbers


# ── Table Extraction ─────────────────────────────────────────────────────

def extract_tables(text: str) -> List[Dict[str, Any]]:
    """Extract markdown tables and their contents."""
    tables = []
    # Match markdown tables: | header | header | \n |---|---| \n | data | data |
    table_pattern = re.compile(
        r'(\|[^\n]+\|\n\|[\s:|-]+\|\n(?:\|[^\n]+\|\n?)+)', re.MULTILINE
    )

    for i, m in enumerate(table_pattern.finditer(text)):
        raw = m.group(0)
        rows = raw.strip().split("\n")
        if len(rows) < 3:
            continue

        # Parse header
        headers = [h.strip() for h in rows[0].split("|")[1:-1]]
        # Skip separator row
        data_rows = []
        for row_str in rows[2:]:
            cells = [c.strip() for c in row_str.split("|")[1:-1]]
            if cells:
                data_rows.append(cells)

        # Find numbers in cells
        cell_numbers = []
        for ri, row in enumerate(data_rows):
            for ci, cell in enumerate(row):
                for nm in re.finditer(r'-?\d+\.?\d*', cell):
                    try:
                        cell_numbers.append({
                            "value": float(nm.group()),
                            "row": ri,
                            "col": ci,
                            "header": headers[ci] if ci < len(headers) else f"col{ci}",
                        })
                    except ValueError:
                        pass

        # Detect table caption (line before or **Table N** pattern)
        caption_start = max(0, m.start() - 200)
        preceding = text[caption_start:m.start()]
        caption_match = re.search(
            r'\*\*Table\s+[\d.]+[:\.]?\*\*\s*(.+?)(?:\n|$)', preceding
        )
        caption = caption_match.group(1).strip() if caption_match else ""

        tables.append({
            "index": i,
            "caption": caption,
            "headers": headers,
            "rows": data_rows,
            "numbers": cell_numbers,
            "position": m.start(),
        })

    return tables


# ── Consistency Checking ─────────────────────────────────────────────────

def check_result_consistency(paper_dir) -> ConsistencyReport:
    """Check numerical consistency across manuscript, results.json, and figures.

    Parameters
    ----------
    paper_dir : str or Path
        Path to paper directory.

    Returns
    -------
    ConsistencyReport
    """
    paper_dir = Path(paper_dir)
    report = ConsistencyReport(paper_dir=str(paper_dir))

    # Load manuscript
    paper_file = paper_dir / "paper.md"
    if not paper_file.exists():
        paper_file = paper_dir / "chapter.md"
    if not paper_file.exists():
        report.issues.append(ConsistencyIssue(
            "CRITICAL", "missing_file", str(paper_dir),
            "No paper.md or chapter.md found"
        ))
        report.verdict = "FAIL"
        return report

    text = paper_file.read_text(encoding="utf-8")

    # Extract numbers and tables from text
    text_numbers = extract_numbers(text)
    text_tables = extract_tables(text)
    report.numbers_found = len(text_numbers)
    report.tables_checked = len(text_tables)

    # Load results.json
    results = {}
    results_file = paper_dir / "results.json"
    if results_file.exists():
        try:
            with open(results_file) as f:
                results = json.load(f)
        except json.JSONDecodeError:
            report.issues.append(ConsistencyIssue(
                "HIGH", "parse_error", "results.json",
                "results.json is not valid JSON"
            ))

    key_results = results.get("key_results", {})
    report.results_json_keys = len(key_results)

    # ── Check 1: results.json values appear in text ──
    for key, val in key_results.items():
        if isinstance(val, (int, float)):
            val_str = str(val)
            # Also check rounded versions
            found = (val_str in text or
                     f"{val:.1f}" in text or
                     f"{val:.0f}" in text or
                     str(round(val, 2)) in text)
            if not found:
                report.issues.append(ConsistencyIssue(
                    "MEDIUM", "missing_reference", "text vs results.json",
                    f"Key result '{key}' = {val} not found in manuscript text",
                    value_a=val,
                    suggestion=f"Add the value {val} to the relevant section"
                ))

    # ── Check 2: Internal table consistency ──
    for table in text_tables:
        # Check for sum-to-100 in percentage columns
        for ci, header in enumerate(table["headers"]):
            if any(kw in header.lower() for kw in ["%", "percent", "fraction", "mol"]):
                col_vals = [n["value"] for n in table["numbers"] if n["col"] == ci]
                if col_vals:
                    total = sum(col_vals)
                    if 95 < total < 105 and abs(total - 100) > 0.5:
                        report.issues.append(ConsistencyIssue(
                            "LOW", "sum_mismatch", f"Table '{table['caption'][:50]}'",
                            f"Column '{header}' sums to {total:.1f}% (expect 100%)",
                            value_a=total, value_b=100.0,
                            suggestion="Check if values should sum to 100%"
                        ))

    # ── Check 3: Duplicate/contradictory numbers ──
    # Group numbers by category and look for near-duplicates with different values
    by_category = {}
    for n in text_numbers:
        cat = n["category"]
        by_category.setdefault(cat, []).append(n)

    # Check for the same metric appearing with different values
    for cat, nums in by_category.items():
        if cat in ("value",):
            continue  # Too generic
        seen_values = {}
        for n in nums:
            key = n["context"][:30]
            if key in seen_values:
                prev = seen_values[key]
                if abs(prev - n["value"]) / max(abs(prev), 1e-10) > 0.05:
                    report.issues.append(ConsistencyIssue(
                        "MEDIUM", "number_mismatch",
                        f"Category: {cat}",
                        f"Similar context has different values: "
                        f"{prev} vs {n['value']}",
                        value_a=prev, value_b=n["value"],
                        suggestion="Verify both values are correct"
                    ))
            else:
                seen_values[key] = n["value"]

    # ── Check 4: Figure references ──
    fig_refs = re.findall(r'[Ff]ig(?:ure)?\.?\s*(\d+)', text)
    fig_nums = set(int(n) for n in fig_refs)
    figures_dir = paper_dir / "figures"
    if figures_dir.exists():
        fig_files = sorted(figures_dir.glob("*.png")) + sorted(figures_dir.glob("*.jpg"))
        report.figures_checked = len(fig_files)
        for i in range(1, len(fig_files) + 1):
            if i not in fig_nums:
                report.issues.append(ConsistencyIssue(
                    "MEDIUM", "unreferenced_figure",
                    f"Figure {i}",
                    f"Figure {i} exists in figures/ but is not referenced in text",
                    suggestion=f"Add a reference to Figure {i} or remove the file"
                ))

    # ── Check 5: Table references ──
    table_refs = re.findall(r'[Tt]able\s*(\d+)', text)
    table_nums = set(int(n) for n in table_refs)
    if text_tables:
        for i in range(1, len(text_tables) + 1):
            if i not in table_nums:
                report.issues.append(ConsistencyIssue(
                    "LOW", "unreferenced_table",
                    f"Table {i}",
                    f"Table {i} may not be explicitly referenced in text",
                    suggestion=f"Add a reference to Table {i}"
                ))

    # ── Check 6: Units consistency ──
    # Look for same quantity with different units
    temp_c = re.findall(r'(-?\d+\.?\d*)\s*°?C\b', text)
    temp_k = re.findall(r'(\d+\.?\d*)\s*K\b', text)
    if temp_c and temp_k:
        # Check if any C and K values are inconsistent
        for tc in temp_c[:10]:
            tc_val = float(tc)
            expected_k = tc_val + 273.15
            for tk in temp_k[:10]:
                tk_val = float(tk)
                if abs(tk_val - expected_k) < 1.0 and tk_val != expected_k:
                    report.issues.append(ConsistencyIssue(
                        "LOW", "unit_precision",
                        "Temperature",
                        f"{tc}°C should be {expected_k:.2f} K, found {tk_val} K",
                        value_a=expected_k, value_b=tk_val,
                        suggestion="Use consistent decimal places for temperature conversion"
                    ))

    # Verdict
    critical = sum(1 for i in report.issues if i.severity == "CRITICAL")
    high = sum(1 for i in report.issues if i.severity == "HIGH")
    if critical > 0:
        report.verdict = "FAIL"
    elif high > 0:
        report.verdict = "REVIEW"
    elif report.issues:
        report.verdict = "PASS_WITH_WARNINGS"
    else:
        report.verdict = "PASS"

    return report


# ── LLM Result Quality Evaluation ────────────────────────────────────────

_RESULT_EVALUATION_PROMPT = """You are a senior reviewer of scientific papers in thermodynamics and process engineering.

Evaluate the quality and completeness of these results from a paper/book chapter.

Paper/chapter text (excerpt):
{text_excerpt}

Key results from results.json:
{results_json}

Tables found:
{tables_summary}

Return a JSON object:
{{
  "completeness_score": <1-5>,
  "reproducibility_score": <1-5>,
  "significance_score": <1-5>,
  "presentation_score": <1-5>,
  "missing_results": ["what result is missing1", "..."],
  "questionable_values": ["value that seems wrong1", "..."],
  "suggestions": ["improvement1", "..."]
}}

Scoring:
- completeness: Are all expected results present? Any key metrics missing?
- reproducibility: Could someone reproduce these results from the description?
- significance: Do the results advance understanding or have practical impact?
- presentation: Are results clearly organized with appropriate precision?

Return ONLY valid JSON."""


def evaluate_results_quality(paper_dir, provider="openai", model="gpt-4o",
                             text_limit=3000) -> ResultQuality:
    """Use LLM to evaluate the quality of results in a paper.

    Parameters
    ----------
    paper_dir : str or Path
        Path to paper directory.
    provider : str
        LLM provider.
    model : str
        Model name.
    text_limit : int
        Max characters of manuscript text to send.

    Returns
    -------
    ResultQuality
    """
    paper_dir = Path(paper_dir)
    quality = ResultQuality()

    # Load text
    paper_file = paper_dir / "paper.md"
    if not paper_file.exists():
        paper_file = paper_dir / "chapter.md"
    if not paper_file.exists():
        return quality

    text = paper_file.read_text(encoding="utf-8")

    # Extract results section
    results_section = _extract_section(text, "results")
    if not results_section:
        results_section = text[-text_limit:]
    else:
        results_section = results_section[:text_limit]

    # Load results.json
    results_json_str = "{}"
    results_file = paper_dir / "results.json"
    if results_file.exists():
        try:
            with open(results_file) as f:
                data = json.load(f)
            results_json_str = json.dumps(data.get("key_results", {}), indent=2)
        except (json.JSONDecodeError, OSError):
            pass

    # Tables summary
    tables = extract_tables(text)
    tables_summary = ""
    for t in tables[:5]:
        tables_summary += f"Table: {t['caption'][:80]}\n"
        tables_summary += f"  Headers: {t['headers']}\n"
        tables_summary += f"  Rows: {len(t['rows'])}\n\n"

    prompt = _RESULT_EVALUATION_PROMPT.format(
        text_excerpt=results_section,
        results_json=results_json_str,
        tables_summary=tables_summary or "No tables found."
    )

    # Call LLM (text-only, no vision needed)
    response = _call_llm_text(prompt, provider, model)
    if not response:
        return quality

    quality.raw_response = response

    try:
        cleaned = re.sub(r'^```(?:json)?\s*', '', response.strip())
        cleaned = re.sub(r'\s*```$', '', cleaned)
        data = json.loads(cleaned)

        quality.completeness_score = int(data.get("completeness_score", 0))
        quality.reproducibility_score = int(data.get("reproducibility_score", 0))
        quality.significance_score = int(data.get("significance_score", 0))
        quality.presentation_score = int(data.get("presentation_score", 0))
        quality.missing_results = data.get("missing_results", [])
        quality.questionable_values = data.get("questionable_values", [])
        quality.suggestions = data.get("suggestions", [])

        scores = [quality.completeness_score, quality.reproducibility_score,
                  quality.significance_score, quality.presentation_score]
        quality.overall_score = round(sum(scores) / 4, 2) if all(scores) else 0

    except (json.JSONDecodeError, ValueError, TypeError):
        pass

    return quality


def _extract_section(text: str, section_name: str) -> str:
    """Extract a named section from markdown text."""
    pattern = rf'##\s*\d*\.?\d*\s*{section_name}'
    match = re.search(pattern, text, re.IGNORECASE)
    if not match:
        return ""
    next_section = re.search(r'\n##\s', text[match.end():])
    end = match.end() + next_section.start() if next_section else len(text)
    return text[match.start():end]


# ── LLM Context Generation for Figures ───────────────────────────────────

_FIGURE_CONTEXT_PROMPT = """You are a scientific writer for a thermodynamics and process engineering textbook/paper.

For this figure, generate contextual discussion that connects it to the broader narrative.

Figure: {figure_name}
Caption: {caption}
Surrounding text: {surrounding_text}

Key results: {key_results}

Return a JSON object:
{{
  "generated_caption": "Concise 1-2 sentence caption if the existing one is weak",
  "observation": "What the figure shows, with specific numbers from the data",
  "mechanism": "Physical/chemical explanation of why the results look this way",
  "implication": "What this means for engineering design or scientific understanding",
  "recommendation": "Specific actionable recommendation based on this figure",
  "linked_claims": ["claim1 from the text this figure supports", "claim2"],
  "linked_results": ["key_result_name1", "key_result_name2"]
}}

Write for an expert audience. Be specific with numbers. Keep each field to 2-3 sentences.
Return ONLY valid JSON."""


def generate_figure_context(paper_dir, provider="openai", model="gpt-4o",
                            use_vision=True) -> List[FigureContext]:
    """Auto-generate discussion context for each figure in a paper.

    Parameters
    ----------
    paper_dir : str or Path
        Path to paper directory.
    provider : str
        LLM provider.
    model : str
        Model name.
    use_vision : bool
        If True, also send the figure image to the LLM for vision analysis.

    Returns
    -------
    list of FigureContext
    """
    paper_dir = Path(paper_dir)
    contexts = []

    # Load manuscript
    paper_file = paper_dir / "paper.md"
    if not paper_file.exists():
        paper_file = paper_dir / "chapter.md"
    if not paper_file.exists():
        return contexts

    text = paper_file.read_text(encoding="utf-8")

    # Load captions and results
    captions = {}
    results_file = paper_dir / "results.json"
    key_results = {}
    if results_file.exists():
        try:
            with open(results_file) as f:
                data = json.load(f)
            captions = data.get("figure_captions", {})
            key_results = data.get("key_results", {})
        except (json.JSONDecodeError, OSError):
            pass

    # Find figures
    figures_dir = paper_dir / "figures"
    if not figures_dir.exists():
        return contexts

    fig_files = sorted(
        list(figures_dir.glob("*.png")) +
        list(figures_dir.glob("*.jpg")) +
        list(figures_dir.glob("*.jpeg"))
    )

    for fig_path in fig_files:
        caption = captions.get(fig_path.name, "")

        # Find surrounding text for this figure
        surrounding = _find_figure_context_in_text(text, fig_path.name)

        prompt = _FIGURE_CONTEXT_PROMPT.format(
            figure_name=fig_path.name,
            caption=caption or "No caption available",
            surrounding_text=surrounding[:1500],
            key_results=json.dumps(key_results, indent=2)[:1000]
        )

        # Choose vision or text-only
        if use_vision and fig_path.suffix.lower() in (".png", ".jpg", ".jpeg"):
            from tools.figure_evaluator import _call_llm_vision
            response = _call_llm_vision(fig_path, prompt, provider, model)
        else:
            response = _call_llm_text(prompt, provider, model)

        ctx = FigureContext(figure_name=fig_path.name)
        if response and not response.startswith("LLM_ERROR"):
            ctx.raw_response = response
            try:
                cleaned = re.sub(r'^```(?:json)?\s*', '', response.strip())
                cleaned = re.sub(r'\s*```$', '', cleaned)
                data = json.loads(cleaned)

                ctx.generated_caption = data.get("generated_caption", "")
                ctx.observation = data.get("observation", "")
                ctx.mechanism = data.get("mechanism", "")
                ctx.implication = data.get("implication", "")
                ctx.recommendation = data.get("recommendation", "")
                ctx.linked_claims = data.get("linked_claims", [])
                ctx.linked_results = data.get("linked_results", [])
            except (json.JSONDecodeError, ValueError):
                pass

        contexts.append(ctx)

    return contexts


def _find_figure_context_in_text(text: str, figure_name: str) -> str:
    """Find text surrounding a figure reference."""
    # Try to find the figure reference
    stem = Path(figure_name).stem
    # Look for ![...](figures/name) or references like "Figure 1"
    patterns = [
        re.escape(figure_name),
        re.escape(stem),
    ]

    for pat in patterns:
        match = re.search(pat, text)
        if match:
            start = max(0, match.start() - 500)
            end = min(len(text), match.end() + 500)
            return text[start:end]

    return ""


# ── Text-only LLM calls ─────────────────────────────────────────────────

def _call_llm_text(prompt: str, provider: str = "openai",
                   model: str = "gpt-4o") -> Optional[str]:
    """Call an LLM with text-only prompt.

    Supports: litellm, openai, anthropic.
    """
    try:
        _HAS_LITELLM = False
        _HAS_OPENAI = False
        _HAS_ANTHROPIC = False
        try:
            import litellm
            _HAS_LITELLM = True
        except ImportError:
            pass
        try:
            import openai as _openai_mod
            _HAS_OPENAI = True
        except ImportError:
            pass
        try:
            import anthropic as _anthropic_mod
            _HAS_ANTHROPIC = True
        except ImportError:
            pass

        messages = [{"role": "user", "content": prompt}]

        if provider == "litellm" and _HAS_LITELLM:
            response = litellm.completion(
                model=model, messages=messages,
                max_tokens=2000, temperature=0.1
            )
            return response.choices[0].message.content

        elif provider == "openai" and _HAS_OPENAI:
            client = _openai_mod.OpenAI()
            response = client.chat.completions.create(
                model=model, messages=messages,
                max_tokens=2000, temperature=0.1
            )
            return response.choices[0].message.content

        elif provider == "anthropic" and _HAS_ANTHROPIC:
            client = _anthropic_mod.Anthropic()
            response = client.messages.create(
                model=model, max_tokens=2000,
                messages=[{"role": "user", "content": prompt}]
            )
            return response.content[0].text

        return None

    except Exception as e:
        return f"LLM_ERROR: {e}"


# ── Report Output ────────────────────────────────────────────────────────

def print_consistency_report(report: ConsistencyReport):
    """Print formatted consistency report."""
    print(f"Result Consistency Report -- {report.paper_dir}")
    print(f"Numbers: {report.numbers_found} | Tables: {report.tables_checked} "
          f"| Figures: {report.figures_checked} | Results.json keys: {report.results_json_keys}")
    print(f"Verdict: {report.verdict}")
    print("=" * 70)

    by_severity = {"CRITICAL": [], "HIGH": [], "MEDIUM": [], "LOW": [], "INFO": []}
    for issue in report.issues:
        by_severity[issue.severity].append(issue)

    for sev in ["CRITICAL", "HIGH", "MEDIUM", "LOW"]:
        issues = by_severity[sev]
        if issues:
            icon = {"CRITICAL": "[CRIT]", "HIGH": "[HIGH]", "MEDIUM": "[WARN]", "LOW": "[LOW]"}[sev]
            print(f"\n  [{sev}] ({len(issues)})")
            for issue in issues:
                print(f"    {icon} [{issue.category}] {issue.message}")
                if issue.suggestion:
                    print(f"       → {issue.suggestion}")

    if not report.issues:
        print("\n  [OK] No consistency issues found.")

    print()


def print_quality_report(quality: ResultQuality):
    """Print formatted quality assessment."""
    if quality.overall_score == 0:
        print("Result quality assessment: not available (no LLM response)")
        return

    print("Result Quality Assessment (LLM)")
    print("=" * 50)
    print(f"  Completeness:    {quality.completeness_score}/5")
    print(f"  Reproducibility: {quality.reproducibility_score}/5")
    print(f"  Significance:    {quality.significance_score}/5")
    print(f"  Presentation:    {quality.presentation_score}/5")
    print(f"  Overall:         {quality.overall_score}/5")

    if quality.missing_results:
        print("\n  Missing results:")
        for m in quality.missing_results:
            print(f"    - {m}")

    if quality.questionable_values:
        print("\n  Questionable values:")
        for q in quality.questionable_values:
            print(f"    [?] {q}")

    if quality.suggestions:
        print("\n  Suggestions:")
        for s in quality.suggestions:
            print(f"    → {s}")

    print()


def save_consistency_report(report: ConsistencyReport, output_path):
    """Save consistency report to JSON."""
    output_path = Path(output_path)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report.to_dict(), f, indent=2, default=str)
