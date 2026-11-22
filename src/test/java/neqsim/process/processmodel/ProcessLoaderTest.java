package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;

class ProcessLoaderTest {
  private static final Logger logger = LogManager.getLogger(ProcessLoaderTest.class);

  /**
   * Execute the published YAML file and compare it with the equivalent JSON flowsheet.
   *
   * @param tempDir directory for the UTF-8 configuration file
   * @throws Exception if the fixture cannot be read or written
   */
  @Test
  void publishedYamlMatchesJson(@TempDir Path tempDir) throws Exception {
    String guide = new String(
        Files.readAllBytes(java.nio.file.Paths.get("docs/process/json_process_models_and_systems.md")),
        StandardCharsets.UTF_8);
    int start = guide.indexOf("```yaml\n");
    assertTrue(start >= 0);
    String yaml = guide.substring(start + 8, guide.indexOf("\n```", start + 8));
    Path file = tempDir.resolve("feed.yaml");
    Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
    String json = "{\"autoRun\":true,\"fluid\":{\"model\":\"SRK\",\"temperature\":298.15,\"pressure\":50.0,"
        + "\"components\":{\"methane\":0.8,\"n-decane\":0.2}},\"process\":["
        + "{\"type\":\"Stream\",\"name\":\"feed\",\"properties\":{\"flowRate\":[240.0,\"kg/hr\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"inlet separator\",\"inlet\":\"feed\"},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"gas valve\",\"inlet\":\"inlet separator.gasOut\","
        + "\"properties\":{\"outletPressure\":[15.0,\"bara\"]}}]}";
    SimulationResult reference = ProcessSystem.fromJson(json);
    assertTrue(reference.isSuccess(), reference.toString());
    Separator expected = (Separator) reference.getProcessSystem().getUnit("inlet separator");
    for (SimulationResult result : new SimulationResult[] {ProcessSystem.fromYaml(yaml),
        ProcessSystem.fromYamlFile(file)}) {
      assertTrue(result.isSuccess(), result.toString());
      ProcessSystem process = result.getProcessSystem();
      assertNotNull(result.getReportJson());
      Stream feed = (Stream) process.getUnit("feed");
      assertEquals(240.0, feed.getFlowRate("kg/hr"), 1e-7);
      Separator separator = (Separator) process.getUnit("inlet separator");
      double gasFlow = separator.getGasOutStream().getFlowRate("kg/hr");
      assertTrue(gasFlow > 0.0 && gasFlow < 240.0);
      assertEquals(expected.getGasOutStream().getFlowRate("kg/hr"), gasFlow, 1e-8);
      ThrottlingValve valve = (ThrottlingValve) process.getUnit("gas valve");
      assertEquals(gasFlow, valve.getOutletStream().getFlowRate("kg/hr"), 1e-8);
      assertEquals(15.0, valve.getOutletStream().getPressure("bara"), 1e-9);
    }
  }

  /**
   * Verify parse, file and canonical schema errors are represented explicitly.
   *
   * @param tempDir directory without the requested file
   */
  @Test
  void yamlErrorsAreStructured(@TempDir Path tempDir) {
    for (String invalid : new String[] {null, "", "[", "[]", "process: []\nprocess: []",
        "process: []\n---\nprocess: []"}) {
      assertEquals("YAML_PARSE_ERROR", ProcessSystem.fromYaml(invalid).getErrors().get(0).getCode());
    }
    assertEquals("YAML_READ_ERROR",
        ProcessSystem.fromYamlFile(tempDir.resolve("missing")).getErrors().get(0).getCode());
    assertEquals(ProcessSystem.fromJson("{}").getErrors().get(0).getCode(),
        ProcessSystem.fromYaml("{}").getErrors().get(0).getCode());
  }

  @Test
  void testLoadProcessFromYaml(@TempDir Path tempDir) throws Exception {
    // Create a temporary YAML file for testing
    File tempYamlFile = File.createTempFile("testProcess", ".yaml");
    tempYamlFile.deleteOnExit();
    // Load YAML content from the relative path
    File yamlFile = new File("src/test/java/neqsim/process/processmodel/process.yaml");
    String yamlContent = new String(Files.readAllBytes(yamlFile.toPath()), StandardCharsets.UTF_8);
    Files.write(tempYamlFile.toPath(), yamlContent.getBytes(StandardCharsets.UTF_8));

    // Create a ProcessSystem instance
    ProcessSystem processSystem = new ProcessSystem();

    // Call the method under test
    ProcessLoader.loadProcessFromYaml(tempYamlFile, processSystem);
    processSystem.run();
    // logger.info(processSystem.getAllUnitNames());
    // Verify the unit was added
    ThrottlingValve unit = (ThrottlingValve) processSystem.getUnit("throttlingValve_1");
    assertNotNull(unit, "Unit should be added to the process system");
    assertEquals(10.45841962, unit.getOutletStream().getTemperature("C"), 1e-3);

    processSystem.exportToGraphviz(tempDir.resolve("process.dot").toString());
  }
}
