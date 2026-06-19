package neqsim.standards.oilquality;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ASTM D86 - Standard Test Method for Distillation of Petroleum Products and Liquid Fuels at Atmospheric Pressure.
 *
 * <p>
 * The atmospheric distillation curve is generated with a series of Pressure-Vapor-Fraction (PVF) flash calculations at
 * constant atmospheric pressure: pressure is held fixed and temperature is solved for each target recovered fraction.
 * The resulting curve maps percent distilled to boiling temperature.
 * </p>
 *
 * <p>
 * The recovered fraction can be interpreted on three bases (see {@link D86Basis}): molar vapor fraction (default),
 * recovered liquid-volume fraction, or an empirical TBP&rarr;D86 conversion (Riazi&ndash;Daubert / API). The class also
 * derives the standard average boiling points (VABP, MABP, WABP, CABP, MeABP), the Watson (UOP) characterization
 * factor, the D86 slope, and a recovery/loss/residue split, and supports a Sydney Young barometric-pressure correction.
 * </p>
 *
 * <p>
 * This is the primary specification used for characterising crude oil cuts, gasoline, jet fuel, diesel, and fuel oils
 * for refinery planning and product quality.
 * </p>
 *
 * <p>
 * Calculated properties:
 * </p>
 * <ul>
 * <li>IBP (Initial Boiling Point)</li>
 * <li>T5, T10, T50, T90, T95 (temperatures at 5, 10, 50, 90, 95 vol% distilled)</li>
 * <li>FBP (Final Boiling Point)</li>
 * <li>VABP, MABP, WABP, CABP, MeABP (average boiling points)</li>
 * <li>Watson (UOP) characterization factor and D86 slope</li>
 * <li>Recovery, loss and residue percentages</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D86 standard = new Standard_ASTM_D86(oilFluid);
 * standard.calculate();
 * double ibp = standard.getValue("IBP", "C");
 * double t50 = standard.getValue("T50", "C");
 * double fbp = standard.getValue("FBP", "C");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 2.0
 */
