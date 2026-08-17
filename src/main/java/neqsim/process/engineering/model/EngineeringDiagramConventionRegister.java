package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import neqsim.process.engineering.model.EngineeringNode.Kind;

/**
 * Immutable project symbol-convention input for native engineering-diagram rendering.
 *
 * <p>
 * The register maps canonical semantic node kinds to small renderer-native vector shapes and explicit colours. It does
 * not define or claim ISO 10628, ISO 14617, ISA, company-standard, or project-standard conformance. A reviewed entry is
 * evidence that a project convention was reviewed, not approval of the generated drawing for design or construction.
 * </p>
 */
public final class EngineeringDiagramConventionRegister implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Generic vector shapes supported by the native renderer. */
  public enum SymbolShape {
    /** Legacy rectangular symbol. */
    RECTANGLE,
    /** Four-sided diamond symbol. */
    DIAMOND,
    /** Six-sided hexagonal symbol. */
    HEXAGON
  }

  /** Evidence state attached to one project convention. */
  public enum EvidenceState {
    /** Proposed project convention requiring review. */
    PROPOSED,
    /** Project review evidence has been recorded; accountable drawing approval is not implied. */
    REVIEWED
  }

  /** Immutable symbol convention with explicit project provenance. */
  public static final class SymbolConvention implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Kind nodeKind;
    private final SymbolShape shape;
    private final String strokeColor;
    private final String fillColor;
    private final String sourceReference;
    private final EvidenceState evidenceState;
    private final String reviewedBy;
    private final String reviewReference;
    private final String recordedAt;
    private final String revision;

    /**
     * Creates one project symbol convention.
     *
     * @param nodeKind canonical semantic node kind
     * @param shape renderer-native generic vector shape
     * @param strokeColor stroke colour in {@code #RRGGBB} form
     * @param fillColor fill colour in {@code #RRGGBB} form
     * @param sourceReference project convention or source-document reference
     * @param evidenceState proposal/review state
     * @param reviewedBy reviewer identity or role; required for reviewed conventions
     * @param reviewReference review-record reference; required for reviewed conventions
     * @param recordedAt explicit project timestamp, normally ISO 8601
     * @param revision project convention revision
     */
    public SymbolConvention(Kind nodeKind, SymbolShape shape, String strokeColor, String fillColor,
        String sourceReference, EvidenceState evidenceState, String reviewedBy, String reviewReference,
        String recordedAt, String revision) {
      if (nodeKind == null) {
        throw new IllegalArgumentException("nodeKind must not be null");
      }
      if (shape == null) {
        throw new IllegalArgumentException("shape must not be null");
      }
      this.nodeKind = nodeKind;
      this.shape = shape;
      this.strokeColor = requireColor(strokeColor, "strokeColor");
      this.fillColor = requireColor(fillColor, "fillColor");
      this.sourceReference = requireText(sourceReference, "sourceReference");
      if (evidenceState == null) {
        throw new IllegalArgumentException("evidenceState must not be null");
      }
      this.evidenceState = evidenceState;
      this.reviewedBy = optionalText(reviewedBy);
      this.reviewReference = optionalText(reviewReference);
      if (evidenceState == EvidenceState.REVIEWED && (this.reviewedBy.isEmpty() || this.reviewReference.isEmpty())) {
        throw new IllegalArgumentException("reviewed convention requires reviewedBy and reviewReference");
      }
      this.recordedAt = requireText(recordedAt, "recordedAt");
      this.revision = requireText(revision, "revision");
    }

    /** @return canonical semantic node kind */
    public Kind getNodeKind() {
      return nodeKind;
    }

    /** @return renderer-native generic vector shape */
    public SymbolShape getShape() {
      return shape;
    }

    /** @return stroke colour in {@code #RRGGBB} form */
    public String getStrokeColor() {
      return strokeColor;
    }

    /** @return fill colour in {@code #RRGGBB} form */
    public String getFillColor() {
      return fillColor;
    }

    /** @return project convention or source-document reference */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return proposal/review state */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    /** @return reviewer identity or role, or empty text for a proposal */
    public String getReviewedBy() {
      return reviewedBy;
    }

    /** @return review-record reference, or empty text for a proposal */
    public String getReviewReference() {
      return reviewReference;
    }

    /** @return explicit project timestamp */
    public String getRecordedAt() {
      return recordedAt;
    }

    /** @return project convention revision */
    public String getRevision() {
      return revision;
    }
  }

  private final List<SymbolConvention> conventions;

  /** Creates an empty register that preserves the native renderer's legacy rectangle output. */
  public EngineeringDiagramConventionRegister() {
    this(Collections.<SymbolConvention>emptyList());
  }

  /**
   * Creates a deterministic immutable register.
   *
   * @param values project symbol conventions
   */
  public EngineeringDiagramConventionRegister(List<SymbolConvention> values) {
    if (values == null) {
      throw new IllegalArgumentException("values must not be null");
    }
    List<SymbolConvention> sorted = new ArrayList<SymbolConvention>(values);
    for (SymbolConvention convention : sorted) {
      if (convention == null) {
        throw new IllegalArgumentException("convention must not be null");
      }
    }
    Collections.sort(sorted, new Comparator<SymbolConvention>() {
      @Override
      public int compare(SymbolConvention left, SymbolConvention right) {
        return left.getNodeKind().name().compareTo(right.getNodeKind().name());
      }
    });
    Kind previousKind = null;
    for (SymbolConvention convention : sorted) {
      if (convention.getNodeKind() == previousKind) {
        throw new IllegalArgumentException("Duplicate symbol convention for " + convention.getNodeKind());
      }
      previousKind = convention.getNodeKind();
    }
    this.conventions = Collections.unmodifiableList(sorted);
  }

  /** @return immutable node-kind-sorted project symbol conventions */
  public List<SymbolConvention> getConventions() {
    return conventions;
  }

  /** @return whether this register leaves the legacy renderer defaults unchanged */
  public boolean isEmpty() {
    return conventions.isEmpty();
  }

  /**
   * Finds the configured symbol for a canonical node kind.
   *
   * @param nodeKind canonical semantic node kind
   * @return configured symbol convention, or {@code null} when the renderer must use its explicit fallback
   */
  public SymbolConvention getSymbolConvention(Kind nodeKind) {
    if (nodeKind == null) {
      return null;
    }
    for (SymbolConvention convention : conventions) {
      if (convention.getNodeKind() == nodeKind) {
        return convention;
      }
    }
    return null;
  }

  /**
   * Returns a new register containing one additional convention.
   *
   * @param convention project symbol convention
   * @return new immutable register
   */
  public EngineeringDiagramConventionRegister withConvention(SymbolConvention convention) {
    if (convention == null) {
      throw new IllegalArgumentException("convention must not be null");
    }
    List<SymbolConvention> result = new ArrayList<SymbolConvention>(conventions);
    result.add(convention);
    return new EngineeringDiagramConventionRegister(result);
  }

  private static String requireText(String value, String name) {
    String result = optionalText(value);
    if (result.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return result;
  }

  private static String requireColor(String value, String name) {
    String result = requireText(value, name);
    if (!result.matches("#[0-9A-Fa-f]{6}")) {
      throw new IllegalArgumentException(name + " must use #RRGGBB form");
    }
    return result.toLowerCase(java.util.Locale.ROOT);
  }

  private static String optionalText(String value) {
    return value == null ? "" : value.trim();
  }
}
