---
title: "IEC 81346 Reference Designation Support"
description: "Guide to using IEC 81346 reference designations in NeqSim for structured equipment identification in process plants. Covers letter codes, automatic generation, DEXPI export, and automation API integration."
---

# IEC 81346 Reference Designation Support

NeqSim provides built-in support for **IEC 81346** — the international standard
for structuring and identifying objects in industrial plants. This integration
enables consistent, standards-compliant equipment tagging across process
simulations, DEXPI XML exports, and the ProcessAutomation API.

## Overview

IEC 81346 defines three orthogonal aspects for identifying any object in a
plant:

| Aspect | Prefix | Describes | Example |
|--------|--------|-----------|---------|
| **Function** | `=` | What the system *does* | `=A1` (separation system) |
| **Product** | `-` | What the physical equipment *is* | `-B1` (heat exchanger 1) |
| **Location** | `+` | Where the equipment *is installed* | `+P1.M1` (platform 1, module 1) |

A full reference designation combines all three:

$$
\texttt{=A1-B1+P1.M1}
$$

This means: *"Heat exchanger 1 in the separation system, installed in platform
1, module 1."*

### IEC 81346-2 Letter Codes

IEC 81346-2 classifies equipment into categories using single-letter codes. The
following codes are relevant to process simulation:

| Code | Category | NeqSim Equipment Examples |
|------|----------|--------------------------|
| **A** | Two or more purposes | Multi-functional assemblies |
| **B** | Converting, separating, changing form | Separators, heat exchangers, coolers, heaters, reactors, distillation columns, filters |
| **C** | Storing, presenting information | Tanks, vessels, accumulators |
| **G** | Generating, providing energy | Gas turbines, steam turbines, fuel cells, wind turbines, solar panels |
| **K** | Processing, compressing, driving | Compressors, pumps, expanders, fans |
| **M** | Providing mechanical energy | Motors, engines |
| **N** | Regulating, controlling, modulating | Adjusters, recycles, controllers, calculators |
| **Q** | Controlling flow, movement | Throttling valves, control valves, check valves |
| **S** | Sensing, detecting, measuring | Pressure transmitters, temperature transmitters, flow meters, level transmitters |
| **T** | Transporting, moving | Streams, pipes, pipelines, conveyors |
| **W** | Guiding, containing | Manifolds, pipe networks, well equipment |
| **X** | Connecting, branching | Mixers, splitters, tees |

## Quick Start

### Java — Single Process System

```java
import neqsim.process.equipment.iec81346.ReferenceDesignationGenerator;
import neqsim.process.processmodel.ProcessSystem;

// Build your process as usual
ProcessSystem process = new ProcessSystem("Gas Processing");
// ... add feed, separator, compressor, etc.
process.run();

// Generate IEC 81346 designations
ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(process);
gen.setFunctionPrefix("A1");       // Function aspect
gen.setLocationPrefix("P1.M1");    // Location aspect
gen.generate();

// Each equipment now has a reference designation
String refDes = process.getUnit("HP Sep").getReferenceDesignationString();
// e.g. "=A1-B1+P1.M1"

// Export as JSON report
String json = gen.toJson();
```

### Java — Multi-Area Process Model

```java
import neqsim.process.processmodel.ProcessModel;

ProcessModel plant = new ProcessModel();
plant.add("Separation", separationSystem);
plant.add("Compression", compressionSystem);

ReferenceDesignationGenerator gen = new ReferenceDesignationGenerator(plant);
gen.setLocationPrefix("P1");
gen.generate();

// Area names become function sub-levels:
// Separation area  -> =A1 (first area)
// Compression area -> =A2 (second area)
// HP Sep in Separation -> =A1-B1+P1
// 1st Stage Compressor in Compression -> =A2-K1+P1
```

### Python (Jupyter)

```python
from neqsim import jneqsim

# Create process system...
process = jneqsim.process.processmodel.ProcessSystem("Gas Processing")
# ... add equipment and run

# Generate IEC 81346 designations
ReferenceDesignationGenerator = jneqsim.process.equipment.iec81346.ReferenceDesignationGenerator
gen = ReferenceDesignationGenerator(process)
gen.setFunctionPrefix("A1")
gen.setLocationPrefix("P1.M1")
gen.generate()

# Access designations
ref_des = process.getUnit("HP Sep").getReferenceDesignationString()
print(ref_des)  # "=A1-B1+P1.M1"

# JSON report
import json
report = json.loads(gen.toJson())
for entry in report["designations"]:
    print(f"{entry['equipmentName']:20s} {entry['referenceDesignation']:15s} "
          f"({entry['letterCode']}: {entry['letterCodeDescription']})")
```

## API Reference

### Package `neqsim.process.equipment.iec81346`

#### `IEC81346LetterCode` (Enum)

Maps IEC 81346-2 letter codes to NeqSim equipment types.

**Key methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `fromEquipmentEnum(EquipmentEnum)` | `IEC81346LetterCode` | Looks up letter code from equipment enum |
| `fromEquipment(ProcessEquipmentInterface)` | `IEC81346LetterCode` | Classifies an equipment instance |
| `getDescription()` | `String` | IEC 81346-2 description text |
| `getEquipmentMapping()` | `Map` | Full equipment-to-letter-code map |

#### `ReferenceDesignation` (Class)

Holds the three IEC 81346 aspects for one piece of equipment.

