package neqsim.process.modelpackage;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Controlled dependency on another model revision or authoritative source. */
public final class ModelDependency implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String modelId;
  private final String revision;
  private final String relationship;
  private final String sourceSystem;
  private final boolean required;

  public ModelDependency(String modelId, String revision, String relationship, String sourceSystem, boolean required) {
    this.modelId = requireText(modelId, "modelId");
    this.revision = requireText(revision, "revision");
    this.relationship = requireText(relationship, "relationship");
    this.sourceSystem = requireText(sourceSystem, "sourceSystem");
    this.required = required;
  }

  public String getModelId() {
    return modelId;
  }

  public String getRevision() {
    return revision;
  }

  public String getRelationship() {
    return relationship;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public boolean isRequired() {
    return required;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("modelId", modelId);
    result.put("revision", revision);
    result.put("relationship", relationship);
    result.put("sourceSystem", sourceSystem);
    result.put("required", Boolean.valueOf(required));
    return result;
  }

  static ModelDependency fromMap(Map<String, Object> value) {
    return new ModelDependency(String.valueOf(value.get("modelId")), String.valueOf(value.get("revision")),
        String.valueOf(value.get("relationship")), String.valueOf(value.get("sourceSystem")),
        Boolean.TRUE.equals(value.get("required")));
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
