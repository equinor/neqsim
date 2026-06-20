package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Estimated Cold Filter Plugging Point (CFPP) - EN 116 (screening correlation).
 *
 * <p>
 * The CFPP is the highest temperature at which a given volume of fuel fails to pass through a standardised filtration
 * device within a specified time when cooled. It characterises the low-temperature operability of diesel and heating
 * oils and is widely used in European specifications.
 * </p>
 *
 * <p>
 * <b>This class returns an estimate, not a laboratory measurement.</b> For untreated middle distillates the CFPP
 * closely tracks the cloud point (wax appearance temperature), which is obtained internally from
 * {@link Standard_ASTM_D2500}:
 * </p>
 *
 * <pre>
 * {@code
 * CFPP[C] = cloudPoint[C] + offset
 * }
 * </pre>
 *
 * <p>
 * The default offset is {@code 0.0 C} (CFPP equal to cloud point). For untreated fuels the CFPP is commonly within a
 * few degrees of the cloud point; cold-flow additives can depress the CFPP well below the cloud point, in which case
 * the offset should be set from response testing. The offset is configurable. For rigorous work, calibrate against EN
 * 116 laboratory data.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_EN116 cfpp = new Standard_EN116(dieselFluid);
 * cfpp.setOffset(-2.0);
 * cfpp.calculate();
 * double cfppC = cfpp.getValue("CFPP", "C");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_EN116 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_EN116.class);

  /** Cold filter plugging point in Celsius. */
  private double cfppC = Double.NaN;

  /** Cloud point in Celsius used as the basis. */
  private double cloudPointC = Double.NaN;

  /** Offset added to the cloud point to obtain CFPP, in Celsius. */
  private double offsetC = 0.0;

  /** Optional maximum CFPP specification limit in Celsius (NaN = no limit). */
  private double maxCfppSpecC = Double.NaN;

  /**
   * Constructor for Standard_EN116.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the fuel
   */
  public Standard_EN116(SystemInterface thermoSystem) {
    super("Standard_EN116", "EN 116 - Estimated Cold Filter Plugging Point", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      Standard_ASTM_D2500 d2500 = new Standard_ASTM_D2500(thermoSystem);
      d2500.calculate();
      cloudPointC = d2500.getValue("cloudPoint", "C");

      if (Double.isNaN(cloudPointC)) {
	logger.error("CFPP input unavailable (cloud point)");
	return;
      }

      cfppC = cloudPointC + offsetC;
    } catch (Exception ex) {
      logger.error("CFPP calculation failed: {}", ex.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if ("CFPP".equalsIgnoreCase(returnParameter) || "cloudPoint".equalsIgnoreCase(returnParameter)
	|| "CP".equalsIgnoreCase(returnParameter)) {
      return convertTempFromC(getValue(returnParameter), returnUnit);
    }
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("CFPP".equalsIgnoreCase(returnParameter)) {
      return cfppC;
    } else if ("cloudPoint".equalsIgnoreCase(returnParameter) || "CP".equalsIgnoreCase(returnParameter)) {
      return cloudPointC;
    } else {
      logger.error("returnParameter not supported: {}", returnParameter);
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return "C";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (Double.isNaN(cfppC)) {
      return false;
    }
    if (Double.isNaN(maxCfppSpecC)) {
      return true;
    }
    return cfppC <= maxCfppSpecC;
  }

  /**
   * Sets the offset (in Celsius) added to the cloud point to estimate the CFPP.
   *
   * @param offset offset in Celsius (default 0.0)
   */
  public void setOffset(double offset) {
    this.offsetC = offset;
  }

  /**
   * Gets the offset (in Celsius) currently applied to the cloud point.
   *
   * @return the offset in Celsius
   */
  public double getOffset() {
    return offsetC;
  }

  /**
   * Sets an optional maximum CFPP specification limit used by {@link #isOnSpec()}.
   *
   * @param maxCfpp maximum allowed CFPP
   * @param maxCfppUnit temperature unit, one of {@code "C"}, {@code "K"}, {@code "F"}, {@code "R"}
   */
  public void setMaxCfppSpec(double maxCfpp, String maxCfppUnit) {
    this.maxCfppSpecC = convertTempToC(maxCfpp, maxCfppUnit);
  }

  /**
   * Clears any previously configured maximum CFPP specification limit.
   */
  public void clearMaxCfppSpec() {
    this.maxCfppSpecC = Double.NaN;
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
