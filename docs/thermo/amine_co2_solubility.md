---
title: "Amine CO2 Solubility (Kent-Eisenberg)"
description: "Screening-level CO2 vapor-liquid equilibrium for aqueous alkanolamine solvents (MEA, DEA, MDEA, activated MDEA) using the Kent-Eisenberg apparent-equilibrium-constant model. Covers the validated screening API, validation status, equations, and usage examples."
keywords: "amine, CO2 solubility, Kent-Eisenberg, MEA, DEA, MDEA, aMDEA, acid gas, absorption, loading, partial pressure, gas sweetening, carbon capture"
---

# Amine CO2 Solubility — Kent-Eisenberg Model

NeqSim provides a fast, screening-level model for the solubility of carbon dioxide
in aqueous alkanolamine solvents. It implements the classic
**Kent-Eisenberg** approach: acid-gas speciation is governed by a small set of
*apparent* (lumped) equilibrium constants, and the physically dissolved free CO2
is related to its partial pressure through Henry's law.

The model is intended for **process screening** — solvent selection, loading and
circulation-rate estimates, and qualitative comparison of primary, secondary and
tertiary amines. It is **not** a custody-grade VLE package.

---

## Supported amines

| Amine | Type | Class enum | Molar mass (g/mol) | Typical loading window (mol CO2 / mol amine) |
|-------|------|------------|--------------------|----------------------------------------------|
| MEA   | Primary   | `MEA`   | 61.08  | 0.2 – 0.5 |
| DEA   | Secondary | `DEA`   | 105.14 | 0.2 – 0.5 |
| MDEA  | Tertiary  | `MDEA`  | 119.16 | 0.1 – 1.0 |
| aMDEA | Activated MDEA (MDEA + piperazine) | `AMDEA` | 119.16 | 0.1 – 1.0 |

Primary and secondary amines (MEA, DEA) form a stable carbamate, bind CO2
strongly at low loading, and saturate near a stoichiometric limit of about
0.5 mol/mol. Tertiary MDEA cannot form a carbamate; it promotes bicarbonate
formation, binds CO2 more weakly, and can be loaded toward 1.0 mol/mol.

`AmineKentEisenberg` (the core static model) supports `MEA`, `DEA` and `MDEA`.
`AmineSystem` (the convenience wrapper) additionally accepts `AMDEA`, which is
mapped to the MDEA correlation for the screening partial-pressure path.

---

## Theory

CO2 absorbed into an aqueous amine is partitioned between a small amount of
*physically dissolved* (free) molecular CO2 and a much larger pool of chemically
bound species (bicarbonate, carbonate, carbamate). Only the free CO2 sets the
vapor-phase partial pressure through Henry's law:

$$
p_{CO_2} = H_{CO_2}(T)\,[CO_2]_{\text{free}}
$$

The free-CO2 concentration is found from the loading (total absorbed CO2 per mole
of amine) by solving the apparent-equilibrium speciation. For a tertiary amine
the dominant reaction is bicarbonate formation:

$$
CO_2 + R_3N + H_2O \;\rightleftharpoons\; R_3NH^+ + HCO_3^-
$$

For primary and secondary amines, carbamate formation dominates at low loading:

$$
CO_2 + 2\,R_2NH \;\rightleftharpoons\; R_2NCOO^- + R_2NH_2^+
$$

The temperature dependence of the Henry coefficient drives the absorber /
stripper duality: cold solvent holds CO2 at low partial pressure (absorption),
while hot solvent rejects it at high partial pressure (regeneration).

---

## Validated screening API

The **design-default, validated** entry point is the partial-pressure calculation.
There are two ways to call it.

### 1. Static model (`AmineKentEisenberg`)

```java
import neqsim.thermo.util.amines.AmineKentEisenberg;
import neqsim.thermo.util.amines.AmineKentEisenberg.AmineType;

// Solvent molarity from mass fraction and amine molar mass
double molarity = AmineKentEisenberg.amineMolarity(0.50, 119.16); // 50 wt% MDEA -> ~4.36 mol/L

// CO2 partial pressure [bara] at 40 C and loading 0.4 mol CO2 / mol MDEA
double pCO2 = AmineKentEisenberg.partialPressureCO2Bara(
    AmineType.MDEA, 313.15, molarity, 0.40); // ~0.229 bara
```

