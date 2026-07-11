package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VanEverdingenHurstAquifer}.
 */
public class VanEverdingenHurstAquiferTest {

  @Test
  public void testInfiniteActingPdMonotonic() {
    double prev = 0.0;
    double[] tDs = { 0.005, 0.05, 0.5, 5.0, 50.0, 150.0, 500.0, 5000.0 };
    for (double tD : tDs) {
      double pd = VanEverdingenHurstAquifer.infiniteActingPd(tD);
      Assertions.assertTrue(pd > prev, "P_D should increase with t_D at t_D = " + tD);
      Assertions.assertTrue(pd > 0.0);
      prev = pd;
    }
  }

  @Test
  public void testLateTimeApproachesSemilog() {
    // At large t_D the infinite-acting solution approaches 0.5(ln t_D + 0.80907).
    double tD = 1.0e5;
    double pd = VanEverdingenHurstAquifer.infiniteActingPd(tD);
    double semilog = 0.5 * (Math.log(tD) + 0.80907);
    Assertions.assertEquals(semilog, pd, semilog * 0.03, "Late-time P_D should approach the semilog line");
  }

  @Test
  public void testFiniteAquiferBounded() {
    double reD = 5.0;
    double pdInfinite = VanEverdingenHurstAquifer.dimensionlessPressure(100.0, Double.POSITIVE_INFINITY);
    double pdFinite = VanEverdingenHurstAquifer.dimensionlessPressure(100.0, reD);
    Assertions.assertTrue(pdFinite >= pdInfinite, "Bounded P_D should exceed infinite-acting at late time");
  }

  @Test
  public void testCarterTracyInfluxIncreases() {
    double[] tD = { 0.0, 10.0, 20.0, 40.0, 80.0, 160.0 };
    double[] deltaP = { 0.0, 5.0, 12.0, 22.0, 35.0, 50.0 };
    double u = VanEverdingenHurstAquifer.aquiferConstant(0.20, 1.0e-4, 30.0, 3000.0, 180.0);
    double[] we = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(tD, deltaP, u, Double.POSITIVE_INFINITY);
    Assertions.assertEquals(0.0, we[0], 1.0e-12);
    for (int i = 1; i < we.length; i++) {
      Assertions.assertTrue(we[i] >= we[i - 1], "Cumulative influx should be non-decreasing");
    }
    Assertions.assertTrue(we[we.length - 1] > 0.0, "Total influx should be positive");
  }

  @Test
  public void testDimensionlessTimePositive() {
    double tD = VanEverdingenHurstAquifer.dimensionlessTime(1.0e-13, 8.64e7, 0.2, 5.0e-4, 1.0e-9, 3000.0);
    Assertions.assertTrue(tD > 0.0);
  }

  @Test
  public void testAqutabExportFormat() {
    double[] tD = { 1.0, 10.0, 100.0, 1000.0 };
    String aqutab = VanEverdingenHurstAquifer.exportAqutab(tD, Double.POSITIVE_INFINITY);
    Assertions.assertTrue(aqutab.startsWith("AQUTAB"), "Export should start with AQUTAB keyword");
    Assertions.assertTrue(aqutab.trim().endsWith("/"), "Table should be terminated with a slash");
  }
}
