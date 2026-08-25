---
title: "Solid Handling Equipment"
description: "Reference for BioFeedstockPreparation and the NeqSim solids-separation equipment used for dewatering, filtration, centrifugation, pressing, drying, and crystallization."
---

NeqSim represents solid-handling steps through one dedicated feed-preparation unit and several
solids-separation or thermal-processing units. Select the model according to the material contract:
`BioFeedstockPreparation` uses a characterized biological feedstock and mass ledger, while the
separator and heat-exchanger units operate on NeqSim thermodynamic streams.

## Equipment selection

| Equipment | Source package | Role |
| --- | --- | --- |
| `BioFeedstockPreparation` | `solidhandling` | Dewatering, size-reduction/handling energy, densification, dry-solids loss, and mass closure |
| `SolidsSeparator` | `separator` | Base solids/liquid separation model |
| `SolidsCentrifuge` | `separator` | Centrifugal solids separation |
| `RotaryVacuumFilter` | `separator` | Vacuum filtration |
| `PressureFilter` | `separator` | Pressure filtration |
| `ScrewPress` | `separator` | Mechanical dewatering |
| `Crystallizer` | `separator` | Solid formation and phase separation |
| `Dryer` | `heatexchanger` | Thermal moisture removal |
| `MultiEffectEvaporator` | `heatexchanger` | Staged solvent or water evaporation |

## BioFeedstockPreparation contract

`BioFeedstockPreparation` accepts a `BioFeedstock` characterization and an as-received rate in
kg/h. Optional specifications set target total-solids mass fraction, prepared bulk density,
handling and densification energy intensities, water-removal energy, and dry-solids loss.

After execution, inspect prepared feed rate, water removed, dry solids lost, inlet and prepared
bulk volumes, electrical power, and `getMassClosureFraction()`. The default energy intensities are
screening assumptions rather than vendor guarantees; replace them with project evidence before
design use.

## Model boundary

The dedicated feed-preparation model does not create a `StreamInterface` outlet. It produces a
prepared `BioFeedstock` characterization and report-ready mass/energy results. Connect it to a
larger study through explicit feedstock data and energy demand. For thermodynamic solid/liquid
streams, use the separator, crystallizer, dryer, or evaporator models instead.

## Related documentation

- [Bio-processing unit operations](../bioprocessing) — reactors, separators, extraction, drying, and biorefinery examples
- [Separators](separators) — phase and solids separation equipment
- [Heat exchangers](heat_exchangers) — dryers and evaporators
- [Complete equipment catalog](equipment_catalog) — every concrete equipment implementation
