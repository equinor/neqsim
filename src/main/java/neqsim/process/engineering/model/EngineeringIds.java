package neqsim.process.engineering.model;

/** Stable canonical identifiers shared by graph, envelope and deliverable generation. */
public final class EngineeringIds {
  private EngineeringIds() {
  }

  public static String nodeId(EngineeringNode.Kind kind, String externalKey) {
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    return kind.name().toLowerCase() + ":" + canonical(externalKey);
  }

  public static String edgeId(EngineeringEdge.Kind kind, String sourceId, String targetId, String role) {
    return "edge:" + kind.name().toLowerCase() + ":" + canonical(sourceId) + ":" + canonical(targetId) + ":"
        + canonical(role == null || role.trim().isEmpty() ? "default" : role);
  }

  public static String canonical(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("identifier value must not be blank");
    }
    return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}
