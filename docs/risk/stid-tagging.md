# STID & Functional Location Tagging

STID (Standard Tag Identification) provides a standardized way to identify equipment across offshore installations, following ISO 14224 conventions used on the Norwegian Continental Shelf.

---

## STID Format

### Tag Structure

```
PPPP-TT-NNNNN[S]
│    │  │     └─ Train suffix (optional): A, B, C...
│    │  └─────── Sequential number: 5 digits
│    └────────── Equipment type code: 2 characters
└─────────────── Installation code: 4 digits
```

### Examples

| Tag | Installation | Type | Number | Train |
|-----|--------------|------|--------|-------|
| `1775-KA-23011A` | Gullfaks C | Compressor | 23011 | A |
| `1775-KA-23011B` | Gullfaks C | Compressor | 23011 | B |
| `2540-VG-30001` | Åsgard A | Separator | 30001 | - |
| `1910-PA-12005` | Troll A | Pump | 12005 | - |

---

## Installation Codes

### Norwegian Continental Shelf

| Code | Installation | Field | Operator |
|------|--------------|-------|----------|
| `1770` | Gullfaks A | Gullfaks | Equinor |
| `1773` | Gullfaks B | Gullfaks | Equinor |
| `1775` | Gullfaks C | Gullfaks | Equinor |
| `2540` | Åsgard A | Åsgard | Equinor |
| `2541` | Åsgard B | Åsgard | Equinor |
| `2542` | Åsgard C (FPSO) | Åsgard | Equinor |
| `1910` | Troll A | Troll | Equinor |
| `1820` | Oseberg A | Oseberg | Equinor |
| `6608` | Snorre A | Snorre | Equinor |

### Using Installation Codes

```java
// Predefined constants
String code = FunctionalLocation.GULLFAKS_C;  // "1775"
String code = FunctionalLocation.ASGARD_A;    // "2540"
String code = FunctionalLocation.TROLL_A;     // "1910"
```

---

## Equipment Type Codes

### ISO 14224 / NORSOK Equipment Types

| Code | Type | Description |
|------|------|-------------|
| `KA` | Compressor | Centrifugal, reciprocating |
| `PA` | Pump | All pump types |
| `VA` | Valve | Control, safety, manual valves |
| `VG` | Separator | 2-phase, 3-phase separators |
| `WA` | Heat Exchanger | Shell-tube, plate, etc. |
| `WC` | Cooler | Air coolers, water coolers |
| `WH` | Heater | Direct fired, electric |
| `GA` | Turbine | Gas turbines |
| `MA` | Motor | Electric motors |
| `TK` | Tank | Storage tanks |
| `PL` | Pipeline | Pipelines, risers |
| `FI` | Filter | All filter types |

### Using Type Codes

```java
// Predefined constants
String type = FunctionalLocation.TYPE_COMPRESSOR;     // "KA"
String type = FunctionalLocation.TYPE_PUMP;           // "PA"
String type = FunctionalLocation.TYPE_SEPARATOR;      // "VG"
String type = FunctionalLocation.TYPE_HEAT_EXCHANGER; // "WA"
```

---

## Sequential Number Convention

The 5-digit sequential number often encodes system/subsystem information:

```
NNNNN
├─ NN─── System number (first 2 digits)
└──── NNN─ Equipment sequence (last 3 digits)
```

### System Number Examples

| System | Description |
|--------|-------------|
| 20 | Wellhead systems |
| 21 | Manifold systems |
| 23 | First stage separation |
| 24 | Second stage separation |
| 26 | Gas compression |
| 27 | Gas treatment |
| 29 | Export systems |
| 30 | Oil processing |
| 32 | Water treatment |

### Example Breakdown

```
1775-KA-23011A
│    │  │││││
│    │  ││└┴┴── Equipment 011
│    │  └┴───── System 23 (1st stage separation)
│    └────────── Compressor
└─────────────── Gullfaks C
```

---

## Using FunctionalLocation

### Creating from Tag String

