package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * JSON-friendly report from a safety-system barrier performance assessment.
 *
 * <p>
 * The report captures one assessment per barrier and demand case. It is designed for agentic STID
 * workflows where document evidence, instrument status, SIS voting status, and NeqSim consequence
 * loads need to be auditable in one object.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetySystemPerformanceReport implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Overall and per-barrier performance verdict. */
  public enum Verdict {
    /** All mandatory checks passed with no warnings. */
    PASS,
    /** No mandatory check failed, but evidence or completeness warnings remain. */
    PASS_WITH_WARNINGS,
    /** One or more mandatory checks failed. */
    FAIL
  }

  /** Severity of one assessment finding. */
  public enum FindingSeverity {
    /** Informational finding. */
    INFO,
    /** Warning that should be reviewed before claiming full credit. */
    WARNING,
    /** Failing finding that prevents barrier credit. */
    FAIL
  }

  private final String registerId;
  private String name = "";
  private Verdict overallVerdict = Verdict.PASS;
  private final List<BarrierAssessment> assessments = new ArrayList<BarrierAssessment>();

  /**
   * Creates a performance report.
   *
   * @param registerId source barrier-register identifier
   */
  public SafetySystemPerformanceReport(String registerId) {
    this.registerId = normalize(registerId);
  }

  /**
   * Normalizes nullable text values for safe JSON export.
   *
   * @param value text value to normalize
   * @return trimmed text or empty string
   */
  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Gets the source barrier-register identifier.
   *
   * @return register identifier
   */
  public String getRegisterId() {
    return registerId;
  }

  /**
   * Gets the report name.
   *
   * @return report name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the report name.
   *
   * @param name report name
   * @return this report
   */
  public SafetySystemPerformanceReport setName(String name) {
    this.name = normalize(name);
    return this;
  }

  /**
   * Gets the overall verdict.
   *
   * @return overall verdict
   */
  public Verdict getOverallVerdict() {
    return overallVerdict;
  }

  /**
   * Adds one barrier assessment and updates the overall verdict.
   *
   * @param assessment assessment to add
   * @return this report
   */
  public SafetySystemPerformanceReport addAssessment(BarrierAssessment assessment) {
    if (assessment != null) {
      assessments.add(assessment);
      mergeOverallVerdict(assessment.getVerdict());
    }
    return this;
  }

  /**
   * Gets all barrier assessments.
   *
   * @return copy of assessments
   */
  public List<BarrierAssessment> getAssessments() {
    return new ArrayList<BarrierAssessment>(assessments);
  }

  /**
   * Counts assessments with a specific verdict.
   *
   * @param verdict verdict to count
   * @return number of matching assessments
   */
  public int countAssessments(Verdict verdict) {
    int count = 0;
    for (BarrierAssessment assessment : assessments) {
      if (assessment.getVerdict() == verdict) {
        count++;
      }
    }
    return count;
  }

  /**
   * Merges a per-assessment verdict into the overall report verdict.
   *
   * @param verdict assessment verdict
   */
  private void mergeOverallVerdict(Verdict verdict) {
    if (verdict == Verdict.FAIL) {
      overallVerdict = Verdict.FAIL;
    } else if (verdict == Verdict.PASS_WITH_WARNINGS && overallVerdict == Verdict.PASS) {
      overallVerdict = Verdict.PASS_WITH_WARNINGS;
    }
  }

  /**
   * Converts the report to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("registerId", registerId);
    map.put("name", name);
    map.put("overallVerdict", overallVerdict.name());
    map.put("assessmentCount", assessments.size());
    map.put("passCount", countAssessments(Verdict.PASS));
    map.put("warningCount", countAssessments(Verdict.PASS_WITH_WARNINGS));
    map.put("failCount", countAssessments(Verdict.FAIL));

    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (BarrierAssessment assessment : assessments) {
      rows.add(assessment.toMap());
    }
    map.put("assessments", rows);
    return map;
  }

  /**
   * Converts the report to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Finding from one safety-system assessment.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Finding implements Serializable {
    private static final long serialVersionUID = 1L;

    private final FindingSeverity severity;
    private final String message;
    private final String remediation;

    /**
     * Creates a finding.
     *
     * @param severity finding severity
     * @param message finding message
     * @param remediation recommended remediation
     */
    public Finding(FindingSeverity severity, String message, String remediation) {
      this.severity = severity == null ? FindingSeverity.INFO : severity;
      this.message = normalize(message);
      this.remediation = normalize(remediation);
    }

    /**
     * Gets the finding severity.
     *
     * @return finding severity
     */
    public FindingSeverity getSeverity() {
      return severity;
    }

    /**
     * Gets the finding message.
     *
     * @return finding message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Gets the recommended remediation.
     *
     * @return recommended remediation
     */
    public String getRemediation() {
      return remediation;
    }

    /**
     * Converts the finding to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("severity", severity.name());
      map.put("message", message);
      map.put("remediation", remediation);
      return map;
    }
  }

  /**
   * Assessment of one barrier for one demand case or generic register check.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class BarrierAssessment implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String barrierId;
    private String barrierName = "";
    private String demandId = "";
    private SafetySystemCategory category = SafetySystemCategory.UNKNOWN;
    private Verdict verdict = Verdict.PASS;
    private final Map<String, Object> metrics = new LinkedHashMap<String, Object>();
    private final List<String> instrumentTags = new ArrayList<String>();
    private final List<String> safetyInstrumentedFunctions = new ArrayList<String>();
    private final List<Finding> findings = new ArrayList<Finding>();

    /**
     * Creates a barrier assessment.
     *
     * @param barrierId barrier identifier
     */
    public BarrierAssessment(String barrierId) {
      this.barrierId = normalize(barrierId);
    }

    /**
     * Gets the barrier identifier.
     *
     * @return barrier identifier
     */
    public String getBarrierId() {
      return barrierId;
    }

    /**
     * Gets the barrier name.
     *
     * @return barrier name
     */
    public String getBarrierName() {
      return barrierName;
    }

    /**
     * Sets the barrier name.
     *
     * @param barrierName barrier name
     * @return this assessment
     */
    public BarrierAssessment setBarrierName(String barrierName) {
      this.barrierName = normalize(barrierName);
      return this;
    }

    /**
     * Gets the demand-case identifier.
     *
     * @return demand-case identifier, or empty string when not set
     */
    public String getDemandId() {
      return demandId;
    }

    /**
     * Sets the demand-case identifier.
     *
     * @param demandId demand-case identifier
     * @return this assessment
     */
    public BarrierAssessment setDemandId(String demandId) {
      this.demandId = normalize(demandId);
      return this;
    }

    /**
     * Gets the safety-system category.
     *
     * @return safety-system category
     */
    public SafetySystemCategory getCategory() {
      return category;
    }

    /**
     * Sets the safety-system category.
     *
     * @param category safety-system category
     * @return this assessment
     */
    public BarrierAssessment setCategory(SafetySystemCategory category) {
      this.category = category == null ? SafetySystemCategory.UNKNOWN : category;
      return this;
    }

    /**
     * Gets the assessment verdict.
     *
     * @return assessment verdict
     */
    public Verdict getVerdict() {
      return verdict;
    }

    /**
     * Adds a numeric or textual metric to the assessment.
     *
     * @param name metric name
     * @param value metric value
     * @return this assessment
     */
    public BarrierAssessment addMetric(String name, Object value) {
      String key = normalize(name);
      if (!key.isEmpty()) {
        metrics.put(key, value);
      }
      return this;
    }

    /**
     * Adds an instrument tag involved in the assessment.
     *
     * @param tag instrument tag or name
     * @return this assessment
     */
    public BarrierAssessment addInstrumentTag(String tag) {
      String normalized = normalize(tag);
      if (!normalized.isEmpty() && !instrumentTags.contains(normalized)) {
        instrumentTags.add(normalized);
      }
      return this;
    }

    /**
     * Adds a safety instrumented function involved in the assessment.
     *
     * @param name safety instrumented function name
     * @return this assessment
     */
    public BarrierAssessment addSafetyInstrumentedFunction(String name) {
      String normalized = normalize(name);
      if (!normalized.isEmpty() && !safetyInstrumentedFunctions.contains(normalized)) {
        safetyInstrumentedFunctions.add(normalized);
      }
      return this;
    }

    /**
     * Adds a finding and updates the assessment verdict.
     *
     * @param severity finding severity
     * @param message finding message
     * @param remediation recommended remediation
     * @return this assessment
     */
    public BarrierAssessment addFinding(FindingSeverity severity, String message,
        String remediation) {
      Finding finding = new Finding(severity, message, remediation);
      findings.add(finding);
      mergeVerdict(finding.getSeverity());
      return this;
    }

    /**
     * Gets the findings for this assessment.
     *
     * @return copy of findings
     */
    public List<Finding> getFindings() {
      return new ArrayList<Finding>(findings);
    }

    /**
     * Merges a finding severity into the assessment verdict.
     *
     * @param severity finding severity
     */
    private void mergeVerdict(FindingSeverity severity) {
      if (severity == FindingSeverity.FAIL) {
        verdict = Verdict.FAIL;
      } else if (severity == FindingSeverity.WARNING && verdict == Verdict.PASS) {
        verdict = Verdict.PASS_WITH_WARNINGS;
      }
    }

    /**
     * Converts the assessment to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("barrierId", barrierId);
      map.put("barrierName", barrierName);
      map.put("demandId", demandId);
      map.put("category", category.name());
      map.put("verdict", verdict.name());
      map.put("metrics", new LinkedHashMap<String, Object>(metrics));
      map.put("instrumentTags", new ArrayList<String>(instrumentTags));
      map.put("safetyInstrumentedFunctions", new ArrayList<String>(safetyInstrumentedFunctions));

      List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
      for (Finding finding : findings) {
        rows.add(finding.toMap());
      }
      map.put("findings", rows);
      return map;
    }
  }
}
