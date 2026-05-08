package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.risk.bowtie.BowTieModel;

/**
 * Safety barrier or protection layer with document evidence and performance-standard links.
 *
 * <p>
 * A safety barrier may represent a technical, operational, or organizational barrier. It can be
 * linked to process equipment tags, bow-tie hazards, performance standards, and source documents
 * extracted by agents from technical documentation.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetyBarrier implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Barrier position relative to a top event. */
  public enum BarrierType {
    /** Prevents the top event from occurring. */
    PREVENTION,
    /** Mitigates consequences after the top event occurs. */
    MITIGATION,
    /** Provides both preventive and mitigative credit. */
    BOTH
  }

  /** Operational status of the barrier. */
  public enum BarrierStatus {
    /** Barrier is available and can be credited. */
    AVAILABLE,
    /** Barrier is degraded but may retain partial credit. */
    IMPAIRED,
    /** Barrier is bypassed and should not be credited without explicit approval. */
    BYPASSED,
    /** Barrier is out of service and should not be credited. */
    OUT_OF_SERVICE,
    /** Barrier status has not been confirmed. */
    UNKNOWN
  }

  private final String id;
  private String name = "";
  private String description = "";
  private BarrierType type = BarrierType.PREVENTION;
  private BarrierStatus status = BarrierStatus.UNKNOWN;
  private String safetyFunction = "";
  private String owner = "";
  private double pfd = Double.NaN;
  private double effectiveness = Double.NaN;
  private PerformanceStandard performanceStandard;
  private final List<String> linkedEquipmentTags = new ArrayList<String>();
  private final List<String> linkedHazardIds = new ArrayList<String>();
  private final List<DocumentEvidence> evidence = new ArrayList<DocumentEvidence>();

  /**
   * Creates a safety barrier.
   *
   * @param id stable barrier identifier
   */
  public SafetyBarrier(String id) {
    this.id = normalize(id);
  }

  /**
   * Creates a safety barrier from a bow-tie barrier.
   *
   * @param bowTieBarrier bow-tie barrier to convert
   * @return safety barrier with copied type, PFD, owner, and status data
   */
  public static SafetyBarrier fromBowTieBarrier(BowTieModel.Barrier bowTieBarrier) {
    SafetyBarrier barrier = new SafetyBarrier(bowTieBarrier.getId())
        .setName(bowTieBarrier.getDescription()).setDescription(bowTieBarrier.getDescription())
        .setPfd(bowTieBarrier.getPfd()).setOwner(bowTieBarrier.getOwner());
    if (bowTieBarrier.getBarrierType() == BowTieModel.BarrierType.MITIGATION) {
      barrier.setType(BarrierType.MITIGATION);
    } else if (bowTieBarrier.getBarrierType() == BowTieModel.BarrierType.BOTH) {
      barrier.setType(BarrierType.BOTH);
    } else {
      barrier.setType(BarrierType.PREVENTION);
    }
    barrier.setStatus(
        bowTieBarrier.isFunctional() ? BarrierStatus.AVAILABLE : BarrierStatus.OUT_OF_SERVICE);
    if (bowTieBarrier.getVerificationStatus() != null
        && !bowTieBarrier.getVerificationStatus().trim().isEmpty()) {
      barrier.addEvidence(new DocumentEvidence("verification-" + bowTieBarrier.getId(), "", "", "",
          "verificationStatus", 0, "BowTieModel", bowTieBarrier.getVerificationStatus(), 1.0));
    }
    return barrier;
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
   * Gets the barrier identifier.
   *
   * @return barrier identifier
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the barrier name.
   *
   * @return barrier name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the barrier name.
   *
   * @param name barrier name
   * @return this barrier
   */
  public SafetyBarrier setName(String name) {
    this.name = normalize(name);
    return this;
  }

  /**
   * Gets the barrier description.
   *
   * @return barrier description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the barrier description.
   *
   * @param description barrier description
   * @return this barrier
   */
  public SafetyBarrier setDescription(String description) {
    this.description = normalize(description);
    return this;
  }

  /**
   * Gets the barrier type.
   *
   * @return barrier type
   */
  public BarrierType getType() {
    return type;
  }

  /**
   * Sets the barrier type.
   *
   * @param type barrier type
   * @return this barrier
   */
  public SafetyBarrier setType(BarrierType type) {
    this.type = type == null ? BarrierType.PREVENTION : type;
    return this;
  }

  /**
   * Gets the barrier status.
   *
   * @return barrier status
   */
  public BarrierStatus getStatus() {
    return status;
  }

  /**
   * Sets the barrier status.
   *
   * @param status barrier status
   * @return this barrier
   */
  public SafetyBarrier setStatus(BarrierStatus status) {
    this.status = status == null ? BarrierStatus.UNKNOWN : status;
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
   * @return this barrier
   */
  public SafetyBarrier setSafetyFunction(String safetyFunction) {
    this.safetyFunction = normalize(safetyFunction);
    return this;
  }

  /**
   * Gets the barrier owner.
   *
   * @return owner discipline or role
   */
  public String getOwner() {
    return owner;
  }

  /**
   * Sets the barrier owner.
   *
   * @param owner owner discipline or role
   * @return this barrier
   */
  public SafetyBarrier setOwner(String owner) {
    this.owner = normalize(owner);
    return this;
  }

  /**
   * Gets the probability of failure on demand.
   *
   * @return PFD, or NaN when not specified
   */
  public double getPfd() {
    return pfd;
  }

  /**
   * Sets the probability of failure on demand.
   *
   * @param pfd probability of failure on demand
   * @return this barrier
   */
  public SafetyBarrier setPfd(double pfd) {
    this.pfd = pfd;
    this.effectiveness = Double.isNaN(pfd) ? Double.NaN : 1.0 - pfd;
    return this;
  }

  /**
   * Gets the effectiveness.
   *
   * @return effectiveness, or NaN when not specified
   */
  public double getEffectiveness() {
    return effectiveness;
  }

  /**
   * Sets the effectiveness directly for non-PFD barriers.
   *
   * @param effectiveness effectiveness in the range 0 to 1
   * @return this barrier
   */
  public SafetyBarrier setEffectiveness(double effectiveness) {
    this.effectiveness = effectiveness;
    return this;
  }

  /**
   * Gets the linked performance standard.
   *
   * @return performance standard, or null when not linked
   */
  public PerformanceStandard getPerformanceStandard() {
    return performanceStandard;
  }

  /**
   * Links a performance standard.
   *
   * @param performanceStandard performance standard
   * @return this barrier
   */
  public SafetyBarrier setPerformanceStandard(PerformanceStandard performanceStandard) {
    this.performanceStandard = performanceStandard;
    return this;
  }

  /**
   * Adds a linked equipment tag.
   *
   * @param equipmentTag equipment tag
   * @return this barrier
   */
  public SafetyBarrier addEquipmentTag(String equipmentTag) {
    String tag = normalize(equipmentTag);
    if (!tag.isEmpty() && !linkedEquipmentTags.contains(tag)) {
      linkedEquipmentTags.add(tag);
    }
    return this;
  }

  /**
   * Adds a linked hazard identifier.
   *
   * @param hazardId hazard identifier
   * @return this barrier
   */
  public SafetyBarrier addHazardId(String hazardId) {
    String hazard = normalize(hazardId);
    if (!hazard.isEmpty() && !linkedHazardIds.contains(hazard)) {
      linkedHazardIds.add(hazard);
    }
    return this;
  }

  /**
   * Adds traceable document evidence.
   *
   * @param evidenceItem evidence item
   * @return this barrier
   */
  public SafetyBarrier addEvidence(DocumentEvidence evidenceItem) {
    if (evidenceItem != null) {
      evidence.add(evidenceItem);
    }
    return this;
  }

  /**
   * Gets linked equipment tags.
   *
   * @return copy of linked equipment tags
   */
  public List<String> getLinkedEquipmentTags() {
    return new ArrayList<String>(linkedEquipmentTags);
  }

  /**
   * Gets linked hazard identifiers.
   *
   * @return copy of linked hazard identifiers
   */
  public List<String> getLinkedHazardIds() {
    return new ArrayList<String>(linkedHazardIds);
  }

  /**
   * Gets evidence items.
   *
   * @return copy of evidence items
   */
  public List<DocumentEvidence> getEvidence() {
    return new ArrayList<DocumentEvidence>(evidence);
  }

  /**
   * Gets the risk reduction factor.
   *
   * @return RRF, or positive infinity when PFD is zero
   */
  public double getRiskReductionFactor() {
    if (Double.isNaN(pfd)) {
      return Double.NaN;
    }
    return pfd > 0.0 ? 1.0 / pfd : Double.POSITIVE_INFINITY;
  }

  /**
   * Checks whether the barrier can normally be credited.
   *
   * @return true when status is available
   */
  public boolean isAvailable() {
    return status == BarrierStatus.AVAILABLE;
  }

  /**
   * Checks whether traceable evidence is available directly or through the performance standard.
   *
   * @return true when traceable evidence exists
   */
  public boolean hasTraceableEvidence() {
    for (DocumentEvidence item : evidence) {
      if (item.isTraceable()) {
        return true;
      }
    }
    return performanceStandard != null && performanceStandard.hasTraceableEvidence();
  }

  /**
   * Validates the barrier for agent and audit use.
   *
   * @return list of validation findings, empty when no findings were detected
   */
  public List<String> validate() {
    List<String> findings = new ArrayList<String>();
    if (id.isEmpty()) {
      findings.add("Barrier id is missing.");
    }
    if (name.isEmpty() && description.isEmpty()) {
      findings.add("Barrier name or description is missing.");
    }
    if (!Double.isNaN(pfd) && (pfd < 0.0 || pfd > 1.0)) {
      findings.add("Barrier PFD must be in the range 0 to 1.");
    }
    if (!Double.isNaN(effectiveness) && (effectiveness < 0.0 || effectiveness > 1.0)) {
      findings.add("Barrier effectiveness must be in the range 0 to 1.");
    }
    if (status == BarrierStatus.UNKNOWN) {
      findings.add("Barrier status is unknown.");
    }
    if (linkedEquipmentTags.isEmpty()) {
      findings.add("No linked equipment tag is registered.");
    }
    if (performanceStandard == null) {
      findings.add("No performance standard is linked.");
    } else {
      List<String> standardFindings = performanceStandard.validate();
      for (String finding : standardFindings) {
        findings.add("Performance standard " + performanceStandard.getId() + ": " + finding);
      }
    }
    if (!hasTraceableEvidence()) {
      findings.add("No traceable document evidence is linked.");
    }
    return findings;
  }

  /**
   * Converts the barrier to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    map.put("name", name);
    map.put("description", description);
    map.put("type", type.name());
    map.put("status", status.name());
    map.put("safetyFunction", safetyFunction);
    map.put("owner", owner);
    map.put("pfd", pfd);
    map.put("effectiveness", effectiveness);
    map.put("riskReductionFactor", getRiskReductionFactor());
    map.put("linkedEquipmentTags", new ArrayList<String>(linkedEquipmentTags));
    map.put("linkedHazardIds", new ArrayList<String>(linkedHazardIds));
    map.put("performanceStandard",
        performanceStandard == null ? null : performanceStandard.toMap());

    List<Map<String, Object>> evidenceList = new ArrayList<Map<String, Object>>();
    for (DocumentEvidence item : evidence) {
      evidenceList.add(item.toMap());
    }
    map.put("evidence", evidenceList);
    map.put("validationFindings", validate());
    return map;
  }

  /**
   * Converts the barrier to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }
}
