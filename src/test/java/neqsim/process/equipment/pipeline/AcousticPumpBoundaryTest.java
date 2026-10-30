package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.operations.WaterHammerStudy;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent forward-curve, acoustic wave, conservation and local-restart oracles for Safety Shutdown pumps.
 *
 * @author NeqSim
 * @version 1.0
 */
class AcousticPumpBoundaryTest {
  /** Standard gravity, m/s2. */
  private static final double G = 9.81;
  /** Frozen physical flow at the reference curve duty, m3/s. */
  private static final double Q0 = 0.01;
  /** Acoustic head/flow impedance for the public 0.2m/1000m/s line, s/m2. */
  private static final double B = 1000.0 / (G * Math.PI * 0.01);

  /**
   * Create the declared synthetic reference curve, not vendor data.
   *
   * @param times absolute actual-speed sample times, s
   * @param speeds actual speeds, rpm
   * @param suction absolute suction pressure, bara
   * @return detached bounded boundary
   */
  private static AcousticPumpBoundary boundary(double[] times, double[] speeds, double suction) {
    return new AcousticPumpBoundary(600.0, new double[] {0.0, 36.0, 72.0}, new double[] {60.0, 50.0, 20.0},
        new double[] {70.0, 75.0, 70.0}, "meter", 6.0, 600.0, times, speeds, suction,
        "Public synthetic quadratic; prescribed actual speed; constant impeller and matching velocity/elevation bases");
  }

  /**
   * Independent rationalized positive root of 100000*Q^2+B*Q+Cm-Hs-60*(n/600)^2=0.
   *
   * @param difference Cm-Hs, m
   * @param speed actual speed, rpm
   * @return positive forward flow, m3/s
   */
  private static double root(double difference, double speed) {
    double c = difference - 60.0 * Math.pow(speed / 600.0, 2.0);
    return -2.0 * c / (B + Math.sqrt(B * B - 4.0e5 * c));
  }

  /**
   * Create a real SRK/classic liquid fixture at the independent curve duty; use actual EOS density.
   *
   * @param nodes number of physical faces
   * @param times speed table times, s
   * @param speeds actual speed table, rpm
   * @return initialized pipe with forward pump inlet and fixed outlet reservoir
   */
  private static WaterHammerPipe pipe(int nodes, double[] times, double[] speeds) {
    return pipe(nodes, times, speeds, Q0);
  }

