"""Format-independent helpers for the technical report generator.

generate_report.py is one large script that renders the same content to Word,
HTML and PDF. New logic that does not need python-docx or the generator's
module state lives here instead, so it can be tested on its own and shared by
every renderer:

  * ``FigurePlan``        where each figure goes and what number it carries
  * ``latex_to_omml``     editable Word equations (None when unsupported)
  * number traceability   prose numbers that cannot be found in results.json
  * ``katex_assets``      KaTeX bundled with the toolkit for offline HTML
  * ``report_keywords``   document-property helpers

Only the standard library is used.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))


# ── Figure plan ──────────────────────────────────────────

FIG_REF_RE = re.compile(r"\{fig:([^}\s]+)\}")


def figure_slug(name):
    """Stable identifier for a figure file, used in anchors and bookmarks."""
    stem = os.path.splitext(os.path.basename(str(name)))[0]
    return re.sub(r"[^A-Za-z0-9]+", "_", stem).strip("_").lower()[:30] or "figure"


class FigurePlan:
    """Decide where each figure is shown and which number it carries.

    A figure discussed in the Discussion section is shown there, next to the
    text that interprets it. Figures without a discussion stay in Results.
    Numbers follow document order (Results first), which is also the order
    Word's SEQ fields will assign when the document is updated.
    """

    def __init__(self, figure_paths, discussion=None):
        by_name = {os.path.basename(path).lower(): path for path in figure_paths}
        self.results_figures = []
        self.discussion_figure = {}   # discussion index -> path shown there
        self.discussion_ref = {}      # discussion index -> path already shown earlier
        discussed = set()
        for index, entry in enumerate(discussion or []):
            if not isinstance(entry, dict):
                continue
            path = by_name.get(os.path.basename(str(entry.get("figure", ""))).lower())
            if not path:
                continue
            if path in discussed:
                self.discussion_ref[index] = path
            else:
                discussed.add(path)
                self.discussion_figure[index] = path
        self.results_figures = [path for path in figure_paths if path not in discussed]
        self.order = list(self.results_figures) + [
            self.discussion_figure[index] for index in sorted(self.discussion_figure)]
        self.number = {os.path.basename(path).lower(): n
                       for n, path in enumerate(self.order, 1)}

    def number_of(self, path_or_name):
        """Figure number for a path or file name, or None when unknown."""
        return self.number.get(os.path.basename(str(path_or_name)).lower())

    def resolve(self, token):
        """Map a ``{fig:name}`` token (extension optional) to a figure path."""
        wanted = str(token).lower()
        for path in self.order:
            base = os.path.basename(path).lower()
            if wanted in (base, os.path.splitext(base)[0]):
                return path
        return None


# ── LaTeX -> OMML ────────────────────────────────────────

_M_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"
_W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

_SYMBOLS = {
    "alpha": "\u03b1", "beta": "\u03b2", "gamma": "\u03b3", "delta": "\u03b4",
    "epsilon": "\u03b5", "varepsilon": "\u03b5", "zeta": "\u03b6", "eta": "\u03b7",
    "theta": "\u03b8", "vartheta": "\u03d1", "kappa": "\u03ba", "lambda": "\u03bb",
    "mu": "\u03bc", "nu": "\u03bd", "xi": "\u03be", "pi": "\u03c0", "rho": "\u03c1",
    "sigma": "\u03c3", "tau": "\u03c4", "phi": "\u03c6", "varphi": "\u03c6",
    "chi": "\u03c7", "psi": "\u03c8", "omega": "\u03c9",
    "Gamma": "\u0393", "Delta": "\u0394", "Theta": "\u0398", "Lambda": "\u039b",
    "Xi": "\u039e", "Pi": "\u03a0", "Sigma": "\u03a3", "Phi": "\u03a6",
    "Psi": "\u03a8", "Omega": "\u03a9",
    "cdot": "\u00b7", "times": "\u00d7", "pm": "\u00b1", "mp": "\u2213",
    "div": "\u00f7", "leq": "\u2264", "le": "\u2264", "geq": "\u2265", "ge": "\u2265",
    "neq": "\u2260", "ne": "\u2260", "approx": "\u2248", "sim": "\u223c",
    "propto": "\u221d", "infty": "\u221e", "partial": "\u2202", "nabla": "\u2207",
    "rightarrow": "\u2192", "to": "\u2192", "leftarrow": "\u2190",
    "Rightarrow": "\u21d2", "circ": "\u2218", "ll": "\u226a", "gg": "\u226b",
    "equiv": "\u2261", "ldots": "\u2026", "dots": "\u2026", "cdots": "\u22ef",
    "degree": "\u00b0", "prime": "\u2032", "in": "\u2208", "forall": "\u2200",
    "%": "%", "&": "&", "_": "_", "{": "{", "}": "}", "$": "$", "#": "#",
}
_SPACES = {",": "\u2009", ";": "\u2005", ":": "\u2005", "!": "", " ": " ",
           "quad": "\u2003", "qquad": "\u2003\u2003"}
_FUNCTIONS = {"sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
              "sinh", "cosh", "tanh", "ln", "log", "exp", "min", "max", "lim", "det",
              "sup", "inf", "deg", "dim", "arg"}
_NARY = {"sum": "\u2211", "prod": "\u220f", "int": "\u222b", "oint": "\u222e"}
_ACCENTS = {"bar": "\u0304", "overline": "\u0304", "hat": "\u0302", "tilde": "\u0303",
            "dot": "\u0307", "ddot": "\u0308", "vec": "\u20d7"}
_DELIMS = {"(": "(", ")": ")", "[": "[", "]": "]", "|": "|", "\\{": "{", "\\}": "}",
           "\\|": "\u2016", "\\langle": "\u27e8", "\\rangle": "\u27e9", ".": ""}


class UnsupportedLatex(Exception):
    """Raised for LaTeX the converter does not handle; caller falls back to an image."""


def _esc(text):
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def _run(text, style=None):
    """One math run. ``style``: 'p' upright, 'b' bold, 'bi' bold italic, None italic."""
    props = ""
    if style:
        props = '<m:rPr><m:sty m:val="{}"/></m:rPr>'.format(style)
    return ('<m:r>{}<w:rPr><w:rFonts w:ascii="Cambria Math" w:hAnsi="Cambria Math"/>'
            '</w:rPr><m:t xml:space="preserve">{}</m:t></m:r>'.format(props, _esc(text)))


def _tokenize(latex):
    tokens = []
    i, n = 0, len(latex)
    while i < n:
        ch = latex[i]
        if ch == "\\":
            j = i + 1
            if j < n and latex[j].isalpha():
                while j < n and latex[j].isalpha():
                    j += 1
            else:
                j = min(j + 1, n)
            tokens.append(latex[i:j])
            i = j
        elif ch in "{}^_":
            tokens.append(ch)
            i += 1
        elif ch.isspace():
            i += 1
        else:
            tokens.append(ch)
            i += 1
    return tokens


class _Parser:
    """Recursive-descent parser emitting OMML fragments for a LaTeX subset."""

    def __init__(self, latex):
        if "\\begin" in latex or "\\\\" in latex or "&" in latex.replace("\\&", ""):
            raise UnsupportedLatex("environment or alignment")
        self.tokens = _tokenize(latex)
        self.pos = 0

    def peek(self):
        return self.tokens[self.pos] if self.pos < len(self.tokens) else None

    def take(self):
        token = self.peek()
        if token is None:
            raise UnsupportedLatex("unexpected end")
        self.pos += 1
        return token

    def group(self):
        """Parse a mandatory argument: a {...} group or a single token."""
        if self.peek() == "{":
            self.take()
            body = self.sequence(stop="}")
            if self.take() != "}":
                raise UnsupportedLatex("unbalanced braces")
            return body
        return self.atom_only()

    def sequence(self, stop=None):
        out = []
        while self.peek() is not None and self.peek() != stop:
            if stop is None and self.peek() == "}":
                raise UnsupportedLatex("unbalanced braces")
            if self.peek() == "\\right":
                break
            out.append(self.script_atom())
        return self.merge(out)

    @staticmethod
    def merge(parts):
        """Join adjacent plain runs (same style) into one run for compact XML."""
        merged = []
        for kind, payload, style in parts:
            if (kind == "t" and merged and merged[-1][0] == "t"
                    and merged[-1][2] == style):
                merged[-1] = ("t", merged[-1][1] + payload, style)
            else:
                merged.append((kind, payload, style))
        return "".join(_run(p, s) if k == "t" else p for k, p, s in merged)

    def atom_only(self):
        kind, payload, style = self.atom()
        return _run(payload, style) if kind == "t" else payload

    def script_atom(self):
        """An atom followed by optional ^ and _ scripts."""
        kind, payload, style = self.atom()
        if kind == "nary":
            return self.nary(payload)
        sub = sup = None
        while self.peek() in ("^", "_"):
            marker = self.take()
            arg = self.group()
            if marker == "^":
                sup = arg
            else:
                sub = arg
        if sub is None and sup is None:
            return (kind, payload, style)
        base = _run(payload, style) if kind == "t" else payload
        if kind == "t" and len(payload) > 1 and not style:
            # Scripts bind to the last character only.
            base = _run(payload[-1], style)
            head = _run(payload[:-1], style)
        else:
            head = ""
        if sub is not None and sup is not None:
            xml = ("<m:sSubSup><m:e>{}</m:e><m:sub>{}</m:sub><m:sup>{}</m:sup>"
                   "</m:sSubSup>".format(base, sub, sup))
        elif sup is not None:
            xml = "<m:sSup><m:e>{}</m:e><m:sup>{}</m:sup></m:sSup>".format(base, sup)
        else:
            xml = "<m:sSub><m:e>{}</m:e><m:sub>{}</m:sub></m:sSub>".format(base, sub)
        if head:
            return ("x", head + xml, None)
        return ("x", xml, None)

    def nary(self, char):
        sub = sup = ""
        while self.peek() in ("^", "_"):
            marker = self.take()
            arg = self.group()
            if marker == "^":
                sup = arg
            else:
                sub = arg
        operand = self.sequence(stop="}" if self.has_open_group() else None)
        props = '<m:naryPr><m:chr m:val="{}"/>'.format(char)
        if not sub:
            props += '<m:subHide m:val="1"/>'
        if not sup:
            props += '<m:supHide m:val="1"/>'
        props += "</m:naryPr>"
        return ("x", "<m:nary>{}<m:sub>{}</m:sub><m:sup>{}</m:sup><m:e>{}</m:e></m:nary>"
                .format(props, sub, sup, operand), None)

    def has_open_group(self):
        depth = 0
        for token in self.tokens[:self.pos]:
            if token == "{":
                depth += 1
            elif token == "}":
                depth -= 1
        return depth > 0

    def atom(self):
        token = self.take()
        if token == "{":
            self.pos -= 1
            return ("x", self.group(), None)
        if token in ("}", "^", "_"):
            raise UnsupportedLatex("misplaced " + token)
        if not token.startswith("\\"):
            return ("t", token, None)
        name = token[1:]
        if name in _SYMBOLS:
            return ("t", _SYMBOLS[name], "p" if name in ("degree",) else None)
        if name in _SPACES or token in ("\\,", "\\;", "\\:", "\\!", "\\ "):
            return ("t", _SPACES.get(name, _SPACES.get(token[1:], " ")), "p")
        if name in _FUNCTIONS:
            return ("t", name, "p")
        if name in _NARY:
            return ("nary", _NARY[name], None)
        if name in ("frac", "dfrac", "tfrac"):
            num, den = self.group(), self.group()
            return ("x", "<m:f><m:num>{}</m:num><m:den>{}</m:den></m:f>".format(num, den), None)
        if name == "sqrt":
            degree = ""
            if self.peek() == "[":
                self.take()
                chars = []
                while self.peek() not in ("]", None):
                    chars.append(self.take())
                self.take()
                degree = _run("".join(chars))
            body = self.group()
            props = "<m:radPr><m:degHide m:val=\"1\"/></m:radPr>" if not degree else "<m:radPr/>"
            return ("x", "<m:rad>{}<m:deg>{}</m:deg><m:e>{}</m:e></m:rad>".format(
                props, degree, body), None)
        if name in ("text", "mathrm", "operatorname", "textrm", "mathit", "mathbf",
                    "boldsymbol", "textbf"):
            style = {"mathit": None, "mathbf": "b", "boldsymbol": "b", "textbf": "b"}.get(
                name, "p")
            if self.peek() != "{":
                return ("t", self.take(), style)
            self.take()
            chars = []
            depth = 1
            while True:
                piece = self.take()
                if piece == "{":
                    depth += 1
                elif piece == "}":
                    depth -= 1
                    if depth == 0:
                        break
                chars.append(_SYMBOLS.get(piece[1:], piece) if piece.startswith("\\")
                             else piece)
            text = "".join(chars)
            return ("t", text, style)
        if name in _ACCENTS:
            body = self.group()
            return ("x", '<m:acc><m:accPr><m:chr m:val="{}"/></m:accPr><m:e>{}</m:e></m:acc>'
                    .format(_ACCENTS[name], body), None)
        if name == "left":
            open_char = self.delim()
            inner = self.sequence()
            if self.take() != "\\right":
                raise UnsupportedLatex("missing \\right")
            close_char = self.delim()
            return ("x", ('<m:d><m:dPr><m:begChr m:val="{}"/><m:endChr m:val="{}"/></m:dPr>'
                          '<m:e>{}</m:e></m:d>').format(open_char, close_char, inner), None)
        raise UnsupportedLatex(token)

    def delim(self):
        token = self.take()
        if token in _DELIMS:
            return _DELIMS[token]
        if token.startswith("\\") and token in _DELIMS:
            return _DELIMS[token]
        raise UnsupportedLatex("delimiter " + token)


def latex_to_omml(latex):
    """Convert LaTeX to an ``<m:oMath>`` XML string, or None when unsupported.

    Covers fractions, roots, scripts, big operators, delimiters, accents, text
    and the usual Greek and relation symbols. Anything else (aligned blocks,
    matrices, unknown commands) returns None so the caller keeps using the
    rendered image.
    """
    text = str(latex or "").strip()
    if not text:
        return None
    try:
        parser = _Parser(text)
        body = parser.sequence()
        if parser.pos != len(parser.tokens):
            return None
    except UnsupportedLatex:
        return None
    except (IndexError, ValueError):
        return None
    return '<m:oMath xmlns:m="{}" xmlns:w="{}">{}</m:oMath>'.format(_M_NS, _W_NS, body)


# ── Number traceability ──────────────────────────────────

_NUMBER_RE = re.compile(
    r"(?<![\w.\-/+])(\d{1,3}(?:[\u00a0\u202f ]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?)"
    r"(?![\w/]*[A-Za-z])")
_NARRATIVE_KEYS = {
    "executive_summary", "conclusions", "approach", "figure_discussion",
    "recommendations", "summary", "task_statement", "objective",
}
_SCALES = (1.0, 100.0, 0.01, 1000.0, 0.001, 1e6, 1e-6)


def _to_float(text):
    cleaned = re.sub(r"[\u00a0\u202f ]", "", text)
    if re.fullmatch(r"\d+,\d{1,2}", cleaned):
        cleaned = cleaned.replace(",", ".")
    else:
        cleaned = cleaned.replace(",", "")
    try:
        return float(cleaned)
    except ValueError:
        return None


def numbers_in_text(text, significant_only=True):
    """Numbers in prose as floats; years, small counts and tag fragments are skipped."""
    found = []
    for match in _NUMBER_RE.finditer(str(text or "")):
        raw = match.group(1)
        value = _to_float(raw)
        if value is None:
            continue
        if significant_only:
            is_decimal = bool(re.search(r"[.,]\d", raw)) and not re.fullmatch(
                r"\d{1,3}(?:[\u00a0\u202f ]\d{3})+", raw)
            if not is_decimal and (value < 20 or 1900 <= value <= 2100):
                continue
        found.append(value)
    return found


def harvest_result_numbers(results):
    """Every number in results.json outside the narrative fields."""
    numbers = set()

    def walk(node, key=None):
        if key in _NARRATIVE_KEYS:
            return
        if isinstance(node, bool):
            return
        if isinstance(node, (int, float)):
            numbers.add(float(node))
        elif isinstance(node, str):
            numbers.update(numbers_in_text(node, significant_only=False))
        elif isinstance(node, dict):
            for sub_key, sub_value in node.items():
                walk(sub_value, sub_key)
        elif isinstance(node, (list, tuple)):
            for item in node:
                walk(item, key)

    walk(results or {})
    return numbers


def _same_number(value, candidate):
    for scale in _SCALES:
        scaled = candidate * scale
        if scaled == 0:
            if value == 0:
                return True
            continue
        if abs(value - scaled) <= 0.006 * abs(scaled) + 1e-9:
            return True
    return False


def narrative_numbers(results):
    """Numbers quoted in the report's written findings."""
    results = results or {}
    chunks = [results.get("executive_summary"), results.get("conclusions")]
    for entry in results.get("figure_discussion") or []:
        if isinstance(entry, dict):
            chunks.extend(entry.get(key) for key in
                          ("observation", "mechanism", "implication", "recommendation"))
    for item in results.get("recommendations") or []:
        chunks.append(item.get("recommendation") if isinstance(item, dict) else item)
    found = []
    for chunk in chunks:
        found.extend(numbers_in_text(chunk))
    return found


