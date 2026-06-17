# Skill: Journal Formatting

## Purpose

Apply journal-specific formatting rules to transform a manuscript from Markdown
into a submission-ready LaTeX or Word document.

## When to Use

- Final formatting before submission
- Converting between journal formats (e.g., rejected → resubmit elsewhere)
- Checking manuscript compliance with journal guidelines

## Supported Journals

Each journal has a YAML profile in `journals/`. Currently supported:

| Journal | File | Publisher | Template |
|---------|------|-----------|----------|
| Fluid Phase Equilibria | `fluid_phase_equilibria.yaml` | Elsevier | `elsarticle` |
| Computers & Chemical Engineering | `computers_chem_eng.yaml` | Elsevier | `elsarticle` |
| Industrial & Engineering Chemistry Research | `iecr.yaml` | ACS | `achemso` |
| AIChE Journal | `aiche.yaml` | Wiley | Custom |
| Chemical Engineering Science | `chem_eng_sci.yaml` | Elsevier | `elsarticle` |

## Elsevier Formatting

### LaTeX Class

```latex
\documentclass[review,3p,authoryear]{elsarticle}
% review = double-spaced with line numbers
% 3p = three-column proof style
% authoryear or number for citation style
```

### Front Matter

```latex
\begin{frontmatter}

\title{Your Title Here}

\author[inst1]{First Author\corref{cor1}}
\cortext[cor1]{Corresponding author. Tel.: +47-xxx}
\ead{email@institution.no}

\author[inst1]{Second Author}
\author[inst2]{Third Author}

\affiliation[inst1]{organization={Department of Chemistry, NTNU},
                     city={Trondheim},
                     country={Norway}}
\affiliation[inst2]{organization={Equinor ASA},
                     city={Stavanger},
                     country={Norway}}

\begin{abstract}
% Check journal profile for abstract word limit (FPE: 250 words)
\end{abstract}

\begin{keyword}
flash calculation \sep phase equilibrium \sep equation of state \sep
successive substitution \sep Newton-Raphson \sep convergence
\end{keyword}

\begin{highlights}
\item Improved TP flash algorithm with adaptive SS-NR switching
\item Systematic benchmark across 1000+ cases and 6 fluid families
\item 15\% fewer iterations on average for multicomponent systems
\item Enhanced robustness for near-critical conditions
\item Open-source implementation in NeqSim
\end{highlights}

\end{frontmatter}
```

### Reference Style

Elsevier numbered style:
```latex
\bibliographystyle{elsarticle-num}
\bibliography{refs}
```

### Figure Inclusion

```latex
\begin{figure}[htbp]
\centering
\includegraphics[width=\textwidth]{figures/convergence_map.pdf}
\caption{Convergence success map in temperature-pressure space for
the lean gas family. Green dots indicate successful flash convergence;
red crosses indicate failure. The proposed algorithm (right) reduces
the failure region near the phase boundary.}
\label{fig:convergence_map}
\end{figure}
```

### Table Style (Three-Line)

```latex
\begin{table}[htbp]
\centering
\caption{Benchmark results summary across all fluid families.
$N$ is the number of test cases, Conv.\ is the convergence rate,
Med.\ Iter.\ is the median iteration count for converged cases.}
\label{tab:results}
\begin{tabular}{lrcccc}
\toprule
Family & $N$ & \multicolumn{2}{c}{Conv.\ (\%)} & \multicolumn{2}{c}{Med.\ Iter.} \\
\cmidrule(lr){3-4} \cmidrule(lr){5-6}
       &     & Base & Cand. & Base & Cand. \\
\midrule
Lean gas       & 200  & 100.0 & 100.0 & 6  & 5  \\
Rich gas       & 200  & 99.0  & 99.5  & 8  & 7  \\
Gas condensate & 200  & 95.0  & 98.0  & 14 & 11 \\
CO2-rich       & 200  & 97.5  & 98.5  & 10 & 9  \\
Wide-boiling   & 100  & 93.0  & 96.0  & 18 & 14 \\
Near-critical  & 100  & 85.0  & 92.0  & 22 & 16 \\
\midrule
\textbf{All}   & \textbf{1000} & \textbf{95.8} & \textbf{98.2} & \textbf{10} & \textbf{8} \\
\bottomrule
\end{tabular}
\end{table}
```

## Compliance Checklist Generator

```python
def check_compliance(manuscript, journal_profile):
    """Check manuscript against journal requirements."""
    checks = []

    # Abstract length
    abstract = extract_abstract(manuscript)
    word_count = len(abstract.split())
    max_words = journal_profile["abstract_words_max"]
    checks.append({
        "check": "Abstract length",
        "status": "PASS" if word_count <= max_words else "FAIL",
        "detail": f"{word_count}/{max_words} words"
    })

    # Keywords
    keywords = extract_keywords(manuscript)
    max_kw = journal_profile["keywords_max"]
    checks.append({
        "check": "Keywords count",
        "status": "PASS" if len(keywords) <= max_kw else "FAIL",
        "detail": f"{len(keywords)}/{max_kw} keywords"
    })

    # Highlights
    if journal_profile.get("highlights_required"):
        highlights = extract_highlights(manuscript)
        checks.append({
            "check": "Highlights present",
            "status": "PASS" if highlights else "FAIL",
            "detail": f"{len(highlights)} highlights"
        })
        for i, h in enumerate(highlights):
            max_chars = journal_profile["highlights_max_chars_each"]
            checks.append({
                "check": f"Highlight {i+1} length",
                "status": "PASS" if len(h) <= max_chars else "FAIL",
                "detail": f"{len(h)}/{max_chars} chars"
            })

    # Data availability
    if journal_profile.get("data_availability_required"):
        has_da = "data availability" in manuscript.lower()
        checks.append({
            "check": "Data availability statement",
            "status": "PASS" if has_da else "FAIL"
        })

    return checks
```

