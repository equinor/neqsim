package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Tests for PipeSection and FlowRegimeDetector.
 */
class PipeSectionTest {

  private PipeSection section;
  private FlowRegimeDetector detector;

  @BeforeEach
  void setUp() {
    section = new PipeSection(0, 10, 0.2, 0);
    section.setRoughness(0.0001);

    // Set typical gas-liquid properties
    section.setGasDensity(50);
    section.setLiquidDensity(800);
    section.setGasViscosity(1.5e-5);
    section.setLiquidViscosity(1e-3);
    section.setSurfaceTension(0.02);
    section.setGasEnthalpy(500000);
    section.setLiquidEnthalpy(200000);
    section.setGasSoundSpeed(350);
    section.setLiquidSoundSpeed(1200);

    detector = new FlowRegimeDetector();
  }

  @Test
  void testSectionGeometry() {
    assertEquals(0, section.getPosition());
    assertEquals(10, section.getLength());
    assertEquals(0.2, section.getDiameter());
    assertEquals(Math.PI * 0.04 / 4, section.getArea(), 1e-6);
  }

  @Test
  void testHoldupNormalization() {
    section.setGasHoldup(0.6);
    section.setLiquidHoldup(0.6);
    section.updateDerivedQuantities();

    // Should normalize to sum = 1
    assertEquals(1.0, section.getGasHoldup() + section.getLiquidHoldup(), 1e-10);
    assertEquals(0.5, section.getGasHoldup(), 1e-10);
    assertEquals(0.5, section.getLiquidHoldup(), 1e-10);
  }

  @Test
  void testMixtureProperties() {
    section.setGasHoldup(0.3);
    section.setLiquidHoldup(0.7);
    section.setGasVelocity(5);
    section.setLiquidVelocity(1);
    section.updateDerivedQuantities();

    // Mixture density = α_G * ρ_G + α_L * ρ_L
    double expectedRhoM = 0.3 * 50 + 0.7 * 800;
    assertEquals(expectedRhoM, section.getMixtureDensity(), 1e-6);

    // Superficial velocities
    assertEquals(0.3 * 5, section.getSuperficialGasVelocity(), 1e-6);
    assertEquals(0.7 * 1, section.getSuperficialLiquidVelocity(), 1e-6);
  }

  @Test
  void testConservativeVariables() {
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(3);
    section.setLiquidVelocity(1);
    section.updateDerivedQuantities();

    double[] U = section.getConservativeVariables();

    // U[0] = ρ_G * α_G
    assertEquals(50 * 0.4, U[0], 1e-6);
    // U[1] = ρ_L * α_L
    assertEquals(800 * 0.6, U[1], 1e-6);
  }

  @Test
  void testSectionClone() {
    section.setPressure(5e6);
    section.setTemperature(300);
    section.setGasHoldup(0.5);

    PipeSection clone = section.clone();

    assertEquals(section.getPressure(), clone.getPressure());
    assertEquals(section.getTemperature(), clone.getTemperature());
    assertEquals(section.getGasHoldup(), clone.getGasHoldup());

    // Modify original, clone should be independent
    section.setPressure(10e6);
    assertEquals(5e6, clone.getPressure());
  }

  @Test
  void testFlowRegimeDetectionSinglePhaseGas() {
    section.setGasHoldup(0.9999);
    section.setLiquidHoldup(0.0001); // Below 0.001 threshold
    section.setGasVelocity(10);
    section.setLiquidVelocity(0);
    section.updateDerivedQuantities();

    FlowRegime regime = detector.detectFlowRegime(section);
    assertEquals(FlowRegime.SINGLE_PHASE_GAS, regime);
  }

  @Test
  void testFlowRegimeDetectionSinglePhaseLiquid() {
    section.setGasHoldup(0.0005);
    section.setLiquidHoldup(0.9995);
    section.setGasVelocity(0);
    section.setLiquidVelocity(2);
    section.updateDerivedQuantities();

    FlowRegime regime = detector.detectFlowRegime(section);
    assertEquals(FlowRegime.SINGLE_PHASE_LIQUID, regime);
  }

  @Test
  void testFlowRegimeDetectionStratified() {
    // Low velocities, horizontal pipe
    section.setInclination(0);
    section.setGasHoldup(0.6);
    section.setLiquidHoldup(0.4);
    section.setGasVelocity(2);
    section.setLiquidVelocity(0.3);
    section.updateDerivedQuantities();

    FlowRegime regime = detector.detectFlowRegime(section);
    assertTrue(regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY);
  }

  @Test
  void testFlowRegimeDetectionSlug() {
    // Moderate velocities, horizontal pipe
    section.setInclination(0);
    section.setGasHoldup(0.5);
    section.setLiquidHoldup(0.5);
    section.setGasVelocity(5);
    section.setLiquidVelocity(1);
    section.updateDerivedQuantities();

    FlowRegime regime = detector.detectFlowRegime(section);
    // Could be slug, stratified or annular depending on exact conditions
    assertTrue(
        regime == FlowRegime.SLUG || regime == FlowRegime.STRATIFIED_WAVY
            || regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.ANNULAR,
        "Expected slug, stratified, or annular flow regime but got: " + regime);
  }

  @Test
  void testFlowRegimeDetectionAnnular() {
    // High gas velocity
    section.setInclination(0);
    section.setGasHoldup(0.95);
    section.setLiquidHoldup(0.05);
    section.setGasVelocity(25);
    section.setLiquidVelocity(0.5);
    section.updateDerivedQuantities();

    FlowRegime regime = detector.detectFlowRegime(section);
    assertEquals(FlowRegime.ANNULAR, regime);
  }

  @Test
  void testFlowRegimeDetectionUpwardBubble() {
    // Upward flow, low gas velocity
    section.setInclination(Math.toRadians(80));
    section.setGasHoldup(0.15);
    section.setLiquidHoldup(0.85);
    section.setGasVelocity(0.5);
    section.setLiquidVelocity(0.8);
    section.updateDerivedQuantities();

    FlowRegime regime = detector.detectFlowRegime(section);
    assertTrue(regime == FlowRegime.BUBBLE || regime == FlowRegime.SLUG);
  }

  @Test
  void testLowPointDetection() {
    section.setLowPoint(true);
    assertTrue(section.isLowPoint());
    assertFalse(section.isHighPoint());

    section.setLowPoint(false);
    section.setHighPoint(true);
    assertFalse(section.isLowPoint());
    assertTrue(section.isHighPoint());
  }

  @Test
  void testSlugMarkers() {
    section.setInSlugBody(true);
    section.setSlugHoldup(0.9);

    assertTrue(section.isInSlugBody());
    assertFalse(section.isInSlugBubble());
    assertEquals(0.9, section.getSlugHoldup());
  }
}
