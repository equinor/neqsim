package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Readiness verdict and findings for governed safety calculations.
 *
 * <p>
 * The readiness object separates three states that matter in agentic engineering: not enough data to calculate, a
 * screening calculation with explicit gaps, and a design-grade calculation where controlled evidence has been reviewed.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class SafetyStudyReadiness implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Overall readiness verdict. */
  public enum Verdict {
    /** Required calculation inputs are missing or invalid. */
    NOT_READY,
    /** Calculation may run, but gaps or unverified assumptions prevent design-grade use. */
    SCREENING,
    /** Inputs and governance evidence are complete enough for engineering review. */
    DESIGN_GRADE
  }

  /** Finding severity. */
  public enum Severity {
    /** Informational finding. */
    INFO,
    /** Warning that keeps the study at screening level. */
    WARNING,
    /** Blocker that prevents calculation. */
    BLOCKER
  }

  private final Verdict verdict;
  private final List<Finding> findings;
  private final List<SafetyEvidenceReference> evidenceReferences;

  /**
   * Creates a readiness result.
   *
   * @param builder populated builder
   */
  private SafetyStudyReadiness(Builder builder) {
    this.verdict = builder.explicitVerdict == null ? inferVerdict(builder.findings) : builder.explicitVerdict;
    this.findings = Collections.unmodifiableList(new ArrayList<Finding>(builder.findings));
    this.evidenceReferences = Collections
        .unmodifiableList(new ArrayList<SafetyEvidenceReference>(builder.evidenceReferences));
  }

  /**
   * Creates a readiness builder.
   *
   * @return readiness builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the readiness verdict.
   *
   * @return readiness verdict
   */
  public Verdict getVerdict() {
    return verdict;
  }

  /**
   * Checks if all calculation-critical inputs are present.
   *
   * @return true if the study may be calculated
   */
  public boolean isReadyForCalculation() {
    return verdict != Verdict.NOT_READY;
  }

  /**
   * Checks if the study has design-grade readiness.
   *
   * @return true when the verdict is design grade
   */
  public boolean isDesignGrade() {
    return verdict == Verdict.DESIGN_GRADE;
  }

  /**
   * Gets readiness findings.
   *
   * @return immutable finding list
   */
  public List<Finding> getFindings() {
    return findings;
  }

  /**
   * Gets evidence references.
   *
   * @return immutable evidence reference list
   */
  public List<SafetyEvidenceReference> getEvidenceReferences() {
    return evidenceReferences;
  }

  /**
   * Converts readiness to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "safety_study_readiness.v1");
    map.put("verdict", verdict.name());
    map.put("readyForCalculation", Boolean.valueOf(isReadyForCalculation()));
    map.put("designGrade", Boolean.valueOf(isDesignGrade()));
    List<Map<String, Object>> findingMaps = new ArrayList<Map<String, Object>>();
    for (Finding finding : findings) {
      findingMaps.add(finding.toMap());
    }
    map.put("findings", findingMaps);
    List<Map<String, Object>> evidenceMaps = new ArrayList<Map<String, Object>>();
    for (SafetyEvidenceReference reference : evidenceReferences) {
      evidenceMaps.add(reference.toMap());
    }
    map.put("evidenceReferences", evidenceMaps);
    return map;
  }

  /**
   * Converts readiness to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Infers verdict from findings.
   *
   * @param findings finding list
   * @return inferred verdict
   */
  private static Verdict inferVerdict(List<Finding> findings) {
    boolean warning = false;
    for (Finding finding : findings) {
      if (finding.getSeverity() == Severity.BLOCKER) {
        return Verdict.NOT_READY;
      }
      if (finding.getSeverity() == Severity.WARNING) {
        warning = true;
      }
    }
    return warning ? Verdict.SCREENING : Verdict.DESIGN_GRADE;
  }

  /** One readiness finding. */
  public static final class Finding implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Severity severity;
    private final String category;
    private final String message;
    private final String action;

    /**
     * Creates a finding.
     *
     * @param severity finding severity
     * @param category category or evidence area
     * @param message finding message
     * @param action recommended action
     */
    public Finding(Severity severity, String category, String message, String action) {
      if (severity == null) {
        throw new IllegalArgumentException("severity must not be null");
      }
      this.severity = severity;
      this.category = clean(category);
      this.message = clean(message);
      this.action = clean(action);
    }

    /**
     * Gets severity.
     *
     * @return severity
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Converts finding to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("severity", severity.name());
      map.put("category", category);
      map.put("message", message);
      map.put("action", action);
      return map;
    }

    /**
     * Normalizes nullable text.
     *
     * @param value text value
     * @return trimmed text or empty string
     */
    private static String clean(String value) {
      return value == null ? "" : value.trim();
    }
  }

  /** Builder for {@link SafetyStudyReadiness}. */
  public static final class Builder {
    private Verdict explicitVerdict;
    private final List<Finding> findings = new ArrayList<Finding>();
    private final List<SafetyEvidenceReference> evidenceReferences = new ArrayList<SafetyEvidenceReference>();

    /** Creates a builder. */
    private Builder() {
    }

    /**
     * Sets an explicit verdict.
     *
     * @param verdict readiness verdict
     * @return this builder
     */
    public Builder verdict(Verdict verdict) {
      this.explicitVerdict = verdict;
      return this;
    }

    /**
     * Adds a finding.
     *
     * @param severity finding severity
     * @param category category or evidence area
     * @param message finding message
     * @param action recommended action
     * @return this builder
     */
    public Builder addFinding(Severity severity, String category, String message, String action) {
      findings.add(new Finding(severity, category, message, action));
      return this;
    }

    /**
     * Adds an informational finding.
     *
     * @param category category or evidence area
     * @param message finding message
     * @param action recommended action
     * @return this builder
     */
    public Builder addInfo(String category, String message, String action) {
      return addFinding(Severity.INFO, category, message, action);
    }

    /**
     * Adds a warning finding.
     *
     * @param category category or evidence area
     * @param message finding message
     * @param action recommended action
     * @return this builder
     */
    public Builder addWarning(String category, String message, String action) {
      return addFinding(Severity.WARNING, category, message, action);
    }

    /**
     * Adds a blocker finding.
     *
     * @param category category or evidence area
     * @param message finding message
     * @param action recommended action
     * @return this builder
     */
    public Builder addBlocker(String category, String message, String action) {
      return addFinding(Severity.BLOCKER, category, message, action);
    }

    /**
     * Adds an evidence reference.
     *
     * @param reference evidence reference; ignored when null
     * @return this builder
     */
    public Builder addEvidenceReference(SafetyEvidenceReference reference) {
      if (reference != null) {
        evidenceReferences.add(reference);
      }
      return this;
    }

    /**
     * Merges another readiness object into this builder.
     *
     * @param readiness readiness object; ignored when null
     * @return this builder
     */
    public Builder merge(SafetyStudyReadiness readiness) {
      if (readiness == null) {
        return this;
      }
      findings.addAll(readiness.getFindings());
      evidenceReferences.addAll(readiness.getEvidenceReferences());
      return this;
    }

    /**
     * Builds readiness.
     *
     * @return readiness result
     */
    public SafetyStudyReadiness build() {
      return new SafetyStudyReadiness(this);
    }
  }
}
