---
title: "Sarir atmospheric pump-around screening"
description: "Explicit tray mapping, source-bounded cooling, and fail-closed pump-around evidence."
---

# Sarir atmospheric pump-around screening

`SarirAtmosphericPumparoundScreen` adds one or both published pump-around circuits to the qualified
Sarir assay → heating → atmospheric-fractionation workflow. It keeps a strict distinction between
published evidence and caller-selected engineering inputs.

## Source boundary

The source is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil Refinery Using Aspen
HYSYS*, Journal of Engineering Research (Libya), issue 33, pages 51–64, published 31 March 2022,
DOI [10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The
[open-access article](https://jer.ly/jer/index.php/jer/article/download/46/38/39) is licensed CC BY
4.0.

Table 4 reports two circuits:

| Source label | Raw draw tray | Raw return tray | Flow (kg/h) | Draw (degC) | Return (degC) | Derived drop (K) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Top pump around (TPA) | 3 | 1 | 29,777.64 | 143.9 | 80.99 | 62.91 |
| Bottom pump around (BPA) | 22 | 19 | 60,423.66 | 232.4 | 173.99 | 58.41 |

The temperature drops are arithmetic differences of the published temperatures. The source does
not say whether the Table 4 tray numbers are counted from the top or bottom. The screen therefore
never converts those raw labels automatically. Every bottom-up NeqSim draw and return index is an
explicit caller input.

The published circulation rates are comparison evidence. `DistillationColumn` specifies a liquid
draw fraction, not a mass-flow target, so the screen also requires the caller to provide that
fraction and does not tune it to the source rate.

## Java and JPype-accessible workflow

Build the existing qualified `SarirAtmosphericFractionationCase`, then provide an auditable mapping:

```java
SarirAtmosphericPumparoundScreen.Mapping top =
    new SarirAtmosphericPumparoundScreen.Mapping(
        "Top pump around (TPA)", 30, 32, 0.005);

SarirAtmosphericPumparoundScreen screen =
    SarirAtmosphericPumparoundScreen.configure(fractionation, 20, 1.0e-4, top);

SarirAtmosphericPumparoundScreen.Result result = screen.run(UUID.randomUUID());
SarirAtmosphericPumparoundScreen.PumparoundResult evidence =
    result.getPumparounds()[0];

double modeledFlowKgPerHour = evidence.getModeledReturnMassFlowKgPerHour();
double sourceFlowKgPerHour = evidence.getSourceMassFlowKgPerHour();
double coolingDutyW = evidence.getDutyW();
```

Indices `30` and `32` and the draw fraction `0.005` above are illustrative engineering inputs, not
values reported by the source. A real study must document its chosen mapping and fraction. The same
constructors and getters are callable through JPype.

## Acceptance contract

Configuration fails before column mutation for an unknown source label, invalid index, identical
draw and return tray, non-finite or boundary draw fraction, duplicate source row, or duplicate draw
tray. It also rejects a column that has already been solved or already owns a pump-around.

Evaluation returns only after all of these gates pass:

- the existing Sarir atmospheric product result is conservative, ordered, MESH-residual based, and
  neither failed nor fallback;
- the combined column outer tear converged;
- each liquid draw and return stream is finite and has positive flow;
- each internal circuit closes mass flow to `1e-8` relative;
- the modeled draw-to-return temperature change equals the source-derived cooling drop; and
- cooling duty is finite and negative.

The result retains raw source tray labels, explicit NeqSim indices, published and modeled flows,
published and modeled temperatures, internal closure, cooling duty, and outer-tear diagnostics.
Source/model deviations are reported for engineering comparison only. They are not plant-calibration
acceptance thresholds and do not establish that the illustrative mapping reproduces the refinery.

## Scope boundary

This screen does not infer tray numbering, solve for a draw fraction, configure steam, add side
strippers, assign tray efficiencies, alter the shared column solver, or claim plant performance.
Those capabilities require their own degrees-of-freedom analysis and independent qualification.
