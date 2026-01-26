package neqsim.process.equipment.pipeline.twophasepipe;

import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedGeometry;

/**
 * Extended pipe section state for the two-fluid model.
 *
 * <p>
 * Extends the base {@link PipeSection} with additional state variables and methods required for the
 * two-fluid conservation equations. Key additions include:
 * </p>
 * <ul>
 * <li>Separate conservative variables for each phase</li>
 * <li>Wetted perimeter and interfacial geometry</li>
 * <li>Wall and interfacial shear stresses</li>
 * <li>Source term storage</li>
 * </ul>
 *
 * <h2>Conservative Variables</h2>
 * <p>
 * For each phase k (gas or liquid), the conservative variables are:
 * </p>
 * <ul>
 * <li>U1 = α_k * ρ_k * A (mass per unit length)</li>
 * <li>U2 = α_k * ρ_k * v_k * A (momentum per unit length)</li>
 * <li>U3 = α_k * ρ_k * E_k * A (energy per unit length)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see PipeSection
 */
public class TwoFluidSection extends PipeSection {

  private static final long serialVersionUID = 1L;

  // ============ Geometry for two-fluid model ============

  /** Gas wetted perimeter (m). */
  private double gasWettedPerimeter;

  /** Liquid wetted perimeter (m). */
  private double liquidWettedPerimeter;

  /** Interfacial width/perimeter (m). */
  private double interfacialWidth;

  /** Liquid level for stratified flow (m). */
  private double stratifiedLiquidLevel;

  /** Gas hydraulic diameter (m). */
  private double gasHydraulicDiameter;

  /** Liquid hydraulic diameter (m). */
  private double liquidHydraulicDiameter;

  // ============ Shear stresses ============

  /** Gas wall shear stress (Pa). */
  private double gasWallShear;

  /** Liquid wall shear stress (Pa). */
  private double liquidWallShear;

  /** Interfacial shear stress (Pa). Positive = gas pushes liquid. */
  private double interfacialShear;

  // ============ Source terms (per unit length) ============

  /** Gas mass source (kg/(m·s)). From liquid evaporation. */
  private double gasMassSource;

  /** Liquid mass source (kg/(m·s)). From gas condensation. */
  private double liquidMassSource;

  /** Gas momentum source (N/m). Wall + interfacial + gravity. */
  private double gasMomentumSource;

  /** Liquid momentum source (N/m). Wall + interfacial + gravity. */
  private double liquidMomentumSource;

  /** Energy source (W/m). Heat transfer + friction work. */
  private double energySource;

  // ============ Conservative variables ============

  /** Gas mass per unit length: α_g * ρ_g * A (kg/m). */
  private double gasMassPerLength;

  /** Liquid mass per unit length: α_L * ρ_L * A (kg/m). */
  private double liquidMassPerLength;

  /** Gas momentum per unit length: α_g * ρ_g * v_g * A (kg/s). */
  private double gasMomentumPerLength;

  /** Liquid momentum per unit length: α_L * ρ_L * v_L * A (kg/s). */
  private double liquidMomentumPerLength;

  /** Mixture energy per unit length: (α_g*ρ_g*E_g + α_L*ρ_L*E_L) * A (J/m). */
  private double energyPerLength;

  // ============ Three-phase support ============

  /** Oil holdup within liquid phase (oil/(oil+water)). */
  private double oilFractionInLiquid = 1.0;

  /** Water cut (water fraction of total liquid). */
  private double waterCut = 0.0;

  /** Water holdup (fraction of pipe area). α_w */
  private double waterHoldup = 0.0;

  /** Oil holdup (fraction of pipe area). α_o = α_L - α_w */
  private double oilHoldup = 0.0;

  /** Water mass per unit length: α_w * ρ_w * A (kg/m). */
  private double waterMassPerLength = 0.0;

  /** Oil mass per unit length: α_o * ρ_o * A (kg/m). */
  private double oilMassPerLength = 0.0;

  /** Water momentum per unit length: α_w * ρ_w * v_w * A (kg/s). */
  private double waterMomentumPerLength = 0.0;

  /** Oil momentum per unit length: α_o * ρ_o * v_o * A (kg/s). */
  private double oilMomentumPerLength = 0.0;

