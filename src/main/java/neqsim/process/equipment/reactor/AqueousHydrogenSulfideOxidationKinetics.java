package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Primary-source screening correlation for abiotic oxidation of total dissolved sulfide by
 * air-saturated oxygen.
 *
 * <p>
 * Millero et al. measured total-sulfide loss in air-saturated water, seawater, and NaCl
 * solutions. For pH 4-8, 278.15-338.15 K, and ionic strength 0-6 mol/kg water, their simplified
 * correlation is {@code log10(k) = 10.50 + 0.16 pH - 3000/T + 0.44 sqrt(I)}. The second-order
 * rate constant uses the reported kg-water mol-1 h-1 basis.
 * </p>
 *
 * <p>
 * This class does not calculate oxygen solubility, sulfide speciation, products, pressure
 * effects, or a pipeline source term. Exposure methods require the caller to supply the molality
 * of air-saturated dissolved oxygen and assume that it remains constant.
 * </p>
 *
 * @see <a href="https://doi.org/10.1021/es00159a003">Millero et al. (1987),
 *      Environmental Science &amp; Technology 21, 439-443</a>
 * @author NeqSim Team
 * @version 1.0
 */
public final class AqueousHydrogenSulfideOxidationKinetics implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Primary-source DOI. */
  public static final String SOURCE_IDENTIFIER = "doi:10.1021/es00159a003";

  /** Human-readable primary-source citation. */
  public static final String SOURCE_CITATION =
      "F. J. Millero, S. Hubinger, M. Fernandez and S. Garnett, Environmental Science & Technology 21 (1987) 439-443";

  /** Public-access and redistribution note for the implemented source material. */
  public static final String SOURCE_ACCESS_STATUS =
      "Public DOI metadata and source equation; no tabulated measurements redistributed";

  /** Minimum temperature in the simplified published correlation [K]. */
  public static final double MINIMUM_TEMPERATURE_K = 278.15;

  /** Maximum temperature in the simplified published correlation [K]. */
  public static final double MAXIMUM_TEMPERATURE_K = 338.15;

  /** Minimum pH in the simplified published correlation. */
  public static final double MINIMUM_PH = 4.0;

  /** Maximum pH in the simplified published correlation. */
  public static final double MAXIMUM_PH = 8.0;

  /** Minimum ionic strength in the simplified published correlation [mol/kg water]. */
  public static final double MINIMUM_IONIC_STRENGTH_MOL_PER_KG_WATER = 0.0;

  /** Maximum ionic strength in the simplified published correlation [mol/kg water]. */
  public static final double MAXIMUM_IONIC_STRENGTH_MOL_PER_KG_WATER = 6.0;

  /** Initial total-sulfide molality reported for the experiments [mol/kg water]. */
  public static final double PUBLISHED_INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;

  /** Reported spread around the initial total-sulfide molality [mol/kg water]. */
  public static final double PUBLISHED_INITIAL_TOTAL_SULFIDE_SPREAD = 5.0e-6;

  /** Standard deviation of the simplified fit in log10(k). */
  public static final double LOG10_RATE_STANDARD_DEVIATION = 0.18;

  private static final double LOG10_INTERCEPT = 10.50;
  private static final double PH_COEFFICIENT = 0.16;
  private static final double INVERSE_TEMPERATURE_K = 3000.0;
  private static final double SQRT_IONIC_STRENGTH_COEFFICIENT = 0.44;

  private AqueousHydrogenSulfideOxidationKinetics() {
  }

  /**
   * Calculate the published second-order total-sulfide oxidation rate constant.
   *
   * @param temperatureK aqueous temperature [K]
   * @param pH aqueous pH on the source-compatible scale
   * @param ionicStrengthMolPerKgWater ionic strength [mol/kg water]
   * @return second-order rate constant [kg water/(mol h)]
   * @throws IllegalArgumentException when an input is non-finite or outside the published range
   */
  public static double secondOrderRateConstant(double temperatureK, double pH,
      double ionicStrengthMolPerKgWater) {
    requirePublishedState(temperatureK, pH, ionicStrengthMolPerKgWater);
    double log10Rate = LOG10_INTERCEPT + PH_COEFFICIENT * pH
        - INVERSE_TEMPERATURE_K / temperatureK
        + SQRT_IONIC_STRENGTH_COEFFICIENT * Math.sqrt(ionicStrengthMolPerKgWater);
    return Math.pow(10.0, log10Rate);
  }

  /**
   * Calculate the nominal correlation and its reported one-standard-deviation fit interval.
   *
   * <p>
   * The source reports a standard deviation of 0.18 in log10(k). The returned interval therefore
   * multiplies and divides the nominal rate by {@code 10^0.18}. It represents regression scatter,
   * not a complete predictive or mechanistic uncertainty.
   * </p>
   *
   * @param temperatureK aqueous temperature [K]
   * @param pH aqueous pH on the source-compatible scale
   * @param ionicStrengthMolPerKgWater ionic strength [mol/kg water]
   * @return immutable nominal and one-standard-deviation rate interval
   * @throws IllegalArgumentException when an input is non-finite or outside the published range
   */
  public static RateConstantRange secondOrderRateConstantRange(double temperatureK, double pH,
      double ionicStrengthMolPerKgWater) {
    double nominal = secondOrderRateConstant(temperatureK, pH, ionicStrengthMolPerKgWater);
    double factor = Math.pow(10.0, LOG10_RATE_STANDARD_DEVIATION);
    return new RateConstantRange(nominal / factor, nominal, nominal * factor);
  }

  /**
   * Calculate the pseudo-first-order rate for a supplied air-saturated oxygen molality.
   *
   * @param airSaturatedOxygenMolality dissolved oxygen molality for an independently established
   *        air-saturated aqueous state [mol/kg water]
   * @param temperatureK aqueous temperature [K]
   * @param pH aqueous pH on the source-compatible scale
   * @param ionicStrengthMolPerKgWater ionic strength [mol/kg water]
   * @return pseudo-first-order total-sulfide loss rate [1/h]
   * @throws IllegalArgumentException when oxygen is not finite and positive, or the state is
   *         outside the published range
   */
  public static double pseudoFirstOrderRateConstant(double airSaturatedOxygenMolality,
      double temperatureK, double pH, double ionicStrengthMolPerKgWater) {
    requireFinitePositive(airSaturatedOxygenMolality, "air-saturated oxygen molality");
    double rate = secondOrderRateConstant(temperatureK, pH, ionicStrengthMolPerKgWater)
        * airSaturatedOxygenMolality;
    if (!Double.isFinite(rate)) {
      throw new IllegalArgumentException("pseudo-first-order rate must be finite");
    }
    return rate;
  }

  /**
   * Calculate the nominal total-sulfide half-life under constant air-saturated oxygen.
   *
   * @param airSaturatedOxygenMolality dissolved oxygen molality for an independently established
   *        air-saturated aqueous state [mol/kg water]
   * @param temperatureK aqueous temperature [K]
   * @param pH aqueous pH on the source-compatible scale
   * @param ionicStrengthMolPerKgWater ionic strength [mol/kg water]
   * @return nominal total-sulfide half-life [h]
   * @throws IllegalArgumentException when oxygen is not finite and positive, or the state is
   *         outside the published range
   */
  public static double halfLifeHours(double airSaturatedOxygenMolality, double temperatureK,
      double pH, double ionicStrengthMolPerKgWater) {
    return Math.log(2.0) / pseudoFirstOrderRateConstant(airSaturatedOxygenMolality,
        temperatureK, pH, ionicStrengthMolPerKgWater);
  }

  /**
   * Screen total-sulfide loss for an exposure with constant air-saturated dissolved oxygen.
   *
   * <p>
   * The remaining fraction is the exact pseudo-first-order result {@code exp(-k[O2]t)}. No
   * reaction products or oxygen consumption are calculated.
   * </p>
   *
   * @param airSaturatedOxygenMolality dissolved oxygen molality for an independently established
   *        air-saturated aqueous state [mol/kg water]
   * @param elapsedTimeHours elapsed exposure time [h]
   * @param temperatureK aqueous temperature [K]
   * @param pH aqueous pH on the source-compatible scale
   * @param ionicStrengthMolPerKgWater ionic strength [mol/kg water]
   * @return immutable exposure-screening result
   * @throws IllegalArgumentException when elapsed time is negative or non-finite, oxygen is not
   *         finite and positive, or the state is outside the published range
   */
  public static ScreeningResult screenAirSaturatedExposure(double airSaturatedOxygenMolality,
      double elapsedTimeHours, double temperatureK, double pH,
      double ionicStrengthMolPerKgWater) {
    requireFiniteNonNegative(elapsedTimeHours, "elapsed time");
    double secondOrderRate =
        secondOrderRateConstant(temperatureK, pH, ionicStrengthMolPerKgWater);
    double pseudoFirstOrderRate =
        pseudoFirstOrderRateConstant(airSaturatedOxygenMolality, temperatureK, pH,
            ionicStrengthMolPerKgWater);
    double exposure = pseudoFirstOrderRate * elapsedTimeHours;
    if (!Double.isFinite(exposure)) {
      throw new IllegalArgumentException("kinetic exposure must be finite");
    }
    double remainingFraction = Math.exp(-exposure);
    double reactedFraction = Math.max(0.0, Math.min(1.0, -Math.expm1(-exposure)));
    return new ScreeningResult(secondOrderRate, pseudoFirstOrderRate,
        Math.log(2.0) / pseudoFirstOrderRate, exposure, remainingFraction, reactedFraction);
  }

  private static void requirePublishedState(double temperatureK, double pH,
      double ionicStrengthMolPerKgWater) {
    requireRange(temperatureK, MINIMUM_TEMPERATURE_K, MAXIMUM_TEMPERATURE_K, "temperature");
    requireRange(pH, MINIMUM_PH, MAXIMUM_PH, "pH");
    requireRange(ionicStrengthMolPerKgWater, MINIMUM_IONIC_STRENGTH_MOL_PER_KG_WATER,
        MAXIMUM_IONIC_STRENGTH_MOL_PER_KG_WATER, "ionic strength");
  }

  private static void requireRange(double value, double minimum, double maximum, String name) {
    if (!Double.isFinite(value) || value < minimum || value > maximum) {
      throw new IllegalArgumentException(
          name + " must be within the published range of " + minimum + " to " + maximum);
    }
  }

  private static void requireFinitePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFiniteNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /** Immutable second-order rate-constant interval. */
  public static final class RateConstantRange implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double lower;
    private final double nominal;
    private final double upper;

    private RateConstantRange(double lower, double nominal, double upper) {
      this.lower = lower;
      this.nominal = nominal;
      this.upper = upper;
    }

    /** @return lower one-standard-deviation rate [kg water/(mol h)]. */
    public double getLower() {
      return lower;
    }

    /** @return nominal rate [kg water/(mol h)]. */
    public double getNominal() {
      return nominal;
    }

    /** @return upper one-standard-deviation rate [kg water/(mol h)]. */
    public double getUpper() {
      return upper;
    }
  }

  /** Immutable constant-oxygen exposure result. */
  public static final class ScreeningResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double secondOrderRateConstant;
    private final double pseudoFirstOrderRateConstant;
    private final double halfLifeHours;
    private final double exposure;
    private final double remainingFraction;
    private final double reactedFraction;

    private ScreeningResult(double secondOrderRateConstant, double pseudoFirstOrderRateConstant,
        double halfLifeHours, double exposure, double remainingFraction, double reactedFraction) {
      this.secondOrderRateConstant = secondOrderRateConstant;
      this.pseudoFirstOrderRateConstant = pseudoFirstOrderRateConstant;
      this.halfLifeHours = halfLifeHours;
      this.exposure = exposure;
      this.remainingFraction = remainingFraction;
      this.reactedFraction = reactedFraction;
    }

    /** @return second-order rate constant [kg water/(mol h)]. */
    public double getSecondOrderRateConstant() {
      return secondOrderRateConstant;
    }

    /** @return pseudo-first-order rate constant [1/h]. */
    public double getPseudoFirstOrderRateConstant() {
      return pseudoFirstOrderRateConstant;
    }

    /** @return nominal total-sulfide half-life [h]. */
    public double getHalfLifeHours() {
      return halfLifeHours;
    }

    /** @return dimensionless pseudo-first-order exposure {@code k[O2]t}. */
    public double getExposure() {
      return exposure;
    }

    /** @return total-sulfide fraction remaining in the interval [0, 1]. */
    public double getRemainingFraction() {
      return remainingFraction;
    }

    /** @return total-sulfide fraction lost in the interval [0, 1]. */
    public double getReactedFraction() {
      return reactedFraction;
    }
  }
}
