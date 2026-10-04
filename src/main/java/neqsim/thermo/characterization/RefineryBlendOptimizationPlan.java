package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable, label-addressable plan for one qualified refinery blend optimization.
 *
 * <p>
 * This class composes an existing {@link RefineryLinearBlendOptimizer.Result}, its scaled {@link RefineryBlendBatch},
 * and a {@link RefineryBlendSourceLedger}. It adds source-level cost receipts and closure checks without re-solving the
 * optimization or changing any qualified property calculation.
 * </p>
 */
public final class RefineryBlendOptimizationPlan implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double CLOSURE_TOLERANCE = 1.0e-12;

  private final RefineryLinearBlendOptimizer.Result optimization;
  private final RefineryBlendBatch batch;
  private final RefineryBlendSourceLedger sourceLedger;
  private final SourceCostReceipt[] sourceCostReceipts;

  private RefineryBlendOptimizationPlan(String[] sourceIdentifiers, double totalMassKg,
      double[] sourceSpecificGravities, double[] sourceCostsPerMass, RefineryLinearBlendOptimizer.Result optimization) {
    if (optimization == null) {
      throw new NullPointerException("optimization");
    }

    double[] massFractions = optimization.getSourceMassFractions();
    if (sourceCostsPerMass == null || sourceCostsPerMass.length != massFractions.length) {
      throw new IllegalArgumentException("Source costs must match the optimization source count");
    }

    batch = RefineryBlendBatch.fromOptimization(totalMassKg, optimization, sourceSpecificGravities);
    sourceLedger = RefineryBlendSourceLedger.fromBatch(sourceIdentifiers, batch);
    RefineryBlendSourceLedger.SourceReceipt[] sourceReceipts = sourceLedger.getSourceReceipts();
    sourceCostReceipts = new SourceCostReceipt[sourceReceipts.length];

    double resolvedUnitCostPerMass = 0.0;
    double resolvedTotalCost = 0.0;
    for (int i = 0; i < sourceReceipts.length; i++) {
      double sourceUnitCostPerMass = sourceCostsPerMass[i];
      if (!Double.isFinite(sourceUnitCostPerMass) || sourceUnitCostPerMass < 0.0) {
        throw new IllegalArgumentException("Source costs must be finite and non-negative");
      }
      double sourceTotalCost = sourceReceipts[i].getMassKg() * sourceUnitCostPerMass;
      if (!Double.isFinite(sourceTotalCost) || sourceTotalCost < 0.0) {
        throw new IllegalArgumentException("Source total costs must be finite and non-negative");
      }
      sourceCostReceipts[i] = new SourceCostReceipt(sourceReceipts[i], sourceUnitCostPerMass, sourceTotalCost);
      resolvedUnitCostPerMass += massFractions[i] * sourceUnitCostPerMass;
      resolvedTotalCost += sourceTotalCost;
    }

    requireClosure(resolvedUnitCostPerMass, optimization.getUnitCostPerMass(), "unit cost");
    requireClosure(resolvedUnitCostPerMass, batch.getUnitCostPerMass(), "batch unit cost");
    requireClosure(resolvedTotalCost, batch.getTotalCost(), "total cost");
    this.optimization = optimization;
  }

  /**
   * Compose one optimized recipe, scaled batch, labeled source ledger, and cost ledger.
   *
   * @param sourceIdentifiers unique nonblank identifiers in optimization source order
   * @param totalMassKg requested positive batch mass in kg
   * @param sourceSpecificGravities source specific gravities on the 60 degrees Fahrenheit basis
   * @param sourceCostsPerMass source costs in the optimizer's common currency/mass basis
   * @param optimization qualified refinery linear-blend result
   * @return immutable optimized blend plan
   * @throws NullPointerException if {@code optimization} is {@code null}
   * @throws IllegalArgumentException for invalid, mismatched, or cost-inconsistent inputs
   * @throws IllegalStateException if copied receipts do not close to the qualified batch
   */
  public static RefineryBlendOptimizationPlan fromOptimization(String[] sourceIdentifiers, double totalMassKg,
      double[] sourceSpecificGravities, double[] sourceCostsPerMass, RefineryLinearBlendOptimizer.Result optimization) {
    return new RefineryBlendOptimizationPlan(sourceIdentifiers, totalMassKg, sourceSpecificGravities,
        sourceCostsPerMass, optimization);
  }

  private static void requireClosure(double resolvedValue, double expectedValue, String quantity) {
    double tolerance = CLOSURE_TOLERANCE * Math.max(1.0, Math.max(Math.abs(resolvedValue), Math.abs(expectedValue)));
    if (!Double.isFinite(resolvedValue) || !Double.isFinite(expectedValue)
        || Math.abs(resolvedValue - expectedValue) > tolerance) {
      throw new IllegalArgumentException("Optimized blend plan " + quantity + " does not close");
    }
  }

  /** @return the qualified optimization result underlying this plan */
  public RefineryLinearBlendOptimizer.Result getOptimization() {
    return optimization;
  }

  /** @return the qualified scaled batch underlying this plan */
  public RefineryBlendBatch getBatch() {
    return batch;
  }

  /** @return the qualified label-addressable source ledger */
  public RefineryBlendSourceLedger getSourceLedger() {
    return sourceLedger;
  }

  /** @return the optimization's immutable quality-constraint receipt */
  public RefineryLinearBlendOptimizer.QualityConstraintReceipt getQualityConstraintReceipt() {
    return optimization.getQualityConstraintReceipt();
  }

  /** @return defensive copy of immutable source-cost receipts in source order */
  public SourceCostReceipt[] getSourceCostReceipts() {
    return Arrays.copyOf(sourceCostReceipts, sourceCostReceipts.length);
  }

  /**
   * Return one source-cost receipt by its exact caller identifier.
   *
   * @param sourceIdentifier exact source identifier
   * @return immutable source-cost receipt
   * @throws IllegalArgumentException if the identifier is null, blank, or absent
   */
  public SourceCostReceipt getSourceCostReceipt(String sourceIdentifier) {
    if (sourceIdentifier == null || sourceIdentifier.isEmpty()) {
      throw new IllegalArgumentException("Source identifier must be nonblank");
    }
    for (SourceCostReceipt receipt : sourceCostReceipts) {
      if (receipt.getSourceIdentifier().equals(sourceIdentifier)) {
        return receipt;
      }
    }
    throw new IllegalArgumentException("Unknown optimized blend source identifier: " + sourceIdentifier);
  }

  /** Immutable cost receipt for one labeled source in an optimized blend plan. */
  public static final class SourceCostReceipt implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final RefineryBlendSourceLedger.SourceReceipt sourceReceipt;
    private final double unitCostPerMass;
    private final double totalCost;

    private SourceCostReceipt(RefineryBlendSourceLedger.SourceReceipt sourceReceipt, double unitCostPerMass,
        double totalCost) {
      this.sourceReceipt = sourceReceipt;
      this.unitCostPerMass = unitCostPerMass;
      this.totalCost = totalCost;
    }

    /** @return qualified mass and ideal-additive-volume source receipt */
    public RefineryBlendSourceLedger.SourceReceipt getSourceReceipt() {
      return sourceReceipt;
    }

    /** @return zero-based source index in optimization order */
    public int getSourceIndex() {
      return sourceReceipt.getSourceIndex();
    }

    /** @return exact caller-supplied source identifier */
    public String getSourceIdentifier() {
      return sourceReceipt.getSourceIdentifier();
    }

    /** @return normalized source mass fraction */
    public double getMassFraction() {
      return sourceReceipt.getMassFraction();
    }

    /** @return source mass in kg */
    public double getMassKg() {
      return sourceReceipt.getMassKg();
    }

    /** @return source specific gravity on the batch's 60 degrees Fahrenheit basis */
    public double getSpecificGravity() {
      return sourceReceipt.getSpecificGravity();
    }

    /** @return ideal-additive source volume at 60 degrees Fahrenheit in m3 */
    public double getAdditiveVolumeM3At60F() {
      return sourceReceipt.getAdditiveVolumeM3At60F();
    }

    /** @return source cost in the optimizer's common currency/mass basis */
    public double getUnitCostPerMass() {
      return unitCostPerMass;
    }

    /** @return source total cost in the optimizer's common currency */
    public double getTotalCost() {
      return totalCost;
    }

    /** @return whether this source contributes positive mass to the batch */
    public boolean isContributing() {
      return sourceReceipt.isContributing();
    }
  }
}
