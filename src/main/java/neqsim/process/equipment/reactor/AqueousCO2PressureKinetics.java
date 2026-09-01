package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Published pressure multipliers for aqueous carbon-dioxide hydration and carbonic-acid dehydration.
 *
 * <p>
 * van Eldik and Palmer measured activation volumes for {@code CO2(aq) + H2O -> H2CO3} and
 * {@code H2CO3 -> CO2(aq) + H2O} at 25 degrees Celsius, ionic strength 0.5, and pressures up to 1 kbar. This class
 * evaluates only the pressure response implied by those activation volumes:
 * {@code k(P2) / k(P1) = exp(-deltaV * (P2 - P1) / (R * T))}.
 * </p>
 *
 * <p>
 * The result is a dimensionless multiplier, not an absolute high-pressure rate constant. Combining it with a rate
 * correlation from a different solution composition is a separate cross-dataset assumption and is intentionally left to
 * the caller.
 * </p>
 *
 * @see <a href="https://doi.org/10.1007/BF00649292">van Eldik and Palmer (1982), Journal of Solution Chemistry 11,
 * 339-346</a>
 * @author NeqSim Team
 * @version 1.0
 */
public final class AqueousCO2PressureKinetics implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Primary-source DOI. */
  public static final String SOURCE_IDENTIFIER = "doi:10.1007/BF00649292";

  /** Human-readable primary-source citation. */
  public static final String SOURCE_CITATION = "R. van Eldik and D. A. Palmer, Journal of Solution Chemistry 11 (1982) 339-346";

  /** Public-access and redistribution note for the implemented source material. */
  public static final String SOURCE_ACCESS_STATUS = "Public publisher abstract and DOI; activation volumes implemented without redistributing tabulated data";

  /** Independent high-pressure study retained as corroborating provenance, not a parameter source. */
  public static final String CORROBORATING_SOURCE_IDENTIFIER = "doi:10.1071/CH15271";

  /** Published experimental temperature [K]. */
  public static final double PUBLISHED_TEMPERATURE_K = 298.15;

  /** Published ionic strength. */
  public static final double PUBLISHED_IONIC_STRENGTH = 0.5;

  /** Minimum qualified pressure [bara]. */
  public static final double MINIMUM_PRESSURE_BARA = 1.0;

  /** Maximum qualified pressure [bara]. */
  public static final double MAXIMUM_PRESSURE_BARA = 1000.0;

  /** Hydration activation volume [cm3/mol]. */
  public static final double HYDRATION_ACTIVATION_VOLUME_CM3_PER_MOL = -9.9;

  /** Reported hydration activation-volume uncertainty [cm3/mol]. */
  public static final double HYDRATION_ACTIVATION_VOLUME_UNCERTAINTY_CM3_PER_MOL = 1.9;

  /** Dehydration activation volume [cm3/mol]. */
  public static final double DEHYDRATION_ACTIVATION_VOLUME_CM3_PER_MOL = 6.4;

  /** Reported dehydration activation-volume uncertainty [cm3/mol]. */
  public static final double DEHYDRATION_ACTIVATION_VOLUME_UNCERTAINTY_CM3_PER_MOL = 0.4;

  private static final double GAS_CONSTANT_J_PER_MOL_K = 8.31446261815324;
  private static final double BARA_TO_PA = 1.0e5;
  private static final double CM3_TO_M3 = 1.0e-6;
  private static final double TEMPERATURE_TOLERANCE_K = 1.0e-9;

  private AqueousCO2PressureKinetics() {
  }

  /**
   * Calculate the published pressure multiplier for aqueous CO2 hydration.
   *
   * @param targetPressureBara target pressure [bara]
   * @param referencePressureBara reference pressure of the supplied rate [bara]
   * @param temperatureK aqueous-phase temperature [K]
   * @return dimensionless {@code k(target) / k(reference)}
   */
  public static double hydrationMultiplier(double targetPressureBara, double referencePressureBara,
      double temperatureK) {
    return pressureMultiplier(HYDRATION_ACTIVATION_VOLUME_CM3_PER_MOL, targetPressureBara, referencePressureBara,
        temperatureK);
  }

  /**
   * Calculate the published pressure multiplier for carbonic-acid dehydration.
   *
   * @param targetPressureBara target pressure [bara]
   * @param referencePressureBara reference pressure of the supplied rate [bara]
   * @param temperatureK aqueous-phase temperature [K]
   * @return dimensionless {@code k(target) / k(reference)}
   */
  public static double dehydrationMultiplier(double targetPressureBara, double referencePressureBara,
      double temperatureK) {
    return pressureMultiplier(DEHYDRATION_ACTIVATION_VOLUME_CM3_PER_MOL, targetPressureBara, referencePressureBara,
        temperatureK);
  }

  /**
   * Calculate the hydration multiplier and the interval implied by the reported uncertainty.
   *
   * @param targetPressureBara target pressure [bara]
   * @param referencePressureBara reference pressure of the supplied rate [bara]
   * @param temperatureK aqueous-phase temperature [K]
   * @return nominal, minimum, and maximum dimensionless multipliers
   */
  public static MultiplierRange hydrationMultiplierRange(double targetPressureBara, double referencePressureBara,
      double temperatureK) {
    return multiplierRange(HYDRATION_ACTIVATION_VOLUME_CM3_PER_MOL, HYDRATION_ACTIVATION_VOLUME_UNCERTAINTY_CM3_PER_MOL,
        targetPressureBara, referencePressureBara, temperatureK);
  }

  /**
   * Calculate the dehydration multiplier and the interval implied by the reported uncertainty.
   *
   * @param targetPressureBara target pressure [bara]
   * @param referencePressureBara reference pressure of the supplied rate [bara]
   * @param temperatureK aqueous-phase temperature [K]
   * @return nominal, minimum, and maximum dimensionless multipliers
   */
  public static MultiplierRange dehydrationMultiplierRange(double targetPressureBara, double referencePressureBara,
      double temperatureK) {
    return multiplierRange(DEHYDRATION_ACTIVATION_VOLUME_CM3_PER_MOL,
        DEHYDRATION_ACTIVATION_VOLUME_UNCERTAINTY_CM3_PER_MOL, targetPressureBara, referencePressureBara, temperatureK);
  }

  private static MultiplierRange multiplierRange(double activationVolumeCm3PerMol, double uncertaintyCm3PerMol,
      double targetPressureBara, double referencePressureBara, double temperatureK) {
    double nominal = pressureMultiplier(activationVolumeCm3PerMol, targetPressureBara, referencePressureBara,
        temperatureK);
    double lowerActivationVolume = pressureMultiplier(activationVolumeCm3PerMol - uncertaintyCm3PerMol,
        targetPressureBara, referencePressureBara, temperatureK);
    double upperActivationVolume = pressureMultiplier(activationVolumeCm3PerMol + uncertaintyCm3PerMol,
        targetPressureBara, referencePressureBara, temperatureK);
    return new MultiplierRange(nominal, Math.min(lowerActivationVolume, upperActivationVolume),
        Math.max(lowerActivationVolume, upperActivationVolume));
  }

  private static double pressureMultiplier(double activationVolumeCm3PerMol, double targetPressureBara,
      double referencePressureBara, double temperatureK) {
    requirePublishedState(targetPressureBara, temperatureK, "target");
    requirePublishedState(referencePressureBara, temperatureK, "reference");
    double pressureDifferencePa = (targetPressureBara - referencePressureBara) * BARA_TO_PA;
    double activationVolumeM3PerMol = activationVolumeCm3PerMol * CM3_TO_M3;
    double multiplier = Math
        .exp(-activationVolumeM3PerMol * pressureDifferencePa / (GAS_CONSTANT_J_PER_MOL_K * temperatureK));
    if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
      throw new IllegalArgumentException("pressure multiplier must be finite and positive");
    }
    return multiplier;
  }

  private static void requirePublishedState(double pressureBara, double temperatureK, String pressureRole) {
    if (!Double.isFinite(temperatureK) || Math.abs(temperatureK - PUBLISHED_TEMPERATURE_K) > TEMPERATURE_TOLERANCE_K) {
      throw new IllegalArgumentException(
          "temperature must equal the published value of " + PUBLISHED_TEMPERATURE_K + " K");
    }
    if (!Double.isFinite(pressureBara) || pressureBara < MINIMUM_PRESSURE_BARA
        || pressureBara > MAXIMUM_PRESSURE_BARA) {
      throw new IllegalArgumentException(pressureRole + " pressure must be within the published range of "
          + MINIMUM_PRESSURE_BARA + " to " + MAXIMUM_PRESSURE_BARA + " bara");
    }
  }

  /** Immutable pressure-multiplier interval. */
  public static final class MultiplierRange implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double nominal;
    private final double minimum;
    private final double maximum;

    private MultiplierRange(double nominal, double minimum, double maximum) {
      this.nominal = nominal;
      this.minimum = minimum;
      this.maximum = maximum;
    }

    /** @return nominal dimensionless multiplier */
    public double getNominal() {
      return nominal;
    }

    /** @return minimum multiplier implied by the reported activation-volume uncertainty */
    public double getMinimum() {
      return minimum;
    }

    /** @return maximum multiplier implied by the reported activation-volume uncertainty */
    public double getMaximum() {
      return maximum;
    }
  }
}
