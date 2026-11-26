package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Reproduces the well-mixed natural-gas/propane orifice series of Richardson et al. (2006). */
class HomogeneousEquilibriumRichardsonBenchmarkTest extends neqsim.NeqSimTest {
  private static final double RECEIVER_PRESSURE_PA = 100000.0;
  private static final double MAXIMUM_DISCHARGE_COEFFICIENT_DIFFERENCE = 0.10;
  private static final double MAXIMUM_UPSTREAM_LIQUID_FRACTION_DIFFERENCE = 0.25;

  /**
   * Compares the unity-coefficient HEM rate with the measured flow and published HEM coefficient.
   *
   * @throws Exception when the retained dataset or generated receipts cannot be read or written
   */
  @Test
  void publishedWellMixedSeriesRetainsIndependentMulticomponentEvidence() throws Exception {
    List<Benchmark> benchmarks = Benchmark.readAll();
    assertEquals(27, benchmarks.size());
    StringBuilder receipt = new StringBuilder("caseId,modelVersion,expectedUpstreamPhase,"
        + "resolvedUpstreamPhaseMassFractions,publishedUpstreamLiquidMassFraction,"
        + "neqsimUpstreamLiquidMassFraction,upstreamLiquidFractionDifference,measuredMassFlow_kg_s,"
        + "measurementRelativeUncertainty,publishedHemDischargeCoefficient,neqsimImpliedDischargeCoefficient,"
        + "dischargeCoefficientDifference,unityCoefficientMassFlow_kg_s,flowComparisonStatus,evidenceLevel,choked\n");
    double absoluteCoefficientDifferenceSum = 0.0;
    double maximumAbsoluteCoefficientDifference = 0.0;
    double maximumAbsoluteLiquidFractionDifference = 0.0;
    int comparableCases = 0;
    int withinMeasuredFlowUncertainty = 0;
    for (Benchmark benchmark : benchmarks) {
      SystemInterface fluid = benchmark.fluid();
      ReleaseFlowRequest request = new ReleaseFlowRequest(fluid, benchmark.diameterM, 1.0, RECEIVER_PRESSURE_PA);
      ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
      assertTrue(result.isUsable(), benchmark.caseId + ": " + result.getDiagnostics());
      assertTrue(result.isChoked(), benchmark.caseId);
      assertTrue(result.getEvidence().hasIndependentEvidence());
      assertEquals(benchmark.pressurePa / 1.0e5, fluid.getPressure(), 0.0);
      assertEquals(benchmark.temperatureK, fluid.getTemperature(), 0.0);

      ReleaseBenchmarkApplicability applicability = ReleaseBenchmarkApplicability.assess(result,
          benchmark.expectedUpstreamPhase);
      Map<String, Double> phases = applicability.getResolvedPhaseMassFractions();
      double actualLiquidFraction = phaseFraction(phases, "OIL") + phaseFraction(phases, "LIQUID")
          + phaseFraction(phases, "AQUEOUS");
      double liquidFractionDifference = actualLiquidFraction - benchmark.publishedUpstreamLiquidMassFraction;
      double impliedDischargeCoefficient = benchmark.measuredMassFlowKgS / result.getMassFlowRateKgS();
      double coefficientDifference = impliedDischargeCoefficient - benchmark.publishedHemDischargeCoefficient;
      String comparisonStatus = applicability.isComparable() ? "COMPARABLE" : applicability.getStatus().name();
      if (applicability.isComparable()) {
        comparableCases++;
        absoluteCoefficientDifferenceSum += Math.abs(coefficientDifference);
        maximumAbsoluteCoefficientDifference = Math.max(maximumAbsoluteCoefficientDifference,
            Math.abs(coefficientDifference));
        maximumAbsoluteLiquidFractionDifference = Math.max(maximumAbsoluteLiquidFractionDifference,
            Math.abs(liquidFractionDifference));
        assertTrue(Math.abs(coefficientDifference) <= MAXIMUM_DISCHARGE_COEFFICIENT_DIFFERENCE,
            benchmark.caseId + " discharge-coefficient difference=" + coefficientDifference);
        assertTrue(Math.abs(liquidFractionDifference) <= MAXIMUM_UPSTREAM_LIQUID_FRACTION_DIFFERENCE,
            benchmark.caseId + " upstream liquid-fraction difference=" + liquidFractionDifference);
        double reconstructedMassFlowKgS = result.getMassFlowRateKgS() * benchmark.publishedHemDischargeCoefficient;
        double relativeFlowError = Math.abs(reconstructedMassFlowKgS - benchmark.measuredMassFlowKgS)
            / benchmark.measuredMassFlowKgS;
        if (relativeFlowError <= benchmark.measurementRelativeUncertainty) {
          withinMeasuredFlowUncertainty++;
        }
      }
      receipt.append(benchmark.caseId).append(',').append(result.getModelVersion()).append(',')
          .append(benchmark.expectedUpstreamPhase).append(',').append('"').append(phases).append('"').append(',')
          .append(benchmark.publishedUpstreamLiquidMassFraction).append(',').append(actualLiquidFraction).append(',')
          .append(liquidFractionDifference).append(',').append(benchmark.measuredMassFlowKgS).append(',')
          .append(benchmark.measurementRelativeUncertainty).append(',')
          .append(benchmark.publishedHemDischargeCoefficient).append(',').append(impliedDischargeCoefficient)
          .append(',').append(coefficientDifference).append(',').append(result.getMassFlowRateKgS()).append(',')
          .append(comparisonStatus).append(",UNQUALIFIED,").append(result.isChoked()).append('\n');
    }
    assertEquals(26, comparableCases);
    double meanAbsoluteCoefficientDifference = absoluteCoefficientDifferenceSum / comparableCases;
    // The mean accumulates small flash differences across supported JDKs.
    assertEquals(0.035358313303078676, meanAbsoluteCoefficientDifference, 1.0e-10);
    assertEquals(0.09556420167548318, maximumAbsoluteCoefficientDifference, 1.0e-12);
    assertEquals(0.2460683265058093, maximumAbsoluteLiquidFractionDifference, 1.0e-12);
    assertEquals(0, withinMeasuredFlowUncertainty);
    Path outputDirectory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(outputDirectory);
    Files.write(outputDirectory.resolve("richardson-natural-gas-propane-2006-receipt.csv"),
        receipt.toString().getBytes(StandardCharsets.UTF_8));
    String summary = "caseCount,comparableCases,meanAbsoluteDischargeCoefficientDifference,"
        + "maximumAbsoluteDischargeCoefficientDifference,maximumAbsoluteUpstreamLiquidFractionDifference,"
        + "withinMeasuredFlowUncertainty,evidenceLevel\n" + benchmarks.size() + "," + comparableCases + ","
        + meanAbsoluteCoefficientDifference + "," + maximumAbsoluteCoefficientDifference + ","
        + maximumAbsoluteLiquidFractionDifference + "," + withinMeasuredFlowUncertainty + ",UNQUALIFIED\n";
    Files.write(outputDirectory.resolve("richardson-natural-gas-propane-2006-summary.csv"),
        summary.getBytes(StandardCharsets.UTF_8));
  }

