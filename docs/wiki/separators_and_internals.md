# Separators and Internals Handling in NeqSim

This document describes the separator equipment modeling capabilities in NeqSim, including support for primary separation devices, demisting internals, and mechanical design calculations.

## Overview

NeqSim provides a comprehensive framework for modeling separators (gas scrubbers, two-phase and three-phase separators) with:

- **Thermodynamic separation** calculations (gas/liquid/water)
- **Primary separation devices** (inlet vanes, cyclones, meshpads)
- **Demisting internals** with pressure drop and carry-over calculations
- **Mechanical design** calculations (sizing, wall thickness, weight)
- **Separator sections** (manways, nozzles, vanes, meshpads)

## Separator Classes

### Base Separator

The `Separator` class ([Separator.java](src/main/java/neqsim/process/equipment/separator/Separator.java)) is the base class for all separators. It provides:

```java
// Create a simple separator
Separator separator = new Separator("MySeparator", inletStream);
separator.setInternalDiameter(2.0);      // m
separator.setSeparatorLength(8.0);        // m
separator.setOrientation("horizontal");   // or "vertical"
separator.run();

// Access outlet streams
StreamInterface gasOut = separator.getGasOutStream();
StreamInterface liquidOut = separator.getLiquidOutStream();
```

### Gas Scrubber

The `GasScrubber` class is designed for gas-dominated separation with default vertical orientation:

```java
GasScrubber scrubber = new GasScrubber("Scrubber", inletStream);
scrubber.run();

// Gas scrubbers automatically use vertical orientation
assertEquals("vertical", scrubber.getOrientation());
```

### GasScrubberSimple

The `GasScrubberSimple` class adds support for liquid carry-over calculations based on separator sections:

```java
GasScrubberSimple scrubber = new GasScrubberSimple("scrubber", inletStream);
scrubber.setInternalDiameter(3.75);       // m
scrubber.setSeparatorLength(4.0);          // m

// Add separator sections (triggers carry-over calculation)
scrubber.addSeparatorSection("inlet vane", "vane");
scrubber.getSeparatorSection("inlet vane").setCalcEfficiency(true);

scrubber.addSeparatorSection("top mesh", "meshpad");

// Run and calculate carry-over
scrubber.run();
double carryOver = scrubber.calcLiquidCarryoverFraction();
```

## Primary Separation Devices

Primary separation devices are inlet devices that perform initial liquid-gas separation through momentum effects. They are located in the `neqsim.process.mechanicaldesign.separator.primaryseparation` package.

### PrimarySeparation (Base Class)

Base class providing:
- Inlet nozzle diameter specification
- Reference to parent separator
- Momentum and carry-over calculations

### InletVane

Basic inlet vane with deflection-based separation:

```java
InletVane vane = new InletVane("InletVane1", 0.1, 4.0);  // name, nozzle diameter, expansion ratio
separator.setPrimarySeparation(vane);
```

The geometrical expansion ratio represents the ratio of vane open area to nozzle area. Higher ratios provide better separation efficiency.

### InletVaneWithMeshpad

Combined vane and meshpad configuration for enhanced separation:

```java
InletVaneWithMeshpad vaneMeshpad = new InletVaneWithMeshpad(
    "InletVaneMeshpad1", 
    0.1,      // inlet nozzle diameter (m)
    0.3,      // vane-to-meshpad distance (m)
    0.25      // free distance above meshpad (m)
);
separator.setPrimarySeparation(vaneMeshpad);

// Access carry-over calculation
double carryOver = vaneMeshpad.calcLiquidCarryOver();
```

The carry-over calculation accounts for:
- Vane deflection efficiency
- Inlet velocity effects
- Settling time between vane and meshpad
- Coalescence in the meshpad

### InletCyclones

Cyclone-based primary separation using centrifugal force:

