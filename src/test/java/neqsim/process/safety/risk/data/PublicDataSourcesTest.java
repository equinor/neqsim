package neqsim.process.safety.risk.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for public domain reliability data sources.
 */
class PublicDataSourcesTest {

  @Test
  void testLoadIEEE493Data() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadIEEE493Data();

    assertTrue(importer.getRecordCount() > 50,
        "Should have at least 50 IEEE 493 records, got: " + importer.getRecordCount());

    // Verify specific IEEE 493 data - check for Transformer equipment type
    List<String> types = importer.getEquipmentTypes();
    assertTrue(types.contains("Transformer"), "Should have Transformer data");

    // Get any transformer record
    List<OREDADataImporter.ReliabilityRecord> transformerRecords =
        importer.getRecordsByType("Transformer");
    assertFalse(transformerRecords.isEmpty(), "Should have transformer records");
    assertTrue(transformerRecords.get(0).getDataSource().contains("IEEE493"),
        "Data source should be IEEE 493");

    // Check motor data
    assertTrue(types.contains("Motor"), "Should have Motor data");
  }

  @Test
  void testLoadIOGPData() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadIOGPData();

    assertTrue(importer.getRecordCount() > 50,
        "Should have at least 50 IOGP records, got: " + importer.getRecordCount());

    // Verify offshore equipment
    List<String> types = importer.getEquipmentTypes();
    assertTrue(types.contains("BOP"), "Should have BOP data");
    assertTrue(types.contains("Subsea Tree"), "Should have subsea tree data");

    // Check safety system data
    List<OREDADataImporter.ReliabilityRecord> esdRecords = importer.search("ESD");
    assertFalse(esdRecords.isEmpty(), "Should have ESD records");
  }

  @Test
  void testLoadGenericLiteratureData() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadGenericLiteratureData();

    assertTrue(importer.getRecordCount() > 100,
        "Should have at least 100 generic records, got: " + importer.getRecordCount());

    // Verify process equipment
    List<String> types = importer.getEquipmentTypes();
    assertTrue(types.contains("Vessel"), "Should have Vessel data");

    // Check valve data
    assertTrue(types.contains("Valve"), "Should have Valve data");
  }

  @Test
  void testLoadAllPublicDataSources() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadAllPublicDataSources();

    assertTrue(importer.getRecordCount() > 400,
        "Combined sources should have 400+ records, got: " + importer.getRecordCount());

    // Verify data from different sources is combined
    List<String> types = importer.getEquipmentTypes();
    assertTrue(types.size() > 30, "Should have 30+ equipment types, got: " + types.size());
  }

  @Test
  void testCreateWithAllPublicData() {
    OREDADataImporter importer = OREDADataImporter.createWithAllPublicData();

    assertTrue(importer.getRecordCount() > 400,
        "Factory method should load all public data, got: " + importer.getRecordCount());
  }

  @Test
  void testCreateForElectricalEquipment() {
    OREDADataImporter importer = OREDADataImporter.createForElectricalEquipment();

    assertTrue(importer.getRecordCount() > 50,
        "Electrical equipment importer should have 50+ records, got: " + importer.getRecordCount());

    // Should have transformer data
    assertTrue(importer.getEquipmentTypes().contains("Transformer"),
        "Should have transformer data");
  }

  @Test
  void testCreateForOilAndGas() {
    OREDADataImporter importer = OREDADataImporter.createForOilAndGas();

    assertTrue(importer.getRecordCount() > 150,
        "O&G importer should have 150+ records, got: " + importer.getRecordCount());

    // Should have subsea data
    assertTrue(importer.getEquipmentTypes().contains("Subsea Tree"),
        "Should have subsea tree data");
  }

  @Test
  void testConvenienceMethodsGetFailureRate() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromResource("/reliabilitydata/oreda_equipment.csv");

    double failureRate = importer.getFailureRate("Pump", "Centrifugal", "All modes");
    assertTrue(failureRate > 0, "Failure rate should be positive");
    assertTrue(failureRate < 1e-2, "Failure rate should be reasonable");
  }

  @Test
  void testConvenienceMethodsGetMTBF() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromResource("/reliabilitydata/oreda_equipment.csv");

    // Get a record that exists
    OREDADataImporter.ReliabilityRecord compressorRecord =
        importer.getRecord("Compressor", "Centrifugal", "All modes");
    assertNotNull(compressorRecord, "Should find compressor record");

    double mtbf = compressorRecord.getMtbfHours();
    assertTrue(mtbf > 1000, "MTBF should be > 1000 hours, got: " + mtbf);
    assertTrue(mtbf < 100000, "MTBF should be < 100000 hours, got: " + mtbf);
  }

  @Test
  void testConvenienceMethodsGetMTTR() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromResource("/reliabilitydata/oreda_equipment.csv");

    double mttr = importer.getMTTR("Separator", "Two-phase", "All modes");
    assertTrue(mttr > 0, "MTTR should be positive, got: " + mttr);
    assertTrue(mttr < 720, "MTTR should be < 720 hours for topside equipment, got: " + mttr);
  }

  @Test
  void testGetEquipmentClasses() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromResource("/reliabilitydata/oreda_equipment.csv");

    List<String> pumpClasses = importer.getEquipmentClasses("Pump");
    assertTrue(pumpClasses.size() >= 3,
        "Should have multiple pump classes, got: " + pumpClasses.size());
    assertTrue(pumpClasses.contains("Centrifugal"), "Should have centrifugal pumps");
  }

  @Test
  void testGetFailureModes() throws IOException {
    OREDADataImporter importer = new OREDADataImporter();
    importer.loadFromResource("/reliabilitydata/oreda_equipment.csv");

    List<String> valveModes = importer.getFailureModes("Valve", "Ball");
    assertTrue(valveModes.size() >= 1, "Should have failure modes for ball valves");
    assertTrue(valveModes.contains("All modes"), "Should have 'All modes' failure mode");
  }

  @Test
  void testClearAndReload() throws IOException {
    OREDADataImporter importer = OREDADataImporter.createWithAllPublicData();
    int initialCount = importer.getRecordCount();
    assertTrue(initialCount > 0, "Initial count should be positive");

    importer.clear();
    assertTrue(importer.getRecordCount() == 0, "Should be empty after clear");

    importer.loadIEEE493Data();
    assertTrue(importer.getRecordCount() > 0, "Should have records after reload");
    assertTrue(importer.getRecordCount() < initialCount,
        "Should have fewer records than combined sources");
  }
}
