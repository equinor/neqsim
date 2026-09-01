package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable project-controlled sheet and layout overrides for an engineering diagram document set.
 *
 * <p>
 * The register supplements automatic sheet generation without changing canonical semantic-object identities. Every
 * override carries an explicit coordinate unit and source evidence. A protected route prevents an automatic renderer
 * from replacing reviewed or proposed waypoints, but it does not constitute engineering approval.
 * </p>
 */
public final class EngineeringDiagramLayoutRegister implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Coordinate units supported by controlled layout records. */
  public enum CoordinateUnit {
    /** Drawing-paper millimetres. */
    MILLIMETRE
  }

  /** Evidence state for a manual layout decision. */
  public enum EvidenceState {
    /** Proposed manual refinement requiring project review. */
    PROPOSED,
    /** Project review evidence has been recorded; accountable approval is not implied. */
    REVIEWED
  }

  /** Immutable provenance shared by one layout decision. */
  public abstract static class LayoutEvidence implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String sourceReference;
    private final EvidenceState evidenceState;
    private final String recordedBy;
    private final String recordedAt;
    private final String revision;

    LayoutEvidence(String sourceReference, EvidenceState evidenceState, String recordedBy, String recordedAt,
        String revision) {
      this.sourceReference = requireText(sourceReference, "sourceReference");
      if (evidenceState == null) {
        throw new IllegalArgumentException("evidenceState must not be null");
      }
      this.evidenceState = evidenceState;
      this.recordedBy = requireText(recordedBy, "recordedBy");
      this.recordedAt = requireText(recordedAt, "recordedAt");
      this.revision = requireText(revision, "revision");
    }

    public String getSourceReference() {
      return sourceReference;
    }

    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    public String getRecordedBy() {
      return recordedBy;
    }

    public String getRecordedAt() {
      return recordedAt;
    }

    public String getRevision() {
      return revision;
    }

    void addEvidence(Map<String, Object> result) {
      result.put("sourceReference", sourceReference);
      result.put("evidenceState", evidenceState.name());
      result.put("recordedBy", recordedBy);
      result.put("recordedAt", recordedAt);
      result.put("revision", revision);
    }
  }

  /** Immutable additional controlled sheet definition. */
  public static final class SheetDefinition extends LayoutEvidence {
    private static final long serialVersionUID = 1000L;
    private final String sheetKey;
    private final String number;
    private final String title;

    public SheetDefinition(String sheetKey, String number, String title, String sourceReference,
        EvidenceState evidenceState, String recordedBy, String recordedAt, String revision) {
      super(sourceReference, evidenceState, recordedBy, recordedAt, revision);
      this.sheetKey = requireText(sheetKey, "sheetKey");
      this.number = requireText(number, "number");
      this.title = requireText(title, "title");
    }

    public String getSheetKey() {
      return sheetKey;
    }

    public String getNumber() {
      return number;
    }

    public String getTitle() {
      return title;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("sheetKey", sheetKey);
      result.put("number", number);
      result.put("title", title);
      addEvidence(result);
      return result;
    }
  }

  /** Immutable manual assignment of one semantic object to a controlled sheet. */
  public static final class SheetAssignment extends LayoutEvidence {
    private static final long serialVersionUID = 1000L;
    private final String semanticObjectId;
    private final String sheetKey;

    public SheetAssignment(String semanticObjectId, String sheetKey, String sourceReference,
        EvidenceState evidenceState, String recordedBy, String recordedAt, String revision) {
      super(sourceReference, evidenceState, recordedBy, recordedAt, revision);
      this.semanticObjectId = requireText(semanticObjectId, "semanticObjectId");
      this.sheetKey = requireText(sheetKey, "sheetKey");
    }

    public String getSemanticObjectId() {
      return semanticObjectId;
    }

    public String getSheetKey() {
      return sheetKey;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("semanticObjectId", semanticObjectId);
      result.put("sheetKey", sheetKey);
      addEvidence(result);
      return result;
    }
  }

  /** Immutable pinned object position on one sheet. */
  public static final class PinnedPosition extends LayoutEvidence {
    private static final long serialVersionUID = 1000L;
    private final String semanticObjectId;
    private final String sheetKey;
    private final double x;
    private final double y;
    private final CoordinateUnit unit;

    public PinnedPosition(String semanticObjectId, String sheetKey, double x, double y, CoordinateUnit unit,
        String sourceReference, EvidenceState evidenceState, String recordedBy, String recordedAt, String revision) {
      super(sourceReference, evidenceState, recordedBy, recordedAt, revision);
      this.semanticObjectId = requireText(semanticObjectId, "semanticObjectId");
      this.sheetKey = requireText(sheetKey, "sheetKey");
      this.x = requireCoordinate(x, "x");
      this.y = requireCoordinate(y, "y");
      if (unit == null) {
        throw new IllegalArgumentException("unit must not be null");
      }
      this.unit = unit;
    }

    public String getSemanticObjectId() {
      return semanticObjectId;
    }

    public String getSheetKey() {
      return sheetKey;
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    public CoordinateUnit getUnit() {
      return unit;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("semanticObjectId", semanticObjectId);
      result.put("sheetKey", sheetKey);
      result.put("x", Double.valueOf(x));
      result.put("y", Double.valueOf(y));
      result.put("unit", unit.name());
      addEvidence(result);
      return result;
    }
  }

  /** Immutable route waypoint. */
  public static final class Waypoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double x;
    private final double y;

    public Waypoint(double x, double y) {
      this.x = requireCoordinate(x, "x");
      this.y = requireCoordinate(y, "y");
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("x", Double.valueOf(x));
      result.put("y", Double.valueOf(y));
      return result;
    }
  }

  /** Immutable protected route for one semantic connection view. */
  public static final class ProtectedRoute extends LayoutEvidence {
    private static final long serialVersionUID = 1000L;
    private final String semanticConnectionId;
    private final String sheetKey;
    private final List<Waypoint> waypoints;
    private final CoordinateUnit unit;

    public ProtectedRoute(String semanticConnectionId, String sheetKey, List<Waypoint> waypoints, CoordinateUnit unit,
        String sourceReference, EvidenceState evidenceState, String recordedBy, String recordedAt, String revision) {
      super(sourceReference, evidenceState, recordedBy, recordedAt, revision);
      this.semanticConnectionId = requireText(semanticConnectionId, "semanticConnectionId");
      this.sheetKey = requireText(sheetKey, "sheetKey");
      if (waypoints == null || waypoints.size() < 2) {
        throw new IllegalArgumentException("waypoints must contain at least two points");
      }
      for (Waypoint waypoint : waypoints) {
        if (waypoint == null) {
          throw new IllegalArgumentException("waypoint must not be null");
        }
      }
      this.waypoints = Collections.unmodifiableList(new ArrayList<Waypoint>(waypoints));
      if (unit == null) {
        throw new IllegalArgumentException("unit must not be null");
      }
      this.unit = unit;
    }

    public String getSemanticConnectionId() {
      return semanticConnectionId;
    }

    public String getSheetKey() {
      return sheetKey;
    }

    public List<Waypoint> getWaypoints() {
      return Collections.unmodifiableList(new ArrayList<Waypoint>(waypoints));
    }

    public CoordinateUnit getUnit() {
      return unit;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("semanticConnectionId", semanticConnectionId);
      result.put("sheetKey", sheetKey);
      List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
      for (Waypoint waypoint : waypoints) {
        points.add(waypoint.toMap());
      }
      result.put("waypoints", points);
      result.put("unit", unit.name());
      result.put("protected", Boolean.TRUE);
      addEvidence(result);
      return result;
    }
  }

  private final List<SheetDefinition> sheets;
  private final List<SheetAssignment> assignments;
  private final List<PinnedPosition> pinnedPositions;
  private final List<ProtectedRoute> protectedRoutes;

  /** Creates an empty immutable layout register. */
  public EngineeringDiagramLayoutRegister() {
    this(Collections.<SheetDefinition>emptyList(), Collections.<SheetAssignment>emptyList(),
        Collections.<PinnedPosition>emptyList(), Collections.<ProtectedRoute>emptyList());
  }

  public EngineeringDiagramLayoutRegister(List<SheetDefinition> sheets, List<SheetAssignment> assignments,
      List<PinnedPosition> pinnedPositions, List<ProtectedRoute> protectedRoutes) {
    this.sheets = sortedSheets(sheets);
    this.assignments = sortedAssignments(assignments);
    this.pinnedPositions = sortedPositions(pinnedPositions);
    this.protectedRoutes = sortedRoutes(protectedRoutes);
  }

  public List<SheetDefinition> getSheets() {
    return Collections.unmodifiableList(new ArrayList<SheetDefinition>(sheets));
  }

  public List<SheetAssignment> getAssignments() {
    return Collections.unmodifiableList(new ArrayList<SheetAssignment>(assignments));
  }

  public List<PinnedPosition> getPinnedPositions() {
    return Collections.unmodifiableList(new ArrayList<PinnedPosition>(pinnedPositions));
  }

  public List<ProtectedRoute> getProtectedRoutes() {
    return Collections.unmodifiableList(new ArrayList<ProtectedRoute>(protectedRoutes));
  }

  public EngineeringDiagramLayoutRegister withSheet(SheetDefinition sheet) {
    List<SheetDefinition> result = new ArrayList<SheetDefinition>(sheets);
    result.add(requireItem(sheet, "sheet"));
    return new EngineeringDiagramLayoutRegister(result, assignments, pinnedPositions, protectedRoutes);
  }

  public EngineeringDiagramLayoutRegister withAssignment(SheetAssignment assignment) {
    List<SheetAssignment> result = new ArrayList<SheetAssignment>(assignments);
    result.add(requireItem(assignment, "assignment"));
    return new EngineeringDiagramLayoutRegister(sheets, result, pinnedPositions, protectedRoutes);
  }

  public EngineeringDiagramLayoutRegister withPinnedPosition(PinnedPosition position) {
    List<PinnedPosition> result = new ArrayList<PinnedPosition>(pinnedPositions);
    result.add(requireItem(position, "position"));
    return new EngineeringDiagramLayoutRegister(sheets, assignments, result, protectedRoutes);
  }

  public EngineeringDiagramLayoutRegister withProtectedRoute(ProtectedRoute route) {
    List<ProtectedRoute> result = new ArrayList<ProtectedRoute>(protectedRoutes);
    result.add(requireItem(route, "route"));
    return new EngineeringDiagramLayoutRegister(sheets, assignments, pinnedPositions, result);
  }

  private static List<SheetDefinition> sortedSheets(List<SheetDefinition> values) {
    List<SheetDefinition> result = checkedCopy(values, "sheets");
    Collections.sort(result, new Comparator<SheetDefinition>() {
      @Override
      public int compare(SheetDefinition left, SheetDefinition right) {
        return left.getSheetKey().compareTo(right.getSheetKey());
      }
    });
    requireUnique(result, new Key<SheetDefinition>() {
      @Override
      public String value(SheetDefinition item) {
        return item.getSheetKey();
      }
    }, "sheetKey");
    return Collections.unmodifiableList(result);
  }

  private static List<SheetAssignment> sortedAssignments(List<SheetAssignment> values) {
    List<SheetAssignment> result = checkedCopy(values, "assignments");
    Collections.sort(result, new Comparator<SheetAssignment>() {
      @Override
      public int compare(SheetAssignment left, SheetAssignment right) {
        return left.getSemanticObjectId().compareTo(right.getSemanticObjectId());
      }
    });
    requireUnique(result, new Key<SheetAssignment>() {
      @Override
      public String value(SheetAssignment item) {
        return item.getSemanticObjectId();
      }
    }, "semanticObjectId");
    return Collections.unmodifiableList(result);
  }

  private static List<PinnedPosition> sortedPositions(List<PinnedPosition> values) {
    List<PinnedPosition> result = checkedCopy(values, "pinnedPositions");
    Collections.sort(result, new Comparator<PinnedPosition>() {
      @Override
      public int compare(PinnedPosition left, PinnedPosition right) {
        int sheet = left.getSheetKey().compareTo(right.getSheetKey());
        return sheet != 0 ? sheet : left.getSemanticObjectId().compareTo(right.getSemanticObjectId());
      }
    });
    requireUnique(result, new Key<PinnedPosition>() {
      @Override
      public String value(PinnedPosition item) {
        return item.getSheetKey() + "\n" + item.getSemanticObjectId();
      }
    }, "sheet/object position");
    return Collections.unmodifiableList(result);
  }

  private static List<ProtectedRoute> sortedRoutes(List<ProtectedRoute> values) {
    List<ProtectedRoute> result = checkedCopy(values, "protectedRoutes");
    Collections.sort(result, new Comparator<ProtectedRoute>() {
      @Override
      public int compare(ProtectedRoute left, ProtectedRoute right) {
        int sheet = left.getSheetKey().compareTo(right.getSheetKey());
        return sheet != 0 ? sheet : left.getSemanticConnectionId().compareTo(right.getSemanticConnectionId());
      }
    });
    requireUnique(result, new Key<ProtectedRoute>() {
      @Override
      public String value(ProtectedRoute item) {
        return item.getSheetKey() + "\n" + item.getSemanticConnectionId();
      }
    }, "sheet/connection route");
    return Collections.unmodifiableList(result);
  }

  private interface Key<T> {
    String value(T item);
  }

  private static <T> void requireUnique(List<T> values, Key<T> key, String name) {
    String previous = null;
    for (T value : values) {
      String current = key.value(value);
      if (current.equals(previous)) {
        throw new IllegalArgumentException("Duplicate " + name + " " + current.replace('\n', '/'));
      }
      previous = current;
    }
  }

  private static <T> List<T> checkedCopy(List<T> values, String name) {
    if (values == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    List<T> result = new ArrayList<T>(values);
    for (T value : result) {
      requireItem(value, name + " item");
    }
    return result;
  }

  private static <T> T requireItem(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    return value;
  }

  private static double requireCoordinate(double value, String name) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be a finite non-negative coordinate");
    }
    return value;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
