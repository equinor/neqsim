package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import neqsim.process.safety.release.PredictiveBubbleTransferZoneReleaseModel.Prediction;
import neqsim.process.safety.release.RanzMarshallMassTransferCorrelation.Morphology;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.process.safety.release.ReleaseFlowResult.Status;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/**
 * Predictive bubbly transfer-zone bounds, convergence, conservation and process-path coverage.
 *
 * @author esol
 * @version 1.0
 */
class PredictiveBubbleTransferZoneReleaseModelTest extends neqsim.NeqSimTest {
  /**
   * Creates a methane/heptane fluid that resolves one gas and one liquid phase at the opening.
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
   * Creates deterministic continuous-liquid diffusivities.
   *
   * @param methaneM2S methane diffusivity in m2/s
   * @param heptaneM2S n-heptane diffusivity in m2/s
   * @return component-name-sorted values
   */
  private Map<String, Double> diffusivities(double methaneM2S, double heptaneM2S) {
    Map<String, Double> values = new TreeMap<String, Double>();
    values.put("methane", methaneM2S);
    values.put("n-heptane", heptaneM2S);
    return values;
  }

  /**
   * Creates the common transfer-zone model.
   *
   * @param verticalLengthM transfer-zone vertical contact length in m
   * @return configured predictive model
   */
  private PredictiveBubbleTransferZoneReleaseModel model(double verticalLengthM) {
    return new PredictiveBubbleTransferZoneReleaseModel(0.020, 0.10, verticalLengthM, 1.0e-3,
        diffusivities(1.0e-8, 2.0e-8), "synthetic-transfer-zone:v1");
  }

  /** Predicted diameter and residence time satisfy every declared spherical-bubble bound. */
  @Test
  void predictsBoundedBubbleHydrodynamics() {
    ReleaseFlowRequest request = request(flashingFluid(0.10));
    ReleaseFlowResult drift = new DriftFluxHomogeneousEquilibriumReleaseModel(0.020).calculate(request);
    assertTrue(drift.isUsable(), () -> drift.getDiagnostics().get(0).getMessage());
    PredictiveBubbleTransferZoneReleaseModel model = model(0.50);
    Prediction prediction = model.predictHydrodynamics(drift.getStations().get(Station.THROAT_CRITICAL), request);

    assertTrue(prediction.sauterMeanDiameterM > 0.0);
    assertTrue(prediction.sauterMeanDiameterM <= request.getDiameterM());
    assertTrue(prediction.sauterMeanDiameterM <= model.getMaximumBubbleToHydraulicDiameter()
        * model.getTransferZoneHydraulicDiameterM());
    assertTrue(prediction.eotvosNumber <= model.getMaximumEotvosNumber() * (1.0 + 1.0e-12));
    assertTrue(prediction.weberNumber <= model.getMaximumWeberNumber() * (1.0 + 1.0e-12));
    assertEquals(model.getTransferZoneVerticalLengthM() / prediction.gasVelocityMs, prediction.residenceTimeS, 1.0e-14);
    assertEquals(Math.abs(prediction.gasVelocityMs - prediction.liquidVelocityMs), prediction.relativeVelocityMs,
        1.0e-14);
  }

  /** Longer contact geometry increases residence and approaches the equilibrium phase split. */
  @Test
  void nearbyLengthSweepHasPhysicalTrend() {
    ReleaseFlowRequest request = request(flashingFluid(0.10));
    ReleaseFlowResult equilibrium = new DriftFluxHomogeneousEquilibriumReleaseModel(0.020).calculate(request);
    ReleaseFlowResult shortZone = model(0.25).calculate(request);
    ReleaseFlowResult longZone = model(1.00).calculate(request);
    assertTrue(equilibrium.isUsable());
    assertTrue(shortZone.isUsable(), () -> shortZone.getDiagnostics().get(0).getMessage());
    assertTrue(longZone.isUsable(), () -> longZone.getDiagnostics().get(0).getMessage());

    double equilibriumGas = equilibrium.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction();
    double shortGas = shortZone.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction();
    double longGas = longZone.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction();
    assertTrue(Math.abs(longGas - equilibriumGas) <= Math.abs(shortGas - equilibriumGas) + 1.0e-12);

    Prediction shortPrediction = model(0.25)
        .predictHydrodynamics(equilibrium.getStations().get(Station.THROAT_CRITICAL), request);
    Prediction longPrediction = model(1.00).predictHydrodynamics(equilibrium.getStations().get(Station.THROAT_CRITICAL),
        request);
    assertEquals(4.0 * shortPrediction.residenceTimeS, longPrediction.residenceTimeS, 1.0e-14);
  }

