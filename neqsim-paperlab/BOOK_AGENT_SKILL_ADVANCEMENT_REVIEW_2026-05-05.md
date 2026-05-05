# PaperLab Book Agent and Skill Advancement Review

Date: 2026-05-05
Scope: PaperLab books, with the TPG4230 Field Development and Operations book as the reference case.

## Executive Summary

The TPG4230 book is now in a strong release state: 32 chapters, 0 TODOs, 166 figures, 78 notebooks, passing structural checks, passing evidence checks, and a conciseness audit with 0 exact duplicate paragraph groups and 0 near-duplicate paragraph pairs. The remaining improvement opportunity is not basic cleanup. The next step is to make PaperLab books more advanced by turning them into continuously verified engineering learning systems.

PaperLab already has the foundations: book authoring, figure generation, adversarial review, conciseness editing, chapter synthesis, notebook verification, student readability guidance, scientific traceability, and book typesetting release skills. The gap is orchestration and depth: no single pipeline yet verifies learning-objective achievement, equation consistency, NeqSim API accuracy, standards clause traceability, worked-example progression, exercise difficulty ramp, or interactive computational learning across a whole book.

The highest-value improvement is a book-quality control loop built around these artifacts:

- a curriculum prerequisite graph,
- a learning-objective achievement matrix,
- an equation and notation audit,
- a NeqSim API claim audit,
- a notebook regression and figure staleness monitor,
- an exercise progression map,
- and a chapter readiness dashboard.

## Implementation Update

The first implementation pass has now been completed in PaperLab. The agent
inventory was refreshed from the stale 14-agent decision tree to the current
35-agent system, and all twelve recommended book-quality agents have been
scaffolded under `agents/` with matching skill packages under `skills/`.

New agents added:

- `curriculum-architect`
- `learning-objective-verifier`
- `equation-consistency-auditor`
- `neqsim-api-verifier`
- `notebook-regression-monitor`
- `exercise-progression-builder`
- `standards-traceability-curator`
- `case-thread-continuity-editor`
- `figure-accessibility-and-style-reviewer`
- `interactive-lab-designer`
- `chapter-readiness-scorer`
- `book-release-orchestrator`

New skills added:

- `paperlab_curriculum_prerequisite_graph`
- `paperlab_learning_objective_matrix`
- `paperlab_equation_dimensional_audit`
- `paperlab_neqsim_api_claim_verification`
- `paperlab_notebook_regression_baselines`
- `paperlab_exercise_difficulty_ramp`
- `paperlab_standards_clause_traceability`
- `paperlab_case_thread_continuity`
- `paperlab_figure_accessibility_style`
- `paperlab_interactive_html_labs`
- `paperlab_chapter_health_dashboard`
- `paperlab_book_release_orchestration`

This pass defines responsibilities, required context, output artifacts, schemas,
pass criteria, and guardrails. The next implementation layer should add CLI
commands that generate the JSON and Markdown artifacts automatically.

## Current Book Review Findings

### Strengths

The TPG4230 book is unusually strong for an engineering textbook because it has:

- a complete chapter sequence from field-development framing to case-study decision support,
- high figure density in process, optimization, facilities, and field-development chapters,
- extensive NeqSim computational integration in notebooks,
- recurring worked examples and decision framing,
- validation artifacts from PaperLab book checks,
- standalone HTML and PDF release outputs,
- and strong final capstone chapters around Ultima Thule.

Recent checks show:

- `book-check --check all`: passed,
- `book-evidence-check`: 0 issues,
- `book-conciseness-audit`: 0 duplicate paragraph groups and 0 near-duplicate pairs,
- standalone HTML embedding: 172 images embedded, 0 missing, 0 errors,
- final `book-status`: 32 chapters, 133,355 words, 0 TODOs, 166 figures, 78 notebooks.

### Remaining Editorial Risks

These are not release blockers, but they are the next frontier:

1. **Chapter similarity is pedagogical, but still worth managing.** The conciseness audit still finds 10 adjacent chapter merge candidates. Most are expected because adjacent chapters share vocabulary, but an agent should classify whether each similarity is intentional scaffolding, a duplicated concept, or a missed synthesis opportunity.

