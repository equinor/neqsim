package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fire exposure definition for blowdown pipe fire-rupture heat-up calculations.
 *
 * <p>
 * The scenario mirrors the radiative plus convective heat-flux expression used in the strain-rate workbook and exposes
 * all heat-transfer inputs explicitly for evidence review.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipeFireRuptureScenario implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final double STEFAN_BOLTZMANN_W_PER_M2K4 = 5.67e-8;

  private final String name;
  private final double fireTemperatureC;
  private final double gasTemperatureC;
  private final double convectiveHeatTransferCoefficientWPerM2K;
  private final double fireEmissivity;
  private final double metalEmissivity;
  private final double metalAbsorptivity;
  private final double passiveProtectionHeatFluxFactor;

  /**
   * Creates a pipe fire exposure scenario.
   *
   * @param name scenario name; defaults to pipe fire when null or empty
   * @param fireTemperatureC radiating fire temperature in degrees Celsius
   * @param gasTemperatureC convecting gas temperature in degrees Celsius
   * @param convectiveHeatTransferCoefficientWPerM2K convective heat transfer coefficient in W/m2-K; must be
   * non-negative
   * @param fireEmissivity fire emissivity from 0 to 1
   * @param metalEmissivity metal emissivity from 0 to 1
   * @param metalAbsorptivity metal absorptivity from 0 to 1
   * @param passiveProtectionHeatFluxFactor heat-flux multiplier from 0 to 1 after PFP/insulation
   * @throws IllegalArgumentException if any input is outside its valid range
   */
  public PipeFireRuptureScenario(String name, double fireTemperatureC, double gasTemperatureC,
      double convectiveHeatTransferCoefficientWPerM2K, double fireEmissivity, double metalEmissivity,
      double metalAbsorptivity, double passiveProtectionHeatFluxFactor) {
    validateNonNegative(convectiveHeatTransferCoefficientWPerM2K, "convectiveHeatTransferCoefficientWPerM2K");
    validateFraction(fireEmissivity, "fireEmissivity");
    validateFraction(metalEmissivity, "metalEmissivity");
    validateFraction(metalAbsorptivity, "metalAbsorptivity");
    validateFraction(passiveProtectionHeatFluxFactor, "passiveProtectionHeatFluxFactor");
    this.name = name == null || name.trim().isEmpty() ? "Pipe fire" : name.trim();
    this.fireTemperatureC = fireTemperatureC;
    this.gasTemperatureC = gasTemperatureC;
    this.convectiveHeatTransferCoefficientWPerM2K = convectiveHeatTransferCoefficientWPerM2K;
    this.fireEmissivity = fireEmissivity;
    this.metalEmissivity = metalEmissivity;
    this.metalAbsorptivity = metalAbsorptivity;
    this.passiveProtectionHeatFluxFactor = passiveProtectionHeatFluxFactor;
  }

  /**
   * Creates the workbook's large-jet-fire scenario.
   *
   * @return large jet fire scenario
   */
  public static PipeFireRuptureScenario spreadsheetLargeJetFire() {
    return new PipeFireRuptureScenario("Large jet fire 350 kW/m2", 1155.0, 1155.0, 100.0, 1.0, 0.85, 0.85, 1.0);
  }

  /**
   * Creates the workbook's small-jet-fire scenario.
   *
   * @return small jet fire scenario
   */
  public static PipeFireRuptureScenario spreadsheetSmallJetFire() {
    return new PipeFireRuptureScenario("Small jet fire 250 kW/m2", 1000.0, 1000.0, 100.0, 1.0, 0.85, 0.85, 1.0);
  }

  /**
   * Creates the workbook's pool-fire scenario.
   *
   * @return pool fire scenario
   */
  public static PipeFireRuptureScenario spreadsheetPoolFire() {
    return new PipeFireRuptureScenario("Pool fire 250 kW/m2", 1125.0, 1125.0, 30.0, 1.0, 0.85, 0.85, 1.0);
  }

  /**
   * Returns a copy with passive fire protection or insulation applied.
   *
   * @param heatFluxFactor heat-flux multiplier from 0 to 1
   * @return copied scenario with the new heat-flux factor
   */
  public PipeFireRuptureScenario withPassiveProtectionFactor(double heatFluxFactor) {
    return new PipeFireRuptureScenario(name, fireTemperatureC, gasTemperatureC,
	convectiveHeatTransferCoefficientWPerM2K, fireEmissivity, metalEmissivity, metalAbsorptivity, heatFluxFactor);
  }

  /**
   * Gets the scenario name.
   *
   * @return scenario name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets fire temperature.
   *
   * @return fire temperature in degrees Celsius
   */
  public double getFireTemperatureC() {
    return fireTemperatureC;
  }

  /**
   * Gets convecting gas temperature.
   *
   * @return gas temperature in degrees Celsius
   */
  public double getGasTemperatureC() {
    return gasTemperatureC;
  }

  /**
   * Gets convective heat-transfer coefficient.
   *
   * @return convective coefficient in W/m2-K
   */
  public double getConvectiveHeatTransferCoefficientWPerM2K() {
    return convectiveHeatTransferCoefficientWPerM2K;
  }

  /**
   * Gets fire emissivity.
   *
   * @return fire emissivity fraction
   */
  public double getFireEmissivity() {
    return fireEmissivity;
  }

  /**
   * Gets metal emissivity.
   *
   * @return metal emissivity fraction
   */
  public double getMetalEmissivity() {
    return metalEmissivity;
  }

  /**
   * Gets metal absorptivity.
   *
   * @return metal absorptivity fraction
   */
  public double getMetalAbsorptivity() {
    return metalAbsorptivity;
  }

  /**
   * Gets passive fire protection heat-flux factor.
   *
   * @return heat-flux multiplier from 0 to 1
   */
  public double getPassiveProtectionHeatFluxFactor() {
    return passiveProtectionHeatFluxFactor;
  }

  /**
   * Calculates heat flux into the pipe outer surface.
   *
   * @param outerSurfaceTemperatureC outer surface temperature in degrees Celsius
   * @return absorbed heat flux in W/m2
   */
  public double heatFluxWPerM2(double outerSurfaceTemperatureC) {
    double radiativeFlux = STEFAN_BOLTZMANN_W_PER_M2K4
	* (fireEmissivity * metalAbsorptivity * Math.pow(fireTemperatureC + 273.0, 4.0)
	    - metalEmissivity * Math.pow(outerSurfaceTemperatureC + 273.0, 4.0));
    double convectiveFlux = convectiveHeatTransferCoefficientWPerM2K * (gasTemperatureC - outerSurfaceTemperatureC);
    return Math.max(0.0, (radiativeFlux + convectiveFlux) * passiveProtectionHeatFluxFactor);
  }

  /**
   * Calculates heat flux into the pipe outer surface.
   *
   * @param outerSurfaceTemperatureC outer surface temperature in degrees Celsius
   * @return absorbed heat flux in kW/m2
   */
  public double heatFluxKWPerM2(double outerSurfaceTemperatureC) {
    return heatFluxWPerM2(outerSurfaceTemperatureC) / 1000.0;
  }

  /**
   * Converts the scenario to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", name);
    map.put("fireTemperatureC", fireTemperatureC);
    map.put("gasTemperatureC", gasTemperatureC);
    map.put("convectiveHeatTransferCoefficientWPerM2K", convectiveHeatTransferCoefficientWPerM2K);
    map.put("fireEmissivity", fireEmissivity);
    map.put("metalEmissivity", metalEmissivity);
    map.put("metalAbsorptivity", metalAbsorptivity);
    map.put("passiveProtectionHeatFluxFactor", passiveProtectionHeatFluxFactor);
    return map;
  }

  /**
   * Converts the scenario to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Validates that a value is non-negative and finite.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validateNonNegative(double value, String name) {
    if (value < 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }

  /**
   * Validates a fraction.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is outside 0 to 1
   */
  private static void validateFraction(double value, String name) {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be between 0 and 1");
    }
  }
}
