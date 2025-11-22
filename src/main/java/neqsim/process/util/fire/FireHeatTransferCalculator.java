package neqsim.process.util.fire;

/**
 * Calculates metal wall temperatures for wetted and unwetted zones during fire exposure.
 *
 * <p>The model treats the vessel wall as a one-dimensional thermal resistance network and solves for
 * wall temperatures using the imposed fire temperature and process fluid temperature. It can be
 * combined with {@link FireHeatLoadCalculator} to evaluate transient blowdown cases where external
 * heat input varies over time.
 */
public final class FireHeatTransferCalculator {

  private FireHeatTransferCalculator() {}

  /**
   * Result container for surface temperature calculations.
   */
  public static final class SurfaceTemperatureResult {
    private final double heatFlux;
    private final double innerWallTemperatureK;
    private final double outerWallTemperatureK;

    /**
     * Creates a result container.
     *
     * @param heatFlux Heat flux through the wall [W/m2]
     * @param innerWallTemperatureK Inner wall temperature [K]
     * @param outerWallTemperatureK Outer wall temperature [K]
     */
    public SurfaceTemperatureResult(
        double heatFlux, double innerWallTemperatureK, double outerWallTemperatureK) {
      this.heatFlux = heatFlux;
      this.innerWallTemperatureK = innerWallTemperatureK;
      this.outerWallTemperatureK = outerWallTemperatureK;
    }

    public double heatFlux() {
      return heatFlux;
    }

    public double innerWallTemperatureK() {
      return innerWallTemperatureK;
    }

    public double outerWallTemperatureK() {
      return outerWallTemperatureK;
    }
  }

  /**
   * Calculates the heat flux and wall temperatures for a single wall section.
   *
   * <p>The solution assumes a series of resistances: external convection/radiation, wall conduction,
   * and internal convection/boiling. It is valid for both wetted and unwetted regions as long as
   * appropriate film coefficients are supplied.
   *
   * @param processFluidTemperatureK Fluid bulk temperature [K]
   * @param fireTemperatureK Incident fire temperature [K]
   * @param wallThicknessM Wall thickness [m]
   * @param thermalConductivityWPerMPerK Metal thermal conductivity [W/(m*K)]
   * @param internalFilmCoefficientWPerM2K Internal film coefficient (boiling/condensation vs. gas)
   *     [W/(m2*K)]
   * @param externalFilmCoefficientWPerM2K External film coefficient (radiation/impingement) [W/(m2*
   *     K)]
   * @return Heat flux and wall temperatures for the section
   */
  public static SurfaceTemperatureResult calculateWallTemperatures(
      double processFluidTemperatureK,
      double fireTemperatureK,
      double wallThicknessM,
      double thermalConductivityWPerMPerK,
      double internalFilmCoefficientWPerM2K,
      double externalFilmCoefficientWPerM2K) {

    if (processFluidTemperatureK <= 0.0 || fireTemperatureK <= 0.0) {
      throw new IllegalArgumentException("Temperatures must be positive Kelvin values");
    }
    if (wallThicknessM <= 0.0 || thermalConductivityWPerMPerK <= 0.0) {
      throw new IllegalArgumentException("Wall properties must be positive");
    }
    if (internalFilmCoefficientWPerM2K <= 0.0 || externalFilmCoefficientWPerM2K <= 0.0) {
      throw new IllegalArgumentException("Film coefficients must be positive");
    }

    double totalResistance =
        1.0 / externalFilmCoefficientWPerM2K
            + wallThicknessM / thermalConductivityWPerMPerK
            + 1.0 / internalFilmCoefficientWPerM2K;

    double heatFlux = (fireTemperatureK - processFluidTemperatureK) / totalResistance;
    double innerWallTemperatureK = processFluidTemperatureK + heatFlux / internalFilmCoefficientWPerM2K;
    double outerWallTemperatureK = fireTemperatureK - heatFlux / externalFilmCoefficientWPerM2K;

    return new SurfaceTemperatureResult(heatFlux, innerWallTemperatureK, outerWallTemperatureK);
  }
}
