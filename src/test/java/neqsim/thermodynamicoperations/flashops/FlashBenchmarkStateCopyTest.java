package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Validation must not repair or otherwise modify the next timed operation's warm state.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class FlashBenchmarkStateCopyTest {
  /**
   * Serializes a state to detect any validation-induced mutation.
   *
   * @param fluid source system
   * @return complete serialized state
   * @throws Exception if serialization fails
   */
  private static byte[] serialized(SystemInterface fluid) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
      stream.writeObject(fluid);
    }
    return bytes.toByteArray();
  }

  /**
   * Checks ordinary, associating and ionic phase graphs remain unchanged during qualification.
   *
   * @throws Exception if a source or validation state cannot be serialized
   */
  @Test
  void validationPreservesTheCompleteOriginalStateAcrossEosFamilies() throws Exception {
    for (int model = 0; model < 3; model++) {
      SystemInterface fluid = model == 0 ? new SystemPrEos(300.0, 50.0)
          : model == 1 ? new SystemSrkCPAstatoil(298.15, 60.0) : new SystemElectrolyteCPAstatoil(298.15, 10.01325);
      fluid.addComponent("methane", model == 0 ? 0.9 : 0.1);
      fluid.addComponent(model == 0 ? "n-decane" : "water", model == 0 ? 0.1 : 1.0);
      if (model == 2) {
        fluid.addComponent("Na+", 0.001);
        fluid.addComponent("Cl-", 0.001);
        fluid.setMultiPhaseCheck(true);
      }
      fluid.setMixingRule(model == 0 ? 2 : 10);
      new ThermodynamicOperations(fluid).TPflash();
      double[] pressures = new double[fluid.getNumberOfPhases()];
      for (int phase = 0; phase < pressures.length; phase++) {
        pressures[phase] = ((PhaseEosInterface) fluid.getPhase(phase)).calcPressure();
      }
      byte[] before = serialized(fluid);
      SystemInterface copy = FlashBenchmarkStateCopy.copy(fluid);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        assertEquals(Double.doubleToLongBits(fluid.getPhase(phase).getMolarVolume()),
            Double.doubleToLongBits(copy.getPhase(phase).getMolarVolume()));
        assertEquals(pressures[phase], ((PhaseEosInterface) copy.getPhase(phase)).calcPressure(), 1e-8);
      }
      JsonObject snapshot = StabilityOptimizationBenchmark.snapshot(copy, fluid.getTotalNumberOfMoles(),
          StabilityOptimizationBenchmark.inventoryMass(fluid));
      assertTrue(snapshot.get("validationPassed").getAsBoolean(), snapshot.toString());
      assertArrayEquals(before, serialized(fluid), "Validation must preserve every serialized warm-state field");
    }
  }
}
