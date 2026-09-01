package neqsim.process.engineering.model;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Value;

/**
 * Immutable, deterministic mass and stream-enthalpy balance projection.
 *
 * <p>
 * The projection consumes a governed {@link EngineeringDiagramStreamTable} plus explicit boundary assignments. It never
 * guesses whether a stream is an inlet or outlet. Stream enthalpy flow is calculated as mass flow times mass-specific
 * enthalpy. The result therefore excludes equipment heat duties and shaft work and is not a complete energy balance.
 * </p>
 */
public final class EngineeringDiagramBalanceTable implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_balance_table.v1";

  /** Direction of a stream across a declared balance boundary. */
  public enum Direction {
    /** Stream enters the balance boundary. */
    INLET,
    /** Stream leaves the balance boundary. */
    OUTLET
  }

  /** Evidence state of a boundary assignment. */
  public enum EvidenceState {
    /** Boundary assignment is an engineering proposal requiring review. */
    PROPOSED,
    /** Boundary assignment has review evidence, without implying design approval. */
    REVIEWED
  }

  /** One explicit stream assignment to a named balance boundary. */
  public static final class Boundary implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String streamSemanticObjectId;
    private final Direction direction;
    private final String sourceReference;
    private final EvidenceState evidenceState;

    /**
     * Creates an explicit balance-boundary assignment.
     *
     * @param balanceId stable balance identity
     * @param streamSemanticObjectId canonical stream semantic-object identity
     * @param direction direction across the balance boundary
     * @param sourceReference source or register reference for the assignment
     * @param evidenceState review evidence state
     */
    public Boundary(String balanceId, String streamSemanticObjectId, Direction direction, String sourceReference,
        EvidenceState evidenceState) {
      this.balanceId = requireText(balanceId, "balanceId");
      this.streamSemanticObjectId = requireText(streamSemanticObjectId, "streamSemanticObjectId");
      if (direction == null) {
        throw new IllegalArgumentException("direction must not be null");
      }
      this.direction = direction;
      this.sourceReference = requireText(sourceReference, "sourceReference");
      if (evidenceState == null) {
        throw new IllegalArgumentException("evidenceState must not be null");
      }
      this.evidenceState = evidenceState;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return canonical stream semantic-object identity */
    public String getStreamSemanticObjectId() {
      return streamSemanticObjectId;
    }

    /** @return direction across the boundary */
    public Direction getDirection() {
      return direction;
    }

    /** @return source or register reference */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return evidence state */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("streamSemanticObjectId", streamSemanticObjectId);
      result.put("direction", direction.name());
      result.put("sourceReference", sourceReference);
      result.put("evidenceState", evidenceState.name());
      return result;
    }
  }

  /** One structured balance diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    private Diagnostic(Severity severity, String code, String message, String subjectId) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subjectId = subjectId;
    }

    /** @return diagnostic severity */
    public Severity getSeverity() {
      return severity;
    }

    /** @return stable machine-readable diagnostic code */
    public String getCode() {
      return code;
    }

    /** @return human-readable diagnostic message */
    public String getMessage() {
      return message;
    }

    /** @return affected stable identity */
    public String getSubjectId() {
      return subjectId;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subjectId", subjectId);
      return result;
    }
  }

  /** One aggregate balance result. */
  public static final class Balance implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final int boundaryCount;
    private final double inletMassFlow;
    private final double outletMassFlow;
    private final double massResidual;
    private final double relativeMassResidual;
    private final boolean massFlowComplete;
    private final double inletStreamEnthalpyFlow;
    private final double outletStreamEnthalpyFlow;
    private final double streamEnthalpyResidual;
    private final double relativeStreamEnthalpyResidual;
    private final boolean streamEnthalpyFlowComplete;

    private Balance(String balanceId, Accumulator values) {
      this.balanceId = balanceId;
      this.boundaryCount = values.boundaryCount;
      this.inletMassFlow = values.inletMassFlow;
      this.outletMassFlow = values.outletMassFlow;
      this.massResidual = inletMassFlow - outletMassFlow;
      double scale = Math.max(Math.abs(inletMassFlow), Math.abs(outletMassFlow));
      this.relativeMassResidual = scale == 0.0 ? 0.0 : massResidual / scale;
      this.massFlowComplete = values.massFlowComplete;
      this.inletStreamEnthalpyFlow = values.inletStreamEnthalpyFlow;
      this.outletStreamEnthalpyFlow = values.outletStreamEnthalpyFlow;
      this.streamEnthalpyResidual = inletStreamEnthalpyFlow - outletStreamEnthalpyFlow;
      double enthalpyScale = Math.max(Math.abs(inletStreamEnthalpyFlow), Math.abs(outletStreamEnthalpyFlow));
      this.relativeStreamEnthalpyResidual = enthalpyScale == 0.0 ? 0.0 : streamEnthalpyResidual / enthalpyScale;
      this.streamEnthalpyFlowComplete = values.streamEnthalpyFlowComplete;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return number of declared boundary streams */
    public int getBoundaryCount() {
      return boundaryCount;
    }

    /** @return inlet mass flow in kg/s */
    public double getInletMassFlow() {
      return inletMassFlow;
    }

    /** @return outlet mass flow in kg/s */
    public double getOutletMassFlow() {
      return outletMassFlow;
    }

    /** @return inlet minus outlet mass flow in kg/s */
    public double getMassResidual() {
      return massResidual;
    }

    /** @return mass residual divided by the larger absolute boundary total */
    public double getRelativeMassResidual() {
      return relativeMassResidual;
    }

    /** @return true when every boundary has a usable mass-flow value */
    public boolean isMassFlowComplete() {
      return massFlowComplete;
    }

    /** @return inlet stream enthalpy flow in W */
    public double getInletStreamEnthalpyFlow() {
      return inletStreamEnthalpyFlow;
    }

    /** @return outlet stream enthalpy flow in W */
    public double getOutletStreamEnthalpyFlow() {
      return outletStreamEnthalpyFlow;
    }

    /** @return inlet minus outlet stream enthalpy flow in W */
    public double getStreamEnthalpyResidual() {
      return streamEnthalpyResidual;
    }

    /** @return stream enthalpy residual divided by the larger absolute boundary total */
    public double getRelativeStreamEnthalpyResidual() {
      return relativeStreamEnthalpyResidual;
    }

    /** @return true when every boundary has usable mass flow and specific enthalpy */
    public boolean isStreamEnthalpyFlowComplete() {
      return streamEnthalpyFlowComplete;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("boundaryCount", Integer.valueOf(boundaryCount));
      result.put("inletMassFlow", Double.valueOf(inletMassFlow));
      result.put("outletMassFlow", Double.valueOf(outletMassFlow));
      result.put("massResidual", Double.valueOf(massResidual));
      result.put("relativeMassResidual", Double.valueOf(relativeMassResidual));
      result.put("massFlowUnit", "kg/s");
      result.put("massFlowComplete", Boolean.valueOf(massFlowComplete));
      result.put("inletStreamEnthalpyFlow", Double.valueOf(inletStreamEnthalpyFlow));
      result.put("outletStreamEnthalpyFlow", Double.valueOf(outletStreamEnthalpyFlow));
      result.put("streamEnthalpyResidual", Double.valueOf(streamEnthalpyResidual));
      result.put("relativeStreamEnthalpyResidual", Double.valueOf(relativeStreamEnthalpyResidual));
      result.put("streamEnthalpyFlowUnit", "W");
      result.put("streamEnthalpyFlowComplete", Boolean.valueOf(streamEnthalpyFlowComplete));
      return result;
    }
  }

  private static final class Accumulator {
    private int boundaryCount;
    private double inletMassFlow;
    private double outletMassFlow;
    private boolean massFlowComplete = true;
    private double inletStreamEnthalpyFlow;
    private double outletStreamEnthalpyFlow;
    private boolean streamEnthalpyFlowComplete = true;
  }

  private final String documentSetId;
  private final String sourceGraphFingerprint;
  private final String designCaseId;
  private final String sourceStreamTableFingerprint;
  private final List<Boundary> boundaries;
  private final List<Balance> balances;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramBalanceTable(EngineeringDiagramStreamTable streamTable, List<Boundary> boundaries,
      List<Balance> balances, List<Diagnostic> diagnostics) {
    this.documentSetId = streamTable.getDocumentSetId();
    this.sourceGraphFingerprint = streamTable.getSourceGraphFingerprint();
    this.designCaseId = streamTable.getDesignCaseId();
    this.sourceStreamTableFingerprint = streamTable.toMap().get("fingerprint").toString();
    this.boundaries = Collections.unmodifiableList(new ArrayList<Boundary>(boundaries));
    this.balances = Collections.unmodifiableList(new ArrayList<Balance>(balances));
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Aggregates explicit boundary assignments from a governed stream table.
   *
   * @param streamTable governed stream table
   * @param boundaries explicit stream directions grouped by stable balance identity
   * @return immutable balance table with structured diagnostics
   */
  public static EngineeringDiagramBalanceTable fromStreamTable(EngineeringDiagramStreamTable streamTable,
      List<Boundary> boundaries) {
    if (streamTable == null) {
      throw new IllegalArgumentException("streamTable must not be null");
    }
    if (boundaries == null) {
      throw new IllegalArgumentException("boundaries must not be null");
    }
    List<Boundary> sortedBoundaries = new ArrayList<Boundary>(boundaries);
    if (containsNull(sortedBoundaries)) {
      throw new IllegalArgumentException("boundaries must not contain null");
    }
    Collections.sort(sortedBoundaries, boundaryComparator());
    List<Diagnostic> diagnostics = sourceDiagnostics(streamTable);
    if (sortedBoundaries.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_BOUNDARY_NOT_DECLARED",
          "At least one explicit balance-boundary assignment is required", ""));
    }

    Map<String, Row> rowsById = new LinkedHashMap<String, Row>();
    for (Row row : streamTable.getRows()) {
      rowsById.put(row.getSemanticObjectId(), row);
    }
    Map<String, Accumulator> valuesByBalance = new LinkedHashMap<String, Accumulator>();
    Set<String> assignedStreams = new LinkedHashSet<String>();
    for (Boundary boundary : sortedBoundaries) {
      Accumulator accumulator = valuesByBalance.get(boundary.getBalanceId());
      if (accumulator == null) {
        accumulator = new Accumulator();
        valuesByBalance.put(boundary.getBalanceId(), accumulator);
      }
      accumulator.boundaryCount++;
      String assignmentKey = boundary.getBalanceId() + "|" + boundary.getStreamSemanticObjectId();
      if (!assignedStreams.add(assignmentKey)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_BOUNDARY_DUPLICATE_STREAM",
            "A stream may be assigned only once to the same balance", boundary.getStreamSemanticObjectId()));
        accumulator.massFlowComplete = false;
        accumulator.streamEnthalpyFlowComplete = false;
        continue;
      }
      Row row = rowsById.get(boundary.getStreamSemanticObjectId());
      if (row == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_BOUNDARY_UNKNOWN_STREAM",
            "A boundary assignment references a stream absent from the governed stream table",
            boundary.getStreamSemanticObjectId()));
        accumulator.massFlowComplete = false;
        accumulator.streamEnthalpyFlowComplete = false;
        continue;
      }
      addBoundaryValues(boundary, row, accumulator, diagnostics);
    }

    List<Balance> balances = new ArrayList<Balance>();
    for (Map.Entry<String, Accumulator> entry : valuesByBalance.entrySet()) {
      balances.add(new Balance(entry.getKey(), entry.getValue()));
    }
    Collections.sort(diagnostics, diagnosticComparator());
    return new EngineeringDiagramBalanceTable(streamTable, sortedBoundaries, balances, diagnostics);
  }

  /** @return source controlled-document identity */
  public String getDocumentSetId() {
    return documentSetId;
  }

  /** @return canonical source-graph fingerprint */
  public String getSourceGraphFingerprint() {
    return sourceGraphFingerprint;
  }

  /** @return selected operating-case identity */
  public String getDesignCaseId() {
    return designCaseId;
  }

  /** @return deterministic fingerprint of the source stream-table representation */
  public String getSourceStreamTableFingerprint() {
    return sourceStreamTableFingerprint;
  }

  /** @return immutable explicit boundary assignments in deterministic order */
  public List<Boundary> getBoundaries() {
    return Collections.unmodifiableList(new ArrayList<Boundary>(boundaries));
  }

  /** @return immutable aggregate balances in deterministic order */
  public List<Balance> getBalances() {
    return Collections.unmodifiableList(new ArrayList<Balance>(balances));
  }

  /** @return immutable structured diagnostics */
  public List<Diagnostic> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /** @return true when no error-severity diagnostic is present */
  public boolean isValid() {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return false;
      }
    }
    return true;
  }

  /** @return deterministic machine-readable representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("documentSetId", documentSetId);
    result.put("sourceGraphFingerprint", sourceGraphFingerprint);
    result.put("designCaseId", designCaseId);
    result.put("sourceStreamTableFingerprint", sourceStreamTableFingerprint);
    List<Map<String, Object>> boundaryMaps = new ArrayList<Map<String, Object>>();
    for (Boundary boundary : boundaries) {
      boundaryMaps.add(boundary.toMap());
    }
    result.put("boundaries", boundaryMaps);
    List<Map<String, Object>> balanceMaps = new ArrayList<Map<String, Object>>();
    for (Balance balance : balances) {
      balanceMaps.add(balance.toMap());
    }
    result.put("balances", balanceMaps);
    List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
    for (Diagnostic diagnostic : diagnostics) {
      diagnosticMaps.add(diagnostic.toMap());
    }
    result.put("diagnostics", diagnosticMaps);
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  /** @return deterministic pretty-printed JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static void addBoundaryValues(Boundary boundary, Row row, Accumulator accumulator,
      List<Diagnostic> diagnostics) {
    Value massFlow = row.getValues().get(Quantity.MASS_FLOW);
    if (!validValue(massFlow, "kg/s", "MASS")) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_MASS_FLOW_UNAVAILABLE",
          "A boundary stream requires mass flow in kg/s on a MASS basis", row.getSemanticObjectId()));
      accumulator.massFlowComplete = false;
      accumulator.streamEnthalpyFlowComplete = false;
      return;
    }
    double mass = massFlow.getResultValue();
    if (mass < 0.0) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_MASS_FLOW_NEGATIVE",
          "Boundary direction must be explicit and boundary mass flow must be non-negative",
          row.getSemanticObjectId()));
      accumulator.massFlowComplete = false;
      accumulator.streamEnthalpyFlowComplete = false;
      return;
    }
    if (boundary.getDirection() == Direction.INLET) {
      accumulator.inletMassFlow += mass;
    } else {
      accumulator.outletMassFlow += mass;
    }

    Value enthalpy = row.getValues().get(Quantity.SPECIFIC_ENTHALPY);
    if (!validValue(enthalpy, "J/kg", "MASS_SPECIFIC")) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_SPECIFIC_ENTHALPY_UNAVAILABLE",
          "A boundary stream requires specific enthalpy in J/kg on a MASS_SPECIFIC basis", row.getSemanticObjectId()));
      accumulator.streamEnthalpyFlowComplete = false;
      return;
    }
    double enthalpyFlow = mass * enthalpy.getResultValue();
    if (!Double.isFinite(enthalpyFlow)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_STREAM_ENTHALPY_FLOW_NONFINITE",
          "Mass flow times specific enthalpy produced a non-finite stream enthalpy flow", row.getSemanticObjectId()));
      accumulator.streamEnthalpyFlowComplete = false;
      return;
    }
    if (boundary.getDirection() == Direction.INLET) {
      accumulator.inletStreamEnthalpyFlow += enthalpyFlow;
    } else {
      accumulator.outletStreamEnthalpyFlow += enthalpyFlow;
    }
  }

  private static boolean validValue(Value value, String unit, String basis) {
    return value != null && unit.equals(value.getResultUnit()) && basis.equals(value.getQuantityBasis())
        && Double.isFinite(value.getResultValue());
  }

  private static List<Diagnostic> sourceDiagnostics(EngineeringDiagramStreamTable streamTable) {
    List<Diagnostic> result = new ArrayList<Diagnostic>();
    for (EngineeringDiagramStreamTable.Diagnostic diagnostic : streamTable.getDiagnostics()) {
      result.add(new Diagnostic(diagnostic.getSeverity(), "BALANCE_SOURCE_" + diagnostic.getCode(),
          diagnostic.getMessage(), diagnostic.getSubjectId()));
    }
    return result;
  }

  private static boolean containsNull(List<Boundary> boundaries) {
    for (Boundary boundary : boundaries) {
      if (boundary == null) {
        return true;
      }
    }
    return false;
  }

  private static Comparator<Boundary> boundaryComparator() {
    return new Comparator<Boundary>() {
      @Override
      public int compare(Boundary left, Boundary right) {
        int order = left.getBalanceId().compareTo(right.getBalanceId());
        if (order != 0) {
          return order;
        }
        order = left.getStreamSemanticObjectId().compareTo(right.getStreamSemanticObjectId());
        if (order != 0) {
          return order;
        }
        order = left.getDirection().compareTo(right.getDirection());
        if (order != 0) {
          return order;
        }
        return left.getSourceReference().compareTo(right.getSourceReference());
      }
    };
  }

  private static Comparator<Diagnostic> diagnosticComparator() {
    return new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int order = left.getSubjectId().compareTo(right.getSubjectId());
        if (order != 0) {
          return order;
        }
        order = left.getCode().compareTo(right.getCode());
        if (order != 0) {
          return order;
        }
        return left.getMessage().compareTo(right.getMessage());
      }
    };
  }

  private static String fingerprint(Map<String, Object> value) {
    String canonical = new GsonBuilder().create().toJson(value);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : hash) {
        result.append(String.format("%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
