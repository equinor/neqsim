package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a wall with multiple material layers for heat transfer calculations.
 *
 * <p>
 * This base class implements a planar (flat wall) heat transfer model using series thermal
 * resistance:
 * </p>
 *
 * <pre>
 * R_total = Σ(thickness_i / k_i)
 * U = 1 / R_total
 * </pre>
 *
 * <p>
 * For cylindrical geometries (pipes), use {@link PipeWall} which accounts for the logarithmic
 * temperature profile through curved walls.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @see PipeWall
 */
public class Wall implements WallInterface {

  /** List of material layers from inside to outside. */
  private ArrayList<MaterialLayer> wallMaterialLayers = new ArrayList<MaterialLayer>();

  /** Cached heat transfer coefficient in W/(m²·K). */
  private double heatTransferCoefficient = 10.0;

  /**
   * Default constructor.
   */
  public Wall() {}

  /**
   * Gets the heat transfer coefficient.
   *
   * @return Heat transfer coefficient in W/(m²·K)
   */
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * Sets the heat transfer coefficient.
   *
   * @param heatTransferCoefficient Heat transfer coefficient in W/(m²·K)
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient) {
    this.heatTransferCoefficient = heatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void addMaterialLayer(MaterialLayer layer) {
    wallMaterialLayers.add(layer);
    heatTransferCoefficient = calcHeatTransferCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public MaterialLayer getWallMaterialLayer(int i) {
    return wallMaterialLayers.get(i);
  }

  /**
   * Gets all material layers.
   *
   * @return Unmodifiable list of material layers
   */
  public List<MaterialLayer> getMaterialLayers() {
    return new ArrayList<>(wallMaterialLayers);
  }

  /**
   * Gets the number of material layers.
   *
   * @return Number of layers
   */
  public int getNumberOfLayers() {
    return wallMaterialLayers.size();
  }

  /**
   * Removes all material layers.
   */
  public void clearLayers() {
    wallMaterialLayers.clear();
    heatTransferCoefficient = 10.0; // Reset to default
  }

  /**
   * Gets the total wall thickness.
   *
   * @return Sum of all layer thicknesses in meters
   */
  public double getTotalThickness() {
    double total = 0.0;
    for (MaterialLayer layer : wallMaterialLayers) {
      total += layer.getThickness();
    }
    return total;
  }

  /**
   * Calculates the planar heat transfer coefficient (series resistance model).
   *
   * <pre>
   * U = 1 / Σ(t_i / k_i)
   * </pre>
   *
   * @return Heat transfer coefficient in W/(m²·K)
   */
  public double calcHeatTransferCoefficient() {
    if (wallMaterialLayers.isEmpty()) {
      return Double.POSITIVE_INFINITY; // No wall = infinite heat transfer
    }

    double totalResistance = 0.0;
    for (MaterialLayer mat : wallMaterialLayers) {
      totalResistance += mat.getThickness() / mat.getConductivity();
    }

    if (totalResistance == 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return 1.0 / totalResistance;
  }

  /**
   * Calculates the thermal resistance of the wall.
   *
   * <pre>
   * R = Σ(t_i / k_i) [m²·K/W]
   * </pre>
   *
   * @return Thermal resistance in m²·K/W
   */
  public double calcThermalResistance() {
    double totalResistance = 0.0;
    for (MaterialLayer mat : wallMaterialLayers) {
      totalResistance += mat.getThickness() / mat.getConductivity();
    }
    return totalResistance;
  }

  /**
   * Calculates the total thermal mass per unit area.
   *
   * @return Thermal mass in J/(m²·K)
   */
  public double calcThermalMassPerArea() {
    double totalMass = 0.0;
    for (MaterialLayer layer : wallMaterialLayers) {
      totalMass += layer.getThermalMassPerArea();
    }
    return totalMass;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Wall[layers=%d, thickness=%.4f m, U=%.3f W/(m²·K)]%n",
        getNumberOfLayers(), getTotalThickness(), getHeatTransferCoefficient()));

    for (int i = 0; i < wallMaterialLayers.size(); i++) {
      sb.append(String.format("  Layer %d: %s%n", i, wallMaterialLayers.get(i)));
    }

    return sb.toString();
  }
}
