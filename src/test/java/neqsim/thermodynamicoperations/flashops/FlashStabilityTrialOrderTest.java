package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Requires ordinary enhanced stability trials for both homogeneous-reference root orders.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class FlashStabilityTrialOrderTest extends neqsim.NeqSimTest {
  /**
   * Checks actual trial execution rather than accepting zero-initialized tangent-plane distances.
   *
   * @param pr whether to use PR rather than SRK
   * @throws Exception if the stability analysis fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void enhancedTrialsExecuteForGasAndLiquidReference(boolean pr) throws Exception {
    for (int rule : new int[] {2, 4}) {
      for (int expectedReference : new int[] {0, 1}) {
        double pressure = expectedReference == 0 ? 1e-5 : 0.01;
        SystemInterface fluid = pr ? new SystemPrEos(268.33, pressure) : new SystemSrkEos(268.33, pressure);
        fluid.addComponent("water", 0.965);
        fluid.addComponent("MEG", 0.035);
        fluid.setMixingRule(rule);
        fluid.setEnhancedMultiPhaseCheck(true);
        fluid.init(0);
        fluid.init(1);
        TPflash operation = new TPflash(fluid);
        assertEquals(expectedReference, operation.findLowestGibbsEnergyPhase(), "Chosen homogeneous root");
        operation.minGibsPhaseLogZ = new double[2];
        operation.minGibsLogFugCoef = new double[2];
        for (int i = 0; i < 2; i++) {
          operation.minGibsPhaseLogZ[i] = Math.log(fluid.getPhase(expectedReference).getComponent(i).getz());
          operation.minGibsLogFugCoef[i] = Math
              .log(fluid.getPhase(expectedReference).getComponent(i).getFugacityCoefficient());
        }
        operation.iterations = 0;
        operation.stabilityAnalysis();
        assertTrue(operation.iterations >= 3, "Both reference orders must execute ordinary stability iterations");
      }
    }
  }
}
