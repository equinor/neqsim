package neqsim.process.safety.esd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.operations.OperationalTagBinding;
import neqsim.process.operations.OperationalTagMap;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for emergency shutdown dynamic test reporting.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class EmergencyShutdownTestRunnerTest {

  /**
   * Verifies that a simple ESD isolation-valve trip completes and produces evidence JSON.
   */
  @Test
  void runnerPassesWhenIsolationValveClosesWithinCriteria() {
    ProcessSystem process = createProcess();
    ESDLogic esdLogic = createEsdLogic(process);
    OperationalTagMap tagMap = createTagMap();

    EmergencyShutdownTestPlan plan = EmergencyShutdownTestPlan.builder("ESD1 isolation closure")
        .duration(8.0).timeStep(1.0).tagMap(tagMap).enableLogic("ESD Level 1")
        .triggerLogic("ESD Level 1").fieldData("xv_opening", 0.0)
        .criterion(
            EmergencyShutdownTestCriterion.finalAtMost("ESD-XV-CLOSED", "xv_opening", 5.0, "%")
                .withClause("NORSOK S-001 Clause 11"))
        .criterion(
            EmergencyShutdownTestCriterion.logicCompleted("ESD-LOGIC-COMPLETE", "ESD Level 1"))
        .criterion(EmergencyShutdownTestCriterion.fieldAbsoluteDeviationAtMost("ESD-FIELD-MATCH",
            "xv_opening", 5.0, "%"))
        .criterion(EmergencyShutdownTestCriterion.noSimulationErrors("ESD-NO-SIM-ERRORS"))
        .standardReference("NORSOK S-001 Clause 11 emergency shutdown testing")
        .evidenceReference("Cause and effect matrix row ESD-001").build();

    EmergencyShutdownTestResult result = EmergencyShutdownTestRunner.run(process, plan, esdLogic);

    assertEquals(EmergencyShutdownTestResult.Verdict.PASS, result.getVerdict(), result.toJson());
    assertEquals("COMPLETED", result.getLogicStates().get("ESD Level 1"));
    assertTrue(result.getSignalStats().get("xv_opening").getFinalValue() <= 5.0);
    assertFalse(result.getTimeSeries().isEmpty());
    assertTrue(result.toJson().contains("NORSOK S-001 Clause 11"));
  }

  /**
   * Verifies that field comparison criteria fail when tagreader evidence contradicts the model.
   */
  @Test
  void runnerFailsWhenFieldComparisonExceedsTolerance() {
    ProcessSystem process = createProcess();
    ESDLogic esdLogic = createEsdLogic(process);

    EmergencyShutdownTestPlan plan =
        EmergencyShutdownTestPlan.builder("ESD field mismatch").duration(8.0).timeStep(1.0)
            .tagMap(createTagMap()).enableLogic("ESD Level 1").triggerLogic("ESD Level 1")
            .fieldData("xv_opening", 100.0).criterion(EmergencyShutdownTestCriterion
                .fieldAbsoluteDeviationAtMost("ESD-FIELD-MATCH", "xv_opening", 5.0, "%"))
            .build();

    EmergencyShutdownTestResult result = EmergencyShutdownTestRunner.run(process, plan, esdLogic);

    assertEquals(EmergencyShutdownTestResult.Verdict.FAIL, result.getVerdict(), result.toJson());
    assertFalse(result.getCriterionResults().get(0).isPassed());
    assertTrue(result.getFieldComparisons().get("xv_opening").hasBothValues());
  }

  /**
   * Creates a small process with an ESD valve feeding a separator.
   *
   * @return initialized process system
   */
  private ProcessSystem createProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setPressure(70.0, "bara");
    feed.setTemperature(25.0, "C");

    ESDValve isolationValve = new ESDValve("ESD Inlet Isolation", feed);
    isolationValve.setStrokeTime(4.0);
    isolationValve.setCv(500.0);
    isolationValve.energize();
    isolationValve.setPercentValveOpening(100.0);

    Separator separator = new Separator("HP Separator", isolationValve.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(isolationValve);
    process.add(separator);
    process.run();
    return process;
  }

  /**
   * Creates ESD logic that trips the inlet isolation valve.
   *
   * @param process process containing the valve
   * @return ESD logic sequence
   */
  private ESDLogic createEsdLogic(ProcessSystem process) {
    ESDValve valve = (ESDValve) process.getUnit("ESD Inlet Isolation");
    ESDLogic esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new TripValveAction(valve), 0.0);
    return esdLogic;
  }

  /**
   * Creates logical tag bindings for ESD evidence reporting.
   *
   * @return operational tag map
   */
  private OperationalTagMap createTagMap() {
    return new OperationalTagMap()
        .addBinding(OperationalTagBinding.builder("xv_opening").historianTag("PRIVATE-XV-001-ZSO")
            .pidReference("P&ID-ESD-001/XV-001")
            .automationAddress("ESD Inlet Isolation.percentValveOpening").unit("%")
            .role(InstrumentTagRole.BENCHMARK).build())
        .addBinding(OperationalTagBinding.builder("separator_pressure")
            .historianTag("PRIVATE-PT-001").pidReference("P&ID-ESD-001/PT-001")
            .automationAddress("HP Separator.gasOutStream.pressure").unit("bara")
            .role(InstrumentTagRole.BENCHMARK).build());
  }
}
