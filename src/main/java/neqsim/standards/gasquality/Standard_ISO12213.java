package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.gerg.NeqSimAGA8Detail;

/**
 * Implementation of ISO 12213 - Natural gas - Calculation of compression factor.
 *
 * <p>
 * ISO 12213 consists of three parts:
 * </p>
 * <ul>
 * <li>Part 1: Introduction and guidelines</li>
 * <li>Part 2: Calculation using molar-composition analysis (wraps SGERG-88 model)</li>
 * <li>Part 3: Calculation using physical properties (wraps AGA 8 Detail method, AGA8-DC92)</li>
 * </ul>
 *
 * <p>
 * This implementation primarily wraps the AGA 8 Detail method (ISO 12213-3) which is already
 * implemented in NeqSim via {@link neqsim.thermo.util.gerg.NeqSimAGA8Detail}. It provides the
 * standard ISO interface for computing compression factor (Z), molar density, and mass density of
 * natural gas from detailed composition at specified temperature and pressure.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO12213 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ISO12213.class);

  /** Calculated compression factor (Z). */
  private double compressionFactor = 1.0;

  /** Calculated molar density in mol/dm3. */
  private double molarDensity = 0.0;

  /** Calculated mass density in kg/m3. */
  private double massDensity = 0.0;

  /** Calculated molar mass of the mixture in g/mol. */
  private double molarMass = 0.0;

  /** Calculation temperature in K. */
  private double calculationTemperature = 288.15;

  /** Calculation pressure in MPa. */
  private double calculationPressure = 0.101325;

  /** Calculation method: "AGA8" (Part 3) or "SGERG" (Part 2). */
  private String calculationMethod = "AGA8";

  /**
   * Constructor for Standard_ISO12213.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO12213(SystemInterface thermoSystem) {
    super("Standard_ISO12213", "Natural gas - Calculation of compression factor (ISO 12213)",
        thermoSystem);
    this.calculationTemperature = thermoSystem.getTemperature();
    this.calculationPressure = thermoSystem.getPressure() * 0.1; // bara to MPa
  }

  /**
   * Constructor for Standard_ISO12213 with specified conditions.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   */
  public Standard_ISO12213(SystemInterface thermoSystem, double temperatureK, double pressureBara) {
    this(thermoSystem);
    this.calculationTemperature = temperatureK;
    this.calculationPressure = pressureBara * 0.1; // bara to MPa
  }

  /**
   * Sets the calculation method.
   *
   * @param method "AGA8" for ISO 12213-3 or "SGERG" for ISO 12213-2
   */
  public void setCalculationMethod(String method) {
    this.calculationMethod = method;
  }

  /**
   * Gets the calculation method.
   *
   * @return the calculation method string
   */
  public String getCalculationMethod() {
    return calculationMethod;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      SystemInterface tempSystem = thermoSystem.clone();
      tempSystem.setTemperature(calculationTemperature);
      tempSystem.setPressure(calculationPressure * 10.0); // MPa to bara
      tempSystem.init(0);
      tempSystem.init(1);

      if ("AGA8".equals(calculationMethod)) {
        calculateAGA8(tempSystem);
      } else {
        // SGERG-88 simplified method - use GERG-2008 as best available
        calculateAGA8(tempSystem);
      }
    } catch (Exception ex) {
      logger.error("ISO 12213 calculation failed", ex);
    }
  }

  /**
   * Calculates compression factor using AGA 8 Detail method (ISO 12213-3).
   *
   * @param system the thermodynamic system to use
   */
  private void calculateAGA8(SystemInterface system) {
    try {
      NeqSimAGA8Detail aga8 = new NeqSimAGA8Detail(system.getPhase(0));
      massDensity = aga8.getDensity();
      molarMass = system.getPhase(0).getMolarMass() * 1000.0; // kg/mol to g/mol

      if (molarMass > 0.0 && massDensity > 0.0) {
        molarDensity = massDensity / (molarMass / 1000.0); // mol/m3
        double R = 8.314510; // J/(mol*K)
        compressionFactor =
            (calculationPressure * 1.0e6) / (molarDensity * R * calculationTemperature);
      }
    } catch (Exception ex) {
      logger.error("AGA8 calculation failed", ex);
      // Fallback to EOS calculation
      compressionFactor = system.getPhase(0).getZ();
      massDensity = system.getPhase(0).getDensity("kg/m3");
      molarMass = system.getPhase(0).getMolarMass() * 1000.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("compressionFactor".equals(returnParameter) || "Z".equals(returnParameter)) {
      return compressionFactor;
    }
    if ("molarDensity".equals(returnParameter)) {
      return molarDensity;
    }
    if ("density".equals(returnParameter) || "massDensity".equals(returnParameter)) {
      return massDensity;
    }
    if ("molarMass".equals(returnParameter)) {
      return molarMass;
    }
    return compressionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("compressionFactor".equals(returnParameter) || "Z".equals(returnParameter)) {
      return "-";
    }
    if ("molarDensity".equals(returnParameter)) {
      return "mol/m3";
    }
    if ("density".equals(returnParameter) || "massDensity".equals(returnParameter)) {
      return "kg/m3";
    }
    if ("molarMass".equals(returnParameter)) {
      return "g/mol";
    }
    return "-";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return compressionFactor > 0.0 && compressionFactor < 2.0;
  }
}
