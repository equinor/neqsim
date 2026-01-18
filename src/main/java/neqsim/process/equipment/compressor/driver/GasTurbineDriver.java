package neqsim.process.equipment.compressor.driver;

/**
 * Gas turbine driver model with ambient derating.
 *
 * <p>
 * Models gas turbine performance including:
 * </p>
 * <ul>
 * <li>Ambient temperature derating (typically ~0.7% per °C above ISO)</li>
 * <li>Altitude derating (due to reduced air density)</li>
 * <li>Part-load efficiency characteristics</li>
 * <li>Fuel gas consumption</li>
 * </ul>
 *
 * <h3>Temperature Derating</h3>
 * <p>
 * Gas turbines experience reduced power output at higher ambient temperatures due to reduced air
 * density and compressor work increase. The typical derating is approximately 0.7% per °C above the
 * ISO reference temperature of 15°C.
 * </p>
 *
 * <h3>Altitude Derating</h3>
 * <p>
 * At higher altitudes, reduced air density decreases mass flow through the turbine, reducing power
 * output. Typical derating is about 3.5% per 305m (1000 ft) of elevation.
 * </p>
 *
 * <h3>Example Usage</h3>
 * 
 * <pre>
 * GasTurbineDriver driver = new GasTurbineDriver(10000, 7500, 0.35);
 * driver.setAmbientTemperature(35.0); // 35°C ambient
 * driver.setAltitude(500); // 500m elevation
 * 
 * double availablePower = driver.getAvailablePower(7500); // ~8200 kW after derating
 * double fuelRate = driver.getFuelConsumption(8000, 7500); // kg/hr or kW thermal
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class GasTurbineDriver extends DriverCurveBase {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** ISO reference temperature in Celsius. */
  private static final double ISO_REFERENCE_TEMP = 15.0;

  /** Sea level reference altitude in meters. */
  private static final double SEA_LEVEL_ALTITUDE = 0.0;

  /** Temperature derating factor (fraction per °C above ISO). */
  private double temperatureDeratingFactor = 0.007; // 0.7% per °C

  /** Altitude derating factor (fraction per 305m / 1000ft). */
  private double altitudeDeratingFactor = 0.035; // 3.5% per 305m

  /** Heat rate at rated conditions in kJ/kWh. */
  private double ratedHeatRate = 10000.0; // Typical aeroderivative

  /** Fuel gas lower heating value in kJ/kg. */
  private double fuelLHV = 50000.0; // Typical natural gas

  /** Speed turndown capability (minimum speed as fraction of rated). */
  private double speedTurndown = 0.7;

  /** Part-load efficiency curve coefficients. */
  private double[] efficiencyCoeffs = {0.1, 0.6, 0.3}; // a + b*x + c*x^2

  /**
   * Default constructor.
   */
  public GasTurbineDriver() {
    super();
    setDriverType();
  }

  /**
   * Constructor with rated power and efficiency.
   *
   * @param ratedPowerKW rated power in kW at ISO conditions
   * @param designEfficiency design efficiency at rated conditions (0-1)
   */
  public GasTurbineDriver(double ratedPowerKW, double designEfficiency) {
    super(ratedPowerKW, 7500.0, designEfficiency); // Typical GT speed
    setDriverType();
    this.minSpeed = ratedSpeed * speedTurndown;
  }

  /**
   * Constructor with full parameters.
   *
   * @param ratedPowerKW rated power in kW at ISO conditions
   * @param ratedSpeedRPM rated speed in RPM
   * @param designEfficiency design efficiency at rated conditions (0-1)
   */
  public GasTurbineDriver(double ratedPowerKW, double ratedSpeedRPM, double designEfficiency) {
    super(ratedPowerKW, ratedSpeedRPM, designEfficiency);
    setDriverType();
    this.minSpeed = ratedSpeed * speedTurndown;
  }

  private void setDriverType() {}

  /** {@inheritDoc} */
  @Override
  public String getDriverType() {
    return "GasTurbine";
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailablePower(double speed) {
    if (speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }

    // Apply ambient derating
    double deratedPower = ratedPower * getAmbientDeratingFactor();

    // Gas turbines have roughly constant power over speed range
    // with some variation near minimum speed
    double speedRatio = speed / ratedSpeed;
    double speedFactor = 1.0;
    if (speedRatio < 0.9) {
      // Slight power reduction at lower speeds
      speedFactor = 0.9 + 0.1 * (speedRatio - speedTurndown) / (0.9 - speedTurndown);
    }

    return deratedPower * speedFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiency(double speed, double loadFraction) {
    if (speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }
    if (loadFraction <= 0 || loadFraction > 1.2) {
      return 0.0;
    }

    // Part-load efficiency drops significantly
    // Typical curve: efficiency = design_eff * (a + b*load + c*load^2)
    double x = loadFraction;
    double loadFactor = efficiencyCoeffs[0] + efficiencyCoeffs[1] * x + efficiencyCoeffs[2] * x * x;
    loadFactor = Math.min(1.0, loadFactor);

    return designEfficiency * loadFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getAmbientDeratingFactor() {
    // Temperature derating
    double tempDerate = 1.0;
    if (ambientTemperature > ISO_REFERENCE_TEMP) {
      tempDerate = 1.0 - temperatureDeratingFactor * (ambientTemperature - ISO_REFERENCE_TEMP);
    } else if (ambientTemperature < ISO_REFERENCE_TEMP) {
      // Cold temperature can increase power (limited)
      double bonus = temperatureDeratingFactor * (ISO_REFERENCE_TEMP - ambientTemperature) * 0.5;
      tempDerate = Math.min(1.1, 1.0 + bonus);
    }

    // Altitude derating
    double altDerate = 1.0;
    if (altitude > SEA_LEVEL_ALTITUDE) {
      altDerate = 1.0 - altitudeDeratingFactor * (altitude / 305.0);
    }

    return Math.max(0.1, tempDerate * altDerate);
  }

  /** {@inheritDoc} */
  @Override
  public double getFuelConsumption(double powerOutput, double speed) {
    if (powerOutput <= 0 || speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }

    double availablePower = getAvailablePower(speed);
    double loadFraction = powerOutput / availablePower;
    double efficiency = getEfficiency(speed, loadFraction);

    if (efficiency <= 0) {
      return 0.0;
    }

    // Power input = Power output / efficiency
    double powerInput = powerOutput / efficiency;

    // Fuel consumption (kg/hr) = Power input (kW) * 3600 (s/hr) / LHV (kJ/kg)
    return powerInput * 3600.0 / fuelLHV;
  }

  /**
   * Gets the heat rate at the specified operating point.
   *
   * @param speed operating speed in RPM
   * @param loadFraction load as fraction of available power
   * @return heat rate in kJ/kWh
   */
  public double getHeatRate(double speed, double loadFraction) {
    double efficiency = getEfficiency(speed, loadFraction);
    if (efficiency <= 0) {
      return Double.MAX_VALUE;
    }
    return 3600.0 / efficiency; // kJ/kWh
  }

  /**
   * Gets the exhaust gas temperature.
   *
   * <p>
   * Exhaust temperature typically increases at part load due to reduced turbine work extraction.
   * </p>
   *
   * @param loadFraction load as fraction of available power
   * @return exhaust temperature in Celsius
   */
  public double getExhaustTemperature(double loadFraction) {
    // Base exhaust temp (typical for aeroderivative GT)
    double baseExhaustTemp = 500.0;

    // Exhaust temp rises at part load
    double loadFactor = 1.0 + 0.15 * (1.0 - loadFraction);

    return baseExhaustTemp * loadFactor;
  }

  /**
   * Gets the exhaust mass flow rate.
   *
   * @param speed operating speed in RPM
   * @return exhaust mass flow in kg/s
   */
  public double getExhaustMassFlow(double speed) {
    // Approximate: mass flow proportional to power
    double baseMassFlow = ratedPower / 500.0; // Rough estimate: 500 kW per kg/s
    double speedRatio = speed / ratedSpeed;
    return baseMassFlow * speedRatio * getAmbientDeratingFactor();
  }

  // Getters and setters for configuration

  /**
   * Gets the temperature derating factor.
   *
   * @return temperature derating factor (fraction per °C)
   */
  public double getTemperatureDeratingFactor() {
    return temperatureDeratingFactor;
  }

  /**
   * Sets the temperature derating factor.
   *
   * @param factor derating factor (fraction per °C above ISO)
   */
  public void setTemperatureDeratingFactor(double factor) {
    this.temperatureDeratingFactor = factor;
  }

  /**
   * Gets the altitude derating factor.
   *
   * @return altitude derating factor (fraction per 305m)
   */
  public double getAltitudeDeratingFactor() {
    return altitudeDeratingFactor;
  }

  /**
   * Sets the altitude derating factor.
   *
   * @param factor derating factor (fraction per 305m)
   */
  public void setAltitudeDeratingFactor(double factor) {
    this.altitudeDeratingFactor = factor;
  }

  /**
   * Gets the fuel lower heating value.
   *
   * @return LHV in kJ/kg
   */
  public double getFuelLHV() {
    return fuelLHV;
  }

  /**
   * Sets the fuel lower heating value.
   *
   * @param lhv LHV in kJ/kg
   */
  public void setFuelLHV(double lhv) {
    this.fuelLHV = lhv;
  }

  /**
   * Gets the speed turndown capability.
   *
   * @return minimum speed as fraction of rated speed
   */
  public double getSpeedTurndown() {
    return speedTurndown;
  }

  /**
   * Sets the speed turndown capability.
   *
   * @param turndown minimum speed as fraction of rated speed
   */
  public void setSpeedTurndown(double turndown) {
    this.speedTurndown = turndown;
    this.minSpeed = ratedSpeed * turndown;
  }

  /**
   * Sets the part-load efficiency curve coefficients.
   *
   * <p>
   * The efficiency at part load is calculated as: design_eff * (a + b*load + c*load^2) where load
   * is the load fraction (0-1).
   * </p>
   *
   * @param a constant term
   * @param b linear term
   * @param c quadratic term
   */
  public void setEfficiencyCurve(double a, double b, double c) {
    this.efficiencyCoeffs = new double[] {a, b, c};
  }
}
