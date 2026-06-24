package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Source-traceable evidence reference for process-safety calculations.
 *
 * <p>
 * The reference is intentionally compact and JSON-friendly so document-management, piping-specification, plant-data,
 * and material-certificate agents can attach the source of each important input without moving the source document
 * itself into the NeqSim result. Values are stored as text to avoid serializing arbitrary external objects.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class SafetyEvidenceReference implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String sourceSystem;
  private final String documentId;
  private final String documentTitle;
  private final String revision;
  private final String location;
  private final String fieldName;
  private final String valueText;
  private final String unit;
  private final String status;
  private final double confidence;
  private final String notes;

  /**
   * Creates an evidence reference.
   *
   * @param builder populated builder
   */
  private SafetyEvidenceReference(Builder builder) {
    builder.validate();
    this.sourceSystem = clean(builder.sourceSystem);
    this.documentId = clean(builder.documentId);
    this.documentTitle = clean(builder.documentTitle);
    this.revision = clean(builder.revision);
    this.location = clean(builder.location);
    this.fieldName = clean(builder.fieldName);
    this.valueText = clean(builder.valueText);
    this.unit = clean(builder.unit);
    this.status = clean(builder.status);
    this.confidence = builder.confidence;
    this.notes = clean(builder.notes);
  }

  /**
   * Creates a builder.
   *
   * @param sourceSystem source system such as a document register, piping specification, historian, or NeqSim
   * @param fieldName model input or evidence field name
   * @return evidence reference builder
   */
  public static Builder builder(String sourceSystem, String fieldName) {
    return new Builder(sourceSystem, fieldName);
  }

  /**
   * Gets the source system.
   *
   * @return source system text
   */
  public String getSourceSystem() {
    return sourceSystem;
  }

  /**
   * Gets the evidence field name.
   *
   * @return field name
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * Gets the evidence status.
   *
   * @return status text
   */
  public String getStatus() {
    return status;
  }

  /**
   * Converts the reference to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("sourceSystem", sourceSystem);
    map.put("documentId", documentId);
    map.put("documentTitle", documentTitle);
    map.put("revision", revision);
    map.put("location", location);
    map.put("fieldName", fieldName);
    map.put("valueText", valueText);
    map.put("unit", unit);
    map.put("status", status);
    map.put("confidence", confidence);
    map.put("notes", notes);
    return map;
  }

  /**
   * Converts the reference to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
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

  /** Builder for {@link SafetyEvidenceReference}. */
  public static final class Builder {
    private final String sourceSystem;
    private final String fieldName;
    private String documentId = "";
    private String documentTitle = "";
    private String revision = "";
    private String location = "";
    private String valueText = "";
    private String unit = "";
    private String status = "planned_not_fetched";
    private double confidence = 0.0;
    private String notes = "";

    /**
     * Creates a builder.
     *
     * @param sourceSystem source system text
     * @param fieldName model field name
     */
    private Builder(String sourceSystem, String fieldName) {
      this.sourceSystem = sourceSystem;
      this.fieldName = fieldName;
    }

    /**
     * Sets document id.
     *
     * @param documentId document id or tag
     * @return this builder
     */
    public Builder documentId(String documentId) {
      this.documentId = documentId;
      return this;
    }

    /**
     * Sets document title.
     *
     * @param documentTitle document title
     * @return this builder
     */
    public Builder documentTitle(String documentTitle) {
      this.documentTitle = documentTitle;
      return this;
    }

    /**
     * Sets document revision.
     *
     * @param revision revision text
     * @return this builder
     */
    public Builder revision(String revision) {
      this.revision = revision;
      return this;
    }

    /**
     * Sets source location.
     *
     * @param location page, row, URL, or file path location
     * @return this builder
     */
    public Builder location(String location) {
      this.location = location;
      return this;
    }

    /**
     * Sets evidence value text.
     *
     * @param valueText value as displayed in the source
     * @return this builder
     */
    public Builder valueText(String valueText) {
      this.valueText = valueText;
      return this;
    }

    /**
     * Sets evidence unit.
     *
     * @param unit unit text
     * @return this builder
     */
    public Builder unit(String unit) {
      this.unit = unit;
      return this;
    }

    /**
     * Sets evidence status.
     *
     * @param status status such as verified, extracted, inferred, assumed, or planned_not_fetched
     * @return this builder
     */
    public Builder status(String status) {
      this.status = status;
      return this;
    }

    /**
     * Sets confidence.
     *
     * @param confidence confidence from 0 to 1
     * @return this builder
     */
    public Builder confidence(double confidence) {
      this.confidence = confidence;
      return this;
    }

    /**
     * Sets notes.
     *
     * @param notes review note
     * @return this builder
     */
    public Builder notes(String notes) {
      this.notes = notes;
      return this;
    }

    /**
     * Builds the evidence reference.
     *
     * @return evidence reference
     */
    public SafetyEvidenceReference build() {
      return new SafetyEvidenceReference(this);
    }

    /**
     * Validates builder state.
     *
     * @throws IllegalArgumentException if required fields or confidence are invalid
     */
    private void validate() {
      if (clean(sourceSystem).isEmpty()) {
	throw new IllegalArgumentException("sourceSystem must not be empty");
      }
      if (clean(fieldName).isEmpty()) {
	throw new IllegalArgumentException("fieldName must not be empty");
      }
      if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence) || Double.isInfinite(confidence)) {
	throw new IllegalArgumentException("confidence must be between 0 and 1");
      }
    }
  }
}
