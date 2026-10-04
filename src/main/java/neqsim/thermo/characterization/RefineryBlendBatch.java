package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable source receipts for a mass-basis refinery blend batch.
 *
 * <p>
 * A normalized source recipe is scaled to one positive batch mass. Source liquid volumes use the same
 * ideal-additive-volume and 60 degrees Fahrenheit water-density basis as {@link RefineryAssayBlend}. The result is
 * bookkeeping evidence only; it does not model temperature correction, blend contraction, tank gauging, phase behavior,
 * or compatibility.
 * </p>
 */
public final class RefineryBlendBatch implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double WATER_DENSITY_60F_KG_M3 = 999.016;
  private static final double FRACTION_CLOSURE_TOLERANCE = 1.0e-12;

  private final double totalMassKg;
  private final double[] sourceMassFractions;
  private final double[] sourceMassesKg;
  private final double[] sourceSpecificGravities;
  private final double[] sourceVolumesM3At60F;
  private final double totalAdditiveVolumeM3At60F;
  private final double specificGravity;
  private final boolean costAvailable;
  private final double unitCostPerMass;
  private final double totalCost;

  private RefineryBlendBatch(double totalMassKg, double[] sourceMassFractions, double[] sourceSpecificGravities,
      boolean costAvailable, double unitCostPerMass) {
    if (!Double.isFinite(totalMassKg) || !(totalMassKg > 0.0)) {
      throw new IllegalArgumentException("Total blend mass must be finite and positive");
    }
    if (sourceMassFractions == null || sourceSpecificGravities == null || sourceMassFractions.length == 0
        || sourceMassFractions.length != sourceSpecificGravities.length) {
      throw new IllegalArgumentException(
          "Source fraction and specific-gravity arrays must be non-empty and equal length");
    }
    if (costAvailable && (!Double.isFinite(unitCostPerMass) || unitCostPerMass < 0.0)) {
      throw new IllegalArgumentException("Blend unit cost must be finite and non-negative");
    }

    double fractionSum = 0.0;
    for (double sourceMassFraction : sourceMassFractions) {
      if (!Double.isFinite(sourceMassFraction) || sourceMassFraction < 0.0) {
        throw new IllegalArgumentException("Source mass fractions must be finite and non-negative");
      }
      fractionSum += sourceMassFraction;
      if (!Double.isFinite(fractionSum)) {
        throw new IllegalArgumentException("Source mass-fraction sum must be finite");
      }
    }
    if (!(fractionSum > 0.0) || Math.abs(fractionSum - 1.0) > FRACTION_CLOSURE_TOLERANCE) {
      throw new IllegalArgumentException("Source mass fractions must sum to one");
    }

    this.totalMassKg = totalMassKg;
    this.sourceMassFractions = new double[sourceMassFractions.length];
    sourceMassesKg = new double[sourceMassFractions.length];
    this.sourceSpecificGravities = Arrays.copyOf(sourceSpecificGravities, sourceSpecificGravities.length);
    sourceVolumesM3At60F = new double[sourceMassFractions.length];

    double resolvedTotalMassKg = 0.0;
    double resolvedTotalVolumeM3 = 0.0;
    for (int i = 0; i < sourceMassFractions.length; i++) {
      double normalizedMassFraction = sourceMassFractions[i] / fractionSum;
      this.sourceMassFractions[i] = normalizedMassFraction;
      double sourceMassKg = totalMassKg * normalizedMassFraction;
      sourceMassesKg[i] = sourceMassKg;
      resolvedTotalMassKg += sourceMassKg;
      if (!(normalizedMassFraction > 0.0)) {
        continue;
      }

      double sourceSpecificGravity = sourceSpecificGravities[i];
      if (!Double.isFinite(sourceSpecificGravity) || !(sourceSpecificGravity > 0.0)) {
        throw new IllegalArgumentException("Positive-mass source specific gravities must be finite and positive");
      }
      double sourceVolumeM3 = sourceMassKg / (sourceSpecificGravity * WATER_DENSITY_60F_KG_M3);
      if (!Double.isFinite(sourceVolumeM3) || !(sourceVolumeM3 > 0.0)) {
        throw new IllegalArgumentException("Source additive volume must be finite and positive");
      }
      sourceVolumesM3At60F[i] = sourceVolumeM3;
      resolvedTotalVolumeM3 += sourceVolumeM3;
    }

    double massTolerance = FRACTION_CLOSURE_TOLERANCE * Math.max(1.0, totalMassKg);
    if (!Double.isFinite(resolvedTotalMassKg) || Math.abs(resolvedTotalMassKg - totalMassKg) > massTolerance) {
      throw new IllegalStateException("Blend source masses do not close to the requested total");
    }
    if (!Double.isFinite(resolvedTotalVolumeM3) || !(resolvedTotalVolumeM3 > 0.0)) {
      throw new IllegalStateException("Blend additive volume must be finite and positive");
    }

    double resolvedSpecificGravity = totalMassKg / (resolvedTotalVolumeM3 * WATER_DENSITY_60F_KG_M3);
    double assaySpecificGravity = RefineryAssayBlend.fromBulkProperties(sourceMassesKg, sourceSpecificGravities)
        .getSpecificGravity();
    double gravityTolerance = FRACTION_CLOSURE_TOLERANCE * Math.max(1.0, Math.abs(assaySpecificGravity));
    if (!Double.isFinite(resolvedSpecificGravity)
        || Math.abs(resolvedSpecificGravity - assaySpecificGravity) > gravityTolerance) {
      throw new IllegalStateException("Blend receipt specific gravity does not match RefineryAssayBlend");
    }

    totalAdditiveVolumeM3At60F = resolvedTotalVolumeM3;
    specificGravity = resolvedSpecificGravity;
    this.costAvailable = costAvailable;
    this.unitCostPerMass = unitCostPerMass;
    totalCost = costAvailable ? totalMassKg * unitCostPerMass : Double.NaN;
    if (costAvailable && !Double.isFinite(totalCost)) {
      throw new IllegalArgumentException("Total blend cost must be finite");
    }
  }

  /**
   * Scale one normalized source recipe to an auditable batch receipt.
   *
   * @param totalMassKg requested positive total batch mass in kg
   * @param sourceMassFractions non-negative source mass fractions summing to one
   * @param sourceSpecificGravities source specific gravities on the 60 degrees Fahrenheit basis
   * @return immutable uncosted batch receipt
   * @throws IllegalArgumentException for invalid mass, arrays, fractions, or contributing source gravities
   */
  public static RefineryBlendBatch fromMassFractions(double totalMassKg, double[] sourceMassFractions,
      double[] sourceSpecificGravities) {
    return new RefineryBlendBatch(totalMassKg, sourceMassFractions, sourceSpecificGravities, false, Double.NaN);
  }

  /**
   * Scale a qualified minimum-cost optimization result to an auditable batch receipt.
   *
   * @param totalMassKg requested positive total batch mass in kg
   * @param optimization qualified refinery linear-blend result
   * @param sourceSpecificGravities source specific gravities in optimization source order
   * @return immutable costed batch receipt
   * @throws NullPointerException if {@code optimization} is {@code null}
   * @throws IllegalArgumentException for invalid mass, arrays, fractions, gravities, or cost
   */
  public static RefineryBlendBatch fromOptimization(double totalMassKg,
      RefineryLinearBlendOptimizer.Result optimization, double[] sourceSpecificGravities) {
    if (optimization == null) {
      throw new NullPointerException("optimization");
    }
    return new RefineryBlendBatch(totalMassKg, optimization.getSourceMassFractions(), sourceSpecificGravities, true,
        optimization.getUnitCostPerMass());
  }

  /** @return requested total batch mass in kg */
  public double getTotalMassKg() {
    return totalMassKg;
  }

  /** @return defensive copy of normalized source mass fractions */
  public double[] getSourceMassFractions() {
    return Arrays.copyOf(sourceMassFractions, sourceMassFractions.length);
  }

  /** @return defensive copy of source masses in kg */
  public double[] getSourceMassesKg() {
    return Arrays.copyOf(sourceMassesKg, sourceMassesKg.length);
  }

  /** @return defensive copy of source specific gravities */
  public double[] getSourceSpecificGravities() {
    return Arrays.copyOf(sourceSpecificGravities, sourceSpecificGravities.length);
  }

  /** @return defensive copy of ideal-additive source volumes at 60 degrees Fahrenheit in m3 */
  public double[] getSourceVolumesM3At60F() {
    return Arrays.copyOf(sourceVolumesM3At60F, sourceVolumesM3At60F.length);
  }

  /** @return total ideal-additive source volume at 60 degrees Fahrenheit in m3 */
  public double getTotalAdditiveVolumeM3At60F() {
    return totalAdditiveVolumeM3At60F;
  }

  /** @return ideal-additive-volume batch specific gravity */
  public double getSpecificGravity() {
    return specificGravity;
  }

  /** @return whether optimizer cost evidence is available */
  public boolean hasCost() {
    return costAvailable;
  }

  /**
   * Return the optimizer unit cost.
   *
   * @return cost in the optimizer's common currency per mass unit
   * @throws IllegalStateException when the batch was not created from an optimization result
   */
  public double getUnitCostPerMass() {
    requireCost();
    return unitCostPerMass;
  }

  /**
   * Return total batch cost.
   *
   * @return total cost in the optimizer's common currency
   * @throws IllegalStateException when the batch was not created from an optimization result
   */
  public double getTotalCost() {
    requireCost();
    return totalCost;
  }

  private void requireCost() {
    if (!costAvailable) {
      throw new IllegalStateException("Blend batch does not contain optimizer cost evidence");
    }
  }
}
