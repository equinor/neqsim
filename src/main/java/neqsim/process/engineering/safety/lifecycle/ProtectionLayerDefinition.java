package neqsim.process.engineering.safety.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** User-declared protection layer and the evidence needed before it receives LOPA credit. */
public final class ProtectionLayerDefinition implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Protection-layer category used in the lifecycle handoff. */
  public enum LayerType {
    BPCS, ALARM_AND_OPERATOR, RELIEF, MECHANICAL, EXISTING_SIS, PROCEDURAL, OTHER
  }

  private final String id;
  private final String name;
  private final LayerType type;
  private final double probabilityOfFailureOnDemand;
  private final boolean independentFromInitiatingEvent;
  private final boolean independentFromOtherLayers;
  private final boolean specific;
  private final boolean auditable;
  private final double proofTestIntervalHours;
  private final String evidenceReference;

  private ProtectionLayerDefinition(Builder builder) {
    id = requireText(builder.id, "id");
    name = requireText(builder.name, "name");
    type = builder.type;
    probabilityOfFailureOnDemand = fractionAboveZero(builder.probabilityOfFailureOnDemand,
        "probabilityOfFailureOnDemand");
    independentFromInitiatingEvent = builder.independentFromInitiatingEvent;
    independentFromOtherLayers = builder.independentFromOtherLayers;
    specific = builder.specific;
    auditable = builder.auditable;
    proofTestIntervalHours = builder.proofTestIntervalHours;
    evidenceReference = normalize(builder.evidenceReference);
  }

  public static Builder builder(String id, String name, LayerType type, double probabilityOfFailureOnDemand) {
    return new Builder(id, name, type, probabilityOfFailureOnDemand);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public double getProbabilityOfFailureOnDemand() {
    return probabilityOfFailureOnDemand;
  }

  /** @return true only when every declared IPL eligibility gate has evidence */
  public boolean isCreditEligible() {
    return getCreditFindings().isEmpty();
  }

  /** @return explicit reasons why this layer cannot receive LOPA credit */
  public List<String> getCreditFindings() {
    List<String> findings = new ArrayList<String>();
    if (!independentFromInitiatingEvent) {
      findings.add("not demonstrated independent from the initiating event");
    }
    if (!independentFromOtherLayers) {
      findings.add("not demonstrated independent from other credited layers");
    }
    if (!specific) {
      findings.add("specific action for this scenario is not demonstrated");
    }
    if (!auditable) {
      findings.add("auditable test or inspection is not demonstrated");
    }
    if (!Double.isFinite(proofTestIntervalHours) || proofTestIntervalHours <= 0.0) {
      findings.add("proof-test or inspection interval is not recorded");
    }
    if (evidenceReference.isEmpty()) {
      findings.add("controlled evidence reference is missing");
    }
    return findings;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    map.put("name", name);
    map.put("type", type.name());
    map.put("probabilityOfFailureOnDemand", Double.valueOf(probabilityOfFailureOnDemand));
    map.put("independentFromInitiatingEvent", Boolean.valueOf(independentFromInitiatingEvent));
    map.put("independentFromOtherLayers", Boolean.valueOf(independentFromOtherLayers));
    map.put("specific", Boolean.valueOf(specific));
    map.put("auditable", Boolean.valueOf(auditable));
    map.put("proofTestIntervalHours", Double.valueOf(proofTestIntervalHours));
    map.put("evidenceReference", evidenceReference);
    map.put("creditEligible", Boolean.valueOf(isCreditEligible()));
    map.put("creditFindings", getCreditFindings());
    return map;
  }

  /** Builder for one protection-layer declaration. */
  public static final class Builder {
    private final String id;
    private final String name;
    private final LayerType type;
    private final double probabilityOfFailureOnDemand;
    private boolean independentFromInitiatingEvent;
    private boolean independentFromOtherLayers;
    private boolean specific;
    private boolean auditable;
    private double proofTestIntervalHours = Double.NaN;
    private String evidenceReference = "";

    private Builder(String id, String name, LayerType type, double probabilityOfFailureOnDemand) {
      if (type == null) {
        throw new IllegalArgumentException("type must not be null");
      }
      this.id = id;
      this.name = name;
      this.type = type;
      this.probabilityOfFailureOnDemand = probabilityOfFailureOnDemand;
    }

    public Builder independentFromInitiatingEvent(boolean value) {
      independentFromInitiatingEvent = value;
      return this;
    }

    public Builder independentFromOtherLayers(boolean value) {
      independentFromOtherLayers = value;
      return this;
    }

    public Builder specific(boolean value) {
      specific = value;
      return this;
    }

    public Builder auditable(boolean value) {
      auditable = value;
      return this;
    }

    public Builder proofTestIntervalHours(double value) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException("proofTestIntervalHours must be finite and positive");
      }
      proofTestIntervalHours = value;
      return this;
    }

    public Builder evidenceReference(String value) {
      evidenceReference = requireText(value, "evidenceReference");
      return this;
    }

    public ProtectionLayerDefinition build() {
      return new ProtectionLayerDefinition(this);
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double fractionAboveZero(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      throw new IllegalArgumentException(field + " must be in (0, 1]");
    }
    return value;
  }
}
