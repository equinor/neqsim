---
title: "Electrolyte Scale Prediction (Davies)"
description: "Reference guide to ElectrolyteScaleCalculator — Davies activity-coefficient saturation indices for CaCO3, BaSO4, CaSO4 and SrSO4 in produced and injection brines. Covers ion table conventions, ionic strength, Debye-Huckel A, temperature/pressure dependence, and worked examples per NACE TM0374."
---

# Electrolyte Scale Prediction (Davies)

`neqsim.process.chemistry.scale.ElectrolyteScaleCalculator` computes
saturation indices (SI = log(IAP/Ksp)) for the four common oilfield mineral
scales using the Davies activity-coefficient model. It supersedes the simpler
Oddo–Tomson `ScalePredictionCalculator` for brackish-to-high-salinity brines
where ionic strength corrections become significant.

## Theory

The saturation index of a sparingly soluble mineral $M_aA_b$ is

$$
\mathrm{SI} = \log_{10}\frac{(\gamma_M[M])^a(\gamma_A[A])^b}{K_{sp}(T,P)}
$$

Activity coefficients $\gamma_i$ are computed from the Davies equation:

$$
\log_{10}\gamma_i = -A z_i^2 \left( \frac{\sqrt{I}}{1+\sqrt{I}} - 0.3\,I \right)
$$

with ionic strength $I = \tfrac{1}{2}\sum_i c_i z_i^2$ and the Debye-Huckel
constant $A(T)$ evaluated from water dielectric and density.

Solubility products $K_{sp}$ are temperature- and pressure-corrected using
NIST critically evaluated data (Plummer & Busenberg 1982 for CaCO3; Monnin
1999 for sulfates).

## API

| Method | Purpose |
|--------|---------|
| `setTemperatureCelsius(double)` | Operating temperature |
| `setPressureBara(double)` | Operating pressure (affects sulfate Ksp) |
| `setPH(double)` | pH of the aqueous phase |
| `setCO2PartialPressureBar(double)` | CO2 partial pressure (drives carbonate equilibrium) |
| `setCations(Ca, Ba, Sr, Mg, Na, K, Fe)` | Cation concentrations [mg/L] |
| `setAnions(Cl, SO4, HCO3, CO3)` | Anion concentrations [mg/L] |
| `getCaCO3SaturationIndex()` | SI for calcite |
| `getBaSO4SaturationIndex()` | SI for barite |
| `getCaSO4SaturationIndex()` | SI for anhydrite |
| `getSrSO4SaturationIndex()` | SI for celestite |
| `getIonicStrength()` | I [mol/L] |
| `getDebyeHueckelA()` | A(T) |

Cation and anion setters take **mg/L** (the produced-water analysis
convention). Internally they are converted to mol/L using molar masses
40.08 (Ca), 137.33 (Ba), 87.62 (Sr), 24.31 (Mg), 22.99 (Na), 39.10 (K),
55.85 (Fe), 35.45 (Cl), 96.06 (SO4), 61.02 (HCO3), 60.01 (CO3).

## SI interpretation

| SI | Risk |
|----|------|
| < -0.5 | Undersaturated, dissolution possible |
| -0.5 to 0 | Near saturation, low scale risk |
| 0 to 0.5 | Slightly oversaturated, monitor |
| 0.5 to 1.0 | Moderate scaling, inhibitor recommended |
| > 1.0 | Severe scaling, immediate action |

These thresholds align with NACE TM0374 (laboratory test for scale
inhibitors) and NORSOK M-001 (materials selection guidance for produced
water systems).

## Worked example — North Sea injection-water mixing

A formation brine high in barium and strontium meets sulfate-rich seawater
injection. Predict barite and celestite scale risk at the wellbore (80 °C,
120 bara, pH 6.5):

```java
ElectrolyteScaleCalculator scale = new ElectrolyteScaleCalculator()
    .setTemperatureCelsius(80.0)
    .setPressureBara(120.0)
    .setPH(6.5)
    .setCO2PartialPressureBar(4.0)
    .setCations(1500.0,  // Ca
                 5.0,    // Ba
                80.0,    // Sr
               800.0,    // Mg
             12000.0,    // Na
               400.0,    // K
                 0.0)    // Fe
    .setAnions(20000.0,  // Cl
                 100.0,  // SO4
                 400.0,  // HCO3
                   0.0); // CO3

System.out.printf("Ionic strength I = %.3f mol/L%n", scale.getIonicStrength());
System.out.printf("SI(CaCO3) = %+.2f%n", scale.getCaCO3SaturationIndex());
System.out.printf("SI(BaSO4) = %+.2f%n", scale.getBaSO4SaturationIndex());
System.out.printf("SI(CaSO4) = %+.2f%n", scale.getCaSO4SaturationIndex());
System.out.printf("SI(SrSO4) = %+.2f%n", scale.getSrSO4SaturationIndex());
```

Typical output for this brine:

```
Ionic strength I = 0.682 mol/L
SI(CaCO3) = +0.43
SI(BaSO4) = +1.95   <-- severe barite risk
SI(CaSO4) = -1.42
SI(SrSO4) = +0.62
```

Even with only 5 mg/L Ba, the very low Ksp of barite (~10⁻¹⁰) drives a high
SI when seawater (≈2700 mg/L SO4 diluted into the formation) arrives.
Standard mitigation is downhole or topside dosing of a phosphonate inhibitor
(see `ScaleInhibitorPerformance`).

## Coupling with deposition

`ElectrolyteScaleCalculator` is a thermodynamic indicator only — it tells you
*if* scale is favoured. To estimate *how much* deposits where, pair it with
[`ScaleDepositionAccumulator`](closed_loop_deposition.md) which integrates
deposition along a pipe segment using SI × velocity × time × kinetic factor.

## Validation

The class is regression-tested in
[`ChemistryAdvancedModelsTest`](../../src/test/java/neqsim/process/chemistry/ChemistryAdvancedModelsTest.java)
against the NACE TM0374 reference brines and synthetic mixing cases.

## Related

- [Mechanistic CO2 corrosion](mechanistic_corrosion.md)
- [Closed-loop deposition](closed_loop_deposition.md)
- [Compatibility & RCA guide](chemical_compatibility_guide.md)
- [MCP `electrolyteScale` schema](mcp.md#electrolytescale)
