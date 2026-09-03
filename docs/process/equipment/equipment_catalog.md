---
title: "Complete Process Equipment Catalog"
description: "Source-backed catalog of every concrete NeqSim ProcessEquipmentInterface implementation, grouped by Java package and linked to its maintained guide."
---

This catalog is generated from `src/main/java/neqsim/process/equipment`. It lists every public,
non-abstract class that implements `ProcessEquipmentInterface`, directly or through an equipment
base class. Helper classes, result records, strategies, enums, and interfaces are intentionally
excluded from the equipment count.

**Current source inventory:** 237 concrete equipment classes in 33 packages.

Regenerate this page after adding or removing equipment:

```text
python devtools/generate_equipment_documentation_catalog.py
```

## Equipment by source package

| Source package | Maintained guide | Concrete equipment classes |
| --- | --- | --- |
| `absorber` | [Absorbers and strippers](absorbers)<br>Gas absorption, stripping, amine, and TEG contactors | `AbsorptionColumn`, `H2SScavenger`, `RateBasedAbsorber`, `SimpleAbsorber`, `SimpleAmineAbsorber`, `SimpleAmineRegenerator`, `SimpleTEGAbsorber`, `StrippingColumn`, `WaterStripperColumn` |
| `adsorber` | [Adsorbers](adsorbers) and [adsorption beds](adsorption_bed)<br>Adsorption beds, mercury removal, and PSA equipment | `AdsorptionBed`, `MercuryRemovalBed`, `PSACascade`, `PressureSwingAdsorptionBed`, `SimpleAdsorber` |
| `battery` | [Battery storage](battery_storage)<br>Electrical energy storage and balancing | `BatteryStorage` |
| `blackoil` | [Black-oil separation](black_oil_separator)<br>Black-oil PVT separation in ProcessSystem | `BlackOilSeparator` |
| `compressor` | [Compressors](compressors)<br>Compressors, trains, drivers, maps, and anti-surge models | `Compressor`, `CompressorTrain`, `RecycleFlowCoordinator` |
| `diffpressure` | [Differential-pressure equipment](differential_pressure)<br>Orifice and differential-pressure flow equipment | `Orifice` |
| `distillation` | [Distillation](distillation)<br>Tray, packed, reactive, and shortcut columns | `Condenser`, `DistillationColumn`, `PackedColumn`, `RateBasedPackedColumn`, `ReactiveTray`, `Reboiler`, `ScrubColumn`, `ShortcutDistillationColumn`, `SimpleTray`, `VLSolidTray` |
| `ejector` | [Ejectors](ejectors)<br>Motive/suction ejector equipment | `Ejector` |
| `electrolyzer` | [Electrolyzers](electrolyzers)<br>Water and carbon-dioxide electrolysis | `CO2Electrolyzer`, `Electrolyzer` |
| `energy` | [Energy conversion equipment](energy_conversion)<br>Motors, generators, converters, utility sources, and network solvers | `CommittedEnergyGenerator`, `ElectricMotor`, `EnergyConverter`, `EnergyNetworkSolver`, `Gearbox`, `Generator`, `Inverter`, `LoadMappedEnergyConverter`, `PrimeMover`, `ThermalUtilityConsumer`, `ThermalUtilitySource`, `Transformer` |
| `expander` | [Expanders](expanders)<br>Turboexpanders and coupled expander-compressor units | `Expander`, `ExpanderOld`, `MapTurboExpanderCompressor`, `TurboExpanderCompressor` |
| `filter` | [Filters](filters)<br>Particulate, charcoal, and sulfur filters | `CharCoalFilter`, `Filter`, `SulfurFilter` |
| `flare` | [Flares](flares)<br>Flare units and stacks | `Flare`, `FlareStack` |
| `heatexchanger` | [Heat exchangers](heat_exchangers)<br>Heaters, coolers, exchangers, evaporators, and dryers | `AirCooler`, `Cooler`, `Dryer`, `FiredHeater`, `HeatExchanger`, `Heater`, `LNGHeatExchanger`, `MultiEffectEvaporator`, `MultiStreamHeatExchanger`, `MultiStreamHeatExchanger2`, `NeqHeater`, `ReBoiler`, `SteamHeater`, `WaterCooler` |
| `lng` | [LNG cargo ageing](../lng-ageing)<br>LNG storage, ageing, boil-off, and transport scenarios | `LNGAgeingScenario` |
| `manifold` | [Manifolds](manifolds)<br>Multi-inlet production and routing manifolds | `Manifold` |
| `membrane` | [Membranes](membranes)<br>Membrane separators | `MembraneSeparator` |
| `mixer` | [Mixers and splitters](mixers_splitters)<br>Equilibrium, static, non-equilibrium, and phase mixers | `Mixer`, `StaticMixer`, `StaticNeqMixer`, `StaticPhaseMixer` |
| `network` | [Networks](networks)<br>Pipe, looped, and well-flowline networks | `LoopedPipeNetwork`, `PipeFlowNetwork`, `WellFlowlineNetwork` |
| `pipeline` | [Pipelines](pipelines)<br>Steady and transient single-, two-, and multiphase pipelines | `AdiabaticPipe`, `AdiabaticTwoPhasePipe`, `IncompressiblePipeFlow`, `MultiphasePipe`, `OnePhasePipeLine`, `PipeBeggsAndBrills`, `PipeGray`, `PipeHagedornBrown`, `PipeMukherjeeAndBrill`, `Pipeline`, `Riser`, `SimpleTPoutPipeline`, `TopsidePiping`, `TransientPipe`, `TransientWellbore`, `TubingPerformance`, `TwoFluidPipe`, `TwoPhasePipeLine`, `WaterHammerPipe` |
| `powergeneration` | [Power generation](power_generation)<br>Turbines, fuel cells, renewables, and combined-cycle systems | `CombinedCycleSystem`, `FuelCell`, `GasTurbine`, `GasTurbineUnit`, `GasTurbineVendorPerformance`, `HRSG`, `OffshoreEnergySystem`, `SolarPanel`, `SteamTurbine`, `WindFarm`, `WindTurbine` |
| `pump` | [Pumps](pumps)<br>Centrifugal, ESP, jet, and sucker-rod pumps | `ESPPump`, `JetPump`, `Pump`, `SuckerRodPump` |
| `reactor` | [Reactors](reactors)<br>Equilibrium, kinetic, reforming, sulfur, and bioprocess reactors | `AmmoniaSynthesisReactor`, `AnaerobicDigester`, `AutothermalReformer`, `BiomassGasifier`, `CO2ImpurityKineticReactor`, `CatalyticTubeReformer`, `ClausCatalyticConverter`, `ClausReactionFurnace`, `EnzymeTreatment`, `FermentationReactor`, `Fermenter`, `FurnaceBurner`, `GibbsReactor`, `GibbsReactorCO2`, `IronSulfideOxidationSource`, `PartialOxidationReactor`, `PlugFlowReactor`, `PyrolysisReactor`, `QualifiedCO2ImpurityKineticReactor`, `QuenchSection`, `ReactiveWasteHeatBoiler`, `ReformerFurnace`, `StirredTankReactor`, `SubDewPointSulfurReactor`, `SulfurCondenser`, `SulfurDepositionAnalyser`, `SulfurOxidationReactor`, `SulfurRecoveryUnit`, `SyngasBurnerZone`, `TailGasTreatmentUnit`, `ThermalIncinerator`, `WaterGasShiftReactor` |
| `reservoir` | [Reservoirs and wells](reservoirs)<br>Reservoir, inflow, surveillance, and well-system equipment | `AnnularLeakagePath`, `CementDegradationModel`, `InjectionConformanceMonitor`, `MultiCompartmentReservoir`, `ReservoirCVDsim`, `ReservoirDiffLibsim`, `ReservoirTPsim`, `SimpleReservoir`, `TubingPerformance`, `WellFlow`, `WellSystem` |
| `separator` | [Separators](separators)<br>Phase, solids, cryogenic, and extraction separators | `CryogenicSeparator`, `Crystallizer`, `EndFlash`, `GasScrubber`, `GasScrubberSimple`, `Hydrocyclone`, `LiquidLiquidExtractor`, `NeqGasScrubber`, `PressureFilter`, `RotaryVacuumFilter`, `ScrewPress`, `Separator`, `SolidsCentrifuge`, `SolidsSeparator`, `ThreePhaseGasScrubber`, `ThreePhaseSeparator`, `TwoPhaseSeparator` |
| `solidhandling` | [Solid handling](solid_handling)<br>Biological feedstock preparation and solids handling | `BioFeedstockPreparation` |
| `splitter` | [Mixers and splitters](mixers_splitters)<br>Flow, component, capture, and upgrading splitters | `BiogasUpgrader`, `ComponentCaptureUnit`, `ComponentSplitter`, `Splitter` |
| `stream` | [Streams](streams)<br>Material, equilibrium, virtual, and diagnostic streams | `EquilibriumStream`, `IronIonSaturationStream`, `NeqStream`, `ScalePotentialCheckStream`, `Stream`, `VirtualStream` |
| `subsea` | [Subsea equipment](subsea_equipment)<br>Trees, manifolds, boosters, jumpers, flowlines, and umbilicals | `FlexiblePipe`, `FloatingSubstructure`, `MooringSystem`, `PLEM`, `PLET`, `SimpleFlowLine`, `SubseaBooster`, `SubseaJumper`, `SubseaManifold`, `SubseaPowerCable`, `SubseaTree`, `SubseaWell`, `Umbilical` |
| `tank` | [Tanks](tanks)<br>Storage, LNG, and vessel-depressurization equipment | `LNGTank`, `Tank`, `VesselDepressurization` |
| `util` | [Process utilities](util/)<br>Adjusters, recycles, calculators, fitters, setters, and utility systems | `Adjuster`, `AntiSurgeCalculator`, `Calculator`, `CompressorShaftCalculator`, `FlowRateAdjuster`, `FlowSetter`, `FuelGasSystem`, `GORfitter`, `LubeOilSystem`, `MPFMfitter`, `MoleFractionControllerUtil`, `MultiVariableAdjuster`, `NeqSimUnit`, `PressureDrop`, `ProductionRateFitter`, `Recycle`, `SetPoint`, `Setter`, `SpreadsheetBlock`, `StreamSaturatorUtil`, `StreamTransition`, `UnisimCalculator`, `UtilityAirSystem` |
| `valve` | [Valves](valves)<br>Control, shutdown, relief, rupture-disk, and throttling valves | `BlowdownValve`, `CheckValve`, `ControlValve`, `ESDValve`, `HIPPSValve`, `LevelControlValve`, `PSDValve`, `PressureControlValve`, `RuptureDisk`, `SafetyReliefValve`, `SafetyValve`, `ThrottlingValve` |
| `watertreatment` | [Water treatment](water_treatment)<br>Hydrocyclone, flotation, and produced-water treatment trains | `GasFlotationUnit`, `Hydrocyclone`, `ProducedWaterTreatmentTrain` |

## Equipment-adjacent framework packages

These packages live below `neqsim.process.equipment` but provide shared contracts, metadata,
or services rather than concrete process units, so they are not included in the equipment count.

| Source package | Documentation | Role |
| --- | --- | --- |
| `capacity` | [Capacity constraint framework](../CAPACITY_CONSTRAINT_FRAMEWORK) | Capacity strategies, constraints, bottleneck results, and design data |
| `failure` | [Failure modes](failure_modes) | Reliability and failure-mode metadata attached to equipment |
| `iec81346` | [Engineering diagram and identification guide](../../integration/engineering-diagram-document-model) | IEC 81346 reference designations and automatic assignment |
| `well` | [Well allocation](well_allocation) | Allocation results and production-allocation services |

## Catalog boundary

Controllers and measurement devices implement the broader `ProcessElementInterface` contract and
are documented separately in [controllers](../controllers) and [measurement devices](measurement_devices).
Mechanical-design calculators, thermodynamic systems, and process modules are likewise outside this
equipment-class inventory even when they configure or consume equipment results.

Return to the [equipment guide](./).
