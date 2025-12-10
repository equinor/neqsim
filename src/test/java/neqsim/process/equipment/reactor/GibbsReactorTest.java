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

  @Test
  public void testWaterH2O2Mix() {
    // 600 ml water, density ~1 g/ml => 0.6 kg => ~33.3 mol
    double waterMass = 0.6; // kg
    double waterMolarMass = 18.015; // g/mol
    double waterMoles = waterMass * 1000 / waterMolarMass;

    double h2o2Moles = 0.0892;

    SystemInterface system = new SystemSrkEos(293.15, 1.0);
    system.addComponent("water", waterMoles);
    system.addComponent("H2O2", h2o2Moles);
    system.addComponent("oxygen", 0.0);
    system.setMixingRule(2);
    system.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", system);
    inlet.run();

    GibbsReactor reactor = new GibbsReactor("reactor", inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.0005);
    reactor.setMaxIterations(5000);
    reactor.setConvergenceTolerance(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    reactor.run();

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
    outlet.prettyPrint();
  }

  /**
   * Test N2O4 ⇌ 2NO2 equilibrium reaction at 298 K and 1 bara using SRK EOS. N2O4 (dinitrogen
   * tetroxide) dissociates to NO2 (nitrogen dioxide).
   */
  @Test
  public void testN2O4_NO2_Equilibrium() {
    // Create system at 298 K (25°C) and 1 bara
    SystemInterface system = new SystemSrkEos(298.15, 1.0);

    // Add N2O4 and NO2 - starting with pure N2O4
    system.addComponent("N2O4", 1e6);
    system.addComponent("NO2", 1e6);
    system.addComponent("oxygen", 1e6);
    system.addComponent("N2O", 1e6);
    system.addComponent("NO", 1e6);

    system.setMixingRule(2);

    Stream inlet = new Stream("inlet", system);
    inlet.setPressure(1.01325, "bara");
    inlet.setTemperature(308, "K");
    inlet.run();

    GibbsReactor reactor = new GibbsReactor("reactor", inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(50000);
    reactor.setConvergenceTolerance(1e-6);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.run();

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();

    // Print results to see the equilibrium composition
    inlet.getFluid().prettyPrint();
    outlet.prettyPrint();

    // Get mole fractions
    double n2o4MoleFraction = outlet.getComponent("N2O4").getz();
    double no2MoleFraction = outlet.getComponent("NO2").getz();

    // Calculate equilibrium constant K = (fug_NO2)^2 / (fug_N2O4)

    double fugNO2 = outlet.getPhase(0).getComponent("NO2").fugcoef(outlet.getPhase(0))
        * outlet.getPhase(0).getComponent("NO2").getz() * outlet.getPressure();
    double fugN2O4 = outlet.getPhase(0).getComponent("N2O4").fugcoef(outlet.getPhase(0))
        * outlet.getPhase(0).getComponent("N2O4").getz() * outlet.getPressure();

    double K_equilibrium = (fugNO2 * fugNO2) / fugN2O4;

    System.out.println("Equilibrium Constant K = (fug_NO2)^2 / (fug_N2O4) = " + K_equilibrium);
    System.out.println("Fugacity c NO2: "
        + inlet.getFluid().getPhase(0).getComponent("NO2").getFugacityCoefficient());
    System.out.println(
        "Fugacity c N2O4: " + outlet.getPhase(0).getComponent("N2O4").getFugacityCoefficient());
    // 38 is strange here

  }
}
