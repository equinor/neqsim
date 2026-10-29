package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.operations.WaterHammerStudy;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent liquid capacity, travel timing and Safety Shutdown acoustic verification.
 *
 * @author NeqSim
 * @version 1.0
 */
class AcousticValveBoundaryTest {
  /**
   * Build a public synthetic consistent pipe/receiver/capacity fixture without altering vendor data.
   *
   * @param nodes grid nodes
   * @param massRate feed kg/hour
   * @param duration travel duration seconds
   * @return initialized physical-boundary fixture
   */
  private static WaterHammerPipe pipe(int nodes, double massRate, double duration) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(massRate, "kg/hr");
    Stream feed = new Stream("public water feed", fluid);
    feed.run();
    WaterHammerPipe pipe = new WaterHammerPipe("Safety Shutdown physical valve", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(nodes);
    pipe.setWaveSpeed(1000.0);
    pipe.run(UUID.randomUUID());
    double q = pipe.getFlowProfile()[0];
    double rho = feed.getThermoSystem().getDensity("kg/m3");
    double outletPa = pipe.getPressureProfile()[nodes - 1];
    // Synthetic capacity selected from the independently stated steady liquid law, not vendor calibration.
    double kv = 3600.0 * q * Math.sqrt((rho / 1000.0) / ((outletPa - 1.0e5) / 1.0e5));
    pipe.setValveBoundary(boundary(kv, duration));
    pipe.run(UUID.randomUUID());
    return pipe;
  }

