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
+-- Mine code, books, and papers for a ranked publication roadmap
|       -> paper-opportunity-miner        (paper_opportunity_miner.agent.md)
|
+-- Plan a new paper / set up papers/<slug>/
|       -> paper-planner                  (planner.agent.md)
|
+-- Turn research questions into falsifiable benchmark matrices
|       -> hypothesis-benchmark-compiler  (hypothesis_benchmark_compiler.agent.md)
|
+-- Survey prior art for a paper / produce literature_map.md
|       -> literature-reviewer            (literature_reviewer.agent.md)
|
+-- Curate validation datasets and reference data
|       -> dataset-and-reference-curator  (dataset_and_reference_curator.agent.md)
|
+-- Propose Java code changes in NeqSim that a paper will need
|       -> algorithm-engineer             (algorithm_engineer.agent.md)
|
+-- Verify equations and derivations in papers or books
|       -> mathematical-derivation-verifier
|                                          (mathematical_derivation_verifier.agent.md)
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
+-- Select and position a paper for the best target journal
|       -> journal-fit-strategist         (journal_fit_strategist.agent.md)
|
+-- Respond to peer-review comments
|       -> reviewer-response              (reviewer_response.agent.md)
|
+-- Simulate a multi-reviewer panel before submission
|       -> reviewer-simulator-panel       (reviewer_simulator_panel.agent.md)
|
+-- Build a reviewer-ready reproducibility package
|       -> replication-package-engineer   (replication_package_engineer.agent.md)
|
+-- Track calls for papers, conferences, and special issues
|       -> grant-and-special-issue-scout  (grant_and_special_issue_scout.agent.md)
|
+-- Create, write, build, or render a book
|       -> book-author                    (book_author.agent.md)
|
+-- Convert a finished paper into a book chapter or teaching section
|       -> paper-to-book-synthesizer      (paper_to_book_synthesizer.agent.md)
|
+-- Find paper candidates inside mature books
|       -> book-to-paper-miner            (book_to_paper_miner.agent.md)
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
+-- Improve cross-chapter concept recurrence and deepening
|       -> concept-spiral-editor          (concept_spiral_editor.agent.md)
|
+-- Generate polished worked examples from equations and notebooks
|       -> worked-example-factory         (worked_example_factory.agent.md)
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
+-- Build instructor resources from a PaperLab book
|       -> instructor-resource-builder    (instructor_resource_builder.agent.md)
|
`-- Run the full book release gate
        -> book-release-orchestrator      (book_release_orchestrator.agent.md)
```

---

## Agent-by-Agent Cheat Sheet

