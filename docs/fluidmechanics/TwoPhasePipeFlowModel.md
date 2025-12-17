# Two-Phase Pipe Flow Model with Heat and Mass Transfer

## Overview

The NeqSim two-phase pipe flow model implements a **non-equilibrium thermodynamic approach** for simulating gas-liquid flow in pipes. This document describes the theoretical foundation, numerical methods, and practical usage in NeqSim.

**Key Features:**
- Non-equilibrium mass and heat transfer between phases
- Multiple flow pattern support (stratified, annular, slug, bubble, droplet)
- Inclined and vertical pipe support
- Wall heat transfer with multiple boundary conditions
- Steady-state and transient simulation capabilities

---

## 1. Governing Equations

### 1.1 Mass Conservation

For each phase $k$ (gas $G$ or liquid $L$):

$$\frac{\partial}{\partial t}(\alpha_k \rho_k) + \frac{\partial}{\partial z}(\alpha_k \rho_k u_k) = \Gamma_k$$

| Symbol | Description | Units |
|--------|-------------|-------|
| $\alpha_k$ | Volume fraction of phase $k$ | [-] |
| $\rho_k$ | Density of phase $k$ | [kg/m³] |
| $u_k$ | Velocity of phase $k$ | [m/s] |
| $\Gamma_k$ | Mass transfer rate to phase $k$ | [kg/(m³·s)] |
| $z$ | Axial position | [m] |

**Constraint:** $\alpha_G + \alpha_L = 1$

### 1.2 Momentum Conservation

$$\frac{\partial}{\partial t}(\alpha_k \rho_k u_k) + \frac{\partial}{\partial z}(\alpha_k \rho_k u_k^2) = -\alpha_k \frac{\partial P}{\partial z} - \tau_{wk}\frac{S_k}{A} \mp \tau_i\frac{S_i}{A} - \alpha_k \rho_k g \sin\theta$$

| Symbol | Description | Units |
|--------|-------------|-------|
| $P$ | Pressure | [Pa] |
| $\tau_{wk}$ | Wall shear stress for phase $k$ | [Pa] |
| $\tau_i$ | Interfacial shear stress | [Pa] |
| $S_k$ | Wetted perimeter of phase $k$ | [m] |
| $S_i$ | Interfacial perimeter | [m] |
| $A$ | Pipe cross-sectional area | [m²] |
| $g$ | Gravitational acceleration | [m/s²] |
| $\theta$ | Pipe inclination angle | [rad] |

### 1.3 Energy Conservation

$$\frac{\partial}{\partial t}(\alpha_k \rho_k H_k) + \frac{\partial}{\partial z}(\alpha_k \rho_k u_k H_k) = q_{ik} + q_{wk} + \Gamma_k H_k^{int}$$

| Symbol | Description | Units |
|--------|-------------|-------|
| $H_k$ | Specific enthalpy of phase $k$ | [J/kg] |
| $q_{ik}$ | Interphase heat flux to phase $k$ | [W/m³] |
| $q_{wk}$ | Wall heat flux to phase $k$ | [W/m³] |
| $H_k^{int}$ | Interface enthalpy | [J/kg] |

---

## 2. Heat Transfer Model

### 2.1 Interphase Heat Transfer

Heat transfer between gas and liquid phases at the interface:

$$q_{GL} = h_{GL} \cdot a \cdot (T_G - T_L)$$

Where:
- $h_{GL}$ = overall interphase heat transfer coefficient [W/(m²·K)]
- $a$ = specific interfacial area [m²/m³]
- $T_G, T_L$ = gas and liquid temperatures [K]

### 2.2 Heat Transfer Coefficient Correlations

#### Dittus-Boelter (Turbulent Flow, Re > 10,000)

$$Nu = 0.023 \cdot Re^{0.8} \cdot Pr^n$$

Where $n = 0.4$ for heating, $n = 0.3$ for cooling.

$$h = \frac{Nu \cdot k}{D_h}$$

#### Gnielinski (Transitional Flow, 2300 < Re < 10,000)

$$Nu = \frac{(f/8)(Re - 1000)Pr}{1 + 12.7\sqrt{f/8}(Pr^{2/3} - 1)}$$

#### Laminar Flow (Re < 2300)

| Boundary Condition | Nusselt Number |
|-------------------|----------------|
| Constant wall temperature | $Nu = 3.66$ |
| Constant heat flux | $Nu = 4.36$ |

