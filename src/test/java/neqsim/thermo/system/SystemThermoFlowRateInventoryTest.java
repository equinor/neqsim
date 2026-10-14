package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Flow scaling must preserve composition when converting reservoir inventories into stream rates.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SystemThermoFlowRateInventoryTest {
  /**
   * Repeated scaling must not subtract nearly equal large inventories or discard trace components.
   *
   * @param inventory initial total inventory in mol
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0e-12, 1.0, 1.0e6, 1.0e12, 1.0e18})
  void flowScalingPreservesComponentRatios(double inventory) {
    SystemInterface fluid = new SystemSrkEos(363.15, 250.0);
    String[] names = {"methane", "ethane", "propane", "water", "nitrogen"};
    double[] fractions = {0.904, 0.06, 0.03, 0.0059999999, 1.0e-10};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], inventory * fractions[i]);
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    for (double rate : new double[] {1.0, 1957.0, 1.0e-8, 10.0}) {
      fluid.setTotalFlowRate(rate, "mole/sec");
      assertEquals(rate, fluid.getTotalNumberOfMoles(), rate * 1.0e-12);
      for (int i = 0; i < names.length; i++) {
        assertEquals(fractions[i], fluid.getPhase(0).getComponent(i).getNumberOfmoles() / rate, fractions[i] * 1.0e-12,
            names[i]);
      }
      new ThermodynamicOperations(fluid).TPflash();
      double sum = 0.0;
      for (int i = 0; i < names.length; i++) {
        sum += fluid.getPhase(0).getComponent(i).getz();
        assertEquals(fractions[i], fluid.getPhase(0).getComponent(i).getz(), fractions[i] * 1.0e-12);
      }
      assertEquals(1.0, sum, 1.0e-12);
    }
  }
}