  /** Confirms the retained benchmark can emit one deterministic schema-valid source frame. */
  @Test
  void representativeCaseProducesSchemaValidUnqualifiedFrame() throws Exception {
    Benchmark benchmark = Benchmark.readAll().get(10);
    ReleaseFlowRequest request = new ReleaseFlowRequest(benchmark.fluid(), benchmark.diameterM, 1.0,
        RECEIVER_PRESSURE_PA);
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
    assertTrue(result.isUsable(), result.getDiagnostics().toString());
    SourceTermFrame frame = SourceTermFrame.calculated("richardson-2006-benchmark", benchmark.caseId,
        UUID.fromString("00000000-0000-0000-0000-000000003860"), 0, 0.0, Instant.parse("2026-11-24T00:00:00Z"), request,
        result, Collections.singletonMap("benchmarkCase", benchmark.caseId));
    SourceTermFrame.verifyEnvelope(frame.toJson());
    JsonObject payload = JsonParser.parseString(frame.toJson()).getAsJsonObject();
    assertEquals("VALID", payload.get("status").getAsString());
    assertEquals("UNQUALIFIED", payload.getAsJsonObject("model").get("evidenceLevel").getAsString());
  }

  /**
   * Returns a named phase fraction or zero when the phase is absent.
   *
   * @param phases native phase-mass fractions
   * @param name phase name
   * @return retained mass fraction
   */
  private static double phaseFraction(Map<String, Double> phases, String name) {
    Double value = phases.get(name);
    return value == null ? 0.0 : value.doubleValue();
  }

