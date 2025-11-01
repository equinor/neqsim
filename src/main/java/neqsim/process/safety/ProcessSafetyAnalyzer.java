package neqsim.process.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.conditionmonitor.ConditionMonitor;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Executes {@link ProcessSafetyScenario}s by creating scenario specific copies of a base
 * {@link ProcessSystem}, applying perturbations and running condition monitoring to produce
 * summaries that can optionally be persisted via a {@link ProcessSafetyResultRepository}.
 */
public class ProcessSafetyAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(ProcessSafetyAnalyzer.class);

  private final ProcessSystem baseProcessSystem;
  private final ProcessSafetyResultRepository resultRepository;

  public ProcessSafetyAnalyzer(ProcessSystem baseProcessSystem) {
    this(baseProcessSystem, null);
  }

  public ProcessSafetyAnalyzer(ProcessSystem baseProcessSystem,
      ProcessSafetyResultRepository resultRepository) {
    this.baseProcessSystem = Objects.requireNonNull(baseProcessSystem, "baseProcessSystem");
    this.resultRepository = resultRepository;
  }

  public List<ProcessSafetyAnalysisSummary> analyze(List<ProcessSafetyScenario> scenarios) {
    Objects.requireNonNull(scenarios, "scenarios");
    List<ProcessSafetyAnalysisSummary> summaries = new ArrayList<>();
    for (ProcessSafetyScenario scenario : scenarios) {
      summaries.add(analyze(scenario));
    }
    return summaries;
  }

  public ProcessSafetyAnalysisSummary analyze(ProcessSafetyScenario scenario) {
    Objects.requireNonNull(scenario, "scenario");

    ConditionMonitor monitor = new ConditionMonitor(baseProcessSystem);
    ProcessSystem scenarioProcess = monitor.getProcess();
    scenario.applyTo(scenarioProcess);

    try {
      scenarioProcess.run(UUID.randomUUID());
    } catch (RuntimeException ex) {
      logger.error("Scenario '{}' failed to run: {}", scenario.getName(), ex.getMessage(), ex);
      throw ex;
    }

    monitor.conditionAnalysis();

    String report = sanitizeReport(monitor.getReport());
    Set<String> affectedUnits = new LinkedHashSet<>(scenario.getTargetUnits());
    if (affectedUnits.isEmpty()) {
      affectedUnits.addAll(scenarioProcess.getAllUnitNames());
    }
    Map<String, ProcessSafetyAnalysisSummary.UnitKpiSnapshot> kpis =
        collectUnitKpis(scenarioProcess, affectedUnits);
    Map<String, String> conditionMessages = collectConditionMessages(scenarioProcess, affectedUnits);

    ProcessSafetyAnalysisSummary summary =
        new ProcessSafetyAnalysisSummary(scenario.getName(), affectedUnits, report, conditionMessages,
            kpis);
    if (resultRepository != null) {
      resultRepository.save(summary);
    }
    return summary;
  }

  private Map<String, ProcessSafetyAnalysisSummary.UnitKpiSnapshot> collectUnitKpis(
      ProcessSystem scenarioProcess, Set<String> affectedUnits) {
    Map<String, ProcessSafetyAnalysisSummary.UnitKpiSnapshot> kpis = new LinkedHashMap<>();
    for (String unitName : affectedUnits) {
      ProcessEquipmentInterface unit = scenarioProcess.getUnit(unitName);
      if (unit == null) {
        continue;
      }
      double massBalance = safeEvaluate(() -> unit.getMassBalance("kg/hr"));
      double pressure = safeEvaluate(unit::getPressure);
      double temperature = safeEvaluate(unit::getTemperature);
      kpis.put(unitName,
          new ProcessSafetyAnalysisSummary.UnitKpiSnapshot(massBalance, pressure, temperature));
    }
    return kpis;
  }

  private Map<String, String> collectConditionMessages(ProcessSystem scenarioProcess,
      Set<String> affectedUnits) {
    Map<String, String> messages = new LinkedHashMap<>();
    for (String unitName : affectedUnits) {
      ProcessEquipmentInterface unit = scenarioProcess.getUnit(unitName);
      if (unit == null) {
        continue;
      }
      messages.put(unitName, nullToEmpty(unit.getConditionAnalysisMessage()));
    }
    return messages;
  }

  private String sanitizeReport(String report) {
    if (report == null) {
      return "";
    }
    return report.startsWith("null") ? report.substring(4) : report;
  }

  private String nullToEmpty(String message) {
    return message == null ? "" : message;
  }

  private double safeEvaluate(DoubleSupplier supplier) {
    try {
      return supplier.getAsDouble();
    } catch (RuntimeException ex) {
      logger.debug("Failed to evaluate KPI: {}", ex.getMessage());
      return Double.NaN;
    }
  }

  @FunctionalInterface
  private interface DoubleSupplier extends Serializable {
    double getAsDouble();
  }
}
