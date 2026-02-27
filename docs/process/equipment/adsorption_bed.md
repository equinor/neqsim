---
title: "Adsorption Bed - Transient Simulation and Cyclic Processes"
description: "Complete mathematical and engineering reference for the AdsorptionBed unit operation in NeqSim. Covers fixed-bed dynamics, axial discretization, LDF mass transfer, Ergun pressure drop, breakthrough prediction, mass transfer zones, and PSA/TSA/VSA cycle control."
---

# Adsorption Bed — Transient Simulation and Cyclic Processes

The `AdsorptionBed` class is a process equipment unit operation for simulating fixed-bed adsorption columns. It supports both steady-state and transient simulation with axial discretization, mass transfer zones, breakthrough detection, and full PSA/TSA/VSA cycle control.

**Package:** `neqsim.process.equipment.adsorber`

## Architecture

```
ProcessEquipmentBaseClass
└── TwoPortEquipment
    └── AdsorptionBed
```

The bed is a single-inlet, single-outlet equipment (`TwoPortEquipment`) that integrates with the NeqSim `ProcessSystem` flowsheet engine. It clones the inlet thermodynamic system, modifies it to reflect adsorption, and passes the result to the outlet stream.

---

## 1. Bed Geometry and Packing

### Bed Parameters

| Parameter | Symbol | Default | Unit | Method |
|-----------|--------|---------|------|--------|
| Internal diameter | $D$ | 1.0 | m | `setBedDiameter(double)` |
| Bed length | $L$ | 3.0 | m | `setBedLength(double)` |
| Void fraction | $\varepsilon$ | 0.35 | — | `setVoidFraction(double)` |
| Particle diameter | $d_p$ | 0.003 | m | `setParticleDiameter(double)` |
| Particle porosity | $\varepsilon_p$ | 0.40 | — | `setParticlePorosity(double)` |
| Adsorbent bulk density | $\rho_b$ | 500 | kg/m$^3$ | `setAdsorbentBulkDensity(double)` |
| Adsorbent material | — | `"AC"` | — | `setAdsorbentMaterial(String)` |

### Derived Quantities

$$
A_{bed} = \frac{\pi}{4} D^2
\qquad
V_{bed} = A_{bed} \cdot L
\qquad
m_{ads} = V_{bed} \cdot (1 - \varepsilon) \cdot \rho_b
$$

---

## 2. Steady-State Model

The steady-state mode (`run(UUID)`) provides a quick estimate of separation performance without tracking spatial or temporal dynamics. It is invoked when `setCalculateSteadyState(true)` (the default).

### Algorithm

1. **Equilibrium loading**: Create the selected isotherm model and evaluate $q_i^*$ for each component
2. **Superficial velocity**:

$$
u_s = \frac{\dot{n}_{total}}{\rho_{mol} \cdot A_{bed}}
$$

3. **Gas residence time**:

$$
\tau = \frac{\varepsilon \cdot V_{bed}}{u_s \cdot A_{bed}}
$$

4. **NTU-based removal efficiency**:

$$
\eta_i = 1 - \exp(-k_{LDF,i} \cdot \tau)
$$

5. **Moles adsorbed per unit time**:

$$
\dot{n}_{ads,i} = \eta_i \cdot q_i^* \cdot \frac{m_{ads}}{\tau}
$$

   Capped at 99.9% of inlet moles to prevent complete depletion.

6. **Outlet composition**: Subtract adsorbed moles from the inlet, normalise
7. **Pressure drop**: Ergun equation (see Section 4)
8. **Flash**: TP-flash on the outlet to determine final phase state

### Isotherm Model Selection

Set via `setIsothermType(IsothermType)`:

| `IsothermType` | Isotherm Class Used |
|---------------|-------------------|
| `LANGMUIR` | `LangmuirAdsorption` (single-component) |
| `EXTENDED_LANGMUIR` | `LangmuirAdsorption` (extended, multi-component) |
| `BET` | `BETAdsorption` |
| `FREUNDLICH` | `FreundlichAdsorption` |
| `SIPS` | `SipsAdsorption` |
| `DRA` | `PotentialTheoryAdsorption` |

See [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md) for the mathematical details of each isotherm.

