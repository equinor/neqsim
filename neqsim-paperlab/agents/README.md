# PaperLab Agents — Decision Tree

This directory holds the 14 agents that collaborate to take a research idea
all the way to a peer-reviewed paper or a published book. This README is the
**one-page guide** for picking the right agent for a job.

For the core philosophy ("papers improve NeqSim"), see
[../README.md](../README.md) and [../PAPER_WRITING_GUIDELINES.md](../PAPER_WRITING_GUIDELINES.md).

---

## Quick decision tree

```
What do you want to do?
│
├─ Find a paper opportunity in the NeqSim codebase
│       → research-scout                 (research_scout.agent.md)
│
├─ Plan a new paper / set up papers/<slug>/
│       → paper-planner                  (planner.agent.md)
│
├─ Survey prior art for a paper / produce literature_map.md
│       → literature-reviewer            (literature_reviewer.agent.md)
│
├─ Propose Java code changes in NeqSim that a paper will need
│       → algorithm-engineer             (algorithm_engineer.agent.md)
│
├─ Run the experiments / produce results.json (the truth source)
│       → benchmark-runner               (benchmark.agent.md)
│
├─ Decide whether claimed improvements are statistically real
│       → validation-agent               (validation.agent.md)
│
├─ Generate publication-quality figures (PNG + PDF, journal style)
│       → figure-generator               (figure_generator.agent.md)
│
├─ Draft a full paper.md from plan + results + approved claims
│       → scientific-writer              (scientific_writer.agent.md)
│
├─ Draft ONE section (300–1500 words) inside a long-form orchestration
│       → section-writer                 (section_writer.agent.md)
│       (Used by tools/book_writer.py — do not call directly for whole chapters.)
│
├─ Frame abstract / intro / discussion as a story (run after writer)
│       → narrative-framer               (narrative_framer.agent.md)
│
├─ Internal hostile review of a complete draft before submission
│       → adversarial-reviewer           (adversarial_reviewer.agent.md)
│
├─ Adapt a finished paper to a specific journal's format / cover letter
│       → journal-formatter              (journal_formatter.agent.md)
│
├─ Respond to peer-review comments and produce a redline + response letter
│       → reviewer-response              (reviewer_response.agent.md)
│
└─ Create / write / build / render a multi-chapter book
        → book-author                   (book_author.agent.md)
```

---

## Agent-by-agent cheat sheet

| Agent | Owns | Reads | Produces | Skills it loads |
|-------|------|-------|----------|-----------------|
| `research-scout` | Topic discovery | NeqSim source, recent papers | `papers/_research_scan/topics.md` | — |
| `paper-planner` | Project setup | topic, refs.bib, NeqSim source | `plan.json`, `outline.md`, `benchmark_config.json` | — |
| `literature-reviewer` | Prior-art mapping | web, papers/*/refs.bib | `refs.bib`, `literature_map.md`, `gap_statement.md` | — |
| `algorithm-engineer` | Code design | NeqSim Java sources | implementation tickets, pseudocode | — |
| `benchmark-runner` | Experiments | Java code, test matrices | `results.json`, JUnit fixtures | `design_flash_benchmark`, `design_reactor_benchmark`, `run_flash_experiments` |
| `validation-agent` | Statistical gating | `results.json` | `approved_claims.json` | `analyze_convergence`, `analyze_gibbs_convergence` |
| `figure-generator` | Plots & diagrams | `results.json`, captions | `figures/*.png`, `figures/*.pdf` | `generate_publication_figures` |
| `scientific-writer` | Manuscript prose | plan, results, approved claims | `paper.md`, `claims_manifest.json` | `write_methods_section`, `neqsim_in_writing`, `figure_discussion` |
| `section-writer` | One section at a time | section spec, chapter context | a single markdown section | — |
| `narrative-framer` | Abstract / intro / discussion as story | `paper.md`, `gap_statement.md`, `approved_claims.json` | edits to abstract+intro+discussion, `narrative_notes.md` | — |
| `adversarial-reviewer` | Hostile pre-submission critique | `paper.md`, `approved_claims.json`, `results.json` | `adversarial_review.md`, `required_fixes.json` | — |
| `journal-formatter` | Submission package | `paper.md`, journal profile | LaTeX/Word, cover letter, checklist | `journal_formatting` |
| `reviewer-response` | Revision round | reviewer letter, manuscript | response letter, redline patches | — |
| `book-author` | Books end-to-end | `book.yaml`, refs.bib, papers/ | chapter.md, notebooks, rendered book | `book_creation`, `neqsim_in_writing` |

Skill files live under [../skills/](../skills/). Every skill has a `SKILL.md`.

---

## End-to-end pipelines

### New paper (clean slate)

```
research-scout   →  paper-planner   →  literature-reviewer
                                   ↓
algorithm-engineer (if NeqSim changes are needed)
                                   ↓
benchmark-runner  →  validation-agent  →  figure-generator
                                   ↓
scientific-writer  →  narrative-framer  →  adversarial-reviewer
                                   ↓
(address required_fixes.json)  →  journal-formatter
                                   ↓
(after submission) reviewer-response
```

`narrative-framer` may be invoked twice — once before `scientific-writer`
to seed the abstract/intro hook from `gap_statement.md`, and once after
to rewrite the discussion as a three-act story. `adversarial-reviewer`
blocks `journal-formatter` until every BLOCKER in `required_fixes.json`
is marked `ADDRESSED`.

Driver: [`../paperflow.py`](../paperflow.py) and the workflow YAML
[`../workflows/new_paper.yaml`](../workflows/new_paper.yaml).

### Revising a paper after peer review

```
reviewer-response   →   benchmark-runner (re-run affected experiments)
                                   ↓
validation-agent (update approved_claims.json)
                                   ↓
scientific-writer (patch paper.md)   →   journal-formatter
```

Driver: [`../workflows/revise_paper.yaml`](../workflows/revise_paper.yaml).

### New book

```
book-author (scaffolds book.yaml + chapter dirs + master refs.bib)
        ↓
literature-reviewer (one pass per chapter — populate refs.bib)
        ↓
book-author (imports figures/tables from matching papers/*/)
        ↓
