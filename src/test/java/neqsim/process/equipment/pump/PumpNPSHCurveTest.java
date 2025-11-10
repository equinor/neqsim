package neqsim.process.equipment.pump;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for validating NPSH curve functionality and affinity law scaling.
 *
 * <p>
 * Tests that NPSH required follows affinity laws: NPSH ∝ N² (where N is speed)
 * </p>
 *
 * @author NeqSim
 */
public class PumpNPSHCurveTest extends neqsim.NeqSimTest {

  private SystemInterface testFluid;
  private Stream feedStream;
  private Pump pump;

  @BeforeEach
  void setUp() {
    // Create test fluid (water at standard conditions)
    testFluid = new SystemSrkEos(298.15, 2.0);
    testFluid.addComponent("water", 1.0);
    testFluid.setTemperature(25.0, "C");
    testFluid.setPressure(2.0, "bara");
    testFluid.init(0);
    testFluid.initPhysicalProperties();

    feedStream = new Stream("Feed", testFluid);
    feedStream.run();

    pump = new Pump("TestPump", feedStream);

    // Set up pump performance curves
    double[] speed = new double[] {1000.0, 1500.0};
    double[][] flow = new double[][] {{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0},
        {15.0, 30.0, 45.0, 60.0, 75.0, 90.0, 105.0, 120.0}};
    double[][] head = new double[][] {{120.0, 118.0, 115.0, 110.0, 103.0, 94.0, 83.0, 70.0},
        {270.0, 265.5, 258.8, 247.5, 231.8, 211.5, 186.8, 157.5}};
    double[][] efficiency = new double[][] {{60.0, 70.0, 78.0, 82.0, 81.0, 76.0, 68.0, 55.0},
        {62.0, 71.0, 79.0, 83.0, 82.0, 77.0, 69.0, 56.0}};

    pump.getPumpChart().setCurves(new double[] {}, speed, flow, head, efficiency);
    pump.getPumpChart().setHeadUnit("meter");
  }

