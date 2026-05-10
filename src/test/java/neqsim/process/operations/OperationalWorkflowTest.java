package neqsim.process.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.validation.ValidationResult;

/**
 * Tests for operational P&amp;ID and tag-driven workflow helpers.
 *
 * @author ESOL
 * @version 1.0
 */
class OperationalWorkflowTest {

  /**
   * Verifies that logical and historian-keyed values can update measurement devices and automation
   * variables through the operational tag map.
   */
  @Test
  void tagMapAppliesLogicalAndHistorianValues() {
    ProcessSystem process = createProcess();
    PressureTransmitter pressure =
        new PressureTransmitter("Feed PT", (Stream) process.getUnit("feed"));
    pressure.setUnit("bara");
    pressure.setTag("PRIVATE-PT-001");
    pressure.setTagRole(InstrumentTagRole.INPUT);
    process.add(pressure);

    OperationalTagMap tagMap = new OperationalTagMap()
        .addBinding(OperationalTagBinding.builder("feed_pressure").historianTag("PRIVATE-PT-001")
            .unit("bara").role(InstrumentTagRole.INPUT).build())
        .addBinding(OperationalTagBinding.builder("outlet_valve_position")
            .historianTag("PRIVATE-PV-001").automationAddress("Outlet Valve.percentValveOpening")
            .unit("%").role(InstrumentTagRole.INPUT).build());

    ValidationResult validation = tagMap.validate(process);
    assertTrue(validation.isValid(), validation.getReport());

    Map<String, Double> fieldData = new HashMap<String, Double>();
    fieldData.put("feed_pressure", 75.0);
    fieldData.put("PRIVATE-PV-001", 35.0);

    Map<String, Double> applied = tagMap.applyFieldData(process, fieldData);
    assertEquals(2, applied.size());
    assertEquals(75.0, ((Stream) process.getUnit("feed")).getPressure(), 0.1);
    assertEquals(35.0, ((ThrottlingValve) process.getUnit("Outlet Valve")).getPercentValveOpening(),
        1.0e-10);

    Map<String, Double> values = tagMap.readValues(process);
    assertEquals(35.0, values.get("outlet_valve_position"), 1.0e-10);
  }

  /**
   * Verifies that operational scenarios reuse existing valve logic, automation writes, and
   * steady-state process execution.
   */
  @Test
  void scenarioRunnerUsesExistingValveActionAndAutomation() {
    ProcessSystem process = createProcess();
    OperationalScenario scenario = OperationalScenario.builder("partly close outlet")
        .addAction(OperationalAction.setValveOpening("Outlet Valve", 15.0))
        .addAction(OperationalAction.setVariable("Outlet Valve.outletPressure", 45.0, "bara"))
        .addAction(OperationalAction.runSteadyState()).build();

    OperationalScenarioResult result = OperationalScenarioRunner.run(process, scenario);
    assertTrue(result.isSuccessful(), result.getErrors().toString());
    assertFalse(result.getActionLog().isEmpty());
    assertEquals(15.0, ((ThrottlingValve) process.getUnit("Outlet Valve")).getPercentValveOpening(),
        1.0e-10);
    assertEquals(45.0, ((ThrottlingValve) process.getUnit("Outlet Valve")).getOutletPressure(),
        0.5);
    assertTrue(result.getBeforeValues().containsKey("Outlet Valve.percentValveOpening"));
    assertTrue(result.getAfterValues().containsKey("Outlet Valve.outletPressure"));
  }

  /**
   * Verifies controller-response metrics and recommendations for acceptable and saturated cases.
   */
  @Test
  void controllerTuningStudyFlagsGoodAndSaturatedResponses() {
    double[] time = new double[] {0.0, 10.0, 20.0, 30.0, 40.0, 50.0};
    double[] processValue = new double[] {0.0, 0.55, 0.82, 0.95, 0.99, 1.0};
    double[] output = new double[] {40.0, 55.0, 58.0, 53.0, 50.0, 50.0};

    ControllerTuningResult good = ControllerTuningStudy.evaluateStepResponse("LC-001", 1.0, time,
        processValue, output, 0.0, 100.0, 0.05);
    assertTrue(good.isStableAtEnd());
    assertEquals("ACCEPTABLE_SCREENING_RESULT", good.getRecommendation());
    assertTrue(good.getIntegralAbsoluteError() > 0.0);

    double[] saturatedOutput = new double[] {0.0, 100.0, 100.0, 100.0, 100.0, 100.0};
    ControllerTuningResult saturated = ControllerTuningStudy.evaluateStepResponse("LC-002", 1.0,
        time, processValue, saturatedOutput, 0.0, 100.0, 0.05);
    assertEquals("CHECK_ACTUATOR_LIMITS_OR_PROCESS_CAPACITY", saturated.getRecommendation());
  }

  /**
   * Verifies that an operational evidence package combines tag reconciliation, scenarios, and
   * capacity bottleneck reporting.
   */
  @Test
  void evidencePackageReportsBenchmarkAndBottleneck() {
    ProcessSystem process = createProcess();
    OperationalTagMap tagMap = new OperationalTagMap()
        .addBinding(OperationalTagBinding.builder("outlet_valve_position")
            .automationAddress("Outlet Valve.percentValveOpening").unit("%")
            .role(InstrumentTagRole.INPUT).build())
        .addBinding(OperationalTagBinding.builder("outlet_pressure")
            .automationAddress("Outlet Valve.outletPressure").unit("bara")
            .role(InstrumentTagRole.BENCHMARK).build());

    Map<String, Double> fieldData = new HashMap<String, Double>();
    fieldData.put("outlet_valve_position", 70.0);
    fieldData.put("outlet_pressure", 49.0);

    OperationalScenario scenario = OperationalScenario.builder("raise valve loading")
        .addAction(OperationalAction.setValveOpening("Outlet Valve", 90.0))
        .addAction(OperationalAction.runSteadyState()).build();
    JsonObject report = OperationalEvidencePackage.buildReport("operations screen", process, tagMap,
        fieldData, Collections.singletonList(scenario), 0.05);

    assertTrue(
        report.getAsJsonObject("benchmarkComparison").get("allWithinTolerance").getAsBoolean(),
        report.toString());
    assertTrue(report.getAsJsonObject("baseCapacity").getAsJsonObject("bottleneck")
        .get("hasBottleneck").getAsBoolean(), report.toString());
    assertEquals(1, report.getAsJsonArray("scenarioStudies").size());
    assertTrue(report.getAsJsonArray("scenarioStudies").get(0).getAsJsonObject().get("successful")
        .getAsBoolean(), report.toString());
  }

  /**
   * Creates a small process with a separator and outlet valve.
   *
   * @return process system ready for operational workflow tests
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

    Separator separator = new Separator("Separator", feed);
    ThrottlingValve valve = new ThrottlingValve("Outlet Valve", separator.getGasOutStream());
    valve.setOutletPressure(50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(valve);
    process.run();
    return process;
  }
}
