# NeqSim Functionality Review for Oil & Gas Installation Design and Operations

**Review Date:** January 31, 2026  
**Version Reviewed:** NeqSim 3.3.0  
**Scope:** Onshore and Offshore Oil & Gas Installations

---

## Executive Summary

NeqSim is a comprehensive Java-based thermodynamic and process simulation toolkit with strong capabilities for oil and gas applications. This review evaluates its functionality for designing and operating both onshore and offshore installations, identifying strengths and areas for improvement.

**Overall Assessment:**
- **Thermodynamics & PVT:** ★★★★★ Excellent
- **Process Simulation:** ★★★★☆ Very Good
- **Mechanical Design:** ★★★★☆ Very Good
- **Safety Systems:** ★★★★☆ Very Good
- **Flow Assurance:** ★★★☆☆ Good
- **Operations Support:** ★★★☆☆ Good
- **Offshore-Specific:** ★★★☆☆ Good

---

## 1. STRONG FUNCTIONALITY (Well-Developed Areas)

### 1.1 Thermodynamic Foundation (Excellent)

**Strengths:**
- **Comprehensive EoS Library:** 50+ equation of state implementations including:
  - SRK, PR, GERG-2004/2008 for natural gas
  - CPA (Cubic Plus Association) for polar/associating systems
  - PC-SAFT for polymer applications
  - Electrolyte models (Pitzer, Desmukh-Mather)
  - Specialized models: Leachman (hydrogen), Span-Wagner, VEGA

- **Multi-Phase Flash Calculations:**
  - TP, PH, PS, VU, TV, TH flashes fully implemented
  - Solid phase handling (hydrates, wax, ice, CO₂ solids)
  - Hydrate equilibrium calculations (`TPHydrateFlash`)
  - WAX flash calculations (`TPmultiflashWAX`)

- **Physical Properties:**
  - Density, viscosity, thermal conductivity, heat capacity
  - Surface/interfacial tension
  - Diffusion coefficients
  - Sound velocity

**Code Example Location:** [src/main/java/neqsim/thermo/system](src/main/java/neqsim/thermo/system)

### 1.2 Process Equipment Library (Very Good)

**Comprehensive Equipment Coverage:**

| Category | Equipment Types | Maturity |
|----------|-----------------|----------|
| **Separators** | 2-phase, 3-phase, gas scrubbers, hydrocyclones | ★★★★★ |
| **Compressors** | Centrifugal with performance charts, surge/stone wall, anti-surge | ★★★★★ |
| **Heat Exchangers** | Shell & tube, multi-stream, air coolers, heaters/coolers | ★★★★☆ |
| **Pumps** | Standard pumps, ESP (Electric Submersible Pump) | ★★★★☆ |
| **Valves** | Control, safety/relief, HIPPS, ESD, PSD, blowdown, check | ★★★★★ |
| **Distillation** | Tray columns, multiple solvers (direct, damped, inside-out) | ★★★★☆ |
| **Pipelines** | Adiabatic pipe, Beggs & Brill, Two-Fluid transient, riser models | ★★★★★ |
| **Reactors** | Gibbs reactors, furnace burners | ★★★☆☆ |
| **Subsea** | Trees, manifolds, PLET/PLEM, jumpers, umbilicals | ★★★★☆ |

**Notable Features:**
- Compressor performance charts with surge/stone wall curve generation
- ESP pump with GVF (Gas Void Fraction) degradation modeling
- Capacity constraint framework for all major equipment
- Auto-sizing capabilities for separators, compressors, pumps

**Code Locations:**
- Equipment: [src/main/java/neqsim/process/equipment](src/main/java/neqsim/process/equipment)
- Compressor charts: [src/main/java/neqsim/process/equipment/compressor](src/main/java/neqsim/process/equipment/compressor)

### 1.3 Mechanical Design Framework (Very Good)

**Strengths:**
- **Design Standards Implementation:**
  - ASME Section VIII (pressure vessels)
  - ASME B31.3/B31.4/B31.8 (piping & pipelines)
  - API 5L, API 650, API 520/521
  - DNV-OS-F101/ST-F101 (subsea pipelines)
  - NORSOK standards integration

