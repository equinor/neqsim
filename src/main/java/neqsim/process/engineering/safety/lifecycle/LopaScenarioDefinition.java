package neqsim.process.engineering.safety.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Traceable HAZOP scenario and user-supplied frequency basis for LOPA. */
public final class LopaScenarioDefinition implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String scenarioId;
  private final String hazopNodeId;
  private final String hazopDeviationId;
  private final String equipmentTag;
  private final String initiatingEvent;
  private final String consequence;
  private final double initiatingEventFrequencyPerYear;
  private final double targetFrequencyPerYear;
  private final String frequencyBasisReference;
  private final List<ProtectionLayerDefinition> protectionLayers;

  private LopaScenarioDefinition(Builder builder) {
    scenarioId = requireText(builder.scenarioId, "scenarioId");
    hazopNodeId = requireText(builder.hazopNodeId, "hazopNodeId");
    hazopDeviationId = requireText(builder.hazopDeviationId, "hazopDeviationId");
    equipmentTag = requireText(builder.equipmentTag, "equipmentTag");
    initiatingEvent = requireText(builder.initiatingEvent, "initiatingEvent");
    consequence = requireText(builder.consequence, "consequence");
    initiatingEventFrequencyPerYear = positive(builder.initiatingEventFrequencyPerYear,
        "initiatingEventFrequencyPerYear");
    targetFrequencyPerYear = positive(builder.targetFrequencyPerYear, "targetFrequencyPerYear");
    frequencyBasisReference = requireText(builder.frequencyBasisReference, "frequencyBasisReference");
    protectionLayers = Collections.unmodifiableList(new ArrayList<ProtectionLayerDefinition>(builder.protectionLayers));
  }

  public static Builder builder(String scenarioId, String hazopNodeId, String hazopDeviationId) {
    return new Builder(scenarioId, hazopNodeId, hazopDeviationId);
  }

  public String getScenarioId() {
    return scenarioId;
  }

  public String getHazopNodeId() {
    return hazopNodeId;
  }

  public String getHazopDeviationId() {
    return hazopDeviationId;
  }

  public String getEquipmentTag() {
    return equipmentTag;
  }

  public String getInitiatingEvent() {
    return initiatingEvent;
  }

  public String getConsequence() {
    return consequence;
  }

  public double getInitiatingEventFrequencyPerYear() {
    return initiatingEventFrequencyPerYear;
  }

  public double getTargetFrequencyPerYear() {
    return targetFrequencyPerYear;
  }

  public String getFrequencyBasisReference() {
    return frequencyBasisReference;
  }

  public List<ProtectionLayerDefinition> getProtectionLayers() {
    return protectionLayers;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("scenarioId", scenarioId);
    map.put("hazopNodeId", hazopNodeId);
    map.put("hazopDeviationId", hazopDeviationId);
    map.put("equipmentTag", equipmentTag);
    map.put("initiatingEvent", initiatingEvent);
    map.put("consequence", consequence);
    map.put("initiatingEventFrequencyPerYear", Double.valueOf(initiatingEventFrequencyPerYear));
    map.put("targetFrequencyPerYear", Double.valueOf(targetFrequencyPerYear));
    map.put("frequencyBasisReference", frequencyBasisReference);
    List<Map<String, Object>> layers = new ArrayList<Map<String, Object>>();
    for (ProtectionLayerDefinition layer : protectionLayers) {
      layers.add(layer.toMap());
    }
    map.put("protectionLayers", layers);
    return map;
  }

  /** Builder for one LOPA scenario. */
  public static final class Builder {
    private final String scenarioId;
    private final String hazopNodeId;
    private final String hazopDeviationId;
    private String equipmentTag;
    private String initiatingEvent;
    private String consequence;
    private double initiatingEventFrequencyPerYear;
    private double targetFrequencyPerYear;
    private String frequencyBasisReference;
    private final List<ProtectionLayerDefinition> protectionLayers = new ArrayList<ProtectionLayerDefinition>();

    private Builder(String scenarioId, String hazopNodeId, String hazopDeviationId) {
      this.scenarioId = scenarioId;
      this.hazopNodeId = hazopNodeId;
      this.hazopDeviationId = hazopDeviationId;
    }

    public Builder equipmentTag(String value) {
      equipmentTag = value;
      return this;
    }

    public Builder initiatingEvent(String value, double frequencyPerYear) {
      initiatingEvent = value;
      initiatingEventFrequencyPerYear = frequencyPerYear;
      return this;
    }

    public Builder consequence(String value) {
      consequence = value;
      return this;
    }

    public Builder targetFrequencyPerYear(double value) {
      targetFrequencyPerYear = value;
      return this;
    }

    public Builder frequencyBasisReference(String value) {
      frequencyBasisReference = value;
      return this;
    }

    public Builder addProtectionLayer(ProtectionLayerDefinition value) {
      if (value == null) {
        throw new IllegalArgumentException("protectionLayer must not be null");
      }
      for (ProtectionLayerDefinition existing : protectionLayers) {
        if (existing.getId().equals(value.getId())) {
          throw new IllegalArgumentException("duplicate protection layer " + value.getId());
        }
      }
      protectionLayers.add(value);
      return this;
    }

    public LopaScenarioDefinition build() {
      return new LopaScenarioDefinition(this);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }
}
