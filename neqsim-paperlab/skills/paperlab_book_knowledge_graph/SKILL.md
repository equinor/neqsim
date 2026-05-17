# Skill: PaperLab Book Knowledge Graph

## Purpose

Build a review graph for a PaperLab book so editors can see how chapters,
figures, notebooks, standards, source folders, and skills connect. Use this
when a book needs gap analysis, traceability review, duplicate-concept review,
or a visual map of the editorial workflow.

## Inputs

Use the book artifacts when they exist:

- `book.yaml`
- `coverage_matrix.md`
- `source_manifest.json`
- `figure_dossier.json`
- `standards_map.json`
- `skill_stack_plan.json`
- chapter notebooks and figures

## Workflow

1. Run the prerequisite audits:
   `book-source-inventory`, `book-figure-dossier`, `book-standards-map`, and
   `book-skill-stack-plan`.
2. Run `python paperflow.py book-knowledge-graph <book_dir>`.
3. Open `book_knowledge_graph.html` and review isolated nodes, missing skill
   links, figures without discussion, chapters without notebooks, and standards
   that appear in text but have no clause-level evidence.
4. Use the graph during adversarial review to decide which chapters need
   synthesis, source retrieval, or de-duplication.

## Output

- `book_knowledge_graph.json` with `nodes[]` and `edges[]`.
- `book_knowledge_graph.html` as a self-contained review page.

## Review Questions

- Does every figure-heavy chapter connect to `technical_figure_understanding`
  and `figure-discussion`?
- Do equipment chapters connect to standards and safety skills?
- Do notebook-heavy chapters have traceability markers?
- Are case-study chapters connected to source documents and skills?

## Safety Rules

- Treat the graph as an editorial map, not proof of standards compliance.
- Do not expose private source documents in a public graph; include only path
  labels and high-level source areas unless the source is public.
- Regenerate after chapter order, figure, source, or standards-map changes.