### 2.3 Wall Heat Transfer Models

| Model | Equation | NeqSim Setting |
|-------|----------|----------------|
| Adiabatic | $q_w = 0$ | `WallHeatTransferModel.ADIABATIC` |
| Constant Wall Temp | $q_w = h_w(T_w - T_{fluid})$ | `WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE` |
| Constant Heat Flux | $q_w = q_0$ | `WallHeatTransferModel.CONSTANT_HEAT_FLUX` |
| Convective Boundary | $q_w = U(T_{ambient} - T_{fluid})$ | `WallHeatTransferModel.CONVECTIVE_BOUNDARY` |

### 2.4 Overall Heat Transfer Coefficient

For a pipe with wall and insulation:

$$\frac{1}{U} = \frac{1}{h_{inner}} + \frac{r_i \ln(r_o/r_i)}{k_{wall}} + \frac{r_i \ln(r_{ins}/r_o)}{k_{ins}} + \frac{r_i}{r_{ins} \cdot h_{outer}}$$

---

## 3. Mass Transfer Model

### 3.1 Krishna-Standart Film Theory

The model uses the Krishna-Standart film theory for multicomponent mass transfer:

$$N_i = k_{i,L} \cdot a \cdot (x_i^{int} - x_i^{bulk}) = k_{i,G} \cdot a \cdot (y_i^{bulk} - y_i^{int})$$

| Symbol | Description | Units |
|--------|-------------|-------|
| $N_i$ | Molar flux of component $i$ | [mol/(m²·s)] |
| $k_{i,L}, k_{i,G}$ | Mass transfer coefficients | [m/s] |
| $a$ | Specific interfacial area | [m²/m³] |
| $x_i, y_i$ | Mole fractions in liquid/gas | [-] |

### 3.2 Chilton-Colburn Analogy

Mass transfer coefficients are related to heat transfer via:

$$Sh = Nu \cdot \left(\frac{Sc}{Pr}\right)^{1/3}$$

Where:
- $Sh = k \cdot D_h / D_{AB}$ (Sherwood number)
- $Sc = \mu / (\rho \cdot D_{AB})$ (Schmidt number)
- $D_{AB}$ = binary diffusion coefficient [m²/s]

### 3.3 Condensation/Evaporation Rate

$$\Gamma = \sum_{i=1}^{n} M_i \cdot N_i \cdot a$$

Interface equilibrium: $y_i^{int} = K_i(T^{int}, P) \cdot x_i^{int}$

---

## 4. Pressure Drop Calculation

### 4.1 Total Pressure Gradient

$$-\frac{dP}{dz} = \left(-\frac{dP}{dz}\right)_{friction} + \left(-\frac{dP}{dz}\right)_{gravity} + \left(-\frac{dP}{dz}\right)_{acceleration}$$

### 4.2 Lockhart-Martinelli Correlation

$$\left(-\frac{dP}{dz}\right)_{friction} = \phi_L^2 \cdot \left(-\frac{dP}{dz}\right)_{L,alone}$$

Two-phase multiplier:
$$\phi_L^2 = 1 + \frac{C}{X} + \frac{1}{X^2}$$

Lockhart-Martinelli parameter:
$$X = \sqrt{\frac{(dP/dz)_L}{(dP/dz)_G}}$$

| Gas Flow | Liquid Flow | C Value |
|----------|-------------|---------|
| Turbulent | Turbulent | 20 |
| Laminar | Turbulent | 12 |
| Turbulent | Laminar | 10 |
| Laminar | Laminar | 5 |

### 4.3 Gravitational Pressure Drop

$$\left(-\frac{dP}{dz}\right)_{gravity} = \rho_m \cdot g \cdot \sin\theta$$

Mixture density: $\rho_m = \alpha_G \rho_G + (1-\alpha_G) \rho_L$

### 4.4 Acceleration Pressure Drop

$$\left(-\frac{dP}{dz}\right)_{acc} = G^2 \frac{d}{dz}\left[\frac{x^2}{\rho_G \alpha_G} + \frac{(1-x)^2}{\rho_L \alpha_L}\right]$$

---

## 5. Numerical Discretization

### 5.1 Staggered Grid

The solver uses a staggered grid where:
- **Scalar variables** (P, T, ρ, α) are stored at cell centers
- **Vector variables** (velocities) are stored at cell faces

