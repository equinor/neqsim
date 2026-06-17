# PaperLab Agents - Decision Tree

This directory holds the PaperLab agents that collaborate to take a research
idea all the way to a peer-reviewed paper, a reproducible benchmark, or a
published engineering book. This README is the quick guide for picking the
right agent for a job.

For the core philosophy ("papers improve NeqSim"), see
[../README.md](../README.md) and [../PAPER_WRITING_GUIDELINES.md](../PAPER_WRITING_GUIDELINES.md).

---

## Quick Decision Tree

```text
What do you want to do?
|
+-- Find a paper opportunity in the NeqSim codebase
|       -> research-scout                 (research_scout.agent.md)
|
+-- Plan a new paper / set up papers/<slug>/
|       -> paper-planner                  (planner.agent.md)
|
+-- Survey prior art for a paper / produce literature_map.md
|       -> literature-reviewer            (literature_reviewer.agent.md)
|
+-- Propose Java code changes in NeqSim that a paper will need
|       -> algorithm-engineer             (algorithm_engineer.agent.md)
|
+-- Run experiments / produce results.json
|       -> benchmark-runner               (benchmark.agent.md)
|
+-- Decide whether claimed improvements are statistically real
|       -> validation-agent               (validation.agent.md)
|
+-- Generate publication-quality figures
|       -> figure-generator               (figure_generator.agent.md)
|
+-- Draft a full paper.md from plan + results + approved claims
|       -> scientific-writer              (scientific_writer.agent.md)
|
+-- Draft one section inside a long-form orchestration
|       -> section-writer                 (section_writer.agent.md)
|
+-- Frame abstract / intro / discussion as a story
|       -> narrative-framer               (narrative_framer.agent.md)
|
+-- Internal hostile review of a complete paper draft
|       -> adversarial-reviewer           (adversarial_reviewer.agent.md)
|
+-- Adapt a finished paper to a journal format / cover letter
|       -> journal-formatter              (journal_formatter.agent.md)
|
+-- Respond to peer-review comments
|       -> reviewer-response              (reviewer_response.agent.md)
|
+-- Create, write, build, or render a book
|       -> book-author                    (book_author.agent.md)
|
+-- Review a book before release
|       -> book-adversarial-reviewer      (book_adversarial_reviewer.agent.md)
|
+-- Reduce repetition in a large book
|       -> book-conciseness-editor        (book_conciseness_editor.agent.md)
|
+-- Improve chapter flow and figure discussions
|       -> chapter-synthesis-editor       (chapter_synthesis_editor.agent.md)
|
+-- Strengthen figure context
|       -> figure-context-editor          (figure_context_editor.agent.md)
|
+-- Map lectures, exercises, and source material to chapters
|       -> lecture-coverage-scout         (lecture_coverage_scout.agent.md)
|
+-- Align book material with exams and assessments
|       -> exam-alignment-reviewer        (exam_alignment_reviewer.agent.md)
|
+-- Verify notebooks, generated figures, and result manifests
|       -> notebook-verifier              (notebook_verifier.agent.md)
|
+-- Maintain sources and citations
|       -> source-librarian               (source_librarian.agent.md)
|
+-- Read and describe technical figures
|       -> technical-figure-reader        (technical_figure_reader.agent.md)
|
+-- Design whole-book curriculum flow
|       -> curriculum-architect           (curriculum_architect.agent.md)
|
+-- Verify learning-objective achievement
|       -> learning-objective-verifier    (learning_objective_verifier.agent.md)
|
+-- Audit equations, units, and notation
|       -> equation-consistency-auditor   (equation_consistency_auditor.agent.md)
|
+-- Verify NeqSim API references in books
|       -> neqsim-api-verifier            (neqsim_api_verifier.agent.md)
|
+-- Monitor notebook regression baselines
|       -> notebook-regression-monitor    (notebook_regression_monitor.agent.md)
|
+-- Improve exercise difficulty progression
|       -> exercise-progression-builder   (exercise_progression_builder.agent.md)
|
+-- Curate standards traceability
|       -> standards-traceability-curator (standards_traceability_curator.agent.md)
|
+-- Keep recurring case threads consistent
|       -> case-thread-continuity-editor  (case_thread_continuity_editor.agent.md)
|
+-- Audit figure accessibility and style
|       -> figure-accessibility-and-style-reviewer
|                                          (figure_accessibility_and_style_reviewer.agent.md)
|
+-- Turn notebooks into interactive HTML labs
|       -> interactive-lab-designer       (interactive_lab_designer.agent.md)
|
+-- Score chapter readiness across audits
|       -> chapter-readiness-scorer       (chapter_readiness_scorer.agent.md)
|
`-- Run the full book release gate
        -> book-release-orchestrator      (book_release_orchestrator.agent.md)
