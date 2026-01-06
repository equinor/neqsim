package neqsim.process.mechanicaldesign.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.mechanicaldesign.DesignLimitData;

/**
 * Tests for StandardBasedCsvDataSource class.
 */
class StandardBasedCsvDataSourceTest {

  @TempDir
  Path tempDir;

  @Test
  void testLoadStandardFormatCsv() throws IOException {
    // Create a test CSV file with standard format
    String csvContent =
        "STANDARD_CODE,VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION\n"
            + "NORSOK-L-001,Rev 6,Pipeline,MaxPressure,0,150,barg,Maximum design pressure\n"
            + "NORSOK-L-001,Rev 6,Pipeline,MinTemperature,-46,-46,C,Minimum design temperature\n"
            + "NORSOK-L-001,Rev 6,Pipeline,CorrosionAllowance,3,3,mm,Corrosion allowance\n"
            + "ASME-VIII-Div1,2021,Separator,MaxPressure,0,200,barg,Maximum design pressure\n"
            + "ASME-VIII-Div1,2021,Separator,JointEfficiency,0.7,1.0,-,Joint efficiency\n";

    Path csvFile = tempDir.resolve("test_standards.csv");
    Files.write(csvFile, csvContent.getBytes());

    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(csvFile);

    // Test getting design limits by standard
    Optional<DesignLimitData> pipelineData =
        dataSource.getDesignLimitsByStandard("NORSOK-L-001", "Rev 6", "Pipeline");

    assertTrue(pipelineData.isPresent());
    assertEquals(150.0, pipelineData.get().getMaxPressure(), 0.01);
    assertEquals(-46.0, pipelineData.get().getMinTemperature(), 0.01);
    assertEquals(3.0, pipelineData.get().getCorrosionAllowance(), 0.01);

    // Test getting separator data
    Optional<DesignLimitData> separatorData =
        dataSource.getDesignLimitsByStandard("ASME-VIII-Div1", "2021", "Separator");

    assertTrue(separatorData.isPresent());
    assertEquals(200.0, separatorData.get().getMaxPressure(), 0.01);
    assertEquals(1.0, separatorData.get().getJointEfficiency(), 0.01);
  }

  @Test
  void testLoadLegacyFormatCsv() throws IOException {
    // Create a test CSV file with legacy format
    String csvContent =
        "EQUIPMENTTYPE,COMPANY,MAXPRESSURE,MINPRESSURE,MAXTEMPERATURE,MINTEMPERATURE,CORROSIONALLOWANCE,JOINTEFFICIENCY\n"
            + "Pipeline,StatoilTR,100,0,150,-50,3.0,0.85\n"
            + "Separator,StatoilTR,150,0,200,-40,2.5,0.90\n";

    Path csvFile = tempDir.resolve("legacy_standards.csv");
    Files.write(csvFile, csvContent.getBytes());

    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(csvFile);

    // Test getting design limits by company
    Optional<DesignLimitData> pipelineData = dataSource.getDesignLimits("Pipeline", "StatoilTR");

    assertTrue(pipelineData.isPresent());
    assertEquals(100.0, pipelineData.get().getMaxPressure(), 0.01);
    assertEquals(0.0, pipelineData.get().getMinPressure(), 0.01);
    assertEquals(150.0, pipelineData.get().getMaxTemperature(), 0.01);
    assertEquals(-50.0, pipelineData.get().getMinTemperature(), 0.01);
    assertEquals(3.0, pipelineData.get().getCorrosionAllowance(), 0.01);
    assertEquals(0.85, pipelineData.get().getJointEfficiency(), 0.01);
  }

  @Test
  void testGetAvailableStandards() throws IOException {
    String csvContent =
        "STANDARD_CODE,VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION\n"
            + "NORSOK-L-001,Rev 6,Pipeline,MaxPressure,0,150,barg,Max pressure\n"
            + "ASME-VIII-Div1,2021,Separator,MaxPressure,0,200,barg,Max pressure\n"
            + "API-12J,8th Ed,Separator,GasLoadFactor,0.1,0.15,-,K-factor\n";

    Path csvFile = tempDir.resolve("standards_list.csv");
    Files.write(csvFile, csvContent.getBytes());

    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(csvFile);

    // Get all standards
    List<String> allStandards = dataSource.getAvailableStandards(null);
    assertEquals(3, allStandards.size());
    assertTrue(allStandards.contains("NORSOK-L-001"));
    assertTrue(allStandards.contains("ASME-VIII-Div1"));
    assertTrue(allStandards.contains("API-12J"));

    // Get standards for specific equipment
    List<String> separatorStandards = dataSource.getAvailableStandards("Separator");
    assertEquals(2, separatorStandards.size());
    assertTrue(separatorStandards.contains("ASME-VIII-Div1"));
    assertTrue(separatorStandards.contains("API-12J"));
    assertFalse(separatorStandards.contains("NORSOK-L-001"));
  }

  @Test
  void testGetAvailableVersions() throws IOException {
    String csvContent =
        "STANDARD_CODE,VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION\n"
            + "ASME-VIII-Div1,2021,Separator,MaxPressure,0,200,barg,Max pressure\n"
            + "ASME-VIII-Div1,2019,Separator,MaxPressure,0,180,barg,Max pressure\n"
            + "ASME-VIII-Div1,2017,Separator,MaxPressure,0,175,barg,Max pressure\n";

    Path csvFile = tempDir.resolve("versions.csv");
    Files.write(csvFile, csvContent.getBytes());

    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(csvFile);

    List<String> versions = dataSource.getAvailableVersions("ASME-VIII-Div1");
    assertEquals(3, versions.size());
    assertTrue(versions.contains("2021"));
    assertTrue(versions.contains("2019"));
    assertTrue(versions.contains("2017"));
  }

  @Test
  void testNonExistentFile() {
    Path nonExistent = tempDir.resolve("nonexistent.csv");
    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(nonExistent);

    Optional<DesignLimitData> data = dataSource.getDesignLimits("Pipeline", "StatoilTR");
    assertFalse(data.isPresent());
  }

  @Test
  void testQuotedFields() throws IOException {
    String csvContent =
        "STANDARD_CODE,VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION\n"
            + "\"NORSOK-L-001\",\"Rev 6\",\"Pipeline\",\"MaxPressure\",0,150,\"barg\",\"Maximum design pressure\"\n";

    Path csvFile = tempDir.resolve("quoted.csv");
    Files.write(csvFile, csvContent.getBytes());

    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(csvFile);

    Optional<DesignLimitData> data =
        dataSource.getDesignLimitsByStandard("NORSOK-L-001", "Rev 6", "Pipeline");

    assertTrue(data.isPresent());
    assertEquals(150.0, data.get().getMaxPressure(), 0.01);
  }

  @Test
  void testCaseInsensitiveMatching() throws IOException {
    String csvContent =
        "STANDARD_CODE,VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION\n"
            + "NORSOK-L-001,Rev 6,Pipeline,MaxPressure,0,150,barg,Max pressure\n";

    Path csvFile = tempDir.resolve("case.csv");
    Files.write(csvFile, csvContent.getBytes());

    StandardBasedCsvDataSource dataSource = new StandardBasedCsvDataSource(csvFile);

    // Test case-insensitive matching
    Optional<DesignLimitData> data =
        dataSource.getDesignLimitsByStandard("norsok-l-001", "rev 6", "pipeline");

    assertTrue(data.isPresent());
  }
}