  /** Water velocity (m/s). */
  private double waterVelocity = 0.0;

  /** Oil velocity (m/s). */
  private double oilVelocity = 0.0;

  /** Oil density (kg/m³). */
  private double oilDensity;

  /** Water density (kg/m³). */
  private double waterDensity;

  /** Oil viscosity (Pa·s). */
  private double oilViscosity;

  /** Water viscosity (Pa·s). */
  private double waterViscosity;

  // ============ Terrain tracking fields ============

  /** Flag indicating terrain-induced slug is pending at this section. */
  private boolean terrainSlugPending = false;

  /** Flag indicating severe slugging potential at this section (riser base). */
  private boolean severeSlugPotential = false;

  /** Accumulated liquid volume in low point (m³). */
  private double accumulatedLiquidVolume = 0.0;

  /** Time since last terrain slug event (s). */
  private double timeSinceTerrainSlug = 0.0;

  // ============ Geometry calculator ============
  private transient GeometryCalculator geometryCalc;

  /**
   * Default constructor.
   */
  public TwoFluidSection() {
    super();
  }

  /**
   * Constructor with geometry.
   *
   * @param position Position from inlet (m)
   * @param length Segment length (m)
   * @param diameter Pipe diameter (m)
   * @param inclination Pipe inclination (radians)
   */
  public TwoFluidSection(double position, double length, double diameter, double inclination) {
    super(position, length, diameter, inclination);
  }

  /**
   * Create from existing PipeSection.
   *
   * @param base Base pipe section to copy from
   * @return New TwoFluidSection with copied state
   */
  public static TwoFluidSection fromPipeSection(PipeSection base) {
    TwoFluidSection section = new TwoFluidSection(base.getPosition(), base.getLength(),
        base.getDiameter(), base.getInclination());

    // Copy state
    section.setPressure(base.getPressure());
    section.setTemperature(base.getTemperature());
    section.setGasHoldup(base.getGasHoldup());
    section.setLiquidHoldup(base.getLiquidHoldup());
    section.setGasVelocity(base.getGasVelocity());
    section.setLiquidVelocity(base.getLiquidVelocity());
    section.setGasDensity(base.getGasDensity());
    section.setLiquidDensity(base.getLiquidDensity());
    section.setGasViscosity(base.getGasViscosity());
    section.setLiquidViscosity(base.getLiquidViscosity());
    section.setGasSoundSpeed(base.getGasSoundSpeed());
    section.setLiquidSoundSpeed(base.getLiquidSoundSpeed());
    section.setSurfaceTension(base.getSurfaceTension());
    section.setFlowRegime(base.getFlowRegime());
    section.setElevation(base.getElevation());
    section.setRoughness(base.getRoughness());

    return section;
  }

  /**
   * Update stratified flow geometry based on current liquid holdup.
   */
  public void updateStratifiedGeometry() {
    if (geometryCalc == null) {
      geometryCalc = new GeometryCalculator();
    }

    StratifiedGeometry geom = geometryCalc.calculateFromHoldup(getLiquidHoldup(), getDiameter());

    this.stratifiedLiquidLevel = geom.liquidLevel;
    this.gasWettedPerimeter = geom.gasWettedPerimeter;
    this.liquidWettedPerimeter = geom.liquidWettedPerimeter;
    this.interfacialWidth = geom.interfacialWidth;
    this.gasHydraulicDiameter = geom.gasHydraulicDiameter;
    this.liquidHydraulicDiameter = geom.liquidHydraulicDiameter;
  }

