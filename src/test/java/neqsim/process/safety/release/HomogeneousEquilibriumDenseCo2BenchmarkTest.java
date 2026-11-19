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
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;

/** Reproduces the dense-CO2 restriction cases reported by Hammer et al. (2022). */
class HomogeneousEquilibriumDenseCo2BenchmarkTest extends neqsim.NeqSimTest {
  private static final double RESTRICTION_SENSITIVITY_BACK_PRESSURE_PA = 600000.0;

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
        + "publishedHemRelativeDeviation_percent,restrictionSensitivityBackPressure_Pa,actualMassFlow_kg_s,"
        + "relativeError,flowComparisonStatus," + "resultStatus,resultDiagnosticCodes,evidenceLevel\n");
    double absoluteRelativeErrorSum = 0.0;
    double signedRelativeErrorSum = 0.0;
    double squaredRelativeErrorSum = 0.0;
    double maximumAbsoluteRelativeError = 0.0;
    double maximumUncertaintyMultiple = 0.0;
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

      ReleaseFlowResult restrictionResult = new HomogeneousEquilibriumReleaseModel()
          .calculate(benchmark.request(benchmark.fluid(), RESTRICTION_SENSITIVITY_BACK_PRESSURE_PA));
      assertTrue(restrictionResult.isUsable(), restrictionResult.getDiagnostics().get(0).getMessage());
      assertTrue(restrictionResult.isChoked());
      ReleaseBenchmarkApplicability restrictionApplicability = ReleaseBenchmarkApplicability.assess(restrictionResult,
          benchmark.withdrawalPhase);
      assertEquals(ReleaseBenchmarkApplicability.Status.COMPARABLE, restrictionApplicability.getStatus());
      double actualMassFlowKgS = restrictionResult.getMassFlowRateKgS();
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
          .append(RESTRICTION_SENSITIVITY_BACK_PRESSURE_PA).append(',').append(actualMassFlowKgS).append(',')
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
    assertEquals(0.1938602080317141, meanAbsoluteRelativeError, 1.0e-12);
    assertEquals(0.09858475470227625, meanSignedRelativeError, 1.0e-12);
    assertEquals(0.21191467226918664, rootMeanSquaredRelativeError, 1.0e-12);
    assertEquals(0.3425940889197125, maximumAbsoluteRelativeError, 1.0e-12);
    assertEquals(10.269665665474237, maximumUncertaintyMultiple, 1.0e-12);
    assertEquals(1, withinReportedUncertainty);
    String summary = "caseCount,meanAbsoluteRelativeError,meanSignedRelativeError,rootMeanSquaredRelativeError,"
        + "maximumAbsoluteRelativeError,maximumUncertaintyMultiple,withinReportedUncertainty,evidenceLevel\n"
        + benchmarks.size() + "," + meanAbsoluteRelativeError + "," + meanSignedRelativeError + ","
        + rootMeanSquaredRelativeError + "," + maximumAbsoluteRelativeError + "," + maximumUncertaintyMultiple + ","
        + withinReportedUncertainty + ",UNQUALIFIED\n";
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
