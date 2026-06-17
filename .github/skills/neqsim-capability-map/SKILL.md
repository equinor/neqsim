---
name: neqsim-capability-map
description: "Structured inventory of NeqSim's capabilities by engineering discipline. USE WHEN: checking what NeqSim can do, planning implementations, assessing gaps for engineering tasks, or routing work to the right agent. Covers thermodynamics, process equipment, PVT, standards, mechanical design, flow assurance, safety, and economics."
last_verified: "2026-07-04"
---

# NeqSim Capability Map

Structured reference of what NeqSim can do, organized by engineering discipline.
Use this to quickly check if a capability exists before searching the source code.

**Last updated:** 2026-03-23

---

## A. Thermodynamic Models (Equations of State)

### Cubic EOS

| Class | Fluid Type | Mixing Rule | Notes |
|-------|-----------|-------------|-------|
| `SystemSrkEos` | Dry/lean gas, simple HC | `"classic"` | Most common choice |
| `SystemPrEos` | General HC, oil systems | `"classic"` | Better liquid densities than SRK |
| `SystemPrEos1978` | HC with Peng-Robinson 1978 | `"classic"` | Updated alpha function |
| `SystemPrEosvolcor` | PR with volume correction | `"classic"` | Volume-translated PR |
| `SystemSrkEosvolcor` | SRK with volume correction | `"classic"` | Volume-translated SRK |
| `SystemSrkPenelouxEos` | SRK with Peneloux correction | `"classic"` | Volume shift |
| `SystemSrkMathiasCopeman` | SRK + Mathias-Copeman alpha | `"classic"` | Better vapor pressure fit |
| `SystemPrMathiasCopeman` | PR + Mathias-Copeman alpha | `"classic"` | Better vapor pressure fit |
| `SystemSrkTwuCoonEos` | SRK + Twu-Coon alpha | `"classic"` | TST-type alpha function |
| `SystemRKEos` | Redlich-Kwong (original) | `"classic"` | Historical / teaching |
| `SystemPrDanesh` | PR-Danesh variant | `"classic"` | Oil recovery applications |
| `SystemPsrkEos` | Predictive SRK (group contribution) | — | No BIPs needed for some systems |
| `SystemSrkSchwartzentruberEos` | SRK + Schwarzentruber-Renon | `"classic"` | Polar compounds |

### Association & Polar EOS (CPA)

| Class | Fluid Type | Mixing Rule | Notes |
|-------|-----------|-------------|-------|
| `SystemSrkCPAstatoil` | Water, MEG, methanol, polar | `10` (numeric) | **Primary CPA choice** |
| `SystemSrkCPA` | CPA (generic) | `10` | Base CPA |
| `SystemSrkCPAs` | CPA simplified | `10` | Faster CPA variant |
| `SystemPrCPA` | PR-based CPA | `10` | PR + association |
| `SystemElectrolyteCPAstatoil` | Brines, salt solutions | `10` | Electrolyte + CPA |
| `SystemElectrolyteCPA` | Electrolyte CPA | `10` | Base electrolyte |

### Reference EOS (High Accuracy)

| Class | Fluid Type | Notes |
|-------|-----------|-------|
| `SystemGERG2008Eos` | Natural gas (custody transfer) | ISO 20765, highest accuracy |
| `SystemGERG2004Eos` | Natural gas (older GERG) | Predecessor to 2008 |
| `SystemSpanWagnerEos` | Pure CO2 | CO2 reference EOS |
| `SystemLeachmanEos` | Pure hydrogen | H2 reference EOS |
| `SystemWaterIF97` | Pure water / steam | IAPWS-IF97 |
| `SystemVegaEos` | LNG / cryogenic | Specialized for LNG |

### SAFT Family

| Class | Fluid Type | Notes |
|-------|-----------|-------|
| `SystemPCSAFT` | Polymers, associating | PC-SAFT |
| `SystemPCSAFTa` | PC-SAFT variant | Alternative parameterization |

### Other / Specialized

| Class | Fluid Type | Notes |
|-------|-----------|-------|
| `SystemBWRSEos` | Benedict-Webb-Rubin-Starling | Only CH4 + C2H6 parameterized |
| `SystemIdealGas` | Ideal gas law | Teaching / reference |
| `SystemBnsEos` | BNS EOS | Specialized |
| `SystemUMRPRUEos` | UMRPRU group contribution | Predictive mixing rules |
| `SystemNRTL` | Activity coefficient (NRTL) | Liquid-liquid equilibrium |
| `SystemUNIFAC` | Activity coefficient (UNIFAC) | Group contribution |
| `SystemSoreideWhitson` | Soreide-Whitson for oil/water | HC-water mutual solubility |
| `SystemKentEisenberg` | Acid gas treating | H2S/CO2 in amines |
| `SystemDesmukhMather` | Acid gas treating | Amine systems |
| `SystemDuanSun` | CO2 in brine | CO2 solubility |
| `SystemAmmoniaEos` | Ammonia systems | NH3-specific |

