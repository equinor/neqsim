package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests integration of the ideal-gas model with the Gibbs reactor.
 */
public class GibbsReactorIdealGasTest {

  /**
   * Ensures Gibbs reactor can reach equilibrium for a simple ideal-gas mixture.
   */
  @Test
  public void testGibbsReactorIdealGasConversion() {
    SystemInterface system = new SystemIdealGas(298.15, 1.0);
    system.addComponent("hydrogen", 2.0);
    system.addComponent("oxygen", 1.0);
    system.addComponent("water", 0.0);

    Stream inlet = new Stream("inlet", system);
    inlet.run();

    GibbsReactor reactor = new GibbsReactor("reactor", inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(2500);
    reactor.setConvergenceTolerance(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    reactor.run();

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();

    Assertions.assertEquals(0.0, outlet.getComponent("hydrogen").getz(), 1e-6);
    Assertions.assertEquals(0.0, outlet.getComponent("oxygen").getz(), 1e-6);
    Assertions.assertEquals(1.0, outlet.getComponent("water").getz(), 1e-6);
  }
}

