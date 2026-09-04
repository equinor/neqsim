package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/**
 * Independent-evidence qualification of the COMPSALT gypsum/anhydrite phase boundary.
 *
 * <p>
 * The calculation reuses the authoritative {@link CalcSaltSatauration} solubility-product path. For simultaneous
 * equilibrium of anhydrite ({@code CaSO4}) and gypsum ({@code CaSO4.2H2O}), the dissolved-ion activity product cancels
 * and the required water activity is {@code a(H2O) = sqrt(Ksp(gypsum) / Ksp(anhydrite))}. No mineral, Pitzer,
 * electrolyte-EOS, or reaction parameter is fitted or changed by this evidence view.
 * </p>
 *
 * <p>
 * The declared validation envelopes are from Voigt and Freyer (2023), DOI 10.3389/fnuen.2023.1208582, CC BY 4.0. Bock
 * (1961), DOI 10.1139/v61-228, is the primary experimental lineage for the two NaCl crossing intervals; no primary
 * table row is redistributed here.
 * </p>
 */
public final class CalciumSulfatePhaseBoundaryQualification implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One-bar reference pressure used by the registered evidence. */
  public static final double REFERENCE_PRESSURE_BARA = 1.0;
  /** CC BY evidence source DOI. */
  public static final String EVIDENCE_DOI = "10.3389/fnuen.2023.1208582";
  /** Primary experimental lineage DOI. */
  public static final String PRIMARY_LINEAGE_DOI = "10.1139/v61-228";
  /** Primary high-pressure anhydrite-solubility lineage DOI. */
  public static final String HIGH_PRESSURE_LINEAGE_DOI = "10.2475/ajs.261.1.61";
  /** License of the registered numerical evidence. */
  public static final String EVIDENCE_LICENSE = "CC BY 4.0";
  /** Primary anhydrite synchrotron crystallography DOI. */
  public static final String ANHYDRITE_CRYSTALLOGRAPHY_DOI = "10.1154/1.3659285";
  /** Primary gypsum synchrotron crystallography DOI. */
  public static final String GYPSUM_CRYSTALLOGRAPHY_DOI = "10.1154/1.1725254";
  /** Liquid-water density reference-correlation DOI. */
  public static final String WATER_DENSITY_REFERENCE_DOI = "10.1063/1.3043575";
  /** Temperature of the liquid-water density reference. */
  public static final double WATER_DENSITY_REFERENCE_TEMPERATURE_K = 298.15;
  /** Pressure of the liquid-water density reference. */
  public static final double WATER_DENSITY_REFERENCE_PRESSURE_BARA = 1.0;
  /** Reference pressure of the COMPSALT constant-volume correction. */
  public static final double COMPSALT_PRESSURE_CORRECTION_REFERENCE_BARA = CalcSaltSatauration.PRESSURE_CORRECTION_REFERENCE_BARA;

  private static final double PURE_WATER_MINIMUM_C = 41.0;
  private static final double PURE_WATER_MAXIMUM_C = 43.0;
  private static final double NACL_25_WATER_ACTIVITY_MINIMUM = 0.8551;
  private static final double NACL_25_WATER_ACTIVITY_MAXIMUM = 0.8634;
  private static final double NACL_40_WATER_ACTIVITY_MINIMUM = 0.9370;
  private static final double NACL_40_WATER_ACTIVITY_MAXIMUM = 0.9587;
  private static final double REFERENCE_PRESSURE_TOLERANCE_BARA = 0.02;
  private static final double AVOGADRO_ANGSTROM3_TO_CM3_PER_MOL = 0.602214076;
  private static final double ANHYDRITE_CELL_VOLUME_ANGSTROM3 = 305.487;
  private static final double GYPSUM_CELL_VOLUME_ANGSTROM3 = 494.536;
  private static final double FORMULA_UNITS_PER_CELL = 4.0;
  private static final double WATER_MOLAR_MASS_G_PER_MOL = 18.01528;
  private static final double WATER_DENSITY_KG_PER_M3 = 997.047013;

  private final double evaluatedPressureBara;
  private final double evaluatedTemperatureKelvin;
  private final double predictedPureWaterTransitionCelsius;
  private final double predictedPureWaterTransitionAtEvaluatedPressureCelsius;
  private final double requiredWaterActivityAt25Celsius;
  private final double requiredWaterActivityAt40Celsius;
  private final double anhydriteLumpedReactionVolumeCm3PerMol;
  private final double gypsumLumpedReactionVolumeCm3PerMol;
  private final double anhydriteLogKspPressureCorrection;
  private final double gypsumLogKspPressureCorrection;
  private final boolean pureWaterEnvelopePass;
  private final boolean sodiumChloride25CEnvelopePass;
  private final boolean sodiumChloride40CEnvelopePass;
  private final boolean referencePressureEnvelopePass;

  /**
   * Evaluates the current COMPSALT correlations without changing the supplied system.
   *
   * @param system thermodynamic system whose pressure defines the requested use state
   */
  public CalciumSulfatePhaseBoundaryQualification(SystemInterface system) {
    if (system == null) {
      throw new IllegalArgumentException("Thermodynamic system must not be null");
    }
    evaluatedPressureBara = system.getPressure();
    evaluatedTemperatureKelvin = system.getTemperature();
    if (!(evaluatedPressureBara > 0.0) || !Double.isFinite(evaluatedPressureBara)) {
      throw new IllegalArgumentException("System pressure must be finite and positive");
    }
    if (!(evaluatedTemperatureKelvin > 0.0) || !Double.isFinite(evaluatedTemperatureKelvin)) {
      throw new IllegalArgumentException("System temperature must be finite and positive");
    }

    CalcSaltSatauration anhydrite = new CalcSaltSatauration(system, "CaSO4_A");
    CalcSaltSatauration gypsum = new CalcSaltSatauration(system, "CaSO4_G");
    predictedPureWaterTransitionCelsius = solvePureWaterTransitionCelsius(anhydrite, gypsum, REFERENCE_PRESSURE_BARA);
    boolean pressureCorrectionIsZero = evaluatedPressureBara <= 1.013
        || evaluatedPressureBara == COMPSALT_PRESSURE_CORRECTION_REFERENCE_BARA;
    predictedPureWaterTransitionAtEvaluatedPressureCelsius = pressureCorrectionIsZero
        ? predictedPureWaterTransitionCelsius
        : solvePureWaterTransitionCelsius(anhydrite, gypsum, evaluatedPressureBara);
    requiredWaterActivityAt25Celsius = requiredWaterActivity(anhydrite, gypsum, 298.15);
    requiredWaterActivityAt40Celsius = requiredWaterActivity(anhydrite, gypsum, 313.15);
    anhydriteLumpedReactionVolumeCm3PerMol = anhydrite.getLumpedReactionVolumeCm3PerMol();
    gypsumLumpedReactionVolumeCm3PerMol = gypsum.getLumpedReactionVolumeCm3PerMol();
    anhydriteLogKspPressureCorrection = anhydrite.getLogPressureCorrection(evaluatedTemperatureKelvin,
        evaluatedPressureBara);
    gypsumLogKspPressureCorrection = gypsum.getLogPressureCorrection(evaluatedTemperatureKelvin, evaluatedPressureBara);

    pureWaterEnvelopePass = within(predictedPureWaterTransitionCelsius, PURE_WATER_MINIMUM_C, PURE_WATER_MAXIMUM_C);
    sodiumChloride25CEnvelopePass = within(requiredWaterActivityAt25Celsius, NACL_25_WATER_ACTIVITY_MINIMUM,
        NACL_25_WATER_ACTIVITY_MAXIMUM);
    sodiumChloride40CEnvelopePass = within(requiredWaterActivityAt40Celsius, NACL_40_WATER_ACTIVITY_MINIMUM,
        NACL_40_WATER_ACTIVITY_MAXIMUM);
    referencePressureEnvelopePass = Math
        .abs(evaluatedPressureBara - REFERENCE_PRESSURE_BARA) <= REFERENCE_PRESSURE_TOLERANCE_BARA;
  }

  /** @return pressure of the requested use state in bara */
  public double getEvaluatedPressureBara() {
    return evaluatedPressureBara;
  }

  /** @return temperature of the requested use state in Kelvin */
  public double getEvaluatedTemperatureKelvin() {
    return evaluatedTemperatureKelvin;
  }

  /** @return predicted atmospheric pure-water transition temperature in degrees Celsius */
  public double getPredictedPureWaterTransitionCelsius() {
    return predictedPureWaterTransitionCelsius;
  }

  /**
   * Returns the COMPSALT pure-water transition prediction at the requested pressure.
   *
   * <p>
   * This is a reproducibility diagnostic only. It is not high-pressure qualification because the constant lumped
   * reaction-volume convention does not separately resolve aqueous-species volumes.
   * </p>
   *
   * @return predicted transition temperature in degrees Celsius at the evaluated pressure
   */
  public double getPredictedPureWaterTransitionAtEvaluatedPressureCelsius() {
    return predictedPureWaterTransitionAtEvaluatedPressureCelsius;
  }

  /** @return COMPSALT anhydrite lumped reaction-volume coefficient in cm3/mol */
  public double getAnhydriteLumpedReactionVolumeCm3PerMol() {
    return anhydriteLumpedReactionVolumeCm3PerMol;
  }

  /** @return COMPSALT gypsum lumped reaction-volume coefficient in cm3/mol */
  public double getGypsumLumpedReactionVolumeCm3PerMol() {
    return gypsumLumpedReactionVolumeCm3PerMol;
  }

  /**
   * Returns the nominal ambient molar volume derived from the published anhydrite unit cell.
   *
   * @return anhydrite molar volume in cm3/mol
   */
  public double getAnhydriteCrystallographicMolarVolumeCm3PerMol() {
    return ANHYDRITE_CELL_VOLUME_ANGSTROM3 * AVOGADRO_ANGSTROM3_TO_CM3_PER_MOL / FORMULA_UNITS_PER_CELL;
  }

  /**
   * Returns the nominal ambient molar volume derived from the published gypsum unit cell.
   *
   * @return gypsum molar volume in cm3/mol
   */
  public double getGypsumCrystallographicMolarVolumeCm3PerMol() {
    return GYPSUM_CELL_VOLUME_ANGSTROM3 * AVOGADRO_ANGSTROM3_TO_CM3_PER_MOL / FORMULA_UNITS_PER_CELL;
  }

  /**
   * Returns the liquid-water molar volume from the 298.15 K, 0.1 MPa reference density.
   *
   * @return liquid-water molar volume in cm3/mol
   */
  public double getLiquidWaterReferenceMolarVolumeCm3PerMol() {
    return WATER_MOLAR_MASS_G_PER_MOL / (WATER_DENSITY_KG_PER_M3 / 1000.0);
  }

  /**
   * Returns the nominal ambient crystallographic reaction volume for gypsum transforming to
   * anhydrite plus two liquid-water molecules.
   *
   * <p>
   * This is a measurement-derived structural diagnostic, not a fitted COMPSALT parameter or a
   * high-pressure qualification.
   * </p>
   *
   * @return nominal reaction volume in cm3/mol
   */
  public double getCrystallographicTransitionReactionVolumeCm3PerMol() {
    return getAnhydriteCrystallographicMolarVolumeCm3PerMol()
        + 2.0 * getLiquidWaterReferenceMolarVolumeCm3PerMol()
        - getGypsumCrystallographicMolarVolumeCm3PerMol();
  }

  /**
   * Returns the transition reaction volume implied by the difference between the existing lumped
   * COMPSALT gypsum and anhydrite coefficients.
   *
   * @return COMPSALT transition reaction volume in cm3/mol
   */
  public double getCompsaltTransitionReactionVolumeCm3PerMol() {
    return gypsumLumpedReactionVolumeCm3PerMol - anhydriteLumpedReactionVolumeCm3PerMol;
  }

  /** @return COMPSALT minus crystallographic transition reaction volume in cm3/mol */
  public double getTransitionReactionVolumeDifferenceCm3PerMol() {
    return getCompsaltTransitionReactionVolumeCm3PerMol()
        - getCrystallographicTransitionReactionVolumeCm3PerMol();
  }

  /** @return ratio of COMPSALT to crystallographic transition reaction volume */
  public double getTransitionReactionVolumeRatio() {
    return getCompsaltTransitionReactionVolumeCm3PerMol()
        / getCrystallographicTransitionReactionVolumeCm3PerMol();
  }

  /** @return primary anhydrite synchrotron crystallography DOI */
  public String getAnhydriteCrystallographyDoi() {
    return ANHYDRITE_CRYSTALLOGRAPHY_DOI;
  }

  /** @return primary gypsum synchrotron crystallography DOI */
  public String getGypsumCrystallographyDoi() {
    return GYPSUM_CRYSTALLOGRAPHY_DOI;
  }

  /** @return liquid-water density reference-correlation DOI */
  public String getWaterDensityReferenceDoi() {
    return WATER_DENSITY_REFERENCE_DOI;
  }

  /** @return logarithmic anhydrite Ksp pressure correction at the evaluated state */
  public double getAnhydriteLogKspPressureCorrection() {
    return anhydriteLogKspPressureCorrection;
  }

  /** @return logarithmic gypsum Ksp pressure correction at the evaluated state */
  public double getGypsumLogKspPressureCorrection() {
    return gypsumLogKspPressureCorrection;
  }

  /** @return {@code false}; aqueous-species volume contributions are not separately resolved */
  public boolean isAqueousSpeciesVolumeResolved() {
    return false;
  }

  /** @return {@code false}; the high-pressure reaction-volume convention remains unqualified */
  public boolean isHighPressureQualified() {
    return false;
  }

  /** @return primary high-pressure anhydrite-solubility lineage DOI */
  public String getHighPressureLineageDoi() {
    return HIGH_PRESSURE_LINEAGE_DOI;
  }

  /** @return lower independent pure-water transition limit in degrees Celsius */
  public double getPureWaterEvidenceMinimumCelsius() {
    return PURE_WATER_MINIMUM_C;
  }

  /** @return upper independent pure-water transition limit in degrees Celsius */
  public double getPureWaterEvidenceMaximumCelsius() {
    return PURE_WATER_MAXIMUM_C;
  }

  /** @return whether the predicted pure-water transition is inside 42 +/- 1 degrees Celsius */
  public boolean isPureWaterEnvelopePass() {
    return pureWaterEnvelopePass;
  }

  /** @return water activity required by the COMPSALT correlations at 25 degrees Celsius */
  public double getRequiredWaterActivityAt25Celsius() {
    return requiredWaterActivityAt25Celsius;
  }

  /** @return lower independent NaCl water-activity crossing limit at 25 degrees Celsius */
  public double getSodiumChloride25CWaterActivityMinimum() {
    return NACL_25_WATER_ACTIVITY_MINIMUM;
  }

  /** @return upper independent NaCl water-activity crossing limit at 25 degrees Celsius */
  public double getSodiumChloride25CWaterActivityMaximum() {
    return NACL_25_WATER_ACTIVITY_MAXIMUM;
  }

  /** @return whether the 25 degrees Celsius required water activity is inside the independent interval */
  public boolean isSodiumChloride25CEnvelopePass() {
    return sodiumChloride25CEnvelopePass;
  }

  /** @return water activity required by the COMPSALT correlations at 40 degrees Celsius */
  public double getRequiredWaterActivityAt40Celsius() {
    return requiredWaterActivityAt40Celsius;
  }

  /** @return lower independent NaCl water-activity crossing limit at 40 degrees Celsius */
  public double getSodiumChloride40CWaterActivityMinimum() {
    return NACL_40_WATER_ACTIVITY_MINIMUM;
  }

  /** @return upper independent NaCl water-activity crossing limit at 40 degrees Celsius */
  public double getSodiumChloride40CWaterActivityMaximum() {
    return NACL_40_WATER_ACTIVITY_MAXIMUM;
  }

  /** @return whether the 40 degrees Celsius required water activity is inside the independent interval */
  public boolean isSodiumChloride40CEnvelopePass() {
    return sodiumChloride40CEnvelopePass;
  }

  /** @return whether the requested pressure is within 0.02 bara of the one-bar evidence pressure */
  public boolean isReferencePressureEnvelopePass() {
    return referencePressureEnvelopePass;
  }

  /**
   * Reports the fail-closed publication decision.
   *
   * @return {@code true} only when all three independent phase-boundary envelopes and pressure scope pass
   */
  public boolean isPublicationReady() {
    return pureWaterEnvelopePass && sodiumChloride25CEnvelopePass && sodiumChloride40CEnvelopePass
        && referencePressureEnvelopePass;
  }

  /** @return deterministic {@code ACCEPTED} or {@code REJECTED} decision */
  public String getDecision() {
    return isPublicationReady() ? "ACCEPTED" : "REJECTED";
  }

  /** @return CC BY evidence source DOI */
  public String getEvidenceDoi() {
    return EVIDENCE_DOI;
  }

  /** @return primary Bock experimental-lineage DOI */
  public String getPrimaryLineageDoi() {
    return PRIMARY_LINEAGE_DOI;
  }

  /** @return evidence license */
  public String getEvidenceLicense() {
    return EVIDENCE_LICENSE;
  }

  /** @return immutable scientific limitations outside this evidence registration */
  public List<String> getLimitations() {
    return Collections.unmodifiableList(Arrays.asList(
        "Bock primary-table transcription, preprocessing uncertainty, and absolute-solubility "
            + "residuals remain unqualified",
        "COMPSALT Vdelta is a constant lumped reaction-volume coefficient, not a pure-mineral molar volume",
        "The ambient crystallographic reaction-volume cycle is a diagnostic only; its reported cell errors do not "
            + "bound thermal expansion, compressibility, sample, or other systematic effects",
        "High-pressure use requires a verified reaction-volume convention that separately resolves aqueous partial "
            + "or apparent molar volumes",
        "The registered evidence covers pure-water and NaCl phase crossings, not general "
            + "mixed-brine mineral equilibrium"));
  }

  /** @return deterministic evidence and decision diagnostic */
  public String formatDiagnostic() {
    return "Calcium-sulfate phase-boundary qualification: decision=" + getDecision() + ", pureWaterTransition_C="
        + predictedPureWaterTransitionCelsius + ", pureWaterEnvelope_C=[" + PURE_WATER_MINIMUM_C + ", "
        + PURE_WATER_MAXIMUM_C + "]" + ", requiredWaterActivity25C=" + requiredWaterActivityAt25Celsius
        + ", evidence25C=[" + NACL_25_WATER_ACTIVITY_MINIMUM + ", " + NACL_25_WATER_ACTIVITY_MAXIMUM + "]"
        + ", requiredWaterActivity40C=" + requiredWaterActivityAt40Celsius + ", evidence40C=["
        + NACL_40_WATER_ACTIVITY_MINIMUM + ", " + NACL_40_WATER_ACTIVITY_MAXIMUM + "]" + ", evaluatedPressure_bara="
        + evaluatedPressureBara + ", evaluatedPressureTransition_C="
        + predictedPureWaterTransitionAtEvaluatedPressureCelsius + ", anhydriteVdelta_cm3_per_mol="
        + anhydriteLumpedReactionVolumeCm3PerMol + ", gypsumVdelta_cm3_per_mol=" + gypsumLumpedReactionVolumeCm3PerMol
        + ", crystallographicTransitionV_cm3_per_mol=" + getCrystallographicTransitionReactionVolumeCm3PerMol()
        + ", compsaltTransitionV_cm3_per_mol=" + getCompsaltTransitionReactionVolumeCm3PerMol()
        + ", transitionVdifference_cm3_per_mol=" + getTransitionReactionVolumeDifferenceCm3PerMol()
        + ", transitionVratio=" + getTransitionReactionVolumeRatio() + ", aqueousSpeciesVolumeResolved="
        + isAqueousSpeciesVolumeResolved() + ", highPressureQualified="
        + isHighPressureQualified() + ", referencePressurePass=" + referencePressureEnvelopePass + ", evidenceDoi="
        + EVIDENCE_DOI + ", license=" + EVIDENCE_LICENSE;
  }

  private static double solvePureWaterTransitionCelsius(CalcSaltSatauration anhydrite, CalcSaltSatauration gypsum,
      double pressureBara) {
    double lowerTemperatureK = 273.15;
    double upperTemperatureK = 373.15;
    double lowerResidual = logKspDifference(anhydrite, gypsum, lowerTemperatureK, pressureBara);
    double upperResidual = logKspDifference(anhydrite, gypsum, upperTemperatureK, pressureBara);
    if (lowerResidual == 0.0) {
      return lowerTemperatureK - 273.15;
    }
    if (upperResidual == 0.0) {
      return upperTemperatureK - 273.15;
    }
    if (Math.signum(lowerResidual) == Math.signum(upperResidual)) {
      throw new IllegalStateException(
          "COMPSALT gypsum/anhydrite correlations do not bracket a transition from 0 to 100 C");
    }
    for (int iteration = 0; iteration < 100; iteration++) {
      double trialTemperatureK = 0.5 * (lowerTemperatureK + upperTemperatureK);
      double trialResidual = logKspDifference(anhydrite, gypsum, trialTemperatureK, pressureBara);
      if (Math.abs(trialResidual) <= 1.0e-12) {
        return trialTemperatureK - 273.15;
      }
      if (Math.signum(trialResidual) == Math.signum(lowerResidual)) {
        lowerTemperatureK = trialTemperatureK;
        lowerResidual = trialResidual;
      } else {
        upperTemperatureK = trialTemperatureK;
      }
    }
    return 0.5 * (lowerTemperatureK + upperTemperatureK) - 273.15;
  }

  private static double requiredWaterActivity(CalcSaltSatauration anhydrite, CalcSaltSatauration gypsum,
      double temperatureK) {
    return Math.exp(0.5 * logKspDifference(anhydrite, gypsum, temperatureK, REFERENCE_PRESSURE_BARA));
  }

  private static double logKspDifference(CalcSaltSatauration anhydrite, CalcSaltSatauration gypsum, double temperatureK,
      double pressureBara) {
    return Math.log(gypsum.getSolubilityProduct(temperatureK, pressureBara))
        - Math.log(anhydrite.getSolubilityProduct(temperatureK, pressureBara));
  }

  private static boolean within(double value, double minimum, double maximum) {
    return Double.isFinite(value) && value >= minimum && value <= maximum;
  }
}
