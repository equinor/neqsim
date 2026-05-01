"""
math_utils — LaTeX equation processing for book renderers.

Provides:
- LaTeX → OMML conversion for native Word equations (via latex2mathml + MML2OMML.XSL)
- LaTeX → Unicode fallback for ODF and plain-text rendering
- Display/inline equation extraction from markdown text
"""

import os
import re
import tempfile
from pathlib import Path

# ---------------------------------------------------------------------------
# LaTeX → MathML → OMML for native Word equations
# ---------------------------------------------------------------------------

_MML2OMML_PATH = None

def _find_mml2omml_xsl():
    """Locate Microsoft's MML2OMML.XSL on the system."""
    global _MML2OMML_PATH
    if _MML2OMML_PATH is not None:
        return _MML2OMML_PATH

    candidates = [
        r"C:\Program Files\Microsoft Office\root\Office16\MML2OMML.XSL",
        r"C:\Program Files (x86)\Microsoft Office\root\Office16\MML2OMML.XSL",
        r"C:\Program Files\Microsoft Office\Office16\MML2OMML.XSL",
        r"C:\Program Files\Microsoft Office\root\Office15\MML2OMML.XSL",
    ]
    for p in candidates:
        if os.path.isfile(p):
            _MML2OMML_PATH = p
            return p
    _MML2OMML_PATH = ""
    return ""


def latex_to_omml(latex_str):
    """Convert a LaTeX math string to OMML XML element for Word.

    Returns an lxml Element (oMathPara or oMath) that can be appended
    to a python-docx paragraph, or None if conversion fails.
    """
    try:
        import latex2mathml.converter
        from lxml import etree
    except ImportError:
        return None

    xsl_path = _find_mml2omml_xsl()
    if not xsl_path:
        return None

    # Clean LaTeX for latex2mathml compatibility
    cleaned = _clean_latex_for_mathml(latex_str)

    try:
        mathml_str = latex2mathml.converter.convert(cleaned)
    except Exception:
        return None

    try:
        # Parse MathML
        mathml_dom = etree.fromstring(mathml_str.encode("utf-8"))

        # Load XSLT
        xsl_dom = etree.parse(xsl_path)
        transform = etree.XSLT(xsl_dom)

        # Transform MathML → OMML
        omml_dom = transform(mathml_dom)
        return omml_dom.getroot()
    except Exception:
        return None


def _clean_latex_for_mathml(latex_str):
    """Pre-process LaTeX for better latex2mathml compatibility."""
    s = latex_str.strip()
    # Remove \left and \right (latex2mathml handles bare delimiters)
    s = s.replace(r"\left(", "(").replace(r"\right)", ")")
    s = s.replace(r"\left[", "[").replace(r"\right]", "]")
    s = s.replace(r"\left\{", r"\{").replace(r"\right\}", r"\}")
    s = s.replace(r"\left.", "").replace(r"\right.", "")
    # Replace \text{} with \mathrm{} for latex2mathml
    s = re.sub(r"\\text\{([^}]*)\}", r"\\mathrm{\1}", s)
    # Replace \tag{...} — latex2mathml doesn't support it
    s = re.sub(r"\\tag\{[^}]*\}", "", s)
    return s


# ---------------------------------------------------------------------------
# LaTeX → Unicode fallback (for ODF and environments without OMML)
# ---------------------------------------------------------------------------

_GREEK = {
    r"\alpha": "\u03B1", r"\beta": "\u03B2", r"\gamma": "\u03B3",
    r"\delta": "\u03B4", r"\epsilon": "\u03B5", r"\varepsilon": "\u03B5",
    r"\zeta": "\u03B6", r"\eta": "\u03B7", r"\theta": "\u03B8",
    r"\iota": "\u03B9", r"\kappa": "\u03BA", r"\lambda": "\u03BB",
    r"\mu": "\u03BC", r"\nu": "\u03BD", r"\xi": "\u03BE",
    r"\pi": "\u03C0", r"\rho": "\u03C1", r"\sigma": "\u03C3",
    r"\tau": "\u03C4", r"\upsilon": "\u03C5", r"\phi": "\u03C6",
    r"\varphi": "\u03C6", r"\chi": "\u03C7", r"\psi": "\u03C8",
    r"\omega": "\u03C9",
    r"\Gamma": "\u0393", r"\Delta": "\u0394", r"\Theta": "\u0398",
    r"\Lambda": "\u039B", r"\Xi": "\u039E", r"\Pi": "\u03A0",
    r"\Sigma": "\u03A3", r"\Phi": "\u03A6", r"\Psi": "\u03A8",
    r"\Omega": "\u03A9",
}