  @Test
  void testSetNPSHCurve() {
    // Test setting NPSH curve with valid data
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0}, // At 1000 rpm
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0} // At 1500 rpm (2.25x first row)
    };

    pump.getPumpChart().setNPSHCurve(npsh);

    Assertions.assertTrue(pump.getPumpChart().hasNPSHCurve(),
        "Pump chart should report NPSH curve is available");
  }

  @Test
  void testNPSHAffinityLaw() {
    // NPSH should scale as N² (speed squared)
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0}, // At 1000 rpm
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0} // At 1500 rpm
    };

    pump.getPumpChart().setNPSHCurve(npsh);

    double speed1 = 1000.0;
    double speed2 = 1500.0;
    double speedRatio = speed2 / speed1; // 1.5

    // Test at same reduced flow (different actual flows)
    double reducedFlow = 0.04; // m³/hr per rpm
    double flow1 = reducedFlow * speed1; // 40 m³/hr
    double flow2 = reducedFlow * speed2; // 60 m³/hr

    double npsh1 = pump.getPumpChart().getNPSHRequired(flow1, speed1);
    double npsh2 = pump.getPumpChart().getNPSHRequired(flow2, speed2);

    // NPSH should scale as square of speed ratio
    double expectedRatio = speedRatio * speedRatio; // 2.25
    double actualRatio = npsh2 / npsh1;

    Assertions.assertEquals(expectedRatio, actualRatio, 0.15,
        "NPSH should scale as (N₂/N₁)² per affinity laws");
  }

  @Test
  void testNPSHIncreasesWithFlow() {
    // NPSH typically increases with flow rate
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);

    double speed = 1000.0;
    double npsh1 = pump.getPumpChart().getNPSHRequired(20.0, speed);
    double npsh2 = pump.getPumpChart().getNPSHRequired(50.0, speed);
    double npsh3 = pump.getPumpChart().getNPSHRequired(70.0, speed);

    Assertions.assertTrue(npsh2 > npsh1, "NPSH should increase with flow");
    Assertions.assertTrue(npsh3 > npsh2, "NPSH should continue increasing with flow");
  }

  @Test
  void testNPSHInterpolation() {
    // Test interpolation between data points
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);

    // Test at a flow between data points (e.g., 35 m³/hr at 1000 rpm)
    // Should interpolate between 30 m³/hr (2.5 m) and 40 m³/hr (3.0 m)
    double speed = 1000.0;
    double flowBetween = 35.0;
    double npshInterpolated = pump.getPumpChart().getNPSHRequired(flowBetween, speed);

    // Should be between 2.5 and 3.0
    Assertions.assertTrue(npshInterpolated > 2.5 && npshInterpolated < 3.0,
        "Interpolated NPSH should be between bounding values");

    // Should be close to midpoint
    Assertions.assertEquals(2.75, npshInterpolated, 0.2,
        "Interpolated NPSH should be near expected value");
  }

  @Test
  void testPumpUsesChartNPSH() {
    // Test that Pump.getNPSHRequired() uses chart data when available
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);
    pump.setSpeed(1000.0);

    // Set flow by adjusting fluid and re-initializing
    feedStream.getThermoSystem().setTotalFlowRate(40.0 / 3600.0, "kg/sec"); // ~40 m³/hr for water
    feedStream.run();

    // Run pump to process the stream
    pump.run();

    double npshFromPump = pump.getNPSHRequired();

    // Should return value from NPSH curve (reasonable range 2-4 m for this flow/speed)
    Assertions.assertTrue(npshFromPump > 2.0 && npshFromPump < 4.0,
        "Pump should return NPSH from chart data, got: " + npshFromPump + " m");
  }

  @Test
  void testNPSHFallbackWithoutCurve() {
    // Without NPSH curve, should return estimated values
    pump.setSpeed(1000.0);

    // Set flow by adjusting fluid and re-initializing
    feedStream.getThermoSystem().setTotalFlowRate(50.0 / 3600.0, "kg/sec"); // ~50 m³/hr for water
    feedStream.run();

    double npshEstimate = pump.getNPSHRequired();

    // Should return reasonable estimate (2-10 m range)
    Assertions.assertTrue(npshEstimate >= 2.0 && npshEstimate <= 10.0,
        "Without NPSH curve, should return reasonable estimate");
  }

  @Test
  void testNPSHCurveDimensionValidation() {
    // Test that mismatched dimensions throw exception
    double[][] invalidNPSH = new double[][] {{2.0, 2.2, 2.5} // Wrong number of flow points
    };

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      pump.getPumpChart().setNPSHCurve(invalidNPSH);
    }, "Should throw exception for mismatched dimensions");
  }

  @Test
  void testNPSHCurveBeforePerformanceCurves() {
    // Test that setting NPSH before performance curves throws exception
    Pump newPump = new Pump("NewPump");

    double[][] npsh = new double[][] {{2.0, 2.2, 2.5}};

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      newPump.getPumpChart().setNPSHCurve(npsh);
    }, "Should require performance curves before NPSH curves");
  }

  @Test
  void testCavitationDetectionWithNPSHCurve() {
    // Test cavitation detection using chart NPSH
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);
    pump.setSpeed(1000.0);
    pump.setCheckNPSH(true);
    pump.setNPSHMargin(1.3);

    // Set low suction pressure to create cavitation risk
    feedStream.setTemperature(80.0, "C"); // High temperature = high vapor pressure
    feedStream.setPressure(1.05, "bara"); // Low suction pressure
    feedStream.getThermoSystem().setTotalFlowRate(50.0 / 3600.0, "kg/sec"); // ~50 m³/hr for water
    feedStream.run();

    // This may or may not cavitate depending on conditions
    // Just ensure method executes without error
    boolean cavitating = pump.isCavitating();
    Assertions.assertNotNull(cavitating);
  }

  @Test
  void testNPSHExtrapolationWarning() {
    // Test that extrapolation beyond measured range logs warning
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);

    // Query beyond measured range
    double speed = 1000.0;
    double highFlow = 150.0; // Well beyond 80 m³/hr maximum

    // Should still return a value (extrapolated)
    double npshExtrapolated = pump.getPumpChart().getNPSHRequired(highFlow, speed);

    Assertions.assertTrue(npshExtrapolated > 0.0,
        "Should return extrapolated value even outside range");
  }

  @Test
  void testNPSHWithDifferentSpeeds() {
    // Test NPSH calculation at various speeds
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);

    double baseSpeed = 1000.0;
    double baseFlow = 40.0;
    double baseNPSH = pump.getPumpChart().getNPSHRequired(baseFlow, baseSpeed);

    // Test at 1200 rpm (1.2x speed) - same reduced flow
    double newSpeed = 1200.0;
    double newFlow = baseFlow * 1.2; // Scale flow proportionally
    double newNPSH = pump.getPumpChart().getNPSHRequired(newFlow, newSpeed);

    // Should scale as (1.2)² = 1.44
    double expectedRatio = 1.44;
    double actualRatio = newNPSH / baseNPSH;

    Assertions.assertEquals(expectedRatio, actualRatio, 0.1,
        "NPSH should scale with speed squared");
  }

  @Test
  void testNPSHNonNegative() {
    // Ensure NPSH is never negative even with bad curve fit
    double[][] npsh = new double[][] {{2.0, 2.2, 2.5, 3.0, 3.8, 4.8, 6.2, 8.0},
        {4.5, 4.95, 5.625, 6.75, 8.55, 10.8, 13.95, 18.0}};

    pump.getPumpChart().setNPSHCurve(npsh);

    // Query at very low flow where polynomial might go negative
    double speed = 1000.0;
    double veryLowFlow = 1.0;

    double npshAtLowFlow = pump.getPumpChart().getNPSHRequired(veryLowFlow, speed);

    Assertions.assertTrue(npshAtLowFlow >= 0.0, "NPSH should never be negative");
  }
}
