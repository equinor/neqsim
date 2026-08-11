package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests the configurable bubbly-flow diameter closure. */
class BubbleSizeClosureTest {

  @Test
  void reproducesHistoricalDefaultAssumptions() {
    BubbleSizeClosure closure = new BubbleSizeClosure();
    double diameter = closure.estimateDiameter(0.10, 1000.0, 5.0, 9.81);

    double expected = Math.min(Math.sqrt(0.02 / (9.81 * 995.0)), 0.02);
    assertEquals(0.02, closure.getSurfaceTension(), 0.0);
    assertEquals(0.20, closure.getMaximumPipeDiameterFraction(), 0.0);
    assertEquals(expected, diameter, 1.0e-15);
  }

  @Test
  void respondsMonotonicallyToSurfaceTensionBeforeGeometryCap() {
    BubbleSizeClosure low = new BubbleSizeClosure(0.01);
    BubbleSizeClosure high = new BubbleSizeClosure(0.04);

    double lowDiameter = low.estimateDiameter(1.0, 900.0, 20.0, 9.81);
    double highDiameter = high.estimateDiameter(1.0, 900.0, 20.0, 9.81);

    assertTrue(highDiameter > lowDiameter);
    assertEquals(2.0, highDiameter / lowDiameter, 1.0e-12);
  }

  @Test
  void appliesExplicitPipeDiameterCapAndDensitySymmetry() {
    BubbleSizeClosure closure = new BubbleSizeClosure(0.5);
    closure.setMaximumPipeDiameterFraction(0.10);

    assertEquals(0.005, closure.estimateDiameter(0.05, 5.0, 1000.0, 9.81), 1.0e-15);
    assertEquals(0.005, closure.estimateDiameter(0.05, 1000.0, 5.0, 9.81), 1.0e-15);
  }

  @Test
  void equalPhaseDensitiesFallBackToGeometryBound() {
    BubbleSizeClosure closure = new BubbleSizeClosure();
    closure.setMaximumPipeDiameterFraction(0.15);

    assertEquals(0.03, closure.estimateDiameter(0.20, 500.0, 500.0, 9.81), 1.0e-15);
  }

  @Test
  void rejectsNonPhysicalInputs() {
    BubbleSizeClosure closure = new BubbleSizeClosure();

    assertThrows(IllegalArgumentException.class, () -> closure.setSurfaceTension(0.0));
    assertThrows(IllegalArgumentException.class, () -> closure.setMaximumPipeDiameterFraction(1.1));
    assertThrows(IllegalArgumentException.class, () -> closure.estimateDiameter(0.0, 1000.0, 5.0, 9.81));
    assertThrows(IllegalArgumentException.class,
        () -> closure.estimateDiameter(0.1, Double.NaN, 5.0, 9.81));
  }
}
