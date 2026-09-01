---
name: neqsim-professional-reporting
version: "1.0.0"
description: "Engineering deliverable quality — the nine analytical-depth moves (contributor ranking, adjudicating the source document, quantitative rule-outs, robustness crossover, conservatism direction, discriminating test), results.json schema, figure→discussion→linked_results traceability, evidence matrices, assumptions/gaps registers, citation conventions, KaTeX math formatting, units consistency, executive-summary structure, AACE class declaration. USE WHEN: producing a task report, a PEPR/M1/root-cause problem-solving report, building a notebook deliverable, or finalizing any engineering output that needs to look like it came from a senior engineer. Consolidates the rules scattered across AGENTS.md and copilot-instructions.md."
last_verified: "2026-07-09"
---

# NeqSim Professional Reporting Skill

What separates "an answer" from a **professional deliverable**: traceability,
unit hygiene, citation discipline, structured `results.json`, and a report
narrative that matches the way senior engineers communicate.

## When to Use

- Finalizing any task report under `task_solve/`
- Building Jupyter notebook deliverables (study-grade, not exploratory)
- Producing FEED-quality memos, technical notes, or design basis documents
- Any output that will be read by a reviewer, client, or auditor

## Principle 0 — Analytical depth (what makes a report worth reading)

Principles 1–10 are **hygiene**: they stop a report being wrong or unreadable.
They do not make it *useful*. A hygienic report that restates the originating
memo, lists contributors without ranking them, and ends in "further study is
recommended" passes every checklist below and tells the reader nothing they did
not already know.

The depth of a report is set in the **study**, not in the write-up. Plan for
these moves while the analysis is still running — most of them cannot be added
afterwards.

### The nine depth moves

Aim for **≥ 6 of 9** on a Standard report and **all 9** on a Comprehensive or
root-cause/problem-solving report. Record the score in `results.json`
(`depth_score`) and name the moves that were not achievable and why.

| # | Move | What it looks like | Anti-pattern it replaces |
|---|------|--------------------|--------------------------|
| 1 | **Rank the contributors on one common basis** | A single table of every candidate cause with an improvement factor or utilisation number, computed the same way, so they are directly comparable | An unranked bullet list of "contributing factors" |
| 2 | **Adjudicate the source document's own conclusions** | A verdict table over each recommendation of the originating memo/PEPR/notification: *Supported / Supported with a correction / Challenged*, each with the number that decides it | Silently agreeing with the source, or silently ignoring it |
| 3 | **Rule things out, quantitatively** | "Thermal cycling does not explain this, by three orders of magnitude" — a competing explanation eliminated with a number and a stated margin | Leaving every hypothesis nominally alive |
| 4 | **Find what the source document missed** | A contributor, coupling, or second-order consequence absent from the originating document, established from evidence — and stated as such | Answering only the question as posed |
| 5 | **Test the conclusion's robustness and say where it flips** | A sensitivity table over the one or two genuinely uncertain modelling parameters, plus the explicit crossover point: "the top two swap around a slope of ~0.7, but the headline does not depend on it" | A single-point answer with an unquantified caveat |
| 6 | **State the direction of every conservatism** | Each screening value, default, and correlation labelled as an upper or lower bound on the reported quantity, so the reader knows which way the number can move | Undirected "this is approximate" |
| 7 | **Name the cheapest discriminating test** | The single measurement or inspection that would confirm or refute the diagnosis, why it discriminates, and what each outcome would mean | A generic "further investigation is recommended" |
| 8 | **Report what does not fit** | The observation that disagrees with the model, reported as a disagreement rather than smoothed over or omitted | Presenting only corroborating evidence |
| 9 | **Convert qualitative asks into specifications** | "Effective oxygen exclusion" → a purity table with the consequence of each grade; "improve filtration" → a micron rating with the mechanism that sets it | Repeating the source's qualitative wording back |

### Two further depth habits

