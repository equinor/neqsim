package neqsim.process.safety.risk.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for OREDADataImporter.
 */
class OREDADataImporterTest {

  private OREDADataImporter importer;

  @BeforeEach
  void setUp() {
    importer = OREDADataImporter.createWithDefaults();
  }

  @Test
  void testCreateWithDefaults() {
    assertNotNull(importer);
    assertTrue(importer.getRecordCount() > 0);
    assertEquals("OREDA-2015 (Built-in defaults)", importer.getDataSource());
  }

  @Test
  void testGetCompressorData() {
    OREDADataImporter.ReliabilityRecord record =
        importer.getRecord("Compressor", "Centrifugal", "All modes");
    assertNotNull(record);
    assertEquals("Compressor", record.getEquipmentType());
    assertEquals("Centrifugal", record.getEquipmentClass());
    assertEquals(1.14e-4, record.getFailureRate(), 1e-6);
    assertEquals(8772, record.getMtbfHours(), 1);
    assertEquals(72, record.getMttrHours(), 1);
    assertEquals("High", record.getConfidence());
  }

  @Test
  void testGetPumpData() {
    OREDADataImporter.ReliabilityRecord record = importer.getRecord("Pump", "Centrifugal");
    assertNotNull(record);
    assertEquals("Pump", record.getEquipmentType());
    assertTrue(record.getMtbfHours() > 0);
  }

  @Test
  void testGetSeparatorData() {
    OREDADataImporter.ReliabilityRecord record = importer.getRecord("Separator", "Two-phase");
    assertNotNull(record);
    assertEquals(5.71e-5, record.getFailureRate(), 1e-7);
  }

  @Test
  void testGetRecordsByType() {
    List<OREDADataImporter.ReliabilityRecord> compressors = importer.getRecordsByType("Compressor");
    assertFalse(compressors.isEmpty());
    assertTrue(compressors.size() >= 3);
    for (OREDADataImporter.ReliabilityRecord r : compressors) {
      assertEquals("Compressor", r.getEquipmentType());
    }
  }

  @Test
  void testGetEquipmentTypes() {
    List<String> types = importer.getEquipmentTypes();
    assertFalse(types.isEmpty());
    assertTrue(types.contains("Compressor"));
    assertTrue(types.contains("Pump"));
    assertTrue(types.contains("Separator"));
    assertTrue(types.contains("Valve"));
  }

  @Test
  void testAvailabilityCalculation() {
    OREDADataImporter.ReliabilityRecord record = importer.getRecord("Compressor", "Centrifugal");
    double availability = record.getAvailability();
    assertTrue(availability > 0.99); // Compressors typically >99%
    assertTrue(availability < 1.0);
  }

  @Test
  void testFailureRatePerYear() {
    OREDADataImporter.ReliabilityRecord record = importer.getRecord("Compressor", "Centrifugal");
    double perYear = record.getFailureRatePerYear();
    assertEquals(record.getFailureRate() * 8760, perYear, 1e-6);
  }

  @Test
  void testSearch() {
    List<OREDADataImporter.ReliabilityRecord> results = importer.search("centrifugal");
    assertFalse(results.isEmpty());
    for (OREDADataImporter.ReliabilityRecord r : results) {
      assertTrue(r.getEquipmentType().toLowerCase().contains("centrifugal")
          || r.getEquipmentClass().toLowerCase().contains("centrifugal"));
    }
  }

  @Test
  void testSubseaEquipment() {
    OREDADataImporter.ReliabilityRecord tree = importer.getRecord("Subsea Tree", "Vertical");
    assertNotNull(tree);
    assertTrue(tree.getMttrHours() > 100); // Subsea repairs take longer
  }

  @Test
  void testToMap() {
    OREDADataImporter.ReliabilityRecord record = importer.getRecord("Valve", "Control");
    assertNotNull(record);
    var map = record.toMap();
    assertEquals("Valve", map.get("equipmentType"));
    assertEquals("Control", map.get("equipmentClass"));
    assertTrue((Double) map.get("availability") > 0.99);
  }

  @Test
  void testAddCustomRecord() {
    OREDADataImporter custom = new OREDADataImporter();
    custom.addRecord(new OREDADataImporter.ReliabilityRecord("CustomEquipment", "TypeA",
        "All modes", 1e-5, 100000, 24, "Internal", "High"));

    OREDADataImporter.ReliabilityRecord retrieved = custom.getRecord("CustomEquipment", "TypeA");
    assertNotNull(retrieved);
    assertEquals(1e-5, retrieved.getFailureRate(), 1e-8);
  }

  @Test
  void testLoadFromResource() throws Exception {
    OREDADataImporter resourceImporter = new OREDADataImporter();
    resourceImporter.loadFromResource("/reliabilitydata/oreda_equipment.csv");
    assertTrue(resourceImporter.getRecordCount() > 50);
  }
}
