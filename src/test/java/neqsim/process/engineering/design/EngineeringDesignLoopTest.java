package neqsim.process.engineering.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

  @Test
  void detectsTwoStateDiscreteCandidateOscillation() {
    ProcessSystem base = process();
    EngineeringCaseSet cases = new EngineeringCaseSet("oscillation-test").addCase(new EngineeringDesignCase("normal",
        "Normal", EngineeringDesignCase.Type.NORMAL, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            // The design update supplies the changing physical state.
          }
        })).addMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringDesignLoopResult result = EngineeringDesignLoop.run(base, cases,
        Arrays.<EngineeringDesignModule>asList(new OscillatingCandidateModule()),
        EngineeringDesignLoopOptions.builder().maximumIterations(6).build());

    assertFalse(result.isConverged());
    assertEquals("DISCRETE_DESIGN_OSCILLATION_DETECTED", result.getTerminationReason());
    assertEquals(3, result.getIterations().size());
  }

  @Test
  void dependencyGraphOrdersModulesAndMakesUpstreamStateAvailableInSameIteration() {
    DependencyModule downstream = new DependencyModule("a-downstream", Arrays.asList("z-upstream"), "design.downstream",
        2.0, "design.upstream", EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);
    DependencyModule upstream = new DependencyModule("z-upstream", Collections.<String>emptyList(), "design.upstream",
        1.0, null, EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);

    EngineeringDesignDependencyGraph graph = EngineeringDesignDependencyGraph
        .of(Arrays.<EngineeringDesignModule>asList(downstream, upstream));
    EngineeringDesignLoopResult result = EngineeringDesignLoop.run(process(), cases("dependency-order"),
        Arrays.<EngineeringDesignModule>asList(downstream, upstream),
        EngineeringDesignLoopOptions.builder().maximumIterations(4).build());

    assertEquals(Arrays.asList("z-upstream", "a-downstream"), graph.getOrderedModuleIds());
    assertTrue(result.isConverged());
    assertEquals(1.0, result.getState().requireValue("design.upstream"), 1.0e-12);
    assertEquals(2.0, result.getState().requireValue("design.downstream"), 1.0e-12);
  }

  @Test
  void dependencyGraphRejectsMissingDependenciesAndCycles() {
    DependencyModule missing = new DependencyModule("missing-user", Arrays.asList("not-configured"), null, 0.0, null,
        EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);
    EngineeringDesignDependencyException missingException = assertThrows(EngineeringDesignDependencyException.class,
        () -> EngineeringDesignDependencyGraph.of(Arrays.<EngineeringDesignModule>asList(missing)));
    assertTrue(missingException.getMessage().contains("not-configured"));

    DependencyModule first = new DependencyModule("first", Arrays.asList("second"), null, 0.0, null,
        EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);
    DependencyModule second = new DependencyModule("second", Arrays.asList("first"), null, 0.0, null,
        EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);
    EngineeringDesignDependencyException cycleException = assertThrows(EngineeringDesignDependencyException.class,
        () -> EngineeringDesignDependencyGraph.of(Arrays.<EngineeringDesignModule>asList(first, second)));
    assertTrue(cycleException.getMessage().contains("Cycle detected"));
  }

  @Test
  void duplicateUpdatesFailClosedWithoutGoverningRule() {
    DependencyModule first = new DependencyModule("first", Collections.<String>emptyList(), "shared.pressure", 60.0,
        null, EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);
    DependencyModule second = new DependencyModule("second", Collections.<String>emptyList(), "shared.pressure", 70.0,
        null, EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE);

    EngineeringDesignConflictException exception = assertThrows(EngineeringDesignConflictException.class,
        () -> EngineeringDesignLoop.run(process(), cases("conflict"),
            Arrays.<EngineeringDesignModule>asList(first, second),
            EngineeringDesignLoopOptions.builder().maximumIterations(3).build()));

    assertEquals("shared.pressure", exception.getDesignVariableKey());
    assertTrue(exception.getMessage().contains("first=60.0 bara"));
    assertTrue(exception.getMessage().contains("second=70.0 bara"));
  }

  @Test
  void explicitGoverningMaximumSelectsTraceableProposal() {
    DependencyModule low = new DependencyModule("low-case", Collections.<String>emptyList(), "shared.pressure", 60.0,
        null, EngineeringDesignUpdate.ConflictResolution.GOVERNING_MAXIMUM);
    DependencyModule high = new DependencyModule("high-case", Collections.<String>emptyList(), "shared.pressure", 70.0,
        null, EngineeringDesignUpdate.ConflictResolution.GOVERNING_MAXIMUM);

    EngineeringDesignLoopResult result = EngineeringDesignLoop.run(process(), cases("governing-maximum"),
        Arrays.<EngineeringDesignModule>asList(low, high),
        EngineeringDesignLoopOptions.builder().maximumIterations(3).build());

    assertTrue(result.isConverged());
    assertEquals(70.0, result.getState().requireValue("shared.pressure"), 1.0e-12);
    assertEquals("high-case", result.getState().get("shared.pressure").getSourceModule());
  }

  private EngineeringCaseSet cases(String id) {
    return new EngineeringCaseSet(id).addCase(new EngineeringDesignCase("normal", "Normal",
        EngineeringDesignCase.Type.NORMAL, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            // The dependency and conflict tests use the unchanged normal case.
          }
        })).addMetric(EngineeringMetric.equipmentPressure("FEED"));
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

  private static final class OscillatingCandidateModule implements EngineeringDesignModule {
    private static final long serialVersionUID = 1000L;

    @Override
    public String getId() {
      return "oscillating-candidate";
    }

    @Override
    public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
        EngineeringDesignState state, EngineeringCalculationContext context) {
      double candidate = !state.contains("FEED.discreteCandidate")
          || state.requireValue("FEED.discreteCandidate") == 2.0 ? 1.0 : 2.0;
      return EngineeringDesignModuleResult.builder(getId(), "Oscillation regression", "1.0")
          .addUpdate(EngineeringDesignUpdate.builder("FEED.discreteCandidate", candidate, "m").relativeTolerance(0.0)
              .applier(new EngineeringDesignUpdate.Applier() {
                private static final long serialVersionUID = 1000L;

                @Override
                public void apply(ProcessSystem working, double value) {
                  ((Stream) working.getUnit("FEED")).setPressure(49.0 + value, "bara");
                }
              }).build())
          .build();
    }
  }

  private static final class DependencyModule implements EngineeringDesignModule {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final List<String> dependencies;
    private final String updateKey;
    private final double value;
    private final String requiredStateKey;
    private final EngineeringDesignUpdate.ConflictResolution conflictResolution;

    private DependencyModule(String id, List<String> dependencies, String updateKey, double value,
        String requiredStateKey, EngineeringDesignUpdate.ConflictResolution conflictResolution) {
      this.id = id;
      this.dependencies = dependencies;
      this.updateKey = updateKey;
      this.value = value;
      this.requiredStateKey = requiredStateKey;
      this.conflictResolution = conflictResolution;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public List<String> getDependencies() {
      return dependencies;
    }

    @Override
    public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
        EngineeringDesignState state, EngineeringCalculationContext context) {
      if (requiredStateKey != null && !state.contains(requiredStateKey)) {
        throw new IllegalStateException("Required upstream state was not available: " + requiredStateKey);
      }
      EngineeringDesignModuleResult.Builder builder = EngineeringDesignModuleResult.builder(id, "dependency test",
          "1.0");
      if (updateKey != null) {
        builder.addUpdate(
            EngineeringDesignUpdate.builder(updateKey, value, "bara").conflictResolution(conflictResolution).build());
      }
      return builder.build();
    }
  }
}
