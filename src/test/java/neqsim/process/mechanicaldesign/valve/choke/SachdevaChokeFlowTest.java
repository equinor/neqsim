package neqsim.process.mechanicaldesign.valve.choke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the Sachdeva multiphase choke flow model.
 *
 * @author esol
 */
public class SachdevaChokeFlowTest {

  private SystemInterface twoPhaseFluid;
  private SystemInterface gasOnlyFluid;
  private SystemInterface liquidOnlyFluid;
  private SachdevaChokeFlow chokeModel;

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

    // Gas-only fluid
    gasOnlyFluid = new SystemSrkEos(300.0, 100.0);
    gasOnlyFluid.addComponent("methane", 0.9);
    gasOnlyFluid.addComponent("ethane", 0.1);
    gasOnlyFluid.setMixingRule(2);
    gasOnlyFluid.init(0);
    gasOnlyFluid.init(1);
    gasOnlyFluid.initPhysicalProperties();

    // Liquid-only fluid (high pressure to keep in liquid phase)
    liquidOnlyFluid = new SystemSrkEos(300.0, 200.0);
    liquidOnlyFluid.addComponent("n-heptane", 0.7);
    liquidOnlyFluid.addComponent("nC10", 0.3);
    liquidOnlyFluid.setMixingRule(2);
    liquidOnlyFluid.init(0);
    liquidOnlyFluid.init(1);
    liquidOnlyFluid.initPhysicalProperties();

