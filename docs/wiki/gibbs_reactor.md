---
title: "Gibbs Reactor"
description: "The Gibbs Reactor is a chemical equilibrium reactor that computes outlet compositions by minimizing the total Gibbs free energy of the system. It is used for modeling chemical reactions at thermodynam..."
---

# Gibbs Reactor

The Gibbs Reactor is a chemical equilibrium reactor that computes outlet compositions by minimizing the total Gibbs free energy of the system. It is used for modeling chemical reactions at thermodynamic equilibrium.

## Overview

The `GibbsReactor` class performs chemical equilibrium calculations using Gibbs free energy minimization with Lagrange multipliers. The reactor automatically determines the equilibrium composition based on:

- Inlet stream composition
- Temperature and pressure conditions
- Elemental mass balance constraints
- Thermodynamic properties from the Gibbs database

## Key Features

- **Isothermal and Adiabatic Modes**: Supports both constant-temperature and heat-balanced operation
- **Multi-component Systems**: Handles complex mixtures with multiple reacting species
- **Inert Components**: Allows marking specific components as non-reactive
- **Convergence Diagnostics**: Provides detailed iteration metrics and mass balance verification
- **Customizable Solver**: Adjustable damping, tolerance, and iteration limits

## Mathematical Background

The reactor minimizes the objective function:

$$G = \sum_i n_i \left( \mu_i^0 + RT \ln(\phi_i y_i P) \right) - \sum_j \lambda_j \left( \sum_i a_{ij} n_i - b_j \right)$$

Where:
- $n_i$ = molar amount of component $i$
- $\mu_i^0$ = standard chemical potential of component $i$
- $\phi_i$ = fugacity coefficient of component $i$
- $y_i$ = mole fraction of component $i$
- $P$ = pressure
- $\lambda_j$ = Lagrange multiplier for element $j$
- $a_{ij}$ = number of atoms of element $j$ in component $i$
- $b_j$ = total moles of element $j$ (conserved)

The Newton-Raphson method iteratively solves for compositions and Lagrange multipliers until convergence.

## Basic Usage

```java
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create inlet stream
SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
system.addComponent("methane", 1.0, "mol/sec");
system.addComponent("oxygen", 2.0, "mol/sec");
system.addComponent("CO2", 0.0, "mol/sec");
system.addComponent("water", 0.0, "mol/sec");
system.setMixingRule(2);

Stream inlet = new Stream("inlet", system);
inlet.run();

// Create and configure reactor
GibbsReactor reactor = new GibbsReactor("combustion reactor", inlet);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
reactor.setMaxIterations(5000);
reactor.setConvergenceTolerance(1e-6);
reactor.setDampingComposition(0.01);
reactor.run();

// Get results
Stream outlet = (Stream) reactor.getOutletStream();
System.out.println("Outlet temperature: " + outlet.getTemperature("C") + " °C");
System.out.println("Conversion completed: " + reactor.hasConverged());
```

## Configuration Options

### Energy Mode

```java
// Isothermal: temperature remains constant
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

// Adiabatic: temperature changes based on reaction enthalpy
reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);

// Using string (case-insensitive)
reactor.setEnergyMode("adiabatic");
```

### Solver Parameters

| Parameter | Method | Default | Description |
|-----------|--------|---------|-------------|
| Max Iterations | `setMaxIterations(int)` | 5000 | Maximum Newton-Raphson iterations |
| Convergence Tolerance | `setConvergenceTolerance(double)` | 1e-3 | Convergence criterion for delta norm |
| Damping Factor | `setDampingComposition(double)` | 0.05 | Step size for composition updates |

```java
reactor.setMaxIterations(10000);
reactor.setConvergenceTolerance(1e-8);
reactor.setDampingComposition(0.001);  // Smaller = more stable, slower
```

### Inert Components

Mark components that should not participate in reactions:

```java
// By name
reactor.setComponentAsInert("nitrogen");
reactor.setComponentAsInert("argon");

// By index
reactor.setComponentAsInert(0);
```

