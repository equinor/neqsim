# Skill: Journal Formatting

## Purpose

Apply journal-specific formatting rules to transform a manuscript from Markdown
into a submission-ready LaTeX or Word document.

## When to Use

- Final formatting before submission
- Converting between journal formats (e.g., rejected → resubmit elsewhere)
- Checking manuscript compliance with journal guidelines

## Scope: Journal Papers Only (Not Books)

This skill and the `--journal` profiles apply **only to single-manuscript
journal papers** built with the `paperflow new / draft / format / render`
pipeline (`papers/`). The journal `latex_class` and `citation_style` metadata
drive the paper renderer in `tools/paper_renderer.py`.

**Books are a separate pipeline and do NOT use journal profiles.** A book lives
under `books/<slug>/`, is configured by `book.yaml` (not a journal YAML), and is
produced by `paperflow book-render --format {pdf|docx|html|epub|odf}` via the
dedicated `tools/book_render_*` renderers:

| Format | Book renderer | Engine |
|--------|---------------|--------|
| PDF | `book_render_pdf.py` | Typst (auto-generated preamble) |
| Word | `book_render_word.py` | python-docx |
| HTML | `book_render_html.py` | custom HTML template |
| EPUB | `book_render_epub.py` | pandoc |
| ODF | `book_render_odf.py` | odfpy |
| JATS/DocBook | `book_render_xml.py` (via `book-render-xml`) | pandoc |

`cmd_book_render` never calls `load_journal_profile`, so `--journal`,
`elsarticle`/`achemso` classes, and the compliance checks below are irrelevant
to books. Style books through `book.yaml` and the `book_render_*` engines
instead. The shared building blocks between the two pipelines are bibliography
handling (`refs.bib`, Crossref enrichment) and the equation/figure utilities in
`tools/math_utils.py` — not the journal class machinery.

## Supported Journals

Each journal has a YAML profile in `journals/`. Run
`python paperflow.py list-journals` to print this list with the exact
`--journal` names. Currently supported (10 profiles):

| Journal | `--journal` name | Publisher | LaTeX Class | Citation Style |
|---------|------------------|-----------|-------------|----------------|
| Fluid Phase Equilibria | `fluid_phase_equilibria` | Elsevier | `elsarticle` | `numbered` |
| Chemical Engineering Science | `chem_eng_sci` | Elsevier | `elsarticle` | `numbered` |
| Computers & Chemical Engineering | `computers_chem_eng` | Elsevier | `elsarticle` | `authoryear` |
| Geoenergy Science and Engineering | `geoenergy_sci_eng` | Elsevier | `elsarticle` | `numbered` |
| Int. J. of Greenhouse Gas Control | `ijggc` | Elsevier | `elsarticle` | `numbered` |
| Industrial & Engineering Chemistry Research | `iecr` | ACS | `achemso` | `numbered_superscript` |
| Journal of Chemical & Engineering Data | `jced` | ACS | `achemso` | `numbered_superscript` |
| Energy & Fuels | `energy_fuels` | ACS | `achemso` | `numbered_superscript` |
| AIChE Journal | `aiche` | Wiley | `custom` | `numbered` |
| SPE Conference Paper | `spe` | SPE | `custom` | `numbered` |

The `latex_class` value drives how the LaTeX renderer
(`tools/paper_renderer.py`, `render_latex`) emits front matter — see the
class-specific sections below. The three supported `citation_style` values are
`numbered` (square-bracket `[1]`), `numbered_superscript` (ACS-style `¹`), and
`authoryear` (`(Author, Year)`).

## Elsevier Formatting (`elsarticle`)

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

## ACS Formatting (`achemso`)

ACS journals (`iecr`, `jced`, `energy_fuels`) use the `achemso` document class.
Unlike `elsarticle`, **achemso declares authors, affiliations, and the title in
the preamble — before `\begin{document}`** — and has no `frontmatter` or
`highlights` environment. The renderer emits this automatically based on
`latex_class: achemso`.

### LaTeX Class

```latex
\documentclass{achemso}
% achemso auto-detects the journal style from \journal{} when set; the
% journal profiles deliberately set `latex_options: null` so no class options
% are injected (the renderer omits the [] brackets entirely).
```

### Preamble (before `\begin{document}`)

```latex
\author{First Author}
\affiliation{Department of Chemistry, NTNU, Trondheim, Norway}
\author{Second Author}
\affiliation{Equinor ASA, Stavanger, Norway}
\email{corresponding@institution.no}   % only for the corresponding author
\title{Your Title Here}

\begin{document}
```

### Body front matter (after `\begin{document}`)

```latex
\begin{abstract}
% Check journal profile for the abstract word limit
\end{abstract}

\keywords{flash calculation; phase equilibrium; equation of state}
```

### Citations and References

ACS journals use **superscript numbered** citations
(`citation_style: numbered_superscript`):

```latex
\bibliographystyle{achemso}
\bibliography{refs}
```

> **Highlights:** `achemso` has no `highlights` environment. When a profile sets
> `highlights_required: true`, the renderer preserves the highlight text as a
> LaTeX comment block so no content is lost, but it does not appear in the
> compiled PDF. Move highlight content into the abstract for ACS submissions.

## Custom / Generic Formatting (`custom`)

`aiche` (Wiley) and `spe` (SPE) set `latex_class: custom` because the publisher
supplies its own class file. The renderer falls back to a **generic
`\maketitle` front matter** that compiles with the standard `article` class as a
preview, then you drop in the publisher template for the real submission.

```latex
\documentclass{custom}      % swap for the publisher-provided class

\begin{document}

\title{Your Title Here}
\author{First Author \and Second Author}
\maketitle

\begin{abstract}
% ...
\end{abstract}

\noindent\textbf{Keywords:} keyword one, keyword two, keyword three
```

Both custom journals use `citation_style: numbered`. Replace the generic
preamble with the publisher's macros (Wiley `\corraddress`, SPE template fields)
before final submission.

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
