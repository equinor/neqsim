# Process Flow Diagram (PFD) Export

NeqSim can generate professional oil & gas style process flow diagrams (PFDs) that follow industry conventions, comparable to UniSim, Aspen, and HYSYS.

## Quick Start

```java
// Create and run your process
ProcessSystem process = new ProcessSystem("Gas Processing Plant");
// ... add equipment ...
process.run();

// Export to DOT format (text)
String dot = process.toDOT();

// Or use the diagram exporter for more control
process.createDiagramExporter()
    .setTitle("Gas Processing Plant")
    .setDetailLevel(DiagramDetailLevel.STANDARD)
    .exportAsSVG(Path.of("diagram.svg"));
```

## Features

### Gravity-Based Layout

The diagram layout follows oil & gas conventions with left-to-right flow:
- **Feed streams** enter from the **left**
- **Products** exit from the **right**
- **Gas equipment** positioned at **top** (compressors, gas coolers)
- **Separators** positioned at **center** (anchor points)
- **Liquid equipment** positioned at **bottom** (pumps, liquid heaters)

### Phase-Aware Stream Styling

Streams are automatically colored based on phase composition:
- **Gas streams**: Light blue (#87CEEB), dashed lines
- **Oil streams**: Brown (#8B4513), solid lines
- **Aqueous streams**: Dodger blue (#1E90FF), solid lines
- **Liquid streams**: Dark blue (#4169E1), solid lines
- **Mixed streams**: Gold (#FFD700), bold lines
- **Recycle streams**: Purple (#9932CC), dashed bold lines

### Separator Outlet Semantics

For two-phase separators, outlets are positioned:
- Gas outlet exits from **top** (north port)
- Liquid outlet exits from **bottom** (south port)
- Feed enters from **side** (west port)

For **three-phase separators** (gas, oil, aqueous), outlets follow gravity:
- Gas outlet exits from **top** (lightest phase)
- Oil outlet exits from **middle/side** (intermediate density)
- Aqueous/water outlet exits from **bottom** (heaviest phase)

### Comprehensive Equipment Support

The diagram system supports all NeqSim equipment types with industry-standard shapes:

#### Separators & Vessels
| Equipment | Shape | Color |
|-----------|-------|-------|
| Separator | Cylinder | Green |
| ThreePhaseSeparator | Cylinder | Green |
| Scrubber | Cylinder | Light Green |
| GasScrubber | Cylinder | Light Green |
| KnockOutDrum | Cylinder | Light Green |
| Flash | Cylinder | Light Green |

#### Columns
| Equipment | Shape | Color |
|-----------|-------|-------|
| DistillationColumn | Tall Cylinder | Green |
| Absorber | Rectangle | Light Green |
| Stripper | Rectangle | Light Green |
| WaterStripperColumn | Rectangle | Light Green |

#### Compressors & Expanders
| Equipment | Shape | Symbol |
|-----------|-------|--------|
| Compressor | Trapezoid | Standard P&ID trapezoid |
| CompressorModule | Trapezoid | Standard P&ID trapezoid |
| Expander | Inverted Trapezium | Inverted trapezoid |
| TurbineExpander | Inverted Trapezium | Inverted trapezoid |

#### Pumps
| Equipment | Shape | Symbol |
|-----------|-------|--------|
| Pump | Circle with impeller | Circle on triangle base |
| PumpModule | Circle with impeller | Circle on triangle base |
| ESP (Electrical Submersible Pump) | Circle with impeller | Circle on triangle base |

#### Heat Exchangers
| Equipment | Shape | Symbol |
|-----------|-------|--------|
| HeatExchanger | Circle | Simple circle |
| Cooler | Circle | Simple circle |
| Heater | Circle | Simple circle |
| Condenser | Circle | Simple circle |
| Reboiler | Circle | Simple circle |
| MultiStreamHeatExchanger | Circle | Simple circle |
| DirectContactHeater | Circle | Simple circle |

#### Valves
| Equipment | Shape | Symbol |
|-----------|-------|--------|
| ThrottlingValve | Bowtie (▶◀) | Two triangles tip-to-tip |
| ValveMoV | Bowtie (▶◀) | Two triangles tip-to-tip |
| ControlValve | Bowtie (▶◀) | Two triangles tip-to-tip |
| SafetyValve | Bowtie (▶◀) | Two triangles tip-to-tip |
| PressureReliefValve | Bowtie (▶◀) | Two triangles tip-to-tip |
| ESDValve | Bowtie (▶◀) | Two triangles tip-to-tip |
| HIPPSValve | Bowtie (▶◀) | Two triangles tip-to-tip |

#### Reactors
| Equipment | Shape | Color |
|-----------|-------|-------|
| Reactor | Hexagon | Orange |
| GibbsReactor | Hexagon | Orange |
| EquilibriumReactor | Hexagon | Orange |
| ElectrolyzerCell | Hexagon | Light Blue |

#### Other Equipment
| Equipment | Shape | Color |
|-----------|-------|-------|
| Mixer | Triangle | Light Gray |
| Splitter | Inverted Triangle | Light Gray |
| Stream | Ellipse | Light Green |
| Ejector | Pentagon | Light Gray |
| Flare | Diamond | Orange |
| Filter | Rectangle | Tan |
| Membrane | Rectangle | Light Blue |
| Tank | Cylinder | Gray |
| Pipeline | Rectangle | Gray |
| Well | House | Brown |
| GasGenerator/GasTurbine | Octagon | Steel Blue |

#### Control Equipment
| Equipment | Shape | Color |
|-----------|-------|-------|
| Recycle | Rectangle (dashed) | Gray |
| Adjuster | Rectangle (dashed) | Gray |
| Calculator | Rectangle (dashed) | Light Blue |
| Controller | Rectangle (dashed) | Light Yellow |
| SetPoint | Rectangle (dashed) | Light Gray |

### Diagram Styles

Four diagram styles are available to match different simulator conventions:

```java
import neqsim.process.processmodel.diagram.DiagramStyle;

ProcessDiagramExporter exporter = new ProcessDiagramExporter(process);
exporter.setDiagramStyle(DiagramStyle.HYSYS);
```

| Style | Description | Stream Color | Background |
|-------|-------------|--------------|------------|
| `NEQSIM` | Default NeqSim style with phase-based coloring | Phase-dependent | White |
| `HYSYS` | HYSYS/UniSim style | Blue (#0066CC) | Light Cyan |
| `PROII` | PRO/II style | Dark Blue (#003366) | White |
| `ASPEN_PLUS` | Aspen Plus style | Blue (#0066FF) | Light Gray |

### Equipment Symbols (PFD Standard)

Equipment symbols follow oil & gas PFD conventions:

| Equipment | Symbol | Description |
|-----------|--------|-------------|
| **Valve** | ▶◀ | Bowtie - two triangles tip-to-tip |
| **Heater/Cooler** | ○ | Simple circle |
| **Pump** | ○ on ▽ | Circle with impeller on triangle |
| **Compressor** | ⌂ | Trapezoid shape |
| **Mixer** | ▶ | Right-pointing triangle |
| **Splitter** | ◀ | Left-pointing triangle |
| **Separator** | ▭ | Vertical cylinder |

### Recycle Stream Highlighting

Anti-surge loops and recycle streams are automatically detected and highlighted:
- Distinct purple color (#9932CC)
- Dashed bold line style
- Backward constraint disabled (allows reverse flow in layout)
- Recycle indicator "(R)" in stream tables

### Stream Value Display

Two display modes for process values:

#### Simple Text Labels
```java
exporter.setShowStreamValues(true)
        .setUseStreamTables(false);
```
Shows: `Stream Name\n25.0°C, 50.0 bar\n1000 kg/hr`

#### HTML Table Labels (Professional)
```java
exporter.setShowStreamValues(true)
        .setUseStreamTables(true);
```
Generates HTML tables with:
- Stream name header
- Temperature (T)
- Pressure (P)  
- Flow rate (F)

### Control Equipment Filtering

Control equipment (Recycle, Adjuster, Calculator, etc.) can be hidden for cleaner diagrams:
```java
exporter.setShowControlEquipment(false);
```

## Detail Levels

Three detail levels are available:

### CONCEPTUAL
- Clean, simplified diagrams
- Equipment names and connections only
- No process conditions
- Ideal for teaching, AI agents, documentation

### ENGINEERING
- Full PFD with process data
- Temperature and pressure shown
- Flow rates included
- Equipment specifications
- Ideal for engineering review

### DEBUG
- Maximum detail
- Full stream compositions
- Solver convergence info
- Recycle loop indicators
- Ideal for troubleshooting

## API Reference

### ProcessSystem Methods

```java
// Quick DOT export
String dot = process.toDOT();

// DOT export with specific detail level
String dot = process.toDOT(DiagramDetailLevel.CONCEPTUAL);

// Full-featured exporter
ProcessDiagramExporter exporter = process.createDiagramExporter();

// Direct SVG/PNG export (requires Graphviz)
process.exportDiagramSVG(Path.of("diagram.svg"));
process.exportDiagramPNG(Path.of("diagram.png"));
```

### ProcessDiagramExporter Configuration

```java
ProcessDiagramExporter exporter = new ProcessDiagramExporter(process)
    .setTitle("My Process")
    .setDiagramStyle(DiagramStyle.HYSYS)  // NEQSIM, HYSYS, PROII, ASPEN_PLUS
    .setDetailLevel(DiagramDetailLevel.ENGINEERING)
    .setVerticalLayout(false)   // LR layout (left-to-right flow) - default
    .setUseClusters(true)       // Group equipment by role
    .setShowLegend(true)        // Include legend
    .setShowStreamValues(true)  // Show T/P/F on streams
    .setUseStreamTables(false)  // Use HTML tables (true) or text (false)
    .setHighlightRecycles(true) // Highlight recycle streams
    .setShowControlEquipment(true)  // Show/hide control equipment
    .setShowDexpiMetadata(true);    // Show DEXPI line numbers/fluid codes

// Export options
String dot = exporter.toDOT();
exporter.exportDOT(Path.of("diagram.dot"));
exporter.exportSVG(Path.of("diagram.svg"));  // Requires Graphviz
exporter.exportPNG(Path.of("diagram.png"));  // Requires Graphviz
exporter.exportPDF(Path.of("diagram.pdf"));  // Requires Graphviz
```


## Rendering DOT Files

The DOT format can be rendered using Graphviz:

```bash
# Install Graphviz (if needed)
# Windows: choco install graphviz
# macOS: brew install graphviz
# Linux: apt-get install graphviz

# Render to SVG
dot -Tsvg process.dot -o process.svg

# Render to PNG
dot -Tpng process.dot -o process.png

# Render to PDF
dot -Tpdf process.dot -o process.pdf
```

## Example: Gas Separation Process

```java
// Create fluid
SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("ethane", 0.15);
fluid.addComponent("propane", 0.1);
fluid.addComponent("n-butane", 0.05);
fluid.setMixingRule("classic");

// Build process
ProcessSystem process = new ProcessSystem("Gas Separation");

Stream feed = new Stream("Well Fluid", fluid);
feed.setFlowRate(5000.0, "kg/hr");
feed.setTemperature(60.0, "C");
feed.setPressure(80.0, "bara");
process.add(feed);

Separator hpSep = new Separator("HP Separator", feed);
process.add(hpSep);

Compressor comp = new Compressor("Export Compressor", hpSep.getGasOutStream());
comp.setOutletPressure(120.0, "bara");
process.add(comp);

Cooler cooler = new Cooler("Gas Cooler", comp.getOutletStream());
cooler.setOutTemperature(40.0, "C");
process.add(cooler);

Pump pump = new Pump("Oil Pump", hpSep.getLiquidOutStream());
pump.setOutletPressure(60.0, "bara");
process.add(pump);

process.run();

// Export diagram
process.createDiagramExporter()
    .setTitle("Gas Separation Process")
    .setDetailLevel(DiagramDetailLevel.ENGINEERING)
    .exportSVG(Path.of("gas_separation.svg"));
```

This generates a professional PFD with:
- Compressor and cooler at top (gas section)
- Separator in center
- Pump at bottom (liquid section)
- Correct stream colors and port positions

## Example: Three-Phase Separation

```java
// Create three-phase fluid (gas, oil, water)
SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
fluid.addComponent("methane", 0.5);
fluid.addComponent("n-heptane", 0.3);
fluid.addComponent("water", 0.2);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Build process
ProcessSystem process = new ProcessSystem("Production Separation");

Stream feed = new Stream("Well Fluid", fluid);
feed.setFlowRate(5000.0, "kg/hr");
feed.setTemperature(60.0, "C");
feed.setPressure(80.0, "bara");
process.add(feed);

// Three-phase separator
ThreePhaseSeparator separator = new ThreePhaseSeparator("Production Separator", feed);
process.add(separator);

// Gas compression
Compressor gasCompressor = new Compressor("Gas Compressor", separator.getGasOutStream());
gasCompressor.setOutletPressure(120.0, "bara");
process.add(gasCompressor);

// Oil export pump
Pump oilPump = new Pump("Oil Pump", separator.getOilOutStream());
oilPump.setOutletPressure(60.0, "bara");
process.add(oilPump);

// Produced water handling
Pump waterPump = new Pump("Water Pump", separator.getWaterOutStream());
waterPump.setOutletPressure(10.0, "bara");
process.add(waterPump);

process.run();

// Export diagram
process.createDiagramExporter()
    .setTitle("Production Separation")
    .setDetailLevel(DiagramDetailLevel.ENGINEERING)
    .exportSVG(Path.of("production_separation.svg"));
```

This generates a PFD where the three-phase separator shows:
- Gas outlet at top → compressor (gas section)
- Oil outlet at side → oil pump (liquid section)
- Water outlet at bottom → water pump (liquid section)

Each stream is color-coded by phase type for easy identification.

## Example: Compressor Anti-Surge System

```java
// Create gas fluid
SystemInterface gas = new SystemSrkEos(298.0, 50.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

// Build process with recycle
ProcessSystem process = new ProcessSystem("Compressor Station");

Stream feed = new Stream("Feed Gas", gas);
feed.setFlowRate(1000.0, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(50.0, "bara");
process.add(feed);

// Mixer for recycle
Mixer suctionMixer = new Mixer("Suction Mixer");
suctionMixer.addStream(feed);
process.add(suctionMixer);

// Main compressor
Compressor compressor = new Compressor("Main Compressor", suctionMixer.getOutletStream());
compressor.setOutletPressure(100.0, "bara");
process.add(compressor);

// Anti-surge splitter
Splitter splitter = new Splitter("Discharge Splitter", compressor.getOutletStream(), 2);
splitter.setSplitFactors(new double[] {0.9, 0.1});
process.add(splitter);

// Anti-surge recycle
Recycle recycle = new Recycle("Anti-Surge Recycle");
recycle.addStream(splitter.getSplitStream(1));
recycle.setOutletStream(suctionMixer.getOutletStream());
process.add(recycle);

process.run();

// Export with recycle highlighting
process.createDiagramExporter()
    .setTitle("Compressor Anti-Surge System")
    .setDetailLevel(DiagramDetailLevel.ENGINEERING)
    .setHighlightRecycles(true)    // Purple dashed lines for recycles
    .setShowStreamValues(true)
    .exportSVG(Path.of("anti_surge.svg"));
```

This generates a PFD with:
- Recycle stream highlighted in purple with dashed bold lines
- Recycle equipment visible (or hidden with `setShowControlEquipment(false)`)
- Legend includes recycle stream indicator

## DEXPI Integration

The diagram system integrates with DEXPI (Data Exchange in the Process Industry) for importing
P&ID data and generating PFD diagrams from industry-standard data exchange files.

### One-Step Import and Diagram

```java
// Import DEXPI XML and create pre-configured diagram exporter
ProcessDiagramExporter exporter = DexpiDiagramBridge.importAndCreateExporter(
    Paths.get("plant.xml"));

// DEXPI metadata (line numbers, fluid codes) shown in labels by default
exporter.exportDOT(Paths.get("diagram.dot"));
exporter.exportSVG(Paths.get("diagram.svg"));  // Requires Graphviz
```

### Full Round-Trip Workflow

```java
// Import DEXPI → run simulation → generate diagram → export enriched DEXPI
ProcessSystem system = DexpiDiagramBridge.roundTrip(
    Paths.get("input.xml"),     // Input DEXPI P&ID file
    Paths.get("diagram.dot"),   // Output diagram
    Paths.get("output.xml"));   // Re-exported DEXPI with simulation results
```

### DEXPI Metadata in Labels

Equipment imported from DEXPI files displays P&ID reference information:
- **Line numbers** - Cross-reference to P&ID line designations
- **Fluid codes** - Process fluid identification
- **Tag names** - Equipment tag from original P&ID

```java
// Enable/disable DEXPI metadata display
exporter.setShowDexpiMetadata(true);
```

### Creating Exporters for DEXPI-Imported Processes

```java
// Standard exporter with DEXPI features enabled
ProcessDiagramExporter exporter = DexpiDiagramBridge.createExporter(system);

// Detailed exporter with full operating conditions
ProcessDiagramExporter detailed = DexpiDiagramBridge.createDetailedExporter(system);
```

See [DEXPI XML Reader](../../integration/dexpi-reader.md) for complete DEXPI import/export documentation.

## Customization

### Custom Layout Policy

```java
PFDLayoutPolicy customPolicy = new PFDLayoutPolicy();
// Policy automatically classifies equipment by type

ProcessDiagramExporter exporter = new ProcessDiagramExporter(process, customPolicy);
```

### Equipment Visual Styles

Visual styles are defined in `EquipmentVisualStyle` with defaults for all common equipment types. The style includes:
- Graphviz shape
- Fill color
- Border color
- Font color
- Width and height

## Architecture

The diagram export system consists of:

1. **ProcessDiagramExporter** - Main entry point for diagram generation
2. **PFDLayoutPolicy** - Layout intelligence layer with gravity logic
3. **EquipmentRole** - Classification of equipment by function
4. **DiagramDetailLevel** - Control over information density
5. **EquipmentVisualStyle** - Visual styling for equipment types (unified with EquipmentEnum)
6. **DexpiDiagramBridge** - Integration bridge for DEXPI P&ID data exchange

## Design Philosophy

> Professional PFDs are not drawn — they are computed using rules.

The layout intelligence layer applies engineering conventions:
1. Gravity logic (gas up, liquid down)
2. Functional zoning (separation center, gas upper, liquid lower)
3. Equipment semantics (separator outlets positioned correctly)
4. Stable layout (same model → same diagram)

This approach produces diagrams that are:
- Deterministic (same input → same output)
- Professional appearance
- Ready for documentation or AI consumption
- Comparable to commercial simulators
