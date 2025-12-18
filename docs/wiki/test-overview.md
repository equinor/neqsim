# JUnit Test Overview

The NeqSim project contains an extensive JUnit 5 test suite. The tests are grouped by feature area under `src/test/java/neqsim`. The main groups are summarised below.

## Thermodynamic operations

Directory: `src/test/java/neqsim/thermodynamicoperations`

Tests for the core thermodynamic calculation utilities. The `flashops` package verifies different flash calculations (TP, PH, PS etc.) while `phaseenvelopeops` checks phase envelope algorithms. Utilities and common operations are tested under `util`.

## PVT simulations

Directory: `src/test/java/neqsim/pvtsimulation/simulation`

Covers simulation models used for PVT studies such as constant volume depletion, differential liberation and slim‑tube simulations. These tests ensure that the simulation workflow and calculated properties are consistent.

## Process modelling

Directory: `src/test/java/neqsim/process`

Tests of the dynamic process models and process equipment. Examples include separator, compressor and process controller behaviour.

## Physical properties and fluid mechanics

Directories:
- `src/test/java/neqsim/physicalproperties`
- `src/test/java/neqsim/fluidmechanics`

Focus on methods for viscosity, density and other property models together with flow system calculations.

### Fluid Mechanics Test Files

| Test File | Description |
|-----------|-------------|
| `TwoPhasePipeFlowSystemTest.java` | System setup, steady-state solving, mass/heat transfer, model comparisons |
| `NonEquilibriumPipeFlowTest.java` | Non-equilibrium mass transfer, evaporation, dissolution, bidirectional transfer |
| `FlowPatternDetectorTest.java` | Flow pattern detection (Taitel-Dukler, Baker, Barnea, Beggs-Brill) |
| `InterfacialAreaCalculatorTest.java` | Interfacial area calculations for all flow patterns |
| `MassTransferCoefficientCalculatorTest.java` | Mass transfer coefficient correlations |
| `TwoPhasePipeFlowSystemBuilderTest.java` | Builder API tests |

## Chemical reactions and thermo

Directories:
- `src/test/java/neqsim/chemicalreactions`
- `src/test/java/neqsim/thermo`

Verify reaction models and the underlying thermodynamic phase implementations.

## Utilities, statistics and standards

Directories:
- `src/test/java/neqsim/util`
- `src/test/java/neqsim/statistics`
- `src/test/java/neqsim/standards`

Contain unit tests for helper utilities (database connectors, units), statistical calculations and implementation of industry standards.

## Running the tests

All tests can be executed with Maven:

```bash
mvn test
```

Use the Maven wrapper (`./mvnw test`) when Maven is not installed. To run a specific test class you can supply the class name:

```bash
mvn -Dtest=ClassName test
```

A code coverage report can be produced using Jacoco:

```bash
mvn jacoco:prepare-agent test install jacoco:report
```

The resulting report is written to `target/site/jacoco/index.html`.

