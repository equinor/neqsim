package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Performance standard for a process safety function or safety critical element.
 *
 * <p>
 * A performance standard defines what a barrier must do, how well it must perform, and what
 * evidence demonstrates compliance. It is the natural bridge between extracted technical
 * documentation and quantitative NeqSim calculations such as LOPA, SIL, source terms, and
 * consequence analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PerformanceStandard implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Demand mode used for the safety function. */
  public enum DemandMode {
    /** Low-demand safety function, typically evaluated with PFDavg. */
    LOW_DEMAND,
    /** High-demand safety function, typically evaluated with PFH or availability. */
    HIGH_DEMAND,
    /** Continuous safety function where availability and impairment matter continuously. */
    CONTINUOUS,
    /** Demand mode is not yet classified. */
    OTHER
  }

  private final String id;
  private String title = "";
  private String safetyFunction = "";
  private DemandMode demandMode = DemandMode.OTHER;
  private double targetPfd = Double.NaN;
  private double requiredAvailability = Double.NaN;
  private double proofTestIntervalHours = Double.NaN;
  private double responseTimeSeconds = Double.NaN;
  private final List<String> acceptanceCriteria = new ArrayList<String>();
  private final List<DocumentEvidence> evidence = new ArrayList<DocumentEvidence>();

  /**
   * Creates a performance standard.
   *
   * @param id stable performance standard identifier
   */
  public PerformanceStandard(String id) {
    this.id = normalize(id);
  }

  /**
   * Normalizes nullable text values for safe JSON export.
   *
   * @param value text value to normalize
   * @return trimmed text or an empty string
   */
  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Gets the performance standard identifier.
   *
   * @return performance standard identifier
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the title.
   *
   * @return title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Sets the title.
   *
   * @param title performance standard title
   * @return this performance standard
   */
  public PerformanceStandard setTitle(String title) {
    this.title = normalize(title);
    return this;
  }

  /**
   * Gets the safety function description.
   *
   * @return safety function description
   */
  public String getSafetyFunction() {
    return safetyFunction;
  }

  /**
   * Sets the safety function description.
   *
   * @param safetyFunction safety function description
   * @return this performance standard
   */
  public PerformanceStandard setSafetyFunction(String safetyFunction) {
    this.safetyFunction = normalize(safetyFunction);
    return this;
  }

  /**
   * Gets the demand mode.
   *
   * @return demand mode
   */
  public DemandMode getDemandMode() {
    return demandMode;
  }

  /**
   * Sets the demand mode.
   *
   * @param demandMode demand mode
   * @return this performance standard
   */
  public PerformanceStandard setDemandMode(DemandMode demandMode) {
    this.demandMode = demandMode == null ? DemandMode.OTHER : demandMode;
    return this;
  }

  /**
   * Gets the target probability of failure on demand.
   *
   * @return target PFD, or NaN when not specified
   */
  public double getTargetPfd() {
    return targetPfd;
  }

  /**
   * Sets the target probability of failure on demand.
   *
   * @param targetPfd target PFD in the range 0 to 1
   * @return this performance standard
   */
  public PerformanceStandard setTargetPfd(double targetPfd) {
    this.targetPfd = targetPfd;
    return this;
  }

  /**
   * Gets the required availability.
   *
   * @return required availability, or NaN when not specified
   */
  public double getRequiredAvailability() {
    return requiredAvailability;
  }

  /**
   * Sets the required availability.
   *
   * @param requiredAvailability availability in the range 0 to 1
   * @return this performance standard
   */
  public PerformanceStandard setRequiredAvailability(double requiredAvailability) {
    this.requiredAvailability = requiredAvailability;
    return this;
  }

  /**
   * Gets the proof-test interval.
   *
   * @return proof-test interval in hours, or NaN when not specified
   */
  public double getProofTestIntervalHours() {
    return proofTestIntervalHours;
  }

  /**
   * Sets the proof-test interval.
   *
   * @param proofTestIntervalHours proof-test interval in hours
   * @return this performance standard
   */
  public PerformanceStandard setProofTestIntervalHours(double proofTestIntervalHours) {
    this.proofTestIntervalHours = proofTestIntervalHours;
    return this;
  }

  /**
   * Gets the response time requirement.
   *
   * @return response time in seconds, or NaN when not specified
   */
  public double getResponseTimeSeconds() {
    return responseTimeSeconds;
  }

  /**
   * Sets the response time requirement.
   *
   * @param responseTimeSeconds response time in seconds
   * @return this performance standard
   */
  public PerformanceStandard setResponseTimeSeconds(double responseTimeSeconds) {
    this.responseTimeSeconds = responseTimeSeconds;
    return this;
  }

  /**
   * Adds an acceptance criterion.
   *
   * @param criterion criterion text
   * @return this performance standard
   */
  public PerformanceStandard addAcceptanceCriterion(String criterion) {
    String normalized = normalize(criterion);
    if (!normalized.isEmpty()) {
      acceptanceCriteria.add(normalized);
    }
    return this;
  }

  /**
   * Adds traceable document evidence.
   *
   * @param evidenceItem evidence item
   * @return this performance standard
   */
  public PerformanceStandard addEvidence(DocumentEvidence evidenceItem) {
    if (evidenceItem != null) {
      evidence.add(evidenceItem);
    }
    return this;
  }

  /**
   * Gets the acceptance criteria.
   *
   * @return copy of acceptance criteria
   */
  public List<String> getAcceptanceCriteria() {
    return new ArrayList<String>(acceptanceCriteria);
  }

  /**
   * Gets the evidence items.
   *
   * @return copy of evidence items
   */
  public List<DocumentEvidence> getEvidence() {
    return new ArrayList<DocumentEvidence>(evidence);
  }

  /**
   * Checks whether traceable evidence is available.
   *
   * @return true when at least one traceable evidence item exists
   */
  public boolean hasTraceableEvidence() {
    for (DocumentEvidence item : evidence) {
      if (item.isTraceable()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validates the performance standard for completeness and physical bounds.
   *
   * @return list of validation findings, empty when no findings were detected
   */
  public List<String> validate() {
    List<String> findings = new ArrayList<String>();
    if (id.isEmpty()) {
      findings.add("Performance standard id is missing.");
    }
    if (safetyFunction.isEmpty()) {
      findings.add("Safety function description is missing.");
    }
    if (!Double.isNaN(targetPfd) && (targetPfd <= 0.0 || targetPfd > 1.0)) {
      findings.add("Target PFD must be in the range 0 to 1.");
    }
    if (!Double.isNaN(requiredAvailability)
        && (requiredAvailability < 0.0 || requiredAvailability > 1.0)) {
      findings.add("Required availability must be in the range 0 to 1.");
    }
    if (!Double.isNaN(proofTestIntervalHours) && proofTestIntervalHours <= 0.0) {
      findings.add("Proof-test interval must be positive.");
    }
    if (acceptanceCriteria.isEmpty()) {
      findings.add("At least one acceptance criterion should be defined.");
    }
    if (!hasTraceableEvidence()) {
      findings.add("No traceable document evidence is linked.");
    }
    return findings;
  }

  /**
   * Converts the performance standard to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    map.put("title", title);
    map.put("safetyFunction", safetyFunction);
    map.put("demandMode", demandMode.name());
    map.put("targetPfd", targetPfd);
    map.put("requiredAvailability", requiredAvailability);
    map.put("proofTestIntervalHours", proofTestIntervalHours);
    map.put("responseTimeSeconds", responseTimeSeconds);
    map.put("acceptanceCriteria", new ArrayList<String>(acceptanceCriteria));

    List<Map<String, Object>> evidenceList = new ArrayList<Map<String, Object>>();
    for (DocumentEvidence item : evidence) {
      evidenceList.add(item.toMap());
    }
    map.put("evidence", evidenceList);
    map.put("validationFindings", validate());
    return map;
  }

  /**
   * Converts the performance standard to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }
}
