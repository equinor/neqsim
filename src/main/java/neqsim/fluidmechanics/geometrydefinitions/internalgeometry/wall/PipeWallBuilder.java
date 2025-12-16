package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

import neqsim.fluidmechanics.geometrydefinitions.surrounding.PipeSurroundingEnvironment;

/**
 * Builder class for creating common pipe wall configurations.
 *
 * <p>
 * This builder simplifies the creation of pipe walls with common configurations such as:
 * </p>
 * <ul>
 * <li>Bare (uninsulated) pipes</li>
 * <li>Insulated pipes with various insulation types</li>
 * <li>Coated pipes (FBE, polyethylene, concrete)</li>
 * <li>Subsea pipelines with wet insulation and concrete coating</li>
 * <li>Buried pipelines</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * // Create an insulated carbon steel pipe
 * PipeWall wall = PipeWallBuilder.barePipe(0.1, PipeMaterial.CARBON_STEEL, 0.010)
 *     .addInsulation(PipeMaterial.MINERAL_WOOL, 0.050).addCoating(PipeMaterial.POLYETHYLENE, 0.003)
 *     .build();
 *
 * // Create a typical subsea pipeline
 * PipeWall subseaWall = PipeWallBuilder.subseaPipe(0.15, 0.020, 0.040, 0.070).build();
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PipeWallBuilder {

  private final PipeWall pipeWall;
  private PipeSurroundingEnvironment environment;

  /**
   * Private constructor - use static factory methods.
   *
   * @param innerRadius Inner pipe radius in meters
   */
  private PipeWallBuilder(double innerRadius) {
    this.pipeWall = new PipeWall(innerRadius);
    this.environment = new PipeSurroundingEnvironment();
  }

  // ===== Static factory methods =====

  /**
   * Creates a builder for a bare (uninsulated) pipe.
   *
   * @param innerRadius Inner pipe radius in meters
   * @param pipeMaterial Pipe material
   * @param wallThickness Pipe wall thickness in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder barePipe(double innerRadius, PipeMaterial pipeMaterial,
      double wallThickness) {
    PipeWallBuilder builder = new PipeWallBuilder(innerRadius);
    builder.pipeWall.addMaterialLayer(new MaterialLayer(pipeMaterial, wallThickness));
    return builder;
  }

  /**
   * Creates a builder for a carbon steel pipe.
   *
   * @param innerDiameter Inner pipe diameter in meters
   * @param wallThickness Pipe wall thickness in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder carbonSteelPipe(double innerDiameter, double wallThickness) {
    return barePipe(innerDiameter / 2.0, PipeMaterial.CARBON_STEEL, wallThickness);
  }

  /**
   * Creates a builder for a stainless steel 316 pipe.
   *
   * @param innerDiameter Inner pipe diameter in meters
   * @param wallThickness Pipe wall thickness in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder stainlessSteel316Pipe(double innerDiameter, double wallThickness) {
    return barePipe(innerDiameter / 2.0, PipeMaterial.STAINLESS_STEEL_316, wallThickness);
  }

  /**
   * Creates a builder for a pre-configured insulated pipe.
   *
   * @param innerDiameter Inner pipe diameter in meters
   * @param pipeThickness Pipe wall thickness in meters
   * @param insulation Insulation material
   * @param insulationThickness Insulation thickness in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder insulatedPipe(double innerDiameter, double pipeThickness,
      PipeMaterial insulation, double insulationThickness) {
    return carbonSteelPipe(innerDiameter, pipeThickness).addInsulation(insulation,
        insulationThickness);
  }

  /**
   * Creates a builder for a typical subsea pipeline.
   *
   * <p>
   * Standard subsea pipeline configuration:
   * </p>
   * <ol>
   * <li>Carbon steel pipe</li>
   * <li>Fusion bonded epoxy (FBE) corrosion coating</li>
   * <li>Polypropylene insulation</li>
   * <li>Concrete weight coating</li>
   * </ol>
   *
   * @param innerDiameter Inner pipe diameter in meters
   * @param pipeThickness Pipe wall thickness in meters
   * @param insulationThickness Insulation thickness in meters
   * @param concreteThickness Concrete coating thickness in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder subseaPipe(double innerDiameter, double pipeThickness,
      double insulationThickness, double concreteThickness) {
    return carbonSteelPipe(innerDiameter, pipeThickness)
        .addCoating(PipeMaterial.FUSION_BONDED_EPOXY, 0.0004) // ~0.4mm FBE
        .addInsulation(PipeMaterial.POLYPROPYLENE, insulationThickness)
        .addCoating(PipeMaterial.CONCRETE, concreteThickness);
  }

  /**
   * Creates a builder for a typical buried pipeline.
   *
   * <p>
   * Standard buried pipeline configuration:
   * </p>
   * <ol>
   * <li>Carbon steel pipe</li>
   * <li>Fusion bonded epoxy (FBE) corrosion coating</li>
   * <li>Polyethylene outer jacket</li>
   * </ol>
   *
   * @param innerDiameter Inner pipe diameter in meters
   * @param pipeThickness Pipe wall thickness in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder buriedPipe(double innerDiameter, double pipeThickness) {
    return carbonSteelPipe(innerDiameter, pipeThickness)
        .addCoating(PipeMaterial.FUSION_BONDED_EPOXY, 0.0004)
        .addCoating(PipeMaterial.POLYETHYLENE, 0.003);
  }

  /**
   * Creates a builder starting with just the inner radius.
   *
   * @param innerRadius Inner pipe radius in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder withInnerRadius(double innerRadius) {
    return new PipeWallBuilder(innerRadius);
  }

  /**
   * Creates a builder starting with the inner diameter.
   *
   * @param innerDiameter Inner pipe diameter in meters
   * @return PipeWallBuilder for method chaining
   */
  public static PipeWallBuilder withInnerDiameter(double innerDiameter) {
    return new PipeWallBuilder(innerDiameter / 2.0);
  }

  // ===== Builder methods =====

  /**
   * Adds the primary pipe wall layer.
   *
   * @param material Pipe material
   * @param thickness Wall thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addPipeLayer(PipeMaterial material, double thickness) {
    pipeWall.addMaterialLayer(new MaterialLayer(material, thickness));
    return this;
  }

  /**
   * Adds an insulation layer.
   *
   * @param material Insulation material
   * @param thickness Insulation thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addInsulation(PipeMaterial material, double thickness) {
    if (!material.isInsulation() && material.getThermalConductivity() > 1.0) {
      // Warning: not a typical insulation material, but allow it
    }
    pipeWall.addMaterialLayer(new MaterialLayer(material, thickness));
    return this;
  }

  /**
   * Adds a coating layer.
   *
   * @param material Coating material
   * @param thickness Coating thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addCoating(PipeMaterial material, double thickness) {
    pipeWall.addMaterialLayer(new MaterialLayer(material, thickness));
    return this;
  }

  /**
   * Adds a custom material layer.
   *
   * @param name Material name
   * @param thickness Layer thickness in meters
   * @param conductivity Thermal conductivity in W/(m·K)
   * @param density Density in kg/m³
   * @param specificHeat Specific heat capacity in J/(kg·K)
   * @return this builder for method chaining
   */
  public PipeWallBuilder addCustomLayer(String name, double thickness, double conductivity,
      double density, double specificHeat) {
    pipeWall
        .addMaterialLayer(new MaterialLayer(name, thickness, conductivity, density, specificHeat));
    return this;
  }

  /**
   * Adds mineral wool insulation.
   *
   * @param thickness Insulation thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addMineralWoolInsulation(double thickness) {
    return addInsulation(PipeMaterial.MINERAL_WOOL, thickness);
  }

  /**
   * Adds polyurethane foam insulation.
   *
   * @param thickness Insulation thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addPolyurethaneFoamInsulation(double thickness) {
    return addInsulation(PipeMaterial.POLYURETHANE_FOAM, thickness);
  }

  /**
   * Adds aerogel insulation (high-performance).
   *
   * @param thickness Insulation thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addAerogelInsulation(double thickness) {
    return addInsulation(PipeMaterial.AEROGEL, thickness);
  }

  /**
   * Adds a concrete weight coating.
   *
   * @param thickness Coating thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addConcreteCoating(double thickness) {
    return addCoating(PipeMaterial.CONCRETE, thickness);
  }

  /**
   * Adds a polyethylene jacket.
   *
   * @param thickness Jacket thickness in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder addPolyethyleneJacket(double thickness) {
    return addCoating(PipeMaterial.POLYETHYLENE, thickness);
  }

  /**
   * Adds an FBE corrosion coating.
   *
   * @param thickness Coating thickness in meters (typically 0.3-0.5 mm)
   * @return this builder for method chaining
   */
  public PipeWallBuilder addFBECoating(double thickness) {
    return addCoating(PipeMaterial.FUSION_BONDED_EPOXY, thickness);
  }

  // ===== Environment configuration =====

  /**
   * Sets the surrounding environment for air exposure.
   *
   * @param temperatureK Air temperature in Kelvin
   * @param windVelocityMs Wind velocity in m/s
   * @return this builder for method chaining
   */
  public PipeWallBuilder exposedToAir(double temperatureK, double windVelocityMs) {
    this.environment = PipeSurroundingEnvironment.exposedToAir(temperatureK, windVelocityMs);
    return this;
  }

  /**
   * Sets the surrounding environment for subsea operation.
   *
   * @param seawaterTemperatureK Seawater temperature in Kelvin
   * @param currentVelocityMs Current velocity in m/s
   * @return this builder for method chaining
   */
  public PipeWallBuilder subseaEnvironment(double seawaterTemperatureK, double currentVelocityMs) {
    this.environment =
        PipeSurroundingEnvironment.subseaPipe(seawaterTemperatureK, currentVelocityMs);
    return this;
  }

  /**
   * Sets the surrounding environment for buried pipe.
   *
   * @param groundTemperatureK Ground temperature in Kelvin
   * @param burialDepthM Burial depth to pipe centerline in meters
   * @param soilType Soil material type
   * @return this builder for method chaining
   */
  public PipeWallBuilder buriedInSoil(double groundTemperatureK, double burialDepthM,
      PipeMaterial soilType) {
    double outerRadius = pipeWall.getOuterRadius();
    this.environment = PipeSurroundingEnvironment.buriedPipe(groundTemperatureK, burialDepthM,
        outerRadius, soilType);
    return this;
  }

  /**
   * Sets the surrounding environment for a buried pipe with typical soil.
   *
   * @param groundTemperatureK Ground temperature in Kelvin
   * @param burialDepthM Burial depth to pipe centerline in meters
   * @return this builder for method chaining
   */
  public PipeWallBuilder buriedInTypicalSoil(double groundTemperatureK, double burialDepthM) {
    return buriedInSoil(groundTemperatureK, burialDepthM, PipeMaterial.SOIL_TYPICAL);
  }

  // ===== Build methods =====

  /**
   * Builds and returns the configured PipeWall.
   *
   * @return The configured PipeWall
   */
  public PipeWall build() {
    return pipeWall;
  }

  /**
   * Builds and returns the configured surrounding environment.
   *
   * @return The configured PipeSurroundingEnvironment
   */
  public PipeSurroundingEnvironment buildEnvironment() {
    return environment;
  }

  /**
   * Gets a summary of the current configuration.
   *
   * @return String describing the pipe wall configuration
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("PipeWallBuilder Configuration:\n");
    sb.append(pipeWall.toString());
    sb.append("\nEnvironment: ").append(environment.toString());
    return sb.toString();
  }

  /**
   * Calculates the overall U-value including inside and outside film coefficients.
   *
   * @param innerFilmCoefficient Inside film coefficient in W/(m²·K)
   * @return Overall heat transfer coefficient in W/(m²·K)
   */
  public double calcOverallUValue(double innerFilmCoefficient) {
    if (pipeWall.getNumberOfLayers() == 0) {
      return 1.0 / (1.0 / innerFilmCoefficient + 1.0 / environment.getHeatTransferCoefficient());
    }

    double innerRadius = pipeWall.getInnerRadius();
    double outerRadius = pipeWall.getOuterRadius();

    // Wall resistance (per unit length)
    double wallResistance = pipeWall.calcCylindricalThermalResistancePerLength();

    // Inside film resistance (per unit length)
    double innerFilmResistance = 1.0 / (2.0 * Math.PI * innerRadius * innerFilmCoefficient);

    // Outside film resistance (per unit length)
    double outerFilmResistance =
        1.0 / (2.0 * Math.PI * outerRadius * environment.getHeatTransferCoefficient());

    // Total resistance
    double totalResistance = innerFilmResistance + wallResistance + outerFilmResistance;

    // U-value referenced to inner surface
    return 1.0 / (2.0 * Math.PI * innerRadius * totalResistance);
  }
}
