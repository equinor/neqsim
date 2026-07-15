package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import neqsim.process.safety.overpressure.ReliefCause;

/** Controlled list of credible API 521 overpressure causes required for one protected item. */
public final class ReliefScenarioBasis implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final Set<ReliefCause> requiredCauses = EnumSet.noneOf(ReliefCause.class);
  private final List<String> evidenceReferences = new ArrayList<String>();
  private String hazardReviewReference = "";

  public ReliefScenarioBasis(String equipmentTag) {
    if (equipmentTag == null || equipmentTag.trim().isEmpty()) {
      throw new IllegalArgumentException("equipmentTag must not be blank");
    }
    this.equipmentTag = equipmentTag.trim();
  }

  public ReliefScenarioBasis require(ReliefCause cause) {
    if (cause == null) {
      throw new IllegalArgumentException("cause must not be null");
    }
    requiredCauses.add(cause);
    return this;
  }

  public ReliefScenarioBasis setHazardReviewReference(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("hazardReviewReference must not be blank");
    }
    hazardReviewReference = value.trim();
    return this;
  }

  public ReliefScenarioBasis addEvidenceReference(String value) {
    if (value != null && !value.trim().isEmpty() && !evidenceReferences.contains(value.trim())) {
      evidenceReferences.add(value.trim());
    }
    return this;
  }

  public String getEquipmentTag() {
    return equipmentTag;
  }

  public Set<ReliefCause> getRequiredCauses() {
    return Collections.unmodifiableSet(requiredCauses);
  }

  public List<String> getEvidenceReferences() {
    return Collections.unmodifiableList(evidenceReferences);
  }

  public String getHazardReviewReference() {
    return hazardReviewReference;
  }

  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    if (requiredCauses.isEmpty()) {
      missing.add("credibleCauses");
    }
    if (hazardReviewReference.isEmpty()) {
      missing.add("hazardReviewReference");
    }
    if (evidenceReferences.isEmpty()) {
      missing.add("scenarioEvidenceReference");
    }
    return missing;
  }
}
