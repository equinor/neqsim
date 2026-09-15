---
title: Getting Started with NeqSim in Java
description: Install NeqSim 3.20.0, run source-verified Java thermodynamics and process examples, and understand model and validation boundaries.
---

This guide provides a maintained starting path for NeqSim 3.20.0. It covers the
published Java distributions, one thermodynamic calculation, one process simulation,
model selection, and repository development.

## Choose the correct Java distribution

NeqSim 3.20.0 was released on 8 September 2026. Select the runtime before copying an
example.

| Use case | Distribution | Required runtime |
| --- | --- | --- |
| New Maven or Gradle application | `com.equinor.neqsim:neqsim:3.20.0` | Java 17 or newer |
| Existing Java 8 application | `neqsim-3.20.0-Java8.jar` release asset | Java 8 or newer |
| Build or contribute to NeqSim | Repository source and Maven wrapper | Source must remain Java 8 compatible; CI covers Java 8 and 21 |
| Run the MCP server | MCP runner or container | Follow the [MCP server README](https://github.com/equinor/neqsim/tree/master/neqsim-mcp-server#readme) |

The normal `neqsim-3.20.0.jar` release asset and Maven Central artifact require Java
17 or newer. The separately published Java 8 asset exists for compatibility. Do not
silently substitute one artifact for the other.

Install a suitable JDK and verify it with `java -version`. A separate Maven
installation is not needed when building NeqSim itself because the repository includes
`mvnw` and `mvnw.cmd`.

## Add NeqSim to a Maven application

Add the current Maven Central artifact to your application's `pom.xml`:

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.20.0</version>
</dependency>
```

Maven resolves NeqSim and its transitive dependencies. Java 8 users should instead
download the explicit
[`neqsim-3.20.0-Java8.jar`](https://github.com/equinor/neqsim/releases/download/v3.20.0/neqsim-3.20.0-Java8.jar)
asset from the [v3.20.0 release](https://github.com/equinor/neqsim/releases/tag/v3.20.0).
The large release jar is the supported standalone distribution; a thin project jar
without its dependencies is not a complete classpath.

## First calculation: TP flash and properties

The constructor temperature is in kelvin and pressure is in bara. Component amounts
are relative mole amounts until a total flow is assigned. The complete program below
runs a TP flash, initializes physical properties, checks basic physical bounds, and
reports through Log4j2.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class FirstCalculation {
  private static final Logger logger = LogManager.getLogger(FirstCalculation.class);

  private FirstCalculation() {}

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.01);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double densityKgM3 = fluid.getDensity("kg/m3");
    double compressibility = fluid.getPhase(0).getZ();
    double molarMassKgMol = fluid.getMolarMass("kg/mol");

    assert fluid.getNumberOfPhases() >= 1;
    assert Double.isFinite(densityKgM3) && densityKgM3 > 0.0;
    assert Double.isFinite(compressibility) && compressibility > 0.0;
    assert molarMassKgMol > 0.01 && molarMassKgMol < 0.10;

    logger.info("Phases: {}", fluid.getNumberOfPhases());
    logger.info("Density: {} kg/m3", densityKgM3);
    logger.info("Compressibility factor: {}", compressibility);
    logger.info("Molar mass: {} kg/mol", molarMassKgMol);
  }
}
```

The sequence is:

`create fluid -> add components -> select model configuration -> flash -> initProperties -> inspect and validate results`

`initProperties()` is required before reading transport properties such as viscosity
and thermal conductivity. It is also a safe general initialization step before
reporting derived properties.

This example verifies numerical sanity and API behavior. It is not validation against
laboratory data, a custody-transfer standard, or a design case.

## First process: separation, compression, and cooling

A `ProcessSystem` runs equipment in the order it is added. Use setters that carry
explicit units at the application boundary.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public final class SimpleProcess {
  private static final Logger logger = LogManager.getLogger(SimpleProcess.class);

  private SimpleProcess() {}

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 80.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Separator separator = new Separator("HP Separator", feed);
    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(150.0, "bara");
    compressor.setIsentropicEfficiency(0.75);

    Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
    aftercooler.setOutletTemperature(30.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(aftercooler);
    process.run();

    double powerKw = compressor.getPower("kW");
    double compressorPressureBara = compressor.getOutletStream().getPressure("bara");
    double cooledTemperatureC = aftercooler.getOutletStream().getTemperature("C");

    assert Double.isFinite(powerKw) && powerKw > 0.0;
    assert Math.abs(compressorPressureBara - 150.0) < 0.1;
    assert Math.abs(cooledTemperatureC - 30.0) < 0.1;
    assert aftercooler.getOutletStream().getFlowRate("kg/hr") > 0.0;

    logger.info("Compressor power: {} kW", powerKw);
    logger.info("Compressor outlet pressure: {} bara", compressorPressureBara);
    logger.info("Aftercooler outlet temperature: {} C", cooledTemperatureC);
  }
}
```

The checks prove that the published program executes and reaches its specified
pressure and temperature. They do not qualify compressor maps, separator performance,
heat-exchanger design, relief loads, controls, or plant operability.

## Select a thermodynamic model deliberately

There is no universally best equation of state. Start from the fluid chemistry and
required property, then compare against relevant measurements or a recognized
reference over the operating envelope.

| Starting point | Suitable screening use | Important boundary |
| --- | --- | --- |
| `SystemSrkEos` | General hydrocarbon phase behavior | Validate liquid density and heavy-end characterization for the case |
| `SystemPrEos` | Alternative cubic-EOS hydrocarbon screening | Validate phase behavior and volume correction against data |
| `SystemSrkCPAstatoil` | Associating mixtures such as water, glycols, alcohols, and amines | Select the documented CPA mixing rule and validate binary interactions |
| `SystemGERG2008Eos` | Covered natural-gas properties | Confirm mixture/property coverage; not every derivative or phase-equilibrium path is implemented |
| `SystemUMRPRUMCEos` | UMR-PRU hydrocarbon screening | Confirm group assignment and validate the intended temperature/composition range |

See [thermodynamic models](thermo/thermodynamic_models) and
[system implementations](thermo/system/) before extending a screening model to
engineering decisions.

## Common thermodynamic operations

Create `ThermodynamicOperations` with the fluid, then call the operation that matches
the known state and specification.

| Operation | Method | Boundary |
| --- | --- | --- |
| Temperature-pressure flash | `TPflash()` | Fluid temperature and pressure must already be set |
| Pressure-enthalpy flash | `PHflash(enthalpy, type)` | Enthalpy basis and the integer type must match the calling workflow |
| Pressure-entropy flash | `PSflash(entropy)` | Entropy basis must be consistent with the fluid state |
| Dew-point temperature | `dewPointTemperatureFlash()` | Requires a meaningful gas composition and starting pressure |
| Bubble-point pressure | `bubblePointPressureFlash(false)` | Requires a meaningful liquid composition and starting temperature |
| Hydrate formation temperature | `hydrateFormationTemperature()` | Requires water, hydrate-forming components, and hydrate-phase configuration |
| Pressure-temperature envelope | `calcPTphaseEnvelope()` | Inspect convergence and the returned branches before reuse |

Do not infer accuracy from convergence alone. Check units, phases, material balance,
physical trends, and case-specific validation evidence.

## Build and test NeqSim from source

Clone the repository and use its wrapper:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```

On Windows use `mvnw.cmd install`. Useful focused commands include:

```bash
./mvnw test -Dtest=SeparatorTest
./mvnw test -Dtest=SeparatorTest#testTwoPhase
python devtools/run_spotless.py apply
python devtools/run_spotless.py check
python devtools/check_documentation_search.py
```

All contributed Java source must remain Java 8 compatible even when the build runs on
a newer JDK. New behavior needs focused JUnit coverage, documentation impact must be
assessed, and examples presented as complete programs must compile and execute.

The repository also provides a
[development container](https://github.com/equinor/neqsim/tree/master/.devcontainer).
Open the Maven project directly in IntelliJ IDEA, Eclipse, or VS Code and let the IDE
use the wrapper-managed project configuration.

## Repository map

| Path | Purpose |
| --- | --- |
| `src/main/java/neqsim/thermo` | Thermodynamic systems, phases, components, and properties |
| `src/main/java/neqsim/thermodynamicoperations` | Flash and saturation operations |
| `src/main/java/neqsim/process` | Streams, equipment, flowsheets, controls, and engineering utilities |
| `src/main/java/neqsim/pvtsimulation` | PVT laboratory-test simulations |
| `src/main/java/neqsim/standards` | Gas and oil quality calculations |
| `src/main/java/neqsim/chemicalreactions` | Reaction equilibrium and kinetics |
| `src/test/java/neqsim` | JUnit regression and documentation tests |
| `docs` | Published guides, contracts, and examples |

## Next steps

- [NeqSim documentation](https://equinor.github.io/neqsim/)
- [JavaDoc API reference](https://equinor.github.io/neqsim/javadoc/index.html)
- [Current releases](https://github.com/equinor/neqsim/releases)
- [Java examples catalog](examples/)
- [Docker getting started](docker-getting-started)
- [Contributing guide](https://github.com/equinor/neqsim/blob/master/CONTRIBUTING.md)
- [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
