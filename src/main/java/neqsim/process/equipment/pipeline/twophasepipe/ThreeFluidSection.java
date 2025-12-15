package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;

/**
 * Extended section state for three-phase (gas-oil-water) pipe flow.
 *
 * <p>
 * Extends TwoFluidSection to include a water phase alongside gas and liquid (oil). Uses a layered
 * stratified model where water settles at the bottom, oil in the middle, and gas at the top.
 * </p>
 *
 * <h2>Conservation Variables</h2> The three-fluid model tracks 7 PDEs:
 * <ul>
 * <li>Gas mass: ∂(α_g ρ_g A)/∂t + ∂(α_g ρ_g u_g A)/∂x = Γ_go + Γ_gw</li>
 * <li>Oil mass: ∂(α_o ρ_o A)/∂t + ∂(α_o ρ_o u_o A)/∂x = -Γ_go</li>
 * <li>Water mass: ∂(α_w ρ_w A)/∂t + ∂(α_w ρ_w u_w A)/∂x = -Γ_gw</li>
 * <li>Gas momentum</li>
 * <li>Oil momentum</li>
 * <li>Water momentum</li>
 * <li>Mixture energy (or separate phase energies)</li>
 * </ul>
 *
 * <h2>Stratified Three-Layer Geometry</h2>
 * 
 * <pre>
 *         Gas (α_g)
 *     ─────────────────
 *         Oil (α_o)
 *     ─────────────────
 *        Water (α_w)
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ThreeFluidSection extends TwoFluidSection implements Cloneable, Serializable {

  private static final long serialVersionUID = 1L;

  // Water phase properties
  private double waterHoldup; // α_w (0-1)
  private double waterVelocity; // m/s
  private double waterDensity; // kg/m³
  private double waterViscosity; // Pa·s
  private double waterEnthalpy; // J/kg

  // Oil phase properties (renamed from "liquid" for clarity)
  private double oilHoldup; // α_o (0-1)
  private double oilVelocity; // m/s
  private double oilDensity; // kg/m³
  private double oilViscosity; // Pa·s
  private double oilEnthalpy; // J/kg

  // Additional conservative variables (per unit length)
  private double waterMassPerLength; // α_w ρ_w A (kg/m)
  private double waterMomentumPerLength; // α_w ρ_w u_w A (kg/s)
  private double oilMassPerLength; // α_o ρ_o A (kg/m)
  private double oilMomentumPerLength; // α_o ρ_o u_o A (kg/s)

  // Interfacial properties
  private double gasOilSurfaceTension; // N/m
  private double oilWaterSurfaceTension; // N/m
  private double gasWaterSurfaceTension; // N/m

  // Three-layer geometry
  private double waterLevel; // m (height of water layer)
  private double oilLevel; // m (height of oil layer)
  private double waterArea; // m²
  private double oilArea; // m²
  private double waterWettedPerimeter; // m
  private double oilWettedPerimeter; // m
  private double gasOilInterfacialWidth; // m
  private double oilWaterInterfacialWidth; // m

  // Water cut
  private double waterCut; // water volume fraction of total liquid

  // Mass transfer rates
  private double oilEvaporationRate; // kg/(m·s)
  private double waterEvaporationRate; // kg/(m·s)

  /**
   * Default constructor.
   */
  public ThreeFluidSection() {
    super();
    initializeWaterPhase();
  }

  /**
   * Constructor with basic parameters.
   *
   * @param position Distance from inlet (m)
   * @param length Section length (m)
   * @param diameter Pipe diameter (m)
   */
  public ThreeFluidSection(double position, double length, double diameter) {
    super(position, length, diameter, 0.0);
    initializeWaterPhase();
  }

  /**
   * Constructor with inclination.
   *
   * @param position Distance from inlet (m)
   * @param length Section length (m)
   * @param diameter Pipe diameter (m)
   * @param inclination Pipe inclination (radians)
   */
  public ThreeFluidSection(double position, double length, double diameter, double inclination) {
    super(position, length, diameter, inclination);
    initializeWaterPhase();
  }

  /**
   * Initialize water phase with default values.
   */
  private void initializeWaterPhase() {
    this.waterHoldup = 0.0;
    this.waterVelocity = 0.0;
    this.waterDensity = 1000.0; // Water at standard conditions
    this.waterViscosity = 1e-3; // Pa·s
    this.waterEnthalpy = 0.0;

    this.oilHoldup = getLiquidHoldup();
    this.oilVelocity = getLiquidVelocity();
    this.oilDensity = getLiquidDensity();
    this.oilViscosity = getLiquidViscosity();
    this.oilEnthalpy = getLiquidEnthalpy();

    this.gasOilSurfaceTension = getSurfaceTension();
    this.oilWaterSurfaceTension = 0.03; // Typical oil-water value
    this.gasWaterSurfaceTension = 0.072; // Water-air at 20°C

    this.waterCut = 0.0;
  }

  /**
   * Set holdups for all three phases. Must sum to 1.0.
   *
   * @param gasHoldup Gas holdup (0-1)
   * @param oilHoldup Oil holdup (0-1)
   * @param waterHoldup Water holdup (0-1)
   */
  public void setHoldups(double gasHoldup, double oilHoldup, double waterHoldup) {
    double sum = gasHoldup + oilHoldup + waterHoldup;
    if (Math.abs(sum - 1.0) > 1e-10) {
      throw new IllegalArgumentException("Holdups must sum to 1.0, got " + sum);
    }

    setGasHoldup(gasHoldup);
    this.oilHoldup = oilHoldup;
    this.waterHoldup = waterHoldup;

    // Update parent liquid holdup
    setLiquidHoldup(oilHoldup + waterHoldup);

    // Update water cut
    double totalLiquid = oilHoldup + waterHoldup;
    if (totalLiquid > 0) {
      this.waterCut = waterHoldup / totalLiquid;
    } else {
      this.waterCut = 0.0;
    }
  }

  /**
   * Update conservative variables from primitive variables.
   */
  @Override
  public void updateConservativeVariables() {
    super.updateConservativeVariables();

    double area = getArea();

    // Water phase
    waterMassPerLength = waterHoldup * waterDensity * area;
    waterMomentumPerLength = waterMassPerLength * waterVelocity;

    // Oil phase
    oilMassPerLength = oilHoldup * oilDensity * area;
    oilMomentumPerLength = oilMassPerLength * oilVelocity;
  }

  /**
   * Extract primitive variables from conservative variables.
   */
  @Override
  public void extractPrimitiveVariables() {
    super.extractPrimitiveVariables();

    double area = getArea();

    // Water phase
    if (waterMassPerLength > 1e-20) {
      waterHoldup = waterMassPerLength / (waterDensity * area);
      waterVelocity = waterMomentumPerLength / waterMassPerLength;
    } else {
      waterHoldup = 0.0;
      waterVelocity = 0.0;
    }

    // Oil phase
    if (oilMassPerLength > 1e-20) {
      oilHoldup = oilMassPerLength / (oilDensity * area);
      oilVelocity = oilMomentumPerLength / oilMassPerLength;
    } else {
      oilHoldup = 0.0;
      oilVelocity = 0.0;
    }

    // Update total liquid holdup
    setLiquidHoldup(oilHoldup + waterHoldup);

    // Update water cut
    double totalLiquid = oilHoldup + waterHoldup;
    if (totalLiquid > 0) {
      waterCut = waterHoldup / totalLiquid;
    }
  }

  /**
   * Calculate three-layer stratified geometry.
   *
   * <p>
   * Assumes circular pipe with three stratified layers: water at bottom, oil in middle, gas at top.
   * </p>
   */
  public void updateThreeLayerGeometry() {
    double d = getDiameter();
    double r = d / 2.0;
    double totalArea = getArea();

    // Calculate water layer (bottom)
    waterArea = waterHoldup * totalArea;
    if (waterArea > 0 && waterArea < totalArea) {
      waterLevel = calculateLevelFromArea(waterArea, d);
      double thetaW = 2.0 * Math.acos(1.0 - 2.0 * waterLevel / d);
      waterWettedPerimeter = r * thetaW;
      oilWaterInterfacialWidth = d * Math.sin(thetaW / 2.0);
    } else {
      waterLevel = waterArea > 0 ? d : 0;
      waterWettedPerimeter = waterArea > 0 ? Math.PI * d : 0;
      oilWaterInterfacialWidth = 0;
    }

    // Calculate oil layer (middle)
    oilArea = oilHoldup * totalArea;
    double combinedLiquidArea = waterArea + oilArea;
    double combinedLevel;
    if (combinedLiquidArea > 0 && combinedLiquidArea < totalArea) {
      combinedLevel = calculateLevelFromArea(combinedLiquidArea, d);
      oilLevel = combinedLevel - waterLevel;

      double thetaC = 2.0 * Math.acos(1.0 - 2.0 * combinedLevel / d);
      double combinedWettedPerimeter = r * thetaC;
      oilWettedPerimeter = combinedWettedPerimeter - waterWettedPerimeter;
      gasOilInterfacialWidth = d * Math.sin(thetaC / 2.0);
    } else {
      oilLevel = oilArea > 0 ? (d - waterLevel) : 0;
      oilWettedPerimeter = 0;
      gasOilInterfacialWidth = 0;
    }
  }

  /**
   * Calculate liquid level from cross-sectional area using Newton iteration.
   *
   * @param targetArea target cross-sectional area in m²
   * @param d pipe diameter in meters
   * @return liquid level height in meters
   */
  private double calculateLevelFromArea(double targetArea, double d) {
    double r = d / 2.0;
    double totalArea = Math.PI * r * r;

    // Handle edge cases
    if (targetArea <= 0) {
      return 0.0;
    }
    if (targetArea >= totalArea) {
      return d;
    }

    // Initial guess based on linear approximation
    double h = d * targetArea / totalArea;
    h = Math.max(1e-10, Math.min(d - 1e-10, h));

    for (int i = 0; i < 50; i++) {
      // Clamp h to valid range
      h = Math.max(1e-10, Math.min(d - 1e-10, h));

      double arg = 1.0 - 2.0 * h / d;
      arg = Math.max(-0.9999, Math.min(0.9999, arg));

      double theta = 2.0 * Math.acos(arg);
      double area = r * r * (theta - Math.sin(theta)) / 2.0;

      // Derivative: dA/dh = 2*r*sqrt(h*(d-h))
      double sqrtTerm = Math.sqrt(Math.max(0.0, h * (d - h)));
      double dArea_dh = 2.0 * r * sqrtTerm;

      double error = area - targetArea;
      if (Math.abs(error) < 1e-12 * totalArea) {
        break;
      }

      if (dArea_dh > 1e-12) {
        double correction = error / dArea_dh;
        // Limit step size to prevent overshooting
        correction = Math.max(-h * 0.5, Math.min((d - h) * 0.5, correction));
        h = h - correction;
      }
    }

    return Math.max(0.0, Math.min(d, h));
  }

  /**
   * Get total liquid holdup (oil + water).
   *
   * @return Total liquid holdup
   */
  public double getTotalLiquidHoldup() {
    return oilHoldup + waterHoldup;
  }

  /**
   * Get mixture liquid velocity (flow-weighted average).
   *
   * @return Mixture liquid velocity (m/s)
   */
  public double getMixtureLiquidVelocity() {
    double totalLiquidFlow = oilHoldup * oilVelocity + waterHoldup * waterVelocity;
    double totalLiquidHoldup = oilHoldup + waterHoldup;
    if (totalLiquidHoldup > 1e-20) {
      return totalLiquidFlow / totalLiquidHoldup;
    }
    return 0.0;
  }

  /**
   * Get mixture liquid density (holdup-weighted average).
   *
   * @return Mixture liquid density (kg/m³)
   */
  public double getMixtureLiquidDensity() {
    double totalLiquidHoldup = oilHoldup + waterHoldup;
    if (totalLiquidHoldup > 1e-20) {
      return (oilHoldup * oilDensity + waterHoldup * waterDensity) / totalLiquidHoldup;
    }
    return oilDensity;
  }

  /**
   * Get mixture liquid viscosity (holdup-weighted average).
   *
   * @return Mixture liquid viscosity (Pa·s)
   */
  public double getMixtureLiquidViscosity() {
    double totalLiquidHoldup = oilHoldup + waterHoldup;
    if (totalLiquidHoldup > 1e-20) {
      return (oilHoldup * oilViscosity + waterHoldup * waterViscosity) / totalLiquidHoldup;
    }
    return oilViscosity;
  }

  /**
   * Create a deep copy of this section.
   *
   * @return Cloned section
   */
  @Override
  public ThreeFluidSection clone() {
    ThreeFluidSection clone = (ThreeFluidSection) super.clone();
    return clone;
  }

  // Getters and setters for water phase

  public double getWaterHoldup() {
    return waterHoldup;
  }

  public void setWaterHoldup(double waterHoldup) {
    this.waterHoldup = waterHoldup;
  }

  public double getWaterVelocity() {
    return waterVelocity;
  }

  public void setWaterVelocity(double waterVelocity) {
    this.waterVelocity = waterVelocity;
  }

  public double getWaterDensity() {
    return waterDensity;
  }

  public void setWaterDensity(double waterDensity) {
    this.waterDensity = waterDensity;
  }

  public double getWaterViscosity() {
    return waterViscosity;
  }

  public void setWaterViscosity(double waterViscosity) {
    this.waterViscosity = waterViscosity;
  }

  public double getWaterEnthalpy() {
    return waterEnthalpy;
  }

  public void setWaterEnthalpy(double waterEnthalpy) {
    this.waterEnthalpy = waterEnthalpy;
  }

  // Getters and setters for oil phase

  public double getOilHoldup() {
    return oilHoldup;
  }

  public void setOilHoldup(double oilHoldup) {
    this.oilHoldup = oilHoldup;
  }

  public double getOilVelocity() {
    return oilVelocity;
  }

  public void setOilVelocity(double oilVelocity) {
    this.oilVelocity = oilVelocity;
  }

  public double getOilDensity() {
    return oilDensity;
  }

  public void setOilDensity(double oilDensity) {
    this.oilDensity = oilDensity;
  }

  public double getOilViscosity() {
    return oilViscosity;
  }

  public void setOilViscosity(double oilViscosity) {
    this.oilViscosity = oilViscosity;
  }

  public double getOilEnthalpy() {
    return oilEnthalpy;
  }

  public void setOilEnthalpy(double oilEnthalpy) {
    this.oilEnthalpy = oilEnthalpy;
  }

  // Getters and setters for conservative variables

  public double getWaterMassPerLength() {
    return waterMassPerLength;
  }

  public void setWaterMassPerLength(double waterMassPerLength) {
    this.waterMassPerLength = waterMassPerLength;
  }

  public double getWaterMomentumPerLength() {
    return waterMomentumPerLength;
  }

  public void setWaterMomentumPerLength(double waterMomentumPerLength) {
    this.waterMomentumPerLength = waterMomentumPerLength;
  }

  public double getOilMassPerLength() {
    return oilMassPerLength;
  }

  public void setOilMassPerLength(double oilMassPerLength) {
    this.oilMassPerLength = oilMassPerLength;
  }

  public double getOilMomentumPerLength() {
    return oilMomentumPerLength;
  }

  public void setOilMomentumPerLength(double oilMomentumPerLength) {
    this.oilMomentumPerLength = oilMomentumPerLength;
  }

  // Getters for geometry

  public double getWaterLevel() {
    return waterLevel;
  }

  public double getOilLevel() {
    return oilLevel;
  }

  public double getWaterArea() {
    return waterArea;
  }

  public double getOilArea() {
    return oilArea;
  }

  public double getWaterWettedPerimeter() {
    return waterWettedPerimeter;
  }

  public double getOilWettedPerimeter() {
    return oilWettedPerimeter;
  }

  public double getGasOilInterfacialWidth() {
    return gasOilInterfacialWidth;
  }

  public double getOilWaterInterfacialWidth() {
    return oilWaterInterfacialWidth;
  }

  // Getters and setters for surface tensions

  public double getGasOilSurfaceTension() {
    return gasOilSurfaceTension;
  }

  public void setGasOilSurfaceTension(double gasOilSurfaceTension) {
    this.gasOilSurfaceTension = gasOilSurfaceTension;
  }

  public double getOilWaterSurfaceTension() {
    return oilWaterSurfaceTension;
  }

  public void setOilWaterSurfaceTension(double oilWaterSurfaceTension) {
    this.oilWaterSurfaceTension = oilWaterSurfaceTension;
  }

  public double getGasWaterSurfaceTension() {
    return gasWaterSurfaceTension;
  }

  public void setGasWaterSurfaceTension(double gasWaterSurfaceTension) {
    this.gasWaterSurfaceTension = gasWaterSurfaceTension;
  }

  public double getWaterCut() {
    return waterCut;
  }

  public void setWaterCut(double waterCut) {
    this.waterCut = waterCut;
  }

  public double getOilEvaporationRate() {
    return oilEvaporationRate;
  }

  public void setOilEvaporationRate(double oilEvaporationRate) {
    this.oilEvaporationRate = oilEvaporationRate;
  }

  public double getWaterEvaporationRate() {
    return waterEvaporationRate;
  }

  public void setWaterEvaporationRate(double waterEvaporationRate) {
    this.waterEvaporationRate = waterEvaporationRate;
  }
}
