package neqsim.process.equipment.reactor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Links two checkpointed S8 transfer-ledger states as one exact-prefix transition receipt.
 *
 * <p>
 * The receipt combines the existing field-complete prefix reconciliation with canonical ledger checkpoints. It is
 * deterministic audit evidence only and does not persist a ledger, coordinate writers, mutate a process model, or
 * provide authentication, compare-and-swap, or exactly-once delivery.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8TransferLedgerTransition {
  /** Digest algorithm used by transition receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical transition-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-transfer-ledger-transition-v1";

  private AqueousHydrogenSulfideOxidationS8TransferLedgerTransition() {
  }

  /**
   * Create one deterministic transition receipt between exact-prefix ledger states.
   *
   * @param prior persisted prior ledger state
   * @param candidate unchanged or strict-append candidate state
   * @return immutable checkpoint-linked transition receipt
   * @throws IllegalArgumentException if either state is missing or the candidate is not an exact ordered extension
   */
  public static Result create(AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate) {
    AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.Result delta = AqueousHydrogenSulfideOxidationS8TransferLedgerDelta
        .reconcile(prior, candidate);
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result priorCheckpoint = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(prior);
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result candidateCheckpoint = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(candidate);

    byte[] transitionDigest = digest(priorCheckpoint, candidateCheckpoint, delta);
    return new Result(delta.getLedgerIdentifier(), delta.getProductIdentityBasisIdentifier(),
        priorCheckpoint.getDigestHex(), candidateCheckpoint.getDigestHex(), delta.isUnchanged(), delta.isStrictAppend(),
        delta.getAddedBatchCount(), delta.getAddedTransferCount(), delta.getSourceSulfurEquivalentMassDeltaKg(),
        delta.getTransferredS8MassDeltaKg(), delta.getUnallocatedSulfurEquivalentMassDeltaKg(),
        delta.getMassClosureResidualDeltaKg(), transitionDigest);
  }

  /**
   * Verify both ledger states and every transition field against a supplied receipt.
   *
   * @param prior persisted prior ledger state
   * @param candidate unchanged or strict-append candidate state
   * @param receipt expected transition receipt
   * @return true only when all metadata and the constant-time transition digest match
   */
  public static boolean verify(AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate, Result receipt) {
    if (receipt == null) {
      throw new IllegalArgumentException("S8 transfer ledger transition receipt is required");
    }
    Result expected = create(prior, candidate);
    return expected.ledgerIdentifier.equals(receipt.ledgerIdentifier)
        && expected.productIdentityBasisIdentifier.equals(receipt.productIdentityBasisIdentifier)
        && expected.priorCheckpointHex.equals(receipt.priorCheckpointHex)
        && expected.candidateCheckpointHex.equals(receipt.candidateCheckpointHex)
        && expected.unchanged == receipt.unchanged && expected.strictAppend == receipt.strictAppend
        && expected.addedBatchCount == receipt.addedBatchCount
        && expected.addedTransferCount == receipt.addedTransferCount
        && sameDouble(expected.sourceSulfurEquivalentMassDeltaKg, receipt.sourceSulfurEquivalentMassDeltaKg)
        && sameDouble(expected.transferredS8MassDeltaKg, receipt.transferredS8MassDeltaKg)
        && sameDouble(expected.unallocatedSulfurEquivalentMassDeltaKg, receipt.unallocatedSulfurEquivalentMassDeltaKg)
        && sameDouble(expected.massClosureResidualDeltaKg, receipt.massClosureResidualDeltaKg)
        && MessageDigest.isEqual(expected.transitionDigest, receipt.transitionDigest);
  }

  private static byte[] digest(AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result prior,
      AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result candidate,
      AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.Result delta) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, delta.getLedgerIdentifier());
        writeString(output, delta.getComponentName());
        writeString(output, delta.getProductIdentityBasisIdentifier());
        writeBytes(output, prior.getDigestBytes());
        writeBytes(output, candidate.getDigestBytes());
        output.writeBoolean(delta.isUnchanged());
        output.writeBoolean(delta.isStrictAppend());
        output.writeInt(delta.getAddedBatchCount());
        output.writeInt(delta.getAddedTransferCount());
        writeDouble(output, delta.getSourceSulfurEquivalentMassDeltaKg());
        writeDouble(output, delta.getTransferredS8MassDeltaKg());
        writeDouble(output, delta.getUnallocatedSulfurEquivalentMassDeltaKg());
        writeDouble(output, delta.getMassClosureResidualDeltaKg());
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 ledger transition receipt", exception);
    }
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(utf8.length);
    output.write(utf8);
  }

  private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
    output.writeInt(value.length);
    output.write(value);
  }

  private static void writeDouble(DataOutputStream output, double value) throws IOException {
    output.writeLong(Double.doubleToLongBits(value));
  }

  private static boolean sameDouble(double left, double right) {
    return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
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

  /** Immutable audit evidence linking two exact S8 transfer-ledger states. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final String priorCheckpointHex;
    private final String candidateCheckpointHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final int addedBatchCount;
    private final int addedTransferCount;
    private final double sourceSulfurEquivalentMassDeltaKg;
    private final double transferredS8MassDeltaKg;
    private final double unallocatedSulfurEquivalentMassDeltaKg;
    private final double massClosureResidualDeltaKg;
    private final byte[] transitionDigest;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier, String priorCheckpointHex,
        String candidateCheckpointHex, boolean unchanged, boolean strictAppend, int addedBatchCount,
        int addedTransferCount, double sourceSulfurEquivalentMassDeltaKg, double transferredS8MassDeltaKg,
        double unallocatedSulfurEquivalentMassDeltaKg, double massClosureResidualDeltaKg, byte[] transitionDigest) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.priorCheckpointHex = priorCheckpointHex;
      this.candidateCheckpointHex = candidateCheckpointHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.addedBatchCount = addedBatchCount;
      this.addedTransferCount = addedTransferCount;
      this.sourceSulfurEquivalentMassDeltaKg = sourceSulfurEquivalentMassDeltaKg;
      this.transferredS8MassDeltaKg = transferredS8MassDeltaKg;
      this.unallocatedSulfurEquivalentMassDeltaKg = unallocatedSulfurEquivalentMassDeltaKg;
      this.massClosureResidualDeltaKg = massClosureResidualDeltaKg;
      this.transitionDigest = transitionDigest.clone();
    }

    /** @return digest algorithm name. */
    public String getDigestAlgorithm() {
      return DIGEST_ALGORITHM;
    }

    /** @return versioned canonical transition-encoding identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return existing NeqSim component name represented by the ledger. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return shared caller-supplied S8 product-identity basis. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return canonical fingerprint of the prior ledger state. */
    public String getPriorCheckpointHex() {
      return priorCheckpointHex;
    }

    /** @return canonical fingerprint of the candidate ledger state. */
    public String getCandidateCheckpointHex() {
      return candidateCheckpointHex;
    }

    /** @return true when the two ledger states are exactly unchanged. */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when the candidate is a strict append of the prior ledger. */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return number of appended batches. */
    public int getAddedBatchCount() {
      return addedBatchCount;
    }

    /** @return number of appended transfer receipts. */
    public int getAddedTransferCount() {
      return addedTransferCount;
    }

    /** @return source sulfur-equivalent mass delta [kg S-equivalent]. */
    public double getSourceSulfurEquivalentMassDeltaKg() {
      return sourceSulfurEquivalentMassDeltaKg;
    }

    /** @return transferred S8 mass delta [kg]. */
    public double getTransferredS8MassDeltaKg() {
      return transferredS8MassDeltaKg;
    }

    /** @return unallocated sulfur-equivalent mass delta [kg S-equivalent]. */
    public double getUnallocatedSulfurEquivalentMassDeltaKg() {
      return unallocatedSulfurEquivalentMassDeltaKg;
    }

    /** @return cumulative closure-residual delta [kg]. */
    public double getMassClosureResidualDeltaKg() {
      return massClosureResidualDeltaKg;
    }

    /** @return lowercase hexadecimal SHA-256 transition fingerprint. */
    public String getTransitionDigestHex() {
      return toHex(transitionDigest);
    }

    /** @return defensive copy of the raw transition fingerprint bytes. */
    public byte[] getTransitionDigestBytes() {
      return transitionDigest.clone();
    }
  }
}
