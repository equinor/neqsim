package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Implementation of ISO 23874 - Natural gas - Gas chromatographic requirements for hydrocarbon dew
 * point calculation.
 *
 * <p>
 * ISO 23874 specifies the minimum requirements for gas chromatographic (GC) analysis to provide
 * reliable input data for hydrocarbon dew point calculations. The standard requires extended C6+
 * analysis (up to at least C9 or C12) rather than just total C6+ fraction, because the heavy
 * hydrocarbon distribution significantly affects the cricondenbar.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Validates that the composition has sufficient heavy-end detail for dew point calculation</li>
 * <li>Checks for extended C6+ analysis (individual C6 through C9+ or C12+)</li>
 * <li>Calculates HC dew point temperature at a specified pressure (cricondenbar region)</li>
 * <li>Reports the cricondenbar temperature and pressure from the phase envelope</li>
 * <li>Assesses composition quality for dew point reliability</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO23874 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ISO23874.class);

  /** Internal thermo system for HC-only dew point. */
  private SystemInterface hcSystem;

  /** ThermodynamicOperations for calculations. */
  private ThermodynamicOperations thermoOps;

  /** HC dew point temperature in degrees C at specified pressure. */
  private double dewPointTemperature = -999.0;

  /** Cricondenbar temperature in degrees C. */
  private double cricondenbarTemperature = -999.0;

  /** Cricondenbar pressure in bara. */
  private double cricondenbarPressure = -999.0;

  /** Maximum carbon number found in composition. */
  private int maxCarbonNumber = 0;

  /** Whether composition meets ISO 23874 extended analysis requirements. */
  private boolean compositionQualityOk = false;

  /** Pressure at which to calculate HC dew point in bara. */
  private double evaluationPressure = 70.0;

  /** Minimum required carbon number for extended analysis. */
  private int minimumCarbonNumber = 9;

  /**
   * Constructor for Standard_ISO23874.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO23874(SystemInterface thermoSystem) {
    super("Standard_ISO23874",
        "Natural gas - Gas chromatographic requirements for hydrocarbon dew point calculation",
        thermoSystem);
  }

  /**
   * Sets the pressure at which to evaluate the HC dew point.
   *
   * @param pressureBara pressure in bara
   */
  public void setEvaluationPressure(double pressureBara) {
    this.evaluationPressure = pressureBara;
  }

  /**
   * Gets the evaluation pressure.
   *
   * @return pressure in bara
   */
  public double getEvaluationPressure() {
    return evaluationPressure;
  }

  /**
   * Sets the minimum required carbon number for quality check.
   *
   * @param minCN minimum carbon number (typically 9 or 12)
   */
  public void setMinimumCarbonNumber(int minCN) {
    this.minimumCarbonNumber = minCN;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      // Step 1: Assess composition quality
      assessCompositionQuality();

      // Step 2: Create HC-only system (exclude water, CO2, H2S for HC dew point)
      hcSystem = new SystemSrkEos(273.15 - 20.0, evaluationPressure);
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        String name = thermoSystem.getPhase(0).getComponent(i).getName();
        String type = thermoSystem.getPhase(0).getComponent(i).getComponentType();

        // Include only hydrocarbons and inerts (N2, He) that affect HC dew point
        if (!"water".equals(name) && !"H2S".equals(name)) {
          hcSystem.addComponent(name, thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles());
        }
      }
      hcSystem.setMixingRule(2);
      hcSystem.init(0);
      hcSystem.init(1);
      thermoOps = new ThermodynamicOperations(hcSystem);

      // Step 3: Calculate HC dew point at evaluation pressure
      hcSystem.setTemperature(273.15 - 20.0);
      hcSystem.setPressure(evaluationPressure);
      try {
        thermoOps.dewPointTemperatureFlash();
        dewPointTemperature = hcSystem.getTemperature() - 273.15;
      } catch (Exception ex) {
        logger.warn("HC dew point flash failed at {} bara", evaluationPressure);
        dewPointTemperature = -999.0;
      }

      // Step 4: Try to calculate cricondenbar via phase envelope
      try {
        hcSystem.setTemperature(273.15 - 20.0);
        hcSystem.setPressure(1.0);
        thermoOps.calcPTphaseEnvelope();
        double[] cricondenbar = thermoOps.get("cricondenbar");
        if (cricondenbar != null && cricondenbar.length >= 2) {
          cricondenbarTemperature = cricondenbar[0] - 273.15;
          cricondenbarPressure = cricondenbar[1];
        }
      } catch (Exception ex) {
        logger.warn("Phase envelope calculation failed", ex);
      }

    } catch (Exception ex) {
      logger.error("ISO 23874 calculation failed", ex);
    }
  }

  /**
   * Assesses whether the gas composition has sufficient heavy-end detail per ISO 23874.
   */
  private void assessCompositionQuality() {
    maxCarbonNumber = 1; // At least methane

    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      String name = thermoSystem.getPhase(0).getComponent(i).getName();
      String type = thermoSystem.getPhase(0).getComponent(i).getComponentType();

      // Check for individual heavy hydrocarbons
      int cn = estimateCarbonNumber(name, type);
      if (cn > maxCarbonNumber) {
        maxCarbonNumber = cn;
      }
    }

    compositionQualityOk = maxCarbonNumber >= minimumCarbonNumber;
  }

  /**
   * Estimates carbon number from component name or type.
   *
   * @param name the component name
   * @param type the component type
   * @return estimated carbon number
   */
  private int estimateCarbonNumber(String name, String type) {
    if ("methane".equals(name)) {
      return 1;
    }
    if ("ethane".equals(name)) {
      return 2;
    }
    if ("propane".equals(name)) {
      return 3;
    }
    if ("i-butane".equals(name) || "n-butane".equals(name)) {
      return 4;
    }
    if ("i-pentane".equals(name) || "n-pentane".equals(name) || "22-dim-C3".equals(name)) {
      return 5;
    }
    if ("n-hexane".equals(name) || name.startsWith("2-m-C5") || name.startsWith("3-m-C5")
        || "c-hexane".equals(name) || "benzene".equals(name)) {
      return 6;
    }
    if ("n-heptane".equals(name) || name.contains("C7") || "toluene".equals(name)) {
      return 7;
    }
    if ("n-octane".equals(name) || name.contains("C8")) {
      return 8;
    }
    if ("n-nonane".equals(name) || name.contains("C9")) {
      return 9;
    }
    if ("n-decane".equals(name) || name.contains("C10")) {
      return 10;
    }
    if (name.contains("C11")) {
      return 11;
    }
    if (name.contains("C12")) {
      return 12;
    }
    if ("TBP".equals(type) || "plus".equals(type)) {
      // TBP fractions — estimate from molar mass
      double mw = thermoSystem.getPhase(0).getComponent(name).getMolarMass() * 1000.0;
      return Math.max(1, (int) (mw / 14.0));
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("dewPointTemperature".equals(returnParameter)) {
      return dewPointTemperature;
    }
    if ("cricondenbarTemperature".equals(returnParameter)) {
      return cricondenbarTemperature;
    }
    if ("cricondenbarPressure".equals(returnParameter)) {
      return cricondenbarPressure;
    }
    if ("maxCarbonNumber".equals(returnParameter)) {
      return maxCarbonNumber;
    }
    if ("compositionQuality".equals(returnParameter)) {
      return compositionQualityOk ? 1.0 : 0.0;
    }
    return dewPointTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("dewPointTemperature".equals(returnParameter)
        || "cricondenbarTemperature".equals(returnParameter)) {
      return "C";
    }
    if ("cricondenbarPressure".equals(returnParameter)) {
      return "bara";
    }
    if ("maxCarbonNumber".equals(returnParameter)) {
      return "-";
    }
    return "C";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return compositionQualityOk;
  }
}
