---
title: Java Quickstart
description: Get started with NeqSim in Java. Maven setup, first flash calculation, and first process simulation in under 10 minutes.
---

# Java Quickstart

Get NeqSim running in a Java project with one thermodynamic calculation and one composable
process simulation. The examples use NeqSim 3.18.0; check
[Maven Central](https://central.sonatype.com/artifact/com.equinor.neqsim/neqsim) for a newer
published version before starting a new project.

## Step 1: add NeqSim to a project

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.18.0</version>
</dependency>
```

The run command below uses Maven's exec plugin. Add this build entry if the project does not
already configure it:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.6.3</version>
    </plugin>
  </plugins>
</build>
```

With Gradle:

```groovy
implementation 'com.equinor.neqsim:neqsim:3.18.0'
```

## Step 2: first flash calculation

Create `FirstCalculation.java`:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FirstCalculation {
    private static final Logger logger = LogManager.getLogger(FirstCalculation.class);

    public static void main(String[] args) {
        // SystemSrkEos accepts temperature in K and absolute pressure in bara.
        SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);

        // addComponent accepts component amounts in mol. These sum to 1.0 mol,
        // so the amounts are also the overall mole fractions for this example.
        fluid.addComponent("methane", 0.85);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.05);

        // Select an appropriate mixing rule before flashing a cubic-EOS mixture.
        fluid.setMixingRule("classic");

        ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
        operations.TPflash();

        // Initialize physical properties before reading density or transport properties.
        fluid.initProperties();

        logger.info("Number of phases: {}", fluid.getNumberOfPhases());
        logger.info("Bulk density: {} kg/m³", fluid.getDensity("kg/m3"));
        logger.info("System Z-factor: {}", fluid.getZ());
    }
}
```

The API unit token is `"kg/m3"`; the displayed SI symbol is kg/m³. `getDensity("kg/m3")`
converts the returned unit but does not select or validate a density model.

Run it from the project directory:

```bash
mvn compile exec:java -Dexec.mainClass="FirstCalculation"
```

## Step 3: first process simulation

Create `FirstProcess.java`:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class FirstProcess {
    private static final Logger logger = LogManager.getLogger(FirstProcess.class);

    public static void main(String[] args) {
        SystemSrkEos fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
        fluid.addComponent("methane", 0.70);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.10);
        fluid.addComponent("n-butane", 0.05);
        fluid.addComponent("n-pentane", 0.05);
        fluid.setMixingRule("classic");

        ProcessSystem process = new ProcessSystem();

        Stream feed = new Stream("Feed", fluid);
        feed.setFlowRate(10000.0, "kg/hr");
        process.add(feed);

        // Separator performs an equilibrium split at the feed state, here 50 bara.
        Separator separator = new Separator("HP Separator", feed);
        separator.setInternalDiameter(2.0);
        process.add(separator);

        Compressor compressor =
            new Compressor("Gas Compressor", separator.getGasOutStream());
        compressor.setOutletPressure(80.0, "bara");
        compressor.setIsentropicEfficiency(0.75);
        process.add(compressor);

        process.run();

        double gasFlowKgPerHour =
            separator.getGasOutStream().getFlowRate("kg/hr");
        double liquidFlowKgPerHour =
            separator.getLiquidOutStream().getFlowRate("kg/hr");

        logger.info("Gas outlet: {} kg/hr", gasFlowKgPerHour);
        logger.info("Liquid outlet: {} kg/hr", liquidFlowKgPerHour);
        logger.info("Separated total: {} kg/hr", gasFlowKgPerHour + liquidFlowKgPerHour);
        logger.info("Compressor power: {} kW", compressor.getPower("kW"));
        logger.info(
            "Compressor outlet temperature: {} °C",
            compressor.getOutletStream().getTemperature("C"));
    }
}
```

A `Separator` does not impose a pressure reduction. Add a valve or another pressure-changing
unit upstream when the engineering case requires letdown before separation. The stream, separator,
compressor, and `ProcessSystem` remain available for extension into a larger flowsheet.

## Common gotchas

| Issue | Corrective action |
|-------|-------------------|
| `NullPointerException` or unavailable physical properties | Call `fluid.initProperties()` after the flash before reading density, viscosity, or conductivity. |
| Unexpected density | Request an explicit supported unit and verify the selected thermodynamic model, volume-correction setting, composition, temperature, and pressure. The unit string alone does not enable Peneloux correction. |
| Temperature seems wrong | Constructors use K unless an API explicitly accepts a unit. Convert with `T_K = T_C + 273.15` or use a setter with a unit argument. |
| Pressure basis is unclear | Constructors and single-argument process pressure setters use bara. Prefer overloads such as `setOutletPressure(80.0, "bara")` in user examples. |
| Mixing rule is missing | Select the mixing rule appropriate to the chosen model and mixture before the first flash. `"classic"` is the simple cubic-EOS choice used here. |
| Separator pressure is unexpected | A separator uses its inlet state; it does not perform an implicit letdown. Model pressure-changing equipment explicitly. |
| Flash does not converge | Verify component names, positive amounts, units, model applicability, and a physically plausible temperature-pressure state. Let the exception propagate while diagnosing it. |

## Engineering boundary

These examples demonstrate API composition and deterministic calculations, not fluid-model
selection, equipment sizing, process design approval, or safety certification. Validate the model,
composition, operating envelope, convergence, conservation, and results against suitable
engineering evidence before design use.

## Next steps

- **[Reading Fluid Properties](../thermo/reading_fluid_properties)** - Understand initialization
  levels and property access.
- **[Thermodynamic Models](../thermo/thermodynamic_models)** - Choose an equation of state.
- **[Process Equipment](../process/equipment/)** - Explore available unit operations.
- **[JavaDoc API](https://equinor.github.io/neqsim/javadoc/index.html)** - Read the API reference.

## API quick reference

Key interfaces to explore in the [JavaDoc](https://equinor.github.io/neqsim/javadoc/index.html):

| Interface | Purpose |
|-----------|---------|
| `SystemInterface` | Fluid composition, properties, and flash state |
| `PhaseInterface` | Individual phase properties |
| `ComponentInterface` | Pure-component and in-mixture properties |
| `ProcessEquipmentInterface` | Common process-equipment contract |
| `StreamInterface` | Composable material streams |
