package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One traceable proposed or approved object in a generated P&amp;ID design model. */
public final class PidElement implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final String tag;
  private final PidElementType type;
  private String equipmentTag = "";
  private String lineTag = "";
  private String service = "";
  private String description = "";
  private String ruleId = "";
  private String rationale = "";
  private PidProposalStatus status = PidProposalStatus.REVIEW_REQUIRED;
  private final List<String> requirementIds = new ArrayList<String>();
  private final List<String> standardReferences = new ArrayList<String>();
  private final List<String> connectedElementIds = new ArrayList<String>();
  private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

  public PidElement(String id, String tag, PidElementType type) {
    this.id = requireText(id, "id");
    this.tag = requireText(tag, "tag");
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    this.type = type;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  public PidElement equipment(String value) {
    equipmentTag = requireText(value, "equipmentTag");
    return this;
  }

  public PidElement line(String value) {
    lineTag = requireText(value, "lineTag");
    return this;
  }

  public PidElement service(String value) {
    service = requireText(value, "service");
    return this;
  }

  public PidElement description(String value) {
    description = requireText(value, "description");
    return this;
  }

  public PidElement provenance(String sourceRuleId, String engineeringRationale) {
    ruleId = requireText(sourceRuleId, "ruleId");
    rationale = requireText(engineeringRationale, "rationale");
    return this;
  }

  public PidElement status(PidProposalStatus value) {
    if (value == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    status = value;
    return this;
  }

  public PidElement requirement(String value) {
    addUnique(requirementIds, requireText(value, "requirementId"));
    return this;
  }

  public PidElement standard(String value) {
    addUnique(standardReferences, requireText(value, "standardReference"));
    return this;
  }

  public PidElement connect(String elementId) {
    addUnique(connectedElementIds, requireText(elementId, "connectedElementId"));
    return this;
  }

  public PidElement attribute(String name, Object value) {
    attributes.put(requireText(name, "attribute name"), value);
    return this;
  }

  private static void addUnique(List<String> values, String value) {
    if (!values.contains(value)) {
      values.add(value);
    }
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    map.put("tag", tag);
    map.put("type", type.name());
    map.put("equipmentTag", equipmentTag);
    map.put("lineTag", lineTag);
    map.put("service", service);
    map.put("description", description);
    map.put("ruleId", ruleId);
    map.put("rationale", rationale);
    map.put("status", status.name());
    map.put("requirementIds", new ArrayList<String>(requirementIds));
    map.put("standardReferences", new ArrayList<String>(standardReferences));
    map.put("connectedElementIds", new ArrayList<String>(connectedElementIds));
    map.put("attributes", new LinkedHashMap<String, Object>(attributes));
    return map;
  }

  public String getId() {
    return id;
  }

  public String getTag() {
    return tag;
  }

  public PidElementType getType() {
    return type;
  }

  public String getEquipmentTag() {
    return equipmentTag;
  }

  public String getLineTag() {
    return lineTag;
  }

  public String getRuleId() {
    return ruleId;
  }

  public PidProposalStatus getStatus() {
    return status;
  }

  public List<String> getRequirementIds() {
    return Collections.unmodifiableList(requirementIds);
  }

  public List<String> getStandardReferences() {
    return Collections.unmodifiableList(standardReferences);
  }

  public List<String> getConnectedElementIds() {
    return Collections.unmodifiableList(connectedElementIds);
  }

  public Map<String, Object> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }
}
