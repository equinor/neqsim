package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening-level deaerator sizer for boiler feedwater treatment.
 *
 * <p>
 * A deaerator heats boiler feedwater to the saturation temperature at its operating pressure using low-pressure
 * stripping steam, driving off dissolved oxygen and carbon dioxide. This model estimates the stripping-steam demand
 * (sensible heating of the feedwater plus a vent allowance) and the vent rate. It is deterministic and intended for
 * early-stage utility screening.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Deaerator dea = new Deaerator("Deaerator");
 * dea.setFeedwaterFlowKgh(20000.0);
 * dea.setFeedwaterInletTempC(30.0);
 * dea.setOperatingPressureBara(1.2);
 * dea.calculate();
 * double steam = dea.getStrippingSteamKgh();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class Deaerator implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Specific heat capacity of water [kJ/(kg·K)]. */
  private static final double CP_WATER = 4.186;

  /** Equipment name. */
  private String name = "Deaerator";

  /** Boiler feedwater flow to be deaerated [kg/h]. */
  private double feedwaterFlowKgh = 0.0;

  /** Feedwater inlet temperature [°C]. */
  private double feedwaterInletTempC = 30.0;

  /** Deaerator operating pressure [bara], default 1.2. */
  private double operatingPressureBara = 1.2;

  /** Latent heat of the stripping steam [kJ/kg], default 2200. */
  private double strippingSteamLatentHeatKJperKg = 2200.0;

  /** Vent rate as a fraction of feedwater flow, default 0.001. */
  private double ventFraction = 0.001;

  // Results
  private double deaeratorTempC = 0.0;
  private double heatDutyKW = 0.0;
  private double strippingSteamKgh = 0.0;
  private double ventRateKgh = 0.0;
  private boolean calculated = false;

  /**
   * Creates a deaerator with the default name.
   */
  public Deaerator() {
  }

  /**
   * Creates a named deaerator.
   *
   * @param name equipment name
   */
  public Deaerator(String name) {
    this.name = name;
  }

  /**
   * Estimates the saturation temperature of water at the given pressure using a screening Antoine-style correlation.
   *
   * @param pressureBara absolute pressure in bara
   * @return saturation temperature in °C
   */
  private static double saturationTempC(double pressureBara) {
    // Antoine equation for water (T in C, P in mmHg): log10(P) = A - B/(C+T)
    double a = 8.07131;
    double b = 1730.63;
    double c = 233.426;
    double pmmHg = pressureBara * 750.062;
    return b / (a - Math.log10(pmmHg)) - c;
  }

  /**
   * Sizes the deaerator stripping-steam demand.
   */
  public void calculate() {
    deaeratorTempC = saturationTempC(operatingPressureBara);
    double deltaT = Math.max(0.0, deaeratorTempC - feedwaterInletTempC);
    // Sensible heat to raise feedwater to saturation [kW] = kg/s * kJ/kgK * K
    heatDutyKW = feedwaterFlowKgh / 3600.0 * CP_WATER * deltaT;
    strippingSteamKgh = strippingSteamLatentHeatKJperKg > 0.0 ? heatDutyKW * 3600.0 / strippingSteamLatentHeatKJperKg
        : 0.0;
    ventRateKgh = feedwaterFlowKgh * ventFraction;
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
    basis.put("feedwaterFlow_kg_per_h", feedwaterFlowKgh);
    basis.put("feedwaterInletTemp_C", feedwaterInletTempC);
    basis.put("operatingPressure_bara", operatingPressureBara);
    basis.put("ventFraction", ventFraction);
    root.put("designBasis", basis);
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("deaeratorTemp_C", deaeratorTempC);
    results.put("heatDuty_kW", heatDutyKW);
    results.put("strippingSteam_kg_per_h", strippingSteamKgh);
    results.put("ventRate_kg_per_h", ventRateKgh);
    root.put("results", results);
    return root;
  }

  /**
   * Serializes the deaerator results to pretty-printed JSON.
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
   * Sets the feedwater flow to be deaerated.
   *
   * @param value feedwater flow in kg/h
   */
  public void setFeedwaterFlowKgh(double value) {
    this.feedwaterFlowKgh = value;
    calculated = false;
  }

  /**
   * Sets the feedwater inlet temperature.
   *
   * @param value inlet temperature in °C
   */
  public void setFeedwaterInletTempC(double value) {
    this.feedwaterInletTempC = value;
    calculated = false;
  }

  /**
   * Sets the deaerator operating pressure.
   *
   * @param value operating pressure in bara
   */
  public void setOperatingPressureBara(double value) {
    this.operatingPressureBara = value;
    calculated = false;
  }

  /**
   * Sets the latent heat of the stripping steam.
   *
   * @param value latent heat in kJ/kg
   */
  public void setStrippingSteamLatentHeatKJperKg(double value) {
    this.strippingSteamLatentHeatKJperKg = value;
    calculated = false;
  }

  /**
   * Sets the vent fraction.
   *
   * @param value vent rate as a fraction of feedwater flow
   */
  public void setVentFraction(double value) {
    this.ventFraction = value;
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
   * Gets the deaerator operating (saturation) temperature.
   *
   * @return temperature in °C
   */
  public double getDeaeratorTempC() {
    ensureCalculated();
    return deaeratorTempC;
  }

  /**
   * Gets the sensible heat duty to raise feedwater to saturation.
   *
   * @return heat duty in kW
   */
  public double getHeatDutyKW() {
    ensureCalculated();
    return heatDutyKW;
  }

  /**
   * Gets the stripping-steam demand.
   *
   * @return stripping steam in kg/h
   */
  public double getStrippingSteamKgh() {
    ensureCalculated();
    return strippingSteamKgh;
  }

  /**
   * Gets the vent rate.
   *
   * @return vent rate in kg/h
   */
  public double getVentRateKgh() {
    ensureCalculated();
    return ventRateKgh;
  }
}
