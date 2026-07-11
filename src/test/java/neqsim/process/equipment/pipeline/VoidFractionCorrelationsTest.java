package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VoidFractionCorrelations}.
 */
public class VoidFractionCorrelationsTest {

  @Test
  public void testWoldesemayatGhajarInRange() {
    double alpha = VoidFractionCorrelations.woldesemayatGhajar(5.0, 0.2, 800.0, 80.0, 0.02, 0.1, 90.0, 100.0);
    Assertions.assertTrue(alpha > 0.0 && alpha < 1.0, "Void fraction should be in (0,1), got " + alpha);
  }

  @Test
  public void testHigherGasVelocityIncreasesVoidFraction() {
    double alphaLow = VoidFractionCorrelations.woldesemayatGhajar(2.0, 0.5, 800.0, 80.0, 0.02, 0.1, 90.0, 100.0);
    double alphaHigh = VoidFractionCorrelations.woldesemayatGhajar(10.0, 0.5, 800.0, 80.0, 0.02, 0.1, 90.0, 100.0);
    Assertions.assertTrue(alphaHigh > alphaLow, "Higher gas velocity should raise void fraction");
  }

  @Test
  public void testLimitsPureGasAndPureLiquid() {
    double alphaGas = VoidFractionCorrelations.woldesemayatGhajar(5.0, 0.0, 800.0, 80.0, 0.02, 0.1, 90.0, 100.0);
    Assertions.assertEquals(1.0, alphaGas, 1.0e-9, "No liquid should give void fraction 1");
    double alphaLiquid = VoidFractionCorrelations.woldesemayatGhajar(0.0, 1.0, 800.0, 80.0, 0.02, 0.1, 90.0, 100.0);
    Assertions.assertEquals(0.0, alphaLiquid, 1.0e-9, "No gas should give void fraction 0");
  }
}