- **Equipment-Specific Design:**
  - Pipeline wall thickness calculations
  - Riser mechanical design
  - Topside piping design
  - Separator sizing per NORSOK P-001
  - Compressor mechanical design

- **Cost Estimation:**
  - Material costs from database
  - Labor estimation
  - Bill of Materials generation
  - Weight calculations

- **TR Document Support:**
  - Company-specific technical requirements
  - Equinor TR1400-series for pipelines
  - Design factor overrides

**Code Location:** [src/main/java/neqsim/process/mechanicaldesign](src/main/java/neqsim/process/mechanicaldesign)

### 1.4 Safety Systems (Very Good)

**Implemented Safety Features:**

| Feature | Description | Status |
|---------|-------------|--------|
| **Depressurization/Blowdown** | Dynamic vessel blowdown with multiple thermodynamic modes | ★★★★★ |
| **Fire Case Modeling** | API 521 pool fire, jet fire heat loads | ★★★★☆ |
| **Relief Valve Sizing** | API 520/521 compliant PSV sizing | ★★★★☆ |
| **HIPPS** | High Integrity Pressure Protection Systems | ★★★★☆ |
| **ESD Valves** | Emergency shutdown valve modeling | ★★★★☆ |
| **Safety Envelopes** | Hydrate, WAX, MDMT envelope calculations | ★★★★☆ |
| **Risk Framework** | Monte Carlo, bow-tie, SIS/SIF integration | ★★★★☆ |

**Advanced Risk Analysis:**
- Dynamic risk simulation with transients
- SIS/SIF integration per IEC 61508/61511
- Bow-tie diagram analysis
- Portfolio risk (VaR analysis)
- Condition-based reliability

**Code Locations:**
- Safety: [src/main/java/neqsim/process/safety](src/main/java/neqsim/process/safety)
- Fire: [src/main/java/neqsim/process/util/fire](src/main/java/neqsim/process/util/fire)
- Risk: [src/main/java/neqsim/process/safety/risk](src/main/java/neqsim/process/safety/risk)

### 1.5 Gas Quality & Standards (Very Good)

**Implemented Standards:**
- **ISO 6976:** Calorific values, density, Wobbe index (2016 version)
- **ISO 6974:** Gas chromatography analysis
- **ISO 6578:** Cryogenic liquid measurement
- **ISO 15403:** Natural gas quality
- **ISO 18453:** Draft implementation
- **GERG-2004/2008:** Reference equation of state
- **UK ICF SI:** UK gas quality specifications

**Code Location:** [src/main/java/neqsim/standards/gasquality](src/main/java/neqsim/standards/gasquality)

### 1.6 ProcessSystem & Flowsheet Management (Very Good)

**Capabilities:**
- Recycle loop handling with convergence control
- Adjuster units for process specifications
- Transient simulation support
- Equipment serialization/cloning
- Graph-based topology
- JSON/reporting output
- Measurement history tracking

**Code Location:** [src/main/java/neqsim/process/processmodel](src/main/java/neqsim/process/processmodel)

---

## 2. MODERATE FUNCTIONALITY (Adequate but Could Be Enhanced)

### 2.1 Flow Assurance (Good, With Erosion Modeling)

**Current Capabilities:**
- Hydrate equilibrium temperature calculations
- Hydrate curve generation
- WAX flash calculations (TPmultiflashWAX)
- Asphaltene stability analysis (De Boer screening)
- Hydrocarbon dew point analysis
- Water content analysis
- **Erosional velocity calculation** (API RP 14E)

**Erosion-Corrosion Modeling:**

| Feature | Class | Description |
|---------|-------|-------------|
| **API RP 14E** | `ManifoldMechanicalDesignCalculator` | Ve = C/√ρ with configurable C-factor |
| **Velocity Checks** | `calculateErosionalVelocity()` | Gas, liquid, multiphase limits |
| **Sand Effects** | Via C-factor adjustment | C=100 clean, C=125-150 with solids |

