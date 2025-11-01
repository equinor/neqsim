package neqsim.process.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.flare.dto.FlarePerformanceDTO;
import neqsim.process.safety.ProcessSafetyLoadCase.ReliefSourceLoad;
import neqsim.process.safety.dto.CapacityAlertDTO;
import neqsim.process.safety.dto.DisposalLoadCaseResultDTO;
import neqsim.process.safety.dto.DisposalNetworkSummaryDTO;

/**
 * Aggregates loads from multiple relief sources into disposal units and evaluates performance for
 * each simultaneous load case.
 */
public class DisposalNetwork implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Map<String, Flare> disposalUnits = new LinkedHashMap<>();
  private final Map<String, String> sourceToDisposal = new LinkedHashMap<>();

  public void registerDisposalUnit(Flare flare) {
    Objects.requireNonNull(flare, "flare");
    disposalUnits.put(flare.getName(), flare);
  }

  public void mapSourceToDisposal(String sourceId, String disposalUnitName) {
    if (!disposalUnits.containsKey(disposalUnitName)) {
      throw new IllegalArgumentException(
          "Disposal unit not registered for mapping: " + disposalUnitName);
    }
    sourceToDisposal.put(sourceId, disposalUnitName);
  }

  public DisposalNetworkSummaryDTO evaluate(List<ProcessSafetyLoadCase> loadCases) {
    if (loadCases == null || loadCases.isEmpty()) {
      return new DisposalNetworkSummaryDTO(Collections.emptyList(), 0.0, 0.0,
          Collections.emptyList());
    }

    List<DisposalLoadCaseResultDTO> results = new ArrayList<>();
    List<CapacityAlertDTO> alerts = new ArrayList<>();
    double maxHeatDutyMW = 0.0;
    double maxRadiationDistance = 0.0;

    for (ProcessSafetyLoadCase loadCase : loadCases) {
      DisposalLoadCaseResultDTO dto = evaluateLoadCase(loadCase);
      results.add(dto);
      maxHeatDutyMW = Math.max(maxHeatDutyMW, dto.getTotalHeatDutyMW());
      maxRadiationDistance = Math.max(maxRadiationDistance, dto.getMaxRadiationDistanceM());
      alerts.addAll(dto.getAlerts());
    }

    return new DisposalNetworkSummaryDTO(results, maxHeatDutyMW, maxRadiationDistance, alerts);
  }

  private DisposalLoadCaseResultDTO evaluateLoadCase(ProcessSafetyLoadCase loadCase) {
    Map<String, AggregatedLoad> aggregated = new LinkedHashMap<>();

    for (Map.Entry<String, ReliefSourceLoad> entry : loadCase.getReliefLoads().entrySet()) {
      String sourceId = entry.getKey();
      String unitName = sourceToDisposal.get(sourceId);
      if (unitName == null) {
        continue; // source not mapped
      }
      AggregatedLoad load = aggregated.computeIfAbsent(unitName, k -> new AggregatedLoad());
      ReliefSourceLoad sourceLoad = entry.getValue();
      load.massRate += sourceLoad.getMassRateKgS();
      if (sourceLoad.getHeatDutyW() != null) {
        load.heatDuty += sourceLoad.getHeatDutyW();
      }
      if (sourceLoad.getMolarRateMoleS() != null) {
        load.molarRate += sourceLoad.getMolarRateMoleS();
      }
    }

    Map<String, FlarePerformanceDTO> performance = new LinkedHashMap<>();
    List<CapacityAlertDTO> alerts = new ArrayList<>();
    double totalHeatDutyW = 0.0;
    double maxRadiationDistance = 0.0;

    for (Map.Entry<String, AggregatedLoad> entry : aggregated.entrySet()) {
      Flare flare = disposalUnits.get(entry.getKey());
      if (flare == null) {
        continue;
      }
      AggregatedLoad load = entry.getValue();
      FlarePerformanceDTO basePerformance = flare.getPerformanceSummary();
      double heatDuty = load.heatDuty;
      if (heatDuty <= 0.0) {
        heatDuty = basePerformance.getHeatDutyW()
            * safeRatio(load.massRate, basePerformance.getMassRateKgS());
      }
      double molarRate = load.molarRate;
      if (molarRate <= 0.0) {
        molarRate = basePerformance.getMolarRateMoleS()
            * safeRatio(load.massRate, basePerformance.getMassRateKgS());
      }

      FlarePerformanceDTO dto = flare.getPerformanceSummary(loadCase.getName(), heatDuty,
          load.massRate, molarRate);
      performance.put(entry.getKey(), dto);
      totalHeatDutyW += dto.getHeatDutyW();
      maxRadiationDistance = Math.max(maxRadiationDistance, dto.getDistanceTo4kWm2());
      if (dto.isOverloaded()) {
        alerts.add(new CapacityAlertDTO(loadCase.getName(), entry.getKey(),
            "Disposal unit capacity exceeded"));
      }
    }

    return new DisposalLoadCaseResultDTO(loadCase.getName(), performance, totalHeatDutyW * 1.0e-6,
        maxRadiationDistance, alerts);
  }

  private double safeRatio(double numerator, double denominator) {
    if (!Double.isFinite(denominator) || Math.abs(denominator) < 1.0e-12) {
      return 0.0;
    }
    return numerator / denominator;
  }

  private static class AggregatedLoad {
    double massRate;
    double molarRate;
    double heatDuty;
  }
}
