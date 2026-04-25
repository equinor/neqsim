package neqsim.process.equipment.capacity;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.DoubleSupplier;

/**
 * Empirical, calibrated carry-over constraint for a separator / scrubber.
 *
 * <p>
 * This constraint type addresses the situation where the binding limit on a piece of
 * equipment is <em>not</em> set by an industry standard (e.g. K-factor, droplet cut
 * size) but by an observed, downstream consequence — most commonly liquid carry-over
 * from a scrubber accumulating in the suction drum of a downstream compressor.
 * Field measurements give pairs of (operating variable, observed carry-over rate)
 * and a piecewise-linear / affine map is fitted around the measured points.
 * </p>
 *
 * <p>
 * The constraint is built on top of the standard {@link CapacityConstraint} machinery
 * so it integrates with bottleneck detection, optimizer feasibility checks and
 * capacity reporting. The {@link CapacityConstraint.ConstraintSource} is set to
 * {@link CapacityConstraint.ConstraintSource#PROCESS_EMPIRICAL} so consumers can
 * distinguish empirical/calibrated limits from standards-driven ones.
 * </p>
 *
 * <p>
 * <strong>Typical use:</strong>
 * </p>
 *
 * <pre>
 * // Operating variable: actual gas volume rate at scrubber [Am3/s]
 * DoubleSupplier rate = () -&gt; scrubber.getThermoSystem().getPhase(0).getFlowRate("m3/sec");
 *
 * // Calibration points (gasRate Am3/s, observed carry-over kg/h)
 * double[] x = {2.0, 3.0, 4.5, 5.5};
 * double[] y = {0.0, 0.5, 3.0, 12.0};
 *
 * EmpiricalCarryOverConstraint co = EmpiricalCarryOverConstraint.fromObservations(
 *     "carryOver", "kg/h", rate, x, y, 5.0);   // limit = 5 kg/h
 * co.setSourceReference("Suction drum LT-2103, May–Aug 2025");
 * separator.addCapacityConstraint(co);
 * </pre>
 *
 * <p>
 * The instance is itself a {@link CapacityConstraint} so it can be enabled, disabled,
 * inspected for utilization, or wrapped via {@code CapacityConstraintAdapter} for the
 * unified optimizer pipeline.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EmpiricalCarryOverConstraint extends CapacityConstraint implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Sorted operating-variable values used for piecewise-linear interpolation. */
  private final double[] xPoints;
  /** Carry-over values observed at each calibration point. */
  private final double[] yPoints;
  /** Supplier for the operating variable used to drive the correlation. */
  private final transient DoubleSupplier driverSupplier;

  /**
   * Constructs a piecewise-linear empirical carry-over constraint.
   *
   * @param name display name for the constraint (e.g., "carryOver")
   * @param unit unit of the carry-over value (e.g., "kg/h")
   * @param driverSupplier supplier of the operating variable that the correlation depends on
   *        (gas rate, pressure ratio, etc.)
   * @param xPoints sorted ascending operating-variable calibration points
   * @param yPoints carry-over observed at each calibration point (same length as xPoints)
   * @param maxAllowable maximum allowable carry-over above which the constraint is violated
   * @throws IllegalArgumentException if x/y arrays are empty, mismatched, or x not sorted
   */
  public EmpiricalCarryOverConstraint(String name, String unit, DoubleSupplier driverSupplier,
      double[] xPoints, double[] yPoints, double maxAllowable) {
    super(name, unit, ConstraintType.SOFT);
    if (xPoints == null || yPoints == null || xPoints.length == 0
        || xPoints.length != yPoints.length) {
      throw new IllegalArgumentException(
          "xPoints and yPoints must be non-null, equal-length, non-empty arrays");
    }
    for (int i = 1; i < xPoints.length; i++) {
      if (xPoints[i] <= xPoints[i - 1]) {
        throw new IllegalArgumentException("xPoints must be strictly ascending");
      }
    }
    this.xPoints = Arrays.copyOf(xPoints, xPoints.length);
    this.yPoints = Arrays.copyOf(yPoints, yPoints.length);
    this.driverSupplier = driverSupplier;
    setDesignValue(maxAllowable);
    setMaxValue(maxAllowable);
    setSeverity(ConstraintSeverity.HARD);
    setSource(ConstraintSource.PROCESS_EMPIRICAL);
    setDescription(
        "Empirical carry-over correlation calibrated against downstream measurements");
    setValueSupplier(this::evaluateCorrelation);
  }

  /**
   * Convenience factory that mirrors the constructor.
   *
   * @param name display name
   * @param unit unit of the carry-over value
   * @param driverSupplier supplier for the driving operating variable
   * @param xPoints ascending calibration x-values
   * @param yPoints carry-over y-values at each x
   * @param maxAllowable maximum allowable carry-over
   * @return a configured constraint
   */
  public static EmpiricalCarryOverConstraint fromObservations(String name, String unit,
      DoubleSupplier driverSupplier, double[] xPoints, double[] yPoints, double maxAllowable) {
    return new EmpiricalCarryOverConstraint(name, unit, driverSupplier, xPoints, yPoints,
        maxAllowable);
  }

  /**
   * Evaluates the piecewise-linear correlation at the current driver value. Values below the
   * first calibration point return the first observation; values above the last return a
   * linear extrapolation using the slope of the last segment.
   *
   * @return interpolated / extrapolated carry-over value
   */
  private double evaluateCorrelation() {
    if (driverSupplier == null) {
      return 0.0;
    }
    double x = driverSupplier.getAsDouble();
    if (x <= xPoints[0]) {
      return yPoints[0];
    }
    int last = xPoints.length - 1;
    if (x >= xPoints[last]) {
      // Linear extrapolation using the slope of the last segment to keep the
      // correlation conservative when operating beyond the calibration envelope.
      double slope = (yPoints[last] - yPoints[last - 1]) / (xPoints[last] - xPoints[last - 1]);
      return yPoints[last] + slope * (x - xPoints[last]);
    }
    int i = 1;
    while (xPoints[i] < x) {
      i++;
    }
    double t = (x - xPoints[i - 1]) / (xPoints[i] - xPoints[i - 1]);
    return yPoints[i - 1] + t * (yPoints[i] - yPoints[i - 1]);
  }

  /**
   * Returns a defensive copy of the calibration x-values.
   *
   * @return ascending operating-variable calibration points
   */
  public double[] getCalibrationX() {
    return Arrays.copyOf(xPoints, xPoints.length);
  }

  /**
   * Returns a defensive copy of the calibration y-values.
   *
   * @return carry-over observations at each calibration point
   */
  public double[] getCalibrationY() {
    return Arrays.copyOf(yPoints, yPoints.length);
  }
}
