package neqsim.process.materials;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One equipment, piping, line-list, or asset item to evaluate in a materials review.
 *
 * <p>
 * The item combines tag identity, equipment type, current or proposed material selection, service
 * envelope, source references, and free-form metadata from STID or project technical databases.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialReviewItem implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Equipment, line, or asset tag. */
  private String tag = "";

  /** Equipment type such as Pipeline, TopsidePiping, Vessel, HeatExchanger, or Compressor. */
  private String equipmentType = "";

  /** Existing or proposed material grade/class from the technical database. */
  private String existingMaterial = "";

  /** Service envelope used for mechanism selection and calculations. */
  private MaterialServiceEnvelope serviceEnvelope = new MaterialServiceEnvelope();

  /** Traceable references to source documents, rows, tags, or database records. */
  private final List<String> sourceReferences = new ArrayList<String>();

  /** Additional metadata not interpreted by the engine. */
  private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

  /**
   * Creates an empty material review item.
   */
  public MaterialReviewItem() {}

  /**
   * Creates a review item from a generic map.
   *
   * @param source source map containing tag, equipmentType, existingMaterial, service, and metadata
   * @return populated review item
   */
  @SuppressWarnings("unchecked")
  public static MaterialReviewItem fromMap(Map<String, Object> source) {
    MaterialReviewItem item = new MaterialReviewItem();
    if (source == null) {
      return item;
    }
    item.setTag(firstString(source, "tag", "lineNumber", "equipmentTag", "name"));
    item.setEquipmentType(firstString(source, "equipmentType", "type", "assetType"));
    item.setExistingMaterial(firstString(source, "existingMaterial", "material", "materialGrade",
        "materialClass", "mds"));
    Object service = firstObject(source, "service", "serviceEnvelope", "environment");
    if (service instanceof Map<?, ?>) {
      item.setServiceEnvelope(MaterialServiceEnvelope.fromMap((Map<String, Object>) service));
    }
    Object references = firstObject(source, "sourceReferences", "sourceRefs", "evidenceRefs");
    if (references instanceof List<?>) {
      for (Object reference : (List<?>) references) {
        if (reference != null) {
          item.addSourceReference(String.valueOf(reference));
        }
      }
    } else if (references != null) {
      item.addSourceReference(String.valueOf(references));
    }
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (!isCoreKey(entry.getKey())) {
        item.putMetadata(entry.getKey(), entry.getValue());
      }
    }
    return item;
  }

  /**
   * Gets the tag.
   *
   * @return tag string
   */
  public String getTag() {
    return tag;
  }

  /**
   * Sets the tag.
   *
   * @param tag equipment or line tag
   * @return this item for fluent construction
   */
  public MaterialReviewItem setTag(String tag) {
    this.tag = tag == null ? "" : tag;
    return this;
  }

  /**
   * Gets the equipment type.
   *
   * @return equipment type string
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Sets the equipment type.
   *
   * @param equipmentType equipment type string
   * @return this item for fluent construction
   */
  public MaterialReviewItem setEquipmentType(String equipmentType) {
    this.equipmentType = equipmentType == null ? "" : equipmentType;
    return this;
  }

  /**
   * Gets the existing material.
   *
   * @return material grade/class string
   */
  public String getExistingMaterial() {
    return existingMaterial;
  }

  /**
   * Sets the existing material.
   *
   * @param existingMaterial material grade/class string
   * @return this item for fluent construction
   */
  public MaterialReviewItem setExistingMaterial(String existingMaterial) {
    this.existingMaterial = existingMaterial == null ? "" : existingMaterial;
    return this;
  }

  /**
   * Gets the service envelope.
   *
   * @return service envelope
   */
  public MaterialServiceEnvelope getServiceEnvelope() {
    return serviceEnvelope;
  }

  /**
   * Sets the service envelope.
   *
   * @param serviceEnvelope service envelope to assign
   * @return this item for fluent construction
   */
  public MaterialReviewItem setServiceEnvelope(MaterialServiceEnvelope serviceEnvelope) {
    this.serviceEnvelope =
        serviceEnvelope == null ? new MaterialServiceEnvelope() : serviceEnvelope;
    return this;
  }

  /**
   * Adds a source reference.
   *
   * @param sourceReference source reference string
   * @return this item for fluent construction
   */
  public MaterialReviewItem addSourceReference(String sourceReference) {
    if (sourceReference != null && !sourceReference.trim().isEmpty()) {
      sourceReferences.add(sourceReference);
    }
    return this;
  }

  /**
   * Gets source references.
   *
   * @return immutable source-reference list
   */
  public List<String> getSourceReferences() {
    return Collections.unmodifiableList(sourceReferences);
  }

  /**
   * Adds metadata to the item.
   *
   * @param key metadata key
   * @param value metadata value
   * @return this item for fluent construction
   */
  public MaterialReviewItem putMetadata(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      metadata.put(key, value);
    }
    return this;
  }

  /**
   * Gets metadata.
   *
   * @return immutable metadata map
   */
  public Map<String, Object> getMetadata() {
    return Collections.unmodifiableMap(metadata);
  }

  /**
   * Merges another item into this item, preferring non-empty values from the other item.
   *
   * @param other item whose values should update this item
   */
  public void mergeFrom(MaterialReviewItem other) {
    if (other == null) {
      return;
    }
    if (!other.getEquipmentType().isEmpty()) {
      setEquipmentType(other.getEquipmentType());
    }
    if (!other.getExistingMaterial().isEmpty()) {
      setExistingMaterial(other.getExistingMaterial());
    }
    for (Map.Entry<String, Object> entry : other.getServiceEnvelope().getValues().entrySet()) {
      serviceEnvelope.set(entry.getKey(), entry.getValue());
    }
    for (String reference : other.getSourceReferences()) {
      addSourceReference(reference);
    }
    for (Map.Entry<String, Object> entry : other.getMetadata().entrySet()) {
      putMetadata(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Converts this item to a JSON-ready map.
   *
   * @return map representation of the item
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("tag", tag);
    map.put("equipmentType", equipmentType);
    map.put("existingMaterial", existingMaterial);
    map.put("service", serviceEnvelope.toMap());
    map.put("sourceReferences", new ArrayList<String>(sourceReferences));
    if (!metadata.isEmpty()) {
      map.put("metadata", new LinkedHashMap<String, Object>(metadata));
    }
    return map;
  }

  /**
   * Returns the first non-empty string value for a list of keys.
   *
   * @param source source map
   * @param keys keys to test
   * @return first non-empty value, or an empty string
   */
  private static String firstString(Map<String, Object> source, String... keys) {
    Object value = firstObject(source, keys);
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * Returns the first non-null object value for a list of keys.
   *
   * @param source source map
   * @param keys keys to test
   * @return first non-null value, or null
   */
  private static Object firstObject(Map<String, Object> source, String... keys) {
    for (String key : keys) {
      if (source.containsKey(key) && source.get(key) != null) {
        return source.get(key);
      }
    }
    return null;
  }

  /**
   * Tests whether a key is interpreted directly by the item.
   *
   * @param key key name
   * @return true if the key is a core item key
   */
  private static boolean isCoreKey(String key) {
    return "tag".equals(key) || "lineNumber".equals(key) || "equipmentTag".equals(key)
        || "name".equals(key) || "equipmentType".equals(key) || "type".equals(key)
        || "assetType".equals(key) || "existingMaterial".equals(key) || "material".equals(key)
        || "materialGrade".equals(key) || "materialClass".equals(key) || "mds".equals(key)
        || "service".equals(key) || "serviceEnvelope".equals(key) || "environment".equals(key)
        || "sourceReferences".equals(key) || "sourceRefs".equals(key) || "evidenceRefs".equals(key);
  }
}
