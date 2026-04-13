package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SeparatorInternalsDatabase}.
 *
 * @author NeqSim team
 * @version 1.0
 */
class SeparatorInternalsDatabaseTest {

  /**
   * Tests singleton instance retrieval.
   */
  @Test
  void testGetInstance() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    assertNotNull(db, "Database instance should not be null");
    // Same instance
    SeparatorInternalsDatabase db2 = SeparatorInternalsDatabase.getInstance();
    assertTrue(db == db2, "Should return same singleton instance");
  }

  /**
   * Tests that internals records are loaded (either from CSV or defaults).
   */
  @Test
  void testInternalsRecordsLoaded() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InternalsRecord> all = db.getAllInternals();
    assertFalse(all.isEmpty(), "Should have internals records");
    assertTrue(all.size() >= 5, "Should have at least 5 internals records");
  }

  /**
   * Tests that inlet device records are loaded.
   */
  @Test
  void testInletDeviceRecordsLoaded() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InletDeviceRecord> all = db.getAllInletDevices();
    assertFalse(all.isEmpty(), "Should have inlet device records");
    assertTrue(all.size() >= 5, "Should have at least 5 inlet device records");
  }

  /**
   * Tests finding internals by type (WIRE_MESH).
   */
  @Test
  void testFindByTypeWireMesh() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InternalsRecord> wireMesh = db.findByType("WIRE_MESH");
    assertFalse(wireMesh.isEmpty(), "Should find WIRE_MESH records");
    for (SeparatorInternalsDatabase.InternalsRecord rec : wireMesh) {
      assertTrue(rec.internalsType.equals("WIRE_MESH"), "Record type should be WIRE_MESH");
      assertTrue(rec.maxKFactor > 0, "Max K-factor should be positive");
      assertTrue(rec.d50_um > 0, "D50 should be positive");
    }
  }

  /**
   * Tests finding internals by type and subtype.
   */
  @Test
  void testFindByTypeAndSubType() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    SeparatorInternalsDatabase.InternalsRecord rec =
        db.findByTypeAndSubType("WIRE_MESH", "Standard");
    // May be null if that exact subtype isn't in the database, but should not throw
    if (rec != null) {
      assertTrue(rec.internalsType.equals("WIRE_MESH"), "Type should match");
    }
  }

  /**
   * Tests finding inlet device by type.
   */
  @Test
  void testFindInletDeviceByType() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InletDeviceRecord> vanes =
        db.findInletDeviceByType("INLET_VANE");
    assertFalse(vanes.isEmpty(), "Should find INLET_VANE records");
    for (SeparatorInternalsDatabase.InletDeviceRecord rec : vanes) {
      assertTrue(rec.deviceType.equals("INLET_VANE"), "Device type should be INLET_VANE");
      assertTrue(rec.maxMomentum_Pa > 0, "Max momentum should be positive");
    }
  }

  /**
   * Tests that InternalsRecord produces a valid GradeEfficiencyCurve.
   */
  @Test
  void testInternalsRecordToGradeEfficiencyCurve() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InternalsRecord> records = db.findByType("WIRE_MESH");
    if (!records.isEmpty()) {
      GradeEfficiencyCurve curve = records.get(0).toGradeEfficiencyCurve();
      assertNotNull(curve, "Grade efficiency curve should not be null");
      // Check that the curve produces reasonable efficiencies
      double effSmall = curve.getEfficiency(1e-6); // 1 micron
      double effLarge = curve.getEfficiency(100e-6); // 100 micron
      assertTrue(effSmall <= effLarge,
          "Efficiency for larger droplets should be >= small droplets");
    }
  }

  /**
   * Tests catalog JSON generation.
   */
  @Test
  void testToCatalogJson() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    String json = db.toCatalogJson();
    assertNotNull(json, "Catalog JSON should not be null");
    assertTrue(json.length() > 100, "Catalog JSON should have substantial content");
    assertTrue(json.contains("internals"), "JSON should contain 'internals' key");
    assertTrue(json.contains("inletDevices"), "JSON should contain 'inletDevices' key");
  }

  /**
   * Tests that records have valid physical data.
   */
  @Test
  void testRecordDataIntegrity() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    for (SeparatorInternalsDatabase.InternalsRecord rec : db.getAllInternals()) {
      assertTrue(rec.d50_um > 0 && rec.d50_um < 10000,
          "D50 should be between 0 and 10000 um for " + rec.internalsType + "/" + rec.subType);
      assertTrue(rec.maxEfficiency > 0 && rec.maxEfficiency <= 1.0,
          "Max efficiency should be (0,1] for " + rec.internalsType);
      assertTrue(rec.maxKFactor >= rec.minKFactor, "Max K >= Min K for " + rec.internalsType);
    }
  }
}
