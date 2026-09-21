package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.process.safety.release.ReleaseFlowResult.Status;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Analytical ideal-gas and exact legacy-screening adapter coverage. */
class ReleaseFlowAdapterTest extends neqsim.NeqSimTest {
  private static final double GAS_CONSTANT_J_MOL_K = 8.31446261815324;

  private SystemInterface methane(double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(300.0, pressureBar);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  @Test
  void idealGasMatchesAnalyticalChokedEquationAndDeclaresStations() throws Exception {
    SystemInterface input = methane(5.0);
    double initialPressure = input.getPressure();
    double initialTemperature = input.getTemperature();
    ReleaseFlowRequest request = new ReleaseFlowRequest(input, 0.01, 0.75, 1.0e5);
    ReleaseFlowResult result = new IdealGasReleaseModel().calculate(request);

    assertTrue(result.isUsable());
    assertEquals(Status.VALID, result.getStatus());
    assertTrue(result.isChoked());
    SystemInterface reference = request.getFluid();
    new ThermodynamicOperations(reference).TPflash();
    reference.init(3);
    double gamma = reference.getGamma();
    double molarMass = reference.getMolarMass();
    double pressurePa = reference.getPressure() * 1.0e5;
    double flowFactor = Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
    double expected = request.getEffectiveAreaM2() * pressurePa
        * Math.sqrt(gamma * molarMass / (GAS_CONSTANT_J_MOL_K * reference.getTemperature())) * flowFactor;
    assertEquals(expected, result.getMassFlowRateKgS(), expected * 1.0e-12);

    ReleaseState throat = result.getStations().get(Station.THROAT_CRITICAL);
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    assertEquals(pressurePa * criticalRatio, throat.getPressurePa(), 1.0e-8);
    assertEquals(1.0, throat.getGasMassFraction(), 0.0);
    assertEquals(1.0, throat.getPhaseMassFractions().values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-12);
    assertEquals(throat.getVelocityMs(), result.getThroatSoundSpeedMs(), 1.0e-10);
    assertTrue(result.getStations().containsKey(Station.AMBIENT_EXPANDED));
    assertEquals(initialPressure, input.getPressure(), 0.0);
    assertEquals(initialTemperature, input.getTemperature(), 0.0);
    SourceTermFrame frame = SourceTermFrame.calculated("adapter-scenario", "ideal-gas-source",
        UUID.fromString("00000000-0000-0000-0000-000000000101"), 0L, 0.0, Instant.parse("2026-09-21T00:00:00Z"),
        request, result, Collections.<String, String>emptyMap());
    writeFixture("adapter-ideal-gas.json", frame.toJson());
  }

  @Test
  void idealGasIsContinuousAtCriticalRatioAndHandlesNoFlow() {
    SystemInterface reference = methane(5.0);
    new ThermodynamicOperations(reference).TPflash();
    reference.init(3);
    double pressurePa = reference.getPressure() * 1.0e5;
    double gamma = reference.getGamma();
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    IdealGasReleaseModel model = new IdealGasReleaseModel();
    ReleaseFlowResult choked = model
        .calculate(new ReleaseFlowRequest(reference, 0.01, 0.7, pressurePa * criticalRatio * (1.0 - 1.0e-8)));
    ReleaseFlowResult unchoked = model
        .calculate(new ReleaseFlowRequest(reference, 0.01, 0.7, pressurePa * criticalRatio * (1.0 + 1.0e-8)));

    assertTrue(choked.isChoked());
    assertFalse(unchoked.isChoked());
    assertEquals(choked.getMassFlowRateKgS(), unchoked.getMassFlowRateKgS(), choked.getMassFlowRateKgS() * 1.0e-10);

    ReleaseFlowResult zero = model.calculate(new ReleaseFlowRequest(reference, 0.01, 0.7, pressurePa * 1.01));
    assertTrue(zero.isUsable());
    assertFalse(zero.isChoked());
    assertEquals(0.0, zero.getMassFlowRateKgS(), 0.0);
    assertTrue(hasDiagnostic(zero, "NO_FORWARD_FLOW"));
  }

  @Test
  void idealGasRejectsLiquidWithoutSubstitutingAnotherModel() {
    SystemInterface propane = new SystemSrkEos(230.0, 10.0);
    propane.addComponent("propane", 1.0);
    propane.setMixingRule("classic");
    ReleaseFlowResult result = new IdealGasReleaseModel().calculate(new ReleaseFlowRequest(propane, 0.01, 0.7, 1.0e5));

    assertEquals(Status.UNSUPPORTED, result.getStatus());
    assertFalse(result.isUsable());
    assertEquals("UNSUPPORTED_IDEAL_GAS_REGIME", result.getDiagnostics().get(0).getCode());
    assertThrows(IllegalStateException.class, result::getMassFlowRateKgS);
  }

  @Test
  void legacyAdapterExactlyMatchesScalarPathAndMarksScreening() throws Exception {
    SystemInterface input = methane(5.0);
    ReleaseFlowRequest request = new ReleaseFlowRequest(input, 0.013, 0.62, 1.01325e5);
    LeakModel legacy = LeakModel.builder().fluid(input).holeDiameter(request.getDiameterM())
        .dischargeCoefficient(request.getDischargeCoefficient()).backPressure(request.getBackPressurePa()).build();
    double expected = legacy.calculateMassFlowRate(input);

    ReleaseFlowResult result = new LegacyScreeningReleaseModel().calculate(request);
    assertEquals(Status.VALID_WITH_WARNINGS, result.getStatus());
    assertEquals(expected, result.getMassFlowRateKgS(), 0.0);
    assertTrue(hasDiagnostic(result, "SCREENING_ONLY"));
    assertTrue(hasDiagnostic(result, "UNRESOLVED_STATIONS"));
    assertTrue(hasDiagnostic(result, "LEGACY_FALLBACK_POLICY"));
    assertEquals(result.getStations().get(Station.UPSTREAM_STAGNATION).getPressurePa(),
        result.getStations().get(Station.THROAT_CRITICAL).getPressurePa(), 0.0);

    SourceTermFrame frame = SourceTermFrame.calculated("adapter-scenario", "legacy-screening-source",
        UUID.fromString("00000000-0000-0000-0000-000000000102"), 0L, 0.0, Instant.parse("2026-09-21T00:00:00Z"),
        request, result, Collections.<String, String>emptyMap());
    assertTrue(frame.toJson().contains("\"status\":\"VALID_WITH_WARNINGS\""));
    assertTrue(frame.toJson().contains("\"id\":\"legacy-orifice-screening\""));
    assertTrue(frame.toJson().contains("\"code\":\"SCREENING_ONLY\""));
    writeFixture("adapter-legacy-screening.json", frame.toJson());
  }

  @Test
  void legacyAdapterMatchesSubcriticalAndNoFlowLimits() {
    SystemInterface input = methane(5.0);
    for (double backPressurePa : new double[] {4.8e5, 5.1e5}) {
      ReleaseFlowRequest request = new ReleaseFlowRequest(input, 0.013, 0.62, backPressurePa);
      LeakModel legacy = LeakModel.builder().fluid(input).holeDiameter(request.getDiameterM())
          .dischargeCoefficient(request.getDischargeCoefficient()).backPressure(backPressurePa).build();
      ReleaseFlowResult adapted = new LegacyScreeningReleaseModel().calculate(request);

      assertEquals(legacy.calculateMassFlowRate(input), adapted.getMassFlowRateKgS(), 0.0);
      assertEquals(Status.VALID_WITH_WARNINGS, adapted.getStatus());
      assertTrue(hasDiagnostic(adapted, "SCREENING_ONLY"));
    }
  }

  @Test
  void adaptersRemainSerializable() throws Exception {
    assertEquals("ideal-gas-isentropic-orifice", roundTrip(new IdealGasReleaseModel()).getModelId());
    assertEquals("legacy-orifice-screening", roundTrip(new LegacyScreeningReleaseModel()).getModelId());
  }

  private static boolean hasDiagnostic(ReleaseFlowResult result, String code) {
    for (Diagnostic diagnostic : result.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static ReleaseFlowModel roundTrip(ReleaseFlowModel model) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new ObjectOutputStream(bytes).writeObject(model);
    return (ReleaseFlowModel) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
  }

  private static void writeFixture(String name, String json) throws Exception {
    Path directory = Paths.get("target", "source-term-contract-fixtures");
    Files.createDirectories(directory);
    Files.write(directory.resolve(name), json.getBytes(StandardCharsets.UTF_8));
  }
}
