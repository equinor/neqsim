# Virtual Measurement of Emissions from Produced Water

## Overview

This tutorial describes how to use NeqSim to calculate greenhouse gas emissions (CO₂, methane, nmVOC) from produced water handling systems on offshore oil and gas facilities.

**Reference:** The methodology is based on ["Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator"](../GFMW_2023_Emissions_Paper.txt) presented at the Global Flow Measurement Workshop 2023.

> **For Engineers and Operators:** This guide is written to help you implement emissions calculations even if you're not familiar with thermodynamics. Each section includes copy-paste code examples and explains what each parameter means.

---

## ⚡ Get Started in 2 Minutes (Python)

```python
from neqsim.thermo import fluid
from neqsim import jNeqSim

# 1. Create produced water at separator conditions
water = fluid("cpa")                      # Use CPA equation of state
water.addComponent("water", 1.0)          # Produced water base
water.addComponent("CO2", 0.002)          # 0.2 mol% dissolved CO2
water.addComponent("methane", 0.001)      # 0.1 mol% dissolved methane
water.addComponent("ethane", 0.0002)      # Traces of ethane
water.setMixingRule(10)                   # CPA mixing rule for water systems
water.setTemperature(75.0, "C")           # Separator temperature
water.setPressure(65.0, "bara")           # Separator pressure

# 2. Flash to degasser pressure
from neqsim.thermodynamicoperations import TPflash
water.setPressure(4.0, "bara")            # Degasser at 4 bara
TPflash(water)

# 3. Calculate emissions
emissions_calculator = jNeqSim.processimulation.util.monitor.EmissionsCalculator(water)
print(f"CO2 emission:  {emissions_calculator.getCO2Emissions():.4f} kg/hr per m3/hr water")
print(f"CH4 emission:  {emissions_calculator.getMethanEmissions():.4f} kg/hr per m3/hr water")
print(f"nmVOC:         {emissions_calculator.getNmVocEmissions():.4f} kg/hr per m3/hr water")
print(f"CO2 equivalent: {emissions_calculator.getCO2Equivalent():.4f} kg CO2eq/hr")
```