**Code Example:**
```java
ManifoldMechanicalDesignCalculator calc = new ManifoldMechanicalDesignCalculator();
calc.setErosionalCFactor(100.0);  // Clean service
calc.setMixtureDensity(pipe.getMixtureDensity());
double ve = calc.calculateErosionalVelocity();  // m/s

// Check actual vs erosional
double actualV = pipe.getMixtureVelocity();
boolean safe = actualV < ve * 0.8;  // 80% margin
```

**Minor Gaps:**
- ⚠️ Limited pigging simulation
- ⚠️ Scale prediction limited (CaCO₃, BaSO₄ not yet implemented)
- ⚠️ MEG/TEG tracking through system could be enhanced

**Note:** Slug flow is well-handled via TwoFluidPipe + SlugTracker (see Pipeline section)

**Code Location:** [src/main/java/neqsim/pvtsimulation/flowassurance](../src/main/java/neqsim/pvtsimulation/flowassurance)

### 2.2 Pipeline & Multiphase Flow (Very Good)

**Current Models:**
- **TwoFluidPipe**: Full two-fluid transient model (OLGA-like) with:
  - Separate momentum equations for gas and liquid phases
  - Terrain-induced liquid accumulation tracking
  - Transient pressure wave propagation
  - Countercurrent flow capability
- **FlowRegimeDetector**: Mechanistic flow regime detection (Taitel-Dukler, Barnea)
- **SlugTracker**: Lagrangian slug tracking with:
  - Slug front/tail position tracking
  - Slug growth, decay, merging
  - Terrain-induced slug modeling
  - Bendiksen velocity correlation
- **SevereSlugAnalyser**: Severe slug analysis for risers
- Beggs & Brill correlation
- Adiabatic pipe model
- Riser configurations

**Minor Gaps:**
- ⚠️ Limited burial/seabed thermal interaction for offshore
- ✅ Pipeline network solver for looped systems now implemented (LoopedPipeNetwork)

### 2.3 MPC/Control Integration (Good, Needs Enhancement)

**Current Capabilities:**
- Model Predictive Controller framework
- Step response generation
- Process linearization
- State space export
- SUBR-MODL export format
- Soft sensor export

**Limitations:**
- ❌ Limited real-time integration examples
- ❌ No OPC-UA/OPC-DA connectors built-in
- ❌ Limited tuning guidance

**Code Location:** [src/main/java/neqsim/process/mpc](src/main/java/neqsim/process/mpc)

### 2.4 Field Development Tools (Good, Needs Enhancement)

**Current Features:**
- Cash flow engine with tax models (Norwegian, Generic)
- Production profile generation
- Portfolio optimization
- Sensitivity analysis
- Flow assurance screening
- Safety screening
- Economics estimation
- Regional cost factors
- **Reservoir coupling export** to Eclipse/E300 via VFP tables
- **Well performance modeling** with multiple IPR models (Vogel, Fetkovich, Backpressure, Table)

**Well Performance Capabilities:**
| Feature | Description | Status |
|---------|-------------|--------|
| **IPR Models** | Production Index, Vogel, Fetkovich, Backpressure, Table | ✓ Implemented |
| **Multi-Layer Wells** | Commingled production with individual layer contributions | ✓ Implemented |
| **VFP Table Export** | VFPPROD/VFPINJ for Eclipse coupling | ✓ Implemented |
| **Tubing Performance** | Integration with PipeBeggsAndBrills for VLP | ✓ Implemented |

**Code Location:** [src/main/java/neqsim/process/equipment/reservoir/WellFlow.java](../src/main/java/neqsim/process/equipment/reservoir/WellFlow.java)

**Reservoir Coupling:**
- VFP table generation for Eclipse 100/E300
- Separator efficiency curves export
- Compression curves export
- Network deliverability constraints
- Schedule keywords for time-varying constraints

**Code Location:** [src/main/java/neqsim/process/fielddevelopment/reservoir/ReservoirCouplingExporter.java](../src/main/java/neqsim/process/fielddevelopment/reservoir/ReservoirCouplingExporter.java)

**Limitations:**
- ⚠️ No internal decline curve analysis (use external reservoir models)
- ⚠️ No offshore logistics/marine operations

**Code Location:** [src/main/java/neqsim/process/fielddevelopment](src/main/java/neqsim/process/fielddevelopment)

