package neqsim.process.equipment.pipeline.twophasepipe;

import neqsim.process.equipment.pipeline.twophasepipe.numerics.ConservativeStateLimiter;

/**
 * Extended section state for three-phase (gas-oil-water) pipe flow.
 *
 * <p>
 * Extends TwoFluidSection to include a water phase alongside gas and liquid (oil). Uses a layered stratified model
 * where water settles at the bottom, oil in the middle, and gas at the top.
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
public class ThreeFluidSection extends TwoFluidSection {

  /**
   * Version 1 duplicated phase state in this class and its superclass. Those snapshots cannot safely restore the
   * authoritative inventories after removing the shadow fields, so reject them instead of silently losing phase mass.
   */
  private static final long serialVersionUID = 2L;

  // Phase enthalpies supplement the conservative state owned by TwoFluidSection.
  private double waterEnthalpy; // J/kg
  private double oilEnthalpy; // J/kg

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
  private double gasWaterInterfacialWidth; // m, exposed when the oil layer is absent

  // Mass transfer rates
  private double oilEvaporationRate; // kg/(m·s)
  private double waterEvaporationRate; // kg/(m·s)

  /**
   * Default constructor.
   */
  public ThreeFluidSection() {
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
    setWaterHoldup(0.0);
    setWaterDensity(1000.0); // Water at standard conditions
    setWaterViscosity(1e-3); // Pa·s
    this.waterEnthalpy = 0.0;

    setOilHoldup(getLiquidHoldup());
    setLiquidVelocity(getLiquidVelocity());
    setOilDensity(getLiquidDensity());
    setOilViscosity(getLiquidViscosity());
    this.oilEnthalpy = getLiquidEnthalpy();

    this.gasOilSurfaceTension = getSurfaceTension();
    this.oilWaterSurfaceTension = 0.03; // Typical oil-water value
    this.gasWaterSurfaceTension = 0.072; // Water-air at 20°C

    setWaterCut(0.0);
  }

  /**
   * Set holdups for all three phases. Must sum to 1.0.
   *
   * @param gasHoldup Gas holdup (0-1)
   * @param oilHoldup Oil holdup (0-1)
   * @param waterHoldup Water holdup (0-1)
   */
  public void setHoldups(double gasHoldup, double oilHoldup, double waterHoldup) {
    if (!Double.isFinite(gasHoldup) || !Double.isFinite(oilHoldup) || !Double.isFinite(waterHoldup) || gasHoldup < 0.0
        || oilHoldup < 0.0 || waterHoldup < 0.0 || gasHoldup > 1.0 || oilHoldup > 1.0 || waterHoldup > 1.0) {
      throw new IllegalArgumentException("Each holdup must be finite and between zero and one");
    }
    double sum = gasHoldup + oilHoldup + waterHoldup;
    if (Math.abs(sum - 1.0) > 1e-10) {
      throw new IllegalArgumentException("Holdups must sum to 1.0, got " + sum);
    }

    setGasHoldup(gasHoldup);
    setLiquidHoldup(oilHoldup + waterHoldup);
    setOilHoldup(oilHoldup);
    setWaterHoldup(waterHoldup);

    // Update water cut
    double totalLiquid = oilHoldup + waterHoldup;
    if (totalLiquid > 0) {
      setWaterCut(waterHoldup / totalLiquid);
    } else {
      setWaterCut(0.0);
    }
  }

  /**
   * Update conservative variables from primitive variables.
   */
  @Override
  public void updateConservativeVariables() {
    double area = getArea();
    double gasMass = getGasHoldup() * getGasDensity() * area;
    double oilMass = getOilHoldup() * getOilDensity() * area;
    double waterMass = getWaterHoldup() * getWaterDensity() * area;
    initializeNewLiquidPhaseVelocities(oilMass, waterMass);
    double gasVelocity = getGasVelocity();
    double oilVelocity = getOilVelocity();
    double waterVelocity = getWaterVelocity();
    // Total internal plus kinetic energy; pressure work belongs to the energy flux.
    double energy = gasMass * (getGasEnthalpy() + 0.5 * gasVelocity * gasVelocity)
        + oilMass * (getOilEnthalpy() + 0.5 * oilVelocity * oilVelocity)
        + waterMass * (getWaterEnthalpy() + 0.5 * waterVelocity * waterVelocity)
        - getPressure() * area * (getGasHoldup() + getOilHoldup() + getWaterHoldup());
    setStateVector(new double[] { gasMass, oilMass, waterMass, gasMass * gasVelocity, oilMass * oilVelocity,
        waterMass * waterVelocity, energy });
    updateLiquidMixtureProperties();
  }

  /**
   * Extract primitive variables from conservative variables.
   */
  @Override
  public void extractPrimitiveVariables() {
    double[] state = getStateVector();
    ConservativeStateLimiter.enforceThreePhaseMassPositivity(state, null);
    setStateVector(state);
    double area = getArea();
    double alphaG = state[0] > 0.0 ? state[0] / (getGasDensity() * area) : 0.0;
    double alphaO = state[1] > 0.0 ? state[1] / (getOilDensity() * area) : 0.0;
    double alphaW = state[2] > 0.0 ? state[2] / (getWaterDensity() * area) : 0.0;
    double totalHoldup = alphaG + alphaO + alphaW;
    if (totalHoldup > 0.0) {
      // EOS pressure/density recovery remains the caller's responsibility. Normalizing
      // geometry here must never overwrite the transported phase inventories.
      setHoldups(alphaG / totalHoldup, alphaO / totalHoldup, alphaW / totalHoldup);
    } else {
      setHoldups(1.0, 0.0, 0.0);
    }
    setGasVelocity(state[0] > 0.0 ? state[3] / state[0] : 0.0);
    setRecoveredLiquidVelocities(getLiquidVelocity(), state[1] > 0.0 ? state[4] / state[1] : 0.0,
        state[2] > 0.0 ? state[5] / state[2] : 0.0);
    updateLiquidMixtureProperties();
    updateDerivedQuantities();
  }

  /**
   * Refresh aggregate liquid properties without collapsing the independent oil and water momenta.
   */
  private void updateLiquidMixtureProperties() {
    setLiquidDensity(getMixtureLiquidDensity());
    setLiquidViscosity(getMixtureLiquidViscosity());
    setRecoveredLiquidVelocities(getMixtureLiquidVelocity(), getOilVelocity(), getWaterVelocity());
    double liquidMass = getOilMassPerLength() + getWaterMassPerLength();
    setLiquidEnthalpy(liquidMass > 0.0
        ? (getOilMassPerLength() * getOilEnthalpy() + getWaterMassPerLength() * getWaterEnthalpy()) / liquidMass
        : 0.0);
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
    waterArea = getWaterHoldup() * totalArea;
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
    oilArea = getOilHoldup() * totalArea;
    double combinedLiquidArea = waterArea + oilArea;
    double combinedLevel;
    if (combinedLiquidArea > 0 && combinedLiquidArea < totalArea) {
      combinedLevel = calculateLevelFromArea(combinedLiquidArea, d);
      oilLevel = combinedLevel - waterLevel;

      double thetaC = 2.0 * Math.acos(1.0 - 2.0 * combinedLevel / d);
      double combinedWettedPerimeter = r * thetaC;
      oilWettedPerimeter = Math.max(0.0, combinedWettedPerimeter - waterWettedPerimeter);
      gasOilInterfacialWidth = d * Math.sin(thetaC / 2.0);
    } else {
      oilLevel = oilArea > 0 ? (d - waterLevel) : 0;
      oilWettedPerimeter = oilArea > 0.0 ? Math.max(0.0, Math.PI * d - waterWettedPerimeter) : 0.0;
      gasOilInterfacialWidth = 0;
    }
    // When oil disappears, the same horizontal interface directly couples gas and water.
    gasWaterInterfacialWidth = getOilHoldup() == 0.0 ? gasOilInterfacialWidth : 0.0;
    if (getOilHoldup() == 0.0) {
      gasOilInterfacialWidth = 0.0;
      oilWaterInterfacialWidth = 0.0;
    }
  }

  /**
   * Calculate liquid level from cross-sectional area using a bounded, dimensionless bisection.
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

    double fraction = targetArea / totalArea;
    boolean upperHalf = fraction > 0.5;
    double targetFraction = upperHalf ? 1.0 - fraction : fraction;
    double low = 0.0;
    double high = 0.5;
    for (int i = 0; i < 80; i++) {
      double height = 0.5 * (low + high);
      double theta = 2.0 * Math.acos(1.0 - 2.0 * height);
      double areaFraction = (theta - Math.sin(theta)) / (2.0 * Math.PI);
      if (areaFraction < targetFraction) {
        low = height;
      } else {
        high = height;
      }
    }
    double height = 0.5 * (low + high);
    return d * (upperHalf ? 1.0 - height : height);
  }

  /**
   * Get total liquid holdup (oil + water).
   *
   * @return Total liquid holdup
   */
  public double getTotalLiquidHoldup() {
    return getOilHoldup() + getWaterHoldup();
  }

  /**
   * Get mixture liquid velocity (flow-weighted average).
   *
   * @return Mixture liquid velocity (m/s)
   */
  public double getMixtureLiquidVelocity() {
    double totalLiquidFlow = getOilHoldup() * getOilVelocity() + getWaterHoldup() * getWaterVelocity();
    double totalLiquidHoldup = getTotalLiquidHoldup();
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
    double totalLiquidHoldup = getTotalLiquidHoldup();
    if (totalLiquidHoldup > 1e-20) {
      return (getOilHoldup() * getOilDensity() + getWaterHoldup() * getWaterDensity()) / totalLiquidHoldup;
    }
    return getOilDensity();
  }

  /**
   * Get mixture liquid viscosity (holdup-weighted average).
   *
   * @return Mixture liquid viscosity (Pa·s)
   */
  public double getMixtureLiquidViscosity() {
    double totalLiquidHoldup = getTotalLiquidHoldup();
    if (totalLiquidHoldup > 1e-20) {
      return (getOilHoldup() * getOilViscosity() + getWaterHoldup() * getWaterViscosity()) / totalLiquidHoldup;
    }
    return getOilViscosity();
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

  public double getWaterEnthalpy() {
    return waterEnthalpy;
  }

  public void setWaterEnthalpy(double waterEnthalpy) {
    this.waterEnthalpy = waterEnthalpy;
  }

  // Getters and setters for oil phase

  public double getOilEnthalpy() {
    return oilEnthalpy;
  }

  public void setOilEnthalpy(double oilEnthalpy) {
    this.oilEnthalpy = oilEnthalpy;
  }

  // Getters and setters for conservative variables

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

  /**
   * Get the exposed gas-water interface width when the oil layer is absent.
   *
   * @return gas-water interface width in meters
   */
  public double getGasWaterInterfacialWidth() {
    return gasWaterInterfacialWidth;
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
