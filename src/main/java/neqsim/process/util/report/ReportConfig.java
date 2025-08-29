package neqsim.process.util.report;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration options for JSON reporting.
 *
 * @author esol
 */
public class ReportConfig {
  /** Level of detail to include in reports. */
  public enum DetailLevel {
    /** Include only tag/name and minimal key fields. */
    MINIMUM,
    /** Include a small subset of data, omitting composition and detailed properties. */
    SUMMARY,
    /** Include all available information. */
    FULL,
    /** Do not include the unit in reports. */
    HIDE
  }

  /** Selected detail level. Defaults to FULL. */
  public DetailLevel detailLevel = DetailLevel.FULL;

  /** Optional overrides per equipment name. */
  private Map<String, DetailLevel> unitDetailLevel = new HashMap<>();

  /**
   * <p>
   * Constructor for ReportConfig.
   * </p>
   */
  public ReportConfig() {}

  /**
   * <p>
   * Constructor for ReportConfig.
   * </p>
   *
   * @param detailLevel a {@link neqsim.process.util.report.ReportConfig.DetailLevel} object
   */
  public ReportConfig(DetailLevel detailLevel) {
    this.detailLevel = detailLevel;
  }

  /**
   * Set detail level for a specific unit.
   *
   * @param unitName name of equipment
   * @param level desired detail level
   */
  public void setDetailLevel(String unitName, DetailLevel level) {
    unitDetailLevel.put(unitName, level);
  }

  /**
   * Get detail level for a specific unit, falling back to the global level.
   *
   * @param unitName name of equipment
   * @return detail level for the unit
   */
  public DetailLevel getDetailLevel(String unitName) {
    return unitDetailLevel.getOrDefault(unitName, detailLevel);
  }
}
