package neqsim.process.equipment.pipeline;

import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Transient (water/liquid hammer) unbalanced fluid-force calculator for a pipe segment or bend.
 *
 * <p>
 * NeqSim's {@link WaterHammerPipe} resolves the transient pressure and velocity field with the Method of
 * Characteristics but only exposes pressure envelopes. Mechanical and piping engineers additionally need the
 * <b>unbalanced reaction force</b> on a pipe segment, bend, or support during a fast transient (emergency valve
 * closure, pump trip, check-valve slam). This class converts a segment pressure/velocity time-history into that force
 * history, the peak unbalanced force, and a dynamic load factor (DLF) for support evaluation.
 * </p>
 *
 * <p>
 * <b>Governing control-volume momentum balance</b> for a segment with inlet (1) and outlet (2), equal flow area
 * {@code A}, turning through bend angle {@code theta}:
 * </p>
 *
 * <pre>
 *   F(t) = (p1 A - p2 A) cos(theta) + mdot (v1 - v2) + rho A L (dv/dt)
 * </pre>
 *
 * <p>
 * where {@code mdot = rho A vbar} and {@code vbar} is the segment-average velocity. The first term is the pressure
 * thrust projection, the second the steady momentum change, and the third the unsteady (inertial) contribution that
 * dominates during a hammer event.
 * </p>
 *
 * <p>
 * <b>Standards basis:</b> Energy Institute <i>Guidelines for the avoidance of vibration-induced fatigue failure in
 * process pipework</i> (momentum force and DLF), ASME B31.3 (occasional transient loads), API STD 521 §5.20
 * (depressurization transients), and company flow-induced-vibration (FIV) control guidelines.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TransientForceCalculator {
  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(TransientForceCalculator.class);

  /** Flow area of the segment [m2]. */
  private final double area;

  /** Bend turning angle [deg]; 0 for a straight segment, 90 for a right-angle bend. */
  private double bendAngleDeg = 90.0;

  /** Fluid density [kg/m3]. */
  private double fluidDensity = 1000.0;

  /** Segment length used for the unsteady inertial term [m]. */
  private double segmentLength = 1.0;

  /** Support natural period for the DLF estimate [s]; negative means unknown. */
  private double supportNaturalPeriod = -1.0;

  /** Load rise time for the DLF estimate [s]; negative means unknown. */
  private double riseTime = -1.0;

  /** Time grid of the most recent force history [s]. */
  private double[] timeSeries;

  /** Force history corresponding to {@link #timeSeries} [N]. */
  private double[] forceSeries;

  /** Peak absolute unbalanced force of the most recent history [N]. */
  private double peakForce = 0.0;

  /** Time at which the peak unbalanced force occurs [s]. */
  private double timeOfPeak = 0.0;

  /**
   * Create a transient-force calculator from the pipe internal diameter.
   *
   * @param diameter pipe internal diameter [m]; must be positive
   * @param bendAngleDeg bend turning angle [deg] in the range [0, 180]
   * @param fluidDensity fluid density [kg/m3]; must be positive
   * @throws IllegalArgumentException if diameter or density is non-positive
   */
  public TransientForceCalculator(double diameter, double bendAngleDeg, double fluidDensity) {
    if (diameter <= 0.0) {
      throw new IllegalArgumentException("diameter must be positive");
    }
    if (fluidDensity <= 0.0) {
      throw new IllegalArgumentException("fluidDensity must be positive");
    }
    this.area = 0.25 * Math.PI * diameter * diameter;
    this.bendAngleDeg = bendAngleDeg;
    this.fluidDensity = fluidDensity;
  }

  /**
   * Capture geometry and density from an initialised {@link WaterHammerPipe} (bend angle defaults to 90 deg).
   *
   * @param pipe an initialised water hammer pipe (after {@code run()})
   * @return a calculator seeded with the pipe geometry and fluid density
   * @throws IllegalArgumentException if the pipe is null
   */
  public static TransientForceCalculator fromWaterHammerPipe(WaterHammerPipe pipe) {
    if (pipe == null) {
      throw new IllegalArgumentException("pipe must not be null");
    }
    double density = 1000.0;
    if (pipe.getInletStream() != null && pipe.getInletStream().getThermoSystem() != null) {
      density = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
    }
    TransientForceCalculator calc = new TransientForceCalculator(pipe.getDiameter(), 90.0, density);
    calc.setSegmentLength(pipe.getLength() / Math.max(1, pipe.getNumberOfNodes()));
    return calc;
  }

  /**
   * Set the bend turning angle.
   *
   * @param bendAngleDeg bend angle [deg] in the range [0, 180]
   */
  public void setBendAngleDeg(double bendAngleDeg) {
    this.bendAngleDeg = bendAngleDeg;
  }

  /**
   * Set the segment length used for the unsteady inertial term.
   *
   * @param segmentLength segment length [m]; must be positive
   * @throws IllegalArgumentException if segmentLength is non-positive
   */
  public void setSegmentLength(double segmentLength) {
    if (segmentLength <= 0.0) {
      throw new IllegalArgumentException("segmentLength must be positive");
    }
    this.segmentLength = segmentLength;
  }

  /**
   * Set the support natural period used in the dynamic load factor estimate.
   *
   * @param supportNaturalPeriod support natural period [s]; positive value
   */
  public void setSupportNaturalPeriod(double supportNaturalPeriod) {
    this.supportNaturalPeriod = supportNaturalPeriod;
  }

  /**
   * Set the load rise time used in the dynamic load factor estimate.
   *
   * @param riseTime load rise time [s]; use 0 for an instantaneous (step) load
   */
  public void setRiseTime(double riseTime) {
    this.riseTime = riseTime;
  }

  /**
   * Axial pressure thrust on a capped end or straight segment for a given internal pressure.
   *
   * @param pressurePa internal gauge pressure acting on the area [Pa]
   * @return pressure thrust [N]
   */
  public double pressureThrust(double pressurePa) {
    return pressurePa * area;
  }

  /**
   * Resultant reaction force on a bend for a single (steady) state, combining pressure thrust and momentum, turning
   * through the configured bend angle.
   *
   * @param pressurePa internal gauge pressure [Pa]
   * @param velocity fluid velocity through the bend [m/s]
   * @return resultant bend reaction force magnitude [N]
   */
  public double bendForce(double pressurePa, double velocity) {
    double thetaRad = Math.toRadians(bendAngleDeg);
    double thrust = pressurePa * area + fluidDensity * area * velocity * velocity;
    return 2.0 * thrust * Math.sin(thetaRad / 2.0);
  }

  /**
   * Compute the unbalanced axial force history for the segment from end pressure and velocity time-series. The peak
   * force and time of peak are stored and accessible via the getters.
   *
   * @param time monotonically increasing time grid [s]; length n &ge; 2
   * @param inletPressurePa inlet pressure series [Pa]; length n
   * @param outletPressurePa outlet pressure series [Pa]; length n
   * @param inletVelocity inlet velocity series [m/s]; length n
   * @param outletVelocity outlet velocity series [m/s]; length n
   * @return the unbalanced force history [N]
   * @throws IllegalArgumentException if the arrays are null, shorter than 2, or different lengths
   */
  public double[] computeForceHistory(double[] time, double[] inletPressurePa, double[] outletPressurePa,
      double[] inletVelocity, double[] outletVelocity) {
    if (time == null || inletPressurePa == null || outletPressurePa == null || inletVelocity == null
        || outletVelocity == null) {
      throw new IllegalArgumentException("input arrays must not be null");
    }
    int n = time.length;
    if (n < 2) {
      throw new IllegalArgumentException("time series must contain at least 2 points");
    }
    if (inletPressurePa.length != n || outletPressurePa.length != n || inletVelocity.length != n
        || outletVelocity.length != n) {
      throw new IllegalArgumentException("all input arrays must have the same length");
    }

    double cosTheta = Math.cos(Math.toRadians(bendAngleDeg));
    double[] force = new double[n];
    double[] vbar = new double[n];
    for (int i = 0; i < n; i++) {
      vbar[i] = 0.5 * (inletVelocity[i] + outletVelocity[i]);
    }
    for (int i = 0; i < n; i++) {
      double dvdt = computeDerivative(time, vbar, i);
      double pressureTerm = (inletPressurePa[i] - outletPressurePa[i]) * area * cosTheta;
      double momentumTerm = fluidDensity * area * vbar[i] * (inletVelocity[i] - outletVelocity[i]);
      double unsteadyTerm = fluidDensity * area * segmentLength * dvdt;
      force[i] = pressureTerm + momentumTerm + unsteadyTerm;
    }

    this.timeSeries = time.clone();
    this.forceSeries = force;
    this.peakForce = 0.0;
    this.timeOfPeak = time[0];
    for (int i = 0; i < n; i++) {
      if (Math.abs(force[i]) > Math.abs(peakForce)) {
        peakForce = force[i];
        timeOfPeak = time[i];
      }
    }
    logger.debug("Peak unbalanced force {} N at t={} s", peakForce, timeOfPeak);
    return force;
  }

  /**
   * Central-difference time derivative with one-sided differences at the array ends.
   *
   * @param time time grid [s]
   * @param values value series to differentiate
   * @param i index at which to evaluate the derivative
   * @return time derivative of {@code values} at index {@code i}
   */
  private double computeDerivative(double[] time, double[] values, int i) {
    int n = time.length;
    if (i == 0) {
      double dt = time[1] - time[0];
      return dt == 0.0 ? 0.0 : (values[1] - values[0]) / dt;
    }
    if (i == n - 1) {
      double dt = time[n - 1] - time[n - 2];
      return dt == 0.0 ? 0.0 : (values[n - 1] - values[n - 2]) / dt;
    }
    double dt = time[i + 1] - time[i - 1];
    return dt == 0.0 ? 0.0 : (values[i + 1] - values[i - 1]) / dt;
  }

  /**
   * Dynamic load factor for an undamped single-degree-of-freedom support subject to a ramp-rise load held constant,
   * using the closed-form maximum response. The result approaches 2.0 for an instantaneous (step) load and 1.0 for a
   * slow load relative to the support natural period.
   *
   * @return dynamic load factor in the range [1, 2]
   * @throws IllegalStateException if the support natural period or rise time has not been set
   */
  public double getDynamicLoadFactor() {
    if (supportNaturalPeriod <= 0.0 || riseTime < 0.0) {
      throw new IllegalStateException("set supportNaturalPeriod (>0) and riseTime (>=0) before requesting the DLF");
    }
    if (riseTime == 0.0) {
      return 2.0;
    }
    double omega = 2.0 * Math.PI / supportNaturalPeriod;
    double dlf = 1.0 + (2.0 / (omega * riseTime)) * Math.abs(Math.sin(omega * riseTime / 2.0));
    return Math.min(2.0, dlf);
  }

  /**
   * Get the peak absolute unbalanced force of the most recent history.
   *
   * @param unit force unit, one of "N", "kN", or "lbf"
   * @return peak unbalanced force in the requested unit
   */
  public double getPeakUnbalancedForce(String unit) {
    return peakForce * forceConversionFactor(unit);
  }

  /**
   * Get the design force: peak unbalanced force multiplied by the dynamic load factor.
   *
   * @param unit force unit, one of "N", "kN", or "lbf"
   * @return design force in the requested unit
   * @throws IllegalStateException if the DLF inputs have not been set
   */
  public double getDesignForce(String unit) {
    return getPeakUnbalancedForce(unit) * getDynamicLoadFactor();
  }

  /**
   * Get the time at which the peak unbalanced force occurs.
   *
   * @return time of peak force [s]
   */
  public double getTimeOfPeakForce() {
    return timeOfPeak;
  }

  /**
   * Get a defensive copy of the most recent force history.
   *
   * @return force history [N], or an empty array if none has been computed
   */
  public double[] getSegmentForceSeries() {
    return forceSeries == null ? new double[0] : forceSeries.clone();
  }

  /**
   * Get a defensive copy of the time grid of the most recent force history.
   *
   * @return time grid [s], or an empty array if none has been computed
   */
  public double[] getTimeSeries() {
    return timeSeries == null ? new double[0] : timeSeries.clone();
  }

  /**
   * Get the segment flow area.
   *
   * @return flow area [m2]
   */
  public double getArea() {
    return area;
  }

  /**
   * Conversion factor from Newtons to a requested force unit.
   *
   * @param unit force unit, one of "N", "kN", or "lbf" (case-insensitive); null defaults to N
   * @return multiplicative conversion factor from N to the requested unit
   */
  private double forceConversionFactor(String unit) {
    if (unit == null) {
      return 1.0;
    }
    switch (unit.toLowerCase(Locale.ROOT)) {
    case "kn":
      return 1.0e-3;
    case "lbf":
      return 1.0 / 4.4482216152605;
    case "n":
    default:
      return 1.0;
    }
  }
}