`partialPressureCO2Bara(type, temperatureK, amineMolarity, loading)` returns the
equilibrium CO2 partial pressure in **bara**. It returns `0.0` at zero loading and
throws `IllegalArgumentException` for non-physical inputs (negative temperature,
molarity, or loading).

### 2. Convenience wrapper (`AmineSystem`)

```java
import neqsim.thermo.util.amines.AmineSystem;
import neqsim.thermo.util.amines.AmineSystem.AmineType;

AmineSystem solvent = new AmineSystem(AmineType.MDEA, 313.15, 1.0);
solvent.setAmineConcentration(0.50); // 50 wt% MDEA
solvent.setCO2Loading(0.40);          // mol CO2 / mol amine

double pCO2 = solvent.getCO2PartialPressure(); // validated screening path, ~0.229 bara
```

`AmineSystem.getCO2PartialPressure()` is the validated screening path used in
design workflows. For `AMDEA` it uses the MDEA correlation.

### Heat of absorption (optional)

```java
import neqsim.thermo.util.amines.AmineHeatOfAbsorption;
import neqsim.thermo.util.amines.AmineHeatOfAbsorption.AmineType;

AmineHeatOfAbsorption hoa = new AmineHeatOfAbsorption(AmineType.MEA, 0.30, 0.30, 313.15);
double dH = hoa.calcHeatOfAbsorptionCO2(); // kJ/mol CO2 (negative = exothermic)
```

---

## Validation status

The screening model is calibrated and regression-tested against the engineering
loading windows of each amine class. In the validated windows it reproduces
literature isotherms to within roughly **a factor of two on CO2 partial
pressure** — adequate for solvent screening and loading/circulation estimates,
but not for final tower rating.

Selected regression anchors (verified in
`AmineCO2SolubilityTest`):

| Case | Condition | Verified result |
|------|-----------|-----------------|
| 50 wt% MDEA molarity | mass fraction 0.50, MW 119.16 | ≈ 4.36 mol/L |
| 30 wt% MEA molarity  | mass fraction 0.30, MW 61.08  | ≈ 5.03 mol/L |
| MDEA partial pressure | 40 °C, loading 0.10 | ≈ 0.0093 bara |
| MDEA partial pressure | 40 °C, loading 0.40 | ≈ 0.229 bara  |
| Carbamate vs bicarbonate | below half loading | MEA binds CO2 tighter than MDEA |
| Stripping ratio | 100 °C vs 40 °C, same loading | hot / cold pCO2 > 5× |

> **Experimental rigorous path.** `AmineSystem.getCO2PartialPressureRigorous()`
> runs a full electrolyte-CPA equilibrium (`SystemElectrolyteCPAstatoil`, mixing
> rule 10, amine physical-property model, chemical reactions enabled). It is
> **experimental and not yet calibrated** — it may return `NaN` when the
> equilibrium does not converge. Use `getCO2PartialPressure()` for design work.

---

## References

- Kent, R.L., Eisenberg, B. (1976). *Better data for amine treating.*
  Hydrocarbon Processing, 55(2), 87–90.
- Jou, F.-Y., Mather, A.E., Otto, F.D. (1982). *Solubility of H2S and CO2 in
  aqueous methyldiethanolamine solutions.* Ind. Eng. Chem. Process Des. Dev.,
  21(4), 539–544.
- Lee, J.I., Otto, F.D., Mather, A.E. (1976). *Equilibrium between carbon dioxide
  and aqueous monoethanolamine solutions.* J. Appl. Chem. Biotechnol., 26, 541–549.
- Versteeg, G.F., van Swaaij, W.P.M. (1988). *Solubility and diffusivity of acid
  gases (CO2, N2O) in aqueous alkanolamine solutions.* J. Chem. Eng. Data, 33, 29–34.

---

## Related documentation

- [H2S Distribution Guide](H2S_distribution_guide)
- [Electrolyte CPA Model](ElectrolyteCPAModel)
- [Reactive Flash](reactive_flash)
- [Mass Transfer (CO2-amine systems)](../fluidmechanics/mass_transfer)
