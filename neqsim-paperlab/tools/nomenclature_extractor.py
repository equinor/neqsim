"""
Nomenclature Extractor — Scans a manuscript for LaTeX symbols and math notation,
then generates a sorted nomenclature table for inclusion in the paper.

Usage::

    from tools.nomenclature_extractor import extract_nomenclature, print_nomenclature_report

    report = extract_nomenclature("papers/my_paper/paper.md")
    print_nomenclature_report(report)
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# Common symbols in thermodynamics/process engineering with their descriptions
_KNOWN_SYMBOLS = {
    # Thermodynamic properties
    "T": ("Temperature", "K"),
    "P": ("Pressure", "Pa"),
    "p": ("Pressure", "Pa"),
    "V": ("Volume", "m³"),
    "v": ("Molar volume", "m³/mol"),
    "G": ("Gibbs energy", "J"),
    "H": ("Enthalpy", "J"),
    "S": ("Entropy", "J/K"),
    "U": ("Internal energy", "J"),
    "A": ("Helmholtz energy", "J"),
    "R": ("Universal gas constant", "J/(mol·K)"),
    "Z": ("Compressibility factor", "—"),
    "n": ("Number of moles", "mol"),
    "N": ("Number of components", "—"),
    "x": ("Liquid mole fraction", "—"),
    "y": ("Vapour mole fraction", "—"),
    "z": ("Overall mole fraction", "—"),
    "K": ("K-factor (equilibrium ratio)", "—"),
    "f": ("Fugacity", "Pa"),

    # EOS parameters
    "a": ("EOS attraction parameter", "Pa·m⁶/mol²"),
    "b": ("EOS co-volume parameter", "m³/mol"),
    "k_{ij}": ("Binary interaction parameter", "—"),

    # Greek letters
    "\\alpha": ("Alpha function", "—"),
    "\\beta": ("Phase fraction", "—"),
    "\\gamma": ("Activity coefficient", "—"),
    "\\phi": ("Fugacity coefficient", "—"),
    "\\mu": ("Chemical potential", "J/mol"),
    "\\omega": ("Acentric factor", "—"),
    "\\rho": ("Density", "kg/m³"),
    "\\eta": ("Viscosity", "Pa·s"),
    "\\lambda": ("Thermal conductivity", "W/(m·K)"),
    "\\sigma": ("Surface tension", "N/m"),
    "\\kappa": ("Kappa parameter", "—"),
    "\\epsilon": ("Tolerance / convergence criterion", "—"),
    "\\Delta": ("Change / difference", "—"),
    "\\nabla": ("Gradient operator", "—"),

    # Subscripts/superscripts
    "T_c": ("Critical temperature", "K"),
    "P_c": ("Critical pressure", "Pa"),
    "T_r": ("Reduced temperature", "—"),
    "P_r": ("Reduced pressure", "—"),
    "C_p": ("Isobaric heat capacity", "J/(mol·K)"),
    "C_v": ("Isochoric heat capacity", "J/(mol·K)"),
}


def _extract_math_symbols(text):
    """Extract LaTeX math symbols from manuscript text.

    Finds symbols in inline math ($...$) and display math ($$...$$).

    Args:
        text: Manuscript text.

    Returns:
        List of unique symbol strings found.
    """
    symbols = set()

    # Extract all math environments
    math_blocks = []

    # Display math: $$...$$
    for match in re.finditer(r'\$\$(.*?)\$\$', text, re.DOTALL):
        math_blocks.append(match.group(1))

    # Inline math: $...$  (but not $$)
    for match in re.finditer(r'(?<!\$)\$(?!\$)(.*?)\$(?!\$)', text):
        math_blocks.append(match.group(1))

    for block in math_blocks:
        # Extract single-letter variables (possibly with subscripts)
        # e.g., T, P, T_c, K_{ij}, \alpha, \phi_i
        patterns = [
            r'\\[a-zA-Z]+(?:_\{[^}]+\})?',      # \alpha, \phi_{i}, \nabla
            r'[A-Za-z]_\{[^}]+\}',                # T_c, K_{ij}, C_p
            r'[A-Za-z]_[A-Za-z0-9]',              # T_r, P_c (single subscript)
            r'(?<![a-zA-Z\\])[A-Z](?![a-zA-Z])',   # Standalone capitals: T, P, V
        ]
        for pattern in patterns:
            for m in re.finditer(pattern, block):
                sym = m.group(0).strip()
                if sym and len(sym) < 30:  # Skip very long expressions
                    symbols.add(sym)

    return sorted(symbols)


def _match_known_symbols(found_symbols):
    """Match found symbols against the known symbol database.

    Args:
        found_symbols: List of symbol strings from the manuscript.

    Returns:
        List of dicts with symbol, description, unit, and matched status.
    """
    entries = []
    for sym in found_symbols:
        known = _KNOWN_SYMBOLS.get(sym)
        if known:
            entries.append({
                "symbol": sym,
                "description": known[0],
                "unit": known[1],
                "matched": True,
            })
        else:
            entries.append({
                "symbol": sym,
                "description": "",
                "unit": "",
                "matched": False,
            })
    return entries


def extract_nomenclature(manuscript_path, custom_symbols=None):
    """Extract and build a nomenclature table from a manuscript.

    Args:
        manuscript_path: Path to paper.md.
        custom_symbols: Optional dict of {symbol: (description, unit)} overrides.

    Returns:
        Report dict with nomenclature entries and formatted tables.
    """
    manuscript_path = Path(manuscript_path)
    if not manuscript_path.exists():
        return {"error": f"Manuscript not found: {manuscript_path}"}

    text = manuscript_path.read_text(encoding="utf-8", errors="replace")
    found_symbols = _extract_math_symbols(text)

    if not found_symbols:
        return {
            "status": "no_symbols",
            "message": "No LaTeX math symbols found in manuscript",
            "symbol_count": 0,
        }

    # Apply custom overrides
    if custom_symbols:
        _KNOWN_SYMBOLS.update(custom_symbols)

    entries = _match_known_symbols(found_symbols)

    # Sort: Roman letters first, then Greek, then subscripted
    def sort_key(e):
        s = e["symbol"]
        if s.startswith("\\"):
            return (1, s)
        elif "_" in s:
            return (2, s)
        else:
            return (0, s.lower())
    entries.sort(key=sort_key)

    # Separate into categories
    roman = [e for e in entries if not e["symbol"].startswith("\\") and "_" not in e["symbol"]]
    greek = [e for e in entries if e["symbol"].startswith("\\")]
    subscripted = [e for e in entries if "_" in e["symbol"] and not e["symbol"].startswith("\\")]

    # Build Markdown table
    md_lines = ["## Nomenclature", ""]
    if roman:
        md_lines.append("### Roman Letters")
        md_lines.append("")
        md_lines.append("| Symbol | Description | Unit |")
        md_lines.append("|--------|-------------|------|")
        for e in roman:
            md_lines.append(f"| ${e['symbol']}$ | {e['description']} | {e['unit']} |")
        md_lines.append("")

    if greek:
        md_lines.append("### Greek Letters")
        md_lines.append("")
        md_lines.append("| Symbol | Description | Unit |")
        md_lines.append("|--------|-------------|------|")
        for e in greek:
            md_lines.append(f"| ${e['symbol']}$ | {e['description']} | {e['unit']} |")
        md_lines.append("")

    if subscripted:
        md_lines.append("### Subscripted Symbols")
        md_lines.append("")
        md_lines.append("| Symbol | Description | Unit |")
        md_lines.append("|--------|-------------|------|")
        for e in subscripted:
            md_lines.append(f"| ${e['symbol']}$ | {e['description']} | {e['unit']} |")
        md_lines.append("")

    # Build LaTeX nomenclature
    tex_lines = [
        "\\section*{Nomenclature}",
        "\\begin{tabular}{llp{8cm}l}",
        "\\textbf{Symbol} & & \\textbf{Description} & \\textbf{Unit} \\\\",
        "\\hline",
    ]
    for e in entries:
        if e["description"]:
            tex_lines.append(
                f"${e['symbol']}$ & & {e['description']} & {e['unit']} \\\\")
    tex_lines += ["\\end{tabular}", ""]

    matched = sum(1 for e in entries if e["matched"])
    unmatched = [e for e in entries if not e["matched"]]

    report = {
        "status": "generated",
        "symbol_count": len(entries),
        "matched": matched,
        "unmatched_count": len(unmatched),
        "unmatched_symbols": [e["symbol"] for e in unmatched],
        "entries": entries,
        "markdown_table": "\n".join(md_lines),
        "latex_table": "\n".join(tex_lines),
    }

    # Save
    paper_dir = manuscript_path.parent
    (paper_dir / "nomenclature.md").write_text(
        "\n".join(md_lines), encoding="utf-8")

    return report


def print_nomenclature_report(report):
    """Print a formatted nomenclature extraction report.

    Args:
        report: Report dict from extract_nomenclature().
    """
    if "error" in report:
        print(f"Error: {report['error']}")
        return

    if report.get("status") == "no_symbols":
        print(f"  {report['message']}")
        return

    print("=" * 60)
    print("NOMENCLATURE EXTRACTION")
    print("=" * 60)
    print(f"  Symbols found: {report['symbol_count']}")
    print(f"  Auto-matched:  {report['matched']}")
    print(f"  Need manual:   {report['unmatched_count']}")
    print()

    for entry in report.get("entries", []):
        status = "[OK]" if entry["matched"] else "[??]"
        desc = entry["description"] or "(needs description)"
        unit = entry["unit"] or ""
        print(f"    {status} ${entry['symbol']:15s}$  {desc:40s}  {unit}")
    print()

    unmatched = report.get("unmatched_symbols", [])
    if unmatched:
        print("  Unmatched symbols need manual description:")
        for s in unmatched:
            print(f"    → ${s}$")
        print()

    print("  Saved: nomenclature.md")
    print()