---

## 3. WEAK FUNCTIONALITY (Areas Requiring Significant Development)

### 3.1 Offshore-Specific Operations (Needs Development)

**Missing/Limited Features:**
- ❌ Motion effects on separators (no roll/pitch modeling)
- ❌ FPSO-specific equipment limitations
- ❌ Mooring-related flow variations
- ❌ Offloading operations simulation
- ❌ Green water/wave impact on topside
- ❌ Floating production shutdown/startup sequences
- ✓ Riser slugging via SevereSlugAnalyser
- ❌ Hull thermal effects

**Partial Coverage:**
- ✓ Subsea equipment exists but limited operational scenarios
- ✓ Umbilical modeling basic
- ✓ Subsea boosting available

### 3.2 Rotating Equipment Operations (Good, With Enhanced Driver Support)

**Current State:**
- Good compressor performance charts with surge/stone wall curves
- Comprehensive compressor driver framework
- Basic pump modeling
- ESP pump with GVF degradation

**Driver Modeling (Implemented):**

| Driver Type | Class | Capabilities |
|-------------|-------|--------------|
| **Gas Turbine** | [GasTurbineDriver](../src/main/java/neqsim/process/equipment/compressor/driver/GasTurbineDriver.java) | Ambient derating (temp/altitude), part-load efficiency, fuel consumption |
| **Electric Motor** | [ElectricMotorDriver](../src/main/java/neqsim/process/equipment/compressor/driver/ElectricMotorDriver.java) | VFD speed control, efficiency curves, power factor |
| **Steam Turbine** | [SteamTurbineDriver](../src/main/java/neqsim/process/equipment/compressor/driver/SteamTurbineDriver.java) | Extraction/condensing, steam rate curves |

**Condition Monitoring (Implemented):**

| Feature | Class | Description |
|---------|-------|-------------|
| **Vibration Analysis** | [FlowInducedVibrationAnalyser](../src/main/java/neqsim/process/measurementdevice/FlowInducedVibrationAnalyser.java) | LOF/FRMS methods, support arrangement modeling |
| **Health Monitoring** | [ConditionBasedReliability](../src/main/java/neqsim/process/safety/risk/condition/ConditionBasedReliability.java) | Multi-indicator health index, RUL estimation |
| **Degradation Models** | `DegradationModel` enum | Linear, Exponential, Weibull, ML-based |

**Condition Indicators Supported:**
- Vibration (mm/s RMS)
- Temperature (bearing, casing)
- Pressure differentials
- Flow anomalies
- Current/power
- Oil analysis
- Acoustic emission
- Efficiency decline

**Minor Gaps:**
- ⚠️ No specific compressor washing effects model
- ⚠️ Seal gas modeling integrated but not standalone
- ⚠️ No gearbox modeling for multi-shaft arrangements

### 3.3 Utilities & Support Systems (Moderate Coverage)

**Implemented Features:**

| System | Class/Package | Capabilities | Status |
|--------|---------------|--------------|--------|
| **Flare Stack** | [FlareStack](../src/main/java/neqsim/process/equipment/flare/FlareStack.java) | Radiation modeling (Point Source, Chamberlain), tip backpressure, emissions | ★★★★☆ |
| **Flare** | [Flare](../src/main/java/neqsim/process/equipment/flare/Flare.java) | Basic flare equipment, mass balance | ★★★☆☆ |
| **Gas Turbine** | [GasTurbine](../src/main/java/neqsim/process/equipment/powergeneration/GasTurbine.java) | Power generation, fuel consumption, emissions | ★★★★☆ |
| **Fuel Cell** | [FuelCell](../src/main/java/neqsim/process/equipment/powergeneration/FuelCell.java) | Hydrogen/natural gas fuel cells | ★★★☆☆ |
| **Electrolyzer** | [Electrolyzer](../src/main/java/neqsim/process/equipment/powergeneration/Electrolyzer.java) | Water electrolysis for H2 production | ★★★☆☆ |
| **Solar/Wind** | `powergeneration` package | Basic renewable energy models | ★★☆☆☆ |

