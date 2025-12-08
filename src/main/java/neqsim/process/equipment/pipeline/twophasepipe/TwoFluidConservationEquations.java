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

  /** Number of conservation equations. */
  public static final int NUM_EQUATIONS = 5;

  /** Index for gas mass. */
  public static final int IDX_GAS_MASS = 0;

  /** Index for liquid mass. */
  public static final int IDX_LIQUID_MASS = 1;

  /** Index for gas momentum. */
  public static final int IDX_GAS_MOMENTUM = 2;

  /** Index for liquid momentum. */
  public static final int IDX_LIQUID_MOMENTUM = 3;

  /** Index for energy. */
  public static final int IDX_ENERGY = 4;

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

    // Calculate fluxes at each interface
    double[][] fluxes = calcInterfaceFluxes(sections, dx);

    // Calculate source terms for each cell
    double[][] sources = calcSourceTerms(sections);

    // Assemble RHS: dU/dt = -1/dx * (F_{i+1/2} - F_{i-1/2}) + S
    for (int i = 0; i < nCells; i++) {
      double[] fluxLeft = (i > 0) ? fluxes[i - 1] : fluxes[0];
      double[] fluxRight = (i < nCells - 1) ? fluxes[i] : fluxes[nCells - 2];

      for (int j = 0; j < NUM_EQUATIONS; j++) {
        dUdt[i][j] = -1.0 / dx * (fluxRight[j] - fluxLeft[j]) + sources[i][j];
      }
    }

    return dUdt;
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
      PhaseState liqL = createLiquidState(left);
      PhaseState liqR = createLiquidState(right);

      // Calculate phase fluxes
      PhaseFlux gasFlux = fluxCalculator.calcPhaseFlux(gasL, gasR, A);
      PhaseFlux liqFlux = fluxCalculator.calcPhaseFlux(liqL, liqR, A);

      // Assemble interface flux vector
      fluxes[i][IDX_GAS_MASS] = gasFlux.massFlux;
      fluxes[i][IDX_LIQUID_MASS] = liqFlux.massFlux;
      fluxes[i][IDX_GAS_MOMENTUM] = gasFlux.momentumFlux;
      fluxes[i][IDX_LIQUID_MOMENTUM] = liqFlux.momentumFlux;

      if (includeEnergyEquation) {
        fluxes[i][IDX_ENERGY] = gasFlux.energyFlux + liqFlux.energyFlux;
      }
    }

    return fluxes;
  }

  /**
   * Calculate source terms for all cells.
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

      // Gravity forces (N/m)
      double F_gG = -alphaG * rhoG * GRAVITY * A * sinTheta;
      double F_gL = -alphaL * rhoL * GRAVITY * A * sinTheta;

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

      // Assemble source terms
      sources[i][IDX_GAS_MASS] = Gamma_G;
      sources[i][IDX_LIQUID_MASS] = Gamma_L;
      sources[i][IDX_GAS_MOMENTUM] = F_wG + F_iG + F_gG + Gamma_G * sec.getGasVelocity();
      sources[i][IDX_LIQUID_MOMENTUM] = F_wL + F_iL + F_gL + Gamma_L * sec.getLiquidVelocity();

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
      sec.setLiquidMomentumSource(sources[i][IDX_LIQUID_MOMENTUM]);
      sec.setEnergySource(sources[i][IDX_ENERGY]);
    }

    return sources;
  }

  /**
   * Create gas phase state for flux calculation.
   */
  private PhaseState createGasState(TwoFluidSection sec) {
    PhaseState state = new PhaseState();
    state.density = sec.getGasDensity();
    state.velocity = sec.getGasVelocity();
    state.pressure = sec.getPressure();
    state.soundSpeed = sec.getGasSoundSpeed();
    state.enthalpy = sec.getGasEnthalpy();
    state.holdup = sec.getGasHoldup();
    return state;
  }

  /**
   * Create liquid phase state for flux calculation.
   */
  private PhaseState createLiquidState(TwoFluidSection sec) {
    PhaseState state = new PhaseState();
    state.density = sec.getLiquidDensity();
    state.velocity = sec.getLiquidVelocity();
    state.pressure = sec.getPressure();
    state.soundSpeed = sec.getLiquidSoundSpeed();
    state.enthalpy = sec.getLiquidEnthalpy();
    state.holdup = sec.getLiquidHoldup();
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
    // Would need ambient temperature and heat transfer coefficient
    // Q = U * pi * D * (T_ambient - T_fluid)
    return 0; // Adiabatic by default
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

      // Pressure force on each phase
      dUdt[i][IDX_GAS_MOMENTUM] -= sec.getGasHoldup() * A * dPdx;
      dUdt[i][IDX_LIQUID_MOMENTUM] -= sec.getLiquidHoldup() * A * dPdx;
    }
  }

  /**
   * Extract state from sections into array format.
   *
   * @param sections Pipe sections
   * @return State array [nCells][NUM_EQUATIONS]
   */
  public double[][] extractState(TwoFluidSection[] sections) {
    int nCells = sections.length;
    double[][] U = new double[nCells][NUM_EQUATIONS];

    for (int i = 0; i < nCells; i++) {
      sections[i].updateConservativeVariables();
      U[i] = sections[i].getStateVector();
    }

    return U;
  }

  /**
   * Apply state to sections from array format.
   *
   * @param sections Pipe sections
   * @param U State array [nCells][NUM_EQUATIONS]
   */
  public void applyState(TwoFluidSection[] sections, double[][] U) {
    int nCells = sections.length;

    for (int i = 0; i < nCells; i++) {
      sections[i].setStateVector(U[i]);
      sections[i].extractPrimitiveVariables();
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
}
