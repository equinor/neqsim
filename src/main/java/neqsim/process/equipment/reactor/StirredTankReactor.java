package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Stirred tank reactor (CSTR) for bio-processing and chemical operations.
 *
 * <p>
 * Models a continuous stirred-tank reactor (CSTR) or batch reactor with one or more stoichiometric
 * reactions. The reactor applies each reaction in sequence to the feed, then performs a flash
 * calculation at the specified outlet conditions.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * StirredTankReactor cstr = new StirredTankReactor("Reactor", feedStream);
 * cstr.setReactorTemperature(273.15 + 37.0); // 37 C
 * cstr.setResidenceTime(24.0, "hr");
 * cstr.setVesselVolume(50.0); // m3
 * cstr.addReaction(ethanolFermentation);
 * cstr.run();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class StirredTankReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(StirredTankReactor.class);

  /** Reactions to apply in this reactor. */
  private List<StoichiometricReaction> reactions = new ArrayList<StoichiometricReaction>();

  /** Reactor temperature in Kelvin. If NaN, uses feed temperature (isothermal to feed). */
  private double reactorTemperature = Double.NaN;

  /** Reactor pressure in bara. If NaN, uses feed pressure. */
  private double reactorPressure = Double.NaN;

  /** Vessel volume in m3. */
  private double vesselVolume = 10.0;

  /** Residence time in hours. */
  private double residenceTime = 1.0;

  /** Agitator power per unit volume in kW/m3. */
  private double agitatorPowerPerVolume = 1.0;

  /** Pressure drop across reactor in bar. */
  private double pressureDrop = 0.0;

  /** Heat duty calculated from energy balance in Watts. */
  private double heatDuty = 0.0;

  /** Whether to operate in isothermal mode (fix temperature). */
  private boolean isothermal = true;

  /**
   * Constructor for StirredTankReactor.
   *
   * @param name name of the reactor
   */
  public StirredTankReactor(String name) {
    super(name);
  }

  /**
   * Constructor for StirredTankReactor with inlet stream.
   *
   * @param name name of the reactor
   * @param inletStream inlet feed stream
   */
  public StirredTankReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Add a stoichiometric reaction to the reactor.
   *
   * @param reaction a {@link StoichiometricReaction} to apply
   */
  public void addReaction(StoichiometricReaction reaction) {
    reactions.add(reaction);
  }

  /**
   * Get the list of reactions.
   *
   * @return list of reactions
   */
  public List<StoichiometricReaction> getReactions() {
    return reactions;
  }

  /**
   * Clear all reactions.
   */
  public void clearReactions() {
    reactions.clear();
  }

  /**
   * Set the reactor operating temperature in Kelvin.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setReactorTemperature(double temperatureK) {
    this.reactorTemperature = temperatureK;
    this.isothermal = true;
  }

  /**
   * Set the reactor operating temperature with unit.
   *
   * @param temperature temperature value
   * @param unit temperature unit ("K", "C", "F")
   */
  public void setReactorTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.reactorTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.reactorTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.reactorTemperature = temperature;
    }
    this.isothermal = true;
  }

  /**
   * Get the reactor temperature in Kelvin.
   *
   * @return reactor temperature in K
   */
  public double getReactorTemperature() {
    return reactorTemperature;
  }

  /**
   * Set the reactor pressure in bara.
   *
   * @param pressureBara pressure in bara
   */
  public void setReactorPressure(double pressureBara) {
    this.reactorPressure = pressureBara;
  }

  /**
   * Get the reactor pressure in bara.
   *
   * @return reactor pressure in bara
   */
  public double getReactorPressure() {
    return reactorPressure;
  }

  /**
   * Set the vessel volume.
   *
   * @param volumeM3 vessel volume in cubic meters
   */
  public void setVesselVolume(double volumeM3) {
    this.vesselVolume = volumeM3;
  }

  /**
   * Get the vessel volume in m3.
   *
   * @return vessel volume
   */
  public double getVesselVolume() {
    return vesselVolume;
  }

  /**
   * Set residence time.
   *
   * @param time residence time value
   * @param unit time unit ("hr", "min", "s")
   */
  public void setResidenceTime(double time, String unit) {
    if ("min".equalsIgnoreCase(unit)) {
      this.residenceTime = time / 60.0;
    } else if ("s".equalsIgnoreCase(unit)) {
      this.residenceTime = time / 3600.0;
    } else {
      this.residenceTime = time;
    }
  }

  /**
   * Get residence time in hours.
   *
   * @return residence time in hours
   */
  public double getResidenceTime() {
    return residenceTime;
  }

  /**
   * Set the agitator power per unit volume.
   *
   * @param powerPerVolume agitator power in kW/m3
   */
  public void setAgitatorPowerPerVolume(double powerPerVolume) {
    this.agitatorPowerPerVolume = powerPerVolume;
  }

  /**
   * Get the agitator power per unit volume.
   *
   * @return agitator power in kW/m3
   */
  public double getAgitatorPowerPerVolume() {
    return agitatorPowerPerVolume;
  }

  /**
   * Get total agitator power in kW.
   *
   * @return total agitator power
   */
  public double getAgitatorPower() {
    return agitatorPowerPerVolume * vesselVolume;
  }

  /**
   * Set the pressure drop across the reactor.
   *
   * @param dP pressure drop in bar
   */
  public void setPressureDrop(double dP) {
    this.pressureDrop = dP;
  }

  /**
   * Get the pressure drop in bar.
   *
   * @return pressure drop
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Get the heat duty calculated from energy balance in Watts.
   *
   * @return heat duty in W (positive = heat added, negative = removed)
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /**
   * Get the heat duty in specified unit.
   *
   * @param unit unit for heat duty ("W", "kW", "MW")
   * @return heat duty in specified unit
   */
  public double getHeatDuty(String unit) {
    if ("kW".equalsIgnoreCase(unit)) {
      return heatDuty / 1000.0;
    } else if ("MW".equalsIgnoreCase(unit)) {
      return heatDuty / 1.0e6;
    }
    return heatDuty;
  }

  /**
   * Set whether reactor operates isothermally.
   *
   * @param isothermal true for isothermal, false for adiabatic
   */
  public void setIsothermal(boolean isothermal) {
    this.isothermal = isothermal;
  }

  /**
   * Check if reactor is isothermal.
   *
   * @return true if isothermal
   */
  public boolean isIsothermal() {
    return isothermal;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();

    // Store inlet enthalpy for energy balance
    system.init(3);
    double inletEnthalpy = system.getEnthalpy();

    // Apply all reactions to the system
    for (StoichiometricReaction rxn : reactions) {
      try {
        rxn.react(system);
      } catch (Exception ex) {
        logger.warn("Reaction '{}' failed: {}", rxn.getName(), ex.getMessage());
      }
    }

    // Set outlet conditions
    if (!Double.isNaN(reactorPressure)) {
      system.setPressure(reactorPressure);
    } else {
      system.setPressure(system.getPressure() - pressureDrop);
    }

    if (isothermal && !Double.isNaN(reactorTemperature)) {
      system.setTemperature(reactorTemperature);
    }

    // Flash calculation at outlet conditions
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      if (isothermal) {
        ops.TPflash();
      } else {
        // Adiabatic: use inlet enthalpy
        ops.PHflash(inletEnthalpy, 0);
      }
    } catch (Exception ex) {
      logger.error("Flash calculation failed in reactor '{}': {}", getName(), ex.getMessage());
    }

    system.init(3);
    system.initProperties();

    // Calculate heat duty (energy balance)
    double outletEnthalpy = system.getEnthalpy();
    if (isothermal) {
      heatDuty = outletEnthalpy - inletEnthalpy;
    } else {
      heatDuty = 0.0;
    }

    outStream.setThermoSystem(system);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Get a map representation of the reactor state.
   *
   * @return map of reactor properties
   */
  private java.util.Map<String, Object> toMap() {
    java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("type", "StirredTankReactor");
    map.put("vesselVolume_m3", vesselVolume);
    map.put("residenceTime_hr", residenceTime);
    map.put("agitatorPower_kW", getAgitatorPower());
    map.put("isothermal", isothermal);
    map.put("heatDuty_W", heatDuty);
    map.put("numberOfReactions", reactions.size());
    if (!Double.isNaN(reactorTemperature)) {
      map.put("reactorTemperature_K", reactorTemperature);
    }
    if (!Double.isNaN(reactorPressure)) {
      map.put("reactorPressure_bara", reactorPressure);
    }
    return map;
  }
}
