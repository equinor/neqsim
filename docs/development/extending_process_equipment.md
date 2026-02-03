---
title: Extending NeqSim with New Process Equipment
description: Complete guide to creating custom process equipment classes in NeqSim, including Java implementation patterns and Python integration.
---

# Extending NeqSim with New Process Equipment

This guide explains how to add new process equipment to NeqSim. Whether you're creating a specialized reactor, a custom separator design, or a novel unit operation, you'll need to understand the equipment architecture and implement the required interfaces.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Creating a Simple Equipment Class](#creating-a-simple-equipment-class)
3. [Implementing the run() Method](#implementing-the-run-method)
4. [Stream Connections](#stream-connections)
5. [Adding Mechanical Design](#adding-mechanical-design)
6. [Validation Framework](#validation-framework)
7. [Complete Example: Static Mixer](#complete-example-static-mixer)
8. [Testing Your Equipment](#testing-your-equipment)
9. [Python Integration](#python-integration)
10. [Best Practices](#best-practices)

---

## Architecture Overview

### Equipment Hierarchy

All process equipment in NeqSim follows this inheritance hierarchy:

```
SimulationInterface
    └── SimulationBaseClass
            └── ProcessEquipmentBaseClass (abstract)
                    ├── TwoPortEquipment (single inlet/outlet)
                    │   ├── Heater, Cooler
                    │   ├── Compressor, Pump
                    │   ├── ThrottlingValve
                    │   └── AdiabaticPipe
                    ├── Separator (multiple outlets)
                    │   ├── ThreePhaseSeparator
                    │   └── GasScrubber
                    ├── Mixer, Splitter
                    └── Your Custom Equipment
```

### Key Interfaces

| Interface | Purpose |
|-----------|---------|
| `ProcessEquipmentInterface` | Core interface all equipment must implement |
| `SimulationInterface` | Defines `run()`, `getName()`, `solved()` |
| `CapacityConstrainedEquipment` | Optional: for capacity-constrained equipment |
| `StateVectorProvider` | Optional: for ML/digital twin integration |

### Required Methods

Your equipment class must implement or inherit:

| Method | Purpose |
|--------|---------|
| `run(UUID id)` | Main calculation logic |
| `getThermoSystem()` | Return fluid state |
| `getOutletStream()` | Return outlet stream(s) |
| `getMechanicalDesign()` | Return mechanical design object |

---

## Creating a Simple Equipment Class

### Step 1: Create the Package and Class

Create your class in the appropriate package under `neqsim.process.equipment`:

```java
package neqsim.process.equipment.mixer;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * StaticMixer performs inline mixing of a main stream with an injection stream.
 * 
 * @author YourName
 * @version 1.0
 */
public class StaticMixer extends ProcessEquipmentBaseClass {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    /** Main process stream. */
    private StreamInterface mainStream;
    
    /** Injection stream (chemical, water, etc.). */
    private StreamInterface injectionStream;
    
    /** Mixed outlet stream. */
    private StreamInterface outletStream;
    
    /** Internal thermodynamic system for calculations. */
    private SystemInterface thermoSystem;
    
    /** Pressure drop across mixer in bar. */
    private double pressureDrop = 0.0;
    
    /** Mixing efficiency (0-1). */
    private double mixingEfficiency = 1.0;
    
    /**
     * Constructor for StaticMixer.
     *
     * @param name Equipment name
     */
    public StaticMixer(String name) {
        super(name);
    }
    
    /**
     * Constructor for StaticMixer with main stream.
     *
     * @param name Equipment name
     * @param mainStream Main process stream
     */
    public StaticMixer(String name, StreamInterface mainStream) {
        this(name);
        setMainStream(mainStream);
    }
}
```

### Step 2: Add Stream Setters and Getters

```java
    /**
     * Sets the main process stream.
     *
     * @param mainStream the main stream to set
     */
    public void setMainStream(StreamInterface mainStream) {
        this.mainStream = mainStream;
    }
    
    /**
     * Sets the injection stream.
     *
     * @param injectionStream the injection stream to set
     */
    public void setInjectionStream(StreamInterface injectionStream) {
        this.injectionStream = injectionStream;
    }
    
    /**
     * Gets the outlet stream.
     *
     * @return the mixed outlet stream
     */
    public StreamInterface getOutletStream() {
        return outletStream;
    }
    
    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem() {
        return thermoSystem;
    }
```

---

## Implementing the run() Method

The `run(UUID id)` method is where all calculations happen. This method is called by `ProcessSystem` when executing the flowsheet.

### Basic Pattern

```java
    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
        // Step 1: Validate inputs
        if (mainStream == null) {
            throw new RuntimeException("Main stream not set for " + getName());
        }
        
        // Step 2: Clone inlet fluid(s) to avoid modifying upstream
        thermoSystem = mainStream.getThermoSystem().clone();
        
        // Step 3: Mix streams if injection is present
        if (injectionStream != null) {
            SystemInterface injectionFluid = injectionStream.getThermoSystem();
            
            // Add components from injection stream
            for (int i = 0; i < injectionFluid.getNumberOfComponents(); i++) {
                String compName = injectionFluid.getComponent(i).getName();
                double moles = injectionFluid.getComponent(i).getNumberOfmoles();
                
                if (thermoSystem.hasComponent(compName)) {
                    thermoSystem.addComponent(compName, moles * mixingEfficiency);
                } else {
                    thermoSystem.addComponent(compName, moles * mixingEfficiency);
                }
            }
        }
        
        // Step 4: Apply pressure drop
        if (pressureDrop > 0) {
            thermoSystem.setPressure(thermoSystem.getPressure() - pressureDrop);
        }
        
        // Step 5: Run thermodynamic flash
        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
        ops.TPflash();
        thermoSystem.initProperties();
        
        // Step 6: Create/update outlet stream
        if (outletStream == null) {
            outletStream = new Stream("Mixed Stream", thermoSystem);
        } else {
            outletStream.setThermoSystem(thermoSystem);
        }
        outletStream.run(id);
        
        // Step 7: Mark as solved
        setCalculationIdentifier(id);
    }
```

### Handling Multiple Outlets

For equipment with multiple outlets (like separators):

```java
    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
        // ... inlet processing ...
        
        // Run flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
        ops.TPflash();
        thermoSystem.initProperties();
        
        // Create phase-specific outlet streams
        if (thermoSystem.hasPhaseType("gas")) {
            gasSystem = thermoSystem.phaseToSystem("gas");
            gasOutStream.setThermoSystem(gasSystem);
            gasOutStream.run(id);
        }
        
        if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
            liquidSystem = thermoSystem.phaseToSystem(1); // liquid phase
            liquidOutStream.setThermoSystem(liquidSystem);
            liquidOutStream.run(id);
        }
        
        setCalculationIdentifier(id);
    }
```

---

## Stream Connections

### Input Streams

Equipment receives input through stream setters:

```java
// In your equipment class
public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    // Initialize internal systems based on inlet
    thermoSystem = inletStream.getThermoSystem().clone();
}

// For multiple inlets, use a Mixer internally
private Mixer inletMixer = new Mixer("Internal Mixer");

public void addStream(StreamInterface newStream) {
    inletMixer.addStream(newStream);
    numberOfInputStreams++;
}
```

### Output Streams

Provide getters for downstream equipment:

```java
public StreamInterface getOutletStream() {
    return outletStream;
}

// For multiple outlets
public StreamInterface getGasOutStream() {
    return gasOutStream;
}

public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
}
```

### Using in ProcessSystem

```java
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", fluid);
process.add(feed);

StaticMixer mixer = new StaticMixer("Chemical Injection", feed);
mixer.setInjectionStream(chemicalStream);
mixer.setPressureDrop(0.5);
process.add(mixer);

Separator sep = new Separator("HP Sep", mixer.getOutletStream());
process.add(sep);

process.run();
```

---

## Adding Mechanical Design

### Create a MechanicalDesign Subclass

```java
package neqsim.process.mechanicaldesign.mixer;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for static mixer equipment.
 */
public class StaticMixerMechanicalDesign extends MechanicalDesign {
    
    private double pipeDiameter = 0.1; // meters
    private int numberOfElements = 6;
    private String materialGrade = "316SS";
    
    public StaticMixerMechanicalDesign(ProcessEquipmentInterface equipment) {
        super(equipment);
    }
    
    @Override
    public void calcDesign() {
        // Calculate design parameters based on process conditions
        double flowRate = getProcessEquipment().getFluid()
            .getFlowRate("m3/hr");
        
        // Size based on velocity (typical 1-3 m/s)
        double velocity = 2.0; // m/s target
        double area = flowRate / 3600.0 / velocity;
        pipeDiameter = Math.sqrt(4.0 * area / Math.PI);
        
        // Number of elements based on mixing requirements
        numberOfElements = (int) Math.ceil(pipeDiameter * 20);
    }
    
    // Getters and setters...
}
```

### Wire Up in Equipment Class

```java
public class StaticMixer extends ProcessEquipmentBaseClass {
    
    private StaticMixerMechanicalDesign mechanicalDesign;
    
    @Override
    public void initMechanicalDesign() {
        mechanicalDesign = new StaticMixerMechanicalDesign(this);
    }
    
    @Override
    public StaticMixerMechanicalDesign getMechanicalDesign() {
        if (mechanicalDesign == null) {
            initMechanicalDesign();
        }
        return mechanicalDesign;
    }
}
```

---

## Validation Framework

Implement `validateSetup()` to catch configuration errors early:

```java
import neqsim.util.validation.ValidationResult;

public class StaticMixer extends ProcessEquipmentBaseClass {
    
    /**
     * Validates equipment setup before running.
     *
     * @return validation result with any errors or warnings
     */
    @Override
    public ValidationResult validateSetup() {
        ValidationResult result = new ValidationResult(getName());
        
        // Check required inputs
        if (mainStream == null) {
            result.addError("mainStream", 
                "Main stream not connected",
                "Call setMainStream() with a valid stream");
        }
        
        // Check parameter ranges
        if (pressureDrop < 0) {
            result.addError("pressureDrop",
                "Pressure drop cannot be negative: " + pressureDrop,
                "Set pressureDrop to a positive value or zero");
        }
        
        if (mixingEfficiency < 0 || mixingEfficiency > 1) {
            result.addWarning("mixingEfficiency",
                "Efficiency outside 0-1 range: " + mixingEfficiency,
                "Consider setting efficiency between 0 and 1");
        }
        
        return result;
    }
}
```

---

## Complete Example: Static Mixer

Here's the complete implementation:

```java
package neqsim.process.equipment.mixer;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.mixer.StaticMixerMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.validation.ValidationResult;

/**
 * StaticMixer performs inline mixing of a main stream with an injection stream.
 * 
 * <p>Static mixers use fixed internal elements to create turbulent mixing without
 * moving parts. This class models the thermodynamic mixing and pressure drop.</p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class StaticMixer extends ProcessEquipmentBaseClass {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    /** Logger object for class. */
    static Logger logger = LogManager.getLogger(StaticMixer.class);
    
    private StreamInterface mainStream;
    private StreamInterface injectionStream;
    private StreamInterface outletStream;
    private SystemInterface thermoSystem;
    
    private double pressureDrop = 0.0;
    private double mixingEfficiency = 1.0;
    private StaticMixerMechanicalDesign mechanicalDesign;
    
    /**
     * Constructor for StaticMixer.
     *
     * @param name Equipment name
     */
    public StaticMixer(String name) {
        super(name);
    }
    
    /**
     * Constructor for StaticMixer with main stream.
     *
     * @param name Equipment name
     * @param mainStream Main process stream
     */
    public StaticMixer(String name, StreamInterface mainStream) {
        this(name);
        setMainStream(mainStream);
    }
    
    /**
     * Sets the main process stream.
     *
     * @param mainStream the main stream to set
     */
    public void setMainStream(StreamInterface mainStream) {
        this.mainStream = mainStream;
    }
    
    /**
     * Sets the injection stream.
     *
     * @param injectionStream the injection stream to set
     */
    public void setInjectionStream(StreamInterface injectionStream) {
        this.injectionStream = injectionStream;
    }
    
    /**
     * Gets the outlet stream.
     *
     * @return the mixed outlet stream
     */
    public StreamInterface getOutletStream() {
        return outletStream;
    }
    
    /**
     * Sets the pressure drop across the mixer.
     *
     * @param pressureDrop pressure drop in bar
     */
    public void setPressureDrop(double pressureDrop) {
        this.pressureDrop = pressureDrop;
    }
    
    /**
     * Gets the pressure drop across the mixer.
     *
     * @return pressure drop in bar
     */
    public double getPressureDrop() {
        return pressureDrop;
    }
    
    /**
     * Sets the mixing efficiency.
     *
     * @param efficiency mixing efficiency (0-1)
     */
    public void setMixingEfficiency(double efficiency) {
        this.mixingEfficiency = efficiency;
    }
    
    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem() {
        return thermoSystem;
    }
    
    /** {@inheritDoc} */
    @Override
    public void initMechanicalDesign() {
        mechanicalDesign = new StaticMixerMechanicalDesign(this);
    }
    
    /** {@inheritDoc} */
    @Override
    public StaticMixerMechanicalDesign getMechanicalDesign() {
        if (mechanicalDesign == null) {
            initMechanicalDesign();
        }
        return mechanicalDesign;
    }
    
    /** {@inheritDoc} */
    @Override
    public ValidationResult validateSetup() {
        ValidationResult result = new ValidationResult(getName());
        
        if (mainStream == null) {
            result.addError("mainStream", 
                "Main stream not connected",
                "Call setMainStream() with a valid stream");
        }
        
        if (pressureDrop < 0) {
            result.addError("pressureDrop",
                "Pressure drop cannot be negative: " + pressureDrop,
                "Set pressureDrop to a positive value or zero");
        }
        
        if (mixingEfficiency < 0 || mixingEfficiency > 1) {
            result.addWarning("mixingEfficiency",
                "Efficiency outside 0-1 range: " + mixingEfficiency,
                "Consider setting efficiency between 0 and 1");
        }
        
        return result;
    }
    
    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
        // Validate setup
        ValidationResult validation = validateSetup();
        if (validation.hasErrors()) {
            throw new RuntimeException("Validation failed: " + 
                validation.getErrors().get(0).getMessage());
        }
        
        // Clone inlet fluid
        thermoSystem = mainStream.getThermoSystem().clone();
        
        // Mix streams if injection is present
        if (injectionStream != null) {
            SystemInterface injectionFluid = injectionStream.getThermoSystem();
            
            for (int i = 0; i < injectionFluid.getNumberOfComponents(); i++) {
                String compName = injectionFluid.getComponent(i).getName();
                double moles = injectionFluid.getComponent(i).getNumberOfmoles();
                
                thermoSystem.addComponent(compName, moles * mixingEfficiency);
            }
        }
        
        // Apply pressure drop
        if (pressureDrop > 0) {
            thermoSystem.setPressure(thermoSystem.getPressure() - pressureDrop);
        }
        
        // Run thermodynamic flash
        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
        ops.TPflash();
        thermoSystem.initProperties();
        
        // Create/update outlet stream
        if (outletStream == null) {
            outletStream = new Stream(getName() + " outlet", thermoSystem);
        } else {
            outletStream.setThermoSystem(thermoSystem);
        }
        outletStream.run(id);
        
        setCalculationIdentifier(id);
    }
}
```

---

## Testing Your Equipment

### Unit Test Example

```java
package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class StaticMixerTest {
    
    private SystemSrkEos mainFluid;
    private SystemSrkEos injectionFluid;
    
    @BeforeEach
    void setUp() {
        mainFluid = new SystemSrkEos(298.15, 50.0);
        mainFluid.addComponent("methane", 0.9);
        mainFluid.addComponent("ethane", 0.1);
        mainFluid.setMixingRule("classic");
        
        injectionFluid = new SystemSrkEos(298.15, 50.0);
        injectionFluid.addComponent("MEG", 1.0);
        injectionFluid.setMixingRule("classic");
    }
    
    @Test
    void testBasicMixing() {
        ProcessSystem process = new ProcessSystem();
        
        Stream mainStream = new Stream("Main", mainFluid);
        mainStream.setFlowRate(1000.0, "kg/hr");
        process.add(mainStream);
        
        Stream injection = new Stream("Injection", injectionFluid);
        injection.setFlowRate(10.0, "kg/hr");
        process.add(injection);
        
        StaticMixer mixer = new StaticMixer("Chemical Injection", mainStream);
        mixer.setInjectionStream(injection);
        mixer.setPressureDrop(0.5);
        process.add(mixer);
        
        process.run();
        
        // Verify outlet contains all components
        assertTrue(mixer.getOutletStream().getFluid().hasComponent("MEG"));
        assertTrue(mixer.getOutletStream().getFluid().hasComponent("methane"));
        
        // Verify pressure drop
        assertEquals(49.5, mixer.getOutletStream().getPressure(), 0.01);
        
        // Verify mass balance
        double inMass = mainStream.getFlowRate("kg/hr") + injection.getFlowRate("kg/hr");
        double outMass = mixer.getOutletStream().getFlowRate("kg/hr");
        assertEquals(inMass, outMass, 0.1);
    }
    
    @Test
    void testValidation() {
        StaticMixer mixer = new StaticMixer("Test");
        // No main stream set
        
        var result = mixer.validateSetup();
        assertTrue(result.hasErrors());
    }
}
```

---

## Python Integration

### Using Your Equipment from Python

Once your Java equipment is compiled into NeqSim, you can use it from Python via neqsim-python:

```python
from neqsim import jneqsim

# Import your custom equipment
StaticMixer = jneqsim.process.equipment.mixer.StaticMixer

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")

# Create injection fluid
injection_fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
injection_fluid.addComponent("MEG", 1.0)
injection_fluid.setMixingRule("classic")

# Build process
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream

process = ProcessSystem()

main_stream = Stream("Main Feed", fluid)
main_stream.setFlowRate(1000.0, "kg/hr")
process.add(main_stream)

injection = Stream("MEG Injection", injection_fluid)
injection.setFlowRate(10.0, "kg/hr")
process.add(injection)

mixer = StaticMixer("Chemical Injection", main_stream)
mixer.setInjectionStream(injection)
mixer.setPressureDrop(0.5)
process.add(mixer)

# Run and get results
process.run()

print(f"Outlet temperature: {mixer.getOutletStream().getTemperature() - 273.15:.1f} °C")
print(f"Outlet pressure: {mixer.getOutletStream().getPressure():.1f} bara")
print(f"MEG in outlet: {mixer.getOutletStream().getFluid().hasComponent('MEG')}")
```

### Implementing Java Interfaces from Python

For callbacks and custom objective functions, you can implement Java interfaces in Python using JPype:

```python
from jpype import JImplements, JOverride

@JImplements("neqsim.process.util.optimizer.ObjectiveFunction")
class CustomObjective:
    @JOverride
    def evaluate(self, process):
        mixer = process.getUnit("Chemical Injection")
        return mixer.getPressureDrop()
    
    @JOverride
    def getName(self):
        return "Mixer Pressure Drop"
    
    @JOverride
    def getDirection(self):
        return jneqsim.process.util.optimizer.ObjectiveFunction.Direction.MINIMIZE
```

---

## Best Practices

### 1. Always Clone Input Fluids

```java
// CORRECT - clone to avoid modifying upstream
thermoSystem = inletStream.getThermoSystem().clone();

// WRONG - modifies upstream equipment
thermoSystem = inletStream.getThermoSystem();
thermoSystem.setPressure(newPressure); // Changes inlet!
```

### 2. Handle Missing Phases Gracefully

```java
if (thermoSystem.hasPhaseType("gas")) {
    gasOutStream.setThermoSystem(thermoSystem.phaseToSystem("gas"));
} else {
    // Create empty gas stream or handle appropriately
    logger.warn("No gas phase in " + getName());
}
```

### 3. Support Unit Conversions

```java
public double getPressureDrop(String unit) {
    if (unit.equals("bar") || unit.equals("bara")) {
        return pressureDrop;
    } else if (unit.equals("psi") || unit.equals("psia")) {
        return pressureDrop * 14.5038;
    }
    throw new IllegalArgumentException("Unknown unit: " + unit);
}
```

### 4. Implement Serialization

Ensure all fields are serializable for `ProcessEquipmentBaseClass.copy()`:

```java
// Mark non-serializable fields as transient
private transient SomeNonSerializableClass tempObject;

// Or ensure custom classes implement Serializable
private MyCustomClass config; // Must implement Serializable
```

### 5. Add Logging for Debugging

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StaticMixer extends ProcessEquipmentBaseClass {
    static Logger logger = LogManager.getLogger(StaticMixer.class);
    
    @Override
    public void run(UUID id) {
        logger.debug("Running {} with inlet T={} K, P={} bar",
            getName(),
            mainStream.getTemperature(),
            mainStream.getPressure());
        
        // ... calculations ...
        
        logger.debug("{} completed: outlet T={} K, P={} bar",
            getName(),
            outletStream.getTemperature(),
            outletStream.getPressure());
    }
}
```

### 6. Document with JavaDoc

```java
/**
 * Calculates the outlet conditions after mixing.
 * 
 * <p>The mixing process is adiabatic unless heat input is specified.
 * Pressure drop is applied after mixing.</p>
 *
 * @param id Calculation identifier for tracking
 * @throws RuntimeException if main stream is not connected
 * @see ProcessEquipmentBaseClass#run(UUID)
 */
@Override
public void run(UUID id) {
    // ...
}
```

---

## See Also

- [Process Simulation Introduction](../wiki/process_simulation)
- [Equipment Hierarchy](../process/README)
- [Mechanical Design Framework](../process/mechanical_design)
- [Validation Framework](../integration/ai_validation_framework)
- [Python Extension Patterns](python_extension_patterns)

---

*Document last updated: February 2026*