**Flare System Capabilities:**
- **Radiation Models:** Point source (q = χᵣQ/4πR²) and Chamberlain line-source
- **Emissions Tracking:** CO₂, H₂O, NOx, SO₂, THC, CO per component
- **Tip Design:** Backpressure calculation, tip velocity
- **Combustion:** Burning efficiency, radiant fraction
- **Air/Steam Assist:** Optional assisted flaring
- **Atmospheric Effects:** Wind tilt, transmissivity

**Code Location:** [src/main/java/neqsim/process/equipment/flare](../src/main/java/neqsim/process/equipment/flare)

**Partially Implemented:**
- ⚠️ Flare header hydraulics (use PipeBeggsAndBrills for piping)
- ⚠️ Fuel gas system (model via streams and heaters)
- ⚠️ Chemical injection (track via component mole fractions)

**Not Implemented:**
- ❌ Produced water treatment (oil-in-water modeling)
- ❌ HVAC/utility air systems
- ❌ Firewater system hydraulics
- ❌ Detailed power grid/load management

### 3.4 Operational Diagnostics & Monitoring (Needs Development)

**Missing Features:**
- ❌ No built-in digital twin framework (though API suitable)
- ❌ Limited condition monitoring algorithms
- ❌ No automated performance degradation detection
- ❌ No model-based soft sensors beyond basic
- ❌ Limited well test analysis
- ❌ No allocation system beyond basic well allocator

### 3.5 Dynamic Simulation (Good)

**Current State:**
- Transient vessel depressurization excellent
- **TwoFluidPipe transient multiphase simulation** with full conservation equations
- Basic transient separator modeling
- Transient compressor state modeling
- **Lagrangian slug tracking** through pipelines

**Minor Gaps:**
- ⚠️ Pressure-flow network solver for looped transient systems
- ⚠️ Controller tuning simulation could be enhanced
- ⚠️ Trip/ESD sequence simulation framework

---

## 4. IMPROVEMENT SUGGESTIONS

### 4.1 High Priority (Critical for O&G Design/Operations)

| Area | Improvement | Benefit | Status |
|------|-------------|---------|--------|
| **Erosion-Corrosion** | API RP 14E erosional velocity with sand | Pipeline/choke integrity | ✅ Implemented in ManifoldMechanicalDesignCalculator |
| **Pipeline Network** | Network topology solver with looped systems | Required for gathering systems | ✅ Implemented (LoopedPipeNetwork) |
| **Inhibitor Management** | Track MEG/TEG through system with losses | Flow assurance operations | ⚠️ Partial |
| **Digital Twin Interface** | REST API / OPC-UA connector layer | Real-time operations | ⚠️ Partial |

**Erosion-Corrosion Implementation:**

The `ManifoldMechanicalDesignCalculator` class implements API RP 14E erosional velocity calculation:

```java
// Calculate erosional velocity per API RP 14E
// Ve = C / sqrt(ρ_mix) where C = 100-150 typically
calculator.setErosionalCFactor(100.0);
calculator.setMixtureDensity(rhoMix);
double erosionalVelocity = calculator.calculateErosionalVelocity();
```

**Code Location:** [src/main/java/neqsim/process/mechanicaldesign/manifold/ManifoldMechanicalDesignCalculator.java](../src/main/java/neqsim/process/mechanicaldesign/manifold/ManifoldMechanicalDesignCalculator.java)

### 4.2 Medium Priority (Enhances Capability)

| Area | Improvement | Benefit |
|------|-------------|---------|
| **FPSO Operations** | Motion effects on separation, riser slugging | Floating production design |
| **Condition Monitoring** | Equipment health tracking, RUL estimation | Predictive maintenance |
| **Well Performance** | IPR curves, artificial lift optimization | Production optimization |
| **Scale Prediction** | CaCO₃, BaSO₄, etc. precipitation | Water handling |
| **Crude Compatibility** | Blending stability predictions | Operations planning |
| **Emissions Intensity** | CO₂/CH₄ per unit production tracking | ESG reporting |

### 4.3 Lower Priority (Nice-to-Have)

