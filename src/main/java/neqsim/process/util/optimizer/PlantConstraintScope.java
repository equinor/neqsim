package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.Locale;

/**
 * Immutable address of the plant subject governed by a registered optimization constraint.
 *
 * <p>
 * Scope identity is independent of object identity and process execution state. Names are escaped before the stable
 * identifier is assembled, so ordinary plant names containing separators cannot collide. The class intentionally
 * retains model, area, and subject names separately for straightforward Java and JPype reporting.
 * </p>
 */
public final class PlantConstraintScope implements Serializable, Comparable<PlantConstraintScope> {
  private static final long serialVersionUID = 1L;

  /** Supported plant-wide constraint scopes. */
  public enum Type {
    /** One equipment item in one process area. */
    EQUIPMENT,
    /** One named process stream or boundary stream. */
    STREAM,
    /** One process area. */
    AREA,
    /** The complete process model. */
    MODEL,
    /** A shared utility, environmental, or handling resource. */
    SHARED_RESOURCE,
    /** A coupled equipment group such as a common-shaft compressor train. */
    COUPLED_GROUP
  }

  private final Type type;
  private final String modelName;
  private final String areaName;
  private final String subjectName;
  private final String stableId;

  private PlantConstraintScope(Type type, String modelName, String areaName, String subjectName) {
    if (type == null) {
      throw new IllegalArgumentException("Plant constraint scope type is required");
    }
    this.type = type;
    this.modelName = requireText(modelName, "Model name");
    this.areaName = safeText(areaName);
    this.subjectName = safeText(subjectName);
    validateShape();
    this.stableId = buildStableId();
  }

  /** Creates an equipment-local scope with plant-stable identity. */
  public static PlantConstraintScope equipment(String modelName, String areaName, String equipmentName) {
    return new PlantConstraintScope(Type.EQUIPMENT, modelName, requireText(areaName, "Area name"),
        requireText(equipmentName, "Equipment name"));
  }

  /** Creates a stream or process-boundary scope. */
  public static PlantConstraintScope stream(String modelName, String areaName, String streamName) {
    return new PlantConstraintScope(Type.STREAM, modelName, requireText(areaName, "Area name"),
        requireText(streamName, "Stream name"));
  }

  /** Creates a process-area scope. */
  public static PlantConstraintScope area(String modelName, String areaName) {
    return new PlantConstraintScope(Type.AREA, modelName, requireText(areaName, "Area name"), "");
  }

  /** Creates a complete-model scope. */
  public static PlantConstraintScope model(String modelName) {
    return new PlantConstraintScope(Type.MODEL, modelName, "", "");
  }

  /** Creates a shared-resource scope. */
  public static PlantConstraintScope sharedResource(String modelName, String resourceName) {
    return new PlantConstraintScope(Type.SHARED_RESOURCE, modelName, "",
        requireText(resourceName, "Shared resource name"));
  }

  /** Creates a coupled-equipment group scope. */
  public static PlantConstraintScope coupledGroup(String modelName, String areaName, String groupName) {
    return new PlantConstraintScope(Type.COUPLED_GROUP, modelName, safeText(areaName),
        requireText(groupName, "Coupled group name"));
  }

  private void validateShape() {
    if ((type == Type.EQUIPMENT || type == Type.STREAM) && (areaName.isEmpty() || subjectName.isEmpty())) {
      throw new IllegalArgumentException(type + " scope requires area and subject names");
    }
    if (type == Type.AREA && areaName.isEmpty()) {
      throw new IllegalArgumentException("AREA scope requires an area name");
    }
    if ((type == Type.SHARED_RESOURCE || type == Type.COUPLED_GROUP) && subjectName.isEmpty()) {
      throw new IllegalArgumentException(type + " scope requires a subject name");
    }
  }

  private String buildStableId() {
    StringBuilder id = new StringBuilder(type.name().toLowerCase(Locale.ROOT));
    id.append(":").append(escape(modelName));
    if (!areaName.isEmpty()) {
      id.append("/").append(escape(areaName));
    }
    if (!subjectName.isEmpty()) {
      id.append("/").append(escape(subjectName));
    }
    return id.toString();
  }

  static String escape(String value) {
    return value.replace("%", "%25").replace("/", "%2F").replace("#", "%23").replace(":", "%3A").replace("|", "%7C")
        .replace("\r", "%0D").replace("\n", "%0A");
  }

  static String requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value.trim();
  }

  static String safeText(String value) {
    return value == null ? "" : value.trim();
  }

  /** @return scope type */
  public Type getType() {
    return type;
  }

  /** @return stable model name */
  public String getModelName() {
    return modelName;
  }

  /** @return process area name, or empty when not applicable */
  public String getAreaName() {
    return areaName;
  }

  /** @return equipment, stream, resource, or group name, or empty */
  public String getSubjectName() {
    return subjectName;
  }

  /** @return escaped stable scope identity */
  public String getStableId() {
    return stableId;
  }

  @Override
  public int compareTo(PlantConstraintScope other) {
    return stableId.compareTo(other.stableId);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof PlantConstraintScope && stableId.equals(((PlantConstraintScope) object).stableId);
  }

  @Override
  public int hashCode() {
    return stableId.hashCode();
  }

  @Override
  public String toString() {
    return stableId;
  }
}
