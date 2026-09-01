package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Cone-meter differential-pressure flow meter following ISO 5167-1 and ISO 5167-5.
 *
 * <p>
 * The cone meter has no physical throat bore: a cone of diameter <i>dc</i> is mounted concentrically in a pipe of
 * diameter <i>D</i>, and the diameter ratio is derived from the annular flow area around the cone, ISO 5167-5 Formula
 * (2):
 * </p>
 *
 * <pre>
 * beta = sqrt(1 - dc ^ 2 / D ^ 2)
 * </pre>
 *
 * <p>
 * Set the geometry with {@link #setGeometry(double, double, String)}, passing the cone diameter as the second argument;
 * the equivalent throat diameter <i>d = D beta</i> is derived and stored exactly as for the other differential-pressure
 * devices.
 * </p>
 *
 * <p>
 * Within its limits of use (50 mm &lt;= D &lt;= 500 mm, 0.45 &lt;= beta &lt;= 0.75, 8.0e4 &lt;= Re,D &lt;= 1.2e7) an
 * uncalibrated cone meter has a constant discharge coefficient C = 0.82 (ISO 5167-5), independent of the Reynolds
 * number and the pipe diameter. The expansibility factor is {@link ExpansibilityModel#CONE}, ISO 5167-5 Formula (4):
 * </p>
 *
 * <pre>
 * epsilon = 1 - (0.649 + 0.696 beta^4) * dP / (kappa * p1)
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ConeFlowMeter extends DifferentialPressureFlowMeter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ConeFlowMeter.class);

  /** Discharge coefficient of an uncalibrated cone meter (ISO 5167-5). */
  public static final double DISCHARGE_COEFFICIENT = 0.82;

  /** Minimum upstream pipe diameter D for which ISO 5167-5 applies. */
  public static final double MIN_PIPE_DIAMETER_MM = 50.0;

  /** Maximum upstream pipe diameter D for which ISO 5167-5 applies. */
  public static final double MAX_PIPE_DIAMETER_MM = 500.0;

  /** Minimum diameter ratio beta for which ISO 5167-5 applies. */
  public static final double MIN_BETA = 0.45;

  /** Maximum diameter ratio beta for which ISO 5167-5 applies. */
  public static final double MAX_BETA = 0.75;

  /** Minimum pipe Reynolds number for which ISO 5167-5 applies. */
  public static final double MIN_REYNOLDS_NUMBER = 8.0e4;

  /** Maximum pipe Reynolds number for which ISO 5167-5 applies. */
  public static final double MAX_REYNOLDS_NUMBER = 1.2e7;

  /**
   * Constructor for ConeFlowMeter with the default name "cone flow meter".
   *
   * @param stream the stream the meter is installed on, must be non-null
   */
  public ConeFlowMeter(StreamInterface stream) {
    this("cone flow meter", stream);
  }

  /**
   * Constructor for ConeFlowMeter.
   *
   * @param name device tag, must be non-null
   * @param stream the stream the meter is installed on, must be non-null
   */
  public ConeFlowMeter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * Sets the pipe diameter and the cone diameter; the diameter ratio beta = sqrt(1 - dc^2 / D^2) is derived and the
   * equivalent throat diameter D * beta is stored.
   *
   * @param pipeDiameter upstream pipe internal diameter D, must be greater than the cone diameter
   * @param coneDiameter cone diameter dc, must be positive
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   */
  @Override
  public void setGeometry(double pipeDiameter, double coneDiameter, String unit) {
    setPipeDiameter(pipeDiameter, unit);
    double pipeDiameterMeters = getPipeDiameter("m");
    double coneDiameterMeters = coneDiameter * lengthConversionToMeter(unit);
    if (!(pipeDiameterMeters > 0.0) || !(coneDiameterMeters > 0.0) || coneDiameterMeters >= pipeDiameterMeters) {
      logger.warn("{}: cone diameter {} m is not smaller than the pipe diameter {} m; storing NaN throat diameter",
          getName(), coneDiameterMeters, pipeDiameterMeters);
      setThroatDiameter(Double.NaN, "m");
      return;
    }
    double beta = Math.sqrt(1.0 - coneDiameterMeters * coneDiameterMeters / (pipeDiameterMeters * pipeDiameterMeters));
    setThroatDiameter(beta * pipeDiameterMeters, "m");
  }

  /**
   * Returns the cone diameter dc, back-derived from the current pipe diameter and diameter ratio.
   *
   * @param unit length unit, one of "m", "cm", "mm" or "in"
   * @return cone diameter in the requested unit, or NaN when beta is not physical (outside (0, 1])
   */
  public double getConeDiameter(String unit) {
    double beta = getBetaRatio();
    if (Double.isNaN(beta) || beta <= 0.0 || beta > 1.0) {
      return Double.NaN;
    }
    double pipeDiameterMeters = getPipeDiameter("m");
    double coneDiameterMeters = pipeDiameterMeters * Math.sqrt(1.0 - beta * beta);
    return coneDiameterMeters / lengthConversionToMeter(unit);
  }

  /** {@inheritDoc} */
  @Override
  protected String getDifferentialPressureFlowMeterTransientStateCoverageIssue() {
    if (getClass() != ConeFlowMeter.class) {
      return "cone-flow-meter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected Serializable captureDifferentialPressureFlowMeterExtensionState() {
    return ConeFlowMeterState.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreDifferentialPressureFlowMeterExtensionState(Serializable extensionState) {
    if (!(extensionState instanceof ConeFlowMeterState)) {
      throw new IllegalArgumentException("Cone flow-meter extension snapshot has the wrong type");
    }
  }

  /** Immutable marker proving that the concrete cone meter owns no state beyond its base snapshot. */
  private static final class ConeFlowMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private static final ConeFlowMeterState INSTANCE = new ConeFlowMeterState();

    private ConeFlowMeterState() {
    }
  }

  /** {@inheritDoc} */
  @Override
  protected ExpansibilityModel getExpansibilityModel() {
    return ExpansibilityModel.CONE;
  }

  /**
   * Returns the constant discharge coefficient of an uncalibrated cone meter, independent of the Reynolds number.
   *
   * @param beta diameter ratio d/D [-], unused
   * @param reynoldsD pipe Reynolds number [-], unused
   * @return discharge coefficient [-], always {@link #DISCHARGE_COEFFICIENT}
   */
  @Override
  protected double calcDischargeCoefficient(double beta, double reynoldsD) {
    return DISCHARGE_COEFFICIENT;
  }

  /**
   * Lists the ISO 5167-5 limits of use that the current operating point violates.
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
