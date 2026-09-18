package neqsim.process.equipment.reactor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Creates a deterministic integrity checkpoint for one immutable S8 transfer ledger.
 *
 * <p>
 * The checkpoint hashes a versioned canonical encoding of every ledger, batch, and transfer field. It is compact
 * evidence that a restored ledger is bitwise identical to the checkpointed state. It is not a signature,
 * authentication mechanism, durable store, distributed lock, compare-and-swap operation, or exactly-once guarantee.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint {
  /** Digest algorithm used by checkpoint receipts. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /** Versioned canonical-encoding identifier. */
  public static final String SCHEMA_IDENTIFIER = "neqsim-s8-transfer-ledger-checkpoint-v1";

  private AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint() {
  }

  /**
   * Create a deterministic checkpoint for a validated ledger state.
   *
   * @param ledger immutable S8 transfer ledger
   * @return immutable checkpoint receipt
   * @throws IllegalArgumentException if the ledger is null or fails its existing validation contract
   */
  public static Result create(AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger) {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result validated = validate(ledger);
    byte[] digest = digest(validated);
    return new Result(validated.getLedgerIdentifier(), validated.getProductIdentityBasisIdentifier(),
        validated.getBatchCount(), validated.getTransferCount(), validated.getTotalSourceSulfurEquivalentMassKg(),
        validated.getTotalTransferredS8MassKg(), validated.getTotalUnallocatedSulfurEquivalentMassKg(),
        validated.getMassClosureResidualKg(), digest);
  }

  /**
   * Verify a ledger against a previously created checkpoint.
   *
   * @param ledger ledger state to verify
   * @param checkpoint expected checkpoint receipt
   * @return true only when the canonical ledger digest matches the checkpoint digest
   * @throws IllegalArgumentException if either argument is null or the ledger is invalid
   */
  public static boolean verify(AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger, Result checkpoint) {
    if (checkpoint == null) {
      throw new IllegalArgumentException("S8 transfer ledger checkpoint is required");
    }
    byte[] actual = digest(validate(ledger));
    return MessageDigest.isEqual(actual, checkpoint.digest.clone());
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result validate(
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger) {
    if (ledger == null) {
      throw new IllegalArgumentException("S8 transfer ledger is required");
    }
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(ledger.getBatches(), ledger.getLedgerIdentifier());
  }

  private static byte[] digest(AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeString(output, SCHEMA_IDENTIFIER);
        writeString(output, ledger.getLedgerIdentifier());
        writeString(output, ledger.getComponentName());
        writeString(output, ledger.getProductIdentityBasisIdentifier());
        output.writeInt(ledger.getBatchCount());
        output.writeInt(ledger.getTransferCount());

        for (AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch : ledger.getBatches()) {
          writeBatch(output, batch);
        }

        writeDouble(output, ledger.getTotalSourceSulfurEquivalentMassKg());
        writeDouble(output, ledger.getTotalTransferredS8MassKg());
        writeDouble(output, ledger.getTotalUnallocatedSulfurEquivalentMassKg());
        writeDouble(output, ledger.getMassClosureResidualKg());
      }
      return messageDigest.digest(bytes.toByteArray());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest algorithm is unavailable", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode the S8 transfer ledger checkpoint", exception);
    }
  }

  private static void writeBatch(DataOutputStream output,
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch) throws IOException {
    writeString(output, batch.getBatchIdentifier());
    writeString(output, batch.getComponentName());
    writeString(output, batch.getProductIdentityBasisIdentifier());
    output.writeInt(batch.getTransferCount());
    for (AqueousHydrogenSulfideOxidationS8Transfer.Result transfer : batch.getTransfers()) {
      writeTransfer(output, transfer);
    }
    writeDouble(output, batch.getTotalSourceSulfurEquivalentMassKg());
    writeDouble(output, batch.getTotalTransferredS8MassKg());
    writeDouble(output, batch.getTotalUnallocatedSulfurEquivalentMassKg());
    writeDouble(output, batch.getMassClosureResidualKg());
  }

  private static void writeTransfer(DataOutputStream output, AqueousHydrogenSulfideOxidationS8Transfer.Result transfer)
      throws IOException {
    writeString(output, transfer.getComponentName());
    output.writeInt(transfer.getSourceSegmentIndex());
    writeDouble(output, transfer.getDurationHours());
    writeDouble(output, transfer.getWaterInventoryKg());
    output.writeInt(transfer.getFitPath().ordinal());
    writeString(output, transfer.getAllocationBasisIdentifier());
    writeString(output, transfer.getProductIdentityBasisIdentifier());
    writeString(output, transfer.getDownstreamIdempotencyKey());
    writeDouble(output, transfer.getSourceSulfurEquivalentMassRateKgPerHour());
    writeDouble(output, transfer.getTransferredS8MassRateKgPerHour());
    writeDouble(output, transfer.getUnallocatedSulfurEquivalentMassRateKgPerHour());
    writeDouble(output, transfer.getRateClosureResidualKgPerHour());
    writeDouble(output, transfer.getSourceSulfurEquivalentMassKg());
    writeDouble(output, transfer.getTransferredS8MassKg());
    writeDouble(output, transfer.getUnallocatedSulfurEquivalentMassKg());
    writeDouble(output, transfer.getMassClosureResidualKg());
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(utf8.length);
    output.write(utf8);
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

  /** Immutable checkpoint evidence for one exact S8 transfer-ledger state. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String ledgerIdentifier;
    private final String productIdentityBasisIdentifier;
    private final int batchCount;
    private final int transferCount;
    private final double totalSourceSulfurEquivalentMassKg;
    private final double totalTransferredS8MassKg;
    private final double totalUnallocatedSulfurEquivalentMassKg;
    private final double massClosureResidualKg;
    private final byte[] digest;

    private Result(String ledgerIdentifier, String productIdentityBasisIdentifier, int batchCount, int transferCount,
        double totalSourceSulfurEquivalentMassKg, double totalTransferredS8MassKg,
        double totalUnallocatedSulfurEquivalentMassKg, double massClosureResidualKg, byte[] digest) {
      this.ledgerIdentifier = ledgerIdentifier;
      this.productIdentityBasisIdentifier = productIdentityBasisIdentifier;
      this.batchCount = batchCount;
      this.transferCount = transferCount;
      this.totalSourceSulfurEquivalentMassKg = totalSourceSulfurEquivalentMassKg;
      this.totalTransferredS8MassKg = totalTransferredS8MassKg;
      this.totalUnallocatedSulfurEquivalentMassKg = totalUnallocatedSulfurEquivalentMassKg;
      this.massClosureResidualKg = massClosureResidualKg;
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

    /** @return caller-supplied ledger identifier. */
    public String getLedgerIdentifier() {
      return ledgerIdentifier;
    }

    /** @return existing NeqSim component name represented by the ledger. */
    public String getComponentName() {
      return AqueousHydrogenSulfideOxidationS8Transfer.S8_COMPONENT_NAME;
    }

    /** @return shared caller-supplied S8 product-identity basis. */
    public String getProductIdentityBasisIdentifier() {
      return productIdentityBasisIdentifier;
    }

    /** @return number of checkpointed batches. */
    public int getBatchCount() {
      return batchCount;
    }

    /** @return number of checkpointed transfer receipts. */
    public int getTransferCount() {
      return transferCount;
    }

    /** @return checkpointed source sulfur-equivalent mass [kg S-equivalent]. */
    public double getTotalSourceSulfurEquivalentMassKg() {
      return totalSourceSulfurEquivalentMassKg;
    }

    /** @return checkpointed mass represented as the downstream S8 component [kg]. */
    public double getTotalTransferredS8MassKg() {
      return totalTransferredS8MassKg;
    }

    /** @return checkpointed unallocated sulfur-equivalent mass [kg S-equivalent]. */
    public double getTotalUnallocatedSulfurEquivalentMassKg() {
      return totalUnallocatedSulfurEquivalentMassKg;
    }

    /** @return checkpointed cumulative mass-closure residual [kg]. */
    public double getMassClosureResidualKg() {
      return massClosureResidualKg;
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
