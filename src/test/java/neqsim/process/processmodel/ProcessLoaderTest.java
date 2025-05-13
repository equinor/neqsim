package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.File;
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
    String yamlContent = java.nio.file.Files.readString(yamlFile.toPath());
    java.nio.file.Files.writeString(tempYamlFile.toPath(), yamlContent);

    // Create a ProcessSystem instance
    ProcessSystem processSystem = new ProcessSystem();

    // Call the method under test
    ProcessLoader.loadProcessFromYaml(tempYamlFile, processSystem);
    processSystem.run();
    System.out.println(processSystem.getAllUnitNames());
    // Verify the unit was added
    ThrottlingValve unit = (ThrottlingValve) processSystem.getUnit("throttlingValve_1");
    assertNotNull(unit, "Unit should be added to the process system");
    assertEquals(10.45841962, unit.getOutletStream().getTemperature("C"), 1e-3);

    processSystem.exportToGraphviz("C:\\Users\\esol\\OneDrive - Equinor\\Documents\\GitHub\\n" + //
        "eqsim\\src\\test\\java\\n" + //
        "eqsim\\process\\processmodel\\process.dot");
  }

}
