package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for BIPEstimator - Chueh-Prausnitz and Katz-Firoozabadi correlations.
 */
class BIPEstimatorTest {

  @Test
  void testChuehPrausnitzBasicCalculation() {
    // Create a simple fluid with methane and n-heptane
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("n-heptane", 0.1);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    // Calculate BIP using Chueh-Prausnitz
    double kij = BIPEstimator.estimateChuehPrausnitz(fluid.getComponent("methane"),
        fluid.getComponent("n-heptane"));

    // BIP should be positive and reasonably small (typically 0-0.1 for HC-HC)
    assertTrue(kij > 0.0, "Chueh-Prausnitz BIP should be positive");
    assertTrue(kij < 0.2, "Chueh-Prausnitz BIP should be reasonable for HC-HC pair");
  }

  @Test
  void testChuehPrausnitzSymmetry() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("ethane", 1.0);
    fluid.addComponent("propane", 1.0);
    fluid.createDatabase(true);

    double kij = BIPEstimator.estimateChuehPrausnitz(fluid.getComponent("ethane"),
        fluid.getComponent("propane"));
    double kji = BIPEstimator.estimateChuehPrausnitz(fluid.getComponent("propane"),
        fluid.getComponent("ethane"));

    assertEquals(kij, kji, 1e-10, "BIP should be symmetric: kij = kji");
  }

  @Test
  void testChuehPrausnitzSameComponent() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.createDatabase(true);

    double kii = BIPEstimator.estimateChuehPrausnitz(fluid.getComponent("methane"),
        fluid.getComponent("methane"));

    assertEquals(0.0, kii, 1e-10, "BIP for same component should be zero");
  }

  @Test
  void testKatzFiroozabadiForMethaneC7Plus() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addTBPfraction("C10", 0.1, 140.0 / 1000.0, 0.78);
    fluid.createDatabase(true);

    // Access TBP fraction by index since addTBPfraction generates a modified name
    double kij = BIPEstimator.estimateKatzFiroozabadi(fluid.getPhase(0).getComponent(0),
        fluid.getPhase(0).getComponent(1));

    // Katz-Firoozabadi returns positive values for methane-C7+ pairs
    // For MW=140 g/mol: kij = 0.0289 + 0.0429 * sqrt(140-86) ≈ 0.34
    assertTrue(kij >= 0.0, "Katz-Firoozabadi BIP should be non-negative, got: " + kij);
    assertTrue(kij < 0.5, "Katz-Firoozabadi BIP should be less than 0.5 for C10");
  }

  @Test
  void testKatzFiroozabadiFallsBackForLightComponents() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("propane", 1.0);
    fluid.createDatabase(true);

    // For light components (MW < 86), should fall back to Chueh-Prausnitz
    double kijKF = BIPEstimator.estimateKatzFiroozabadi(fluid.getComponent("methane"),
        fluid.getComponent("propane"));
    double kijCP = BIPEstimator.estimateChuehPrausnitz(fluid.getComponent("methane"),
        fluid.getComponent("propane"));

    assertEquals(kijCP, kijKF, 1e-10,
        "For light components, Katz-Firoozabadi should equal Chueh-Prausnitz");
  }

  @Test
  void testCalculateBIPMatrix() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("ethane", 0.5);
    fluid.addComponent("propane", 0.3);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    double[][] bipMatrix =
        BIPEstimator.calculateBIPMatrix(fluid, BIPEstimationMethod.CHUEH_PRAUSNITZ);

    assertEquals(3, bipMatrix.length, "Matrix should have 3 rows");
    assertEquals(3, bipMatrix[0].length, "Matrix should have 3 columns");

    // Diagonal should be zero
    for (int i = 0; i < 3; i++) {
      assertEquals(0.0, bipMatrix[i][i], 1e-10, "Diagonal elements should be zero");
    }

    // Matrix should be symmetric
    for (int i = 0; i < 3; i++) {
      for (int j = i + 1; j < 3; j++) {
        assertEquals(bipMatrix[i][j], bipMatrix[j][i], 1e-10, "Matrix should be symmetric");
      }
    }
  }

  @Test
  void testNullHandling() {
    assertEquals(0.0, BIPEstimator.estimateChuehPrausnitz(null, null), 1e-10);
    assertEquals(0.0, BIPEstimator.estimateKatzFiroozabadi(null, null), 1e-10);
  }

  @Test
  void testEstimateMethodEnum() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("n-heptane", 0.1);
    fluid.createDatabase(true);

    double kipCP = BIPEstimator.estimate(fluid.getComponent("methane"),
        fluid.getComponent("n-heptane"), BIPEstimationMethod.CHUEH_PRAUSNITZ);

    double kipKF = BIPEstimator.estimate(fluid.getComponent("methane"),
        fluid.getComponent("n-heptane"), BIPEstimationMethod.KATZ_FIROOZABADI);

    double kipDefault = BIPEstimator.estimate(fluid.getComponent("methane"),
        fluid.getComponent("n-heptane"), BIPEstimationMethod.DEFAULT);

    assertTrue(kipCP > 0.0);
    assertTrue(kipKF > 0.0);
    assertEquals(0.0, kipDefault, 1e-10);
  }
}
