package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Additional test for GibbsReactor: methane combustion.
 */
public class GibbsReactorMethaneCombustionTest {
  @Test
  public void testMethaneCombustion() {
    // Create a system with methane and oxygen at 298.15 K and 1 bar
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("methane", 1.0);
    system.addComponent("oxygen", 2.0);
    system.addComponent("carbon dioxide", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);
    system.init(0);

    Stream inletStream = new Stream("Inlet Stream", system);
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-4);
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    // Check that methane is almost consumed, and products are formed
    double methaneFrac = outletSystem.getComponent("methane").getz();
    double oxygenFrac = outletSystem.getComponent("oxygen").getz();
    double co2Frac = outletSystem.getComponent("carbon dioxide").getz();
    double waterFrac = outletSystem.getComponent("water").getz();

    // Methane should be almost zero, CO2 and H2O should be dominant
    Assertions.assertTrue(methaneFrac < 0.01, "Methane should be almost consumed");
    Assertions.assertTrue(co2Frac > 0.3, "CO2 should be formed");
    Assertions.assertTrue(waterFrac > 0.3, "Water should be formed");
    Assertions.assertTrue(oxygenFrac < 0.4, "Oxygen should be partially consumed");
  }
}
