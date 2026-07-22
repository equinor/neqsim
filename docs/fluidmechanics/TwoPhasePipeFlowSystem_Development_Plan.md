---
title: Two-Phase Mass Transfer Pipeline Development Plan
description: This document outlines the development plan for enhancing the `TwoPhasePipeFlowSystem` to become a general-purpose two-phase mass and heat transfer pipeline simulation tool. The implementation is base...
---

# Two-Phase Mass Transfer Pipeline Development Plan

## Overview

This document outlines the development plan for enhancing the `TwoPhasePipeFlowSystem` to become a general-purpose two-phase mass and heat transfer pipeline simulation tool. The implementation is based on the non-equilibrium thermodynamics approach described in Solbraa (2002) and uses the Krishna-Standart film model for interphase mass transfer.

## Current State

The `TwoPhasePipeFlowSystem` currently supports:
- Multiple flow patterns: stratified, annular, slug, droplet/mist, bubble
- Steady-state and transient solving (TDMA solver)
- Basic non-equilibrium mass transfer using Krishna-Standart film model
- Basic heat transfer to surroundings
- Integration with NeqSim thermodynamic models (SRK, CPA, etc.)

## Development Tasks

### 1. Mass Transfer Models

#### 1.1 Add Mass Transfer Model Selection
**Priority: High**

Allow users to select between different mass transfer models:

| Model | Description | Best For |
|-------|-------------|----------|
| Krishna-Standart Film | Multi-component diffusion with thermodynamic correction | General purpose, current default |
| Penetration Theory | Time-dependent diffusion into semi-infinite medium | Short contact times |
| Surface Renewal Theory | Statistical distribution of surface ages | Turbulent interfaces |

**API Design:**
```java
public enum MassTransferModel {
    KRISHNA_STANDART_FILM,
    PENETRATION_THEORY,
    SURFACE_RENEWAL
}

pipe.setMassTransferModel(MassTransferModel.KRISHNA_STANDART_FILM);
```

#### 1.2 Implement Mass Transfer Coefficient Correlations
**Priority: High**

Add flow pattern-specific Sherwood number correlations:

| Flow Pattern | Correlation | Reference |
|--------------|-------------|-----------|
| Stratified | Sh = f(Re, Sc, geometry) | Solbraa (2002) |
| Annular | Sh = f(Re_film, Sc, wave amplitude) | Hewitt & Hall-Taylor |
| Slug | Sh_bubble + Sh_slug weighted | Fernandes et al. |
| Bubble | Sh = 2 + 0.6 Re^0.5 Sc^0.33 | Ranz-Marshall |
| Droplet | Sh = 2 + 0.6 Re^0.5 Sc^0.33 | Ranz-Marshall |

#### 1.3 Add Component Mass Transfer Tracking
**Priority: Medium**

Track mass transfer for each component along the pipe:

```java
double[] methaneProfile = pipe.getMassTransferProfile("methane");
double waterMassBalance = pipe.getComponentMassBalance("water");
```

---

### 2. Interphase Area Models

#### 2.1 Implement Interfacial Area Models
**Priority: High**

The interfacial area per unit volume (a) is critical for mass transfer calculations:
$$\dot{n}_i = k_L \cdot a \cdot \Delta C_i$$

| Flow Pattern | Interfacial Area Model | Key Parameters |
|--------------|----------------------|----------------|
| **Stratified** | Flat interface: $a = \frac{S_i}{A}$ where $S_i$ = interface chord length | Liquid holdup, pipe diameter |
| **Annular** | Film interface: $a = \frac{4}{D} \cdot \frac{1}{1-\sqrt{1-\alpha_L}}$ | Film thickness, entrainment |
| **Slug** | $a = a_{Taylor} \cdot f_{bubble} + a_{slug} \cdot (1-f_{bubble})$ | Slug frequency, bubble length |
| **Bubble** | $a = \frac{6\alpha_G}{d_{32}}$ (Sauter mean diameter) | Bubble size distribution |
| **Droplet** | $a = \frac{6\alpha_L}{d_{32}}$ from Weber number | Droplet size, We number |

**API Design:**
```java
public enum InterfacialAreaModel {
    GEOMETRIC,           // Based on flow geometry
    EMPIRICAL_CORRELATION,  // Literature correlations
    USER_DEFINED         // Custom model
}

pipe.setInterfacialAreaModel(InterfacialAreaModel.GEOMETRIC);
```

**Implementation Details:**

For **bubble flow**, use Hinze theory for maximum stable bubble size:
$$d_{max} = 0.725 \left(\frac{\sigma}{\rho_L}\right)^{0.6} \epsilon^{-0.4}$$

