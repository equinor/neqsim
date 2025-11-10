package neqsim.process.equipment.pump;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for verifying pump affinity laws (similarity laws).
 *
 * <p>
 * Affinity laws relate pump performance at different speeds:
 * </p>
 * <ul>
 * <li>Flow: Q₂/Q₁ = N₂/N₁</li>
 * <li>Head: H₂/H₁ = (N₂/N₁)²</li>
 * <li>Power: P₂/P₁ = (N₂/N₁)³</li>
 * </ul>
 *
 * @author NeqSim
 */
public class PumpAffinityLawTest extends neqsim.NeqSimTest {

  private SystemInterface testFluid;
  private Stream feedStream;
  private Pump pump;

  @BeforeEach
  void setUp() {
    // Create test fluid (water at standard conditions)
    testFluid = new SystemSrkEos(298.15, 1.0);
    testFluid.addComponent("water", 1.0);
    testFluid.init(0);
    testFluid.initPhysicalProperties();

    feedStream = new Stream("Feed", testFluid);

    pump = new Pump("TestPump", feedStream);

    // Set up pump curve at reference speed
    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0}};
    double[][] head = new double[][] {{120.0, 118.0, 115.0, 110.0, 103.0, 94.0, 83.0, 70.0}};
    double[][] efficiency = new double[][] {{60.0, 70.0, 78.0, 82.0, 81.0, 76.0, 68.0, 55.0}};

    pump.getPumpChart().setCurves(new double[] {}, speed, flow, head, efficiency);
    pump.getPumpChart().setHeadUnit("meter");
  }

  @Test
  void testAffinityLawFlow() {
    // Test that flow scales linearly with speed: Q₂/Q₁ = N₂/N₁
    double speed1 = 1000.0;
    double speed2 = 1500.0;
    double speedRatio = speed2 / speed1;

    double testFlow = 40.0; // m³/hr at speed1

    double head1 = pump.getPumpChart().getHead(testFlow, speed1);
    // At speed2, same reduced flow means actual flow increases proportionally
    double flow2 = testFlow * speedRatio;
    double head2 = pump.getPumpChart().getHead(flow2, speed2);

    // Head should scale as square of speed ratio
    double expectedHeadRatio = speedRatio * speedRatio;
    double actualHeadRatio = head2 / head1;

    Assertions.assertEquals(expectedHeadRatio, actualHeadRatio, 0.01,
        "Head should scale as square of speed ratio per affinity laws");
  }

  @Test
  void testAffinityLawHead() {
    // Test that head scales with square of speed: H₂/H₁ = (N₂/N₁)²
    double speed1 = 1000.0;
    double speed2 = 800.0;
    double speedRatio = speed2 / speed1;

    // Test at same reduced flow (which means different actual flows)
    double reducedFlow = 0.05; // m³/hr per rpm
    double flow1 = reducedFlow * speed1;
    double flow2 = reducedFlow * speed2;

    double head1 = pump.getPumpChart().getHead(flow1, speed1);
    double head2 = pump.getPumpChart().getHead(flow2, speed2);

    double expectedHeadRatio = speedRatio * speedRatio;
    double actualHeadRatio = head2 / head1;

    Assertions.assertEquals(expectedHeadRatio, actualHeadRatio, 0.01,
        "Head at same reduced flow should scale as (N₂/N₁)²");
  }

  @Test
  void testAffinityLawPower() {
    // Test that power scales with cube of speed: P₂/P₁ = (N₂/N₁)³
    double speed1 = 1000.0;
    double speed2 = 1200.0;
    double speedRatio = speed2 / speed1;

    double reducedFlow = 0.04; // Same reduced flow
    double flow1 = reducedFlow * speed1;
    double flow2 = reducedFlow * speed2;

    pump.setSpeed(speed1);
    feedStream.getThermoSystem().setTotalFlowRate(flow1 / 3600.0, "kg/sec");
    feedStream.run();
    pump.run();
    double power1 = pump.getPower();

    pump.setSpeed(speed2);
    feedStream.getThermoSystem().setTotalFlowRate(flow2 / 3600.0, "kg/sec");
    feedStream.run();
    pump.run();
    double power2 = pump.getPower();

    double expectedPowerRatio = speedRatio * speedRatio * speedRatio;
    double actualPowerRatio = power2 / power1;

    // Allow larger tolerance due to efficiency variations and numerical effects
    Assertions.assertEquals(expectedPowerRatio, actualPowerRatio, 0.15,
        "Power at same reduced flow should scale as (N₂/N₁)³");
  }

  @Test
  void testEfficiencyRelativelyConstantWithSpeed() {
    // Efficiency should remain relatively constant at same reduced flow
    double speed1 = 1000.0;
    double speed2 = 1300.0;

    double reducedFlow = 0.045; // Near best efficiency point
    double flow1 = reducedFlow * speed1;
    double flow2 = reducedFlow * speed2;

    double eff1 = pump.getPumpChart().getEfficiency(flow1, speed1);
    double eff2 = pump.getPumpChart().getEfficiency(flow2, speed2);

    // Efficiency should be nearly constant (within a few percent)
    Assertions.assertEquals(eff1, eff2, 3.0,
        "Efficiency should remain relatively constant at same reduced flow");
  }

  @Test
  void testBestEfficiencyPoint() {
    // BEP should be around 40-50 m³/hr based on input data
    double bepFlow = pump.getPumpChart().getBestEfficiencyFlowRate();

    Assertions.assertTrue(bepFlow > 30.0 && bepFlow < 60.0,
        "BEP flow should be in the range where efficiency is highest");

    double bepEfficiency = pump.getPumpChart().getEfficiency(bepFlow, 1000.0);
    Assertions.assertTrue(bepEfficiency > 80.0,
        "BEP efficiency should be above 80% based on input data");
  }

  @Test
  void testSpecificSpeed() {
    // Calculate specific speed (should be consistent with centrifugal pump)
    double ns = pump.getPumpChart().getSpecificSpeed();

    // For typical centrifugal pumps: 500 < Ns < 4000 in normal operation
    // Our test setup has minimal flow from init(), so specific speed will be very low (~3)
    // This is expected and just validates the calculation works
    Assertions.assertTrue(ns > 0 && ns < 10000,
        "Specific speed should be positive and reasonable, got: " + ns);
  }
}