| Area | Improvement | Benefit |
|------|-------------|---------|
| **Offshore Logistics** | Supply vessel, helicopter scheduling | OPEX estimation |
| **Integrity Management** | Corrosion rate tracking, inspection planning | Asset integrity |
| **Training Simulator** | OTS-compatible output format | Operator training |
| **CO₂ Sequestration** | Injection well modeling | CCUS applications |

---

## 5. DETAILED FEATURE GAP ANALYSIS

### 5.1 Onshore Applications

| Application | NeqSim Capability | Gap |
|-------------|-------------------|-----|
| **Gas Processing Plant** | ★★★★☆ Very Good | Amine regeneration could be improved |
| **Oil Terminal** | ★★★★☆ Very Good | Tank mixing/blending limited |
| **Refinery Integration** | ★★★☆☆ Good | Reactor modeling limited |
| **Pipeline Network** | ★★★★☆ Very Good | Looped network solver implemented |
| **Compression Station** | ★★★★★ Excellent | Well covered |
| **Gas Gathering** | ★★★★☆ Very Good | Slug catcher sizing basic |

### 5.2 Offshore Applications

| Application | NeqSim Capability | Gap |
|-------------|-------------------|-----|
| **Fixed Platform** | ★★★★☆ Very Good | Minor gaps only |
| **FPSO** | ★★★☆☆ Good | Motion effects, offloading |
| **Subsea Production** | ★★★★☆ Very Good | TwoFluidPipe provides transient flow |
| **TLP/Spar/Semi** | ★★★☆☆ Good | Motion effects |
| **Subsea Tieback** | ★★★★☆ Very Good | Long-distance flow assurance |
| **Gas Export** | ★★★★☆ Very Good | Mostly complete |

---

## 6. COMPARISON WITH COMMERCIAL TOOLS

| Feature Area | NeqSim | HYSYS/UniSim | OLGA/LedaFlow | PIPESIM |
|--------------|--------|--------------|---------------|---------|
| **Thermodynamics** | ★★★★★ | ★★★★★ | ★★★☆☆ | ★★★★☆ |
| **Steady-State Process** | ★★★★☆ | ★★★★★ | ★★☆☆☆ | ★★★☆☆ |
| **Transient Multiphase** | ★★★★☆ | ★★★☆☆ | ★★★★★ | ★★★★★ |
| **Mechanical Design** | ★★★★☆ | ★★★☆☆ | ★★☆☆☆ | ★★★☆☆ |
| **Safety Analysis** | ★★★★☆ | ★★★☆☆ | ★★☆☆☆ | ★★☆☆☆ |
| **Cost Estimation** | ★★★★☆ | ★★★☆☆ | ★☆☆☆☆ | ★★☆☆☆ |
| **Open Source/API** | ★★★★★ | ★★☆☆☆ | ★★☆☆☆ | ★★☆☆☆ |
| **Python Integration** | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★★★☆☆ |

**NeqSim Competitive Advantages:**
1. Open source with full code access
2. Excellent thermodynamic foundation
3. Strong Python/Java API
4. Integrated mechanical design
5. Comprehensive safety framework
6. Active development community

---

## 7. RECOMMENDED DEVELOPMENT ROADMAP

### Phase 1: Critical Operations Features (✅ Largely Complete)
1. ✅ Pipeline network solver for looped systems (LoopedPipeNetwork)
2. ⚠️ Digital twin REST API layer (partial)
3. ❌ OPC-UA data connector
4. ⚠️ Enhanced TwoFluidPipe validation against field data
5. ⚠️ Slug catcher sizing based on SlugTracker statistics

### Phase 2: Enhanced Flow Assurance (In Progress)
1. ⚠️ MEG/TEG tracking system-wide
2. ❌ Scale prediction (at least CaCO₃)
3. ✅ Erosion velocity calculations (API RP 14E)
4. ⚠️ Pigging simulation basic
5. ⚠️ Sand transport modeling

### Phase 3: Offshore Operations (12-18 months)
1. ❌ FPSO motion effects
2. ✅ Riser slugging prediction (SevereSlugAnalyser)
3. ❌ Offloading simulation
4. ⚠️ Subsea processing enhancements
5. ❌ Marine operations integration