For **droplet flow**, use critical Weber number:
$$d_{max} = \frac{We_{crit} \cdot \sigma}{\rho_G \cdot u_G^2}$$

---

### 3. Heat Transfer Models

#### 3.1 Implement Wall Heat Transfer Models
**Priority: High**

Support different thermal boundary conditions:

| Boundary Condition | Description | Use Case |
|-------------------|-------------|----------|
| Constant Wall Temperature | $T_{wall} = T_{const}$ | Isothermal pipes |
| Constant Heat Flux | $q'' = q''_{const}$ | Electric heating |
| Convective Boundary | $q'' = U_{overall}(T_{ambient} - T_{fluid})$ | Subsea pipelines |

**API Design:**
```java
public enum WallHeatTransferModel {
    CONSTANT_WALL_TEMPERATURE,
    CONSTANT_HEAT_FLUX,
    CONVECTIVE_BOUNDARY,
    ADIABATIC
}

pipe.setWallHeatTransferModel(WallHeatTransferModel.CONVECTIVE_BOUNDARY);
pipe.setOverallHeatTransferCoefficient(10.0); // W/(m²·K)
```

#### 3.2 Add Heat Transfer Coefficient Correlations
**Priority: Medium**

Implement flow pattern-specific Nusselt number correlations:

| Flow Pattern | Correlation | Notes |
|--------------|-------------|-------|
| Stratified | Separate gas/liquid Nu | Geometry-dependent |
| Annular | Nu = f(Re_film, Pr, roughness) | Film heat transfer |
| Slug | Weighted Nu_bubble + Nu_slug | Time-averaged |
| Bubble | Shah correlation | Enhanced mixing |
| Droplet | Dittus-Boelter for gas + droplet contribution | Mist flow |

#### 3.3 Implement Energy Balance with Enthalpy
**Priority: Medium**

Use rigorous enthalpy-based energy balance:

$$\frac{\partial(\rho H)}{\partial t} + \nabla \cdot (\rho \mathbf{u} H) = -\nabla \cdot \mathbf{q} + \dot{Q}_{wall} + \dot{Q}_{interphase}$$

Include:
- Latent heat from phase change (evaporation/condensation)
- Joule-Thomson effects
- Kinetic energy changes
- Pressure-volume work

---

### 4. Flow Pattern Detection

#### 4.1 Add Automatic Flow Pattern Detection
**Priority: Medium**

Implement flow pattern transition criteria:

| Method | Description | Applicability |
|--------|-------------|---------------|
| **Baker Chart** | Empirical map based on G_L, G_G | Horizontal pipes |
| **Taitel-Dukler** | Mechanistic model | Horizontal/inclined |
| **Barnea** | Extended Taitel-Dukler | All inclinations |

**Transition Criteria:**
- Stratified → Slug: Kelvin-Helmholtz instability
- Slug → Annular: Liquid film stability
- Bubble → Slug: Void fraction > 0.25
- Annular → Mist: Entrainment dominance

**API Design:**
```java
pipe.enableAutomaticFlowPatternDetection(true);
pipe.setFlowPatternModel(FlowPatternModel.TAITEL_DUKLER);
```

---

### 5. Output and Results

#### 5.1 Create Profile Output Methods
**Priority: High**

Add convenient methods for extracting simulation results:

```java
// Get profiles along pipe
double[] temperatures = pipe.getTemperatureProfile();
double[] pressures = pipe.getPressureProfile(); // [bar]
double[] positions = pipe.getPositionProfile();

// Get composition profiles
double[][] gasComposition = pipe.getGasCompositionProfile();
double[][] liquidComposition = pipe.getLiquidCompositionProfile();

// Get flow properties
double[] gasVelocities = pipe.getVelocityProfile(0); // Gas phase
double[] liquidVelocities = pipe.getVelocityProfile(1); // Liquid phase
double[] voidFraction = pipe.getVoidFractionProfile();

// Export to CSV/JSON
pipe.exportResults("simulation_results.csv");
```

---

### 6. Advanced Features

#### 6.1 Add Enhancement Factors for Reactive Systems
**Priority: Low**

Support chemical enhancement for reactive mass transfer:

$$N_A = E \cdot k_L \cdot (C_{A,i} - C_{A,bulk})$$

| System | Enhancement Factor Model |
|--------|-------------------------|
| CO₂ + Amine | Danckwerts surface renewal |
| H₂S removal | Instantaneous reaction |
| SO₂ absorption | Film theory with reaction |

#### 6.2 Pipe Insulation and Wall Heat Transfer Model
**Priority: Low** ✅ **Already Implemented**

NeqSim already has a comprehensive pipe wall and insulation infrastructure in the `geometrydefinitions.internalgeometry.wall` and `geometrydefinitions.surrounding` packages.

##### Existing Infrastructure

