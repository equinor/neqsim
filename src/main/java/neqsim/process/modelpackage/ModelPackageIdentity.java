package neqsim.process.modelpackage;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable asset, model, and revision identity carried by a NeqSim model package. */
public final class ModelPackageIdentity implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String assetId;
  private final String facilityId;
  private final String modelId;
  private final String modelType;
  private final String revision;
  private final String parentRevision;
  private final String lifecyclePhase;
  private final String responsibleParty;

  public ModelPackageIdentity(String assetId, String facilityId, String modelId, String modelType, String revision,
      String parentRevision, String lifecyclePhase, String responsibleParty) {
    this.assetId = requireText(assetId, "assetId");
    this.facilityId = text(facilityId);
    this.modelId = requireText(modelId, "modelId");
    this.modelType = requireText(modelType, "modelType");
    this.revision = requireText(revision, "revision");
    this.parentRevision = text(parentRevision);
    this.lifecyclePhase = requireText(lifecyclePhase, "lifecyclePhase");
    this.responsibleParty = text(responsibleParty);
  }

  public String getAssetId() {
    return assetId;
  }

  public String getFacilityId() {
    return facilityId;
  }

  public String getModelId() {
    return modelId;
  }

  public String getModelType() {
    return modelType;
  }

  public String getRevision() {
    return revision;
  }

  public String getParentRevision() {
    return parentRevision;
  }

  public String getLifecyclePhase() {
    return lifecyclePhase;
  }

  public String getResponsibleParty() {
    return responsibleParty;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("assetId", assetId);
    result.put("facilityId", facilityId);
    result.put("modelId", modelId);
    result.put("modelType", modelType);
    result.put("revision", revision);
    result.put("parentRevision", parentRevision);
    result.put("lifecyclePhase", lifecyclePhase);
    result.put("responsibleParty", responsibleParty);
    return result;
  }

  static ModelPackageIdentity fromMap(Map<String, Object> value) {
    if (value == null) {
      throw new IllegalArgumentException("identity must not be null");
    }
    return new ModelPackageIdentity(string(value, "assetId"), string(value, "facilityId"), string(value, "modelId"),
        string(value, "modelType"), string(value, "revision"), string(value, "parentRevision"),
        string(value, "lifecyclePhase"), string(value, "responsibleParty"));
  }

  private static String string(Map<String, Object> value, String key) {
    Object item = value.get(key);
    return item == null ? "" : String.valueOf(item);
  }

  private static String requireText(String value, String field) {
    String normalized = text(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }
}
