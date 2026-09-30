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
 * Proves that one S8 manifest-transition-chain transition ledger is unchanged or an ordered strict append of another
 * ledger.
 *
 * <p>
 * This immutable receipt compares already-qualified ledger values. It does not revalidate the underlying chains,
 * manifests, reconciliations, or stream-application evidence and is not a durable store, authentication mechanism,
 * transaction coordinator, compare-and-swap operation, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition {
  /** Digest algorithm used by ledger-transition receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical receipt-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-transition-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition() {
  }

  /**
   * Create a deterministic receipt for an unchanged or strict-append ledger transition.
   *
   * @param prior prior qualified transition ledger
   * @param candidate candidate qualified transition ledger
   * @return immutable ledger-transition receipt
   * @throws IllegalArgumentException if either ledger is invalid or the candidate is not an exact ordered continuation
   */
  public static Result create(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result candidate) {
    validateLedger(prior, "Prior");
    validateLedger(candidate, "Candidate");
    if (!prior.getLedgerIdentifier().equals(candidate.getLedgerIdentifier())) {
      throw new IllegalArgumentException("Transition-ledger identifiers must match");
    }
    if (!prior.getChainIdentifier().equals(candidate.getChainIdentifier())) {
      throw new IllegalArgumentException("Transition-ledger chain identifiers must match");
    }
    if (!prior.getManifestIdentifier().equals(candidate.getManifestIdentifier())) {
      throw new IllegalArgumentException("Transition-ledger manifest identifiers must match");
    }
    if (candidate.getTransitionCount() < prior.getTransitionCount()) {
      throw new IllegalArgumentException("Candidate transition ledger cannot truncate the prior ledger");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> priorTransitions = prior
        .getTransitions();
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> candidateTransitions = candidate
        .getTransitions();
    for (int index = 0; index < priorTransitions.size(); index++) {
      if (!sameTransition(priorTransitions.get(index), candidateTransitions.get(index))) {
        throw new IllegalArgumentException("Candidate transition ledger must preserve the exact ordered prior prefix");
      }
    }

    int addedReceiptCount = subtractExact(candidate.getTransitionCount(), prior.getTransitionCount(),
        "Added receipt count");
    int addedTransitionCount = subtractExact(candidate.getAddedTransitionCount(), prior.getAddedTransitionCount(),
        "Added transition count");
    int addedStrictAppendTransitionCount = subtractExact(candidate.getAddedStrictAppendTransitionCount(),
        prior.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
    int addedUnchangedTransitionCount = subtractExact(candidate.getAddedUnchangedTransitionCount(),
        prior.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
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
      throw new IllegalArgumentException("Transition-ledger transition-count deltas are inconsistent");
    }
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Added entry-state count") != addedEntryCount) {
      throw new IllegalArgumentException("Transition-ledger entry-count deltas are inconsistent");
    }

    boolean unchanged = addedReceiptCount == 0;
    boolean strictAppend = addedReceiptCount > 0;
    if (unchanged && !MessageDigest.isEqual(prior.getLedgerDigestBytes(), candidate.getLedgerDigestBytes())) {
      throw new IllegalArgumentException("Equal-length transition ledgers must be unchanged");
    }
    if (strictAppend) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result firstAppended = candidateTransitions
          .get(priorTransitions.size());
      if (!prior.getFinalCandidateChainDigestHex().equals(firstAppended.getPriorChainDigestHex())) {
        throw new IllegalArgumentException("Candidate transition ledger does not continue the prior chain endpoint");
      }
      if (!prior.getFinalCandidateFinalManifestDigestHex().equals(firstAppended.getPriorFinalManifestDigestHex())) {
        throw new IllegalArgumentException("Candidate transition ledger does not continue the prior manifest endpoint");
      }
    }

    byte[] transitionDigest = digest(prior, candidate, unchanged, strictAppend, addedReceiptCount, addedTransitionCount,
        addedStrictAppendTransitionCount, addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount,
        addedStrictAppendCount, addedUnchangedCount);
    return new Result(prior.getLedgerIdentifier(), prior.getChainIdentifier(), prior.getManifestIdentifier(),
        prior.getLedgerDigestHex(), candidate.getLedgerDigestHex(), prior.getFinalCandidateChainDigestHex(),
        candidate.getFinalCandidateChainDigestHex(), prior.getFinalCandidateFinalManifestDigestHex(),
        candidate.getFinalCandidateFinalManifestDigestHex(), unchanged, strictAppend, addedReceiptCount,
        addedTransitionCount, addedStrictAppendTransitionCount, addedUnchangedTransitionCount, addedReconciliationCount,
        addedEntryCount, addedStrictAppendCount, addedUnchangedCount, transitionDigest);
  }

  /**
   * Verify two qualified ledgers against a stored ledger-transition receipt.
   *
   * @param prior prior qualified transition ledger
   * @param candidate candidate qualified transition ledger
   * @param receipt expected ledger-transition receipt
   * @return true only when all metadata and the constant-time receipt digest match
   */
  public static boolean verify(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result candidate,
      Result receipt) {
    if (receipt == null) {
      throw new IllegalArgumentException("S8 transition-ledger transition receipt is required");
    }
    Result expected = create(prior, candidate);
    return expected.ledgerIdentifier.equals(receipt.ledgerIdentifier)
        && expected.chainIdentifier.equals(receipt.chainIdentifier)
        && expected.manifestIdentifier.equals(receipt.manifestIdentifier)
        && expected.priorLedgerDigestHex.equals(receipt.priorLedgerDigestHex)
        && expected.candidateLedgerDigestHex.equals(receipt.candidateLedgerDigestHex)
        && expected.priorFinalCandidateChainDigestHex.equals(receipt.priorFinalCandidateChainDigestHex)
        && expected.candidateFinalCandidateChainDigestHex.equals(receipt.candidateFinalCandidateChainDigestHex)
        && expected.priorFinalCandidateManifestDigestHex.equals(receipt.priorFinalCandidateManifestDigestHex)
        && expected.candidateFinalCandidateManifestDigestHex.equals(receipt.candidateFinalCandidateManifestDigestHex)
        && expected.unchanged == receipt.unchanged && expected.strictAppend == receipt.strictAppend
        && expected.addedReceiptCount == receipt.addedReceiptCount
        && expected.addedTransitionCount == receipt.addedTransitionCount
        && expected.addedStrictAppendTransitionCount == receipt.addedStrictAppendTransitionCount
        && expected.addedUnchangedTransitionCount == receipt.addedUnchangedTransitionCount
        && expected.addedReconciliationCount == receipt.addedReconciliationCount
        && expected.addedEntryCount == receipt.addedEntryCount
        && expected.addedStrictAppendCount == receipt.addedStrictAppendCount
        && expected.addedUnchangedCount == receipt.addedUnchangedCount
        && MessageDigest.isEqual(expected.transitionDigest, receipt.transitionDigest);
  }

  /** Validate one ledger and its aggregate contract. */
  private static void validateLedger(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result ledger,
      String label) {
    if (ledger == null) {
      throw new IllegalArgumentException(label + " S8 transition ledger is required");
    }
    requireText(ledger.getLedgerIdentifier(), label + " transition-ledger identifier");
    requireText(ledger.getChainIdentifier(), label + " chain identifier");
    requireText(ledger.getManifestIdentifier(), label + " manifest identifier");
    if (!AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
        .verify(ledger.getLedgerIdentifier(), ledger.getTransitions(), ledger)) {
      throw new IllegalArgumentException(label + " S8 transition ledger is inconsistent");
    }
  }

  /** Compare two immutable chain-transition receipts. */
  private static boolean sameTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result left,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result right) {
    return left.getSchemaIdentifier().equals(right.getSchemaIdentifier())
        && left.getDigestAlgorithm().equals(right.getDigestAlgorithm())
        && left.getChainIdentifier().equals(right.getChainIdentifier())
        && left.getManifestIdentifier().equals(right.getManifestIdentifier())
        && left.getPriorChainDigestHex().equals(right.getPriorChainDigestHex())
        && left.getCandidateChainDigestHex().equals(right.getCandidateChainDigestHex())
        && left.getPriorFinalManifestDigestHex().equals(right.getPriorFinalManifestDigestHex())
        && left.getCandidateFinalManifestDigestHex().equals(right.getCandidateFinalManifestDigestHex())
        && left.isUnchanged() == right.isUnchanged() && left.isStrictAppend() == right.isStrictAppend()
        && left.getAddedTransitionCount() == right.getAddedTransitionCount()
        && left.getAddedStrictAppendTransitionCount() == right.getAddedStrictAppendTransitionCount()
        && left.getAddedUnchangedTransitionCount() == right.getAddedUnchangedTransitionCount()
        && left.getAddedReconciliationCount() == right.getAddedReconciliationCount()
        && left.getAddedEntryCount() == right.getAddedEntryCount()
        && left.getAddedStrictAppendCount() == right.getAddedStrictAppendCount()
        && left.getAddedUnchangedCount() == right.getAddedUnchangedCount()
        && MessageDigest.isEqual(left.getTransitionDigestBytes(), right.getTransitionDigestBytes());
  }

  /** Subtract two counts with underflow and overflow detection. */
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

  /** Add two counts with overflow detection. */
  private static int addExact(int left, int right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
  }

  /** Calculate the canonical ledger-transition digest. */
  private static byte[] digest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result candidate,
      boolean unchanged, boolean strictAppend, int addedReceiptCount, int addedTransitionCount,
      int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount, int addedReconciliationCount,
      int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, prior.getLedgerIdentifier());
        writeString(output, prior.getChainIdentifier());
        writeString(output, prior.getManifestIdentifier());
        writeBytes(output, prior.getLedgerDigestBytes());
        writeBytes(output, candidate.getLedgerDigestBytes());
        writeString(output, prior.getFinalCandidateChainDigestHex());
        writeString(output, candidate.getFinalCandidateChainDigestHex());
        writeString(output, prior.getFinalCandidateFinalManifestDigestHex());
        writeString(output, candidate.getFinalCandidateFinalManifestDigestHex());
        output.writeBoolean(unchanged);
        output.writeBoolean(strictAppend);
        output.writeInt(addedReceiptCount);
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
      throw new IllegalStateException("Unable to encode the S8 transition-ledger transition", exception);
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

  /** Immutable transition receipt between two transition ledgers. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final String priorLedgerDigestHex;
    private final String candidateLedgerDigestHex;
    private final String priorFinalCandidateChainDigestHex;
    private final String candidateFinalCandidateChainDigestHex;
    private final String priorFinalCandidateManifestDigestHex;
    private final String candidateFinalCandidateManifestDigestHex;
    private final boolean unchanged;
    private final boolean strictAppend;
    private final int addedReceiptCount;
    private final int addedTransitionCount;
    private final int addedStrictAppendTransitionCount;
    private final int addedUnchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] transitionDigest;

    /** Create an immutable ledger-transition receipt. */
    private Result(String ledgerIdentifier, String chainIdentifier, String manifestIdentifier,
        String priorLedgerDigestHex, String candidateLedgerDigestHex, String priorFinalCandidateChainDigestHex,
        String candidateFinalCandidateChainDigestHex, String priorFinalCandidateManifestDigestHex,
        String candidateFinalCandidateManifestDigestHex, boolean unchanged, boolean strictAppend, int addedReceiptCount,
        int addedTransitionCount, int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount,
        int addedReconciliationCount, int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount,
        byte[] transitionDigest) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.priorLedgerDigestHex = priorLedgerDigestHex;
      this.candidateLedgerDigestHex = candidateLedgerDigestHex;
      this.priorFinalCandidateChainDigestHex = priorFinalCandidateChainDigestHex;
      this.candidateFinalCandidateChainDigestHex = candidateFinalCandidateChainDigestHex;
      this.priorFinalCandidateManifestDigestHex = priorFinalCandidateManifestDigestHex;
      this.candidateFinalCandidateManifestDigestHex = candidateFinalCandidateManifestDigestHex;
      this.unchanged = unchanged;
      this.strictAppend = strictAppend;
      this.addedReceiptCount = addedReceiptCount;
      this.addedTransitionCount = addedTransitionCount;
      this.addedStrictAppendTransitionCount = addedStrictAppendTransitionCount;
      this.addedUnchangedTransitionCount = addedUnchangedTransitionCount;
      this.addedReconciliationCount = addedReconciliationCount;
      this.addedEntryCount = addedEntryCount;
      this.addedStrictAppendCount = addedStrictAppendCount;
      this.addedUnchangedCount = addedUnchangedCount;
      this.transitionDigest = transitionDigest.clone();
    }

    /** @return digest algorithm */
    public String getDigestAlgorithm() {
      return DIGEST_ALGORITHM;
    }

    /** @return canonical schema identifier */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return caller-owned ledger identity */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return caller-owned chain identity */
    public String getChainIdentifier() {
      return chainIdentifier;
    }

    /** @return caller-owned manifest identity */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return prior ledger digest */
    public String getPriorLedgerDigestHex() {
      return priorLedgerDigestHex;
    }

    /** @return candidate ledger digest */
    public String getCandidateLedgerDigestHex() {
      return candidateLedgerDigestHex;
    }

    /** @return prior final candidate-chain digest */
    public String getPriorFinalCandidateChainDigestHex() {
      return priorFinalCandidateChainDigestHex;
    }

    /** @return candidate final candidate-chain digest */
    public String getCandidateFinalCandidateChainDigestHex() {
      return candidateFinalCandidateChainDigestHex;
    }

    /** @return prior final candidate-manifest digest */
    public String getPriorFinalCandidateManifestDigestHex() {
      return priorFinalCandidateManifestDigestHex;
    }

    /** @return candidate final candidate-manifest digest */
    public String getCandidateFinalCandidateManifestDigestHex() {
      return candidateFinalCandidateManifestDigestHex;
    }

    /** @return true when the two ledgers are unchanged */
    public boolean isUnchanged() {
      return unchanged;
    }

    /** @return true when the candidate is a strict append */
    public boolean isStrictAppend() {
      return strictAppend;
    }

    /** @return number of appended chain-transition receipts */
    public int getAddedReceiptCount() {
      return addedReceiptCount;
    }

    /** @return added underlying transition count */
    public int getAddedTransitionCount() {
      return addedTransitionCount;
    }

    /** @return added strict-append transition count */
    public int getAddedStrictAppendTransitionCount() {
      return addedStrictAppendTransitionCount;
    }

    /** @return added unchanged transition count */
    public int getAddedUnchangedTransitionCount() {
      return addedUnchangedTransitionCount;
    }

    /** @return added reconciliation count */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return added represented-entry count */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return added strict-append entry count */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return added unchanged entry count */
    public int getAddedUnchangedCount() {
      return addedUnchangedCount;
    }

    /** @return lowercase transition digest */
    public String getTransitionDigestHex() {
      return toHex(transitionDigest);
    }

    /** @return defensive transition-digest copy */
    public byte[] getTransitionDigestBytes() {
      return transitionDigest.clone();
    }
  }
}