  /**
   * Calculate conservative variables from primitive variables.
   */
  public void updateConservativeVariables() {
    double A = getArea();
    double alphaG = getGasHoldup();
    double alphaL = getLiquidHoldup();
    double rhoG = getGasDensity();
    double rhoL = getLiquidDensity();
    double vG = getGasVelocity();
    double vL = getLiquidVelocity();

    gasMassPerLength = alphaG * rhoG * A;
    liquidMassPerLength = alphaL * rhoL * A;
    gasMomentumPerLength = alphaG * rhoG * vG * A;
    liquidMomentumPerLength = alphaL * rhoL * vL * A;

    // For the 7-equation model, split liquid into oil and water using proper densities
    // waterCut is the volume fraction of water in the liquid phase
    if (waterCut > 0 && waterCut < 1.0) {
      // Three-phase: use individual phase densities for mass calculation
      // Volume fractions within total cross-section
      double alphaO = alphaL * (1.0 - waterCut); // Oil holdup = liquid holdup * oil fraction
      double alphaW = alphaL * waterCut; // Water holdup = liquid holdup * water fraction

      // Use phase-specific densities for mass
      double rhoO = (oilDensity > 100) ? oilDensity : rhoL;
      double rhoW = (waterDensity > 100) ? waterDensity : 1000.0;

      oilMassPerLength = alphaO * rhoO * A;
      waterMassPerLength = alphaW * rhoW * A;

      // Recalculate total liquid mass for consistency
      liquidMassPerLength = oilMassPerLength + waterMassPerLength;

      // Split momentum using volume fractions (same velocity assumption)
      oilMomentumPerLength = oilMassPerLength * vL;
      waterMomentumPerLength = waterMassPerLength * vL;
      liquidMomentumPerLength = oilMomentumPerLength + waterMomentumPerLength;
    } else {
      // Two-phase: all liquid is oil (no water)
      double rhoO = (oilDensity > 100) ? oilDensity : rhoL;
      oilMassPerLength = alphaL * rhoO * A;
      waterMassPerLength = 0;
      oilMomentumPerLength = oilMassPerLength * vL;
      waterMomentumPerLength = 0;
      liquidMassPerLength = oilMassPerLength;
      liquidMomentumPerLength = oilMomentumPerLength;
    }

    // Total energy (internal + kinetic)
    double eG = getGasEnthalpy() - getPressure() / rhoG + 0.5 * vG * vG; // Internal + kinetic
    double eL = getLiquidEnthalpy() - getPressure() / rhoL + 0.5 * vL * vL;
    energyPerLength = (alphaG * rhoG * eG + alphaL * rhoL * eL) * A;
  }

