package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

/**
 * Enumeration of common pipe wall materials with their thermal properties.
 *
 * <p>
 * Provides predefined thermal properties for materials commonly used in pipe construction,
 * insulation, and coatings. Properties are at approximately 20°C (293 K).
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public enum PipeMaterial {

  // Metals - Pipe materials
  /** Carbon steel (ASTM A106/A53). */
  CARBON_STEEL("Carbon Steel", 50.0, 7850.0, 490.0),

  /** Stainless steel 304. */
  STAINLESS_STEEL_304("Stainless Steel 304", 16.0, 8000.0, 500.0),

  /** Stainless steel 316. */
  STAINLESS_STEEL_316("Stainless Steel 316", 14.0, 8000.0, 500.0),

  /** Duplex stainless steel 2205. */
  DUPLEX_2205("Duplex 2205", 19.0, 7800.0, 500.0),

  /** Super duplex stainless steel 2507. */
  SUPER_DUPLEX_2507("Super Duplex 2507", 17.0, 7800.0, 500.0),

  /** Inconel 625. */
  INCONEL_625("Inconel 625", 9.8, 8440.0, 410.0),

  /** Titanium Grade 2. */
  TITANIUM_GRADE_2("Titanium Grade 2", 16.4, 4510.0, 528.0),

  /** Copper. */
  COPPER("Copper", 385.0, 8960.0, 385.0),

  /** Aluminum alloy 6061. */
  ALUMINUM_6061("Aluminum 6061", 167.0, 2700.0, 896.0),

  // Insulation materials
  /** Mineral wool insulation. */
  MINERAL_WOOL("Mineral Wool", 0.04, 100.0, 840.0),

  /** Glass wool insulation. */
  GLASS_WOOL("Glass Wool", 0.035, 48.0, 670.0),

  /** Polyurethane foam (PUR). */
  POLYURETHANE_FOAM("Polyurethane Foam", 0.025, 40.0, 1500.0),

  /** Expanded polystyrene (EPS). */
  POLYSTYRENE_EPS("Polystyrene EPS", 0.033, 25.0, 1300.0),

  /** Extruded polystyrene (XPS). */
  POLYSTYRENE_XPS("Polystyrene XPS", 0.030, 35.0, 1500.0),

  /** Cellular glass insulation (Foamglas). */
  CELLULAR_GLASS("Cellular Glass", 0.045, 120.0, 840.0),

  /** Calcium silicate insulation. */
  CALCIUM_SILICATE("Calcium Silicate", 0.065, 240.0, 840.0),

  /** Aerogel insulation. */
  AEROGEL("Aerogel", 0.015, 150.0, 1000.0),

  /** Perlite insulation. */
  PERLITE("Perlite", 0.05, 130.0, 837.0),

  // Coatings and protective layers
  /** Concrete weight coating. */
  CONCRETE("Concrete", 1.7, 2400.0, 880.0),

  /** Fusion bonded epoxy (FBE). */
  FUSION_BONDED_EPOXY("Fusion Bonded Epoxy", 0.3, 1400.0, 1000.0),

  /** Polyethylene coating. */
  POLYETHYLENE("Polyethylene", 0.4, 940.0, 2300.0),

  /** Polypropylene coating. */
  POLYPROPYLENE("Polypropylene", 0.22, 905.0, 1920.0),

  /** Neoprene rubber. */
  NEOPRENE("Neoprene", 0.25, 1230.0, 2140.0),

  /** Asphalt enamel coating. */
  ASPHALT_ENAMEL("Asphalt Enamel", 0.7, 1150.0, 920.0),

  // Soil types (for buried pipes)
  /** Dry sand. */
  SOIL_DRY_SAND("Dry Sand", 0.3, 1600.0, 800.0),

  /** Wet sand. */
  SOIL_WET_SAND("Wet Sand", 2.0, 1900.0, 1000.0),

  /** Dry clay. */
  SOIL_DRY_CLAY("Dry Clay", 0.5, 1600.0, 890.0),

  /** Wet clay. */
  SOIL_WET_CLAY("Wet Clay", 1.5, 2000.0, 1100.0),

  /** Typical soil (average conditions). */
  SOIL_TYPICAL("Typical Soil", 1.0, 1800.0, 1000.0),

  /** Frozen soil. */
  SOIL_FROZEN("Frozen Soil", 2.5, 1800.0, 1200.0);

  private final String displayName;
  private final double thermalConductivity; // W/(m·K)
  private final double density; // kg/m³
  private final double specificHeatCapacity; // J/(kg·K)

  /**
   * Constructor for PipeMaterial.
   *
   * @param displayName Human-readable material name
   * @param thermalConductivity Thermal conductivity in W/(m·K)
   * @param density Density in kg/m³
   * @param specificHeatCapacity Specific heat capacity in J/(kg·K)
   */
  PipeMaterial(String displayName, double thermalConductivity, double density,
      double specificHeatCapacity) {
    this.displayName = displayName;
    this.thermalConductivity = thermalConductivity;
    this.density = density;
    this.specificHeatCapacity = specificHeatCapacity;
  }

  /**
   * Gets the human-readable material name.
   *
   * @return Material display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the thermal conductivity.
   *
   * @return Thermal conductivity in W/(m·K)
   */
  public double getThermalConductivity() {
    return thermalConductivity;
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
   * Gets the specific heat capacity.
   *
   * @return Specific heat capacity in J/(kg·K)
   */
  public double getSpecificHeatCapacity() {
    return specificHeatCapacity;
  }

  /**
   * Gets the thermal diffusivity.
   *
   * @return Thermal diffusivity in m²/s
   */
  public double getThermalDiffusivity() {
    return thermalConductivity / (density * specificHeatCapacity);
  }

  /**
   * Creates a MaterialLayer from this material with the specified thickness.
   *
   * @param thickness Layer thickness in meters
   * @return A MaterialLayer configured with this material's properties
   */
  public MaterialLayer createLayer(double thickness) {
    return new MaterialLayer(this, thickness);
  }

  /**
   * Checks if this material is an insulation type.
   *
   * @return true if thermal conductivity is less than 0.1 W/(m·K)
   */
  public boolean isInsulation() {
    return thermalConductivity < 0.1;
  }

  /**
   * Checks if this material is a metal.
   *
   * @return true if thermal conductivity is greater than 10 W/(m·K)
   */
  public boolean isMetal() {
    return thermalConductivity > 10.0;
  }

  /**
   * Checks if this is a soil type.
   *
   * @return true if this is a soil material
   */
  public boolean isSoil() {
    return this.name().startsWith("SOIL_");
  }
}
