package neqsim.process.equipment.compressor.seal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LabyrinthSealGeometry}.
 *
 * @author neqsim
 * @version 1.0
 */
public class LabyrinthSealGeometryTest {

  /**
   * Builds the reference geometry (dimensions in mm).
   *
   * @return reference geometry
   */
  private static LabyrinthSealGeometry referenceGeometry() {
    LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
    geometry.setShaftDiameter(108.566, "mm");
    geometry.setToothHeight(2.0, "mm");
    geometry.setToothPitch(2.0, "mm");
    geometry.setTipWidth(0.3, "mm");
    geometry.setRadialClearance(0.2286, "mm");
    geometry.setNumberOfTeeth(5);
    return geometry;
  }

  /** Clearance area is the annulus between shaft and tooth tips, pi*c*(D + c). */
  @Test
  void clearanceAreaIsAnnulusBetweenShaftAndToothTips() {
    LabyrinthSealGeometry geometry = referenceGeometry();
    double expected = Math.PI * 0.2286e-3 * (108.566e-3 + 0.2286e-3);
    assertEquals(expected, geometry.getClearanceArea(), 1.0e-15);
    assertEquals(7.8133e-5, geometry.getClearanceArea(), 1.0e-8);
  }

  /** Getters return SI values and the requested unit. */
  @Test
  void dimensionsAreStoredInSiAndReturnedInRequestedUnit() {
    LabyrinthSealGeometry geometry = referenceGeometry();
    assertEquals(0.108566, geometry.getShaftDiameter(), 1.0e-12);
    assertEquals(0.2286e-3, geometry.getRadialClearance(), 1.0e-15);
    assertEquals(0.2286, geometry.getRadialClearance("mm"), 1.0e-12);
    assertEquals(2.0, geometry.getToothPitch("mm"), 1.0e-12);
    assertEquals(0.3, geometry.getTipWidth("mm"), 1.0e-12);
    assertEquals(2.0, geometry.getToothHeight("mm"), 1.0e-12);
    assertEquals(5, geometry.getNumberOfTeeth());
  }

  /** The same physical clearance in inch and mm gives the same area. */
  @Test
  void inchAndMillimetreInputsAreEquivalent() {
    LabyrinthSealGeometry inches = referenceGeometry();
    inches.setRadialClearance(0.009, "inch");
    LabyrinthSealGeometry millimetres = referenceGeometry();
    millimetres.setRadialClearance(0.2286, "mm");
    assertEquals(millimetres.getClearanceArea(), inches.getClearanceArea(), 1.0e-15);
  }

  /** Non-physical dimensions are rejected immediately. */
  @Test
  void nonPositiveDimensionsAreRejected() {
    LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
    assertThrows(IllegalArgumentException.class, () -> geometry.setShaftDiameter(0.0, "mm"));
    assertThrows(IllegalArgumentException.class, () -> geometry.setRadialClearance(-0.1, "mm"));
    assertThrows(IllegalArgumentException.class, () -> geometry.setToothPitch(Double.NaN, "mm"));
    assertThrows(IllegalArgumentException.class, () -> geometry.setTipWidth(0.0, "mm"));
    assertThrows(IllegalArgumentException.class, () -> geometry.setToothHeight(0.0, "mm"));
    assertThrows(IllegalArgumentException.class, () -> geometry.setNumberOfTeeth(0));
  }

  /** An incomplete or inconsistent geometry fails validation with a remediation hint. */
  @Test
  void validateReportsMissingAndInconsistentDimensions() {
    LabyrinthSealGeometry incomplete = new LabyrinthSealGeometry();
    incomplete.setShaftDiameter(100.0, "mm");
    IllegalStateException missing = assertThrows(IllegalStateException.class, incomplete::validate);
    assertEquals(true, missing.getMessage().contains("radial clearance"));

    LabyrinthSealGeometry wideTip = referenceGeometry();
    wideTip.setTipWidth(2.5, "mm");
    IllegalStateException inconsistent = assertThrows(IllegalStateException.class, wideTip::validate);
    assertEquals(true, inconsistent.getMessage().contains("tip width"));
  }
}
