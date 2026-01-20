package neqsim.process.equipment.compressor.driver;

/**
 * Steam turbine driver model.
 *
 * <p>
 * Models steam turbine performance including:
 * </p>
 * <ul>
 * <li>Inlet steam conditions (pressure, temperature)</li>
 * <li>Exhaust pressure (condensing or back-pressure)</li>
 * <li>Extraction steam for process use</li>
 * <li>Willans line efficiency characteristics</li>
 * </ul>
 *
 * <p><strong>Steam Turbine Types</strong></p>
 * <ul>
 * <li><strong>Back-pressure:</strong> Exhaust at process steam pressure</li>
 * <li><strong>Condensing:</strong> Exhaust to condenser at vacuum</li>
 * <li><strong>Extraction:</strong> Steam taken from intermediate stage</li>
 * </ul>
 *
 * <p><strong>Example Usage</strong></p>
 * 
 * <pre>
 * SteamTurbineDriver turbine = new SteamTurbineDriver(5000, 6000, 0.75);
 * turbine.setInletPressure(42.0); // 42 bara inlet
 * turbine.setInletTemperature(400.0); // 400°C inlet
 * turbine.setExhaustPressure(0.1); // 0.1 bara condensing
 * 
 * double power = turbine.getAvailablePower(6000);
 * double steamRate = turbine.getSteamConsumption(4000, 6000); // kg/hr
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SteamTurbineDriver extends DriverCurveBase {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;

  /** Turbine type enumeration. */
  public enum TurbineType {
    BACK_PRESSURE, CONDENSING, EXTRACTION
  }

  /** Turbine type. */
  private TurbineType turbineType = TurbineType.CONDENSING;

  /** Inlet steam pressure in bara. */
  private double inletPressure = 42.0;

  /** Inlet steam temperature in Celsius. */
  private double inletTemperature = 400.0;

  /** Exhaust pressure in bara. */
  private double exhaustPressure = 0.1;

  /** Extraction pressure in bara (for extraction turbines). */
  private double extractionPressure = 5.0;

  /** Extraction flow rate in kg/s. */
  private double extractionFlow = 0.0;

  /** Steam rate at no-load in kg/kWh. */
  private double noLoadSteamRate = 0.5;

  /** Incremental steam rate in kg/kWh. */
  private double incrementalSteamRate = 4.0;

  /** Speed turndown capability. */
  private double speedTurndown = 0.6;

  /**
   * Default constructor.
   */
  public SteamTurbineDriver() {
    super();
  }

  /**
   * Constructor with rated power and efficiency.
   *
   * @param ratedPowerKW rated power in kW
   * @param designEfficiency design efficiency at rated conditions (0-1)
   */
  public SteamTurbineDriver(double ratedPowerKW, double designEfficiency) {
    super(ratedPowerKW, 6000.0, designEfficiency); // Typical ST speed
    this.minSpeed = ratedSpeed * speedTurndown;
  }

  /**
   * Constructor with full parameters.
   *
   * @param ratedPowerKW rated power in kW
   * @param ratedSpeedRPM rated speed in RPM
   * @param designEfficiency design efficiency at rated conditions (0-1)
   */
  public SteamTurbineDriver(double ratedPowerKW, double ratedSpeedRPM, double designEfficiency) {
    super(ratedPowerKW, ratedSpeedRPM, designEfficiency);
    this.minSpeed = ratedSpeed * speedTurndown;
  }

  /** {@inheritDoc} */
  @Override
  public String getDriverType() {
    return "SteamTurbine_" + turbineType.name();
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailablePower(double speed) {
    if (speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }

    // Steam turbines have relatively flat power curve with speed
    double speedRatio = speed / ratedSpeed;
    double speedFactor = 0.9 + 0.1 * speedRatio;

    // Account for extraction steam if applicable
    double extractionDerating = 1.0;
    if (turbineType == TurbineType.EXTRACTION && extractionFlow > 0) {
      // Rough estimate: extraction reduces available power
      extractionDerating = 0.95; // Simplified
    }

    return ratedPower * speedFactor * extractionDerating;
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiency(double speed, double loadFraction) {
    if (speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }
    if (loadFraction <= 0 || loadFraction > 1.1) {
      return 0.0;
    }

    // Steam turbine efficiency follows Willans line
    // Part-load efficiency drops due to fixed losses
    double loadFactor = 0.7 + 0.3 * loadFraction;

    // Speed effect (small)
    double speedRatio = speed / ratedSpeed;
    double speedFactor = 0.95 + 0.05 * speedRatio;

    return designEfficiency * loadFactor * speedFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getAmbientDeratingFactor() {
    // Steam turbine derating based on cooling water temperature (for condensing)
    if (turbineType == TurbineType.CONDENSING) {
      // Higher cooling water temp = higher exhaust pressure = less power
      double coolingWaterTemp = ambientTemperature + 10.0; // Assume 10°C approach
      double baseTemp = 25.0;
      if (coolingWaterTemp > baseTemp) {
        double tempRise = coolingWaterTemp - baseTemp;
        return Math.max(0.8, 1.0 - 0.01 * tempRise);
      }
    }
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getFuelConsumption(double powerOutput, double speed) {
    // Returns steam consumption in kg/hr
    return getSteamConsumption(powerOutput, speed);
  }

  /**
   * Gets the steam consumption for given power output.
   *
   * <p>
   * Uses Willans line: Steam = no_load_rate * P_rated + incremental_rate * P_actual
   * </p>
   *
   * @param powerOutput power output in kW
   * @param speed operating speed in RPM
   * @return steam consumption in kg/hr
   */
  public double getSteamConsumption(double powerOutput, double speed) {
    if (powerOutput <= 0 || speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }

    // Willans line calculation
    double noLoadSteam = noLoadSteamRate * ratedPower;
    double incrementalSteam = incrementalSteamRate * powerOutput;

    return noLoadSteam + incrementalSteam;
  }

  /**
   * Gets the theoretical steam rate.
   *
   * @return theoretical steam rate in kg/kWh
   */
  public double getTheoreticalSteamRate() {
    // Simplified calculation based on enthalpy drop
    // Actual would use steam tables
    double enthalpyDrop = calculateEnthalpyDrop();
    if (enthalpyDrop <= 0) {
      return Double.MAX_VALUE;
    }
    return 3600.0 / enthalpyDrop; // kg/kWh
  }

  /**
   * Calculates approximate enthalpy drop across turbine.
   *
   * @return enthalpy drop in kJ/kg
   */
  private double calculateEnthalpyDrop() {
    // Simplified isentropic calculation (actual would use steam tables)
    // Approximate: 3 kJ/kg per bar of pressure drop, adjusted for superheat
    double pressDrop = inletPressure - exhaustPressure;
    double superheat = Math.max(0, inletTemperature - 200.0); // Above saturation estimate

    return pressDrop * 3.0 + superheat * 0.5;
  }

  // Steam condition setters

  /**
   * Gets the turbine type.
   *
   * @return turbine type
   */
  public TurbineType getTurbineType() {
    return turbineType;
  }

  /**
   * Sets the turbine type.
   *
   * @param turbineType turbine type
   */
  public void setTurbineType(TurbineType turbineType) {
    this.turbineType = turbineType;
  }

  /**
   * Gets the inlet pressure.
   *
   * @return inlet pressure in bara
   */
  public double getInletPressure() {
    return inletPressure;
  }

  /**
   * Sets the inlet pressure.
   *
   * @param pressureBara inlet pressure in bara
   */
  public void setInletPressure(double pressureBara) {
    this.inletPressure = pressureBara;
  }

  /**
   * Gets the inlet temperature.
   *
   * @return inlet temperature in Celsius
   */
  public double getInletTemperature() {
    return inletTemperature;
  }

  /**
   * Sets the inlet temperature.
   *
   * @param temperatureCelsius inlet temperature in Celsius
   */
  public void setInletTemperature(double temperatureCelsius) {
    this.inletTemperature = temperatureCelsius;
  }

  /**
   * Gets the exhaust pressure.
   *
   * @return exhaust pressure in bara
   */
  public double getExhaustPressure() {
    return exhaustPressure;
  }

  /**
   * Sets the exhaust pressure.
   *
   * @param pressureBara exhaust pressure in bara
   */
  public void setExhaustPressure(double pressureBara) {
    this.exhaustPressure = pressureBara;
  }

  /**
   * Gets the extraction pressure.
   *
   * @return extraction pressure in bara
   */
  public double getExtractionPressure() {
    return extractionPressure;
  }

  /**
   * Sets the extraction pressure.
   *
   * @param pressureBara extraction pressure in bara
   */
  public void setExtractionPressure(double pressureBara) {
    this.extractionPressure = pressureBara;
  }

  /**
   * Gets the extraction flow rate.
   *
   * @return extraction flow in kg/s
   */
  public double getExtractionFlow() {
    return extractionFlow;
  }

  /**
   * Sets the extraction flow rate.
   *
   * @param flowKgPerSec extraction flow in kg/s
   */
  public void setExtractionFlow(double flowKgPerSec) {
    this.extractionFlow = flowKgPerSec;
  }

  /**
   * Sets the Willans line parameters.
   *
   * @param noLoadRate no-load steam rate in kg/kWh
   * @param incrementalRate incremental steam rate in kg/kWh
   */
  public void setWillansLineParameters(double noLoadRate, double incrementalRate) {
    this.noLoadSteamRate = noLoadRate;
    this.incrementalSteamRate = incrementalRate;
  }
}
