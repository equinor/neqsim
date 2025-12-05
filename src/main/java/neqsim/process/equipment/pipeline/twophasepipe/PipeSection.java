package neqsim.process.equipment.pipeline.twophasepipe;

/**
 * Represents the state of a single section/cell in the multiphase pipe.
 *
 * <p>
 * Stores both conservative variables (for numerical integration) and primitive variables (for
 * physical interpretation and closure relations).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipeSection implements Cloneable {

  // Geometry
  private double position; // m from inlet
  private double length; // segment length, m
  private double diameter; // m
  private double area; // m²
  private double inclination; // radians (positive = uphill)
  private double elevation; // m (absolute)
  private double roughness; // m

  // Primary state variables
  private double pressure; // Pa
  private double temperature; // K
  private double gasHoldup; // α_g (0-1)
  private double liquidHoldup; // α_L (0-1)
  private double gasVelocity; // m/s
  private double liquidVelocity; // m/s

  // Phase properties (from thermodynamics)
  private double gasDensity; // kg/m³
  private double liquidDensity; // kg/m³
  private double gasViscosity; // Pa·s
  private double liquidViscosity; // Pa·s
  private double gasSoundSpeed; // m/s
  private double liquidSoundSpeed; // m/s
  private double surfaceTension; // N/m
  private double gasEnthalpy; // J/kg
  private double liquidEnthalpy; // J/kg

  // Derived quantities
  private FlowRegime flowRegime;
  private double mixtureVelocity; // m/s
  private double mixtureDensity; // kg/m³
  private double superficialGasVelocity; // m/s
  private double superficialLiquidVelocity; // m/s
  private double liquidLevel; // m (for stratified flow)
  private double frictionPressureGradient; // Pa/m
  private double gravityPressureGradient; // Pa/m

  // Accumulation tracking
  private double accumulatedLiquidVolume; // m³
  private boolean isLowPoint;
  private boolean isHighPoint;

  // Slug tracking
  private boolean isInSlugBody;
  private boolean isInSlugBubble;
  private double slugHoldup;

  // Mass transfer
  private double massTransferRate; // kg/s (positive = liquid to gas)

  /**
   * Flow regimes for two-phase pipe flow.
   */
  public enum FlowRegime {
    /** Stratified smooth flow - calm interface. */
    STRATIFIED_SMOOTH,
    /** Stratified wavy flow - waves on interface. */
    STRATIFIED_WAVY,
    /** Intermittent/slug flow. */
    SLUG,
    /** Annular flow - liquid film on wall, gas core. */
    ANNULAR,
    /** Dispersed bubble flow - bubbles in liquid. */
    DISPERSED_BUBBLE,
    /** Bubble flow - larger bubbles. */
    BUBBLE,
    /** Churn flow - chaotic vertical flow. */
    CHURN,
    /** Mist flow - droplets in gas. */
    MIST,
    /** Single phase gas. */
    SINGLE_PHASE_GAS,
    /** Single phase liquid. */
    SINGLE_PHASE_LIQUID
  }

  /**
   * Default constructor.
   */
  public PipeSection() {
    this.flowRegime = FlowRegime.STRATIFIED_SMOOTH;
  }

  /**
   * Constructor with geometry.
   *
   * @param position Position from inlet (m)
   * @param length Segment length (m)
   * @param diameter Pipe diameter (m)
   * @param inclination Pipe inclination (radians)
   */
  public PipeSection(double position, double length, double diameter, double inclination) {
    this();
    this.position = position;
    this.length = length;
    this.diameter = diameter;
    this.area = Math.PI * diameter * diameter / 4.0;
    this.inclination = inclination;
  }

  /**
   * Calculate derived quantities from primary variables.
   */
  public void updateDerivedQuantities() {
    // Ensure holdups sum to 1
    if (gasHoldup + liquidHoldup > 1.0) {
      double total = gasHoldup + liquidHoldup;
      gasHoldup /= total;
      liquidHoldup /= total;
    }

    // Mixture properties
    mixtureDensity = gasHoldup * gasDensity + liquidHoldup * liquidDensity;
    mixtureVelocity = gasHoldup * gasVelocity + liquidHoldup * liquidVelocity;

    // Superficial velocities
    superficialGasVelocity = gasHoldup * gasVelocity;
    superficialLiquidVelocity = liquidHoldup * liquidVelocity;

    // Liquid level for stratified flow (simplified circular geometry)
    if (flowRegime == FlowRegime.STRATIFIED_SMOOTH || flowRegime == FlowRegime.STRATIFIED_WAVY) {
      liquidLevel = calcLiquidLevelFromHoldup(liquidHoldup, diameter);
    }
  }

  /**
   * Calculate liquid level from holdup for circular pipe.
   *
   * @param holdup Liquid holdup (0-1)
   * @param D Diameter (m)
   * @return Liquid level (m)
   */
  private double calcLiquidLevelFromHoldup(double holdup, double D) {
    // Iterative solution: A_L / A = holdup
    // A_L = (D²/8) * (theta - sin(theta)) where theta = 2*arccos(1 - 2*h/D)
    // Approximate for speed
    if (holdup < 0.01) {
      return 0;
    }
    if (holdup > 0.99) {
      return D;
    }

    // Newton-Raphson iteration
    double h = holdup * D; // Initial guess
    for (int iter = 0; iter < 10; iter++) {
      double theta = 2.0 * Math.acos(1.0 - 2.0 * h / D);
      double areaFrac = (theta - Math.sin(theta)) / (2.0 * Math.PI);
      double error = areaFrac - holdup;
      if (Math.abs(error) < 1e-6) {
        break;
      }
      // Derivative
      double dAdh = 4.0 / (D * Math.PI) * Math.sqrt(h * (D - h)) / D;
      h -= error / dAdh;
      h = Math.max(0, Math.min(D, h));
    }
    return h;
  }

  /**
   * Get conservative variables for numerical integration.
   *
   * @return Array [ρ_g * α_g, ρ_L * α_L, ρ_m * v_m, ρ_m * e]
   */
  public double[] getConservativeVariables() {
    double[] U = new double[4];
    U[0] = gasDensity * gasHoldup; // Gas mass concentration
    U[1] = liquidDensity * liquidHoldup; // Liquid mass concentration
    U[2] = mixtureDensity * mixtureVelocity; // Mixture momentum
    U[3] = mixtureDensity * (gasHoldup * gasEnthalpy + liquidHoldup * liquidEnthalpy); // Energy
    return U;
  }

  /**
   * Set state from conservative variables.
   *
   * @param U Conservative variables
   * @param gasProps Gas density and enthalpy
   * @param liqProps Liquid density and enthalpy
   */
  public void setFromConservativeVariables(double[] U, double[] gasProps, double[] liqProps) {
    this.gasDensity = gasProps[0];
    this.gasEnthalpy = gasProps[1];
    this.liquidDensity = liqProps[0];
    this.liquidEnthalpy = liqProps[1];

    // Invert to get holdups
    this.gasHoldup = U[0] / gasDensity;
    this.liquidHoldup = U[1] / liquidDensity;

    // Normalize
    double total = gasHoldup + liquidHoldup;
    if (total > 0) {
      gasHoldup /= total;
      liquidHoldup /= total;
    }

    // Mixture velocity
    this.mixtureDensity = U[0] + U[1];
    if (mixtureDensity > 0) {
      this.mixtureVelocity = U[2] / mixtureDensity;
    }

    updateDerivedQuantities();
  }

  @Override
  public PipeSection clone() {
    try {
      return (PipeSection) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  // ========== Getters and Setters ==========

  public double getPosition() {
    return position;
  }

  public void setPosition(double position) {
    this.position = position;
  }

  public double getLength() {
    return length;
  }

  public void setLength(double length) {
    this.length = length;
  }

  public double getDiameter() {
    return diameter;
  }

  public void setDiameter(double diameter) {
    this.diameter = diameter;
    this.area = Math.PI * diameter * diameter / 4.0;
  }

  public double getArea() {
    return area;
  }

  public double getInclination() {
    return inclination;
  }

  public void setInclination(double inclination) {
    this.inclination = inclination;
  }

  public double getElevation() {
    return elevation;
  }

  public void setElevation(double elevation) {
    this.elevation = elevation;
  }

  public double getRoughness() {
    return roughness;
  }

  public void setRoughness(double roughness) {
    this.roughness = roughness;
  }

  public double getPressure() {
    return pressure;
  }

  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  public double getTemperature() {
    return temperature;
  }

  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  public double getGasHoldup() {
    return gasHoldup;
  }

  public void setGasHoldup(double gasHoldup) {
    this.gasHoldup = gasHoldup;
  }

  public double getLiquidHoldup() {
    return liquidHoldup;
  }

  public void setLiquidHoldup(double liquidHoldup) {
    this.liquidHoldup = liquidHoldup;
  }

  public double getGasVelocity() {
    return gasVelocity;
  }

  public void setGasVelocity(double gasVelocity) {
    this.gasVelocity = gasVelocity;
  }

  public double getLiquidVelocity() {
    return liquidVelocity;
  }

  public void setLiquidVelocity(double liquidVelocity) {
    this.liquidVelocity = liquidVelocity;
  }

  public double getGasDensity() {
    return gasDensity;
  }

  public void setGasDensity(double gasDensity) {
    this.gasDensity = gasDensity;
  }

  public double getLiquidDensity() {
    return liquidDensity;
  }

  public void setLiquidDensity(double liquidDensity) {
    this.liquidDensity = liquidDensity;
  }

  public double getGasViscosity() {
    return gasViscosity;
  }

  public void setGasViscosity(double gasViscosity) {
    this.gasViscosity = gasViscosity;
  }

  public double getLiquidViscosity() {
    return liquidViscosity;
  }

  public void setLiquidViscosity(double liquidViscosity) {
    this.liquidViscosity = liquidViscosity;
  }

  public double getGasSoundSpeed() {
    return gasSoundSpeed;
  }

  public void setGasSoundSpeed(double gasSoundSpeed) {
    this.gasSoundSpeed = gasSoundSpeed;
  }

  public double getLiquidSoundSpeed() {
    return liquidSoundSpeed;
  }

  public void setLiquidSoundSpeed(double liquidSoundSpeed) {
    this.liquidSoundSpeed = liquidSoundSpeed;
  }

  public double getSurfaceTension() {
    return surfaceTension;
  }

  public void setSurfaceTension(double surfaceTension) {
    this.surfaceTension = surfaceTension;
  }

  public double getGasEnthalpy() {
    return gasEnthalpy;
  }

  public void setGasEnthalpy(double gasEnthalpy) {
    this.gasEnthalpy = gasEnthalpy;
  }

  public double getLiquidEnthalpy() {
    return liquidEnthalpy;
  }

  public void setLiquidEnthalpy(double liquidEnthalpy) {
    this.liquidEnthalpy = liquidEnthalpy;
  }

  public FlowRegime getFlowRegime() {
    return flowRegime;
  }

  public void setFlowRegime(FlowRegime flowRegime) {
    this.flowRegime = flowRegime;
  }

  public double getMixtureVelocity() {
    return mixtureVelocity;
  }

  public double getMixtureDensity() {
    return mixtureDensity;
  }

  public double getSuperficialGasVelocity() {
    return superficialGasVelocity;
  }

  public double getSuperficialLiquidVelocity() {
    return superficialLiquidVelocity;
  }

  public double getLiquidLevel() {
    return liquidLevel;
  }

  public double getFrictionPressureGradient() {
    return frictionPressureGradient;
  }

  public void setFrictionPressureGradient(double frictionPressureGradient) {
    this.frictionPressureGradient = frictionPressureGradient;
  }

  public double getGravityPressureGradient() {
    return gravityPressureGradient;
  }

  public void setGravityPressureGradient(double gravityPressureGradient) {
    this.gravityPressureGradient = gravityPressureGradient;
  }

  public double getAccumulatedLiquidVolume() {
    return accumulatedLiquidVolume;
  }

  public void setAccumulatedLiquidVolume(double accumulatedLiquidVolume) {
    this.accumulatedLiquidVolume = accumulatedLiquidVolume;
  }

  public boolean isLowPoint() {
    return isLowPoint;
  }

  public void setLowPoint(boolean lowPoint) {
    isLowPoint = lowPoint;
  }

  public boolean isHighPoint() {
    return isHighPoint;
  }

  public void setHighPoint(boolean highPoint) {
    isHighPoint = highPoint;
  }

  public boolean isInSlugBody() {
    return isInSlugBody;
  }

  public void setInSlugBody(boolean inSlugBody) {
    isInSlugBody = inSlugBody;
  }

  public boolean isInSlugBubble() {
    return isInSlugBubble;
  }

  public void setInSlugBubble(boolean inSlugBubble) {
    isInSlugBubble = inSlugBubble;
  }

  public double getSlugHoldup() {
    return slugHoldup;
  }

  public void setSlugHoldup(double slugHoldup) {
    this.slugHoldup = slugHoldup;
  }

  public double getMassTransferRate() {
    return massTransferRate;
  }

  public void setMassTransferRate(double massTransferRate) {
    this.massTransferRate = massTransferRate;
  }
}
