package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Physics gates for representative full flashes affected by stability-kernel optimization. */
class StabilityOptimizationInvariantTest extends neqsim.NeqSimTest {
  @Test
  void representativeColdRepeatedAndChangedFlashesPreserveEquilibrium() {
    for (StabilityOptimizationBenchmark.FluidCase testCase : StabilityOptimizationBenchmark.cases()) {
      if (!Arrays.asList("srk-dry-gas", "pr-near-cricondenbar", "cpa-three-phase", "electrolyte-gas-brine")
          .contains(testCase.name)) {
        continue;
      }
      SystemInterface system = testCase.create(true, false);
      double originalMoles = system.getTotalNumberOfMoles();
      double originalMass = StabilityOptimizationBenchmark.inventoryMass(system);
      flashAndAssert(system, originalMoles, originalMass, testCase.name + " cold");
      int expectedPhases = "srk-dry-gas".equals(testCase.name) ? 1 : "cpa-three-phase".equals(testCase.name) ? 3 : 2;
      assertEquals(expectedPhases, system.getNumberOfPhases(), testCase.name + " cold phase topology");
      SystemInterface cold = system.clone();
      flashAndAssert(system, originalMoles, originalMass, testCase.name + " repeat");
      assertEquivalent(cold, system, testCase.name + " repeat");
      system.setTemperature(testCase.temperature + 0.25);
      system.setPressure(testCase.pressure * 0.995);
      flashAndAssert(system, originalMoles, originalMass, testCase.name + " changed");
      SystemInterface fresh = testCase.create(true, true);
      flashAndAssert(fresh, originalMoles, originalMass, testCase.name + " fresh changed");
      assertEquivalent(fresh, system, testCase.name + " changed");
    }
  }

  private void flashAndAssert(SystemInterface system, double moles, double mass, String label) {
    new ThermodynamicOperations(system).TPflash();
    JsonObject state = StabilityOptimizationBenchmark.snapshot(system, moles, mass);
    assertTrue(state.get("validationPassed").getAsBoolean(), label + ": " + state);
  }

  private void assertEquivalent(SystemInterface expected, SystemInterface actual, String label) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label);
    for (int phase = 0; phase < expected.getNumberOfPhases(); phase++) {
      String type = expected.getPhase(phase).getType().toString();
      assertTrue(actual.hasPhaseType(expected.getPhase(phase).getType()), label + " missing phase " + type);
      int actualPhase = actual.getPhaseNumberOfPhase(expected.getPhase(phase).getType());
      assertEquals(expected.getPhase(phase).getType(), actual.getPhase(actualPhase).getType(), label);
      assertEquals(expected.getBeta(phase), actual.getBeta(actualPhase), 1.0e-7, label);
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(phase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), 1.0e-7, label);
      }
    }
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1.0e-7 * Math.max(1.0, Math.abs(expected.getEnthalpy())),
        label);
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        1.0e-7 * Math.max(1.0, Math.abs(expected.getGibbsEnergy())), label);
  }
}
