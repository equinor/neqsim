package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Represents a radial thermal layer for multi-layer heat transfer calculations in pipelines.
 *
 * <p>
 * This class supports OLGA-style multi-layer thermal modeling with typical layers including:
 * </p>
 * <ul>
 * <li>Steel pipe wall - high conductivity, high thermal mass</li>
 * <li>Corrosion coating - thin, low conductivity</li>
 * <li>Insulation layers (PU foam, aerogel, etc.) - thick, very low conductivity</li>
 * <li>Concrete weight coating - high conductivity, high thermal mass</li>
 * <li>Burial/soil layer - variable conductivity depending on conditions</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Bai, Y. &amp; Bai, Q. "Subsea Pipelines and Risers" - thermal design chapter</li>
 * <li>DNV-OS-F101 - Submarine Pipeline Systems</li>
 * <li>OLGA User Manual - Heat Transfer Models</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class RadialThermalLayer implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Predefined layer material types with default properties.
   */
  public enum MaterialType {
    /** Carbon steel pipe wall. */
    CARBON_STEEL(50.0, 7850.0, 480.0),
    /** Stainless steel (duplex). */
    STAINLESS_STEEL(16.0, 8000.0, 500.0),
    /** Inconel/CRA liner. */
    CRA_LINER(14.0, 8200.0, 460.0),
    /** Fusion-bonded epoxy coating. */
    FBE_COATING(0.3, 1400.0, 1000.0),
    /** Three-layer polyethylene. */
    THREE_LAYER_PE(0.4, 950.0, 2300.0),
    /** Polypropylene coating. */
    POLYPROPYLENE(0.22, 910.0, 1800.0),
    /** Polyurethane foam insulation. */
    PU_FOAM(0.035, 80.0, 1500.0),
    /** Syntactic foam (glass microspheres). */
    SYNTACTIC_FOAM(0.15, 650.0, 1100.0),
    /** Aerogel insulation. */
    AEROGEL(0.015, 150.0, 1000.0),
    /** Microporous insulation. */
    MICROPOROUS(0.022, 250.0, 850.0),
    /** Concrete weight coating. */
    CONCRETE(1.4, 2400.0, 880.0),
    /** Wet soil (buried pipe). */
    SOIL_WET(1.5, 1800.0, 1500.0),
    /** Dry soil (buried pipe). */
    SOIL_DRY(0.8, 1600.0, 1200.0),
    /** Marine growth / biofouling. */
    MARINE_GROWTH(0.6, 1200.0, 3400.0),
    /** Air gap (pipe-in-pipe). */
    AIR_GAP(0.026, 1.2, 1005.0),
    /** Vacuum (pipe-in-pipe with vacuum). */
    VACUUM_GAP(0.001, 0.01, 1005.0),
    /** Generic insulation. */
    GENERIC_INSULATION(0.05, 100.0, 1200.0),
    /** Custom - user-defined properties. */
    CUSTOM(0.0, 0.0, 0.0);

    private final double thermalConductivity;
    private final double density;
    private final double specificHeat;

    MaterialType(double k, double rho, double cp) {
      this.thermalConductivity = k;
      this.density = rho;
      this.specificHeat = cp;
    }

    /**
     * Get default thermal conductivity for this material.
     *
     * @return Thermal conductivity in W/(m·K)
     */
    public double getThermalConductivity() {
      return thermalConductivity;
    }

    /**
     * Get default density for this material.
     *
     * @return Density in kg/m³
     */
    public double getDensity() {
      return density;
    }

    /**
     * Get default specific heat capacity for this material.
     *
     * @return Specific heat in J/(kg·K)
     */
    public double getSpecificHeat() {
      return specificHeat;
    }
  }

  /** Layer name/identifier. */
  private String name;

  /** Layer material type. */
  private MaterialType materialType;

  /** Inner radius of this layer [m]. */
  private double innerRadius;

  /** Outer radius of this layer [m]. */
  private double outerRadius;

  /** Thermal conductivity [W/(m·K)]. */
  private double thermalConductivity;

  /** Density [kg/m³]. */
  private double density;

  /** Specific heat capacity [J/(kg·K)]. */
  private double specificHeat;

  /** Current temperature of layer [K] - for transient calculations. */
  private double temperature = 288.15;

  /** Temperature at previous time step [K]. */
  private double previousTemperature = 288.15;

  /**
   * Default constructor creating a custom layer.
   */
  public RadialThermalLayer() {
    this.name = "Custom";
    this.materialType = MaterialType.CUSTOM;
    this.innerRadius = 0.0;
    this.outerRadius = 0.0;
    this.thermalConductivity = 0.0;
    this.density = 0.0;
    this.specificHeat = 0.0;
  }

  /**
   * Construct a thermal layer with specified dimensions and material type.
   *
   * @param name Layer identifier
   * @param innerRadius Inner radius [m]
   * @param thickness Layer thickness [m]
   * @param material Material type preset
   */
  public RadialThermalLayer(String name, double innerRadius, double thickness,
      MaterialType material) {
    this.name = name;
    this.materialType = material;
    this.innerRadius = innerRadius;
    this.outerRadius = innerRadius + thickness;
    this.thermalConductivity = material.getThermalConductivity();
    this.density = material.getDensity();
    this.specificHeat = material.getSpecificHeat();
  }

  /**
   * Construct a custom thermal layer with user-specified properties.
   *
   * @param name Layer identifier
   * @param innerRadius Inner radius [m]
   * @param thickness Layer thickness [m]
   * @param k Thermal conductivity [W/(m·K)]
   * @param rho Density [kg/m³]
   * @param cp Specific heat [J/(kg·K)]
   */
  public RadialThermalLayer(String name, double innerRadius, double thickness, double k, double rho,
      double cp) {
    this.name = name;
    this.materialType = MaterialType.CUSTOM;
    this.innerRadius = innerRadius;
    this.outerRadius = innerRadius + thickness;
    this.thermalConductivity = k;
    this.density = rho;
    this.specificHeat = cp;
  }

  /**
   * Get the layer thickness.
   *
   * @return Thickness in meters
   */
  public double getThickness() {
    return outerRadius - innerRadius;
  }

  /**
   * Calculate thermal resistance of this cylindrical layer per unit length.
   *
   * <p>
   * For a cylindrical shell: R = ln(r_o/r_i) / (2 * π * k)
   * </p>
   *
   * @return Thermal resistance in (m·K)/W per unit length
   */
  public double getThermalResistance() {
    if (thermalConductivity <= 0 || innerRadius <= 0 || outerRadius <= innerRadius) {
      return 0.0;
    }
    return Math.log(outerRadius / innerRadius) / (2.0 * Math.PI * thermalConductivity);
  }

  /**
   * Calculate thermal mass per unit length.
   *
   * <p>
   * Thermal mass = ρ * Cp * A = ρ * Cp * π * (r_o² - r_i²)
   * </p>
   *
   * @return Thermal mass in J/(K·m)
   */
  public double getThermalMassPerLength() {
    double area = Math.PI * (outerRadius * outerRadius - innerRadius * innerRadius);
    return density * specificHeat * area;
  }

  /**
   * Calculate cross-sectional area of the layer.
   *
   * @return Area in m²
   */
  public double getCrossSectionalArea() {
    return Math.PI * (outerRadius * outerRadius - innerRadius * innerRadius);
  }

  /**
   * Calculate mass per unit length.
   *
   * @return Mass in kg/m
   */
  public double getMassPerLength() {
    return density * getCrossSectionalArea();
  }

  /**
   * Get mean radius of the layer.
   *
   * @return Mean radius in meters
   */
  public double getMeanRadius() {
    return (innerRadius + outerRadius) / 2.0;
  }

  /**
   * Get layer name.
   *
   * @return Layer identifier
   */
  public String getName() {
    return name;
  }

  /**
   * Set layer name.
   *
   * @param name Layer identifier
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Get material type.
   *
   * @return Material type enum
   */
  public MaterialType getMaterialType() {
    return materialType;
  }

  /**
   * Set material type and update properties from preset.
   *
   * @param materialType Material type preset
   */
  public void setMaterialType(MaterialType materialType) {
    this.materialType = materialType;
    if (materialType != MaterialType.CUSTOM) {
      this.thermalConductivity = materialType.getThermalConductivity();
      this.density = materialType.getDensity();
      this.specificHeat = materialType.getSpecificHeat();
    }
  }

  /**
   * Get inner radius.
   *
   * @return Inner radius in meters
   */
  public double getInnerRadius() {
    return innerRadius;
  }

  /**
   * Set inner radius.
   *
   * @param innerRadius Inner radius in meters
   */
  public void setInnerRadius(double innerRadius) {
    this.innerRadius = innerRadius;
  }

  /**
   * Get outer radius.
   *
   * @return Outer radius in meters
   */
  public double getOuterRadius() {
    return outerRadius;
  }

  /**
   * Set outer radius.
   *
   * @param outerRadius Outer radius in meters
   */
  public void setOuterRadius(double outerRadius) {
    this.outerRadius = outerRadius;
  }

  /**
   * Get thermal conductivity.
   *
   * @return Thermal conductivity in W/(m·K)
   */
  public double getThermalConductivity() {
    return thermalConductivity;
  }

  /**
   * Set thermal conductivity (overrides material preset).
   *
   * @param thermalConductivity Thermal conductivity in W/(m·K)
   */
  public void setThermalConductivity(double thermalConductivity) {
    this.thermalConductivity = thermalConductivity;
    this.materialType = MaterialType.CUSTOM;
  }

  /**
   * Get density.
   *
   * @return Density in kg/m³
   */
  public double getDensity() {
    return density;
  }

  /**
   * Set density (overrides material preset).
   *
   * @param density Density in kg/m³
   */
  public void setDensity(double density) {
    this.density = density;
    this.materialType = MaterialType.CUSTOM;
  }

  /**
   * Get specific heat capacity.
   *
   * @return Specific heat in J/(kg·K)
   */
  public double getSpecificHeat() {
    return specificHeat;
  }

  /**
   * Set specific heat (overrides material preset).
   *
   * @param specificHeat Specific heat in J/(kg·K)
   */
  public void setSpecificHeat(double specificHeat) {
    this.specificHeat = specificHeat;
    this.materialType = MaterialType.CUSTOM;
  }

  /**
   * Get current temperature.
   *
   * @return Temperature in Kelvin
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Set current temperature.
   *
   * @param temperature Temperature in Kelvin
   */
  public void setTemperature(double temperature) {
    this.previousTemperature = this.temperature;
    this.temperature = temperature;
  }

  /**
   * Get previous time step temperature.
   *
   * @return Previous temperature in Kelvin
   */
  public double getPreviousTemperature() {
    return previousTemperature;
  }

  /**
   * Initialize temperatures for start of transient.
   *
   * @param initialTemperature Initial temperature in Kelvin
   */
  public void initializeTemperature(double initialTemperature) {
    this.temperature = initialTemperature;
    this.previousTemperature = initialTemperature;
  }

  @Override
  public String toString() {
    return String.format("RadialThermalLayer[%s: ri=%.4f m, ro=%.4f m, k=%.3f W/(m·K), T=%.1f K]",
        name, innerRadius, outerRadius, thermalConductivity, temperature);
  }

  /**
   * Create a copy of this layer.
   *
   * @return Deep copy of the layer
   */
  public RadialThermalLayer copy() {
    RadialThermalLayer copy = new RadialThermalLayer(name, innerRadius, getThickness(),
        thermalConductivity, density, specificHeat);
    copy.materialType = this.materialType;
    copy.temperature = this.temperature;
    copy.previousTemperature = this.previousTemperature;
    return copy;
  }
}
