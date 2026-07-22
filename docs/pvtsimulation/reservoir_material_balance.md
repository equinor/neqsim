---
title: "Reservoir Material Balance, Decline Analysis & Aquifer Influx"
description: "Inverse reservoir-surveillance tools in NeqSim: gas and oil material balance (OGIP/OOIP, drive indices), Van Everdingen-Hurst / Carter-Tracy aquifer influx with ECLIPSE AQUTAB export, and Arps + Duong decline-curve history matching for reserves and production forecasting."
keywords: "material balance, P/Z, OGIP, OOIP, Havlena-Odeh, Cole plot, drive indices, aquifer, Van Everdingen-Hurst, Carter-Tracy, AQUTAB, decline curve, Arps, Duong, EUR, reserves, reservoir surveillance"
---

# Reservoir Material Balance, Decline Analysis & Aquifer Influx

This package provides **inverse** reservoir-surveillance tools that regress
reserves and drive mechanism from a measured pressure-versus-production history.
They complement the **forward** tank model
(`neqsim.process.equipment.reservoir.SimpleReservoir`) and the standard PVT
laboratory simulations.

**Location:** `neqsim.pvtsimulation.reservoirproperties.materialbalance` and
`neqsim.pvtsimulation.util.DeclineCurveAnalysis`.

## Table of Contents

