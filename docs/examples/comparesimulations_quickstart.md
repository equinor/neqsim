# Comparing process simulations with NeqSim

This guide shows how to set up a NeqSim process model and extract results similar to the
`comparesimulations.ipynb` Colab notebook
(https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/process/comparesimulations.ipynb).
The examples below highlight both the Python and Java APIs so you can replicate the
flowsheet and collect comparable reports.

## Prerequisites

- Java 8+ with Maven if you want to run the Java example locally (`./mvnw test` also
  builds the codebase).
- Python 3.9+ with the `neqsim` package installed (in Colab you can run
  `pip install neqsim==3.*`).
- A copy of the fluid characterisation used in your study (the Colab notebook links to
  the composition from the DWSIM/HYSYS comparison paper).

## Python (Colab) outline

1. **Install and import NeqSim** – in a fresh notebook cell run:
   ```python
   !pip install neqsim==3.*
   from neqsim import process
   from neqsim.thermo import fluid
   ```
2. **Build the fluid** – recreate the feed described in your reference by defining
   components and conditions, for example:
   ```python
   feed = fluid('srk')
   feed.addComponent('methane', 0.8)
   feed.addComponent('ethane', 0.1)
   feed.addComponent('propane', 0.05)
   feed.setTemperature(310.0, 'K')
   feed.setPressure(50.0, 'bara')
   feed.initProperties()
   ```
3. **Add equipment and streams** – construct the flowsheet units you want to compare,
   such as separators, compressors and coolers:
   ```python
   well_stream = process.Stream('well stream', feed)
   sep1 = process.Separator3Phase('1st stage separator', well_stream)
   booster = process.Compressor('gas booster', sep1.getGasOutStream())
   booster.setOutletPressure(100.0, 'bara')
   ```
4. **Bundle into a process model** – the Colab notebook uses Pydantic to expose a clean
   API; in pure NeqSim you can collect units in a `ProcessSystem` and run them together:
   ```python
   flowsheet = process.ProcessSystem()
   flowsheet.add(well_stream)
   flowsheet.add(sep1)
   flowsheet.add(booster)
   flowsheet.run()
   ```
5. **Read the results** – after the run, access stream properties or export a JSON
   report to line up with HYSYS/DWSIM outputs:
   ```python
   gas_out = sep1.getGasOutStream()
   print(gas_out.getFlowRate('MSm3/day'))
   print(gas_out.getTemperature('C'))
   print(flowsheet.createJSON())
   ```

These steps mirror the notebook: define the feed, assemble the equipment, run the
flowsheet, then compare key variables (flow, temperature, power) against other
simulators.

## Java quick start using `ProcessModel`

The Java API contains the same building blocks as the Colab example. The snippet below
combines an inlet separator and a compressor into a single `ProcessModel`, then produces
an aligned JSON report for comparison.

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.util.report.Report;
import neqsim.thermo.system.SystemInterface;

// 1) Create the feed and initial process section
SystemInterface wellFluid = ... // load your composition
Stream wellStreamHP = new Stream("HP well stream", wellFluid);
wellStreamHP.setFlowRate(10.0, "MSm3/day");
ThreePhaseSeparator firstStageSeparator =
    new ThreePhaseSeparator("1st stage separator", wellStreamHP);

// 2) Create a downstream compressor section
Stream compressorFeed = firstStageSeparator.getGasOutStream();
Compressor compressor1 = new Compressor("Compressor1", compressorFeed);
compressor1.setPolytropicEfficiency(0.56);
compressor1.setUsePolytropicCalc(true);
compressor1.setOutletPressure(100.0, "bara");

// 3) Combine the sections in a ProcessModel and run
ProcessModel combinedProcess = new ProcessModel();
ProcessSystem inletSection = new ProcessSystem();
inletSection.add(wellStreamHP);
inletSection.add(firstStageSeparator);

ProcessSystem compressorSection = new ProcessSystem();
compressorSection.add(compressorFeed);
compressorSection.add(compressor1);

combinedProcess.add("feed process", inletSection);
combinedProcess.add("compressor process", compressorSection);
combinedProcess.setRunStep(true); // run each section in sequence
combinedProcess.run();

// 4) Generate the same JSON report format used in the notebook
Report reporter = new Report(combinedProcess);
String jsonReport = reporter.generateJsonReport();
System.out.println(jsonReport);
```

Key points when translating notebook logic to Java:

- **Reuse the same feed definition** so pressures, temperatures and compositions match
  the comparison datasets.
- **Set `setRunStep(true)`** when you want the `ProcessModel` to execute each embedded
  `ProcessSystem` in the order you added them (mirroring the sequential cells in the
  Colab example).【F:src/test/java/neqsim/process/processmodel/CombinedModelsTest.java†L67-L96】【F:src/test/java/neqsim/process/processmodel/CombinedModelsTest.java†L111-L124】
- **Use `Report` to align outputs** – `Report.generateJsonReport()` produces the same
  structure stored on the `ProcessModel` via `getReport_json()`, which you can compare
  against HYSYS or DWSIM values just like the notebook does.【F:src/test/java/neqsim/process/processmodel/CombinedModelsTest.java†L130-L136】

With these steps you can reproduce the Colab study locally, tweak equipment settings,
then collate results for side-by-side validation against other process simulators.
