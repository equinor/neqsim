package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cylindrical pipe wall with multiple material layers.
 *
 * <p>
 * This class extends {@link Wall} to provide proper cylindrical coordinate heat transfer
 * calculations. For a pipe, the thermal resistance through a cylindrical layer is:
 * </p>
 *
 * <pre>
 * R = ln(r_outer / r_inner) / (2π * k * L)
 * </pre>
 *
 * <p>
 * Per unit length, this simplifies to:
 * </p>
 *
 * <pre>
 * R' = ln(r_outer / r_inner) / (2π * k) [K·m/W]
 * </pre>
 *
 * <p>
 * The overall heat transfer coefficient (U-value) referenced to the inner surface is:
 * </p>
 *
 * <pre>
 * U_inner = 1 / (r_inner * Σ(ln(r_i + 1 / r_i) / k_i))
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PipeWall extends Wall {

  /** Inner radius of the pipe in meters. */
  private double innerRadius = 0.0;

  /** List to track the outer radius of each layer. */
  private List<Double> layerOuterRadii = new ArrayList<>();

  /**
   * Default constructor for PipeWall.
   */
  public PipeWall() {
    super();
  }

  /**
   * Constructor with inner radius specification.
   *
   * @param innerRadius Inner pipe radius in meters
   */
  public PipeWall(double innerRadius) {
    super();
    this.innerRadius = innerRadius;
  }

  /**
   * Sets the inner radius of the pipe.
   *
   * <p>
   * This should be set before adding material layers. If layers already exist, their radii will be
   * recalculated.
   * </p>
   *
   * @param innerRadius Inner pipe radius in meters
   */
  public void setInnerRadius(double innerRadius) {
    this.innerRadius = innerRadius;
    recalculateLayerRadii();
  }

  /**
   * Gets the inner radius of the pipe.
   *
   * @return Inner radius in meters
   */
  public double getInnerRadius() {
    return innerRadius;
  }

  /**
   * Gets the outer radius of the pipe wall (including all layers).
   *
   * @return Outer radius in meters
   */
  public double getOuterRadius() {
    if (layerOuterRadii.isEmpty()) {
      return innerRadius;
    }
    return layerOuterRadii.get(layerOuterRadii.size() - 1);
  }

  /**
   * Gets the total wall thickness (sum of all layers).
   *
   * @return Total wall thickness in meters
   */
  public double getTotalThickness() {
    return getOuterRadius() - innerRadius;
  }

  /**
   * Gets the outer radius after a specific layer.
   *
   * @param layerIndex Zero-based layer index
   * @return Outer radius of the specified layer in meters
   */
  public double getLayerOuterRadius(int layerIndex) {
    if (layerIndex < 0 || layerIndex >= layerOuterRadii.size()) {
      throw new IndexOutOfBoundsException("Layer index out of range: " + layerIndex);
    }
    return layerOuterRadii.get(layerIndex);
  }

  /**
   * Gets the inner radius of a specific layer.
   *
   * @param layerIndex Zero-based layer index
   * @return Inner radius of the specified layer in meters
   */
  public double getLayerInnerRadius(int layerIndex) {
    if (layerIndex < 0 || layerIndex >= layerOuterRadii.size()) {
      throw new IndexOutOfBoundsException("Layer index out of range: " + layerIndex);
    }
    return layerIndex == 0 ? innerRadius : layerOuterRadii.get(layerIndex - 1);
  }

  /** {@inheritDoc} */
  @Override
  public void addMaterialLayer(MaterialLayer layer) {
    super.addMaterialLayer(layer);

    // Calculate and store the outer radius for this layer
    double previousOuterRadius =
        layerOuterRadii.isEmpty() ? innerRadius : layerOuterRadii.get(layerOuterRadii.size() - 1);
    double newOuterRadius = previousOuterRadius + layer.getThickness();
    layerOuterRadii.add(newOuterRadius);

    // Recalculate cylindrical heat transfer coefficient
    recalculateCylindricalHeatTransfer();
  }

  /**
   * Recalculates layer radii when inner radius changes.
   */
  private void recalculateLayerRadii() {
    layerOuterRadii.clear();
    double currentRadius = innerRadius;
    int layerCount = getNumberOfLayers();
    for (int i = 0; i < layerCount; i++) {
      MaterialLayer layer = getWallMaterialLayer(i);
      currentRadius += layer.getThickness();
      layerOuterRadii.add(currentRadius);
    }
    if (layerCount > 0) {
      recalculateCylindricalHeatTransfer();
    }
  }

  /**
   * Recalculates the heat transfer coefficient using cylindrical coordinates.
   */
  private void recalculateCylindricalHeatTransfer() {
    if (innerRadius <= 0) {
      // Fall back to planar calculation if inner radius not set
      setHeatTransferCoefficient(calcHeatTransferCoefficient());
      return;
    }
    setHeatTransferCoefficient(calcCylindricalHeatTransferCoefficient());
  }

  /**
   * Calculates the overall heat transfer coefficient for cylindrical geometry.
   *
   * <p>
   * The heat transfer coefficient is referenced to the inner surface area:
   * </p>
   *
   * <pre>
   * U_inner = 1 / (r_inner * Σ(ln(r_i + 1 / r_i) / k_i))
   * </pre>
   *
   * @return Heat transfer coefficient in W/(m²·K) referenced to inner surface
   */
  public double calcCylindricalHeatTransferCoefficient() {
    if (innerRadius <= 0) {
      throw new IllegalStateException("Inner radius must be set before calculating cylindrical "
          + "heat transfer. Use setInnerRadius() first.");
    }

    int layerCount = getNumberOfLayers();
    if (layerCount == 0) {
      return Double.POSITIVE_INFINITY; // No wall resistance
    }

    double sumResistance = 0.0;
    for (int i = 0; i < layerCount; i++) {
      MaterialLayer layer = getWallMaterialLayer(i);
      double rInner = getLayerInnerRadius(i);
      double rOuter = getLayerOuterRadius(i);
      double k = layer.getConductivity();

      // Cylindrical thermal resistance: ln(r_outer/r_inner) / k
      sumResistance += Math.log(rOuter / rInner) / k;
    }

    // U referenced to inner surface: U = 1 / (r_inner * sum_resistance)
    return 1.0 / (innerRadius * sumResistance);
  }

  /**
   * Calculates the thermal resistance per unit length of the pipe wall.
   *
   * <pre>
   * R' = Σ(ln(r_i+1/r_i) / (2π * k_i)) [K·m/W]
   * </pre>
   *
   * @return Thermal resistance per unit length in K·m/W
   */
  public double calcCylindricalThermalResistancePerLength() {
    if (innerRadius <= 0) {
      throw new IllegalStateException("Inner radius must be set for cylindrical calculations");
    }

    int layerCount = getNumberOfLayers();
    if (layerCount == 0) {
      return 0.0;
    }

    double sumResistance = 0.0;
    for (int i = 0; i < layerCount; i++) {
      MaterialLayer layer = getWallMaterialLayer(i);
      double rInner = getLayerInnerRadius(i);
      double rOuter = getLayerOuterRadius(i);
      double k = layer.getConductivity();

      sumResistance += Math.log(rOuter / rInner) / (2.0 * Math.PI * k);
    }

    return sumResistance;
  }

  /**
   * Calculates the heat loss per unit length for given temperature difference.
   *
   * @param innerTemp Inner surface temperature in K
   * @param outerTemp Outer surface temperature in K
   * @return Heat loss per unit length in W/m
   */
  public double calcHeatLossPerLength(double innerTemp, double outerTemp) {
    double resistance = calcCylindricalThermalResistancePerLength();
    if (resistance == 0.0) {
      return 0.0; // No wall, no resistance
    }
    return (innerTemp - outerTemp) / resistance;
  }

  /**
   * Calculates the temperature at a given radial position within the wall.
   *
   * @param radius Radial position in meters (must be between innerRadius and outerRadius)
   * @param innerTemp Temperature at inner surface in K
   * @param outerTemp Temperature at outer surface in K
   * @return Temperature at the specified radius in K
   */
  public double calcTemperatureAtRadius(double radius, double innerTemp, double outerTemp) {
    if (radius < innerRadius || radius > getOuterRadius()) {
      throw new IllegalArgumentException(
          String.format("Radius %.4f m is outside wall bounds [%.4f, %.4f] m", radius, innerRadius,
              getOuterRadius()));
    }

    if (radius == innerRadius) {
      return innerTemp;
    }
    if (radius == getOuterRadius()) {
      return outerTemp;
    }

    // Find which layer contains this radius
    double totalResistance = calcCylindricalThermalResistancePerLength();
    double resistanceToPoint = 0.0;
    double currentInnerRadius = innerRadius;

    int layerCount = getNumberOfLayers();
    for (int i = 0; i < layerCount; i++) {
      MaterialLayer layer = getWallMaterialLayer(i);
      double rOuter = getLayerOuterRadius(i);
      double k = layer.getConductivity();

      if (radius <= rOuter) {
        // This layer contains the target radius
        resistanceToPoint += Math.log(radius / currentInnerRadius) / (2.0 * Math.PI * k);
        break;
      } else {
        // Add full resistance of this layer
        resistanceToPoint += Math.log(rOuter / currentInnerRadius) / (2.0 * Math.PI * k);
        currentInnerRadius = rOuter;
      }
    }

    // Linear interpolation based on thermal resistance
    double fraction = resistanceToPoint / totalResistance;
    return innerTemp - fraction * (innerTemp - outerTemp);
  }

  /**
   * Gets the number of material layers in the wall.
   *
   * @return Number of layers
   */
  public int getNumberOfLayers() {
    return layerOuterRadii.size();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("PipeWall[innerRadius=%.4f m, outerRadius=%.4f m, layers=%d]%n",
        innerRadius, getOuterRadius(), getNumberOfLayers()));

    for (int i = 0; i < getNumberOfLayers(); i++) {
      MaterialLayer layer = getWallMaterialLayer(i);
      sb.append(String.format("  Layer %d: %s (r=%.4f to %.4f m)%n", i, layer.getMaterialName(),
          getLayerInnerRadius(i), getLayerOuterRadius(i)));
    }

    if (innerRadius > 0 && getNumberOfLayers() > 0) {
      sb.append(String.format("  U-value (inner): %.3f W/(m²·K)%n",
          calcCylindricalHeatTransferCoefficient()));
      sb.append(String.format("  R-value (per m): %.4f K·m/W%n",
          calcCylindricalThermalResistancePerLength()));
    }

    return sb.toString();
  }
}