**Package:** `neqsim.thermo.system`

---

## B. Flash Calculations (ThermodynamicOperations)

| Method | Class/Operation | Notes |
|--------|----------------|-------|
| TP flash | `ops.TPflash()` | Standard pressure-temperature |
| PH flash | `ops.PHflash()` | Pressure-enthalpy |
| PS flash | `ops.PSflash()` | Pressure-entropy |
| TV flash | `ops.TVflash()` | Temperature-volume |
| UV flash | `ops.UVflash()` | Internal energy-volume |
| Dew point P | `ops.dewPointPressureFlash()` | At given T |
| Dew point T | `ops.dewPointTemperatureFlash()` | At given P |
| Bubble point P | `ops.bubblePointPressureFlash()` | At given T |
| Bubble point T | `ops.bubblePointTemperatureFlash()` | At given P |
| Phase envelope | `ops.calcPTphaseEnvelope()` | Full PT envelope |
| Cricondenbar | `ops.calcPTphaseEnvelope()` → `.get("cricondenbar")` | Max pressure on envelope |
| Cricondentherm | `ops.calcPTphaseEnvelope()` → `.get("cricondentherm")` | Max temperature on envelope |
| Solid flash | `ops.TPSolidflash()` | Wax, hydrate, ice, S8 precipitation |
| Hydrate T | `ops.hydrateEquilibriumTemperature()` | Hydrate formation temperature |
| Hydrate P | `ops.hydrateFormationPressure()` | Hydrate formation pressure |
| Wax T | `ops.calcWAT()` | Wax appearance temperature |
| Freeze T | `ops.freezingPointTemperatureFlash()` | Freeze-out temperature |
| Water dew point | `ops.waterDewPointTemperature()` | Water content specification |

**CRITICAL:** After any flash, call `fluid.initProperties()` before reading
transport properties (viscosity, thermal conductivity, density).

**Package:** `neqsim.thermodynamicoperations`

---

## C. Process Equipment

### Separation

| Class | Type | Package |
|-------|------|---------|
| `Separator` | 2-phase (gas/liquid) | `process.equipment.separator` |
| `ThreePhaseSeparator` | 3-phase (gas/oil/water) | `process.equipment.separator` |
| `GasScrubber` | Gas scrubbing | `process.equipment.separator` |
| `GasScrubberSimple` | Simplified scrubber | `process.equipment.separator` |
| `NeqSeparator` | Non-equilibrium separator | `process.equipment.separator` |

### Compression & Expansion

| Class | Type | Package |
|-------|------|---------|
| `Compressor` | Centrifugal / reciprocating | `process.equipment.compressor` |
| `CompressorChart` | Performance curves | `process.equipment.compressor` |
| `Expander` | Turbo-expander | `process.equipment.expander` |

### Heat Transfer

| Class | Type | Package |
|-------|------|---------|
| `Heater` | Generic heater (duty specification) | `process.equipment.heatexchanger` |
| `Cooler` | Generic cooler (duty specification) | `process.equipment.heatexchanger` |
| `HeatExchanger` | Shell-and-tube (2 streams) | `process.equipment.heatexchanger` |
| `NeqHeater` | Non-equilibrium heater | `process.equipment.heatexchanger` |

### Valves & Pressure Control

| Class | Type | Package |
|-------|------|---------|
| `ThrottlingValve` | Isenthalpic flash | `process.equipment.valve` |

### Distillation & Absorption

| Class | Type | Package |
|-------|------|---------|
| `DistillationColumn` | Tray column | `process.equipment.distillation` |
| `Condenser` | Column condenser | `process.equipment.distillation` |
| `Reboiler` | Column reboiler | `process.equipment.distillation` |
| `SimpleTEGAbsorber` | TEG absorption | `process.equipment.absorber` |
| `SimpleAbsorber` | Generic absorber | `process.equipment.absorber` |
| `WaterStripperColumn` | Water stripper | `process.equipment.absorber` |

### Mixing & Splitting

| Class | Type | Package |
|-------|------|---------|
| `Mixer` | Stream mixing | `process.equipment.mixer` |
| `StaticMixer` | In-line mixer | `process.equipment.mixer` |
| `Splitter` | Stream splitting | `process.equipment.splitter` |
| `ComponentSplitter` | Component-based split | `process.equipment.splitter` |

### Pipelines & CO2 Well Analysis

