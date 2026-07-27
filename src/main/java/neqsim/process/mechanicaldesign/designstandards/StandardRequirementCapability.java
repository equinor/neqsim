package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;

/** One executable or review capability mapped to a standard requirement pack. */
public final class StandardRequirementCapability implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Nature of the mapped NeqSim capability. */
  public enum Kind {
    /** Deterministic numerical screening with structured output. */
    CALCULATION_SCREENING,
    /** Structured review or lifecycle evidence workflow. */
    REVIEW_WORKFLOW
  }

  private final String id;
  private final Kind kind;
  private final String implementationClassName;
  private final String boundary;

  StandardRequirementCapability(String id, Kind kind, String implementationClassName, String boundary) {
    this.id = requireText(id, "id");
    if (kind == null) {
      throw new IllegalArgumentException("kind cannot be null");
    }
    this.kind = kind;
    this.implementationClassName = requireText(implementationClassName, "implementationClassName");
    this.boundary = requireText(boundary, "boundary");
  }

  /** @return stable capability identifier */
  public String getId() {
    return id;
  }

  /** @return capability kind */
  public Kind getKind() {
    return kind;
  }

  /** @return fully qualified implementing class */
  public String getImplementationClassName() {
    return implementationClassName;
  }

  /** @return concise scope boundary */
  public String getBoundary() {
    return boundary;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " cannot be null or blank");
    }
    return value.trim();
  }
}
