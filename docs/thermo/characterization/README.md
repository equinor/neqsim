---
title: "Characterization Package"
description: "Petroleum TBP, plus-fraction, assay, lumping, asphaltene, and pseudo-component characterization in NeqSim."
---

# Characterization Package

The `neqsim.thermo.characterization` package converts petroleum assay, TBP, and plus-fraction information into pseudo-components suitable for NeqSim equations of state and process calculations.

## Main workflows

| Workflow | Primary API | Use |
| --- | --- | --- |
| Pre-binned TBP fractions | `SystemInterface.addTBPfraction(...)` | Add a petroleum cut from moles, molar mass, and specific gravity |
| Plus fraction | `SystemInterface.addPlusFraction(...)` + `Characterise` | Represent and split a C7+/C20+ heavy end |
| Refinery assay | `OilAssayCharacterisation` | Convert mass- or volume-basis refinery cuts/TBP boundaries to pseudo-components |
| TBP property model selection | `Characterise.setTBPModel(...)` | Select Pedersen, Lee-Kesler, Riazi-Daubert, Twu, Cavett, Standing, and related models |
| Lumping | `Characterise.configureLumping()` | Reduce a detailed heavy-end slate while preserving configured grouping rules |
| Common-slate characterization | `PseudoComponentCombiner` | Align multiple characterized fluids to a shared pseudo-component definition |
| Wax/asphaltene characterization | wax/asphaltene classes | Specialized heavy-phase workflows |

## Critical unit contract

`addTBPfraction` and `addPlusFraction` use **kg/mol** for molar mass. Petroleum examples therefore use values such as `0.096 kg/mol` for a roughly C7 cut, not `96.0`.

The density argument is the petroleum specific gravity/relative-density numeric value. Current system APIs also accept density values above 1.5 as kg/m3 and normalize them internally, but unit-explicit refinery-assay helpers are preferred when importing assay tables.

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(373.15, 100.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.08);
fluid.addComponent("n-butane", 0.05);

// name, moles, molar mass [kg/mol], specific gravity [-]
fluid.addTBPfraction("C7", 0.04, 0.096, 0.727);
fluid.addPlusFraction("C20+", 0.03, 0.400, 0.90);
```

## Refinery assay characterization

For crude/petroleum assays, use `OilAssayCharacterisation` rather than manually converting every volume cut to moles. The assay API provides:

- one explicit composition basis per assay: mass or liquid volume;
- volume-to-mass conversion using cut density;
- kg/mol and g/mol explicit molar-mass helpers;
- specific-gravity, kg/m3, and API-gravity density inputs;
- exact API-gravity/SG60/60 round-tripping and explicit bulk density at 60 degF;
- forward and inverse UOP/Watson characterization between representative boiling point and specific gravity;
- mass-basis mapping of known assay light ends to authoritative NeqSim standard components;
- number-average molar mass from mass-basis PIANO family/carbon-number data;
- per-cut total-sulfur and total-nitrogen inputs with mass-basis whole-assay reconstruction;
- pre-binned cumulative TBP cut-boundary ingestion;
- preserved finite and one-sided lower/upper boiling boundaries;
- closure, duplicate-name, monotonicity, and repeated-application guards.

See [Refinery Assay and TBP Cut Characterization](refinery_assay) for the complete contract and the refinery campaign gap matrix. Independent public-data bookkeeping evidence is tracked in [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation), whole-assay density/API evidence in [DOE/OEDI COA bulk density qualification](refinery_oedi_coa_bulk_density_validation), per-cut characterization evidence in [DOE Big Hill Watson-factor qualification](refinery_big_hill_watson_validation), terminal representative-temperature evidence in [DOE Big Hill terminal-Watson qualification](refinery_big_hill_watson_terminal_validation), composition-resolved light-end evidence in [DOE Big Hill light-end qualification](refinery_big_hill_light_ends_validation), PIANO aggregate molar-mass evidence in [DOE Big Hill C5-175 degF qualification](refinery_big_hill_piano_molar_mass_validation), assay-quality evidence in [DOE Big Hill sulfur qualification](refinery_big_hill_sulfur_validation) and [DOE Big Hill nitrogen qualification](refinery_big_hill_nitrogen_validation), terminal-boundary evidence in [DOE Big Hill terminal-cut qualification](refinery_big_hill_terminal_boundary_validation), and the process-integration gate in [DOE Big Hill atmospheric fractionation qualification](refinery_big_hill_atmospheric_fractionation).

The qualified inputs are assembled into one reusable reference composition by
[DOE Big Hill Sweet complete modeled assay slate](refinery_big_hill_complete_slate).

A second public operating case is retained by
[Al-Diwiniya atmospheric operating reference](refinery_al_diwiniya_atmospheric_reference).
Its 2-60 liquid-vol% partial TBP curve, plant configuration, product rates, and draw
temperatures remain explicitly separate from the complete DOE modeled slate. The
source fixed its HYSYS product rates, so those values are operating specifications
rather than independent yield-validation evidence.

[Sarir atmospheric validation reference](refinery_sarir_atmospheric_reference)
adds a CC BY 4.0, complete-range TBP case with 34-tray operating data, numeric
product ASTM D86 endpoints, and plant product rates that remain independent
validation targets.

## TBP fraction models

TBP models estimate the properties needed to represent petroleum pseudo-components in an EOS. Available implementations include Pedersen SRK/PR variants, Lee-Kesler, Riazi-Daubert, Twu, Cavett, and Standing.

```java
SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
fluid.getCharacterization().setTBPModel("PedersenSRK");
fluid.addTBPfraction("C7", 0.05, 0.096, 0.727);
fluid.addTBPfraction("C10", 0.04, 0.134, 0.782);
fluid.addTBPfraction("C12+", 0.15, 0.250, 0.85);
```

For equations, model selection boundaries, and references, see:

- [TBP Fraction Models](../../wiki/tbp_fraction_models)
- [Fluid Characterization Mathematical Foundations](../../pvtsimulation/fluid_characterization_mathematics)
- [PVT Fluid Characterization](../pvt_fluid_characterization)

## Lumping configuration

After plus-fraction splitting, lumping can reduce the number of pseudo-components for faster process calculations.

```java
// Preserve lighter TBP fractions and lump the heavier range.
fluid.getCharacterization().configureLumping()
    .model("PVTlumpingModel")
    .plusFractionGroups(5)
    .build();

