---
title: "Reactive Distillation in NeqSim"
description: "Guide to reactive distillation using equilibrium-based reactive flash on each tray. Covers API usage, the Modified RAND method integration with the column solver, NR=0 optimization, partial reactive sections, and benchmark validation."
---

# Reactive Distillation in NeqSim

## Overview

Reactive distillation combines chemical reaction and separation in a single unit operation. In NeqSim, this is implemented by replacing the standard PH flash on each tray with a **reactive PH flash** that solves simultaneous chemical equilibrium (CE) and phase equilibrium (PE) via the Modified RAND method.

**Key features:**

- **Equilibrium-based**: Each tray reaches full chemical and phase equilibrium (no kinetic models)
- **Non-stoichiometric**: Reactions are discovered automatically from elemental composition — no need to specify reaction equations or equilibrium constants
- **Partial reactive sections**: Designate only specific trays as reactive
- **NR=0 optimization**: When a system has no independent reactions (e.g., hydrocarbons only), the solver automatically delegates to NeqSim's optimized standard flash

## Architecture

The implementation consists of three layers:

1. **`ReactiveTray`** — extends `SimpleTray` with `useReactiveFlash = true`
2. **`DistillationColumn.setReactive()`** — replaces middle trays with `ReactiveTray` instances
3. **`ReactiveMultiphasePHflash`** — solves enthalpy-specified reactive flash on each tray (delegates to standard `PHflash` when NR=0)

```
DistillationColumn
  ├── Condenser (standard flash)
  ├── ReactiveTray 1 ──→ ReactiveMultiphasePHflash
  │     └── if NR=0: delegates to standard PHflash
  │     └── if NR>0: secant+bisection on T, inner ReactiveMultiphaseTPflash
  ├── ReactiveTray 2 ──→ ...
  ├── ...
  └── Reboiler (standard flash)
```

## Quick Start

### Java

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.distillation.DistillationColumn;

// WGS system: CO + H2O ⇌ CO2 + H2
SystemInterface fluid = new SystemSrkEos(273.15 + 250.0, 10.0);
fluid.addComponent("CO", 0.40);
fluid.addComponent("water", 0.40);
fluid.addComponent("CO2", 0.10);
fluid.addComponent("hydrogen", 0.10);
fluid.setMixingRule("classic");

Stream feed = new Stream("WGS feed", fluid);
feed.setFlowRate(500.0, "kg/hr");
feed.setTemperature(250.0, "C");
feed.setPressure(10.0, "bara");

// Create column with reboiler only (no condenser)
DistillationColumn column = new DistillationColumn("WGS Column", 3, true, false);
column.setReactive(true);  // All middle trays become reactive
column.addFeedStream(feed, 2);
column.getReboiler().setOutTemperature(273.15 + 350.0);
column.setTopPressure(10.0);
column.setBottomPressure(10.0);
column.run();

// Products
double h2InGas = column.getGasOutStream().getFluid()
    .getPhase(0).getComponent("hydrogen").getx();
System.out.println("H2 mole fraction in gas: " + h2InGas);
```

### Partial Reactive Section

To make only specific trays reactive (e.g., trays 2–4 out of 6):

```java
DistillationColumn column = new DistillationColumn("Column", 6, true, true);
column.setReactive(true, 2, 4);  // Only trays 2, 3, 4 are reactive
column.addFeedStream(feed, 3);
column.run();

// Check which trays are reactive
for (int i = 0; i < column.getNumberOfTrays(); i++) {
    System.out.println("Tray " + i + " reactive: "
        + column.getTray(i).isUseReactiveFlash());
}
```

### Python (Jupyter)

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
DistillationColumn = jneqsim.process.equipment.distillation.DistillationColumn

fluid = SystemSrkEos(273.15 + 250.0, 10.0)
fluid.addComponent("CO", 0.40)
fluid.addComponent("water", 0.40)
fluid.addComponent("CO2", 0.10)
fluid.addComponent("hydrogen", 0.10)
fluid.setMixingRule("classic")

feed = Stream("WGS feed", fluid)
feed.setFlowRate(500.0, "kg/hr")
feed.setTemperature(250.0, "C")
feed.setPressure(10.0, "bara")

column = DistillationColumn("WGS Column", 3, True, False)
column.setReactive(True)
column.addFeedStream(feed, 2)
column.getReboiler().setOutTemperature(273.15 + 350.0)
column.setTopPressure(10.0)
column.setBottomPressure(10.0)
column.run()

h2_gas = column.getGasOutStream().getFluid().getPhase(0).getComponent("hydrogen").getx()
print(f"H2 in gas product: {h2_gas:.4f}")
```

