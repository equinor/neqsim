package neqsim.process.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.safety.dto.DisposalNetworkSummaryDTO;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * High level helper coordinating load case evaluation for disposal networks.
 */
public class ProcessSafetyAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DisposalNetwork disposalNetwork = new DisposalNetwork();
  private final List<ProcessSafetyLoadCase> loadCases = new ArrayList<>();
  private final ProcessSystem baseProcessSystem;
  private final ProcessSafetyResultRepository resultRepository;

  public ProcessSafetyAnalyzer() {
    this(null, null);
  }

  public ProcessSafetyAnalyzer(ProcessSystem baseProcessSystem) {
    this(baseProcessSystem, null);
  }

  public ProcessSafetyAnalyzer(ProcessSystem baseProcessSystem,
      ProcessSafetyResultRepository resultRepository) {
    this.baseProcessSystem = baseProcessSystem == null ? null : baseProcessSystem.copy();
    this.resultRepository = resultRepository;
  }

  public void registerDisposalUnit(Flare flare) {
    disposalNetwork.registerDisposalUnit(flare);
  }

  public void mapSourceToDisposal(String sourceId, String disposalUnitName) {
    disposalNetwork.mapSourceToDisposal(sourceId, disposalUnitName);
  }

  public void addLoadCase(ProcessSafetyLoadCase loadCase) {
    loadCases.add(loadCase);
  }

  public List<ProcessSafetyLoadCase> getLoadCases() {
    return Collections.unmodifiableList(loadCases);
  }

  public DisposalNetworkSummaryDTO analyze() {
    return disposalNetwork.evaluate(loadCases);
  }

  public ProcessSafetyAnalysisSummary analyze(ProcessSafetyScenario scenario) {
    Objects.requireNonNull(scenario, "scenario");
    ProcessSystem referenceSystem = requireBaseProcessSystem();
    ProcessSystem scenarioSystem = referenceSystem.copy();
    scenario.applyTo(scenarioSystem);

    ProcessSafetyAnalysisSummary summary = buildSummary(scenario, referenceSystem, scenarioSystem);
    if (resultRepository != null) {
      resultRepository.save(summary);
    }
    return summary;
  }

  public List<ProcessSafetyAnalysisSummary> analyze(
      Collection<ProcessSafetyScenario> scenarios) {
    Objects.requireNonNull(scenarios, "scenarios");
    List<ProcessSafetyAnalysisSummary> summaries = new ArrayList<>();
    for (ProcessSafetyScenario scenario : scenarios) {
      summaries.add(analyze(scenario));
    }
    return summaries;
  }

  private ProcessSystem requireBaseProcessSystem() {
    if (baseProcessSystem == null) {
      throw new IllegalStateException("ProcessSystem must be provided to analyse scenarios.");
    }
    return baseProcessSystem;
  }

  private ProcessSafetyAnalysisSummary buildSummary(ProcessSafetyScenario scenario,
      ProcessSystem referenceSystem, ProcessSystem scenarioSystem) {
    Set<String> affectedUnits = new LinkedHashSet<>(scenario.getTargetUnits());
    Map<String, String> conditionMessages = new LinkedHashMap<>();
    Map<String, ProcessSafetyAnalysisSummary.UnitKpiSnapshot> unitKpis = new LinkedHashMap<>();

    for (String unitName : affectedUnits) {
      ProcessEquipmentInterface scenarioUnit = scenarioSystem.getUnit(unitName);
      if (scenarioUnit == null) {
        continue;
      }
      ProcessEquipmentInterface referenceUnit = referenceSystem.getUnit(unitName);
      if (referenceUnit != null) {
        try {
          scenarioUnit.runConditionAnalysis(referenceUnit);
        } catch (RuntimeException ex) {
          // Ignore condition analysis failures for individual units while still capturing KPIs.
        }
      }

      String message = scenarioUnit.getConditionAnalysisMessage();
      if (message == null) {
        message = "";
      }
      conditionMessages.put(unitName, message);

      double massBalance = captureSafely(() -> scenarioUnit.getMassBalance("kg/s"));
      double pressure = captureSafely(scenarioUnit::getPressure);
      double temperature = captureSafely(scenarioUnit::getTemperature);
      unitKpis.put(unitName,
          new ProcessSafetyAnalysisSummary.UnitKpiSnapshot(massBalance, pressure, temperature));
    }

    String conditionReport = conditionMessages.values().stream()
        .filter(message -> message != null && !message.isEmpty())
        .collect(Collectors.joining(System.lineSeparator()));

    return new ProcessSafetyAnalysisSummary(scenario.getName(), affectedUnits, conditionReport,
        conditionMessages, unitKpis);
  }

  private double captureSafely(DoubleSupplierWithException supplier) {
    try {
      return supplier.getAsDouble();
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  @FunctionalInterface
  private interface DoubleSupplierWithException {
    double getAsDouble();
  }
}
