package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;

/** Quantitative integration tests for the compressor receiver relief and disposal connection. */
class CompressorReliefDisposalStudyTest {
  private static CompressorAutomationStudy.Result compressorCase;

  /** Creates the expensive physical compressor trajectory once; connected calculations must not mutate it. */
  @BeforeAll
  static void createCompressorCase() {
    compressorCase = blockedOutletCase();
  }

  /** Executes real receiver inventory blowdown and retains explicit approval holds. */
  @Test
  @SuppressWarnings("unchecked")
  void connectsExecutedReceiverToReliefBlowdownAndFlare() {
    CompressorAutomationStudy.Result compressor = compressorCase;
    String before = new Gson().toJson(compressor.toMap());
    CompressorReliefDisposalStudy.Design design = syntheticDesign(1.0);

    CompressorReliefDisposalStudy.Result result = CompressorReliefDisposalStudy.run(compressor, design);
    design.setTimeStepSeconds(9.0);
    Map<String, Object> report = result.toMap();
    Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
    Map<String, Object> frozenDesign = (Map<String, Object>) report.get("design");
    Map<String, String> inputUnits = (Map<String, String>) report.get("inputUnits");

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        result.getCalculationResult().getStatus());
    assertEquals("PASS", metrics.get("status"), report.toString());
    assertTrue(((Number) metrics.get("initialMassKg")).doubleValue() > 100.0);
    assertTrue(((Number) metrics.get("releasedMassKg")).doubleValue() > 100.0);
    assertTrue(((Number) metrics.get("peakDisposalLoadKgPerSecond")).doubleValue() > 1.0);
    assertTrue(((Number) metrics.get("timeToHalfPressureSeconds")).doubleValue() > 0.0);
    assertTrue(((Number) metrics.get("minimumFluidTemperatureK")).doubleValue() > 100.0);
    assertTrue(Math.abs(((Number) metrics.get("massClosureErrorKg")).doubleValue()) < 1.0e-6);
    assertEquals(Boolean.TRUE, metrics.get("headerMachAcceptable"));
    assertEquals(1.0, ((Number) frozenDesign.get("timeStepSeconds")).doubleValue(), 0.0);
    assertEquals("bara absolute", inputUnits.get("reliefSetPressureBara"));
    assertEquals("kg/s", inputUnits.get("flareDesignMassFlowKgPerSecond"));
    assertEquals(Boolean.FALSE, report.get("sourceTrainMutated"));
    assertEquals(before, new Gson().toJson(compressor.toMap()));
    assertTrue(new Gson().toJson(report).contains("Vendor BDV, PSV, flare-network, MDMT"));
  }

  /** Repeated execution is deterministic and the frozen compressor evidence remains identical. */
  @Test
  void repeatsDeterministicallyWithoutMutatingTheSourceCase() {
    CompressorAutomationStudy.Result compressor = compressorCase;
    CompressorReliefDisposalStudy.Design design = syntheticDesign(1.0);
    String first = new Gson().toJson(CompressorReliefDisposalStudy.run(compressor, design).toMap());
    String second = new Gson().toJson(CompressorReliefDisposalStudy.run(compressor, design).toMap());
    assertEquals(first, second);
  }

  /** Halving the timestep preserves the physically relevant peak, released mass and pressure-decay evidence. */
  @Test
  @SuppressWarnings("unchecked")
  void reportsBoundedTimestepSensitivity() {
    CompressorAutomationStudy.Result compressor = compressorCase;
    Map<String, Object> coarse = (Map<String, Object>) CompressorReliefDisposalStudy
        .run(compressor, syntheticDesign(1.0)).toMap().get("metrics");
    Map<String, Object> fine = (Map<String, Object>) CompressorReliefDisposalStudy.run(compressor, syntheticDesign(0.5))
        .toMap().get("metrics");

    assertRelativeDifferenceBelow(coarse, fine, "peakDisposalLoadKgPerSecond", 0.01);
    assertRelativeDifferenceBelow(coarse, fine, "releasedMassKg", 0.01);
    assertRelativeDifferenceBelow(coarse, fine, "timeToHalfPressureSeconds", 0.02);
    assertFalse(((Number) coarse.get("releasedMassKg")).doubleValue() == 0.0);
    assertNotNull(fine.get("minimumWallTemperatureK"));
  }

  private static CompressorAutomationStudy.Result blockedOutletCase() {
    CompressorAutomationStudy.Design design = new CompressorAutomationStudy.Design();
    return CompressorAutomationStudy.run(CompressorAutomationStudy.create(design),
        new CompressorAutomationStudy.CaseDefinition(CompressorAutomationStudy.Profile.BLOCKED_OUTLET, 0.1, 12.0, 1.0));
  }

  private static CompressorReliefDisposalStudy.Design syntheticDesign(double timeStepSeconds) {
    return new CompressorReliefDisposalStudy.Design().setTimeStepSeconds(timeStepSeconds).setMaximumTimeSeconds(300.0)
        .addEvidenceReference("SYNTHETIC-REFERENCE-DYN-C039");
  }

  private static void assertRelativeDifferenceBelow(Map<String, Object> first, Map<String, Object> second, String key,
      double maximumRelativeDifference) {
    double a = ((Number) first.get(key)).doubleValue();
    double b = ((Number) second.get(key)).doubleValue();
    double scale = Math.max(Math.abs(a), Math.abs(b));
    assertTrue(Math.abs(a - b) / scale <= maximumRelativeDifference, key + " changed from " + a + " to " + b);
  }
}
