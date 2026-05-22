---
title: "Dispersion and Consequence Analysis"
description: "Quantitative consequence analysis with NeqSim — Gaussian and heavy-gas dispersion, jet/pool fire radiation, vapour-cloud explosion, BLEVE, probit fatality models, and individual-risk roll-up via ConsequenceAnalysisEngine per API 521, API 752, NORSOK Z-013 and CCPS QRA Guidelines."
---

# Dispersion and Consequence Analysis

The `neqsim.process.safety` package provides the building blocks for a
quantitative risk assessment (QRA): dispersion footprints, fire/explosion
radiation and overpressure contours, probit-based fatality probabilities, and
an aggregate IRPA (individual risk per annum) calculation.

## Dispersion

`GaussianPlume` implements the standard Pasquill–Gifford / Briggs model.
Stability classes A (very unstable) through F (stable) are encoded as the
`Stability` enum; terrain is `RURAL` or `URBAN`.

```java
GaussianPlume plume = new GaussianPlume(
    10.0,                 // mass release rate [kg/s]
    0.0,                  // source height [m] — use 0 for ground-level release
    4.0,                  // wind speed [m/s]
    GaussianPlume.Stability.D,
    GaussianPlume.Terrain.RURAL);
double cAt100m = plume.centerlineGroundConcentration(100.0); // kg/m³
double rLfl    = plume.distanceToConcentration(0.025);       // m to 2.5 % CH4
```

For dense releases (CO₂, propane, chlorine), use `HeavyGasDispersion`
(Britter–McQuaid screening).

## Fire and explosion

| Model | Class | Citation |
|-------|-------|----------|
| Jet fire | `JetFireModel` | API 521 §6.4.4, Chamberlain solid-flame |
| Pool fire | `PoolFireModel` | API 521 §6.4.3 |
| VCE | `VCEModel` | TNO multi-energy / Baker-Strehlow |
| BLEVE | `BLEVECalculator` | TNO Yellow Book Ch. 6 |

```java
JetFireModel jet = new JetFireModel(30.0, 50.0e6, 0.25);  // ṁ, HoC, η
double q50 = jet.radiationFluxAt(50.0);          // W/m² at 50 m
double r12 = jet.distanceForFlux(12500.0);       // m to 12.5 kW/m²
```

## Probit fatality

`ProbitModel` exposes ready-made factories for thermal radiation, overpressure,
and toxic gases. The form is

$$
Y = a + b \ln\!\left(C^{n}\,t\right), \qquad
P_{fatality} = \tfrac{1}{2}\!\left[1 + \mathrm{erf}\!\left(\tfrac{Y-5}{\sqrt{2}}\right)\right]
$$

```java
ProbitModel pr = ProbitModel.h2sFatality();           // -31.42, 3.008, 1.43
double pFat   = pr.fatalityProbability(60.0, 500e-6);  // 60 s @ 500 ppm
```

## QRA roll-up

`ConsequenceAnalysisEngine` aggregates branches (each with branch probability,
hazard model, probit, and exposure time) into an IRPA at a receptor distance.

```java
ConsequenceAnalysisEngine eng = new ConsequenceAnalysisEngine(1.0e-4); // base freq
eng.addJetFire(0.30, jet, ProbitModel.thermalFatality(), 60.0);
eng.addToxicCloud(0.10, plume, "h2s", ProbitModel.h2sFatality(), 600.0);
double irpa = eng.individualFatalityRiskPerYear(50.0);
String json = eng.exportSourceTerm();   // for PHAST / FLACS / KFX handoff
```

## Acceptance criteria

| Criterion | Limit | Source |
|-----------|-------|--------|
| Process equipment radiation | 12.5 kW/m² | API 521 |
| Personnel egress radiation | 4.7 kW/m² | API 521 |
| Occupied building overpressure | 0.21 bar | API 752 |
| Broadly acceptable IRPA | < 1·10⁻⁶ /yr | UK HSE R2P2 |
| Tolerable IRPA (worker) | < 1·10⁻³ /yr | UK HSE R2P2 |

## See also

- [HAZOP Worksheet](HAZOP.md)
- [FMEA Worksheet](FMEA.md)
- [Event and Fault Trees](event_fault_trees.md)
- [Depressurization per API 521](depressurization_per_API_521.md)
