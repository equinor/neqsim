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
double[] pressures = pipe.getPressureProfile();
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

#### 6.2 Implement Pipe Insulation Model
**Priority: Low**

Multi-layer thermal resistance:

$$R_{total} = R_{conv,in} + \sum_i R_{cond,i} + R_{conv,out}$$

```java
pipe.addInsulationLayer("polyurethane", 0.05, 0.025); // material, thickness, k
pipe.addInsulationLayer("concrete", 0.10, 1.0);
pipe.setExternalConvectionCoefficient(500); // W/(m²·K) for seawater
```

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
2. [ ] Interphase area models for all flow patterns
3. [ ] Mass transfer coefficient correlations
4. [ ] Profile output methods
5. [ ] Comprehensive test suite

### Phase 2: Enhanced Models (Medium Priority)
6. [ ] Wall heat transfer models
7. [ ] Heat transfer coefficient correlations
8. [ ] Automatic flow pattern detection
9. [ ] Builder API
10. [ ] Energy balance improvements

### Phase 3: Advanced Features (Low Priority)
11. [ ] Enhancement factors for reactive systems
12. [ ] Pipe insulation model
13. [ ] Mass transfer model selection
14. [ ] Documentation and examples

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
| Solver | `src/main/java/neqsim/fluidmechanics/flowsolver/twophaseflowsolver/twophasepipeflowsolver/TwoPhaseFixedStaggeredGridSolver.java` |
| Flow nodes | `src/main/java/neqsim/fluidmechanics/flownode/twophasenode/twophasepipeflownode/` |
| Fluid boundary | `src/main/java/neqsim/fluidmechanics/flownode/fluidboundary/` |
| Tests | `src/test/java/neqsim/fluidmechanics/flowsystem/twophaseflowsystem/twophasepipeflowsystem/` |