**Need more detail?** Continue reading the full tutorial below, or jump to [Quick Start - Python](#quick-start-python) for a complete example.

---

## Quick Links

| Topic | Description |
|-------|-------------|
| [SolutionGasWaterRatio](../pvtsimulation/SolutionGasWaterRatio.md) | Gas solubility in water calculations |
| [CPA Equation of State](../thermo/CPA_EoS.md) | Thermodynamic model for polar systems |
| [Process Simulation](./index.md) | General process simulation examples |
| [Jupyter Notebook](./ProducedWaterEmissions_Tutorial.ipynb) | Runnable Python examples |

---

## Table of Contents

1. [Why Use NeqSim?](#why-use-neqsim-for-emissions-calculation)
2. [Key Concepts](#key-concepts-understanding-the-problem)
3. [Quick Start - Python](#quick-start-python)
4. [Quick Start - Java](#quick-start-java)
5. [Process Description](#process-description)
6. [Step-by-Step Implementation](#step-by-step-implementation)
7. [Accounting for Salinity](#accounting-for-salinity)
8. [Generalizing to Other Processes](#generalizing-to-other-emission-sources)
9. [Mathematical Background](#mathematical-background)
10. [Validation](#validation-and-calibration)
11. [Regulatory Requirements](#regulatory-requirements)
12. [Troubleshooting](#troubleshooting-common-issues)
13. [References](#references)

---

## Why Use NeqSim for Emissions Calculation?

### The Problem with Conventional Methods

The conventional method for emission reporting uses:
- Fixed gas solubility constants (e.g., 14 g/m³/bar for methane, 3.5 g/m³/bar for nmVOC)
- Annual produced water volumes
- **Assumes all emissions are hydrocarbons (no CO₂)**

### Problems Identified

| Issue | Impact |
|-------|--------|
| CO₂ not measured | Misses 72-78% of total gas emissions! |
| Fixed constants | Overestimates CH₄ by ~60% |
| No process conditions | Cannot reflect real variations |
| No salinity correction | Under/over estimates solubility |

### NeqSim Advantages

| Feature | Benefit |
|---------|---------|
| Rigorous thermodynamics (CPA-EoS) | Accurate water-gas equilibrium |
| Salinity effects | Accounts for "salting-out" |
| Individual components | Separates CO₂, CH₄, C₂+ |
| Real-time capable | Connect to process data systems |
| Low uncertainty | ±3.6% vs ±50%+ conventional |

---

## Key Concepts (Understanding the Problem)

### What are Produced Water Emissions?

```
                        ┌─────────────┐
  Reservoir Fluid  ──▶  │ Separator   │ ──▶ Oil/Gas
  (high pressure)       │ (65 bara)   │
                        └──────┬──────┘
                               │
                               ▼
                        Produced Water
                        (contains dissolved
                         CO₂, CH₄, C₂+)
                               │
         ┌─────────────────────┼─────────────────────┐
         ▼                     ▼                     ▼
   ┌───────────┐        ┌───────────┐        ┌───────────┐
   │ Degasser  │        │   CFU     │        │ Caisson   │
   │ (4 bara)  │        │ (1 bara)  │        │ (1 atm)   │
   └─────┬─────┘        └─────┬─────┘        └─────┬─────┘
         │                    │                    │
         ▼                    ▼                    ▼
     Released Gas         Released Gas         Released Gas
     (to flare)          (to flare)          (to atmosphere)
         │                    │                    │
         └────────────────────┴────────────────────┘
                              │
                              ▼
                    EMISSIONS (CO₂, CH₄, nmVOC)
```

### Key Terms

| Term | Definition | Unit |
|------|------------|------|
| **CO₂** | Carbon dioxide - main emission (72-78%) | kg/hr |
| **CH₄** | Methane - strong greenhouse gas (GWP=28) | kg/hr |
| **nmVOC** | Non-methane VOC (C₂+ hydrocarbons) | kg/hr |
| **GWP** | Global Warming Potential (100-year) | - |
| **CO₂eq** | CO₂ equivalent = CO₂ + CH₄×28 + nmVOC×2.2 | kg/hr |
| **CPA-EoS** | Cubic Plus Association - the thermodynamic model | - |
| **GWMF** | Gas-to-Water Mass Factor (g/m³/bar) | g/m³/bar |

## Process Description

A typical produced water degassing process includes:

```
Well Stream → 1st Stage Separator → Hydrocyclone → Water Degasser → CFU → Caisson → Sea
                      ↓                                  ↓           ↓
                 Gas to Process              Gas to Cold Flare  Gas to Atmosphere
```

**Key emission points:**
1. **Water Degasser** - Pressure reduced to 3-5 barg
2. **Compact Flotation Unit (CFU)** - Pressure reduced to 0.2-1 barg  
3. **Caisson** - Atmospheric pressure before discharge

---

## Quick Start - Python

> **For beginners:** Start here. Copy-paste this code to get your first emissions calculation.

### Installation

```bash
pip install neqsim pandas matplotlib
```

### Simple Example (5 minutes)

```python
# ============================================================
# PRODUCED WATER EMISSIONS - QUICK START
# ============================================================
# This calculates emissions when water is depressurized

from neqsim import jNeqSim

# ----- STEP 1: Create produced water fluid -----
# CPA equation of state handles water + gases accurately
fluid = jNeqSim.thermo.system.SystemSrkCPAstatoil(273.15 + 80, 30.0)

# Add components (adjust these to your facility)
# These are MOLE FRACTIONS - they must sum to 1.0
fluid.addComponent('water', 0.90)      # Produced water
fluid.addComponent('CO2', 0.03)        # Dissolved CO2
fluid.addComponent('methane', 0.05)    # Dissolved methane
fluid.addComponent('ethane', 0.015)    # Dissolved ethane
fluid.addComponent('propane', 0.005)   # Dissolved propane

# Set mixing rule (10 = CPA)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

# ----- STEP 2: Set process conditions -----
# Change these to match your separator/degasser
inlet_pressure = 30.0  # bara - separator outlet
degasser_pressure = 4.0  # bara - degasser pressure
temperature = 80.0  # °C

# ----- STEP 3: Flash to degasser pressure -----
fluid.setPressure(degasser_pressure, 'bara')
fluid.setTotalFlowRate(100000, 'kg/hr')  # 100 tonnes/hr water

# Run thermodynamic flash calculation
thermoOps = jNeqSim.thermodynamicoperations.ThermodynamicOperations(fluid)
thermoOps.TPflash()
fluid.initProperties()

# ----- STEP 4: Get emissions -----
if fluid.hasPhaseType('gas'):
    gas = fluid.getPhase('gas')
    
    # Individual emissions (kg/hr)
    co2 = gas.getComponent('CO2').getFlowRate('kg/hr')
    ch4 = gas.getComponent('methane').getFlowRate('kg/hr')
    c2 = gas.getComponent('ethane').getFlowRate('kg/hr')
    c3 = gas.getComponent('propane').getFlowRate('kg/hr')
    nmvoc = c2 + c3  # non-methane VOC
    
    # CO2 equivalents (kg/hr)
    # GWP: CO2=1, CH4=28, nmVOC≈2.2
    co2eq = co2 + ch4 * 28 + nmvoc * 2.2
    
    print("=== EMISSIONS RESULTS ===")
    print(f"CO2:        {co2:.1f} kg/hr")
    print(f"Methane:    {ch4:.1f} kg/hr")
    print(f"nmVOC:      {nmvoc:.1f} kg/hr")
    print(f"CO2eq:      {co2eq:.1f} kg/hr")
    print(f"Annual:     {co2eq * 8760 / 1000:.0f} tonnes CO2eq/year")
else:
    print("No gas released (check pressure drop)")
```

### What Each Parameter Means

| Parameter | What to Set | How to Find It |
|-----------|-------------|----------------|
| `water` mole fraction | 0.85-0.95 | From water cut + gas analysis |
| `CO2` mole fraction | 0.01-0.10 | From reservoir gas analysis |
| `methane` mole fraction | 0.02-0.10 | From reservoir gas analysis |
| `inlet_pressure` | 20-100 bara | Separator pressure tag |
| `degasser_pressure` | 2-6 bara | Degasser pressure tag |
| `temperature` | 50-100 °C | Process temperature tag |
| `flow_rate` | varies | Produced water meter |

---

## Quick Start - Java

### Basic Flash Calculation

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create fluid representing produced water with dissolved gas
// Using CPA-EoS for accurate water-gas equilibrium
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(273.15 + 80.0, 65.0); // 80°C, 65 bara

// Add components (mole fractions from reservoir fluid)
fluid.addComponent("water", 0.85);        // Dominant component
fluid.addComponent("CO2", 0.03);          // Important for emissions!
fluid.addComponent("methane", 0.08);      // Main hydrocarbon
fluid.addComponent("ethane", 0.02);       // nmVOC
fluid.addComponent("propane", 0.01);      // nmVOC
fluid.addComponent("n-butane", 0.005);    // nmVOC
fluid.addComponent("i-butane", 0.005);    // nmVOC

// Set CPA mixing rule (rule 10 for CPA)
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);

// Set flow rate
fluid.setTotalFlowRate(100.0, "kg/hr");

// Run flash at separator conditions
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Get gas phase emissions
if (fluid.hasPhaseType("gas")) {
    double gasFlowRate = fluid.getPhase("gas").getFlowRate("kg/hr");
    double co2Flow = fluid.getPhase("gas").getComponent("CO2").getFlowRate("kg/hr");
    double ch4Flow = fluid.getPhase("gas").getComponent("methane").getFlowRate("kg/hr");
    
    System.out.println("Gas emission rate: " + gasFlowRate + " kg/hr");
    System.out.println("CO2 emission: " + co2Flow + " kg/hr");
    System.out.println("Methane emission: " + ch4Flow + " kg/hr");
}
```

---

## Step-by-Step Implementation

### Complete Multi-Stage Process Simulation (Java)

```java
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public class ProducedWaterEmissionsExample {
    
    public static void main(String[] args) {
        // =====================================================
        // STEP 1: Define the reservoir fluid
        // =====================================================
        
        // Use CPA-EoS for water-hydrocarbon-CO2 systems
        SystemSrkCPAstatoil reservoirFluid = new SystemSrkCPAstatoil(273.15 + 80.0, 67.0);
        
        // Typical high-CO2 reservoir composition (mole fractions)
        reservoirFluid.addComponent("water", 0.129);      // ~10-11 wt% salinity water
        reservoirFluid.addComponent("nitrogen", 0.002);
        reservoirFluid.addComponent("CO2", 0.052);        // High CO2 content
        reservoirFluid.addComponent("methane", 0.642);
        reservoirFluid.addComponent("ethane", 0.062);
        reservoirFluid.addComponent("propane", 0.030);
        reservoirFluid.addComponent("i-butane", 0.004);
        reservoirFluid.addComponent("n-butane", 0.011);
        reservoirFluid.addComponent("i-pentane", 0.004);
        reservoirFluid.addComponent("n-pentane", 0.005);
        
        // Add heavier fractions as pseudo-components
        reservoirFluid.addTBPfraction("C6", 0.006, 86.0/1000, 0.664);
        reservoirFluid.addTBPfraction("C7", 0.010, 95.0/1000, 0.738);
        reservoirFluid.addTBPfraction("C8", 0.011, 106.0/1000, 0.765);
        reservoirFluid.addTBPfraction("C9", 0.006, 120.0/1000, 0.781);
        reservoirFluid.addTBPfraction("C10+", 0.026, 200.0/1000, 0.830);
        
        // Set CPA mixing rule
        reservoirFluid.setMixingRule(10);
        reservoirFluid.setMultiPhaseCheck(true);
        
        // =====================================================
        // STEP 2: Create process streams and equipment
        // =====================================================
        
        // Inlet stream to first stage separator
        Stream inletStream = new Stream("Well Stream", reservoirFluid);
        inletStream.setTemperature(79.4, "C");
        inletStream.setPressure(67.0, "bara");
        inletStream.setFlowRate(273050.0, "kg/hr");
        
        // First stage separator (3-phase)
        ThreePhaseSeparator firstStageSeparator = 
            new ThreePhaseSeparator("1st Stage Separator", inletStream);
        
        // Hydrocyclone (modeled as TP change + separator)
        Heater hydrocycloneTPSetter = new Heater("Hydrocyclone TP Setter", 
            firstStageSeparator.getWaterOutStream());
        hydrocycloneTPSetter.setOutPressure(30.0, "bara");
        
        ThreePhaseSeparator hydrocyclone = 
            new ThreePhaseSeparator("Hydrocyclone", hydrocycloneTPSetter.getOutletStream());
        
        // Water Degasser (main emission point 1)
        Heater degasserTPSetter = new Heater("Degasser TP Setter", 
            hydrocyclone.getWaterOutStream());
        degasserTPSetter.setOutPressure(4.0, "bara");  // Flash to ~4 barg
        degasserTPSetter.setOutTemperature(77.0, "C");
        
        ThreePhaseSeparator waterDegasser = 
            new ThreePhaseSeparator("Water Degasser", degasserTPSetter.getOutletStream());
        
        // CFU - Compact Flotation Unit (emission point 2)
        Heater cfuTPSetter = new Heater("CFU TP Setter", 
            waterDegasser.getWaterOutStream());
        cfuTPSetter.setOutPressure(1.0, "bara");  // Near atmospheric
        
        ThreePhaseSeparator cfu = 
            new ThreePhaseSeparator("CFU", cfuTPSetter.getOutletStream());
        
        // Caisson - final atmospheric flash (emission point 3)
        Heater caissonTPSetter = new Heater("Caisson TP Setter", 
            cfu.getWaterOutStream());
        caissonTPSetter.setOutPressure(1.01325, "bara");  // Atmospheric
        
        ThreePhaseSeparator caisson = 
            new ThreePhaseSeparator("Caisson", caissonTPSetter.getOutletStream());
        
        // =====================================================
        // STEP 3: Build and run process system
        // =====================================================
        
        ProcessSystem process = new ProcessSystem();
        process.add(inletStream);
        process.add(firstStageSeparator);
        process.add(hydrocycloneTPSetter);
        process.add(hydrocyclone);
        process.add(degasserTPSetter);
        process.add(waterDegasser);
        process.add(cfuTPSetter);
        process.add(cfu);
        process.add(caissonTPSetter);
        process.add(caisson);
        
        process.run();
        
        // =====================================================
        // STEP 4: Calculate emissions from each source
        // =====================================================
        
        System.out.println("\n=== PRODUCED WATER EMISSIONS REPORT ===\n");
        
        // Calculate emissions from each degassing stage
        calculateEmissions("Water Degasser", waterDegasser);
        calculateEmissions("CFU", cfu);
        calculateEmissions("Caisson", caisson);
        
        // Calculate totals
        double totalCO2 = 0, totalCH4 = 0, totalNMVOC = 0;
        
        for (ThreePhaseSeparator sep : 
                new ThreePhaseSeparator[]{waterDegasser, cfu, caisson}) {
            if (sep.getGasOutStream().getFluid().hasPhaseType("gas")) {
                totalCO2 += getComponentFlow(sep, "CO2");
                totalCH4 += getComponentFlow(sep, "methane");
                totalNMVOC += getNMVOCFlow(sep);
            }
        }
        
        System.out.println("\n=== TOTAL COLD FLARE EMISSIONS ===");
        System.out.printf("Total CO2:     %.2f kg/hr (%.2f tonnes/year)%n", 
            totalCO2, totalCO2 * 8760 / 1000);
        System.out.printf("Total Methane: %.2f kg/hr (%.2f tonnes/year)%n", 
            totalCH4, totalCH4 * 8760 / 1000);
        System.out.printf("Total nmVOC:   %.2f kg/hr (%.2f tonnes/year)%n", 
            totalNMVOC, totalNMVOC * 8760 / 1000);
        
        // Calculate CO2 equivalents
        // GWP: CH4 = 28, nmVOC ≈ 2.2
        double co2Equiv = totalCO2 + totalCH4 * 28 + totalNMVOC * 2.2;
        System.out.printf("%nTotal CO2 Equivalents: %.2f kg/hr (%.2f tonnes/year)%n", 
            co2Equiv, co2Equiv * 8760 / 1000);
    }
    
    private static void calculateEmissions(String name, ThreePhaseSeparator separator) {
        if (!separator.getGasOutStream().getFluid().hasPhaseType("gas")) {
            System.out.printf("%s: No gas phase%n", name);
            return;
        }
        
        var gasPhase = separator.getGasOutStream().getFluid().getPhase("gas");
        
        System.out.printf("%s Emissions:%n", name);
        System.out.printf("  Total gas:  %.4f kg/hr%n", gasPhase.getFlowRate("kg/hr"));
        System.out.printf("  CO2:        %.4f kg/hr%n", 
            gasPhase.getComponent("CO2").getFlowRate("kg/hr"));
        System.out.printf("  Methane:    %.4f kg/hr%n", 
            gasPhase.getComponent("methane").getFlowRate("kg/hr"));
        System.out.printf("  nmVOC:      %.4f kg/hr%n", getNMVOCFlow(separator));
        System.out.println();
    }
    
    private static double getComponentFlow(ThreePhaseSeparator sep, String component) {
        return sep.getGasOutStream().getFluid().getPhase("gas")
            .getComponent(component).getFlowRate("kg/hr");
    }
    
    private static double getNMVOCFlow(ThreePhaseSeparator sep) {
        var gasPhase = sep.getGasOutStream().getFluid().getPhase("gas");
        double nmvoc = 0;
        
        // nmVOC = C2+ hydrocarbons (excluding methane)
        String[] nmvocComponents = {"ethane", "propane", "i-butane", "n-butane", 
                                    "i-pentane", "n-pentane", "C6", "C7", "C8", "C9", "C10+"};
        
        for (String comp : nmvocComponents) {
            if (gasPhase.hasComponent(comp)) {
                nmvoc += gasPhase.getComponent(comp).getFlowRate("kg/hr");
            }
        }
        return nmvoc;
    }
}
```

## Accounting for Salinity

Produced water typically contains 10-11 wt% NaCl. Salinity reduces gas solubility ("salting-out effect"), which affects emissions calculations.

### Method 1: Using Binary Interaction Parameters

The CPA-EoS can account for salinity by tuning binary interaction parameters:

```java
// Tuned kij values for 10-11 wt% NaCl brine (from GFMW 2023 paper)
// kij = kij,0 + kij,T * (T - 288.15)

// These parameters improve gas solubility predictions in saline water:
// CO2:     kij,0 = -0.24,  kij,T = 0.001121
// Methane: kij,0 = -0.72,  kij,T = 0.002605
// Ethane:  kij,0 = 0.11,   kij,T = 0
// Propane: kij,0 = 0.205,  kij,T = 0
// i-Butane:kij,0 = 0.081,  kij,T = 0
// n-Butane:kij,0 = 0.17,   kij,T = 0
```

### Method 2: Using Electrolyte CPA

For explicit salt modeling:

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

// Create electrolyte system
SystemElectrolyteCPAstatoil fluid = 
    new SystemElectrolyteCPAstatoil(273.15 + 80.0, 65.0);

// Add components
fluid.addComponent("water", 100.0);       // 100 moles water
fluid.addComponent("CO2", 1.0);
fluid.addComponent("methane", 2.0);

// Add NaCl as ions (10 wt% ≈ 1.7 molal)
double molesWater = 100.0;
double kgWater = molesWater * 18.015 / 1000.0;
double molality = 1.7;  // mol NaCl per kg water
double molesNaCl = molality * kgWater;

fluid.addComponent("Na+", molesNaCl);
fluid.addComponent("Cl-", molesNaCl);

fluid.createDatabase(true);
fluid.setMixingRule(10);
```

### Method 3: Using SolutionGasWaterRatio Class

For gas solubility calculations with salinity:

```java
import neqsim.pvtsimulation.simulation.SolutionGasWaterRatio;
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Define reservoir gas composition
SystemSrkCPAstatoil gas = new SystemSrkCPAstatoil(350.0, 100.0);
gas.addComponent("methane", 0.85);
gas.addComponent("CO2", 0.10);
gas.addComponent("ethane", 0.05);
gas.setMixingRule(10);

// Create Rsw calculator
SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);

// Set conditions
rswCalc.setTemperaturesAndPressures(
    new double[]{350.0},  // Temperature in K
    new double[]{65.0}    // Pressure in bara
);

// Set salinity (1.7 molal ≈ 10 wt% NaCl)
rswCalc.setSalinity(1.7);

// Use Electrolyte CPA method for best accuracy with salinity
rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
rswCalc.runCalc();

double rsw = rswCalc.getRsw(0);  // Sm³ gas / Sm³ water
System.out.printf("GWR at separator conditions: %.4f Sm³/Sm³%n", rsw);
```

---

## Mathematical Background

This section provides the theoretical basis for the calculations. You don't need to understand this to use NeqSim, but it helps for validation and troubleshooting.

### Thermodynamic Foundation

#### Phase Equilibrium

Gas release from water is governed by vapor-liquid equilibrium:

$$f_i^V = f_i^L$$

Where $f_i$ is the fugacity of component $i$ in vapor (V) and liquid (L) phases.

For practical calculations:
$$y_i \phi_i^V P = x_i \gamma_i H_i$$

Where:
- $y_i$ = mole fraction in gas phase
- $x_i$ = mole fraction in liquid phase  
- $\phi_i^V$ = vapor phase fugacity coefficient
- $\gamma_i$ = liquid phase activity coefficient
- $H_i$ = Henry's law constant

#### CPA Equation of State

NeqSim uses the **Cubic-Plus-Association** (CPA) equation of state, which combines:
- SRK cubic equation for physical interactions
- Association term for hydrogen bonding (important for water)

$$P = \frac{RT}{V_m - b} - \frac{a(T)}{V_m(V_m + b)} + P_{assoc}$$

The association term $P_{assoc}$ accounts for water's self-association and cross-association with CO₂.

#### Salinity Effect (Salting-Out)

Dissolved salts reduce gas solubility. The Setschenow equation describes this:

$$\log\left(\frac{H_{salt}}{H_{pure}}\right) = K_s \cdot m_{salt}$$

Where:
- $K_s$ = Setschenow coefficient (component-specific)
- $m_{salt}$ = salt molality (mol/kg water)

In CPA, this is modeled through adjusted binary interaction parameters $k_{ij}$:

$$k_{ij} = k_{ij,0} + k_{ij,T} \cdot (T - 288.15)$$

### CO₂ Equivalent Calculation

Total greenhouse gas impact uses GWP-100 weighting:

$$\text{CO}_2\text{eq} = \dot{m}_{CO_2} + \dot{m}_{CH_4} \times GWP_{CH_4} + \dot{m}_{nmVOC} \times GWP_{nmVOC}$$

Standard GWP-100 values (IPCC AR5):
| Gas | GWP-100 |
|-----|---------|
| CO₂ | 1 |
| CH₄ | 28 |
| nmVOC | ~2.2 (average) |

### Uncertainty Propagation

For combined standard uncertainty:

$$u_c(y) = \sqrt{\sum_i \left(\frac{\partial y}{\partial x_i}\right)^2 u^2(x_i)}$$

Key uncertainty contributors:
| Source | Typical Uncertainty |
|--------|---------------------|
| Water flow measurement | ±2% |
| Temperature | ±0.5°C |
| Pressure | ±0.5% |
| Composition | ±5% (from PVT analysis) |
| Thermodynamic model | ±2% (for CPA) |

Combined uncertainty for total gas: **±3.6%**

### GWMF Calculation

Gas-to-Water Mass Factor for comparing with conventional methods:

$$GWMF = \frac{\dot{m}_{gas} \times 1000}{Q_{water} \times \Delta P}$$

Where:
- $\dot{m}_{gas}$ = gas mass flow (kg/hr)
- $Q_{water}$ = water volumetric flow (m³/hr)
- $\Delta P$ = pressure drop (bar)
- Units: g/m³/bar

---

## Calculating Emissions Factors

### Gas-to-Water Mass Factor (GWMF)

```java
/**
 * Calculate GWMF for comparison with conventional method
 * GWMF = g gas / m³ water / bar pressure drop
 */
public static double calculateGWMF(ThreePhaseSeparator separator, 
                                   double pressureDrop_bar) {
    
    double gasFlowKgHr = separator.getGasOutStream().getFlowRate("kg/hr") * 1000; // g/hr
    double waterFlowM3Hr = separator.getWaterOutStream().getFlowRate("m3/hr");
    
    return gasFlowKgHr / waterFlowM3Hr / pressureDrop_bar;  // g/m³/bar
}

// Typical values from literature (vs conventional method):
// Component    | NeqSim      | Conventional
// -------------|-------------|-------------
// Methane      | 5-6 g/m³/bar| 14 g/m³/bar
// nmVOC        | 1.2-1.4     | 3.5 g/m³/bar
// CO2          | 15-30       | Not reported!
```

---

## Generalizing to Other Emission Sources

The same methodology can be applied to **any process** where gas is released from a liquid:

### 1. TEG Dehydration Emissions

TEG (Triethylene Glycol) regeneration releases absorbed gases. This is a significant emission source.

**Emission Points:**
- Flash separator (HP flash)
- Regenerator overhead vapor
- Stripping gas vent

```python
# Python example for TEG flash emissions
from neqsim import jNeqSim

# Rich TEG composition after absorption
teg_fluid = jNeqSim.thermo.system.SystemSrkCPAstatoil(273.15 + 35, 52.0)

# TEG with absorbed water and hydrocarbons
teg_fluid.addComponent('TEG', 0.95)
teg_fluid.addComponent('water', 0.03)
teg_fluid.addComponent('methane', 0.015)  # Absorbed in contactor
teg_fluid.addComponent('CO2', 0.005)      # Absorbed CO2

teg_fluid.setMixingRule(10)
teg_fluid.setMultiPhaseCheck(True)

# Flash to regenerator feed pressure
teg_fluid.setPressure(4.0, 'bara')
teg_fluid.setTotalFlowRate(6000, 'kg/hr')  # Rich TEG flow

thermoOps = jNeqSim.thermodynamicoperations.ThermodynamicOperations(teg_fluid)
thermoOps.TPflash()
teg_fluid.initProperties()

if teg_fluid.hasPhaseType('gas'):
    gas = teg_fluid.getPhase('gas')
    print(f"TEG Flash Emissions:")
    print(f"  Methane: {gas.getComponent('methane').getFlowRate('kg/hr'):.2f} kg/hr")
    print(f"  CO2:     {gas.getComponent('CO2').getFlowRate('kg/hr'):.2f} kg/hr")
```

**See Also:** The TEG test classes in NeqSim (`TEGdehydrationProcess*.java`) provide comprehensive examples.

### 2. Crude Oil Storage Tank Emissions

Stabilized crude still contains dissolved gas that flashes in storage tanks.

```python
# Tank breathing emissions calculation
oil_fluid = jNeqSim.thermo.system.SystemSrkEos(273.15 + 40, 1.5)  # Tank pressure

oil_fluid.addComponent('methane', 0.002)
oil_fluid.addComponent('ethane', 0.005)
oil_fluid.addComponent('propane', 0.01)
oil_fluid.addComponent('nC10', 0.983)  # Crude proxy

oil_fluid.setMixingRule(2)
oil_fluid.setMultiPhaseCheck(True)
oil_fluid.setTotalFlowRate(500, 'm3/hr')

thermoOps = jNeqSim.thermodynamicoperations.ThermodynamicOperations(oil_fluid)
thermoOps.TPflash()
oil_fluid.initProperties()

# VOC emissions from tank vapor space
```

### 3. Loading/Offloading Operations

Marine loading of crude oil or condensate releases VOCs.

### Comparison of Emission Sources

| Source | Typical Components | Key Consideration |
|--------|-------------------|-------------------|
| Produced Water | CO₂, CH₄, C₂+ | Salinity effect on solubility |
| TEG Regeneration | CH₄, H₂O, BTEX | High temperature regeneration |
| Crude Storage | C₁-C₆, BTEX | Tank pressure, temperature |
| Condensate Loading | C₃-C₈ | Working + breathing losses |
| Glycol Flash | CH₄, CO₂ | Contactor pressure |

---

## Online Implementation (NeqSimLive Pattern)

For real-time emissions monitoring:

```java
public class ProducedWaterEmissionsMonitor {
    
    private ProcessSystem process;
    private ThreePhaseSeparator degasser;
    
    // Cumulative emissions tracking
    private double cumulativeCO2_tonnes = 0.0;
    private double cumulativeCH4_tonnes = 0.0;
    private double cumulativeNMVOC_tonnes = 0.0;
    
    public void initialize() {
        // Build process model (as shown above)
        // ...
    }
    
    /**
     * Update model with new process data
     * Called every 5-15 minutes from SIGMA/OPC connector
     */
    public void updateFromProcessData(double waterFlowRate_kghr,
                                       double temperature_C,
                                       double pressure_bara,
                                       double timeStep_hours) {
        
        // Update inlet conditions
        process.getUnit("Well Stream").setFlowRate(waterFlowRate_kghr, "kg/hr");
        // ... update other conditions
        
        // Run simulation
        process.run();
        
        // Calculate instantaneous emissions
        double co2_kghr = getComponentFlow(degasser, "CO2");
        double ch4_kghr = getComponentFlow(degasser, "methane");
        double nmvoc_kghr = getNMVOCFlow(degasser);
        
        // Update cumulative values
        cumulativeCO2_tonnes += co2_kghr * timeStep_hours / 1000.0;
        cumulativeCH4_tonnes += ch4_kghr * timeStep_hours / 1000.0;
        cumulativeNMVOC_tonnes += nmvoc_kghr * timeStep_hours / 1000.0;
        
        // Write to PI/Aspen tags
        writeToTag("EMISSIONS.CO2.INSTANT", co2_kghr);
        writeToTag("EMISSIONS.CH4.INSTANT", ch4_kghr);
        writeToTag("EMISSIONS.NMVOC.INSTANT", nmvoc_kghr);
        writeToTag("EMISSIONS.CO2.CUMULATIVE", cumulativeCO2_tonnes);
        // ... etc
    }
    
    public double getCO2Equivalents_tonnes() {
        return cumulativeCO2_tonnes + 
               cumulativeCH4_tonnes * 28.0 +   // GWP of methane
               cumulativeNMVOC_tonnes * 2.2;   // GWP of nmVOC
    }
}
```

## Validation and Calibration

### Comparing with Water Samples

```java
/**
 * Validate model against laboratory water sample analysis
 */
public void validateAgainstWaterSample(double labGWR,         // Sm³/Sm³
                                       double labCO2_molpct,
                                       double labCH4_molpct) {
    
    // Run flash at sample conditions (atmospheric, 15°C)
    // ...
    
    double modelGWR = calculateGWR();
    double modelCO2 = getGasPhaseMoleFraction("CO2") * 100;
    double modelCH4 = getGasPhaseMoleFraction("methane") * 100;
    
    System.out.println("Validation Results:");
    System.out.printf("GWR - Lab: %.3f, Model: %.3f, Dev: %.1f%%%n",
        labGWR, modelGWR, (modelGWR - labGWR) / labGWR * 100);
    System.out.printf("CO2 - Lab: %.2f%%, Model: %.2f%%, Dev: %.1f%%%n",
        labCO2_molpct, modelCO2, (modelCO2 - labCO2_molpct) / labCO2_molpct * 100);
    System.out.printf("CH4 - Lab: %.2f%%, Model: %.2f%%, Dev: %.1f%%%n",
        labCH4_molpct, modelCH4, (modelCH4 - labCH4_molpct) / labCH4_molpct * 100);
}
```

### Recommended Calibration Frequency

- **Normal operation**: Every 6 months (with water sample analysis)
- **After well changes**: When CO₂-rich wells started/stopped
- **After salinity changes**: If freshwater injection affects composition

## Uncertainty Estimation

Based on the GFMW 2023 paper methodology:

| Component | Typical Uncertainty |
|-----------|---------------------|
| Total gas | ±3.6% |
| CO₂ | ±3.6% |
| Methane | ±7.4% |
| nmVOC | ±38% (higher due to low concentrations) |

## Regulatory Requirements

### Norwegian Continental Shelf (Aktivitetsforskriften)

> **Key Regulation:** [Aktivitetsforskriften (Activities Regulations) §70](https://www.lovdata.no/dokument/SF/forskrift/2010-04-29-613) mandates measurement or calculation of emissions to air and sea with quality-assured methods.

#### Measurement and Calculation Requirements (§70)

According to Aktivitetsforskriften §70:

1. **Measurement/Calculation Obligation:** Operators must measure or calculate quantities of emissions to air and sea, as well as quantities of energy used and waste generated.

2. **Quality Assurance:** The methods used must be quality-assured and representative.

3. **Sampling and Analysis:** Sampling and analysis of waste and emissions must be representative.

4. **Indirect Calculations:** When emissions cannot be measured directly, calculations from process measurements may be used.

#### Norwegian Offshore Emission Handbook

The conventional method uses empirical solubility factors:

| Component | Factor (f) | Unit |
|-----------|------------|------|
| Methane (CH₄) | 14.0 | g/m³/bar |
| nmVOC (C₂+) | 3.5 | g/m³/bar |

**Conventional Formula:**
```
Annual_emission = f × V_annual × ΔP
```
Where:
- `f` = solubility factor (g/m³/bar)
- `V_annual` = annual produced water volume (m³/year)
- `ΔP` = pressure drop from separator to discharge (bar)

**Limitations of Conventional Method:**
- **Does NOT account for CO₂** (which is typically 72-78% of total emissions!)
- Fixed factors do not reflect actual process conditions
- Uncertainty estimated at ±50% or more
- No temperature dependence
- No salinity correction

#### Thermodynamic Method vs Conventional Method

NeqSim provides a thermodynamic-based calculation that addresses these limitations:

```python
# Compare thermodynamic vs conventional methods
from neqsim import jNeqSim

# Create and configure system
system = jNeqSim.process.equipment.util.ProducedWaterDegassingSystem("Comparison Test")
system.setWaterFlowRate(100000, "kg/hr")
system.setTemperature(80, "C")
system.setInletPressure(65, "bara")
system.setDegasserPressure(4.0, "bara")
system.setCFUPressure(1.2, "bara")
system.setCaissonPressure(1.013, "bara")

# Set dissolved gas composition
system.setDissolvesGasComposition({"CO2": 0.03, "methane": 0.05, "ethane": 0.015, "propane": 0.005})
system.setSalinity(3.5, "wt%")

# Build and run
system.build()
system.run()

# Get comparison report
print(system.getMethodComparisonReport())
```

**Example Output:**
```
╔════════════════════════════════════════════════════════════════╗
║   METHOD COMPARISON: Thermodynamic vs Conventional Handbook     ║
╠════════════════════════════════════════════════════════════════╣

  BASIS:
    Annual water volume:   876000 m³/year
    Pressure drop:         64.0 bar
    Handbook factors:      f_CH4=14.0, f_nmVOC=3.5 g/m³/bar

  ANNUAL EMISSIONS (tonnes/year):
    ─────────────────────────────────────────────────────────────
    Component       Thermodynamic     Conventional     Difference
    ─────────────────────────────────────────────────────────────
    CO2                   245.3              0.0       +100%
    Methane (CH4)          35.2            785.0       -95%
    nmVOC (C2+)            18.5            196.2       -91%
    ─────────────────────────────────────────────────────────────
    CO2 Equivalents      1273.8          22415.3       -94%

  KEY FINDINGS:
    • CO2 represents 82% of total gas emissions
    • Conventional method COMPLETELY MISSES this CO2!
    • CO2eq reduced by 94% using thermodynamic method

  REGULATORY NOTE:
    Per Aktivitetsforskriften §70, thermodynamic calculations
    provide more accurate emission quantification than empirical
    solubility factors. Uncertainty: ±3.6% vs ±50%+ conventional.

╚════════════════════════════════════════════════════════════════╝
```

#### Regulatory Tolerance

| Parameter | Tolerance | Source |
|-----------|-----------|--------|
| Emission quantities | ±7.5% | Norwegian Offshore Emission Handbook |
| Measurement uncertainty | Quality-assured | Aktivitetsforskriften §70 |
| PVT model validation | Compare with lab GWR | Industry best practice |

#### Using Tuned Binary Interaction Parameters

For improved accuracy, use laboratory-calibrated kij parameters:

```python
# Enable tuned binary interaction parameters per Kristiansen et al. (2023)
system.setTunedInteractionParameters(True)

# Validate against laboratory Gas-to-Water Ratio
system.setLabGWR(0.85)  # Sm³ gas / Sm³ water from PVT lab

# Run and check validation
system.run()
validation = system.getValidationResults()
print(f"GWR deviation: {validation['gwrDeviation_pct']:.1f}%")
print(f"Within tolerance: {validation['withinTolerance']}")
```

**Tuned kij Parameters (CPA-EoS):**

| System | kij Formula | Reference |
|--------|-------------|-----------|
| Water-CO₂ | kij = -0.24 + 0.001121 × T(°C) | Kristiansen et al. (2023) |
| Water-CH₄ | kij = -0.72 + 0.002605 × T(°C) | Kristiansen et al. (2023) |
| Water-C₂H₆ | kij = 0.11 (fixed) | Literature correlation |
| Water-C₃H₈ | kij = 0.205 (fixed) | Literature correlation |

### UK North Sea
- EEMS (Environmental Emissions Monitoring System) reporting
- Annual PRTR submissions

### US (EPA)
- 40 CFR Part 98 Subpart W (Petroleum and Natural Gas Systems)
- Annual GHG reporting for facilities >25,000 tonnes CO₂eq/year

### CO₂ Equivalent Factors (GWP-100)

| Gas | GWP-100 | Notes |
|-----|---------|-------|
| CO₂ | 1 | Reference |
| Methane (CH₄) | 28 | IPCC AR5 (2013) |
| nmVOC | ~2.2 | Varies by composition |

---

## Troubleshooting Common Issues

### No Gas Phase Formed

**Symptom:** `fluid.hasPhaseType('gas')` returns `false`

**Causes and Solutions:**

| Cause | Solution |
|-------|----------|
| Pressure too high | Lower the degasser pressure or increase temperature |
| Insufficient dissolved gas | Check input composition - water may be undersaturated |
| Multi-phase check disabled | Add `fluid.setMultiPhaseCheck(True)` |
| Wrong mixing rule | Use mixing rule 10 for CPA: `fluid.setMixingRule(10)` |

### Unrealistic Emission Values

**Symptom:** Values too high or too low compared to field data

**Check:**
1. **Units:** Flow rates in kg/hr vs m³/hr
2. **Composition:** Mole fractions should sum to ~1.0
3. **Pressure units:** bara vs barg
4. **Temperature:** Kelvin vs Celsius

### Model vs Field Discrepancy

**Expected deviation:** ±5-10% is acceptable

**If deviation > 15%:**
1. Verify process conditions (P, T, flow)
2. Get updated PVT analysis
3. Check for process changes (new wells, water injection)
4. Validate salinity assumption

### Performance Issues (Slow Calculation)

**For batch processing:**
```python
# Reuse fluid object instead of creating new ones
fluid.setPressure(new_pressure, 'bara')
thermoOps.TPflash()
# Don't recreate SystemSrkCPAstatoil each iteration
```

---

## References

### Primary Literature

1. **GFMW 2023 Paper:** "Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator" - *Global Flow Measurement Workshop 2023*

2. **Søreide & Whitson (1992):** "Peng-Robinson predictions for hydrocarbons, CO2, N2 and H2S with pure water and NaCl brine", *Fluid Phase Equilibria*, 77, 217-240

3. **Kontogeorgis et al. (2006):** "Ten Years with CPA Equation of State", *Industrial & Engineering Chemistry Research*, 45(14), 4855-4868

4. **Yan et al. (2011):** "Representation of CO2 and H2S solubility in aqueous MDEA solutions using an extended uniquac model", *Chemical Engineering Science*

### Standards and Regulations

5. **Norwegian Environment Agency:** Handbook for VOC Emissions (ver 20, 2022)

6. **IPCC AR5 (2014):** Global Warming Potentials - *Climate Change 2014: Synthesis Report*

7. **ISO 14064-1:** Specification with guidance for quantification of GHG emissions and removals

### NeqSim Resources

8. **NeqSim Documentation:** https://equinor.github.io/neqsimhome/

9. **NeqSim GitHub:** https://github.com/equinor/neqsim

10. **NeqSim Python Package:** https://github.com/equinor/neqsim-python

---

## See Also

- [SolutionGasWaterRatio Documentation](../pvtsimulation/SolutionGasWaterRatio.md) - Gas solubility calculations
- [Thermodynamic Models](../thermo/thermodynamic_models.md) - CPA and other EoS models
- [Process Simulation Examples](./index.md) - General process examples
- [Jupyter Notebook Tutorial](./ProducedWaterEmissions_Tutorial.ipynb) - Runnable Python notebook
- [Flash Calculations Guide](../thermo/flash_calculations_guide.md) - Detailed flash calculation theory

---

## Appendix: Quick Reference Card

### Common Commands (Python)

```python
# Create CPA fluid
fluid = jNeqSim.thermo.system.SystemSrkCPAstatoil(T_kelvin, P_bara)

# Add components
fluid.addComponent('component_name', mole_fraction)

# Configure
fluid.setMixingRule(10)  # CPA mixing rule
fluid.setMultiPhaseCheck(True)
fluid.setTotalFlowRate(value, 'kg/hr')

# Flash calculation
ops = jNeqSim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

# Get gas phase
if fluid.hasPhaseType('gas'):
    gas = fluid.getPhase('gas')
    flow = gas.getComponent('CO2').getFlowRate('kg/hr')
```

### GWP-100 Values (IPCC AR5)

| Gas | GWP-100 | Use |
|-----|---------|-----|
| CO₂ | 1 | Reference |
| CH₄ | 28 | Methane |
| C₂H₆ | 5.5 | Ethane |
| C₃H₈ | 3.3 | Propane |
| nmVOC avg | 2.2 | C₂+ mix |

### Unit Conversions

| From | To | Multiply by |
|------|-----|------------|
| kg/hr | tonnes/year | 8.76 |
| kg/hr | kg/day | 24 |
| bara | barg | subtract 1.01325 |
| °C | K | add 273.15 |
| wt% NaCl | molal | ÷58.44×10 (approx) |
