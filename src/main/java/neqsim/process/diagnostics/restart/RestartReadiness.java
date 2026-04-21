package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Assessment of process readiness for restart after a trip event.
 *
 * <p>
 * Contains individual constraint checks, each of which may pass, fail, or warn. The overall
 * readiness is determined by the worst-case constraint.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartReadiness implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /**
   * Overall readiness status.
   */
  public enum Status {
    /** All constraints satisfied, safe to restart. */
    READY("Ready to restart"),
    /** Warnings exist but restart is possible with caution. */
    READY_WITH_WARNINGS("Ready with warnings"),
    /** One or more constraints not satisfied, do not restart. */
    NOT_READY("Not ready for restart");

    private final String displayName;

    Status(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Severity of a single constraint check result.
   */
  public enum ConstraintSeverity {
    /** Constraint is satisfied. */
    PASS,
    /** Constraint is marginally satisfied — proceed with caution. */
    WARNING,
    /** Constraint is violated — do not restart until resolved. */
    FAIL
  }

  /**
   * Result of a single restart constraint check.
   *
   * @author esol
   * @version 1.0
   */
  public static class ConstraintResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String constraintName;
    private final ConstraintSeverity severity;
    private final String description;
    private final String remediation;

    /**
     * Constructs a constraint result.
     *
     * @param constraintName name of the constraint
     * @param severity the severity
     * @param description what was checked and the outcome
     * @param remediation action to take if not PASS
     */
    public ConstraintResult(String constraintName, ConstraintSeverity severity, String description,
        String remediation) {
      this.constraintName = constraintName;
      this.severity = severity;
      this.description = description;
      this.remediation = remediation;
    }

    /**
     * Returns the constraint name.
     *
     * @return constraint name
     */
    public String getConstraintName() {
      return constraintName;
    }

    /**
     * Returns the severity.
     *
     * @return severity
     */
    public ConstraintSeverity getSeverity() {
      return severity;
    }

    /**
     * Returns the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns the remediation action.
     *
     * @return remediation text
     */
    public String getRemediation() {
      return remediation;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s", severity, constraintName, description);
    }
  }

  private final List<ConstraintResult> results;
  private final double assessmentTime;

  /**
   * Constructs a readiness assessment.
   *
   * @param results list of individual constraint results
   * @param assessmentTime the simulation time when the assessment was made
   */
  public RestartReadiness(List<ConstraintResult> results, double assessmentTime) {
    this.results = new ArrayList<>(results);
    this.assessmentTime = assessmentTime;
  }

  /**
   * Returns the overall readiness status.
   *
   * @return status
   */
  public Status getStatus() {
    boolean hasWarning = false;
    for (ConstraintResult cr : results) {
      if (cr.getSeverity() == ConstraintSeverity.FAIL) {
        return Status.NOT_READY;
      }
      if (cr.getSeverity() == ConstraintSeverity.WARNING) {
        hasWarning = true;
      }
    }
    return hasWarning ? Status.READY_WITH_WARNINGS : Status.READY;
  }

  /**
   * Returns true if the process is ready to restart (READY or READY_WITH_WARNINGS).
   *
   * @return true if restart is possible
   */
  public boolean isReady() {
    return getStatus() != Status.NOT_READY;
  }

  /**
   * Returns all constraint results.
   *
   * @return unmodifiable list of results
   */
  public List<ConstraintResult> getResults() {
    return Collections.unmodifiableList(results);
  }

  /**
   * Returns only the failed constraint results.
   *
   * @return list of FAIL results
   */
  public List<ConstraintResult> getFailures() {
    List<ConstraintResult> failures = new ArrayList<>();
    for (ConstraintResult cr : results) {
      if (cr.getSeverity() == ConstraintSeverity.FAIL) {
        failures.add(cr);
      }
    }
    return failures;
  }

  /**
   * Returns the assessment time.
   *
   * @return assessment time in seconds
   */
  public double getAssessmentTime() {
    return assessmentTime;
  }

  /**
   * Serialises to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("status", getStatus().name());
    map.put("isReady", isReady());
    map.put("assessmentTime_s", assessmentTime);

    List<Map<String, Object>> constraintList = new ArrayList<>();
    for (ConstraintResult cr : results) {
      Map<String, Object> crMap = new LinkedHashMap<>();
      crMap.put("constraint", cr.getConstraintName());
      crMap.put("severity", cr.getSeverity().name());
      crMap.put("description", cr.getDescription());
      crMap.put("remediation", cr.getRemediation());
      constraintList.add(crMap);
    }
    map.put("constraints", constraintList);
    return GSON.toJson(map);
  }

  @Override
  public String toString() {
    return String.format("RestartReadiness{status=%s, constraints=%d, failures=%d}", getStatus(),
        results.size(), getFailures().size());
  }
}
