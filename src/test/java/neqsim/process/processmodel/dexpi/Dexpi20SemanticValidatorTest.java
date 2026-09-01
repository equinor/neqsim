package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Native DEXPI 2.0 semantic, branching-topology and golden-summary regression tests. */
class Dexpi20SemanticValidatorTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void branchingProcessMatchesGoldenSemanticSummary() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    Stream feed = new Stream("10-FEED-001", fluid);
    Separator separator = new Separator("10-VA-001", feed);
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    Pump pump = new Pump("10-PA-001", separator.getLiquidOutStream());
    ProcessSystem process = new ProcessSystem();
    process.setName("DEXPI branching golden model");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(pump);

    Path output = temporaryDirectory.resolve("branching.dexpi.xml");
    Dexpi20XmlWriter.write(process, output.toFile());
    Path goldenPath = Paths.get(getClass().getResource("/dexpi/2.0/golden/branching-process.dexpi.xml").toURI());
    String goldenXml = new String(Files.readAllBytes(goldenPath), StandardCharsets.UTF_8);
    String generatedXml = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
    assertEquals(normalizeLineEndings(goldenXml), normalizeLineEndings(generatedXml));
    Dexpi20XmlValidator.validate(output);
    assertTrue(Dexpi20SemanticValidator.validate(output).isValid());

    Dexpi20ModelInspector.ModelSummary summary = Dexpi20ModelInspector.inspect(output);
    JsonObject expected = JsonParser
        .parseReader(new java.io.InputStreamReader(
            getClass().getResourceAsStream("/dexpi/2.0/golden/neqsim-native-summary.json"), StandardCharsets.UTF_8))
        .getAsJsonObject();
    assertEquals(expected.getAsJsonObject("equipmentTypes").size(), summary.getEquipmentTypes().size());
    for (String tag : expected.getAsJsonObject("equipmentTypes").keySet()) {
      assertEquals(expected.getAsJsonObject("equipmentTypes").get(tag).getAsString(),
          summary.getEquipmentTypes().get(tag));
    }
    assertEquals(expected.get("pipingConnectionCount").getAsInt(), summary.getPipingConnectionCount());
    assertEquals(expected.get("instrumentationFunctionCount").getAsInt(), summary.getInstrumentationFunctionCount());
    assertEquals(expected.get("offPageConnectorCount").getAsInt(), summary.getOffPageConnectorCount());
    assertEquals(expected.get("representationGroupCount").getAsInt(), summary.getRepresentationGroupCount());
  }

  @Test
  void rejectsDuplicateIdsAndDanglingReferences() throws Exception {
    String xml = "<Model name=\"Invalid\" uri=\"urn:invalid\">"
        + "<Import prefix=\"Core\" source=\"https://data.dexpi.org/models/2.0.0/Core.xml\"/>"
        + "<Import prefix=\"Plant\" source=\"https://data.dexpi.org/models/2.0.0/Plant.xml\"/>"
        + "<Object id=\"Duplicate\" type=\"Core/EngineeringModel\">"
        + "<References property=\"ConceptualModel\" objects=\"#Missing\"/></Object>"
        + "<Object id=\"Duplicate\" type=\"Plant/PlantModel\"/>"
        + "<Object id=\"Typo\" type=\"Plant/Piping.Piep\"/></Model>";
    Path file = temporaryDirectory.resolve("invalid.dexpi.xml");
    Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
    Dexpi20SemanticValidator.ValidationReport report = Dexpi20SemanticValidator.validate(file);
    assertFalse(report.isValid());
    assertTrue(report.getErrors().toString().contains("Duplicate object id"));
    assertTrue(report.getErrors().toString().contains("Dangling object reference"));
    assertTrue(report.getErrors().toString().contains("outside the supported NeqSim DEXPI 2.0 profile"));
  }

  private static String normalizeLineEndings(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }
}
