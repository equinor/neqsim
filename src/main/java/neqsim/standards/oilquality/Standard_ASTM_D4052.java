package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ASTM D4052 / ISO 12185 - Standard Test Method for Density, Relative Density, and API Gravity of
 * Liquids.
 *
 * <p>
 * Calculates oil density at reference temperature (15 C or 60 F) and derives API gravity. This is
 * the fundamental crude oil classification parameter used for trading, custody transfer, and
 * refinery planning.
 * </p>
 *
 * <p>
 * API Gravity is defined as:
 * </p>
 *
 * <pre>
 * {@code
 * API = (141.5 / SG_60F) - 131.5
 * }
 * </pre>
 *
 * <p>
 * where SG_60F is the specific gravity at 60 F (15.56 C) relative to water at 60 F.
 * </p>
 *
 * <p>
 * Key outputs:
 * </p>
 * <ul>
 * <li>Density at 15 C in kg/m3</li>
 * <li>Specific gravity at 60 F (relative to water)</li>
 * <li>API gravity (degrees API)</li>
 * <li>Oil classification (light/medium/heavy/extra-heavy)</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D4052 standard = new Standard_ASTM_D4052(oilFluid);
 * standard.calculate();
 * double apiGravity = standard.getValue("API");
 * double density15C = standard.getValue("density");
 * String classification = standard.getOilClassification();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D4052 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D4052.class);

  /** Density of oil at 15 C in kg/m3. */
  private double density15C = Double.NaN;

  /** Specific gravity at 60 F (15.56 C) relative to water at 60 F. */
  private double specificGravity60F = Double.NaN;

  /** API gravity in degrees API. */
  private double apiGravity = Double.NaN;

  /** Density of water at 15.56 C (60 F) in kg/m3. */
  private static final double WATER_DENSITY_60F = 999.016;

  /** Reference temperature for API gravity in Celsius (60 F). */
  private double referenceTemperatureC = 15.556;

  /** Measurement pressure in bara (standard atmospheric). */
  private double measurementPressure = 1.01325;

  /**
   * Constructor for Standard_ASTM_D4052.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D4052(SystemInterface thermoSystem) {
    super("Standard_ASTM_D4052", "ASTM D4052 - Density, Relative Density, and API Gravity",
        thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      SystemInterface fluid = thermoSystem.clone();
      fluid.setTemperature(273.15 + referenceTemperatureC);
      fluid.setPressure(measurementPressure);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initPhysicalProperties("density");

      // Find oil/liquid phase
      int liquidPhaseIndex = -1;
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        String phaseType = fluid.getPhase(i).getType().toString();
        if ("oil".equals(phaseType) || "liquid".equals(phaseType)) {
          liquidPhaseIndex = i;
          break;
        }
      }

      if (liquidPhaseIndex < 0 && fluid.getNumberOfPhases() > 0) {
        liquidPhaseIndex = 0;
      }

      if (liquidPhaseIndex >= 0) {
        density15C = fluid.getPhase(liquidPhaseIndex).getDensity("kg/m3");
        specificGravity60F = density15C / WATER_DENSITY_60F;
        apiGravity = (141.5 / specificGravity60F) - 131.5;
      }
    } catch (Exception ex) {
      logger.error("Density calculation at 15 C failed: {}", ex.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
      case "density":
      case "density15C":
        return density15C;
      case "SG":
      case "specificGravity":
        return specificGravity60F;
      case "API":
      case "apiGravity":
        return apiGravity;
      default:
        logger.error("Unsupported parameter: {}", returnParameter);
        return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if ("density".equals(returnParameter) || "density15C".equals(returnParameter)) {
      double densityKgM3 = getValue(returnParameter);
      if (Double.isNaN(densityKgM3)) {
        return Double.NaN;
      }
      if ("lb/ft3".equalsIgnoreCase(returnUnit)) {
        return densityKgM3 * 0.062428;
      } else if ("g/cm3".equalsIgnoreCase(returnUnit) || "g/cc".equalsIgnoreCase(returnUnit)) {
        return densityKgM3 / 1000.0;
      }
      return densityKgM3;
    }
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    switch (returnParameter) {
      case "density":
      case "density15C":
        return "kg/m3";
      case "SG":
      case "specificGravity":
        return "-";
      case "API":
      case "apiGravity":
        return "deg API";
      default:
        return "";
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return !Double.isNaN(apiGravity);
  }

  /**
   * Returns the crude oil classification based on API gravity.
   *
   * <p>
   * Classification per standard industry convention:
   * </p>
   * <ul>
   * <li>Light oil: API &gt; 31.1</li>
   * <li>Medium oil: 22.3 &lt; API &lt;= 31.1</li>
   * <li>Heavy oil: 10.0 &lt; API &lt;= 22.3</li>
   * <li>Extra-heavy oil / bitumen: API &lt;= 10.0</li>
   * </ul>
   *
   * @return a string classification of the crude oil
   */
  public String getOilClassification() {
    if (Double.isNaN(apiGravity)) {
      return "Unknown";
    }
    if (apiGravity > 31.1) {
      return "Light";
    } else if (apiGravity > 22.3) {
      return "Medium";
    } else if (apiGravity > 10.0) {
      return "Heavy";
    } else {
      return "Extra-Heavy / Bitumen";
    }
  }

  /**
   * Gets the reference temperature for density measurement.
   *
   * @return reference temperature in Celsius
   */
  public double getReferenceTemperatureC() {
    return referenceTemperatureC;
  }

  /**
   * Sets the reference temperature for density measurement. Default is 15.556 C (60 F).
   *
   * @param temperatureC reference temperature in Celsius
   */
  public void setReferenceTemperatureC(double temperatureC) {
    this.referenceTemperatureC = temperatureC;
  }

  /**
   * Gets the measurement pressure.
   *
   * @return pressure in bara
   */
  public double getMeasurementPressure() {
    return measurementPressure;
  }

  /**
   * Sets the measurement pressure. Default is 1.01325 bara.
   *
   * @param pressure pressure in bara
   */
  public void setMeasurementPressure(double pressure) {
    this.measurementPressure = pressure;
  }
}
