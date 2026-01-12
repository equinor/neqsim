package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

/**
 * Represents a single material layer in a pipe wall or vessel wall.
 *
 * <p>
 * Each layer has thermal properties (conductivity, density, heat capacity) and geometric properties
 * (thickness). Layers can be created from predefined {@link PipeMaterial} types or with custom
 * properties.
 * </p>
 *
 * <p>
 * <b>Property Units:</b>
 * </p>
 * <ul>
 * <li>Thickness: meters [m]</li>
 * <li>Conductivity: W/(m·K)</li>
 * <li>Density: kg/m³</li>
 * <li>Specific heat capacity (Cp): J/(kg·K)</li>
 * <li>Temperature: Kelvin [K]</li>
 * </ul>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MaterialLayer {

  /** Layer thickness in meters. */
  private double thickness = 0.01;

  /** Thermal conductivity in W/(m·K). */
  private double conductivity = 1.0;

  /** Specific heat capacity in J/(kg·K). */
  private double specificHeatCapacity = 500.0;

  /** Density in kg/m³. */
  private double density = 2000.0;

  /** Temperature at inside surface [K]. */
  private double insideTemperature = 298.15;

  /** Temperature at outside surface [K]. */
  private double outsideTemperature = 298.15;

  /** Material name or identifier. */
  private String materialName = "Unknown";

  /** Reference to PipeMaterial enum if created from it. */
  private PipeMaterial pipeMaterial = null;

  /**
   * Default constructor with generic material properties.
   */
  public MaterialLayer() {}

  /**
   * Constructor for MaterialLayer with material name and thickness.
   *
   * @param material Material name/identifier
   * @param thickness Layer thickness in meters
   */
  public MaterialLayer(String material, double thickness) {
    this.thickness = thickness;
    this.materialName = material;
  }

  /**
   * Constructor for MaterialLayer from a PipeMaterial enum.
   *
   * @param material The PipeMaterial enum value
   * @param thickness Layer thickness in meters
   */
  public MaterialLayer(PipeMaterial material, double thickness) {
    this.thickness = thickness;
    this.pipeMaterial = material;
    this.materialName = material.getDisplayName();
    this.conductivity = material.getThermalConductivity();
    this.density = material.getDensity();
    this.specificHeatCapacity = material.getSpecificHeatCapacity();
  }

  /**
   * Constructor with all thermal properties.
   *
   * @param materialName Material name/identifier
   * @param thickness Layer thickness in meters
   * @param conductivity Thermal conductivity in W/(m·K)
   * @param density Density in kg/m³
   * @param specificHeatCapacity Specific heat capacity in J/(kg·K)
   */
  public MaterialLayer(String materialName, double thickness, double conductivity, double density,
      double specificHeatCapacity) {
    this.materialName = materialName;
    this.thickness = thickness;
    this.conductivity = conductivity;
    this.density = density;
    this.specificHeatCapacity = specificHeatCapacity;
  }

  // ===== Factory methods for common materials =====

  /**
   * Creates a carbon steel layer.
   *
   * @param thickness Wall thickness in meters
   * @return MaterialLayer configured for carbon steel
   */
  public static MaterialLayer carbonSteel(double thickness) {
    return new MaterialLayer(PipeMaterial.CARBON_STEEL, thickness);
  }

  /**
   * Creates a stainless steel 316 layer.
   *
   * @param thickness Wall thickness in meters
   * @return MaterialLayer configured for stainless steel 316
   */
  public static MaterialLayer stainlessSteel316(double thickness) {
    return new MaterialLayer(PipeMaterial.STAINLESS_STEEL_316, thickness);
  }

  /**
   * Creates a mineral wool insulation layer.
   *
   * @param thickness Insulation thickness in meters
   * @return MaterialLayer configured for mineral wool
   */
  public static MaterialLayer mineralWool(double thickness) {
    return new MaterialLayer(PipeMaterial.MINERAL_WOOL, thickness);
  }

  /**
   * Creates a polyurethane foam insulation layer.
   *
   * @param thickness Insulation thickness in meters
   * @return MaterialLayer configured for polyurethane foam
   */
  public static MaterialLayer polyurethaneFoam(double thickness) {
    return new MaterialLayer(PipeMaterial.POLYURETHANE_FOAM, thickness);
  }

  /**
   * Creates a concrete coating layer.
   *
   * @param thickness Coating thickness in meters
   * @return MaterialLayer configured for concrete
   */
  public static MaterialLayer concrete(double thickness) {
    return new MaterialLayer(PipeMaterial.CONCRETE, thickness);
  }

  /**
   * Creates a polyethylene coating layer.
   *
   * @param thickness Coating thickness in meters
   * @return MaterialLayer configured for polyethylene
   */
  public static MaterialLayer polyethylene(double thickness) {
    return new MaterialLayer(PipeMaterial.POLYETHYLENE, thickness);
  }

  // ===== Getters and Setters =====

  /**
   * Gets the layer thickness.
   *
   * @return Thickness in meters
   */
  public double getThickness() {
    return thickness;
  }

  /**
   * Sets the layer thickness.
   *
   * @param thickness Thickness in meters
   */
  public void setThickness(double thickness) {
    this.thickness = thickness;
  }

  /**
   * Gets the thermal conductivity.
   *
   * @return Thermal conductivity in W/(m·K)
   */
  public double getConductivity() {
    return conductivity;
  }

  /**
   * Sets the thermal conductivity.
   *
   * @param conductivity Thermal conductivity in W/(m·K)
   */
  public void setConductivity(double conductivity) {
    this.conductivity = conductivity;
  }

  /**
   * Gets the material density.
   *
   * @return Density in kg/m³
   */
  public double getDensity() {
    return density;
  }

  /**
   * Sets the material density.
   *
   * @param density Density in kg/m³
   */
  public void setDensity(double density) {
    this.density = density;
  }

  /**
   * Gets the specific heat capacity.
   *
   * @return Specific heat capacity in J/(kg·K)
   */
  public double getSpecificHeatCapacity() {
    return specificHeatCapacity;
  }

  /**
   * Sets the specific heat capacity.
   *
   * @param specificHeatCapacity Specific heat capacity in J/(kg·K)
   */
  public void setSpecificHeatCapacity(double specificHeatCapacity) {
    this.specificHeatCapacity = specificHeatCapacity;
  }

  /**
   * Gets the specific heat capacity (Cp).
   *
   * @return Specific heat capacity in J/(kg·K)
   * @deprecated Use {@link #getSpecificHeatCapacity()} instead
   */
  @Deprecated
  public double getCv() {
    return specificHeatCapacity;
  }

  /**
   * Sets the specific heat capacity.
   *
   * @param Cv Specific heat capacity in J/(kg·K)
   * @deprecated Use {@link #setSpecificHeatCapacity(double)} instead
   */
  @Deprecated
  public void setCv(double Cv) {
    this.specificHeatCapacity = Cv;
  }

  /**
   * Gets the inside surface temperature.
   *
   * @return Temperature in Kelvin
   */
  public double getInsideTemperature() {
    return insideTemperature;
  }

  /**
   * Sets the inside surface temperature.
   *
   * @param insideTemperature Temperature in Kelvin
   */
  public void setInsideTemperature(double insideTemperature) {
    this.insideTemperature = insideTemperature;
  }

  /**
   * Gets the outside surface temperature.
   *
   * @return Temperature in Kelvin
   */
  public double getOutsideTemperature() {
    return outsideTemperature;
  }

  /**
   * Sets the outside surface temperature.
   *
   * @param outsideTemperature Temperature in Kelvin
   */
  public void setOutsideTemperature(double outsideTemperature) {
    this.outsideTemperature = outsideTemperature;
  }

  /**
   * Gets the material name.
   *
   * @return Material name/identifier
   */
  public String getMaterialName() {
    return materialName;
  }

  /**
   * Sets the material name.
   *
   * @param materialName Material name/identifier
   */
  public void setMaterialName(String materialName) {
    this.materialName = materialName;
  }

  /**
   * Gets the PipeMaterial enum if this layer was created from one.
   *
   * @return PipeMaterial or null if created with custom properties
   */
  public PipeMaterial getPipeMaterial() {
    return pipeMaterial;
  }

  // ===== Thermal calculations =====

  /**
   * Calculates the planar (flat wall) heat transfer coefficient.
   *
   * <p>
   * For a flat wall: h = k / t
   * </p>
   *
   * @return Heat transfer coefficient in W/(m²·K)
   */
  public double getHeatTransferCoefficient() {
    return conductivity / thickness;
  }

  /**
   * Calculates the thermal resistance for planar (flat wall) geometry.
   *
   * <p>
   * R = t / k [m²·K/W]
   * </p>
   *
   * @return Thermal resistance in m²·K/W
   */
  public double getThermalResistance() {
    return thickness / conductivity;
  }

  /**
   * Calculates the thermal resistance for cylindrical geometry.
   *
   * <p>
   * R = ln(r_outer/r_inner) / (2π * k * L)
   * </p>
   * <p>
   * Per unit length: R' = ln(r_outer/r_inner) / (2π * k) [K·m/W]
   * </p>
   *
   * @param innerRadius Inner radius in meters
   * @return Thermal resistance per unit length in K·m/W
   */
  public double getCylindricalThermalResistance(double innerRadius) {
    if (innerRadius <= 0) {
      throw new IllegalArgumentException("Inner radius must be positive");
    }
    double outerRadius = innerRadius + thickness;
    return Math.log(outerRadius / innerRadius) / (2.0 * Math.PI * conductivity);
  }

  /**
   * Calculates the thermal diffusivity.
   *
   * <p>
   * α = k / (ρ * Cp)
   * </p>
   *
   * @return Thermal diffusivity in m²/s
   */
  public double getThermalDiffusivity() {
    return conductivity / (density * specificHeatCapacity);
  }

  /**
   * Calculates the thermal mass per unit area.
   *
   * <p>
   * m * Cp = ρ * t * Cp [J/(m²·K)]
   * </p>
   *
   * @return Thermal mass per unit area in J/(m²·K)
   */
  public double getThermalMassPerArea() {
    return density * thickness * specificHeatCapacity;
  }

  /**
   * Checks if this material is an insulation (k &lt; 0.1 W/(m·K)).
   *
   * @return true if this is an insulation material
   */
  public boolean isInsulation() {
    return conductivity < 0.1;
  }

  /**
   * Checks if this material is a metal (k &gt; 10 W/(m·K)).
   *
   * @return true if this is a metallic material
   */
  public boolean isMetal() {
    return conductivity > 10.0;
  }

  @Override
  public String toString() {
    return String.format("MaterialLayer[%s: t=%.4f m, k=%.3f W/(m·K), ρ=%.0f kg/m³]", materialName,
        thickness, conductivity, density);
  }
}
