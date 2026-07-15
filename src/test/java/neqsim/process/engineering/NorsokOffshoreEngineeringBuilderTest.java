package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter.ExportResult;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the standards-based engineering and DEXPI package vertical slice. */
class NorsokOffshoreEngineeringBuilderTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void buildsGovernedRequirementsAndExportsReferencedCompressorMap() throws Exception {
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule(2);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1.0, "MSm3/day");
    Separator scrubber = new Separator("20-VG-001", feed);
    Compressor compressor = new Compressor("20-KA-001", scrubber.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.setName("Gas compression train");
    process.add(feed);
    process.add(scrubber);
    process.add(compressor);
    process.run();

    double[] chartConditions = new double[] { 19.0, 303.15, 50.0, 0.90 };
    double[] speeds = new double[] { 8000.0, 10000.0 };
    double[][] flows = new double[][] { { 4000.0, 6000.0, 8000.0 }, { 5000.0, 7500.0, 10000.0 } };
    double[][] heads = new double[][] { { 55.0, 50.0, 42.0 }, { 85.0, 78.0, 65.0 } };
    double[][] efficiencies = new double[][] { { 70.0, 78.0, 72.0 }, { 71.0, 80.0, 73.0 } };
    compressor.getCompressorChart().setCurves(chartConditions, speeds, flows, heads, efficiencies);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.getAntiSurge().setActive(true);

    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Compression engineering model", process)
        .registerProposedInstruments(true).build();

    assertFalse(project.getRequirementsForEquipment("20-KA-001").isEmpty());
    assertFalse(process.getMeasurementDevices().isEmpty());
    for (EngineeringRequirement requirement : project.getRequirements()) {
      assertEquals(EngineeringApprovalStatus.REVIEW_REQUIRED, requirement.getApprovalStatus());
      if (requirement.getType() == EngineeringRequirement.Type.TRIP) {
        assertEquals("SIL_UNASSIGNED", requirement.getSilTarget());
      }
    }
    assertTrue(project.getDesignBasis().getStandards().stream()
        .anyMatch(standard -> "NORSOK I-001".equals(standard.getCode())));

    ExportResult result = DexpiEngineeringExporter.export(project, temporaryDirectory.resolve("package"));
    assertTrue(Files.exists(result.getDexpiFile()));
    assertTrue(Files.exists(result.getManifestFile()));
    assertTrue(Files.exists(result.getCauseAndEffectFile()));
    assertTrue(result.getCompressorMapFiles().containsKey("20-KA-001"));

    String xml = new String(Files.readAllBytes(result.getDexpiFile()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("NeqSimEngineeringProject"));
    assertTrue(xml.contains("EngineeringRequirementIds"));
    assertTrue(xml.contains("CompressorPerformanceMapDocument"));
    assertTrue(xml.contains("datasets/20-KA-001-compressor-map.json"));
    assertTrue(xml.contains("ProcessInstrumentationFunction"));
    assertTrue(xml.contains("SpringLoadedGlobeSafetyValve"));
    assertTrue(xml.contains("ASCV-20KA001"));
    assertTrue(xml.contains("ESDV-SUC-20KA001"));
    assertTrue(xml.contains("ESDV-DIS-20KA001"));
    assertTrue(xml.contains("NRV-20KA001"));
    assertTrue(xml.contains("EngineeringGovernance"));
    assertTrue(xml.contains("VotingArchitecture"));
    assertTrue(xml.contains("NOT_ASSIGNED"));

    String manifest = new String(Files.readAllBytes(result.getManifestFile()), StandardCharsets.UTF_8);
    assertTrue(manifest.contains("NORSOK P-002"));
    assertTrue(manifest.contains("SIL_UNASSIGNED"));
    assertTrue(manifest.contains("REVIEW_REQUIRED"));

    String causeAndEffect = new String(Files.readAllBytes(result.getCauseAndEffectFile()), StandardCharsets.UTF_8);
    assertTrue(causeAndEffect.contains("PROPOSED_FOR_HAZOP_LOPA_AND_DISCIPLINE_REVIEW"));
    assertTrue(causeAndEffect.contains("High-high pressure"));
    assertTrue(causeAndEffect.contains("Trip compressor driver"));
    assertTrue(causeAndEffect.contains("votingArchitecture"));
  }
}
