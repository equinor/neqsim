package neqsim.process.safety;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Summary of a {@link ProcessSafetyScenario} evaluation.
 */
public final class ProcessSafetyAnalysisSummary implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String scenarioName;
  private final Set<String> affectedUnits;
  private final String conditionMonitorReport;
  private final Map<String, String> conditionMessages;
  private final Map<String, UnitKpiSnapshot> unitKpis;

  public ProcessSafetyAnalysisSummary(String scenarioName, Set<String> affectedUnits,
      String conditionMonitorReport, Map<String, String> conditionMessages,
      Map<String, UnitKpiSnapshot> unitKpis) {
    this.scenarioName = Objects.requireNonNull(scenarioName, "scenarioName");
    this.affectedUnits = Collections.unmodifiableSet(new LinkedHashSet<>(affectedUnits));
    this.conditionMonitorReport = conditionMonitorReport == null ? "" : conditionMonitorReport;
    this.conditionMessages = Collections.unmodifiableMap(new LinkedHashMap<>(conditionMessages));
    this.unitKpis = Collections.unmodifiableMap(new LinkedHashMap<>(unitKpis));
  }

  public String getScenarioName() {
    return scenarioName;
  }

  public Set<String> getAffectedUnits() {
    return affectedUnits;
  }

  public String getConditionMonitorReport() {
    return conditionMonitorReport;
  }

  public Map<String, String> getConditionMessages() {
    return conditionMessages;
  }

  public Map<String, UnitKpiSnapshot> getUnitKpis() {
    return unitKpis;
  }

  /** Snapshot of key KPIs for a unit. */
  public static final class UnitKpiSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double massBalance;
    private final double pressure;
    private final double temperature;

    public UnitKpiSnapshot(double massBalance, double pressure, double temperature) {
      this.massBalance = massBalance;
      this.pressure = pressure;
      this.temperature = temperature;
    }

    public double getMassBalance() {
      return massBalance;
    }

    public double getPressure() {
      return pressure;
    }

    public double getTemperature() {
      return temperature;
    }
  }
}
