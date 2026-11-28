package neqsim.process.equipment.pipeline;

/**
 * Delivery policy that keeps hydrocarbon liquid from leaving a {@link CompositionalLinePackPipe} during restart.
 *
 * <p>
 * The outlet stays shut until two conditions hold: the whole line is liquid-free, and the outlet pressure exceeds a
 * floor. The floor is the larger of the feed cricondenbar and the upper dew-point pressure of the gas in the last
 * sections of the line (each at the colder of its own and the outlet temperature), plus a margin. The local term
 * matters: gas left after revaporising condensate is richer than the feed and has a higher dew point. Once open,
 * delivery ramps linearly toward the requested rate and is throttled proportionally when the outlet falls toward the
 * floor, which is re-evaluated at a fixed interval.
 * </p>
 *
 * <p>
 * The controller is advisory: call {@link #update(CompositionalLinePackPipe, double, double)} every interval and pass
 * the result to {@link CompositionalLinePackPipe#setOutletMassFlowRate(double)}.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class DewPointGuardedOutletController implements java.io.Serializable {
  private static final long serialVersionUID = 1000;
  /** Feed cricondenbar in bara. */
  private final double cricondenbarBara;
  /** Pressure margin above the floor in bar. */
  private double marginBar = 2.0;
  /** Number of sections counted back from the outlet for the local dew point. */
  private int downstreamSections = 8;
  /** Interval between dew-point evaluations in s. */
  private double evaluationInterval = 900.0;
  /** Delivery ramp duration in s. */
  private double rampTime = 6.0 * 3600.0;
  /** Proportional band below which delivery is throttled, bar. */
  private double throttleBandBar = 10.0;
  /** Upper end of the dew-point scan in bara. */
  private double maxScanPressureBara = 220.0;
  /** Time since the last dew-point evaluation, s. */
  private double sinceEvaluation = Double.POSITIVE_INFINITY;
  /** Current outlet-pressure floor in bara. */
  private double floorBara;
  /** Last evaluated downstream dew-point pressure in bara. */
  private double downstreamDewPointBara;
  /** Whether delivery has been opened. */
  private boolean open = false;
  /** Time since opening, s. */
  private double timeOpen = 0.0;
  /** Number of updates in which the outlet section was two-phase while delivering. */
  private int twoPhaseDeliveryCount = 0;

  /**
   * Creates the controller.
   *
   * @param cricondenbarBara feed cricondenbar in bara (for example from {@link RetrogradeDewPointScanner#cricondenbar})
   */
  public DewPointGuardedOutletController(double cricondenbarBara) {
    this.cricondenbarBara = cricondenbarBara;
    this.floorBara = cricondenbarBara + marginBar;
  }

  /**
   * Sets the margin added to the dew-point floor (default 2 bar).
   *
   * @param bar margin in bar
   */
  public void setMarginBar(double bar) {
    this.marginBar = bar;
  }

  /**
   * Sets how many sections back from the outlet are checked for the local dew point (default 8).
   *
   * @param sections number of sections
   */
  public void setDownstreamSections(int sections) {
    this.downstreamSections = sections;
  }

  /**
   * Sets the interval between dew-point evaluations (default 900 s).
   *
   * @param seconds interval in s
   */
  public void setEvaluationInterval(double seconds) {
    this.evaluationInterval = seconds;
  }

  /**
   * Sets the delivery ramp duration (default 6 h).
   *
   * @param seconds ramp duration in s
   */
  public void setRampTime(double seconds) {
    this.rampTime = seconds;
  }

  /**
   * Sets the upper pressure of the dew-point scan (default 220 bara).
   *
   * @param bara pressure in bara
   */
  public void setMaxScanPressure(double bara) {
    this.maxScanPressureBara = bara;
  }

  /**
   * Computes the allowed outlet mass flow for the next interval.
   *
   * @param pipe the pipe being controlled
   * @param requestedKgPerSecond requested delivery rate in kg/s
   * @param dtSeconds interval length in s
   * @return outlet mass flow to apply, kg/s
   */
  public double update(CompositionalLinePackPipe pipe, double requestedKgPerSecond, double dtSeconds) {
    sinceEvaluation += dtSeconds;
    if (sinceEvaluation >= evaluationInterval) {
      downstreamDewPointBara = pipe.getDownstreamDewPointPressure(downstreamSections, maxScanPressureBara);
      floorBara = Math.max(cricondenbarBara, downstreamDewPointBara) + marginBar;
      sinceEvaluation = 0.0;
    }
    double[] p = pipe.getPressureProfile();
    double pOut = p[p.length - 1];
    if (!open && pOut >= floorBara && pipe.getNumberOfTwoPhaseSections() == 0) {
      open = true;
      timeOpen = 0.0;
    }
    if (!open) {
      return 0.0;
    }
    timeOpen += dtSeconds;
    if (pipe.isOutletSectionTwoPhase()) {
      twoPhaseDeliveryCount++;
    }
    double ramp = rampTime > 0 ? Math.min(1.0, timeOpen / rampTime) : 1.0;
    double throttle = Math.max(0.0, Math.min(1.0, (pOut - floorBara) / throttleBandBar));
    return requestedKgPerSecond * ramp * throttle;
  }

  /**
   * Whether delivery has been opened.
   *
   * @return true once the opening conditions were met
   */
  public boolean isOpen() {
    return open;
  }

  /**
   * Current outlet-pressure floor.
   *
   * @return floor in bara
   */
  public double getFloorBara() {
    return floorBara;
  }

  /**
   * Last evaluated downstream dew-point pressure.
   *
   * @return pressure in bara (0 if single phase over the scanned range)
   */
  public double getDownstreamDewPointBara() {
    return downstreamDewPointBara;
  }

  /**
   * Number of updates in which delivery was open while the outlet section was two-phase (should stay 0).
   *
   * @return count of violating updates
   */
  public int getTwoPhaseDeliveryCount() {
    return twoPhaseDeliveryCount;
  }
}