### Database Species

```java
// Use only components present in inlet stream (default)
reactor.setUseAllDatabaseSpecies(false);

// Add all species from Gibbs database (for product prediction)
reactor.setUseAllDatabaseSpecies(true);
```

## Results and Diagnostics

### Convergence Status

```java
if (reactor.hasConverged()) {
    System.out.println("Solution converged in " + reactor.getActualIterations() + " iterations");
} else {
    System.out.println("Failed to converge. Final error: " + reactor.getFinalConvergenceError());
}
```

### Thermodynamic Results

```java
// Enthalpy of reaction (kJ)
double deltaH = reactor.getEnthalpyOfReactions();

// Temperature change in adiabatic mode (K)
double deltaT = reactor.getTemperatureChange();

// Reactor power (W, kW, or MW)
double powerW = reactor.getPower("W");
double powerKW = reactor.getPower("kW");
```

### Mass Balance Verification

```java
// Check mass balance closure
double massError = reactor.getMassBalanceError();  // Percentage error
boolean balanced = reactor.getMassBalanceConverged();  // True if error < 0.1%

// Element-wise balance
double[] elementIn = reactor.getElementMoleBalanceIn();
double[] elementOut = reactor.getElementMoleBalanceOut();
double[] elementDiff = reactor.getElementMoleBalanceDiff();
String[] elementNames = reactor.getElementNames();  // ["O", "N", "C", "H", "S", "Ar", "Z"]
```

### Molar Flows

```java
List<Double> inletMoles = reactor.getInletMoles();
List<Double> outletMoles = reactor.getOutletMoles();
```

## Specialized Reactor: GibbsReactorCO2

For CO2/acid gas systems, use `GibbsReactorCO2` which provides pre-configured reaction pathways:

```java
import neqsim.process.equipment.reactor.GibbsReactorCO2;

GibbsReactorCO2 acidGasReactor = new GibbsReactorCO2("acid gas reactor", inlet);
acidGasReactor.run();
```

**Important Limitations of GibbsReactorCO2:**
- Only bulk (homogeneous) phase reactions are modeled
- Surface reactions and heterogeneous catalysis are not included
- Reactions are disabled when CO2 density falls below 300 kg/m³

## Troubleshooting

### Convergence Issues

1. **Reduce damping factor**: Try `setDampingComposition(0.001)` or smaller
2. **Increase iterations**: Use `setMaxIterations(20000)`
3. **Check initial compositions**: Ensure products have small non-zero initial amounts
4. **Mark inerts**: Components that don't react should be marked as inert

### Mass Balance Errors

If mass balance doesn't close:
- Reduce the damping factor for better numerical stability
- Check that all relevant species are included in the system
- Verify component names match the Gibbs database

### Numerical Instabilities

For stiff systems:
```java
reactor.setDampingComposition(0.0001);  // Very small steps
reactor.setMaxIterations(50000);        // Allow more iterations
reactor.setConvergenceTolerance(1e-4);  // Relax tolerance slightly
```

## Gibbs Database

The reactor uses thermodynamic data from CSV files in `src/main/resources/data/GibbsReactDatabase/`:

- `GibbsReactDatabase.csv` - Component properties (elements, heat capacity, formation enthalpies)
- `DatabaseGibbsFreeEnergyCoeff.csv` - Polynomial coefficients for Gibbs energy calculations

### Supported Elements

The reactor tracks mass balance for: O, N, C, H, S, Ar, Z (charge)

### Adding Custom Components

Custom components can be added to the database files following the existing format. Each component requires:
- Elemental composition
- Heat capacity coefficients (A, B, C, D)
- Standard enthalpy of formation (ΔHf° at 298.15 K)
- Standard Gibbs energy of formation (ΔGf° at 298.15 K)
- Standard entropy (ΔSf° at 298.15 K)

## See Also

- [Process Equipment Overview](./index)
- [Stream Documentation](./getting_started)
- [Thermodynamic Systems](../thermo/)
