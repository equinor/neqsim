package neqsim.process.equipment.capacity;

import java.util.Arrays;
import java.util.function.DoubleSupplier;

/**
 * Empirical, calibrated carry-over constraint for a separator or scrubber.
 *
 * <p>
 * This constraint type addresses the situation where the binding limit on a piece of equipment is <em>not</em> set by
 * an industry standard (K-factor, droplet cut size) but by an observed downstream consequence - most commonly liquid
 * carry-over from a scrubber accumulating in the suction drum of a downstream compressor. Field measurements give pairs
 * of (operating variable, observed carry-over rate), and a piecewise-linear map is fitted through the measured points.
 * </p>
 *
 * <p>
 * The constraint is built on the standard {@link CapacityConstraint} machinery, so it integrates with bottleneck
 * detection, optimizer feasibility checks and capacity reporting. It declares
 * {@link CapacityConstraint.ConstraintSource#PROCESS_EMPIRICAL} so consumers can distinguish a calibrated limit from a
 * standards-driven one, and it is created as a {@link CapacityConstraint.ConstraintType#HARD} constraint so that
 * exceeding the allowable carry-over registers in {@code isHardLimitExceeded()} and in the plant-wide
 * {@code anyHardLimitExceeded} feasibility gate.
 * </p>
 *
 * <p>
 * <strong>Typical use:</strong>
 * </p>
 *
 * <pre>
 * // Operating variable: actual gas volume rate at the scrubber [Am3/s]
 * DoubleSupplier rate = () -&gt; scrubber.getThermoSystem().getPhase(0).getFlowRate("m3/sec");
 *
 * // Calibration points (gas rate Am3/s, observed carry-over kg/h)
 * double[] x = {2.0, 3.0, 4.5, 5.5};
 * double[] y = {0.0, 0.5, 3.0, 12.0};
 *
 * EmpiricalCarryOverConstraint co =
 *     EmpiricalCarryOverConstraint.fromObservations("carryOver", "kg/h", rate, x, y, 5.0);
 * co.setSourceReference("Suction drum LT-2103, May-Aug 2025");
 * separator.addCapacityConstraint(co);
 * </pre>
 *
 * <p>
 * Units are not interpreted by this class. The {@code unit} argument labels the carry-over axis, and the caller is
 * responsible for supplying {@code yPoints} and {@code maxAllowable} in that same unit, and for supplying a driver in
 * the unit implied by {@code xPoints}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EmpiricalCarryOverConstraint extends CapacityConstraint {
  private static final long serialVersionUID = 1000L;

  /** Ascending operating-variable values used for piecewise-linear interpolation. */
  private final double[] xPoints;

  /** Carry-over values observed at each calibration point. */
  private final double[] yPoints;

  /** Supplier for the operating variable that drives the correlation. */
  private final transient DoubleSupplier driverSupplier;

  /**
   * Constructs a piecewise-linear empirical carry-over constraint.
   *
   * @param name display name for the constraint, for example "carryOver"
   * @param unit unit of the carry-over value, for example "kg/h"; also the unit of {@code yPoints} and
   * {@code maxAllowable}
   * @param driverSupplier supplier of the operating variable the correlation depends on (gas rate, pressure ratio, and
   * so on), expressed in the unit of {@code xPoints}
   * @param xPoints strictly ascending operating-variable calibration points
   * @param yPoints carry-over observed at each calibration point, same length as {@code xPoints}
   * @param maxAllowable maximum allowable carry-over, in the same unit as {@code yPoints}, above which the constraint
   * is violated
   * @throws IllegalArgumentException if the arrays are null, empty, of unequal length, or if {@code xPoints} is not
   * strictly ascending
   */
  public EmpiricalCarryOverConstraint(String name, String unit, DoubleSupplier driverSupplier,
      double[] xPoints, double[] yPoints, double maxAllowable) {
    super(name, unit, ConstraintType.HARD);
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
    setDataSource("empirical_correlation");
    setDescription("Empirical carry-over correlation calibrated against downstream measurements");
    setValueSupplier(this::evaluateCorrelation);
  }

  /**
   * Convenience factory that mirrors the constructor.
   *
   * @param name display name for the constraint
   * @param unit unit of the carry-over value, also the unit of {@code yPoints} and {@code maxAllowable}
   * @param driverSupplier supplier for the driving operating variable
   * @param xPoints strictly ascending calibration x-values
   * @param yPoints carry-over y-values at each x
   * @param maxAllowable maximum allowable carry-over
   * @return a configured constraint
   * @throws IllegalArgumentException if the arrays are null, empty, of unequal length, or if {@code xPoints} is not
   * strictly ascending
   */
  public static EmpiricalCarryOverConstraint fromObservations(String name, String unit,
      DoubleSupplier driverSupplier, double[] xPoints, double[] yPoints, double maxAllowable) {
    return new EmpiricalCarryOverConstraint(name, unit, driverSupplier, xPoints, yPoints,
        maxAllowable);
  }

  /**
   * Evaluates the piecewise-linear correlation at the current driver value. A driver below the first calibration point
   * returns the first observation; a driver above the last point is linearly extrapolated using the slope of the final
   * segment, which keeps the correlation conservative outside the calibration envelope.
   *
   * @return interpolated or extrapolated carry-over value, in the unit of {@code yPoints}, or 0.0 if no driver supplier
   * is available
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
      if (last == 0) {
        return yPoints[0];
      }
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
