package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
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

/** Qualifies demanded ESD final-element response, faults, and study traceability. */
class DynamicSafetyScenarioFinalElementFaultTest {
  private static final double TOLERANCE = 1.0e-12;

  /** Healthy, slow, and unavailable actuators retain distinct quantitative verdicts. */
  @Test
  void distinguishesHealthySlowAndUnavailableFinalElements() {
    DynamicSafetyScenarioResult healthy = runScenario("HEALTHY", 2.0, true, 0.25);
    DynamicSafetyScenarioResult slow = runScenario("SLOW", 4.0, true, 0.25);
    DynamicSafetyScenarioResult unavailable = runScenario("UNAVAILABLE", 2.0, false, 0.25);

    assertTrue(healthy.isPassed());
    assertEquals(3.5, healthy.getCriterionResults().get("esdv-safe-position").getFirstSatisfiedSeconds().doubleValue(),
        TOLERANCE);
    assertFalse(slow.isPassed());
    assertEquals(5.5, slow.getCriterionResults().get("esdv-safe-position").getFirstSatisfiedSeconds().doubleValue(),
        TOLERANCE);
    assertFalse(unavailable.isPassed());
    assertNull(unavailable.getCriterionResults().get("esdv-safe-position").getFirstSatisfiedSeconds());

    assertTimingEvidence(healthy);
    assertTimingEvidence(slow);
    assertTimingEvidence(unavailable);
  }

  /** Ordered requirement/tag identities survive execution and deterministic JSON export. */
  @Test
  void preservesTraceabilityOrderAndDeterministicRefinement() {
    DynamicSafetyScenarioResult coarse = runScenario("TRACEABLE", 2.0, true, 0.25);
    DynamicSafetyScenarioResult repeat = runScenario("TRACEABLE", 2.0, true, 0.25);
    DynamicSafetyScenarioResult refined = runScenario("TRACEABLE-REFINED", 2.0, true, 0.125);

    assertEquals(Arrays.asList("REQ-ESD-101", "REQ-ESD-102"), coarse.getRequirementIds());
    assertEquals(Arrays.asList("PT-101A", "PT-101B", "PT-101C", "ESDV-101"), coarse.getEquipmentTags());
    assertEquals(coarse.toJson(), repeat.toJson());
    assertEquals(3.5, refined.getCriterionResults().get("esdv-safe-position").getFirstSatisfiedSeconds().doubleValue(),
        TOLERANCE);
    assertTrue(coarse.toJson().contains("\"requirementIds\""));
    assertTrue(coarse.toJson().contains("\"equipmentTags\""));
  }

  /** The base model remains unchanged because every scenario executes on an isolated process copy. */
  @Test
  void isolatesFaultConfigurationFromBaseProcess() {
    ProcessSystem base = esdProcess();
    ESDValve baseValve = (ESDValve) base.getUnit("ESDV-101");
    DynamicSafetyScenarioResult result = DynamicSafetyScenarioRunner.run(base, scenario("ISOLATED", 2.0, false, 0.25));

    assertFalse(result.isPassed());
    assertTrue(baseValve.isActuatorAvailable());
    assertTrue(baseValve.isEnergized());
    assertEquals(100.0, baseValve.getPercentValveOpening(), TOLERANCE);
  }

  /** Rejected trials and Java restart preserve explicit final-element unavailability. */
  @Test
  void restoresAndSerializesActuatorAvailability() throws Exception {
    ProcessSystem process = esdProcess();
    ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
    valve.setActuatorAvailable(false);

    try (TransientStepTransaction transaction = process.beginTransientStepTransaction()) {
      valve.setActuatorAvailable(true);
      valve.deEnergize();
      process.runTransient(0.25, UUID.randomUUID());
      assertEquals(87.5, valve.getPercentValveOpening(), TOLERANCE);
      transaction.rollback();
    }

    assertFalse(valve.isActuatorAvailable());
    assertTrue(valve.isEnergized());
    assertEquals(100.0, valve.getPercentValveOpening(), TOLERANCE);
    ProcessSystem restarted = roundTrip(process);
    assertFalse(((ESDValve) restarted.getUnit("ESDV-101")).isActuatorAvailable());
  }

  private static DynamicSafetyScenarioResult runScenario(String id, double strokeSeconds, boolean available,
      double timeStepSeconds) {
    return DynamicSafetyScenarioRunner.run(esdProcess(), scenario(id, strokeSeconds, available, timeStepSeconds));
  }

  private static DynamicSafetyScenario scenario(String id, final double strokeSeconds, final boolean available,
      double timeStepSeconds) {
    return DynamicSafetyScenario.builder(id, "HP inlet isolation").durationSeconds(6.0).timeStepSeconds(timeStepSeconds)
        .triggerTimeSeconds(0.0).eventExactTiming(true).configureCase(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
            valve.setStrokeTime(strokeSeconds);
            valve.setActuatorAvailable(available);
          }
        }).initiatingEvent(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(70.0, "bara");
          }
        }).addLogic(new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ProcessLogic create(ProcessSystem process) {
            ESDLogic finalElements = new ESDLogic("ESD final elements");
            finalElements.addAction(new TripValveAction((ESDValve) process.getUnit("ESDV-101")), 0.0);
            return ClosedLoopSafetyFunction
                .builder(id, "2oo3 HP isolation", process, VotingPattern.TWO_OUT_OF_THREE, finalElements)
                .addChannel(channel("PT-101A")).addChannel(channel("PT-101B")).addChannel(channel("PT-101C"))
                .logicSolverDelaySeconds(0.5).build();
          }
        }).addCriterion(DynamicScenarioCriterion.builder("esdv-safe-position", "Inlet ESD valve at safe position", "%",
            new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              @Override
              public double extract(ProcessSystem process) {
                return ((ESDValve) process.getUnit("ESDV-101")).getPercentValveOpening();
              }
            }).acceptanceRange(null, Double.valueOf(5.0)).deadlineSeconds(3.5).build())
        .addRequirementId("REQ-ESD-101").addRequirementId("REQ-ESD-102").addEquipmentTag("PT-101A")
        .addEquipmentTag("PT-101B").addEquipmentTag("PT-101C").addEquipmentTag("ESDV-101")
        .addEvidenceReference("SRS-ESD-101").build();
  }

  private static SafetyFunctionChannel channel(String tag) {
    return SafetyFunctionChannel.highTrip(tag, "bara", 60.0, new SafetyFunctionChannel.SignalExtractor() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double extract(ProcessSystem process) {
        return ((Stream) process.getUnit("FEED")).getPressure("bara");
      }
    }).responseDelaySeconds(1.0).build();
  }

  private static void assertTimingEvidence(DynamicSafetyScenarioResult result) {
    Map<String, Object> evidence = result.getLogicEvidence().get("2oo3 HP isolation");
    assertEquals(1.0, ((Double) evidence.get("firstVoteSeconds")).doubleValue(), TOLERANCE);
    assertEquals(1.5, ((Double) evidence.get("finalElementActuationSeconds")).doubleValue(), TOLERANCE);
  }

  private static ProcessSystem esdProcess() {
    ProcessSystem process = new ProcessSystem("DYN-C027 final-element study");
    Stream feed = new Stream("FEED", fluid());
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve valve = new ESDValve("ESDV-101", feed);
    valve.setCv(500.0);
    valve.setStrokeTime(2.0);
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

  private static ProcessSystem roundTrip(ProcessSystem process) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(process);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ProcessSystem) input.readObject();
    }
  }
}
