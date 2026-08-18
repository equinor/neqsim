package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.closure.BubbleSizeClosure;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.DispersedBubbleDragSolver;
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
 * <li><b>Gas Momentum:</b> ∂/∂t(α_g·ρ_g·v_g·A) + ∂/∂x(α_g·ρ_g·v_g²·A) = -α_g·A·∂P/∂x - τ_wg·S_g - τ_i·S_i -
 * α_g·ρ_g·g·A·sin(θ)</li>
 * <li><b>Liquid Momentum:</b> ∂/∂t(α_L·ρ_L·v_L·A) + ∂/∂x(α_L·ρ_L·v_L²·A) = -α_L·A·∂P/∂x - τ_wL·S_L + τ_i·S_i -
 * α_L·ρ_L·g·A·sin(θ)</li>
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
   * Number of conservation equations (7 for three-phase with water-oil slip: gas mass, oil mass, water mass, gas
   * momentum, oil momentum, water momentum, energy).
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
  private EntrainmentDeposition entrainmentDeposition;
  private ThermodynamicCoupling thermodynamicCoupling;

  // Numerical methods
  private AUSMPlusFluxCalculator fluxCalculator;
  private MUSCLReconstructor reconstructor;

  // Settings
  private boolean includeEnergyEquation = true;
  private boolean includeMassTransfer = false;
  private double massTransferRelaxationTime = 30.0;
  private double massTransferCoefficient = 0.01; // kg/(m³·s·Pa)

  /** Enable heat transfer to surroundings. */
  private boolean enableHeatTransfer = false;

  /** Surface temperature for heat transfer (K). */
  private double surfaceTemperature = 288.15;

  /** Heat transfer coefficient (W/(m²·K)). */
  private double heatTransferCoefficient = 0.0;

  /**
   * Enable water-oil velocity slip (7-equation model). When true, oil and water have separate momentum equations
   * allowing different velocities.
   */
  private boolean enableWaterOilSlip = true;

  /**
   * Enable the non-conservative holdup-gradient momentum term, including the interfacial pressure correction that keeps
   * the two-fluid system hyperbolic.
   *
   * <p>
   * Off by default. The term removes the sustained phase backflow and the unbounded liquid packing that liquid-rich
   * lines otherwise show, but it is acoustic in scale and is evaluated explicitly, so it needs a CFL number near 0.05
   * rather than the default 0.5. Enabling it without also tightening the CFL diverges. Completing it means folding the
   * term into the implicit pressure solve of the IMEX integrator.
   * </p>
   */
  private boolean enableInterfacialPressure = false;

  /** Whether the Bestion stabilizer is handled by the time integrator instead of the explicit RHS. */
  private boolean implicitInterfacialPressure = false;

  /**
   * Interfacial pressure coefficient delta in the Bestion closure. The two-fluid system has real characteristics for
   * delta greater than or equal to one; a value slightly above one leaves margin. Reference: Bestion, D. (1990), "The
   * physical closure laws in the CATHARE code", Nuclear Engineering and Design 124, 229-245.
   */
  private double interfacialPressureCoefficient = 1.2;

  /**
   * Whether the transmissive outlet has had to suppress a reversed phase velocity.
   *
   * <p>
   * Sticky once set, so a caller can ask after a sequence of steps. Cleared with {@link #clearOutletBackflowClamped()}.
   * </p>
   */
  private boolean outletBackflowClamped = false;

  /** Allow a zero-gradient pressure outlet to carry a phase back into the domain. */
  private boolean allowOutletPhaseBackflow = false;

  /** Interface gas holdup used by the pressure part of the momentum flux, one per interface. */
  private double[] interfaceGasHoldup = new double[0];

  /** Interface liquid holdup used by the pressure part of the momentum flux, one per interface. */
  private double[] interfaceLiquidHoldup = new double[0];

  /** Interface pressure used by the pressure part of the momentum flux, one per interface. */
  private double[] interfacePressure = new double[0];

  /**
   * Enable virtual mass force term in momentum equations. The virtual mass force accounts for the inertia of the
   * displaced phase during rapid acceleration/deceleration. Reference: Drew, D.A. and Lahey, R.T. (1987), "The virtual
   * mass and lift force on a sphere in rotating and straining inviscid flow", Int. J. Multiphase Flow.
   */
  private boolean enableVirtualMassForce = false;

  /**
   * Virtual mass coefficient. For spherical bubbles/droplets, C_vm = 0.5 (theoretical). Values 0.3-0.7 are common in
   * practice.
   */
  private double virtualMassCoefficient = 0.5;

  /** Treat corrected bubble drag with a conservative local implicit source step when opted in. */
  private boolean enableStiffBubbleDrag = false;

  /** Retained timestep setting for source compatibility with existing callers. */
  private double dt = 0.01;

  /** Most recent phase-resolved boundary and source rates calculated by {@link #calcRHS}. */
  private MassBalanceRate lastMassBalanceRate = new MassBalanceRate(new double[3], new double[3], new double[3]);

  /** Phase-resolved face mass flows from the most recent {@link #calcRHS} evaluation. */
  private double[][] lastPhaseMassFaceFluxes = new double[0][3];

  /** Phase-resolved cell source rates from the most recent {@link #calcRHS} evaluation. */
  private double[][] lastPhaseMassSourcesPerLength = new double[0][3];

  /**
   * Instantaneous phase-resolved terms in the finite-volume domain mass balance.
   */
  public static final class MassBalanceRate implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[] inletMassFlowKgPerSecond;
    private final double[] outletMassFlowKgPerSecond;
    private final double[] sourceMassFlowKgPerSecond;

    private MassBalanceRate(double[] inletMassFlowKgPerSecond, double[] outletMassFlowKgPerSecond,
        double[] sourceMassFlowKgPerSecond) {
      this.inletMassFlowKgPerSecond = inletMassFlowKgPerSecond.clone();
      this.outletMassFlowKgPerSecond = outletMassFlowKgPerSecond.clone();
      this.sourceMassFlowKgPerSecond = sourceMassFlowKgPerSecond.clone();
    }

    /**
     * Get gas, oil, and water inlet mass-flow rates.
     *
     * @return three-element array in kg/s
     */
    public double[] getInletMassFlowKgPerSecond() {
      return inletMassFlowKgPerSecond.clone();
    }

    /**
     * Get gas, oil, and water outlet mass-flow rates.
     *
     * @return three-element array in kg/s
     */
    public double[] getOutletMassFlowKgPerSecond() {
      return outletMassFlowKgPerSecond.clone();
    }

    /**
     * Get gas, oil, and water domain-integrated source rates.
     *
     * @return three-element array in kg/s
     */
    public double[] getSourceMassFlowKgPerSecond() {
      return sourceMassFlowKgPerSecond.clone();
    }
  }

  /**
   * Constructor.
   */
  public TwoFluidConservationEquations() {
    this.wallFriction = new WallFriction();
    this.interfacialFriction = new InterfacialFriction();
    this.flowRegimeDetector = new FlowRegimeDetector();
    this.geometryCalc = new GeometryCalculator();
    this.entrainmentDeposition = new EntrainmentDeposition();
    this.fluxCalculator = new AUSMPlusFluxCalculator();
    this.reconstructor = new MUSCLReconstructor();
  }

  /**
   * Calculate the right-hand side (dU/dt) for all cells.
   *
   * <p>
   * This is the main entry point for the numerical integration. Returns the time derivative of conservative variables
   * for each cell.
   * </p>
   *
   * @param sections Array of pipe sections with current state
   * @param dx Cell size (m) — used as uniform dx; for non-uniform mesh each section's own length is used via
   * {@code sections[i].getLength()}
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

    // Calculate the two external boundary fluxes once. These exact values are also
    // retained for a stage-consistent domain mass-balance diagnostic.
    double[] inletFlux = calcInletFlux(sections[0]);
    double[] outletFlux = calcOutletFlux(sections[nCells - 1]);
    lastPhaseMassFaceFluxes = populatePhaseMassFaceFluxes(lastPhaseMassFaceFluxes, nCells, fluxes, inletFlux,
        outletFlux);

    // Assemble RHS: dU/dt = -1/dx_i * (F_{i+1/2} - F_{i-1/2}) + S_i
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
        fluxLeft = inletFlux;
        fluxRight = fluxes[0];
      } else if (i == nCells - 1) {
        // Outlet cell: left flux from last interface, right flux uses extrapolation
        // For transmissive outlet, we compute the outgoing flux from the outlet cell state
        fluxLeft = fluxes[nCells - 2];
        fluxRight = outletFlux;
      } else {
        // Interior cells: use interface fluxes normally
        fluxLeft = fluxes[i - 1];
        fluxRight = fluxes[i];
      }

      // Use per-section length for non-uniform mesh
      double secDx = sections[i].getLength();

      for (int j = 0; j < NUM_EQUATIONS; j++) {
        dUdt[i][j] = -1.0 / secDx * (fluxRight[j] - fluxLeft[j]) + sources[i][j];
      }
    }

    applyInterfacialPressure(sections, dUdt);
    applyVirtualMassCoupling(sections, dUdt);

    double[] inletMassFlow = { inletFlux[IDX_GAS_MASS], inletFlux[IDX_OIL_MASS], inletFlux[IDX_WATER_MASS] };
    double[] outletMassFlow = { outletFlux[IDX_GAS_MASS], outletFlux[IDX_OIL_MASS], outletFlux[IDX_WATER_MASS] };
    double[] sourceMassFlow = new double[3];
    for (int i = 0; i < nCells; i++) {
      double sectionLength = sections[i].getLength();
      sourceMassFlow[0] += sources[i][IDX_GAS_MASS] * sectionLength;
      sourceMassFlow[1] += sources[i][IDX_OIL_MASS] * sectionLength;
      sourceMassFlow[2] += sources[i][IDX_WATER_MASS] * sectionLength;
    }
    lastMassBalanceRate = new MassBalanceRate(inletMassFlow, outletMassFlow, sourceMassFlow);

    return dUdt;
  }

  /**
   * Get the phase-resolved boundary and source rates from the most recent right-hand-side evaluation.
   *
   * @return immutable mass-balance rate snapshot
   */
  public MassBalanceRate getLastMassBalanceRate() {
    return lastMassBalanceRate;
  }

  /**
   * Get a defensive copy of the phase-resolved face mass flows from the most recent
   * {@link #calcRHS(TwoFluidSection[], double)} evaluation.
   *
   * @return face mass flows with shape {@code [sections.length + 1][3]}, or an empty array before the first evaluation
   */
  public double[][] getLastPhaseMassFaceFluxes() {
    double[][] snapshot = new double[lastPhaseMassFaceFluxes.length][];
    for (int face = 0; face < lastPhaseMassFaceFluxes.length; face++) {
      snapshot[face] = lastPhaseMassFaceFluxes[face].clone();
    }
    return snapshot;
  }

  /**
   * Add the most recently evaluated phase-resolved face mass flows to a caller-owned accumulator.
   *
   * <p>
   * This avoids allocating a defensive snapshot for every Runge-Kutta stage while keeping the retained internal buffer
   * encapsulated. The accumulator must have shape {@code [sections.length + 1][3]} for the most recent
   * {@link #calcRHS(TwoFluidSection[], double)} evaluation.
   * </p>
   *
   * @param accumulator destination receiving {@code weight * faceMassFlow}
   * @param weight integration-stage weight
   * @throws IllegalArgumentException if the accumulator shape is incompatible or the weight is not finite
   */
  public void accumulateLastPhaseMassFaceFluxes(double[][] accumulator, double weight) {
    if (accumulator == null || accumulator.length != lastPhaseMassFaceFluxes.length) {
      throw new IllegalArgumentException("Accumulator must match the most recent face-flux shape");
    }
    if (!Double.isFinite(weight)) {
      throw new IllegalArgumentException("Integration-stage weight must be finite");
    }
    for (int face = 0; face < lastPhaseMassFaceFluxes.length; face++) {
      if (accumulator[face] == null || accumulator[face].length != 3) {
        throw new IllegalArgumentException("Accumulator must contain three phase columns at every face");
      }
      for (int phase = 0; phase < 3; phase++) {
        accumulator[face][phase] += weight * lastPhaseMassFaceFluxes[face][phase];
      }
    }
  }

  /**
   * Add the most recently evaluated cell phase-mass sources to a caller-owned accumulator.
   *
   * <p>
   * Rows are finite-volume cells and columns are gas, oil, and water in kg/(m s). This is the source counterpart to
   * {@link #accumulateLastPhaseMassFaceFluxes(double[][], double)} and lets component transport use the same
   * Runge-Kutta stage weights as the accepted hydrodynamic phase masses.
   * </p>
   *
   * @param accumulator destination receiving {@code weight * sourceRate}
   * @param weight integration-stage weight
   */
  public void accumulateLastPhaseMassSourcesPerLength(double[][] accumulator, double weight) {
    if (accumulator == null || accumulator.length != lastPhaseMassSourcesPerLength.length) {
      throw new IllegalArgumentException("Accumulator must match the most recent phase-source shape");
    }
    if (!Double.isFinite(weight)) {
      throw new IllegalArgumentException("Accumulator weight must be finite");
    }
    for (int cell = 0; cell < lastPhaseMassSourcesPerLength.length; cell++) {
      if (accumulator[cell] == null || accumulator[cell].length != 3) {
        throw new IllegalArgumentException("Accumulator must contain three phase columns at every cell");
      }
      for (int phase = 0; phase < 3; phase++) {
        accumulator[cell][phase] += weight * lastPhaseMassSourcesPerLength[cell][phase];
      }
    }
  }

  /**
   * Calculate phase-resolved mass flow at every finite-volume face from the same boundary and AUSM+ fluxes used by the
   * conservative equations.
   *
   * <p>
   * Rows are faces from inlet through outlet; columns are gas, oil, and water in kg/s. A positive value points in the
   * increasing section-index direction. CLOSED boundaries are represented by the zero boundary velocities imposed by
   * {@link neqsim.process.equipment.pipeline.TwoFluidPipe}, so their external face flow is exactly zero while internal
   * convection remains available.
   * </p>
   *
   * @param sections current finite-volume sections
   * @param dx minimum mesh spacing in metres, retained for consistency with the spatial flux API
   * @return face mass flows with shape {@code [sections.length + 1][3]}
   * @throws IllegalArgumentException if {@code sections} is null or empty
   */
  public double[][] calcPhaseMassFaceFluxes(TwoFluidSection[] sections, double dx) {
    if (sections == null || sections.length == 0) {
      throw new IllegalArgumentException("At least one section is required to calculate face fluxes");
    }

    double[][] interfaceFluxes = sections.length > 1 ? calcInterfaceFluxes(sections, dx) : new double[0][NUM_EQUATIONS];
    double[] inletFlux = calcInletFlux(sections[0]);
    double[] outletFlux = calcOutletFlux(sections[sections.length - 1]);
    return populatePhaseMassFaceFluxes(null, sections.length, interfaceFluxes, inletFlux, outletFlux);
  }

  /**
   * Populate a caller-provided face-flux buffer, allocating only when its face count is incompatible.
   *
   * @param phaseMassFaceFluxes reusable destination, or {@code null}
   * @param sectionCount number of finite-volume sections
   * @param interfaceFluxes conservative fluxes at internal faces
   * @param inletFlux conservative inlet-face flux
   * @param outletFlux conservative outlet-face flux
   * @return populated face mass flows with gas, oil, and water columns
   */
  private double[][] populatePhaseMassFaceFluxes(double[][] phaseMassFaceFluxes, int sectionCount,
      double[][] interfaceFluxes, double[] inletFlux, double[] outletFlux) {
    if (phaseMassFaceFluxes == null || phaseMassFaceFluxes.length != sectionCount + 1) {
      phaseMassFaceFluxes = new double[sectionCount + 1][3];
    }
    phaseMassFaceFluxes[0][0] = inletFlux[IDX_GAS_MASS];
    phaseMassFaceFluxes[0][1] = inletFlux[IDX_OIL_MASS];
    phaseMassFaceFluxes[0][2] = inletFlux[IDX_WATER_MASS];

    for (int face = 1; face < sectionCount; face++) {
      double[] interfaceFlux = interfaceFluxes[face - 1];
      phaseMassFaceFluxes[face][0] = interfaceFlux[IDX_GAS_MASS];
      phaseMassFaceFluxes[face][1] = interfaceFlux[IDX_OIL_MASS];
      phaseMassFaceFluxes[face][2] = interfaceFlux[IDX_WATER_MASS];
    }

    phaseMassFaceFluxes[sectionCount][0] = outletFlux[IDX_GAS_MASS];
    phaseMassFaceFluxes[sectionCount][1] = outletFlux[IDX_OIL_MASS];
    phaseMassFaceFluxes[sectionCount][2] = outletFlux[IDX_WATER_MASS];
    return phaseMassFaceFluxes;
  }

  /**
   * Calculate inlet flux using the inlet cell state. This represents mass entering the domain from the inlet stream.
   * Uses holdups directly (set by steady state or BC) rather than computing from mass per length to avoid feedback from
   * cell depletion.
   *
   * @param sec the inlet pipe section
   * @return array of flux values for each conserved variable
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
    alphaG = Math.max(0.0, Math.min(1.0, alphaG));
    flux[IDX_GAS_MASS] = alphaG * rhoG * vG * A;
    flux[IDX_GAS_MOMENTUM] = alphaG * rhoG * vG * vG * A + alphaG * sec.getPressure() * A;

    // Oil flux - use holdup directly for inlet BC stability
    double rhoO = sec.getOilDensity();
    if (rhoO < 100)
      rhoO = 700.0; // Default oil density
    double vO = sec.getOilVelocity();
    double alphaO = sec.getOilHoldup();
    // Phase-resolved boundary holdup is authoritative; do not reconstruct an absent oil phase.
    alphaO = Math.max(0, Math.min(1 - alphaG, alphaO));
    flux[IDX_OIL_MASS] = alphaO * rhoO * vO * A;
    flux[IDX_OIL_MOMENTUM] = alphaO * rhoO * vO * vO * A + alphaO * sec.getPressure() * A;

    // Water flux - use holdup directly for inlet BC stability
    double rhoW = sec.getWaterDensity();
    if (rhoW < 100)
      rhoW = 1000.0; // Default water density
    double vW = sec.getWaterVelocity();
    double alphaW = sec.getWaterHoldup();
    // Phase-resolved boundary holdup is authoritative; do not reconstruct an absent water phase.
    alphaW = Math.max(0, Math.min(1 - alphaG - alphaO, alphaW));
    flux[IDX_WATER_MASS] = alphaW * rhoW * vW * A;
    flux[IDX_WATER_MOMENTUM] = alphaW * rhoW * vW * vW * A + alphaW * sec.getPressure() * A;

    // Energy flux
    if (includeEnergyEquation) {
      double HG = sec.getGasEnthalpy();
      double HL = sec.getLiquidEnthalpy();
      flux[IDX_ENERGY] = (alphaG * rhoG * vG * HG + (alphaO * rhoO * vO + alphaW * rhoW * vW) * HL) * A;
    }

    return flux;
  }

  /**
   * Calculate outlet flux using upwind scheme (transmissive boundary).
   *
   * <p>
   * By default, each reversed phase velocity is clamped at zero because a one-way transmissive boundary has no
   * upstream state to advect in. When signed outlet flow is explicitly enabled, the zero-gradient interior phase state
   * supplies that boundary state and a reversed phase carries signed mass and energy back into the domain. Use signed
   * flow only with a well-posed pressure-momentum model and a boundary whose physical interpretation permits fallback.
   * </p>
   *
   * @param sec the outlet pipe section
   * @return array of flux values for each conserved variable
   */
  private double[] calcOutletFlux(TwoFluidSection sec) {
    double[] flux = new double[NUM_EQUATIONS];
    double A = sec.getArea();

    if (!allowOutletPhaseBackflow
        && (sec.getGasVelocity() < 0.0 || sec.getOilVelocity() < 0.0 || sec.getWaterVelocity() < 0.0)) {
      outletBackflowClamped = true;
    }

    // Gas flux (positive velocity means outflow) - use default density if not set
    double rhoG = sec.getGasDensity();
    if (rhoG < 0.1)
      rhoG = 1.0; // Default gas density
    double vG = allowOutletPhaseBackflow ? sec.getGasVelocity() : Math.max(0.0, sec.getGasVelocity());
    double alphaG = sec.getGasMassPerLength() / (rhoG * A);
    alphaG = Math.max(0, Math.min(1, alphaG));
    flux[IDX_GAS_MASS] = alphaG * rhoG * vG * A;
    flux[IDX_GAS_MOMENTUM] = alphaG * rhoG * vG * vG * A + alphaG * sec.getPressure() * A;

    // Oil flux - use default density if not set
    double rhoO = sec.getOilDensity();
    if (rhoO < 100)
      rhoO = 700.0; // Default oil density
    double vO = allowOutletPhaseBackflow ? sec.getOilVelocity() : Math.max(0.0, sec.getOilVelocity());
    double alphaO = sec.getOilMassPerLength() / (rhoO * A);
    alphaO = Math.max(0, Math.min(alphaG > 0.99 ? 0 : 1, alphaO));
    flux[IDX_OIL_MASS] = alphaO * rhoO * vO * A;
    flux[IDX_OIL_MOMENTUM] = alphaO * rhoO * vO * vO * A + alphaO * sec.getPressure() * A;

    // Water flux - use default density if not set
    double rhoW = sec.getWaterDensity();
    if (rhoW < 100)
      rhoW = 1000.0; // Default water density
    double vW = allowOutletPhaseBackflow ? sec.getWaterVelocity() : Math.max(0.0, sec.getWaterVelocity());
    double alphaW = sec.getWaterMassPerLength() / (rhoW * A);
    alphaW = Math.max(0, Math.min(1 - alphaG - alphaO, alphaW));
    flux[IDX_WATER_MASS] = alphaW * rhoW * vW * A;
    flux[IDX_WATER_MOMENTUM] = alphaW * rhoW * vW * vW * A + alphaW * sec.getPressure() * A;

    // Energy flux
    if (includeEnergyEquation) {
      double HG = sec.getGasEnthalpy();
      double HL = sec.getLiquidEnthalpy();
      flux[IDX_ENERGY] = (alphaG * rhoG * vG * HG + (alphaO * rhoO * vO + alphaW * rhoW * vW) * HL) * A;
    }

    return flux;
  }

  /**
   * Update closure relations for all sections.
   *
   * @param sections array of pipe sections to update
   */
  private void updateClosureRelations(TwoFluidSection[] sections) {
    for (TwoFluidSection sec : sections) {
      sec.updateThreePhaseProperties();

      // Update flow regime
      sec.setFlowRegime(flowRegimeDetector.detectFlowRegime(sec));

      // Update stratified geometry if applicable
      PipeSection.FlowRegime regime = sec.getFlowRegime();
      if (regime == PipeSection.FlowRegime.STRATIFIED_SMOOTH || regime == PipeSection.FlowRegime.STRATIFIED_WAVY) {
        sec.updateStratifiedGeometry();
      }

      // Calculate wall friction
      WallFriction.WallFrictionResult wallResult = wallFriction.calculate(regime, sec.getGasVelocity(),
          sec.getLiquidVelocity(), sec.getGasDensity(), sec.getLiquidDensity(), sec.getGasViscosity(),
          sec.getLiquidViscosity(), sec.getLiquidHoldup(), sec.getDiameter(), sec.getRoughness());

      sec.setGasWallShear(wallResult.gasWallShear);
      sec.setLiquidWallShear(wallResult.liquidWallShear);

      // Calculate interfacial friction
      InterfacialFriction.InterfacialFrictionResult ifResult = interfacialFriction.calculate(regime,
          sec.getGasVelocity(), sec.getLiquidVelocity(), sec.getGasDensity(), sec.getLiquidDensity(),
          sec.getGasViscosity(), sec.getLiquidViscosity(), sec.getLiquidHoldup(), sec.getDiameter(),
          sec.getSurfaceTension());

      sec.setInterfacialShear(ifResult.interfacialShear);
      sec.setInterfacialWidth(ifResult.interfacialAreaPerLength);

      EntrainmentDeposition.EntrainmentResult entrainment = entrainmentDeposition.calculate(regime,
          sec.getGasVelocity(), sec.getLiquidVelocity(), sec.getGasDensity(), sec.getLiquidDensity(),
          sec.getGasViscosity(), sec.getLiquidViscosity(), sec.getSurfaceTension(), sec.getDiameter(),
          sec.getLiquidHoldup());
      sec.setEntrainmentFraction(entrainment.entrainmentFraction);
      sec.setEntrainedDropletDiameter(entrainment.dropletDiameter);

      double gasCarryoverNumber = calcInclinedSectionGasCarryoverNumber(sec);
      sec.setInclinedSectionGasCarryoverNumber(gasCarryoverNumber);
      sec.setInclinedSectionLiquidFallbackPotential(gasCarryoverNumber < 1.0);
    }
  }

  /**
   * Calculate a local inclined-section gas-carryover screen.
   *
   * <p>
   * The result contains no upstream gas volume, riser height, top pressure, or choke response and therefore must not be
   * interpreted as a severe-slugging system criterion.
   * </p>
   *
   * @param sec local solved pipe section
   * @return dimensionless gas-carryover number, or positive infinity when not applicable
   */
  private double calcInclinedSectionGasCarryoverNumber(TwoFluidSection sec) {
    if (sec.getInclination() <= Math.toRadians(5.0)) {
      return Double.POSITIVE_INFINITY;
    }

    double alphaL = sec.getLiquidHoldup();
    if (alphaL <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double rhoG = Math.max(sec.getGasDensity(), 0.1);
    double rhoL = Math.max(sec.getLiquidDensity(), 100.0);
    double densityContrast = Math.max(rhoL - rhoG, 1.0);
    double gasFroude = Math.abs(sec.getSuperficialGasVelocity())
        / Math.sqrt(GRAVITY * sec.getDiameter() * densityContrast / rhoL);
    return gasFroude / alphaL;
  }

  /**
   * Calculate fluxes at cell interfaces using AUSM+.
   *
   * <p>
   * For three-phase flow with water-oil slip, we track oil and water mass and momentum fluxes separately. Water
   * generally moves slower than oil in upward flow due to density differences.
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
    if (interfaceGasHoldup.length != nInterfaces) {
      interfaceGasHoldup = new double[nInterfaces];
      interfaceLiquidHoldup = new double[nInterfaces];
      interfacePressure = new double[nInterfaces];
    }

    for (int i = 0; i < nInterfaces; i++) {
      TwoFluidSection left = sections[i];
      TwoFluidSection right = sections[i + 1];
      double A = 0.5 * (left.getArea() + right.getArea());

      // Create phase states for AUSM+
      PhaseState gasL = createGasState(left);
      PhaseState gasR = createGasState(right);

      // Calculate gas flux using AUSM+
      PhaseFlux gasFlux = fluxCalculator.calcPhaseFlux(gasL, gasR, A);
      fluxes[i][IDX_GAS_MASS] = gasFlux.massFlux;
      fluxes[i][IDX_GAS_MOMENTUM] = gasFlux.momentumFlux;
      interfaceGasHoldup[i] = gasFlux.interfaceHoldup;
      interfacePressure[i] = gasFlux.interfacePressure;

      // For oil and water, use coupled approach to prevent oscillations.
      // Calculate combined liquid flux first, then split by upwind water cut.
      // This ensures oil and water move together proportionally.
      PhaseState liqL = createLiquidState(left);
      PhaseState liqR = createLiquidState(right);
      PhaseFlux liqFlux = fluxCalculator.calcPhaseFlux(liqL, liqR, A);
      interfaceLiquidHoldup[i] = liqFlux.interfaceHoldup;

      // Determine upwind direction for liquid
      double cHalf = 0.5 * (liqL.soundSpeed + liqR.soundSpeed);
      double Mhalf = liqL.velocity / cHalf + liqR.velocity / cHalf;
      boolean upwindIsLeft = (Mhalf >= 0) || (liqL.velocity >= 0 && liqR.velocity >= 0);

      // Get upwind water cut and densities for flux splitting
      double upwindWaterCut, upwindOilFrac;
      double rhoO, rhoW, vO, vW;
      if (upwindIsLeft) {
        upwindWaterCut = left.getWaterCut();
        rhoO = left.getOilDensity() > 100 ? left.getOilDensity() : 700.0;
        rhoW = left.getWaterDensity() > 100 ? left.getWaterDensity() : 1000.0;
        vO = left.getOilVelocity();
        vW = left.getWaterVelocity();
      } else {
        upwindWaterCut = right.getWaterCut();
        rhoO = right.getOilDensity() > 100 ? right.getOilDensity() : 700.0;
        rhoW = right.getWaterDensity() > 100 ? right.getWaterDensity() : 1000.0;
        vO = right.getOilVelocity();
        vW = right.getWaterVelocity();
      }
      upwindOilFrac = 1.0 - upwindWaterCut;

      // Split liquid mass flux by water cut (volume-based)
      // But account for density difference: m_oil/m_water = (1-wc)*rho_o / (wc*rho_w)
      double totalLiqMassFlux = liqFlux.massFlux;
      double oilMassFrac = upwindOilFrac * rhoO / (upwindOilFrac * rhoO + upwindWaterCut * rhoW);
      double waterMassFrac = 1.0 - oilMassFrac;

      fluxes[i][IDX_OIL_MASS] = totalLiqMassFlux * oilMassFrac;
      fluxes[i][IDX_WATER_MASS] = totalLiqMassFlux * waterMassFrac;

      // Split momentum flux similarly, but use individual velocities if available
      double totalLiqMomFlux = liqFlux.momentumFlux;
      // Approximate: momentum splits with mass but velocity may differ
      fluxes[i][IDX_OIL_MOMENTUM] = fluxes[i][IDX_OIL_MASS] * vO
          + upwindOilFrac * (totalLiqMomFlux - totalLiqMassFlux * liqL.velocity);
      fluxes[i][IDX_WATER_MOMENTUM] = fluxes[i][IDX_WATER_MASS] * vW
          + upwindWaterCut * (totalLiqMomFlux - totalLiqMassFlux * liqL.velocity);

      if (includeEnergyEquation) {
        fluxes[i][IDX_ENERGY] = gasFlux.energyFlux + liqFlux.energyFlux;
      }
    }

    return fluxes;
  }

  /**
   * Calculate source terms for all cells.
   *
   * <p>
   * For three-phase flow, tracks water separately from oil. Water accumulates more in valleys due to higher density.
   * </p>
   *
   * @param sections Pipe sections
   * @return Source terms [nCells][NUM_EQUATIONS]
   */
  private double[][] calcSourceTerms(TwoFluidSection[] sections) {
    int nCells = sections.length;
    double[][] sources = new double[nCells][NUM_EQUATIONS];
    if (lastPhaseMassSourcesPerLength.length != nCells) {
      lastPhaseMassSourcesPerLength = new double[nCells][3];
    }

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
      boolean stiffBubbleDrag = enableStiffBubbleDrag && isDispersedBubbleRegime(sec.getFlowRegime());
      double F_iG = stiffBubbleDrag ? 0.0 : -sec.getInterfacialShear() * S_i;
      double F_iL = stiffBubbleDrag ? 0.0 : sec.getInterfacialShear() * S_i;

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
      PhaseMassTransfer phaseMassTransfer = PhaseMassTransfer.zero(true, true, null);
      if (includeMassTransfer) {
        phaseMassTransfer = calcPhaseMassTransfer(sec);
      }
      double Gamma_G = phaseMassTransfer.getGasSourceKgPerMetreSecond();
      double Gamma_O = phaseMassTransfer.getOilSourceKgPerMetreSecond();
      double Gamma_W = phaseMassTransfer.getWaterSourceKgPerMetreSecond();
      double Gamma_L = Gamma_O + Gamma_W;
      double[] transferMomentum = calcTransferMomentumSources(sec, phaseMassTransfer);

      // Assemble source terms - now with separate oil and water mass equations
      sources[i][IDX_GAS_MASS] = Gamma_G;

      // Oil-water segregation is driven by the separate momentum equations and
      // transported through phase fluxes. A local mass relaxation would convert oil
      // into water (or vice versa) and, because their densities differ, would also
      // change total inventory without a conservative face flux.
      sources[i][IDX_OIL_MASS] = Gamma_O;
      sources[i][IDX_WATER_MASS] = Gamma_W;
      lastPhaseMassSourcesPerLength[i][0] = Gamma_G;
      lastPhaseMassSourcesPerLength[i][1] = Gamma_O;
      lastPhaseMassSourcesPerLength[i][2] = Gamma_W;

      sources[i][IDX_GAS_MOMENTUM] = F_wG + F_iG + F_gG + transferMomentum[0];

      if (enableWaterOilSlip && NUM_EQUATIONS == 7) {
        // Separate oil and water momentum equations
        // Wall friction partitioned between oil and water based on holdup
        double oilHoldupFrac = (alphaL > 0.01) ? alphaO / alphaL : 0.5;
        double waterHoldupFrac = (alphaL > 0.01) ? alphaW / alphaL : 0.5;

        double F_wO = F_wL * oilHoldupFrac;
        double F_wW = F_wL * waterHoldupFrac;

        // Gas-liquid interfacial force partitioned based on oil-water flow regime.
        // In stratified oil-water: gas sits on top of oil, so oil gets most interface force.
        // In dispersed W/O: oil (continuous) gets all gas-liquid interface force.
        // In dispersed O/W: water (continuous) gets most gas-liquid interface force.
        double oilInterfaceFrac = 0.8; // Default: oil gets most of gas-liquid interface
        if (sec.getOilWaterResult() != null) {
          switch (sec.getOilWaterResult().regime) {
          case DISPERSED_OIL_IN_WATER:
            // Water is continuous; gas interacts mainly with water
            oilInterfaceFrac = 0.2;
            break;
          case DISPERSED_WATER_IN_OIL:
            // Oil is continuous; gas interacts mainly with oil
            oilInterfaceFrac = 0.9;
            break;
          case DUAL_DISPERSION:
            // Both present; split by holdup fraction
            oilInterfaceFrac = oilHoldupFrac;
            break;
          case STRATIFIED:
          case STRATIFIED_WITH_MIXING:
            // Stratified: gas on top of oil, oil gets most interface
            oilInterfaceFrac = 0.85;
            break;
          default:
            oilInterfaceFrac = 0.8;
            break;
          }
        }
        double F_iO = F_iL * oilInterfaceFrac;
        double F_iW = F_iL * (1.0 - oilInterfaceFrac);

        // Oil-water interfacial shear (from TwoFluidSection calculation)
        double tau_ow = sec.calcOilWaterInterfacialShear();

        // Estimate oil-water interface length (simplified as fraction of diameter)
        double S_ow = sec.getDiameter() * 0.5 * alphaL;

        // Force on oil from oil-water interface (negative = retarded by water)
        double F_ow_oil = -tau_ow * S_ow;
        double F_ow_water = tau_ow * S_ow; // Opposite sign

        // Assemble oil momentum source
        sources[i][IDX_OIL_MOMENTUM] = F_wO + F_iO + F_gO + F_ow_oil + transferMomentum[1];

        // Assemble water momentum source
        sources[i][IDX_WATER_MOMENTUM] = F_wW + F_iW + F_gW + F_ow_water + transferMomentum[2];
      } else {
        // Combined liquid momentum (original 6-equation model)
        sources[i][IDX_OIL_MOMENTUM] = F_wL + F_iL + F_gL + transferMomentum[1] + transferMomentum[2];
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
   * Apply the non-conservative holdup-gradient momentum term.
   *
   * <p>
   * The momentum flux carries the phase pressure contribution as {@code alpha_k * p * A}, so its divergence produces
   * {@code p * A * d(alpha_k)/dx} on top of the physical {@code alpha_k * A * dp/dx}. That extra term is spurious and
   * must be cancelled. Cancelling it alone would leave the classical two-fluid system, which has complex
   * characteristics and is ill-posed: short wavelengths grow without bound, which shows up as sustained phase backflow
   * in liquid-rich flow. The standard remedy is to retain an interfacial pressure difference
   * {@code delta_p_i = p - p_i} in the same term, giving
   * </p>
   *
   * <pre>
   * S_k += (p - delta_p_i) * A * d(alpha_k) / dx
   * </pre>
   *
   * <p>
   * with the Bestion closure {@code delta_p_i = delta * rho_g * rho_l * (u_g - u_l)^2 / (alpha_g * rho_l + alpha_l *
   * rho_g)}. Because the phase fractions sum to one their gradients sum to zero, so the total momentum added over the
   * three phases is exactly zero and the scheme stays conservative.
   * </p>
   *
   * @param sections current stage state
   * @param dUdt complete uncoupled conservative-variable rates, modified in place
   */
  void applyInterfacialPressure(TwoFluidSection[] sections, double[][] dUdt) {
    if (!enableInterfacialPressure) {
      return;
    }
    int nCells = sections.length;
    if (nCells < 2 || interfaceGasHoldup.length != nCells - 1) {
      return;
    }

    for (int i = 0; i < nCells; i++) {
      TwoFluidSection sec = sections[i];
      double area = sec.getArea();
      double pressure = sec.getPressure();
      double secDx = sec.getLength();
      if (!(area > 0.0) || !(pressure > 0.0) || !(secDx > 0.0)) {
        continue;
      }

      // Cancel the spurious force exactly: the flux divergence contributes
      // d(alphaBar * pBar)/dx, while the physical term is alpha_i * dp/dx. Differencing the
      // same interface values the flux used leaves no residual when the holdup is uniform.
      double gasRight = (i < nCells - 1) ? interfaceGasHoldup[i] : sec.getGasHoldup();
      double gasLeft = (i > 0) ? interfaceGasHoldup[i - 1] : sec.getGasHoldup();
      double liqRight = (i < nCells - 1) ? interfaceLiquidHoldup[i] : sec.getLiquidHoldup();
      double liqLeft = (i > 0) ? interfaceLiquidHoldup[i - 1] : sec.getLiquidHoldup();
      double pRight = (i < nCells - 1) ? interfacePressure[i] : pressure;
      double pLeft = (i > 0) ? interfacePressure[i - 1] : pressure;

      double deltaPi = calcInterfacialPressureDifference(sec);
      double dp = pRight - pLeft;

      double gasSpurious = (gasRight * pRight - gasLeft * pLeft) - sec.getGasHoldup() * dp;
      double liqSpurious = (liqRight * pRight - liqLeft * pLeft) - sec.getLiquidHoldup() * dp;
      double gasStabiliser = implicitInterfacialPressure ? 0.0 : deltaPi * (gasRight - gasLeft);
      double liqStabiliser = implicitInterfacialPressure ? 0.0 : deltaPi * (liqRight - liqLeft);

      double gasSource = (gasSpurious - gasStabiliser) * area / secDx;
      double liqSource = (liqSpurious - liqStabiliser) * area / secDx;

      dUdt[i][IDX_GAS_MOMENTUM] += gasSource;
      if (enableWaterOilSlip) {
        // Split the liquid share by holdup so the three phases still sum to zero.
        double alphaL = sec.getLiquidHoldup();
        double oilFraction = alphaL > 1.0e-9 ? sec.getOilHoldup() / alphaL : 1.0;
        oilFraction = Math.max(0.0, Math.min(1.0, oilFraction));
        dUdt[i][IDX_OIL_MOMENTUM] += liqSource * oilFraction;
        dUdt[i][IDX_WATER_MOMENTUM] += liqSource * (1.0 - oilFraction);
      } else {
        dUdt[i][IDX_OIL_MOMENTUM] += liqSource;
      }
    }
  }

  /**
   * Get the interfacial pressure difference {@code p - p_i} from the Bestion closure.
   *
   * <p>
   * The characteristics of the two-fluid system are real when {@code p - p_i} is at least
   * {@code alpha_g * alpha_l * rho_g * rho_l * (u_g - u_l)^2 / (alpha_g * rho_l + alpha_l * rho_g)}, so the coefficient
   * is that critical value scaled by delta. The {@code alpha_g * alpha_l} factor makes the term vanish in both
   * single-phase limits, where no interfacial pressure exists.
   * </p>
   *
   * @param sec pipe section to evaluate
   * @return interfacial pressure difference in pascals, never negative
   */
  double calcInterfacialPressureDifference(TwoFluidSection sec) {
    double alphaG = Math.max(0.0, Math.min(1.0, sec.getGasHoldup()));
    double alphaL = Math.max(0.0, Math.min(1.0, sec.getLiquidHoldup()));
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    if (!(rhoG > 0.0) || !(rhoL > 0.0) || alphaG <= 0.0 || alphaL <= 0.0) {
      return 0.0;
    }
    double mixed = alphaG * rhoL + alphaL * rhoG;
    if (!(mixed > 0.0)) {
      return 0.0;
    }
    double slip = sec.getGasVelocity() - sec.getLiquidVelocity();
    double deltaPi = interfacialPressureCoefficient * alphaG * alphaL * rhoG * rhoL * slip * slip / mixed;
    return Double.isFinite(deltaPi) ? Math.max(0.0, deltaPi) : 0.0;
  }

  /**
   * Get the drift-flux slip coefficient of the interfacial pressure term.
   *
   * <p>
   * Converting the two phase momentum sources {@code -delta_p_i * A * d(alpha_k)/dx} into an equation for the drift
   * flux {@code q = alpha_g * alpha_l * (u_g - u_l)} gives {@code dq/dt = -K * d(alpha_g)/dx} with
   * {@code K = delta_p_i * (alpha_l / rho_g + alpha_g / rho_l)}. That coefficient is what the implicit void-wave update
   * needs, and it is not the same as the square of {@link #calcVoidWaveSpeed(TwoFluidSection)}.
   * </p>
   *
   * @param sec pipe section to evaluate
   * @return slip coefficient in m2/s2, never negative
   */
  public double calcVoidWaveSlipCoefficient(TwoFluidSection sec) {
    double alphaG = Math.max(0.0, Math.min(1.0, sec.getGasHoldup()));
    double alphaL = Math.max(0.0, Math.min(1.0, sec.getLiquidHoldup()));
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    if (!enableInterfacialPressure || !(rhoG > 0.0) || !(rhoL > 0.0) || alphaG <= 0.0 || alphaL <= 0.0) {
      return 0.0;
    }
    double coefficient = calcInterfacialPressureDifference(sec) * (alphaL / rhoG + alphaG / rhoL);
    return Double.isFinite(coefficient) ? Math.max(0.0, coefficient) : 0.0;
  }

  /**
   * Get the void-wave speed introduced by the interfacial pressure term.
   *
   * <p>
   * This is the extra characteristic speed relative to the mixture velocity, and it must be included in the CFL limit
   * once the term is active.
   * </p>
   *
   * @param sec pipe section to evaluate
   * @return void-wave speed in m/s, never negative
   */
  public double calcVoidWaveSpeed(TwoFluidSection sec) {
    if (!enableInterfacialPressure) {
      return 0.0;
    }
    double alphaG = Math.max(0.0, Math.min(1.0, sec.getGasHoldup()));
    double alphaL = Math.max(0.0, Math.min(1.0, sec.getLiquidHoldup()));
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    if (!(rhoG > 0.0) || !(rhoL > 0.0) || alphaG <= 0.0 || alphaL <= 0.0) {
      return 0.0;
    }
    double mixed = alphaG * rhoL + alphaL * rhoG;
    if (!(mixed > 0.0)) {
      return 0.0;
    }
    double deltaPi = calcInterfacialPressureDifference(sec);
    double speed = Math.sqrt(Math.max(0.0, deltaPi / mixed));
    return Double.isFinite(speed) ? speed : 0.0;
  }

  /**
   * Apply the local, stage-pure virtual-mass momentum coupling.
   *
   * <p>
   * The uncoupled conservative rates already contain flux divergence, pressure, gravity, friction, and transfer
   * sources. Converting those rates to phase accelerations and solving the two-phase added-inertia relation
   * algebraically avoids hidden velocity history and makes repeated RHS evaluations deterministic.
   * </p>
   *
   * @param sections current stage state
   * @param dUdt complete uncoupled conservative-variable rates, modified in place
   */
  void applyVirtualMassCoupling(TwoFluidSection[] sections, double[][] dUdt) {
    if (!enableVirtualMassForce || virtualMassCoefficient <= 0.0) {
      return;
    }

    for (int i = 0; i < sections.length; i++) {
      TwoFluidSection sec = sections[i];
      double gasMass = sec.getGasMassPerLength();
      double oilMass = sec.getOilMassPerLength();
      double waterMass = sec.getWaterMassPerLength();
      double liquidMass = oilMass + waterMass;
      double liquidDensity = sec.getLiquidDensity();
      double area = sec.getArea();
      double gasHoldup = Math.max(0.0, Math.min(1.0, sec.getGasHoldup()));

      if (!Double.isFinite(gasMass) || !Double.isFinite(liquidMass) || !Double.isFinite(liquidDensity)
          || !Double.isFinite(area) || gasMass <= 0.0 || liquidMass <= 0.0 || liquidDensity <= 0.0 || area <= 0.0
          || gasHoldup <= 0.0) {
        continue;
      }

      double gasVelocity = sec.getGasVelocity();
      double gasAcceleration = (dUdt[i][IDX_GAS_MOMENTUM] - gasVelocity * dUdt[i][IDX_GAS_MASS]) / gasMass;

      double liquidMomentumRate;
      double liquidMassVelocityRate;
      if (enableWaterOilSlip) {
        liquidMomentumRate = dUdt[i][IDX_OIL_MOMENTUM] + dUdt[i][IDX_WATER_MOMENTUM];
        liquidMassVelocityRate = sec.getOilVelocity() * dUdt[i][IDX_OIL_MASS]
            + sec.getWaterVelocity() * dUdt[i][IDX_WATER_MASS];
      } else {
        liquidMomentumRate = dUdt[i][IDX_OIL_MOMENTUM];
        liquidMassVelocityRate = sec.getLiquidVelocity() * (dUdt[i][IDX_OIL_MASS] + dUdt[i][IDX_WATER_MASS]);
      }
      double liquidAcceleration = (liquidMomentumRate - liquidMassVelocityRate) / liquidMass;

      double addedMassPerLength = virtualMassCoefficient * gasHoldup * liquidDensity * area;
      double denominator = 1.0 + addedMassPerLength * (1.0 / gasMass + 1.0 / liquidMass);
      double gasVirtualMassForce = -addedMassPerLength * (gasAcceleration - liquidAcceleration) / denominator;
      if (!Double.isFinite(gasVirtualMassForce)) {
        continue;
      }

      dUdt[i][IDX_GAS_MOMENTUM] += gasVirtualMassForce;
      if (enableWaterOilSlip) {
        double liquidVirtualMassForce = -gasVirtualMassForce;
        double oilVirtualMassForce = liquidVirtualMassForce * oilMass / liquidMass;
        double waterVirtualMassForce = liquidVirtualMassForce - oilVirtualMassForce;
        dUdt[i][IDX_OIL_MOMENTUM] += oilVirtualMassForce;
        dUdt[i][IDX_WATER_MOMENTUM] += waterVirtualMassForce;
      } else {
        dUdt[i][IDX_OIL_MOMENTUM] -= gasVirtualMassForce;
      }

      sec.setGasMomentumSource(sec.getGasMomentumSource() + gasVirtualMassForce);
      sec.setLiquidMomentumSource(sec.getLiquidMomentumSource() - gasVirtualMassForce);
    }
  }

  /**
   * Advance corrected dispersed-bubble drag with a pure local implicit source solve.
   *
   * <p>
   * The input state is not modified. Gas and combined-liquid momentum are coupled conservatively; the liquid impulse is
   * distributed by active oil/water mass so water-oil slip is preserved. The energy state is unchanged, making lost
   * kinetic energy available as internal energy.
   * </p>
   *
   * @param sections sections whose primitive properties correspond to {@code state}
   * @param state conservative state before the drag source step
   * @param timeStep source-step duration in s
   * @return a new conservative state after dispersed-bubble drag
   * @throws IllegalArgumentException if section/state dimensions or the time step are invalid
   */
  public double[][] applyStiffBubbleDrag(TwoFluidSection[] sections, double[][] state, double timeStep) {
    if (sections == null || state == null || sections.length != state.length) {
      throw new IllegalArgumentException("Section and state dimensions must agree");
    }
    if (!Double.isFinite(timeStep) || timeStep < 0.0) {
      throw new IllegalArgumentException("Bubble-drag source-step duration must be finite and non-negative");
    }

    double[][] result = new double[state.length][NUM_EQUATIONS];
    for (int sectionIndex = 0; sectionIndex < state.length; sectionIndex++) {
      if (state[sectionIndex] == null || state[sectionIndex].length != NUM_EQUATIONS) {
        throw new IllegalArgumentException("Every section state must contain seven conservative variables");
      }
      System.arraycopy(state[sectionIndex], 0, result[sectionIndex], 0, NUM_EQUATIONS);
      if (!enableStiffBubbleDrag || timeStep == 0.0) {
        continue;
      }

      TwoFluidSection section = sections[sectionIndex];
      PipeSection.FlowRegime regime = flowRegimeDetector.detectFlowRegime(section);
      if (!isDispersedBubbleRegime(regime)) {
        continue;
      }
      double[] masses = { state[sectionIndex][IDX_GAS_MASS], state[sectionIndex][IDX_OIL_MASS],
          state[sectionIndex][IDX_WATER_MASS] };
      double[] momenta = { state[sectionIndex][IDX_GAS_MOMENTUM], state[sectionIndex][IDX_OIL_MOMENTUM],
          state[sectionIndex][IDX_WATER_MOMENTUM] };
      double[] relaxedMomenta = DispersedBubbleDragSolver.relax(regime, masses, momenta, section.getGasDensity(),
          section.getLiquidDensity(), section.getGasViscosity(), section.getLiquidViscosity(),
          section.getLiquidHoldup(), section.getDiameter(), section.getSurfaceTension(), timeStep, interfacialFriction);
      result[sectionIndex][IDX_GAS_MOMENTUM] = relaxedMomenta[0];
      result[sectionIndex][IDX_OIL_MOMENTUM] = relaxedMomenta[1];
      result[sectionIndex][IDX_WATER_MOMENTUM] = relaxedMomenta[2];
    }
    return result;
  }

  private boolean isDispersedBubbleRegime(PipeSection.FlowRegime flowRegime) {
    return flowRegime == PipeSection.FlowRegime.BUBBLE || flowRegime == PipeSection.FlowRegime.DISPERSED_BUBBLE;
  }

  /**
   * Create gas phase state for flux calculation. Uses true holdup from mass per length for mass-consistent flux.
   *
   * @param sec the pipe section
   * @return gas phase state object
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
   * Create liquid phase state for flux calculation. Uses true holdup from mass per length for mass-consistent flux.
   *
   * @param sec the pipe section
   * @return liquid phase state object
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
   * Create oil phase state for flux calculation in three-phase flow. Uses true holdup from mass per length for
   * mass-consistent flux.
   *
   * @param sec the pipe section
   * @return oil phase state object
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
   * Create water phase state for flux calculation in three-phase flow. Uses true holdup from mass per length for
   * mass-consistent flux.
   *
   * @param sec the pipe section
   * @return water phase state object
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
   * Calculate conservative mass transfer between gas and liquid phases.
   *
   * @param sec Pipe section
   * @return [gasSource, liquidSource] in kg/(m·s)
   */
  double[] calcMassTransfer(TwoFluidSection sec) {
    if (thermodynamicCoupling != null) {
      PhaseMassTransfer transfer = calcPhaseMassTransfer(sec);
      return new double[] { transfer.getGasSourceKgPerMetreSecond(),
          transfer.getOilSourceKgPerMetreSecond() + transfer.getWaterSourceKgPerMetreSecond() };
    }

    return conservedMassTransferPair(getPrescribedGasSourcePerLength(sec));
  }

  /**
   * Calculate phase-resolved mass-transfer sources for a section.
   *
   * <p>
   * Flash-driven transfer delegates to {@link ThermodynamicCoupling}. A prescribed evaporation source is distributed
   * over the actual oil and water donor inventories. A prescribed condensation source has no equilibrium phase
   * identity, so the explicitly configured hydrodynamic water cut is retained for backward compatibility.
   * </p>
   *
   * @param sec pipe section
   * @return immutable phase-resolved transfer result in kg/(m s)
   */
  PhaseMassTransfer calcPhaseMassTransfer(TwoFluidSection sec) {
    if (thermodynamicCoupling != null) {
      return thermodynamicCoupling.calcPhaseMassTransferRatePerLength(sec, massTransferRelaxationTime);
    }

    double gasSource = getPrescribedGasSourcePerLength(sec);
    if (gasSource > 0.0) {
      double oilInventory = Math.max(0.0, sec.getOilMassPerLength());
      double waterInventory = Math.max(0.0, sec.getWaterMassPerLength());
      double liquidInventory = oilInventory + waterInventory;
      if (liquidInventory <= 0.0) {
        return PhaseMassTransfer.zero(true, false, "No liquid donor inventory for prescribed evaporation");
      }
      double oilSource = -gasSource * oilInventory / liquidInventory;
      double waterSource = -gasSource - oilSource;
      return new PhaseMassTransfer(gasSource, oilSource, waterSource, true, true, null);
    }
    if (gasSource < 0.0) {
      double waterFraction = Math.max(0.0, Math.min(1.0, sec.getWaterCut()));
      double oilSource = -gasSource * (1.0 - waterFraction);
      double waterSource = -gasSource - oilSource;
      return new PhaseMassTransfer(gasSource, oilSource, waterSource, true, true, null);
    }
    return PhaseMassTransfer.zero(true, true, null);
  }

  /**
   * Convert a prescribed section transfer rate to a gas source per unit pipe length.
   *
   * @param sec pipe section
   * @return gas source in kg/(m s)
   */
  private double getPrescribedGasSourcePerLength(TwoFluidSection sec) {
    double prescribedRate = sec.getMassTransferRate();
    if (!Double.isFinite(prescribedRate)) {
      throw new IllegalStateException("Mass transfer rate must be finite");
    }
    if (Math.abs(prescribedRate) == 0.0) {
      return 0.0;
    }
    double sectionLength = sec.getLength();
    if (!Double.isFinite(sectionLength) || sectionLength <= 0.0) {
      throw new IllegalStateException("Section length must be positive for mass transfer");
    }
    return prescribedRate / sectionLength;
  }

  /**
   * Calculate transfer-only phase momentum sources using donor velocity.
   *
   * <p>
   * During evaporation the gas receives the momentum removed from each liquid donor. During condensation the gas loses
   * momentum at gas velocity and each receiving liquid gains momentum at that same donor velocity. The three returned
   * sources therefore sum to zero apart from round-off.
   * </p>
   *
   * @param sec pipe section containing phase velocities
   * @param transfer phase-resolved mass-transfer sources
   * @return gas, oil, and water momentum sources in N/m
   */
  double[] calcTransferMomentumSources(TwoFluidSection sec, PhaseMassTransfer transfer) {
    double gasMassSource = transfer.getGasSourceKgPerMetreSecond();
    double oilMassSource = transfer.getOilSourceKgPerMetreSecond();
    double waterMassSource = transfer.getWaterSourceKgPerMetreSecond();
    double gasMomentumSource;
    double oilMomentumSource;
    double waterMomentumSource;

    if (gasMassSource > 0.0) {
      oilMomentumSource = oilMassSource * sec.getOilVelocity();
      waterMomentumSource = waterMassSource * sec.getWaterVelocity();
      gasMomentumSource = -oilMomentumSource - waterMomentumSource;
    } else if (gasMassSource < 0.0) {
      gasMomentumSource = gasMassSource * sec.getGasVelocity();
      oilMomentumSource = oilMassSource * sec.getGasVelocity();
      waterMomentumSource = -gasMomentumSource - oilMomentumSource;
    } else {
      gasMomentumSource = 0.0;
      oilMomentumSource = 0.0;
      waterMomentumSource = 0.0;
    }
    return new double[] { gasMomentumSource, oilMomentumSource, waterMomentumSource };
  }

  private double[] conservedMassTransferPair(double gasSource) {
    if (!Double.isFinite(gasSource)) {
      throw new IllegalStateException("Mass transfer source must be finite");
    }

    double liquidSource = -gasSource;
    if (Math.abs(gasSource + liquidSource) > 1e-12) {
      throw new IllegalStateException("Mass transfer source terms must sum to zero");
    }

    return new double[] { gasSource, liquidSource };
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
    double WG = Math.abs(sec.getGasWallShear() * sec.getGasVelocity() * sec.getGasWettedPerimeter());
    double WL = Math.abs(sec.getLiquidWallShear() * sec.getLiquidVelocity() * sec.getLiquidWettedPerimeter());

    return WG + WL;
  }

  /**
   * Apply pressure gradient term (handled separately for numerical stability).
   *
   * @param sections Pipe sections
   * @param dUdt Current RHS values to modify
   * @param dx Cell size (used for uniform mesh; per-section length used when available)
   */
  public void applyPressureGradient(TwoFluidSection[] sections, double[][] dUdt, double dx) {
    int nCells = sections.length;

    for (int i = 0; i < nCells; i++) {
      TwoFluidSection sec = sections[i];

      // Central difference for pressure gradient using per-section lengths
      double dPdx;
      if (i == 0) {
        double dxFwd = 0.5 * (sections[0].getLength() + sections[1].getLength());
        dPdx = (sections[1].getPressure() - sections[0].getPressure()) / dxFwd;
      } else if (i == nCells - 1) {
        double dxBwd = 0.5 * (sections[nCells - 2].getLength() + sections[nCells - 1].getLength());
        dPdx = (sections[nCells - 1].getPressure() - sections[nCells - 2].getPressure()) / dxBwd;
      } else {
        // Distance from center of cell i-1 to center of cell i+1
        double dxCentral = 0.5 * sections[i - 1].getLength() + sections[i].getLength()
            + 0.5 * sections[i + 1].getLength();
        dPdx = (sections[i + 1].getPressure() - sections[i - 1].getPressure()) / dxCentral;
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

  /** @return true when the holdup-gradient and interfacial pressure momentum term is applied */
  public boolean isEnableInterfacialPressure() {
    return enableInterfacialPressure;
  }

  /**
   * Enable or disable the holdup-gradient momentum term with its interfacial pressure correction.
   *
   * @param enableInterfacialPressure true to apply the term
   */
  public void setEnableInterfacialPressure(boolean enableInterfacialPressure) {
    this.enableInterfacialPressure = enableInterfacialPressure;
  }

  /**
   * Select whether the time integrator handles the stiff Bestion stabilizer implicitly.
   *
   * @param implicitInterfacialPressure true to omit the stabilizer from the explicit RHS
   */
  public void setImplicitInterfacialPressure(boolean implicitInterfacialPressure) {
    this.implicitInterfacialPressure = implicitInterfacialPressure;
  }

  /**
   * Whether the transmissive outlet has had to suppress a reversed phase velocity.
   *
   * @return true when at least one phase reversed at the outlet since the flag was last cleared
   */
  public boolean isOutletBackflowClamped() {
    return outletBackflowClamped;
  }

  /** Clear the outlet backflow record. */
  public void clearOutletBackflowClamped() {
    this.outletBackflowClamped = false;
  }

  /**
   * Allow signed phase flow through the zero-gradient outlet.
   *
   * @param allow true to extrapolate the interior phase state for outlet fallback
   */
  public void setAllowOutletPhaseBackflow(boolean allow) {
    this.allowOutletPhaseBackflow = allow;
  }

  /** @return true when signed outlet phase flow is enabled */
  public boolean isOutletPhaseBackflowAllowed() {
    return allowOutletPhaseBackflow;
  }

  /** @return interfacial pressure coefficient delta */
  public double getInterfacialPressureCoefficient() {
    return interfacialPressureCoefficient;
  }

  /**
   * Set the interfacial pressure coefficient delta. Values below one leave the system ill-posed.
   *
   * @param interfacialPressureCoefficient non-negative finite coefficient
   */
  public void setInterfacialPressureCoefficient(double interfacialPressureCoefficient) {
    if (!Double.isFinite(interfacialPressureCoefficient) || interfacialPressureCoefficient < 0.0) {
      throw new IllegalArgumentException("Interfacial pressure coefficient must be finite and non-negative");
    }
    this.interfacialPressureCoefficient = interfacialPressureCoefficient;
  }

  public void setThermodynamicCoupling(ThermodynamicCoupling thermodynamicCoupling) {
    this.thermodynamicCoupling = thermodynamicCoupling;
  }

  public void setMassTransferRelaxationTime(double massTransferRelaxationTime) {
    if (!Double.isFinite(massTransferRelaxationTime) || massTransferRelaxationTime <= 0.0) {
      throw new IllegalArgumentException("Mass transfer relaxation time must be finite and positive");
    }
    this.massTransferRelaxationTime = massTransferRelaxationTime;
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
   * When enabled, uses 7-equation model with separate oil and water momentum equations, allowing water to flow at
   * different velocity than oil (e.g., water slipping back in uphill flow).
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
    if (!Double.isFinite(massTransferCoefficient) || massTransferCoefficient < 0.0) {
      throw new IllegalArgumentException("Mass transfer coefficient must be finite and non-negative");
    }
    this.massTransferCoefficient = massTransferCoefficient;
  }

  public WallFriction getWallFriction() {
    return wallFriction;
  }

  public InterfacialFriction getInterfacialFriction() {
    return interfacialFriction;
  }

  /**
   * Get the bubble-size closure used by the interfacial momentum model.
   *
   * @return mutable bubble-size closure configuration
   */
  public BubbleSizeClosure getBubbleSizeClosure() {
    return interfacialFriction.getBubbleSizeClosure();
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

  // ============ Virtual Mass Force Configuration ============

  /**
   * Enable or disable the virtual mass force term.
   *
   * <p>
   * The virtual mass force accounts for the inertia of displaced fluid during phase acceleration. It is important for:
   * </p>
   * <ul>
   * <li>Slug initiation and propagation</li>
   * <li>Rapid transients (valve closures, ramp-ups)</li>
   * <li>Wave growth and slug frequency prediction</li>
   * </ul>
   *
   * <p>
   * Reference: Drew, D.A. and Lahey, R.T. (1987), Int. J. Multiphase Flow.
   * </p>
   *
   * @param enable true to enable virtual mass force
   */
  public void setEnableVirtualMassForce(boolean enable) {
    this.enableVirtualMassForce = enable;
  }

  /**
   * Check if virtual mass force is enabled.
   *
   * @return true if virtual mass force is active
   */
  public boolean isVirtualMassForceEnabled() {
    return enableVirtualMassForce;
  }

  /**
   * Set the virtual mass coefficient.
   *
   * <p>
   * For spherical particles, the theoretical value is C_vm = 0.5. Practical values range from 0.3 to 0.7 depending on
   * void fraction and flow regime.
   * </p>
   *
   * @param coefficient Virtual mass coefficient (typically 0.3-0.7)
   */
  public void setVirtualMassCoefficient(double coefficient) {
    this.virtualMassCoefficient = Math.max(0, Math.min(coefficient, 1.0));
  }

  /**
   * Get the virtual mass coefficient.
   *
   * @return Virtual mass coefficient
   */
  public double getVirtualMassCoefficient() {
    return virtualMassCoefficient;
  }

  /**
   * Enable or disable conservative local implicit treatment of bubble drag.
   *
   * <p>
   * This mode is opt-in because the corrected closure is not yet quantitatively validated by the public Tengesdal
   * severe-slugging benchmark. Enabling it selects the corrected force and the stiff source treatment together.
   * </p>
   *
   * @param enable true to use the local stiff source solve
   */
  public void setEnableStiffBubbleDrag(boolean enable) {
    this.enableStiffBubbleDrag = enable;
    interfacialFriction.setUseCorrectedBubbleDrag(enable);
  }

  /**
   * Check whether corrected bubble drag uses the local stiff source solve.
   *
   * @return true when conservative implicit bubble drag is enabled
   */
  public boolean isStiffBubbleDragEnabled() {
    return enableStiffBubbleDrag;
  }

  /**
   * Retain the timestep setting used by earlier virtual-mass implementations.
   *
   * <p>
   * The stage-pure algebraic coupling no longer depends on a stored timestep. This method remains for source and
   * serialization compatibility.
   * </p>
   *
   * @param timestep Timestep in seconds
   */
  public void setTimestep(double timestep) {
    this.dt = Math.max(1e-10, timestep);
  }

  /**
   * Get the retained legacy timestep setting.
   *
   * @return Timestep in seconds
   */
  public double getTimestep() {
    return dt;
  }

  /** Reset legacy virtual-mass history (no-op for the stage-pure implementation). */
  public void resetVirtualMassState() {
    // No hidden history is retained.
  }
}
