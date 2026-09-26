package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Prescribed-slip analytical limits, closure, contract and process-container coverage. */
class SlipCorrectedHomogeneousEquilibriumReleaseModelTest extends neqsim.NeqSimTest {
  private SystemInterface flashingFluid() {
    SystemInterface fluid = new SystemSrkEos(270.0, 30.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("propane", 0.5);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private ReleaseFlowRequest request(SystemInterface fluid) {
    return new ReleaseFlowRequest(fluid, 0.01, 0.62, 1.0e5);
  }

  @Test
  void homogeneousLimitAndPrescribedSlipCloseAreaAndEnergy() {
    SystemInterface input = flashingFluid();
    ReleaseFlowResult homogeneous = new HomogeneousEquilibriumReleaseModel().calculate(request(input));
    ReleaseFlowResult unity = new SlipCorrectedHomogeneousEquilibriumReleaseModel(1.0).calculate(request(input));
    ReleaseFlowResult slip = new SlipCorrectedHomogeneousEquilibriumReleaseModel(2.0).calculate(request(input));
    assertTrue(homogeneous.isUsable(), () -> homogeneous.getDiagnostics().get(0).getMessage());
    assertTrue(unity.isUsable(), () -> unity.getDiagnostics().get(0).getMessage());
    assertTrue(slip.isUsable(), () -> slip.getDiagnostics().get(0).getMessage());
    assertEquals(homogeneous.getMassFlowRateKgS(), unity.getMassFlowRateKgS(), 1e-10);
    ReleaseState homogeneousThroat = homogeneous.getStations().get(Station.THROAT_CRITICAL);
    ReleaseState throat = slip.getStations().get(Station.THROAT_CRITICAL);
    Map<String, Double> velocities = throat.getPhaseVelocitiesMs();
    assertEquals(2, velocities.size());
    String liquid = velocities.containsKey("OIL") ? "OIL" : velocities.containsKey("LIQUID") ? "LIQUID" : "AQUEOUS";
    assertEquals(2.0, velocities.get("GAS") / velocities.get(liquid), 1e-12);
    double massFlux = throat.getMassFluxKgM2s();
    double areaSum = 0.0;
    double kineticEnergy = 0.0;
    for (String phase : velocities.keySet()) {
      double massFraction = throat.getPhaseMassFractions().get(phase);
      areaSum += massFlux * massFraction / (throat.getPhaseDensitiesKgM3().get(phase) * velocities.get(phase));
      kineticEnergy += 0.5 * massFraction * velocities.get(phase) * velocities.get(phase);
    }
    assertEquals(1.0, areaSum, 1e-10);
    assertEquals(0.5 * homogeneousThroat.getVelocityMs() * homogeneousThroat.getVelocityMs(), kineticEnergy, 1e-7);
    assertEquals(30.0, input.getPressure(), 0.0);
    assertEquals(270.0, input.getTemperature(), 0.0);
    assertEquals(1.0, input.getTotalNumberOfMoles(), 0.0);
    assertTrue(slip.getDiagnostics().stream().anyMatch(d -> "SLIP_CLOSURE".equals(d.getCode())));
  }

  @Test
  void invalidConfigurationAndSinglePhaseFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new SlipCorrectedHomogeneousEquilibriumReleaseModel(0.99));
    SystemInterface gas = new SystemSrkEos(300.0, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    ReleaseFlowResult result = new SlipCorrectedHomogeneousEquilibriumReleaseModel(2.0).calculate(request(gas));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, result.getStatus());
    assertFalse(result.isUsable());
    assertThrows(IllegalStateException.class, result::getMassFlowRateKgS);
  }

  @Test
  void nearbySlipCasesRemainSmoothAndRetainEvidence() throws Exception {
    List<String> rows = new ArrayList<String>();
    rows.add("slip_ratio,mass_flow_kg_s,gas_velocity_m_s,liquid_velocity_m_s,phase_area_sum");
    double previousRate = Double.NaN;
    for (double ratio : new double[] {1.0, 1.5, 1.9, 2.0, 2.1, 2.5, 3.0}) {
      ReleaseFlowResult result = new SlipCorrectedHomogeneousEquilibriumReleaseModel(ratio)
          .calculate(request(flashingFluid()));
      assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
      ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
      String liquid = throat.getPhaseVelocitiesMs().containsKey("OIL") ? "OIL"
          : throat.getPhaseVelocitiesMs().containsKey("LIQUID") ? "LIQUID" : "AQUEOUS";
      double areaSum = 0.0;
      for (String phase : throat.getPhaseVelocitiesMs().keySet()) {
        areaSum += throat.getMassFluxKgM2s() * throat.getPhaseMassFractions().get(phase)
            / (throat.getPhaseDensitiesKgM3().get(phase) * throat.getPhaseVelocitiesMs().get(phase));
      }
      assertEquals(1.0, areaSum, 1e-10);
      if (Double.isFinite(previousRate)) {
        assertTrue(Math.abs(result.getMassFlowRateKgS() - previousRate) / previousRate < 0.25);
      }
      previousRate = result.getMassFlowRateKgS();
      rows.add(ratio + "," + result.getMassFlowRateKgS() + "," + throat.getPhaseVelocitiesMs().get("GAS") + ","
          + throat.getPhaseVelocitiesMs().get(liquid) + "," + areaSum);
    }
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("prescribed-slip-sensitivity.csv"), rows, StandardCharsets.UTF_8);
  }

  @Test
  void schemaCarriesPhaseDensitiesAndVelocitiesThroughBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid());
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("area", process);
        session = new SourceTermSession("slip-study", model);
        session.addSource("opening", "area", "feed", -1, 0.01, 0.62, 1.0e5,
            new SlipCorrectedHomogeneousEquilibriumReleaseModel(2.0));
      } else {
        session = new SourceTermSession("slip-study", process);
        session.addSource("opening", "feed", 0.01, 0.62, 1.0e5,
            new SlipCorrectedHomogeneousEquilibriumReleaseModel(2.0));
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.1).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject throat = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("source")
            .getAsJsonObject("stations").getAsJsonObject("THROAT_CRITICAL");
        assertTrue(throat.has("phaseDensities"));
        assertTrue(throat.has("phaseVelocities"));
        assertEquals("m/s", throat.getAsJsonObject("phaseVelocities").getAsJsonObject("GAS").get("unit").getAsString());
      }
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("prescribed-slip.json"), dynamic.toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }
}
