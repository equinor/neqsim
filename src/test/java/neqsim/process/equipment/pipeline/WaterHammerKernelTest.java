package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Analytical Safety Shutdown kernel checks.
 *
 * @author NeqSim
 * @version 1.0
 */
class WaterHammerKernelTest {
  /** Static model edits cannot reuse old grid or impedance state. */
  @Test
  void staticConfigurationRequiresExplicitInitialization() {
    java.util.List<java.util.function.Consumer<WaterHammerPipe>> changes = java.util.Arrays.asList(
        p -> p.setWaveSpeed(2000.0), p -> p.setLength(2000.0), p -> p.setLength(2.0, "km"), p -> p.setDiameter(0.3),
        p -> p.setDiameter(300.0, "mm"), p -> p.setWallThickness(0.02), p -> p.setRoughness(0.0001),
        p -> p.setPipeWallRoughness(0.0001), p -> p.setNumberOfNodes(51), p -> p.setNumberOfIncrements(51),
        p -> p.setPipeElasticModulus(100.0e9), p -> p.setElevation(10.0), p -> p.setElevationChange(10.0));
    for (java.util.function.Consumer<WaterHammerPipe> change : changes) {
      WaterHammerPipe pipe = pipe();
      double[] before = pipe.getPressureProfile();
      change.accept(pipe);
      assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.005, UUID.randomUUID()));
      assertArrayEquals(before, pipe.getPressureProfile(), 0.0);
      assertEquals(0.0, pipe.getCurrentTime());
      assertTrue(pipe.getTimeHistory().isEmpty());
      pipe.run(UUID.randomUUID());
      pipe.runTransient(0.005, UUID.randomUUID());
      assertEquals(0.005, pipe.getCurrentTime(), 1.0e-15);
    }
  }

  /** Wave-speed inputs reject nonphysical overrides at their API boundary. */
  @Test
  void invalidWaveSpeedOverride() {
    WaterHammerPipe pipe = pipe();
    for (double speed : new double[] {0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> pipe.setWaveSpeed(speed));
    }
    pipe.runTransient(0.005, UUID.randomUUID());
    assertEquals(0.005, pipe.getCurrentTime());
  }

  /** Unchanged static inputs and supported live controls retain transient state. */
  @Test
  void unchangedConfigurationAndLiveControls() {
    WaterHammerPipe pipe = pipe();
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(101);
    pipe.setWaveSpeed(1000.0);
    pipe.setCourantNumber(0.5);
    pipe.setValveBackPressure(1.1);
    pipe.setValveOpening(0.9);
    pipe.runTransient(0.005, UUID.randomUUID());
    assertEquals(0.005, pipe.getCurrentTime());
    assertEquals(1, pipe.getTimeHistory().size());
  }

  /** Explicit reinitialization recomputes EOS/elastic speed but preserves a user override. */
  @Test
  void reinitializeAutomaticAndOverriddenWaveSpeed() {
    WaterHammerPipe pipe = pipe();
    pipe.setWaveSpeed(-1.0);
    pipe.run(UUID.randomUUID());
    double steelSpeed = pipe.getWaveSpeed();
    pipe.setPipeElasticModulus(100.0e9);
    pipe.run(UUID.randomUUID());
    assertTrue(pipe.getWaveSpeed() < steelSpeed);
    pipe.setWaveSpeed(900.0);
    pipe.setPipeElasticModulus(200.0e9);
    pipe.run(UUID.randomUUID());
    assertEquals(900.0, pipe.getWaveSpeed());
  }

  /** Study duration and unsupported events have explicit execution semantics. */
  @Test
  void studyDurationAndEventValidation() {
    com.google.gson.JsonObject input = com.google.gson.JsonParser
        .parseString(neqsim.mcp.catalog.ExampleCatalog.waterHammerValveClosure()).getAsJsonObject();
    input.addProperty("simulationTime_s", 0.037);
    input.addProperty("timeStep_s", 0.01);
    input.add("eventSchedule", new com.google.gson.JsonArray());
    com.google.gson.JsonObject result = com.google.gson.JsonParser
        .parseString(neqsim.process.operations.WaterHammerStudy.run(input.toString())).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    com.google.gson.JsonArray times = result.getAsJsonObject("timeSeries").getAsJsonArray("time_s");
    assertEquals(0.037, times.get(times.size() - 1).getAsDouble(), 1.0e-15);
    com.google.gson.JsonObject event = new com.google.gson.JsonObject();
    event.addProperty("type", "PUMP_TRIP");
    input.getAsJsonArray("eventSchedule").add(event);
    result = com.google.gson.JsonParser.parseString(neqsim.process.operations.WaterHammerStudy.run(input.toString()))
        .getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
  }

  /**
   * Write independently interpretable public benchmark evidence.
   *
   * @throws Exception if evidence cannot be written
   */
  @Test
  void benchmarkEvidence() throws Exception {
    long started = System.nanoTime();
    WaterHammerPipe pipe = pipe();
    double[] initial = pipe.getPressureProfile();
    for (int i = 0; i < 100; i++) {
      pipe.runTransient(0.005, UUID.randomUUID());
    }
    double maxDrift = 0.0;
    for (int i = 0; i < initial.length; i++) {
      maxDrift = Math.max(maxDrift, Math.abs(initial[i] - pipe.getPressureProfile()[i]));
    }
    assertTrue(maxDrift < 1.0e-4);
    pipe = pipe();
    double pressure = pipe.getPressureProfile()[100];
    double expected = pipe.calcJoukowskyPressureSurge(pipe.getVelocityProfile()[100]);
    pipe.setValveOpening(0.0);
    pipe.runTransient(0.005, UUID.randomUUID());
    double actual = pipe.getPressureProfile()[100] - pressure;
    com.google.gson.JsonObject evidence = new com.google.gson.JsonObject();
    evidence.addProperty("equilibriumMaxDrift_Pa", maxDrift);
    evidence.addProperty("joukowskyExpected_Pa", expected);
    evidence.addProperty("firstImpulse_Pa", actual);
    evidence.addProperty("relativeImpulseError", Math.abs(actual / expected - 1.0));
    evidence.addProperty("waveRoundTripTime_s", pipe.getWaveRoundTripTime());
    evidence.addProperty("fixtureRuntime_ms", (System.nanoTime() - started) / 1.0e6);
    java.nio.file.Files.write(java.nio.file.Paths.get("target", "ss-kernel-benchmark.json"),
        evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * Creates a public single-phase liquid fixture.
   *
   * @return initialized pipe
   */
  private WaterHammerPipe pipe() {
    return pipe(36000.0);
  }

  /**
   * Creates a nearby-rate fixture.
   *
   * @param massFlow mass flow in kg/hr
   * @return initialized single-phase pipe
   */
  private WaterHammerPipe pipe(double massFlow) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(massFlow, "kg/hr");
    Stream feed = new Stream("feed", fluid);
    feed.run();
    WaterHammerPipe pipe = new WaterHammerPipe("Safety Shutdown", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(101);
    pipe.setWaveSpeed(1000.0);
    pipe.run(UUID.randomUUID());
    return pipe;
  }

  /** An unchanged open valve must preserve the initialized operating point. */
  @Test
  void openValveEquilibrium() {
    WaterHammerPipe pipe = pipe();
    double[] before = pipe.getPressureProfile();
    double flow = pipe.getFlowProfile()[100];
    for (int i = 0; i < 20; i++)
      pipe.runTransient(0.005, UUID.randomUUID());
    assertArrayEquals(before, pipe.getPressureProfile(), 1.0e-4);
    assertEquals(flow, pipe.getFlowProfile()[100], 1.0e-10);
  }

  /** Acoustic travel time must not depend on the requested outer timestep. */
  @Test
  void waveTravelTime() {
    WaterHammerPipe pipe = pipe();
    double p0 = pipe.getPressureProfile()[50];
    pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.CLOSED_END);
    for (int i = 0; i < 60; i++)
      pipe.runTransient(0.005, UUID.randomUUID());
    assertEquals(p0, pipe.getPressureProfile()[50], 100.0);
  }

  /** First arrival and reservoir reflection follow acoustic time at nearby rates. */
  @Test
  void arrivalReflectionAndNearbyRates() {
    for (double rate : new double[] {18000.0, 36000.0, 54000.0}) {
      for (double dt : new double[] {0.01, 0.005}) {
        WaterHammerPipe pipe = pipe(rate);
        double midpoint = pipe.getPressureProfile()[50];
        double outlet = pipe.getPressureProfile()[100];
        double impulse = pipe.calcJoukowskyPressureSurge(pipe.getVelocityProfile()[100]);
        pipe.setValveOpening(0.0);
        double arrival = Double.NaN;
        double reflection = Double.NaN;
        while (pipe.getCurrentTime() < 2.2) {
          pipe.runTransient(dt, UUID.randomUUID());
          if (Double.isNaN(arrival) && pipe.getPressureProfile()[50] > midpoint + 0.5 * impulse) {
            arrival = pipe.getCurrentTime();
          }
          if (pipe.getCurrentTime() > 1.5 && Double.isNaN(reflection) && pipe.getPressureProfile()[100] < outlet) {
            reflection = pipe.getCurrentTime();
          }
        }
        assertEquals(0.5, arrival, 2.0 * dt);
        assertEquals(2.0, reflection, 2.0 * dt);
      }
    }
  }

  /** Inclined steady state must retain its hydrostatic and friction gradients. */
  @Test
  void inclinedEquilibrium() {
    WaterHammerPipe pipe = pipe();
    pipe.setElevationChange(50.0);
    pipe.run(UUID.randomUUID());
    double[] before = pipe.getPressureProfile();
    pipe.runTransient(0.123, UUID.randomUUID());
    assertArrayEquals(before, pipe.getPressureProfile(), 1.0e-4);
    assertEquals(0.123, pipe.getCurrentTime(), 1.0e-15);
  }

  /** The first closure impulse equals the analytical Joukowsky limit. */
  @Test
  void joukowskyAndRefinement() {
    for (int nodes : new int[] {51, 101, 201}) {
      for (double fraction : new double[] {1.0, 0.5, 0.37}) {
        WaterHammerPipe pipe = pipe();
        pipe.setNumberOfNodes(nodes);
        pipe.run(UUID.randomUUID());
        double initialPressure = pipe.getPressureProfile()[nodes - 1];
        double expected = pipe.calcJoukowskyPressureSurge(pipe.getVelocityProfile()[nodes - 1]);
        pipe.setValveOpening(0.0);
        pipe.runTransient(fraction * pipe.getMaxStableTimeStep(), UUID.randomUUID());
        assertEquals(expected, pipe.getPressureProfile()[nodes - 1] - initialPressure, expected * 0.01);
      }
    }
  }

  /** Closure slower than the acoustic round trip reduces the peak surge. */
  @Test
  void slowClosureReducesSurge() {
    double[] peaks = new double[2];
    double[] durations = {0.1, 3.0};
    for (int caseIndex = 0; caseIndex < durations.length; caseIndex++) {
      WaterHammerPipe pipe = pipe();
      for (int step = 1; step <= 700; step++) {
        double time = step * 0.01;
        pipe.setValveOpening(Math.max(0.0, 1.0 - time / durations[caseIndex]));
        pipe.runTransient(0.01, UUID.randomUUID());
      }
      peaks[caseIndex] = pipe.getMaxPressure("bar");
    }
    assertTrue(peaks[1] < peaks[0]);
  }

  /** Invalid intervals are rejected instead of corrupting the clock. */
  @Test
  void invalidIntervalsAndReceiver() {
    WaterHammerPipe pipe = pipe();
    for (double dt : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(dt, UUID.randomUUID()));
    }
    assertEquals(0.0, pipe.getCurrentTime());
    assertThrows(IllegalArgumentException.class, () -> pipe.setValveBackPressure(Double.NaN));
    pipe.setValveBackPressure(5.0);
    pipe.run(UUID.randomUUID());
    double[] before = pipe.getPressureProfile();
    pipe.runTransient(0.02, UUID.randomUUID());
    assertArrayEquals(before, pipe.getPressureProfile(), 1.0e-4);
  }

  /** Subcycling is equivalent to explicit equal internal steps. */
  @Test
  void subcycleEquivalence() {
    WaterHammerPipe a = pipe();
    WaterHammerPipe b = pipe();
    a.setValveOpening(0.5);
    b.setValveOpening(0.5);
    a.runTransient(0.037, UUID.randomUUID());
    for (int i = 0; i < 4; i++) {
      b.runTransient(0.037 / 4.0, UUID.randomUUID());
    }
    assertArrayEquals(a.getPressureProfile(), b.getPressureProfile(), 1.0e-8);
    assertArrayEquals(a.getFlowProfile(), b.getFlowProfile(), 1.0e-12);
  }

  /** Linear acoustic storage closes against trapezoidal boundary-volume exchange. */
  @Test
  void acousticVolumeBalance() {
    WaterHammerPipe pipe = pipe();
    double[] initial = pipe.getPressureProfile();
    double velocity = pipe.getVelocityProfile()[0];
    double density = pipe.calcJoukowskyPressureSurge(velocity) / (pipe.getWaveSpeed() * velocity);
    double boundaryVolume = 0.0;
    pipe.setValveOpening(0.0);
    for (int step = 0; step < 120; step++) {
      double[] before = pipe.getFlowProfile();
      if (step == 0) {
        // The imposed instantaneous closure occurs at t=0, before integration;
        // the pre-event outlet flow must not be integrated as a half-step ramp.
        before[100] = 0.0;
      }
      pipe.runTransient(0.005, UUID.randomUUID());
      double[] after = pipe.getFlowProfile();
      boundaryVolume += 0.005 * 0.5 * (before[0] + after[0] - before[100] - after[100]);
    }
    double integral = 0.0;
    double[] pressure = pipe.getPressureProfile();
    for (int i = 0; i < pressure.length; i++) {
      double weight = i == 0 || i == 100 ? 0.5 : 1.0;
      integral += weight * (pressure[i] - initial[i]) * 10.0;
    }
    double storedVolume = Math.PI * 0.01 * integral / (density * 1.0e6);
    assertEquals(boundaryVolume, storedVolume, 1.0e-9);
  }

  /**
   * Java serialization preserves deterministic continuation of the hydraulic state.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void restartContinuation() throws Exception {
    WaterHammerPipe pipe = pipe();
    pipe.setValveOpening(0.5);
    pipe.runTransient(0.037, UUID.randomUUID());
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(pipe);
    }
    WaterHammerPipe restored;
    try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(
        new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (WaterHammerPipe) in.readObject();
    }
    pipe.runTransient(0.023, UUID.randomUUID());
    restored.runTransient(0.023, UUID.randomUUID());
    assertArrayEquals(pipe.getPressureProfile(), restored.getPressureProfile(), 0.0);
    assertArrayEquals(pipe.getFlowProfile(), restored.getFlowProfile(), 0.0);
    assertEquals(pipe.getCurrentTime(), restored.getCurrentTime());
  }
}
