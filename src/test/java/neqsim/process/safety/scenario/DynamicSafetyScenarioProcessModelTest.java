package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative multi-area execution, isolation, timing, and rollback tests. */
class DynamicSafetyScenarioProcessModelTest {

  /** Exact boundaries, cross-area logic, deterministic replay, and source isolation must hold together. */
  @Test
  void executesStrictMultiAreaScenarioDeterministically() {
    ProcessModel source = streamModel();
    DynamicSafetyScenario scenario = pressureScenario();

    DynamicSafetyScenarioResult first = DynamicSafetyScenarioRunner.runStrict(source, scenario);
    DynamicSafetyScenarioResult second = DynamicSafetyScenarioRunner.runStrict(source, scenario);

    assertTrue(first.isPassed(), first.toJson());
    assertEquals(DynamicSafetyScenario.ExecutionTarget.PROCESS_MODEL, first.getExecutionTarget());
    assertEquals("EVENT_EXACT", first.getTimingPolicy());
    assertEquals(2.3, first.getCompletedDurationSeconds(), 1.0e-12);
    assertEquals(3, first.getPhysicalStepSeconds().size());
    assertEquals(0.4, first.getPhysicalStepSeconds().get(0).doubleValue(), 1.0e-12);
    assertEquals(1.0, first.getPhysicalStepSeconds().get(1).doubleValue(), 1.0e-12);
    assertEquals(0.9, first.getPhysicalStepSeconds().get(2).doubleValue(), 1.0e-12);
    assertEquals(1.0, first.getCriterionResults().get("area-a-pressure").getFirstSatisfiedSeconds().doubleValue(),
        1.0e-12);
    assertEquals(1.0, first.getCriterionResults().get("area-b-pressure").getFirstSatisfiedSeconds().doubleValue(),
        1.0e-12);
    assertEquals(first.toJson(), second.toJson());
    assertEquals(50.0, stream(source, "area-a").getPressure("bara"), 1.0e-12);
    assertEquals(50.0, stream(source, "area-b").getPressure("bara"), 1.0e-12);
    assertEquals(0.0, source.get("area-a").getTime(), 1.0e-12);
    assertEquals(0.0, source.get("area-b").getTime(), 1.0e-12);
  }

  /** A later-area failure must restore the already-advanced area before evidence is collected. */
  @Test
  void rollsBackAllAreasAfterLaterAreaFailure() {
    StatefulUnit first = new StatefulUnit("first", "area-a/first", 1.0, false);
    StatefulUnit second = new StatefulUnit("second", "area-b/second", 2.0, true);
    ProcessModel source = new ProcessModel();
    source.add("area-a", process("area-a", first));
    source.add("area-b", process("area-b", second));
    DynamicSafetyScenario scenario = DynamicSafetyScenario.builder("MODEL-ROLLBACK", "Coordinated rollback")
        .durationSeconds(1.0).timeStepSeconds(1.0).triggerTimeSeconds(0.0).eventExactTiming(true)
        .modelInitiatingEvent("NOOP-EVENT-v1", model -> {
        })
        .addModelLogic("ROLLBACK-EVIDENCE-v1",
            model -> new ModelStateLogic((StatefulUnit) model.get("area-a").getUnit("first"),
                (StatefulUnit) model.get("area-b").getUnit("second")))
        .addCriterion(DynamicScenarioCriterion
            .processModelBuilder("finite-state", "Finite first-area state", "1", "FIRST-STATE-v1",
                model -> ((StatefulUnit) model.get("area-a").getUnit("first")).getValue())
            .acceptanceRange(Double.valueOf(0.0), Double.valueOf(10.0)).deadlineSeconds(0.0).build())
        .build();

    DynamicSafetyScenarioResult result = DynamicSafetyScenarioRunner.run(source, scenario);

    assertTrue(!result.isPassed());
    assertTrue(result.toJson().contains("intentional later-area failure"));
    Map<String, Object> evidence = result.getLogicEvidence().get("model-state-evidence");
    assertEquals(0.0, ((Double) evidence.get("firstValue")).doubleValue(), 1.0e-12);
    assertEquals(0.0, ((Double) evidence.get("secondValue")).doubleValue(), 1.0e-12);
    assertEquals(0.0, first.getValue(), 1.0e-12);
    assertEquals(0.0, second.getValue(), 1.0e-12);
  }

  /** Process-system and process-model actions and observations cannot be combined accidentally. */
  @Test
  void rejectsMixedExecutionScopesAndWrongRunnerOverload() {
    DynamicSafetyScenario.Builder mixed = DynamicSafetyScenario.builder("MIXED", "Mixed scope")
        .initiatingEvent(process -> {
        });
    assertThrows(IllegalStateException.class, () -> mixed.addModelLogic(model -> new CrossAreaPressureLogic(model)));

    DynamicSafetyScenario modelScenario = pressureScenario();
    assertThrows(IllegalArgumentException.class,
        () -> DynamicSafetyScenarioRunner.run(streamModel().get("area-a"), modelScenario));
  }

