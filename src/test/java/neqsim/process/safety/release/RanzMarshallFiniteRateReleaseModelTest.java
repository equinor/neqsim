package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Ranz-Marshall equations, range guards, conservation and process-path coverage.
 *
 * @author esol
 * @version 1.0
 */
class RanzMarshallFiniteRateReleaseModelTest extends neqsim.NeqSimTest {
  /**
   * Creates a methane/heptane fluid that flashes at the release throat.
   *
   * @param methaneFraction feed methane mole fraction
   * @return configured mutable test fluid
   */
  private SystemInterface flashingFluid(double methaneFraction) {
    SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("n-heptane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates a standard short-opening request.
   *
   * @param fluid release-fluid basis
   * @return configured release request
   */
  private ReleaseFlowRequest request(SystemInterface fluid) {
    return new ReleaseFlowRequest(fluid, 0.01, 0.62, 1.0e5);
  }

  /**
   * Creates deterministic component diffusivity inputs.
   *
   * @param methaneM2S methane diffusivity in m2/s
   * @param heptaneM2S n-heptane diffusivity in m2/s
   * @return component-name-sorted diffusivity map
   */
  private Map<String, Double> diffusivities(double methaneM2S, double heptaneM2S) {
    Map<String, Double> values = new TreeMap<String, Double>();
    values.put("methane", methaneM2S);
    values.put("n-heptane", heptaneM2S);
    return values;
  }

  /**
   * Creates the common explicit Ranz-Marshall test basis.
   *
   * @param morphology dispersed-phase morphology
   * @param relativeVelocityMs relative speed magnitude in m/s
   * @param componentDiffusivitiesM2S continuous-phase component diffusivities in m2/s
   * @return immutable correlation basis
   */
  private RanzMarshallMassTransferCorrelation correlation(Morphology morphology, double relativeVelocityMs,
      Map<String, Double> componentDiffusivitiesM2S) {
    return new RanzMarshallMassTransferCorrelation(morphology, 1.0e-3, 800.0, 1.0e-3, relativeVelocityMs,
        componentDiffusivitiesM2S, "ranz-marshall-public-basis:v1");
  }

  /**
   * Creates the release adapter on the common tension and residence-time basis.
   *
   * @param correlation immutable transfer correlation
   * @return configured release-flow model
   */
  private RanzMarshallFiniteRateReleaseModel model(RanzMarshallMassTransferCorrelation correlation) {
    return new RanzMarshallFiniteRateReleaseModel(0.020, 0.25, correlation);
  }

  /** Verifies every equation and the exact stagnant-sphere limit. */
  @Test
  void evaluatesDimensionlessGroupsAndStagnantLimit() {
    RanzMarshallMassTransferCorrelation moving = correlation(Morphology.GAS_BUBBLES, 0.010,
        diffusivities(1.0e-8, 2.0e-8));
    assertEquals(8.0, moving.getReynoldsNumber(), 1e-14);
    for (Map.Entry<String, Double> entry : moving.getComponentDiffusivitiesM2S().entrySet()) {
      String component = entry.getKey();
      double diffusivity = entry.getValue();
      double expectedSchmidt = 1.0e-3 / (800.0 * diffusivity);
      double expectedSherwood = 2.0 + 0.6 * Math.sqrt(8.0) * Math.cbrt(expectedSchmidt);
      double expectedCoefficient = expectedSherwood * diffusivity / 1.0e-3;
      double expectedRelaxation = 1.0e-3 / (6.0 * expectedCoefficient);
      assertEquals(expectedSchmidt, moving.getSchmidtNumbers().get(component), 1e-13);
      assertEquals(expectedSherwood, moving.getSherwoodNumbers().get(component), 1e-13);
      assertEquals(expectedCoefficient, moving.getMassTransferCoefficientsMs().get(component), 1e-15);
      assertEquals(expectedRelaxation, moving.getComponentRelaxationTimesS().get(component), 1e-12);
    }

    RanzMarshallMassTransferCorrelation stagnant = correlation(Morphology.LIQUID_DROPLETS, 0.0,
        diffusivities(1.0e-8, 2.0e-8));
    assertEquals(0.0, stagnant.getReynoldsNumber(), 0.0);
    for (Map.Entry<String, Double> entry : stagnant.getComponentDiffusivitiesM2S().entrySet()) {
      assertEquals(2.0, stagnant.getSherwoodNumbers().get(entry.getKey()), 0.0);
      assertEquals(1.0e-6 / (12.0 * entry.getValue()), stagnant.getComponentRelaxationTimesS().get(entry.getKey()),
          1e-12);
    }
  }

  /** Faster slip and larger diffusivity reduce the external-film transfer time monotonically. */
  @Test
  void nearbyPropertySweepHasPhysicalTrends() {
    RanzMarshallMassTransferCorrelation stagnant = correlation(Morphology.GAS_BUBBLES, 0.0,
        diffusivities(1.0e-8, 1.0e-8));
    RanzMarshallMassTransferCorrelation moving = correlation(Morphology.GAS_BUBBLES, 0.010,
        diffusivities(1.0e-8, 1.0e-8));
    RanzMarshallMassTransferCorrelation fasterDiffusion = correlation(Morphology.GAS_BUBBLES, 0.010,
        diffusivities(2.0e-8, 2.0e-8));
    assertTrue(
        moving.getComponentRelaxationTimesS().get("methane") < stagnant.getComponentRelaxationTimesS().get("methane"));
    assertTrue(fasterDiffusion.getComponentRelaxationTimesS().get("methane") < moving.getComponentRelaxationTimesS()
        .get("methane"));
  }

  /** Inputs are explicit and invalid or out-of-range cases fail without clamping. */
  @Test
  void validatesInputsAndFailsClosedAtRangeBoundaries() {
    assertThrows(IllegalArgumentException.class, () -> correlation(null, 0.010, diffusivities(1.0e-8, 2.0e-8)));
    assertThrows(IllegalArgumentException.class, () -> new RanzMarshallMassTransferCorrelation(Morphology.GAS_BUBBLES,
        0.0, 800.0, 1.0e-3, 0.010, diffusivities(1.0e-8, 2.0e-8), "source"));
    assertThrows(IllegalArgumentException.class,
        () -> correlation(Morphology.GAS_BUBBLES, -0.010, diffusivities(1.0e-8, 2.0e-8)));
    assertThrows(IllegalArgumentException.class,
        () -> correlation(Morphology.GAS_BUBBLES, 0.010, diffusivities(0.0, 2.0e-8)));

    double reynolds200Velocity = 200.0 * 1.0e-3 / (800.0 * 1.0e-3);
    ReleaseFlowResult reynoldsBoundary = model(
        correlation(Morphology.GAS_BUBBLES, reynolds200Velocity, diffusivities(1.0e-8, 2.0e-8)))
        .calculate(request(flashingFluid(0.10)));
    assertEquals(Status.UNSUPPORTED, reynoldsBoundary.getStatus());
    assertEquals("RANZ_MARSHALL_RANGE_UNSUPPORTED", reynoldsBoundary.getDiagnostics().get(0).getCode());

    double schmidt250Diffusivity = 1.0e-3 / (800.0 * 250.0);
    ReleaseFlowResult schmidtBoundary = model(
        correlation(Morphology.GAS_BUBBLES, 0.010, diffusivities(schmidt250Diffusivity, 2.0e-8)))
        .calculate(request(flashingFluid(0.10)));
    assertEquals(Status.UNSUPPORTED, schmidtBoundary.getStatus());
    assertTrue(schmidtBoundary.getDiagnostics().get(0).getMessage().contains("methane"));

    ReleaseFlowResult dropletHydrodynamics = model(
        correlation(Morphology.LIQUID_DROPLETS, 0.010, diffusivities(1.0e-8, 2.0e-8)))
        .calculate(request(flashingFluid(0.10)));
    assertEquals(Status.UNSUPPORTED, dropletHydrodynamics.getStatus());
    assertEquals("LIQUID_DROPLET_HYDRODYNAMICS_UNSUPPORTED", dropletHydrodynamics.getDiagnostics().get(0).getCode());

    Map<String, Double> incomplete = new TreeMap<String, Double>();
    incomplete.put("methane", 1.0e-8);
    ReleaseFlowResult missingComponent = model(correlation(Morphology.GAS_BUBBLES, 0.010, incomplete))
        .calculate(request(flashingFluid(0.10)));
    assertEquals(Status.INVALID, missingComponent.getStatus());
    assertEquals("RANZ_MARSHALL_COMPONENT_BASIS_INVALID", missingComponent.getDiagnostics().get(0).getCode());
  }

  /** The predicted times drive component-selective transfer with exact conservation. */
  @Test
  void couplesPredictedTimesWithComponentAndEnergyClosure() {
    SystemInterface input = flashingFluid(0.10);
    RanzMarshallMassTransferCorrelation transfer = correlation(Morphology.GAS_BUBBLES, 0.010,
        diffusivities(1.0e-8, 2.0e-8));
    RanzMarshallFiniteRateReleaseModel releaseModel = model(transfer);
    ReleaseFlowResult result = releaseModel.calculate(request(input));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
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
    assertTrue(result.getDiagnostics().stream()
        .anyMatch(diagnostic -> "RANZ_MARSHALL_COMPONENT_TRANSFER".equals(diagnostic.getCode())));
    assertTrue(releaseModel.getEvidence().hasIndependentEvidence());
    assertTrue(releaseModel.getEvidence().getLimitations().contains("NO_EXPERIMENTAL_QUALIFICATION"));
  }

  /** Equal diffusivities recover the existing uniform component-relaxation endpoint. */
  @Test
  void equalDiffusivitiesRecoverUniformComponentRelaxation() {
    SystemInterface input = flashingFluid(0.10);
    RanzMarshallMassTransferCorrelation transfer = correlation(Morphology.GAS_BUBBLES, 0.010,
        diffusivities(1.0e-8, 1.0e-8));
    RanzMarshallFiniteRateReleaseModel predictive = model(transfer);
    ComponentSelectiveFiniteRateReleaseModel declared = new ComponentSelectiveFiniteRateReleaseModel(0.020,
        transfer.getComponentRelaxationTimesS(), 0.25, "ranz-marshall-public-basis:v1");
    ReleaseFlowResult predictedResult = predictive.calculate(request(input));
    ReleaseFlowResult declaredResult = declared.calculate(request(input));
    assertTrue(predictedResult.isUsable(), () -> predictedResult.getDiagnostics().get(0).getMessage());
    assertTrue(declaredResult.isUsable(), () -> declaredResult.getDiagnostics().get(0).getMessage());
    assertEquals(declaredResult.getMassFlowRateKgS(), predictedResult.getMassFlowRateKgS(), 1e-12);
    assertEquals(declaredResult.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction(),
        predictedResult.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction(), 1e-12);
  }

  /** Correlation maps are defensive and repeated calculations are deterministic. */
  @Test
  void correlationIsImmutableAndCalculationIsDeterministic() {
    Map<String, Double> values = diffusivities(1.0e-8, 2.0e-8);
    RanzMarshallMassTransferCorrelation transfer = correlation(Morphology.GAS_BUBBLES, 0.010, values);
    values.put("methane", 9.0);
    assertEquals(1.0e-8, transfer.getComponentDiffusivitiesM2S().get("methane"), 0.0);
    assertNotSame(transfer.getComponentDiffusivitiesM2S(), transfer.getComponentDiffusivitiesM2S());
    assertThrows(UnsupportedOperationException.class,
        () -> transfer.getComponentDiffusivitiesM2S().put("methane", 2.0e-8));
    RanzMarshallFiniteRateReleaseModel releaseModel = model(transfer);
    ReleaseFlowResult first = releaseModel.calculate(request(flashingFluid(0.10)));
    ReleaseFlowResult second = releaseModel.calculate(request(flashingFluid(0.10)));
    assertTrue(first.isUsable());
    assertTrue(second.isUsable());
    assertEquals(first.getMassFlowRateKgS(), second.getMassFlowRateKgS(), 0.0);
    assertEquals(first.getStations().get(Station.THROAT_CRITICAL).getPhaseMassFractions(),
        second.getStations().get(Station.THROAT_CRITICAL).getPhaseMassFractions());
  }

  /** Deterministic schema output is retained through both process-container paths. */
  @Test
  void schemaCarriesCorrelationThroughBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid(0.10));
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      RanzMarshallFiniteRateReleaseModel releaseModel = model(
          correlation(Morphology.GAS_BUBBLES, 0.010, diffusivities(1.0e-8, 2.0e-8)));
      if (useModel) {
        ProcessModel processModel = new ProcessModel();
        processModel.add("area", process);
        session = new SourceTermSession("ranz-marshall-study", processModel);
        session.addSource("opening", "area", "feed", -1, 0.01, 0.62, 1.0e5, releaseModel);
      } else {
        session = new SourceTermSession("ranz-marshall-study", process);
        session.addSource("opening", "feed", 0.01, 0.62, 1.0e5, releaseModel);
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.1).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject json = JsonParser.parseString(frame.toJson()).getAsJsonObject();
        assertEquals("ranz-marshall-component-transfer-vertical-drift-flux-orifice",
            json.getAsJsonObject("model").get("id").getAsString());
        assertTrue(json.getAsJsonArray("diagnostics").toString().contains("ranz-marshall-public-basis:v1"));
        JsonObject throat = json.getAsJsonObject("source").getAsJsonObject("stations")
            .getAsJsonObject("THROAT_CRITICAL");
        assertTrue(throat.getAsJsonObject("phaseComponentMassFractions").has("GAS"));
        assertTrue(throat.has("phaseVelocities"));
      }
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("ranz-marshall-finite-rate.json"),
            dynamic.toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }
}
