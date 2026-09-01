package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.processmodel.ProcessSystem;
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
