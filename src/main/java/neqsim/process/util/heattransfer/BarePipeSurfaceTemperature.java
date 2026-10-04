package neqsim.process.util.heattransfer;

/**
 * Steady, local heat balance for an uninsulated horizontal circular pipe in outdoor air.
 *
 * <p>
 * The caller supplies the local bulk-fluid temperature and the fluid-side film coefficient. Air properties are
 * estimated at the air/surface film temperature at approximately atmospheric pressure. Wind is perpendicular to the
 * pipe. Radiation sees surroundings at the air temperature. The higher of the Churchill-Bernstein crossflow and
 * Churchill-Chu horizontal-cylinder natural convection coefficients is used; mixed convection and solar radiation are
 * not modelled. Conductive resistance is cylindrical, and the nonlinear surface balance is bracketed between bulk-fluid
 * and ambient temperatures. This is a local steady screening calculation, not a prediction of the axial fluid
 * temperature or a certified touch-temperature assessment.
 */
public final class BarePipeSurfaceTemperature {
  private static final double SIGMA = 5.670374419e-8;
  private static final double TWO_PI = 2.0 * Math.PI;

  private BarePipeSurfaceTemperature() {
  }

  /** Immutable local heat-balance result, with positive heat loss directed out of the fluid. */
  public static final class Result {
    private final double outerTemperatureK;
    private final double innerTemperatureK;
    private final double heatLossWPerM;
    private final double convectionWPerM;
    private final double radiationWPerM;
    private final double convectionCoefficientWPerM2K;

    private Result(double outerTemperatureK, double innerTemperatureK, double heatLossWPerM, double convectionWPerM,
        double radiationWPerM, double convectionCoefficientWPerM2K) {
      this.outerTemperatureK = outerTemperatureK;
      this.innerTemperatureK = innerTemperatureK;
      this.heatLossWPerM = heatLossWPerM;
      this.convectionWPerM = convectionWPerM;
      this.radiationWPerM = radiationWPerM;
      this.convectionCoefficientWPerM2K = convectionCoefficientWPerM2K;
    }

    public double getOuterTemperatureK() {
      return outerTemperatureK;
    }

    public double getInnerTemperatureK() {
      return innerTemperatureK;
    }

    public double getHeatLossWPerM() {
      return heatLossWPerM;
    }

    public double getConvectionWPerM() {
      return convectionWPerM;
    }

    public double getRadiationWPerM() {
      return radiationWPerM;
    }

    public double getConvectionCoefficientWPerM2K() {
      return convectionCoefficientWPerM2K;
    }
  }

  /**
   * Solves the local fluid/steel/air thermal circuit per metre of pipe.
   *
   * @param fluidTemperatureK local bulk-fluid temperature, K
   * @param ambientTemperatureK ambient air and radiative-surroundings temperature, K
   * @param innerDiameterM actual pipe bore, m (DN is not an inner diameter)
   * @param wallThicknessM steel wall thickness, m
   * @param wallConductivityWPerMK steel thermal conductivity, W/(m K)
   * @param innerFilmWPerM2K fluid-side coefficient referenced to bore area, W/(m2 K)
   * @param windSpeedMPerS crosswind speed, m/s; zero selects natural convection
   * @param emissivity surface hemispherical emissivity, between zero and one
   * @return solved outer and inner surface temperatures and heat-loss components
   */
  public static Result calculate(double fluidTemperatureK, double ambientTemperatureK, double innerDiameterM,
      double wallThicknessM, double wallConductivityWPerMK, double innerFilmWPerM2K, double windSpeedMPerS,
      double emissivity) {
    positive(fluidTemperatureK, "fluidTemperatureK");
    positive(ambientTemperatureK, "ambientTemperatureK");
    positive(innerDiameterM, "innerDiameterM");
    positive(wallThicknessM, "wallThicknessM");
    positive(wallConductivityWPerMK, "wallConductivityWPerMK");
    positive(innerFilmWPerM2K, "innerFilmWPerM2K");
    if (!Double.isFinite(windSpeedMPerS) || windSpeedMPerS < 0.0 || !Double.isFinite(emissivity) || emissivity < 0.0
        || emissivity > 1.0) {
      throw new IllegalArgumentException("Wind must be non-negative and emissivity between 0 and 1");
    }

    double ri = innerDiameterM / 2.0;
    double ro = ri + wallThicknessM;
    double resistance = 1.0 / (TWO_PI * ri * innerFilmWPerM2K) + Math.log(ro / ri) / (TWO_PI * wallConductivityWPerMK);
    double lower = Math.min(fluidTemperatureK, ambientTemperatureK);
    double upper = Math.max(fluidTemperatureK, ambientTemperatureK);
    for (int i = 0; i < 90; i++) {
      double surface = (lower + upper) / 2.0;
      double external = externalFlux(surface, ambientTemperatureK, ro, windSpeedMPerS, emissivity);
      double residual = (fluidTemperatureK - surface) / resistance - external;
      if (residual > 0.0) {
        lower = surface;
      } else {
        upper = surface;
      }
    }
    double surface = (lower + upper) / 2.0;
    double h = convectionCoefficient(surface, ambientTemperatureK, 2.0 * ro, windSpeedMPerS);
    double convection = TWO_PI * ro * h * (surface - ambientTemperatureK);
    double radiation = TWO_PI * ro * emissivity * SIGMA * (Math.pow(surface, 4) - Math.pow(ambientTemperatureK, 4));
    double heatLoss = convection + radiation;
    double inner = fluidTemperatureK - heatLoss / (TWO_PI * ri * innerFilmWPerM2K);
    return new Result(surface, inner, heatLoss, convection, radiation, h);
  }

  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static double externalFlux(double surface, double ambient, double radius, double wind, double emissivity) {
    double h = convectionCoefficient(surface, ambient, 2.0 * radius, wind);
    return TWO_PI * radius
        * (h * (surface - ambient) + emissivity * SIGMA * (Math.pow(surface, 4) - Math.pow(ambient, 4)));
  }

  private static double convectionCoefficient(double surface, double ambient, double diameter, double wind) {
    double film = (surface + ambient) / 2.0;
    double density = 1.225 * 293.15 / film;
    double viscosity = 1.81e-5 * Math.pow(film / 293.15, 0.7);
    double conductivity = 0.026 * Math.pow(film / 293.15, 0.8);
    double pr = viscosity * 1005.0 / conductivity;
    double reynolds = density * wind * diameter / viscosity;
    double nuForced = 0.3
        + 0.62 * Math.sqrt(reynolds) * Math.cbrt(pr) / Math.pow(1.0 + Math.pow(0.4 / pr, 2.0 / 3.0), 0.25)
            * Math.pow(1.0 + Math.pow(reynolds / 282000.0, 5.0 / 8.0), 4.0 / 5.0);
    double nu = viscosity / density;
    double alpha = conductivity / (density * 1005.0);
    double rayleigh = 9.80665 * Math.abs(surface - ambient) * Math.pow(diameter, 3) / (film * nu * alpha);
    double nuNatural = Math.pow(
        0.60 + 0.387 * Math.pow(rayleigh, 1.0 / 6.0) / Math.pow(1.0 + Math.pow(0.559 / pr, 9.0 / 16.0), 8.0 / 27.0),
        2.0);
    return Math.max(nuNatural, wind > 0.0 ? nuForced : 0.0) * conductivity / diameter;
  }
}
