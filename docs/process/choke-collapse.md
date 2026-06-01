---
title: "Choke Collapse Analysis"
description: "Diagnose loss of critical (choked / sonic) flow across throttling valves and chokes in NeqSim. Computes critical pressure ratio, classifies the flow regime, and flags flashing/cavitation in liquid service."
---

# Choke Collapse Analysis

`ChokeCollapseAnalyzer` diagnoses whether a `ThrottlingValve` is operating in
critical (sonic) flow, subcritical flow, or in the transition window between
the two. For liquid service it also flags **flashing** and **cavitation**.

## Theory

For an ideal gas, the throat reaches sonic conditions when the downstream /
upstream pressure ratio falls to the **critical pressure ratio**:

$$
r_c = \left( \frac{2}{\gamma + 1} \right)^{\gamma / (\gamma - 1)}
$$

where $\gamma = C_p / C_v$. With $r = p_2 / p_1$:

- $r \le r_c$ → **CRITICAL** (choked / sonic, mass flow set by upstream only).
- $r > r_c$ → **SUBCRITICAL** (downstream pressure propagates upstream — the
  choke has *collapsed*).
- $|r - r_c| / r_c$ within the configured margin (default 5%) → **TRANSITION**
  / **NEAR_COLLAPSE**.
- $r \ge 1$ → **REVERSE** flow.

For liquid service the analyser uses a bubble-point flash on a clone of the
inlet system to estimate the vapour pressure $p_v$, then checks:

$$
\sigma = \frac{p_2 - p_v}{p_1 - p_2}
$$

If $p_2 < p_v$ the liquid is **FLASHING**. If $\sigma$ is below the threshold
(default 1.5) the trim is flagged as **CAVITATION**.

## Quick example

```java
SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
feed.setPressure(100.0, "bara");
feed.setTemperature(25.0, "C");
feed.run();

ThrottlingValve cv = new ThrottlingValve("CV-100", feed);
cv.setOutletPressure(10.0);
cv.run();

ChokeCollapseResult result = cv.analyseChokeCollapse();
System.out.println(result.toJson());
```

Typical output:

```json
{
  "flowRegime": "CRITICAL",
  "collapseMode": "NONE",
  "fluidPhase": "gas",
  "inletPressureBara": 100.0,
  "outletPressureBara": 10.0,
  "pressureRatio": 0.10,
  "criticalPressureRatio": 0.54,
  "marginToCollapse": 0.44,
  "gamma": 1.31,
  "machNumber": 1.0,
  "flashing": false,
  "recommendations": []
}
```

## API surface

| Method | Purpose |
|--------|---------|
| `ThrottlingValve.analyseChokeCollapse()` | Convenience wrapper |
| `new ChokeCollapseAnalyzer(valve).analyze()` | Run full analysis |
| `findCollapsePressureRatio()` | Returns $r_c$ for the inlet gas |
| `setCriticalMarginThreshold(double)` | Width of TRANSITION band (default 5%) |
| `setCavitationThreshold(double)` | Liquid cavitation $\sigma$ limit (default 1.5) |
| `setDownstreamPressure(double, "bara")` | What-if downstream pressure |
| `ChokeCollapseAnalyzer.criticalPressureRatio(gamma)` | Pure analytical helper |

## Result fields

`ChokeCollapseResult` exposes `flowRegime` (`CRITICAL` / `SUBCRITICAL` /
`TRANSITION` / `REVERSE`), `collapseMode` (`NONE` / `NEAR_COLLAPSE` /
`COLLAPSED` / `FLASHING` / `CAVITATION`), the actual and critical pressure
ratios, $\gamma$, an estimated Mach number, the cavitation index (liquid
service only), a `flashing` flag, and a `recommendations` list with
remediation hints.

## Liquid service — flashing and cavitation

For a liquid feed the analyser runs a bubble-point flash on a clone of the
inlet system to estimate $p_v$, then classifies the regime:

```java
SystemInterface liq = new SystemSrkEos(298.15, 50.0);
liq.addComponent("n-butane", 1.0);
liq.setMixingRule("classic");

Stream feed = new Stream("feed", liq);
feed.setFlowRate(5000.0, "kg/hr");
feed.setPressure(50.0, "bara");
feed.setTemperature(25.0, "C");
feed.run();

ThrottlingValve cv = new ThrottlingValve("LCV-101", feed);
cv.setOutletPressure(1.5);    // below vapour pressure → flashing
cv.run();

ChokeCollapseResult r = cv.analyseChokeCollapse();
// r.getCollapseMode() → FLASHING
// r.isFlashing()      → true
// r.getRecommendations() lists anti-cavitation trim guidance
```

Adjust the cavitation threshold (default $\sigma = 1.5$) via
`analyzer.setCavitationThreshold(2.0)` for conservative screening.

## Python (Jupyter) usage

```python
ChokeCollapseAnalyzer = ns.JClass(
    "neqsim.process.equipment.valve.ChokeCollapseAnalyzer")

result = valve.analyseChokeCollapse()
print(result.toJson())
```

## When to use it

- During a **flow assurance** screening, to confirm a let-down station stays
  choked across the operating envelope (independent of downstream upsets).
- After an **operability** review, to flag stations whose pressure ratio
  drifts above $r_c$ during turndown and would couple to severe slugging.
- In **safety** studies, to identify chokes that may collapse during a
  blowdown or flare loading transient.
- For **valve specification**, to decide when anti-cavitation or multi-stage
  trim is required.

## Related

- [Valves](equipment/valves.md)
- [Well Choke Implementation](well_choke_implementation.md)
- [Multiphase Choke Flow](MultiphaseChokeFlow.md)
- [Valve Mechanical Design](ValveMechanicalDesign.md)
