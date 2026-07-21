package neqsim.process.engineering.designcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.engineering.numerics.EngineeringNumericalHealthCriteria;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests deterministic isolated engineering-case execution. */
class EngineeringCaseRunnerTest {

  @Test
  void sequentialAndParallelRunsProduceTheSameFingerprintWithoutMutatingBaseProcess() {
    ProcessSystem process = process();
    double basePressure = ((Stream) process.getUnit("FEED")).getPressure("bara");
    EngineeringCaseSet cases = new EngineeringCaseSet("pressure-envelope").addCase(caseAtPressure("normal", 50.0, 10))
        .addCase(caseAtPressure("maximum", 80.0, 20)).addMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringCaseRunReport sequential = EngineeringCaseRunner.run(process, cases,
        EngineeringCaseRunOptions.sequential());
    EngineeringCaseRunReport parallel = EngineeringCaseRunner.run(process, cases,
        EngineeringCaseRunOptions.builder().parallelism(2).build());

    assertEquals(sequential.getDefinitionFingerprint(), parallel.getDefinitionFingerprint());
    assertEquals(sequential.getResultFingerprint(), parallel.getResultFingerprint());
    assertEquals(80.0, sequential.getEnvelope().getGoverningValues().get("FEED.pressure").getValue(), 1.0e-10);
    assertEquals(basePressure, ((Stream) process.getUnit("FEED")).getPressure("bara"), 1.0e-10);
    assertEquals(2, sequential.getEnvelope().getSuccessfulCaseCount());
    assertTrue(sequential.isComplete());
    assertFalse(sequential.isAccepted());
    assertEquals(1, sequential.getEnvelope().getUnassessedGoverningMetricIds().size());
    assertTrue(sequential.toJson().contains("isolatedProcessCopies"));
    assertNotEquals(sequential.getDefinitionFingerprint(), sequential.getResultFingerprint());
  }

  @Test
  void caseRunnerCanEmbedNumericalHealthReports() {
    ProcessSystem process = process();
    EngineeringCaseSet cases = new EngineeringCaseSet("health-report").addCase(caseAtPressure("normal", 50.0, 10))
        .addMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringCaseRunReport report = EngineeringCaseRunner.run(process, cases, EngineeringCaseRunOptions.builder()
        .numericalHealthCriteria(EngineeringNumericalHealthCriteria.defaults()).build());

    assertTrue(report.toJson().contains("numericalHealth"));
    assertEquals("HEALTHY", report.getEnvelope().getCaseResults().get(0).getNumericalHealthReport().getStatus().name());
  }

  @Test
  void incompleteEnvelopeCanReturnOrThrowWithTheSamePartialEvidence() {
    ProcessSystem process = process();
    EngineeringMetric nonFinite = new EngineeringMetric("FEED.invalid", "FEED", "Invalid metric", "fraction",
        EngineeringMetric.GoverningDirection.MAXIMUM, new EngineeringMetric.Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem ignored) {
            return Double.NaN;
          }
        });
    EngineeringCaseSet cases = new EngineeringCaseSet("incomplete").addCase(caseAtPressure("normal", 50.0, 10))
        .addMetric(nonFinite);

    EngineeringCaseRunReport partial = EngineeringCaseRunner.run(process, cases,
        EngineeringCaseRunOptions.sequential());

    assertFalse(partial.isComplete());
    assertEquals("FEED.invalid", partial.getEnvelope().getMissingGoverningMetricIds().get(0));
    EngineeringCaseExecutionException requireException = assertThrows(EngineeringCaseExecutionException.class,
        partial::requireComplete);
    assertSame(partial, requireException.getPartialReport());

    EngineeringCaseExecutionException executionException = assertThrows(EngineeringCaseExecutionException.class,
        () -> EngineeringCaseRunner.run(process, cases,
            EngineeringCaseRunOptions.builder()
                .failurePolicy(EngineeringCaseFailurePolicy.THROW_WITH_PARTIAL_RESULT).build()));
    assertFalse(executionException.getPartialReport().isComplete());
  }

  @Test
  void acceptanceRequiresConfiguredLimitsAndNoViolation() {
    ProcessSystem process = process();
    EngineeringCaseSet passing = new EngineeringCaseSet("passing-limits")
        .addCase(caseAtPressure("normal", 50.0, 10)).addCase(caseAtPressure("maximum", 80.0, 20))
        .addMetric(EngineeringMetric.equipmentPressure("FEED").setAcceptanceRange(null, Double.valueOf(90.0)));
    EngineeringCaseSet failing = new EngineeringCaseSet("failing-limits")
        .addCase(caseAtPressure("normal", 50.0, 10)).addCase(caseAtPressure("maximum", 80.0, 20))
        .addMetric(EngineeringMetric.equipmentPressure("FEED").setAcceptanceRange(null, Double.valueOf(70.0)));

    EngineeringCaseRunReport accepted = EngineeringCaseRunner.run(process, passing,
        EngineeringCaseRunOptions.sequential());
    EngineeringCaseRunReport violated = EngineeringCaseRunner.run(process, failing,
        EngineeringCaseRunOptions.sequential());

    assertTrue(accepted.isComplete());
    assertTrue(accepted.isAccepted());
    assertTrue(violated.isComplete());
    assertFalse(violated.isAccepted());
    assertEquals(1, violated.getEnvelope().getLimitViolationCount());
  }

  private EngineeringDesignCase caseAtPressure(String id, final double pressureBara, int priority) {
    return new EngineeringDesignCase(id, id, EngineeringDesignCase.Type.CUSTOM,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(pressureBara, "bara");
          }
        }).setPriority(priority)
        .addInput(new EngineeringDesignCase.Input("feedPressure", pressureBara, "bara", "DESIGN-BASIS-A"));
  }

  private ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    return process;
  }
}
