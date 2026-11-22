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

/** Finite-rate phase-split relaxation, conservation, refinement and process coverage. */
class FiniteRateDriftFluxReleaseModelTest extends neqsim.NeqSimTest {
  /**
   * Creates a reproducible multicomponent flashing fluid.
   *
   * @param methaneFraction methane mole fraction
   * @return unflashed SRK fluid
   */
  private SystemInterface flashingFluid(double methaneFraction) {
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("n-heptane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates the common opening request.
   *
   * @param fluid upstream fluid
   * @return immutable release request
   */
  private ReleaseFlowRequest request(SystemInterface fluid) {
    return new ReleaseFlowRequest(fluid, 0.01, 0.62, 1.0e5);
  }

  /** Verifies phase relaxation, component conservation and stagnation-energy closure. */
  @Test
  void relaxesPhaseSplitAndConservesComponentsAndEnergy() {
    SystemInterface input = flashingFluid(0.10);
    FiniteRateDriftFluxReleaseModel model = new FiniteRateDriftFluxReleaseModel(0.020, 0.50, 0.25,
        "public-screening-assumption:v1");
    ReleaseFlowResult result = model.calculate(request(input));
    ReleaseFlowResult equilibrium = new HomogeneousEquilibriumReleaseModel().calculate(request(input));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    assertTrue(equilibrium.isUsable(), () -> equilibrium.getDiagnostics().get(0).getMessage());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    ReleaseState equilibriumThroat = equilibrium.getStations().get(Station.THROAT_CRITICAL);
    double upstreamGas = upstream.getGasMassFraction();
    double equilibriumGas = equilibriumThroat.getGasMassFraction();
    double expectedGas = upstreamGas + model.getPhaseTransferProgress() * (equilibriumGas - upstreamGas);
    assertEquals(expectedGas, throat.getGasMassFraction(), 1e-12);
    assertEquals(upstream.getComponentMassFractions(), throat.getComponentMassFractions());
    assertEquals(1.0, throat.getPhaseMassFractions().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    double kineticEnergyJkg = 0.0;
    for (Map.Entry<String, Double> entry : throat.getPhaseMassFractions().entrySet()) {
      double velocityMs = throat.getPhaseVelocitiesMs().get(entry.getKey());
      kineticEnergyJkg += 0.5 * entry.getValue() * velocityMs * velocityMs;
    }
    assertEquals(upstream.getEnthalpyJkg(), throat.getEnthalpyJkg() + kineticEnergyJkg, 1e-6);
    assertEquals(30.0, input.getPressure(), 0.0);
    assertEquals(300.0, input.getTemperature(), 0.0);
    assertEquals(1.0, input.getTotalNumberOfMoles(), 0.0);
    assertFalse(model.getEvidence().hasIndependentEvidence());
    assertTrue(result.getDiagnostics().stream().anyMatch(d -> "FINITE_RATE_CONSERVATION".equals(d.getCode())));
  }

  /** Verifies the exact exponential endpoint and refinement against explicit Euler integration. */
  @Test
  void analyticalRelaxationIsTimestepIndependentAndEulerConverges() throws Exception {
    double relaxationTimeS = 0.50;
    double residenceTimeS = 0.25;
    FiniteRateDriftFluxReleaseModel model = new FiniteRateDriftFluxReleaseModel(0.020, relaxationTimeS, residenceTimeS,
        "public-screening-assumption:v1");
    double exact = model.getPhaseTransferProgress();
    double previousError = Double.POSITIVE_INFINITY;
    List<String> rows = new ArrayList<String>();
    rows.add("steps,step_s,euler_progress,exact_progress,absolute_error");
    for (int steps : new int[] {1, 2, 4, 8, 16, 32, 64}) {
      double stepS = residenceTimeS / steps;
      double progress = 0.0;
      for (int step = 0; step < steps; step++) {
        progress += stepS * (1.0 - progress) / relaxationTimeS;
      }
      double error = Math.abs(progress - exact);
      assertTrue(error < previousError);
      previousError = error;
      rows.add(steps + "," + stepS + "," + progress + "," + exact + "," + error);
    }
    assertEquals(1.0 - Math.exp(-residenceTimeS / relaxationTimeS), exact, 0.0);
    assertTrue(previousError < 0.002);
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("finite-rate-phase-relaxation-refinement.csv"), rows, StandardCharsets.UTF_8);
  }

  /** Verifies fail-closed parameter and single-gas behavior. */
  @Test
  void invalidParametersAndSingleGasFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new FiniteRateDriftFluxReleaseModel(0.0, 1.0, 1.0, "source"));
    assertThrows(IllegalArgumentException.class, () -> new FiniteRateDriftFluxReleaseModel(0.02, 0.0, 1.0, "source"));
    assertThrows(IllegalArgumentException.class, () -> new FiniteRateDriftFluxReleaseModel(0.02, 1.0, 0.0, "source"));
    assertThrows(IllegalArgumentException.class, () -> new FiniteRateDriftFluxReleaseModel(0.02, 1.0, 1.0, " "));
    SystemInterface gas = new SystemSrkEos(300.0, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    ReleaseFlowResult result = new FiniteRateDriftFluxReleaseModel(0.02, 1.0, 0.1, "source").calculate(request(gas));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, result.getStatus());
    assertFalse(result.isUsable());
  }

  /** Verifies deterministic schema output through both process-container paths. */
  @Test
  void schemaCarriesFiniteRateProvenanceThroughBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid(0.10));
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      FiniteRateDriftFluxReleaseModel releaseModel = new FiniteRateDriftFluxReleaseModel(0.020, 0.50, 0.25,
          "public-screening-assumption:v1");
      if (useModel) {
        ProcessModel processModel = new ProcessModel();
        processModel.add("area", process);
        session = new SourceTermSession("finite-rate-study", processModel);
        session.addSource("opening", "area", "feed", -1, 0.01, 0.62, 1.0e5, releaseModel);
      } else {
        session = new SourceTermSession("finite-rate-study", process);
        session.addSource("opening", "feed", 0.01, 0.62, 1.0e5, releaseModel);
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.1).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject json = JsonParser.parseString(frame.toJson()).getAsJsonObject();
        assertEquals("finite-rate-phase-relaxation-vertical-drift-flux-orifice",
            json.getAsJsonObject("model").get("id").getAsString());
        assertTrue(json.getAsJsonArray("diagnostics").toString().contains("public-screening-assumption:v1"));
        JsonObject throat = json.getAsJsonObject("source").getAsJsonObject("stations")
            .getAsJsonObject("THROAT_CRITICAL");
        assertTrue(throat.has("phaseDensities"));
        assertTrue(throat.has("phaseVelocities"));
      }
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("finite-rate-drift-flux.json"),
            dynamic.toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }
}
