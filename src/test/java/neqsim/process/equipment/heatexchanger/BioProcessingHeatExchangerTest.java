package neqsim.process.equipment.heatexchanger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * Test class for bio-processing heat exchanger equipment: MultiEffectEvaporator and Dryer. Uses
 * COMP_EXT database for bio-relevant components such as glucose, d-sorbitol, glycerol, and other
 * bio-processing solutes.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioProcessingHeatExchangerTest {

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
   * Test MultiEffectEvaporator basic operation.
   */
  @Test
  public void testMultiEffectEvaporatorBasic() {
    // Concentrating a glucose solution (sugar syrup evaporation)
    SystemInterface system = new SystemSrkEos(373.15, 2.0); // 100 C, 2 bara
    system.addComponent("water", 10.0);
    system.addComponent("glucose", 0.1);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    MultiEffectEvaporator evap = new MultiEffectEvaporator("MEE", feed);
    evap.setNumberOfEffects(3);
    evap.setFirstEffectPressure(2.0);
    evap.setLastEffectPressure(0.2);
    evap.setTargetConcentrationFactor(3.0);
    evap.run();

    Assertions.assertNotNull(evap.getConcentrateStream());
    Assertions.assertNotNull(evap.getVaporCondensateStream());
    Assertions.assertEquals(3, evap.getNumberOfEffects());
    Assertions.assertTrue(evap.getSteamEconomy() > 0, "Steam economy should be positive");
  }

  /**
   * Test MultiEffectEvaporator single effect.
   */
  @Test
  public void testMultiEffectEvaporatorSingleEffect() {
    // Concentrating a glycerol solution from biodiesel production
    SystemInterface system = new SystemSrkEos(373.15, 3.0);
    system.addComponent("water", 10.0);
    system.addComponent("glycerol", 0.2);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    MultiEffectEvaporator evap = new MultiEffectEvaporator("SingleEffect", feed);
    evap.setNumberOfEffects(1);
    evap.setFirstEffectPressure(1.0);
    evap.setLastEffectPressure(0.5);
    evap.run();

    Assertions.assertNotNull(evap.getConcentrateStream().getThermoSystem());
  }

  /**
   * Test MultiEffectEvaporator validation.
   */
  @Test
  public void testMultiEffectEvaporatorValidation() {
    MultiEffectEvaporator evap = new MultiEffectEvaporator("Test");
    Assertions.assertThrows(IllegalArgumentException.class, () -> evap.setNumberOfEffects(0));
  }

  /**
   * Test MultiEffectEvaporator JSON output.
   */
  @Test
  public void testMultiEffectEvaporatorJson() {
    MultiEffectEvaporator evap = new MultiEffectEvaporator("TestMEE");
    evap.setNumberOfEffects(4);
    evap.setFirstEffectPressure(3.0);
    evap.setLastEffectPressure(0.3);

    String json = evap.toJson();
    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.contains("MultiEffectEvaporator"));
    Assertions.assertTrue(json.contains("TestMEE"));
  }

  /**
   * Test Dryer basic operation.
   */
  @Test
  public void testDryerBasic() {
    // Drying d-sorbitol crystals from aqueous slurry
    SystemInterface system = new SystemSrkEos(353.15, 1.01325); // 80 C, 1 bara
    system.addComponent("water", 5.0);
    system.addComponent("d-sorbitol", 0.5);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    Dryer dryer = new Dryer("ProductDryer", feed);
    dryer.setDryerType("drum");
    dryer.setOutletTemperature(378.15); // 105 C
    dryer.setTargetMoistureContent(0.05);
    dryer.setThermalEfficiency(0.85);
    dryer.run();

    Assertions.assertNotNull(dryer.getDriedProductStream());
    Assertions.assertNotNull(dryer.getVaporStream());
    Assertions.assertEquals("drum", dryer.getDryerType());
    Assertions.assertEquals(0.85, dryer.getThermalEfficiency(), 1e-6);
  }

  /**
   * Test Dryer temperature unit conversion.
   */
  @Test
  public void testDryerTemperatureUnits() {
    Dryer dryer = new Dryer("Test");
    dryer.setOutletTemperature(100.0, "C");
    Assertions.assertEquals(373.15, dryer.getOutletTemperature(), 0.01);

    dryer.setOutletTemperature(212.0, "F");
    Assertions.assertEquals(373.15, dryer.getOutletTemperature(), 0.1);
  }

  /**
   * Test Dryer JSON output.
   */
  @Test
  public void testDryerJson() {
    Dryer dryer = new Dryer("TestDryer");
    dryer.setDryerType("spray");
    dryer.setTargetMoistureContent(0.03);
    dryer.setOutletTemperature(383.15);

    String json = dryer.toJson();
    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.contains("Dryer"));
    Assertions.assertTrue(json.contains("TestDryer"));
  }

  /**
   * Test Dryer with pressure drop.
   */
  @Test
  public void testDryerWithPressureDrop() {
    // Drying xylitol crystals
    SystemInterface system = new SystemSrkEos(353.15, 3.0);
    system.addComponent("water", 5.0);
    system.addComponent("xylitol", 0.5);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    Dryer dryer = new Dryer("Dryer", feed);
    dryer.setOutletTemperature(383.15);
    dryer.setPressureDrop(0.5);
    dryer.run();

    Assertions.assertNotNull(dryer.getDriedProductStream().getThermoSystem());
  }

  /**
   * Test Dryer heat duty calculation.
   */
  @Test
  public void testDryerHeatDuty() {
    SystemInterface system = new SystemSrkEos(333.15, 1.0); // 60 C
    system.addComponent("water", 10.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    Dryer dryer = new Dryer("Dryer", feed);
    dryer.setOutletTemperature(393.15); // 120 C - above boiling
    dryer.setThermalEfficiency(1.0);
    dryer.run();

    // Heat duty should be non-zero when temperature changes
    double heatDuty = dryer.getHeatDuty();
    Assertions.assertTrue(Math.abs(heatDuty) > 0.0, "Heat duty should be non-zero");

    // Check unit conversion
    double heatDutyKW = dryer.getHeatDuty("kW");
    Assertions.assertEquals(heatDuty / 1000.0, heatDutyKW, 1e-6);
  }
}
