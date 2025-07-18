package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for GibbsReactor.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GibbsReactorTest {

  /**
   * Test adiabatic mode in GibbsReactor (PH flash at inlet enthalpy).
   */
  @Test
  public void testAdiabaticMode() {
    // Create a system with hydrogen, oxygen, and water at 10 bar and 350 K
    SystemInterface system = new SystemSrkEos(298, 1.0);
    system.addComponent("hydrogen", 0.1);
    system.addComponent("oxygen", 1);
    system.addComponent("water", 0);
    system.setMixingRule(2);


    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.run();

    // Create GibbsReactor in adiabatic mode
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(2500);
    reactor.setConvergenceTolerance(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    // Assert outlet temperature is close to 350.73 K
    Assertions.assertEquals(755.51, outletSystem.getTemperature(), 5);

    // Assert outlet mole fractions (rounded to 5 significant digits)
    double h2 = outletSystem.getComponent("hydrogen").getz();
    double o2 = outletSystem.getComponent("oxygen").getz();
    double h2o = outletSystem.getComponent("water").getz();

    Assertions.assertEquals(0.0, h2, 0.01);
    Assertions.assertEquals(0.9, o2, 0.05);
    Assertions.assertEquals(0.095, h2o, 0.01);
  }

  @Test
  public void testMethaneCombustion() {
    // Create a system with methane and oxygen at 298.15 K and 1 bar
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("methane", 0.1);
    system.addComponent("oxygen", 2.0);
    system.addComponent("nitrogen", 10.0);
    system.addComponent("NO2", 0.0);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule(2);
    system.init(0);

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.run();

    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();
    // Assert outlet temperature is close to 991.11 K
    org.junit.jupiter.api.Assertions.assertEquals(991.11, outletSystem.getTemperature(), 2.0);

    // Assert enthalpy of reaction is close to -35.14 kJ
    org.junit.jupiter.api.Assertions.assertEquals(-35.14, reactor.getenthalpyOfReactions(), 1.0);

    // Assert outlet mole fractions (rounded to 3 significant digits)
    double methane = outletSystem.getComponent("methane").getz();
    double oxygen = outletSystem.getComponent("oxygen").getz();
    double nitrogen = outletSystem.getComponent("nitrogen").getz();
    double no2 = outletSystem.getComponent("NO2").getz();
    double co2 = outletSystem.getComponent("CO2").getz();
    double water = outletSystem.getComponent("water").getz();

    org.junit.jupiter.api.Assertions.assertEquals(0.0, methane, 1e-6);
    org.junit.jupiter.api.Assertions.assertEquals(0.0382, oxygen, 0.01);
    org.junit.jupiter.api.Assertions.assertEquals(0.816, nitrogen, 0.01);
    org.junit.jupiter.api.Assertions.assertEquals(0.119, no2, 0.01);
    org.junit.jupiter.api.Assertions.assertEquals(0.00876, co2, 0.002);
    org.junit.jupiter.api.Assertions.assertEquals(0.0175, water, 0.002);

  }

}
