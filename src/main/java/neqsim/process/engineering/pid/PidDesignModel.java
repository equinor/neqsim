package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Revisionable collection of proposed P&amp;ID elements with duplicate protection and governance metadata. */
public final class PidDesignModel implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final String profileId;
  private final List<PidElement> elements = new ArrayList<PidElement>();
  private final Set<String> ids = new LinkedHashSet<String>();
  private final Set<String> tags = new LinkedHashSet<String>();

  public PidDesignModel(String projectId, String profileId) {
    this.projectId = requireText(projectId, "projectId");
    this.profileId = requireText(profileId, "profileId");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  public PidDesignModel add(PidElement element) {
    if (element == null) {
      throw new IllegalArgumentException("element must not be null");
    }
    if (!ids.add(element.getId())) {
      throw new IllegalArgumentException("duplicate P&ID element id: " + element.getId());
    }
    if (!tags.add(element.getTag())) {
      throw new IllegalArgumentException("duplicate P&ID tag: " + element.getTag());
    }
    elements.add(element);
    return this;
  }

  public List<PidElement> getElementsForEquipment(String equipmentTag) {
    List<PidElement> matches = new ArrayList<PidElement>();
    for (PidElement element : elements) {
      if (element.getEquipmentTag().equals(equipmentTag)) {
        matches.add(element);
      }
    }
    return matches;
  }

  public List<PidElement> getElementsByType(PidElementType type) {
    List<PidElement> matches = new ArrayList<PidElement>();
    for (PidElement element : elements) {
      if (element.getType() == type) {
        matches.add(element);
      }
    }
    return matches;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (PidElement element : elements) {
      values.add(element.toMap());
    }
    map.put("schemaVersion", "neqsim_pid_design_model.v1");
    map.put("projectId", projectId);
    map.put("profileId", profileId);
    map.put("elements", values);
    map.put("qualificationStatus", "REVIEW_REQUIRED");
    map.put("fitnessForConstruction", Boolean.FALSE);
    map.put("governance",
        "Generated P&ID elements are proposals and require discipline review, safety lifecycle evidence and approval");
    return map;
  }

  public List<PidElement> getElements() {
    return Collections.unmodifiableList(elements);
  }

  public String getProjectId() {
    return projectId;
  }
}