2. **Repeated headings are intentional but should be governed.** Repeated headings such as Learning Objectives, Summary, Exercises, Notebook Learning Path, and NeqSim Implementation are useful textbook scaffolding. They should not be removed, but a book-level schema should enforce consistent order and naming.

3. **Prose review remains noisy.** The prose reviewer reports many long sentences and passive-voice findings, but many are false positives from front matter, lists, technical nomenclature, and formulas. A student-facing readability agent should filter these findings by chapter role and reader friction rather than raw sentence length.

4. **Technical claims need richer traceability.** Evidence checks passed, but advanced engineering books need claim-level traceability for key numbers, equations, design rules, standards statements, and NeqSim API examples.

5. **Notebooks need regression-style monitoring.** There are 78 notebooks. A one-time verification is useful, but advanced books need a baseline/tolerance system that detects stale figures and drift in numerical examples.

6. **Exercises need a difficulty ramp.** The book has many exercises, but PaperLab would be stronger if it could verify progression from conceptual questions to calculation tasks, uncertainty/risk tasks, and cross-chapter capstone problems.

7. **Agent documentation is slightly stale.** `agents/README.md` says the directory contains 14 agents, but the current directory has 24 agent files. The decision tree should be updated to include newer book-specific agents such as `book-conciseness-editor`, `book-adversarial-reviewer`, `chapter-synthesis-editor`, `exam-alignment-reviewer`, `lecture-coverage-scout`, `notebook-verifier`, `source-librarian`, `technical-figure-reader`, and `figure-context-editor`.

## Existing Agents That Already Help

### Core Production Agents

- `book-author`: scaffolds, writes, builds, and renders PaperLab books.
- `section-writer`: drafts individual sections for long books.
- `scientific-writer`: writes research-style content and can support technical chapters.
- `figure-generator`: creates publication-quality figures.
- `notebook-verifier`: checks notebooks, generated figures, and result manifests.
- `journal-formatter`: handles publication-style output for papers and can inform release polish.

### Review and Improvement Agents

- `book-adversarial-reviewer`: final release-readiness critique for pedagogy, evidence, computation, citations, and readability.
- `book-conciseness-editor`: detects repeated text, duplicated figures, repeated headings, and merge candidates.
- `chapter-synthesis-editor`: connects figure discussions into coherent teaching narrative.
- `figure-context-editor`: strengthens figure context.
- `exam-alignment-reviewer`: checks course/exam alignment.
- `lecture-coverage-scout`: maps course source material to chapters and learning objectives.
- `technical-figure-reader`: reads and describes technical figures.
- `source-librarian`: improves citation/source management.
- `narrative-framer`: strengthens story arc, transitions, and discussion framing.

### Technical Research Agents

- `research-scout`, `paper-planner`, `literature-reviewer`, `algorithm-engineer`, `benchmark-runner`, `validation-agent`, and `adversarial-reviewer` are strongest for papers, but their methods can be reused for book chapters that contain benchmark claims or new NeqSim features.

## Existing Skills That Already Help

The most relevant current skills are:

- `book_creation`: project structure, book.yaml, figures, builds, and rendering.
- `neqsim_in_writing`: claim-to-test linkage, units, notebooks, and NeqSim-backed writing.
- `paperlab_student_readability`: student learning quality.
- `paperlab_chapter_flow_editor`: chapter transitions, case continuity, and narrative arc.
- `paperlab_scientific_traceability_audit`: claim, figure, equation, citation, unit, and notebook traceability.
- `paperlab_book_typesetting_release`: release-quality HTML/PDF/DOCX/ODF rendering.
- `paperlab_book_knowledge_graph`: graph of chapters, figures, notebooks, standards, and skills.
- `paperlab_exam_alignment`: learning and assessment alignment.
- `paperlab_field_development_timeline`: field-development timeline framing.
- `paperlab_source_pdf_to_html`: source-material ingestion.
- `technical_figure_understanding`: figure reading and technical interpretation.
- `figure_discussion` / `figure-discussion`: observation, mechanism, implication, recommendation structure.
- `generate_publication_figures`: consistent technical figures.
- `neqsim_standard_requirement_extraction`: standards requirement extraction.

