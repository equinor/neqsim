package neqsim.process.engineering.designcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