_SYMBOLS = {
    r"\times": "\u00D7", r"\cdot": "\u00B7", r"\cdots": "\u22EF",
    r"\ldots": "\u2026", r"\pm": "\u00B1", r"\mp": "\u2213",
    r"\leq": "\u2264", r"\geq": "\u2265", r"\neq": "\u2260",
    r"\approx": "\u2248", r"\equiv": "\u2261", r"\sim": "\u223C",
    r"\propto": "\u221D",
    r"\infty": "\u221E", r"\partial": "\u2202", r"\nabla": "\u2207",
    r"\sum": "\u2211", r"\prod": "\u220F", r"\int": "\u222B",
    r"\rightarrow": "\u2192", r"\leftarrow": "\u2190",
    r"\Rightarrow": "\u21D2", r"\Leftarrow": "\u21D0",
    r"\leftrightarrow": "\u2194", r"\Leftrightarrow": "\u21D4",
    r"\forall": "\u2200", r"\exists": "\u2203", r"\in": "\u2208",
    r"\notin": "\u2209", r"\subset": "\u2282", r"\supset": "\u2283",
    r"\cup": "\u222A", r"\cap": "\u2229",
    r"\quad": "  ", r"\qquad": "    ",
    r"\,": "\u2009", r"\;": "\u2005", r"\!": "",
    r"\circ": "\u00B0",
}

_SUPERSCRIPTS = {
    "0": "\u2070", "1": "\u00B9", "2": "\u00B2", "3": "\u00B3",
    "4": "\u2074", "5": "\u2075", "6": "\u2076", "7": "\u2077",
    "8": "\u2078", "9": "\u2079", "+": "\u207A", "-": "\u207B",
    "n": "\u207F", "i": "\u2071",
}

_SUBSCRIPTS = {
    "0": "\u2080", "1": "\u2081", "2": "\u2082", "3": "\u2083",
    "4": "\u2084", "5": "\u2085", "6": "\u2086", "7": "\u2087",
    "8": "\u2088", "9": "\u2089", "+": "\u208A", "-": "\u208B",
    "i": "\u1D62", "j": "\u2C7C", "m": "\u2098", "n": "\u2099",
    "r": "\u1D63",
}