  /** The converged fixed point remains unchanged by one additional hydrodynamic/transfer refinement. */
  @Test
  void fixedPointRefinementIsConverged() {
    ReleaseFlowRequest request = request(flashingFluid(0.10));
    PredictiveBubbleTransferZoneReleaseModel model = model(0.50);
    ReleaseFlowResult converged = model.calculate(request);
    assertTrue(converged.isUsable(), () -> converged.getDiagnostics().get(0).getMessage());

    Prediction prediction = model.predictHydrodynamics(converged.getStations().get(Station.THROAT_CRITICAL), request);
    RanzMarshallMassTransferCorrelation correlation = new RanzMarshallMassTransferCorrelation(Morphology.GAS_BUBBLES,
        prediction.sauterMeanDiameterM, prediction.liquidDensityKgM3, model.getContinuousPhaseDynamicViscosityPaS(),
        prediction.relativeVelocityMs, model.getComponentDiffusivitiesM2S(), model.getParameterProvenance());
    ReleaseFlowResult refined = new RanzMarshallFiniteRateReleaseModel(model.getSurfaceTensionNm(),
        prediction.residenceTimeS, correlation).calculate(request);
    assertTrue(refined.isUsable(), () -> refined.getDiagnostics().get(0).getMessage());
    double relativeRateDifference = Math.abs(refined.getMassFlowRateKgS() - converged.getMassFlowRateKgS())
        / converged.getMassFlowRateKgS();
    assertTrue(relativeRateDifference <= model.getFixedPointRelativeTolerance());
    assertEquals(converged.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction(),
        refined.getStations().get(Station.THROAT_CRITICAL).getGasMassFraction(), 1.0e-10);
  }

  /** Converged transfer retains exact component reconstruction and stagnation-energy closure. */
  @Test
  void retainsComponentAndEnergyConservation() {
    SystemInterface input = flashingFluid(0.10);
    ReleaseFlowResult result = model(0.50).calculate(request(input));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    for (String component : upstream.getComponentMassFractions().keySet()) {
      double reconstructed = 0.0;
      for (Map.Entry<String, Double> phase : throat.getPhaseMassFractions().entrySet()) {
        reconstructed += phase.getValue() * throat.getPhaseComponentMassFractions().get(phase.getKey()).get(component);
      }
      assertEquals(upstream.getComponentMassFractions().get(component), reconstructed, 1.0e-12);
    }
    double kineticEnergyJkg = 0.0;
    for (Map.Entry<String, Double> phase : throat.getPhaseMassFractions().entrySet()) {
      double velocityMs = throat.getPhaseVelocitiesMs().get(phase.getKey());
      kineticEnergyJkg += 0.5 * phase.getValue() * velocityMs * velocityMs;
    }
    assertEquals(upstream.getEnthalpyJkg(), throat.getEnthalpyJkg() + kineticEnergyJkg, 1.0e-6);
    assertEquals(30.0, input.getPressure(), 0.0);
    assertEquals(300.0, input.getTemperature(), 0.0);
    assertTrue(result.getEvidence().hasIndependentEvidence());
    assertTrue(result.getEvidence().getLimitations().contains("NO_EXPERIMENTAL_QUALIFICATION"));
  }

  /** Invalid inputs and out-of-range Ranz-Marshall properties fail without defaults or clamping. */
  @Test
  void validatesInputsAndFailsClosed() {
    assertThrows(IllegalArgumentException.class, () -> new PredictiveBubbleTransferZoneReleaseModel(0.0, 0.10, 0.50,
        1.0e-3, diffusivities(1.0e-8, 2.0e-8), "source"));
    assertThrows(IllegalArgumentException.class, () -> new PredictiveBubbleTransferZoneReleaseModel(0.020, 0.0, 0.50,
        1.0e-3, diffusivities(1.0e-8, 2.0e-8), "source"));
    assertThrows(IllegalArgumentException.class, () -> new PredictiveBubbleTransferZoneReleaseModel(0.020, 0.10, 0.0,
        1.0e-3, diffusivities(1.0e-8, 2.0e-8), "source"));

    PredictiveBubbleTransferZoneReleaseModel outsideSchmidt = new PredictiveBubbleTransferZoneReleaseModel(0.020, 0.10,
        0.50, 1.0e-3, diffusivities(1.0e-10, 2.0e-10), "outside-range");
    ReleaseFlowResult result = outsideSchmidt.calculate(request(flashingFluid(0.10)));
    assertEquals(Status.UNSUPPORTED, result.getStatus());
    assertEquals("TRANSFER_ZONE_RANZ_MARSHALL_RANGE_UNSUPPORTED", result.getDiagnostics().get(0).getCode());
  }

