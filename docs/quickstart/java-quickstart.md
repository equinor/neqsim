---
title: Java Quickstart
description: Get started with NeqSim in Java. Maven setup, first flash calculation, and first process simulation in under 10 minutes.
---

# Java Quickstart

Get NeqSim running in your Java project in under 10 minutes.

## Step 1: Add Maven Dependency (2 minutes)

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.equinor.neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>3.0.0</version>
</dependency>
```

Or with Gradle (`build.gradle`):

```groovy
implementation 'com.equinor.neqsim:neqsim:3.0.0'
```

## Step 2: First Flash Calculation (3 minutes)

Create `FirstCalculation.java`:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FirstCalculation {
    public static void main(String[] args) {
        // Create a natural gas at 25°C and 50 bar
        SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);  // Temperature in Kelvin!
        
        // Add components (mole fractions)
        fluid.addComponent("methane", 0.85);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.05);
        
        // IMPORTANT: Always set mixing rule
        fluid.setMixingRule("classic");
        
        // Run flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Initialize properties (required for most property access)
        fluid.initProperties();
        
        // Print results
        System.out.println("Number of phases: " + fluid.getNumberOfPhases());
        System.out.println("Density: " + fluid.getDensity("kg/m3") + " kg/m³");
        System.out.println("Z-factor: " + fluid.getZ());
        
        // Pretty print full results
        fluid.prettyPrint();
    }
}
```

Run it:
```bash
mvn compile exec:java -Dexec.mainClass="FirstCalculation"
```

## Step 3: First Process Simulation (5 minutes)

Create `FirstProcess.java`:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.thermo.system.SystemSrkEos;

public class FirstProcess {
    public static void main(String[] args) {
        // 1. Create feed fluid
        SystemSrkEos fluid = new SystemSrkEos(273.15 + 30, 50.0);  // 30°C, 50 bara
        fluid.addComponent("methane", 0.70);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.10);
        fluid.addComponent("n-butane", 0.05);
        fluid.addComponent("n-pentane", 0.05);
        fluid.setMixingRule("classic");
        
        // 2. Create process system
        ProcessSystem process = new ProcessSystem();
        
        // 3. Add feed stream
        Stream feed = new Stream("Feed", fluid);
        feed.setFlowRate(10000, "kg/hr");
        process.add(feed);
        
        // 4. Add separator (flash to 20 bar)
        Separator separator = new Separator("HP Separator", feed);
        separator.setInternalDiameter(2.0);  // meters
        process.add(separator);
        
        // 5. Add compressor on gas outlet
        Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
        compressor.setOutletPressure(80.0);  // bara
        compressor.setIsentropicEfficiency(0.75);
        process.add(compressor);
        
        // 6. Run the process
        process.run();
        
        // 7. Print results
        System.out.println("=== SEPARATOR ===");
        System.out.println("Gas out: " + separator.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
        System.out.println("Liquid out: " + separator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
        
        System.out.println("\n=== COMPRESSOR ===");
        System.out.println("Power: " + compressor.getPower("kW") + " kW");
        System.out.println("Outlet T: " + (compressor.getOutletStream().getTemperature() - 273.15) + " °C");
    }
}
```

## Common Gotchas

| Issue | Solution |
|-------|----------|
| `NullPointerException` on properties | Call `fluid.initProperties()` after flash |
| Wrong density values | Use `getDensity("kg/m3")` with unit for Peneloux correction |
| Temperature seems wrong | NeqSim uses **Kelvin** internally. Convert: `T_K = T_C + 273.15` |
| Missing mixing rule | Always call `setMixingRule("classic")` after adding components |
| Flash doesn't converge | Check if T,P are in valid range for your composition |

## Next Steps

- **[Reading Fluid Properties](../thermo/reading_fluid_properties)** - Understanding init levels and property access
- **[Thermodynamic Models](../thermo/thermodynamic_models)** - Choosing the right equation of state
- **[Process Equipment](../process/equipment/README)** - All available unit operations
- **[JavaDoc API](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)** - Complete API reference

## API Quick Reference

Key interfaces to explore in the [JavaDoc](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html):

| Interface | Purpose |
|-----------|---------|
| `SystemInterface` | Fluid/mixture - composition, properties, flash |
| `PhaseInterface` | Individual phase properties |
| `ComponentInterface` | Pure component properties |
| `ProcessEquipmentInterface` | Base for all equipment |
| `StreamInterface` | Material streams |
