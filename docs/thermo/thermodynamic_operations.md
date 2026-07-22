---
title: "Thermodynamic Operations"
description: "Thermodynamic operations execute equilibrium and property tasks using a configured fluid. Most workflows create a `ThermodynamicOperations` object once and reuse it for multiple calls."
---

# Thermodynamic Operations

Thermodynamic operations execute equilibrium and property tasks using a configured fluid. Most workflows create a `ThermodynamicOperations` object once and reuse it for multiple calls.

## Flash Calculations
- **`TPflash()`**: Calculates phase split at specified temperature and pressure. Run `initProperties()` afterward for density/viscosity.
- **`PHflash(H)` and `PSflash(S)`**: Solve for temperature/phase split given enthalpy or entropy targets—useful for compressors and turbines. Set the pressure on the fluid with `setPressure()` before calling these.
- **`TVflash(V)`**: Volume-constrained flash—finds pressure at fixed temperature and total volume.
- **`VUflash(V, U)`**: Volume- and internal-energy-constrained flash for transient simulations.

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
double vaporFraction = fluid.getBeta();
```

## Phase Envelopes
- **PT envelope**: `ops.calcPTphaseEnvelope()` fills critical point, cricondenbar, cricondentherm, and two-phase boundary.
- **Hydrate curves**: Enable `hydrateCheck(true)` before calling `ops.hydrateFormationTemperature(pressure)`.
- **Wax/solid envelopes**: Use a solid-enabled system and call `ops.calcSolidFormationTemperature()`.

## Property Routines
After flashes, properties are available on each phase:
- `getDensity()` or `getNumberOfMoles()` for molar/volume properties.
- `getEnthalpy()`, `getEntropy()`, and `getCp()` for energy balances.
- `getViscosity()`, `getThermalConductivity()`, and `getInterfacialTension()` for transport analyses.

## Electrolytes and Reactions
- **Electrolytes**: Build systems such as `SystemFurstElectrolyteEos` or `SystemElectrolyteCPAstatoil`, add salts/acids, and enable charge balance. Use `ops.electrolyteFlash()` for salt precipitation studies.
- **Chemical reactions**: Define reactions with stoichiometry and equilibrium constants, then call `ops.calcChemicalEquilibrium()` to couple them into flashes.

## Best Practices
- Always reinitialize (`fluid.init(3)`) after changing temperature, pressure, or composition significantly.
- Reuse the same `ThermodynamicOperations` instance when sweeping conditions to avoid rebuilding internal caches.
- For performance-sensitive loops, pre-allocate fluids and avoid repeated parsing of the component database.
