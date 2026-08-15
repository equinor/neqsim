package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.List;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Nozzle differential-pressure flow meter following ISO 5167-1 and ISO 5167-3.
 *
 * <p>
 * ISO 5167-3 covers four nozzle sub-types, selected with {@link #setNozzleType(NozzleType)}, each with its own
 * discharge-coefficient formula but sharing the same isentropic expansibility factor,
 * {@link ExpansibilityModel#ISENTROPIC}:
 * </p>
 *
 * <ul>
 * <li>{@link NozzleType#ISA_1932} &mdash; ISO 5167-3 Formula (5).</li>
 * <li>{@link NozzleType#LONG_RADIUS} &mdash; ISO 5167-3 Formula (10).</li>
 * <li>{@link NozzleType#THROAT_TAPPED} &mdash; ISO 5167-3 Formulae (13) and (14).</li>
 * <li>{@link NozzleType#VENTURI_NOZZLE} &mdash; ISO 5167-3 Formula (19), Reynolds-number independent.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class NozzleFlowMeter extends DifferentialPressureFlowMeter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * The four nozzle sub-types of ISO 5167-3, each with its own discharge-coefficient formula and limits of use.
   */
  public enum NozzleType {
    /**
     * ISA 1932 nozzle, ISO 5167-3 5.1: 50 mm &lt;= D &lt;= 500 mm, 0.3 &lt;= beta &lt;= 0.8, Re,D &gt;= 7.0e4 (beta
     * &lt; 0.44) or 2.0e4 (beta &gt;= 0.44), Re,D &lt;= 1.0e7.
     */
    ISA_1932,
    /**
     * Long radius nozzle, ISO 5167-3 5.2: 50 mm &lt;= D &lt;= 630 mm, 0.2 &lt;= beta &lt;= 0.8, 1.0e4 &lt;= Re,D &lt;=
     * 1.0e7.
     */
    LONG_RADIUS,
    /**
     * Throat-tapped flow nozzle, ISO 5167-3 5.3: 100 mm &lt;= D &lt;= 630 mm, 0.4 &lt;= beta &lt;= 0.5, 8.0e5 &lt;=
     * Re,d &lt;= 2.0e7.
     */
    THROAT_TAPPED,
    /**
     * Venturi nozzle, ISO 5167-3 5.4: 65 mm &lt;= D &lt;= 500 mm, d &gt;= 50 mm, 0.316 &lt;= beta &lt;= 0.775, 1.5e5
     * &lt;= Re,D &lt;= 2.0e6. Discharge coefficient independent of the Reynolds number.
     */
    VENTURI_NOZZLE
  }

  /** Selected nozzle sub-type. */
  private NozzleType nozzleType = NozzleType.ISA_1932;

  /**
   * Constructor for NozzleFlowMeter with the default name "nozzle flow meter".
   *
   * @param stream the stream the meter is installed on, must be non-null
   */
  public NozzleFlowMeter(StreamInterface stream) {
    this("nozzle flow meter", stream);
  }

  /**
   * Constructor for NozzleFlowMeter.
   *
   * @param name device tag, must be non-null
   * @param stream the stream the meter is installed on, must be non-null
   */
  public NozzleFlowMeter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * Selects the nozzle sub-type.
   *
   * @param nozzleType nozzle sub-type, must be non-null
   */
  public void setNozzleType(NozzleType nozzleType) {
    this.nozzleType = nozzleType == null ? NozzleType.ISA_1932 : nozzleType;
  }

  /**
   * Getter for the selected nozzle sub-type.
   *
   * @return the active nozzle sub-type, never null
   */
  public NozzleType getNozzleType() {
    return nozzleType;
  }

  /** {@inheritDoc} */
  @Override
  protected String getDifferentialPressureFlowMeterTransientStateCoverageIssue() {
    if (getClass() != NozzleFlowMeter.class) {
      return "nozzle-flow-meter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected Serializable captureDifferentialPressureFlowMeterExtensionState() {
    return new NozzleFlowMeterState(nozzleType);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreDifferentialPressureFlowMeterExtensionState(Serializable extensionState) {
    if (!(extensionState instanceof NozzleFlowMeterState)) {
      throw new IllegalArgumentException("Nozzle flow-meter extension snapshot has the wrong type");
    }
    nozzleType = ((NozzleFlowMeterState) extensionState).nozzleType;
  }

  /** Immutable nozzle-specific rollback point. */
  private static final class NozzleFlowMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final NozzleType nozzleType;

    private NozzleFlowMeterState(NozzleType nozzleType) {
      this.nozzleType = nozzleType;
    }
  }

  /** {@inheritDoc} */
  @Override
  protected ExpansibilityModel getExpansibilityModel() {
    return ExpansibilityModel.ISENTROPIC;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcDischargeCoefficient(double beta, double reynoldsD) {
    switch (nozzleType) {
    case LONG_RADIUS:
      return 0.9965 - 0.00653 * Math.sqrt(1.0e6 * beta / reynoldsD);
    case THROAT_TAPPED:
      return calcThroatTappedDischargeCoefficient(reynoldsD / beta);
    case VENTURI_NOZZLE:
      return 0.9858 - 0.196 * Math.pow(beta, 4.5);
    case ISA_1932:
    default:
      double beta2 = beta * beta;
      return 0.9900 - 0.2262 * Math.pow(beta, 4.1)
          - (0.00175 * beta2 - 0.0033 * Math.pow(beta, 4.15)) * Math.pow(1.0e6 / reynoldsD, 1.15);
    }
  }

  /**
   * Calculates the throat-tapped flow nozzle discharge coefficient, ISO 5167-3 Formulae (13) and (14).
   *
   * @param reynoldsThroat throat Reynolds number Re,d [-]
   * @return discharge coefficient C [-], or NaN when Re,d &lt; 400000 (the {@code 1 - 400000 / Re,d} term of Formula
   * (13)/(14) would otherwise go negative, outside the domain of a non-integer power)
   */
  private double calcThroatTappedDischargeCoefficient(double reynoldsThroat) {
    if (reynoldsThroat < 400000.0) {
      return Double.NaN;
    }
    double common = 0.255 / Math.pow(reynoldsThroat, 0.2) * Math.pow(1.0 - 400000.0 / reynoldsThroat, 0.8);
    if (reynoldsThroat < 3.0e6) {
      return 1.0090 - common;
    }
    return 0.9823 - common + 0.0018 * Math.log(reynoldsThroat);
  }

  /**
   * Lists the ISO 5167-3 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  @Override
  public List<String> getValidityViolations() {
    switch (nozzleType) {
    case LONG_RADIUS:
      return getLongRadiusValidityViolations();
    case THROAT_TAPPED:
      return getThroatTappedValidityViolations();
    case VENTURI_NOZZLE:
      return getVenturiNozzleValidityViolations();
    case ISA_1932:
    default:
      return getIsa1932ValidityViolations();
    }
  }

  /**
   * Lists the ISO 5167-3 5.1.6.1 limits of use for the ISA 1932 nozzle.
   *
   * @return list of human-readable violations
   */
  private List<String> getIsa1932ValidityViolations() {
    List<String> violations = newViolationList();
    double pipeDiameterMm = getPipeDiameter("mm");
    if (!(pipeDiameterMm >= 50.0 && pipeDiameterMm <= 500.0)) {
      violations.add("D = " + pipeDiameterMm + " mm outside 50 to 500 mm");
    }
    double beta = getBetaRatio();
    if (!(beta >= 0.3 && beta <= 0.8)) {
      violations.add("beta = " + beta + " outside 0.3 to 0.8");
    }
    double reynoldsD = getReynoldsNumberPipe();
    if (!Double.isNaN(reynoldsD)) {
      double minReynolds = beta < 0.44 ? 7.0e4 : 2.0e4;
      if (!(reynoldsD >= minReynolds && reynoldsD <= 1.0e7)) {
        violations.add("Re,D = " + reynoldsD + " outside " + minReynolds + " to 1.0e7");
      }
    }
    if (!isWithinExpansibilityPressureRatio()) {
      violations.add("p2 / p1 below 0.75");
    }
    return violations;
  }

  /**
   * Lists the ISO 5167-3 5.2.6.1 limits of use for the long radius nozzle.
   *
   * @return list of human-readable violations
   */
  private List<String> getLongRadiusValidityViolations() {
    List<String> violations = newViolationList();
    double pipeDiameterMm = getPipeDiameter("mm");
    if (!(pipeDiameterMm >= 50.0 && pipeDiameterMm <= 630.0)) {
      violations.add("D = " + pipeDiameterMm + " mm outside 50 to 630 mm");
    }
    double beta = getBetaRatio();
    if (!(beta >= 0.2 && beta <= 0.8)) {
      violations.add("beta = " + beta + " outside 0.2 to 0.8");
    }
    double reynoldsD = getReynoldsNumberPipe();
    if (!Double.isNaN(reynoldsD) && !(reynoldsD >= 1.0e4 && reynoldsD <= 1.0e7)) {
      violations.add("Re,D = " + reynoldsD + " outside 1.0e4 to 1.0e7");
    }
    if (!isWithinExpansibilityPressureRatio()) {
      violations.add("p2 / p1 below 0.75");
    }
    return violations;
  }

  /**
   * Lists the ISO 5167-3 5.3.5.1 limits of use for the throat-tapped flow nozzle.
   *
   * @return list of human-readable violations
   */
  private List<String> getThroatTappedValidityViolations() {
    List<String> violations = newViolationList();
    double pipeDiameterMm = getPipeDiameter("mm");
    if (!(pipeDiameterMm >= 100.0 && pipeDiameterMm <= 630.0)) {
      violations.add("D = " + pipeDiameterMm + " mm outside 100 to 630 mm");
    }
    double beta = getBetaRatio();
    if (!(beta >= 0.4 && beta <= 0.5)) {
      violations.add("beta = " + beta + " outside 0.4 to 0.5");
    }
    double reynoldsThroat = getReynoldsNumberThroat();
    if (!Double.isNaN(reynoldsThroat) && !(reynoldsThroat >= 8.0e5 && reynoldsThroat <= 2.0e7)) {
      violations.add("Re,d = " + reynoldsThroat + " outside 8.0e5 to 2.0e7");
    }
    if (!isWithinExpansibilityPressureRatio()) {
      violations.add("p2 / p1 below 0.75");
    }
    return violations;
  }

  /**
   * Lists the ISO 5167-3 5.4.4.1 limits of use for the Venturi nozzle.
   *
   * @return list of human-readable violations
   */
  private List<String> getVenturiNozzleValidityViolations() {
    List<String> violations = newViolationList();
    double pipeDiameterMm = getPipeDiameter("mm");
    if (!(pipeDiameterMm >= 65.0 && pipeDiameterMm <= 500.0)) {
      violations.add("D = " + pipeDiameterMm + " mm outside 65 to 500 mm");
    }
    double throatDiameterMm = getThroatDiameter("mm");
    if (!(throatDiameterMm >= 50.0)) {
      violations.add("d = " + throatDiameterMm + " mm is below 50 mm");
    }
    double beta = getBetaRatio();
    if (!(beta >= 0.316 && beta <= 0.775)) {
      violations.add("beta = " + beta + " outside 0.316 to 0.775");
    }
    double reynoldsD = getReynoldsNumberPipe();
    if (!Double.isNaN(reynoldsD) && !(reynoldsD >= 1.5e5 && reynoldsD <= 2.0e6)) {
      violations.add("Re,D = " + reynoldsD + " outside 1.5e5 to 2.0e6");
    }
    if (!isWithinExpansibilityPressureRatio()) {
      violations.add("p2 / p1 below 0.75");
    }
    return violations;
  }
}
