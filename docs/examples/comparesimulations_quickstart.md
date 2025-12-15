# Comparing process simulations with NeqSim (Java)

This guide shows how to reproduce the `comparesimulations.ipynb` Colab notebook using
only the Java API. It walks through building the feed, assembling the flowsheet, running
the model, and exporting a JSON report so you can align the results with HYSYS/DWSIM or
other tools.

## Prerequisites

- Java 8+ and Maven (the repository includes the `./mvnw` wrapper).
- Access to the feed composition you want to benchmark (the Colab notebook links to the
  composition from the DWSIM/HYSYS comparison paper).

## Step-by-step Java example

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.report.Report;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// 1) Build the feed to mirror the notebook composition
SystemInterface wellFluid = new SystemSrkEos(310.0, 50.0); // T=310 K, p=50 bara
wellFluid.addComponent("methane", 0.8);
wellFluid.addComponent("ethane", 0.1);
wellFluid.addComponent("propane", 0.05);
wellFluid.addComponent("n-butane", 0.05);
wellFluid.initProperties();

// 2) Create the inlet process section (stream + three-phase separator)
Stream wellStreamHP = new Stream("HP well stream", wellFluid);
wellStreamHP.setFlowRate(10.0, "MSm3/day");
ThreePhaseSeparator firstStageSeparator =
    new ThreePhaseSeparator("1st stage separator", wellStreamHP);

ProcessSystem inletSection = new ProcessSystem();
inletSection.add(wellStreamHP);
inletSection.add(firstStageSeparator);

// 3) Create the compressor section using the separator gas outlet
Stream compressorFeed = firstStageSeparator.getGasOutStream();
Compressor compressor1 = new Compressor("Compressor1", compressorFeed);
compressor1.setPolytropicEfficiency(0.56);
compressor1.setUsePolytropicCalc(true);
compressor1.setOutletPressure(100.0, "bara");

ProcessSystem compressorSection = new ProcessSystem();
compressorSection.add(compressorFeed);
compressorSection.add(compressor1);

// 4) Combine the sections in a ProcessModel and run them sequentially
ProcessModel combinedProcess = new ProcessModel();
combinedProcess.add("feed process", inletSection);
combinedProcess.add("compressor process", compressorSection);
combinedProcess.setRunStep(true); // ensures each section runs in the order added
combinedProcess.run();

// 5) Read results or export the JSON report used for cross-tool comparison
System.out.printf("Gas flow after separator: %.3f MSm3/day%n",
    firstStageSeparator.getGasOutStream().getFlowRate("MSm3/day"));
System.out.printf("Compressor outlet temperature: %.2f C%n",
    compressor1.getOutletStream().getTemperature("C"));

Report reporter = new Report(combinedProcess);
String jsonReport = reporter.generateJsonReport();
System.out.println(jsonReport);
```

Tips when translating the notebook to Java:

- Keep the same feed composition, temperature, and pressure as your reference notebook
  so downstream comparisons stay aligned.
- Enable sequential execution with `setRunStep(true)` to mirror the order in which the
  Colab notebook runs each process section.【F:src/test/java/neqsim/process/processmodel/CombinedModelsTest.java†L87-L135】
- Use `Report.generateJsonReport()` to create the same JSON structure stored on the
  `ProcessModel` (`getReport_json()`), which you can compare directly against the
  notebook outputs.【F:src/test/java/neqsim/process/processmodel/CombinedModelsTest.java†L135-L136】
