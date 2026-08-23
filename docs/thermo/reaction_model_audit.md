---
title: "Reaction Model Audit"
description: "Compare active chemical reactions and parameter provenance across electrolyte EOS and GE models without changing the calculation path."
---

# Reaction Model Audit

NeqSim can use chemical-reaction equilibrium together with electrolyte EOS models such as Electrolyte-CPA and electrolyte GE models such as Pitzer. Electrolyte EOS models select the `STANDARD` mole-fraction reaction-data source, `SystemPitzer` selects the dedicated `PITZER` molality-standard-state source, and `SystemKentEisenberg` selects the dedicated `KENT_EISENBERG` apparent-constant source.

A shared reaction name or stoichiometry is not evidence that its equilibrium constants, activity convention, or validity range transfer between thermodynamic models. Capture the actual initialized state with `ChemicalReactionModelAudit` before changing model-specific reactions.

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

`SystemPitzer` evaluates solute activities from molality, defined as moles of solute per kilogram of water solvent, while solvent activity retains its mole-fraction convention. This matches the concentration basis of the Pitzer ion-interaction equations. The `PITZER` source supplies molality-standard-state correlations for CO2/HCO3-, HCO3-/CO3--, and water dissociation; Electrolyte-CPA retains the legacy `STANDARD` source and its mole-fraction reaction convention. Pitzer's thermodynamic framework is documented in DOI `10.1021/j100621a026` and the binary-electrolyte formulation in DOI `10.1021/j100638a009`.

### Carbonate parameter evidence and validity

The three Pitzer correlations reproduce the analytical expressions distributed in the public-domain USGS PHREEQC 3 `phreeqc.dat` database. The CO2 and carbonate expressions trace to Plummer and Busenberg (1982), DOI `10.1016/0016-7037(82)90056-4`; PHREEQC 3 is documented by Parkhurst and Appelo (2013), DOI `10.3133/tm6A43`. The authoritative software page marks the source and usage as public domain: <https://www.usgs.gov/software/phreeqc-version-3>.

NeqSim fits its existing four-coefficient natural-log form, `ln(K) = K1 + K2/T + K3 ln(T) + K4 T`, to the PHREEQC base-10 analytical expressions. Training temperatures are 0, 10, ..., 90 degC. Held-out validation temperatures are 5, 15, ..., 85 degC. Maximum held-out absolute errors are 0.000471 log10 units for CO2 dissociation, 0.000157 for bicarbonate dissociation, and 0.000192 for water dissociation; RMS errors are 0.000311, 0.000104, and 0.000127 log10 units, respectively. The declared validity range is 0-90 degC at the infinite-dilution thermodynamic standard state; Pitzer activity coefficients provide the finite-ionic-strength correction.

No Pitzer binary interaction parameter was fitted or changed. Non-carbonate rows are copied from `REACTIONDATA.csv` to preserve compatibility but remain unvalidated for the Pitzer molality convention; their provenance must be established before model-specific parameter changes.

## Scientific use

Use this diagnostic to freeze model-specific validation questions before changing reaction data:

1. Compare EOS and GE systems with the same feed species and reaction-init lifecycle.
2. Record differences in active reactions and stored parameter provenance.
3. Check the activity/standard-state convention used by each thermodynamic model.
4. Validate equilibrium constants and speciation against independent public data over a declared temperature, pressure, ionic-strength, and composition range.
5. Split reaction tables or activate/deactivate model-specific reactions only when the scientific evidence requires it.

Relevant foundations include Plummer and Busenberg (1982), DOI `10.1016/0016-7037(82)90056-4`, for carbonate equilibria and the Pitzer electrolyte framework for activity-coefficient treatment. NeqSim does not infer model-specific parameter validity merely because two sources retain the same reaction name.