  private static DynamicSafetyScenario pressureScenario() {
    return DynamicSafetyScenario.builder("MODEL-EVENT-EXACT", "Two-area high-pressure isolation").durationSeconds(2.3)
        .timeStepSeconds(1.0).triggerTimeSeconds(0.4).eventExactTiming(true)
        .modelInitiatingEvent("TWO-AREA-HP-v1", model -> {
          stream(model, "area-a").setPressure(60.0, "bara");
          stream(model, "area-b").setPressure(60.0, "bara");
        }).addModelLogic("TWO-AREA-TRIP-v1", CrossAreaPressureLogic::new)
        .addCriterion(DynamicScenarioCriterion
            .processModelBuilder("area-a-pressure", "Area A safe pressure", "bara", "AREA-A-P-v1",
                model -> stream(model, "area-a").getPressure("bara"))
            .acceptanceRange(null, Double.valueOf(10.0)).deadlineSeconds(1.0).build())
        .addCriterion(DynamicScenarioCriterion
            .processModelBuilder("area-b-pressure", "Area B safe pressure", "bara", "AREA-B-P-v1",
                model -> stream(model, "area-b").getPressure("bara"))
            .acceptanceRange(null, Double.valueOf(10.0)).deadlineSeconds(1.0).build())
        .addRequirementId("DYN-C037").addEquipmentTag("AREA-A/PT-101").addEquipmentTag("AREA-B/PT-201").build();
  }

  private static ProcessModel streamModel() {
    ProcessModel model = new ProcessModel();
    model.add("area-a", streamProcess("area-a"));
    model.add("area-b", streamProcess("area-b"));
    return model;
  }

  private static ProcessSystem streamProcess(String name) {
    ProcessSystem process = new ProcessSystem(name);
    Stream feed = new Stream("feed", fluid());
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);
    process.run();
    return process;
  }

  private static SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static Stream stream(ProcessModel model, String area) {
    return (Stream) model.get(area).getUnit("feed");
  }

  private static ProcessSystem process(String name, StatefulUnit unit) {
    ProcessSystem process = new ProcessSystem(name);
    process.add(unit);
    return process;
  }

  /** Cross-area protection logic used by the executable reference scenario. */
  private static final class CrossAreaPressureLogic implements ProcessLogic {
    private static final long serialVersionUID = 1000L;
    private final Stream first;
    private final Stream second;
    private LogicState state = LogicState.IDLE;

    private CrossAreaPressureLogic(ProcessModel model) {
      first = stream(model, "area-a");
      second = stream(model, "area-b");
    }

    @Override
    public String getName() {
      return "cross-area pressure trip";
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
      first.setPressure(5.0, "bara");
      second.setPressure(5.0, "bara");
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
      List<ProcessEquipmentInterface> equipment = new ArrayList<ProcessEquipmentInterface>();
      equipment.add(first);
      equipment.add(second);
      return equipment;
    }

    @Override
    public String getStatusDescription() {
      return state.name();
    }
  }

  /** Logic that exposes post-failure state without modifying the physical model. */
  private static final class ModelStateLogic implements ProcessLogic, DynamicSafetyScenarioEvidenceProvider {
    private static final long serialVersionUID = 1000L;
    private final StatefulUnit first;
    private final StatefulUnit second;
    private LogicState state = LogicState.IDLE;

    private ModelStateLogic(StatefulUnit first, StatefulUnit second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public String getName() {
      return "model-state-evidence";
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
    }

    @Override
    public boolean isActive() {
      return state == LogicState.RUNNING;
    }

    @Override
    public boolean isComplete() {
      return false;
    }

    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      List<ProcessEquipmentInterface> equipment = new ArrayList<ProcessEquipmentInterface>();
      equipment.add(first);
      equipment.add(second);
      return equipment;
    }

    @Override
    public String getStatusDescription() {
      return state.name();
    }

    @Override
    public Map<String, Object> getDynamicSafetyScenarioEvidence() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("firstValue", Double.valueOf(first.getValue()));
      evidence.put("secondValue", Double.valueOf(second.getValue()));
      return evidence;
    }
  }

  /** Transactional test equipment with a controlled later-area failure. */
  private static final class StatefulUnit extends ProcessEquipmentBaseClass
      implements TransientStateParticipant<StatefulUnit.Snapshot> {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final double rate;
    private final boolean failAfterMutation;
    private double value;

    private StatefulUnit(String name, String stateIdentity, double rate, boolean failAfterMutation) {
      super(name);
      this.stateIdentity = stateIdentity;
      this.rate = rate;
      this.failAfterMutation = failAfterMutation;
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public void runTransient(double dt, UUID id) {
      value += rate * dt;
      increaseTime(dt);
      setCalculationIdentifier(id);
      if (failAfterMutation) {
        throw new RuntimeException("intentional later-area failure");
      }
    }

    @Override
    public String getTransientStateIdentity() {
      return stateIdentity;
    }

    @Override
    public Snapshot captureTransientState() {
      return new Snapshot(value, getTime(), getCalculationIdentifier());
    }

    @Override
    public void restoreTransientState(Snapshot snapshot) {
      value = snapshot.value;
      setTime(snapshot.time);
      setCalculationIdentifier(snapshot.calculationIdentifier);
    }

    private double getValue() {
      return value;
    }

    /** Immutable transient snapshot. */
    private static final class Snapshot implements Serializable {
      private static final long serialVersionUID = 1000L;
      private final double value;
      private final double time;
      private final UUID calculationIdentifier;

      private Snapshot(double value, double time, UUID calculationIdentifier) {
        this.value = value;
        this.time = time;
        this.calculationIdentifier = calculationIdentifier;
      }
    }
  }
}
