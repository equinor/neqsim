package neqsim.process.equipment.powergeneration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Heat Recovery Steam Generator (HRSG) for combined-cycle power plants.
 *
 * <p>
 * Models a counter-current heat exchanger where hot exhaust gas from a gas turbine heats
 * water/steam to produce superheated steam. The HRSG transfers heat from the gas-side inlet stream
 * to produce a steam outlet at the specified conditions.
 * </p>
 *
 * <p>
 * The model calculates the heat transfer based on the gas-side cooling and applies an effectiveness
 * factor to determine the actual steam production rate for given steam conditions.
 * </p>
 *
 * <pre>
 * HRSG hrsg = new HRSG("HRSG-1", gasTurbineExhaust);
 * hrsg.setSteamPressure(40.0); // bara
 * hrsg.setSteamTemperature(400.0, "C"); // superheated
 * hrsg.setApproachTemperature(15.0); // K
 * hrsg.run();
 * double steamFlow = hrsg.getSteamFlowRate("kg/hr");
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HRSG extends TwoPortEquipment implements CapacityConstrainedEquipment, AutoSizeable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(HRSG.class);

  private double steamPressure = 40.0; // bara
  private double steamTemperature = 673.15; // Kelvin (400 C)
  private double feedWaterTemperature = 333.15; // Kelvin (60 C)
  private double approachTemperature = 15.0; // K (minimum approach at pinch)
  private double effectiveness = 0.85; // heat transfer effectiveness
  private double heatTransferred = 0.0; // Watts
  private double steamFlowRate = 0.0; // kg/sec
  private double gasOutletTemperature = 0.0; // Kelvin

  /** Design (maximum) heat transfer duty in Watts. Used for capacity constraint calculations. */
  private double designHeatDutyW = 0.0;

  /** Whether this equipment has been auto-sized. */
  private boolean autoSized = false;

  /** Storage for capacity constraints. */
  private final Map<String, CapacityConstraint> capacityConstraints =
      new LinkedHashMap<String, CapacityConstraint>();

  /**
   * Constructor for HRSG.
   *
   * @param name equipment name
   */
  public HRSG(String name) {
    super(name);
  }

  /**
   * Constructor for HRSG with hot gas inlet stream (from gas turbine exhaust).
   *
   * @param name equipment name
   * @param hotGasInletStream hot gas stream from gas turbine exhaust
   */
  public HRSG(String name, StreamInterface hotGasInletStream) {
    super(name, hotGasInletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface gasIn = inStream.getThermoSystem().clone();
    double gasInletTemp = gasIn.getTemperature();
    double gasInletEnthalpy = gasIn.getEnthalpy();
    double gasMassFlow = gasIn.getFlowRate("kg/sec");

    // Calculate maximum available heat
    // Cool gas to approach temperature above feed water temperature
    double minGasOutTemp = feedWaterTemperature + approachTemperature;

    SystemInterface gasCooled = gasIn.clone();
    gasCooled.setTemperature(minGasOutTemp);
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(gasCooled);
    ops.TPflash();
    double gasEnthalypCooled = gasCooled.getEnthalpy();

    double maxHeatAvailable = gasInletEnthalpy - gasEnthalypCooled;

    // Apply effectiveness
    heatTransferred = maxHeatAvailable * effectiveness;
    if (heatTransferred < 0) {
      heatTransferred = 0.0;
    }

    // Calculate actual gas outlet conditions
    double actualGasOutEnthalpy = gasInletEnthalpy - heatTransferred;
    SystemInterface gasOut = gasIn.clone();
    neqsim.thermodynamicoperations.ThermodynamicOperations opsOut =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(gasOut);
    try {
      opsOut.PHflash(actualGasOutEnthalpy);
    } catch (Exception ex) {
      logger.warn("PH flash failed in HRSG gas side, using TP flash: " + ex.getMessage());
      opsOut.TPflash();
    }
    gasOutletTemperature = gasOut.getTemperature();

    // Estimate steam production
    // Steam enthalpy at given conditions minus feedwater enthalpy
    // Using approximate steam properties: hsteam ~ 3200 kJ/kg at 40 bar 400C,
    // hfw ~ 250 kJ/kg at 60C
    // This is a simplified model; for rigorous calculation, create a water fluid
    double steamSpecificEnthalpy = estimateSteamEnthalpy(steamPressure, steamTemperature);
    double feedWaterSpecificEnthalpy = estimateFeedWaterEnthalpy(feedWaterTemperature);
    double enthalpyDifference = steamSpecificEnthalpy - feedWaterSpecificEnthalpy;

    if (enthalpyDifference > 0) {
      steamFlowRate = heatTransferred / enthalpyDifference; // kg/sec
    } else {
      steamFlowRate = 0.0;
    }

    outStream.setThermoSystem(gasOut);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Estimate steam specific enthalpy using simplified correlation.
   *
   * @param pressure steam pressure in bara
   * @param temperature steam temperature in Kelvin
   * @return specific enthalpy in J/kg
   */
  private double estimateSteamEnthalpy(double pressure, double temperature) {
    // Simplified: h = hfg(P) + Cp_steam * (T - Tsat)
    // At typical HRSG conditions (20-100 bar): hfg ~ 1800-2000 kJ/kg
    // Tsat ~ 200-310 C, Cp_steam ~ 2.1 kJ/kgK
    double tempC = temperature - 273.15;
    double tSat = 100.0 + 30.0 * Math.log(pressure); // rough approximation
    double hfg = 2250.0e3 - 1.5e3 * (tSat - 100.0); // J/kg, decreasing with pressure
    double hSat = 420.0e3 + 4.18e3 * (tSat - 100.0); // saturated liquid enthalpy
    double hSteam = hSat + hfg;
    if (tempC > tSat) {
      hSteam += 2.1e3 * (tempC - tSat); // superheat
    }
    return hSteam;
  }

  /**
   * Estimate feed water specific enthalpy.
   *
   * @param temperature feed water temperature in Kelvin
   * @return specific enthalpy in J/kg
   */
  private double estimateFeedWaterEnthalpy(double temperature) {
    return 4.18e3 * (temperature - 273.15); // J/kg (cp * dT from 0C)
  }

  /**
   * Get heat transferred from gas to steam.
   *
   * @return heat transferred in Watts
   */
  public double getHeatTransferred() {
    return heatTransferred;
  }

  /**
   * Get heat transferred in specified unit.
   *
   * @param unit heat unit ("W", "kW", "MW")
   * @return heat transferred
   */
  public double getHeatTransferred(String unit) {
    switch (unit) {
      case "kW":
        return heatTransferred / 1000.0;
      case "MW":
        return heatTransferred / 1.0e6;
      default:
        return heatTransferred;
    }
  }

  /**
   * Get the calculated steam flow rate.
   *
   * @return steam flow rate in kg/sec
   */
  public double getSteamFlowRate() {
    return steamFlowRate;
  }

  /**
   * Get steam flow rate in specified unit.
   *
   * @param unit flow unit ("kg/sec", "kg/hr", "ton/hr")
   * @return steam flow rate
   */
  public double getSteamFlowRate(String unit) {
    switch (unit) {
      case "kg/hr":
        return steamFlowRate * 3600.0;
      case "ton/hr":
        return steamFlowRate * 3.6;
      default:
        return steamFlowRate;
    }
  }

  /**
   * Get gas outlet temperature.
   *
   * @return gas outlet temperature in Kelvin
   */
  public double getGasOutletTemperature() {
    return gasOutletTemperature;
  }

  /**
   * Set steam pressure.
   *
   * @param pressure steam pressure in bara
   */
  public void setSteamPressure(double pressure) {
    this.steamPressure = pressure;
  }

  /**
   * Set steam temperature.
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
   * @param unit temperature unit ("C", "K", "F")
   */
  public void setSteamTemperature(double temperature, String unit) {
    if ("C".equals(unit)) {
      this.steamTemperature = temperature + 273.15;
    } else if ("F".equals(unit)) {
      this.steamTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.steamTemperature = temperature;
    }
  }

  /**
   * Set feed water temperature.
   *
   * @param temperature feed water temperature in Kelvin
   */
  public void setFeedWaterTemperature(double temperature) {
    this.feedWaterTemperature = temperature;
  }

  /**
   * Set feed water temperature with unit.
   *
   * @param temperature feed water temperature
   * @param unit temperature unit ("C", "K")
   */
  public void setFeedWaterTemperature(double temperature, String unit) {
    if ("C".equals(unit)) {
      this.feedWaterTemperature = temperature + 273.15;
    } else {
      this.feedWaterTemperature = temperature;
    }
  }

  /**
   * Set approach temperature (minimum temperature difference at pinch).
   *
   * @param approachTemp approach temperature in K (or C since it is a difference)
   */
  public void setApproachTemperature(double approachTemp) {
    this.approachTemperature = approachTemp;
  }

  /**
   * Set heat transfer effectiveness.
   *
   * @param effectiveness effectiveness factor (0 to 1)
   */
  public void setEffectiveness(double effectiveness) {
    this.effectiveness = effectiveness;
  }

  /**
   * Get the design (maximum) heat transfer duty.
   *
   * @return design heat duty in Watts
   */
  public double getDesignHeatDuty() {
    return designHeatDutyW;
  }

  /**
   * Get the design (maximum) heat transfer duty in specified unit.
   *
   * @param unit heat unit ("W", "kW", "MW")
   * @return design heat duty
   */
  public double getDesignHeatDuty(String unit) {
    switch (unit) {
      case "kW":
        return designHeatDutyW / 1000.0;
      case "MW":
        return designHeatDutyW / 1.0e6;
      default:
        return designHeatDutyW;
    }
  }

  /**
   * Set the design (maximum) heat transfer duty.
   *
   * @param designHeatDuty design heat duty in Watts
   */
  public void setDesignHeatDuty(double designHeatDuty) {
    this.designHeatDutyW = designHeatDuty;
    initializeCapacityConstraints();
  }

  /**
   * Set the design (maximum) heat transfer duty with unit.
   *
   * @param designHeatDuty design heat duty value
   * @param unit heat unit ("W", "kW", "MW")
   */
  public void setDesignHeatDuty(double designHeatDuty, String unit) {
    switch (unit) {
      case "kW":
        this.designHeatDutyW = designHeatDuty * 1000.0;
        break;
      case "MW":
        this.designHeatDutyW = designHeatDuty * 1.0e6;
        break;
      default:
        this.designHeatDutyW = designHeatDuty;
    }
    initializeCapacityConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return Math.abs(heatTransferred);
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return designHeatDutyW > 0 ? designHeatDutyW : Math.abs(heatTransferred) * 1.2;
  }

  /**
   * Initialize capacity constraints for the HRSG.
   */
  private void initializeCapacityConstraints() {
    capacityConstraints.clear();
    if (designHeatDutyW > 0) {
      addCapacityConstraint(
          new CapacityConstraint("heatDuty", "kW", CapacityConstraint.ConstraintType.HARD)
              .setDesignValue(designHeatDutyW / 1000.0).setMaxValue(designHeatDutyW / 1000.0 * 1.1)
              .setWarningThreshold(0.9).setDescription("HRSG heat transfer duty vs design capacity")
              .setValueSupplier(() -> Math.abs(this.heatTransferred) / 1000.0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (heatTransferred > 0) {
      this.designHeatDutyW = Math.abs(heatTransferred) * safetyFactor;
      initializeCapacityConstraints();
      autoSized = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== HRSG Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");
    sb.append("\n--- Operating Conditions ---\n");
    sb.append("Heat Transferred: ")
        .append(String.format("%.2f kW", Math.abs(heatTransferred) / 1000.0)).append("\n");
    if (designHeatDutyW > 0) {
      sb.append("Design Heat Duty: ").append(String.format("%.2f kW", designHeatDutyW / 1000.0))
          .append("\n");
      sb.append("Utilization: ")
          .append(String.format("%.1f%%", Math.abs(heatTransferred) / designHeatDutyW * 100))
          .append("\n");
    }
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }
}
