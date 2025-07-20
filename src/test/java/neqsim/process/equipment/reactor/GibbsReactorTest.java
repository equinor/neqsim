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
  public void testAdiabaticMode0() {
    // Create a system with hydrogen, oxygen, and water at 10 bar and 350 K
    SystemInterface system = new SystemSrkEos(298, 1.0);
    system.addComponent("hydrogen", 3);
    system.addComponent("nitrogen", 1);
    system.addComponent("ammonia", 0);
    system.setMixingRule(2);


    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(200, "bara");
    inletStream.setTemperature(750, "K");
    inletStream.run();

    // Create GibbsReactor in adiabatic mode
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(2500);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    outletSystem.prettyPrint(); 
  }

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
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertEquals(986, outletSystem.getTemperature(), 5);

    // Assert outlet mole fractions (rounded to 5 significant digits)
    double h2 = outletSystem.getComponent("hydrogen").getz();
    double o2 = outletSystem.getComponent("oxygen").getz();
    double h2o = outletSystem.getComponent("water").getz();

    Assertions.assertEquals(0.0, h2, 0.01);
    Assertions.assertEquals(0.9, o2, 0.05);
    Assertions.assertEquals(0.095, h2o, 0.01);
  }


    /**
   * Test adiabatic mode in GibbsReactor (PH flash at inlet enthalpy).
   */
  @Test
  public void testAdiabaticMode2() {
    // Create a system with hydrogen, oxygen, and water at 10 bar and 350 K
    SystemInterface system = new SystemSrkEos(298, 1.0);
    system.addComponent("methane", 0.25);
    system.addComponent("oxygen", 1);
    system.addComponent("nitrogen", 1);
    system.addComponent("CO2", 0.0);
    system.addComponent("CO", 0.0);
    system.addComponent("NO", 0.0);
    system.addComponent("NO2", 0.0);
    system.addComponent("water", 0);

    system.setMixingRule(2);


    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setTemperature(1200, "K");
    inletStream.run();

    // Create GibbsReactor in adiabatic mode
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(2500);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();
    outletSystem.prettyPrint();
  }

}
