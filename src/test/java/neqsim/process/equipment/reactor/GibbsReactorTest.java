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
   * Test GibbsReactor with CPA model and ppm-level NO2, water, NO, HNO3, rest CO2.
   */
  @Test
  public void testCO2model() {
    // Total moles = 1.0 for simplicity
    double totalMoles = 1e6;
    double no2 = 100 ; // 370 ppm
    double water = 160 ; // 130 ppm
    double no = 0.0;
    double hno3 = 0.0;
    double so2 = 80.0;
    double so3 = 0.0;
    double sulfuricAcid = 0.0;
    double h2s = 0;
    double co2 = totalMoles - (no2 + water + no + hno3 + h2s + so2 + so3 + sulfuricAcid);

    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.0);
    system.addComponent("NO2", no2);
    system.addComponent("water", water);
    system.addComponent("NO", no);
    system.addComponent("H2S", h2s);
    system.addComponent("nitric acid", hno3);
    system.addComponent("CO2", co2);
    system.addComponent("SO2", so2);
    system.addComponent("SO3", so3);
    system.addComponent("sulfuric acid", sulfuricAcid);
    system.setMixingRule(2);

    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(100, "bara");
    inletStream.setTemperature(25, "C");
    inletStream.run();
    inletStream.getFluid().prettyPrint();

    // Create GibbsReactor in isothermal mode (as default)
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.001);
    reactor.setMaxIterations(5000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();
    outletSystem.prettyPrint();

    // Print composition in ppm for each component
    double totalMolesOut = 0.0;
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      totalMolesOut += outletSystem.getComponent(i).getz();
    }
    System.out.println("\n--- Composition in ppm (mole fraction * 1e6) ---");
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      String name = outletSystem.getComponent(i).getComponentName();
      double ppm = outletSystem.getComponent(i).getz() * 1e6;
      System.out.printf("%20s: %12.3f ppm\n", name, ppm);
    }

  }



  /**
   * Test adiabatic mode in GibbsReactor (PH flash at inlet enthalpy).
   */
  @Test
  public void testAdiabaticMode0() {
    // Create a system with hydrogen, oxygen, and water at 10 bar and 350 K
    SystemInterface system = new SystemSrkEos(298, 100.0);
    system.addComponent("hydrogen", 1.5);
    system.addComponent("nitrogen", 0.5);
    system.addComponent("ammonia", 0);
    system.setMixingRule(2);


    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(300, "bara");
    inletStream.setTemperature(450, "K");
    inletStream.run();

    // Create GibbsReactor in adiabatic mode
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.05);
    reactor.setMaxIterations(2500);
    reactor.setConvergenceTolerance(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();


    // Assert outlet mole fractions (rounded to 5 significant digits)
    double h2 = outletSystem.getComponent("hydrogen").getz();
    double n2 = outletSystem.getComponent("nitrogen").getz();
    double nh3 = outletSystem.getComponent("ammonia").getz();

    Assertions.assertEquals(0.52, h2, 0.01);
    Assertions.assertEquals(0.175, n2, 0.01);
    Assertions.assertEquals(0.29, nh3, 0.01);
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
    SystemInterface system = new SystemSrkEos(598, 100.0);
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

    // Assert pressure (bara)
    Assertions.assertEquals(100.0, outletSystem.getPressure(), 1e-2);

    // Assert temperature (Celsius)
    double tempC = outletSystem.getTemperature() - 273.15;
    Assertions.assertEquals(1423.0898896906488, tempC, 10);

    // Assert outlet mole fractions (rounded to 5 significant digits)
    double methane = outletSystem.getComponent("methane").getz();
    double oxygen = outletSystem.getComponent("oxygen").getz();
    double nitrogen = outletSystem.getComponent("nitrogen").getz();
    double co2 = outletSystem.getComponent("CO2").getz();
    double co = outletSystem.getComponent("CO").getz();
    double no = outletSystem.getComponent("NO").getz();
    double no2 = outletSystem.getComponent("NO2").getz();
    double water = outletSystem.getComponent("water").getz();

    Assertions.assertEquals(3.41456E-16, methane, 0.0001);
    Assertions.assertEquals(8.21862E-3, oxygen, 0.01);
    Assertions.assertEquals(2.16505E-1, nitrogen, 0.01);
    Assertions.assertEquals(8.70194E-2, co2, 0.01);
    Assertions.assertEquals(2.28316E-2, co, 0.01);
    Assertions.assertEquals(4.45723E-1, no, 0.1);
    Assertions.assertEquals(1.66995E-7, no2, 0.0001);
    Assertions.assertEquals(2.19702E-1, water, 0.01);


  }

}