| Class | Type | Package |
|-------|------|---------|
| `AdiabaticPipe` | Adiabatic pipe flow | `process.equipment.pipeline` |
| `PipeBeggsAndBrills` | Beggs & Brill correlation (+ formation temperature gradient) | `process.equipment.pipeline` |
| `TwoFluidPipe` | Transient two-fluid multiphase model (OLGA-like) with 7 conservation equations, AUSM+, boundary conditions | `process.equipment.pipeline` |
| `CO2InjectionWellAnalyzer` | CO2 injection well safety analysis | `process.equipment.pipeline` |
| `TransientWellbore` | Shutdown cooling / depressurization transient | `process.equipment.pipeline` |
| `CO2FlowCorrections` | CO2-specific two-phase flow corrections (static utility) | `process.equipment.pipeline` |
| `InterfacialFriction` | Interfacial friction closures (Taitel-Dukler, Andritsos-Hanratty, Wallis, Oliemans) | `process.equipment.pipeline.twophasepipe.closure` |
| `OnePhasePipeFlowNode` | Single-phase pipe | `fluidmechanics` |
| `TwoPhasePipeFlowNode` | Two-phase pipe | `fluidmechanics` |

### Reactors

| Class | Type | Package |
|-------|------|---------|
| `GibbsReactor` | Gibbs energy minimization | `process.equipment.reactor` |
| `GibbsReactorCO2` | CO2-focused Gibbs reactor | `process.equipment.reactor` |
| `StoichiometricReaction` | Stoichiometric reactions | `process.equipment.reactor` |
| `StirredTankReactor` | CSTR | `process.equipment.reactor` |
| `FurnaceBurner` | Combustion | `process.equipment.reactor` |
| `SulfurDepositionAnalyser` | S8 deposition analysis | `process.equipment.reactor` |
| `AmmoniaSynthesisReactor` | NH3 synthesis | `process.equipment.reactor` |
| `AnaerobicDigester` | Biogas production (AD) | `process.equipment.reactor` |
| `FermentationReactor` | Monod/Contois/substrate-inhibited kinetics | `process.equipment.reactor` |
| `BiogasUpgrader` | Biogas upgrading (membrane, PSA, amine, water scrub) | `process.equipment.splitter` |
| `BiomassGasifier` | Thermochemical gasification | `process.equipment.reactor` |
| `PyrolysisReactor` | Fast/slow/flash pyrolysis | `process.equipment.reactor` |

### Biorefinery Modules (Pre-built)

| Class | Type | Package |
|-------|------|--------|
| `BiogasToGridModule` | AD → upgrading → compression → grid | `process.processmodel.biorefinery` |
| `GasificationSynthesisModule` | Biomass gasification + Fischer-Tropsch | `process.processmodel.biorefinery` |
| `WasteToEnergyCHPModule` | AD → gas engine CHP | `process.processmodel.biorefinery` |

### Sustainability & Cost

| Class | Type | Package |
|-------|------|--------|
| `SustainabilityMetrics` | CO₂eq tracking, carbon intensity, EROI | `process.util.fielddevelopment` |
| `BiomassCharacterization` | Biomass properties (proximate, ultimate, HHV) | `thermo.characterization` |
| `BiorefineryCostEstimator` | CAPEX/OPEX for biorefinery equipment | `process.mechanicaldesign` |

### Measurement Devices & Instrumentation

| Class | Type | Package |
|-------|------|---------|
| `ImpurityMonitor` | Phase-partitioned impurity tracker with alarms | `process.measurementdevice` |
| `PressureTransmitter` | Pressure measurement (PT) | `process.measurementdevice` |
| `TemperatureTransmitter` | Temperature measurement (TT) | `process.measurementdevice` |
| `LevelTransmitter` | Level measurement (LT) | `process.measurementdevice` |
| `VolumeFlowTransmitter` | Volume flow measurement (FT) | `process.measurementdevice` |
| `MassFlowTransmitter` | Mass flow measurement | `process.measurementdevice` |
| `WaterDewPointAnalyser` | Water dew point measurement | `process.measurementdevice` |
| `HydrocarbonDewPointAnalyser` | HC dew point measurement | `process.measurementdevice` |
| `WaterContentAnalyser` | Water content measurement | `process.measurementdevice` |
| `CO2waterAnalyser` | CO2 in water measurement | `process.measurementdevice` |
| `AlarmConfig` | Alarm HH/H/L/LL thresholds per IEC 61511 | `process.measurementdevice` |
| `AlarmState` | Current alarm state tracking | `process.measurementdevice` |
| `ProcessAlarmManager` | System-wide alarm management | `process.measurementdevice` |
| `InstrumentTagRole` | Tag role classification (INPUT/BENCHMARK/VIRTUAL) | `process.measurementdevice` |
| `ControllerDeviceBaseClass` | PID controller base | `process.equipment.controller` |
| `DynamicProcessHelper` | Auto-instruments and controls a ProcessSystem | `process.equipment.util` |

### Pumps

| Class | Type | Package |
|-------|------|---------|
| `Pump` | Liquid pump | `process.equipment.pump` |

