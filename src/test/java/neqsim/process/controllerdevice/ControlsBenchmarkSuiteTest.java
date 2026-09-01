package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControlsBenchmarkSuite.CaseResult;
import neqsim.process.controllerdevice.ControlsBenchmarkSuite.ChallengeType;
import neqsim.process.controllerdevice.ControlsBenchmarkSuite.Report;

/** CI qualification for the canonical NeqSim controls benchmark. */
class ControlsBenchmarkSuiteTest {

  @Test
  void canonicalSuitePassesAllSixCases() {
    Report report = ControlsBenchmarkSuite.runCanonicalSuite();
    assertEquals(6, report.getCases().size());
    assertTrue(report.isPassed(), failureSummary(report));
    assertEquals(6, report.getAgentBenchmarkReport().getPassed());
    assertEquals(0, report.getAgentBenchmarkReport().getFailed());
    assertEquals(0, report.getAgentBenchmarkReport().getNotAttempted());
  }

  @Test
  void suiteCoversSetPointDisturbanceAndProtectionChallenges() {
    Report report = ControlsBenchmarkSuite.runCanonicalSuite();
    EnumSet<ChallengeType> challenges = EnumSet.noneOf(ChallengeType.class);
    for (CaseResult result : report.getCases()) {
      challenges.add(result.getChallengeType());
      assertTrue(result.getMetrics().getSampleCount() > 100, result.getId());
      assertTrue(Double.isFinite(result.getMetrics().getIntegralAbsoluteError()), result.getId());
      assertTrue(Double.isFinite(result.getMetrics().getSettlingTime()), result.getId());
      assertTrue(result.getControllerOutput().length == result.getTimeSeconds().length, result.getId());
    }
    assertTrue(challenges.contains(ChallengeType.SET_POINT_AND_DISTURBANCE));
    assertTrue(challenges.contains(ChallengeType.DISTURBANCE));
    assertTrue(challenges.contains(ChallengeType.PROTECTION));
  }

  @Test
  void protectionAndCoordinationCasesExerciseTheirSafetyBoundaries() {
    Report report = ControlsBenchmarkSuite.runCanonicalSuite();
    CaseResult antiSurge = report.getCase("control_anti_surge");
    CaseResult coordination = report.getCase("control_speed_recycle_coordination");
    assertNotNull(antiSurge);
    assertNotNull(coordination);
    assertTrue(antiSurge.getMinimumProcessValue() > 0.0, antiSurge.getAcceptanceDetail());
    assertTrue(maximum(antiSurge.getControllerOutput()) > 5.0, antiSurge.getAcceptanceDetail());
    assertTrue(coordination.isPassed(), coordination.getAcceptanceDetail());
  }

  @Test
  void publishedReferenceValuesRemainCurrent() {
    Report report = ControlsBenchmarkSuite.runCanonicalSuite();
    assertPublished(report, "control_level_setpoint", 0.909, 62.000, 50.000, 55.500, 0.000, 55.000);
    assertPublished(report, "control_pressure_disturbance", 1.111, 143.093, 49.444, 51.111, 41.111, 54.444);
    assertPublished(report, "control_cascade_temperature", 1.215, 114.233, 50.000, 51.684, 50.000, 82.000);
    assertPublished(report, "control_split_range", 0.952, 147.880, 49.000, 50.000, 50.000, 70.000);
    assertPublished(report, "control_anti_surge", 0.00488, 2.333, 0.0423, 0.300, 0.000, 37.481);
    assertPublished(report, "control_speed_recycle_coordination", 0.503, 476.566, 79.068, 84.523, 55.364, 88.091);
  }

  private static void assertPublished(Report report, String id, double finalError, double iae, double minimumPv,
      double maximumPv, double minimumOutput, double maximumOutput) {
    CaseResult result = report.getCase(id);
    assertNotNull(result, id);
    double printedPrecision = 6.0e-4;
    assertEquals(finalError, result.getFinalRelativeErrorPercent(), printedPrecision, id + " final error");
    assertEquals(iae, result.getMetrics().getIntegralAbsoluteError(), printedPrecision, id + " IAE");
    assertEquals(minimumPv, result.getMinimumProcessValue(), printedPrecision, id + " minimum PV");
    assertEquals(maximumPv, result.getMaximumProcessValue(), printedPrecision, id + " maximum PV");
    assertEquals(minimumOutput, minimum(result.getControllerOutput()), printedPrecision, id + " minimum output");
    assertEquals(maximumOutput, maximum(result.getControllerOutput()), printedPrecision, id + " maximum output");
  }

  private static double maximum(double[] values) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      maximum = Math.max(maximum, value);
    }
    return maximum;
  }

  private static String failureSummary(Report report) {
    StringBuilder summary = new StringBuilder();
    for (CaseResult result : report.getCases()) {
      summary.append(result.getId()).append(": passed=").append(result.isPassed()).append(", finalErrorPct=")
          .append(result.getFinalRelativeErrorPercent()).append(", IAE=")
          .append(result.getMetrics().getIntegralAbsoluteError()).append(", min=")
          .append(result.getMinimumProcessValue()).append(", max=").append(result.getMaximumProcessValue())
          .append(", minOutput=").append(minimum(result.getControllerOutput())).append(", maxOutput=")
          .append(maximum(result.getControllerOutput())).append("; ");
    }
    return summary.toString();
  }

  private static double minimum(double[] values) {
    double minimum = Double.POSITIVE_INFINITY;
    for (double value : values) {
      minimum = Math.min(minimum, value);
    }
    return minimum;
  }
}
