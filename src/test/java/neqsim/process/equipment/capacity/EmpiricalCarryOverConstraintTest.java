package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSource;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;

/**
 * Unit tests for {@link EmpiricalCarryOverConstraint}.
 *
 * <p>
 * The calibration used throughout is (gas rate [Am3/s], carry-over [kg/h]) = (2.0, 0.0), (3.0, 0.5), (4.5, 3.0), (5.5,
 * 12.0) with a maximum allowable carry-over of 5.0 kg/h. Expected values below are computed by hand from the
 * piecewise-linear definition so that a change in the interpolation logic is detected rather than accommodated.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EmpiricalCarryOverConstraintTest {

  /** Tolerance for exactly representable binary floating point results. */
  private static final double EXACT = 1.0e-12;

  /** Calibration operating-variable points [Am3/s]. */
  private static final double[] X_POINTS = { 2.0, 3.0, 4.5, 5.5 };

  /** Calibration carry-over observations [kg/h]. */
  private static final double[] Y_POINTS = { 0.0, 0.5, 3.0, 12.0 };

  /** Maximum allowable carry-over [kg/h]. */
  private static final double MAX_ALLOWABLE = 5.0;

  /** Mutable driver value written by each test and read through the supplier. */
  private final double[] driver = new double[1];

  /**
   * Builds a constraint whose driver reads the mutable {@link #driver} holder.
   *
   * @return a configured constraint
   */
  private EmpiricalCarryOverConstraint newConstraint() {
    DoubleSupplier supplier = new DoubleSupplier() {
      @Override
      public double getAsDouble() {
        return driver[0];
      }
    };
    return EmpiricalCarryOverConstraint.fromObservations("carryOver", "kg/h", supplier, X_POINTS, Y_POINTS,
        MAX_ALLOWABLE);
  }

  /** A driver at or below the first calibration point returns the first observation. */
  @Test
  public void belowCalibrationRangeClampsToFirstObservation() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    driver[0] = 1.0;
    assertEquals(0.0, constraint.getCurrentValue(), EXACT);

    driver[0] = 2.0;
    assertEquals(0.0, constraint.getCurrentValue(), EXACT);
  }

  /** The correlation reproduces the observations exactly at each calibration point. */
  @Test
  public void returnsObservedValueAtEachCalibrationPoint() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    for (int i = 0; i < X_POINTS.length; i++) {
      driver[0] = X_POINTS[i];
      assertEquals(Y_POINTS[i], constraint.getCurrentValue(), EXACT, "mismatch at calibration point x=" + X_POINTS[i]);
    }
  }

  /** Between two calibration points the correlation is linear. */
  @Test
  public void interpolatesLinearlyBetweenCalibrationPoints() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    // Midpoint of the segment (3.0, 0.5) - (4.5, 3.0): 0.5 + 0.5 * (3.0 - 0.5) = 1.75
    driver[0] = 3.75;
    assertEquals(1.75, constraint.getCurrentValue(), EXACT);

    // Quarter point of the segment (2.0, 0.0) - (3.0, 0.5): 0.0 + 0.25 * 0.5 = 0.125
    driver[0] = 2.25;
    assertEquals(0.125, constraint.getCurrentValue(), EXACT);
  }

  /** Above the calibration envelope the last segment slope is extrapolated. */
  @Test
  public void extrapolatesAboveCalibrationRangeUsingLastSegmentSlope() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    // Slope of last segment = (12.0 - 3.0) / (5.5 - 4.5) = 9.0 kg/h per Am3/s.
    // At 6.0: 12.0 + 9.0 * 0.5 = 16.5
    driver[0] = 6.0;
    assertEquals(16.5, constraint.getCurrentValue(), EXACT);
  }

  /** Utilization is the correlated carry-over divided by the maximum allowable. */
  @Test
  public void utilizationIsRelativeToMaximumAllowable() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    driver[0] = 3.75;
    assertEquals(1.75 / MAX_ALLOWABLE, constraint.getUtilization(), 1.0e-12);
    assertEquals(35.0, constraint.getUtilizationPercent(), 1.0e-9);
    assertFalse(constraint.isViolated(), "1.75 kg/h is below the 5.0 kg/h limit");
    assertFalse(constraint.isHardLimitExceeded());
  }

  /**
   * Exceeding the allowable carry-over registers both as a violation and as a hard-limit exceedance, so the constraint
   * participates in the plant-wide feasibility gate.
   */
  @Test
  public void exceedingAllowableCarryOverTripsHardLimit() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    driver[0] = 6.0;
    assertTrue(constraint.isViolated(), "16.5 kg/h exceeds the 5.0 kg/h limit");
    assertTrue(constraint.isHardLimitExceeded(), "an empirical carry-over limit must be a HARD constraint to bind");
  }

  /** The constraint declares empirical provenance and hard severity. */
  @Test
  public void declaresEmpiricalProvenanceAndHardSeverity() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    assertEquals(ConstraintSource.PROCESS_EMPIRICAL, constraint.getSource());
    assertEquals(ConstraintSeverity.HARD, constraint.getSeverity());
    assertEquals(ConstraintType.HARD, constraint.getType());
    assertEquals("kg/h", constraint.getUnit());
    assertEquals("carryOver", constraint.getName());
  }

  /** The source reference can be attached without disturbing the declared source. */
  @Test
  public void sourceReferenceIsRecorded() {
    EmpiricalCarryOverConstraint constraint = newConstraint();

    assertEquals("", constraint.getSourceReference());
    assertSame(constraint, constraint.setSourceReference("Suction drum LT-2103, May-Aug 2025"));
    assertEquals("Suction drum LT-2103, May-Aug 2025", constraint.getSourceReference());
    assertEquals(ConstraintSource.PROCESS_EMPIRICAL, constraint.getSource());

    constraint.setSourceReference(null);
    assertEquals("", constraint.getSourceReference());
  }

  /** Calibration arrays are defensively copied on both input and output. */
  @Test
  public void calibrationArraysAreDefensivelyCopied() {
    double[] mutableX = { 2.0, 3.0, 4.5, 5.5 };
    double[] mutableY = { 0.0, 0.5, 3.0, 12.0 };
    EmpiricalCarryOverConstraint constraint = new EmpiricalCarryOverConstraint("carryOver", "kg/h",
        new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return driver[0];
          }
        }, mutableX, mutableY, MAX_ALLOWABLE);

    // Mutating the caller's arrays must not change the fitted correlation.
    mutableY[3] = 999.0;
    driver[0] = 5.5;
    assertEquals(12.0, constraint.getCurrentValue(), EXACT);

    // Mutating the returned arrays must not change it either.
    double[] returned = constraint.getCalibrationY();
    returned[3] = -1.0;
    assertEquals(12.0, constraint.getCalibrationY()[3], EXACT);
    assertEquals(2.0, constraint.getCalibrationX()[0], EXACT);
  }

  /** Malformed calibration data is rejected rather than silently producing a wrong correlation. */
  @Test
  public void rejectsInvalidCalibrationData() {
    DoubleSupplier supplier = new DoubleSupplier() {
      @Override
      public double getAsDouble() {
        return 0.0;
      }
    };

    assertThrows(IllegalArgumentException.class,
        () -> EmpiricalCarryOverConstraint.fromObservations("co", "kg/h", supplier, null, Y_POINTS, MAX_ALLOWABLE));

    assertThrows(IllegalArgumentException.class,
        () -> EmpiricalCarryOverConstraint.fromObservations("co", "kg/h", supplier, X_POINTS, null, MAX_ALLOWABLE));

    assertThrows(IllegalArgumentException.class, () -> EmpiricalCarryOverConstraint.fromObservations("co", "kg/h",
        supplier, new double[0], new double[0], MAX_ALLOWABLE));

    assertThrows(IllegalArgumentException.class, () -> EmpiricalCarryOverConstraint.fromObservations("co", "kg/h",
        supplier, new double[] { 2.0, 3.0 }, new double[] { 0.0, 0.5, 3.0 }, MAX_ALLOWABLE));

    // Not strictly ascending.
    assertThrows(IllegalArgumentException.class, () -> EmpiricalCarryOverConstraint.fromObservations("co", "kg/h",
        supplier, new double[] { 2.0, 3.0, 3.0 }, new double[] { 0.0, 0.5, 3.0 }, MAX_ALLOWABLE));

    assertThrows(IllegalArgumentException.class, () -> EmpiricalCarryOverConstraint.fromObservations("co", "kg/h",
        supplier, new double[] { 4.0, 3.0 }, new double[] { 0.0, 0.5 }, MAX_ALLOWABLE));
  }

  /** A single calibration point degenerates to a constant rather than dividing by zero. */
  @Test
  public void singleCalibrationPointBehavesAsConstant() {
    EmpiricalCarryOverConstraint constraint = EmpiricalCarryOverConstraint.fromObservations("co", "kg/h",
        new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return driver[0];
          }
        }, new double[] { 3.0 }, new double[] { 1.25 }, MAX_ALLOWABLE);

    driver[0] = 1.0;
    assertEquals(1.25, constraint.getCurrentValue(), EXACT);

    driver[0] = 3.0;
    assertEquals(1.25, constraint.getCurrentValue(), EXACT);

    driver[0] = 50.0;
    assertEquals(1.25, constraint.getCurrentValue(), EXACT);
  }

  /** The constraint registers on a separator like any other capacity constraint. */
  @Test
  public void registersOnSeparatorAsCapacityConstraint() {
    Separator separator = new Separator("Kollsnes scrubber");
    EmpiricalCarryOverConstraint constraint = newConstraint();
    separator.addCapacityConstraint(constraint);

    assertTrue(separator.getCapacityConstraints().containsKey("carryOver"));
    assertSame(constraint, separator.getCapacityConstraints().get("carryOver"));
  }
}