### Subsea & Wells

| Class | Type | Package |
|-------|------|---------|
| `SubseaWell` | Subsea well model | `process.equipment.subsea` |

### Utilities & Special

| Class | Type | Package |
|-------|------|---------|
| `Recycle` | Stream recycle convergence | `process.equipment.util` |
| `Adjuster` | Variable adjuster | `process.equipment.util` |
| `SetPoint` | Set point controller | `process.equipment.util` |
| `Calculator` | Custom calculator | `process.equipment.util` |
| `StreamSaturatorUtil` | Stream saturation | `process.equipment.util` |
| `GasGenerator` | Gas generation | `process.equipment.util` |
| `Flare` | Flare system | `process.equipment.flare` |
| `Filter` | Particle filter | `process.equipment.filter` |
| `Ejector` | Ejector / eductor | `process.equipment.ejector` |
| `Electrolyzer` | Water electrolysis | `process.equipment.electrolyzer` |
| `Tank` | Storage tank | `process.equipment.tank` |
| `Manifold` | Subsea manifold | `process.equipment.manifold` |
| `SimpleReservoir` | Simple reservoir model | `process.equipment.reservoir` |

### Process System

| Class | Type | Package |
|-------|------|---------|
| `ProcessSystem` | Flowsheet container | `process.processmodel` |
| `ProcessConnection` | Equipment connections | `process.processmodel` |

---

## D. PVT Simulations

| Class | Experiment | Package |
|-------|-----------|---------|
| `ConstantMassExpansion` | CME / CCE | `pvtsimulation.simulation` |
| `ConstantVolumeDepletion` | CVD | `pvtsimulation.simulation` |
| `DifferentialLiberation` | Differential liberation | `pvtsimulation.simulation` |
| `SeparatorTest` | Multi-stage separator test | `pvtsimulation.simulation` |
| `SwellingTest` | Gas injection swelling | `pvtsimulation.simulation` |
| `SaturationPressure` | Bubble/dew point P | `pvtsimulation.simulation` |
| `SaturationTemperature` | Bubble/dew point T | `pvtsimulation.simulation` |
| `SlimTube` | Slim tube / MMP | `pvtsimulation.simulation` |
| `ViscositySimulation` | Viscosity vs P | `pvtsimulation.simulation` |
| `GOR` | Gas-oil ratio vs P | `pvtsimulation.simulation` |

### Flow Assurance (PVT module)

| Class | Assessment | Package |
|-------|-----------|---------|
| `DeWaardMilliamsCorrosion` | CO2/H2S corrosion rate | `pvtsimulation.flowassurance` |

### Regression / Tuning

| Class | Purpose | Package |
|-------|---------|---------|
| `ModelTuning` | EOS parameter fitting | `pvtsimulation.modeltuning` |

---

## E. Gas Quality Standards

| Class | Standard | Package |
|-------|---------|---------|
| `Standard_ISO6976` | ISO 6976 — calorific value, Wobbe, density | `standards.gasquality` |
| `Standard_ISO6976_2016` | ISO 6976:2016 update | `standards.gasquality` |
| `Standard_ISO6578` | ISO 6578 — LNG custody transfer | `standards.gasquality` |
| `Standard_ISO15403` | ISO 15403 — natural gas for vehicles | `standards.gasquality` |
| `Standard_EN16726` | EN 16726 — European gas quality | `standards.gasquality` |
| `UKgasQuality` | UK gas quality specification | `standards.gasquality` |
| `GasChromatographData` | GC data processing | `standards.gasquality` |
| `BrackettedMolarComposition` | Molar composition bracketing | `standards.gasquality` |
| `Draft_GERG2004` | GERG-2004 draft implementation | `standards.gasquality` |
| `SulfurSpecificationMethod` | H2S / total sulfur limits | `standards.gasquality` |

### Oil Quality

| Class | Standard | Package |
|-------|---------|---------|
| `Standard_ASTM_D6377` | ASTM D6377 — vapor pressure | `standards.oilquality` |
| `Standard_ASTM_D2887` | ASTM D2887 — simulated distillation | `standards.oilquality` |
| `Standard_ASTM_D4294` | ASTM D4294 — sulfur in oil | `standards.oilquality` |

---

## F. Mechanical Design

