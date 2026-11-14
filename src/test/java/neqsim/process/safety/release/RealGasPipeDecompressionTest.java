package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** EOS evolution, conservation, refinement and process integration for transient line packing. */
class RealGasPipeDecompressionTest extends neqsim.NeqSimTest {

  private static SystemInterface gas(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressureBar);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("nitrogen", 0.04);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static RealGasPipeDecompression pipe(int cells) {
    return new RealGasPipeDecompression("real-linepack", gas(80.0), 40.0, 0.15, 1.0, 101325.0, 0.01, cells, 0.35);
  }

  private static RealGasPipeDecompression vesselPipe(double vesselVolumeM3, int cells, double cfl) {
    return new RealGasPipeDecompression("real-vessel-linepack", gas(80.0), 40.0, 0.15, 1.0, 101325.0, 0.01,
        vesselVolumeM3, cells, cfl);
  }

  @Test
  void eosStateEvolutionConservesMassEnergyAndComposition() {
    RealGasPipeDecompression pipe = pipe(12);
    double[] composition = pipe.getComponentMassFractions();
    double initialExitTemperature = pipe.getTemperatureProfileK()[11];
    pipe.runTransient(0.02, UUID.randomUUID());
    assertTrue(pipe.getReleasedMassKg() > 0.0);
    assertEquals(pipe.getInitialMassKg(), pipe.getRemainingMassKg() + pipe.getReleasedMassKg(),
        pipe.getInitialMassKg() * 3.0e-10);
    assertEquals(pipe.getInitialEnergyJ(), pipe.getRemainingEnergyJ() + pipe.getReleasedEnergyJ(),
        Math.abs(pipe.getInitialEnergyJ()) * 3.0e-10);
    assertArrayEquals(composition, pipe.getComponentMassFractions(), 0.0);
    assertNotEquals(initialExitTemperature, pipe.getTemperatureProfileK()[11], 1.0e-6);
    assertEquals("real-gas-pipe-decompression", pipe.getReleaseResult().getModelId());
    assertTrue(pipe.getReleaseResult().getMassFlowRateKgS() > 0.0);
  }

  @Test
  void diluteLimitTracksPerfectGasFiniteVolumeBoundary() {
    SystemInterface dilute = gas(1.3);
    RealGasPipeDecompression real = new RealGasPipeDecompression("real", dilute, 20.0, 0.1, 1.0, 101325.0, 0.0, 12,
        0.35);
    IdealGasPipeDecompression ideal = new IdealGasPipeDecompression("ideal", dilute, 20.0, 0.1, 1.0, 101325.0, 0.0, 12,
        0.35);
    real.runTransient(0.01, UUID.randomUUID());
    ideal.runTransient(0.01, UUID.randomUUID());
    double relativeMassDifference = Math.abs(real.getReleasedMassKg() - ideal.getReleasedMassKg())
        / ideal.getReleasedMassKg();
    assertTrue(relativeMassDifference < 0.04, "dilute mass difference=" + relativeMassDifference);
  }

  @Test
  void spatialRefinementBoundsDischargeAndRetainsFiniteWaveFront() throws Exception {
    double[] released = new double[3];
    double[] closedPressure = new double[3];
    int[] cells = new int[] {8, 12, 16};
    for (int i = 0; i < cells.length; i++) {
      RealGasPipeDecompression pipe = pipe(cells[i]);
      pipe.runTransient(0.02, UUID.randomUUID());
      released[i] = pipe.getReleasedMassKg();
      closedPressure[i] = pipe.getPressureProfilePa()[0];
    }
    StringBuilder receipt = new StringBuilder("cells,released_mass_kg,closed_end_pressure_Pa\n");
    for (int i = 0; i < cells.length; i++) {
      receipt.append(cells[i]).append(',').append(released[i]).append(',').append(closedPressure[i]).append('\n');
    }
    write("source-term-benchmarks", "real-gas-pipe-decompression-refinement.csv", receipt.toString());
    assertTrue(released[0] > released[1] && released[1] > released[2], receipt.toString());
    assertTrue((released[0] - released[2]) / released[2] < 0.06, receipt.toString());
    assertEquals(closedPressure[0], closedPressure[1], closedPressure[0] * 1.0e-10, receipt.toString());
    assertEquals(closedPressure[1], closedPressure[2], closedPressure[1] * 1.0e-10, receipt.toString());
  }

