package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * Test class for bio-processing reactor equipment: StoichiometricReaction, StirredTankReactor,
 * Fermenter, and EnzymeTreatment. Uses COMP_EXT database for bio-relevant components such as
 * glucose, ethanol, lactic acid, succinic acid, and glycerol.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioProcessingReactorTest {

  /**
   * Enable extended component database before each test.
   */
  @BeforeEach
  public void setUp() {
    NeqSimDataBase.useExtendedComponentDatabase(true);
  }

  /**
   * Reset to default database after each test.
   */
  @AfterEach
  public void tearDown() {
    NeqSimDataBase.useExtendedComponentDatabase(false);
  }

  /**
   * Test StoichiometricReaction basic functionality.
   */
  @Test
  public void testStoichiometricReaction() {
    // Create a system with glucose, ethanol, CO2, and water for fermentation
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    system.addComponent("glucose", 1.0);
    system.addComponent("ethanol", 0.0);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 10.0);
    system.setMixingRule("classic");

    // Gay-Lussac fermentation: C6H12O6 -> 2 C2H5OH + 2 CO2
    StoichiometricReaction rxn = new StoichiometricReaction("GlucoseFermentation");
    rxn.addReactant("glucose", 1.0);
    rxn.addProduct("ethanol", 2.0);
    rxn.addProduct("CO2", 2.0);
    rxn.setLimitingReactant("glucose");
    rxn.setConversion(0.90);

    double molesReacted = rxn.react(system);

    // 90% of 1.0 mol glucose = 0.9 mol reacted
    Assertions.assertEquals(0.9, molesReacted, 1e-6, "Moles reacted should be 0.9");

    // After reaction: glucose should be 0.1 mol remaining
    double glucoseMoles = system.getComponent("glucose").getNumberOfmoles();
    Assertions.assertEquals(0.1, glucoseMoles, 1e-6, "Glucose should have 0.1 mol remaining");

    // Ethanol should be 1.8 mol (2.0 * 0.9 = 1.8)
    double ethanolMoles = system.getComponent("ethanol").getNumberOfmoles();
    Assertions.assertEquals(1.8, ethanolMoles, 1e-6, "Ethanol should have 1.8 mol");

    // CO2 should be 1.8 mol (2.0 * 0.9 = 1.8)
    double co2Moles = system.getComponent("CO2").getNumberOfmoles();
    Assertions.assertEquals(1.8, co2Moles, 1e-6, "CO2 should have 1.8 mol");
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
    // Lactic acid fermentation: C6H12O6 -> 2 C3H6O3
    StoichiometricReaction rxn = new StoichiometricReaction("LacticFermentation");
    rxn.addReactant("glucose", 1.0);
    rxn.addProduct("lactic acid", 2.0);
    rxn.setLimitingReactant("glucose");
    rxn.setConversion(0.85);

    String str = rxn.toString();
    Assertions.assertTrue(str.contains("glucose"), "Should contain glucose");
    Assertions.assertTrue(str.contains("lactic acid"), "Should contain lactic acid");
    Assertions.assertTrue(str.contains("0.85"), "Should contain conversion 0.85");
  }

  /**
   * Test StirredTankReactor basic operation.
   */
  @Test
  public void testStirredTankReactorBasic() {
    // Aqueous glucose solution for bio-reactor
    SystemInterface system = new SystemSrkEos(303.15, 1.01325);
    system.addComponent("water", 0.85);
    system.addComponent("glucose", 0.10);
    system.addComponent("ethanol", 0.05);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    StirredTankReactor reactor = new StirredTankReactor("CSTR", feed);
    reactor.setReactorTemperature(305.15); // ~32 C, typical fermentation temp
    reactor.setVesselVolume(50.0);
    reactor.setResidenceTime(24.0, "hr");
    reactor.setAgitatorPowerPerVolume(1.5);
    reactor.run();

    // Verify outlet stream exists and has expected properties
    Assertions.assertNotNull(reactor.getOutletStream());
    Assertions.assertNotNull(reactor.getOutletStream().getThermoSystem());

    // Temperature should be set to reactor temperature
    double outTemp = reactor.getOutletStream().getThermoSystem().getTemperature();
    Assertions.assertEquals(305.15, outTemp, 0.1, "Outlet temperature should be 305.15 K");

    // Check agitator power
    Assertions.assertEquals(75.0, reactor.getAgitatorPower(), 1e-6,
        "Agitator power should be 1.5 * 50 = 75 kW");
  }

  /**
   * Test StirredTankReactor with a reaction.
   */
  @Test
  public void testStirredTankReactorWithReaction() {
    // Glucose to ethanol fermentation in CSTR
    SystemInterface system = new SystemSrkEos(303.15, 1.01325);
    system.addComponent("glucose", 1.0);
    system.addComponent("ethanol", 0.0);
    system.addComponent("CO2", 0.0);
    system.addComponent("water", 10.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    StoichiometricReaction rxn = new StoichiometricReaction("EthanolFermentation");
    rxn.addReactant("glucose", 1.0);
    rxn.addProduct("ethanol", 2.0);
    rxn.addProduct("CO2", 2.0);
    rxn.setLimitingReactant("glucose");
    rxn.setConversion(0.90);

    StirredTankReactor reactor = new StirredTankReactor("CSTR", feed);
    reactor.addReaction(rxn);
    reactor.setReactorTemperature(305.15);
    reactor.run();

    SystemInterface outSys = reactor.getOutletStream().getThermoSystem();
    Assertions.assertNotNull(outSys);

    // Check that glucose has been consumed by fermentation reaction
    double glucoseMoles = outSys.getComponent("glucose").getNumberOfmoles();
    Assertions.assertTrue(glucoseMoles < 1.0, "Glucose should be partially consumed");
  }

  /**
   * Test Fermenter basic operation.
   */
  @Test
  public void testFermenterBasic() {
    // Typical ethanol fermentation broth with glucose substrate
    SystemInterface system = new SystemSrkEos(303.15, 1.01325);
    system.addComponent("water", 0.80);
    system.addComponent("glucose", 0.15);
    system.addComponent("ethanol", 0.0);
    system.addComponent("CO2", 0.0);
    system.addComponent("glycerol", 0.05);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(5000.0, "kg/hr");
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
    // Aerobic citric acid production with glucose substrate
    SystemInterface system = new SystemSrkEos(303.15, 1.01325);
    system.addComponent("water", 0.85);
    system.addComponent("glucose", 0.10);
    system.addComponent("citric acid", 0.05);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(2000.0, "kg/hr");
    feed.run();

    Fermenter fermenter = new Fermenter("CitricAcid", feed);
    fermenter.setAerobic(true);
    fermenter.setAerationRate(1.0); // 1 vvm
    fermenter.setVesselVolume(100.0);
    fermenter.setReactorTemperature(303.15);
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
    // Enzymatic hydrolysis of sucrose to glucose and fructose
    SystemInterface system = new SystemSrkEos(323.15, 1.01325); // 50 C
    system.addComponent("water", 0.80);
    system.addComponent("sucrose", 0.15);
    system.addComponent("glucose", 0.03);
    system.addComponent("fructose", 0.02);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    EnzymeTreatment treatment = new EnzymeTreatment("Invertase", feed);
    treatment.setEnzymeType("invertase");
    treatment.setEnzymeLoading(5.0);
    treatment.setOptimalPH(4.5);
    treatment.setReactorTemperature(323.15);
    treatment.setResidenceTime(4.0, "hr");
    treatment.run();

    Assertions.assertNotNull(treatment.getOutletStream());
    Assertions.assertEquals("invertase", treatment.getEnzymeType());
    Assertions.assertEquals(5.0, treatment.getEnzymeLoading(), 1e-6);

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
    // Adiabatic reactor with succinic acid production broth
    SystemInterface system = new SystemSrkEos(310.0, 1.01325);
    system.addComponent("water", 0.85);
    system.addComponent("succinic acid", 0.10);
    system.addComponent("acetic acid", 0.05);
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
