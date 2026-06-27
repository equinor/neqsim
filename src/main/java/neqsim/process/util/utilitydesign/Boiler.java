package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening-level fired steam boiler / steam-generation package sizer.
 *
 * <p>
 * Collects steam heating duties, computes the fuel-fired thermal input required (accounting for boiler efficiency), the
 * resulting fuel mass demand, generated steam mass flow, boiler feedwater and blowdown flows, forced-draught fan power
 * and the associated CO<sub>2</sub> emissions and operating cost. The model is deterministic and intended for
 * early-stage utility screening, not detailed combustion design.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Boiler boiler = new Boiler("HP Boiler");
 * boiler.addSteamDuty("Reboiler", 5000.0); // kW
 * boiler.setBoilerEfficiency(0.85);
 * boiler.calculate();
 * double fuelKgh = boiler.getFuelMassDemandKgh();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class Boiler implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Equipment name. */
  private String name = "Boiler";

  /** Boiler thermal efficiency (fuel-to-steam), default 0.85. */
  private double boilerEfficiency = 0.85;

  /** Fuel lower heating value [MJ/kg], default natural gas 47.0. */
  private double fuelLowHeatingValueMJperKg = 47.0;

  /** CO2 produced per unit mass of fuel burned [kg/kg], default natural gas 2.75. */
  private double co2FuelFactorKgPerKg = 2.75;

  /** Steam enthalpy rise from feedwater to delivered steam [kJ/kg], default 2200. */
  private double steamEnthalpyRiseKJperKg = 2200.0;

  /** Continuous blowdown as a fraction of steam generation, default 0.02. */
  private double blowdownFraction = 0.02;

  /** Forced-draught fan electrical power as a fraction of fuel thermal input, default 0.01. */
  private double fanPowerFraction = 0.01;

  /** Annual operating hours, default 8000. */
  private double annualOperatingHours = 8000.0;

  /** Fuel-gas cost [$/kg], default 0.25. */
  private double fuelGasCostPerKg = 0.25;

  /** Electricity cost [$/kWh], default 0.10. */
  private double electricityCostPerKWh = 0.10;

  /** Carbon tax [$/tonne CO2], default 0.0. */
  private double carbonTaxPerTonne = 0.0;

  /** Steam heating demands. */
  private final List<SteamDuty> duties = new ArrayList<SteamDuty>();

  // Results
  private double totalSteamDutyKW = 0.0;
  private double fuelThermalKW = 0.0;
  private double fuelMassDemandKgh = 0.0;
  private double steamGenerationKgh = 0.0;
  private double feedwaterFlowKgh = 0.0;
  private double blowdownFlowKgh = 0.0;
  private double fanPowerKW = 0.0;
  private double co2TonnePerYear = 0.0;
  private double annualOperatingCost = 0.0;
  private boolean calculated = false;

  /**
   * A single steam heating duty served by the boiler.
   */
  public static class SteamDuty implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Demand name. */
    public final String name;

    /** Required steam thermal duty [kW]. */
    public final double dutyKW;

    /**
     * Creates a steam duty.
     *
     * @param name demand name
     * @param dutyKW thermal duty in kW
     */
    public SteamDuty(String name, double dutyKW) {
      this.name = name;
      this.dutyKW = dutyKW;
    }
  }

  /**
   * Creates a boiler with the default name.
   */
  public Boiler() {
  }

  /**
   * Creates a named boiler.
   *
   * @param name equipment name
   */
  public Boiler(String name) {
    this.name = name;
  }

  /**
   * Adds a steam heating duty to be served by this boiler.
   *
   * @param name demand name
   * @param dutyKW thermal duty in kW (must be non-negative)
   */
  public void addSteamDuty(String name, double dutyKW) {
    if (dutyKW < 0.0) {
      throw new IllegalArgumentException("steam duty must be non-negative, got " + dutyKW);
    }
    duties.add(new SteamDuty(name, dutyKW));
    calculated = false;
  }

  /**
   * Sizes the boiler from the registered steam duties.
   */
  public void calculate() {
    totalSteamDutyKW = 0.0;
    for (SteamDuty d : duties) {
      totalSteamDutyKW += d.dutyKW;
    }
    fuelThermalKW = boilerEfficiency > 0.0 ? totalSteamDutyKW / boilerEfficiency : 0.0;
    // kW = kJ/s; kg/h = kJ/s * 3600 / (MJ/kg * 1000)
    fuelMassDemandKgh = fuelThermalKW * 3600.0 / (fuelLowHeatingValueMJperKg * 1000.0);
    steamGenerationKgh = steamEnthalpyRiseKJperKg > 0.0 ? totalSteamDutyKW * 3600.0 / steamEnthalpyRiseKJperKg : 0.0;
    blowdownFlowKgh = steamGenerationKgh * blowdownFraction;
    feedwaterFlowKgh = steamGenerationKgh + blowdownFlowKgh;
    fanPowerKW = fuelThermalKW * fanPowerFraction;
    co2TonnePerYear = fuelMassDemandKgh * co2FuelFactorKgPerKg * annualOperatingHours / 1000.0;
    double fuelCost = fuelMassDemandKgh * annualOperatingHours * fuelGasCostPerKg;
    double powerCost = fanPowerKW * annualOperatingHours * electricityCostPerKWh;
    double taxCost = co2TonnePerYear * carbonTaxPerTonne;
    annualOperatingCost = fuelCost + powerCost + taxCost;
    calculated = true;
  }

  private void ensureCalculated() {
    if (!calculated) {
      calculate();
    }
  }

  /**
   * Builds an ordered results map suitable for JSON serialization.
   *
   * @return ordered results map
   */
  public Map<String, Object> toResultsMap() {
    ensureCalculated();
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("name", name);
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("boilerEfficiency", boilerEfficiency);
    basis.put("fuelLowHeatingValue_MJ_per_kg", fuelLowHeatingValueMJperKg);
    basis.put("steamEnthalpyRise_kJ_per_kg", steamEnthalpyRiseKJperKg);
    basis.put("blowdownFraction", blowdownFraction);
    basis.put("annualOperatingHours", annualOperatingHours);
    root.put("designBasis", basis);
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("steamDuty_kW", totalSteamDutyKW);
    results.put("fuelThermal_kW", fuelThermalKW);
    results.put("fuelMassDemand_kg_per_h", fuelMassDemandKgh);
    results.put("steamGeneration_kg_per_h", steamGenerationKgh);
    results.put("feedwaterFlow_kg_per_h", feedwaterFlowKgh);
    results.put("blowdownFlow_kg_per_h", blowdownFlowKgh);
    results.put("fanPower_kW", fanPowerKW);
    results.put("co2_tonne_per_year", co2TonnePerYear);
    results.put("annualOperatingCost", annualOperatingCost);
    root.put("results", results);
    return root;
  }

  /**
   * Serializes the boiler results to pretty-printed JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toResultsMap());
  }

  // ==========================================================================
  // Setters
  // ==========================================================================

  /**
   * Sets the boiler thermal efficiency.
   *
   * @param value fuel-to-steam efficiency in the range (0, 1]
   */
  public void setBoilerEfficiency(double value) {
    this.boilerEfficiency = value;
    calculated = false;
  }

  /**
   * Sets the fuel lower heating value.
   *
   * @param value lower heating value in MJ/kg
   */
  public void setFuelLowHeatingValueMJperKg(double value) {
    this.fuelLowHeatingValueMJperKg = value;
    calculated = false;
  }

  /**
   * Sets the CO2 emission factor per unit mass of fuel.
   *
   * @param value CO2 mass per fuel mass in kg/kg
   */
  public void setCo2FuelFactorKgPerKg(double value) {
    this.co2FuelFactorKgPerKg = value;
    calculated = false;
  }

  /**
   * Sets the steam enthalpy rise from feedwater to delivered steam.
   *
   * @param value enthalpy rise in kJ/kg
   */
  public void setSteamEnthalpyRiseKJperKg(double value) {
    this.steamEnthalpyRiseKJperKg = value;
    calculated = false;
  }

  /**
   * Sets the continuous blowdown fraction.
   *
   * @param value blowdown as a fraction of steam generation
   */
  public void setBlowdownFraction(double value) {
    this.blowdownFraction = value;
    calculated = false;
  }

  /**
   * Sets the forced-draught fan power fraction.
   *
   * @param value fan power as a fraction of fuel thermal input
   */
  public void setFanPowerFraction(double value) {
    this.fanPowerFraction = value;
    calculated = false;
  }

  /**
   * Sets the annual operating hours.
   *
   * @param value operating hours per year
   */
  public void setAnnualOperatingHours(double value) {
    this.annualOperatingHours = value;
    calculated = false;
  }

  /**
   * Sets the fuel-gas cost.
   *
   * @param value fuel cost in $/kg
   */
  public void setFuelGasCostPerKg(double value) {
    this.fuelGasCostPerKg = value;
    calculated = false;
  }

  /**
   * Sets the electricity cost.
   *
   * @param value electricity cost in $/kWh
   */
  public void setElectricityCostPerKWh(double value) {
    this.electricityCostPerKWh = value;
    calculated = false;
  }

  /**
   * Sets the carbon tax.
   *
   * @param value carbon tax in $/tonne CO2
   */
  public void setCarbonTaxPerTonne(double value) {
    this.carbonTaxPerTonne = value;
    calculated = false;
  }

  // ==========================================================================
  // Result getters
  // ==========================================================================

  /**
   * Gets the equipment name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the total steam thermal duty.
   *
   * @return steam duty in kW
   */
  public double getTotalSteamDutyKW() {
    ensureCalculated();
    return totalSteamDutyKW;
  }

  /**
   * Gets the fuel-fired thermal input.
   *
   * @return fuel thermal input in kW
   */
  public double getFuelThermalKW() {
    ensureCalculated();
    return fuelThermalKW;
  }

  /**
   * Gets the fuel mass demand.
   *
   * @return fuel demand in kg/h
   */
  public double getFuelMassDemandKgh() {
    ensureCalculated();
    return fuelMassDemandKgh;
  }

  /**
   * Gets the generated steam mass flow.
   *
   * @return steam generation in kg/h
   */
  public double getSteamGenerationKgh() {
    ensureCalculated();
    return steamGenerationKgh;
  }

  /**
   * Gets the boiler feedwater flow.
   *
   * @return feedwater flow in kg/h
   */
  public double getFeedwaterFlowKgh() {
    ensureCalculated();
    return feedwaterFlowKgh;
  }

  /**
   * Gets the continuous blowdown flow.
   *
   * @return blowdown flow in kg/h
   */
  public double getBlowdownFlowKgh() {
    ensureCalculated();
    return blowdownFlowKgh;
  }

  /**
   * Gets the forced-draught fan power.
   *
   * @return fan power in kW
   */
  public double getFanPowerKW() {
    ensureCalculated();
    return fanPowerKW;
  }

  /**
   * Gets the annual CO2 emissions.
   *
   * @return emissions in tonne/year
   */
  public double getCo2TonnePerYear() {
    ensureCalculated();
    return co2TonnePerYear;
  }

  /**
   * Gets the annual operating cost.
   *
   * @return operating cost in currency/year
   */
  public double getAnnualOperatingCost() {
    ensureCalculated();
    return annualOperatingCost;
  }
}
