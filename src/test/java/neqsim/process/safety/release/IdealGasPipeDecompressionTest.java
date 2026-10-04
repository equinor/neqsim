package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Conservation, wave propagation, refinement and process integration for transient line packing. */
class IdealGasPipeDecompressionTest extends neqsim.NeqSimTest {

  private static SystemInterface gas(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressureBar);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("nitrogen", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static IdealGasPipeDecompression pipe(int cells) {
    return new IdealGasPipeDecompression("linepack", gas(20.0), 100.0, 0.2, 1.0, 101325.0, 0.012, cells, 0.45);
  }

  @Test
  void conservativeUpdateRetainsCompositionAndPropagatesFiniteSpeedWave() {
    IdealGasPipeDecompression pipe = pipe(80);
    double[] composition = pipe.getComponentMassFractions();
    pipe.runTransient(0.08, UUID.randomUUID());
    assertTrue(pipe.getReleasedMassKg() > 0.0);
    assertEquals(pipe.getInitialMassKg(), pipe.getRemainingMassKg() + pipe.getReleasedMassKg(),
        pipe.getInitialMassKg() * 2.0e-10);
    assertEquals(pipe.getInitialEnergyJ(), pipe.getRemainingEnergyJ() + pipe.getReleasedEnergyJ(),
        pipe.getInitialEnergyJ() * 2.0e-10);
    assertArrayEquals(composition, pipe.getComponentMassFractions(), 0.0);
    double[] pressure = pipe.getPressureProfilePa();
    assertEquals(2.0e6, pressure[0], 2.0e6 * 2.0e-3);
    assertTrue(pressure[pressure.length - 1] < pressure[0]);
    assertTrue(pipe.getReleaseResult().getMassFlowRateKgS() > 0.0);
  }

  @Test
  void spatialRefinementConvergesDischargeAndClosedEndPressure() throws Exception {
    double[] released = new double[3];
    double[] closedPressure = new double[3];
    int[] cells = new int[] {20, 40, 80};
    for (int i = 0; i < cells.length; i++) {
      IdealGasPipeDecompression pipe = pipe(cells[i]);
      pipe.runTransient(0.2, UUID.randomUUID());
      released[i] = pipe.getReleasedMassKg();
      closedPressure[i] = pipe.getPressureProfilePa()[0];
    }
    assertTrue(Math.abs(released[2] - released[1]) < Math.abs(released[1] - released[0]));
    assertTrue(Math.abs(closedPressure[2] - closedPressure[1]) < Math.abs(closedPressure[1] - closedPressure[0]));
    StringBuilder receipt = new StringBuilder("cells,released_mass_kg,closed_end_pressure_Pa\n");
    for (int i = 0; i < cells.length; i++) {
      receipt.append(cells[i]).append(',').append(released[i]).append(',').append(closedPressure[i]).append('\n');
    }
    write("source-term-benchmarks", "ideal-gas-pipe-decompression-refinement.csv", receipt.toString());
  }

  @Test
  void physicalIsolationStopsDischargeAndRetainsAtomicState() {
    IdealGasPipeDecompression pipe = pipe(40);
    pipe.runTransient(0.05, UUID.randomUUID());
    pipe.setReleaseEnabled(false);
    double mass = pipe.getRemainingMassKg();
    double released = pipe.getReleasedMassKg();
    pipe.runTransient(0.05, UUID.randomUUID());
    assertEquals(mass, pipe.getRemainingMassKg(), mass * 1.0e-12);
    assertEquals(released, pipe.getReleasedMassKg(), 0.0);
    assertEquals(0.0, pipe.getReleaseResult().getMassFlowRateKgS(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(Double.NaN, UUID.randomUUID()));
    assertEquals(mass, pipe.getRemainingMassKg(), 0.0);
  }

  @Test
  void bothProcessContainersEmitSchemaValidTransientFrames() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      IdealGasPipeDecompression pipe = pipe(40);
      ProcessSystem process = new ProcessSystem();
      process.add(pipe);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("pipeline", process);
        session = new SourceTermSession("transient-linepack", model);
        session.addInventorySource("rupture", "pipeline", "linepack");
      } else {
        session = new SourceTermSession("transient-linepack", process);
        session.addInventorySource("rupture", "linepack");
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      assertTrue(steady.getStatus() == SourceTermFrame.Status.VALID
          || steady.getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, steady.toJson());
      SourceTermFrame frame = session.step(0.05).get(0);
      assertTrue(frame.getStatus() == SourceTermFrame.Status.VALID
          || frame.getStatus() == SourceTermFrame.Status.VALID_WITH_WARNINGS, frame.toJson());
      SourceTermFrame.verifyEnvelope(frame.toJson());
      JsonObject provenance = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("provenance");
      assertEquals("COUPLED_1D_IDEAL_GAS_PIPE_DECOMPRESSION_LINE_PACKING",
          provenance.get("releaseBasis").getAsString());
      assertEquals("FINITE_VOLUME_LOCAL_LAX_FRIEDRICHS_V1", provenance.get("pipeIntegrator").getAsString());
      assertEquals(pipe.getReleasedMassKg(), provenance.get("cumulativeReleasedMassKg").getAsDouble(), 0.0);
      write("source-term-contract-fixtures", "ideal-gas-pipe-decompression-" + useModel + ".json", frame.toJson());
    }
  }

  @Test
  void unsupportedInitialPhaseFailsClosed() {
    SystemInterface wet = new SystemSrkEos(280.0, 40.0);
    wet.addComponent("methane", 0.5);
    wet.addComponent("n-hexane", 0.5);
    wet.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class,
        () -> new IdealGasPipeDecompression("wet", wet, 100.0, 0.2, 1.0, 101325.0, 0.01, 20, 0.4));
  }

  @Test
  void statelessModelCallFailsClosed() {
    IdealGasPipeDecompression pipe = pipe(20);
    ReleaseFlowResult result = pipe.getReleaseModel().calculate(pipe.getReleaseRequest());
    assertFalse(result.isUsable());
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, result.getStatus());
  }

  private static void write(String directoryName, String filename, String value) throws Exception {
    Path directory = Paths.get("target", directoryName);
    Files.createDirectories(directory);
    Files.write(directory.resolve(filename), value.getBytes(StandardCharsets.UTF_8));
  }
}