---

## 3. Transient Model

The transient mode (`runTransient(double dt, UUID id)`) discretises the bed axially into $N$ cells and solves coupled convection + mass transfer equations in time. Activate with `setCalculateSteadyState(false)`.

### 3.1 Axial Discretisation

The bed is divided into $N$ uniform cells (default 50, set via `setNumberOfCells(int)`):

$$
\Delta z = \frac{L}{N}
$$

Each cell has:
- **Gas concentration** $C_i^{(k)}$ — moles of component $i$ per m$^3$ of gas in cell $k$
- **Solid loading** $q_i^{(k)}$ — moles of component $i$ per kg of adsorbent in cell $k$
- **Cell void volume**: $V_{void} = A_{bed} \cdot \Delta z \cdot \varepsilon$
- **Cell adsorbent mass**: $m_{ads}^{cell} = A_{bed} \cdot \Delta z \cdot (1 - \varepsilon) \cdot \rho_b$

### 3.2 CFL-Limited Sub-Stepping

The interstitial velocity is:

$$
u_{int} = \frac{u_s}{\varepsilon}
$$

To maintain numerical stability, the time step is limited by the Courant-Friedrichs-Lewy (CFL) condition:

$$
\Delta t_{max} = 0.8 \cdot \frac{\Delta z}{u_{int}}
$$

The user-requested time step $\Delta t$ is divided into $N_{sub} = \lceil \Delta t / \Delta t_{max} \rceil$ sub-steps, each of size $\Delta t_{sub} = \Delta t / N_{sub}$.

### 3.3 Convective Transport (Upwind Scheme)

Gas flows from cell 0 (inlet) to cell $N-1$ (outlet). At each sub-step, the upwind finite-difference scheme updates gas concentrations:

$$
C_i^{(k),new} = C_i^{(k)} + \Delta t_{sub} \cdot u_{int} \cdot \frac{C_i^{(k-1)} - C_i^{(k)}}{\Delta z}
$$

For the first cell ($k = 0$), the upstream concentration is:

$$
C_i^{upstream} = \begin{cases}
C_i^{inlet} & \text{adsorption mode (normal flow)} \\
0 & \text{desorption mode (purge with clean gas)}
\end{cases}
$$

### 3.4 Linear Driving Force (LDF) Mass Transfer

After convective transport, mass transfer between the gas and solid phases is calculated in each cell using the LDF approximation:

$$
\frac{dq_i^{(k)}}{dt} = k_{LDF,i} \cdot \left(q_i^{*,(k)} - q_i^{(k)}\right)
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $k_{LDF,i}$ | LDF mass transfer coefficient for component $i$ | 1/s |
| $q_i^{*,(k)}$ | Equilibrium loading from the isotherm at local conditions | mol/kg |
| $q_i^{(k)}$ | Current solid loading in cell $k$ | mol/kg |

The equilibrium loading $q_i^*$ is evaluated by creating a local thermodynamic system with the cell's gas-phase composition and (during desorption) the desorption pressure/temperature.

**Solid update**:

$$
q_i^{(k)} \leftarrow q_i^{(k)} + \Delta t_{sub} \cdot \frac{dq_i}{dt}
\qquad
q_i^{(k)} = \max(0, q_i^{(k)})
$$

**Gas-phase mass balance** (moles transferred from gas to solid reduces gas concentration):

$$
C_i^{(k)} \leftarrow C_i^{(k)} - \Delta t_{sub} \cdot \frac{dq_i/dt \cdot m_{ads}^{cell}}{V_{void}}
\qquad
C_i^{(k)} = \max(0, C_i^{(k)})
$$

### 3.5 LDF Coefficient

The LDF coefficient $k_{LDF}$ lumps together all mass transfer resistances (external film, macropore diffusion, micropore diffusion). It can be set globally or per-component:

```java
bed.setKLDF(0.05);           // Global default: 0.05 s⁻¹ for all components
bed.setKLDF(0, 0.02);        // Component 0 specific
bed.setKLDF(1, 0.08);        // Component 1 specific
```

The default value is **0.01 s$^{-1}$**. Typical ranges:

| Adsorbent | $k_{LDF}$ Range (s$^{-1}$) |
|-----------|---------------------------|
| Zeolite pellets (2-3 mm) | 0.001 – 0.05 |
| Activated carbon granules | 0.005 – 0.1 |
| Carbon molecular sieves | 0.0001 – 0.01 |
| MOF pellets | 0.01 – 0.5 |

### 3.6 Desorption Mode

During desorption, two modifications alter the physics:

1. **Inlet concentration set to zero**: Simulates purge with clean gas (no adsorbate entering)
2. **Equilibrium calculated at desorption conditions**: The local thermodynamic system uses the desorption pressure and/or temperature rather than feed conditions

$$
q_i^* = f(T_{des}, P_{des}, \mathbf{y}) \quad \text{where } q_i^* < q_i \text{ (drives mass transfer reversal)}
$$

Since $q_i^* < q_i^{(k)}$, the LDF driving force becomes **negative**:

$$
\frac{dq_i}{dt} = k_{LDF,i} \cdot \underbrace{(q_i^* - q_i)}_{< 0} < 0
$$

This releases molecules from the solid back into the gas phase — desorption.

**Pressure Swing** (PSA): Reducing pressure lowers $q^*$ through the isotherm's pressure dependence.

**Temperature Swing** (TSA): Increasing temperature lowers $q^*$ through the van 't Hoff equation ($K$ decreases with $T$).

If no explicit desorption pressure is set, the model defaults to atmospheric pressure (1.01325 bara) for PSA-style regeneration.

```java
bed.setDesorptionMode(true);
bed.setDesorptionPressure(1.0);       // PSA: regenerate at 1 bara
bed.setDesorptionTemperature(423.15); // TSA: regenerate at 150°C
```

### 3.7 Outlet Composition

After all sub-steps, the outlet stream composition is set from the **last cell** ($k = N-1$):

$$
y_i^{out} = \frac{C_i^{(N-1)}}{\displaystyle\sum_j C_j^{(N-1)}}
$$

followed by a TP-flash to determine the outlet phase state.

---

## 4. Ergun Pressure Drop

The pressure drop across the packed bed is calculated using the Ergun equation, which combines viscous (Blake-Kozeny) and inertial (Burke-Plummer) contributions:

$$
\frac{\Delta P}{L} = \underbrace{\frac{150\, \mu\, u_s\, (1 - \varepsilon)^2}{\varepsilon^3\, d_p^2}}_{\text{viscous (laminar)}} + \underbrace{\frac{1.75\, \rho\, u_s^2\, (1 - \varepsilon)}{\varepsilon^3\, d_p}}_{\text{inertial (turbulent)}}
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $\Delta P$ | Total pressure drop | Pa |
| $L$ | Bed length | m |
| $\mu$ | Gas dynamic viscosity | Pa s |
| $\rho$ | Gas density | kg/m$^3$ |
| $u_s$ | Superficial velocity | m/s |
| $\varepsilon$ | Void fraction | — |
| $d_p$ | Particle diameter | m |

The pressure drop calculation can be toggled with `setCalculatePressureDrop(boolean)`.

### Bed Reynolds Number

The transition from viscous to inertial flow is characterized by:

$$
Re_p = \frac{\rho\, u_s\, d_p}{\mu\, (1 - \varepsilon)}
$$

- $Re_p < 10$: Viscous-dominated (Blake-Kozeny)
- $Re_p > 1000$: Inertial-dominated (Burke-Plummer)
- $10 < Re_p < 1000$: Transition (full Ergun)

---

## 5. Breakthrough and Mass Transfer Zone

### 5.1 Breakthrough Detection

The bed continuously monitors the outlet-to-inlet concentration ratio for all components:

$$
\frac{C_i^{out}}{C_i^{in}} > \theta_{BT}
$$

where $\theta_{BT}$ is the breakthrough threshold (default 0.05, i.e., 5% leakage). When any component exceeds this, `breakthroughOccurred` is set to `true` and the `breakthroughTime` is recorded.

```java
bed.setBreakthroughThreshold(0.01); // 1% leakage criterion
// After simulation:
if (bed.hasBreakthrough()) {
    double tBT = bed.getBreakthroughTime(); // seconds
}
```