```java
InletCyclones cyclones = new InletCyclones(
    "InletCyclones1",
    0.15,     // inlet nozzle diameter (m)
    4,        // number of cyclones in parallel
    0.2       // cyclone diameter (m)
);
separator.setPrimarySeparation(cyclones);

// Calculate separation efficiency
double efficiency = cyclones.calcSeparationEfficiency(
    gasDensity, liquidDensity, inletVelocity, liquidViscosity
);
```

## Demisting Internals

Demisting internals reduce liquid carry-over by providing surfaces where droplets coalesce. They are located in `neqsim.process.mechanicaldesign.separator.internals`.

### DemistingInternal

Basic demisting internal without drainage:

```java
DemistingInternal internal = new DemistingInternal(
    0.5,      // area (m²)
    2.5       // Euler number for pressure drop
);

// Add to mechanical design
GasScrubberMechanicalDesign mechDesign = scrubber.getMechanicalDesign();
mechDesign.addDemistingInternal(internal);

// Calculate performance
double velocity = internal.calcGasVelocity(volumetricFlow);   // m³/s -> m/s
double pressureDrop = internal.calcPressureDrop(gasDensity, velocity);  // Pa
double carryOver = internal.calcLiquidCarryOver();
double efficiency = internal.calcEfficiency();  // Separation efficiency (0 to 1)
```

**Input Validation:**

Setters validate input and throw `IllegalArgumentException` for invalid values:
- `setArea(area)` - throws if `area <= 0`
- `setEuNumber(euNumber)` - throws if `euNumber < 0`

**Euler Number and Pressure Drop:**

The pressure drop is calculated using:
```
Δp = Eu × ρ × v²
```
Where:
- Eu = Euler number (dimensionless pressure drop coefficient)
- ρ = gas density (kg/m³)
- v = gas velocity (m/s)

### DemistingInternalWithDrainage

Extended demisting internal with drainage pipes for improved liquid removal:

```java
DemistingInternalWithDrainage internalWithDrainage = new DemistingInternalWithDrainage(
    0.3,      // area (m²)
    2.0,      // Euler number
    0.85      // drainage efficiency (0 to 1)
);

mechDesign.addDemistingInternal(internalWithDrainage);

// Drainage efficiency reduces carry-over
double carryOver = internalWithDrainage.calcLiquidCarryOver();
// carryOver = baseCarryOver × (1 - drainageEfficiency)
```

### Managing Multiple Internals

The mechanical design class supports multiple demisting internals:

```java
GasScrubberMechanicalDesign mechDesign = scrubber.getMechanicalDesign();

// Add multiple internals
mechDesign.addDemistingInternal(internal1);
mechDesign.addDemistingInternal(internal2);

// Query internals
int count = mechDesign.getNumberOfDemistingInternals();
double totalArea = mechDesign.getTotalDemistingArea();

// Calculate total performance
double totalCarryOver = mechDesign.calcTotalLiquidCarryOver(gasDensity, liquidDensity, inletContent);
double totalPressureDrop = mechDesign.calcTotalPressureDrop(gasDensity);
```

## Separator Sections

Separator sections define physical elements within the separator vessel. They are located in `neqsim.process.equipment.separator.sectiontype`.

### Available Section Types

| Type | Description |
|------|-------------|
| `manway` | Access manway section |
| `nozzle` | Process nozzle section |
| `vane` | Vane-type separation section |
| `meshpad` | Mesh pad section |
| `valve` | Internal valve section |
| `packed` | Packed section |

### Using Separator Sections

