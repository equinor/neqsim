package neqsim.process.util.report.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.conditionmonitor.ConditionMonitor;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.report.Report;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.safety.ProcessSafetyReport.ConditionFinding;
import neqsim.process.util.report.safety.ProcessSafetyReport.ReliefDeviceAssessment;
import neqsim.process.util.report.safety.ProcessSafetyReport.SafetyMarginAssessment;
import neqsim.process.util.report.safety.ProcessSafetyReport.SystemKpiSnapshot;

/**
 * Builder that collects safety information for a {@link ProcessSystem}. The builder aggregates
 * condition monitoring messages, equipment safety margins, relief valve diagnostics and
 * thermodynamic KPIs before serializing the result using the familiar {@link Report} utilities.
 */
public class ProcessSafetyReportBuilder {
  private static final Logger logger = LogManager.getLogger(ProcessSafetyReportBuilder.class);

  private final ProcessSystem processSystem;
  private ConditionMonitor conditionMonitor;
  private ProcessSafetyThresholds thresholds;
  private ReportConfig reportConfig;
  private String scenarioLabel;

  /**
   * Create a builder for the supplied process system.
   *
   * @param processSystem process to analyse
   */
  public ProcessSafetyReportBuilder(ProcessSystem processSystem) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem");
  }

  /**
   * Provide a pre-configured {@link ConditionMonitor}. If not supplied the builder will instantiate
   * one based on the process itself.
   *
   * @param monitor monitor instance
   * @return this builder
   */
  public ProcessSafetyReportBuilder withConditionMonitor(ConditionMonitor monitor) {
    this.conditionMonitor = monitor;
    return this;
  }

  /**
   * Configure safety thresholds.
   *
   * @param thresholds thresholds
   * @return this builder
   */
  public ProcessSafetyReportBuilder withThresholds(ProcessSafetyThresholds thresholds) {
    this.thresholds = thresholds;
    return this;
  }

  /**
   * Configure report serialization options.
   *
   * @param cfg report configuration
   * @return this builder
   */
  public ProcessSafetyReportBuilder withReportConfig(ReportConfig cfg) {
    this.reportConfig = cfg;
    return this;
  }

  /**
   * Optional descriptive scenario label included in serialized output.
   *
   * @param label scenario label
   * @return this builder
   */
  public ProcessSafetyReportBuilder withScenarioLabel(String label) {
    this.scenarioLabel = label;
    return this;
  }

  /**
   * Build the {@link ProcessSafetyReport}. The method is thread-safe but will copy internal data to
   * avoid exposing mutable state.
   *
   * @return built report
   */
  public ProcessSafetyReport build() {
    ProcessSafetyThresholds appliedThresholds = new ProcessSafetyThresholds(thresholds);
    ConditionMonitor monitor = Optional.ofNullable(conditionMonitor)
        .orElseGet(() -> new ConditionMonitor(processSystem));

    // Run the condition monitoring analysis if not already performed.
    try {
      monitor.conditionAnalysis();
    } catch (Exception ex) {
      logger.warn("Condition analysis failed", ex);
    }

    List<ConditionFinding> findings = collectConditionFindings(monitor);
    List<SafetyMarginAssessment> safetyMargins = collectSafetyMargins(appliedThresholds);
    List<ReliefDeviceAssessment> reliefs = collectReliefAssessments(appliedThresholds);
    SystemKpiSnapshot kpis = collectSystemKpis(appliedThresholds);
    String equipmentJson = generateEquipmentJson();

    return new ProcessSafetyReport(scenarioLabel, appliedThresholds, findings, safetyMargins, reliefs,
        kpis, equipmentJson);
  }

  private List<ConditionFinding> collectConditionFindings(ConditionMonitor monitor) {
    List<ConditionFinding> findings = new ArrayList<>();
    String report = monitor.getReport();
    if (report == null || report.trim().isEmpty()) {
      return findings;
    }

    String[] segments = report.split("/");
    String currentUnit = null;
    for (String segment : segments) {
      String trimmed = segment == null ? null : segment.trim();
      if (trimmed == null || trimmed.isEmpty()) {
        continue;
      }
      String lower = trimmed.toLowerCase(Locale.ROOT);
      if (lower.endsWith("analysis started")) {
        currentUnit = trimmed.split(" ", 2)[0];
        continue;
      }
      if (lower.endsWith("analysis ended")) {
        currentUnit = null;
        continue;
      }

      SeverityLevel severity = classifyConditionSeverity(lower);
      findings.add(new ConditionFinding(currentUnit, trimmed, severity));
    }
    return findings;
  }

  private SeverityLevel classifyConditionSeverity(String messageLower) {
    if (messageLower == null) {
      return SeverityLevel.NORMAL;
    }
    if (messageLower.contains("critical") || messageLower.contains("too high")
        || messageLower.contains("error") || messageLower.contains("fail")) {
      return SeverityLevel.CRITICAL;
    }
    if (messageLower.contains("warn") || messageLower.contains("deviation")
        || messageLower.contains("monitor")) {
      return SeverityLevel.WARNING;
    }
    return SeverityLevel.WARNING;
  }

  private List<SafetyMarginAssessment> collectSafetyMargins(ProcessSafetyThresholds appliedThresholds) {
    List<SafetyMarginAssessment> margins = new ArrayList<>();
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      MechanicalDesign design;
      try {
        design = unit.getMechanicalDesign();
      } catch (Exception ex) {
        logger.debug("Mechanical design not available for {}", unit.getName(), ex);
        continue;
      }
      if (design == null) {
        continue;
      }
      double designPressure = design.getMaxDesignPressure();
      double operatingPressure;
      try {
        operatingPressure = unit.getPressure();
      } catch (Exception ex) {
        logger.debug("Unable to obtain operating pressure for {}", unit.getName(), ex);
        continue;
      }
      if (Double.isNaN(designPressure) || Double.isInfinite(designPressure) || designPressure == 0.0
          || Double.isNaN(operatingPressure) || Double.isInfinite(operatingPressure)) {
        continue;
      }
      double marginFraction = (designPressure - operatingPressure) / designPressure;
      SeverityLevel severity = gradeSafetyMargin(appliedThresholds, marginFraction);
      String notes = marginFraction < 0 ? "Operating pressure above design" : null;
      margins.add(new SafetyMarginAssessment(unit.getName(), designPressure, operatingPressure,
          marginFraction, severity, notes));
    }
    return margins;
  }

  private SeverityLevel gradeSafetyMargin(ProcessSafetyThresholds thresholds, double marginFraction) {
    if (Double.isNaN(marginFraction)) {
      return SeverityLevel.NORMAL;
    }
    if (marginFraction <= thresholds.getMinSafetyMarginCritical()) {
      return SeverityLevel.CRITICAL;
    }
    if (marginFraction <= thresholds.getMinSafetyMarginWarning()) {
      return SeverityLevel.WARNING;
    }
    return SeverityLevel.NORMAL;
  }

  private List<ReliefDeviceAssessment> collectReliefAssessments(
      ProcessSafetyThresholds appliedThresholds) {
    List<ReliefDeviceAssessment> reliefs = new ArrayList<>();
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof SafetyReliefValve) {
        reliefs.add(buildReliefAssessment((SafetyReliefValve) unit, appliedThresholds));
      } else if (unit instanceof SafetyValve) {
        reliefs.add(buildReliefAssessment((SafetyValve) unit, appliedThresholds));
      }
    }
    return reliefs;
  }

  private ReliefDeviceAssessment buildReliefAssessment(SafetyReliefValve valve,
      ProcessSafetyThresholds thresholds) {
    double setPressure = valve.getSetPressureBar();
    double relievingPressure = safeDouble(valve::getRelievingPressureBar);
    double upstreamPressure = safeDouble(valve::getInletPressure);
    double massFlow = extractMassFlow(valve.getInletStream());
    double utilisation = normalizeOpenFraction(valve);
    SeverityLevel severity = gradeUtilisation(thresholds, utilisation);
    return new ReliefDeviceAssessment(valve.getName(), setPressure, relievingPressure,
        upstreamPressure, massFlow, utilisation, severity);
  }

  private ReliefDeviceAssessment buildReliefAssessment(SafetyValve valve,
      ProcessSafetyThresholds thresholds) {
    double setPressure = valve.getPressureSpec();
    double relievingPressure = setPressure;
    double upstreamPressure = safeDouble(valve::getInletPressure);
    double massFlow = extractMassFlow(valve.getInletStream());
    double utilisation = normalizeOpenFraction(valve);
    SeverityLevel severity = gradeUtilisation(thresholds, utilisation);
    return new ReliefDeviceAssessment(valve.getName(), setPressure, relievingPressure,
        upstreamPressure, massFlow, utilisation, severity);
  }

  private double safeDouble(DoubleSupplier supplier) {
    try {
      return supplier.get();
    } catch (Exception ex) {
      logger.debug("Unable to evaluate metric", ex);
      return Double.NaN;
    }
  }

  @FunctionalInterface
  private interface DoubleSupplier {
    double get() throws Exception;
  }

  private double extractMassFlow(neqsim.process.equipment.stream.StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      return Double.NaN;
    }
    try {
      return stream.getThermoSystem().getFlowRate("kg/hr");
    } catch (Exception ex) {
      logger.debug("Unable to obtain mass flow", ex);
      return Double.NaN;
    }
  }

  private double normalizeOpenFraction(ValveInterface valve) {
    if (valve == null) {
      return Double.NaN;
    }
    try {
      double percentOpen = valve.getPercentValveOpening();
      return Math.max(0.0, Math.min(1.0, percentOpen / 100.0));
    } catch (Exception ex) {
      logger.debug("Unable to obtain valve opening", ex);
      return Double.NaN;
    }
  }

  private SeverityLevel gradeUtilisation(ProcessSafetyThresholds thresholds, double utilisation) {
    if (Double.isNaN(utilisation)) {
      return SeverityLevel.NORMAL;
    }
    if (utilisation >= thresholds.getReliefUtilisationCritical()) {
      return SeverityLevel.CRITICAL;
    }
    if (utilisation >= thresholds.getReliefUtilisationWarning()) {
      return SeverityLevel.WARNING;
    }
    return SeverityLevel.NORMAL;
  }

  private SystemKpiSnapshot collectSystemKpis(ProcessSafetyThresholds thresholds) {
    double entropy = safeDouble(() -> processSystem.getEntropyProduction("kJ/K"));
    double exergy = safeDouble(() -> processSystem.getExergyChange("kJ"));
    SeverityLevel entropySeverity = gradeHighIsBad(thresholds.getEntropyChangeWarning(),
        thresholds.getEntropyChangeCritical(), Math.abs(entropy));
    SeverityLevel exergySeverity = gradeHighIsBad(thresholds.getExergyChangeWarning(),
        thresholds.getExergyChangeCritical(), Math.abs(exergy));
    return new SystemKpiSnapshot(entropy, exergy, entropySeverity, exergySeverity);
  }

  private SeverityLevel gradeHighIsBad(double warningThreshold, double criticalThreshold,
      double value) {
    if (Double.isNaN(value)) {
      return SeverityLevel.NORMAL;
    }
    if (value >= criticalThreshold) {
      return SeverityLevel.CRITICAL;
    }
    if (value >= warningThreshold) {
      return SeverityLevel.WARNING;
    }
    return SeverityLevel.NORMAL;
  }

  private String generateEquipmentJson() {
    if (reportConfig == null) {
      return null;
    }
    try {
      Report report = new Report(processSystem);
      return report.generateJsonReport(reportConfig);
    } catch (Exception ex) {
      logger.warn("Unable to generate equipment JSON report", ex);
      return null;
    }
  }
}