**MaterialLayer.java** - Single layer with thermal properties:
```java
MaterialLayer layer = new MaterialLayer(PipeMaterial.POLYURETHANE_FOAM, 0.05); // 50mm insulation
double k = layer.getThermalConductivity(); // W/(m·K)
double R = layer.getThermalResistance(innerRadius, outerRadius); // For cylindrical geometry
```

**PipeMaterial.java** - Enum with 30+ predefined materials:
- Steels: CARBON_STEEL, STAINLESS_STEEL_316, DUPLEX_STEEL
- Insulations: POLYURETHANE_FOAM, MINERAL_WOOL, AEROGEL
- Coatings: POLYPROPYLENE, POLYETHYLENE, FUSION_BONDED_EPOXY
- Soils: DRY_SAND, WET_SAND, CLAY, PERMAFROST

**PipeWall.java** - Multi-layer cylindrical heat transfer:
```java
PipeWall wall = new PipeWall(innerDiameter);
wall.addLayer(new MaterialLayer(PipeMaterial.CARBON_STEEL, 0.01));      // 10mm steel
wall.addLayer(new MaterialLayer(PipeMaterial.POLYURETHANE_FOAM, 0.05)); // 50mm insulation
wall.addLayer(new MaterialLayer(PipeMaterial.POLYPROPYLENE, 0.003));    // 3mm coating
double U_inner = wall.getOverallHeatTransferCoefficient(); // W/(m²·K) based on inner surface
```

**PipeSurroundingEnvironment.java** - Factory methods for external conditions:
```java
// Subsea pipeline
PipeSurroundingEnvironment env = PipeSurroundingEnvironment.subseaPipe(seawaterTemp, depth);
double h_ext = env.getExternalHeatTransferCoefficient(); // ~500 W/(m²·K) for seawater

// Buried onshore
PipeSurroundingEnvironment env = PipeSurroundingEnvironment.buriedPipe(soilTemp, burialDepth, soilType);

// Exposed to air
PipeSurroundingEnvironment env = PipeSurroundingEnvironment.exposedToAir(airTemp, windSpeed);
```

##### Integration with TwoPhasePipeFlowSystem

The FlowSystem base class already supports:
```java
// Set wall heat transfer coefficients per leg
pipe.setLegWallHeatTransferCoefficients(new double[] {50.0, 50.0}); // U values
pipe.setLegOuterHeatTransferCoefficients(new double[] {500.0, 500.0}); // h_ext values
```

For advanced wall modeling, use PipeWall to calculate overall U and pass to the flow system.

---

### 7. API Improvements

#### 7.1 Create Simplified Builder API
**Priority: Medium**

Fluent builder pattern for easy setup:

```java
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
    .withFluid(thermoSystem)
    .withDiameter(0.1, "m")
    .withLength(1000, "m")
    .withNodes(100)
    .withFlowPattern("stratified")
    .withWallTemperature(278, "K")
    .enableNonEquilibriumMassTransfer()
    .enableNonEquilibriumHeatTransfer()
    .build();

pipe.solve();
```

---

### 8. Testing and Validation

#### 8.1 Add Comprehensive Test Suite
**Priority: High**

| Test Category | Validation Data |
|---------------|-----------------|
| Mass transfer coefficients | Solbraa (2002) experimental data |
| Interfacial areas | Azzopardi correlations |
| Heat transfer | Gnielinski, Shah correlations |
| Pressure drop | Lockhart-Martinelli, Friedel |
| Flow pattern transitions | Taitel-Dukler maps |

#### 8.2 Write Documentation and Examples
**Priority: Medium**

Create Jupyter notebook examples:
1. **TEG Dehydration** - Water removal from natural gas
2. **CO₂ Absorption** - Amine scrubbing in pipelines
3. **Pipeline Cooling** - Subsea pipeline temperature profiles
4. **Hydrate Prevention** - MEG injection effectiveness
5. **Condensate Dropout** - Retrograde condensation in pipelines

---

## Implementation Priority

### Phase 1: Core Functionality (High Priority)
1. ✅ Basic non-equilibrium mass/heat transfer (completed)
2. ✅ Interphase area models for all flow patterns (completed - InterfacialAreaCalculator with geometric models for stratified, annular, slug, bubble, droplet, churn)
3. ✅ Mass transfer coefficient correlations (completed - MassTransferCoefficientCalculator with Dittus-Boelter, Ranz-Marshall)
4. ✅ Profile output methods (completed - added getEnthalpyProfile, getTotalEnthalpyProfile, getHeatCapacityProfile, getFlowPatternProfile, getFlowPatternNameProfile)
5. ✅ Comprehensive test suite (completed - 84+ tests for flow pattern detection, interfacial area, mass transfer, builder)

