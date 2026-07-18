package neqsim.process.safety.sif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Verifies process detection, voting, logic delay, and physical final-element response in one loop. */
class ClosedLoopSafetyFunctionTest {

  @Test
  void twoOutOfThreePressureVoteTripsCopiedEsdValveWithOneBypassedChannel() {
    ProcessSystem base = process();
    ESDValve designValve = (ESDValve) base.getUnit("ESDV-101");

    DynamicSafetyScenario scenario = DynamicSafetyScenario.builder("SIF-HP-101", "HP inlet isolation")
        .durationSeconds(6.0).timeStepSeconds(1.0).triggerTimeSeconds(0.0)
        .initiatingEvent(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(70.0, "bara");
          }
        }).addLogic(new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ClosedLoopSafetyFunction create(ProcessSystem process) {
            ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
            ESDLogic finalElements = new ESDLogic("ESD-101 final elements");
            finalElements.addAction(new TripValveAction(valve), 0.0);
            return ClosedLoopSafetyFunction
                .builder("SIF-HP-101", "2oo3 high-pressure isolation", process,
                    VotingPattern.TWO_OUT_OF_THREE, finalElements)
                .addChannel(highPressureChannel("PT-101A", SafetyFunctionChannel.FaultMode.HEALTHY))
                .addChannel(highPressureChannel("PT-101B", SafetyFunctionChannel.FaultMode.HEALTHY))
                .addChannel(highPressureChannel("PT-101C", SafetyFunctionChannel.FaultMode.BYPASSED))
                .logicSolverDelaySeconds(0.5).build();
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("esdv-closed", "Inlet ESD valve closed", "%",
                new DynamicScenarioCriterion.Extractor() {
                  private static final long serialVersionUID = 1000L;

                  @Override
                  public double extract(ProcessSystem process) {
                    return ((ESDValve) process.getUnit("ESDV-101")).getPercentValveOpening();
                  }
                })
            .acceptanceRange(null, Double.valueOf(5.0)).deadlineSeconds(4.0).build())
        .addEvidenceReference("SRS-SIF-HP-101").build();

    DynamicSafetyScenarioResult result = DynamicSafetyScenarioRunner.run(base, scenario);

    assertTrue(result.isPassed());
    assertTrue(designValve.isEnergized(), "the design case must remain isolated from scenario execution");
    assertEquals(100.0, designValve.getPercentValveOpening(), 1.0e-12);
    Map<String, Object> evidence = result.getLogicEvidence().get("2oo3 high-pressure isolation");
    assertNotNull(evidence);
    assertEquals("2oo3", evidence.get("votingPattern"));
    assertNotNull(evidence.get("firstVoteSeconds"));
    assertNotNull(evidence.get("finalElementActuationSeconds"));
    assertTrue(result.toJson().contains("closed_loop_sif_evidence.v1"));
  }

  @Test
  void rejectsChannelCountThatDoesNotMatchVotingPattern() {
    ProcessSystem process = process();
    ESDLogic finalElements = new ESDLogic("final elements");
    finalElements.addAction(new TripValveAction((ESDValve) process.getUnit("ESDV-101")), 0.0);

    boolean rejected = false;
    try {
      ClosedLoopSafetyFunction
          .builder("SIF-BAD", "invalid SIF", process, VotingPattern.TWO_OUT_OF_THREE, finalElements)
          .addChannel(highPressureChannel("PT-A", SafetyFunctionChannel.FaultMode.HEALTHY)).build();
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    assertTrue(rejected);
    assertFalse(finalElements.isActive());
  }

  private static SafetyFunctionChannel highPressureChannel(String tag,
      SafetyFunctionChannel.FaultMode faultMode) {
    return SafetyFunctionChannel.highTrip(tag, "bara", 60.0, new SafetyFunctionChannel.SignalExtractor() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double extract(ProcessSystem process) {
        return ((Stream) process.getUnit("FEED")).getPressure("bara");
      }
    }).responseDelaySeconds(1.0).faultMode(faultMode).build();
  }

  private static ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve isolationValve = new ESDValve("ESDV-101", feed);
    isolationValve.setCv(500.0);
    isolationValve.setStrokeTime(2.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(isolationValve);
    process.run();
    return process;
  }
}
