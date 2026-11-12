package neqsim.thermo.util.steam;

/**
 * Salinity corrections for the density and viscosity of an aqueous phase, after Batzle and Wang (1992).
 *
 * <p>
 * The correlations of Batzle, M. and Wang, Z., "Seismic properties of pore fluids", Geophysics 57(11), 1396-1408
 * (1992), Eqs. 27a, 27b and 32, treat the salt as NaCl. They are applied here as ratios brine/fresh water evaluated
 * with the same correlation, so that the correction can be multiplied onto any fresh-water value (an equation of state
 * or a reference equation) without inheriting the absolute error of the correlation. The published range is up to 350
 * C, 100 MPa and a NaCl mass fraction of about 0.32.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class BrineSalinityCorrection {
  /** Highest accepted salt mass fraction [kg/kg solution]. */
  public static final double MAX_MASS_FRACTION = 0.35;

  /** Prevents instantiation. */
  private BrineSalinityCorrection() {
  }

  /**
   * Validates a salt mass fraction.
   *
   * @param salinity salt mass fraction [kg/kg solution]
   * @throws IllegalArgumentException if salinity is non-finite or outside 0 to {@link #MAX_MASS_FRACTION}
   */
  private static void validate(double salinity) {
    if (!Double.isFinite(salinity) || salinity < 0.0 || salinity > MAX_MASS_FRACTION) {
      throw new IllegalArgumentException("Salt mass fraction must be within 0-" + MAX_MASS_FRACTION + " kg/kg");
    }
  }

  /**
   * Batzle-Wang fresh water density, Eq. 27a.
   *
   * @param t temperature [C]
   * @param p pressure [MPa]
   * @return density [g/cm3]
   */
  private static double freshDensity(double t, double p) {
    return 1.0 + 1.0e-6 * (-80.0 * t - 3.3 * t * t + 0.00175 * t * t * t + 489.0 * p - 2.0 * t * p + 0.016 * t * t * p
        - 1.3e-5 * t * t * t * p - 0.333 * p * p - 0.002 * t * p * p);
  }

  /**
   * Batzle-Wang brine density, Eq. 27b.
   *
   * @param t temperature [C]
   * @param p pressure [MPa]
   * @param s NaCl mass fraction
   * @return density [g/cm3]
   */
  private static double brineDensity(double t, double p, double s) {
    return freshDensity(t, p) + s * (0.668 + 0.44 * s
        + 1.0e-6 * (300.0 * p - 2400.0 * p * s + t * (80.0 + 3.0 * t - 3300.0 * s - 13.0 * p + 47.0 * p * s)));
  }

  /**
   * Batzle-Wang brine viscosity, Eq. 32.
   *
   * @param t temperature [C]
   * @param s NaCl mass fraction
   * @return viscosity [cP]
   */
  private static double brineViscosity(double t, double s) {
    double tc = Math.max(t, 0.0);
    double shape = 0.42 * Math.pow(Math.pow(s, 0.8) - 0.17, 2.0) + 0.045;
    return 0.1 + 0.333 * s + (1.65 + 91.9 * s * s * s) * Math.exp(-shape * Math.pow(tc, 0.8));
  }

  /**
   * Calculates the ratio of brine density to fresh-water density.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity salt mass fraction [kg/kg solution]
   * @return density ratio [-]
   * @throws IllegalArgumentException if salinity is out of range
   */
  public static double densityRatio(double temperature, double pressure, double salinity) {
    validate(salinity);
    double t = temperature - 273.15;
    double p = pressure / 10.0;
    return brineDensity(t, p, salinity) / freshDensity(t, p);
  }

  /**
   * Calculates the pressure derivative of {@link #densityRatio(double, double, double)}.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity salt mass fraction [kg/kg solution]
   * @return derivative [1/Pa]
   * @throws IllegalArgumentException if salinity is out of range
   */
  public static double densityRatioPressureDerivative(double temperature, double pressure, double salinity) {
    double step = 0.01;
    return (densityRatio(temperature, pressure + step, salinity) - densityRatio(temperature, pressure - step, salinity))
        / (2.0 * step * 1.0e5);
  }

  /**
   * Calculates the temperature derivative of {@link #densityRatio(double, double, double)}.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity salt mass fraction [kg/kg solution]
   * @return derivative [1/K]
   * @throws IllegalArgumentException if salinity is out of range
   */
  public static double densityRatioTemperatureDerivative(double temperature, double pressure, double salinity) {
    double step = 0.01;
    return (densityRatio(temperature + step, pressure, salinity) - densityRatio(temperature - step, pressure, salinity))
        / (2.0 * step);
  }

  /**
   * Calculates the ratio of brine viscosity to fresh-water viscosity. The correlation has no pressure dependence and is
   * held at its 0 C value below the freezing point of fresh water.
   *
   * @param temperature temperature [K]
   * @param salinity salt mass fraction [kg/kg solution]
   * @return viscosity ratio [-]
   * @throws IllegalArgumentException if salinity is out of range
   */
  public static double viscosityRatio(double temperature, double salinity) {
    validate(salinity);
    double t = temperature - 273.15;
    return brineViscosity(t, salinity) / brineViscosity(t, 0.0);
  }

  /**
   * Converts a salt mass concentration to a mass fraction using the brine density at the given state.
   *
   * @param concentration salt mass concentration [mg/l of solution]
   * @param temperature temperature at which the concentration is defined [K]
   * @param pressure pressure at which the concentration is defined [bara]
   * @return salt mass fraction [kg/kg solution]
   * @throws IllegalArgumentException if the concentration is negative or gives an out-of-range mass fraction
   */
  public static double massFractionFromConcentration(double concentration, double temperature, double pressure) {
    if (!Double.isFinite(concentration) || concentration < 0.0) {
      throw new IllegalArgumentException("Salt concentration must be a finite, non-negative value in mg/l");
    }
    double t = temperature - 273.15;
    double p = pressure / 10.0;
    double gramsPerCm3 = concentration * 1.0e-6;
    double salinity = gramsPerCm3 / freshDensity(t, p);
    for (int i = 0; i < 50; i++) {
      double next = gramsPerCm3 / brineDensity(t, p, salinity);
      if (Math.abs(next - salinity) < 1.0e-12) {
        salinity = next;
        break;
      }
      salinity = next;
    }
    validate(salinity);
    return salinity;
  }
}