**Key methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `toReferenceDesignationString()` | `String` | Full designation, e.g. `"=A1-B1+P1"` |
| `getFormattedFunctionDesignation()` | `String` | Function with prefix, e.g. `"=A1"` |
| `getFormattedProductDesignation()` | `String` | Product with prefix, e.g. `"-B1"` |
| `getFormattedLocationDesignation()` | `String` | Location with prefix, e.g. `"+P1"` |
| `getProductCode()` | `String` | Letter code + sequence, e.g. `"B1"` |
| `isSet()` | `boolean` | True if any aspect is non-empty |

#### `ReferenceDesignationGenerator` (Class)

Walks a `ProcessSystem` or `ProcessModel` and automatically assigns IEC 81346
reference designations to all equipment.

**Configuration methods:**

| Method | Default | Description |
|--------|---------|-------------|
| `setFunctionPrefix(String)` | `"A1"` | Function aspect prefix |
| `setLocationPrefix(String)` | `""` | Location aspect prefix |
| `setIncludeStreams(boolean)` | `false` | Whether to assign designations to streams |
| `setIncludeMeasurementDevices(boolean)` | `true` | Whether to assign designations to sensors |

**After calling `generate()`:**

| Method | Returns | Description |
|--------|---------|-------------|
| `findByName(String)` | `DesignationEntry` | Lookup by equipment name |
| `findByDesignation(String)` | `DesignationEntry` | Lookup by ref designation string |
| `findByLetterCode(IEC81346LetterCode)` | `List` | All entries for a given letter code |
| `getNameToDesignationMap()` | `Map` | Name-to-designation cross-reference |
| `getDesignationToNameMap()` | `Map` | Designation-to-name cross-reference |
| `getLetterCodeSummary()` | `Map` | Count of equipment per letter code |
| `toJson()` | `String` | JSON report of all designations |

### Integration Points

#### ProcessEquipmentInterface

All process equipment supports reference designations via three methods added to
`ProcessEquipmentInterface`:

```java
// Get the current reference designation
ReferenceDesignation refDes = equipment.getReferenceDesignation();

// Set a reference designation manually
equipment.setReferenceDesignation(new ReferenceDesignation("A1", "B1", "P1",
    IEC81346LetterCode.B, 1));

// Convenience: get the formatted string directly
String str = equipment.getReferenceDesignationString();
```

#### DEXPI XML Export

When equipment has an IEC 81346 reference designation, the `DexpiXmlWriter`
automatically includes five `GenericAttribute` elements per equipment:

- `IEC81346ReferenceDesignation` — Full designation string
- `IEC81346FunctionDesignation` — Function aspect
- `IEC81346ProductDesignation` — Product aspect
- `IEC81346LocationDesignation` — Location aspect
- `IEC81346LetterCode` — Letter code classification

These attributes are only written when the reference designation is set (i.e.,
`isSet()` returns true).

#### ProcessAutomation API

The `ProcessAutomation` string-address API recognises IEC 81346 reference
designations. You can use the `=` or `-` prefix styles to address equipment:

```java
ProcessAutomation auto = process.getAutomation();

// Address by reference designation instead of name
String equipType = auto.getEquipmentType("=A1-B1+P1.M1");
// Returns "Separator"

double temp = auto.getVariableValue("=A1-B1+P1.M1.gasOutStream.temperature", "C");
```

This enables agents and automation scripts to work with standardised equipment
identifiers rather than arbitrary names.

## JSON Report Format

The `toJson()` method on `ReferenceDesignationGenerator` produces a structured
report:

```json
{
  "standard": "IEC 81346",
  "functionPrefix": "A1",
  "locationPrefix": "P1.M1",
  "designationCount": 5,
  "letterCodeSummary": {
    "B (Converting, separating, changing form)": 2,
    "K (Processing, compressing, driving)": 1,
    "Q (Controlling flow, movement)": 1,
    "S (Sensing, detecting, measuring)": 1
  },
  "designations": [
    {
      "equipmentName": "HP Separator",
      "equipmentType": "Separator",
      "referenceDesignation": "=A1-B1+P1.M1",
      "letterCode": "B",
      "letterCodeDescription": "Converting, separating, changing form",
      "sequenceNumber": 1,
      "functionArea": "A1"
    }
  ]
}
```

## Sequence Numbering

Within each process system (or area), equipment is numbered sequentially per
letter code category. For example, if a process system contains:

1. HP Separator (B category)
2. Aftercooler (B category)
3. Export Compressor (K category)
4. LP Separator (B category)

The designations would be:

| Equipment | Letter Code | Sequence | Product Code |
|-----------|-------------|----------|--------------|
| HP Separator | B | 1 | B1 |
| Aftercooler | B | 2 | B2 |
| Export Compressor | K | 1 | K1 |
| LP Separator | B | 3 | B3 |

Note that each letter code has its own sequence counter.

## Related Standards

- **IEC 81346-1:** Industrial systems — Structuring principles and reference
  designations — Part 1: Basic rules
- **IEC 81346-2:** Industrial systems — Structuring principles and reference
  designations — Part 2: Classification of objects and codes for classes
- **ISO 14617:** Graphical symbols for diagrams (used alongside IEC 81346)
- **DEXPI:** Data Exchange in the Process Industry (uses IEC 81346 as the
  equipment identification standard)
- **ISA-5.1:** Instrumentation Symbols and Identification (complementary to
  IEC 81346 for instruments)

## See Also

- [DEXPI Export](../process/dexpi-export.md) — How to export process models to
  DEXPI XML with IEC 81346 attributes
- [ProcessAutomation API](../process/automation-api.md) — String-addressable
  variable access including IEC 81346 addresses
- [Equipment Types](../process/equipment-types.md) — NeqSim equipment type
  classification
