package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Orifice-plate differential-pressure flow meter following ISO 5167-1 and ISO 5167-2.
 *
 * <p>
 * The discharge coefficient is the Reader-Harris/Gallagher (1998) equation, ISO 5167-2:2022 Formula (4):
 * </p>
 *
 * <pre>
 * C = 0.5961 + 0.0261 beta^2 - 0.216 beta^8
 *     + 0.000521 (1e6 beta / ReD) ^ 0.7
 *     + (0.0188 + 0.0063 A) beta^3.5 (1e6 / ReD) ^ 0.3
 *     + (0.043 + 0.080 exp(-10 L1) - 0.123 exp(-7 L1)) (1 - 0.11 A) beta^4 / (1 - beta^4)
 *     - 0.031 (M2' - 0.8 M2'^1.1) beta^1.3
 * </pre>
 *
 * <p>
 * with <i>A = (19000 beta / ReD) ^ 0.8</i>, <i>M2' = 2 L2' / (1 - beta)</i>, and where D &lt; 71.12 mm the additional
 * term <i>0.011 (0.75 - beta) (2.8 - D / 25.4)</i> (D in millimetres) is added. <i>L1</i> and <i>L2'</i> depend on the
 * {@link TappingArrangement}: both zero for corner tappings, 1 and 0.47 for D and D/2 tappings, and 25.4 / D
 * (millimetres) for flange tappings.
 * </p>
 *
 * <p>
 * The expansibility factor is {@link ExpansibilityModel#ORIFICE}. Both C and epsilon are only valid within the ISO
 * 5167-2 limits of use: 12.5 mm &lt;= d, 50 mm &lt;= D &lt;= 1000 mm, 0.1 &lt;= beta &lt;= 0.75, and a Reynolds-number
 * range that depends on the tapping arrangement and beta; see {@link #getValidityViolations()}.
 * </p>
 *
 * <h2>Wet-gas operation (ISO/TR 11583)</h2>
 *
 * <p>
 * Selecting {@link WetGasCorrelation#ISO_TR_11583} switches the device to the ISO/TR 11583 Clause 7 wet-gas orifice
 * method. Unlike the Venturi tube (ISO/TR 11583 Clause 6), <b>the discharge coefficient is never replaced</b>: clause
 * 7.5.2 states that C remains the plain Reader-Harris/Gallagher equation, evaluated at the Reynolds number that would
 * be obtained if only the gas were flowing. The gas mass flow rate becomes
 * </p>
 *
 * <pre>
 * qm,gas = C / sqrt(1 - beta ^ 4) * epsilon * (pi / 4) * d ^ 2 * sqrt(2 * dP * rho1,gas) / Phi
 * </pre>
 *
 * <p>
 * with the same Chisholm-form over-reading equation as the Venturi tube, <i>Phi = sqrt(1 + CCh X + X^2)</i>, <i>CCh =
 * (rho,liquid / rho1,gas)^n + (rho1,gas / rho,liquid)^n</i>, but the orifice's own exponent (ISO/TR 11583 Equation
 * (6a)/(6b)), which depends only on the gas densiometric Froude number and has no diameter-ratio term:
 * </p>
 *
 * <pre>
 * n = 0.214                                    for 0.2 &lt;= Fr,gas &lt; 1.5
 * n = (1 / sqrt(2) - 0.3 / sqrt(Fr,gas)) ^ 2    for Fr,gas &gt; 1.5
 * </pre>
 *
 * <p>
 * The liquid load is supplied either as an absolute rate ({@link #setLiquidMassFlowRate(double, String)}), as a
 * liquid-to-gas mass ratio ({@link #setLiquidToGasMassRatio(double)}), or taken from the phase split of the connected
 * stream ({@link #setLiquidFromStream(boolean)}). When the liquid rate is unknown and 0.5 &lt;= beta &lt;= 0.68, ISO/TR
 * 11583 7.5.5 allows X to be derived from the permanent pressure loss measured at a third tapping 5D to 7D downstream
 * of the plate; see {@link #setPressureLoss(double, String)}.
 * </p>
 *
 * <p>
 * <b>Limitations of ISO/TR 11583 Clause 7.</b> The equations were derived for nitrogen, natural gas, Exxsol D80 and
 * decane and may not apply to significantly different liquids. Limits of use: 0.24 &lt;= beta &lt;= 0.73, 0 &lt; X
 * &lt;= 0.3, Fr,gas &gt;= 0.2, rho,gas / rho,liquid &gt; 0.014, D &gt;= 50 mm; the pressure-loss route additionally
 * requires 0.5 &lt;= beta &lt;= 0.68 and two density-ratio-dependent bounds on X. Validity is reported by
 * {@link #getValidityViolations()} rather than enforced.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class OrificeFlowMeter extends DifferentialPressureFlowMeter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(OrificeFlowMeter.class);

  /** Minimum orifice bore diameter d for which ISO 5167-2 applies. */
  public static final double MIN_ORIFICE_DIAMETER_MM = 12.5;

  /** Minimum upstream pipe diameter D for which ISO 5167-2 applies. */
  public static final double MIN_PIPE_DIAMETER_MM = 50.0;

  /** Maximum upstream pipe diameter D for which ISO 5167-2 applies. */
  public static final double MAX_PIPE_DIAMETER_MM = 1000.0;

  /** Minimum diameter ratio beta for which ISO 5167-2 applies. */
  public static final double MIN_BETA = 0.1;

  /** Maximum diameter ratio beta for which ISO 5167-2 applies. */
  public static final double MAX_BETA = 0.75;

  /** Minimum diameter ratio beta for which the ISO/TR 11583 Clause 7 wet-gas orifice method applies. */
  public static final double WET_GAS_MIN_BETA = 0.24;

  /** Maximum diameter ratio beta for which the ISO/TR 11583 Clause 7 wet-gas orifice method applies. */
  public static final double WET_GAS_MAX_BETA = 0.73;

  /** Minimum gas densiometric Froude number for which the ISO/TR 11583 Clause 7 exponent is defined. */
  public static final double MIN_FROUDE_NUMBER = 0.2;

  /** Minimum gas/liquid density ratio for which the ISO/TR 11583 Clause 7 wet-gas orifice method applies. */
  public static final double MIN_DENSITY_RATIO = 0.014;

  /** Minimum diameter ratio beta for the ISO/TR 11583 7.5.5 pressure-loss route to determine X. */
  public static final double PRESSURE_LOSS_ROUTE_MIN_BETA = 0.5;

  /** Maximum diameter ratio beta for the ISO/TR 11583 7.5.5 pressure-loss route to determine X. */
  public static final double PRESSURE_LOSS_ROUTE_MAX_BETA = 0.68;

  /** Maximum number of wet-gas iterations before giving up. */
  private static final int MAX_WET_GAS_ITERATIONS = 100;

  /** Relative convergence tolerance of the wet-gas iteration. */
  private static final double WET_GAS_TOLERANCE = 1.0e-12;

  /**
   * Wet-gas over-reading correlation applied to the measured differential pressure.
   */
  public enum WetGasCorrelation {
    /** No wet-gas correction; the dry-gas ISO 5167-2 equation is used as-is. */
    NONE,
    /** ISO/TR 11583 Clause 7 wet-gas orifice method. */
    ISO_TR_11583
  }

  /**
   * The three standardized pressure-tapping arrangements of ISO 5167-2, each with its own L1 / L2' pair used in the
   * Reader-Harris/Gallagher discharge-coefficient equation.
   */
  public enum TappingArrangement {
    /** Corner tappings: L1 = L2' = 0. */
    CORNER,
    /** D and D/2 tappings: L1 = 1, L2' = 0.47. */
    D_AND_D_HALF,
    /** Flange tappings: L1 = L2' = 25.4 mm / D. */
    FLANGE
  }

  /** Selected pressure-tapping arrangement. */
  private TappingArrangement tappingArrangement = TappingArrangement.FLANGE;

  /** Selected wet-gas over-reading correlation. */
  private WetGasCorrelation wetGasCorrelation = WetGasCorrelation.NONE;

  /** Explicit liquid mass flow rate [kg/sec], NaN when not set. */
  private double liquidMassFlowRate = Double.NaN;

  /** Explicit liquid-to-gas mass flow ratio [-], NaN when not set. */
  private double liquidToGasMassRatio = Double.NaN;

  /** Explicit liquid density [kg/m3], NaN when not set. */
  private double liquidDensity = Double.NaN;

  /** When true the liquid load and liquid density are read from the stream phase split. */
  private boolean liquidFromStream = false;

  /** Local acceleration due to gravity [m/s2]. */
  private double gravitationalAcceleration = 9.80665;

  /** Permanent pressure loss across the orifice plate [Pa], NaN when not set. */
  private double pressureLoss = Double.NaN;

  /**
   * Wet-gas result of the last {@link #computeWetGas()} call, reused while {@link #buildWetGasSignature()} is
   * unchanged.
   */
  private transient WetGasResult cachedWetGasResult;

  /** Input fingerprint {@link #cachedWetGasResult} was computed for; null before the first solve. */
  private transient double[] cachedWetGasSignature;

  /**
   * Constructor for OrificeFlowMeter with the default name "orifice flow meter".
   *
   * @param stream the stream the meter is installed on, must be non-null
   */
  public OrificeFlowMeter(StreamInterface stream) {
    this("orifice flow meter", stream);
  }

  /**
   * Constructor for OrificeFlowMeter.
   *
   * @param name device tag, must be non-null
   * @param stream the stream the meter is installed on, must be non-null
   */
  public OrificeFlowMeter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * Selects the pressure-tapping arrangement.
   *
   * @param tappingArrangement tapping arrangement, must be non-null
   */
  public void setTappingArrangement(TappingArrangement tappingArrangement) {
    this.tappingArrangement = tappingArrangement == null ? TappingArrangement.FLANGE : tappingArrangement;
  }

  /**
   * Getter for the selected pressure-tapping arrangement.
   *
   * @return the active tapping arrangement, never null
   */
  public TappingArrangement getTappingArrangement() {
    return tappingArrangement;
  }

  /**
   * Selects the wet-gas over-reading correlation.
   *
   * @param correlation correlation to apply, must be non-null; {@link WetGasCorrelation#NONE} restores the plain
   * dry-gas ISO 5167-2 calculation. {@link WetGasCorrelation#ISO_TR_11583} requires a liquid load to be configured (see
   * {@link #setLiquidFromStream(boolean)}, {@link #setLiquidToGasMassRatio(double)} or
   * {@link #setLiquidMassFlowRate(double, String)})
   */
  public void setWetGasCorrelation(WetGasCorrelation correlation) {
    this.wetGasCorrelation = correlation == null ? WetGasCorrelation.NONE : correlation;
  }

  /**
   * Getter for the selected wet-gas correlation.
   *
   * @return the active wet-gas correlation, never null
   */
  public WetGasCorrelation getWetGasCorrelation() {
    return wetGasCorrelation;
  }

  /**
   * Sets an explicit liquid mass flow rate passing through the meter. Clears any previously set liquid-to-gas mass
   * ratio.
   *
   * @param liquidMassFlowRate liquid mass flow rate, must be non-negative
   * @param unit mass flow unit, one of "kg/sec", "kg/min", "kg/hr", "kg/day" or "tonnes/year"
   */
  public void setLiquidMassFlowRate(double liquidMassFlowRate, String unit) {
    this.liquidMassFlowRate = liquidMassFlowRate * massFlowConversionToKgPerSecond(unit);
    this.liquidToGasMassRatio = Double.NaN;
  }

  /**
   * Sets the liquid-to-gas mass flow ratio. Clears any previously set absolute liquid mass flow rate.
   *
   * @param liquidToGasMassRatio ratio qm,liquid / qm,gas, must be non-negative
   */
  public void setLiquidToGasMassRatio(double liquidToGasMassRatio) {
    this.liquidToGasMassRatio = liquidToGasMassRatio;
    this.liquidMassFlowRate = Double.NaN;
  }

  /**
   * Enables reading the liquid load and liquid density from the phase split of the connected stream. Any hydrocarbon
   * and aqueous liquid phases are combined into one effective liquid, which is an extension beyond ISO/TR 11583.
   *
   * @param liquidFromStream true to derive the liquid load from the stream
   */
  public void setLiquidFromStream(boolean liquidFromStream) {
    this.liquidFromStream = liquidFromStream;
  }

  /**
   * Returns whether the liquid load is derived from the stream phase split.
   *
   * @return true when the liquid load is read from the stream
   */
  public boolean isLiquidFromStream() {
    return liquidFromStream;
  }

  /**
   * Overrides the liquid density used by the wet-gas correlation.
   *
   * @param liquidDensity liquid density, must be positive
   * @param unit density unit, one of "kg/m3" or "g/cm3"
   */
  public void setLiquidDensity(double liquidDensity, String unit) {
    this.liquidDensity = liquidDensity * densityConversionToKgPerM3(unit);
  }

  /**
   * Sets the local acceleration due to gravity used in the Froude number.
   *
   * @param gravitationalAcceleration acceleration in m/s2, must be positive
   */
  public void setGravitationalAcceleration(double gravitationalAcceleration) {
    this.gravitationalAcceleration = gravitationalAcceleration;
  }

  /**
   * Sets the permanent pressure loss measured between the upstream tapping and a third tapping 5D to 7D downstream of
   * the plate. When set, and no liquid load has been supplied, the Lockhart-Martinelli parameter is derived from the
   * pressure-loss ratio using ISO/TR 11583 7.5.5.
   *
   * @param pressureLoss permanent pressure loss, must be non-negative
   * @param unit pressure unit, one of "Pa", "kPa", "MPa", "bar", "mbar" or "psi"
   */
  public void setPressureLoss(double pressureLoss, String unit) {
    this.pressureLoss = pressureLoss * pressureConversionToPa(unit);
  }

  /**
   * Returns the permanent pressure loss used by the ISO/TR 11583 7.5.5 route.
   *
   * @param unit pressure unit, one of "Pa", "kPa", "MPa", "bar", "mbar" or "psi"
   * @return pressure loss in the requested unit, or NaN when not set
   */
  public double getPressureLoss(String unit) {
    return pressureLoss / pressureConversionToPa(unit);
  }

  /**
   * Returns the Lockhart-Martinelli parameter for the current operating point.
   *
   * @return parameter X [-], 0.0 in dry-gas mode and NaN when it cannot be evaluated
   */
  public double getLockhartMartinelliParameter() {
    return solveWetGas().lockhartMartinelli;
  }

  /**
   * Returns the gas densiometric Froude number.
   *
   * @return Froude number Fr,gas [-], or NaN when it cannot be evaluated
   */
  public double getGasDensiometricFroudeNumber() {
    return solveWetGas().froudeNumber;
  }

  /**
   * Returns the wet-gas over-reading correction factor Phi, ISO/TR 11583 Equation (6).
   *
   * @return over-reading factor Phi [-], 1.0 in dry-gas mode
   */
  public double getOverReadingFactor() {
    return solveWetGas().overReading;
  }

  /**
   * Returns the Chisholm-form coefficient CCh, ISO/TR 11583 7.5.3.
   *
   * @return Chisholm-form coefficient [-], or NaN when it cannot be evaluated
   */
  public double getChisholmCoefficient() {
    return solveWetGas().chisholmCoefficient;
  }

  /**
   * Returns the exponent n of the Chisholm-form over-reading equation, ISO/TR 11583 Equation (6a)/(6b).
   *
   * @return exponent n [-], or NaN when it cannot be evaluated
   */
  public double getChisholmExponent() {
    return solveWetGas().chisholmExponent;
  }

  /** {@inheritDoc} */
  @Override
  protected String getDifferentialPressureFlowMeterTransientStateCoverageIssue() {
    if (getClass() != OrificeFlowMeter.class) {
      return "orifice-flow-meter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected Serializable captureDifferentialPressureFlowMeterExtensionState() {
    return new OrificeFlowMeterState(tappingArrangement, wetGasCorrelation, liquidMassFlowRate, liquidToGasMassRatio,
        liquidDensity, liquidFromStream, gravitationalAcceleration, pressureLoss);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreDifferentialPressureFlowMeterExtensionState(Serializable extensionState) {
    if (!(extensionState instanceof OrificeFlowMeterState)) {
      throw new IllegalArgumentException("Orifice flow-meter extension snapshot has the wrong type");
    }
    OrificeFlowMeterState state = (OrificeFlowMeterState) extensionState;
    tappingArrangement = state.tappingArrangement;
    wetGasCorrelation = state.wetGasCorrelation;
    liquidMassFlowRate = state.liquidMassFlowRate;
    liquidToGasMassRatio = state.liquidToGasMassRatio;
    liquidDensity = state.liquidDensity;
    liquidFromStream = state.liquidFromStream;
    gravitationalAcceleration = state.gravitationalAcceleration;
    pressureLoss = state.pressureLoss;
    cachedWetGasResult = null;
    cachedWetGasSignature = null;
  }

  /** Immutable orifice-specific rollback point. Derived wet-gas caches are invalidated on restore. */
  private static final class OrificeFlowMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final TappingArrangement tappingArrangement;
    private final WetGasCorrelation wetGasCorrelation;
    private final double liquidMassFlowRate;
    private final double liquidToGasMassRatio;
    private final double liquidDensity;
    private final boolean liquidFromStream;
    private final double gravitationalAcceleration;
    private final double pressureLoss;

    private OrificeFlowMeterState(TappingArrangement tappingArrangement, WetGasCorrelation wetGasCorrelation,
        double liquidMassFlowRate, double liquidToGasMassRatio, double liquidDensity, boolean liquidFromStream,
        double gravitationalAcceleration, double pressureLoss) {
      this.tappingArrangement = tappingArrangement;
      this.wetGasCorrelation = wetGasCorrelation;
      this.liquidMassFlowRate = liquidMassFlowRate;
      this.liquidToGasMassRatio = liquidToGasMassRatio;
      this.liquidDensity = liquidDensity;
      this.liquidFromStream = liquidFromStream;
      this.gravitationalAcceleration = gravitationalAcceleration;
      this.pressureLoss = pressureLoss;
    }
  }

  /** {@inheritDoc} */
  @Override
  protected ExpansibilityModel getExpansibilityModel() {
    return ExpansibilityModel.ORIFICE;
  }

  /**
   * Returns the upstream tapping spacing term L1 for the selected tapping arrangement.
   *
   * @return L1 [-]
   */
  private double getL1() {
    switch (tappingArrangement) {
    case D_AND_D_HALF:
      return 1.0;
    case FLANGE:
      return 25.4 / getPipeDiameter("mm");
    case CORNER:
    default:
      return 0.0;
    }
  }

  /**
   * Returns the downstream tapping spacing term L2' for the selected tapping arrangement.
   *
   * @return L2' [-]
   */
  private double getL2Prime() {
    switch (tappingArrangement) {
    case D_AND_D_HALF:
      return 0.47;
    case FLANGE:
      return 25.4 / getPipeDiameter("mm");
    case CORNER:
    default:
      return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  protected double calcDischargeCoefficient(double beta, double reynoldsD) {
    double l1 = getL1();
    double l2Prime = getL2Prime();
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    double beta8 = beta4 * beta4;
    double a = Math.pow(19000.0 * beta / reynoldsD, 0.8);
    double m2Prime = 2.0 * l2Prime / (1.0 - beta);

    double c = 0.5961 + 0.0261 * beta2 - 0.216 * beta8 + 0.000521 * Math.pow(1.0e6 * beta / reynoldsD, 0.7)
        + (0.0188 + 0.0063 * a) * Math.pow(beta, 3.5) * Math.pow(1.0e6 / reynoldsD, 0.3)
        + (0.043 + 0.080 * Math.exp(-10.0 * l1) - 0.123 * Math.exp(-7.0 * l1)) * (1.0 - 0.11 * a) * beta4
            / (1.0 - beta4)
        - 0.031 * (m2Prime - 0.8 * Math.pow(m2Prime, 1.1)) * Math.pow(beta, 1.3);

    double pipeDiameterMm = getPipeDiameter("mm");
    if (pipeDiameterMm < 71.12) {
      c += 0.011 * (0.75 - beta) * (2.8 - pipeDiameterMm / 25.4);
    }
    return c;
  }

  /**
   * Calculates the mass flow rate from the ISO 5167-1 general equation, including the ISO/TR 11583 Clause 7 wet-gas
   * correction when selected.
   *
   * @return mass flow rate [kg/sec], 0.0 when the differential pressure is not positive and NaN when the inputs are not
   * physically valid
   */
  @Override
  protected double getMassFlowRatePerSecond() {
    return solveWetGas().gasMassFlowRate;
  }

  /**
   * Holds the outcome of one wet-gas evaluation.
   */
  private static final class WetGasResult {
    /** Gas mass flow rate [kg/sec]. */
    private double gasMassFlowRate = Double.NaN;
    /** Lockhart-Martinelli parameter [-]. */
    private double lockhartMartinelli = 0.0;
    /** Gas densiometric Froude number [-]. */
    private double froudeNumber = Double.NaN;
    /** Over-reading correction factor [-]. */
    private double overReading = 1.0;
    /** Chisholm coefficient [-]. */
    private double chisholmCoefficient = Double.NaN;
    /** Chisholm exponent [-]. */
    private double chisholmExponent = Double.NaN;
    /** Upstream gas density [kg/m3]. */
    private double gasDensity = Double.NaN;
    /** Effective liquid density [kg/m3]. */
    private double liquidDensity = Double.NaN;
  }

  /**
   * Evaluates the mass flow rate from the ISO 5167-1 general equation for a given discharge coefficient and
   * over-reading factor.
   *
   * @param dp differential pressure [Pa]
   * @param epsilon expansibility factor [-]
   * @param beta diameter ratio [-]
   * @param density upstream gas density [kg/m3]
   * @param coefficient discharge coefficient [-]
   * @param overReading over-reading correction factor Phi [-]
   * @return mass flow rate [kg/sec]
   */
  private double evaluateFlowEquation(double dp, double epsilon, double beta, double density, double coefficient,
      double overReading) {
    double throatDiameterMeters = getThroatDiameter("m");
    return coefficient / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0 * throatDiameterMeters
        * throatDiameterMeters * Math.sqrt(2.0 * dp * density) / overReading;
  }

  /**
   * Returns the wet-gas evaluation for the current operating point, reusing the last solve when nothing that
   * {@link #buildWetGasSignature()} tracks has changed. Every wet-gas getter and {@link #getMassFlowRatePerSecond()} go
   * through this method, so multiple reads within the same timestep see one consistent, cheaply-repeated result instead
   * of re-running the iterative solve on every call.
   *
   * @return the wet-gas evaluation outcome
   */
  private WetGasResult solveWetGas() {
    double[] signature = buildWetGasSignature();
    if (cachedWetGasResult != null && Arrays.equals(signature, cachedWetGasSignature)) {
      return cachedWetGasResult;
    }
    WetGasResult result = computeWetGas();
    cachedWetGasResult = result;
    cachedWetGasSignature = signature;
    return result;
  }

  /**
   * Builds a cheap fingerprint of every input {@link #computeWetGas()} depends on (stream properties and wet-gas
   * configuration), used by {@link #solveWetGas()} to detect whether a fresh solve is required. Unset optional inputs
   * (e.g. {@link #pressureLoss}) are NaN; {@link Arrays#equals(double[], double[])} treats NaN as equal to NaN (per its
   * Javadoc contract), so that does not defeat the cache.
   *
   * @return fingerprint array, compared with {@link Arrays#equals(double[], double[])}
   */
  private double[] buildWetGasSignature() {
    return new double[] { getDifferentialPressurePa(), getUpstreamPressurePa(), getBetaRatio(), getGasDensity(),
        getDynamicViscosity(), getIsentropicExponent(), liquidMassFlowRate, resolveLiquidToGasMassRatio(),
        getLiquidDensity(), pressureLoss, gravitationalAcceleration, wetGasCorrelation.ordinal(),
        tappingArrangement.ordinal() };
  }

  /**
   * Solves the ISO/TR 11583 Clause 7 wet-gas equations for the current operating point. Returns the dry-gas result
   * (from the base class's Reynolds-number iteration) when no wet-gas correlation is selected or when no liquid load is
   * available. Never throws.
   *
   * @return the wet-gas evaluation outcome
   */
  private WetGasResult computeWetGas() {
    WetGasResult result = new WetGasResult();
    double dryFlow = super.getMassFlowRatePerSecond();
    result.gasMassFlowRate = dryFlow;
    if (wetGasCorrelation != WetGasCorrelation.ISO_TR_11583 || Double.isNaN(dryFlow)) {
      return result;
    }

    double dp = getDifferentialPressurePa();
    if (dp <= 0.0) {
      return result;
    }
    double p1 = getUpstreamPressurePa();
    double beta = getBetaRatio();
    result.gasDensity = getGasDensity();
    double kappa = getIsentropicExponent();
    double epsilon = ExpansibilityModel.ORIFICE.calculate(dp, p1, beta, kappa);
    double mu = getDynamicViscosity();
    if (Double.isNaN(epsilon) || p1 <= 0.0 || result.gasDensity <= 0.0 || mu <= 0.0) {
      return result;
    }

    result.liquidDensity = getLiquidDensity();
    double massRatio = resolveLiquidToGasMassRatio();
    boolean hasAbsoluteLiquidRate = !Double.isNaN(liquidMassFlowRate);
    boolean hasLiquidRatio = !Double.isNaN(massRatio);
    // The 7.5.5 pressure-loss route is only defined for 0.5 <= beta <= 0.68.
    boolean usePressureLossRoute = !hasAbsoluteLiquidRate && !hasLiquidRatio && !Double.isNaN(pressureLoss)
        && pressureLoss > 0.0 && beta >= PRESSURE_LOSS_ROUTE_MIN_BETA && beta <= PRESSURE_LOSS_ROUTE_MAX_BETA;
    if (Double.isNaN(result.liquidDensity) || result.liquidDensity <= 0.0
        || (!hasAbsoluteLiquidRate && !hasLiquidRatio && !usePressureLossRoute)) {
      // No usable liquid information: fall back to the dry-gas result.
      return result;
    }

    double densityRatioSqrt = Math.sqrt(result.gasDensity / result.liquidDensity);
    double pipeDiameterMeters = getPipeDiameter("m");
    double flow = dryFlow;
    double reynoldsD = getReynoldsNumberPipe();
    if (Double.isNaN(reynoldsD) || reynoldsD <= 0.0) {
      reynoldsD = 4.0 * flow / (Math.PI * mu * pipeDiameterMeters);
    }
    for (int iteration = 0; iteration < MAX_WET_GAS_ITERATIONS; iteration++) {
      double coefficient = calcDischargeCoefficient(beta, reynoldsD);
      double froude = calcGasDensiometricFroudeNumber(flow, result.gasDensity, result.liquidDensity,
          pipeDiameterMeters);
      double exponent = calcChisholmExponent(froude);
      double chisholm = Math.pow(result.liquidDensity / result.gasDensity, exponent)
          + Math.pow(result.gasDensity / result.liquidDensity, exponent);

      double x;
      if (usePressureLossRoute) {
        x = calcLockhartMartinelliFromPressureLoss(dp, beta, coefficient, result.gasDensity, result.liquidDensity);
      } else if (hasAbsoluteLiquidRate) {
        x = flow > 0.0 ? liquidMassFlowRate / flow * densityRatioSqrt : Double.NaN;
      } else {
        x = massRatio * densityRatioSqrt;
      }
      if (Double.isNaN(x)) {
        return result;
      }

      double overReading = Math.sqrt(1.0 + chisholm * x + x * x);
      double updated = evaluateFlowEquation(dp, epsilon, beta, result.gasDensity, coefficient, overReading);
      double updatedReynoldsD = 4.0 * updated / (Math.PI * mu * pipeDiameterMeters);

      result.lockhartMartinelli = x;
      result.froudeNumber = froude;
      result.chisholmExponent = exponent;
      result.chisholmCoefficient = chisholm;
      result.overReading = overReading;
      result.gasMassFlowRate = updated;

      if (Math.abs(updated - flow) <= WET_GAS_TOLERANCE * Math.abs(updated)) {
        setReynoldsNumberPipe(updatedReynoldsD);
        return result;
      }
      flow = updated;
      reynoldsD = updatedReynoldsD;
    }
    setReynoldsNumberPipe(reynoldsD);
    logger.warn("{}: ISO/TR 11583 orifice wet-gas iteration did not converge", getName());
    return result;
  }

  /**
   * Resolves the liquid-to-gas mass ratio from the configured ratio or from the stream phase split. An absolute liquid
   * rate is handled separately because it makes X depend on the gas rate.
   *
   * @return ratio qm,liquid / qm,gas [-], or NaN when no ratio source is available
   */
  private double resolveLiquidToGasMassRatio() {
    if (!Double.isNaN(liquidToGasMassRatio)) {
      return liquidToGasMassRatio;
    }
    if (liquidFromStream) {
      return getStreamLiquidToGasMassRatio();
    }
    return Double.NaN;
  }

  /**
   * Returns the effective liquid density, combining any hydrocarbon and aqueous phase on a volume basis. An explicit
   * value set with {@link #setLiquidDensity(double, String)} takes precedence.
   *
   * @return liquid density [kg/m3], or NaN when the stream carries no liquid and no value was set
   */
  private double getLiquidDensity() {
    if (!Double.isNaN(liquidDensity) && liquidDensity > 0.0) {
      return liquidDensity;
    }
    SystemInterface fluid = stream.getThermoSystem();
    double mass = 0.0;
    double volume = 0.0;
    String[] liquidPhases = new String[] { "oil", "aqueous" };
    for (int i = 0; i < liquidPhases.length; i++) {
      try {
        if (!fluid.hasPhaseType(liquidPhases[i])) {
          continue;
        }
        PhaseInterface phase = fluid.getPhase(liquidPhases[i]);
        double phaseMass = phase.getNumberOfMolesInPhase() * phase.getMolarMass();
        double phaseDensity = phase.getDensity("kg/m3");
        if (phaseMass > 0.0 && phaseDensity > 0.0) {
          mass += phaseMass;
          volume += phaseMass / phaseDensity;
        }
      } catch (Exception ex) {
        logger.debug("could not read {} phase density for {}", liquidPhases[i], getName(), ex);
      }
    }
    return volume > 0.0 ? mass / volume : Double.NaN;
  }

  /**
   * Returns the liquid-to-gas mass flow ratio read from the phase split of the connected stream.
   *
   * @return ratio qm,liquid / qm,gas [-], or NaN when it cannot be evaluated
   */
  private double getStreamLiquidToGasMassRatio() {
    SystemInterface fluid = stream.getThermoSystem();
    double gasMass = 0.0;
    double liquidMass = 0.0;
    try {
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        PhaseInterface phase = fluid.getPhase(i);
        double phaseMass = phase.getNumberOfMolesInPhase() * phase.getMolarMass();
        if (phase.getType() == PhaseType.GAS) {
          gasMass += phaseMass;
        } else {
          liquidMass += phaseMass;
        }
      }
    } catch (Exception ex) {
      logger.debug("could not read phase split for {}", getName(), ex);
      return Double.NaN;
    }
    return gasMass > 0.0 ? liquidMass / gasMass : Double.NaN;
  }

  /**
   * Calculates the gas densiometric Froude number.
   *
   * @param gasMassFlowRate gas mass flow rate [kg/sec]
   * @param gasDensity upstream gas density [kg/m3]
   * @param liquidDensityValue liquid density [kg/m3]
   * @param pipeDiameterMeters upstream pipe diameter [m]
   * @return Froude number [-]
   */
  private double calcGasDensiometricFroudeNumber(double gasMassFlowRate, double gasDensity, double liquidDensityValue,
      double pipeDiameterMeters) {
    double densityDifference = liquidDensityValue - gasDensity;
    if (densityDifference <= 0.0) {
      return Double.NaN;
    }
    double superficial = 4.0 * gasMassFlowRate
        / (Math.PI * pipeDiameterMeters * pipeDiameterMeters * Math.sqrt(gasDensity));
    return superficial / Math.sqrt(gravitationalAcceleration * pipeDiameterMeters * densityDifference);
  }

  /**
   * Calculates the ISO/TR 11583 Clause 7 exponent n from the gas densiometric Froude number. Below the 0.2 lower bound
   * of the correlation the 0.214 plateau is extrapolated rather than returning NaN, so the meter never throws; the
   * point is reported by {@link #getValidityViolations()} instead.
   *
   * @param froude gas densiometric Froude number [-]
   * @return exponent n [-]
   */
  private double calcChisholmExponent(double froude) {
    if (froude > 1.5) {
      double term = 1.0 / Math.sqrt(2.0) - 0.3 / Math.sqrt(froude);
      return term * term;
    }
    return 0.214;
  }

  /**
   * Derives the Lockhart-Martinelli parameter from the permanent pressure loss, per ISO/TR 11583 7.5.5.
   *
   * @param dp differential pressure [Pa]
   * @param beta diameter ratio [-]
   * @param dischargeCoefficient current discharge coefficient estimate [-]
   * @param gasDensity upstream gas density [kg/m3]
   * @param liquidDensityValue liquid density [kg/m3]
   * @return parameter X [-], or NaN when the route is not applicable
   */
  private double calcLockhartMartinelliFromPressureLoss(double dp, double beta, double dischargeCoefficient,
      double gasDensity, double liquidDensityValue) {
    if (dp <= 0.0) {
      return Double.NaN;
    }
    double beta4 = Math.pow(beta, 4.0);
    double c2 = dischargeCoefficient * dischargeCoefficient;
    double radicand = 1.0 - beta4 * (1.0 - c2);
    if (radicand < 0.0) {
      return Double.NaN;
    }
    double sqrtTerm = Math.sqrt(radicand);
    double dryRatio = (sqrtTerm - dischargeCoefficient * beta * beta) / (sqrtTerm + dischargeCoefficient * beta * beta);
    double measuredRatio = pressureLoss / dp;
    double y = measuredRatio - dryRatio;
    double densityRatio = gasDensity / liquidDensityValue;
    return 6.41 * y / Math.pow(beta, 4.9) * Math.pow(densityRatio, 0.92);
  }

  /**
   * Lists the ISO 5167-2 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  @Override
  public List<String> getValidityViolations() {
    if (wetGasCorrelation == WetGasCorrelation.ISO_TR_11583) {
      return getIso11583ValidityViolations();
    }
    return getDryValidityViolations();
  }

  /**
   * Lists the dry-gas ISO 5167-2 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  private List<String> getDryValidityViolations() {
    List<String> violations = newViolationList();
    double throatDiameterMm = getThroatDiameter("mm");
    if (!(throatDiameterMm >= MIN_ORIFICE_DIAMETER_MM)) {
      violations.add("d = " + throatDiameterMm + " mm is below " + MIN_ORIFICE_DIAMETER_MM + " mm");
    }
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
    if (!Double.isNaN(reynoldsD)) {
      if (tappingArrangement == TappingArrangement.FLANGE) {
        double minReynolds = Math.max(5000.0, 170.0 * beta * beta * pipeDiameterMm);
        if (!(reynoldsD >= minReynolds)) {
          violations.add("Re,D = " + reynoldsD + " below " + minReynolds + " (flange tappings)");
        }
      } else {
        double minReynolds = beta <= 0.56 ? 5000.0 : 16000.0 * beta * beta;
        if (!(reynoldsD >= minReynolds)) {
          violations.add("Re,D = " + reynoldsD + " below " + minReynolds);
        }
      }
    }
    return violations;
  }

  /**
   * Lists the ISO/TR 11583 Clause 7 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  private List<String> getIso11583ValidityViolations() {
    List<String> violations = newViolationList();
    WetGasResult result = solveWetGas();
    double beta = getBetaRatio();
    if (!(beta >= WET_GAS_MIN_BETA && beta <= WET_GAS_MAX_BETA)) {
      violations.add("beta = " + beta + " outside " + WET_GAS_MIN_BETA + " to " + WET_GAS_MAX_BETA);
    }
    if (!(result.lockhartMartinelli > 0.0 && result.lockhartMartinelli <= 0.3)) {
      violations.add("X = " + result.lockhartMartinelli + " outside 0 to 0.3");
    }
    if (!(result.froudeNumber >= MIN_FROUDE_NUMBER)) {
      violations.add("Fr,gas = " + result.froudeNumber + " below " + MIN_FROUDE_NUMBER);
    }
    double densityRatio = result.gasDensity / result.liquidDensity;
    if (!(densityRatio > MIN_DENSITY_RATIO)) {
      violations.add("rho,gas / rho,liquid = " + densityRatio + " not greater than " + MIN_DENSITY_RATIO);
    }
    if (getPipeDiameter("mm") < MIN_PIPE_DIAMETER_MM) {
      violations.add("D = " + getPipeDiameter("mm") + " mm is below " + MIN_PIPE_DIAMETER_MM + " mm");
    }
    boolean usedPressureLossRoute = !Double.isNaN(pressureLoss) && pressureLoss > 0.0
        && Double.isNaN(liquidMassFlowRate) && Double.isNaN(liquidToGasMassRatio);
    if (usedPressureLossRoute) {
      if (!(beta >= PRESSURE_LOSS_ROUTE_MIN_BETA && beta <= PRESSURE_LOSS_ROUTE_MAX_BETA)) {
        violations.add("beta = " + beta + " outside " + PRESSURE_LOSS_ROUTE_MIN_BETA + " to "
            + PRESSURE_LOSS_ROUTE_MAX_BETA + " (pressure-loss route)");
      }
      double maxX = 0.45 * Math.pow(densityRatio, 0.46);
      if (!(result.lockhartMartinelli < maxX)) {
        violations.add("X = " + result.lockhartMartinelli + " not below " + maxX + " (pressure-loss route)");
      }
      double maxDensityRatio = 0.21 * beta - 0.09;
      if (!(densityRatio <= maxDensityRatio)) {
        violations
            .add("rho,gas / rho,liquid = " + densityRatio + " exceeds " + maxDensityRatio + " (pressure-loss route)");
      }
    }
    return violations;
  }
}