| Class | Equipment | Package |
|-------|----------|---------|
| `MechanicalDesign` | Base class | `process.mechanicaldesign` |
| `SeparatorMechanicalDesign` | Separator vessel sizing, K-factor, demister config, nozzle sizing, liquid levels (HHLL/HLL/NLL/LLL/LLLL), inlet device, foam allowance, retention time, entrainment performance, design validation. **Gateway for all separator physical configuration.** Bridge methods: entrainment (`setInletPipeDiameter`, `setInletDeviceType`, `addSeparatorSection`, `setGasLiquidSurfaceTension`) and dynamic internals (`setWeirHeightAbsolute`, `setWeirLength`, `setBootVolume`, `setMistEliminatorDpCoeff`, `setMistEliminatorThickness`, `applyDemistingInternal`). | `process.mechanicaldesign.separator` |
| `DemistingInternal` | Demisting internal sizing — Souders-Brown max gas velocity, Eu-number pressure drop, carry-over model. Types: wire mesh, vane pack, cyclone. | `process.mechanicaldesign.separator.internals` |
| `DemistingInternalWithDrainage` | Demisting internal with drainage section — reduces carry-over by drainage efficiency factor. | `process.mechanicaldesign.separator.internals` |
| `PrimarySeparation` | Inlet device base — inlet momentum (rho*v^2), momentum limit checking, liquid carry-over with degradation. | `process.mechanicaldesign.separator.primaryseparation` |
| `InletVane` | Inlet vane device (6000 Pa max momentum, 85% bulk efficiency). | `process.mechanicaldesign.separator.primaryseparation` |
| `InletVaneWithMeshpad` | Inlet vane + downstream mesh pad (92% + mesh pad capture). | `process.mechanicaldesign.separator.primaryseparation` |
| `InletCyclones` | Inlet cyclone cluster (8000 Pa max momentum, 95% bulk efficiency). | `process.mechanicaldesign.separator.primaryseparation` |
| `PipelineMechanicalDesign` | Pipeline wall thickness | `process.mechanicaldesign.pipeline` |
| `CompressorMechanicalDesign` | Compressor design | `process.mechanicaldesign.compressor` |
| `ValveMechanicalDesign` | Valve mechanical design | `process.mechanicaldesign.valve` |
| `HeatExchangerMechanicalDesign` | HX mechanical design with auto-selection (min area/weight/dP) | `process.mechanicaldesign.heatexchanger` |
| `CompressorDesignFeasibilityReport` | Compressor feasibility (design + cost + suppliers + curves) | `process.mechanicaldesign.compressor` |
| `HeatExchangerDesignFeasibilityReport` | HX/cooler/heater feasibility (design + cost + suppliers) | `process.mechanicaldesign.heatexchanger` |
| `ThermalDesignCalculator` | Tube/shell-side HTCs, overall U, pressure drops, zone analysis | `process.mechanicaldesign.heatexchanger` |
| `BellDelawareMethod` | Bell-Delaware shell-side HTC & dP with J-factor corrections | `process.mechanicaldesign.heatexchanger` |
| `LMTDcorrectionFactor` | LMTD F_t for multi-pass configurations (Bowman-Mueller-Nagle) | `process.mechanicaldesign.heatexchanger` |
| `VibrationAnalysis` | Flow-induced vibration screening per TEMA RCB-4.6 | `process.mechanicaldesign.heatexchanger` |
| `ShellAndTubeDesignCalculator` | Full TEMA-based S&T design with ASME VIII, NACE, thermal, cost | `process.mechanicaldesign.heatexchanger` |
| `WellMechanicalDesign` | Well casing design | `process.mechanicaldesign.subsea` |
| `WellDesignCalculator` | API 5C3 calculations | `process.mechanicaldesign.subsea` |
| `WellCostEstimator` | Well cost estimation | `process.mechanicaldesign.subsea` |
| `SURFCostEstimator` | Subsea CAPEX | `process.mechanicaldesign.subsea` |
| `SubseaCostEstimator` | Regional cost factors | `process.mechanicaldesign.subsea` |
| `FieldDevelopmentDesignOrchestrator` | Full field design | `process.mechanicaldesign` |
| `MotorMechanicalDesign` | Motor foundation, vibration, cooling, bearings, noise, enclosure | `process.mechanicaldesign.motor` |
| `EquipmentDesignReport` | Combined mech + elec + motor design report with verdict | `process.mechanicaldesign` |

### Engineering Deliverables

| Class | Purpose | Package |
|-------|---------|---------|
| `StudyClass` | Enum (CLASS_A/CLASS_B/CLASS_C) defining required deliverables per study tier | `process.mechanicaldesign` |
| `EngineeringDeliverablesPackage` | Orchestrates generation of all deliverables for a study class | `process.mechanicaldesign` |
| `ProcessFlowDiagramExporter` | Graphviz DOT export of ProcessSystem topology | `process.processmodel` |
| `ThermalUtilitySummary` | Cooling water, LP/MP/HP steam, fuel gas, instrument air | `process.mechanicaldesign` |
| `AlarmTripScheduleGenerator` | Alarm/trip setpoints per IEC 61511 / NORSOK I-001 | `process.mechanicaldesign` |
| `InstrumentScheduleGenerator` | ISA-5.1 tagged instrument schedule with live MeasurementDevice bridge | `process.mechanicaldesign` |
| `SparePartsInventory` | Recommended spare parts by equipment type with lead times | `process.mechanicaldesign` |
| `FireProtectionDesign` | Jet fire, BLEVE, pool fire scenario assessment (API 521) | `process.mechanicaldesign.designstandards` |
| `NoiseAssessment` | Equipment noise + ISO 9613-2 atmospheric attenuation | `process.mechanicaldesign.designstandards` |

