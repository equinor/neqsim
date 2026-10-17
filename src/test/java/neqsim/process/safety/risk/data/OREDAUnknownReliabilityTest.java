package neqsim.process.safety.risk.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for unavailable and degenerate imported reliability data.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class OREDAUnknownReliabilityTest {
  @TempDir
  Path directory;

  /**
   * Keeps the record and its valid measurements when one numeric field is malformed.
   *
   * @throws IOException if the synthetic input cannot be written or loaded
   */
  @Test
  void malformedFieldsDoNotDiscardRecords() throws IOException {
    String csv = "EquipmentType,EquipmentClass,FailureMode,FailureRate,MTBF_hours,MTTR_hours,DataSource,Confidence\n"
        + "Mixed,BadRate,All modes,N/A,10000,10,Synthetic,Low\n"
        + "Mixed,BadMtbf,All modes,1e-4,invalid,10,Synthetic,Low\n"
        + "Mixed,BadMttr,All modes,1e-4,10000,1e-,Synthetic,Low\n"
        + "Known,AfterMalformed,All modes, 1e-4 , 10000 , 0 ,Synthetic,High\n";
    Path file = directory.resolve("malformed.csv");
    Files.write(file, csv.getBytes(StandardCharsets.UTF_8));
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromFile(file);

    assertEquals(4, importer.getRecordCount());
    OREDADataImporter.ReliabilityRecord badRate = importer.getRecord("Mixed", "BadRate");
    assertNotNull(badRate);
    assertTrue(Double.isNaN(badRate.getFailureRate()));
    assertTrue(Double.isNaN(badRate.getFailureRatePerYear()));
    assertNull(badRate.toMap().get("failureRate_perHour"));
    assertEquals(10000.0, badRate.getMtbfHours());
    assertEquals(10.0, badRate.getMttrHours());
    assertEquals(10000.0 / 10010.0, badRate.getAvailability(), 1e-12);

    OREDADataImporter.ReliabilityRecord badMtbf = importer.getRecord("Mixed", "BadMtbf");
    assertNotNull(badMtbf);
    assertEquals(1e-4, badMtbf.getFailureRate());
    assertTrue(Double.isNaN(badMtbf.getMtbfHours()));
    assertEquals(10.0, badMtbf.getMttrHours());
    assertNull(badMtbf.toMap().get("availability"));

    OREDADataImporter.ReliabilityRecord badMttr = importer.getRecord("Mixed", "BadMttr");
    assertNotNull(badMttr);
    assertEquals(1e-4, badMttr.getFailureRate());
    assertEquals(10000.0, badMttr.getMtbfHours());
    assertTrue(Double.isNaN(badMttr.getMttrHours()));
    assertNull(badMttr.toMap().get("availability"));

    OREDADataImporter.ReliabilityRecord valid = importer.getRecord("Known", "AfterMalformed");
    assertNotNull(valid);
    assertEquals(1e-4, valid.getFailureRate());
    assertEquals(10000.0, valid.getMtbfHours());
    assertEquals(0.0, valid.getMttrHours());
    assertEquals(1.0, valid.getAvailability());
  }

  /**
   * Preserves unavailable fields through the public CSV import and record accessors.
   *
   * @throws IOException if the synthetic input cannot be written or loaded
   */
  @Test
  void unavailableFieldsRemainUnknown() throws IOException {
    String csv = "EquipmentType,EquipmentClass,FailureMode,FailureRate,MTBF_hours,MTTR_hours,DataSource,Confidence\n"
        + "Unknown,Dash,All modes,-,-,-,Synthetic,Low\n" + "Unknown,Blank,All modes,,,,Synthetic,Low\n"
        + "Unknown,Partial,All modes,1e-4,10000,-,Synthetic,Low\n"
        + "Known,ZeroRepair,All modes,1e-4,10000,0,Synthetic,High\n"
        + "Known,ZeroRate,All modes,0,10000,10,Synthetic,High\n";
    Path file = directory.resolve("reliability.csv");
    Files.write(file, csv.getBytes(StandardCharsets.UTF_8));
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromFile(file);
    assertEquals(5, importer.getRecordCount());
    for (String equipmentClass : new String[] {"Dash", "Blank"}) {
      OREDADataImporter.ReliabilityRecord record = importer.getRecord("Unknown", equipmentClass);
      assertNotNull(record);
      assertTrue(Double.isNaN(record.getFailureRate()));
      assertTrue(Double.isNaN(record.getFailureRatePerYear()));
      assertTrue(Double.isNaN(record.getMtbfHours()));
      assertTrue(Double.isNaN(record.getMttrHours()));
      assertTrue(Double.isNaN(record.getAvailability()));
      assertTrue(Double.isNaN(importer.getFailureRate("Unknown", equipmentClass, "All modes")));
      assertTrue(Double.isNaN(importer.getMTBF("Unknown", equipmentClass, "All modes")));
      assertTrue(Double.isNaN(importer.getMTTR("Unknown", equipmentClass, "All modes")));
      for (String field : new String[] {"failureRate_perHour", "failureRate_perYear", "mtbf_hours", "mttr_hours",
          "availability"}) {
        assertNull(record.toMap().get(field));
      }
    }
    assertTrue(Double.isNaN(importer.getRecord("Unknown", "Partial").getAvailability()));
    assertEquals(1.0, importer.getRecord("Known", "ZeroRepair").getAvailability());
    assertEquals(0.0, importer.getRecord("Known", "ZeroRate").getFailureRate());
    assertEquals(10000.0 / 10010.0, importer.getRecord("Known", "ZeroRate").getAvailability(), 1e-12);
  }

  /**
   * Does not manufacture perfect availability from invalid or absent repair statistics.
   */
  @Test
  void degenerateStatisticsDoNotBecomePerfectAvailability() {
    double[][] invalid = {{0.0, 0.0}, {-1.0, 10.0}, {10.0, -1.0}, {Double.NaN, 10.0}, {10.0, Double.NaN},
        {Double.POSITIVE_INFINITY, 10.0}, {10.0, Double.POSITIVE_INFINITY}};
    for (double[] pair : invalid) {
      OREDADataImporter.ReliabilityRecord record = new OREDADataImporter.ReliabilityRecord("Unknown", "Custom",
          "All modes", Double.NaN, pair[0], pair[1], "Synthetic", "Low");
      assertTrue(Double.isNaN(record.getAvailability()));
    }
    OREDADataImporter.ReliabilityRecord zeroUptime = new OREDADataImporter.ReliabilityRecord("Known", "ZeroUptime",
        "All modes", 0.1, 0.0, 10.0, "Synthetic", "High");
    assertEquals(0.0, zeroUptime.getAvailability());
    OREDADataImporter.ReliabilityRecord largeFinite = new OREDADataImporter.ReliabilityRecord("Known", "Large",
        "All modes", 0.0, Double.MAX_VALUE, Double.MAX_VALUE, "Synthetic", "High");
    assertEquals(0.5, largeFinite.getAvailability());
  }
}
