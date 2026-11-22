---
title: Translate NeqSim Colab workflows to Java
description: Preserve a notebook calculation basis while translating a verified NeqSim workflow into executable Java.
---

This guide shows how to translate a NeqSim Colab calculation into a Java application
without silently changing its thermodynamic basis. It does not claim that Python and
Java inputs are interchangeable by inspection: record the composition, model, mixing
rule, temperature, pressure, units, operation sequence, and reported phase before
comparing results.

## Choose a source notebook and freeze its basis

Start from a specific notebook revision, such as the
[NeqSim-Colab getting-started notebook](https://github.com/EvenSol/NeqSim-Colab/blob/master/GettingStartedWIthNeqSim.ipynb)
or the
[SRK gas-density notebook](https://github.com/EvenSol/NeqSim-Colab/blob/master/notebooks/thermodynamics/density_of_gas_using_SRK_EoS.ipynb).
Record the notebook commit and these inputs before translating:

| Basis item | Translation check |
| --- | --- |
| Components and amounts | Preserve names and relative mole amounts in the same order. |
| Thermodynamic model | Instantiate the corresponding Java `SystemInterface` implementation. |
| Mixing rule and database use | Copy the explicit configuration; do not infer it from a notebook title. |
| Temperature and pressure | Carry units explicitly in the record. Java system constructors use kelvin and bara. |
| Flash or process sequence | Preserve operation order and initialization calls. |
| Result phase and property unit | Compare the same phase and request the same output unit. |

Python gateway objects resolve Java calls at runtime. Direct Java code is checked by
the compiler, so translated code also needs explicit imports, a complete class, and a
declared runtime dependency. The current Maven application coordinate is
`com.equinor.neqsim:neqsim:3.20.0`. See
[Getting Started with NeqSim in Java](../java-getting-started) for the separate Java 8
distribution boundary.

## Executable TP-flash translation

The program below represents the direct-Java side of a notebook TP-flash cell. The
constructor values are 288.15 K and 100 bara. Assertions are deliberately part of the
example so a changed phase or nonphysical property stops automated validation instead
of producing a plausible-looking report.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class ColabFlashTranslation {
  private static final Logger logger = LogManager.getLogger(ColabFlashTranslation.class);

  private ColabFlashTranslation() {}

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(288.15, 100.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-hexane", 0.02);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double densityKgM3 = fluid.getDensity("kg/m3");
    double compressibility = fluid.getPhase(0).getZ();

    assert fluid.getNumberOfPhases() >= 1;
    assert Double.isFinite(densityKgM3) && densityKgM3 > 0.0;
    assert Double.isFinite(compressibility) && compressibility > 0.0;

    logger.info("Phases: {}", fluid.getNumberOfPhases());
    logger.info("System density: {} kg/m3", densityKgM3);
    logger.info("Phase-0 compressibility factor: {}", compressibility);
  }
}
```

This is an API and physical-sanity check, not laboratory validation. For a decision
case, compare the translated result against the notebook at the recorded commit and
against appropriate measurements or a recognized reference.

## Translate process and transient notebooks safely

Do not guess a Java method from a Python variable name. Verify every call against the
[current JavaDoc](https://equinor.github.io/neqsim/javadoc/index.html) and source, then
use maintained workflow guides:

- [process simulation](process_simulation) for streams, equipment, and steady-state execution;
- [dynamic process simulation](../process/dynamic-simulation) for explicit transient stepping;
- [pipeline transient simulation](pipeline_transient_simulation) for distributed profiles;
- [reading fluid properties](../thermo/reading_fluid_properties) for phase-aware property access.

`ProcessSystem.runTransient()` performs one transient step for the configured time
increment; it is not a request for an implicit number of steps. Equipment-specific
profile methods belong only to classes that declare them. A generic `Stream` does not
provide pressure- or flow-history arrays, so record observations in the calling loop
or use the reporting API documented for the selected equipment.

## Result exchange and reproducibility

Prefer an explicit result schema over ad hoc console or CSV fragments. Include:

- notebook repository, path, and commit;
- NeqSim version and Java runtime distribution;
- model, mixing rule, composition, temperature, pressure, and units;
- flash/process operation sequence and convergence state;
- phase selected for every reported property;
- assertions and the comparison tolerance.

Java file I/O and table serialization are application concerns. Choose a maintained
CSV or JSON library, test the written schema, and keep output generation separate from
the thermodynamic calculation. Never treat agreement between two unvalidated scripts
as engineering qualification.

## Translation checklist

1. Freeze the source notebook path and commit.
2. Record the complete calculation basis and units.
3. Verify every Java constructor and method against current source.
4. Compile with Java 8 source compatibility when contributing an example to NeqSim.
5. Run with assertions enabled.
6. Compare the same phase, property, and unit against the notebook.
7. Investigate deviations before changing tolerances or model configuration.
