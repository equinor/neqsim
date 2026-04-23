package neqsim.process.mechanicaldesign.separator.conformity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collection of conformity check results for a separator or scrubber.
 *
 * <p>
 * Aggregates individual {@link ConformityResult} entries from vessel-level and
 * internals-level
 * checks. Provides summary methods and formatted printing.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ConformityReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String equipmentName;
  private final String standard;
  private final List<ConformityResult> results = new ArrayList<ConformityResult>();

  /**
   * Constructs a ConformityReport.
   *
   * @param equipmentName name of the equipment being checked
   * @param standard      the conformity standard applied
   */
  public ConformityReport(String equipmentName, String standard) {
    this.equipmentName = equipmentName;
    this.standard = standard;
  }

  /**
   * Adds a result to the report.
   *
   * @param result the conformity result to add
   */
  public void addResult(ConformityResult result) {
    results.add(result);
  }

  /**
   * Gets all results.
   *
   * @return unmodifiable list of results
   */
  public List<ConformityResult> getResults() {
    return Collections.unmodifiableList(results);
  }

  /**
   * Gets the equipment name.
   *
   * @return the equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Gets the standard name.
   *
   * @return the standard name
   */
  public String getStandard() {
    return standard;
  }

  /**
   * Returns true if all checks passed (PASS or WARNING or NOT_APPLICABLE).
   *
   * @return true if no FAIL results
   */
  public boolean isConforming() {
    for (ConformityResult r : results) {
      if (r.getStatus() == ConformityResult.Status.FAIL) {
        return false;
      }
    }
    return true;
  }

  /**
   * Counts results with FAIL status.
   *
   * @return number of failed checks
   */
  public int getFailCount() {
    int count = 0;
    for (ConformityResult r : results) {
      if (r.getStatus() == ConformityResult.Status.FAIL) {
        count++;
      }
    }
    return count;
  }

  /**
   * Counts results with WARNING status.
   *
   * @return number of warning checks
   */
  public int getWarningCount() {
    int count = 0;
    for (ConformityResult r : results) {
      if (r.getStatus() == ConformityResult.Status.WARNING) {
        count++;
      }
    }
    return count;
  }

  /**
   * Counts results with PASS status.
   *
   * @return number of passed checks
   */
  public int getPassCount() {
    int count = 0;
    for (ConformityResult r : results) {
      if (r.getStatus() == ConformityResult.Status.PASS) {
        count++;
      }
    }
    return count;
  }

  /**
   * Prints a formatted summary table of all results.
   *
   * @return formatted text report
   */
  public String toTextReport() {
    StringBuilder sb = new StringBuilder();
    String line = "----------------------------------------------------------------------"
        + "--------------------";
    sb.append(line).append('\n');
    sb.append("  CONFORMITY CHECK: ").append(equipmentName);
    sb.append("  [").append(standard).append("]\n");
    sb.append(line).append('\n');
    sb.append(String.format("  %-25s %10s %10s %-6s %s\n",
        "Check", "Actual", "Limit", "Unit", "Status"));
    sb.append(line).append('\n');

    for (ConformityResult r : results) {
      String statusStr;
      switch (r.getStatus()) {
        case PASS:
          statusStr = "PASS";
          break;
        case WARNING:
          statusStr = "WARN";
          break;
        case FAIL:
          statusStr = "FAIL";
          break;
        default:
          statusStr = "N/A";
          break;
      }
      if (r.getStatus() == ConformityResult.Status.NOT_APPLICABLE) {
        sb.append(String.format("  %-25s %10s %10s %-6s %s\n",
            r.getCheckName(), "-", "-", "", statusStr));
      } else {
        sb.append(String.format("  %-25s %10.4f %10.4f %-6s %s\n",
            r.getCheckName(), r.getActualValue(), r.getLimitValue(), r.getUnit(), statusStr));
      }
    }

    sb.append(line).append('\n');
    sb.append("  Summary: ").append(getPassCount()).append(" PASS, ");
    sb.append(getWarningCount()).append(" WARN, ");
    sb.append(getFailCount()).append(" FAIL");
    sb.append("  → ").append(isConforming() ? "CONFORMING" : "NON-CONFORMING").append('\n');
    sb.append(line).append('\n');
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return toTextReport();
  }
}
