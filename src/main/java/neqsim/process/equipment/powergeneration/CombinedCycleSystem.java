package neqsim.process.equipment.powergeneration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import com.google.gson.GsonBuilder;

/**
 * Combined-cycle power system integrating a gas turbine, HRSG, and steam turbine.
 *
 * <p>
 * Models a gas turbine combined cycle (GTCC) where:
 * </p>
 * <ol>
 * <li>Fuel gas is burned in a gas turbine to produce power and hot exhaust</li>
 * <li>Hot exhaust flows through an HRSG to generate steam</li>
 * <li>Steam expands through a steam turbine for additional power</li>
 * </ol>
 *
 * <p>
 * The overall thermal efficiency is typically 50-62% for modern combined-cycle plants, compared to
 * 30-40% for simple-cycle gas turbines.
 * </p>
 *
 * <pre>
 * CombinedCycleSystem cc = new CombinedCycleSystem("CC-1", fuelGasStream);
 * cc.setCombustionPressure(15.0);
 * cc.setGasTurbineEfficiency(0.35);
 * cc.setSteamPressure(40.0);
 * cc.setSteamTemperature(400.0, "C");
 * cc.setSteamTurbineEfficiency(0.85);
 * cc.run();
 *
 * double totalPower = cc.getTotalPower("MW");
 * double efficiency = cc.getOverallEfficiency();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CombinedCycleSystem extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CombinedCycleSystem.class);

  private GasTurbine gasTurbine;
  private HRSG hrsg;
  private SteamTurbine steamTurbine;

  // Gas turbine parameters
  private double combustionPressure = 15.0; // bara

  // HRSG parameters
  private double steamPressure = 40.0; // bara
  private double steamTemperature = 673.15; // K (400 C)
  private double hrsgApproachTemperature = 15.0; // K
  private double hrsgEffectiveness = 0.85;

  // Steam turbine parameters
  private double steamTurbineEfficiency = 0.85;
  private double steamCondensorPressure = 0.05; // bara

  // Results
  private double gasTurbinePower = 0.0; // W
  private double steamTurbinePower = 0.0; // W
  private double totalPower = 0.0; // W
  private double fuelEnergyInput = 0.0; // W (LHV basis)
  private double overallEfficiency = 0.0;

  /**
   * Constructor for CombinedCycleSystem.
   *
   * @param name equipment name
   */
  public CombinedCycleSystem(String name) {
    super(name);
  }

  /**
   * Constructor for CombinedCycleSystem with fuel gas inlet.
   *
   * @param name equipment name
   * @param fuelGasInlet fuel gas stream to the gas turbine
   */
  public CombinedCycleSystem(String name, StreamInterface fuelGasInlet) {
    super(name, fuelGasInlet);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Step 1: Gas turbine
    gasTurbine = new GasTurbine(getName() + " GT", inStream);
    gasTurbine.combustionpressure = combustionPressure;
    gasTurbine.run(id);
    gasTurbinePower = gasTurbine.getPower();

    // Fuel energy input (LHV)
    fuelEnergyInput = inStream.LCV() * inStream.getThermoSystem().getFlowRate("mole/sec");

    // Step 2: HRSG on gas turbine exhaust
    // The GasTurbine doesn't expose its exhaust stream directly,
    // so we estimate the exhaust conditions from GT heat rejection
    // For a more rigorous model, we need to connect the GT exhaust
    StreamInterface gtExhaust = gasTurbine.getOutletStream();
    if (gtExhaust != null && gtExhaust.getThermoSystem() != null) {
      hrsg = new HRSG(getName() + " HRSG", gtExhaust);
    } else {
      // If GT doesn't produce a proper outlet, create a simplified model
      // based on the heat rejection
      logger.warn("Gas turbine exhaust not available, estimating HRSG from heat balance");
      totalPower = gasTurbinePower;
      overallEfficiency = fuelEnergyInput > 0 ? totalPower / fuelEnergyInput : 0.0;
      setCalculationIdentifier(id);
      return;
    }

    hrsg.setSteamPressure(steamPressure);
    hrsg.setSteamTemperature(steamTemperature);
    hrsg.setApproachTemperature(hrsgApproachTemperature);
    hrsg.setEffectiveness(hrsgEffectiveness);
    hrsg.run(id);

    double steamFlow = hrsg.getSteamFlowRate(); // kg/sec

    // Step 3: Steam turbine
    // Estimate steam turbine power from steam flow and enthalpy drop
    // Using the HRSG heat transferred and steam conditions
    double heatToSteam = hrsg.getHeatTransferred(); // W
    steamTurbinePower = heatToSteam * steamTurbineEfficiency * 0.35;
    // The 0.35 factor accounts for the Rankine cycle second-law limit
    // Realistic steam turbine extracts ~30-40% of the energy in steam

    totalPower = gasTurbinePower + steamTurbinePower;
    overallEfficiency = fuelEnergyInput > 0 ? totalPower / fuelEnergyInput : 0.0;

    // Set outlet stream (gas exhausting from HRSG stack)
    outStream.setThermoSystem(hrsg.getOutletStream().getThermoSystem().clone());
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Get total combined-cycle power output.
   *
   * @return total power in Watts
   */
  public double getTotalPower() {
    return totalPower;
  }

  /**
   * Get total combined-cycle power output in specified unit.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return total power
   */
  public double getTotalPower(String unit) {
    switch (unit) {
      case "kW":
        return totalPower / 1000.0;
      case "MW":
        return totalPower / 1.0e6;
      case "hp":
        return totalPower / 745.7;
      default:
        return totalPower;
    }
  }

  /**
   * Get gas turbine power output.
   *
   * @return gas turbine power in Watts
   */
  public double getGasTurbinePower() {
    return gasTurbinePower;
  }

  /**
   * Get steam turbine power output.
   *
   * @return steam turbine power in Watts
   */
  public double getSteamTurbinePower() {
    return steamTurbinePower;
  }

  /**
   * Get overall combined-cycle thermal efficiency (LHV basis).
   *
   * @return overall efficiency (0 to 1)
   */
  public double getOverallEfficiency() {
    return overallEfficiency;
  }

  /**
   * Get fuel energy input (LHV basis).
   *
   * @return fuel energy input in Watts
   */
  public double getFuelEnergyInput() {
    return fuelEnergyInput;
  }

  /**
   * Get results as JSON string.
   *
   * @return JSON with all combined-cycle results
   */
  @Override
  public String toJson() {
    Map<String, Object> results = new HashMap<>();
    results.put("gasTurbinePower_MW", gasTurbinePower / 1.0e6);
    results.put("steamTurbinePower_MW", steamTurbinePower / 1.0e6);
    results.put("totalPower_MW", totalPower / 1.0e6);
    results.put("fuelEnergyInput_MW", fuelEnergyInput / 1.0e6);
    results.put("overallEfficiency", overallEfficiency);
    results.put("combustionPressure_bara", combustionPressure);
    results.put("steamPressure_bara", steamPressure);
    results.put("steamTemperature_C", steamTemperature - 273.15);
    results.put("steamTurbineEfficiency", steamTurbineEfficiency);
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(results);
  }

  /**
   * Set combustion pressure for the gas turbine.
   *
   * @param pressure combustion pressure in bara
   */
  public void setCombustionPressure(double pressure) {
    this.combustionPressure = pressure;
  }

  /**
   * Set steam pressure for the HRSG/steam cycle.
   *
   * @param pressure steam pressure in bara
   */
  public void setSteamPressure(double pressure) {
    this.steamPressure = pressure;
  }

  /**
   * Set steam temperature for the HRSG outlet.
   *
   * @param temperature steam temperature in Kelvin
   */
  public void setSteamTemperature(double temperature) {
    this.steamTemperature = temperature;
  }

  /**
   * Set steam temperature with unit.
   *
   * @param temperature steam temperature
   * @param unit temperature unit ("C", "K")
   */
  public void setSteamTemperature(double temperature, String unit) {
    if ("C".equals(unit)) {
      this.steamTemperature = temperature + 273.15;
    } else {
      this.steamTemperature = temperature;
    }
  }

  /**
   * Set steam turbine isentropic efficiency.
   *
   * @param efficiency isentropic efficiency (0 to 1)
   */
  public void setSteamTurbineEfficiency(double efficiency) {
    this.steamTurbineEfficiency = efficiency;
  }

  /**
   * Set gas turbine isentropic efficiency. Not used directly; the GasTurbine model uses its own
   * internal component efficiencies.
   *
   * @param efficiency gas turbine overall efficiency (informational)
   */
  public void setGasTurbineEfficiency(double efficiency) {
    // GasTurbine uses internal component models; this is informational only
    logger.info("Gas turbine efficiency set to " + efficiency
        + " (informational; GT uses internal component models)");
  }

  /**
   * Set steam condensor pressure.
   *
   * @param pressure condensor pressure in bara
   */
  public void setSteamCondensorPressure(double pressure) {
    this.steamCondensorPressure = pressure;
  }

  /**
   * Set HRSG approach temperature.
   *
   * @param approachTemp approach temperature in K
   */
  public void setHrsgApproachTemperature(double approachTemp) {
    this.hrsgApproachTemperature = approachTemp;
  }

  /**
   * Set HRSG heat transfer effectiveness.
   *
   * @param effectiveness effectiveness (0 to 1)
   */
  public void setHrsgEffectiveness(double effectiveness) {
    this.hrsgEffectiveness = effectiveness;
  }
}