  /**
   * Extract primitive variables from conservative variables.
   *
   * <p>
   * This is the inverse operation of updateConservativeVariables. Requires equation of state
   * evaluation for complete solution. Includes stability guards to prevent NaN values.
   * </p>
   */
  public void extractPrimitiveVariables() {
    double A = getArea();

    // Guard against negative or NaN conservative variables
    gasMassPerLength = Math.max(0, gasMassPerLength);
    oilMassPerLength = Math.max(0, oilMassPerLength);
    waterMassPerLength = Math.max(0, waterMassPerLength);
    liquidMassPerLength = oilMassPerLength + waterMassPerLength;

    if (Double.isNaN(gasMassPerLength))
      gasMassPerLength = 0;
    if (Double.isNaN(oilMassPerLength))
      oilMassPerLength = 0;
    if (Double.isNaN(waterMassPerLength))
      waterMassPerLength = 0;
    if (Double.isNaN(liquidMassPerLength))
      liquidMassPerLength = 0;
    if (Double.isNaN(gasMomentumPerLength))
      gasMomentumPerLength = 0;
    if (Double.isNaN(liquidMomentumPerLength))
      liquidMomentumPerLength = 0;
    if (Double.isNaN(energyPerLength))
      energyPerLength = 0;

    // Extract holdups from mass per length
    double rhoG = getGasDensity();
    if (rhoG < 0.1)
      rhoG = 1.0; // Minimum gas density

    // Use individual phase densities for oil and water
    double rhoO = oilDensity > 100 ? oilDensity : 700.0;
    double rhoW = waterDensity > 100 ? waterDensity : 1000.0;

    double alphaG = 0;
    double alphaO = 0;
    double alphaW = 0;

    // Calculate holdups from conservative variables using consistent densities
    if (A > 0 && rhoG > 0) {
      alphaG = gasMassPerLength / (rhoG * A);
    }
    if (A > 0) {
      alphaO = oilMassPerLength / (rhoO * A);
      alphaW = waterMassPerLength / (rhoW * A);
    }

    // Clamp holdups to valid range
    alphaG = Math.max(0, Math.min(1, alphaG));
    alphaO = Math.max(0, Math.min(1, alphaO));
    alphaW = Math.max(0, Math.min(1, alphaW));

    // Total liquid holdup is sum of oil and water holdups
    double alphaL = alphaO + alphaW;

    // Calculate water cut from mass values BEFORE normalization to preserve ratio
    double calculatedWaterCut = waterCut; // Keep existing as default
    if (alphaL > 1e-10) {
      calculatedWaterCut = alphaW / alphaL;
    } else if (oilMassPerLength + waterMassPerLength > 1e-12) {
      // Calculate from mass if holdups are too small
      calculatedWaterCut = waterMassPerLength / (oilMassPerLength + waterMassPerLength);
    }
    // Clamp water cut to valid range with some margin
    calculatedWaterCut = Math.max(0.001, Math.min(0.999, calculatedWaterCut));

    // Normalize holdups to sum to 1
    double total = alphaG + alphaL;
    if (total > 1e-10) {
      double scale = 1.0 / total;
      alphaG *= scale;
      alphaL *= scale;
      // Redistribute liquid holdup between oil and water using preserved water cut
      alphaW = alphaL * calculatedWaterCut;
      alphaO = alphaL * (1.0 - calculatedWaterCut);
    } else {
      // Default to previous values
      alphaG = getGasHoldup();
      alphaL = getLiquidHoldup();
      if (Double.isNaN(alphaG) || Double.isNaN(alphaL) || alphaG < 0 || alphaL < 0) {
        alphaG = 0.5;
        alphaL = 0.5;
      }
      // Split liquid according to preserved water cut
      alphaW = alphaL * calculatedWaterCut;
      alphaO = alphaL * (1.0 - calculatedWaterCut);
    }

    // Final validation - ensure no NaN
    if (Double.isNaN(alphaG))
      alphaG = 0.5;
    if (Double.isNaN(alphaL))
      alphaL = 0.5;
    if (Double.isNaN(alphaO))
      alphaO = alphaL * (1.0 - calculatedWaterCut);
    if (Double.isNaN(alphaW))
      alphaW = alphaL * calculatedWaterCut;

    setGasHoldup(alphaG);
    setLiquidHoldup(alphaL);
    oilHoldup = alphaO;
    waterHoldup = alphaW;

    // Update water cut for consistency - use the preserved value
    waterCut = calculatedWaterCut;
    oilFractionInLiquid = 1.0 - waterCut;

    // Extract velocities with stability guards
    if (gasMassPerLength > 1e-12) {
      double vG = gasMomentumPerLength / gasMassPerLength;
      // Limit velocity to physical range
      vG = Math.max(-100, Math.min(100, vG));
      if (!Double.isNaN(vG)) {
        setGasVelocity(vG);
      }
    }
    if (liquidMassPerLength > 1e-12) {
      double vL = liquidMomentumPerLength / liquidMassPerLength;
      // Limit velocity to physical range
      vL = Math.max(-50, Math.min(50, vL));
      if (!Double.isNaN(vL)) {
        setLiquidVelocity(vL);
      }
    }

    // Update derived quantities
    updateDerivedQuantities();
  }

  /**
   * Get state vector for numerical integration (7-equation model with water-oil slip).
   *
   * @return Conservative state [gasMass, oilMass, waterMass, gasMom, oilMom, waterMom, energy]
   */
  public double[] getStateVector() {
    return new double[] {gasMassPerLength, oilMassPerLength, waterMassPerLength,
        gasMomentumPerLength, oilMomentumPerLength, waterMomentumPerLength, energyPerLength};
  }

