package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An auditable engineering calculation and its explicit dependency inputs. */
public final class EngineeringCalculation implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Status {
    CALCULATED, BLOCKED, FAILED, REVIEW_REQUIRED, APPROVED
  }

  /** A controlled standards basis for a calculation method or acceptance criterion. */
  public static final class StandardReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String standard;
    private final String edition;
    private final String clause;
    private final String applicability;

    public StandardReference(String standard, String edition, String clause, String applicability) {
      this.standard = requireText(standard, "standard");
      this.edition = textOrEmpty(edition);
      this.clause = textOrEmpty(clause);
      this.applicability = textOrEmpty(applicability);
    }

    public String getStandard() {
      return standard;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("standard", standard);
      result.put("edition", edition);
      result.put("clause", clause);
      result.put("applicability", applicability);
      return result;
    }
  }

  /** A named value or graph-node dependency used by a calculation. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String sourceNodeId;
    private final Double value;
    private final String unit;
    private final String evidenceReference;

    public Input(String name, String sourceNodeId, Double value, String unit, String evidenceReference) {
      this.name = requireText(name, "name");
      this.sourceNodeId = textOrEmpty(sourceNodeId);
      this.value = value != null && Double.isFinite(value.doubleValue()) ? value : null;
      this.unit = textOrEmpty(unit);
      this.evidenceReference = textOrEmpty(evidenceReference);
    }

    public String getSourceNodeId() {
      return sourceNodeId;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("sourceNodeId", sourceNodeId);
      if (value != null) {
        result.put("value", value);
      }
      result.put("unit", unit);
      result.put("evidenceReference", evidenceReference);
      return result;
    }
  }

  private final String id;
  private final String subjectNodeId;
  private final String method;
  private Status status = Status.REVIEW_REQUIRED;
  private Double resultValue;
  private String resultUnit = "";
  private String designCaseId = "";
  private String standardReference = "";
  private boolean standardsRequired;
  private String message = "";
  private final List<Input> inputs = new ArrayList<Input>();
  private final List<String> prerequisiteCalculationIds = new ArrayList<String>();
  private final List<StandardReference> standardReferences = new ArrayList<StandardReference>();
  private final List<String> evidenceReferences = new ArrayList<String>();

  public EngineeringCalculation(String id, String subjectNodeId, String method) {
    this.id = requireText(id, "id");
    this.subjectNodeId = requireText(subjectNodeId, "subjectNodeId");
    this.method = requireText(method, "method");
  }

  public EngineeringCalculation setStatus(Status value) {
    if (value == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    status = value;
    return this;
  }

  public EngineeringCalculation setResult(double value, String unit) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("result must be finite");
    }
    resultValue = Double.valueOf(value);
    resultUnit = requireText(unit, "unit");
    return this;
  }

  public EngineeringCalculation setDesignCaseId(String value) {
    designCaseId = textOrEmpty(value);
    return this;
  }

  public EngineeringCalculation setStandardReference(String value) {
    standardReference = textOrEmpty(value);
    return this;
  }

  public EngineeringCalculation setStandardsRequired(boolean value) {
    standardsRequired = value;
    return this;
  }

  public EngineeringCalculation addStandardReference(StandardReference value) {
    if (value == null) {
      throw new IllegalArgumentException("standard reference must not be null");
    }
    standardReferences.add(value);
    return this;
  }

  public EngineeringCalculation dependsOnCalculation(String calculationId) {
    String normalized = requireText(calculationId, "calculationId");
    if (id.equals(normalized)) {
      throw new IllegalArgumentException("Calculation must not depend on itself: " + id);
    }
    if (!prerequisiteCalculationIds.contains(normalized)) {
      prerequisiteCalculationIds.add(normalized);
    }
    return this;
  }

  public EngineeringCalculation setMessage(String value) {
    message = textOrEmpty(value);
    return this;
  }

  public EngineeringCalculation addInput(Input input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    inputs.add(input);
    return this;
  }

  public EngineeringCalculation addEvidenceReference(String value) {
    String normalized = textOrEmpty(value);
    if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
      evidenceReferences.add(normalized);
    }
    return this;
  }

  public String getId() {
    return id;
  }

  public String getSubjectNodeId() {
    return subjectNodeId;
  }

  public String getMethod() {
    return method;
  }

  public Status getStatus() {
    return status;
  }

  public Double getResultValue() {
    return resultValue;
  }

  public String getResultUnit() {
    return resultUnit;
  }

  public String getDesignCaseId() {
    return designCaseId;
  }

  public String getStandardReference() {
    return standardReference;
  }

  public boolean isStandardsRequired() {
    return standardsRequired;
  }

  public boolean hasStandardsBasis() {
    return !standardReference.isEmpty() || !standardReferences.isEmpty();
  }

  public String getMessage() {
    return message;
  }

  public List<Input> getInputs() {
    return Collections.unmodifiableList(inputs);
  }

  public List<String> getPrerequisiteCalculationIds() {
    return Collections.unmodifiableList(prerequisiteCalculationIds);
  }

  public List<StandardReference> getStandardReferences() {
    return Collections.unmodifiableList(standardReferences);
  }

  public List<String> getEvidenceReferences() {
    return Collections.unmodifiableList(evidenceReferences);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("subjectNodeId", subjectNodeId);
    result.put("method", method);
    result.put("status", status.name());
    if (resultValue != null) {
      result.put("resultValue", resultValue);
      result.put("resultUnit", resultUnit);
    }
    result.put("designCaseId", designCaseId);
    result.put("standardReference", standardReference);
    result.put("standardsRequired", Boolean.valueOf(standardsRequired));
    result.put("standardsReady", Boolean.valueOf(!standardsRequired || hasStandardsBasis()));
    result.put("message", message);
    List<Map<String, Object>> inputMaps = new ArrayList<Map<String, Object>>();
    for (Input input : inputs) {
      inputMaps.add(input.toMap());
    }
    result.put("inputs", inputMaps);
    result.put("prerequisiteCalculationIds", new ArrayList<String>(prerequisiteCalculationIds));
    List<Map<String, Object>> standards = new ArrayList<Map<String, Object>>();
    for (StandardReference reference : standardReferences) {
      standards.add(reference.toMap());
    }
    result.put("standardReferences", standards);
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    return result;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