  /**
   * Create a nearby real-liquid operating point without calibrating the curve.
   *
   * @param nodes physical faces
   * @param times actual-speed times, s
   * @param speeds actual speeds, rpm
   * @param duty actual initial flow, m3/s
   * @return initialized pipe
   */
  private static WaterHammerPipe pipe(int nodes, double[] times, double[] speeds, double duty) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("public synthetic pump discharge", fluid);
    feed.run();
    double rho = feed.getThermoSystem().getDensity("kg/m3");
    feed.setFlowRate(rho * duty, "kg/sec");
    feed.run();
    WaterHammerPipe pipe = new WaterHammerPipe("Safety Shutdown forward pump coastdown", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setWaveSpeed(1000.0);
    pipe.setNumberOfNodes(nodes);
    pipe.setUpstreamBoundary(WaterHammerPipe.BoundaryType.PUMP);
    pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.RESERVOIR);
    pipe.setPumpBoundary(boundary(times, speeds, 20.0 - rho * G * (60 - 100000 * duty * duty) / 1.0e5));
    pipe.run(UUID.randomUUID());
    return pipe;
  }

  /**
   * Set an ideal frictionless uniform-reference linear acoustic oracle after actual-fluid initialization.
   *
   * @param pipe actual-fluid initialized pipe
   * @throws Exception if the internal mathematical initial data are unavailable
   */
  private static void ideal(WaterHammerPipe pipe) throws Exception {
    double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
    double h = 2.0e6 / (rho * G);
    for (String name : new String[] {"upstreamHead", "downstreamHead"}) {
      field(name).setDouble(pipe, h);
    }
    field("frictionFactor").setDouble(pipe, 0.0);
    for (String name : new String[] {"referenceHead", "head", "cellHead"}) {
      Arrays.fill((double[]) field(name).get(pipe), h);
    }
    Arrays.fill((double[]) field("pressureProfile").get(pipe), 2.0e6);
  }

  /**
   * Get a mathematical diagnostic field; production input never bypasses fluid/geometry guards.
   *
   * @param name declared field name
   * @return accessible field
   * @throws Exception if absent
   */
  private static Field field(String name) throws Exception {
    Field f = WaterHammerPipe.class.getDeclaredField(name);
    f.setAccessible(true);
    return f;
  }

  /**
   * Roundtrip using Java serialization, exercising the privately owned real PumpChart fit.
   *
   * @param value serializable boundary or pipe
   * @return detached restored value
   * @throws Exception if serialization fails
   */
  private static Object restore(Object value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return input.readObject();
    }
  }

  /**
   * Retain deterministic user-facing benchmark data; runtime is reported separately from deterministic physics.
   *
   * @param name result filename
   * @param result executed JSON result
   * @throws Exception if evidence cannot be written
   */
  private static void evidence(String name, JsonObject result) throws Exception {
    Path directory = Paths.get("target", "safety-shutdown-pump");
    Files.createDirectories(directory);
    Files.write(directory.resolve(name),
        new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
  }

  /** Independent roots, nearby speeds, unit conversion and original curve-domain rejection. */
  @Test
  void independentRootsAndUnits() {
    double difference = 50.0 - B * Q0;
    double suction = 5.0e5 / (1000.0 * G);
    for (double speed : new double[] {600, 550, 500, 450, 400, 350, 325}) {
      AcousticPumpBoundary pump = boundary(new double[] {0, 2}, new double[] {speed, speed}, 5.0);
      double q = pump.solveFlow(suction + difference, B, 1000.0, 1.0);
      assertEquals(root(difference, speed), q, 1.0e-12);
      assertEquals(difference + B * q, pump.getHead(q, 1.0), 1.0e-9);
      AcousticPumpBoundary energyUnit = new AcousticPumpBoundary(600, new double[] {0, 36, 72},
          new double[] {60 * G / 1000, 50 * G / 1000, 20 * G / 1000}, new double[] {70, 75, 70}, "kJ/kg", 6, 600,
          new double[] {0, 2}, new double[] {speed, speed}, 5, "Public synthetic unit oracle");
      assertEquals(q, energyUnit.solveFlow(suction + difference, B, 1000, 1), 1.0e-12);
      assertThrows(IllegalStateException.class, () -> pump.getHead(-1.0e-6, 1));
      assertThrows(IllegalStateException.class, () -> pump.getHead(0.121 * speed / 3600, 1));
    }
    for (double speed : new double[] {320, 300, 60, 6}) {
      AcousticPumpBoundary pump = boundary(new double[] {0, 2}, new double[] {speed, speed}, 5.0);
      assertThrows(IllegalStateException.class, () -> pump.solveFlow(suction + difference, B, 1000, 1));
    }
  }

  /** Invalid input tables and a decreasing data set whose quadratic fit turns upward must reject. */
  @Test
  void guardsRejectUnsupportedStates() {
    for (double speed : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 601}) {
      assertThrows(IllegalArgumentException.class, () -> boundary(new double[] {0, 1}, new double[] {600, speed}, 5));
    }
    assertThrows(IllegalArgumentException.class, () -> boundary(new double[] {0, 0}, new double[] {600, 600}, 5));
    assertThrows(IllegalArgumentException.class, () -> boundary(new double[] {0, 1}, new double[] {600, 600}, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new AcousticPumpBoundary(600, new double[] {0, 36, 72}, new double[] {60, 59, 20},
            new double[] {70, 75, 70}, "meter", 6, 600, new double[] {0, 1}, new double[] {600, 600}, 5, "synthetic"));
    AcousticPumpBoundary pump = boundary(new double[] {0, 1}, new double[] {600, 550}, 5);
    assertThrows(IllegalArgumentException.class, () -> pump.getSpeed(-1));
    assertThrows(IllegalArgumentException.class, () -> pump.solveFlow(Double.NaN, B, 1000, 0));
    assertThrows(IllegalArgumentException.class, () -> pump.solveFlow(100, 0, 1000, 0));
  }

  /** Exact knots, endpoint holds, input isolation and serializable fit state. */
  @Test
  void exactKnotsIsolationAndSerialization() throws Exception {
    double[] times = {0, 0.1 + 0.2, 0.7};
    double[] speeds = {600, 580, 550};
    AcousticPumpBoundary pump = boundary(times, speeds, 5);
    times[1] = 0.0;
    speeds[1] = 0.0;
    assertEquals(580, pump.getSpeed(0.1 + 0.2), 0.0);
    assertEquals(0.1 + 0.2, pump.nextKnotAfter(0), 0.0);
    assertEquals(0.7, pump.nextKnotAfter(0.1 + 0.2), 0.0);
    assertEquals(550, pump.getSpeed(100), 0.0);
    AcousticPumpBoundary copy = (AcousticPumpBoundary) restore(pump);
    assertEquals(pump.getHead(Q0, 0.2), copy.getHead(Q0, 0.2), 0.0);
    assertEquals(pump.getProvenance(), copy.getProvenance());
  }

  /** Constant actual speed must preserve the initialized real-liquid inclined/frictional equilibrium. */
  @Test
  void unchangedPumpEquilibrium() {
    for (int nodes : new int[] {51, 101, 201}) {
      WaterHammerPipe pipe = pipe(nodes, new double[] {0, 2}, new double[] {600, 600});
      double[] initial = pipe.getPressureProfile();
      pipe.runTransient(0.371, UUID.randomUUID());
      assertArrayEquals(initial, pipe.getPressureProfile(), 1.0e-4);
      assertEquals(0, pipe.getAcousticVolumeResidual(), 1.0e-9);
      assertEquals(0, pipe.getMomentumResidual(), 1.0e-7);
      assertTrue(pipe.getPumpHydraulicWork() > 0);
    }
  }

  /** Single unsupported interval must preserve accepted physical state, histories and every diagnostic ledger. */
  @Test
  void rejectedIntervalAndConfiguration() {
    WaterHammerPipe pipe = pipe(101, new double[] {0, 0.001}, new double[] {600, 300});
    double[] p = pipe.getCellPressureProfile();
    double[] q = pipe.getCellFlowProfile();
    int samples = pipe.getTimeHistory().size();
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.001, UUID.randomUUID()));
    assertEquals(0.0, pipe.getCurrentTime(), 0.0);
    assertArrayEquals(p, pipe.getCellPressureProfile(), 0.0);
    assertArrayEquals(q, pipe.getCellFlowProfile(), 0.0);
    assertEquals(samples, pipe.getTimeHistory().size());
    assertEquals(0.0, pipe.getPumpHydraulicWork(), 0.0);
    assertEquals(0.0, pipe.getIntegratedInletVolume(), 0.0);
    assertEquals(0.0, pipe.getIntegratedOutletVolume(), 0.0);
    pipe.setUpstreamBoundary(WaterHammerPipe.BoundaryType.RESERVOIR);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.001, UUID.randomUUID()));
    pipe.setUpstreamBoundary(WaterHammerPipe.BoundaryType.PUMP);
    pipe.setPumpBoundary(boundary(new double[] {0, 1}, new double[] {600, 600}, 5));
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.001, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> pipe.run(UUID.randomUUID()));
  }

  /**
   * Existing substeps must localize both speed and valve knots, retain remainder and deterministic restart.
   *
   * @throws Exception if checkpoint serialization fails
   */
  @Test
  void restartAndExactRemainder() throws Exception {
    WaterHammerPipe pipe = pipe(101, new double[] {0, 0.1 + 0.2, 0.347}, new double[] {600, 590, 585});
    pipe.runTransient(0.123, UUID.randomUUID());
    WaterHammerPipe copy = (WaterHammerPipe) restore(pipe);
    pipe.runTransient(0.234, UUID.randomUUID());
    copy.runTransient(0.234, UUID.randomUUID());
    assertEquals(0.357, pipe.getCurrentTime(), 1.0e-15);
    assertTrue(pipe.getTimeHistory().contains(0.1 + 0.2));
    assertTrue(pipe.getTimeHistory().contains(0.347));
    assertArrayEquals(pipe.getCellPressureProfile(), copy.getCellPressureProfile(), 0.0);
    assertArrayEquals(pipe.getCellFlowProfile(), copy.getCellFlowProfile(), 0.0);
    assertEquals(pipe.getPumpHydraulicWork(), copy.getPumpHydraulicWork(), 0.0);
    assertEquals(pipe.getAcousticVolumeResidual(), copy.getAcousticVolumeResidual(), 0.0);
  }

  /**
   * Pre-reflection characteristic, volume, momentum, hydraulic work and smooth retarded-wave mesh/time oracles.
   *
   * @throws Exception if ideal data or evidence cannot be written
   */
  @Test
  void forwardCoastdownRefinementAndWork() throws Exception {
    JsonObject result = new JsonObject();
    JsonArray rows = new JsonArray();
    double previous = Double.NaN;
    for (int nodes : new int[] {51, 101, 201}) {
      double[] times = new double[61];
      double[] speeds = new double[61];
      for (int i = 0; i < times.length; i++) {
        times[i] = i / 100.0;
        speeds[i] = 600 - 50 * times[i] * times[i];
      }
      WaterHammerPipe pipe = pipe(nodes, times, speeds);
      ideal(pipe);
      double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
      double difference = 50 - B * Q0;
      double work = 0;
      long start = System.nanoTime();
      while (pipe.getCurrentTime() < 0.4) {
        double dt = Math.min(0.5 * pipe.getMaxStableTimeStep(), 0.4 - pipe.getCurrentTime());
        dt = Math.min(dt, pipe.getPumpBoundary().nextKnotAfter(pipe.getCurrentTime()) - pipe.getCurrentTime());
        double end = pipe.getCurrentTime() + dt;
        double speed = smoothSpeed(end);
        double expectedQ = root(difference, speed);
        double pumpHead = 60 * Math.pow(speed / 600, 2) - 100000 * expectedQ * expectedQ;
        work += dt * rho * G * expectedQ * pumpHead;
        pipe.runTransient(dt, UUID.randomUUID());
        assertEquals(expectedQ, pipe.getFlowProfile()[0], 1.0e-12);
        assertEquals(0, pipe.getAcousticVolumeResidual(), 1.0e-9);
        assertEquals(0, pipe.getMomentumResidual(), 1.0e-7);
      }
      assertEquals(work, pipe.getPumpHydraulicWork(), Math.max(1.0e-6, Math.abs(work) * 1.0e-10));
      double[] p = pipe.getCellPressureProfile();
      double sum = 0;
      double dx = 1000.0 / p.length;
      // Independent quadrature of retarded characteristic over each complete cell; midpoint subquadrature is
      // independent of the production flux update and sufficiently fine for the frozen convergence gate.
      for (int i = 0; i < p.length; i++) {
        double expected = 0;
        for (int k = 0; k < 100; k++) {
          double retarded = 0.4 - (i + (k + 0.5) / 100) * dx / 1000;
          double q = retarded > 0 ? root(difference, smoothSpeed(retarded)) : Q0;
          expected += 2.0e6 + rho * G * B * (q - Q0);
        }
        double error = (p[i] - expected / 100) / (rho * G * B * Q0);
        sum += error * error;
      }
      double rms = Math.sqrt(sum / p.length);
      if (Double.isFinite(previous)) {
        assertTrue(previous / rms >= 1.7, "Frozen smooth-wave refinement ratio: " + previous / rms);
      }
      previous = rms;
      JsonObject row = new JsonObject();
      row.addProperty("faces", nodes);
      row.addProperty("normalizedCellPressureRms", rms);
      row.addProperty("volumeResidual_m3", pipe.getAcousticVolumeResidual());
      row.addProperty("momentumResidual_kg_m_s", pipe.getMomentumResidual());
      row.addProperty("hydraulicWork_J", pipe.getPumpHydraulicWork());
      row.addProperty("independentWork_J", work);
      row.addProperty("elapsed_ms", (System.nanoTime() - start) * 1.0e-6);
      rows.add(row);
    }
    result.addProperty("benchmark", "Public synthetic forward pump coastdown, ideal single-phase acoustic limit");
    result.addProperty("status", "PASS");
    result.addProperty("fullTripAndSlam", "NOT_EVALUATED");
    result.add("refinement", rows);
    evidence("analytical-refinement.json", result);
  }

  /**
   * Independently interpolate the declared 0.01s samples of n=600-50*t^2; no production interpolator is called.
   *
   * @param time physical time in the benchmark, s
   * @return prescribed actual speed, rpm
   */
  private static double smoothSpeed(double time) {
    int lower = (int) Math.floor(time * 100.0);
    double left = lower / 100.0;
    double right = (lower + 1) / 100.0;
    return 600 - 50 * (left * left + (time - left) / (right - left) * (right * right - left * left));
  }

  /**
   * Pump and valve knot unions must share conservative stepping, including a partial final interval.
   */
  @Test
  void coupledTravelAndSpeedKnotUnion() {
    WaterHammerPipe pipe = pipe(101, new double[] {0, 0.15, 0.37}, new double[] {600, 595, 590});
    double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
    double[] pressure = pipe.getPressureProfile();
    double dropHead = (pressure[pressure.length - 1] - 1.0e5) / (rho * G);
    double kv = Q0 * 3600 * Math.sqrt(100.0 / (G * dropHead));
    pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.VALVE);
    pipe.setValveBoundary(
        new AcousticValveBoundary(new double[] {0, 1}, new double[] {0, kv}, AcousticValveBoundary.CapacityUnit.KV,
            new double[] {0, 0.07, 0.23, 0.37}, new double[] {1, 0.98, 0.98, 1}, 0, 0));
    pipe.run(UUID.randomUUID());
    pipe.runTransient(0.397, UUID.randomUUID());
    for (double knot : new double[] {0.07, 0.15, 0.23, 0.37}) {
      assertTrue(pipe.getTimeHistory().contains(knot));
    }
    assertEquals(0.397, pipe.getCurrentTime(), 0.0);
    assertEquals(0, pipe.getAcousticVolumeResidual(), 1.0e-9);
    assertEquals(0, pipe.getMomentumResidual(), 1.0e-7);
  }

  /**
   * Nearby flow and time-step cases retain accepted volume/momentum balances without tuning the pump curve.
   *
   * @throws Exception if evidence cannot be written
   */
  @Test
  void nearbyOperatingPointsAndTimesteps() throws Exception {
    JsonObject result = new JsonObject();
    JsonArray rows = new JsonArray();
    for (double duty : new double[] {0.0075, 0.01, 0.0125}) {
      for (double dt : new double[] {0.001, 0.0037, 0.009}) {
        WaterHammerPipe pipe = pipe(101, new double[] {0, 0.2, 0.4}, new double[] {600, 595, 590}, duty);
        long start = System.nanoTime();
        while (pipe.getCurrentTime() < 0.4) {
          pipe.runTransient(Math.min(dt, 0.4 - pipe.getCurrentTime()), UUID.randomUUID());
          assertEquals(0, pipe.getAcousticVolumeResidual(), 1.0e-9);
          assertEquals(0, pipe.getMomentumResidual(), 1.0e-7);
        }
        assertTrue(pipe.getPumpHydraulicWork() > 0);
        JsonObject row = new JsonObject();
        row.addProperty("initialFlow_m3_s", duty);
        row.addProperty("requestedStep_s", dt);
        row.addProperty("volumeResidual_m3", pipe.getAcousticVolumeResidual());
        row.addProperty("momentumResidual_kg_m_s", pipe.getMomentumResidual());
        row.addProperty("work_J", pipe.getPumpHydraulicWork());
        row.addProperty("elapsed_ms", (System.nanoTime() - start) * 1.0e-6);
        rows.add(row);
      }
    }
    result.addProperty("status", "PASS");
    result.add("cases", rows);
    evidence("nearby-cases.json", result);
  }

  /** Physical pump capacity cannot be attached to a gas inlet or an unsupported boundary orientation. */
  @Test
  void phaseAndBoundaryOrientationGuards() {
    SystemSrkEos gas = new SystemSrkEos(298.15, 20);
    gas.addComponent("methane", 1);
    gas.setMixingRule("classic");
    Stream feed = new Stream("unsupported gas pump", gas);
    feed.run();
    WaterHammerPipe gasPipe = new WaterHammerPipe("Safety Shutdown unsupported gas pump", feed);
    gasPipe.setUpstreamBoundary(WaterHammerPipe.BoundaryType.PUMP);
    gasPipe.setPumpBoundary(boundary(new double[] {0, 1}, new double[] {600, 600}, 5));
    assertThrows(IllegalArgumentException.class, () -> gasPipe.run(UUID.randomUUID()));
    WaterHammerPipe pipe = pipe(101, new double[] {0, 1}, new double[] {600, 600});
    pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.PUMP);
    assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(0.001, UUID.randomUUID()));
    pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.RESERVOIR);
    pipe.setPumpBoundary(null);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.001, UUID.randomUUID()));
  }

  /**
   * Fixed-grid temporal refinement of endpoint-held hydraulic work against independent continuous-time integration.
   *
   * @throws Exception if ideal initialization or evidence fails
   */
  @Test
  void timeRefinementOfHydraulicWork() throws Exception {
    JsonObject result = new JsonObject();
    JsonArray rows = new JsonArray();
    double previous = Double.NaN;
    for (double courant : new double[] {0.9, 0.45, 0.225}) {
      WaterHammerPipe pipe = pipe(101, new double[] {0, 0.6}, new double[] {600, 570});
      ideal(pipe);
      pipe.setCourantNumber(courant);
      double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
      double integral = 0;
      int intervals = 10000;
      for (int i = 0; i <= intervals; i++) {
        double t = 0.4 * i / intervals;
        double q = root(50 - B * Q0, 600 - 50 * t);
        double h = 60 * Math.pow((600 - 50 * t) / 600, 2) - 100000 * q * q;
        integral += (i == 0 || i == intervals ? 1 : i % 2 == 0 ? 2 : 4) * rho * G * q * h;
      }
      integral *= 0.4 / intervals / 3;
      pipe.runTransient(0.4, UUID.randomUUID());
      double error = Math.abs(pipe.getPumpHydraulicWork() - integral);
      if (Double.isFinite(previous)) {
        assertTrue(previous / error >= 1.7, "Frozen time-only work refinement: " + previous / error);
      }
      previous = error;
      JsonObject row = new JsonObject();
      row.addProperty("courant", courant);
      row.addProperty("continuousWork_J", integral);
      row.addProperty("endpointHeldWork_J", pipe.getPumpHydraulicWork());
      row.addProperty("absoluteError_J", error);
      rows.add(row);
    }
    result.addProperty("status", "PASS");
    result.add("timeRefinement", rows);
    evidence("time-refinement.json", result);
  }

  /**
   * Retain the initial failed smooth-wave criterion for a nonsmooth linear-start front; never silently relabel it.
   *
   * @throws Exception if ideal initialization or evidence fails
   */
  @Test
  void retainNonsmoothFrontDiagnostic() throws Exception {
    JsonObject result = new JsonObject();
    JsonArray rows = new JsonArray();
    double previous = Double.NaN;
    boolean passesSmoothGate = true;
    for (int nodes : new int[] {51, 101, 201}) {
      WaterHammerPipe pipe = pipe(nodes, new double[] {0, 0.6}, new double[] {600, 570});
      ideal(pipe);
      double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
      while (pipe.getCurrentTime() < 0.4) {
        pipe.runTransient(Math.min(0.5 * pipe.getMaxStableTimeStep(), 0.4 - pipe.getCurrentTime()), UUID.randomUUID());
      }
      double[] p = pipe.getCellPressureProfile();
      double dx = 1000.0 / p.length;
      double sum = 0;
      for (int i = 0; i < p.length; i++) {
        double expected = 0;
        for (int k = 0; k < 100; k++) {
          double retarded = 0.4 - (i + (k + 0.5) / 100) * dx / 1000;
          double q = retarded > 0 ? root(50 - B * Q0, 600 - 50 * retarded) : Q0;
          expected += 2.0e6 + rho * G * B * (q - Q0);
        }
        double error = (p[i] - expected / 100) / (rho * G * B * Q0);
        sum += error * error;
      }
      double rms = Math.sqrt(sum / p.length);
      JsonObject row = new JsonObject();
      row.addProperty("faces", nodes);
      row.addProperty("normalizedCellPressureRms", rms);
      if (Double.isFinite(previous)) {
        double ratio = previous / rms;
        row.addProperty("refinementRatio", ratio);
        row.addProperty("smoothGate", ratio >= 1.7 ? "PASS" : "FAIL");
        passesSmoothGate &= ratio >= 1.7;
      }
      previous = rms;
      rows.add(row);
      assertEquals(0, pipe.getAcousticVolumeResidual(), 1.0e-9);
      assertEquals(0, pipe.getMomentumResidual(), 1.0e-7);
    }
    result.addProperty("smoothGateTarget", 1.7);
    result.addProperty("smoothGateStatus", passesSmoothGate ? "PASS" : "FAIL");
    result.addProperty("interpretation",
        "Abrupt linear speed start creates a nonsmooth front; retain failed smooth criterion");
    result.add("cases", rows);
    evidence("nonsmooth-front-diagnostic.json", result);
  }

  /**
   * Execute the public real-fluid study JSON and retain aligned outputs and applicability flags.
   *
   * @throws Exception if public evidence cannot be written
   */
  @Test
  void publicStudyExample() throws Exception {
    WaterHammerPipe reference = pipe(101, new double[] {0, 1}, new double[] {600, 590});
    JsonObject input = JsonParser.parseString("{\"studyName\":\"Safety Shutdown forward pump coastdown\","
        + "\"temperature_C\":25,\"pressure_bara\":20,\"flowRate\":{\"value\":0,\"unit\":\"kg/sec\"},"
        + "\"pipe\":{\"length_m\":1000,\"diameter_m\":0.2,\"numberOfNodes\":101,\"waveSpeed_m_s\":1000},"
        + "\"upstreamBoundary\":\"PUMP\",\"downstreamBoundary\":\"RESERVOIR\",\"eventSchedule\":[],"
        + "\"simulationTime_s\":0.4,\"timeStep_s\":0.0037,\"pumpBoundary\":{\"referenceSpeed_rpm\":600,"
        + "\"flow_m3_hr\":[0,36,72],\"head\":[60,50,20],\"efficiency_percent\":[70,75,70],\"headUnit\":\"meter\","
        + "\"minimumSpeed_rpm\":6,\"maximumSpeed_rpm\":600,\"time_s\":[0,0.4],\"actualSpeed_rpm\":[600,580],"
        + "\"suctionPressure_bara\":0,\"provenance\":\"Public synthetic forward-only curve and actual-speed table\"}}")
        .getAsJsonObject();
    input.getAsJsonObject("flowRate").addProperty("value", reference.getInletStream().getFlowRate("kg/sec"));
    input.getAsJsonObject("pumpBoundary").addProperty("suctionPressure_bara",
        reference.getPumpBoundary().getSuctionPressure() / 1.0e5);
    JsonObject output = JsonParser.parseString(WaterHammerStudy.run(input.toString())).getAsJsonObject();
    assertEquals("success", output.get("status").getAsString(), output.toString());
    assertEquals("NOT_EVALUATED",
        output.getAsJsonObject("validation").get("safetyShutdownQualification").getAsString());
    JsonObject series = output.getAsJsonObject("timeSeries");
    int n = series.getAsJsonArray("time_s").size();
    for (String name : new String[] {"actualPumpSpeed_rpm", "inletFlow_m3_s", "inletPressure_bara",
        "pumpHydraulicWork_J"}) {
      assertEquals(n, series.getAsJsonArray(name).size());
    }
    assertEquals(580, series.getAsJsonArray("actualPumpSpeed_rpm").get(n - 1).getAsDouble(), 0.0);
    evidence("public-input.json", input);
    evidence("public-output.json", output);
    JsonObject failedInput = input.deepCopy();
    JsonObject pump = failedInput.getAsJsonObject("pumpBoundary");
    pump.add("time_s", JsonParser.parseString("[0,0.015]"));
    pump.add("actualSpeed_rpm", JsonParser.parseString("[600,300]"));
    failedInput.addProperty("timeStep_s", 0.009);
    JsonObject rejected = JsonParser.parseString(WaterHammerStudy.run(failedInput.toString())).getAsJsonObject();
    assertEquals("error", rejected.get("status").getAsString());
    assertTrue(rejected.get("lastAcceptedTime_s").getAsDouble() > 0);
    assertTrue(rejected.get("lastAcceptedTime_s").getAsDouble() < rejected.get("requestedEnd_s").getAsDouble());
    assertTrue(rejected.get("message").getAsString().contains("not accepted"));
    assertTrue(rejected.getAsJsonArray("acceptedInternalTime_s").size() > 0);
    assertEquals("FAIL", rejected.getAsJsonObject("validation").get("requestedInterval").getAsString());
    evidence("domain-transition-input.json", failedInput);
    evidence("domain-transition-rejected.json", rejected);
  }
}