def untraced_numbers(results):
    """(quoted, unmatched) numbers: quoted in the findings but absent from results.json.

    A written number that matches no value in results.json is either derived
    (the derivation then belongs in the report) or stale, so the report should
    say where it comes from.
    """
    quoted = narrative_numbers(results)
    pool = harvest_result_numbers(results)
    unmatched = []
    for value in quoted:
        if not any(_same_number(value, candidate) for candidate in pool):
            unmatched.append(value)
    return quoted, unmatched


# ── Offline KaTeX ────────────────────────────────────────

def katex_assets():
    """(css, katex_js, auto_render_js) from the bundled vendor folder, else None."""
    vendor = os.path.join(HERE, "vendor")
    names = ("katex_inline.css", "katex.min.js", "auto-render.min.js")
    try:
        texts = []
        for name in names:
            with open(os.path.join(vendor, name), "r", encoding="utf-8") as handle:
                texts.append(handle.read())
        return tuple(texts)
    except OSError:
        return None


# ── Document properties ──────────────────────────────────

def report_keywords(*groups):
    """Comma-separated, de-duplicated keywords from the given iterables."""
    seen = []
    for group in groups:
        for item in group or []:
            text = str(item).strip()
            if text and text.lower() not in (s.lower() for s in seen):
                seen.append(text)
    return ", ".join(seen)


def plain_alt_text(text, limit=250):
    """Alt text: markup removed, whitespace collapsed, cut at a word boundary."""
    cleaned = re.sub(r"[*`$]|\{fig:[^}]*\}", "", str(text or ""))
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if len(cleaned) <= limit:
        return cleaned
    return cleaned[:limit].rsplit(" ", 1)[0] + "..."
