package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for minimum-value capacity constraints.
 *
 * @author NeqSim
 * @version 1.0
 */
public class CapacityConstraintMinimumLimitTest {

  /** Verifies utilization and hard-limit behavior on both sides of a minimum. */
  @Test
  public void testHardMinimumConstraint() {
    CapacityConstraint margin = new CapacityConstraint("surgeMargin", "%", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(Double.MAX_VALUE).setMinValue(10.0).setCurrentValue(15.0);

    assertTrue(margin.isMinimumConstraint());
    assertEquals(10.0 / 15.0, margin.getUtilization(), 1.0e-12);
    assertFalse(margin.isViolated());
    assertFalse(margin.isHardLimitExceeded());

    margin.setCurrentValue(5.0);

    assertEquals(2.0, margin.getUtilization(), 1.0e-12);
    assertTrue(margin.isViolated());
    assertTrue(margin.isHardLimitExceeded());
  }

  /** Verifies that equality with the minimum remains feasible. */
  @Test
  public void testMinimumBoundaryIsFeasible() {
    CapacityConstraint margin = new CapacityConstraint("stonewallMargin", "%", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(Double.MAX_VALUE).setMinValue(5.0).setCurrentValue(5.0);

    assertEquals(1.0, margin.getUtilization(), 0.0);
    assertFalse(margin.isViolated());
    assertFalse(margin.isHardLimitExceeded());
  }
}
