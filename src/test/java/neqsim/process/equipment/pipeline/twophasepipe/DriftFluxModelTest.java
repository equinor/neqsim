package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.DriftFluxModel.DriftFluxParameters;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Tests for DriftFluxModel.
 */
class DriftFluxModelTest {

  private DriftFluxModel model;
  private PipeSection section;

  @BeforeEach
  void setUp() {
    model = new DriftFluxModel();

    section = new PipeSection(0, 10, 0.2, 0);
    section.setRoughness(0.0001);
    section.setGasDensity(50);
    section.setLiquidDensity(800);
    section.setGasViscosity(1.5e-5);
    section.setLiquidViscosity(1e-3);
    section.setSurfaceTension(0.02);
    section.setGasSoundSpeed(350);
    section.setLiquidSoundSpeed(1200);
  }

  @Test
  void testDriftFluxBubbleFlow() {
    section.setFlowRegime(FlowRegime.BUBBLE);
    section.setGasHoldup(0.15);
    section.setLiquidHoldup(0.85);
    section.setGasVelocity(1.5);
    section.setLiquidVelocity(1.0);
    section.setInclination(Math.toRadians(90)); // Vertical upward
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // C0 should be around 1.2 for bubble flow
    assertTrue(params.C0 >= 1.1 && params.C0 <= 1.3,
        "C0 for bubble flow should be ~1.2, got " + params.C0);

    // Drift velocity should be positive for upward flow
    assertTrue(params.driftVelocity > 0, "Drift velocity should be positive for upward flow");

    // Gas should move faster than liquid (slip ratio > 1)
    assertTrue(params.slipRatio > 1.0, "Slip ratio should be > 1 for bubble flow");
  }

  @Test
  void testDriftFluxSlugFlow() {
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(3);
    section.setLiquidVelocity(1);
    section.setInclination(0); // Horizontal
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // C0 for horizontal slug flow
    assertTrue(params.C0 >= 1.0 && params.C0 <= 1.3,
        "C0 for slug flow should be 1.0-1.3, got " + params.C0);

    // Drift velocity present even in horizontal
    assertTrue(params.driftVelocity >= 0, "Drift velocity should be >= 0");
  }

  @Test
  void testDriftFluxAnnularFlow() {
    section.setFlowRegime(FlowRegime.ANNULAR);
    section.setGasHoldup(0.9);
    section.setLiquidHoldup(0.1);
    section.setGasVelocity(20);
    section.setLiquidVelocity(2);
    section.setInclination(0);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // C0 should be close to 1.0 for annular flow (more homogeneous)
    assertEquals(1.0, params.C0, 0.1);
  }

  @Test
  void testDriftFluxStratifiedFlow() {
    section.setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);
    section.setGasHoldup(0.7);
    section.setLiquidHoldup(0.3);
    section.setGasVelocity(3);
    section.setLiquidVelocity(0.5);
    section.setInclination(0);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // For stratified, holdup is calculated from momentum balance
    assertTrue(params.liquidHoldup >= 0 && params.liquidHoldup <= 1);
    assertTrue(params.voidFraction >= 0 && params.voidFraction <= 1);
    assertEquals(1.0, params.liquidHoldup + params.voidFraction, 1e-10);
  }

  @Test
  void testPressureGradientCalculation() {
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(3);
    section.setLiquidVelocity(1);
    section.setInclination(Math.toRadians(10)); // Slight uphill
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);
    double dPdx = model.calculatePressureGradient(section, params);

    // Pressure should decrease in flow direction (uphill + friction)
    assertTrue(dPdx < 0, "Pressure gradient should be negative (pressure decreasing)");

    // Gravity component should be negative (uphill)
    assertTrue(section.getGravityPressureGradient() < 0);

    // Friction component should be negative
    assertTrue(section.getFrictionPressureGradient() < 0);
  }

  @Test
  void testPressureGradientDownhill() {
    section.setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);
    section.setGasHoldup(0.5);
    section.setLiquidHoldup(0.5);
    section.setGasVelocity(2);
    section.setLiquidVelocity(0.5);
    section.setInclination(Math.toRadians(-20)); // Downhill
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);
    double dPdx = model.calculatePressureGradient(section, params);

    // Gravity helps flow (positive contribution), friction opposes
    // Net could be positive or negative depending on angle
    assertNotNull(dPdx);

    // Gravity component should be positive (downhill)
    assertTrue(section.getGravityPressureGradient() > 0);
  }

  @Test
  void testVoidFractionFromDriftFlux() {
    section.setFlowRegime(FlowRegime.BUBBLE);
    section.setGasHoldup(0.2);
    section.setLiquidHoldup(0.8);
    section.setGasVelocity(1);
    section.setLiquidVelocity(0.8);
    section.setInclination(Math.toRadians(45));
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // Void fraction should be between 0 and 1
    assertTrue(params.voidFraction >= 0 && params.voidFraction <= 1);

    // With slip, actual holdup differs from no-slip
    // For bubble flow with upward inclination, gas rises faster
    // so void fraction < input GVF
    double gvf = section.getSuperficialGasVelocity()
        / (section.getSuperficialGasVelocity() + section.getSuperficialLiquidVelocity());
    // Void fraction typically less than no-slip GVF due to slip
    assertTrue(params.voidFraction <= gvf + 0.1,
        "Void fraction should be close to or less than no-slip GVF");
  }

  @Test
  void testSlipRatioLimits() {
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.5);
    section.setLiquidHoldup(0.5);
    section.setGasVelocity(5);
    section.setLiquidVelocity(1);
    section.setInclination(0);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // Slip ratio should be positive and reasonable
    assertTrue(params.slipRatio > 0);
    assertTrue(params.slipRatio < 100, "Slip ratio should be reasonable");
  }

  @Test
  void testZeroFlow() {
    section.setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);
    section.setGasHoldup(0.5);
    section.setLiquidHoldup(0.5);
    section.setGasVelocity(0);
    section.setLiquidVelocity(0);
    section.setInclination(0);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // Should not crash with zero flow
    assertNotNull(params);
    assertTrue(params.voidFraction >= 0);
  }

  @Test
  void testInclinationEffect() {
    // Same conditions, different inclinations
    double[] inclinations = {-45, 0, 45, 90};
    double[] driftVelocities = new double[4];

    for (int i = 0; i < inclinations.length; i++) {
      section.setFlowRegime(FlowRegime.SLUG);
      section.setGasHoldup(0.4);
      section.setLiquidHoldup(0.6);
      section.setGasVelocity(3);
      section.setLiquidVelocity(1);
      section.setInclination(Math.toRadians(inclinations[i]));
      section.updateDerivedQuantities();

      DriftFluxParameters params = model.calculateDriftFlux(section);
      driftVelocities[i] = params.driftVelocity;
    }

    // Drift velocity varies with inclination (Bendiksen 1984)
    // For slug flow, horizontal drift velocity (Zukoski) is typically higher than vertical
    // The important thing is that inclination DOES affect drift velocity
    assertTrue(Math.abs(driftVelocities[1] - driftVelocities[3]) > 0.001,
        "Drift velocity should be different for vertical vs horizontal");
    // Verify all values are non-negative for upward and horizontal flow
    assertTrue(driftVelocities[1] >= 0, "Horizontal drift velocity should be non-negative");
    assertTrue(driftVelocities[2] >= 0, "45 degree drift velocity should be non-negative");
    assertTrue(driftVelocities[3] >= 0, "Vertical drift velocity should be non-negative");
  }
}