## Cross-Journal Conversion

When a paper is rejected and needs to be resubmitted elsewhere:

1. Read the new journal's YAML profile
2. Adjust section order if different
3. Change citation style
4. Update abstract length if needed
5. Add/remove highlights
6. Update LaTeX class and options
7. Re-run compliance check

## Common Gotchas

| Issue | Solution |
|-------|----------|
| Abstract too long for target journal | Shorten, keep key numbers |
| Wrong citation style | Change `\bibliographystyle{}` |
| Double spacing not applied | Add `\usepackage{setspace}\doublespacing` |
| Line numbers missing | Add `\usepackage{lineno}\linenumbers` |
| Figures wrong format | Convert PNG → PDF/EPS |
| Highlights missing | Extract from abstract/conclusions |
| CRediT statement missing | Add to acknowledgements |

## Word Document Generation

### Overview

The `word_renderer.py` tool in `tools/` generates publication-quality Word
documents from `paper.md`. It is called automatically by `paperflow format`.

### Native OMML Equations (Primary Pipeline)

Word documents use **native Office Math (OMML)** equations — the same editing
experience as typing in Word's equation editor. The pipeline is:

```
LaTeX string → latex2mathml → MathML XML → XSLT (MML2OMML.XSL) → OMML element → Word paragraph
```

**Requirements:**
- Python packages: `latex2mathml`, `lxml`, `python-docx`
- XSL file: `MML2OMML.XSL` (ships with Microsoft Office 2013+, typically at
  `%ProgramFiles%\Microsoft Office\root\Office16\MML2OMML.XSL`)

**Fallback:** If OMML pipeline is unavailable, equations render as Unicode text
(Greek letters, operators) — readable but not editable as math objects.

### Word Rendering Features

| Feature | Implementation |
|---------|---------------|
| Inline math `$...$` | OMML elements embedded in paragraph runs |
| Display equations `$$...$$` | Centered OMML with sequential `(N)` numbering |
| Tables | Booktabs style: top/bottom 1.5pt borders, thin inside-H, blue header fill, no vertical rules |
| Figures | Embedded PNG with numbered "Figure N." caption |
| Citations `\cite{key}` | Resolved to `[N]` from `refs.bib` (alphabetical order) |
| Code blocks | Shaded single-cell table in Consolas 8.5pt |
| Bold/italic/code | Inline formatting via `**`, `*`, backtick |
| Page numbers | Footer field (auto-incrementing PAGE) |
| Highlights | Bulleted list with filled circles |
| Abstract | Slightly indented, 1.5× line spacing |
| References | Hanging indent `[N] Author (year). Title. Journal.` |

### Journal Profile Integration

The Word renderer reads journal profile YAML values:

```yaml
# In journals/computers_chem_eng.yaml
double_spacing_required: true
line_spacing: 2.0
```

These override the renderer defaults (font sizes, margins, spacing).

### API Usage

```python
from word_renderer import render_word_document

# Basic usage
render_word_document("papers/my_paper/")

# With journal profile
import yaml
with open("journals/computers_chem_eng.yaml") as f:
    profile = yaml.safe_load(f)
render_word_document("papers/my_paper/", journal_profile=profile)

# Custom output directory
render_word_document("papers/my_paper/", output_dir="output/")
```

### How It Works Internally

1. **Setup:** Creates Word document with page layout, styles, footer
2. **Parse:** Splits `paper.md` into sections by `#` headings
3. **Front matter:** Title → Author placeholder → Highlights → Abstract → Keywords
4. **Body:** For each section:
   - Display equations (`$$...$$`) → centered OMML + equation number
   - Tables (pipe syntax) → booktabs-style Word table
   - Images (`![alt](path)`) → embedded figure with caption
   - Code blocks → shaded table cell
   - Lists → bulleted or numbered paragraphs
   - Text → paragraph with inline math/bold/italic/code
5. **References:** Parsed from `refs.bib`, numbered alphabetically
6. **Save:** Writes to `submission/paper.docx` (uses timestamped name if locked)

### Limitations

- Does not support line numbering (Word limitation in python-docx)
- No automatic cross-references (`\ref{fig:X}` not resolved)
- BibTeX parsing is regex-based (covers standard entry types)
- Complex nested LaTeX (e.g., `\frac{\frac{a}{b}}{c}`) may need manual cleanup

## Pre-Submission Quality Tools

Run these CLI commands **before** generating submission files:

```bash
# Validate figures meet journal requirements (DPI, format, size)
python paperflow.py validate-figures papers/<slug>/ --journal <journal_name>

# Validate bibliography completeness and cross-references
python paperflow.py validate-bib papers/<slug>/

# Check prose readability, passive voice, hedging language
python paperflow.py check-prose papers/<slug>/

# Discover missing highly-cited references
python paperflow.py suggest-refs papers/<slug>/ --max 10

# After revision: generate visual diff for reviewer response
python paperflow.py diff papers/<slug>/
```

All `[!!]` (fail) items from `validate-figures` and `validate-bib` must be
resolved before formatting. `check-prose` and `suggest-refs` are advisory.
