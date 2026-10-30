package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Quantifies opt-in event-exact scenario timing, SIF response, and degraded sensor behavior. */
class DynamicSafetyScenarioEventTimingTest {

  /** A non-grid event and final duration must become accepted physical-step boundaries. */
  @Test
  void splitsTriggerAndDurationBoundariesExactly() {
    ProcessSystem base = streamProcess();
    double originalPressure = ((Stream) base.getUnit("FEED")).getPressure("bara");
    DynamicSafetyScenario scenario = DynamicSafetyScenario.builder("EVENT-EXACT", "Non-grid event")
        .durationSeconds(2.3).timeStepSeconds(1.0).triggerTimeSeconds(0.4).eventExactTiming(true)
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
        .addEvidenceReference("SRS-EVENT-EXACT").build();

    DynamicSafetyScenarioResult result = DynamicSafetyScenarioRunner.run(base, scenario);

    assertTrue(result.isPassed());
    assertEquals("EVENT_EXACT", result.getTimingPolicy());
    assertEquals(2.3, result.getCompletedDurationSeconds(), 1.0e-12);
    assertEquals(3, result.getPhysicalStepSeconds().size());
    assertEquals(0.4, result.getPhysicalStepSeconds().get(0).doubleValue(), 1.0e-12);
    assertEquals(1.0, result.getPhysicalStepSeconds().get(1).doubleValue(), 1.0e-12);
    assertEquals(0.9, result.getPhysicalStepSeconds().get(2).doubleValue(), 1.0e-12);
    assertEquals(1.0,
        result.getCriterionResults().get("safe-pressure").getFirstSatisfiedSeconds().doubleValue(), 1.0e-12);
    assertEquals(originalPressure, ((Stream) base.getUnit("FEED")).getPressure("bara"), 1.0e-12);
    assertTrue(result.toJson().contains("\"timingPolicy\": \"EVENT_EXACT\""));
  }

  /** The declared channel, logic-solver, and actuator delays must sum to the safe-state time. */
  @Test
  void resolvesDetectionLogicAndActuatorResponse() {
    DynamicSafetyScenarioResult first = DynamicSafetyScenarioRunner.run(esdProcess(),
        esdScenario("SIF-TIMING", SafetyFunctionChannel.FaultMode.HEALTHY));
    DynamicSafetyScenarioResult second = DynamicSafetyScenarioRunner.run(esdProcess(),
        esdScenario("SIF-TIMING", SafetyFunctionChannel.FaultMode.HEALTHY));

    assertTrue(first.isPassed());
    Map<String, Object> evidence = first.getLogicEvidence().get("2oo3 HP isolation");
    assertEquals(1.0, ((Double) evidence.get("firstVoteSeconds")).doubleValue(), 1.0e-12);
    assertEquals(1.5, ((Double) evidence.get("finalElementActuationSeconds")).doubleValue(), 1.0e-12);
    assertEquals(3.5,
        first.getCriterionResults().get("esdv-closed").getFirstSatisfiedSeconds().doubleValue(), 1.0e-12);
    assertEquals(16, first.getPhysicalStepSeconds().size());
    assertEquals(first.toJson(), second.toJson(), "independent exact-timing runs must be deterministic");
  }

  /** One dangerous channel failure plus one bypass must prevent a 2oo3 vote and remain a visible FAIL. */
  @Test
  void retainsFailedSafeStateForInsufficientHealthyVotes() {
    DynamicSafetyScenarioResult result = DynamicSafetyScenarioRunner.run(esdProcess(),
        esdScenario("SIF-DEGRADED", SafetyFunctionChannel.FaultMode.STUCK_NORMAL));

    assertTrue(!result.isPassed());
    Map<String, Object> evidence = result.getLogicEvidence().get("2oo3 HP isolation");
    assertNull(evidence.get("firstVoteSeconds"));
    assertNull(evidence.get("finalElementActuationSeconds"));
    assertNull(result.getCriterionResults().get("esdv-closed").getFirstSatisfiedSeconds());
  }

  private static DynamicSafetyScenario esdScenario(String id, SafetyFunctionChannel.FaultMode secondChannelFault) {
    return DynamicSafetyScenario.builder(id, "HP inlet isolation").durationSeconds(4.0).timeStepSeconds(0.25)
        .triggerTimeSeconds(0.0).eventExactTiming(true)
        .initiatingEvent(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(70.0, "bara");
          }
        }).addLogic(new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ProcessLogic create(ProcessSystem process) {
            ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
            ESDLogic finalElements = new ESDLogic("ESD final elements");
            finalElements.addAction(new TripValveAction(valve), 0.0);
            return ClosedLoopSafetyFunction
                .builder(id, "2oo3 HP isolation", process, VotingPattern.TWO_OUT_OF_THREE, finalElements)
                .addChannel(channel("PT-A", SafetyFunctionChannel.FaultMode.HEALTHY))
                .addChannel(channel("PT-B", secondChannelFault))
                .addChannel(channel("PT-C", SafetyFunctionChannel.FaultMode.BYPASSED))
                .logicSolverDelaySeconds(0.5).build();
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("esdv-closed", "Inlet ESD valve closed", "%", new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              @Override
              public double extract(ProcessSystem process) {
                return ((ESDValve) process.getUnit("ESDV-101")).getPercentValveOpening();
              }
            }).acceptanceRange(null, Double.valueOf(5.0)).deadlineSeconds(3.5).build())
        .addEvidenceReference("SRS-EVENT-TIMING").build();
  }

  private static SafetyFunctionChannel channel(String tag, SafetyFunctionChannel.FaultMode faultMode) {
    return SafetyFunctionChannel.highTrip(tag, "bara", 60.0, new SafetyFunctionChannel.SignalExtractor() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double extract(ProcessSystem process) {
        return ((Stream) process.getUnit("FEED")).getPressure("bara");
      }
    }).responseDelaySeconds(1.0).faultMode(faultMode).build();
  }

  private static ProcessSystem streamProcess() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("FEED", fluid());
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);
    process.run();
    return process;
  }

  private static ProcessSystem esdProcess() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("FEED", fluid());
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve valve = new ESDValve("ESDV-101", feed);
    valve.setCv(500.0);
    valve.setStrokeTime(2.0);
    valve.setPhysicalStepIdempotencyEnabled(true);
    process.add(feed);
    process.add(valve);
    process.run();
    return process;
  }

  private static SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static final class PressureTripLogic implements ProcessLogic {
    private static final long serialVersionUID = 1000L;
    private final Stream stream;
    private LogicState state = LogicState.IDLE;

    private PressureTripLogic(Stream stream) {
      this.stream = stream;
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
      stream.setPressure(5.0, "bara");
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
