package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.release.RanzMarshallMassTransferCorrelation.Morphology;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.process.safety.release.ReleaseFlowResult.Status;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/**
 * Prescribed liquid-droplet slip, conservation, applicability and process-path coverage.
 *
 * @author esol
 * @version 1.0
 */
class RanzMarshallDropletFiniteRateReleaseModelTest extends neqsim.NeqSimTest {
  /**
   * Creates a methane/heptane fluid that flashes at the release throat.
   *
   * @return configured mutable test fluid
   */
  private SystemInterface flashingFluid() {
    SystemInterface fluid = new SystemSrkEos(270.0, 30.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("propane", 0.20);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates deterministic gas-phase component diffusivities.
   *
   * @return component-name-sorted diffusivity map in m2/s
   */
  private Map<String, Double> diffusivities() {
    Map<String, Double> values = new TreeMap<String, Double>();
    values.put("methane", 2.0e-6);
    values.put("propane", 1.0e-6);
    return values;
  }

  /**
   * Creates a bounded liquid-droplet external-gas-film correlation.
   *
   * @param dropletDiameterM Sauter mean diameter in m
   * @param relativeVelocityMs gas-to-droplet relative speed in m/s
   * @return immutable correlation
   */
  private RanzMarshallMassTransferCorrelation correlation(double dropletDiameterM, double relativeVelocityMs) {
    return new RanzMarshallMassTransferCorrelation(Morphology.LIQUID_DROPLETS, dropletDiameterM, 25.0, 1.0e-5,
        relativeVelocityMs, diffusivities(), "public-prescribed-droplet-basis:v1");
  }

  /**
   * Creates the release adapter.
   *
   * @param correlation immutable droplet correlation
   * @return configured release-flow model
   */
  private RanzMarshallDropletFiniteRateReleaseModel model(RanzMarshallMassTransferCorrelation correlation) {
    return new RanzMarshallDropletFiniteRateReleaseModel(0.020, 0.10, correlation);
  }

  /**
   * Creates the standard short-opening request.
   *
   * @param fluid release-fluid basis
   * @return configured request
   */
  private ReleaseFlowRequest request(SystemInterface fluid) {
    return new ReleaseFlowRequest(fluid, 0.010, 0.62, 1.0e5);
  }

  /** Liquid-droplet slip retains component, area, kinetic-energy and stagnation-energy closure. */
  @Test
  void prescribedDropletSlipClosesConservativeBalances() {
    SystemInterface input = flashingFluid();
    RanzMarshallDropletFiniteRateReleaseModel releaseModel = model(correlation(2.0e-4, 0.05));
    ReleaseFlowResult result = releaseModel.calculate(request(input));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    String liquid = throat.getPhaseMassFractions().containsKey("OIL") ? "OIL" : "LIQUID";
    assertTrue(throat.getPhaseVelocitiesMs().get("GAS") > throat.getPhaseVelocitiesMs().get(liquid));
    assertEquals(0.05, throat.getPhaseVelocitiesMs().get("GAS") - throat.getPhaseVelocitiesMs().get(liquid), 1e-10);
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
    assertEquals(270.0, input.getTemperature(), 0.0);
    assertTrue(
        result.getDiagnostics().stream().anyMatch(diagnostic -> "DROPLET_SLIP_CLOSURE".equals(diagnostic.getCode())));
    assertTrue(releaseModel.getEvidence().hasIndependentEvidence());
    assertTrue(releaseModel.getEvidence().getLimitations().contains("NO_EXPERIMENTAL_QUALIFICATION"));
  }

  /** A nearby-slip sweep changes phase velocities smoothly without changing the relaxed component basis. */
  @Test
  void nearbySlipSweepHasExpectedVelocityTrend() {
    ReleaseFlowRequest request = request(flashingFluid());
    ReleaseFlowResult equilibrium = new HomogeneousEquilibriumReleaseModel().calculate(request);
    assertTrue(equilibrium.isUsable(), () -> equilibrium.getDiagnostics().get(0).getMessage());
    Map<String, Double> relaxationTimes = new TreeMap<String, Double>();
    relaxationTimes.put("methane", 0.20);
    relaxationTimes.put("propane", 0.30);
    ComponentSelectiveFiniteRateReleaseModel phaseTransfer = new ComponentSelectiveFiniteRateReleaseModel(0.020,
        relaxationTimes, 0.10, "nearby-slip-test:v1");
    ReleaseState upstream = equilibrium.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState relaxed = phaseTransfer.relaxedState(upstream, equilibrium.getStations().get(Station.THROAT_CRITICAL));
    TwoPhaseDropletSlipClosure.Result lower = TwoPhaseDropletSlipClosure.solve(relaxed, 0.020, 2.0e-4, 0.04);
    TwoPhaseDropletSlipClosure.Result upper = TwoPhaseDropletSlipClosure.solve(relaxed, 0.020, 2.0e-4, 0.06);
    String liquid = relaxed.getPhaseMassFractions().containsKey("OIL") ? "OIL" : "LIQUID";
    assertTrue(upper.state.getPhaseVelocitiesMs().get("GAS") > lower.state.getPhaseVelocitiesMs().get("GAS"));
    assertTrue(upper.state.getPhaseVelocitiesMs().get(liquid) < lower.state.getPhaseVelocitiesMs().get(liquid));
    assertTrue(upper.liquidAreaFraction > lower.liquidAreaFraction);
    assertEquals(1.0, lower.phaseAreaSum, 1e-10);
    assertEquals(1.0, upper.phaseAreaSum, 1e-10);
    assertTrue(upper.liquidAreaFraction < TwoPhaseDropletSlipClosure.getMaximumLiquidAreaFraction());
  }

  /** Morphology and spherical/dilute applicability are explicit fail-closed boundaries. */
  @Test
  void validatesMorphologyAndFailsClosedOutsideSphericalRange() {
    RanzMarshallMassTransferCorrelation bubble = new RanzMarshallMassTransferCorrelation(Morphology.GAS_BUBBLES, 2.0e-4,
        800.0, 1.0e-3, 0.05, diffusivities(), "bubble-basis:v1");
    assertThrows(IllegalArgumentException.class, () -> model(bubble));

    ReleaseFlowResult excessiveEotvos = model(correlation(0.020, 0.001)).calculate(request(flashingFluid()));
    assertEquals(Status.UNSUPPORTED, excessiveEotvos.getStatus());
    assertEquals("DROPLET_REGIME_UNSUPPORTED", excessiveEotvos.getDiagnostics().get(0).getCode());
    assertTrue(excessiveEotvos.getDiagnostics().get(0).getMessage().contains("Eotvos"));
  }

  /** Deterministic schema output is retained through both process-container paths. */
  @Test
  void schemaCarriesDropletClosureThroughBothProcessContainers() {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid());
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      RanzMarshallDropletFiniteRateReleaseModel releaseModel = model(correlation(2.0e-4, 0.05));
      if (useModel) {
        ProcessModel processModel = new ProcessModel();
        processModel.add("area", process);
        session = new SourceTermSession("droplet-study", processModel);
        session.addSource("opening", "area", "feed", -1, 0.010, 0.62, 1.0e5, releaseModel);
      } else {
        session = new SourceTermSession("droplet-study", process);
        session.addSource("opening", "feed", 0.010, 0.62, 1.0e5, releaseModel);
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.10).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject json = JsonParser.parseString(frame.toJson()).getAsJsonObject();
        assertEquals("ranz-marshall-component-transfer-prescribed-droplet-slip-orifice",
            json.getAsJsonObject("model").get("id").getAsString());
        assertTrue(json.getAsJsonArray("diagnostics").toString().contains("public-prescribed-droplet-basis:v1"));
        JsonObject throat = json.getAsJsonObject("source").getAsJsonObject("stations")
            .getAsJsonObject("THROAT_CRITICAL");
        assertTrue(throat.getAsJsonObject("phaseComponentMassFractions").has("GAS"));
        assertTrue(throat.has("phaseVelocities"));
      }
    }
  }
}