### 5.2 Mass Transfer Zone (MTZ) Length

The MTZ is the region of the bed where active mass transfer is occurring — where the concentration front is spreading between fully saturated (upstream) and clean (downstream) regions. It is calculated from the axial concentration profile:

$$
L_{MTZ} = \Delta z \cdot (k_{0.95} - k_{0.05})
$$

where $k_{0.05}$ is the cell index where the normalised concentration first exceeds 0.05, and $k_{0.95}$ where it exceeds 0.95, relative to the inlet concentration.

A shorter MTZ indicates sharper mass transfer and better bed utilisation.

```java
double mtzLength = bed.getMassTransferZoneLength(componentIndex);
```

### 5.3 Bed Utilisation

The fraction of the total bed capacity that is being used:

$$
U_i = \frac{\bar{q}_i}{q_{max,i}}
$$

where $\bar{q}_i$ is the average loading across all cells:

$$
\bar{q}_i = \frac{1}{N} \sum_{k=0}^{N-1} q_i^{(k)}
$$

```java
double utilization = bed.getBedUtilization(componentIndex); // 0 to 1
double avgLoading = bed.getAverageLoading(componentIndex);  // mol/kg
```

---

## 6. Axial Profiles

The transient simulation maintains complete profiles that can be queried at any time:

```java
// Loading profile: mol/kg in each cell for a given component
double[] loadings = bed.getLoadingProfile(componentIndex);

// Concentration profile: mol/m³ in each cell for a given component
double[] concentrations = bed.getConcentrationProfile(componentIndex);
```

These profiles are essential for:
- Visualising the concentration front propagation
- Determining when to switch cycles in PSA
- Calculating the MTZ length and bed utilisation

---

## 7. PSA/TSA Cycle Controller

The `AdsorptionCycleController` manages multi-step cyclic adsorption processes. It schedules phase transitions, applies the correct operating conditions to the bed, and tracks cycle completion.

### 7.1 Cycle Phases

| Phase | `CyclePhase` Enum | Bed Mode | Description |
|-------|-------------------|----------|-------------|
| Adsorption | `ADSORPTION` | Normal | Feed gas flows through; adsorbate captured |
| Co-current depressurisation | `COCURRENT_DEPRESSURISATION` | Desorption | Gentle pressure reduction in feed direction |
| Blowdown | `BLOWDOWN` | Desorption | Rapid counter-current depressurisation |
| Purge | `PURGE` | Desorption | Clean gas sweeps at low pressure |
| Repressurisation | `REPRESSURISATION` | Normal | Pressure raised to feed level |
| Desorption (TSA) | `DESORPTION` | Desorption | Heated regeneration |
| Cooling | `COOLING` | Normal | Cool bed after TSA heating |
| Standby | `STANDBY` | — | No flow (multi-bed scheduling) |

### 7.2 Pre-Configured Cycles

#### Skarstrom PSA Cycle (4-Step)

```java
AdsorptionCycleController controller = new AdsorptionCycleController(bed);
controller.configurePSA(
    300.0,   // Adsorption time (s)
    30.0,    // Blowdown time (s)
    60.0,    // Purge time (s)
    30.0,    // Repressurisation time (s)
    1.0      // Low-side pressure (bara)
);
```

This creates the classic Skarstrom cycle:

```
ADSORPTION (300s) → BLOWDOWN (30s, P→1 bar) → PURGE (60s, P=1 bar) → REPRESSURISATION (30s)
    ↑_____________________________________________↓ (auto-loop)
```

#### TSA Cycle (3-Step)

```java
controller.configureTSA(
    1800.0,  // Adsorption time (s)
    600.0,   // Heating/desorption time (s)
    300.0,   // Cooling time (s)
    423.15   // Desorption temperature (K = 150°C)
);
```

```
ADSORPTION (1800s) → DESORPTION (600s, T=423K) → COOLING (300s)
    ↑_____________________________________________↓ (auto-loop)
```

### 7.3 Custom Cycles

