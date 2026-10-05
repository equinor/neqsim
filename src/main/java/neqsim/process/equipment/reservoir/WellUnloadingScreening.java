package neqsim.process.equipment.reservoir;

/**
 * Screening helper for starting a liquid-loaded (drowned) well.
 *
 * <p>
 * A well whose tubing holds brine or other kill fluid is lifted by the reservoir only while the wellhead pressure is
 * below the static pressure the reservoir can support. This helper provides the three first-pass relations used to
 * judge a blow-down or flare-down procedure: the static wellhead pressure for a given gas-cap depth, the choke-limited
 * liquid rate when a separator is pulled down, and the liquid share of the column read from a downhole gauge. It is
 * pure hydrostatic and valve arithmetic with no thermodynamics, no friction and no transient wellbore effects, and it
 * uses a single mean gas density for the gas cap.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class WellUnloadingScreening {
  /** Gravitational acceleration [m/s2]. */
  private static final double GRAVITY = 9.80665;
  /** Pascal per bar. */
  private static final double PA_PER_BAR = 1.0e5;
  /** Conversion from US customary Cv to metric Kv. */
  private static final double KV_PER_CV = 0.865;

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private WellUnloadingScreening() {
  }

  /**
   * Converts a US customary flow coefficient to the metric flow coefficient.
   *
   * @param cv flow coefficient Cv [US gpm per psi^0.5]
   * @return flow coefficient Kv [m3/h per bar^0.5]
   */
  public static double cvToKv(double cv) {
    return KV_PER_CV * cv;
  }

  /**
   * Static wellhead pressure the reservoir supports with a gas cap above a liquid column.
   *
   * @param reservoirPressureBara reservoir pressure at the datum depth [bara]
   * @param datumTvdM true vertical depth of the reservoir datum [m]
   * @param liquidDensityKgM3 density of the liquid column [kg/m3]
   * @param gasDensityKgM3 mean density of the gas cap [kg/m3]
   * @param gasCapTvdM true vertical depth of the gas-liquid interface below the wellhead [m], 0 for a liquid-full
   * tubing and the datum depth for a gas-filled tubing
   * @return static wellhead pressure [bara], never negative
   * @throws IllegalArgumentException if a depth or density is negative, or the gas cap is deeper than the datum
   */
  public static double staticWellheadPressure(double reservoirPressureBara, double datumTvdM, double liquidDensityKgM3,
      double gasDensityKgM3, double gasCapTvdM) {
    if (datumTvdM < 0.0 || gasCapTvdM < 0.0 || liquidDensityKgM3 < 0.0 || gasDensityKgM3 < 0.0) {
      throw new IllegalArgumentException("depths and densities must be non-negative");
    }
    if (gasCapTvdM > datumTvdM) {
      throw new IllegalArgumentException("gas cap cannot be deeper than the datum");
    }
    double head = (liquidDensityKgM3 * (datumTvdM - gasCapTvdM) + gasDensityKgM3 * gasCapTvdM) * GRAVITY / PA_PER_BAR;
    return Math.max(0.0, reservoirPressureBara - head);
  }

  /**
   * Steady liquid rate of a liquid-full well pulled down to a separator through a choke.
   *
   * <p>
   * The pressure the reservoir has available above the separator is shared between the choke ({@code SG (q/Kv)^2}) and
   * the reservoir drawdown ({@code 24 q / PI}); the resulting quadratic is solved for the rate.
   * </p>
   *
   * @param reservoirPressureBara reservoir pressure at the datum depth [bara]
   * @param datumTvdM true vertical depth of the reservoir datum [m]
   * @param liquidDensityKgM3 density of the liquid column [kg/m3]
   * @param separatorPressureBara pressure downstream of the choke [bara]
   * @param kv metric flow coefficient of the choke at its opening [m3/h per bar^0.5], see {@link #cvToKv(double)}
   * @param productivityIndexM3DayBar productivity index [m3/d per bar]
   * @return liquid rate [m3/h], zero when the reservoir cannot lift the column or the choke is closed
   * @throws IllegalArgumentException if the productivity index is not positive
   */
  public static double chokeLimitedLiquidRate(double reservoirPressureBara, double datumTvdM, double liquidDensityKgM3,
      double separatorPressureBara, double kv, double productivityIndexM3DayBar) {
    if (productivityIndexM3DayBar <= 0.0) {
      throw new IllegalArgumentException("productivity index must be positive");
    }
    double available = reservoirPressureBara - liquidDensityKgM3 * GRAVITY * datumTvdM / PA_PER_BAR
        - separatorPressureBara;
    if (available <= 0.0 || kv <= 0.0) {
      return 0.0;
    }
    double a = (liquidDensityKgM3 / 1000.0) / (kv * kv);
    double b = 24.0 / productivityIndexM3DayBar;
    return (-b + Math.sqrt(b * b + 4.0 * a * available)) / (2.0 * a);
  }

  /**
   * Time to displace a liquid volume at a steady rate.
   *
   * @param volumeM3 liquid volume to displace [m3]
   * @param rateM3PerHour liquid rate [m3/h]
   * @return time [h], positive infinity when the rate is not positive
   */
  public static double unloadingTimeHours(double volumeM3, double rateM3PerHour) {
    return rateM3PerHour > 0.0 ? volumeM3 / rateM3PerHour : Double.POSITIVE_INFINITY;
  }

  /**
   * Liquid share of the column above a downhole gauge, from the gauge and wellhead pressures.
   *
   * @param gaugePressureBara downhole gauge pressure [bara]
   * @param wellheadPressureBara wellhead pressure [bara]
   * @param gaugeTvdM true vertical depth of the gauge [m]
   * @param liquidDensityKgM3 density of the liquid [kg/m3]
   * @param gasDensityKgM3 mean density of the gas [kg/m3]
   * @return liquid share between 0 (gas-filled) and 1 (liquid-full), clipped to that range
   * @throws IllegalArgumentException if the gauge depth is not positive or the densities are equal
   */
  public static double liquidShare(double gaugePressureBara, double wellheadPressureBara, double gaugeTvdM,
      double liquidDensityKgM3, double gasDensityKgM3) {
    if (gaugeTvdM <= 0.0 || liquidDensityKgM3 == gasDensityKgM3) {
      throw new IllegalArgumentException("gauge depth must be positive and the densities must differ");
    }
    double mean = (gaugePressureBara - wellheadPressureBara) * PA_PER_BAR / (GRAVITY * gaugeTvdM);
    double share = (mean - gasDensityKgM3) / (liquidDensityKgM3 - gasDensityKgM3);
    return Math.max(0.0, Math.min(1.0, share));
  }
}
