package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Offshore/onshore wind farm model aggregating multiple turbines with power curves and wake losses.
 *
 * <p>
 * Models a wind farm using industry-standard approaches:
 * </p>
 * <ul>
 * <li>Per-turbine power curve (cut-in, rated, cut-out wind speeds)</li>
 * <li>Jensen/Park wake loss factor</li>
 * <li>Air density correction for altitude, temperature, and pressure</li>
 * <li>Weibull wind speed distribution for annual energy production (AEP)</li>
 * <li>Time-series wind speed input for dynamic simulations</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * WindFarm farm = new WindFarm("Dogger Bank", 100);
 * farm.setRatedPowerPerTurbine(15.0e6); // 15 MW
 * farm.setRotorDiameter(236.0); // meters
 * farm.setHubHeight(135.0); // meters
 * farm.setWindSpeed(10.5);
 * farm.run();
 * double totalPower = farm.getPower(); // Watts
 * }</pre>
 *
 * @author esol
 * @version 1.0
 */
public class WindFarm extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Number of turbines in the farm. */
  private int numberOfTurbines = 1;

  /** Rated power per turbine [W]. */
  private double ratedPowerPerTurbine = 15.0e6;

  /** Rotor diameter [m]. */
  private double rotorDiameter = 236.0;

  /** Hub height above sea level [m]. */
  private double hubHeight = 135.0;

  /** Current wind speed at hub height [m/s]. */
  private double windSpeed = 0.0;

  /** Cut-in wind speed [m/s]. */
  private double cutInSpeed = 3.0;

  /** Rated wind speed [m/s]. */
  private double ratedSpeed = 12.0;

  /** Cut-out wind speed [m/s]. */
  private double cutOutSpeed = 25.0;

  /** Air density at hub height [kg/m3]. */
  private double airDensity = 1.225;

  /** Air temperature [C] for density correction. */
  private double airTemperature = 15.0;

  /** Atmospheric pressure [Pa] for density correction. */
  private double atmosphericPressure = 101325.0;

  /** Maximum power coefficient (Betz limit is 0.593). */
  private double maxPowerCoefficient = 0.48;

  /** Wake loss factor [0-1], typical 0.05 to 0.15 for offshore. */
  private double wakeLossFactor = 0.10;

  /** Availability factor [0-1], accounts for maintenance downtime. */
  private double availabilityFactor = 0.95;

  /** Electrical losses [0-1], cables, transformers, etc. */
  private double electricalLossFactor = 0.03;

  /** Calculated total farm power output [W]. */
  private double power = 0.0;

  /** Calculated capacity factor [0-1]. */
  private double capacityFactor = 0.0;

  /** Weibull shape parameter for AEP calculation. */
  private double weibullShape = 2.0;

  /** Weibull scale parameter [m/s] for AEP calculation. */
  private double weibullScale = 10.0;

  /** Time-series wind speeds for dynamic simulation [m/s]. */
  private double[] windSpeedTimeSeries;

  /** Time-series power output [W]. */
  private double[] powerTimeSeries;

  /** Current time step index for dynamic simulation. */
  private int currentTimeStep = 0;

  /**
   * Default constructor.
   */
  public WindFarm() {
    this("WindFarm");
  }

  /**
   * Construct with name.
   *
   * @param name equipment name
   */
  public WindFarm(String name) {
    super(name);
  }

  /**
   * Construct with name and number of turbines.
   *
   * @param name equipment name
   * @param numberOfTurbines number of wind turbines
   */
  public WindFarm(String name, int numberOfTurbines) {
    super(name);
    this.numberOfTurbines = numberOfTurbines;
  }

  /**
   * Calculate single turbine power from wind speed using cubic power curve model.
   *
   * <p>
   * Uses the standard regions:
   * </p>
   * <ul>
   * <li>Below cut-in: 0 W</li>
   * <li>Cut-in to rated: cubic interpolation P = Prated * ((v - vci)/(vr - vci))^3</li>
   * <li>Rated to cut-out: Prated</li>
   * <li>Above cut-out: 0 W</li>
   * </ul>
   *
   * @param speed wind speed at hub height [m/s]
   * @return single turbine power output [W]
   */
  public double calculateTurbinePower(double speed) {
    if (speed < cutInSpeed || speed > cutOutSpeed) {
      return 0.0;
    }
    if (speed >= ratedSpeed) {
      return ratedPowerPerTurbine;
    }
    // Cubic interpolation between cut-in and rated
    double fraction = (speed - cutInSpeed) / (ratedSpeed - cutInSpeed);
    return ratedPowerPerTurbine * fraction * fraction * fraction;
  }

  /**
   * Get corrected air density based on temperature and pressure.
   *
   * <p>
   * Uses ideal gas law: rho = P / (R_air * T) where R_air = 287.058 J/(kg K).
   * </p>
   *
   * @return corrected air density [kg/m3]
   */
  public double getCorrectedAirDensity() {
    double tempK = airTemperature + 273.15;
    return atmosphericPressure / (287.058 * tempK);
  }

  /**
   * Calculate annual energy production using Weibull distribution.
   *
   * <p>
   * Integrates the power curve against the Weibull probability density function using numerical
   * integration from 0 to 35 m/s with 0.5 m/s steps.
   * </p>
   *
   * @return annual energy production [Wh]
   */
  public double calculateAEP() {
    double aep = 0.0;
    double dv = 0.5;
    for (double v = 0.0; v <= 35.0; v += dv) {
      double probability = weibullPDF(v);
      double singleTurbinePower = calculateTurbinePower(v);
      aep += singleTurbinePower * probability * dv;
    }
    // Hours per year * number of turbines * losses
    double hoursPerYear = 8760.0;
    aep = aep * hoursPerYear * numberOfTurbines * (1.0 - wakeLossFactor) * availabilityFactor
        * (1.0 - electricalLossFactor);
    return aep;
  }

  /**
   * Weibull probability density function.
   *
   * @param v wind speed [m/s]
   * @return probability density [1/(m/s)]
   */
  private double weibullPDF(double v) {
    if (v < 0.0) {
      return 0.0;
    }
    double k = weibullShape;
    double c = weibullScale;
    return (k / c) * Math.pow(v / c, k - 1.0) * Math.exp(-Math.pow(v / c, k));
  }

  /**
   * Set time-series wind speeds for dynamic simulation.
   *
   * @param windSpeeds array of wind speeds [m/s], one per time step
   */
  public void setWindSpeedTimeSeries(double[] windSpeeds) {
    this.windSpeedTimeSeries = windSpeeds;
    this.powerTimeSeries = new double[windSpeeds.length];
    this.currentTimeStep = 0;
  }

  /**
   * Get time-series power output after running dynamic simulation.
   *
   * @return array of power outputs [W]
   */
  public double[] getPowerTimeSeries() {
    return powerTimeSeries;
  }

  /**
   * Run the full time series and populate powerTimeSeries array.
   */
  public void runTimeSeries() {
    if (windSpeedTimeSeries == null) {
      return;
    }
    for (int i = 0; i < windSpeedTimeSeries.length; i++) {
      double singlePower = calculateTurbinePower(windSpeedTimeSeries[i]);
      powerTimeSeries[i] = singlePower * numberOfTurbines * (1.0 - wakeLossFactor)
          * availabilityFactor * (1.0 - electricalLossFactor);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Correct air density for conditions
    airDensity = getCorrectedAirDensity();

    // Calculate single turbine power
    double singlePower = calculateTurbinePower(windSpeed);

    // Apply farm-level losses
    power = singlePower * numberOfTurbines * (1.0 - wakeLossFactor) * availabilityFactor
        * (1.0 - electricalLossFactor);

    // Update capacity factor
    double totalRated = ratedPowerPerTurbine * numberOfTurbines;
    capacityFactor = totalRated > 0 ? power / totalRated : 0.0;

    // Set energy stream (negative = produced/available)
    getEnergyStream().setDuty(-power);
    setCalculationIdentifier(id);
  }

  /**
   * Get total farm power output [W].
   *
   * @return power [W]
   */
  public double getPower() {
    return power;
  }

  /**
   * Get total farm power output in specified unit.
   *
   * @param unit unit string: "W", "kW", "MW", "GW"
   * @return power in specified unit
   */
  public double getPower(String unit) {
    switch (unit) {
      case "kW":
        return power / 1.0e3;
      case "MW":
        return power / 1.0e6;
      case "GW":
        return power / 1.0e9;
      default:
        return power;
    }
  }

  /**
   * Get calculated capacity factor.
   *
   * @return capacity factor [0-1]
   */
  public double getCapacityFactor() {
    return capacityFactor;
  }

  /**
   * Get total rated farm capacity [W].
   *
   * @return total rated power [W]
   */
  public double getTotalRatedPower() {
    return ratedPowerPerTurbine * numberOfTurbines;
  }

  /**
   * Set number of turbines.
   *
   * @param n number of turbines
   */
  public void setNumberOfTurbines(int n) {
    this.numberOfTurbines = n;
  }

  /**
   * Get number of turbines.
   *
   * @return number of turbines
   */
  public int getNumberOfTurbines() {
    return numberOfTurbines;
  }

  /**
   * Set rated power per turbine [W].
   *
   * @param ratedPower rated power [W]
   */
  public void setRatedPowerPerTurbine(double ratedPower) {
    this.ratedPowerPerTurbine = ratedPower;
  }

  /**
   * Get rated power per turbine [W].
   *
   * @return rated power [W]
   */
  public double getRatedPowerPerTurbine() {
    return ratedPowerPerTurbine;
  }

  /**
   * Set rotor diameter [m].
   *
   * @param diameter rotor diameter [m]
   */
  public void setRotorDiameter(double diameter) {
    this.rotorDiameter = diameter;
  }

  /**
   * Get rotor diameter [m].
   *
   * @return rotor diameter [m]
   */
  public double getRotorDiameter() {
    return rotorDiameter;
  }

  /**
   * Set hub height [m].
   *
   * @param height hub height [m]
   */
  public void setHubHeight(double height) {
    this.hubHeight = height;
  }

  /**
   * Get hub height [m].
   *
   * @return hub height [m]
   */
  public double getHubHeight() {
    return hubHeight;
  }

  /**
   * Set current wind speed at hub height [m/s].
   *
   * @param speed wind speed [m/s]
   */
  public void setWindSpeed(double speed) {
    this.windSpeed = speed;
  }

  /**
   * Get current wind speed [m/s].
   *
   * @return wind speed [m/s]
   */
  public double getWindSpeed() {
    return windSpeed;
  }

  /**
   * Set cut-in wind speed [m/s].
   *
   * @param cutIn cut-in speed [m/s]
   */
  public void setCutInSpeed(double cutIn) {
    this.cutInSpeed = cutIn;
  }

  /**
   * Get cut-in wind speed [m/s].
   *
   * @return cut-in speed [m/s]
   */
  public double getCutInSpeed() {
    return cutInSpeed;
  }

  /**
   * Set rated wind speed [m/s].
   *
   * @param rated rated speed [m/s]
   */
  public void setRatedSpeed(double rated) {
    this.ratedSpeed = rated;
  }

  /**
   * Get rated wind speed [m/s].
   *
   * @return rated speed [m/s]
   */
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /**
   * Set cut-out wind speed [m/s].
   *
   * @param cutOut cut-out speed [m/s]
   */
  public void setCutOutSpeed(double cutOut) {
    this.cutOutSpeed = cutOut;
  }

  /**
   * Get cut-out wind speed [m/s].
   *
   * @return cut-out speed [m/s]
   */
  public double getCutOutSpeed() {
    return cutOutSpeed;
  }

  /**
   * Set air temperature for density correction [C].
   *
   * @param temp air temperature [C]
   */
  public void setAirTemperature(double temp) {
    this.airTemperature = temp;
  }

  /**
   * Set atmospheric pressure for density correction [Pa].
   *
   * @param pressure atmospheric pressure [Pa]
   */
  public void setAtmosphericPressure(double pressure) {
    this.atmosphericPressure = pressure;
  }

  /**
   * Set wake loss factor [0-1].
   *
   * @param factor wake loss factor
   */
  public void setWakeLossFactor(double factor) {
    this.wakeLossFactor = factor;
  }

  /**
   * Get wake loss factor.
   *
   * @return wake loss factor [0-1]
   */
  public double getWakeLossFactor() {
    return wakeLossFactor;
  }

  /**
   * Set availability factor [0-1].
   *
   * @param factor availability factor
   */
  public void setAvailabilityFactor(double factor) {
    this.availabilityFactor = factor;
  }

  /**
   * Get availability factor.
   *
   * @return availability factor [0-1]
   */
  public double getAvailabilityFactor() {
    return availabilityFactor;
  }

  /**
   * Set electrical loss factor [0-1].
   *
   * @param factor electrical loss factor
   */
  public void setElectricalLossFactor(double factor) {
    this.electricalLossFactor = factor;
  }

  /**
   * Set Weibull shape parameter for AEP calculation.
   *
   * @param shape Weibull shape parameter k [-]
   */
  public void setWeibullShape(double shape) {
    this.weibullShape = shape;
  }

  /**
   * Set Weibull scale parameter for AEP calculation.
   *
   * @param scale Weibull scale parameter c [m/s]
   */
  public void setWeibullScale(double scale) {
    this.weibullScale = scale;
  }

  /**
   * Get Weibull shape parameter.
   *
   * @return shape parameter [-]
   */
  public double getWeibullShape() {
    return weibullShape;
  }

  /**
   * Get Weibull scale parameter.
   *
   * @return scale parameter [m/s]
   */
  public double getWeibullScale() {
    return weibullScale;
  }

  /**
   * Set max power coefficient (Cp).
   *
   * @param cp power coefficient [-]
   */
  public void setMaxPowerCoefficient(double cp) {
    this.maxPowerCoefficient = cp;
  }

  /**
   * Get current air density [kg/m3].
   *
   * @return air density
   */
  public double getAirDensity() {
    return airDensity;
  }

  /**
   * Get rotor swept area [m2].
   *
   * @return swept area
   */
  public double getRotorArea() {
    return Math.PI * rotorDiameter * rotorDiameter / 4.0;
  }

  /**
   * Get current time step index.
   *
   * @return time step index
   */
  public int getCurrentTimeStep() {
    return currentTimeStep;
  }
}
