package neqsim.fluidmechanics.geometrydefinitions.pipe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinition;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeMaterial;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeWall;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeWallBuilder;
import neqsim.fluidmechanics.geometrydefinitions.surrounding.PipeSurroundingEnvironment;

/**
 * Represents the geometry data for a pipe including diameter, wall structure, and surroundings.
 *
 * <p>
 * This class combines the pipe dimensions with the wall material layers and surrounding environment
 * for comprehensive heat transfer calculations. The wall can include multiple layers (pipe steel,
 * insulation, coatings) with proper cylindrical heat transfer calculations.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * // Simple pipe
 * PipeData pipe = new PipeData(0.2); // 0.2m diameter
 * pipe.setPipeWallMaterial(PipeMaterial.CARBON_STEEL, 0.010); // 10mm wall
 *
 * // Insulated pipe
 * PipeData insulatedPipe = new PipeData(0.3);
 * insulatedPipe.setPipeWallMaterial(PipeMaterial.CARBON_STEEL, 0.015);
 * insulatedPipe.addInsulation(PipeMaterial.MINERAL_WOOL, 0.050);
 *
 * // Or use the builder
 * PipeData subseaPipe = PipeData.createFromBuilder(
 *     PipeWallBuilder.subseaPipe(0.25, 0.020, 0.040, 0.070).subseaEnvironment(277.0, 0.5));
 * </pre>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeData extends GeometryDefinition {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PipeData.class);

  /**
   * Default constructor.
   */
  public PipeData() {
    this.wall = new PipeWall();
  }

  /**
   * Constructor with diameter.
   *
   * @param diameter Pipe inner diameter in meters
   */
  public PipeData(double diameter) {
    super(diameter);
    PipeWall pipeWall = new PipeWall(diameter / 2.0);
    this.wall = pipeWall;
  }

  /**
   * Constructor with diameter and roughness.
   *
   * @param diameter Pipe inner diameter in meters
   * @param roughness Pipe inner surface roughness in meters
   */
  public PipeData(double diameter, double roughness) {
    super(diameter, roughness);
    PipeWall pipeWall = new PipeWall(diameter / 2.0);
    this.wall = pipeWall;
  }

  /**
   * Creates a PipeData instance from a PipeWallBuilder.
   *
   * @param builder Configured PipeWallBuilder
   * @return PipeData with wall and environment from builder
   */
  public static PipeData createFromBuilder(PipeWallBuilder builder) {
    PipeWall pipeWall = builder.build();
    double innerDiameter = pipeWall.getInnerRadius() * 2.0;
    PipeData pipeData = new PipeData(innerDiameter);
    pipeData.wall = pipeWall;
    pipeData.setSurroundingEnvironment(builder.buildEnvironment());
    return pipeData;
  }

  // ===== Convenience methods for wall configuration =====

  /**
   * Sets the primary pipe wall material.
   *
   * <p>
   * This clears any existing wall layers and sets a single pipe wall layer.
   * </p>
   *
   * @param material Pipe material
   * @param thickness Wall thickness in meters
   */
  public void setPipeWallMaterial(PipeMaterial material, double thickness) {
    PipeWall pipeWall = getPipeWall();
    pipeWall.clearLayers();
    pipeWall.setInnerRadius(radius);
    pipeWall.addMaterialLayer(new MaterialLayer(material, thickness));
  }

  /**
   * Sets the pipe wall as carbon steel.
   *
   * @param thickness Wall thickness in meters
   */
  public void setCarbonSteelWall(double thickness) {
    setPipeWallMaterial(PipeMaterial.CARBON_STEEL, thickness);
  }

  /**
   * Sets the pipe wall as stainless steel 316.
   *
   * @param thickness Wall thickness in meters
   */
  public void setStainlessSteel316Wall(double thickness) {
    setPipeWallMaterial(PipeMaterial.STAINLESS_STEEL_316, thickness);
  }

  /**
   * Adds an insulation layer to the pipe wall.
   *
   * @param material Insulation material
   * @param thickness Insulation thickness in meters
   */
  public void addInsulation(PipeMaterial material, double thickness) {
    getPipeWall().addMaterialLayer(new MaterialLayer(material, thickness));
  }

  /**
   * Adds mineral wool insulation.
   *
   * @param thickness Insulation thickness in meters
   */
  public void addMineralWoolInsulation(double thickness) {
    addInsulation(PipeMaterial.MINERAL_WOOL, thickness);
  }

  /**
   * Adds polyurethane foam insulation.
   *
   * @param thickness Insulation thickness in meters
   */
  public void addPolyurethaneFoamInsulation(double thickness) {
    addInsulation(PipeMaterial.POLYURETHANE_FOAM, thickness);
  }

  /**
   * Adds a coating layer to the pipe wall.
   *
   * @param material Coating material
   * @param thickness Coating thickness in meters
   */
  public void addCoating(PipeMaterial material, double thickness) {
    getPipeWall().addMaterialLayer(new MaterialLayer(material, thickness));
  }

  /**
   * Adds a concrete weight coating.
   *
   * @param thickness Coating thickness in meters
   */
  public void addConcreteCoating(double thickness) {
    addCoating(PipeMaterial.CONCRETE, thickness);
  }

  // ===== Environment configuration =====

  /**
   * Configures for air exposure.
   *
   * @param airTemperatureK Air temperature in Kelvin
   * @param windVelocityMs Wind velocity in m/s
   */
  public void setAirEnvironment(double airTemperatureK, double windVelocityMs) {
    setSurroundingEnvironment(
        PipeSurroundingEnvironment.exposedToAir(airTemperatureK, windVelocityMs));
  }

  /**
   * Configures for seawater environment.
   *
   * @param seawaterTemperatureK Seawater temperature in Kelvin
   * @param currentVelocityMs Current velocity in m/s
   */
  public void setSeawaterEnvironment(double seawaterTemperatureK, double currentVelocityMs) {
    setSurroundingEnvironment(
        PipeSurroundingEnvironment.subseaPipe(seawaterTemperatureK, currentVelocityMs));
  }

  /**
   * Configures for buried pipe.
   *
   * @param groundTemperatureK Ground temperature in Kelvin
   * @param burialDepthM Burial depth to centerline in meters
   * @param soilType Soil material type
   */
  public void setBuriedEnvironment(double groundTemperatureK, double burialDepthM,
      PipeMaterial soilType) {
    double outerRadius = getPipeWall().getOuterRadius();
    if (outerRadius <= 0) {
      outerRadius = radius; // Use inner radius if no wall layers defined
    }
    setSurroundingEnvironment(PipeSurroundingEnvironment.buriedPipe(groundTemperatureK,
        burialDepthM, outerRadius, soilType));
  }

  // ===== Accessor methods =====

  /**
   * Gets the pipe wall as PipeWall type for cylindrical calculations.
   *
   * @return PipeWall instance
   */
  public PipeWall getPipeWall() {
    if (wall instanceof PipeWall) {
      return (PipeWall) wall;
    }
    // Convert to PipeWall if needed
    PipeWall pipeWall = new PipeWall(radius);
    // Copy layers if possible
    for (int i = 0; i < wall.getNumberOfLayers(); i++) {
      pipeWall.addMaterialLayer(wall.getWallMaterialLayer(i));
    }
    this.wall = pipeWall;
    return pipeWall;
  }

  /**
   * Gets the pipe surrounding environment.
   *
   * @return PipeSurroundingEnvironment instance
   */
  public PipeSurroundingEnvironment getPipeSurroundingEnvironment() {
    if (getSurroundingEnvironment() instanceof PipeSurroundingEnvironment) {
      return (PipeSurroundingEnvironment) getSurroundingEnvironment();
    }
    return new PipeSurroundingEnvironment();
  }

  /**
   * Gets the outer radius of the pipe (including wall and coatings).
   *
   * @return Outer radius in meters
   */
  public double getOuterRadius() {
    return getPipeWall().getOuterRadius();
  }

  /**
   * Gets the total wall thickness.
   *
   * @return Total wall thickness in meters
   */
  public double getTotalWallThickness() {
    return getPipeWall().getTotalThickness();
  }

  /**
   * Calculates the overall heat transfer coefficient using cylindrical coordinates.
   *
   * <p>
   * This includes the wall thermal resistance (cylindrical) and outer environment coefficient.
   * </p>
   *
   * @return Overall U-value in W/(m²·K) referenced to inner surface
   */
  public double calcOverallHeatTransferCoefficient() {
    PipeWall pipeWall = getPipeWall();
    if (pipeWall.getNumberOfLayers() == 0) {
      return getSurroundingEnvironment().getHeatTransferCoefficient();
    }

    double innerRadius = pipeWall.getInnerRadius();
    double outerRadius = pipeWall.getOuterRadius();

    // Wall thermal resistance per unit length
    double wallResistance = pipeWall.calcCylindricalThermalResistancePerLength();

    // Outer environment resistance per unit length
    double outerResistance = 1.0
        / (2.0 * Math.PI * outerRadius * getSurroundingEnvironment().getHeatTransferCoefficient());

    // Total resistance per unit length
    double totalResistance = wallResistance + outerResistance;

    // U-value referenced to inner surface
    return 1.0 / (2.0 * Math.PI * innerRadius * totalResistance);
  }

  /** {@inheritDoc} */
  @Override
  public void setDiameter(double diameter) {
    super.setDiameter(diameter);
    if (wall instanceof PipeWall) {
      ((PipeWall) wall).setInnerRadius(diameter / 2.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public PipeData clone() {
    PipeData clonedPipe = null;
    try {
      clonedPipe = (PipeData) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedPipe;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("PipeData[ID=%.4f m, OD=%.4f m]%n", diameter, getOuterRadius() * 2.0));
    sb.append(getPipeWall().toString());
    sb.append("Environment: ").append(getSurroundingEnvironment().toString());
    return sb.toString();
  }
}
