package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ASTM D97 - Standard Test Method for Pour Point of Petroleum Products.
 *
 * <p>
 * Determines the pour point of a petroleum product, which is the lowest temperature at which the
 * oil will still flow. The pour point is 3 C above the temperature at which the oil ceases to flow
 * when cooled.
 * </p>
 *
 * <p>
 * The pour point is critical for:
 * </p>
 * <ul>
 * <li>Pipeline restart after shutdown (gel strength)</li>
 * <li>Crude oil storage and transport</li>
 * <li>Lubricant selection for cold environments</li>
 * <li>Fuel oil handling and pumping</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D97 standard = new Standard_ASTM_D97(oilFluid);
 * standard.calculate();
 * double pourPoint = standard.getValue("pourPoint", "C");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D97 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D97.class);

  /** Pour point temperature in Kelvin. */
  private double pourPointK = Double.NaN;

  /** Measurement pressure in bara. */
  private double measurementPressure = 1.01325;

  /**
   * Pour point offset above gel point in degrees (standard is +3 C above the point where oil ceases
   * to flow).
   */
  private double pourPointOffset = 3.0;

  /**
   * Maximum viscosity threshold in mPa.s (cP) for considering oil as non-flowing. Typical practical
   * limit is around 10000-50000 cP.
   */
  private double nonFlowViscosityThreshold = 20000.0;

  /**
   * Constructor for Standard_ASTM_D97.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D97(SystemInterface thermoSystem) {
    super("Standard_ASTM_D97", "ASTM D97 - Pour Point of Petroleum Products", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    double startTempC = 50.0;
    double endTempC = -60.0;
    double stepC = 3.0;

    double gelTempC = Double.NaN;

    for (double tempC = startTempC; tempC >= endTempC; tempC -= stepC) {
      try {
        SystemInterface fluid = thermoSystem.clone();
        fluid.setTemperature(273.15 + tempC);
        fluid.setPressure(measurementPressure);
        fluid.setMultiPhaseCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        fluid.initPhysicalProperties("viscosity");

        // Check for very high viscosity indicating non-flow
        int oilPhase = findOilPhase(fluid);
        if (oilPhase >= 0) {
          double viscosity = fluid.getPhase(oilPhase).getViscosity("kg/msec") * 1000.0; // mPa.s
          if (viscosity > nonFlowViscosityThreshold) {
            gelTempC = tempC;
            break;
          }
        }

        // Also check if a solid/wax phase dominates
        if (fluid.hasPhaseType("wax") || fluid.hasPhaseType("solid")) {
          // Check wax fraction; if very high, oil won't flow
          double solidFraction = 0.0;
          for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
            String phaseType = fluid.getPhase(i).getType().toString();
            if ("wax".equals(phaseType) || "solid".equals(phaseType)) {
              solidFraction += fluid.getPhase(i).getBeta();
            }
          }
          if (solidFraction > 0.02) { // >2% wax typically stops flow
            gelTempC = tempC;
            break;
          }
        }
      } catch (Exception ex) {
        logger.debug("Flash failed at {} C: {}", tempC, ex.getMessage());
      }
    }

    if (!Double.isNaN(gelTempC)) {
      // Pour point is 3 C above the gel point (per ASTM D97 convention)
      pourPointK = 273.15 + gelTempC + pourPointOffset;
    }
  }

  /**
   * Finds the oil/liquid phase in the fluid system.
   *
   * @param fluid the fluid system
   * @return phase index of the oil phase, or -1 if not found
   */
  private int findOilPhase(SystemInterface fluid) {
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      String phaseType = fluid.getPhase(i).getType().toString();
      if ("oil".equals(phaseType) || "liquid".equals(phaseType)) {
        return i;
      }
    }
    if (fluid.getNumberOfPhases() > 0) {
      return 0;
    }
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("pourPoint".equals(returnParameter) || "PP".equals(returnParameter)) {
      return Double.isNaN(pourPointK) ? Double.NaN : pourPointK - 273.15;
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
    return !Double.isNaN(pourPointK);
  }

  /**
   * Gets the non-flow viscosity threshold.
   *
   * @return viscosity threshold in mPa.s (cP)
   */
  public double getNonFlowViscosityThreshold() {
    return nonFlowViscosityThreshold;
  }

  /**
   * Sets the non-flow viscosity threshold. The default is 20000 mPa.s.
   *
   * @param threshold viscosity threshold in mPa.s (cP)
   */
  public void setNonFlowViscosityThreshold(double threshold) {
    this.nonFlowViscosityThreshold = threshold;
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
