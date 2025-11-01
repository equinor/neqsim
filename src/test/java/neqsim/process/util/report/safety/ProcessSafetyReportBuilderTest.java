package neqsim.process.util.report.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import neqsim.process.conditionmonitor.ConditionMonitor;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Integration tests for {@link ProcessSafetyReportBuilder}. */
public class ProcessSafetyReportBuilderTest {

  @Test
  public void testCriticalFindingsAreHighlighted() {
    ScenarioFixtures upset = createScenario("upset", 75.0, 90.0);

    ProcessSafetyThresholds thresholds = new ProcessSafetyThresholds()
        .setMinSafetyMarginWarning(0.20).setMinSafetyMarginCritical(0.05)
        .setReliefUtilisationWarning(0.4).setReliefUtilisationCritical(0.7)
        .setEntropyChangeWarning(0.1).setEntropyChangeCritical(1.0)
        .setExergyChangeWarning(100.0).setExergyChangeCritical(500.0);

    ProcessSafetyReport report = new ProcessSafetyReportBuilder(upset.process)
        .withScenarioLabel("compressor-upset").withConditionMonitor(upset.monitor)
        .withThresholds(thresholds).build();

    assertFalse(report.getConditionFindings().isEmpty(), "Condition findings should be present");
    assertEquals(SeverityLevel.CRITICAL, report.getConditionFindings().get(0).getSeverity());

    ProcessSafetyReport.SafetyMarginAssessment margin = report.getSafetyMargins().stream()
        .filter(m -> upset.compressor.getName().equals(m.getUnitName())).findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Expected safety margin assessment for " + upset.compressor.getName()));
    assertEquals(SeverityLevel.CRITICAL, margin.getSeverity());

    ProcessSafetyReport.ReliefDeviceAssessment reliefAssessment = report.getReliefDeviceAssessments()
        .stream().filter(r -> upset.reliefValve.getName().equals(r.getUnitName())).findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Expected relief device assessment for " + upset.reliefValve.getName()));
    assertEquals(SeverityLevel.CRITICAL, reliefAssessment.getSeverity());

    assertNotNull(report.getSystemKpis());
    assertTrue(report.toJson().contains("compressor-upset"));
    assertTrue(report.toCsv().startsWith("Category"));
    assertTrue(report.toUiModel().containsKey("systemKpis"));
  }

  @Test
  public void testScenarioComparisonShowsDifferentSeverities() {
    ScenarioFixtures baseline = createScenario("baseline", 60.0, 10.0);
    ScenarioFixtures upset = createScenario("upset", 75.0, 90.0);

    ProcessSafetyThresholds thresholds = new ProcessSafetyThresholds()
        .setMinSafetyMarginWarning(0.20).setMinSafetyMarginCritical(0.05)
        .setReliefUtilisationWarning(0.4).setReliefUtilisationCritical(0.7);

    ProcessSafetyReport baselineReport = new ProcessSafetyReportBuilder(baseline.process)
        .withScenarioLabel("baseline").withConditionMonitor(baseline.monitor)
        .withThresholds(thresholds).build();

    ProcessSafetyReport upsetReport = new ProcessSafetyReportBuilder(upset.process)
        .withScenarioLabel("upset").withConditionMonitor(upset.monitor).withThresholds(thresholds)
        .build();

    Optional<ProcessSafetyReport.SafetyMarginAssessment> baselineMargin = baselineReport
        .getSafetyMargins().stream()
        .filter(m -> baseline.compressor.getName().equals(m.getUnitName())).findFirst();
    Optional<ProcessSafetyReport.SafetyMarginAssessment> upsetMargin = upsetReport.getSafetyMargins()
        .stream().filter(m -> upset.compressor.getName().equals(m.getUnitName())).findFirst();

    assertTrue(baselineMargin.isPresent());
    assertTrue(upsetMargin.isPresent());
    assertTrue(baselineMargin.get().getSeverity().ordinal() <= SeverityLevel.WARNING.ordinal());
    assertEquals(SeverityLevel.CRITICAL, upsetMargin.get().getSeverity());

    Optional<ProcessSafetyReport.ReliefDeviceAssessment> baselineRelief = baselineReport
        .getReliefDeviceAssessments().stream()
        .filter(r -> baseline.reliefValve.getName().equals(r.getUnitName())).findFirst();
    Optional<ProcessSafetyReport.ReliefDeviceAssessment> upsetRelief = upsetReport
        .getReliefDeviceAssessments().stream()
        .filter(r -> upset.reliefValve.getName().equals(r.getUnitName())).findFirst();

    assertTrue(baselineRelief.isPresent());
    assertTrue(upsetRelief.isPresent());
    assertTrue(baselineRelief.get().getSeverity().ordinal() <= SeverityLevel.WARNING.ordinal());
    assertEquals(SeverityLevel.CRITICAL, upsetRelief.get().getSeverity());
  }

  private ScenarioFixtures createScenario(String scenarioName, double outletPressureBar,
      double valveOpeningPercent) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 100.0);
    fluid.addComponent("n-heptane", 5.0);
    fluid.setMixingRule(2);

    ProcessSystem process = new ProcessSystem();

    Stream feed = new Stream("feed", fluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(35.0, "C");
    feed.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("separator", feed);
    Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
    compressor.setOutletPressure(outletPressureBar, "bara");
    compressor.initMechanicalDesign();
    compressor.getMechanicalDesign().setMaxOperationPressure(60.0);

    SafetyReliefValve reliefValve = new SafetyReliefValve("relief", compressor.getOutStream());
    reliefValve.setSetPressureBar(62.0);
    reliefValve.setPercentValveOpening(valveOpeningPercent);

    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(reliefValve);
    process.run();

    ConditionMonitor monitor = new ConditionMonitor(process);
    ProcessSystem monitorProcess = monitor.getProcess();
    ProcessEquipmentBaseClass monitorCompressor = (ProcessEquipmentBaseClass) monitorProcess
        .getUnit(compressor.getName());
    monitorCompressor.conditionAnalysisMessage =
        scenarioName.equals("upset") ? "CRITICAL: High vibration detected" : "Monitoring";

    return new ScenarioFixtures(process, monitor, compressor, reliefValve);
  }

  private static final class ScenarioFixtures {
    final ProcessSystem process;
    final ConditionMonitor monitor;
    final Compressor compressor;
    final SafetyReliefValve reliefValve;

    ScenarioFixtures(ProcessSystem process, ConditionMonitor monitor, Compressor compressor,
        SafetyReliefValve reliefValve) {
      this.process = process;
      this.monitor = monitor;
      this.compressor = compressor;
      this.reliefValve = reliefValve;
    }
  }
}
