package neqsim.process.safety.compliance;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.safety.depressurization.STS0131AcceptanceResult;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;
import neqsim.process.safety.rupture.TrappedLiquidFireRuptureResult;

/**
 * Generalised STS-0131 acceptance gate.
 *
 * <p>
 * Equinor's STS-0131 ("Safety technology - supplementary requirements") gates a range of process safety result types -
 * not only depressurisation. This class consolidates several pass/fail checks (depressurisation, PSV sizing margin,
 * MDMT, trapped-liquid rupture, SIL versus NOG 070 minimum) into a single, traceable verdict.
 * </p>
 *
 * <p>
 * Each check is optional: only the checks explicitly added produce findings. The overall gate passes only when every
 * configured check passes.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Sts0131Gate implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Severity of an STS-0131 finding. */
  public enum Severity {
    /** Acceptance criterion met. */
    PASS,
    /** Acceptance criterion not met but not safety critical. */
    WARNING,
    /** Acceptance criterion not met and safety critical. */
    FAIL
  }

  /** A single STS-0131 finding. */
  public static class Finding implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String checkName;
    private final Severity severity;
    private final String message;

    /**
     * Creates a finding.
     *
     * @param checkName short identifier for the check (e.g. "Depressurisation")
     * @param severity {@link Severity}
     * @param message human readable description
     */
    public Finding(String checkName, Severity severity, String message) {
      this.checkName = checkName;
      this.severity = severity;
      this.message = message;
    }

    /**
     * @return check name
     */
    public String getCheckName() {
      return checkName;
    }

    /**
     * @return severity
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * @return human readable message
     */
    public String getMessage() {
      return message;
    }
  }

  private final List<Finding> findings = new ArrayList<Finding>();

  /**
   * Adds an STS-0131 depressurisation check to the gate.
   *
   * @param result depressurisation acceptance result; never null
   * @return this gate
   */
  public Sts0131Gate addDepressurisation(STS0131AcceptanceResult result) {
    if (result == null) {
      throw new IllegalArgumentException("result must not be null");
    }
    Severity sev = result.isAcceptable() ? Severity.PASS : Severity.FAIL;
    String msg = "Depressurisation STS-0131 acceptance " + (result.isAcceptable() ? "MET" : "NOT MET")
        + " at limiting time " + result.getLimitingTimeS() + " s (P = " + result.getPressureAtLimitingTimeBara()
        + " bara, remaining mass = " + result.getRemainingMassAtLimitingTimeKg() + " kg)";
    findings.add(new Finding("Depressurisation", sev, msg));
    return this;
  }

  /**
   * Adds a PSV sizing margin check. STS-0131 typically requires the selected orifice to provide at least 10% margin
   * over the required area.
   *
   * @param requiredAreaIn2 required orifice area in square inches
   * @param selectedAreaIn2 selected standard orifice area in square inches
   * @param requiredMargin required margin as a fraction (e.g. 0.10 for 10%)
   * @return this gate
   */
  public Sts0131Gate addPsvSizingMargin(double requiredAreaIn2, double selectedAreaIn2, double requiredMargin) {
    if (requiredAreaIn2 <= 0.0 || selectedAreaIn2 <= 0.0) {
      throw new IllegalArgumentException("areas must be positive");
    }
    double margin = (selectedAreaIn2 - requiredAreaIn2) / requiredAreaIn2;
    boolean ok = margin >= requiredMargin;
    Severity sev = ok ? Severity.PASS : Severity.FAIL;
    String msg = "PSV sizing margin = " + String.format(java.util.Locale.ROOT, "%.1f%%", margin * 100.0) + " (required "
        + String.format(java.util.Locale.ROOT, "%.1f%%", requiredMargin * 100.0) + "), required area " + requiredAreaIn2
        + " in2, selected " + selectedAreaIn2 + " in2";
    findings.add(new Finding("PSV sizing margin", sev, msg));
    return this;
  }

  /**
   * Adds an MDMT check comparing the minimum metal temperature reached during a transient with the design MDMT.
   *
   * @param minimumWallTemperatureC minimum wall metal temperature reached (degC)
   * @param designMdmtC design MDMT (degC)
   * @return this gate
   */
  public Sts0131Gate addMdmt(double minimumWallTemperatureC, double designMdmtC) {
    boolean ok = minimumWallTemperatureC >= designMdmtC;
    Severity sev = ok ? Severity.PASS : Severity.FAIL;
    String msg = "Minimum wall metal temperature " + minimumWallTemperatureC + " degC vs design MDMT " + designMdmtC
        + " degC -> " + (ok ? "above" : "below") + " MDMT";
    findings.add(new Finding("MDMT", sev, msg));
    return this;
  }

  /**
   * Adds a trapped-liquid fire-rupture check.
   *
   * @param result trapped liquid fire rupture result
   * @return this gate
   */
  public Sts0131Gate addTrappedLiquidRupture(TrappedLiquidFireRuptureResult result) {
    if (result == null) {
      throw new IllegalArgumentException("result must not be null");
    }
    boolean ruptured = result.isRupturePredicted();
    Severity sev = ruptured ? Severity.FAIL : Severity.PASS;
    String msg = "Trapped-liquid fire rupture screening: "
        + (ruptured ? "rupture predicted - PFP demand or relief required"
            : "no rupture predicted within evaluation window");
    findings.add(new Finding("Trapped liquid fire rupture", sev, msg));
    return this;
  }

  /**
   * Adds a NOG 070 SIL determination to the gate.
   *
   * @param sil NOG 070 SIL determination result
   * @return this gate
   */
  public Sts0131Gate addSil(Nog070SilDetermination sil) {
    if (sil == null) {
      throw new IllegalArgumentException("sil must not be null");
    }
    Severity sev = sil.isCompliant() ? Severity.PASS : Severity.FAIL;
    findings.add(new Finding("NOG 070 SIL", sev, sil.getMessage()));
    return this;
  }

  /**
   * Adds a generic custom check.
   *
   * @param checkName short identifier
   * @param ok pass/fail flag
   * @param message human readable message
   * @return this gate
   */
  public Sts0131Gate addCustom(String checkName, boolean ok, String message) {
    findings.add(new Finding(checkName, ok ? Severity.PASS : Severity.FAIL, message));
    return this;
  }

  /**
   * @return immutable view of all findings
   */
  public List<Finding> getFindings() {
    return new ArrayList<Finding>(findings);
  }

  /**
   * @return true when no finding has severity {@link Severity#FAIL}
   */
  public boolean isAcceptable() {
    for (Finding f : findings) {
      if (f.severity == Severity.FAIL) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return number of FAIL findings
   */
  public int countFailures() {
    int n = 0;
    for (Finding f : findings) {
      if (f.severity == Severity.FAIL) {
        n++;
      }
    }
    return n;
  }

  /**
   * Returns the gate as pretty JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
