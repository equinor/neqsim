# PVT simulation workflows backed by regression tests

The PVT simulation tests under `src/test/java/neqsim/pvtsimulation/simulation` capture end-to-end recipes for assembling fluids, configuring experiments, and validating outputs. This guide extracts the tested setups so you can reproduce them in your own studies.

## Constant-volume depletion (CVD)

`ConstantVolumeDepletionTest` builds a lean-gas condensate fluid with TBP fractions, sets the SRK mixing rule, and drives a pressure staircase while keeping temperature fixed.【F:src/test/java/neqsim/pvtsimulation/simulation/ConstantVolumeDepletionTest.java†L14-L41】 After `runCalc()`, the simulation exposes relative volumes and phase properties that can be compared to laboratory measurements via `setExperimentalData(...)`. The test asserts that the calculated relative volume at a mid-range pressure (index 4) matches 2.1981, demonstrating how NeqSim preserves the reservoir volume constraint during depletion.【F:src/test/java/neqsim/pvtsimulation/simulation/ConstantVolumeDepletionTest.java†L31-L41】 A second test reads an Eclipse deck, flashes it to saturation pressure, and verifies that phase densities computed after `runCalc()` remain consistent when phases are split and re-flashed independently.【F:src/test/java/neqsim/pvtsimulation/simulation/ConstantVolumeDepletionTest.java†L43-L74】 Use this workflow when calibrating CVD curves or comparing simulator results to PVT lab data.

**Setup checklist**

1. Create a `SystemInterface` with EOS and add components/TBP fractions.
2. Enable database use and select a mixing rule.
3. Initialize the system (state 0 and 1) before constructing `ConstantVolumeDepletion`.
4. Call `setTemperature(...)`, `setPressures(...)`, and `runCalc()`.
5. Optionally load experimental matrices for regression and retrieve arrays such as `getRelativeVolume()`.

## Differential liberation

`DifferentialLiberationTest` prepares a rich oil system with extensive TBP characterization, including lumping of the plus fraction into 12 pseudo-components.【F:src/test/java/neqsim/pvtsimulation/simulation/DifferentialLiberationTest.java†L11-L37】 The test first computes saturation pressure at 97.5 °C, then steps down through 15 pressure stages while tracking formation volume factor (\(B_o\)), solution gas–oil ratio (\(R_s\)), gas formation volume factor (\(B_g\)), and oil density.【F:src/test/java/neqsim/pvtsimulation/simulation/DifferentialLiberationTest.java†L38-L65】 Assertions span early, mid, and late pressures, confirming that flash results translate into monotonic \(B_o\) shrinkage and degassed densities as expected from the core differential liberation equations.

**Interpreting the outputs**

- \(B_o\) in the test is computed as \(V_{oil,\,res}/V_{oil,\,stock}\) for each pressure step; values trend from 1.69 toward 1.05 as pressure decreases, consistent with expanding shrinkage.
- \(R_s\) (standard gas dissolved in stock-tank barrels of oil) declines to zero by the final stage, aligning with complete gas liberation.
- \(B_g\) is reported in reservoir volume per standard volume; the late-stage value of ~0.056 m³/Sm³ illustrates the increasing compressibility of liberated gas.

Follow the same staged pressure list and temperature target to benchmark your own differential liberation runs against the regression suite.

## General simulation hygiene

The tests highlight a few recurring best practices:

- Always set a mixing rule and initialize the system before running a PVT simulation to avoid inconsistent pseudo-component properties.【F:src/test/java/neqsim/pvtsimulation/simulation/ConstantVolumeDepletionTest.java†L26-L31】
- Use `ThermodynamicOperations` flashes to reinitialize separated phases when comparing densities or re-flashing isolated phases, as shown in the CVD Eclipse example.【F:src/test/java/neqsim/pvtsimulation/simulation/ConstantVolumeDepletionTest.java†L54-L73】
- Keep temperature explicit on each simulation (`setTemperature`) to avoid accidental reuse of a previous state across experiments.【F:src/test/java/neqsim/pvtsimulation/simulation/DifferentialLiberationTest.java†L41-L48】