### Phase 4: Operational Excellence (12-18 months)
1. ✅ Advanced condition monitoring (ConditionBasedReliability)
2. ⚠️ Production optimization framework
3. ⚠️ Integrity management module
4. ❌ Training simulator export
5. ⚠️ ESG/emissions tracking enhancements

---

## 8. CONCLUSION

NeqSim provides a **strong foundation** for oil and gas installation design and operations with particular excellence in:
- Thermodynamic calculations
- Process simulation
- Mechanical design
- Safety systems
- **Looped pipeline network solving** (newly implemented)
- **Driver modeling** for rotating equipment
- **Condition-based reliability** monitoring

The remaining gaps are in:
- Offshore-specific operational scenarios (FPSO motion effects)
- Real-time operations integration (OPC connectors)
- MEG/TEG inhibitor tracking through full system
- Scale prediction

For **design applications**, NeqSim is highly capable and competitive with commercial tools. For **operations applications**, additional development in real-time integration would further enhance its value.

**Recommendation:** NeqSim is well-suited for conceptual and detailed design of oil and gas facilities, including transient multiphase flow analysis via TwoFluidPipe and looped pipeline networks via Hardy Cross solver. For operational support, it can serve as a calculation engine within a larger digital architecture.

---

## Appendix A: Package Structure Reference

```
neqsim/
├── thermo/                    # Thermodynamic core
│   ├── system/               # EoS implementations (50+)
│   ├── phase/                # Phase models
│   └── component/            # Component data
├── thermodynamicoperations/   # Flash & phase envelope
│   └── flashops/             # All flash types
├── process/                   # Process simulation
│   ├── equipment/            # Unit operations
│   │   ├── separator/
│   │   ├── compressor/
│   │   ├── heatexchanger/
│   │   ├── pipeline/
│   │   ├── valve/
│   │   ├── pump/
│   │   ├── distillation/
│   │   ├── subsea/
│   │   └── ...
│   ├── mechanicaldesign/     # Equipment sizing & costs
│   ├── safety/               # Safety systems & risk
│   ├── mpc/                  # Control integration
│   └── fielddevelopment/     # Project economics
├── standards/                 # Industry standards
│   └── gasquality/           # ISO, GERG, etc.
├── pvtsimulation/            # PVT experiments
│   └── flowassurance/        # Hydrate, wax, asphaltene
└── fluidmechanics/           # Pipe flow models
```

---

## Appendix B: Key Documentation Links

### Core Documentation
- [Modules Overview](modules.md)
- [Reference Manual Index](REFERENCE_MANUAL_INDEX.md)
- [Developer Setup](development/DEVELOPER_SETUP.md)

### Process & Equipment
- [Process Design Guide](process/process_design_guide.md)
- [Equipment Design Parameters](process/EQUIPMENT_DESIGN_PARAMETERS.md)
- [Capacity Constraint Framework](process/CAPACITY_CONSTRAINT_FRAMEWORK.md)
- [TwoFluidPipe Transient Model](process/TWOFLUIDPIPE_MODEL.md)
- [Pipeline Network Solver Enhancement](process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md)

### Mechanical Design
- [Mechanical Design Overview](process/mechanical_design.md)
- [Design Standards](process/mechanical_design_standards.md)
- [Pipeline Mechanical Design](process/pipeline_mechanical_design.md)
- [Riser Mechanical Design](process/riser_mechanical_design.md)
- [Topside Piping Design](process/topside_piping_design.md)
- [Compressor Mechanical Design](process/CompressorMechanicalDesign.md)
- [Valve Mechanical Design](process/ValveMechanicalDesign.md)

### Safety & Risk
- [Safety Systems Overview](safety/README.md)
- [ESD & Blowdown System](safety/ESD_BLOWDOWN_SYSTEM.md)
- [HIPPS Implementation](safety/hipps_implementation.md)
- [Integrated Safety Systems](safety/INTEGRATED_SAFETY_SYSTEMS.md)
- [Layered Safety Architecture](safety/layered_safety_architecture.md)

