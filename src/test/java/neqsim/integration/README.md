# NeqSim Integration Tests

## Overview

This directory contains **cross-module integration tests** that validate NeqSim's architecture by exercising multiple modules together in realistic process scenarios. These tests serve as regression references for AI-assisted development and help developers understand how thermodynamic models, PVT calculations, and process equipment interact.

## Motivation

NeqSim's monorepo structure is a strategic advantage for AI-assisted development because all modules (thermo, PVT, process, flow) are co-located. However, this advantage only materializes when developers understand the integration points. These tests make those integration points explicit.

## Test Suites

### 1. **ThermoToProcessTests.java**
**Focus:** Thermodynamic system integration with process equipment.

**Scenarios:**
- `testThermoToDistillationDeethanizer()` – Light hydrocarbon separation (methane/ethane/propane/butane feed) through a 5-tray SRK-EOS deethanizer. Validates that the inside-out solver converges and produces realistic product splits.
  - **Integration:** `SystemSrkEos` → `DistillationColumn` with `SolverType.INSIDE_OUT`
  - **Validation:** Mole balance, composition splits, solver iteration count

- `testThermoToDistillationWithCPAModel()` – Dehydration of rich TEG solvent using the CPA equation of state, which includes hydrogen bonding for water-glycol interactions.
  - **Integration:** `SystemSrkCPAstatoil` (polar molecules) → `Heater` + `Cooler` → simplified separation process
  - **Validation:** Convergence with association terms; enthalpy balance

- `testThermoToMultiUnitFlowsheet()` – Three-unit sequence: high-pressure separator → cooler → stabilizer distillation on a light crude feed.
  - **Integration:** Consistent `SystemInterface` across separator (flash), heat exchanger, and distillation (tray equilibrium)
  - **Validation:** Global mole balance across three units

- `testThermoSolverConvergenceInProcessContext()` – Direct validation that `ThermodynamicOperations` (bubble point flash, etc.) converge within process equipment context.
  - **Integration:** `ThermodynamicOperations` PT-flash and bubble point calculations
  - **Validation:** Phase existence, convergence flags

**Why These Tests Matter:**
- Changes to thermo models (equation of state, mixing rules) must not break distillation equilibrium calculations.
- Energy balance across heaters, coolers, and distillation columns depends on thermodynamic property derivatives.
- AI agents can use these tests as templates for new distillation scenarios.

---

### 2. **PVTToFlowTests.java**
**Focus:** PVT module (pressure-volume-temperature) integration with separator equipment.

**Scenarios:**
- `testPVTFlashInSeparatorCascade()` – Multi-stage separator cascade (HP → LP) at different pressures and temperatures. Each stage performs a PT-flash to determine phase split.
  - **Integration:** `Separator` performs internal PT-flash using `ThermodynamicOperations`
  - **Validation:** Mole balance at each separator; vapor fraction increases with lower pressure (physically realistic)

- `testPVTIsenthalpicFlashThroughCooler()` – Expansion through a cooler with pressure drop, causing isenthalpic (Joule-Thomson) vaporization.
  - **Integration:** `Cooler` enforces energy balance; PVT module solves isenthalpic flash
  - **Validation:** Enthalpy conservation within 5%; two-phase system exists after expansion

- `testPVTIsothermalFlashPressureSensitivity()` – Isothermal separator at multiple pressures; validates that K-values and vapor fraction vary smoothly.
  - **Integration:** Flash algorithm sensitivity to thermo K-value calculations
  - **Validation:** Monotonic increase in vapor fraction with decreasing pressure

- `testPVTFlashAlgorithmConvergence()` – Direct test of PT-flash and bubble-point flash algorithms.
  - **Integration:** `ThermodynamicOperations.flashPT()` and `bubblePointPressureFlash()`
  - **Validation:** Both liquid and vapor phases exist; no NaN or convergence failures

**Why These Tests Matter:**
- Separators are ubiquitous in production simulations; their flash calculations must be rock-solid.
- K-value predictions from the thermo module directly affect phase split accuracy.
- PVT enhancements (new flash algorithms, improved convergence) can be validated instantly across all separator scenarios.

---

### 3. **EndToEndSimulationTests.java**
**Focus:** Complete production flowsheets exercising all three modules simultaneously.

**Scenarios:**
- `testWellToOilStabilization()` – Production from reservoir (300 bar, 80°C) through HP separator (60 bar) → LP separator (10 bar) → validation of stabilization quality.
  - **Integration:** `SystemSrkEos` → two-stage `Separator` cascade → `ProcessSystem` coordination
  - **Validation:** Global mole balance; gas content decreases after LP separation

- `testHeavyOilThermalTreatmentWithStabilization()` – Heavy crude heated to 200°C, cooled back to 40°C, then distilled to remove light ends.
  - **Integration:** Wide-range thermo stability (80→200→40°C) → `Heater` + `Cooler` with energy balance → `DistillationColumn`
  - **Validation:** Enthalpy conservation in heater/cooler; light components concentrate in overhead

- `testProcessSystemWithMultipleUnits()` – Uses `ProcessSystem` to chain HP and LP separators with automatic recycle/adjuster coordination.
  - **Integration:** `ProcessSystem.add()` unit registration → `ProcessSystem.run()` multi-unit execution
  - **Validation:** Global material balance; all units converge simultaneously

- `testStreamCloningIndependence()` – Ensures that cloned streams (critical for branch-flow simulations) maintain independent state.
  - **Integration:** `SystemInterface.clone()` for stream branching
  - **Validation:** Temperature/pressure changes in one branch do not affect another

**Why These Tests Matter:**
- Production simulations often involve 5–10+ unit operations; global convergence and recycle handling are critical.
- Energy balances across heating, cooling, and separation must be consistent with the thermo model.
- These tests give AI agents realistic, end-to-end workflows to learn from and extend.

