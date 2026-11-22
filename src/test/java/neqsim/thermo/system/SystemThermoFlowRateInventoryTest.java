package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
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
  /** Accurate same-rate and ordinary updates must not unnecessarily clear and rebuild the inventory. */
  @Test
  void positiveRebuildIsReservedForInaccurateDifferenceUpdates() {
    CountingFlowSystem fluid = new CountingFlowSystem();
    fluid.addComponent("methane", 9.0e17);
    fluid.addComponent("nitrogen", 1.0e17);
    fluid.setMixingRule(2);
    fluid.setTotalFlowRate(1.0, "mole/sec");
    assertEquals(1, fluid.rebuilds);
    assertEquals(1.0, fluid.getTotalNumberOfMoles(), 1.0e-12);
    assertEquals(0.9, fluid.getPhase(0).getComponent("methane").getNumberOfmoles(), 1.0e-12);
    fluid.setTotalFlowRate(1.0, "mole/sec");
    fluid.setTotalFlowRate(2.0, "mole/sec");
    assertEquals(1, fluid.rebuilds);
    assertEquals(2.0, fluid.getTotalNumberOfMoles(), 1.0e-12);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(0.9, fluid.getPhase(0).getComponent("methane").getz(), 1.0e-12);
  }

  /**
   * Counts full inventory rebuilds without introducing a thermodynamic-model override.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class CountingFlowSystem extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;
    private int rebuilds;

    /** Creates the synthetic reservoir fluid at 250 bara and 363.15 K. */
    private CountingFlowSystem() {
      super(363.15, 250.0);
    }

    /** {@inheritDoc} */
    @Override
    public void setEmptyFluid() {
      rebuilds++;
      super.setEmptyFluid();
    }
  }

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
