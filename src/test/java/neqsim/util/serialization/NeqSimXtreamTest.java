package neqsim.util.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class NeqSimXtreamTest {

  private ProcessSystem testProcess;
  private String testFileName;

  @TempDir
  File tempDir;

  @BeforeEach
  void setUp() {
    // Create a simple test process system
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("methane", 10.0);
    testFluid.addComponent("ethane", 5.0);
    testFluid.setMixingRule(2);

    testProcess = new ProcessSystem("Test Process");
    Stream feed = (Stream) testProcess.addUnit("Feed", "stream");
    feed.setFluid(testFluid);

    testFileName = new File(tempDir, "test_process.neqsim").getAbsolutePath();
  }

  @AfterEach
  void cleanup() {
    File file = new File(testFileName);
    if (file.exists()) {
      file.delete();
    }
  }

  @Test
  void testSaveAndOpenNeqsim() {
    try {
      // Save the process
      boolean saved = NeqSimXtream.saveNeqsim(testProcess, testFileName);
      assertTrue(saved, "Process should be saved successfully");

      // Verify file exists and has content
      File savedFile = new File(testFileName);
      assertTrue(savedFile.exists(), "File should exist");
      assertTrue(savedFile.length() > 0, "File should have content");

      // Open the process
      Object loadedObject = NeqSimXtream.openNeqsim(testFileName);
      assertNotNull(loadedObject, "Loaded object should not be null");
      assertTrue(loadedObject instanceof ProcessSystem, "Should be a ProcessSystem");

      ProcessSystem loadedProcess = (ProcessSystem) loadedObject;
      assertEquals(testProcess.getName(), loadedProcess.getName(),
          "Process name should be preserved");
      assertEquals(testProcess.getUnitOperations().size(), loadedProcess.getUnitOperations().size(),
          "Number of units should be preserved");

    } catch (IOException e) {
      fail("Exception thrown: " + e.getMessage());
    }
  }

  @Test
  void testOpenNonExistentFile() {
    try {
      NeqSimXtream.openNeqsim("non_existent_file.neqsim");
      fail("Should throw FileNotFoundException");
    } catch (IOException e) {
      // Expected exception
      assertTrue(e.getMessage().contains("non_existent_file.neqsim"));
    }
  }
}
