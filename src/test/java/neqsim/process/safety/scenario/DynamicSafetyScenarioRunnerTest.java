package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests dynamic initiating-event, trip-logic, and safe-state response verification. */
class DynamicSafetyScenarioRunnerTest {

  @Test
  void verifiesSafeStateOnAnIsolatedProcessCopy() {
    ProcessSystem base = process();
    final double originalPressure = ((Stream) base.getUnit("FEED")).getPressure("bara");
    DynamicSafetyScenario scenario = DynamicSafetyScenario.builder("SIF-TEST", "High-pressure trip")
        .durationSeconds(5.0).timeStepSeconds(1.0).triggerTimeSeconds(0.0)
        .initiatingEvent(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(60.0, "bara");
          }
        }).addLogic(new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ProcessLogic create(ProcessSystem process) {
            return new PressureTripLogic((Stream) process.getUnit("FEED"));
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("safe-pressure", "Safe pressure", "bara", new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              @Override
              public double extract(ProcessSystem process) {
                return ((Stream) process.getUnit("FEED")).getPressure("bara");
              }
            }).acceptanceRange(null, Double.valueOf(10.0)).deadlineSeconds(1.0).build())
        .addEvidenceReference("SRS-TEST-A").build();

    DynamicSafetyScenarioResult result = DynamicSafetyScenarioRunner.run(base, scenario);

    assertTrue(result.isPassed());
    assertTrue(result.toJson().contains("silTargetInferred"));
    assertEquals(originalPressure, ((Stream) base.getUnit("FEED")).getPressure("bara"), 1.0e-10);
  }

  /** Checks ordered results, isolation, and retention of a failed physical criterion in a batch. */
  @Test
  void strictBatchMatchesIndependentSerialRuns() {
    ProcessSystem base = process();
    DynamicSafetyScenario passing = pressureScenario("PASSING", 40.0);
    DynamicSafetyScenario failing = pressureScenario("FAILING", 60.0);
    List<DynamicSafetyScenario> scenarios = Arrays.asList(failing, passing);
    boolean originalWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    List<DynamicSafetyScenarioResult> parallel;
    List<DynamicSafetyScenarioResult> serial;
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(true);
      parallel = DynamicSafetyScenarioRunner.runStrictBatch(base, scenarios, 2);
      serial = DynamicSafetyScenarioRunner.runStrictBatch(base, scenarios, 1);
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(originalWarmStart);
    }
    assertFalse(parallel.get(0).isPassed());
    assertTrue(parallel.get(1).isPassed());
    for (int index = 0; index < serial.size(); index++) {
      assertEquals(serial.get(index).toMap(), parallel.get(index).toMap());
    }
    assertEquals(50.0, ((Stream) base.getUnit("FEED")).getPressure("bara"));
    assertTrue(DynamicSafetyScenarioRunner.runStrictBatch(base, Collections.emptyList(), 2).isEmpty());
    assertThrows(IllegalArgumentException.class,
        () -> DynamicSafetyScenarioRunner.runStrictBatch(base, Arrays.asList(passing, passing), 2));
    assertThrows(IllegalArgumentException.class,
        () -> DynamicSafetyScenarioRunner.runStrictBatch(base, Arrays.asList(failing, null), 2));
  }

  /**
   * Builds a deterministic scenario with a criterion that passes at or below 50 bara.
   *
   * @param name scenario identifier
   * @param pressure event pressure in bara
   * @return isolated event and criterion definition
   */
  private static DynamicSafetyScenario pressureScenario(String name, double pressure) {
    return DynamicSafetyScenario.builder(name, name).durationSeconds(0.2).timeStepSeconds(0.1).triggerTimeSeconds(0.0)
        .eventExactTiming(true).initiatingEvent("MARK-DEMAND", model -> {
        }).addLogic("PRESSURE-RESPONSE", model -> new PressureTripLogic((Stream) model.getUnit("FEED"), pressure))
        .addCriterion(DynamicScenarioCriterion
            .builder("PRESSURE", "Feed pressure", "bara", "READ-FEED-PRESSURE",
                model -> ((Stream) model.getUnit("FEED")).getPressure("bara"))
            .acceptanceRange(0.0, 50.0).evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS)
            .deadlineSeconds(0.2).build())
        .build();
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

  private static final class PressureTripLogic implements ProcessLogic {
    private static final long serialVersionUID = 1000L;
    private final Stream stream;
    private final double responsePressure;
    private LogicState state = LogicState.IDLE;

    private PressureTripLogic(Stream stream) {
      this(stream, 5.0);
    }

    /**
     * Creates isolated test logic bound to a copied process stream.
     *
     * @param stream copied feed stream
     * @param responsePressure pressure to publish in bara
     */
    private PressureTripLogic(Stream stream, double responsePressure) {
      this.stream = stream;
      this.responsePressure = responsePressure;
    }

    @Override
    public String getName() {
      return "Pressure trip";
    }

    @Override
    public LogicState getState() {
      return state;
    }

    @Override
    public void activate() {
      state = LogicState.RUNNING;
    }

    @Override
    public void deactivate() {
      state = LogicState.PAUSED;
    }

    @Override
    public boolean reset() {
      state = LogicState.IDLE;
      return true;
    }

    @Override
    public void execute(double timeStep) {
      stream.setPressure(responsePressure, "bara");
      state = LogicState.COMPLETED;
    }

    @Override
    public boolean isActive() {
      return state == LogicState.RUNNING;
    }

    @Override
    public boolean isComplete() {
      return state == LogicState.COMPLETED;
    }

    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      return new ArrayList<ProcessEquipmentInterface>();
    }

    @Override
    public String getStatusDescription() {
      return state.name();
    }
  }
}
