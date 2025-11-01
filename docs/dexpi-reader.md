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

## Tested example

A regression test (`DexpiXmlReaderTest`) imports the
[`C01V04-VER.EX01.xml`](https://gitlab.com/dexpi/TrainingTestCases/-/blob/master/dexpi%201.3/example%20pids/C01%20DEXPI%20Reference%20P&ID/C01V04-VER.EX01.xml)
training case provided by the
[DEXPI Training Test Cases repository](https://gitlab.com/dexpi/TrainingTestCases/-/tree/master/dexpi%201.3/example%20pids) and
verifies that the expected equipment (two heat exchangers, two pumps, a tank, valves and piping
segments) are discovered. The regression additionally seeds the import with an example NeqSim feed
stream and confirms that the generated streams remain active after `process.run()`.
