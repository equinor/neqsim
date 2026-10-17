package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.mechanicaldesign.data.StandardBasedCsvDataSource;

/**
 * Guards the structure of bundled standards CSVs and the records recovered from missing line separators.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class StandardsCsvIntegrityTest {
  /**
   * Requires every standards record, including index and subsea schemas, to match its own header width.
   *
   * @throws IOException if a resource cannot be read
   * @throws URISyntaxException if the resource directory URL cannot be converted to a path
   */
  @Test
  void everyStandardsCsvRecordMatchesItsHeader() throws IOException, URISyntaxException {
    Path directory = Paths.get(getClass().getResource("/designdata/standards").toURI());
    int fileCount = 0;
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.csv")) {
      for (Path file : files) {
        fileCount++;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
          String header = reader.readLine();
          assertNotNull(header, file + " must have a header");
          int expectedWidth = countCsvFields(header);
          int lineNumber = 1;
          String line;
          while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!line.trim().isEmpty()) {
              assertEquals(expectedWidth, countCsvFields(line), file.getFileName() + ":" + lineNumber);
            }
          }
        }
      }
    }
    assertTrue(fileCount >= 7, "All bundled standards CSV files must be checked");
  }

  /** Checks that quoted commas and escaped quotes do not create additional CSV fields. */
  @Test
  void fieldCounterHandlesCsvQuoting() {
    assertEquals(3, countCsvFields("one,\"two, three\",\"four \"\"quoted\"\"\""));
    assertEquals(3, countCsvFields("one,two,"));
    assertEquals(5, countCsvFields("one,two,\"description\"\"next\",value,\"description\""));
  }

  /**
   * Confirms that the public CSV data source returns both limits for each previously hidden specification.
   *
   * @param table standards resource basename
   * @param standard standard identifier
   * @param version standard version
   * @param equipment equipment type
   * @param specification specification name
   * @param minimum stored lower limit
   * @param maximum stored upper limit
   */
  @ParameterizedTest
  @CsvSource({"asme_standards,ASME-B16.5,2020,Manifold,FlangePressureClass2500,425.0,425.0",
      "dnv_iso_en_standards,DNV-ST-F101,2021,Manifold,UsageFactor,0.77,0.90",
      "dnv_iso_en_standards,ISO-16530-1,2017,SubseaWell,WellIntegrityTestInterval,0.50,1.00"})
  void restoredSpecificationsAreAvailable(String table, String standard, String version, String equipment,
      String specification, double minimum, double maximum) {
    StandardBasedCsvDataSource source = new StandardBasedCsvDataSource("designdata/standards/" + table + ".csv");
    Map<String, double[]> specifications = source.getSpecificationValues(standard, version, equipment);
    assertTrue(specifications.containsKey(specification),
        "Missing " + standard + "/" + equipment + "/" + specification);
    assertArrayEquals(new double[] {minimum, maximum}, specifications.get(specification), 1.0e-12);
  }

  /**
   * Verifies exact H2 lookup keys, stored limits, units and descriptions on both sides of each repaired boundary.
   *
   * @param table database table
   * @param standard standard identifier
   * @param version standard version
   * @param equipment equipment type
   * @param specification specification name
   * @param minimum stored lower limit
   * @param maximum stored upper limit
   * @param unit stored unit
   * @param description uncorrupted record description
   * @throws SQLException if a database lookup fails
   */
  @ParameterizedTest
  @CsvSource({
      "asme_standards,ASME-B16.5,2020,Manifold,FlangePressureClass1500,255.0,255.0,barg,Class 1500 rating at ambient",
      "asme_standards,ASME-B16.5,2020,Manifold,FlangePressureClass2500,425.0,425.0,barg,Class 2500 rating at ambient",
      "dnv_iso_en_standards,DNV-RP-F112,2019,Manifold,SuperDuplexDesignFactor,0.67,0.72,-,Design factor for super duplex",
      "dnv_iso_en_standards,DNV-ST-F101,2021,Manifold,UsageFactor,0.77,0.90,-,Usage factor for manifold piping",
      "dnv_iso_en_standards,PD-5500,2021,HeatExchanger,CorrosionAllowance,1.50,3.00,mm,Corrosion allowance",
      "dnv_iso_en_standards,ISO-16530-1,2017,SubseaWell,WellIntegrityTestInterval,0.50,1.00,yr,Well integrity test interval (Section 8)"})
  void repairedRecordsAreLoadedWithoutCorruptedDescriptions(String table, String standard, String version,
      String equipment, String specification, double minimum, double maximum, String unit, String description)
      throws SQLException {
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet result = database.getResultSet("SELECT MINVALUE, MAXVALUE, UNIT, DESCRIPTION FROM " + table
            + " WHERE STANDARD_CODE='" + standard + "' AND VERSION='" + version + "' AND EQUIPMENTTYPE='" + equipment
            + "' AND SPECIFICATION='" + specification + "'")) {
      assertTrue(result.next(), "Missing " + standard + "/" + equipment + "/" + specification);
      assertEquals(minimum, result.getDouble("MINVALUE"), 1.0e-12);
      assertEquals(maximum, result.getDouble("MAXVALUE"), 1.0e-12);
      assertEquals(unit, result.getString("UNIT"));
      assertEquals(description, result.getString("DESCRIPTION"));
      assertFalse(result.next(), "The restored lookup key must be unique");
    }
  }

  /**
   * Counts commas outside quoted fields, respecting doubled quotes inside a CSV description.
   *
   * @param record one complete CSV record
   * @return the number of fields, including any trailing empty field
   */
  private static int countCsvFields(String record) {
    boolean quoted = false;
    int fields = 1;
    for (int index = 0; index < record.length(); index++) {
      char character = record.charAt(index);
      if (character == '"') {
        if (quoted && index + 1 < record.length() && record.charAt(index + 1) == '"') {
          index++;
        } else {
          quoted = !quoted;
        }
      } else if (character == ',' && !quoted) {
        fields++;
      }
    }
    assertFalse(quoted, "Unterminated quoted CSV record: " + record);
    return fields;
  }
}
