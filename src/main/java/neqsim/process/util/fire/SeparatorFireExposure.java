package neqsim.process.util.fire;

import java.util.Objects;
import neqsim.process.equipment.separator.Separator;

/**
 * Convenience wrapper that wires separator geometry and process conditions into the fire
 * calculators so they can be used directly from NeqSim process simulations.
 */
public final class SeparatorFireExposure {

  private SeparatorFireExposure() {}

  /**
   * Fire scenario configuration with sensible defaults so callers only override what they need.
   */
  public static final class FireScenarioConfig {
    private double fireTemperatureK = 1200.0;
    private double environmentalFactor = 1.0;
    private double emissivity = 0.35;
    private double viewFactor = 0.7;
    private double externalFilmCoefficientWPerM2K = 35.0;
    private double wettedInternalFilmCoefficientWPerM2K = 1500.0;
    private double unwettedInternalFilmCoefficientWPerM2K = 50.0;
    private double wallThicknessM = 0.02;
    private double thermalConductivityWPerMPerK = 45.0;
    private double allowableTensileStrengthPa = 2.4e8;

    public double fireTemperatureK() {
      return fireTemperatureK;
    }

    public FireScenarioConfig setFireTemperatureK(double fireTemperatureK) {
      this.fireTemperatureK = fireTemperatureK;
      return this;
    }

    public double environmentalFactor() {
      return environmentalFactor;
    }

    public FireScenarioConfig setEnvironmentalFactor(double environmentalFactor) {
      this.environmentalFactor = environmentalFactor;
      return this;
    }

    public double emissivity() {
      return emissivity;
    }

    public FireScenarioConfig setEmissivity(double emissivity) {
      this.emissivity = emissivity;
      return this;
    }

    public double viewFactor() {
      return viewFactor;
    }

    public FireScenarioConfig setViewFactor(double viewFactor) {
      this.viewFactor = viewFactor;
      return this;
    }

    public double externalFilmCoefficientWPerM2K() {
      return externalFilmCoefficientWPerM2K;
    }

    public FireScenarioConfig setExternalFilmCoefficientWPerM2K(
        double externalFilmCoefficientWPerM2K) {
      this.externalFilmCoefficientWPerM2K = externalFilmCoefficientWPerM2K;
      return this;
    }

    public double wettedInternalFilmCoefficientWPerM2K() {
      return wettedInternalFilmCoefficientWPerM2K;
    }

    public FireScenarioConfig setWettedInternalFilmCoefficientWPerM2K(
        double wettedInternalFilmCoefficientWPerM2K) {
      this.wettedInternalFilmCoefficientWPerM2K = wettedInternalFilmCoefficientWPerM2K;
      return this;
    }

    public double unwettedInternalFilmCoefficientWPerM2K() {
      return unwettedInternalFilmCoefficientWPerM2K;
    }

    public FireScenarioConfig setUnwettedInternalFilmCoefficientWPerM2K(
        double unwettedInternalFilmCoefficientWPerM2K) {
      this.unwettedInternalFilmCoefficientWPerM2K = unwettedInternalFilmCoefficientWPerM2K;
      return this;
    }

    public double wallThicknessM() {
      return wallThicknessM;
    }

    public FireScenarioConfig setWallThicknessM(double wallThicknessM) {
      this.wallThicknessM = wallThicknessM;
      return this;
    }

    public double thermalConductivityWPerMPerK() {
      return thermalConductivityWPerMPerK;
    }

    public FireScenarioConfig setThermalConductivityWPerMPerK(double thermalConductivityWPerMPerK) {
      this.thermalConductivityWPerMPerK = thermalConductivityWPerMPerK;
      return this;
    }

    public double allowableTensileStrengthPa() {
      return allowableTensileStrengthPa;
    }

    public FireScenarioConfig setAllowableTensileStrengthPa(double allowableTensileStrengthPa) {
      this.allowableTensileStrengthPa = allowableTensileStrengthPa;
      return this;
    }
  }

