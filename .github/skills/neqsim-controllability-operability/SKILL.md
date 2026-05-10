---
name: neqsim-controllability-operability
version: "1.0.0"
description: "Process controllability and operability — operating envelope mapping, turndown analysis, startup/shutdown sequencing, control valve sizing per ISA-75 (Fl, Fp, choked flow), rangeability, hunting/loop tuning, recycle stability. USE WHEN: a task involves operability assessment, turndown studies, control valve sizing, startup/shutdown procedures, or 'will this design operate?' questions. Complements neqsim-dynamic-simulation (transient solver) with steady-state operability framing."
last_verified: "2026-04-26"
requires:
  java_packages: [neqsim.process.equipment.valve, neqsim.process.controllerdevice]
---

# NeqSim Controllability & Operability Skill

The "can this plant be operated?" half of process design — operating envelopes,
turndown, startup/shutdown sequencing, control valve sizing, and steady-state
operability checks. Pairs with [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md)
which solves the transient response.

## When to Use

- Mapping operating envelope (P, T, flow, composition window where the plant runs)
- Turndown analysis (10–100% capacity)
- Control valve sizing per ISA-75 / IEC 60534
- Startup / shutdown sequence definition
- Recycle stability / loop interaction screening
- Identifying hidden constraints (compressor surge, tray weeping, HX pinch)
- Evaluating P&ID-derived valve changes such as closing an isolation valve,
  partly closing a control valve, opening a bypass, or changing controller mode

For P&ID-driven operational changes, load `neqsim-pid-process-operations` first
to classify symbols, define the topology, bind plant tags, and turn each action
into a NeqSim model delta.

For MCP or Java workflows that already have a `ProcessSystem`, use
`neqsim.process.operations.envelope.OperationalEnvelopeEvaluator` or MCP
`runOperationalStudy` with `action="evaluateOperatingEnvelope"`. The evaluator
does not duplicate equipment physics; it ranks margins from
`EquipmentCapacityStrategyRegistry`, uses optional tagreader field data through
`OperationalTagMap`, and can turn margin history into simple time-to-limit
screening.

Standards: **ISA-75.01 / IEC 60534-2-1**, **API 685** (control valves), **NORSOK P-002** (operability).

## Pattern 1 — Operating Envelope Map

Sweep two key handles (e.g. flow and composition) and check that every constraint
is satisfied in the operating window:

```java
double[] flows = linspace(0.1, 1.0, 10);   // 10–100% turndown
double[] gors  = linspace(50.0, 800.0, 8); // GOR variation

for (double f : flows) {
    for (double g : gors) {
        SystemInterface fluid = makeFluid(g);
        feed.setFlowRate(f * design_kgh, "kg/hr");
        process.run();
        envelope.recordPoint(f, g, checkConstraints(process));
    }
}
```

Constraints to check on **every** point:

| Constraint              | Check                                                | Source                |
| ----------------------- | ---------------------------------------------------- | --------------------- |
| Compressor surge        | Q_actual > Q_surge × 1.10                            | Vendor curve          |
| Compressor stonewall    | Q_actual < Q_choke × 0.95                            | Vendor curve          |
| Separator vapor velocity | v < K × √((ρL−ρV)/ρV)                              | NORSOK P-100 (K=0.107) |
| Separator residence time | t_res ≥ 3 min liquid, ≥ 30 s gas                    | API 12J               |
| HX ΔTmin                | ΔT > 5 °C everywhere                                 | Pinch design          |
| Column flooding         | C_factor < 0.85 × C_flood                            | Souders–Brown         |
| Column weeping          | C_factor > 0.55 × C_flood                            | Tray vendor           |
| Pump NPSHa              | NPSHa > NPSHr + 1 m margin                           | API 610               |
| Control valve opening   | 20% < travel < 80%                                   | ISA-75                |

## Pattern 2 — Control Valve Sizing (ISA-75)

```java
import neqsim.process.equipment.valve.ThrottlingValve;

ThrottlingValve cv = new ThrottlingValve("LV-1101", inletStream);
cv.setOutletPressure(P_downstream_bara);
cv.run();

double Cv_required = cv.getCv();          // ISA-75 sizing
double opening     = cv.getPercentValveOpening();
double dpRatio     = cv.getDeltaPressure() / cv.getInletPressure();
boolean choked     = dpRatio > cv.getFL() * cv.getFL() * (1 - cv.getFf() * cv.getXt());
```

**Sizing rules:**
- Pick valve such that **normal flow** is at **60–70% open**
- **Minimum flow** must be at **> 20% open** (rangeability check)
- **Maximum flow** must be at **< 90% open** (margin)
- For choked flow: use **Fl × ΔP_choke** instead of actual ΔP

**Rangeability:**

