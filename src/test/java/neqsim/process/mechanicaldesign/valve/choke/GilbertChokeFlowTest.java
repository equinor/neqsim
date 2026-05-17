package neqsim.process.mechanicaldesign.valve.choke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the Gilbert empirical choke flow correlations.
 *
 * @author esol
 */
public class GilbertChokeFlowTest {

  private SystemInterface twoPhaseFluid;
  private GilbertChokeFlow gilbertModel;

  @BeforeEach
  void setUp() {
    // Two-phase gas-oil fluid
    twoPhaseFluid = new SystemSrkEos(300.0, 100.0);
    twoPhaseFluid.addComponent("methane", 0.7);
    twoPhaseFluid.addComponent("ethane", 0.1);
    twoPhaseFluid.addComponent("n-heptane", 0.15);
    twoPhaseFluid.addComponent("nC10", 0.05);
    twoPhaseFluid.setMixingRule(2);
    twoPhaseFluid.setMultiPhaseCheck(true);
    twoPhaseFluid.init(0);
    twoPhaseFluid.init(1);
    twoPhaseFluid.initPhysicalProperties();

    gilbertModel = new GilbertChokeFlow();
    gilbertModel.setChokeDiameter(32, "64ths"); // 32/64" = 0.5"
  }

  @Test
  void testGilbertModelName() {
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.GILBERT);
    assertEquals("Gilbert (1954)", gilbertModel.getModelName());
  }

  @Test
  void testBaxendellModelName() {
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.BAXENDELL);
    assertEquals("Baxendell (1958)", gilbertModel.getModelName());
  }

  @Test
  void testRosModelName() {
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ROS);
    assertEquals("Ros (1960)", gilbertModel.getModelName());
  }

  @Test
  void testAchongModelName() {
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ACHONG);
    assertEquals("Achong (1961)", gilbertModel.getModelName());
  }

  @Test
  void testCorrelationConstants() {
    // Test Gilbert constants
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.GILBERT);
    assertEquals(10.0, gilbertModel.getCorrelationConstant(), 0.01);
    assertEquals(1.89, gilbertModel.getDiameterExponent(), 0.01);
    assertEquals(0.546, gilbertModel.getGlrExponent(), 0.01);

    // Test Baxendell constants
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.BAXENDELL);
    assertEquals(9.56, gilbertModel.getCorrelationConstant(), 0.01);
    assertEquals(1.93, gilbertModel.getDiameterExponent(), 0.01);

    // Test Ros constants
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ROS);
    assertEquals(17.4, gilbertModel.getCorrelationConstant(), 0.01);
    assertEquals(2.0, gilbertModel.getDiameterExponent(), 0.01);
    assertEquals(0.5, gilbertModel.getGlrExponent(), 0.01);

    // Test Achong constants
    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ACHONG);
    assertEquals(3.82, gilbertModel.getCorrelationConstant(), 0.01);
    assertEquals(0.65, gilbertModel.getGlrExponent(), 0.01);
  }

  @Test
  void testCustomConstants() {
    gilbertModel.setCorrelationConstant(12.0);
    assertEquals(GilbertChokeFlow.CorrelationType.CUSTOM, gilbertModel.getCorrelationType());
    assertEquals(12.0, gilbertModel.getCorrelationConstant(), 0.01);

    gilbertModel.setDiameterExponent(1.95);
    assertEquals(1.95, gilbertModel.getDiameterExponent(), 0.01);

    gilbertModel.setGlrExponent(0.55);
    assertEquals(0.55, gilbertModel.getGlrExponent(), 0.01);

    assertEquals("Custom Gilbert-type", gilbertModel.getModelName());
  }

  @Test
  void testMassFlowCalculation() {
    double P1 = 100.0e5; // 100 bar
    double P2 = 30.0e5; // 30 bar (not used for critical flow)

    double massFlow = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1, P2);

    assertTrue(massFlow > 0, "Mass flow should be positive");
    assertTrue(massFlow < 1000, "Mass flow should be reasonable");
  }

  @Test
  void testFlowIncreasesWithPressure() {
    double P2 = 30.0e5;

    double P1_low = 50.0e5;
    double P1_high = 100.0e5;

    double flow_low = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1_low, P2);
    double flow_high = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1_high, P2);

    assertTrue(flow_high > flow_low, "Flow should increase with upstream pressure");
  }

  @Test
  void testFlowIncreasesWithChokeDiameter() {
    double P1 = 100.0e5;
    double P2 = 30.0e5;

    gilbertModel.setChokeDiameter(24, "64ths"); // 24/64" = 0.375"
    double flow_small = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1, P2);

    gilbertModel.setChokeDiameter(48, "64ths"); // 48/64" = 0.75"
    double flow_large = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1, P2);

    assertTrue(flow_large > flow_small, "Flow should increase with choke diameter");
  }

  @Test
  void testDifferentCorrelationsGiveDifferentResults() {
    double P1 = 100.0e5;
    double P2 = 30.0e5;

    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.GILBERT);
    double flow_gilbert = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1, P2);

    gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ACHONG);
    double flow_achong = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1, P2);

    // Different correlations should give different results
    assertTrue(Math.abs(flow_gilbert - flow_achong) > 0.001,
        "Different correlations should give different results");
  }

  @Test
  void testRequiredChokeDiameter() {
    double P1 = 100.0e5;

    // Gilbert model requires liquid flow - skip if fluid is all gas
    double gasQuality = gilbertModel.calculateGasQuality(twoPhaseFluid);
    if (gasQuality >= 0.999) {
      // All gas fluid - skip this test
      return;
    }

    // First calculate flow for a known diameter
    gilbertModel.setChokeDiameter(32, "64ths");
    double massFlow = gilbertModel.calculateMassFlowRate(twoPhaseFluid, P1, 30e5);

    // Approximate liquid flow rate
    double liquidDensity = 700.0; // kg/m3
    double liquidFlow = massFlow * (1.0 - gasQuality) / liquidDensity;

    // Only test if we have meaningful liquid flow
    if (liquidFlow > 1e-6) {
      // Calculate required diameter
      double requiredDiameter =
          gilbertModel.calculateRequiredChokeDiameter(twoPhaseFluid, P1, liquidFlow);

      // Should be non-negative (may be zero for all-gas case)
      assertTrue(requiredDiameter >= 0, "Required diameter should be non-negative");
    }
  }

  @Test
  void testCriticalPressureRatio() {
    // Gilbert assumes critical flow, but we still calculate critical ratio
    double yCrit = gilbertModel.calculateCriticalPressureRatio(0.5, 1.3);

    assertTrue(yCrit > 0.3 && yCrit < 0.9, "Critical ratio should be reasonable");
  }

  @Test
  void testDownstreamPressure() {
    double P1 = 100.0e5;
    double massFlow = 10.0; // kg/s

    // Gilbert assumes critical flow - downstream pressure is critical pressure
    double P2 = gilbertModel.calculateDownstreamPressure(twoPhaseFluid, P1, massFlow);

    assertTrue(P2 > 0, "Downstream pressure should be positive");

    // For critical flow, P2 should be around the critical pressure ratio times P1
    double gasQuality = gilbertModel.calculateGasQuality(twoPhaseFluid);
    double gamma = twoPhaseFluid.getGamma2();
    double criticalRatio = gilbertModel.calculateCriticalPressureRatio(gasQuality, gamma);
    double expectedP2 = P1 * criticalRatio;

    // Check that returned P2 is close to expected critical pressure
    assertEquals(expectedP2, P2, expectedP2 * 0.05,
        "Downstream pressure should be approximately the critical pressure");
  }

  @Test
  void testGLRCalculation() {
    double glr = gilbertModel.calculateGLR(twoPhaseFluid);
    assertTrue(glr >= 0, "GLR should be non-negative");
  }
}
