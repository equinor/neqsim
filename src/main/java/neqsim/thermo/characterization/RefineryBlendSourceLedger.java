package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, label-addressable source ledger for a qualified refinery blend batch.
 *
 * <p>
 * Source identifiers are caller metadata in the exact order of the batch arrays. This class copies the already-qualified
 * batch receipts; it does not infer source identity, alter blend calculations, or attest provenance.
 * </p>
 */
public final class RefineryBlendSourceLedger implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double CLOSURE_TOLERANCE = 1.0e-12;

  private final RefineryBlendBatch batch;
  private final SourceReceipt[] sourceReceipts;

  private RefineryBlendSourceLedger(String[] sourceIdentifiers, RefineryBlendBatch batch) {
    if (batch == null) {
      throw new NullPointerException("batch");
    }
    double[] massFractions = batch.getSourceMassFractions();
    if (sourceIdentifiers == null || sourceIdentifiers.length != massFractions.length) {
      throw new IllegalArgumentException("Source identifiers must match the batch source count");
    }

    double[] massesKg = batch.getSourceMassesKg();
    double[] specificGravities = batch.getSourceSpecificGravities();
    double[] volumesM3At60F = batch.getSourceVolumesM3At60F();
    sourceReceipts = new SourceReceipt[sourceIdentifiers.length];
    Set<String> uniqueIdentifiers = new HashSet<String>();

    double resolvedMassKg = 0.0;
    double resolvedVolumeM3At60F = 0.0;
    for (int i = 0; i < sourceIdentifiers.length; i++) {
      String identifier = sourceIdentifiers[i];
      if (identifier == null || identifier.isEmpty() || !identifier.equals(identifier.trim())) {
        throw new IllegalArgumentException("Source identifiers must be nonblank and have no surrounding whitespace");
      }
      if (!uniqueIdentifiers.add(identifier)) {
        throw new IllegalArgumentException("Source identifiers must be unique");
      }
      sourceReceipts[i] = new SourceReceipt(i, identifier, massFractions[i], massesKg[i], specificGravities[i],
          volumesM3At60F[i]);
      resolvedMassKg += massesKg[i];
      resolvedVolumeM3At60F += volumesM3At60F[i];
    }

    requireClosure(resolvedMassKg, batch.getTotalMassKg(), "mass");
    requireClosure(resolvedVolumeM3At60F, batch.getTotalAdditiveVolumeM3At60F(), "additive volume");
    this.batch = batch;
  }

  /**
   * Pair caller source identifiers with one qualified batch receipt.
   *
   * @param sourceIdentifiers unique nonblank identifiers in exact batch source order
   * @param batch qualified immutable blend batch
   * @return immutable label-addressable source ledger
   * @throws NullPointerException if {@code batch} is {@code null}
   * @throws IllegalArgumentException for invalid or mismatched source identifiers
   * @throws IllegalStateException if copied source receipts do not close to the batch
   */
  public static RefineryBlendSourceLedger fromBatch(String[] sourceIdentifiers, RefineryBlendBatch batch) {
    return new RefineryBlendSourceLedger(sourceIdentifiers, batch);
  }

  private static void requireClosure(double resolvedValue, double expectedValue, String quantity) {
    double tolerance = CLOSURE_TOLERANCE * Math.max(1.0, Math.abs(expectedValue));
    if (!Double.isFinite(resolvedValue) || Math.abs(resolvedValue - expectedValue) > tolerance) {
      throw new IllegalStateException("Blend source-ledger " + quantity + " does not close to the batch");
    }
  }

  /** @return the qualified immutable batch underlying this ledger */
  public RefineryBlendBatch getBatch() {
    return batch;
  }

  /** @return defensive copy of source identifiers in batch order */
  public String[] getSourceIdentifiers() {
    String[] identifiers = new String[sourceReceipts.length];
    for (int i = 0; i < sourceReceipts.length; i++) {
      identifiers[i] = sourceReceipts[i].getSourceIdentifier();
    }
    return identifiers;
  }

  /** @return defensive copy of immutable source receipts in batch order */
  public SourceReceipt[] getSourceReceipts() {
    return Arrays.copyOf(sourceReceipts, sourceReceipts.length);
  }

  /**
   * Return one source receipt by its exact caller identifier.
   *
   * @param sourceIdentifier exact source identifier
   * @return immutable source receipt
   * @throws IllegalArgumentException if the identifier is null, blank, or absent
   */
  public SourceReceipt getSourceReceipt(String sourceIdentifier) {
    if (sourceIdentifier == null || sourceIdentifier.isEmpty()) {
      throw new IllegalArgumentException("Source identifier must be nonblank");
    }
    for (SourceReceipt receipt : sourceReceipts) {
      if (receipt.getSourceIdentifier().equals(sourceIdentifier)) {
        return receipt;
      }
    }
    throw new IllegalArgumentException("Unknown blend source identifier: " + sourceIdentifier);
  }

  /** Immutable receipt for one source in a refinery blend batch. */
  public static final class SourceReceipt implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int sourceIndex;
    private final String sourceIdentifier;
    private final double massFraction;
    private final double massKg;
    private final double specificGravity;
    private final double additiveVolumeM3At60F;

    private SourceReceipt(int sourceIndex, String sourceIdentifier, double massFraction, double massKg,
        double specificGravity, double additiveVolumeM3At60F) {
      this.sourceIndex = sourceIndex;
      this.sourceIdentifier = sourceIdentifier;
      this.massFraction = massFraction;
      this.massKg = massKg;
      this.specificGravity = specificGravity;
      this.additiveVolumeM3At60F = additiveVolumeM3At60F;
    }

    /** @return zero-based source index in batch order */
    public int getSourceIndex() {
      return sourceIndex;
    }

    /** @return exact caller-supplied source identifier */
    public String getSourceIdentifier() {
      return sourceIdentifier;
    }

    /** @return normalized source mass fraction */
    public double getMassFraction() {
      return massFraction;
    }

    /** @return source mass in kg */
    public double getMassKg() {
      return massKg;
    }

    /**
     * Return the source specific gravity on the batch's 60 degrees Fahrenheit basis.
     *
     * @return specific gravity, or the caller's non-finite placeholder for a zero-contribution source
     */
    public double getSpecificGravity() {
      return specificGravity;
    }

    /** @return ideal-additive source volume at 60 degrees Fahrenheit in m3 */
    public double getAdditiveVolumeM3At60F() {
      return additiveVolumeM3At60F;
    }

    /** @return whether this source contributes positive mass to the batch */
    public boolean isContributing() {
      return massFraction > 0.0;
    }
  }
}
