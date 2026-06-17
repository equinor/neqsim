package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Safety critical element (SCE) containing one or more safety barriers.
 *
 * <p>
 * The class supports barrier management by grouping technical or operational barriers under the
 * equipment, function, or system that must remain available to manage major accident risk.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetyCriticalElement implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Broad type of safety critical element. */
  public enum ElementType {
    /** Process equipment such as vessels, compressors, or pipelines. */
    PROCESS_EQUIPMENT,
    /** Instrumented safety function or logic system. */
    INSTRUMENTED_FUNCTION,
    /** Fire, gas, deluge, PFP, or other fire protection element. */
    FIRE_PROTECTION,
    /** Structural item such as blast wall, firewall, or support steel. */
    STRUCTURAL,
    /** Utility system required for safe operation. */
    UTILITY,
    /** Element type has not been classified. */
    OTHER
  }

  private final String id;
  private String tag = "";
  private String name = "";
  private ElementType type = ElementType.OTHER;
  private String owner = "";
  private final List<String> equipmentTags = new ArrayList<String>();
  private final List<SafetyBarrier> barriers = new ArrayList<SafetyBarrier>();
  private final List<DocumentEvidence> evidence = new ArrayList<DocumentEvidence>();

  /**
   * Creates a safety critical element.
   *
   * @param id stable SCE identifier
   */
  public SafetyCriticalElement(String id) {
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
   * Gets the SCE identifier.
   *
   * @return SCE identifier
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the SCE tag.
   *
   * @return SCE tag
   */
  public String getTag() {
    return tag;
  }

  /**
   * Sets the SCE tag.
   *
   * @param tag tag or reference designation
   * @return this SCE
   */
  public SafetyCriticalElement setTag(String tag) {
    this.tag = normalize(tag);
    return this;
  }

  /**
   * Gets the SCE name.
   *
   * @return SCE name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the SCE name.
   *
   * @param name SCE name
   * @return this SCE
   */
  public SafetyCriticalElement setName(String name) {
    this.name = normalize(name);
    return this;
  }

  /**
   * Gets the SCE type.
   *
   * @return SCE type
   */
  public ElementType getType() {
    return type;
  }

  /**
   * Sets the SCE type.
   *
   * @param type SCE type
   * @return this SCE
   */
  public SafetyCriticalElement setType(ElementType type) {
    this.type = type == null ? ElementType.OTHER : type;
    return this;
  }

  /**
   * Gets the SCE owner.
   *
   * @return owner discipline or role
   */
  public String getOwner() {
    return owner;
  }

  /**
   * Sets the SCE owner.
   *
   * @param owner owner discipline or role
   * @return this SCE
   */
  public SafetyCriticalElement setOwner(String owner) {
    this.owner = normalize(owner);
    return this;
  }

  /**
   * Adds a linked process equipment tag.
   *
   * @param equipmentTag process equipment tag
   * @return this SCE
   */
  public SafetyCriticalElement addEquipmentTag(String equipmentTag) {
    String tagValue = normalize(equipmentTag);
    if (!tagValue.isEmpty() && !equipmentTags.contains(tagValue)) {
      equipmentTags.add(tagValue);
    }
    return this;
  }

  /**
   * Adds a barrier to this SCE.
   *
   * @param barrier safety barrier
   * @return this SCE
   */
  public SafetyCriticalElement addBarrier(SafetyBarrier barrier) {
    if (barrier != null && getBarrier(barrier.getId()) == null) {
      barriers.add(barrier);
    }
    return this;
  }

  /**
   * Adds traceable document evidence.
   *
   * @param evidenceItem evidence item
   * @return this SCE
   */
  public SafetyCriticalElement addEvidence(DocumentEvidence evidenceItem) {
    if (evidenceItem != null) {
      evidence.add(evidenceItem);
    }
    return this;
  }

  /**
   * Gets a barrier by identifier.
   *
   * @param barrierId barrier identifier
   * @return matching barrier, or null when absent
   */
  public SafetyBarrier getBarrier(String barrierId) {
    for (SafetyBarrier barrier : barriers) {
      if (barrier.getId().equals(barrierId)) {
        return barrier;
      }
    }
    return null;
  }

  /**
   * Gets linked equipment tags.
   *
   * @return copy of equipment tags
   */
  public List<String> getEquipmentTags() {
    return new ArrayList<String>(equipmentTags);
  }

  /**
   * Gets barriers in this SCE.
   *
   * @return copy of barriers
   */
  public List<SafetyBarrier> getBarriers() {
    return new ArrayList<SafetyBarrier>(barriers);
  }

  /**
   * Gets evidence linked directly to this SCE.
   *
   * @return copy of evidence items
   */
  public List<DocumentEvidence> getEvidence() {
    return new ArrayList<DocumentEvidence>(evidence);
  }

  /**
   * Counts available barriers.
   *
   * @return number of barriers with available status
   */
  public int getAvailableBarrierCount() {
    int count = 0;
    for (SafetyBarrier barrier : barriers) {
      if (barrier.isAvailable()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Checks whether any barrier is impaired, bypassed, out of service, or unknown.
   *
   * @return true when the SCE has at least one non-available barrier
   */
  public boolean hasImpairedBarrier() {
    for (SafetyBarrier barrier : barriers) {
      if (!barrier.isAvailable()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether traceable evidence is linked directly or through a barrier.
   *
   * @return true when traceable evidence exists
   */
  public boolean hasTraceableEvidence() {
    for (DocumentEvidence item : evidence) {
      if (item.isTraceable()) {
        return true;
      }
    }
    for (SafetyBarrier barrier : barriers) {
      if (barrier.hasTraceableEvidence()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validates the SCE for completeness and barrier health.
   *
   * @return list of validation findings, empty when no findings were detected
   */
  public List<String> validate() {
    List<String> findings = new ArrayList<String>();
    if (id.isEmpty()) {
      findings.add("SCE id is missing.");
    }
    if (tag.isEmpty() && name.isEmpty()) {
      findings.add("SCE tag or name is missing.");
    }
    if (barriers.isEmpty()) {
      findings.add("SCE has no linked barriers.");
    }
    if (!hasTraceableEvidence()) {
      findings.add("SCE has no traceable evidence.");
    }
    for (SafetyBarrier barrier : barriers) {
      List<String> barrierFindings = barrier.validate();
      for (String finding : barrierFindings) {
        findings.add("Barrier " + barrier.getId() + ": " + finding);
      }
    }
    return findings;
  }

  /**
   * Converts the SCE to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    map.put("tag", tag);
    map.put("name", name);
    map.put("type", type.name());
    map.put("owner", owner);
    map.put("equipmentTags", new ArrayList<String>(equipmentTags));
    map.put("availableBarrierCount", getAvailableBarrierCount());
    map.put("hasImpairedBarrier", hasImpairedBarrier());

    List<Map<String, Object>> barrierList = new ArrayList<Map<String, Object>>();
    for (SafetyBarrier barrier : barriers) {
      barrierList.add(barrier.toMap());
    }
    map.put("barriers", barrierList);

    List<Map<String, Object>> evidenceList = new ArrayList<Map<String, Object>>();
    for (DocumentEvidence item : evidence) {
      evidenceList.add(item.toMap());
    }
    map.put("evidence", evidenceList);
    map.put("validationFindings", validate());
    return map;
  }

  /**
   * Converts the SCE to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }
}
