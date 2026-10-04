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
 * Proves that one S8 reconciliation-checkpoint manifest-transition chain is unchanged or an ordered strict append of
 * another chain.
 *
 * <p>
 * This immutable receipt compares already-qualified transition chains. It does not revalidate transition receipts
 * against manifests and is not a durable ledger, authentication mechanism, transaction coordinator, compare-and-swap
 * operation, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition {
  /** Digest algorithm used by chain-transition receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical receipt-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition() {
  }

  /**
   * Create a deterministic receipt for an unchanged or strict-append chain transition.
   *
   * @param prior prior qualified transition chain
   * @param candidate candidate qualified transition chain
   * @return immutable chain-transition receipt
   * @throws IllegalArgumentException if either chain is invalid or the candidate is not an exact ordered continuation
   */
  public static Result create(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result candidate) {
    validateChain(prior, "Prior");
    validateChain(candidate, "Candidate");
    if (!prior.getChainIdentifier().equals(candidate.getChainIdentifier())) {
      throw new IllegalArgumentException("Transition-chain identifiers must match");
    }
    if (!prior.getManifestIdentifier().equals(candidate.getManifestIdentifier())) {
      throw new IllegalArgumentException("Transition-chain manifest identifiers must match");
    }
    if (candidate.getTransitionCount() < prior.getTransitionCount()) {
      throw new IllegalArgumentException("Candidate transition chain cannot truncate the prior chain");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> priorTransitions = prior
        .getTransitions();
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> candidateTransitions = candidate
        .getTransitions();
    for (int index = 0; index < priorTransitions.size(); index++) {
      if (!sameTransition(priorTransitions.get(index), candidateTransitions.get(index))) {
        throw new IllegalArgumentException("Candidate transition chain must preserve the exact ordered prior prefix");
      }
    }

    int addedTransitionCount = subtractExact(candidate.getTransitionCount(), prior.getTransitionCount(),
        "Added transition count");
    int addedStrictAppendTransitionCount = subtractExact(candidate.getStrictAppendTransitionCount(),
        prior.getStrictAppendTransitionCount(), "Added strict-append transition count");
    int addedUnchangedTransitionCount = subtractExact(candidate.getUnchangedTransitionCount(),
        prior.getUnchangedTransitionCount(), "Added unchanged transition count");
    int addedReconciliationCount = subtractExact(candidate.getAddedReconciliationCount(),
        prior.getAddedReconciliationCount(), "Added reconciliation count");
    int addedEntryCount = subtractExact(candidate.getAddedEntryCount(), prior.getAddedEntryCount(),
        "Added represented-entry count");
    int addedStrictAppendCount = subtractExact(candidate.getAddedStrictAppendCount(), prior.getAddedStrictAppendCount(),
        "Added strict-append entry count");
    int addedUnchangedCount = subtractExact(candidate.getAddedUnchangedCount(), prior.getAddedUnchangedCount(),
        "Added unchanged entry count");

    if (addExact(addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        "Added transition-state count") != addedTransitionCount) {
      throw new IllegalArgumentException("Transition-chain transition-count deltas are inconsistent");
    }
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Added entry-state count") != addedEntryCount) {
      throw new IllegalArgumentException("Transition-chain entry-count deltas are inconsistent");
    }

    boolean unchanged = addedTransitionCount == 0;
    boolean strictAppend = addedTransitionCount > 0;
    if (unchanged && !MessageDigest.isEqual(prior.getChainDigestBytes(), candidate.getChainDigestBytes())) {
      throw new IllegalArgumentException("Equal-length transition chains must be unchanged");
    }
    if (strictAppend && !prior.getFinalManifestDigestHex()
        .equals(candidateTransitions.get(priorTransitions.size()).getPriorManifestDigestHex())) {
      throw new IllegalArgumentException("Candidate transition chain does not continue the prior endpoint");
    }

    byte[] transitionDigest = digest(prior, candidate, unchanged, strictAppend, addedTransitionCount,
        addedStrictAppendTransitionCount, addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount,
        addedStrictAppendCount, addedUnchangedCount);
    return new Result(prior.getChainIdentifier(), prior.getManifestIdentifier(), prior.getChainDigestHex(),
        candidate.getChainDigestHex(), prior.getFinalManifestDigestHex(), candidate.getFinalManifestDigestHex(),
        unchanged, strictAppend, addedTransitionCount, addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        addedReconciliationCount, addedEntryCount, addedStrictAppendCount, addedUnchangedCount, transitionDigest);
  }

  /**
   * Verify two qualified chains against a stored chain-transition receipt.
   *
   * @param prior prior qualified transition chain
   * @param candidate candidate qualified transition chain
   * @param receipt expected chain-transition receipt
   * @return true only when all metadata and the constant-time receipt digest match
   */
  public static boolean verify(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result candidate,
      Result receipt) {
    if (receipt == null) {
      throw new IllegalArgumentException("S8 manifest transition-chain transition receipt is required");
    }
    Result expected = create(prior, candidate);
    return expected.chainIdentifier.equals(receipt.chainIdentifier)
        && expected.manifestIdentifier.equals(receipt.manifestIdentifier)
        && expected.priorChainDigestHex.equals(receipt.priorChainDigestHex)
        && expected.candidateChainDigestHex.equals(receipt.candidateChainDigestHex)
        && expected.priorFinalManifestDigestHex.equals(receipt.priorFinalManifestDigestHex)
        && expected.candidateFinalManifestDigestHex.equals(receipt.candidateFinalManifestDigestHex)
        && expected.unchanged == receipt.unchanged && expected.strictAppend == receipt.strictAppend
        && expected.addedTransitionCount == receipt.addedTransitionCount
        && expected.addedStrictAppendTransitionCount == receipt.addedStrictAppendTransitionCount
        && expected.addedUnchangedTransitionCount == receipt.addedUnchangedTransitionCount
        && expected.addedReconciliationCount == receipt.addedReconciliationCount
        && expected.addedEntryCount == receipt.addedEntryCount
        && expected.addedStrictAppendCount == receipt.addedStrictAppendCount
        && expected.addedUnchangedCount == receipt.addedUnchangedCount
        && MessageDigest.isEqual(expected.transitionDigest, receipt.transitionDigest);
  }

  /**
   * Validate one chain and its aggregate contract.
   *
   * @param chain transition chain to validate
   * @param label diagnostic label
   * @throws IllegalArgumentException if the chain is missing or inconsistent
   */
  private static void validateChain(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain,
      String label) {
    if (chain == null) {
      throw new IllegalArgumentException(label + " S8 manifest transition chain is required");
    }
    requireText(chain.getChainIdentifier(), label + " transition-chain identifier");
    requireText(chain.getManifestIdentifier(), label + " manifest identifier");
    if (!AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .verify(chain.getChainIdentifier(), chain.getTransitions(), chain)) {
      throw new IllegalArgumentException(label + " S8 manifest transition chain is inconsistent");
    }
  }

  /**
   * Compare two immutable manifest-transition receipts.
   *
   * @param left first transition receipt
   * @param right second transition receipt
   * @return true only when all metadata and raw digests match
   */
  private static boolean sameTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result left,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result right) {
    return left.getSchemaIdentifier().equals(right.getSchemaIdentifier())
        && left.getDigestAlgorithm().equals(right.getDigestAlgorithm())
        && left.getManifestIdentifier().equals(right.getManifestIdentifier())
        && left.getPriorManifestDigestHex().equals(right.getPriorManifestDigestHex())
        && left.getCandidateManifestDigestHex().equals(right.getCandidateManifestDigestHex())
        && left.isUnchanged() == right.isUnchanged() && left.isStrictAppend() == right.isStrictAppend()
        && left.getAddedReconciliationCount() == right.getAddedReconciliationCount()
        && left.getAddedEntryCount() == right.getAddedEntryCount()
        && left.getAddedStrictAppendCount() == right.getAddedStrictAppendCount()
        && left.getAddedUnchangedCount() == right.getAddedUnchangedCount()
        && MessageDigest.isEqual(left.getTransitionDigestBytes(), right.getTransitionDigestBytes());
  }

  /**
   * Subtract two counts with underflow and overflow detection.
   *
   * @param candidate candidate count
   * @param prior prior count
   * @param name delta name for diagnostics
   * @return exact non-negative difference
   * @throws IllegalArgumentException if the difference is negative or exceeds the supported range
   */
  private static int subtractExact(int candidate, int prior, String name) {
    final int difference;
    try {
      difference = Math.subtractExact(candidate, prior);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
    if (difference < 0) {
      throw new IllegalArgumentException(name + " cannot be negative");
    }
    return difference;
  }

  /**
   * Add two counts with overflow detection.
   *
   * @param left first count
   * @param right second count
   * @param name aggregate name for diagnostics
   * @return exact sum
   * @throws IllegalArgumentException if the sum exceeds the supported integer range
   */
  private static int addExact(int left, int right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
  }

  /**
   * Calculate the canonical chain-transition digest.
   *
   * @param prior prior qualified transition chain
   * @param candidate candidate qualified transition chain
   * @param unchanged true when the chains are identical
   * @param strictAppend true when the candidate strictly appends the prior
   * @param addedTransitionCount added transition count
   * @param addedStrictAppendTransitionCount added strict-append transition count
   * @param addedUnchangedTransitionCount added unchanged transition count
   * @param addedReconciliationCount added reconciliation count
   * @param addedEntryCount added represented-entry count
   * @param addedStrictAppendCount added strict-append entry count
   * @param addedUnchangedCount added unchanged entry count
   * @return canonical SHA-256 digest bytes
   */
  private static byte[] digest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result candidate,
      boolean unchanged, boolean strictAppend, int addedTransitionCount, int addedStrictAppendTransitionCount,
      int addedUnchangedTransitionCount, int addedReconciliationCount, int addedEntryCount, int addedStrictAppendCount,
      int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, prior.getChainIdentifier());
        writeString(output, prior.getManifestIdentifier());
        writeBytes(output, prior.getChainDigestBytes());
        writeBytes(output, candidate.getChainDigestBytes());
        writeString(output, prior.getFinalManifestDigestHex());
        writeString(output, candidate.getFinalManifestDigestHex());
        output.writeBoolean(unchanged);
        output.writeBoolean(strictAppend);
        output.writeInt(addedTransitionCount);
        output.writeInt(addedStrictAppendTransitionCount);
        output.writeInt(addedUnchangedTransitionCount);
        output.writeInt(addedReconciliationCount);
        output.writeInt(addedEntryCount);
        output.writeInt(addedStrictAppendCount);
        output.writeInt(addedUnchangedCount);
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 manifest transition-chain transition", exception);
    }
  }

  /**
   * Require non-blank text.
   *
   * @param value text value
   * @param name value name for diagnostics
   * @throws IllegalArgumentException if the value is blank
   */
  private static void requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  /**
   * Write one length-prefixed UTF-8 string.
   *
   * @param output canonical output stream
   * @param value string value
   * @throws IOException if canonical encoding fails
   */
  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(utf8.length);
    output.write(utf8);
  }

  /**
   * Write one length-prefixed byte array.
   *
   * @param output canonical output stream
   * @param value byte-array value
   * @throws IOException if canonical encoding fails
   */
  private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
    output.writeInt(value.length);
    output.write(value);
  }

  /**
   * Convert bytes to lowercase hexadecimal.
   *
   * @param bytes digest bytes
   * @return lowercase hexadecimal representation
   */
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

  /** Immutable transition receipt between two manifest-transition chains. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final String priorChainDigestHex;
    private final String candidateChainDigestHex;
    private final String priorFinalManifestDigestHex;
    private final String candidateFinalManifestDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final int addedTransitionCount;
    private final int addedStrictAppendTransitionCount;
    private final int addedUnchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] transitionDigest;

    /**
     * Create an immutable chain-transition receipt.
     *
     * @param chainIdentifier caller-defined chain identity
     * @param manifestIdentifier shared manifest identity
     * @param priorChainDigestHex prior chain digest
     * @param candidateChainDigestHex candidate chain digest
     * @param priorFinalManifestDigestHex prior final-manifest digest
     * @param candidateFinalManifestDigestHex candidate final-manifest digest
     * @param unchanged true when the chains are identical
     * @param strictAppend true when the candidate strictly appends the prior
     * @param addedTransitionCount added transition count
     * @param addedStrictAppendTransitionCount added strict-append transition count
     * @param addedUnchangedTransitionCount added unchanged transition count
     * @param addedReconciliationCount added reconciliation count
     * @param addedEntryCount added represented-entry count
     * @param addedStrictAppendCount added strict-append entry count
     * @param addedUnchangedCount added unchanged entry count
     * @param transitionDigest canonical transition digest
     */
    private Result(String chainIdentifier, String manifestIdentifier, String priorChainDigestHex,
        String candidateChainDigestHex, String priorFinalManifestDigestHex, String candidateFinalManifestDigestHex,
        boolean unchanged, boolean strictAppend, int addedTransitionCount, int addedStrictAppendTransitionCount,
        int addedUnchangedTransitionCount, int addedReconciliationCount, int addedEntryCount,
        int addedStrictAppendCount, int addedUnchangedCount, byte[] transitionDigest) {
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.priorChainDigestHex = priorChainDigestHex;
      this.candidateChainDigestHex = candidateChainDigestHex;
      this.priorFinalManifestDigestHex = priorFinalManifestDigestHex;
      this.candidateFinalManifestDigestHex = candidateFinalManifestDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.addedTransitionCount = addedTransitionCount;
      this.addedStrictAppendTransitionCount = addedStrictAppendTransitionCount;
      this.addedUnchangedTransitionCount = addedUnchangedTransitionCount;
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

    /** @return versioned canonical receipt-encoding identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return caller-defined chain identity. */
    public String getChainIdentifier() {
      return chainIdentifier;
    }

    /** @return shared caller-defined manifest identity. */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return lowercase hexadecimal prior-chain digest. */
    public String getPriorChainDigestHex() {
      return priorChainDigestHex;
    }

    /** @return lowercase hexadecimal candidate-chain digest. */
    public String getCandidateChainDigestHex() {
      return candidateChainDigestHex;
    }

    /** @return prior chain final-manifest digest. */
    public String getPriorFinalManifestDigestHex() {
      return priorFinalManifestDigestHex;
    }

    /** @return candidate chain final-manifest digest. */
    public String getCandidateFinalManifestDigestHex() {
      return candidateFinalManifestDigestHex;
    }

    /** @return true when the candidate chain is unchanged. */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when the candidate chain strictly appends the prior chain. */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return number of appended transition receipts. */
    public int getAddedTransitionCount() {
      return addedTransitionCount;
    }

    /** @return number of appended strict-append transition receipts. */
    public int getAddedStrictAppendTransitionCount() {
      return addedStrictAppendTransitionCount;
    }

    /** @return number of appended unchanged transition receipts. */
    public int getAddedUnchangedTransitionCount() {
      return addedUnchangedTransitionCount;
    }

    /** @return aggregate added reconciliation-checkpoint delta. */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return aggregate added represented-entry delta. */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return aggregate added strict-append entry delta. */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return aggregate added unchanged entry delta. */
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
