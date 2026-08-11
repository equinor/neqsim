package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RachfordRiceTest {
  private static final Logger logger = LogManager.getLogger(RachfordRiceTest.class);

  /** Logger object for class. */

  @Test
  void testCalcBeta() {
    double[] z = new double[] { 0.7, 0.3 };
    double[] K = new double[] { 2.0, 0.01 };

    try {
      RachfordRice rachfordRice = new RachfordRice();
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    try {
      String startMetod = RachfordRice.getMethod();
      RachfordRice rachfordRice = new RachfordRice();
      RachfordRice.setMethod("Nielsen2023");
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
      RachfordRice.setMethod("Michelsen2001");
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
      RachfordRice.setMethod(startMetod);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  @Test
  void testCalcBetaMethod2() {
    double[] z = new double[] { 0.7, 0.3 };
    double[] K = new double[] { 2.0, 0.01 };

    try {
      RachfordRice rachfordRice = new RachfordRice();
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  @Test
  void testCalcBetaNielsen2023NoArrayMutation() {
    double[] z = new double[] { 0.9, 0.1 };
    double[] K = new double[] { 5.0, 0.2 };
    double k0Original = K[0];
    double k1Original = K[1];

    RachfordRice rachfordRice = new RachfordRice();
    double beta = rachfordRice.calcBetaNielsen2023(K, z);

    Assertions.assertTrue(beta > 0.0 && beta < 1.0);
    Assertions.assertEquals(k0Original, K[0], 1e-12, "Input K[0] must not be mutated in-place");
    Assertions.assertEquals(k1Original, K[1], 1e-12, "Input K[1] must not be mutated in-place");
  }
}
