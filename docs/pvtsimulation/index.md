---
title: "PVT Simulation Documentation"
description: "Documentation for PVT simulation, fluid characterization, and flow assurance capabilities in NeqSim."
keywords: "PVT, CME, CVD, differential liberation, separator test, swelling test, saturation pressure, bubble point, dew point, GOR, oil characterization, plus fraction, TBP"
---

{% include_relative README.md %}

- [Reservoir-fluid method qualification](pedersen_method_qualification.md)
- [PVT and fluid-characterization study report](pvt_fluid_study_report.md)
- [Reservoir material balance, compositional history replay and original/tuned PVT handoff](reservoir_material_balance.md)
- [Finite-aquifer replay and discrete Bayesian uncertainty](reservoir_aquifer_uncertainty.md)
- [Native CCE/CVD/DLE handoff, checked upper-dew reference, gas-only CVD continuation and bounded PVT conversion](depletion_pvt_generation.md)
- [Phase-selective reservoir production and separator outputs](reservoir_phase_production.md)
- [Laboratory gas-density consistency before EOS handoff](laboratory_gas_density_consistency.md)

Closed-mass wet CCE handoff and its synthetic gas/aqueous and three-phase
qualification are described in the [depletion PVT guide](depletion_pvt_generation.md).

Native [wet differential liberation](depletion_pvt_generation.md#native-wet-differential-liberation)
removes gas while retaining oil/aqueous inventories, with independent synthetic references.