```java
AdsorptionCycleController controller = new AdsorptionCycleController(bed);

// 6-step modified PSA with equalisation
controller.addStep(new AdsorptionCycleController.PhaseStep(
    CyclePhase.ADSORPTION, 240.0));
controller.addStep(new AdsorptionCycleController.PhaseStep(
    CyclePhase.COCURRENT_DEPRESSURISATION, 30.0, 5.0, -1)); // to 5 bar
controller.addStep(new AdsorptionCycleController.PhaseStep(
    CyclePhase.BLOWDOWN, 30.0, 1.0, -1));                   // to 1 bar
controller.addStep(new AdsorptionCycleController.PhaseStep(
    CyclePhase.PURGE, 90.0, 1.0, -1));
controller.addStep(new AdsorptionCycleController.PhaseStep(
    CyclePhase.REPRESSURISATION, 30.0));
```

### 7.4 Advancing the Cycle

The controller is advanced inside the transient simulation loop:

```java
double dt = 0.5; // time step
UUID id = UUID.randomUUID();

for (int step = 0; step < totalSteps; step++) {
    controller.advance(dt, id);   // Manages phase transitions
    bed.runTransient(dt, id);     // Runs the bed physics
}
```

The `advance()` method:
1. Accumulates elapsed time in the current phase
2. Checks if the phase duration has been exceeded
3. If transitioning to a new phase:
   - Applies the new operating conditions (`setDesorptionMode`, pressure, temperature)
   - If a full cycle completes: resets the bed, re-initialises the grid, increments the cycle counter
4. Optionally transitions on breakthrough (`setTransitionOnBreakthrough(true)`)

### 7.5 Cycle Monitoring

```java
CyclePhase phase = controller.getCurrentPhase();
int cycles = controller.getCompletedCycles();
double timeInPhase = controller.getTimeInCurrentStep();

controller.setAutoLoop(true);   // Repeat cycles automatically (default)
controller.setTransitionOnBreakthrough(true); // Switch on early breakthrough
```

---

## 8. Bed Pre-Loading and Reset

### Pre-Loading

For simulating partially saturated beds or continuing from a previous state:

$$
q_i^{(k)} = f_{sat} \cdot q_i^{*}
$$

where $f_{sat}$ is the fractional saturation (0 to 1) and $q_i^*$ is the equilibrium loading from the isotherm.

```java
bed.preloadBed(0.5, 0);  // 50% saturated using phase 0 equilibrium
```

### Reset

Clears all loadings, gas concentrations, and timing state:

```java
bed.resetBed();  // All q = 0, all C = 0, elapsed time = 0
```

---

## 9. Validation

The bed implements `validateSetup()` which checks:

| Check | Error Condition |
|-------|----------------|
| Inlet stream | Not connected |
| Bed diameter | $\leq 0$ |
| Bed length | $\leq 0$ |
| Void fraction | $\leq 0$ or $\geq 1$ |
| Particle diameter | $\leq 0$ |
| Number of cells | $< 2$ |
| kLDF | $< 0$ |

```java
ValidationResult result = SimulationValidator.validate(bed);
if (!result.isValid()) {
    System.out.println(result.getErrors());
}
```

---

## 10. JSON Reporting

The `toJson()` method produces a comprehensive JSON report including:

```json
{
  "equipmentName": "CO2 Adsorber",
  "bedGeometry": {
    "diameter_m": 1.0,
    "length_m": 3.0,
    "voidFraction": 0.35,
    "bedVolume_m3": 2.356,
    "adsorbentMass_kg": 766.0
  },
  "adsorbent": {
    "material": "Zeolite 13X",
    "bulkDensity_kgm3": 500.0,
    "particleDiameter_m": 0.003,
    "particlePorosity": 0.4
  },
  "isothermType": "LANGMUIR",
  "operatingConditions": {
    "temperature_K": 298.15,
    "pressure_bara": 10.0,
    "desorptionMode": false
  },
  "transient": {
    "numberOfCells": 50,
    "elapsedTime_s": 150.0,
    "breakthroughOccurred": false,
    "breakthroughTime_s": -1.0
  },
  "pressureDrop_Pa": 1234.5
}
```

---

## 11. Complete Example

### PSA for CO$_2$ Removal from Natural Gas