```
    |----[i-1]----|-----[i]-----|----[i+1]----|
    |      ●      |      ●      |      ●      |   ← Scalars (P, T, ρ, α)
    |             ↑             ↑             |   ← Velocities (u_G, u_L)
         face i-½      face i+½
```

### 5.2 Finite Volume Method

General conservation equation:
$$\frac{\partial \phi}{\partial t} + \frac{\partial (u\phi)}{\partial z} = S_\phi$$

Discretized form:
$$\frac{\phi_i^{n+1} - \phi_i^n}{\Delta t} + \frac{(u\phi)_{i+½} - (u\phi)_{i-½}}{\Delta z} = S_{\phi,i}$$

### 5.3 TDMA Solver

The tri-diagonal system:
$$a_i \phi_{i-1} + b_i \phi_i + c_i \phi_{i+1} = d_i$$

**Forward Sweep:**
$$c'_1 = \frac{c_1}{b_1}, \quad d'_1 = \frac{d_1}{b_1}$$
$$c'_i = \frac{c_i}{b_i - a_i c'_{i-1}}, \quad d'_i = \frac{d_i - a_i d'_{i-1}}{b_i - a_i c'_{i-1}}$$

**Back Substitution:**
$$\phi_n = d'_n, \quad \phi_i = d'_i - c'_i \phi_{i+1}$$

### 5.4 Iterative Solution Procedure

```
1. Initialize: Set initial P, T, u_G, u_L, α profiles
2. Solve Momentum: Update velocities from pressure gradient
3. Solve Pressure: Apply pressure correction
4. Solve Mass: Update phase fractions from continuity
5. Solve Energy: Update temperatures from energy equation
6. Update Properties: Recalculate ρ, μ, k, h from EOS
7. Check Convergence: If ||residual|| < tolerance, stop; else goto 2
```

### 5.5 Boundary Conditions

| Location | Variable | Condition |
|----------|----------|-----------|
| Inlet (z=0) | P, T, u, α | Dirichlet (specified) |
| Outlet (z=L) | P | Dirichlet or extrapolated |
| Outlet (z=L) | T, u, α | Zero-gradient (Neumann) |

---

## 6. Flow Pattern Models

### 6.1 Supported Flow Patterns

| Pattern | Description | NeqSim Enum |
|---------|-------------|-------------|
| Stratified | Liquid bottom, gas top | `FlowPattern.STRATIFIED` |
| Stratified-Wavy | With interfacial waves | `FlowPattern.STRATIFIED_WAVY` |
| Annular | Liquid film, gas core | `FlowPattern.ANNULAR` |
| Slug | Alternating slugs/bubbles | `FlowPattern.SLUG` |
| Bubble | Gas bubbles in liquid | `FlowPattern.BUBBLE` |
| Droplet/Mist | Liquid drops in gas | `FlowPattern.DROPLET` |
| Churn | Chaotic oscillating | `FlowPattern.CHURN` |

### 6.2 Flow Pattern Detection Models

| Model | Description | NeqSim Enum |
|-------|-------------|-------------|
| Manual | User-specified | `FlowPatternModel.MANUAL` |
| Taitel-Dukler | Horizontal/near-horizontal | `FlowPatternModel.TAITEL_DUKLER` |
| Baker Chart | General correlation | `FlowPatternModel.BAKER_CHART` |
| Barnea | Unified model | `FlowPatternModel.BARNEA` |

---

## 7. NeqSim Implementation

### 7.1 Basic Usage with Builder Pattern

```java
// Create fluid system
SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
fluid.addComponent("methane", 0.85, 0);
fluid.addComponent("water", 0.15, 1);
fluid.createDatabase(true);
fluid.setMixingRule(2);

// Build pipe system
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
    .withFluid(fluid)
    .withDiameter(0.15, "m")
    .withLength(500, "m")
    .withNodes(50)
    .withFlowPattern(FlowPattern.STRATIFIED)
    .withConvectiveBoundary(278.15, "K", 10.0)  // Ambient temp, U-value
    .build();

// Solve
pipe.solveSteadyState(2);
```

### 7.2 Inclined Pipe Configuration

```java
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
    .withFluid(fluid)
    .withDiameter(0.1, "m")
    .withLength(1000, "m")
    .withNodes(100)
    .withInclination(15, "degrees")  // 15° upward
    .withFlowPattern(FlowPattern.SLUG)
    .build();

// Check inclination
System.out.println("Inclination: " + pipe.getInclinationDegrees() + " degrees");
System.out.println("Is upward flow: " + pipe.isUpwardFlow());
```

### 7.3 Extracting Results

```java
// Get profiles
double[] temperature = pipe.getTemperatureProfile();
double[] pressure = pipe.getPressureProfile();
double[] gasVelocity = pipe.getVelocityProfile(0);
double[] liquidVelocity = pipe.getVelocityProfile(1);
double[] liquidHoldup = pipe.getLiquidHoldupProfile();

// Get pressure drop breakdown
double totalDP = pipe.getTotalPressureDrop();
double frictionalDP = pipe.getFrictionalPressureDrop();
double gravitationalDP = pipe.getGravitationalPressureDrop();

// Get heat transfer info
double[] htc = pipe.getOverallInterphaseHeatTransferCoefficientProfile();
double totalHeatLoss = pipe.getTotalHeatLoss();

// Export to CSV
pipe.exportToCSV("results.csv");

// Get summary report
System.out.println(pipe.getSummaryReport());
```

**Pressure drop (steady-state):** The steady-state solver updates the pressure profile from an
integrated momentum balance (friction + gravity) after solving the phase momentum equations. This
ensures a physically consistent pressure drop along the pipe instead of keeping pressure nearly
constant due to a density-only correction.

### 7.4 Flow Pattern Detection

```java
// Enable automatic flow pattern detection
pipe.setFlowPatternModel(FlowPatternModel.TAITEL_DUKLER);
pipe.detectFlowPatterns();

// Get flow pattern at each node
FlowPattern[] patterns = pipe.getFlowPatternProfile();
int transitions = pipe.getFlowPatternTransitionCount();
```

### 7.5 Non-Equilibrium Mode

```java
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
    .withFluid(fluid)
    .withDiameter(0.1, "m")
    .withLength(100, "m")
    .withNodes(50)
    .enableNonEquilibriumMassTransfer()  // Enable mass transfer
    .enableNonEquilibriumHeatTransfer()  // Enable heat transfer
    .build();
```

---

## 8. Key Classes and Methods

### TwoPhasePipeFlowSystem

| Method | Description |
|--------|-------------|
| `solveSteadyState(int type)` | Solve steady-state equations |
| `solveTransient(int type)` | Solve transient equations |
| `getTemperatureProfile()` | Temperature along pipe [K] |
| `getPressureProfile()` | Pressure along pipe [Pa] |
| `getVelocityProfile(int phase)` | Phase velocity [m/s] |
| `getLiquidHoldupProfile()` | Liquid holdup [-] |
| `getTotalPressureDrop()` | Total ΔP [bar] |
| `exportToCSV(String path)` | Export results to CSV |
| `getSummaryReport()` | Formatted text summary |

### TwoPhasePipeFlowSystemBuilder

| Method | Description |
|--------|-------------|
| `withFluid(SystemInterface)` | Set thermodynamic system |
| `withDiameter(double, String)` | Set pipe diameter |
| `withLength(double, String)` | Set pipe length |
| `withNodes(int)` | Set number of nodes |
| `withInclination(double, String)` | Set pipe inclination |
| `withFlowPattern(FlowPattern)` | Set initial flow pattern |
| `withWallTemperature(double, String)` | Constant wall temp BC |
| `withConvectiveBoundary(double, String, double)` | Convective BC |
| `withAdiabaticWall()` | Adiabatic BC |
| `build()` | Create the pipe system |

---

## 9. References

1. **Solbraa, E. (2002)**. "Measurement and Calculation of Two-Phase Flow in Pipes." PhD Thesis, Norwegian University of Science and Technology.

2. **Taitel, Y., & Dukler, A.E. (1976)**. "A model for predicting flow regime transitions in horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.

3. **Lockhart, R.W. and Martinelli, R.C. (1949)**. "Proposed Correlation of Data for Isothermal Two-Phase, Two-Component Flow in Pipes." Chemical Engineering Progress, 45(1), 39-48.

4. **Krishna, R. and Standart, G.L. (1976)**. "A multicomponent film model incorporating a general matrix method of solution to the Maxwell-Stefan equations." AIChE Journal, 22(2), 383-389.

5. **Gnielinski, V. (1976)**. "New equations for heat and mass transfer in turbulent pipe and channel flow." International Chemical Engineering, 16(2), 359-368.

---

*Document generated for NeqSim Two-Phase Pipe Flow Module*
