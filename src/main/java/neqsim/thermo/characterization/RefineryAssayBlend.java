package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable screening properties for a mass blend of resolved refinery assays.
 *
 * <p>
 * Blend specific gravity uses ideal additive liquid volumes. Sulfur and nitrogen, when supplied,
 * use linear mass-basis mixing. This class does not create pseudo-components or mutate an assay or
 * thermodynamic system.
 * </p>
 */
public final class RefineryAssayBlend implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double WATER_DENSITY_60F_KG_M3 = 999.016;

  private final double[] massFractions;
  private final double specificGravity;
  private final double sulfurMassFraction;
  private final double nitrogenMassFraction;
  private final boolean qualitiesAvailable;

  private RefineryAssayBlend(double[] sourceMasses, double[] sourceSpecificGravities,
      double[] sourceSulfurMassFractions, double[] sourceNitrogenMassFractions) {
    validateEqualLength(sourceMasses, sourceSpecificGravities, "specific-gravity");
    boolean hasQualities = sourceSulfurMassFractions != null || sourceNitrogenMassFractions != null;
    if (hasQualities) {
      if (sourceSulfurMassFractions == null || sourceNitrogenMassFractions == null) {
        throw new IllegalArgumentException("Sulfur and nitrogen arrays must be supplied together");
      }
      validateEqualLength(sourceMasses, sourceSulfurMassFractions, "sulfur");
      validateEqualLength(sourceMasses, sourceNitrogenMassFractions, "nitrogen");
    }

    double totalMass = 0.0;
    for (double sourceMass : sourceMasses) {
      if (!Double.isFinite(sourceMass) || sourceMass < 0.0) {
        throw new IllegalArgumentException("Source masses must be finite and non-negative");
      }
      totalMass += sourceMass;
      if (!Double.isFinite(totalMass)) {
        throw new IllegalArgumentException("Total source mass must be finite");
      }
    }
    if (!(totalMass > 0.0)) {
      throw new IllegalArgumentException("Blend must contain positive source mass");
    }

    double[] resolvedMassFractions = new double[sourceMasses.length];
    double additiveVolumePerUnitMass = 0.0;
    double resolvedSulfurMassFraction = 0.0;
    double resolvedNitrogenMassFraction = 0.0;
    for (int i = 0; i < sourceMasses.length; i++) {
      double massFraction = sourceMasses[i] / totalMass;
      resolvedMassFractions[i] = massFraction;
      if (!(massFraction > 0.0)) {
        continue;
      }

      double sourceSpecificGravity = sourceSpecificGravities[i];
      requirePositiveFinite(sourceSpecificGravity, "Source specific gravity");
      additiveVolumePerUnitMass += massFraction / sourceSpecificGravity;

      if (hasQualities) {
        double sourceSulfurMassFraction = sourceSulfurMassFractions[i];
        double sourceNitrogenMassFraction = sourceNitrogenMassFractions[i];
        requireMassFraction(sourceSulfurMassFraction, "Source sulfur mass fraction");
        requireMassFraction(sourceNitrogenMassFraction, "Source nitrogen mass fraction");
        resolvedSulfurMassFraction += massFraction * sourceSulfurMassFraction;
        resolvedNitrogenMassFraction += massFraction * sourceNitrogenMassFraction;
      }
    }

    double resolvedSpecificGravity = 1.0 / additiveVolumePerUnitMass;
    requirePositiveFinite(resolvedSpecificGravity, "Blend specific gravity");
    if (hasQualities) {
      requireMassFraction(resolvedSulfurMassFraction, "Blend sulfur mass fraction");
      requireMassFraction(resolvedNitrogenMassFraction, "Blend nitrogen mass fraction");
    }

    massFractions = resolvedMassFractions;
    specificGravity = resolvedSpecificGravity;
    sulfurMassFraction = resolvedSulfurMassFraction;
    nitrogenMassFraction = resolvedNitrogenMassFraction;
    qualitiesAvailable = hasQualities;
  }

  /**
   * Create a density/API screening blend from already-resolved whole-assay properties.
   *
   * @param sourceMasses non-negative source masses in any common mass unit
   * @param sourceSpecificGravities source whole-assay specific gravities
   * @return immutable blend properties
   */
  public static RefineryAssayBlend fromBulkProperties(double[] sourceMasses,
      double[] sourceSpecificGravities) {
    return new RefineryAssayBlend(sourceMasses, sourceSpecificGravities, null, null);
  }

  /**
   * Create a density/API and sulfur/nitrogen screening blend from resolved bulk properties.
   *
   * @param sourceMasses non-negative source masses in any common mass unit
   * @param sourceSpecificGravities source whole-assay specific gravities
   * @param sourceSulfurMassFractions source total-sulfur mass fractions on a 0-1 basis
   * @param sourceNitrogenMassFractions source total-nitrogen mass fractions on a 0-1 basis
   * @return immutable blend properties
   */
  public static RefineryAssayBlend fromBulkProperties(double[] sourceMasses,
      double[] sourceSpecificGravities, double[] sourceSulfurMassFractions,
      double[] sourceNitrogenMassFractions) {
    return new RefineryAssayBlend(sourceMasses, sourceSpecificGravities,
        sourceSulfurMassFractions, sourceNitrogenMassFractions);
  }

  /**
   * Resolve complete density and quality inputs from configured assays before returning a blend.
   *
   * <p>
   * Every positive-mass assay must support bulk specific gravity, sulfur, and nitrogen. Zero-mass
   * assays are retained in the returned mass-fraction array but their properties are not queried.
   * </p>
   *
   * @param assays configured source assays
   * @param sourceMasses non-negative source masses in any common mass unit
   * @return immutable blend properties
   */
  public static RefineryAssayBlend fromAssays(OilAssayCharacterisation[] assays,
      double[] sourceMasses) {
    if (assays == null || sourceMasses == null || assays.length == 0
        || assays.length != sourceMasses.length) {
      throw new IllegalArgumentException("Assay and mass arrays must be non-empty and equal length");
    }

    double[] sourceSpecificGravities = new double[assays.length];
    double[] sourceSulfurMassFractions = new double[assays.length];
    double[] sourceNitrogenMassFractions = new double[assays.length];
    for (int i = 0; i < assays.length; i++) {
      if (assays[i] == null) {
        throw new IllegalArgumentException("Source assays cannot be null");
      }
      if (!Double.isFinite(sourceMasses[i]) || sourceMasses[i] < 0.0) {
        throw new IllegalArgumentException("Source masses must be finite and non-negative");
      }
      if (sourceMasses[i] > 0.0) {
        sourceSpecificGravities[i] = assays[i].getBulkSpecificGravity();
        sourceSulfurMassFractions[i] = assays[i].getBulkSulfurMassFraction();
        sourceNitrogenMassFractions[i] = assays[i].getBulkNitrogenMassFraction();
      }
    }
    return fromBulkProperties(sourceMasses, sourceSpecificGravities,
        sourceSulfurMassFractions, sourceNitrogenMassFractions);
  }

  /** @return a defensive copy of normalized source mass fractions */
  public double[] getMassFractions() {
    return Arrays.copyOf(massFractions, massFractions.length);
  }

  /** @return ideal-additive-volume blend specific gravity */
  public double getSpecificGravity() {
    return specificGravity;
  }

  /** @return blend API gravity */
  public double getApiGravity() {
    return 141.5 / specificGravity - 131.5;
  }

  /** @return blend density at 60 degrees Fahrenheit in kg/m3 */
  public double getDensityKgPerCubicMetreAt60F() {
    return specificGravity * WATER_DENSITY_60F_KG_M3;
  }

  /** @return whether complete sulfur and nitrogen screening results are available */
  public boolean hasAssayQualities() {
    return qualitiesAvailable;
  }

  /** @return blend total-sulfur mass fraction on a 0-1 basis */
  public double getSulfurMassFraction() {
    requireQualities();
    return sulfurMassFraction;
  }

  /** @return blend total sulfur in mass percent */
  public double getSulfurMassPercent() {
    return 100.0 * getSulfurMassFraction();
  }

  /** @return blend total-nitrogen mass fraction on a 0-1 basis */
  public double getNitrogenMassFraction() {
    requireQualities();
    return nitrogenMassFraction;
  }

  /** @return blend total nitrogen in mass percent */
  public double getNitrogenMassPercent() {
    return 100.0 * getNitrogenMassFraction();
  }

  private void requireQualities() {
    if (!qualitiesAvailable) {
      throw new IllegalStateException("Complete sulfur and nitrogen inputs were not supplied");
    }
  }

  private static void validateEqualLength(double[] sourceMasses, double[] values,
      String propertyName) {
    if (sourceMasses == null || values == null || sourceMasses.length == 0
        || sourceMasses.length != values.length) {
      throw new IllegalArgumentException(
          "Source mass and " + propertyName + " arrays must be non-empty and equal length");
    }
  }

  private static void requirePositiveFinite(double value, String propertyName) {
    if (!Double.isFinite(value) || !(value > 0.0)) {
      throw new IllegalArgumentException(propertyName + " must be finite and positive");
    }
  }

  private static void requireMassFraction(double value, String propertyName) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(propertyName + " must be finite and between 0 and 1");
    }
  }
}