    // Choke model with 0.5 inch diameter
    chokeModel = new SachdevaChokeFlow();
    chokeModel.setChokeDiameter(0.5, "in");
  }

  @Test
  void testModelName() {
    assertEquals("Sachdeva et al. (1986)", chokeModel.getModelName());
  }

  @Test
  void testChokeDiameterConversion() {
    // Test various unit conversions
    chokeModel.setChokeDiameter(32, "64ths");
    assertEquals(0.0127, chokeModel.getChokeDiameter(), 0.0001); // 32/64" = 0.5"

    chokeModel.setChokeDiameter(12.7, "mm");
    assertEquals(0.0127, chokeModel.getChokeDiameter(), 0.0001);

    chokeModel.setChokeDiameter(0.5, "in");
    assertEquals(0.0127, chokeModel.getChokeDiameter(), 0.0001);
  }

  @Test
  void testCriticalPressureRatio() {
    // Test critical pressure ratio correlation
    // y_c = 0.5847 - 0.0227 * ln(x_g)

    // Low gas quality
    double yCrit_low = chokeModel.calculateCriticalPressureRatio(0.1, 1.3);
    assertTrue(yCrit_low > 0.5 && yCrit_low < 0.8, "Critical ratio for low gas quality");

    // High gas quality
    double yCrit_high = chokeModel.calculateCriticalPressureRatio(0.9, 1.3);
    assertTrue(yCrit_high > 0.4 && yCrit_high < 0.65, "Critical ratio for high gas quality");

    // Near-pure gas (should approach isentropic ratio)
    double yCrit_gas = chokeModel.calculateCriticalPressureRatio(0.999, 1.3);
    double isentropicRatio = Math.pow(2.0 / 2.3, 1.3 / 0.3);
    assertEquals(isentropicRatio, yCrit_gas, 0.05);

    // Near-pure liquid
    double yCrit_liquid = chokeModel.calculateCriticalPressureRatio(0.001, 1.3);
    assertTrue(yCrit_liquid > 0.85, "Critical ratio for liquid should be high");
  }

  @Test
  void testGasQualityCalculation() {
    // Two-phase fluid should have gas quality between 0 and 1
    double gasQuality = chokeModel.calculateGasQuality(twoPhaseFluid);
    assertTrue(gasQuality >= 0.0 && gasQuality <= 1.0,
        "Gas quality should be between 0 and 1: " + gasQuality);

    // Gas-only should be close to 1
    double gasOnlyQuality = chokeModel.calculateGasQuality(gasOnlyFluid);
    assertTrue(gasOnlyQuality > 0.9, "Gas-only fluid should have high gas quality");

    // Liquid-only should be close to 0
    double liquidOnlyQuality = chokeModel.calculateGasQuality(liquidOnlyFluid);
    assertTrue(liquidOnlyQuality < 0.1, "Liquid-only fluid should have low gas quality");
  }

  @Test
  void testMassFlowRateCalculation() {
    double P1 = 100.0e5; // 100 bar
    double P2 = 30.0e5; // 30 bar

    double massFlow = chokeModel.calculateMassFlowRate(twoPhaseFluid, P1, P2);

    assertTrue(massFlow > 0, "Mass flow should be positive");
    assertTrue(massFlow < 1000, "Mass flow should be reasonable (< 1000 kg/s)");

    // Flow should increase with larger pressure drop
    double P2_lower = 20.0e5;
    double massFlow_higher = chokeModel.calculateMassFlowRate(twoPhaseFluid, P1, P2_lower);
    assertTrue(massFlow_higher >= massFlow, "Flow should increase with larger pressure drop");
  }

  @Test
  void testFlowRegimeDetermination() {
    double P1 = 100.0e5;

    // Get the critical pressure ratio for this fluid
    double gasQuality = chokeModel.calculateGasQuality(twoPhaseFluid);
    double gamma = twoPhaseFluid.getGamma2();
    double criticalRatio = chokeModel.calculateCriticalPressureRatio(gasQuality, gamma);

    // Test subcritical: pressure ratio above critical ratio
    double P2_subcritical = P1 * (criticalRatio + 0.15); // Above critical
    MultiphaseChokeFlow.FlowRegime regime_sub =
        chokeModel.determineFlowRegime(twoPhaseFluid, P1, P2_subcritical);
    assertEquals(MultiphaseChokeFlow.FlowRegime.SUBCRITICAL, regime_sub, "Pressure ratio "
        + (P2_subcritical / P1) + " > critical ratio " + criticalRatio + " should be subcritical");

    // Test critical: pressure ratio below critical ratio
    double P2_critical = P1 * (criticalRatio - 0.15); // Below critical
    MultiphaseChokeFlow.FlowRegime regime_crit =
        chokeModel.determineFlowRegime(twoPhaseFluid, P1, P2_critical);
    assertEquals(MultiphaseChokeFlow.FlowRegime.CRITICAL, regime_crit, "Pressure ratio "
        + (P2_critical / P1) + " < critical ratio " + criticalRatio + " should be critical");
  }

  @Test
  void testCriticalFlowIndependenceOfDownstreamPressure() {
    // In critical flow, mass flow should not change with downstream pressure
    double P1 = 100.0e5;

    // Get critical pressure
    double gasQuality = chokeModel.calculateGasQuality(twoPhaseFluid);
    double criticalRatio = chokeModel.calculateCriticalPressureRatio(gasQuality, 1.3);
    double P_critical = P1 * criticalRatio;

    // Calculate flow at critical condition
    double P2_1 = P_critical * 0.8; // Below critical
    double P2_2 = P_critical * 0.5; // Much below critical

    double massFlow1 = chokeModel.calculateMassFlowRate(twoPhaseFluid, P1, P2_1);
    double massFlow2 = chokeModel.calculateMassFlowRate(twoPhaseFluid, P1, P2_2);

    // Both should be essentially equal (within 1%)
    assertEquals(massFlow1, massFlow2, massFlow1 * 0.01,
        "Critical flow should be independent of downstream pressure");
  }

  @Test
  void testSizingResults() {
    double P1 = 100.0e5;
    double P2 = 30.0e5;

    Map<String, Object> results = chokeModel.calculateSizingResults(twoPhaseFluid, P1, P2);

    assertNotNull(results);
    assertTrue(results.containsKey("massFlowRate"));
    assertTrue(results.containsKey("gasQuality"));
    assertTrue(results.containsKey("flowRegime"));
    assertTrue(results.containsKey("criticalPressureRatio"));
    assertTrue(results.containsKey("isChoked"));
    assertTrue(results.containsKey("dischargeCoefficient"));

    double massFlow = (double) results.get("massFlowRate");
    assertTrue(massFlow > 0, "Mass flow should be positive");

    String regime = (String) results.get("flowRegime");
    assertTrue(regime.equals("CRITICAL") || regime.equals("SUBCRITICAL"));
  }

  @Test
  void testDownstreamPressureCalculation() {
    double P1 = 100.0e5;

    // Get critical pressure for this fluid
    double gasQuality = chokeModel.calculateGasQuality(twoPhaseFluid);
    double gamma = twoPhaseFluid.getGamma2();
    double criticalRatio = chokeModel.calculateCriticalPressureRatio(gasQuality, gamma);
    double P_critical = P1 * criticalRatio;

    // Test in subcritical range - use a pressure well above critical
    double P2_test = P1 * 0.9; // 90% of upstream pressure (subcritical)
    double massFlow = chokeModel.calculateMassFlowRate(twoPhaseFluid, P1, P2_test);

    // Now calculate back the downstream pressure
    double P2_calculated = chokeModel.calculateDownstreamPressure(twoPhaseFluid, P1, massFlow);

    // In subcritical flow, calculated P2 should be within 15% of test P2
    // or equal to the critical pressure (if the test P2 is below critical)
    if (P2_test < P_critical) {
      // Test pressure is below critical - we expect critical pressure
      assertEquals(P_critical, P2_calculated, P_critical * 0.10,
          "For critical flow, should return critical pressure");
    } else {
      // Subcritical - should match
      assertEquals(P2_test, P2_calculated, P2_test * 0.15,
          "Calculated downstream pressure should match input for subcritical flow");
    }
  }

  @Test
  void testVariableDischargeCoefficient() {
    // Test Cd variation with Reynolds number and void fraction
    double Cd_high_Re = chokeModel.calculateVariableDischargeCoefficient(100000, 0.5);
    double Cd_low_Re = chokeModel.calculateVariableDischargeCoefficient(5000, 0.5);

    assertTrue(Cd_high_Re > Cd_low_Re, "Cd should be higher at higher Reynolds number");

    // Test variation with void fraction
    double Cd_gas_rich = chokeModel.calculateVariableDischargeCoefficient(50000, 0.9);
    double Cd_liquid_rich = chokeModel.calculateVariableDischargeCoefficient(50000, 0.1);

    assertTrue(Cd_gas_rich > Cd_liquid_rich, "Cd should be higher for gas-rich flow");
  }

  @Test
  void testLimitingCases() {
    // Near-pure gas should give similar results to gas-only model
    chokeModel.setChokeDiameter(0.5, "in");
    double P1 = 100.0e5;
    double P2 = 30.0e5;

    double gasFlow = chokeModel.calculateMassFlowRate(gasOnlyFluid, P1, P2);
    assertTrue(gasFlow > 0, "Gas flow should be positive");

    // Near-pure liquid should give reasonable flow
    double liquidFlow = chokeModel.calculateMassFlowRate(liquidOnlyFluid, P1, P2);
    assertTrue(liquidFlow > 0, "Liquid flow should be positive");
  }

  @Test
  void testChokeAreaCalculation() {
    chokeModel.setChokeDiameter(0.0254); // 1 inch
    double area = chokeModel.getChokeArea();

    // Area = pi * d^2 / 4
    double expectedArea = Math.PI * 0.0254 * 0.0254 / 4.0;
    assertEquals(expectedArea, area, 1e-8);
  }
}
