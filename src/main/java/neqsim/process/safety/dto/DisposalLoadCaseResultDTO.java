package neqsim.process.safety.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.flare.dto.FlarePerformanceDTO;

/**
 * Result of evaluating a disposal network for a single load case.
 */
public class DisposalLoadCaseResultDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String loadCaseName;
  private final Map<String, FlarePerformanceDTO> performanceByUnit;
  private final double totalHeatDutyMW;
  private final double maxRadiationDistanceM;
  private final List<CapacityAlertDTO> alerts;

  public DisposalLoadCaseResultDTO(String loadCaseName,
      Map<String, FlarePerformanceDTO> performanceByUnit, double totalHeatDutyMW,
      double maxRadiationDistanceM, List<CapacityAlertDTO> alerts) {
    this.loadCaseName = loadCaseName;
    this.performanceByUnit = performanceByUnit == null ? Collections.emptyMap()
        : Collections.unmodifiableMap(performanceByUnit);
    this.totalHeatDutyMW = totalHeatDutyMW;
    this.maxRadiationDistanceM = maxRadiationDistanceM;
    this.alerts = alerts == null ? Collections.emptyList() : Collections.unmodifiableList(alerts);
  }

  public String getLoadCaseName() {
    return loadCaseName;
  }

  public Map<String, FlarePerformanceDTO> getPerformanceByUnit() {
    return performanceByUnit;
  }

  public double getTotalHeatDutyMW() {
    return totalHeatDutyMW;
  }

  public double getMaxRadiationDistanceM() {
    return maxRadiationDistanceM;
  }

  public List<CapacityAlertDTO> getAlerts() {
    return alerts;
  }
}