| Trim type        | Rangeability |
| ---------------- | ------------ |
| Linear           | 30:1         |
| Equal percentage | 50:1         |
| Quick opening    | 10:1         |

If turndown ratio > rangeability, you need a **split-range** or two valves in parallel.

## Pattern 3 — Recycle Stability

A process with strong recycles can have multiple steady states or be unstable:

```java
ProcessSystem.RecycleConvergenceMonitor mon = process.getRecycleMonitor();
process.run();
double[] residual = mon.getMassResidualHistory();
// Plot residual vs iteration — should be monotone decreasing.
// If oscillating: recycle interactions; if diverging: bad initial guess or fundamental instability
```

**Stabilization tactics:**
- Increase damping factor on recycle stream (0.3–0.7)
- Set initial guess from previous-load solution (warm start)
- Decompose with `ProcessModel` so each area converges first
- Add an explicit pressure setpoint instead of free-floating P

## Pattern 4 — Startup / Shutdown Sequence

Document each major mode as a state machine:

| State          | P / T / flow                                | Active controllers                  | Active SIFs   |
| -------------- | ------------------------------------------- | ----------------------------------- | ------------- |
| Cold shutdown  | atmospheric, ambient                        | none                                | all in OOS    |
| Pressurization | ramp to design × 0.3 in 30 min              | PIC slow ramp                       | HIPPS armed   |
| Cold circulation | full rate, ambient (recycle to flare)     | LIC, FIC                            | all armed     |
| Heat-up        | ramp 50 °C/h to design T                    | TIC, LIC, FIC                       | all armed     |
| Normal         | design point                                | all loops in AUTO                   | all armed     |
| Emergency BD   | depressurize per API 521 in 15 min          | controllers tripped                 | BDV open      |

Use [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) to validate the transient.

## Pattern 5 — Loop Tuning Sanity

For each PID:
- **Gain (Kp)**: 0.5 × Ziegler–Nichols ultimate; tighten only if oscillation
- **Integral (Ti)**: 1–3 × dead time
- **Derivative (Td)**: usually off; only for slow exotherms / temperature

Common bad loops:
- Level on a vessel with rapid throughput change → use averaging level (P-only, low gain)
- Composition (e.g. column reflux) → cascade onto temperature
- Surge control on compressor → fast PI + proportional kicker

## Pattern 6 — Operability Scorecard for the Report

```
Operability Scorecard
=====================
Turndown        : 25–110% of design     PASS (target ≥ 25%)
Compressor      : surge margin 18% @ low end   PASS (≥ 10%)
Column          : flooding margin 22% @ high end  PASS (≥ 15%)
Recycle stab.   : converges in 8 iters @ all loads  PASS
Control valves  : 4 valves outside 20–80% range — RESIZE
Startup time    : 6.5 hr (cold→full)    PASS (target ≤ 8 hr)
SD time         : 12 min API 521 BD     PASS (target ≤ 15 min)
```

## Common Mistakes

| Mistake                                                   | Fix                                                                |
| --------------------------------------------------------- | ------------------------------------------------------------------ |
| Sizing CV with normal ΔP and forgetting choked flow       | Always check σ = ΔP / Fl² × ΔP_max; if exceeded use choked formula |
| One CV across full 10–110% range                          | Rangeability > 30:1 needs split-range or two valves                |
| Designing only at 100% flow                               | Min/max constraints often govern (surge, weeping, NPSH)            |
| Ignoring recycle interactions until commissioning         | Decompose with `ProcessModel`, verify convergence at each load     |
| Aggressive Kp on level control                            | Causes throughput oscillation downstream; use P-only averaging      |
| No startup BD path                                        | Cold flare/atmospheric vent path required for trip-from-pressurized |
| Using "design rate" pump curve                            | Pumps often run at 60–80% Qbep at normal — pick BEP at normal      |

## Validation Checklist

- [ ] Operating envelope plotted with turndown range and at least 2 disturbance axes
- [ ] All control valves: 20% < normal opening < 80%
- [ ] Compressor surge margin ≥ 10% at lowest expected flow
- [ ] All recycles: convergence in ≤ 20 iterations across the envelope
- [ ] Startup sequence documented as state machine with controller modes
- [ ] Emergency BD validated dynamically per API 521 (≤ 15 min to 50% MAWP)
- [ ] Operability scorecard included in the report (`results.json` → `operability` section)

## Related Skills

- [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) — solve the transient
- [`neqsim-process-safety`](../neqsim-process-safety/SKILL.md) — link operability gaps to safety scenarios
- [`neqsim-relief-flare-network`](../neqsim-relief-flare-network/SKILL.md) — startup/SD blowdown loads
- [`neqsim-platform-modeling`](../neqsim-platform-modeling/SKILL.md) — multi-area recycle decomposition
