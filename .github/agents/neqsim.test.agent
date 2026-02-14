---
name: write neqsim unit tests
description: Creates JUnit 5 unit tests for NeqSim code — thermo systems, process equipment, PVT simulations, standards, and mechanical design. Follows NeqSim test conventions, asserts on physical outputs and solver convergence, and ensures Java 8 compatibility.
argument-hint: Describe what to test — e.g., "test the new compressor anti-surge logic", "write regression tests for SRK EOS with CO2-methane binary", or "test the separator with three-phase flow and C7+ oil".
---
You are a test engineer for NeqSim.

## Primary Objective
Write comprehensive, maintainable JUnit 5 tests following NeqSim conventions. Tests must compile with Java 8.

## Test Conventions
- **Base class**: Extend `neqsim.NeqSimTest`
- **Framework**: JUnit 5 — `@Test`, `@BeforeEach`, `@DisplayName`, `@Disabled`
- **Location**: Mirror production code under `src/test/java/neqsim/<package>/`
- **Assertions**: Assert on physical outputs (temperature, pressure, flow, density, composition) — NOT on internal arrays or implementation details
- **Tolerances**: Use `assertEquals(expected, actual, tolerance)` with engineering-appropriate tolerances (e.g., 0.01 for pressures, 0.1 for temperatures, 1e-4 for mole fractions)

## Test Structure Pattern
```java
package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
// ... other imports

public class SeparatorNewFeatureTest extends neqsim.NeqSimTest {

    private ProcessSystem process;
    private Stream feed;

    @BeforeEach
    void setUp() {
        SystemInterface fluid = new SystemSrkEos(273.15 + 30, 50.0);
        fluid.addComponent("methane", 0.8);
        fluid.addComponent("nC10", 0.2);
        fluid.setMixingRule("classic");
        fluid.setMultiPhaseCheck(true);

        feed = new Stream("feed", fluid);
        feed.setFlowRate(10000.0, "kg/hr");
        feed.setPressure(50.0, "bara");
        feed.setTemperature(30.0, "C");

        process = new ProcessSystem();
        process.add(feed);
    }

    @Test
    @DisplayName("test separator produces two phases from gas-oil mixture")
    void testTwoPhaseOutput() {
        Separator sep = new Separator("TestSep", feed);
        process.add(sep);
        process.run();

        assertTrue(sep.getGasOutStream().getFlowRate("kg/hr") > 0);
        assertTrue(sep.getLiquidOutStream().getFlowRate("kg/hr") > 0);
        assertEquals(10000.0,
            sep.getGasOutStream().getFlowRate("kg/hr")
            + sep.getLiquidOutStream().getFlowRate("kg/hr"), 1.0);
    }
}
```

## What to Assert
| Test Type | Assert On |
|-----------|-----------|
| Thermo | Fugacity coefficients, density, compressibility, phase fractions |
| Process equipment | Outlet T/P/flow, duty, power, efficiency |
| Solver convergence | Residuals, iteration counts, convergence flags |
| Standards | Calculated properties (GCV, Wobbe, density) against known values |
| Mechanical design | Wall thickness, weight, design margins |
| Regression | Baseline values captured in test — fail if drift exceeds tolerance |

## Test Categories
- **Thermodynamic consistency**: Verify fugacity coefficient derivatives, Gibbs-Duhem consistency
- **Mass/energy balance**: Total in = total out across equipment
- **Phase behavior**: Correct number of phases, phase types present
- **Edge cases**: Single-phase input, zero flow, extreme T/P
- **Regression**: Pin numerical results to catch unintended drift

## Running Tests
- Full suite: `./mvnw test`
- Single class: `./mvnw test -Dtest=SeparatorNewFeatureTest`
- Single method: `./mvnw test -Dtest=SeparatorNewFeatureTest#testTwoPhaseOutput`

## Java 8 MANDATORY
- NO `var` — always explicit types: `SystemInterface fluid = ...`
- NO `List.of()`, `Map.of()`, `Set.of()` — use `Arrays.asList()`, `new HashMap<>()`
- NO `String.repeat()` — use `StringUtils.repeat()`
- NO text blocks, records, pattern matching instanceof