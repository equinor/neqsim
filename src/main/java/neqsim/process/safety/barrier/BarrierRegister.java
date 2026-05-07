package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Register of safety critical elements, barriers, performance standards, and evidence.
 *
 * <p>
 * The register is designed as a stable handoff object between agents that read technical
 * documentation and NeqSim calculations that quantify barrier performance, LOC scenarios, and risk
 * reduction. It deliberately stores both standalone barriers and SCE-linked barriers so early
 * studies can start with incomplete documentation and improve traceability over time.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BarrierRegister implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String registerId;
  private String name = "";
  private final Map<String, SafetyCriticalElement> safetyCriticalElements =
      new LinkedHashMap<String, SafetyCriticalElement>();
  private final Map<String, SafetyBarrier> barriers = new LinkedHashMap<String, SafetyBarrier>();
  private final Map<String, PerformanceStandard> performanceStandards =
      new LinkedHashMap<String, PerformanceStandard>();
  private final List<DocumentEvidence> evidence = new ArrayList<DocumentEvidence>();

  /**
   * Creates a barrier register.
   *
   * @param registerId stable register identifier
   */
  public BarrierRegister(String registerId) {
    this.registerId = normalize(registerId);
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
   * Gets the register identifier.
   *
   * @return register identifier
   */
  public String getRegisterId() {
    return registerId;
  }

  /**
   * Gets the register name.
   *
   * @return register name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the register name.
   *
   * @param name register name
   * @return this register
   */
  public BarrierRegister setName(String name) {
    this.name = normalize(name);
    return this;
  }

  /**
   * Adds a safety critical element.
   *
   * @param element safety critical element
   * @return this register
   */
  public BarrierRegister addSafetyCriticalElement(SafetyCriticalElement element) {
    if (element != null) {
      safetyCriticalElements.put(element.getId(), element);
      for (SafetyBarrier barrier : element.getBarriers()) {
        barriers.put(barrier.getId(), barrier);
      }
    }
    return this;
  }

  /**
   * Adds a standalone barrier.
   *
   * @param barrier safety barrier
   * @return this register
   */
  public BarrierRegister addBarrier(SafetyBarrier barrier) {
    if (barrier != null) {
      barriers.put(barrier.getId(), barrier);
    }
    return this;
  }

  /**
   * Adds a performance standard.
   *
   * @param standard performance standard
   * @return this register
   */
  public BarrierRegister addPerformanceStandard(PerformanceStandard standard) {
    if (standard != null) {
      performanceStandards.put(standard.getId(), standard);
    }
    return this;
  }

  /**
   * Adds register-level document evidence.
   *
   * @param evidenceItem evidence item
   * @return this register
   */
  public BarrierRegister addEvidence(DocumentEvidence evidenceItem) {
    if (evidenceItem != null) {
      evidence.add(evidenceItem);
    }
    return this;
  }

  /**
   * Links a barrier to an SCE, registering both relationships when found.
   *
   * @param barrierId barrier identifier
   * @param sceId safety critical element identifier
   * @return true when both records existed and were linked
   */
  public boolean linkBarrierToSafetyCriticalElement(String barrierId, String sceId) {
    SafetyBarrier barrier = barriers.get(barrierId);
    SafetyCriticalElement element = safetyCriticalElements.get(sceId);
    if (barrier == null || element == null) {
      return false;
    }
    element.addBarrier(barrier);
    return true;
  }

  /**
   * Gets a safety critical element by identifier.
   *
   * @param sceId safety critical element identifier
   * @return matching SCE, or null when absent
   */
  public SafetyCriticalElement getSafetyCriticalElement(String sceId) {
    return safetyCriticalElements.get(sceId);
  }

  /**
   * Gets a barrier by identifier.
   *
   * @param barrierId barrier identifier
   * @return matching barrier, or null when absent
   */
  public SafetyBarrier getBarrier(String barrierId) {
    return barriers.get(barrierId);
  }

  /**
   * Gets a performance standard by identifier.
   *
   * @param standardId performance standard identifier
   * @return matching performance standard, or null when absent
   */
  public PerformanceStandard getPerformanceStandard(String standardId) {
    return performanceStandards.get(standardId);
  }

  /**
   * Gets all safety critical elements.
   *
   * @return copy of safety critical elements
   */
  public List<SafetyCriticalElement> getSafetyCriticalElements() {
    return new ArrayList<SafetyCriticalElement>(safetyCriticalElements.values());
  }

  /**
   * Gets all registered barriers.
   *
   * @return copy of barriers
   */
  public List<SafetyBarrier> getBarriers() {
    return new ArrayList<SafetyBarrier>(barriers.values());
  }

  /**
   * Gets all registered performance standards.
   *
   * @return copy of performance standards
   */
  public List<PerformanceStandard> getPerformanceStandards() {
    return new ArrayList<PerformanceStandard>(performanceStandards.values());
  }

  /**
   * Gets barriers linked to an equipment tag.
   *
   * @param equipmentTag equipment tag to search for
   * @return barriers linked to the tag
   */
  public List<SafetyBarrier> getBarriersForEquipment(String equipmentTag) {
    String normalizedTag = normalize(equipmentTag);
    List<SafetyBarrier> result = new ArrayList<SafetyBarrier>();
    for (SafetyBarrier barrier : barriers.values()) {
      if (barrier.getLinkedEquipmentTags().contains(normalizedTag)) {
        result.add(barrier);
      }
    }
    return result;
  }

  /**
   * Gets non-available barriers.
   *
   * @return barriers with impaired, bypassed, out-of-service, or unknown status
   */
  public List<SafetyBarrier> getImpairedBarriers() {
    List<SafetyBarrier> result = new ArrayList<SafetyBarrier>();
    for (SafetyBarrier barrier : barriers.values()) {
      if (!barrier.isAvailable()) {
        result.add(barrier);
      }
    }
    return result;
  }

  /**
   * Validates the register and all linked objects.
   *
   * @return list of validation findings, empty when no findings were detected
   */
  public List<String> validate() {
    List<String> findings = new ArrayList<String>();
    if (registerId.isEmpty()) {
      findings.add("Barrier register id is missing.");
    }
    if (safetyCriticalElements.isEmpty() && barriers.isEmpty()) {
      findings.add("Barrier register contains no SCEs or barriers.");
    }
    for (SafetyCriticalElement element : safetyCriticalElements.values()) {
      List<String> elementFindings = element.validate();
      for (String finding : elementFindings) {
        findings.add("SCE " + element.getId() + ": " + finding);
      }
    }
    for (SafetyBarrier barrier : barriers.values()) {
      boolean foundInSce = false;
      for (SafetyCriticalElement element : safetyCriticalElements.values()) {
        if (element.getBarrier(barrier.getId()) != null) {
          foundInSce = true;
          break;
        }
      }
      if (!foundInSce) {
        findings.add("Barrier " + barrier.getId() + " is not linked to an SCE.");
      }
    }
    return findings;
  }

  /**
   * Converts the register to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("registerId", registerId);
    map.put("name", name);

    List<Map<String, Object>> sceList = new ArrayList<Map<String, Object>>();
    for (SafetyCriticalElement element : safetyCriticalElements.values()) {
      sceList.add(element.toMap());
    }
    map.put("safetyCriticalElements", sceList);

    List<Map<String, Object>> barrierList = new ArrayList<Map<String, Object>>();
    for (SafetyBarrier barrier : barriers.values()) {
      barrierList.add(barrier.toMap());
    }
    map.put("barriers", barrierList);

    List<Map<String, Object>> standardList = new ArrayList<Map<String, Object>>();
    for (PerformanceStandard standard : performanceStandards.values()) {
      standardList.add(standard.toMap());
    }
    map.put("performanceStandards", standardList);

    List<Map<String, Object>> evidenceList = new ArrayList<Map<String, Object>>();
    for (DocumentEvidence item : evidence) {
      evidenceList.add(item.toMap());
    }
    map.put("evidence", evidenceList);
    map.put("impairedBarrierCount", getImpairedBarriers().size());
    map.put("validationFindings", validate());
    return map;
  }

  /**
   * Converts the register to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }
}