```java
// Parse from full STID tag
FunctionalLocation loc = new FunctionalLocation("1775-KA-23011A");

// Access components
String installation = loc.getInstallationCode();     // "1775"
String installName = loc.getInstallationName();      // "Gullfaks C"
String type = loc.getEquipmentTypeCode();            // "KA"
String typeDesc = loc.getEquipmentTypeDescription(); // "Compressor"
String seqNum = loc.getSequentialNumber();           // "23011"
String train = loc.getTrainSuffix();                 // "A"
String fullTag = loc.getFullTag();                   // "1775-KA-23011A"
```

### Creating with Builder

```java
FunctionalLocation loc = FunctionalLocation.builder()
    .installation(FunctionalLocation.GULLFAKS_C)
    .type(FunctionalLocation.TYPE_COMPRESSOR)
    .sequentialNumber("23011")
    .trainSuffix("A")
    .description("HP Export Compressor Train A")
    .system("Export Compression")
    .build();
```

### Creating with Components

```java
FunctionalLocation loc = new FunctionalLocation(
    "1775",   // Installation
    "KA",     // Type
    "23011",  // Sequential number
    "A"       // Train suffix (optional)
);
```

---

## Parallel Equipment Detection

### Train Suffix Convention

Parallel equipment shares the same base tag with different suffixes:

| Equipment | Tag | Base Tag |
|-----------|-----|----------|
| Compressor A | 1775-KA-23011A | 1775-KA-23011 |
| Compressor B | 1775-KA-23011B | 1775-KA-23011 |
| Compressor C | 1775-KA-23011C | 1775-KA-23011 |

### Checking Parallel Relationship

```java
FunctionalLocation compA = new FunctionalLocation("1775-KA-23011A");
FunctionalLocation compB = new FunctionalLocation("1775-KA-23011B");
FunctionalLocation pump = new FunctionalLocation("1775-PA-24001");

// Check if parallel
compA.isParallelTo(compB);  // true - same base, different suffix
compA.isParallelTo(pump);   // false - different type and number

// Get base tag for grouping
String baseA = compA.getBaseTag();  // "1775-KA-23011"
String baseB = compB.getBaseTag();  // "1775-KA-23011"
// Same base tag = parallel trains
```

### Finding Parallel Equipment

```java
// In topology analyzer
topology.setFunctionalLocation("Comp A", "1775-KA-23011A");
topology.setFunctionalLocation("Comp B", "1775-KA-23011B");

// Automatic parallel detection based on STID
List<Set<String>> parallelGroups = topology.getParallelGroupsBySTID();
```

---

## Installation and System Queries

### Same Installation Check

```java
FunctionalLocation loc1 = new FunctionalLocation("1775-KA-23011A");
FunctionalLocation loc2 = new FunctionalLocation("1775-PA-24001");
FunctionalLocation loc3 = new FunctionalLocation("2540-VG-30001");

loc1.isSameInstallation(loc2);  // true (both Gullfaks C)
loc1.isSameInstallation(loc3);  // false (Gullfaks C vs Åsgard A)
```

### Same System Check

```java
FunctionalLocation sep = new FunctionalLocation("1775-VG-23001");
FunctionalLocation comp = new FunctionalLocation("1775-KA-23011A");
FunctionalLocation pump = new FunctionalLocation("1775-PA-24001");

sep.isSameSystem(comp);   // true (both system 23)
sep.isSameSystem(pump);   // false (system 23 vs 24)
```

---

## Integration with Topology

### Tagging Equipment in Topology

```java
ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
topology.buildTopology();

// Assign STID tags
topology.setFunctionalLocation("HP Separator", "1775-VG-23001");
topology.setFunctionalLocation("Compressor A", "1775-KA-23011A");
topology.setFunctionalLocation("Compressor B", "1775-KA-23011B");
topology.setFunctionalLocation("Export Cooler", "1775-WC-29001");

// Query by STID attributes
List<String> gullfaksEquip = topology.getEquipmentByInstallation("1775");
List<String> compressors = topology.getEquipmentByType("KA");
List<String> system23 = topology.getEquipmentBySystem("23");
```

