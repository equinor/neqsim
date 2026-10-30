package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import neqsim.process.equipment.pump.PumpChart;

/**
 * Forward-only quasi-steady pump characteristic for the existing single-liquid acoustic inlet.
 *
 * <p>
 * A detached {@link PumpChart} fits the supplied reference-speed data and applies its affinity laws. The original
 * reduced-flow interval and an explicit positive-speed interval bound every evaluation; the chart's endpoint clamp is
 * never used as an extrapolation model. A monotone nonincreasing fitted head permits one compatible characteristic
 * root. Equal suction/discharge velocity and elevation bases are assumed, so total head gain equals pressure-head gain.
 * No suction-line storage, density/viscosity correction, shaft inertia, stopped/reverse pump, NPSH or disc model is
 * supplied. The actual-speed table is prescribed data, not a commanded speed or inferred driver dynamics.
 *
 * @author NeqSim
 * @version 1.0
 * @see WaterHammerPipe
 */
public final class AcousticPumpBoundary implements Serializable {
  /** Serialization identifier. */
  private static final long serialVersionUID = 1L;
  /** Standard gravity, m/s2, consistent with WaterHammerPipe. */
  private static final double GRAVITY = 9.81;
  /** Privately owned fit with no mutable external handle. */
  private final PumpChart chart;
  /** Original reduced-flow limits, (m3/hour)/rpm. */
  private final double minimumReducedFlow;
  /** Original upper reduced-flow limit, (m3/hour)/rpm. */
  private final double maximumReducedFlow;
  /** Absolute suction pressure, Pa. */
  private final double suctionPressure;
  /** Actual-speed times on the existing pipe clock, s. */
  private final double[] times;
  /** Actual speeds, rpm. */
  private final double[] speeds;
  /** Input source and applicability declaration. */
  private final String provenance;
  /** Conversion from chart head unit to metres. */
  private final double headToMetres;

