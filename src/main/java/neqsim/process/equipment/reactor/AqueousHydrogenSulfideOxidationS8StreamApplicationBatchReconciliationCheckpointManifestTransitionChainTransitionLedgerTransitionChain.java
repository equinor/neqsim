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
 * Binds an ordered chain of qualified S8 transition-ledger transition receipts.
 *
 * <p>
 * This immutable evidence object validates identity, ledger-digest adjacency, chain and manifest endpoint continuity,
 * unique receipt digests, and exact aggregate counts. It does not replay or revalidate the underlying ledgers, chains,
 * manifests, reconciliations, or stream evidence.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain {
  /** Digest algorithm used by transition-chain receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical transition-chain encoding. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-transition-chain-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain() {
  }

  /**
   * Create an immutable ordered chain of qualified ledger-transition receipts.
   *
   * @param transitionChainIdentifier caller-owned transition-chain identity
   * @param transitions ordered qualified ledger-transition receipts
   * @return immutable transition-chain result
   * @throws IllegalArgumentException if identity, adjacency, uniqueness, or count gates fail
   */
  public static Result create(String transitionChainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> transitions) {
    requireText(transitionChainIdentifier, "Transition-chain identifier");
    if (transitions == null || transitions.isEmpty()) {
      throw new IllegalArgumentException("At least one S8 transition-ledger transition receipt is required");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> ordered = new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result>(
        transitions);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result first = ordered
        .get(0);
    validateReceipt(first, 0);

    String ledgerIdentifier = first.getLedgerIdentifier();
    String chainIdentifier = first.getChainIdentifier();
    String manifestIdentifier = first.getManifestIdentifier();
    Set<String> receiptDigests = new HashSet<String>();
    int strictAppendReceiptCount = 0;
    int unchangedReceiptCount = 0;
    int addedLedgerReceiptCount = 0;
    int addedTransitionCount = 0;
    int addedStrictAppendTransitionCount = 0;
    int addedUnchangedTransitionCount = 0;
    int addedReconciliationCount = 0;
    int addedEntryCount = 0;
    int addedStrictAppendCount = 0;
    int addedUnchangedCount = 0;

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result previous = null;
    for (int index = 0; index < ordered.size(); index++) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result receipt = ordered
          .get(index);
      validateReceipt(receipt, index);
      if (!ledgerIdentifier.equals(receipt.getLedgerIdentifier())
          || !chainIdentifier.equals(receipt.getChainIdentifier())
          || !manifestIdentifier.equals(receipt.getManifestIdentifier())) {
        throw new IllegalArgumentException("All transition-ledger receipts must retain identical identities");
      }
      if (!receiptDigests.add(receipt.getTransitionDigestHex())) {
        throw new IllegalArgumentException("Duplicate transition-ledger receipt digest is not allowed");
      }
      if (previous != null) {
        requireAdjacency(previous, receipt);
      }

      strictAppendReceiptCount = addExact(strictAppendReceiptCount, receipt.isStrictAppend() ? 1 : 0,
          "Strict-append receipt count");
      unchangedReceiptCount = addExact(unchangedReceiptCount, receipt.isUnchanged() ? 1 : 0, "Unchanged receipt count");
      addedLedgerReceiptCount = addExact(addedLedgerReceiptCount, receipt.getAddedReceiptCount(),
          "Added ledger-receipt count");
      addedTransitionCount = addExact(addedTransitionCount, receipt.getAddedTransitionCount(),
          "Added transition count");
      addedStrictAppendTransitionCount = addExact(addedStrictAppendTransitionCount,
          receipt.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
      addedUnchangedTransitionCount = addExact(addedUnchangedTransitionCount,
          receipt.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
      addedReconciliationCount = addExact(addedReconciliationCount, receipt.getAddedReconciliationCount(),
          "Added reconciliation count");
      addedEntryCount = addExact(addedEntryCount, receipt.getAddedEntryCount(), "Added represented-entry count");
      addedStrictAppendCount = addExact(addedStrictAppendCount, receipt.getAddedStrictAppendCount(),
          "Added strict-append entry count");
      addedUnchangedCount = addExact(addedUnchangedCount, receipt.getAddedUnchangedCount(),
          "Added unchanged entry count");
      previous = receipt;
    }

    if (addExact(strictAppendReceiptCount, unchangedReceiptCount, "Transition-ledger receipt state count") != ordered
        .size()) {
      throw new IllegalArgumentException("Transition-ledger receipt state counts are inconsistent");
    }
    if (addExact(addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        "Underlying transition state count") != addedTransitionCount) {
      throw new IllegalArgumentException("Underlying transition-count aggregates are inconsistent");
    }
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Represented-entry state count") != addedEntryCount) {
      throw new IllegalArgumentException("Represented-entry count aggregates are inconsistent");
    }

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result last = ordered
        .get(ordered.size() - 1);
    byte[] chainDigest = digest(transitionChainIdentifier, ordered, ledgerIdentifier, chainIdentifier,
        manifestIdentifier, strictAppendReceiptCount, unchangedReceiptCount, addedLedgerReceiptCount,
        addedTransitionCount, addedStrictAppendTransitionCount, addedUnchangedTransitionCount, addedReconciliationCount,
        addedEntryCount, addedStrictAppendCount, addedUnchangedCount);
    return new Result(transitionChainIdentifier, ledgerIdentifier, chainIdentifier, manifestIdentifier,
        first.getPriorLedgerDigestHex(), last.getCandidateLedgerDigestHex(),
        first.getPriorFinalCandidateChainDigestHex(), last.getCandidateFinalCandidateChainDigestHex(),
        first.getPriorFinalCandidateManifestDigestHex(), last.getCandidateFinalCandidateManifestDigestHex(), ordered,
        strictAppendReceiptCount, unchangedReceiptCount, addedLedgerReceiptCount, addedTransitionCount,
        addedStrictAppendTransitionCount, addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount,
        addedStrictAppendCount, addedUnchangedCount, chainDigest);
  }

  /**
   * Verify an ordered receipt list against a stored transition-chain result.
   *
   * @param transitionChainIdentifier caller-owned transition-chain identity
   * @param transitions ordered qualified ledger-transition receipts
   * @param result expected transition-chain result
   * @return true only when metadata, counts, ordered receipts, and raw digest match
   */
  public static boolean verify(String transitionChainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> transitions,
      Result result) {
    if (result == null) {
      throw new IllegalArgumentException("S8 transition-ledger transition chain is required");
    }
    Result expected = create(transitionChainIdentifier, transitions);
    return expected.transitionChainIdentifier.equals(result.transitionChainIdentifier)
        && expected.ledgerIdentifier.equals(result.ledgerIdentifier)
        && expected.chainIdentifier.equals(result.chainIdentifier)
        && expected.manifestIdentifier.equals(result.manifestIdentifier)
        && expected.firstPriorLedgerDigestHex.equals(result.firstPriorLedgerDigestHex)
        && expected.finalCandidateLedgerDigestHex.equals(result.finalCandidateLedgerDigestHex)
        && expected.firstPriorChainDigestHex.equals(result.firstPriorChainDigestHex)
        && expected.finalCandidateChainDigestHex.equals(result.finalCandidateChainDigestHex)
        && expected.firstPriorManifestDigestHex.equals(result.firstPriorManifestDigestHex)
        && expected.finalCandidateManifestDigestHex.equals(result.finalCandidateManifestDigestHex)
        && expected.transitionCount == result.transitionCount
        && expected.strictAppendReceiptCount == result.strictAppendReceiptCount
        && expected.unchangedReceiptCount == result.unchangedReceiptCount
        && expected.addedLedgerReceiptCount == result.addedLedgerReceiptCount
        && expected.addedTransitionCount == result.addedTransitionCount
        && expected.addedStrictAppendTransitionCount == result.addedStrictAppendTransitionCount
        && expected.addedUnchangedTransitionCount == result.addedUnchangedTransitionCount
        && expected.addedReconciliationCount == result.addedReconciliationCount
        && expected.addedEntryCount == result.addedEntryCount
        && expected.addedStrictAppendCount == result.addedStrictAppendCount
        && expected.addedUnchangedCount == result.addedUnchangedCount
        && sameOrderedReceipts(expected.transitions, result.transitions)
        && MessageDigest.isEqual(expected.chainDigest, result.chainDigest);
  }

  /** Validate one already-qualified ledger-transition receipt. */
  private static void validateReceipt(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result receipt,
      int index) {
    if (receipt == null) {
      throw new IllegalArgumentException("Transition-ledger receipt at index " + index + " cannot be null");
    }
    requireText(receipt.getLedgerIdentifier(), "Ledger identifier");
    requireText(receipt.getChainIdentifier(), "Chain identifier");
    requireText(receipt.getManifestIdentifier(), "Manifest identifier");
    requireText(receipt.getPriorLedgerDigestHex(), "Prior ledger digest");
    requireText(receipt.getCandidateLedgerDigestHex(), "Candidate ledger digest");
    requireText(receipt.getPriorFinalCandidateChainDigestHex(), "Prior chain endpoint");
    requireText(receipt.getCandidateFinalCandidateChainDigestHex(), "Candidate chain endpoint");
    requireText(receipt.getPriorFinalCandidateManifestDigestHex(), "Prior manifest endpoint");
    requireText(receipt.getCandidateFinalCandidateManifestDigestHex(), "Candidate manifest endpoint");
    requireText(receipt.getTransitionDigestHex(), "Transition-ledger receipt digest");
    if (!AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.DIGEST_ALGORITHM
        .equals(receipt.getDigestAlgorithm())
        || !AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.SCHEMA_IDENTIFIER
            .equals(receipt.getSchemaIdentifier())) {
      throw new IllegalArgumentException("Transition-ledger receipt encoding is not supported");
    }
    if (receipt.isStrictAppend() == receipt.isUnchanged()) {
      throw new IllegalArgumentException("Each transition-ledger receipt must have exactly one state");
    }
    requireNonNegative(receipt.getAddedReceiptCount(), "Added ledger-receipt count");
    requireNonNegative(receipt.getAddedTransitionCount(), "Added transition count");
    requireNonNegative(receipt.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
    requireNonNegative(receipt.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
    requireNonNegative(receipt.getAddedReconciliationCount(), "Added reconciliation count");
    requireNonNegative(receipt.getAddedEntryCount(), "Added represented-entry count");
    requireNonNegative(receipt.getAddedStrictAppendCount(), "Added strict-append entry count");
    requireNonNegative(receipt.getAddedUnchangedCount(), "Added unchanged entry count");
    if (addExact(receipt.getAddedStrictAppendTransitionCount(), receipt.getAddedUnchangedTransitionCount(),
        "Receipt transition state count") != receipt.getAddedTransitionCount()) {
      throw new IllegalArgumentException("Transition-ledger receipt transition counts are inconsistent");
    }
    if (addExact(receipt.getAddedStrictAppendCount(), receipt.getAddedUnchangedCount(),
        "Receipt represented-entry state count") != receipt.getAddedEntryCount()) {
      throw new IllegalArgumentException("Transition-ledger receipt entry counts are inconsistent");
    }
    if (receipt.getTransitionDigestBytes().length != 32) {
      throw new IllegalArgumentException("Transition-ledger receipt digest must contain 32 bytes");
    }
  }

  /** Require exact adjacency between two ledger-transition receipts. */
  private static void requireAdjacency(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result previous,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result next) {
    if (!previous.getCandidateLedgerDigestHex().equals(next.getPriorLedgerDigestHex())) {
      throw new IllegalArgumentException("Transition-ledger receipts contain a ledger gap or fork");
    }
    if (!previous.getCandidateFinalCandidateChainDigestHex().equals(next.getPriorFinalCandidateChainDigestHex())) {
      throw new IllegalArgumentException("Transition-ledger receipts contain a chain-endpoint gap or fork");
    }
    if (!previous.getCandidateFinalCandidateManifestDigestHex()
        .equals(next.getPriorFinalCandidateManifestDigestHex())) {
      throw new IllegalArgumentException("Transition-ledger receipts contain a manifest-endpoint gap or fork");
    }
  }

  /** Compare two ordered receipt lists by raw receipt digest. */
  private static boolean sameOrderedReceipts(
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> left,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      if (!MessageDigest.isEqual(left.get(index).getTransitionDigestBytes(),
          right.get(index).getTransitionDigestBytes())) {
        return false;
      }
    }
    return true;
  }

  /** Calculate the canonical transition-chain digest. */
  private static byte[] digest(String transitionChainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> transitions,
      String ledgerIdentifier, String chainIdentifier, String manifestIdentifier, int strictAppendReceiptCount,
      int unchangedReceiptCount, int addedLedgerReceiptCount, int addedTransitionCount,
      int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount, int addedReconciliationCount,
      int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, transitionChainIdentifier);
        writeString(output, ledgerIdentifier);
        writeString(output, chainIdentifier);
        writeString(output, manifestIdentifier);
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result first = transitions
            .get(0);
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result last = transitions
            .get(transitions.size() - 1);
        writeString(output, first.getPriorLedgerDigestHex());
        writeString(output, last.getCandidateLedgerDigestHex());
        writeString(output, first.getPriorFinalCandidateChainDigestHex());
        writeString(output, last.getCandidateFinalCandidateChainDigestHex());
        writeString(output, first.getPriorFinalCandidateManifestDigestHex());
        writeString(output, last.getCandidateFinalCandidateManifestDigestHex());
        output.writeInt(transitions.size());
        for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result transition : transitions) {
          writeBytes(output, transition.getTransitionDigestBytes());
        }
        output.writeInt(strictAppendReceiptCount);
        output.writeInt(unchangedReceiptCount);
        output.writeInt(addedLedgerReceiptCount);
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
      throw new IllegalStateException("Unable to encode the S8 transition-ledger transition chain", exception);
    }
  }

  /** Add counts with overflow detection. */
  private static int addExact(int left, int right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
  }

  /** Require a non-negative count. */
  private static void requireNonNegative(int value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " cannot be negative");
    }
  }

  /** Require non-blank text. */
  private static void requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  /** Write one length-prefixed UTF-8 string. */
  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(utf8.length);
    output.write(utf8);
  }

  /** Write one length-prefixed byte array. */
  private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
    output.writeInt(value.length);
    output.write(value);
  }

  /** Convert bytes to lowercase hexadecimal. */
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

  /** Immutable ordered transition-ledger transition chain. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String transitionChainIdentifier;
    private final String ledgerIdentifier;
    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final String firstPriorLedgerDigestHex;
    private final String finalCandidateLedgerDigestHex;
    private final String firstPriorChainDigestHex;
    private final String finalCandidateChainDigestHex;
    private final String firstPriorManifestDigestHex;
    private final String finalCandidateManifestDigestHex;
    private final List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> transitions;
    private final int transitionCount;
    private final int strictAppendReceiptCount;
    private final int unchangedReceiptCount;
    private final int addedLedgerReceiptCount;
    private final int addedTransitionCount;
    private final int addedStrictAppendTransitionCount;
    private final int addedUnchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] chainDigest;

    /** Create an immutable transition chain. */
    private Result(String transitionChainIdentifier, String ledgerIdentifier, String chainIdentifier,
        String manifestIdentifier, String firstPriorLedgerDigestHex, String finalCandidateLedgerDigestHex,
        String firstPriorChainDigestHex, String finalCandidateChainDigestHex, String firstPriorManifestDigestHex,
        String finalCandidateManifestDigestHex,
        List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> transitions,
        int strictAppendReceiptCount, int unchangedReceiptCount, int addedLedgerReceiptCount, int addedTransitionCount,
        int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount, int addedReconciliationCount,
        int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount, byte[] chainDigest) {
      this.transitionChainIdentifier = transitionChainIdentifier;
      this.ledgerIdentifier = ledgerIdentifier;
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.firstPriorLedgerDigestHex = firstPriorLedgerDigestHex;
      this.finalCandidateLedgerDigestHex = finalCandidateLedgerDigestHex;
      this.firstPriorChainDigestHex = firstPriorChainDigestHex;
      this.finalCandidateChainDigestHex = finalCandidateChainDigestHex;
      this.firstPriorManifestDigestHex = firstPriorManifestDigestHex;
      this.finalCandidateManifestDigestHex = finalCandidateManifestDigestHex;
      this.transitions = Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result>(
              transitions));
      this.transitionCount = transitions.size();
      this.strictAppendReceiptCount = strictAppendReceiptCount;
      this.unchangedReceiptCount = unchangedReceiptCount;
      this.addedLedgerReceiptCount = addedLedgerReceiptCount;
      this.addedTransitionCount = addedTransitionCount;
      this.addedStrictAppendTransitionCount = addedStrictAppendTransitionCount;
      this.addedUnchangedTransitionCount = addedUnchangedTransitionCount;
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

    /** @return versioned canonical encoding identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return caller-owned transition-chain identity. */
    public String getTransitionChainIdentifier() {
      return transitionChainIdentifier;
    }

    /** @return inherited ledger identity. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return inherited chain identity. */
    public String getChainIdentifier() {
      return chainIdentifier;
    }

    /** @return inherited manifest identity. */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return first prior-ledger digest. */
    public String getFirstPriorLedgerDigestHex() {
      return firstPriorLedgerDigestHex;
    }

    /** @return final candidate-ledger digest. */
    public String getFinalCandidateLedgerDigestHex() {
      return finalCandidateLedgerDigestHex;
    }

    /** @return first prior chain endpoint. */
    public String getFirstPriorChainDigestHex() {
      return firstPriorChainDigestHex;
    }

    /** @return final candidate chain endpoint. */
    public String getFinalCandidateChainDigestHex() {
      return finalCandidateChainDigestHex;
    }

    /** @return first prior manifest endpoint. */
    public String getFirstPriorManifestDigestHex() {
      return firstPriorManifestDigestHex;
    }

    /** @return final candidate manifest endpoint. */
    public String getFinalCandidateManifestDigestHex() {
      return finalCandidateManifestDigestHex;
    }

    /** @return immutable ordered receipt list. */
    public List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> getTransitions() {
      return transitions;
    }

    /** @return receipt count. */
    public int getTransitionCount() {
      return transitionCount;
    }

    /** @return strict-append receipt count. */
    public int getStrictAppendReceiptCount() {
      return strictAppendReceiptCount;
    }

    /** @return unchanged receipt count. */
    public int getUnchangedReceiptCount() {
      return unchangedReceiptCount;
    }

    /** @return aggregate appended ledger-receipt count. */
    public int getAddedLedgerReceiptCount() {
      return addedLedgerReceiptCount;
    }

    /** @return aggregate appended underlying transition count. */
    public int getAddedTransitionCount() {
      return addedTransitionCount;
    }

    /** @return aggregate appended strict-append transition count. */
    public int getAddedStrictAppendTransitionCount() {
      return addedStrictAppendTransitionCount;
    }

    /** @return aggregate appended unchanged transition count. */
    public int getAddedUnchangedTransitionCount() {
      return addedUnchangedTransitionCount;
    }

    /** @return aggregate appended reconciliation count. */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return aggregate appended represented-entry count. */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return aggregate appended strict-append entry count. */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return aggregate appended unchanged entry count. */
    public int getAddedUnchangedCount() {
      return addedUnchangedCount;
    }

    /** @return lowercase hexadecimal SHA-256 chain digest. */
    public String getChainDigestHex() {
      return toHex(chainDigest);
    }

    /** @return defensive copy of raw chain digest bytes. */
    public byte[] getChainDigestBytes() {
      return chainDigest.clone();
    }
  }
}
