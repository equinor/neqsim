---
title: "DEXPI P&ID Import, Export and Visualization"
description: "Complete DEXPI integration for NeqSim — import P&ID XML into runnable process models, export professional P&ID drawings with ISO 10628 symbols, auto-layout, instruments, mechanical design data, and configurable visualization."
keywords: "DEXPI, P&ID, piping and instrumentation diagram, XML import, XML export, ISO 10628, process flow diagram, PFD, visualization"
---

# DEXPI P&ID Import, Export and Visualization

NeqSim provides a complete [DEXPI](https://dexpi.org/) integration that supports:

- **Import** — parse DEXPI XML P&ID documents into runnable `ProcessSystem` models
- **Export** — serialize any NeqSim process into DEXPI XML with professional P&ID layout
- **Round-trip** — import, simulate, and re-export with simulation results embedded
- **Visualization** — auto-layout with ISO 10628:2012 shapes, instruments, signal lines, drawing borders, stream tables, and symbol legends

## Architecture overview

| Class | Purpose |
|-------|---------|
| `DexpiXmlReader` | Parses DEXPI XML into lightweight `DexpiProcessUnit` / `DexpiStream` objects |
| `DexpiXmlWriter` | Exports `ProcessSystem` to DEXPI XML with full P&ID layout |
| `DexpiLayoutEngine` | Computes auto-layout positions; renders graphical elements (shapes, lines, instruments, borders) |
| `DexpiShapeCatalog` | Generates `ShapeCatalogue` with 22 ISO 10628:2012 equipment shapes |
| `DexpiLayoutConfig` | Configurable layout parameters (spacing, fonts, colors, feature toggles) |
| `DexpiMetadata` | Shared constants for `GenericAttribute` names used by both reader and writer |
| `DexpiSimulationBuilder` | High-level builder: DEXPI XML to runnable `ProcessSystem` |
| `DexpiTopologyResolver` | Nozzle/connection graph, topological sort, cycle detection |
| `DexpiEquipmentFactory` | Converts DEXPI placeholders to real NeqSim equipment with sizing |
| `DexpiMappingLoader` | Thread-safe cached loader for equipment mapping `.properties` files |
| `DexpiStreamUtils` | Shared outlet-stream resolution for separators, splitters, two-port equipment |
| `DexpiRoundTripProfile` | Validates minimal metadata for reliable round-trip fidelity |
| `DexpiStream` | Lightweight piping segment with DEXPI class, line number, and fluid code |
| `DexpiProcessUnit` | Imported equipment with original DEXPI class and mapped `EquipmentEnum` |
| `DexpiInstrumentInfo` | Instrument metadata (tag, type, function letter) |

---

## Importing DEXPI XML

The `DexpiXmlReader` converts DEXPI XML P&ID exports into `ProcessSystem` models. It recognises
major equipment (pumps, heat exchangers, tanks, control valves, reactors, compressors, inline
analysers) and imports piping segments as runnable `DexpiStream` units tagged with the source line
number.

```java
Path xmlFile = Paths.get("/path/to/dexpi.xml");
SystemSrkEos exampleFluid = new SystemSrkEos(298.15, 50.0);
exampleFluid.addComponent("methane", 0.9);
exampleFluid.addComponent("ethane", 0.1);
exampleFluid.setMixingRule(2);
exampleFluid.init(0);

Stream template = new Stream("feed", exampleFluid);
template.setFlowRate(1.0, "MSm3/day");
template.setPressure(50.0, "bara");
template.setTemperature(30.0, "C");

ProcessSystem process = DexpiXmlReader.read(xmlFile.toFile(), template);

DexpiProcessUnit feedPump = (DexpiProcessUnit) process.getUnit("P4711");
if (feedPump.getMappedEquipment() == EquipmentEnum.Pump) {
  // handle pump metadata
}
```

Each imported equipment item is represented as a lightweight `DexpiProcessUnit` that records the
original DEXPI class, mapped `EquipmentEnum` category, and contextual information (line numbers,
fluid codes). Piping segments become `DexpiStream` objects that clone pressure, temperature, and
flow settings from the template stream.

### Metadata conventions

Both the reader and writer share `DexpiMetadata` constants that describe the recommended generic
attributes for DEXPI exchanges. Equipment exports include tag names, line numbers, and fluid codes.
Piping segments carry segment numbers and operating pressure/temperature/flow triples with explicit
unit annotations. Query `DexpiMetadata.recommendedStreamAttributes()` and
`DexpiMetadata.recommendedEquipmentAttributes()` for the minimal metadata sets guaranteed by NeqSim.

---

## Exporting to DEXPI XML

The `DexpiXmlWriter` serializes any `ProcessSystem` into a DEXPI XML document with professional
P&ID visualization. The writer produces valid DEXPI XML with proper namespace declarations.

### Basic export

```java
// Build and run a process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.run();

// Export with auto-layout
DexpiXmlWriter.write(process, new File("output.xml"));
```

### Export with layout configuration

```java
DexpiLayoutConfig config = new DexpiLayoutConfig()
    .setXSpacing(120.0)
    .setYBranchOffset(70.0)
    .setFontName("Arial")
    .setTagFontHeight(5.0)
    .setProcessLineWeight(0.6)
    .setShowStreamTable(true)
    .setShowSymbolLegend(true)
    .setShowRevisionHistory(true)
    .setShowBatteryLimit(true)
    .setShowFlowArrows(true)
    .setShowEquipmentBars(true)
    .setShowFailPositionMarkers(true)
    .setShowSilMarkers(true)
    .setShowInsulationMarks(true);

DexpiXmlWriter.write(process, new File("output.xml"), config);
```

### Round-trip (import, simulate, re-export)

```java
Stream templateStream = new Stream("feed", fluid);
templateStream.setFlowRate(1.0, "MSm3/day");
templateStream.setPressure(50.0, "bara");
templateStream.setTemperature(30.0, "C");

DexpiXmlWriter.roundTrip(
    new File("input_pid.xml"),
    new File("output_with_results.xml"),
    templateStream);
```

### XML namespace compliance

The writer produces namespace-aware DEXPI XML:

- Root `PlantModel` element includes `xmlns` (DEXPI namespace), `xmlns:xsi`, and `xsi:schemaLocation`
- Document factory is configured with `setNamespaceAware(true)`
- Equipment elements carry `ComponentClassURI` attributes mapped to RDL (Reference Data Library) URIs

### Equipment mapping (native NeqSim to DEXPI)

The writer reverse-maps Java classes to DEXPI `ComponentClass` strings:

| NeqSim class | DEXPI ComponentClass | RDL URI suffix |
|---|---|---|
| `ThreePhaseSeparator` | `ThreePhaseSeparator` | `RDS327127` |
| `Separator` | `Separator` | `RDS327127` |
| `Compressor` | `CentrifugalCompressor` | `RDS414578` |
| `Pump` | `CentrifugalPump` | `RDS414578` |
| `Cooler` | `AirCooledHeatExchanger` | `RDS414578` |
| `HeatExchanger` | `ShellAndTubeHeatExchanger` | `RDS414578` |
| `Heater` | `FiredHeater` | `RDS414578` |
| `ThrottlingValve` | `GlobeValve` | `RDS414578` |
| `Expander` | `Expander` | `RDS327127` |
| `Mixer` | `Mixer` | `RDS327127` |
| `Splitter` | `Splitter` | `RDS327127` |
| `DistillationColumn` | `DistillationColumn` | `RDS327127` |

Valve types are further distinguished by tag name prefix: `XV-` (gate valve), `BV-` (ball valve),
`NRV-` / `CV-` (check valve), `BFV-` (butterfly valve), or globe valve (default).

### Nozzles and connections

Every exported equipment element receives `<Nozzle>` children with phase-aware positioning:

| Equipment type | Outlet nozzles |
|---|---|
| `Separator` | 2 (gas top, liquid bottom) |
| `ThreePhaseSeparator` | 3 (gas top, oil middle, water bottom) |
| All other equipment | 1 |

`<Connection>` elements link outlet nozzles to inlet nozzles using **stream identity matching**.
Pass-through `Stream` wrappers are recognised by tracing the delegated fluid identity.

### Simulation results export

After `process.run()`, the writer exports converged simulation results (operating pressure,
temperature, and flow rate) as `GenericAttribute` entries on each equipment element.

### Mechanical design data export

When equipment has a `MechanicalDesign` associated, the writer exports:

- **Design pressure** (bara)
- **Design temperature** (°C, converted from Kelvin)
- **Wall thickness** (mm)
- **Material grade**
- **Outer diameter** (mm)
- **Weight** (kg)

These appear as rows in the equipment data bar and as `GenericAttribute` entries in the XML.

### Valve-specific data

For `ThrottlingValve` equipment, the writer exports the valve flow coefficient (Cv) as a
`GenericAttribute` when a non-zero value is available.

### Piping network export

`DexpiStream` segments are grouped by line number (or fluid code) into
`<PipingNetworkSystem>` / `<PipingNetworkSegment>` elements. Each segment carries piping class
and line size metadata when available. Networks are labelled with a `NeqSimGroupingKey` generic
attribute for downstream visualization tools.

---

## P&ID Layout and Visualization

The `DexpiLayoutEngine` computes auto-layout positions and renders a full set of graphical
elements that produce professional-quality P&ID drawings.

### ISO 10628:2012 shape catalogue

The `DexpiShapeCatalog` generates a `<ShapeCatalogue>` with 22 standardized shapes drawn using
DEXPI graphical primitives (Circle, PolyLine, TrimmedCurve). Each shape records its ISO 10628
registration number via a `ComponentName` attribute.

| Shape | ISO Reference | Description |
|-------|--------------|-------------|
| Vertical separator | ISO 10628:2012-2091-A | Vessel body with dished heads |
| Three-phase separator | ISO 10628:2012-2091-A | Vessel with weir partition |
| Centrifugal compressor | ISO 10628:2012-2331-A | Trapezoid with circle driver |
| Centrifugal pump | ISO 10628:2012-2301-A | Circle with discharge triangle |
| Air-cooled heat exchanger | ISO 10628:2012-2141-A | Rectangular with fan symbol |
| Fired heater | ISO 10628:2012-2191-A | Rectangular enclosure with flame |
| Shell-and-tube heat exchanger | ISO 10628:2012-2131-A | Rectangular with tube passes |
| Globe valve | ISO 10628:2012-X8058-A | Double-triangle (bowtie) |
| Gate valve | ISO 10628:2012-X8062-A | Bowtie with vertical bar |
| Ball valve | ISO 10628:2012-X8038-A | Bowtie with filled circle |
| Check valve | ISO 10628:2012-X8072-A | Triangle with vertical stop |
| Butterfly valve | ISO 10628:2012-X8042-A | Bowtie with vertical line |
| Expander/turbine | ISO 10628:2012-2331-A | Reversed trapezoid |
| Mixer | — | Converging tee |
| Splitter | — | Diverging tee |
| Nozzle | — | Small stub |
| Generic equipment | — | Dashed rectangle (fallback) |
| Distillation column | ISO 10628:2012-2092-A | Tall vessel with 5 internal tray lines |
| Relief valve | ISO 10628:2012-X8088-A | Triangle with spring/bonnet line and arrowhead |
| Solenoid valve | — | Diamond outline with coil symbol |
| Utility supply | — | Circle with incoming arrow |

### Auto-layout features

The layout engine automatically arranges equipment left-to-right with liquid branches flowing
downward and produces the following visual elements:

**Drawing frame and border:**
- Auto-fit sheet size (A4 to A0) based on process extent
- ISO 5457 drawing border with margin lines
- Column/row grid markers (A-H columns, 1-6 rows) for location referencing
- Title block with drawing name, scale bar, and revision areas

**Equipment visualization:**
- Equipment data bars below each unit showing tag name, operating conditions (T, P, flow), and mechanical design data
- Automatic shape selection based on equipment type
- Equipment rotation support (rotation-aware position reference vectors)

**Process lines:**
- Process flow lines connecting equipment nozzles
- Flow direction arrows on each line segment
- Stream labels at midpoints
- Phase-aware nozzle positioning (gas exits top, liquid exits bottom, water exits lowest)

**Instrumentation (per ISA 5.1):**
- Instrument circles with function letter labels (PT, TT, FT, LT, AT)
- Proper ISA 5.1 function letter decomposition (first letter = measured variable, subsequent = function)
- Signal lines from instruments to equipment (dashed lines with signal nodes)
- PID controller parameters displayed (Kp, Ti, Td) when controllers are present
- Controller signal lines connecting instruments to final control elements
- SIL-rated instrument visualization with concentric double-border circles for SIL 2 and above
- Fail-position markers on control valves: **FC** (red), **FO** (green), **FL** (amber)
- Solenoid valve symbols with diamond shape and wiring to controllers

**Safety elements:**
- PST (Partial Stroke Test) annotation boxes near safety valves
- Relief valve shapes with ISO 10628 spring/bonnet symbol

**Piping detail:**
- Heat trace indication marks (zigzag pattern with ET/ST type labels)
- Insulation marks on process lines
- Piping class and line size attribute export

**Annotations:**
- Equipment weight annotations (dry and operating weight)
- Sample point markers (filled dot with stem and tag label)
- Gauge glass symbols (narrow rectangle with connection stubs and "LG" label)
- Utility connection point markers with utility code labels

**Additional drawing elements:**
- Stream table with equipment conditions (T, P, flow, phase, density, MW, Cp)
- Symbol legend identifying all shapes used in the drawing
- Revision history table
- Battery limit boundary (dashed rectangle)
- Multi-page drawing support with page grid computation and continuation arrows

### Layout configuration

`DexpiLayoutConfig` provides a builder-pattern API for customizing every aspect of the layout:

**Spacing and positioning:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `xSpacing` | 100.0 | Horizontal spacing between equipment (mm) |
| `yBranchOffset` | 60.0 | Vertical offset for liquid branches (mm) |
| `xStart` | 80.0 | X-coordinate of first equipment (mm) |
| `yBase` | 150.0 | Y-coordinate of main process line (mm) |
| `defaultScale` | 1.0 | Overall drawing scale factor |
| `instrumentOffsetY` | 45.0 | Vertical offset for instruments above process line (mm) |
| `instrumentXSpacing` | 15.0 | Horizontal spacing between instruments (mm) |
| `borderMargin` | 14.0 | Drawing border margin (mm) |
| `batteryLimitPadding` | 30.0 | Battery limit boundary padding (mm) |

**Typography and line styles:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `fontName` | `"Calibri"` | Font family for all text |
| `tagFontHeight` | 4.5 | Font height for tag labels (mm) |
| `processLineWeight` | 0.5 | Stroke weight for process lines |
| `signalLineWeight` | 0.2 | Stroke weight for signal/instrument lines |
| `lineColorR/G/B` | 0.5/0.5/0 | RGB color for process lines |

**Feature toggles:**

| Toggle | Default | Controls |
|--------|---------|----------|
| `showStreamTable` | `true` | Stream data table at bottom of drawing |
| `showSymbolLegend` | `true` | Symbol identification legend |
| `showRevisionHistory` | `true` | Revision history table |
| `showBatteryLimit` | `true` | Battery limit boundary |
| `showFlowArrows` | `true` | Flow direction arrows |
| `showStreamLabels` | `true` | Stream name labels on lines |
| `showEquipmentBars` | `true` | Equipment data bars |
| `showInsulationMarks` | `true` | Insulation tick marks on pipes |
| `showFailPositionMarkers` | `true` | FC/FO/FL markers on control valves |
| `showSilMarkers` | `true` | SIL-level double-border circles |
| `showOrientationMarkers` | `true` | Equipment orientation indicators |

---

## Building runnable simulations from DEXPI

The `DexpiSimulationBuilder` provides a high-level API that goes beyond basic import: it resolves
the P&ID topology (nozzle/connection graph), instantiates real NeqSim equipment (separators,
compressors, valves, heat exchangers, etc.) with sizing attributes from DEXPI GenericAttributes,
and wires them into a runnable `ProcessSystem`.

```java
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

ProcessSystem process = new DexpiSimulationBuilder(new File("plant.xml"))
    .setFluidTemplate(fluid)
    .setFeedPressure(50.0, "bara")
    .setFeedTemperature(30.0, "C")
    .setFeedFlowRate(1.0, "MSm3/day")
    .setAutoInstrument(true)
    .build();

process.run();
```

The builder performs these steps internally:

1. **Topology resolution** — `DexpiTopologyResolver` parses `<Equipment>`, `<Nozzle>` and
   `<Connection>` elements into a directed graph, collapses inline piping components (valves,
   reducers) into equipment-level edges, and produces a topological ordering via Kahn's algorithm.
   The resolver also detects cycles and logs warnings when cyclic dependencies are found;
   `ResolvedTopology.hasCycle()` can be queried programmatically.
2. **Equipment mapping** — `DexpiMappingLoader` reads `dexpi_equipment_mapping.properties` and
   `dexpi_piping_component_mapping.properties` from the classpath to translate DEXPI ComponentClass
   strings (e.g. `CentrifugalCompressor`) into `EquipmentEnum` values.
3. **Equipment instantiation** — `DexpiEquipmentFactory` creates real NeqSim equipment from the
   mapped enum, applying sizing attributes such as `InsideDiameter`, `TangentToTangentLength`,
   `DesignPressure`, `ValveCv` and `Orientation`. Distillation columns are instantiated with
   `NumberOfTrays` and `FeedTray` attributes when present. Column subtypes are detected from the
   DEXPI class name: absorbers (class containing "absorb") are created without condenser or
   reboiler, and strippers (class containing "strip") without a condenser.
4. **Stream wiring** — The builder walks the topology in upstream-to-downstream order, connecting
   outlet streams of upstream equipment to inlets of downstream equipment.
5. **Auto-instrumentation** — When enabled, `DynamicProcessHelper.instrumentAndControl()` adds
   transmitters and PID controllers to separators, compressors and heat exchangers. DEXPI
   instrument tags are matched to auto-generated transmitters by category prefix (e.g. `PT-` for
   pressure transmitters) and the auto-generated names are **renamed** to the actual DEXPI tag
   names. Controller tags are derived by replacing the function letter (e.g. `PT-100` to `PC-100`).
6. **Namespace-aware parsing** — The builder supports an optional `setNamespaceAware(true)` flag
   for DEXPI documents that use XML namespaces. When enabled, the underlying DOM parser and
   equipment factory use namespace-aware element resolution.

### Sizing attributes

The following DEXPI GenericAttributes are automatically extracted and applied to equipment:

| Attribute | Applied to | Effect |
|-----------|-----------|--------|
| `InsideDiameter` | Separators | Sets `setInternalDiameter()` |
| `TangentToTangentLength` | Separators | Sets `setSeparatorLength()` |
| `Orientation` | Separators | Sets vertical orientation flag |
| `DesignPressure` | Compressors, Valves | Sets outlet pressure |
| `DesignTemperature` | Heat exchangers | Sets outlet temperature |
| `ValveCv` | Valves | Sets flow coefficient via `setCv()` |
| `NumberOfTrays` | Distillation columns | Sets number of trays |
| `FeedTray` | Distillation columns | Sets feed tray location |

---

## Round-trip profile and validation

The `DexpiRoundTripProfile` utility validates that a process contains the minimal metadata required
for reliable imports and exports: runnable `DexpiStream` segments (with line/fluid references and
operating conditions), tagged equipment, and at least one piece of equipment alongside the piping
network. Regression tests enforce this profile on the reference training case and the re-imported
export artefacts to guarantee round-trip fidelity.

---

## Security considerations

Both the reader and writer configure their XML factories with hardened defaults:

- Secure-processing is enabled
- External entity resolution is disabled
- `ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` properties are cleared

These guardrails prevent XXE injection attacks and should be preserved if the parsing/serialisation
logic is extended.

---

## Generating PFD diagrams from DEXPI

The `DexpiDiagramBridge` class provides seamless integration between DEXPI imports and NeqSim's
PFD diagram generation system:

```java
// One-step: import DEXPI and create diagram exporter
ProcessDiagramExporter exporter = DexpiDiagramBridge.importAndCreateExporter(
    Paths.get("plant.xml"));
exporter.exportDOT(Paths.get("diagram.dot"));
exporter.exportSVG(Paths.get("diagram.svg"));  // Requires Graphviz

// Full round-trip: import, simulate, diagram, export
ProcessSystem system = DexpiDiagramBridge.roundTrip(
    Paths.get("input.xml"),     // Input DEXPI
    Paths.get("diagram.dot"),   // Output DOT diagram
    Paths.get("output.xml"));   // Re-exported DEXPI with simulation results
```

The bridge automatically configures the diagram exporter to display DEXPI metadata (tag names, line
numbers, fluid codes) alongside equipment labels.

---

## Tested examples

### Import test

`DexpiXmlReaderTest` imports the official
[C01V04-VER.EX01.xml](https://gitlab.com/dexpi/TrainingTestCases/-/blob/master/dexpi%201.3/example%20pids/C01%20DEXPI%20Reference%20P&ID/C01V04-VER.EX01.xml)
training case from the DEXPI Training Test Cases repository. It verifies that expected equipment
(heat exchangers, pumps, tanks, valves, piping segments) are discovered, streams remain active after
`process.run()`, and exported metadata (pressure, temperature, flow, units) survives a round-trip
reload.

### Export / visualization tests

`DexpiExportForViewerTest` exercises five export scenarios:

1. **Gas processing** — separator, compressor, cooler, valve with flow lines and equipment bars
2. **Two-stage compression** — multi-stage compressor train with intercooling
3. **Official DEXPI example** — reproduces the official DEXPI reference P&ID structure
4. **Instruments test** — transmitters, PID controllers, signal lines, SIL markers, fail-position markers
5. **Professional P&ID** — full-featured drawing with border, title block, stream table, symbol legend, revision history, battery limit

All five tests validate that the exported XML is well-formed and contains the expected DEXPI
elements (Equipment, PipingComponent, PipingNetworkSystem, Drawing, ShapeCatalogue, etc.).
