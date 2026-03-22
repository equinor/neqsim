---
name: neqsim-capability-map
description: "Structured inventory of NeqSim's capabilities by engineering discipline. USE WHEN: checking what NeqSim can do, planning implementations, assessing gaps for engineering tasks, or routing work to the right agent. Covers thermodynamics, process equipment, PVT, standards, mechanical design, flow assurance, safety, and economics."
---

# NeqSim Capability Map

Structured reference of what NeqSim can do, organized by engineering discipline.
Use this to quickly check if a capability exists before searching the source code.

**Last updated:** 2026-03-21

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

### Pipelines

| Class | Type | Package |
|-------|------|---------|
| `AdiabaticPipe` | Adiabatic pipe flow | `process.equipment.pipeline` |
| `PipeBeggsAndBrills` | Beggs & Brill correlation | `process.equipment.pipeline` |
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
| `SeparatorMechanicalDesign` | Separator sizing | `process.mechanicaldesign.separator` |
| `PipelineMechanicalDesign` | Pipeline wall thickness | `process.mechanicaldesign.pipeline` |
| `CompressorMechanicalDesign` | Compressor design | `process.mechanicaldesign.compressor` |
| `ValveMechanicalDesign` | Valve mechanical design | `process.mechanicaldesign.valve` |
| `HeatExchangerMechanicalDesign` | HX mechanical design | `process.mechanicaldesign.heatexchanger` |
| `CompressorDesignFeasibilityReport` | Compressor feasibility (design + cost + suppliers + curves) | `process.mechanicaldesign.compressor` |
| `HeatExchangerDesignFeasibilityReport` | HX/cooler/heater feasibility (design + cost + suppliers) | `process.mechanicaldesign.heatexchanger` |
| `WellMechanicalDesign` | Well casing design | `process.mechanicaldesign.subsea` |
| `WellDesignCalculator` | API 5C3 calculations | `process.mechanicaldesign.subsea` |
| `WellCostEstimator` | Well cost estimation | `process.mechanicaldesign.subsea` |
| `SURFCostEstimator` | Subsea CAPEX | `process.mechanicaldesign.subsea` |
| `SubseaCostEstimator` | Regional cost factors | `process.mechanicaldesign.subsea` |
| `FieldDevelopmentDesignOrchestrator` | Full field design | `process.mechanicaldesign` |
| `MotorMechanicalDesign` | Motor foundation, vibration, cooling, bearings, noise, enclosure | `process.mechanicaldesign.motor` |
| `EquipmentDesignReport` | Combined mech + elec + motor design report with verdict | `process.mechanicaldesign` |

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

## J. Known Gaps and Limitations

### Major Gaps (commonly requested but missing or incomplete)

| Gap | Description | Workaround |
|-----|-------------|------------|
| **Rate-based distillation** | No mass-transfer-rate column model | Use equilibrium stages with efficiency |
| **Asphaltene modeling** | Limited asphaltene precipitation | Use PC-SAFT with tuned parameters |
| **Scale prediction** | No mineral scale model | External tool (ScaleChem) |
| **Erosion modeling** | No erosion rate calculation | API RP 14E velocity check only |
| **Detailed heat exchanger design** | No TEMA-level design | Duty + LMTD approach only |
| **Dynamic simulation** | Limited transient capability | `runTransient()` on ProcessSystem |
| **Reservoir coupling** | Simple reservoir only | No full reservoir simulator |
| **Control system simulation** | Basic PID controllers | No advanced control |
| **Amine sweetening** | Basic Kent-Eisenberg / DM | No rate-based amine model |
| **Membrane separation** | Basic membrane model | No detailed permeation |
| **BWRS EOS** | Only CH4 + C2H6 parameterized | Use SRK/PR instead |
| **NACE MR0175 material selection** | No systematic material logic | Manual standard lookup |
| **Detailed flare modeling** | No radiation / noise model | Source term only |
| **Full pipeline network** | Basic looped network solver | Limited to simple networks |

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
| Rate-based column? | ❌ | Not available |
| Scale prediction? | ❌ | Not available |
| Detailed HX design? | ❌ | Use duty + LMTD |
| Full reservoir sim? | ❌ | `SimpleReservoir` only |
| Motor sizing? | ✅ | `ElectricalMotor.sizeMotor()` |
| Motor foundation design? | ✅ | `MotorMechanicalDesign` |
| Motor vibration check? | ✅ | `MotorMechanicalDesign.getVibrationZone()` |
| Electrical load list? | ✅ | `ElectricalLoadList` |
| Combined design report? | ✅ | `EquipmentDesignReport` |
| VFD selection? | ✅ | `VariableFrequencyDrive` |
| Cable sizing? | ✅ | `ElectricalCable` |
| Hazardous area classification? | ✅ | `HazardousAreaClassification` |
