package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Incipient vapour-phase regressions and independent saturation-path numerical comparison. */
class ReleaseFlowFlashingTest extends neqsim.NeqSimTest {
  private SystemInterface liquid(double propane, double temperature, double moles) {
    SystemInterface fluid = new SystemSrkEos(temperature, 20.0);
    fluid.addComponent("propane", moles * propane);
    fluid.addComponent("n-butane", moles * (1.0 - propane));
    fluid.setMixingRule("classic");
    return fluid;
  }

  private ReleaseFlowResult release(SystemInterface fluid, double backPressure) {
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel()
        .calculate(new ReleaseFlowRequest(fluid, 0.01, 0.62, backPressure));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    for (ReleaseState state : result.getStations().values()) {
      assertEquals(upstream.getEntropyJkgK(), state.getEntropyJkgK(), 1e-5);
      assertEquals(upstream.getEnthalpyJkg(),
          state.getEnthalpyJkg() + 0.5 * state.getVelocityMs() * state.getVelocityMs(), 1e-5);
      assertEquals(1.0, state.getPhaseMassFractions().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
      assertEquals(upstream.getComponentMoleFractions(), state.getComponentMoleFractions());
      assertTrue(state.getDensityKgM3() > 0.0);
    }
    return result;
  }

  @Test
  void nearbyMixturesCloseAndRetainNumericalEvidence() throws Exception {
    List<String> rows = new ArrayList<String>();
    rows.add("propane_mole_fraction,temperature_K,rate_kg_s,throat_Pa,ambient_gas_mass_fraction,status");
    for (double propane : new double[] {0.75, 0.80, 0.85}) {
      for (double temperature : new double[] {299.0, 300.0, 301.0}) {
        SystemInterface fluid = liquid(propane, temperature, 1.0);
        ReleaseFlowResult result = release(fluid, 3e5);
        assertEquals(20.0, fluid.getPressure());
        assertEquals(temperature, fluid.getTemperature());
        assertEquals(1.0, fluid.getTotalNumberOfMoles());
        assertTrue(result.isChoked());
        double gasFraction = result.getStations().get(Station.AMBIENT_EXPANDED).getGasMassFraction();
        assertTrue(gasFraction > 0.0 && gasFraction < 1.0);
        rows.add(propane + "," + temperature + "," + result.getMassFlowRateKgS() + ","
            + result.getStations().get(Station.THROAT_CRITICAL).getPressurePa() + "," + gasFraction + ","
            + result.getStatus());
      }
    }
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("mixture-flashing.csv"), rows, StandardCharsets.UTF_8);
  }

  @Test
  void criticalPointAgreesWithIndependentSaturationPath() throws Exception {
    SystemInterface upstream = liquid(0.8, 300.0, 1.0);
    new ThermodynamicOperations(upstream).TPflash();
    upstream.init(3);
    ReleaseFlowResult result = release(upstream, 3e5);
    // Independent algorithm: move along the bubble-pressure curve until saturated-liquid
    // entropy equals upstream entropy. No HEM temperature root or pressure maximizer is reused.
    double low = 295.0;
    double high = 300.0;
    SystemInterface bubble = null;
    for (int iteration = 0; iteration < 40; iteration++) {
      double temperature = 0.5 * (low + high);
      bubble = upstream.clone();
      bubble.setTemperature(temperature);
      new ThermodynamicOperations(bubble).bubblePointPressureFlash(false);
      bubble.init(3);
      if (bubble.getEntropy("J/kgK") > upstream.getEntropy("J/kgK")) {
        high = temperature;
      } else {
        low = temperature;
      }
    }
    assertNotNull(bubble);
    assertEquals(upstream.getEntropy("J/kgK"), bubble.getEntropy("J/kgK"), 1e-6);
    double velocity = Math.sqrt(2.0 * (upstream.getEnthalpy("J/kg") - bubble.getEnthalpy("J/kg")));
    double referenceRate = 0.62 * Math.PI * 0.01 * 0.01 / 4.0 * bubble.getMass("kg") / bubble.getVolume("m3")
        * velocity;
    assertEquals(bubble.getPressure() * 1e5, result.getStations().get(Station.THROAT_CRITICAL).getPressurePa(), 2.0);
    assertEquals(referenceRate, result.getMassFlowRateKgS(), referenceRate * 1e-5);
    assertTrue(result.getDiagnostics().stream().anyMatch(d -> "INCIPIENT_PHASE_CONTINUATION".equals(d.getCode())));
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("mixture-saturation-reference.csv"), java.util.Arrays.asList(
        "reference_rate_kg_s,hem_rate_kg_s,reference_pressure_Pa,hem_pressure_Pa,evidence",
        referenceRate + "," + result.getMassFlowRateKgS() + "," + bubble.getPressure() * 1e5 + ","
            + result.getStations().get(Station.THROAT_CRITICAL).getPressurePa() + ",same-EOS-independent-algorithm"),
        StandardCharsets.UTF_8);
  }

  @Test
  void backpressureInventoryScaleAndSchemaRemainConsistent() throws Exception {
    ReleaseFlowResult reference = release(liquid(0.8, 300.0, 1.0), 3e5);
    for (double moles : new double[] {0.1, 10.0}) {
      assertEquals(reference.getMassFlowRateKgS(), release(liquid(0.8, 300.0, moles), 3e5).getMassFlowRateKgS(), 1e-5);
    }
    for (double back : new double[] {2.8e5, 3.2e5, 8.2e5}) {
      ReleaseFlowResult result = release(liquid(0.8, 300.0, 1.0), back);
      assertTrue(result.isChoked());
      assertEquals(reference.getMassFlowRateKgS(), result.getMassFlowRateKgS(), 1e-5);
    }
    ReleaseFlowResult highBack = release(liquid(0.8, 300.0, 1.0), 12e5);
    assertFalse(highBack.isChoked());
    assertTrue(highBack.getMassFlowRateKgS() < reference.getMassFlowRateKgS());
    ReleaseFlowRequest request = new ReleaseFlowRequest(liquid(0.8, 300.0, 1.0), 0.01, 0.62, 3e5);
    SourceTermFrame frame = SourceTermFrame.calculated("mixture-flashing", "synthetic/feed",
        UUID.fromString("00000000-0000-0000-0000-000000000386"), 0, 0.0, Instant.parse("2026-09-21T00:00:00Z"), request,
        reference, Collections.singletonMap("evidence", "numerical regression; not experimental qualification"));
    SourceTermFrame.verifyEnvelope(frame.toJson());
    assertTrue(frame.toJson().contains("UNQUALIFIED"));
    assertTrue(frame.toJson().contains("1.2.0"));
    Path directory = Paths.get("target", "source-term-contract-fixtures");
    Files.createDirectories(directory);
    Files.write(directory.resolve("mixture-flashing.json"), frame.toJson().getBytes(StandardCharsets.UTF_8));
  }
}
