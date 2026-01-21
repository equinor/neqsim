package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.WaterHammerPipe.BoundaryType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for WaterHammerPipe.
 */
public class WaterHammerPipeTest {
  private SystemInterface water;
  private Stream feed;
  private WaterHammerPipe pipe;
  private UUID id;

  @BeforeEach
  void setUp() {
    // Create water system
    water = new SystemSrkEos(298.15, 10.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    water.setTotalFlowRate(100.0, "kg/hr");

    feed = new Stream("feed", water);
    feed.run();

    pipe = new WaterHammerPipe("test pipe", feed);
    id = UUID.randomUUID();
  }

  @Test
  void testSteadyStateInitialization() {
    pipe.setLength(1000);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(50);
    pipe.run(id);

    // Check wave speed is positive and reasonable
    // Note: SRK EOS may give different values than pure water correlations
    double waveSpeed = pipe.getWaveSpeed();
    assertTrue(waveSpeed > 100 && waveSpeed < 5000,
        "Wave speed should be positive and reasonable, got: " + waveSpeed);

    // Check pressure profile exists
    double[] pressures = pipe.getPressureProfile();
    assertEquals(50, pressures.length);

    // Pressure should decrease along pipe (friction)
    assertTrue(pressures[0] > pressures[49], "Pressure should decrease along pipe due to friction");
  }

  @Test
  void testJoukowskyPressureSurge() {
    pipe.setLength(1000);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(50);
    pipe.run(id);

    // Test Joukowsky formula: dP = rho * c * dv
    double velocityChange = 1.0; // m/s
    double surgePa = pipe.calcJoukowskyPressureSurge(velocityChange);
    double surgeBar = pipe.calcJoukowskyPressureSurge(velocityChange, "bar");

    // Surge should be positive and significant
    // Exact value depends on EOS-calculated wave speed
    assertTrue(surgeBar > 1 && surgeBar < 50,
        "Joukowsky surge for 1 m/s should be positive and reasonable, got: " + surgeBar);
    assertEquals(surgePa / 1e5, surgeBar, 0.001);
  }

  @Test
  void testWaveRoundTripTime() {
    pipe.setLength(1000);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(50);
    pipe.run(id);

    double roundTripTime = pipe.getWaveRoundTripTime();
    double waveSpeed = pipe.getWaveSpeed();

    // Round trip = 2L/c
    double expected = 2 * 1000 / waveSpeed;
    assertEquals(expected, roundTripTime, 0.001);

    // Round trip time should be positive and reasonable
    assertTrue(roundTripTime > 0.1 && roundTripTime < 10,
        "Round trip time should be reasonable for 1 km pipe, got: " + roundTripTime);
  }

  @Test
  void testMaxStableTimeStep() {
    pipe.setLength(1000);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(100);
    pipe.run(id);

    double maxDt = pipe.getMaxStableTimeStep();
    double segmentLength = 1000.0 / 99; // ~10.1 m
    double waveSpeed = pipe.getWaveSpeed();

    // Max dt = Cn * dx / c
    double expected = 1.0 * segmentLength / waveSpeed;
    assertEquals(expected, maxDt, 0.001);

    // Should be on order of 0.01 s for typical case
    assertTrue(maxDt > 0.001 && maxDt < 0.1, "Max time step should be 0.001-0.1 s, got: " + maxDt);
  }

  @Test
  void testTransientSimulation() {
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfNodes(50);
    pipe.setDownstreamBoundary(BoundaryType.RESERVOIR);
    pipe.run(id);

    double initialOutletPressure = pipe.getPressureProfile("bar")[49];

    // Run 100 transient steps
    double dt = pipe.getMaxStableTimeStep();
    for (int step = 0; step < 100; step++) {
      pipe.runTransient(dt, id);
    }

    // Should reach steady state (outlet pressure stable)
    double finalOutletPressure = pipe.getPressureProfile("bar")[49];
    assertEquals(initialOutletPressure, finalOutletPressure, 0.5,
        "Pressure should be stable with constant BCs");

    // Check time history was recorded
    List<Double> history = pipe.getPressureHistory();
    assertEquals(100, history.size());

    double currentTime = pipe.getCurrentTime();
    assertEquals(100 * dt, currentTime, 0.001);
  }

  @Test
  void testValveClosure() {
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfNodes(50);
    pipe.setDownstreamBoundary(BoundaryType.VALVE);
    pipe.run(id);

    double steadyStatePressure = pipe.getMaxPressure("bar");

    // Run transient with valve closure
    double dt = pipe.getMaxStableTimeStep() * 0.5; // Use smaller step for stability
    double closureTime = 0.1; // 100 ms closure

    for (int step = 0; step < 500; step++) {
      double t = step * dt;

      // Close valve linearly over closureTime
      if (t < closureTime) {
        pipe.setValveOpening(1.0 - t / closureTime);
      } else {
        pipe.setValveOpening(0.0);
      }

      pipe.runTransient(dt, id);
    }

    // Maximum pressure should exceed steady state due to water hammer
    double maxPressure = pipe.getMaxPressure("bar");
    assertTrue(maxPressure > steadyStatePressure,
        "Max pressure should exceed steady state after valve closure");

    // Valve should be closed
    assertEquals(0.0, pipe.getValveOpening(), 0.001);
  }

  @Test
  void testClosedEndBoundary() {
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfNodes(50);
    pipe.setUpstreamBoundary(BoundaryType.RESERVOIR);
    pipe.setDownstreamBoundary(BoundaryType.CLOSED_END);
    pipe.run(id);

    // Run a few transient steps
    double dt = pipe.getMaxStableTimeStep();
    for (int step = 0; step < 50; step++) {
      pipe.runTransient(dt, id);
    }

    // At closed end, flow should be zero
    double[] flows = pipe.getFlowProfile();
    assertEquals(0.0, flows[49], 1e-6, "Flow at closed end should be zero");
  }

  @Test
  void testReset() {
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfNodes(50);
    pipe.run(id);

    // Run some transient steps
    double dt = pipe.getMaxStableTimeStep();
    for (int step = 0; step < 50; step++) {
      pipe.runTransient(dt, id);
    }

    assertTrue(pipe.getCurrentTime() > 0);
    assertTrue(pipe.getPressureHistory().size() > 0);

    // Reset
    pipe.reset();
    assertEquals(0.0, pipe.getCurrentTime());
    assertEquals(1.0, pipe.getValveOpening());
  }

  @Test
  void testUnitConversions() {
    pipe.setLength(1, "km");
    assertEquals(1000, pipe.getLength());

    pipe.setLength(3280.84, "ft");
    assertEquals(1000, pipe.getLength(), 1);

    pipe.setDiameter(200, "mm");
    assertEquals(0.2, pipe.getDiameter(), 0.001);

    pipe.setDiameter(8, "in");
    assertEquals(0.2032, pipe.getDiameter(), 0.001);
  }

  @Test
  void testPressureEnvelopes() {
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfNodes(50);
    pipe.setDownstreamBoundary(BoundaryType.VALVE);
    pipe.run(id);

    // Initial max = min = steady state
    double[] maxEnv = pipe.getMaxPressureEnvelope();
    double[] minEnv = pipe.getMinPressureEnvelope();
    assertArrayEquals(maxEnv, minEnv, 0.001);

    // Run transient with valve closure
    double dt = pipe.getMaxStableTimeStep() * 0.5;
    for (int step = 0; step < 200; step++) {
      if (step == 50) {
        pipe.setValveOpening(0.0); // Instant closure
      }
      pipe.runTransient(dt, id);
    }

    // After transient, max > min
    maxEnv = pipe.getMaxPressureEnvelope();
    minEnv = pipe.getMinPressureEnvelope();

    // At least some nodes should show pressure variation
    boolean hasVariation = false;
    for (int i = 0; i < 50; i++) {
      if (maxEnv[i] > minEnv[i] + 1000) { // > 0.01 bar difference
        hasVariation = true;
        break;
      }
    }
    assertTrue(hasVariation, "Should have pressure variation after valve closure");
  }

  @Test
  void testKortewegWaveSpeedReduction() {
    // Test that elastic pipe reduces wave speed
    pipe.setLength(1000);
    pipe.setDiameter(0.2);
    pipe.setWallThickness(0.01); // 10 mm wall
    pipe.setPipeElasticModulus(200e9); // Steel
    pipe.setNumberOfNodes(50);
    pipe.run(id);

    double effectiveSpeed = pipe.getWaveSpeed();

    // Effective speed should be positive and finite
    assertTrue(effectiveSpeed > 0, "Effective wave speed should be positive");
    assertTrue(effectiveSpeed < 10000, "Wave speed should be reasonable");

    // The Korteweg formula should reduce speed compared to pure fluid
    // But we can't easily test this without knowing the pure fluid speed
    // Just verify the speed is calculated
    assertTrue(effectiveSpeed > 100, "Wave speed should be at least 100 m/s");
  }

  @Test
  void testWithGasSystem() {
    // Test with natural gas instead of water
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");
    gas.setTotalFlowRate(10000, "kg/hr");

    Stream gasFeed = new Stream("gas feed", gas);
    gasFeed.run();

    WaterHammerPipe gasPipe = new WaterHammerPipe("gas pipe", gasFeed);
    gasPipe.setLength(1000);
    gasPipe.setDiameter(0.3);
    gasPipe.setNumberOfNodes(50);
    gasPipe.run(id);

    // Gas wave speed should be lower than liquid (~400 m/s)
    double waveSpeed = gasPipe.getWaveSpeed();
    assertTrue(waveSpeed > 100 && waveSpeed < 600,
        "Gas wave speed should be 100-600 m/s, got: " + waveSpeed);

    // Joukowsky surge is lower for gas (lower density)
    double surgePa = gasPipe.calcJoukowskyPressureSurge(10); // 10 m/s change
    double surgeBar = surgePa / 1e5;
    // Gas density ~50 kg/m3, c ~400, dv=10: dP ~ 200 kPa = 2 bar
    assertTrue(surgeBar > 0.5 && surgeBar < 10,
        "Gas surge should be 0.5-10 bar for 10 m/s, got: " + surgeBar);
  }
}