```

---

## Agent-by-Agent Cheat Sheet

| Agent | Owns | Reads | Produces | Skills it should load |
|-------|------|-------|----------|-----------------------|
| `research-scout` | Topic discovery | NeqSim source, recent papers | `papers/_research_scan/topics.md` | - |
| `paper-planner` | Project setup | topic, refs.bib, NeqSim source | `plan.json`, `outline.md`, `benchmark_config.json` | - |
| `literature-reviewer` | Prior-art mapping | web, papers/*/refs.bib | `refs.bib`, `literature_map.md`, `gap_statement.md` | - |
| `algorithm-engineer` | Code design | NeqSim Java sources | implementation tickets, pseudocode | - |
| `benchmark-runner` | Experiments | Java code, test matrices | `results.json`, JUnit fixtures | `design_flash_benchmark`, `design_reactor_benchmark`, `run_flash_experiments` |
| `validation-agent` | Statistical gating | `results.json` | `approved_claims.json` | `analyze_convergence`, `analyze_gibbs_convergence` |
| `figure-generator` | Plots and diagrams | `results.json`, captions | `figures/*.png`, `figures/*.pdf` | `generate_publication_figures` |
| `scientific-writer` | Manuscript prose | plan, results, approved claims | `paper.md`, `claims_manifest.json` | `write_methods_section`, `neqsim_in_writing`, `figure-discussion` |
| `section-writer` | One section at a time | section spec, chapter context | a single markdown section | - |
| `narrative-framer` | Abstract / intro / discussion as story | `paper.md`, `gap_statement.md`, `approved_claims.json` | edits to abstract, intro, discussion, `narrative_notes.md` | - |
| `adversarial-reviewer` | Hostile paper critique | `paper.md`, `approved_claims.json`, `results.json` | `adversarial_review.md`, `required_fixes.json` | - |
| `journal-formatter` | Submission package | `paper.md`, journal profile | LaTeX/Word, cover letter, checklist | `journal_formatting` |
| `reviewer-response` | Revision round | reviewer letter, manuscript | response letter, redline patches | - |
| `book-author` | Books end-to-end | `book.yaml`, refs.bib, papers/ | chapters, notebooks, rendered book | `book_creation`, `neqsim_in_writing` |
| `book-adversarial-reviewer` | Book release critique | book checks, evidence, chapters | `book_adversarial_review.md`, `required_fixes.json` | `paperlab_scientific_traceability_audit`, `paperlab_student_readability` |
| `book-conciseness-editor` | Repetition control | `conciseness_audit.md`, chapters | concise restructuring plan | `paperlab_chapter_flow_editor` |
| `chapter-synthesis-editor` | Chapter narrative synthesis | chapters, figures, notebooks | improved transitions and discussions | `paperlab_chapter_flow_editor`, `figure_discussion` |
| `figure-context-editor` | Figure explanation quality | figures, captions, nearby prose | stronger figure context | `figure_discussion` |
| `lecture-coverage-scout` | Source-to-chapter mapping | lectures, exercises, `coverage_matrix.md` | `coverage_audit.md` | `paperlab_book_knowledge_graph` |
| `exam-alignment-reviewer` | Assessment fit | exams, objectives, exercises | alignment review | `paperlab_exam_alignment` |
| `notebook-verifier` | Notebook existence and figure links | notebooks, figures, manifests | verification summary | `neqsim_in_writing` |
| `source-librarian` | Source and citation hygiene | source folders, refs, chapters | source manifest and fixes | `paperlab_source_pdf_to_html` |
| `technical-figure-reader` | Technical figure interpretation | figures and source images | structured figure notes | `technical_figure_understanding` |
| `curriculum-architect` | Prerequisite graph | objectives, headings, glossary, exercises | `learning_path_audit.md` | `paperlab_curriculum_prerequisite_graph` |
| `learning-objective-verifier` | Objective achievement | chapters, objectives, exercises, notebooks | `lo_achievement_matrix.json`, `lo_coverage_report.md` | `paperlab_learning_objective_matrix` |
| `equation-consistency-auditor` | Equation and notation quality | chapters, equations, `nomenclature.yaml` | `equation_audit.json`, `notation_consistency_report.md` | `paperlab_equation_dimensional_audit` |
| `neqsim-api-verifier` | NeqSim API accuracy | code blocks, notebooks, Java source | `neqsim_api_audit.json` | `paperlab_neqsim_api_claim_verification` |
| `notebook-regression-monitor` | Notebook baselines and drift | notebooks, figures, baselines | `replication_status.json`, `stale_figure_alert.md` | `paperlab_notebook_regression_baselines` |
| `exercise-progression-builder` | Exercise difficulty ramp | exercises, objectives, cases | `exercise_difficulty_ramp.json`, proposals | `paperlab_exercise_difficulty_ramp` |
| `standards-traceability-curator` | Standards evidence | standards statements, sources | `standards_traceability_matrix.json` | `paperlab_standards_clause_traceability` |
| `case-thread-continuity-editor` | Recurring case consistency | case-study chapters, notebooks | case registry and drift report | `paperlab_case_thread_continuity` |
| `figure-accessibility-and-style-reviewer` | Figure readability and accessibility | figures, captions, style rules | `figure_style_audit.json` | `paperlab_figure_accessibility_style` |
| `interactive-lab-designer` | Interactive HTML learning | notebooks, rendered HTML constraints | `interactive_lab_manifest.json` | `paperlab_interactive_html_labs` |
| `chapter-readiness-scorer` | Composite chapter health | audit outputs | `chapter_health_dashboard.md` | `paperlab_chapter_health_dashboard` |
| `book-release-orchestrator` | Full release gate | all audit outputs and render configs | `release_gate_report.md`, final artifacts | `paperlab_book_release_orchestration` |

Skill files live under [../skills/](../skills/). Every skill has a `SKILL.md`.

---

## End-to-End Pipelines

### New Paper

```text
research-scout -> paper-planner -> literature-reviewer
                                  |
                                  v
                         algorithm-engineer (if needed)
                                  |
                                  v
benchmark-runner -> validation-agent -> figure-generator
                                  |
                                  v
scientific-writer -> narrative-framer -> adversarial-reviewer
                                  |
                                  v
                    journal-formatter -> reviewer-response
```

Driver: [../paperflow.py](../paperflow.py) and
[../workflows/new_paper.yaml](../workflows/new_paper.yaml).

### Book Creation and Release

```text
book-author -> source-librarian -> lecture-coverage-scout
        |                               |
        v                               v
curriculum-architect -> learning-objective-verifier
        |                               |
        v                               v
equation-consistency-auditor -> neqsim-api-verifier -> standards-traceability-curator
        |                               |
        v                               v
notebook-verifier -> notebook-regression-monitor -> figure-generator
        |                               |
        v                               v
chapter-synthesis-editor -> exercise-progression-builder -> case-thread-continuity-editor
        |                               |
        v                               v
figure-context-editor -> figure-accessibility-and-style-reviewer -> interactive-lab-designer
        |                               |
        v                               v
book-conciseness-editor -> book-adversarial-reviewer -> chapter-readiness-scorer
        |                               |
        v                               v
                         book-release-orchestrator
```

The release orchestrator should stop on blockers from evidence, notebooks,
API verification, standards traceability, unresolved TODOs, or render failures.

---

## Common Confusions, Resolved

**`notebook-verifier` vs `notebook-regression-monitor`.**
`notebook-verifier` checks that notebooks and generated figures exist and can
be verified or executed. `notebook-regression-monitor` compares key notebook
outputs and figure freshness against stored baselines and tolerances.

**`lecture-coverage-scout` vs `learning-objective-verifier`.**
`lecture-coverage-scout` maps source material into the book. The learning
objective verifier checks whether the final book teaches and assesses what it
promises.

**`book-adversarial-reviewer` vs `chapter-readiness-scorer`.**
The adversarial reviewer writes a critical qualitative review. The readiness
scorer aggregates structured audit outputs into a dashboard for triage.

**`book-author` vs `book-release-orchestrator`.**
`book-author` creates and edits book content. `book-release-orchestrator` runs
the final quality gates and render workflow for a release candidate.

---

## Files in This Directory

```text
adversarial_reviewer.agent.md                 - hostile paper critique
algorithm_engineer.agent.md                   - propose Java-level changes
benchmark.agent.md                            - run experiments and produce results.json
book_adversarial_reviewer.agent.md            - release-readiness critique for books
book_author.agent.md                          - full book lifecycle
book_conciseness_editor.agent.md              - repetition and merge-candidate review
book_release_orchestrator.agent.md            - full book release gate
case_thread_continuity_editor.agent.md        - recurring case consistency
chapter_readiness_scorer.agent.md             - chapter health dashboard
chapter_synthesis_editor.agent.md             - chapter narrative and discussion synthesis
curriculum_architect.agent.md                 - prerequisite graph and curriculum flow
equation_consistency_auditor.agent.md         - equations, units, and notation audit
exam_alignment_reviewer.agent.md              - course/exam alignment
exercise_progression_builder.agent.md         - exercise ramp and capstone proposals
figure_accessibility_and_style_reviewer.agent.md - figure readability and accessibility
figure_context_editor.agent.md                - figure context and discussion quality
figure_generator.agent.md                     - publication figures
interactive_lab_designer.agent.md             - interactive HTML lab planning
journal_formatter.agent.md                    - journal submission package
learning_objective_verifier.agent.md          - objective achievement matrix
lecture_coverage_scout.agent.md               - source material coverage mapping
literature_reviewer.agent.md                  - prior-art review
narrative_framer.agent.md                     - abstract, intro, and discussion framing
neqsim_api_verifier.agent.md                  - NeqSim API accuracy in books
notebook_regression_monitor.agent.md          - notebook baseline and drift monitoring
notebook_verifier.agent.md                    - notebook and figure verification
planner.agent.md                              - research planning
research_scout.agent.md                       - research topic discovery
reviewer_response.agent.md                    - peer-review response
scientific_writer.agent.md                    - full manuscript drafting
section_writer.agent.md                       - single-section drafting
source_librarian.agent.md                     - source and citation hygiene
standards_traceability_curator.agent.md       - standards evidence mapping
technical_figure_reader.agent.md              - technical figure interpretation
validation.agent.md                           - statistical claim gating
```

Each file uses standard YAML front matter (`name`, `description`, `tools`)
consumed by VS Code Copilot, Claude Code, and other agentic clients. Keep that
front matter in sync if you rename or split an agent.