package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Wedge-meter differential-pressure flow meter following ISO 5167-1 and ISO 5167-6.
 *
 * <p>
 * The wedge meter has no physical throat bore: a V-shaped restriction of gap height <i>h</i> is machined into a pipe of
 * diameter <i>D</i>, and the diameter ratio is derived from the wedge ratio <i>h / D</i>, ISO 5167-6 Formula (3):
 * </p>
 *
 * <pre>
 * beta = sqrt((1 / pi) * (arccos(1 - 2h/D) - (1 - 2h/D) * 2 * sqrt(h/D - (h/D)^2)))
 * </pre>
 *
 * <p>
 * Set the geometry with {@link #setGeometry(double, double, String)}, passing the wedge gap height as the second
 * argument, or with {@link #setWedgeRatio(double)} when h / D is already known; the equivalent throat diameter <i>d = D
 * beta</i> is derived and stored exactly as for the other differential-pressure devices.
 * </p>
 *
 * <p>
 * Within its limits of use (50 mm &lt;= D &lt;= 600 mm, 0.377 &lt;= beta &lt;= 0.791 i.e. 0.2 &lt;= h/D &lt;= 0.6,
 * 1.0e4 &lt;= Re,D &lt;= 9.0e6) an uncalibrated wedge meter has the discharge coefficient of ISO 5167-6 Formula in
 * 5.5.2:
 * </p>
 *
 * <pre>
 * C = 0.77 - 0.09 beta
 * </pre>
 *
 * <p>
 * No wedge-specific expansibility data has been published, so ISO 5167-6 5.6 applies the same isentropic expansibility
 * factor as the ISO 5167-3 nozzles and the ISO 5167-4 classical Venturi tube, {@link ExpansibilityModel#ISENTROPIC}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WedgeFlowMeter extends DifferentialPressureFlowMeter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(WedgeFlowMeter.class);

  /** Minimum upstream pipe diameter D for which ISO 5167-6 applies. */
  public static final double MIN_PIPE_DIAMETER_MM = 50.0;

  /** Maximum upstream pipe diameter D for which ISO 5167-6 applies. */
  public static final double MAX_PIPE_DIAMETER_MM = 600.0;

  /** Minimum diameter ratio beta for which ISO 5167-6 applies (wedge ratio h/D = 0.2). */
  public static final double MIN_BETA = 0.377;

  /** Maximum diameter ratio beta for which ISO 5167-6 applies (wedge ratio h/D = 0.6). */
  public static final double MAX_BETA = 0.791;

  /** Minimum pipe Reynolds number for which ISO 5167-6 applies. */
  public static final double MIN_REYNOLDS_NUMBER = 1.0e4;

  /** Maximum pipe Reynolds number for which ISO 5167-6 applies. */
  public static final double MAX_REYNOLDS_NUMBER = 9.0e6;

  /** Relative expanded uncertainty of an uncalibrated wedge meter's discharge coefficient (ISO 5167-6 5.7). */
  public static final double DISCHARGE_COEFFICIENT_UNCERTAINTY = 0.04;

  /** Wedge ratio h / D [-], NaN until the geometry has been set. */
  private double wedgeRatio = Double.NaN;

  /**
   * Constructor for WedgeFlowMeter with the default name "wedge flow meter".
   *
   * @param stream the stream the meter is installed on, must be non-null
   */
  public WedgeFlowMeter(StreamInterface stream) {
    this("wedge flow meter", stream);
  }

  /**
   * Constructor for WedgeFlowMeter.
   *
   * @param name device tag, must be non-null
   * @param stream the stream the meter is installed on, must be non-null
   */
  public WedgeFlowMeter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * Sets the pipe diameter and the wedge gap height; the wedge ratio h / D and the diameter ratio beta (ISO 5167-6
   * Formula (3)) are derived and the equivalent throat diameter D * beta is stored.
   *
   * @param pipeDiameter upstream pipe internal diameter D, must be positive
   * @param wedgeHeight wedge gap height h, must satisfy 0 &lt; h &lt; D
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   */
  @Override
  public void setGeometry(double pipeDiameter, double wedgeHeight, String unit) {
    setPipeDiameter(pipeDiameter, unit);
    double pipeDiameterMeters = getPipeDiameter("m");
    double wedgeHeightMeters = wedgeHeight * lengthConversionToMeter(unit);
    setWedgeRatio(wedgeHeightMeters / pipeDiameterMeters);
  }

  /**
   * Sets the wedge ratio h / D directly and derives the diameter ratio beta (ISO 5167-6 Formula (3)) from it. Uses
   * whatever pipe diameter is currently stored (the base class default is 0.2 m until
   * {@link #setGeometry(double, double, String)} or {@link #setPipeDiameter(double, String)} is called); call one of
   * those first for the derived throat diameter to be meaningful.
   *
   * @param wedgeRatio wedge ratio h / D, must satisfy 0 &lt; h/D &lt; 1
   */
  public void setWedgeRatio(double wedgeRatio) {
    if (!(wedgeRatio > 0.0) || !(wedgeRatio < 1.0)) {
      logger.warn("{}: wedge ratio h/D = {} is outside the valid (0, 1) range; storing NaN throat diameter", getName(),
          wedgeRatio);
      this.wedgeRatio = Double.NaN;
      setThroatDiameter(Double.NaN, "m");
      return;
    }
    this.wedgeRatio = wedgeRatio;
    double oneMinus2hOverD = 1.0 - 2.0 * wedgeRatio;
    double sqrtTerm = Math.sqrt(Math.max(0.0, wedgeRatio - wedgeRatio * wedgeRatio));
    double beta = Math.sqrt(Math.max(0.0, (Math.acos(oneMinus2hOverD) - oneMinus2hOverD * 2.0 * sqrtTerm) / Math.PI));
    setThroatDiameter(beta * getPipeDiameter("m"), "m");
  }

  /**
   * Getter for the wedge ratio h / D.
   *
   * @return wedge ratio [-], NaN until the geometry has been set
   */
  public double getWedgeRatio() {
    return wedgeRatio;
  }

  /** {@inheritDoc} */
  @Override
  protected String getDifferentialPressureFlowMeterTransientStateCoverageIssue() {
    if (getClass() != WedgeFlowMeter.class) {
      return "wedge-flow-meter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected Serializable captureDifferentialPressureFlowMeterExtensionState() {
    return new WedgeFlowMeterState(wedgeRatio);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreDifferentialPressureFlowMeterExtensionState(Serializable extensionState) {
    if (!(extensionState instanceof WedgeFlowMeterState)) {
      throw new IllegalArgumentException("Wedge flow-meter extension snapshot has the wrong type");
    }
    wedgeRatio = ((WedgeFlowMeterState) extensionState).wedgeRatio;
  }

  /** Immutable wedge-specific rollback point. */
  private static final class WedgeFlowMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double wedgeRatio;

    private WedgeFlowMeterState(double wedgeRatio) {
      this.wedgeRatio = wedgeRatio;
    }
  }

  /** {@inheritDoc} */
  @Override
  protected ExpansibilityModel getExpansibilityModel() {
    return ExpansibilityModel.ISENTROPIC;
  }

  /**
   * Returns the discharge coefficient of an uncalibrated wedge meter, independent of the Reynolds number.
   *
   * @param beta diameter ratio d/D [-]
   * @param reynoldsD pipe Reynolds number [-], unused
   * @return discharge coefficient [-]
   */
  @Override
  protected double calcDischargeCoefficient(double beta, double reynoldsD) {
    return 0.77 - 0.09 * beta;
  }

  /**
   * Lists the ISO 5167-6 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  @Override
  public List<String> getValidityViolations() {
    List<String> violations = newViolationList();
    double pipeDiameterMm = getPipeDiameter("mm");
    if (!(pipeDiameterMm >= MIN_PIPE_DIAMETER_MM && pipeDiameterMm <= MAX_PIPE_DIAMETER_MM)) {
      violations
          .add("D = " + pipeDiameterMm + " mm outside " + MIN_PIPE_DIAMETER_MM + " to " + MAX_PIPE_DIAMETER_MM + " mm");
    }
    double beta = getBetaRatio();
    if (!(beta >= MIN_BETA && beta <= MAX_BETA)) {
      violations.add("beta = " + beta + " outside " + MIN_BETA + " to " + MAX_BETA);
    }
    if (!isWithinExpansibilityPressureRatio()) {
      violations.add("p2 / p1 below 0.75");
    }
    double reynoldsD = getReynoldsNumberPipe();
    if (!Double.isNaN(reynoldsD) && !(reynoldsD >= MIN_REYNOLDS_NUMBER && reynoldsD <= MAX_REYNOLDS_NUMBER)) {
      violations.add("Re,D = " + reynoldsD + " outside " + MIN_REYNOLDS_NUMBER + " to " + MAX_REYNOLDS_NUMBER);
    }
    return violations;
  }
}
