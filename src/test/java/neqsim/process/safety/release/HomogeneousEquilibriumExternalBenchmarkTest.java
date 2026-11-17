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
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Reproduces the published STEP flashing-propane release case from Witlox et al. (2007). */
class HomogeneousEquilibriumExternalBenchmarkTest extends neqsim.NeqSimTest {

  /** Compares the HEM result with the independently published STEP propane rate. */
  @Test
  void publishedStepCaseRecordsUnqualifiedRateMismatch() throws Exception {
    Benchmark benchmark = Benchmark.read();
    SystemInterface reservoir = new SystemSrkEos(benchmark.storageTemperatureK, benchmark.storagePressurePa / 1.0e5);
    reservoir.addComponent("propane", 1.0);
    reservoir.setMixingRule("classic");
    ReleaseFlowRequest request = new ReleaseFlowRequest(reservoir, benchmark.orificeDiameterM,
        benchmark.dischargeCoefficient, benchmark.ambientPressurePa);
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
    assertTrue(result.isUsable(), result.getDiagnostics().toString());
    assertTrue(result.isChoked());
    assertEquals(benchmark.storagePressurePa / 1.0e5, reservoir.getPressure(), 0.0);
    assertEquals(benchmark.storageTemperatureK, reservoir.getTemperature(), 0.0);

    double actualMassFlowKgS = result.getMassFlowRateKgS();
    double relativeError = Math.abs(actualMassFlowKgS - benchmark.measuredMassFlowKgS) / benchmark.measuredMassFlowKgS;
    assertTrue(actualMassFlowKgS < benchmark.measuredMassFlowKgS);
    assertTrue(relativeError > 0.50,
        "Reassess the evidence manifest and qualification text when the STEP rate mismatch changes materially");
    assertEquals("NOT_REPORTED", benchmark.measurementUncertaintyStatus);
    assertTrue(result.getEvidence().hasIndependentEvidence());
    assertTrue(result.getEvidence().getLimitations().contains("EXPERIMENTAL_RATE_MISMATCH"));

    SourceTermFrame frame = SourceTermFrame.calculated("step-propane-benchmark", "published-opening",
        UUID.fromString("00000000-0000-0000-0000-000000003860"), 0, 0.0, Instant.parse("2026-11-17T00:00:00Z"), request,
        result, Collections.singletonMap("benchmarkCase", benchmark.caseId));
    SourceTermFrame.verifyEnvelope(frame.toJson());
    JsonObject model = JsonParser.parseString(frame.toJson()).getAsJsonObject().getAsJsonObject("model");
    assertEquals("UNQUALIFIED", model.get("evidenceLevel").getAsString());
    assertTrue(model.getAsJsonObject("evidence").get("independentEvidence").getAsBoolean());

    Path outputDirectory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(outputDirectory);
    String receipt = "caseId,modelVersion,measuredMassFlow_kg_s,actualMassFlow_kg_s,relativeError,"
        + "flowComparisonStatus,measurementUncertaintyStatus,evidenceLevel,choked\n" + benchmark.caseId + ","
        + result.getModelVersion() + "," + benchmark.measuredMassFlowKgS + "," + actualMassFlowKgS + "," + relativeError
        + ",OUTSIDE_PUBLISHED_RATE," + benchmark.measurementUncertaintyStatus + ",UNQUALIFIED," + result.isChoked()
        + "\n";
    Files.write(outputDirectory.resolve("witlox-step-propane-2007-receipt.csv"),
        receipt.getBytes(StandardCharsets.UTF_8));
  }

