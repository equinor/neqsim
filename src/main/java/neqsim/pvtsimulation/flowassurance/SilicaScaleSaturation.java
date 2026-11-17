package neqsim.pvtsimulation.flowassurance;

import java.util.Objects;

/**
 * Activity-based saturation for the dissolution of pure silica polymorphs, SiO2(s) + 2 H2O = H4SiO4(aq).
 *
 * <p>
 * The activity inputs come from a separately qualified aqueous speciation model. Total dissolved silicon is not the
 * activity of neutral orthosilicic acid. The calculation does not model precipitation quantity, rate or deposition.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SilicaScaleSaturation {
  /** Reference temperature for the tabulated standard equilibria, K. */
  private static final double REFERENCE_TEMPERATURE_K = 298.15;

  /** PHREEQC gas constant used for Van't Hoff conversion, J/(mol K). */
  private static final double PHREEQC_GAS_CONSTANT = 8.3147;

  /** Silica phases with the same dissolution stoichiometry and different equilibrium constants. */
  public enum Mineral {
    /** Amorphous silica, PHREEQC phase SiO2(a). */
    AMORPHOUS(-2.71, 3.340),
    /** Chalcedony. */
    CHALCEDONY(-3.55, 4.720),
    /** Quartz. */
    QUARTZ(-3.98, 5.990);

    /** Decimal logarithm of the dissolution constant at 25 degrees Celsius. */
    private final double referenceLogK;
    /** Enthalpy of dissolution, kcal/mol. */
    private final double dissolutionEnthalpyKcalPerMol;

    /**
     * Creates a phase with its PHREEQC 25 degree Celsius parameters.
     *
     * @param referenceLogK decimal logarithm of the dissolution constant
     * @param dissolutionEnthalpyKcalPerMol dissolution enthalpy, kcal/mol
     */
    Mineral(double referenceLogK, double dissolutionEnthalpyKcalPerMol) {
      this.referenceLogK = referenceLogK;
      this.dissolutionEnthalpyKcalPerMol = dissolutionEnthalpyKcalPerMol;
    }
  }

  /** Utility class. */
  private SilicaScaleSaturation() {
  }

  /**
   * Evaluates the saturation index at the supplied activity and mineral equilibrium conditions.
   *
   * @param log10SilicicAcidActivity decimal logarithm of molality-standard-state H4SiO4 activity, or negative infinity
   * when dissolved silica is absent
   * @param log10WaterActivity decimal logarithm of positive solvent water activity
   * @param log10DissolutionConstant decimal logarithm of the mineral-specific dissolution constant at the same
   * temperature and pressure as the activities
   * @return decimal-log saturation index, or negative infinity when silica is absent
   * @throws IllegalArgumentException if an activity logarithm or the equilibrium constant is invalid
   */
  public static double saturationIndex(double log10SilicicAcidActivity, double log10WaterActivity,
      double log10DissolutionConstant) {
    if (!(Double.isFinite(log10SilicicAcidActivity) || log10SilicicAcidActivity == Double.NEGATIVE_INFINITY)
        || !Double.isFinite(log10WaterActivity) || !Double.isFinite(log10DissolutionConstant)) {
      throw new IllegalArgumentException("Silica saturation requires valid log activities and a finite log K");
    }
    return log10SilicicAcidActivity - 2.0 * log10WaterActivity - log10DissolutionConstant;
  }

  /**
   * Evaluates the silica dissolution constant at 1 atm using the PHREEQC 3.7.3 Pitzer database.
   *
   * <p>
   * This screening reference is restricted to 25 to 100 degrees Celsius. Use a qualified external equilibrium constant
   * for elevated-pressure work. The database analytical expression takes precedence over the tabulated reference
   * constant for amorphous silica.
   * </p>
   *
   * @param mineral silica polymorph
   * @param temperatureK temperature, K
   * @return decimal logarithm of the dissolution constant at 1 atm
   * @throws IllegalArgumentException if temperature is outside 298.15 to 373.15 K
   * @throws NullPointerException if the mineral is absent
   */
  public static double log10DissolutionConstantAtOneAtmosphere(Mineral mineral, double temperatureK) {
    Objects.requireNonNull(mineral, "mineral");
    if (!(temperatureK >= REFERENCE_TEMPERATURE_K && temperatureK <= 373.15)) {
      throw new IllegalArgumentException("Silica reference temperature must be between 298.15 and 373.15 K");
    }
    if (mineral == Mineral.AMORPHOUS) {
      return 20.42 + 0.003107 * temperatureK - 1492.0 / temperatureK - 7.68 * Math.log10(temperatureK);
    }
    double enthalpyJPerMol = 4184.0 * mineral.dissolutionEnthalpyKcalPerMol;
    return mineral.referenceLogK + enthalpyJPerMol / (Math.log(10.0) * PHREEQC_GAS_CONSTANT)
        * (1.0 / REFERENCE_TEMPERATURE_K - 1.0 / temperatureK);
  }
}