```java
GasScrubberSimple scrubber = new GasScrubberSimple("scrubber", inletStream);

// Add sections by name and type
scrubber.addSeparatorSection("bottom manway", "manway");
scrubber.addSeparatorSection("inlet nozzle", "nozzle");
scrubber.addSeparatorSection("inlet vane", "vane");
scrubber.addSeparatorSection("top mesh", "meshpad");
scrubber.addSeparatorSection("top manway", "manway");

// Configure section properties
SeparatorSection vaneSection = scrubber.getSeparatorSection("inlet vane");
vaneSection.setCalcEfficiency(true);       // Enable efficiency calculation
vaneSection.setEfficiency(0.95);           // Set manual efficiency
vaneSection.setPressureDrop(0.0066);       // bar

// Configure mechanical design
SepDesignSection mechSection = vaneSection.getMechanicalDesign();
mechSection.setNominalSize("DN 500");
mechSection.setANSIclass(300);
```

### Section Efficiency

Each separator section can have efficiency calculations enabled:

```java
// Enable calculated efficiency (based on internal correlations)
scrubber.getSeparatorSection(1).setCalcEfficiency(true);

// Or set a fixed efficiency value
scrubber.getSeparatorSection("inlet vane").setEfficiency(0.92);
```

The overall liquid carry-over is calculated as the product of individual section efficiencies:

```
carryOver = ∏(1 - efficiency_i) for all sections
```

## Mechanical Design

### Gas Scrubber Mechanical Design

The `GasScrubberMechanicalDesign` class calculates vessel sizing and weight:

```java
GasScrubber scrubber = new GasScrubber("Scrubber", inletStream);
scrubber.run();

GasScrubberMechanicalDesign mechDesign = scrubber.getMechanicalDesign();
mechDesign.setMaxOperationPressure(70.0);  // bar

// Set design standards
mechDesign.setDesignStandard("pressure vessel design code", "ASME - Loss Factor");
mechDesign.setDesignStandard("gas scrubber process design", "Statoil Gas Scrubber");

// Calculate design
mechDesign.calcDesign();

// Access results
double innerDiameter = mechDesign.getInnerDiameter();
double wallThickness = mechDesign.getWallThickness();
double totalWeight = mechDesign.getWeightTotal();
double vesselWeight = mechDesign.getWeigthVesselShell();
double internalsWeight = mechDesign.getWeigthInternals();

// Module dimensions
double moduleLength = mechDesign.getModuleLength();
double moduleWidth = mechDesign.getModuleWidth();
double moduleHeight = mechDesign.getModuleHeight();
```

### Design Standards

Design standards can be applied for standardized calculations:

```java
mechDesign.setDesignStandard("pressure vessel design code", "ASME - Loss Factor");
mechDesign.setDesignStandard("separator process design", "API12J");
mechDesign.setDesignStandard("gas scrubber process design", "Statoil Gas Scrubber");
mechDesign.setDesignStandard("material plate design codes", "Carbon Steel Plates");
```

## Inlet Stream Properties

The Separator class provides methods to access inlet stream properties used in separation calculations:

```java
// Gas velocity through inlet nozzle (requires primary separation to be set)
double inletVelocity = separator.getInletGasVelocity();

// Gas volumetric flow rate
double volumetricFlow = separator.getInletGasVolumetricFlow();

// Density values
double gasDensity = separator.getInletGasDensity();
double liquidDensity = separator.getInletLiquidDensity();

// Liquid content (volumetric fraction)
double liquidContent = separator.getInletLiquidContent();
```

These properties are automatically cached during `run()` and used by the primary separation and demisting internal calculations.

## Complete Example

