---
title: "Flash calculations validated by tests"
description: "NeqSim's flash algorithms are exercised heavily in the JUnit suite under `src/test/java/neqsim/thermodynamicoperations/flashops`. The tests document how the solvers are configured and what outputs the..."
---

# Flash calculations validated by tests

NeqSim's flash algorithms are exercised heavily in the JUnit suite under `src/test/java/neqsim/thermodynamicoperations/flashops`. The tests document how the solvers are configured and what outputs they must reproduce, giving a reproducible view of the underlying theory.

## Rachford-Rice vapor fraction solving

`RachfordRiceTest` switches between the Nielsen (2023) and Michelsen (2001) variants of the Rachford–Rice solver to verify that all implementations converge to the same vapor fraction for the same `K`-values and overall composition.【F:src/test/java/neqsim/thermodynamicoperations/flashops/RachfordRiceTest.java†L14-L39】 The test uses a binary mixture with `z=[0.7, 0.3]` and `K=[2.0, 0.01]` and asserts a vapor fraction ($\beta$) of 0.40707, which is the root of the classic balance equation:

$
\sum_i z_i \frac{K_i - 1}{1 + \beta (K_i - 1)} = 0
$

The converged solution satisfies material balance between vapor and liquid while honoring the phase equilibrium ratios supplied by the `K`-values. Switching `RachfordRice.setMethod(...)` in the test demonstrates that NeqSim exposes multiple solver strategies for the same equation without altering the target root.【F:src/test/java/neqsim/thermodynamicoperations/flashops/RachfordRiceTest.java†L21-L33】 When modeling your own flashes, choose a method that matches your numerical preferences; the test shows that the default and named methods must agree on the fundamental solution.

## TP flash energy consistency

`TPFlashTest` configures multicomponent systems with cubic equations of state (Peng–Robinson, UMR-PRU-MC, SRK-CPA) and validates both phase splits and energy properties after a `TPflash()` call. The tests cover low and high pressure regimes, multi-phase checks, and heavy pseudo-component handling.【F:src/test/java/neqsim/thermodynamicoperations/flashops/TPFlashTest.java†L19-L140】 Assertions include vapor fraction (`getBeta()`), number of phases, and total enthalpy, confirming that the flash calculation preserves the combined internal energy and molar balance implied by the Rachford–Rice solution and the chosen EOS.

To mirror the test configuration:

- Create a `SystemInterface` instance with the appropriate EOS and reference conditions.
- Add light components, water, and TBP fractions as needed.
- Select a mixing rule (`setMixingRule("classic")` or numeric variants) and enable multiphase detection if solids or water are expected.
- Apply pressure/temperature targets and call `new ThermodynamicOperations(system).TPflash()`.
- Reinitialize properties to obtain enthalpy, densities, and phase fractions for validation.

The enthalpy checks in `testRun2` and `testRun3` highlight that the flash solution must satisfy both material balance and the caloric EOS relationships at the specified state points.【F:src/test/java/neqsim/thermodynamicoperations/flashops/TPFlashTest.java†L43-L82】 If discrepancies appear in your own models, align your setup with the tested recipe before exploring alternative property packages.

## Q-Function Flash Testing

`QfuncFlashTest` provides comprehensive testing for state-function based flash calculations following Michelsen's (1999) Q-function methodology. The test class validates multiple flash specifications:

### Single-Variable Q-Function Flashes

These flashes solve for one unknown (T or P) given a state function constraint:

| Test | Flash Type | Specification | Validates |
|------|------------|---------------|-----------|
| `testTSFlash_*` | TSflash | Temperature, Entropy | Pressure convergence |
| `testTHFlash_*` | THflash | Temperature, Enthalpy | Pressure convergence |
| `testTUFlash_*` | TUflash | Temperature, Internal Energy | Pressure convergence |
| `testTVFlash_*` | TVflash | Temperature, Volume | Pressure convergence |
| `testPVFlash_*` | PVflash | Pressure, Volume | Temperature convergence |

### Two-Variable Q-Function Flashes

These flashes solve for both T and P simultaneously:

| Test | Flash Type | Specification | Validates |
|------|------------|---------------|-----------|
| `testVUFlash_*` | VUflash | Volume, Internal Energy | T, P convergence |
| `testVHFlash_*` | VHflash | Volume, Enthalpy | T, P convergence |
| `testVSFlash_*` | VSflash | Volume, Entropy | T, P convergence |

### Test Methodology

Each Q-function flash test follows this pattern:

1. Create a fluid system and run TPflash at known conditions
2. Store the target state function value (H, S, U, or V)
3. Perturb the system (change T or P)
4. Run the Q-function flash with the stored specification
5. Verify the state function converges to the original value within tolerance

Example from the test suite:
```java
// Store enthalpy at initial conditions
ops.TPflash();
double targetH = system.getEnthalpy();

// Change temperature (perturb the system)
system.setTemperature(newTemperature);

// Flash should find pressure that recovers original enthalpy
ops.THflash(targetH);

assertEquals(targetH, system.getEnthalpy(), tolerance);
```

### Thermodynamic Derivatives

The Q-function flashes use analytical derivatives computed via `system.init(3)`:

- `getdVdTpn()` returns $-(\partial V/\partial T)_P$
- `getdVdPtn()` returns $(\partial V/\partial P)_T$

These are combined to form the Newton iteration Jacobians for each flash type.

### Reference

Michelsen, M.L. (1999). "State function based flash specifications." *Fluid Phase Equilibria*, 158-160, 617-626.
