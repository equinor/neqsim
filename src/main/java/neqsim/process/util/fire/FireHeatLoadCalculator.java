package neqsim.process.util.fire;

/**
 * Utility methods for estimating fire heat loads for blowdown and relief calculations.
 *
 * <p>The calculator supports the legacy API 521 pool fire correlation as well as a generalized
 * Stefan-Boltzmann formulation that can account for configuration factors. The values are expressed
 * in SI units to make them easy to integrate with the rest of the NeqSim process equipment model.
 */
public final class FireHeatLoadCalculator {

  private FireHeatLoadCalculator() {}

  /** Stefan-Boltzmann constant in W/(m^2*K^4). */
  public static final double STEFAN_BOLTZMANN = 5.670374419e-8;

  /**
   * Calculates total heat input from a pool fire using the API 521 correlation.
   *
   * <p>The API 521 pool-fire heat load (Eq. 5.15) is expressed as:
   *
   * <p>Q = 6.19e6 * F * A^0.82 [W]
   *
   * <p>where F is the environmental factor and A is the wetted surface area [m2]. This metric form
   * is derived from 21,000,000 * F * A^0.82 [Btu/hr].
   *
   * @param wettedArea Wetted surface area exposed to fire [m2]
   * @param environmentalFactor API 521 environmental factor, unitless (e.g., insulation quality)
   * @return Heat input from pool fire in Watts
   */
  public static double api521PoolFireHeatLoad(double wettedArea, double environmentalFactor) {
    if (wettedArea <= 0.0) {
      throw new IllegalArgumentException("Wetted area must be positive");
    }
    if (environmentalFactor <= 0.0) {
      throw new IllegalArgumentException("Environmental factor must be positive");
    }
    return 6.19e6 * environmentalFactor * Math.pow(wettedArea, 0.82);
  }

  /**
   * Calculates radiative heat flux using a generalized Stefan-Boltzmann approach.
   *
   * <p>This method supports modern fire heat load calculations that use configuration/view factors
   * instead of a pure point-source assumption.
   *
   * @param emissivity Effective flame emissivity (0-1)
   * @param viewFactor Geometric view/configuration factor between flame and surface (0-1)
   * @param flameTemperatureK Flame or source temperature [K]
   * @param surfaceTemperatureK Current surface temperature [K]
   * @return Radiative heat flux toward the surface in W/m2
   */
  public static double generalizedStefanBoltzmannHeatFlux(
      double emissivity,
      double viewFactor,
      double flameTemperatureK,
      double surfaceTemperatureK) {
    if (emissivity < 0.0 || emissivity > 1.0) {
      throw new IllegalArgumentException("Emissivity must be between 0 and 1");
    }
    if (viewFactor < 0.0 || viewFactor > 1.0) {
      throw new IllegalArgumentException("View factor must be between 0 and 1");
    }
    if (flameTemperatureK <= 0.0 || surfaceTemperatureK <= 0.0) {
      throw new IllegalArgumentException("Temperatures must be positive Kelvin values");
    }

    return emissivity
        * viewFactor
        * STEFAN_BOLTZMANN
        * (Math.pow(flameTemperatureK, 4) - Math.pow(surfaceTemperatureK, 4));
  }
}
