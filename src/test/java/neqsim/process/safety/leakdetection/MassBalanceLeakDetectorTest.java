package neqsim.process.safety.leakdetection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for mass-balance leak detection sensitivity calculations.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class MassBalanceLeakDetectorTest {

  /**
   * Verifies minimum detectable leak rate includes measurement uncertainty and imbalance.
   */
  @Test
  void calculatesMinimumDetectableLeakRate() {
    Stream inlet = new Stream("inlet", gasFluid());
    inlet.setFlowRate(10.0, "kg/sec");
    inlet.run();

    Stream outlet = new Stream("outlet", gasFluid());
    outlet.setFlowRate(9.8, "kg/sec");
    outlet.run();

    MassBalanceLeakDetector.LeakDetectionSensitivityResult result =
        new MassBalanceLeakDetector(inlet, outlet).setFlowMeasurementUncertaintyFraction(0.005)
            .setLinepackVolumeM3(25.0).setPressureUncertaintyBara(0.1)
            .setTemperatureUncertaintyK(0.5).setDetectionWindowS(120.0).calculateSensitivity();

    assertTrue(result.getMinimumDetectableLeakRateKgPerS() > 0.2);
    assertTrue(result.getMinimumDetectableLeakFraction() > 0.0);
    assertTrue(result.toJson().contains("mass-balance"));
  }

  /**
   * Creates a simple gas fluid for leak detection tests.
   *
   * @return methane SRK fluid
   */
  private SystemInterface gasFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }
}