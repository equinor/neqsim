package neqsim.thermodynamicOperations.flashOps;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RachfordRiceTest {
  @Test
  void testCalcBeta() {

    double[] z = new double[] {0.1, 0.9};
    double[] K = new double[] {20.0, 0.1};

    try {
      Assertions.assertEquals(0.06374269005, RachfordRice.calcBeta(K, z), 1e-6);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  @Test
  void testCalcBetaMethod2() {

    double[] z = new double[] {0.7, 0.3};
    double[] K = new double[] {2.0, 0.01};

    try {
      Assertions.assertEquals(0.407070707, RachfordRice.calcBeta(K, z), 1e-6);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
