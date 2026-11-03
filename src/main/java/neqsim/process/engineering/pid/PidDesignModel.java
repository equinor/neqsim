package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

  /**
   * Restores the supported P&amp;ID proposal model without recreating or configuring process physics.
   *
   * @param json exported {@code neqsim_pid_design_model.v1} JSON
   * @return duplicate-checked design model with preserved IDs and proposal provenance
   */
  public static PidDesignModel fromJson(String json) {
    if (json == null) {
      throw new IllegalArgumentException("P&ID JSON must not be null");
    }
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    if (!root.has("schemaVersion") || !"neqsim_pid_design_model.v1".equals(root.get("schemaVersion").getAsString())
        || !root.has("elements") || !root.get("elements").isJsonArray()) {
      throw new IllegalArgumentException("Unsupported or incomplete P&ID artifact");
    }
    PidDesignModel model = new PidDesignModel(root.get("projectId").getAsString(), root.get("profileId").getAsString());
    for (JsonElement item : root.getAsJsonArray("elements")) {
      JsonObject row = item.getAsJsonObject();
      PidElement element = new PidElement(row.get("id").getAsString(), row.get("tag").getAsString(),
          PidElementType.valueOf(row.get("type").getAsString()));
      if (!text(row, "equipmentTag").isEmpty()) {
        element.equipment(text(row, "equipmentTag"));
      }
      if (!text(row, "lineTag").isEmpty()) {
        element.line(text(row, "lineTag"));
      }
      if (!text(row, "service").isEmpty()) {
        element.service(text(row, "service"));
      }
      if (!text(row, "description").isEmpty()) {
        element.description(text(row, "description"));
      }
      if (!text(row, "ruleId").isEmpty() && !text(row, "rationale").isEmpty()) {
        element.provenance(text(row, "ruleId"), text(row, "rationale"));
      }
      element.status(PidProposalStatus.valueOf(row.get("status").getAsString()));
      for (JsonElement requirement : row.getAsJsonArray("requirementIds")) {
        element.requirement(requirement.getAsString());
      }
      for (JsonElement standard : row.getAsJsonArray("standardReferences")) {
        element.standard(standard.getAsString());
      }
      for (JsonElement connected : row.getAsJsonArray("connectedElementIds")) {
        element.connect(connected.getAsString());
      }
      for (Map.Entry<String, JsonElement> attribute : row.getAsJsonObject("attributes").entrySet()) {
        element.attribute(attribute.getKey(), jsonValue(attribute.getValue()));
      }
      model.add(element);
    }
    return model;
  }

  /**
   * Reads an optional string field.
   *
   * @param object source JSON object
   * @param field field name
   * @return field value or empty string
   */
  private static String text(JsonObject object, String field) {
    return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
  }

  /**
   * Preserves JSON number spellings and nested attribute values for fingerprint-stable intake.
   *
   * @param value serialized attribute
   * @return Java JSON-compatible value
   */
  private static Object jsonValue(JsonElement value) {
    if (value.isJsonNull()) {
      return null;
    }
    if (value.isJsonObject()) {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
        result.put(entry.getKey(), jsonValue(entry.getValue()));
      }
      return result;
    }
    if (value.isJsonArray()) {
      List<Object> result = new ArrayList<Object>();
      for (JsonElement child : value.getAsJsonArray()) {
        result.add(jsonValue(child));
      }
      return result;
    }
    if (value.getAsJsonPrimitive().isBoolean()) {
      return Boolean.valueOf(value.getAsBoolean());
    }
    return value.getAsJsonPrimitive().isNumber() ? value.getAsBigDecimal() : value.getAsString();
  }

  public List<PidElement> getElements() {
    return Collections.unmodifiableList(elements);
  }

  public String getProjectId() {
    return projectId;
  }

  public String getProfileId() {
    return profileId;
  }
}
