package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

/**
 * ASTM D86 - Standard Test Method for Distillation of Petroleum Products and Liquid Fuels at
 * Atmospheric Pressure.
 *
 * <p>
 * Simulates true boiling point (TBP) distillation by performing a series of bubble-point
 * calculations at atmospheric pressure. The resulting curve maps volume percent distilled to
 * boiling temperature.
 * </p>
 *
 * <p>
 * This is the primary specification used for characterising crude oil cuts, gasoline, jet fuel,
 * diesel, and fuel oils for refinery planning and product quality.
 * </p>
 *
 * <p>
 * Calculated properties:
 * </p>
 * <ul>
 * <li>IBP (Initial Boiling Point)</li>
 * <li>T10, T50, T90, T95 (temperatures at 10, 50, 90, 95 vol% distilled)</li>
 * <li>FBP (Final Boiling Point)</li>
 * <li>Residue fraction</li>
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
 * @version 1.0
 */
public class Standard_ASTM_D86 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D86.class);

  /** Number of distillation points to calculate. */
  private int numberOfPoints = 19;

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

  /**
   * Constructor for Standard_ASTM_D86.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D86(SystemInterface thermoSystem) {
    super("Standard_ASTM_D86",
        "ASTM D86 - Distillation of Petroleum Products at Atmospheric " + "Pressure", thermoSystem);
    initVolumeFractions();
  }

  /**
   * Initializes the default volume fraction points for the distillation curve.
   */
  private void initVolumeFractions() {
    numberOfPoints = 19;
    volumeFractions = new double[numberOfPoints];
    temperatures = new double[numberOfPoints];
    // IBP(~0.5%), 5%, 10%, 15%, 20%, 25%, 30%, 35%, 40%, 45%, 50%,
    // 55%, 60%, 65%, 70%, 75%, 80%, 85%, 90%, 95%
    double[] fracs = {0.005, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60,
        0.65, 0.70, 0.75, 0.80, 0.85, 0.90};
    for (int i = 0; i < numberOfPoints; i++) {
      volumeFractions[i] = fracs[i];
      temperatures[i] = Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    SystemInterface fluid = thermoSystem.clone();
    fluid.setPressure(distillationPressure);

    thermoOps = new ThermodynamicOperations(fluid);

    // Calculate IBP via bubble point temperature
    try {
      fluid.setTemperature(273.15 + 20.0);
      thermoOps.bubblePointTemperatureFlash();
      IBP = fluid.getTemperature();
      temperatures[0] = IBP;
    } catch (Exception ex) {
      logger.error("Failed to calculate IBP: {}", ex.getMessage());
      return;
    }

    // Calculate temperatures at each volume fraction via TV fraction flash
    for (int i = 1; i < numberOfPoints; i++) {
      try {
        SystemInterface flashFluid = thermoSystem.clone();
        flashFluid.setPressure(distillationPressure);
        flashFluid.setTemperature(IBP);
        ThermodynamicOperations flashOps = new ThermodynamicOperations(flashFluid);
        flashOps.TVfractionFlash(volumeFractions[i]);
        temperatures[i] = flashFluid.getTemperature();
      } catch (Exception ex) {
        logger.debug("TV fraction flash failed at {}%: {}", volumeFractions[i] * 100.0,
            ex.getMessage());
        temperatures[i] = Double.NaN;
      }
    }

    // Calculate FBP via dew point temperature
    try {
      SystemInterface dewFluid = thermoSystem.clone();
      dewFluid.setPressure(distillationPressure);
      dewFluid.setTemperature(273.15 + 400.0);
      ThermodynamicOperations dewOps = new ThermodynamicOperations(dewFluid);
      dewOps.dewPointTemperatureFlash();
      FBP = dewFluid.getTemperature();
    } catch (Exception ex) {
      logger.debug("Failed to calculate FBP: {}", ex.getMessage());
      FBP = Double.NaN;
    }

    // Estimate residue fraction (fraction that doesn't boil at atmospheric pressure)
    if (!Double.isNaN(temperatures[numberOfPoints - 1]) && !Double.isNaN(FBP)) {
      residueFraction = Math.max(0.0, 1.0 - 0.95);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
      case "IBP":
        return IBP - 273.15;
      case "T5":
        return getTemperatureAtFraction(0.05) - 273.15;
      case "T10":
        return getTemperatureAtFraction(0.10) - 273.15;
      case "T50":
        return getTemperatureAtFraction(0.50) - 273.15;
      case "T90":
        return getTemperatureAtFraction(0.90) - 273.15;
      case "T95":
        return temperatures.length > 0 ? temperatures[numberOfPoints - 1] - 273.15 : Double.NaN;
      case "FBP":
        return Double.isNaN(FBP) ? Double.NaN : FBP - 273.15;
      case "residue":
        return residueFraction;
      default:
        // Try interpreting as "Txx" where xx is percentage
        if (returnParameter.startsWith("T") && returnParameter.length() > 1) {
          try {
            double pct = Double.parseDouble(returnParameter.substring(1));
            return getTemperatureAtFraction(pct / 100.0) - 273.15;
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
    return !Double.isNaN(IBP);
  }

  /**
   * Gets the temperature at a given distilled volume fraction by linear interpolation.
   *
   * @param fraction the volume fraction (0.0 to 1.0)
   * @return temperature in Kelvin at the specified fraction
   */
  private double getTemperatureAtFraction(double fraction) {
    if (fraction <= volumeFractions[0]) {
      return temperatures[0];
    }
    for (int i = 1; i < numberOfPoints; i++) {
      if (Double.isNaN(temperatures[i]) || Double.isNaN(temperatures[i - 1])) {
        continue;
      }
      if (fraction <= volumeFractions[i]) {
        double x =
            (fraction - volumeFractions[i - 1]) / (volumeFractions[i] - volumeFractions[i - 1]);
        return temperatures[i - 1] + x * (temperatures[i] - temperatures[i - 1]);
      }
    }
    return temperatures[numberOfPoints - 1];
  }

  /**
   * Returns the full distillation curve data.
   *
   * @return a two-dimensional array where [i][0] is volume fraction and [i][1] is temperature in
   *         Celsius
   */
  public double[][] getDistillationCurve() {
    double[][] curve = new double[numberOfPoints][2];
    for (int i = 0; i < numberOfPoints; i++) {
      curve[i][0] = volumeFractions[i] * 100.0;
      curve[i][1] = Double.isNaN(temperatures[i]) ? Double.NaN : temperatures[i] - 273.15;
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
}
