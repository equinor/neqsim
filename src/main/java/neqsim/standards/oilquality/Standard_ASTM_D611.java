package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Estimated Aniline Point - ASTM D611 (screening correlation).
 *
 * <p>
 * The aniline point is the lowest temperature at which equal volumes of the oil and aniline are
 * completely miscible. It is an inverse measure of the aromatic content of a distillate: paraffinic
 * (high Watson K) fuels have high aniline points, aromatic stocks have low aniline points. It feeds
 * the Diesel Index and is a component of jet-fuel and solvent quality control.
 * </p>
 *
 * <p>
 * <b>This class returns an estimate, not a laboratory measurement.</b> The aniline point is
 * physically governed by paraffinicity and boiling range, so it is estimated here from the Watson
 * (UOP) characterization factor and the mean average boiling point (MeABP), both obtained
 * internally from {@link Standard_ASTM_D86}. The default coefficients reproduce typical
 * middle-distillate behaviour:
 * </p>
 *
 * <pre>
 * {@code
 * AP[C] = c0 + c1 * (Kw - 12.0) + c2 * (MeABP[C] - 190.0)
 * }
 * </pre>
 *
 * <p>
 * with defaults {@code c0 = 60.0}, {@code c1 = 35.0}, {@code c2 = 0.083}, anchored to: paraffinic
 * kerosene (Kw &asymp; 12.0, MeABP &asymp; 190 &deg;C &rarr; AP &asymp; 60 &deg;C), aromatic
 * kerosene (Kw &asymp; 11.0 &rarr; AP &asymp; 25 &deg;C) and paraffinic diesel (Kw &asymp; 12.5,
 * MeABP &asymp; 280 &deg;C &rarr; AP &asymp; 85 &deg;C). The coefficients are configurable so the
 * correlation can be calibrated to measured assay data. For rigorous work, calibrate against ASTM
 * D611 / API Procedure 2B8.1 (Walsh-Mortimer) data.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D611 aniline = new Standard_ASTM_D611(dieselFluid);
 * aniline.calculate();
 * double anilinePointC = aniline.getValue("anilinePoint", "C");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D611 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D611.class);

  /** Aniline point in Celsius. */
  private double anilinePointC = Double.NaN;

  /** Watson K used in the correlation. */
  private double watsonK = Double.NaN;

  /** Mean average boiling point in Celsius used in the correlation. */
  private double meabpC = Double.NaN;

  /** Correlation intercept coefficient. */
  private double c0 = 60.0;

  /** Correlation Watson-K coefficient. */
  private double c1 = 35.0;

  /** Correlation boiling-point coefficient. */
  private double c2 = 0.083;

  /** Optional minimum aniline-point specification limit in Celsius (NaN = no limit). */
  private double minAnilineSpecC = Double.NaN;

  /**
   * Constructor for Standard_ASTM_D611.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D611(SystemInterface thermoSystem) {
    super("Standard_ASTM_D611", "ASTM D611 - Estimated Aniline Point", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      Standard_ASTM_D86 d86 = new Standard_ASTM_D86(thermoSystem);
      d86.calculate();
      watsonK = d86.getWatsonK();
      meabpC = d86.getValue("MeABP", "C");

      if (Double.isNaN(watsonK) || Double.isNaN(meabpC)) {
        logger.error("Aniline point inputs unavailable (Watson K / MeABP)");
        return;
      }

      anilinePointC = c0 + c1 * (watsonK - 12.0) + c2 * (meabpC - 190.0);
    } catch (Exception ex) {
      logger.error("Aniline point calculation failed: {}", ex.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if ("anilinePoint".equalsIgnoreCase(returnParameter) || "AP".equalsIgnoreCase(returnParameter)
        || "MeABP".equalsIgnoreCase(returnParameter)) {
      return convertTempFromC(getValue(returnParameter), returnUnit);
    }
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("anilinePoint".equalsIgnoreCase(returnParameter)
        || "AP".equalsIgnoreCase(returnParameter)) {
      return anilinePointC;
    } else if ("MeABP".equalsIgnoreCase(returnParameter)) {
      return meabpC;
    } else if ("watsonK".equalsIgnoreCase(returnParameter)
        || "WatsonCharacterizationFactor".equalsIgnoreCase(returnParameter)) {
      return watsonK;
    } else {
      logger.error("returnParameter not supported: {}", returnParameter);
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("watsonK".equalsIgnoreCase(returnParameter)
        || "WatsonCharacterizationFactor".equalsIgnoreCase(returnParameter)) {
      return "-";
    }
    return "C";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (Double.isNaN(anilinePointC)) {
      return false;
    }
    if (Double.isNaN(minAnilineSpecC)) {
      return true;
    }
    return anilinePointC >= minAnilineSpecC;
  }

  /**
   * Sets the correlation coefficients used to estimate the aniline point.
   *
   * @param newC0 intercept coefficient (default 60.0)
   * @param newC1 Watson-K coefficient (default 35.0)
   * @param newC2 boiling-point coefficient (default 0.083)
   */
  public void setCorrelationCoefficients(double newC0, double newC1, double newC2) {
    this.c0 = newC0;
    this.c1 = newC1;
    this.c2 = newC2;
  }

  /**
   * Sets an optional minimum aniline-point specification limit used by {@link #isOnSpec()}.
   *
   * @param minAniline minimum allowed aniline point
   * @param minAnilineUnit temperature unit, one of {@code "C"}, {@code "K"}, {@code "F"},
   *        {@code "R"}
   */
  public void setMinAnilineSpec(double minAniline, String minAnilineUnit) {
    this.minAnilineSpecC = convertTempToC(minAniline, minAnilineUnit);
  }

  /**
   * Clears any previously configured minimum aniline-point specification limit.
   */
  public void clearMinAnilineSpec() {
    this.minAnilineSpecC = Double.NaN;
  }

  /**
   * Converts a temperature from Celsius to the requested unit.
   *
   * @param valueC temperature value in Celsius (may be NaN)
   * @param unit target unit, one of {@code "C"}, {@code "K"}, {@code "F"}, {@code "R"}
   * @return the converted temperature, or the Celsius value if the unit is unrecognised
   */
  private double convertTempFromC(double valueC, String unit) {
    if (Double.isNaN(valueC) || unit == null) {
      return valueC;
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
   * Converts a temperature in the supplied unit to Celsius.
   *
   * @param value temperature value
   * @param unit source unit, one of {@code "C"}, {@code "K"}, {@code "F"}, {@code "R"}
   * @return the temperature in Celsius
   */
  private double convertTempToC(double value, String unit) {
    if (unit == null) {
      return value;
    }
    if ("K".equalsIgnoreCase(unit)) {
      return value - 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      return (value - 32.0) * 5.0 / 9.0;
    } else if ("R".equalsIgnoreCase(unit)) {
      return value * 5.0 / 9.0 - 273.15;
    }
    return value;
  }
}