## Main Capability Gaps

### Gap 1: Learning Objective Achievement

PaperLab can list learning objectives and run broad coverage checks, but it does not yet prove that each objective is supported by a theory section, worked example, figure, exercise, and notebook where appropriate.

Needed output:

- `lo_achievement_matrix.json`,
- `lo_coverage_report.md`,
- per-chapter status: complete, partial, missing.

### Gap 2: Prerequisite and Curriculum Flow

Chapter order currently relies on author judgement and manual review. Advanced course books need a prerequisite graph that checks whether concepts are introduced before they are used.

Needed output:

- `learning_path_audit.md`,
- prerequisite DAG,
- misplaced concept list,
- recommended bridge paragraphs or cross-references.

### Gap 3: Equation and Notation Consistency

Technical books need a cross-chapter equation and notation audit. This is different from prose review. It should detect symbol collisions, unit mismatches, duplicate formulas with different constants, missing definitions, and equations without sources or implementation links.

Needed output:

- `equation_audit.json`,
- `notation_consistency_report.md`,
- proposed entries for `nomenclature.yaml`.

### Gap 4: NeqSim API Accuracy

NeqSim examples in books can drift as Java APIs evolve. A verifier should read Java source and check class names, methods, signatures, units, and deprecations used in book code blocks and notebooks.

Needed output:

- `neqsim_api_audit.json`,
- valid/deprecated/wrong/missing API classifications,
- patch suggestions for broken snippets.

### Gap 5: Notebook Regression Monitoring

Book notebooks need more than syntax checks. They need repeatable baselines: figures should regenerate, key numeric outputs should stay within tolerance, and chapter claims should still match notebook results.

Needed output:

- `replication_status.json`,
- `stale_figure_alert.md`,
- per-notebook pass/stale/broken state,
- figure and numeric tolerance report.

### Gap 6: Worked Example and Exercise Progression

Advanced books should ramp exercises deliberately: concept check, deterministic calculation, sensitivity, uncertainty, decision trade-off, and cross-chapter integration.

Needed output:

- `exercise_difficulty_ramp.json`,
- integration exercise proposals,
- missing exercise types by chapter,
- suggested capstone threads.

### Gap 7: Standards Clause Traceability

Standards-heavy chapters need clause-level or at least requirement-level evidence. Broad references to API, DNV, NORSOK, ISO, or IEC are not enough for advanced professional review.

Needed output:

- `standards_traceability_matrix.json`,
- standard/revision/clause or paraphrased-requirement map,
- unsupported compliance language report.

### Gap 8: Interactive and Advanced HTML Learning

The standalone HTML book is already useful, but PaperLab could become exceptional if it supported embedded computational labs, collapsible solution logic, interactive concept maps, and executable NeqSim examples.

Needed output:

- `interactive_lab_manifest.json`,
- embedded or linked lab widgets,
- static fallback for PDF/DOCX,
- solution-hint blocks for students.

## Recommended New Agents

### 1. `curriculum-architect`

Purpose: Build and review the prerequisite graph for a whole book.

Responsibilities:

- extract concepts from learning objectives, headings, glossary, and exercises,
- identify where each concept is introduced, used, reinforced, and assessed,
- flag concepts used before introduction,
- propose bridge paragraphs or cross-references,
- produce `learning_path_audit.md`.

Why it matters: It makes a 30-chapter technical book feel like a designed curriculum rather than a collection of strong chapters.

### 2. `learning-objective-verifier`

Purpose: Verify that every learning objective is actually taught and assessed.

Responsibilities:

- map objectives to sections, figures, examples, notebooks, summaries, and exercises,
- classify each objective as complete, partial, or missing,
- suggest one concrete edit for each partial objective,
- produce `lo_achievement_matrix.json` and `lo_coverage_report.md`.

Why it matters: It creates a rigorous link between chapter promises and student outcomes.

### 3. `equation-consistency-auditor`

Purpose: Audit equations, units, notation, and repeated formulas across a book.

Responsibilities:

- extract displayed and inline equations,
- check symbol definitions against `nomenclature.yaml`,
- detect unit inconsistencies and symbol collisions,
- detect formula variants that should be reconciled or explicitly distinguished,
- link equations to citations, standards, or NeqSim implementation.

Why it matters: It prevents technical textbooks from quietly accumulating conflicting formula conventions.

### 4. `neqsim-api-verifier`

Purpose: Verify all NeqSim API references in prose, code blocks, and notebooks.

Responsibilities:

- search Java source for referenced classes and methods,
- validate constructor signatures and method parameters,
- check units and deprecation status where available,
- compare snippets against notebook/test examples,
- produce API fix suggestions.

Why it matters: It would have caught errors like misspelled `Cooler` examples automatically.

### 5. `notebook-regression-monitor`

Purpose: Continuously check that book notebooks remain executable and numerically stable.

Responsibilities:

- run selected notebooks or scripts,
- compare key outputs against tolerances,
- verify generated figures exist and were refreshed,
- write `replication_status.json`,
- classify notebooks as pass, stale, broken, or expensive-skip.

Why it matters: It keeps computational textbooks alive as NeqSim evolves.

### 6. `exercise-progression-builder`

Purpose: Analyze and improve exercise difficulty and cross-chapter integration.

Responsibilities:

- classify exercises by type and difficulty,
- ensure each chapter has conceptual, calculation, design, and reflection exercises as appropriate,
- generate cross-chapter capstone exercises,
- create solution-outline stubs for instructors.

Why it matters: It turns a textbook into a course-ready learning system.

### 7. `standards-traceability-curator`

Purpose: Audit and strengthen standards-heavy content.

Responsibilities:

- extract API, DNV, ISO, IEC, NORSOK, ASME, and local regulation references,
- map statements to clauses or paraphrased requirements,
- mark broad unsupported compliance claims,
- maintain `standards_traceability_matrix.json`.

Why it matters: It makes engineering guidance defensible for industry readers.

### 8. `case-thread-continuity-editor`

Purpose: Ensure recurring field cases remain consistent and pedagogically useful.

Responsibilities:

- track recurring cases such as Snøhvit, Aasta Hansteen, and Ultima Thule,
- verify that assumptions do not drift unintentionally,
- maintain a case parameter registry,
- add transitions that remind students how the case evolves.

Why it matters: Case continuity is the difference between a textbook with examples and a textbook with a memorable engineering story.

### 9. `figure-accessibility-and-style-reviewer`

Purpose: Audit figure readability, accessibility, and style consistency.

Responsibilities:

- check axes, units, legends, captions, contrast, font size, and color accessibility,
- classify figures as teaching figures, evidence figures, schematic figures, or decorative figures,
- suggest style harmonization across chapters,
- generate a figure readability score.

Why it matters: Engineering books depend heavily on figures; poor visual consistency weakens learning even when the science is correct.

### 10. `interactive-lab-designer`

Purpose: Convert static NeqSim notebooks into student-facing computational labs.

Responsibilities:

- identify notebook cells that can become parameterized labs,
- define safe input sliders/ranges,
- provide static fallback tables/figures for PDF,
- generate HTML lab manifests and student instructions.

Why it matters: The HTML book can become an interactive engineering environment, not just a rendered manuscript.

### 11. `chapter-readiness-scorer`

Purpose: Combine multiple audits into a chapter health dashboard.

Responsibilities:

- aggregate readability, evidence, equation, notebook, exercise, and figure scores,
- classify chapters as ready, minor revision, major revision, or blocked,
- produce a sortable `chapter_health_dashboard.md`.

Why it matters: It gives authors a quick way to decide what to fix next.

### 12. `book-release-orchestrator`

Purpose: Run the complete multi-agent book release process.

Responsibilities:

- run prerequisite audits in the right order,
- collect outputs from all book agents,
- stop release on blockers,
- regenerate HTML, standalone HTML, PDF, DOCX, ODF as configured,
- write a final `release_gate_report.md`.

Why it matters: PaperLab already has the pieces; this agent would make the release workflow predictable.

## Recommended New Skills

### 1. `paperlab_curriculum_prerequisite_graph`