// Target a total number of pseudo-components.
fluid.getCharacterization().configureLumping()
    .model("standard")
    .totalPseudoComponents(6)
    .build();

// Match user-defined grouping boundaries.
fluid.getCharacterization().configureLumping()
    .customBoundaries(6, 7, 10, 15, 20)
    .build();

// Keep the detailed SCN representation.
fluid.getCharacterization().configureLumping()
    .noLumping()
    .build();
```

| Model | Behaviour | Typical use |
| --- | --- | --- |
| `PVTlumpingModel` | Preserves configured lighter TBP fractions and lumps the plus fraction | PVT/process workflows that retain light-cut detail |
| `standard` | Lumps the characterized heavy range to a requested total | Smaller simulation slates |
| custom boundaries | Uses explicit carbon-number/group boundaries | Matching an external/reference characterization |
| no lumping | Retains the generated detailed representation | Detailed characterization studies |

## Common pseudo-component slates

When several reservoir or process fluids need to be mixed consistently, use `PseudoComponentCombiner` rather than assuming independently generated pseudo-components have identical meaning.

See [Fluid Characterization Combining](fluid_characterization_combining) for common-slate and reference-slate workflows.

## Asphaltene and wax workflows

NeqSim also contains specialized heavy-phase characterization used by wax and asphaltene calculations. These are separate from the refinery-assay bookkeeping API because they introduce additional phase-model assumptions and validation requirements.

See:

- [Wax Characterization](wax_characterization)
- [Asphaltene Modeling](../../pvtsimulation/flowassurance/asphaltene_modeling)

## Validation guidance

Characterization validation should distinguish three layers:

1. **Bookkeeping:** units, cut yields, composition/mass closure, component identity, splitting/lumping conservation.
2. **Property correlations:** boiling point, molecular weight, density, critical properties, acentric factor, and applicability ranges.
3. **Process behaviour:** flash, phase envelope, distillation/fractionation, and product-yield agreement for representative fluids.

A bookkeeping regression does not by itself validate a petroleum-property correlation, and a property match does not by itself establish refinery column performance. The refinery campaign in issue #3305 uses these layers as separate quality gates.

## Related documentation

- [Refinery Assay and TBP Cut Characterization](refinery_assay)
- [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation)
- [DOE Big Hill Watson-factor qualification](refinery_big_hill_watson_validation)
- [DOE Big Hill terminal-Watson qualification](refinery_big_hill_watson_terminal_validation)
- [DOE Big Hill composition-resolved light-end qualification](refinery_big_hill_light_ends_validation)
- [DOE Big Hill C5-175 degF PIANO molar-mass qualification](refinery_big_hill_piano_molar_mass_validation)
- [DOE Big Hill Sweet complete modeled assay slate](refinery_big_hill_complete_slate)
- [DOE Big Hill assay sulfur qualification](refinery_big_hill_sulfur_validation)
- [DOE Big Hill assay nitrogen qualification](refinery_big_hill_nitrogen_validation)
- [DOE Big Hill terminal-cut boundary qualification](refinery_big_hill_terminal_boundary_validation)
- [DOE Big Hill atmospheric fractionation qualification](refinery_big_hill_atmospheric_fractionation)
- [Al-Diwiniya atmospheric operating reference](refinery_al_diwiniya_atmospheric_reference)
- [Sarir atmospheric validation reference](refinery_sarir_atmospheric_reference)
- [DOE/OEDI COA bulk density and API qualification](refinery_oedi_coa_bulk_density_validation)
- [Fluid Characterization Guide](../../wiki/fluid_characterization)
- [TBP Fraction Models](../../wiki/tbp_fraction_models)
- [PVT Fluid Characterization](../pvt_fluid_characterization)
- [Fluid Characterization Mathematical Foundations](../../pvtsimulation/fluid_characterization_mathematics)
- [Fluid Characterization Combining](fluid_characterization_combining)
- [Fluid Creation Guide](../fluid_creation_guide)