- **Look for the natural experiment in the data.** Near-identical units with
  different duty, a repaired section that failed again, a period with a barrier
  out of service — these discriminate between hypotheses far more cheaply than
  any model. Actively search the fleet/historian/maintenance record for one.
- **Bound rather than assert.** When a value cannot be measured, compute what it
  would have to be for the conclusion to change ("the screening factor of 4.0
  would require a shear ratio of 16; this geometry produces 2.74"). A bounded
  unknown is a result; an asserted unknown is a gap.

### Numerical results earn their own subsection

Any non-trivial computed result (CFD, FEM, transient, Monte Carlo, optimiser)
gets: **validation against an analytical or independent value first**, then a
**convergence/mesh/sample-count check**, then the result, then an explicit
statement of **what the computation does and does not decide**. A quantity that
still moves with refinement is an artefact and must be reported as one — report
the converged averaged measures, not the unconverged point maximum.

### Report skeleton for a problem-solving / root-cause / PEPR report

```
Executive summary          ranking table + the conclusion that inverts or
                           confirms the source document + N further findings
                           (count them; keep the count in sync)
0. Design/evidence basis   what was retrieved, with document ids and revisions,
                           and the two or three basis facts that change the reading
1..n Findings              one section per finding, each: observation (numbers) →
                           mechanism (physics) → implication (for the decision) →
                           recommendation (specific action)
n+1 Robustness             sensitivity table + where the conclusion flips
n+2 Ruled out              each eliminated hypothesis with its quantitative margin
n+3 Assessment of the      verdict table over the source document's own
    source's recommendations recommendations
n+4 What remains open      per-finding, not one lumped register; each with the
                           test that would close it and its owner
```

Every section that reaches a conclusion ends with **"what remains open"** for
that conclusion specifically. One consolidated gap register at the end of a
report is where gaps go to be ignored.

### Depth failure modes to check for before sending

| Symptom | What it means |
|---------|---------------|
| The report's recommendations are the source document's recommendations | Moves 1–4 were not attempted |
| Every hypothesis is still "possible" | Move 3 was not attempted |
| The only number in the executive summary is a restatement of the input | The study produced description, not analysis |
| "Further study is recommended" with no named test | Move 7 was not attempted |
| No sentence in the report contradicts anything | Moves 2, 4, and 8 were not attempted — verify this is genuinely the case, not avoidance |

## Principle 1 — Traceability Chain (MANDATORY)

Every figure → discussion → result must be linkable both ways:

```
results.json[key] ──→ discussed in §3.2 ──→ shown in figures/fig_03.png
              ↑                                        ↓
              └──── caption references key ────────────┘
```

Required JSON schema fragment:

```json
{
  "figures": [
    {
      "id": "fig_03",
      "path": "figures/fig_03_phase_envelope.png",
      "caption": "Phase envelope at 95 mol% methane composition.",
      "discussed_in": "section_3_2",
      "linked_results": ["dew_point_T_K", "cricondentherm_K"]
    }
  ],
  "results": {
    "dew_point_T_K": {"value": 244.3, "unit": "K", "source": "neqsim TPflash"},
    "cricondentherm_K": {"value": 254.8, "unit": "K", "source": "calcPTphaseEnvelope"}
  }
}
```

## Principle 2 — Executive Summary Structure

Every report opens with a 1-page executive summary built from these blocks (in order):

1. **Objective** — one sentence: "Determine X for Y under Z conditions."
2. **Method** — one sentence: "Using EOS / equipment model / standard X."
3. **Key result** — 2–3 numbers with units and uncertainty (P10/P50/P90 if Monte Carlo run)
4. **Conclusion** — one sentence with the engineering decision
5. **Limitations** — 1–2 bullets on key caveats

The executive summary and problem description are report-blocking sections. Do
not leave template text such as "[Replace with ...]" or "[Auto-populated ...]"
in a final HTML/Word report. If `results.json` and `task_spec.md` contain enough
information, generate these sections automatically from those sources; otherwise
pause and fill the missing source material before finalizing.

## Principle 3 — Units & Significant Figures

- **State units everywhere** — `bara`, `°C`, `kg/h`, `MJ/Sm³`, never bare numbers
- **Significant figures match accuracy** — 3 sig fig for thermo; 2 for cost; never more than 4 unless source is exact
- **Consistent within report** — pick one set (SI, °C/bara) and don't switch
- **Standard conditions** — always disclaim Sm³ basis (15 °C / 1.01325 bara, or 20 °C, or 0 °C — they differ ~5%)
- **Stream tables** — use standardized columns: name, T [°C], P [bara], ṁ [kg/h], xi [mol%]

## Principle 4 — Citations

For every standard, correlation, or vendor source:

```markdown
Per **API 521 §5.15 (2020)**, fire heat input is Q = C × F × A_w^0.82 [API521-2020].

References:
[API521-2020]  API Standard 521, Pressure-Relieving and Depressuring Systems, 7th ed., 2020.
[NORSOK-P-100] NORSOK Standard P-100, Process Systems, Rev. 3, 2018.
[Turton-5e]    Turton et al., Analysis, Synthesis and Design of Chemical Processes, 5th ed., 2018.
```

Avoid: "as is well known", "industry standard says". State the source.

## Principle 5 — Math (KaTeX)

For documents rendered through Jekyll docs site:

```markdown
Inline: the acentric factor $\omega$ affects $\alpha(T_r, \omega)$.

Display:
$$
P = \frac{RT}{v - b} - \frac{a(T)}{v(v + b)}
$$
```

Never use `\[ ... \]` or `\( ... \)` — they are stripped by markdown processors.

## Principle 6 — Figure Quality

Every plot must have:
- **Axis labels with units** — `Pressure [bara]`, not `P`
- **Title** — what is shown, at what conditions
- **Legend** — even with 1 series (states what is plotted)
- **Grid** — minor or major, increases readability
- **Annotation of key values** — pinch point, surge line, design point
- **Resolution** — ≥ 150 DPI for embedding, vector (SVG/PDF) preferred for line plots

```python
fig, ax = plt.subplots(figsize=(8, 5), dpi=150)
ax.plot(T, P, label="Phase envelope")
ax.scatter([T_op], [P_op], color="red", marker="x", s=80, label="Operating point")
ax.set_xlabel("Temperature [K]")
ax.set_ylabel("Pressure [bara]")
ax.set_title("Phase envelope — sales gas, 95% C1")
ax.legend(loc="best", fontsize=9)
ax.grid(alpha=0.3)
fig.tight_layout()
fig.savefig("figures/fig_03_phase_envelope.png", dpi=150)
```

## Principle 7 — Uncertainty Disclosure

Standard / Comprehensive task reports MUST include:

- **Monte Carlo with P10 / P50 / P90** for any economic or reservoir-tied output
- **Tornado diagram** ranking inputs by impact on the key output
- **Sensitivity scan** to top-3 driving inputs
- **AACE class declaration** for any cost number (Class 5: ±100%, Class 4: ±50%, Class 3: ±30%)

Quick tasks may skip MC but still must state qualitative uncertainty.

**`uncertainty` sub-schema (validated by the gate).** `p10`, `p50`, `p90` must be
**numeric** and **monotonically ordered** (`p10 ≤ p50 ≤ p90`); a non-numeric or
out-of-order percentile is a hard error in both `TaskResultValidator` and
`devtools/validate_task_results.py`. Include `method` and `n_simulations`
(≥ 200 when the Monte Carlo loop runs full NeqSim simulations).

The community skill `neqsim-uncertainty-quantification` emits this block
directly (`UncertaintyReport.to_results_json()`), in the correct ascending
convention, with the sampler and seed, the tornado, a convergence check, and a
`blockers` field. Note the trap it guards: `p10` here is the 10th percentile
(the *low* estimate), the opposite of the petroleum resource convention where
P10 is the optimistic volume. State which convention a resource table uses.

## Principle 8 — Risk Section

Standard / Comprehensive reports include a **risk register** scored on a 5×5 matrix
(probability × consequence) per ISO 31000 / NORSOK Z-013, with mitigation actions.
Use [`neqsim-process-safety`](../neqsim-process-safety/SKILL.md) classes.

## Principle 9 — Benchmark Validation

Every numerical result must be benchmarked against an independent reference:

| Output                  | Benchmark                                               |
| ----------------------- | ------------------------------------------------------- |
| Phase envelope          | Lab CME / CVD / GERG-2008 reference                     |
| Equipment cost          | Vendor budget quote OR another correlation              |
| Heat duty               | Hand check: Q = ṁ × cp × ΔT                            |
| PSV size                | Independent calc per API 520 worked example             |
| NPV                     | Two methods: DCF and (NPV/CAPEX) ratio                  |

State the benchmark in the report. **No benchmark = result is provisional.**

**`benchmark_validation` sub-schema (validated by the gate).** Emit it as a JSON
array (or an object wrapping `benchmarks`/`cases`). Each entry must carry:

| Field | Purpose |
|-------|---------|
| `what` / `name` / `output` / `parameter` | what was compared |
| `reference` / `source` / `benchmark` / `reference_value` | the independent reference |
| `delta_pct` / `deviation_pct` / `status` / `neqsim_value` | the comparison result |
| `status` (optional) | one of `PASS`, `FAIL`, `WARN`, `INFO` (any other value is rejected) |

Both `TaskResultValidator` (Java) and `devtools/validate_task_results.py` (the CI
gate) now check this structure, so a malformed benchmark block fails the gate
instead of crashing the report generator.

The community skill `neqsim-benchmark-reference-data` emits this block directly
(`BenchmarkReport.to_results_json()`), together with the citation, the authority
tier of the reference, whether the deviation is inside the reference's own
uncertainty, and the three-graded-point check. Prefer it over hand-writing the
block with pasted reference literals.

## Principle 9b — Evidence Matrix for Safety Studies

For safety-critical studies, especially trapped-liquid fire rupture, relief,
depressurization, MDMT, and consequence handoffs, include an evidence matrix and
assumptions/gaps register in both `results.json` and the report:

| Report item | Required content |
|-------------|------------------|
| Evidence matrix | Document id, title, revision, page/sheet, extracted value, unit, confidence, consuming calculation |
| Assumptions/gaps | Missing value, screening default used, impact on result, action to close, owner if known |
| Standards basis | Standard number/year, clause/table/equation, PASS/FAIL/INFO status |
| Segment summary | Segment id, limiting mode, event times, PFP margin, source-term handoff status |
| Recommendations | Specific action: relief/PFP/procedure/data retrieval/detailed specialist analysis |

Do not hide missing material certificates, flange/gasket/bolt ratings, fire-study
heat fluxes, or acceptance criteria. A study may still provide screening results,
but the executive summary must state when final design is blocked by evidence gaps.

Safety-critical reports must include a front-page readiness badge or equivalent
plain-text label: `NOT_READY`, `SCREENING`, or `DESIGN_GRADE`. The label must be
backed by visible blockers/findings and must not imply sign-off when any
controlled-document, historian/tagreader, pressure-profile, or material basis is
missing or unreviewed.

For script-backed studies, `study_config.yaml` is the source of truth for whether
notebooks are required. A report generator should not warn about missing planned
notebooks when the configuration explicitly says `notebooks.required: false`,
`execution_required: false`, and `execution_engine: script`.

Before report generation, check consistency between `task_spec.md`, analysis
scripts/notebooks, `results.json`, and the report narrative. Method changes such
as replacing a reconstructed depressurization profile with a directly exported
dynamic NeqSim profile must be reflected everywhere, including
`capability_assessment.md`, `analysis.md`, and `neqsim_improvements.md` when
workflow gaps were found.

## Principle 10 — `results.json` Master Schema

```json
{
  "task_id": "2026-04-26_my-task-slug",
  "task_type": "B-process",
  "scale": "standard",
  "objective": "...",
  "method_summary": "...",
  "agent_workflow_plan": {
    "discovery": {"skill_search": "devtools/skill_search.py", "agent_search": "step1_scope_and_research/agent_plan.json"},
    "agents_used": [ {"name": "...", "repo": "neqsim|community|enterprise", "role": "...", "loads_skills": ["..."]} ],
    "workflow_type": "single_agent | composition_pattern | declarative_workflow",
    "workflow": "e.g. process.model -> mechanical.design, or composeWorkflow id / harness study name",
    "rationale": "why this composition utilizes the needed functionality"
  },
  "key_results": {
    "primary_metric": {"value": 1.23, "unit": "MW", "uncertainty": "±10%"},
    "...": {}
  },
  "results": { "...": "..." },
  "figures": [ { "id": "fig_01", "path": "...", "caption": "...", "discussed_in": "...", "linked_results": [] } ],
  "tables": [ { "id": "tbl_01", "path": "...", "caption": "..." } ],
  "uncertainty": { "method": "Monte Carlo n=10000", "P10": ..., "P50": ..., "P90": ... },
  "risks": [ { "id": "R1", "description": "...", "P": 3, "C": 4, "score": 12, "mitigation": "..." } ],
  "standards_applied": ["API 521-2020", "NORSOK Z-013"],
  "benchmarks": [ { "what": "PSV area", "reference": "API 520 Ex 5", "delta_pct": 1.2 } ],
  "evidence_matrix": [ { "document": "...", "value": "...", "used_for": "..." } ],
  "assumptions_gaps": [ { "gap": "...", "default_used": "...", "impact": "...", "action": "..." } ],
  "contributor_ranking": [ { "contributor": "...", "lever": "...", "improvement_factor": 20.0, "basis": "..." } ],
  "ruled_out": [ { "hypothesis": "...", "margin": "3 orders of magnitude", "basis": "...", "residual_caveat": "..." } ],
  "source_recommendation_assessment": [ { "recommendation": "...", "verdict": "SUPPORTED|SUPPORTED_WITH_CORRECTION|CHALLENGED", "basis": "..." } ],
  "robustness": { "parameter": "...", "range": "...", "conclusion_stable": true, "crossover": "..." },
  "conservatism": [ { "value": "...", "direction": "upper_bound|lower_bound", "effect_on_result": "..." } ],
  "discriminating_test": { "test": "...", "why_it_discriminates": "...", "outcome_if_positive": "...", "outcome_if_negative": "...", "cost": "..." },
  "depth_score": { "achieved": 8, "of": 9, "missing": [ { "move": 5, "why": "..." } ] },
  "limitations": ["..."],
  "next_actions": ["..."]
}
```

## Common Mistakes

| Mistake                                          | Fix                                                                 |
| ------------------------------------------------ | ------------------------------------------------------------------- |
| "About 100 kg/hr" in a final report              | State value with sig figs and uncertainty                           |
| Mixing barg / bara silently                      | One pressure basis per report; document conversion                  |
| Cost without escalation year                     | Always cite CEPCI year and Class of estimate                        |
| 6-decimal numbers from a simulator               | Round to 3 sig fig; simulator precision ≠ result accuracy           |
| Figure with no caption / no axis units           | Reject — these are unread placeholders                              |
| "Standard says" without citation                 | Provide doc, year, section                                          |
| No benchmark validation                          | Run hand check or compare to literature; report deviation %         |
| Discussion that doesn't reference its figures    | Use `[fig_03]` cross-references in prose                            |

## Validation Checklist (RUN BEFORE FINALIZING)

**Depth (Principle 0) — check these first; they cannot be fixed by editing prose:**

- [ ] `depth_score` recorded, ≥ 6/9 (Standard) or 9/9 (Comprehensive / root-cause)
- [ ] Contributors ranked on one common basis, not merely listed
- [ ] Each recommendation of the originating document given an explicit verdict
- [ ] At least one competing hypothesis ruled out with a stated quantitative margin
- [ ] Robustness tested, with the crossover point named
- [ ] Every screening default labelled upper or lower bound
- [ ] One named discriminating test, not "further study recommended"
- [ ] Any evidence that does not fit the conclusion is reported
- [ ] Every conclusion carries its own "what remains open", not one lumped register

**Hygiene:**

- [ ] Executive summary present, 1 page max
- [ ] Every figure referenced in text and has caption + units
- [ ] Every result in `results.json` traceable to figure or table
- [ ] Units consistent and labelled everywhere
- [ ] Standards cited by document number, year, section
- [ ] Uncertainty (P10/P50/P90) for every economic / reservoir result
- [ ] Risk register with 5×5 scoring (Standard+ tasks)
- [ ] Benchmark comparison ≤ 5% deviation OR justified
- [ ] AACE class declared for cost numbers
- [ ] `python devtools/consistency_checker.py` passes
- [ ] Limitations section honest about model assumptions
- [ ] Next-actions list at end (what would close the gaps)

## Pre-send review (the pass that catches stale numbers)

A report assembled incrementally accumulates contradictions: an early section
states a first-pass number, a later section supersedes it, and the early one
survives. `consistency_checker.py` does not catch these — they are internally
well-formed. Run this pass separately, immediately before sending.

**1. Repeated-quantity sweep.** Extract every quantity that appears more than
once and confirm the values agree:

```python
import re, pathlib
t = pathlib.Path("step3_report/report.md").read_text(encoding="utf-8")
for q in ["boiling", "design flow", "margin"]:          # quantities to audit
    for i, line in enumerate(t.splitlines(), 1):
        if q in line.lower() and re.search(r"\d", line):
            print(i, line.strip()[:120])
```

Anything quoted at two different values must be either reconciled or explicitly
labelled with its basis ("13.1 bar against the design pressure, 10.3 bar against
the measured pressure").

**2. Numbered-list integrity.** Lead-ins like "Three further findings:" drift out
of sync when items are added. Count the items.

**3. Section numbering.** List `^## ` headings and check for gaps — an §8 → §10
jump reads as a missing section to a reviewer.

**4. Stale open/closed statuses.** Every "Open", "not yet retrieved", "would be a
free test" in the limitations and next-actions sections must be re-read against
what the study actually ended up doing. Work performed late in a study routinely
closes gaps that the gap register still lists as open.

**5. Alternative-basis values must be in `results.json` too.** If the report
quotes a quantity on both a design and a measured basis, both belong in
`results.json` — otherwise provenance closure passes on the primary value while
the secondary one is unsourced.

**Recurring physical-quantity trap.** A `T → P_saturation` table row read as if
that temperature were the boiling point at the *operating* pressure. Invert the
curve at the operating pressure; do not quote the nearest row. The same trap
applies to any monotonic property table used backwards (dew point, hydrate
curve, wax appearance).

## Related Skills

- [`neqsim-regression-baselines`](../neqsim-regression-baselines/SKILL.md) — locking baselines for traceability
- [`neqsim-input-validation`](../neqsim-input-validation/SKILL.md) — catching bad inputs early
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — citation lookup
- [`neqsim-process-safety`](../neqsim-process-safety/SKILL.md) — risk-register schema
- [`neqsim-trapped-liquid-fire-rupture`](../neqsim-trapped-liquid-fire-rupture/SKILL.md) — evidence matrix, rupture/PFP results, and source-term handoff reporting
