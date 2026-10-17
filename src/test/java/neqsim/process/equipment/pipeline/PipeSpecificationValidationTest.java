package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for unavailable pipe specification cells and atomic geometry updates.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipeSpecificationValidationTest extends neqsim.NeqSimTest {
  /** A populated cell must set the wall and inside diameter in meters. */
  @Test
  void availableSpecificationSetsConsistentGeometry() {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe");
    pipe.setPipeSpecification(4.0, "LD201");
    assertEquals(0.01712, pipe.getThickness(), 1e-12);
    assertEquals(0.1143 - 2.0 * 0.01712, pipe.getDiameter(), 1e-12);
  }

  /** Blank cells must describe the unavailable selection and preserve prior geometry. */
  @Test
  void unavailableSpecificationsFailWithContextAndLeaveGeometryUnchanged() {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe");
    pipe.setPipeSpecification(4.0, "LD201");
    double originalDiameter = pipe.getDiameter();
    double originalWall = pipe.getThickness();
    for (double size : new double[] {5.0, 18.0, 40.0}) {
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
          () -> pipe.setPipeSpecification(size, "LD201"));
      assertTrue(error.getMessage().contains("LD201"));
      assertTrue(error.getMessage().contains(Double.toString(size)));
      assertEquals(originalDiameter, pipe.getDiameter());
      assertEquals(originalWall, pipe.getThickness());
    }
    assertThrows(IllegalArgumentException.class, () -> pipe.setPipeSpecification(4.0, "TS31A"));
  }

  /** A typo, unsupported size, or absent selection must fail explicitly. */
  @Test
  void invalidSelectionIsRejectedBeforeChangingGeometry() {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe");
    IllegalArgumentException typo = assertThrows(IllegalArgumentException.class,
        () -> pipe.setPipeSpecification(4.0, "MISSPELT"));
    assertTrue(typo.getMessage().contains("Unknown pipe specification"));
    assertThrows(IllegalArgumentException.class, () -> pipe.setPipeSpecification(1000.0, "LD201"));
    assertThrows(IllegalArgumentException.class, () -> pipe.setPipeSpecification(Double.NaN, "LD201"));
    assertThrows(IllegalArgumentException.class, () -> pipe.setPipeSpecification(4.0, null));
  }
}
