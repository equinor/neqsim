package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening-level inert-gas (nitrogen) generation system sizer.
 *
 * <p>
 * Sizes a nitrogen generation package (membrane, pressure-swing adsorption, or cryogenic air separation) from the
 * required nitrogen delivery rate. It estimates the electrical power from a method- and purity-dependent specific
 * energy, the air feed required, and the associated CO<sub>2</sub> emissions and operating cost. The model is
 * deterministic and intended for early-stage utility screening.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * NitrogenSystem n2 = new NitrogenSystem("Nitrogen Package");
 * n2.setNitrogenDemandNm3h(500.0);
 * n2.setPurityPercent(99.5);
 * n2.setGenerationMethod(NitrogenSystem.GenerationMethod.MEMBRANE);
 * n2.calculate();
 * double power = n2.getPowerKW();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class NitrogenSystem implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /**
   * Nitrogen generation technology, each with a representative specific energy and air-to-product ratio.
   */
  public enum GenerationMethod {
    /** Membrane separation - low to medium purity, simple and compact. */
    MEMBRANE(0.35, 3.0, "Membrane"),
    /** Pressure-swing adsorption - medium to high purity. */
    PSA(0.30, 2.5, "PSA"),
    /** Cryogenic air separation - very high purity, large scale. */
    CRYOGENIC(0.45, 2.0, "Cryogenic");

    private final double specificEnergyKWhPerNm3;
    private final double airToProductRatio;
    private final String label;

    GenerationMethod(double specificEnergy, double airRatio, String label) {
      this.specificEnergyKWhPerNm3 = specificEnergy;
      this.airToProductRatio = airRatio;
      this.label = label;
    }

    /**
     * Gets the representative specific energy.
     *
     * @return specific energy in kWh/Nm3 of nitrogen
     */
    public double getSpecificEnergyKWhPerNm3() {
      return specificEnergyKWhPerNm3;
    }

    /**
     * Gets the representative air-to-product ratio.
     *
     * @return air feed per unit of nitrogen product (Nm3/Nm3)
     */
    public double getAirToProductRatio() {
      return airToProductRatio;
    }

    /**
     * Gets the human-readable label.
     *
     * @return method label
     */
    public String getLabel() {
      return label;
    }
  }

  /** Equipment name. */
  private String name = "Nitrogen System";

  /** Required nitrogen delivery rate [Nm3/h]. */
  private double nitrogenDemandNm3h = 0.0;

  /** Delivered nitrogen purity [%], default 99.5. */
  private double purityPercent = 99.5;

  /** Delivery pressure [barg], default 8.0. */
  private double deliveryPressureBarg = 8.0;

  /** Generation method, default membrane. */
  private GenerationMethod generationMethod = GenerationMethod.MEMBRANE;

  /** Optional explicit specific energy override [kWh/Nm3]; NaN uses the method default. */
  private double specificEnergyOverride = Double.NaN;

  /** Annual operating hours, default 8000. */
  private double annualOperatingHours = 8000.0;

  /** Electricity cost [$/kWh], default 0.10. */
  private double electricityCostPerKWh = 0.10;

  /** Grid CO2 emission factor [kg/kWh], default 0.0. */
  private double co2GridFactorKgPerKWh = 0.0;

  /** Carbon tax [$/tonne CO2], default 0.0. */
  private double carbonTaxPerTonne = 0.0;

  // Results
  private double specificEnergyKWhPerNm3 = 0.0;
  private double powerKW = 0.0;
  private double airFeedNm3h = 0.0;
  private double co2TonnePerYear = 0.0;
  private double annualOperatingCost = 0.0;
  private boolean calculated = false;

  /**
   * Creates a nitrogen system with the default name.
   */
  public NitrogenSystem() {
  }

  /**
   * Creates a named nitrogen system.
   *
   * @param name equipment name
   */
  public NitrogenSystem(String name) {
    this.name = name;
  }

  /**
   * Sizes the nitrogen generation package.
   */
  public void calculate() {
    double purityFactor = 1.0;
    // Higher purity demands more separation work; scale specific energy mildly above 99 %.
    if (purityPercent > 99.0) {
      purityFactor = 1.0 + (purityPercent - 99.0) * 0.1;
    }
    specificEnergyKWhPerNm3 = (!Double.isNaN(specificEnergyOverride) ? specificEnergyOverride
        : generationMethod.getSpecificEnergyKWhPerNm3()) * purityFactor;
    powerKW = nitrogenDemandNm3h * specificEnergyKWhPerNm3;
    airFeedNm3h = nitrogenDemandNm3h * generationMethod.getAirToProductRatio();
    co2TonnePerYear = powerKW * annualOperatingHours * co2GridFactorKgPerKWh / 1000.0;
    double powerCost = powerKW * annualOperatingHours * electricityCostPerKWh;
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
    basis.put("nitrogenDemand_Nm3_per_h", nitrogenDemandNm3h);
    basis.put("purity_percent", purityPercent);
    basis.put("deliveryPressure_barg", deliveryPressureBarg);
    basis.put("generationMethod", generationMethod.getLabel());
    basis.put("annualOperatingHours", annualOperatingHours);
    root.put("designBasis", basis);
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("specificEnergy_kWh_per_Nm3", specificEnergyKWhPerNm3);
    results.put("power_kW", powerKW);
    results.put("airFeed_Nm3_per_h", airFeedNm3h);
    results.put("co2_tonne_per_year", co2TonnePerYear);
    results.put("annualOperatingCost", annualOperatingCost);
    root.put("results", results);
    return root;
  }

  /**
   * Serializes the nitrogen system results to pretty-printed JSON.
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
   * Sets the nitrogen delivery rate.
   *
   * @param value nitrogen demand in Nm3/h
   */
  public void setNitrogenDemandNm3h(double value) {
    this.nitrogenDemandNm3h = value;
    calculated = false;
  }

  /**
   * Sets the delivered nitrogen purity.
   *
   * @param value purity in percent
   */
  public void setPurityPercent(double value) {
    this.purityPercent = value;
    calculated = false;
  }

  /**
   * Sets the delivery pressure.
   *
   * @param value delivery pressure in barg
   */
  public void setDeliveryPressureBarg(double value) {
    this.deliveryPressureBarg = value;
    calculated = false;
  }

  /**
   * Sets the generation method.
   *
   * @param value generation method
   */
  public void setGenerationMethod(GenerationMethod value) {
    this.generationMethod = value;
    calculated = false;
  }

  /**
   * Sets an explicit specific-energy override.
   *
   * @param value specific energy in kWh/Nm3 (use {@link Double#NaN} to use the method default)
   */
  public void setSpecificEnergyOverride(double value) {
    this.specificEnergyOverride = value;
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
   * Gets the applied specific energy.
   *
   * @return specific energy in kWh/Nm3
   */
  public double getSpecificEnergyKWhPerNm3() {
    ensureCalculated();
    return specificEnergyKWhPerNm3;
  }

  /**
   * Gets the electrical power demand.
   *
   * @return power in kW
   */
  public double getPowerKW() {
    ensureCalculated();
    return powerKW;
  }

  /**
   * Gets the air feed required.
   *
   * @return air feed in Nm3/h
   */
  public double getAirFeedNm3h() {
    ensureCalculated();
    return airFeedNm3h;
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
