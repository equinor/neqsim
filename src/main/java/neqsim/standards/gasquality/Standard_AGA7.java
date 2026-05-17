package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Implementation of AGA Report No. 7 / ISO 12242 - Measurement of gas flow in closed conduits -
 * Ultrasonic meters for gas.
 *
 * <p>
 * AGA 7 (and the international standard ISO 12242) specifies performance requirements and
 * installation conditions for multi-path ultrasonic transit-time gas meters used for custody
 * transfer of natural gas. Ultrasonic meters are now the dominant technology for fiscal-quality
 * metering in the gas industry.
 * </p>
 *
 * <p>
 * Key calculations provided:
 * </p>
 * <ul>
 * <li>Speed of sound in the gas at flowing conditions (from thermodynamic EOS)</li>
 * <li>Gas density at flowing conditions</li>
 * <li>Volume flow rate at line conditions and at standard conditions</li>
 * <li>Reynolds number for installation evaluation</li>
 * <li>Speed of sound comparison: measured vs. calculated (diagnostic)</li>
 * <li>Meter verification diagnostics (SOS deviation, path velocity ratios)</li>
 * </ul>
 *
 * <p>
 * Per AGA 7/ISO 12242, the maximum allowable error for an ultrasonic meter is +/-0.7% for flow
 * rates between qt and qmax, and +/-1.4% between qmin and qt.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_AGA7 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_AGA7.class);

  /** Pipe internal diameter in meters. */
  private double pipeDiameter = 0.3048; // 12 inch default

  /** Flowing pressure in bara. */
  private double flowingPressure = 70.0;

  /** Flowing temperature in K. */
  private double flowingTemperature = 288.15;

  /** Measured average velocity in m/s. */
  private double measuredVelocity = 10.0;

  /** Measured speed of sound in m/s (from USM). */
  private double measuredSpeedOfSound = 0.0;

  /** Calculated speed of sound in m/s (from EOS). */
  private double calculatedSpeedOfSound = 0.0;

  /** Gas density at flowing conditions in kg/m3. */
  private double flowingDensity = 0.0;

  /** Gas density at standard conditions in kg/m3. */
  private double standardDensity = 0.0;

  /** Dynamic viscosity at flowing conditions in Pa*s. */
  private double viscosity = 0.0;

  /** Volume flow rate at line conditions in m3/h. */
  private double lineVolumeFlowRate = 0.0;

  /** Volume flow rate at standard conditions in Sm3/h. */
  private double standardVolumeFlowRate = 0.0;

  /** Mass flow rate in kg/h. */
  private double massFlowRate = 0.0;

  /** Reynolds number based on pipe diameter. */
  private double reynoldsNumber = 0.0;

  /** Speed of sound deviation between measured and calculated in %. */
  private double sosDeviation = 0.0;

  /** Maximum allowable SOS deviation for diagnostics in %. */
  private double sosDeviationLimit = 0.2;

  /**
   * Constructor for Standard_AGA7.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_AGA7(SystemInterface thermoSystem) {
    super("Standard_AGA7", "Measurement of gas flow by ultrasonic meters (AGA 7 / ISO 12242)",
        thermoSystem);
  }

  /**
   * Sets the pipe diameter.
   *
   * @param diameterM pipe internal diameter in meters
   */
  public void setPipeDiameter(double diameterM) {
    this.pipeDiameter = diameterM;
  }

  /**
   * Sets the flowing conditions.
   *
   * @param pressureBara flowing pressure in bara
   * @param temperatureK flowing temperature in K
   */
  public void setFlowingConditions(double pressureBara, double temperatureK) {
    this.flowingPressure = pressureBara;
    this.flowingTemperature = temperatureK;
  }

  /**
   * Sets the measured average velocity from the USM.
   *
   * @param velocityMs average velocity in m/s
   */
  public void setMeasuredVelocity(double velocityMs) {
    this.measuredVelocity = velocityMs;
  }

  /**
   * Sets the measured speed of sound from the USM for diagnostic comparison.
   *
   * @param sosMs measured speed of sound in m/s
   */
  public void setMeasuredSpeedOfSound(double sosMs) {
    this.measuredSpeedOfSound = sosMs;
  }

  /**
   * Sets the maximum allowable SOS deviation for meter diagnostics.
   *
   * @param limitPercent deviation limit in percent (default 0.2%)
   */
  public void setSOSDeviationLimit(double limitPercent) {
    this.sosDeviationLimit = limitPercent;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      // Calculate gas properties at flowing conditions
      SystemInterface flowingSystem = thermoSystem.clone();
      flowingSystem.setTemperature(flowingTemperature);
      flowingSystem.setPressure(flowingPressure);
      flowingSystem.init(0);
      flowingSystem.init(1);
      ThermodynamicOperations ops = new ThermodynamicOperations(flowingSystem);
      ops.TPflash();
      flowingSystem.initPhysicalProperties();

      flowingDensity = flowingSystem.getPhase(0).getDensity("kg/m3");
      viscosity = flowingSystem.getPhase(0).getViscosity("kg/msec");

      // Speed of sound from EOS: c = sqrt(Cp/Cv * P / rho) simplified
      // More accurately: c = sqrt(dP/drho at constant S)
      double cp = flowingSystem.getPhase(0).getCp();
      double cv = flowingSystem.getPhase(0).getCv();
      double kappa = cp / cv;
      // Using ideal gas approximation with compressibility: c = sqrt(kappa * Z * R * T / M)
      double molarMass = flowingSystem.getPhase(0).getMolarMass(); // kg/mol
      double z = flowingSystem.getPhase(0).getZ();
      double R = 8.314510;
      calculatedSpeedOfSound = Math.sqrt(kappa * z * R * flowingTemperature / molarMass);

      // Speed of sound diagnostic
      if (measuredSpeedOfSound > 0.0 && calculatedSpeedOfSound > 0.0) {
        sosDeviation =
            (measuredSpeedOfSound - calculatedSpeedOfSound) / calculatedSpeedOfSound * 100.0;
      }

      // Calculate volume flow rate at line conditions
      double pipeArea = (Math.PI / 4.0) * pipeDiameter * pipeDiameter;
      lineVolumeFlowRate = measuredVelocity * pipeArea * 3600.0; // m3/h

      // Mass flow rate
      massFlowRate = lineVolumeFlowRate * flowingDensity; // kg/h

      // Standard conditions
      SystemInterface stdSystem = thermoSystem.clone();
      stdSystem.setTemperature(288.15);
      stdSystem.setPressure(1.01325);
      stdSystem.init(0);
      stdSystem.init(1);
      ops = new ThermodynamicOperations(stdSystem);
      ops.TPflash();
      standardDensity = stdSystem.getPhase(0).getDensity("kg/m3");

      // Standard volume flow rate
      if (standardDensity > 0.0) {
        standardVolumeFlowRate = massFlowRate / standardDensity; // Sm3/h
      }

      // Reynolds number
      if (viscosity > 0.0 && pipeDiameter > 0.0) {
        double avgVelocity = measuredVelocity;
        reynoldsNumber = flowingDensity * avgVelocity * pipeDiameter / viscosity;
      }

    } catch (Exception ex) {
      logger.error("AGA 7 calculation failed", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double value = getValue(returnParameter);
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      if ("SCFD".equals(returnUnit) || "scf/d".equals(returnUnit)) {
        return value * 24.0 * 35.3147;
      }
      if ("MMSCFD".equals(returnUnit)) {
        return value * 24.0 * 35.3147 / 1.0e6;
      }
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("calculatedSpeedOfSound".equals(returnParameter) || "SOS".equals(returnParameter)) {
      return calculatedSpeedOfSound;
    }
    if ("measuredSpeedOfSound".equals(returnParameter)) {
      return measuredSpeedOfSound;
    }
    if ("sosDeviation".equals(returnParameter)) {
      return sosDeviation;
    }
    if ("flowingDensity".equals(returnParameter)) {
      return flowingDensity;
    }
    if ("standardDensity".equals(returnParameter)) {
      return standardDensity;
    }
    if ("lineVolumeFlowRate".equals(returnParameter)) {
      return lineVolumeFlowRate;
    }
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      return standardVolumeFlowRate;
    }
    if ("massFlowRate".equals(returnParameter)) {
      return massFlowRate;
    }
    if ("reynoldsNumber".equals(returnParameter) || "Re".equals(returnParameter)) {
      return reynoldsNumber;
    }
    return standardVolumeFlowRate;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("calculatedSpeedOfSound".equals(returnParameter) || "SOS".equals(returnParameter)
        || "measuredSpeedOfSound".equals(returnParameter)) {
      return "m/s";
    }
    if ("sosDeviation".equals(returnParameter)) {
      return "%";
    }
    if ("flowingDensity".equals(returnParameter) || "standardDensity".equals(returnParameter)) {
      return "kg/m3";
    }
    if ("lineVolumeFlowRate".equals(returnParameter)
        || "standardVolumeFlowRate".equals(returnParameter)) {
      return "Sm3/h";
    }
    if ("massFlowRate".equals(returnParameter)) {
      return "kg/h";
    }
    if ("reynoldsNumber".equals(returnParameter) || "Re".equals(returnParameter)) {
      return "-";
    }
    return "Sm3/h";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    // Diagnostics: SOS deviation should be within limit
    boolean sosDiagOk = measuredSpeedOfSound == 0.0 || Math.abs(sosDeviation) <= sosDeviationLimit;
    // Reynolds number should be sufficient for accurate measurement
    boolean reOk = reynoldsNumber > 10000;
    return sosDiagOk && reOk;
  }
}
