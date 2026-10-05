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
 * Binds an ordered chain of qualified S8 checkpoint-transition-chain checkpoint-transition receipts.
 *
 * <p>
 * This immutable evidence object validates checkpoint sequence and digest adjacency, transition-chain and endpoint
 * continuity, unique receipt digests, and exact aggregate counts. It does not replay or revalidate the underlying
 * transition chains, ledgers, manifests, reconciliations, or stream evidence.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain {
  /** Digest algorithm used by checkpoint-transition-chain receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical checkpoint-transition-chain checkpoint-transition-chain encoding. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-transition-chain-checkpoint-transition-chain-checkpoint-transition-chain-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain() {
  }

  /**
   * Create an immutable ordered chain of qualified checkpoint-transition-chain checkpoint-transition receipts.
   *
   * @param checkpointTransitionChainCheckpointTransitionChainIdentifier caller-owned checkpoint-transition-chain
   * checkpoint-transition-chain identity
   * @param transitions ordered qualified checkpoint-transition-chain checkpoint-transition receipts
   * @return immutable checkpoint-transition-chain checkpoint-transition-chain result
   * @throws IllegalArgumentException if identity, adjacency, uniqueness, or count gates fail
   */
  public static Result create(String checkpointTransitionChainCheckpointTransitionChainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> transitions) {
    requireText(checkpointTransitionChainCheckpointTransitionChainIdentifier, "Checkpoint-transition-chain identifier");
    if (transitions == null || transitions.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one S8 checkpoint-transition-chain checkpoint-transition receipt is required");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> ordered = new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result>(
        transitions);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result first = ordered
        .get(0);
    validateReceipt(first, 0);

    String checkpointTransitionChainCheckpointIdentifier = first.getCheckpointTransitionChainCheckpointIdentifier();
    String checkpointTransitionChainIdentifier = first.getCheckpointTransitionChainIdentifier();
    String ledgerIdentifier = first.getLedgerIdentifier();
    String chainIdentifier = first.getChainIdentifier();
    String manifestIdentifier = first.getManifestIdentifier();
    Set<String> receiptDigests = new HashSet<String>();
    int strictAppendReceiptCount = 0;
    int unchangedReceiptCount = 0;
    long totalSequenceDelta = 0L;
    int addedReceiptCount = 0;
    int addedLedgerReceiptCount = 0;
    int addedTransitionCount = 0;
    int addedStrictAppendTransitionCount = 0;
    int addedUnchangedTransitionCount = 0;
    int addedReconciliationCount = 0;
    int addedEntryCount = 0;
    int addedStrictAppendCount = 0;
    int addedUnchangedCount = 0;

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result previous = null;
    for (int index = 0; index < ordered.size(); index++) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result receipt = ordered
          .get(index);
      validateReceipt(receipt, index);
      if (!checkpointTransitionChainCheckpointIdentifier
          .equals(receipt.getCheckpointTransitionChainCheckpointIdentifier())
          || !checkpointTransitionChainIdentifier.equals(receipt.getCheckpointTransitionChainIdentifier())
          || !ledgerIdentifier.equals(receipt.getLedgerIdentifier())
          || !chainIdentifier.equals(receipt.getChainIdentifier())
          || !manifestIdentifier.equals(receipt.getManifestIdentifier())) {
        throw new IllegalArgumentException(
            "All checkpoint-transition-chain checkpoint-transition receipts must retain identical identities");
      }
      if (!receiptDigests.add(receipt.getTransitionDigestHex())) {
        throw new IllegalArgumentException(
            "Duplicate checkpoint-transition-chain checkpoint-transition receipt digest is not allowed");
      }
      if (previous != null) {
        requireAdjacency(previous, receipt);
      }

      strictAppendReceiptCount = addExact(strictAppendReceiptCount, receipt.isStrictAppend() ? 1 : 0,
          "Strict-append receipt count");
      unchangedReceiptCount = addExact(unchangedReceiptCount, receipt.isUnchanged() ? 1 : 0, "Unchanged receipt count");
      totalSequenceDelta = addExact(totalSequenceDelta, receipt.getSequenceDelta(), "Checkpoint-sequence delta");
      addedReceiptCount = addExact(addedReceiptCount, receipt.getAddedReceiptCount(), "Added receipt count");
      addedLedgerReceiptCount = addExact(addedLedgerReceiptCount, receipt.getAddedLedgerReceiptCount(),
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

    if (addExact(strictAppendReceiptCount, unchangedReceiptCount,
        "Checkpoint-transition receipt state count") != ordered.size()) {
      throw new IllegalArgumentException("Checkpoint-transition receipt state counts are inconsistent");
    }
    if (addExact(addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        "Underlying transition state count") != addedTransitionCount) {
      throw new IllegalArgumentException("Underlying transition-count aggregates are inconsistent");
    }
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Represented-entry state count") != addedEntryCount) {
      throw new IllegalArgumentException("Represented-entry count aggregates are inconsistent");
    }

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result last = ordered
        .get(ordered.size() - 1);
    byte[] chainDigest = digest(checkpointTransitionChainCheckpointTransitionChainIdentifier, ordered,
        checkpointTransitionChainCheckpointIdentifier, checkpointTransitionChainIdentifier, ledgerIdentifier,
        chainIdentifier, manifestIdentifier, totalSequenceDelta, strictAppendReceiptCount, unchangedReceiptCount,
        addedReceiptCount, addedLedgerReceiptCount, addedTransitionCount, addedStrictAppendTransitionCount,
        addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount, addedStrictAppendCount,
        addedUnchangedCount);
    return new Result(checkpointTransitionChainCheckpointTransitionChainIdentifier,
        checkpointTransitionChainCheckpointIdentifier, checkpointTransitionChainIdentifier, ledgerIdentifier,
        chainIdentifier, manifestIdentifier, first.getPriorCheckpointSequence(), last.getCandidateCheckpointSequence(),
        totalSequenceDelta, first.getPriorCheckpointDigestBytes(), last.getCandidateCheckpointDigestBytes(),
        first.getPriorChainDigestBytes(), last.getCandidateChainDigestBytes(), first.getPriorFinalLedgerDigestHex(),
        last.getCandidateFinalLedgerDigestHex(), first.getPriorFinalChainDigestHex(),
        last.getCandidateFinalChainDigestHex(), first.getPriorFinalManifestDigestHex(),
        last.getCandidateFinalManifestDigestHex(), ordered, strictAppendReceiptCount, unchangedReceiptCount,
        addedReceiptCount, addedLedgerReceiptCount, addedTransitionCount, addedStrictAppendTransitionCount,
        addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount, addedStrictAppendCount,
        addedUnchangedCount, chainDigest);
  }

  /**
   * Verify an ordered receipt list against a stored transition-chain result.
   *
   * @param checkpointTransitionChainCheckpointTransitionChainIdentifier caller-owned checkpoint-transition-chain
   * identity
   * @param transitions ordered qualified ledger-transition receipts
   * @param result expected transition-chain result
   * @return true only when metadata, counts, ordered receipts, and raw digest match
   */
  public static boolean verify(String checkpointTransitionChainCheckpointTransitionChainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> transitions,
      Result result) {
    if (result == null) {
      throw new IllegalArgumentException("S8 checkpoint-transition-chain checkpoint-transition chain is required");
    }
    Result expected = create(checkpointTransitionChainCheckpointTransitionChainIdentifier, transitions);
    return expected.checkpointTransitionChainCheckpointTransitionChainIdentifier
        .equals(result.checkpointTransitionChainCheckpointTransitionChainIdentifier)
        && expected.checkpointTransitionChainCheckpointIdentifier
            .equals(result.checkpointTransitionChainCheckpointIdentifier)
        && expected.checkpointTransitionChainIdentifier.equals(result.checkpointTransitionChainIdentifier)
        && expected.ledgerIdentifier.equals(result.ledgerIdentifier)
        && expected.chainIdentifier.equals(result.chainIdentifier)
        && expected.manifestIdentifier.equals(result.manifestIdentifier)
        && expected.firstPriorCheckpointSequence == result.firstPriorCheckpointSequence
        && expected.finalCandidateCheckpointSequence == result.finalCandidateCheckpointSequence
        && expected.totalSequenceDelta == result.totalSequenceDelta
        && MessageDigest.isEqual(expected.firstPriorCheckpointDigest, result.firstPriorCheckpointDigest)
        && MessageDigest.isEqual(expected.finalCandidateCheckpointDigest, result.finalCandidateCheckpointDigest)
        && MessageDigest.isEqual(expected.firstPriorCheckpointTransitionChainDigest,
            result.firstPriorCheckpointTransitionChainDigest)
        && MessageDigest.isEqual(expected.finalCandidateCheckpointTransitionChainDigest,
            result.finalCandidateCheckpointTransitionChainDigest)
        && expected.firstPriorLedgerDigestHex.equals(result.firstPriorLedgerDigestHex)
        && expected.finalCandidateLedgerDigestHex.equals(result.finalCandidateLedgerDigestHex)
        && expected.firstPriorChainDigestHex.equals(result.firstPriorChainDigestHex)
        && expected.finalCandidateChainDigestHex.equals(result.finalCandidateChainDigestHex)
        && expected.firstPriorManifestDigestHex.equals(result.firstPriorManifestDigestHex)
        && expected.finalCandidateManifestDigestHex.equals(result.finalCandidateManifestDigestHex)
        && expected.transitionCount == result.transitionCount
        && expected.strictAppendReceiptCount == result.strictAppendReceiptCount
        && expected.unchangedReceiptCount == result.unchangedReceiptCount
        && expected.addedReceiptCount == result.addedReceiptCount
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

  /** Validate one already-qualified checkpoint-transition-chain checkpoint-transition receipt. */
  private static void validateReceipt(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result receipt,
      int index) {
    if (receipt == null) {
      throw new IllegalArgumentException("Checkpoint-transition receipt at index " + index + " cannot be null");
    }
    requireText(receipt.getCheckpointTransitionChainCheckpointIdentifier(), "Checkpoint identifier");
    requireText(receipt.getCheckpointTransitionChainIdentifier(), "Transition-chain identifier");
    requireText(receipt.getLedgerIdentifier(), "Ledger identifier");
    requireText(receipt.getChainIdentifier(), "Chain identifier");
    requireText(receipt.getManifestIdentifier(), "Manifest identifier");
    requireText(receipt.getPriorFinalLedgerDigestHex(), "Prior ledger endpoint");
    requireText(receipt.getCandidateFinalLedgerDigestHex(), "Candidate ledger endpoint");
    requireText(receipt.getPriorFinalChainDigestHex(), "Prior chain endpoint");
    requireText(receipt.getCandidateFinalChainDigestHex(), "Candidate chain endpoint");
    requireText(receipt.getPriorFinalManifestDigestHex(), "Prior manifest endpoint");
    requireText(receipt.getCandidateFinalManifestDigestHex(), "Candidate manifest endpoint");
    requireText(receipt.getTransitionDigestHex(), "Checkpoint-transition receipt digest");
    if (!AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.DIGEST_ALGORITHM
        .equals(receipt.getDigestAlgorithm())
        || !AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.SCHEMA_IDENTIFIER
            .equals(receipt.getSchemaIdentifier())) {
      throw new IllegalArgumentException("Checkpoint-transition receipt encoding is not supported");
    }
    if (receipt.isStrictAppend() == receipt.isUnchanged()) {
      throw new IllegalArgumentException(
          "Each checkpoint-transition-chain checkpoint-transition receipt must have exactly one state");
    }
    if (receipt.getSequenceDelta() < 0L || subtractExact(receipt.getCandidateCheckpointSequence(),
        receipt.getPriorCheckpointSequence(), "Checkpoint-sequence delta") != receipt.getSequenceDelta()) {
      throw new IllegalArgumentException("Checkpoint-transition sequence delta is inconsistent");
    }
    requireNonNegative(receipt.getAddedReceiptCount(), "Added receipt count");
    requireNonNegative(receipt.getAddedLedgerReceiptCount(), "Added ledger-receipt count");
    requireNonNegative(receipt.getAddedTransitionCount(), "Added transition count");
    requireNonNegative(receipt.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
    requireNonNegative(receipt.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
    requireNonNegative(receipt.getAddedReconciliationCount(), "Added reconciliation count");
    requireNonNegative(receipt.getAddedEntryCount(), "Added represented-entry count");
    requireNonNegative(receipt.getAddedStrictAppendCount(), "Added strict-append entry count");
    requireNonNegative(receipt.getAddedUnchangedCount(), "Added unchanged entry count");
    if (addExact(receipt.getAddedStrictAppendTransitionCount(), receipt.getAddedUnchangedTransitionCount(),
        "Receipt transition state count") != receipt.getAddedTransitionCount()) {
      throw new IllegalArgumentException("Checkpoint-transition transition counts are inconsistent");
    }
    if (addExact(receipt.getAddedStrictAppendCount(), receipt.getAddedUnchangedCount(),
        "Receipt represented-entry state count") != receipt.getAddedEntryCount()) {
      throw new IllegalArgumentException("Checkpoint-transition entry counts are inconsistent");
    }
    if ((receipt.isUnchanged() && receipt.getAddedReceiptCount() != 0)
        || (receipt.isStrictAppend() && receipt.getAddedReceiptCount() <= 0)) {
      throw new IllegalArgumentException("Checkpoint-transition receipt state disagrees with its receipt delta");
    }
    if (receipt.getPriorCheckpointDigestBytes().length != 32 || receipt.getCandidateCheckpointDigestBytes().length != 32
        || receipt.getPriorChainDigestBytes().length != 32 || receipt.getCandidateChainDigestBytes().length != 32) {
      throw new IllegalArgumentException("Checkpoint and transition-chain digests must contain 32 bytes");
    }
    if (receipt.getTransitionDigestBytes().length != 32) {
      throw new IllegalArgumentException("Transition-ledger receipt digest must contain 32 bytes");
    }
  }

  /** Require exact adjacency between two checkpoint-transition-chain checkpoint-transition receipts. */
  private static void requireAdjacency(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result previous,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result next) {
    if (previous.getCandidateCheckpointSequence() != next.getPriorCheckpointSequence()) {
      throw new IllegalArgumentException("Checkpoint-transition receipts contain a sequence gap or fork");
    }
    if (!MessageDigest.isEqual(previous.getCandidateCheckpointDigestBytes(), next.getPriorCheckpointDigestBytes())) {
      throw new IllegalArgumentException("Checkpoint-transition receipts contain a checkpoint-digest gap or fork");
    }
    if (!MessageDigest.isEqual(previous.getCandidateChainDigestBytes(), next.getPriorChainDigestBytes())) {
      throw new IllegalArgumentException("Checkpoint-transition receipts contain a transition-chain gap or fork");
    }
    if (!previous.getCandidateFinalLedgerDigestHex().equals(next.getPriorFinalLedgerDigestHex())
        || !previous.getCandidateFinalChainDigestHex().equals(next.getPriorFinalChainDigestHex())
        || !previous.getCandidateFinalManifestDigestHex().equals(next.getPriorFinalManifestDigestHex())) {
      throw new IllegalArgumentException("Checkpoint-transition receipts contain an endpoint gap or fork");
    }
  }

  /** Compare two ordered receipt lists by raw receipt digest. */
  private static boolean sameOrderedReceipts(
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> left,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> right) {
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
  private static byte[] digest(String checkpointTransitionChainCheckpointTransitionChainIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> transitions,
      String checkpointTransitionChainCheckpointIdentifier, String checkpointTransitionChainIdentifier,
      String ledgerIdentifier, String chainIdentifier, String manifestIdentifier, long totalSequenceDelta,
      int strictAppendReceiptCount, int unchangedReceiptCount, int addedReceiptCount, int addedLedgerReceiptCount,
      int addedTransitionCount, int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount,
      int addedReconciliationCount, int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, checkpointTransitionChainCheckpointTransitionChainIdentifier);
        writeString(output, checkpointTransitionChainCheckpointIdentifier);
        writeString(output, checkpointTransitionChainIdentifier);
        writeString(output, ledgerIdentifier);
        writeString(output, chainIdentifier);
        writeString(output, manifestIdentifier);
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result first = transitions
            .get(0);
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result last = transitions
            .get(transitions.size() - 1);
        output.writeLong(first.getPriorCheckpointSequence());
        output.writeLong(last.getCandidateCheckpointSequence());
        output.writeLong(totalSequenceDelta);
        writeBytes(output, first.getPriorCheckpointDigestBytes());
        writeBytes(output, last.getCandidateCheckpointDigestBytes());
        writeBytes(output, first.getPriorChainDigestBytes());
        writeBytes(output, last.getCandidateChainDigestBytes());
        writeString(output, first.getPriorFinalLedgerDigestHex());
        writeString(output, last.getCandidateFinalLedgerDigestHex());
        writeString(output, first.getPriorFinalChainDigestHex());
        writeString(output, last.getCandidateFinalChainDigestHex());
        writeString(output, first.getPriorFinalManifestDigestHex());
        writeString(output, last.getCandidateFinalManifestDigestHex());
        output.writeInt(transitions.size());
        for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result transition : transitions) {
          writeBytes(output, transition.getTransitionDigestBytes());
        }
        output.writeInt(strictAppendReceiptCount);
        output.writeInt(unchangedReceiptCount);
        output.writeInt(addedReceiptCount);
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
      throw new IllegalStateException("Unable to encode the S8 checkpoint-transition chain", exception);
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

  /** Add sequence deltas with overflow detection. */
  private static long addExact(long left, long right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported long range", exception);
    }
  }

  /** Subtract checkpoint sequences with overflow detection. */
  private static long subtractExact(long candidate, long prior, String name) {
    try {
      return Math.subtractExact(candidate, prior);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported long range", exception);
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

  /** Immutable ordered checkpoint-transition chain. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String checkpointTransitionChainCheckpointTransitionChainIdentifier;
    private final String checkpointTransitionChainCheckpointIdentifier;
    private final String checkpointTransitionChainIdentifier;
    private final String ledgerIdentifier;
    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final String firstPriorLedgerDigestHex;
    private final String finalCandidateLedgerDigestHex;
    private final String firstPriorChainDigestHex;
    private final String finalCandidateChainDigestHex;
    private final String firstPriorManifestDigestHex;
    private final String finalCandidateManifestDigestHex;
    private final long firstPriorCheckpointSequence;
    private final long finalCandidateCheckpointSequence;
    private final long totalSequenceDelta;
    private final byte[] firstPriorCheckpointDigest;
    private final byte[] finalCandidateCheckpointDigest;
    private final byte[] firstPriorCheckpointTransitionChainDigest;
    private final byte[] finalCandidateCheckpointTransitionChainDigest;
    private final List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> transitions;
    private final int transitionCount;
    private final int strictAppendReceiptCount;
    private final int unchangedReceiptCount;
    private final int addedReceiptCount;
    private final int addedLedgerReceiptCount;
    private final int addedTransitionCount;
    private final int addedStrictAppendTransitionCount;
    private final int addedUnchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] chainDigest;

    /** Create an immutable checkpoint-transition chain. */
    private Result(String checkpointTransitionChainCheckpointTransitionChainIdentifier,
        String checkpointTransitionChainCheckpointIdentifier, String checkpointTransitionChainIdentifier,
        String ledgerIdentifier, String chainIdentifier, String manifestIdentifier, long firstPriorCheckpointSequence,
        long finalCandidateCheckpointSequence, long totalSequenceDelta, byte[] firstPriorCheckpointDigest,
        byte[] finalCandidateCheckpointDigest, byte[] firstPriorCheckpointTransitionChainDigest,
        byte[] finalCandidateCheckpointTransitionChainDigest, String firstPriorLedgerDigestHex,
        String finalCandidateLedgerDigestHex, String firstPriorChainDigestHex, String finalCandidateChainDigestHex,
        String firstPriorManifestDigestHex, String finalCandidateManifestDigestHex,
        List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> transitions,
        int strictAppendReceiptCount, int unchangedReceiptCount, int addedReceiptCount, int addedLedgerReceiptCount,
        int addedTransitionCount, int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount,
        int addedReconciliationCount, int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount,
        byte[] chainDigest) {
      this.checkpointTransitionChainCheckpointTransitionChainIdentifier = checkpointTransitionChainCheckpointTransitionChainIdentifier;
      this.checkpointTransitionChainCheckpointIdentifier = checkpointTransitionChainCheckpointIdentifier;
      this.checkpointTransitionChainIdentifier = checkpointTransitionChainIdentifier;
      this.ledgerIdentifier = ledgerIdentifier;
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.firstPriorLedgerDigestHex = firstPriorLedgerDigestHex;
      this.finalCandidateLedgerDigestHex = finalCandidateLedgerDigestHex;
      this.firstPriorChainDigestHex = firstPriorChainDigestHex;
      this.finalCandidateChainDigestHex = finalCandidateChainDigestHex;
      this.firstPriorManifestDigestHex = firstPriorManifestDigestHex;
      this.finalCandidateManifestDigestHex = finalCandidateManifestDigestHex;
      this.firstPriorCheckpointSequence = firstPriorCheckpointSequence;
      this.finalCandidateCheckpointSequence = finalCandidateCheckpointSequence;
      this.totalSequenceDelta = totalSequenceDelta;
      this.firstPriorCheckpointDigest = firstPriorCheckpointDigest.clone();
      this.finalCandidateCheckpointDigest = finalCandidateCheckpointDigest.clone();
      this.firstPriorCheckpointTransitionChainDigest = firstPriorCheckpointTransitionChainDigest.clone();
      this.finalCandidateCheckpointTransitionChainDigest = finalCandidateCheckpointTransitionChainDigest.clone();
      this.transitions = Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result>(
              transitions));
      this.transitionCount = transitions.size();
      this.strictAppendReceiptCount = strictAppendReceiptCount;
      this.unchangedReceiptCount = unchangedReceiptCount;
      this.addedReceiptCount = addedReceiptCount;
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

    /** @return caller-owned checkpoint-transition-chain identity. */
    public String getCheckpointTransitionChainCheckpointTransitionChainIdentifier() {
      return checkpointTransitionChainCheckpointTransitionChainIdentifier;
    }

    /** @return inherited checkpoint-series identity. */
    public String getCheckpointTransitionChainCheckpointIdentifier() {
      return checkpointTransitionChainCheckpointIdentifier;
    }

    /** @return inherited transition-chain identity. */
    public String getCheckpointTransitionChainIdentifier() {
      return checkpointTransitionChainIdentifier;
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

    /** @return first prior checkpoint sequence. */
    public long getFirstPriorCheckpointSequence() {
      return firstPriorCheckpointSequence;
    }

    /** @return final candidate checkpoint sequence. */
    public long getFinalCandidateCheckpointSequence() {
      return finalCandidateCheckpointSequence;
    }

    /** @return exact aggregate checkpoint-sequence delta. */
    public long getTotalSequenceDelta() {
      return totalSequenceDelta;
    }

    /** @return defensive copy of the first prior checkpoint digest. */
    public byte[] getFirstPriorCheckpointDigestBytes() {
      return firstPriorCheckpointDigest.clone();
    }

    /** @return defensive copy of the final candidate checkpoint digest. */
    public byte[] getFinalCandidateCheckpointDigestBytes() {
      return finalCandidateCheckpointDigest.clone();
    }

    /** @return defensive copy of the first prior transition-chain digest. */
    public byte[] getFirstPriorCheckpointTransitionChainDigestBytes() {
      return firstPriorCheckpointTransitionChainDigest.clone();
    }

    /** @return defensive copy of the final candidate transition-chain digest. */
    public byte[] getFinalCandidateCheckpointTransitionChainDigestBytes() {
      return finalCandidateCheckpointTransitionChainDigest.clone();
    }

    /** @return immutable ordered receipt list. */
    public List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result> getTransitions() {
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

    /** @return aggregate appended transition-ledger receipt count. */
    public int getAddedReceiptCount() {
      return addedReceiptCount;
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
