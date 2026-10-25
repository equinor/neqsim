package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualifies the homogeneous-reaction inventory supplied to a subsequent phase flash.
 *
 * @author NeqSim Team
 * @version 1.0
 */
class GibbsReactorInventorySynchronizationTest {
  /** Verifies active and inactive slots receive the same absolute Newton inventory before the outlet flash. */
  @Test
  void acidGasWorkingInventoryIsConsistentBeforeOutletFlash() {
    SystemInterface fluid = new SystemSrkEos(275.15, 100.0);
    fluid.addComponent("CO2", 1.0e6);
    fluid.addComponent("helium", 0.25);
    fluid.addComponent("water", 50.0);
    fluid.addComponent("NO2", 10.0);
    fluid.addComponent("oxygen", 30.0);
    fluid.addComponent("nitric acid", 0.0);
    fluid.addComponent("HNO2", 0.0);
    fluid.setMixingRule(2);
    Stream inlet = new Stream("feed", fluid);
    inlet.run();

    GibbsReactor reactor = new GibbsReactor("inventory qualification", inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setReconcileCompositionBeforeFugacity(true);
    reactor.setComponentAsInert("CO2");
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.setMaxIterations(20);
    reactor.setDampingComposition(0.01);
    InventoryCheckingStream outlet = new InventoryCheckingStream(fluid.clone());
    reactor.setOutletStream(outlet);
    reactor.run();
    assertEquals(1, outlet.flashCalls);
    assertEquals(0.25, outlet.getThermoSystem().getComponent("helium").getNumberOfmoles(), 1.0e-12,
        "Species outside the Gibbs database must retain their exact feed inventory");
  }

  /** Captures the feed to the outlet flash without conflating inventory validity with reaction convergence. */
  private static class InventoryCheckingStream extends Stream {
    private static final long serialVersionUID = 1L;
    private int flashCalls;

    /**
     * Creates the outlet consumer.
     *
     * @param fluid initial outlet fluid
     */
    InventoryCheckingStream(SystemInterface fluid) {
      super("outlet inventory check", fluid);
    }

    /**
     * Checks the normalized overall inventory in every storage slot before a phase flash can reactivate it.
     *
     * @param id calculation identifier
     */
    @Override
    public void run(UUID id) {
      flashCalls++;
      SystemInterface fluid = getThermoSystem();
      double totalInventory = 0.0;
      for (int componentIndex = 0; componentIndex < fluid.getNumberOfComponents(); componentIndex++) {
        totalInventory += fluid.getComponent(componentIndex).getNumberOfmoles();
      }
      assertEquals(totalInventory, fluid.getTotalNumberOfMoles(), 1.0e-8);
      for (PhaseInterface phase : fluid.getPhases()) {
        if (phase == null) {
          continue;
        }
        double fractionSum = 0.0;
        for (int componentIndex = 0; componentIndex < fluid.getNumberOfComponents(); componentIndex++) {
          double inventory = fluid.getComponent(componentIndex).getNumberOfmoles();
          assertEquals(inventory, phase.getComponent(componentIndex).getNumberOfmoles(), 1.0e-12);
          assertEquals(inventory / totalInventory, phase.getComponent(componentIndex).getz(), 1.0e-14,
              "Inactive slots must carry the reacted composition before they can be reused by a flash");
          fractionSum += phase.getComponent(componentIndex).getz();
        }
        assertEquals(1.0, fractionSum, 1.0e-12);
      }
    }
  }
}
