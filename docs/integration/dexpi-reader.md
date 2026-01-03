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

Each piping network is also labelled with a `NeqSimGroupingKey` generic attribute so that
visualisation libraries—such as [pyDEXPI](https://github.com/process-intelligence-research/pyDEXPI)
or Graphviz exports—can easily recreate line-centric layouts without additional heuristics.

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