  /** One immutable row from Richardson et al. (2006), Table 3. */
  private static final class Benchmark {
    private static final double METHANE_MOLAR_MASS_KG_MOL = 0.016043;
    private static final double ETHANE_MOLAR_MASS_KG_MOL = 0.030070;
    private static final double PROPANE_MOLAR_MASS_KG_MOL = 0.044097;
    private static final double N_BUTANE_MOLAR_MASS_KG_MOL = 0.058124;
    private static final double N_PENTANE_MOLAR_MASS_KG_MOL = 0.072151;

    private final String caseId;
    private final double diameterM;
    private final double pressurePa;
    private final double temperatureK;
    private final double propaneFeedMassFraction;
    private final double measuredMassFlowKgS;
    private final double publishedUpstreamLiquidMassFraction;
    private final double publishedThroatLiquidMassFraction;
    private final double publishedHemDischargeCoefficient;
    private final double measurementRelativeUncertainty;
    private final ReleaseBenchmarkApplicability.ExpectedPhase expectedUpstreamPhase;

    /**
     * Parses and verifies one retained row.
     *
     * @param values CSV columns
     */
    private Benchmark(String[] values) {
      caseId = values[0];
      diameterM = Double.parseDouble(values[1]);
      pressurePa = Double.parseDouble(values[2]);
      temperatureK = Double.parseDouble(values[3]);
      propaneFeedMassFraction = Double.parseDouble(values[4]);
      measuredMassFlowKgS = Double.parseDouble(values[5]);
      publishedUpstreamLiquidMassFraction = Double.parseDouble(values[6]);
      publishedThroatLiquidMassFraction = Double.parseDouble(values[7]);
      publishedHemDischargeCoefficient = Double.parseDouble(values[8]);
      measurementRelativeUncertainty = Double.parseDouble(values[9]);
      expectedUpstreamPhase = ReleaseBenchmarkApplicability.ExpectedPhase.valueOf(values[10]);
      if (!values[11].contains("10.1205/psep.04298")) {
        throw new IllegalArgumentException("Richardson benchmark DOI provenance is required");
      }
      if (publishedUpstreamLiquidMassFraction < 0.0 || publishedUpstreamLiquidMassFraction > 1.0
          || publishedThroatLiquidMassFraction < 0.0 || publishedThroatLiquidMassFraction > 1.0) {
        throw new IllegalArgumentException("Published liquid mass fractions must be in [0, 1]");
      }
    }

    /**
     * Builds the average Table 1 feed using normal-paraffin proxies for the reported carbon-number groups.
     *
     * @return independent fluid instance
     */
    private SystemInterface fluid() {
      double naturalGas = 1.0 - propaneFeedMassFraction;
      SystemInterface fluid = new SystemSrkEos(temperatureK, pressurePa / 1.0e5);
      fluid.addComponent("methane", naturalGas * 0.897 / METHANE_MOLAR_MASS_KG_MOL);
      fluid.addComponent("ethane", (naturalGas * 0.089 + propaneFeedMassFraction * 0.003) / ETHANE_MOLAR_MASS_KG_MOL);
      fluid.addComponent("propane", (naturalGas * 0.011 + propaneFeedMassFraction * 0.963) / PROPANE_MOLAR_MASS_KG_MOL);
      fluid.addComponent("n-butane",
          (naturalGas * 0.003 + propaneFeedMassFraction * 0.033) / N_BUTANE_MOLAR_MASS_KG_MOL);
      fluid.addComponent("n-pentane", propaneFeedMassFraction * 0.001 / N_PENTANE_MOLAR_MASS_KG_MOL);
      fluid.setMixingRule("classic");
      return fluid;
    }

    /**
     * Reads all retained rows.
     *
     * @return immutable benchmark row list
     * @throws Exception when the retained dataset cannot be read
     */
    private static List<Benchmark> readAll() throws Exception {
      Path path = Paths.get("src", "test", "resources", "neqsim", "process", "safety", "release",
          "richardson-natural-gas-propane-2006.csv");
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      assertTrue(lines.get(0).contains("publishedHemDischargeCoefficient"));
      List<Benchmark> rows = new java.util.ArrayList<Benchmark>();
      for (int index = 1; index < lines.size(); index++) {
        rows.add(new Benchmark(lines.get(index).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1)));
      }
      return Collections.unmodifiableList(rows);
    }
  }
}
