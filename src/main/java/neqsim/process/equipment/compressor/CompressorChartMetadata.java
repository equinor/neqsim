package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Descriptive metadata for a single compressor performance chart held in a {@link CompressorChartLibrary}.
 *
 * <p>
 * The metadata makes a chart self-describing so that a library of vendor curves (a "chart database") can be browsed,
 * filtered and selected professionally: which casing/model the chart belongs to, which service and tag it was issued
 * for, the source document, whether it is an expected/as-tested/generated curve, and the reference (basis) conditions
 * the curve was measured at.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class CompressorChartMetadata implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Origin of the curve data. */
  public enum CurveType {
    /** Vendor expected/predicted performance curve (design). */
    EXPECTED,
    /** Vendor as-tested (shop test) performance curve. */
    AS_TESTED,
    /** NeqSim-generated curve anchored to a rated point. */
    GENERATED,
    /** Curve fitted to field / historian data. */
    FIELD_FITTED,
    /** Unspecified origin. */
    UNSPECIFIED;
  }

  private String casingModel = "";
  private String service = "";
  private String tag = "";
  private String documentReference = "";
  private CurveType curveType = CurveType.UNSPECIFIED;
  private double molecularWeight = Double.NaN;
  private double referenceTemperature = Double.NaN;
  private double referencePressure = Double.NaN;
  private double compressibilityZ = Double.NaN;
  private String description = "";

  /**
   * Default constructor.
   */
  public CompressorChartMetadata() {
  }

  /**
   * Convenience constructor for the most common descriptive fields.
   *
   * @param casingModel the compressor casing/model, e.g. "BCL 405/B"
   * @param service the service description, e.g. "pipeline gas export"
   * @param tag the equipment tag the chart was issued for, e.g. "27-KA01"
   * @param documentReference the source document reference, e.g. "8300199-CA-001 sheet 14"
   * @param curveType the origin of the curve data (never null; null is stored as UNSPECIFIED)
   */
  public CompressorChartMetadata(String casingModel, String service, String tag, String documentReference,
      CurveType curveType) {
    this.casingModel = casingModel == null ? "" : casingModel;
    this.service = service == null ? "" : service;
    this.tag = tag == null ? "" : tag;
    this.documentReference = documentReference == null ? "" : documentReference;
    this.curveType = curveType == null ? CurveType.UNSPECIFIED : curveType;
  }

  /**
   * Set the reference (basis) conditions the chart was measured at.
   *
   * @param molecularWeight reference gas molecular weight in g/mol
   * @param referenceTemperature reference inlet temperature in Kelvin
   * @param referencePressure reference inlet pressure in bara
   * @param compressibilityZ reference compressibility factor Z (dimensionless)
   * @return this metadata object for chaining
   */
  public CompressorChartMetadata setReferenceConditions(double molecularWeight, double referenceTemperature,
      double referencePressure, double compressibilityZ) {
    this.molecularWeight = molecularWeight;
    this.referenceTemperature = referenceTemperature;
    this.referencePressure = referencePressure;
    this.compressibilityZ = compressibilityZ;
    return this;
  }

  /**
   * Get the casing/model.
   *
   * @return the casing/model string
   */
  public String getCasingModel() {
    return casingModel;
  }

  /**
   * Set the casing/model.
   *
   * @param casingModel the casing/model string
   */
  public void setCasingModel(String casingModel) {
    this.casingModel = casingModel == null ? "" : casingModel;
  }

  /**
   * Get the service description.
   *
   * @return the service description
   */
  public String getService() {
    return service;
  }

  /**
   * Set the service description.
   *
   * @param service the service description
   */
  public void setService(String service) {
    this.service = service == null ? "" : service;
  }

  /**
   * Get the equipment tag.
   *
   * @return the equipment tag
   */
  public String getTag() {
    return tag;
  }

  /**
   * Set the equipment tag.
   *
   * @param tag the equipment tag
   */
  public void setTag(String tag) {
    this.tag = tag == null ? "" : tag;
  }

  /**
   * Get the source document reference.
   *
   * @return the document reference
   */
  public String getDocumentReference() {
    return documentReference;
  }

  /**
   * Set the source document reference.
   *
   * @param documentReference the document reference
   */
  public void setDocumentReference(String documentReference) {
    this.documentReference = documentReference == null ? "" : documentReference;
  }

  /**
   * Get the curve type (origin).
   *
   * @return the curve type
   */
  public CurveType getCurveType() {
    return curveType;
  }

  /**
   * Set the curve type (origin).
   *
   * @param curveType the curve type (null is stored as UNSPECIFIED)
   */
  public void setCurveType(CurveType curveType) {
    this.curveType = curveType == null ? CurveType.UNSPECIFIED : curveType;
  }

  /**
   * Get the reference molecular weight.
   *
   * @return reference molecular weight in g/mol
   */
  public double getMolecularWeight() {
    return molecularWeight;
  }

  /**
   * Get the reference temperature.
   *
   * @return reference temperature in Kelvin
   */
  public double getReferenceTemperature() {
    return referenceTemperature;
  }

  /**
   * Get the reference pressure.
   *
   * @return reference pressure in bara
   */
  public double getReferencePressure() {
    return referencePressure;
  }

  /**
   * Get the reference compressibility factor.
   *
   * @return reference compressibility Z
   */
  public double getCompressibilityZ() {
    return compressibilityZ;
  }

  /**
   * Get the free-text description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Set the free-text description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description == null ? "" : description;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "CompressorChartMetadata[model=" + casingModel + ", service=" + service + ", tag=" + tag + ", doc="
        + documentReference + ", type=" + curveType + "]";
  }
}