  @Test
  void finiteUpstreamVesselFeedsPipeAndClosesCombinedMassAndEnergy() {
    RealGasPipeDecompression pipe = vesselPipe(0.20, 12, 0.30);
    double initialVesselMass = pipe.getUpstreamVesselMassKg();
    double initialPipeMass = pipe.getRemainingPipeMassKg();
    pipe.runTransient(0.20, UUID.randomUUID());

    assertTrue(pipe.hasFiniteUpstreamVessel());
    assertEquals(0.20, pipe.getUpstreamVesselVolumeM3(), 0.0);
    assertTrue(pipe.getUpstreamVesselMassKg() < initialVesselMass);
    assertTrue(pipe.getRemainingPipeMassKg() < initialPipeMass);
    assertTrue(pipe.getReleasedMassKg() > 0.0);
    assertEquals(pipe.getInitialMassKg(), pipe.getRemainingMassKg() + pipe.getReleasedMassKg(),
        pipe.getInitialMassKg() * 3.0e-10);
    assertEquals(pipe.getInitialEnergyJ(), pipe.getRemainingEnergyJ() + pipe.getReleasedEnergyJ(),
        Math.abs(pipe.getInitialEnergyJ()) * 3.0e-10);
    SystemInterface snapshot = pipe.getUpstreamVesselFluid();
    assertNotNull(snapshot);
    double pressure = snapshot.getPressure("Pa");
    snapshot.setPressure(1.0, "bara");
    assertEquals(pressure, pipe.getUpstreamVesselFluid().getPressure("Pa"), pressure * 1.0e-12);
  }

  @Test
  void upstreamVesselCflRefinementAndNearbyVolumesRemainPhysical() throws Exception {
    double[] cfls = new double[] {0.45, 0.30, 0.20};
    double[] released = new double[cfls.length];
    double[] vesselPressure = new double[cfls.length];
    StringBuilder receipt = new StringBuilder("cfl,released_mass_kg,vessel_pressure_Pa\n");
    for (int i = 0; i < cfls.length; i++) {
      RealGasPipeDecompression pipe = vesselPipe(0.20, 12, cfls[i]);
      pipe.runTransient(0.20, UUID.randomUUID());
      released[i] = pipe.getReleasedMassKg();
      vesselPressure[i] = pipe.getUpstreamVesselFluid().getPressure("Pa");
      receipt.append(cfls[i]).append(',').append(released[i]).append(',').append(vesselPressure[i]).append('\n');
    }
    assertTrue(Math.abs(released[2] - released[1]) < Math.abs(released[1] - released[0]), receipt.toString());
    assertTrue(Math.abs(vesselPressure[2] - vesselPressure[1]) < Math.abs(vesselPressure[1] - vesselPressure[0]),
        receipt.toString());
    write("source-term-benchmarks", "real-gas-vessel-pipe-cfl-refinement.csv", receipt.toString());

    double previousPressure = 0.0;
    for (double volume : new double[] {0.10, 0.20, 0.40}) {
      RealGasPipeDecompression pipe = vesselPipe(volume, 12, 0.30);
      pipe.runTransient(0.20, UUID.randomUUID());
      double pressure = pipe.getUpstreamVesselFluid().getPressure("Pa");
      assertTrue(pressure > previousPressure);
      previousPressure = pressure;
    }
  }

