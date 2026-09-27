package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Component-selective phase-transfer, conservation, uniform-limit and process-path coverage. */
class ComponentSelectiveFiniteRateReleaseModelTest extends neqsim.NeqSimTest {
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

  private Map<String, Double> relaxationTimes(double methaneS, double heptaneS) {
    Map<String, Double> values = new TreeMap<String, Double>();
    values.put("methane", methaneS);
    values.put("n-heptane", heptaneS);
    return values;
  }

  /** Verifies exact component endpoints and component/energy conservation. */
  @Test
  void relaxesEachComponentAndReconstructsOverallComposition() {
    SystemInterface input = flashingFluid(0.10);
    ComponentSelectiveFiniteRateReleaseModel model = new ComponentSelectiveFiniteRateReleaseModel(0.020,
        relaxationTimes(0.10, 1.00), 0.25, "public-component-screening-assumption:v1");
    ReleaseFlowResult result = model.calculate(request(input));
    ReleaseFlowResult equilibrium = new HomogeneousEquilibriumReleaseModel().calculate(request(input));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    assertTrue(equilibrium.isUsable(), () -> equilibrium.getDiagnostics().get(0).getMessage());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    ReleaseState equilibriumThroat = equilibrium.getStations().get(Station.THROAT_CRITICAL);
    double expectedGasFraction = 0.0;
    for (String component : upstream.getComponentMassFractions().keySet()) {
      double initial = gasHeld(upstream, component);
      double target = gasHeld(equilibriumThroat, component);
      double expected = initial + model.getComponentTransferProgress(component) * (target - initial);
      double actual = throat.getPhaseMassFractions().get("GAS")
          * throat.getPhaseComponentMassFractions().get("GAS").get(component);
      assertEquals(expected, actual, 1e-12);
      expectedGasFraction += expected;
    }
    assertEquals(expectedGasFraction, throat.getGasMassFraction(), 1e-12);
    for (String component : upstream.getComponentMassFractions().keySet()) {
      double reconstructed = 0.0;
      for (Map.Entry<String, Double> phase : throat.getPhaseMassFractions().entrySet()) {
        reconstructed += phase.getValue() * throat.getPhaseComponentMassFractions().get(phase.getKey()).get(component);
      }
      assertEquals(upstream.getComponentMassFractions().get(component), reconstructed, 1e-12);
    }
    double kineticEnergyJkg = 0.0;
    for (Map.Entry<String, Double> phase : throat.getPhaseMassFractions().entrySet()) {
      double velocityMs = throat.getPhaseVelocitiesMs().get(phase.getKey());
      kineticEnergyJkg += 0.5 * phase.getValue() * velocityMs * velocityMs;
    }
    assertEquals(upstream.getEnthalpyJkg(), throat.getEnthalpyJkg() + kineticEnergyJkg, 1e-6);
    assertEquals(30.0, input.getPressure(), 0.0);
    assertEquals(300.0, input.getTemperature(), 0.0);
    assertFalse(model.getEvidence().hasIndependentEvidence());
  }

  /** Equal component times recover the existing uniform phase-split relaxation exactly. */
  @Test
  void equalComponentTimesRecoverUniformRelaxation() {
    SystemInterface input = flashingFluid(0.10);
    double relaxationTimeS = 0.50;
    ComponentSelectiveFiniteRateReleaseModel componentModel = new ComponentSelectiveFiniteRateReleaseModel(0.020,
        relaxationTimes(relaxationTimeS, relaxationTimeS), 0.25, "uniform-limit:v1");
    FiniteRateDriftFluxReleaseModel uniformModel = new FiniteRateDriftFluxReleaseModel(0.020, relaxationTimeS, 0.25,
        "uniform-limit:v1");
    ReleaseFlowResult component = componentModel.calculate(request(input));
    ReleaseFlowResult uniform = uniformModel.calculate(request(input));
    assertTrue(component.isUsable(), () -> component.getDiagnostics().get(0).getMessage());
    assertTrue(uniform.isUsable(), () -> uniform.getDiagnostics().get(0).getMessage());
    assertEquals(uniform.getMassFlowRateKgS(), component.getMassFlowRateKgS(), 1e-12);
    assertEquals(uniform.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction(),
        component.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction(), 1e-12);
  }

