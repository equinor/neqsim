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
 * Proves an unchanged or strict-append transition between two qualified S8 transition-chain
 * checkpoints.
 *
 * <p>
 * The receipt binds independently verified checkpoints to an exact ordered transition-chain
 * prefix. It does not replay the underlying ledgers, manifests, reconciliations, or stream
 * evidence.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition {
  /** Digest algorithm used by checkpoint-transition receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical receipt encoding. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-transition-chain-checkpoint-transition-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition() {
  }

  /**
   * Create a deterministic receipt for an unchanged or strict-append checkpoint transition.
   *
   * @param priorChain prior qualified transition chain
   * @param priorCheckpoint checkpoint that verifies the prior chain
   * @param candidateChain candidate qualified transition chain
   * @param candidateCheckpoint checkpoint that verifies the candidate chain
   * @return immutable checkpoint-transition receipt
   * @throws IllegalArgumentException if a checkpoint, sequence, identity, prefix, endpoint, or
   *         count gate fails
   */
  public static Result create(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result priorChain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result priorCheckpoint,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result candidateChain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result candidateCheckpoint) {
    validateCheckpoint(priorChain, priorCheckpoint, "Prior");
    validateCheckpoint(candidateChain, candidateCheckpoint, "Candidate");
    requireMatchingIdentities(priorCheckpoint, candidateCheckpoint);

    long sequenceDelta = subtractSequence(candidateCheckpoint.getCheckpointSequence(),
        priorCheckpoint.getCheckpointSequence());
    if (candidateChain.getTransitionCount() < priorChain.getTransitionCount()) {
      throw new IllegalArgumentException("Candidate transition chain cannot truncate the prior chain");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> priorTransitions = priorChain
        .getTransitions();
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result> candidateTransitions = candidateChain
        .getTransitions();
    for (int index = 0; index < priorTransitions.size(); index++) {
      if (!sameTransition(priorTransitions.get(index), candidateTransitions.get(index))) {
        throw new IllegalArgumentException("Candidate transition chain must preserve the exact ordered prior prefix");
      }
    }

    int addedReceiptCount = subtractCount(candidateChain.getTransitionCount(), priorChain.getTransitionCount(),
        "Added receipt count");
    int addedLedgerReceiptCount = subtractCount(candidateChain.getAddedLedgerReceiptCount(),
        priorChain.getAddedLedgerReceiptCount(), "Added ledger-receipt count");
    int addedTransitionCount = subtractCount(candidateChain.getAddedTransitionCount(),
        priorChain.getAddedTransitionCount(), "Added transition count");
    int addedStrictAppendTransitionCount = subtractCount(candidateChain.getAddedStrictAppendTransitionCount(),
        priorChain.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
    int addedUnchangedTransitionCount = subtractCount(candidateChain.getAddedUnchangedTransitionCount(),
        priorChain.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
    int addedReconciliationCount = subtractCount(candidateChain.getAddedReconciliationCount(),
        priorChain.getAddedReconciliationCount(), "Added reconciliation count");
    int addedEntryCount = subtractCount(candidateChain.getAddedEntryCount(), priorChain.getAddedEntryCount(),
        "Added represented-entry count");
    int addedStrictAppendCount = subtractCount(candidateChain.getAddedStrictAppendCount(),
        priorChain.getAddedStrictAppendCount(), "Added strict-append entry count");
    int addedUnchangedCount = subtractCount(candidateChain.getAddedUnchangedCount(),
        priorChain.getAddedUnchangedCount(), "Added unchanged entry count");

    if (addCount(addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        "Added transition-state count") != addedTransitionCount) {
      throw new IllegalArgumentException("Checkpoint-transition transition-count deltas are inconsistent");
    }
    if (addCount(addedStrictAppendCount, addedUnchangedCount,
        "Added represented-entry state count") != addedEntryCount) {
      throw new IllegalArgumentException("Checkpoint-transition represented-entry deltas are inconsistent");
    }

    boolean unchanged = addedReceiptCount == 0;
    boolean strictAppend = addedReceiptCount > 0;
    if (unchanged && !MessageDigest.isEqual(priorChain.getChainDigestBytes(), candidateChain.getChainDigestBytes())) {
      throw new IllegalArgumentException("Equal-length checkpoint chains must be unchanged");
    }
    if (strictAppend) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result firstAppended = candidateTransitions
          .get(priorTransitions.size());
      if (!priorChain.getFinalCandidateLedgerDigestHex().equals(firstAppended.getPriorLedgerDigestHex())) {
        throw new IllegalArgumentException("Candidate checkpoint chain does not continue the prior ledger endpoint");
      }
      if (!priorChain.getFinalCandidateChainDigestHex()
          .equals(firstAppended.getPriorFinalCandidateChainDigestHex())) {
        throw new IllegalArgumentException("Candidate checkpoint chain does not continue the prior chain endpoint");
      }
      if (!priorChain.getFinalCandidateManifestDigestHex()
          .equals(firstAppended.getPriorFinalCandidateManifestDigestHex())) {
        throw new IllegalArgumentException("Candidate checkpoint chain does not continue the prior manifest endpoint");
      }
    }

    byte[] transitionDigest = digest(priorChain, priorCheckpoint, candidateChain, candidateCheckpoint, sequenceDelta,
        unchanged, strictAppend, addedReceiptCount, addedLedgerReceiptCount, addedTransitionCount,
        addedStrictAppendTransitionCount, addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount,
        addedStrictAppendCount, addedUnchangedCount);
    return new Result(priorCheckpoint.getCheckpointIdentifier(), priorCheckpoint.getCheckpointSequence(),
        candidateCheckpoint.getCheckpointSequence(), sequenceDelta, priorCheckpoint.getTransitionChainIdentifier(),
        priorCheckpoint.getLedgerIdentifier(), priorCheckpoint.getChainIdentifier(),
        priorCheckpoint.getManifestIdentifier(), priorCheckpoint.getCheckpointDigestBytes(),
        candidateCheckpoint.getCheckpointDigestBytes(), priorChain.getChainDigestBytes(),
        candidateChain.getChainDigestBytes(), priorChain.getFinalCandidateLedgerDigestHex(),
        candidateChain.getFinalCandidateLedgerDigestHex(), priorChain.getFinalCandidateChainDigestHex(),
        candidateChain.getFinalCandidateChainDigestHex(), priorChain.getFinalCandidateManifestDigestHex(),
        candidateChain.getFinalCandidateManifestDigestHex(), unchanged, strictAppend, addedReceiptCount,
        addedLedgerReceiptCount, addedTransitionCount, addedStrictAppendTransitionCount,
        addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount, addedStrictAppendCount,
        addedUnchangedCount, transitionDigest);
  }

  /**
   * Verify two chains and checkpoints against a stored transition receipt.
   *
   * @param priorChain prior qualified transition chain
   * @param priorCheckpoint checkpoint that verifies the prior chain
   * @param candidateChain candidate qualified transition chain
   * @param candidateCheckpoint checkpoint that verifies the candidate chain
   * @param receipt expected checkpoint-transition receipt
   * @return true only when metadata, counts, and raw digests match
   */
  public static boolean verify(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result priorChain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result priorCheckpoint,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result candidateChain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result candidateCheckpoint,
      Result receipt) {
    if (receipt == null) {
      throw new IllegalArgumentException("S8 checkpoint-transition receipt is required");
    }
    Result expected = create(priorChain, priorCheckpoint, candidateChain, candidateCheckpoint);
    return expected.checkpointIdentifier.equals(receipt.checkpointIdentifier)
        && expected.priorCheckpointSequence == receipt.priorCheckpointSequence
        && expected.candidateCheckpointSequence == receipt.candidateCheckpointSequence
        && expected.sequenceDelta == receipt.sequenceDelta
        && expected.transitionChainIdentifier.equals(receipt.transitionChainIdentifier)
        && expected.ledgerIdentifier.equals(receipt.ledgerIdentifier)
        && expected.chainIdentifier.equals(receipt.chainIdentifier)
        && expected.manifestIdentifier.equals(receipt.manifestIdentifier)
        && MessageDigest.isEqual(expected.priorCheckpointDigest, receipt.priorCheckpointDigest)
        && MessageDigest.isEqual(expected.candidateCheckpointDigest, receipt.candidateCheckpointDigest)
        && MessageDigest.isEqual(expected.priorChainDigest, receipt.priorChainDigest)
        && MessageDigest.isEqual(expected.candidateChainDigest, receipt.candidateChainDigest)
        && expected.priorFinalLedgerDigestHex.equals(receipt.priorFinalLedgerDigestHex)
        && expected.candidateFinalLedgerDigestHex.equals(receipt.candidateFinalLedgerDigestHex)
        && expected.priorFinalChainDigestHex.equals(receipt.priorFinalChainDigestHex)
        && expected.candidateFinalChainDigestHex.equals(receipt.candidateFinalChainDigestHex)
        && expected.priorFinalManifestDigestHex.equals(receipt.priorFinalManifestDigestHex)
        && expected.candidateFinalManifestDigestHex.equals(receipt.candidateFinalManifestDigestHex)
        && expected.unchanged == receipt.unchanged && expected.strictAppend == receipt.strictAppend
        && expected.addedReceiptCount == receipt.addedReceiptCount
        && expected.addedLedgerReceiptCount == receipt.addedLedgerReceiptCount
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
   * Validate one chain and its independently reconstructed checkpoint.
   *
   * @param chain qualified transition chain
   * @param checkpoint checkpoint that should bind the chain
   * @param label input label for fail-closed diagnostics
   */
  private static void validateCheckpoint(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result chain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result checkpoint,
      String label) {
    if (chain == null) {
      throw new IllegalArgumentException(label + " S8 transition chain is required");
    }
    if (checkpoint == null) {
      throw new IllegalArgumentException(label + " S8 transition-chain checkpoint is required");
    }
    if (!AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .verify(checkpoint.getCheckpointIdentifier(), checkpoint.getCheckpointSequence(), chain, checkpoint)) {
      throw new IllegalArgumentException(label + " S8 transition-chain checkpoint is inconsistent");
    }
  }

  /**
   * Require stable identities across the checkpoint transition.
   *
   * @param prior prior checkpoint
   * @param candidate candidate checkpoint
   */
  private static void requireMatchingIdentities(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result candidate) {
    if (!prior.getCheckpointIdentifier().equals(candidate.getCheckpointIdentifier())) {
      throw new IllegalArgumentException("Checkpoint identifiers must match");
    }
    if (!prior.getTransitionChainIdentifier().equals(candidate.getTransitionChainIdentifier())) {
      throw new IllegalArgumentException("Transition-chain identifiers must match");
    }
    if (!prior.getLedgerIdentifier().equals(candidate.getLedgerIdentifier())) {
      throw new IllegalArgumentException("Ledger identifiers must match");
    }
    if (!prior.getChainIdentifier().equals(candidate.getChainIdentifier())) {
      throw new IllegalArgumentException("Chain identifiers must match");
    }
    if (!prior.getManifestIdentifier().equals(candidate.getManifestIdentifier())) {
      throw new IllegalArgumentException("Manifest identifiers must match");
    }
  }

  /**
   * Compare two immutable transition-ledger receipts.
   *
   * @param left first receipt
   * @param right second receipt
   * @return true when every identity, endpoint, state, count, and digest matches
   */
  private static boolean sameTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result left,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result right) {
    return left.getSchemaIdentifier().equals(right.getSchemaIdentifier())
        && left.getDigestAlgorithm().equals(right.getDigestAlgorithm())
        && left.getLedgerIdentifier().equals(right.getLedgerIdentifier())
        && left.getChainIdentifier().equals(right.getChainIdentifier())
        && left.getManifestIdentifier().equals(right.getManifestIdentifier())
        && left.getPriorLedgerDigestHex().equals(right.getPriorLedgerDigestHex())
        && left.getCandidateLedgerDigestHex().equals(right.getCandidateLedgerDigestHex())
        && left.getPriorFinalCandidateChainDigestHex().equals(right.getPriorFinalCandidateChainDigestHex())
        && left.getCandidateFinalCandidateChainDigestHex().equals(right.getCandidateFinalCandidateChainDigestHex())
        && left.getPriorFinalCandidateManifestDigestHex().equals(right.getPriorFinalCandidateManifestDigestHex())
        && left.getCandidateFinalCandidateManifestDigestHex()
            .equals(right.getCandidateFinalCandidateManifestDigestHex())
        && left.isUnchanged() == right.isUnchanged() && left.isStrictAppend() == right.isStrictAppend()
        && left.getAddedReceiptCount() == right.getAddedReceiptCount()
        && left.getAddedTransitionCount() == right.getAddedTransitionCount()
        && left.getAddedStrictAppendTransitionCount() == right.getAddedStrictAppendTransitionCount()
        && left.getAddedUnchangedTransitionCount() == right.getAddedUnchangedTransitionCount()
        && left.getAddedReconciliationCount() == right.getAddedReconciliationCount()
        && left.getAddedEntryCount() == right.getAddedEntryCount()
        && left.getAddedStrictAppendCount() == right.getAddedStrictAppendCount()
        && left.getAddedUnchangedCount() == right.getAddedUnchangedCount()
        && MessageDigest.isEqual(left.getTransitionDigestBytes(), right.getTransitionDigestBytes());
  }

  /**
   * Subtract checkpoint sequences with overflow and ordering checks.
   *
   * @param candidate candidate sequence
   * @param prior prior sequence
   * @return exact positive sequence delta
   */
  private static long subtractSequence(long candidate, long prior) {
    final long difference;
    try {
      difference = Math.subtractExact(candidate, prior);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Checkpoint sequence delta exceeds the supported range", exception);
    }
    if (difference <= 0L) {
      throw new IllegalArgumentException("Candidate checkpoint sequence must increase");
    }
    return difference;
  }

  /**
   * Subtract counts with underflow and overflow checks.
   *
   * @param candidate candidate count
   * @param prior prior count
   * @param name count name
   * @return exact non-negative difference
   */
  private static int subtractCount(int candidate, int prior, String name) {
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
   * Add counts with overflow detection.
   *
   * @param left first count
   * @param right second count
   * @param name count name
   * @return exact sum
   */
  private static int addCount(int left, int right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
  }

  /**
   * Calculate the canonical checkpoint-transition digest.
   *
   * @param priorChain prior transition chain
   * @param priorCheckpoint prior checkpoint
   * @param candidateChain candidate transition chain
   * @param candidateCheckpoint candidate checkpoint
   * @param sequenceDelta checkpoint sequence delta
   * @param unchanged unchanged-state flag
   * @param strictAppend strict-append-state flag
   * @param addedReceiptCount added receipt count
   * @param addedLedgerReceiptCount added ledger-receipt count
   * @param addedTransitionCount added underlying transition count
   * @param addedStrictAppendTransitionCount added strict-append transition count
   * @param addedUnchangedTransitionCount added unchanged transition count
   * @param addedReconciliationCount added reconciliation count
   * @param addedEntryCount added represented-entry count
   * @param addedStrictAppendCount added strict-append entry count
   * @param addedUnchangedCount added unchanged entry count
   * @return canonical SHA-256 digest
   */
  private static byte[] digest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result priorChain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result priorCheckpoint,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result candidateChain,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result candidateCheckpoint,
      long sequenceDelta, boolean unchanged, boolean strictAppend, int addedReceiptCount,
      int addedLedgerReceiptCount, int addedTransitionCount, int addedStrictAppendTransitionCount,
      int addedUnchangedTransitionCount, int addedReconciliationCount, int addedEntryCount,
      int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, priorCheckpoint.getCheckpointIdentifier());
        output.writeLong(priorCheckpoint.getCheckpointSequence());
        output.writeLong(candidateCheckpoint.getCheckpointSequence());
        output.writeLong(sequenceDelta);
        writeString(output, priorCheckpoint.getTransitionChainIdentifier());
        writeString(output, priorCheckpoint.getLedgerIdentifier());
        writeString(output, priorCheckpoint.getChainIdentifier());
        writeString(output, priorCheckpoint.getManifestIdentifier());
        writeBytes(output, priorCheckpoint.getCheckpointDigestBytes());
        writeBytes(output, candidateCheckpoint.getCheckpointDigestBytes());
        writeBytes(output, priorChain.getChainDigestBytes());
        writeBytes(output, candidateChain.getChainDigestBytes());
        writeString(output, priorChain.getFinalCandidateLedgerDigestHex());
        writeString(output, candidateChain.getFinalCandidateLedgerDigestHex());
        writeString(output, priorChain.getFinalCandidateChainDigestHex());
        writeString(output, candidateChain.getFinalCandidateChainDigestHex());
        writeString(output, priorChain.getFinalCandidateManifestDigestHex());
        writeString(output, candidateChain.getFinalCandidateManifestDigestHex());
        output.writeBoolean(unchanged);
        output.writeBoolean(strictAppend);
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
      throw new IllegalStateException("Unable to encode the S8 checkpoint transition", exception);
    }
  }

  /**
   * Write one length-prefixed UTF-8 string.
   *
   * @param output destination stream
   * @param value string value
   * @throws IOException if the canonical encoding cannot be written
   */
  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(utf8.length);
    output.write(utf8);
  }

  /**
   * Write one length-prefixed byte array.
   *
   * @param output destination stream
   * @param value byte-array value
   * @throws IOException if the canonical encoding cannot be written
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

  /** Immutable receipt between two qualified transition-chain checkpoints. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String checkpointIdentifier;
    private final long priorCheckpointSequence;
    private final long candidateCheckpointSequence;
    private final long sequenceDelta;
    private final String transitionChainIdentifier;
    private final String ledgerIdentifier;
    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final byte[] priorCheckpointDigest;
    private final byte[] candidateCheckpointDigest;
    private final byte[] priorChainDigest;
    private final byte[] candidateChainDigest;
    private final String priorFinalLedgerDigestHex;
    private final String candidateFinalLedgerDigestHex;
    private final String priorFinalChainDigestHex;
    private final String candidateFinalChainDigestHex;
    private final String priorFinalManifestDigestHex;
    private final String candidateFinalManifestDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final int addedReceiptCount;
    private final int addedLedgerReceiptCount;
    private final int addedTransitionCount;
    private final int addedStrictAppendTransitionCount;
    private final int addedUnchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] transitionDigest;

    /**
     * Create one immutable checkpoint-transition receipt.
     *
     * @param checkpointIdentifier checkpoint-series identity
     * @param priorCheckpointSequence prior checkpoint sequence
     * @param candidateCheckpointSequence candidate checkpoint sequence
     * @param sequenceDelta exact positive sequence delta
     * @param transitionChainIdentifier transition-chain identity
     * @param ledgerIdentifier ledger identity
     * @param chainIdentifier chain identity
     * @param manifestIdentifier manifest identity
     * @param priorCheckpointDigest prior checkpoint digest
     * @param candidateCheckpointDigest candidate checkpoint digest
     * @param priorChainDigest prior chain digest
     * @param candidateChainDigest candidate chain digest
     * @param priorFinalLedgerDigestHex prior final ledger endpoint
     * @param candidateFinalLedgerDigestHex candidate final ledger endpoint
     * @param priorFinalChainDigestHex prior final chain endpoint
     * @param candidateFinalChainDigestHex candidate final chain endpoint
     * @param priorFinalManifestDigestHex prior final manifest endpoint
     * @param candidateFinalManifestDigestHex candidate final manifest endpoint
     * @param unchanged unchanged-state flag
     * @param strictAppend strict-append-state flag
     * @param addedReceiptCount added receipt count
     * @param addedLedgerReceiptCount added ledger-receipt count
     * @param addedTransitionCount added transition count
     * @param addedStrictAppendTransitionCount added strict-append transition count
     * @param addedUnchangedTransitionCount added unchanged transition count
     * @param addedReconciliationCount added reconciliation count
     * @param addedEntryCount added represented-entry count
     * @param addedStrictAppendCount added strict-append entry count
     * @param addedUnchangedCount added unchanged entry count
     * @param transitionDigest canonical transition digest
     */
    private Result(String checkpointIdentifier, long priorCheckpointSequence, long candidateCheckpointSequence,
        long sequenceDelta, String transitionChainIdentifier, String ledgerIdentifier, String chainIdentifier,
        String manifestIdentifier, byte[] priorCheckpointDigest, byte[] candidateCheckpointDigest,
        byte[] priorChainDigest, byte[] candidateChainDigest, String priorFinalLedgerDigestHex,
        String candidateFinalLedgerDigestHex, String priorFinalChainDigestHex, String candidateFinalChainDigestHex,
        String priorFinalManifestDigestHex, String candidateFinalManifestDigestHex, boolean unchanged,
        boolean strictAppend, int addedReceiptCount, int addedLedgerReceiptCount, int addedTransitionCount,
        int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount, int addedReconciliationCount,
        int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount, byte[] transitionDigest) {
      this.checkpointIdentifier = checkpointIdentifier;
      this.priorCheckpointSequence = priorCheckpointSequence;
      this.candidateCheckpointSequence = candidateCheckpointSequence;
      this.sequenceDelta = sequenceDelta;
      this.transitionChainIdentifier = transitionChainIdentifier;
      this.ledgerIdentifier = ledgerIdentifier;
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.priorCheckpointDigest = priorCheckpointDigest.clone();
      this.candidateCheckpointDigest = candidateCheckpointDigest.clone();
      this.priorChainDigest = priorChainDigest.clone();
      this.candidateChainDigest = candidateChainDigest.clone();
      this.priorFinalLedgerDigestHex = priorFinalLedgerDigestHex;
      this.candidateFinalLedgerDigestHex = candidateFinalLedgerDigestHex;
      this.priorFinalChainDigestHex = priorFinalChainDigestHex;
      this.candidateFinalChainDigestHex = candidateFinalChainDigestHex;
      this.priorFinalManifestDigestHex = priorFinalManifestDigestHex;
      this.candidateFinalManifestDigestHex = candidateFinalManifestDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.addedReceiptCount = addedReceiptCount;
      this.addedLedgerReceiptCount = addedLedgerReceiptCount;
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

    /** @return versioned canonical schema identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return checkpoint-series identity. */
    public String getCheckpointIdentifier() {
      return checkpointIdentifier;
    }

    /** @return prior checkpoint sequence. */
    public long getPriorCheckpointSequence() {
      return priorCheckpointSequence;
    }

    /** @return candidate checkpoint sequence. */
    public long getCandidateCheckpointSequence() {
      return candidateCheckpointSequence;
    }

    /** @return exact positive sequence delta. */
    public long getSequenceDelta() {
      return sequenceDelta;
    }

    /** @return transition-chain identity. */
    public String getTransitionChainIdentifier() {
      return transitionChainIdentifier;
    }

    /** @return ledger identity. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return chain identity. */
    public String getChainIdentifier() {
      return chainIdentifier;
    }

    /** @return manifest identity. */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return defensive copy of the prior checkpoint digest. */
    public byte[] getPriorCheckpointDigestBytes() {
      return priorCheckpointDigest.clone();
    }

    /** @return defensive copy of the candidate checkpoint digest. */
    public byte[] getCandidateCheckpointDigestBytes() {
      return candidateCheckpointDigest.clone();
    }

    /** @return defensive copy of the prior chain digest. */
    public byte[] getPriorChainDigestBytes() {
      return priorChainDigest.clone();
    }

    /** @return defensive copy of the candidate chain digest. */
    public byte[] getCandidateChainDigestBytes() {
      return candidateChainDigest.clone();
    }

    /** @return prior final ledger endpoint. */
    public String getPriorFinalLedgerDigestHex() {
      return priorFinalLedgerDigestHex;
    }

    /** @return candidate final ledger endpoint. */
    public String getCandidateFinalLedgerDigestHex() {
      return candidateFinalLedgerDigestHex;
    }

    /** @return prior final chain endpoint. */
    public String getPriorFinalChainDigestHex() {
      return priorFinalChainDigestHex;
    }

    /** @return candidate final chain endpoint. */
    public String getCandidateFinalChainDigestHex() {
      return candidateFinalChainDigestHex;
    }

    /** @return prior final manifest endpoint. */
    public String getPriorFinalManifestDigestHex() {
      return priorFinalManifestDigestHex;
    }

    /** @return candidate final manifest endpoint. */
    public String getCandidateFinalManifestDigestHex() {
      return candidateFinalManifestDigestHex;
    }

    /** @return true when the candidate chain is unchanged. */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when the candidate chain is a strict append. */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return added transition-ledger receipt count. */
    public int getAddedReceiptCount() {
      return addedReceiptCount;
    }

    /** @return added ledger-receipt count. */
    public int getAddedLedgerReceiptCount() {
      return addedLedgerReceiptCount;
    }

    /** @return added underlying transition count. */
    public int getAddedTransitionCount() {
      return addedTransitionCount;
    }

    /** @return added strict-append transition count. */
    public int getAddedStrictAppendTransitionCount() {
      return addedStrictAppendTransitionCount;
    }

    /** @return added unchanged transition count. */
    public int getAddedUnchangedTransitionCount() {
      return addedUnchangedTransitionCount;
    }

    /** @return added reconciliation count. */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return added represented-entry count. */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return added strict-append entry count. */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return added unchanged entry count. */
    public int getAddedUnchangedCount() {
      return addedUnchangedCount;
    }

    /** @return lowercase hexadecimal prior checkpoint digest. */
    public String getPriorCheckpointDigestHex() {
      return toHex(priorCheckpointDigest);
    }

    /** @return lowercase hexadecimal candidate checkpoint digest. */
    public String getCandidateCheckpointDigestHex() {
      return toHex(candidateCheckpointDigest);
    }

    /** @return lowercase hexadecimal prior chain digest. */
    public String getPriorChainDigestHex() {
      return toHex(priorChainDigest);
    }

    /** @return lowercase hexadecimal candidate chain digest. */
    public String getCandidateChainDigestHex() {
      return toHex(candidateChainDigest);
    }

    /** @return lowercase hexadecimal transition digest. */
    public String getTransitionDigestHex() {
      return toHex(transitionDigest);
    }

    /** @return defensive copy of the raw transition digest. */
    public byte[] getTransitionDigestBytes() {
      return transitionDigest.clone();
    }
  }
}