### Electrical Design

| Class | Purpose | Package |
|-------|---------|---------|
| `ElectricalDesign` | Base class — sizes motor, VFD, cables, switchgear | `process.electricaldesign` |
| `ElectricalMotor` | AC induction motor model (IEC 60034, IEEE 841) | `process.electricaldesign.components` |
| `VariableFrequencyDrive` | VFD with topology, harmonics, efficiency | `process.electricaldesign.components` |
| `ElectricalCable` | Cable sizing with derating (IEC 60502) | `process.electricaldesign.components` |
| `Transformer` | Power transformer model (IEC 60076) | `process.electricaldesign.components` |
| `Switchgear` | MCC / switchgear bucket (IEC 61439) | `process.electricaldesign.components` |
| `HazardousAreaClassification` | Zone / Ex marking (IEC 60079) | `process.electricaldesign.components` |
| `CompressorElectricalDesign` | Compressor-specific with auxiliary loads | `process.electricaldesign.compressor` |
| `PumpElectricalDesign` | Pump-specific design | `process.electricaldesign.pump` |
| `SeparatorElectricalDesign` | Separator auxiliary loads | `process.electricaldesign.separator` |
| `HeatExchangerElectricalDesign` | Electric heater / air cooler / S&T detection | `process.electricaldesign.heatexchanger` |
| `PipelineElectricalDesign` | Heat tracing, cathodic protection | `process.electricaldesign.pipeline` |
| `SystemElectricalDesign` | Plant-wide load aggregation, transformer sizing | `process.electricaldesign.system` |
| `ElectricalLoadList` | Load list with demand/diversity factors | `process.electricaldesign.loadanalysis` |

---

## G. Physical Properties

| Class | Property | Package |
|-------|---------|---------|
| `PhysicalPropertyHandler` | Property dispatcher | `physicalproperties` |
| Viscosity models | Gas/liquid/mixing rule | `physicalproperties.methods` |
| Thermal conductivity | Gas/liquid | `physicalproperties.methods` |
| Diffusion coefficients | Binary diffusion | `physicalproperties.methods` |

**Access pattern:** After `fluid.initProperties()`, use:
- `fluid.getPhase("gas").getViscosity("kg/msec")`
- `fluid.getPhase("gas").getThermalConductivity("W/mK")`
- `fluid.getPhase("gas").getDensity("kg/m3")`

---

## H. Utility Classes

| Class | Purpose | Package |
|-------|---------|---------|
| `FluidBuilder` | Fluent fluid construction | `thermo.system` |
| `HeatMaterialBalance` | Process-wide H&M balance | `util.engineering` |
| `SensitivityAnalysis` | Parametric sweeps | `util.engineering` |
| `MonteCarloSimulator` | Monte Carlo sampling | `util.engineering` |
| `ConvergenceDiagnostics` | Solver diagnostics | `util.engineering` |
| `HydrateRiskMapper` | Hydrate risk matrix | `util.engineering` |
| `EOSComparison` | Multi-EOS comparison | `util.engineering` |
| `PinchAnalyzer` | Pinch analysis (heat integration) | `util.engineering` |
| `DCFCalculator` | NPV / DCF economics | `util.engineering` |
| `DebottleneckAnalyzer` | Capacity bottleneck finding | `util.engineering` |
| `ProcessValidator` | Equipment validation | `util.engineering` |
| `CoolingWaterSystem` | Cooling water sizing | `process.equipment.heatexchanger` |
| `FiredHeater` | Fired heater modeling | `process.equipment.heatexchanger` |

---

## I. Chemical Reactions

| Capability | Implementation | Notes |
|-----------|---------------|-------|
| Gibbs energy minimization | `GibbsReactor` | Newton-Raphson, element balanced |
| Stoichiometric reactions | `StoichiometricReaction` | User-defined reactions |
| Combustion | `FurnaceBurner` | Fuel gas combustion |
| Sulfur reactions | `SulfurDepositionAnalyser` | Claus + corrosion reactions |
| Ammonia synthesis | `AmmoniaSynthesisReactor` | Haber-Bosch |
| Chemical equilibrium DB | `GibbsReactDatabase.csv` | Thermodynamic data |

---

## I-bis. Bioprocessing & Bioenergy

