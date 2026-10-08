package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for rounded cumulative fractions in highly separated products.
 *
 * @author NeqSim
 * @version 1.0
 */
class ProductBoilingPointDistributionTest {
  /** A heavy trace below one cumulative-fraction ulp cannot create a new distribution step. */
  @Test
  void heavyTraceDoesNotInvalidateProductDistribution() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("n-decane", 1.0e-30);
    fluid.setMixingRule("classic");
    fluid.init(0);
    ProductBoilingPointDistribution distribution = ProductBoilingPointDistribution.from(new Stream("product", fluid));
    assertEquals(1, distribution.getCumulativeMoleFractions().length);
    assertEquals(1.0, distribution.getCumulativeMoleFractions()[0], 0.0);
    assertEquals(fluid.getNormalBoilingPointTemperatures()[0], distribution.getNormalBoilingPointQuantileKelvin(1.0),
        0.0);
    assertTrue(Double.isFinite(distribution.getMeanNormalBoilingPointKelvin()));
  }

  /** Representable light traces remain even at very small mole fractions. */
  @Test
  void lightTraceRetainsItsRepresentableCumulativeStep() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 1.0e-30);
    fluid.addComponent("n-decane", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    ProductBoilingPointDistribution distribution = ProductBoilingPointDistribution.from(new Stream("product", fluid));
    assertEquals(2, distribution.getCumulativeMoleFractions().length);
    assertTrue(distribution.getCumulativeMoleFractions()[0] > 0.0);
    assertEquals(1.0, distribution.getCumulativeMoleFractions()[1], 0.0);
  }
}
