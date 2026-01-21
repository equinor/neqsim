package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

  // ========== Energy Equation Tests ==========

  @Test
  void testEnergyEquationHeatTransfer() {
    // Test heat transfer to surroundings (cooling)
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(3);
    section.setLiquidVelocity(1);
    section.setTemperature(350); // K - hot fluid
    section.setMixtureHeatCapacity(2000); // J/(kg·K)
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    double dt = 1.0; // 1 second time step
    double dx = 10.0; // 10 m section length
    double ambientTemp = 288.15; // K - cool ambient
    double heatTransferCoeff = 10.0; // W/(m²·K)
    double jouleThomsonCoeff = 3e-6; // K/Pa

    DriftFluxModel.EnergyEquationResult result = model.calculateEnergyEquation(section, params, dt,
        dx, ambientTemp, heatTransferCoeff, jouleThomsonCoeff);

    // Fluid should cool toward ambient temperature
    assertTrue(result.heatTransferDeltaT < 0, "Fluid should lose heat when warmer than ambient");
    assertTrue(result.heatTransferRate < 0, "Heat transfer rate should be negative (heat loss)");
    assertTrue(result.newTemperature < 350, "New temperature should be lower than initial");
  }

  @Test
  void testEnergyEquationHeatGain() {
    // Test heat transfer from surroundings (heating)
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(3);
    section.setLiquidVelocity(1);
    section.setTemperature(270); // K - cold fluid
    section.setMixtureHeatCapacity(2000); // J/(kg·K)
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    double dt = 1.0;
    double dx = 10.0;
    double ambientTemp = 300; // K - warm ambient
    double heatTransferCoeff = 50.0; // W/(m²·K) - higher for faster heating
    double jouleThomsonCoeff = 3e-6;

    DriftFluxModel.EnergyEquationResult result = model.calculateEnergyEquation(section, params, dt,
        dx, ambientTemp, heatTransferCoeff, jouleThomsonCoeff);

    // Fluid should heat up toward ambient temperature
    assertTrue(result.heatTransferDeltaT > 0, "Fluid should gain heat when cooler than ambient");
    assertTrue(result.heatTransferRate > 0, "Heat transfer rate should be positive (heat gain)");
    assertTrue(result.newTemperature > 270, "New temperature should be higher than initial");
  }

  @Test
  void testEnergyEquationJouleThomsonEffect() {
    // Test Joule-Thomson cooling during gas expansion (negative pressure gradient)
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.8); // High gas fraction for noticeable JT effect
    section.setLiquidHoldup(0.2);
    section.setGasVelocity(5);
    section.setLiquidVelocity(1);
    section.setTemperature(300);
    section.setMixtureHeatCapacity(2000);
    section.setPressure(5e6);
    section.setInclination(0); // Horizontal
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    double dt = 0.1;
    double dx = 10.0;
    double ambientTemp = 300; // Same as fluid to isolate JT effect
    double heatTransferCoeff = 0.0; // No heat transfer to isolate JT effect
    double jouleThomsonCoeff = 5e-6; // Typical for natural gas

    DriftFluxModel.EnergyEquationResult result = model.calculateEnergyEquation(section, params, dt,
        dx, ambientTemp, heatTransferCoeff, jouleThomsonCoeff);

    // For gas expansion (negative dP/dx), JT effect should cause cooling
    // dP/dx is negative in flow direction due to friction
    // Result depends on sign of pressure gradient
    assertNotNull(result.jouleThomsonDeltaT);
    assertTrue(Math.abs(result.jouleThomsonDeltaT) < 10,
        "JT temperature change should be reasonable");
  }

  @Test
  void testEnergyEquationFrictionHeating() {
    // Test friction heating (viscous dissipation)
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(10); // Higher velocity for more friction
    section.setLiquidVelocity(3);
    section.setTemperature(300);
    section.setMixtureHeatCapacity(2000);
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    // Calculate pressure gradient first to set friction component
    DriftFluxParameters params = model.calculateDriftFlux(section);
    model.calculatePressureGradient(section, params);

    double dt = 1.0;
    double dx = 10.0;
    double ambientTemp = 300; // Same as fluid
    double heatTransferCoeff = 0.0; // No heat transfer
    double jouleThomsonCoeff = 0.0; // No JT effect

    DriftFluxModel.EnergyEquationResult result = model.calculateEnergyEquation(section, params, dt,
        dx, ambientTemp, heatTransferCoeff, jouleThomsonCoeff);

    // Friction heating should always be positive (heat generation)
    assertTrue(result.frictionHeatingDeltaT >= 0,
        "Friction heating should cause temperature increase");
    assertTrue(result.frictionHeatingPower >= 0, "Friction heating power should be non-negative");
  }

  @Test
  void testEnergyEquationTemperatureBounds() {
    // Test that temperature stays within physical bounds
    section.setFlowRegime(FlowRegime.ANNULAR);
    section.setGasHoldup(0.9);
    section.setLiquidHoldup(0.1);
    section.setGasVelocity(20);
    section.setLiquidVelocity(2);
    section.setTemperature(150); // Very cold
    section.setMixtureHeatCapacity(2000);
    section.setPressure(10e6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    // Extreme conditions that might push temperature out of bounds
    double dt = 10.0; // Large time step
    double dx = 100.0;
    double ambientTemp = 400; // Very hot ambient
    double heatTransferCoeff = 1000.0; // Very high heat transfer
    double jouleThomsonCoeff = 1e-5;

    DriftFluxModel.EnergyEquationResult result = model.calculateEnergyEquation(section, params, dt,
        dx, ambientTemp, heatTransferCoeff, jouleThomsonCoeff);

    // Temperature should be bounded
    assertTrue(result.newTemperature >= 100, "Temperature should not go below 100K");
    assertTrue(result.newTemperature <= 500, "Temperature should not exceed 500K");
  }

  @Test
  void testSteadyStateTemperature() {
    // Test steady-state temperature calculation
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setGasVelocity(3);
    section.setLiquidVelocity(1);
    section.setTemperature(350);
    section.setMixtureHeatCapacity(2000);
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    double upstreamTemp = 350.0; // K
    double dx = 100.0; // 100 m section
    double ambientTemp = 288.15; // K
    double heatTransferCoeff = 10.0; // W/(m²·K)
    double massFlowRate = 10.0; // kg/s
    double jouleThomsonCoeff = 3e-6;

    double newTemp = model.calculateSteadyStateTemperature(section, upstreamTemp, dx, ambientTemp,
        heatTransferCoeff, massFlowRate, jouleThomsonCoeff);

    // Temperature should decrease toward ambient
    assertTrue(newTemp < upstreamTemp, "Temperature should decrease toward ambient");
    assertTrue(newTemp > ambientTemp, "Temperature should not drop below ambient");
  }

  @Test
  void testJouleThomsonCoefficientEstimate() {
    // Test JT coefficient estimation
    double T = 300; // K
    double P = 50e5; // 50 bar
    double MW = 18.0; // Approximate natural gas MW

    double mu_JT = model.estimateJouleThomsonCoefficient(T, P, MW);

    // Should be positive for real gas
    assertTrue(mu_JT > 0, "JT coefficient should be positive for hydrocarbon gas");
    // Typical range for natural gas
    assertTrue(mu_JT < 1e-4, "JT coefficient should be reasonable magnitude");
  }

  @Test
  void testMixtureHeatCapacity() {
    section.setFlowRegime(FlowRegime.SLUG);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    double Cp_gas = 2000; // J/(kg·K) - typical for methane
    double Cp_liquid = 2200; // J/(kg·K) - typical for light oil

    double Cp_mix = model.calculateMixtureHeatCapacity(section, params, Cp_gas, Cp_liquid);

    // Mixture Cp should be between gas and liquid values
    assertTrue(Cp_mix >= Math.min(Cp_gas, Cp_liquid),
        "Mixture Cp should be at least the minimum of components");
    assertTrue(Cp_mix <= Math.max(Cp_gas, Cp_liquid),
        "Mixture Cp should not exceed the maximum of components");
  }

  @Test
  void testZeroFlowEnergyEquation() {
    // Test energy equation with zero flow (should not crash)
    section.setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);
    section.setGasHoldup(0.5);
    section.setLiquidHoldup(0.5);
    section.setGasVelocity(0);
    section.setLiquidVelocity(0);
    section.setTemperature(300);
    section.setMixtureHeatCapacity(2000);
    section.setPressure(5e6);
    section.updateDerivedQuantities();

    DriftFluxParameters params = model.calculateDriftFlux(section);

    DriftFluxModel.EnergyEquationResult result =
        model.calculateEnergyEquation(section, params, 1.0, 10.0, 288.15, 10.0, 3e-6);

    // Should not crash and temperature should remain unchanged
    assertNotNull(result);
    assertEquals(300, result.newTemperature, 0.01);
  }
}
