package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for AutomaticScenarioGenerator safety scenario generation.
 */
public class AutomaticScenarioGeneratorTest {
  private ProcessSystem process;
  private AutomaticScenarioGenerator generator;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("inlet-valve", feed);
    valve.setOutletPressure(30.0, "bara");

    Separator separator = new Separator("hp-separator", valve.getOutletStream());

    Cooler cooler = new Cooler("export-cooler", separator.getGasOutStream());
    cooler.setOutTemperature(20.0, "C");

    process = new ProcessSystem();
    process.setName("TestProcess");
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.add(cooler);

    generator = new AutomaticScenarioGenerator(process);
  }

  @Test
  void testCreation() {
    assertNotNull(generator);
  }

  @Test
  void testIdentifyFailures() {
    List<AutomaticScenarioGenerator.EquipmentFailure> failures = generator.getIdentifiedFailures();

    assertNotNull(failures);
    assertFalse(failures.isEmpty());
  }

  @Test
  void testAddFailureModes() {
    AutomaticScenarioGenerator result =
        generator.addFailureModes(AutomaticScenarioGenerator.FailureMode.COOLING_LOSS,
            AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_CLOSED);

    // Should return same generator for chaining
    assertEquals(generator, result);
  }

  @Test
  void testEnableAllFailureModes() {
    AutomaticScenarioGenerator result = generator.enableAllFailureModes();

    assertEquals(generator, result);
  }

  @Test
  void testGenerateSingleFailures() {
    generator.addFailureModes(AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_CLOSED,
        AutomaticScenarioGenerator.FailureMode.COOLING_LOSS);

    List<ProcessSafetyScenario> scenarios = generator.generateSingleFailures();

    assertNotNull(scenarios);
    // Should have scenarios for valve and cooler failures
  }

  @Test
  void testGenerateSingleFailuresWithAllModes() {
    generator.enableAllFailureModes();

    List<ProcessSafetyScenario> scenarios = generator.generateSingleFailures();

    assertNotNull(scenarios);
    assertFalse(scenarios.isEmpty());
  }

  @Test
  void testGenerateCombinations() {
    generator.addFailureModes(AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_CLOSED,
        AutomaticScenarioGenerator.FailureMode.COOLING_LOSS);

    List<ProcessSafetyScenario> scenarios = generator.generateCombinations(2);

    assertNotNull(scenarios);
    // Should include single failures plus combinations
  }

  @Test
  void testGenerateCombinationsMaxSize3() {
    generator.enableAllFailureModes();

    List<ProcessSafetyScenario> scenarios = generator.generateCombinations(3);

    assertNotNull(scenarios);
  }

  @Test
  void testGetFailureModeSummary() {
    String summary = generator.getFailureModeSummary();

    assertNotNull(summary);
    assertTrue(summary.contains("Failure Mode Analysis Summary"));
    assertTrue(summary.contains("Total potential failures"));
  }

  @Test
  void testFailureModeEnum() {
    // Test all failure modes exist
    AutomaticScenarioGenerator.FailureMode[] modes =
        AutomaticScenarioGenerator.FailureMode.values();

    assertTrue(modes.length > 0);
  }

  @Test
  void testFailureModeDescription() {
    AutomaticScenarioGenerator.FailureMode mode =
        AutomaticScenarioGenerator.FailureMode.COOLING_LOSS;

    assertNotNull(mode.getDescription());
    assertEquals("Loss of Cooling", mode.getDescription());
  }

  @Test
  void testFailureModeCategory() {
    AutomaticScenarioGenerator.FailureMode mode =
        AutomaticScenarioGenerator.FailureMode.COOLING_LOSS;

    assertNotNull(mode.getCategory());
  }

  @Test
  void testFailureModeHazopDeviation() {
    AutomaticScenarioGenerator.FailureMode mode =
        AutomaticScenarioGenerator.FailureMode.COOLING_LOSS;

    assertNotNull(mode.getHazopDeviation());
    assertEquals(AutomaticScenarioGenerator.HazopDeviation.NO_FLOW, mode.getHazopDeviation());
  }

  @Test
  void testHazopDeviationEnum() {
    AutomaticScenarioGenerator.HazopDeviation[] deviations =
        AutomaticScenarioGenerator.HazopDeviation.values();

    assertTrue(deviations.length > 0);
    assertNotNull(AutomaticScenarioGenerator.HazopDeviation.NO_FLOW);
    assertNotNull(AutomaticScenarioGenerator.HazopDeviation.HIGH_PRESSURE);
    assertNotNull(AutomaticScenarioGenerator.HazopDeviation.HIGH_TEMPERATURE);
  }

  @Test
  void testEquipmentFailureGetters() {
    List<AutomaticScenarioGenerator.EquipmentFailure> failures = generator.getIdentifiedFailures();

    if (!failures.isEmpty()) {
      AutomaticScenarioGenerator.EquipmentFailure failure = failures.get(0);

      assertNotNull(failure.getEquipmentName());
      assertNotNull(failure.getEquipmentType());
      assertNotNull(failure.getMode());
    }
  }

  @Test
  void testEquipmentFailureToString() {
    List<AutomaticScenarioGenerator.EquipmentFailure> failures = generator.getIdentifiedFailures();

    if (!failures.isEmpty()) {
      String failureStr = failures.get(0).toString();
      assertNotNull(failureStr);
      assertFalse(failureStr.isEmpty());
    }
  }

  @Test
  void testEmptyProcessNoFailures() {
    ProcessSystem emptyProcess = new ProcessSystem();
    emptyProcess.setName("EmptyProcess");

    AutomaticScenarioGenerator emptyGen = new AutomaticScenarioGenerator(emptyProcess);

    // Should have no identified failures
    assertTrue(emptyGen.getIdentifiedFailures().isEmpty());
  }

  @Test
  void testProcessWithValveIdentifiesValveFailures() {
    generator.addFailureModes(AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_CLOSED,
        AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_OPEN);

    List<AutomaticScenarioGenerator.EquipmentFailure> failures = generator.getIdentifiedFailures();

    // Should have valve failures identified
    boolean hasValveFailure = failures.stream()
        .anyMatch(f -> f.getMode() == AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_CLOSED
            || f.getMode() == AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_OPEN);

    assertTrue(hasValveFailure);
  }

  @Test
  void testProcessWithCoolerIdentifiesCoolingLoss() {
    List<AutomaticScenarioGenerator.EquipmentFailure> failures = generator.getIdentifiedFailures();

    boolean hasCoolingLoss = failures.stream()
        .anyMatch(f -> f.getMode() == AutomaticScenarioGenerator.FailureMode.COOLING_LOSS);

    assertTrue(hasCoolingLoss);
  }

  @Test
  void testChainedConfiguration() {
    List<ProcessSafetyScenario> scenarios =
        generator.addFailureModes(AutomaticScenarioGenerator.FailureMode.VALVE_STUCK_CLOSED)
            .addFailureModes(AutomaticScenarioGenerator.FailureMode.COOLING_LOSS)
            .generateSingleFailures();

    assertNotNull(scenarios);
  }
}