  /**
   * Set state from vector (7-equation model with water-oil slip).
   *
   * @param state Conservative state [gasMass, oilMass, waterMass, gasMom, oilMom, waterMom, energy]
   */
  public void setStateVector(double[] state) {
    if (state.length >= 7) {
      // Full 7-equation model with water-oil slip
      gasMassPerLength = state[0];
      oilMassPerLength = state[1];
      waterMassPerLength = state[2];
      liquidMassPerLength = oilMassPerLength + waterMassPerLength;
      gasMomentumPerLength = state[3];
      oilMomentumPerLength = state[4];
      waterMomentumPerLength = state[5];
      // Combined liquid momentum for compatibility
      liquidMomentumPerLength = oilMomentumPerLength + waterMomentumPerLength;
      energyPerLength = state[6];
    } else if (state.length >= 6) {
      // Legacy 6-equation model (combined liquid momentum)
      gasMassPerLength = state[0];
      oilMassPerLength = state[1];
      waterMassPerLength = state[2];
      liquidMassPerLength = oilMassPerLength + waterMassPerLength;
      gasMomentumPerLength = state[3];
      liquidMomentumPerLength = state[4];
      // Split momentum proportionally to mass
      double totalLiqMass = oilMassPerLength + waterMassPerLength;
      if (totalLiqMass > 1e-10) {
        oilMomentumPerLength = liquidMomentumPerLength * oilMassPerLength / totalLiqMass;
        waterMomentumPerLength = liquidMomentumPerLength * waterMassPerLength / totalLiqMass;
      }
      energyPerLength = state[5];
    } else if (state.length >= 5) {
      // Legacy 5-variable state (no water separation)
      gasMassPerLength = state[0];
      liquidMassPerLength = state[1];
      gasMomentumPerLength = state[2];
      liquidMomentumPerLength = state[3];
      energyPerLength = state[4];
    }
  }

  /**
   * Update water and oil holdups from conservative variables. Should be called after
   * extractPrimitiveVariables if separate tracking is needed. Note: extractPrimitiveVariables now
   * also sets oil/water holdups, so this is mainly for velocity extraction.
   */
  public void updateWaterOilHoldups() {
    // Extract velocities from momenta (with slip)
    if (oilMassPerLength > 1e-12) {
      double vO = oilMomentumPerLength / oilMassPerLength;
      vO = Math.max(-50, Math.min(50, vO)); // Clamp to physical range
      if (!Double.isNaN(vO)) {
        oilVelocity = vO;
      }
    }
    if (waterMassPerLength > 1e-12) {
      double vW = waterMomentumPerLength / waterMassPerLength;
      vW = Math.max(-50, Math.min(50, vW)); // Clamp to physical range
      if (!Double.isNaN(vW)) {
        waterVelocity = vW;
      }
    }
  }

  /**
   * Update conservative variables for water and oil separately.
   */
  public void updateWaterOilConservativeVariables() {
    double A = getArea();

    // Calculate water and oil mass per length from holdups
    waterMassPerLength = waterHoldup * waterDensity * A;
    oilMassPerLength = oilHoldup * oilDensity * A;

    // Calculate water and oil momentum per length from velocities
    waterMomentumPerLength = waterMassPerLength * waterVelocity;
    oilMomentumPerLength = oilMassPerLength * oilVelocity;

    // Combined liquid values
    liquidMassPerLength = waterMassPerLength + oilMassPerLength;
    liquidMomentumPerLength = waterMomentumPerLength + oilMomentumPerLength;
  }

  /**
   * Calculate effective liquid properties for three-phase flow.
   *
   * <p>
   * Combines oil and water properties using volume-weighted averages. Uses local water cut which
   * may vary along the pipeline.
   * </p>
   */
  public void updateThreePhaseProperties() {
    if (waterCut > 0 && waterCut < 1 && waterDensity > 0 && oilDensity > 0) {
      // Volume-weighted density
      double rhoL = oilFractionInLiquid * oilDensity + waterCut * waterDensity;
      setLiquidDensity(rhoL);

      // Viscosity - use Brinkman equation for emulsions
      double muL;
      if (oilViscosity > 0 && waterViscosity > 0) {
        if (oilFractionInLiquid > 0.5) {
          // Oil continuous
          muL = oilViscosity * Math.pow(1.0 - waterCut, -2.5);
        } else {
          // Water continuous
          muL = waterViscosity * Math.pow(1.0 - oilFractionInLiquid, -2.5);
        }
        setLiquidViscosity(muL);
      }
    } else if (waterCut >= 1.0 && waterDensity > 0 && waterViscosity > 0) {
      // Pure water
      setLiquidDensity(waterDensity);
      setLiquidViscosity(waterViscosity);
    } else if (waterCut <= 0 && oilDensity > 0 && oilViscosity > 0) {
      // Pure oil
      setLiquidDensity(oilDensity);
      setLiquidViscosity(oilViscosity);
    }
  }

