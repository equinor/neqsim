package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * BS&amp;W - Basic Sediment and Water determination for crude oil.
 *
 * <p>
 * Calculates the volume fraction of water and sediment in crude oil, per practices described in
 * ASTM D4007 (centrifuge method) and API MPMS Chapter 10. BS&amp;W is a critical specification for:
 * </p>
 * <ul>
 * <li>Crude oil custody transfer (typically max 0.5% or 1.0%)</li>
 * <li>Pipeline tariff calculations</li>
 * <li>Refinery intake quality</li>
 * <li>Storage tank management</li>
 * </ul>
 *
 * <p>
 * This implementation determines the water volume fraction in the liquid phase at given conditions
 * via thermodynamic flash and reports it as BS&amp;W percentage.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_BSW standard = new Standard_BSW(oilFluid);
 * standard.calculate();
 * double bsw = standard.getValue("BSW"); // vol%
 * boolean onSpec = standard.isOnSpec(); // checks against max limit
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_BSW extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_BSW.class);

  /** BS&amp;W in volume percent. */
  private double bswVolPct = Double.NaN;

  /** Water cut in volume percent. */
  private double waterCutVolPct = Double.NaN;

  /** Maximum allowed BS&amp;W in volume percent (for specification check). */
  private double maxBSW = 0.5;

  /** Measurement temperature in Celsius. */
  private double measurementTemperatureC = 60.0;

  /** Measurement pressure in bara. */
  private double measurementPressure = 1.01325;

  /**
   * Constructor for Standard_BSW.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_BSW(SystemInterface thermoSystem) {
    super("Standard_BSW", "BS&W - Basic Sediment and Water (ASTM D4007 / API MPMS Ch10)",
        thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      SystemInterface fluid = thermoSystem.clone();
      fluid.setTemperature(273.15 + measurementTemperatureC);
      fluid.setPressure(measurementPressure);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initPhysicalProperties("density");

      double waterVolume = 0.0;
      double oilVolume = 0.0;

      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        String phaseType = fluid.getPhase(i).getType().toString();
        double phaseMoles = fluid.getPhase(i).getNumberOfMolesInPhase();
        double phaseMolarMass = fluid.getPhase(i).getMolarMass(); // kg/mol
        double phaseDensity = fluid.getPhase(i).getDensity("kg/m3");

        if (phaseDensity > 0) {
          double phaseVolume = phaseMoles * phaseMolarMass / phaseDensity;

          if ("aqueous".equals(phaseType) || "water".equals(phaseType)) {
            waterVolume += phaseVolume;
          } else if ("oil".equals(phaseType) || "liquid".equals(phaseType)) {
            oilVolume += phaseVolume;
          }
        }
      }

      double totalLiquidVolume = waterVolume + oilVolume;
      if (totalLiquidVolume > 0) {
        waterCutVolPct = (waterVolume / totalLiquidVolume) * 100.0;
        // BS&W approximates water cut for thermodynamic calculation (no sediment model)
        bswVolPct = waterCutVolPct;
      } else {
        bswVolPct = 0.0;
        waterCutVolPct = 0.0;
      }
    } catch (Exception ex) {
      logger.error("BS&W calculation failed: {}", ex.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
      case "BSW":
      case "BS&W":
        return bswVolPct;
      case "waterCut":
        return waterCutVolPct;
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
    return "vol%";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return !Double.isNaN(bswVolPct) && bswVolPct <= maxBSW;
  }

  /**
   * Gets the maximum allowed BS&amp;W for specification check.
   *
   * @return maximum BS&amp;W in vol%
   */
  public double getMaxBSW() {
    return maxBSW;
  }

  /**
   * Sets the maximum allowed BS&amp;W for specification check. Default is 0.5 vol%.
   *
   * @param maxBSW maximum BS&amp;W in vol%
   */
  public void setMaxBSW(double maxBSW) {
    this.maxBSW = maxBSW;
  }

  /**
   * Gets the measurement temperature.
   *
   * @return temperature in Celsius
   */
  public double getMeasurementTemperatureC() {
    return measurementTemperatureC;
  }

  /**
   * Sets the measurement temperature. Default is 60 C (standard centrifuge temperature).
   *
   * @param temperatureC temperature in Celsius
   */
  public void setMeasurementTemperatureC(double temperatureC) {
    this.measurementTemperatureC = temperatureC;
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
