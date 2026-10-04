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

/** Predictive vertical drift-flux closure, applicability and process-container coverage. */
class DriftFluxHomogeneousEquilibriumReleaseModelTest extends neqsim.NeqSimTest {
  private SystemInterface flashingFluid(double methaneFraction) {
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("n-heptane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private ReleaseFlowRequest request(SystemInterface fluid) {
    return new ReleaseFlowRequest(fluid, 0.01, 0.62, 1.0e5);
  }

  @Test
  void predictsSlipAndClosesDriftAreaAndEnergy() {
    SystemInterface input = flashingFluid(0.10);
    DriftFluxHomogeneousEquilibriumReleaseModel model = new DriftFluxHomogeneousEquilibriumReleaseModel(0.020);
    ReleaseFlowResult result = model.calculate(request(input));
    ReleaseFlowResult homogeneous = new HomogeneousEquilibriumReleaseModel().calculate(request(input));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    assertTrue(homogeneous.isUsable(), () -> homogeneous.getDiagnostics().get(0).getMessage());
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    ReleaseState homogeneousThroat = homogeneous.getStations().get(Station.THROAT_CRITICAL);
    Map<String, Double> velocities = throat.getPhaseVelocitiesMs();
    String liquid = liquidPhase(velocities);
    double slipRatio = velocities.get("GAS") / velocities.get(liquid);
    assertTrue(slipRatio > 1.0);
    double massFlux = throat.getMassFluxKgM2s();
    double areaSum = 0.0;
    double kineticEnergy = 0.0;
    double volumetricFlux = 0.0;
    for (String phase : velocities.keySet()) {
      double massFraction = throat.getPhaseMassFractions().get(phase);
      double density = throat.getPhaseDensitiesKgM3().get(phase);
      areaSum += massFlux * massFraction / (density * velocities.get(phase));
      kineticEnergy += 0.5 * massFraction * velocities.get(phase) * velocities.get(phase);
      volumetricFlux += massFlux * massFraction / density;
    }
    assertEquals(1.0, areaSum, 1e-10);
    assertEquals(0.5 * homogeneousThroat.getVelocityMs() * homogeneousThroat.getVelocityMs(), kineticEnergy, 1e-7);
    assertTrue(velocities.get("GAS") - model.getDistributionParameter() * volumetricFlux > 0.0);
    ReleaseFlowResult prescribed = new SlipCorrectedHomogeneousEquilibriumReleaseModel(slipRatio)
        .calculate(request(input));
    assertTrue(prescribed.isUsable(), () -> prescribed.getDiagnostics().get(0).getMessage());
    assertEquals(prescribed.getMassFlowRateKgS(), result.getMassFlowRateKgS(), 1e-10);
    assertEquals(30.0, input.getPressure(), 0.0);
    assertEquals(300.0, input.getTemperature(), 0.0);
    assertEquals(1.0, input.getTotalNumberOfMoles(), 0.0);
    assertFalse(model.getEvidence().hasIndependentEvidence());
    assertTrue(result.getDiagnostics().stream().anyMatch(d -> "DRIFT_FLUX_CLOSURE".equals(d.getCode())));
  }

  @Test
  void singlePhaseFailsClosed() {
    SystemInterface gas = new SystemSrkEos(300.0, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    ReleaseFlowResult result = new DriftFluxHomogeneousEquilibriumReleaseModel(0.020).calculate(request(gas));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, result.getStatus());
    assertFalse(result.isUsable());
    assertThrows(IllegalArgumentException.class, () -> new DriftFluxHomogeneousEquilibriumReleaseModel(0.0));
    assertThrows(IllegalArgumentException.class, () -> new DriftFluxHomogeneousEquilibriumReleaseModel(Double.NaN));
  }

  @Test
  void nearbyMixturesRemainSmoothAndBounded() throws Exception {
    List<String> rows = new ArrayList<String>();
    rows.add("methane_mole_fraction,mass_flow_kg_s,slip_ratio,gas_area_fraction,phase_area_sum");
    double previousRate = Double.NaN;
    for (double methaneFraction : new double[] {0.0875, 0.10, 0.1125, 0.125, 0.1375}) {
      ReleaseFlowResult result = new DriftFluxHomogeneousEquilibriumReleaseModel(0.020)
          .calculate(request(flashingFluid(methaneFraction)));
      assertTrue(result.isUsable(), () -> methaneFraction + ": " + result.getDiagnostics().get(0).getMessage());
      ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
      String liquid = liquidPhase(throat.getPhaseVelocitiesMs());
      double areaSum = 0.0;
      double gasAreaFraction = 0.0;
      for (String phase : throat.getPhaseVelocitiesMs().keySet()) {
        double areaFraction = throat.getMassFluxKgM2s() * throat.getPhaseMassFractions().get(phase)
            / (throat.getPhaseDensitiesKgM3().get(phase) * throat.getPhaseVelocitiesMs().get(phase));
        areaSum += areaFraction;
        if ("GAS".equals(phase)) {
          gasAreaFraction = areaFraction;
        }
      }
      assertEquals(1.0, areaSum, 1e-10);
      assertTrue(gasAreaFraction <= new DriftFluxHomogeneousEquilibriumReleaseModel(0.020).getMaximumGasAreaFraction());
      if (Double.isFinite(previousRate)) {
        assertTrue(Math.abs(result.getMassFlowRateKgS() - previousRate) / previousRate < 0.20);
      }
      previousRate = result.getMassFlowRateKgS();
      rows.add(methaneFraction + "," + result.getMassFlowRateKgS() + ","
          + throat.getPhaseVelocitiesMs().get("GAS") / throat.getPhaseVelocitiesMs().get(liquid) + "," + gasAreaFraction
          + "," + areaSum);
    }
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("vertical-drift-flux-sensitivity.csv"), rows, StandardCharsets.UTF_8);
  }

  @Test
  void schemaCarriesPredictiveSlipThroughBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid(0.10));
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("area", process);
        session = new SourceTermSession("drift-flux-study", model);
        session.addSource("opening", "area", "feed", -1, 0.01, 0.62, 1.0e5,
            new DriftFluxHomogeneousEquilibriumReleaseModel(0.020));
      } else {
        session = new SourceTermSession("drift-flux-study", process);
        session.addSource("opening", "feed", 0.01, 0.62, 1.0e5, new DriftFluxHomogeneousEquilibriumReleaseModel(0.020));
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.1).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject json = JsonParser.parseString(frame.toJson()).getAsJsonObject();
        assertEquals("equilibrium-thermodynamics-vertical-drift-flux-orifice",
            json.getAsJsonObject("model").get("id").getAsString());
        JsonObject throat = json.getAsJsonObject("source").getAsJsonObject("stations")
            .getAsJsonObject("THROAT_CRITICAL");
        assertTrue(throat.has("phaseDensities"));
        assertTrue(throat.has("phaseVelocities"));
      }
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("vertical-drift-flux.json"), dynamic.toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  private String liquidPhase(Map<String, Double> velocities) {
    return velocities.containsKey("OIL") ? "OIL" : velocities.containsKey("LIQUID") ? "LIQUID" : "AQUEOUS";
  }
}
