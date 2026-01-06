package neqsim.process.mechanicaldesign.torg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/**
 * Tests for CsvTorgDataSource class.
 */
class CsvTorgDataSourceTest {

  @TempDir
  Path tempDir;

  @Test
  void testLoadStandardsFormatCsv() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION,MIN_AMBIENT_TEMP,MAX_AMBIENT_TEMP\n"
            + "PROJ-001,Test Platform,TestCo,1,pressure vessel design code,ASME-VIII-Div1,2021,-40,45\n"
            + "PROJ-001,Test Platform,TestCo,1,separator process design,API-12J,8th Ed,-40,45\n"
            + "PROJ-001,Test Platform,TestCo,1,pipeline design codes,NORSOK-L-001,Rev 6,-40,45\n";

    Path csvFile = tempDir.resolve("torg_standards.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    Optional<TechnicalRequirementsDocument> optTorg = dataSource.loadByProjectId("PROJ-001");

    assertTrue(optTorg.isPresent());
    TechnicalRequirementsDocument torg = optTorg.get();

    assertEquals("PROJ-001", torg.getProjectId());
    assertEquals("Test Platform", torg.getProjectName());
    assertEquals("TestCo", torg.getCompanyIdentifier());

    // Check standards
    List<StandardType> pvStandards = torg.getStandardsForCategory("pressure vessel design code");
    assertEquals(1, pvStandards.size());
    assertEquals(StandardType.ASME_VIII_DIV1, pvStandards.get(0));

    List<StandardType> sepStandards = torg.getStandardsForCategory("separator process design");
    assertEquals(1, sepStandards.size());
    assertEquals(StandardType.API_12J, sepStandards.get(0));

    List<StandardType> pipeStandards = torg.getStandardsForCategory("pipeline design codes");
    assertEquals(1, pipeStandards.size());
    assertEquals(StandardType.NORSOK_L_001, pipeStandards.get(0));

    // Check environmental conditions
    assertNotNull(torg.getEnvironmentalConditions());
    assertEquals(-40.0, torg.getEnvironmentalConditions().getMinAmbientTemperature(), 0.01);
    assertEquals(45.0, torg.getEnvironmentalConditions().getMaxAmbientTemperature(), 0.01);
  }

  @Test
  void testLoadMultipleProjects() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-001,Project One,CompanyA,1,pressure vessel design code,ASME-VIII-Div1,2021\n"
            + "PROJ-002,Project Two,CompanyB,2,pressure vessel design code,EN-13445,2021\n"
            + "PROJ-002,Project Two,CompanyB,2,pipeline design codes,DNV-ST-F101,2021\n";

    Path csvFile = tempDir.resolve("multi_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    // Check project 1
    Optional<TechnicalRequirementsDocument> torg1 = dataSource.loadByProjectId("PROJ-001");
    assertTrue(torg1.isPresent());
    assertEquals("Project One", torg1.get().getProjectName());

    // Check project 2
    Optional<TechnicalRequirementsDocument> torg2 = dataSource.loadByProjectId("PROJ-002");
    assertTrue(torg2.isPresent());
    assertEquals("Project Two", torg2.get().getProjectName());

    List<StandardType> torg2Standards =
        torg2.get().getStandardsForCategory("pressure vessel design code");
    assertEquals(1, torg2Standards.size());
    assertEquals(StandardType.EN_13445, torg2Standards.get(0));
  }

  @Test
  void testLoadByCompanyAndProject() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-001,Offshore Platform,Equinor,1,pressure vessel design code,ASME-VIII-Div1,2021\n";

    Path csvFile = tempDir.resolve("company_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    Optional<TechnicalRequirementsDocument> torg =
        dataSource.loadByCompanyAndProject("Equinor", "Offshore Platform");

    assertTrue(torg.isPresent());
    assertEquals("PROJ-001", torg.get().getProjectId());
  }

  @Test
  void testGetAvailableProjectIds() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-001,Project One,CompanyA,1,pressure vessel design code,ASME-VIII-Div1,2021\n"
            + "PROJ-002,Project Two,CompanyB,2,pressure vessel design code,EN-13445,2021\n"
            + "PROJ-003,Project Three,CompanyA,1,pipeline design codes,NORSOK-L-001,Rev 6\n";

    Path csvFile = tempDir.resolve("ids_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    List<String> projectIds = dataSource.getAvailableProjectIds();
    assertEquals(3, projectIds.size());
    assertTrue(projectIds.contains("PROJ-001"));
    assertTrue(projectIds.contains("PROJ-002"));
    assertTrue(projectIds.contains("PROJ-003"));
  }

  @Test
  void testGetAvailableCompanies() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-001,Project One,Equinor,1,pressure vessel design code,ASME-VIII-Div1,2021\n"
            + "PROJ-002,Project Two,Shell,2,pressure vessel design code,EN-13445,2021\n"
            + "PROJ-003,Project Three,Equinor,1,pipeline design codes,NORSOK-L-001,Rev 6\n";

    Path csvFile = tempDir.resolve("companies_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    List<String> companies = dataSource.getAvailableCompanies();
    assertEquals(2, companies.size());
    assertTrue(companies.contains("Equinor"));
    assertTrue(companies.contains("Shell"));
  }

  @Test
  void testNonExistentProject() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-001,Project One,TestCo,1,pressure vessel design code,ASME-VIII-Div1,2021\n";

    Path csvFile = tempDir.resolve("single_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    Optional<TechnicalRequirementsDocument> torg = dataSource.loadByProjectId("NONEXISTENT");
    assertFalse(torg.isPresent());
  }

  @Test
  void testNonExistentFile() {
    Path nonExistent = tempDir.resolve("nonexistent.csv");
    CsvTorgDataSource dataSource = new CsvTorgDataSource(nonExistent);

    Optional<TechnicalRequirementsDocument> torg = dataSource.loadByProjectId("ANY");
    assertFalse(torg.isPresent());
  }

  @Test
  void testHasProject() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "PROJ-001,Project One,TestCo,1,pressure vessel design code,ASME-VIII-Div1,2021\n";

    Path csvFile = tempDir.resolve("has_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    assertTrue(dataSource.hasProject("PROJ-001"));
    assertFalse(dataSource.hasProject("NONEXISTENT"));
  }

  @Test
  void testQuotedFields() throws IOException {
    String csvContent =
        "PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,DESIGN_CATEGORY,STANDARD_CODE,VERSION\n"
            + "\"PROJ-001\",\"Test, Platform\",\"Test Co, Inc.\",\"1\",\"pressure vessel design code\",\"ASME-VIII-Div1\",\"2021\"\n";

    Path csvFile = tempDir.resolve("quoted_torg.csv");
    Files.write(csvFile, csvContent.getBytes());

    CsvTorgDataSource dataSource = new CsvTorgDataSource(csvFile);

    Optional<TechnicalRequirementsDocument> torg = dataSource.loadByProjectId("PROJ-001");
    assertTrue(torg.isPresent());
    assertEquals("Test, Platform", torg.get().getProjectName());
    assertEquals("Test Co, Inc.", torg.get().getCompanyIdentifier());
  }
}
