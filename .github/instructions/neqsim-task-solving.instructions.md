---
description: "Use when solving a NeqSim engineering task, study, design, screening, report or analysis, in a source checkout or with only the plugin installed."
applyTo: "**"
---

# Solving a NeqSim engineering task

A quick answer needs no ceremony: give the result with units, assumptions and a validation note.
Anything that is a study, design, screening, comparison, notebook or report is a **task** and follows
this workflow. Do not improvise your own folder layout or report format.

## Route first

1. **Use the `solve-task` agent** for a full task (`solve-process` for a model-only deliverable,
   `process-study-report`, `flow-assurance-study-report`, `pvt-fluid-study-report`, `surf-design-study`
   for those report types). If you are not already that agent, delegate to it with the verbatim request.
2. **Check the environment once per session** before the first task:
   `neqsim doctor` (or `<python> -m neqsim_cli doctor`), then `neqsim --show-task-root`,
   `neqsim --show-report-template`, `neqsim --show-document-root`. Report a missing toolkit or Java as a
   blocker with the remedy; never fall back to a hand-made folder.
3. **Ask once, not every time**, when a setting is empty and the task needs it. Offer to save it:
   `neqsim --set-task-root <folder>` (where tasks are created), `neqsim --set-report-template <.docx>`
   (company Word styling), `neqsim --set-document-root <folder>` (standards/datasheets library, read-only).
   The user can decline; the built-in defaults then apply.

## The fixed task structure

1. **Create the folder first** with
   `neqsim new-task "<title>" --type X --author "<name>" --prompt "<verbatim request>"`
   (`neqsim` missing: `<python> -m neqsim_cli new-task ...`). It makes `YYYY-MM-DD_<slug>/` with
   `step1_scope_and_research/`, `step2_analysis/`, `step3_report/`, `figures/`, `results.json`,
   `user_input.md`, `README.md`. Never create it by hand. Pass the absolute path to every child agent.
   Resume an existing task in place.
2. **Step 1, scope and research:** `analysis.md`/`notes.md`, `capability_assessment.md`. Search earlier
   tasks (`neqsim tasks search <keywords> --exclude <this task>`) and record the hits. Find skills and
   agents (`-m skill_search`, `-m agent_search`) and load only the relevant sections.
   Documents live under `step1_scope_and_research/references/<source>/`; search the document root
   (`neqsim documents PATTERN`) and run `neqsim fetch-docs <task>` before calling a standard, datasheet
   or P&ID unavailable. Check installed data-source agents (STID, PDM, plant data) against the request and
   record each in `results.json` `agent_workflow_plan.evidence_sources`.
3. **Step 2, analysis:** scripts and notebooks run end to end in this folder (never unexecuted).
   Use an MCP tool for a single calculation; write Python only for loops, plots, state or notebooks.
   Validate against independent data or a hand calculation; for Standard/Comprehensive tasks also run
   Monte Carlo P10/P50/P90 with a tornado, an ISO 31000 risk register and
   `-m consistency_checker <task>` before the report. Every flow diagram passes `neqsim pfd-check`.
4. **`results.json`** passes `-m validate_task_results <task>`; `assumptions` and `data_gaps` are always
   filled (`data_gaps` entries are objects with `gap`, `source`, `status`, `assumed`, `effect`;
   `conclusions` is a string).
5. **Step 3, report:** `neqsim report <task>` (Word + HTML, `--pdf` optional). Put hand-written prose in
   `step3_report/report_sections.json`; never open or edit `generate_report.py`. The run writes
   `WORK_RECORD.md`; fill its narrative blocks. Finish with a quality pass: read the generated report,
   fix formatting and wording until it is a clear, professional engineering report.
6. **All output stays in the task folder.** Never write to SharePoint or the document root. Do not
   commit task output or company data to any repository.
7. **Improve the tooling:** record each skill, agent or toolkit gap in `neqsim_improvements.md` and
   `results.json` `improvements` (or state that nothing was needed). Offer
   `neqsim file-issue <task>` for NeqSim gaps; file only with the user's authorization and remove private
   task data first.

## Without a NeqSim checkout

`AGENTS.md`, `CONTEXT.md` and `docs/...` are not installed; this file and the `neqsim-task-workflow`
skill carry the workflow. Load that skill per phase instead of reading repository paths.
