package neqsim.process.safety.depressurization;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.rupture.SafetyStudyReadiness;

/**
 * Source-traceable line and equipment list evidence for dynamic blowdown and flare studies.
 *
 * <p>
 * The class records the structured line-list and equipment-list rows extracted from P&amp;ID, E3D, or technical
 * database evidence. It does not perform document extraction itself; it gives document-reading agents a stable handoff
 * contract before NeqSim builds a transient depressurization and flare-load model.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class LineEquipmentListEvidence implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String sourceId;
  private final String revision;
  private final boolean lineListReviewed;
  private final boolean equipmentListReviewed;
  private final List<LineItem> lineItems;
  private final List<EquipmentItem> equipmentItems;
  private final List<String> missingFields;

  /**
   * Creates line/equipment-list evidence.
   *
   * @param builder populated builder
   */
  private LineEquipmentListEvidence(Builder builder) {
    builder.validate();
    this.sourceId = clean(builder.sourceId);
    this.revision = clean(builder.revision);
    this.lineListReviewed = builder.lineListReviewed;
    this.equipmentListReviewed = builder.equipmentListReviewed;
    this.lineItems = Collections.unmodifiableList(new ArrayList<LineItem>(builder.lineItems));
    this.equipmentItems = Collections.unmodifiableList(new ArrayList<EquipmentItem>(builder.equipmentItems));
    this.missingFields = immutableText(builder.missingFields);
  }

  /**
   * Creates a builder.
   *
   * @param sourceId document id, extract id, or evidence package id
   * @return evidence builder
   */
  public static Builder builder(String sourceId) {
    return new Builder(sourceId);
  }

  /**
   * Checks if the evidence is ready to support model construction.
   *
   * @return true when line and equipment rows are present, reviewed, and have no missing required fields
   */
  public boolean isSimulationReady() {
    return lineListReviewed && equipmentListReviewed && !lineItems.isEmpty() && !equipmentItems.isEmpty()
	&& missingFields.isEmpty();
  }

  /**
   * Gets line-list rows.
   *
   * @return immutable line-list rows
   */
  public List<LineItem> getLineItems() {
    return lineItems;
  }

  /**
   * Gets equipment-list rows.
   *
   * @return immutable equipment-list rows
   */
  public List<EquipmentItem> getEquipmentItems() {
    return equipmentItems;
  }

  /**
   * Creates a readiness verdict for the line/equipment-list evidence.
   *
   * @return readiness result
   */
  public SafetyStudyReadiness readiness() {
    SafetyStudyReadiness.Builder readiness = SafetyStudyReadiness.builder();
    if (!lineListReviewed) {
      readiness.addWarning("line_list", "Line-list evidence has not been marked reviewed.",
	  "Review source drawing, E3D, and piping-specification line rows before design-grade dynamic blowdown modelling.");
    }
    if (!equipmentListReviewed) {
      readiness.addWarning("equipment_list", "Equipment-list evidence has not been marked reviewed.",
	  "Review vessel/equipment inventory, protected equipment tags, and design conditions.");
    }
    if (lineItems.isEmpty()) {
      readiness.addWarning("line_list", "No line-list rows are present.",
	  "Extract line ids, from/to nodes, sizes, wall thicknesses, and flare/blowdown connections.");
    }
    if (equipmentItems.isEmpty()) {
      readiness.addWarning("equipment_list", "No equipment-list rows are present.",
	  "Extract protected vessel/equipment tags, volumes, design pressure, and operating state.");
    }
    if (!missingFields.isEmpty()) {
      readiness.addWarning("line_equipment_list", "Required extracted fields are missing: " + missingFields,
	  "Close missing rows or keep them as explicit dynamic study gaps.");
    }
    return readiness.build();
  }

  /**
   * Converts evidence to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "line_equipment_list_evidence.v1");
    map.put("sourceId", sourceId);
    map.put("revision", revision);
    map.put("lineListReviewed", Boolean.valueOf(lineListReviewed));
    map.put("equipmentListReviewed", Boolean.valueOf(equipmentListReviewed));
    map.put("simulationReady", Boolean.valueOf(isSimulationReady()));
    List<Map<String, Object>> lineMaps = new ArrayList<Map<String, Object>>();
    for (LineItem item : lineItems) {
      lineMaps.add(item.toMap());
    }
    map.put("lineItems", lineMaps);
    List<Map<String, Object>> equipmentMaps = new ArrayList<Map<String, Object>>();
    for (EquipmentItem item : equipmentItems) {
      equipmentMaps.add(item.toMap());
    }
    map.put("equipmentItems", equipmentMaps);
    map.put("missingFields", missingFields);
    map.put("readiness", readiness().toMap());
    return map;
  }

  /**
   * Converts evidence to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
  }

  /**
   * Copies text values.
   *
   * @param values values to copy
   * @return immutable copy
   */
  private static List<String> immutableText(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  /**
   * Normalizes nullable text.
   *
   * @param value text value
   * @return trimmed text or empty string
   */
  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Converts finite values to boxed values and non-finite values to null.
   *
   * @param value numeric value
   * @return boxed value or null
   */
  private static Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /** One extracted line-list row. */
  public static final class LineItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String lineId;
    private final String fromTag;
    private final String toTag;
    private final double nominalDiameterInches;
    private final double internalDiameterM;
    private final double wallThicknessM;
    private final double lengthM;
    private final String pipeClass;
    private final String material;

    /**
     * Creates a line item.
     *
     * @param lineId line id or line number
     * @param fromTag upstream node or equipment tag
     * @param toTag downstream node or equipment tag
     * @param nominalDiameterInches nominal pipe size in inches
     * @param internalDiameterM internal pipe diameter in m
     * @param wallThicknessM wall thickness in m
     * @param lengthM segment length in m
     * @param pipeClass piping class or PCS id
     * @param material material grade or MDS basis
     */
    public LineItem(String lineId, String fromTag, String toTag, double nominalDiameterInches, double internalDiameterM,
	double wallThicknessM, double lengthM, String pipeClass, String material) {
      if (clean(lineId).isEmpty()) {
	throw new IllegalArgumentException("lineId must not be empty");
      }
      this.lineId = clean(lineId);
      this.fromTag = clean(fromTag);
      this.toTag = clean(toTag);
      this.nominalDiameterInches = nominalDiameterInches;
      this.internalDiameterM = internalDiameterM;
      this.wallThicknessM = wallThicknessM;
      this.lengthM = lengthM;
      this.pipeClass = clean(pipeClass);
      this.material = clean(material);
    }

    /**
     * Gets line id.
     *
     * @return line id
     */
    public String getLineId() {
      return lineId;
    }

    /**
     * Converts line item to a map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("lineId", lineId);
      map.put("fromTag", fromTag);
      map.put("toTag", toTag);
      map.put("nominalDiameterInches", finiteOrNull(nominalDiameterInches));
      map.put("internalDiameterM", finiteOrNull(internalDiameterM));
      map.put("wallThicknessM", finiteOrNull(wallThicknessM));
      map.put("lengthM", finiteOrNull(lengthM));
      map.put("pipeClass", pipeClass);
      map.put("material", material);
      return map;
    }
  }

  /** One extracted equipment-list row. */
  public static final class EquipmentItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String equipmentTag;
    private final String equipmentType;
    private final double volumeM3;
    private final double designPressureBara;
    private final double operatingPressureBara;
    private final double operatingTemperatureK;

    /**
     * Creates an equipment item.
     *
     * @param equipmentTag equipment tag
     * @param equipmentType equipment type
     * @param volumeM3 internal volume in m3
     * @param designPressureBara design pressure in bara
     * @param operatingPressureBara operating pressure in bara
     * @param operatingTemperatureK operating temperature in K
     */
    public EquipmentItem(String equipmentTag, String equipmentType, double volumeM3, double designPressureBara,
	double operatingPressureBara, double operatingTemperatureK) {
      if (clean(equipmentTag).isEmpty()) {
	throw new IllegalArgumentException("equipmentTag must not be empty");
      }
      this.equipmentTag = clean(equipmentTag);
      this.equipmentType = clean(equipmentType);
      this.volumeM3 = volumeM3;
      this.designPressureBara = designPressureBara;
      this.operatingPressureBara = operatingPressureBara;
      this.operatingTemperatureK = operatingTemperatureK;
    }

    /**
     * Gets equipment tag.
     *
     * @return equipment tag
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Converts equipment item to a map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("equipmentTag", equipmentTag);
      map.put("equipmentType", equipmentType);
      map.put("volumeM3", finiteOrNull(volumeM3));
      map.put("designPressureBara", finiteOrNull(designPressureBara));
      map.put("operatingPressureBara", finiteOrNull(operatingPressureBara));
      map.put("operatingTemperatureK", finiteOrNull(operatingTemperatureK));
      return map;
    }
  }

  /** Builder for {@link LineEquipmentListEvidence}. */
  public static final class Builder {
    private final String sourceId;
    private String revision = "";
    private boolean lineListReviewed;
    private boolean equipmentListReviewed;
    private final List<LineItem> lineItems = new ArrayList<LineItem>();
    private final List<EquipmentItem> equipmentItems = new ArrayList<EquipmentItem>();
    private final List<String> missingFields = new ArrayList<String>();

    /**
     * Creates a builder.
     *
     * @param sourceId source id
     */
    private Builder(String sourceId) {
      this.sourceId = sourceId;
    }

    /**
     * Sets revision.
     *
     * @param revision revision text
     * @return this builder
     */
    public Builder revision(String revision) {
      this.revision = revision;
      return this;
    }

    /**
     * Marks line-list evidence reviewed.
     *
     * @param lineListReviewed true when reviewed
     * @return this builder
     */
    public Builder lineListReviewed(boolean lineListReviewed) {
      this.lineListReviewed = lineListReviewed;
      return this;
    }

    /**
     * Marks equipment-list evidence reviewed.
     *
     * @param equipmentListReviewed true when reviewed
     * @return this builder
     */
    public Builder equipmentListReviewed(boolean equipmentListReviewed) {
      this.equipmentListReviewed = equipmentListReviewed;
      return this;
    }

    /**
     * Adds a line-list row.
     *
     * @param lineId line id or number
     * @param fromTag upstream node tag
     * @param toTag downstream node tag
     * @param nominalDiameterInches nominal pipe size in inches
     * @param internalDiameterM internal diameter in m
     * @param wallThicknessM wall thickness in m
     * @param lengthM line length in m
     * @param pipeClass pipe class or PCS id
     * @param material material text
     * @return this builder
     */
    public Builder addLine(String lineId, String fromTag, String toTag, double nominalDiameterInches,
	double internalDiameterM, double wallThicknessM, double lengthM, String pipeClass, String material) {
      lineItems.add(new LineItem(lineId, fromTag, toTag, nominalDiameterInches, internalDiameterM, wallThicknessM,
	  lengthM, pipeClass, material));
      return this;
    }

    /**
     * Adds an equipment-list row.
     *
     * @param equipmentTag equipment tag
     * @param equipmentType equipment type
     * @param volumeM3 internal volume in m3
     * @param designPressureBara design pressure in bara
     * @param operatingPressureBara operating pressure in bara
     * @param operatingTemperatureK operating temperature in K
     * @return this builder
     */
    public Builder addEquipment(String equipmentTag, String equipmentType, double volumeM3, double designPressureBara,
	double operatingPressureBara, double operatingTemperatureK) {
      equipmentItems.add(new EquipmentItem(equipmentTag, equipmentType, volumeM3, designPressureBara,
	  operatingPressureBara, operatingTemperatureK));
      return this;
    }

    /**
     * Adds a missing field name.
     *
     * @param field field text
     * @return this builder
     */
    public Builder addMissingField(String field) {
      if (!clean(field).isEmpty()) {
	missingFields.add(clean(field));
      }
      return this;
    }

    /**
     * Builds evidence.
     *
     * @return line/equipment-list evidence
     */
    public LineEquipmentListEvidence build() {
      return new LineEquipmentListEvidence(this);
    }

    /**
     * Validates builder state.
     *
     * @throws IllegalArgumentException if source id is missing
     */
    private void validate() {
      if (clean(sourceId).isEmpty()) {
	throw new IllegalArgumentException("sourceId must not be empty");
      }
    }
  }
}
