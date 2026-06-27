package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening-level mechanical refrigeration cycle sizer for sub-ambient process cooling.
 *
 * <p>
 * Provides sub-ambient cooling duty (for example propane or mixed-refrigerant chillers) by estimating the cycle
 * coefficient of performance (COP) from the evaporator and condenser temperatures via a Carnot reference scaled by a
 * cycle efficiency factor. From the COP the model derives the refrigeration-compressor shaft power, the heat rejected
 * at the condenser, the electrical load and the associated CO<sub>2</sub> emissions and operating cost. It is
 * deterministic and intended for early-stage utility screening.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * RefrigerationCycle cycle = new RefrigerationCycle("Propane Refrigeration");
 * cycle.addRefrigerationDuty("Gas Chiller", 4000.0); // kW
 * cycle.setEvaporatorTempC(-35.0);
 * cycle.setCondenserTempC(35.0);
 * cycle.calculate();
 * double power = cycle.getCompressorPowerKW();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RefrigerationCycle implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Equipment name. */
  private String name = "Refrigeration Cycle";

  /** Refrigerant label (informational). */
  private String refrigerant = "propane";

  /** Evaporator (cold-side) temperature [°C], default -35. */
  private double evaporatorTempC = -35.0;

  /** Condenser (warm-side) temperature [°C], default 35. */
  private double condenserTempC = 35.0;

  /** Cycle efficiency as a fraction of the Carnot COP, default 0.55. */
  private double cycleEfficiency = 0.55;

  /** Annual operating hours, default 8000. */
  private double annualOperatingHours = 8000.0;

  /** Electricity cost [$/kWh], default 0.10. */
  private double electricityCostPerKWh = 0.10;

  /** Grid CO2 emission factor [kg/kWh], default 0.0. */
  private double co2GridFactorKgPerKWh = 0.0;

  /** Carbon tax [$/tonne CO2], default 0.0. */
  private double carbonTaxPerTonne = 0.0;

  /** Refrigeration cooling demands. */
  private final List<RefrigerationDuty> duties = new ArrayList<RefrigerationDuty>();

  // Results
  private double totalRefrigerationDutyKW = 0.0;
  private double carnotCop = 0.0;
  private double cop = 0.0;
  private double compressorPowerKW = 0.0;
  private double condenserDutyKW = 0.0;
  private double co2TonnePerYear = 0.0;
  private double annualOperatingCost = 0.0;
  private boolean calculated = false;

  /**
   * A single sub-ambient refrigeration duty.
   */
  public static class RefrigerationDuty implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Demand name. */
    public final String name;

    /** Required refrigeration duty [kW]. */
    public final double dutyKW;

    /**
     * Creates a refrigeration duty.
     *
     * @param name demand name
     * @param dutyKW refrigeration duty in kW
     */
    public RefrigerationDuty(String name, double dutyKW) {
      this.name = name;
      this.dutyKW = dutyKW;
    }
  }

  /**
   * Creates a refrigeration cycle with the default name.
   */
  public RefrigerationCycle() {
  }

  /**
   * Creates a named refrigeration cycle.
   *
   * @param name equipment name
   */
  public RefrigerationCycle(String name) {
    this.name = name;
  }

  /**
   * Adds a sub-ambient refrigeration duty.
   *
   * @param name demand name
   * @param dutyKW refrigeration duty in kW (must be non-negative)
   */
  public void addRefrigerationDuty(String name, double dutyKW) {
    if (dutyKW < 0.0) {
      throw new IllegalArgumentException("refrigeration duty must be non-negative, got " + dutyKW);
    }
    duties.add(new RefrigerationDuty(name, dutyKW));
    calculated = false;
  }

  /**
   * Sizes the refrigeration cycle from the registered duties.
   */
  public void calculate() {
    totalRefrigerationDutyKW = 0.0;
    for (RefrigerationDuty d : duties) {
      totalRefrigerationDutyKW += d.dutyKW;
    }
    double tEvapK = evaporatorTempC + 273.15;
    double tCondK = condenserTempC + 273.15;
    double deltaTK = tCondK - tEvapK;
    carnotCop = deltaTK > 0.0 ? tEvapK / deltaTK : 0.0;
    cop = carnotCop * cycleEfficiency;
    compressorPowerKW = cop > 0.0 ? totalRefrigerationDutyKW / cop : 0.0;
    condenserDutyKW = totalRefrigerationDutyKW + compressorPowerKW;
    co2TonnePerYear = compressorPowerKW * annualOperatingHours * co2GridFactorKgPerKWh / 1000.0;
    double powerCost = compressorPowerKW * annualOperatingHours * electricityCostPerKWh;
    double taxCost = co2TonnePerYear * carbonTaxPerTonne;
    annualOperatingCost = powerCost + taxCost;
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
    basis.put("refrigerant", refrigerant);
    basis.put("evaporatorTemp_C", evaporatorTempC);
    basis.put("condenserTemp_C", condenserTempC);
    basis.put("cycleEfficiency", cycleEfficiency);
    basis.put("annualOperatingHours", annualOperatingHours);
    root.put("designBasis", basis);
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("refrigerationDuty_kW", totalRefrigerationDutyKW);
    results.put("carnotCOP", carnotCop);
    results.put("COP", cop);
    results.put("compressorPower_kW", compressorPowerKW);
    results.put("condenserDuty_kW", condenserDutyKW);
    results.put("co2_tonne_per_year", co2TonnePerYear);
    results.put("annualOperatingCost", annualOperatingCost);
    root.put("results", results);
    return root;
  }

  /**
   * Serializes the refrigeration cycle results to pretty-printed JSON.
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
   * Sets the refrigerant label.
   *
   * @param value refrigerant name
   */
  public void setRefrigerant(String value) {
    this.refrigerant = value;
  }

  /**
   * Sets the evaporator (cold-side) temperature.
   *
   * @param value evaporator temperature in °C
   */
  public void setEvaporatorTempC(double value) {
    this.evaporatorTempC = value;
    calculated = false;
  }

  /**
   * Sets the condenser (warm-side) temperature.
   *
   * @param value condenser temperature in °C
   */
  public void setCondenserTempC(double value) {
    this.condenserTempC = value;
    calculated = false;
  }

  /**
   * Sets the cycle efficiency as a fraction of the Carnot COP.
   *
   * @param value cycle efficiency in the range (0, 1]
   */
  public void setCycleEfficiency(double value) {
    this.cycleEfficiency = value;
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
   * Sets the electricity cost.
   *
   * @param value electricity cost in $/kWh
   */
  public void setElectricityCostPerKWh(double value) {
    this.electricityCostPerKWh = value;
    calculated = false;
  }

  /**
   * Sets the grid CO2 emission factor.
   *
   * @param value CO2 emission factor in kg/kWh
   */
  public void setCo2GridFactorKgPerKWh(double value) {
    this.co2GridFactorKgPerKWh = value;
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
   * Gets the total refrigeration duty.
   *
   * @return refrigeration duty in kW
   */
  public double getTotalRefrigerationDutyKW() {
    ensureCalculated();
    return totalRefrigerationDutyKW;
  }

  /**
   * Gets the Carnot reference COP.
   *
   * @return Carnot COP (dimensionless)
   */
  public double getCarnotCop() {
    ensureCalculated();
    return carnotCop;
  }

  /**
   * Gets the actual cycle COP.
   *
   * @return COP (dimensionless)
   */
  public double getCop() {
    ensureCalculated();
    return cop;
  }

  /**
   * Gets the refrigeration-compressor shaft power.
   *
   * @return compressor power in kW
   */
  public double getCompressorPowerKW() {
    ensureCalculated();
    return compressorPowerKW;
  }

  /**
   * Gets the heat rejected at the condenser.
   *
   * @return condenser duty in kW
   */
  public double getCondenserDutyKW() {
    ensureCalculated();
    return condenserDutyKW;
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