```java
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.adsorber.AdsorptionCycleController;
import neqsim.process.equipment.adsorber.AdsorptionCycleController.CyclePhase;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// 1. Create feed gas
SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
gas.addComponent("methane", 0.85);
gas.addComponent("CO2", 0.10);
gas.addComponent("nitrogen", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(5000.0, "kg/hr");
feed.run();

// 2. Configure adsorption bed
AdsorptionBed bed = new AdsorptionBed("PSA Bed 1", feed);
bed.setBedDiameter(1.5);
bed.setBedLength(4.0);
bed.setVoidFraction(0.38);
bed.setAdsorbentMaterial("Zeolite 13X");
bed.setAdsorbentBulkDensity(620.0);
bed.setParticleDiameter(0.002);
bed.setIsothermType(IsothermType.LANGMUIR);
bed.setKLDF(0.05);
bed.setNumberOfCells(100);
bed.setCalculateSteadyState(false);
bed.setBreakthroughThreshold(0.02);

// 3. Configure PSA cycle
AdsorptionCycleController controller = new AdsorptionCycleController(bed);
controller.configurePSA(300.0, 30.0, 60.0, 30.0, 1.0);

// 4. Run transient simulation
double dt = 0.5;
UUID id = UUID.randomUUID();
int totalSteps = (int)(420.0 / dt); // one full cycle

for (int step = 0; step < totalSteps; step++) {
    controller.advance(dt, id);
    bed.runTransient(dt, id);

    // Monitor every 10 seconds
    if (step % 20 == 0) {
        System.out.printf("t=%.0f s, Phase=%s, CO2 avg loading=%.3f mol/kg%n",
            bed.getElapsedTime(),
            controller.getCurrentPhase(),
            bed.getAverageLoading(1));  // CO2 = component 1

        if (bed.hasBreakthrough()) {
            System.out.println("  ** BREAKTHROUGH at t=" + bed.getBreakthroughTime());
        }
    }
}

// 5. Results
double[] co2Profile = bed.getLoadingProfile(1);
double mtzLength = bed.getMassTransferZoneLength(1);
double utilization = bed.getBedUtilization(1);
System.out.println("MTZ length: " + mtzLength + " m");
System.out.println("Bed utilization: " + (utilization * 100) + "%");
System.out.println("Completed cycles: " + controller.getCompletedCycles());
System.out.println(bed.toJson());
```

---

## 12. Numerical Considerations

### Grid Resolution

The number of cells $N$ controls the trade-off between accuracy and computational cost:

| $N$ | Numerical Diffusion | MTZ Resolution | Relative Speed |
|-----|---------------------|----------------|----------------|
| 10 | High (smeared front) | Poor | Very fast |
| 50 | Moderate | Good | Fast |
| 100 | Low | Very good | Moderate |
| 200+ | Minimal | Excellent | Slow |

Use $N \geq 50$ for quantitative MTZ analysis.

### Time Step Selection

The external time step $\Delta t$ is automatically sub-divided for CFL stability. However, choosing $\Delta t$ too large increases the overhead of computing many sub-steps. Recommended:

$$
\Delta t \approx 2 \text{–} 5 \times \Delta t_{CFL}
$$

For typical conditions ($u_s = 0.1$ m/s, $\varepsilon = 0.35$, $L = 3$ m, $N = 50$):

$$
\Delta t_{CFL} \approx 0.8 \times \frac{0.06}{0.286} \approx 0.17 \text{ s}
$$

So $\Delta t = 0.5$–1.0 s is a good choice.

### Upwind Scheme Properties

The first-order upwind scheme is:
- **Unconditionally stable** when CFL $\leq 1$ (ensured by sub-stepping)
- **First-order accurate** in space and time
- **Numerically diffusive**: the concentration front is smeared over ~$\sqrt{N}$ cells

For sharper fronts, increase $N$. Higher-order schemes (TVD, WENO) are not currently implemented but could be added for reduced numerical diffusion.

---

## Related Documentation

- [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md) — Mathematical details of all isotherm models
- [Adsorption Cookbook](../cookbook/adsorption-recipes.md) — Quick-start recipes and common workflows
- [Adsorbers (SimpleAdsorber)](adsorbers.md) — The simplified chemical absorption model
- [Process Simulation Fundamentals](../process/) — NeqSim process equipment framework
