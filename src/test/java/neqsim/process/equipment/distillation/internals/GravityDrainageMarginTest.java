package neqsim.process.equipment.distillation.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link GravityDrainageMargin}. */
public class GravityDrainageMarginTest {
  /** Lean TEG density at regeneration temperature [kg/m3]. */
  private static final double TEG_DENSITY = 962.0;

  /** Head pressure must equal rho g h and drainage must be reported as possible. */
  @Test
  void headPressureAndDrainageAtLowPressureDrop() {
    GravityDrainageMargin margin = new GravityDrainageMargin(TEG_DENSITY, 1.5, 9000.0);
    double expected = TEG_DENSITY * GravityDrainageMargin.STANDARD_GRAVITY * 1.5;
    assertEquals(expected, margin.getAvailableHeadPressure(), 1.0e-9);
    assertEquals(expected - 9000.0, margin.getMarginPressure(), 1.0e-9);
    assertEquals(expected / 9000.0, margin.getMarginRatio(), 1.0e-9);
    assertEquals(9000.0 / expected, margin.getHeadUtilisation(), 1.0e-9);
    assertTrue(margin.canDrain());
    assertEquals(expected, margin.getMaximumAllowableGasPressureDrop(), 1.0e-9);
  }

  /** A gas-side pressure drop above the head must block drainage. */
  @Test
  void drainageFailsWhenPressureDropExceedsHead() {
    GravityDrainageMargin margin = new GravityDrainageMargin(TEG_DENSITY, 1.5, 29000.0);
    assertFalse(margin.canDrain());
    assertTrue(margin.getMarginPressure() < 0.0);
    assertTrue(margin.getMarginRatio() < 1.0);
    assertTrue(margin.getHeadUtilisation() > 1.0);
    assertTrue(margin.getRequiredStaticHead() > margin.getAvailableStaticHead());
    assertTrue(margin.toString().contains("canDrain=false"));
  }

  /** The static helpers must be exact inverses of one another. */
  @Test
  void staticHelpersAreInverses() {
    double head = 1.25;
    double dp = GravityDrainageMargin.criticalPressureDrop(TEG_DENSITY, head);
    assertEquals(head, GravityDrainageMargin.criticalStaticHead(TEG_DENSITY, dp), 1.0e-12);
    assertEquals(0.0, GravityDrainageMargin.criticalPressureDrop(TEG_DENSITY, 0.0), 1.0e-12);
  }

  /** With no gas-side pressure drop the margin ratio must be infinite, not NaN. */
  @Test
  void zeroPressureDropGivesInfiniteMargin() {
    GravityDrainageMargin margin = new GravityDrainageMargin(TEG_DENSITY, 1.5, 0.0);
    assertTrue(Double.isInfinite(margin.getMarginRatio()));
    assertTrue(margin.canDrain());
    assertEquals(0.0, margin.getRequiredStaticHead(), 1.0e-12);
  }

  /** Invalid arguments must be rejected. */
  @Test
  void invalidArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new GravityDrainageMargin(0.0, 1.0, 100.0));
    assertThrows(IllegalArgumentException.class, () -> new GravityDrainageMargin(TEG_DENSITY, -1.0, 100.0));
    assertThrows(IllegalArgumentException.class, () -> new GravityDrainageMargin(TEG_DENSITY, 1.0, -1.0));
    assertThrows(IllegalArgumentException.class, () -> GravityDrainageMargin.criticalPressureDrop(-1.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> GravityDrainageMargin.criticalStaticHead(TEG_DENSITY, Double.NaN));
  }
}
