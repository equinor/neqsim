package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests the coordinated project-level engineering simulation entry point. */
class EngineeringSimulationRunnerTest {

  @Test
  void runsConfiguredFoundationsAndProducesGovernedProjectResult() {
    ProcessSystem process = process();
    EngineeringProject project = new EngineeringProject("project-1", "Integration test", process,
        new EngineeringDesignBasis().setJurisdiction("Norway").setProjectPhase("Concept study"));
    project.addDesignCase(new EngineeringDesignCase("maximum", "Maximum pressure",
        EngineeringDesignCase.Type.CUSTOM, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem caseProcess) {
            ((Stream) caseProcess.getUnit("FEED")).setPressure(80.0, "bara");
          }
        }));
    project.addEngineeringMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringSimulationResult result = EngineeringSimulationRunner.run(project,
        EngineeringCaseRunOptions.builder().parallelism(2).build());

    assertNotNull(result.getCaseRunReport());
    assertEquals(1, result.getCaseRunReport().getEnvelope().getSuccessfulCaseCount());
    assertEquals(80.0,
        result.getCaseRunReport().getEnvelope().getGoverningValues().get("FEED.pressure").getValue(), 1.0e-10);
    assertTrue(result.getCoupledSafetyResults().isEmpty());
    assertTrue(result.getDynamicScenarioResults().isEmpty());
    assertTrue(result.toJson().contains("engineeringApprovalRequired"));
    assertEquals(50.0, ((Stream) process.getUnit("FEED")).getPressure("bara"), 1.0e-10);
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
