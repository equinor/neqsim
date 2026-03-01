package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for bio-processing reactor equipment: StoichiometricReaction, StirredTankReactor,
 * Fermenter, and EnzymeTreatment.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioProcessingReactorTest {

  /**
   * Test StoichiometricReaction basic functionality.
   */
  @Test
  public void testStoichiometricReaction() {
    // Create a system with methane and ethane
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 1.0);
    system.addComponent("ethane", 0.5);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule("classic");

    // Create a reaction: methane + 2O2 -> CO2 + 2H2O (simplified, no O2)
    // We'll just test the stoichiometry mechanism
    StoichiometricReaction rxn = new StoichiometricReaction("TestReaction");
    rxn.addReactant("methane", 1.0);
    rxn.addProduct("CO2", 1.0);
    rxn.addProduct("water", 2.0);
    rxn.setLimitingReactant("methane");
    rxn.setConversion(0.50);

    double molesReacted = rxn.react(system);

    // 50% of 1.0 mol methane = 0.5 mol reacted
    Assertions.assertEquals(0.5, molesReacted, 1e-6, "Moles reacted should be 0.5");

    // After reaction: methane should be 0.5 mol
    double methaneMoles = system.getComponent("methane").getNumberOfmoles();
    Assertions.assertEquals(0.5, methaneMoles, 1e-6, "Methane should have 0.5 mol remaining");

    // CO2 should be 0.5 mol (1.0 * 0.5 = 0.5)
    double co2Moles = system.getComponent("CO2").getNumberOfmoles();
    Assertions.assertEquals(0.5, co2Moles, 1e-6, "CO2 should have 0.5 mol");

    // Water should be 1.0 mol (2.0 * 0.5 = 1.0)
    double waterMoles = system.getComponent("water").getNumberOfmoles();
    Assertions.assertEquals(1.0, waterMoles, 1e-6, "Water should have 1.0 mol");
  }

  /**
   * Test StoichiometricReaction conversion validation.
   */
  @Test
  public void testStoichiometricReactionConversionBounds() {
    StoichiometricReaction rxn = new StoichiometricReaction("TestRxn");
    Assertions.assertThrows(IllegalArgumentException.class, () -> rxn.setConversion(1.5));
    Assertions.assertThrows(IllegalArgumentException.class, () -> rxn.setConversion(-0.1));
  }

  /**
   * Test StoichiometricReaction toString output.
   */
  @Test
  public void testStoichiometricReactionToString() {
    StoichiometricReaction rxn = new StoichiometricReaction("Fermentation");
    rxn.addReactant("glucose", 1.0);
    rxn.addProduct("ethanol", 2.0);
    rxn.addProduct("CO2", 2.0);
    rxn.setLimitingReactant("glucose");
    rxn.setConversion(0.9);

    String str = rxn.toString();
    Assertions.assertTrue(str.contains("glucose"), "Should contain glucose");
    Assertions.assertTrue(str.contains("ethanol"), "Should contain ethanol");
    Assertions.assertTrue(str.contains("CO2"), "Should contain CO2");
    Assertions.assertTrue(str.contains("0.9"), "Should contain conversion 0.9");
  }

  /**
   * Test StirredTankReactor basic operation.
   */
  @Test
  public void testStirredTankReactorBasic() {
    // Create a simple gas system
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.1);
    system.addComponent("CO2", 0.1);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    StirredTankReactor reactor = new StirredTankReactor("CSTR", feed);
    reactor.setReactorTemperature(310.0); // ~37 C
    reactor.setVesselVolume(50.0);
    reactor.setResidenceTime(2.0, "hr");
    reactor.setAgitatorPowerPerVolume(1.5);
    reactor.run();

    // Verify outlet stream exists and has expected properties
    Assertions.assertNotNull(reactor.getOutletStream());
    Assertions.assertNotNull(reactor.getOutletStream().getThermoSystem());

    // Temperature should be set to reactor temperature
    double outTemp = reactor.getOutletStream().getThermoSystem().getTemperature();
    Assertions.assertEquals(310.0, outTemp, 0.1, "Outlet temperature should be 310 K");

    // Check agitator power
    Assertions.assertEquals(75.0, reactor.getAgitatorPower(), 1e-6,
        "Agitator power should be 1.5 * 50 = 75 kW");
  }

  /**
   * Test StirredTankReactor with a reaction.
   */
  @Test
  public void testStirredTankReactorWithReaction() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 1.0);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 0.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    StoichiometricReaction rxn = new StoichiometricReaction("MethaneConversion");
    rxn.addReactant("methane", 1.0);
    rxn.addProduct("CO2", 1.0);
    rxn.addProduct("water", 2.0);
    rxn.setLimitingReactant("methane");
    rxn.setConversion(0.5);

    StirredTankReactor reactor = new StirredTankReactor("CSTR", feed);
    reactor.addReaction(rxn);
    reactor.setReactorTemperature(350.0);
    reactor.run();

    SystemInterface outSys = reactor.getOutletStream().getThermoSystem();
    Assertions.assertNotNull(outSys);

    // Check that reaction has been applied
    double methaneMoles = outSys.getComponent("methane").getNumberOfmoles();
    Assertions.assertTrue(methaneMoles < 1.0, "Methane should be partially consumed");
  }

  /**
   * Test Fermenter basic operation.
   */
  @Test
  public void testFermenterBasic() {
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("water", 10.0);
    system.addComponent("ethanol", 0.0);
    system.addComponent("CO2", 0.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    Fermenter fermenter = new Fermenter("EtOH Fermenter", feed);
    fermenter.setReactorTemperature(273.15 + 32.0);
    fermenter.setResidenceTime(48.0, "hr");
    fermenter.setVesselVolume(200.0);
    fermenter.setAerobic(false);
    fermenter.run();

    Assertions.assertNotNull(fermenter.getOutletStream());
    Assertions.assertFalse(fermenter.isAerobic());
    Assertions.assertEquals(200.0, fermenter.getVesselVolume(), 1e-6);
  }

  /**
   * Test Fermenter aerobic mode with power calculation.
   */
  @Test
  public void testFermenterAerobicPower() {
    SystemInterface system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("water", 10.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    Fermenter fermenter = new Fermenter("Aerobic", feed);
    fermenter.setAerobic(true);
    fermenter.setAerationRate(1.0); // 1 vvm
    fermenter.setVesselVolume(100.0);
    fermenter.setReactorTemperature(310.0);
    fermenter.run();

    Assertions.assertTrue(fermenter.isAerobic());
    double totalPower = fermenter.getTotalPower();
    Assertions.assertTrue(totalPower > 0.0, "Total power should be positive for aerobic fermenter");
  }

  /**
   * Test EnzymeTreatment basic operation.
   */
  @Test
  public void testEnzymeTreatmentBasic() {
    SystemInterface system = new SystemSrkEos(323.15, 1.0); // 50 C
    system.addComponent("water", 10.0);
    system.addComponent("methane", 0.1); // proxy for substrate
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    EnzymeTreatment treatment = new EnzymeTreatment("Hydrolysis", feed);
    treatment.setEnzymeType("cellulase");
    treatment.setEnzymeLoading(20.0);
    treatment.setOptimalPH(5.0);
    treatment.setReactorTemperature(323.15);
    treatment.setResidenceTime(72.0, "hr");
    treatment.run();

    Assertions.assertNotNull(treatment.getOutletStream());
    Assertions.assertEquals("cellulase", treatment.getEnzymeType());
    Assertions.assertEquals(20.0, treatment.getEnzymeLoading(), 1e-6);

    // At optimal temperature, relative activity should be 1.0
    Assertions.assertEquals(1.0, treatment.getRelativeActivity(), 1e-6);
  }

  /**
   * Test EnzymeTreatment relative activity calculation.
   */
  @Test
  public void testEnzymeTreatmentActivity() {
    EnzymeTreatment treatment = new EnzymeTreatment("Test");
    treatment.setOptimalTemperature(323.15); // 50 C
    treatment.setReactorTemperature(333.15); // 60 C - 10 degrees off

    // With default sensitivity 0.02 and 10 degree offset: 1.0 - 0.02 * 10 = 0.8
    double activity = treatment.getRelativeActivity();
    Assertions.assertEquals(0.8, activity, 1e-6,
        "Activity should be 0.8 at 10 degrees from optimal");
  }

  /**
   * Test StirredTankReactor temperature unit conversion.
   */
  @Test
  public void testReactorTemperatureUnits() {
    StirredTankReactor reactor = new StirredTankReactor("Test");
    reactor.setReactorTemperature(100.0, "C");
    Assertions.assertEquals(373.15, reactor.getReactorTemperature(), 0.01);

    reactor.setReactorTemperature(212.0, "F");
    Assertions.assertEquals(373.15, reactor.getReactorTemperature(), 0.1);
  }

  /**
   * Test StirredTankReactor residence time unit conversion.
   */
  @Test
  public void testResidenceTimeUnits() {
    StirredTankReactor reactor = new StirredTankReactor("Test");

    reactor.setResidenceTime(60.0, "min");
    Assertions.assertEquals(1.0, reactor.getResidenceTime(), 1e-6);

    reactor.setResidenceTime(3600.0, "s");
    Assertions.assertEquals(1.0, reactor.getResidenceTime(), 1e-6);

    reactor.setResidenceTime(2.0, "hr");
    Assertions.assertEquals(2.0, reactor.getResidenceTime(), 1e-6);
  }

  /**
   * Test StirredTankReactor adiabatic mode.
   */
  @Test
  public void testStirredTankReactorAdiabatic() {
    SystemInterface system = new SystemSrkEos(350.0, 5.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.3);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    StirredTankReactor reactor = new StirredTankReactor("CSTR", feed);
    reactor.setIsothermal(false);
    reactor.run();

    // Adiabatic: heat duty should be 0
    Assertions.assertEquals(0.0, reactor.getHeatDuty(), 1e-6,
        "Adiabatic reactor should have zero heat duty");
  }
}