  /**
   * Create a detached reference curve and prescribed actual-speed history.
   *
   * @param referenceSpeed reference curve speed, positive rpm
   * @param flow reference actual volume flow, strictly increasing nonnegative m3/hour, at least three points
   * @param head reference nonnegative nonincreasing pump head in the declared unit
   * @param efficiency reference efficiencies, percent, positive and at most 100
   * @param headUnit exactly meter or kJ/kg
   * @param minimumSpeed declared lower applicable speed, positive rpm
   * @param maximumSpeed declared upper applicable speed, rpm
   * @param timeSeconds strictly increasing absolute times starting at zero, at least two points
   * @param speedRpm prescribed actual speeds within the declared range; endpoints are held outside the table
   * @param suctionPressureBara fixed absolute suction pressure at the matching velocity/elevation basis, bara
   * @param source source/applicability declaration, nonempty; synthetic data must be labelled synthetic
   * @throws IllegalArgumentException if data are invalid or the fitted head is not monotone nonincreasing
   */
  public AcousticPumpBoundary(double referenceSpeed, double[] flow, double[] head, double[] efficiency, String headUnit,
      double minimumSpeed, double maximumSpeed, double[] timeSeconds, double[] speedRpm, double suctionPressureBara,
      String source) {
    positive(referenceSpeed, "reference speed");
    positive(minimumSpeed, "minimum speed");
    positive(maximumSpeed, "maximum speed");
    positive(suctionPressureBara, "absolute suction pressure");
    if (minimumSpeed > maximumSpeed || referenceSpeed < minimumSpeed || referenceSpeed > maximumSpeed || source == null
        || source.trim().isEmpty()) {
      throw new IllegalArgumentException("Declare applicable speed limits and source");
    }
    if (!"meter".equals(headUnit) && !"kJ/kg".equals(headUnit)) {
      throw new IllegalArgumentException("Pump head unit must be meter or kJ/kg");
    }
    if (flow == null || head == null || efficiency == null || flow.length < 3 || flow.length != head.length
        || flow.length != efficiency.length) {
      throw new IllegalArgumentException("Provide at least three matching reference-curve points");
    }
    for (int i = 0; i < flow.length; i++) {
      if (!Double.isFinite(flow[i]) || flow[i] < 0.0 || !Double.isFinite(head[i]) || head[i] < 0.0
          || !Double.isFinite(efficiency[i]) || efficiency[i] <= 0.0 || efficiency[i] > 100.0
          || (i > 0 && (flow[i] <= flow[i - 1] || head[i] > head[i - 1]))) {
        throw new IllegalArgumentException("Invalid forward reference-curve data");
      }
    }
    if (timeSeconds == null || speedRpm == null || timeSeconds.length < 2 || timeSeconds.length != speedRpm.length
        || timeSeconds[0] != 0.0) {
      throw new IllegalArgumentException("Actual-speed history must start at zero with matching arrays");
    }
    for (int i = 0; i < timeSeconds.length; i++) {
      if (!Double.isFinite(timeSeconds[i]) || !Double.isFinite(speedRpm[i]) || speedRpm[i] < minimumSpeed
          || speedRpm[i] > maximumSpeed || (i > 0 && timeSeconds[i] <= timeSeconds[i - 1])) {
        throw new IllegalArgumentException("Invalid actual-speed history or applicability range");
      }
    }
    chart = new PumpChart();
    chart.setCurves(new double[0], new double[] {referenceSpeed}, new double[][] {flow.clone()},
        new double[][] {head.clone()}, new double[][] {efficiency.clone()});
    chart.setHeadUnit(headUnit);
    headToMetres = "meter".equals(headUnit) ? 1.0 : 1000.0 / GRAVITY;
    minimumReducedFlow = flow[0] / referenceSpeed;
    maximumReducedFlow = flow[flow.length - 1] / referenceSpeed;
    suctionPressure = suctionPressureBara * 1.0e5;
    positive(suctionPressure, "suction pressure in Pa");
    if (!Double.isFinite(maximumReducedFlow) || maximumReducedFlow <= minimumReducedFlow) {
      throw new IllegalArgumentException("Reference reduced-flow interval is not representable");
    }
    // PumpChart is exactly quadratic in reduced flow. Its derivative is linear, so endpoint derivative checks
    // establish monotonicity over the entire interval, rather than sampling an arbitrary implementation.
    double left = chart.getHead(flow[0], referenceSpeed);
    double middle = chart.getHead(0.5 * flow[0] + 0.5 * flow[flow.length - 1], referenceSpeed);
    double right = chart.getHead(flow[flow.length - 1], referenceSpeed);
    double derivativeLeft = 4.0 * middle - 3.0 * left - right;
    double derivativeRight = 3.0 * right + left - 4.0 * middle;
    double roundoff = 1.0e-12 * Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
    if (!Double.isFinite(left) || !Double.isFinite(middle) || !Double.isFinite(right) || right < 0.0
        || derivativeLeft > roundoff || derivativeRight > roundoff) {
      throw new IllegalArgumentException("Fitted quadratic must have finite nonnegative, nonincreasing head");
    }
    times = timeSeconds.clone();
    speeds = speedRpm.clone();
    provenance = source;
  }

  /**
   * Evaluate actual speed, with exact absolute knots and endpoint holds.
   *
   * @param time physical pipe time, s
   * @return positive actual speed, rpm
   * @throws IllegalArgumentException if time is invalid
   */
  public double getSpeed(double time) {
    validTime(time);
    if (time >= times[times.length - 1]) {
      return speeds[speeds.length - 1];
    }
    for (int i = 1; i < times.length; i++) {
      if (time <= times[i]) {
        double fraction = (time - times[i - 1]) / (times[i] - times[i - 1]);
        return time == times[i] ? speeds[i] : speeds[i - 1] + fraction * (speeds[i] - speeds[i - 1]);
      }
    }
    return speeds[0];
  }

  /**
   * Find the next absolute speed knot without mutating an actuator clock.
   *
   * @param time physical pipe time, s
   * @return next knot strictly after time, or positive infinity
   */
  public double nextKnotAfter(double time) {
    validTime(time);
    for (double knot : times) {
      if (knot > time) {
        return knot;
      }
    }
    return Double.POSITIVE_INFINITY;
  }

