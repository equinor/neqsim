package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Emulsion viscosity and phase inversion prediction for oil-water systems.
 *
 * <p>
 * Calculates effective viscosity of oil-water emulsions using industry-standard correlations, and
 * predicts the phase inversion point where a water-in-oil (W/O) emulsion transitions to an
 * oil-in-water (O/W) emulsion.
 * </p>
 *
 * <p>
 * Implemented models:
 * </p>
 * <ul>
 * <li><b>Einstein (1906)</b> - Dilute suspensions (water cut &lt; 2%): mu_eff = mu_c * (1 + 2.5 *
 * phi)</li>
 * <li><b>Taylor (1932)</b> - Dilute emulsions with internal circulation: mu_eff = mu_c * (1 + 2.5 *
 * phi * (mu_d + 0.4*mu_c) / (mu_d + mu_c))</li>
 * <li><b>Brinkman (1952)</b> - Moderate concentrations: mu_eff = mu_c * (1 - phi)^(-2.5)</li>
 * <li><b>Pal-Rhodes (1989)</b> - Full range with maximum packing: mu_eff = mu_c * (1 -
 * phi/phi_max)^(-2.5 * phi_max)</li>
 * <li><b>Woelflin (1942)</b> - Empirical for oilfield emulsions</li>
 * <li><b>Richardson (1950)</b> - Exponential model: mu_eff = mu_c * exp(k * phi)</li>
 * </ul>
 *
 * <p>
 * Phase inversion:
 * </p>
 * <ul>
 * <li>Predicts the critical water cut at which the emulsion inverts from W/O to O/W</li>
 * <li>At inversion, viscosity undergoes a dramatic change</li>
 * <li>Inversion point depends on oil/water viscosity ratio, mixing intensity, and surfactants</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
 * calc.setOilViscosity(10.0); // mPa.s (cP)
 * calc.setWaterViscosity(0.5); // mPa.s (cP)
 * calc.setWaterCut(0.30); // volume fraction
 * calc.setModel("pal_rhodes");
 * calc.calculate();
 * double effectiveViscosity = calc.getEffectiveViscosity(); // mPa.s
 * double inversionPoint = calc.getInversionWaterCut(); // volume fraction
 * String emulsionType = calc.getEmulsionType(); // "W/O" or "O/W"
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class EmulsionViscosityCalculator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ========== Input Parameters ==========

  /** Oil (continuous phase) dynamic viscosity in mPa.s (cP). */
  private double oilViscosity = 10.0;

  /** Water (dispersed phase) dynamic viscosity in mPa.s (cP). */
  private double waterViscosity = 0.5;

  /** Oil density in kg/m3. */
  private double oilDensity = 850.0;

  /** Water density in kg/m3. */
  private double waterDensity = 1020.0;

  /** Water cut as volume fraction (0.0 to 1.0). */
  private double waterCut = 0.30;

  /** Temperature in degrees Celsius. */
  private double temperatureC = 60.0;

  /** Pressure in bara. */
  private double pressureBara = 50.0;

  /** Maximum packing fraction for dispersed phase (used in Pal-Rhodes). */
  private double maxPackingFraction = 0.74;

  /**
   * Viscosity model to use: "einstein", "taylor", "brinkman", "pal_rhodes", "woelflin",
   * "richardson".
   */
  private String model = "pal_rhodes";

  /**
   * Emulsion tightness factor (0.0 = loose, 1.0 = tight). Tight emulsions have smaller droplets and
   * higher viscosity.
   */
  private double tightnessFactor = 0.5;

  /** Interfacial tension in mN/m. */
  private double interfacialTension = 25.0;

  /** Shear rate in 1/s (affects droplet size and hence viscosity). */
  private double shearRate = 100.0;

  /**
   * Whether to apply chemical demulsifier correction (reduces effective viscosity).
   */
  private boolean demulsifierPresent = false;

  /** Demulsifier efficiency (0.0 to 1.0). */
  private double demulsifierEfficiency = 0.0;

  // ========== Output Results ==========

  /** Effective emulsion viscosity in mPa.s (cP). */
  private double effectiveViscosity = Double.NaN;

  /** Viscosity ratio (emulsion / continuous phase). */
  private double viscosityRatio = Double.NaN;

  /** Phase inversion water cut (volume fraction). */
  private double inversionWaterCut = Double.NaN;

  /** Current emulsion type: "W/O" (water-in-oil) or "O/W" (oil-in-water). */
  private String emulsionType = "W/O";

  /** Whether the system is currently inverted. */
  private boolean isInverted = false;

  /** Relative viscosity from model (mu_eff / mu_continuous). */
  private double relativeViscosity = Double.NaN;

  /**
   * Performs the complete emulsion viscosity calculation.
   *
   * <p>
   * Steps:
   * </p>
   * <ul>
   * <li>Determine phase inversion point</li>
   * <li>Identify emulsion type (W/O or O/W)</li>
   * <li>Calculate emulsion viscosity using selected model</li>
   * <li>Apply corrections for demulsifier if present</li>
   * </ul>
   */
  public void calculate() {
    // 1. Calculate phase inversion point
    inversionWaterCut = calculateInversionPoint();

    // 2. Determine emulsion type
    if (waterCut < inversionWaterCut) {
      emulsionType = "W/O";
      isInverted = false;
    } else {
      emulsionType = "O/W";
      isInverted = true;
    }

    // 3. Calculate viscosity
    double muContinuous;
    double muDispersed;
    double phi; // dispersed phase volume fraction

    if (!isInverted) {
      // Water-in-oil emulsion
      muContinuous = oilViscosity;
      muDispersed = waterViscosity;
      phi = waterCut;
    } else {
      // Oil-in-water emulsion (inverted)
      muContinuous = waterViscosity;
      muDispersed = oilViscosity;
      phi = 1.0 - waterCut;
    }

    relativeViscosity = calculateRelativeViscosity(phi, muContinuous, muDispersed);
    effectiveViscosity = muContinuous * relativeViscosity;

    // 4. Apply tightness correction
    if (tightnessFactor > 0.5) {
      double tightnessMultiplier = 1.0 + (tightnessFactor - 0.5) * 2.0;
      effectiveViscosity *= tightnessMultiplier;
    }

    // 5. Apply demulsifier correction
    if (demulsifierPresent && demulsifierEfficiency > 0) {
      double demulsifierReduction = demulsifierEfficiency * 0.5; // max 50% reduction
      effectiveViscosity *= (1.0 - demulsifierReduction);
    }

    viscosityRatio = effectiveViscosity / muContinuous;
  }

  /**
   * Calculates the relative viscosity using the selected model.
   *
   * @param phi dispersed phase volume fraction
   * @param muC continuous phase viscosity in mPa.s
   * @param muD dispersed phase viscosity in mPa.s
   * @return relative viscosity (dimensionless)
   */
  private double calculateRelativeViscosity(double phi, double muC, double muD) {
    if (phi <= 0) {
      return 1.0;
    }
    if (phi >= 1.0) {
      return muD / muC;
    }

    switch (model.toLowerCase()) {
      case "einstein":
        return calculateEinstein(phi);
      case "taylor":
        return calculateTaylor(phi, muC, muD);
      case "brinkman":
        return calculateBrinkman(phi);
      case "pal_rhodes":
        return calculatePalRhodes(phi);
      case "woelflin":
        return calculateWoelflin(phi);
      case "richardson":
        return calculateRichardson(phi);
      default:
        return calculatePalRhodes(phi);
    }
  }

  /**
   * Einstein (1906) model for dilute suspensions.
   *
   * <pre>
   * mu_r = 1 + 2.5 * phi
   * </pre>
   *
   * <p>
   * Valid for phi &lt; 0.02.
   * </p>
   *
   * @param phi dispersed phase volume fraction
   * @return relative viscosity
   */
  private double calculateEinstein(double phi) {
    return 1.0 + 2.5 * phi;
  }

  /**
   * Taylor (1932) model for emulsions with internal droplet circulation.
   *
   * <pre>
   * mu_r = 1 + 2.5 * phi * (mu_d + 0.4 * mu_c) / (mu_d + mu_c)
   * </pre>
   *
   * <p>
   * Valid for phi &lt; 0.02. Accounts for viscosity ratio effect.
   * </p>
   *
   * @param phi dispersed phase volume fraction
   * @param muC continuous phase viscosity
   * @param muD dispersed phase viscosity
   * @return relative viscosity
   */
  private double calculateTaylor(double phi, double muC, double muD) {
    double ratio = (muD + 0.4 * muC) / (muD + muC);
    return 1.0 + 2.5 * phi * ratio;
  }

  /**
   * Brinkman (1952) model for moderate concentrations.
   *
   * <pre>
   * mu_r = (1 - phi) ^ (-2.5)
   * </pre>
   *
   * <p>
   * Valid for phi up to about 0.4.
   * </p>
   *
   * @param phi dispersed phase volume fraction
   * @return relative viscosity
   */
  private double calculateBrinkman(double phi) {
    if (phi >= 1.0) {
      return Double.MAX_VALUE;
    }
    return Math.pow(1.0 - phi, -2.5);
  }

  /**
   * Pal-Rhodes (1989) model for the full concentration range.
   *
   * <pre>
   * mu_r = (1 - phi / phi_max) ^ (-2.5 * phi_max)
   * </pre>
   *
   * <p>
   * Accounts for maximum packing fraction. Most widely used for oilfield emulsions.
   * </p>
   *
   * @param phi dispersed phase volume fraction
   * @return relative viscosity
   */
  private double calculatePalRhodes(double phi) {
    if (phi >= maxPackingFraction) {
      return 1e6; // Effectively solid (jammed)
    }
    double ratio = phi / maxPackingFraction;
    return Math.pow(1.0 - ratio, -2.5 * maxPackingFraction);
  }

  /**
   * Woelflin (1942) empirical model for oilfield emulsions.
   *
   * <p>
   * Uses empirical coefficients based on emulsion type classification:
   * </p>
   * <ul>
   * <li>Loose: mu_r = exp(2.5 * phi)</li>
   * <li>Medium: mu_r = exp(4.0 * phi)</li>
   * <li>Tight: mu_r = exp(6.0 * phi)</li>
   * </ul>
   *
   * @param phi dispersed phase volume fraction
   * @return relative viscosity
   */
  private double calculateWoelflin(double phi) {
    double k;
    if (tightnessFactor < 0.33) {
      k = 2.5; // Loose emulsion
    } else if (tightnessFactor < 0.67) {
      k = 4.0; // Medium emulsion
    } else {
      k = 6.0; // Tight emulsion
    }
    return Math.exp(k * phi);
  }

  /**
   * Richardson (1950) exponential model.
   *
   * <pre>
   * mu_r = exp(k * phi)
   * </pre>
   *
   * <p>
   * k is typically between 2.5 and 5.0 depending on droplet size distribution.
   * </p>
   *
   * @param phi dispersed phase volume fraction
   * @return relative viscosity
   */
  private double calculateRichardson(double phi) {
    double k = 2.5 + 5.0 * tightnessFactor;
    return Math.exp(k * phi);
  }

  /**
   * Calculates the phase inversion point (critical water cut).
   *
   * <p>
   * Uses the Arirachakaran et al. (1989) correlation:
   * </p>
   *
   * <pre>
   * WC_inv = 1 / (1 + (mu_o / mu_w) ^ 0.25 * (rho_w / rho_o) ^ 0.5)
   * </pre>
   *
   * <p>
   * Modified by tightness factor and chemical effects.
   * </p>
   *
   * @return inversion water cut as volume fraction
   */
  private double calculateInversionPoint() {
    double viscRatio = oilViscosity / waterViscosity;
    double densRatio = waterDensity / oilDensity;

    // Arirachakaran correlation
    double invWC = 1.0 / (1.0 + Math.pow(viscRatio, 0.25) * Math.pow(densRatio, 0.5));

    // Clamp to reasonable range
    invWC = Math.max(0.20, Math.min(0.90, invWC));

    // Tight emulsions shift inversion to higher water cuts (emulsifier stabilises W/O)
    invWC += (tightnessFactor - 0.5) * 0.10;
    invWC = Math.max(0.15, Math.min(0.95, invWC));

    return invWC;
  }

  /**
   * Calculates emulsion viscosity over a range of water cuts.
   *
   * <p>
   * Useful for generating viscosity vs water cut curves for production engineering.
   * </p>
   *
   * @param waterCutMin minimum water cut (volume fraction)
   * @param waterCutMax maximum water cut (volume fraction)
   * @param steps number of data points
   * @return a two-dimensional array where [i][0] is water cut and [i][1] is effective viscosity in
   *         mPa.s
   */
  public double[][] calculateViscosityCurve(double waterCutMin, double waterCutMax, int steps) {
    double[][] curve = new double[steps][2];
    double originalWaterCut = this.waterCut;

    for (int i = 0; i < steps; i++) {
      double wc = waterCutMin + (waterCutMax - waterCutMin) * i / (steps - 1);
      this.waterCut = wc;
      calculate();
      curve[i][0] = wc;
      curve[i][1] = effectiveViscosity;
    }

    // Restore original water cut
    this.waterCut = originalWaterCut;
    calculate();

    return curve;
  }

  // ========== Getters for Results ==========

  /**
   * Gets the effective emulsion viscosity.
   *
   * @return effective viscosity in mPa.s (cP)
   */
  public double getEffectiveViscosity() {
    return effectiveViscosity;
  }

  /**
   * Gets the viscosity ratio.
   *
   * @return viscosity ratio (emulsion / continuous phase)
   */
  public double getViscosityRatio() {
    return viscosityRatio;
  }

  /**
   * Gets the relative viscosity from the model.
   *
   * @return relative viscosity (dimensionless)
   */
  public double getRelativeViscosity() {
    return relativeViscosity;
  }

  /**
   * Gets the predicted phase inversion water cut.
   *
   * @return inversion water cut as volume fraction
   */
  public double getInversionWaterCut() {
    return inversionWaterCut;
  }

  /**
   * Gets the current emulsion type.
   *
   * @return "W/O" (water-in-oil) or "O/W" (oil-in-water)
   */
  public String getEmulsionType() {
    return emulsionType;
  }

  /**
   * Checks whether the emulsion is inverted (oil-in-water).
   *
   * @return true if the emulsion is O/W (inverted)
   */
  public boolean isInverted() {
    return isInverted;
  }

  // ========== Setters for Input Parameters ==========

  /**
   * Sets the oil dynamic viscosity.
   *
   * @param oilViscosity oil viscosity in mPa.s (cP)
   */
  public void setOilViscosity(double oilViscosity) {
    this.oilViscosity = oilViscosity;
  }

  /**
   * Sets the water dynamic viscosity.
   *
   * @param waterViscosity water viscosity in mPa.s (cP)
   */
  public void setWaterViscosity(double waterViscosity) {
    this.waterViscosity = waterViscosity;
  }

  /**
   * Sets the oil density.
   *
   * @param oilDensity oil density in kg/m3
   */
  public void setOilDensity(double oilDensity) {
    this.oilDensity = oilDensity;
  }

  /**
   * Sets the water density.
   *
   * @param waterDensity water density in kg/m3
   */
  public void setWaterDensity(double waterDensity) {
    this.waterDensity = waterDensity;
  }

  /**
   * Sets the water cut.
   *
   * @param waterCut water cut as volume fraction (0.0 to 1.0)
   */
  public void setWaterCut(double waterCut) {
    this.waterCut = waterCut;
  }

  /**
   * Sets the temperature.
   *
   * @param temperatureC temperature in degrees Celsius
   */
  public void setTemperatureC(double temperatureC) {
    this.temperatureC = temperatureC;
  }

  /**
   * Sets the pressure.
   *
   * @param pressureBara pressure in bara
   */
  public void setPressureBara(double pressureBara) {
    this.pressureBara = pressureBara;
  }

  /**
   * Sets the maximum packing fraction for the Pal-Rhodes model.
   *
   * @param maxPackingFraction maximum packing fraction (0.5 to 0.95, default 0.74 for random close
   *        packing)
   */
  public void setMaxPackingFraction(double maxPackingFraction) {
    this.maxPackingFraction = maxPackingFraction;
  }

  /**
   * Sets the viscosity model.
   *
   * @param model model name: "einstein", "taylor", "brinkman", "pal_rhodes", "woelflin",
   *        "richardson"
   */
  public void setModel(String model) {
    this.model = model;
  }

  /**
   * Sets the emulsion tightness factor.
   *
   * @param tightnessFactor tightness factor (0.0 = loose, 1.0 = tight)
   */
  public void setTightnessFactor(double tightnessFactor) {
    this.tightnessFactor = tightnessFactor;
  }

  /**
   * Sets the interfacial tension.
   *
   * @param interfacialTension interfacial tension in mN/m
   */
  public void setInterfacialTension(double interfacialTension) {
    this.interfacialTension = interfacialTension;
  }

  /**
   * Sets the shear rate.
   *
   * @param shearRate shear rate in 1/s
   */
  public void setShearRate(double shearRate) {
    this.shearRate = shearRate;
  }

  /**
   * Sets whether a demulsifier chemical is present.
   *
   * @param demulsifierPresent true if demulsifier is present
   */
  public void setDemulsifierPresent(boolean demulsifierPresent) {
    this.demulsifierPresent = demulsifierPresent;
  }

  /**
   * Sets the demulsifier efficiency.
   *
   * @param demulsifierEfficiency efficiency between 0.0 and 1.0
   */
  public void setDemulsifierEfficiency(double demulsifierEfficiency) {
    this.demulsifierEfficiency = demulsifierEfficiency;
  }

  /**
   * Gets the oil viscosity.
   *
   * @return oil viscosity in mPa.s (cP)
   */
  public double getOilViscosity() {
    return oilViscosity;
  }

  /**
   * Gets the water viscosity.
   *
   * @return water viscosity in mPa.s (cP)
   */
  public double getWaterViscosity() {
    return waterViscosity;
  }

  /**
   * Gets the water cut.
   *
   * @return water cut as volume fraction
   */
  public double getWaterCut() {
    return waterCut;
  }

  /**
   * Gets the model name.
   *
   * @return model name
   */
  public String getModel() {
    return model;
  }

  /**
   * Converts results to a map suitable for JSON serialization.
   *
   * @return map of all inputs and results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input parameters
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("oilViscosity_cP", oilViscosity);
    inputs.put("waterViscosity_cP", waterViscosity);
    inputs.put("oilDensity_kgm3", oilDensity);
    inputs.put("waterDensity_kgm3", waterDensity);
    inputs.put("waterCut", waterCut);
    inputs.put("temperatureC", temperatureC);
    inputs.put("pressureBara", pressureBara);
    inputs.put("model", model);
    inputs.put("tightnessFactor", tightnessFactor);
    inputs.put("maxPackingFraction", maxPackingFraction);
    result.put("inputs", inputs);

    // Results
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("effectiveViscosity_cP", effectiveViscosity);
    results.put("relativeViscosity", relativeViscosity);
    results.put("viscosityRatio", viscosityRatio);
    results.put("emulsionType", emulsionType);
    results.put("isInverted", isInverted);
    results.put("inversionWaterCut", inversionWaterCut);
    result.put("results", results);

    return result;
  }

  /**
   * Returns JSON representation of all inputs and results.
   *
   * @return a JSON string
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