  @Test
  void upstreamVesselFramesAreSchemaValidForBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      RealGasPipeDecompression pipe = vesselPipe(0.20, 8, 0.30);
      ProcessSystem process = new ProcessSystem();
      process.add(pipe);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("pipeline", process);
        session = new SourceTermSession("real-vessel-linepack", model);
        session.addInventorySource("rupture", "pipeline", "real-vessel-linepack");
      } else {
        session = new SourceTermSession("real-vessel-linepack", process);
        session.addInventorySource("rupture", "real-vessel-linepack");
      }
      session.runSteadyState();
      SourceTermFrame frame = session.step(0.20).get(0);
      SourceTermFrame.verifyEnvelope(frame.toJson());
      JsonObject provenance = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("provenance");
      assertEquals("COUPLED_1D_REAL_GAS_VESSEL_PIPE_DECOMPRESSION", provenance.get("releaseBasis").getAsString());
      assertEquals("RIGID_ADIABATIC_WELL_MIXED_GAS_VESSEL", provenance.get("upstreamBoundary").getAsString());
      assertEquals(0.20, provenance.get("upstreamVesselVolumeM3").getAsDouble(), 0.0);
      write("source-term-contract-fixtures", "real-gas-vessel-pipe-" + useModel + ".json", frame.toJson());
    }
  }

  @Test
  void legacyClosedEndAndInvalidVesselConfigurationRemainExplicit() {
    RealGasPipeDecompression legacy = pipe(8);
    assertFalse(legacy.hasFiniteUpstreamVessel());
    assertEquals(0.0, legacy.getUpstreamVesselVolumeM3(), 0.0);
    assertEquals(0.0, legacy.getUpstreamVesselMassKg(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> vesselPipe(Double.NaN, 8, 0.30));
    assertThrows(IllegalArgumentException.class, () -> vesselPipe(-0.10, 8, 0.30));
  }

  @Test
  void bothProcessContainersEmitSchemaValidTransientFrames() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      RealGasPipeDecompression pipe = pipe(8);
      ProcessSystem process = new ProcessSystem();
      process.add(pipe);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("pipeline", process);
        session = new SourceTermSession("real-transient-linepack", model);
        session.addInventorySource("rupture", "pipeline", "real-linepack");
      } else {
        session = new SourceTermSession("real-transient-linepack", process);
        session.addInventorySource("rupture", "real-linepack");
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      assertTrue(steady.getStatus() == SourceTermFrame.Status.VALID
          || steady.getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, steady.toJson());
      SourceTermFrame frame = session.step(0.01).get(0);
      assertTrue(frame.getStatus() == SourceTermFrame.Status.VALID
          || frame.getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, frame.toJson());
      SourceTermFrame.verifyEnvelope(frame.toJson());
      JsonObject provenance = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("provenance");
      assertEquals("COUPLED_1D_REAL_GAS_PIPE_DECOMPRESSION_LINE_PACKING", provenance.get("releaseBasis").getAsString());
      assertEquals("EOS_VU_FINITE_VOLUME_LOCAL_LAX_FRIEDRICHS_V1", provenance.get("pipeIntegrator").getAsString());
      write("source-term-contract-fixtures", "real-gas-pipe-decompression-" + useModel + ".json", frame.toJson());
    }
  }

  @Test
  void isolationAndUnsupportedRegimesFailClosedAtomically() {
    RealGasPipeDecompression pipe = pipe(8);
    pipe.runTransient(0.01, UUID.randomUUID());
    pipe.setReleaseEnabled(false);
    double mass = pipe.getRemainingMassKg();
    double released = pipe.getReleasedMassKg();
    pipe.runTransient(0.01, UUID.randomUUID());
    assertEquals(mass, pipe.getRemainingMassKg(), mass * 1.0e-12);
    assertEquals(released, pipe.getReleasedMassKg(), 0.0);
    assertEquals(0.0, pipe.getReleaseResult().getMassFlowRateKgS(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(Double.NaN, UUID.randomUUID()));
    assertEquals(mass, pipe.getRemainingMassKg(), mass * 1.0e-12);
    ReleaseFlowResult stateless = pipe.getReleaseModel().calculate(pipe.getReleaseRequest());
    assertFalse(stateless.isUsable());
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, stateless.getStatus());

    SystemInterface wet = new SystemSrkEos(250.0, 50.0);
    wet.addComponent("methane", 0.4);
    wet.addComponent("n-hexane", 0.6);
    wet.setMixingRule("classic");
    assertThrows(RuntimeException.class,
        () -> new RealGasPipeDecompression("wet", wet, 20.0, 0.1, 1.0, 101325.0, 0.01, 8, 0.3));
  }

  private static void write(String directoryName, String filename, String value) throws Exception {
    Path directory = Paths.get("target", directoryName);
    Files.createDirectories(directory);
    Files.write(directory.resolve(filename), value.getBytes(StandardCharsets.UTF_8));
  }
}
