package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.MUSCLReconstructor;

/**
 * Two-fluid conservation equations for transient multiphase pipe flow.
 *
 * <p>
 * Implements the spatial discretization of the two-fluid model equations:
 * </p>
 *
 * <h2>Conservation Equations</h2>
 * <ul>
 * <li><b>Gas Mass:</b> ∂/∂t(α_g·ρ_g·A) + ∂/∂x(α_g·ρ_g·v_g·A) = Γ_g</li>
 * <li><b>Liquid Mass:</b> ∂/∂t(α_L·ρ_L·A) + ∂/∂x(α_L·ρ_L·v_L·A) = Γ_L</li>
 * <li><b>Gas Momentum:</b> ∂/∂t(α_g·ρ_g·v_g·A) + ∂/∂x(α_g·ρ_g·v_g²·A) = -α_g·A·∂P/∂x - τ_wg·S_g -
 * τ_i·S_i - α_g·ρ_g·g·A·sin(θ)</li>
 * <li><b>Liquid Momentum:</b> ∂/∂t(α_L·ρ_L·v_L·A) + ∂/∂x(α_L·ρ_L·v_L²·A) = -α_L·A·∂P/∂x - τ_wL·S_L
 * + τ_i·S_i - α_L·ρ_L·g·A·sin(θ)</li>
 * <li><b>Mixture Energy:</b> ∂/∂t(E_mix·A) + ∂/∂x((E_mix+P)·v_mix·A) = Q_wall + W_friction</li>
 * </ul>
 *
 * <h2>Variable Indices</h2>
 * <ul>
 * <li>0: Gas mass per length (α_g·ρ_g·A)</li>
 * <li>1: Liquid mass per length (α_L·ρ_L·A)</li>
 * <li>2: Gas momentum per length (α_g·ρ_g·v_g·A)</li>
 * <li>3: Liquid momentum per length (α_L·ρ_L·v_L·A)</li>
 * <li>4: Mixture energy per length (E_mix·A)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TwoFluidConservationEquations implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;

  /**
   * Number of conservation equations (7 for three-phase with water-oil slip: gas mass, oil mass,
   * water mass, gas momentum, oil momentum, water momentum, energy).
   */
  public static final int NUM_EQUATIONS = 7;

  /** Index for gas mass. */
  public static final int IDX_GAS_MASS = 0;

  /** Index for oil mass (separate from water). */
  public static final int IDX_OIL_MASS = 1;

  /** Index for water mass (separate conservation equation). */
  public static final int IDX_WATER_MASS = 2;

  /** Index for gas momentum. */
  public static final int IDX_GAS_MOMENTUM = 3;

  /** Index for oil momentum (separate from water for slip). */
  public static final int IDX_OIL_MOMENTUM = 4;

  /** Index for water momentum (separate for water-oil slip). */
  public static final int IDX_WATER_MOMENTUM = 5;

  /** Index for energy. */
  public static final int IDX_ENERGY = 6;

  /** Legacy index for liquid momentum (for compatibility - uses oil momentum). */
  public static final int IDX_LIQUID_MOMENTUM = 4;

  // Closure models
  private WallFriction wallFriction;
  private InterfacialFriction interfacialFriction;
  private FlowRegimeDetector flowRegimeDetector;
  private GeometryCalculator geometryCalc;

  // Numerical methods
  private AUSMPlusFluxCalculator fluxCalculator;
  private MUSCLReconstructor reconstructor;

  // Settings
  private boolean includeEnergyEquation = true;
  private boolean includeMassTransfer = false;
  private double massTransferCoefficient = 0.01; // kg/(m³·s·Pa)

  /** Enable heat transfer to surroundings. */
  private boolean enableHeatTransfer = false;

  /** Surface temperature for heat transfer (K). */
  private double surfaceTemperature = 288.15;

  /** Heat transfer coefficient (W/(m²·K)). */
  private double heatTransferCoefficient = 0.0;

  /**
   * Enable water-oil velocity slip (7-equation model). When true, oil and water have separate
   * momentum equations allowing different velocities.
   */
  private boolean enableWaterOilSlip = true;

  /**
   * Constructor.
   */
  public TwoFluidConservationEquations() {
    this.wallFriction = new WallFriction();
    this.interfacialFriction = new InterfacialFriction();
    this.flowRegimeDetector = new FlowRegimeDetector();
    this.geometryCalc = new GeometryCalculator();
    this.fluxCalculator = new AUSMPlusFluxCalculator();
    this.reconstructor = new MUSCLReconstructor();
  }

  /**
   * Calculate the right-hand side (dU/dt) for all cells.
   *
   * <p>
   * This is the main entry point for the numerical integration. Returns the time derivative of
   * conservative variables for each cell.
   * </p>
   *
   * @param sections Array of pipe sections with current state
   * @param dx Cell size (m)
   * @return Time derivatives [nCells][NUM_EQUATIONS]
   */
  public double[][] calcRHS(TwoFluidSection[] sections, double dx) {
    int nCells = sections.length;
    double[][] dUdt = new double[nCells][NUM_EQUATIONS];

    // First pass: update flow regimes, geometry, and closure relations
    updateClosureRelations(sections);

    // Calculate fluxes at each interface (nCells-1 interfaces between cells)
    double[][] fluxes = calcInterfaceFluxes(sections, dx);

    // Calculate source terms for each cell
    double[][] sources = calcSourceTerms(sections);

    // Assemble RHS: dU/dt = -1/dx * (F_{i+1/2} - F_{i-1/2}) + S
    //
    // Boundary treatment:
    // - For inlet cell (i=0): Use inlet flux from cell 0 state (inlet stream sets this state)
    // The inlet boundary condition will maintain the proper state, so we use the cell's own flux
    // - For outlet cell (i=nCells-1): Use transmissive outlet with extrapolated flux
    for (int i = 0; i < nCells; i++) {
      double[] fluxLeft, fluxRight;

      if (i == 0) {
        // Inlet cell: Inlet BC maintains the state, so inlet flux = outlet flux from cell 0
        // This creates a "quasi-steady" inlet where what enters = what leaves for the cell
        // The mass is replenished by the boundary condition after each step
        fluxLeft = calcInletFlux(sections[0]);
        fluxRight = fluxes[0];
      } else if (i == nCells - 1) {
        // Outlet cell: left flux from last interface, right flux uses extrapolation
        // For transmissive outlet, we compute the outgoing flux from the outlet cell state
        fluxLeft = fluxes[nCells - 2];
        fluxRight = calcOutletFlux(sections[nCells - 1]);
      } else {
        // Interior cells: use interface fluxes normally
        fluxLeft = fluxes[i - 1];
        fluxRight = fluxes[i];
      }

      for (int j = 0; j < NUM_EQUATIONS; j++) {
        dUdt[i][j] = -1.0 / dx * (fluxRight[j] - fluxLeft[j]) + sources[i][j];
      }
    }

    return dUdt;
  }

  /**
   * Calculate inlet flux using the inlet cell state. This represents mass entering the domain from
   * the inlet stream. Uses holdups directly (set by steady state or BC) rather than computing from
   * mass per length to avoid feedback from cell depletion.
   */
  private double[] calcInletFlux(TwoFluidSection sec) {
    double[] flux = new double[NUM_EQUATIONS];
    double A = sec.getArea();

    // Gas flux (positive velocity means flow INTO domain)
    // Use the stored gas holdup directly, not derived from mass
    double rhoG = sec.getGasDensity();
    if (rhoG < 0.1)
      rhoG = 1.0; // Default gas density
    double vG = sec.getGasVelocity();
    double alphaG = sec.getGasHoldup();
    alphaG = Math.max(0.001, Math.min(0.999, alphaG));
    flux[IDX_GAS_MASS] = alphaG * rhoG * vG * A;
    flux[IDX_GAS_MOMENTUM] = alphaG * rhoG * vG * vG * A + alphaG * sec.getPressure() * A;

    // Oil flux - use holdup directly for inlet BC stability
    double rhoO = sec.getOilDensity();
    if (rhoO < 100)
      rhoO = 700.0; // Default oil density
    double vO = sec.getOilVelocity();
    double alphaO = sec.getOilHoldup();
    // If oil holdup is zero but we have liquid, estimate from liquid holdup
    if (alphaO < 1e-10 && sec.getLiquidHoldup() > 0.01) {
      alphaO = sec.getLiquidHoldup() * sec.getOilFractionInLiquid();
    }
    alphaO = Math.max(0, Math.min(1 - alphaG, alphaO));
    flux[IDX_OIL_MASS] = alphaO * rhoO * vO * A;
    flux[IDX_OIL_MOMENTUM] = alphaO * rhoO * vO * vO * A + alphaO * sec.getPressure() * A;

    // Water flux - use holdup directly for inlet BC stability
    double rhoW = sec.getWaterDensity();
    if (rhoW < 100)
      rhoW = 1000.0; // Default water density
    double vW = sec.getWaterVelocity();
    double alphaW = sec.getWaterHoldup();
    // If water holdup is zero but we have liquid, estimate from liquid holdup
    if (alphaW < 1e-10 && sec.getLiquidHoldup() > 0.01) {
      alphaW = sec.getLiquidHoldup() * sec.getWaterCut();
    }
    alphaW = Math.max(0, Math.min(1 - alphaG - alphaO, alphaW));
    flux[IDX_WATER_MASS] = alphaW * rhoW * vW * A;
    flux[IDX_WATER_MOMENTUM] = alphaW * rhoW * vW * vW * A + alphaW * sec.getPressure() * A;

    // Energy flux
    if (includeEnergyEquation) {
      double HG = sec.getGasEnthalpy();
      double HL = sec.getLiquidEnthalpy();
      flux[IDX_ENERGY] =
          (alphaG * rhoG * vG * HG + (alphaO * rhoO * vO + alphaW * rhoW * vW) * HL) * A;
    }

    return flux;
  }

  /**
   * Calculate outlet flux using upwind scheme (transmissive boundary).
   */
  private double[] calcOutletFlux(TwoFluidSection sec) {
    double[] flux = new double[NUM_EQUATIONS];
    double A = sec.getArea();

    // Gas flux (positive velocity means outflow) - use default density if not set
    double rhoG = sec.getGasDensity();
    if (rhoG < 0.1)
      rhoG = 1.0; // Default gas density
    double vG = Math.max(0, sec.getGasVelocity()); // Only outflow
    double alphaG = sec.getGasMassPerLength() / (rhoG * A);
    alphaG = Math.max(0, Math.min(1, alphaG));
    flux[IDX_GAS_MASS] = alphaG * rhoG * vG * A;
    flux[IDX_GAS_MOMENTUM] = alphaG * rhoG * vG * vG * A + alphaG * sec.getPressure() * A;

    // Oil flux - use default density if not set
    double rhoO = sec.getOilDensity();
    if (rhoO < 100)
      rhoO = 700.0; // Default oil density
    double vO = Math.max(0, sec.getOilVelocity());
    double alphaO = sec.getOilMassPerLength() / (rhoO * A);
    alphaO = Math.max(0, Math.min(alphaG > 0.99 ? 0 : 1, alphaO));
    flux[IDX_OIL_MASS] = alphaO * rhoO * vO * A;
    flux[IDX_OIL_MOMENTUM] = alphaO * rhoO * vO * vO * A + alphaO * sec.getPressure() * A;

    // Water flux - use default density if not set
    double rhoW = sec.getWaterDensity();
    if (rhoW < 100)
      rhoW = 1000.0; // Default water density
    double vW = Math.max(0, sec.getWaterVelocity());
    double alphaW = sec.getWaterMassPerLength() / (rhoW * A);
    alphaW = Math.max(0, Math.min(1 - alphaG - alphaO, alphaW));
    flux[IDX_WATER_MASS] = alphaW * rhoW * vW * A;
    flux[IDX_WATER_MOMENTUM] = alphaW * rhoW * vW * vW * A + alphaW * sec.getPressure() * A;

    // Energy flux
    if (includeEnergyEquation) {
      double HG = sec.getGasEnthalpy();
      double HL = sec.getLiquidEnthalpy();
      flux[IDX_ENERGY] =
          (alphaG * rhoG * vG * HG + (alphaO * rhoO * vO + alphaW * rhoW * vW) * HL) * A;
    }

    return flux;
  }

  /**
   * Update closure relations for all sections.
   */
  private void updateClosureRelations(TwoFluidSection[] sections) {
    for (TwoFluidSection sec : sections) {
      // Update flow regime
      sec.setFlowRegime(flowRegimeDetector.detectFlowRegime(sec));

      // Update stratified geometry if applicable
      PipeSection.FlowRegime regime = sec.getFlowRegime();
      if (regime == PipeSection.FlowRegime.STRATIFIED_SMOOTH
          || regime == PipeSection.FlowRegime.STRATIFIED_WAVY) {
        sec.updateStratifiedGeometry();
      }

      // Calculate wall friction
      WallFriction.WallFrictionResult wallResult = wallFriction.calculate(regime,
          sec.getGasVelocity(), sec.getLiquidVelocity(), sec.getGasDensity(),
          sec.getLiquidDensity(), sec.getGasViscosity(), sec.getLiquidViscosity(),
          sec.getLiquidHoldup(), sec.getDiameter(), sec.getRoughness());

      sec.setGasWallShear(wallResult.gasWallShear);
      sec.setLiquidWallShear(wallResult.liquidWallShear);

      // Calculate interfacial friction
      InterfacialFriction.InterfacialFrictionResult ifResult = interfacialFriction.calculate(regime,
          sec.getGasVelocity(), sec.getLiquidVelocity(), sec.getGasDensity(),
          sec.getLiquidDensity(), sec.getGasViscosity(), sec.getLiquidViscosity(),
          sec.getLiquidHoldup(), sec.getDiameter(), sec.getSurfaceTension());

      sec.setInterfacialShear(ifResult.interfacialShear);
      sec.setInterfacialWidth(ifResult.interfacialAreaPerLength);
    }
  }

  /**
   * Calculate fluxes at cell interfaces using AUSM+.
   *
   * <p>
   * For three-phase flow with water-oil slip, we track oil and water mass and momentum fluxes
   * separately. Water generally moves slower than oil in upward flow due to density differences.
   * </p>
   *
   * @param sections Pipe sections
   * @param dx Cell size
   * @return Fluxes at interfaces [nInterfaces][NUM_EQUATIONS]
   */
  private double[][] calcInterfaceFluxes(TwoFluidSection[] sections, double dx) {
    int nCells = sections.length;
    int nInterfaces = nCells - 1;
    double[][] fluxes = new double[nInterfaces][NUM_EQUATIONS];

    for (int i = 0; i < nInterfaces; i++) {
      TwoFluidSection left = sections[i];
      TwoFluidSection right = sections[i + 1];
      double A = 0.5 * (left.getArea() + right.getArea());

      // Create phase states for AUSM+
      PhaseState gasL = createGasState(left);
      PhaseState gasR = createGasState(right);

      // Create separate oil and water states for slip modeling
      PhaseState oilL = createOilState(left);
      PhaseState oilR = createOilState(right);
      PhaseState waterL = createWaterState(left);
      PhaseState waterR = createWaterState(right);

      // Calculate phase fluxes separately for gas, oil, and water
      PhaseFlux gasFlux = fluxCalculator.calcPhaseFlux(gasL, gasR, A);
      PhaseFlux oilFlux = fluxCalculator.calcPhaseFlux(oilL, oilR, A);
      PhaseFlux waterFlux = fluxCalculator.calcPhaseFlux(waterL, waterR, A);

      // Assemble interface flux vector - separate oil and water
      fluxes[i][IDX_GAS_MASS] = gasFlux.massFlux;
      fluxes[i][IDX_OIL_MASS] = oilFlux.massFlux;
      fluxes[i][IDX_WATER_MASS] = waterFlux.massFlux;

      fluxes[i][IDX_GAS_MOMENTUM] = gasFlux.momentumFlux;
      fluxes[i][IDX_OIL_MOMENTUM] = oilFlux.momentumFlux;
      fluxes[i][IDX_WATER_MOMENTUM] = waterFlux.momentumFlux;

      if (includeEnergyEquation) {
        fluxes[i][IDX_ENERGY] = gasFlux.energyFlux + oilFlux.energyFlux + waterFlux.energyFlux;
      }
    }

    return fluxes;
  }

  /**
   * Calculate source terms for all cells.
   *
   * <p>
   * For three-phase flow, tracks water separately from oil. Water accumulates more in valleys due
   * to higher density.
   * </p>
   *
   * @param sections Pipe sections
   * @return Source terms [nCells][NUM_EQUATIONS]
   */
  private double[][] calcSourceTerms(TwoFluidSection[] sections) {
    int nCells = sections.length;
    double[][] sources = new double[nCells][NUM_EQUATIONS];

    for (int i = 0; i < nCells; i++) {
      TwoFluidSection sec = sections[i];
      double A = sec.getArea();
      double alphaG = sec.getGasHoldup();
      double alphaL = sec.getLiquidHoldup();
      double rhoG = sec.getGasDensity();
      double rhoL = sec.getLiquidDensity();
      double sinTheta = Math.sin(sec.getInclination());

      // Get water and oil holdups
      double waterCut = sec.getWaterCut();
      double alphaW = sec.getWaterHoldup();
      double alphaO = sec.getOilHoldup();
      double rhoW = sec.getWaterDensity();
      double rhoO = sec.getOilDensity();

      // Get geometry parameters
      double S_G = sec.getGasWettedPerimeter();
      double S_L = sec.getLiquidWettedPerimeter();
      double S_i = sec.getInterfacialWidth();

      // Default to simple estimates if geometry not set
      if (S_G < 1e-10) {
        S_G = Math.PI * sec.getDiameter() * (1 - alphaL);
      }
      if (S_L < 1e-10) {
        S_L = Math.PI * sec.getDiameter() * alphaL;
      }
      if (S_i < 1e-10) {
        S_i = sec.getDiameter(); // Approximate for non-stratified
      }

      // Wall friction forces (N/m)
      double F_wG = -sec.getGasWallShear() * S_G;
      double F_wL = -sec.getLiquidWallShear() * S_L;

      // Interfacial friction force (N/m)
      // Positive interfacial shear decelerates gas, accelerates liquid
      double F_iG = -sec.getInterfacialShear() * S_i;
      double F_iL = sec.getInterfacialShear() * S_i;

      // Gravity forces (N/m) - calculated separately for oil and water
      double F_gG = -alphaG * rhoG * GRAVITY * A * sinTheta;
      double F_gL = -alphaL * rhoL * GRAVITY * A * sinTheta;

      // Water-specific gravity source (water is heavier, accumulates more in downslopes)
      double F_gW = 0;
      double F_gO = 0;
      if (rhoW > 0 && rhoO > 0) {
        F_gW = -alphaW * rhoW * GRAVITY * A * sinTheta;
        F_gO = -alphaO * rhoO * GRAVITY * A * sinTheta;
      }

      // Mass transfer source (if enabled)
      double Gamma_G = 0;
      double Gamma_L = 0;
      if (includeMassTransfer) {
        // Simplified equilibrium departure model
        // Positive Gamma = evaporation (liquid to gas)
        double[] massTransfer = calcMassTransfer(sec);
        Gamma_G = massTransfer[0];
        Gamma_L = massTransfer[1];
      }

      // Assemble source terms - now with separate oil and water mass equations
      sources[i][IDX_GAS_MASS] = Gamma_G;

      // Oil and water mass sources (no phase change between oil/water for now)
      // Gravity-driven segregation source term for water accumulation
      double waterSegregationSource = 0;
      if (rhoW > rhoO && rhoO > 0 && alphaL > 0.01 && sinTheta != 0) {
        // Water settles in valleys (negative inclination after positive)
        // This is a simplified model for water stratification within the liquid phase
        double densityRatio = (rhoW - rhoO) / rhoO;
        waterSegregationSource = 0.01 * densityRatio * alphaW * A * Math.abs(sinTheta);
        if (sinTheta < 0) {
          waterSegregationSource = -waterSegregationSource; // Water accumulates going downhill
        }
      }

      sources[i][IDX_OIL_MASS] = Gamma_L * (1.0 - waterCut);
      sources[i][IDX_WATER_MASS] = Gamma_L * waterCut + waterSegregationSource;

      sources[i][IDX_GAS_MOMENTUM] = F_wG + F_iG + F_gG + Gamma_G * sec.getGasVelocity();

      if (enableWaterOilSlip && NUM_EQUATIONS == 7) {
        // Separate oil and water momentum equations
        // Wall friction partitioned between oil and water based on holdup
        double oilHoldupFrac = (alphaL > 0.01) ? alphaO / alphaL : 0.5;
        double waterHoldupFrac = (alphaL > 0.01) ? alphaW / alphaL : 0.5;

        double F_wO = F_wL * oilHoldupFrac;
        double F_wW = F_wL * waterHoldupFrac;

        // Gas-liquid interfacial force partitioned to the dominant liquid phase
        // (Gas interacts primarily with oil on top for stratified oil-water flow)
        double F_iO = F_iL * 0.8; // Oil gets most of gas-liquid interface
        double F_iW = F_iL * 0.2;

        // Oil-water interfacial shear (from TwoFluidSection calculation)
        double tau_ow = sec.calcOilWaterInterfacialShear();

        // Estimate oil-water interface length (simplified as fraction of diameter)
        double S_ow = sec.getDiameter() * 0.5 * alphaL;

        // Force on oil from oil-water interface (negative = retarded by water)
        double F_ow_oil = -tau_ow * S_ow;
        double F_ow_water = tau_ow * S_ow; // Opposite sign

        // Assemble oil momentum source
        sources[i][IDX_OIL_MOMENTUM] =
            F_wO + F_iO + F_gO + F_ow_oil + Gamma_L * (1.0 - waterCut) * sec.getOilVelocity();

        // Assemble water momentum source
        sources[i][IDX_WATER_MOMENTUM] =
            F_wW + F_iW + F_gW + F_ow_water + Gamma_L * waterCut * sec.getWaterVelocity();
      } else {
        // Combined liquid momentum (original 6-equation model)
        sources[i][IDX_OIL_MOMENTUM] = F_wL + F_iL + F_gL + Gamma_L * sec.getLiquidVelocity();
        sources[i][IDX_WATER_MOMENTUM] = 0; // Not used in 6-equation mode
      }

      if (includeEnergyEquation) {
        // Energy source: heat transfer + friction work
        double Q_wall = calcHeatTransfer(sec);
        double W_fric = calcFrictionWork(sec);
        sources[i][IDX_ENERGY] = Q_wall + W_fric;
      }

      // Store in section for diagnostics
      sec.setGasMassSource(Gamma_G);
      sec.setLiquidMassSource(Gamma_L);
      sec.setGasMomentumSource(sources[i][IDX_GAS_MOMENTUM]);
      // Combined liquid momentum for diagnostics
      double liquidMomSource = sources[i][IDX_OIL_MOMENTUM] + sources[i][IDX_WATER_MOMENTUM];
      sec.setLiquidMomentumSource(liquidMomSource);
      sec.setEnergySource(sources[i][IDX_ENERGY]);
    }

    return sources;
  }

  /**
   * Create gas phase state for flux calculation. Uses true holdup from mass per length for
   * mass-consistent flux.
   */
  private PhaseState createGasState(TwoFluidSection sec) {
    PhaseState state = new PhaseState();
    state.density = sec.getGasDensity();
    state.velocity = sec.getGasVelocity();
    state.pressure = sec.getPressure();
    state.soundSpeed = sec.getGasSoundSpeed();
    state.enthalpy = sec.getGasEnthalpy();
    // Use true holdup from conservative variables for mass-consistent flux
    double A = sec.getArea();
    double rhoG = state.density;
    if (A > 0 && rhoG > 0.1) {
      state.holdup = sec.getGasMassPerLength() / (rhoG * A);
    } else {
      state.holdup = sec.getGasHoldup();
    }
    return state;
  }

  /**
   * Create liquid phase state for flux calculation. Uses true holdup from mass per length for
   * mass-consistent flux.
   */
  private PhaseState createLiquidState(TwoFluidSection sec) {
    PhaseState state = new PhaseState();
    state.density = sec.getLiquidDensity();
    state.velocity = sec.getLiquidVelocity();
    state.pressure = sec.getPressure();
    state.soundSpeed = sec.getLiquidSoundSpeed();
    state.enthalpy = sec.getLiquidEnthalpy();
    // Use true holdup from conservative variables for mass-consistent flux
    double A = sec.getArea();
    double rhoL = state.density;
    if (A > 0 && rhoL > 100) {
      state.holdup = sec.getLiquidMassPerLength() / (rhoL * A);
    } else {
      state.holdup = sec.getLiquidHoldup();
    }
    return state;
  }

  /**
   * Create oil phase state for flux calculation in three-phase flow. Uses true holdup from mass per
   * length for mass-consistent flux.
   */
  private PhaseState createOilState(TwoFluidSection sec) {
    PhaseState state = new PhaseState();
    // Use default density if not properly set
    double rhoO = sec.getOilDensity();
    if (rhoO < 100) {
      rhoO = 700.0; // Default oil density
    }
    state.density = rhoO;
    state.velocity = sec.getOilVelocity();
    state.pressure = sec.getPressure();
    // Use liquid sound speed for oil (simplified)
    state.soundSpeed = sec.getLiquidSoundSpeed();
    state.enthalpy = sec.getLiquidEnthalpy() * sec.getOilFractionInLiquid();
    // Use true holdup from conservative variables for mass-consistent flux
    double A = sec.getArea();
    if (A > 0) {
      state.holdup = sec.getOilMassPerLength() / (rhoO * A);
      state.holdup = Math.max(0, Math.min(1, state.holdup));
    } else {
      state.holdup = sec.getOilHoldup();
    }
    return state;
  }

  /**
   * Create water phase state for flux calculation in three-phase flow. Uses true holdup from mass
   * per length for mass-consistent flux.
   */
  private PhaseState createWaterState(TwoFluidSection sec) {
    PhaseState state = new PhaseState();
    // Use default density if not properly set
    double rhoW = sec.getWaterDensity();
    if (rhoW < 100) {
      rhoW = 1000.0; // Default water density
    }
    state.density = rhoW;
    state.velocity = sec.getWaterVelocity();
    state.pressure = sec.getPressure();
    // Use liquid sound speed for water (simplified)
    state.soundSpeed = sec.getLiquidSoundSpeed();
    state.enthalpy = sec.getLiquidEnthalpy() * sec.getWaterCut();
    // Use true holdup from conservative variables for mass-consistent flux
    double A = sec.getArea();
    if (A > 0) {
      state.holdup = sec.getWaterMassPerLength() / (rhoW * A);
      state.holdup = Math.max(0, Math.min(1, state.holdup));
    } else {
      state.holdup = sec.getWaterHoldup();
    }
    return state;
  }

  /**
   * Calculate mass transfer between phases (simplified model).
   *
   * @param sec Pipe section
   * @return [gasSource, liquidSource] in kg/(m·s)
   */
  private double[] calcMassTransfer(TwoFluidSection sec) {
    // Simple relaxation to equilibrium
    // Would need flash calculation for accurate implementation
    double Gamma = 0;

    // Placeholder: could use departure from bubble point pressure
    // Gamma = k * (P - P_bubble)
    // Positive = evaporation (liquid to gas)

    return new double[] {Gamma, -Gamma}; // Mass conserved
  }

  /**
   * Calculate heat transfer to/from pipe wall.
   *
   * @param sec Pipe section
   * @return Heat source (W/m)
   */
  private double calcHeatTransfer(TwoFluidSection sec) {
    if (!enableHeatTransfer || heatTransferCoefficient <= 0) {
      return 0.0; // Adiabatic
    }

    // Heat transfer from external surface: Q = h * A * (T_fluid - T_surface)
    // where:
    // h = heat transfer coefficient [W/(m²·K)]
    // A = pipe outer surface area per unit length = π * D [m²/m]
    // T_fluid = bulk fluid temperature [K]
    // T_surface = surrounding surface temperature [K]
    // Q = heat flow per unit length [W/m]

    double diameter = sec.getDiameter();
    double fluidTemperature = sec.getTemperature();
    double pipePerimeter = Math.PI * diameter; // Surface area per unit length

    // Heat transfer rate (W/m)
    // Positive when fluid is warmer than surroundings (cooling)
    // Negative when fluid is cooler than surroundings (heating)
    double Q = heatTransferCoefficient * pipePerimeter * (fluidTemperature - surfaceTemperature);

    return Q;
  }

  /**
   * Calculate friction work (viscous dissipation).
   *
   * @param sec Pipe section
   * @return Friction work source (W/m)
   */
  private double calcFrictionWork(TwoFluidSection sec) {
    // W = tau_w * v (friction heating)
    double WG =
        Math.abs(sec.getGasWallShear() * sec.getGasVelocity() * sec.getGasWettedPerimeter());
    double WL = Math
        .abs(sec.getLiquidWallShear() * sec.getLiquidVelocity() * sec.getLiquidWettedPerimeter());

    return WG + WL;
  }

  /**
   * Apply pressure gradient term (handled separately for numerical stability).
   *
   * @param sections Pipe sections
   * @param dUdt Current RHS values to modify
   * @param dx Cell size
   */
  public void applyPressureGradient(TwoFluidSection[] sections, double[][] dUdt, double dx) {
    int nCells = sections.length;

    for (int i = 0; i < nCells; i++) {
      TwoFluidSection sec = sections[i];

      // Central difference for pressure gradient
      double dPdx;
      if (i == 0) {
        dPdx = (sections[1].getPressure() - sections[0].getPressure()) / dx;
      } else if (i == nCells - 1) {
        dPdx = (sections[nCells - 1].getPressure() - sections[nCells - 2].getPressure()) / dx;
      } else {
        dPdx = (sections[i + 1].getPressure() - sections[i - 1].getPressure()) / (2 * dx);
      }

      double A = sec.getArea();

      // Pressure force on gas phase
      dUdt[i][IDX_GAS_MOMENTUM] -= sec.getGasHoldup() * A * dPdx;

      if (enableWaterOilSlip && NUM_EQUATIONS == 7) {
        // Separate pressure forces for oil and water
        dUdt[i][IDX_OIL_MOMENTUM] -= sec.getOilHoldup() * A * dPdx;
        dUdt[i][IDX_WATER_MOMENTUM] -= sec.getWaterHoldup() * A * dPdx;
      } else {
        // Combined liquid pressure force
        dUdt[i][IDX_OIL_MOMENTUM] -= sec.getLiquidHoldup() * A * dPdx;
        // IDX_WATER_MOMENTUM not used in 6-equation mode
      }
    }
  }

  /**
   * Extract state from sections into array format.
   *
   * <p>
   * For three-phase flow, extracts gas mass, oil mass, water mass separately.
   * </p>
   *
   * @param sections Pipe sections
   * @return State array [nCells][NUM_EQUATIONS]
   */
  public double[][] extractState(TwoFluidSection[] sections) {
    int nCells = sections.length;
    double[][] U = new double[nCells][NUM_EQUATIONS];

    for (int i = 0; i < nCells; i++) {
      // DO NOT call updateConservativeVariables() here!
      // The conservative variables should already be correct from:
      // - Steady-state initialization (which calls updateConservativeVariables at the end)
      // - Previous transient step (which applies state via applyState -> setStateVector)
      // Calling updateConservativeVariables here would recalculate mass from current holdups,
      // which can corrupt mass conservation if holdups were modified by normalization.
      U[i] = sections[i].getStateVector();
    }

    return U;
  }

  /**
   * Apply state to sections from array format.
   *
   * <p>
   * For three-phase flow, updates water and oil holdups after extracting primitives.
   * </p>
   *
   * @param sections Pipe sections
   * @param U State array [nCells][NUM_EQUATIONS]
   */
  public void applyState(TwoFluidSection[] sections, double[][] U) {
    int nCells = sections.length;

    for (int i = 0; i < nCells; i++) {
      sections[i].setStateVector(U[i]);
      sections[i].extractPrimitiveVariables();
      sections[i].updateWaterOilHoldups();
      sections[i].updateThreePhaseProperties();
    }
  }

  // Getters and setters

  public boolean isIncludeEnergyEquation() {
    return includeEnergyEquation;
  }

  public void setIncludeEnergyEquation(boolean includeEnergyEquation) {
    this.includeEnergyEquation = includeEnergyEquation;
  }

  public boolean isIncludeMassTransfer() {
    return includeMassTransfer;
  }

  public void setIncludeMassTransfer(boolean includeMassTransfer) {
    this.includeMassTransfer = includeMassTransfer;
  }

  /**
   * Check if water-oil velocity slip modeling is enabled.
   *
   * @return true if 7-equation model with separate oil/water momentum is enabled
   */
  public boolean isEnableWaterOilSlip() {
    return enableWaterOilSlip;
  }

  /**
   * Enable or disable water-oil velocity slip modeling.
   *
   * <p>
   * When enabled, uses 7-equation model with separate oil and water momentum equations, allowing
   * water to flow at different velocity than oil (e.g., water slipping back in uphill flow).
   * </p>
   *
   * @param enableWaterOilSlip true to enable 7-equation slip model
   */
  public void setEnableWaterOilSlip(boolean enableWaterOilSlip) {
    this.enableWaterOilSlip = enableWaterOilSlip;
  }

  public double getMassTransferCoefficient() {
    return massTransferCoefficient;
  }

  public void setMassTransferCoefficient(double massTransferCoefficient) {
    this.massTransferCoefficient = massTransferCoefficient;
  }

  public WallFriction getWallFriction() {
    return wallFriction;
  }

  public InterfacialFriction getInterfacialFriction() {
    return interfacialFriction;
  }

  public FlowRegimeDetector getFlowRegimeDetector() {
    return flowRegimeDetector;
  }

  public AUSMPlusFluxCalculator getFluxCalculator() {
    return fluxCalculator;
  }

  public MUSCLReconstructor getReconstructor() {
    return reconstructor;
  }

  /**
   * Set surface temperature for heat transfer calculations.
   *
   * @param temperature Surface temperature in Kelvin
   */
  public void setSurfaceTemperature(double temperature) {
    this.surfaceTemperature = temperature;
    this.enableHeatTransfer = true;
  }

  /**
   * Set heat transfer coefficient for convective heat transfer.
   *
   * @param heatTransferCoefficient Heat transfer coefficient in W/(m²·K)
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient) {
    this.heatTransferCoefficient = Math.max(0, heatTransferCoefficient);
    this.enableHeatTransfer = heatTransferCoefficient > 0;
  }

  /**
   * Enable/disable heat transfer modeling.
   *
   * @param enable true to enable heat transfer
   */
  public void setEnableHeatTransfer(boolean enable) {
    this.enableHeatTransfer = enable;
  }

  /**
   * Get the surface temperature used in heat transfer calculations.
   *
   * @return Surface temperature in Kelvin
   */
  public double getSurfaceTemperature() {
    return surfaceTemperature;
  }

  /**
   * Get the heat transfer coefficient.
   *
   * @return Heat transfer coefficient in W/(m²·K)
   */
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * Check if heat transfer is enabled.
   *
   * @return true if heat transfer modeling is active
   */
  public boolean isHeatTransferEnabled() {
    return enableHeatTransfer && heatTransferCoefficient > 0;
  }
}