Defines how to extract concepts, prerequisites, and chapter dependencies. Used by `curriculum-architect`.

### 2. `paperlab_learning_objective_matrix`

Defines the schema for mapping learning objectives to sections, figures, notebooks, examples, exercises, and assessments. Used by `learning-objective-verifier`.

### 3. `paperlab_equation_dimensional_audit`

Defines equation extraction, unit checking, symbol collision detection, and nomenclature synchronization. Used by `equation-consistency-auditor`.

### 4. `paperlab_neqsim_api_claim_verification`

Defines how to verify book API snippets against Java source, tests, notebooks, and deprecation information. Used by `neqsim-api-verifier`.

### 5. `paperlab_notebook_regression_baselines`

Defines notebook output baselines, tolerances, figure staleness rules, and pass/stale/broken classifications. Used by `notebook-regression-monitor`.

### 6. `paperlab_exercise_difficulty_ramp`

Defines exercise types, difficulty scoring, Bloom-level classification, and cross-chapter integration exercise patterns. Used by `exercise-progression-builder`.

### 7. `paperlab_standards_clause_traceability`

Defines how to map standards statements to standard, revision, clause, paraphrase, and evidence strength. Used by `standards-traceability-curator`.

### 8. `paperlab_case_thread_continuity`

Defines case registries, assumption drift checks, and cross-chapter case continuity rules. Used by `case-thread-continuity-editor`.

### 9. `paperlab_figure_accessibility_style`

Defines figure readability and accessibility checks: axis labels, units, color contrast, legend placement, font size, and print safety. Used by `figure-accessibility-and-style-reviewer`.

### 10. `paperlab_interactive_html_labs`

Defines how static notebooks become interactive labs in HTML with safe inputs and static fallbacks. Used by `interactive-lab-designer`.

### 11. `paperlab_chapter_health_dashboard`

Defines composite scoring from audit outputs and chapter release classifications. Used by `chapter-readiness-scorer`.

### 12. `paperlab_book_release_orchestration`

Defines the order of audits, blocking criteria, output formats, and final release report. Used by `book-release-orchestrator`.

## Recommended Enhanced Pipeline

### Phase 1: Source and Curriculum Setup

Agents:

- `source-librarian`,
- `lecture-coverage-scout`,
- new `curriculum-architect`,
- new `learning-objective-verifier`.

Outputs:

- `source_manifest.json`,
- `coverage_audit.md`,
- `learning_path_audit.md`,
- `lo_achievement_matrix.json`.

### Phase 2: Technical Defensibility

Agents:

- new `equation-consistency-auditor`,
- new `neqsim-api-verifier`,
- new `standards-traceability-curator`,
- existing `paperlab_scientific_traceability_audit` skill.

Outputs:

- `equation_audit.json`,
- `neqsim_api_audit.json`,
- `standards_traceability_matrix.json`,
- `scientific_traceability_report.md`.

### Phase 3: Computational Reproducibility

Agents:

- `notebook-verifier`,
- new `notebook-regression-monitor`,
- `validation-agent`,
- `figure-generator`.

Outputs:

- `replication_status.json`,
- refreshed figures,
- `approved_claims.json` where statistical claims are involved,
- notebook baseline files.

### Phase 4: Pedagogy and Exercises

Agents:

- `chapter-synthesis-editor`,
- `exam-alignment-reviewer`,
- new `exercise-progression-builder`,
- new `case-thread-continuity-editor`.

Outputs:

- `exercise_difficulty_ramp.json`,
- `integration_exercise_proposals.md`,
- case assumption registry,
- strengthened chapter summaries and transitions.

### Phase 5: Visual and Interactive Upgrade

Agents:

- `technical-figure-reader`,
- `figure-context-editor`,
- new `figure-accessibility-and-style-reviewer`,
- new `interactive-lab-designer`.

Outputs:

- `figure_style_audit.json`,
- stronger captions and discussion blocks,
- `interactive_lab_manifest.json`,
- static PDF fallbacks.

### Phase 6: Release Gate

Agents:

- `book-conciseness-editor`,
- `book-adversarial-reviewer`,
- new `chapter-readiness-scorer`,
- new `book-release-orchestrator`.

