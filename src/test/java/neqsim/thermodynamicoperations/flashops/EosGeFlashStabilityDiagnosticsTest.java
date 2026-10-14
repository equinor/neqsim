package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemPitzer;

/**
 * Keeps same-model cubic-root diagnostics separate from EOS-GE phase reference states.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class EosGeFlashStabilityDiagnosticsTest {
  /**
   * A homogeneous Pitzer liquid is not compared with a different model's cubic liquid-water reference.
   *
   * @param saltMoles positive inventory of each counterion, including the salt-saturation bracket feed
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0e-20, 1.0e-6})
  void homogeneousEosGeFlashRetainsItsAqueousModelAndQualifiedInventory(double saltMoles) {
    SystemPitzer fluid = new SystemPitzer(298.15, 1.01325);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Ba++", saltMoles);
    fluid.addComponent("SO4--", saltMoles);
    fluid.init(0);
    fluid.setMixingRule("classic");
    double[] inventory = FluidEquilibriumChecks.inventory(fluid);
    for (int repetition = 0; repetition < 2; repetition++) {
      TPflash operation = new TPflash(fluid);
      operation.run();
      fluid.init(3);
      FluidEquilibriumChecks.validate(fluid, inventory);
      assertEquals(1, fluid.getNumberOfPhases());
      assertTrue(fluid.getPhase(0) instanceof PhasePitzer);
      assertEquals(PhaseType.AQUEOUS, fluid.getPhase(0).getType());
      for (double distance : operation.getLastTangentPlaneDistances()) {
        assertTrue(Double.isNaN(distance), "Same-model cubic-root distances do not qualify EOS-GE phase stability");
      }
    }
  }
}
