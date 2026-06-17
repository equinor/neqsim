---
name: neqsim-professional-reporting
version: "1.0.0"
description: "Engineering deliverable quality — results.json schema, figure→discussion→linked_results traceability, evidence matrices, assumptions/gaps registers, citation conventions, KaTeX math formatting, units consistency, executive-summary structure, AACE class declaration. USE WHEN: producing a task report, building a notebook deliverable, or finalizing any engineering output that needs to look like it came from a senior engineer. Consolidates the rules scattered across AGENTS.md and copilot-instructions.md."
last_verified: "2026-04-26"
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

## Principle 10 — `results.json` Master Schema

```json
{
  "task_id": "2026-04-26_my-task-slug",
  "task_type": "B-process",
  "scale": "standard",
  "objective": "...",
  "method_summary": "...",
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

## Related Skills

- [`neqsim-regression-baselines`](../neqsim-regression-baselines/SKILL.md) — locking baselines for traceability
- [`neqsim-input-validation`](../neqsim-input-validation/SKILL.md) — catching bad inputs early
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — citation lookup
- [`neqsim-process-safety`](../neqsim-process-safety/SKILL.md) — risk-register schema
- [`neqsim-trapped-liquid-fire-rupture`](../neqsim-trapped-liquid-fire-rupture/SKILL.md) — evidence matrix, rupture/PFP results, and source-term handoff reporting
