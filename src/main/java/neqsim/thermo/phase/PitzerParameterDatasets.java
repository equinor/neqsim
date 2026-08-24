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

  /** Exact source identity for the public-domain PHREEQC Na-K-Cl subset. */
  public static final String PHREEQC_NA_K_CL_ID = "usgs-phreeqc-pitzer-b0b3be767158ccc3322d2c816625cf470045e67e-na-k-cl-v1";

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

  /** Lowest temperature checked against independent IPhreeqc Na-K-Cl calculations, in K. */
  public static final double NA_K_CL_VALIDATION_MIN_TEMPERATURE_K = 298.15;

  /** Highest temperature checked against independent IPhreeqc Na-K-Cl calculations, in K. */
  public static final double NA_K_CL_VALIDATION_MAX_TEMPERATURE_K = 423.15;

  /** Lowest total chloride molality checked for the Na-K-Cl subset, in mol/kg water. */
  public static final double NA_K_CL_VALIDATION_MIN_CHLORIDE_MOLALITY = 0.1;

  /** Highest total chloride molality checked for the Na-K-Cl subset, in mol/kg water. */
  public static final double NA_K_CL_VALIDATION_MAX_CHLORIDE_MOLALITY = 3.0;

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
    phase.enablePhreeqcCommonIonTerms();
    phase.markManualParameterDatasetLoaded();
  }

  /**
   * Applies the coherent public-domain PHREEQC Na-K-Cl parameter subset audited at commit
   * {@code b0b3be767158ccc3322d2c816625cf470045e67e}, database blob {@code 324f852784be84650b77bd7f07f8316aafd8188b}.
   *
   * <p>
   * The subset contains the complete binary {@code B0}, {@code B1}, and {@code C0} six-term temperature functions for
   * Na+/Cl- and K+/Cl-, the K+/Na+ {@code theta} interaction, and the Cl-/K+/Na+ {@code psi} six-term function. PHREEQC
   * {@code C0} maps directly to NeqSim {@code Cphi} before the common charge normalization. Both binaries use
   * {@code alpha1=2}; neither has a {@code B2} term. Values are passed without fitting, scale conversion, or mixing
   * with the legacy NeqSim dataset.
   * </p>
   *
   * @param phase Pitzer aqueous phase containing Na+, K+, and Cl-
   * @throws IllegalArgumentException if a required component is absent or has the wrong charge role
   */
  public static void applyPhreeqcSodiumPotassiumChloride(PhasePitzer phase) {
    if (phase == null) {
      throw new IllegalArgumentException("Pitzer phase must not be null");
    }
    int sodium = requiredComponentIndex(phase, "Na+");
    int potassium = requiredComponentIndex(phase, "K+");
    int chloride = requiredComponentIndex(phase, "Cl-");
    if (phase.getComponent(sodium).getIonicCharge() <= 0.0 || phase.getComponent(potassium).getIonicCharge() <= 0.0
        || phase.getComponent(chloride).getIonicCharge() >= 0.0) {
      throw new IllegalArgumentException("PHREEQC Na-K-Cl dataset species have incompatible charge roles");
    }

    phase.setParameterDatasetId(PHREEQC_NA_K_CL_ID);
    phase.setPhreeqcBinaryTemperatureCoefficients(sodium, chloride, PHREEQC_REFERENCE_TEMPERATURE_K,
        new double[] { 7.534e-2, 9598.4, 35.48, -5.8731e-2, 1.798e-5, -5.0e5 },
        new double[] { 0.2769, 1.377e4, 46.8, -6.9512e-2, 2.0e-5, -7.4823e5 },
        new double[] { 1.48e-3, -120.5, -0.2081, 0.0, 1.166e-7, 11121.0 });
    phase.setPhreeqcBinaryTemperatureCoefficients(potassium, chloride, PHREEQC_REFERENCE_TEMPERATURE_K,
        new double[] { 0.04808, -758.48, -4.7062, 0.010072, -3.7599e-6, 0.0 },
        new double[] { 0.2168, 0.0, -6.895, 2.262e-2, -9.293e-6, -1.0e5 },
        new double[] { -7.88e-4, 91.27, 0.58643, -1.298e-3, 4.9567e-7, 0.0 });
    phase.setThetaTemperatureCoefficients(potassium, sodium, PHREEQC_REFERENCE_TEMPERATURE_K,
        new double[] { -0.012, 0.0, 0.0, 0.0, 0.0, 0.0 });
    phase.setPsiTemperatureCoefficients(potassium, sodium, chloride, PHREEQC_REFERENCE_TEMPERATURE_K,
        new double[] { -0.0015, 0.0, 0.0, 1.8e-5, 0.0, 0.0 });
    phase.enablePhreeqcCommonIonTerms();
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

  /**
   * Reports whether a Na-K-Cl aqueous state is inside the independently checked IPhreeqc envelope.
   *
   * <p>
   * This is an implementation-comparison envelope, not a universal empirical validity claim for every composition or
   * pressure.
   * </p>
   *
   * @param temperature temperature in K
   * @param sodiumMolality sodium molality in mol/kg water
   * @param potassiumMolality potassium molality in mol/kg water
   * @param chlorideMolality chloride molality in mol/kg water
   * @return {@code true} when inputs are finite, non-negative, charge balanced, and inside the checked envelope
   */
  public static boolean isWithinSodiumPotassiumChlorideValidationRange(double temperature, double sodiumMolality,
      double potassiumMolality, double chlorideMolality) {
    if (!Double.isFinite(temperature) || !Double.isFinite(sodiumMolality) || !Double.isFinite(potassiumMolality)
        || !Double.isFinite(chlorideMolality) || sodiumMolality < 0.0 || potassiumMolality < 0.0
        || chlorideMolality < NA_K_CL_VALIDATION_MIN_CHLORIDE_MOLALITY
        || chlorideMolality > NA_K_CL_VALIDATION_MAX_CHLORIDE_MOLALITY) {
      return false;
    }
    double chargeScale = Math.max(1.0, chlorideMolality);
    return temperature >= NA_K_CL_VALIDATION_MIN_TEMPERATURE_K && temperature <= NA_K_CL_VALIDATION_MAX_TEMPERATURE_K
        && Math.abs(sodiumMolality + potassiumMolality - chlorideMolality) <= 1.0e-12 * chargeScale;
  }

  private static int requiredComponentIndex(PhasePitzer phase, String componentName) {
    if (!phase.hasComponent(componentName)) {
      throw new IllegalArgumentException("PHREEQC Pitzer dataset requires component '" + componentName + "'");
    }
    return phase.getComponent(componentName).getComponentNumber();
  }
}
