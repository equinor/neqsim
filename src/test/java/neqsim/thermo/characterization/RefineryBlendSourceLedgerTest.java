package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests label-addressable source evidence for qualified refinery blend batches. */
public class RefineryBlendSourceLedgerTest {
  private static final double WATER_DENSITY_60F_KG_M3 = 999.016;

  @Test
  public void publicOediIdentifiersPreserveOrderLookupAndClosure() {
    RefineryBlendBatch batch = RefineryBlendBatch.fromMassFractions(10000.0, new double[] {0.60, 0.40},
        new double[] {0.847, 0.771});
    String[] identifiers = {"DOE/OEDI sample 50146", "DOE/OEDI sample 56337"};

    RefineryBlendSourceLedger ledger = RefineryBlendSourceLedger.fromBatch(identifiers, batch);
    RefineryBlendSourceLedger.SourceReceipt[] receipts = ledger.getSourceReceipts();

    assertSame(batch, ledger.getBatch());
    assertArrayEquals(identifiers, ledger.getSourceIdentifiers());
    assertEquals(0, receipts[0].getSourceIndex());
    assertEquals("DOE/OEDI sample 50146", receipts[0].getSourceIdentifier());
    assertEquals(0.60, receipts[0].getMassFraction(), 0.0);
    assertEquals(6000.0, receipts[0].getMassKg(), 0.0);
    assertEquals(0.847, receipts[0].getSpecificGravity(), 0.0);
    assertEquals(6000.0 / (0.847 * WATER_DENSITY_60F_KG_M3), receipts[0].getAdditiveVolumeM3At60F(), 1.0e-12);
    assertSame(receipts[1], ledger.getSourceReceipt("DOE/OEDI sample 56337"));
    assertTrue(receipts[0].isContributing());
    assertEquals(batch.getTotalMassKg(), receipts[0].getMassKg() + receipts[1].getMassKg(), 0.0);
    assertEquals(batch.getTotalAdditiveVolumeM3At60F(),
        receipts[0].getAdditiveVolumeM3At60F() + receipts[1].getAdditiveVolumeM3At60F(), 1.0e-12);

    identifiers[0] = "changed";
    receipts[0] = null;
    assertEquals("DOE/OEDI sample 50146", ledger.getSourceIdentifiers()[0]);
    assertEquals("DOE/OEDI sample 50146", ledger.getSourceReceipts()[0].getSourceIdentifier());
  }

  @Test
  public void zeroContributionRetainsIdentityWithoutFabricatedDensity() {
    RefineryBlendBatch batch = RefineryBlendBatch.fromMassFractions(10.0, new double[] {1.0, 0.0},
        new double[] {0.82, Double.NaN});

    RefineryBlendSourceLedger ledger = RefineryBlendSourceLedger.fromBatch(new String[] {"feed", "unused"}, batch);
    RefineryBlendSourceLedger.SourceReceipt unused = ledger.getSourceReceipt("unused");

    assertFalse(unused.isContributing());
    assertEquals(0.0, unused.getMassFraction(), 0.0);
    assertEquals(0.0, unused.getMassKg(), 0.0);
    assertTrue(Double.isNaN(unused.getSpecificGravity()));
    assertEquals(0.0, unused.getAdditiveVolumeM3At60F(), 0.0);
  }

  @Test
  public void invalidIdentifiersFailClosed() {
    RefineryBlendBatch batch = RefineryBlendBatch.fromMassFractions(10.0, new double[] {0.5, 0.5},
        new double[] {0.82, 0.84});

    assertThrows(NullPointerException.class, () -> RefineryBlendSourceLedger.fromBatch(new String[] {"a", "b"}, null));
    assertThrows(IllegalArgumentException.class, () -> RefineryBlendSourceLedger.fromBatch(null, batch));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendSourceLedger.fromBatch(new String[] {"only-one"}, batch));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendSourceLedger.fromBatch(new String[] {"", "b"}, batch));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendSourceLedger.fromBatch(new String[] {" a", "b"}, batch));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryBlendSourceLedger.fromBatch(new String[] {"same", "same"}, batch));

    RefineryBlendSourceLedger ledger = RefineryBlendSourceLedger.fromBatch(new String[] {"a", "b"}, batch);
    assertThrows(IllegalArgumentException.class, () -> ledger.getSourceReceipt("missing"));
  }
}
