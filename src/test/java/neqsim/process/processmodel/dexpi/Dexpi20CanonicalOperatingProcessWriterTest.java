package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for canonical operating values in assessed DEXPI Process export. */
class Dexpi20CanonicalOperatingProcessWriterTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void writesCalculatedCanonicalOperatingValuesWithDexpiUnits() throws Exception {
    ProcessSystem process = operatingProcess();
    process.run();
    Path output = temporaryDirectory.resolve("canonical-operating.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20CanonicalOperatingProcessWriter
        .writeAndAssessTopology(process, output.toFile(), "DEXPI-C009", "A", "NORMAL-001");
    String xml = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);

    assertTrue(report.isSchemaProfileAndSupportedTopologyValid(), report.getDiagnostics().toString());
    assertTrue(xml.contains("CANONICAL")
        || "CANONICAL_ENGINEERING_GRAPH_OPERATING_CASE".equals(report.getExportTopologySource()));
    assertTrue(xml.contains("MassFlowRateUnit.KilogramPerHour"));
    assertTrue(xml.contains("PressureAbsoluteUnit.Bar"));
    assertTrue(xml.contains("TemperatureUnit.DegreeCelsius"));
    assertTrue(xml.contains("<Double>1000.0</Double>"));
    assertTrue(xml.contains("<Double>40.0</Double>"));
    assertTrue(xml.contains("<Double>25.0</Double>"));
  }

  @Test
  void omitsOperatingValuesWhenProcessHasNotCompletedSuccessfully() throws Exception {
    ProcessSystem process = operatingProcess();
    Path output = temporaryDirectory.resolve("not-run.dexpi.xml");

    Dexpi20ProcessTopologyAssessment.Report report = Dexpi20CanonicalOperatingProcessWriter
        .writeAndAssessTopology(process, output.toFile(), "DEXPI-C009-NOT-RUN", "A", "NORMAL-001");
    String xml = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);

    assertTrue(hasDiagnostic(report, "DIAGRAM_OPERATING_CASE_NOT_SUCCESSFUL"));
    assertFalse(xml.contains("property=\"MassFlow\""));
    assertFalse(xml.contains("property=\"Pressure\""));
    assertFalse(xml.contains("property=\"Temperature\""));
  }

  private static boolean hasDiagnostic(Dexpi20ProcessTopologyAssessment.Report report, String code) {
    for (Dexpi20ProcessTopologyAssessment.Diagnostic diagnostic : report.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static ProcessSystem operatingProcess() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("operating feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Heater heater = new Heater("operating heater", feed);
    ProcessSystem process = new ProcessSystem("operating area");
    process.add(feed);
    process.add(heater);
    return process;
  }
}
