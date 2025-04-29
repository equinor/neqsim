package neqsim.process.processmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessLoader;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;


class ProcessLoaderTest {

  @Test
  void testLoadProcessFromYaml() throws Exception {
    // Create a temporary YAML file for testing
    File tempYamlFile = File.createTempFile("testProcess", ".yaml");
    tempYamlFile.deleteOnExit();
    // Load YAML content from an external file named "process.yaml"
    File yamlFile = new File("C:\\Users\\esol\\OneDrive - Equinor\\Documents\\GitHub\\n" + //
        "eqsim\\src\\test\\java\\n" + //
        "eqsim\\process\\processmodel\\process.yaml");
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