  /**
   * Result bundle that exposes all fire-related calculations for a separator timestep.
   */
  public static final class FireExposureResult {
    private final double wettedArea;
    private final double unwettedArea;
    private final double poolFireHeatLoad;
    private final double radiativeHeatFlux;
    private final double unwettedRadiativeHeat;
    private final double flareRadiativeFlux;
    private final double flareRadiativeHeat;
    private final double totalFireHeat;
    private final FireHeatTransferCalculator.SurfaceTemperatureResult wettedWall;
    private final FireHeatTransferCalculator.SurfaceTemperatureResult unwettedWall;
    private final double vonMisesStressPa;
    private final double ruptureMarginPa;
    private final boolean ruptureLikely;

    public FireExposureResult(double wettedArea, double unwettedArea, double poolFireHeatLoad,
        double radiativeHeatFlux, double unwettedRadiativeHeat, double flareRadiativeFlux,
        double flareRadiativeHeat, double totalFireHeat,
        FireHeatTransferCalculator.SurfaceTemperatureResult wettedWall,
        FireHeatTransferCalculator.SurfaceTemperatureResult unwettedWall, double vonMisesStressPa,
        double ruptureMarginPa, boolean ruptureLikely) {
      this.wettedArea = wettedArea;
      this.unwettedArea = unwettedArea;
      this.poolFireHeatLoad = poolFireHeatLoad;
      this.radiativeHeatFlux = radiativeHeatFlux;
      this.unwettedRadiativeHeat = unwettedRadiativeHeat;
      this.flareRadiativeFlux = flareRadiativeFlux;
      this.flareRadiativeHeat = flareRadiativeHeat;
      this.totalFireHeat = totalFireHeat;
      this.wettedWall = wettedWall;
      this.unwettedWall = unwettedWall;
      this.vonMisesStressPa = vonMisesStressPa;
      this.ruptureMarginPa = ruptureMarginPa;
      this.ruptureLikely = ruptureLikely;
    }

    public double wettedArea() {
      return wettedArea;
    }

    public double unwettedArea() {
      return unwettedArea;
    }

    public double poolFireHeatLoad() {
      return poolFireHeatLoad;
    }

    public double radiativeHeatFlux() {
      return radiativeHeatFlux;
    }

    public double unwettedRadiativeHeat() {
      return unwettedRadiativeHeat;
    }

    /**
     * Radiative heat flux from the flare flame at the specified distance (W/m2).
     *
     * @return radiative heat flux in W/m2
     */
    public double flareRadiativeFlux() {
      return flareRadiativeFlux;
    }

    /**
     * Heat from flare radiation incident on the separator shell using the inner surface area as the
     * exposure area approximation (W).
     */
    public double flareRadiativeHeat() {
      return flareRadiativeHeat;
    }

    /**
     * Combined pool-fire and radiative heat acting on the separator (W).
     *
     * @return total fire heat load in watts
     */
    public double totalFireHeat() {
      return totalFireHeat;
    }

    public FireHeatTransferCalculator.SurfaceTemperatureResult wettedWall() {
      return wettedWall;
    }

    public FireHeatTransferCalculator.SurfaceTemperatureResult unwettedWall() {
      return unwettedWall;
    }

    public double vonMisesStressPa() {
      return vonMisesStressPa;
    }

    public double ruptureMarginPa() {
      return ruptureMarginPa;
    }

    public boolean isRuptureLikely() {
      return ruptureLikely;
    }
  }

  /**
   * Aggregates fire heat-load, wall-temperature, and rupture calculations for a separator timestep
   * using only the separator and a simple configuration object.
   *
   * @param separator separator instance that already reflects the current
   *        level/pressure/temperature
   * @param config fire scenario configuration; defaults match typical API 521 pool fire settings
   * @return populated {@link FireExposureResult}
   */
  public static FireExposureResult evaluate(Separator separator, FireScenarioConfig config) {
    return evaluate(separator, config, null, Double.NaN);
  }

