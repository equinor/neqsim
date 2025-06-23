package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.valve.ThrottlingValve;

class ProcessLoaderTest {
  @Test
  void testLoadProcessFromYaml() throws Exception {
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
    // System.out.println(processSystem.getAllUnitNames());
    // Verify the unit was added
    ThrottlingValve unit = (ThrottlingValve) processSystem.getUnit("throttlingValve_1");
    assertNotNull(unit, "Unit should be added to the process system");
    assertEquals(10.45841962, unit.getOutletStream().getTemperature("C"), 1e-3);

    processSystem.exportToGraphviz("src/test/java/neqsim/process/processmodel/process.dot");
  }
}