| Capability | Implementation | Notes |
|-----------|---------------|-------|
| Anaerobic digestion | `AnaerobicDigester` | Substrate-specific biogas yields (food waste, sewage, manure, crop residue) |
| Fermentation kinetics | `FermentationReactor` | Monod, Contois, substrate-inhibited; batch, fed-batch, continuous |
| Biogas upgrading | `BiogasUpgrader` | Water scrubbing, amine, PSA, membrane; auto CH4/CO2 split |
| Biomass gasification | `BiomassGasifier` | Air/oxygen/steam; syngas composition from biomass properties |
| Pyrolysis | `PyrolysisReactor` | Fast, slow, flash; bio-oil, char, gas yields |
| Sustainability / LCA | `SustainabilityMetrics` | CO₂eq (IPCC AR6 GWP), carbon intensity, EROI, renewable fraction |
| Biomass characterization | `BiomassCharacterization` | Proximate, ultimate analysis; HHV correlations |
| Biorefinery costing | `BiorefineryCostEstimator` | Equipment-level CAPEX/OPEX estimation |
| Biogas-to-grid chain | `BiogasToGridModule` | Pre-built: AD → upgrading → compression → grid |
| Gasification + FT | `GasificationSynthesisModule` | Pre-built: biomass → syngas → Fischer-Tropsch liquids |
| Waste-to-energy CHP | `WasteToEnergyCHPModule` | Pre-built: AD → gas engine with electrical + thermal output |

---

## I-ter. Equipment Diagnostics & Reliability

| Capability | Implementation | Notes |
|-----------|---------------|-------|
| Root cause analysis | `RootCauseAnalyzer` | Bayesian-inspired RCA with multi-source reliability priors |
| Symptom-based diagnosis | `Symptom` enum (12 symptoms) | TRIP, HIGH_VIBRATION, SURGE, FOULING, etc. |
| Hypothesis generation | `HypothesisGenerator` | Built-in libraries for compressor, pump, separator, HX, valve |
| Time-series evidence | `EvidenceCollector` | Trend, threshold, rate-of-change, correlation, multi-parameter pattern |
| Simulation verification | `SimulationVerifier` | Clone-perturb-compare with graduated severity |
| Reliability data | `ReliabilityDataSource` | IOGP/SINTEF, CCPS, IEEE 493, Lees, OREDA (9 sources, 4 CSVs) |
| Equipment failure modes | `EquipmentFailureMode` | MTBF/MTTR/failure rate calculations |
| Diagnostic reporting | `RootCauseReport` | JSON, text, ranked hypotheses with confidence scores |
| Custom hypothesis registry | `HypothesisGenerator.register()` | Domain-specific hypothesis extension |

**Package:** `neqsim.process.diagnostics`, `neqsim.process.equipment.failure`

---

## J. Known Gaps and Limitations

### Major Gaps (commonly requested but missing or incomplete)

| Gap | Description | Workaround |
|-----|-------------|------------|
| **Rate-based distillation** | No mass-transfer-rate column model | Use equilibrium stages with efficiency |
| **Asphaltene modeling** | Limited asphaltene precipitation | Use PC-SAFT with tuned parameters |
| **Scale prediction** | No mineral scale model | External tool (ScaleChem) |
| **Erosion modeling** | No erosion rate calculation | API RP 14E velocity check only |
| **Detailed heat exchanger design** | TEMA-level design with Bell-Delaware, vibration, ASME VIII | Full thermal-hydraulic + mechanical via `ShellAndTubeDesignCalculator` |
| **Dynamic simulation** | Limited transient capability | `runTransient()` on ProcessSystem |
| **Reservoir coupling** | Simple reservoir only | No full reservoir simulator |
| **Control system simulation** | PID controllers + AlarmConfig + InstrumentScheduleGenerator + DynamicProcessHelper | No DCS/SIS emulation |
| **Amine sweetening** | Basic Kent-Eisenberg / DM | No rate-based amine model |
| **Membrane separation** | Basic membrane model | No detailed permeation |
| **BWRS EOS** | Only CH4 + C2H6 parameterized | Use SRK/PR instead |
| **NACE MR0175 material selection** | No systematic material logic | Manual standard lookup |
| **Detailed flare modeling** | No radiation / noise model | Source term only |
| **Full pipeline network** | LoopedPipeNetwork: NR-GGA solver, 120+ wells, IPR (PI/Vogel/Fetkovich), chokes, tubing VLP, Beggs-Brill multiphase, compressors, regulators, artificial lift (gas lift/ESP/jet/rod pump), water handling, sand erosion (DNV RP O501), corrosion (de Waard-Milliams/NORSOK M-506), GHG emissions tracking | Full-featured production network |

### EOS Limitations

| EOS | Known Limitation |
|-----|-----------------|
| SRK/PR | Poor near critical point; liquid density without volume correction |
| CPA | Slower convergence; limited component database for association |
| GERG-2008 | Only natural gas components (21 components) |
| PC-SAFT | Limited BIP database; slower than cubic EOS |
| BWRS | Only 2 components parameterized |

