package neqsim.process.safety.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Summary of disposal network evaluation across all analysed load cases.
 */
public class DisposalNetworkSummaryDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<DisposalLoadCaseResultDTO> loadCaseResults;
  private final double maxHeatDutyMW;
  private final double maxRadiationDistanceM;
  private final List<CapacityAlertDTO> alerts;

  public DisposalNetworkSummaryDTO(List<DisposalLoadCaseResultDTO> loadCaseResults,
      double maxHeatDutyMW, double maxRadiationDistanceM, List<CapacityAlertDTO> alerts) {
    this.loadCaseResults = loadCaseResults == null ? Collections.emptyList()
        : Collections.unmodifiableList(loadCaseResults);
    this.maxHeatDutyMW = maxHeatDutyMW;
    this.maxRadiationDistanceM = maxRadiationDistanceM;
    this.alerts = alerts == null ? Collections.emptyList() : Collections.unmodifiableList(alerts);
  }

  public List<DisposalLoadCaseResultDTO> getLoadCaseResults() {
    return loadCaseResults;
  }

  public double getMaxHeatDutyMW() {
    return maxHeatDutyMW;
  }

  public double getMaxRadiationDistanceM() {
    return maxRadiationDistanceM;
  }

  public List<CapacityAlertDTO> getAlerts() {
    return alerts;
  }
}
