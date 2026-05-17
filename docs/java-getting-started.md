---
title: Getting Started with NeqSim in Java
description: Complete guide to using and developing with NeqSim in Java — prerequisites, installation, first calculations, process simulation, and developer setup.
---

# Getting Started with NeqSim in Java

This guide covers everything you need to start using NeqSim as a Java library — from installing prerequisites to running your first thermodynamic calculation and building a process simulation.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | 8 or newer (11+ recommended) | Compile and run NeqSim |
| **Maven** | 3.6+ | Dependency management (included via wrapper if building from source) |
| **Git** | Any recent | Clone the repository (only needed for development) |
| **IDE** (optional) | IntelliJ IDEA, Eclipse, or VS Code | Recommended for development |

### Installing a JDK

If you don't have a JDK installed:

- **Windows**: Download [Eclipse Temurin](https://adoptium.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- **macOS**: `brew install temurin` or download from [Adoptium](https://adoptium.net/)
- **Linux**: `sudo apt install openjdk-11-jdk` (Ubuntu/Debian) or `sudo yum install java-11-openjdk-devel` (RHEL/CentOS)

Verify your installation:

```bash
java -version
# Should show: openjdk version "11.0.x" or similar
```

---

## Using NeqSim as a library

The easiest way to use NeqSim is to add it as a Maven dependency to your project.

### Option 1: Maven Central (recommended)

No authentication required. Add to your `pom.xml`:

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.6.1</version>
</dependency>
```

Run `mvn clean install` and Maven will resolve NeqSim from Central automatically.

### Option 2: GitHub Packages (latest snapshots)

Useful if you want pre-release versions.

1. Create a [Personal Access Token](https://github.com/settings/tokens) with `read:packages` scope.

2. Add to your Maven `settings.xml` (typically at `~/.m2/settings.xml`):

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

3. Add the repository and dependency to `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/equinor/neqsim</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.6.1</version>
</dependency>
```

### Option 3: Direct JAR download

Download `neqsim-x.x.x.jar` from the [releases page](https://github.com/equinor/neqsim/releases) and add it to your classpath.

---

## Your first calculation — TP flash

This example creates a natural gas mixture and calculates its phase equilibrium and physical properties:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FirstCalculation {
    public static void main(String[] args) {
        // 1. Create fluid at 25°C and 50 bara
        SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
        fluid.addComponent("methane", 0.90);
        fluid.addComponent("ethane", 0.06);
        fluid.addComponent("propane", 0.03);
        fluid.addComponent("n-butane", 0.01);
        fluid.setMixingRule("classic");  // ALWAYS set a mixing rule

        // 2. Run flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();

        // 3. Initialize physical properties (REQUIRED before reading density, viscosity, etc.)
        fluid.initProperties();

        // 4. Read results
        System.out.println("Phases:       " + fluid.getNumberOfPhases());
        System.out.println("Density:      " + fluid.getDensity("kg/m3") + " kg/m³");
        System.out.println("Z-factor:     " + fluid.getPhase("gas").getZ());
        System.out.println("Viscosity:    " + fluid.getPhase("gas").getViscosity("kg/msec") + " kg/(m·s)");
        System.out.println("Molar mass:   " + fluid.getMolarMass("kg/mol") * 1000.0 + " g/mol");
    }
}
```

### Key pattern to remember

```
Create fluid → Add components → Set mixing rule → Flash → initProperties() → Read results
```

> **Important:** Always call `fluid.initProperties()` after a flash calculation before reading transport properties (density, viscosity, thermal conductivity). The flash itself only solves phase equilibrium — transport properties require the extra initialization step.

---

## Process simulation

Build a flowsheet by chaining equipment together:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.processmodel.ProcessSystem;

public class SimpleProcess {
    public static void main(String[] args) {
        // Create fluid
        SystemSrkEos fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
        fluid.addComponent("methane", 0.80);
        fluid.addComponent("ethane", 0.12);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.03);
        fluid.setMixingRule("classic");

        // Build flowsheet
        Stream feed = new Stream("Feed", fluid);
        feed.setFlowRate(50000.0, "kg/hr");

        Separator separator = new Separator("HP Separator", feed);

        Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
        compressor.setOutletPressure(150.0);

        Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
        aftercooler.setOutTemperature(273.15 + 30.0);

        // Assemble and run
        ProcessSystem process = new ProcessSystem();
        process.add(feed);
        process.add(separator);
        process.add(compressor);
        process.add(aftercooler);
        process.run();

        // Results
        System.out.println("Compressor power:   " + compressor.getPower("kW") + " kW");
        System.out.println("Outlet temperature: " + (compressor.getOutletStream().getTemperature() - 273.15) + " °C");
        System.out.println("Cooling duty:       " + aftercooler.getDuty() / 1000.0 + " kW");
    }
}
```

### Available equipment types

| Category | Equipment classes |
|----------|-----------------|
| **Separation** | `Separator`, `ThreePhaseSeparator`, `DistillationColumn`, `MembraneSeparator`, `ComponentSplitter` |
| **Compression** | `Compressor`, `Pump`, `Expander`, `Ejector` |
| **Heat transfer** | `Heater`, `Cooler`, `HeatExchanger` |
| **Flow control** | `ThrottlingValve`, `Splitter`, `Mixer` |
| **Pipelines** | `PipeBeggsAndBrills`, `AdiabaticPipe` |
| **Reactors** | `GibbsReactor` |
| **Utilities** | `Recycle`, `Adjuster`, `SetPoint` |

---

## Choosing an equation of state

| EOS class | Best for | Notes |
|-----------|----------|-------|
| `SystemSrkEos` | General hydrocarbon systems | Good all-round choice |
| `SystemPrEos` | Reservoir fluids, liquid density | Better liquid density than SRK |
| `SystemSrkCPAstatoil` | Water, glycols, alcohols, amines | Handles hydrogen bonding (CPA) |
| `SystemGERG2008Eos` | Natural gas custody transfer | Highest accuracy for gas properties |
| `SystemUMRPRUMCEos` | Wide-range mixtures | Predictive, no interaction parameters needed |

```java
// SRK for general use
SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);

