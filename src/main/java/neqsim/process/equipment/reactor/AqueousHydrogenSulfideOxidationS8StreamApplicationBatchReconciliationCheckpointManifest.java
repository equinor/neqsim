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
 * Binds named S8 batch-reconciliation checkpoints into one ordered integrity manifest.
 *
 * <p>
 * The manifest preserves caller-defined reconciliation identities and source order without retaining or replaying the
 * underlying stream-application evidence. It is an integrity fingerprint, not a durable ledger, digital signature,
 * authentication mechanism, transaction coordinator, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest {
  /** Digest algorithm used by manifests. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-v1";

  private AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest() {
  }

  /**
   * Create one named manifest entry directly from qualified reconciliation evidence.
   *
   * @param reconciliationIdentifier caller-defined reconciliation identity
   * @param reconciliation qualified ordered reconciliation evidence
   * @return immutable named checkpoint entry
   */
  public static Entry entry(String reconciliationIdentifier,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation) {
    requireText(reconciliationIdentifier, "Reconciliation identifier");
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.Result checkpoint = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint
        .create(reconciliation);
    return new Entry(reconciliationIdentifier, checkpoint);
  }

  /**
   * Create a deterministic manifest for an ordered set of distinct reconciliation checkpoints.
   *
   * @param manifestIdentifier caller-defined manifest identity
   * @param entries ordered named checkpoint entries
   * @return immutable manifest evidence
   */
  public static Result create(String manifestIdentifier, List<Entry> entries) {
    requireText(manifestIdentifier, "Manifest identifier");
    if (entries == null || entries.isEmpty()) {
      throw new IllegalArgumentException("S8 reconciliation checkpoint manifest cannot be empty");
    }

    List<Entry> copy = new ArrayList<Entry>(entries.size());
    Set<String> identifiers = new HashSet<String>();
    Set<String> checkpointDigests = new HashSet<String>();
    int totalEntryCount = 0;
    int totalStrictAppendCount = 0;
    int totalUnchangedCount = 0;

    for (Entry entry : entries) {
      if (entry == null) {
        throw new IllegalArgumentException("S8 reconciliation checkpoint manifest entries cannot be null");
      }
      requireText(entry.reconciliationIdentifier, "Reconciliation identifier");
      if (entry.checkpoint == null) {
        throw new IllegalArgumentException("Reconciliation checkpoint is required");
      }
      if (!identifiers.add(entry.reconciliationIdentifier)) {
        throw new IllegalArgumentException("Duplicate reconciliation identifier: " + entry.reconciliationIdentifier);
      }
      if (!checkpointDigests.add(entry.checkpoint.getDigestHex())) {
        throw new IllegalArgumentException("Duplicate reconciliation checkpoint digest");
      }
      totalEntryCount = addExact(totalEntryCount, entry.checkpoint.getEntryCount(), "Total entry count");
      totalStrictAppendCount = addExact(totalStrictAppendCount, entry.checkpoint.getStrictAppendCount(),
          "Total strict-append count");
      totalUnchangedCount = addExact(totalUnchangedCount, entry.checkpoint.getUnchangedCount(),
          "Total unchanged count");
      copy.add(entry);
    }

    if (addExact(totalStrictAppendCount, totalUnchangedCount, "Total state count") != totalEntryCount) {
      throw new IllegalArgumentException("Manifest checkpoint state counts are inconsistent");
    }
    byte[] digest = digest(manifestIdentifier, copy);
    return new Result(manifestIdentifier, copy, totalEntryCount, totalStrictAppendCount, totalUnchangedCount, digest);
  }

  /**
   * Verify ordered named checkpoint entries against a stored manifest in constant time.
   *
   * @param manifestIdentifier expected caller-defined manifest identity
   * @param entries restored ordered checkpoint entries
   * @param manifest expected manifest
   * @return true only when the canonical manifest digests match
   */
  public static boolean verify(String manifestIdentifier, List<Entry> entries, Result manifest) {
    if (manifest == null) {
      throw new IllegalArgumentException("S8 reconciliation checkpoint manifest is required");
    }
    Result actual = create(manifestIdentifier, entries);
    return MessageDigest.isEqual(actual.digest.clone(), manifest.digest.clone());
  }

  private static int addExact(int left, int right, String name) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(name + " exceeds the supported integer range", exception);
    }
  }

  private static byte[] digest(String manifestIdentifier, List<Entry> entries) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, manifestIdentifier);
        output.writeInt(entries.size());
        for (Entry entry : entries) {
          writeString(output, entry.reconciliationIdentifier);
          AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.Result checkpoint = entry.checkpoint;
          writeString(output, checkpoint.getSchemaIdentifier());
          writeString(output, checkpoint.getDigestAlgorithm());
          writeBytes(output, checkpoint.getDigestBytes());
          output.writeInt(checkpoint.getEntryCount());
          output.writeInt(checkpoint.getStrictAppendCount());
          output.writeInt(checkpoint.getUnchangedCount());
          writeDouble(output, checkpoint.getPlannedS8IncrementMol());
          writeDouble(output, checkpoint.getS8ResidualMol());
          writeDouble(output, checkpoint.getS8ToleranceMol());
          writeDouble(output, checkpoint.getTotalAmountResidualMol());
          writeDouble(output, checkpoint.getTotalAmountToleranceMol());
        }
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 reconciliation checkpoint manifest", exception);
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " cannot be blank");
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

  /** Immutable named checkpoint entry. */
  public static final class Entry implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String reconciliationIdentifier;
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.Result checkpoint;

    private Entry(String reconciliationIdentifier,
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.Result checkpoint) {
      this.reconciliationIdentifier = reconciliationIdentifier;
      this.checkpoint = checkpoint;
    }

    /** @return caller-defined reconciliation identity. */
    public String getReconciliationIdentifier() {
      return reconciliationIdentifier;
    }

    /** @return immutable checkpoint evidence. */
    public AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.Result getCheckpoint() {
      return checkpoint;
    }
  }

  /** Immutable ordered reconciliation-checkpoint manifest. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String manifestIdentifier;
    private final List<Entry> entries;
    private final int totalEntryCount;
    private final int totalStrictAppendCount;
    private final int totalUnchangedCount;
    private final byte[] digest;

    private Result(String manifestIdentifier, List<Entry> entries, int totalEntryCount, int totalStrictAppendCount,
        int totalUnchangedCount, byte[] digest) {
      this.manifestIdentifier = manifestIdentifier;
      this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
      this.totalEntryCount = totalEntryCount;
      this.totalStrictAppendCount = totalStrictAppendCount;
      this.totalUnchangedCount = totalUnchangedCount;
      this.digest = digest.clone();
    }

    /** @return digest algorithm name. */
    public String getDigestAlgorithm() {
      return DIGEST_ALGORITHM;
    }

    /** @return versioned canonical-encoding identifier. */
    public String getSchemaIdentifier() {
      return SCHEMA_IDENTIFIER;
    }

    /** @return caller-defined manifest identity. */
    public String getManifestIdentifier() {
      return manifestIdentifier;
    }

    /** @return fresh unmodifiable ordered checkpoint-entry list. */
    public List<Entry> getEntries() {
      return Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    /** @return number of named reconciliation checkpoints. */
    public int getReconciliationCount() {
      return entries.size();
    }

    /** @return total number of entries represented by all checkpoints. */
    public int getTotalEntryCount() {
      return totalEntryCount;
    }

    /** @return total strict-append entry count represented by all checkpoints. */
    public int getTotalStrictAppendCount() {
      return totalStrictAppendCount;
    }

    /** @return total unchanged entry count represented by all checkpoints. */
    public int getTotalUnchangedCount() {
      return totalUnchangedCount;
    }

    /** @return lowercase hexadecimal SHA-256 fingerprint. */
    public String getDigestHex() {
      return toHex(digest);
    }

    /** @return defensive copy of the raw SHA-256 bytes. */
    public byte[] getDigestBytes() {
      return digest.clone();
    }
  }
}
