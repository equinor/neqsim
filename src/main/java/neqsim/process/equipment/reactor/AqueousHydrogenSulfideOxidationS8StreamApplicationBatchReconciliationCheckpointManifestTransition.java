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
 * Links two S8 reconciliation-checkpoint manifests as one exact-prefix transition receipt.
 *
 * <p>
 * The receipt proves only that a candidate manifest is unchanged or is a strict ordered append of a prior manifest. It
 * is deterministic integrity evidence, not a persistence store, authentication mechanism, transaction coordinator,
 * compare-and-swap operation, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition {
  /** Digest algorithm used by transition receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical transition-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-v1";

  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition() {
  }

  /**
   * Create one deterministic transition receipt between exact-prefix manifest states.
   *
   * @param prior persisted prior manifest state
   * @param candidate unchanged or strict-append candidate state
   * @return immutable manifest transition receipt
   * @throws IllegalArgumentException if either state is missing or the candidate is not an exact ordered extension
   */
  public static Result create(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result candidate) {
    requireManifest(prior, "Prior");
    requireManifest(candidate, "Candidate");
    if (!prior.getManifestIdentifier().equals(candidate.getManifestIdentifier())) {
      throw new IllegalArgumentException("S8 reconciliation checkpoint manifest identifiers must match");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry> priorEntries = prior
        .getEntries();
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry> candidateEntries = candidate
        .getEntries();
    if (candidateEntries.size() < priorEntries.size()) {
      throw new IllegalArgumentException("Candidate S8 reconciliation checkpoint manifest is truncated");
    }
    for (int index = 0; index < priorEntries.size(); index++) {
      if (!sameEntry(priorEntries.get(index), candidateEntries.get(index))) {
        throw new IllegalArgumentException(
            "Candidate S8 reconciliation checkpoint manifest is not an exact ordered extension");
      }
    }

    int addedReconciliationCount = subtractCount(candidateEntries.size(), priorEntries.size(), "Reconciliation count");
    int addedEntryCount = subtractCount(candidate.getTotalEntryCount(), prior.getTotalEntryCount(),
        "Represented entry count");
    int addedStrictAppendCount = subtractCount(candidate.getTotalStrictAppendCount(), prior.getTotalStrictAppendCount(),
        "Strict-append count");
    int addedUnchangedCount = subtractCount(candidate.getTotalUnchangedCount(), prior.getTotalUnchangedCount(),
        "Unchanged count");
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Added state count") != addedEntryCount) {
      throw new IllegalArgumentException("Manifest transition checkpoint state-count deltas are inconsistent");
    }

    boolean unchanged = addedReconciliationCount == 0;
    boolean strictAppend = addedReconciliationCount > 0;
    byte[] transitionDigest = digest(prior, candidate, unchanged, strictAppend, addedReconciliationCount,
        addedEntryCount, addedStrictAppendCount, addedUnchangedCount);
    return new Result(prior.getManifestIdentifier(), prior.getDigestHex(), candidate.getDigestHex(), unchanged,
        strictAppend, addedReconciliationCount, addedEntryCount, addedStrictAppendCount, addedUnchangedCount,
        transitionDigest);
  }

  /**
   * Verify both manifest states and every transition field against a supplied receipt.
   *
   * @param prior persisted prior manifest state
   * @param candidate unchanged or strict-append candidate state
   * @param receipt expected transition receipt
   * @return true only when all metadata and the constant-time transition digest match
   */
  public static boolean verify(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result candidate,
      Result receipt) {
    if (receipt == null) {
      throw new IllegalArgumentException("S8 reconciliation checkpoint manifest transition receipt is required");
    }
    Result expected = create(prior, candidate);
    return expected.manifestIdentifier.equals(receipt.manifestIdentifier)
        && expected.priorManifestDigestHex.equals(receipt.priorManifestDigestHex)
        && expected.candidateManifestDigestHex.equals(receipt.candidateManifestDigestHex)
        && expected.unchanged == receipt.unchanged && expected.strictAppend == receipt.strictAppend
        && expected.addedReconciliationCount == receipt.addedReconciliationCount
        && expected.addedEntryCount == receipt.addedEntryCount
        && expected.addedStrictAppendCount == receipt.addedStrictAppendCount
        && expected.addedUnchangedCount == receipt.addedUnchangedCount
        && MessageDigest.isEqual(expected.transitionDigest, receipt.transitionDigest);
  }

  private static void requireManifest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest,
      String name) {
    if (manifest == null) {
      throw new IllegalArgumentException(name + " S8 reconciliation checkpoint manifest is required");
    }
  }

  private static boolean sameEntry(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry candidate) {
    return prior.getReconciliationIdentifier().equals(candidate.getReconciliationIdentifier())
        && MessageDigest.isEqual(prior.getCheckpoint().getDigestBytes(), candidate.getCheckpoint().getDigestBytes());
  }

  private static int subtractCount(int candidate, int prior, String name) {
    if (candidate < prior) {
      throw new IllegalArgumentException(name + " cannot decrease across a manifest transition");
    }
    return candidate - prior;
  }

  private static int addExact(int left, int right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
  }

  private static byte[] digest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result candidate,
      boolean unchanged, boolean strictAppend, int addedReconciliationCount, int addedEntryCount,
      int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, prior.getManifestIdentifier());
        writeBytes(output, prior.getDigestBytes());
        writeBytes(output, candidate.getDigestBytes());
        output.writeBoolean(unchanged);
        output.writeBoolean(strictAppend);
        output.writeInt(addedReconciliationCount);
        output.writeInt(addedEntryCount);
        output.writeInt(addedStrictAppendCount);
        output.writeInt(addedUnchangedCount);
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 manifest transition receipt", exception);
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

  /** Immutable exact-prefix manifest transition receipt. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String manifestIdentifier;
    private final String priorManifestDigestHex;
    private final String candidateManifestDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] transitionDigest;

    private Result(String manifestIdentifier, String priorManifestDigestHex, String candidateManifestDigestHex,
        boolean unchanged, boolean strictAppend, int addedReconciliationCount, int addedEntryCount,
        int addedStrictAppendCount, int addedUnchangedCount, byte[] transitionDigest) {
      this.manifestIdentifier = manifestIdentifier;
      this.priorManifestDigestHex = priorManifestDigestHex;
      this.candidateManifestDigestHex = candidateManifestDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.addedReconciliationCount = addedReconciliationCount;
      this.addedEntryCount = addedEntryCount;
      this.addedStrictAppendCount = addedStrictAppendCount;
      this.addedUnchangedCount = addedUnchangedCount;
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

    /** @return caller-defined manifest identity shared by both states. */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return canonical fingerprint of the prior manifest state. */
    public String getPriorManifestDigestHex() {
      return priorManifestDigestHex;
    }

    /** @return canonical fingerprint of the candidate manifest state. */
    public String getCandidateManifestDigestHex() {
      return candidateManifestDigestHex;
    }

    /** @return true when both manifest states are exactly unchanged. */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when the candidate is a strict append of the prior manifest. */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return number of appended reconciliation checkpoints. */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return number of newly represented stream-application entries. */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return number of newly represented strict-append entries. */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return number of newly represented unchanged entries. */
    public int getAddedUnchangedCount() {
      return addedUnchangedCount;
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
