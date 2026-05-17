package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.util.fire.FireHeatLoadCalculator;

/**
 * Fire exposure definition for trapped-liquid rupture screening.
 *
 * <p>
 * The scenario converts an industry fire basis, such as an API 521 pool-fire heat input or a fixed
 * incident heat flux, into a heat flux applied to the exposed pipe or flange surface.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FireExposureScenario implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Fire exposure calculation method. */
  public enum FireType {
    /** API 521 pool-fire total heat load divided over exposed area. */
    API_521_POOL_FIRE,
    /** Fixed incident heat flux supplied by the user. */
    FIXED_HEAT_FLUX,
    /** Stefan-Boltzmann radiative fire model. */
    RADIATIVE_FIRE
  }

  private final String name;
  private final FireType fireType;
  private final double exposedAreaM2;
  private final double heatFluxWPerM2;
  private final double environmentalFactor;
  private final double emissivity;
  private final double viewFactor;
  private final double flameTemperatureK;
  private final double ambientTemperatureK;
  private final double passiveProtectionHeatFluxFactor;

  /**
   * Creates a fire exposure scenario.
   *
   * @param name scenario name
   * @param fireType fire calculation method
   * @param exposedAreaM2 exposed external area in m2; must be positive
   * @param heatFluxWPerM2 fixed heat flux in W/m2 for fixed-flux cases
   * @param environmentalFactor API 521 environmental factor for pool-fire cases
   * @param emissivity effective flame emissivity for radiative cases
   * @param viewFactor view factor for radiative cases
   * @param flameTemperatureK flame temperature in K for radiative cases
   * @param ambientTemperatureK ambient temperature in K; must be positive
   * @param passiveProtectionHeatFluxFactor heat flux multiplier after PFP or insulation, from 0 to
   *        1
   * @throws IllegalArgumentException if the scenario input is invalid
   */
  public FireExposureScenario(String name, FireType fireType, double exposedAreaM2,
      double heatFluxWPerM2, double environmentalFactor, double emissivity, double viewFactor,
      double flameTemperatureK, double ambientTemperatureK, double passiveProtectionHeatFluxFactor) {
    if (fireType == null) {
      throw new IllegalArgumentException("fireType must not be null");
    }
    validatePositive(exposedAreaM2, "exposedAreaM2");
    validatePositive(ambientTemperatureK, "ambientTemperatureK");
    if (passiveProtectionHeatFluxFactor < 0.0 || passiveProtectionHeatFluxFactor > 1.0) {
      throw new IllegalArgumentException("passiveProtectionHeatFluxFactor must be in [0,1]");
    }
    this.name = name == null || name.trim().isEmpty() ? fireType.name() : name.trim();
    this.fireType = fireType;
    this.exposedAreaM2 = exposedAreaM2;
    this.heatFluxWPerM2 = heatFluxWPerM2;
    this.environmentalFactor = environmentalFactor;
    this.emissivity = emissivity;
    this.viewFactor = viewFactor;
    this.flameTemperatureK = flameTemperatureK;
    this.ambientTemperatureK = ambientTemperatureK;
    this.passiveProtectionHeatFluxFactor = passiveProtectionHeatFluxFactor;
  }

  /**
   * Creates an API 521 pool-fire exposure.
   *
   * @param exposedAreaM2 exposed external area in m2; must be positive
   * @param environmentalFactor API 521 environmental factor; must be positive
   * @return fire exposure scenario
   */
  public static FireExposureScenario api521PoolFire(double exposedAreaM2,
      double environmentalFactor) {
    validatePositive(environmentalFactor, "environmentalFactor");
    double totalHeatW = FireHeatLoadCalculator.api521PoolFireHeatLoad(exposedAreaM2,
        environmentalFactor);
    return new FireExposureScenario("API 521 pool fire", FireType.API_521_POOL_FIRE,
        exposedAreaM2, totalHeatW / exposedAreaM2, environmentalFactor, 0.0, 0.0, 1200.0, 298.15,
        1.0);
  }

  /**
   * Creates a fixed heat-flux exposure.
   *
   * @param exposedAreaM2 exposed external area in m2; must be positive
   * @param heatFluxWPerM2 incident heat flux in W/m2; must be positive
   * @return fire exposure scenario
   */
  public static FireExposureScenario fixedHeatFlux(double exposedAreaM2,
      double heatFluxWPerM2) {
    validatePositive(heatFluxWPerM2, "heatFluxWPerM2");
    return new FireExposureScenario("Fixed heat flux", FireType.FIXED_HEAT_FLUX, exposedAreaM2,
        heatFluxWPerM2, 1.0, 0.0, 0.0, 1200.0, 298.15, 1.0);
  }

  /**
   * Creates a radiative fire exposure using Stefan-Boltzmann heat flux.
   *
   * @param exposedAreaM2 exposed external area in m2; must be positive
   * @param emissivity effective flame emissivity from 0 to 1
   * @param viewFactor geometric view factor from 0 to 1
   * @param flameTemperatureK flame temperature in K; must be positive
   * @param ambientTemperatureK ambient temperature in K; must be positive
   * @return fire exposure scenario
   */
  public static FireExposureScenario radiativeFire(double exposedAreaM2, double emissivity,
      double viewFactor, double flameTemperatureK, double ambientTemperatureK) {
    return new FireExposureScenario("Radiative fire", FireType.RADIATIVE_FIRE, exposedAreaM2,
        0.0, 1.0, emissivity, viewFactor, flameTemperatureK, ambientTemperatureK, 1.0);
  }

  /**
   * Returns a copy with a passive-fire-protection heat-flux factor applied.
   *
   * @param heatFluxFactor heat flux multiplier from 0 to 1
   * @return protected fire exposure scenario
   */
  public FireExposureScenario withPassiveProtectionFactor(double heatFluxFactor) {
    return new FireExposureScenario(name, fireType, exposedAreaM2, heatFluxWPerM2,
        environmentalFactor, emissivity, viewFactor, flameTemperatureK, ambientTemperatureK,
        heatFluxFactor);
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
   * Gets the fire type.
   *
   * @return fire type
   */
  public FireType getFireType() {
    return fireType;
  }

  /**
   * Gets exposed area.
   *
   * @return exposed area in m2
   */
  public double getExposedAreaM2() {
    return exposedAreaM2;
  }

  /**
   * Gets the ambient temperature.
   *
   * @return ambient temperature in K
   */
  public double getAmbientTemperatureK() {
    return ambientTemperatureK;
  }

  /**
   * Gets the passive protection heat-flux multiplier.
   *
   * @return heat-flux multiplier from 0 to 1
   */
  public double getPassiveProtectionHeatFluxFactor() {
    return passiveProtectionHeatFluxFactor;
  }

  /**
   * Calculates incident heat flux into the external wall surface.
   *
   * @param outerSurfaceTemperatureK current outer wall temperature in K; must be positive
   * @return incident heat flux in W/m2
   */
  public double incidentHeatFlux(double outerSurfaceTemperatureK) {
    validatePositive(outerSurfaceTemperatureK, "outerSurfaceTemperatureK");
    double flux;
    if (fireType == FireType.RADIATIVE_FIRE) {
      flux = FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(emissivity, viewFactor,
          flameTemperatureK, outerSurfaceTemperatureK);
    } else {
      flux = heatFluxWPerM2;
    }
    return Math.max(0.0, flux) * passiveProtectionHeatFluxFactor;
  }

  /**
   * Calculates total incident heat input to the exposed area.
   *
   * @param outerSurfaceTemperatureK current outer wall temperature in K; must be positive
   * @return heat input in W
   */
  public double heatInputW(double outerSurfaceTemperatureK) {
    return incidentHeatFlux(outerSurfaceTemperatureK) * exposedAreaM2;
  }

  /**
   * Converts the scenario to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", name);
    map.put("fireType", fireType.name());
    map.put("exposedAreaM2", exposedAreaM2);
    map.put("heatFluxWPerM2", heatFluxWPerM2);
    map.put("environmentalFactor", environmentalFactor);
    map.put("emissivity", emissivity);
    map.put("viewFactor", viewFactor);
    map.put("flameTemperatureK", flameTemperatureK);
    map.put("ambientTemperatureK", ambientTemperatureK);
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
   * Validates that a numeric value is positive and finite.
   *
   * @param value value to validate
   * @param name parameter name used in exception messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }
}
