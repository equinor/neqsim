package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class GenerateStandardsPidArtifactTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void generateReviewedArtifact() throws Exception {
    String configuredOutput = System.getProperty("pid.output");
    Path output = configuredOutput == null ? temporaryDirectory : Paths.get(configuredOutput);
    Files.createDirectories(output);
    SystemSrkEos fluid = new SystemSrkEos(303.15, 55.0);
    fluid.addComponent("methane", 0.78);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("n-heptane", 0.10);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(12000.0, "kg/hr");
    AdiabaticPipe upstream = new AdiabaticPipe("20-PL-001A", feed);
    upstream.setDiameter(0.2032);
    upstream.setLength(20.0);
    AdiabaticPipe downstream = new AdiabaticPipe("20-PL-001B", upstream.getOutletStream());
    downstream.setDiameter(0.1016);
    downstream.setLength(15.0);
    DexpiStream separatorLine = new DexpiStream("20-PG-1002", downstream.getOutletStream(), "PipingNetworkSegment",
        "1002", "PG");
    separatorLine.setNominalDiameterRepresentation("DN 100");
    separatorLine.setPipingClassCode("A1B");
    separatorLine.setInsulationType("H25");
    Separator separator = new Separator("20-VA-001", separatorLine);
    Tank tank = new Tank("20-TK-001", separator.getLiquidOutStream());
    LevelTransmitter separatorLevel = new LevelTransmitter("LT-2101", separator);
    LevelTransmitter tankLevel = new LevelTransmitter("LT-2201", tank);
    ProcessSystem process = new ProcessSystem("Vessel taps and line-data reference");
    process.add(feed);
    process.add(upstream);
    process.add(downstream);
    process.add(separatorLine);
    process.add(separator);
    process.add(tank);
    process.add(separatorLevel);
    process.add(tankLevel);
    process.run();

    Path dexpi = output.resolve("neqsim-vessel-taps-line-data.dexpi.xml");
    Path svg = output.resolve("neqsim-vessel-taps-line-data.pid.svg");
    Path assessment = output.resolve("neqsim-vessel-taps-line-data.assessment.json");
    DexpiXmlWriter.writeForPyDexpi(process, dexpi.toFile());
    DexpiXmlSvgRenderer.render(dexpi.toFile(), svg.toFile());
    DexpiVisualQualityAssessment.Report report = DexpiVisualQualityAssessment.assess(dexpi.toFile());
    Files.write(assessment, report.toJson().getBytes(StandardCharsets.UTF_8));
    assertFalse(report.hasErrors(), report.toJson());
    assertEquals(3, report.getMetrics().get("routedProcessLines"), report.toJson());
    assertEquals(1, report.getMetrics().get("linesWithSourceNominalDiameter"), report.toJson());
    assertEquals(1, report.getMetrics().get("linesWithModelInsideDiameter"), report.toJson());
    assertEquals(1, report.getMetrics().get("linesMissingSizeSourceData"), report.toJson());
    assertEquals(1, report.getMetrics().get("pipeReducers"), report.toJson());
    assertEquals(2, report.getMetrics().get("levelMeasurements"), report.toJson());
    assertEquals(2, report.getMetrics().get("vesselLevelAttachments"), report.toJson());
  }
}