### Phase 2: Enhanced Models (Medium Priority)
6. ✅ Wall heat transfer models (completed - WallHeatTransferModel enum)
7. ✅ Heat transfer coefficient correlations (completed - basic Dittus-Boelter for internal heat transfer)
8. ✅ Automatic flow pattern detection (completed - FlowPatternDetector with Taitel-Dukler, Baker, Barnea, Beggs-Brill)
9. ✅ Builder API (completed - TwoPhasePipeFlowSystemBuilder)
10. ✅ Pipe wall/insulation model (already implemented in existing PipeWall, MaterialLayer, PipeMaterial, PipeSurroundingEnvironment classes)
11. ✅ Energy balance improvements (completed - added Joule-Thomson effect, latent heat from phase change, proper wall heat transfer coefficients)

### Phase 3: Advanced Features (Low Priority)
12. [ ] Enhancement factors for reactive systems
13. [ ] Mass transfer model selection (Krishna-Standart, Penetration Theory, Surface Renewal)
14. ✅ Documentation and examples (completed - comprehensive documentation in docs/fluidmechanics/)

### Known Limitations and Future Work

Some advanced test scenarios are currently disabled pending solver optimization:

| Test | Status | Issue |
|------|--------|-------|
| `testCompleteLiquidEvaporationIn1kmPipe` | Disabled | Solver timeout - needs performance optimization |
| `testTransientWaterDryingInGasPipeline` | Disabled | Solver timeout - needs performance optimization |
| `testSubseaGasOilPipelineWithElevationProfile` | Disabled | Temperature calculation needs improvement |
| `testGasWithCondensationAlongPipeline` | Disabled | Phase transition solver needs optimization |

These tests represent advanced use cases that require further solver development to handle:
- Complete phase disappearance (evaporation/condensation to single phase)
- Long transient simulations with changing inlet conditions
- Complex temperature profiles with large gradients

The steady-state solver (type 2) calculates mass transfer fluxes correctly at each node, but accumulated composition changes downstream may require more iterations or transient simulation.

---

## References

1. Solbraa, E. (2002). "Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing." PhD Thesis, NTNU.
2. Krishna, R., & Standart, G. L. (1976). "A multicomponent film model incorporating a general matrix method of solution to the Maxwell-Stefan equations." AIChE Journal, 22(2), 383-389.
3. Taitel, Y., & Dukler, A. E. (1976). "A model for predicting flow regime transitions in horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.
4. Hewitt, G. F., & Hall-Taylor, N. S. (1970). "Annular Two-Phase Flow." Pergamon Press.
5. Barnea, D. (1987). "A unified model for predicting flow-pattern transitions for the whole range of pipe inclinations." International Journal of Multiphase Flow, 13(1), 1-12.

---

## File Locations

| Component | File Path |
|-----------|-----------|
| Main class | `src/main/java/neqsim/fluidmechanics/flowsystem/twophaseflowsystem/twophasepipeflowsystem/TwoPhasePipeFlowSystem.java` |
| Builder | `src/main/java/neqsim/fluidmechanics/flowsystem/twophaseflowsystem/twophasepipeflowsystem/TwoPhasePipeFlowSystemBuilder.java` |
| Solver | `src/main/java/neqsim/fluidmechanics/flowsolver/twophaseflowsolver/twophasepipeflowsolver/TwoPhaseFixedStaggeredGridSolver.java` |
| Flow nodes | `src/main/java/neqsim/fluidmechanics/flownode/twophasenode/twophasepipeflownode/` |
| Flow pattern enum | `src/main/java/neqsim/fluidmechanics/flownode/FlowPattern.java` |
| Flow pattern model | `src/main/java/neqsim/fluidmechanics/flownode/FlowPatternModel.java` |
| Flow pattern detector | `src/main/java/neqsim/fluidmechanics/flownode/FlowPatternDetector.java` |
| Wall heat transfer model | `src/main/java/neqsim/fluidmechanics/flownode/WallHeatTransferModel.java` |
| Interfacial area model | `src/main/java/neqsim/fluidmechanics/flownode/InterfacialAreaModel.java` |
| Interfacial area calculator | `src/main/java/neqsim/fluidmechanics/flownode/InterfacialAreaCalculator.java` |
| Mass transfer calculator | `src/main/java/neqsim/fluidmechanics/flownode/MassTransferCoefficientCalculator.java` |
| Fluid boundary | `src/main/java/neqsim/fluidmechanics/flownode/fluidboundary/` |
| Tests | `src/test/java/neqsim/fluidmechanics/flowsystem/twophaseflowsystem/twophasepipeflowsystem/` |
| Tests (flow node) | `src/test/java/neqsim/fluidmechanics/flownode/` |
