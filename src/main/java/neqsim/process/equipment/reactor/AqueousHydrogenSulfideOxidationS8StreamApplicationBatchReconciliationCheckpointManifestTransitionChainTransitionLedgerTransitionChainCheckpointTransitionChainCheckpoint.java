package neqsim.process.equipment.reactor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Freezes one qualified S8 checkpoint-transition chain as an immutable checkpoint.
 *
 * <p>
 * The checkpoint preserves the upstream identities, endpoints, exact aggregate counts and raw chain digest under a
 * caller-owned checkpoint identity and sequence. It does not replay or revalidate the underlying transition receipts,
 * ledgers, chains, manifests, reconciliations or stream evidence.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint {
  /** Digest algorithm used by chain checkpoints. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical checkpoint encoding. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-transition-chain-checkpoint-transition-chain-checkpoint-v1";

  /** Prevent instantiation. */
  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint() {
  }

  /**
   * Create an immutable checkpoint from one qualified transition chain.
   *
   * @param checkpointTransitionChainCheckpointIdentifier caller-owned checkpoint identity
   * @param checkpointTransitionChainCheckpointSequence non-negative caller-owned sequence
   * @param chain qualified checkpoint-transition chain
   * @return immutable checkpoint result
   * @throws IllegalArgumentException if the identity, sequence or upstream-chain gates fail
   */
  public static Result create(String checkpointTransitionChainCheckpointIdentifier,
      long checkpointTransitionChainCheckpointSequence,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain) {
    requireText(checkpointTransitionChainCheckpointIdentifier, "Checkpoint-transition-chain checkpoint identifier");
    if (checkpointTransitionChainCheckpointSequence < 0L) {
      throw new IllegalArgumentException("Checkpoint-transition-chain checkpoint sequence cannot be negative");
    }
    validateChain(chain);
    byte[] checkpointDigest = digest(checkpointTransitionChainCheckpointIdentifier,
        checkpointTransitionChainCheckpointSequence, chain);
    return new Result(checkpointTransitionChainCheckpointIdentifier, checkpointTransitionChainCheckpointSequence,
        chain.getCheckpointTransitionChainIdentifier(), chain.getCheckpointIdentifier(),
        chain.getTransitionChainIdentifier(), chain.getLedgerIdentifier(), chain.getChainIdentifier(),
        chain.getManifestIdentifier(), chain.getFirstPriorCheckpointSequence(),
        chain.getFinalCandidateCheckpointSequence(), chain.getTotalSequenceDelta(),
        chain.getFirstPriorCheckpointDigestBytes(), chain.getFinalCandidateCheckpointDigestBytes(),
        chain.getFirstPriorTransitionChainDigestBytes(), chain.getFinalCandidateTransitionChainDigestBytes(),
        chain.getFirstPriorLedgerDigestHex(), chain.getFinalCandidateLedgerDigestHex(),
        chain.getFirstPriorChainDigestHex(), chain.getFinalCandidateChainDigestHex(),
        chain.getFirstPriorManifestDigestHex(), chain.getFinalCandidateManifestDigestHex(), chain.getTransitionCount(),
        chain.getStrictAppendReceiptCount(), chain.getUnchangedReceiptCount(), chain.getAddedReceiptCount(),
        chain.getAddedLedgerReceiptCount(), chain.getAddedTransitionCount(),
        chain.getAddedStrictAppendTransitionCount(), chain.getAddedUnchangedTransitionCount(),
        chain.getAddedReconciliationCount(), chain.getAddedEntryCount(), chain.getAddedStrictAppendCount(),
        chain.getAddedUnchangedCount(), chain.getChainDigestBytes(), checkpointDigest);
  }

  /**
   * Verify an upstream chain against a stored checkpoint result.
   *
   * @param checkpointTransitionChainCheckpointIdentifier caller-owned checkpoint identity
   * @param checkpointTransitionChainCheckpointSequence non-negative caller-owned sequence
   * @param chain qualified checkpoint-transition chain
   * @param result expected checkpoint result
   * @return true only when metadata, counts and raw digests match
   */
  public static boolean verify(String checkpointTransitionChainCheckpointIdentifier,
      long checkpointTransitionChainCheckpointSequence,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain,
      Result result) {
    if (result == null) {
      throw new IllegalArgumentException("S8 checkpoint-transition-chain checkpoint is required");
    }
    Result expected = create(checkpointTransitionChainCheckpointIdentifier, checkpointTransitionChainCheckpointSequence,
        chain);
    return expected.checkpointTransitionChainCheckpointIdentifier
        .equals(result.checkpointTransitionChainCheckpointIdentifier)
        && expected.checkpointTransitionChainCheckpointSequence == result.checkpointTransitionChainCheckpointSequence
        && expected.checkpointTransitionChainIdentifier.equals(result.checkpointTransitionChainIdentifier)
        && expected.checkpointIdentifier.equals(result.checkpointIdentifier)
        && expected.transitionChainIdentifier.equals(result.transitionChainIdentifier)
        && expected.ledgerIdentifier.equals(result.ledgerIdentifier)
        && expected.chainIdentifier.equals(result.chainIdentifier)
        && expected.manifestIdentifier.equals(result.manifestIdentifier)
        && expected.firstPriorCheckpointSequence == result.firstPriorCheckpointSequence
        && expected.finalCandidateCheckpointSequence == result.finalCandidateCheckpointSequence
        && expected.totalSequenceDelta == result.totalSequenceDelta
        && MessageDigest.isEqual(expected.firstPriorCheckpointDigest, result.firstPriorCheckpointDigest)
        && MessageDigest.isEqual(expected.finalCandidateCheckpointDigest, result.finalCandidateCheckpointDigest)
        && MessageDigest.isEqual(expected.firstPriorTransitionChainDigest, result.firstPriorTransitionChainDigest)
        && MessageDigest.isEqual(expected.finalCandidateTransitionChainDigest,
            result.finalCandidateTransitionChainDigest)
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
        && MessageDigest.isEqual(expected.chainDigest, result.chainDigest)
        && MessageDigest.isEqual(expected.checkpointDigest, result.checkpointDigest);
  }

  /** Validate one already-qualified transition chain without replaying its receipts. */
  private static void validateChain(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain) {
    if (chain == null) {
      throw new IllegalArgumentException("S8 checkpoint-transition chain is required");
    }
    if (!AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.DIGEST_ALGORITHM
        .equals(chain.getDigestAlgorithm())
        || !AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.SCHEMA_IDENTIFIER
            .equals(chain.getSchemaIdentifier())) {
      throw new IllegalArgumentException("Transition-chain encoding is not supported");
    }
    requireText(chain.getCheckpointTransitionChainIdentifier(), "Checkpoint-transition-chain identifier");
    requireText(chain.getCheckpointIdentifier(), "Checkpoint identifier");
    requireText(chain.getTransitionChainIdentifier(), "Transition-chain identifier");
    requireText(chain.getLedgerIdentifier(), "Ledger identifier");
    requireText(chain.getChainIdentifier(), "Chain identifier");
    requireText(chain.getManifestIdentifier(), "Manifest identifier");
    requireText(chain.getFirstPriorLedgerDigestHex(), "First prior-ledger digest");
    requireText(chain.getFinalCandidateLedgerDigestHex(), "Final candidate-ledger digest");
    requireText(chain.getFirstPriorChainDigestHex(), "First prior-chain endpoint");
    requireText(chain.getFinalCandidateChainDigestHex(), "Final candidate-chain endpoint");
    requireText(chain.getFirstPriorManifestDigestHex(), "First prior-manifest endpoint");
    requireText(chain.getFinalCandidateManifestDigestHex(), "Final candidate-manifest endpoint");
    if (chain.getFirstPriorCheckpointDigestBytes().length != 32
        || chain.getFinalCandidateCheckpointDigestBytes().length != 32
        || chain.getFirstPriorTransitionChainDigestBytes().length != 32
        || chain.getFinalCandidateTransitionChainDigestBytes().length != 32
        || chain.getChainDigestBytes().length != 32) {
      throw new IllegalArgumentException("Transition-chain digest must contain 32 bytes");
    }
    long sequenceDelta;
    try {
      sequenceDelta = Math.subtractExact(chain.getFinalCandidateCheckpointSequence(),
          chain.getFirstPriorCheckpointSequence());
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Checkpoint sequence delta exceeds the supported range", exception);
    }
    if (chain.getTotalSequenceDelta() <= 0L || sequenceDelta != chain.getTotalSequenceDelta()) {
      throw new IllegalArgumentException("Checkpoint sequence delta is inconsistent");
    }
    if (chain.getTransitionCount() <= 0) {
      throw new IllegalArgumentException("Transition-chain receipt count must be positive");
    }
    requireNonNegative(chain.getStrictAppendReceiptCount(), "Strict-append receipt count");
    requireNonNegative(chain.getUnchangedReceiptCount(), "Unchanged receipt count");
    requireNonNegative(chain.getAddedReceiptCount(), "Added receipt count");
    requireNonNegative(chain.getAddedLedgerReceiptCount(), "Added ledger-receipt count");
    requireNonNegative(chain.getAddedTransitionCount(), "Added transition count");
    requireNonNegative(chain.getAddedStrictAppendTransitionCount(), "Added strict-append transition count");
    requireNonNegative(chain.getAddedUnchangedTransitionCount(), "Added unchanged transition count");
    requireNonNegative(chain.getAddedReconciliationCount(), "Added reconciliation count");
    requireNonNegative(chain.getAddedEntryCount(), "Added represented-entry count");
    requireNonNegative(chain.getAddedStrictAppendCount(), "Added strict-append entry count");
    requireNonNegative(chain.getAddedUnchangedCount(), "Added unchanged entry count");
    if (addExact(chain.getStrictAppendReceiptCount(), chain.getUnchangedReceiptCount(),
        "Transition-chain receipt state count") != chain.getTransitionCount()) {
      throw new IllegalArgumentException("Transition-chain receipt-state counts are inconsistent");
    }
    if (addExact(chain.getAddedStrictAppendTransitionCount(), chain.getAddedUnchangedTransitionCount(),
        "Transition-chain transition state count") != chain.getAddedTransitionCount()) {
      throw new IllegalArgumentException("Transition-chain transition counts are inconsistent");
    }
    if (addExact(chain.getAddedStrictAppendCount(), chain.getAddedUnchangedCount(),
        "Transition-chain represented-entry state count") != chain.getAddedEntryCount()) {
      throw new IllegalArgumentException("Transition-chain represented-entry counts are inconsistent");
    }
  }

  /** Calculate the canonical checkpoint digest. */
  private static byte[] digest(String checkpointTransitionChainCheckpointIdentifier,
      long checkpointTransitionChainCheckpointSequence,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, checkpointTransitionChainCheckpointIdentifier);
        output.writeLong(checkpointTransitionChainCheckpointSequence);
        writeString(output, chain.getSchemaIdentifier());
        writeString(output, chain.getCheckpointTransitionChainIdentifier());
        writeString(output, chain.getCheckpointIdentifier());
        writeString(output, chain.getTransitionChainIdentifier());
        writeString(output, chain.getLedgerIdentifier());
        writeString(output, chain.getChainIdentifier());
        writeString(output, chain.getManifestIdentifier());
        output.writeLong(chain.getFirstPriorCheckpointSequence());
        output.writeLong(chain.getFinalCandidateCheckpointSequence());
        output.writeLong(chain.getTotalSequenceDelta());
        writeBytes(output, chain.getFirstPriorCheckpointDigestBytes());
        writeBytes(output, chain.getFinalCandidateCheckpointDigestBytes());
        writeBytes(output, chain.getFirstPriorTransitionChainDigestBytes());
        writeBytes(output, chain.getFinalCandidateTransitionChainDigestBytes());
        writeString(output, chain.getFirstPriorLedgerDigestHex());
        writeString(output, chain.getFinalCandidateLedgerDigestHex());
        writeString(output, chain.getFirstPriorChainDigestHex());
        writeString(output, chain.getFinalCandidateChainDigestHex());
        writeString(output, chain.getFirstPriorManifestDigestHex());
        writeString(output, chain.getFinalCandidateManifestDigestHex());
        output.writeInt(chain.getTransitionCount());
        output.writeInt(chain.getStrictAppendReceiptCount());
        output.writeInt(chain.getUnchangedReceiptCount());
        output.writeInt(chain.getAddedReceiptCount());
        output.writeInt(chain.getAddedLedgerReceiptCount());
        output.writeInt(chain.getAddedTransitionCount());
        output.writeInt(chain.getAddedStrictAppendTransitionCount());
        output.writeInt(chain.getAddedUnchangedTransitionCount());
        output.writeInt(chain.getAddedReconciliationCount());
        output.writeInt(chain.getAddedEntryCount());
        output.writeInt(chain.getAddedStrictAppendCount());
        output.writeInt(chain.getAddedUnchangedCount());
        writeBytes(output, chain.getChainDigestBytes());
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 checkpoint-transition-chain checkpoint", exception);
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

  /** Immutable checkpoint-transition-chain checkpoint. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String checkpointTransitionChainCheckpointIdentifier;
    private final long checkpointTransitionChainCheckpointSequence;
    private final String checkpointTransitionChainIdentifier;
    private final String checkpointIdentifier;
    private final String transitionChainIdentifier;
    private final String ledgerIdentifier;
    private final String chainIdentifier;
    private final String manifestIdentifier;
    private final long firstPriorCheckpointSequence;
    private final long finalCandidateCheckpointSequence;
    private final long totalSequenceDelta;
    private final byte[] firstPriorCheckpointDigest;
    private final byte[] finalCandidateCheckpointDigest;
    private final byte[] firstPriorTransitionChainDigest;
    private final byte[] finalCandidateTransitionChainDigest;
    private final String firstPriorLedgerDigestHex;
    private final String finalCandidateLedgerDigestHex;
    private final String firstPriorChainDigestHex;
    private final String finalCandidateChainDigestHex;
    private final String firstPriorManifestDigestHex;
    private final String finalCandidateManifestDigestHex;
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
    private final byte[] checkpointDigest;

    /** Create one immutable checkpoint result. */
    private Result(String checkpointTransitionChainCheckpointIdentifier,
        long checkpointTransitionChainCheckpointSequence, String checkpointTransitionChainIdentifier,
        String checkpointIdentifier, String transitionChainIdentifier, String ledgerIdentifier, String chainIdentifier,
        String manifestIdentifier, long firstPriorCheckpointSequence, long finalCandidateCheckpointSequence,
        long totalSequenceDelta, byte[] firstPriorCheckpointDigest, byte[] finalCandidateCheckpointDigest,
        byte[] firstPriorTransitionChainDigest, byte[] finalCandidateTransitionChainDigest,
        String firstPriorLedgerDigestHex, String finalCandidateLedgerDigestHex, String firstPriorChainDigestHex,
        String finalCandidateChainDigestHex, String firstPriorManifestDigestHex, String finalCandidateManifestDigestHex,
        int transitionCount, int strictAppendReceiptCount, int unchangedReceiptCount, int addedReceiptCount,
        int addedLedgerReceiptCount, int addedTransitionCount, int addedStrictAppendTransitionCount,
        int addedUnchangedTransitionCount, int addedReconciliationCount, int addedEntryCount,
        int addedStrictAppendCount, int addedUnchangedCount, byte[] chainDigest, byte[] checkpointDigest) {
      this.checkpointTransitionChainCheckpointIdentifier = checkpointTransitionChainCheckpointIdentifier;
      this.checkpointTransitionChainCheckpointSequence = checkpointTransitionChainCheckpointSequence;
      this.checkpointTransitionChainIdentifier = checkpointTransitionChainIdentifier;
      this.checkpointIdentifier = checkpointIdentifier;
      this.transitionChainIdentifier = transitionChainIdentifier;
      this.ledgerIdentifier = ledgerIdentifier;
      this.chainIdentifier = chainIdentifier;
      this.manifestIdentifier = manifestIdentifier;
      this.firstPriorCheckpointSequence = firstPriorCheckpointSequence;
      this.finalCandidateCheckpointSequence = finalCandidateCheckpointSequence;
      this.totalSequenceDelta = totalSequenceDelta;
      this.firstPriorCheckpointDigest = firstPriorCheckpointDigest.clone();
      this.finalCandidateCheckpointDigest = finalCandidateCheckpointDigest.clone();
      this.firstPriorTransitionChainDigest = firstPriorTransitionChainDigest.clone();
      this.finalCandidateTransitionChainDigest = finalCandidateTransitionChainDigest.clone();
      this.firstPriorLedgerDigestHex = firstPriorLedgerDigestHex;
      this.finalCandidateLedgerDigestHex = finalCandidateLedgerDigestHex;
      this.firstPriorChainDigestHex = firstPriorChainDigestHex;
      this.finalCandidateChainDigestHex = finalCandidateChainDigestHex;
      this.firstPriorManifestDigestHex = firstPriorManifestDigestHex;
      this.finalCandidateManifestDigestHex = finalCandidateManifestDigestHex;
      this.transitionCount = transitionCount;
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
      this.checkpointDigest = checkpointDigest.clone();
    }

    /** @return digest algorithm name. */
    public String getDigestAlgorithm() {
      return DIGEST_ALGORITHM;
    }

    /** @return versioned canonical encoding identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return caller-owned checkpoint identity. */
    public String getCheckpointTransitionChainCheckpointIdentifier() {
      return checkpointTransitionChainCheckpointIdentifier;
    }

    /** @return caller-owned checkpoint sequence. */
    public long getCheckpointTransitionChainCheckpointSequence() {
      return checkpointTransitionChainCheckpointSequence;
    }

    /** @return upstream checkpoint-transition-chain identity. */
    public String getCheckpointTransitionChainIdentifier() {
      return checkpointTransitionChainIdentifier;
    }

    /** @return inherited checkpoint-series identity. */
    public String getCheckpointIdentifier() {
      return checkpointIdentifier;
    }

    /** @return upstream transition-chain identity. */
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

    /** @return first prior checkpoint sequence. */
    public long getFirstPriorCheckpointSequence() {
      return firstPriorCheckpointSequence;
    }

    /** @return final candidate checkpoint sequence. */
    public long getFinalCandidateCheckpointSequence() {
      return finalCandidateCheckpointSequence;
    }

    /** @return exact aggregate checkpoint sequence delta. */
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
    public byte[] getFirstPriorTransitionChainDigestBytes() {
      return firstPriorTransitionChainDigest.clone();
    }

    /** @return defensive copy of the final candidate transition-chain digest. */
    public byte[] getFinalCandidateTransitionChainDigestBytes() {
      return finalCandidateTransitionChainDigest.clone();
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

    /** @return upstream transition count. */
    public int getTransitionCount() {
      return transitionCount;
    }

    /** @return upstream strict-append receipt count. */
    public int getStrictAppendReceiptCount() {
      return strictAppendReceiptCount;
    }

    /** @return upstream unchanged receipt count. */
    public int getUnchangedReceiptCount() {
      return unchangedReceiptCount;
    }

    /** @return aggregate appended checkpoint receipt count. */
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

    /** @return lowercase hexadecimal upstream chain digest. */
    public String getChainDigestHex() {
      return toHex(chainDigest);
    }

    /** @return defensive copy of raw upstream chain digest bytes. */
    public byte[] getChainDigestBytes() {
      return chainDigest.clone();
    }

    /** @return lowercase hexadecimal SHA-256 checkpoint digest. */
    public String getCheckpointDigestHex() {
      return toHex(checkpointDigest);
    }

    /** @return defensive copy of raw checkpoint digest bytes. */
    public byte[] getCheckpointDigestBytes() {
      return checkpointDigest.clone();
    }
  }
}
