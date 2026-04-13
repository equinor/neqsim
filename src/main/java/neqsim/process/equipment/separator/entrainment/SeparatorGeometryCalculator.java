package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Precise separator geometry calculations for horizontal and vertical vessels.
 *
 * <p>
 * This class computes the gas and liquid volumes, cross-sectional areas, effective settling
 * heights, and residence times needed for accurate separation performance prediction in both
 * horizontal and vertical separators.
 * </p>
 *
 * <h3>Horizontal Separator Geometry</h3>
 * <p>
 * For a horizontal vessel with liquid level at height h in a cylinder of diameter D, the liquid and
 * gas cross-sectional areas are computed from circular segment geometry:
 * </p>
 *
 * $$ A_{liq} = \frac{D^2}{4}\left[\cos^{-1}\left(\frac{D-2h}{D}\right) -
 * \frac{(D-2h)}{D}\sqrt{1-\left(\frac{D-2h}{D}\right)^2}\right] $$
 *
 * $$ A_{gas} = \frac{\pi D^2}{4} - A_{liq} $$
 *
 * <h3>Vertical Separator Geometry</h3>
 * <p>
 * For vertical vessels, the gas and liquid occupy the full cross section at different heights. The
 * gas residence time depends on the gas travel height above the liquid level to the mist
 * eliminator.
 * </p>
 *
 * <p>
 * <b>References:</b>
 * </p>
 * <ul>
 * <li>Arnold, K., Stewart, M. (2008), <i>Surface Production Operations</i>, Vol. 1, Gulf
 * Professional Publishing.</li>
 * <li>Svrcek, W.Y., Monnery, W.D. (1993), "Design Two-Phase Separators within the Right Limits",
 * <i>Chemical Engineering Progress</i>, October, 53-60.</li>
 * <li>API RP 12J (2008), "Specification for Oil and Gas Separators".</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class SeparatorGeometryCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // -- Vessel dimensions --
  private double internalDiameter = 2.0; // [m]
  private double tangentToTangentLength = 8.0; // [m]
  private String orientation = "horizontal"; // "horizontal" or "vertical"

  // -- Liquid levels --
  private double normalLiquidLevel = 0.5; // Fraction of diameter (horizontal) or height (vertical)
  private double highLiquidLevel = 0.7; // Fraction
  private double lowLiquidLevel = 0.3; // Fraction

  // -- Internals positions (fraction of length from inlet) --
  private double inletDevicePosition = 0.05; // 5% from inlet
  private double mistEliminatorPosition = 0.90; // 90% from inlet
  private double weirPosition = 0.60; // 60% from inlet (three-phase only)

  // -- Nozzle sizes --
  private double inletNozzleDiameter = 0.2; // [m]
  private double gasOutletNozzleDiameter = 0.3; // [m]
  private double liquidOutletNozzleDiameter = 0.1; // [m]

  // -- Calculated results --
  private double gasArea; // Gas cross-sectional area [m2]
  private double liquidArea; // Liquid cross-sectional area [m2]
  private double totalArea; // Total cross-sectional area [m2]
  private double gasVolume; // Gas volume [m3]
  private double liquidVolume; // Liquid volume [m3]
  private double totalVolume; // Total volume [m3]
  private double effectiveGasSettlingHeight; // Height available for droplet settling [m]
  private double effectiveLiquidSettlingHeight; // Height available for bubble rising [m]
  private double gasResidenceTime; // [s]
  private double liquidResidenceTime; // [s]
  private double gravitySectionLength; // Effective gravity section length [m]
  private double oilPadThickness; // For 3-phase: oil pad height [m]
  private double waterLayerHeight; // For 3-phase: water layer height [m]

  /**
   * Creates a default SeparatorGeometryCalculator.
   */
  public SeparatorGeometryCalculator() {
    // Defaults set in field declarations
  }

  /**
   * Calculates all geometry parameters for a two-phase separator.
   *
   * @param gasVolumeFlow actual gas volume flow rate [m3/s]
   * @param liquidVolumeFlow actual liquid volume flow rate [m3/s]
   */
  public void calculate(double gasVolumeFlow, double liquidVolumeFlow) {
    totalArea = Math.PI * internalDiameter * internalDiameter / 4.0;

    if ("vertical".equalsIgnoreCase(orientation)) {
      calculateVertical(gasVolumeFlow, liquidVolumeFlow);
    } else {
      calculateHorizontal(gasVolumeFlow, liquidVolumeFlow);
    }
  }

  /**
   * Calculates all geometry parameters for a three-phase separator.
   *
   * @param gasVolumeFlow actual gas volume flow rate [m3/s]
   * @param oilVolumeFlow actual oil volume flow rate [m3/s]
   * @param waterVolumeFlow actual water volume flow rate [m3/s]
   * @param oilLevelFraction fraction of liquid height occupied by oil (above weir height)
   */
  public void calculateThreePhase(double gasVolumeFlow, double oilVolumeFlow,
      double waterVolumeFlow, double oilLevelFraction) {
    totalArea = Math.PI * internalDiameter * internalDiameter / 4.0;

    if ("vertical".equalsIgnoreCase(orientation)) {
      calculateVertical(gasVolumeFlow, oilVolumeFlow + waterVolumeFlow);
    } else {
      calculateHorizontalThreePhase(gasVolumeFlow, oilVolumeFlow, waterVolumeFlow,
          oilLevelFraction);
    }
  }

  /**
   * Calculates geometry for a horizontal two-phase separator.
   *
   * @param gasVolumeFlow gas flow [m3/s]
   * @param liquidVolumeFlow liquid flow [m3/s]
   */
  private void calculateHorizontal(double gasVolumeFlow, double liquidVolumeFlow) {
    double liquidHeight = normalLiquidLevel * internalDiameter;

    // Circular segment areas
    liquidArea = calcSegmentArea(internalDiameter, liquidHeight);
    gasArea = totalArea - liquidArea;

    // Gas settling height = distance from liquid surface to top of vessel
    effectiveGasSettlingHeight = internalDiameter - liquidHeight;

    // Liquid settling height = liquid depth
    effectiveLiquidSettlingHeight = liquidHeight;

    // Effective gravity section length (from inlet device to mist eliminator)
    gravitySectionLength = tangentToTangentLength * (mistEliminatorPosition - inletDevicePosition);

    // Volumes
    gasVolume = gasArea * tangentToTangentLength;
    liquidVolume = liquidArea * tangentToTangentLength;
    totalVolume = totalArea * tangentToTangentLength;

    // Residence times
    if (gasArea > 0 && gasVolumeFlow > 0) {
      double gasVelocity = gasVolumeFlow / gasArea;
      gasResidenceTime = gravitySectionLength / gasVelocity;
    } else {
      gasResidenceTime = 0.0;
    }

    if (liquidVolume > 0 && liquidVolumeFlow > 0) {
      liquidResidenceTime = liquidVolume / liquidVolumeFlow;
    } else {
      liquidResidenceTime = 0.0;
    }
  }

  /**
   * Calculates geometry for a horizontal three-phase separator with weir.
   *
   * @param gasVolumeFlow gas flow [m3/s]
   * @param oilVolumeFlow oil flow [m3/s]
   * @param waterVolumeFlow water flow [m3/s]
   * @param oilLevelFraction fraction of total liquid height that is oil
   */
  private void calculateHorizontalThreePhase(double gasVolumeFlow, double oilVolumeFlow,
      double waterVolumeFlow, double oilLevelFraction) {
    double liquidHeight = normalLiquidLevel * internalDiameter;

    // Oil-water interface
    double totalLiquidFlow = oilVolumeFlow + waterVolumeFlow;
    double waterFrac = (totalLiquidFlow > 0) ? waterVolumeFlow / totalLiquidFlow : 0.5;
    double oilFrac = 1.0 - waterFrac;

    // Water layer at bottom, oil on top
    waterLayerHeight = liquidHeight * waterFrac;
    oilPadThickness = liquidHeight * oilFrac;

    // Areas
    liquidArea = calcSegmentArea(internalDiameter, liquidHeight);
    gasArea = totalArea - liquidArea;

    // Gas settling
    effectiveGasSettlingHeight = internalDiameter - liquidHeight;

    // Liquid-liquid settling heights
    effectiveLiquidSettlingHeight = oilPadThickness; // Water drops settle through oil pad

    // Gravity section
    gravitySectionLength = tangentToTangentLength * (weirPosition - inletDevicePosition);
    double oilCompartmentLength = tangentToTangentLength * (mistEliminatorPosition - weirPosition);

    // Volumes
    double oilArea = calcSegmentArea(internalDiameter, liquidHeight)
        - calcSegmentArea(internalDiameter, waterLayerHeight);
    double waterArea = calcSegmentArea(internalDiameter, waterLayerHeight);
    gasVolume = gasArea * tangentToTangentLength;
    liquidVolume = liquidArea * tangentToTangentLength;
    totalVolume = totalArea * tangentToTangentLength;

    // Residence times
    if (gasArea > 0 && gasVolumeFlow > 0) {
      double gasVelocity = gasVolumeFlow / gasArea;
      gasResidenceTime = gravitySectionLength / gasVelocity;
    } else {
      gasResidenceTime = 0.0;
    }

    double oilVolume = oilArea * gravitySectionLength;
    double waterVolume = waterArea * gravitySectionLength;

    if (oilVolumeFlow > 0 && oilVolume > 0) {
      liquidResidenceTime = oilVolume / oilVolumeFlow;
    } else {
      liquidResidenceTime = 0.0;
    }
  }

  /**
   * Calculates geometry for a vertical two-phase separator.
   *
   * @param gasVolumeFlow gas flow [m3/s]
   * @param liquidVolumeFlow liquid flow [m3/s]
   */
  private void calculateVertical(double gasVolumeFlow, double liquidVolumeFlow) {
    gasArea = totalArea; // Full cross section for gas
    liquidArea = totalArea; // Full cross section for liquid

    double liquidHeight = normalLiquidLevel * tangentToTangentLength;
    double gasHeight = tangentToTangentLength - liquidHeight;

    // Gas settling height = vessel diameter (droplets must reach the wall or settle)
    effectiveGasSettlingHeight = internalDiameter;

    // Liquid settling height = liquid height
    effectiveLiquidSettlingHeight = liquidHeight;

    // Gravity section is the gas space above liquid
    double mistEliminatorHeight = tangentToTangentLength * mistEliminatorPosition;
    gravitySectionLength = Math.max(0, mistEliminatorHeight - liquidHeight);

    // Volumes
    gasVolume = totalArea * gasHeight;
    liquidVolume = totalArea * liquidHeight;
    totalVolume = totalArea * tangentToTangentLength;

    // Residence times
    if (totalArea > 0 && gasVolumeFlow > 0) {
      double gasVelocity = gasVolumeFlow / totalArea;
      gasResidenceTime = (gasVelocity > 0) ? gasHeight / gasVelocity : 0.0;
    } else {
      gasResidenceTime = 0.0;
    }

    if (liquidVolume > 0 && liquidVolumeFlow > 0) {
      liquidResidenceTime = liquidVolume / liquidVolumeFlow;
    } else {
      liquidResidenceTime = 0.0;
    }
  }

  /**
   * Calculates the area of a circular segment for liquid height h in a circle of diameter D.
   *
   * <p>
   * Uses the standard circular segment formula:
   * </p>
   *
   * $$ A = \frac{R^2}{2}\left(\theta - \sin\theta\right) $$
   *
   * <p>
   * where theta = 2 * arccos((R - h) / R) and R = D/2.
   * </p>
   *
   * @param diameter vessel internal diameter [m]
   * @param height liquid height from bottom [m]
   * @return segment area [m2]
   */
  public static double calcSegmentArea(double diameter, double height) {
    if (height <= 0 || diameter <= 0) {
      return 0.0;
    }
    if (height >= diameter) {
      return Math.PI * diameter * diameter / 4.0;
    }

    double radius = diameter / 2.0;
    double theta = 2.0 * Math.acos((radius - height) / radius);
    return radius * radius / 2.0 * (theta - Math.sin(theta));
  }

  /**
   * Calculates the Souders-Brown K-factor for the current gas velocity and phase properties.
   *
   * $$ K = V_g \sqrt{\frac{\rho_g}{\rho_l - \rho_g}} $$
   *
   * @param gasVelocity actual gas velocity in gas section [m/s]
   * @param gasDensity gas density [kg/m3]
   * @param liquidDensity liquid density [kg/m3]
   * @return K-factor [m/s]
   */
  public static double calcKFactor(double gasVelocity, double gasDensity, double liquidDensity) {
    double deltaRho = liquidDensity - gasDensity;
    if (deltaRho <= 0 || gasDensity <= 0) {
      return 0.0;
    }
    return gasVelocity * Math.sqrt(gasDensity / deltaRho);
  }

  /**
   * Returns a JSON representation of all calculated geometric parameters.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("orientation", orientation);
    result.put("internalDiameter_m", internalDiameter);
    result.put("tangentToTangentLength_m", tangentToTangentLength);
    result.put("normalLiquidLevel_frac", normalLiquidLevel);
    result.put("gasArea_m2", gasArea);
    result.put("liquidArea_m2", liquidArea);
    result.put("totalArea_m2", totalArea);
    result.put("gasVolume_m3", gasVolume);
    result.put("liquidVolume_m3", liquidVolume);
    result.put("effectiveGasSettlingHeight_m", effectiveGasSettlingHeight);
    result.put("effectiveLiquidSettlingHeight_m", effectiveLiquidSettlingHeight);
    result.put("gravitySectionLength_m", gravitySectionLength);
    result.put("gasResidenceTime_s", gasResidenceTime);
    result.put("liquidResidenceTime_s", liquidResidenceTime);
    if (oilPadThickness > 0) {
      result.put("oilPadThickness_m", oilPadThickness);
      result.put("waterLayerHeight_m", waterLayerHeight);
    }
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  // ----- Getters and Setters -----

  /**
   * Sets the vessel internal diameter.
   *
   * @param internalDiameter [m]
   */
  public void setInternalDiameter(double internalDiameter) {
    this.internalDiameter = internalDiameter;
  }

  /**
   * Gets the vessel internal diameter.
   *
   * @return internal diameter [m]
   */
  public double getInternalDiameter() {
    return internalDiameter;
  }

  /**
   * Sets the tangent-to-tangent length.
   *
   * @param length [m]
   */
  public void setTangentToTangentLength(double length) {
    this.tangentToTangentLength = length;
  }

  /**
   * Gets the tangent-to-tangent length.
   *
   * @return T-T length [m]
   */
  public double getTangentToTangentLength() {
    return tangentToTangentLength;
  }

  /**
   * Sets the vessel orientation.
   *
   * @param orientation "horizontal" or "vertical"
   */
  public void setOrientation(String orientation) {
    this.orientation = orientation;
  }

  /**
   * Gets the vessel orientation.
   *
   * @return orientation
   */
  public String getOrientation() {
    return orientation;
  }

  /**
   * Sets the normal liquid level as a fraction of diameter (horizontal) or total height (vertical).
   *
   * @param fraction [0-1]
   */
  public void setNormalLiquidLevel(double fraction) {
    this.normalLiquidLevel = Math.max(0, Math.min(1, fraction));
  }

  /**
   * Gets the normal liquid level fraction.
   *
   * @return fraction [0-1]
   */
  public double getNormalLiquidLevel() {
    return normalLiquidLevel;
  }

  /**
   * Sets the high liquid level fraction.
   *
   * @param fraction [0-1]
   */
  public void setHighLiquidLevel(double fraction) {
    this.highLiquidLevel = Math.max(0, Math.min(1, fraction));
  }

  /**
   * Sets the low liquid level fraction.
   *
   * @param fraction [0-1]
   */
  public void setLowLiquidLevel(double fraction) {
    this.lowLiquidLevel = Math.max(0, Math.min(1, fraction));
  }

  /**
   * Sets the inlet nozzle diameter.
   *
   * @param diameter [m]
   */
  public void setInletNozzleDiameter(double diameter) {
    this.inletNozzleDiameter = diameter;
  }

  /**
   * Gets the inlet nozzle diameter.
   *
   * @return diameter [m]
   */
  public double getInletNozzleDiameter() {
    return inletNozzleDiameter;
  }

  /**
   * Sets the weir position as a fraction of T-T length from inlet.
   *
   * @param fraction [0-1]
   */
  public void setWeirPosition(double fraction) {
    this.weirPosition = Math.max(0, Math.min(1, fraction));
  }

  /**
   * Sets the mist eliminator position as a fraction of T-T length from inlet.
   *
   * @param fraction [0-1]
   */
  public void setMistEliminatorPosition(double fraction) {
    this.mistEliminatorPosition = Math.max(0, Math.min(1, fraction));
  }

  /**
   * Gets the calculated gas cross-sectional area.
   *
   * @return gas area [m2]
   */
  public double getGasArea() {
    return gasArea;
  }

  /**
   * Gets the calculated liquid cross-sectional area.
   *
   * @return liquid area [m2]
   */
  public double getLiquidArea() {
    return liquidArea;
  }

  /**
   * Gets the effective gas settling height.
   *
   * @return height [m]
   */
  public double getEffectiveGasSettlingHeight() {
    return effectiveGasSettlingHeight;
  }

  /**
   * Gets the effective liquid settling height.
   *
   * @return height [m]
   */
  public double getEffectiveLiquidSettlingHeight() {
    return effectiveLiquidSettlingHeight;
  }

  /**
   * Gets the gas residence time in the gravity section.
   *
   * @return time [s]
   */
  public double getGasResidenceTime() {
    return gasResidenceTime;
  }

  /**
   * Gets the liquid retention time.
   *
   * @return time [s]
   */
  public double getLiquidResidenceTime() {
    return liquidResidenceTime;
  }

  /**
   * Gets the effective gravity section length.
   *
   * @return length [m]
   */
  public double getGravitySectionLength() {
    return gravitySectionLength;
  }

  /**
   * Gets the gas volume.
   *
   * @return gas volume [m3]
   */
  public double getGasVolume() {
    return gasVolume;
  }

  /**
   * Gets the liquid volume.
   *
   * @return liquid volume [m3]
   */
  public double getLiquidVolume() {
    return liquidVolume;
  }

  /**
   * Gets the oil pad thickness (three-phase only).
   *
   * @return oil pad thickness [m]
   */
  public double getOilPadThickness() {
    return oilPadThickness;
  }

  /**
   * Gets the water layer height (three-phase only).
   *
   * @return water layer height [m]
   */
  public double getWaterLayerHeight() {
    return waterLayerHeight;
  }
}
