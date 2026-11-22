package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProfitOilShareTable}.
 *
 * @author ESOL
 * @version 1.0
 */
class ProfitOilShareTableTest {
  private static final ProfitOilShareTable TABLE = ProfitOilShareTable.brazilAnnexXx();

  @Test
  void gridHasExpectedShape() {
    assertEquals(41, TABLE.getPriceBandCount());
    assertEquals(101, TABLE.getRateBandCount());
  }

  @Test
  void referenceCellsMatchAnnex() {
    assertEquals(0.0, TABLE.adjustmentPp(50.0, 10000.0), 1e-9);
    assertEquals(13.3884, TABLE.adjustmentPp(70.0, 28000.0), 1e-4);
    assertEquals(13.4876, TABLE.adjustmentPp(110.0, 28000.0), 1e-4);
    assertEquals(-19.0754, TABLE.adjustmentPp(70.0, 5000.0), 1e-4);
  }

  @Test
  void priceBandEdges() {
    assertEquals(TABLE.adjustmentPp(49.0, 28000.0), TABLE.adjustmentPp(50.0, 28000.0), 0.0);
    assertEquals(TABLE.adjustmentPp(52.0, 28000.0), TABLE.adjustmentPp(50.004, 28000.0), 0.0);
    assertEquals(13.3012, TABLE.adjustmentPp(50.004, 28000.0), 1e-9);
    assertEquals(13.519, TABLE.adjustmentPp(250.0, 50250.0), 1e-9);
  }

  @Test
  void rateBandEdgesAndClamping() {
    assertEquals(TABLE.adjustmentPp(70.0, 500.0), TABLE.adjustmentPp(70.0, 0.0), 0.0);
    assertEquals(TABLE.adjustmentPp(70.0, 0.0), TABLE.adjustmentPp(70.0, -10.0), 0.0);
    assertEquals(TABLE.adjustmentPp(70.0, 1000.0), TABLE.adjustmentPp(70.0, 501.0), 0.0);
    assertEquals(TABLE.adjustmentPp(70.0, 50500.0), TABLE.adjustmentPp(70.0, 1.0e6), 0.0);
  }

  @Test
  void effectiveShareFloorAndCap() {
    assertEquals(0.01, TABLE.effectiveShare(0.0695, 30.0, 1000.0), 1e-12);
    assertEquals(0.25 + 0.133884, TABLE.effectiveShare(0.25, 70.0, 28000.0), 1e-6);
    assertEquals(1.0, TABLE.effectiveShare(0.95, 70.0, 28000.0), 0.0);
  }

  @Test
  void invalidTablesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ProfitOilShareTable(new double[0], new double[0][]));
    assertThrows(IllegalArgumentException.class,
        () -> new ProfitOilShareTable(new double[] {10.0, 5.0}, new double[][] {{0.0}, {0.0}}));
    assertTrue(new ProfitOilShareTable(new double[] {10.0}, new double[][] {{1.5}}).adjustmentPp(99.0, 5.0) == 1.5);
  }
}
