package neqsim.process.safety.dispersion;

import java.io.Serializable;
import java.util.Locale;

/**
 * Result from a gas dispersion screening calculation.
 *
 * <p>
 * The result is deliberately compact so it can be used from process simulations, dynamic release
 * studies, notebooks, MCP tools and QRA workflows without depending on a CFD model. Distances are
 * centerline screening distances from the release point and should be validated with detailed
 * dispersion software for layout or regulatory decisions.
 *
 * @author ESOL
 * @version 1.0
 */
public class GasDispersionResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String scenarioName;
  private final String selectedModel;
  private final double massReleaseRateKgPerS;
  private final double flammableMassReleaseRateKgPerS;
  private final double sourceDensityKgPerM3;
  private final double airDensityKgPerM3;
  private final double fuelMoleFraction;
  private final double fuelMassFraction;
  private final double lowerFlammableLimitVolumeFraction;
  private final double flammableEndpointFractionOfLfl;
  private final double distanceToFlammableEndpointM;
  private final double distanceToHalfLflM;
  private final double distanceToLflM;
  private final double flammableCloudVolumeM3;
  private final String toxicComponentName;
  private final double toxicThresholdPpm;
  private final double toxicDistanceM;
  private final double windSpeedMPerS;
  private final char stabilityClass;
  private final String screeningBasis;

  /**
   * Creates a gas dispersion result.
   *
   * @param scenarioName scenario identifier
   * @param selectedModel selected dispersion model name
   * @param massReleaseRateKgPerS total released gas mass rate in kg/s
   * @param flammableMassReleaseRateKgPerS released flammable mass rate in kg/s
   * @param sourceDensityKgPerM3 expanded source gas density in kg/m3
   * @param airDensityKgPerM3 ambient air density in kg/m3
   * @param fuelMoleFraction fuel mole fraction in the released gas
   * @param fuelMassFraction fuel mass fraction in the released gas
   * @param lowerFlammableLimitVolumeFraction lower flammable limit as volume fraction in air
  * @param flammableEndpointFractionOfLfl endpoint concentration fraction of LFL
  * @param distanceToFlammableEndpointM distance to configured endpoint in m
   * @param distanceToHalfLflM distance to 50 percent LFL in m
   * @param distanceToLflM distance to 100 percent LFL in m
   * @param flammableCloudVolumeM3 estimated flammable cloud volume in m3
   * @param toxicComponentName toxic component name, or empty string when not evaluated
   * @param toxicThresholdPpm toxic endpoint threshold in ppm
   * @param toxicDistanceM distance to the toxic endpoint in m
   * @param windSpeedMPerS wind speed used in m/s
   * @param stabilityClass Pasquill stability class A to F
   * @param screeningBasis text describing the screening method basis
   */
  public GasDispersionResult(String scenarioName, String selectedModel,
      double massReleaseRateKgPerS, double flammableMassReleaseRateKgPerS,
      double sourceDensityKgPerM3, double airDensityKgPerM3, double fuelMoleFraction,
      double fuelMassFraction, double lowerFlammableLimitVolumeFraction,
      double flammableEndpointFractionOfLfl, double distanceToFlammableEndpointM,
      double distanceToHalfLflM, double distanceToLflM, double flammableCloudVolumeM3,
      String toxicComponentName, double toxicThresholdPpm, double toxicDistanceM,
      double windSpeedMPerS, char stabilityClass, String screeningBasis) {
    this.scenarioName = scenarioName;
    this.selectedModel = selectedModel;
    this.massReleaseRateKgPerS = massReleaseRateKgPerS;
    this.flammableMassReleaseRateKgPerS = flammableMassReleaseRateKgPerS;
    this.sourceDensityKgPerM3 = sourceDensityKgPerM3;
    this.airDensityKgPerM3 = airDensityKgPerM3;
    this.fuelMoleFraction = fuelMoleFraction;
    this.fuelMassFraction = fuelMassFraction;
    this.lowerFlammableLimitVolumeFraction = lowerFlammableLimitVolumeFraction;
    this.flammableEndpointFractionOfLfl = flammableEndpointFractionOfLfl;
    this.distanceToFlammableEndpointM = distanceToFlammableEndpointM;
    this.distanceToHalfLflM = distanceToHalfLflM;
    this.distanceToLflM = distanceToLflM;
    this.flammableCloudVolumeM3 = flammableCloudVolumeM3;
    this.toxicComponentName = toxicComponentName == null ? "" : toxicComponentName;
    this.toxicThresholdPpm = toxicThresholdPpm;
    this.toxicDistanceM = toxicDistanceM;
    this.windSpeedMPerS = windSpeedMPerS;
    this.stabilityClass = stabilityClass;
    this.screeningBasis = screeningBasis == null ? "" : screeningBasis;
  }

  /**
   * Creates a gas dispersion result using 50 percent LFL as the configured endpoint.
   *
   * @param scenarioName scenario identifier
   * @param selectedModel selected dispersion model name
   * @param massReleaseRateKgPerS total released gas mass rate in kg/s
   * @param flammableMassReleaseRateKgPerS released flammable mass rate in kg/s
   * @param sourceDensityKgPerM3 expanded source gas density in kg/m3
   * @param airDensityKgPerM3 ambient air density in kg/m3
   * @param fuelMoleFraction fuel mole fraction in the released gas
   * @param fuelMassFraction fuel mass fraction in the released gas
   * @param lowerFlammableLimitVolumeFraction lower flammable limit as volume fraction in air
   * @param distanceToHalfLflM distance to 50 percent LFL in m
   * @param distanceToLflM distance to 100 percent LFL in m
   * @param flammableCloudVolumeM3 estimated flammable cloud volume in m3
   * @param toxicComponentName toxic component name, or empty string when not evaluated
   * @param toxicThresholdPpm toxic endpoint threshold in ppm
   * @param toxicDistanceM distance to the toxic endpoint in m
   * @param windSpeedMPerS wind speed used in m/s
   * @param stabilityClass Pasquill stability class A to F
   * @param screeningBasis text describing the screening method basis
   */
  public GasDispersionResult(String scenarioName, String selectedModel,
      double massReleaseRateKgPerS, double flammableMassReleaseRateKgPerS,
      double sourceDensityKgPerM3, double airDensityKgPerM3, double fuelMoleFraction,
      double fuelMassFraction, double lowerFlammableLimitVolumeFraction, double distanceToHalfLflM,
      double distanceToLflM, double flammableCloudVolumeM3, String toxicComponentName,
      double toxicThresholdPpm, double toxicDistanceM, double windSpeedMPerS, char stabilityClass,
      String screeningBasis) {
    this(scenarioName, selectedModel, massReleaseRateKgPerS, flammableMassReleaseRateKgPerS,
        sourceDensityKgPerM3, airDensityKgPerM3, fuelMoleFraction, fuelMassFraction,
        lowerFlammableLimitVolumeFraction, 0.5, distanceToHalfLflM, distanceToHalfLflM,
        distanceToLflM, flammableCloudVolumeM3, toxicComponentName, toxicThresholdPpm,
        toxicDistanceM, windSpeedMPerS, stabilityClass, screeningBasis);
  }

  /**
   * Gets the scenario name.
   *
   * @return scenario name
   */
  public String getScenarioName() {
    return scenarioName;
  }

  /**
   * Gets the selected dispersion model.
   *
   * @return model name
   */
  public String getSelectedModel() {
    return selectedModel;
  }

  /**
   * Gets the total release rate.
   *
   * @return mass release rate in kg/s
   */
  public double getMassReleaseRateKgPerS() {
    return massReleaseRateKgPerS;
  }

  /**
   * Gets the flammable release rate.
   *
   * @return flammable mass release rate in kg/s
   */
  public double getFlammableMassReleaseRateKgPerS() {
    return flammableMassReleaseRateKgPerS;
  }

  /**
   * Gets the expanded source gas density.
   *
   * @return source density in kg/m3
   */
  public double getSourceDensityKgPerM3() {
    return sourceDensityKgPerM3;
  }

  /**
   * Gets the ambient air density.
   *
   * @return air density in kg/m3
   */
  public double getAirDensityKgPerM3() {
    return airDensityKgPerM3;
  }

  /**
   * Gets the fuel mole fraction in the released gas.
   *
   * @return fuel mole fraction
   */
  public double getFuelMoleFraction() {
    return fuelMoleFraction;
  }

  /**
   * Gets the fuel mass fraction in the released gas.
   *
   * @return fuel mass fraction
   */
  public double getFuelMassFraction() {
    return fuelMassFraction;
  }

  /**
   * Gets the lower flammable limit.
   *
   * @return LFL as volume fraction in air
   */
  public double getLowerFlammableLimitVolumeFraction() {
    return lowerFlammableLimitVolumeFraction;
  }

  /**
   * Gets the configured flammable endpoint fraction of LFL.
   *
   * @return endpoint fraction of LFL, for example 0.20 or 0.50
   */
  public double getFlammableEndpointFractionOfLfl() {
    return flammableEndpointFractionOfLfl;
  }

  /**
   * Gets the distance to the configured flammable endpoint.
   *
   * @return distance to configured endpoint in m, or NaN if not reached
   */
  public double getDistanceToFlammableEndpointM() {
    return distanceToFlammableEndpointM;
  }

  /**
   * Gets the distance to 50 percent LFL.
   *
   * @return distance in m, or NaN if no flammable endpoint is reached
   */
  public double getDistanceToHalfLflM() {
    return distanceToHalfLflM;
  }

  /**
   * Gets the distance to 100 percent LFL.
   *
   * @return distance in m, or NaN if no flammable endpoint is reached
   */
  public double getDistanceToLflM() {
    return distanceToLflM;
  }

  /**
   * Gets the estimated flammable cloud volume.
   *
   * @return flammable cloud volume in m3
   */
  public double getFlammableCloudVolumeM3() {
    return flammableCloudVolumeM3;
  }

  /**
   * Gets the toxic component evaluated.
   *
   * @return toxic component name, or an empty string if not evaluated
   */
  public String getToxicComponentName() {
    return toxicComponentName;
  }

  /**
   * Gets the toxic threshold.
   *
   * @return toxic threshold in ppm
   */
  public double getToxicThresholdPpm() {
    return toxicThresholdPpm;
  }

  /**
   * Gets the toxic endpoint distance.
   *
   * @return toxic endpoint distance in m, or NaN if not evaluated or not reached
   */
  public double getToxicDistanceM() {
    return toxicDistanceM;
  }

  /**
   * Gets the wind speed used.
   *
   * @return wind speed in m/s
   */
  public double getWindSpeedMPerS() {
    return windSpeedMPerS;
  }

  /**
   * Gets the Pasquill stability class used.
   *
   * @return stability class A to F
   */
  public char getStabilityClass() {
    return stabilityClass;
  }

  /**
   * Gets the screening basis text.
   *
   * @return screening basis
   */
  public String getScreeningBasis() {
    return screeningBasis;
  }

  /**
   * Checks whether a flammable endpoint was found.
   *
   * @return true if a finite LFL distance was calculated
   */
  public boolean hasFlammableCloud() {
    return Double.isFinite(distanceToLflM) && distanceToLflM > 0.0;
  }

  /**
   * Checks whether a toxic endpoint was found.
   *
   * @return true if a finite toxic distance was calculated
   */
  public boolean hasToxicEndpoint() {
    return Double.isFinite(toxicDistanceM) && toxicDistanceM > 0.0;
  }

  /**
   * Export the result as a compact JSON object.
   *
   * @return JSON representation of this result
   */
  public String toJson() {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    appendString(json, "scenarioName", scenarioName, true);
    appendString(json, "selectedModel", selectedModel, true);
    appendNumber(json, "massReleaseRate_kg_s", massReleaseRateKgPerS, true);
    appendNumber(json, "flammableMassReleaseRate_kg_s", flammableMassReleaseRateKgPerS, true);
    appendNumber(json, "sourceDensity_kg_m3", sourceDensityKgPerM3, true);
    appendNumber(json, "airDensity_kg_m3", airDensityKgPerM3, true);
    appendNumber(json, "fuelMoleFraction", fuelMoleFraction, true);
    appendNumber(json, "fuelMassFraction", fuelMassFraction, true);
    appendNumber(json, "lowerFlammableLimit_volumeFraction", lowerFlammableLimitVolumeFraction,
        true);
    appendNumber(json, "flammableEndpointFractionOfLFL", flammableEndpointFractionOfLfl, true);
    appendNumber(json, "distanceToFlammableEndpoint_m", distanceToFlammableEndpointM, true);
    appendNumber(json, "distanceToHalfLFL_m", distanceToHalfLflM, true);
    appendNumber(json, "distanceToLFL_m", distanceToLflM, true);
    appendNumber(json, "flammableCloudVolume_m3", flammableCloudVolumeM3, true);
    appendString(json, "toxicComponentName", toxicComponentName, true);
    appendNumber(json, "toxicThreshold_ppm", toxicThresholdPpm, true);
    appendNumber(json, "toxicDistance_m", toxicDistanceM, true);
    appendNumber(json, "windSpeed_m_s", windSpeedMPerS, true);
    appendString(json, "stabilityClass", Character.toString(stabilityClass), true);
    appendString(json, "screeningBasis", screeningBasis, false);
    json.append("}\n");
    return json.toString();
  }

  /**
   * Append a JSON string field.
   *
   * @param json builder to append to
   * @param name field name
   * @param value field value
   * @param comma true to append a trailing comma
   */
  private static void appendString(StringBuilder json, String name, String value, boolean comma) {
    json.append("  \"").append(escapeJson(name)).append("\": \"").append(escapeJson(value))
        .append("\"");
    if (comma) {
      json.append(',');
    }
    json.append('\n');
  }

  /**
   * Append a JSON numeric field.
   *
   * @param json builder to append to
   * @param name field name
   * @param value numeric value
   * @param comma true to append a trailing comma
   */
  private static void appendNumber(StringBuilder json, String name, double value, boolean comma) {
    json.append("  \"").append(escapeJson(name)).append("\": ").append(jsonNumber(value));
    if (comma) {
      json.append(',');
    }
    json.append('\n');
  }

  /**
   * Format a JSON number while preserving JSON validity for NaN and infinity.
   *
   * @param value numeric value
   * @return formatted JSON number or null literal
   */
  private static String jsonNumber(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "null";
    }
    return String.format(Locale.ROOT, "%.10g", value);
  }

  /**
   * Escape a string for JSON output.
   *
   * @param value raw string value
   * @return escaped string value
   */
  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT,
        "GasDispersionResult[%s, model=%s, rate=%.3f kg/s, LFL=%.1f m, 50%%LFL=%.1f m]",
        scenarioName, selectedModel, massReleaseRateKgPerS, distanceToLflM, distanceToHalfLflM);
  }
}
