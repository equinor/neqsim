package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Reproduces the dense-CO2 restriction cases reported by Hammer et al. (2022). */
class HomogeneousEquilibriumDenseCo2BenchmarkTest extends neqsim.NeqSimTest {
  private static final double RESTRICTION_SENSITIVITY_BACK_PRESSURE_PA = 600000.0;
  private static final double[] RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA = {600000.0, 700000.0, 800000.0};
  private static final double RESTRICTION_RATE_RELATIVE_TOLERANCE = 2.0e-8;
  // GERG pressure closure affects the last digits of the restriction rates and derived error statistics.
  private static final double SUMMARY_RELATIVE_ERROR_TOLERANCE = 1.0e-9;
  private static final double SUMMARY_UNCERTAINTY_MULTIPLE_TOLERANCE = 1.0e-8;
  private static final double[] EXPECTED_SOLID_MASS_FRACTIONS = {0.41238620556823946, 0.41116003185166017,
      0.40963012688431566, 0.40993911095569924, 0.41358726185580075, 0.4158684359820324};
  private static final double EXPECTED_AMBIENT_SUBLIMATION_TEMPERATURE_K = 194.65411168766354;

  /**
   * Exercises the opt-in equilibrium dry-ice ambient model against every retained Hammer case.
   *
   * <p>
   * The experiment measured restriction rate, not ambient solid fraction. The retained solid fractions therefore verify
   * deterministic entropy and energy closure only; the adverse rate errors remain explicit and unqualified.
   *
   * @throws Exception when the dataset or receipt cannot be read or written
   */
  @Test
  void equilibriumDryIceAmbientClosesHammerCasesWithoutClaimingQualification() throws Exception {
    List<Benchmark> benchmarks = Benchmark.readAll();
    EquilibriumSolidCo2ReleaseModel model = new EquilibriumSolidCo2ReleaseModel();
    StringBuilder receipt = new StringBuilder("caseId,geometry,modelVersion,measuredMassFlow_kg_s,"
        + "absoluteUncertainty_kg_s,actualMassFlow_kg_s,relativeError,ambientPressure_Pa,"
        + "sublimationTemperature_K,gasMassFraction,solidMassFraction,ambientVelocity_m_s,"
        + "entropyResidual_J_kg_K,stagnationEnergyResidual_J_kg,evidenceLevel\n");
    double absoluteRelativeErrorSum = 0.0;
    double signedRelativeErrorSum = 0.0;
    double squaredRelativeErrorSum = 0.0;
    double maximumAbsoluteRelativeError = 0.0;
    double maximumUncertaintyMultiple = 0.0;
    int withinReportedUncertainty = 0;
    for (int index = 0; index < benchmarks.size(); index++) {
      Benchmark benchmark = benchmarks.get(index);
      SystemInterface fluid = benchmark.fluid();
      ReleaseFlowRequest request = benchmark.request(fluid, benchmark.backPressurePa);
      ReleaseFlowResult result = model.calculate(request);
      assertTrue(result.isUsable(), result.getDiagnostics().get(0).getMessage());
      assertTrue(result.isChoked());
      assertEquals(model.getModelId(), result.getModelId());
      assertEquals(model.getModelVersion(), result.getModelVersion());
      assertTrue(result.getEvidence().hasIndependentEvidence());
      assertTrue(result.getEvidence().getLimitations().contains("NO_ENGINEERING_QUALIFICATION"));
      assertTrue(result.getDiagnostics().stream().anyMatch(item -> "SOLID_MODEL_ASSUMPTIONS".equals(item.getCode())));
      assertFalse(result.getDiagnostics().stream().anyMatch(item -> "SOLID_RISK_ASSESSED".equals(item.getCode())
          && item.getMessage().startsWith(Station.AMBIENT_EXPANDED.name() + ":")));

      ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
      ReleaseState ambient = result.getStations().get(Station.AMBIENT_EXPANDED);
      assertEquals(benchmark.backPressurePa, ambient.getPressurePa(), 1.0e-8);
      assertEquals(EXPECTED_AMBIENT_SUBLIMATION_TEMPERATURE_K, ambient.getTemperatureK(), 1.0e-10);
      assertEquals(EXPECTED_SOLID_MASS_FRACTIONS[index], ambient.getPhaseMassFractions().get(PhaseType.SOLID.name()),
          1.0e-7);
      assertEquals(1.0, ambient.getPhaseMassFractions().values().stream().mapToDouble(Double::doubleValue).sum(),
          1.0e-12);
      assertEquals(1.0, ambient.getPhaseComponentMassFractions().get(PhaseType.GAS.name()).get("CO2"), 0.0);
      assertEquals(1.0, ambient.getPhaseComponentMassFractions().get(PhaseType.SOLID.name()).get("CO2"), 0.0);
      assertTrue(ambient.getPhaseDensitiesKgM3().get(PhaseType.GAS.name()) > 0.0);
      assertTrue(ambient.getPhaseDensitiesKgM3().get(PhaseType.SOLID.name()) > 1000.0);
      double entropyResidualJkgK = ambient.getEntropyJkgK() - upstream.getEntropyJkgK();
      double stagnationEnergyResidualJkg = ambient.getEnthalpyJkg()
          + 0.5 * ambient.getVelocityMs() * ambient.getVelocityMs() - upstream.getEnthalpyJkg();
      assertEquals(0.0, entropyResidualJkgK, 1.0e-9);
      assertEquals(0.0, stagnationEnergyResidualJkg, 1.0e-8);
      assertEquals(benchmark.initialPressurePa / 1.0e5, fluid.getPressure(), 0.0);
      assertEquals(benchmark.initialTemperatureK, fluid.getTemperature(), 0.0);

      double actualMassFlowKgS = result.getMassFlowRateKgS();
      double relativeError = (actualMassFlowKgS - benchmark.measuredMassFlowKgS) / benchmark.measuredMassFlowKgS;
      double absoluteRelativeError = Math.abs(relativeError);
      double uncertaintyMultiple = Math.abs(actualMassFlowKgS - benchmark.measuredMassFlowKgS)
          / benchmark.absoluteUncertaintyKgS;
      absoluteRelativeErrorSum += absoluteRelativeError;
      signedRelativeErrorSum += relativeError;
      squaredRelativeErrorSum += relativeError * relativeError;
      maximumAbsoluteRelativeError = Math.max(maximumAbsoluteRelativeError, absoluteRelativeError);
      maximumUncertaintyMultiple = Math.max(maximumUncertaintyMultiple, uncertaintyMultiple);
      if (uncertaintyMultiple <= 1.0) {
        withinReportedUncertainty++;
      }

      SourceTermFrame frame = SourceTermFrame.calculated("dense-co2-solid-ambient", benchmark.caseId,
          UUID.nameUUIDFromBytes((benchmark.caseId + "-solid").getBytes(StandardCharsets.UTF_8)), 0, 0.0,
          Instant.parse("2026-11-26T00:00:00Z"), request, result,
          Collections.singletonMap("benchmarkCase", benchmark.caseId));
      SourceTermFrame.verifyEnvelope(frame.toJson());
      JsonObject payload = JsonParser.parseString(frame.toJson()).getAsJsonObject();
      assertTrue(payload.get("status").getAsString().startsWith("VALID"));
      assertEquals("UNQUALIFIED", payload.getAsJsonObject("model").get("evidenceLevel").getAsString());
      JsonObject ambientPayload = payload.getAsJsonObject("source").getAsJsonObject("stations")
          .getAsJsonObject(Station.AMBIENT_EXPANDED.name());
      assertEquals(EXPECTED_SOLID_MASS_FRACTIONS[index],
          ambientPayload.getAsJsonObject("phaseMassFractions").get(PhaseType.SOLID.name()).getAsDouble(), 1.0e-7);
      assertEquals(1.0, ambientPayload.getAsJsonObject("phaseComponentMassFractions")
          .getAsJsonObject(PhaseType.SOLID.name()).get("CO2").getAsDouble(), 0.0);

      receipt.append(benchmark.caseId).append(',').append(benchmark.geometry).append(',')
          .append(result.getModelVersion()).append(',').append(benchmark.measuredMassFlowKgS).append(',')
          .append(benchmark.absoluteUncertaintyKgS).append(',').append(actualMassFlowKgS).append(',')
          .append(relativeError).append(',').append(ambient.getPressurePa()).append(',')
          .append(ambient.getTemperatureK()).append(',')
          .append(ambient.getPhaseMassFractions().get(PhaseType.GAS.name())).append(',')
          .append(ambient.getPhaseMassFractions().get(PhaseType.SOLID.name())).append(',')
          .append(ambient.getVelocityMs()).append(',').append(entropyResidualJkgK).append(',')
          .append(stagnationEnergyResidualJkg).append(",UNQUALIFIED\n");
    }
    double count = benchmarks.size();
    assertEquals(0.1938602080317141, absoluteRelativeErrorSum / count, 2.0e-8);
    assertEquals(0.09858475470227625, signedRelativeErrorSum / count, 2.0e-8);
    assertEquals(0.21191467226918664, Math.sqrt(squaredRelativeErrorSum / count), 2.0e-8);
    assertEquals(0.3425940889197125, maximumAbsoluteRelativeError, 2.0e-8);
    assertEquals(10.269665665474237, maximumUncertaintyMultiple, 2.0e-7);
    assertEquals(1, withinReportedUncertainty);
    Path outputDirectory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(outputDirectory);
    Files.write(outputDirectory.resolve("hammer-dense-co2-solid-ambient-2022-receipt.csv"),
        receipt.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Verifies receiving-pressure continuity and fail-closed applicability boundaries.
   *
   * @throws Exception when the retained benchmark dataset cannot be read
   */
  @Test
  void equilibriumDryIceModelChecksNearbyCasesAndRejectsUnsupportedInputs() throws Exception {
    Benchmark benchmark = Benchmark.readAll().get(0);
    EquilibriumSolidCo2ReleaseModel model = new EquilibriumSolidCo2ReleaseModel();
    double[] pressuresPa = {90000.0, 101325.0, 110000.0};
    double previousTemperatureK = 0.0;
    double referenceRateKgS = Double.NaN;
    for (double pressurePa : pressuresPa) {
      ReleaseFlowResult result = model.calculate(benchmark.request(benchmark.fluid(), pressurePa));
      assertTrue(result.isUsable(), result.getDiagnostics().get(0).getMessage());
      ReleaseState ambient = result.getStations().get(Station.AMBIENT_EXPANDED);
      assertTrue(ambient.getTemperatureK() > previousTemperatureK);
      assertTrue(ambient.getPhaseMassFractions().get(PhaseType.SOLID.name()) > 0.0);
      assertTrue(ambient.getPhaseMassFractions().get(PhaseType.SOLID.name()) < 1.0);
      if (Double.isNaN(referenceRateKgS)) {
        referenceRateKgS = result.getMassFlowRateKgS();
      } else {
        assertEquals(referenceRateKgS, result.getMassFlowRateKgS(), referenceRateKgS * 1.0e-12);
      }
      previousTemperatureK = ambient.getTemperatureK();
    }

    SystemInterface methane = new SystemSrkEos(300.0, 50.0);
    methane.addComponent("methane", 1.0);
    ReleaseFlowResult mixture = model.calculate(new ReleaseFlowRequest(methane, 0.01, 0.7, 101325.0));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, mixture.getStatus());
    assertFalse(mixture.isUsable());
    assertTrue(mixture.getStations().isEmpty());

    ReleaseFlowResult aboveTriple = model
        .calculate(benchmark.request(benchmark.fluid(), RESTRICTION_SENSITIVITY_BACK_PRESSURE_PA));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, aboveTriple.getStatus());
    assertTrue(aboveTriple.getDiagnostics().get(0).getMessage().contains("triple-point pressure"));

    ReleaseFlowResult pipe = model.calculate(new ReleaseFlowRequest(benchmark.fluid(), benchmark.diameterM,
        benchmark.contractionCoefficient, benchmark.backPressurePa, 10.0, 0.01));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, pipe.getStatus());
    assertTrue(pipe.getDiagnostics().get(0).getMessage().contains("Short-opening model"));
  }

  /**
   * Retains the six experimental cases and refuses rate errors for the unsupported solid-bearing expansion path.
   *
   * @throws Exception when the dataset or receipt cannot be read or written
   */
  @Test
  void publishedDenseCo2CasesFailClosedOnAmbientSolidRisk() throws Exception {
    List<Benchmark> benchmarks = Benchmark.readAll();
    assertEquals(6, benchmarks.size());
    StringBuilder receipt = new StringBuilder("caseId,geometry,modelVersion,withdrawalPhase,measuredMassFlow_kg_s,"
        + "absoluteUncertainty_kg_s,relativeUncertainty_percent,publishedHemMassFlux_kg_m2_s,"
        + "publishedHemRelativeDeviation_percent,restrictionSensitivityBackPressureMin_Pa,"
        + "restrictionSensitivityBackPressureMax_Pa,maximumRateRelativeDifference,actualMassFlow_kg_s,"
        + "relativeError,flowComparisonStatus," + "resultStatus,resultDiagnosticCodes,evidenceLevel\n");
    double absoluteRelativeErrorSum = 0.0;
    double signedRelativeErrorSum = 0.0;
    double squaredRelativeErrorSum = 0.0;
    double maximumAbsoluteRelativeError = 0.0;
    double maximumUncertaintyMultiple = 0.0;
    double maximumRateRelativeDifference = 0.0;
    int withinReportedUncertainty = 0;
    for (Benchmark benchmark : benchmarks) {
      SystemInterface fluid = benchmark.fluid();
      ReleaseFlowRequest request = benchmark.request(fluid, benchmark.backPressurePa);
      ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
      assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, result.getStatus());
      assertFalse(result.isUsable());
      assertTrue(result.getStations().isEmpty());
      assertEquals("SOLID_RISK", result.getDiagnostics().get(0).getCode());
      assertTrue(result.getDiagnostics().get(0).getMessage().contains("AMBIENT_EXPANDED"));
      assertTrue(result.getDiagnostics().get(0).getMessage().contains("CO2"));
      assertEquals(benchmark.initialPressurePa / 1.0e5, fluid.getPressure(), 0.0);
      assertEquals(benchmark.initialTemperatureK, fluid.getTemperature(), 0.0);

      ReleaseBenchmarkApplicability applicability = ReleaseBenchmarkApplicability.assess(result,
          benchmark.withdrawalPhase);
      assertEquals(ReleaseBenchmarkApplicability.Status.RESULT_UNUSABLE, applicability.getStatus());
      assertFalse(applicability.isComparable());
      assertTrue(Double.isNaN(applicability.getExpectedPhaseMassFraction()));
      assertTrue(applicability.getResolvedPhaseMassFractions().isEmpty());
      assertEquals(Collections.singletonList("SOLID_RISK"), applicability.getResultDiagnosticCodes());
      assertTrue(result.getEvidence().hasIndependentEvidence());
      assertTrue(result.getEvidence().getLimitations().contains("DENSE_CO2_AMBIENT_SOLID_BEARING_PATH_UNSUPPORTED"));

      ReleaseFlowResult[] restrictionResults = new ReleaseFlowResult[RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA.length];
      for (int index = 0; index < RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA.length; index++) {
        restrictionResults[index] = new HomogeneousEquilibriumReleaseModel()
            .calculate(benchmark.request(benchmark.fluid(), RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA[index]));
        assertTrue(restrictionResults[index].isUsable(),
            restrictionResults[index].getDiagnostics().get(0).getMessage());
        assertTrue(restrictionResults[index].isChoked());
        ReleaseBenchmarkApplicability restrictionApplicability = ReleaseBenchmarkApplicability
            .assess(restrictionResults[index], benchmark.withdrawalPhase);
        assertEquals(ReleaseBenchmarkApplicability.Status.COMPARABLE, restrictionApplicability.getStatus());
      }
      ReleaseFlowResult restrictionResult = restrictionResults[0];
      double actualMassFlowKgS = restrictionResult.getMassFlowRateKgS();
      double caseMaximumRateRelativeDifference = 0.0;
      for (int index = 1; index < restrictionResults.length; index++) {
        double rateRelativeDifference = Math.abs(restrictionResults[index].getMassFlowRateKgS() - actualMassFlowKgS)
            / actualMassFlowKgS;
        caseMaximumRateRelativeDifference = Math.max(caseMaximumRateRelativeDifference, rateRelativeDifference);
      }
      assertTrue(caseMaximumRateRelativeDifference <= RESTRICTION_RATE_RELATIVE_TOLERANCE,
          benchmark.caseId + " restriction-plane rate relative difference=" + caseMaximumRateRelativeDifference);
      maximumRateRelativeDifference = Math.max(maximumRateRelativeDifference, caseMaximumRateRelativeDifference);
      double relativeError = (actualMassFlowKgS - benchmark.measuredMassFlowKgS) / benchmark.measuredMassFlowKgS;
      double absoluteRelativeError = Math.abs(relativeError);
      double uncertaintyMultiple = Math.abs(actualMassFlowKgS - benchmark.measuredMassFlowKgS)
          / benchmark.absoluteUncertaintyKgS;
      absoluteRelativeErrorSum += absoluteRelativeError;
      signedRelativeErrorSum += relativeError;
      squaredRelativeErrorSum += relativeError * relativeError;
      maximumAbsoluteRelativeError = Math.max(maximumAbsoluteRelativeError, absoluteRelativeError);
      maximumUncertaintyMultiple = Math.max(maximumUncertaintyMultiple, uncertaintyMultiple);
      if (uncertaintyMultiple <= 1.0) {
        withinReportedUncertainty++;
      }
      assertTrue(Math.abs(relativeError) <= 0.35,
          benchmark.caseId + " restriction-plane relative error=" + relativeError);

      SourceTermFrame frame = SourceTermFrame.calculated("dense-co2-benchmark", benchmark.caseId,
          UUID.nameUUIDFromBytes(benchmark.caseId.getBytes(StandardCharsets.UTF_8)), 0, 0.0,
          Instant.parse("2026-11-18T00:00:00Z"), request, result,
          Collections.singletonMap("benchmarkCase", benchmark.caseId));
      SourceTermFrame.verifyEnvelope(frame.toJson());
      JsonObject payload = JsonParser.parseString(frame.toJson()).getAsJsonObject();
      assertEquals("UNSUPPORTED", payload.get("status").getAsString());
      assertFalse(payload.has("source"));
      assertEquals("UNQUALIFIED", payload.getAsJsonObject("model").get("evidenceLevel").getAsString());

      receipt.append(benchmark.caseId).append(',').append(benchmark.geometry).append(',')
          .append(result.getModelVersion()).append(',').append(benchmark.withdrawalPhase).append(',')
          .append(benchmark.measuredMassFlowKgS).append(',').append(benchmark.absoluteUncertaintyKgS).append(',')
          .append(benchmark.relativeUncertaintyPercent).append(',').append(benchmark.publishedHemMassFluxKgM2S)
          .append(',').append(benchmark.publishedHemRelativeDeviationPercent).append(',')
          .append(RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA[0]).append(',')
          .append(RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA[RESTRICTION_SENSITIVITY_BACK_PRESSURES_PA.length - 1])
          .append(',').append(caseMaximumRateRelativeDifference).append(',').append(actualMassFlowKgS).append(',')
          .append(relativeError).append(",COMPARABLE_RESTRICTION_PLANE_ONLY,").append(result.getStatus()).append(',')
          .append(applicability.getResultDiagnosticCodes()).append(",UNQUALIFIED\n");
    }
    Path outputDirectory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(outputDirectory);
    Files.write(outputDirectory.resolve("hammer-dense-co2-2022-receipt.csv"),
        receipt.toString().getBytes(StandardCharsets.UTF_8));
    double count = benchmarks.size();
    double meanAbsoluteRelativeError = absoluteRelativeErrorSum / count;
    double meanSignedRelativeError = signedRelativeErrorSum / count;
    double rootMeanSquaredRelativeError = Math.sqrt(squaredRelativeErrorSum / count);
    assertEquals(0.1938602080317141, meanAbsoluteRelativeError, SUMMARY_RELATIVE_ERROR_TOLERANCE);
    assertEquals(0.09858475470227625, meanSignedRelativeError, SUMMARY_RELATIVE_ERROR_TOLERANCE);
    assertEquals(0.21191467226918664, rootMeanSquaredRelativeError, SUMMARY_RELATIVE_ERROR_TOLERANCE);
    assertEquals(0.3425940889197125, maximumAbsoluteRelativeError, SUMMARY_RELATIVE_ERROR_TOLERANCE);
    assertEquals(10.269665665474237, maximumUncertaintyMultiple, SUMMARY_UNCERTAINTY_MULTIPLE_TOLERANCE);
    assertEquals(1, withinReportedUncertainty);
    String summary = "caseCount,meanAbsoluteRelativeError,meanSignedRelativeError,rootMeanSquaredRelativeError,"
        + "maximumAbsoluteRelativeError,maximumUncertaintyMultiple,withinReportedUncertainty,"
        + "maximumRateRelativeDifference,evidenceLevel\n" + benchmarks.size() + "," + meanAbsoluteRelativeError + ","
        + meanSignedRelativeError + "," + rootMeanSquaredRelativeError + "," + maximumAbsoluteRelativeError + ","
        + maximumUncertaintyMultiple + "," + withinReportedUncertainty + "," + maximumRateRelativeDifference
        + ",UNQUALIFIED\n";
    Files.write(outputDirectory.resolve("hammer-dense-co2-2022-summary.csv"), summary.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Shows that the published ambient path, rather than the dense inlet alone, triggers the fail-closed solid gate.
   *
   * @throws Exception when the dataset cannot be read
   */
  @Test
  void raisedBackPressureSeparatesDenseFluidSupportFromSolidBearingPath() throws Exception {
    Benchmark benchmark = Benchmark.readAll().get(5);
    SystemInterface fluid = benchmark.fluid();
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel()
        .calculate(benchmark.request(fluid, RESTRICTION_SENSITIVITY_BACK_PRESSURE_PA));
    assertTrue(result.isUsable(), result.getDiagnostics().get(0).getMessage());
    assertTrue(result.isChoked());
    assertEquals(4.836566429537901, result.getMassFlowRateKgS(), 1.0e-9);
    ReleaseBenchmarkApplicability applicability = ReleaseBenchmarkApplicability.assess(result,
        benchmark.withdrawalPhase);
    assertEquals(ReleaseBenchmarkApplicability.Status.COMPARABLE, applicability.getStatus());
    assertTrue(applicability.isComparable());
    assertEquals(1.0, applicability.getExpectedPhaseMassFraction(), 1.0e-12);
    assertFalse(applicability.getResolvedPhaseMassFractions().isEmpty());
    assertEquals(benchmark.initialPressurePa / 1.0e5, fluid.getPressure(), 0.0);
    assertEquals(benchmark.initialTemperatureK, fluid.getTemperature(), 0.0);
  }

  /** One immutable published benchmark row. */
  private static final class Benchmark {
    private final String caseId;
    private final String geometry;
    private final double diameterM;
    private final double contractionCoefficient;
    private final double initialPressurePa;
    private final double initialTemperatureK;
    private final double measuredMassFlowKgS;
    private final double absoluteUncertaintyKgS;
    private final double relativeUncertaintyPercent;
    private final double publishedHemMassFluxKgM2S;
    private final double publishedHemRelativeDeviationPercent;
    private final double backPressurePa;
    private final ReleaseBenchmarkApplicability.ExpectedPhase withdrawalPhase;

    /**
     * Parses one benchmark row.
     *
     * @param values CSV values
     */
    private Benchmark(String[] values) {
      caseId = values[0];
      geometry = values[1];
      diameterM = Double.parseDouble(values[2]);
      contractionCoefficient = Double.parseDouble(values[3]);
      initialPressurePa = Double.parseDouble(values[4]);
      initialTemperatureK = Double.parseDouble(values[5]);
      measuredMassFlowKgS = Double.parseDouble(values[6]);
      absoluteUncertaintyKgS = Double.parseDouble(values[7]);
      relativeUncertaintyPercent = Double.parseDouble(values[8]);
      publishedHemMassFluxKgM2S = Double.parseDouble(values[9]);
      publishedHemRelativeDeviationPercent = Double.parseDouble(values[10]);
      backPressurePa = Double.parseDouble(values[11]);
      withdrawalPhase = ReleaseBenchmarkApplicability.ExpectedPhase.valueOf(values[12]);
      if (!values[13].contains("10.1016/j.ijmultiphaseflow.2022.104201")) {
        throw new IllegalArgumentException("Hammer dense-CO2 benchmark provenance is required");
      }
    }

    /** @return fresh GERG-2008 pure-CO2 state at the published initial condition */
    private SystemInterface fluid() {
      SystemInterface fluid = new SystemGERG2008Eos(initialTemperatureK, initialPressurePa / 1.0e5);
      fluid.addComponent("CO2", 1.0);
      return fluid;
    }

    /**
     * Builds the explicit short-opening request.
     *
     * @param fluid caller-owned benchmark state
     * @param receiverPressurePa receiver absolute pressure in Pa
     * @return immutable request
     */
    private ReleaseFlowRequest request(SystemInterface fluid, double receiverPressurePa) {
      return new ReleaseFlowRequest(fluid, diameterM, contractionCoefficient, receiverPressurePa);
    }

    /**
     * Reads all retained benchmark rows.
     *
     * @return immutable benchmark list
     * @throws Exception when the dataset cannot be read
     */
    private static List<Benchmark> readAll() throws Exception {
      Path path = Paths.get("src", "test", "resources", "neqsim", "process", "safety", "release",
          "hammer-dense-co2-2022.csv");
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      assertEquals(7, lines.size());
      assertTrue(lines.get(0).contains("absoluteUncertaintyKgS"));
      List<Benchmark> benchmarks = new ArrayList<Benchmark>();
      for (int index = 1; index < lines.size(); index++) {
        benchmarks.add(new Benchmark(lines.get(index).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1)));
      }
      return Collections.unmodifiableList(benchmarks);
    }
  }
}
