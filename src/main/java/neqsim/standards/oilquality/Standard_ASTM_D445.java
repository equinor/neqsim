package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ASTM D445 - Standard Test Method for Kinematic Viscosity of Transparent and Opaque Liquids.
 *
 * <p>
 * Calculates kinematic viscosity at standard reference temperatures (typically 40 C and 100 C) by
 * performing a thermodynamic flash and extracting the liquid phase viscosity. Also computes the
 * Viscosity Index (VI) per ASTM D2270.
 * </p>
 *
 * <p>
 * Key outputs:
 * </p>
 * <ul>
 * <li>Kinematic viscosity at 40 C (KV40) in mm2/s (cSt)</li>
 * <li>Kinematic viscosity at 100 C (KV100) in mm2/s (cSt)</li>
 * <li>Viscosity Index (VI) per ASTM D2270</li>
 * <li>Dynamic viscosity in mPa.s (cP)</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D445 standard = new Standard_ASTM_D445(oilFluid);
 * standard.calculate();
 * double kv40 = standard.getValue("KV40"); // cSt at 40C
 * double kv100 = standard.getValue("KV100"); // cSt at 100C
 * double vi = standard.getValue("VI"); // Viscosity Index
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D445 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D445.class);

  /** Kinematic viscosity at 40 C in mm2/s (cSt). */
  private double kinematicViscosity40C = Double.NaN;

  /** Kinematic viscosity at 100 C in mm2/s (cSt). */
  private double kinematicViscosity100C = Double.NaN;

  /** Dynamic viscosity at 40 C in mPa.s (cP). */
  private double dynamicViscosity40C = Double.NaN;

  /** Dynamic viscosity at 100 C in mPa.s (cP). */
  private double dynamicViscosity100C = Double.NaN;

  /** Liquid density at 40 C in kg/m3. */
  private double density40C = Double.NaN;

  /** Liquid density at 100 C in kg/m3. */
  private double density100C = Double.NaN;

  /** Viscosity Index per ASTM D2270. */
  private double viscosityIndex = Double.NaN;

  /** Measurement pressure in bara (standard atmospheric). */
  private double measurementPressure = 1.01325;

  /**
   * Constructor for Standard_ASTM_D445.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D445(SystemInterface thermoSystem) {
    super("Standard_ASTM_D445", "ASTM D445 - Kinematic Viscosity of Transparent and Opaque Liquids",
        thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    // Calculate viscosity at 40 C
    calculateAtTemperature(40.0, true);

    // Calculate viscosity at 100 C
    calculateAtTemperature(100.0, false);

    // Calculate Viscosity Index per ASTM D2270
    if (!Double.isNaN(kinematicViscosity40C) && !Double.isNaN(kinematicViscosity100C)) {
      viscosityIndex = calculateViscosityIndex(kinematicViscosity40C, kinematicViscosity100C);
    }
  }

  /**
   * Performs flash at specified temperature and extracts liquid viscosity and density.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param is40C true if storing results for 40 C, false for 100 C
   */
  private void calculateAtTemperature(double temperatureC, boolean is40C) {
    try {
      SystemInterface fluid = thermoSystem.clone();
      fluid.setTemperature(273.15 + temperatureC);
      fluid.setPressure(measurementPressure);
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initPhysicalProperties("viscosity");

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
        // Fall back to first phase if no liquid found (single phase)
        liquidPhaseIndex = 0;
      }

      if (liquidPhaseIndex >= 0) {
        // Dynamic viscosity in Pa.s from NeqSim, convert to mPa.s (cP)
        double dynVisc = fluid.getPhase(liquidPhaseIndex).getViscosity("kg/msec") * 1000.0;
        // Density in kg/m3
        double dens = fluid.getPhase(liquidPhaseIndex).getDensity("kg/m3");
        // Kinematic viscosity = dynamic / density, convert to mm2/s
        // dynVisc in mPa.s = 1e-3 Pa.s, density in kg/m3
        // KV [mm2/s] = (dynVisc [mPa.s] / density [kg/m3]) * 1000
        double kinVisc = (dynVisc / dens) * 1000.0;

        if (is40C) {
          dynamicViscosity40C = dynVisc;
          density40C = dens;
          kinematicViscosity40C = kinVisc;
        } else {
          dynamicViscosity100C = dynVisc;
          density100C = dens;
          kinematicViscosity100C = kinVisc;
        }
      }
    } catch (Exception ex) {
      logger.error("Viscosity calculation failed at {} C: {}", temperatureC, ex.getMessage());
    }
  }

  /**
   * Calculates the Viscosity Index per ASTM D2270.
   *
   * <p>
   * For KV100 between 2 and 70 mm2/s, uses the standard L and H reference oils lookup. This is a
   * simplified calculation using the empirical correlation.
   * </p>
   *
   * @param kv40 kinematic viscosity at 40 C in mm2/s
   * @param kv100 kinematic viscosity at 100 C in mm2/s
   * @return viscosity index
   */
  private double calculateViscosityIndex(double kv40, double kv100) {
    if (kv100 <= 0 || kv40 <= 0) {
      return Double.NaN;
    }

    // ASTM D2270 simplified correlation for VI
    // L and H are reference viscosities at 40C for oils with VI=0 and VI=100
    // Using the polynomial approximations for L and H
    double y = kv100;

    if (y < 2.0) {
      return Double.NaN; // Below valid range
    }

    // Simplified ASTM D2270 procedure
    double logY = Math.log10(y);
    double h = 0.7 + 14.534 * y - 48.44 * Math.pow(y, 0.5) + 0.544 * y * y;
    double l = 1.0 + 18.17 * y - 55.57 * Math.pow(y, 0.5) + 0.585 * y * y;

    if (y >= 2.0 && y <= 3.8) {
      h = 0.7 + 14.534 * y - 48.44 * Math.pow(y, 0.5) + 0.544 * y * y;
      l = 1.0 + 18.17 * y - 55.57 * Math.pow(y, 0.5) + 0.585 * y * y;
    } else if (y > 3.8 && y <= 70.0) {
      h = 0.8353 * y * y + 14.67 * y - 216.0;
      l = 0.8353 * y * y + 14.67 * y - 216.0 + 13.0 * y;
    }

    if (l <= h) {
      return Double.NaN;
    }

    double vi = ((l - kv40) / (l - h)) * 100.0;
    return vi;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
      case "KV40":
        return kinematicViscosity40C;
      case "KV100":
        return kinematicViscosity100C;
      case "dynamicViscosity40C":
        return dynamicViscosity40C;
      case "dynamicViscosity100C":
        return dynamicViscosity100C;
      case "density40C":
        return density40C;
      case "density100C":
        return density100C;
      case "VI":
        return viscosityIndex;
      default:
        logger.error("Unsupported parameter: {}", returnParameter);
        return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    switch (returnParameter) {
      case "KV40":
      case "KV100":
        return "mm2/s";
      case "dynamicViscosity40C":
      case "dynamicViscosity100C":
        return "mPa.s";
      case "density40C":
      case "density100C":
        return "kg/m3";
      case "VI":
        return "-";
      default:
        return "";
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return !Double.isNaN(kinematicViscosity40C);
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
   * @param pressure measurement pressure in bara
   */
  public void setMeasurementPressure(double pressure) {
    this.measurementPressure = pressure;
  }
}
