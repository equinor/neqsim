package neqsim.thermo.characterization;

import java.util.Objects;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;

/**
 * Constrained pseudo-component input factory for the public Sarir refinery TBP reference.
 *
 * <p>
 * The source publishes cumulative liquid-volume TBP evidence, whole-crude density, and whole-crude average molar mass,
 * but does not publish density or molar mass for each boiling interval. This factory therefore derives cut yields and
 * boundaries only from {@link SarirAtmosphericReference} and requires callers to supply the missing per-cut property
 * profiles. It does not silently synthesize light-end composition or heavy-end properties.
 * </p>
 *
 * <p>
 * Supplied profiles must reproduce the reported 841.5 kg/m3 whole-crude density within 1.0 kg/m3 and the reported
 * 0.2447 kg/mol number-average molar mass within 0.001 kg/mol. These are model-consistency gates, not estimates of
 * source uncertainty. The first 7.44 liquid vol% cut retains only the published 70 degrees Celsius upper boundary; the
 * final 16.30 liquid vol% cut retains only the published 550 degrees Celsius lower boundary.
 * </p>
 */
public final class SarirAtmosphericAssay {
  /** Number of source-derived boiling intervals, including the two one-sided terminal cuts. */
  public static final int CUT_COUNT = 18;

  /** Maximum absolute mismatch from the reported whole-crude density, in kg/m3. */
  public static final double BULK_DENSITY_TOLERANCE_KG_PER_CUBIC_METRE = 1.0;

  /** Maximum absolute mismatch from the reported whole-crude average molar mass, in kg/mol. */
  public static final double BULK_MOLAR_MASS_TOLERANCE_KG_PER_MOL = 0.001;

  private static final double KILOGRAM_PER_CUBIC_METRE_PER_SPECIFIC_GRAVITY = 1000.0;
  private static final double[] CUT_VOLUME_PERCENT = { 7.44, 3.03, 3.36, 7.33, 7.36, 3.02, 6.49, 3.73, 2.92, 7.29, 7.22,
      4.31, 9.02, 3.09, 3.05, 2.39, 2.65, 16.30 };
  private static final String[] CUT_NAMES = { "SARIR_TBP_70_MINUS", "SARIR_TBP_70_90", "SARIR_TBP_90_110",
      "SARIR_TBP_110_150", "SARIR_TBP_150_195", "SARIR_TBP_195_215", "SARIR_TBP_215_255", "SARIR_TBP_255_275",
      "SARIR_TBP_275_295", "SARIR_TBP_295_335", "SARIR_TBP_335_370", "SARIR_TBP_370_400", "SARIR_TBP_400_460",
      "SARIR_TBP_460_480", "SARIR_TBP_480_500", "SARIR_TBP_500_520", "SARIR_TBP_520_550", "SARIR_TBP_550_PLUS" };

  private SarirAtmosphericAssay() {
  }

  /**
   * Return the source-derived liquid-volume percentage of every modeled cut.
   *
   * @return defensive copy of the 18 cut yields, in liquid volume percent
   */
  public static double[] getCutVolumePercent() {
    return CUT_VOLUME_PERCENT.clone();
  }

  /**
   * Calculate bulk density-correlation input from a cut specific-gravity profile.
   *
   * <p>
   * Because the source yields are on a liquid-volume basis, the bulk value is the volume-weighted arithmetic mean of
   * the cut specific gravities.
   * </p>
   *
   * @param cutSpecificGravity dimensionless specific gravity for each source-derived cut
   * @return volume-weighted bulk specific gravity
   */
  public static double calculateBulkSpecificGravity(double[] cutSpecificGravity) {
    validatePositiveProfile(cutSpecificGravity, "Cut specific gravity");
    double bulkSpecificGravity = 0.0;
    for (int i = 0; i < CUT_COUNT; i++) {
      bulkSpecificGravity += CUT_VOLUME_PERCENT[i] / 100.0 * cutSpecificGravity[i];
    }
    return bulkSpecificGravity;
  }

  /**
   * Calculate the modeled whole-crude number-average molar mass.
   *
   * <p>
   * Cut volume yields are first converted to relative masses with the supplied specific gravities. The mixture molar
   * mass is then {@code total mass / total moles}.
   * </p>
   *
   * @param cutSpecificGravity dimensionless specific gravity for each source-derived cut
   * @param cutMolarMassKgPerMol molar mass for each source-derived cut, in kg/mol
   * @return modeled whole-crude number-average molar mass, in kg/mol
   */
  public static double calculateBulkMolarMassKgPerMol(double[] cutSpecificGravity, double[] cutMolarMassKgPerMol) {
    validatePositiveProfile(cutSpecificGravity, "Cut specific gravity");
    validatePositiveProfile(cutMolarMassKgPerMol, "Cut molar mass");

    double relativeMass = 0.0;
    double relativeMoles = 0.0;
    for (int i = 0; i < CUT_COUNT; i++) {
      double cutRelativeMass = CUT_VOLUME_PERCENT[i] / 100.0 * cutSpecificGravity[i];
      relativeMass += cutRelativeMass;
      relativeMoles += cutRelativeMass / cutMolarMassKgPerMol[i];
    }
    if (!Double.isFinite(relativeMass) || !Double.isFinite(relativeMoles) || !(relativeMass > 0.0)
        || !(relativeMoles > 0.0)) {
      throw new IllegalArgumentException("Cut profiles do not produce finite positive bulk properties");
    }
    return relativeMass / relativeMoles;
  }