  /**
   * Calculate gravity force per unit length for each phase.
   *
   * @return [gasGravityForce, liquidGravityForce] in N/m
   */
  public double[] calcGravityForces() {
    double g = 9.81;
    double sinTheta = Math.sin(getInclination());
    double A = getArea();

    double Fg = -getGasHoldup() * getGasDensity() * g * A * sinTheta;
    double Fl = -getLiquidHoldup() * getLiquidDensity() * g * A * sinTheta;

    return new double[] {Fg, Fl};
  }

  // ============ Getters and Setters ============

  public double getGasWettedPerimeter() {
    return gasWettedPerimeter;
  }

  public void setGasWettedPerimeter(double gasWettedPerimeter) {
    this.gasWettedPerimeter = gasWettedPerimeter;
  }

  public double getLiquidWettedPerimeter() {
    return liquidWettedPerimeter;
  }

  public void setLiquidWettedPerimeter(double liquidWettedPerimeter) {
    this.liquidWettedPerimeter = liquidWettedPerimeter;
  }

  public double getInterfacialWidth() {
    return interfacialWidth;
  }

  public void setInterfacialWidth(double interfacialWidth) {
    this.interfacialWidth = interfacialWidth;
  }

  public double getStratifiedLiquidLevel() {
    return stratifiedLiquidLevel;
  }

  public void setStratifiedLiquidLevel(double stratifiedLiquidLevel) {
    this.stratifiedLiquidLevel = stratifiedLiquidLevel;
  }

  public double getGasHydraulicDiameter() {
    return gasHydraulicDiameter;
  }

  public void setGasHydraulicDiameter(double gasHydraulicDiameter) {
    this.gasHydraulicDiameter = gasHydraulicDiameter;
  }

  public double getLiquidHydraulicDiameter() {
    return liquidHydraulicDiameter;
  }

  public void setLiquidHydraulicDiameter(double liquidHydraulicDiameter) {
    this.liquidHydraulicDiameter = liquidHydraulicDiameter;
  }

  public double getGasWallShear() {
    return gasWallShear;
  }

  public void setGasWallShear(double gasWallShear) {
    this.gasWallShear = gasWallShear;
  }

  public double getLiquidWallShear() {
    return liquidWallShear;
  }

  public void setLiquidWallShear(double liquidWallShear) {
    this.liquidWallShear = liquidWallShear;
  }

  public double getInterfacialShear() {
    return interfacialShear;
  }

  public void setInterfacialShear(double interfacialShear) {
    this.interfacialShear = interfacialShear;
  }

  /**
   * Calculate the oil-water interfacial shear stress.
   *
   * <p>
   * Models the shear between oil and water phases when they flow at different velocities. Uses a
   * simplified model based on relative velocity and Stokes settling.
   * </p>
   *
   * @return Oil-water interfacial shear stress (Pa), positive when oil flows faster than water
   */
  public double calcOilWaterInterfacialShear() {
    // Only relevant for three-phase flow
    if (waterHoldup < 0.001 || oilHoldup < 0.001) {
      return 0.0;
    }

    // Relative velocity between oil and water
    double deltaV = oilVelocity - waterVelocity;

    // If no significant slip, no shear
    if (Math.abs(deltaV) < 0.001) {
      return 0.0;
    }

    // Friction factor for oil-water interface (simplified)
    // Higher for stratified flow, lower for dispersed
    double f_ow = 0.01;
    if (waterHoldup > 0.1 && oilHoldup > 0.1) {
      // Stratified regime - higher friction
      f_ow = 0.02;
    }

    // Average density at interface
    double rhoAvg = 0.5 * (oilDensity + waterDensity);

    // Interfacial shear stress (Pa)
    // tau_ow = f_ow * rho * |deltaV| * deltaV / 2
    double tau_ow = f_ow * rhoAvg * Math.abs(deltaV) * deltaV / 2.0;

    return tau_ow;
  }

  public double getGasMassSource() {
    return gasMassSource;
  }

  public void setGasMassSource(double gasMassSource) {
    this.gasMassSource = gasMassSource;
  }

  public double getLiquidMassSource() {
    return liquidMassSource;
  }

  public void setLiquidMassSource(double liquidMassSource) {
    this.liquidMassSource = liquidMassSource;
  }

  public double getGasMomentumSource() {
    return gasMomentumSource;
  }

  public void setGasMomentumSource(double gasMomentumSource) {
    this.gasMomentumSource = gasMomentumSource;
  }