  /** Faster methane relaxation approaches its equilibrium phase allocation monotonically. */
  @Test
  void methaneRelaxationSweepIsSmoothAndMonotonic() throws Exception {
    SystemInterface input = flashingFluid(0.10);
    ReleaseFlowResult equilibrium = new HomogeneousEquilibriumReleaseModel().calculate(request(input));
    assertTrue(equilibrium.isUsable(), () -> equilibrium.getDiagnostics().get(0).getMessage());
    double target = gasHeld(equilibrium.getStations().get(Station.THROAT_CRITICAL), "methane");
    double previousError = Double.POSITIVE_INFINITY;
    List<String> rows = new ArrayList<String>();
    rows.add("methane_relaxation_s,methane_gas_held_mass_fraction,equilibrium_target,absolute_error,mass_rate_kg_s");
    for (double methaneRelaxationS : new double[] {1.0, 0.5, 0.25, 0.125}) {
      ComponentSelectiveFiniteRateReleaseModel model = new ComponentSelectiveFiniteRateReleaseModel(0.020,
          relaxationTimes(methaneRelaxationS, 1.0), 0.25, "nearby-component-sweep:v1");
      ReleaseFlowResult result = model.calculate(request(input));
      assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
      double actual = gasHeld(result.getStations().get(Station.THROAT_CRITICAL), "methane");
      double error = Math.abs(target - actual);
      assertTrue(error < previousError);
      previousError = error;
      rows.add(methaneRelaxationS + "," + actual + "," + target + "," + error + "," + result.getMassFlowRateKgS());
    }
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("component-selective-relaxation-sweep.csv"), rows, StandardCharsets.UTF_8);
  }

  /** Parameter maps are defensive and incomplete component bases fail closed. */
  @Test
  void validatesAndDefensivelyCopiesComponentTimes() {
    Map<String, Double> times = relaxationTimes(0.10, 1.00);
    ComponentSelectiveFiniteRateReleaseModel model = new ComponentSelectiveFiniteRateReleaseModel(0.020, times, 0.25,
        "source");
    times.put("methane", 9.0);
    assertEquals(0.10, model.getComponentRelaxationTimesS().get("methane"), 0.0);
    assertNotSame(model.getComponentRelaxationTimesS(), model.getComponentRelaxationTimesS());
    assertThrows(UnsupportedOperationException.class, () -> model.getComponentRelaxationTimesS().put("methane", 2.0));
    assertThrows(IllegalArgumentException.class,
        () -> new ComponentSelectiveFiniteRateReleaseModel(0.020, relaxationTimes(0.0, 1.0), 0.25, "source"));
    Map<String, Double> incomplete = new TreeMap<String, Double>();
    incomplete.put("methane", 0.10);
    ReleaseFlowResult result = new ComponentSelectiveFiniteRateReleaseModel(0.020, incomplete, 0.25, "source")
        .calculate(request(flashingFluid(0.10)));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, result.getStatus());
    assertFalse(result.isUsable());
  }

  /** Verifies deterministic component-resolved schema output through both process containers. */
  @Test
  void schemaCarriesComponentPartitionsThroughBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid(0.10));
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      ComponentSelectiveFiniteRateReleaseModel releaseModel = new ComponentSelectiveFiniteRateReleaseModel(0.020,
          relaxationTimes(0.10, 1.00), 0.25, "public-component-screening-assumption:v1");
      if (useModel) {
        ProcessModel processModel = new ProcessModel();
        processModel.add("area", process);
        session = new SourceTermSession("component-transfer-study", processModel);
        session.addSource("opening", "area", "feed", -1, 0.01, 0.62, 1.0e5, releaseModel);
      } else {
        session = new SourceTermSession("component-transfer-study", process);
        session.addSource("opening", "feed", 0.01, 0.62, 1.0e5, releaseModel);
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.1).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject json = JsonParser.parseString(frame.toJson()).getAsJsonObject();
        assertEquals("component-selective-finite-rate-vertical-drift-flux-orifice",
            json.getAsJsonObject("model").get("id").getAsString());
        assertTrue(json.getAsJsonArray("diagnostics").toString().contains("public-component-screening-assumption:v1"));
        JsonObject throat = json.getAsJsonObject("source").getAsJsonObject("stations")
            .getAsJsonObject("THROAT_CRITICAL");
        JsonObject phaseComponents = throat.getAsJsonObject("phaseComponentMassFractions");
        assertTrue(phaseComponents.has("GAS"));
        assertTrue(phaseComponents.getAsJsonObject("GAS").has("methane"));
        assertTrue(throat.has("phaseVelocities"));
      }
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("component-selective-finite-rate.json"),
            dynamic.toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  private double gasHeld(ReleaseState state, String component) {
    if (!state.getPhaseMassFractions().containsKey("GAS")) {
      return 0.0;
    }
    return state.getPhaseMassFractions().get("GAS") * state.getPhaseComponentMassFractions().get("GAS").get(component);
  }
}
