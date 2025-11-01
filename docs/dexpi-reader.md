# DEXPI XML reader

The `DexpiXmlReader` utility converts [DEXPI](https://dexpi.org/) XML P&ID exports into
[`ProcessSystem`](../src/main/java/neqsim/process/processmodel/ProcessSystem.java) models.
It recognises major equipment such as pumps, heat exchangers, tanks and control valves as well as
piping segments, which are imported as runnable `DexpiStream` units tagged with the source line
number.

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
fallback), allowing the resulting `ProcessSystem` to perform full thermodynamic calculations when
`run()` is invoked.

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
`GenericAttribute` entries. The resulting XML focuses on the metadata required to rehydrate the
process structure and is intentionally compact to ease downstream tooling consumption.

## Tested example

A regression test (`DexpiXmlReaderTest`) imports the
[`C01V04-VER.EX01.xml`](https://gitlab.com/dexpi/TrainingTestCases/-/blob/master/dexpi%201.3/example%20pids/C01%20DEXPI%20Reference%20P&ID/C01V04-VER.EX01.xml)
training case provided by the
[DEXPI Training Test Cases repository](https://gitlab.com/dexpi/TrainingTestCases/-/tree/master/dexpi%201.3/example%20pids) and
verifies that the expected equipment (two heat exchangers, two pumps, a tank, valves and piping
segments) are discovered. The regression additionally seeds the import with an example NeqSim feed
stream and confirms that the generated streams remain active after `process.run()`. A companion test
exports the imported process with `DexpiXmlWriter`, then parses the generated XML with a hardened DOM
builder to confirm that the document contains equipment, piping components and
`PipingNetworkSystem`/`PipingNetworkSegment` structures ready for downstream DEXPI tooling such as
pyDEXPI.
