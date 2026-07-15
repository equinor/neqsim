package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A selectable compressor definition containing metadata and a thermal-model template. */
public class CompressorCatalogEntry implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String id;
  private String manufacturer;
  private String model;
  private String compressorType = "centrifugal";
  private int numberOfStages = 1;
  private CompressorThermalModel thermalModel;
  private final Map<String, String> requiredParameters = new LinkedHashMap<String, String>();
  private final Map<String, String> references = new LinkedHashMap<String, String>();

  /** No-argument constructor for JSON deserialization. */
  public CompressorCatalogEntry() {}

  /**
   * Create a catalog entry.
   *
   * @param id unique catalog identifier
   * @param manufacturer manufacturer or generic-template owner
   * @param model model designation
   */
  public CompressorCatalogEntry(String id, String manufacturer, String model) {
    setId(id);
    setManufacturer(manufacturer);
    setModel(model);
  }

  /** @return unique catalog identifier */
  public String getId() {
    return id;
  }

  /** @param id unique non-empty catalog identifier */
  public void setId(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("catalog entry id must not be empty");
    }
    this.id = id;
  }

  /** @return manufacturer */
  public String getManufacturer() {
    return manufacturer;
  }

  /** @param manufacturer manufacturer or template owner */
  public void setManufacturer(String manufacturer) {
    if (manufacturer == null || manufacturer.trim().isEmpty()) {
      throw new IllegalArgumentException("manufacturer must not be empty");
    }
    this.manufacturer = manufacturer;
  }

  /** @return model designation */
  public String getModel() {
    return model;
  }

  /** @param model model designation */
  public void setModel(String model) {
    if (model == null || model.trim().isEmpty()) {
      throw new IllegalArgumentException("model must not be empty");
    }
    this.model = model;
  }

  /** @return compressor type */
  public String getCompressorType() {
    return compressorType;
  }

  /** @param compressorType compressor type */
  public void setCompressorType(String compressorType) {
    if (compressorType == null || compressorType.trim().isEmpty()) {
      throw new IllegalArgumentException("compressor type must not be empty");
    }
    this.compressorType = compressorType;
  }

  /** @return stage count */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /** @param numberOfStages positive stage count */
  public void setNumberOfStages(int numberOfStages) {
    if (numberOfStages <= 0) {
      throw new IllegalArgumentException("number of stages must be positive");
    }
    this.numberOfStages = numberOfStages;
  }

  /** @return thermal-model template */
  public CompressorThermalModel getThermalModel() {
    return thermalModel;
  }

  /** @param thermalModel thermal-model template */
  public void setThermalModel(CompressorThermalModel thermalModel) {
    this.thermalModel = thermalModel;
  }

  /**
   * Document a machine-specific input needed to replace a screening assumption.
   *
   * @param parameterName parameter name
   * @param description engineering description and expected unit
   * @return this entry
   */
  public CompressorCatalogEntry requireParameter(String parameterName, String description) {
    if (parameterName == null || parameterName.trim().isEmpty()) {
      throw new IllegalArgumentException("parameter name must not be empty");
    }
    requiredParameters.put(parameterName, description);
    return this;
  }

  /** @return immutable machine-specific parameter descriptions */
  public Map<String, String> getRequiredParameters() {
    return Collections.unmodifiableMap(requiredParameters);
  }

  /**
   * Add a traceable source for the entry.
   *
   * @param name short source name
   * @param reference citation or URL
   * @return this entry
   */
  public CompressorCatalogEntry addReference(String name, String reference) {
    references.put(name, reference);
    return this;
  }

  /** @return immutable source map */
  public Map<String, String> getReferences() {
    return Collections.unmodifiableMap(references);
  }

  /**
   * Apply independent model instances to a compressor.
   *
   * @param compressor target compressor
   */
  public void applyTo(Compressor compressor) {
    if (compressor == null) {
      throw new IllegalArgumentException("compressor must not be null");
    }
    compressor.setThermalModel(thermalModel == null ? null : thermalModel.copy());
  }
}
