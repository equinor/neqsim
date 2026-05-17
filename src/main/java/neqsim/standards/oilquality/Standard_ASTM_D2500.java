package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ASTM D2500 - Standard Test Method for Cloud Point of Petroleum Products and Liquid Fuels.
 *
 * <p>
 * Calculates the cloud point of a petroleum product, which is the temperature at which a cloud of
 * wax crystals first appears when the oil is cooled under prescribed conditions. The cloud point is
 * important for:
 * </p>
 * <ul>
 * <li>Cold weather operability of diesel fuels</li>
 * <li>Pipeline flow assurance (wax deposition onset)</li>
 * <li>Crude oil transportation and storage</li>
 * </ul>
 *
 * <p>
 * The cloud point approximates the Wax Appearance Temperature (WAT) and is determined by cooling
 * the fluid and checking for wax formation.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D2500 standard = new Standard_ASTM_D2500(oilFluid);
 * standard.calculate();
 * double cloudPoint = standard.getValue("cloudPoint", "C");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D2500 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D2500.class);

  /** Cloud point temperature in Kelvin. */
  private double cloudPointK = Double.NaN;

  /** Measurement pressure in bara (standard atmospheric). */
  private double measurementPressure = 1.01325;

  /**
   * Constructor for Standard_ASTM_D2500.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D2500(SystemInterface thermoSystem) {
    super("Standard_ASTM_D2500", "ASTM D2500 - Cloud Point of Petroleum Products", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      SystemInterface fluid = thermoSystem.clone();
      fluid.setPressure(measurementPressure);
      fluid.setTemperature(273.15 + 80.0);

      // Enable wax / solid check
      fluid.setMultiPhaseCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

      // Try to calculate WAT (Wax Appearance Temperature)
      try {
        ops.calcWAT();
        cloudPointK = fluid.getTemperature();
      } catch (Exception ex) {
        logger.debug("WAT calculation failed, trying cooling approach: {}", ex.getMessage());
        // Fall back to a cooling scan
        cloudPointK = coolingApproach(fluid);
      }
    } catch (Exception ex) {
      logger.error("Cloud point calculation failed: {}", ex.getMessage());
    }
  }

  /**
   * Fall-back cooling approach: stepwise cool the fluid until a solid/wax phase appears.
   *
   * @param originalFluid the fluid system to test
   * @return cloud point temperature in Kelvin, or NaN if not found
   */
  private double coolingApproach(SystemInterface originalFluid) {
    double startTempC = 80.0;
    double endTempC = -60.0;
    double stepC = 2.0;

    for (double tempC = startTempC; tempC >= endTempC; tempC -= stepC) {
      try {
        SystemInterface fluid = originalFluid.clone();
        fluid.setTemperature(273.15 + tempC);
        fluid.setPressure(measurementPressure);
        fluid.setMultiPhaseCheck(true);
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();

        if (fluid.hasPhaseType("wax") || fluid.hasPhaseType("solid")) {
          // Refine: binary search between this temperature and previous step
          double upper = tempC + stepC;
          double lower = tempC;
          for (int i = 0; i < 10; i++) {
            double mid = (upper + lower) / 2.0;
            SystemInterface testFluid = originalFluid.clone();
            testFluid.setTemperature(273.15 + mid);
            testFluid.setPressure(measurementPressure);
            testFluid.setMultiPhaseCheck(true);
            ThermodynamicOperations testOps = new ThermodynamicOperations(testFluid);
            testOps.TPflash();
            if (testFluid.hasPhaseType("wax") || testFluid.hasPhaseType("solid")) {
              lower = mid;
            } else {
              upper = mid;
            }
          }
          return 273.15 + (upper + lower) / 2.0;
        }
      } catch (Exception ex) {
        logger.debug("Flash failed at {} C: {}", tempC, ex.getMessage());
      }
    }
    return Double.NaN;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("cloudPoint".equals(returnParameter) || "CP".equals(returnParameter)) {
      return Double.isNaN(cloudPointK) ? Double.NaN : cloudPointK - 273.15;
    }
    logger.error("Unsupported parameter: {}", returnParameter);
    return Double.NaN;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double valueC = getValue(returnParameter);
    if (Double.isNaN(valueC)) {
      return Double.NaN;
    }
    if ("K".equalsIgnoreCase(returnUnit)) {
      return valueC + 273.15;
    } else if ("F".equalsIgnoreCase(returnUnit)) {
      return valueC * 9.0 / 5.0 + 32.0;
    }
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
    return !Double.isNaN(cloudPointK);
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
