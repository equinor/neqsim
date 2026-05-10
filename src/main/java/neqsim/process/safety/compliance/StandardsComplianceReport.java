package neqsim.process.safety.compliance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standards Compliance Report — produces an auditable checklist of mandatory requirements from
 * the major process-safety standards and tracks the project's compliance status.
 *
 * <p>
 * Built-in checklists are provided for:
 * <ul>
 * <li>API RP 14C — Safety analysis of surface safety systems for offshore production</li>
 * <li>NORSOK S-001 — Technical safety</li>
 * <li>IEC 61511 — Functional safety: SIS for the process industry</li>
 * <li>STS0131 — Technical safety requirements</li>
 * <li>TR1965 — Gas scrubber/separator requirements</li>
 * <li>NORSOK P-002 — Process system line sizing</li>
 * <li>TR2237 — Performance standard structure</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class StandardsComplianceReport implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Compliance status of a single requirement. */
  public enum Status {
    /** Requirement met and evidence on file. */
    COMPLIANT,
    /** Requirement met with deviations or compensating measures. */
    PARTIAL,
    /** Requirement not met. */
    NON_COMPLIANT,
    /** Status not yet assessed. */
    NOT_ASSESSED,
    /** Requirement does not apply to this project / scope. */
    NOT_APPLICABLE
  }

  private final String projectName;
  private final List<Requirement> requirements = new ArrayList<>();

  /**
   * Construct a compliance report.
   *
   * @param projectName project identifier
   */
  public StandardsComplianceReport(String projectName) {
    this.projectName = projectName;
  }

  /**
   * Add a requirement.
   *
   * @param standard standard ID (e.g. "API RP 14C")
   * @param clause clause / section reference
   * @param description requirement description
   * @return this report for chaining
   */
  public StandardsComplianceReport addRequirement(String standard, String clause,
      String description) {
    requirements.add(new Requirement(standard, clause, description, Status.NOT_ASSESSED, null));
    return this;
  }

  /**
   * Set status of an existing requirement (matched by standard + clause).
   *
   * @param standard standard ID
   * @param clause clause reference
   * @param status status
   * @param evidence supporting evidence text (document ref, calc ID)
   * @return this report for chaining
   */
  public StandardsComplianceReport setStatus(String standard, String clause, Status status,
      String evidence) {
    for (Requirement r : requirements) {
      if (r.standard.equals(standard) && r.clause.equals(clause)) {
        r.status = status;
        r.evidence = evidence;
        return this;
      }
    }
    throw new IllegalArgumentException("Requirement not found: " + standard + " " + clause);
  }

  /**
   * @return immutable list of all requirements
   */
  public List<Requirement> getRequirements() {
    return new ArrayList<>(requirements);
  }

  /**
   * Compliance percentage (compliant / (total − N/A − not-assessed)).
   *
   * @return compliance percentage in [0, 100]
   */
  public double compliancePercent() {
    int considered = 0;
    int compliant = 0;
    for (Requirement r : requirements) {
      if (r.status == Status.NOT_ASSESSED || r.status == Status.NOT_APPLICABLE) {
        continue;
      }
      considered++;
      if (r.status == Status.COMPLIANT) {
        compliant++;
      } else if (r.status == Status.PARTIAL) {
        compliant++;
      }
    }
    return considered == 0 ? 0.0 : 100.0 * compliant / considered;
  }

  /**
   * Count requirements by status.
   *
   * @return map status → count (in declaration order)
   */
  public Map<Status, Integer> statusSummary() {
    Map<Status, Integer> map = new LinkedHashMap<>();
    for (Status s : Status.values()) {
      map.put(s, 0);
    }
    for (Requirement r : requirements) {
      map.put(r.status, map.get(r.status) + 1);
    }
    return map;
  }

  /**
   * Build a multi-line text report.
   *
   * @return human-readable compliance report
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("Standards compliance: ").append(projectName).append('\n');
    sb.append(String.format("Overall compliance: %.1f%%%n", compliancePercent()));
    sb.append("Status counts: ").append(statusSummary()).append('\n');
    sb.append("---------------------------------------------------\n");
    String currentStd = "";
    for (Requirement r : requirements) {
      if (!r.standard.equals(currentStd)) {
        sb.append("\n[").append(r.standard).append("]\n");
        currentStd = r.standard;
      }
      sb.append(String.format("  %-10s %-12s %s%n", r.status, r.clause, r.description));
      if (r.evidence != null) {
        sb.append("             evidence: ").append(r.evidence).append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Pre-populate the report with the API RP 14C SAFE chart skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadAPI14C() {
    String std = "API RP 14C";
    for (String[] r : Arrays.asList(
        new String[] {"3.4", "Safety Analysis Table (SAT) for each component"},
        new String[] {"3.5", "Safety Analysis Checklist (SAC) for each component"},
        new String[] {"3.6", "Safety Analysis Function Evaluation (SAFE) chart"},
        new String[] {"4.x", "PSV protection per ASME Section VIII"},
        new String[] {"4.x", "Pressure Sensors (PSH/PSL) on each pressure vessel"},
        new String[] {"4.x", "Level Sensors (LSH/LSL) on each separator"},
        new String[] {"4.x", "Temperature Sensors (TSH) on heated equipment"},
        new String[] {"5.x", "ESD (Emergency Shutdown) hierarchy"},
        new String[] {"5.x", "Fire / gas detection coverage"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with a NORSOK S-001 skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadNORSOKS001() {
    String std = "NORSOK S-001";
    for (String[] r : Arrays.asList(
        new String[] {"5.1", "Safety strategy aligned with NORSOK Z-013 risk policy"},
        new String[] {"5.4", "Performance standards for safety functions"},
        new String[] {"6", "Layout and area classification per IEC 60079-10"},
        new String[] {"7.1", "ESD philosophy and ESD levels"},
        new String[] {"7.2", "PSD philosophy and PSD levels"},
        new String[] {"8", "Fire and gas detection — coverage analysis"},
        new String[] {"9", "Open drain systems and spill drainage"},
        new String[] {"10", "Process safety system: PSD, PSV, SIF, alarms, utilities, survivability"},
        new String[] {"11", "Active and passive fire protection evidence"},
        new String[] {"12", "Blast walls and overpressure resistance"},
        new String[] {"13", "Escape, evacuation and rescue (EER)"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with a NORSOK S-001 Clause 10 process safety system checklist.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadNORSOKS001Clause10() {
    String std = "NORSOK S-001";
    for (String[] r : Arrays.asList(
        new String[] {"10.1", "Process safety system role and interfaces documented"},
        new String[] {"10.3", "Means of protection and scenario design basis documented"},
        new String[] {"10.4.1", "Process safety principles, safe state, and bypass controls documented"},
        new String[] {"10.4.2", "PSD valves and shutdown actions documented and fail safe"},
        new String[] {"10.4.3", "PSV protection and relief sizing basis documented"},
        new String[] {"10.4.4", "Alarms and operator actions have setpoints and response basis"},
        new String[] {"10.4.5", "Actual response time does not exceed required response time"},
        new String[] {"10.4.6", "Logic solver suitability, independence, and C&E testing documented"},
        new String[] {"10.4.7", "Instrumented secondary pressure protection pressure, frequency, leakage, and proof-test basis documented"},
        new String[] {"10.4.8", "Required utility dependencies and loss-of-utility safe states documented"},
        new String[] {"10.4.9", "PSD independence and manual initiation principles documented"},
        new String[] {"10.4.10", "Process safety system survivability requirements documented"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with an IEC 61511 skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadIEC61511() {
    String std = "IEC 61511";
    for (String[] r : Arrays.asList(
        new String[] {"5", "Safety lifecycle plan documented"},
        new String[] {"8", "Hazard and risk assessment (LOPA / risk graph)"},
        new String[] {"9", "Allocation of safety functions and SIL determination"},
        new String[] {"10", "SRS — Safety Requirements Specification"},
        new String[] {"11", "SIL verification (PFDavg / PFH)"},
        new String[] {"12", "Application program — language and competence"},
        new String[] {"15", "Proof testing strategy and intervals"},
        new String[] {"16", "Operations and maintenance procedures"},
        new String[] {"17", "Modification management of change"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with an STS0131 technical safety checklist skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadSTS0131() {
    String std = "STS0131";
    for (String[] r : Arrays.asList(
        new String[] {"Req-11199", "Flammable gas endpoints use 50% LEL for CFD and 20% LEL for integral tools"},
        new String[] {"Overpressure", "LOPA target frequency selected from pressure severity bands"},
        new String[] {"Blowdown", "Depressurization acceptance covers escape time, rupture pressure, inventory, and fire rate"},
        new String[] {"Detection", "Leak detection sensitivity is documented for mass-balance or field-data methods"},
        new String[] {"Barriers", "Safety barriers are linked to performance standards and verification evidence"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with a TR1965 gas scrubber checklist skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadTR1965() {
    String std = "TR1965";
    for (String[] r : Arrays.asList(
        new String[] {"K-factor", "Gas-load K-factor within internals-specific TR1965 limit"},
        new String[] {"Entrainment", "Liquid entrainment to gas outlet is documented and within limit"},
        new String[] {"Gas margin", "Gas design margin is documented and at least 10%"},
        new String[] {"Liquid margin", "Liquid design margin is documented and at least 20%"},
        new String[] {"Layout", "HHLL-to-inlet and inlet-to-demister distances are documented"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with a NORSOK P-002 line sizing checklist skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadNORSOKP002() {
    String std = "NORSOK P-002";
    for (String[] r : Arrays.asList(
        new String[] {"Velocity", "Line velocity is within service-specific screening limits"},
        new String[] {"Pressure gradient", "Pressure gradient is within project screening limit"},
        new String[] {"Erosion", "Two-phase velocity is below the selected erosional velocity fraction"},
        new String[] {"Service", "Line service is classified from fluid phase behavior and design basis"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * Pre-populate the report with a TR2237 performance-standard checklist skeleton.
   *
   * @return this report for chaining
   */
  public StandardsComplianceReport loadTR2237() {
    String std = "TR2237";
    for (String[] r : Arrays.asList(
        new String[] {"Register", "Barrier register contains safety critical elements and barriers"},
        new String[] {"Performance standards", "Each credited barrier has a performance standard"},
        new String[] {"Evidence", "Each performance standard has traceable evidence"},
        new String[] {"Verification", "Impairments, bypasses, tests, and verification intervals are tracked"})) {
      requirements.add(new Requirement(std, r[0], r[1], Status.NOT_ASSESSED, null));
    }
    return this;
  }

  /**
   * One requirement / clause record.
   */
  public static class Requirement implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Standard ID. */
    public final String standard;
    /** Clause / section reference. */
    public final String clause;
    /** Description text. */
    public final String description;
    /** Compliance status. */
    public Status status;
    /** Evidence reference (document, calculation, drawing). */
    public String evidence;

    /**
     * @param standard standard ID
     * @param clause clause reference
     * @param description description
     * @param status status
     * @param evidence evidence text or null
     */
    public Requirement(String standard, String clause, String description, Status status,
        String evidence) {
      this.standard = standard;
      this.clause = clause;
      this.description = description;
      this.status = status;
      this.evidence = evidence;
    }
  }
}
