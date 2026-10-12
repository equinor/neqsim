package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for {@link ShutInPressureEstimator}.
 *
 * @author ESOL
 * @version 1.0
 */
class ShutInPressureEstimatorTest {
  /**
   * Builds a lean natural gas.
   *
   * @return gas fluid
   */
  private SystemInterface leanGas() {
    SystemInterface fluid = new SystemPrEos(273.15 + 120.0, 300.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * The estimated column drop must lie between the drop of a light and of a dense gas gradient and agree with a hand
   * integration of rho g h at the mean density within a few percent.
   */
  @Test
  void columnDropAgreesWithHandCalculation() {
    SystemInterface fluid = leanGas();
    double pRes = 500.0;
    double sithp = ShutInPressureEstimator.estimateShutInTubingHeadPressure(fluid, pRes, 273.15 + 150.0, 277.15, 4000.0,
        40);
    double drop = pRes - sithp;
    assertTrue(drop > 50.0 && drop < 250.0, "unexpected column drop " + drop);

    SystemInterface mean = leanGas();
    mean.setTemperature(273.15 + 80.0);
    mean.setPressure(0.5 * (pRes + sithp));
    new neqsim.thermodynamicoperations.ThermodynamicOperations(mean).TPflash();
    mean.initProperties();
    double hand = mean.getDensity("kg/m3") * 9.80665 * 4000.0 / 1.0e5;
    assertEquals(hand, drop, 0.05 * hand);
  }

  /**
   * A higher reservoir pressure must give a higher shut-in pressure.
   */
  @Test
  void shutInPressureIncreasesWithReservoirPressure() {
    double low = ShutInPressureEstimator.estimateShutInTubingHeadPressure(leanGas(), 300.0, 273.15 + 120.0, 277.15,
        3000.0, 20);
    double high = ShutInPressureEstimator.estimateShutInTubingHeadPressure(leanGas(), 600.0, 273.15 + 120.0, 277.15,
        3000.0, 20);
    assertTrue(high > low);
  }

  /**
   * Rating classes follow the standard 5, 10, 15 and 20 ksi limits including the margin.
   */
  @Test
  void ratingClassification() {
    assertEquals("5 ksi", ShutInPressureEstimator.classifyPressureRating(300.0, 0.05));
    assertEquals("10 ksi", ShutInPressureEstimator.classifyPressureRating(660.0, 0.0));
    assertEquals("15 ksi", ShutInPressureEstimator.classifyPressureRating(660.0, 0.10));
    assertEquals("15 ksi", ShutInPressureEstimator.classifyPressureRating(735.0, 0.05));
    assertEquals("above 20 ksi", ShutInPressureEstimator.classifyPressureRating(1400.0, 0.0));
  }

  /**
   * Non-physical inputs are rejected.
   */
  @Test
  void invalidInputsAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> ShutInPressureEstimator.estimateShutInTubingHeadPressure(leanGas(), -1.0, 400.0, 277.0, 1000.0, 10));
    assertThrows(IllegalArgumentException.class,
        () -> ShutInPressureEstimator.estimateShutInTubingHeadPressure(null, 300.0, 400.0, 277.0, 1000.0, 10));
  }
}
