package neqsim.process.measurementdevice;

import java.util.ArrayList;
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
 * Classical Venturi tube differential-pressure flow meter following ISO 5167-1 and ISO 5167-4.
 *
 * <p>
 * The device reads the flowing density and the isentropic exponent from the connected stream, combines them with the
 * measured differential pressure and the Venturi geometry, and reports the mass flow, the actual volume flow and the
 * standard volume flow during a process run.
 * </p>
 *
 * <p>
 * The mass flow rate is calculated from the ISO 5167-1 general equation
 * </p>
 *
 * <pre>
 * qm = C / sqrt(1 - beta ^ 4) * epsilon * (pi / 4) * d ^ 2 * sqrt(2 * dP * rho1)
 * </pre>
 *
 * <p>
 * where the expansibility factor <i>epsilon</i> is the ISO 5167-4 Venturi expression
 * </p>
 *
 * <pre>
 * epsilon = sqrt(kappa * tau ^ (2 / kappa) / (kappa - 1) * (1 - beta ^ 4) / (1 - beta ^ 4 * tau ^ (2 / kappa))
 *     * (1 - tau ^ ((kappa - 1) / kappa)) / (1 - tau))
 * </pre>
 *
 * <p>
 * with <i>tau = p2 / p1</i>. Note that this is the Venturi expansibility, not the ISO 5167-2 orifice approximation
 * <i>epsilon = 1 - (0.41 + 0.35 beta^4) dP / (kappa p1)</i>, which must not be used for Venturi tubes.
 * </p>
 *
 * <p>
 * The upstream static pressure <i>p1</i> is taken from the connected stream and <i>p2 = p1 - dP</i>. The differential
 * pressure is either set explicitly with {@link #setDifferentialPressure(double, String)} (typical when a measured
 * field value is pushed in each time step) or read from a linked {@link DifferentialPressureTransmitter}, which takes
 * precedence when present.
 * </p>
 *
 * <p>
 * Typical discharge coefficients from ISO 5167-4 for a classical Venturi tube are 0.995 (machined convergent section),
 * 0.984 (as-cast convergent section) and 0.985 (rough-welded sheet-iron convergent section). The ISO 5167-4
 * expansibility is defined for <i>p2 / p1 &gt;= 0.75</i>; values outside that window are still evaluated but reported
 * by {@link #isWithinIso5167ValidityRange()} as out of range.
 * </p>
 *
 * <h2>Wet-gas operation (ISO/TR 11583)</h2>
 *
 * <p>
 * When liquid is present the meter over-reads. Selecting {@link WetGasCorrelation#ISO_TR_11583} switches the device to
 * the ISO/TR 11583 wet-gas Venturi method, in which the gas mass flow rate becomes
 * </p>
 *
 * <pre>
 * qm,gas = C / sqrt(1 - beta ^ 4) * epsilon * (pi / 4) * d ^ 2 * sqrt(2 * dP * rho1,gas) / Phi
 * </pre>
 *
 * <p>
 * with the Lockhart-Martinelli parameter <i>X = (qm,liquid / qm,gas) sqrt(rho1,gas / rho,liquid)</i>, the gas
 * densiometric Froude number <i>Fr,gas</i>, the throat Froude number <i>Fr,gas,th = Fr,gas / beta^2.5</i>, and
 * </p>
 *
 * <pre>
 * C     = 1 - 0.0463 exp(-0.05 Fr,gas,th) min(1, sqrt(X / 0.016))
 * Phi   = sqrt(1 + CCh X + X ^ 2)
 * CCh   = (rho,liquid / rho1,gas) ^ n + (rho1,gas / rho,liquid) ^ n
 * n     = max(0.583 - 0.18 beta ^ 2 - 0.578 exp(-0.8 Fr,gas / H), 0.392 - 0.18 beta ^ 2)
 * </pre>
 *
 * <p>
 * <b>Note that ISO/TR 11583 replaces the discharge coefficient.</b> In wet-gas mode the value set with
 * {@link #setDischargeCoefficient(double)} is not used; the wet-gas <i>C</i> above is used instead, and it tends to 1
 * rather than to the dry-gas 0.985. Switching the correlation on therefore changes both <i>C</i> and adds <i>Phi</i>.
 * </p>
 *
 * <p>
 * Since <i>X</i>, <i>Fr,gas</i> and <i>C</i> all depend on the gas mass flow rate, the solution is iterative, starting
 * from <i>C = 1</i> and <i>Phi = 1</i> exactly as in ISO/TR 11583 Annex A.
 * </p>
 *
 * <p>
 * The liquid load is supplied either as an absolute rate ({@link #setLiquidMassFlowRate(double, String)}), as a
 * liquid-to-gas mass ratio ({@link #setLiquidToGasMassRatio(double)}), or taken from the phase split of the connected
 * stream ({@link #setLiquidFromStream(boolean)}). When the liquid rate is unknown, ISO/TR 11583 6.4.5 allows <i>X</i>
 * to be derived from the permanent pressure loss measured at a third tapping downstream of the divergent section; see
 * {@link #setPressureLoss(double, String)}. That third tapping is rarely fitted in practice.
 * </p>
 *
 * <p>
 * <b>Limitations of ISO/TR 11583.</b> The Technical Report states that it applies to wet gas at roughly 95 % gas volume
 * fraction or more, only to flows with a <i>single</i> liquid, and that it "is not intended for the oil and gas
 * industry". Combining an aqueous and a hydrocarbon liquid phase into one effective liquid, as this class does when
 * reading the liquid from the stream, is therefore an extension beyond the Technical Report. The correlations were
 * derived for nitrogen, argon, natural gas and steam with water, Exxsol D80, white spirit and decane, for horizontally
 * installed meters with a single pair of tappings. Validity is reported by {@link #isWithinIso11583ValidityRange()} and
 * {@link #getValidityViolations()} rather than enforced.
 * </p>
 *
 * <h2>Wet-gas operation (de Leeuw, 1997)</h2>
 *
 * <p>
 * Selecting {@link WetGasCorrelation#DE_LEEUW} switches to the de Leeuw (1997) correlation, reported by R.N. Steven,
 * "Wet gas metering with a horizontally mounted Venturi meter", Flow Measurement and Instrumentation 12 (2002) 361-372,
 * Eqs. (12)-(14). It uses the same Chisholm-form over-reading equation as ISO/TR 11583,
 * </p>
 *
 * <pre>
 * qm,gas = C / sqrt(1 - beta ^ 4) * epsilon * (pi / 4) * d ^ 2 * sqrt(2 * dP * rho1,gas) / Phi
 * Phi    = sqrt(1 + CCh X + X ^ 2)
 * CCh    = (rho,liquid / rho1,gas) ^ n + (rho1,gas / rho,liquid) ^ n
 * </pre>
 *
 * <p>
 * but with a different, purely empirical exponent that depends only on the gas densiometric Froude number and has no
 * diameter-ratio term,
 * </p>
 *
 * <pre>
 * n = 0.41                             for Fr,gas &lt;= 1.5
 * n = 0.606 (1 - exp(-0.746 Fr,gas))    for Fr,gas &gt;= 1.5
 * </pre>
 *
 * <p>
 * <b>de Leeuw never replaces the discharge coefficient.</b> Unlike ISO/TR 11583 Equation (4), <i>C</i> above is always
 * the configured {@link #setDischargeCoefficient(double) discharge coefficient};
 * {@link #setUseWetGasDischargeCoefficient} has no effect on this correlation. This makes de Leeuw compatible with an
 * in-service-calibrated discharge coefficient without any extra configuration.
 * </p>
 *
 * <p>
 * <b>Limitations of de Leeuw (1997).</b> Steven (2002) independently benchmarked de Leeuw against five general
 * two-phase Orifice Plate correlations and one other Venturi correlation on NEL wet-gas-loop data (20-60 bara,
 * nitrogen/kerosene, 6 in ISA Controls Venturi, beta = 0.55) and found it the best performer (root-mean-square
 * fractional deviation 0.0211, versus 0.0710 for Chisholm and 0.1260 for Smith &amp; Leang). However, de Leeuw's own
 * data was taken on a 4 in Venturi with beta = 0.401 and the exponent <i>n</i> carries no beta term, so applying it to
 * a meter with a different diameter ratio is an extrapolation; this is reported by
 * {@link #isWithinDeLeeuwValidityRange()} and {@link #getValidityViolations()} rather than enforced. The correlation
 * also has no published Lockhart-Martinelli (X) range of its own (unlike ISO/TR 11583's 0 to 0.3) and, unlike ISO/TR
 * 11583 6.4.5, no permanent-pressure-loss route to estimate X when the liquid rate is unknown.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class VenturiFlowMeter extends DifferentialPressureFlowMeter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(VenturiFlowMeter.class);

  /**
   * Largest pressure-drop ratio dP/p1 for which the ISO 5167-4 expansibility is defined (p2/p1 &gt;= 0.75).
   */
  public static final double ISO_5167_4_MAX_PRESSURE_DROP_RATIO = 0.25;

  /** Default discharge coefficient for a rough-welded sheet-iron classical Venturi tube (ISO 5167-4). */
  public static final double DEFAULT_DISCHARGE_COEFFICIENT = 0.985;

  /**
   * Wet-gas over-reading correlation applied to the measured differential pressure.
   */
  public enum WetGasCorrelation {
    /** No wet-gas correction; the dry-gas ISO 5167 equation is used as-is. */
    NONE,
    /** ISO/TR 11583 wet-gas Venturi method. */
    ISO_TR_11583,
    /**
     * de Leeuw (1997) wet-gas Venturi correlation, in the form reported by R.N. Steven, "Wet gas metering with a
     * horizontally mounted Venturi meter", Flow Measurement and Instrumentation 12 (2002) 361-372, Eqs. (12)-(14).
     * Unlike ISO/TR 11583 the discharge coefficient is never replaced and the exponent n has no diameter-ratio term.
     */
    DE_LEEUW
  }

  /** Surface-tension factor H for a hydrocarbon liquid (ISO/TR 11583 6.4.3). */
  public static final double H_HYDROCARBON = 1.0;

  /** Surface-tension factor H for liquid water at ambient temperature (ISO/TR 11583 6.4.3). */
  public static final double H_WATER_AMBIENT = 1.35;

  /** Surface-tension factor H for liquid water in a wet-steam flow (ISO/TR 11583 6.4.3). */
  public static final double H_WATER_WET_STEAM = 0.79;

  /**
   * Lower bound of the gas densiometric Froude number for which the de Leeuw (1997) exponent is defined (Steven, 2002,
   * Eq. (13a)). Below this the exponent is still extrapolated at its 0.41 plateau so the meter never throws, and the
   * point is reported by {@link #getValidityViolations()}.
   */
  public static final double DE_LEEUW_MIN_FROUDE_NUMBER = 0.5;

  /**
   * Diameter ratio of the 4 in Venturi that the de Leeuw (1997) correlation was fitted to (Steven, 2002, Sec. 4). The
   * exponent n has no diameter-ratio term, so a meter far from this beta is an extrapolation; used only to flag that in
   * {@link #getValidityViolations()}.
   */
  public static final double DE_LEEUW_REFERENCE_BETA = 0.401;

  /** Maximum number of wet-gas iterations before giving up. */
  private static final int MAX_WET_GAS_ITERATIONS = 100;

  /** Relative convergence tolerance of the wet-gas iteration. */
  private static final double WET_GAS_TOLERANCE = 1.0e-12;

  /** Discharge coefficient C [-]. */
  private double dischargeCoefficient = DEFAULT_DISCHARGE_COEFFICIENT;

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

  /** Surface-tension factor H [-]. */
  private double surfaceTensionFactor = H_HYDROCARBON;

  /** Local acceleration due to gravity [m/s2]. */
  private double gravitationalAcceleration = 9.80665;

  /** Permanent pressure loss across the Venturi [Pa], NaN when not set. */
  private double pressureLoss = Double.NaN;

  /** When true the ISO/TR 11583 wet-gas discharge coefficient replaces the configured one. */
  private boolean useWetGasDischargeCoefficient = true;

  /**
   * Wet-gas result of the last {@link #computeWetGas()} call, reused while {@link #buildWetGasSignature()} is
   * unchanged.
   */
  private transient WetGasResult cachedWetGasResult;

  /** Input fingerprint {@link #cachedWetGasResult} was computed for; null before the first solve. */
  private transient double[] cachedWetGasSignature;

  /**
   * Constructor for VenturiFlowMeter with the default name "venturi flow meter".
   *
   * @param stream the stream the meter is installed on, must be non-null
   */
  public VenturiFlowMeter(StreamInterface stream) {
    this("venturi flow meter", stream);
  }

  /**
   * Constructor for VenturiFlowMeter.
   *
   * @param name device tag, must be non-null
   * @param stream the stream the meter is installed on, must be non-null
   */
  public VenturiFlowMeter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * Setter for the discharge coefficient C.
   *
   * @param dischargeCoefficient discharge coefficient, must be positive (typically 0.98 - 1.0)
   */
  public void setDischargeCoefficient(double dischargeCoefficient) {
    this.dischargeCoefficient = dischargeCoefficient;
  }

  /**
   * Getter for the discharge coefficient C.
   *
   * @return discharge coefficient [-]
   */
  public double getDischargeCoefficient() {
    return dischargeCoefficient;
  }

  /**
   * Selects the wet-gas over-reading correlation.
   *
   * @param correlation correlation to apply, must be non-null; {@link WetGasCorrelation#NONE} restores the plain
   * dry-gas ISO 5167 calculation, {@link WetGasCorrelation#ISO_TR_11583} and {@link WetGasCorrelation#DE_LEEUW} both
   * require a liquid load to be configured (see {@link #setLiquidFromStream(boolean)},
   * {@link #setLiquidToGasMassRatio(double)} or {@link #setLiquidMassFlowRate(double, String)})
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
   * Controls whether the ISO/TR 11583 Equation (4) discharge coefficient replaces the configured one.
   *
   * <p>
   * ISO/TR 11583 pairs its own wet-gas discharge coefficient with the over-reading factor, and that is the default.
   * However, a meter whose discharge coefficient has been calibrated in service carries meter-specific bias in that
   * number; replacing it with the generic wet-gas value discards the calibration. Setting this to false keeps the
   * configured discharge coefficient and applies only the over-reading factor, which departs from the Technical Report
   * but preserves an existing calibration.
   * </p>
   *
   * <p>
   * This setting only affects {@link WetGasCorrelation#ISO_TR_11583}. The de Leeuw (1997) correlation
   * ({@link WetGasCorrelation#DE_LEEUW}) never replaces the discharge coefficient, so it is unaffected either way.
   * </p>
   *
   * @param useWetGasDischargeCoefficient true to use ISO/TR 11583 Equation (4), false to keep the configured value
   */
  public void setUseWetGasDischargeCoefficient(boolean useWetGasDischargeCoefficient) {
    this.useWetGasDischargeCoefficient = useWetGasDischargeCoefficient;
  }

  /**
   * Returns whether the ISO/TR 11583 wet-gas discharge coefficient is used. Has no effect in
   * {@link WetGasCorrelation#DE_LEEUW} mode, which never replaces the discharge coefficient.
   *
   * @return true when Equation (4) replaces the configured discharge coefficient
   */
  public boolean isUseWetGasDischargeCoefficient() {
    return useWetGasDischargeCoefficient;
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
   * Sets the liquid-to-gas mass flow ratio. Clears any previously set absolute liquid mass flow rate. This is the form
   * used in ISO/TR 11583 Annex A, where the ratio is known from a recent separator test and stays fixed while the gas
   * rate iterates.
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
   * Sets the surface-tension factor H of ISO/TR 11583 6.4.3.
   *
   * @param surfaceTensionFactor factor H, 1.0 for a hydrocarbon liquid, 1.35 for water at ambient temperature and 0.79
   * for liquid water in a wet-steam flow; must be positive
   */
  public void setSurfaceTensionFactor(double surfaceTensionFactor) {
    this.surfaceTensionFactor = surfaceTensionFactor;
  }

  /**
   * Getter for the surface-tension factor H.
   *
   * @return factor H [-]
   */
  public double getSurfaceTensionFactor() {
    return surfaceTensionFactor;
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
   * Sets the permanent pressure loss measured between the upstream tapping and a third tapping downstream of the
   * divergent section. When set, and no liquid load has been supplied, the Lockhart-Martinelli parameter is derived
   * from the pressure-loss ratio using ISO/TR 11583 6.4.5.
   *
   * @param pressureLoss permanent pressure loss, must be non-negative
   * @param unit pressure unit, one of "Pa", "kPa", "MPa", "bar", "mbar" or "psi"
   */
  public void setPressureLoss(double pressureLoss, String unit) {
    this.pressureLoss = pressureLoss * pressureConversionToPa(unit);
  }

  /**
   * Returns the permanent pressure loss used by the ISO/TR 11583 6.4.5 route.
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
   * Returns the gas densiometric Froude number of ISO/TR 11583 Equation (3).
   *
   * @return Froude number Fr,gas [-], or NaN when it cannot be evaluated
   */
  public double getGasDensiometricFroudeNumber() {
    return solveWetGas().froudeNumber;
  }

  /**
   * Returns the throat Froude number Fr,gas,th = Fr,gas / beta^2.5.
   *
   * @return throat Froude number [-], or NaN when it cannot be evaluated
   */
  public double getThroatFroudeNumber() {
    return solveWetGas().throatFroudeNumber;
  }

  /**
   * Returns the wet-gas over-reading correction factor Phi. Both ISO/TR 11583 Equation (5) and the de Leeuw (1997)
   * correlation share this Chisholm-form expression; they differ only in the exponent n and in whether C is replaced.
   *
   * @return over-reading factor Phi [-], 1.0 in dry-gas mode
   */
  public double getOverReadingFactor() {
    return solveWetGas().overReading;
  }

  /**
   * Returns the Chisholm-form coefficient CCh (ISO/TR 11583 6.4.3, or de Leeuw (1997) Eq. (14) with its own exponent).
   *
   * @return Chisholm-form coefficient [-], or NaN when it cannot be evaluated
   */
  public double getChisholmCoefficient() {
    return solveWetGas().chisholmCoefficient;
  }

  /**
   * Returns the exponent n of the active correlation's Chisholm-form over-reading equation: ISO/TR 11583 6.4.3 (beta-
   * dependent) or de Leeuw (1997) Eqs. (13a)-(13b) (beta-independent).
   *
   * @return exponent n [-], or NaN when it cannot be evaluated
   */
  public double getChisholmExponent() {
    return solveWetGas().chisholmExponent;
  }

  /**
   * Returns the discharge coefficient actually used. In dry-gas mode this is the configured value. In ISO/TR 11583
   * wet-gas mode it is the Equation (4) value, which replaces the configured one (unless
   * {@link #setUseWetGasDischargeCoefficient(boolean)} is false). de Leeuw (1997) never replaces the discharge
   * coefficient, so this is always the configured value in {@link WetGasCorrelation#DE_LEEUW} mode.
   *
   * @return discharge coefficient [-]
   */
  public double getEffectiveDischargeCoefficient() {
    return solveWetGas().dischargeCoefficient;
  }

  /**
   * Returns the relative uncertainty of C/Phi from ISO/TR 11583 6.5, Table 2. This is normally the dominant term in the
   * gas mass flow uncertainty. Steven (2002) does not report an uncertainty band for the de Leeuw (1997) correlation,
   * so this returns NaN in {@link WetGasCorrelation#DE_LEEUW} mode rather than fabricate one.
   *
   * @return relative uncertainty as a fraction, or NaN outside ISO/TR 11583 wet-gas mode
   */
  public double getRelativeUncertaintyOfCOverPhi() {
    if (wetGasCorrelation != WetGasCorrelation.ISO_TR_11583) {
      return Double.NaN;
    }
    WetGasResult result = solveWetGas();
    if (Double.isNaN(result.lockhartMartinelli)) {
      return Double.NaN;
    }
    if (result.pressureLossRatio > 0.0) {
      return result.pressureLossRatio < 0.6 ? 0.04 : 0.06;
    }
    return result.lockhartMartinelli <= 0.15 ? 0.03 : 0.025;
  }

  /**
   * Lists the limits of use of the active wet-gas correlation that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window or no wet-gas
   * correlation is selected
   */
  @Override
  public List<String> getValidityViolations() {
    if (wetGasCorrelation == WetGasCorrelation.ISO_TR_11583) {
      return getIso11583ValidityViolations();
    }
    if (wetGasCorrelation == WetGasCorrelation.DE_LEEUW) {
      return getDeLeeuwValidityViolations();
    }
    return new ArrayList<String>();
  }

  /**
   * Lists the ISO/TR 11583 limits of use that the current operating point violates.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  private List<String> getIso11583ValidityViolations() {
    List<String> violations = new ArrayList<String>();
    WetGasResult result = solveWetGas();
    double beta = getBetaRatio();
    if (!(beta >= 0.4 && beta <= 0.75)) {
      violations.add("beta = " + beta + " outside 0.4 to 0.75");
    }
    if (!(result.lockhartMartinelli > 0.0 && result.lockhartMartinelli <= 0.3)) {
      violations.add("X = " + result.lockhartMartinelli + " outside 0 to 0.3");
    }
    if (!(result.throatFroudeNumber > 3.0)) {
      violations.add("Fr,gas,th = " + result.throatFroudeNumber + " not greater than 3");
    }
    double densityRatio = result.gasDensity / result.liquidDensity;
    if (!(densityRatio > 0.02)) {
      violations.add("rho,gas / rho,liquid = " + densityRatio + " not greater than 0.02");
    }
    if (getPipeDiameter("m") < 0.05) {
      violations.add("D = " + getPipeDiameter("m") + " m is below 50 mm");
    }
    if (result.pressureLossRatio > 0.0) {
      if (!(result.throatFroudeNumber > 4.0)) {
        violations.add("Fr,gas,th = " + result.throatFroudeNumber + " not greater than 4 (pressure-loss route)");
      }
      if (!(result.froudeNumber / surfaceTensionFactor <= 5.5)) {
        violations.add("Fr,gas / H exceeds 5.5 (pressure-loss route)");
      }
      if (!(densityRatio <= 0.09)) {
        violations.add("rho,gas / rho,liquid = " + densityRatio + " exceeds 0.09 (pressure-loss route)");
      }
    }
    return violations;
  }

  /**
   * Lists the de Leeuw (1997) limits of use that the current operating point violates. Steven (2002) only reports the
   * Fr,gas &gt;= 0.5 lower bound and the 4 in / beta = 0.401 reference geometry; there is no published X or pressure
   * range for the correlation itself, unlike ISO/TR 11583.
   *
   * @return list of human-readable violations, empty when the point is inside the validity window
   */
  private List<String> getDeLeeuwValidityViolations() {
    List<String> violations = new ArrayList<String>();
    WetGasResult result = solveWetGas();
    if (!(result.froudeNumber >= DE_LEEUW_MIN_FROUDE_NUMBER)) {
      violations.add("Fr,gas = " + result.froudeNumber + " below the de Leeuw (1997) lower bound of "
          + DE_LEEUW_MIN_FROUDE_NUMBER);
    }
    double beta = getBetaRatio();
    if (Math.abs(beta - DE_LEEUW_REFERENCE_BETA) > 0.05) {
      violations.add("beta = " + beta + " departs from the 0.401 diameter ratio de Leeuw (1997) was fitted to; n has "
          + "no beta term");
    }
    return violations;
  }

  /**
   * Checks whether the current operating point is inside the ISO/TR 11583 limits of use.
   *
   * @return true when no limit is violated
   */
  public boolean isWithinIso11583ValidityRange() {
    return getValidityViolations().isEmpty();
  }

  /**
   * Checks whether the current operating point is inside the de Leeuw (1997) limits of use.
   *
   * @return true when no limit is violated
   */
  public boolean isWithinDeLeeuwValidityRange() {
    return getValidityViolations().isEmpty();
  }

  /**
   * Checks whether the current operating point is inside the ISO 5167-4 validity window for the expansibility factor
   * (p2 / p1 &gt;= 0.75).
   *
   * @return true when the pressure-drop ratio is within the ISO 5167-4 range
   */
  public boolean isWithinIso5167ValidityRange() {
    double p1 = stream.getPressure("Pa");
    double dp = getDifferentialPressurePa();
    if (p1 <= 0.0 || dp < 0.0) {
      return false;
    }
    return dp / p1 <= ISO_5167_4_MAX_PRESSURE_DROP_RATIO;
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
   * Holds the outcome of one wet-gas evaluation.
   */
  private static final class WetGasResult {
    /** Gas mass flow rate [kg/sec]. */
    private double gasMassFlowRate = Double.NaN;
    /** Lockhart-Martinelli parameter [-]. */
    private double lockhartMartinelli = 0.0;
    /** Gas densiometric Froude number [-]. */
    private double froudeNumber = Double.NaN;
    /** Throat Froude number [-]. */
    private double throatFroudeNumber = Double.NaN;
    /** Over-reading correction factor [-]. */
    private double overReading = 1.0;
    /** Chisholm coefficient [-]. */
    private double chisholmCoefficient = Double.NaN;
    /** Chisholm exponent [-]. */
    private double chisholmExponent = Double.NaN;
    /** Discharge coefficient actually used [-]. */
    private double dischargeCoefficient = Double.NaN;
    /** Upstream gas density [kg/m3]. */
    private double gasDensity = Double.NaN;
    /** Effective liquid density [kg/m3]. */
    private double liquidDensity = Double.NaN;
    /** Y/Ymax of the pressure-loss route, 0.0 when that route was not used. */
    private double pressureLossRatio = 0.0;
  }

  /**
   * Evaluates the dry-gas mass flow rate from the ISO 5167-1 general equation for a given discharge coefficient and
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
        getIsentropicExponent(), dischargeCoefficient, liquidMassFlowRate, resolveLiquidToGasMassRatio(),
        getLiquidDensity(), pressureLoss, gravitationalAcceleration, surfaceTensionFactor, wetGasCorrelation.ordinal(),
        useWetGasDischargeCoefficient ? 1.0 : 0.0 };
  }

  /**
   * Solves the ISO/TR 11583 wet-gas equations for the current operating point. Returns a dry-gas result when no wet-gas
   * correlation is selected or when no liquid load is available. Never throws.
   *
   * @return the wet-gas evaluation outcome
   */
  private WetGasResult computeWetGas() {
    WetGasResult result = new WetGasResult();
    double dp = getDifferentialPressurePa();
    double p1 = getUpstreamPressurePa();
    double beta = getBetaRatio();
    result.gasDensity = getGasDensity();
    result.dischargeCoefficient = dischargeCoefficient;

    if (dp <= 0.0) {
      result.gasMassFlowRate = 0.0;
      return result;
    }
    if (p1 <= 0.0 || result.gasDensity <= 0.0 || getThroatDiameter("m") <= 0.0 || dischargeCoefficient <= 0.0) {
      return result;
    }
    double epsilon = ExpansibilityModel.ISENTROPIC.calculate(dp, p1, beta, getIsentropicExponent());
    if (Double.isNaN(epsilon)) {
      return result;
    }

    double dryFlow = evaluateFlowEquation(dp, epsilon, beta, result.gasDensity, dischargeCoefficient, 1.0);
    if (wetGasCorrelation != WetGasCorrelation.ISO_TR_11583 && wetGasCorrelation != WetGasCorrelation.DE_LEEUW) {
      result.gasMassFlowRate = dryFlow;
      return result;
    }

    result.liquidDensity = getLiquidDensity();
    double massRatio = resolveLiquidToGasMassRatio();
    boolean hasAbsoluteLiquidRate = !Double.isNaN(liquidMassFlowRate);
    boolean hasLiquidRatio = !Double.isNaN(massRatio);
    // The 6.4.5 pressure-loss route is an ISO/TR 11583 construction; de Leeuw (1997) does not define it.
    boolean usePressureLossRoute = wetGasCorrelation == WetGasCorrelation.ISO_TR_11583 && !hasAbsoluteLiquidRate
        && !hasLiquidRatio && !Double.isNaN(pressureLoss) && pressureLoss > 0.0;
    if (Double.isNaN(result.liquidDensity) || result.liquidDensity <= 0.0
        || (!hasAbsoluteLiquidRate && !hasLiquidRatio && !usePressureLossRoute)) {
      // No usable liquid information: fall back to the dry-gas result.
      result.gasMassFlowRate = dryFlow;
      return result;
    }

    double densityRatioSqrt = Math.sqrt(result.gasDensity / result.liquidDensity);
    double flow = evaluateFlowEquation(dp, epsilon, beta, result.gasDensity, 1.0, 1.0);
    for (int iteration = 0; iteration < MAX_WET_GAS_ITERATIONS; iteration++) {
      double froude = calcFroudeNumber(flow, result.gasDensity, result.liquidDensity);
      double throatFroude = froude / Math.pow(beta, 2.5);
      double exponent = wetGasCorrelation == WetGasCorrelation.DE_LEEUW ? calcDeLeeuwExponent(froude)
          : calcChisholmExponent(beta, froude);
      double chisholm = Math.pow(result.liquidDensity / result.gasDensity, exponent)
          + Math.pow(result.gasDensity / result.liquidDensity, exponent);

      double x;
      if (usePressureLossRoute) {
        x = calcLockhartMartinelliFromPressureLoss(dp, beta, froude, result.gasDensity, result.liquidDensity, result);
      } else if (hasAbsoluteLiquidRate) {
        x = flow > 0.0 ? liquidMassFlowRate / flow * densityRatioSqrt : Double.NaN;
      } else {
        x = massRatio * densityRatioSqrt;
      }
      if (Double.isNaN(x)) {
        result.gasMassFlowRate = dryFlow;
        return result;
      }

      double coefficient;
      if (wetGasCorrelation == WetGasCorrelation.DE_LEEUW) {
        // de Leeuw (1997) applies the over-reading factor to the configured discharge coefficient only.
        coefficient = dischargeCoefficient;
      } else {
        coefficient = 1.0 - 0.0463 * Math.exp(-0.05 * throatFroude) * Math.min(1.0, Math.sqrt(x / 0.016));
        if (!useWetGasDischargeCoefficient) {
          coefficient = dischargeCoefficient;
        }
      }
      double overReading = Math.sqrt(1.0 + chisholm * x + x * x);
      double updated = evaluateFlowEquation(dp, epsilon, beta, result.gasDensity, coefficient, overReading);

      result.lockhartMartinelli = x;
      result.froudeNumber = froude;
      result.throatFroudeNumber = throatFroude;
      result.chisholmExponent = exponent;
      result.chisholmCoefficient = chisholm;
      result.overReading = overReading;
      result.dischargeCoefficient = coefficient;
      result.gasMassFlowRate = updated;

      if (Math.abs(updated - flow) <= WET_GAS_TOLERANCE * Math.abs(updated)) {
        return result;
      }
      flow = updated;
    }
    logger.warn("{}: wet-gas iteration did not converge for correlation {}", getName(), wetGasCorrelation);
    return result;
  }

  /**
   * Resolves the liquid-to-gas mass ratio from the configured ratio or from the stream phase split. An absolute liquid
   * rate is handled separately inside the iteration because it makes X depend on the gas rate.
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
   * Calculates the gas densiometric Froude number of ISO/TR 11583 Equation (3).
   *
   * @param gasMassFlowRate gas mass flow rate [kg/sec]
   * @param gasDensity upstream gas density [kg/m3]
   * @param liquidDensityValue liquid density [kg/m3]
   * @return Froude number [-]
   */
  private double calcFroudeNumber(double gasMassFlowRate, double gasDensity, double liquidDensityValue) {
    double densityDifference = liquidDensityValue - gasDensity;
    if (densityDifference <= 0.0) {
      return Double.NaN;
    }
    double pipeDiameterMeters = getPipeDiameter("m");
    double superficial = 4.0 * gasMassFlowRate
        / (Math.PI * pipeDiameterMeters * pipeDiameterMeters * Math.sqrt(gasDensity));
    return superficial / Math.sqrt(gravitationalAcceleration * pipeDiameterMeters * densityDifference);
  }

  /**
   * Calculates the de Leeuw (1997) exponent n from the gas densiometric Froude number (Steven, 2002, Eqs. (13a)-(13b)).
   * Unlike {@link #calcChisholmExponent(double, double)} this has no diameter-ratio term. Below the 0.5 lower bound of
   * the correlation the 0.41 plateau is extrapolated rather than returning NaN, so the meter never throws; the point is
   * reported by {@link #getValidityViolations()} instead.
   *
   * @param froude gas densiometric Froude number [-]
   * @return exponent n [-]
   */
  private double calcDeLeeuwExponent(double froude) {
    if (froude >= 1.5) {
      return 0.606 * (1.0 - Math.exp(-0.746 * froude));
    }
    return 0.41;
  }

  /**
   * Calculates the Chisholm exponent n of ISO/TR 11583 6.4.3.
   *
   * @param beta diameter ratio [-]
   * @param froude gas densiometric Froude number [-]
   * @return exponent n [-]
   */
  private double calcChisholmExponent(double beta, double froude) {
    double beta2 = beta * beta;
    return Math.max(0.583 - 0.18 * beta2 - 0.578 * Math.exp(-0.8 * froude / surfaceTensionFactor),
        0.392 - 0.18 * beta2);
  }

  /**
   * Derives the Lockhart-Martinelli parameter from the permanent pressure loss, per ISO/TR 11583 6.4.5. The route is
   * only defined for Y/Ymax below 0.65.
   *
   * @param dp differential pressure [Pa]
   * @param beta diameter ratio [-]
   * @param froude gas densiometric Froude number [-]
   * @param gasDensity upstream gas density [kg/m3]
   * @param liquidDensityValue liquid density [kg/m3]
   * @param result result holder receiving the Y/Ymax ratio
   * @return parameter X [-], or NaN when the route is not applicable
   */
  private double calcLockhartMartinelliFromPressureLoss(double dp, double beta, double froude, double gasDensity,
      double liquidDensityValue, WetGasResult result) {
    if (Double.isNaN(froude) || dp <= 0.0) {
      return Double.NaN;
    }
    double y = pressureLoss / dp - 0.0896 - 0.48 * Math.pow(beta, 9.0);
    double yMax = 0.61 * Math.exp(-11.0 * gasDensity / liquidDensityValue - 0.045 * froude / surfaceTensionFactor);
    if (yMax <= 0.0) {
      return Double.NaN;
    }
    double ratio = y / yMax;
    result.pressureLossRatio = ratio;
    if (ratio >= 0.65 || ratio <= 0.0) {
      return Double.NaN;
    }
    double numerator = -Math.log(1.0 - ratio);
    double denominator = 35.0 * Math.exp(-0.28 * froude / surfaceTensionFactor);
    if (denominator <= 0.0) {
      return Double.NaN;
    }
    return Math.pow(numerator / denominator, 4.0 / 3.0);
  }

  /**
   * Calculates the mass flow rate from the ISO 5167-1 general equation, including the ISO/TR 11583 or de Leeuw (1997)
   * wet-gas correction when selected.
   *
   * @return mass flow rate [kg/sec], 0.0 when the differential pressure is not positive and NaN when the inputs are not
   * physically valid
   */
  @Override
  protected double getMassFlowRatePerSecond() {
    return solveWetGas().gasMassFlowRate;
  }

  /**
   * Returns the configured discharge coefficient, unaffected by the pipe Reynolds number: a classical Venturi tube's
   * discharge coefficient is constant per ISO 5167-4.
   *
   * @param beta diameter ratio d/D [-], unused
   * @param reynoldsD pipe Reynolds number [-], unused
   * @return the configured discharge coefficient [-]
   */
  @Override
  protected double calcDischargeCoefficient(double beta, double reynoldsD) {
    return dischargeCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  protected String getDifferentialPressureFlowMeterTransientStateCoverageIssue() {
    if (getClass() != VenturiFlowMeter.class) {
      return "venturi-flow-meter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected Serializable captureDifferentialPressureFlowMeterExtensionState() {
    return new VenturiFlowMeterState(dischargeCoefficient, wetGasCorrelation, liquidMassFlowRate, liquidToGasMassRatio,
        liquidDensity, liquidFromStream, surfaceTensionFactor, gravitationalAcceleration, pressureLoss,
        useWetGasDischargeCoefficient);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreDifferentialPressureFlowMeterExtensionState(Serializable extensionState) {
    if (!(extensionState instanceof VenturiFlowMeterState)) {
      throw new IllegalArgumentException("Venturi flow-meter extension snapshot has the wrong type");
    }
    VenturiFlowMeterState state = (VenturiFlowMeterState) extensionState;
    dischargeCoefficient = state.dischargeCoefficient;
    wetGasCorrelation = state.wetGasCorrelation;
    liquidMassFlowRate = state.liquidMassFlowRate;
    liquidToGasMassRatio = state.liquidToGasMassRatio;
    liquidDensity = state.liquidDensity;
    liquidFromStream = state.liquidFromStream;
    surfaceTensionFactor = state.surfaceTensionFactor;
    gravitationalAcceleration = state.gravitationalAcceleration;
    pressureLoss = state.pressureLoss;
    useWetGasDischargeCoefficient = state.useWetGasDischargeCoefficient;
    cachedWetGasResult = null;
    cachedWetGasSignature = null;
  }

  /** Immutable Venturi-specific rollback point. Derived wet-gas caches are invalidated on restore. */
  private static final class VenturiFlowMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double dischargeCoefficient;
    private final WetGasCorrelation wetGasCorrelation;
    private final double liquidMassFlowRate;
    private final double liquidToGasMassRatio;
    private final double liquidDensity;
    private final boolean liquidFromStream;
    private final double surfaceTensionFactor;
    private final double gravitationalAcceleration;
    private final double pressureLoss;
    private final boolean useWetGasDischargeCoefficient;

    private VenturiFlowMeterState(double dischargeCoefficient, WetGasCorrelation wetGasCorrelation,
        double liquidMassFlowRate, double liquidToGasMassRatio, double liquidDensity, boolean liquidFromStream,
        double surfaceTensionFactor, double gravitationalAcceleration, double pressureLoss,
        boolean useWetGasDischargeCoefficient) {
      this.dischargeCoefficient = dischargeCoefficient;
      this.wetGasCorrelation = wetGasCorrelation;
      this.liquidMassFlowRate = liquidMassFlowRate;
      this.liquidToGasMassRatio = liquidToGasMassRatio;
      this.liquidDensity = liquidDensity;
      this.liquidFromStream = liquidFromStream;
      this.surfaceTensionFactor = surfaceTensionFactor;
      this.gravitationalAcceleration = gravitationalAcceleration;
      this.pressureLoss = pressureLoss;
      this.useWetGasDischargeCoefficient = useWetGasDischargeCoefficient;
    }
  }

  /** {@inheritDoc} */
  @Override
  protected ExpansibilityModel getExpansibilityModel() {
    return ExpansibilityModel.ISENTROPIC;
  }
}
