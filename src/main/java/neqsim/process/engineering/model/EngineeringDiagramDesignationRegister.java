package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable reviewed project-designation input for an engineering diagram document set.
 *
 * <p>
 * Designations supplement canonical source labels without mutating the source {@link EngineeringGraph}. A reviewed
 * designation is evidence of project review, not accountable approval for design or construction.
 * </p>
 */
public final class EngineeringDiagramDesignationRegister implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Supported controlled designation types. */
  public enum Kind {
    /** Reviewed equipment tag assigned to an equipment semantic object. */
    EQUIPMENT_TAG,
    /** Reviewed stream or line number assigned to a material-connection semantic object. */
    STREAM_NUMBER
  }

  /** Review state carried by the current designation register. */
  public enum ReviewState {
    /** Project review evidence has been recorded; accountable approval is not implied. */
    REVIEWED
  }

  /** Immutable reviewed designation with its provenance and revision evidence. */
  public static final class Designation implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String semanticObjectId;
    private final Kind kind;
    private final String value;
    private final String sourceReference;
    private final ReviewState reviewState;
    private final String reviewedBy;
    private final String reviewReference;
    private final String recordedAt;
    private final String revision;

    /**
     * Creates reviewed project designation evidence.
     *
     * @param semanticObjectId stable target semantic-object identity
     * @param kind designation type
     * @param value reviewed designation value
     * @param sourceReference project register or source-document reference
     * @param reviewState recorded review state
     * @param reviewedBy reviewer identity or accountable role
     * @param reviewReference stable review-record reference
     * @param recordedAt explicit project timestamp, normally ISO 8601
     * @param revision project designation revision
     */
    public Designation(String semanticObjectId, Kind kind, String value, String sourceReference,
        ReviewState reviewState, String reviewedBy, String reviewReference, String recordedAt, String revision) {
      this.semanticObjectId = requireText(semanticObjectId, "semanticObjectId");
      if (kind == null) {
        throw new IllegalArgumentException("kind must not be null");
      }
      this.kind = kind;
      this.value = requireText(value, "value");
      this.sourceReference = requireText(sourceReference, "sourceReference");
      if (reviewState == null) {
        throw new IllegalArgumentException("reviewState must not be null");
      }
      this.reviewState = reviewState;
      this.reviewedBy = requireText(reviewedBy, "reviewedBy");
      this.reviewReference = requireText(reviewReference, "reviewReference");
      this.recordedAt = requireText(recordedAt, "recordedAt");
      this.revision = requireText(revision, "revision");
    }

    public String getSemanticObjectId() {
      return semanticObjectId;
    }

    public Kind getKind() {
      return kind;
    }

    public String getValue() {
      return value;
    }

    public String getSourceReference() {
      return sourceReference;
    }

    public ReviewState getReviewState() {
      return reviewState;
    }

    public String getReviewedBy() {
      return reviewedBy;
    }

    public String getReviewReference() {
      return reviewReference;
    }

    public String getRecordedAt() {
      return recordedAt;
    }

    public String getRevision() {
      return revision;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("semanticObjectId", semanticObjectId);
      result.put("kind", kind.name());
      result.put("value", value);
      result.put("sourceReference", sourceReference);
      result.put("reviewState", reviewState.name());
      result.put("reviewedBy", reviewedBy);
      result.put("reviewReference", reviewReference);
      result.put("recordedAt", recordedAt);
      result.put("revision", revision);
      return result;
    }
  }

  private final List<Designation> designations;

  /** Creates an empty reviewed-designation register. */
  public EngineeringDiagramDesignationRegister() {
    this(Collections.<Designation>emptyList());
  }

  /**
   * Creates a deterministic immutable register.
   *
   * @param values reviewed designations
   */
  public EngineeringDiagramDesignationRegister(List<Designation> values) {
    if (values == null) {
      throw new IllegalArgumentException("values must not be null");
    }
    for (Designation designation : values) {
      if (designation == null) {
        throw new IllegalArgumentException("designation must not be null");
      }
    }
    List<Designation> sorted = new ArrayList<Designation>(values);
    Collections.sort(sorted, new Comparator<Designation>() {
      @Override
      public int compare(Designation left, Designation right) {
        int target = left.getSemanticObjectId().compareTo(right.getSemanticObjectId());
        return target != 0 ? target : left.getKind().name().compareTo(right.getKind().name());
      }
    });
    String previousKey = null;
    for (Designation designation : sorted) {
      String key = designation.getSemanticObjectId() + "\n" + designation.getKind().name();
      if (key.equals(previousKey)) {
        throw new IllegalArgumentException(
            "Duplicate designation kind for semantic object " + designation.getSemanticObjectId());
      }
      previousKey = key;
    }
    this.designations = Collections.unmodifiableList(sorted);
  }

  /**
   * Returns immutable, target- and kind-sorted reviewed designations.
   *
   * @return reviewed designations
   */
  public List<Designation> getDesignations() {
    return designations;
  }

  /**
   * Returns a new register containing one additional reviewed designation.
   *
   * @param designation reviewed designation
   * @return new immutable register
   */
  public EngineeringDiagramDesignationRegister withDesignation(Designation designation) {
    if (designation == null) {
      throw new IllegalArgumentException("designation must not be null");
    }
    List<Designation> result = new ArrayList<Designation>(designations);
    result.add(designation);
    return new EngineeringDiagramDesignationRegister(result);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