  /**
   * Configure a constrained Sarir assay on a one-kilogram basis.
   *
   * @param system empty or caller-owned thermodynamic system
   * @param cutSpecificGravity dimensionless specific gravity for each source-derived cut
   * @param cutMolarMassKgPerMol molar mass for each source-derived cut, in kg/mol
   * @return configured assay; no component has been added to {@code system}
   */
  public static OilAssayCharacterisation create(SystemInterface system, double[] cutSpecificGravity,
      double[] cutMolarMassKgPerMol) {
    return create(system, 1.0, cutSpecificGravity, cutMolarMassKgPerMol);
  }

  /**
   * Configure a constrained Sarir assay.
   *
   * <p>
   * Validation completes before the attached assay is cleared, so invalid profiles do not modify existing assay data or
   * the thermodynamic component list.
   * </p>
   *
   * @param system empty or caller-owned thermodynamic system
   * @param totalAssayMassKg positive total modeled assay mass, in kg
   * @param cutSpecificGravity dimensionless specific gravity for each source-derived cut
   * @param cutMolarMassKgPerMol molar mass for each source-derived cut, in kg/mol
   * @return configured assay; no component has been added to {@code system}
   */
  public static OilAssayCharacterisation create(SystemInterface system, double totalAssayMassKg,
      double[] cutSpecificGravity, double[] cutMolarMassKgPerMol) {
    Objects.requireNonNull(system, "system");
    if (!Double.isFinite(totalAssayMassKg) || !(totalAssayMassKg > 0.0)) {
      throw new IllegalArgumentException("Total assay mass must be finite and positive");
    }

    double bulkSpecificGravity = calculateBulkSpecificGravity(cutSpecificGravity);
    double bulkDensityKgPerCubicMetre = bulkSpecificGravity * KILOGRAM_PER_CUBIC_METRE_PER_SPECIFIC_GRAVITY;
    double sourceDensityKgPerCubicMetre = SarirAtmosphericReference.getCrudeDensityAt15CKgPerCubicMetre();
    if (Math
        .abs(bulkDensityKgPerCubicMetre - sourceDensityKgPerCubicMetre) > BULK_DENSITY_TOLERANCE_KG_PER_CUBIC_METRE) {
      throw new IllegalArgumentException("Cut density profile does not reproduce the reported Sarir bulk density");
    }

    double bulkMolarMassKgPerMol = calculateBulkMolarMassKgPerMol(cutSpecificGravity, cutMolarMassKgPerMol);
    double sourceMolarMassKgPerMol = SarirAtmosphericReference.getCrudeAverageMolarMassKgPerMol();
    if (Math.abs(bulkMolarMassKgPerMol - sourceMolarMassKgPerMol) > BULK_MOLAR_MASS_TOLERANCE_KG_PER_MOL) {
      throw new IllegalArgumentException(
          "Cut molar-mass profile does not reproduce the reported Sarir average molar mass");
    }

    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.setTotalAssayMass(totalAssayMassKg);

    double[] tbpTemperatureCelsius = SarirAtmosphericReference.getTbpTemperatureCelsius();
    for (int i = 0; i < CUT_COUNT; i++) {
      AssayCut cut = new AssayCut(CUT_NAMES[i]).withVolumePercent(CUT_VOLUME_PERCENT[i])
          .withSpecificGravity(cutSpecificGravity[i]).withMolarMassKgPerMol(cutMolarMassKgPerMol[i]);
      if (i == 0) {
        cut.withUpperBoilingPointCelsius(tbpTemperatureCelsius[0]);
      } else if (i == CUT_COUNT - 1) {
        cut.withLowerBoilingPointCelsius(SarirAtmosphericReference.getTerminalResidueLowerBoundaryCelsius());
      } else {
        cut.withBoilingRangeCelsius(tbpTemperatureCelsius[i - 1], tbpTemperatureCelsius[i]);
      }
      assay.addCut(cut);
    }
    return assay;
  }

  private static void validatePositiveProfile(double[] profile, String label) {
    if (profile == null || profile.length != CUT_COUNT) {
      throw new IllegalArgumentException(label + " profile must contain exactly " + CUT_COUNT + " values");
    }
    for (double value : profile) {
      if (!Double.isFinite(value) || !(value > 0.0)) {
        throw new IllegalArgumentException(label + " values must be finite and positive");
      }
    }
  }
}