Outputs:

- `chapter_health_dashboard.md`,
- `conciseness_audit.md`,
- `required_fixes.json`,
- `release_gate_report.md`,
- final HTML, standalone HTML, PDF, DOCX, ODF.

## Priority Implementation Plan

### P0: Immediate High-Impact Additions

1. `learning-objective-verifier` + `paperlab_learning_objective_matrix`.
2. `neqsim-api-verifier` + `paperlab_neqsim_api_claim_verification`.
3. `notebook-regression-monitor` + `paperlab_notebook_regression_baselines`.

These would catch the most damaging issues: promised-but-unsupported learning outcomes, broken API examples, and stale computational figures.

### P1: Technical Excellence

4. `equation-consistency-auditor` + `paperlab_equation_dimensional_audit`.
5. `standards-traceability-curator` + `paperlab_standards_clause_traceability`.
6. `figure-accessibility-and-style-reviewer` + `paperlab_figure_accessibility_style`.

These would move books from good course material toward professional engineering reference quality.

### P2: Advanced Student Experience

7. `exercise-progression-builder` + `paperlab_exercise_difficulty_ramp`.
8. `interactive-lab-designer` + `paperlab_interactive_html_labs`.
9. `case-thread-continuity-editor` + `paperlab_case_thread_continuity`.

These would make the books much more useful for students and instructors.

### P3: Whole-System Orchestration

10. `curriculum-architect` + `paperlab_curriculum_prerequisite_graph`.
11. `chapter-readiness-scorer` + `paperlab_chapter_health_dashboard`.
12. `book-release-orchestrator` + `paperlab_book_release_orchestration`.

These would make PaperLab scalable across many books.

## Specific Recommendations for the TPG4230 Book

1. Keep the current 32-chapter structure. The numbering and progression are coherent, and the remaining adjacent-chapter similarity is mostly domain continuity, not duplication.

2. Build a learning-objective matrix next. The book is large enough that manual confidence is not enough; each chapter objective should map to content, figures, notebooks, and exercises.

3. Prioritize NeqSim API verification for Chapters 24-26 and the case-study chapters. These are where code drift is most likely and most visible.

4. Add notebook regression baselines for all notebook-heavy chapters. Chapters 1, 3, 4, 11, 20, 24, and 26 are especially important because they bridge theory and simulation.

5. Add standards traceability for Chapters 10, 13, 14, 21, 22, 23, and 25. These chapters contain the highest density of regulatory or standards-linked engineering statements.

6. Add an exercise progression audit before course release. The book should intentionally ramp from chapter-level calculations to integrated Ultima Thule decision exercises.

7. Generate an interactive concept map for the HTML version. This would make the standalone HTML book feel like an engineering learning platform rather than a static manuscript.

8. Update `agents/README.md` to match the current agent inventory. The README still describes a smaller agent set than exists in the directory.

## Quality Gates for Future PaperLab Books

A future PaperLab book should be considered advanced only when these pass:

- all current `book-check` and `book-evidence-check` gates,
- duplicate paragraph groups and near-duplicate pairs are zero or intentionally waived,
- every learning objective has at least one teaching asset and one assessment asset,
- every main figure has provenance and discussion,
- every major equation has units, symbols, and source/implementation link,
- every NeqSim code example is API-verified,
- every notebook-heavy chapter has a reproducibility baseline,
- every standards-heavy chapter has standards traceability,
- every chapter has exercises with an intentional difficulty ramp,
- all generated outputs are refreshed: HTML, standalone HTML, PDF, and any course-release formats.

## Bottom Line

PaperLab already has the right foundation. To make the books better and more advanced, the next work should not be more generic writing agents. It should be specialized engineering-book agents that verify learning, formulas, APIs, standards, notebooks, figures, exercises, and release readiness as a connected system.

The best next three builds are:

1. `learning-objective-verifier`,
2. `neqsim-api-verifier`,
3. `notebook-regression-monitor`.

Together they would make PaperLab books more trustworthy for students, more maintainable for authors, and more credible as NeqSim-backed engineering references.
