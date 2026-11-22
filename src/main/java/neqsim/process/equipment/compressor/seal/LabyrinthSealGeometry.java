package neqsim.process.equipment.compressor.seal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.util.unit.LengthUnit;

/**
 * Geometry of a straight-through labyrinth seal with teeth on the stator.
 *
 * <p>
 * All dimensions are stored in metres. Setters accept any length unit supported by {@link LengthUnit} (for example
 * {@code "m"}, {@code "mm"} or {@code "inch"}). The leakage area is the annulus between the shaft and the tooth tips,
 * {@code A = pi * c * (D + c)}.
 * </p>
 *
 * <p>
 * The tooth height is kept for completeness and reporting; none of the correlations in
 * {@link LabyrinthSealFlowCalculator} use it, so it is optional.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
public class LabyrinthSealGeometry implements Serializable {
  private static final long serialVersionUID = 1L;

  private double shaftDiameter = Double.NaN;
  private double toothHeight = Double.NaN;
  private double toothPitch = Double.NaN;
  private double tipWidth = Double.NaN;
  private double radialClearance = Double.NaN;
  private int numberOfTeeth = 0;

  /**
   * Creates an empty geometry. Set all dimensions before use.
   */
  public LabyrinthSealGeometry() {
  }

  /**
   * Creates an independent copy of another geometry.
   *
   * @param other geometry to copy
   * @throws IllegalArgumentException if other is null
   */
  public LabyrinthSealGeometry(LabyrinthSealGeometry other) {
    if (other == null) {
      throw new IllegalArgumentException("Geometry to copy must not be null");
    }
    this.shaftDiameter = other.shaftDiameter;
    this.toothHeight = other.toothHeight;
    this.toothPitch = other.toothPitch;
    this.tipWidth = other.tipWidth;
    this.radialClearance = other.radialClearance;
    this.numberOfTeeth = other.numberOfTeeth;
  }

  /**
   * Converts a positive length to metres.
   *
   * @param value length value
   * @param unit length unit
   * @param name dimension name used in the error message
   * @return length in metres
   * @throws IllegalArgumentException if the value is not finite and positive
   */
  private static double toMetres(double value, String unit, String name) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be a finite positive length, got " + value + " " + unit);
    }
    return LengthUnit.convert(value, unit, "m");
  }

  /**
   * Sets the shaft diameter under the teeth.
   *
   * @param value shaft diameter
   * @param unit length unit, for example "mm"
   */
  public void setShaftDiameter(double value, String unit) {
    shaftDiameter = toMetres(value, unit, "Shaft diameter");
  }

  /**
   * Sets the tooth height (informational, not used by the correlations).
   *
   * @param value tooth height
   * @param unit length unit
   */
  public void setToothHeight(double value, String unit) {
    toothHeight = toMetres(value, unit, "Tooth height");
  }

  /**
   * Sets the tooth pitch (axial distance between neighbouring teeth).
   *
   * @param value tooth pitch
   * @param unit length unit
   */
  public void setToothPitch(double value, String unit) {
    toothPitch = toMetres(value, unit, "Tooth pitch");
  }

  /**
   * Sets the tooth tip width.
   *
   * @param value tip width
   * @param unit length unit
   */
  public void setTipWidth(double value, String unit) {
    tipWidth = toMetres(value, unit, "Tip width");
  }

  /**
   * Sets the radial clearance between the tooth tips and the shaft.
   *
   * @param value radial clearance
   * @param unit length unit
   */
  public void setRadialClearance(double value, String unit) {
    radialClearance = toMetres(value, unit, "Radial clearance");
  }

  /**
   * Sets the number of teeth (throttling restrictions).
   *
   * @param numberOfTeeth number of teeth, at least 1
   * @throws IllegalArgumentException if fewer than one tooth is given
   */
  public void setNumberOfTeeth(int numberOfTeeth) {
    if (numberOfTeeth < 1) {
      throw new IllegalArgumentException("Number of teeth must be at least 1, got " + numberOfTeeth);
    }
    this.numberOfTeeth = numberOfTeeth;
  }

  /**
   * Returns the shaft diameter.
   *
   * @return shaft diameter in m
   */
  public double getShaftDiameter() {
    return shaftDiameter;
  }

  /**
   * Returns the shaft diameter in the requested unit.
   *
   * @param unit length unit
   * @return shaft diameter
   */
  public double getShaftDiameter(String unit) {
    return LengthUnit.convert(shaftDiameter, "m", unit);
  }

  /**
   * Returns the tooth height.
   *
   * @return tooth height in m, NaN if not set
   */
  public double getToothHeight() {
    return toothHeight;
  }

  /**
   * Returns the tooth height in the requested unit.
   *
   * @param unit length unit
   * @return tooth height, NaN if not set
   */
  public double getToothHeight(String unit) {
    return LengthUnit.convert(toothHeight, "m", unit);
  }

  /**
   * Returns the tooth pitch.
   *
   * @return tooth pitch in m
   */
  public double getToothPitch() {
    return toothPitch;
  }

  /**
   * Returns the tooth pitch in the requested unit.
   *
   * @param unit length unit
   * @return tooth pitch
   */
  public double getToothPitch(String unit) {
    return LengthUnit.convert(toothPitch, "m", unit);
  }

  /**
   * Returns the tip width.
   *
   * @return tip width in m
   */
  public double getTipWidth() {
    return tipWidth;
  }

  /**
   * Returns the tip width in the requested unit.
   *
   * @param unit length unit
   * @return tip width
   */
  public double getTipWidth(String unit) {
    return LengthUnit.convert(tipWidth, "m", unit);
  }

  /**
   * Returns the radial clearance.
   *
   * @return radial clearance in m
   */
  public double getRadialClearance() {
    return radialClearance;
  }

  /**
   * Returns the radial clearance in the requested unit.
   *
   * @param unit length unit
   * @return radial clearance
   */
  public double getRadialClearance(String unit) {
    return LengthUnit.convert(radialClearance, "m", unit);
  }

  /**
   * Returns the number of teeth.
   *
   * @return number of teeth, 0 if not set
   */
  public int getNumberOfTeeth() {
    return numberOfTeeth;
  }

  /**
   * Returns the leakage area, the annulus between shaft and tooth tips.
   *
   * @return clearance area pi * c * (D + c) in m2
   * @throws IllegalStateException if the shaft diameter or the radial clearance is not set
   */
  public double getClearanceArea() {
    if (Double.isNaN(shaftDiameter) || Double.isNaN(radialClearance)) {
      throw new IllegalStateException(
          "Clearance area needs the shaft diameter and the radial clearance: call setShaftDiameter(...) and "
              + "setRadialClearance(...)");
    }
    return Math.PI * radialClearance * (shaftDiameter + radialClearance);
  }

  /**
   * Checks that the geometry is complete and consistent.
   *
   * @throws IllegalStateException if a required dimension is missing or the tip width is not smaller than the pitch
   */
  public void validate() {
    List<String> missing = new ArrayList<String>();
    if (Double.isNaN(shaftDiameter)) {
      missing.add("shaft diameter (setShaftDiameter)");
    }
    if (Double.isNaN(radialClearance)) {
      missing.add("radial clearance (setRadialClearance)");
    }
    if (Double.isNaN(toothPitch)) {
      missing.add("tooth pitch (setToothPitch)");
    }
    if (Double.isNaN(tipWidth)) {
      missing.add("tip width (setTipWidth)");
    }
    if (numberOfTeeth < 1) {
      missing.add("number of teeth (setNumberOfTeeth)");
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Labyrinth seal geometry is incomplete, set: " + String.join(", ", missing));
    }
    if (tipWidth >= toothPitch) {
      throw new IllegalStateException("The tip width (" + tipWidth * 1000.0
          + " mm) must be smaller than the tooth pitch (" + toothPitch * 1000.0 + " mm); check the seal drawing");
    }
  }

  /**
   * Returns the geometry as an ordered map in millimetres for reporting. Unset dimensions are omitted.
   *
   * @return map of dimension name to value
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    putIfSet(map, "shaftDiameter_mm", shaftDiameter);
    putIfSet(map, "toothHeight_mm", toothHeight);
    putIfSet(map, "toothPitch_mm", toothPitch);
    putIfSet(map, "tipWidth_mm", tipWidth);
    putIfSet(map, "radialClearance_mm", radialClearance);
    map.put("numberOfTeeth", numberOfTeeth);
    return map;
  }

  /**
   * Adds a dimension in millimetres to the map when it is set.
   *
   * @param map target map
   * @param key map key
   * @param metres dimension in metres, NaN when unset
   */
  private static void putIfSet(Map<String, Object> map, String key, double metres) {
    if (!Double.isNaN(metres)) {
      map.put(key, metres * 1000.0);
    }
  }
}
