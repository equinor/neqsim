package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Prevents a benchmark from accepting a volume repaired only by later property initialization.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class FlashBenchmarkPressureQualificationTest {
  /** A correct gas endpoint passes, while its independently perturbed volume remains a recorded failure. */
  @Test
  void invalidReturnedVolumeCannotBecomeASuccessfulBenchmark() {
    SystemInterface fluid = new SystemSrkEos(323.15, 20.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TPflash();
    double moles = fluid.getTotalNumberOfMoles();
    double mass = StabilityOptimizationBenchmark.inventoryMass(fluid);
    JsonObject valid = StabilityOptimizationBenchmark.snapshot(fluid, moles, mass);
    assertTrue(valid.get("validationPassed").getAsBoolean());

    fluid.getPhase(0).setMolarVolume(1.1 * fluid.getPhase(0).getMolarVolume());
    JsonObject invalid = StabilityOptimizationBenchmark.snapshot(fluid, moles, mass);
    assertFalse(invalid.getAsJsonObject("returnedPressureRoots").get("passed").getAsBoolean());
    assertFalse(invalid.get("validationPassed").getAsBoolean());
  }
}
