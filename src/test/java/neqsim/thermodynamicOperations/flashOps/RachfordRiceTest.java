package neqsim.thermodynamicOperations.flashOps;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RachfordRiceTest {
  @Test
  void testCalcBeta() {
    double[] z = new double[] {0.7, 0.3};
    double[] K = new double[] {2.0, 0.01};

    try {
      RachfordRice rachfordRice = new RachfordRice();
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      RachfordRice rachfordRice = new RachfordRice();
      rachfordRice.setMethod("Nielsen2023");
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
      rachfordRice.setMethod("Michelsen2001");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  void testCalcBetaMethod2() {
    double[] z = new double[] {0.7, 0.3};
    double[] K = new double[] {2.0, 0.01};

    try {
      RachfordRice rachfordRice = new RachfordRice();
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
