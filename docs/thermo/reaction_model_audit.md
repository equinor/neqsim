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

The audit reports the selected typed data source, deterministic active-reaction list, stoichiometry, stored literature/data reference, reference temperature, and equilibrium-constant coefficients. The comparison separates reactions present in only one model from common reactions whose stored parameters differ.

The API is deliberately read-only. It requires `chemicalReactionInit()` to have been called and never initializes reactions implicitly, runs a flash, or changes model state. It therefore adds no work to ordinary neutral PR/SRK/CPA calculations and no work to electrolyte calculations unless the audit is explicitly requested.

## Scientific use

Use this diagnostic to freeze model-specific validation questions before changing reaction data:

1. Compare EOS and GE systems with the same feed species and reaction-init lifecycle.
2. Record differences in active reactions and stored parameter provenance.
3. Check the activity/standard-state convention used by each thermodynamic model.
4. Validate equilibrium constants and speciation against independent public data over a declared temperature, pressure, ionic-strength, and composition range.
5. Split reaction tables or activate/deactivate model-specific reactions only when the scientific evidence requires it.

Relevant foundations include Plummer and Busenberg (1982), DOI `10.1016/0016-7037(82)90056-4`, for carbonate equilibria represented in the existing standard reaction data, and the Pitzer electrolyte framework for activity-coefficient treatment. NeqSim does not infer model-specific parameter validity merely because two models currently select the same source.