```java
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternal;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternalWithDrainage;
import neqsim.process.mechanicaldesign.separator.primaryseparation.InletVaneWithMeshpad;
import neqsim.thermo.system.SystemSrkEos;

public class ScrubberExample {
    public static void main(String[] args) {
        // Create thermodynamic system
        SystemInterface system = new SystemSrkEos(273.15 + 25, 60.0);
        system.addComponent("methane", 0.8);
        system.addComponent("ethane", 0.15);
        system.addComponent("propane", 0.05);
        system.setMixingRule("classic");

        // Create inlet stream
        Stream inlet = new Stream("inlet", system);
        inlet.setFlowRate(1000.0, "kg/hr");
        inlet.run();

        // Create gas scrubber
        GasScrubber scrubber = new GasScrubber("HP Scrubber", inlet);
        scrubber.setInternalDiameter(2.0);
        scrubber.setSeparatorLength(6.0);

        // Configure primary separation
        InletVaneWithMeshpad primarySep = new InletVaneWithMeshpad(
            "InletDevice", 0.15, 0.3, 0.25
        );
        scrubber.setPrimarySeparation(primarySep);

        // Add separator sections
        scrubber.addSeparatorSection("inlet manway", "manway");
        scrubber.addSeparatorSection("inlet vane", "vane");
        scrubber.getSeparatorSection("inlet vane").setCalcEfficiency(true);
        scrubber.addSeparatorSection("demister mesh", "meshpad");
        scrubber.addSeparatorSection("outlet nozzle", "nozzle");

        // Run process
        scrubber.run();

        // Get mechanical design
        GasScrubberMechanicalDesign mechDesign = scrubber.getMechanicalDesign();
        mechDesign.setMaxOperationPressure(70.0);

        // Add demisting internals
        DemistingInternal demister1 = new DemistingInternal(0.5, 2.5);
        DemistingInternalWithDrainage demister2 = 
            new DemistingInternalWithDrainage(0.3, 2.0, 0.9);
        mechDesign.addDemistingInternal(demister1);
        mechDesign.addDemistingInternal(demister2);

        // Calculate mechanical design
        mechDesign.calcDesign();

        // Print results
        System.out.println("=== Scrubber Design Results ===");
        System.out.println("Inner Diameter: " + mechDesign.getInnerDiameter() + " m");
        System.out.println("Wall Thickness: " + mechDesign.getWallThickness() + " m");
        System.out.println("Total Weight: " + mechDesign.getWeightTotal() + " kg");
        System.out.println("Demisting Area: " + mechDesign.getTotalDemistingArea() + " m²");

        // Access separation performance
        System.out.println("\n=== Separation Performance ===");
        System.out.println("Primary separation carry-over: " + primarySep.calcLiquidCarryOver());
        System.out.println("Gas load factor: " + scrubber.getGasLoadFactor());
    }
}
```

## API Reference

### Key Classes and Interfaces

| Class | Package | Description |
|-------|---------|-------------|
| `Separator` | `neqsim.process.equipment.separator` | Base separator class |
| `GasScrubber` | `neqsim.process.equipment.separator` | Vertical gas scrubber |
| `GasScrubberSimple` | `neqsim.process.equipment.separator` | Scrubber with carry-over calc |
| `SeparatorSection` | `neqsim.process.equipment.separator.sectiontype` | Separator section definition |
| `PrimarySeparation` | `neqsim.process.mechanicaldesign.separator.primaryseparation` | Base primary separation device |
| `InletVane` | `neqsim.process.mechanicaldesign.separator.primaryseparation` | Vane inlet device |
| `InletVaneWithMeshpad` | `neqsim.process.mechanicaldesign.separator.primaryseparation` | Combined vane/meshpad |
| `InletCyclones` | `neqsim.process.mechanicaldesign.separator.primaryseparation` | Cyclone inlet device |
| `DemistingInternal` | `neqsim.process.mechanicaldesign.separator.internals` | Basic demisting internal |
| `DemistingInternalWithDrainage` | `neqsim.process.mechanicaldesign.separator.internals` | Demisting with drainage |
| `SeparatorMechanicalDesign` | `neqsim.process.mechanicaldesign.separator` | Base mechanical design |
| `GasScrubberMechanicalDesign` | `neqsim.process.mechanicaldesign.separator` | Gas scrubber mechanical design |

## See Also

- [Process Equipment Overview](getting_started.md)
- [Pump Theory and Implementation](pump_theory_and_implementation.md)
- [Heat Exchanger Mechanical Design](heat_exchanger_mechanical_design.md)
