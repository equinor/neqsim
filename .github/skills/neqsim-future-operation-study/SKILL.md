---
name: neqsim-future-operation-study
description: "Screening-level future-operation and equipment-modification-register studies on top of a finished multi-year NeqSim plant model. USE WHEN: asked what a facility must change to operate to year N, which limits bind in which year, reconciling earlier studies, per-year operating strategy tables, modification registers with trigger year and evidence quality, sensitivity/tornado ranking, consultant or vendor study comparison, or design-basis deviation registers."
last_verified: "2026-10-28"
---

# Future-operation and modification-register study

A synthesis workflow, not a new solver. It sits on top of a solved year x case sweep
(see `neqsim-process-modeling` reference `multi-platform-complex` for building and
sweeping the model). The deliverable is a defensible, reviewable list of what changes
when, with the evidence quality stated for each item. Screening level: no item is a
design value.

## Inputs

- A solved, resumable year x case table (base/low/high) with a `status` column
  (`solved`, `solved, X RELAXED`, `unsolved: reason`), the utilisation register per year
  and the rate basis (stream-day or calendar-day).
- The earlier studies and their stated numbers (stable-oil, export-gas, power, ejector,
  compression, and similar), each with version, date and basis.
- The design basis and any consultant or vendor study to compare with.

## Workflow

1. **Reconcile the studies first.** One row per number that appears in more than one
   study: value, source, basis (stream/calendar day, mode definition, EOS, year
   definition), difference, resolution (which is used and why). Write the key
   resolutions in words. Refresh earlier-study tables when the sweep changed; record the
   percentage by which each statement moves (`synthesis_impact`).
2. **Per-year operating strategy table.** For every year: feed and export rates,
   pressures, active trains and modes (for example decoupled shafts, ejector on or off,
   minimum-flow status), binding constraint, status. Infeasible or partial years get a
   paragraph stating why and what is unmet; never smooth a failed year.
3. **Modification register.** One row per candidate: id, equipment, trigger year (first
   year utilisation exceeds the limit, from the register, not interpolated), problem and
   utilisation, options, recommended option, rough effect, CAPEX class if known,
   **evidence quality** (measured, vendor datasheet, calibrated model, screening model,
   assumption), dependencies, owner of the open data. Items that depend on an unmodelled
   effect are listed as such. Keep it as CSV plus a short table in the report.
4. **Robustness.** Sensitivity (one-at-a-time low/base/high) on feed scale, rates,
   setpoints and fluid-basis proxy; tornado ranking; then the pre-solved sweep to Monte
   Carlo pattern (surrogate on the solved runs, P10/P50/P90) for the headline numbers.
   State which items flip trigger year across the band.
5. **Benchmark against the consultant or vendor study.** Digitise the charts, align mode
   definitions and rate basis, then list: agreements, differences by cause (basis, data,
   physics), bottlenecks they report that the model does not cover with your own
   utilisation check, and conclusions that change. Add a dated update section instead of
   silently rewriting the earlier text.
6. **Deviation register against the design basis.** Requirement, clause, status
   (`compliant`, `deviates`, `not modelled`), model hook, impact ranking, justification.
   Run a strict configuration that reproduces the design basis before the best-estimate
   case; record contradictions inside the basis and the choice made.
7. **Open questions and data needs**, ranked by their effect on the headline answers,
   with an owner.

## Quality rules

- Distinguish what the model computed from what was assumed or taken from a document.
- Never present a year with an unsolved or relaxed status as a result; carry the status
  into every table that uses it.
- Do not mix stream-day and calendar-day volumes between tables.
- A modification is justified by the utilisation register and the trigger year, not by
  the existence of a spare option.
- Carry the calibrated vs independent flag into any statement that cites model accuracy.
- Keep generic facts in reusable tooling; keep company or asset data in the task folder.

## Related

- `neqsim-process-modeling` (reference `multi-platform-complex`) for the model, sweep and
  validation design.
- `neqsim-capacity-increase-screening` for ranking capacity ideas on an existing host.
- `neqsim-rotating-equipment-design` and `neqsim-compressor-antisurge-recycle` for
  compressor, shaft and driver limits that usually dominate the register.
- `neqsim-optimization-and-doe` for tornado and Monte Carlo tooling.
- `neqsim-professional-reporting` for the Word/PDF deliverable and `neqsim-task-workflow`
  for the task folder and status file conventions.
