---
title: DEXPI XML reader
description: "The DexpiXmlReader utility converts DEXPI XML P&ID exports into NeqSim ProcessSystem models."
---

# DEXPI XML reader

The `DexpiXmlReader` utility converts [DEXPI](https://dexpi.org/) XML P&ID exports into
[`ProcessSystem`](../src/main/java/neqsim/process/processmodel/ProcessSystem.java) models.
It recognises major equipment such as pumps, heat exchangers, tanks and control valves as well as
complex reactors, compressors and inline analysers. Piping segments are imported as runnable
`DexpiStream` units tagged with the source line number.

## Usage

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

The reader also exposes `load` methods if you want to populate an existing process model instance.
Each imported equipment item is represented as a lightweight `DexpiProcessUnit` that records the
original DEXPI class together with the mapped `EquipmentEnum` category and contextual information
like line numbers or fluid codes. Piping segments become `DexpiStream` objects that clone the
pressure, temperature and flow settings from the template stream (or a built-in methane/ethane
fallback). When available, the reader honours the recommended metadata exported by NeqSim so
pressure, temperature and flow values embedded in DEXPI documents override the template defaults.
The resulting `ProcessSystem` can therefore perform full thermodynamic calculations when `run()` is
invoked without requiring downstream tooling to remap metadata.

### Metadata conventions

Both the reader and writer share the [`DexpiMetadata`](../../src/main/java/neqsim/process/processmodel/dexpi/DexpiMetadata.java)
constants that describe the recommended generic attributes for DEXPI exchanges. Equipment exports
include tag names, line numbers and fluid codes, while piping segments also carry segment numbers
and operating pressure/temperature/flow triples (together with their units). Downstream tools can
consult `DexpiMetadata.recommendedStreamAttributes()` and
`DexpiMetadata.recommendedEquipmentAttributes()` to understand the minimal metadata sets guaranteed
by NeqSim.

### Exporting back to DEXPI

The companion `DexpiXmlWriter` can serialise a process system created from DEXPI data back into a
lightweight DEXPI XML document. This is useful when you want to post-process the imported model with
tooling such as [pyDEXPI](https://github.com/process-intelligence-research/pyDEXPI) to produce
graphical output.

```java
ProcessSystem process = DexpiXmlReader.read(xmlFile.toFile(), template);
Path exportPath = Paths.get("target", "dexpi-export.xml");
DexpiXmlWriter.write(process, exportPath.toFile());
```

The writer groups all discovered `DexpiStream` segments by line number (or fluid code when a line is
not available) to generate simple `<PipingNetworkSystem>` elements with associated
`<PipingNetworkSegment>` children. Equipment and valves are exported as `<Equipment>` and
`<PipingComponent>` elements that preserve the original tag names, line numbers and fluid codes via
`GenericAttribute` entries. Stream metadata is enriched with operating pressure, temperature and flow
values (stored in the default NeqSim units, but accompanied by explicit `Unit` annotations) so that
downstream thermodynamic simulators can reproduce NeqSim's state without bespoke mappings.

#### Native equipment and reverse mapping

The writer also handles **native NeqSim equipment** (i.e. equipment that was not imported from
DEXPI) by reverse-mapping Java classes to DEXPI `ComponentClass` strings:

| NeqSim class | DEXPI ComponentClass |
|---|---|
| `ThreePhaseSeparator` | `ThreePhaseSeparator` |
| `Separator` | `Separator` |
| `Compressor` | `CentrifugalCompressor` |
| `Pump` | `CentrifugalPump` |
| `Cooler` | `AirCooledHeatExchanger` |
| `HeatExchanger` | `ShellAndTubeHeatExchanger` |
| `Heater` | `FiredHeater` |
| `ThrottlingValve` | `GlobeValve` |
| `Expander` | `Expander` |
| `Mixer` | `Mixer` |
| `Splitter` | `Splitter` |

This means you can build a process model entirely in NeqSim and export it to DEXPI XML without
having to import from DEXPI first.

#### Nozzles and connections

Every exported equipment piece receives `<Nozzle>` child elements (one inlet, one outlet). The
writer then builds `<Connection>` elements linking outlet nozzles to inlet nozzles based on the
process wiring in the `ProcessSystem`. This produces a valid DEXPI topology graph that downstream
tools can traverse.

#### Sizing attribute export

When equipment originates from a `DexpiProcessUnit`, the writer preserves all sizing attributes
(e.g. `InsideDiameter`, `TangentToTangentLength`, `DesignPressure`, `NumberOfTrays`) as
`GenericAttribute` entries in the exported XML.

#### Simulation results

After `process.run()`, the writer exports simulation results (operating pressure, temperature and
flow rate) as `GenericAttribute` entries on each native equipment element. This provides a complete
snapshot of the converged simulation state in the DEXPI output.

Each piping network is also labelled with a `NeqSimGroupingKey` generic attribute so that
visualisation libraries—such as [pyDEXPI](https://github.com/process-intelligence-research/pyDEXPI)
or Graphviz exports—can easily recreate line-centric layouts without additional heuristics.

### Generating PFD diagrams from DEXPI

The `DexpiDiagramBridge` class provides seamless integration between DEXPI imports and NeqSim's
PFD diagram generation system. This allows you to import P&ID data and immediately produce
professional process flow diagrams.

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

// Create optimized exporter for existing DEXPI-imported process
ProcessDiagramExporter exporter = DexpiDiagramBridge.createExporter(system);
exporter.setShowDexpiMetadata(true);  // Show line numbers and fluid codes in labels
```

The bridge automatically configures the diagram exporter to display DEXPI metadata (tag names, line
numbers, fluid codes) alongside equipment labels, making it easy to cross-reference the generated
PFD with the original P&ID source.

### Round-trip profile

To codify the minimal metadata required for reliable imports/exports NeqSim exposes the
[`DexpiRoundTripProfile`](../../src/main/java/neqsim/process/processmodel/dexpi/DexpiRoundTripProfile.java)
utility. The `minimalRunnableProfile` validates that a process contains runnable `DexpiStream`
segments (with line/fluid references and operating conditions), tagged equipment and at least one
piece of equipment alongside the piping network. Regression tests enforce this profile on the
reference training case and the re-imported export artefacts to guarantee round-trip fidelity.

### Security considerations

Both the reader and writer configure their XML factories with hardened defaults: secure-processing
is enabled, external entity resolution is disabled and `ACCESS_EXTERNAL_DTD` /
`ACCESS_EXTERNAL_SCHEMA` properties are cleared. These guardrails mirror the guidance in the
regression tests and should be preserved if the parsing/serialisation logic is extended.

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
   reducers) into equipment-level edges, and produces a topological ordering via Kahn's algorithm.   The resolver also detects cycles and logs warnings when cyclic dependencies are found;
   `ResolvedTopology.hasCycle()` can be queried programmatically.2. **Equipment mapping** — `DexpiMappingLoader` reads `dexpi_equipment_mapping.properties` and
   `dexpi_piping_component_mapping.properties` from the classpath to translate DEXPI ComponentClass
   strings (e.g. `CentrifugalCompressor`) into `EquipmentEnum` values.
3. **Equipment instantiation** — `DexpiEquipmentFactory` creates real NeqSim equipment from the
   mapped enum, applying sizing attributes such as `InsideDiameter`, `TangentToTangentLength`,
   `DesignPressure`, `ValveCv` and `Orientation`. Distillation columns are instantiated with
   `NumberOfTrays` and `FeedTray` attributes when present.
4. **Stream wiring** — The builder walks the topology in upstream-to-downstream order, connecting
   outlet streams of upstream equipment to inlets of downstream equipment.
5. **Auto-instrumentation** — When enabled, `DynamicProcessHelper.instrumentAndControl()` adds
   transmitters and PID controllers to separators, compressors and heat exchangers. DEXPI
   instrument tags are associated with auto-generated transmitters and controllers for traceability.

### Component classes

| Class | Purpose |
|-------|--------|
| `DexpiSimulationBuilder` | High-level builder: DEXPI XML → runnable `ProcessSystem` |
| `DexpiTopologyResolver` | Parses nozzle/connection graph, topological sort, edge collapsing |
| `DexpiEquipmentFactory` | Converts DEXPI placeholders to real equipment with sizing |
| `DexpiMappingLoader` | Thread-safe cached loader for `.properties` mapping files |
| `DexpiXmlWriterTest` | Tests for round-trip, reverse mapping, connections, nozzles, results export |

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

## Tested example

A regression test (`DexpiXmlReaderTest`) imports the
[`C01V04-VER.EX01.xml`](https://gitlab.com/dexpi/TrainingTestCases/-/blob/master/dexpi%201.3/example%20pids/C01%20DEXPI%20Reference%20P&ID/C01V04-VER.EX01.xml)
training case provided by the
[DEXPI Training Test Cases repository](https://gitlab.com/dexpi/TrainingTestCases/-/tree/master/dexpi%201.3/example%20pids) and
verifies that the expected equipment (two heat exchangers, two pumps, a tank, valves and piping
segments) are discovered. The regression additionally seeds the import with an example NeqSim feed
stream and confirms that the generated streams remain active after `process.run()`. Companion
assertions enforce the `DexpiRoundTripProfile` and check that exported metadata (pressure,
temperature, flow and units) survives a round-trip reload. A companion test exports the imported
process with `DexpiXmlWriter`, then parses the generated XML with a hardened DOM builder to confirm
that the document contains equipment, piping components and `PipingNetworkSystem`/
`PipingNetworkSegment` structures ready for downstream DEXPI tooling such as pyDEXPI.
