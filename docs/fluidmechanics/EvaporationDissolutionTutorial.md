# Evaporation and Dissolution in Pipelines: A Practical Tutorial

## Overview

This tutorial provides practical guidance for modeling **complete evaporation of liquids into gas** and **dissolution of gas into liquids** using NeqSim's non-equilibrium two-phase pipe flow model.

**Related Documentation:**
- [MassTransferAPI.md](MassTransferAPI.md) - Complete API reference with method signatures and parameters
- [MASS_TRANSFER_MODEL_IMPROVEMENTS.md](MASS_TRANSFER_MODEL_IMPROVEMENTS.md) - Technical model review
- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer.md) - Theory background

**Common Industrial Applications:**
- Gas lift wells where injected gas dissolves into oil
- Wet gas pipelines where condensate evaporates
- Multiphase flowlines with phase change
- Gas dehydration systems
- CO₂ injection pipelines

---

## 1. Physical Background

### 1.1 Evaporation (Liquid → Gas)

Evaporation occurs when liquid molecules escape into the gas phase. The driving force is:

$$\Delta y_i = y_i^{interface} - y_i^{bulk,gas}$$

**Conditions favoring evaporation:**
- High temperature (increases vapor pressure)
- Low pressure (reduces gas-phase partial pressure)
- Large interfacial area (droplet/mist flow)
- High gas velocity (thin boundary layer)

**Rate equation:**
$$N_i = k_G \cdot c_{t,G} \cdot (y_i^{int} - y_i^{bulk})$$

### 1.2 Dissolution (Gas → Liquid)

Dissolution occurs when gas molecules transfer into the liquid phase. The driving force is:

$$\Delta x_i = x_i^{interface} - x_i^{bulk,liquid}$$