  /** Confirms the adverse result is surrounded by smooth, physically ordered numerical behavior. */
  @Test
  void nearbyPublishedInputsHaveExpectedTrends() throws Exception {
    Benchmark benchmark = Benchmark.read();
    ReleaseFlowResult lowerPressure = calculate(0.95 * benchmark.storagePressurePa, benchmark.storageTemperatureK,
        benchmark.orificeDiameterM, benchmark.ambientPressurePa);
    ReleaseFlowResult reference = calculate(benchmark.storagePressurePa, benchmark.storageTemperatureK,
        benchmark.orificeDiameterM, benchmark.ambientPressurePa);
    ReleaseFlowResult upperPressure = calculate(1.05 * benchmark.storagePressurePa, benchmark.storageTemperatureK,
        benchmark.orificeDiameterM, benchmark.ambientPressurePa);
    assertTrue(lowerPressure.isUsable());
    assertTrue(reference.isUsable());
    assertTrue(upperPressure.isUsable());
    assertTrue(lowerPressure.getMassFlowRateKgS() < reference.getMassFlowRateKgS());
    assertTrue(reference.getMassFlowRateKgS() < upperPressure.getMassFlowRateKgS());

    ReleaseFlowResult smallerOpening = calculate(benchmark.storagePressurePa, benchmark.storageTemperatureK,
        0.9 * benchmark.orificeDiameterM, benchmark.ambientPressurePa);
    ReleaseFlowResult largerOpening = calculate(benchmark.storagePressurePa, benchmark.storageTemperatureK,
        1.1 * benchmark.orificeDiameterM, benchmark.ambientPressurePa);
    assertEquals(0.9 * 0.9, smallerOpening.getMassFlowRateKgS() / reference.getMassFlowRateKgS(), 1.0e-12);
    assertEquals(1.1 * 1.1, largerOpening.getMassFlowRateKgS() / reference.getMassFlowRateKgS(), 1.0e-12);

    ReleaseFlowResult lowerBackPressure = calculate(benchmark.storagePressurePa, benchmark.storageTemperatureK,
        benchmark.orificeDiameterM, 0.9 * benchmark.ambientPressurePa);
    ReleaseFlowResult upperBackPressure = calculate(benchmark.storagePressurePa, benchmark.storageTemperatureK,
        benchmark.orificeDiameterM, 1.1 * benchmark.ambientPressurePa);
    assertTrue(lowerBackPressure.isChoked());
    assertTrue(upperBackPressure.isChoked());
    assertEquals(reference.getMassFlowRateKgS(), lowerBackPressure.getMassFlowRateKgS(), 1.0e-10);
    assertEquals(reference.getMassFlowRateKgS(), upperBackPressure.getMassFlowRateKgS(), 1.0e-10);
  }

  /**
   * Evaluates one nearby HEM condition with the same explicit model assumptions.
   *
   * @param storagePressurePa upstream absolute pressure in Pa
   * @param storageTemperatureK upstream temperature in K
   * @param orificeDiameterM opening diameter in m
   * @param ambientPressurePa receiving absolute pressure in Pa
   * @return checked release result
   */
  private static ReleaseFlowResult calculate(double storagePressurePa, double storageTemperatureK,
      double orificeDiameterM, double ambientPressurePa) {
    SystemInterface reservoir = new SystemSrkEos(storageTemperatureK, storagePressurePa / 1.0e5);
    reservoir.addComponent("propane", 1.0);
    reservoir.setMixingRule("classic");
    return new HomogeneousEquilibriumReleaseModel()
        .calculate(new ReleaseFlowRequest(reservoir, orificeDiameterM, 1.0, ambientPressurePa));
  }

  /** One immutable published benchmark row. */
  private static final class Benchmark {
    private final String caseId;
    private final double storagePressurePa;
    private final double storageTemperatureK;
    private final double orificeDiameterM;
    private final double dischargeCoefficient;
    private final double ambientPressurePa;
    private final double measuredMassFlowKgS;
    private final String measurementUncertaintyStatus;

    /**
     * Parses one benchmark row.
     *
     * @param values CSV values
     */
    private Benchmark(String[] values) {
      caseId = values[0];
      if (!"propane".equals(values[1])) {
        throw new IllegalArgumentException("STEP benchmark fluid must be propane");
      }
      storagePressurePa = Double.parseDouble(values[2]);
      storageTemperatureK = Double.parseDouble(values[3]);
      orificeDiameterM = Double.parseDouble(values[4]);
      dischargeCoefficient = Double.parseDouble(values[5]);
      ambientPressurePa = Double.parseDouble(values[6]);
      measuredMassFlowKgS = Double.parseDouble(values[7]);
      measurementUncertaintyStatus = values[8];
      if (!values[9].contains("10.1016/j.jhazmat.2006.06.126")) {
        throw new IllegalArgumentException("Witlox benchmark provenance is required");
      }
    }

    /**
     * Reads the repository benchmark row.
     *
     * @return parsed benchmark
     * @throws Exception when the file cannot be read
     */
    private static Benchmark read() throws Exception {
      Path path = Paths.get("src", "test", "resources", "neqsim", "process", "safety", "release",
          "witlox-step-propane-2007.csv");
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      assertEquals(2, lines.size());
      assertTrue(lines.get(0).contains("measuredMassFlowKgS"));
      return new Benchmark(lines.get(1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));
    }
  }
}
