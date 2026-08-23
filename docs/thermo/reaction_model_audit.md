---
title: "Reaction Model Audit"
description: "Compare active chemical reactions and parameter provenance across electrolyte EOS and GE models without changing the calculation path."
---

# Reaction Model Audit

NeqSim can use chemical-reaction equilibrium together with electrolyte EOS models such as Electrolyte-CPA and electrolyte GE models such as Pitzer. These models currently select the `STANDARD` reaction-data source, while `SystemKentEisenberg` selects the dedicated `KENT_EISENBERG` apparent-constant source.

A shared reaction table is an implementation choice, not evidence that the same active reaction set, equilibrium constants, activity convention, or validity range has been independently validated for every thermodynamic model. Before splitting a table or changing model-specific reactions, capture the actual initialized state with `ChemicalReactionModelAudit`.

```java
SystemInterface cpa = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
cpa.addComponent("CO2", 0.01);
cpa.addComponent("water", 0.99);
cpa.chemicalReactionInit();

SystemInterface pitzer = new SystemPitzer(298.15, 1.01325);
pitzer.addComponent("CO2", 0.01);
pitzer.addComponent("water", 0.99);
pitzer.chemicalReactionInit();

ChemicalReactionModelAudit.AuditComparison comparison =
    ChemicalReactionModelAudit.compare(cpa, pitzer);
```

The audit reports the selected typed data source, reaction-quotient concentration basis, deterministic active-reaction list, stoichiometry, stored literature/data reference, reference temperature, and equilibrium-constant coefficients. The comparison separates concentration conventions, reactions present in only one model, and common reactions whose stored parameters differ.

The API is deliberately read-only. It requires `chemicalReactionInit()` to have been called and never initializes reactions implicitly, runs a flash, or changes model state. It therefore adds no work to ordinary neutral PR/SRK/CPA calculations and no work to electrolyte calculations unless the audit is explicitly requested.

## Pitzer reaction concentration basis

`SystemPitzer` evaluates solute activities from molality, defined as moles of solute per kilogram of water solvent, while solvent activity retains its mole-fraction convention. This matches the concentration basis of the Pitzer ion-interaction equations and the thermodynamic carbonate constants represented in the standard reaction table. Electrolyte EOS and Kent-Eisenberg systems retain their existing reaction conventions.

The concentration-basis selector changes only Pitzer chemical-reaction quotients and mineral saturation ratios. It does not split the shared reaction table or change any stored equilibrium or Pitzer interaction coefficient. Pitzer's thermodynamic framework is documented in DOI `10.1021/j100621a026` and the binary-electrolyte formulation in DOI `10.1021/j100638a009`.

## Scientific use

Use this diagnostic to freeze model-specific validation questions before changing reaction data:

1. Compare EOS and GE systems with the same feed species and reaction-init lifecycle.
2. Record differences in active reactions and stored parameter provenance.
3. Check the activity/standard-state convention used by each thermodynamic model.
4. Validate equilibrium constants and speciation against independent public data over a declared temperature, pressure, ionic-strength, and composition range.
5. Split reaction tables or activate/deactivate model-specific reactions only when the scientific evidence requires it.

Relevant foundations include Plummer and Busenberg (1982), DOI `10.1016/0016-7037(82)90056-4`, for carbonate equilibria represented in the existing standard reaction data, and the Pitzer electrolyte framework for activity-coefficient treatment. NeqSim does not infer model-specific parameter validity merely because two models currently select the same source.

## Public NaCl control for the Pitzer activity model

Before changing Pitzer reaction tables or equilibrium constants, validate the underlying molality-scale activity model independently from the reaction-equilibrium regression. `SystemPitzerTest.testNaClActivityAndOsmoticCoefficientAgainstLiterature` compares the current Pitzer phase against Hamer and Wu's evaluated NaCl data at 298.15 K and approximately atmospheric pressure:

| NaCl molality (mol/kg water) | Mean ionic activity coefficient | Osmotic coefficient |
|---:|---:|---:|
| 0.1 | 0.778 | 0.932 |
| 0.5 | 0.681 | 0.921 |
| 1.0 | 0.657 | 0.936 |
| 2.0 | 0.668 | 0.984 |
| 3.0 | 0.714 | 1.045 |

The source is Hamer and Wu (1972), DOI `10.1063/1.3253108`. Values are transcribed directly at the published three-decimal precision; there is no digitization, interpolation, unit conversion, or fitting. The selected range is 0.1-3.0 mol NaCl per kg water at 298.15 K. The test accepts one percent relative error in mean ionic activity and 0.001 absolute error in osmotic coefficient, the latter including the source's rounding precision.

The numerical facts are reproduced with attribution under NIST's public-information guidance; the test does not copy the article's prose or table layout. See the [NIST Library public-domain guidance](https://www.nist.gov/nist-research-library/library-faqs). Existing NeqSim code and repository data remain Apache-2.0.

This control is validation evidence for the present NaCl activity implementation, not a calibration dataset introduced by this change and not independent hold-out evidence for the historical Pitzer interaction parameters. It does not validate carbonate reaction constants, prove that the shared `STANDARD` reaction table is transferable between Pitzer and Electrolyte-CPA, or justify a model-specific table split. Those decisions still require species-specific equilibrium/speciation data with explicit standard-state conventions and an independent validation subset.
