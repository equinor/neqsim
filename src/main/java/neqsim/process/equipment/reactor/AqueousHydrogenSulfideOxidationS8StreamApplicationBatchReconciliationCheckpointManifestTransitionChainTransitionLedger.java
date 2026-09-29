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
 * Binds ordered S8 manifest transition-chain transition receipts into one immutable ledger.
 *
 * <p>
 * The ledger proves adjacency between already-qualified receipts and aggregates their exact counts. It does not
 * revalidate the underlying chains or manifests and is not a durable store, authentication mechanism, transaction
 * coordinator, replay store, compare-and-swap operation, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger {
  /** Digest algorithm used by transition ledgers. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical ledger-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger() {
  }

  /**
   * Create one deterministic ledger from ordered chain-transition receipts.
   *
   * @param ledgerIdentifier caller-defined ledger identity
   * @param transitions ordered qualified chain-transition receipts
   * @return immutable transition ledger
   * @throws IllegalArgumentException if inputs are missing, duplicated, disconnected, or inconsistent
   */
  public static Result create(String ledgerIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> transitions) {
    requireText(ledgerIdentifier, "Transition-ledger identifier");
    if (transitions == null || transitions.isEmpty()) {
      throw new IllegalArgumentException("S8 manifest transition-chain transition ledger cannot be empty");
    }

    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> copy =
        new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result>(
            transitions.size());
    Set<String> transitionDigests = new HashSet<String>();
    String chainIdentifier = null;
    String manifestIdentifier = null;
    String firstPriorChainDigestHex = null;
    String finalCandidateChainDigestHex = null;
    String firstPriorFinalManifestDigestHex = null;
    String finalCandidateFinalManifestDigestHex = null;
    String previousCandidateChainDigestHex = null;
    String previousCandidateFinalManifestDigestHex = null;
    int addedTransitionCount = 0;
    int addedStrictAppendTransitionCount = 0;
    int addedUnchangedTransitionCount = 0;
    int addedReconciliationCount = 0;
    int addedEntryCount = 0;
    int addedStrictAppendCount = 0;
    int addedUnchangedCount = 0;

    for (int index = 0; index < transitions.size(); index++) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result transition =
          transitions.get(index);
      validateTransition(transition);
      if (!transitionDigests.add(transition.getTransitionDigestHex())) {
        throw new IllegalArgumentException("Transition ledger cannot contain duplicate receipt evidence");
      }
      if (index == 0) {
        chainIdentifier = transition.getChainIdentifier();
        manifestIdentifier = transition.getManifestIdentifier();
        firstPriorChainDigestHex = transition.getPriorChainDigestHex();
        firstPriorFinalManifestDigestHex = transition.getPriorFinalManifestDigestHex();
      } else {
        if (!chainIdentifier.equals(transition.getChainIdentifier())) {
          throw new IllegalArgumentException("Transition-ledger chain identifiers must match");
        }
        if (!manifestIdentifier.equals(transition.getManifestIdentifier())) {
          throw new IllegalArgumentException("Transition-ledger manifest identifiers must match");
        }
        if (!previousCandidateChainDigestHex.equals(transition.getPriorChainDigestHex())) {
          throw new IllegalArgumentException("Transition ledger contains a chain gap, fork, or reordered receipt");
        }
        if (!previousCandidateFinalManifestDigestHex.equals(transition.getPriorFinalManifestDigestHex())) {
          throw new IllegalArgumentException("Transition ledger contains a manifest-endpoint gap or fork");
        }
      }

      previousCandidateChainDigestHex = transition.getCandidateChainDigestHex();
      previousCandidateFinalManifestDigestHex = transition.getCandidateFinalManifestDigestHex();
      finalCandidateChainDigestHex = previousCandidateChainDigestHex;
      finalCandidateFinalManifestDigestHex = previousCandidateFinalManifestDigestHex;
      addedTransitionCount =
          addExact(addedTransitionCount, transition.getAddedTransitionCount(), "Added transition count");
      addedStrictAppendTransitionCount = addExact(addedStrictAppendTransitionCount,
          transition.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
      addedUnchangedTransitionCount = addExact(addedUnchangedTransitionCount,
          transition.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
      addedReconciliationCount = addExact(addedReconciliationCount, transition.getAddedReconciliationCount(),
          "Added reconciliation count");
      addedEntryCount = addExact(addedEntryCount, transition.getAddedEntryCount(), "Added represented-entry count");
      addedStrictAppendCount = addExact(addedStrictAppendCount, transition.getAddedStrictAppendCount(),
          "Added strict-append entry count");
      addedUnchangedCount = addExact(addedUnchangedCount, transition.getAddedUnchangedCount(),
          "Added unchanged entry count");
      copy.add(transition);
    }

    if (addExact(addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        "Added transition-state count") != addedTransitionCount) {
      throw new IllegalArgumentException("Transition-ledger transition-count aggregates are inconsistent");
    }
    if (addExact(addedStrictAppendCount, addedUnchangedCount, "Added entry-state count") != addedEntryCount) {
      throw new IllegalArgumentException("Transition-ledger entry-count aggregates are inconsistent");
    }

    byte[] ledgerDigest = digest(ledgerIdentifier, chainIdentifier, manifestIdentifier, copy,
        firstPriorChainDigestHex, finalCandidateChainDigestHex, firstPriorFinalManifestDigestHex,
        finalCandidateFinalManifestDigestHex, addedTransitionCount, addedStrictAppendTransitionCount,
        addedUnchangedTransitionCount, addedReconciliationCount, addedEntryCount, addedStrictAppendCount,
        addedUnchangedCount);
    return new Result(ledgerIdentifier, chainIdentifier, manifestIdentifier, copy, firstPriorChainDigestHex,
        finalCandidateChainDigestHex, firstPriorFinalManifestDigestHex, finalCandidateFinalManifestDigestHex,
        addedTransitionCount, addedStrictAppendTransitionCount, addedUnchangedTransitionCount,
        addedReconciliationCount, addedEntryCount, addedStrictAppendCount, addedUnchangedCount, ledgerDigest);
  }

  /**
   * Verify ordered receipts against a stored ledger.
   *
   * @param ledgerIdentifier expected caller-defined ledger identity
   * @param transitions ordered qualified chain-transition receipts
   * @param ledger expected ledger
   * @return true only when all metadata and the constant-time digest match
   */
  public static boolean verify(String ledgerIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> transitions,
      Result ledger) {
    if (ledger == null) {
      throw new IllegalArgumentException("S8 manifest transition-chain transition ledger is required");
    }
    Result expected = create(ledgerIdentifier, transitions);
    return expected.ledgerIdentifier.equals(ledger.ledgerIdentifier)
        && expected.chainIdentifier.equals(ledger.chainIdentifier)
        && expected.manifestIdentifier.equals(ledger.manifestIdentifier)
        && expected.transitionCount == ledger.transitionCount
        && expected.firstPriorChainDigestHex.equals(ledger.firstPriorChainDigestHex)
        && expected.finalCandidateChainDigestHex.equals(ledger.finalCandidateChainDigestHex)
        && expected.firstPriorFinalManifestDigestHex.equals(ledger.firstPriorFinalManifestDigestHex)
        && expected.finalCandidateFinalManifestDigestHex.equals(ledger.finalCandidateFinalManifestDigestHex)
        && expected.addedTransitionCount == ledger.addedTransitionCount
        && expected.addedStrictAppendTransitionCount == ledger.addedStrictAppendTransitionCount
        && expected.addedUnchangedTransitionCount == ledger.addedUnchangedTransitionCount
        && expected.addedReconciliationCount == ledger.addedReconciliationCount
        && expected.addedEntryCount == ledger.addedEntryCount
        && expected.addedStrictAppendCount == ledger.addedStrictAppendCount
        && expected.addedUnchangedCount == ledger.addedUnchangedCount
        && MessageDigest.isEqual(expected.ledgerDigest, ledger.ledgerDigest);
  }

  /**
   * Validate one qualified transition receipt.
   *
   * @param transition receipt to validate
   */
  private static void validateTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result transition) {
    if (transition == null) {
      throw new IllegalArgumentException("Transition ledger cannot contain null receipts");
    }
    requireText(transition.getChainIdentifier(), "Transition chain identifier");
    requireText(transition.getManifestIdentifier(), "Transition manifest identifier");
    requireText(transition.getPriorChainDigestHex(), "Prior chain digest");
    requireText(transition.getCandidateChainDigestHex(), "Candidate chain digest");
    requireText(transition.getPriorFinalManifestDigestHex(), "Prior final-manifest digest");
    requireText(transition.getCandidateFinalManifestDigestHex(), "Candidate final-manifest digest");
    requireText(transition.getTransitionDigestHex(), "Transition receipt digest");
    byte[] digestBytes = transition.getTransitionDigestBytes();
    if (!DIGEST_ALGORITHM.equals(transition.getDigestAlgorithm())
        || !AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.SCHEMA_IDENTIFIER
            .equals(transition.getSchemaIdentifier())
        || digestBytes.length != 32 || !toHex(digestBytes).equals(transition.getTransitionDigestHex())) {
      throw new IllegalArgumentException("Transition receipt digest metadata is inconsistent");
    }
    if (transition.isStrictAppend() == transition.isUnchanged()) {
      throw new IllegalArgumentException("Transition receipt state flags must be exclusive");
    }
    if (transition.getAddedTransitionCount() < 0 || transition.getAddedStrictAppendTransitionCount() < 0
        || transition.getAddedUnchangedTransitionCount() < 0 || transition.getAddedReconciliationCount() < 0
        || transition.getAddedEntryCount() < 0 || transition.getAddedStrictAppendCount() < 0
        || transition.getAddedUnchangedCount() < 0) {
      throw new IllegalArgumentException("Transition receipt counts must be non-negative");
    }
    if (addExact(transition.getAddedStrictAppendTransitionCount(), transition.getAddedUnchangedTransitionCount(),
        "Transition receipt state count") != transition.getAddedTransitionCount()) {
      throw new IllegalArgumentException("Transition receipt transition counts are inconsistent");
    }
    if (addExact(transition.getAddedStrictAppendCount(), transition.getAddedUnchangedCount(),
        "Transition receipt entry-state count") != transition.getAddedEntryCount()) {
      throw new IllegalArgumentException("Transition receipt entry counts are inconsistent");
    }
  }

  /**
   * Create the canonical ledger digest.
   *
   * @return SHA-256 digest bytes
   */
  private static byte[] digest(String ledgerIdentifier, String chainIdentifier, String manifestIdentifier,
      List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> transitions,
      String firstPriorChainDigestHex, String finalCandidateChainDigestHex,
      String firstPriorFinalManifestDigestHex, String finalCandidateFinalManifestDigestHex, int addedTransitionCount,
      int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount, int addedReconciliationCount,
      int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream output = new DataOutputStream(bytes);
      writeText(output, SCHEMA_IDENTIFIER);
      writeText(output, DIGEST_ALGORITHM);
      writeText(output, ledgerIdentifier);
      writeText(output, chainIdentifier);
      writeText(output, manifestIdentifier);
      writeText(output, firstPriorChainDigestHex);
      writeText(output, finalCandidateChainDigestHex);
      writeText(output, firstPriorFinalManifestDigestHex);
      writeText(output, finalCandidateFinalManifestDigestHex);
      output.writeInt(transitions.size());
      output.writeInt(addedTransitionCount);
      output.writeInt(addedStrictAppendTransitionCount);
      output.writeInt(addedUnchangedTransitionCount);
      output.writeInt(addedReconciliationCount);
      output.writeInt(addedEntryCount);
      output.writeInt(addedStrictAppendCount);
      output.writeInt(addedUnchangedCount);
      for (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result transition : transitions) {
        byte[] transitionDigest = transition.getTransitionDigestBytes();
        output.writeInt(transitionDigest.length);
        output.write(transitionDigest);
        writeText(output, transition.getPriorChainDigestHex());
        writeText(output, transition.getCandidateChainDigestHex());
        writeText(output, transition.getPriorFinalManifestDigestHex());
        writeText(output, transition.getCandidateFinalManifestDigestHex());
        output.writeBoolean(transition.isStrictAppend());
        output.writeBoolean(transition.isUnchanged());
        output.writeInt(transition.getAddedTransitionCount());
        output.writeInt(transition.getAddedStrictAppendTransitionCount());
        output.writeInt(transition.getAddedUnchangedTransitionCount());
        output.writeInt(transition.getAddedReconciliationCount());
        output.writeInt(transition.getAddedEntryCount());
        output.writeInt(transition.getAddedStrictAppendCount());
        output.writeInt(transition.getAddedUnchangedCount());
      }
      output.flush();
      return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(bytes.toByteArray());
    } catch (IOException exception) {
      throw new IllegalStateException("Could not encode transition ledger", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  /**
   * Write one UTF-8 value with an explicit byte length.
   *
   * @param output canonical output
   * @param value value to write
   * @throws IOException if writing fails
   */
  private static void writeText(DataOutputStream output, String value) throws IOException {
    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(encoded.length);
    output.write(encoded);
  }

  /**
   * Require non-blank text.
   *
   * @param value text to validate
   * @param label diagnostic label
   */
  private static void requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
  }

  /**
   * Add two non-negative counts with overflow detection.
   *
   * @param left first count
   * @param right second count
   * @param label diagnostic label
   * @return exact sum
   */
  private static int addExact(int left, int right, String label) {
    if (left < 0 || right < 0 || left > Integer.MAX_VALUE - right) {
      throw new IllegalArgumentException(label + " is invalid or overflows");
    }
    return left + right;
  }

  /**
   * Convert digest bytes to lowercase hexadecimal.
   *
   * @param bytes digest bytes
   * @return lowercase hexadecimal
   */
  private static String toHex(byte[] bytes) {
    StringBuilder value = new StringBuilder(bytes.length * 2);
    for (byte item : bytes) {
      value.append(String.format("%02x", item & 0xff));
    }
    return value.toString();
  }

  /** Immutable transition-ledger result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String ledgerIdentifier;
    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> transitions;
    private final int transitionCount;
    private final String firstPriorChainDigestHex;
    private final String finalCandidateChainDigestHex;
    private final String firstPriorFinalManifestDigestHex;
    private final String finalCandidateFinalManifestDigestHex;
    private final int addedTransitionCount;
    private final int addedStrictAppendTransitionCount;
    private final int addedUnchangedTransitionCount;
    private final int addedReconciliationCount;
    private final int addedEntryCount;
    private final int addedStrictAppendCount;
    private final int addedUnchangedCount;
    private final byte[] ledgerDigest;

    /** Create an immutable result. */
    private Result(String ledgerIdentifier, String chainIdentifier, String manifestIdentifier,
        List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> transitions,
        String firstPriorChainDigestHex, String finalCandidateChainDigestHex,
        String firstPriorFinalManifestDigestHex, String finalCandidateFinalManifestDigestHex, int addedTransitionCount,
        int addedStrictAppendTransitionCount, int addedUnchangedTransitionCount, int addedReconciliationCount,
        int addedEntryCount, int addedStrictAppendCount, int addedUnchangedCount, byte[] ledgerDigest) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.transitions = Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result>(
              transitions));
      this.transitionCount = transitions.size();
      this.firstPriorChainDigestHex = firstPriorChainDigestHex;
      this.finalCandidateChainDigestHex = finalCandidateChainDigestHex;
      this.firstPriorFinalManifestDigestHex = firstPriorFinalManifestDigestHex;
      this.finalCandidateFinalManifestDigestHex = finalCandidateFinalManifestDigestHex;
      this.addedTransitionCount = addedTransitionCount;
      this.addedStrictAppendTransitionCount = addedStrictAppendTransitionCount;
      this.addedUnchangedTransitionCount = addedUnchangedTransitionCount;
      this.addedReconciliationCount = addedReconciliationCount;
      this.addedEntryCount = addedEntryCount;
      this.addedStrictAppendCount = addedStrictAppendCount;
      this.addedUnchangedCount = addedUnchangedCount;
      this.ledgerDigest = ledgerDigest.clone();
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

    /** @return inherited transition-chain identity */
    public String getChainIdentifier() {
      return chainIdentifier;
    }

    /** @return inherited manifest identity */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return fresh unmodifiable ordered receipt list */
    public List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> getTransitions() {
      return Collections.unmodifiableList(
          new ArrayList<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result>(
              transitions));
    }

    /** @return number of receipts */
    public int getTransitionCount() {
      return transitionCount;
    }

    /** @return first prior chain digest */
    public String getFirstPriorChainDigestHex() {
      return firstPriorChainDigestHex;
    }

    /** @return final candidate chain digest */
    public String getFinalCandidateChainDigestHex() {
      return finalCandidateChainDigestHex;
    }

    /** @return first prior final-manifest digest */
    public String getFirstPriorFinalManifestDigestHex() {
      return firstPriorFinalManifestDigestHex;
    }

    /** @return final candidate final-manifest digest */
    public String getFinalCandidateFinalManifestDigestHex() {
      return finalCandidateFinalManifestDigestHex;
    }

    /** @return aggregate added transition count */
    public int getAddedTransitionCount() {
      return addedTransitionCount;
    }

    /** @return aggregate added strict-append transition count */
    public int getAddedStrictAppendTransitionCount() {
      return addedStrictAppendTransitionCount;
    }

    /** @return aggregate added unchanged transition count */
    public int getAddedUnchangedTransitionCount() {
      return addedUnchangedTransitionCount;
    }

    /** @return aggregate added reconciliation count */
    public int getAddedReconciliationCount() {
      return addedReconciliationCount;
    }

    /** @return aggregate added represented-entry count */
    public int getAddedEntryCount() {
      return addedEntryCount;
    }

    /** @return aggregate added strict-append entry count */
    public int getAddedStrictAppendCount() {
      return addedStrictAppendCount;
    }

    /** @return aggregate added unchanged entry count */
    public int getAddedUnchangedCount() {
      return addedUnchangedCount;
    }

    /** @return lowercase SHA-256 ledger digest */
    public String getLedgerDigestHex() {
      return toHex(ledgerDigest);
    }

    /** @return defensive raw ledger digest bytes */
    public byte[] getLedgerDigestBytes() {
      return ledgerDigest.clone();
    }
  }
}
