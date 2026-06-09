package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculated Cetane Index - ASTM D4737 (four-variable) with ASTM D976 (two-variable) cross-check.
 *
 * <p>
 * The cetane index is a calculated estimate of the cetane number of a middle-distillate fuel (jet,
 * kerosene, diesel) derived from its density and distillation recovery temperatures. It is used as
 * a sales-specification surrogate for the engine-measured cetane number (ASTM D613) when an engine
 * test is not available.
 * </p>
 *
 * <p>
 * Two correlations are evaluated:
 * </p>
 * <ul>
 * <li><b>ASTM D4737</b> (primary, four-variable) using the 10 %, 50 % and 90 % recovered
 * temperatures and density at 15 &deg;C.</li>
 * <li><b>ASTM D976</b> (two-variable) using the 50 % recovered temperature and density at 15
 * &deg;C.</li>
 * </ul>
 *
 * <p>
 * The required inputs are obtained internally from {@link Standard_ASTM_D86} (distillation curve)
 * and {@link Standard_ASTM_D4052} (density / API gravity), so only the fluid is required.
 * </p>
 *
 * <p>
 * The ASTM D4737 calculated cetane index (CCI) is:
 * </p>
 *
 * <pre>
 * {@code
 * CCI = 45.2 + 0.0892 * T10N + (0.131 + 0.901 * B) * T50N + (0.0523 - 0.420 * B) * T90N
 *     + 0.00049 * (T10N ^ 2 - T90N ^ 2) + 107 * B + 60 * B ^ 2
 * }
 * </pre>
 *
 * <p>
 * where {@code T10N = T10 - 215}, {@code T50N = T50 - 260}, {@code T90N = T90 - 310} (temperatures
 * in &deg;C), {@code B = exp(-3.5*(D - 0.85)) - 1} and {@code D} is the density at 15 &deg;C in
 * g/mL.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D4737 cetane = new Standard_ASTM_D4737(dieselFluid);
 * cetane.calculate();
 * double cci = cetane.getValue("cetaneIndex");
 * double cciD976 = cetane.getValue("cetaneIndexD976");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D4737 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D4737.class);

  /** Calculated cetane index per ASTM D4737 (four-variable). */
  private double cetaneIndexD4737 = Double.NaN;

  /** Calculated cetane index per ASTM D976 (two-variable). */
  private double cetaneIndexD976 = Double.NaN;

  /** 10 % recovered temperature in Celsius. */
  private double t10C = Double.NaN;

  /** 50 % recovered temperature in Celsius. */
  private double t50C = Double.NaN;

  /** 90 % recovered temperature in Celsius. */
  private double t90C = Double.NaN;

  /** Density at 15 Celsius in kg/m3. */
  private double density15C = Double.NaN;

  /** Optional minimum cetane-index specification limit (NaN = no limit). */
  private double minCetaneSpec = Double.NaN;

  /**
   * Constructor for Standard_ASTM_D4737.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the fuel
   */
  public Standard_ASTM_D4737(SystemInterface thermoSystem) {
    super("Standard_ASTM_D4737", "ASTM D4737 - Calculated Cetane Index", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      Standard_ASTM_D86 d86 = new Standard_ASTM_D86(thermoSystem);
      d86.calculate();
      t10C = d86.getValue("T10", "C");
      t50C = d86.getValue("T50", "C");
      t90C = d86.getValue("T90", "C");

      Standard_ASTM_D4052 d4052 = new Standard_ASTM_D4052(thermoSystem);
      d4052.calculate();
      density15C = d4052.getValue("density");

      if (Double.isNaN(t10C) || Double.isNaN(t50C) || Double.isNaN(t90C)
          || Double.isNaN(density15C)) {
        logger.error("Cetane index inputs unavailable (T10/T50/T90/density)");
        return;
      }

      // Density in g/mL at 15 C for both correlations.
      double dGmL = density15C / 1000.0;

      // ASTM D4737 (four-variable).
      double t10n = t10C - 215.0;
      double t50n = t50C - 260.0;
      double t90n = t90C - 310.0;
      double b = Math.exp(-3.5 * (dGmL - 0.85)) - 1.0;
      cetaneIndexD4737 =
          45.2 + 0.0892 * t10n + (0.131 + 0.901 * b) * t50n + (0.0523 - 0.420 * b) * t90n
              + 0.00049 * (t10n * t10n - t90n * t90n) + 107.0 * b + 60.0 * b * b;

      // ASTM D976 (two-variable) using mid-boiling point T50.
      double logT50 = Math.log10(t50C);
      cetaneIndexD976 =
          454.74 - 1641.416 * dGmL + 774.74 * dGmL * dGmL - 0.554 * t50C + 97.803 * logT50 * logT50;
    } catch (Exception ex) {
      logger.error("Cetane index calculation failed: {}", ex.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    // Cetane index and the underlying temperatures are reported on a fixed basis.
    if ("T10".equalsIgnoreCase(returnParameter) || "T50".equalsIgnoreCase(returnParameter)
        || "T90".equalsIgnoreCase(returnParameter)) {
      return convertTempFromC(getValue(returnParameter), returnUnit);
    }
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
      case "cetaneIndex":
      case "cetaneIndexD4737":
      case "CCI":
        return cetaneIndexD4737;
      case "cetaneIndexD976":
        return cetaneIndexD976;
      case "T10":
        return t10C;
      case "T50":
        return t50C;
      case "T90":
        return t90C;
      case "density":
      case "density15C":
        return density15C;
      default:
        logger.error("returnParameter not supported: {}", returnParameter);
        return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("density".equalsIgnoreCase(returnParameter)
        || "density15C".equalsIgnoreCase(returnParameter)) {
      return "kg/m3";
    }
    if ("T10".equalsIgnoreCase(returnParameter) || "T50".equalsIgnoreCase(returnParameter)
        || "T90".equalsIgnoreCase(returnParameter)) {
      return "C";
    }
    return "-";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (Double.isNaN(cetaneIndexD4737)) {
      return false;
    }
    if (Double.isNaN(minCetaneSpec)) {
      return true;
    }
    return cetaneIndexD4737 >= minCetaneSpec;
  }

  /**
   * Sets an optional minimum cetane-index specification limit used by {@link #isOnSpec()}.
   *
   * @param minCetane minimum allowed cetane index (e.g. 51 for EN 590 automotive diesel)
   */
  public void setMinCetaneSpec(double minCetane) {
    this.minCetaneSpec = minCetane;
  }

  /**
   * Clears any previously configured minimum cetane-index specification limit.
   */
  public void clearMinCetaneSpec() {
    this.minCetaneSpec = Double.NaN;
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
}
