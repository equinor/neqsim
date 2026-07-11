package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GasMaterialBalance}.
 */
public class GasMaterialBalanceTest {

  @Test
  public void testVolumetricFitRecoversOgip() {
    // Synthetic volumetric reservoir: p/Z = (pi/Zi)(1 - Gp/G), with G = 100, pi/Zi = 350.
    double[] pressure = { 350.0, 315.0, 280.0, 245.0, 210.0 };
    double[] z = { 1.0, 1.0, 1.0, 1.0, 1.0 };
    double[] gp = { 0.0, 10.0, 20.0, 30.0, 40.0 };

    GasMaterialBalance.Result r = GasMaterialBalance.fitVolumetric(pressure, z, gp);
    Assertions.assertEquals(100.0, r.getOgip(), 1.0e-6, "OGIP should be recovered");
    Assertions.assertEquals(350.0, r.getPiOverZi(), 1.0e-6, "pi/Zi intercept should be recovered");
    Assertions.assertTrue(r.getRSquared() > 0.9999, "Fit should be nearly perfect");
  }

  @Test
  public void testColePlotConstantForVolumetric() {
    double[] pressure = { 350.0, 315.0, 280.0, 245.0, 210.0 };
    double[] z = { 1.0, 1.0, 1.0, 1.0, 1.0 };
    double[] gp = { 0.0, 10.0, 20.0, 30.0, 40.0 };

    double[][] cole = GasMaterialBalance.colePlot(pressure, z, gp, 350.0);
    Assertions.assertEquals(4, cole[0].length);
    // F/Eg equals OGIP (100) at every point for a volumetric reservoir.
    for (int i = 0; i < cole[1].length; i++) {
      Assertions.assertEquals(100.0, cole[1][i], 1.0e-3, "Cole ratio should equal OGIP");
    }
  }

  @Test
  public void testHavlenaOdehZeroInfluxMatchesVolumetric() {
    double[] pressure = { 350.0, 315.0, 280.0, 245.0, 210.0 };
    double[] z = { 1.0, 1.0, 1.0, 1.0, 1.0 };
    double[] gp = { 0.0, 10.0, 20.0, 30.0, 40.0 };
    double[] we = { 0.0, 0.0, 0.0, 0.0, 0.0 };

    GasMaterialBalance.Result r = GasMaterialBalance.fitHavlenaOdeh(pressure, z, gp, we, 350.0);
    Assertions.assertEquals(100.0, r.getOgip(), 1.0e-2, "OGIP from Havlena-Odeh should match volumetric");
    Assertions.assertTrue(r.getRSquared() > 0.999);
  }

  @Test
  public void testInternalZFactorFitRuns() {
    double[] pressure = { 300.0, 260.0, 220.0, 180.0, 140.0 };
    double[] gp = { 0.0, 15.0, 30.0, 45.0, 60.0 };
    GasMaterialBalance.Result r = GasMaterialBalance.fitVolumetric(pressure, gp, 350.0, 0.7);
    Assertions.assertTrue(r.getOgip() > 0.0, "OGIP should be positive");
    Assertions.assertTrue(r.getRSquared() > 0.9, "Fit should be reasonable");
  }

  @Test
  public void testBgPositive() {
    double bg = GasMaterialBalance.bg(200.0, 0.9, 350.0);
    Assertions.assertTrue(bg > 0.0);
  }
}
