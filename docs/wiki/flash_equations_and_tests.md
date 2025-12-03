# Flash calculations validated by tests

NeqSim's flash algorithms are exercised heavily in the JUnit suite under `src/test/java/neqsim/thermodynamicoperations/flashops`. The tests document how the solvers are configured and what outputs they must reproduce, giving a reproducible view of the underlying theory.

## Rachford-Rice vapor fraction solving

`RachfordRiceTest` switches between the Nielsen (2023) and Michelsen (2001) variants of the Rachford–Rice solver to verify that all implementations converge to the same vapor fraction for the same `K`-values and overall composition.【F:src/test/java/neqsim/thermodynamicoperations/flashops/RachfordRiceTest.java†L14-L39】 The test uses a binary mixture with `z=[0.7, 0.3]` and `K=[2.0, 0.01]` and asserts a vapor fraction (\(\beta\)) of 0.40707, which is the root of the classic balance equation:

\[
\sum_i z_i \frac{K_i - 1}{1 + \beta (K_i - 1)} = 0
\]

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
