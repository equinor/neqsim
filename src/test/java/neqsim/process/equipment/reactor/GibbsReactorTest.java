package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for GibbsReactor.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GibbsReactorTest {

  /**
   * Test that a component not found in the Gibbs database (e.g., TBPfraction) does not change
   * moles.
   */
  @Test
  public void testComponentNotInDatabaseMolesUnchanged() {
    SystemInterface system = new SystemSrkEos(298.15, 100.0);
    system.addComponent("methane", 0.05);
    system.addComponent("oxygen", 0.5);
    system.addComponent("CO2", 0.0);
    system.addComponent("CO", 0.0);
    system.addComponent("water", 0.0);
    // system.addTBPfraction("test", 0.5, 0.2, 0.6); // Component not in database
    system.setMixingRule(2);

    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.run();

    // Create GibbsReactor
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    // Check mole fractions against expected values (from reference table)
    double z_methane = outletSystem.getComponent("methane").getz();
    double z_oxygen = outletSystem.getComponent("oxygen").getz();
    double z_co2 = outletSystem.getComponent("CO2").getz();
    double z_co = outletSystem.getComponent("CO").getz();
    double z_water = outletSystem.getComponent("water").getz();

    // Expected values from the table
    double exp_methane = 1.81814E-6;
    double exp_oxygen = 7.27224E-1;
    double exp_co2 = 9.09176E-2;
    double exp_co = 7.92303E-6;
    double exp_water = 1.81849E-1;

    // Assert within reasonable absolute tolerances
    Assertions.assertEquals(exp_methane, z_methane, 1e-8, "methane mole fraction");
    Assertions.assertEquals(exp_oxygen, z_oxygen, 1e-3, "oxygen mole fraction");
    Assertions.assertEquals(exp_co2, z_co2, 1e-3, "CO2 mole fraction");
    Assertions.assertEquals(exp_co, z_co, 1e-8, "CO mole fraction");
    Assertions.assertEquals(exp_water, z_water, 1e-4, "water mole fraction");

    // Assert that mass balance is converged
    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be converged for TBPfraction test");
  }

  /**
   * Test sulfur formation from H2S and oxygen in methane, including SO2, SO3, H2SO4, and water.
   */
  @Test
  public void testSulfurFormation() {
    // Example: Claus process-like mixture
    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.0);
    system.addComponent("methane", 1e6);
    system.addComponent("H2S", 10);
    system.addComponent("oxygen", 2);
    system.addComponent("SO2", 0.0);
    system.addComponent("SO3", 0.0);
    system.addComponent("sulfuric acid", 0.0);
    system.addComponent("water", 0.0);
    system.addComponent("S8", 0.0);
    system.setMixingRule(2);

    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(10, "bara");
    inletStream.setTemperature(100, "C");
    inletStream.run();

    // Create GibbsReactor in isothermal mode
    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.001);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);

    // Run the reactor
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    // Assert ppm values for each component (mole fraction * 1e6)
    double ppm_methane = outletSystem.getComponent("methane").getz() * 1e6;
    double ppm_h2s = outletSystem.getComponent("H2S").getz() * 1e6;
    double ppm_oxygen = outletSystem.getComponent("oxygen").getz() * 1e6;
    double ppm_so2 = outletSystem.getComponent("SO2").getz() * 1e6;
    double ppm_so3 = outletSystem.getComponent("SO3").getz() * 1e6;
    double ppm_h2so4 = outletSystem.getComponent("sulfuric acid").getz() * 1e6;
    double ppm_water = outletSystem.getComponent("water").getz() * 1e6;
    double ppm_s = outletSystem.getComponent("S8").getz() * 1e6;

    // Assert ppm values against expected results
    Assertions.assertEquals(999989.5000303726, ppm_methane, 1e-6, "ppm_methane");
    Assertions.assertEquals(5.999872080506604, ppm_h2s, 1e-6, "ppm_h2s");
    Assertions.assertEquals(9.999894999481088E-7, ppm_oxygen, 1e-12, "ppm_oxygen");
    Assertions.assertEquals(6.283934988123905E-5, ppm_so2, 1e-12, "ppm_so2");
    Assertions.assertEquals(0.0, ppm_so3, 1e-12, "ppm_so3");
    Assertions.assertEquals(0.0, ppm_h2so4, 1e-12, "ppm_h2so4");
    Assertions.assertEquals(4.000033305357223, ppm_water, 1e-6, "ppm_water");
    Assertions.assertEquals(0.5000004000363735, ppm_s, 1e-9, "ppm_s");
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

    Assertions.assertEquals(0.055, h2, 0.01);
    Assertions.assertEquals(0.018, n2, 0.01);
    Assertions.assertEquals(0.9256, nh3, 0.02);
  }

  /**
   * Test adiabatic mode in GibbsReactor (PH flash at inlet enthalpy).
   */
  @Test
  public void testAdiabaticMode() {
    // Create a system with hydrogen, oxygen, and water at 10 bar and 350 K
    SystemInterface system = new SystemSrkEos(298, 100.0);
    system.addComponent("hydrogen", 0.1);
    system.addComponent("oxygen", 1);
    system.addComponent("water", 0);
    system.addComponent("argon", 0.05); // Add argon to test Ar element
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
    Assertions.assertEquals(934, outletSystem.getTemperature(), 5);

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
    Assertions.assertEquals(1977.1221536959, tempC, 10);

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
    Assertions.assertEquals(0.217, oxygen, 0.01);
    Assertions.assertEquals(0.44, nitrogen, 0.01);
    Assertions.assertEquals(8.70194E-2, co2, 0.05);
    Assertions.assertEquals(2.28316E-2, co, 0.05);
    Assertions.assertEquals(0.01, no, 0.05);
    Assertions.assertEquals(1.66995E-7, no2, 0.01);
    Assertions.assertEquals(2.19702E-1, water, 0.01);
  }

  /**
   * Test GibbsReactor with a custom composition including SO2, SO3, H2SO4, HNO3, and the rest as
   * CO2.
   */
  @Test
  public void testGibbsReactorCO2WithAcidGases6() {


    SystemInterface system = new SystemSrkEos(298, 1.0);
    system.addComponent("CO2", 1e6, "mole/sec");
    system.addComponent("SO3", 0.0, "mole/sec");
    system.addComponent("SO2", 300.0, "mole/sec");
    system.addComponent("NO2", 0.0, "mole/sec");
    system.addComponent("NO", 0.0, "mole/sec");
    system.addComponent("water", 130, "mole/sec");
    // system.addComponent("ammonia", 0, "mole/sec");
    system.addComponent("H2S", 0, "mole/sec");
    system.addComponent("oxygen", 275.0, "mole/sec");
    system.addComponent("sulfuric acid", 0, "mole/sec");
    system.addComponent("nitric acid", 0, "mole/sec");
    system.addComponent("HNO2", 0, "mole/sec");
    system.addComponent("NH4NO3", 0.0, "mole/sec");
    system.addComponent("NH4HSO4", 0, "mole/sec");
    system.addComponent("S8", 0, "mole/sec");

    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(20, "bara");
    inletStream.setTemperature(-25, "C");
    inletStream.run();

    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(15000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    SystemInterface outletSystem2 = null;

    // Also remove H2S from the inlet if its concentration is negligibly small (ppm threshold)
    if (inletStream.getFluid().getComponent("NO2").getz() * 1E6 > 0.01
        && inletStream.getFluid().getComponent("H2S").getz() * 1E6 > 0.01) {
      reactor.run();

      outletSystem2 = reactor.getOutletStream().getThermoSystem();

    } else {
      if (inletStream.getFluid().getComponent("oxygen").getz() * 1E6 > 0.01) {
        GibbsReactor H2Sreactor = new GibbsReactor("Gibbs Reactor", inletStream);
        H2Sreactor.setUseAllDatabaseSpecies(false);
        H2Sreactor.setDampingComposition(0.01);
        H2Sreactor.setMaxIterations(15000);
        H2Sreactor.setConvergenceTolerance(1e-3);
        H2Sreactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
        H2Sreactor.setComponentAsInert("sulfuric acid");
        H2Sreactor.setComponentAsInert("NH4HSO4");
        H2Sreactor.setComponentAsInert("SO3");
        if (inletStream.getFluid().getComponent("NO2").getz() * 1E6 < 0.01) {
          H2Sreactor.setComponentAsInert("SO2");
        }
        if (inletStream.getFluid().getComponent("SO2").getz() * 1E6 > 0.01) {
          H2Sreactor.setComponentAsInert("H2S");
        }
        H2Sreactor.run();

        GibbsReactor SO2reactor = new GibbsReactor("Gibbs Reactor", H2Sreactor.getOutletStream());
        SO2reactor.setUseAllDatabaseSpecies(false);
        SO2reactor.setDampingComposition(0.01);
        SO2reactor.setMaxIterations(15000);
        SO2reactor.setConvergenceTolerance(1e-3);
        SO2reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
        if (H2Sreactor.getOutletStream().getFluid().getComponent("oxygen").getz() > 0.01) {
          SO2reactor.setComponentAsInert("SO2");
        } else {
          SO2reactor.setComponentAsInert("oxygen");
          SO2reactor.setComponentAsInert("SO2");
        }
        SO2reactor.run();
        outletSystem2 = SO2reactor.getOutletStream().getThermoSystem();
      } else {
        reactor.setComponentAsInert("SO2");
        reactor.run();
        outletSystem2 = reactor.getOutletStream().getThermoSystem();
      }
    }
    // Optionally, print mole fractions for inspection
    System.out.println("GibbsReactor outlet composition (mole fractions):");
    for (int i = 0; i < outletSystem2.getNumberOfComponents(); i++) {
      if (outletSystem2.getComponent(i).getz() * 1e6 > 0.1) {
        System.out.println(outletSystem2.getComponent(i).getComponentName() + ": "
            + outletSystem2.getComponent(i).getz() * 1e6);
      }
    }
  }

  /**
   * Test GibbsReactor with a custom composition including SO2, SO3, H2SO4, HNO3, and the rest as
   * CO2.
   */
  @Test
  public void testGibbsReactorCO2WithAcidGases() {


    SystemInterface system = new SystemSrkEos(298, 1.0);
    system.addComponent("CO2", 1e6, "mole/sec");
    system.addComponent("SO2", 10, "mole/sec");
    system.addComponent("SO3", 0, "mole/sec");
    system.addComponent("NO2", 10.0, "mole/sec");
    system.addComponent("NO", 0, "mole/sec");
    system.addComponent("water", 9.5, "mole/sec");
    // system.addComponent("ammonia", 0, "mole/sec");
    system.addComponent("H2S", 10, "mole/sec");
    system.addComponent("oxygen", 10.0, "mole/sec");
    system.addComponent("sulfuric acid", 0, "mole/sec");
    system.addComponent("nitric acid", 0, "mole/sec");
    system.addComponent("NH4NO3", 0.0, "mole/sec");
    system.addComponent("NH4HSO4", 0, "mole/sec");
    system.addComponent("formic acid", 0, "mole/sec");
    system.addComponent("acetic acid", 0, "mole/sec");
    system.addComponent("methanol", 0, "mole/sec");
    system.addComponent("ethanol", 0, "mole/sec");
    system.addComponent("CO", 0, "mole/sec");
    // system.addComponent("hydrogen", 0, "mole/sec");
    // system.addComponent("N2O3", 0, "mole/sec");
    // system.addComponent("N2O", 0, "mole/sec");
    // system.addComponent("nitrogen", 0, "mole/sec");
    system.addComponent("NH2OH", 0, "mole/sec");
    // system.addComponent("N2H4", 0, "mole/sec");
    system.addComponent("S8", 0, "mole/sec");
    system.addComponent("HNO2", 0, "mole/sec");
    system.addComponent("MEG", 0.0, "mole/sec");
    system.addComponent("DEG", 0, "mole/sec");
    system.addComponent("TEG", 0.0, "mole/sec");
    system.addComponent("MEA", 0, "mole/sec");
    system.addComponent("MDEA", 0, "mole/sec");
    system.addComponent("DEA", 0, "mole/sec");
    // system.addComponent("methane", 0, "mole/sec");
    system.addComponent("ethane", 0, "mole/sec");
    system.addComponent("propane", 0, "mole/sec");
    system.addComponent("i-butane", 0, "mole/sec");
    system.addComponent("n-butane", 0, "mole/sec");
    system.addComponent("i-pentane", 0, "mole/sec");
    system.addComponent("n-pentane", 0, "mole/sec");
    system.addComponent("ethylene", 0, "mole/sec");
    system.addComponent("benzene", 0.0, "mole/sec");
    system.addComponent("toluene", 0.0, "mole/sec");
    system.addComponent("o-Xylene", 0.0, "mole/sec");
    system.addComponent("HCN", 0, "mole/sec");
    // system.addComponent("COS", 0, "mole/sec");
    system.addComponent("CS2", 0, "mole/sec");
    system.addComponent("argon", 0, "mole/sec");
    system.addComponent("CH2O", 0, "mole/sec");
    system.addComponent("C2H4O", 0, "mole/sec");
    system.addComponent("C2H4", 0, "mole/sec");


    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(20, "bara");
    inletStream.setTemperature(-25, "C");
    inletStream.run();


    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.05);
    reactor.setMaxIterations(5000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be converged for acid gas test");

    // Optionally, print mole fractions for inspection
    System.out.println("GibbsReactor outlet composition (mole fractions):");
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      if (outletSystem.getComponent(i).getz() * 1e6 > 0.1) {
        System.out.println(outletSystem.getComponent(i).getComponentName() + ": "
            + outletSystem.getComponent(i).getz() * 1e6);
      }
    }
  }



  /**
   * Test GibbsReactor with a custom composition including SO2, SO3, H2SO4, HNO3, and the rest as
   * CO2.
   */
  @Test
  public void testGibbsReactorCO2WithAcidGases2() {


    SystemInterface system = new SystemSrkEos(298, 1.0);
    system.addComponent("CO2", 1e6, "mole/sec");
    system.addComponent("water", 100, "mole/sec");
    system.addComponent("oxygen", 0, "mole/sec");
    system.addComponent("H2S", 30, "mole/sec");
    system.addComponent("SO2", 0.0, "mole/sec");
    system.addComponent("SO3", 0.0, "mole/sec");
    system.addComponent("NO2", 500, "mole/sec");
    system.addComponent("H+", 0, "mole/sec");
    system.addComponent("SO4--", 0, "mole/sec");
    system.addComponent("OH-", 0, "mole/sec");
    system.addComponent("NH4+", 0, "mole/sec");
    system.addComponent("NO3-", 0, "mole/sec");
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(1, "bara");
    inletStream.setTemperature(25, "C");
    inletStream.run();


    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.1);
    reactor.setMaxIterations(20000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be converged for acid gas test");

    // Optionally, print mole fractions for inspection
    System.out.println("GibbsReactor outlet composition (mole fractions):");
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      System.out.println(outletSystem.getComponent(i).getComponentName() + ": "
          + outletSystem.getComponent(i).getz() * 1e6);
    }
  }

  /**
   * Test GibbsReactor with a custom composition including SO2, SO3, H2SO4, HNO3, and the rest as
   * CO2.
   */
  @Test
  public void testGibbsReactorCO2WithAcidGases3() {


    SystemInterface system = new SystemPitzer(298, 1.0);
    system.addComponent("CO2", 1e6, "mole/sec");
    system.addComponent("water", 30, "mole/sec");
    system.addComponent("oxygen", 10, "mole/sec");
    system.addComponent("H2S", 10, "mole/sec");
    system.addComponent("SO2", 0.0, "mole/sec");
    system.addComponent("SO3", 0.0, "mole/sec");
    system.addComponent("NO2", 450, "mole/sec");
    system.addComponent("H+", 0, "mole/sec");
    system.addComponent("SO4--", 0, "mole/sec");
    system.addComponent("OH-", 0, "mole/sec");
    system.addComponent("NH4+", 0, "mole/sec");
    system.addComponent("NO3-", 0, "mole/sec");
    system.setMixingRule("classic");

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(1, "bara");
    inletStream.setTemperature(25, "C");
    inletStream.run();


    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.1);
    reactor.setMaxIterations(20000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();
    reactor.getOutletStream().getFluid().setMultiPhaseCheck(true);
    reactor.getOutletStream().run();

    reactor.getOutletStream().getFluid().prettyPrint();



    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be converged for acid gas test");

    // Optionally, print mole fractions for inspection
    System.out.println("GibbsReactor outlet composition (mole fractions):");
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      System.out.println(outletSystem.getComponent(i).getComponentName() + ": "
          + outletSystem.getComponent(i).getz() * 1e6);
    }
  }

  /**
   * Test GibbsReactor with a custom composition including SO2, SO3, H2SO4, HNO3, and the rest as
   * CO2.
   */
  @Test
  public void testGibbsReactorCO2WithAcidGases4() {


    SystemInterface system = new SystemFurstElectrolyteEos(298, 1.0);
    system.addComponent("CO2", 1e6, "mole/sec");
    system.addComponent("water", 30, "mole/sec");
    system.addComponent("oxygen", 0, "mole/sec");
    system.addComponent("H2S", 0, "mole/sec");
    system.addComponent("SO2", 0.0, "mole/sec");
    system.addComponent("SO3", 0.0, "mole/sec");
    system.addComponent("NO2", 0, "mole/sec");
    system.addComponent("H+", 0, "mole/sec");
    system.addComponent("SO4--", 0, "mole/sec");
    system.addComponent("OH-", 0, "mole/sec");
    system.addComponent("NH4+", 0, "mole/sec");
    system.addComponent("NO3-", 0, "mole/sec");
    system.setMixingRule("classic");
    // system.setMultiPhaseCheck(true);

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(1, "bara");
    inletStream.setTemperature(25, "C");
    inletStream.run();

    inletStream.getFluid().prettyPrint();


    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(2000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();
    // reactor.getOutletStream().run();

    reactor.getOutletStream().getFluid().prettyPrint();



    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be converged for acid gas test");

    // Optionally, print mole fractions for inspection
    System.out.println("GibbsReactor outlet composition (mole fractions):");
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      System.out.println(outletSystem.getComponent(i).getComponentName() + ": "
          + outletSystem.getComponent(i).getz() * 1e6);
    }
  }



  /**
   * Test GibbsReactor with a custom composition including SO2, SO3, H2SO4, HNO3, and the rest as
   * CO2.
   */
  @Test
  public void testGibbsReactorCO2WithAcidGases5() {


    SystemInterface system = new SystemSrkEos(298, 100.0);
    system.addComponent("CO2", 1e6, "mole/sec");
    system.addComponent("SO2", 0, "mole/sec");
    system.addComponent("SO3", 0, "mole/sec");
    system.addComponent("NO2", 50, "mole/sec");
    system.addComponent("NO", 0, "mole/sec");
    system.addComponent("water", 50, "mole/sec");
    system.addComponent("ammonia", 0, "mole/sec");
    system.addComponent("H2S", 50, "mole/sec");
    system.addComponent("oxygen", 0, "mole/sec");
    system.addComponent("sulfuric acid", 0, "mole/sec");
    system.addComponent("nitric acid", 0, "mole/sec");
    system.addComponent("hydrogen", 0, "mole/sec");
    system.addComponent("S8", 0, "mole/sec");
    // system.addComponent("nitrogen", 0, "mole/sec");
    system.addComponent("COS", 0, "mole/sec");
    system.setMixingRule(2);

    Stream inletStream = new Stream("Inlet Stream", system);
    inletStream.setPressure(20, "bara");
    inletStream.setTemperature(25, "C");
    inletStream.run();


    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.1);
    reactor.setMaxIterations(20000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    SystemInterface outletSystem = reactor.getOutletStream().getThermoSystem();

    Assertions.assertTrue(reactor.getMassBalanceConverged(),
        "Mass balance should be converged for acid gas test");

    // Optionally, print mole fractions for inspection
    System.out.println("GibbsReactor outlet composition (mole fractions):");
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      System.out.println(outletSystem.getComponent(i).getComponentName() + ": "
          + outletSystem.getComponent(i).getz() * 1e6);
    }
  }


}