---

## How AI Developers Should Use These Tests

### 1. **As a Reference Library**
Before implementing a new feature (e.g., a new equipment type, thermo model, or solver), run these tests:
```bash
# Run all integration tests
mvn test -Dtest=ThermoToProcessTests,PVTToFlowTests,EndToEndSimulationTests

# Run a single scenario
mvn test -Dtest=ThermoToProcessTests#testThermoToDistillationDeethanizer
```

### 2. **As Regression Checks**
After modifying a thermodynamic model, equation of state, or separator logic, **all** integration tests should still pass:
```bash
mvn clean test -Dtest=neqsim.integration.*
```
Failures indicate unintended side effects on downstream modules.

### 3. **As Templates for New Scenarios**
To add a new industrial scenario (e.g., gas drying with molecular sieves, three-phase separator with water), **copy a similar test** and modify:
- Fluid composition and initial state
- Equipment parameters (pressure, temperature, solver type)
- Assertions (what physical result you expect)

### 4. **For AI-Assisted Feature Development**
Provide an AI agent (like Copilot) with these tests and ask:
- "Add support for isothermal separation. Write a new test in `PVTToFlowTests`."
- "Implement a heat-integrated distillation column. Extend `ThermoToProcessTests` with a scenario."
- "Improve the inside-out solver convergence. Update `testThermoToDistillationDeethanizer` to validate faster convergence."

The AI agent has a concrete reference for what "working code" looks like.

---

## Test Execution Notes

### Prerequisites
- Java 8+ (NeqSim target is Java 8 compatibility)
- Maven 3.8.9+
- All dependencies installed via `mvn clean install` in the root project

### Running the Tests
```bash
# Single test class
mvn test -Dtest=ThermoToProcessTests

# Single test method
mvn test -Dtest=ThermoToProcessTests#testThermoToDistillationDeethanizer

# All integration tests
mvn test -Dtest=neqsim.integration.*

# All tests (including integration)
mvn test
```

### Expected Execution Time
- Each individual test: 2–5 seconds
- Full integration suite: ~30–60 seconds (depending on solver convergence)

### Common Issues

| Issue | Solution |
|-------|----------|
| `NullPointerException` on `getTopProduct()` | Distillation column did not converge. Check feed state and column configuration. |
| `Assertion failed: mole balance` | Separator or equipment may not have run (`equipment.run()` missing). Verify run sequence. |
| `Enthalpy conservation failure` | Cooler/Heater energy balance incorrect. Verify outlet temperature/pressure constraints. |
| Test hangs / times out | Equipment solver may be stuck. Check log level; often caused by unconverged flash. |

---

## Integration Test Design Principles

1. **One scenario per test method** – Each test validates a distinct integration point.
2. **Realistic industrial context** – Compositions, pressures, and temperatures reflect real production scenarios.
3. **Global balance assertions** – Tests validate that total moles, energy, and species are conserved across module boundaries.
4. **Solver convergence metrics** – Tests check iteration counts, residuals (where available) to catch slow/stalled solvers.
5. **Independence** – Tests do not depend on execution order; can be run individually or in any sequence.
6. **Documentation-driven** – Each test includes JavaDoc explaining the cross-module dependency and expected result.

---

## Extending Integration Tests

### Adding a New Scenario
1. Create a new test method in the appropriate class (`ThermoToProcessTests`, `PVTToFlowTests`, or `EndToEndSimulationTests`).
2. Include a detailed JavaDoc comment explaining:
   - The industrial/physical scenario
   - Which modules are involved
   - What integration point(s) are validated
   - Expected physical result
3. Use assertions on global quantities (mole balance, enthalpy balance, composition reasonableness).
4. Add a comment for any new equipment type or thermo model used.

### Example: Adding a PVT Flash Test
```java
/**
 * Adiabatic flash (isentropic expansion) validation.
 *
 * <p>
 * <b>Scenario:</b> High-pressure stream expands adiabatically through a
 * throttle valve, vaporizing at constant entropy. This tests thermo entropy
 * calculations and adiabatic flash solver.
 *
 * <p>
 * <b>Cross-Module Validation:</b>
 * <ul>
 * <li>thermo: Entropy derivatives vs. pressure must be accurate</li>
 * <li>pvt: Adiabatic flash solver must converge</li>
 * </ul>
 */
@Test
public void testPVTAdiabaticFlash() {
  SystemInterface system = new SystemSrkEos(273.15 + 30.0, 50.0);
  // ... setup and assertions
}
```

---

## Regression Test Data

For large simulations or complex scenarios, baseline results (expected outputs) can be stored in `src/test/resources/integration/`:
- `well_to_stabilization_baseline.csv` – Expected product flowrates and compositions
- `heavy_oil_thermal_baseline.json` – Temperature profiles, enthalpy changes

Update these baselines when intentional changes are made to thermodynamic models or solvers. Run a diff before and after to ensure changes are as expected.

---

## Related Documentation

- **NeqSim Architecture:** See `docs/modules.md` for package structure and module responsibilities
- **Thermo System Docs:** `docs/thermo/` for EOS models and property calculations
- **Process Equipment:** `docs/process/` for equipment-specific guidance
- **Developer Setup:** `docs/development/DEVELOPER_SETUP.md` for environment configuration

---

## Contact & Contributions

For questions about these integration tests or to propose new scenarios:
1. Open a GitHub issue with a clear description of the scenario
2. Include the physics/engineering motivation
3. Reference existing tests that are similar

AI agents and human developers can collaborate on expanding this test suite to cover new production scenarios, equipment types, and thermodynamic models.
