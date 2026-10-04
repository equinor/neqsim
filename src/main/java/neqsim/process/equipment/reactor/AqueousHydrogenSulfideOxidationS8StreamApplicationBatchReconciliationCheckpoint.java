package neqsim.process.equipment.reactor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Creates deterministic integrity checkpoints for ordered S8 batch-reconciliation evidence.
 *
 * <p>
 * The checkpoint uses a versioned canonical binary encoding and SHA-256. It is an integrity fingerprint, not a digital
 * signature, authentication mechanism, transaction, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint {
  /** Digest algorithm used by reconciliation checkpoints. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-v1";

  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint() {
  }

  /**
   * Create a deterministic checkpoint for one qualified reconciliation result.
   *
   * @param reconciliation ordered preview-to-application reconciliation evidence
   * @return immutable checkpoint metadata and digest
   */
  public static Result create(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result validated = validate(reconciliation);
    return new Result(validated.getEntries().size(), validated.getStrictAppendCount(), validated.getUnchangedCount(),
        validated.getPlannedS8IncrementMol(), validated.getS8ResidualMol(), validated.getS8ToleranceMol(),
        validated.getTotalAmountResidualMol(), validated.getTotalAmountToleranceMol(), digest(validated));
  }

  /**
   * Verify reconciliation evidence against a stored checkpoint in constant time.
   *
   * @param reconciliation restored reconciliation evidence
   * @param checkpoint expected checkpoint
   * @return true only when the canonical reconciliation digest matches
   */
  public static boolean verify(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation, Result checkpoint) {
    if (checkpoint == null) {
      throw new IllegalArgumentException("S8 batch-reconciliation checkpoint is required");
    }
    byte[] actual = digest(validate(reconciliation));
    return MessageDigest.isEqual(actual, checkpoint.digest.clone());
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result validate(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation) {
    if (reconciliation == null) {
      throw new IllegalArgumentException("S8 batch reconciliation is required");
    }
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Entry> entries = reconciliation
        .getEntries();
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("S8 batch reconciliation cannot be empty");
    }
    if (reconciliation.getStrictAppendCount() < 0 || reconciliation.getUnchangedCount() < 0
        || reconciliation.getStrictAppendCount() + reconciliation.getUnchangedCount() != entries.size()) {
      throw new IllegalArgumentException("S8 batch reconciliation state counts are inconsistent");
    }

    double maximumS8ResidualMol = 0.0;
    double maximumTotalResidualMol = 0.0;
    for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Entry entry : entries) {
      if (entry == null) {
        throw new IllegalArgumentException("S8 batch reconciliation entries cannot be null");
      }
      requireText(entry.getTargetStateIdentifier(), "Target-state identifier");
      requireText(entry.getApplicationIdempotencyKey(), "Application idempotency key");
      requireText(entry.getTransitionDigestHex(), "Transition digest");
      requireNonNegativeFinite(entry.getPreviewCandidateS8AmountMol(), "Preview candidate S8 amount");
      requireNonNegativeFinite(entry.getApplicationCandidateS8AmountMol(), "Application candidate S8 amount");
      requireNonNegativeFinite(entry.getPreviewCandidateTotalAmountMol(), "Preview candidate total amount");
      requireNonNegativeFinite(entry.getApplicationCandidateTotalAmountMol(), "Application candidate total amount");
      requireResidual(entry.getCandidateS8ResidualMol(), entry.getCandidateS8ToleranceMol(), "Candidate S8 residual");
      requireResidual(entry.getCandidateTotalAmountResidualMol(), entry.getCandidateTotalAmountToleranceMol(),
          "Candidate total-amount residual");
      maximumS8ResidualMol = Math.max(maximumS8ResidualMol, Math.abs(entry.getCandidateS8ResidualMol()));
      maximumTotalResidualMol = Math.max(maximumTotalResidualMol, Math.abs(entry.getCandidateTotalAmountResidualMol()));
    }

    requireNonNegativeFinite(reconciliation.getPlannedS8IncrementMol(), "Planned S8 increment");
    requireNonNegativeFinite(reconciliation.getPreviewObservedS8IncrementMol(), "Preview observed S8 increment");
    requireNonNegativeFinite(reconciliation.getApplicationObservedS8IncrementMol(),
        "Application observed S8 increment");
    requireNonNegativeFinite(reconciliation.getPreviewObservedTotalIncrementMol(), "Preview observed total increment");
    requireNonNegativeFinite(reconciliation.getApplicationObservedTotalIncrementMol(),
        "Application observed total increment");
    requireResidual(reconciliation.getS8ResidualMol(), reconciliation.getS8ToleranceMol(), "Aggregate S8 residual");
    requireResidual(reconciliation.getTotalAmountResidualMol(), reconciliation.getTotalAmountToleranceMol(),
        "Aggregate total-amount residual");
    requireExactDouble(maximumS8ResidualMol, reconciliation.getMaximumEntryS8ResidualMol(),
        "Maximum entry S8 residual");
    requireExactDouble(maximumTotalResidualMol, reconciliation.getMaximumEntryTotalAmountResidualMol(),
        "Maximum entry total-amount residual");
    return reconciliation;
  }

  private static byte[] digest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Entry> entries = reconciliation
            .getEntries();
        output.writeInt(entries.size());
        output.writeInt(reconciliation.getStrictAppendCount());
        output.writeInt(reconciliation.getUnchangedCount());
        for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Entry entry : entries) {
          writeString(output, entry.getTargetStateIdentifier());
          writeString(output, entry.getApplicationIdempotencyKey());
          writeString(output, entry.getTransitionDigestHex());
          writeDouble(output, entry.getPreviewCandidateS8AmountMol());
          writeDouble(output, entry.getApplicationCandidateS8AmountMol());
          writeDouble(output, entry.getCandidateS8ResidualMol());
          writeDouble(output, entry.getCandidateS8ToleranceMol());
          writeDouble(output, entry.getPreviewCandidateTotalAmountMol());
          writeDouble(output, entry.getApplicationCandidateTotalAmountMol());
          writeDouble(output, entry.getCandidateTotalAmountResidualMol());
          writeDouble(output, entry.getCandidateTotalAmountToleranceMol());
        }
        writeDouble(output, reconciliation.getPlannedS8IncrementMol());
        writeDouble(output, reconciliation.getPreviewObservedS8IncrementMol());
        writeDouble(output, reconciliation.getApplicationObservedS8IncrementMol());
        writeDouble(output, reconciliation.getS8ResidualMol());
        writeDouble(output, reconciliation.getS8ToleranceMol());
        writeDouble(output, reconciliation.getPreviewObservedTotalIncrementMol());
        writeDouble(output, reconciliation.getApplicationObservedTotalIncrementMol());
        writeDouble(output, reconciliation.getTotalAmountResidualMol());
        writeDouble(output, reconciliation.getTotalAmountToleranceMol());
        writeDouble(output, reconciliation.getMaximumEntryS8ResidualMol());
        writeDouble(output, reconciliation.getMaximumEntryTotalAmountResidualMol());
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 batch-reconciliation checkpoint", exception);
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  private static void requireResidual(double residual, double tolerance, String name) {
    requireFinite(residual, name);
    requireNonNegativeFinite(tolerance, name + " tolerance");
    if (Math.abs(residual) > tolerance) {
      throw new IllegalArgumentException(name + " exceeds its tolerance");
    }
  }

  private static void requireExactDouble(double expected, double actual, String name) {
    requireNonNegativeFinite(actual, name);
    if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) {
      throw new IllegalArgumentException(name + " is inconsistent");
    }
  }

  private static void requireNonNegativeFinite(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(utf8.length);
    output.write(utf8);
  }

  private static void writeDouble(DataOutputStream output, double value) throws IOException {
    output.writeLong(Double.doubleToLongBits(value));
  }

  private static String toHex(byte[] bytes) {
    char[] hexadecimal = new char[bytes.length * 2];
    char[] digits = "0123456789abcdef".toCharArray();
    for (int index = 0; index < bytes.length; index++) {
      int value = bytes[index] & 0xff;
      hexadecimal[index * 2] = digits[value >>> 4];
      hexadecimal[index * 2 + 1] = digits[value & 0x0f];
    }
    return new String(hexadecimal);
  }

  /** Immutable checkpoint evidence for one ordered S8 batch reconciliation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int entryCount;
    private final int strictAppendCount;
    private final int unchangedCount;
    private final double plannedS8IncrementMol;
    private final double s8ResidualMol;
    private final double s8ToleranceMol;
    private final double totalAmountResidualMol;
    private final double totalAmountToleranceMol;
    private final byte[] digest;

    private Result(int entryCount, int strictAppendCount, int unchangedCount, double plannedS8IncrementMol,
        double s8ResidualMol, double s8ToleranceMol, double totalAmountResidualMol, double totalAmountToleranceMol,
        byte[] digest) {
      this.entryCount = entryCount;
      this.strictAppendCount = strictAppendCount;
      this.unchangedCount = unchangedCount;
      this.plannedS8IncrementMol = plannedS8IncrementMol;
      this.s8ResidualMol = s8ResidualMol;
      this.s8ToleranceMol = s8ToleranceMol;
      this.totalAmountResidualMol = totalAmountResidualMol;
      this.totalAmountToleranceMol = totalAmountToleranceMol;
      this.digest = digest.clone();
    }

    /** @return digest algorithm name. */
    public String getDigestAlgorithm() {
      return DIGEST_ALGORITHM;
    }

    /** @return versioned canonical-encoding identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return number of checkpointed ordered entries. */
    public int getEntryCount() {
      return entryCount;
    }

    /** @return number of strict-append entries. */
    public int getStrictAppendCount() {
      return strictAppendCount;
    }

    /** @return number of unchanged entries. */
    public int getUnchangedCount() {
      return unchangedCount;
    }

    /** @return aggregate planned S8 increment [mol]. */
    public double getPlannedS8IncrementMol() {
      return plannedS8IncrementMol;
    }

    /** @return aggregate preview-to-application S8 residual [mol]. */
    public double getS8ResidualMol() {
      return s8ResidualMol;
    }

    /** @return aggregate S8 residual tolerance [mol]. */
    public double getS8ToleranceMol() {
      return s8ToleranceMol;
    }

    /** @return aggregate preview-to-application total-amount residual [mol]. */
    public double getTotalAmountResidualMol() {
      return totalAmountResidualMol;
    }

    /** @return aggregate total-amount residual tolerance [mol]. */
    public double getTotalAmountToleranceMol() {
      return totalAmountToleranceMol;
    }

    /** @return lowercase hexadecimal SHA-256 fingerprint. */
    public String getDigestHex() {
      return toHex(digest);
    }

    /** @return defensive copy of the raw SHA-256 bytes. */
    public byte[] getDigestBytes() {
      return digest.clone();
    }
  }
}
