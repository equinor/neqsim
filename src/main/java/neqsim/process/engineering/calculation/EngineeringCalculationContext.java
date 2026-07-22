package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Controlled context shared by typed engineering calculation modules. */
public final class EngineeringCalculationContext implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String designCaseId;
  private final String simulationFingerprint;
  private final List<String> evidenceReferences;
  private final List<String> standardReferences;
  private final Map<String, String> attributes;

  private EngineeringCalculationContext(Builder builder) {
    designCaseId = textOrEmpty(builder.designCaseId);
    simulationFingerprint = textOrEmpty(builder.simulationFingerprint);
    evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
    standardReferences = Collections.unmodifiableList(new ArrayList<String>(builder.standardReferences));
    attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.attributes));
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getDesignCaseId() {
    return designCaseId;
  }

  public String getSimulationFingerprint() {
    return simulationFingerprint;
  }

  public List<String> getEvidenceReferences() {
    return evidenceReferences;
  }

  public List<String> getStandardReferences() {
    return standardReferences;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("designCaseId", designCaseId);
    result.put("simulationFingerprint", simulationFingerprint);
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("standardReferences", new ArrayList<String>(standardReferences));
    result.put("attributes", new LinkedHashMap<String, String>(attributes));
    return result;
  }

  /** Builder for a controlled calculation context. */
  public static final class Builder {
    private String designCaseId = "";
    private String simulationFingerprint = "";
    private final List<String> evidenceReferences = new ArrayList<String>();
    private final List<String> standardReferences = new ArrayList<String>();
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();

    public Builder designCaseId(String value) {
      designCaseId = textOrEmpty(value);
      return this;
    }

    public Builder simulationFingerprint(String value) {
      simulationFingerprint = textOrEmpty(value);
      return this;
    }

    public Builder addEvidenceReference(String value) {
      addUnique(evidenceReferences, value);
      return this;
    }

    public Builder addStandardReference(String value) {
      addUnique(standardReferences, value);
      return this;
    }

    public Builder attribute(String name, String value) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("attribute name must not be blank");
      }
      attributes.put(name.trim(), textOrEmpty(value));
      return this;
    }

    public EngineeringCalculationContext build() {
      return new EngineeringCalculationContext(this);
    }
  }

  private static void addUnique(List<String> target, String value) {
    String normalized = textOrEmpty(value);
    if (!normalized.isEmpty() && !target.contains(normalized)) {
      target.add(normalized);
    }
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
