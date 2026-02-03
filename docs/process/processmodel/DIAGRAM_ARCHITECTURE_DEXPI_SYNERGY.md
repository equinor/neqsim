---
title: PFD Diagram System: Architecture Alignment and DEXPI Synergy
description: The PFD diagram system integrates cleanly at three architectural levels:
---

# PFD Diagram System: Architecture Alignment and DEXPI Synergy

## 1. Architectural Fit Assessment

### 1.1 Current NeqSim Process Model Architecture

```
neqsim.process
├── equipment/           # Individual unit operations (150+ classes)
│   ├── EquipmentEnum    # Canonical equipment type enumeration
│   └── ProcessEquipmentInterface
├── processmodel/
│   ├── ProcessSystem    # Main flowsheet container
│   ├── graph/           # Graph representation layer
│   │   ├── ProcessGraph       # DAG with cycle detection
│   │   ├── ProcessNode        # Equipment nodes
│   │   ├── ProcessEdge        # Stream edges
│   │   └── ProcessGraphBuilder
│   ├── diagram/         # PFD visualization layer
│   │   ├── ProcessDiagramExporter
│   │   ├── PFDLayoutPolicy
│   │   ├── EquipmentRole
│   │   ├── DiagramDetailLevel
│   │   └── EquipmentVisualStyle
│   ├── dexpi/           # DEXPI integration layer
│   │   ├── DexpiXmlReader
│   │   ├── DexpiXmlWriter
│   │   ├── DexpiProcessUnit
│   │   ├── DexpiStream
│   │   ├── DexpiMetadata
│   │   └── DexpiRoundTripProfile
│   └── lifecycle/       # Phase/state management
└── thermo/              # Thermodynamic models
```

### 1.2 Diagram System Integration Points

The PFD diagram system integrates cleanly at three architectural levels:

| Layer | Integration Point | Relationship |
|-------|-------------------|--------------|
| **ProcessSystem** | `toDOT()`, `createDiagramExporter()` | Facade methods for convenience |
| **ProcessGraph** | `ProcessGraphBuilder.build()` | Diagram uses graph for topology |
| **Equipment** | `ProcessEquipmentInterface` | Visual styles keyed by class name |

**Key Design Decisions:**

1. **Graph-based rendering** - Diagram exports use `ProcessGraph`, not raw equipment lists
2. **Separation of concerns** - Layout policy separate from rendering
3. **Deterministic output** - Same process → same diagram (no random placement)
4. **Serializable** - All diagram classes implement `Serializable`

### 1.3 Alignment with NeqSim Principles

| Principle | Diagram System Compliance |
|-----------|---------------------------|
| Equipment extends `ProcessEquipmentBaseClass` | ✓ Uses interfaces only, no tight coupling |
| Mixing rules before init | ✓ Works on run() output, not during setup |
| Cloning with `system.clone()` | ✓ No shared mutable state |
| Java 8 compatibility | ✓ No var, no List.of(), streams used correctly |
| Serialization support | ✓ All classes serializable |
| Package boundaries | ✓ Located in `processmodel.diagram` |

---

## 2. DEXPI Synergy Analysis

### 2.1 Current DEXPI Capabilities

NeqSim already has comprehensive DEXPI support:

| Component | Purpose |
|-----------|---------|
| `DexpiXmlReader` | Import DEXPI P&ID XML → ProcessSystem |
| `DexpiXmlWriter` | Export ProcessSystem → DEXPI XML |
| `DexpiProcessUnit` | Lightweight placeholder for imported equipment |
| `DexpiStream` | Runnable stream with DEXPI metadata |
| `DexpiMetadata` | Shared constants (tag names, line numbers, etc.) |
| `DexpiRoundTripProfile` | Validation for round-trip fidelity |
| `dexpi_equipment_mapping.properties` | DEXPI class → EquipmentEnum mapping |

### 2.2 Synergy Opportunities

#### 2.2.1 Shared Equipment Type Registry

**Current State:**
- `EquipmentVisualStyle` uses class names: `"Separator"`, `"Compressor"`, etc.
- `dexpi_equipment_mapping.properties` maps DEXPI classes → `EquipmentEnum`
- Two separate mapping systems with potential inconsistency

