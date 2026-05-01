package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    assertTrue(all.size() >= 60,
        "Should have at least 60 internals records, got " + all.size());
  }

  /**
   * Tests that inlet device records are loaded.
   */
  @Test
  void testInletDeviceRecordsLoaded() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InletDeviceRecord> all = db.getAllInletDevices();
    assertFalse(all.isEmpty(), "Should have inlet device records");
    assertTrue(all.size() >= 25,
        "Should have at least 25 inlet device records, got " + all.size());
  }

  /**
   * Tests that vendor curve records are loaded.
   */
  @Test
  void testVendorCurveRecordsLoaded() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.VendorCurveRecord> all = db.getAllVendorCurves();
    assertFalse(all.isEmpty(), "Should have vendor curve records");
    assertTrue(all.size() >= 20,
        "Should have at least 20 vendor curve records, got " + all.size());
  }

  /**
   * Tests finding internals by type (WIRE_MESH).
   */
  @Test
  void testFindByTypeWireMesh() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.InternalsRecord> wireMesh = db.findByType("WIRE_MESH");
    assertFalse(wireMesh.isEmpty(), "Should find WIRE_MESH records");
    assertTrue(wireMesh.size() >= 15,
        "Should have at least 15 wire mesh variants, got " + wireMesh.size());
    for (SeparatorInternalsDatabase.InternalsRecord rec : wireMesh) {
      assertTrue(rec.internalsType.equals("WIRE_MESH"), "Record type should be WIRE_MESH");
      assertTrue(rec.maxKFactor > 0, "Max K-factor should be positive");
      assertTrue(rec.d50_um > 0, "D50 should be positive");
    }
  }

  /**
   * Tests that all 5 internals types are present in the database.
   */
  @Test
  void testAllInternalsTypesPresent() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    Set<String> types = new HashSet<String>();
    for (SeparatorInternalsDatabase.InternalsRecord rec : db.getAllInternals()) {
      types.add(rec.internalsType);
    }
    assertTrue(types.contains("WIRE_MESH"), "Should have WIRE_MESH");
    assertTrue(types.contains("VANE_PACK"), "Should have VANE_PACK");
    assertTrue(types.contains("AXIAL_CYCLONE"), "Should have AXIAL_CYCLONE");
    assertTrue(types.contains("PLATE_PACK"), "Should have PLATE_PACK");
    assertTrue(types.contains("GRAVITY"), "Should have GRAVITY");
  }

  /**
   * Tests that all expected inlet device types are present.
   */
  @Test
  void testAllInletDeviceTypesPresent() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    Set<String> types = new HashSet<String>();
    for (SeparatorInternalsDatabase.InletDeviceRecord rec : db.getAllInletDevices()) {
      types.add(rec.deviceType);
    }
    assertTrue(types.contains("NONE"), "Should have NONE");
    assertTrue(types.contains("DEFLECTOR_PLATE"), "Should have DEFLECTOR_PLATE");
    assertTrue(types.contains("HALF_PIPE"), "Should have HALF_PIPE");
    assertTrue(types.contains("INLET_VANE"), "Should have INLET_VANE");
    assertTrue(types.contains("INLET_CYCLONE"), "Should have INLET_CYCLONE");
    assertTrue(types.contains("SCHOEPENTOETER"), "Should have SCHOEPENTOETER");
    assertTrue(types.contains("IMPINGEMENT_PLATE"), "Should have IMPINGEMENT_PLATE");
    assertTrue(types.contains("ELBOW_INLET"), "Should have ELBOW_INLET");
    assertTrue(types.contains("DISTRIBUTOR"), "Should have DISTRIBUTOR");
  }

  /**
   * Tests finding internals by type and subtype.
   */
  @Test
  void testFindByTypeAndSubType() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    SeparatorInternalsDatabase.InternalsRecord rec =
        db.findByTypeAndSubType("WIRE_MESH", "Standard Knitted");
    assertNotNull(rec, "Should find Standard Knitted wire mesh");
    assertTrue(rec.d50_um > 0 && rec.d50_um < 50, "D50 should be in reasonable range");
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
    assertTrue(vanes.size() >= 4,
        "Should have at least 4 inlet vane variants, got " + vanes.size());
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
   * Tests that a VendorCurveRecord produces a valid custom GradeEfficiencyCurve.
   */
  @Test
  void testVendorCurveRecordToGradeEfficiencyCurve() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.VendorCurveRecord> curves = db.getAllVendorCurves();
    assertFalse(curves.isEmpty(), "Should have vendor curves");

    SeparatorInternalsDatabase.VendorCurveRecord rec = curves.get(0);
    GradeEfficiencyCurve curve = rec.toGradeEfficiencyCurve();
    assertNotNull(curve, "Vendor grade efficiency curve should not be null");
    assertTrue(curve.getType() == GradeEfficiencyCurve.InternalsType.CUSTOM,
        "Vendor curve should produce CUSTOM type");

    // Check monotonically increasing efficiency
    double prevEff = 0.0;
    for (double d_um : rec.diameterPoints_um) {
      double eff = curve.getEfficiency(d_um * 1e-6);
      assertTrue(eff >= prevEff - 0.001,
          "Vendor curve efficiency should be non-decreasing at " + d_um + " um");
      prevEff = eff;
    }
    // Large droplets should have high efficiency
    double effLarge = curve.getEfficiency(100e-6);
    assertTrue(effLarge > 0.9, "Efficiency at 100 um should be > 0.9, got " + effLarge);
  }

  /**
   * Tests finding vendor curves by internals type.
   */
  @Test
  void testFindVendorCurvesByType() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.VendorCurveRecord> wireMeshCurves =
        db.findVendorCurvesByType("WIRE_MESH");
    assertFalse(wireMeshCurves.isEmpty(), "Should have WIRE_MESH vendor curves");
    assertTrue(wireMeshCurves.size() >= 5,
        "Should have at least 5 wire mesh vendor curves, got " + wireMeshCurves.size());

    List<SeparatorInternalsDatabase.VendorCurveRecord> cycloneCurves =
        db.findVendorCurvesByType("AXIAL_CYCLONE");
    assertFalse(cycloneCurves.isEmpty(), "Should have AXIAL_CYCLONE vendor curves");
  }

  /**
   * Tests finding vendor curves by vendor name.
   */
  @Test
  void testFindVendorCurvesByVendor() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.VendorCurveRecord> vendorA =
        db.findVendorCurvesByVendor("VendorA");
    assertFalse(vendorA.isEmpty(), "Should find VendorA curves");

    List<SeparatorInternalsDatabase.VendorCurveRecord> vendorB =
        db.findVendorCurvesByVendor("VendorB");
    assertFalse(vendorB.isEmpty(), "Should find VendorB curves");
  }

  /**
   * Tests finding a vendor curve by ID.
   */
  @Test
  void testFindVendorCurveById() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    SeparatorInternalsDatabase.VendorCurveRecord rec = db.findVendorCurveById("VC001");
    assertNotNull(rec, "Should find VC001");
    assertTrue(rec.internalsType.equals("WIRE_MESH"), "VC001 should be WIRE_MESH");
    assertNotNull(rec.diameterPoints_um, "Should have diameter points");
    assertNotNull(rec.efficiencyPoints, "Should have efficiency points");
    assertTrue(rec.diameterPoints_um.length == rec.efficiencyPoints.length,
        "Diameter and efficiency arrays should have same length");
    assertTrue(rec.diameterPoints_um.length >= 10,
        "Should have at least 10 data points, got " + rec.diameterPoints_um.length);
  }

  /**
   * Tests finding vendor curves by type and vendor.
   */
  @Test
  void testFindVendorCurvesByTypeAndVendor() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    List<SeparatorInternalsDatabase.VendorCurveRecord> results =
        db.findVendorCurvesByTypeAndVendor("AXIAL_CYCLONE", "VendorA");
    assertFalse(results.isEmpty(), "Should find VendorA cyclone curves");
    for (SeparatorInternalsDatabase.VendorCurveRecord rec : results) {
      assertTrue(rec.internalsType.equals("AXIAL_CYCLONE"), "Type should be AXIAL_CYCLONE");
      assertTrue(rec.vendorName.equals("VendorA"), "Vendor should be VendorA");
    }
  }

  /**
   * Tests that vendor curves have valid test metadata.
   */
  @Test
  void testVendorCurveMetadata() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    for (SeparatorInternalsDatabase.VendorCurveRecord rec : db.getAllVendorCurves()) {
      assertNotNull(rec.curveId, "Curve ID should not be null");
      assertFalse(rec.curveId.trim().isEmpty(), "Curve ID should not be empty");
      assertNotNull(rec.vendorName, "Vendor name should not be null");
      assertFalse(rec.vendorName.trim().isEmpty(), "Vendor name should not be empty for " + rec.curveId);
      assertNotNull(rec.testStandard, "Test standard should not be null for " + rec.curveId);
      assertTrue(rec.maxKFactor > 0, "Max K-factor should be positive for " + rec.curveId);
      assertTrue(rec.diameterPoints_um.length >= 2,
          "Should have at least 2 data points for " + rec.curveId);
    }
  }

  /**
   * Tests that high-pressure vendor curves have lower max K-factor than atmospheric curves.
   * At higher gas density the flooding velocity is lower, reducing the allowable K-factor.
   */
  @Test
  void testHighPressureVendorCurvesShifted() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    // Compare atmospheric vs HP wire mesh from same vendor families
    SeparatorInternalsDatabase.VendorCurveRecord atm = db.findVendorCurveById("VC001");
    SeparatorInternalsDatabase.VendorCurveRecord hp = db.findVendorCurveById("VC007");
    assertNotNull(atm, "Should find atmospheric curve VC001");
    assertNotNull(hp, "Should find HP curve VC007");
    assertTrue(hp.testPressure_bar > atm.testPressure_bar,
        "HP curve should have higher test pressure");
    // At high pressure the max K-factor is lower (flooding earlier)
    assertTrue(hp.maxKFactor < atm.maxKFactor,
        "HP max K-factor should be lower: atm=" + atm.maxKFactor + " hp=" + hp.maxKFactor);
    // Both should produce valid custom curves
    GradeEfficiencyCurve atmCurve = atm.toGradeEfficiencyCurve();
    GradeEfficiencyCurve hpCurve = hp.toGradeEfficiencyCurve();
    assertNotNull(atmCurve, "Atmospheric curve should not be null");
    assertNotNull(hpCurve, "HP curve should not be null");
  }

  /**
   * Tests catalog JSON generation includes all three sections.
   */
  @Test
  void testToCatalogJson() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    String json = db.toCatalogJson();
    assertNotNull(json, "Catalog JSON should not be null");
    assertTrue(json.length() > 1000, "Catalog JSON should have substantial content");
    assertTrue(json.contains("internals"), "JSON should contain 'internals' key");
    assertTrue(json.contains("inletDevices"), "JSON should contain 'inletDevices' key");
    assertTrue(json.contains("vendorCurves"), "JSON should contain 'vendorCurves' key");
    assertTrue(json.contains("certificateRef"), "Vendor curves should include certificateRef");
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

  /**
   * Tests total record count across all three tables meets 100+ target.
   */
  @Test
  void testTotalRecordCountExceeds100() {
    SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
    int total = db.getAllInternals().size() + db.getAllInletDevices().size()
        + db.getAllVendorCurves().size();
    assertTrue(total >= 100,
        "Total records across all tables should be >= 100, got " + total);
  }
}
