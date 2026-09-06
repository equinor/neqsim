package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the literature-qualified Riazi-Daubert D86/TBP reference conversion. */
class RiaziDaubertDistillationConversionTest {
  private static final double[] RECOVERY_PERCENT = { 0.0, 10.0, 30.0, 50.0, 70.0, 90.0, 95.0 };
  private static final double[] D86_C = { 36.5, 54.1, 76.9, 101.5, 131.0, 171.0, 186.5 };
  private static final double[] TBP_C = { 14.1, 33.4, 68.9, 101.6, 135.1, 180.5, 194.1 };

  @Test
  void reproducesPublishedWorkedExample() {
    double previousTbpC = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < RECOVERY_PERCENT.length; i++) {
      double convertedTbpC = RiaziDaubertDistillationConversion.convertD86ToTbpC(D86_C[i], RECOVERY_PERCENT[i]);
      assertEquals(TBP_C[i], convertedTbpC, 0.08, "published rounded TBP value");
      assertTrue(convertedTbpC > previousTbpC, "converted reference curve must be ordered");
      previousTbpC = convertedTbpC;
    }
  }

  @Test
  void inverseConversionClosesAtEveryReferencePoint() {
    for (int i = 0; i < RECOVERY_PERCENT.length; i++) {
      double tbpC = RiaziDaubertDistillationConversion.convertD86ToTbpC(D86_C[i], RECOVERY_PERCENT[i]);
      double recoveredD86C = RiaziDaubertDistillationConversion.convertTbpToD86C(tbpC, RECOVERY_PERCENT[i]);
      assertEquals(D86_C[i], recoveredD86C, 1.0e-10, "D86 inverse round trip");

      double d86C = RiaziDaubertDistillationConversion.convertTbpToD86C(tbpC, RECOVERY_PERCENT[i]);
      double recoveredTbpC = RiaziDaubertDistillationConversion.convertD86ToTbpC(d86C, RECOVERY_PERCENT[i]);
      assertEquals(tbpC, recoveredTbpC, 1.0e-10, "TBP inverse round trip");
    }
  }

  @Test
  void referenceDataIsCompleteAndDefensivelyCopied() {
    double[][] first = RiaziDaubertDistillationConversion.getReferenceData();
    double[][] second = RiaziDaubertDistillationConversion.getReferenceData();

    assertEquals(7, first.length);
    assertEquals(7, first[0].length);
    assertEquals(0.9177, first[0][1], 0.0);
    assertEquals(1.0355, first[6][2], 0.0);
    assertEquals(20.0, first[0][3], 0.0);
    assertEquals(400.0, first[6][4], 0.0);
    assertEquals("https://www.osti.gov/biblio/5212509", RiaziDaubertDistillationConversion.getSourceUri());

    first[0][1] = -1.0;
    assertEquals(0.9177, second[0][1], 0.0);
    assertEquals(0.9177, RiaziDaubertDistillationConversion.getReferenceData()[0][1], 0.0);
  }

  @Test
  void rejectsUnsupportedOrOutOfDomainInputs() {
    assertTrue(RiaziDaubertDistillationConversion.isSupportedRecoveryPoint(0.0));
    assertTrue(RiaziDaubertDistillationConversion.isSupportedRecoveryPoint(95.0));
    assertTrue(!RiaziDaubertDistillationConversion.isSupportedRecoveryPoint(5.0));
    assertTrue(!RiaziDaubertDistillationConversion.isSupportedRecoveryPoint(Double.NaN));

    assertThrows(IllegalArgumentException.class, () -> RiaziDaubertDistillationConversion.convertD86ToTbpC(100.0, 5.0));
    assertThrows(IllegalArgumentException.class,
        () -> RiaziDaubertDistillationConversion.convertD86ToTbpC(Double.NaN, 50.0));
    assertThrows(IllegalArgumentException.class, () -> RiaziDaubertDistillationConversion.convertD86ToTbpC(34.9, 10.0));
    assertThrows(IllegalArgumentException.class,
        () -> RiaziDaubertDistillationConversion.convertD86ToTbpC(400.1, 95.0));
    assertThrows(IllegalArgumentException.class,
        () -> RiaziDaubertDistillationConversion.convertTbpToD86C(-273.15, 50.0));
    assertThrows(IllegalArgumentException.class,
        () -> RiaziDaubertDistillationConversion.convertTbpToD86C(1000.0, 50.0));
  }
}
