package neqsim.process.equipment.powergeneration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.battery.BatteryStorage;

/**
 * Offshore energy system integrating multiple power sources with dispatch logic.
 *
 * <p>
 * Models the energy balance of an offshore installation combining:
 * </p>
 * <ul>
 * <li>Wind farm power generation (variable)</li>
 * <li>Gas turbine backup power (dispatchable)</li>
 * <li>Battery energy storage (time-shifting)</li>
 * <li>Solar panels (supplementary)</li>
 * <li>Process power demand (compressors, pumps, etc.)</li>
 * </ul>
 *
 * <h2>Dispatch Strategy</h2>
 * <p>
 * The dispatch logic follows a priority order:
 * </p>
 * <ol>
 * <li>Use wind power first (zero marginal cost)</li>
 * <li>Discharge battery if wind insufficient</li>
 * <li>Start gas turbine for remaining deficit</li>
 * <li>Charge battery with excess wind power</li>
 * <li>Curtail excess wind if battery full</li>
 * </ol>
 *
 * <h2>CO2 Emissions Tracking</h2>
 * <p>
 * Tracks CO2 emissions from gas turbine usage vs. wind power, enabling comparison
 * of power supply scenarios for offshore platforms.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * OffshoreEnergySystem energy = new OffshoreEnergySystem("Platform Power");
 *
 * // Configure wind farm
 * WindFarm wind = new WindFarm("Offshore Wind", 20);
 * wind.setRatedPowerPerTurbine(15.0e6);
 * energy.setWindFarm(wind);
 *
 * // Configure gas turbine backup
 * energy.setGasTurbineCapacity(50.0e6);    // 50 MW
 * energy.setGasTurbineEfficiency(0.35);
 *
 * // Configure battery storage
 * BatteryStorage battery = new BatteryStorage("BESS", 100.0e6 * 3600);
 * energy.setBatteryStorage(battery);
 *
 * // Set power demand
 * energy.setTotalPowerDemand(200.0e6);     // 200 MW
 *
 * energy.run();
 * double windFraction = energy.getWindPowerFraction();
 * double co2Saved = energy.getCO2Avoided();
 * }</pre>
 *
 * @author esol
 * @version 1.0
 */