- [Overview](#overview)
- [Gas material balance](#gas-material-balance)
- [Oil material balance](#oil-material-balance)
- [Aquifer influx (Van Everdingen-Hurst / Carter-Tracy)](#aquifer-influx)
- [Decline-curve history matching (Arps + Duong)](#decline-curve-history-matching)
- [Gray correlation for gas-condensate wells](#gray-correlation)
- [Units](#units)

---

## Overview

| Class | Purpose | Package |
|-------|---------|---------|
| `GasMaterialBalance` | Gas P/Z straight line (OGIP), Cole plot aquifer diagnostic, Havlena-Odeh gas balance | `pvtsimulation.reservoirproperties.materialbalance` |
| `OilMaterialBalance` | Havlena-Odeh oil balance (OOIP, gas-cap ratio `m`, water drive, Pirson drive indices) | `pvtsimulation.reservoirproperties.materialbalance` |
| `VanEverdingenHurstAquifer` | Radial aquifer influence functions, Carter-Tracy cumulative water influx, ECLIPSE `AQUTAB` export | `pvtsimulation.reservoirproperties.materialbalance` |
| `DeclineCurveAnalysis` | Arps + Duong (2011) decline: forward rate/EUR and least-squares history matching | `pvtsimulation.util` |

A typical surveillance workflow is:

1. Diagnose the drive mechanism (Cole plot / drive indices).
2. If water drive is present, build the cumulative influx `We` with Carter-Tracy.
3. Regress OGIP/OOIP with the matching material-balance method.
4. Cross-check the reserves against a decline-curve EUR.

---

## Gas material balance

For a volumetric dry-gas reservoir with no water influx the material balance
reduces to a straight line (Craft & Hawkins; Havlena & Odeh, 1963):

$$
\frac{p}{Z} = \frac{p_i}{Z_i}\left(1 - \frac{G_p}{G}\right)
$$

A linear regression of $p/Z$ against $G_p$ gives the intercept $p_i/Z_i$ and the
x-intercept $G$ (the OGIP) where $p/Z = 0$.

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GasMaterialBalance;

double[] pressure = {350.0, 315.0, 280.0, 245.0, 210.0}; // bara
double[] z        = {0.95, 0.94, 0.93, 0.93, 0.92};
double[] gp       = {0.0, 10.0, 20.0, 30.0, 40.0};        // any consistent surface volume

GasMaterialBalance.Result r = GasMaterialBalance.fitVolumetric(pressure, z, gp);
double ogip = r.getOgip();          // same unit as gp
double piZi = r.getPiOverZi();      // bara
double r2   = r.getRSquared();
```

If Z-factors are not supplied, they can be computed internally with the Sutton
pseudo-critical correlation and the Hall-Yarborough equation of state:

```java
GasMaterialBalance.Result r =
    GasMaterialBalance.fitVolumetric(pressure, gp, 350.0, 0.7); // tempK, gasGravity
```

The **Cole plot** of the withdrawal-to-expansion ratio $F/E_g$ against cumulative
production diagnoses drive mechanism — a horizontal trend indicates volumetric
depletion, an upward trend indicates water influx:

```java
double[][] cole = GasMaterialBalance.colePlot(pressure, z, gp, 350.0);
double[] gpAxis  = cole[0];  // cumulative gas (from the second history point)
double[] fOverEg = cole[1];  // withdrawal / expansion
```

With a supplied cumulative water influx `We`, the Havlena-Odeh balance
$F = G\,E_g + W_e$ is regressed for the OGIP:

```java
GasMaterialBalance.Result rHO =
    GasMaterialBalance.fitHavlenaOdeh(pressure, z, gp, we, 350.0);
```

---

## Oil material balance

The general Havlena-Odeh material balance (1963, 1964) is

$$
F = N\,(E_o + m\,E_g + E_{fw}) + W_e B_w
$$

with reservoir withdrawal, oil/dissolved-gas expansion, gas-cap expansion and
connate-water/rock expansion terms

$$
F = N_p\,[B_o + (R_p - R_s)B_g] + W_p B_w
$$
$$
E_o = (B_o - B_{oi}) + (R_{si} - R_s)B_g, \qquad E_g = B_{oi}\left(\frac{B_g}{B_{gi}} - 1\right)
$$

The static helpers build these terms from black-oil PVT, then the `fit*` methods
regress the OOIP `N` and gas-cap ratio `m`:

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.OilMaterialBalance;

// F, Eo, Eg arrays built with withdrawalF(...), eo(...), eg(...), efw(...)
OilMaterialBalance.Result dep = OilMaterialBalance.fitDepletionDrive(f, eo);       // OOIP (no gas cap / aquifer)
OilMaterialBalance.Result gc  = OilMaterialBalance.fitGasCapDrive(f, eo, eg);      // OOIP + gas-cap ratio m
OilMaterialBalance.Result wd  = OilMaterialBalance.fitWaterDrive(f, eo, we, bw);   // OOIP with known We

double ooip = gc.getOoip();
double m    = gc.getM();
```

The fractional **Pirson drive indices** — depletion (DDI), segregation / gas-cap
(SDI), water (WDI) and connate-water/rock expansion (EDI) — sum to unity and
quantify each mechanism's contribution:

```java
double[] di = OilMaterialBalance.driveIndices(n, m, eoTerm, egTerm, efwTerm, we, bw, fTerm);
// di = {DDI, SDI, WDI, EDI}
```

---

## Aquifer influx

`VanEverdingenHurstAquifer` implements the constant-terminal-rate dimensionless
pressure $P_D(t_D)$ of Van Everdingen and Hurst (1949) for radial aquifers,
together with the Carter-Tracy (1960) cumulative water-influx method.

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;

double u = VanEverdingenHurstAquifer.aquiferConstant(
    0.20, 1.0e-4, 30.0, 3000.0, 180.0);  // phi, ct (1/bar), h (m), re (m), angle (deg)

// tD (dimensionless time), deltaP = p_i - p (bar), reD = dimensionless outer radius
double[] we = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(
    tD, deltaP, u, Double.POSITIVE_INFINITY);  // infinite-acting aquifer

// Export an ECLIPSE AQUTAB influence table for a Carter-Tracy aquifer
String aqutab = VanEverdingenHurstAquifer.exportAqutab(tD, Double.POSITIVE_INFINITY);
```

The returned `We` array is the aquifer term consumed by
`GasMaterialBalance.fitHavlenaOdeh` and `OilMaterialBalance.fitWaterDrive`. Use
`Double.POSITIVE_INFINITY` for an infinite-acting aquifer or a finite `reD` for a
bounded (no-flow outer boundary) aquifer.

---

## Decline-curve history matching

`DeclineCurveAnalysis` is a static, unit-agnostic Arps **+ Duong** toolkit. In
addition to the forward `rate`, `cumulativeProduction`, `eur` and `forecast`
methods it least-squares **fits** decline parameters to a measured rate-time
history.

```java
import java.util.Map;
import neqsim.pvtsimulation.util.DeclineCurveAnalysis;

// t[] in days, q[] in any consistent rate unit
Map<String, Double> arps = DeclineCurveAnalysis.fitArps(t, q); // qi, di, b, rSquared
double eur = DeclineCurveAnalysis.eurFromFit(arps, 50.0);       // EUR to qLimit = 50

// Windowed fit — skip early transient / cleanup points
Map<String, Double> arpsW = DeclineCurveAnalysis.fitArps(t, q, 3, t.length - 1);
```

For fracture-dominated tight-gas and shale wells, the **Duong (2011)** model
avoids the reserves over-estimate of hyperbolic Arps:

```java
Map<String, Double> duong = DeclineCurveAnalysis.fitDuong(t, q); // q1, a, m, rSquared
double q  = DeclineCurveAnalysis.rateDuong(1000.0, 1.5, 1.2, 100.0);
double gp = DeclineCurveAnalysis.cumulativeDuong(1000.0, 1.5, 1.2, 100.0);
```

Fit both models, compare `rSquared`, and prefer Duong when the well is in
transient linear (fracture-dominated) flow.

---

## Gray correlation

For **gas / gas-condensate vertical wells**, the Gray (1974) correlation
(`neqsim.process.equipment.pipeline.PipeGray`) predicts in-situ liquid holdup and
a condensate-film effective roughness. It is the vertical-well companion to the
material-balance reserves work above. See
[Flow Assurance](flowassurance/) and the process pipeline documentation for
details.

```java
PipeGray well = new PipeGray("Gray well", inletStream);
well.setDiameter(0.0889);   // 3.5 inch tubing
well.setLength(3000.0);
well.setElevation(3000.0);  // vertical
well.setNumberOfIncrements(10);
well.run();
double dP = well.getTotalPressureDrop(); // bar
```

---

## Units

- **Material balance:** pressures in bara, temperatures in Kelvin; formation
  volume factors in reservoir volume per surface volume at NeqSim standard
  conditions (1.01325 bara, 288.71 K); cumulative volumes in any consistent
  surface unit, with the returned in-place volume in that same unit.
- **Aquifer functions:** SI throughout (permeability m², time s, viscosity Pa·s,
  compressibility 1/Pa, length m) except `aquiferConstant` and the influx
  `deltaP`, which use 1/bar and bar so the aquifer constant has units of
  reservoir m³ per bar.
- **`DeclineCurveAnalysis`:** unit-agnostic for rate; times are in days.

## Related Documentation

- [PVT Simulation Package](README.md)
- [Relative Permeability](relative_permeability.md)
- [Gas Pseudopressure & Pseudocritical Properties](gas_pseudopressure_pseudocritical.md)
- [Field Development](../fielddevelopment/index.md)
