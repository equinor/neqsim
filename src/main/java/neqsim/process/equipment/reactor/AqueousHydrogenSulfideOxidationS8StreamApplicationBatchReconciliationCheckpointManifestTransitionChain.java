package neqsim.process.equipment.reactor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Binds ordered S8 reconciliation-checkpoint manifest transitions into one continuity chain.
 *
 * <p>
 * The chain proves adjacency between immutable transition receipts and aggregates their exact counts. It does not
 * revalidate receipts against underlying manifests and is not a durable ledger, authentication mechanism, transaction
 * coordinator, compare-and-swap operation, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain {
  /** Digest algorithm used by transition chains. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical chain-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain() {
  }

  /**
   * Create one deterministic continuity chain from ordered manifest-transition receipts.
   *
   * @param chainIdentifier caller-defined chain identity
   * @param transitions ordered manifest-transition receipts
   * @return immutable transition chain
   * @throws IllegalArgumentException if inputs are missing, duplicated, disconnected, or inconsistent
   */
  public static Result create(String chainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> transitions) {
    requireText(chainIdentifier, "Transition chain identifier");
    if (transitions == null || transitions.isEmpty()) {
      throw new IllegalArgumentException("S8 manifest transition chain cannot be empty");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> copy = new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result>(
        transitions.size());
    Set<String> transitionDigests = new HashSet<String>();
    String manifestIdentifier = null;
    String previousCandidateManifestDigestHex = null;
    int strictAppendTransitionCount = 0;
    int unchangedTransitionCount = 0;
    int addedReconciliationCount = 0;
    int addedEntryCount = 0;
    int addedStrictAppendCount = 0;
    int addedUnchangedCount = 0;

    for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result transition : transitions) {
      validateTransition(transition);
      if (manifestIdentifier == null) {
        manifestIdentifier = transition.getManifestIdentifier();
      } else if (!manifestIdentifier.equals(transition.getManifestIdentifier())) {
        throw new IllegalArgumentException("Manifest transition chain identifiers must match");
      }
      if (previousCandidateManifestDigestHex != null
          && !previousCandidateManifestDigestHex.equals(transition.getPriorManifestDigestHex())) {
        throw new IllegalArgumentException("Manifest transition chain contains a gap, fork, or reordering");
      }
      if (!transitionDigests.add(transition.getTransitionDigestHex())) {
        throw new IllegalArgumentException("Duplicate manifest transition receipt digest");
      }

      strictAppendTransitionCount = addExact(strictAppendTransitionCount, transition.isStrictAppend() ? 1 : 0,
          "Strict-append transition count");
      unchangedTransitionCount = addExact(unchangedTransitionCount, transition.isUnchanged() ? 1 : 0,
          "Unchanged transition count");
      addedReconciliationCount = addExact(addedReconciliationCount, transition.getAddedReconciliationCount(),
          "Added reconciliation count");
      addedEntryCount = addExact(addedEntryCount, transition.getAddedEntryCount(), "Added represented-entry count");
      addedStrictAppendCount = addExact(addedStrictAppendCount, transition.getAddedStrictAppendCount(),
          "Added strict-append entry count");
      addedUnchangedCount = addExact(addedUnchangedCount, transition.getAddedUnchangedCount(),
          "Added unchanged entry count");
      previousCandidateManifestDigestHex = transition.getCandidateManifestDigestHex();
      copy.add(transition);
    }

    if (addExact(strictAppendTransitionCount, unchangedTransitionCount, "Transition state count") != copy.size()) {
      throw new IllegalArgumentException("Manifest transition chain state counts are inconsistent");
    }
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Added entry-state count") != addedEntryCount) {
      throw new IllegalArgumentException("Manifest transition chain entry-count deltas are inconsistent");
    }

    String firstManifestDigestHex = copy.get(0).getPriorManifestDigestHex();
    String finalManifestDigestHex = copy.get(copy.size() - 1).getCandidateManifestDigestHex();
    byte[] chainDigest = digest(chainIdentifier, manifestIdentifier, copy, strictAppendTransitionCount,
        unchangedTransitionCount, addedReconciliationCount, addedEntryCount, addedStrictAppendCount,
        addedUnchangedCount);
    return new Result(chainIdentifier, manifestIdentifier, copy, firstManifestDigestHex, finalManifestDigestHex,
        strictAppendTransitionCount, unchangedTransitionCount, addedReconciliationCount, addedEntryCount,
        addedStrictAppendCount, addedUnchangedCount, chainDigest);
  }

  /**
   * Verify ordered transition receipts against a stored chain.
   *
   * @param chainIdentifier expected caller-defined chain identity
   * @param transitions restored ordered manifest-transition receipts
   * @param chain expected transition chain
   * @return true only when all metadata and the constant-time chain digest match
   */
  public static boolean verify(String chainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> transitions,
      Result chain) {
    if (chain == null) {
      throw new IllegalArgumentException("S8 manifest transition chain is required");
    }
    Result expected = create(chainIdentifier, transitions);
    return expected.chainIdentifier.equals(chain.chainIdentifier)
        && expected.manifestIdentifier.equals(chain.manifestIdentifier)
        && expected.firstManifestDigestHex.equals(chain.firstManifestDigestHex)
        && expected.finalManifestDigestHex.equals(chain.finalManifestDigestHex)
        && expected.transitions.size() == chain.transitions.size()
        && expected.strictAppendTransitionCount == chain.strictAppendTransitionCount
        && expected.unchangedTransitionCount == chain.unchangedTransitionCount
        && expected.addedReconciliationCount == chain.addedReconciliationCount
        && expected.addedEntryCount == chain.addedEntryCount
        && expected.addedStrictAppendCount == chain.addedStrictAppendCount
        && expected.addedUnchangedCount == chain.addedUnchangedCount
        && MessageDigest.isEqual(expected.chainDigest, chain.chainDigest);
  }

  /**
   * Validate one immutable transition receipt before chaining it.
   *
   * @param transition transition receipt to validate
   * @throws IllegalArgumentException if the receipt is missing or internally inconsistent
   */
  private static void validateTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result transition) {
    if (transition == null) {
      throw new IllegalArgumentException("S8 manifest transition chain entries cannot be null");
    }
    requireText(transition.getManifestIdentifier(), "Manifest identifier");
    boolean unchanged = transition.isUnchanged();
    boolean strictAppend = transition.isStrictAppend();
    if (unchanged == strictAppend) {
      throw new IllegalArgumentException("Manifest transition receipt must be unchanged or strict append");
    }
    requireNonNegative(transition.getAddedReconciliationCount(), "Added reconciliation count");
    requireNonNegative(transition.getAddedEntryCount(), "Added represented-entry count");
    requireNonNegative(transition.getAddedStrictAppendCount(), "Added strict-append entry count");
    requireNonNegative(transition.getAddedUnchangedCount(), "Added unchanged entry count");
    if (addExact(transition.getAddedStrictAppendCount(), transition.getAddedUnchangedCount(),
        "Transition added entry-state count") != transition.getAddedEntryCount()) {
      throw new IllegalArgumentException("Manifest transition receipt entry-count deltas are inconsistent");
    }
    if (unchanged) {
      if (!transition.getPriorManifestDigestHex().equals(transition.getCandidateManifestDigestHex())
          || transition.getAddedReconciliationCount() != 0 || transition.getAddedEntryCount() != 0) {
        throw new IllegalArgumentException("Unchanged manifest transition receipt contains a state delta");
      }
    } else if (transition.getPriorManifestDigestHex().equals(transition.getCandidateManifestDigestHex())
        || transition.getAddedReconciliationCount() == 0) {
      throw new IllegalArgumentException("Strict-append manifest transition receipt lacks an append delta");
    }
  }

  /**
   * Require a non-negative count.
   *
   * @param value count value
   * @param name count name for diagnostics
   * @throws IllegalArgumentException if the value is negative
   */
  private static void requireNonNegative(int value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " cannot be negative");
    }
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
   * Calculate the canonical chain digest.
   *
   * @param chainIdentifier caller-defined chain identity
   * @param manifestIdentifier shared manifest identity
   * @param transitions ordered transition receipts
   * @param strictAppendTransitionCount strict-append transition count
   * @param unchangedTransitionCount unchanged transition count
   * @param addedReconciliationCount aggregate added reconciliation count
   * @param addedEntryCount aggregate added represented-entry count
   * @param addedStrictAppendCount aggregate added strict-append entry count
   * @param addedUnchangedCount aggregate added unchanged entry count
   * @return canonical SHA-256 digest bytes
   */
  private static byte[] digest(String chainIdentifier, String manifestIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> transitions,
      int strictAppendTransitionCount, int unchangedTransitionCount, int addedReconciliationCount, int addedEntryCount,
      int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, chainIdentifier);
        writeString(output, manifestIdentifier);
        output.writeInt(transitions.size());
        for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result transition : transitions) {
          writeString(output, transition.getSchemaIdentifier());
          writeString(output, transition.getDigestAlgorithm());
          writeString(output, transition.getManifestIdentifier());
          writeString(output, transition.getPriorManifestDigestHex());
          writeString(output, transition.getCandidateManifestDigestHex());
          output.writeBoolean(transition.isUnchanged());
          output.writeBoolean(transition.isStrictAppend());
          output.writeInt(transition.getAddedReconciliationCount());
          output.writeInt(transition.getAddedEntryCount());
          output.writeInt(transition.getAddedStrictAppendCount());
          output.writeInt(transition.getAddedUnchangedCount());
          writeBytes(output, transition.getTransitionDigestBytes());
        }
        output.writeInt(strictAppendTransitionCount);
        output.writeInt(unchangedTransitionCount);
        output.writeInt(addedReconciliationCount);
        output.writeInt(addedEntryCount);
        output.writeInt(addedStrictAppendCount);
        output.writeInt(addedUnchangedCount);
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 manifest transition chain", exception);
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

  /** Immutable ordered manifest-transition chain. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> transitions;
    private final String firstManifestDigestHex;
    private final String finalManifestDigestHex;
    private final int strictAppendTransitionCount;
    private final int unchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] chainDigest;

    /**
     * Create an immutable transition-chain result.
     *
     * @param chainIdentifier caller-defined chain identity
     * @param manifestIdentifier shared manifest identity
     * @param transitions ordered transition receipts
     * @param firstManifestDigestHex first prior-manifest digest
     * @param finalManifestDigestHex final candidate-manifest digest
     * @param strictAppendTransitionCount strict-append transition count
     * @param unchangedTransitionCount unchanged transition count
     * @param addedReconciliationCount aggregate added reconciliation count
     * @param addedEntryCount aggregate added represented-entry count
     * @param addedStrictAppendCount aggregate added strict-append entry count
     * @param addedUnchangedCount aggregate added unchanged entry count
     * @param chainDigest canonical chain digest
     */
    private Result(String chainIdentifier, String manifestIdentifier,
        List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> transitions,
        String firstManifestDigestHex, String finalManifestDigestHex, int strictAppendTransitionCount,
        int unchangedTransitionCount, int addedReconciliationCount, int addedEntryCount, int addedStrictAppendCount,
        int addedUnchangedCount, byte[] chainDigest) {
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.transitions = Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result>(
              transitions));
      this.firstManifestDigestHex = firstManifestDigestHex;
      this.finalManifestDigestHex = finalManifestDigestHex;
      this.strictAppendTransitionCount = strictAppendTransitionCount;
      this.unchangedTransitionCount = unchangedTransitionCount;
      this.addedReconciliationCount = addedReconciliationCount;
      this.addedEntryCount = addedEntryCount;
      this.addedStrictAppendCount = addedStrictAppendCount;
      this.addedUnchangedCount = addedUnchangedCount;
      this.chainDigest = chainDigest.clone();
    }

    /** @return digest algorithm name. */
    public String getDigestAlgorithm() {
      return DIGEST_ALGORITHM;
    }

    /** @return versioned canonical chain-encoding identifier. */
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

    /** @return fresh unmodifiable ordered transition list. */
    public List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result> getTransitions() {
      return Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result>(
              transitions));
    }

    /** @return number of chained transition receipts. */
    public int getTransitionCount() {
      return transitions.size();
    }

    /** @return canonical digest of the first prior manifest. */
    public String getFirstManifestDigestHex() {
      return firstManifestDigestHex;
    }

    /** @return canonical digest of the final candidate manifest. */
    public String getFinalManifestDigestHex() {
      return finalManifestDigestHex;
    }

    /** @return number of strict-append transitions. */
    public int getStrictAppendTransitionCount() {
      return strictAppendTransitionCount;
    }

    /** @return number of unchanged transitions. */
    public int getUnchangedTransitionCount() {
      return unchangedTransitionCount;
    }

    /** @return aggregate number of appended reconciliation checkpoints. */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return aggregate number of newly represented stream-application entries. */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return aggregate number of newly represented strict-append entries. */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return aggregate number of newly represented unchanged entries. */
    public int getAddedUnchangedCount() {
      return addedUnchangedCount;
    }

    /** @return lowercase hexadecimal SHA-256 chain fingerprint. */
    public String getChainDigestHex() {
      return toHex(chainDigest);
    }

    /** @return defensive copy of the raw chain fingerprint bytes. */
    public byte[] getChainDigestBytes() {
      return chainDigest.clone();
    }
  }
}
