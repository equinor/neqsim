# Saving and Loading Process Simulations in NeqSim

This document describes how to save and load process simulations in NeqSim using JSON/XML serialization. NeqSim uses the **XStream** library for object serialization, supporting both uncompressed XML and compressed `.neqsim` file formats.

## Table of Contents

- [Overview](#overview)
- [Serialization Options](#serialization-options)
- [Java API](#java-api)
  - [Saving Process Models](#saving-process-models)
  - [Loading Process Models](#loading-process-models)
  - [State-Based Serialization](#state-based-serialization)
- [Python API (neqsim-python)](#python-api-neqsim-python)
  - [Saving and Loading .neqsim Files](#saving-and-loading-neqsim-files)
  - [Saving and Loading XML Files](#saving-and-loading-xml-files)
- [Compressed Files (.neqsim format)](#compressed-files-neqsim-format)
- [JSON State Export/Import](#json-state-exportimport)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Overview

NeqSim provides multiple ways to persist and restore process simulations:

| Method | Format | Compression | Use Case |
|--------|--------|-------------|----------|
| `NeqSimXtream.saveNeqsim()` | XML in ZIP | ✅ Yes | Production - compact storage |
| `save_xml()` / `open_xml()` | Plain XML | ❌ No | Debugging - human-readable |
| `ProcessSystemState` | JSON | ✅ Optional (.neqsim) | Version control - Git-friendly |

The underlying serialization technology is [XStream](https://x-stream.github.io/), a powerful Java library that converts objects to XML and back without requiring any modifications to the objects.

---

## Serialization Options

### 1. Compressed .neqsim Files (Recommended)

The `.neqsim` file format stores the XML serialization inside a ZIP archive, significantly reducing file size for large process models. This is the recommended format for production use.

**Advantages:**
- Smaller file sizes (typically 5-20x compression)
- Single file contains complete model state
- Compatible with both Java and Python APIs

### 2. Plain XML Files

Uncompressed XML files are useful for debugging and manual inspection but can become very large for complex simulations.

### 3. JSON State Files

JSON-based state export provides a human-readable, Git-friendly format for version control and lifecycle management.

---

## Java API

### Saving Process Models

#### Using NeqSimXtream (Compressed Format)

```java
import neqsim.util.serialization.NeqSimXtream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create and configure a process
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 10.0);
fluid.addComponent("ethane", 5.0);
fluid.setMixingRule(2);

ProcessSystem process = new ProcessSystem("My Process");
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
process.add(feed);
process.run();

// Save to compressed .neqsim file
boolean success = NeqSimXtream.saveNeqsim(process, "my_process.neqsim");
if (success) {
    System.out.println("Process saved successfully!");
}
```

#### Using XStream Directly (Uncompressed XML)

```java
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;

// Create XStream instance
XStream xstream = new XStream();

// Serialize to XML string
String xml = xstream.toXML(process);

// Save to file
try (FileWriter writer = new FileWriter("my_process.xml")) {
    writer.write(xml);
}
```

### Loading Process Models

#### Loading Compressed .neqsim Files

```java
import neqsim.util.serialization.NeqSimXtream;
import neqsim.process.processmodel.ProcessSystem;

try {
    // Load from .neqsim file
    Object loaded = NeqSimXtream.openNeqsim("my_process.neqsim");
    
    if (loaded instanceof ProcessSystem) {
        ProcessSystem process = (ProcessSystem) loaded;
        
        // Run the restored process
        process.run();
        
        System.out.println("Process loaded: " + process.getName());
        System.out.println("Number of units: " + process.getUnitOperations().size());
    }
} catch (IOException e) {
    System.err.println("Failed to load process: " + e.getMessage());
}
```

#### Loading Uncompressed XML

```java
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;

// Create XStream instance with security permissions
XStream xstream = new XStream();
xstream.addPermission(AnyTypePermission.ANY);

// Read XML from file
String xmlContent = new String(Files.readAllBytes(Paths.get("my_process.xml")));

// Deserialize
ProcessSystem process = (ProcessSystem) xstream.fromXML(xmlContent);
process.run();
```

### State-Based Serialization

NeqSim provides a `ProcessSystemState` class for JSON-based state management, ideal for version control and lifecycle tracking:

```java
import neqsim.process.processmodel.lifecycle.ProcessSystemState;
import neqsim.process.processmodel.ProcessSystem;
import java.io.File;

// Create a state snapshot from existing process
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

// Add metadata
state.setVersion("1.2.3");
state.setDescription("Post-commissioning tuned model");

// Save to JSON file (uncompressed) - using String path or File
state.saveToFile("asset_model_v1.2.3.json");
state.saveToFile(new File("asset_model_v1.2.3.json"));

// Later: Load the state - using String path or File
ProcessSystemState loadedState = ProcessSystemState.loadFromFile("asset_model_v1.2.3.json");
ProcessSystemState loadedState = ProcessSystemState.loadFromFile(new File("asset_model_v1.2.3.json"));

// Convert to ProcessSystem (limited reconstruction)
ProcessSystem restoredProcess = loadedState.toProcessSystem();

// Or apply state to existing process with matching structure
loadedState.applyTo(existingProcess);
```

#### Compressed State Files

For large process models, use GZIP compression to reduce file sizes (typically 5-20x reduction):

```java
// Save to compressed file (.neqsim) - using String path or File
state.saveToCompressedFile("asset_model_v1.2.3.neqsim");
state.saveToCompressedFile(new File("asset_model_v1.2.3.neqsim"));

// Load from compressed file - using String path or File
ProcessSystemState loadedState = ProcessSystemState.loadFromCompressedFile("asset_model_v1.2.3.neqsim");
ProcessSystemState loadedState = ProcessSystemState.loadFromCompressedFile(new File("asset_model_v1.2.3.neqsim"));

// Auto-detect compression based on file extension - using String path or File
state.saveToFileAuto("asset_model.neqsim");  // Compressed
state.saveToFileAuto("asset_model.json");    // Uncompressed
state.saveToFileAuto(new File("asset_model.neqsim"));  // Also works with File

ProcessSystemState loaded = ProcessSystemState.loadFromFileAuto("asset_model.neqsim");
ProcessSystemState loaded = ProcessSystemState.loadFromFileAuto(new File("asset_model.neqsim"));
```

**When to use compressed state files (.neqsim):**
- Large process models with many equipment units
- Storage-constrained environments
- Archiving historical model versions
- Network transfer of state files

**When to use plain JSON (.json):**
- Version control (Git) - human-readable diffs
- Debugging and manual inspection
- Small to medium-sized models

#### JSON State Export/Import

```java
// Export to JSON string
String json = state.toJson();

// Import from JSON string
ProcessSystemState restored = ProcessSystemState.fromJson(json);
```

#### ProcessSystem Methods for State Management

```java
// Export current state to JSON file
process.exportStateToFile("process_state.json");

// Load and apply state from JSON file
process.loadStateFromFile("process_state.json");
```

---

## Python API (neqsim-python)

The [neqsim-python](https://github.com/equinor/neqsim-python) package provides Python wrappers for saving and loading NeqSim objects.

### Saving and Loading .neqsim Files

The `.neqsim` format stores compressed XML, making it efficient for large process models:

```python
import neqsim
from neqsim.thermo import fluid
from neqsim.process import stream, separator, runProcess, clearProcess, getProcess

# Build a process
clearProcess()
feed_fluid = fluid('srk')
feed_fluid.addComponent('methane', 0.9)
feed_fluid.addComponent('ethane', 0.1)
feed_fluid.setTemperature(30.0, 'C')
feed_fluid.setPressure(50.0, 'bara')
feed_fluid.setTotalFlowRate(10.0, 'MSm3/day')

inlet = stream('inlet', feed_fluid)
sep = separator('separator', inlet)
runProcess()

# Get the process object
process = getProcess()

# Save to compressed .neqsim file
neqsim.save_neqsim(process, "my_process.neqsim")
print("Process saved!")
```

```python
# Load the process from .neqsim file
loaded_process = neqsim.open_neqsim("my_process.neqsim")

# Run the loaded process
loaded_process.run()

print(f"Loaded process: {loaded_process.getName()}")
print(f"Number of units: {loaded_process.getUnitOperations().size()}")
```

### Saving and Loading XML Files

For debugging or when human-readable output is needed:

```python
import neqsim
from neqsim.thermo import createfluid

# Create a fluid
fluid1 = createfluid("dry gas")

# Save to uncompressed XML
neqsim.save_xml(fluid1, "my_fluid.xml")

# Load from XML
fluid2 = neqsim.open_xml("my_fluid.xml")

# Verify the data was preserved
assert fluid1.getTemperature() == fluid2.getTemperature()
```

### Using ProcessBuilder with Configuration Files

neqsim-python also supports JSON/YAML-based process configuration:

```python
from neqsim.thermo import fluid
from neqsim.process import ProcessBuilder

# Create fluid
feed = fluid('srk')
feed.addComponent('methane', 0.9)
feed.addComponent('ethane', 0.1)
feed.setTemperature(30.0, 'C')
feed.setPressure(50.0, 'bara')

# Load process from JSON configuration
process = ProcessBuilder.from_json('process_config.json', 
                                   fluids={'feed': feed}).run()

# Get results as JSON
results = process.results_json()

# Save results to file
process.save_results('results.json', format='json')
```

#### Example JSON Configuration File

```json
{
  "name": "Compression Train",
  "equipment": [
    {
      "type": "stream",
      "name": "inlet",
      "fluid": "feed",
      "flow_rate": 10.0,
      "flow_unit": "MSm3/day"
    },
    {
      "type": "separator",
      "name": "inlet_separator",
      "inlet": "inlet"
    },
    {
      "type": "compressor",
      "name": "stage1_compressor",
      "inlet": "inlet_separator",
      "outlet_pressure": 80.0
    }
  ]
}
```

---

## Compressed Files (.neqsim format)

### Internal Structure

A `.neqsim` file is a standard ZIP archive containing a single XML file:

```
my_process.neqsim (ZIP archive)
└── process.xml    (XStream-serialized XML)
```

### File Size Comparison

| Process Complexity | XML Size | .neqsim Size | Compression Ratio |
|-------------------|----------|--------------|-------------------|
| Simple (5 units)  | ~500 KB  | ~30 KB       | ~17:1             |
| Medium (20 units) | ~2 MB    | ~120 KB      | ~17:1             |
| Complex (50+ units) | ~10 MB | ~600 KB      | ~17:1             |

### Manual Inspection

You can manually inspect `.neqsim` files using any ZIP tool:

```bash
# Linux/Mac
unzip -l my_process.neqsim
unzip -p my_process.neqsim process.xml | head -100

# Windows PowerShell
Expand-Archive -Path my_process.neqsim -DestinationPath extracted
Get-Content extracted\process.xml | Select-Object -First 100
```

---

## Best Practices

### 1. Use Compressed Format for Production

Always use `.neqsim` format for production deployments to minimize storage and transfer costs:

```java
// Good - compressed
NeqSimXtream.saveNeqsim(process, "production_model.neqsim");

// Avoid for large models - uncompressed
// xstream.toXML(process, new FileWriter("production_model.xml"));
```

### 2. Version Your Models

Use `ProcessSystemState` with version metadata for proper model lifecycle management:

```java
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setVersion("2.1.0");
state.setDescription("Updated valve Cv values based on commissioning data");
state.setCreatedBy("process_engineer@company.com");
state.saveToFile("model_v2.1.0.json");
```

### 3. Run After Loading

Always run the process after loading to ensure the internal state is consistent:

```java
ProcessSystem loaded = (ProcessSystem) NeqSimXtream.openNeqsim("model.neqsim");
loaded.run();  // Important: reinitialize calculations
```

### 4. Handle Security Permissions

When using XStream directly, explicitly set permissions:

```java
XStream xstream = new XStream();
xstream.addPermission(AnyTypePermission.ANY);  // Required for deserialization
xstream.allowTypesByWildcard(new String[]{"neqsim.**"});
```

### 5. Use JSON for Version Control

JSON state files are ideal for Git-based version control:

```bash
# Track model changes in Git
git add asset_model_v1.2.3.json
git commit -m "Updated model with new heat exchanger configuration"
```

---

## Troubleshooting

### Common Issues

#### 1. FileNotFoundException: process.xml not found in zip file

The `.neqsim` file is corrupted or not a valid ZIP archive.

**Solution:** Verify the file is a valid ZIP and contains `process.xml`:
```bash
unzip -l my_process.neqsim
```

#### 2. ClassNotFoundException during deserialization

The serialized object references a class that doesn't exist in the current NeqSim version.

**Solution:** Ensure you're using the same (or compatible) NeqSim version that created the file.

#### 3. Large file sizes

Complex processes with many components can create large files.

**Solution:** 
- Use compressed `.neqsim` format
- Consider saving only the process state (JSON) instead of the full object

#### 4. ThreadLocal serialization error

XStream cannot serialize ThreadLocal fields.

**Solution:** The `NeqSimXtream` class automatically handles this by skipping ThreadLocal fields. Use `NeqSimXtream` instead of raw XStream.

### Debugging Serialization Issues

Enable detailed logging to diagnose problems:

```java
// Check what's being serialized
String xml = xstream.toXML(process);
System.out.println("XML length: " + xml.length());
System.out.println("First 1000 chars: " + xml.substring(0, Math.min(1000, xml.length())));
```

---

## API Reference

### Java Classes

| Class | Description |
|-------|-------------|
| `neqsim.util.serialization.NeqSimXtream` | Compressed XML serialization to `.neqsim` files |
| `neqsim.util.serialization.SerializationManager` | General-purpose object serialization |
| `neqsim.process.processmodel.lifecycle.ProcessSystemState` | JSON-based state snapshots |

### Python Functions

| Function | Description |
|----------|-------------|
| `neqsim.save_neqsim(obj, filename)` | Save object to compressed `.neqsim` file |
| `neqsim.open_neqsim(filename)` | Load object from compressed `.neqsim` file |
| `neqsim.save_xml(obj, filename)` | Save object to uncompressed XML file |
| `neqsim.open_xml(filename)` | Load object from uncompressed XML file |

---

## See Also

- [NeqSim Java Documentation](https://equinor.github.io/neqsim/)
- [NeqSim Python Documentation](https://github.com/equinor/neqsim-python)
- [XStream Library](https://x-stream.github.io/)
- [Process Simulation Guide](../process/README.md)
