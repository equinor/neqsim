package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent control-volume, standing-wave and passivity oracles for Safety Shutdown acoustics.
 *
 * @author NeqSim
 * @version 1.0
 */
class WaterHammerConservationTest {
  /**
   * Create a public SRK/classic water fixture; its properties are always obtained from the actual EOS.
   *
   * @param nodes physical face count
   * @return initialized physical pipe
   */
  private static WaterHammerPipe pipe(int nodes) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(36000.0, "kg/hr");
    Stream feed = new Stream("public water", fluid);
    feed.run();
    WaterHammerPipe pipe = new WaterHammerPipe("Safety Shutdown conservative acoustics", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfNodes(nodes);
    pipe.setWaveSpeed(1000.0);
    pipe.run(UUID.randomUUID());
    return pipe;
  }

  /**
   * Access a private numerical field only for ideal linear-PDE initial-value oracles, never for production fluids.
   *
   * @param name declared field name
   * @return accessible field
   * @throws Exception if the diagnostic field is unavailable
   */
  private static Field field(String name) throws Exception {
    Field field = WaterHammerPipe.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  /**
   * Seed exact zero-friction uniform-reference PDE data after real-fluid initialization.
   *
   * @param pipe real-fluid fixture
   * @param q initial actual uniform flow in m3/s
   * @param amplitude pressure-mode amplitude in Pa, zero for uniform head
   * @throws Exception if mathematical initial data cannot be seeded
   */
  private static void ideal(WaterHammerPipe pipe, double q, double amplitude) throws Exception {
    double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
    double h0 = 2.0e6 / (rho * 9.81);
    field("frictionFactor").setDouble(pipe, 0.0);
    field("referenceFlow").setDouble(pipe, q);
    field("upstreamHead").setDouble(pipe, h0);
    field("downstreamHead").setDouble(pipe, h0);
    Arrays.fill((double[]) field("referenceHead").get(pipe), h0);
    Arrays.fill((double[]) field("head").get(pipe), h0);
    Arrays.fill((double[]) field("flow").get(pipe), q);
    Arrays.fill((double[]) field("cellFlow").get(pipe), q);
    Arrays.fill((double[]) field("pressureProfile").get(pipe), 2.0e6);
    double[] h = (double[]) field("cellHead").get(pipe);
    double dx = 1000.0 / h.length;
    for (int i = 0; i < h.length; i++) {
      // Integral of cos(k*x) over the whole physical cell, not a midpoint sample.
      double average = (Math.sin(Math.PI * (i + 1) * dx / 1000.0) - Math.sin(Math.PI * i * dx / 1000.0))
          / (Math.PI * dx / 1000.0);
      h[i] = h0 + amplitude * average / (rho * 9.81);
    }
  }

  /**
   * Compute physical linear acoustic energy by summing cell means; numerical mixing is dissipative.
   *
   * @param pipe ideal closed line
   * @return acoustic perturbation energy in J
   */
  private static double energy(WaterHammerPipe pipe) {
    double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
    double[] p = pipe.getCellPressureProfile();
    double[] q = pipe.getCellFlowProfile();
    double sum = 0.0;
    for (int i = 0; i < p.length; i++) {
      sum += Math.PI * 0.01 * (p[i] - 2.0e6) * (p[i] - 2.0e6) / (rho * 1.0e6) + rho * q[i] * q[i] / (Math.PI * 0.01);
    }
    return 0.5 * 1000.0 / p.length * sum;
  }

  /**
   * Closed-end standing waves have an exact independent d'Alembert solution and no boundary work.
   *
   * @throws Exception if ideal initialization or durable evidence fails
   */
  @Test
  void standingWaveRefinementAndPassivity() throws Exception {
    JsonArray rows = new JsonArray();
    double previous = Double.NaN;
    for (int nodes : new int[] {51, 101, 201}) {
      WaterHammerPipe pipe = pipe(nodes);
      ideal(pipe, 0.0, 10000.0);
      pipe.setUpstreamBoundary(WaterHammerPipe.BoundaryType.CLOSED_END);
      pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.CLOSED_END);
      double initialEnergy = energy(pipe);
      double oldEnergy = initialEnergy;
      long started = System.nanoTime();
      while (pipe.getCurrentTime() < 0.37) {
        pipe.runTransient(Math.min(0.5 * pipe.getMaxStableTimeStep(), 0.37 - pipe.getCurrentTime()), UUID.randomUUID());
        double newEnergy = energy(pipe);
        assertTrue(newEnergy <= oldEnergy + 1.0e-10 * initialEnergy, "Passive closed acoustic step");
        oldEnergy = newEnergy;
        assertEquals(0.0, pipe.getAcousticVolumeResidual(), 1.0e-9);
      }
      double[] pressure = pipe.getCellPressureProfile();
      double[] flow = pipe.getCellFlowProfile();
      double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
      double sum = 0.0;
      double dx = 1000.0 / pressure.length;
      double sinc = Math.sin(Math.PI * dx / 2000.0) / (Math.PI * dx / 2000.0);
      for (int i = 0; i < pressure.length; i++) {
        double x = (i + 0.5) * dx;
        double expectedP = 10000.0 * sinc * Math.cos(Math.PI * x / 1000.0) * Math.cos(Math.PI * 0.37);
        double expectedQ = Math.PI * 0.01 * 10000.0 / (rho * 1000.0) * sinc * Math.sin(Math.PI * x / 1000.0)
            * Math.sin(Math.PI * 0.37);
        sum += Math.pow((pressure[i] - 2.0e6 - expectedP) / 10000.0, 2)
            + Math.pow((flow[i] - expectedQ) * rho * 1000.0 / (Math.PI * 0.01 * 10000.0), 2);
      }
      double error = Math.sqrt(sum / pressure.length);
      assertTrue(error <= (nodes == 201 ? 0.01 : 0.03), "Frozen independent wave-error gate: " + error);
      if (Double.isFinite(previous)) {
        assertTrue(previous / error >= 1.7, "First-order characteristic convergence");
      }
      previous = error;
      JsonObject row = new JsonObject();
      row.addProperty("nodes", nodes);
      row.addProperty("relativeRmsWaveError", error);
      row.addProperty("initialEnergy_J", initialEnergy);
      row.addProperty("finalEnergy_J", oldEnergy);
      row.addProperty("volumeResidual_m3", pipe.getAcousticVolumeResidual());
      row.addProperty("runtime_ms", (System.nanoTime() - started) / 1.0e6);
      row.addProperty("standingWave", "PASS");
      row.addProperty("passivity", "PASS");
      rows.add(row);
    }
    Files.write(Paths.get("target", "ss-conservative-standing-wave.json"),
        rows.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Before the reservoir receives the closure wave, exact stored volume is Q0*t, independent of mesh and timestep.
   *
   * @throws Exception if ideal initialization fails
   */
  @Test
  void independentClosureVolumeAndJoukowsky() throws Exception {
    for (int nodes : new int[] {51, 101, 201}) {
      for (double fraction : new double[] {1.0, 0.5, 0.37}) {
        WaterHammerPipe pipe = pipe(nodes);
        double q0 = pipe.getFlowProfile()[0];
        ideal(pipe, q0, 0.0);
        pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.CLOSED_END);
        double dt = fraction * pipe.getMaxStableTimeStep();
        pipe.runTransient(dt, UUID.randomUUID());
        double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
        assertEquals(rho * 1000.0 * q0 / (Math.PI * 0.01), pipe.getPressureProfile()[nodes - 1] - 2.0e6,
            rho * 1000.0 * q0 / (Math.PI * 0.01) * 0.01);
        while (pipe.getCurrentTime() < 0.3) {
          pipe.runTransient(Math.min(dt, 0.3 - pipe.getCurrentTime()), UUID.randomUUID());
        }
        assertEquals(q0 * 0.3, pipe.getAcousticStoredVolume(), 1.0e-9);
        assertEquals(q0 * 0.3, pipe.getIntegratedInletVolume(), 1.0e-9);
        assertEquals(0.0, pipe.getIntegratedOutletVolume(), 0.0);
        assertEquals(0.0, pipe.getAcousticVolumeResidual(), 1.0e-9);
        assertEquals(0.0, pipe.getMomentumResidual(), 1.0e-7);
      }
    }
  }

  /**
   * Signed implicit Darcy resistance must dissipate both flow directions and obey the analytical algebraic limit.
   *
   * @throws Exception if ideal state seeding fails
   */
  @Test
  void signedFrictionLimit() throws Exception {
    for (double sign : new double[] {-1.0, 1.0}) {
      WaterHammerPipe pipe = pipe(101);
      double q = sign * 0.01;
      ideal(pipe, q, 0.0);
      field("referenceFlow").setDouble(pipe, 0.0);
      field("frictionFactor").setDouble(pipe, 0.02);
      pipe.setUpstreamBoundary(WaterHammerPipe.BoundaryType.CONSTANT_FLOW);
      pipe.setDownstreamBoundary(WaterHammerPipe.BoundaryType.CONSTANT_FLOW);
      double dt = 0.005;
      double kdt = dt * 0.02 / (2.0 * 0.2 * Math.PI * 0.01);
      double expected = sign * (Math.sqrt(1.0 + 4.0 * kdt * Math.abs(q)) - 1.0) / (2.0 * kdt);
      pipe.runTransient(dt, UUID.randomUUID());
      for (double actual : pipe.getCellFlowProfile()) {
        assertEquals(expected, actual, 1.0e-12);
        assertTrue(Math.abs(actual) < Math.abs(q));
      }
    }
  }

  /**
   * Rejected intervals do not alter accepted local state; serialization carries the independent inventories.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void rejectedIntervalAndCheckpoint() throws Exception {
    WaterHammerPipe pipe = pipe(101);
    pipe.setValveOpening(0.5);
    pipe.runTransient(0.037, UUID.randomUUID());
    double[] cells = pipe.getCellPressureProfile();
    double[] flows = pipe.getCellFlowProfile();
    double volume = pipe.getIntegratedInletVolume();
    double impulse = pipe.getIntegratedPressureImpulse();
    double time = pipe.getCurrentTime();
    int history = pipe.getTimeHistory().size();
    pipe.setValveBackPressure(100.0);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.005, UUID.randomUUID()));
    assertArrayEquals(cells, pipe.getCellPressureProfile(), 0.0);
    assertArrayEquals(flows, pipe.getCellFlowProfile(), 0.0);
    assertEquals(volume, pipe.getIntegratedInletVolume(), 0.0);
    assertEquals(impulse, pipe.getIntegratedPressureImpulse(), 0.0);
    assertEquals(time, pipe.getCurrentTime(), 0.0);
    assertEquals(history, pipe.getTimeHistory().size());
    pipe.setValveBackPressure(1.0);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(pipe);
    }
    WaterHammerPipe copy;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (WaterHammerPipe) input.readObject();
    }
    pipe.runTransient(0.073, UUID.randomUUID());
    copy.runTransient(0.073, UUID.randomUUID());
    assertArrayEquals(pipe.getCellPressureProfile(), copy.getCellPressureProfile(), 0.0);
    assertArrayEquals(pipe.getCellFlowProfile(), copy.getCellFlowProfile(), 0.0);
    assertEquals(pipe.getAcousticVolumeResidual(), copy.getAcousticVolumeResidual(), 0.0);
    assertEquals(pipe.getMomentumResidual(), copy.getMomentumResidual(), 0.0);
    assertEquals(pipe.getIntegratedInletVolume(), copy.getIntegratedInletVolume(), 0.0);
    field("cellHead").set(copy, null);
    assertThrows(IllegalStateException.class, () -> copy.runTransient(0.005, UUID.randomUUID()));
  }

  /** Cell means are defensively copied and correspond to actual physical cell centres. */
  @Test
  void cellGeometryAndOwnership() {
    WaterHammerPipe pipe = pipe(101);
    assertEquals(100, pipe.getCellPressureProfile().length);
    assertEquals(100, pipe.getCellFlowProfile().length);
    assertEquals(5.0, pipe.getCellPositions()[0], 0.0);
    assertEquals(995.0, pipe.getCellPositions()[99], 0.0);
    double original = pipe.getCellPressureProfile()[0];
    pipe.getCellPressureProfile()[0] = 0.0;
    pipe.getCellFlowProfile()[0] = 0.0;
    assertEquals(original, pipe.getCellPressureProfile()[0], 0.0);
    assertTrue(pipe.getCellFlowProfile()[0] > 0.0);
  }

  /**
   * Variable sub-Courant requests, physical travel/reopening and nearby rates preserve both physical ledgers.
   *
   * @throws Exception if evidence output fails
   */
  @Test
  void variableStepTravelAndNearbyStates() throws Exception {
    JsonArray rows = new JsonArray();
    for (int nodes : new int[] {51, 101, 201}) {
      for (double rate : new double[] {18000.0, 36000.0, 54000.0}) {
        WaterHammerPipe pipe = pipe(nodes);
        pipe.getInletStream().setFlowRate(rate, "kg/hr");
        pipe.getInletStream().run();
        pipe.setElevationChange(12.0);
        pipe.run(UUID.randomUUID());
        double q = pipe.getFlowProfile()[nodes - 1];
        double rho = pipe.getInletStream().getThermoSystem().getDensity("kg/m3");
        double deltaP = pipe.getPressureProfile()[nodes - 1] - 1.0e5;
        double kv = 3600.0 * q * Math.sqrt(rho / 1000.0 * 1.0e5 / deltaP);
        pipe.setValveBoundary(new AcousticValveBoundary(new double[] {0.0, 0.5, 1.0}, new double[] {0.0, 0.2 * kv, kv},
            AcousticValveBoundary.CapacityUnit.KV, new double[] {0.0, 0.04, 0.1, 0.3, 0.7},
            new double[] {1.0, 0.5, 0.0, 0.0, 1.0}, 0.013, 0.01));
        pipe.run(UUID.randomUUID());
        double[] initial = pipe.getCellPressureProfile();
        double maxVolume = 0.0;
        double maxMomentum = 0.0;
        long start = System.nanoTime();
        int step = 0;
        double[] fractions = {0.37, 1.0, 0.19, 0.73};
        while (pipe.getCurrentTime() < 2.3) {
          double dt = fractions[step++ % fractions.length] * pipe.getMaxStableTimeStep();
          pipe.runTransient(Math.min(dt, 2.3 - pipe.getCurrentTime()), UUID.randomUUID());
          double sum = 0.0;
          double[] cells = pipe.getCellPressureProfile();
          for (int i = 0; i < cells.length; i++) {
            sum += (cells[i] - initial[i]) * 1000.0 / cells.length;
          }
          double stored = Math.PI * 0.01 / (rho * 1.0e6) * sum;
          double exchanged = pipe.getIntegratedInletVolume() - pipe.getIntegratedOutletVolume();
          maxVolume = Math.max(maxVolume, Math.abs(stored - exchanged));
          maxMomentum = Math.max(maxMomentum, Math.abs(pipe.getMomentumResidual()));
          assertEquals(exchanged, stored, 1.0e-9);
          assertEquals(0.0, pipe.getMomentumResidual(), 1.0e-7);
        }
        assertEquals(2.3, pipe.getCurrentTime(), 0.0);
        assertEquals(kv, pipe.getEffectiveValveKv(), kv * 1.0e-12);
        JsonObject row = new JsonObject();
        row.addProperty("nodes", nodes);
        row.addProperty("massRate_kg_hr", rate);
        row.addProperty("elevation_m", 12.0);
        row.addProperty("maxVolumeResidual_m3", maxVolume);
        row.addProperty("maxMomentumResidual_kg_m_s", maxMomentum);
        row.addProperty("runtime_ms", (System.nanoTime() - start) / 1.0e6);
        row.addProperty("acousticVolumeBalance", "PASS");
        row.addProperty("momentumBalance", "PASS");
        rows.add(row);
      }
    }
    Files.write(Paths.get("target", "ss-conservative-nearby.json"), rows.toString().getBytes(StandardCharsets.UTF_8));
  }
}