public class Standard_ASTM_D86 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D86.class);

  /** Number of distillation points to calculate. */
  private int numberOfPoints = 20;

  /** Volume fractions to evaluate (0 to 1). */
  private double[] volumeFractions;

  /** Temperatures at each volume fraction in Kelvin. */
  private double[] temperatures;

  /** Initial Boiling Point in Kelvin. */
  private double IBP = Double.NaN;

  /** Final Boiling Point in Kelvin. */
  private double FBP = Double.NaN;

  /** Distillation pressure in bara (standard atmospheric). */
  private double distillationPressure = 1.01325;

  /** Residue volume fraction (undistilled). */
  private double residueFraction = 0.0;

  /** Loss volume fraction (light ends boiling below the IBP). */
  private double lossFraction = 0.0;

  /** Recovered liquid-volume fraction at each evaluated point (for liquid-volume basis). */
  private double[] liquidVolumeFractions;

  /** Barometric (ambient) pressure for the Sydney Young correction, in mmHg. */
  private double barometricPressureMmHg = 760.0;

  /** Reporting basis for the recovered fraction. */
  private D86Basis basis = D86Basis.MOLAR;

  /** Optional product specification limits (point key -&gt; max temperature in Celsius). */
  private final Map<String, Double> specLimitsC = new LinkedHashMap<String, Double>();

  /** Volume-percent breakpoints for the Riazi-Daubert ASTM D86 &harr; TBP interconversion. */
  private static final double[] CONV_PCT = { 0.0, 10.0, 30.0, 50.0, 70.0, 90.0, 95.0 };

  /** Coefficient a for T_TBP = a (T_D86)^b at each breakpoint (Kelvin). */
  private static final double[] CONV_A = { 0.9177, 0.5564, 0.7617, 0.9013, 0.8821, 0.9552, 0.8177 };

  /** Exponent b for T_TBP = a (T_D86)^b at each breakpoint (Kelvin). */
  private static final double[] CONV_B = { 1.0019, 1.0900, 1.0425, 1.0176, 1.0226, 1.0110, 1.0355 };

  /**
   * Reporting basis for the recovered (distilled) fraction of an ASTM D86 curve.
   */
  public enum D86Basis {
    /** Recovered fraction interpreted as the molar vapor fraction (default, EOS equilibrium). */
    MOLAR,
    /** Recovered fraction interpreted as the condensed liquid-volume fraction. */
    LIQUID_VOLUME,
    /** Molar equilibrium (TBP-like) curve converted to ASTM D86 via Riazi-Daubert. */
    TBP_CONVERTED
  }

  /**
   * Constructor for Standard_ASTM_D86.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D86(SystemInterface thermoSystem) {
    super("Standard_ASTM_D86", "ASTM D86 - Distillation of Petroleum Products at Atmospheric " + "Pressure",
	thermoSystem);
    initVolumeFractions();
  }

  /**
   * Initializes the default volume fraction points for the distillation curve.
   */
  private void initVolumeFractions() {
    numberOfPoints = 20;
    volumeFractions = new double[numberOfPoints];
    temperatures = new double[numberOfPoints];
    liquidVolumeFractions = new double[numberOfPoints];
    // IBP(~0.5%), 5%, 10%, 15%, 20%, 25%, 30%, 35%, 40%, 45%, 50%,
    // 55%, 60%, 65%, 70%, 75%, 80%, 85%, 90%, 95%
    double[] fracs = { 0.005, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75,
	0.80, 0.85, 0.90, 0.95 };
    for (int i = 0; i < numberOfPoints; i++) {
      volumeFractions[i] = fracs[i];
      temperatures[i] = Double.NaN;
      liquidVolumeFractions[i] = Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    // The distillation curve maps the recovered (distilled) fraction to the temperature at which
    // that fraction has evaporated, all at constant atmospheric pressure. The correct flash is
    // therefore a Pressure-Vapor-Fraction (PVF) flash that holds pressure fixed and solves for the
    // temperature giving the target vapor fraction. The recovered fraction is interpreted as the
    // molar vapor fraction (the standard simplification used for boiling-point curves).

    // Calculate IBP via bubble point (vapor fraction -> 0) at atmospheric pressure.
    try {
      SystemInterface ibpFluid = thermoSystem.clone();
      ibpFluid.setPressure(distillationPressure);
      ibpFluid.setTemperature(273.15 + 20.0);
      thermoOps = new ThermodynamicOperations(ibpFluid);
      thermoOps.bubblePointTemperatureFlash();
      IBP = ibpFluid.getTemperature();
      temperatures[0] = IBP;
    } catch (Exception ex) {
      logger.error("Failed to calculate IBP: {}", ex.getMessage());
      return;
    }

    // Pre-compute feed component data for the liquid-volume basis and the recovery/loss split.
    int nComp = thermoSystem.getNumberOfComponents();
    double[] vmFeed = new double[nComp];
    double[] zFeed = new double[nComp];
    double sumVfeed = 0.0;
    for (int c = 0; c < nComp; c++) {
      ComponentInterface comp = thermoSystem.getComponent(c);
      zFeed[c] = comp.getz();
      double rho = 0.0;
      try {
	rho = comp.getNormalLiquidDensity("kg/m3");
      } catch (Exception e) {
	rho = 0.0;
      }
      vmFeed[c] = (rho > 1.0e-6) ? comp.getMolarMass() / rho : 0.0;
      sumVfeed += zFeed[c] * vmFeed[c];
    }
    liquidVolumeFractions[0] = 0.0;

    // Calculate temperatures at each recovered fraction via a Pressure-Vapor-Fraction flash.
    // Reuse a single fluid and march upward in fraction so each solve warm-starts from the
    // previous (lower-temperature) solution.
    SystemInterface flashFluid = thermoSystem.clone();
    flashFluid.setPressure(distillationPressure);
    flashFluid.setTemperature(IBP);
    ThermodynamicOperations flashOps = new ThermodynamicOperations(flashFluid);
    for (int i = 1; i < numberOfPoints; i++) {
      try {
	flashOps.PVFflash(volumeFractions[i]);
	double t = flashFluid.getTemperature();
	if (Double.isNaN(t) || Double.isInfinite(t)) {
	  temperatures[i] = Double.NaN;
	  liquidVolumeFractions[i] = Double.NaN;
	} else {
	  temperatures[i] = t;
	  liquidVolumeFractions[i] = computeRecoveredLiquidVolume(flashFluid, volumeFractions[i], vmFeed, sumVfeed,
	      nComp);
	}
      } catch (Exception ex) {
	logger.debug("PVF flash failed at {}%: {}", volumeFractions[i] * 100.0, ex.getMessage());
	temperatures[i] = Double.NaN;
	liquidVolumeFractions[i] = Double.NaN;
      }
    }

    // Calculate FBP via dew point (vapor fraction -> 1) at atmospheric pressure.
    try {
      SystemInterface dewFluid = thermoSystem.clone();
      dewFluid.setPressure(distillationPressure);
      dewFluid.setTemperature(273.15 + 200.0);
      ThermodynamicOperations dewOps = new ThermodynamicOperations(dewFluid);
      dewOps.dewPointTemperatureFlash();
      FBP = dewFluid.getTemperature();
    } catch (Exception ex) {
      logger.debug("Failed to calculate FBP: {}", ex.getMessage());
      FBP = Double.NaN;
    }

    // Split the unrecovered fraction into light-ends loss and heavy residue.
    computeRecoveryLossResidue(zFeed, vmFeed, sumVfeed, nComp);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
    case "IBP":
      return reportedIBP();
    case "T5":
      return reportedTemperatureAtFractionK(0.05) - 273.15;
    case "T10":
      return reportedTemperatureAtFractionK(0.10) - 273.15;
    case "T50":
      return reportedTemperatureAtFractionK(0.50) - 273.15;
    case "T90":
      return reportedTemperatureAtFractionK(0.90) - 273.15;
    case "T95":
      return reportedTemperatureAtFractionK(0.95) - 273.15;
    case "FBP":
      return reportedFBP();
    case "residue":
      return residueFraction;
    case "loss":
      return lossFraction;
    case "VABP":
      return getVABP();
    case "MABP":
      return getMABP();
    case "WABP":
      return getWABP();
    case "CABP":
      return getCABP();
    case "MeABP":
      return getMeABP();
    case "slope":
      return getSlope();
    case "WatsonK":
    case "watsonK":
    case "UOPK":
      return getWatsonK();
    default:
      // Try interpreting as "Txx" where xx is percentage
      if (returnParameter.startsWith("T") && returnParameter.length() > 1) {
	try {
	  double pct = Double.parseDouble(returnParameter.substring(1));
	  return reportedTemperatureAtFractionK(pct / 100.0) - 273.15;
	} catch (NumberFormatException e) {
	  logger.error("Unsupported parameter: {}", returnParameter);
	}
      }
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double valueC = getValue(returnParameter);
    if (Double.isNaN(valueC)) {
      return Double.NaN;
    }
    // Dimensionless or non-temperature outputs are returned unchanged regardless of unit.
    if ("WatsonK".equalsIgnoreCase(returnParameter) || "watsonK".equalsIgnoreCase(returnParameter)
	|| "UOPK".equalsIgnoreCase(returnParameter) || "residue".equalsIgnoreCase(returnParameter)
	|| "loss".equalsIgnoreCase(returnParameter) || "slope".equalsIgnoreCase(returnParameter)) {
      return valueC;
    }
    if ("K".equalsIgnoreCase(returnUnit)) {
      return valueC + 273.15;
    } else if ("F".equalsIgnoreCase(returnUnit)) {
      return valueC * 9.0 / 5.0 + 32.0;
    } else if ("R".equalsIgnoreCase(returnUnit)) {
      return (valueC + 273.15) * 9.0 / 5.0;
    }
    // Default: Celsius
    return valueC;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return "C";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (specLimitsC.isEmpty()) {
      return !Double.isNaN(IBP);
    }
    for (Map.Entry<String, Double> entry : specLimitsC.entrySet()) {
      double value = getValue(entry.getKey());
      if (Double.isNaN(value) || value > entry.getValue()) {
	return false;
      }
    }
    return true;
  }

  /**
   * Linear interpolation helper for a monotonically increasing fraction axis.
   *
   * @param fractionAxis the recovered-fraction values (x-axis)
   * @param temps the temperatures in Kelvin (y-axis)
   * @param fraction the recovered fraction to interpolate at (0.0 to 1.0)
   * @return interpolated temperature in Kelvin
   */
  private double interpolateK(double[] fractionAxis, double[] temps, double fraction) {
    if (fraction <= fractionAxis[0]) {
      return temps[0];
    }
    for (int i = 1; i < numberOfPoints; i++) {
      if (Double.isNaN(temps[i]) || Double.isNaN(temps[i - 1]) || Double.isNaN(fractionAxis[i])
	  || Double.isNaN(fractionAxis[i - 1])) {
	continue;
      }
      if (fraction <= fractionAxis[i]) {
	double dx = fractionAxis[i] - fractionAxis[i - 1];
	if (Math.abs(dx) < 1.0e-12) {
	  return temps[i];
	}
	double x = (fraction - fractionAxis[i - 1]) / dx;
	return temps[i - 1] + x * (temps[i] - temps[i - 1]);
      }
    }
    return temps[numberOfPoints - 1];
  }

  /**
   * Gets the temperature at a given distilled fraction on the molar (simulated) basis by linear interpolation.
   *
   * @param fraction the recovered fraction (0.0 to 1.0)
   * @return temperature in Kelvin at the specified fraction
   */
  private double getTemperatureAtFraction(double fraction) {
    return interpolateK(volumeFractions, temperatures, fraction);
  }

  /**
   * Gets the temperature at a given recovered liquid-volume fraction by interpolation.
   *
   * @param fraction the recovered liquid-volume fraction (0.0 to 1.0)
   * @return temperature in Kelvin at the specified fraction
   */
  private double liquidVolumeTemperatureAtFractionK(double fraction) {
    return interpolateK(liquidVolumeFractions, temperatures, fraction);
  }

  /**
   * Returns the reported temperature at a recovered fraction, honouring the active reporting basis and any
   * barometric-pressure correction.
   *
   * @param fraction the recovered fraction (0.0 to 1.0)
   * @return temperature in Kelvin
   */
  private double reportedTemperatureAtFractionK(double fraction) {
    double tk;
    switch (basis) {
    case LIQUID_VOLUME:
      tk = liquidVolumeTemperatureAtFractionK(fraction);
      break;
    case TBP_CONVERTED:
      tk = tbpToD86K(getTemperatureAtFraction(fraction), fraction * 100.0);
      break;
    default:
      tk = getTemperatureAtFraction(fraction);
      break;
    }
    return applyBarometricCorrectionK(tk);
  }

  /**
   * Reported Initial Boiling Point honouring basis and barometric correction.
   *
   * @return IBP in Celsius
   */
  private double reportedIBP() {
    if (Double.isNaN(IBP)) {
      return Double.NaN;
    }
    double tk = (basis == D86Basis.TBP_CONVERTED) ? tbpToD86K(IBP, 0.0) : IBP;
    return applyBarometricCorrectionK(tk) - 273.15;
  }

  /**
   * Reported Final Boiling Point honouring basis and barometric correction.
   *
   * @return FBP in Celsius
   */
  private double reportedFBP() {
    if (Double.isNaN(FBP)) {
      return Double.NaN;
    }
    double tk = (basis == D86Basis.TBP_CONVERTED) ? tbpToD86K(FBP, 100.0) : FBP;
    return applyBarometricCorrectionK(tk) - 273.15;
  }

  /**
   * Applies the Sydney Young barometric-pressure correction used by ASTM D86 to convert an observed temperature to the
   * equivalent at 760 mmHg.
   *
   * @param temperatureK the observed temperature in Kelvin
   * @return corrected temperature in Kelvin
   */
  private double applyBarometricCorrectionK(double temperatureK) {
    if (Double.isNaN(temperatureK) || Math.abs(barometricPressureMmHg - 760.0) < 1.0e-9) {
      return temperatureK;
    }
    double tC = temperatureK - 273.15;
    double cc = 0.00012 * (760.0 - barometricPressureMmHg) * (273.15 + tC);
    return temperatureK + cc;
  }

  /**
   * Interpolates the Riazi-Daubert interconversion coefficients (a, b) at a given volume percent.
   *
   * @param percent the volume percent distilled (0 to 100)
   * @return a two-element array {a, b}
   */
  private double[] conversionCoefficients(double percent) {
    int last = CONV_PCT.length - 1;
    if (percent <= CONV_PCT[0]) {
      return new double[] { CONV_A[0], CONV_B[0] };
    }
    if (percent >= CONV_PCT[last]) {
      return new double[] { CONV_A[last], CONV_B[last] };
    }
    for (int i = 1; i < CONV_PCT.length; i++) {
      if (percent <= CONV_PCT[i]) {
	double t = (percent - CONV_PCT[i - 1]) / (CONV_PCT[i] - CONV_PCT[i - 1]);
	double a = CONV_A[i - 1] + t * (CONV_A[i] - CONV_A[i - 1]);
	double b = CONV_B[i - 1] + t * (CONV_B[i] - CONV_B[i - 1]);
	return new double[] { a, b };
      }
    }
    return new double[] { CONV_A[last], CONV_B[last] };
  }

  /**
   * Converts a TBP temperature to the equivalent ASTM D86 temperature using the Riazi-Daubert relation T_TBP = a
   * (T_D86)^b (temperatures in Kelvin).
   *
   * @param tbpK TBP temperature in Kelvin
   * @param percent the volume percent distilled (0 to 100)
   * @return ASTM D86 temperature in Kelvin
   */
  private double tbpToD86K(double tbpK, double percent) {
    if (Double.isNaN(tbpK) || tbpK <= 0.0) {
      return tbpK;
    }
    double[] ab = conversionCoefficients(percent);
    return Math.pow(tbpK / ab[0], 1.0 / ab[1]);
  }

  /**
   * Converts an ASTM D86 temperature to the equivalent TBP temperature using the Riazi-Daubert relation T_TBP = a
   * (T_D86)^b (temperatures in Kelvin).
   *
   * @param d86K ASTM D86 temperature in Kelvin
   * @param percent the volume percent distilled (0 to 100)
   * @return TBP temperature in Kelvin
   */
  private double d86ToTbpK(double d86K, double percent) {
    if (Double.isNaN(d86K) || d86K <= 0.0) {
      return d86K;
    }
    double[] ab = conversionCoefficients(percent);
    return ab[0] * Math.pow(d86K, ab[1]);
  }

  /**
   * Computes the cumulative recovered liquid-volume fraction for the vapour formed at a flash point.
   *
   * @param fluid the flashed fluid holding gas and liquid phases
   * @param beta the molar vapor fraction at this point
   * @param vmFeed per-component liquid molar volume proxy (molar mass / liquid density)
   * @param sumVfeed the feed total liquid-volume proxy
   * @param nComp the number of components
   * @return recovered liquid-volume fraction (0.0 to 1.0)
   */
  private double computeRecoveredLiquidVolume(SystemInterface fluid, double beta, double[] vmFeed, double sumVfeed,
      int nComp) {
    if (sumVfeed <= 0.0) {
      return beta;
    }
    try {
      PhaseInterface gas = fluid.getPhase("gas");
      double sumVgas = 0.0;
      for (int c = 0; c < nComp; c++) {
	sumVgas += gas.getComponent(c).getx() * vmFeed[c];
      }
      double lv = beta * sumVgas / sumVfeed;
      if (lv < 0.0) {
	lv = 0.0;
      }
      if (lv > 1.0) {
	lv = 1.0;
      }
      return lv;
    } catch (Exception e) {
      return beta;
    }
  }

  /**
   * Splits the unrecovered fraction into a light-ends loss (components boiling below the IBP) and a heavy residue, on a
   * liquid-volume basis, so that recovery + loss + residue = 100%.
   *
   * @param zFeed per-component overall mole fractions
   * @param vmFeed per-component liquid molar volume proxy
   * @param sumVfeed the feed total liquid-volume proxy
   * @param nComp the number of components
   */
  private void computeRecoveryLossResidue(double[] zFeed, double[] vmFeed, double sumVfeed, int nComp) {
    double lossVol = 0.0;
    if (sumVfeed > 0.0 && !Double.isNaN(IBP)) {
      for (int c = 0; c < nComp; c++) {
	double tb = thermoSystem.getComponent(c).getNormalBoilingPoint();
	if (tb < IBP) {
	  lossVol += zFeed[c] * vmFeed[c];
	}
      }
      lossVol /= sumVfeed;
    }
    double maxFraction = volumeFractions[numberOfPoints - 1];
    double notRecovered = Math.max(0.0, 1.0 - maxFraction);
    lossFraction = Math.min(Math.max(0.0, lossVol), notRecovered);
    residueFraction = Math.max(0.0, notRecovered - lossFraction);
  }

  /**
   * Computes the component-based average boiling points and specific gravity from the fluid normal boiling points and
   * liquid densities.
   *
   * @return a four-element array {MABP_K, WABP_K, CABP_K, SG}
   */
  private double[] computeABPs() {
    int n = thermoSystem.getNumberOfComponents();
    double sumZ = 0.0;
    for (int i = 0; i < n; i++) {
      sumZ += thermoSystem.getComponent(i).getz();
    }
    if (sumZ <= 0.0) {
      sumZ = 1.0;
    }
    double mabp = 0.0;
    double sumMass = 0.0;
    double wabpNum = 0.0;
    double sumVol = 0.0;
    double cabpNum = 0.0;
    for (int i = 0; i < n; i++) {
      ComponentInterface c = thermoSystem.getComponent(i);
      double z = c.getz() / sumZ;
      double tb = c.getNormalBoilingPoint();
      double mass = z * c.getMolarMass();
      mabp += z * tb;
      sumMass += mass;
      wabpNum += mass * tb;
      double rho = 0.0;
      try {
	rho = c.getNormalLiquidDensity("kg/m3");
      } catch (Exception e) {
	rho = 0.0;
      }
      if (rho > 1.0e-6) {
	double vol = mass / rho;
	sumVol += vol;
	cabpNum += vol * Math.cbrt(tb);
      }
    }
    double wabp = (sumMass > 0.0) ? wabpNum / sumMass : Double.NaN;
    double cabp = (sumVol > 0.0) ? Math.pow(cabpNum / sumVol, 3.0) : Double.NaN;
    double sg = (sumVol > 0.0) ? (sumMass / sumVol) / 999.016 : Double.NaN;
    return new double[] { mabp, wabp, cabp, sg };
  }

  /**
   * Converts a temperature in Celsius to the requested unit.
   *
   * @param valueC the temperature in Celsius
   * @param unit the target unit ("C", "K", "F" or "R")
   * @return the converted temperature
   */
  private double convertTempFromC(double valueC, String unit) {
    if (Double.isNaN(valueC)) {
      return Double.NaN;
    }
    if ("K".equalsIgnoreCase(unit)) {
      return valueC + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      return valueC * 9.0 / 5.0 + 32.0;
    } else if ("R".equalsIgnoreCase(unit)) {
      return (valueC + 273.15) * 9.0 / 5.0;
    }
    return valueC;
  }

  /**
   * Returns the full distillation curve data on the active reporting basis.
   *
   * @return a two-dimensional array where [i][0] is volume percent and [i][1] is temperature in Celsius
   */
  public double[][] getDistillationCurve() {
    double[][] curve = new double[numberOfPoints][2];
    for (int i = 0; i < numberOfPoints; i++) {
      curve[i][0] = volumeFractions[i] * 100.0;
      curve[i][1] = reportedTemperatureAtFractionK(volumeFractions[i]) - 273.15;
    }
    return curve;
  }

  /**
   * Returns the full distillation curve data on the active reporting basis in a chosen unit.
   *
   * @param tempUnit the temperature unit ("C", "K", "F" or "R")
   * @return a two-dimensional array where [i][0] is volume percent and [i][1] is temperature in the requested unit
   */
  public double[][] getDistillationCurve(String tempUnit) {
    double[][] curve = new double[numberOfPoints][2];
    for (int i = 0; i < numberOfPoints; i++) {
      curve[i][0] = volumeFractions[i] * 100.0;
      double tc = reportedTemperatureAtFractionK(volumeFractions[i]) - 273.15;
      curve[i][1] = convertTempFromC(tc, tempUnit);
    }
    return curve;
  }

  /**
   * Returns the full distillation curve data on the active reporting basis in Kelvin.
   *
   * @return a two-dimensional array where [i][0] is volume percent and [i][1] is temperature in Kelvin
   */
  public double[][] getDistillationCurveKelvin() {
    double[][] curve = new double[numberOfPoints][2];
    for (int i = 0; i < numberOfPoints; i++) {
      curve[i][0] = volumeFractions[i] * 100.0;
      curve[i][1] = reportedTemperatureAtFractionK(volumeFractions[i]);
    }
    return curve;
  }

  /**
   * Returns the simulated molar-equilibrium (TBP-like) distillation curve, independent of the active reporting basis.
   *
   * @return a two-dimensional array where [i][0] is volume percent and [i][1] is temperature in Celsius
   */
  public double[][] getTBPCurve() {
    double[][] curve = new double[numberOfPoints][2];
    for (int i = 0; i < numberOfPoints; i++) {
      curve[i][0] = volumeFractions[i] * 100.0;
      curve[i][1] = Double.isNaN(temperatures[i]) ? Double.NaN : temperatures[i] - 273.15;
    }
    return curve;
  }

  /**
   * Returns the ASTM D86 curve obtained by converting the simulated molar-equilibrium (TBP-like) curve with the
   * Riazi-Daubert TBP&rarr;D86 relation.
   *
   * @return a two-dimensional array where [i][0] is volume percent and [i][1] is temperature in Celsius
   */
  public double[][] getD86Curve() {
    double[][] curve = new double[numberOfPoints][2];
    for (int i = 0; i < numberOfPoints; i++) {
      double pct = volumeFractions[i] * 100.0;
      curve[i][0] = pct;
      curve[i][1] = Double.isNaN(temperatures[i]) ? Double.NaN : tbpToD86K(temperatures[i], pct) - 273.15;
    }
    return curve;
  }

  /**
   * Gets the distillation pressure.
   *
   * @return distillation pressure in bara
   */
  public double getDistillationPressure() {
    return distillationPressure;
  }

  /**
   * Sets the distillation pressure. Default is 1.01325 bara (standard atmospheric).
   *
   * @param pressure distillation pressure in bara
   */
  public void setDistillationPressure(double pressure) {
    this.distillationPressure = pressure;
  }

  /**
   * Gets the active reporting basis.
   *
   * @return the {@link D86Basis} currently used for reporting
   */
  public D86Basis getBasis() {
    return basis;
  }

  /**
   * Sets the reporting basis for the recovered fraction.
   *
   * @param basis the {@link D86Basis} to use; if null, defaults to MOLAR
   */
  public void setBasis(D86Basis basis) {
    this.basis = (basis == null) ? D86Basis.MOLAR : basis;
  }

  /**
   * Sets the barometric (ambient) pressure used for the Sydney Young correction.
   *
   * @param pressure the barometric pressure
   * @param unit the unit ("mmHg", "bar", "bara", "kPa", "Pa", "atm" or "psia")
   */
  public void setBarometricPressure(double pressure, String unit) {
    double mmHg;
    if ("mmHg".equalsIgnoreCase(unit)) {
      mmHg = pressure;
    } else if ("bar".equalsIgnoreCase(unit) || "bara".equalsIgnoreCase(unit)) {
      mmHg = pressure * 750.06157585;
    } else if ("kPa".equalsIgnoreCase(unit)) {
      mmHg = pressure * 7.5006157585;
    } else if ("Pa".equalsIgnoreCase(unit)) {
      mmHg = pressure * 0.0075006157585;
    } else if ("atm".equalsIgnoreCase(unit)) {
      mmHg = pressure * 760.0;
    } else if ("psia".equalsIgnoreCase(unit)) {
      mmHg = pressure * 51.714932572;
    } else {
      throw new RuntimeException("unit not supported " + unit);
    }
    this.barometricPressureMmHg = mmHg;
  }

  /**
   * Gets the barometric pressure used for the Sydney Young correction.
   *
   * @return barometric pressure in mmHg
   */
  public double getBarometricPressure() {
    return barometricPressureMmHg;
  }

  /**
   * Adds or updates a product specification limit checked by {@link #isOnSpec()}.
   *
   * @param pointKey the report point (e.g. "T90", "FBP", "IBP")
   * @param maxTemperatureC the maximum allowed temperature in Celsius
   */
  public void setSpecLimit(String pointKey, double maxTemperatureC) {
    specLimitsC.put(pointKey, maxTemperatureC);
  }

  /**
   * Clears all product specification limits.
   */
  public void clearSpecLimits() {
    specLimitsC.clear();
  }

  /**
   * Gets the volume average boiling point (VABP) from the reported curve, defined as the average of the temperatures at
   * 10, 30, 50, 70 and 90 percent recovered.
   *
   * @return VABP in Celsius
   */
  public double getVABP() {
    double t10 = reportedTemperatureAtFractionK(0.10);
    double t30 = reportedTemperatureAtFractionK(0.30);
    double t50 = reportedTemperatureAtFractionK(0.50);
    double t70 = reportedTemperatureAtFractionK(0.70);
    double t90 = reportedTemperatureAtFractionK(0.90);
    return (t10 + t30 + t50 + t70 + t90) / 5.0 - 273.15;
  }

  /**
   * Gets the volume average boiling point (VABP) in a chosen unit.
   *
   * @param unit the temperature unit ("C", "K", "F" or "R")
   * @return VABP in the requested unit
   */
  public double getVABP(String unit) {
    return convertTempFromC(getVABP(), unit);
  }

  /**
   * Gets the D86 slope, defined as (T90 - T10) / 80, in Celsius per percent.
   *
   * @return the D86 slope in Celsius per percent
   */
  public double getSlope() {
    double t10 = reportedTemperatureAtFractionK(0.10);
    double t90 = reportedTemperatureAtFractionK(0.90);
    return (t90 - t10) / 80.0;
  }

  /**
   * Gets the molal average boiling point (MABP) from the component normal boiling points.
   *
   * @return MABP in Celsius
   */
  public double getMABP() {
    return computeABPs()[0] - 273.15;
  }

  /**
   * Gets the molal average boiling point (MABP) in a chosen unit.
   *
   * @param unit the temperature unit ("C", "K", "F" or "R")
   * @return MABP in the requested unit
   */
  public double getMABP(String unit) {
    return convertTempFromC(getMABP(), unit);
  }

  /**
   * Gets the weight average boiling point (WABP) from the component normal boiling points.
   *
   * @return WABP in Celsius
   */
  public double getWABP() {
    return computeABPs()[1] - 273.15;
  }

  /**
   * Gets the weight average boiling point (WABP) in a chosen unit.
   *
   * @param unit the temperature unit ("C", "K", "F" or "R")
   * @return WABP in the requested unit
   */
  public double getWABP(String unit) {
    return convertTempFromC(getWABP(), unit);
  }

  /**
   * Gets the cubic average boiling point (CABP) from the component normal boiling points.
   *
   * @return CABP in Celsius
   */
  public double getCABP() {
    return computeABPs()[2] - 273.15;
  }

  /**
   * Gets the cubic average boiling point (CABP) in a chosen unit.
   *
   * @param unit the temperature unit ("C", "K", "F" or "R")
   * @return CABP in the requested unit
   */
  public double getCABP(String unit) {
    return convertTempFromC(getCABP(), unit);
  }

  /**
   * Gets the mean average boiling point (MeABP), defined as the mean of MABP and CABP.
   *
   * @return MeABP in Celsius
   */
  public double getMeABP() {
    double[] abps = computeABPs();
    return (abps[0] + abps[2]) / 2.0 - 273.15;
  }

  /**
   * Gets the mean average boiling point (MeABP) in a chosen unit.
   *
   * @param unit the temperature unit ("C", "K", "F" or "R")
   * @return MeABP in the requested unit
   */
  public double getMeABP(String unit) {
    return convertTempFromC(getMeABP(), unit);
  }

  /**
   * Gets the specific gravity (60/60 F) estimated as an ideal-solution mixture of the component liquid densities.
   *
   * @return specific gravity (dimensionless)
   */
  public double getSpecificGravity() {
    return computeABPs()[3];
  }

  /**
   * Gets the Watson (UOP) characterization factor, K = (MeABP in Rankine)^(1/3) / SG.
   *
   * @return the Watson characterization factor (dimensionless)
   */
  public double getWatsonK() {
    double[] abps = computeABPs();
    double meabpK = (abps[0] + abps[2]) / 2.0;
    double sg = abps[3];
    if (Double.isNaN(meabpK) || Double.isNaN(sg) || sg <= 0.0) {
      return Double.NaN;
    }
    return Math.cbrt(meabpK * 1.8) / sg;
  }

  /**
   * Gets the recovered percentage (the highest evaluated recovered point).
   *
   * @return percent recovered
   */
  public double getPercentRecovered() {
    return volumeFractions[numberOfPoints - 1] * 100.0;
  }

  /**
   * Gets the light-ends loss percentage (components boiling below the IBP).
   *
   * @return percent loss
   */
  public double getPercentLoss() {
    return lossFraction * 100.0;
  }

  /**
   * Gets the heavy residue percentage left in the flask.
   *
   * @return percent residue
   */
  public double getPercentResidue() {
    return residueFraction * 100.0;
  }
}