## NR=0 Optimization

When all components in a system are non-reactive (i.e., the formula matrix rank equals the number of components), the number of independent reactions $N_R = 0$. In this case, the reactive flash delegates directly to NeqSim's standard `PHflash` algorithm, producing results **identical** to a standard (non-reactive) column. This optimization:

- Eliminates the overhead of the reactive flash's temperature iteration loop
- Ensures perfect consistency with standard column results
- Is detected automatically — no user action needed

**Example**: A methane/ethane system has $N_C = 2$ components and the formula matrix has rank 2, so $N_R = 0$. Setting `column.setReactive(true)` on such a system incurs zero accuracy penalty.

## Algorithm Details

### For NR > 0 (Reactive Systems)

Each tray solves an enthalpy-specified reactive flash:

1. **Outer loop**: Secant method on temperature $T$ to match the enthalpy specification $H(T) = H_{\text{spec}}$
2. **Inner loop**: At each trial $T$, run `ReactiveMultiphaseTPflash` (Modified RAND) to find the equilibrium composition and phase split
3. **Convergence**: When $|H(T) - H_{\text{spec}}| / |H_{\text{spec}}| < 10^{-8}$ and the inner TP flash converged

### For NR = 0 (Non-Reactive Systems)

The `ReactiveMultiphasePHflash` detects NR=0 via the `FormulaMatrix` and delegates to:
- `PHflash` for enthalpy specifications (standard trays)
- `PSFlash` for entropy specifications

This produces results identical to a standard (non-reactive) distillation column.

## Comparison to Commercial Simulators

| Feature | NeqSim | Aspen Plus / HYSYS | ChemSep |
|---------|--------|-------------------|---------|
| Reaction model | Equilibrium only | Equilibrium + kinetic | Equilibrium + kinetic |
| CE method | Non-stoichiometric (RAND) | Stoichiometric or Gibbs minimization | Stoichiometric |
| Column solver | Sequential tray-by-tray | Inside-out or Newton | Newton |
| Automatic reaction discovery | Yes (from formula matrix) | No (must specify reactions) | No |
| Partial reactive sections | Yes | Yes | Yes |

**Key difference**: NeqSim uses the non-stoichiometric Modified RAND approach, which does not require specifying reaction equations or equilibrium constants. The method discovers all independent reactions from the elemental formula matrix. This is advantageous for complex systems but does not support kinetic rate expressions.

## Limitations

1. **Equilibrium only**: No kinetic rate expressions — every reactive tray reaches full chemical equilibrium
2. **Column convergence for NR > 0**: The sequential column solver with reactive PH flash on each tray can be slow or show mass balance errors for strongly reactive systems
3. **No heat of reaction correction**: The enthalpy balance uses the standard mixture enthalpy which includes the heat of reaction implicitly through the Gibbs energy, but there is no explicit reaction heat term

## Related Documentation

- [Reactive Flash](../thermo/reactive_flash.md) — Standalone reactive TP and PH flash
- [Distillation Column Design](../process/process_design_guide.md) — Standard column design patterns

## Test Coverage

The reactive distillation implementation is tested in [ReactiveDistillationTest.java](../../src/test/java/neqsim/process/equipment/distillation/ReactiveDistillationTest.java) with 8 tests:

1. **Tray creation** — Verifying reactive trays are created correctly
2. **Partial reactive sections** — Testing `setReactive(true, start, end)`
3. **NR=0 mass balance** — Reactive column matches standard column exactly for non-reactive systems
4. **Non-reactive system** — Temperature profiles match standard column
5. **API usability** — Column runs with reactive mode enabled
6. **Single tray WGS** — Reactive tray shows reaction products
7. **Tray vs standalone flash** — Reactive tray composition matches standalone reactive TP flash
8. **WGS column** — Full reactive column with water-gas shift reaction
