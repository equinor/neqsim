package neqsim.process.equipment.heatexchanger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.report.ReportConfig;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Duty-controlled fired heater with thermal efficiency modeling.
 *
 * <p>
 * Models a process heater where the process stream is heated by burning fuel. Unlike a simple
 * {@link Heater} which applies duty directly, this class accounts for thermal efficiency, fuel
 * consumption, stack losses, and CO2 emissions.
 * </p>
 *
 * <p>
 * The user specifies the desired outlet temperature, and the heater calculates the required
 * absorbed duty, fuel consumption (based on thermal efficiency and fuel LHV), and flue gas
 * emissions.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * FiredHeater heater = new FiredHeater("Crude Heater", feedStream);
 * heater.setOutTemperature(273.15 + 350.0); // Outlet temperature in K
 * heater.setThermalEfficiency(0.85); // 85% efficiency
 * heater.setFuelLHV(48.0e6); // J/kg (natural gas)
 * heater.setFuelCO2Factor(2.75); // kg CO2 per kg fuel
 * heater.run();
 * double fuelRate = heater.getFuelConsumption("kg/hr");
 * double co2 = heater.getCO2Emissions("kg/hr");
 * double firedDuty = heater.getFiredDuty("kW");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class FiredHeater extends Heater {
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FiredHeater.class);

  /** Thermal efficiency (fraction, 0.0 to 1.0). Default 0.85. */
  private double thermalEfficiency = 0.85;

  /** Fuel lower heating value in J/kg. Default 48 MJ/kg (natural gas). */
  private double fuelLHV = 48.0e6;

  /** CO2 emission factor in kg CO2 per kg fuel. Default 2.75 (natural gas). */
  private double fuelCO2Factor = 2.75;

  /** NOx emission factor in kg NOx per GJ fired duty. Default 0.08. */
  private double noxFactor = 0.08;

  /** Stack temperature in K. Default 423.15 K (150 C). */
  private double stackTemperature = 423.15;

  // Computed results
  private double absorbedDuty = 0.0; // W
  private double firedDuty = 0.0; // W
  private double fuelConsumptionKgPerSec = 0.0;
  private double co2EmissionsKgPerSec = 0.0;
  private double noxEmissionsKgPerSec = 0.0;
  private double stackLoss = 0.0; // W

  /**
   * Creates a fired heater with the specified name.
   *
   * @param name equipment name
   */
  public FiredHeater(String name) {
    super(name);
  }

  /**
   * Creates a fired heater with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream inlet process stream
   */
  public FiredHeater(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the thermal efficiency of the fired heater.
   *
   * @param efficiency thermal efficiency as fraction (0.0 to 1.0)
   */
  public void setThermalEfficiency(double efficiency) {
    this.thermalEfficiency = efficiency;
  }

  /**
   * Gets the thermal efficiency.
   *
   * @return thermal efficiency as fraction
   */
  public double getThermalEfficiency() {
    return thermalEfficiency;
  }

  /**
   * Sets the fuel lower heating value.
   *
   * @param lhvJPerKg fuel LHV in J/kg
   */
  public void setFuelLHV(double lhvJPerKg) {
    this.fuelLHV = lhvJPerKg;
  }

  /**
   * Gets the fuel lower heating value.
   *
   * @return LHV in J/kg
   */
  public double getFuelLHV() {
    return fuelLHV;
  }

  /**
   * Sets the CO2 emission factor per kg of fuel burned.
   *
   * @param factor kg CO2 per kg fuel
   */
  public void setFuelCO2Factor(double factor) {
    this.fuelCO2Factor = factor;
  }

  /**
   * Sets the NOx emission factor per GJ of fired duty.
   *
   * @param factor kg NOx per GJ
   */
  public void setNoxFactor(double factor) {
    this.noxFactor = factor;
  }

  /**
   * Sets the stack (flue gas exit) temperature.
   *
   * @param temperatureK stack temperature in K
   */
  public void setStackTemperature(double temperatureK) {
    this.stackTemperature = temperatureK;
  }

  /**
   * Gets the stack temperature.
   *
   * @return stack temperature in K
   */
  public double getStackTemperature() {
    return stackTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run the parent Heater logic to compute absorbed duty
    super.run(id);

    // Get the absorbed duty (energy transferred to process stream)
    absorbedDuty = Math.abs(getDuty());

    // Calculate fired duty accounting for thermal efficiency
    if (thermalEfficiency > 0 && thermalEfficiency <= 1.0) {
      firedDuty = absorbedDuty / thermalEfficiency;
    } else {
      firedDuty = absorbedDuty;
    }

    stackLoss = firedDuty - absorbedDuty;

    // Fuel consumption
    if (fuelLHV > 0) {
      fuelConsumptionKgPerSec = firedDuty / fuelLHV;
    } else {
      fuelConsumptionKgPerSec = 0.0;
    }

    // Emissions
    co2EmissionsKgPerSec = fuelConsumptionKgPerSec * fuelCO2Factor;
    noxEmissionsKgPerSec = (firedDuty / 1.0e9) * noxFactor; // GJ basis
  }

  /**
   * Gets the absorbed duty (heat transferred to process stream).
   *
   * @param unit unit string ("W", "kW", "MW", "BTU/hr")
   * @return absorbed duty in specified units
   */
  public double getAbsorbedDuty(String unit) {
    return convertPower(absorbedDuty, unit);
  }

  /**
   * Gets the total fired duty (fuel energy input).
   *
   * @param unit unit string ("W", "kW", "MW", "BTU/hr")
   * @return fired duty in specified units
   */
  public double getFiredDuty(String unit) {
    return convertPower(firedDuty, unit);
  }

  /**
   * Gets the stack heat loss.
   *
   * @param unit unit string ("W", "kW", "MW", "BTU/hr")
   * @return stack loss in specified units
   */
  public double getStackLoss(String unit) {
    return convertPower(stackLoss, unit);
  }

  /**
   * Gets the fuel consumption rate.
   *
   * @param unit unit string ("kg/hr", "kg/sec", "tonnes/hr")
   * @return fuel consumption in specified units
   */
  public double getFuelConsumption(String unit) {
    if ("kg/hr".equals(unit)) {
      return fuelConsumptionKgPerSec * 3600.0;
    } else if ("tonnes/hr".equals(unit)) {
      return fuelConsumptionKgPerSec * 3.6;
    }
    return fuelConsumptionKgPerSec;
  }

  /**
   * Gets the CO2 emissions rate.
   *
   * @param unit unit string ("kg/hr", "kg/sec", "tonnes/hr")
   * @return CO2 emissions in specified units
   */
  public double getCO2Emissions(String unit) {
    if ("kg/hr".equals(unit)) {
      return co2EmissionsKgPerSec * 3600.0;
    } else if ("tonnes/hr".equals(unit)) {
      return co2EmissionsKgPerSec * 3.6;
    }
    return co2EmissionsKgPerSec;
  }

  /**
   * Gets the NOx emissions rate.
   *
   * @param unit unit string ("kg/hr", "kg/sec", "tonnes/hr")
   * @return NOx emissions in specified units
   */
  public double getNOxEmissions(String unit) {
    if ("kg/hr".equals(unit)) {
      return noxEmissionsKgPerSec * 3600.0;
    } else if ("tonnes/hr".equals(unit)) {
      return noxEmissionsKgPerSec * 3.6;
    }
    return noxEmissionsKgPerSec;
  }

  /**
   * Converts power from Watts to the specified unit.
   *
   * @param valueW power in Watts
   * @param unit target unit
   * @return converted value
   */
  private double convertPower(double valueW, String unit) {
    if ("kW".equals(unit)) {
      return valueW / 1.0e3;
    } else if ("MW".equals(unit)) {
      return valueW / 1.0e6;
    } else if ("BTU/hr".equals(unit)) {
      return valueW * 3.412142;
    }
    return valueW; // default Watts
  }

  /**
   * Returns a JSON report of the fired heater performance.
   *
   * @return JSON string with heater performance data
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("equipmentType", "FiredHeater");
    result.put("name", getName());

    Map<String, Object> design = new LinkedHashMap<>();
    design.put("thermalEfficiency", thermalEfficiency);
    design.put("fuelLHV_MJperkg", fuelLHV / 1.0e6);
    design.put("stackTemperature_C", stackTemperature - 273.15);
    design.put("fuelCO2Factor_kgCO2perKgFuel", fuelCO2Factor);
    result.put("designParameters", design);

    Map<String, Object> performance = new LinkedHashMap<>();
    if (getInletStream() != null) {
      performance.put("inletTemperature_C", getInletStream().getTemperature() - 273.15);
    }
    if (getOutletStream() != null) {
      performance.put("outletTemperature_C", getOutletStream().getTemperature() - 273.15);
    }
    performance.put("absorbedDuty_kW", absorbedDuty / 1000.0);
    performance.put("firedDuty_kW", firedDuty / 1000.0);
    performance.put("stackLoss_kW", stackLoss / 1000.0);
    performance.put("fuelConsumption_kgPerHr", fuelConsumptionKgPerSec * 3600.0);
    result.put("performance", performance);

    Map<String, Object> emissions = new LinkedHashMap<>();
    emissions.put("CO2_kgPerHr", co2EmissionsKgPerSec * 3600.0);
    emissions.put("NOx_kgPerHr", noxEmissionsKgPerSec * 3600.0);
    emissions.put("CO2_tonnesPerYear", co2EmissionsKgPerSec * 3600.0 * 8760.0 / 1000.0);
    result.put("emissions", emissions);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }
}