scientific-writer  OR  section-writer (chapter prose)
        ↓
benchmark-runner + figure-generator (chapter notebooks)
        ↓
book-author → paperflow.py book-build → submission/book.{html,docx,pdf,odf}
```

Driver: [`../workflows/build_book.yaml`](../workflows/build_book.yaml).

---

## Common confusions, resolved

**`scientific-writer` vs `section-writer`.**
`scientific-writer` drafts the whole `paper.md` in one shot from the full
artefact set (plan, results, approved claims). `section-writer` is a narrow
worker called by `tools/book_writer.py` to draft one 300–1500-word section
at a time when a chapter is too long for a single LLM call. For book
chapters of normal length, **use `book-author`**, which composes
`scientific-writer`-style passes.

**`literature-reviewer` vs `paper-planner` Step 0.**
Both touch literature. The split is: `literature-reviewer` is a deeper
specialist for `literature_map.md` + `gap_statement.md`. `paper-planner`
Step 0 is a lighter mandatory minimum that every plan must include even
if `literature-reviewer` is not invoked separately. For book projects,
`book-author` invokes `literature-reviewer` per chapter.

**`research-scout` vs `paper-planner`.**
`research-scout` is upstream — it finds *what* could be a paper. It
returns ranked topics. `paper-planner` is downstream — it takes one
chosen topic and turns it into a concrete `plan.json`.

**`benchmark-runner` vs `validation-agent`.**
`benchmark-runner` produces `results.json` (raw numbers). `validation-agent`
produces `approved_claims.json` (which numbers are allowed in the paper).
The writer agents read `approved_claims.json`, not `results.json` directly,
for any A-vs-B improvement claim.

---

## Files in this directory

```
research_scout.agent.md         — find paper opportunities in NeqSim
planner.agent.md                — turn a topic into a research plan
literature_reviewer.agent.md    — deep prior-art review
algorithm_engineer.agent.md     — propose Java-level changes
benchmark.agent.md              — run experiments, produce results.json
validation.agent.md             — gate claims via statistical tests
figure_generator.agent.md       — publication figures (PNG + PDF)
scientific_writer.agent.md      — draft full paper.md
section_writer.agent.md         — draft one section (orchestrator helper)
narrative_framer.agent.md       — frame abstract / intro / discussion as story
adversarial_reviewer.agent.md   — hostile pre-submission critique
journal_formatter.agent.md      — adapt to a specific journal
reviewer_response.agent.md      — handle peer-review revision rounds
book_author.agent.md            — full book lifecycle
```

Each file uses the standard YAML front matter (`name`, `description`,
`tools`) consumed by VS Code Copilot, Claude Code, and other agentic
clients. Keep that front matter in sync if you rename or split an agent.