// PR for oil systems
SystemPrEos oil = new SystemPrEos(350.0, 200.0);

// CPA for water/glycol systems
SystemSrkCPAstatoil wet = new SystemSrkCPAstatoil(280.0, 60.0);
wet.addComponent("methane", 0.90);
wet.addComponent("water", 0.10);
wet.setMixingRule(10);  // CPA mixing rule
```

---

## Common flash calculations

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

ops.TPflash();                          // Temperature-pressure flash
ops.PHflash(enthalpy, 0);              // Pressure-enthalpy flash
ops.PSflash(entropy);                   // Pressure-entropy flash
ops.dewPointTemperatureFlash();         // Dew point temperature
ops.bubblePointPressureFlash(false);    // Bubble point pressure
ops.hydrateFormationTemperature();      // Hydrate formation temperature
ops.calcPTphaseEnvelope();              // Full phase envelope
```

---

## Developing NeqSim — building from source

### Clone and build

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install        # Linux/macOS
mvnw.cmd install      # Windows
```

The Maven wrapper (`mvnw`/`mvnw.cmd`) downloads the correct Maven version automatically — no separate Maven installation needed.

### Run tests

```bash
./mvnw test                                      # all tests
./mvnw test -Dtest=SeparatorTest                 # single class
./mvnw test -Dtest=SeparatorTest#testTwoPhase    # single method
```

### Static analysis

```bash
./mvnw checkstyle:check     # code style
./mvnw spotbugs:check       # bug detection
./mvnw pmd:check            # code quality
```

### Code coverage

```bash
./mvnw jacoco:prepare-agent test install jacoco:report
# Report at target/site/jacoco/index.html
```

### VS Code with dev container

The repository includes a ready-to-use [dev container](.devcontainer/) with Maven and recommended extensions pre-installed:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
code .   # VS Code will prompt to reopen in container
```

### IntelliJ IDEA

1. Open IntelliJ IDEA
2. **File → Open** → select the cloned `neqsim` folder
3. IntelliJ auto-detects the Maven project and imports dependencies
4. Wait for indexing to complete
5. Right-click any test class → **Run** to verify the setup

### Eclipse

1. **File → Import → Maven → Existing Maven Projects**
2. Browse to the cloned `neqsim` folder
3. Select `pom.xml` and finish the import
4. Right-click the project → **Maven → Update Project**

---

## Project structure

```
src/
  main/java/neqsim/
    thermo/                  Thermodynamic models (60+ EOS classes)
    thermodynamicoperations/ Flash calculations (TP, PH, PS, dew, bubble, phase envelope)
    physicalproperties/      Transport properties (viscosity, conductivity, diffusion)
    process/
      equipment/             33+ unit operations (separator, compressor, HX, valve, ...)
      processmodel/          ProcessSystem — the flowsheet orchestrator
      mechanicaldesign/      Wall thickness, cost estimation, ASME/API/DNV standards
      measurementdevice/     Transmitters, meters, monitors
      controllerdevice/      PID and advanced controllers
    pvtsimulation/           PVT lab experiments (CME, CVD, DL, swelling, ...)
    standards/               Gas quality (ISO 6976), oil quality, sales contracts
    fluidmechanics/          Pipeline hydraulics
    chemicalreactions/       Reaction equilibrium and kinetics
    statistics/              Parameter fitting, regression
  test/java/neqsim/         JUnit 5 tests (mirrors production structure)
  main/resources/            Component databases, design data CSVs
```

---

## Key conventions for contributors

1. **Java 8 compatibility** — All code must compile with Java 8. Do not use `var`, `List.of()`, `Map.of()`, text blocks, records, or other Java 9+ features.
2. **Mixing rule required** — Always call `setMixingRule()` after adding components.
3. **initProperties() after flash** — Call `fluid.initProperties()` before reading transport properties.
4. **Tests required** — All new code must have JUnit 5 tests. All tests must pass before merging.
5. **Checkstyle** — Code must pass `./mvnw checkstyle:check` (Google style with project overrides).

---

## Next steps

- [NeqSim User Documentation](https://equinor.github.io/neqsim/) — Full docs site
- [JavaDoc API Reference](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html) — Complete API docs
- [Java Wiki & examples](https://github.com/equinor/neqsim/wiki) — Usage patterns and guides
- [Jupyter notebooks](https://github.com/equinor/neqsim/tree/master/examples/notebooks) — 30+ runnable examples
- [NeqSim Colab demo](https://colab.research.google.com/drive/1XkQ_CrVj2gLTtJvXhFQMWALzXii522CL) — Try NeqSim interactively
- [GitHub Discussions](https://github.com/equinor/neqsim/discussions) — Ask questions
- [CONTRIBUTING.md](https://github.com/equinor/neqsim/blob/master/CONTRIBUTING.md) — How to contribute