public class OffshoreEnergySystem extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Wind farm for renewable power generation. */
  private WindFarm windFarm;

  /** Gas turbine rated capacity [W]. */
  private double gasTurbineCapacity = 50.0e6;

  /** Gas turbine electrical efficiency. */
  private double gasTurbineEfficiency = 0.35;

  /** Gas turbine minimum load fraction [0-1]. */
  private double gasTurbineMinLoad = 0.30;

  /** Battery storage system. */
  private BatteryStorage batteryStorage;

  /** Solar panel system. */
  private SolarPanel solarPanel;

  /** Total power demand [W]. */
  private double totalPowerDemand = 50.0e6;

  /** CO2 emission factor for gas turbine [kg CO2 / kWh]. */
  private double co2EmissionFactor = 0.55;

  /** Natural gas LHV [MJ/kg]. */
  private double gasLHV = 48.0;

  // --- Dispatch results ---

  /** Wind power delivered to load [W]. */
  private double windPowerDelivered = 0.0;

  /** Gas turbine power delivered [W]. */
  private double gasTurbinePowerDelivered = 0.0;

  /** Battery power delivered [W]. */
  private double batteryPowerDelivered = 0.0;

  /** Solar power delivered [W]. */
  private double solarPowerDelivered = 0.0;

  /** Wind power curtailed [W]. */
  private double windPowerCurtailed = 0.0;

  /** Wind power used to charge battery [W]. */
  private double windPowerToCharge = 0.0;

  /** Total power deficit (unmet demand) [W]. */
  private double powerDeficit = 0.0;

  /** CO2 emissions from gas turbine [kg/hr]. */
  private double co2Emissions = 0.0;

  /** CO2 avoided by using wind instead of gas [kg/hr]. */
  private double co2Avoided = 0.0;

  /** Gas fuel consumption rate [kg/hr]. */
  private double fuelConsumption = 0.0;

  /** Time step for battery charge/discharge [hours]. */
  private double timeStepHours = 1.0;

  /** History of hourly dispatch for time-series analysis. */
  private List<Map<String, Double>> dispatchHistory = new ArrayList<Map<String, Double>>();

  /**
   * Default constructor.
   */
  public OffshoreEnergySystem() {
    this("OffshoreEnergySystem");
  }

  /**
   * Construct with name.
   *
   * @param name equipment name
   */
  public OffshoreEnergySystem(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // 1. Get available wind power
    double windAvailable = 0.0;
    if (windFarm != null) {
      windFarm.run(id);
      windAvailable = windFarm.getPower();
    }

    // 2. Get solar power
    double solarAvailable = 0.0;
    if (solarPanel != null) {
      solarPanel.run(id);
      solarAvailable = solarPanel.getPower();
    }

    // 3. Renewable total
    double renewableTotal = windAvailable + solarAvailable;

    // 4. Dispatch logic
    double remainingDemand = totalPowerDemand;

    // 4a. Use renewables first
    double renewableUsed = Math.min(renewableTotal, remainingDemand);
    windPowerDelivered = Math.min(windAvailable, remainingDemand);
    solarPowerDelivered = Math.min(solarAvailable, remainingDemand - windPowerDelivered);
    remainingDemand -= renewableUsed;

    // 4b. Discharge battery if available
    batteryPowerDelivered = 0.0;
    if (remainingDemand > 0.0 && batteryStorage != null
        && batteryStorage.getStateOfCharge() > 0.0) {
      batteryPowerDelivered = batteryStorage.discharge(remainingDemand, timeStepHours);
      remainingDemand -= batteryPowerDelivered;
    }

    // 4c. Start gas turbine for remaining deficit
    gasTurbinePowerDelivered = 0.0;
    if (remainingDemand > 0.0 && gasTurbineCapacity > 0.0) {
      double gtLoad = Math.min(remainingDemand, gasTurbineCapacity);
      // Enforce minimum load
      double minLoad = gasTurbineCapacity * gasTurbineMinLoad;
      if (gtLoad < minLoad && remainingDemand > 0.0) {
        gtLoad = minLoad;
      }
      gasTurbinePowerDelivered = Math.min(gtLoad, gasTurbineCapacity);
      remainingDemand -= gasTurbinePowerDelivered;
    }

    // 4d. Charge battery with excess wind
    double excessRenewable = renewableTotal - renewableUsed;
    windPowerToCharge = 0.0;
    windPowerCurtailed = 0.0;
    if (excessRenewable > 0.0 && batteryStorage != null) {
      double chargeCapacity = batteryStorage.getCapacity() - batteryStorage.getStateOfCharge();
      if (chargeCapacity > 0.0) {
        windPowerToCharge = Math.min(excessRenewable, chargeCapacity / timeStepHours);
        batteryStorage.charge(windPowerToCharge, timeStepHours);
        excessRenewable -= windPowerToCharge;
      }
    }
    windPowerCurtailed = excessRenewable;

    // 4e. Power deficit
    powerDeficit = Math.max(0.0, remainingDemand);

    // 5. Emissions calculations
    // Gas turbine CO2 [kg/hr]
    double gasPowerKWh = gasTurbinePowerDelivered / 1000.0; // W to kW
    co2Emissions = gasPowerKWh * co2EmissionFactor;

    // Gas fuel consumption [kg/hr]
    if (gasTurbineEfficiency > 0.0) {
      fuelConsumption = gasTurbinePowerDelivered / (gasTurbineEfficiency * gasLHV * 1.0e6) * 3600.0;
    }

    // CO2 avoided (compared to 100% gas turbine)
    double totalPowerKWh = totalPowerDemand / 1000.0;
    double allGasCO2 = totalPowerKWh * co2EmissionFactor;
    co2Avoided = allGasCO2 - co2Emissions;

    // 6. Store dispatch snapshot
    Map<String, Double> snapshot = new LinkedHashMap<String, Double>();
    snapshot.put("windAvailable_MW", windAvailable / 1.0e6);
    snapshot.put("windDelivered_MW", windPowerDelivered / 1.0e6);
    snapshot.put("solarDelivered_MW", solarPowerDelivered / 1.0e6);
    snapshot.put("batteryDelivered_MW", batteryPowerDelivered / 1.0e6);
    snapshot.put("gasTurbineDelivered_MW", gasTurbinePowerDelivered / 1.0e6);
    snapshot.put("curtailed_MW", windPowerCurtailed / 1.0e6);
    snapshot.put("demand_MW", totalPowerDemand / 1.0e6);
    snapshot.put("co2_kg_hr", co2Emissions);
    dispatchHistory.add(snapshot);

    setCalculationIdentifier(id);
  }

  /**
   * Run hourly dispatch simulation over wind speed time series.
   *
   * @param windSpeeds array of hourly wind speeds [m/s]
   */
  public void runHourlyDispatch(double[] windSpeeds) {
    dispatchHistory.clear();
    timeStepHours = 1.0;
    for (int i = 0; i < windSpeeds.length; i++) {
      if (windFarm != null) {
        windFarm.setWindSpeed(windSpeeds[i]);
      }
      run();
    }
  }

  /**
   * Get fraction of total power from wind.
   *
   * @return wind power fraction [0-1]
   */
  public double getWindPowerFraction() {
    double totalSupply = windPowerDelivered + gasTurbinePowerDelivered
        + batteryPowerDelivered + solarPowerDelivered;
    return totalSupply > 0 ? windPowerDelivered / totalSupply : 0.0;
  }

  /**
   * Get CO2 emissions [kg/hr].
   *
   * @return CO2 emissions from gas turbine [kg/hr]
   */
  public double getCO2Emissions() {
    return co2Emissions;
  }

  /**
   * Get CO2 avoided by using renewables [kg/hr].
   *
   * @return CO2 avoided [kg/hr]
   */
  public double getCO2Avoided() {
    return co2Avoided;
  }

  /**
   * Get annual CO2 avoided if system runs continuously [tonnes/year].
   *
   * @return annual CO2 avoided [tonnes/year]
   */
  public double getAnnualCO2Avoided() {
    return co2Avoided * 8760.0 / 1000.0;
  }

  /**
   * Get gas fuel consumption [kg/hr].
   *
   * @return fuel consumption [kg/hr]
   */
  public double getFuelConsumption() {
    return fuelConsumption;
  }

  /**
   * Get total power delivered [W].
   *
   * @return total power delivered
   */
  public double getTotalPowerDelivered() {
    return windPowerDelivered + gasTurbinePowerDelivered
        + batteryPowerDelivered + solarPowerDelivered;
  }

  /**
   * Get wind power delivered [W].
   *
   * @return wind power [W]
   */
  public double getWindPowerDelivered() {
    return windPowerDelivered;
  }

  /**
   * Get gas turbine power delivered [W].
   *
   * @return gas turbine power [W]
   */
  public double getGasTurbinePowerDelivered() {
    return gasTurbinePowerDelivered;
  }

  /**
   * Get battery power delivered [W].
   *
   * @return battery power [W]
   */
  public double getBatteryPowerDelivered() {
    return batteryPowerDelivered;
  }

  /**
   * Get solar power delivered [W].
   *
   * @return solar power [W]
   */
  public double getSolarPowerDelivered() {
    return solarPowerDelivered;
  }

  /**
   * Get wind power curtailed [W].
   *
   * @return curtailed wind power [W]
   */
  public double getWindPowerCurtailed() {
    return windPowerCurtailed;
  }

  /**
   * Get unmet power demand [W].
   *
   * @return power deficit [W]
   */
  public double getPowerDeficit() {
    return powerDeficit;
  }

  /**
   * Get dispatch history for time-series analysis.
   *
   * @return list of dispatch snapshots
   */
  public List<Map<String, Double>> getDispatchHistory() {
    return dispatchHistory;
  }

  /**
   * Clear dispatch history.
   */
  public void clearDispatchHistory() {
    dispatchHistory.clear();
  }

  /**
   * Set wind farm.
   *
   * @param windFarm wind farm object
   */
  public void setWindFarm(WindFarm windFarm) {
    this.windFarm = windFarm;
  }

  /**
   * Get wind farm.
   *
   * @return wind farm object
   */
  public WindFarm getWindFarm() {
    return windFarm;
  }

  /**
   * Set battery storage system.
   *
   * @param battery battery storage object
   */
  public void setBatteryStorage(BatteryStorage battery) {
    this.batteryStorage = battery;
  }

  /**
   * Get battery storage.
   *
   * @return battery storage
   */
  public BatteryStorage getBatteryStorage() {
    return batteryStorage;
  }

  /**
   * Set solar panel system.
   *
   * @param solar solar panel object
   */
  public void setSolarPanel(SolarPanel solar) {
    this.solarPanel = solar;
  }

  /**
   * Set gas turbine rated capacity [W].
   *
   * @param capacity gas turbine capacity [W]
   */
  public void setGasTurbineCapacity(double capacity) {
    this.gasTurbineCapacity = capacity;
  }

  /**
   * Get gas turbine capacity [W].
   *
   * @return gas turbine capacity [W]
   */
  public double getGasTurbineCapacity() {
    return gasTurbineCapacity;
  }

  /**
   * Set gas turbine efficiency [0-1].
   *
   * @param efficiency gas turbine efficiency
   */
  public void setGasTurbineEfficiency(double efficiency) {
    this.gasTurbineEfficiency = efficiency;
  }

  /**
   * Set gas turbine minimum load fraction [0-1].
   *
   * @param minLoad minimum load fraction
   */
  public void setGasTurbineMinLoad(double minLoad) {
    this.gasTurbineMinLoad = minLoad;
  }

  /**
   * Set total power demand [W].
   *
   * @param demand total power demand [W]
   */
  public void setTotalPowerDemand(double demand) {
    this.totalPowerDemand = demand;
  }

  /**
   * Get total power demand [W].
   *
   * @return total power demand [W]
   */
  public double getTotalPowerDemand() {
    return totalPowerDemand;
  }

  /**
   * Set CO2 emission factor [kg CO2 / kWh].
   *
   * @param factor emission factor
   */
  public void setCO2EmissionFactor(double factor) {
    this.co2EmissionFactor = factor;
  }

  /**
   * Set time step for dispatch [hours].
   *
   * @param hours time step size
   */
  public void setTimeStepHours(double hours) {
    this.timeStepHours = hours;
  }
}
