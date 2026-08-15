package neqsim.process.engineering.model;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Designation;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Kind;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ProvenanceRecord;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;

/**
 * Immutable, deterministic stream-table projection of one governed operating case.
 *
 * <p>
 * The table is derived only from the canonical semantic-object snapshots in an {@link EngineeringDiagramDocumentSet}.
 * It does not read live process objects, change the controlled document JSON, or imply accountable approval of
 * calculated values. A reviewed stream number is preferred for display while the canonical semantic-object identity
 * remains authoritative.
 * </p>
 */
public final class EngineeringDiagramStreamTable implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_stream_table.v2";

  /** Supported stream-table quantities in deterministic column order. */
  public enum Quantity {
    /** Thermodynamic absolute temperature. */
    TEMPERATURE("temperature"),
    /** Absolute pressure. */
    PRESSURE("pressure"),
    /** Mass-flow rate. */
    MASS_FLOW("massFlow"),
    /** Mass-specific enthalpy. */
    SPECIFIC_ENTHALPY("specificEnthalpy");

    private final String propertyName;

    Quantity(String propertyName) {
      this.propertyName = propertyName;
    }

    /**
     * Returns the canonical calculation property name.
     *
     * @return property name
     */
    public String getPropertyName() {
      return propertyName;
    }

    private static Quantity fromPropertyName(String value) {
      for (Quantity quantity : values()) {
        if (quantity.propertyName.equals(value)) {
          return quantity;
        }
      }
      return null;
    }
  }

  /** One structured extraction diagnostic. */
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

    /**
     * Returns diagnostic severity.
     *
     * @return severity
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Returns stable machine-readable diagnostic code.
     *
     * @return diagnostic code
     */
    public String getCode() {
      return code;
    }

    /**
     * Returns human-readable diagnostic message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Returns the affected semantic-object identity.
     *
     * @return subject identity, or an empty string
     */
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

  /** One governed quantity value in a stream row. */
  public static final class Value implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Quantity quantity;
    private final double resultValue;
    private final String resultUnit;
    private final String quantityBasis;
    private final String engineeringState;
    private final String approvalStatus;
    private final String sourceCalculationId;
    private final List<ProvenanceRecord> provenance;

    private Value(Quantity quantity, double resultValue, String resultUnit, String quantityBasis,
        String engineeringState, String approvalStatus, String sourceCalculationId, List<ProvenanceRecord> provenance) {
      this.quantity = quantity;
      this.resultValue = resultValue;
      this.resultUnit = resultUnit;
      this.quantityBasis = quantityBasis;
      this.engineeringState = engineeringState;
      this.approvalStatus = approvalStatus;
      this.sourceCalculationId = sourceCalculationId;
      this.provenance = Collections.unmodifiableList(new ArrayList<ProvenanceRecord>(provenance));
    }

    /**
     * Returns the quantity represented by this value.
     *
     * @return quantity
     */
    public Quantity getQuantity() {
      return quantity;
    }

    /**
     * Returns the numerical result.
     *
     * @return result value
     */
    public double getResultValue() {
      return resultValue;
    }

    /**
     * Returns the explicit result unit.
     *
     * @return result unit
     */
    public String getResultUnit() {
      return resultUnit;
    }

    /**
     * Returns the explicit quantity basis.
     *
     * @return quantity basis
     */
    public String getQuantityBasis() {
      return quantityBasis;
    }

    /**
     * Returns the engineering state carried by the calculation.
     *
     * @return engineering state
     */
    public String getEngineeringState() {
      return engineeringState;
    }

    /**
     * Returns the approval status carried by the calculation.
     *
     * @return approval status
     */
    public String getApprovalStatus() {
      return approvalStatus;
    }

    /**
     * Returns the stable source calculation identity.
     *
     * @return calculation identity
     */
    public String getSourceCalculationId() {
      return sourceCalculationId;
    }

    /**
     * Returns immutable provenance snapshots.
     *
     * @return provenance snapshots
     */
    public List<ProvenanceRecord> getProvenance() {
      return Collections.unmodifiableList(new ArrayList<ProvenanceRecord>(provenance));
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("quantity", quantity.name());
      result.put("resultValue", Double.valueOf(resultValue));
      result.put("resultUnit", resultUnit);
      result.put("quantityBasis", quantityBasis);
      result.put("engineeringState", engineeringState);
      result.put("approvalStatus", approvalStatus);
      result.put("sourceCalculationId", sourceCalculationId);
      List<Map<String, Object>> provenanceMaps = new ArrayList<Map<String, Object>>();
      for (ProvenanceRecord record : provenance) {
        provenanceMaps.add(provenanceMap(record));
      }
      result.put("provenance", provenanceMaps);
      return result;
    }
  }

  /** One stable canonical stream row. */
  public static final class Row implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String semanticObjectId;
    private final String externalKey;
    private final String sourceLabel;
    private final String displayIdentifier;
    private final String designationSourceReference;
    private final String areaName;
    private final Map<Quantity, Value> values;

    private Row(SemanticObject stream, String displayIdentifier, String designationSourceReference, String areaName,
        Map<Quantity, Value> values) {
      this.semanticObjectId = stream.getId();
      this.externalKey = stream.getExternalKey();
      this.sourceLabel = stream.getLabel();
      this.displayIdentifier = displayIdentifier;
      this.designationSourceReference = designationSourceReference;
      this.areaName = areaName;
      this.values = Collections.unmodifiableMap(new EnumMap<Quantity, Value>(values));
    }

    /**
     * Returns the stable canonical stream semantic-object identity.
     *
     * @return semantic-object identity
     */
    public String getSemanticObjectId() {
      return semanticObjectId;
    }

    /**
     * Returns the canonical external source key.
     *
     * @return external key
     */
    public String getExternalKey() {
      return externalKey;
    }

    /**
     * Returns the canonical source label.
     *
     * @return source label
     */
    public String getSourceLabel() {
      return sourceLabel;
    }

    /**
     * Returns the reviewed stream number when available, otherwise the canonical source label.
     *
     * @return display identifier
     */
    public String getDisplayIdentifier() {
      return displayIdentifier;
    }

    /**
     * Returns the source reference for the reviewed designation used for display.
     *
     * @return designation source reference, or an empty string
     */
    public String getDesignationSourceReference() {
      return designationSourceReference;
    }

    /**
     * Returns the source process-area name.
     *
     * @return area name, or an empty string
     */
    public String getAreaName() {
      return areaName;
    }

    /**
     * Returns immutable governed values by quantity.
     *
     * @return values by quantity
     */
    public Map<Quantity, Value> getValues() {
      return Collections.unmodifiableMap(new EnumMap<Quantity, Value>(values));
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("semanticObjectId", semanticObjectId);
      result.put("externalKey", externalKey);
      result.put("sourceLabel", sourceLabel);
      result.put("displayIdentifier", displayIdentifier);
      result.put("designationSourceReference", designationSourceReference);
      result.put("areaName", areaName);
      List<Map<String, Object>> valueMaps = new ArrayList<Map<String, Object>>();
      for (Quantity quantity : Quantity.values()) {
        Value value = values.get(quantity);
        if (value != null) {
          valueMaps.add(value.toMap());
        }
      }
      result.put("values", valueMaps);
      return result;
    }
  }

  private final String documentSetId;
  private final String sourceGraphFingerprint;
  private final String designCaseId;
  private final List<Row> rows;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramStreamTable(String documentSetId, String sourceGraphFingerprint, String designCaseId,
      List<Row> rows, List<Diagnostic> diagnostics) {
    this.documentSetId = documentSetId;
    this.sourceGraphFingerprint = sourceGraphFingerprint;
    this.designCaseId = designCaseId;
    this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Creates a deterministic stream table from one controlled operating-case snapshot.
   *
   * @param documentSet controlled diagram document set
   * @param designCaseId requested operating-case identity
   * @return immutable stream table with structured loss diagnostics
   * @throws IllegalArgumentException if either argument is missing
   */
  public static EngineeringDiagramStreamTable fromDocumentSet(EngineeringDiagramDocumentSet documentSet,
      String designCaseId) {
    if (documentSet == null) {
      throw new IllegalArgumentException("documentSet must not be null");
    }
    String normalizedCaseId = requireText(designCaseId, "designCaseId");
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    List<SemanticObject> calculations = calculationsForCase(documentSet, normalizedCaseId);
    List<SemanticObject> streams = sortedStreams(documentSet);
    if (calculations.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "STREAM_TABLE_CASE_NOT_FOUND",
          "No governed stream calculations were found for the requested operating case", normalizedCaseId));
    }

    Map<String, Map<Quantity, Value>> valuesByStream = new LinkedHashMap<String, Map<Quantity, Value>>();
    Map<String, String> areaByStream = new LinkedHashMap<String, String>();
    for (SemanticObject calculation : calculations) {
      addCalculation(calculation, streams, valuesByStream, areaByStream, diagnostics);
    }

    List<Row> rows = new ArrayList<Row>();
    for (SemanticObject stream : streams) {
      Map<Quantity, Value> values = valuesByStream.get(stream.getId());
      if (values == null) {
        values = new EnumMap<Quantity, Value>(Quantity.class);
      }
      for (Quantity quantity : Quantity.values()) {
        if (!values.containsKey(quantity)) {
          diagnostics.add(new Diagnostic(Severity.WARNING, "STREAM_TABLE_VALUE_MISSING",
              "The requested operating case has no governed " + quantity.getPropertyName() + " value", stream.getId()));
        }
      }
      Designation streamNumber = reviewedStreamNumber(stream);
      String displayIdentifier = streamNumber == null ? fallback(stream.getLabel(), stream.getExternalKey())
          : streamNumber.getValue();
      String designationSourceReference = streamNumber == null ? "" : streamNumber.getSourceReference();
      String areaName = areaByStream.containsKey(stream.getId()) ? areaByStream.get(stream.getId())
          : optionalProperty(stream, "areaName");
      rows.add(new Row(stream, displayIdentifier, designationSourceReference, areaName, values));
    }
    Collections.sort(diagnostics, new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int subjectOrder = left.getSubjectId().compareTo(right.getSubjectId());
        return subjectOrder != 0 ? subjectOrder : left.getCode().compareTo(right.getCode());
      }
    });
    return new EngineeringDiagramStreamTable(documentSet.getId(), documentSet.getSourceGraphFingerprint(),
        normalizedCaseId, rows, diagnostics);
  }

  /**
   * Returns the source controlled-document identity.
   *
   * @return document-set identity
   */
  public String getDocumentSetId() {
    return documentSetId;
  }

  /**
   * Returns the canonical source-graph fingerprint.
   *
   * @return source-graph fingerprint
   */
  public String getSourceGraphFingerprint() {
    return sourceGraphFingerprint;
  }

  /**
   * Returns the selected operating-case identity.
   *
   * @return operating-case identity
   */
  public String getDesignCaseId() {
    return designCaseId;
  }

  /**
   * Returns immutable rows sorted by canonical semantic-object identity.
   *
   * @return stream rows
   */
  public List<Row> getRows() {
    return Collections.unmodifiableList(new ArrayList<Row>(rows));
  }

  /**
   * Returns immutable structured extraction diagnostics.
   *
   * @return diagnostics
   */
  public List<Diagnostic> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Returns true when extraction found no error-severity diagnostic.
   *
   * @return extraction validity
   */
  public boolean isValid() {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a deterministic machine-readable representation.
   *
   * @return stream-table representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("documentSetId", documentSetId);
    result.put("sourceGraphFingerprint", sourceGraphFingerprint);
    result.put("designCaseId", designCaseId);
    List<Map<String, Object>> rowMaps = new ArrayList<Map<String, Object>>();
    for (Row row : rows) {
      rowMaps.add(row.toMap());
    }
    result.put("rows", rowMaps);
    List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
    for (Diagnostic diagnostic : diagnostics) {
      diagnosticMaps.add(diagnostic.toMap());
    }
    result.put("diagnostics", diagnosticMaps);
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  /**
   * Returns deterministic pretty-printed JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static List<SemanticObject> calculationsForCase(EngineeringDiagramDocumentSet documentSet,
      String designCaseId) {
    List<SemanticObject> result = new ArrayList<SemanticObject>();
    for (SemanticObject object : documentSet.getSemanticObjects()) {
      if (object.getKind() == EngineeringNode.Kind.CALCULATION
          && designCaseId.equals(optionalProperty(object, "designCaseId"))) {
        result.add(object);
      }
    }
    Collections.sort(result, semanticObjectComparator());
    return result;
  }

  private static List<SemanticObject> sortedStreams(EngineeringDiagramDocumentSet documentSet) {
    List<SemanticObject> result = new ArrayList<SemanticObject>();
    for (SemanticObject object : documentSet.getSemanticObjects()) {
      if (object.getKind() == EngineeringNode.Kind.LINE) {
        result.add(object);
      }
    }
    Collections.sort(result, semanticObjectComparator());
    return result;
  }

  private static Comparator<SemanticObject> semanticObjectComparator() {
    return new Comparator<SemanticObject>() {
      @Override
      public int compare(SemanticObject left, SemanticObject right) {
        return left.getId().compareTo(right.getId());
      }
    };
  }

  private static void addCalculation(SemanticObject calculation, List<SemanticObject> streams,
      Map<String, Map<Quantity, Value>> valuesByStream, Map<String, String> areaByStream,
      List<Diagnostic> diagnostics) {
    Quantity quantity = Quantity.fromPropertyName(optionalProperty(calculation, "quantity"));
    if (quantity == null) {
      return;
    }
    String subjectId = optionalProperty(calculation, "subjectNodeId");
    if (!containsSemanticObject(streams, subjectId)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "STREAM_TABLE_UNKNOWN_STREAM",
          "A governed stream value references a missing or non-stream semantic object", calculation.getId()));
      return;
    }
    Object rawValue = calculation.getProperties().get("resultValue");
    String unit = optionalProperty(calculation, "resultUnit");
    String basis = optionalProperty(calculation, "quantityBasis");
    String engineeringState = optionalProperty(calculation, "engineeringState");
    String approvalStatus = optionalProperty(calculation, "approvalStatus");
    if (subjectId.isEmpty() || !(rawValue instanceof Number) || unit.isEmpty() || basis.isEmpty()
        || engineeringState.isEmpty() || approvalStatus.isEmpty() || calculation.getProvenance().isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "STREAM_TABLE_VALUE_INCOMPLETE",
          "A governed stream value is missing identity, value, unit, basis, state, approval, or provenance",
          calculation.getId()));
      return;
    }
    double number = ((Number) rawValue).doubleValue();
    if (!Double.isFinite(number)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "STREAM_TABLE_VALUE_NONFINITE",
          "A non-finite governed stream value was excluded", calculation.getId()));
      return;
    }
    Map<Quantity, Value> values = valuesByStream.get(subjectId);
    if (values == null) {
      values = new EnumMap<Quantity, Value>(Quantity.class);
      valuesByStream.put(subjectId, values);
    }
    if (values.containsKey(quantity)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "STREAM_TABLE_DUPLICATE_VALUE",
          "More than one governed value exists for the stream, case, and quantity", subjectId));
      return;
    }
    values.put(quantity, new Value(quantity, number, unit, basis, engineeringState, approvalStatus, calculation.getId(),
        calculation.getProvenance()));
    String areaName = optionalProperty(calculation, "areaName");
    if (!areaName.isEmpty() && !areaByStream.containsKey(subjectId)) {
      areaByStream.put(subjectId, areaName);
    }
  }

  private static boolean containsSemanticObject(List<SemanticObject> objects, String semanticObjectId) {
    for (SemanticObject object : objects) {
      if (object.getId().equals(semanticObjectId)) {
        return true;
      }
    }
    return false;
  }

  private static Designation reviewedStreamNumber(SemanticObject stream) {
    for (Designation designation : stream.getDesignations()) {
      if (designation.getKind() == Kind.STREAM_NUMBER) {
        return designation;
      }
    }
    return null;
  }

  private static String optionalProperty(SemanticObject object, String key) {
    Object value = object.getProperties().get(key);
    return value == null ? "" : value.toString().trim();
  }

  private static Map<String, Object> provenanceMap(ProvenanceRecord record) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("sourceType", record.getSourceType());
    result.put("sourceReference", record.getSourceReference());
    result.put("method", record.getMethod());
    result.put("designCaseId", record.getDesignCaseId());
    result.put("approvalStatus", record.getApprovalStatus());
    result.put("evidenceReferences", record.getEvidenceReferences());
    return result;
  }

  private static String fallback(String preferred, String fallback) {
    return preferred == null || preferred.trim().isEmpty() ? fallback : preferred;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static String fingerprint(Map<String, Object> map) {
    Map<String, Object> content = new LinkedHashMap<String, Object>(map);
    content.remove("fingerprint");
    String json = new GsonBuilder().create().toJson(content);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format("%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
