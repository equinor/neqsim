package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OilMaterialBalance}.
 */
public class OilMaterialBalanceTest {

  @Test
  public void testDepletionDriveRecoversOoip() {
    double n = 50.0e6;
    double[] eo = { 0.02, 0.05, 0.09, 0.14, 0.20 };
    double[] f = new double[eo.length];
    for (int i = 0; i < eo.length; i++) {
      f[i] = n * eo[i];
    }
    OilMaterialBalance.Result r = OilMaterialBalance.fitDepletionDrive(f, eo);
    Assertions.assertEquals(n, r.getOoip(), n * 1.0e-6, "OOIP should be recovered");
    Assertions.assertTrue(r.getRSquared() > 0.9999);
  }

  @Test
  public void testGasCapDriveRecoversNandM() {
    double n = 40.0e6;
    double m = 0.4;
    double[] eo = { 0.02, 0.05, 0.09, 0.14, 0.20 };
    double[] eg = { 0.10, 0.22, 0.35, 0.50, 0.68 };
    double[] f = new double[eo.length];
    for (int i = 0; i < eo.length; i++) {
      f[i] = n * eo[i] + n * m * eg[i];
    }
    OilMaterialBalance.Result r = OilMaterialBalance.fitGasCapDrive(f, eo, eg);
    Assertions.assertEquals(n, r.getOoip(), n * 1.0e-4, "OOIP should be recovered");
    Assertions.assertEquals(m, r.getM(), 1.0e-4, "Gas-cap ratio should be recovered");
  }

  @Test
  public void testWaterDriveRecoversOoip() {
    double n = 60.0e6;
    double bw = 1.02;
    double[] eo = { 0.02, 0.05, 0.09, 0.14, 0.20 };
    double[] we = { 0.5e6, 1.5e6, 3.0e6, 5.0e6, 8.0e6 };
    double[] f = new double[eo.length];
    for (int i = 0; i < eo.length; i++) {
      f[i] = n * eo[i] + we[i] * bw;
    }
    OilMaterialBalance.Result r = OilMaterialBalance.fitWaterDrive(f, eo, we, bw);
    Assertions.assertEquals(n, r.getOoip(), n * 1.0e-3, "OOIP should be recovered for water drive");
  }

  @Test
  public void testDriveIndicesSumToUnity() {
    double n = 40.0e6;
    double m = 0.4;
    double eoTerm = 0.09;
    double egTerm = 0.35;
    double efwTerm = 0.005;
    double we = 2.0e6;
    double bw = 1.0;
    double f = OilMaterialBalance.withdrawalF(1.0e6, 500.0, 1.30, 120.0, 0.005, 0.1e6, bw);
    // Use a consistent F so indices sum to 1: build F from the terms directly.
    f = n * eoTerm + n * m * egTerm + we * bw + n * efwTerm;
    double[] di = OilMaterialBalance.driveIndices(n, m, eoTerm, egTerm, efwTerm, we, bw, f);
    double sum = di[0] + di[1] + di[2] + di[3];
    Assertions.assertEquals(1.0, sum, 1.0e-6, "Drive indices should sum to unity");
    for (double d : di) {
      Assertions.assertTrue(d >= 0.0, "Each drive index should be non-negative");
    }
  }

  @Test
  public void testMaterialBalanceTermsPositive() {
    double eo = OilMaterialBalance.eo(1.30, 1.25, 150.0, 120.0, 0.005);
    double eg = OilMaterialBalance.eg(1.25, 0.006, 0.005);
    Assertions.assertTrue(eo > 0.0, "E_o should be positive with pressure decline");
    Assertions.assertTrue(eg > 0.0, "E_g should be positive as gas expands");
  }
}
