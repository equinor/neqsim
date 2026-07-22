package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * True Vapor Pressure (TVP) - API MPMS Chapter 19 / GPA TP-15.
 *
 * <p>
 * The True Vapor Pressure is the equilibrium (bubble-point) pressure of a liquid at a specified reference temperature.
 * Unlike the Reid Vapor Pressure (ASTM D323/D6377, see {@link Standard_ASTM_D6377}), which is measured at a fixed 4:1
 * vapor-to-liquid ratio and 37.8 &deg;C, the TVP is the thermodynamic bubble-point pressure of the bulk liquid and can
 * be reported at any storage or transport temperature.
 * </p>
 *
 * <p>
 * TVP is the controlling parameter for storage-tank breathing losses, low-pressure separator and stabiliser design,
 * crude custody-transfer vapour-pressure limits, and the safe set point of pressure/vacuum relief on atmospheric and
 * low-pressure tanks (e.g. a common crude sales limit is a TVP at or below atmospheric pressure at the maximum storage
 * temperature).
 * </p>
 *
 * <p>
 * The TVP is computed directly from the equation of state with a bubble-point pressure flash at the reference
 * temperature, so it requires no empirical correlation.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_TVP tvp = new Standard_TVP(oilFluid);
 * tvp.setReferenceTemperature(50.0, "C");
 * tvp.calculate();
 * double tvpBara = tvp.getValue("TVP", "bara");
 * double tvpPsia = tvp.getValue("TVP", "psia");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_TVP extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_TVP.class);

  /** Default unit for reported pressure values. */
  private String unit = "bara";

  /** True vapor pressure at the reference temperature in bara. */
  private double tvp = Double.NaN;

  /** Reference temperature value. */
  private double referenceTemperature = 37.8;

  /** Reference temperature unit. */
  private String referenceTemperatureUnit = "C";

  /** Optional maximum TVP specification limit in bara (NaN = no limit). */
  private double maxTvpSpecBara = Double.NaN;

  /**
   * Constructor for Standard_TVP.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_TVP(SystemInterface thermoSystem) {
    super("Standard_TVP", "True Vapor Pressure (API MPMS Chapter 19)", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      SystemInterface fluid = thermoSystem.clone();
      fluid.setTemperature(referenceTemperature, referenceTemperatureUnit);
      thermoOps = new ThermodynamicOperations(fluid);
      thermoOps.bubblePointPressureFlash(false);
      tvp = fluid.getPressure();
    } catch (Exception ex) {
      logger.error("TVP bubble-point calculation failed: {}", ex.getMessage());
      tvp = Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if ("TVP".equalsIgnoreCase(returnParameter)) {
      if (Double.isNaN(tvp)) {
        return Double.NaN;
      }
      neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(tvp, "bara");
      return presConversion.getValue(returnUnit);
    }
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("TVP".equalsIgnoreCase(returnParameter)) {
      return tvp;
    } else if ("referenceTemperature".equalsIgnoreCase(returnParameter)) {
      return referenceTemperature;
    } else {
      logger.error("returnParameter not supported: {}", returnParameter);
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("referenceTemperature".equalsIgnoreCase(returnParameter)) {
      return referenceTemperatureUnit;
    }
    return unit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (Double.isNaN(tvp)) {
      return false;
    }
    if (Double.isNaN(maxTvpSpecBara)) {
      return true;
    }
    return tvp <= maxTvpSpecBara;
  }

  /**
   * Sets the reference temperature at which the TVP is evaluated.
   *
   * @param refTemp reference temperature value, must be a finite number
   * @param refTempUnit temperature unit, one of {@code "C"}, {@code "K"}, {@code "F"}, {@code "R"}
   */
  public void setReferenceTemperature(double refTemp, String refTempUnit) {
    this.referenceTemperature = refTemp;
    this.referenceTemperatureUnit = refTempUnit;
  }

  /**
   * Gets the reference temperature value at which the TVP is evaluated.
   *
   * @return reference temperature in the configured unit
   */
  public double getReferenceTemperature() {
    return referenceTemperature;
  }

  /**
   * Sets an optional maximum TVP specification limit used by {@link #isOnSpec()}.
   *
   * @param maxTvp maximum allowed TVP value, must be a finite positive number
   * @param maxTvpUnit pressure unit of {@code maxTvp} (e.g. {@code "bara"}, {@code "psia"}, {@code "kPa"})
   */
  public void setMaxTvpSpec(double maxTvp, String maxTvpUnit) {
    neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(maxTvp, maxTvpUnit);
    this.maxTvpSpecBara = presConversion.getValue("bara");
  }

  /**
   * Clears any previously configured maximum TVP specification limit.
   */
  public void clearMaxTvpSpec() {
    this.maxTvpSpecBara = Double.NaN;
  }
}