### Displaying Tagged Equipment

```java
System.out.println("Equipment with STID Tags:");
System.out.println("─".repeat(70));

for (Map.Entry<String, EquipmentNode> entry : topology.getNodes().entrySet()) {
    EquipmentNode node = entry.getValue();
    FunctionalLocation loc = node.getFunctionalLocation();
    
    if (loc != null) {
        System.out.printf("%-20s │ %-15s │ %s%n",
            loc.getFullTag(),
            entry.getKey(),
            loc.getInstallationName());
    }
}
```

Output:
```
Equipment with STID Tags:
──────────────────────────────────────────────────────────────────────
1775-VG-23001        │ HP Separator    │ Gullfaks C
1775-KA-23011A       │ Compressor A    │ Gullfaks C
1775-KA-23011B       │ Compressor B    │ Gullfaks C
1775-WC-29001        │ Export Cooler   │ Gullfaks C
```

---

## Cross-Installation Analysis

### Defining Cross-Installation Dependencies

```java
// Gullfaks C exports gas to Åsgard A
FunctionalLocation source = new FunctionalLocation("1775-KA-23011A");  // Gullfaks
FunctionalLocation target = new FunctionalLocation("2540-VG-30001");   // Åsgard

DependencyAnalyzer deps = new DependencyAnalyzer(process, topology);
deps.addCrossInstallationDependency(source, target, "gas_export", 0.6);

// Analyze cross-installation effects
System.out.printf("Dependency: %s (%s) → %s (%s)%n",
    source.getFullTag(), source.getInstallationName(),
    target.getFullTag(), target.getInstallationName());
```

### Multi-Installation Queries

```java
// Get all equipment across installations
Map<String, List<String>> byInstallation = new HashMap<>();

for (EquipmentNode node : topology.getNodes().values()) {
    FunctionalLocation loc = node.getFunctionalLocation();
    if (loc != null) {
        String inst = loc.getInstallationName();
        byInstallation.computeIfAbsent(inst, k -> new ArrayList<>())
            .add(node.getName());
    }
}

// Print by installation
for (Map.Entry<String, List<String>> entry : byInstallation.entrySet()) {
    System.out.println(entry.getKey() + ":");
    for (String eq : entry.getValue()) {
        System.out.println("  - " + eq);
    }
}
```

---

## Tag Validation

### Validation Methods

```java
// Check if tag is valid STID format
boolean isValid = FunctionalLocation.isValidSTID("1775-KA-23011A");  // true
boolean isValid = FunctionalLocation.isValidSTID("invalid-tag");     // false

// Validate tag components
FunctionalLocation loc = new FunctionalLocation("1775-KA-23011A");
boolean hasValidInstallation = loc.getInstallationName() != null;    // true
boolean hasValidType = loc.getEquipmentTypeDescription() != null;    // true
boolean isParallelUnit = loc.isParallelUnit();                       // true (has suffix)
```

### Error Handling

```java
try {
    FunctionalLocation loc = new FunctionalLocation("invalid");
    // Non-standard format stored as-is
    System.out.println("Warning: Non-standard STID format");
} catch (IllegalArgumentException e) {
    System.out.println("Invalid tag: " + e.getMessage());
}
```

---

## Best Practices

1. **Use consistent codes** - Follow ISO 14224 equipment types
2. **Document custom codes** - If using non-standard types
3. **Assign early** - Tag equipment during model creation
4. **Verify parallel detection** - Check that A/B trains match
5. **Update SAP/CMMS** - Keep tags synchronized
6. **Include in exports** - Add STID to JSON/reports

---

## See Also

- [Process Topology Analysis](topology.md)
- [Dependency Analysis](dependency-analysis.md)
- [API Reference](api-reference.md#functionallocation)
- [ISO 14224 Standard](https://www.iso.org/standard/64076.html)
