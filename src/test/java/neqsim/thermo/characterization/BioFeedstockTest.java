package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests for {@link BioFeedstock}. */
class BioFeedstockTest {

  @Test
  void libraryCharacterizationIsCompleteAndCopyIsIndependent() {
    BioFeedstock feedstock = BioFeedstock.library("crop_residue");
    BioFeedstock copied = feedstock.copy();

    assertTrue(feedstock.validate().isEmpty());
    assertNotSame(feedstock, copied);
    assertEquals(feedstock.getCarbonFraction(), copied.getCarbonFraction(), 1.0e-12);

    copied.setSolidsAnalysis(0.50, copied.getVolatileSolidsFraction(), copied.getMaximumVsDestruction(),
        copied.getAshFraction());
    assertEquals(0.85, feedstock.getTotalSolidsFraction(), 1.0e-12);
    assertEquals(0.50, copied.getTotalSolidsFraction(), 1.0e-12);
  }

  @Test
  void unknownLibraryFamilyIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> BioFeedstock.library("unsupported"));
  }
}