| Agent | Owns | Reads | Produces | Skills it should load |
|-------|------|-------|----------|-----------------------|
| `research-scout` | Topic discovery | NeqSim source, recent papers | `papers/_research_scan/topics.md` | - |
| `paper-opportunity-miner` | Publication roadmap | source, tests, books, papers, changelog | `publication_opportunities.md`, `opportunity_rankings.json` | `paperlab_publication_opportunity_mining`, `paperlab_journal_positioning` |
| `paper-planner` | Project setup | topic, refs.bib, NeqSim source | `plan.json`, `outline.md`, `benchmark_config.json` | - |
| `hypothesis-benchmark-compiler` | Falsifiable benchmark design | plan, research questions, APIs | `hypothesis_matrix.json`, `benchmark_design.md` | `paperlab_hypothesis_to_benchmark_matrix` |
| `literature-reviewer` | Prior-art mapping | web, papers/*/refs.bib | `refs.bib`, `literature_map.md`, `gap_statement.md` | - |
| `dataset-and-reference-curator` | Reference datasets | refs, sources, raw data | `reference_data_manifest.json`, normalized datasets | `paperlab_reference_dataset_curation` |
| `algorithm-engineer` | Code design | NeqSim Java sources | implementation tickets, pseudocode | - |
| `mathematical-derivation-verifier` | Derivation correctness | papers, chapters, equations, Java source | `derivation_audit.json` | `paperlab_derivation_symbolic_checking`, `paperlab_equation_dimensional_audit` |
| `benchmark-runner` | Experiments | Java code, test matrices | `results.json`, JUnit fixtures | `design_flash_benchmark`, `design_reactor_benchmark`, `run_flash_experiments` |
| `validation-agent` | Statistical gating | `results.json` | `approved_claims.json` | `analyze_convergence`, `analyze_gibbs_convergence` |
| `figure-generator` | Plots and diagrams | `results.json`, captions | `figures/*.png`, `figures/*.pdf` | `generate_publication_figures` |
| `scientific-writer` | Manuscript prose | plan, results, approved claims | `paper.md`, `claims_manifest.json` | `write_methods_section`, `neqsim_in_writing`, `figure-discussion` |
| `section-writer` | One section at a time | section spec, chapter context | a single markdown section | - |
| `narrative-framer` | Abstract / intro / discussion as story | `paper.md`, `gap_statement.md`, `approved_claims.json` | edits to abstract, intro, discussion, `narrative_notes.md` | - |
| `adversarial-reviewer` | Hostile paper critique | `paper.md`, `approved_claims.json`, `results.json` | `adversarial_review.md`, `required_fixes.json` | - |
| `journal-formatter` | Submission package | `paper.md`, journal profile | LaTeX/Word, cover letter, checklist | `journal_formatting` |
| `journal-fit-strategist` | Journal selection and positioning | plan, manuscript, journal profiles | `journal_fit_report.md`, `journal_ranking.json` | `paperlab_journal_positioning` |
| `reviewer-response` | Revision round | reviewer letter, manuscript | response letter, redline patches | - |
| `reviewer-simulator-panel` | Pre-submission review simulation | manuscript, claims, figures, journal profile | `simulated_review_round.md`, `required_fixes.json` | `paperlab_multireviewer_simulation` |
| `replication-package-engineer` | Reproducibility capsule | raw results, scripts, notebooks | `replication_package/`, `reproducibility_manifest.json` | `paperlab_reproducibility_capsule` |
| `grant-and-special-issue-scout` | Publication calendar | roadmaps, public calls, journal fit | `publication_calendar.md`, `call_match_matrix.json` | `paperlab_publication_calendar_scout` |
| `book-author` | Books end-to-end | `book.yaml`, refs.bib, papers/ | chapters, notebooks, rendered book | `book_creation`, `neqsim_in_writing` |
| `paper-to-book-synthesizer` | Paper-to-chapter conversion | finished paper, results, target book | `paper_to_chapter_mapping.md`, `chapter_insert_plan.json` | `paperlab_paper_to_book_chapter` |
| `book-to-paper-miner` | Paper extraction from books | chapters, notebooks, book audits | `book_paper_opportunities.md` | `paperlab_book_to_paper_extraction` |
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
| `concept-spiral-editor` | Cross-chapter concept deepening | objectives, glossary, chapter sequence | `concept_spiral_audit.md`, `concept_spiral_matrix.json` | `paperlab_concept_spiral_learning` |
| `worked-example-factory` | Worked examples | notebooks, equations, chapter context | worked-example blocks, `worked_example_manifest.json` | `paperlab_worked_example_generation` |
| `standards-traceability-curator` | Standards evidence | standards statements, sources | `standards_traceability_matrix.json` | `paperlab_standards_clause_traceability` |
| `case-thread-continuity-editor` | Recurring case consistency | case-study chapters, notebooks | case registry and drift report | `paperlab_case_thread_continuity` |
| `figure-accessibility-and-style-reviewer` | Figure readability and accessibility | figures, captions, style rules | `figure_style_audit.json` | `paperlab_figure_accessibility_style` |
| `interactive-lab-designer` | Interactive HTML learning | notebooks, rendered HTML constraints | `interactive_lab_manifest.json` | `paperlab_interactive_html_labs` |
| `chapter-readiness-scorer` | Composite chapter health | audit outputs | `chapter_health_dashboard.md` | `paperlab_chapter_health_dashboard` |
| `instructor-resource-builder` | Teaching resources | chapters, objectives, exercises, notebooks | instructor resource pack | `paperlab_instructor_resource_pack` |
| `book-release-orchestrator` | Full release gate | all audit outputs and render configs | `release_gate_report.md`, final artifacts | `paperlab_book_release_orchestration` |

Skill files live under [../skills/](../skills/). Every skill has a `SKILL.md`.

---

## End-to-End Pipelines

### New Paper

```text
paper-opportunity-miner -> journal-fit-strategist
                                  |
                                  v
research-scout -> paper-planner -> hypothesis-benchmark-compiler -> literature-reviewer
                                  |
                                  v
dataset-and-reference-curator -> mathematical-derivation-verifier
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
reviewer-simulator-panel -> replication-package-engineer
                                  |
                                  v
                    journal-formatter -> reviewer-response
```

Driver: [../paperflow.py](../paperflow.py) and
[../workflows/new_paper.yaml](../workflows/new_paper.yaml).

### Book Creation and Release

```text
book-to-paper-miner <-> paper-to-book-synthesizer
        |                               |
        v                               v
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
chapter-synthesis-editor -> concept-spiral-editor -> exercise-progression-builder
        |                               |
        v                               v
worked-example-factory -> case-thread-continuity-editor
        |                               |
        v                               v
figure-context-editor -> figure-accessibility-and-style-reviewer -> interactive-lab-designer
        |                               |
        v                               v
book-conciseness-editor -> book-adversarial-reviewer -> chapter-readiness-scorer
        |                               |
        v                               v
instructor-resource-builder -> book-release-orchestrator
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
book_to_paper_miner.agent.md                  - extract paper candidates from books
book_adversarial_reviewer.agent.md            - release-readiness critique for books
book_author.agent.md                          - full book lifecycle
book_conciseness_editor.agent.md              - repetition and merge-candidate review
book_release_orchestrator.agent.md            - full book release gate
case_thread_continuity_editor.agent.md        - recurring case consistency
chapter_readiness_scorer.agent.md             - chapter health dashboard
chapter_synthesis_editor.agent.md             - chapter narrative and discussion synthesis
concept_spiral_editor.agent.md                - cross-chapter concept deepening
curriculum_architect.agent.md                 - prerequisite graph and curriculum flow
dataset_and_reference_curator.agent.md        - validation dataset and source curation
equation_consistency_auditor.agent.md         - equations, units, and notation audit
exam_alignment_reviewer.agent.md              - course/exam alignment
exercise_progression_builder.agent.md         - exercise ramp and capstone proposals
figure_accessibility_and_style_reviewer.agent.md - figure readability and accessibility
figure_context_editor.agent.md                - figure context and discussion quality
figure_generator.agent.md                     - publication figures
grant_and_special_issue_scout.agent.md        - publication calendar and call matching
hypothesis_benchmark_compiler.agent.md        - falsifiable benchmark matrices
instructor_resource_builder.agent.md          - teaching resource packs
interactive_lab_designer.agent.md             - interactive HTML lab planning
journal_formatter.agent.md                    - journal submission package
journal_fit_strategist.agent.md               - journal choice and positioning
learning_objective_verifier.agent.md          - objective achievement matrix
lecture_coverage_scout.agent.md               - source material coverage mapping
literature_reviewer.agent.md                  - prior-art review
narrative_framer.agent.md                     - abstract, intro, and discussion framing
neqsim_api_verifier.agent.md                  - NeqSim API accuracy in books
notebook_regression_monitor.agent.md          - notebook baseline and drift monitoring
notebook_verifier.agent.md                    - notebook and figure verification
paper_opportunity_miner.agent.md              - publication opportunity roadmap
paper_to_book_synthesizer.agent.md            - convert papers into book chapters
planner.agent.md                              - research planning
replication_package_engineer.agent.md         - reproducibility capsules
research_scout.agent.md                       - research topic discovery
reviewer_response.agent.md                    - peer-review response
reviewer_simulator_panel.agent.md             - simulated multi-reviewer critique
scientific_writer.agent.md                    - full manuscript drafting
section_writer.agent.md                       - single-section drafting
source_librarian.agent.md                     - source and citation hygiene
standards_traceability_curator.agent.md       - standards evidence mapping
technical_figure_reader.agent.md              - technical figure interpretation
validation.agent.md                           - statistical claim gating
worked_example_factory.agent.md               - worked examples from equations and notebooks
```

Each file uses standard YAML front matter (`name`, `description`, `tools`)
consumed by VS Code Copilot, Claude Code, and other agentic clients. Keep that
front matter in sync if you rename or split an agent.