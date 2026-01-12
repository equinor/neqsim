package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesignCalculator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for Riser equipment class.
 */
class RiserTest {

  private SystemInterface testFluid;
  private Stream inletStream;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkEos(288.15, 50.0);
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");

    inletStream = new Stream("feed", testFluid);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.run();
  }

  @Test
  void testCreateSCR() {
    Riser scr = Riser.createSCR("Production SCR", inletStream, 500.0);
    assertNotNull(scr);
    assertEquals("Production SCR", scr.getName());
    assertEquals(Riser.RiserType.STEEL_CATENARY_RISER, scr.getRiserType());
    assertEquals(500.0, scr.getWaterDepth(), 0.001);

    scr.setDiameter(0.254); // 10 inch
    scr.run();

    // SCR length should be longer than water depth due to catenary
    assertTrue(scr.getLength() > 500.0);
    assertNotNull(scr.getOutletStream());
  }

  @Test
  void testCreateTTR() {
    Riser ttr = Riser.createTTR("TTR", inletStream, 300.0);
    assertNotNull(ttr);
    assertEquals(Riser.RiserType.TOP_TENSIONED_RISER, ttr.getRiserType());

    ttr.setDiameter(0.254);
    ttr.run();

    // TTR is vertical, so length equals water depth
    assertEquals(300.0, ttr.getLength(), 0.001);
  }

  @Test
  void testCreateLazyWave() {
    Riser lazyWave = Riser.createLazyWave("Lazy Wave", inletStream, 1000.0, 400.0);
    assertNotNull(lazyWave);
    assertEquals(Riser.RiserType.LAZY_WAVE, lazyWave.getRiserType());
    assertEquals(400.0, lazyWave.getBuoyancyModuleDepth(), 0.001);

    lazyWave.setDiameter(0.3);
    lazyWave.run();

    // Lazy-wave length should be much longer than water depth
    assertTrue(lazyWave.getLength() > 1500.0);
  }

  @Test
  void testRiserMechanicalDesign() {
    Riser scr = Riser.createSCR("Design Test SCR", inletStream, 800.0);
    scr.setDiameter(0.254);
    scr.setTopAngle(12.0);
    scr.setCurrentVelocity(0.8);
    scr.setSignificantWaveHeight(3.0);
    scr.run();

    // Get mechanical design
    RiserMechanicalDesign design = scr.getRiserMechanicalDesign();
    assertNotNull(design);

    design.setMaxOperationPressure(100.0);
    design.setMaterialGrade("X65");

    design.readDesignSpecifications();
    design.calcDesign();

    // Get calculator results
    RiserMechanicalDesignCalculator calc = design.getRiserCalculator();
    assertNotNull(calc);

    // Top tension should be positive
    assertTrue(calc.getTopTension() > 0);

    // VIV calculations
    assertTrue(calc.getVortexSheddingFrequency() > 0);
    assertTrue(calc.getNaturalFrequency() > 0);

    // Fatigue life should be finite positive
    assertTrue(calc.getRiserFatigueLife() > 0);
    assertTrue(calc.getRiserFatigueLife() < 1000);
  }

  @Test
  void testRiserTypeClassification() {
    Riser scr = Riser.createSCR("SCR", inletStream, 500.0);
    assertTrue(scr.isCatenaryType());
    assertFalse(scr.isTensionedType());

    Riser ttr = Riser.createTTR("TTR", inletStream, 500.0);
    assertFalse(ttr.isCatenaryType());
    assertTrue(ttr.isTensionedType());

    Riser lazyWave = Riser.createLazyWave("LW", inletStream, 500.0, 200.0);
    assertTrue(lazyWave.isCatenaryType());
    assertTrue(lazyWave.hasBuoyancyModules());

    Riser flexible = Riser.createFlexible("Flex", inletStream, 500.0);
    assertTrue(flexible.isCatenaryType());
    assertTrue(flexible.isFlexibleType());
  }

  @Test
  void testEnvironmentalConditions() {
    Riser riser = new Riser("Test Riser", inletStream);
    riser.setWaterDepth(800.0);
    riser.setSignificantWaveHeight(4.5);
    riser.setPeakWavePeriod(12.0);
    riser.setCurrentVelocity(1.2);
    riser.setSeabedCurrentVelocity(0.3);
    riser.setPlatformHeaveAmplitude(3.0);
    riser.setPlatformHeavePeriod(15.0);
    riser.setSeawaterTemperature(5.0);
    riser.setSoilType("clay");

    assertEquals(4.5, riser.getSignificantWaveHeight(), 0.001);
    assertEquals(12.0, riser.getPeakWavePeriod(), 0.001);
    assertEquals(1.2, riser.getCurrentVelocity(), 0.001);
    assertEquals(0.3, riser.getSeabedCurrentVelocity(), 0.001);
    assertEquals(3.0, riser.getPlatformHeaveAmplitude(), 0.001);
    assertEquals(0.3, riser.getSeabedFriction(), 0.001); // Clay friction
  }

  @Test
  void testTTRTensionSettings() {
    Riser ttr = Riser.createTTR("TTR", inletStream, 400.0);
    ttr.setAppliedTopTension(500.0); // 500 kN
    ttr.setTensionVariationFactor(0.15);

    assertEquals(500.0, ttr.getAppliedTopTension(), 0.001);
    assertEquals(0.15, ttr.getTensionVariationFactor(), 0.001);
  }

  @Test
  void testJsonOutput() {
    Riser scr = Riser.createSCR("JSON Test", inletStream, 600.0);
    scr.setDiameter(0.3);
    scr.run();

    RiserMechanicalDesign design = scr.getRiserMechanicalDesign();
    design.setMaxOperationPressure(80.0);
    design.calcDesign();

    String json = design.toJson();
    assertNotNull(json);
    assertTrue(json.contains("riserDesignCalculations"));
    assertTrue(json.contains("riserProperties"));
    assertTrue(json.contains("topTension"));
    assertTrue(json.contains("touchdownPointStress"));
    assertTrue(json.contains("vivAnalysis"));
    assertTrue(json.contains("fatigueAnalysis"));
  }
}
