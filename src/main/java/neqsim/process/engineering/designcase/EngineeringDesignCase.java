package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/** An executable, evidence-controlled operating or accidental design case. */
public final class EngineeringDesignCase implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Type {
    NORMAL, MAXIMUM_PRODUCTION, MINIMUM_TURNDOWN, STARTUP, SHUTDOWN, UTILITY_FAILURE, EQUIPMENT_TRIP, SETTLE_OUT,
    BLOCKED_OUTLET, FIRE, BLOWDOWN, AMBIENT_EXTREME, CUSTOM
  }

  /** Applies case-specific inputs to an isolated copy of the process. */
  public interface Configurator extends Serializable {
    void configure(ProcessSystem process);
  }

  /** A controlled scalar input used when configuring the design case. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double value;
    private final String unit;
    private final String evidenceReference;

    public Input(String name, double value, String unit, String evidenceReference) {
      this.name = requireText(name, "name");
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("value must be finite");
      }
      this.value = value;
      this.unit = requireText(unit, "unit");
      this.evidenceReference = textOrEmpty(evidenceReference);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("value", value);
      result.put("unit", unit);
      result.put("evidenceReference", evidenceReference);
      return result;
    }
  }

  private final String id;
  private final String name;
  private final Type type;
  private final Configurator configurator;
  private String description = "";
  private String approvalStatus = "REVIEW_REQUIRED";
  private final List<Input> inputs = new ArrayList<Input>();
  private final List<String> evidenceReferences = new ArrayList<String>();

  public EngineeringDesignCase(String id, String name, Type type, Configurator configurator) {
    this.id = requireText(id, "id");
    this.name = requireText(name, "name");
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (configurator == null) {
      throw new IllegalArgumentException("configurator must not be null");
    }
    this.type = type;
    this.configurator = configurator;
  }

  public EngineeringDesignCase setDescription(String value) {
    description = textOrEmpty(value);
    return this;
  }

  public EngineeringDesignCase setApprovalStatus(String value) {
    approvalStatus = requireText(value, "approvalStatus");
    return this;
  }

  public EngineeringDesignCase addInput(Input value) {
    if (value == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    inputs.add(value);
    return this;
  }

  public EngineeringDesignCase addEvidenceReference(String value) {
    String normalized = textOrEmpty(value);
    if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
      evidenceReferences.add(normalized);
    }
    return this;
  }

  void configure(ProcessSystem process) {
    configurator.configure(process);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public String getApprovalStatus() {
    return approvalStatus;
  }

  public List<Input> getInputs() {
    return Collections.unmodifiableList(inputs);
  }

  public List<String> getEvidenceReferences() {
    return Collections.unmodifiableList(evidenceReferences);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", name);
    result.put("type", type.name());
    result.put("description", description);
    result.put("approvalStatus", approvalStatus);
    List<Map<String, Object>> inputMaps = new ArrayList<Map<String, Object>>();
    for (Input input : inputs) {
      inputMaps.add(input.toMap());
    }
    result.put("inputs", inputMaps);
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    return result;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