def latex_to_unicode(latex_str):
    """Best-effort conversion of LaTeX math to readable Unicode text.

    Not a full LaTeX parser — handles the most common constructs found
    in scientific equations: Greek letters, fractions, sub/superscripts,
    common operators, and function names.
    """
    s = latex_str.strip()

    # Remove display math delimiters
    if s.startswith("$$") and s.endswith("$$"):
        s = s[2:-2].strip()
    elif s.startswith("$") and s.endswith("$"):
        s = s[1:-1].strip()

    # Remove \left \right
    s = s.replace(r"\left", "").replace(r"\right", "")

    # \text{...} → plain text
    s = re.sub(r"\\text\{([^}]*)\}", r"\1", s)
    s = re.sub(r"\\mathrm\{([^}]*)\}", r"\1", s)
    s = re.sub(r"\\mathbf\{([^}]*)\}", r"\1", s)

    # \tag{...} → remove
    s = re.sub(r"\\tag\{[^}]*\}", "", s)

    # Greek letters (sort by length to avoid partial matches)
    for cmd in sorted(_GREEK, key=len, reverse=True):
        s = s.replace(cmd, _GREEK[cmd])

    # Symbols
    for cmd in sorted(_SYMBOLS, key=len, reverse=True):
        s = s.replace(cmd, _SYMBOLS[cmd])

    # Function names: \ln → ln, \exp → exp, etc.
    for fn in ("ln", "log", "exp", "sin", "cos", "tan", "sinh", "cosh", "tanh",
               "arcsin", "arccos", "arctan", "max", "min", "lim", "det"):
        s = s.replace(f"\\{fn}", fn)

    # \sqrt{...} → √(...)
    s = re.sub(r"\\sqrt\{([^}]*)\}", r"√(\1)", s)

    # \frac{a}{b} → (a)/(b)
    # Handle nested braces by iterating
    for _ in range(5):  # max nesting depth
        s = re.sub(r"\\frac\{([^{}]*)\}\{([^{}]*)\}", r"(\1)/(\2)", s)

    # Simple superscripts: ^{...}
    def _sup_repl(m):
        content = m.group(1)
        result = ""
        for ch in content:
            result += _SUPERSCRIPTS.get(ch, "^" + ch)
        return result

    s = re.sub(r"\^\{([^}]*)\}", _sup_repl, s)
    # Single char superscript: ^x
    s = re.sub(r"\^([0-9])", lambda m: _SUPERSCRIPTS.get(m.group(1), "^" + m.group(1)), s)

    # Simple subscripts: _{...}
    def _sub_repl(m):
        content = m.group(1)
        result = ""
        for ch in content:
            result += _SUBSCRIPTS.get(ch, "_" + ch)
        return result

    s = re.sub(r"_\{([^}]*)\}", _sub_repl, s)
    # Single char subscript: _x
    s = re.sub(r"_([0-9a-z])", lambda m: _SUBSCRIPTS.get(m.group(1), "_" + m.group(1)), s)

    # Clean up remaining backslashes for unknown commands
    s = re.sub(r"\\([a-zA-Z]+)", r"\1", s)

    # Clean up braces
    s = s.replace("{", "").replace("}", "")

    # Clean up multiple spaces
    s = re.sub(r"  +", " ", s).strip()

    return s


# ---------------------------------------------------------------------------
# Equation extraction from markdown
# ---------------------------------------------------------------------------

def split_math_blocks(text):
    """Split markdown text into alternating (text, math) segments.

    Returns a list of tuples: (content, is_display_math, is_inline_math).
    Display math: $$...$$, Inline math: $...$

    Display equations that span multiple lines are handled.
    """
    segments = []
    pos = 0

    while pos < len(text):
        # Look for display math $$...$$
        dd_start = text.find("$$", pos)
        # Look for inline math $...$
        d_start = text.find("$", pos)

        if dd_start == -1 and d_start == -1:
            # No more math
            segments.append((text[pos:], False, False))
            break

        if dd_start != -1 and (dd_start <= d_start):
            # Display math found first
            if dd_start > pos:
                segments.append((text[pos:dd_start], False, False))
            dd_end = text.find("$$", dd_start + 2)
            if dd_end == -1:
                # Unclosed $$, treat rest as text
                segments.append((text[dd_start:], False, False))
                break
            math_content = text[dd_start + 2:dd_end].strip()
            segments.append((math_content, True, False))
            pos = dd_end + 2
        elif d_start != -1:
            # Check it's not $$ (already handled above)
            if d_start + 1 < len(text) and text[d_start + 1] == "$":
                # This is $$, skip to find it
                if d_start > pos:
                    segments.append((text[pos:d_start], False, False))
                dd_end = text.find("$$", d_start + 2)
                if dd_end == -1:
                    segments.append((text[d_start:], False, False))
                    break
                math_content = text[d_start + 2:dd_end].strip()
                segments.append((math_content, True, False))
                pos = dd_end + 2
            else:
                # Inline math
                if d_start > pos:
                    segments.append((text[pos:d_start], False, False))
                d_end = text.find("$", d_start + 1)
                if d_end == -1:
                    segments.append((text[d_start:], False, False))
                    break
                math_content = text[d_start + 1:d_end]
                # Sanity: inline math shouldn't contain newlines
                if "\n" in math_content:
                    segments.append((text[d_start:d_end + 1], False, False))
                else:
                    segments.append((math_content, False, True))
                pos = d_end + 1
        else:
            segments.append((text[pos:], False, False))
            break

    return segments
