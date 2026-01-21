package neqsim.process.mechanicaldesign.torg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tests for TorgManager class.
 */
class TorgManagerTest {
  @TempDir
  Path tempDir;

  private TorgManager manager;
  private Path csvFile;

  @BeforeEach
  void setUp() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION,MIN_AMBIENT_TEMP,MAX_AMBIENT_TEMP\n"
            + "PROJ-001,Test Platform,Equinor,1,pressure vessel design code,ASME-VIII-Div1,2021,-40,45\n"
            + "PROJ-001,Test Platform,Equinor,1,separator process design,API-12J,8th Ed,-40,45\n"
            + "PROJ-001,Test Platform,Equinor,1,compressor design codes,API-617,8th Ed,-40,45\n"
            + "PROJ-002,Another Project,Shell,2,pressure vessel design code,EN-13445,2021,-20,50\n";

    csvFile = tempDir.resolve("test_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    manager = new TorgManager();
    manager.addDataSource(new CsvTorgDataSource(csvFile));
  }

  @Test
  void testLoadTorg() {
    java.util.Optional<TechnicalRequirementsDocument> optTorg = manager.load("PROJ-001");

    assertTrue(optTorg.isPresent());
    assertEquals("PROJ-001", optTorg.get().getProjectId());
    assertEquals("Test Platform", optTorg.get().getProjectName());
  }

  @Test
  void testLoadTorgByCompanyAndProject() {
    java.util.Optional<TechnicalRequirementsDocument> optTorg =
        manager.load("Equinor", "Test Platform");

    assertTrue(optTorg.isPresent());
    assertEquals("PROJ-001", optTorg.get().getProjectId());
  }

  @Test
  void testLoadNonExistentTorg() {
    java.util.Optional<TechnicalRequirementsDocument> optTorg = manager.load("NONEXISTENT");
    assertFalse(optTorg.isPresent());
  }

  @Test
  void testApplyToProcessSystem() {
    // Create a simple process system
    ProcessSystem process = new ProcessSystem("Test Process");

    Separator separator = new Separator("Test Separator");
    Compressor compressor = new Compressor("Test Compressor");

    process.add(separator);
    process.add(compressor);

    // Load and apply TORG
    boolean applied = manager.loadAndApply("PROJ-001", process);

    assertTrue(applied);
    assertNotNull(manager.getActiveTorg());
    assertEquals("PROJ-001", manager.getActiveTorg().getProjectId());

    // Check that standards were applied
    List<StandardType> separatorStandards = manager.getAppliedStandards("Test Separator");
    assertFalse(separatorStandards.isEmpty());

    List<StandardType> compressorStandards = manager.getAppliedStandards("Test Compressor");
    assertFalse(compressorStandards.isEmpty());
    assertTrue(compressorStandards.contains(StandardType.API_617));
  }

  @Test
  void testApplyToSingleEquipment() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("MANUAL-001").addStandard("separator process design", StandardType.API_12J)
        .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1).build();

    Separator separator = new Separator("Manual Separator");
    manager.applyToEquipment(torg, separator);

    List<StandardType> standards = manager.getAppliedStandards("Manual Separator");
    assertFalse(standards.isEmpty());
  }

  @Test
  void testGetAllAppliedStandards() {
    ProcessSystem process = new ProcessSystem("Test Process");
    Separator sep1 = new Separator("Separator 1");
    Separator sep2 = new Separator("Separator 2");
    process.add(sep1);
    process.add(sep2);

    manager.loadAndApply("PROJ-001", process);

    Map<String, List<StandardType>> allStandards = manager.getAllAppliedStandards();
    assertEquals(2, allStandards.size());
    assertTrue(allStandards.containsKey("Separator 1"));
    assertTrue(allStandards.containsKey("Separator 2"));
  }

  @Test
  void testGetAvailableProjects() {
    List<String> projects = manager.getAvailableProjects();
    assertEquals(2, projects.size());
    assertTrue(projects.contains("PROJ-001"));
    assertTrue(projects.contains("PROJ-002"));
  }

  @Test
  void testGenerateSummary() {
    ProcessSystem process = new ProcessSystem("Test Process");
    Separator separator = new Separator("Test Separator");
    process.add(separator);

    manager.loadAndApply("PROJ-001", process);

    String summary = manager.generateSummary();
    assertTrue(summary.contains("PROJ-001"));
    assertTrue(summary.contains("Test Platform"));
    assertTrue(summary.contains("Equinor"));
    assertTrue(summary.contains("Test Separator"));
  }

  @Test
  void testReset() {
    ProcessSystem process = new ProcessSystem("Test Process");
    Separator separator = new Separator("Test Separator");
    process.add(separator);

    manager.loadAndApply("PROJ-001", process);
    assertNotNull(manager.getActiveTorg());
    assertFalse(manager.getAllAppliedStandards().isEmpty());

    manager.reset();
    assertTrue(manager.getAllAppliedStandards().isEmpty());
  }

  @Test
  void testSetActiveTorg() {
    TechnicalRequirementsDocument torg =
        TechnicalRequirementsDocument.builder().projectId("DIRECT-001").build();

    manager.setActiveTorg(torg);
    assertEquals("DIRECT-001", manager.getActiveTorg().getProjectId());
  }

  @Test
  void testChainedDataSources() throws IOException {
    // Create a second CSV with different projects
    String csvContent2 =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-100,Extra Project,BP,1,pressure vessel design code,ASME-VIII-Div2,2021\n";

    Path csvFile2 = tempDir.resolve("extra_torg.csv");
    Files.write(csvFile2, csvContent2.getBytes());

    manager.addDataSource(new CsvTorgDataSource(csvFile2));

    // Should find projects from both data sources
    List<String> projects = manager.getAvailableProjects();
    assertTrue(projects.contains("PROJ-001"));
    assertTrue(projects.contains("PROJ-100"));

    // Should load from second data source
    java.util.Optional<TechnicalRequirementsDocument> torg = manager.load("PROJ-100");
    assertTrue(torg.isPresent());
    assertEquals("Extra Project", torg.get().getProjectName());
  }

  @Test
  void testLoadAndApplyNonExistent() {
    ProcessSystem process = new ProcessSystem("Test Process");
    boolean applied = manager.loadAndApply("NONEXISTENT", process);
    assertFalse(applied);
  }

  @Test
  void testApplyWithEnvironmentalConditions() {
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("ENV-001").environmentalConditions(-46.0, 40.0)
        .addStandard("separator process design", StandardType.API_12J).build();

    Separator separator = new Separator("Env Test");
    manager.applyToEquipment(torg, separator);

    // Environmental conditions should be applied
    assertEquals(-46.0, separator.getMechanicalDesign().getMinOperationTemperature(), 0.01);
  }
}