  /**
   * Define nonlinear capacity and delayed prescribed travel.
   *
   * @param kv fully open metric capacity
   * @param duration travel seconds
   * @return immutable boundary
   */
  private static AcousticValveBoundary boundary(double kv, double duration) {
    return new AcousticValveBoundary(new double[] {0.0, 0.5, 1.0}, new double[] {0.0, kv * 0.2, kv},
        AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, duration * 0.4, duration},
        new double[] {1.0, 0.5, 0.0}, 0.013, 0.01);
  }

  /** Capacity units, monotone interpolation, exact travel knots and defensive copies are explicit. */
  @Test
  void capacityUnitsAndTravel() {
    double[] positions = {0.0, 0.5, 1.0};
    double[] coefficients = {0.0, 2.0, 10.0};
    double[] times = {0.0, 0.2, 0.5};
    double[] travel = {1.0, 0.5, 0.0};
    AcousticValveBoundary kv = new AcousticValveBoundary(positions, coefficients, AcousticValveBoundary.CapacityUnit.KV,
        times, travel, 1.0, 0.1);
    AcousticValveBoundary cv = new AcousticValveBoundary(positions, new double[] {0.0, 2.312, 11.56},
        AcousticValveBoundary.CapacityUnit.CV_US, times, travel, 1.0, 0.1);
    positions[1] = 0.7;
    coefficients[1] = 8.0;
    times[1] = 0.3;
    travel[1] = 0.1;
    assertEquals(1.0, kv.nextKnotAfter(0.0), 1.0e-12);
    assertEquals(1.1, kv.nextKnotAfter(1.0), 1.0e-12);
    assertEquals(1.3, kv.nextKnotAfter(1.1), 1.0e-12);
    assertEquals(1.0, kv.getOpening(1.1));
    assertEquals(0.75, kv.getOpening(1.2), 1.0e-14);
    assertEquals(2.0, kv.getKv(1.3), 1.0e-12);
    assertEquals(0.0, kv.getKv(1.6));
    assertEquals(Double.POSITIVE_INFINITY, kv.nextKnotAfter(1.6));
    for (int i = 0; i <= 100; i++) {
      double t = 1.1 + i * 0.005;
      assertEquals(kv.getKv(t), cv.getKv(t), 1.0e-12);
      assertTrue(kv.getKv(t) >= 0.0 && kv.getKv(t) <= 10.0);
    }
  }

  /** Invalid physical data reject without filtering or fitting measured records. */
  @Test
  void rejectsInvalidTables() {
    for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1.0}) {
      assertThrows(IllegalArgumentException.class, () -> boundary(10.0, value));
    }
    assertThrows(IllegalArgumentException.class,
        () -> new AcousticValveBoundary(new double[] {0.0, 1.0}, new double[] {2.0, 1.0},
            AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, 1.0}, new double[] {1.0, 0.0}, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new AcousticValveBoundary(new double[] {0.2, 1.0}, new double[] {0.0, 1.0},
            AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, 1.0}, new double[] {1.0, 0.0}, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new AcousticValveBoundary(new double[] {0.0, 1.0}, new double[] {0.0, 1.0},
            AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, 0.0}, new double[] {1.0, 0.0}, 0.0, 0.0));
    AcousticValveBoundary valid = boundary(10.0, 0.1);
    assertThrows(IllegalArgumentException.class, () -> valid.getOpening(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> valid.nextKnotAfter(-1.0));
    assertThrows(IllegalArgumentException.class,
        () -> new AcousticValveBoundary(new double[] {0.0, 1.0}, new double[] {0.0, 1.0},
            AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, 1.0}, new double[] {1.0, 0.0}, 1.0e308, 0.0));
  }

  /** Nonmonotone actual travel can reopen, and closed-position leakage remains declared capacity. */
  @Test
  void leakageAndReopening() {
    AcousticValveBoundary boundary = new AcousticValveBoundary(new double[] {0.0, 1.0}, new double[] {1.0, 10.0},
        AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, 1.0, 2.0, 3.0}, new double[] {1.0, 0.0, 1.0, 0.0},
        0.0, 0.0);
    assertEquals(1.0, boundary.getKv(1.0));
    assertEquals(10.0, boundary.getKv(2.0));
    assertEquals(1.0, boundary.getKv(3.0));
    assertEquals(10.0, boundary.getKv(0.0));
  }

  /** Physical liquid coefficients cannot be applied silently to gas or a non-valve endpoint. */
  @Test
  void applicabilityGuards() {
    SystemSrkEos gas = new SystemSrkEos(298.15, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream feed = new Stream("gas", gas);
    feed.run();
    WaterHammerPipe gasPipe = new WaterHammerPipe("gas capacity must reject", feed);
    gasPipe.setValveBoundary(boundary(10.0, 0.1));
    assertThrows(IllegalArgumentException.class, () -> gasPipe.run(UUID.randomUUID()));
    WaterHammerPipe pipe = pipe(101, 36000.0, 0.1);
    pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.RESERVOIR);
    double[] before = pipe.getPressureProfile();
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertArrayEquals(before, pipe.getPressureProfile());
    assertEquals(0.0, pipe.getCurrentTime());
  }

  /** Declared physical capacity is never silently fitted to an inconsistent initial state. */
  @Test
  void initializationAndControlOwnership() {
    WaterHammerPipe pipe = pipe(101, 36000.0, 0.1);
    assertThrows(IllegalStateException.class, () -> pipe.setValveOpening(0.5));
    pipe.setValveBoundary(boundary(100.0, 0.1));
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> pipe.run(UUID.randomUUID()));
    pipe.setValveBoundary(null);
    pipe.setValveOpening(1.0);
    pipe.run(UUID.randomUUID());
    pipe.runTransient(0.01, UUID.randomUUID());
    assertTrue(Double.isNaN(pipe.getEffectiveValveKv()));
  }

  /** The dimensional liquid-capacity equation and nonnegative hydraulic dissipation hold at every endpoint. */
  @Test
  void capacityEquilibriumAndPassivity() {
    for (double rate : new double[] {18000.0, 36000.0, 54000.0}) {
      WaterHammerPipe pipe = pipe(101, rate, 0.1);
      double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
      double[] initial = pipe.getPressureProfile();
      pipe.runTransient(0.02, UUID.randomUUID());
      assertArrayEquals(initial, pipe.getPressureProfile(), 1.0e-8);
      for (int i = 0; i < 30; i++) {
        pipe.runTransient(0.005, UUID.randomUUID());
        double pressure = pipe.getPressureProfile()[100];
        double drop = pressure - pipe.getValveBackPressure() * 1.0e5;
        double q = pipe.getFlowProfile()[100];
        double expected = pipe.getEffectiveValveKv() / 3600.0 * Math.sqrt(drop / 1.0e5 / (rho / 1000.0));
        assertEquals(expected, q, 1.0e-10);
        assertTrue(q * drop >= 0.0);
      }
      assertEquals(0.0, pipe.getFlowProfile()[100]);
      assertEquals(0.0, pipe.getValveOpening());
    }
  }

  /** Hidden acoustic substeps split at exact delay/travel knots and match explicit interval calls. */
  @Test
  void localizedSubsteps() {
    WaterHammerPipe internal = pipe(101, 36000.0, 0.1);
    WaterHammerPipe explicit = pipe(101, 36000.0, 0.1);
    internal.runTransient(0.151, UUID.randomUUID());
    double[] endpoints = {0.013, 0.023, 0.063, 0.123, 0.151};
    double time = 0.0;
    for (double end : endpoints) {
      explicit.runTransient(end - time, UUID.randomUUID());
      time = end;
    }
    for (double knot : new double[] {0.013, 0.023, 0.063, 0.123}) {
      assertTrue(internal.getTimeHistory().stream().anyMatch(t -> Math.abs(t - knot) < 1.0e-12));
    }
    assertArrayEquals(explicit.getPressureProfile(), internal.getPressureProfile(), 1.0e-8);
    assertArrayEquals(explicit.getFlowProfile(), internal.getFlowProfile(), 1.0e-12);
    assertEquals(0.151, internal.getCurrentTime(), 1.0e-15);
  }

  /**
   * Serialization is a deterministic checkpoint/replay for this local prescribed boundary.
   *
   * @throws Exception if Java serialization fails
   */
  @Test
  void restartAndReplay() throws Exception {
    WaterHammerPipe pipe = pipe(101, 36000.0, 0.1);
    pipe.runTransient(0.051, UUID.randomUUID());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(pipe);
    }
    WaterHammerPipe restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (WaterHammerPipe) input.readObject();
    }
    pipe.runTransient(0.10, UUID.randomUUID());
    restored.runTransient(0.10, UUID.randomUUID());
    assertArrayEquals(pipe.getPressureProfile(), restored.getPressureProfile(), 0.0);
    assertArrayEquals(pipe.getFlowProfile(), restored.getFlowProfile(), 0.0);
    assertEquals(pipe.getEffectiveValveKv(), restored.getEffectiveValveKv());
  }

  /**
   * Characterize the known nodal-volume defect against an independent zero-friction discrete identity. The physical
   * 1e-9 m3 conservation verdict is retained as FAIL, separate from the identity assertion.
   *
   * @throws Exception if ideal test-state seeding or evidence output fails
   */
  @Test
  void characterizeAcousticVolumeDefect() throws Exception {
    JsonArray evidence = new JsonArray();
    for (int nodes : new int[] {51, 101, 201}) {
      WaterHammerPipe pipe = pipe(nodes, 36000.0, 0.1);
      // Ideal linear-PDE diagnostic only: turn off the frozen momentum source after consistent initialization.
      java.lang.reflect.Field friction = WaterHammerPipe.class.getDeclaredField("frictionFactor");
      friction.setAccessible(true);
      friction.setDouble(pipe, 0.0);
      double[] initial = pipe.getPressureProfile();
      double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
      double dx = 1000.0 / (nodes - 1);
      double boundaryVolume = 0.0;
      double predictedDefect = 0.0;
      while (pipe.getCurrentTime() < 0.6) {
        double end = Math.min(0.6, Math.min(pipe.getCurrentTime() + 0.5 * dx / 1000.0,
            pipe.getValveBoundary().nextKnotAfter(pipe.getCurrentTime())));
        double dt = end - pipe.getCurrentTime();
        double[] before = pipe.getFlowProfile();
        pipe.runTransient(dt, UUID.randomUUID());
        double[] after = pipe.getFlowProfile();
        boundaryVolume += dt * 0.5 * (before[0] + after[0] - before[nodes - 1] - after[nodes - 1]);
        predictedDefect += 0.5 * (dx / 1000.0 - dt) * (after[0] - after[nodes - 1] - before[0] + before[nodes - 1]);
      }
      double integral = 0.0;
      double[] pressure = pipe.getPressureProfile();
      for (int i = 0; i < nodes; i++) {
        integral += (i == 0 || i == nodes - 1 ? 0.5 : 1.0) * (pressure[i] - initial[i]) * dx;
      }
      double storedVolume = Math.PI * 0.01 * integral / (rho * 1.0e6);
      double residual = storedVolume - boundaryVolume;
      assertEquals(predictedDefect, residual, 1.0e-9, "Independent nodal MOC consistency identity");
      JsonObject row = new JsonObject();
      row.addProperty("nodes", nodes);
      row.addProperty("boundaryVolume_m3", boundaryVolume);
      row.addProperty("storedVolume_m3", storedVolume);
      row.addProperty("physicalResidual_m3", residual);
      row.addProperty("predictedDiscretizationDefect_m3", predictedDefect);
      row.addProperty("physicalTolerance_m3", 1.0e-9);
      row.addProperty("physicalConservation", Math.abs(residual) <= 1.0e-9 ? "PASS" : "FAIL");
      evidence.add(row);
    }
    Files.write(Paths.get("target", "ss-physical-valve-volume.json"),
        evidence.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Execute public synthetic matrix and retain raw metrics; timings are diagnostics without a CI threshold.
   *
   * @throws Exception if evidence cannot be written
   */
  @Test
  void syntheticSafetyShutdownEvidence() throws Exception {
    JsonArray evidence = new JsonArray();
    for (int nodes : new int[] {51, 101, 201, 401, 801}) {
      for (double fraction : new double[] {1.0, 0.5}) {
        for (double duration : new double[] {0.02, 0.1, 4.0}) {
          WaterHammerPipe pipe = pipe(nodes, 36000.0, duration);
          double initial = pipe.getPressureProfile()[nodes - 1];
          double initialQ = pipe.getFlowProfile()[nodes - 1];
          double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
          double target = rho * 1000.0 * initialQ / (Math.PI * 0.2 * 0.2 / 4.0);
          long started = System.nanoTime();
          double end = duration > 1.0 ? 5.0 : 0.5;
          while (pipe.getCurrentTime() < end) {
            pipe.runTransient(Math.min(fraction * pipe.getMaxStableTimeStep(), end - pipe.getCurrentTime()),
                UUID.randomUUID());
          }
          double surge = pipe.getMaxPressure() - initial;
          if (duration == 0.02) {
            // No reservoir reflection returns before this end time; include recovered pipe friction head.
            assertEquals(target, surge, 0.01 * target);
          }
          JsonObject row = new JsonObject();
          row.addProperty("nodes", nodes);
          row.addProperty("courantFraction", fraction);
          row.addProperty("travel_s", duration);
          row.addProperty("surge_Pa", surge);
          row.addProperty("joukowsky_Pa", target);
          row.addProperty("wall_ms", (System.nanoTime() - started) / 1.0e6);
          evidence.add(row);
        }
      }
    }
    Files.write(Paths.get("target", "ss-physical-valve-matrix.json"),
        evidence.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * JSON transport uses the same boundary, synchronized units and conflict rejection.
   *
   * @throws Exception if public example evidence cannot be written
   */
  @Test
  void studyInputAndOutput() throws Exception {
    JsonObject input = JsonParser.parseString(new String(
        Files.readAllBytes(Paths.get("src/test/resources/neqsim/process/pipeline/safety-shutdown-valve/input.json")),
        StandardCharsets.UTF_8)).getAsJsonObject();
    JsonObject result = JsonParser.parseString(WaterHammerStudy.run(input.toString())).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals("NOT_EVALUATED", result.getAsJsonObject("validation").get("physicalConservation").getAsString());
    assertEquals("NOT_EVALUATED",
        result.getAsJsonObject("validation").get("safetyShutdownQualification").getAsString());
    Files.write(Paths.get("target", "ss-physical-valve-input.json"), input.toString().getBytes(StandardCharsets.UTF_8));
    Files.write(Paths.get("target", "ss-physical-valve-output.json"),
        result.toString().getBytes(StandardCharsets.UTF_8));
    JsonObject series = result.getAsJsonObject("timeSeries");
    assertEquals(series.getAsJsonArray("time_s").size(), series.getAsJsonArray("effectiveValveKv").size());
    assertEquals(series.getAsJsonArray("time_s").size(), series.getAsJsonArray("outletFlow_m3_s").size());
    double density = result.getAsJsonObject("inputSummary").get("frozenDensity_kg_m3").getAsDouble();
    double receiver = result.getAsJsonObject("inputSummary").get("valveBackPressure_bara").getAsDouble();
    for (int i = 0; i < series.getAsJsonArray("time_s").size(); i++) {
      double kv = series.getAsJsonArray("effectiveValveKv").get(i).getAsDouble();
      double pressure = series.getAsJsonArray("outletPressure_bara").get(i).getAsDouble();
      double expected = kv / 3600.0 * Math.sqrt((pressure - receiver) / (density / 1000.0));
      assertEquals(expected, series.getAsJsonArray("outletFlow_m3_s").get(i).getAsDouble(), 1.0e-10);
    }
    int last = series.getAsJsonArray("time_s").size() - 1;
    assertEquals(0.151, series.getAsJsonArray("time_s").get(last).getAsDouble(), 1.0e-12);
    assertEquals(0.0, series.getAsJsonArray("effectiveValveKv").get(last).getAsDouble());
    JsonArray events = new JsonArray();
    events.add(new JsonObject());
    input.add("eventSchedule", events);
    result = JsonParser.parseString(WaterHammerStudy.run(input.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
  }
}
