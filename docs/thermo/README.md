---
title: "Thermodynamic Documentation Set"
description: "This folder collects topic-specific documentation for using NeqSim's thermodynamic, PVT, and physical property capabilities. Each page is intended to be self-contained while pointing to related guides..."
---

# Thermodynamic Documentation Set

This folder collects topic-specific documentation for using NeqSim's thermodynamic, PVT, and physical property capabilities. Each page is intended to be self-contained while pointing to related guides so you can jump directly to the workflows you need.

---

## Package Structure

```
thermo/
├── system/       # Fluid system implementations (58 EoS classes)
├── phase/        # Phase types and calculations (62 classes)
├── component/    # Component properties (65 classes)
├── mixingrule/   # Mixing rules for EoS
└── characterization/  # Plus fraction characterization
```

---

## Subpackage Documentation

| Subpackage | Description | Documentation |
|------------|-------------|---------------|
| system | Equations of state implementations | [system/README.md](system/README.md) |
| phase | Phase modeling (gas, liquid, solid, asphaltene) | [phase/README.md](phase/README.md) |
| component | Component property calculations | [component/README.md](component/README.md) |
| mixingrule | Binary interaction parameters | [mixingrule/README.md](mixingrule/README.md) |
| characterization | Plus fraction and asphaltene characterization | [characterization/README.md](characterization/README.md) |

---

## Guide Contents

### Core Guides

- [Thermodynamic Models Guide](thermodynamic_models.md): **Comprehensive overview** of all thermodynamic models in NeqSim, including equations of state, CPA, reference equations (GERG-2008, EOS-CG), activity coefficient models, electrolyte models, and the auto-select feature. Covers theory, usage, and model selection guidelines.
- [Søreide-Whitson Model](SoreideWhitsonModel.md): **Gas solubility in brine** - Modified Peng-Robinson EoS with salinity-dependent alpha function for water. Essential for produced water emission calculations and used in **NeqSimLive**. Includes mathematical formulation, salt type coefficients, validation data, and literature references.
- [Fluid Creation Guide](fluid_creation_guide.md): **Comprehensive guide** to creating fluids in NeqSim, including all available equations of state, mixing rules, and model selection guidelines.
- [Mixing Rules Guide](mixing_rules_guide.md): **Detailed documentation** on mixing rules, including mathematical formulations, binary interaction parameters, and usage examples for different applications.
- [Flash Calculations Guide](flash_calculations_guide.md): **Comprehensive documentation** of flash calculations available via ThermodynamicOperations, including TP, PH, PS, VU flashes, saturation calculations, and hydrate equilibria.
- [Hydrate Models Guide](hydrate_models.md): **Comprehensive documentation** of gas hydrate thermodynamic models, including van der Waals-Platteeuw theory, Structure I/II hydrates, CPA and PVTsim implementations, and inhibitor modeling.
- [Electrolyte CPA Model](ElectrolyteCPAModel.md): **Detailed documentation** of the electrolyte CPA model, including Fürst electrostatic contributions, validation data, and usage examples.

### Database Documentation

- [Component Database Guide](component_database_guide.md): **Detailed documentation** of the COMP pure component parameters database, including parameter descriptions, units, and links to thermodynamic models.
- [INTER Table Guide](inter_table_guide.md): **Detailed documentation** of the INTER binary interaction parameters database, including column reference for all EoS, CPA, Huron-Vidal, Wong-Sandler, and NRTL parameters.

### Reference Documentation

- [Mathematical Models](mathematical_models.md): Equations of state, activity-coefficient formulations, and transport correlations available in NeqSim.
- [GERG-2008 and EOS-CG](gerg2008_eoscg.md): Detailed guide to the reference equations of state for natural gas and CCS applications.

### Application Guides

- [Thermodynamic Workflows](thermodynamic_workflows.md): How to set up systems, select models, and perform common equilibrium calculations.
- [PVT and Fluid Characterization](pvt_fluid_characterization.md): Building realistic fluid descriptions, including heavy-end handling and lab-data reconciliation.
- [Thermodynamic Operations](thermodynamic_operations.md): Flash calculations, phase envelopes, and other process-centric operations.
- [Physical Properties](physical_properties.md): Density, viscosity, surface tension, and transport-property calculations.

---

Each document favors short, reproducible code snippets using the Java API so the same ideas transfer to other supported languages (Python/Matlab) with minor syntax changes.
