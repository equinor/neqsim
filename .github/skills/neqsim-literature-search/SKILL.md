---
name: neqsim-literature-search
description: "Literature and web research workflow for NeqSim engineering tasks. USE WHEN: a task needs related papers, standards, textbook data, benchmark/reference data, or background information gathered from the open web and paper sources, then stored and summarized so it is reusable and distributable. Stores papers under references/literature/, saved web pages/extracts under references/web/, writes a structured literature_findings.md (claims + citations + relevance), and feeds the distributable references/SOURCES.md."
last_verified: "2026-07-13"
---

# Literature & Web Search for Engineering Tasks

Find, store, and summarize related papers and web information for a NeqSim task
so the research is **auditable, reusable, and distributable** with the task
folder. This skill defines the *workflow and storage convention*; it pairs with
`neqsim-stid-retriever` (internal/vendor corpora), `neqsim-technical-document-reading`
(fact extraction), `neqsim-pdf-ocr` (scanned PDFs), and `neqsim-standards-lookup`
(which standards to pull). It is the open-literature/web counterpart to the
internal-document retrieval flow, and shares the same `references/` collection
layout so everything lands in one distributable folder.

## When to Use

- At the start of Step 1 (Scope & Research) on Standard/Comprehensive tasks.
- When the solver needs a reference for a claim, a correlation source, or an
  independent benchmark dataset (NIST, textbook worked examples, published lab
  data, standards).
- When background context from the web (method descriptions, equipment
  background, operator/vendor public material, regulatory text) informs scope.

## Storage Convention (distributable, per-source)

All research is stored **inside the task folder** under
`step1_scope_and_research/references/`, filed by source so the collection can be
handed to others:

```
references/
├── SOURCES.md               # human-readable summary (auto-generated)
├── collection_manifest.json # machine-readable record (auto-generated)
├── literature_findings.md   # structured claims + citations from this search
├── literature/              # papers, standards, textbook PDFs
│   ├── Smith2019_CO2_dense_phase.pdf
│   └── NORSOK_M-001_2017.pdf
└── web/                     # saved web pages / article extracts
    ├── nist_webbook_CO2_density.md
    └── wikipedia_Joule-Thomson.md
```

- **Papers / standards / textbook PDFs** → `references/literature/`.
- **Web pages** → `references/web/`, saved as a Markdown extract (title, URL,
  retrieval date, and the relevant text/tables) — NOT a raw HTML dump. Name
  files by source + topic (e.g. `nist_webbook_CO2_density.md`).
- Never save research to workspace-level `output/` or `figures/`.

## Workflow

1. **Frame the questions.** From the task scope, write 3–8 specific research
   questions (e.g. "What is the measured CO2 density at 100 bar, 40 °C?",
   "Which standard governs dense-phase CO2 pipeline material selection?").
   Record them at the top of `literature_findings.md`.

2. **Standards first.** Use `neqsim-standards-lookup` to identify applicable
   standards before chasing papers — they often answer the question directly.
   Save standard PDFs (when obtainable) to `literature/`.

3. **Internal corpus (if configured).** If a retrieval backend is available,
   pull internal/vendor docs via `neqsim-stid-retriever` into their own source
   subfolders (`stid/`, `vendor/`, `manual/`).

4. **Web search.** Use the `fetch_webpage` tool to retrieve and read candidate
   pages (query = a research question). Prefer authoritative sources (NIST,
   standards bodies, peer-reviewed / DOI, textbook publishers, reputable
   engineering references). For each useful page, save a Markdown extract to
   `references/web/` with this header, then the relevant excerpt/tables:

   ```markdown
   # <Page title>

   - URL: <full url>
   - Retrieved: YYYY-MM-DD
   - Source type: web | dataset | standard-portal | encyclopedia | vendor
   - Relevance: high | medium | low

   <the relevant text / table / data — quoted, with the location noted>
   ```

   Do **not** invent URLs or fabricate content. If a page is paywalled and only
   the abstract is available, save the abstract and say so.

5. **Papers.** Retrieve open-access PDFs (arXiv / DOI / publisher) into
   `references/literature/`. For paywalled papers, save the abstract + citation
   as a `.md` stub in `literature/` and record the access gap. If a configured
   paper-search backend exists, use it; otherwise list candidates and ask the
   user to confirm downloads.

6. **Extract information.** For each stored PDF, extract the specific facts that
   answer a research question via `neqsim-technical-document-reading` (OCR first
   via `neqsim-pdf-ocr` for scans). For figures/curves/tables, convert PDF pages
   with `devtools/pdf_to_figures.py` and read them with `view_image`; save
   extracted PNGs to the task `figures/` folder.

7. **Write `literature_findings.md`.** One entry per useful source with:
   citation, source path (relative), a two-sentence contribution summary, the
   **specific claim/data** it supports (with page/section/table pointer), and a
   relevance score. Group by research question so each question shows its
   supporting evidence. Triage: keep the strongest ≤ 10; note pruned candidates
   with the reason, so the choice is auditable.

8. **(Re)build the distributable summary.** Run the generator so the papers and
   web extracts are organized and indexed:

   ```bash
   python devtools/generate_sources_md.py <task_dir> --organize
   ```

   It files loose files into `literature/` / `web/` and rebuilds
   `references/SOURCES.md` + `references/collection_manifest.json`.

9. **Feed the task.** Copy the strongest entries into `results.json`
   `references[]`, and use the extracted data (not raw prose) as benchmark /
   basis inputs for the NeqSim study. Append the `## Literature & Reference
   Documents` section to `notes.md`.

## `literature_findings.md` skeleton

```markdown
# Literature & Web Findings — <task title>

Search date: YYYY-MM-DD

## Research questions
1. <question>
2. <question>

## Findings

### Q1 — <question>
- **Smith (2019)**, `literature/Smith2019_CO2_dense_phase.pdf` — measured CO2
  density 628 kg/m3 at 100 bar / 40 °C (Table 3, p. 5). Relevance: high.
  Use as benchmark for the SRK density validation.
- **NIST WebBook**, `web/nist_webbook_CO2_density.md` — reference EOS density
  627.9 kg/m3 at the same state. Relevance: high.

### Q2 — <question>
- **NORSOK M-001 (2017)**, `literature/NORSOK_M-001_2017.pdf` §6.3 — material
  selection for CO2 wet service. Relevance: medium.

## Pruned candidates (with reason)
- <title> — superseded by Smith (2019); older correlation.
```

## Operating Principles

1. **Store inside the task folder**, filed by source (`literature/`, `web/`).
2. **Cite, don't paraphrase** — every finding points to a page/section/table or
   a URL so it can be verified.
3. **No invented URLs or data.** Record access gaps (paywall, not found)
   honestly.
4. **Triage by relevance** — prune to the strongest sources; keep the reasoning.
5. **Prefer authoritative sources** (standards, NIST, DOI, textbooks) for any
   number used as a benchmark or design basis.
6. **Keep it distributable** — always regenerate `SOURCES.md` after a search so
   the whole task folder can be shared and the collected material reused.

## Related Skills

- `neqsim-stid-retriever` — internal/vendor document corpora (per-source folders).
- `neqsim-technical-document-reading` — extract structured facts from PDFs/images.
- `neqsim-pdf-ocr` — OCR for scanned PDFs before extraction.
- `neqsim-standards-lookup` — identify governing standards to retrieve.
- `neqsim-professional-reporting` — `results.json` `references[]` and citation format.