  public double getLiquidMomentumSource() {
    return liquidMomentumSource;
  }

  public void setLiquidMomentumSource(double liquidMomentumSource) {
    this.liquidMomentumSource = liquidMomentumSource;
  }

  public double getEnergySource() {
    return energySource;
  }

  public void setEnergySource(double energySource) {
    this.energySource = energySource;
  }

  public double getGasMassPerLength() {
    return gasMassPerLength;
  }

  public void setGasMassPerLength(double gasMassPerLength) {
    this.gasMassPerLength = gasMassPerLength;
  }

  public double getLiquidMassPerLength() {
    return liquidMassPerLength;
  }

  public void setLiquidMassPerLength(double liquidMassPerLength) {
    this.liquidMassPerLength = liquidMassPerLength;
  }

  public double getGasMomentumPerLength() {
    return gasMomentumPerLength;
  }

  public void setGasMomentumPerLength(double gasMomentumPerLength) {
    this.gasMomentumPerLength = gasMomentumPerLength;
  }

  public double getLiquidMomentumPerLength() {
    return liquidMomentumPerLength;
  }

  public void setLiquidMomentumPerLength(double liquidMomentumPerLength) {
    this.liquidMomentumPerLength = liquidMomentumPerLength;
  }

  public double getEnergyPerLength() {
    return energyPerLength;
  }

  public void setEnergyPerLength(double energyPerLength) {
    this.energyPerLength = energyPerLength;
  }

  public double getOilFractionInLiquid() {
    return oilFractionInLiquid;
  }

  public void setOilFractionInLiquid(double oilFractionInLiquid) {
    this.oilFractionInLiquid = oilFractionInLiquid;
  }

  public double getWaterCut() {
    return waterCut;
  }

  public void setWaterCut(double waterCut) {
    this.waterCut = waterCut;
    this.oilFractionInLiquid = 1.0 - waterCut;
  }

  public double getOilDensity() {
    return oilDensity;
  }

  public void setOilDensity(double oilDensity) {
    this.oilDensity = oilDensity;
  }

  public double getWaterDensity() {
    return waterDensity;
  }

  public void setWaterDensity(double waterDensity) {
    this.waterDensity = waterDensity;
  }

  public double getOilViscosity() {
    return oilViscosity;
  }

  public void setOilViscosity(double oilViscosity) {
    this.oilViscosity = oilViscosity;
  }

  public double getWaterViscosity() {
    return waterViscosity;
  }

  public void setWaterViscosity(double waterViscosity) {
    this.waterViscosity = waterViscosity;
  }

  public double getWaterHoldup() {
    return waterHoldup;
  }

  public void setWaterHoldup(double waterHoldup) {
    this.waterHoldup = waterHoldup;
  }

  public double getOilHoldup() {
    return oilHoldup;
  }

  public void setOilHoldup(double oilHoldup) {
    this.oilHoldup = oilHoldup;
  }

  /**
   * Override getLiquidHoldup to return the total liquid holdup (oil + water).
   * 
   * <p>
   * In TwoFluidSection, the oil and water holdups are tracked separately. This override ensures
   * that getLiquidHoldup() returns their sum for consistent behavior with LiquidAccumulationTracker
   * and other components that depend on total liquid holdup.
   * </p>
   *
   * @return Total liquid holdup (oil + water)
   */
  @Override
  public double getLiquidHoldup() {
    // Return sum of oil and water if they're being used
    double totalLiq = oilHoldup + waterHoldup;
    if (totalLiq > 1e-6) {
      return totalLiq;
    }
    // Fall back to parent's value
    return super.getLiquidHoldup();
  }

