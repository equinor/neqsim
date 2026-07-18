package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** A directed semantic relationship between two canonical engineering nodes. */
public final class EngineeringEdge implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Kind {
    CONTAINS, CONNECTS_TO, PROTECTS, APPLIES_TO, DEPENDS_ON, GENERATED_FROM, GOVERNS, REFERENCES, HAS_PORT,
    PROCESS_FLOW, SIGNAL_FLOW, ENERGY_FLOW, PART_OF_LINE, MEASURES, APPROVES, SUPERSEDES, REPRESENTS_SAME_AS,
    AUTHORED_IN, SYNCHRONIZED_FROM, CALCULATED_BY, VALIDATED_AGAINST, CONSUMED_BY_MODEL, INVALIDATES,
    REQUIRES_REAPPROVAL
  }

  private final String id;
  private final String sourceId;
  private final String targetId;
  private final Kind kind;
  private final String role;

  public EngineeringEdge(String id, String sourceId, String targetId, Kind kind, String role) {
    this.id = requireText(id, "id");
    this.sourceId = requireText(sourceId, "sourceId");
    this.targetId = requireText(targetId, "targetId");
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    this.kind = kind;
    this.role = role == null ? "" : role.trim();
  }

  public String getId() {
    return id;
  }

  public String getSourceId() {
    return sourceId;
  }

  public String getTargetId() {
    return targetId;
  }

  public Kind getKind() {
    return kind;
  }

  public String getRole() {
    return role;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("sourceId", sourceId);
    result.put("targetId", targetId);
    result.put("kind", kind.name());
    result.put("role", role);
    return result;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
