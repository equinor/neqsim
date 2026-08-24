package neqsim.thermo.phase;

/**
 * Coherent, versioned Pitzer parameter datasets whose equation conventions and public provenance have been mapped to
 * {@link PhasePitzer}.
 *
 * <p>
 * A dataset method configures only the named species system and marks that phase as manually populated so the legacy
 * NeqSim table is not mixed into it. Additional active species remain subject to the normal fail-closed binary,
 * mixed-ion, and neutral-family coverage diagnostics.
 * </p>
 */
public final class PitzerParameterDatasets {
  /**
   * Exact source identity for the public-domain PHREEQC CO2-Na2SO4 subset.
   */
  public static final String PHREEQC_CO2_NA2SO4_ID = "usgs-phreeqc-pitzer-b0b3be767158ccc3322d2c816625cf470045e67e-co2-na2so4-v1";

  /** Reference temperature of the PHREEQC six-term functions, in K. */
  public static final double PHREEQC_REFERENCE_TEMPERATURE_K = 298.15;

  /**
   * Lower temperature of the independent CO2-Na2SO4 experimental trend validation, in K.
   *
   * <p>
   * This is not a generic extrapolation limit for every PHREEQC row.
   * </p>
   */
  public static final double CO2_NA2SO4_VALIDATION_MIN_TEMPERATURE_K = 303.15;

  /**
   * Upper temperature of both the PHREEQC CO2-CO2 row comment and independent CO2-Na2SO4 experimental validation, in K.
   */
  public static final double CO2_NA2SO4_VALIDATION_MAX_TEMPERATURE_K = 423.15;

  /** Lowest independently validated Na2SO4 molality, in mol/kg water. */
  public static final double CO2_NA2SO4_VALIDATION_MIN_SALT_MOLALITY = 1.0;

  /** Highest independently validated Na2SO4 molality, in mol/kg water. */
  public static final double CO2_NA2SO4_VALIDATION_MAX_SALT_MOLALITY = 2.0;

  /** Utility class. */
  private PitzerParameterDatasets() {
  }

  /**
   * Applies the exact public-domain PHREEQC CO2-Na2SO4 parameter subset audited at commit
   * {@code b0b3be767158ccc3322d2c816625cf470045e67e}, database blob {@code 324f852784be84650b77bd7f07f8316aafd8188b}.
   *
   * <p>
   * The configured molality-scale families are Na+/SO4-- beta0, beta1, and Cphi (PHREEQC C0); CO2/CO2, CO2/Na+, and
   * CO2/SO4-- lambda; and CO2/Na+/SO4-- zeta. The 1-2 binary uses the standard alpha1=2 Pitzer branch and has no beta2
   * term. Values are passed in PHREEQC six-term order without fitting or conversion.
   * </p>
   *
   * @param phase Pitzer aqueous phase containing CO2, Na+, and SO4--
   * @throws IllegalArgumentException if a required component is absent or has the wrong charge role
   */
  public static void applyPhreeqcCo2SodiumSulfate(PhasePitzer phase) {
    if (phase == null) {
      throw new IllegalArgumentException("Pitzer phase must not be null");
    }
    int carbonDioxide = requiredComponentIndex(phase, "CO2");
    int sodium = requiredComponentIndex(phase, "Na+");
    int sulfate = requiredComponentIndex(phase, "SO4--");
    if (Math.abs(phase.getComponent(carbonDioxide).getIonicCharge()) >= 0.5
        || phase.getComponent(sodium).getIonicCharge() <= 0.0 || phase.getComponent(sulfate).getIonicCharge() >= 0.0) {
      throw new IllegalArgumentException("PHREEQC CO2-Na2SO4 dataset species have incompatible charge roles");
    }

    phase.setParameterDatasetId(PHREEQC_CO2_NA2SO4_ID);
    phase.setPhreeqcBinaryTemperatureCoefficients(sodium, sulfate, PHREEQC_REFERENCE_TEMPERATURE_K,
        new double[] { 2.73e-2, 0.0, -5.8, 9.89e-3, 0.0, -1.563e5 },
        new double[] { 0.956, 2.663e3, 0.0, 1.158e-2, 0.0, -3.194e5 },
        new double[] { 3.418e-3, -384.0, 0.0, -8.451e-4, 0.0, 5.177e4 });
    phase.setLambdaTemperatureCoefficients(carbonDioxide, carbonDioxide, PHREEQC_REFERENCE_TEMPERATURE_K,
        new double[] { -1.34e-2, 348.0, 0.803, 0.0, 0.0, 0.0 });
    phase.setLambda(carbonDioxide, sodium, 0.085);
    phase.setLambda(carbonDioxide, sulfate, 0.075);
    phase.setZeta(carbonDioxide, sodium, sulfate, -0.015);
    phase.markManualParameterDatasetLoaded();
  }

  /**
   * Reports whether temperature and Na2SO4 molality are inside the independent experimental validation envelope
   * recorded for this dataset subset.
   *
   * @param temperature temperature in K
   * @param sodiumSulfateMolality formula-unit molality in mol/kg water
   * @return {@code true} within the inclusive validation envelope
   */
  public static boolean isWithinCo2SodiumSulfateValidationRange(double temperature, double sodiumSulfateMolality) {
    return Double.isFinite(temperature) && Double.isFinite(sodiumSulfateMolality)
        && temperature >= CO2_NA2SO4_VALIDATION_MIN_TEMPERATURE_K
        && temperature <= CO2_NA2SO4_VALIDATION_MAX_TEMPERATURE_K
        && sodiumSulfateMolality >= CO2_NA2SO4_VALIDATION_MIN_SALT_MOLALITY
        && sodiumSulfateMolality <= CO2_NA2SO4_VALIDATION_MAX_SALT_MOLALITY;
  }

  private static int requiredComponentIndex(PhasePitzer phase, String componentName) {
    if (!phase.hasComponent(componentName)) {
      throw new IllegalArgumentException("PHREEQC CO2-Na2SO4 dataset requires component '" + componentName + "'");
    }
    return phase.getComponent(componentName).getComponentNumber();
  }
}