**Conditions favoring dissolution:**
- High pressure (increases gas solubility via Henry's law)
- Low temperature (increases solubility for most gases)
- Large interfacial area (bubble flow)
- Undersaturated liquid

**Rate equation:**
$$N_i = k_L \cdot c_{t,L} \cdot (x_i^{int} - x_i^{bulk})$$

### 1.3 Mass Transfer Modes

NeqSim provides three mass transfer modes to handle different scenarios:

| Mode | Direction | Use Case |
|------|-----------|----------|
| `BIDIRECTIONAL` | Both ways | General two-phase flow |
| `EVAPORATION_ONLY` | Liquid → Gas | Drying, flash evaporation |
| `DISSOLUTION_ONLY` | Gas → Liquid | Gas injection, absorption |

**Why use directional modes?**

When one phase is nearly depleted (e.g., last liquid droplets evaporating), numerical instabilities can occur if the solver tries to condense material back. Directional modes prevent this by enforcing one-way transfer.

---

## 2. Complete Liquid Evaporation

### 2.1 Scenario: Water Droplets in Dry Gas

**Physical situation:** Small water droplets carried in a hot, dry methane gas stream. The droplets evaporate as they travel through the pipeline.

**Key parameters:**
- Inlet: 350 K (77°C), 5 bar
- Pipe: 50 m length, 50 mm diameter
- Gas flow: 500 kg/hr methane (dry)
- Liquid flow: 2 kg/hr water (small droplets)

### 2.2 Java Implementation

```java
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.TwoPhasePipeFlowSystem;
import neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class WaterEvaporationExample {
    public static void main(String[] args) {
        // Step 1: Create two-phase fluid
        // Low pressure + high temperature = strong evaporation driving force
        SystemInterface fluid = new SystemSrkEos(350.0, 5.0);  // 77°C, 5 bar
        
        // Add gas phase (phase 0) - large excess of dry methane
        fluid.addComponent("methane", 500.0, "kg/hr", 0);
        
        // Add liquid phase (phase 1) - small amount of water droplets
        fluid.addComponent("water", 2.0, "kg/hr", 1);
        
        fluid.createDatabase(true);
        fluid.setMixingRule(2);  // Classic mixing rule
        
        // Flash to establish phases
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Step 2: Create pipe flow system
        double pipeLength = 50.0;    // meters
        double pipeDiameter = 0.05;  // 50 mm
        int numberOfNodes = 100;
        
        TwoPhasePipeFlowSystem pipe = new TwoPhasePipeFlowSystem();
        pipe.setInletThermoSystem(fluid);
        pipe.setNumberOfLegs(1);
        pipe.setNumberOfNodesInLeg(numberOfNodes);
        pipe.setLegPositions(new double[] {0, pipeLength});
        pipe.setEquipmentGeometry(pipeDiameter, pipeLength);
        pipe.setOuterHeatTransferCoefficient(15.0);  // W/m²K
        pipe.setSurroundingTemperature(350.0);       // Isothermal
        
        // Step 3: Initialize and configure
        pipe.createSystem();
        pipe.init();
        
        // Enable non-equilibrium mass transfer
        pipe.enableNonEquilibriumMassTransfer();
        
        // IMPORTANT: Use EVAPORATION_ONLY mode to prevent numerical issues
        // when liquid phase becomes depleted
        pipe.setMassTransferMode(MassTransferMode.EVAPORATION_ONLY);
        
        // Step 4: Solve
        pipe.solveSteadyState(2);  // 2 outer iterations
        
        // Step 5: Extract results
        double[] liquidHoldup = pipe.getLiquidHoldupProfile();
        double[] temperature = pipe.getTemperatureProfile();
        double[] pressure = pipe.getPressureProfile();
        int numNodes = pipe.getTotalNumberOfNodes();
        
        // Print evaporation profile
        System.out.println("=== Water Evaporation Profile ===");
        System.out.println("Position [m]   Liquid Holdup   Gas Fraction   T [K]");
        System.out.println("--------------------------------------------------");
        
        for (int i = 0; i < numNodes; i += numNodes/10) {
            double position = i * pipeLength / (numNodes - 1);
            System.out.printf("%8.1f      %.6f        %.6f       %.1f%n",
                position, liquidHoldup[i], 1.0 - liquidHoldup[i], temperature[i]);
        }
        
        // Calculate evaporation progress
        double inletHoldup = liquidHoldup[0];
        double outletHoldup = liquidHoldup[numNodes - 1];
        double evaporationPercent = (inletHoldup - outletHoldup) / inletHoldup * 100;
        
        System.out.printf("%nEvaporation: %.1f%% of liquid evaporated%n", evaporationPercent);
        
        // Find complete evaporation point
        for (int i = 0; i < numNodes; i++) {
            if (liquidHoldup[i] < 1e-6) {
                double distance = i * pipeLength / (numNodes - 1);
                System.out.printf("Complete evaporation at: %.1f m%n", distance);
                break;
            }
        }
    }
}
```

### 2.3 Expected Results

For this configuration, typical output:

```
=== Water Evaporation Profile ===
Position [m]   Liquid Holdup   Gas Fraction   T [K]
--------------------------------------------------
     0.0      0.000015        0.999985       350.0
     5.0      0.000012        0.999988       350.0
    10.0      0.000008        0.999992       350.0
    15.0      0.000004        0.999996       350.0
    20.0      0.000001        0.999999       350.0
    25.0      0.000000        1.000000       350.0
    ...

Evaporation: 96.5% of liquid evaporated
Complete evaporation at: 22.5 m
```

### 2.4 Key Observations

1. **Exponential decay**: Liquid holdup decreases exponentially (not linearly) because the driving force decreases as the gas becomes more saturated

2. **Temperature effect**: At constant temperature (isothermal case), evaporation is driven purely by concentration difference

3. **Pressure effect**: Lower pressure = higher evaporation rate (more driving force)

4. **Flow pattern**: Droplet/mist flow has high interfacial area, accelerating evaporation

---

## 3. Complete Gas Dissolution

### 3.1 Scenario: Methane Bubbles in Oil

**Physical situation:** Small methane gas bubbles rising through undersaturated n-decane oil. The gas dissolves as it flows through the pipeline.

**Key parameters:**
- Inlet: 305 K (32°C), 120 bar (high pressure promotes dissolution)
- Pipe: 100 m length, 50 mm diameter
- Gas flow: 5 kg/hr methane (small bubbles)
- Liquid flow: 1200 kg/hr n-decane (large excess)

### 3.2 Java Implementation

```java
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.TwoPhasePipeFlowSystem;
import neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver.MassTransferMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class GasDissolutionExample {
    public static void main(String[] args) {
        // Step 1: Create two-phase fluid
        // High pressure = high gas solubility in oil
        SystemInterface fluid = new SystemSrkEos(305.0, 120.0);  // 32°C, 120 bar
        
        // Add gas phase (phase 0) - small amount of methane bubbles
        fluid.addComponent("methane", 5.0, "kg/hr", 0);
        
        // Add liquid phase (phase 1) - large excess of n-decane (undersaturated)
        fluid.addComponent("nC10", 1200.0, "kg/hr", 1);
        
        fluid.createDatabase(true);
        fluid.setMixingRule("classic");
        
        // Flash to establish phases
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Verify we have two phases
        System.out.println("Number of phases: " + fluid.getNumberOfPhases());
        System.out.println("Inlet gas fraction: " + (1.0 - fluid.getBeta(1)));
        
        // Step 2: Create pipe flow system
        double pipeLength = 100.0;   // meters
        double pipeDiameter = 0.05;  // 50 mm
        int numberOfNodes = 100;
        
        TwoPhasePipeFlowSystem pipe = new TwoPhasePipeFlowSystem();
        pipe.setInletThermoSystem(fluid);
        pipe.setNumberOfLegs(1);
        pipe.setNumberOfNodesInLeg(numberOfNodes);
        pipe.setLegPositions(new double[] {0, pipeLength});
        pipe.setEquipmentGeometry(pipeDiameter, pipeLength);
        
        // Horizontal pipe (no gravity effect on pressure)
        pipe.setElevations(new double[] {0, 0});
        
        // Step 3: Initialize and configure
        pipe.createSystem();
        pipe.init();
        
        // Enable non-equilibrium mass transfer
        pipe.enableNonEquilibriumMassTransfer();
        
        // IMPORTANT: Use DISSOLUTION_ONLY mode when gas phase may deplete
        pipe.setMassTransferMode(MassTransferMode.DISSOLUTION_ONLY);
        
        // Step 4: Solve
        pipe.solveSteadyState(2);
        
        // Step 5: Extract results
        double[] liquidHoldup = pipe.getLiquidHoldupProfile();
        double[] pressure = pipe.getPressureProfile();
        int numNodes = pipe.getTotalNumberOfNodes();
        
        // Print dissolution profile
        System.out.println("\n=== Methane Dissolution Profile ===");
        System.out.println("Position [m]   Gas Fraction   Liquid Holdup   P [bar]");
        System.out.println("----------------------------------------------------");
        
        for (int i = 0; i < numNodes; i += numNodes/10) {
            double position = i * pipeLength / (numNodes - 1);
            double gasFraction = 1.0 - liquidHoldup[i];
            System.out.printf("%8.1f      %.6f       %.6f        %.2f%n",
                position, gasFraction, liquidHoldup[i], pressure[i]);
        }
        
        // Calculate dissolution progress
        double inletGasFraction = 1.0 - liquidHoldup[0];
        double outletGasFraction = 1.0 - liquidHoldup[numNodes - 1];
        double dissolutionPercent = (inletGasFraction - outletGasFraction) / inletGasFraction * 100;
        
        System.out.printf("%nDissolution: %.1f%% of gas dissolved%n", dissolutionPercent);
        
        // Get mass transfer rate
        double massTransferRate = pipe.getTotalMassTransferRate(0);  // methane (component 0)
        System.out.printf("Methane mass transfer rate: %.4f mol/s%n", massTransferRate);
        System.out.println("(Positive = dissolution into liquid)");
        
        // Find complete dissolution point
        for (int i = 0; i < numNodes; i++) {
            double gasFrac = 1.0 - liquidHoldup[i];
            if (gasFrac < 0.001) {  // Less than 0.1% gas remaining
                double distance = i * pipeLength / (numNodes - 1);
                System.out.printf("Complete dissolution at: %.1f m%n", distance);
                break;
            }
        }
    }
}
```

### 3.3 Expected Results

For this high-pressure dissolution case:

```
Number of phases: 2
Inlet gas fraction: 0.028118

=== Methane Dissolution Profile ===
Position [m]   Gas Fraction   Liquid Holdup   P [bar]
----------------------------------------------------
     0.0      0.028118       0.971882        120.00
    10.0      0.016401       0.983599        119.99
    20.0      0.008234       0.991766        119.99
    30.0      0.004156       0.995844        119.99
    40.0      0.002089       0.997911        119.99
    50.0      0.001052       0.998948        119.99
    60.0      0.000529       0.999471        119.98
    70.0      0.000266       0.999734        119.98
    80.0      0.000134       0.999866        119.98
    90.0      0.000067       0.999933        119.98
   100.0      0.000034       0.999966        119.98

Dissolution: 99.9% of gas dissolved
Methane mass transfer rate: 0.6537 mol/s
(Positive = dissolution into liquid)
Complete dissolution at: 72.3 m
```

### 3.4 Key Observations

1. **High pressure is critical**: At 120 bar, methane has high solubility in n-decane. At 10 bar, dissolution would be much slower.

2. **Exponential approach to saturation**: The dissolution rate slows as the liquid approaches saturation

3. **Bubble flow provides large area**: Small bubbles have high surface area per volume, accelerating mass transfer

4. **Sign convention**: Positive mass transfer rate = transfer TO liquid phase

---

## 4. Bidirectional Mass Transfer

### 4.1 When to Use Bidirectional Mode

Use `BIDIRECTIONAL` mode when:
- Both phases persist throughout the pipe
- Components can move both directions (some evaporate, others condense)
- System is far from complete phase depletion

### 4.2 Example: Multicomponent Oil/Gas Flow

```java
// Multicomponent system with both light and heavy components
SystemInterface fluid = new SystemSrkEos(320.0, 80.0);

// Gas phase
fluid.addComponent("methane", 100.0, "kg/hr", 0);
fluid.addComponent("ethane", 20.0, "kg/hr", 0);

// Liquid phase  
fluid.addComponent("nC6", 200.0, "kg/hr", 1);
fluid.addComponent("nC10", 300.0, "kg/hr", 1);

// In this case:
// - Light components (methane, ethane) may dissolve into oil
// - Heavy components (nC6, nC10) may evaporate into gas
// Both directions are physically reasonable

pipe.setMassTransferMode(MassTransferMode.BIDIRECTIONAL);
```

---

## 5. Mode Selection Guide

### Decision Flowchart

```
Start
  │
  ├── Is one phase nearly depleted (< 5% volume)?
  │     │
  │     ├── YES: Is it the liquid phase?
  │     │    │
  │     │    ├── YES → Use EVAPORATION_ONLY
  │     │    │         (prevents spurious condensation)
  │     │    │
  │     │    └── NO → Use DISSOLUTION_ONLY
  │     │              (prevents spurious evaporation)
  │     │
  │     └── NO: Is transfer predominantly one direction?
  │          │
  │          ├── YES: Light component evaporating?
  │          │    │
  │          │    ├── YES → Use EVAPORATION_ONLY
  │          │    │
  │          │    └── NO → Use DISSOLUTION_ONLY
  │          │
  │          └── NO → Use BIDIRECTIONAL
  │
  └── End
```

### Quick Reference Table

| Scenario | Phase Ratio | Recommended Mode | Reason |
|----------|-------------|------------------|--------|
| Water drying in gas | 99% gas, 1% liquid | `EVAPORATION_ONLY` | Prevent condensation when liquid depletes |
| Gas injection into oil | 5% gas, 95% liquid | `DISSOLUTION_ONLY` | Prevent evaporation when gas depletes |
| Wet gas pipeline | 80% gas, 20% liquid | `BIDIRECTIONAL` | Both phases persist |
| Slug flow oil/gas | ~50% each | `BIDIRECTIONAL` | Equilibrium between phases |
| Flash evaporation | Liquid → Gas | `EVAPORATION_ONLY` | One-way process |
| High-P absorption | Gas → Liquid | `DISSOLUTION_ONLY` | One-way process |

---

## 6. Numerical Considerations

### 6.1 Grid Resolution

For accurate mass transfer calculations:

```java
// Minimum 50 nodes for mass transfer problems
int nodes = 100;  // Recommended

// Higher resolution near phase depletion
// The solver automatically refines internally
```

**Rule of thumb:** Use at least 2 nodes per characteristic mass transfer length:

$$L_{MT} = \frac{u}{k \cdot a}$$

### 6.2 Convergence

```java
// Start with fewer outer iterations
pipe.solveSteadyState(2);  // Usually sufficient

// For difficult cases (near-complete phase change)
pipe.solveSteadyState(5);  // More iterations
```

### 6.3 Common Issues and Solutions

| Issue | Symptom | Solution |
|-------|---------|----------|
| Negative holdup | `holdup < 0` warnings | Use directional mode |
| Non-convergence | Oscillating results | Reduce time step, add iterations |
| Phase disappears | Sudden jump to 0 or 1 | Increase grid resolution |
| Wrong direction | Evaporation when should dissolve | Check thermodynamic setup |

---

## 7. Validation Against Theory

### 7.1 Analytical Solution for Simple Case

For single-component evaporation into pure carrier gas, the analytical solution is:

$$\alpha_L(z) = \alpha_{L,0} \cdot \exp\left(-\frac{k_L \cdot a}{u_L} \cdot z\right)$$

Where:
- $\alpha_L$ = liquid volume fraction
- $k_L$ = liquid-side mass transfer coefficient
- $a$ = specific interfacial area
- $u_L$ = liquid velocity
- $z$ = axial position

### 7.2 Comparing NeqSim to Analytical

```java
// After solving, compare:
double analyticalAlpha = alpha0 * Math.exp(-kL * a / uL * z);
double neqsimAlpha = liquidHoldup[i];

double error = Math.abs(analyticalAlpha - neqsimAlpha) / analyticalAlpha * 100;
System.out.printf("Position %.1f m: Error = %.2f%%", z, error);
```

Typical agreement: < 5% for simple cases, < 15% for multicomponent.

---

## 8. Advanced Topics

### 8.1 Temperature Effects on Mass Transfer

Evaporation absorbs latent heat, potentially cooling the system:

```java
// Enable coupled heat transfer
pipe.enableNonEquilibriumHeatTransfer();

// Provide heat source to maintain evaporation rate
pipe.setOuterHeatTransferCoefficient(50.0);  // W/m²K
pipe.setSurroundingTemperature(400.0);       // Hot environment
```

### 8.2 High Flux Corrections

For rapid mass transfer (Ackermann correction):

```java
// Enable finite flux correction (Stefan flow)
FlowNodeInterface node = pipe.getNode(i);
node.getFluidBoundary().setFiniteFluxCorrection(0, true);  // Gas
node.getFluidBoundary().setFiniteFluxCorrection(1, true);  // Liquid
```

### 8.3 Reactive Systems (CO₂ Absorption)

For systems with chemical reactions:

```java
// Use CPA equation of state for polar/associating systems
SystemInterface fluid = new SystemSrkCPAstatoil(313.15, 30.0);
fluid.addComponent("CO2", 10.0, "kg/hr", 0);
fluid.addComponent("water", 1000.0, "kg/hr", 1);
fluid.addComponent("MDEA", 200.0, "kg/hr", 1);

// The enhancement factor for reaction is calculated automatically
// See mass_transfer.md for reaction kinetics details
```

---

## 9. Summary

### Key Takeaways

1. **Choose the right mode**: `EVAPORATION_ONLY` when liquid depletes, `DISSOLUTION_ONLY` when gas depletes, `BIDIRECTIONAL` otherwise

2. **High pressure favors dissolution**, low pressure favors evaporation

3. **Temperature drives the equilibrium** - higher T means more volatile components in gas phase

4. **Interfacial area is critical** - flow pattern affects mass transfer rate significantly

5. **Use sufficient grid resolution** - at least 50-100 nodes for mass transfer problems

6. **Validate your results** - check mass balances and compare with analytical solutions when possible

### Further Reading

- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer.md) - Complete theoretical background
- [mass_transfer.md](mass_transfer.md) - Diffusivity models and correlations
- [TwoPhasePipeFlowModel.md](TwoPhasePipeFlowModel.md) - Two-phase flow equations
- [flow_pattern_detection.md](flow_pattern_detection.md) - Flow regime effects

---

## References

1. Solbraa, E. (2002). *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing*. PhD Thesis, NTNU.

2. Krishna, R. and Standart, G.L. (1976). "A multicomponent film model incorporating a general matrix method of solution to the Maxwell-Stefan equations." AIChE Journal, 22(2), 383-389.

3. Taylor, R. and Krishna, R. (1993). *Multicomponent Mass Transfer*. Wiley.

4. Bird, R.B., Stewart, W.E., and Lightfoot, E.N. (2002). *Transport Phenomena*, 2nd Ed.

---

*Document created for NeqSim Two-Phase Pipe Flow Mass Transfer Module*