  /** Configuration is immutable and repeated calculations are deterministic. */
  @Test
  void configurationIsImmutableAndCalculationIsDeterministic() {
    Map<String, Double> values = diffusivities(1.0e-8, 2.0e-8);
    PredictiveBubbleTransferZoneReleaseModel model = new PredictiveBubbleTransferZoneReleaseModel(0.020, 0.10, 0.50,
        1.0e-3, values, "synthetic-transfer-zone:v1");
    values.put("methane", 9.0);
    assertEquals(1.0e-8, model.getComponentDiffusivitiesM2S().get("methane"), 0.0);
    assertNotSame(model.getComponentDiffusivitiesM2S(), model.getComponentDiffusivitiesM2S());
    assertThrows(UnsupportedOperationException.class,
        () -> model.getComponentDiffusivitiesM2S().put("methane", 2.0e-8));

    ReleaseFlowResult first = model.calculate(request(flashingFluid(0.10)));
    ReleaseFlowResult second = model.calculate(request(flashingFluid(0.10)));
    assertTrue(first.isUsable(), () -> first.getDiagnostics().get(0).getMessage());
    assertTrue(second.isUsable(), () -> second.getDiagnostics().get(0).getMessage());
    assertEquals(first.getMassFlowRateKgS(), second.getMassFlowRateKgS(), 0.0);
    assertEquals(first.getStations().get(Station.THROAT_CRITICAL).getPhaseMassFractions(),
        second.getStations().get(Station.THROAT_CRITICAL).getPhaseMassFractions());
  }

  /** Deterministic schema output is retained through both process-container paths. */
  @Test
  void schemaCarriesPredictionThroughBothProcessContainers() throws Exception {
    for (boolean useModel : new boolean[] {false, true}) {
      Stream feed = new Stream("feed", flashingFluid(0.10));
      feed.setFlowRate(1.0, "kg/hr");
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      SourceTermSession session;
      PredictiveBubbleTransferZoneReleaseModel releaseModel = model(0.50);
      if (useModel) {
        ProcessModel processModel = new ProcessModel();
        processModel.add("area", process);
        session = new SourceTermSession("predictive-transfer-zone-study", processModel);
        session.addSource("opening", "area", "feed", -1, 0.01, 0.62, 1.0e5, releaseModel);
      } else {
        session = new SourceTermSession("predictive-transfer-zone-study", process);
        session.addSource("opening", "feed", 0.01, 0.62, 1.0e5, releaseModel);
      }
      SourceTermFrame steady = session.runSteadyState().get(0);
      SourceTermFrame dynamic = session.step(0.1).get(0);
      for (SourceTermFrame frame : new SourceTermFrame[] {steady, dynamic}) {
        SourceTermFrame.verifyEnvelope(frame.toJson());
        JsonObject json = JsonParser.parseString(frame.toJson()).getAsJsonObject();
        assertEquals("predictive-bubble-transfer-zone-vertical-drift-flux-orifice",
            json.getAsJsonObject("model").get("id").getAsString());
        assertTrue(json.getAsJsonArray("diagnostics").toString().contains("PREDICTIVE_BUBBLE_TRANSFER_ZONE"));
        assertTrue(json.getAsJsonArray("diagnostics").toString().contains("synthetic-transfer-zone:v1"));
        JsonObject throat = json.getAsJsonObject("source").getAsJsonObject("stations")
            .getAsJsonObject("THROAT_CRITICAL");
        assertTrue(throat.getAsJsonObject("phaseComponentMassFractions").has("GAS"));
        assertTrue(throat.has("phaseVelocities"));
      }
      if (!useModel) {
        Path directory = Paths.get("target", "source-term-contract-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("predictive-bubble-transfer-zone.json"),
            dynamic.toJson().getBytes(StandardCharsets.UTF_8));
      }
    }
  }
}
