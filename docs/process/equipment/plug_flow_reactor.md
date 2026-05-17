---
title: "Plug Flow Reactor (PFR) Guide"
description: "Comprehensive guide to the NeqSim Plug Flow Reactor for rigorous kinetic modeling. Covers governing equations, kinetic rate expressions (power-law, LHHW, reversible), catalyst bed modeling, Ergun pressure drop, energy modes, integration methods, axial profiles, and worked examples for ammonia synthesis and steam reforming."
---

# Plug Flow Reactor (PFR) Guide

The NeqSim Plug Flow Reactor models chemical transformation along a tubular reactor by solving
coupled ordinary differential equations for species molar flows, temperature, and pressure as a
function of axial position. It supports homogeneous gas-phase and heterogeneous catalytic reactions
with multiple kinetic rate expression types.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Governing Equations](#governing-equations)
- [KineticReaction — Reaction Kinetics](#kineticreaction--reaction-kinetics)
- [CatalystBed — Packed Bed Properties](#catalystbed--packed-bed-properties)
- [PlugFlowReactor — Reactor Configuration](#plugflowreactor--reactor-configuration)
- [ReactorAxialProfile — Results and Post-Processing](#reactoraxialprofile--results-and-post-processing)
- [Worked Examples](#worked-examples)
- [Python (Jupyter Notebook) Usage](#python-jupyter-notebook-usage)
- [Comparison with Commercial Software](#comparison-with-commercial-software)
- [API Reference Summary](#api-reference-summary)
- [Troubleshooting](#troubleshooting)
- [Related Documentation](#related-documentation)

---

## Overview

**Package:** `neqsim.process.equipment.reactor`

| Class | Purpose |
|-------|---------|
| `PlugFlowReactor` | Main process equipment — extends `TwoPortEquipment`, runs ODE integration |
| `KineticReaction` | Kinetic rate expression (power-law, LHHW, reversible Arrhenius) |
| `CatalystBed` | Catalyst bed properties, Ergun pressure drop, Thiele modulus |
| `ReactorAxialProfile` | Storage and interpolation of T(z), P(z), X(z), F_i(z) profiles |

**Key features:**

- Power-law, LHHW, and reversible equilibrium kinetics with modified Arrhenius temperature dependence
- Adiabatic, isothermal, and coolant heat exchange energy modes
- Ergun equation pressure drop for packed catalyst beds
- Catalyst effectiveness factor via Thiele modulus
- Euler and 4th-order Runge-Kutta (RK4) ODE integration
- Multi-tube reactor geometry
- Configurable property update frequency for performance tuning
- Axial profiles with interpolation and JSON/CSV export
- Full thermodynamic coupling via NeqSim equation of state

---

## Architecture

The PFR uses a composition pattern:

```
PlugFlowReactor (extends TwoPortEquipment)
 ├── List<KineticReaction>    — one or more kinetic rate expressions
 ├── CatalystBed (optional)   — packed bed properties and pressure drop
 └── ReactorAxialProfile      — axial results after simulation
```

`PlugFlowReactor` integrates into any `ProcessSystem` flowsheet like other NeqSim equipment.
It reads from an inlet stream and produces an outlet stream with updated composition, temperature,
and pressure.

---

## Governing Equations

### Species Mole Balance

$$
\frac{dF_i}{dz} = A_c \sum_j \nu_{ij} \, r_j
$$

Where:
- $F_i$ = molar flow of component $i$ [mol/s]
- $z$ = axial position along reactor [m]
- $A_c$ = total cross-sectional area [m²]
- $\nu_{ij}$ = stoichiometric coefficient of component $i$ in reaction $j$
- $r_j$ = volumetric rate of reaction $j$ [mol/(m³·s)]

### Energy Balance

**Adiabatic mode:**

$$
\frac{dT}{dz} = \frac{-\sum_j r_j \, \Delta H_{rxn,j} \cdot A_c}{\sum_i F_i \, C_{p,i}}
$$

**Coolant mode:**

$$
\frac{dT}{dz} = \frac{-\sum_j r_j \, \Delta H_{rxn,j} \cdot A_c \;+\; U \pi D \, N_{tubes} (T_c - T)}{\sum_i F_i \, C_{p,i}}
$$

Where:
- $T$ = gas temperature [K]
- $\Delta H_{rxn,j}$ = heat of reaction $j$ [J/mol] (negative for exothermic)
- $C_{p,i}$ = molar heat capacity of component $i$ [J/(mol·K)]
- $U$ = overall heat transfer coefficient [W/(m²·K)]
- $D$ = tube inner diameter [m]
- $T_c$ = coolant temperature [K]
- $N_{tubes}$ = number of parallel tubes

**Isothermal mode:** $dT/dz = 0$ (temperature forced constant, heat duty calculated)

### Pressure Drop — Ergun Equation (Packed Bed)

$$
\frac{dP}{dz} = -\frac{150 \, \mu \, (1-\varepsilon)^2 \, u}{\varepsilon^3 \, d_p^2} \;-\; \frac{1.75 \, \rho \, (1-\varepsilon) \, u^2}{\varepsilon^3 \, d_p}
$$

Where:
- $\mu$ = gas dynamic viscosity [Pa·s]
- $\varepsilon$ = bed void fraction [-]
- $u$ = superficial velocity [m/s]
- $d_p$ = catalyst particle diameter [m]
- $\rho$ = gas density [kg/m³]

For empty-tube (homogeneous) reactors, a Darcy-Weisbach friction loss model is used.

---

## KineticReaction — Reaction Kinetics

### Rate Constant (Modified Arrhenius)

$$
k(T) = A \, T^n \, \exp\!\left(\frac{-E_a}{R \, T}\right)
$$

| Parameter | Method | Unit |
|-----------|--------|------|
| Pre-exponential factor $A$ | `setPreExponentialFactor(double)` | depends on rate expression |
| Activation energy $E_a$ | `setActivationEnergy(double)` | J/mol |
| Temperature exponent $n$ | `setTemperatureExponent(double)` | dimensionless |

### Power-Law Rate Expression

For an irreversible reaction $a\text{A} + b\text{B} \to c\text{C}$:

$$
r = k(T) \prod_i C_i^{\alpha_i}
$$

For a reversible reaction:

$$
r = k(T) \left[ \prod_i C_i^{\alpha_i} \;-\; \frac{\prod_j C_j^{\beta_j}}{K_{eq}(T)} \right]
$$

Where:
- $C_i$ = concentration of species $i$ [mol/m³]
- $\alpha_i$ = reaction order of reactant $i$
- $\beta_j$ = reaction order of product $j$ (for reverse direction)
- $K_{eq}(T)$ = equilibrium constant from correlation

### LHHW Rate Expression

$$
r = k(T) \cdot \frac{\text{driving force}}{(1 + \sum_k K_k \, C_k^{n_k})^m}
$$

The driving force is the same as the power-law numerator. The denominator models surface adsorption:

| Parameter | Method | Description |
|-----------|--------|-------------|
| Adsorption term | `addAdsorptionTerm(name, Ki, order)` | Add species to denominator |
| Denominator exponent $m$ | `setAdsorptionExponent(int)` | Power of denominator |
| Adsorption pre-exp. | `setAdsorptionPreExpFactor(double)` | Temperature dependence |
| Adsorption $E_a$ | `setAdsorptionActivationEnergy(double)` | [J/mol] |

### Equilibrium Constant Correlation

$$
\ln K_{eq} = a + \frac{b}{T} + c \ln T + d \, T
$$

Set via `setEquilibriumConstantCorrelation(a, b, c, d)`.

### Stoichiometry Setup

```java
KineticReaction rxn = new KineticReaction("Name");
rxn.addReactant("nitrogen", 1.0, 1.0);   // stoich coeff, reaction order
rxn.addReactant("hydrogen", 3.0, 1.5);   // stoich = 3, order = 1.5
rxn.addProduct("ammonia", 2.0);           // stoich coeff = +2
```

Internally, reactant stoichiometric coefficients are stored as negative, products as positive.

### Rate Basis

| Basis | Enum | Unit | When to Use |
|-------|------|------|-------------|
| Volume | `VOLUME` | mol/(m³·s) | Homogeneous gas-phase reactions |
| Catalyst mass | `CATALYST_MASS` | mol/(kg_cat·s) | Heterogeneous catalytic reactions |
| Catalyst area | `CATALYST_AREA` | mol/(m²_cat·s) | Surface-controlled reactions |

The `PlugFlowReactor` automatically converts catalyst-basis rates to volumetric rates
using the `CatalystBed` bulk density and specific surface area.

---

## CatalystBed — Packed Bed Properties

### Construction

```java
// Default: 3mm particles, 0.40 void fraction, 800 kg/m3 bulk density
CatalystBed catalyst = new CatalystBed();

// Or specify directly (diameter in mm, void fraction, bulk density in kg/m3)
CatalystBed catalyst = new CatalystBed(3.0, 0.40, 800.0);
```

### Key Properties

| Property | Setter | Default | Unit |
|----------|--------|---------|------|
| Particle diameter | `setParticleDiameter(val, "mm")` | 3 mm | m (stored internally) |
| Void fraction | `setVoidFraction(double)` | 0.40 | - |
| Bulk density | `setBulkDensity(double)` | 800 | kg/m³ |
| Particle density | `setParticleDensity(double)` | 1200 | kg/m³ |
| Particle porosity | `setParticlePorosity(double)` | 0.50 | - |
| Tortuosity | `setTortuosity(double)` | 3.0 | - |
| Specific surface area | `setSpecificSurfaceArea(val, "m2/kg")` | 150,000 | m²/kg |
| Activity factor | `setActivityFactor(double)` | 1.0 | - (0=dead, 1=fresh) |

### Ergun Pressure Drop

```java
// Per-meter pressure drop [Pa/m]
double dPdz = catalyst.calculatePressureDrop(velocity, gasDensity, gasViscosity);

// Total pressure drop for a given bed length [bar]
double dPbar = catalyst.calculateTotalPressureDrop(velocity, gasDensity, gasViscosity, bedLength);
```

### Thiele Modulus and Effectiveness Factor

For a first-order irreversible reaction in a spherical pellet:

$$
\phi_s = \frac{R_p}{3} \sqrt{\frac{k_v}{D_{eff}}}
$$

$$
\eta = \frac{1}{\phi_s} \left[ \coth(3\phi_s) - \frac{1}{3\phi_s} \right]
$$

```java
double Deff = catalyst.getEffectiveDiffusivity(Dmolecular); // Deff = Dmol * eps_p / tau
double phi = catalyst.calculateThieleModulus(kv, Deff);
double eta = catalyst.calculateEffectivenessFactor(phi);     // eta in [0, 1]
```

### Reynolds Number

$$
Re_p = \frac{\rho \, u \, d_p}{\mu \, (1 - \varepsilon)}
$$

```java
double Re = catalyst.calculateReynoldsNumber(velocity, gasDensity, gasViscosity);
```

---

## PlugFlowReactor — Reactor Configuration

### Construction and Basic Setup

```java
// With inlet stream
PlugFlowReactor pfr = new PlugFlowReactor("PFR-100", feedStream);

// Geometry
pfr.setLength(5.0, "m");       // supports "m", "cm", "ft"
pfr.setDiameter(0.10, "m");    // supports "m", "mm", "cm", "in"
pfr.setNumberOfTubes(1);       // multi-tube configuration

// Add reactions (one or more)
pfr.addReaction(rxn1);
pfr.addReaction(rxn2);

// Optional: packed bed catalyst
pfr.setCatalystBed(catalyst);
```

### Energy Modes

| Mode | Enum | Behavior |
|------|------|----------|
| Adiabatic | `EnergyMode.ADIABATIC` | Temperature varies from reaction enthalpy; no external heat transfer |
| Isothermal | `EnergyMode.ISOTHERMAL` | Temperature held at inlet value; heat duty calculated |
| Coolant | `EnergyMode.COOLANT` | External cooling/heating jacket; Q = U·π·D·N·(Tc−T) per meter |

```java
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);

// For coolant mode:
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.COOLANT);
pfr.setCoolantTemperature(300.0, "C");           // "K", "C", "F"
pfr.setOverallHeatTransferCoefficient(200.0);     // W/(m2·K)
```

### Numerical Settings

| Setting | Method | Default | Description |
|---------|--------|---------|-------------|
| Integration steps | `setNumberOfSteps(int)` | 100 | Number of axial segments (min 10) |
| Integration method | `setIntegrationMethod("RK4")` | RK4 | "EULER" or "RK4" |
| Property update freq. | `setPropertyUpdateFrequency(int)` | 10 | Re-flash every N steps |
| Key component | `setKeyComponent("methane")` | first reactant | Component for conversion tracking |

**Performance tip:** Increasing `propertyUpdateFrequency` (e.g., 20 or 50) speeds up
computation at the cost of slightly less accurate property updates. For most applications,
updating every 10 steps is a good balance.

### Running and Results

```java
pfr.run();

// Key results
double conversion = pfr.getConversion();           // 0 to 1
double dP = pfr.getPressureDrop();                 // bar
double Tout = pfr.getOutletTemperature();          // K
double duty = pfr.getHeatDuty("kW");               // "W", "kW", "MW"
double tau = pfr.getResidenceTime();               // seconds
double GHSV = pfr.getSpaceVelocity();             // 1/hr

// Outlet stream
StreamInterface outlet = pfr.getOutletStream();
```

### ProcessSystem Integration

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(pfr);
process.add(cooler);     // downstream equipment
process.run();
```

---

## ReactorAxialProfile — Results and Post-Processing

After running, retrieve the axial profile:

```java
ReactorAxialProfile profile = pfr.getAxialProfile();
```

### Profile Arrays

| Method | Returns | Unit |
|--------|---------|------|
| `getPositionProfile()` | double[] z positions | m |
| `getTemperatureProfile()` | double[] T at each z | K |
| `getPressureProfile()` | double[] P at each z | bara |
| `getConversionProfile()` | double[] X at each z | - |
| `getReactionRateProfile()` | double[] rate at each z | mol/(m³·s) |
| `getMolarFlowProfiles()` | double[][] Fi at each z | mol/s |
| `getComponentNames()` | String[] component names | - |

### Interpolation

Get values at any axial position (linearly interpolated):

```java
double Tmid = profile.getTemperatureAt(2.5);   // T at z = 2.5 m
double Xmid = profile.getConversionAt(2.5);    // conversion at z = 2.5 m
double Pmid = profile.getPressureAt(2.5);      // P at z = 2.5 m
```

### Export

```java
String json = profile.toJson();   // JSON string for reporting
String csv  = profile.toCSV();    // CSV for spreadsheet/plotting
```

---

## Worked Examples

### Example 1: Isothermal First-Order Gas Phase Reaction

A simple homogeneous gas-phase decomposition A → B + C:

```java
// Feed: 90% methane (A) + 10% ethane (B) at 300°C, 20 bara
SystemInterface gas = new SystemSrkEos(273.15 + 300.0, 20.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.10);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(10.0, "mole/sec");
feed.run();

// Define reaction: methane -> ethane (illustrative)
KineticReaction rxn = new KineticReaction("A to B");
rxn.addReactant("methane", 1.0, 1.0);   // stoich=1, order=1
rxn.addProduct("ethane", 1.0);           // stoich=1
rxn.setPreExponentialFactor(1.0e4);
rxn.setActivationEnergy(50000.0);        // 50 kJ/mol
rxn.setHeatOfReaction(-50000.0);         // exothermic
rxn.setRateType(KineticReaction.RateType.POWER_LAW);

// Reactor
PlugFlowReactor pfr = new PlugFlowReactor("PFR-1", feed);
pfr.addReaction(rxn);
pfr.setLength(5.0, "m");
pfr.setDiameter(0.10, "m");
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
pfr.setNumberOfSteps(100);
pfr.setKeyComponent("methane");
pfr.run();

System.out.println("Conversion: " + pfr.getConversion());
System.out.println("Pressure drop: " + pfr.getPressureDrop() + " bar");
```

### Example 2: Adiabatic Catalytic Reactor with Packed Bed

An exothermic catalytic reaction with Ergun pressure drop:

```java
// Feed gas
SystemInterface gas = new SystemSrkEos(273.15 + 250.0, 30.0);
gas.addComponent("methane", 0.70);
gas.addComponent("ethane", 0.30);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(5.0, "mole/sec");
feed.run();

// Reaction on catalyst-mass basis
KineticReaction rxn = new KineticReaction("Catalytic A->B");
rxn.addReactant("methane", 1.0, 1.0);
rxn.addProduct("ethane", 1.0);
rxn.setPreExponentialFactor(1.0e5);
rxn.setActivationEnergy(50000.0);
rxn.setHeatOfReaction(-40000.0);
rxn.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);

// Catalyst: 3mm alumina pellets
CatalystBed catalyst = new CatalystBed(3.0, 0.40, 800.0);
catalyst.setParticleDensity(1200.0);
catalyst.setParticlePorosity(0.50);
catalyst.setTortuosity(3.0);

// Reactor
PlugFlowReactor pfr = new PlugFlowReactor("PFR-Cat", feed);
pfr.addReaction(rxn);
pfr.setCatalystBed(catalyst);
pfr.setLength(4.0, "m");
pfr.setDiameter(0.10, "m");
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);
pfr.setNumberOfSteps(100);
pfr.run();

System.out.println("Conversion: " + pfr.getConversion());
System.out.println("Outlet T: " + (pfr.getOutletTemperature() - 273.15) + " °C");
System.out.println("Pressure drop: " + pfr.getPressureDrop() + " bar");

// Access axial profiles
ReactorAxialProfile profile = pfr.getAxialProfile();
double[] temps = profile.getTemperatureProfile();
double[] convs = profile.getConversionProfile();
```

### Example 3: Cooled Multi-Tube Reactor

A shell-and-tube reactor with external coolant:

```java
// High temperature feed
SystemInterface gas = new SystemSrkEos(273.15 + 400.0, 20.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.10);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(50.0, "mole/sec");
feed.run();

KineticReaction rxn = new KineticReaction("Exothermic synthesis");
rxn.addReactant("methane", 1.0, 1.0);
rxn.addProduct("ethane", 1.0);
rxn.setPreExponentialFactor(1.0e5);
rxn.setActivationEnergy(50000.0);
rxn.setHeatOfReaction(-60000.0);

CatalystBed catalyst = new CatalystBed(4.0, 0.42, 750.0);

PlugFlowReactor pfr = new PlugFlowReactor("PFR-MultiTube", feed);
pfr.addReaction(rxn);
pfr.setCatalystBed(catalyst);
pfr.setLength(6.0, "m");
pfr.setDiameter(0.025, "m");    // 25mm tube ID
pfr.setNumberOfTubes(100);       // 100 parallel tubes
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.COOLANT);
pfr.setCoolantTemperature(350.0, "C");
pfr.setOverallHeatTransferCoefficient(250.0);  // W/(m2*K)
pfr.setNumberOfSteps(200);
pfr.run();

System.out.println("Conversion: " + pfr.getConversion());
System.out.println("Outlet T: " + (pfr.getOutletTemperature() - 273.15) + " °C");
System.out.println("Heat duty: " + pfr.getHeatDuty("kW") + " kW");
```

### Example 4: Reversible Reaction with Equilibrium Limitation

```java
KineticReaction rxn = new KineticReaction("Reversible A <=> B");
rxn.addReactant("methane", 1.0, 1.0);
rxn.addProduct("ethane", 1.0, 0.5);   // reverse order = 0.5
rxn.setPreExponentialFactor(1.0e8);
rxn.setActivationEnergy(80000.0);
rxn.setHeatOfReaction(-30000.0);

// ln(Keq) = 10.0 - 5000/T
rxn.setEquilibriumConstantCorrelation(10.0, -5000.0, 0.0, 0.0);
// setEquilibriumConstantCorrelation automatically sets reversible = true
```

### Example 5: LHHW Kinetics (Langmuir-Hinshelwood)

```java
KineticReaction rxn = new KineticReaction("Catalytic LHHW");
rxn.setRateType(KineticReaction.RateType.LHHW);
rxn.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);

// Stoichiometry
rxn.addReactant("methane", 1.0, 1.0);
rxn.addProduct("hydrogen", 2.0);

// Forward rate constant
rxn.setPreExponentialFactor(1.0e8);
rxn.setActivationEnergy(80000.0);

// Adsorption terms: denom = (1 + K_CH4 * C_CH4 + K_H2 * C_H2)^2
rxn.addAdsorptionTerm("methane", 5.0, 1.0);
rxn.addAdsorptionTerm("hydrogen", 2.0, 0.5);
rxn.setAdsorptionExponent(2);
```

### Example 6: Integration into a Full Flowsheet

```java
// Feed preparation
SystemInterface gas = new SystemSrkEos(273.15 + 250.0, 50.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.15);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(100.0, "kg/hr");

// Preheat
Heater preheater = new Heater("Preheater", feed);
preheater.setOutTemperature(273.15 + 350.0);

// Reactor
KineticReaction rxn = new KineticReaction("rxn");
rxn.addReactant("methane", 1.0, 1.0);
rxn.addProduct("ethane", 1.0);
rxn.setPreExponentialFactor(1.0e5);
rxn.setActivationEnergy(60000.0);
rxn.setHeatOfReaction(-50000.0);

PlugFlowReactor pfr = new PlugFlowReactor("Reactor", preheater.getOutletStream());
pfr.addReaction(rxn);
pfr.setLength(3.0, "m");
pfr.setDiameter(0.08, "m");
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);

// Post-cool
Cooler cooler = new Cooler("Product Cooler", pfr.getOutletStream());
cooler.setOutTemperature(273.15 + 40.0);

// Assemble process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(preheater);
process.add(pfr);
process.add(cooler);
process.run();
```

---

## Python (Jupyter Notebook) Usage

```python
from neqsim import jneqsim

# Import classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
PlugFlowReactor = jneqsim.process.equipment.reactor.PlugFlowReactor
KineticReaction = jneqsim.process.equipment.reactor.KineticReaction
CatalystBed = jneqsim.process.equipment.reactor.CatalystBed
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create fluid
gas = SystemSrkEos(273.15 + 300.0, 20.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.10)
gas.setMixingRule("classic")

feed = Stream("Feed", gas)
feed.setFlowRate(10.0, "mole/sec")

# Define reaction
rxn = KineticReaction("A to B")
rxn.addReactant("methane", 1.0, 1.0)
rxn.addProduct("ethane", 1.0)
rxn.setPreExponentialFactor(1.0e4)
rxn.setActivationEnergy(50000.0)
rxn.setHeatOfReaction(-50000.0)

# Catalyst
catalyst = CatalystBed(3.0, 0.40, 800.0)

# Reactor
pfr = PlugFlowReactor("PFR-1", feed)
pfr.addReaction(rxn)
pfr.setCatalystBed(catalyst)
pfr.setLength(5.0, "m")
pfr.setDiameter(0.10, "m")
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC)
pfr.setNumberOfSteps(100)

# Run
process = ProcessSystem()
process.add(feed)
process.add(pfr)
process.run()

# Results
print(f"Conversion: {pfr.getConversion():.4f}")
print(f"Outlet T: {pfr.getOutletTemperature() - 273.15:.1f} °C")
print(f"Pressure drop: {pfr.getPressureDrop():.4f} bar")

# Axial profiles for plotting
profile = pfr.getAxialProfile()
import matplotlib.pyplot as plt

z = list(profile.getPositionProfile())
T = [t - 273.15 for t in profile.getTemperatureProfile()]
X = list(profile.getConversionProfile())

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))
ax1.plot(z, T)
ax1.set_xlabel("Position [m]")
ax1.set_ylabel("Temperature [°C]")
ax1.set_title("Temperature Profile")
ax1.grid(True)

ax2.plot(z, X)
ax2.set_xlabel("Position [m]")
ax2.set_ylabel("Conversion [-]")
ax2.set_title("Conversion Profile")
ax2.grid(True)

plt.tight_layout()
plt.show()
```

---

## Comparison with Commercial Software

| Feature | NeqSim PFR | Aspen RPlug | CHEMCAD PFR | DWSIM PFR |
|---------|-----------|-------------|-------------|-----------|
| ODE integration | Euler, RK4 | Gear, RK4, RKF45 | RK4 | N-CSTR approximation |
| Power-law kinetics | Yes | Yes | Yes | Yes |
| LHHW kinetics | Yes | Yes | Yes | No |
| Reversible reactions | Yes | Yes | Yes | Yes |
| Ergun pressure drop | Yes | Yes | Yes | No |
| Adiabatic mode | Yes | Yes | Yes | Yes |
| Isothermal mode | Yes | Yes | Yes | Yes |
| Co-current coolant | Yes | Yes | Yes | No |
| Counter-current coolant | Not yet | Yes | No | No |
| Thiele modulus | Yes | Yes | No | No |
| Multi-tube | Yes | Yes | No | No |
| Axial profiles | Yes | Yes | Yes | Limited |
| EOS coupling | Full (SRK, PR, CPA) | Full (multiple) | Full | Limited |
| Catalyst deactivation | Activity factor only | Full time-on-stream | No | No |

---

## API Reference Summary

### KineticReaction

| Method | Description |
|--------|-------------|
| `KineticReaction(String name)` | Constructor |
| `addReactant(name, stoichCoeff, order)` | Add reactant with stoichiometry and reaction order |
| `addProduct(name, stoichCoeff)` | Add product with stoichiometry |
| `addProduct(name, stoichCoeff, reverseOrder)` | Add product with reverse reaction order |
| `setRateType(RateType)` | POWER_LAW, LHHW, or EQUILIBRIUM |
| `setRateBasis(RateBasis)` | VOLUME, CATALYST_MASS, or CATALYST_AREA |
| `setPreExponentialFactor(double)` | Set Arrhenius A factor |
| `setActivationEnergy(double)` | Set Ea [J/mol] |
| `setTemperatureExponent(double)` | Set n for modified Arrhenius |
| `setHeatOfReaction(double)` | Set ΔH_rxn [J/mol] (negative = exothermic) |
| `setReversible(boolean)` | Enable/disable reverse reaction |
| `setEquilibriumConstantCorrelation(a,b,c,d)` | Set ln(Keq) = a + b/T + c·ln(T) + d·T |
| `addAdsorptionTerm(name, Ki, order)` | Add LHHW denominator species |
| `setAdsorptionExponent(int)` | Set denominator power m |
| `calculateRate(SystemInterface, int)` | Calculate rate for a given fluid and phase |
| `calculateRateConstant(double T)` | Calculate k(T) |
| `calculateEquilibriumConstant(double T)` | Calculate Keq(T) |
| `getStoichiometry()` | Get stoichiometry map |
| `getStoichiometricCoefficient(name)` | Get coefficient for a species |

### CatalystBed

| Method | Description |
|--------|-------------|
| `CatalystBed()` | Default constructor (3mm, 0.40, 800 kg/m³) |
| `CatalystBed(dpMm, eps, rhoBulk)` | Convenience constructor |
| `setParticleDiameter(val, unit)` | "mm", "m" |
| `setVoidFraction(double)` | Bed porosity |
| `setBulkDensity(double)` | kg/m³ |
| `setParticleDensity(double)` | kg/m³ |
| `setParticlePorosity(double)` | Intra-particle porosity |
| `setTortuosity(double)` | Pore tortuosity |
| `setSpecificSurfaceArea(val, unit)` | "m2/kg" or "m2/g" |
| `setActivityFactor(double)` | 0 (dead) to 1 (fresh) |
| `calculatePressureDrop(u, rho, mu)` | Ergun dP/dz [Pa/m] |
| `calculateTotalPressureDrop(u, rho, mu, L)` | Total ΔP [bar] |
| `calculateThieleModulus(kv, Deff)` | Thiele modulus φ |
| `calculateEffectivenessFactor(phi)` | Effectiveness η |
| `getEffectiveDiffusivity(Dmol)` | D_eff [m²/s] |
| `calculateReynoldsNumber(u, rho, mu)` | Particle Reynolds number |

### PlugFlowReactor

| Method | Description |
|--------|-------------|
| `PlugFlowReactor(name)` | Constructor |
| `PlugFlowReactor(name, stream)` | Constructor with feed stream |
| `addReaction(KineticReaction)` | Add a kinetic reaction |
| `setCatalystBed(CatalystBed)` | Set packed bed (null for homogeneous) |
| `setLength(val, unit)` | "m", "cm", "ft" |
| `setDiameter(val, unit)` | "m", "mm", "cm", "in" |
| `setNumberOfTubes(int)` | Multi-tube configuration |
| `setEnergyMode(EnergyMode)` | ADIABATIC / ISOTHERMAL / COOLANT |
| `setCoolantTemperature(val, unit)` | "K", "C", "F" |
| `setOverallHeatTransferCoefficient(double)` | U [W/(m²·K)] |
| `setNumberOfSteps(int)` | Integration discretization |
| `setIntegrationMethod(String)` | "EULER" or "RK4" |
| `setPropertyUpdateFrequency(int)` | Re-flash every N steps |
| `setKeyComponent(String)` | Component for conversion tracking |
| `run()` | Execute simulation |
| `getConversion()` | Overall conversion [0-1] |
| `getAxialProfile()` | ReactorAxialProfile result object |
| `getHeatDuty(String unit)` | "W", "kW", "MW" |
| `getPressureDrop()` | ΔP [bar] |
| `getOutletTemperature()` | T_out [K] |
| `getResidenceTime()` | τ [s] |
| `getSpaceVelocity()` | GHSV [1/hr] |
| `getOutletStream()` | Outlet stream with updated fluid |

### ReactorAxialProfile

| Method | Description |
|--------|-------------|
| `getPositionProfile()` | double[] z [m] |
| `getTemperatureProfile()` | double[] T [K] |
| `getPressureProfile()` | double[] P [bara] |
| `getConversionProfile()` | double[] X [-] |
| `getReactionRateProfile()` | double[] r [mol/(m³·s)] |
| `getMolarFlowProfiles()` | double[][] Fi [mol/s] |
| `getComponentNames()` | String[] names |
| `getTemperatureAt(z)` | Interpolated T at position z |
| `getConversionAt(z)` | Interpolated X at position z |
| `getPressureAt(z)` | Interpolated P at position z |
| `toJson()` | Export as JSON string |
| `toCSV()` | Export as CSV string |

---

## Troubleshooting

### Zero or Negative Conversion

- **Cause:** Rate constant too small at the operating temperature, or activation energy too high.
- **Fix:** Check that $E_a$ is in J/mol (not kJ/mol). Verify the pre-exponential factor magnitude
  is appropriate for the rate basis (volume vs catalyst mass).

### Numerical Instability (NaN or Very Large Values)

- **Cause:** Time step too large for stiff kinetics, or very fast reactions with long reactor.
- **Fix:** Increase `numberOfSteps` (e.g., from 100 to 500) or switch to RK4.

### Flash Calculation Failures in Log

- **Cause:** Composition approaching trace levels or unphysical conditions.
- **Fix:** Increase `propertyUpdateFrequency` to reduce flash calls, or check that all
  product species exist in the thermodynamic database.

### Pressure Drop Too Large

- **Cause:** Small catalyst particles with high velocity and long bed.
- **Fix:** Increase particle diameter, increase void fraction, reduce reactor length,
  or add more parallel tubes.

### Product Components Not Appearing in Outlet

- **Cause:** Product species not in the original fluid definition.
- **Fix:** The reactor automatically adds missing product components to the system,
  but verify they exist in the NeqSim component database.

---

## Related Documentation

- [Reactor Models Overview](reactors.md) — All NeqSim reactor types
- [Gibbs Reactor](../../wiki/gibbs_reactor.md) — Chemical equilibrium reactor
- [Bio-Processing Reactors](../bioprocessing.md) — Fermenter and enzyme reactor models
- [PFR Implementation Plan](../PLUG_FLOW_REACTOR_IMPLEMENTATION_PLAN.md) — Design decisions and commercial comparison
- [Chemical Reactions](../../chemicalreactions/) — Reaction modeling background