  /**
   * Evaluate the declared forward head without allowing PumpChart endpoint clamping.
   *
   * @param flow actual volume flow, m3/s
   * @param time physical pipe time, s
   * @return pump head gain, m
   * @throws IllegalStateException if outside the declared domain or the fitted result is invalid
   */
  public double getHead(double flow, double time) {
    double speed = getSpeed(time);
    double lower = minimumReducedFlow * speed / 3600.0;
    double upper = maximumReducedFlow * speed / 3600.0;
    if (!Double.isFinite(flow) || flow < lower || flow > upper) {
      throw new IllegalStateException("Pump flow outside declared forward curve domain; interval not accepted");
    }
    // Bounds use physical flow rather than a second rounded reduced-flow comparison. Endpoint values are evaluated
    // at their original reduced coordinates; interior values remain strictly inside the chart interval.
    double query = flow == lower ? minimumReducedFlow * speed
        : flow == upper ? maximumReducedFlow * speed : flow * 3600.0;
    double value = chart.getHead(query, speed) * headToMetres;
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalStateException("Invalid pump head; interval not accepted");
    }
    return value;
  }

  /**
   * Solve Hs + Hp(Q,n) = Cm + B*Q on the unique declared forward branch.
   *
   * @param cm incoming pressure-head characteristic, m
   * @param impedance positive acoustic head/flow impedance, s/m2
   * @param density frozen actual liquid density, kg/m3
   * @param time physical pipe time, s
   * @return admissible actual inlet flow, m3/s
   * @throws IllegalStateException if no admissible solution or the residual is nonfinite/unresolved
   */
  public double solveFlow(double cm, double impedance, double density, double time) {
    positive(impedance, "acoustic impedance");
    positive(density, "actual liquid density");
    if (!Double.isFinite(cm)) {
      throw new IllegalArgumentException("Incoming pump characteristic must be finite");
    }
    double speed = getSpeed(time);
    double lo = minimumReducedFlow * speed / 3600.0;
    double hi = maximumReducedFlow * speed / 3600.0;
    double difference = cm - suctionPressure / (density * GRAVITY);
    double fLo = getHead(lo, time) - difference - impedance * lo;
    double fHi = getHead(hi, time) - difference - impedance * hi;
    if (!Double.isFinite(fLo) || !Double.isFinite(fHi) || fLo < 0.0 || fHi > 0.0) {
      throw new IllegalStateException("No admissible forward pump root; signed/stopped/check-valve model required");
    }
    double leftHead = getHead(lo, time);
    double middleHead = getHead(0.5 * lo + 0.5 * hi, time);
    double rightHead = getHead(hi, time);
    double acousticSlope = impedance * (hi - lo);
    if (!Double.isFinite(acousticSlope) || acousticSlope <= 0.0
        || 4.0 * middleHead - 3.0 * leftHead - rightHead >= acousticSlope
        || 3.0 * rightHead + leftHead - 4.0 * middleHead >= acousticSlope) {
      throw new IllegalStateException(
          "Pump characteristic is not strictly monotone/representable; interval not accepted");
    }
    if (fLo == 0.0) {
      return lo;
    }
    if (fHi == 0.0) {
      return hi;
    }
    double root = lo;
    for (int iteration = 0; iteration < 100; iteration++) {
      root = 0.5 * lo + 0.5 * hi;
      double residual = getHead(root, time) - difference - impedance * root;
      if (Math.abs(residual) <= 1.0e-10) {
        return root;
      }
      if (root == lo || root == hi) {
        break;
      }
      if (residual > 0.0) {
        lo = root;
      } else {
        hi = root;
      }
    }
    throw new IllegalStateException("Pump characteristic residual unresolved; interval not accepted");
  }

  /**
   * Get the fixed absolute suction pressure.
   *
   * @return absolute pressure, Pa
   */
  public double getSuctionPressure() {
    return suctionPressure;
  }

  /**
   * Get the declared input source and applicability.
   *
   * @return source declaration
   */
  public String getProvenance() {
    return provenance;
  }

  /**
   * Validate a positive finite scalar.
   *
   * @param value scalar
   * @param name physical name
   * @throws IllegalArgumentException if invalid
   */
  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Validate a physical time.
   *
   * @param time physical time, s
   * @throws IllegalArgumentException if invalid
   */
  private static void validTime(double time) {
    if (!Double.isFinite(time) || time < 0.0) {
      throw new IllegalArgumentException("Physical time must be finite and nonnegative");
    }
  }
}
