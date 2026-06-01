package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demand and capacity data for one safety-system barrier performance check.
 *
 * <p>
 * A demand case represents the calculated or extracted requirement imposed on a barrier by a
 * scenario, such as a jet-fire heat flux, required deluge application rate, detector response time,
 * or minimum availability. Values may come from NeqSim consequence analysis, STID/firewater/PFP
 * documents, SIS verification reports, or manual engineering input.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetySystemDemand implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String demandId;
  private String barrierId = "";
  private String equipmentTag = "";
  private String scenario = "";
  private SafetySystemCategory category = SafetySystemCategory.UNKNOWN;
  private double demandValue = Double.NaN;
  private double capacityValue = Double.NaN;
  private String demandUnit = "";
  private double requiredResponseTimeSeconds = Double.NaN;
  private double actualResponseTimeSeconds = Double.NaN;
  private double requiredAvailability = Double.NaN;
  private double actualAvailability = Double.NaN;
  private double requiredEffectiveness = Double.NaN;
  private double actualEffectiveness = Double.NaN;
  private double requiredPfd = Double.NaN;
  private double actualPfd = Double.NaN;
  private final List<DocumentEvidence> evidence = new ArrayList<DocumentEvidence>();

  /**
   * Creates a safety-system demand case.
   *
   * @param demandId stable demand-case identifier
   */
  public SafetySystemDemand(String demandId) {
    this.demandId = normalize(demandId);
  }

  /**
   * Normalizes nullable text values for safe storage and JSON export.
   *
   * @param value text value to normalize
   * @return trimmed text or empty string
   */
  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Gets the demand-case identifier.
   *
   * @return demand-case identifier
   */
  public String getDemandId() {
    return demandId;
  }

  /**
   * Gets the linked barrier identifier.
   *
   * @return barrier identifier, or empty string when not set
   */
  public String getBarrierId() {
    return barrierId;
  }

  /**
   * Sets the linked barrier identifier.
   *
   * @param barrierId barrier identifier
   * @return this demand case
   */
  public SafetySystemDemand setBarrierId(String barrierId) {
    this.barrierId = normalize(barrierId);
    return this;
  }

  /**
   * Gets the linked equipment tag.
   *
   * @return equipment tag, or empty string when not set
   */
  public String getEquipmentTag() {
    return equipmentTag;
  }

  /**
   * Sets the linked equipment tag.
   *
   * @param equipmentTag equipment tag
   * @return this demand case
   */
  public SafetySystemDemand setEquipmentTag(String equipmentTag) {
    this.equipmentTag = normalize(equipmentTag);
    return this;
  }

  /**
   * Gets the scenario description.
   *
   * @return scenario description
   */
  public String getScenario() {
    return scenario;
  }

  /**
   * Sets the scenario description.
   *
   * @param scenario scenario description
   * @return this demand case
   */
  public SafetySystemDemand setScenario(String scenario) {
    this.scenario = normalize(scenario);
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
   * @return this demand case
   */
  public SafetySystemDemand setCategory(SafetySystemCategory category) {
    this.category = category == null ? SafetySystemCategory.UNKNOWN : category;
    return this;
  }

  /**
   * Gets the scenario demand value.
   *
   * @return demand value, or NaN when not set
   */
  public double getDemandValue() {
    return demandValue;
  }

  /**
   * Sets the scenario demand value.
   *
   * @param demandValue demand value in {@link #getDemandUnit()}
   * @return this demand case
   */
  public SafetySystemDemand setDemandValue(double demandValue) {
    this.demandValue = demandValue;
    return this;
  }

  /**
   * Gets the barrier capacity value.
   *
   * @return capacity value, or NaN when not set
   */
  public double getCapacityValue() {
    return capacityValue;
  }

  /**
   * Sets the barrier capacity value.
   *
   * @param capacityValue capacity value in {@link #getDemandUnit()}
   * @return this demand case
   */
  public SafetySystemDemand setCapacityValue(double capacityValue) {
    this.capacityValue = capacityValue;
    return this;
  }

  /**
   * Gets the demand/capacity engineering unit.
   *
   * @return engineering unit
   */
  public String getDemandUnit() {
    return demandUnit;
  }

  /**
   * Sets the demand/capacity engineering unit.
   *
   * @param demandUnit engineering unit
   * @return this demand case
   */
  public SafetySystemDemand setDemandUnit(String demandUnit) {
    this.demandUnit = normalize(demandUnit);
    return this;
  }

  /**
   * Gets the required response time.
   *
   * @return required response time in seconds, or NaN when not set
   */
  public double getRequiredResponseTimeSeconds() {
    return requiredResponseTimeSeconds;
  }

  /**
   * Sets the required response time.
   *
   * @param requiredResponseTimeSeconds response time in seconds
   * @return this demand case
   */
  public SafetySystemDemand setRequiredResponseTimeSeconds(double requiredResponseTimeSeconds) {
    this.requiredResponseTimeSeconds = requiredResponseTimeSeconds;
    return this;
  }

  /**
   * Gets the actual or documented response time.
   *
   * @return actual response time in seconds, or NaN when not set
   */
  public double getActualResponseTimeSeconds() {
    return actualResponseTimeSeconds;
  }

  /**
   * Sets the actual or documented response time.
   *
   * @param actualResponseTimeSeconds response time in seconds
   * @return this demand case
   */
  public SafetySystemDemand setActualResponseTimeSeconds(double actualResponseTimeSeconds) {
    this.actualResponseTimeSeconds = actualResponseTimeSeconds;
    return this;
  }

  /**
   * Gets the required availability.
   *
   * @return required availability from 0 to 1, or NaN when not set
   */
  public double getRequiredAvailability() {
    return requiredAvailability;
  }

  /**
   * Sets the required availability.
   *
   * @param requiredAvailability required availability from 0 to 1
   * @return this demand case
   */
  public SafetySystemDemand setRequiredAvailability(double requiredAvailability) {
    this.requiredAvailability = requiredAvailability;
    return this;
  }

  /**
   * Gets the actual or documented availability.
   *
   * @return actual availability from 0 to 1, or NaN when not set
   */
  public double getActualAvailability() {
    return actualAvailability;
  }

  /**
   * Sets the actual or documented availability.
   *
   * @param actualAvailability actual availability from 0 to 1
   * @return this demand case
   */
  public SafetySystemDemand setActualAvailability(double actualAvailability) {
    this.actualAvailability = actualAvailability;
    return this;
  }

  /**
   * Gets the required effectiveness.
   *
   * @return required effectiveness from 0 to 1, or NaN when not set
   */
  public double getRequiredEffectiveness() {
    return requiredEffectiveness;
  }

  /**
   * Sets the required effectiveness.
   *
   * @param requiredEffectiveness required effectiveness from 0 to 1
   * @return this demand case
   */
  public SafetySystemDemand setRequiredEffectiveness(double requiredEffectiveness) {
    this.requiredEffectiveness = requiredEffectiveness;
    return this;
  }

  /**
   * Gets the actual or documented effectiveness.
   *
   * @return actual effectiveness from 0 to 1, or NaN when not set
   */
  public double getActualEffectiveness() {
    return actualEffectiveness;
  }

  /**
   * Sets the actual or documented effectiveness.
   *
   * @param actualEffectiveness actual effectiveness from 0 to 1
   * @return this demand case
   */
  public SafetySystemDemand setActualEffectiveness(double actualEffectiveness) {
    this.actualEffectiveness = actualEffectiveness;
    return this;
  }

  /**
   * Gets the required probability of failure on demand.
   *
   * @return required PFD, or NaN when not set
   */
  public double getRequiredPfd() {
    return requiredPfd;
  }

  /**
   * Sets the required probability of failure on demand.
   *
   * @param requiredPfd required PFD from 0 to 1
   * @return this demand case
   */
  public SafetySystemDemand setRequiredPfd(double requiredPfd) {
    this.requiredPfd = requiredPfd;
    return this;
  }

  /**
   * Gets the actual or documented probability of failure on demand.
   *
   * @return actual PFD, or NaN when not set
   */
  public double getActualPfd() {
    return actualPfd;
  }

  /**
   * Sets the actual or documented probability of failure on demand.
   *
   * @param actualPfd actual PFD from 0 to 1
   * @return this demand case
   */
  public SafetySystemDemand setActualPfd(double actualPfd) {
    this.actualPfd = actualPfd;
    return this;
  }

  /**
   * Adds traceable evidence for this demand/capacity input.
   *
   * @param evidenceItem evidence item
   * @return this demand case
   */
  public SafetySystemDemand addEvidence(DocumentEvidence evidenceItem) {
    if (evidenceItem != null) {
      evidence.add(evidenceItem);
    }
    return this;
  }

  /**
   * Gets evidence linked to the demand case.
   *
   * @return copy of evidence items
   */
  public List<DocumentEvidence> getEvidence() {
    return new ArrayList<DocumentEvidence>(evidence);
  }

  /**
   * Checks whether this demand case applies to a barrier.
   *
   * @param barrier barrier to test against
   * @return true when the barrier id or equipment tag matches
   */
  public boolean matches(SafetyBarrier barrier) {
    if (barrier == null) {
      return false;
    }
    if (!barrierId.isEmpty() && barrierId.equals(barrier.getId())) {
      return true;
    }
    return !equipmentTag.isEmpty() && barrier.getLinkedEquipmentTags().contains(equipmentTag);
  }

  /**
   * Converts this demand case to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("demandId", demandId);
    map.put("barrierId", barrierId);
    map.put("equipmentTag", equipmentTag);
    map.put("scenario", scenario);
    map.put("category", category.name());
    map.put("demandValue", demandValue);
    map.put("capacityValue", capacityValue);
    map.put("demandUnit", demandUnit);
    map.put("requiredResponseTimeSeconds", requiredResponseTimeSeconds);
    map.put("actualResponseTimeSeconds", actualResponseTimeSeconds);
    map.put("requiredAvailability", requiredAvailability);
    map.put("actualAvailability", actualAvailability);
    map.put("requiredEffectiveness", requiredEffectiveness);
    map.put("actualEffectiveness", actualEffectiveness);
    map.put("requiredPfd", requiredPfd);
    map.put("actualPfd", actualPfd);

    List<Map<String, Object>> evidenceList = new ArrayList<Map<String, Object>>();
    for (DocumentEvidence item : evidence) {
      evidenceList.add(item.toMap());
    }
    map.put("evidence", evidenceList);
    return map;
  }
}
