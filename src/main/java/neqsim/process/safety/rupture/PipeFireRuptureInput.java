package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input data for one blowdown pipe fire-rupture strain-rate calculation.
 *
 * <p>
 * Geometry is stored as pipe outside diameter and nominal wall thickness with explicit corrosion allowance and
 * wall-thickness undertolerance. The effective wall is calculated as
 * {@code nominalWall * (1 - undertolerance) - corrosionAllowance}, matching the benchmark workbook.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipeFireRuptureInput implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String segmentId;
  private final String pipeClass;
  private final double nominalDiameterInches;
  private final double outsideDiameterM;
  private final double nominalWallThicknessM;
  private final double corrosionAllowanceM;
  private final double wallThicknessUndertoleranceFraction;
  private final double weldFactor;
  private final double weightStressMPa;
  private final double fluidDensityKgPerM3;
  private final double fluidHeatCapacityJPerKgK;
  private final double gasMolecularWeightKgPerKmol;
  private final double initialTemperatureC;
  private final double exposedLengthM;
  private final List<SafetyEvidenceReference> evidenceReferences;

  /**
   * Creates a pipe fire-rupture input object.
   *
   * @param builder populated input builder
   */
  private PipeFireRuptureInput(Builder builder) {
    builder.validate();
    this.segmentId = builder.segmentId;
    this.pipeClass = builder.pipeClass;
    this.nominalDiameterInches = builder.nominalDiameterInches;
    this.outsideDiameterM = builder.outsideDiameterM;
    this.nominalWallThicknessM = builder.nominalWallThicknessM;
    this.corrosionAllowanceM = builder.corrosionAllowanceM;
    this.wallThicknessUndertoleranceFraction = builder.wallThicknessUndertoleranceFraction;
    this.weldFactor = builder.weldFactor;
    this.weightStressMPa = builder.weightStressMPa;
    this.fluidDensityKgPerM3 = builder.fluidDensityKgPerM3;
    this.fluidHeatCapacityJPerKgK = builder.fluidHeatCapacityJPerKgK;
    this.gasMolecularWeightKgPerKmol = builder.gasMolecularWeightKgPerKmol;
    this.initialTemperatureC = builder.initialTemperatureC;
    this.exposedLengthM = builder.exposedLengthM;
    this.evidenceReferences = Collections
	.unmodifiableList(new ArrayList<SafetyEvidenceReference>(builder.evidenceReferences));
  }

  /**
   * Creates an input builder.
   *
   * @param segmentId pipe segment identifier
   * @return new builder
   */
  public static Builder builder(String segmentId) {
    return new Builder(segmentId);
  }

  /**
   * Gets the pipe segment identifier.
   *
   * @return segment identifier
   */
  public String getSegmentId() {
    return segmentId;
  }

  /**
   * Gets the pipe class.
   *
   * @return pipe class or empty string
   */
  public String getPipeClass() {
    return pipeClass;
  }

  /**
   * Gets nominal diameter.
   *
   * @return nominal diameter in inches
   */
  public double getNominalDiameterInches() {
    return nominalDiameterInches;
  }

  /**
   * Gets outside diameter.
   *
   * @return outside diameter in m
   */
  public double getOutsideDiameterM() {
    return outsideDiameterM;
  }

  /**
   * Gets nominal wall thickness.
   *
   * @return wall thickness in m
   */
  public double getNominalWallThicknessM() {
    return nominalWallThicknessM;
  }

  /**
   * Gets effective wall thickness after undertolerance and corrosion allowance.
   *
   * @return effective wall thickness in m
   */
  public double getEffectiveWallThicknessM() {
    return nominalWallThicknessM * (1.0 - wallThicknessUndertoleranceFraction) - corrosionAllowanceM;
  }

  /**
   * Gets the initial inside diameter after effective wall reduction.
   *
   * @return inside diameter in m
   */
  public double getInitialInsideDiameterM() {
    return outsideDiameterM - 2.0 * getEffectiveWallThicknessM();
  }

  /**
   * Gets corrosion allowance.
   *
   * @return corrosion allowance in m
   */
  public double getCorrosionAllowanceM() {
    return corrosionAllowanceM;
  }

  /**
   * Gets wall-thickness undertolerance.
   *
   * @return undertolerance fraction
   */
  public double getWallThicknessUndertoleranceFraction() {
    return wallThicknessUndertoleranceFraction;
  }

  /**
   * Gets weld factor.
   *
   * @return weld factor
   */
  public double getWeldFactor() {
    return weldFactor;
  }

  /**
   * Gets axial weight stress.
   *
   * @return weight stress in MPa
   */
  public double getWeightStressMPa() {
    return weightStressMPa;
  }

  /**
   * Gets fluid density.
   *
   * @return fluid density in kg/m3
   */
  public double getFluidDensityKgPerM3() {
    return fluidDensityKgPerM3;
  }

  /**
   * Gets fluid heat capacity.
   *
   * @return fluid heat capacity in J/kg-K
   */
  public double getFluidHeatCapacityJPerKgK() {
    return fluidHeatCapacityJPerKgK;
  }

  /**
   * Gets gas molecular weight.
   *
   * @return gas molecular weight in kg/kmol
   */
  public double getGasMolecularWeightKgPerKmol() {
    return gasMolecularWeightKgPerKmol;
  }

  /**
   * Gets initial temperature.
   *
   * @return initial temperature in degrees Celsius
   */
  public double getInitialTemperatureC() {
    return initialTemperatureC;
  }

  /**
   * Gets exposed pipe length.
   *
   * @return exposed length in m
   */
  public double getExposedLengthM() {
    return exposedLengthM;
  }

  /**
   * Gets evidence references attached to the input row.
   *
   * @return immutable evidence reference list
   */
  public List<SafetyEvidenceReference> getEvidenceReferences() {
    return evidenceReferences;
  }

  /**
   * Creates a builder pre-populated from this input.
   *
   * @return populated builder
   */
  public Builder toBuilder() {
    Builder builder = builder(segmentId).pipeClass(pipeClass).nominalDiameterInches(nominalDiameterInches)
	.outsideDiameter(outsideDiameterM, "m").nominalWallThickness(nominalWallThicknessM, "m")
	.corrosionAllowance(corrosionAllowanceM, "m")
	.wallThicknessUndertoleranceFraction(wallThicknessUndertoleranceFraction).weldFactor(weldFactor)
	.weightStressMPa(weightStressMPa).fluidDensityKgPerM3(fluidDensityKgPerM3)
	.fluidHeatCapacityJPerKgK(fluidHeatCapacityJPerKgK).gasMolecularWeightKgPerKmol(gasMolecularWeightKgPerKmol)
	.initialTemperatureC(initialTemperatureC).exposedLength(exposedLengthM, "m");
    for (SafetyEvidenceReference reference : evidenceReferences) {
      builder.evidenceReference(reference);
    }
    return builder;
  }

  /**
   * Gets external surface area exposed to fire.
   *
   * @return exposed area in m2
   */
  public double getExposedAreaM2() {
    return Math.PI * outsideDiameterM * exposedLengthM;
  }

  /**
   * Gets internal volume of the exposed segment.
   *
   * @return volume in m3
   */
  public double getInternalVolumeM3() {
    double insideDiameterM = getInitialInsideDiameterM();
    return Math.PI / 4.0 * insideDiameterM * insideDiameterM * exposedLengthM;
  }

  /**
   * Converts input data to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("segmentId", segmentId);
    map.put("pipeClass", pipeClass);
    map.put("nominalDiameterInches", nominalDiameterInches);
    map.put("outsideDiameterM", outsideDiameterM);
    map.put("nominalWallThicknessM", nominalWallThicknessM);
    map.put("corrosionAllowanceM", corrosionAllowanceM);
    map.put("wallThicknessUndertoleranceFraction", wallThicknessUndertoleranceFraction);
    map.put("effectiveWallThicknessM", getEffectiveWallThicknessM());
    map.put("initialInsideDiameterM", getInitialInsideDiameterM());
    map.put("weldFactor", weldFactor);
    map.put("weightStressMPa", weightStressMPa);
    map.put("fluidDensityKgPerM3", fluidDensityKgPerM3);
    map.put("fluidHeatCapacityJPerKgK", fluidHeatCapacityJPerKgK);
    map.put("gasMolecularWeightKgPerKmol", gasMolecularWeightKgPerKmol);
    map.put("initialTemperatureC", initialTemperatureC);
    map.put("exposedLengthM", exposedLengthM);
    map.put("exposedAreaM2", getExposedAreaM2());
    map.put("internalVolumeM3", getInternalVolumeM3());
    List<Map<String, Object>> evidenceMaps = new ArrayList<Map<String, Object>>();
    for (SafetyEvidenceReference reference : evidenceReferences) {
      evidenceMaps.add(reference.toMap());
    }
    map.put("evidenceReferences", evidenceMaps);
    return map;
  }

  /**
   * Converts input data to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /** Builder for {@link PipeFireRuptureInput}. */
  public static final class Builder {
    private final String segmentId;
    private String pipeClass = "";
    private double nominalDiameterInches = 0.0;
    private double outsideDiameterM = 0.0;
    private double nominalWallThicknessM = 0.0;
    private double corrosionAllowanceM = 0.0;
    private double wallThicknessUndertoleranceFraction = 0.0;
    private double weldFactor = 1.0;
    private double weightStressMPa = 0.0;
    private double fluidDensityKgPerM3 = 1.0;
    private double fluidHeatCapacityJPerKgK = 2000.0;
    private double gasMolecularWeightKgPerKmol = 18.0;
    private double initialTemperatureC = 20.0;
    private double exposedLengthM = 1.0;
    private final List<SafetyEvidenceReference> evidenceReferences = new ArrayList<SafetyEvidenceReference>();

    /**
     * Creates a builder.
     *
     * @param segmentId pipe segment identifier
     */
    private Builder(String segmentId) {
      if (segmentId == null || segmentId.trim().isEmpty()) {
	throw new IllegalArgumentException("segmentId must not be empty");
      }
      this.segmentId = segmentId.trim();
    }

    /**
     * Sets pipe class.
     *
     * @param pipeClass pipe class or piping class identifier
     * @return this builder
     */
    public Builder pipeClass(String pipeClass) {
      this.pipeClass = pipeClass == null ? "" : pipeClass.trim();
      return this;
    }

    /**
     * Sets nominal diameter.
     *
     * @param nominalDiameterInches nominal diameter in inches; use 0 when unknown
     * @return this builder
     */
    public Builder nominalDiameterInches(double nominalDiameterInches) {
      validateNonNegative(nominalDiameterInches, "nominalDiameterInches");
      this.nominalDiameterInches = nominalDiameterInches;
      return this;
    }

    /**
     * Sets outside diameter.
     *
     * @param outsideDiameter value of outside diameter
     * @param unit unit text, {@code m} or {@code mm}
     * @return this builder
     */
    public Builder outsideDiameter(double outsideDiameter, String unit) {
      this.outsideDiameterM = convertLengthToM(outsideDiameter, unit);
      return this;
    }

    /**
     * Sets nominal wall thickness.
     *
     * @param wallThickness wall-thickness value
     * @param unit unit text, {@code m} or {@code mm}
     * @return this builder
     */
    public Builder nominalWallThickness(double wallThickness, String unit) {
      this.nominalWallThicknessM = convertLengthToM(wallThickness, unit);
      return this;
    }

    /**
     * Sets corrosion allowance.
     *
     * @param corrosionAllowance corrosion allowance value
     * @param unit unit text, {@code m} or {@code mm}
     * @return this builder
     */
    public Builder corrosionAllowance(double corrosionAllowance, String unit) {
      this.corrosionAllowanceM = convertLengthToM(corrosionAllowance, unit);
      return this;
    }

    /**
     * Sets wall-thickness undertolerance.
     *
     * @param undertoleranceFraction undertolerance fraction, from 0 to below 1
     * @return this builder
     */
    public Builder wallThicknessUndertoleranceFraction(double undertoleranceFraction) {
      validateFractionBelowOne(undertoleranceFraction, "undertoleranceFraction");
      this.wallThicknessUndertoleranceFraction = undertoleranceFraction;
      return this;
    }

    /**
     * Sets weld factor.
     *
     * @param weldFactor weld factor, greater than 0 and less than or equal to 1
     * @return this builder
     */
    public Builder weldFactor(double weldFactor) {
      if (weldFactor <= 0.0 || weldFactor > 1.0 || Double.isNaN(weldFactor) || Double.isInfinite(weldFactor)) {
	throw new IllegalArgumentException("weldFactor must be in (0,1]");
      }
      this.weldFactor = weldFactor;
      return this;
    }

    /**
     * Sets the axial weight stress added to longitudinal stress.
     *
     * @param weightStressMPa weight stress in MPa
     * @return this builder
     */
    public Builder weightStressMPa(double weightStressMPa) {
      if (Double.isNaN(weightStressMPa) || Double.isInfinite(weightStressMPa)) {
	throw new IllegalArgumentException("weightStressMPa must be finite");
      }
      this.weightStressMPa = weightStressMPa;
      return this;
    }

    /**
     * Sets fluid density.
     *
     * @param fluidDensityKgPerM3 density in kg/m3; must be positive
     * @return this builder
     */
    public Builder fluidDensityKgPerM3(double fluidDensityKgPerM3) {
      validatePositive(fluidDensityKgPerM3, "fluidDensityKgPerM3");
      this.fluidDensityKgPerM3 = fluidDensityKgPerM3;
      return this;
    }

    /**
     * Sets fluid heat capacity.
     *
     * @param fluidHeatCapacityJPerKgK heat capacity in J/kg-K; must be positive
     * @return this builder
     */
    public Builder fluidHeatCapacityJPerKgK(double fluidHeatCapacityJPerKgK) {
      validatePositive(fluidHeatCapacityJPerKgK, "fluidHeatCapacityJPerKgK");
      this.fluidHeatCapacityJPerKgK = fluidHeatCapacityJPerKgK;
      return this;
    }

    /**
     * Sets gas molecular weight.
     *
     * @param gasMolecularWeightKgPerKmol molecular weight in kg/kmol; must be positive
     * @return this builder
     */
    public Builder gasMolecularWeightKgPerKmol(double gasMolecularWeightKgPerKmol) {
      validatePositive(gasMolecularWeightKgPerKmol, "gasMolecularWeightKgPerKmol");
      this.gasMolecularWeightKgPerKmol = gasMolecularWeightKgPerKmol;
      return this;
    }

    /**
     * Sets initial metal and fluid temperature.
     *
     * @param initialTemperatureC initial temperature in degrees Celsius
     * @return this builder
     */
    public Builder initialTemperatureC(double initialTemperatureC) {
      if (Double.isNaN(initialTemperatureC) || Double.isInfinite(initialTemperatureC)) {
	throw new IllegalArgumentException("initialTemperatureC must be finite");
      }
      this.initialTemperatureC = initialTemperatureC;
      return this;
    }

    /**
     * Sets exposed pipe length.
     *
     * @param exposedLength value of exposed length
     * @param unit unit text, {@code m} or {@code mm}
     * @return this builder
     */
    public Builder exposedLength(double exposedLength, String unit) {
      this.exposedLengthM = convertLengthToM(exposedLength, unit);
      return this;
    }

    /**
     * Adds a source evidence reference for this input row.
     *
     * @param reference evidence reference; ignored when null
     * @return this builder
     */
    public Builder evidenceReference(SafetyEvidenceReference reference) {
      if (reference != null) {
	evidenceReferences.add(reference);
      }
      return this;
    }

    /**
     * Builds the input object.
     *
     * @return pipe fire-rupture input
     */
    public PipeFireRuptureInput build() {
      return new PipeFireRuptureInput(this);
    }

    /**
     * Validates the builder state.
     *
     * @throws IllegalArgumentException if any required input is invalid
     */
    private void validate() {
      validatePositive(outsideDiameterM, "outsideDiameterM");
      validatePositive(nominalWallThicknessM, "nominalWallThicknessM");
      validatePositive(exposedLengthM, "exposedLengthM");
      if (nominalWallThicknessM * (1.0 - wallThicknessUndertoleranceFraction) - corrosionAllowanceM <= 0.0) {
	throw new IllegalArgumentException("effective wall thickness must be positive");
      }
      if (outsideDiameterM <= 2.0
	  * (nominalWallThicknessM * (1.0 - wallThicknessUndertoleranceFraction) - corrosionAllowanceM)) {
	throw new IllegalArgumentException("effective inside diameter must be positive");
      }
    }

    /**
     * Converts length values to metres.
     *
     * @param value length value
     * @param unit unit text
     * @return length in m
     */
    private static double convertLengthToM(double value, String unit) {
      validateNonNegative(value, "length");
      String normalized = unit == null ? "m" : unit.trim().toLowerCase();
      if ("m".equals(normalized) || "meter".equals(normalized) || "metre".equals(normalized)) {
	return value;
      }
      if ("mm".equals(normalized) || "millimeter".equals(normalized) || "millimetre".equals(normalized)) {
	return value / 1000.0;
      }
      throw new IllegalArgumentException("Unsupported length unit: " + unit);
    }
  }

  /**
   * Validates a positive finite value.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Validates a non-negative finite value.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validateNonNegative(double value, String name) {
    if (value < 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }

  /**
   * Validates a fraction that must be lower than one.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validateFractionBelowOne(double value, String name) {
    if (value < 0.0 || value >= 1.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be in [0,1)");
    }
  }
}