  /**
   * Aggregates fire heat-load, wall-temperature, and rupture calculations including optional flare
   * radiation based on the actual flaring heat duty.
   *
   * @param separator separator instance that already reflects the current
   *        level/pressure/temperature
   * @param config fire scenario configuration; defaults match typical API 521 pool fire settings
   * @param flare flare instance supplying real-time heat duty and radiation parameters; may be null
   * @param flareGroundDistanceM horizontal distance from flare base to separator [m]
   * @return populated {@link FireExposureResult}
   */
  public static FireExposureResult evaluate(Separator separator, FireScenarioConfig config,
      neqsim.process.equipment.flare.Flare flare, double flareGroundDistanceM) {
    Objects.requireNonNull(separator, "separator");
    Objects.requireNonNull(config, "config");

    double processTemperatureK = separator.getGasOutStream().getTemperature();
    double separatorPressurePa = separator.getGasOutStream().getPressure("bara") * 1.0e5;
    double innerRadius = separator.getInternalDiameter() / 2.0;

    double wettedArea = separator.getWettedArea();
    double unwettedArea = separator.getUnwettedArea();

    double poolFireHeatLoad =
        FireHeatLoadCalculator.api521PoolFireHeatLoad(wettedArea, config.environmentalFactor());

    FireHeatTransferCalculator.SurfaceTemperatureResult wettedWall = FireHeatTransferCalculator
        .calculateWallTemperatures(processTemperatureK, config.fireTemperatureK(),
            config.wallThicknessM(), config.thermalConductivityWPerMPerK(),
            config.wettedInternalFilmCoefficientWPerM2K(), config.externalFilmCoefficientWPerM2K());

    FireHeatTransferCalculator.SurfaceTemperatureResult unwettedWall =
        FireHeatTransferCalculator.calculateWallTemperatures(processTemperatureK,
            config.fireTemperatureK(), config.wallThicknessM(),
            config.thermalConductivityWPerMPerK(), config.unwettedInternalFilmCoefficientWPerM2K(),
            config.externalFilmCoefficientWPerM2K());

    double radiativeHeatFlux =
        FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(config.emissivity(),
            config.viewFactor(), config.fireTemperatureK(), unwettedWall.outerWallTemperatureK());

    double unwettedRadiativeHeat = radiativeHeatFlux * unwettedArea;
    double flareRadiativeFlux = 0.0;
    double flareRadiativeHeat = 0.0;
    double innerArea = separator.getInnerSurfaceArea();
    if (flare != null && flareGroundDistanceM > 0.0 && innerArea > 0.0) {
      flareRadiativeFlux = flare.estimateRadiationHeatFlux(flareGroundDistanceM);
      flareRadiativeHeat = flareRadiativeFlux * innerArea;
    }

    double totalFireHeat = poolFireHeatLoad + unwettedRadiativeHeat + flareRadiativeHeat;

    double vonMises = VesselRuptureCalculator.vonMisesStress(separatorPressurePa, innerRadius,
        config.wallThicknessM());
    double ruptureMargin =
        VesselRuptureCalculator.ruptureMargin(vonMises, config.allowableTensileStrengthPa());
    boolean ruptureLikely =
        VesselRuptureCalculator.isRuptureLikely(vonMises, config.allowableTensileStrengthPa());

    return new FireExposureResult(wettedArea, unwettedArea, poolFireHeatLoad, radiativeHeatFlux,
        unwettedRadiativeHeat, flareRadiativeFlux, flareRadiativeHeat, totalFireHeat, wettedWall,
        unwettedWall, vonMises, ruptureMargin, ruptureLikely);
  }

  /**
   * Applies the calculated fire heat by setting the separator duty. The temperature change is then
   * handled by {@link Separator#runTransient(double, java.util.UUID)}, which consumes the heat
   * input when performing the energy balance.
   *
   * @param separator separator to update
   * @param fireState fire heat-load bundle produced by
   *        {@link #evaluate(Separator, FireScenarioConfig)}
   * @param timeStepSeconds timestep duration in seconds (retained for backward compatibility)
   * @return heat duty applied to the separator in watts
   */
  public static double applyFireHeating(Separator separator, FireExposureResult fireState,
      double timeStepSeconds) {
    Objects.requireNonNull(separator, "separator");
    Objects.requireNonNull(fireState, "fireState");

    separator.setDuty(fireState.totalFireHeat());
    return fireState.totalFireHeat();
  }
}