**Opportunity:** Unify equipment type handling through `EquipmentEnum`:

```java
// EquipmentVisualStyle could use EquipmentEnum as key
public static EquipmentVisualStyle getStyle(EquipmentEnum type) {
    return STYLE_CACHE.get(type);
}

// DexpiProcessUnit already has getMappedEquipment() → EquipmentEnum
DexpiProcessUnit unit = ...;
EquipmentVisualStyle style = EquipmentVisualStyle.getStyle(unit.getMappedEquipment());
```

#### 2.2.2 DEXPI-Aware Diagram Export

**Current State:**
- `ProcessDiagramExporter` generates Graphviz DOT
- `DexpiXmlWriter` generates DEXPI XML
- No direct connection between them

**Opportunity:** Add DEXPI-aware export options:

```java
public class ProcessDiagramExporter {
    // Export to DEXPI XML with embedded layout hints
    public void exportDexpiWithLayout(Path path) throws IOException {
        // 1. Generate ProcessGraph with layout
        // 2. Add layout coordinates as GenericAttributes
        // 3. Write via DexpiXmlWriter with coordinates
    }
    
    // Import DEXPI and preserve P&ID layout
    public static ProcessDiagramExporter fromDexpi(Path dexpiXml) {
        ProcessSystem system = DexpiXmlReader.read(dexpiXml, template);
        return new ProcessDiagramExporter(system)
            .preserveDexpiLayout(true);  // Use DEXPI positions if available
    }
}
```

#### 2.2.3 Metadata Preservation

**Current State:**
- `DexpiMetadata` defines: TAG_NAME, LINE_NUMBER, FLUID_CODE, etc.
- `ProcessDiagramExporter` doesn't use these

**Opportunity:** Enrich diagram labels with DEXPI metadata:

```java
private String buildNodeLabel(ProcessEquipmentInterface equipment) {
    StringBuilder label = new StringBuilder(equipment.getName());
    
    if (equipment instanceof DexpiProcessUnit) {
        DexpiProcessUnit dexpi = (DexpiProcessUnit) equipment;
        if (dexpi.getLineNumber() != null) {
            label.append("\\nLine: ").append(dexpi.getLineNumber());
        }
        if (dexpi.getFluidCode() != null) {
            label.append("\\nFluid: ").append(dexpi.getFluidCode());
        }
    }
    return label.toString();
}
```

#### 2.2.4 Symbol Standardization (ISO 10628 / DEXPI)

**Current State:**
- `EquipmentVisualStyle` uses Graphviz shapes (circle, rectangle, etc.)
- DEXPI references ISO 10628-2 (P&ID symbols)

**Opportunity:** Map to ISO 10628 symbol classes:

```java
public enum EquipmentSymbol {
    // ISO 10628-2 Section 5: Process equipment
    VESSEL(5.1, "cylinder", "#90EE90"),
    COLUMN(5.2, "cylinder", "#90EE90"),
    HEAT_EXCHANGER(5.3, "rectangle", "#FFD700"),
    
    // ISO 10628-2 Section 6: Piping components
    VALVE(6.1, "diamond", "#FFB6C1"),
    PUMP(6.2, "circle", "#4169E1"),
    COMPRESSOR(6.3, "parallelogram", "#87CEEB");
    
    private final String isoSection;
    private final String graphvizShape;
    private final String defaultColor;
}
```

### 2.3 Implementation Roadmap

#### Phase 1: Type Registry Unification (Low effort, high impact)

1. Refactor `EquipmentVisualStyle` to accept `EquipmentEnum` as primary key
2. Add fallback to class name for custom equipment
3. Ensure DEXPI imports render with correct visual styles

```java
// Before
EquipmentVisualStyle style = EquipmentVisualStyle.getStyle("Separator");

// After
EquipmentVisualStyle style = EquipmentVisualStyle.getStyle(EquipmentEnum.Separator);
// or
EquipmentVisualStyle style = EquipmentVisualStyle.getStyle(unit.getMappedEquipment());
```

#### Phase 2: DEXPI Metadata in Diagrams (Medium effort)

