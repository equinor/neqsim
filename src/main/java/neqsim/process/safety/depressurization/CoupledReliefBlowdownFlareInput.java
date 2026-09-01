package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;

/** Controlled input joining steady relief scenarios with a transient blowdown and flare model. */
public final class CoupledReliefBlowdownFlareInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One protected-item relief study and its simultaneous-load group. */
  public static final class ReliefStudyEntry implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final OverpressureProtectionStudy study;
    private final String concurrencyGroup;

    private ReliefStudyEntry(OverpressureProtectionStudy study, String concurrencyGroup) {
      if (study == null) {
        throw new IllegalArgumentException("study must not be null");
      }
      this.study = study;
      this.concurrencyGroup = requireText(concurrencyGroup, "concurrencyGroup");
    }

    public OverpressureProtectionStudy getStudy() {
      return study;
    }

    public String getConcurrencyGroup() {
      return concurrencyGroup;
    }
  }

  private final String studyId;
  private final List<ReliefStudyEntry> reliefStudies;
  private final DynamicBlowdownFlareStudyDataSource dynamicStudy;
  private final List<String> evidenceReferences;
  private final boolean scenarioSelectionReviewed;

  private CoupledReliefBlowdownFlareInput(Builder builder) {
    studyId = requireText(builder.studyId, "studyId");
    reliefStudies = Collections.unmodifiableList(new ArrayList<ReliefStudyEntry>(builder.reliefStudies));
    dynamicStudy = builder.dynamicStudy;
    evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
    scenarioSelectionReviewed = builder.scenarioSelectionReviewed;
  }

  public static Builder builder(String studyId) {
    return new Builder(studyId);
  }

  public String getStudyId() {
    return studyId;
  }

  public List<ReliefStudyEntry> getReliefStudies() {
    return reliefStudies;
  }

  public DynamicBlowdownFlareStudyDataSource getDynamicStudy() {
    return dynamicStudy;
  }

  public List<String> getEvidenceReferences() {
    return evidenceReferences;
  }

  public boolean isScenarioSelectionReviewed() {
    return scenarioSelectionReviewed;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("studyId", studyId);
    List<Map<String, Object>> studies = new ArrayList<Map<String, Object>>();
    for (ReliefStudyEntry entry : reliefStudies) {
      Map<String, Object> study = new LinkedHashMap<String, Object>();
      study.put("protectedItem", entry.getStudy().getItem().getName());
      study.put("concurrencyGroup", entry.getConcurrencyGroup());
      study.put("scenarioCount", Integer.valueOf(entry.getStudy().getScenarios().size()));
      studies.add(study);
    }
    result.put("reliefStudies", studies);
    result.put("dynamicStudyId", dynamicStudy == null ? "" : dynamicStudy.getStudyId());
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("scenarioSelectionReviewed", Boolean.valueOf(scenarioSelectionReviewed));
    return result;
  }

  /** Builder for a coupled study input. */
  public static final class Builder {
    private final String studyId;
    private final List<ReliefStudyEntry> reliefStudies = new ArrayList<ReliefStudyEntry>();
    private DynamicBlowdownFlareStudyDataSource dynamicStudy;
    private final List<String> evidenceReferences = new ArrayList<String>();
    private boolean scenarioSelectionReviewed;

    private Builder(String studyId) {
      this.studyId = studyId;
    }

    public Builder addReliefStudy(OverpressureProtectionStudy study, String concurrencyGroup) {
      reliefStudies.add(new ReliefStudyEntry(study, concurrencyGroup));
      return this;
    }

    public Builder dynamicStudy(DynamicBlowdownFlareStudyDataSource value) {
      dynamicStudy = value;
      return this;
    }

    public Builder addEvidenceReference(String value) {
      String normalized = value == null ? "" : value.trim();
      if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
        evidenceReferences.add(normalized);
      }
      return this;
    }

    public Builder scenarioSelectionReviewed(boolean value) {
      scenarioSelectionReviewed = value;
      return this;
    }

    public CoupledReliefBlowdownFlareInput build() {
      return new CoupledReliefBlowdownFlareInput(this);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
