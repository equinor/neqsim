# ProcessModule Class

Documentation for modular process units in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Creating Modules](#creating-modules)
- [Module Interface](#module-interface)
- [Built-in Modules](#built-in-modules)
- [Custom Modules](#custom-modules)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.processmodel.ProcessModule`

Process modules encapsulate complex process subsystems into reusable units. Benefits include:
- Reusability across different simulations
- Simplified flowsheet construction
- Better organization of large processes
- Easier maintenance and testing

---

## Creating Modules

### Basic Module

```java
import neqsim.process.processmodel.ProcessModule;

// Create module
ProcessModule module = new ProcessModule("Compression Train");

// Add equipment to module
module.add(scrubber);
module.add(compressor);
module.add(cooler);

// Set inlet/outlet
module.setInletStream(gasIn);
module.setOutletStream(cooler.getOutletStream());
```

### Adding to Process

```java
ProcessSystem process = new ProcessSystem();

// Add module to process
process.addModule(module);

// Connect to other equipment
module.setInletStream(feedStream);
Stream compressed = module.getOutletStream();
process.add(downstream equipment);
```

---

## Module Interface

### ModuleInterface Methods

```java
public interface ModuleInterface {
    // Identification
    String getName();
    void setName(String name);
    
    // Streams
    void setInletStream(StreamInterface stream);
    StreamInterface getInletStream();
    StreamInterface getOutletStream();
    
    // Execution
    void run();
    void runTransient(double dt);
    
    // Initialization
    void initializeModule();
    
    // Equipment access
    ProcessEquipmentInterface getUnit(String name);
    List<ProcessEquipmentInterface> getUnits();
}
```

---

## Built-in Modules

### Compressor Train Module

```java
// Multi-stage compression with intercooling
CompressorTrainModule compTrain = new CompressorTrainModule("HP Compression");
compTrain.setNumberOfStages(3);
compTrain.setOutletPressure(150.0, "bara");
compTrain.setIntercoolerTemperature(40.0, "C");
compTrain.setIsentropicEfficiency(0.78);

compTrain.setInletStream(feedGas);
process.addModule(compTrain);
```

### Separation Train Module

```java
// Multi-stage separation
SeparationTrainModule sepTrain = new SeparationTrainModule("Separation");
sepTrain.addStage(50.0, "bara");  // HP stage
sepTrain.addStage(15.0, "bara");  // MP stage
sepTrain.addStage(3.0, "bara");   // LP stage

sepTrain.setInletStream(wellFluid);
process.addModule(sepTrain);

// Get products
Stream exportGas = sepTrain.getGasOutStream();
Stream exportOil = sepTrain.getOilOutStream();
Stream prodWater = sepTrain.getWaterOutStream();
```

### Dehydration Module

```java
// TEG dehydration unit
DehydrationModule dehy = new DehydrationModule("Gas Dehy");
dehy.setWaterDewPoint(-20.0, "C");
dehy.setSolventType("TEG");

dehy.setInletStream(wetGas);
process.addModule(dehy);

Stream dryGas = dehy.getOutletStream();
```

---

## Custom Modules

### Creating Custom Module

```java
public class MyCustomModule extends ProcessModule {
    
    private Separator inlet Scrubber;
    private HeatExchanger heatEx;
    private Compressor compressor;
    
    public MyCustomModule(String name) {
        super(name);
        initializeEquipment();
    }
    
    private void initializeEquipment() {
        inletScrubber = new Separator("Inlet Scrubber");
        add(inletScrubber);
        
        heatEx = new HeatExchanger("Feed/Effluent HX");
        add(heatEx);
        
        compressor = new Compressor("Main Compressor");
        add(compressor);
    }
    
    @Override
    public void setInletStream(StreamInterface stream) {
        super.setInletStream(stream);
        inletScrubber.setInletStream(stream);
    }
    
    @Override
    public StreamInterface getOutletStream() {
        return compressor.getOutletStream();
    }
    
    public void setOutletPressure(double pressure, String unit) {
        compressor.setOutletPressure(pressure, unit);
    }
}
```

### Using Custom Module

```java
MyCustomModule customModule = new MyCustomModule("My Unit");
customModule.setInletStream(feed);
customModule.setOutletPressure(80.0, "bara");

process.addModule(customModule);
process.run();
```

---

## Module Communication

### Multiple Inlets

```java
public class TwoInletModule extends ProcessModule {
    private Mixer inletMixer;
    
    public void setInletStream1(StreamInterface stream) {
        inletMixer.addStream(stream);
    }
    
    public void setInletStream2(StreamInterface stream) {
        inletMixer.addStream(stream);
    }
}
```

### Multiple Outlets

```java
public class TwoOutletModule extends ProcessModule {
    private Splitter outletSplitter;
    
    public StreamInterface getOutletStream1() {
        return outletSplitter.getOutletStream(0);
    }
    
    public StreamInterface getOutletStream2() {
        return outletSplitter.getOutletStream(1);
    }
}
```

---

## Usage Examples

### LNG Train Module

```java
public class LNGTrainModule extends ProcessModule {
    
    private Cooler precooler;
    private HeatExchanger mainCryoExchanger;
    private Expander jt Expander;
    private Separator lngSeparator;
    
    public LNGTrainModule(String name) {
        super(name);
        
        precooler = new Cooler("Precooler");
        add(precooler);
        
        mainCryoExchanger = new HeatExchanger("MCHE");
        add(mainCryoExchanger);
        
        jtExpander = new Expander("JT Expander");
        add(jtExpander);
        
        lngSeparator = new Separator("LNG Separator");
        add(lngSeparator);
    }
    
    @Override
    public void setInletStream(StreamInterface stream) {
        super.setInletStream(stream);
        precooler.setInletStream(stream);
    }
    
    public void setLNGTemperature(double temp, String unit) {
        // Configure for target LNG temperature
    }
    
    public StreamInterface getLNGStream() {
        return lngSeparator.getLiquidOutStream();
    }
    
    public StreamInterface getBoilOffGas() {
        return lngSeparator.getGasOutStream();
    }
}

// Usage
LNGTrainModule lngTrain = new LNGTrainModule("LNG Production");
lngTrain.setInletStream(treatedGas);
lngTrain.setLNGTemperature(-162.0, "C");

process.addModule(lngTrain);
process.run();

Stream lng = lngTrain.getLNGStream();
System.out.println("LNG production: " + lng.getFlowRate("tonne/day") + " t/d");
```

### FPSO Topsides Module

```java
// Complete FPSO processing
ProcessSystem fpso = new ProcessSystem("FPSO Topsides");

// Separation module
SeparationTrainModule separation = new SeparationTrainModule("Separation");
separation.setInletStream(wellFluid);
fpso.addModule(separation);

// Gas compression
CompressorTrainModule gasComp = new CompressorTrainModule("Gas Compression");
gasComp.setInletStream(separation.getGasOutStream());
gasComp.setOutletPressure(200.0, "bara");
fpso.addModule(gasComp);

// Gas dehydration
DehydrationModule dehy = new DehydrationModule("Dehydration");
dehy.setInletStream(gasComp.getOutletStream());
fpso.addModule(dehy);

// Water treatment
WaterTreatmentModule waterTreat = new WaterTreatmentModule("Water Treatment");
waterTreat.setInletStream(separation.getWaterOutStream());
fpso.addModule(waterTreat);

fpso.run();
```

---

## Best Practices

1. **Self-Contained**: Modules should be self-contained with clear interfaces
2. **Documented Interfaces**: Document inlet/outlet requirements
3. **Reasonable Defaults**: Provide sensible default values
4. **Error Handling**: Validate inputs and provide clear error messages
5. **Testing**: Create unit tests for each module

---

## Related Documentation

- [ProcessSystem](process_system.md) - Process system management
- [Graph Simulation](graph_simulation.md) - Graph-based execution
- [Equipment Overview](../equipment/README.md) - Process equipment