1. Detect `DexpiProcessUnit` and `DexpiStream` instances
2. Extract and display DEXPI metadata (tag names, line numbers)
3. Add legend entry for DEXPI-originated equipment

#### Phase 3: Bidirectional DEXPI-Diagram Integration (Higher effort)

1. Add layout coordinates to DEXPI export via GenericAttributes
2. Parse layout hints from DEXPI imports
3. Round-trip: DEXPI → NeqSim → PFD → DEXPI with layout preserved

---

## 3. Architectural Recommendations

### 3.1 Keep Current Structure

The `diagram/` package is correctly positioned:
- Under `processmodel/` (not `equipment/`)
- Parallel to `graph/` (uses graph, doesn't extend it)
- Separate from DEXPI (clean boundaries)

### 3.2 Create Shared Equipment Type Service

```java
package neqsim.process.equipment;

/**
 * Unified equipment type resolution service.
 */
public final class EquipmentTypeResolver {
    
    /**
     * Resolves equipment to canonical EquipmentEnum.
     */
    public static EquipmentEnum resolve(ProcessEquipmentInterface equipment) {
        if (equipment instanceof DexpiProcessUnit) {
            return ((DexpiProcessUnit) equipment).getMappedEquipment();
        }
        // Fall back to class name mapping
        String className = equipment.getClass().getSimpleName();
        return EquipmentEnum.valueOf(className);
    }
    
    /**
     * Resolves DEXPI class name to EquipmentEnum using mapping file.
     */
    public static EquipmentEnum resolveFromDexpi(String dexpiClassName) {
        // Load from dexpi_equipment_mapping.properties
    }
}
```

### 3.3 Add DEXPI-Diagram Bridge Class

```java
package neqsim.process.processmodel.diagram;

/**
 * Bridge between DEXPI metadata and PFD visualization.
 */
public class DexpiDiagramBridge {
    
    /**
     * Creates diagram exporter optimized for DEXPI-imported processes.
     */
    public static ProcessDiagramExporter createExporter(ProcessSystem system) {
        return new ProcessDiagramExporter(system)
            .setShowDexpiMetadata(true)
            .setPreserveDexpiLayout(true);
    }
    
    /**
     * Exports ProcessSystem to DEXPI XML with embedded layout coordinates.
     */
    public static void exportWithLayout(ProcessSystem system, Path output) {
        // Calculate layout via ProcessDiagramExporter
        // Inject coordinates as GenericAttributes
        // Write via DexpiXmlWriter
    }
}
```

---

## 4. Summary

### Architectural Fit: ✅ Excellent

The PFD diagram system integrates cleanly:
- Uses `ProcessGraph` for topology (not parallel implementation)
- Respects package boundaries (`processmodel.diagram`)
- Follows NeqSim patterns (Serializable, Java 8 compatible)
- Provides convenience methods on `ProcessSystem` (facade pattern)

### DEXPI Synergy: ✅ Implemented

| Synergy Area | Status | Implementation |
|--------------|--------|----------------|
| EquipmentEnum unification | ✅ Complete | `EquipmentVisualStyle.getStyle(EquipmentEnum)` |
| DEXPI metadata in labels | ✅ Complete | `appendDexpiMetadata()`, `setShowDexpiMetadata()` |
| DexpiDiagramBridge | ✅ Complete | `DexpiDiagramBridge` class with round-trip support |
| ISO 10628 symbol mapping | ⏳ Future | Planned for P&ID compliance |

### Available Features

1. **`EquipmentVisualStyle.getStyle(EquipmentEnum)`** - Unified styling via canonical enum
2. **`EquipmentVisualStyle.getStyleForEquipment(equipment)`** - Auto-detects DEXPI units
3. **`ProcessDiagramExporter.setShowDexpiMetadata(true)`** - Display line numbers/fluid codes
4. **`DexpiDiagramBridge.createExporter(system)`** - Pre-configured DEXPI-aware exporter
5. **`DexpiDiagramBridge.importAndCreateExporter(path)`** - One-step DEXPI → diagram
6. **`DexpiDiagramBridge.roundTrip(input, dotOutput, dexpiOutput)`** - Full import/simulate/export