  /**
   * Override setLiquidHoldup to also update oil and water holdups proportionally.
   * 
   * <p>
   * When the liquid holdup is changed (e.g., by LiquidAccumulationTracker), the oil and water
   * holdups must be updated to maintain their relative proportions within the liquid phase.
   * </p>
   *
   * @param liquidHoldup the new total liquid holdup
   */
  @Override
  public void setLiquidHoldup(double liquidHoldup) {
    double oldLiquidHoldup = getLiquidHoldup();
    super.setLiquidHoldup(liquidHoldup);

    // Update oil and water holdups proportionally
    if (oldLiquidHoldup > 1e-10 && liquidHoldup > 1e-10) {
      double scaleFactor = liquidHoldup / oldLiquidHoldup;
      oilHoldup = oilHoldup * scaleFactor;
      waterHoldup = waterHoldup * scaleFactor;

      // Ensure they don't exceed the new liquid holdup
      double totalLiqHoldup = oilHoldup + waterHoldup;
      if (totalLiqHoldup > liquidHoldup + 1e-10) {
        double norm = liquidHoldup / totalLiqHoldup;
        oilHoldup *= norm;
        waterHoldup *= norm;
      }
    } else if (liquidHoldup > 1e-10) {
      // Old holdup was near zero - use water cut to distribute
      oilHoldup = liquidHoldup * (1.0 - waterCut);
      waterHoldup = liquidHoldup * waterCut;
    } else {
      // New holdup is near zero
      oilHoldup = 0;
      waterHoldup = 0;
    }
  }

  public double getWaterMassPerLength() {
    return waterMassPerLength;
  }

  public void setWaterMassPerLength(double waterMassPerLength) {
    this.waterMassPerLength = waterMassPerLength;
  }

  public double getOilMassPerLength() {
    return oilMassPerLength;
  }

  public void setOilMassPerLength(double oilMassPerLength) {
    this.oilMassPerLength = oilMassPerLength;
  }

  public double getWaterMomentumPerLength() {
    return waterMomentumPerLength;
  }

  public void setWaterMomentumPerLength(double waterMomentumPerLength) {
    this.waterMomentumPerLength = waterMomentumPerLength;
  }

  public double getOilMomentumPerLength() {
    return oilMomentumPerLength;
  }

  public void setOilMomentumPerLength(double oilMomentumPerLength) {
    this.oilMomentumPerLength = oilMomentumPerLength;
  }

  public double getWaterVelocity() {
    return waterVelocity;
  }

  public void setWaterVelocity(double waterVelocity) {
    this.waterVelocity = waterVelocity;
  }

  public double getOilVelocity() {
    return oilVelocity;
  }

  public void setOilVelocity(double oilVelocity) {
    this.oilVelocity = oilVelocity;
  }

  // ============ Terrain tracking getters/setters ============

  /**
   * Check if a terrain-induced slug is pending at this section.
   *
   * @return true if terrain slug is pending
   */
  public boolean isTerrainSlugPending() {
    return terrainSlugPending;
  }

  /**
   * Set terrain-induced slug pending flag.
   *
   * @param pending true if terrain slug is pending
   */
  public void setTerrainSlugPending(boolean pending) {
    this.terrainSlugPending = pending;
  }

  /**
   * Check if this section has severe slugging potential (typically riser base).
   *
   * @return true if severe slugging potential exists
   */
  public boolean isSevereSlugPotential() {
    return severeSlugPotential;
  }

  /**
   * Set severe slugging potential flag.
   *
   * @param potential true if severe slugging potential exists
   */
  public void setSevereSlugPotential(boolean potential) {
    this.severeSlugPotential = potential;
  }

  /**
   * Get accumulated liquid volume at this section (for low points).
   *
   * @return Accumulated liquid volume (m³)
   */
  public double getAccumulatedLiquidVolume() {
    return accumulatedLiquidVolume;
  }

  /**
   * Set accumulated liquid volume.
   *
   * @param volume Accumulated liquid volume (m³)
   */
  public void setAccumulatedLiquidVolume(double volume) {
    this.accumulatedLiquidVolume = Math.max(0, volume);
  }

  /**
   * Get time since last terrain slug event at this section.
   *
   * @return Time since last terrain slug (s)
   */
  public double getTimeSinceTerrainSlug() {
    return timeSinceTerrainSlug;
  }

  /**
   * Set time since last terrain slug event.
   *
   * @param time Time since last terrain slug (s)
   */
  public void setTimeSinceTerrainSlug(double time) {
    this.timeSinceTerrainSlug = Math.max(0, time);
  }

  @Override
  public TwoFluidSection clone() {
    TwoFluidSection copy = (TwoFluidSection) super.clone();
    // Deep copy transient fields
    copy.geometryCalc = null; // Will be recreated on demand
    return copy;
  }
}