### Risk Analysis
- [Risk Framework Overview](risk/README.md)
- [Monte Carlo Simulation](risk/monte-carlo.md)
- [Bow-Tie Analysis](risk/bowtie-analysis.md)
- [Condition-Based Reliability](risk/condition-based.md)
- [SIS Integration](risk/sis-integration.md)
- [Dynamic Risk Simulation](risk/dynamic-simulation.md)
- [Reliability Data Guide](risk/RELIABILITY_DATA_GUIDE.md)

### Cost Estimation
- [Cost Estimation Framework](process/COST_ESTIMATION_FRAMEWORK.md)
- [Cost Estimation API Reference](process/COST_ESTIMATION_API_REFERENCE.md)

### Subsea Systems
- [SURF & Subsea Equipment](process/SURF_SUBSEA_EQUIPMENT.md)

### Examples
- [Example Index](examples/index.md)
- [Looped Pipeline Network Example](examples/LoopedPipelineNetworkExample.ipynb)
- [Production Optimizer Tutorial](examples/ProductionOptimizer_Tutorial.ipynb)
- [Network Solver Tutorial](examples/NetworkSolverTutorial.ipynb)
- [ESP Pump Tutorial](examples/ESP_Pump_Tutorial.ipynb)

---

## Appendix C: New Functionality Reference

### Looped Pipeline Network Solver

**Classes:**
- [LoopedPipeNetwork](../src/main/java/neqsim/process/equipment/network/LoopedPipeNetwork.java) - Main network class with Hardy Cross solver
- [LoopDetector](../src/main/java/neqsim/process/equipment/network/LoopDetector.java) - DFS spanning tree loop detection
- [NetworkLoop](../src/main/java/neqsim/process/equipment/network/NetworkLoop.java) - Loop representation

**Key Features:**
- Automatic loop detection using graph theory
- Hardy Cross iterative solver
- Support for ring mains and parallel pipelines
- JSON output for integration

**Example:**
```java
LoopedPipeNetwork network = new LoopedPipeNetwork("Distribution Ring");
network.setFluidTemplate(gas);
network.addSourceNode("supply", 50.0, 2000.0);
network.addJunctionNode("A");
network.addJunctionNode("B");
network.addSinkNode("customer", 2000.0);
network.addPipe("supply", "A", "inlet", 1000.0, 0.3);
network.addPipe("A", "B", "ring1", 500.0, 0.2);
network.addPipe("B", "supply", "ring2", 500.0, 0.2);  // Creates loop
network.addPipe("A", "customer", "outlet", 200.0, 0.15);
network.run();
```

### Compressor Driver Framework

**Classes:**
- [GasTurbineDriver](../src/main/java/neqsim/process/equipment/compressor/driver/GasTurbineDriver.java)
- [ElectricMotorDriver](../src/main/java/neqsim/process/equipment/compressor/driver/ElectricMotorDriver.java)
- [SteamTurbineDriver](../src/main/java/neqsim/process/equipment/compressor/driver/SteamTurbineDriver.java)

### Condition-Based Reliability

**Class:** [ConditionBasedReliability](../src/main/java/neqsim/process/safety/risk/condition/ConditionBasedReliability.java)

**Indicator Types:** VIBRATION, TEMPERATURE, PRESSURE, FLOW, CURRENT, WEAR, CORROSION, EFFICIENCY, ACOUSTIC, OIL_ANALYSIS

### Flow-Induced Vibration Analysis

**Class:** [FlowInducedVibrationAnalyser](../src/main/java/neqsim/process/measurementdevice/FlowInducedVibrationAnalyser.java)

**Methods:** LOF (Likelihood of Failure), FRMS (Fatigue Reduced Modulus of Safety)

### Reservoir Coupling

**Class:** [ReservoirCouplingExporter](../src/main/java/neqsim/process/fielddevelopment/reservoir/ReservoirCouplingExporter.java)

**Export Formats:** Eclipse 100, E300 Compositional, Intersect, CSV

### Well Performance (IPR)

**Class:** [WellFlow](../src/main/java/neqsim/process/equipment/reservoir/WellFlow.java)

**IPR Models:** PRODUCTION_INDEX, VOGEL, FETKOVICH, BACKPRESSURE, TABLE

---

*Document prepared as part of NeqSim capability assessment for oil and gas applications.*