### Component Database Limitations

| Component Type | Coverage |
|---------------|----------|
| Light HCs (C1-C10) | ✅ Excellent |
| Heavy HCs (C11-C36) | ✅ Good (TBP fractions) |
| Common gases (N2, CO2, H2S, H2, O2) | ✅ Excellent |
| Water, MEG, methanol, TEG | ✅ Good (CPA parameterized) |
| Amines (MEA, DEA, MDEA) | ⚠️ Limited |
| Sulfur species (S8, SO2, COS) | ⚠️ Partial |
| Ionic species | ⚠️ Limited database |
| Polymers | ⚠️ Few PC-SAFT parameters |
| Refrigerants | ⚠️ Some available |
| Specialty chemicals | ❌ Case-by-case |

---

## K. Reservoir & Production Properties

| Class | Purpose | Package |
|-------|---------|---------|
| `GasPseudoCriticalProperties` | Pseudo-critical T, P | `pvtsimulation.reservoirproperties` |
| `CompositionEstimation` | Reservoir fluid estimation | `pvtsimulation.reservoirproperties` |
| `ZFactorCorrelations` | Z-factor (Standing, DAK, etc.) | `pvtsimulation.reservoirproperties` |
| `SimpleReservoir` | Material balance reservoir | `process.equipment.reservoir` |
| `Well` | Well inflow model | `process.equipment.well` |

---

## Quick Lookup: "Can NeqSim do X?"

| Question | Answer | Class/Method |
|----------|--------|-------------|
| Phase envelope? | ✅ | `ops.calcPTphaseEnvelope()` |
| Hydrate temperature? | ✅ | `ops.hydrateEquilibriumTemperature()` |
| Wax appearance? | ✅ | `ops.calcWAT()` |
| CO2 corrosion rate? | ✅ | `DeWaardMilliamsCorrosion` |
| H2S sour classification? | ✅ | `DeWaardMilliamsCorrosion.isSourService()` |
| ISO 6976 calorific value? | ✅ | `Standard_ISO6976` |
| Pipeline sizing? | ✅ | `PipeBeggsAndBrills` + `PipelineMechanicalDesign` |
| Compressor power? | ✅ | `Compressor.getPower("kW")` |
| NPV / DCF? | ✅ | `DCFCalculator` |
| Well casing design? | ✅ | `WellDesignCalculator` |
| TEG dehydration? | ✅ | `SimpleTEGAbsorber` |
| Sulfur deposition? | ✅ | `SulfurDepositionAnalyser` |
| JT cooling? | ✅ | `ThrottlingValve` |
| Depressurization? | ✅ | `ProcessSystem.runTransient()` |
| Monte Carlo? | ✅ | `MonteCarloSimulator` |
| Heat integration? | ✅ | `PinchAnalyzer` |
| Amine sweetening? | ⚠️ Basic | Kent-Eisenberg model |
| Biogas production? | ✅ | `AnaerobicDigester` |
| Fermentation kinetics? | ✅ | `FermentationReactor` (Monod/Contois) |
| Biogas upgrading? | ✅ | `BiogasUpgrader` (4 technologies) |
| Biomass gasification? | ✅ | `BiomassGasifier` |
| Sustainability / LCA? | ✅ | `SustainabilityMetrics` |
| Biogas-to-grid module? | ✅ | `BiogasToGridModule` |
| Waste-to-energy CHP? | ✅ | `WasteToEnergyCHPModule` |
| Rate-based column? | ❌ | Not available |
| Scale prediction? | ❌ | Not available |
| Detailed HX design? | ❌ | Use duty + LMTD |
| Full reservoir sim? | ❌ | `SimpleReservoir` only |
| CO2 injection well analysis? | ✅ | `CO2InjectionWellAnalyzer` |
| CO2 wellbore shutdown transient? | ✅ | `TransientWellbore` |
| CO2 flow corrections? | ✅ | `CO2FlowCorrections` |
| Impurity monitoring? | ✅ | `ImpurityMonitor` |
| Formation temperature gradient? | ✅ | `PipeBeggsAndBrills.setFormationTemperatureGradient()` |
| Motor sizing? | ✅ | `ElectricalMotor.sizeMotor()` |
| Motor foundation design? | ✅ | `MotorMechanicalDesign` |
| Motor vibration check? | ✅ | `MotorMechanicalDesign.getVibrationZone()` |
| Electrical load list? | ✅ | `ElectricalLoadList` |
| Combined design report? | ✅ | `EquipmentDesignReport` |
| VFD selection? | ✅ | `VariableFrequencyDrive` |
| Cable sizing? | ✅ | `ElectricalCable` |
| Hazardous area classification? | ✅ | `HazardousAreaClassification` |
