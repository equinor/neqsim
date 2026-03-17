package neqsim.process.equipment.separator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * Test class for bio-processing separator equipment: SolidsSeparator, SolidsCentrifuge,
 * RotaryVacuumFilter, PressureFilter, ScrewPress, LiquidLiquidExtractor, and Crystallizer. Uses
 * COMP_EXT database for bio-relevant components such as glucose, lactic acid, citric acid, succinic
 * acid, and glycerol.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioProcessingSeparatorTest {

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
   * Test SolidsSeparator basic split operation.
   */
  @Test
  public void testSolidsSeparatorBasicSplit() {
    // Fermentation broth with glucose (dissolved sugar) and lactic acid
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    system.addComponent("water", 10.0);
    system.addComponent("glucose", 0.5);
    system.addComponent("lactic acid", 0.3);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    SolidsSeparator separator = new SolidsSeparator("TestSep", feed);
    separator.setSolidsSplitFraction("glucose", 0.95); // 95% recovery of solids
    separator.setSolidsSplitFraction("lactic acid", 0.10); // 10% of dissolved acid
    separator.setDefaultSolidsSplit(0.02); // 2% default entrainment
    separator.setMoistureContent(0.40);
    separator.run();

    Assertions.assertNotNull(separator.getSolidsOutStream());
    Assertions.assertNotNull(separator.getLiquidOutStream());
    Assertions.assertNotNull(separator.getSolidsOutStream().getThermoSystem());
    Assertions.assertNotNull(separator.getLiquidOutStream().getThermoSystem());
  }

  /**
   * Test SolidsSeparator split fraction validation.
   */
  @Test
  public void testSolidsSeparatorSplitValidation() {
    SolidsSeparator separator = new SolidsSeparator("TestSep");
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> separator.setSolidsSplitFraction("test", 1.5));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> separator.setSolidsSplitFraction("test", -0.1));
  }

  /**
   * Test SolidsCentrifuge defaults.
   */
  @Test
  public void testSolidsCentrifugeDefaults() {
    // Yeast cell separation from ethanol fermentation broth
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    system.addComponent("water", 10.0);
    system.addComponent("succinic acid", 1.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    SolidsCentrifuge centrifuge = new SolidsCentrifuge("Centrifuge", feed);
    centrifuge.setSolidsSplitFraction("succinic acid", 0.99);
    centrifuge.run();

    Assertions.assertEquals(3000.0, centrifuge.getGForce(), 1e-6);
    Assertions.assertEquals(0.40, centrifuge.getMoistureContent(), 1e-6);
    Assertions.assertEquals(5.0, centrifuge.getSpecificEnergy(), 1e-6);
    Assertions.assertNotNull(centrifuge.getSolidsOutStream().getThermoSystem());
  }

  /**
   * Test RotaryVacuumFilter defaults.
   */
  @Test
  public void testRotaryVacuumFilterDefaults() {
    // Citric acid crystal filtration
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    system.addComponent("water", 10.0);
    system.addComponent("citric acid", 1.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    RotaryVacuumFilter filter = new RotaryVacuumFilter("RVF", feed);
    filter.setSolidsSplitFraction("citric acid", 0.95);
    filter.run();

    Assertions.assertEquals(0.60, filter.getMoistureContent(), 1e-6);
    Assertions.assertEquals(2.0, filter.getSpecificEnergy(), 1e-6);
    Assertions.assertEquals(0.5, filter.getVacuumPressure(), 1e-6);
  }

  /**
   * Test PressureFilter defaults.
   */
  @Test
  public void testPressureFilterDefaults() {
    PressureFilter filter = new PressureFilter("PF");
    Assertions.assertEquals(0.45, filter.getMoistureContent(), 1e-6);
    Assertions.assertEquals(3.0, filter.getSpecificEnergy(), 1e-6);
    Assertions.assertEquals(5.0, filter.getOperatingPressure(), 1e-6);
  }

  /**
   * Test ScrewPress defaults.
   */
  @Test
  public void testScrewPressDefaults() {
    ScrewPress press = new ScrewPress("SP");
    Assertions.assertEquals(0.65, press.getMoistureContent(), 1e-6);
    Assertions.assertEquals(1.0, press.getSpecificEnergy(), 1e-6);
    Assertions.assertEquals(5.0, press.getScrewSpeed(), 1e-6);
    Assertions.assertEquals(3.0, press.getCompressionRatio(), 1e-6);
  }

  /**
   * Test LiquidLiquidExtractor basic operation.
   */
  @Test
  public void testLiquidLiquidExtractorBasic() {
    // Extract lactic acid from aqueous broth using 1-butanol as solvent
    SystemInterface feed = new SystemSrkEos(298.15, 1.01325);
    feed.addComponent("water", 10.0);
    feed.addComponent("lactic acid", 0.5);
    feed.addComponent("1-butanol", 0.0);
    feed.setMixingRule("classic");

    Stream feedStream = new Stream("feed", feed);
    feedStream.run();

    // 1-butanol solvent stream with same components
    SystemInterface solvent = new SystemSrkEos(298.15, 1.01325);
    solvent.addComponent("water", 0.0);
    solvent.addComponent("lactic acid", 0.0);
    solvent.addComponent("1-butanol", 5.0);
    solvent.setMixingRule("classic");

    Stream solventStream = new Stream("solvent", solvent);
    solventStream.run();

    LiquidLiquidExtractor extractor = new LiquidLiquidExtractor("LLE", feedStream, solventStream);
    extractor.setNumberOfStages(3);
    extractor.setStageEfficiency(0.9);
    extractor.run();

    Assertions.assertNotNull(extractor.getExtractStream());
    Assertions.assertNotNull(extractor.getRaffinateStream());
    Assertions.assertEquals(3, extractor.getNumberOfStages());
    Assertions.assertEquals(0.9, extractor.getStageEfficiency(), 1e-6);
  }

  /**
   * Test LiquidLiquidExtractor stage count validation.
   */
  @Test
  public void testLiquidLiquidExtractorStageValidation() {
    LiquidLiquidExtractor extractor = new LiquidLiquidExtractor("Test");
    Assertions.assertThrows(IllegalArgumentException.class, () -> extractor.setNumberOfStages(0));
  }

  /**
   * Test Crystallizer basic operation.
   */
  @Test
  public void testCrystallizerBasic() {
    // Cooling crystallization of citric acid from aqueous solution
    SystemInterface system = new SystemSrkEos(343.15, 1.01325); // 70 C
    system.addComponent("water", 10.0);
    system.addComponent("citric acid", 0.5);
    system.addComponent("succinic acid", 0.2);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    Crystallizer cryst = new Crystallizer("Crystallizer", feed);
    cryst.setCrystallizationType("cooling");
    cryst.setOutletTemperature(303.15); // cool to 30 C
    cryst.setTargetSolute("citric acid");
    cryst.setSolidRecovery(0.85);
    cryst.run();

    Assertions.assertNotNull(cryst.getCrystalStream());
    Assertions.assertNotNull(cryst.getMotherLiquorStream());
    Assertions.assertEquals("cooling", cryst.getCrystallizationType());
    Assertions.assertEquals(0.85, cryst.getSolidRecovery(), 1e-6);
  }

  /**
   * Test Crystallizer temperature unit conversion.
   */
  @Test
  public void testCrystallizerTempUnits() {
    Crystallizer cryst = new Crystallizer("Test");
    cryst.setOutletTemperature(30.0, "C");
    Assertions.assertEquals(303.15, cryst.getOutletTemperature(), 0.01);
  }

  /**
   * Test SolidsSeparator JSON output.
   */
  @Test
  public void testSolidsSeparatorJson() {
    SolidsSeparator separator = new SolidsSeparator("TestSep");
    separator.setSolidsSplitFraction("component1", 0.90);
    separator.setMoistureContent(0.35);

    String json = separator.toJson();
    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.contains("TestSep"));
    Assertions.assertTrue(json.contains("SolidsSeparator"));
  }

  /**
   * Test Crystallizer JSON output.
   */
  @Test
  public void testCrystallizerJson() {
    Crystallizer cryst = new Crystallizer("TestCryst");
    cryst.setCrystallizationType("evaporative");
    cryst.setTargetSolute("NaCl");

    String json = cryst.toJson();
    Assertions.assertNotNull(json);
    Assertions.assertTrue(json.contains("Crystallizer"));
    Assertions.assertTrue(json.contains("evaporative"));
  }

  /**
   * Test SolidsSeparator with pressure drop.
   */
  @Test
  public void testSolidsSeparatorWithPressureDrop() {
    // Glycerol solution separation with pressure drop
    SystemInterface system = new SystemSrkEos(298.15, 5.0);
    system.addComponent("water", 10.0);
    system.addComponent("glycerol", 1.0);
    system.setMixingRule("classic");

    Stream feed = new Stream("feed", system);
    feed.run();

    SolidsSeparator separator = new SolidsSeparator("Sep", feed);
    separator.setSolidsSplitFraction("glycerol", 0.90);
    separator.setPressureDrop(0.5);
    separator.run();

    double outPressure = separator.getLiquidOutStream().getThermoSystem().getPressure();
    Assertions.assertEquals(4.5, outPressure, 0.1, "Outlet pressure should account for dP");
  }
}
