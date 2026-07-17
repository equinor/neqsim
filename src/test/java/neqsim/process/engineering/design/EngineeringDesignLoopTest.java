package neqsim.process.engineering.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests closed-loop application, rerun, convergence, and process isolation. */
class EngineeringDesignLoopTest {

  @Test
  void appliesDesignUpdateAndRerunsCasesWithoutMutatingBaseProcess() {
    ProcessSystem base = process();
    EngineeringCaseSet cases = new EngineeringCaseSet("loop-test").addCase(new EngineeringDesignCase("normal", "Normal",
        EngineeringDesignCase.Type.NORMAL, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            // Base conditions are the controlled case definition.
          }
        })).addMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringDesignLoopResult result = EngineeringDesignLoop.run(base, cases,
        Arrays.<EngineeringDesignModule>asList(new FeedPressureDesignModule()),
        EngineeringDesignLoopOptions.builder().maximumIterations(4).build());

    assertTrue(result.isConverged());
    assertEquals(3, result.getIterations().size());
    assertTrue(result.getIterations().get(2).getConvergenceReport().isConverged());
    assertEquals(60.0, result.getState().requireValue("FEED.designPressure"), 1.0e-10);
    assertEquals(60.0, ((Stream) result.getDesignedProcess().getUnit("FEED")).getPressure("bara"), 1.0e-10);
    assertEquals(50.0, ((Stream) base.getUnit("FEED")).getPressure("bara"), 1.0e-10);
    assertFalse(result.toJson().contains("fitnessForConstruction\": true"));
  }

  @Test
  void selectsTraceablePhysicalDesignCandidate() {
    EngineeringDesignUpdate update = EngineeringDesignUpdate.builder("L-1.insideDiameter", 0.15, "m")
        .candidates(new DesignCandidate("NPS-4-SCH40", 0.10, "m"), new DesignCandidate("NPS-8-SCH40", 0.20, "m"))
        .build();

    assertEquals(0.20, update.selectedValue(), 1.0e-12);
    assertEquals("NPS-8-SCH40", update.selectedCandidateId());
    assertEquals(2, update.getCandidates().length);
    assertThrows(IllegalArgumentException.class, () -> EngineeringDesignUpdate.builder("L-1.insideDiameter", 0.15, "m")
        .candidates(new DesignCandidate("NPS-8-SCH40", 0.20, "in")).build());
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

  private static final class FeedPressureDesignModule implements EngineeringDesignModule {
    private static final long serialVersionUID = 1000L;

    @Override
    public String getId() {
      return "feed-pressure";
    }

    @Override
    public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
        EngineeringDesignState state, EngineeringCalculationContext context) {
      return EngineeringDesignModuleResult.builder(getId(), "Test pressure design", "1.0")
          .addUpdate(EngineeringDesignUpdate.builder("FEED.designPressure", 60.0, "bara")
              .applier(new EngineeringDesignUpdate.Applier() {
                private static final long serialVersionUID = 1000L;

                @Override
                public void apply(ProcessSystem working, double value) {
                  ((Stream) working.getUnit("FEED")).setPressure(value, "bara");
                }
              }).build())
          .addConstraint(new EngineeringDesignConstraint("feed-minimum", "Minimum design pressure",
              "FEED.designPressure", 60.0, "bara", EngineeringDesignConstraint.Comparison.MINIMUM))
          .build();
    }
  }
}
