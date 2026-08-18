package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.twophasepipe.FlowRegimeDetector;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.closure.BubbleSizeClosure;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterFlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.ConservativeStateLimiter;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Two-fluid transient multiphase pipe model.
 *
 * <p>
 * Implements a full two-fluid model for 1D transient multiphase pipeline flow. Unlike the drift-flux based
 * {@link neqsim.process.equipment.pipeline.twophasepipe.TransientPipe}, this model solves separate momentum equations
 * for each phase and supports studies of:
 * </p>
 * <ul>
 * <li>Countercurrent flow</li>
 * <li>Slug flow dynamics</li>
 * <li>Terrain-induced liquid accumulation</li>
 * <li>Transient pressure waves</li>
 * </ul>
 *
 * <h2>Conservation Equations</h2>
 * <ul>
 * <li><b>Gas Mass:</b> ∂/∂t(α_g·ρ_g·A) + ∂/∂x(α_g·ρ_g·v_g·A) = Γ_g</li>
 * <li><b>Liquid Mass:</b> ∂/∂t(α_L·ρ_L·A) + ∂/∂x(α_L·ρ_L·v_L·A) = Γ_L</li>
 * <li><b>Gas Momentum:</b> ∂/∂t(α_g·ρ_g·v_g·A) + ∂/∂x(α_g·ρ_g·v_g²·A + α_g·P·A) = S_g</li>
 * <li><b>Liquid Momentum:</b> ∂/∂t(α_L·ρ_L·v_L·A) + ∂/∂x(α_L·ρ_L·v_L²·A + α_L·P·A) = S_L</li>
 * <li><b>Mixture Energy:</b> (optional)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create two-phase fluid
 * SystemInterface fluid = new SystemSrkEos(300, 50);
 * fluid.addComponent("methane", 0.85);
 * fluid.addComponent("n-pentane", 0.15);
 * fluid.setMixingRule("classic");
 * fluid.setMultiPhaseCheck(true);
 *
 * // Create inlet stream
 * Stream inlet = new Stream("inlet", fluid);
 * inlet.setFlowRate(10, "kg/sec");
 * inlet.run();
 *
 * // Create two-fluid pipe
 * TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inlet);
 * pipe.setLength(5000); // 5 km
 * pipe.setDiameter(0.3); // 300 mm
 * pipe.setNumberOfSections(100);
 *
 * // Set terrain profile
 * double[] elevations = new double[100];
 * for (int i = 0; i < 100; i++) {
 *   elevations[i] = 50.0 * Math.sin(i * Math.PI / 50); // Undulating terrain
 * }
 * pipe.setElevationProfile(elevations);
 *
 * // Initialize steady state
 * pipe.run();
 *
 * // Transient simulation
 * UUID id = UUID.randomUUID();
 * for (int step = 0; step < 1000; step++) {
 *   pipe.runTransient(0.1, id); // 0.1 second steps
 * }
 *
 * // Get results
 * double[] pressures = pipe.getPressureProfile();
 * double[] holdups = pipe.getLiquidHoldupProfile();
 * double liquidInventory = pipe.getLiquidInventory("m3");
 * }</pre>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Bendiksen, K.H. et al. (1991) - The Dynamic Two-Fluid Model OLGA</li>
 * <li>Taitel, Y. and Dukler, A.E. (1976) - Flow regime transitions</li>
 * <li>Issa, R.I. and Kempf, M.H.W. (2003) - Simulation of slug flow</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see neqsim.process.equipment.pipeline.twophasepipe.TransientPipe
 * @see TwoFluidConservationEquations
 */
public class TwoFluidPipe extends Pipeline {

  private static final long serialVersionUID = 1001;
  private static final Logger logger = LogManager.getLogger(TwoFluidPipe.class);

  /** Numerical epsilon used only inside closure denominators; it is never a phase-state floor. */
  private static final double CLOSURE_DENOMINATOR_EPSILON = 1.0e-14;

  /** Smallest positive holdup used while solving a singular two-phase closure. */
  private static final double CLOSURE_SOLVER_HOLDUP_EPSILON = 1.0e-15;

  /**
   * No-slip fraction over which drift-flux distribution parameters are smoothly withdrawn near a pure-gas state.
   */
  private static final double DRIFT_FLUX_DEGENERACY_TRANSITION = 1.0e-3;

  /** Upper no-slip fraction for the trace-liquid asymptote of the stratified closure. */
  private static final double STRATIFIED_TRACE_LIQUID_TRANSITION = 1.0e-6;

  /** Bendiksen (1984) horizontal Taylor bubble drift coefficient. */
  private static final double SLUG_DRIFT_HORIZONTAL_COEFFICIENT = 0.54;

  /** Bendiksen (1984) vertical Taylor bubble drift coefficient. */
  private static final double SLUG_DRIFT_VERTICAL_COEFFICIENT = 0.35;

  /** Bound on the Taylor bubble film holdup as a fraction of the slug body it separates. */
  private static final double SLUG_FILM_HOLDUP_FRACTION_OF_BODY = 0.9;

  /**
   * Brotz falling-film coefficient used for the liquid film draining around a Taylor bubble.
   *
   * <p>
   * Value 9.916 as used by Taitel and Barnea (1990) in {@code v_film = -9.916 sqrt(g D (1 - sqrt(1 - H_film)))}.
   * </p>
   */
  private static final double SLUG_FALLING_FILM_COEFFICIENT = 9.916;

  /** Bisection iterations for the Taylor bubble film mass balance. */
  private static final int SLUG_FILM_SOLVER_ITERATIONS = 60;

  /** Whether a phase reversed at the transmissive outlet during the transient run. */
  private boolean transientOutletBackflowClamped = false;

  /** Whether the interfacial-pressure stabilizer is advanced implicitly by the time integrator. */
  private boolean implicitInterfacialPressureCoupling = true;

  /**
   * Whether pressure, phase mass fluxes, and phase momenta are corrected in the same transient step.
   *
   * <p>Off by default until the long-horizon liquid-rich and severe-slugging acceptance cases pass.
   */
  private boolean coupledPressureMomentumEnabled = false;

  /** Default closed-flow fluid-side heat-transfer coefficient in W/(m2 K). */
  private static final double DEFAULT_STAGNANT_INNER_HEAT_TRANSFER_COEFFICIENT = 50.0;

  // ============ Geometry ============

  /** Total pipe length (m). */
  private double length = 1000.0;

  /** Pipe inner diameter (m). */
  private double diameter = 0.2;

  /** Pipe wall roughness (m). */
  private double roughness = 4.6e-5;

  /** Number of computational cells. */
  private int numberOfSections = 50;

  /** Elevation profile at each section (m). */
  private double[] elevationProfile;

  // ============ Discretization ============

  /** Pipe sections with state. */
  private TwoFluidSection[] sections;

  /**
   * Minimum spatial step size across all sections (m). Used for CFL calculation. Equal to uniform dx when
   * sectionLengths is null.
   */
  private double dx;

  /**
   * Per-section lengths for non-uniform mesh (m). When null, all sections use uniform dx. Length must equal
   * numberOfSections and sum to total pipe length.
   */
  private double[] sectionLengths;

  // ============ Transient state ============

  /** Current simulation time (s). */
  private double simulationTime = 0;

  /** Maximum simulation time (s). */
  private double maxSimulationTime = 3600;

  /** CFL number for time stepping (0 &lt; CFL &lt; 1). */
  private double cflNumber = 0.5;

  // ============ Sub-models ============

  /** Conservation equations solver. */
  private TwoFluidConservationEquations equations;

  /** Time integrator. */
  private TimeIntegrator timeIntegrator;

  /** Flow regime detector. */
  private FlowRegimeDetector flowRegimeDetector;

  /** Liquid accumulation tracker. */
  private LiquidAccumulationTracker accumulationTracker;

  /** Slug tracker (simplified model). */
  private SlugTracker slugTracker;

  /** Detailed Lagrangian slug tracker. */
  private LagrangianSlugTracker lagrangianSlugTracker;

  /**
   * Slug tracking mode.
   */
  public enum SlugTrackingMode {
    /** Simplified slug unit model. */
    SIMPLIFIED,
    /** Detailed Lagrangian tracking. */
    LAGRANGIAN,
    /** No slug tracking. */
    DISABLED
  }

  /** Current slug tracking mode. */
  private SlugTrackingMode slugTrackingMode = SlugTrackingMode.LAGRANGIAN;

  // ============ Boundary conditions ============

  /** Boundary condition type. */
  public enum BoundaryCondition {
    /** Constant pressure. */
    CONSTANT_PRESSURE,
    /** Constant mass flow. */
    CONSTANT_FLOW,
    /** Connected to stream. */
    STREAM_CONNECTED,
    /** Closed (no flow - blocked/shut-in). */
    CLOSED,
    /**
     * Characteristic-based (Riemann invariant). Incoming characteristics carry boundary data while outgoing
     * characteristics are extrapolated from the interior. Reduces spurious wave reflections during rapid transients
     * (valve closures, slug arrivals). Based on Toro (2009).
     */
    CHARACTERISTIC
  }

  /** Inlet boundary condition type. */
  private BoundaryCondition inletBCType = BoundaryCondition.STREAM_CONNECTED;

  /** Outlet boundary condition type. */
  private BoundaryCondition outletBCType = BoundaryCondition.CONSTANT_PRESSURE;

  /** Outlet pressure (Pa). */
  private double outletPressure;

  /** Flag indicating if outlet pressure was explicitly set. */
  private boolean outletPressureSet = false;

  /** Inlet pressure (Pa) - used when inletBCType is CONSTANT_PRESSURE. */
  private double inletPressure;

  /** Flag indicating if inlet pressure was explicitly set. */
  private boolean inletPressureSet = false;

  /** Inlet mass flow (kg/s) - used when inletBCType is CONSTANT_FLOW. */
  private double inletMassFlow;

  /** Flag indicating if inlet mass flow was explicitly set. */
  private boolean inletMassFlowSet = false;

  // ============ Settings ============

  /** Include energy equation. */
  private boolean includeEnergyEquation = false;

  /** Include mass transfer (flash/condensation). */
  private boolean includeMassTransfer = false;

  /** Enable heat transfer from surroundings. */
  private boolean enableHeatTransfer = false;

  /** Surface temperature for heat transfer (K). */
  private double surfaceTemperature = 288.15;

  /** Overall or simple-model heat transfer coefficient (W/(m²·K)). */
  private double heatTransferCoefficient = 0.0;

  /** Fluid-side heat transfer coefficient used at zero local throughput (W/(m²·K)). */
  private double stagnantInnerHeatTransferCoefficient = DEFAULT_STAGNANT_INNER_HEAT_TRANSFER_COEFFICIENT;

  /** Heat transfer coefficient profile along pipe (W/(m²·K)). */
  private double[] heatTransferProfile = null;

  /** Surface temperature profile along pipe (K). */
  private double[] surfaceTemperatureProfile = null;

  /** Pipe wall thickness (m). */
  private double wallThickness = 0.02;

  /** Pipe wall density (kg/m³) - steel default. */
  private double wallDensity = 7850.0;

  /** Pipe wall specific heat capacity (J/(kg·K)) - steel default. */
  private double wallHeatCapacity = 500.0;

  /** Pipe wall temperature profile (K). */
  private double[] wallTemperatureProfile = null;

  /** Soil/burial thermal resistance (m²·K/W). */
  private double soilThermalResistance = 0.0;

  /** Direct electrical heating power delivered to the fluid (W/m). */
  private double directElectricalHeatingPowerPerMeter = 0.0;

  /** Multi-layer radial heat-transfer calculator and public configuration template. */
  private MultilayerThermalCalculator thermalCalculator = null;

  /** Per-cell temperatures for every stateful radial layer in the multi-layer model. */
  private double[][] multilayerLayerTemperatureProfiles = null;

  /** Enable multi-layer thermal model (vs simple U-value). */
  private boolean useMultilayerThermalModel = false;

  /** Enable Joule-Thomson effect. */
  private boolean enableJouleThomson = true;

  /** Hydrate formation temperature (K). */
  private double hydrateFormationTemperature = 0.0;

  /** Wax appearance temperature (K). */
  private double waxAppearanceTemperature = 0.0;

  // ============ Junction/Bend Loss Coefficients ============

  /**
   * K-factors for local losses (bends, valves, fittings) at specific positions. Key = position (m), Value = K-factor
   * (dimensionless loss coefficient).
   */
  private java.util.Map<Double, Double> localLossKFactors = new java.util.HashMap<>();

  /**
   * Total equivalent length of fittings (m). Used when individual positions are not specified. The equivalent length is
   * added to the total pipe length for friction calculation.
   */
  private double equivalentLengthFittings = 0.0;

  /**
   * Number of 90-degree bends. K-factor = 0.3-0.5 per bend typically.
   */
  private int numberOf90DegreeBends = 0;

  /**
   * Number of 45-degree bends. K-factor = 0.15-0.25 per bend typically.
   */
  private int numberOf45DegreeBends = 0;

  /**
   * Inlet loss coefficient. K-factor for inlet effects (sharp-edge inlet K ~ 0.5, bell-mouth K ~ 0.05).
   */
  private double inletLossCoefficient = 0.0;

  /**
   * Outlet loss coefficient. K-factor for outlet effects (sudden expansion K ~ 1.0).
   */
  private double outletLossCoefficient = 0.0;

  /** Sections flagged for hydrate risk. */
  private boolean[] hydrateRiskSections = null;

  /** Sections flagged for wax risk. */
  private boolean[] waxRiskSections = null;

  /**
   * Insulation type presets with typical U-values.
   */
  public enum InsulationType {
    /** No insulation - bare steel in seawater. */
    NONE(150.0),
    /** Uninsulated subsea - typical bare pipe. */
    UNINSULATED_SUBSEA(25.0),
    /** Standard PU foam insulation. */
    PU_FOAM(10.0),
    /** Multi-layer insulation. */
    MULTI_LAYER(5.0),
    /** Pipe-in-pipe insulation. */
    PIPE_IN_PIPE(2.0),
    /** Vacuum insulated tubing. */
    VIT(0.5),
    /** Buried onshore pipeline. */
    BURIED_ONSHORE(3.0),
    /** Exposed onshore. */
    EXPOSED_ONSHORE(75.0);

    private final double uValue;

    InsulationType(double uValue) {
      this.uValue = uValue;
    }

    /**
     * Get the typical overall heat transfer coefficient.
     *
     * @return U-value in W/(m²·K)
     */
    public double getUValue() {
      return uValue;
    }
  }

  /** Current insulation type. */
  private InsulationType insulationType = InsulationType.NONE;

  /** Enable slug tracking. */
  private boolean enableSlugTracking = true;

  /** Outlet slug statistics. */
  private int outletSlugCount = 0;
  private double totalSlugVolumeAtOutlet = 0;
  private double lastSlugArrivalTime = 0;
  private double maxSlugLengthAtOutlet = 0;
  private double maxSlugVolumeAtOutlet = 0;

  /** Track which slugs have already been counted at outlet (by slug ID). */
  private java.util.Set<Integer> countedOutletSlugs = new java.util.HashSet<>();

  // ============ Literature-inspired model parameters ============

  /**
   * Selects the level of detail used for holdup and flow-regime closures.
   *
   * <p>
   * The enum name is retained for API compatibility. These modes are NeqSim implementations informed by published
   * multiphase-flow literature; they do not claim numerical equivalence with a commercial simulator.
   * </p>
   */
  public enum OLGAModelType {
    /**
     * Flow-regime-specific momentum, film, and slug closures.
     */
    FULL,
    /**
     * Reduced empirical closures for lower computational cost.
     */
    SIMPLIFIED,
    /**
     * Original NeqSim drift-flux closure for backward compatibility.
     */
    DRIFT_FLUX
  }

  /** Current literature-inspired closure set. Default is FULL. */
  private OLGAModelType olgaModelType = OLGAModelType.FULL;

  /**
   * Optional absolute liquid-holdup floor for explicitly configured fixed-floor mode.
   *
   * <p>
   * This value is applied only when minimum-slip enforcement is enabled and adaptive-only mode is disabled. It is not a
   * numerical positivity safeguard and is never applied to an absent phase.
   * </p>
   *
   * <p>
   * The actual minimum applied is the maximum of:
   * </p>
   * <ul>
   * <li>This base value (default 0.1%) in fixed-floor mode</li>
   * <li>A multiple of the no-slip holdup (lambdaL * minimumSlipFactor)</li>
   * </ul>
   * <p>
   * This ensures the minimum is physically reasonable for both lean gas (low liquid loading) and rich gas condensate
   * (high liquid loading) systems.
   * </p>
   *
   * <p>
   * The default is retained for backward-compatible fixed-floor studies; users are responsible for selecting a film
   * value supported by their fluid, pipe-wall wetting, and flow-regime data.
   * </p>
   */
  private double minimumLiquidHoldup = 0.001;

  /**
   * Slip factor applied to no-slip holdup to calculate adaptive minimum.
   *
   * <p>
   * The bound states that the gas moves at least this many times faster than the liquid; see
   * {@link #minimumSlipHoldup(double, double)} for the hold-up it implies. For gas-dominant systems typical slip ratios
   * range from 1.5 to 3.0. The default of 2.0 reduces to twice the no-slip fraction at low liquid loading, which
   * accounts for liquid accumulation due to slip while keeping the minimum from being unrealistically high for lean gas
   * systems.
   * </p>
   */
  private double minimumSlipFactor = 2.0;

  /**
   * Use only adaptive (correlation-based) minimum, without absolute floor.
   *
   * <p>
   * When true, the minimum holdup is calculated purely from flow correlations (Beggs-Brill type) without enforcing the
   * absolute minimumLiquidHoldup floor. This allows the model to predict very low holdups for lean gas systems where
   * the physical holdup may be below 1%.
   * </p>
   * <p>
   * Default is true for better handling of lean gas systems.
   * </p>
   */
  private boolean useAdaptiveMinimumOnly = true;

  /**
   * Enable the minimum-slip closure constraint.
   *
   * <p>
   * When enabled (default), applies a correlation-based lower bound that vanishes with the no-slip liquid fraction.
   * When disabled, no minimum-slip bound is applied. Neither setting creates mass for an absent phase.
   * </p>
   */
  private boolean enforceMinimumSlip = true;

  // ============ Annular Film Closure Parameters ============

  /**
   * Minimum film thickness for annular flow (m).
   *
   * <p>
   * A nonzero film floor is applied only in explicit fixed-floor mode: minimum-slip enforcement enabled, adaptive-only
   * mode disabled, and a positive {@link #minimumLiquidHoldup}. The stored default is 0.1 mm. It is a user-selectable
   * wetting-film assumption, not a numerical phase-presence threshold.
   * </p>
   */
  private double minimumFilmThickness = 0.0001; // 0.1 mm

  /**
   * Entrainment fraction in annular flow.
   *
   * <p>
   * Fraction of liquid entrained as droplets in the gas core. Affects the distribution between film flow and droplet
   * flow in annular regime. The implementation uses an Ishii-Mishima correlation.
   * </p>
   */
  private double annularEntrainmentFraction = 0.0;

  /**
   * Enable the literature-inspired annular film closure.
   *
   * <p>
   * When enabled, the closure accounts for film momentum and liquid entrainment in the gas core. A configured minimum
   * film is active only in explicit fixed-floor mode.
   * </p>
   */
  private boolean enableAnnularFilmModel = true;

  // ============ Terrain Tracking Parameters ============

  /**
   * Enable empirical NeqSim terrain tracking.
   *
   * <p>
   * When enabled, the empirical NeqSim closure identifies terrain extrema, tracks liquid accumulation in valleys, and
   * initiates terrain slugs when configured thresholds are exceeded. It is not an implementation of a proprietary
   * commercial-simulator algorithm.
   * </p>
   */
  private boolean enableTerrainTracking = true;

  /**
   * Critical holdup for terrain-induced slug initiation.
   *
   * <p>
   * When liquid holdup in a low point exceeds this value, a terrain-induced slug is initiated. The default 0.6 is an
   * empirical NeqSim setting, not a published commercial-simulator default.
   * </p>
   */
  private double terrainSlugCriticalHoldup = 0.6;

  /**
   * Liquid fallback coefficient for uphill sections.
   *
   * <p>
   * Controls how much liquid falls back in uphill sections when gas velocity is insufficient to carry liquid upward.
   * Higher values mean more liquid accumulation. The default 0.3 is an empirical NeqSim setting.
   * </p>
   */
  private double liquidFallbackCoefficient = 0.3;

  /**
   * Enable empirical terrain-slug and riser-base liquid-fallback closures.
   *
   * <p>
   * The serialized field name is retained for compatibility. These local closures are separate from the explicit
   * flowline-riser system diagnostic.
   * </p>
   */
  private boolean enableSevereSlugModel = true;

  // ============ Historical Alternate Flow Regime Parameters ============

  /**
   * Use the historical NeqSim alternate flow-regime closure instead of Taitel-Dukler.
   *
   * <p>
   * The serialized field and public method names are retained for compatibility. The closure is literature-inspired;
   * the name does not establish equivalence with or reproduce a proprietary commercial flow-regime map.
   * </p>
   */
  private boolean useOLGAFlowRegimeMap = true;

  /**
   * Flow regime transition hysteresis factor.
   *
   * <p>
   * NeqSim applies this hysteresis to prevent rapid switching between flow regimes. A value of 0.1 means a 10% band
   * around transition boundaries.
   * </p>
   */
  private double flowRegimeHysteresis = 0.1;

  /** Update thermodynamics every N steps. */
  private int thermodynamicUpdateInterval = 10;

  // ============ Steady-State Solver Configuration ============

  /**
   * Under-relaxation factor for steady-state pressure and holdup updates.
   *
   * <p>
   * Values between 0 and 1. Lower values improve stability at the cost of slower convergence. Default 0.5; the solver
   * ramps from 0.3 up to this value over the first iterations.
   * </p>
   */
  private double ssUnderRelaxation = 0.5;

  /**
   * Flash calculation interval during steady-state iterations.
   *
   * <p>
   * A TP-flash is performed for every section only every {@code ssFlashInterval} iterations. Reducing flash frequency
   * from every iteration to every 5th cuts the dominant cost of the steady-state solver without significantly affecting
   * accuracy.
   * </p>
   */
  private int ssFlashInterval = 3;

  /**
   * Maximum wall-clock time for the steady-state solver (seconds).
   *
   * <p>
   * If the solver has not converged within this time, it stops with the best available profile and logs a warning.
   * Prevents truly infinite run times for difficult configurations.
   * </p>
   *
   * <p>
   * A long transmission line needs a few hundred sweeps to settle its pressure profile against the updated section
   * densities; a 74 km line at 320 sections takes about 50 s. The budget has to leave room for that, otherwise the
   * guard silently truncates the solve and {@link #isSteadyStateConverged()} reports false on an otherwise ordinary
   * case.
   * </p>
   */
  private double ssMaxWallClockTime = 300.0;

  /**
   * Use per-phase wall shear for the friction gradient where the phases are separated.
   *
   * <p>
   * Off by default. It is the more consistent description and cuts the three-phase pressure-drop error from +190% to
   * +17%, but because it uses the real liquid-layer geometry it also amplifies the outstanding hold-up deficit, which
   * the mixture correlation masks. On the lean gas-condensate reference line it moves the pressure drop from about +5%
   * to about +16% and drives the highest rate onto the pressure floor. It becomes the right default once hold-up is
   * corrected.
   * </p>
   */
  private boolean useSeparatedFrictionModel = true;

  /**
   * Fraction of the inlet pressure the line must lose before the density coupling is taken to matter for steady-state
   * convergence. Below this the fluid density is uniform to within about the same fraction, so the pressure profile
   * cannot be materially wrong for want of a thermodynamic update.
   */
  private static final double SS_DENSITY_COUPLING_PRESSURE_FRACTION = 0.01;

  /**
   * Upper bound on the oil-over-water slip ratio {@code v_oil / v_water} used to close the three-phase holdup split.
   * Beyond roughly this ratio the layers no longer behave as a co-current stratified pair.
   */
  private static final double MAX_OIL_WATER_SLIP_RATIO = 4.0;

  /**
   * Plateau value of {@code S - 1} for the oil-over-water slip ratio in stratified liquid flow, where S is
   * {@code v_oil / v_water}. Water settles towards the pipe bottom and lags the oil layer, so the in-situ water
   * fraction sits above the transported one until the liquid disperses.
   */
  private static final double OIL_WATER_SLIP_PLATEAU = 1.75;

  /**
   * Liquid Froude number above which oil and water are dispersed and travel together, so the slip ratio returns to one.
   */
  private static final double OIL_WATER_SLIP_CRITICAL_FROUDE = 3.0;

  /**
   * Set when the last steady-state initialization was stopped by the wall-clock guard.
   *
   * <p>
   * Wall-clock truncation makes the initial condition depend on machine speed, so reproducible studies should check
   * this flag instead of silently accepting a machine-dependent starting profile.
   * </p>
   */
  private boolean ssWallClockLimited = false;

  /**
   * Lower bound applied to every section pressure during the steady-state march (Pa).
   *
   * <p>
   * The clamp keeps the marching solver numerically alive when the line has no deliverability, but a profile resting on
   * it is not a solution of the momentum balance. {@link #ssPressureFloorLimited} records that so it cannot be mistaken
   * for one.
   * </p>
   */
  private static final double MIN_SECTION_PRESSURE_PA = 1.0e5;

  /** Set when the converged steady-state profile rests on {@link #MIN_SECTION_PRESSURE_PA}. */
  private boolean ssPressureFloorLimited = false;

  /** Iterations used by the last steady-state refinement loop. */
  private int ssIterationsUsed = 0;

  /**
   * User-specified iteration limit for the steady-state refinement loop.
   *
   * <p>
   * Zero or negative means the limit is derived from the section count.
   * </p>
   */
  private int ssMaxIterations = 0;

  /** True when the last steady-state refinement loop met its tolerance. */
  private boolean ssConverged = false;

  /** Current step count. */
  private int currentStep = 0;

  /**
   * Enable adaptive timestepping that detects instability and reduces dt automatically.
   *
   * <p>
   * When enabled, the solver: (1) recomputes CFL-stable dt each sub-step based on current velocities, (2) detects
   * instability (diverging pressure/holdup) and halves dt with retry, (3) grows dt back toward the CFL limit when
   * stable.
   * </p>
   */
  private boolean enableAdaptiveTimestepping = false;

  /**
   * Maximum pressure allowed before adaptive dt reduction triggers (Pa). Default 1000 bara.
   */
  private double adaptiveMaxPressure = 1000.0e5;

  /**
   * Maximum allowed pressure change ratio per sub-step before triggering dt reduction. Default 0.2 (200% change per
   * sub-step — catches genuine blow-up, not normal transient dynamics).
   */
  private double adaptiveMaxPressureChangeRatio = 2.0;

  /**
   * Current adaptive dt multiplier (starts at 1.0, reduced on instability, slowly grows back).
   */
  private transient double adaptiveDtFactor = 1.0;

  /**
   * Minimum adaptive dt factor to prevent timestep from becoming infinitesimally small.
   */
  private static final double MIN_ADAPTIVE_DT_FACTOR = 0.01;

  /**
   * Growth rate for adaptive dt factor per stable sub-step (multiplicative). Controls how quickly the timestep recovers
   * after a reduction.
   */
  private static final double ADAPTIVE_DT_GROWTH = 1.05;

  /** Flag indicating transient mode (inlet P is free, not fixed from stream). */
  private boolean isTransientMode = false;

  /** Discrete mass balance from the most recent transient call. */
  private TwoFluidMassBalanceReport lastMassBalanceReport = null;

  /** Discrete sensible/latent thermal balance from the most recent thermal transient call. */
  private TwoFluidThermalEnergyBalanceReport lastThermalEnergyBalanceReport = null;

  /** Enable opt-in component inventories and transport in every hydrodynamic phase and cell. */
  private boolean componentTransportEnabled = false;

  /** Fail-loud relative tolerance for component balance, boundedness, and phase-mass synchronization. */
  private double componentConservationTolerance = 1.0e-8;

  /** Retain one immutable component report per accepted outer transient call. */
  private boolean storeComponentConservationHistory = false;

  /** Distributed component state, initialized after the steady-state hydrodynamic solve. */
  private TwoFluidComponentTransport componentTransport = null;

  /** Component diagnostics from the most recent transient call. */
  private TwoFluidComponentConservationReport lastComponentConservationReport = null;

  /** Accepted component reports retained since the latest steady initialization. */
  private final List<TwoFluidComponentConservationReport> componentConservationReports = new ArrayList<>();

  /** Simulation times aligned with {@link #componentConservationReports}. */
  private final List<Double> componentConservationTimes = new ArrayList<>();

  // ============ Results storage ============

  /** Pressure profile (Pa). */
  private double[] pressureProfile;

  /** Temperature profile (K). */
  private double[] temperatureProfile;

  /** Liquid holdup profile. */
  private double[] liquidHoldupProfile;

  /** Gas velocity profile (m/s). */
  private double[] gasVelocityProfile;

  /** Liquid velocity profile (m/s). */
  private double[] liquidVelocityProfile;

  // ============ Thermodynamic reference ============

  /** Reference fluid for flash calculations. */
  private SystemInterface referenceFluid;

  /**
   * Constructor with name only.
   *
   * @param name Equipment name
   */
  public TwoFluidPipe(String name) {
    super(name);
    initSubModels();
  }

  /**
   * Constructor with inlet stream.
   *
   * @param name Equipment name
   * @param inStream Inlet stream
   */
  public TwoFluidPipe(String name, StreamInterface inStream) {
    super(name, inStream);
    initSubModels();
  }

  /**
   * Initialize sub-models.
   */
  private void initSubModels() {
    equations = new TwoFluidConservationEquations();
    timeIntegrator = new TimeIntegrator(TimeIntegrator.Method.RK4);
    flowRegimeDetector = new FlowRegimeDetector();
    accumulationTracker = new LiquidAccumulationTracker();
    slugTracker = new SlugTracker();
    lagrangianSlugTracker = new LagrangianSlugTracker();

    timeIntegrator.setCflNumber(cflNumber);

    // Reset outlet slug statistics
    outletSlugCount = 0;
    totalSlugVolumeAtOutlet = 0;
    lastSlugArrivalTime = 0;
    maxSlugLengthAtOutlet = 0;
    maxSlugVolumeAtOutlet = 0;
    countedOutletSlugs.clear();
  }

  /**
   * Initialize pipe sections with inlet conditions.
   */
  private void initializeSections() {
    if (sectionLengths != null) {
      // Non-uniform mesh: use per-section lengths
      numberOfSections = sectionLengths.length;
      dx = sectionLengths[0];
      for (double sl : sectionLengths) {
        dx = Math.min(dx, sl);
      }
    } else {
      // Uniform mesh
      dx = length / numberOfSections;
    }
    sections = new TwoFluidSection[numberOfSections];

    // Reset slug tracking state on re-initialization
    outletSlugCount = 0;
    totalSlugVolumeAtOutlet = 0;
    lastSlugArrivalTime = 0;
    maxSlugLengthAtOutlet = 0;
    maxSlugVolumeAtOutlet = 0;
    countedOutletSlugs.clear();
    simulationTime = 0;

    // Get inlet properties
    SystemInterface inletFluid = getInletStream().getFluid();
    double P_in = inletFluid.getPressure("Pa");
    double T_in = inletFluid.getTemperature("K");

    // Store reference fluid for flash calculations
    referenceFluid = inletFluid.clone();
    equations.setThermodynamicCoupling(new ThermodynamicCoupling(referenceFluid));

    // Calculate inlet phase properties - initialize with defaults
    double rhoG = 1.0, rhoL = 800.0, muG = 1e-5, muL = 1e-3;
    double cG = 340, cL = 1200, hG = 0, hL = 0, sigma = 0.02;
    double alphaL = 0.0, alphaG = 1.0; // Default to single-phase gas

    // Three-phase specific properties
    double rhoOil = 800.0, rhoWater = 1000.0;
    double muOil = 1e-3, muWater = 1e-3;
    double inletWaterCut = 0.0;
    double inletOilFraction = 1.0;
    boolean isThreePhase = false;
    boolean hasOil = false;
    boolean hasWater = false;

    // Determine phase fractions based on number of phases
    int numPhases = inletFluid.getNumberOfPhases();

    // Determine which phases are present (check for all cases)
    boolean hasGas = inletFluid.hasPhaseType("gas");
    hasOil = inletFluid.hasPhaseType("oil");
    hasWater = inletFluid.hasPhaseType("aqueous");

    if (numPhases == 1) {
      // Single-phase flow
      if (hasGas) {
        // Pure gas
        alphaG = 1.0;
        alphaL = 0.0;
        rhoG = inletFluid.getPhase("gas").getDensity("kg/m3");
        muG = inletFluid.getPhase("gas").getViscosity("kg/msec");
        cG = inletFluid.getPhase("gas").getSoundSpeed();
        hG = inletFluid.getPhase("gas").getEnthalpy("J/kg");
        // Set liquid properties to dummy values (won't be used)
        rhoL = rhoG;
        muL = muG;
        logger.info("Single-phase gas flow");
      } else if (hasOil) {
        // Pure oil
        alphaG = 0.0;
        alphaL = 1.0;
        rhoL = inletFluid.getPhase("oil").getDensity("kg/m3");
        muL = inletFluid.getPhase("oil").getViscosity("kg/msec");
        cL = inletFluid.getPhase("oil").getSoundSpeed();
        hL = inletFluid.getPhase("oil").getEnthalpy("J/kg");
        rhoOil = rhoL;
        muOil = muL;
        // Set gas properties to dummy values
        rhoG = rhoL;
        muG = muL;
        logger.info("Single-phase oil flow");
      } else if (hasWater) {
        // Pure water
        alphaG = 0.0;
        alphaL = 1.0;
        rhoL = inletFluid.getPhase("aqueous").getDensity("kg/m3");
        muL = inletFluid.getPhase("aqueous").getViscosity("kg/msec");
        cL = inletFluid.getPhase("aqueous").getSoundSpeed();
        hL = inletFluid.getPhase("aqueous").getEnthalpy("J/kg");
        rhoWater = rhoL;
        muWater = muL;
        // Set gas properties to dummy values
        rhoG = rhoL;
        muG = muL;
        logger.info("Single-phase water flow");
      }
    } else {
      // Two-phase or multi-phase flow
      if (hasGas) {
        rhoG = inletFluid.getPhase("gas").getDensity("kg/m3");
        muG = inletFluid.getPhase("gas").getViscosity("kg/msec");
        cG = inletFluid.getPhase("gas").getSoundSpeed();
        hG = inletFluid.getPhase("gas").getEnthalpy("J/kg");
      }

      // Handle all liquid-containing phase combinations

      if (hasOil && hasWater) {
        // Three-phase flow: combine oil and water as effective liquid
        isThreePhase = true;
        rhoOil = inletFluid.getPhase("oil").getDensity("kg/m3");
        rhoWater = inletFluid.getPhase("aqueous").getDensity("kg/m3");
        muOil = inletFluid.getPhase("oil").getViscosity("kg/msec");
        muWater = inletFluid.getPhase("aqueous").getViscosity("kg/msec");
        double volOil = phaseVolumetricFlow(inletFluid, "oil");
        double volWater = phaseVolumetricFlow(inletFluid, "aqueous");
        double volLiquid = volOil + volWater;

        // Water cut = water volume / total liquid volume
        inletWaterCut = volWater / volLiquid;
        inletOilFraction = 1.0 - inletWaterCut;

        // Volume-weighted average liquid density
        rhoL = inletOilFraction * rhoOil + inletWaterCut * rhoWater;

        // Effective viscosity using Brinkman equation for emulsions
        if (inletOilFraction > 0.5) {
          // Oil continuous phase
          muL = muOil * Math.pow(1.0 - inletWaterCut, -2.5);
        } else {
          // Water continuous phase
          muL = muWater * Math.pow(1.0 - inletOilFraction, -2.5);
        }

        // Use oil phase for other properties (approximation)
        cL = inletFluid.getPhase("oil").getSoundSpeed();
        hL = (inletOilFraction * inletFluid.getPhase("oil").getEnthalpy("J/kg")
            + inletWaterCut * inletFluid.getPhase("aqueous").getEnthalpy("J/kg"));

        if (inletFluid.hasPhaseType("gas")) {
          // Initialize interfacial properties before getting surface tension
          inletFluid.getInterphaseProperties().init(inletFluid);
          try {
            double sigmaCalc = inletFluid.getInterphaseProperties().getSurfaceTension(inletFluid.getPhaseIndex("gas"),
                inletFluid.getPhaseIndex("oil"));
            // Only use calculated value if it's reasonable (> 1e-6 N/m)
            if (sigmaCalc > 1e-6) {
              sigma = sigmaCalc;
            } else {
              // Default gas-oil IFT: ~20 mN/m (typical hydrocarbon system)
              sigma = 0.020;
              logger.warn(
                  "Interfacial tension calculation returned invalid value ({} N/m). Using default gas-oil IFT: {} N/m",
                  sigmaCalc, sigma);
            }
          } catch (Exception e) {
            // Default gas-oil IFT: ~20 mN/m
            sigma = 0.020;
            logger.warn("Interfacial tension calculation failed. Using default gas-oil IFT: {} N/m. Error: {}", sigma,
                e.getMessage());
          }
        }

        logger.info("Three-phase flow detected: water cut = {}%, oil fraction = {}%", inletWaterCut * 100,
            inletOilFraction * 100);

      } else if (hasOil || hasWater) {
        // Two-phase with single liquid type
        String liqPhase = hasOil ? "oil" : "aqueous";
        rhoL = inletFluid.getPhase(liqPhase).getDensity("kg/m3");
        muL = inletFluid.getPhase(liqPhase).getViscosity("kg/msec");
        cL = inletFluid.getPhase(liqPhase).getSoundSpeed();
        hL = inletFluid.getPhase(liqPhase).getEnthalpy("J/kg");
        if (inletFluid.hasPhaseType("gas")) {
          // Initialize interfacial properties before getting surface tension
          inletFluid.getInterphaseProperties().init(inletFluid);
          try {
            double sigmaCalc = inletFluid.getInterphaseProperties().getSurfaceTension(inletFluid.getPhaseIndex("gas"),
                inletFluid.getPhaseIndex(liqPhase));
            // Only use calculated value if it's reasonable (> 1e-6 N/m)
            if (sigmaCalc > 1e-6) {
              sigma = sigmaCalc;
            } else {
              // Use appropriate default based on liquid type
              // Gas-water: ~72 mN/m, Gas-oil: ~20 mN/m
              sigma = "aqueous".equals(liqPhase) ? 0.072 : 0.020;
              logger.warn(
                  "Interfacial tension calculation returned invalid value ({} N/m). Using default gas-{} IFT: {} N/m",
                  sigmaCalc, liqPhase, sigma);
            }
          } catch (Exception e) {
            // Use appropriate default based on liquid type
            sigma = "aqueous".equals(liqPhase) ? 0.072 : 0.020;
            logger.warn("Interfacial tension calculation failed. Using default gas-{} IFT: {} N/m. Error: {}", liqPhase,
                sigma, e.getMessage());
          }
        }
      }

      // Calculate holdup from volumetric phase fractions
      if (hasGas) {
        double volGas = phaseVolumetricFlow(inletFluid, "gas");
        double volTotal = volGas + phaseVolumetricFlow(inletFluid, "oil") + phaseVolumetricFlow(inletFluid, "aqueous");
        alphaG = volGas / volTotal;
        alphaL = 1.0 - alphaG;
      } else if (hasOil && hasWater) {
        // Oil-water flow (no gas) - treat as two-phase liquid-liquid flow
        // alphaG represents oil (lighter liquid), alphaL represents water (heavier)
        double volOil = phaseVolumetricFlow(inletFluid, "oil");
        double volWater = phaseVolumetricFlow(inletFluid, "aqueous");
        double volTotal = volOil + volWater;
        // Use gas holdup as oil fraction, liquid holdup as water fraction for oil-water
        alphaG = 0.0; // No gas
        alphaL = 1.0; // All liquid
        // Track oil-water split internally
        inletWaterCut = volWater / volTotal;
        inletOilFraction = 1.0 - inletWaterCut;
        isThreePhase = true; // Use three-fluid tracking even without gas
        logger.info("Oil-water flow (no gas): water cut = {}%", inletWaterCut * 100);
      }
    }

    // Create sections
    double area = Math.PI * diameter * diameter / 4.0;
    double massFlow = getInletStream().getFlowRate("kg/sec");
    double rhoMixInit = alphaG * rhoG + alphaL * rhoL;
    double vMix = massFlow / (area * Math.max(rhoMixInit, 1.0));

    // Physics-based initial pressure estimate using Darcy-Weisbach
    double muMixInit = alphaG * muG + alphaL * muL;
    double fInit = calcDarcyFrictionFactor(rhoMixInit, Math.abs(vMix), diameter, muMixInit);
    double dPdxInit = fInit * rhoMixInit * vMix * Math.abs(vMix) / (2.0 * diameter);
    // Add average gravity component from elevation profile
    if (elevationProfile != null && elevationProfile.length > 1) {
      double totalElevChange = elevationProfile[elevationProfile.length - 1] - elevationProfile[0];
      double avgSinTheta = totalElevChange / length;
      dPdxInit += rhoMixInit * 9.81 * avgSinTheta;
    }
    double totalDpEstimate = Math.max(dPdxInit, 0) * length;
    // Clamp to [1%, 50%] of inlet pressure for stability
    totalDpEstimate = Math.max(totalDpEstimate, P_in * 0.01);
    totalDpEstimate = Math.min(totalDpEstimate, P_in * 0.50);

    double previousInclination = 0.0;
    for (int i = 0; i < numberOfSections; i++) {
      double secDx = (sectionLengths != null) ? sectionLengths[i] : dx;
      // Cumulative position to section midpoint
      double position = 0;
      if (sectionLengths != null) {
        for (int k = 0; k < i; k++) {
          position += sectionLengths[k];
        }
        position += secDx * 0.5;
      } else {
        position = (i + 0.5) * dx;
      }
      double elevation = (elevationProfile != null && i < elevationProfile.length) ? elevationProfile[i] : 0.0;

      // Inclination from the elevation profile. secDx is the cell length along the pipe axis - it is
      // what the finite-volume fluxes use and what sums to the pipe length - so the elevation change
      // across the cell is its vertical component and the angle is asin(dz/secDx), not atan2(dz,
      // secDx). atan2 would treat secDx as a horizontal run and return 45 degrees for a vertical
      // cell, leaving a riser with sin(45) = 71% of its hydrostatic head.
      double inclination = previousInclination;
      if (elevationProfile != null && i < elevationProfile.length - 1 && secDx > 0.0) {
        double verticalRise = elevationProfile[i + 1] - elevation;
        inclination = Math.asin(Math.max(-1.0, Math.min(1.0, verticalRise / secDx)));
      }
      previousInclination = inclination;

      TwoFluidSection sec = new TwoFluidSection(position, secDx, diameter, inclination);
      sec.setElevation(elevation);
      sec.setRoughness(roughness);

      // Initialize with inlet conditions (Darcy-based pressure drop estimate)
      double posFrac = position / length;
      double P = P_in - posFrac * totalDpEstimate;
      sec.setPressure(P);
      sec.setTemperature(T_in);

      // Phase properties
      sec.setGasDensity(rhoG);
      sec.setLiquidDensity(rhoL);
      sec.setGasViscosity(muG);
      sec.setLiquidViscosity(muL);
      sec.setGasSoundSpeed(cG);
      sec.setLiquidSoundSpeed(cL);
      sec.setGasEnthalpy(hG);
      sec.setLiquidEnthalpy(hL);
      sec.setSurfaceTension(sigma);

      // Holdup and velocities
      sec.setGasHoldup(alphaG);
      sec.setLiquidHoldup(alphaL);
      sec.setGasVelocity(vMix);
      sec.setLiquidVelocity(vMix * 0.8); // Slip

      // Three-phase specific initialization
      if (isThreePhase) {
        sec.setOilDensity(rhoOil);
        sec.setWaterDensity(rhoWater);
        sec.setOilViscosity(muOil);
        sec.setWaterViscosity(muWater);
        sec.setInputWaterVolumeFraction(inletWaterCut);
        sec.setWaterCut(inletWaterCut);
        sec.setOilFractionInLiquid(inletOilFraction);

        // Initialize water and oil holdups based on inlet water cut
        double alphaW = alphaL * inletWaterCut;
        double alphaO = alphaL * inletOilFraction;
        sec.setWaterHoldup(alphaW);
        sec.setOilHoldup(alphaO);

        // Initialize velocities (assume same as liquid initially, will adjust in steady-state)
        sec.setWaterVelocity(sec.getLiquidVelocity());
        sec.setOilVelocity(sec.getLiquidVelocity());

        // Update water/oil conservative variables
        sec.updateWaterOilConservativeVariables();
      } else if (hasWater && !hasOil) {
        // Two-phase gas + aqueous (no oil) - all liquid is water
        sec.setWaterDensity(rhoL);
        sec.setWaterViscosity(muL);
        sec.setOilDensity(rhoL); // Dummy value, no oil present
        sec.setOilViscosity(muL);
        sec.setInputWaterVolumeFraction(1.0);
        sec.setWaterCut(1.0);
        sec.setOilFractionInLiquid(0.0);

        // All liquid holdup is water
        sec.setWaterHoldup(alphaL);
        sec.setOilHoldup(0.0);
        sec.setWaterVelocity(sec.getLiquidVelocity());
        sec.setOilVelocity(0.0);
        sec.updateWaterOilConservativeVariables();
      } else if (hasOil && !hasWater) {
        // Two-phase gas + oil (no water) OR single-phase oil - all liquid is oil
        sec.setOilDensity(rhoL);
        sec.setOilViscosity(muL);
        sec.setWaterDensity(1000.0); // Dummy value, no water present
        sec.setWaterViscosity(1e-3);
        sec.setInputWaterVolumeFraction(0.0);
        sec.setWaterCut(0.0);
        sec.setOilFractionInLiquid(1.0);

        // All liquid holdup is oil
        sec.setOilHoldup(alphaL);
        sec.setWaterHoldup(0.0);
        sec.setOilVelocity(sec.getLiquidVelocity());
        sec.setWaterVelocity(0.0);
        sec.updateWaterOilConservativeVariables();
      } else if (!hasOil && !hasWater && !hasGas) {
        // No phases detected - this shouldn't happen, log warning
        logger.warn("No phases detected in inlet fluid - using default properties");
      }

      // Initialize derived quantities
      sec.updateDerivedQuantities();
      sec.updateConservativeVariables();
      sec.updateStratifiedGeometry();

      sections[i] = sec;
    }

    // Set outlet pressure if not already set
    if (!outletPressureSet) {
      outletPressure = sections[numberOfSections - 1].getPressure();
    }

    // Initialize accumulation tracker
    accumulationTracker.identifyAccumulationZones(sections);

    logger.info("TwoFluidPipe initialized: {} sections, dx_min={}m{}", numberOfSections, dx,
        sectionLengths != null ? " (non-uniform mesh)" : "");
  }

  /**
   * Run steady-state initialization.
   *
   * <p>
   * Uses a two-phase approach for robust convergence:
   * </p>
   * <ul>
   * <li><b>Phase 1 — Forward march:</b> Single sweep from inlet to outlet computing pressure and holdup
   * section-by-section without flash calculations. Provides a physically consistent initial profile.</li>
   * <li><b>Phase 2 — Iterative refinement:</b> Under-relaxed fixed-point iteration with sparse flash updates (every
   * {@code ssFlashInterval} iterations) to account for condensation effects. Includes a wall-clock time guard to
   * prevent infinite run times.</li>
   * <li><b>Transient handoff:</b> Converts the final primitive profiles to conservative phase mass, momentum, and
   * energy once, so the first transient step starts from the reported steady state.</li>
   * </ul>
   */
  private void runSteadyState() {
    // The refinement loop is an under-relaxed fixed-point sweep, so information travels at
    // roughly one section per iteration. A fixed budget therefore silently fails on long,
    // finely-discretised lines. Scale the default with the mesh unless the user set a limit.
    int maxIter = ssMaxIterations > 0 ? ssMaxIterations : Math.max(100, 20 * numberOfSections);
    double tolerance = 1e-4;
    long startWallClock = System.currentTimeMillis();
    ssWallClockLimited = false;
    ssConverged = false;
    ssPressureFloorLimited = false;
    ssIterationsUsed = 0;
    transientOutletBackflowClamped = false;
    equations.clearOutletBackflowClamped();

    // Get total mass flow rate (conserved)
    double massFlow = getInletStream().getFlowRate("kg/sec");
    double area = Math.PI * diameter * diameter / 4.0;

    // Get inlet pressure - this is a boundary condition
    double P_inlet = getInletStream().getFluid().getPressure("Pa");

    // Fix inlet section pressure to boundary condition
    sections[0].setPressure(P_inlet);

    // Get inlet phase fractions from first section (initial estimate)
    double inletAlphaL = sections[0].getLiquidHoldup();
    double inletAlphaG = sections[0].getGasHoldup();
    double inletRhoG = sections[0].getGasDensity();
    double inletRhoL = sections[0].getLiquidDensity();

    // Calculate INITIAL phase mass flow rates from inlet (may change with condensation)
    double rhoMixInlet = inletAlphaG * inletRhoG + inletAlphaL * inletRhoL;
    double gasQualityInlet = inletAlphaG * inletRhoG / rhoMixInlet;
    double mDotGas = massFlow * gasQualityInlet;
    double mDotLiq = massFlow * (1.0 - gasQualityInlet);

    // Store local mass flow arrays for condensation tracking
    double[] localMDotGas = new double[numberOfSections];
    double[] localMDotLiq = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      localMDotGas[i] = mDotGas;
      localMDotLiq[i] = mDotLiq;
    }

    // ===== PHASE 1: Forward-marching initialization (single pass, no flash) =====
    // Provides a physically consistent pressure/holdup profile by sweeping
    // inlet-to-outlet one section at a time, using upstream gradient estimates.
    {
      for (TwoFluidSection sec : sections) {
        flowRegimeDetector.classify(sec);
      }

      // Update inlet section holdup
      TwoFluidSection inletSec = sections[0];
      double[] h0 = calculateLocalHoldup(inletSec, null, mDotGas, mDotLiq, area);
      inletSec.setLiquidHoldup(h0[0]);
      inletSec.setGasHoldup(h0[1]);
      inletSec.setGasVelocity(calculateFinitePhaseVelocity(mDotGas, h0[1], inletSec.getGasDensity(), area, 100.0));
      inletSec.setLiquidVelocity(calculateFinitePhaseVelocity(mDotLiq, h0[0], inletSec.getLiquidDensity(), area, 50.0));
      if (inletSec.getWaterDensity() > 0 && inletSec.getOilDensity() > 0 && h0[0] > 0.0) {
        updateLiquidPhaseSplit(inletSec, null, h0[0], area);
      }
      inletSec.updateDerivedQuantities();
      inletSec.updateStratifiedGeometry();

      // March from inlet to outlet computing pressure and holdup sequentially
      for (int i = 1; i < numberOfSections; i++) {
        TwoFluidSection sec = sections[i];
        TwoFluidSection prev = sections[i - 1];

        // Pressure from upstream section gradient
        double P_new = marchPressure(prev);
        sec.setPressure(P_new);

        // Holdup and velocities
        double[] hi = calculateLocalHoldup(sec, prev, mDotGas, mDotLiq, area);
        sec.setLiquidHoldup(hi[0]);
        sec.setGasHoldup(hi[1]);
        sec.setGasVelocity(calculateFinitePhaseVelocity(mDotGas, hi[1], sec.getGasDensity(), area, 100.0));
        sec.setLiquidVelocity(calculateFinitePhaseVelocity(mDotLiq, hi[0], sec.getLiquidDensity(), area, 50.0));

        // Water/oil holdups for three-phase
        if (sec.getWaterDensity() > 0 && sec.getOilDensity() > 0 && hi[0] > 0.0) {
          updateLiquidPhaseSplit(sec, prev, hi[0], area);
        }

        flowRegimeDetector.classify(sec);
        sec.updateDerivedQuantities();
        sec.updateStratifiedGeometry();
      }

      logger.info("Forward-marching init complete. Outlet P estimate: {} bara",
          sections[numberOfSections - 1].getPressure() / 1e5);
    }

    // ===== PHASE 2: Iterative refinement with under-relaxation and sparse flash =====
    // The per-section pressure change used below is proportional to the section length, so
    // on a fine mesh it falls under any fixed tolerance after a single sweep even when the
    // accumulated profile is still far from the solution. The total pressure drop is tracked
    // as well, which is mesh-independent and is the quantity the caller actually reads.
    double previousTotalDrop = Double.NaN;
    for (int iter = 0; iter < maxIter; iter++) {
      ssIterationsUsed = iter;
      // Wall-clock time guard
      long elapsed = System.currentTimeMillis() - startWallClock;
      if (elapsed > (long) (ssMaxWallClockTime * 1000)) {
        ssWallClockLimited = true;
        logger.warn("Steady-state solver reached wall-clock limit ({}s) after {} iterations", ssMaxWallClockTime, iter);
        break;
      }

      double maxChange = 0;

      // Under-relaxation: ramp from 0.3 up to ssUnderRelaxation across first 20 iterations
      double omega = Math.min(ssUnderRelaxation, 0.3 + (ssUnderRelaxation - 0.3) * Math.min(1.0, iter / 20.0));

      // Update flow regimes
      for (TwoFluidSection sec : sections) {
        flowRegimeDetector.classify(sec);
      }

      // Update inlet section (i=0) holdup using same momentum balance as other sections
      // This ensures smooth profile from inlet - no discontinuity at first section
      {
        TwoFluidSection inletSec = sections[0];
        double localMDotG = localMDotGas[0];
        double localMDotL = localMDotLiq[0];

        // Calculate inlet holdup using momentum balance (pass null for prev to indicate inlet)
        double[] inletHoldups = calculateLocalHoldup(inletSec, null, localMDotG, localMDotL, area);
        double alphaL_inlet = inletHoldups[0];
        double alphaG_inlet = inletHoldups[1];

        inletSec.setLiquidHoldup(alphaL_inlet);
        inletSec.setGasHoldup(alphaG_inlet);

        // Update inlet velocities
        inletSec.setGasVelocity(
            calculateFinitePhaseVelocity(localMDotG, alphaG_inlet, inletSec.getGasDensity(), area, 100.0));
        inletSec.setLiquidVelocity(
            calculateFinitePhaseVelocity(localMDotL, alphaL_inlet, inletSec.getLiquidDensity(), area, 50.0));

        // Update water/oil holdups for inlet if three-phase
        if (inletSec.getWaterDensity() > 0 && inletSec.getOilDensity() > 0 && alphaL_inlet > 0.0) {
          updateLiquidPhaseSplit(inletSec, null, alphaL_inlet, area);
        }

        inletSec.updateDerivedQuantities();
        inletSec.updateStratifiedGeometry();
      }

      // Update pressures and holdups using momentum balance (under-relaxed)
      for (int i = 1; i < numberOfSections; i++) {
        TwoFluidSection sec = sections[i];
        TwoFluidSection prev = sections[i - 1];

        // Pressure drop estimate (simplified steady-state)
        double P_calc = marchPressure(prev);

        // Under-relaxed pressure update
        double P_new = sec.getPressure() + omega * (P_calc - sec.getPressure());
        double change = Math.abs(P_new - sec.getPressure()) / Math.max(sec.getPressure(), 1e5);
        maxChange = Math.max(maxChange, change);

        sec.setPressure(P_new);

        // Use LOCAL mass flow rates that account for condensation
        double localMDotG = localMDotGas[i];
        double localMDotL = localMDotLiq[i];

        // Update holdup using drift-flux model with terrain effects
        double[] newHoldups = calculateLocalHoldup(sec, prev, localMDotG, localMDotL, area);
        double alphaL_calc = newHoldups[0];

        // Under-relaxed holdup update
        double alphaL_new = sec.getLiquidHoldup() + omega * (alphaL_calc - sec.getLiquidHoldup());
        double alphaG_new = 1.0 - alphaL_new;

        // Track holdup change for convergence
        double holdupChange = Math.abs(alphaL_new - sec.getLiquidHoldup());
        maxChange = Math.max(maxChange, holdupChange);

        // Apply new holdups
        sec.setLiquidHoldup(alphaL_new);
        sec.setGasHoldup(alphaG_new);

        // Update velocities based on new holdups
        sec.setGasVelocity(calculateFinitePhaseVelocity(localMDotG, alphaG_new, sec.getGasDensity(), area, 100.0));
        sec.setLiquidVelocity(calculateFinitePhaseVelocity(localMDotL, alphaL_new, sec.getLiquidDensity(), area, 50.0));

        // Update water and oil holdups for three-phase flow
        // Check if this is a three-phase system (both oil and water densities set)
        if (sec.getWaterDensity() > 0 && sec.getOilDensity() > 0) {
          double waterHoldupBefore = sec.getWaterHoldup();
          // Always update water/oil holdups when we have liquid and three-phase
          // properties
          if (alphaL_new > 0.0) {
            updateLiquidPhaseSplit(sec, prev, alphaL_new, area);
          } else {
            // No liquid: set water and oil holdups to zero
            sec.setWaterHoldup(0);
            sec.setOilHoldup(0);
            sec.setWaterCut(prev != null ? prev.getWaterCut() : sec.getWaterCut());
          }

          // The liquid split is a solved variable. Leaving it out of the residual lets the solver
          // report convergence while oil and water are still redistributing.
          maxChange = Math.max(maxChange, Math.abs(sec.getWaterHoldup() - waterHoldupBefore));
        }

        // Update derived quantities
        sec.updateDerivedQuantities();
        sec.updateStratifiedGeometry();
      }

      // Solve the energy equation whenever any thermal mechanism is active. Joule-Thomson is driven
      // by the pressure drop, not by the wall, so an adiabatic line must still cool on expansion,
      // and a DEH-heated line must still warm without wall heat transfer.
      if ((enableHeatTransfer && heatTransferCoefficient > 0) || enableJouleThomson
          || directElectricalHeatingPowerPerMeter > 0) {
        updateTemperatureProfile(massFlow, area);
      }

      // Update thermodynamics only every ssFlashInterval iterations to reduce cost.
      // TP-flash for every section is the dominant expense; sparse updates are sufficient
      // because properties change slowly with small pressure changes between iterations.
      boolean thermodynamicsRefreshed = false;
      boolean thermodynamicsEvaluated = referenceFluid == null;
      if (referenceFluid != null && (iter % ssFlashInterval == 0)) {
        thermodynamicsEvaluated = true;
        double[] densityBefore = new double[numberOfSections];
        for (int i = 0; i < numberOfSections; i++) {
          densityBefore[i] = sections[i].getGasDensity();
        }
        updateThermodynamicsWithCondensation(massFlow, localMDotGas, localMDotLiq);
        double maxDensityChange = 0.0;
        for (int i = 0; i < numberOfSections; i++) {
          double density = sections[i].getGasDensity();
          if (density > 0.0) {
            maxDensityChange = Math.max(maxDensityChange, Math.abs(density - densityBefore[i]) / density);
          }
        }
        thermodynamicsRefreshed = maxDensityChange > tolerance;
      }

      // Identify the accumulation zones so the terrain closure and the post-loop severe-slugging
      // screen can use them, but do NOT integrate the accumulation tracker here. A steady state
      // has zero net liquid accumulation by definition, and the tracker is a time integrator whose
      // volume only ever grows: it adds a non-negative rate every call, ratchets its own volume up
      // to the liquid already present, and then adds that volume back on top of the section holdup
      // that already contains it. Driven once per sweep with a nominal dt it has no fixed point, so
      // valley sections climb to the holdup cap and the profile can never settle. Terrain effects
      // in steady state come from applyTerrainAccumulation, which is algebraic in the section's own
      // Froude number and is therefore a fixed point. The tracker is still integrated in
      // runTransient, where dt is physical time.
      if (enableTerrainTracking && accumulationTracker != null) {
        accumulationTracker.identifyAccumulationZones(sections);
      }

      // The pressure march above ran on the densities the sections had BEFORE the flash in
      // this iteration, so convergence may only be declared once a flash has stopped moving
      // them - on a gas line the density change along the pipe is exactly what makes the
      // pressure gradient steepen towards the outlet. That coupling only exists when the
      // line actually loses a meaningful fraction of its pressure; on a short pipe the
      // density is uniform and the cheaper per-section criterion is sufficient.
      double totalDrop = P_inlet - sections[numberOfSections - 1].getPressure();
      boolean densityCouplingMatters = totalDrop > SS_DENSITY_COUPLING_PRESSURE_FRACTION * P_inlet;
      double dropChange = Double.isNaN(previousTotalDrop) ? Double.POSITIVE_INFINITY
          : Math.abs(totalDrop - previousTotalDrop) / Math.max(Math.abs(totalDrop), 1.0e3);
      previousTotalDrop = totalDrop;

      // The flash runs only every ssFlashInterval sweeps, and thermodynamicsRefreshed starts false, so on a
      // non-flash sweep it reports "the flash moved nothing" when in truth no flash was performed. Convergence
      // was therefore reachable on a sweep whose densities had never been re-evaluated - observed as an exit at
      // iteration 1 that returned a pressure drop several per cent away from the settled value. Require a sweep
      // in which the thermodynamics was actually evaluated and found stationary.
      boolean profileSettled = !densityCouplingMatters
          || (dropChange < tolerance && thermodynamicsEvaluated && !thermodynamicsRefreshed);
      if (maxChange < tolerance && profileSettled) {
        // A section resting on the pressure floor is a fixed point of the clamp, not of the
        // momentum balance: marchPressure keeps returning the floor, the under-relaxed update
        // stops moving, and the loop would otherwise report success on a profile the line
        // cannot actually deliver. Beggs & Brills throws on the same condition.
        if (isAnySectionAtPressureFloor()) {
          ssPressureFloorLimited = true;
          logger.warn("Steady-state profile rests on the {} bara pressure floor after {} iterations; the line cannot "
              + "deliver the specified rate at the specified inlet pressure. Reduce the flow rate, raise the "
              + "inlet pressure, or increase the diameter.", MIN_SECTION_PRESSURE_PA / 1.0e5, iter);
          break;
        }
        ssConverged = true;
        logger.info("Steady-state converged after {} iterations ({}ms wall-clock)", iter,
            System.currentTimeMillis() - startWallClock);
        break;
      }
    }

    if (!ssPressureFloorLimited && isAnySectionAtPressureFloor()) {
      ssPressureFloorLimited = true;
      ssConverged = false;
    }

    if (!ssConverged && !ssWallClockLimited && !ssPressureFloorLimited) {
      logger.warn("Steady-state solver did not converge within {} iterations for {} sections; "
          + "the reported profile is not converged. Increase setSteadyStateMaxIterations(...) "
          + "or reduce setSteadyStateUnderRelaxation(...).", maxIter, numberOfSections);
    }

    // ===== Final consistency pass: flash + holdup recalculation =====
    // With sparse flash during iteration, the final state may not be fully consistent.
    // Do one mandatory flash + holdup sweep to ensure thermodynamic consistency.
    if (referenceFluid != null) {
      updateThermodynamicsWithCondensation(massFlow, localMDotGas, localMDotLiq);

      // Re-sweep holdups using updated properties (densities changed by flash)
      for (int i = 1; i < numberOfSections; i++) {
        TwoFluidSection sec = sections[i];
        TwoFluidSection prev = sections[i - 1];
        double localMDotG = localMDotGas[i];
        double localMDotL = localMDotLiq[i];

        double[] hi = calculateLocalHoldup(sec, prev, localMDotG, localMDotL, area);
        sec.setLiquidHoldup(hi[0]);
        sec.setGasHoldup(hi[1]);
        sec.setGasVelocity(calculateFinitePhaseVelocity(localMDotG, hi[1], sec.getGasDensity(), area, 100.0));
        sec.setLiquidVelocity(calculateFinitePhaseVelocity(localMDotL, hi[0], sec.getLiquidDensity(), area, 50.0));
        if (sec.getWaterDensity() > 0 && sec.getOilDensity() > 0 && hi[0] > 0.0) {
          updateLiquidPhaseSplit(sec, prev, hi[0], area);
        }
        sec.updateDerivedQuantities();
        sec.updateStratifiedGeometry();
      }
    }

    // Final accumulation zone identification after convergence
    if (enableTerrainTracking && accumulationTracker != null) {
      accumulationTracker.identifyAccumulationZones(sections);
    }

    applySteadyStatePressureBoundary();

    // Update outlet pressure from converged profile (if not user-specified)
    if (!outletPressureSet) {
      outletPressure = sections[numberOfSections - 1].getPressure();
    }

    // The steady solver works in primitive pressure, holdup, and velocity variables.
    // Initialize the finite-volume state from the final converged primitives exactly once,
    // before the transient solver makes conservative phase mass authoritative.
    for (TwoFluidSection sec : sections) {
      double oilVelocity = sec.getOilVelocity();
      double waterVelocity = sec.getWaterVelocity();
      sec.updateConservativeVariables();
      if (sec.getOilHoldup() > 1.0e-12 && sec.getWaterHoldup() > 1.0e-12) {
        // Preserve the independent phase velocities produced by the three-phase steady closure;
        // updateConservativeVariables() otherwise initializes both momenta from bulk-liquid velocity.
        double oilMomentum = sec.getOilMassPerLength() * oilVelocity;
        double waterMomentum = sec.getWaterMassPerLength() * waterVelocity;
        sec.setOilMomentumPerLength(oilMomentum);
        sec.setWaterMomentumPerLength(waterMomentum);
        sec.setLiquidMomentumPerLength(oilMomentum + waterMomentum);
      }
    }

    // Store initial profiles
    updateResultArrays();
  }

  /**
   * Align the converged steady-state pressure profile with explicit pressure boundaries.
   *
   * <p>
   * The steady-state solver computes friction and gravity pressure differences from the specified flow. For a
   * flow-specified inlet with fixed outlet pressure, the absolute pressure level is set by the outlet boundary, so the
   * whole profile can be shifted without changing the calculated pressure gradients.
   * </p>
   */
  private void applySteadyStatePressureBoundary() {
    if (sections == null || sections.length == 0) {
      return;
    }

    if (outletBCType == BoundaryCondition.CONSTANT_PRESSURE && outletPressureSet) {
      double pressureShift = outletPressure - sections[numberOfSections - 1].getPressure();
      for (TwoFluidSection sec : sections) {
        sec.setPressure(Math.max(1.0e5, sec.getPressure() + pressureShift));
      }
      sections[numberOfSections - 1].setPressure(Math.max(1.0e5, outletPressure));
    } else if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE && inletPressureSet) {
      double pressureShift = inletPressure - sections[0].getPressure();
      for (TwoFluidSection sec : sections) {
        sec.setPressure(Math.max(1.0e5, sec.getPressure() + pressureShift));
      }
      sections[0].setPressure(Math.max(1.0e5, inletPressure));
    }
  }

  /**
   * Update thermodynamics along pipe with condensation tracking.
   *
   * <p>
   * Performs TP-flash at each section to determine local phase fractions accounting for condensation/vaporization. This
   * is critical for gas systems with water where liquid may condense as pressure drops and temperature decreases along
   * the pipeline.
   * </p>
   *
   * @param massFlow Total mass flow rate [kg/s]
   * @param localMDotGas Array to store local gas mass flow rates [kg/s]
   * @param localMDotLiq Array to store local liquid mass flow rates [kg/s]
   */
  private void updateThermodynamicsWithCondensation(double massFlow, double[] localMDotGas, double[] localMDotLiq) {
    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      try {
        SystemInterface flash = referenceFluid.clone();
        flash.setPressure(sec.getPressure() / 1e5, "bara"); // Convert Pa to bar
        flash.setTemperature(sec.getTemperature(), "K");

        ThermodynamicOperations ops = new ThermodynamicOperations(flash);
        ops.TPflash();
        flash.initPhysicalProperties();

        // Update phase properties
        if (flash.hasPhaseType("gas")) {
          sec.setGasDensity(flash.getPhase("gas").getDensity("kg/m3"));
          sec.setGasViscosity(flash.getPhase("gas").getViscosity("kg/msec"));
          sec.setGasSoundSpeed(flash.getPhase("gas").getSoundSpeed());
          sec.setGasEnthalpy(flash.getPhase("gas").getEnthalpy("J/kg"));
        }

        // Calculate local phase MASS fractions from flash results
        // Use getMolarMass() weighted by beta (phase fraction) to get mass fractions
        // This properly accounts for condensation as temperature drops
        double gasMassFraction = 0.0;
        double liqMassFraction = 0.0;

        // Calculate total mass-weighted contribution from all phases
        double totalMassWeighted = 0.0;
        for (int p = 0; p < flash.getNumberOfPhases(); p++) {
          // beta is mole fraction, multiply by molar mass to get mass contribution
          double beta = flash.getBeta(p);
          double molarMass = flash.getPhase(p).getMolarMass(); // kg/mol
          totalMassWeighted += beta * molarMass;
        }

        // Now calculate mass fraction for each phase type
        double gasMassContrib = 0.0;
        double liqMassContrib = 0.0;
        double volTotal = 0.0;
        double volLiq = 0.0;

        // Handle liquid phases (oil, water, or both)
        boolean hasOil = flash.hasPhaseType("oil");
        boolean hasWater = flash.hasPhaseType("aqueous");

        for (int p = 0; p < flash.getNumberOfPhases(); p++) {
          double beta = flash.getBeta(p);
          double molarMass = flash.getPhase(p).getMolarMass(); // kg/mol
          double massContrib = beta * molarMass;
          String phaseType = flash.getPhase(p).getType().toString();

          if (phaseType.equalsIgnoreCase("gas")) {
            gasMassContrib += massContrib;
          } else {
            // oil or aqueous
            liqMassContrib += massContrib;
          }

          // Track volumetric flows for water/oil split calculation. Density-consistent,
          // so the split is not biased by the equation-of-state volume shift.
          double phaseDensity = flash.getPhase(p).getDensity("kg/m3");
          double phaseVolFlow = phaseDensity > 0.0 ? flash.getPhase(p).getFlowRate("kg/sec") / phaseDensity : 0.0;
          volTotal += phaseVolFlow;
          if (!phaseType.equalsIgnoreCase("gas")) {
            volLiq += phaseVolFlow;
          }
        }

        // Update liquid phase properties (densities, viscosities)
        if (hasOil) {
          sec.setOilDensity(flash.getPhase("oil").getDensity("kg/m3"));
          sec.setOilViscosity(flash.getPhase("oil").getViscosity("kg/msec"));
        }

        if (hasWater) {
          sec.setWaterDensity(flash.getPhase("aqueous").getDensity("kg/m3"));
          sec.setWaterViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
        }

        // Calculate mass fractions and update local mass flow rates
        if (totalMassWeighted > 0) {
          gasMassFraction = gasMassContrib / totalMassWeighted;
          liqMassFraction = liqMassContrib / totalMassWeighted;

          // Scale to actual mass flow rate
          localMDotGas[i] = massFlow * gasMassFraction;
          localMDotLiq[i] = massFlow * liqMassFraction;
        }

        // IMPORTANT: DO NOT overwrite holdup from flash volumetric fractions!
        // The holdup from flash (volLiq/volTotal) is the NO-SLIP holdup (λL),
        // NOT the actual in-situ holdup (αL) which is calculated from momentum balance
        // in calculateLocalHoldup(). Overwriting would destroy the slip/accumulation
        // effects and cause the model to miss liquid accumulation at low flows.
        //
        // Instead, only update the water/oil SPLIT within the liquid phase,
        // preserving the total liquid holdup from momentum balance.
        if (volTotal > 0 && volLiq > 0) {
          // Update water/oil split if both are present (preserve total liquid holdup)
          if (hasOil && hasWater) {
            double volOil = phaseVolumetricFlow(flash, "oil");
            double volWater = phaseVolumetricFlow(flash, "aqueous");
            // Transported fraction. The in-situ split is closed against it in
            // updateWaterOilHoldups, which may differ from it through slip.
            sec.setInputWaterVolumeFraction(volWater / (volOil + volWater));
            double waterCut = volWater / volLiq;
            sec.setWaterCut(waterCut);
            sec.setOilFractionInLiquid(1.0 - waterCut);
            // Update water/oil holdups based on EXISTING total liquid holdup and
            // new split
            double existingAlphaL = sec.getLiquidHoldup();
            sec.setWaterHoldup(existingAlphaL * waterCut);
            sec.setOilHoldup(existingAlphaL * (1.0 - waterCut));
          } else if (hasWater && !hasOil) {
            // Gas + water only - all liquid is water
            sec.setInputWaterVolumeFraction(1.0);
            sec.setWaterCut(1.0);
            sec.setOilFractionInLiquid(0.0);
            sec.setWaterHoldup(sec.getLiquidHoldup());
            sec.setOilHoldup(0.0);
          } else if (hasOil && !hasWater) {
            // Gas + oil only - all liquid is oil
            sec.setInputWaterVolumeFraction(0.0);
            sec.setWaterCut(0.0);
            sec.setOilFractionInLiquid(1.0);
            sec.setWaterHoldup(0.0);
            sec.setOilHoldup(sec.getLiquidHoldup());
          }
        }

        // Update combined liquid properties
        if (hasOil && hasWater) {
          double rhoOil = flash.getPhase("oil").getDensity("kg/m3");
          double rhoWater = flash.getPhase("aqueous").getDensity("kg/m3");
          double muOil = flash.getPhase("oil").getViscosity("kg/msec");
          double muWater = flash.getPhase("aqueous").getViscosity("kg/msec");
          double waterCut = sec.getWaterCut();
          double oilFraction = 1.0 - waterCut;

          // Volume-weighted density
          sec.setLiquidDensity(oilFraction * rhoOil + waterCut * rhoWater);

          // Effective viscosity (Brinkman)
          double muL;
          if (oilFraction > 0.5) {
            muL = muOil * Math.pow(1.0 - waterCut, -2.5);
          } else {
            muL = muWater * Math.pow(1.0 - oilFraction, -2.5);
          }
          sec.setLiquidViscosity(muL);
          sec.setLiquidSoundSpeed(flash.getPhase("oil").getSoundSpeed());
          sec.setLiquidEnthalpy(oilFraction * flash.getPhase("oil").getEnthalpy("J/kg")
              + waterCut * flash.getPhase("aqueous").getEnthalpy("J/kg"));

        } else if (hasOil) {
          sec.setLiquidDensity(flash.getPhase("oil").getDensity("kg/m3"));
          sec.setLiquidViscosity(flash.getPhase("oil").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("oil").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("oil").getEnthalpy("J/kg"));
          sec.setWaterCut(0.0);
          sec.setOilFractionInLiquid(1.0);
          sec.setWaterHoldup(0.0);
          sec.setOilHoldup(sec.getLiquidHoldup());
        } else if (hasWater) {
          sec.setLiquidDensity(flash.getPhase("aqueous").getDensity("kg/m3"));
          sec.setLiquidViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("aqueous").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("aqueous").getEnthalpy("J/kg"));
          sec.setWaterCut(1.0);
          sec.setOilFractionInLiquid(0.0);
          sec.setWaterHoldup(sec.getLiquidHoldup());
          sec.setOilHoldup(0.0);
        }
      } catch (Exception e) {
        logger.warn("Flash calculation failed for section {} at position {}", i, sec.getPosition());
      }
    }
  }

  /**
   * Update temperature profile along the pipe accounting for heat transfer.
   *
   * <p>
   * Steady-state energy balance: m_dot * Cp * dT/dx = -h * π * D * (T - T_surface) + q_DEH - μ_JT * dP/dx
   * </p>
   *
   * @param massFlow Total mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   */
  private void updateTemperatureProfile(double massFlow, double area) {
    // Get mixture heat capacity from inlet fluid
    SystemInterface inletFluid = getInletStream().getFluid();
    double Cp = inletFluid.getCp("J/kgK");
    if (Cp <= 0 || Double.isNaN(Cp)) {
      Cp = 2000.0; // Default if not available (gas-liquid mixture)
    }

    // Get Joule-Thomson coefficient if enabled
    double muJT = 0.0;
    if (enableJouleThomson) {
      try {
        // Real thermodynamic coefficient for the actual (possibly two-phase) mixture.
        // Do not gate this on Cp/Cv: for a two-phase mixture that ratio is not bounded by 1..2.
        double muJTperBar = inletFluid.getJouleThomsonCoefficient("K/bar");
        if (!Double.isNaN(muJTperBar) && Math.abs(muJTperBar) < 10.0) {
          muJT = muJTperBar / 1.0e5; // K/bar to K/Pa
        }
      } catch (Exception e) {
        muJT = 0.0;
      }
    }

    double pipePerimeter = Math.PI * diameter;
    double P_prev = sections[0].getPressure();

    // Initialize hydrate/wax risk arrays
    hydrateRiskSections = new boolean[numberOfSections];
    waxRiskSections = new boolean[numberOfSections];

    // March through pipe solving energy equation
    for (int i = 1; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      TwoFluidSection prev = sections[i - 1];

      double T_prev = prev.getTemperature();

      // Get local heat transfer coefficient (profile or constant)
      double h = heatTransferCoefficient;
      if (heatTransferProfile != null && i < heatTransferProfile.length) {
        h = heatTransferProfile[i];
      }

      // Get local surface temperature (profile or constant)
      double T_surface = surfaceTemperature;
      if (surfaceTemperatureProfile != null && i < surfaceTemperatureProfile.length) {
        T_surface = surfaceTemperatureProfile[i];
      }

      // Add soil thermal resistance if applicable
      if (soilThermalResistance > 0 && h > 0) {
        // Effective U = 1 / (1/h + R_soil)
        h = 1.0 / (1.0 / h + soilThermalResistance);
      }

      // Joule-Thomson cooling from pressure drop
      double dP = sec.getPressure() - P_prev;
      // The coefficient rises strongly as the gas expands, so evaluate it at the local state
      // rather than holding the inlet value over the whole line.
      double muJTlocal = localJouleThomsonCoefficient(0.5 * (sec.getPressure() + P_prev), T_prev, muJT);
      double dT_JT = muJTlocal * dP; // Temperature change due to J-T effect

      // Heat transfer calculation with exponential solution. Direct electrical heating enters as a
      // uniform source, which shifts the asymptote the exponential decays towards from the surface
      // temperature to the wall-loss/DEH balance temperature. Solving it this way is exact for a
      // constant source and cannot overshoot the balance the way explicit per-segment stepping does.
      double T_new;
      double T_asymptote = T_surface;
      if (h > 0 && massFlow > 0 && Cp > 0) {
        T_asymptote = T_surface + directElectricalHeatingPowerPerMeter / (h * pipePerimeter);
        double exponent = -h * pipePerimeter * sec.getLength() / (massFlow * Cp);
        T_new = T_asymptote + (T_prev - T_asymptote) * Math.exp(exponent);
      } else {
        T_new = T_prev;
        if (massFlow > 0 && Cp > 0) {
          T_new += directElectricalHeatingPowerPerMeter * sec.getLength() / (massFlow * Cp);
        }
      }

      // Bound the heat-exchange term only: wall heat transfer alone can approach the balance
      // temperature but never cross it. Joule-Thomson is applied afterwards and is deliberately
      // not bounded by the surface temperature, because expansion cooling can and does take the
      // fluid below ambient - that is what drives subsea hydrate and MDMT exposure.
      if (h > 0) {
        if (T_prev > T_asymptote) {
          T_new = Math.max(T_new, T_asymptote);
          T_new = Math.min(T_new, T_prev);
        } else {
          T_new = Math.min(T_new, T_asymptote);
          T_new = Math.max(T_new, T_prev);
        }
      }

      // Add Joule-Thomson effect
      T_new += dT_JT;

      T_new = Math.max(T_new, 100.0); // Never below 100K (absolute minimum)

      sec.setTemperature(T_new);

      // Check hydrate/wax risk
      if (hydrateFormationTemperature > 0 && T_new < hydrateFormationTemperature) {
        hydrateRiskSections[i] = true;
      }
      if (waxAppearanceTemperature > 0 && T_new < waxAppearanceTemperature) {
        waxRiskSections[i] = true;
      }

      P_prev = sec.getPressure();
    }
  }

  /**
   * Evaluate the Joule-Thomson coefficient at a local pipe state.
   *
   * <p>
   * The coefficient of a rich gas rises substantially as the fluid expands along the line, so holding the inlet value
   * over the whole pipe under-predicts the expansion cooling.
   * </p>
   *
   * @param pressurePa local pressure in Pa
   * @param temperatureK local temperature in K
   * @param fallback coefficient in K/Pa to return when the local flash is unavailable or fails
   * @return Joule-Thomson coefficient in K/Pa
   */
  private double localJouleThomsonCoefficient(double pressurePa, double temperatureK, double fallback) {
    if (!enableJouleThomson || referenceFluid == null || pressurePa <= 0.0 || temperatureK <= 0.0) {
      return fallback;
    }
    try {
      SystemInterface local = referenceFluid.clone();
      local.setPressure(pressurePa / 1.0e5, "bara");
      local.setTemperature(temperatureK, "K");
      new ThermodynamicOperations(local).TPflash();
      local.initProperties();
      double muJTperBar = local.getJouleThomsonCoefficient("K/bar");
      if (Double.isNaN(muJTperBar) || Math.abs(muJTperBar) >= 10.0) {
        return fallback;
      }
      return muJTperBar / 1.0e5;
    } catch (Exception e) {
      return fallback;
    }
  }

  /** Time-integrated thermal-model terms for one accepted internal step. */
  private static final class ThermalEnergyStep {
    private double fluidEnergyChangeJ;
    private double wallEnergyChangeJ;
    private double sensibleAdvectionEnergyJ;
    private double jouleThomsonEnergyJ;
    private double latentHeatEnergyJ;
    private double ambientHeatLossJ;
    private double directElectricalHeatingEnergyJ;
  }

  /**
   * Update temperature after an accepted transient hydrodynamic step.
   *
   * <p>
   * Sensible-energy advection uses the integration-weighted phase-resolved finite-volume face mass fluxes retained for
   * each hydrodynamic stage. CLOSED external faces are therefore exactly adiabatic to mass transport while internal
   * convection remains active. Radial heat transfer is applied to every physical cell, including section zero. This
   * post-step update is the sole owner of ambient heat exchange; the equation object's duplicate wall source is
   * disabled by the heat-transfer setters.
   * </p>
   *
   * @param dt time step in seconds
   * @param phaseMassFaceFluxes integration-weighted gas, oil, and water face mass flows in kg/s
   * @param latentHeatEnergyByCellJ compositional/interphase heat added in each cell over the step, in joules
   * @return time-integrated sensible-energy terms for the accepted step
   */
  private ThermalEnergyStep updateTransientTemperature(double dt, double[][] phaseMassFaceFluxes,
      double[] latentHeatEnergyByCellJ) {
    SystemInterface inletFluid = getInletStream().getFluid();
    double Cp = inletFluid.getCp("J/kgK");
    if (Cp <= 0.0 || !Double.isFinite(Cp)) {
      Cp = 2000.0;
    }

    if (wallTemperatureProfile == null || wallTemperatureProfile.length != numberOfSections) {
      wallTemperatureProfile = new double[numberOfSections];
      for (int i = 0; i < numberOfSections; i++) {
        wallTemperatureProfile[i] = sections[i].getTemperature();
      }
    }

    if (hydrateRiskSections == null || hydrateRiskSections.length != numberOfSections) {
      hydrateRiskSections = new boolean[numberOfSections];
      waxRiskSections = new boolean[numberOfSections];
    }

    double muJT = enableJouleThomson ? 0.4 / 1.0e5 : 0.0;
    double[] previousFluidTemperatures = new double[numberOfSections];
    for (int section = 0; section < numberOfSections; section++) {
      previousFluidTemperatures[section] = sections[section].getTemperature();
    }

    if (useMultilayerThermalModel && thermalCalculator != null) {
      return updateTransientTemperatureMultilayer(phaseMassFaceFluxes, previousFluidTemperatures,
          latentHeatEnergyByCellJ, dt, Cp, muJT);
    }

    double pipePerimeter = Math.PI * diameter;
    double outerDiameter = diameter + 2.0 * wallThickness;
    double outerPerimeter = Math.PI * outerDiameter;
    double wallArea = Math.PI * (outerDiameter * outerDiameter - diameter * diameter) / 4.0;
    double wallMassPerLength = wallArea * wallDensity;
    double fallbackFluidMassPerLength = sections[0].getArea() * inletFluid.getDensity("kg/m3");
    ThermalEnergyStep energyStep = new ThermalEnergyStep();

    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      double oldFluidTemperature = previousFluidTemperatures[i];
      double wallTemperature = wallTemperatureProfile[i];
      double oldWallTemperature = wallTemperature;

      double hInner = heatTransferCoefficient;
      if (heatTransferProfile != null && i < heatTransferProfile.length) {
        hInner = heatTransferProfile[i];
      }

      double ambientTemperature = surfaceTemperature;
      if (surfaceTemperatureProfile != null && i < surfaceTemperatureProfile.length) {
        ambientTemperature = surfaceTemperatureProfile[i];
      }

      double hOuter = hInner;
      if (soilThermalResistance > 0.0 && hInner > 0.0) {
        hOuter = 1.0 / (1.0 / hInner + soilThermalResistance);
      }

      double fluidToWallHeat = hInner * pipePerimeter * (oldFluidTemperature - wallTemperature);
      double wallToAmbientHeat = hOuter * outerPerimeter * (wallTemperature - ambientTemperature);
      double sensibleAdvection = calcSensibleAdvectionSource(i, phaseMassFaceFluxes, previousFluidTemperatures, Cp);
      double jouleThomsonSource = calcLocalJouleThomsonSource(i, phaseMassFaceFluxes, Cp, muJT);
      double latentHeatSource = latentHeatEnergyByCellJ[i] / (dt * sec.getLength());
      double dehSource = directElectricalHeatingPowerPerMeter;

      double wallThermalMass = wallMassPerLength * wallHeatCapacity;
      if (wallThermalMass > 0.0) {
        wallTemperature += (fluidToWallHeat - wallToAmbientHeat) / wallThermalMass * dt;
      }
      wallTemperatureProfile[i] = wallTemperature;

      double fluidMassPerLength = getLocalFluidMassPerLength(sec, fallbackFluidMassPerLength);
      double newFluidTemperature = oldFluidTemperature
          + (sensibleAdvection - fluidToWallHeat + jouleThomsonSource + latentHeatSource + dehSource)
              / (fluidMassPerLength * Cp) * dt;
      newFluidTemperature = Math.max(newFluidTemperature, 100.0);
      sec.setTemperature(newFluidTemperature);
      updateThermalRiskFlags(i, newFluidTemperature);

      double cellLength = sec.getLength();
      energyStep.fluidEnergyChangeJ += (newFluidTemperature - oldFluidTemperature) * fluidMassPerLength * Cp
          * cellLength;
      energyStep.wallEnergyChangeJ += (wallTemperature - oldWallTemperature) * wallThermalMass * cellLength;
      energyStep.sensibleAdvectionEnergyJ += sensibleAdvection * dt * cellLength;
      energyStep.jouleThomsonEnergyJ += jouleThomsonSource * dt * cellLength;
      energyStep.latentHeatEnergyJ += latentHeatEnergyByCellJ[i];
      energyStep.ambientHeatLossJ += wallToAmbientHeat * dt * cellLength;
      energyStep.directElectricalHeatingEnergyJ += dehSource * dt * cellLength;
    }
    return energyStep;
  }

  /**
   * Select the conservative local phase inventory used as fluid thermal inertia.
   *
   * @param section finite-volume cell
   * @param fallbackMassPerLength fallback inventory in kg/m
   * @return finite positive fluid inventory in kg/m
   */
  private double getLocalFluidMassPerLength(TwoFluidSection section, double fallbackMassPerLength) {
    double localMass = section.getGasMassPerLength() + section.getOilMassPerLength() + section.getWaterMassPerLength();
    return selectFinitePositiveFluidMassPerLength(localMass, fallbackMassPerLength);
  }

  /**
   * Select a finite positive thermal-inertia value, preferring local conservative inventory.
   *
   * @param localMassPerLength local phase inventory in kg/m
   * @param fallbackMassPerLength fallback inventory in kg/m
   * @return local value, fallback value, or the positive numerical floor
   */
  static double selectFinitePositiveFluidMassPerLength(double localMassPerLength, double fallbackMassPerLength) {
    if (Double.isFinite(localMassPerLength) && localMassPerLength > 0.0) {
      return localMassPerLength;
    }
    if (Double.isFinite(fallbackMassPerLength) && fallbackMassPerLength > 0.0) {
      return fallbackMassPerLength;
    }
    return 1.0e-12;
  }

  /**
   * Calculate the explicit sensible-energy advection source for one cell.
   *
   * @param cell zero-based cell index
   * @param phaseMassFaceFluxes face-by-phase mass flows in kg/s
   * @param previousFluidTemperatures immutable pre-update cell temperatures in kelvin
   * @param Cp fluid heat capacity in J/(kg K)
   * @return sensible-energy source in W/m
   */
  private double calcSensibleAdvectionSource(int cell, double[][] phaseMassFaceFluxes,
      double[] previousFluidTemperatures, double Cp) {
    return calculateExplicitSensibleAdvectionSource(cell, phaseMassFaceFluxes, previousFluidTemperatures,
        getInletStream().getFluid().getTemperature("K"), Cp, sections[cell].getLength());
  }

  /**
   * Apply first-order upwinding to phase-resolved face mass flows using one pre-update temperature snapshot.
   *
   * <p>
   * Positive face flow is oriented from inlet to outlet. The external inlet uses {@code inletTemperature}; internal
   * reverse flow uses the downstream cell. The external outlet is outflow-only.
   * </p>
   *
   * @param cell zero-based cell index
   * @param phaseMassFaceFluxes face-by-phase mass flows in kg/s, with one more face than cells
   * @param previousFluidTemperatures cell temperatures in kelvin before the explicit update
   * @param inletTemperature external inlet temperature in kelvin
   * @param Cp fluid heat capacity in J/(kg K)
   * @param cellLength cell length in metres
   * @return sensible-energy source in W/m
   */
  static double calculateExplicitSensibleAdvectionSource(int cell, double[][] phaseMassFaceFluxes,
      double[] previousFluidTemperatures, double inletTemperature, double Cp, double cellLength) {
    double cellTemperature = previousFluidTemperatures[cell];
    double source = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      double leftMassFlow = phaseMassFaceFluxes[cell][phase];
      double rightMassFlow = phaseMassFaceFluxes[cell + 1][phase];

      double leftUpwindTemperature = cellTemperature;
      if (leftMassFlow > 0.0) {
        leftUpwindTemperature = cell == 0 ? inletTemperature : previousFluidTemperatures[cell - 1];
      }

      double rightUpwindTemperature = cellTemperature;
      if (rightMassFlow < 0.0 && cell + 1 < previousFluidTemperatures.length) {
        rightUpwindTemperature = previousFluidTemperatures[cell + 1];
      }
      // The external outlet mass flux is outflow-only. The negative right-flow branch above therefore applies only
      // to internal faces, where the downstream cell supplies the upwind temperature.

      source += Cp * (leftMassFlow * (leftUpwindTemperature - cellTemperature)
          - rightMassFlow * (rightUpwindTemperature - cellTemperature)) / cellLength;
    }
    return source;
  }

  private double calcLocalJouleThomsonSource(int cell, double[][] phaseMassFaceFluxes, double Cp, double muJT) {
    double leftPressure = cell > 0 ? sections[cell - 1].getPressure() : Double.NaN;
    double rightPressure = cell + 1 < numberOfSections ? sections[cell + 1].getPressure() : Double.NaN;
    return calculateLocalJouleThomsonSource(cell, phaseMassFaceFluxes, leftPressure, sections[cell].getPressure(),
        rightPressure, Cp, muJT, sections[cell].getLength());
  }

  /**
   * Calculate the Joule-Thomson source from mass entering a cell at either internal face.
   *
   * <p>
   * Positive face flow is oriented from inlet to outlet. Forward flow therefore uses the left-face pressure increase,
   * while reverse flow uses the right-face pressure increase with the same spatial orientation. External faces are
   * excluded because no external boundary pressure is available to define their local gradient.
   * </p>
   *
   * @param cell zero-based cell index
   * @param phaseMassFaceFluxes face-by-phase mass flows in kg/s
   * @param leftPressure left-neighbour pressure in pascals, or NaN at the inlet boundary
   * @param cellPressure cell pressure in pascals
   * @param rightPressure right-neighbour pressure in pascals, or NaN at the outlet boundary
   * @param Cp fluid heat capacity in J/(kg K)
   * @param muJT Joule-Thomson coefficient in K/Pa
   * @param cellLength cell length in metres
   * @return Joule-Thomson energy source in W/m
   */
  static double calculateLocalJouleThomsonSource(int cell, double[][] phaseMassFaceFluxes, double leftPressure,
      double cellPressure, double rightPressure, double Cp, double muJT, double cellLength) {
    if (muJT == 0.0 || cellLength <= 0.0) {
      return 0.0;
    }
    double source = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      double leftMassFlow = phaseMassFaceFluxes[cell][phase];
      if (leftMassFlow > 0.0 && Double.isFinite(leftPressure)) {
        source += leftMassFlow * Cp * muJT * (cellPressure - leftPressure) / cellLength;
      }
      double rightMassFlow = phaseMassFaceFluxes[cell + 1][phase];
      if (rightMassFlow < 0.0 && Double.isFinite(rightPressure)) {
        source += rightMassFlow * Cp * muJT * (rightPressure - cellPressure) / cellLength;
      }
    }
    return source;
  }

  private double getCellFaceThroughput(int cell, double[][] phaseMassFaceFluxes) {
    double leftThroughput = 0.0;
    double rightThroughput = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      leftThroughput += Math.abs(phaseMassFaceFluxes[cell][phase]);
      rightThroughput += Math.abs(phaseMassFaceFluxes[cell + 1][phase]);
    }
    return Math.max(leftThroughput, rightThroughput);
  }

  private void updateThermalRiskFlags(int section, double temperature) {
    hydrateRiskSections[section] = hydrateFormationTemperature > 0.0 && temperature < hydrateFormationTemperature;
    waxRiskSections[section] = waxAppearanceTemperature > 0.0 && temperature < waxAppearanceTemperature;
  }

  /**
   * Update temperature using the multi-layer radial thermal model.
   *
   * @param phaseMassFaceFluxes gas, oil, and water mass flow at every finite-volume face in kg/s
   * @param previousFluidTemperatures pre-update cell temperatures in kelvin
   * @param latentHeatEnergyByCellJ compositional/interphase heat added in each cell over the step, in joules
   * @param dt time step in seconds
   * @param Cp fluid heat capacity in J/(kg K)
   * @param muJT Joule-Thomson coefficient in K/Pa
   * @return time-integrated sensible-energy terms for the accepted step
   */
  private ThermalEnergyStep updateTransientTemperatureMultilayer(double[][] phaseMassFaceFluxes,
      double[] previousFluidTemperatures, double[] latentHeatEnergyByCellJ, double dt, double Cp, double muJT) {
    double fallbackFluidMassPerLength = sections[0].getArea() * getInletStream().getFluid().getDensity("kg/m3");
    double[][] layerTemperatures = getOrInitializeMultilayerLayerTemperatures();
    ThermalEnergyStep energyStep = new ThermalEnergyStep();

    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      double oldFluidTemperature = previousFluidTemperatures[i];
      double oldWallEnergyPerLength = calculateMultilayerThermalEnergyPerLength(thermalCalculator,
          layerTemperatures[i]);
      double ambientTemperature = surfaceTemperature;
      if (surfaceTemperatureProfile != null && i < surfaceTemperatureProfile.length) {
        ambientTemperature = surfaceTemperatureProfile[i];
      }

      double localMassFlow = getCellFaceThroughput(i, phaseMassFaceFluxes);
      double hInner = calculateInnerHTC(localMassFlow, sec.getArea());
      double wallTemperature = advanceMultilayerCellThermalState(thermalCalculator, layerTemperatures[i],
          oldFluidTemperature, ambientTemperature, hInner, dt);

      double heatLoss = thermalCalculator.getLastFluidHeatTransferPerLength();
      double ambientHeatLoss = thermalCalculator.getLastAmbientHeatTransferPerLength();
      double sensibleAdvection = calcSensibleAdvectionSource(i, phaseMassFaceFluxes, previousFluidTemperatures, Cp);
      double jouleThomsonSource = calcLocalJouleThomsonSource(i, phaseMassFaceFluxes, Cp, muJT);
      double latentHeatSource = latentHeatEnergyByCellJ[i] / (dt * sec.getLength());
      double dehSource = directElectricalHeatingPowerPerMeter;
      double fluidMassPerLength = getLocalFluidMassPerLength(sec, fallbackFluidMassPerLength);
      double newFluidTemperature = oldFluidTemperature
          + (sensibleAdvection - heatLoss + jouleThomsonSource + latentHeatSource + dehSource)
              / (fluidMassPerLength * Cp) * dt;

      newFluidTemperature = Math.max(newFluidTemperature, 100.0);
      sec.setTemperature(newFluidTemperature);

      if (thermalCalculator.getNumberOfLayers() > 0) {
        wallTemperatureProfile[i] = wallTemperature;
      }
      updateThermalRiskFlags(i, newFluidTemperature);

      double cellLength = sec.getLength();
      double newWallEnergyPerLength = calculateMultilayerThermalEnergyPerLength(thermalCalculator,
          layerTemperatures[i]);
      energyStep.fluidEnergyChangeJ += (newFluidTemperature - oldFluidTemperature) * fluidMassPerLength * Cp
          * cellLength;
      energyStep.wallEnergyChangeJ += (newWallEnergyPerLength - oldWallEnergyPerLength) * cellLength;
      energyStep.sensibleAdvectionEnergyJ += sensibleAdvection * dt * cellLength;
      energyStep.jouleThomsonEnergyJ += jouleThomsonSource * dt * cellLength;
      energyStep.latentHeatEnergyJ += latentHeatEnergyByCellJ[i];
      energyStep.ambientHeatLossJ += ambientHeatLoss * dt * cellLength;
      energyStep.directElectricalHeatingEnergyJ += dehSource * dt * cellLength;
    }
    return energyStep;
  }

  /**
   * Calculate stored sensible energy in one cell's radial layers per unit pipe length.
   *
   * @param calculator configured radial-layer properties
   * @param layerTemperatures cell-owned radial-layer temperatures in kelvin
   * @return stored radial-layer energy in J/m relative to zero kelvin
   */
  private static double calculateMultilayerThermalEnergyPerLength(MultilayerThermalCalculator calculator,
      double[] layerTemperatures) {
    double energyPerLength = 0.0;
    List<RadialThermalLayer> layers = calculator.getLayers();
    for (int layer = 0; layer < layers.size(); layer++) {
      energyPerLength += layers.get(layer).getThermalMassPerLength() * layerTemperatures[layer];
    }
    return energyPerLength;
  }

  /**
   * Advance one cell's radial thermal state using a shared calculator configuration.
   *
   * <p>
   * The stored layer temperatures are restored before every advance so sequential cells cannot inherit another cell's
   * state. The updated temperatures are copied back into the caller-owned array.
   * </p>
   *
   * @param calculator configured radial thermal calculator
   * @param layerTemperatures persistent layer temperatures for one cell, in kelvin
   * @param fluidTemperature cell fluid temperature in kelvin
   * @param ambientTemperature local ambient temperature in kelvin
   * @param innerHeatTransferCoefficient fluid-side heat-transfer coefficient in W/(m2 K)
   * @param dt accepted thermal time step in seconds
   * @return inner-wall interface temperature in kelvin, or {@link Double#NaN} when no layers are configured
   * @throws IllegalArgumentException if the stored profile does not match the configured layer count
   */
  static double advanceMultilayerCellThermalState(MultilayerThermalCalculator calculator, double[] layerTemperatures,
      double fluidTemperature, double ambientTemperature, double innerHeatTransferCoefficient, double dt) {
    List<RadialThermalLayer> layers = calculator.getLayers();
    if (layerTemperatures == null || layerTemperatures.length != layers.size()) {
      throw new IllegalArgumentException("Stored radial-layer profile must match the configured layer count");
    }
    for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
      layers.get(layerIndex).initializeTemperature(layerTemperatures[layerIndex]);
    }

    calculator.setFluidTemperature(fluidTemperature);
    calculator.setAmbientTemperature(ambientTemperature);
    calculator.setInnerHTC(innerHeatTransferCoefficient);
    calculator.updateTransient(dt);

    for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
      layerTemperatures[layerIndex] = layers.get(layerIndex).getTemperature();
    }
    return layers.isEmpty() ? Double.NaN : calculator.calculateInterfaceTemperature(0, false);
  }

  /**
   * Return the persistent radial-layer temperature state for every finite-volume cell.
   *
   * <p>
   * {@link MultilayerThermalCalculator} is stateful. Each cell therefore stores its own layer temperatures and restores
   * them before advancing the shared configuration template exactly once per accepted thermal time step.
   * </p>
   *
   * @return cell-by-layer temperature array in kelvin
   */
  private double[][] getOrInitializeMultilayerLayerTemperatures() {
    int layerCount = thermalCalculator.getNumberOfLayers();
    boolean dimensionsMatch = multilayerLayerTemperatureProfiles != null
        && multilayerLayerTemperatureProfiles.length == numberOfSections;
    if (dimensionsMatch) {
      for (double[] cellTemperatures : multilayerLayerTemperatureProfiles) {
        if (cellTemperatures.length != layerCount) {
          dimensionsMatch = false;
          break;
        }
      }
    }
    if (dimensionsMatch) {
      return multilayerLayerTemperatureProfiles;
    }

    multilayerLayerTemperatureProfiles = new double[numberOfSections][layerCount];
    List<RadialThermalLayer> layers = thermalCalculator.getLayers();
    for (int cell = 0; cell < numberOfSections; cell++) {
      for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
        double initialTemperature = layers.get(layerIndex).getTemperature();
        multilayerLayerTemperatureProfiles[cell][layerIndex] = Double.isFinite(initialTemperature) ? initialTemperature
            : sections[cell].getTemperature();
      }
    }
    return multilayerLayerTemperatureProfiles;
  }

  /**
   * Calculate inner (fluid-side) heat transfer coefficient based on flow conditions.
   *
   * <p>
   * Uses the configured stagnant coefficient at zero local face throughput, Dittus-Boelter for turbulent flow, and a
   * constant Nusselt number for laminar flow. The stagnant coefficient is independent of the overall pipe-to-ambient
   * coefficient used by the simple thermal model.
   * </p>
   *
   * @param massFlow Mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   * @return Inner HTC in W/(m²·K)
   */
  double calculateInnerHTC(double massFlow, double area) {
    if (massFlow <= 0 || area <= 0) {
      return stagnantInnerHeatTransferCoefficient;
    }

    SystemInterface fluid = getInletStream().getFluid();
    double rho = fluid.getDensity("kg/m3");
    double mu = fluid.getViscosity("kg/msec");
    double k = 0.025; // Default thermal conductivity for gas [W/(m·K)]
    double Pr = 0.7; // Default Prandtl number for gas

    // Estimate from fluid properties if available
    double Cp = fluid.getCp("J/kgK");
    if (Cp > 0 && mu > 0 && k > 0) {
      Pr = mu * Cp / k;
    }

    double velocity = massFlow / (rho * area);
    double Re = rho * velocity * diameter / mu;

    if (Re < 2300) {
      // Laminar: Nu = 3.66
      return 3.66 * k / diameter;
    } else {
      // Turbulent: Dittus-Boelter Nu = 0.023 * Re^0.8 * Pr^0.3 (cooling)
      double Nu = 0.023 * Math.pow(Re, 0.8) * Math.pow(Pr, 0.3);
      return Nu * k / diameter;
    }
  }

  /**
   * Convert a phase mass flow to velocity without imposing a finite phase-presence threshold.
   *
   * @param massFlow phase mass flow rate in kg/s
   * @param holdup phase holdup
   * @param density phase density in kg/m3
   * @param area pipe cross-sectional area in m2
   * @param maximumMagnitude velocity magnitude limit in m/s
   * @return finite phase velocity in m/s, or zero for an absent phase
   */
  private double calculateFinitePhaseVelocity(double massFlow, double holdup, double density, double area,
      double maximumMagnitude) {
    if (massFlow == 0.0) {
      return 0.0;
    }
    if (!(holdup > 0.0) || !(density > 0.0) || !(area > 0.0)) {
      return 0.0;
    }
    double velocity = massFlow / (holdup * density * area);
    if (!Double.isFinite(velocity)) {
      return 0.0;
    }
    return Math.max(-maximumMagnitude, Math.min(maximumMagnitude, velocity));
  }

  /**
   * Calculate local liquid holdup using the selected NeqSim closure set and terrain effects.
   *
   * <p>
   * Supports multiple model types:
   * </p>
   * <ul>
   * <li>FULL: Momentum balance for stratified, film model for annular, and a slug closure</li>
   * <li>SIMPLIFIED: Empirical correlations with an optional minimum-slip constraint</li>
   * <li>DRIFT_FLUX: Original NeqSim drift-flux model</li>
   * </ul>
   *
   * @param sec Current section
   * @param prev Previous section (upstream)
   * @param mDotGas Gas mass flow rate [kg/s]
   * @param mDotLiq Liquid mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   * @return Array with [liquidHoldup, gasHoldup]
   */
  private double[] calculateLocalHoldup(TwoFluidSection sec, TwoFluidSection prev, double mDotGas, double mDotLiq,
      double area) {
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    double sigma = sec.getSurfaceTension();
    double inclination = sec.getInclination(); // radians
    double g = 9.81;

    // Conservative phase state owns phase presence. Flow direction does not determine phase
    // presence, so closures use mass-flow magnitudes while momentum transport retains its sign.
    // Only an exactly absent phase is single phase; closure epsilons must not create inventory.
    double gasMassFlowMagnitude = Math.abs(mDotGas);
    double liquidMassFlowMagnitude = Math.abs(mDotLiq);
    if (liquidMassFlowMagnitude == 0.0) {
      return new double[] { 0.0, 1.0 }; // Pure gas
    }
    if (gasMassFlowMagnitude == 0.0) {
      return new double[] { 1.0, 0.0 }; // Pure liquid
    }

    // Superficial velocities (based on total area)
    double vsG = gasMassFlowMagnitude / (area * rhoG);
    double vsL = liquidMassFlowMagnitude / (area * rhoL);
    double vMix = vsG + vsL;

    // No-slip holdup (input liquid fraction)
    double lambdaL = vsL / vMix;

    // Determine flow regime to select appropriate correlation
    FlowRegime regime = sec.getFlowRegime();

    // Get viscosities for momentum balance calculations
    double muL = sec.getLiquidViscosity();
    double muG = sec.getGasViscosity();

    double alphaL;

    // Select the literature-inspired NeqSim closure set. Historical enum and helper
    // names containing OLGA are retained for source and serialization compatibility.
    if (olgaModelType == OLGAModelType.FULL) {
      // ========== FULL CLOSURE SET ==========
      // Use flow-regime-specific literature correlations.

      Map<FlowRegime, Double> regimeWeights = sec.getRegimeWeights();
      if (regimeWeights == null) {
        alphaL = holdupForRegime(regime, vsG, vsL, rhoG, rhoL, muG, muL, sigma, inclination, lambdaL);
      } else {
        // On a transition the section is partly each regime; blending the closures removes the
        // step change a hard switch would impose on hold-up.
        alphaL = 0.0;
        for (Map.Entry<FlowRegime, Double> entry : regimeWeights.entrySet()) {
          alphaL += entry.getValue()
              * holdupForRegime(entry.getKey(), vsG, vsL, rhoG, rhoL, muG, muL, sigma, inclination, lambdaL);
        }
      }

      // Apply terrain accumulation enhancement
      alphaL = applyTerrainAccumulation(sec, prev, alphaL);

      // Apply minimum slip constraint. The bound is a statement that the slip ratio cannot fall
      // below a given value, inverted for the hold-up it implies, so it stays a slip statement at
      // every liquid loading. It deliberately does NOT include a correlation-based term: see
      // calculateAdaptiveMinimumHoldup.
      if (enforceMinimumSlip && minimumSlipApplies(inclination)) {
        double effectiveMin;
        if (useAdaptiveMinimumOnly) {
          effectiveMin = minimumSlipHoldup(vsG, vsL);
        } else {
          effectiveMin = minimumLiquidHoldup;
        }
        effectiveMin = Math.min(0.9, effectiveMin);

        if (alphaL < effectiveMin) {
          alphaL = effectiveMin;
        }
      }

    } else if (olgaModelType == OLGAModelType.SIMPLIFIED) {
      // ========== SIMPLIFIED CLOSURE SET ==========
      // Use empirical correlations with minimum slip

      // For gas-dominant systems, use stratified momentum balance
      boolean isStratified = (regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY);
      if (lambdaL < 0.1 || isStratified || regime == FlowRegime.ANNULAR) {
        alphaL = calculateStratifiedHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter, inclination);
      } else {
        // Higher liquid loading: use drift-flux with terrain correction
        alphaL = calculateDriftFluxHoldup(vsG, vsL, rhoG, rhoL, sigma, inclination);
      }

      // Apply terrain accumulation
      if (enableTerrainTracking) {
        alphaL = applyTerrainAccumulation(sec, prev, alphaL);
      }

      // Apply minimum slip constraint; see the parallel block above for why no correlation term
      // is included.
      if (enforceMinimumSlip && minimumSlipApplies(inclination)) {
        double effectiveMin;
        if (useAdaptiveMinimumOnly) {
          effectiveMin = minimumSlipHoldup(vsG, vsL);
        } else {
          effectiveMin = minimumLiquidHoldup;
        }
        effectiveMin = Math.min(0.9, effectiveMin);

        if (alphaL < effectiveMin) {
          alphaL = effectiveMin;
        }
      }

    } else {
      // ========== ORIGINAL DRIFT-FLUX MODEL ==========
      // For backward compatibility with original NeqSim behavior
      alphaL = calculateDriftFluxHoldup(vsG, vsL, rhoG, rhoL, sigma, inclination);
    }

    if (!Double.isFinite(alphaL)) {
      alphaL = lambdaL;
    }
    alphaL = Math.max(0.0, Math.min(1.0, alphaL));

    // Valley/peak terrain adjustments. The strength ramps with how definite the slope reversal is:
    // a hard threshold here steps the hold-up of a section as terrain drifts past it, which shows up
    // as a pressure drop for an undulation that has zero net elevation change.
    if (prev != null) {
      double inclinationChange = inclination - prev.getInclination();
      double magnitude = 0.3 * Math.min(Math.abs(inclinationChange), 0.2);
      double valleyStrength = slopeStrength(-prev.getInclination()) * slopeStrength(inclination);
      double peakStrength = slopeStrength(prev.getInclination()) * slopeStrength(-inclination);
      double factor = 1.0 + magnitude * (valleyStrength - peakStrength);

      if (factor > 1.0) {
        alphaL = Math.min(0.8, alphaL * factor); // Allow up to 80% in valleys
      } else {
        alphaL = Math.max(0.0, alphaL * factor);
      }
    }

    // Re-apply an explicitly requested fixed floor after terrain modifiers. Adaptive
    // and disabled-minimum modes deliberately have no absolute state floor.
    if (enforceMinimumSlip && !useAdaptiveMinimumOnly) {
      alphaL = Math.max(minimumLiquidHoldup, alphaL);
    }
    alphaL = Math.max(0.0, Math.min(1.0, alphaL));

    return new double[] { alphaL, 1.0 - alphaL };
  }

  /**
   * Whether the minimum-slip bound has a basis on a section of the given inclination.
   *
   * <p>
   * The bound states that the gas outruns the liquid by at least a given factor, which is a property of gas-driven
   * transport: the liquid lags because the gas is what moves it. On a downhill section gravity moves the liquid, the
   * slip ratio legitimately falls, and the bound has no basis - it simply overwrites the momentum balance with a
   * constant. Measured on a 5 km, 200 mm profile undulating by +/-30 m, it was binding on 39 of 42 downhill sections
   * while binding on none of the uphill or level ones, so on that line it was acting only where it does not apply.
   * </p>
   *
   * @param inclination section inclination, in radians
   * @return true where the bound applies
   */
  private static boolean minimumSlipApplies(double inclination) {
    return inclination >= 0.0;
  }

  /**
   * Lowest liquid holdup consistent with the minimum slip ratio.
   *
   * <p>
   * The bound states that the gas moves at least {@code minimumSlipFactor} times faster than the liquid. Writing that
   * out, {@code S = v_SG * alphaL / (v_SL * (1 - alphaL))}, and solving for the holdup gives
   * {@code alphaL >= X / (1 + X)} with {@code X = S * v_SL / v_SG}. The result is below one at every liquid loading,
   * goes to zero with the liquid supply, and goes to one as the gas supply vanishes, which is what a liquid-full line
   * at no gas flow should return.
   * </p>
   *
   * <p>
   * The form previously used, {@code alphaL >= lambdaL * S}, is the same statement only in the lean-gas limit. Its
   * exact slip ratio is {@code S * v_SG / (v_SG + v_SL * (1 - S))}, which diverges as {@code v_SL} approaches
   * {@code v_SG / (S - 1)} and exceeds unity as a holdup beyond {@code lambdaL > 1 / S}. Past that point the bound was
   * no longer a slip statement but the clamp it was truncated to: on the Tengesdal (2002) severe-slugging facility, at
   * a no-slip fraction of 0.33 and a slip factor of 2, it pinned every section of the flowline and riser at the 0.9
   * clamp, so the whole line held a constant hold-up and the momentum balance was not used at all.
   * </p>
   *
   * @param vsG superficial gas velocity, in m/s
   * @param vsL superficial liquid velocity, in m/s
   * @return the minimum liquid holdup, between zero and one
   */
  private double minimumSlipHoldup(double vsG, double vsL) {
    if (vsL <= 0.0) {
      return 0.0;
    }
    if (vsG <= 0.0) {
      return 1.0;
    }
    double ratio = minimumSlipFactor * vsL / vsG;
    return ratio / (1.0 + ratio);
  }

  /**
   * How definitely a section slopes upward, ramped over the near-horizontal band.
   *
   * <p>
   * Zero at or below horizontal, one once the slope is clearly upward. Used so a slope reversal enters and leaves the
   * valley and peak corrections continuously rather than at a threshold.
   * </p>
   *
   * @param inclination section inclination, in radians
   * @return a weight between zero and one
   */
  private static double slopeStrength(double inclination) {
    double lower = 0.02;
    double upper = 0.08;
    if (inclination <= lower) {
      return 0.0;
    }
    if (inclination >= upper) {
      return 1.0;
    }
    return (inclination - lower) / (upper - lower);
  }

  /**
   * Liquid holdup from the closure belonging to a single flow regime.
   *
   * <p>
   * Split out of the holdup calculation so a section sitting on a transition can evaluate more than one closure and
   * blend the results, rather than switching between them at a point.
   * </p>
   *
   * @param regime the regime whose closure is evaluated
   * @param vsG superficial gas velocity, in m/s
   * @param vsL superficial liquid velocity, in m/s
   * @param rhoG gas density, in kg/m3
   * @param rhoL liquid density, in kg/m3
   * @param muG gas viscosity, in Pa.s
   * @param muL liquid viscosity, in Pa.s
   * @param sigma surface tension, in N/m
   * @param inclination section inclination, in radians
   * @param lambdaL no-slip liquid fraction
   * @return liquid holdup for that regime
   */
  private double holdupForRegime(FlowRegime regime, double vsG, double vsL, double rhoG, double rhoL, double muG,
      double muL, double sigma, double inclination, double lambdaL) {
    double g = 9.81;

    if (regime == FlowRegime.ANNULAR) {
      if (enableAnnularFilmModel) {
        double[] annularResult = calculateAnnularHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter,
            inclination);
        return annularResult[0];
      }

      // Annular slip model: the gas core outruns the film, so S = vG/vL is between about 1.5 and 4
      // and holdup follows alphaL = lambdaL / (S - (S-1)*lambdaL).
      double vsgRef = 8.0;
      double velocityRatio = Math.max(0.5, Math.min(4.0, vsG / Math.max(vsgRef, 0.1)));
      double baseSlipRatio = 1.5;
      double maxSlipRatio = 4.0;
      double slipRatio = baseSlipRatio
          + (maxSlipRatio - baseSlipRatio) * Math.min(1.0, velocityRatio * velocityRatio / 4.0);

      double denominator = slipRatio - (slipRatio - 1.0) * lambdaL;
      double alphaL = denominator > 0.1 ? lambdaL / denominator : lambdaL;

      // A fixed wetting film is a user-selected physical model, not a universal
      // numerical phase floor. Apply it only in explicit fixed-floor mode.
      if (usesExplicitPhysicalFilmFloor()) {
        double filmHoldup = 4.0 * minimumFilmThickness / diameter;
        alphaL = Math.max(filmHoldup, alphaL);
      }
      return alphaL;
    }

    if (regime == FlowRegime.SLUG || regime == FlowRegime.CHURN) {
      return calculateSlugHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter, inclination);
    }

    if (regime == FlowRegime.DISPERSED_BUBBLE || regime == FlowRegime.BUBBLE) {
      double vSlip = 1.53 * Math.pow(g * sigma * (rhoL - rhoG) / (rhoL * rhoL), 0.25);
      double alphaL = vsL / (vsL + vsG + vSlip * (1.0 - lambdaL));
      return Math.max(lambdaL * 0.9, alphaL);
    }

    return calculateStratifiedHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter, inclination);
  }

  /**
   * Calculate a correlation-based minimum that vanishes continuously with liquid input.
   *
   * <p>
   * This is the Beggs and Brill horizontal holdup correlation, fitted to 1 to 1.5 inch air-water loops at
   * near-atmospheric pressure and no-slip liquid fractions at or above about 0.01. It is no longer used as a lower
   * bound on the solved holdup. On a 73.8 km 14-inch high-pressure gas-condensate line at a no-slip fraction near 0.008
   * it was binding in every section, so the reported holdup was the correlation rather than the momentum balance: about
   * three times the mechanistic value, which carried roughly twenty per cent onto the pressure drop through the mixture
   * density. Restricting it to the regimes without a mechanistic closure is not a remedy either, because that makes the
   * bound a discontinuous function of the flow map and a section flipping between annular and slug then steps between
   * no floor and the full correlation. The remaining bound is the scale-free no-slip multiple
   * {@code lambdaL * minimumSlipFactor}.
   * </p>
   *
   * <p>
   * The method is retained because the stratified closure uses it as the trace-liquid asymptote, where the momentum
   * balance degenerates and a correlation is the only available value.
   * </p>
   *
   * <p>
   * The epsilon regularizes only the Froude-number denominator. No lower bound is applied to {@code lambdaL}, so the
   * returned holdup tends to zero as the liquid superficial velocity tends to zero.
   * </p>
   *
   * @param lambdaL no-slip liquid fraction
   * @param froudeNumber mixture Froude number
   * @param regime local flow regime
   * @return adaptive liquid-holdup lower bound
   */
  private double calculateAdaptiveMinimumHoldup(double lambdaL, double froudeNumber, FlowRegime regime) {
    if (lambdaL <= 0.0) {
      return 0.0;
    }
    double regularizedFroude = Math.max(CLOSURE_DENOMINATOR_EPSILON, froudeNumber);
    double adaptiveMin;
    if (regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY) {
      adaptiveMin = 0.98 * Math.pow(lambdaL, 0.4846) / Math.pow(regularizedFroude, 0.0868);
    } else if (regime == FlowRegime.ANNULAR) {
      adaptiveMin = 1.065 * Math.pow(lambdaL, 0.5824) / Math.pow(regularizedFroude, 0.0609);
      if (usesExplicitPhysicalFilmFloor()) {
        adaptiveMin = Math.max(4.0 * minimumFilmThickness / diameter, adaptiveMin);
      }
    } else if (regime == FlowRegime.SLUG || regime == FlowRegime.CHURN) {
      adaptiveMin = 0.845 * Math.pow(lambdaL, 0.5351) / Math.pow(regularizedFroude, 0.0173);
    } else {
      adaptiveMin = 1.065 * Math.pow(lambdaL, 0.5824) / Math.pow(regularizedFroude, 0.0609);
    }
    return Math.max(0.0, Math.min(1.0, adaptiveMin));
  }

  /** @return true when the user explicitly selected a non-adaptive physical film floor. */
  private boolean usesExplicitPhysicalFilmFloor() {
    return enforceMinimumSlip && !useAdaptiveMinimumOnly && minimumLiquidHoldup > 0.0 && minimumFilmThickness > 0.0;
  }

  /**
   * Calculate holdup using original drift-flux model.
   *
   * <p>
   * Uses the drift-flux model: v_G = C_0 * v_m + v_gj where:
   * </p>
   * <ul>
   * <li>C_0 = distribution coefficient (~1.0-1.2 for pipe flow)</li>
   * <li>v_gj = drift velocity (gas rises relative to mixture)</li>
   * </ul>
   *
   * @param vsG Gas superficial velocity [m/s]
   * @param vsL Liquid superficial velocity [m/s]
   * @param rhoG Gas density [kg/m³]
   * @param rhoL Liquid density [kg/m³]
   * @param sigma Surface tension [N/m]
   * @param inclination Pipe inclination [radians]
   * @return Liquid holdup [-]
   */
  private double calculateDriftFluxHoldup(double vsG, double vsL, double rhoG, double rhoL, double sigma,
      double inclination) {

    double g = 9.81;
    double vMix = vsG + vsL;
    double lambdaL = vsL / vMix;
    double dRho = rhoL - rhoG;

    // Distribution coefficient
    double C0 = 1.2;

    // Terminal rise velocity (Harmathy, 1960)
    double vGj0 = 1.53 * Math.pow(g * sigma * dRho / (rhoL * rhoL), 0.25);

    // Inclination correction factor
    double sinTheta = Math.sin(inclination);
    double cosTheta = Math.cos(inclination);

    double fTheta;
    if (inclination >= 0) {
      fTheta = cosTheta + 1.2 * sinTheta;
    } else {
      fTheta = cosTheta + 0.3 * Math.abs(sinTheta);
    }
    fTheta = Math.max(0.1, fTheta);

    double vGj = vGj0 * fTheta;

    // Pipe diameter effect
    double Eo = g * dRho * diameter * diameter / sigma;
    if (Eo > 40) {
      double inclinationFactor = Math.max(0.2, Math.abs(sinTheta) + 0.2);
      vGj = 0.35 * Math.sqrt(g * diameter * dRho / rhoL) * fTheta * inclinationFactor;
    }

    // Froude number effect
    double Fr = vMix * vMix / (g * diameter);
    if (Fr > 1.0) {
      double frFactor = 1.0 / (1.0 + 0.1 * (Fr - 1.0));
      vGj = vGj * frFactor;
    }

    // Gas velocity from drift-flux
    double vG = C0 * vMix + vGj;

    // Liquid holdup from mass balance
    double alphaG = Math.max(0.0, Math.min(1.0, vsG / vG));
    double driftFluxHoldup = 1.0 - alphaG;

    // C0 and vGj describe interaction with a continuous second phase and otherwise
    // leave a finite liquid holdup as lambdaL -> 0. Smoothly withdraw that slip
    // correction in the model's trace-liquid range. This regularizes the closure only;
    // it does not truncate or seed the conservative phase mass.
    double twoPhaseWeight = lambdaL / (lambdaL + DRIFT_FLUX_DEGENERACY_TRANSITION);
    double liquidHoldup = lambdaL + twoPhaseWeight * (driftFluxHoldup - lambdaL);
    return Math.max(0.0, Math.min(1.0, liquidHoldup));
  }

  /**
   * Calculate stratified-flow liquid holdup using a common-pressure-gradient momentum balance.
   *
   * <p>
   * The liquid level is determined by a momentum balance between the phases. At equilibrium, the pressure gradient is
   * equal in both phases. Exact single-phase endpoints are handled before this two-phase closure is evaluated.
   * </p>
   *
   * <p>
   * The momentum balance accounts for:
   * </p>
   * <ul>
   * <li>Wall friction in each phase (τ_wG, τ_wL)</li>
   * <li>Interfacial friction between phases (τ_i)</li>
   * <li>Gravity component for inclined pipes</li>
   * </ul>
   *
   * <p>
   * Uses exact circular segment geometry for wetted perimeters and areas, which is critical for accurate holdup
   * prediction at low liquid fractions (lean gas systems).
   * </p>
   *
   * <p>
   * Reference: Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA: Theory and Application" SPE Production
   * Engineering, May 1991, pp. 171-180
   * </p>
   *
   * @param vsG Gas superficial velocity [m/s]
   * @param vsL Liquid superficial velocity [m/s]
   * @param rhoG Gas density [kg/m³]
   * @param rhoL Liquid density [kg/m³]
   * @param muG Gas dynamic viscosity [Pa·s]
   * @param muL Liquid dynamic viscosity [Pa·s]
   * @param sigma Surface tension [N/m]
   * @param D Pipe diameter [m]
   * @param theta Pipe inclination [radians]
   * @return Equilibrium liquid holdup [-]
   */
  private double calculateStratifiedHoldupOLGA(double vsG, double vsL, double rhoG, double rhoL, double muG, double muL,
      double sigma, double D, double theta) {
    double vMix = vsG + vsL;
    double lambdaL = vsL / vMix;
    if (lambdaL <= 0.0) {
      return 0.0;
    }
    if (lambdaL >= 1.0) {
      return 1.0;
    }

    double froudeNumber = vMix * vMix / (9.81 * D);
    double asymptoticHoldup = calculateAdaptiveMinimumHoldup(lambdaL, froudeNumber, FlowRegime.STRATIFIED_SMOOTH);
    if (lambdaL <= STRATIFIED_TRACE_LIQUID_TRANSITION) {
      return asymptoticHoldup;
    }

    double lowerBound = Math.max(CLOSURE_SOLVER_HOLDUP_EPSILON, lambdaL * 1.0e-4);
    double upperBound = 1.0 - CLOSURE_SOLVER_HOLDUP_EPSILON;

    // Bisection rather than a damped Newton step with an absolute residual tolerance: the residual is
    // a pressure gradient, so any fixed threshold on it means something different on every line.
    double residualLow = calculateStratifiedMomentumResidual(lowerBound, vsG, vsL, rhoG, rhoL, muG, muL, D, theta);
    double residualHigh = calculateStratifiedMomentumResidual(upperBound, vsG, vsL, rhoG, rhoL, muG, muL, D, theta);

    if (!Double.isFinite(residualLow) || !Double.isFinite(residualHigh) || residualLow * residualHigh > 0.0) {
      return Math.max(0.0, Math.min(1.0, Math.max(lambdaL, asymptoticHoldup)));
    }

    double low = lowerBound;
    double high = upperBound;
    for (int iter = 0; iter < 80; iter++) {
      double mid = 0.5 * (low + high);
      double residualMid = calculateStratifiedMomentumResidual(mid, vsG, vsL, rhoG, rhoL, muG, muL, D, theta);
      if (!Double.isFinite(residualMid)) {
        break;
      }

      if (residualLow * residualMid <= 0.0) {
        high = mid;
        residualHigh = residualMid;
      } else {
        low = mid;
        residualLow = residualMid;
      }

      if (high - low < 1.0e-12) {
        break;
      }
    }

    return Math.max(0.0, Math.min(1.0, 0.5 * (low + high)));
  }

  /** Calculate the common-pressure-gradient residual for a stratified section. */
  private double calculateStratifiedMomentumResidual(double alphaL, double vsG, double vsL, double rhoG, double rhoL,
      double muG, double muL, double D, double theta) {
    double area = Math.PI * D * D / 4.0;
    double alphaG = 1.0 - alphaL;
    double liquidArea = alphaL * area;
    double gasArea = alphaG * area;
    double areaEpsilon = CLOSURE_DENOMINATOR_EPSILON * area;
    double regularizedLiquidArea = Math.max(areaEpsilon, liquidArea);
    double regularizedGasArea = Math.max(areaEpsilon, gasArea);
    double beta = calculateStratifiedCentralAngle(alphaL);

    double liquidPerimeter = D * beta / 2.0;
    double gasPerimeter = D * (Math.PI - beta / 2.0);
    double interfaceWidth = D * Math.sin(beta / 2.0);
    // Taitel and Dukler hydraulic diameters: the interface is a shear surface for the gas but not a
    // wall for the liquid, so it enters the gas perimeter only.
    double liquidHydraulicDiameter = 4.0 * liquidArea / Math.max(CLOSURE_DENOMINATOR_EPSILON, liquidPerimeter);
    double gasHydraulicDiameter = 4.0 * gasArea / Math.max(CLOSURE_DENOMINATOR_EPSILON, gasPerimeter + interfaceWidth);

    double liquidVelocity = vsL / Math.max(CLOSURE_DENOMINATOR_EPSILON, alphaL);
    double gasVelocity = vsG / Math.max(CLOSURE_DENOMINATOR_EPSILON, alphaG);
    double liquidReynolds = rhoL * Math.abs(liquidVelocity) * liquidHydraulicDiameter
        / Math.max(CLOSURE_DENOMINATOR_EPSILON, muL);
    double gasReynolds = rhoG * Math.abs(gasVelocity) * gasHydraulicDiameter
        / Math.max(CLOSURE_DENOMINATOR_EPSILON, muG);
    double liquidFriction = liquidReynolds < 2000.0 ? 16.0 / Math.max(CLOSURE_DENOMINATOR_EPSILON, liquidReynolds)
        : 0.046 / Math.pow(liquidReynolds, 0.2);
    double gasFriction = gasReynolds < 2000.0 ? 16.0 / Math.max(CLOSURE_DENOMINATOR_EPSILON, gasReynolds)
        : 0.046 / Math.pow(gasReynolds, 0.2);
    double interfacialFriction = gasFriction * (1.0 + 75.0 * alphaL);

    double liquidWallShear = liquidFriction * rhoL * liquidVelocity * Math.abs(liquidVelocity) / 2.0;
    double gasWallShear = gasFriction * rhoG * gasVelocity * Math.abs(gasVelocity) / 2.0;
    double relativeVelocity = gasVelocity - liquidVelocity;
    double interfacialShear = interfacialFriction * rhoG * relativeVelocity * Math.abs(relativeVelocity) / 2.0;

    double gasPressureGradient = gasWallShear * gasPerimeter / regularizedGasArea
        + interfacialShear * interfaceWidth / regularizedGasArea + rhoG * 9.81 * Math.sin(theta);
    double liquidPressureGradient = liquidWallShear * liquidPerimeter / regularizedLiquidArea
        - interfacialShear * interfaceWidth / regularizedLiquidArea + rhoL * 9.81 * Math.sin(theta);
    return gasPressureGradient - liquidPressureGradient;
  }

  /** Solve {@code beta - sin(beta) = 2*pi*alphaL} without a finite geometry floor. */
  private double calculateStratifiedCentralAngle(double alphaL) {
    if (alphaL <= 0.0) {
      return 0.0;
    }
    if (alphaL >= 1.0) {
      return 2.0 * Math.PI;
    }
    if (alphaL > 0.5) {
      return 2.0 * Math.PI - calculateStratifiedCentralAngle(1.0 - alphaL);
    }

    double target = 2.0 * Math.PI * alphaL;
    double beta = Math.cbrt(6.0 * target);
    for (int iter = 0; iter < 20; iter++) {
      double residual;
      double derivative;
      if (beta < 1.0e-3) {
        double beta2 = beta * beta;
        double beta3 = beta2 * beta;
        residual = beta3 / 6.0 - beta3 * beta2 / 120.0 + beta3 * beta2 * beta2 / 5040.0 - target;
        derivative = beta2 / 2.0 - beta2 * beta2 / 24.0 + beta2 * beta2 * beta2 / 720.0;
      } else {
        residual = beta - Math.sin(beta) - target;
        derivative = 1.0 - Math.cos(beta);
      }
      if (Math.abs(residual) <= Math.max(CLOSURE_DENOMINATOR_EPSILON, target * 1.0e-12)) {
        break;
      }
      beta -= residual / Math.max(CLOSURE_DENOMINATOR_EPSILON, derivative);
      beta = Math.max(CLOSURE_SOLVER_HOLDUP_EPSILON, Math.min(Math.PI, beta));
    }
    return beta;
  }

  /**
   * Calculate liquid holdup for annular flow using a literature-inspired film model.
   *
   * <p>
   * In annular flow, liquid exists as a thin film on the pipe wall and as entrained droplets in the gas core. This
   * closure uses:
   * </p>
   * <ul>
   * <li>Film flow momentum balance</li>
   * <li>Entrainment/deposition equilibrium</li>
   * <li>An optional, explicitly selected minimum film thickness constraint</li>
   * </ul>
   *
   * <p>
   * Reference: Bendiksen et al. (1991) and Ishii-Mishima entrainment correlations. This implementation does not claim
   * numerical equivalence with a commercial simulator.
   * </p>
   *
   * @param vsG Gas superficial velocity [m/s]
   * @param vsL Liquid superficial velocity [m/s]
   * @param rhoG Gas density [kg/m³]
   * @param rhoL Liquid density [kg/m³]
   * @param muG Gas dynamic viscosity [Pa·s]
   * @param muL Liquid dynamic viscosity [Pa·s]
   * @param sigma Surface tension [N/m]
   * @param D Pipe diameter [m]
   * @param theta Pipe inclination [radians]
   * @return Array with [total liquid holdup, film holdup, entrained fraction]
   */
  private double[] calculateAnnularHoldupOLGA(double vsG, double vsL, double rhoG, double rhoL, double muG, double muL,
      double sigma, double D, double theta) {

    double A = Math.PI * D * D / 4.0;
    double lambdaL = vsL / Math.max(CLOSURE_DENOMINATOR_EPSILON, vsG + vsL);
    boolean applyPhysicalFilmFloor = usesExplicitPhysicalFilmFloor();

    // Calculate entrainment fraction using Ishii-Mishima correlation
    // E = tanh(7.25e-7 * We^1.25 * Re_L^0.25)
    // where We = ρ_G * v_SG² * D / σ (gas Weber number)
    // and Re_L = ρ_L * v_SL * D / μ_L (liquid Reynolds number)

    // Entrainment fraction from the selected NeqSim closure set
    double entrainment = entrainedLiquidFraction(vsG, vsL, rhoG, rhoL, muL, sigma, D);

    // Film superficial velocity (liquid not entrained)
    double vsLF = vsL * (1.0 - entrainment);

    // A minimum physical film is optional and user-selected. In adaptive mode the
    // film thickness is initialized from available liquid and may vanish continuously.
    double minFilmArea = applyPhysicalFilmFloor ? Math.PI * D * minimumFilmThickness : 0.0;
    double minFilmHoldup = minFilmArea / A;

    // Calculate film holdup from momentum balance
    // For thin film: τ_i = τ_wL where interfacial shear balances wall friction
    // This gives: δ/D = (f_L * ρ_L * v_LF²) / (f_i * ρ_G * v_G² * 4)

    double vG = vsG / 0.95; // Approximate gas core velocity
    double ReG = rhoG * vG * D / muG;
    double fG = (ReG < 2000) ? 16.0 / Math.max(ReG, 1.0) : 0.046 / Math.pow(ReG, 0.2);

    // Interfacial friction factor for annular flow (Wallis correlation)
    // f_i = f_G * (1 + 300 * δ/D)
    double minimumDeltaOverD = applyPhysicalFilmFloor ? minimumFilmThickness / D : 0.0;
    double deltaOverD = Math.max(CLOSURE_SOLVER_HOLDUP_EPSILON, Math.max(minimumDeltaOverD, lambdaL / 4.0));

    // Iterative solution for film thickness
    for (int iter = 0; iter < 10; iter++) {
      double filmHoldup = 4.0 * deltaOverD * (1.0 - deltaOverD);
      double regularizedFilmHoldup = Math.max(CLOSURE_DENOMINATOR_EPSILON, filmHoldup);
      double vLF = vsLF / regularizedFilmHoldup;
      double ReLF = rhoL * Math.abs(vLF) * (2.0 * deltaOverD * D) / muL;
      double fLF = (ReLF < 2000) ? 16.0 / Math.max(ReLF, 1.0) : 0.046 / Math.pow(ReLF, 0.2);

      // Interfacial friction with roughness correction
      double fi = fG * (1.0 + 300.0 * deltaOverD);

      // Film momentum balance along the pipe axis. For a thin film the wetted and interfacial
      // perimeters are both close to pi*D and the film area is close to pi*D*delta, so the balance
      // reduces to tau_i = tau_wL + rhoL * g * sin(theta) * delta. Gravity thickens the film on an
      // uphill section and thins it on a downhill one, which is how terrain enters an annular
      // closure; omitting it left the annular regime with no inclination dependence at all and the
      // terrain response had to be supplied by an empirical multiplier applied afterwards.
      double filmThickness = deltaOverD * D;
      double gravityShear = rhoL * 9.81 * Math.sin(theta) * filmThickness;
      double drivingShear = fLF * rhoL * vLF * vLF / 2.0 + gravityShear;
      double tauRatio = 2.0 * Math.max(0.0, drivingShear) / (fi * rhoG * vG * vG + 1e-10);

      // Update film thickness estimate
      double newDeltaOverD = deltaOverD * Math.sqrt(tauRatio);
      newDeltaOverD = Math.max(minimumDeltaOverD, Math.min(0.2, newDeltaOverD));

      if (Math.abs(newDeltaOverD - deltaOverD) < 1e-6) {
        break;
      }
      deltaOverD = 0.5 * deltaOverD + 0.5 * newDeltaOverD;
    }

    // Final film holdup
    double filmHoldup = 4.0 * deltaOverD * (1.0 - deltaOverD);
    if (applyPhysicalFilmFloor) {
      filmHoldup = Math.max(minFilmHoldup, filmHoldup);
    }

    // Entrained droplet holdup (homogeneous with gas core)
    // v_droplet ≈ v_gas (droplets carried by gas)
    double vsLE = vsL * entrainment;
    double dropletHoldup = vsLE / Math.max(CLOSURE_DENOMINATOR_EPSILON, vsG + vsLE);

    // Total liquid holdup
    double totalHoldup = filmHoldup + dropletHoldup * (1.0 - filmHoldup);

    // Apply slip ratio model for annular flow
    // In annular flow, gas flows faster than liquid film (slip ratio S = vG/vL > 1)
    // Holdup formula: αL = λL / (λL + S*(1-λL))
    // Typical slip ratios for annular flow: S = 1.5 to 4.0
    double vsgRef = 8.0;
    double velocityRatio = Math.min(4.0, vsG / Math.max(vsgRef, 0.1));

    // Slip ratio increases with gas velocity
    double baseSlipRatio = 1.5;
    double maxSlipRatio = 4.0;
    double slipRatio = baseSlipRatio
        + (maxSlipRatio - baseSlipRatio) * Math.min(1.0, velocityRatio * velocityRatio / 4.0);

    // Calculate holdup using slip model: αL = λL / (S - (S-1)*λL)
    double slipBasedHoldup;
    double denominator = slipRatio - (slipRatio - 1.0) * lambdaL;
    if (denominator > 0.1) {
      slipBasedHoldup = lambdaL / denominator;
    } else {
      slipBasedHoldup = lambdaL;
    }

    // Use physics-based calculation, with slip model as minimum
    // The film model can under-predict when gas velocity is high
    totalHoldup = Math.max(totalHoldup, slipBasedHoldup);
    if (applyPhysicalFilmFloor) {
      totalHoldup = Math.max(4.0 * minimumFilmThickness / D, totalHoldup);
    }
    totalHoldup = Math.max(0.0, Math.min(0.9, totalHoldup));

    // Store entrainment for diagnostic purposes
    this.annularEntrainmentFraction = entrainment;

    return new double[] { totalHoldup, filmHoldup, entrainment };
  }

  /**
   * Calculate liquid holdup for slug flow using a literature-inspired unit-cell model.
   *
   * <p>
   * Slug flow is represented as a sequence of liquid slugs separated by Taylor bubbles. The average holdup is
   * determined by the slug body holdup, the film holdup under the Taylor bubble, and the slug length ratio.
   * </p>
   *
   * <p>
   * The film under the Taylor bubble is solved with the same wall-film balance the annular closure uses,
   * {@code tau_i = tau_wL + rhoL*g*sin(theta)*delta}, rather than being taken as a constant multiple of the no-slip
   * fraction. That balance is where terrain physically enters a slug unit: gravity thickens the film on an uphill
   * section and thins it on a downhill one. Without it the closure had no usable inclination response at all, and what
   * response remained pointed the wrong way, because the drift velocity grows with upward inclination and enters the
   * DENOMINATOR of the slug length ratio. Measured on a 5 km, 200 mm profile undulating by +/-30 m, uphill sections
   * returned 0.0328 against 0.0493 downhill, the opposite of the accumulation a pipeline shows.
   * </p>
   *
   * <p>
   * The balance is not always solvable at the position it is asked about. In a riser the film weight exceeds the gas
   * shear by more than two orders of magnitude and the iteration stops at its thickness clamp, returning a film hold-up
   * of 0.64 that is the clamp rather than a Taylor bubble. Taken alone that pinned the average hold-up of a riser slug
   * unit at the 0.9 clamp of this method, so the riser of the Tengesdal (2002) benchmark held a constant 0.9 and could
   * not drain. The film is therefore also bounded by
   * {@link #taylorBubbleFilmHoldup(double, double, double, double, double)}, which states liquid conservation across
   * the slug unit for a gravity-drained film and does have a root at any inclination; the smaller of the two is used.
   * </p>
   *
   * <p>
   * The unit cell is kept rather than replaced by the drift-flux form {@code alpha_G = v_sG / (C0*v_m + v_d)}. Drift
   * flux also corrects the direction, but with {@code C0 > 1} and a finite drift velocity the gas fraction stays below
   * one even at zero liquid input, so it invents inventory and fails the trace-liquid degeneracy pinned by
   * {@code TwoFluidPipePhaseDegeneracyTest}. Weighting the drift by {@code (1 - alpha_G)^n} in the Zuber-Findlay manner
   * restores the degeneracy but suppresses the drift by more than an order of magnitude at the liquid fractions of
   * interest, removing the response again. The slug length ratio of the unit cell vanishes with the liquid supply, and
   * the annular film balance vanishes with it too, so the unit cell degenerates correctly while carrying the gravity
   * term.
   * </p>
   *
   * @param vsG Gas superficial velocity [m/s]
   * @param vsL Liquid superficial velocity [m/s]
   * @param rhoG Gas density [kg/m³]
   * @param rhoL Liquid density [kg/m³]
   * @param muG Gas dynamic viscosity [Pa·s]
   * @param muL Liquid dynamic viscosity [Pa·s]
   * @param sigma Surface tension [N/m]
   * @param D Pipe diameter [m]
   * @param theta Pipe inclination [radians]
   * @return Slug flow average liquid holdup [-]
   */
  private double calculateSlugHoldupOLGA(double vsG, double vsL, double rhoG, double rhoL, double muG, double muL,
      double sigma, double D, double theta) {

    double g = 9.81;
    double vMix = vsG + vsL;

    // Slug body holdup using Gregory correlation
    // H_LS = 1 / (1 + (v_m / 8.66)^1.39)
    double slugBodyHoldup = 1.0 / (1.0 + Math.pow(vMix / 8.66, 1.39));
    slugBodyHoldup = Math.max(0.5, Math.min(0.98, slugBodyHoldup));

    double dRho = rhoL - rhoG;
    double C0 = 1.2; // Distribution coefficient

    // Bendiksen (1984) drift velocity: one expression over the whole inclination range, the
    // horizontal and vertical coefficients projected onto the pipe axis, so a negative inclination
    // reduces the drift through sin(theta) instead of through a separate down-flow branch.
    double driftScale = Math.sqrt(g * D * Math.max(0.0, dRho) / Math.max(CLOSURE_DENOMINATOR_EPSILON, rhoL));
    double vD = driftScale
        * (SLUG_DRIFT_HORIZONTAL_COEFFICIENT * Math.cos(theta) + SLUG_DRIFT_VERTICAL_COEFFICIENT * Math.sin(theta));

    // Taylor bubble velocity
    double vTB = C0 * vMix + vD;

    double lambdaL = vsL / vMix;
    // Wall film under the Taylor bubble. Two independent statements bound it: the annular wall-film
    // momentum balance, and liquid conservation across the slug unit with a gravity-drained film.
    // The momentum balance is written for a film carried upward by the gas core, so on a steep
    // section, where the film weight exceeds the gas shear, it has no root and stops at its
    // thickness clamp. Liquid conservation still has one, so the film is taken as the smaller of the
    // two: a film cannot be thicker than the shear that supports it allows, nor thicker than the
    // liquid the unit cell can supply against its own drainage.
    double annularFilm = calculateAnnularHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, D, theta)[1];
    double conservedFilm = taylorBubbleFilmHoldup(vMix, vTB, slugBodyHoldup, D, theta);
    double filmHoldup = Math.max(0.1 * lambdaL, Math.min(annularFilm, conservedFilm));
    // The film cannot be as liquid-rich as the slug body it separates; the margin keeps the slug
    // length ratio below its own denominator.
    filmHoldup = Math.min(filmHoldup, SLUG_FILM_HOLDUP_FRACTION_OF_BODY * slugBodyHoldup);

    // Slug unit composition using mass balance
    // Slug length ratio (Ls/Lu) from Dukler-Hubbard
    double slugLengthRatio = vsL / (vTB * (slugBodyHoldup - filmHoldup) + 1e-10);
    slugLengthRatio = Math.max(0.0, Math.min(0.9, slugLengthRatio));

    // Average holdup = Ls/Lu * H_LS + (1 - Ls/Lu) * H_film
    double avgHoldup = slugLengthRatio * slugBodyHoldup + (1.0 - slugLengthRatio) * filmHoldup;

    return Math.max(0.0, Math.min(0.9, avgHoldup));
  }

  /**
   * Liquid hold-up of the film around a Taylor bubble, from liquid conservation across the slug unit.
   *
   * <p>
   * In a frame moving with the bubble nose the liquid entering the film from the slug ahead must equal the liquid the
   * film carries, {@code H_film (v_TB - v_film) = H_LS (v_TB - v_m)}, and the film drains under its own weight at the
   * Brotz velocity {@code v_film = -9.916 sqrt(g D |sin(theta)| (1 - sqrt(1 - H_film)))}. This is the closure of Taitel
   * and Barnea (1990); it is the statement the annular wall-film balance cannot make, because that balance assumes the
   * film is dragged along with the gas and therefore has no solution once the film weight exceeds the gas shear.
   * </p>
   *
   * <p>
   * The left-hand side increases monotonically with the film hold-up while the right-hand side is fixed, so the root is
   * unique and is found by bisection. On a level section the drainage term vanishes and the closure reduces to the
   * classical no-drainage unit cell {@code H_film = H_LS (1 - v_m / v_TB)}. A Taylor bubble that does not overtake the
   * mixture carries no film.
   * </p>
   *
   * @param vMix mixture velocity in m/s
   * @param vTB Taylor bubble translational velocity in m/s
   * @param slugBodyHoldup liquid hold-up of the slug body, dimensionless and in (0, 1]
   * @param diameterM pipe inside diameter in m
   * @param theta pipe inclination in radians
   * @return film liquid hold-up, dimensionless and in [0, slugBodyHoldup]
   */
  private double taylorBubbleFilmHoldup(double vMix, double vTB, double slugBodyHoldup, double diameterM,
      double theta) {
    double required = slugBodyHoldup * (vTB - vMix);
    if (required <= 0.0) {
      return 0.0;
    }
    double drainageScale = SLUG_FALLING_FILM_COEFFICIENT * Math.sqrt(9.81 * diameterM * Math.abs(Math.sin(theta)));
    double low = 0.0;
    double high = slugBodyHoldup;
    for (int iteration = 0; iteration < SLUG_FILM_SOLVER_ITERATIONS; iteration++) {
      double middle = 0.5 * (low + high);
      double drainage = drainageScale * Math.sqrt(Math.max(0.0, 1.0 - Math.sqrt(Math.max(0.0, 1.0 - middle))));
      if (middle * (vTB + drainage) < required) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Calculate terrain-induced liquid accumulation with empirical NeqSim modifiers.
   *
   * <p>
   * This implements empirical NeqSim terrain holdup modifiers which account for:
   * </p>
   * <ul>
   * <li><b>Low Point Accumulation:</b> Liquid pools in valleys due to gravity. The volume of accumulated liquid depends
   * on the depth of the valley and gas carrying capacity.</li>
   * <li><b>Uphill Liquid Fallback:</b> When gas velocity is below critical velocity, liquid falls back. The critical
   * velocity is based on Taitel-Dukler flooding criterion.</li>
   * <li><b>Downhill Drainage:</b> Liquid accelerates on downhill sections, reducing holdup.</li>
   * <li><b>Riser Base Accumulation:</b> Special treatment for transition from horizontal/downhill to steep uphill
   * (severe slugging potential).</li>
   * <li><b>Terrain-Induced Slugging:</b> When low-point holdup exceeds critical value, slugs form and surge out.</li>
   * </ul>
   *
   * <p>
   * Reference: Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA: Theory and Application" SPE Production
   * Engineering, May 1991, pp. 171-180
   * </p>
   *
   * @param sec Current pipe section
   * @param prev Previous pipe section
   * @param baseHoldup Base holdup from flow regime correlation
   * @return Enhanced holdup accounting for terrain effects
   */
  private double applyTerrainAccumulation(TwoFluidSection sec, TwoFluidSection prev, double baseHoldup) {

    if (!enableTerrainTracking) {
      return baseHoldup;
    }

    double g = 9.81;
    double inclination = sec.getInclination();
    double prevInclination = (prev != null) ? prev.getInclination() : inclination;

    // Get flow properties
    double vsG = sec.getSuperficialGasVelocity();
    double vsL = sec.getSuperficialLiquidVelocity();
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    double dRho = rhoL - rhoG;
    double sigma = sec.getSurfaceTension();

    // Classify terrain features
    boolean isLowPoint = prevInclination < -0.01 && inclination > 0.01;
    boolean isHighPoint = prevInclination > 0.01 && inclination < -0.01;
    boolean isUphill = inclination > 0.02; // > ~1 degree uphill
    boolean isDownhill = inclination < -0.02; // > ~1 degree downhill
    boolean isSteepUphill = inclination > Math.toRadians(15); // > 15 degrees
    boolean isRiserBase = prevInclination < Math.toRadians(5) && inclination > Math.toRadians(30);

    double enhancedHoldup = baseHoldup;

    // ========== LOW POINT ACCUMULATION ==========
    if (isLowPoint || sec.isLowPoint()) {
      // At low points, liquid accumulates due to gravity pooling
      // NeqSim uses a modified Froude-number screen for accumulation

      // Elevation change into the low point
      double elevChange = (prev != null) ? Math.abs(sec.getElevation() - prev.getElevation()) : 0;

      // Gas Froude number - indicates ability to sweep liquid from low point
      // Fr = vG / sqrt(g * D * (rhoL - rhoG) / rhoG)
      double froudeG = vsG / Math.sqrt(g * diameter * dRho / Math.max(rhoG, 1.0));

      // Liquid accumulation factor increases as Froude decreases
      // Below Fr ~ 1.5, significant accumulation occurs
      // ENHANCED: Use stronger accumulation for very low Froude numbers (low gas velocity)
      // Field data shows accumulation factors of 5-15x at Fr < 0.5
      double criticalFroude = 1.5;
      double accumulationFactor = 1.0;

      if (froudeG < criticalFroude) {
        // Use stronger non-linear relationship for low Froude numbers
        // At Fr = 0, factor should be ~10-15x; at Fr = criticalFroude, factor = 1
        double froudeRatio = froudeG / criticalFroude;

        // Enhanced formula: factor = 1 + A * (1 - Fr/Fr_crit)^n
        // With A = 10 and n = 1.5 for stronger low-velocity response
        double exponent = 1.5;
        double amplitude = 10.0;
        accumulationFactor = 1.0 + amplitude * Math.pow(1.0 - froudeRatio, exponent);

        // Additional factor for very low velocities (Fr < 0.3)
        // This captures the "pooling" regime where gas cannot sweep liquid
        if (froudeG < 0.3) {
          double poolingFactor = 1.0 + 3.0 * (0.3 - froudeG) / 0.3;
          accumulationFactor *= poolingFactor;
        }

        // Additional factor for deep valleys
        double depthFactor = 1.0 + 0.5 * Math.min(elevChange / diameter, 10.0);
        accumulationFactor *= depthFactor;
      }

      // Allow holdup up to 85% in low points at very low gas velocities
      double maxHoldup = (froudeG < 0.5) ? 0.85 : terrainSlugCriticalHoldup + 0.2;
      enhancedHoldup = Math.min(maxHoldup, baseHoldup * accumulationFactor);

      // Check for terrain-induced slug initiation
      if (enhancedHoldup > terrainSlugCriticalHoldup && enableSevereSlugModel) {
        // Liquid level high enough to bridge pipe - slug will form
        sec.setTerrainSlugPending(true);
      }
    }

    // ========== RISER-BASE LIQUID FALLBACK CLOSURE ==========
    else if (isRiserBase && enableSevereSlugModel) {
      // This local carryover closure adjusts holdup only. System severe-slugging stability is
      // evaluated separately by evaluateSevereSluggingSystem().

      // Gas velocity must exceed the local carryover velocity to prevent buildup
      double sinTheta = Math.sin(inclination);
      double vCritRiser = 1.5 * Math.sqrt(g * diameter * dRho * sinTheta / Math.max(rhoG, 1.0));

      if (vsG < vCritRiser) {
        // Local liquid-fallback conditions - high accumulation
        // Enhanced: use stronger factor for very low velocities
        double velocityRatio = vsG / vCritRiser;
        double severityFactor = 1.0 + 4.0 * Math.pow(1.0 - velocityRatio, 1.5);
        enhancedHoldup = Math.min(0.90, baseHoldup * severityFactor);
        sec.setInclinedSectionLiquidFallbackPotential(true);
      }
    }

    // ========== UPHILL LIQUID FALLBACK ==========
    else if (isUphill) {
      double sinTheta = Math.sin(inclination);
      double cosTheta = Math.cos(inclination);

      // Critical gas velocity for liquid carryover (Turner droplet model + film flow)
      // For film flow: vG_crit = C * sqrt(g * D * (rhoL - rhoG) * sin(theta) / rhoG)
      // where C ~ 0.8-1.2 depending on liquid loading
      double filmCarryFactor = 0.9 + 0.3 * Math.min(baseHoldup * 10.0, 1.0);
      double vCritFilm = filmCarryFactor * Math.sqrt(g * diameter * dRho * sinTheta / Math.max(rhoG, 1.0));

      // For droplet entrainment (Turner correlation)
      // vG_crit_droplet = 1.593 * (sigma * (rhoL - rhoG) * g / rhoG^2)^0.25
      double vCritDroplet = 1.593 * Math.pow(sigma * dRho * g / (rhoG * rhoG), 0.25);

      // Use more conservative (higher) of the two criteria for steep angles
      double vCrit = isSteepUphill ? Math.max(vCritFilm, vCritDroplet) : vCritFilm;

      if (vsG < vCrit) {
        // Liquid falls back - accumulation factor depends on how far below critical
        double fallbackRatio = Math.min(1.0, vsG / vCrit);
        double fallbackFactor = 1.0 + liquidFallbackCoefficient * (1.0 - fallbackRatio) * 2.0;

        // Additional factor for steep uphills
        if (isSteepUphill) {
          fallbackFactor *= (1.0 + sinTheta);
        }

        enhancedHoldup = Math.min(0.75, baseHoldup * fallbackFactor);
      } else if (vsG > 1.3 * vCrit) {
        // Well above critical - good liquid carryover, slight holdup reduction
        enhancedHoldup = baseHoldup * 0.95;
      }
    }

    // ========== DOWNHILL DRAINAGE ==========
    else if (isDownhill) {
      double sinTheta = Math.abs(Math.sin(inclination));

      // On downhill sections, gravity accelerates liquid, reducing holdup
      // Drainage factor depends on angle and liquid loading
      double drainageFactor = 1.0 - 0.15 * sinTheta * (1.0 - baseHoldup);

      // But at very low velocities, liquid can still pool on downhill
      double froudeL = vsL / Math.sqrt(g * diameter * sinTheta);
      if (froudeL < 0.3) {
        // Low liquid Froude - stratified pooling possible
        drainageFactor = Math.max(drainageFactor, 0.9);
      }

      enhancedHoldup = baseHoldup * drainageFactor;
    }

    // ========== HIGH POINT GAS ACCUMULATION ==========
    else if (isHighPoint) {
      // At high points, gas accumulates (liquid drains away)
      // This can cause flow instabilities and gas blowby
      double gasAccumulationFactor = 0.85;
      enhancedHoldup = baseHoldup * gasAccumulationFactor;
    }

    // The enhanced value above is used to RAISE THE TERRAIN FLAGS ONLY; it is deliberately not
    // returned as the holdup.
    //
    // The holdup handed in has already been solved from the two-fluid momentum balance at this
    // section's own inclination - calculateStratifiedMomentumResidual carries rhoG*g*sin(theta) and
    // rhoL*g*sin(theta), and calculateStratifiedHoldupOLGA is called with the local angle. Scaling
    // that result by a further terrain factor counts the same gravity term twice. The low-point
    // branch compounded three separate proxies for one effect (a Froude factor up to 11, a pooling
    // factor up to 4 and a depth factor up to 6) to a multiplier of order 100 before an arbitrary
    // clip.
    //
    // It is also the wrong kind of model for a steady state. As this package already states for the
    // severe-slugging diagnostic, terrain slugging is a system instability rather than a local
    // pipe-section threshold: liquid accumulates and surges cyclically, which is a transient
    // process. A converged steady state carries no net accumulation by definition, so its low-point
    // holdup is whatever the momentum balance holds there.
    //
    // Measured on a 73.8 km export line at 4 MSm3/d: the multiplier raised the maximum holdup to
    // 11.0 times the median of the same profile, which is far beyond what the momentum balance
    // alone produces on that terrain.
    return baseHoldup;
  }

  /**
   * Check for and handle terrain-induced slug events.
   *
   * <p>
   * Called during transient simulation to detect when accumulated liquid in low points reaches critical level and
   * triggers a terrain-induced slug.
   * </p>
   *
   * @param dt Time step (s)
   */
  private void checkTerrainSlugEvents(double dt) {
    if (!enableTerrainTracking || !enableSevereSlugModel || sections == null || sections.length == 0) {
      return;
    }

    for (int i = 0; i < sections.length; i++) {
      TwoFluidSection sec = sections[i];
      if (sec == null) {
        continue;
      }
      if (sec.isTerrainSlugPending() && sec.getLiquidHoldup() > terrainSlugCriticalHoldup) {
        // Initiate terrain-induced slug
        if (slugTracker != null) {
          // Create a new slug starting at this position
          double slugVolume = sec.getArea() * sec.getLength() * sec.getLiquidHoldup();
          double slugLength = sec.getLength() * sec.getLiquidHoldup() / 0.7; // Assume 70%
                                                                             // holdup in
          // slug

          // Log terrain slug event
          logger.debug("Terrain-induced slug initiated at section {} (position {} m), " + "volume {} m³", i,
              sec.getPosition(), slugVolume);
        }
        sec.setTerrainSlugPending(false);
      }
    }
  }

  /**
   * March the steady-state pressure from one section to the next.
   *
   * <p>
   * Both the forward-marching initialization and the iterative refinement must integrate the <em>same</em> discrete
   * momentum balance, otherwise the refinement drives the profile away from a consistent initialization. The gradient
   * is therefore always evaluated on the upstream section and applied over that section's own length, so the
   * hydrostatic contribution telescopes to {@code rho * g * dz} across the line and terrain undulation cancels
   * correctly.
   * </p>
   *
   * @param prev upstream section supplying the pressure and the gradient
   * @return pressure at the downstream section (Pa), floored at 1 bar
   */
  private double marchPressure(TwoFluidSection prev) {
    double dPdx = estimatePressureGradient(prev);
    return Math.max(MIN_SECTION_PRESSURE_PA, prev.getPressure() - dPdx * prev.getLength());
  }

  /**
   * Check whether any section pressure rests on the marching floor.
   *
   * @return true when at least one section sits at {@link #MIN_SECTION_PRESSURE_PA}
   */
  private boolean isAnySectionAtPressureFloor() {
    if (sections == null) {
      return false;
    }
    for (TwoFluidSection sec : sections) {
      if (sec != null && sec.getPressure() <= MIN_SECTION_PRESSURE_PA * (1.0 + 1.0e-9)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Estimate pressure gradient for steady-state initialization.
   *
   * <p>
   * Uses Haaland equation (explicit approximation to Colebrook-White) for friction factor, consistent with
   * AdiabaticPipe and PipeBeggsAndBrills.
   * </p>
   *
   * @param sec Current pipe section
   * @return Pressure gradient estimate (Pa/m)
   */
  private double estimatePressureGradient(TwoFluidSection sec) {
    double alphaG = sec.getGasHoldup();
    double alphaL = sec.getLiquidHoldup();
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    double vG = sec.getGasVelocity();
    double vL = sec.getLiquidVelocity();

    // Mixture density (holdup-weighted for friction and gravity)
    double rhoMix = alphaG * rhoG + alphaL * rhoL;

    // Mixture velocity (total volumetric flux J = U_SG + U_SL)
    double vMix = alphaG * vG + alphaL * vL;

    // Quality (vapor mass fraction of flow)
    double gGas = alphaG * rhoG * Math.abs(vG);
    double gLiq = alphaL * rhoL * Math.abs(vL);
    double gTotal = gGas + gLiq;
    double x = (gTotal > 1e-10) ? gGas / gTotal : 1.0;

    // McAdams two-phase viscosity: 1/muTP = x/muG + (1-x)/muL
    // This quality-based harmonic averaging is more physical than holdup-weighted
    // for separated flows where gas and liquid have different velocities
    double muG = sec.getGasViscosity();
    double muL = sec.getLiquidViscosity();
    double muTP;
    if (muG > 1e-20 && muL > 1e-20 && x > 0.001 && x < 0.999) {
      muTP = 1.0 / (x / muG + (1.0 - x) / muL);
    } else {
      muTP = alphaG * muG + alphaL * muL;
    }

    // Calculate Darcy friction factor using Haaland equation
    double fTP = calcDarcyFrictionFactor(rhoMix, Math.abs(vMix), diameter, muTP);

    // Darcy-Weisbach: dP/dx = f * rho * v^2 / (2 * D)
    double dPdx_fric = fTP * rhoMix * vMix * vMix / (2.0 * diameter);

    // A mixture correlation charges the whole perimeter with a liquid-weighted density, which is
    // right for a dispersed flow and badly wrong for a separated one. Where the phases are
    // separated the wall shear belongs to each phase over its own wetted perimeter.
    if (useSeparatedFrictionModel) {
      double separatedWeight = separatedFrictionWeight(sec);
      if (separatedWeight > 0.0) {
        double dPdx_sep = separatedFrictionGradient(sec);
        if (Double.isFinite(dPdx_sep)) {
          dPdx_fric = (1.0 - separatedWeight) * dPdx_fric + separatedWeight * dPdx_sep;
        }
      }
    }

    // Gravity gradient
    double dPdx_grav = rhoMix * 9.81 * Math.sin(sec.getInclination());

    return dPdx_fric + dPdx_grav;
  }

  /**
   * Fraction of the friction gradient that should come from the separated model.
   *
   * <p>
   * Stratified flow has a liquid layer at the bottom of the bore, which is the geometry
   * {@link #separatedFrictionGradient(TwoFluidSection)} builds its wetted perimeters from, so the wall shear is per
   * phase there. Slug, churn and dispersed bubble flow are mixed on the scale of the pipe, where the mixture
   * correlation is the appropriate description.
   * </p>
   *
   * <p>
   * Annular flow is deliberately excluded even though its phases are separated. Its film wets the whole perimeter, so
   * the circular-segment split that assigns most of the wall to the gas does not describe it. Including annular flow
   * was measured on a 73.8 km export line: the pressure drop error went from +1.4 per cent to +14.7 per cent at 10
   * MSm3/d, and 12 MSm3/d, previously exact, ran into the pressure floor and failed to converge, while the
   * stratified-dominated cases at 4 and 7 MSm3/d improved from +6.0 and +8.0 per cent to +1.4 and +1.7 per cent. A
   * separated form for annular flow needs the film geometry, not this one.
   * </p>
   *
   * @param sec section being evaluated
   * @return a weight between zero and one
   */
  private double separatedFrictionWeight(TwoFluidSection sec) {
    Map<FlowRegime, Double> weights = sec.getRegimeWeights();
    if (weights == null) {
      return isSeparatedRegime(sec.getFlowRegime()) ? 1.0 : 0.0;
    }

    double separated = 0.0;
    for (Map.Entry<FlowRegime, Double> entry : weights.entrySet()) {
      if (isSeparatedRegime(entry.getKey())) {
        separated += entry.getValue();
      }
    }
    return Math.max(0.0, Math.min(1.0, separated));
  }

  /**
   * Whether a regime keeps the phases separated in a layer the segment geometry describes.
   *
   * <p>
   * Annular flow is excluded even though its phases are separated. Its film wets the whole perimeter, so the
   * circular-segment split that assigns most of the wall to the gas does not describe it. Measured on a 73.8 km export
   * line, including annular flow moved the pressure drop error from +1.4 to +14.7 per cent at 10 MSm3/d and pushed 12
   * MSm3/d, previously exact, into the pressure floor, while the stratified-dominated cases at 4 and 7 MSm3/d improved
   * from +6.0 and +8.0 to +1.4 and +1.6 per cent. Charging only the non-entrained film to the wall was tried and does
   * not recover it: it leaves +12.1 and +34.8 per cent at those two rates. A separated form for annular flow needs the
   * film geometry rather than this one.
   * </p>
   *
   * @param regime the flow regime
   * @return true for stratified flow
   */
  private static boolean isSeparatedRegime(FlowRegime regime) {
    return regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY;
  }

  /**
   * Fraction of the liquid carried as droplets in the gas core.
   *
   * <p>
   * Ishii-Mishima form {@code E = tanh(7.25e-7 * We^1.25 * Re_L^0.25)} on the gas Weber number and the liquid Reynolds
   * number, capped at 0.95 so a film always remains.
   * </p>
   *
   * @param vsG superficial gas velocity, in m/s
   * @param vsL superficial liquid velocity, in m/s
   * @param rhoG gas density, in kg/m3
   * @param rhoL liquid density, in kg/m3
   * @param muL liquid viscosity, in Pa.s
   * @param sigma surface tension, in N/m
   * @param D pipe inner diameter, in m
   * @return entrained fraction of the liquid, between zero and 0.95
   */
  private static double entrainedLiquidFraction(double vsG, double vsL, double rhoG, double rhoL, double muL,
      double sigma, double D) {
    if (sigma <= 0.0 || muL <= 0.0 || vsG <= 0.0 || vsL <= 0.0) {
      return 0.0;
    }
    double weberGas = rhoG * vsG * vsG * D / sigma;
    double reynoldsLiquid = rhoL * vsL * D / muL;
    if (weberGas <= 0.0 || reynoldsLiquid <= 0.0) {
      return 0.0;
    }
    double argument = 7.25e-7 * Math.pow(weberGas, 1.25) * Math.pow(reynoldsLiquid, 0.25);
    return Math.min(0.95, Math.tanh(argument));
  }

  /**
   * Friction pressure gradient from the per-phase wall shear of a separated flow.
   *
   * <p>
   * Summing the gas and liquid momentum equations cancels the interfacial shear and leaves
   * {@code -dP/dx = (tau_wG*S_G + tau_wL*S_L)/A + rho_mix*g*sin(theta)}, so only the wall terms enter here. Each phase
   * carries its own density, velocity and hydraulic diameter, with the interface counted in the gas perimeter alone.
   * The single-phase limit reduces to Darcy-Weisbach exactly.
   * </p>
   *
   * @param sec section being evaluated
   * @return friction pressure gradient, in Pa/m, or NaN when the geometry is degenerate
   */
  private double separatedFrictionGradient(TwoFluidSection sec) {
    double alphaL = sec.getLiquidHoldup();
    double alphaG = 1.0 - alphaL;
    if (alphaL <= 0.0 || alphaG <= 0.0) {
      return Double.NaN;
    }

    double area = Math.PI * diameter * diameter / 4.0;
    double liquidArea = alphaL * area;
    double gasArea = alphaG * area;
    double beta = calculateStratifiedCentralAngle(alphaL);
    double liquidPerimeter = diameter * beta / 2.0;
    double gasPerimeter = diameter * (Math.PI - beta / 2.0);
    double interfaceWidth = diameter * Math.sin(beta / 2.0);

    if (liquidPerimeter <= 0.0 || gasPerimeter <= 0.0) {
      return Double.NaN;
    }

    double liquidHydraulicDiameter = 4.0 * liquidArea / liquidPerimeter;
    double gasHydraulicDiameter = 4.0 * gasArea / (gasPerimeter + interfaceWidth);

    double liquidVelocity = sec.getLiquidVelocity();
    double gasVelocity = sec.getGasVelocity();
    double rhoL = sec.getLiquidDensity();
    double rhoG = sec.getGasDensity();

    double fL = calcDarcyFrictionFactor(rhoL, Math.abs(liquidVelocity), liquidHydraulicDiameter,
        sec.getLiquidViscosity());
    double fG = calcDarcyFrictionFactor(rhoG, Math.abs(gasVelocity), gasHydraulicDiameter, sec.getGasViscosity());

    // tau_w = f_Darcy * rho * U|U| / 8 follows from dP/dx = 4*tau_w/D in the single-phase limit.
    double liquidWallShear = fL * rhoL * liquidVelocity * Math.abs(liquidVelocity) / 8.0;
    double gasWallShear = fG * rhoG * gasVelocity * Math.abs(gasVelocity) / 8.0;

    return (gasWallShear * gasPerimeter + liquidWallShear * liquidPerimeter) / area;
  }

  /**
   * Calculate Darcy friction factor using the Haaland equation.
   *
   * @param rho fluid density (kg/m3)
   * @param velocity flow velocity (m/s)
   * @param D pipe diameter (m)
   * @param mu dynamic viscosity (Pa.s)
   * @return Darcy friction factor
   */
  private double calcDarcyFrictionFactor(double rho, double velocity, double D, double mu) {
    if (velocity < 1e-10 || mu < 1e-20) {
      return 0.0;
    }
    double Re = rho * velocity * D / mu;
    double relativeRoughness = roughness / D;

    if (Re < 1e-10) {
      return 0.0;
    } else if (Re < 2300) {
      return 64.0 / Re;
    } else if (Re < 4000) {
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow(1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughness / 3.7, 1.11))),
          2.0);
      return fLaminar + (fTurbulent - fLaminar) * (Re - 2300.0) / 1700.0;
    } else {
      return Math.pow(1.0 / (-1.8 * Math.log10(6.9 / Re + Math.pow(relativeRoughness / 3.7, 1.11))), 2.0);
    }
  }

  /**
   * Update oil/water holdups without inventing an absent liquid phase.
   *
   * @param sec current pipe section
   * @param prev previous pipe section
   * @param alphaL total liquid holdup
   * @param area pipe cross-sectional area
   */
  private void updateLiquidPhaseSplit(TwoFluidSection sec, TwoFluidSection prev, double alphaL, double area) {
    double waterCut = sec.getWaterCut();
    if (Double.isNaN(waterCut)) {
      waterCut = prev != null ? prev.getWaterCut() : 0.0;
    }

    if (waterCut <= 1.0e-8 || waterCut >= 1.0 - 1.0e-8) {
      waterCut = Math.max(0.0, Math.min(1.0, waterCut));
      sec.setWaterCut(waterCut);
      sec.setOilFractionInLiquid(1.0 - waterCut);
      sec.setWaterHoldup(alphaL * waterCut);
      sec.setOilHoldup(alphaL * (1.0 - waterCut));
      sec.updateWaterOilConservativeVariables();
      return;
    }

    updateWaterOilHoldups(sec, prev, alphaL, area);
  }

  /**
   * Update water and oil holdups for three-phase flow with terrain effects.
   *
   * <p>
   * Water is denser than oil, so it tends to accumulate in valleys (low spots) more than oil. This method calculates
   * the local water cut which can vary along the pipe based on:
   * </p>
   * <ul>
   * <li>Gravity segregation: water settles faster in low-velocity regions</li>
   * <li>Terrain effects: water accumulates more in valleys</li>
   * <li>Slip between oil and water phases</li>
   * </ul>
   *
   * @param sec Current section
   * @param prev Previous section (upstream)
   * @param alphaL Total liquid holdup
   * @param area Pipe cross-sectional area
   */
  private void updateWaterOilHoldups(TwoFluidSection sec, TwoFluidSection prev, double alphaL, double area) {
    double rhoOil = sec.getOilDensity();
    double rhoWater = sec.getWaterDensity();
    double g = 9.81;
    double inclination = sec.getInclination();
    double sinTheta = Math.sin(inclination);
    double deltaRho = rhoWater - rhoOil; // Positive: water is heavier

    // Transported (no-slip) water volume fraction. This is the conservation basis: it comes
    // from the local flash, so it follows condensation, and it must not be modified here.
    double lambdaW = sec.getInputWaterVolumeFraction();
    if (lambdaW <= 0.0 && prev != null) {
      lambdaW = prev.getInputWaterVolumeFraction();
    }
    lambdaW = Math.max(0.0, Math.min(1.0, lambdaW));

    // Get liquid velocity for stratification assessment
    double vL = sec.getLiquidVelocity();

    // ========== Low-Velocity Water Stratification Model ==========
    // At low liquid velocities the denser water segregates towards the pipe bottom and
    // travels slower than the oil. The in-situ water holdup fraction is then LARGER than
    // the transported water fraction. That effect is expressed here as an oil/water slip
    // ratio S = v_oil / v_water >= 1, so the holdup split can be closed on the phase mass
    // balance instead of by scaling the water cut directly (which creates water).

    // Calculate liquid Froude number for stratification assessment
    // Fr_L = v_L / sqrt(g * D * Δρ/ρ_L)
    double effectiveRhoL = lambdaW * rhoWater + (1.0 - lambdaW) * rhoOil;
    double liquidFroude = vL / Math.sqrt(g * diameter * Math.abs(deltaRho) / effectiveRhoL + 1e-10);

    // Oil-over-water slip ratio S = v_oil / v_water. The ratio plateaus near 2.75 while the
    // liquid is stratified and rolls off to 1 once it disperses above a liquid Froude number
    // of about 3.
    double slipRatio = 1.0;

    if (deltaRho > 10.0 && alphaL > 0.01) {
      double froudeRatio = liquidFroude / OIL_WATER_SLIP_CRITICAL_FROUDE;
      double excess = OIL_WATER_SLIP_PLATEAU * Math.max(0.0, 1.0 - froudeRatio * froudeRatio);

      // Inclination tilts the settling balance; kept as a modest correction.
      if (sinTheta < -0.02) {
        // Downhill: water runs ahead, so the layers separate less.
        excess *= 1.0 + 0.3 * Math.abs(sinTheta);
      } else if (sinTheta > 0.02) {
        // Uphill: water lags further behind and pools.
        excess *= 1.0 + 0.5 * sinTheta;
      }

      slipRatio = 1.0 + excess;
    }

    // Oil/water drift is only meaningful when both liquids are actually present and the
    // liquid layer is thick enough to stratify.
    if (!isWaterOilSlipEnabled() || deltaRho <= 0.0 || alphaL <= 0.02 || lambdaW <= 1.0e-6 || lambdaW >= 1.0 - 1.0e-6) {
      slipRatio = 1.0;
    }
    slipRatio = Math.max(1.0, Math.min(MAX_OIL_WATER_SLIP_RATIO, slipRatio));

    // Close the split on the phase mass balance. With q_w/(q_w + q_o) = lambdaW and
    // S = v_o/v_w, requiring rho_k*alpha_k*A*v_k to reproduce the transported phase flows
    // gives alpha_w = alpha_L * S*lambdaW / ((1 - lambdaW) + S*lambdaW). At S = 1 this is
    // the no-slip split, so the previous behaviour is recovered exactly when slip is off.
    double denom = (1.0 - lambdaW) + slipRatio * lambdaW;
    double waterCut = denom > 0.0 ? slipRatio * lambdaW / denom : lambdaW;

    // Exact oil-only and water-only states are valid conservative limits. Denominator
    // regularization belongs in closures and must not seed the absent liquid phase.
    waterCut = Math.max(0.0, Math.min(1.0, waterCut));

    // Calculate water and oil holdups from water cut and total liquid holdup
    double alphaW = alphaL * waterCut;
    double alphaO = alphaL * (1.0 - waterCut);

    // Update section properties
    sec.setWaterCut(waterCut);
    sec.setOilFractionInLiquid(1.0 - waterCut);
    sec.setWaterHoldup(alphaW);
    sec.setOilHoldup(alphaO);

    // Update water and oil mass per length
    sec.setWaterMassPerLength(alphaW * rhoWater * area);
    sec.setOilMassPerLength(alphaO * rhoOil * area);

    // Phase velocities follow from the same mass balance that set the holdups, so
    // rho_k*alpha_k*A*v_k reproduces the transported phase flows by construction.
    double qLiquid = alphaL * vL;
    double vOil = vL;
    double vWater = vL;
    if (alphaW > 1.0e-9 && alphaO > 1.0e-9) {
      vWater = qLiquid * lambdaW / alphaW;
      vOil = qLiquid * (1.0 - lambdaW) / alphaO;
    } else if (alphaW > 1.0e-9) {
      vWater = qLiquid / alphaW;
      vOil = 0.0;
    } else if (alphaO > 1.0e-9) {
      vOil = qLiquid / alphaO;
      vWater = 0.0;
    }

    sec.setOilVelocity(vOil);
    sec.setWaterVelocity(vWater);

    // Update combined liquid properties based on new water cut
    sec.updateThreePhaseProperties();

    // Update momentum variables
    sec.setOilMomentumPerLength(sec.getOilMassPerLength() * vOil);
    sec.setWaterMomentumPerLength(sec.getWaterMassPerLength() * vWater);
  }

  @Override
  public void run(UUID id) {
    lastMassBalanceReport = null;
    lastThermalEnergyBalanceReport = null;
    lastComponentConservationReport = null;
    componentConservationReports.clear();
    componentConservationTimes.clear();

    // Initialize sections
    initializeSections();

    // Run steady-state
    runSteadyState();

    if (componentTransportEnabled) {
      componentTransport = new TwoFluidComponentTransport(referenceFluid, sections);
    } else {
      componentTransport = null;
    }

    // Set up outlet stream
    updateOutletStream();

    setCalculationIdentifier(id);
  }

  /**
   * Run transient simulation for specified time step.
   *
   * @param dt Requested time step (s)
   * @param id Calculation identifier
   * @throws IllegalArgumentException if {@code dt} is not positive and finite
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Transient time step must be positive and finite");
    }
    isTransientMode = true;
    lastMassBalanceReport = null;
    lastThermalEnergyBalanceReport = null;
    lastComponentConservationReport = null;
    if (componentTransportEnabled) {
      if (componentTransport == null) {
        throw new IllegalStateException(
            "Component transport is enabled but not initialized; call run() before runTransient()");
      }
      componentTransport.beginInterval();
    }
    clearSevereSluggingSystemClassification();
    double[] initialMassKg = getPhaseMassInventoriesKg();
    double[] integratedInletMassKg = new double[3];
    double[] integratedOutletMassKg = new double[3];
    double[] integratedSourceMassKg = new double[3];
    double fluidEnergyChangeJ = 0.0;
    double wallEnergyChangeJ = 0.0;
    double sensibleAdvectionEnergyJ = 0.0;
    double jouleThomsonEnergyJ = 0.0;
    double latentHeatEnergyJ = 0.0;
    double ambientHeatLossJ = 0.0;
    double directElectricalHeatingEnergyJ = 0.0;
    boolean thermalEnergyTracked = false;
    double acceptedElapsedTime = 0.0;
    int acceptedSubsteps = 0;

    // Boundary changes must affect the first accepted finite-volume step. With the
    // conservative state initialized by run(), this updates flux primitives and
    // momenta only; it does not replace cell phase inventory.
    applyBoundaryConditions();
    validateSectionStates();

    boolean isIMEX = (timeIntegrator.getMethod() == TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    boolean useImplicitVoidWave = equations.isEnableInterfacialPressure() && implicitInterfacialPressureCoupling;
    equations.setImplicitInterfacialPressure(useImplicitVoidWave);

    // Calculate initial stable time step from the current-velocity CFL limit
    double dtCFL = isIMEX ? calcConvectiveTimeStep() : calcStableTimeStep();
    if (enableAdaptiveTimestepping) {
      dtCFL *= adaptiveDtFactor;
    }
    double dtActual = Math.min(dt, dtCFL);

    // For non-adaptive mode: uniform substeps (legacy behavior)
    if (!enableAdaptiveTimestepping) {
      int subSteps = (int) Math.ceil(dt / dtActual);
      subSteps = Math.max(subSteps, 2);
      subSteps = Math.min(subSteps, 10000);
      dtActual = dt / subSteps;
    }

    // Reference pressure for relative change detection (use inlet pressure)
    double pRef = Math.max(getInletStream().getFluid().getPressure("Pa"), 1e5);

    double timeRemaining = dt;
    int maxSubSteps = enableAdaptiveTimestepping ? 50000 : 10000;
    int stepCount = 0;

    while (timeRemaining > 1e-12 && stepCount < maxSubSteps) {
      stepCount++;

      // Adaptive: recompute CFL from the current state at each step.
      if (enableAdaptiveTimestepping) {
        dtCFL = isIMEX ? calcConvectiveTimeStep() : calcStableTimeStep();
        dtCFL *= adaptiveDtFactor;
        dtActual = Math.min(dtCFL, timeRemaining);
        dtActual = Math.max(dtActual, 1e-10); // absolute floor
      } else {
        dtActual = Math.min(dtActual, timeRemaining);
      }

      // 1. Update thermodynamic properties (periodically)
      currentStep++;
      if (currentStep % thermodynamicUpdateInterval == 0) {
        updateThermodynamics();
      }

      // 2. Store previous state for rollback capability
      double[][] U_prev = equations.extractState(sections);

      // 3. Calculate RHS and advance solution
      final double dtFinal = dtActual;
      final boolean captureThermalStageFluxes = enableHeatTransfer && heatTransferCoefficient > 0.0
          || componentTransportEnabled || directElectricalHeatingPowerPerMeter > 0.0;
      final boolean captureComponentStageFluxes = componentTransportEnabled;
      final boolean capturePhaseStageTerms = captureThermalStageFluxes || captureComponentStageFluxes;
      final List<TwoFluidConservationEquations.MassBalanceRate> stageMassBalanceRates = new ArrayList<>();
      final double[] phaseStageWeights = capturePhaseStageTerms ? getTimeIntegrationStageWeights() : new double[0];
      final double[][] weightedPhaseMassFaceFluxes = capturePhaseStageTerms ? new double[numberOfSections + 1][3]
          : new double[0][0];
      final double[][] weightedPhaseMassSources = captureComponentStageFluxes ? new double[numberOfSections][3]
          : new double[0][0];
      final int[] phaseStageIndex = { 0 };

      TimeIntegrator.RHSFunction rhs = (state, t) -> {
        equations.applyState(sections, state);
        // Boundary conditions are part of the semi-discrete operator and must be
        // enforced for every Runge-Kutta stage, not only after an accepted step.
        // This is especially important for CLOSED boundaries because intermediate
        // stage momenta can otherwise create a spurious boundary flux.
        applyBoundaryConditions();
        double[][] derivative = equations.calcRHS(sections, dx);
        stageMassBalanceRates.add(equations.getLastMassBalanceRate());
        if (capturePhaseStageTerms) {
          int stage = phaseStageIndex[0]++;
          if (stage >= phaseStageWeights.length) {
            throw new IllegalStateException(
                "Received more phase-flux stages than expected for " + timeIntegrator.getMethod());
          }
          equations.accumulateLastPhaseMassFaceFluxes(weightedPhaseMassFaceFluxes, phaseStageWeights[stage]);
          if (captureComponentStageFluxes) {
            equations.accumulateLastPhaseMassSourcesPerLength(weightedPhaseMassSources, phaseStageWeights[stage]);
          }
        }
        return derivative;
      };

      // Provide cell properties for the implicit acoustic, void-wave, and
      // coupled pressure-momentum solves.
      if (isIMEX || useImplicitVoidWave || coupledPressureMomentumEnabled) {
        double[] soundSpeeds = new double[numberOfSections];
        double[] gasSoundSpeeds = new double[numberOfSections];
        double[] oilSoundSpeeds = new double[numberOfSections];
        double[] waterSoundSpeeds = new double[numberOfSections];
        double[] voidWaveSpeeds = new double[numberOfSections];
        double[] voidWaveSlipCoefficients = new double[numberOfSections];
        double[] densities = new double[numberOfSections];
        double[] areas = new double[numberOfSections];
        double[] lengths = new double[numberOfSections];
        double[] pressures = new double[numberOfSections];
        double[] gasDensities = new double[numberOfSections];
        double[] oilDensities = new double[numberOfSections];
        double[] waterDensities = new double[numberOfSections];
        for (int i = 0; i < numberOfSections; i++) {
          TwoFluidSection sec = sections[i];
          double alphaG = sec.getGasHoldup();
          double alphaL = sec.getLiquidHoldup();
          double rhoG = Math.max(sec.getGasDensity(), 0.1);
          double rhoL = Math.max(sec.getLiquidDensity(), 100.0);
          double rhoO = Math.max(sec.getOilDensity(), rhoL);
          double rhoW = Math.max(sec.getWaterDensity(), rhoL);
          densities[i] = alphaG * rhoG + alphaL * rhoL;
          areas[i] = sec.getArea();
          gasDensities[i] = rhoG;
          oilDensities[i] = rhoO;
          waterDensities[i] = rhoW;
          // Mixture sound speed (Wood's equation for two-phase)
          double rhoMix = densities[i];
          double cG = Math.max(sec.getGasSoundSpeed(), 100.0);
          double cL = Math.max(sec.getLiquidSoundSpeed(), 500.0);
          gasSoundSpeeds[i] = cG;
          oilSoundSpeeds[i] = cL;
          waterSoundSpeeds[i] = cL;
          lengths[i] = sec.getLength();
          pressures[i] = sec.getPressure();
          double invC2 = alphaG / (rhoG * cG * cG) + alphaL / (rhoL * cL * cL);
          soundSpeeds[i] = (invC2 > 0) ? Math.sqrt(1.0 / (rhoMix * invC2)) : cG;
          soundSpeeds[i] = Math.max(soundSpeeds[i], 1.0);
          voidWaveSpeeds[i] = equations.calcVoidWaveSpeed(sec);
          voidWaveSlipCoefficients[i] = equations.calcVoidWaveSlipCoefficient(sec);
        }
        if (isIMEX) {
          boolean outletFixed = (outletBCType == BoundaryCondition.CONSTANT_PRESSURE
              || outletBCType == BoundaryCondition.CHARACTERISTIC);
          timeIntegrator.setIMEXProperties(soundSpeeds, densities, areas, gasDensities, oilDensities, waterDensities,
              dx, outletPressure, outletFixed);
        }
        timeIntegrator.setImplicitVoidWaveProperties(voidWaveSpeeds, voidWaveSlipCoefficients, areas, gasDensities,
            oilDensities, waterDensities, dx, useImplicitVoidWave);
        boolean outletFixed = (outletBCType == BoundaryCondition.CONSTANT_PRESSURE
            || outletBCType == BoundaryCondition.CHARACTERISTIC);
        timeIntegrator.setCoupledPressureMomentumProperties(
            pressures,
            areas,
            lengths,
            gasDensities,
            oilDensities,
            waterDensities,
            gasSoundSpeeds,
            oilSoundSpeeds,
            waterSoundSpeeds,
            outletPressure,
            outletFixed,
            coupledPressureMomentumEnabled);
      } else {
        timeIntegrator.setCoupledPressureMomentumEnabled(false);
      }

      double[][] splitState = applyStiffBubbleDragSourceStep(U_prev, 0.5 * dtFinal);
      double[][] U_new = timeIntegrator.step(splitState, rhs, dtFinal);
      U_new = applyStiffBubbleDragSourceStep(U_new, 0.5 * dtFinal);

      if (coupledPressureMomentumEnabled
          && !timeIntegrator.isCoupledPressureMomentumConverged()) {
        equations.applyState(sections, U_prev);
        if (enableAdaptiveTimestepping) {
          adaptiveDtFactor = Math.max(
              adaptiveDtFactor * 0.5, MIN_ADAPTIVE_DT_FACTOR);
          currentStep--;
          continue;
        }
        throw new IllegalStateException(
            getName()
                + ": coupled pressure-momentum correction did not converge; maximum relative "
                + "cell-volume residual="
                + timeIntegrator.getCoupledPressureMomentumVolumeResidual()
                + " after "
                + timeIntegrator.getCoupledPressureMomentumIterations()
                + " iterations");
      }

      // 4. ADAPTIVE: check RAW state for NaN/Inf/negative mass BEFORE clamping
      // Only hard-reject on unphysical values. Normal transient changes (even large)
      // are fine — the post-correction checks catch actual blow-up.
      if (enableAdaptiveTimestepping) {
        boolean stepRejected = false;

        // Check for NaN/Inf in any state variable
        for (int i = 0; i < U_new.length && !stepRejected; i++) {
          for (int j = 0; j < U_new[i].length; j++) {
            if (Double.isNaN(U_new[i][j]) || Double.isInfinite(U_new[i][j])) {
              stepRejected = true;
              break;
            }
          }
        }

        // Check for negative mass (unphysical — indicates numerical blow-up)
        if (!stepRejected) {
          for (int i = 0; i < U_new.length; i++) {
            // Mass variables are indices 0 (gas) and 1 (oil/liquid)
            if (U_new[i][0] < -1e-3 || U_new[i][1] < -1e-3) {
              stepRejected = true;
              break;
            }
          }
        }

        if (stepRejected) {
          equations.applyState(sections, U_prev);
          adaptiveDtFactor = Math.max(adaptiveDtFactor * 0.5, MIN_ADAPTIVE_DT_FACTOR);
          currentStep--;
          continue;
        }
      }

      // 5. Now safe to apply corrections and state
      validateAndCorrectState(U_new, U_prev);

      if (coupledPressureMomentumEnabled) {
        applyCoupledPressureMomentumState(U_new);
      }
      equations.applyState(sections, U_new);

      // 6. The coupled path has already solved pressure from compressibility and
      // corrected phase mass fluxes and momenta with the same face gradients.
      // The legacy path retains the steady friction/gravity pressure march.
      if (!coupledPressureMomentumEnabled) {
        reconstructPressureProfile();
      }

      // 7. Apply boundary conditions
      applyBoundaryConditions();

      // 8. Validate section states and fix any issues
      validateSectionStates();

      // 8b. Adaptive: post-correction sanity check (catch remaining issues)
      if (enableAdaptiveTimestepping) {
        boolean postRejected = false;

        for (TwoFluidSection sec : sections) {
          double p = sec.getPressure();
          if (p > adaptiveMaxPressure || Double.isNaN(p) || p <= 0) {
            postRejected = true;
            break;
          }
          double vg = sec.getGasVelocity();
          double vl = sec.getLiquidVelocity();
          if (Double.isNaN(vg) || Double.isNaN(vl) || Math.abs(vg) > 300.0 || Math.abs(vl) > 300.0) {
            postRejected = true;
            break;
          }
        }

        if (postRejected) {
          equations.applyState(sections, U_prev);
          reconstructPressureProfile();
          applyBoundaryConditions();
          adaptiveDtFactor = Math.max(adaptiveDtFactor * 0.25, MIN_ADAPTIVE_DT_FACTOR);
          currentStep--;
          continue;
        }

        // Step accepted: gradually grow dt factor back toward 1.0 (PI-style recovery)
        if (adaptiveDtFactor < 1.0) {
          adaptiveDtFactor = Math.min(adaptiveDtFactor * ADAPTIVE_DT_GROWTH, 1.0);
        }
      }

      accumulateAcceptedMassBalance(stageMassBalanceRates, dtActual, integratedInletMassKg, integratedOutletMassKg,
          integratedSourceMassKg);
      if (capturePhaseStageTerms && phaseStageIndex[0] != phaseStageWeights.length) {
        throw new IllegalStateException("Expected " + phaseStageWeights.length + " phase-flux stages for "
            + timeIntegrator.getMethod() + " but received " + phaseStageIndex[0]);
      }
      double[] latentHeatEnergyByCellJ = new double[numberOfSections];
      if (captureComponentStageFluxes) {
        latentHeatEnergyByCellJ = componentTransport.advance(dtActual, weightedPhaseMassFaceFluxes,
            weightedPhaseMassSources, sections, getInletStream().getFluid(), referenceFluid,
            componentConservationTolerance);
      }
      acceptedElapsedTime += dtActual;
      acceptedSubsteps++;

      // 8. Update accumulation tracking and slug tracking
      if (enableSlugTracking && slugTrackingMode != SlugTrackingMode.DISABLED) {
        accumulationTracker.updateAccumulation(sections, dtActual);

        // Set reference velocity for slug propagation (from inlet)
        double inletMixtureVelocity = sections[0].getMixtureVelocity();

        if (slugTrackingMode == SlugTrackingMode.LAGRANGIAN) {
          // Detailed Lagrangian tracking
          lagrangianSlugTracker.setReferenceVelocity(inletMixtureVelocity);

          // Check for terrain-induced slug initiation from accumulation zones
          for (LiquidAccumulationTracker.AccumulationZone zone : accumulationTracker.getAccumulationZones()) {
            LiquidAccumulationTracker.SlugCharacteristics slugChar = accumulationTracker.checkForSlugRelease(zone,
                sections);
            if (slugChar != null) {
              lagrangianSlugTracker.initializeTerrainSlug(slugChar, sections);
            }
          }

          // Advance slugs with full Lagrangian tracking
          lagrangianSlugTracker.advanceTimeStep(sections, dtActual);

          // Track slugs arriving at outlet
          trackOutletSlugsLagrangian();

        } else {
          // Simplified slug unit model
          slugTracker.setReferenceVelocity(inletMixtureVelocity);

          // Check for terrain-induced slug initiation from accumulation zones
          for (LiquidAccumulationTracker.AccumulationZone zone : accumulationTracker.getAccumulationZones()) {
            LiquidAccumulationTracker.SlugCharacteristics slugChar = accumulationTracker.checkForSlugRelease(zone,
                sections);
            if (slugChar != null) {
              slugTracker.initializeTerrainSlug(slugChar, sections);
            }
          }

          // Advance existing slugs through the pipeline
          slugTracker.advanceSlugs(sections, dtActual);

          // Track slugs arriving at outlet
          trackOutletSlugs();
        }

      }

      // 9. Update temperature profile when thermal or component transport is enabled
      if (captureThermalStageFluxes) {
        ThermalEnergyStep energyStep = updateTransientTemperature(dtActual, weightedPhaseMassFaceFluxes,
            latentHeatEnergyByCellJ);
        fluidEnergyChangeJ += energyStep.fluidEnergyChangeJ;
        wallEnergyChangeJ += energyStep.wallEnergyChangeJ;
        sensibleAdvectionEnergyJ += energyStep.sensibleAdvectionEnergyJ;
        jouleThomsonEnergyJ += energyStep.jouleThomsonEnergyJ;
        latentHeatEnergyJ += energyStep.latentHeatEnergyJ;
        ambientHeatLossJ += energyStep.ambientHeatLossJ;
        directElectricalHeatingEnergyJ += energyStep.directElectricalHeatingEnergyJ;
        thermalEnergyTracked = true;
      }

      // 10. Advance time
      simulationTime += dtActual;
      timeRemaining -= dtActual;
      timeIntegrator.advanceTime(dtActual);
    }

    lastMassBalanceReport = new TwoFluidMassBalanceReport(acceptedElapsedTime, acceptedSubsteps, initialMassKg,
        getPhaseMassInventoriesKg(), integratedInletMassKg, integratedOutletMassKg, integratedSourceMassKg);
    if (thermalEnergyTracked) {
      lastThermalEnergyBalanceReport = new TwoFluidThermalEnergyBalanceReport(acceptedElapsedTime, acceptedSubsteps,
          fluidEnergyChangeJ, wallEnergyChangeJ, sensibleAdvectionEnergyJ, jouleThomsonEnergyJ, latentHeatEnergyJ,
          ambientHeatLossJ, directElectricalHeatingEnergyJ);
    }
    if (componentTransportEnabled) {
      lastComponentConservationReport = componentTransport.createReport(acceptedElapsedTime, acceptedSubsteps,
          componentConservationTolerance);
      if (storeComponentConservationHistory) {
        componentConservationTimes.add(simulationTime);
        componentConservationReports.add(lastComponentConservationReport);
      }
      if (!lastComponentConservationReport.isConverged()) {
        throw new IllegalStateException(lastComponentConservationReport.getMessage());
      }
    }

    // Publish the accepted interval-average outlet flux after constructing its
    // conservative report. Result profiles use the same accepted final state.
    updateOutletStream();
    updateResultArrays();

    if (equations.isOutletBackflowClamped() && !transientOutletBackflowClamped) {
      transientOutletBackflowClamped = true;
      logger.warn("{}: a phase reversed at the outlet, where the transmissive boundary can only carry mass out, so its "
          + "outflow is clamped at zero while the inlet keeps feeding it. Liquid inventory will grow without "
          + "bound and the transient result must not be used. This is the ill-posedness of the classical "
          + "two-fluid system in liquid-rich flow; see setEnableInterfacialPressure(boolean).", getName());
    }

    setCalculationIdentifier(id);
  }

  private double[][] applyStiffBubbleDragSourceStep(double[][] state, double timeStep) {
    if (!equations.isStiffBubbleDragEnabled() || timeStep == 0.0) {
      return state;
    }
    equations.applyState(sections, state);
    applyBoundaryConditions();
    double[][] boundaryState = equations.extractState(sections);
    return equations.applyStiffBubbleDrag(sections, boundaryState, timeStep);
  }

  private void accumulateAcceptedMassBalance(List<TwoFluidConservationEquations.MassBalanceRate> stageRates,
      double timeStepSeconds, double[] inletMassKg, double[] outletMassKg, double[] sourceMassKg) {
    double[] weights = getTimeIntegrationStageWeights(stageRates.size());
    for (int stage = 0; stage < stageRates.size(); stage++) {
      TwoFluidConservationEquations.MassBalanceRate rate = stageRates.get(stage);
      double[] inletRate = rate.getInletMassFlowKgPerSecond();
      double[] outletRate = rate.getOutletMassFlowKgPerSecond();
      double[] sourceRate = rate.getSourceMassFlowKgPerSecond();
      double weightedTime = weights[stage] * timeStepSeconds;
      for (int phase = 0; phase < 3; phase++) {
        inletMassKg[phase] += inletRate[phase] * weightedTime;
        outletMassKg[phase] += outletRate[phase] * weightedTime;
        sourceMassKg[phase] += sourceRate[phase] * weightedTime;
      }
    }
  }

  private double[] getTimeIntegrationStageWeights(int stageCount) {
    double[] weights = getTimeIntegrationStageWeights();
    if (stageCount != weights.length) {
      throw new IllegalStateException("Expected " + weights.length + " integration stages for "
          + timeIntegrator.getMethod() + " but received " + stageCount);
    }
    return weights;
  }

  private double[] getTimeIntegrationStageWeights() {
    TimeIntegrator.Method method = timeIntegrator.getMethod();
    double[] weights;
    switch (method) {
    case EULER:
    case IMEX_PRESSURE_CORRECTION:
      weights = new double[] { 1.0 };
      break;
    case RK2:
      weights = new double[] { 0.5, 0.5 };
      break;
    case RK4:
      weights = new double[] { 1.0 / 6.0, 1.0 / 3.0, 1.0 / 3.0, 1.0 / 6.0 };
      break;
    case SSP_RK3:
      weights = new double[] { 1.0 / 6.0, 1.0 / 6.0, 2.0 / 3.0 };
      break;
    default:
      throw new IllegalStateException("Unsupported time integration method: " + method);
    }
    return weights;
  }

  /**
   * Validate and correct state vector to prevent numerical instabilities.
   *
   * @param U_new New state to validate
   * @param U_prev Previous state for fallback
   */
  private void validateAndCorrectState(double[][] U_new, double[][] U_prev) {
    for (int i = 0; i < U_new.length; i++) {
      double[] previous = U_prev != null && i < U_prev.length ? U_prev[i] : null;
      ConservativeStateLimiter.enforceThreePhaseMassPositivity(U_new[i], previous);
    }
  }

  /**
   * Validate section states and fix any numerical issues.
   */
  private void validateSectionStates() {
    // Get reference values from inlet stream
    double refPressure = getInletStream().getFluid().getPressure("Pa");
    double refTemperature = getInletStream().getFluid().getTemperature("K");

    for (TwoFluidSection sec : sections) {
      // Ensure holdups are valid (non-NaN, non-negative)
      double alphaL = sec.getLiquidHoldup();
      double alphaG = sec.getGasHoldup();
      double alphaO = sec.getOilHoldup();
      double alphaW = sec.getWaterHoldup();

      // Fix NaN or negative values
      boolean needsRecalc = false;
      if (Double.isNaN(alphaL) || alphaL < 0) {
        needsRecalc = true;
      }
      if (Double.isNaN(alphaG) || alphaG < 0) {
        needsRecalc = true;
      }
      if (Double.isNaN(alphaO) || alphaO < 0) {
        alphaO = 0;
        sec.setOilHoldup(0);
      }
      if (Double.isNaN(alphaW) || alphaW < 0) {
        alphaW = 0;
        sec.setWaterHoldup(0);
      }

      // If liquid or gas holdup is invalid, recalculate from oil+water
      if (needsRecalc) {
        alphaO = sec.getOilHoldup();
        alphaW = sec.getWaterHoldup();
        alphaL = alphaO + alphaW;
        alphaG = 1.0 - alphaL;

        // Clamp to valid range
        alphaL = Math.max(0, Math.min(1, alphaL));
        alphaG = Math.max(0, Math.min(1, alphaG));

        sec.setLiquidHoldup(alphaL);
        sec.setGasHoldup(alphaG);
      }

      // Ensure consistency: liquidHoldup should equal oilHoldup + waterHoldup
      // If they don't match, trust the oil+water values (from conservative variables)
      double sumOilWater = sec.getOilHoldup() + sec.getWaterHoldup();
      double diff = Math.abs(sec.getLiquidHoldup() - sumOilWater);
      if (diff > 1.0e-12) {
        // Determine which source to trust
        if (sumOilWater > 0.0) {
          // We have oil and/or water holdups - use them as the liquid holdup
          double newLiqHL = sumOilWater;
          double newGasHL = Math.max(0, Math.min(1, 1.0 - newLiqHL));
          sec.setLiquidHoldup(newLiqHL);
          sec.setGasHoldup(newGasHL);
        } else if (sec.getLiquidHoldup() > 0.0) {
          // We have liquid holdup but no oil/water - distribute based on water cut
          double waterCut = sec.getWaterCut();
          if (Double.isNaN(waterCut)) {
            waterCut = 0.5; // Default to 50/50 only if no valid water cut exists
          }
          waterCut = Math.max(0.0, Math.min(1.0, waterCut));
          double newAlphaW = sec.getLiquidHoldup() * waterCut;
          double newAlphaO = sec.getLiquidHoldup() * (1.0 - waterCut);
          sec.setWaterHoldup(newAlphaW);
          sec.setOilHoldup(newAlphaO);
        }
      }

      // Ensure pressure is positive
      if (sec.getPressure() <= 0 || Double.isNaN(sec.getPressure())) {
        sec.setPressure(refPressure); // Reset to inlet pressure
      }

      // Ensure temperature is positive
      if (sec.getTemperature() <= 0 || Double.isNaN(sec.getTemperature())) {
        sec.setTemperature(refTemperature);
      }
    }
  }

  /**
   * Calculate stable time step using CFL condition.
   *
   * @return stable time step [s]
   */
  private double calcStableTimeStep() {
    double minDt = Double.MAX_VALUE;

    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      double gasSpeed = Math.abs(sec.getGasVelocity()) + sec.getGasSoundSpeed();
      double liqSpeed = Math.abs(sec.getLiquidVelocity()) + sec.getLiquidSoundSpeed();
      double maxSpeed = Math.max(1.0, Math.max(gasSpeed, liqSpeed));
      if (equations != null) {
        maxSpeed = Math.max(maxSpeed, equations.calcVoidWaveSpeed(sec));
      }
      double secDx = sec.getLength();
      minDt = Math.min(minDt, cflNumber * secDx / maxSpeed);
    }

    return minDt;
  }

  /**
   * Calculate stable time step using convective CFL (material velocities only, no sound speed). Used by the IMEX
   * pressure correction method which handles acoustic waves implicitly.
   *
   * <p>
   * dt_convective = CFL * dx / max(|v_G|, |v_L|)
   * </p>
   *
   * <p>
   * Typically 10-100x larger than the acoustic CFL for gas-liquid flows.
   * </p>
   *
   * @return stable convective time step (s)
   */
  private double calcConvectiveTimeStep() {
    double minDt = Double.MAX_VALUE;

    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      double secDx = sec.getLength();
      double gasSpeed = Math.abs(sec.getGasVelocity());
      double liqSpeed = Math.abs(sec.getLiquidVelocity());
      double maxMaterialSpeed = Math.max(1.0, Math.max(gasSpeed, liqSpeed));

      // The interfacial pressure term adds a void wave on top of the material velocities.
      if (equations != null) {
        maxMaterialSpeed = Math.max(maxMaterialSpeed, equations.calcVoidWaveSpeed(sec));
      }

      // Include gravity-wave speed for inclined/vertical sections (critical for risers)
      // Gravity waves propagate at ~sqrt(g * D * |sin(theta)| * (rhoL - rhoG) / rhoMix)
      if (enableAdaptiveTimestepping && elevationProfile != null && i < numberOfSections - 1) {
        double sinTheta = Math.abs(elevationProfile[Math.min(i + 1, elevationProfile.length - 1)] - elevationProfile[i])
            / secDx;
        if (sinTheta > 0.01) { // Only for significantly inclined sections
          double rhoG = Math.max(sec.getGasDensity(), 0.1);
          double rhoL = Math.max(sec.getLiquidDensity(), 100.0);
          double alphaG = sec.getGasHoldup();
          double alphaL = sec.getLiquidHoldup();
          double rhoMix = alphaG * rhoG + alphaL * rhoL;
          if (rhoMix > 0.1) {
            double gravWaveSpeed = Math.sqrt(9.81 * diameter * sinTheta * Math.abs(rhoL - rhoG) / rhoMix);
            maxMaterialSpeed = Math.max(maxMaterialSpeed, gravWaveSpeed);
          }
        }
      }

      minDt = Math.min(minDt, cflNumber * secDx / maxMaterialSpeed);
    }

    return minDt;
  }

  /**
   * Update thermodynamic properties using flash calculations.
   */
  private void updateThermodynamics() {
    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
      TwoFluidSection sec = sections[sectionIndex];
      try {
        SystemInterface flash;
        if (componentTransportEnabled && componentTransport != null) {
          // The flash is reconstructed from the conservative cell inventory. It may
          // update phase properties and identity, but never overwrites component mass.
          flash = componentTransport.createThermodynamicState(sectionIndex, referenceFluid, sec.getPressure(),
              sec.getTemperature());
        } else {
          flash = referenceFluid.clone();
          flash.setPressure(sec.getPressure() / 1e5, "bara"); // Convert Pa to bar
          flash.setTemperature(sec.getTemperature(), "K");

          ThermodynamicOperations ops = new ThermodynamicOperations(flash);
          ops.TPflash();
          flash.initPhysicalProperties();
        }

        // Update phase properties
        if (flash.hasPhaseType("gas")) {
          sec.setGasDensity(flash.getPhase("gas").getDensity("kg/m3"));
          sec.setGasViscosity(flash.getPhase("gas").getViscosity("kg/msec"));
          sec.setGasSoundSpeed(flash.getPhase("gas").getSoundSpeed());
          sec.setGasEnthalpy(flash.getPhase("gas").getEnthalpy("J/kg"));
        }

        // Handle liquid phases (oil, water, or both)
        boolean hasOil = flash.hasPhaseType("oil");
        boolean hasWater = flash.hasPhaseType("aqueous");

        if (hasOil && hasWater) {
          // Three-phase: combine oil and water as effective liquid
          double rhoOil = flash.getPhase("oil").getDensity("kg/m3");
          double rhoWater = flash.getPhase("aqueous").getDensity("kg/m3");
          double muOil = flash.getPhase("oil").getViscosity("kg/msec");
          double muWater = flash.getPhase("aqueous").getViscosity("kg/msec");
          double volOil = phaseVolumetricFlow(flash, "oil");
          double volWater = phaseVolumetricFlow(flash, "aqueous");
          double volLiquid = volOil + volWater;

          double waterCut = volLiquid > 0 ? volWater / volLiquid : 0;
          double oilFraction = 1.0 - waterCut;
          sec.setInputWaterVolumeFraction(waterCut);

          // Update individual phase properties for three-phase tracking
          sec.setOilDensity(rhoOil);
          sec.setWaterDensity(rhoWater);
          sec.setOilViscosity(muOil);
          sec.setWaterViscosity(muWater);

          // Volume-weighted density
          sec.setLiquidDensity(oilFraction * rhoOil + waterCut * rhoWater);

          // Effective viscosity (Brinkman)
          double muL;
          if (oilFraction > 0.5) {
            muL = muOil * Math.pow(1.0 - waterCut, -2.5);
          } else {
            muL = muWater * Math.pow(1.0 - oilFraction, -2.5);
          }
          sec.setLiquidViscosity(muL);
          sec.setLiquidSoundSpeed(flash.getPhase("oil").getSoundSpeed());
          sec.setLiquidEnthalpy(oilFraction * flash.getPhase("oil").getEnthalpy("J/kg")
              + waterCut * flash.getPhase("aqueous").getEnthalpy("J/kg"));

        } else if (hasOil) {
          double rhoOil = flash.getPhase("oil").getDensity("kg/m3");
          sec.setLiquidDensity(rhoOil);
          sec.setOilDensity(rhoOil);
          sec.setOilViscosity(flash.getPhase("oil").getViscosity("kg/msec"));
          sec.setLiquidViscosity(flash.getPhase("oil").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("oil").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("oil").getEnthalpy("J/kg"));
          sec.setWaterCut(0.0);
          sec.setOilFractionInLiquid(1.0);
          sec.setWaterHoldup(0.0);
          sec.setOilHoldup(sec.getLiquidHoldup());
        } else if (hasWater) {
          double rhoWater = flash.getPhase("aqueous").getDensity("kg/m3");
          sec.setLiquidDensity(rhoWater);
          sec.setWaterDensity(rhoWater);
          sec.setWaterViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
          sec.setLiquidViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("aqueous").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("aqueous").getEnthalpy("J/kg"));
          sec.setWaterCut(1.0);
          sec.setOilFractionInLiquid(0.0);
          sec.setWaterHoldup(sec.getLiquidHoldup());
          sec.setOilHoldup(0.0);
        }
      } catch (Exception e) {
        if (componentTransportEnabled) {
          throw new IllegalStateException("Component thermodynamic synchronization failed for section at position "
              + sec.getPosition() + ": " + e.getMessage(), e);
        }
        logger.warn("Flash calculation failed for section at position {}", sec.getPosition());
      }
    }
  }

  /**
   * Apply pressure and phase densities from the latest coupled correction.
   *
   * @param state corrected conservative state
   */
  private void applyCoupledPressureMomentumState(double[][] state) {
    double[] pressure = timeIntegrator.getCoupledPressureMomentumPressure();
    double[] gasDensity = timeIntegrator.getCoupledPressureMomentumGasDensity();
    double[] oilDensity = timeIntegrator.getCoupledPressureMomentumOilDensity();
    double[] waterDensity = timeIntegrator.getCoupledPressureMomentumWaterDensity();
    if (pressure == null
        || gasDensity == null
        || oilDensity == null
        || waterDensity == null
        || pressure.length != numberOfSections) {
      throw new IllegalStateException(
          "Coupled pressure-momentum correction did not return a complete cell state");
    }

    for (int cell = 0; cell < numberOfSections; cell++) {
      TwoFluidSection section = sections[cell];
      section.setPressure(pressure[cell]);
      section.setGasDensity(gasDensity[cell]);
      section.setOilDensity(oilDensity[cell]);
      section.setWaterDensity(waterDensity[cell]);

      double oilMass = Math.max(state[cell][TwoFluidConservationEquations.IDX_OIL_MASS], 0.0);
      double waterMass = Math.max(state[cell][TwoFluidConservationEquations.IDX_WATER_MASS], 0.0);
      double liquidVolume =
          oilMass / Math.max(oilDensity[cell], CLOSURE_DENOMINATOR_EPSILON)
              + waterMass / Math.max(waterDensity[cell], CLOSURE_DENOMINATOR_EPSILON);
      if (liquidVolume > CLOSURE_DENOMINATOR_EPSILON) {
        section.setLiquidDensity((oilMass + waterMass) / liquidVolume);
      }
    }
  }

  /**
   * Reconstruct pressure profile from the evolved conservative variables.
   *
   * <p>
   * After mass and momentum are updated by the time integrator, the pressure at each section must be recomputed. The
   * method handles different boundary condition types:
   * </p>
   * <ul>
   * <li>CONSTANT_PRESSURE: Fixed pressure (Dirichlet BC) - backward march from outlet</li>
   * <li>CLOSED: Zero gradient (Neumann BC, dp/dx = 0) - pressure floats based on interior</li>
   * </ul>
   *
   * <p>
   * When both boundaries are CLOSED, pressure evolves from the interior state without external forcing, allowing the
   * system to reach true equilibrium.
   * </p>
   */
  private void reconstructPressureProfile() {
    if (sections == null || numberOfSections < 2) {
      return;
    }

    // Handle different boundary condition combinations
    boolean outletClosed = (outletBCType == BoundaryCondition.CLOSED);
    boolean inletClosed = (inletBCType == BoundaryCondition.CLOSED);
    boolean outletCharacteristic = (outletBCType == BoundaryCondition.CHARACTERISTIC);

    if (outletClosed && inletClosed) {
      // BOTH ENDS CLOSED: True closed system
      // Pressure evolves from interior dynamics only (no external forcing)
      // Use momentum-based pressure reconstruction from interior reference
      reconstructPressureClosedSystem();
    } else if (outletClosed) {
      // OUTLET CLOSED: Use Neumann BC (dp/dx = 0) at outlet
      // Pressure at outlet equals interior cell adjacent to it
      // March forward from inlet (if constant flow/pressure) or use interior reference
      reconstructPressureOutletClosed();
    } else if (outletCharacteristic) {
      // CHARACTERISTIC outlet: pressure is set later by applyCharacteristicOutletBC()
      // For now, do backward march from current outlet pressure (which from previous step
      // was set by the characteristic solver). This provides a smooth profile.
      for (int i = numberOfSections - 2; i >= 0; i--) {
        TwoFluidSection sec = sections[i];
        TwoFluidSection downstream = sections[i + 1];
        double dPdx = estimatePressureGradient(sec);
        double P_new = downstream.getPressure() + dPdx * sec.getLength();
        P_new = Math.max(1e5, P_new);
        sec.setPressure(P_new);
      }
    } else {
      // OUTLET OPEN (constant pressure): Standard backward march
      // Start from outlet with known pressure (Dirichlet BC)
      sections[numberOfSections - 1].setPressure(outletPressure);

      // March backward from outlet to inlet
      for (int i = numberOfSections - 2; i >= 0; i--) {
        TwoFluidSection sec = sections[i];
        TwoFluidSection downstream = sections[i + 1];

        // Local pressure gradient from current section properties
        double dPdx = estimatePressureGradient(sec);

        // P_upstream = P_downstream + dPdx * dx_i (pressure increases going upstream)
        double P_new = downstream.getPressure() + dPdx * sec.getLength();

        // Ensure physically reasonable bound
        P_new = Math.max(1e5, P_new); // Minimum 1 bar

        sec.setPressure(P_new);
      }
    }

    // Apply Neumann BC at inlet if CLOSED (after reconstruction)
    if (inletClosed && !outletClosed) {
      // Inlet pressure = adjacent interior cell (dp/dx = 0)
      sections[0].setPressure(sections[1].getPressure());
    }
  }

  /**
   * Reconstruct pressure for fully closed system (both ends CLOSED).
   *
   * <p>
   * In a closed system, there is no external pressure reference. The simplest stable approach is to NOT modify interior
   * pressures (let them evolve naturally from the transient momentum equations), and ONLY apply Neumann BC at the
   * boundaries.
   * </p>
   *
   * <p>
   * The key insight: the time integrator has already evolved the conservative variables (mass, momentum) consistently.
   * We should not fight that by imposing additional pressure changes. Instead, we just ensure the boundaries have zero
   * pressure gradient.
   * </p>
   */
  private void reconstructPressureClosedSystem() {
    // Do NOT modify interior pressures - they evolve naturally from momentum balance
    // Only apply Neumann BC at boundaries (dp/dx = 0)

    // Outlet: P_outlet = P_interior (copy from adjacent interior cell)
    sections[numberOfSections - 1].setPressure(sections[numberOfSections - 2].getPressure());

    // Inlet: P_inlet = P_interior (copy from adjacent interior cell)
    sections[0].setPressure(sections[1].getPressure());
  }

  /**
   * Reconstruct pressure when outlet is CLOSED (Neumann BC at outlet).
   *
   * <p>
   * Use forward marching from inlet reference, then apply Neumann BC at outlet.
   * </p>
   */
  private void reconstructPressureOutletClosed() {
    // Get inlet reference pressure
    double P_inlet;
    if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE && inletPressureSet) {
      P_inlet = inletPressure;
    } else if (inletBCType == BoundaryCondition.STREAM_CONNECTED) {
      P_inlet = getInletStream().getFluid().getPressure("Pa");
    } else {
      // Use current inlet section pressure as reference
      P_inlet = sections[0].getPressure();
    }

    sections[0].setPressure(P_inlet);

    // March forward from inlet to outlet
    for (int i = 1; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      TwoFluidSection upstream = sections[i - 1];

      // Local pressure gradient from upstream section
      double dPdx = estimatePressureGradient(upstream);

      // P_downstream = P_upstream - dPdx * dx_i (pressure decreases going downstream)
      double P_new = upstream.getPressure() - dPdx * upstream.getLength();

      // Ensure physically reasonable bound
      P_new = Math.max(1e5, P_new); // Minimum 1 bar

      sec.setPressure(P_new);
    }

    // Apply Neumann BC at outlet: P_outlet = P_interior (dp/dx = 0)
    sections[numberOfSections - 1].setPressure(sections[numberOfSections - 2].getPressure());
  }

  /**
   * Apply boundary conditions.
   */
  private void applyBoundaryConditions() {
    // Inlet boundary
    TwoFluidSection inlet = sections[0];
    if (inletBCType == BoundaryCondition.STREAM_CONNECTED) {
      // Use inlet stream properties for flow rate and composition
      // NOTE: Inlet PRESSURE is NOT set from stream during transient.
      // It comes from reconstructPressureProfile (backward march from outlet BC).
      // Only the steady-state solver sets inlet P from stream.
      SystemInterface inFluid = getInletStream().getFluid();
      if (!isTransientMode) {
        inlet.setPressure(inFluid.getPressure("Pa"));
      }
      inlet.setTemperature(inFluid.getTemperature("K"));

      // Calculate target mass flow rates from inlet stream
      double massFlow = getInletStream().getFlowRate("kg/sec");
      double area = Math.PI * diameter * diameter / 4.0;

      // Get phase mass fractions from inlet stream (these define the BC).
      double[] phaseMassFractions = calculateInletPhaseMassFractions(inFluid);
      double gasMassFraction = phaseMassFractions[0];
      double oilMassFraction = phaseMassFractions[1];
      double waterMassFraction = phaseMassFractions[2];

      double mDotGas = massFlow * gasMassFraction;
      double mDotOil = massFlow * oilMassFraction;
      double mDotWater = massFlow * waterMassFraction;
      double mDotLiq = mDotOil + mDotWater;

      // Update densities from flash for accurate velocity calculation
      double rhoG = inlet.getGasDensity();
      double rhoOil = inlet.getOilDensity() > 100 ? inlet.getOilDensity() : 700.0;
      double rhoWater = inlet.getWaterDensity() > 100 ? inlet.getWaterDensity() : 1000.0;

      if (inFluid.hasPhaseType("gas")) {
        rhoG = inFluid.getPhase("gas").getDensity("kg/m3");
        inlet.setGasDensity(rhoG);
      }
      if (inFluid.hasPhaseType("oil")) {
        rhoOil = inFluid.getPhase("oil").getDensity("kg/m3");
        inlet.setOilDensity(rhoOil);
        inlet.setLiquidDensity(rhoOil);
      }
      if (inFluid.hasPhaseType("aqueous")) {
        rhoWater = inFluid.getPhase("aqueous").getDensity("kg/m3");
        inlet.setWaterDensity(rhoWater);
      }

      // Get current inlet holdups (from solver state)
      double alphaG = inlet.getGasHoldup();
      double alphaL = inlet.getLiquidHoldup();

      // Calculate velocities to maintain inlet mass flow rates
      // mDot = alpha * rho * v * A => v = mDot / (alpha * rho * A)
      double rhoL = inlet.getLiquidDensity() > 100 ? inlet.getLiquidDensity() : 700.0;
      double vG = calculateFinitePhaseVelocity(mDotGas, alphaG, rhoG, area, 100.0);
      double vL = calculateFinitePhaseVelocity(mDotLiq, alphaL, rhoL, area, 50.0);
      double vOil = mDotOil == 0.0 ? 0.0 : vL;
      double vWater = mDotWater == 0.0 ? 0.0 : vL;

      inlet.setGasVelocity(vG);
      inlet.setLiquidVelocity(vL);
      inlet.setOilVelocity(vOil);
      inlet.setWaterVelocity(vWater);

      // CRITICAL: Enforce inlet water cut from inlet stream
      // The inlet section should have the water cut defined by the inlet stream,
      // not whatever the solver computed. This is a Dirichlet BC for water cut.
      double inletWaterCut = 0.01; // Default
      if (mDotLiq > 0) {
        // Calculate water cut from volume fractions
        if (inFluid.hasPhaseType("oil") && inFluid.hasPhaseType("aqueous")) {
          double volOil = phaseVolumetricFlow(inFluid, "oil");
          double volWater = phaseVolumetricFlow(inFluid, "aqueous");
          if (volOil + volWater > 0) {
            inletWaterCut = volWater / (volOil + volWater);
          }
        } else if (inFluid.hasPhaseType("aqueous")) {
          inletWaterCut = 1.0;
        } else {
          inletWaterCut = 0.0;
        }
      }

      // Apply inlet water cut to redistribute oil and water holdups
      double alphaW_target = alphaL * inletWaterCut;
      double alphaO_target = alphaL * (1.0 - inletWaterCut);
      inlet.setInputWaterVolumeFraction(inletWaterCut);
      inlet.setWaterCut(inletWaterCut);
      inlet.setOilFractionInLiquid(1.0 - inletWaterCut);
      inlet.setWaterHoldup(alphaW_target);
      inlet.setOilHoldup(alphaO_target);

      // The inlet flux uses these primitive boundary values. Do not overwrite the
      // finite-volume phase masses: they are cell inventory advanced by the PDE.
      // Replacing them here would create a domain-volume-scaled mass impulse.
      inlet.setGasMomentumPerLength(inlet.getGasMassPerLength() * inlet.getGasVelocity());
      inlet.setOilMomentumPerLength(inlet.getOilMassPerLength() * inlet.getOilVelocity());
      inlet.setWaterMomentumPerLength(inlet.getWaterMassPerLength() * inlet.getWaterVelocity());
      inlet.setLiquidMomentumPerLength(inlet.getLiquidMassPerLength() * inlet.getLiquidVelocity());
    } else if (inletBCType == BoundaryCondition.CONSTANT_FLOW && inletMassFlowSet) {
      // Use explicit mass flow value (temperature/composition still from inlet stream)
      SystemInterface inFluid = getInletStream().getFluid();
      inlet.setTemperature(inFluid.getTemperature("K"));

      double massFlow = inletMassFlow; // Explicit mass flow BC
      double area = Math.PI * diameter * diameter / 4.0;

      // Get phase mass fractions from inlet stream.
      double[] phaseMassFractions = calculateInletPhaseMassFractions(inFluid);
      double gasMassFraction = phaseMassFractions[0];
      double oilMassFraction = phaseMassFractions[1];
      double waterMassFraction = phaseMassFractions[2];

      double mDotGas = massFlow * gasMassFraction;
      double mDotOil = massFlow * oilMassFraction;
      double mDotWater = massFlow * waterMassFraction;
      double mDotLiq = mDotOil + mDotWater;

      // Update densities from inlet fluid
      double rhoG = inlet.getGasDensity();
      if (inFluid.hasPhaseType("gas")) {
        rhoG = inFluid.getPhase("gas").getDensity("kg/m3");
        inlet.setGasDensity(rhoG);
      }
      if (inFluid.hasPhaseType("oil")) {
        inlet.setOilDensity(inFluid.getPhase("oil").getDensity("kg/m3"));
        inlet.setLiquidDensity(inFluid.getPhase("oil").getDensity("kg/m3"));
      }

      double alphaG = inlet.getGasHoldup();
      double alphaL = inlet.getLiquidHoldup();

      // Calculate velocities to achieve target mass flow
      double rhoL = inlet.getLiquidDensity() > 100 ? inlet.getLiquidDensity() : 700.0;
      double vG = calculateFinitePhaseVelocity(mDotGas, alphaG, rhoG, area, 100.0);
      double vL = calculateFinitePhaseVelocity(mDotLiq, alphaL, rhoL, area, 50.0);

      inlet.setGasVelocity(vG);
      inlet.setLiquidVelocity(vL);
      inlet.setOilVelocity(mDotOil == 0.0 ? 0.0 : vL);
      inlet.setWaterVelocity(mDotWater == 0.0 ? 0.0 : vL);

      inlet.setGasMomentumPerLength(inlet.getGasMassPerLength() * vG);
      inlet.setLiquidMomentumPerLength(inlet.getLiquidMassPerLength() * vL);
    } else if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE && inletPressureSet) {
      // Fix inlet pressure (transient: flow rate computed from momentum balance)
      inlet.setPressure(inletPressure);
      inlet.setTemperature(getInletStream().getFluid().getTemperature("K"));
    } else if (inletBCType == BoundaryCondition.CLOSED) {
      // Zero velocity at inlet (blocked/no inflow condition)
      // Pressure floats based on mass loss through outlet
      inlet.setGasVelocity(0.0);
      inlet.setLiquidVelocity(0.0);
      inlet.setOilVelocity(0.0);
      inlet.setWaterVelocity(0.0);
      inlet.setGasMomentumPerLength(0.0);
      inlet.setLiquidMomentumPerLength(0.0);
      inlet.setOilMomentumPerLength(0.0);
      inlet.setWaterMomentumPerLength(0.0);
    } else if (inletBCType == BoundaryCondition.CHARACTERISTIC) {
      // Riemann-invariant-based inlet boundary (Toro 2009, Chapter 6)
      // At the inlet, the flow is typically subsonic. In a 1D hyperbolic system the
      // characteristics carry information along dx/dt = v ± c.
      // Incoming characteristic (from outside): carries boundary data (mass flow, composition)
      // Outgoing characteristic (from interior): extrapolated from interior cells
      //
      // Gas Riemann invariants: J+ = v + 2c/(gamma-1), J- = v - 2c/(gamma-1)
      // For an inlet: J+ is prescribed from external data, J- comes from interior.
      applyCharacteristicInletBC(inlet);
    }

    // Outlet boundary
    TwoFluidSection outlet = sections[numberOfSections - 1];
    if (outletBCType == BoundaryCondition.CONSTANT_PRESSURE) {
      outlet.setPressure(outletPressure);
    } else if (outletBCType == BoundaryCondition.CLOSED) {
      // Zero velocity at outlet (blocked/shut-in condition)
      // Pressure floats based on mass accumulation
      outlet.setGasVelocity(0.0);
      outlet.setLiquidVelocity(0.0);
      outlet.setOilVelocity(0.0);
      outlet.setWaterVelocity(0.0);
      outlet.setGasMomentumPerLength(0.0);
      outlet.setLiquidMomentumPerLength(0.0);
      outlet.setOilMomentumPerLength(0.0);
      outlet.setWaterMomentumPerLength(0.0);
    } else if (outletBCType == BoundaryCondition.CHARACTERISTIC) {
      // Riemann-invariant-based outlet boundary
      // At the outlet: J- is prescribed (typically from back-pressure), J+ from interior.
      applyCharacteristicOutletBC(outlet);
    }
  }

  /**
   * Calculate gas, oil, and water mass fractions from the inlet stream.
   *
   * @param inFluid inlet fluid
   * @return array containing gas, oil, and water mass fractions
   */
  /**
   * Get the volumetric flow of one phase as mass flow divided by density.
   *
   * <p>
   * {@code PhaseInterface.getVolume()} reports the untranslated equation-of-state volume, so when a Peneloux volume
   * shift is active it disagrees with {@code getDensity()} by that shift. The error is negligible for gas but reaches
   * roughly 17% for oil and 32% for water on a typical SRK three-phase system, which biases every phase fraction built
   * from it. Mass flow and density are mutually consistent, so phase fractions are built from those instead.
   * </p>
   *
   * @param fluid flashed fluid to query
   * @param phaseName phase type name, for example gas, oil, or aqueous
   * @return volumetric flow in m3/s, or zero when the phase is absent
   */
  private static double phaseVolumetricFlow(SystemInterface fluid, String phaseName) {
    if (!fluid.hasPhaseType(phaseName)) {
      return 0.0;
    }
    double density = fluid.getPhase(phaseName).getDensity("kg/m3");
    if (!(density > 0.0)) {
      return 0.0;
    }
    return fluid.getPhase(phaseName).getFlowRate("kg/sec") / density;
  }

  private double[] calculateInletPhaseMassFractions(SystemInterface inFluid) {
    double[] fractions = new double[3];
    double massTotal = inFluid.getFlowRate("kg/sec");

    if (massTotal > 0.0) {
      if (inFluid.hasPhaseType("gas")) {
        fractions[0] = inFluid.getPhase("gas").getFlowRate("kg/sec") / massTotal;
      }
      if (inFluid.hasPhaseType("oil")) {
        fractions[1] = inFluid.getPhase("oil").getFlowRate("kg/sec") / massTotal;
      }
      if (inFluid.hasPhaseType("aqueous")) {
        fractions[2] = inFluid.getPhase("aqueous").getFlowRate("kg/sec") / massTotal;
      }
    }

    double sum = fractions[0] + fractions[1] + fractions[2];
    if (sum <= 0.0) {
      boolean hasGas = inFluid.hasPhaseType("gas");
      boolean hasOil = inFluid.hasPhaseType("oil");
      boolean hasWater = inFluid.hasPhaseType("aqueous");
      int phaseCount = 0;
      if (hasGas) {
        phaseCount++;
      }
      if (hasOil) {
        phaseCount++;
      }
      if (hasWater) {
        phaseCount++;
      }
      if (phaseCount == 0) {
        fractions[0] = 1.0;
      } else {
        double equalFraction = 1.0 / phaseCount;
        fractions[0] = hasGas ? equalFraction : 0.0;
        fractions[1] = hasOil ? equalFraction : 0.0;
        fractions[2] = hasWater ? equalFraction : 0.0;
      }
      return fractions;
    }

    fractions[0] /= sum;
    fractions[1] /= sum;
    fractions[2] /= sum;
    return fractions;
  }

  /**
   * Apply characteristic (Riemann-invariant) boundary condition at inlet.
   *
   * <p>
   * For subsonic inflow, there are two incoming characteristics (one from outside with boundary data, one from
   * interior). The gas-phase Riemann invariants are:
   * </p>
   * <ul>
   * <li>J+ = v + 2c/(gamma-1) : right-running, carries information from outside at inlet</li>
   * <li>J- = v - 2c/(gamma-1) : left-running, carries information from interior</li>
   * </ul>
   *
   * <p>
   * At the inlet, J+ is specified from the boundary data (inlet stream) and J- is extrapolated from the interior (cell
   * 1). The boundary state is solved from the intersection.
   * </p>
   *
   * @param inlet the inlet section to update
   */
  private void applyCharacteristicInletBC(TwoFluidSection inlet) {
    // Get interior state from cell 1 (first interior cell)
    TwoFluidSection interior = sections[1];

    // Gas phase: use isentropic gas relations with effective gamma
    double gammaEff = 1.3; // Effective ratio of specific heats for gas-condensate
    double cInt = Math.max(interior.getGasSoundSpeed(), 1.0);
    double vInt = interior.getGasVelocity();

    // Outgoing characteristic from interior (J- travels leftward out of domain)
    double Jminus = vInt - 2.0 * cInt / (gammaEff - 1.0);

    // Incoming characteristic from boundary (J+ carries inflow data)
    // Use inlet stream mass flow to determine target velocity
    SystemInterface inFluid = getInletStream().getFluid();
    double massFlow = getInletStream().getFlowRate("kg/sec");
    double area = Math.PI * diameter * diameter / 4.0;
    double rhoG = inlet.getGasDensity();
    if (inFluid.hasPhaseType("gas")) {
      rhoG = inFluid.getPhase("gas").getDensity("kg/m3");
    }
    double alphaG = inlet.getGasHoldup();
    double[] phaseMassFractions = calculateInletPhaseMassFractions(inFluid);
    double vTarget = calculateFinitePhaseVelocity(massFlow * phaseMassFractions[0], alphaG, rhoG, area, 100.0);

    double cTarget = Math.max(inlet.getGasSoundSpeed(), 1.0);
    double Jplus = vTarget + 2.0 * cTarget / (gammaEff - 1.0);

    // Solve for boundary state: v_b = (J+ + J-) / 2, c_b = (gamma-1)/4 * (J+ - J-)
    double vBoundary = 0.5 * (Jplus + Jminus);
    double cBoundary = 0.25 * (gammaEff - 1.0) * (Jplus - Jminus);
    cBoundary = Math.max(cBoundary, 1.0);

    // Corresponding pressure: P_b = P_int * (c_b / c_int)^(2*gamma/(gamma-1))
    double pressureRatio = Math.pow(cBoundary / cInt, 2.0 * gammaEff / (gammaEff - 1.0));
    double Pb = interior.getPressure() * pressureRatio;
    Pb = Math.max(1e5, Pb); // minimum 1 bar

    // Apply to inlet section
    inlet.setGasVelocity(vBoundary);
    inlet.setPressure(Pb);
    inlet.setTemperature(inFluid.getTemperature("K"));

    // Liquid phase: use simple upwind for liquid velocity (subsonic liquid always has
    // both characteristics entering at inlet for typical subsonic liquid velocities)
    double rhoL = inlet.getLiquidDensity() > 100 ? inlet.getLiquidDensity() : 700.0;
    double alphaL = inlet.getLiquidHoldup();
    double mDotOil = massFlow * phaseMassFractions[1];
    double mDotWater = massFlow * phaseMassFractions[2];
    double mDotLiq = mDotOil + mDotWater;
    double vL = calculateFinitePhaseVelocity(mDotLiq, alphaL, rhoL, area, 50.0);
    inlet.setLiquidVelocity(vL);
    inlet.setOilVelocity(mDotOil == 0.0 ? 0.0 : vL);
    inlet.setWaterVelocity(mDotWater == 0.0 ? 0.0 : vL);

    // Update momenta consistently
    inlet.setGasMomentumPerLength(inlet.getGasMassPerLength() * vBoundary);
    inlet.setLiquidMomentumPerLength(inlet.getLiquidMassPerLength() * vL);
    inlet.setOilMomentumPerLength(inlet.getOilMassPerLength() * vL);
    inlet.setWaterMomentumPerLength(inlet.getWaterMassPerLength() * vL);
  }

  /**
   * Apply characteristic (Riemann-invariant) boundary condition at outlet.
   *
   * <p>
   * For subsonic outflow, there is one incoming characteristic from outside (back-pressure) and one outgoing from
   * interior. At the outlet:
   * </p>
   * <ul>
   * <li>J- = v - 2c/(gamma-1) : left-running, carries boundary data (back-pressure)</li>
   * <li>J+ = v + 2c/(gamma-1) : right-running, carries information from interior</li>
   * </ul>
   *
   * @param outlet the outlet section to update
   */
  private void applyCharacteristicOutletBC(TwoFluidSection outlet) {
    // Get interior state from second-to-last cell
    TwoFluidSection interior = sections[numberOfSections - 2];

    double gammaEff = 1.3;
    double cInt = Math.max(interior.getGasSoundSpeed(), 1.0);
    double vInt = interior.getGasVelocity();

    // Outgoing characteristic from interior (J+ travels rightward out of domain)
    double Jplus = vInt + 2.0 * cInt / (gammaEff - 1.0);

    // Incoming characteristic from boundary (J- carries back-pressure data)
    // Back-pressure determines the target pressure at outlet
    double Ptarget = outletPressureSet ? outletPressure : outlet.getPressure();
    double rhoOutlet = Math.max(outlet.getGasDensity(), 0.1);

    // Sound speed at target pressure (isentropic relation)
    double cTarget = cInt
        * Math.pow(Ptarget / Math.max(interior.getPressure(), 1e5), (gammaEff - 1.0) / (2.0 * gammaEff));
    cTarget = Math.max(cTarget, 1.0);

    // Estimate velocity at target state for J-
    double vTarget = outlet.getGasVelocity();
    double Jminus = vTarget - 2.0 * cTarget / (gammaEff - 1.0);

    // Solve for boundary state
    double vBoundary = 0.5 * (Jplus + Jminus);
    double cBoundary = 0.25 * (gammaEff - 1.0) * (Jplus - Jminus);
    cBoundary = Math.max(cBoundary, 1.0);

    // Pressure from characteristic sound speed
    double pressureRatio = Math.pow(cBoundary / cInt, 2.0 * gammaEff / (gammaEff - 1.0));
    double Pb = interior.getPressure() * pressureRatio;
    Pb = Math.max(1e5, Pb);

    // Apply: use characteristic pressure (which will be close to target for subsonic)
    outlet.setPressure(Pb);
    outlet.setGasVelocity(Math.max(0, vBoundary)); // only outflow

    // Liquid: extrapolate from interior (outgoing information for subsonic liquid)
    double vLInt = interior.getLiquidVelocity();
    outlet.setLiquidVelocity(Math.max(0, vLInt));
    outlet.setOilVelocity(Math.max(0, vLInt));
    outlet.setWaterVelocity(Math.max(0, vLInt));

    // Update momenta
    outlet.setGasMomentumPerLength(outlet.getGasMassPerLength() * outlet.getGasVelocity());
    outlet.setLiquidMomentumPerLength(outlet.getLiquidMassPerLength() * outlet.getLiquidVelocity());
    outlet.setOilMomentumPerLength(outlet.getOilMassPerLength() * outlet.getOilVelocity());
    outlet.setWaterMomentumPerLength(outlet.getWaterMassPerLength() * outlet.getWaterVelocity());
  }

  /**
   * Update outlet stream with current outlet conditions.
   *
   * <p>
   * Steady-state calculations use the inlet mass flow to enforce global steady closure. After a transient call, the
   * stream exposes the interval-average total outlet flux integrated over the accepted internal stages. Phase-resolved
   * integrals remain available from {@link #getLastMassBalanceReport()}.
   * </p>
   */
  private void updateOutletStream() {
    if (sections == null || sections.length == 0) {
      return;
    }

    TwoFluidSection outlet = sections[numberOfSections - 1];
    SystemInterface outFluid = getInletStream().getFluid().clone();

    outFluid.setPressure(outlet.getPressure() / 1e5, "bara");
    outFluid.setTemperature(outlet.getTemperature(), "K");

    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(outFluid);
      ops.TPflash();
    } catch (Exception e) {
      logger.warn("Outlet flash failed: {}", e.getMessage());
    }

    // Calculate outlet mass flow rate from section state
    double area = Math.PI * diameter * diameter / 4.0;
    double alphaG = outlet.getGasHoldup();
    double alphaL = outlet.getLiquidHoldup();
    double rhoG = outlet.getGasDensity();
    double rhoL = outlet.getLiquidDensity();
    double vG = outlet.getGasVelocity();
    double vL = outlet.getLiquidVelocity();

    // Mass flow from section state (for diagnostics)
    double massFlowFromState = (alphaG * rhoG * vG + alphaL * rhoL * vL) * area;

    // In steady state, mass conservation requires inlet flow = outlet flow. The
    // section-level velocities come from momentum correlations that may not be
    // perfectly consistent with total mass flux.
    double massFlowIn = getInletStream().getFlowRate("kg/sec");
    double massFlowOut = massFlowIn;

    // Transient downstream equipment must see transport and inventory effects,
    // not the current inlet boundary. Use the accepted interval-average outlet
    // flux assembled with the same stage weights as the conservative update.
    if (lastMassBalanceReport != null && lastMassBalanceReport.getElapsedTimeSeconds() > 0.0) {
      massFlowOut = lastMassBalanceReport.getOutletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL)
          / lastMassBalanceReport.getElapsedTimeSeconds();
    }

    if (massFlowFromState > 0.0 && massFlowOut > 0.0 && Math.abs(massFlowFromState - massFlowOut) / massFlowOut > 0.1) {
      logger.debug("Outlet section state mass flow ({} kg/s) differs from published outlet ({} kg/s) by {}%",
          massFlowFromState, massFlowOut, 100.0 * Math.abs(massFlowFromState - massFlowOut) / massFlowOut);
    }

    if (!Double.isFinite(massFlowOut)) {
      throw new IllegalStateException(
          "Outlet mass flow must be finite: outlet=" + massFlowOut + " kg/s, inlet=" + massFlowIn + " kg/s");
    }
    outFluid.setTotalFlowRate(Math.max(0.0, massFlowOut), "kg/sec");

    getOutletStream().setFluid(outFluid);
  }

  /**
   * Update result arrays from section states.
   *
   * <p>
   * Array length equals numberOfSections, with each element representing the section midpoint values.
   * </p>
   */
  private void updateResultArrays() {
    if (sections == null) {
      return;
    }

    // Array length equals number of sections
    pressureProfile = new double[numberOfSections];
    temperatureProfile = new double[numberOfSections];
    liquidHoldupProfile = new double[numberOfSections];
    gasVelocityProfile = new double[numberOfSections];
    liquidVelocityProfile = new double[numberOfSections];

    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      pressureProfile[i] = sec.getPressure();
      temperatureProfile[i] = sec.getTemperature();
      liquidHoldupProfile[i] = sec.getLiquidHoldup();
      gasVelocityProfile[i] = sec.getGasVelocity();
      liquidVelocityProfile[i] = sec.getLiquidVelocity();
    }
  }

  // ============ Result access methods ============

  /**
   * Get the total gas, oil, and water mass stored in the computational domain.
   *
   * <p>
   * The inventory is integrated directly from the conservative phase masses per unit length. It is therefore suitable
   * for checking the finite-volume balance {@code M(t + dt) - M(t) = integral(mDotIn - mDotOut) dt} for cases without
   * external mass sources.
   * </p>
   *
   * @return total domain mass in kg
   */
  public double getTotalMassInventory() {
    double[] phaseMasses = getPhaseMassInventoriesKg();
    return phaseMasses[0] + phaseMasses[1] + phaseMasses[2];
  }

  private double[] getPhaseMassInventoriesKg() {
    double[] phaseMasses = new double[3];
    if (sections == null) {
      return phaseMasses;
    }

    for (TwoFluidSection sec : sections) {
      double sectionLength = sec.getLength();
      phaseMasses[0] += sec.getGasMassPerLength() * sectionLength;
      phaseMasses[1] += sec.getOilMassPerLength() * sectionLength;
      phaseMasses[2] += sec.getWaterMassPerLength() * sectionLength;
    }
    return phaseMasses;
  }

  /**
   * Get the discrete mass balance from the most recent {@link #runTransient(double, UUID)} call.
   *
   * <p>
   * Boundary fluxes and source terms are integrated with the same stage weights as the configured time integrator. The
   * report includes gas, oil, water, combined-liquid, and total residuals in kg and relative form. A steady-state
   * {@link #run(UUID)} clears the previous report.
   * </p>
   *
   * @return last transient mass-balance report, or {@code null} before a transient call
   */
  public TwoFluidMassBalanceReport getLastMassBalanceReport() {
    return lastMassBalanceReport;
  }

  /**
   * Get the thermal-energy balance from the most recent transient thermal update.
   *
   * <p>
   * The report integrates fluid and wall energy changes, conservative-face sensible advection, the optional
   * Joule-Thomson source, component-resolved interphase latent heat, and ambient heat loss over the accepted internal
   * substeps. It is intended for closed-domain thermal validation; its stored-energy terms do not make it a complete
   * control-volume energy balance for open-boundary inventory changes. It is cleared by steady-state
   * {@link #run(UUID)}. Without component transport it remains {@code null} when external heat transfer is disabled;
   * component transport evaluates the thermal step even with zero external heat transfer so sensible advection and
   * latent heat remain coupled.
   * </p>
   *
   * @return last thermal-energy balance report, or {@code null} when no thermal transient was evaluated
   */
  public TwoFluidThermalEnergyBalanceReport getLastThermalEnergyBalanceReport() {
    return lastThermalEnergyBalanceReport;
  }

  /**
   * Enable conservative, component-resolved transport in every gas, oil, and water cell inventory.
   *
   * <p>
   * This opt-in path uses the accepted hydrodynamic phase face fluxes and interphase source terms. Enable it before
   * {@link #run(UUID)} so the distributed component state can be initialized from the steady phase inventories.
   * Positive-flow boundaries and an unchanged named component slate are currently required.
   * </p>
   *
   * @param enabled true to track named components conservatively
   */
  public void setComponentTransportEnabled(boolean enabled) {
    componentTransportEnabled = enabled;
    if (!enabled) {
      componentTransport = null;
      lastComponentConservationReport = null;
      componentConservationReports.clear();
      componentConservationTimes.clear();
    }
  }

  /** @return true when component-resolved transport is enabled */
  public boolean isComponentTransportEnabled() {
    return componentTransportEnabled;
  }

  /**
   * Set the fail-loud relative component conservation and synchronization tolerance.
   *
   * @param tolerance positive finite relative tolerance
   */
  public void setComponentConservationTolerance(double tolerance) {
    if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("Component conservation tolerance must be positive and finite");
    }
    componentConservationTolerance = tolerance;
  }

  /** @return configured relative component conservation tolerance */
  public double getComponentConservationTolerance() {
    return componentConservationTolerance;
  }

  /**
   * Configure storage of one immutable component report per accepted outer transient call.
   *
   * @param store true to retain report history after the next steady initialization
   */
  public void setStoreComponentConservationHistory(boolean store) {
    storeComponentConservationHistory = store;
  }

  /** @return true when full outer-step component report history is retained */
  public boolean isComponentConservationHistoryStorageEnabled() {
    return storeComponentConservationHistory;
  }

  /** @return latest immutable component report, or {@code null} before component transport runs */
  public TwoFluidComponentConservationReport getLastComponentConservationReport() {
    return lastComponentConservationReport;
  }

  /**
   * Get immutable, time-aligned component reports retained since the latest steady initialization.
   *
   * @return defensive immutable history
   */
  public TwoFluidComponentConservationHistory getComponentConservationHistory() {
    double[] times = new double[componentConservationTimes.size()];
    for (int index = 0; index < times.length; index++) {
      times[index] = componentConservationTimes.get(index);
    }
    return new TwoFluidComponentConservationHistory(times, componentConservationReports);
  }

  /**
   * Get a physical-cell component mass-fraction profile in one phase.
   *
   * @param phase gas, oil, or water phase identity
   * @param componentName NeqSim component name
   * @return defensive cell profile; empty-phase cells are reported as zero
   */
  public double[] getComponentMassFractionProfile(TwoFluidComponentConservationReport.Phase phase,
      String componentName) {
    if (componentTransport == null) {
      throw new IllegalStateException("Component transport has not been initialized");
    }
    return componentTransport.getMassFractionProfile(componentPhaseIndex(phase), componentName);
  }

  /**
   * Get the outlet-cell component mass fraction in one phase.
   *
   * @param phase gas, oil, or water phase identity
   * @param componentName NeqSim component name
   * @return outlet-cell mass fraction, or zero when the phase is absent
   */
  public double getOutletComponentMassFraction(TwoFluidComponentConservationReport.Phase phase, String componentName) {
    double[] profile = getComponentMassFractionProfile(phase, componentName);
    return profile[profile.length - 1];
  }

  private int componentPhaseIndex(TwoFluidComponentConservationReport.Phase phase) {
    if (phase == null) {
      throw new IllegalArgumentException("Component phase identity cannot be null");
    }
    switch (phase) {
    case GAS:
      return 0;
    case OIL:
      return 1;
    case WATER:
      return 2;
    default:
      throw new IllegalArgumentException("Unsupported component phase identity: " + phase);
    }
  }

  /**
   * Get total liquid inventory in the pipe.
   *
   * <p>
   * Calculates inventory from conservative mass per length, converted to volume using local liquid density. This
   * ensures consistency with the solver's mass tracking.
   * </p>
   *
   * @param unit Volume unit ("m3", "bbl", "L")
   * @return Liquid volume
   */
  public double getLiquidInventory(String unit) {
    double volume = 0;
    double pipeVolume = Math.PI * diameter * diameter / 4.0 * length; // Max possible volume

    for (TwoFluidSection sec : sections) {
      // Calculate oil volume from oil mass and oil density
      double oilMass = sec.getOilMassPerLength() * sec.getLength();
      // Safety check for unreasonable mass values
      double maxMassPerSection = sec.getArea() * sec.getLength() * 1000.0; // Max: all water
      oilMass = Math.min(oilMass, maxMassPerSection);

      double rhoO = sec.getOilDensity();
      if (rhoO > 100) {
        volume += oilMass / rhoO;
      } else if (sec.getLiquidDensity() > 100) {
        volume += oilMass / sec.getLiquidDensity();
      } else {
        volume += oilMass / 700.0; // Default oil density
      }

      // Calculate water volume from water mass and water density
      double waterMass = sec.getWaterMassPerLength() * sec.getLength();
      waterMass = Math.min(waterMass, maxMassPerSection);

      double rhoW = sec.getWaterDensity();
      if (rhoW > 100) {
        volume += waterMass / rhoW;
      } else {
        volume += waterMass / 1000.0; // Default water density
      }
    }

    // Sanity check: volume cannot exceed pipe volume
    volume = Math.min(volume, pipeVolume);

    switch (unit.toLowerCase()) {
    case "bbl":
      return volume * 6.28981;
    case "l":
      return volume * 1000;
    default:
      return volume;
    }
  }

  /**
   * Get pressure profile.
   *
   * @return Pressure at each section (Pa)
   */
  @Override
  public double[] getPressureProfile() {
    return pressureProfile != null ? pressureProfile.clone() : new double[0];
  }

  /**
   * Get temperature profile.
   *
   * @return Temperature at each section (K)
   */
  @Override
  public double[] getTemperatureProfile() {
    return temperatureProfile != null ? temperatureProfile.clone() : new double[0];
  }

  /**
   * Get liquid holdup profile.
   *
   * <p>
   * For consistency with oil and water holdups, the liquid holdup is calculated as the sum of oil and water holdups.
   * </p>
   *
   * @return Holdup at each section (0-1)
   */
  @Override
  public double[] getLiquidHoldupProfile() {
    if (sections == null) {
      return liquidHoldupProfile != null ? liquidHoldupProfile.clone() : new double[0];
    }
    // Return consistent values: liquidHoldup = oilHoldup + waterHoldup
    double[] profile = new double[numberOfSections];

    for (int i = 0; i < numberOfSections; i++) {
      double oilHL = sections[i].getOilHoldup();
      double waterHL = sections[i].getWaterHoldup();
      double sumOilWater = oilHL + waterHL;
      // Use phase-resolved values whenever a liquid phase is present.
      if (sumOilWater > 0.0) {
        profile[i] = sumOilWater;
      } else {
        profile[i] = sections[i].getLiquidHoldup();
      }
    }
    return profile;
  }

  /**
   * Get water cut profile along the pipeline.
   *
   * <p>
   * For three-phase flow, water cut can vary along the pipeline as water accumulates in low spots (valleys) due to its
   * higher density compared to oil.
   * </p>
   *
   * @return Water cut at each section (0-1, fraction of liquid that is water)
   */
  public double[] getWaterCutProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] waterCuts = new double[numberOfSections];

    for (int i = 0; i < numberOfSections; i++) {
      waterCuts[i] = sections[i].getWaterCut();
    }
    return waterCuts;
  }

  /**
   * Get water holdup profile along the pipeline.
   *
   * @return Water holdup at each section (0-1, fraction of pipe area occupied by water)
   */
  public double[] getWaterHoldupProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] waterHoldups = new double[numberOfSections];

    for (int i = 0; i < numberOfSections; i++) {
      waterHoldups[i] = sections[i].getWaterHoldup();
    }
    return waterHoldups;
  }

  /**
   * Get oil holdup profile along the pipeline.
   *
   * @return Oil holdup at each section (0-1, fraction of pipe area occupied by oil)
   */
  public double[] getOilHoldupProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] oilHoldups = new double[numberOfSections];

    for (int i = 0; i < numberOfSections; i++) {
      oilHoldups[i] = sections[i].getOilHoldup();
    }
    return oilHoldups;
  }

  /**
   * Get the per-section oil mass flux along the pipeline.
   *
   * <p>
   * Returns {@code rho_o * alpha_o * A * v_o} for each section. In a converged steady state without mass transfer this
   * profile must be flat and equal to the inlet oil mass flow, so it is the direct check that the oil/water holdup
   * split is closed on the phase mass balance.
   * </p>
   *
   * @return oil mass flow at each section (kg/s)
   */
  public double[] getOilMassFlowProfile() {
    if (sections == null) {
      return new double[0];
    }
    double area = Math.PI * diameter * diameter / 4.0;
    double[] flows = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      flows[i] = sec.getOilDensity() * sec.getOilHoldup() * area * sec.getOilVelocity();
    }
    return flows;
  }

  /**
   * Get the per-section water mass flux along the pipeline.
   *
   * <p>
   * Returns {@code rho_w * alpha_w * A * v_w} for each section. See {@link #getOilMassFlowProfile()} for how to read
   * it.
   * </p>
   *
   * @return water mass flow at each section (kg/s)
   */
  public double[] getWaterMassFlowProfile() {
    if (sections == null) {
      return new double[0];
    }
    double area = Math.PI * diameter * diameter / 4.0;
    double[] flows = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      flows[i] = sec.getWaterDensity() * sec.getWaterHoldup() * area * sec.getWaterVelocity();
    }
    return flows;
  }

  /**
   * Get gas velocity profile.
   *
   * @return Gas velocity at each section (m/s)
   */
  public double[] getGasVelocityProfile() {
    return gasVelocityProfile != null ? gasVelocityProfile.clone() : new double[0];
  }

  /**
   * Get liquid velocity profile.
   *
   * @return Liquid velocity at each section (m/s)
   */
  public double[] getLiquidVelocityProfile() {
    return liquidVelocityProfile != null ? liquidVelocityProfile.clone() : new double[0];
  }

  /**
   * Get oil velocity profile along the pipeline.
   *
   * <p>
   * When water-oil slip is enabled, this returns the independent oil velocity. Otherwise, it returns the combined
   * liquid velocity.
   * </p>
   *
   * @return Oil velocity at each section (m/s)
   */
  public double[] getOilVelocityProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] velocities = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      velocities[i] = sections[i].getOilVelocity();
    }
    return velocities;
  }

  /**
   * Get water velocity profile along the pipeline.
   *
   * <p>
   * When water-oil slip is enabled, this returns the independent water velocity. Otherwise, it returns the combined
   * liquid velocity.
   * </p>
   *
   * @return Water velocity at each section (m/s)
   */
  public double[] getWaterVelocityProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] velocities = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      velocities[i] = sections[i].getWaterVelocity();
    }
    return velocities;
  }

  /**
   * Get oil-water velocity slip profile along the pipeline.
   *
   * <p>
   * Returns the difference between oil and water velocities (vOil - vWater). Positive values indicate oil is flowing
   * faster than water.
   * </p>
   *
   * @return Oil-water slip velocity at each section (m/s)
   */
  public double[] getOilWaterSlipProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] slip = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      slip[i] = sections[i].getOilVelocity() - sections[i].getWaterVelocity();
    }
    return slip;
  }

  /**
   * Get flow regime at each section.
   *
   * @return Array of flow regimes
   */
  public FlowRegime[] getFlowRegimeProfile() {
    if (sections == null) {
      return new FlowRegime[0];
    }
    FlowRegime[] regimes = new FlowRegime[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      regimes[i] = sections[i].getFlowRegime();
    }
    return regimes;
  }

  /**
   * Get oil-water flow regime at each section.
   *
   * @return oil-water flow regime profile; an entry is {@code null} when the closure has not been evaluated
   */
  public OilWaterFlowRegime[] getOilWaterFlowRegimeProfile() {
    if (sections == null) {
      return new OilWaterFlowRegime[0];
    }
    OilWaterFlowRegime[] regimes = new OilWaterFlowRegime[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      regimes[i] = sections[i].getOilWaterFlowRegime();
    }
    return regimes;
  }

  /**
   * Get the water-wetting diagnostic at each section.
   *
   * @return water-wetting flags for corrosion screening
   */
  public boolean[] getWaterWettingProfile() {
    if (sections == null) {
      return new boolean[0];
    }
    boolean[] profile = new boolean[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].isWaterWetting();
    }
    return profile;
  }

  /**
   * Get the water-dropout diagnostic at each section.
   *
   * @return water-dropout risk flags
   */
  public boolean[] getWaterDropoutRiskProfile() {
    if (sections == null) {
      return new boolean[0];
    }
    boolean[] profile = new boolean[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].isWaterDropoutRisk();
    }
    return profile;
  }

  /**
   * Get estimated liquid entrainment fraction at each section.
   *
   * @return entrainment fraction profile, bounded from 0 to 1
   */
  public double[] getEntrainmentFractionProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].getEntrainmentFraction();
    }
    return profile;
  }

  /**
   * Get characteristic entrained droplet diameter at each section.
   *
   * @return entrained droplet diameter profile in metres
   */
  public double[] getEntrainedDropletDiameterProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].getEntrainedDropletDiameter();
    }
    return profile;
  }

  /**
   * Get the local inclined-section gas-carryover number at each section.
   *
   * <p>
   * Values below 1 indicate possible liquid fallback. The number is a local closure screen; it does not diagnose severe
   * slugging in a flowline-riser system.
   * </p>
   *
   * @return local gas-carryover-number profile
   */
  public double[] getInclinedSectionGasCarryoverNumberProfile() {
    if (sections == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].getInclinedSectionGasCarryoverNumber();
    }
    return profile;
  }

  /**
   * Get the local inclined-section liquid-fallback screen at each section.
   *
   * <p>
   * This profile is maintained by local closure calculations. It is separate from the explicit flowline-riser
   * severe-slugging system classification.
   * </p>
   *
   * @return local liquid-fallback flags
   */
  public boolean[] getInclinedSectionLiquidFallbackPotentialProfile() {
    if (sections == null) {
      return new boolean[0];
    }
    boolean[] profile = new boolean[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].isInclinedSectionLiquidFallbackPotential();
    }
    return profile;
  }

  /**
   * Legacy alias for {@link #getInclinedSectionGasCarryoverNumberProfile()}.
   *
   * @return local gas-carryover-number profile
   * @deprecated The returned quantity is not a severe-slugging system stability number.
   */
  @Deprecated
  public double[] getSevereSluggingNumberProfile() {
    return getInclinedSectionGasCarryoverNumberProfile();
  }

  /**
   * Get the most recent explicit severe-slugging system classification as a section profile.
   *
   * <p>
   * The profile is all false until {@link #evaluateSevereSluggingSystem(int)} is called. An applicable unstable result
   * marks only the selected riser-base section. Each subsequent {@link #runTransient(double, UUID)} call invalidates
   * and clears the classification because the section state has changed.
   * </p>
   *
   * @return explicit system-classification flags
   */
  public boolean[] getSevereSlugPotentialProfile() {
    if (sections == null) {
      return new boolean[0];
    }
    boolean[] profile = new boolean[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = sections[i].isSevereSlugPotential();
    }
    return profile;
  }

  /**
   * Evaluate severe-slugging stability for a flowline feeding a constant-area riser.
   *
   * <p>
   * The solved section states provide upstream gas volume, average riser holdup and density, riser height, and absolute
   * outlet pressure. The default gas-cap void fraction is 0.89, following the air-water basis used in Taitel's
   * published comparison.
   * </p>
   *
   * @param riserBaseSection index of the first continuously rising section
   * @return explicit system-level stability result
   */
  public SevereSluggingSystemDiagnostic.Result evaluateSevereSluggingSystem(int riserBaseSection) {
    return evaluateSevereSluggingSystem(riserBaseSection, 0.89, 0.0);
  }

  /**
   * Evaluate severe-slugging stability with explicit gas-cap and static-choke inputs.
   *
   * <p>
   * The static choke pressure drop is added to absolute outlet pressure. It represents one operating point only;
   * dynamic choke response is outside this quasi-steady diagnostic. Three-phase systems and non-stratified feeders
   * return a not-applicable status.
   * </p>
   *
   * <p>
   * Evaluation clears the previous system-classification profile and marks the selected riser-base section only when
   * the result is applicable and unstable.
   * </p>
   *
   * @param riserBaseSection index of the first continuously rising section
   * @param gasCapVoidFraction void fraction alpha-prime in the penetrating gas cap
   * @param staticChokePressureDropPa fixed choke pressure drop in Pa
   * @return explicit system-level stability result
   */
  public SevereSluggingSystemDiagnostic.Result evaluateSevereSluggingSystem(int riserBaseSection,
      double gasCapVoidFraction, double staticChokePressureDropPa) {
    if (sections == null || sections.length != numberOfSections) {
      throw new IllegalStateException("Run the pipe before evaluating flowline-riser stability");
    }
    if (riserBaseSection <= 0 || riserBaseSection >= sections.length) {
      throw new IllegalArgumentException("riserBaseSection must be between 1 and numberOfSections - 1");
    }

    SevereSluggingSystemDiagnostic.Input input = SevereSluggingSystemDiagnostic.fromSections(sections, riserBaseSection,
        gasCapVoidFraction, staticChokePressureDropPa);
    SevereSluggingSystemDiagnostic.Result result = SevereSluggingSystemDiagnostic.evaluate(input);

    clearSevereSluggingSystemClassification();
    if (result.isSevereSluggingPossible()) {
      sections[riserBaseSection].setSevereSlugPotential(true);
    }
    return result;
  }

  /** Clear the section marker produced by the explicit system diagnostic. */
  private void clearSevereSluggingSystemClassification() {
    if (sections == null) {
      return;
    }
    for (TwoFluidSection section : sections) {
      if (section != null) {
        section.setSevereSlugPotential(false);
      }
    }
  }

  /**
   * Get position array for plotting.
   *
   * @return Position along pipe (m), one value per section at section midpoint
   */
  public double[] getPositionProfile() {
    double[] positions = new double[numberOfSections];
    if (sections != null) {
      for (int i = 0; i < numberOfSections; i++) {
        positions[i] = sections[i].getPosition();
      }
    } else {
      for (int i = 0; i < numberOfSections; i++) {
        positions[i] = (i + 0.5) * dx;
      }
    }
    return positions;
  }

  /**
   * Get current simulation time.
   *
   * @return Time (s)
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Get accumulation tracker for detailed analysis.
   *
   * @return Accumulation tracker
   */
  public LiquidAccumulationTracker getAccumulationTracker() {
    return accumulationTracker;
  }

  /**
   * Get slug tracker for slug statistics (simplified model).
   *
   * @return Slug tracker
   */
  public SlugTracker getSlugTracker() {
    return slugTracker;
  }

  /**
   * Get the detailed Lagrangian slug tracker.
   *
   * @return Lagrangian slug tracker
   */
  public LagrangianSlugTracker getLagrangianSlugTracker() {
    return lagrangianSlugTracker;
  }

  /**
   * Get current slug tracking mode.
   *
   * @return slug tracking mode
   */
  public SlugTrackingMode getSlugTrackingMode() {
    return slugTrackingMode;
  }

  /**
   * Set slug tracking mode.
   *
   * <p>
   * Available modes:
   * </p>
   * <ul>
   * <li><b>SIMPLIFIED:</b> Simple slug unit model with basic tracking</li>
   * <li><b>LAGRANGIAN:</b> Detailed tracking with wake effects, frequency-based initiation, and slug statistics</li>
   * <li><b>DISABLED:</b> No slug tracking</li>
   * </ul>
   *
   * @param mode slug tracking mode
   */
  public void setSlugTrackingMode(SlugTrackingMode mode) {
    this.slugTrackingMode = mode;
    this.enableSlugTracking = (mode != SlugTrackingMode.DISABLED);
  }

  /**
   * Configure Lagrangian slug tracker parameters.
   *
   * <p>
   * This method provides access to detailed Lagrangian slug-tracking configuration.
   * </p>
   *
   * @param enableInletGeneration enable hydrodynamic slug generation at inlet
   * @param enableTerrainGeneration enable terrain-induced slug generation
   * @param enableWakeEffects enable wake interaction between slugs
   */
  public void configureLagrangianSlugTracking(boolean enableInletGeneration, boolean enableTerrainGeneration,
      boolean enableWakeEffects) {
    if (lagrangianSlugTracker != null) {
      lagrangianSlugTracker.setEnableInletSlugGeneration(enableInletGeneration);
      lagrangianSlugTracker.setEnableTerrainSlugGeneration(enableTerrainGeneration);
      lagrangianSlugTracker.setEnableWakeEffects(enableWakeEffects);
    }
  }

  /**
   * Get slug tracking statistics as JSON string.
   *
   * @return JSON string with slug statistics
   */
  public String getSlugTrackingStatisticsJson() {
    if (slugTrackingMode == SlugTrackingMode.LAGRANGIAN && lagrangianSlugTracker != null) {
      return lagrangianSlugTracker.toJson();
    } else if (slugTracker != null) {
      return slugTracker.getStatisticsString();
    }
    return "{}";
  }

  /**
   * Track slugs arriving at outlet and collect statistics. Each slug is only counted once when it first reaches the
   * outlet region.
   */
  private void trackOutletSlugs() {
    if (slugTracker == null || sections == null || sections.length == 0) {
      return;
    }

    double pipeLength = length;
    double outletThreshold = pipeLength - sections[sections.length - 1].getLength() * 2;

    for (SlugTracker.SlugUnit slug : slugTracker.getSlugs()) {
      // Skip if already counted this slug
      if (countedOutletSlugs.contains(slug.id)) {
        continue;
      }

      // Check if slug front has reached outlet
      if (slug.frontPosition >= outletThreshold) {
        // This slug is arriving at outlet for the first time - record statistics
        if (slug.age > 0 && slug.slugBodyLength > 0) {
          outletSlugCount++;
          countedOutletSlugs.add(slug.id);
          if (!Double.isNaN(slug.liquidVolume)) {
            totalSlugVolumeAtOutlet += slug.liquidVolume;
            maxSlugVolumeAtOutlet = Math.max(maxSlugVolumeAtOutlet, slug.liquidVolume);
          }
          lastSlugArrivalTime = simulationTime;
          maxSlugLengthAtOutlet = Math.max(maxSlugLengthAtOutlet, slug.slugBodyLength);
        }
      }
    }
  }

  /**
   * Track slugs arriving at outlet using Lagrangian tracker.
   */
  private void trackOutletSlugsLagrangian() {
    if (lagrangianSlugTracker == null || sections == null || sections.length == 0) {
      return;
    }

    // Statistics are tracked internally by LagrangianSlugTracker
    // Update local statistics from the tracker
    outletSlugCount = lagrangianSlugTracker.getTotalSlugsExited();
    maxSlugVolumeAtOutlet = lagrangianSlugTracker.getMaxSlugVolumeAtOutlet();
    maxSlugLengthAtOutlet = lagrangianSlugTracker.getMaxSlugLength();

    // Get total volume from outlet slug volumes
    double totalVol = 0;
    for (Double vol : lagrangianSlugTracker.getOutletSlugVolumes()) {
      totalVol += vol;
    }
    totalSlugVolumeAtOutlet = totalVol;
  }

  /**
   * Get number of slugs that have arrived at outlet.
   *
   * @return Outlet slug count
   */
  public int getOutletSlugCount() {
    return outletSlugCount;
  }

  /**
   * Get total liquid volume delivered by slugs at outlet.
   *
   * @return Total slug volume (m³)
   */
  public double getTotalSlugVolumeAtOutlet() {
    return totalSlugVolumeAtOutlet;
  }

  /**
   * Get time of last slug arrival at outlet.
   *
   * @return Time (s)
   */
  public double getLastSlugArrivalTime() {
    return lastSlugArrivalTime;
  }

  /**
   * Get maximum slug length observed at outlet.
   *
   * @return Max length (m)
   */
  public double getMaxSlugLengthAtOutlet() {
    return maxSlugLengthAtOutlet;
  }

  /**
   * Get maximum slug volume observed at outlet.
   *
   * @return Max volume (m³)
   */
  public double getMaxSlugVolumeAtOutlet() {
    return maxSlugVolumeAtOutlet;
  }

  /**
   * Get slug statistics summary string.
   *
   * @return Statistics summary
   */
  public String getSlugStatisticsSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Slug Statistics ===\n");
    sb.append(String.format("Tracking mode: %s\n", slugTrackingMode));

    if (slugTrackingMode == SlugTrackingMode.LAGRANGIAN && lagrangianSlugTracker != null) {
      // Use Lagrangian tracker statistics
      sb.append(String.format("Active slugs in pipe: %d\n", lagrangianSlugTracker.getSlugCount()));
      sb.append(String.format("Slugs generated: %d\n", lagrangianSlugTracker.getTotalSlugsGenerated()));
      sb.append(String.format("Slugs merged: %d\n", lagrangianSlugTracker.getTotalSlugsMerged()));
      sb.append(String.format("Slugs dissipated: %d\n", lagrangianSlugTracker.getTotalSlugsDissipated()));
      sb.append(String.format("Slugs at outlet: %d\n", lagrangianSlugTracker.getTotalSlugsExited()));
      sb.append(String.format("Inlet slug frequency: %.4f Hz\n", lagrangianSlugTracker.getInletSlugFrequency()));
      sb.append(String.format("Outlet slug frequency: %.4f Hz\n", lagrangianSlugTracker.getOutletSlugFrequency()));
      sb.append(String.format("Average slug length: %.2f m\n", lagrangianSlugTracker.getAverageSlugLength()));
      sb.append(String.format("Max slug length: %.2f m\n", lagrangianSlugTracker.getMaxSlugLength()));
      sb.append(
          String.format("Max slug volume at outlet: %.4f m³\n", lagrangianSlugTracker.getMaxSlugVolumeAtOutlet()));
      sb.append(String.format("Mass conservation error: %.6f kg\n", lagrangianSlugTracker.getMassConservationError()));
    } else if (slugTracker != null) {
      // Use simplified tracker statistics
      sb.append(String.format("Active slugs in pipe: %d\n", slugTracker.getSlugCount()));
      sb.append(String.format("Slugs generated: %d\n", slugTracker.getTotalSlugsGenerated()));
      sb.append(String.format("Slugs merged: %d\n", slugTracker.getTotalSlugsMerged()));
      sb.append(String.format("Slugs at outlet: %d\n", outletSlugCount));
      sb.append(String.format("Total slug volume at outlet: %.2f m³\n", totalSlugVolumeAtOutlet));
      sb.append(String.format("Max slug length at outlet: %.1f m\n", maxSlugLengthAtOutlet));
      sb.append(String.format("Max slug volume at outlet: %.3f m³\n", maxSlugVolumeAtOutlet));
      if (outletSlugCount > 0 && simulationTime > 0) {
        double avgFrequency = outletSlugCount / simulationTime;
        sb.append(String.format("Average slug frequency: %.4f Hz (%.1f min between slugs)\n", avgFrequency,
            avgFrequency > 0 ? 1.0 / (avgFrequency * 60) : 0));
      }
    }

    return sb.toString();
  }

  // ============ Configuration methods ============

  /**
   * Set pipe length.
   *
   * @param length Length (m)
   */
  @Override
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get pipe length.
   *
   * @return Length (m)
   */
  @Override
  public double getLength() {
    return length;
  }

  /**
   * Set pipe diameter.
   *
   * @param diameter Diameter (m)
   */
  @Override
  public void setDiameter(double diameter) {
    this.diameter = diameter;
  }

  /**
   * Get pipe diameter.
   *
   * @return Diameter (m)
   */
  @Override
  public double getDiameter() {
    return diameter;
  }

  /**
   * Set pipe wall roughness.
   *
   * @param roughness Roughness (m)
   */
  public void setRoughness(double roughness) {
    this.roughness = roughness;
  }

  /**
   * Get pipe wall roughness.
   *
   * @return Roughness (m)
   */
  public double getRoughness() {
    return roughness;
  }

  /**
   * Set number of computational sections.
   *
   * @param numberOfSections Number of sections
   */
  public void setNumberOfSections(int numberOfSections) {
    this.numberOfSections = numberOfSections;
  }

  /**
   * Set per-section lengths for non-uniform mesh.
   *
   * <p>
   * Enables variable spatial resolution along the pipe. Use shorter sections at elevation changes, risers, and dips
   * where flow regime transitions occur, and longer sections in uniform horizontal/vertical runs. This follows the same
   * standard finite-volume practice for concentrating resolution where gradients are largest.
   * </p>
   *
   * <p>
   * The array length determines the number of sections. The sum of all lengths must equal the total pipe length (set
   * via {@link #setLength(double)}). The CFL time step will be governed by the smallest section length.
   * </p>
   *
   * @param lengths Array of section lengths (m), one per computational cell
   * @throws IllegalArgumentException if lengths is null or empty
   */
  public void setSectionLengths(double[] lengths) {
    if (lengths == null || lengths.length < 2) {
      throw new IllegalArgumentException("Section lengths array must have at least 2 elements");
    }
    this.sectionLengths = lengths.clone();
    this.numberOfSections = lengths.length;
  }

  /**
   * Get per-section lengths. Returns null if uniform mesh is used.
   *
   * @return Array of section lengths (m) or null for uniform mesh
   */
  public double[] getSectionLengths() {
    return sectionLengths != null ? sectionLengths.clone() : null;
  }

  /**
   * Generate a non-uniform mesh with refinement at elevation changes.
   *
   * <p>
   * Automatically creates finer sections where the elevation profile has large gradients (risers, dips, terrain
   * changes) and coarser sections in uniform regions. The refinement factor controls how much finer the mesh is at
   * elevation changes relative to flat sections.
   * </p>
   *
   * <p>
   * Use short sections at elevation breaks and longer sections on uniform runs. Demonstrate mesh convergence for the
   * quantities being reported; severe-slug cycle period can be especially sensitive to riser-base cell placement.
   * </p>
   *
   * @param baseSections Base number of sections for uniform regions
   * @param refinementFactor How much finer to make sections at elevation changes (2-10)
   */
  public void generateRefinedMesh(int baseSections, double refinementFactor) {
    if (elevationProfile == null || elevationProfile.length < 2) {
      // No elevation profile, use uniform mesh
      this.numberOfSections = baseSections;
      this.sectionLengths = null;
      return;
    }

    refinementFactor = Math.max(1.5, Math.min(refinementFactor, 10.0));

    // Calculate elevation gradients at each profile point
    int nProfile = elevationProfile.length;
    double dxProfile = length / (nProfile - 1);
    double[] gradients = new double[nProfile];
    double maxGrad = 0;
    for (int i = 0; i < nProfile; i++) {
      if (i == 0) {
        gradients[i] = Math.abs(elevationProfile[1] - elevationProfile[0]) / dxProfile;
      } else if (i == nProfile - 1) {
        gradients[i] = Math.abs(elevationProfile[i] - elevationProfile[i - 1]) / dxProfile;
      } else {
        gradients[i] = Math.abs(elevationProfile[i + 1] - elevationProfile[i - 1]) / (2.0 * dxProfile);
      }
      maxGrad = Math.max(maxGrad, gradients[i]);
    }

    if (maxGrad < 0.01) {
      // Essentially flat, use uniform mesh
      this.numberOfSections = baseSections;
      this.sectionLengths = null;
      return;
    }

    // Compute desired section density (inverse of desired dx) along the pipe
    // Higher gradient → shorter sections
    double[] density = new double[baseSections];
    double totalDensity = 0;
    double baseDx = length / baseSections;
    for (int i = 0; i < baseSections; i++) {
      double pos = (i + 0.5) * baseDx;
      // Interpolate gradient at this position
      double fracIdx = pos / dxProfile;
      int idx = Math.min((int) fracIdx, nProfile - 2);
      double frac = fracIdx - idx;
      double localGrad = gradients[idx] * (1 - frac) + gradients[idx + 1] * frac;
      // Refinement: scale by 1 to refinementFactor based on gradient
      density[i] = 1.0 + (refinementFactor - 1.0) * (localGrad / maxGrad);
      totalDensity += density[i];
    }

    // Convert density to section lengths: higher density → shorter section
    // Section length is inversely proportional to density
    double totalInvDensity = 0;
    for (int i = 0; i < baseSections; i++) {
      totalInvDensity += 1.0 / density[i];
    }
    double[] lengths = new double[baseSections];
    for (int i = 0; i < baseSections; i++) {
      lengths[i] = length * (1.0 / density[i]) / totalInvDensity;
      // Clamp to reasonable range: [dx/refinementFactor, dx*refinementFactor]
      lengths[i] = Math.max(lengths[i], baseDx / refinementFactor);
      lengths[i] = Math.min(lengths[i], baseDx * refinementFactor);
    }

    // Normalise so they sum exactly to total length
    double sum = 0;
    for (double l : lengths) {
      sum += l;
    }
    for (int i = 0; i < lengths.length; i++) {
      lengths[i] *= length / sum;
    }

    setSectionLengths(lengths);
  }

  /**
   * Get number of sections.
   *
   * @return Number of sections
   */
  public int getNumberOfSections() {
    return numberOfSections;
  }

  /**
   * Set elevation profile.
   *
   * @param elevations Elevation at each section (m)
   */
  public void setElevationProfile(double[] elevations) {
    this.elevationProfile = elevations.clone();
  }

  /**
   * Set outlet pressure.
   *
   * @param pressure Pressure (Pa)
   */
  @Override
  public void setOutletPressure(double pressure) {
    this.outletPressure = pressure;
    this.outletPressureSet = true;
  }

  /**
   * Set inlet boundary condition type.
   *
   * <p>
   * Options:
   * <ul>
   * <li>STREAM_CONNECTED (default): Flow rate, temperature, composition from inlet stream</li>
   * <li>CONSTANT_FLOW: Use explicit mass flow set via setInletMassFlow()</li>
   * <li>CONSTANT_PRESSURE: Use explicit pressure set via setInletPressure()</li>
   * </ul>
   *
   * @param bcType the inlet boundary condition type
   */
  public void setInletBoundaryCondition(BoundaryCondition bcType) {
    this.inletBCType = bcType;
  }

  /**
   * Set outlet boundary condition type.
   *
   * <p>
   * Options:
   * <ul>
   * <li>CONSTANT_PRESSURE (default): Fixed pressure at outlet</li>
   * <li>CONSTANT_FLOW: Fixed mass flow at outlet (requires inlet pressure BC)</li>
   * <li>STREAM_CONNECTED: Pressure from downstream equipment</li>
   * <li>CLOSED: Zero flow velocity (blocked/shut-in) - pressure floats</li>
   * </ul>
   *
   * @param bcType the outlet boundary condition type
   */
  public void setOutletBoundaryCondition(BoundaryCondition bcType) {
    this.outletBCType = bcType;
  }

  /**
   * Get inlet boundary condition type.
   *
   * @return the inlet boundary condition type
   */
  public BoundaryCondition getInletBoundaryCondition() {
    return inletBCType;
  }

  /**
   * Get outlet boundary condition type.
   *
   * @return the outlet boundary condition type
   */
  public BoundaryCondition getOutletBoundaryCondition() {
    return outletBCType;
  }

  /**
   * Set inlet mass flow for CONSTANT_FLOW boundary condition.
   *
   * @param massFlow Mass flow rate (kg/s)
   */
  public void setInletMassFlow(double massFlow) {
    this.inletMassFlow = massFlow;
    this.inletMassFlowSet = true;
  }

  /**
   * Set inlet mass flow with unit for CONSTANT_FLOW boundary condition.
   *
   * @param massFlow Mass flow rate value
   * @param unit Unit ("kg/s", "kg/hr", "kg/sec", "ton/hr")
   */
  public void setInletMassFlow(double massFlow, String unit) {
    double mDot;
    switch (unit.toLowerCase()) {
    case "kg/hr":
      mDot = massFlow / 3600.0;
      break;
    case "ton/hr":
      mDot = massFlow * 1000.0 / 3600.0;
      break;
    default:
      mDot = massFlow;
    }
    setInletMassFlow(mDot);
  }

  /**
   * Set inlet pressure for CONSTANT_PRESSURE boundary condition.
   *
   * @param pressure Pressure (Pa)
   */
  @Override
  public void setInletPressure(double pressure) {
    this.inletPressure = pressure;
    this.inletPressureSet = true;
  }

  /**
   * Set inlet pressure with unit.
   *
   * @param pressure Pressure value
   * @param unit Pressure unit ("Pa", "bara", "barg", "psia")
   */
  public void setInletPressure(double pressure, String unit) {
    double P_pa;
    switch (unit.toLowerCase()) {
    case "bara":
    case "bar":
      P_pa = pressure * 1e5;
      break;
    case "barg":
      P_pa = (pressure + 1.01325) * 1e5;
      break;
    case "psia":
      P_pa = pressure * 6894.76;
      break;
    default:
      P_pa = pressure;
    }
    setInletPressure(P_pa);
  }

  /**
   * Set outlet pressure with unit.
   *
   * @param pressure Pressure value
   * @param unit Pressure unit ("Pa", "bara", "barg", "psia")
   */
  @Override
  public void setOutletPressure(double pressure, String unit) {
    double P_pa;
    switch (unit.toLowerCase()) {
    case "bara":
    case "bar":
      P_pa = pressure * 1e5;
      break;
    case "barg":
      P_pa = (pressure + 1.01325) * 1e5;
      break;
    case "psia":
      P_pa = pressure * 6894.76;
      break;
    default:
      P_pa = pressure;
    }
    setOutletPressure(P_pa);
  }

  /**
   * Close the pipe outlet (blocked/shut-in condition).
   *
   * <p>
   * Sets outlet boundary condition to CLOSED, meaning zero flow velocity at outlet. Pressure will build up during
   * transient simulation as mass accumulates in the pipe. Useful for:
   * <ul>
   * <li>Shut-in scenario analysis</li>
   * <li>Pressure surge/water hammer studies</li>
   * <li>Line packing simulations</li>
   * <li>Emergency shutdown modeling</li>
   * </ul>
   */
  public void closeOutlet() {
    this.outletBCType = BoundaryCondition.CLOSED;
  }

  /**
   * Open the pipe outlet with constant pressure boundary condition.
   *
   * <p>
   * Restores outlet to CONSTANT_PRESSURE boundary condition. The outlet pressure is set to the value previously set via
   * setOutletPressure(), or defaults to 1 bara if not set.
   */
  public void openOutlet() {
    this.outletBCType = BoundaryCondition.CONSTANT_PRESSURE;
    if (!outletPressureSet) {
      this.outletPressure = 1e5; // Default to 1 bara
    }
  }

  /**
   * Open the pipe outlet with specified pressure.
   *
   * @param pressure Outlet pressure value
   * @param unit Pressure unit ("Pa", "bara", "barg", "psia")
   */
  public void openOutlet(double pressure, String unit) {
    setOutletPressure(pressure, unit);
    this.outletBCType = BoundaryCondition.CONSTANT_PRESSURE;
  }

  /**
   * Close the pipe inlet (blocked/no inflow condition).
   *
   * <p>
   * Sets inlet boundary condition to CLOSED, meaning zero flow velocity at inlet. Pressure will decrease during
   * transient simulation as mass leaves the pipe. Useful for:
   * <ul>
   * <li>Blowdown simulation</li>
   * <li>Depressurization studies</li>
   * <li>Emergency shutdown with upstream closure</li>
   * </ul>
   */
  public void closeInlet() {
    this.inletBCType = BoundaryCondition.CLOSED;
  }

  /**
   * Open the pipe inlet with stream-connected boundary condition.
   *
   * <p>
   * Restores inlet to STREAM_CONNECTED boundary condition, using flow rate, temperature, and composition from the
   * connected inlet stream.
   */
  public void openInlet() {
    this.inletBCType = BoundaryCondition.STREAM_CONNECTED;
  }

  /**
   * Check if outlet is closed.
   *
   * @return true if outlet boundary condition is CLOSED
   */
  public boolean isOutletClosed() {
    return outletBCType == BoundaryCondition.CLOSED;
  }

  /**
   * Check if inlet is closed.
   *
   * @return true if inlet boundary condition is CLOSED
   */
  public boolean isInletClosed() {
    return inletBCType == BoundaryCondition.CLOSED;
  }

  /**
   * Set CFL number for time stepping.
   *
   * @param cfl CFL number (0 &lt; cfl &lt; 1)
   */
  public void setCflNumber(double cfl) {
    this.cflNumber = Math.max(0.1, Math.min(0.9, cfl));
    if (timeIntegrator != null) {
      timeIntegrator.setCflNumber(cflNumber);
    }
  }

  /**
   * Set the time integration method.
   *
   * <p>
   * Use {@link TimeIntegrator.Method#IMEX_PRESSURE_CORRECTION} for long pipelines where the acoustic CFL constraint is
   * prohibitively small. The IMEX method solves the pressure wave equation implicitly, allowing time steps 10-100x
   * larger than explicit schemes.
   * </p>
   *
   * @param method integration method (RK4, EULER, IMEX_PRESSURE_CORRECTION, etc.)
   */
  public void setTimeIntegrationMethod(TimeIntegrator.Method method) {
    if (timeIntegrator != null) {
      timeIntegrator.setMethod(method);
    }
  }

  /**
   * Get the current time integration method.
   *
   * @return current integration method
   */
  public TimeIntegrator.Method getTimeIntegrationMethod() {
    if (timeIntegrator != null) {
      return timeIntegrator.getMethod();
    }
    return TimeIntegrator.Method.RK4;
  }

  /**
   * Enable or disable conservative local implicit treatment of dispersed-bubble drag.
   *
   * <p>
   * The treatment is opt-in because the corrected closure is not yet quantitatively validated by the public Tengesdal
   * severe-slugging benchmark. Enabling it selects the dimensionally correct Schiller-Naumann force and the local
   * implicit source solve together.
   * </p>
   *
   * @param enable true to use the local stiff source solve
   */
  public void setEnableStiffBubbleDrag(boolean enable) {
    equations.setEnableStiffBubbleDrag(enable);
  }

  /**
   * Check whether dispersed-bubble drag uses the conservative local implicit source solve.
   *
   * @return true when the stiff source treatment is enabled
   */
  public boolean isStiffBubbleDragEnabled() {
    return equations.isStiffBubbleDragEnabled();
  }

  /**
   * Get the bubble-size closure used by bubble and dispersed-bubble regimes.
   *
   * @return mutable bubble-size closure configuration
   */
  public BubbleSizeClosure getBubbleSizeClosure() {
    return equations.getBubbleSizeClosure();
  }

  /**
   * Set the fixed bubble-size surface tension.
   *
   * <p>
   * This value is used by default and preserves legacy behavior at {@code 0.02 N/m}. Enable local surface tension
   * explicitly to use each section's thermodynamic phase-property value instead.
   * </p>
   *
   * @param surfaceTension fixed surface tension in N/m
   */
  public void setBubbleSurfaceTension(double surfaceTension) {
    getBubbleSizeClosure().setSurfaceTension(surfaceTension);
  }

  /** @return configured fixed bubble-size surface tension in N/m */
  public double getBubbleSurfaceTension() {
    return getBubbleSizeClosure().getSurfaceTension();
  }

  /**
   * Select local thermodynamic phase-property surface tension for the bubble-size closure.
   *
   * @param useLocal true to use the surface tension stored for each pipe section
   */
  public void setUseLocalBubbleSurfaceTension(boolean useLocal) {
    getBubbleSizeClosure().setUseLocalSurfaceTension(useLocal);
  }

  /** @return true when section-local surface tension is selected */
  public boolean isUseLocalBubbleSurfaceTension() {
    return getBubbleSizeClosure().isUseLocalSurfaceTension();
  }

  /**
   * Set the maximum bubble diameter as a fraction of pipe diameter.
   *
   * @param fraction fraction in the interval (0, 1]
   */
  public void setMaximumBubbleDiameterFraction(double fraction) {
    getBubbleSizeClosure().setMaximumPipeDiameterFraction(fraction);
  }

  /** @return maximum bubble diameter divided by pipe diameter */
  public double getMaximumBubbleDiameterFraction() {
    return getBubbleSizeClosure().getMaximumPipeDiameterFraction();
  }

  /**
   * Set maximum simulation time for transient calculations.
   *
   * @param time Maximum simulation time in seconds
   */
  public void setMaxSimulationTime(double time) {
    this.maxSimulationTime = Math.max(1.0, time);
  }

  /**
   * Get maximum simulation time.
   *
   * @return Maximum simulation time in seconds
   */
  public double getMaxSimulationTime() {
    return maxSimulationTime;
  }

  /**
   * Enable/disable energy equation.
   *
   * @param include true to include energy equation
   */
  public void setIncludeEnergyEquation(boolean include) {
    this.includeEnergyEquation = include;
    if (equations != null) {
      equations.setIncludeEnergyEquation(include);
    }
  }

  /**
   * Set surface temperature for heat transfer calculations.
   *
   * <p>
   * Defines the thermal boundary temperature and enables the energy equation. A positive heat-transfer coefficient,
   * coefficient profile, or configured multi-layer calculator is also required before a transient heat flux is applied.
   * </p>
   *
   * @param temperature Surface temperature in the specified unit
   * @param unit Temperature unit ("K" or "C")
   */
  public void setSurfaceTemperature(double temperature, String unit) {
    if ("K".equals(unit)) {
      this.surfaceTemperature = temperature;
    } else if ("C".equals(unit)) {
      this.surfaceTemperature = temperature + 273.15;
    } else {
      throw new IllegalArgumentException("Unsupported temperature unit: " + unit + ". Use 'K' or 'C'.");
    }
    this.enableHeatTransfer = true;
    this.includeEnergyEquation = true;
    if (equations != null) {
      equations.setIncludeEnergyEquation(true);
      equations.setSurfaceTemperature(this.surfaceTemperature);
      // The post-step temperature model owns ambient heat exchange.
      equations.setEnableHeatTransfer(false);
    }
  }

  /**
   * Set the overall heat transfer coefficient used by the simple thermal model.
   *
   * <p>
   * Heat transfer rate: Q = U * A * (T_pipe - T_surface)<br>
   * where U = overall heat transfer coefficient (W/(m²·K))<br>
   * A = pipe surface area (m²)<br>
   * T_pipe = bulk fluid temperature (K)<br>
   * T_surface = surrounding surface temperature (K)<br>
   * </p>
   *
   * <p>
   * Typical values:
   * <ul>
   * <li>Insulated subsea pipe: 5-15 W/(m²·K)</li>
   * <li>Uninsulated subsea pipe: 20-30 W/(m²·K)</li>
   * <li>Exposed/above-ground pipe: 50-100 W/(m²·K)</li>
   * </ul>
   *
   * <p>
   * For the multi-layer model, this value enables heat transfer and reports the configuration-level overall U-value; it
   * is not used as the fluid-side film coefficient. Configure the zero-throughput fluid film with
   * {@link #setStagnantInnerHeatTransferCoefficient(double)}.
   * </p>
   *
   * @param heatTransferCoefficient overall heat transfer coefficient in W/(m²·K)
   */
  @Override
  public void setHeatTransferCoefficient(double heatTransferCoefficient) {
    if (heatTransferCoefficient < 0) {
      throw new IllegalArgumentException("Heat transfer coefficient must be non-negative: " + heatTransferCoefficient);
    }
    this.heatTransferCoefficient = heatTransferCoefficient;
    this.enableHeatTransfer = heatTransferCoefficient > 0;
    if (heatTransferCoefficient > 0) {
      this.includeEnergyEquation = true;
      if (equations != null) {
        equations.setIncludeEnergyEquation(true);
        equations.setHeatTransferCoefficient(heatTransferCoefficient);
        // The post-step temperature model owns ambient heat exchange.
        equations.setEnableHeatTransfer(false);
      }
    }
  }

  /**
   * Get the surface temperature used for heat transfer calculations.
   *
   * @return Surface temperature in Kelvin
   */
  public double getSurfaceTemperature() {
    return surfaceTemperature;
  }

  /**
   * Set the direct electrical heating (DEH) power delivered to the fluid, distributed uniformly over the pipe length.
   *
   * <p>
   * DEH passes current through the pipe wall to keep the fluid above the hydrate or wax formation temperature. The
   * power set here is the electrical power actually reaching the fluid, so cable and coating losses must already be
   * deducted. It is added directly to the fluid energy equation, independently of the wall heat loss it counteracts,
   * and therefore bypasses the wall thermal mass in transient runs. The same convention is used by
   * {@link PipeBeggsAndBrills#setDirectElectricalHeatingPower(double)}, so the two models can be compared
   * like-for-like. DEH is active in both steady-state and transient runs, and also when wall heat transfer is off.
   * </p>
   *
   * @param power total DEH power delivered to the fluid in W, non-negative
   * @throws IllegalArgumentException if power is negative or the pipe length is not positive
   */
  public void setDirectElectricalHeatingPower(double power) {
    if (power < 0) {
      throw new IllegalArgumentException("DEH power must be non-negative, got: " + power);
    }
    if (!Double.isFinite(length) || length <= 0) {
      throw new IllegalArgumentException("Pipe length must be set before the total DEH power");
    }
    this.directElectricalHeatingPowerPerMeter = power / length;
    if (this.directElectricalHeatingPowerPerMeter > 0) {
      this.includeEnergyEquation = true;
      if (equations != null) {
        equations.setIncludeEnergyEquation(true);
      }
    }
  }

  /**
   * Set the direct electrical heating (DEH) power per metre of pipe.
   *
   * @param powerPerMeter DEH power delivered to the fluid in W/m, non-negative
   * @throws IllegalArgumentException if powerPerMeter is negative
   * @see #setDirectElectricalHeatingPower(double)
   */
  public void setDirectElectricalHeatingPowerPerMeter(double powerPerMeter) {
    if (powerPerMeter < 0) {
      throw new IllegalArgumentException("DEH power per metre must be non-negative, got: " + powerPerMeter);
    }
    this.directElectricalHeatingPowerPerMeter = powerPerMeter;
    if (powerPerMeter > 0) {
      this.includeEnergyEquation = true;
      if (equations != null) {
        equations.setIncludeEnergyEquation(true);
      }
    }
  }

  /**
   * Get the direct electrical heating (DEH) power per metre of pipe.
   *
   * @return DEH power delivered to the fluid in W/m, zero when DEH is not used
   */
  public double getDirectElectricalHeatingPowerPerMeter() {
    return directElectricalHeatingPowerPerMeter;
  }

  /**
   * Get the total direct electrical heating (DEH) power over the pipe length.
   *
   * @return total DEH power delivered to the fluid in W, zero when DEH is not used or the length is unset
   */
  public double getDirectElectricalHeatingPower() {
    if (!Double.isFinite(length)) {
      return 0.0;
    }
    return directElectricalHeatingPowerPerMeter * length;
  }

  /**
   * Get the overall or simple-model heat transfer coefficient.
   *
   * @return overall heat transfer coefficient in W/(m²·K)
   */
  @Override
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * Set the fluid-side heat transfer coefficient used at zero local face throughput.
   *
   * <p>
   * This coefficient is used only by the multi-layer transient model when the local cell has no gas, oil, or water
   * throughput. It represents stagnant fluid-to-inner-wall heat transfer and is independent of the overall
   * pipe-to-ambient coefficient configured by {@link #setHeatTransferCoefficient(double)}. The default is 50 W/(m2 K),
   * a pragmatic gas-rich shutdown assumption that should be replaced with a case-specific value when available.
   * </p>
   *
   * @param coefficient stagnant fluid-side heat transfer coefficient in W/(m2 K)
   * @throws IllegalArgumentException if the coefficient is negative or non-finite
   */
  public void setStagnantInnerHeatTransferCoefficient(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0.0) {
      throw new IllegalArgumentException(
          "Stagnant inner heat transfer coefficient must be finite and non-negative: " + coefficient);
    }
    stagnantInnerHeatTransferCoefficient = coefficient;
  }

  /**
   * Get the fluid-side heat transfer coefficient used at zero local face throughput.
   *
   * @return stagnant fluid-side heat transfer coefficient in W/(m2 K)
   */
  public double getStagnantInnerHeatTransferCoefficient() {
    return stagnantInnerHeatTransferCoefficient;
  }

  /**
   * Check if heat transfer is enabled.
   *
   * @return true if heat transfer modeling is active
   */
  public boolean isHeatTransferEnabled() {
    return enableHeatTransfer && heatTransferCoefficient > 0;
  }

  /**
   * Enable/disable mass transfer (flashing/condensation).
   *
   * <p>
   * When enabled, PT-flash equilibrium generates conservative gas, hydrocarbon-liquid, and aqueous-liquid sources.
   * Condensation follows the equilibrium liquid mass split, while evaporation is limited by the actual oil and water
   * inventories. Transferred momentum uses donor velocity. The hydrodynamic state tracks bulk phase inventories. When
   * {@link #setComponentTransportEnabled(boolean)} is enabled before {@link #run()}, the same accepted phase sources
   * are also mapped by component identity and their composition-dependent latent heat enters the thermal ledger.
   * </p>
   *
   * @param include true to include mass transfer
   */
  public void setIncludeMassTransfer(boolean include) {
    this.includeMassTransfer = include;
    if (equations != null) {
      equations.setIncludeMassTransfer(include);
    }
  }

  /**
   * Set relaxation time for flash-driven evaporation/condensation source terms.
   *
   * @param relaxationTime relaxation time in seconds
   */
  public void setMassTransferRelaxationTime(double relaxationTime) {
    if (equations != null) {
      equations.setMassTransferRelaxationTime(relaxationTime);
    }
  }

  /**
   * Enable/disable slug tracking.
   *
   * @param enable true to enable slug tracking
   */
  public void setEnableSlugTracking(boolean enable) {
    this.enableSlugTracking = enable;
  }

  /**
   * Enable adaptive timestepping.
   *
   * <p>
   * When enabled, the solver automatically adjusts the internal sub-step size to maintain stability. Per sub-step, it:
   * (1) recomputes the CFL-stable dt from current velocities, (2) monitors solution quality (pressure change, holdup
   * divergence, velocity blow-up), (3) rejects bad steps and halves dt with state rollback, (4) gradually grows dt back
   * when stable.
   * </p>
   *
   * <p>
   * With IMEX integration, the CFL estimate uses material velocities rather than sound speed. Step rejection improves
   * robustness but does not by itself establish accuracy or stability for a particular transient; benchmark timestep
   * sensitivity for the scenario being reported.
   * </p>
   *
   * @param enable true to enable adaptive timestepping
   */
  public void setEnableAdaptiveTimestepping(boolean enable) {
    this.enableAdaptiveTimestepping = enable;
    if (enable) {
      this.adaptiveDtFactor = 1.0;
    }
  }

  /**
   * Check if adaptive timestepping is enabled.
   *
   * @return true if adaptive timestepping is enabled
   */
  public boolean isAdaptiveTimesteppingEnabled() {
    return enableAdaptiveTimestepping;
  }

  /**
   * Get the current adaptive dt factor (1.0 = full CFL step, lower = reduced for stability).
   *
   * <p>
   * Useful for monitoring: if this stays well below 1.0, the simulation is fighting instability.
   * </p>
   *
   * @return current adaptive dt factor in range [0.001, 1.0]
   */
  public double getAdaptiveDtFactor() {
    return adaptiveDtFactor;
  }

  /**
   * Set the maximum pressure before adaptive dt reduction triggers.
   *
   * @param maxPressureBar maximum pressure in bara (default 1000)
   */
  public void setAdaptiveMaxPressure(double maxPressureBar) {
    this.adaptiveMaxPressure = Math.max(10.0, maxPressureBar) * 1e5;
  }

  /**
   * Enable water-oil velocity slip modeling.
   *
   * <p>
   * When enabled, uses 7-equation model with separate oil and water momentum equations, allowing water and oil phases
   * to flow at different velocities. This is important for:
   * </p>
   * <ul>
   * <li>Uphill flow: water slips back relative to oil due to higher density</li>
   * <li>Downhill flow: water accelerates relative to oil</li>
   * <li>Stratified oil-water flow with shear at interface</li>
   * </ul>
   *
   * @param enable true to enable 7-equation model with oil-water slip
   */
  public void setEnableWaterOilSlip(boolean enable) {
    if (equations != null) {
      equations.setEnableWaterOilSlip(enable);
    }
  }

  /**
   * Check if water-oil velocity slip modeling is enabled.
   *
   * @return true if 7-equation model is enabled
   */
  public boolean isWaterOilSlipEnabled() {
    if (equations != null) {
      return equations.isEnableWaterOilSlip();
    }
    return false;
  }

  /**
   * Enable or disable the holdup-gradient momentum term and its interfacial pressure correction.
   *
   * <p>
   * The term cancels the spurious force left by carrying {@code alpha * p} in the momentum flux and keeps the two-fluid
   * system hyperbolic. Disabling it reproduces the historical, ill-posed behaviour and is intended only for regression
   * comparisons.
   * </p>
   *
   * @param enable true to apply the term
   */
  public void setEnableInterfacialPressure(boolean enable) {
    if (equations != null) {
      equations.setEnableInterfacialPressure(enable);
    }
  }

  /**
   * Enable the coupled compressible pressure-momentum transient correction.
   *
   * <p>The option solves the cell-volume pressure equation and corrects phase mass fluxes and
   * phase momenta with the same face pressure gradients. It replaces the post-step steady
   * friction/gravity pressure reconstruction. The option remains off by default while the
   * long-horizon liquid-rich and severe-slugging validation suite is being qualified.
   *
   * <p>Use together with {@link #setEnableInterfacialPressure(boolean)} so the transient momentum
   * equations use the physically correct pressure force and the Bestion hyperbolicity closure.
   *
   * @param enabled true to use the coupled correction
   */
  public void setEnableCoupledPressureMomentum(boolean enabled) {
    coupledPressureMomentumEnabled = enabled;
    if (timeIntegrator != null) {
      timeIntegrator.setCoupledPressureMomentumEnabled(enabled);
    }
  }

  /** @return true when the coupled pressure-momentum correction is selected */
  public boolean isCoupledPressureMomentumEnabled() {
    return coupledPressureMomentumEnabled;
  }

  /** @return convergence status of the most recent coupled correction */
  public boolean isCoupledPressureMomentumConverged() {
    return !coupledPressureMomentumEnabled
        || timeIntegrator.isCoupledPressureMomentumConverged();
  }

  /** @return maximum relative cell-volume residual of the most recent correction */
  public double getCoupledPressureMomentumVolumeResidual() {
    return timeIntegrator.getCoupledPressureMomentumVolumeResidual();
  }

  /** @return nonlinear iterations used by the most recent correction */
  public int getCoupledPressureMomentumIterations() {
    return timeIntegrator.getCoupledPressureMomentumIterations();
  }

  /**
   * Select how the interfacial-pressure stabilizer is advanced in time.
   *
   * <p>
   * The stabilizer carries the void wave, so treating it explicitly inside the spatial right-hand side requires a CFL
   * number near 0.05 and makes the term impractical. The implicit treatment solves the linearized drift-flux subsystem
   * after the transport step, which removes that restriction while leaving every phase mass and the cell total momentum
   * unchanged. Disable only to reproduce the explicit behaviour for verification.
   * </p>
   *
   * @param implicitCoupling true to advance the stabilizer implicitly
   */
  public void setImplicitInterfacialPressureCoupling(boolean implicitCoupling) {
    this.implicitInterfacialPressureCoupling = implicitCoupling;
  }

  /**
   * Whether the interfacial-pressure stabilizer is advanced implicitly.
   *
   * @return true when the implicit drift-flux treatment is selected
   */
  public boolean isImplicitInterfacialPressureCoupling() {
    return implicitInterfacialPressureCoupling;
  }

  /** @return true when the interfacial pressure momentum term is applied */
  public boolean isInterfacialPressureEnabled() {
    return equations != null && equations.isEnableInterfacialPressure();
  }

  /**
   * Set the interfacial pressure coefficient delta used by the Bestion closure.
   *
   * @param coefficient non-negative coefficient; values below one leave the system ill-posed
   */
  public void setInterfacialPressureCoefficient(double coefficient) {
    if (equations != null) {
      equations.setInterfacialPressureCoefficient(coefficient);
    }
  }

  /** @return interfacial pressure coefficient delta */
  public double getInterfacialPressureCoefficient() {
    return equations != null ? equations.getInterfacialPressureCoefficient() : 0.0;
  }

  /**
   * Set thermodynamic update interval.
   *
   * @param interval Update every N time steps
   */
  public void setThermodynamicUpdateInterval(int interval) {
    this.thermodynamicUpdateInterval = Math.max(1, interval);
  }

  /**
   * Set the under-relaxation factor for steady-state pressure and holdup updates.
   *
   * @param factor Relaxation factor in range (0, 1]. Lower is more stable but slower.
   */
  public void setSteadyStateUnderRelaxation(double factor) {
    this.ssUnderRelaxation = Math.max(0.05, Math.min(1.0, factor));
  }

  /**
   * Set the flash calculation interval during steady-state iterations.
   *
   * <p>
   * A TP-flash is performed for every section only every N iterations. Default is 5.
   * </p>
   *
   * @param interval Flash every N iterations (1 = every iteration)
   */
  public void setSteadyStateFlashInterval(int interval) {
    this.ssFlashInterval = Math.max(1, interval);
  }

  /**
   * Set the maximum wall-clock time for the steady-state solver.
   *
   * @param seconds Maximum time in seconds (default 30)
   */
  public void setSteadyStateMaxWallClockTime(double seconds) {
    this.ssMaxWallClockTime = Math.max(1.0, seconds);
  }

  /**
   * Check whether the last steady-state initialization was stopped by the wall-clock guard.
   *
   * <p>
   * A truncated steady-state solve produces a machine-speed-dependent initial condition, so reproducible or
   * cross-platform studies should either assert that this is {@code false} or raise the limit with
   * {@link #setSteadyStateMaxWallClockTime(double)}.
   * </p>
   *
   * @return true when the wall-clock guard stopped the refinement loop before convergence
   */
  public boolean isSteadyStateWallClockLimited() {
    return ssWallClockLimited;
  }

  /**
   * Whether the friction gradient uses per-phase wall shear where the phases are separated.
   *
   * @return true when the separated friction model is active
   */
  public boolean isSeparatedFrictionModel() {
    return useSeparatedFrictionModel;
  }

  /**
   * Selects the friction model used for the pressure gradient.
   *
   * <p>
   * The mixture form charges the whole perimeter with a hold-up weighted density. That is right for a dispersed flow,
   * but in a separated flow it applies a liquid-dominated density to a bore that is mostly gas and over-predicts the
   * pressure drop badly at high liquid hold-up. Disable only to reproduce the mixture-only behaviour.
   * </p>
   *
   * <p>
   * The separated form is the default because the mixture form left the two halves of the model solving different
   * equations: hold-up came from the per-phase momentum balance while the pressure march used a homogeneous correlation
   * over the whole perimeter. That inconsistency produced the error pattern the model used to show - agreement within a
   * few per cent on a lean gas line, where the mixture density degenerates to the gas density, and a pressure drop
   * nearly three times the reference on a liquid-rich three-phase line. It also inverts the sign of the terrain
   * response, because the mixture friction scales as {@code G^2 / rho_mix}, so a section that holds more liquid returns
   * a LOWER frictional gradient.
   * </p>
   *
   * @param enable true to use per-phase wall shear in stratified and annular flow
   */
  public void setSeparatedFrictionModel(boolean enable) {
    this.useSeparatedFrictionModel = enable;
  }

  /**
   * Check whether the steady-state profile rests on the internal pressure floor.
   *
   * <p>
   * True means the line cannot deliver the specified rate at the specified inlet pressure, so one or more sections were
   * clamped at 1 bara. {@link #isSteadyStateConverged()} is false in that case, and the reported pressure profile is
   * the clamp rather than a solution. Reduce the rate, raise the inlet pressure, or increase the diameter.
   * </p>
   *
   * @return true when at least one section was clamped at the pressure floor
   */
  public boolean isSteadyStatePressureFloorLimited() {
    return ssPressureFloorLimited;
  }

  /**
   * Whether a phase reversed at the outlet during the transient run.
   *
   * <p>
   * The transmissive outlet can only carry mass out, so a reversed phase velocity is clamped to zero. That clamp is
   * correct as a boundary condition and is also a one-way trap: the phase momentum equations of the classical two-fluid
   * system are ill-posed in liquid-rich flow and can develop sustained backflow, after which the outflow of that phase
   * pins at exactly zero while the inlet keeps feeding it and the inventory grows without bound. When this is true the
   * transient profile is not a solution and must be discarded, in the same way as
   * {@link #isSteadyStatePressureFloorLimited()} for the steady solve. Gas-dominated lines do not show it;
   * {@link #setEnableInterfacialPressure(boolean)} removes it at the cost of a much smaller CFL number.
   * </p>
   *
   * @return true when at least one phase reversed at the outlet since the last steady-state solve
   */
  public boolean isTransientOutletBackflowClamped() {
    return transientOutletBackflowClamped;
  }

  /**
   * Get the number of refinement iterations used by the last steady-state initialization.
   *
   * @return iteration count, zero when no steady-state solve has run
   */
  public int getSteadyStateIterationsUsed() {
    return ssIterationsUsed;
  }

  /**
   * Set the maximum number of steady-state refinement iterations.
   *
   * <p>
   * The refinement loop is an under-relaxed fixed-point sweep, so information travels roughly one section per
   * iteration. When this is not set, the limit defaults to {@code max(100, 20 * numberOfSections)}, which is adequate
   * for long transport lines. Set an explicit value to trade run time against convergence.
   * </p>
   *
   * @param maxIterations maximum refinement iterations; zero or negative restores the mesh-derived default
   */
  public void setSteadyStateMaxIterations(int maxIterations) {
    this.ssMaxIterations = maxIterations;
  }

  /**
   * Get the maximum number of steady-state refinement iterations.
   *
   * @return the user-specified limit, or zero when the mesh-derived default is in use
   */
  public int getSteadyStateMaxIterations() {
    return ssMaxIterations;
  }

  /**
   * Check whether the last steady-state solve met its convergence tolerance.
   *
   * <p>
   * When this returns {@code false} the reported pressure, holdup and temperature profiles are the last iterate rather
   * than a converged solution and must not be used for design.
   * </p>
   *
   * @return true when the last steady-state refinement loop converged
   */
  public boolean isSteadyStateConverged() {
    return ssConverged;
  }

  // ============ Minimum Slip Methods ============

  /**
   * Set the optional absolute liquid-holdup floor.
   *
   * <p>
   * The value is used only when {@link #setEnforceMinimumSlip(boolean)} is enabled and
   * {@link #setUseAdaptiveMinimumOnly(boolean)} is disabled. Zero disables the absolute floor, including the annular
   * wetting-film floor. An exactly absent phase always remains at zero regardless of this setting.
   * </p>
   *
   * <p>
   * Typical values:
   * </p>
   * <ul>
   * <li>0.001 (0.1%) - Stored default for backward-compatible fixed-floor studies</li>
   * <li>0.005 (0.5%) - Example calibrated wetting-film assumption</li>
   * <li>0.01 (1%) - Conservative estimate for wet gas</li>
   * <li>0.02 (2%) - High liquid loading or wavy stratified flow</li>
   * </ul>
   *
   * @param minHoldup absolute minimum liquid holdup fraction (0-0.5), stored default 0.001
   */
  public void setMinimumLiquidHoldup(double minHoldup) {
    this.minimumLiquidHoldup = Math.max(0.0, Math.min(0.5, minHoldup));
  }

  /**
   * Get the configured absolute minimum liquid holdup.
   *
   * @return configured minimum liquid holdup fraction
   */
  public double getMinimumLiquidHoldup() {
    return minimumLiquidHoldup;
  }

  /**
   * Set the slip factor used for adaptive minimum holdup calculation.
   *
   * <p>
   * The bound is the minimum ratio of gas to liquid velocity; the hold-up it implies is {@code X / (1 + X)} with
   * {@code X = slipFactor * v_SL / v_SG}. At low liquid loading that reduces to {@code lambdaL * slipFactor}, the form
   * this setting used to be documented as.
   * </p>
   *
   * <p>
   * Example: For a lean gas with 0.5% liquid loading and slipFactor=2.0:
   * </p>
   * <ul>
   * <li>adaptiveMin = 0.005 * 2.0 = 1% holdup</li>
   * <li>This is more reasonable than a fixed 5% minimum</li>
   * </ul>
   *
   * @param slipFactor Minimum ratio of gas to liquid velocity (1.0-5.0), default 2.0
   */
  public void setMinimumSlipFactor(double slipFactor) {
    this.minimumSlipFactor = Math.max(1.0, Math.min(5.0, slipFactor));
  }

  /**
   * Get the slip factor used for adaptive minimum holdup calculation.
   *
   * @return Slip factor (multiplier for no-slip holdup)
   */
  public double getMinimumSlipFactor() {
    return minimumSlipFactor;
  }

  /**
   * Enable or disable the minimum-slip closure constraint.
   *
   * <p>
   * When enabled (default), a correlation-based lower bound is applied for a present liquid phase. The adaptive bound
   * tends continuously to zero with the no-slip liquid fraction and never activates an absent phase.
   * </p>
   *
   * <p>
   * When disabled, no minimum-slip bound is applied.
   * </p>
   *
   * @param enforce true to apply the selected minimum-slip mode, false to disable it
   */
  public void setEnforceMinimumSlip(boolean enforce) {
    this.enforceMinimumSlip = enforce;
  }

  /**
   * Check if the minimum-slip constraint is enabled.
   *
   * @return true if minimum slip is enforced
   */
  public boolean isEnforceMinimumSlip() {
    return enforceMinimumSlip;
  }

  /**
   * Set whether to use adaptive-only minimum holdup (no absolute floor).
   *
   * <p>
   * When true (default), the minimum holdup is calculated from flow correlations and the no-slip holdup without an
   * absolute state floor. The bound tends continuously to zero as liquid input vanishes.
   * </p>
   *
   * <p>
   * When false, {@link #minimumLiquidHoldup} is enforced in addition to the correlation-based minimum for a present
   * liquid phase. This opt-in physical assumption may overpredict trace-liquid inventory. Setting the configured
   * minimum to zero disables that absolute floor.
   * </p>
   *
   * @param useAdaptive true to use correlation-only minimum (recommended for lean gas), false to also enforce absolute
   * floor
   */
  public void setUseAdaptiveMinimumOnly(boolean useAdaptive) {
    this.useAdaptiveMinimumOnly = useAdaptive;
  }

  /**
   * Check if adaptive-only minimum holdup mode is enabled.
   *
   * @return true if using correlation-based minimum only (no absolute floor)
   */
  public boolean isUseAdaptiveMinimumOnly() {
    return useAdaptiveMinimumOnly;
  }

  // ============ Closure-set Configuration Methods ============

  /**
   * Set the NeqSim closure set for holdup and flow-regime calculations.
   *
   * <p>
   * The method and enum names are retained for API compatibility and do not imply numerical equivalence with a
   * commercial simulator. Available modes:
   * </p>
   * <ul>
   * <li>FULL - Flow-regime-specific momentum, film, and slug closures</li>
   * <li>SIMPLIFIED - Reduced empirical correlations</li>
   * <li>DRIFT_FLUX - Original NeqSim drift-flux model (for backward compatibility)</li>
   * </ul>
   *
   * @param modelType closure set to use
   */
  public void setOLGAModelType(OLGAModelType modelType) {
    this.olgaModelType = modelType;
    // Update related settings based on model type
    if (modelType == OLGAModelType.FULL) {
      this.enforceMinimumSlip = true;
      this.enableAnnularFilmModel = true;
      this.enableTerrainTracking = true;
      this.useOLGAFlowRegimeMap = true;
    } else if (modelType == OLGAModelType.SIMPLIFIED) {
      this.enforceMinimumSlip = true;
      this.enableAnnularFilmModel = false;
      this.enableTerrainTracking = true;
      this.useOLGAFlowRegimeMap = false;
    } else {
      // DRIFT_FLUX - original NeqSim behavior
      this.enforceMinimumSlip = false;
      this.enableAnnularFilmModel = false;
      this.enableTerrainTracking = false;
      this.useOLGAFlowRegimeMap = false;
    }
  }

  /**
   * Get the current NeqSim closure set.
   *
   * @return current closure-set enum value
   */
  public OLGAModelType getOLGAModelType() {
    return olgaModelType;
  }

  /**
   * Set minimum film thickness for annular flow model.
   *
   * <p>
   * This value becomes a physical holdup floor only in explicit fixed-floor mode. It is otherwise an annular-closure
   * parameter and does not activate an absent liquid phase.
   * </p>
   *
   * @param thickness minimum film thickness in meters (default 0.0001 m = 0.1 mm)
   */
  public void setMinimumFilmThickness(double thickness) {
    this.minimumFilmThickness = Math.max(0.0, thickness);
  }

  /**
   * Get minimum film thickness for annular flow model.
   *
   * @return minimum film thickness in meters
   */
  public double getMinimumFilmThickness() {
    return minimumFilmThickness;
  }

  /**
   * Enable or disable the literature-inspired annular film model.
   *
   * @param enable true to enable annular film model
   */
  public void setEnableAnnularFilmModel(boolean enable) {
    this.enableAnnularFilmModel = enable;
  }

  /**
   * Check if annular film model is enabled.
   *
   * @return true if annular film model is enabled
   */
  public boolean isEnableAnnularFilmModel() {
    return enableAnnularFilmModel;
  }

  /**
   * Select how the horizontal branch of the flow map decides annular flow.
   *
   * <p>
   * Delegates to the flow regime detector owned by this pipe. See
   * {@link FlowRegimeDetector#setUseEquilibriumLevelAnnularTransition(boolean)} for the two criteria and why the
   * equilibrium-level branch of Taitel and Dukler (1976) is the horizontal one.
   * </p>
   *
   * @param enable true to branch on the equilibrium liquid level, false to use the droplet-entrainment criterion
   */
  public void setUseEquilibriumLevelAnnularTransition(boolean enable) {
    flowRegimeDetector.setUseEquilibriumLevelAnnularTransition(enable);
  }

  /**
   * Which horizontal annular criterion this pipe is using.
   *
   * @return true when the equilibrium-level transition is active
   */
  public boolean isUseEquilibriumLevelAnnularTransition() {
    return flowRegimeDetector.isUseEquilibriumLevelAnnularTransition();
  }

  /**
   * Enable or disable full terrain tracking.
   *
   * <p>
   * Terrain tracking identifies low points and applies empirical liquid-accumulation modifiers in valleys. Establish
   * mesh and timestep convergence against suitable data for the quantity being reported.
   * </p>
   *
   * @param enable true to enable terrain tracking (default true)
   */
  public void setEnableTerrainTracking(boolean enable) {
    this.enableTerrainTracking = enable;
  }

  /**
   * Check if terrain tracking is enabled.
   *
   * @return true if terrain tracking is enabled
   */
  public boolean isEnableTerrainTracking() {
    return enableTerrainTracking;
  }

  /**
   * Set the critical holdup for terrain-induced slug initiation.
   *
   * @param criticalHoldup holdup fraction (0-1) at which terrain slug initiates (default 0.6)
   */
  public void setTerrainSlugCriticalHoldup(double criticalHoldup) {
    this.terrainSlugCriticalHoldup = Math.max(0.0, Math.min(1.0, criticalHoldup));
  }

  /**
   * Get the critical holdup for terrain-induced slug initiation.
   *
   * @return critical holdup fraction
   */
  public double getTerrainSlugCriticalHoldup() {
    return terrainSlugCriticalHoldup;
  }

  /**
   * Set the liquid fallback coefficient for uphill sections.
   *
   * <p>
   * Controls empirical liquid accumulation in uphill sections. Higher values mean more liquid falls back and
   * accumulates. The default 0.3 is a NeqSim setting and is not attributed to a commercial simulator.
   * </p>
   *
   * @param coefficient fallback coefficient (0-1), default 0.3
   */
  public void setLiquidFallbackCoefficient(double coefficient) {
    this.liquidFallbackCoefficient = Math.max(0.0, Math.min(1.0, coefficient));
  }

  /**
   * Get the liquid fallback coefficient.
   *
   * @return liquid fallback coefficient
   */
  public double getLiquidFallbackCoefficient() {
    return liquidFallbackCoefficient;
  }

  /**
   * Enable or disable empirical terrain-slug and riser-base liquid-fallback closures.
   *
   * @param enable true to enable the local closures
   */
  public void setEnableTerrainSlugClosures(boolean enable) {
    this.enableSevereSlugModel = enable;
  }

  /**
   * Check whether empirical terrain-slug and riser-base liquid-fallback closures are enabled.
   *
   * @return true if the local closures are enabled
   */
  public boolean isEnableTerrainSlugClosures() {
    return enableSevereSlugModel;
  }

  /**
   * Legacy alias for {@link #setEnableTerrainSlugClosures(boolean)}.
   *
   * @param enable true to enable the local closures
   * @deprecated This switch does not enable or disable the explicit severe-slugging system diagnostic.
   */
  @Deprecated
  public void setEnableSevereSlugModel(boolean enable) {
    setEnableTerrainSlugClosures(enable);
  }

  /**
   * Legacy alias for {@link #isEnableTerrainSlugClosures()}.
   *
   * @return true if the local closures are enabled
   * @deprecated This value does not report availability of the explicit severe-slugging system diagnostic.
   */
  @Deprecated
  public boolean isEnableSevereSlugModel() {
    return isEnableTerrainSlugClosures();
  }

  /**
   * Enable or disable the historical alternate flow-regime closure.
   *
   * <p>
   * The method name is retained for API compatibility. Enabling it selects a literature-inspired NeqSim closure, not a
   * proprietary commercial flow-regime map.
   * </p>
   *
   * @param enable true to use the historical alternate closure (default true)
   */
  public void setUseOLGAFlowRegimeMap(boolean enable) {
    this.useOLGAFlowRegimeMap = enable;
  }

  /**
   * Check if the historical alternate flow-regime closure is used.
   *
   * @return true if the historical alternate closure is enabled
   */
  public boolean isUseOLGAFlowRegimeMap() {
    return useOLGAFlowRegimeMap;
  }

  /**
   * Set flow regime transition hysteresis factor.
   *
   * <p>
   * Prevents rapid switching between flow regimes near transition boundaries. A value of 0.1 means 10% hysteresis band.
   * </p>
   *
   * @param hysteresis hysteresis factor (0-0.5), default 0.1
   */
  public void setFlowRegimeHysteresis(double hysteresis) {
    this.flowRegimeHysteresis = Math.max(0.0, Math.min(0.5, hysteresis));
  }

  /**
   * Get flow regime transition hysteresis factor.
   *
   * @return hysteresis factor
   */
  public double getFlowRegimeHysteresis() {
    return flowRegimeHysteresis;
  }

  // ============ New Heat Transfer API Methods ============

  /**
   * Set insulation type using predefined U-values.
   *
   * <p>
   * This is a convenience method that sets appropriate heat transfer coefficient based on insulation type.
   * Automatically enables heat transfer modeling.
   * </p>
   *
   * @param type Insulation type preset
   */
  public void setInsulationType(InsulationType type) {
    this.insulationType = type;
    setHeatTransferCoefficient(type.getUValue());
  }

  /**
   * Get the current insulation type.
   *
   * @return Current insulation type
   */
  public InsulationType getInsulationTypeEnum() {
    return insulationType;
  }

  /** {@inheritDoc} */
  @Override
  public String getInsulationType() {
    return insulationType != null ? insulationType.name() : "NONE";
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationType(String type) {
    try {
      this.insulationType = InsulationType.valueOf(type.toUpperCase().replace(" ", "_"));
      setHeatTransferCoefficient(this.insulationType.getUValue());
    } catch (IllegalArgumentException e) {
      // Try to match common names
      if (type.toLowerCase().contains("polyurethane") || type.toLowerCase().contains("pu")) {
        this.insulationType = InsulationType.PU_FOAM;
      } else if (type.toLowerCase().contains("pipe-in-pipe") || type.toLowerCase().contains("pip")) {
        this.insulationType = InsulationType.PIPE_IN_PIPE;
      } else if (type.toLowerCase().contains("vit") || type.toLowerCase().contains("vacuum")) {
        this.insulationType = InsulationType.VIT;
      } else if (type.toLowerCase().contains("buried")) {
        this.insulationType = InsulationType.BURIED_ONSHORE;
      } else if (type.toLowerCase().contains("multi")) {
        this.insulationType = InsulationType.MULTI_LAYER;
      } else {
        this.insulationType = InsulationType.NONE;
      }
      setHeatTransferCoefficient(this.insulationType.getUValue());
    }
  }

  /**
   * Set heat transfer coefficient profile along the pipe.
   *
   * <p>
   * Allows different U-values at different positions (e.g., buried vs exposed sections).
   * </p>
   *
   * @param profile Array of U-values [W/(m²·K)], one per section
   */
  public void setHeatTransferProfile(double[] profile) {
    this.heatTransferProfile = profile;
    if (profile != null && profile.length > 0) {
      this.enableHeatTransfer = true;
      this.includeEnergyEquation = true;
      // Set average value as default coefficient
      double avg = 0;
      for (double h : profile) {
        avg += h;
      }
      this.heatTransferCoefficient = avg / profile.length;
    }
  }

  /**
   * Get the heat transfer coefficient profile.
   *
   * @return Array of U-values or null if constant
   */
  public double[] getHeatTransferProfile() {
    return heatTransferProfile;
  }

  /**
   * Set surface temperature profile along the pipe.
   *
   * <p>
   * Allows different ambient temperatures at different positions (e.g., varying seabed depth).
   * </p>
   *
   * @param profile Array of surface temperatures [K], one per section
   */
  public void setSurfaceTemperatureProfile(double[] profile) {
    this.surfaceTemperatureProfile = profile;
    if (profile != null && profile.length > 0) {
      this.enableHeatTransfer = true;
      this.includeEnergyEquation = true;
    }
  }

  /**
   * Get the surface temperature profile.
   *
   * @return Array of surface temperatures or null if constant
   */
  public double[] getSurfaceTemperatureProfile() {
    return surfaceTemperatureProfile;
  }

  /**
   * Set pipe wall properties for transient thermal calculations.
   *
   * @param thickness Wall thickness [m]
   * @param density Wall material density [kg/m³]
   * @param heatCapacity Wall specific heat capacity [J/(kg·K)]
   */
  public void setWallProperties(double thickness, double density, double heatCapacity) {
    this.wallThickness = thickness;
    this.wallDensity = density;
    this.wallHeatCapacity = heatCapacity;
  }

  /**
   * Get pipe wall thickness.
   *
   * @return Wall thickness [m]
   */
  @Override
  public double getWallThickness() {
    return wallThickness;
  }

  /**
   * Set soil/burial thermal resistance.
   *
   * <p>
   * For buried pipelines, this adds thermal resistance between pipe outer wall and ambient. The effective U-value
   * becomes: U_eff = 1 / (1/U + R_soil)
   * </p>
   *
   * @param resistance Soil thermal resistance [m²·K/W]
   */
  public void setSoilThermalResistance(double resistance) {
    this.soilThermalResistance = Math.max(0, resistance);
  }

  /**
   * Get soil thermal resistance.
   *
   * @return Soil thermal resistance [m²·K/W]
   */
  public double getSoilThermalResistance() {
    return soilThermalResistance;
  }

  // ============ Multi-layer Thermal Model API ============

  /**
   * Get or create the multi-layer thermal calculator.
   *
   * <p>
   * If not yet created, initializes with current pipe geometry. The calculator allows defining multiple radial thermal
   * layers (steel wall, insulation, coatings, etc.) for accurate heat transfer calculations.
   * </p>
   *
   * @return MultilayerThermalCalculator instance
   */
  public MultilayerThermalCalculator getThermalCalculator() {
    if (thermalCalculator == null) {
      thermalCalculator = new MultilayerThermalCalculator(diameter / 2.0);
    }
    return thermalCalculator;
  }

  /**
   * Set a pre-configured thermal calculator.
   *
   * @param calculator Configured MultilayerThermalCalculator
   */
  public void setThermalCalculator(MultilayerThermalCalculator calculator) {
    this.thermalCalculator = calculator;
    this.multilayerLayerTemperatureProfiles = null;
    this.useMultilayerThermalModel = (calculator != null);
    if (calculator != null) {
      setHeatTransferCoefficient(calculator.calculateOverallUValue());
    }
  }

  /**
   * Enable the multi-layer radial heat-transfer model.
   *
   * <p>
   * When enabled, uses the MultilayerThermalCalculator for accurate heat transfer with proper modeling of:
   * </p>
   * <ul>
   * <li>Steel pipe wall thermal mass and conductivity</li>
   * <li>Insulation layers (PU foam, syntactic, aerogel, etc.)</li>
   * <li>Coating layers (FBE, 3LPE, etc.)</li>
   * <li>Concrete weight coating</li>
   * <li>Burial/soil effects</li>
   * </ul>
   *
   * @param enable true to use multi-layer model, false to use simple U-value
   */
  public void setUseMultilayerThermalModel(boolean enable) {
    this.useMultilayerThermalModel = enable;
    this.multilayerLayerTemperatureProfiles = null;
    if (enable) {
      setHeatTransferCoefficient(getThermalCalculator().calculateOverallUValue());
    }
  }

  /**
   * Check if multi-layer thermal model is enabled.
   *
   * @return true if using multi-layer thermal model
   */
  public boolean isUseMultilayerThermalModel() {
    return useMultilayerThermalModel;
  }

  /**
   * Configure standard subsea pipe thermal model.
   *
   * <p>
   * Creates a typical subsea configuration with:
   * </p>
   * <ol>
   * <li>Steel wall</li>
   * <li>FBE corrosion coating</li>
   * <li>Insulation (optional)</li>
   * <li>Concrete weight coating (optional)</li>
   * </ol>
   *
   * @param insulationThickness Insulation thickness [m], 0 for uninsulated
   * @param concreteThickness Concrete coating thickness [m], 0 for none
   * @param insulationMaterial Type of insulation material
   */
  public void configureSubseaThermalModel(double insulationThickness, double concreteThickness,
      RadialThermalLayer.MaterialType insulationMaterial) {
    MultilayerThermalCalculator calc = getThermalCalculator();
    calc.createSubseaPipeConfig(diameter, wallThickness, insulationThickness, concreteThickness, insulationMaterial);
    calc.setAmbientTemperature(surfaceTemperature);
    multilayerLayerTemperatureProfiles = null;
    useMultilayerThermalModel = true;
    // Retain the calculated overall U-value for reporting and activation. Closed-flow inner-film
    // resistance is owned independently by stagnantInnerHeatTransferCoefficient.
    setHeatTransferCoefficient(calc.calculateOverallUValue());
  }

  /**
   * Configure buried onshore pipe thermal model.
   *
   * @param burialDepth Depth of cover [m]
   * @param wetSoil true for wet soil, false for dry
   */
  public void configureBuriedThermalModel(double burialDepth, boolean wetSoil) {
    MultilayerThermalCalculator calc = getThermalCalculator();
    RadialThermalLayer.MaterialType soilType = wetSoil ? RadialThermalLayer.MaterialType.SOIL_WET
        : RadialThermalLayer.MaterialType.SOIL_DRY;
    calc.createBuriedOnshorePipe(diameter, wallThickness, burialDepth, soilType);
    calc.setAmbientTemperature(surfaceTemperature);
    multilayerLayerTemperatureProfiles = null;
    useMultilayerThermalModel = true;
    setHeatTransferCoefficient(calc.calculateOverallUValue());
  }

  /**
   * Calculate cooldown time from current state to a target temperature.
   *
   * <p>
   * Estimates shutdown cooldown time, useful for hydrate prevention planning.
   * </p>
   *
   * @param targetTemperature Target temperature
   * @param unit Temperature unit ("K" or "C")
   * @return Cooldown time in hours
   */
  public double calculateCooldownTime(double targetTemperature, String unit) {
    double targetK = "C".equals(unit) ? targetTemperature + 273.15 : targetTemperature;

    if (useMultilayerThermalModel && thermalCalculator != null) {
      // Use initial fluid temperature
      double initialTemp = getInletStream().getTemperature("K");
      double configuredInnerHtc = thermalCalculator.getInnerHTC();
      thermalCalculator.setFluidTemperature(initialTemp);
      thermalCalculator.setInnerHTC(stagnantInnerHeatTransferCoefficient);
      try {
        thermalCalculator.initializeLayerTemperaturesLinear();
        return thermalCalculator.calculateCooldownTime(targetK);
      } finally {
        thermalCalculator.setInnerHTC(configuredInnerHtc);
      }
    }

    // Simple exponential decay estimate with U-value
    if (heatTransferCoefficient <= 0) {
      return Double.POSITIVE_INFINITY;
    }

    double fluidTemp = getInletStream().getTemperature("K");
    double ambientTemp = surfaceTemperature;

    // Thermal mass per unit length (fluid + wall)
    double fluidArea = Math.PI * diameter * diameter / 4.0;
    double wallArea = Math.PI * (Math.pow(diameter / 2 + wallThickness, 2) - Math.pow(diameter / 2, 2));
    double fluidRho = getInletStream().getFluid().getDensity("kg/m3");
    double fluidCp = getInletStream().getFluid().getCp("J/kgK");
    double thermalMass = fluidArea * fluidRho * fluidCp + wallArea * wallDensity * wallHeatCapacity;

    // Heat transfer surface area per unit length
    double perimeter = Math.PI * diameter;

    // Time constant tau = m*Cp / (h*A)
    double tau = thermalMass / (heatTransferCoefficient * perimeter);

    double dT0 = fluidTemp - ambientTemp;
    double dT_target = targetK - ambientTemp;

    if (dT0 <= 0 || dT_target <= 0 || dT_target >= dT0) {
      return 0.0;
    }

    double cooldownSeconds = -tau * Math.log(dT_target / dT0);
    return cooldownSeconds / 3600.0;
  }

  /**
   * Calculate cooldown time to hydrate formation temperature.
   *
   * @return Cooldown time in hours, or infinity if hydrate temp not set
   */
  public double calculateHydrateCooldownTime() {
    if (hydrateFormationTemperature <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return calculateCooldownTime(hydrateFormationTemperature, "K");
  }

  /**
   * Get thermal summary including U-value and layer details.
   *
   * @return Formatted thermal summary string
   */
  public String getThermalSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Pipeline Thermal Configuration:\n");
    sb.append(String.format("  Pipe ID: %.1f mm\n", diameter * 1000));
    sb.append(String.format("  Wall thickness: %.1f mm\n", wallThickness * 1000));
    sb.append(String.format("  Heat transfer enabled: %s\n", enableHeatTransfer));

    if (useMultilayerThermalModel && thermalCalculator != null) {
      sb.append("\nMulti-layer model enabled:\n");
      sb.append(String.format("  Closed-flow inner HTC: %.1f W/(m²·K) (independent)\n",
          stagnantInnerHeatTransferCoefficient));
      sb.append(thermalCalculator.getSummary());
    } else {
      sb.append(String.format("  U-value: %.2f W/(m²·K)\n", heatTransferCoefficient));
      sb.append(String.format("  Insulation type: %s\n", insulationType));
      sb.append(String.format("  Surface temperature: %.1f °C\n", surfaceTemperature - 273.15));
    }

    if (hydrateFormationTemperature > 0) {
      sb.append(String.format("\nHydrate formation temperature: %.1f °C\n", hydrateFormationTemperature - 273.15));
      sb.append(String.format("Cooldown time to hydrate: %.1f hours\n", calculateHydrateCooldownTime()));
    }

    return sb.toString();
  }

  /**
   * Enable or disable Joule-Thomson effect.
   *
   * <p>
   * When enabled, temperature drops due to pressure reduction are calculated. This is important for gas pipelines with
   * significant pressure drop.
   * </p>
   *
   * @param enable true to enable J-T effect
   */
  public void setEnableJouleThomson(boolean enable) {
    this.enableJouleThomson = enable;
  }

  /**
   * Check if Joule-Thomson effect is enabled.
   *
   * @return true if J-T effect is modeled
   */
  public boolean isJouleThomsonEnabled() {
    return enableJouleThomson;
  }

  /**
   * Set hydrate formation temperature for risk monitoring.
   *
   * @param temperature Hydrate formation temperature
   * @param unit Temperature unit ("K" or "C")
   */
  public void setHydrateFormationTemperature(double temperature, String unit) {
    if ("K".equals(unit)) {
      this.hydrateFormationTemperature = temperature;
    } else if ("C".equals(unit)) {
      this.hydrateFormationTemperature = temperature + 273.15;
    } else {
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /**
   * Get hydrate formation temperature.
   *
   * @return Hydrate formation temperature [K], or 0 if not set
   */
  public double getHydrateFormationTemperature() {
    return hydrateFormationTemperature;
  }

  /**
   * Set wax appearance temperature for risk monitoring.
   *
   * @param temperature Wax appearance temperature
   * @param unit Temperature unit ("K" or "C")
   */
  public void setWaxAppearanceTemperature(double temperature, String unit) {
    if ("K".equals(unit)) {
      this.waxAppearanceTemperature = temperature;
    } else if ("C".equals(unit)) {
      this.waxAppearanceTemperature = temperature + 273.15;
    } else {
      throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }

  /**
   * Get wax appearance temperature.
   *
   * @return Wax appearance temperature [K], or 0 if not set
   */
  public double getWaxAppearanceTemperature() {
    return waxAppearanceTemperature;
  }

  /**
   * Get sections with hydrate formation risk.
   *
   * @return Array of booleans, true where temperature is below hydrate formation temperature
   */
  public boolean[] getHydrateRiskSections() {
    return hydrateRiskSections;
  }

  /**
   * Get sections with wax deposition risk.
   *
   * @return Array of booleans, true where temperature is below wax appearance temperature
   */
  public boolean[] getWaxRiskSections() {
    return waxRiskSections;
  }

  /**
   * Check if any section has hydrate risk.
   *
   * @return true if any section temperature is below hydrate formation temperature
   */
  public boolean hasHydrateRisk() {
    if (hydrateRiskSections == null) {
      return false;
    }
    for (boolean risk : hydrateRiskSections) {
      if (risk) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if any section has wax risk.
   *
   * @return true if any section temperature is below wax appearance temperature
   */
  public boolean hasWaxRisk() {
    if (waxRiskSections == null) {
      return false;
    }
    for (boolean risk : waxRiskSections) {
      if (risk) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get temperature profile with specified unit.
   *
   * @param unit Temperature unit ("K", "C", or "F")
   * @return Temperature profile in the specified unit
   */
  public double[] getTemperatureProfile(String unit) {
    if (temperatureProfile == null) {
      return null;
    }
    double[] result = new double[temperatureProfile.length];
    for (int i = 0; i < temperatureProfile.length; i++) {
      double T_K = temperatureProfile[i];
      switch (unit.toUpperCase()) {
      case "K":
        result[i] = T_K;
        break;
      case "C":
        result[i] = T_K - 273.15;
        break;
      case "F":
        result[i] = (T_K - 273.15) * 9.0 / 5.0 + 32.0;
        break;
      default:
        result[i] = T_K; // Default to Kelvin
      }
    }
    return result;
  }

  /**
   * Get the pipe wall temperature profile.
   *
   * @return Wall temperature profile [K], or null if not calculated
   */
  public double[] getWallTemperatureProfile() {
    return wallTemperatureProfile;
  }

  /**
   * Get number of sections with hydrate risk.
   *
   * @return Count of sections below hydrate formation temperature
   */
  public int getHydrateRiskSectionCount() {
    if (hydrateRiskSections == null) {
      return 0;
    }
    int count = 0;
    for (boolean risk : hydrateRiskSections) {
      if (risk) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get first section index with hydrate risk.
   *
   * @return Section index where hydrate risk first occurs, or -1 if no risk
   */
  public int getFirstHydrateRiskSection() {
    if (hydrateRiskSections == null) {
      return -1;
    }
    for (int i = 0; i < hydrateRiskSections.length; i++) {
      if (hydrateRiskSections[i]) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get distance to first hydrate risk location.
   *
   * @return Distance [m] from inlet to first hydrate risk, or -1 if no risk
   */
  public double getDistanceToHydrateRisk() {
    int idx = getFirstHydrateRiskSection();
    if (idx < 0) {
      return -1;
    }
    if (sections != null) {
      return sections[idx].getPosition();
    }
    return idx * dx;
  }

  // ============ Flow Analysis Methods ============

  /**
   * Get average liquid holdup in the pipe.
   *
   * @return Volume-weighted average liquid holdup (fraction 0-1)
   */
  public double getAverageLiquidHoldup() {
    if (sections == null || sections.length == 0) {
      return 0;
    }
    double totalHoldupVolume = 0;
    double totalVolume = 0;

    for (TwoFluidSection sec : sections) {
      double V = sec.getArea() * sec.getLength();
      totalHoldupVolume += sec.getLiquidHoldup() * V;
      totalVolume += V;
    }
    return totalVolume > 0 ? totalHoldupVolume / totalVolume : 0;
  }

  /**
   * Get dominant flow regime in the pipe.
   *
   * <p>
   * Returns the most common flow regime across all sections.
   * </p>
   *
   * @return Name of dominant flow regime
   */
  public String getDominantFlowRegime() {
    if (sections == null || sections.length == 0) {
      return "Unknown";
    }

    java.util.Map<String, Integer> regimeCounts = new java.util.HashMap<>();
    for (TwoFluidSection sec : sections) {
      neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime regime = sec.getFlowRegime();
      if (regime != null) {
        String regimeName = regime.name();
        regimeCounts.merge(regimeName, 1, Integer::sum);
      }
    }

    if (regimeCounts.isEmpty()) {
      return "Unknown";
    }

    return regimeCounts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
        .orElse("Unknown");
  }

  /**
   * Get average superficial gas velocity in the pipe.
   *
   * @return Average superficial gas velocity [m/s]
   */
  public double getAverageSuperficialGasVelocity() {
    if (sections == null || sections.length == 0) {
      return 0;
    }
    double total = 0;
    for (TwoFluidSection sec : sections) {
      total += sec.getSuperficialGasVelocity();
    }
    return total / sections.length;
  }

  /**
   * Get average superficial liquid velocity in the pipe.
   *
   * @return Average superficial liquid velocity [m/s]
   */
  public double getAverageSuperficialLiquidVelocity() {
    if (sections == null || sections.length == 0) {
      return 0;
    }
    double total = 0;
    for (TwoFluidSection sec : sections) {
      total += sec.getSuperficialLiquidVelocity();
    }
    return total / sections.length;
  }

  /**
   * Get inlet pressure.
   *
   * @return Inlet pressure [bara]
   */
  @Override
  public double getInletPressure() {
    if (sections == null || sections.length == 0) {
      return getInletStream().getPressure("bara");
    }
    return sections[0].getPressure() / 1e5; // Pa to bara
  }

  /**
   * Get outlet pressure.
   *
   * @return Outlet pressure [bara]
   */
  @Override
  public double getOutletPressure() {
    if (sections == null || sections.length == 0) {
      if (outletPressure > 0) {
        return outletPressure;
      }
      return getInletStream().getPressure("bara");
    }
    return sections[sections.length - 1].getPressure() / 1e5; // Pa to bara
  }

  // ============ API 14E Erosional Velocity Methods ============

  /**
   * Calculate erosional velocity per API RP 14E.
   *
   * <p>
   * The API 14E erosional velocity is given by:
   * </p>
   *
   * <p>
   * $$V_e = \frac{C}{\sqrt{\rho_{mix}}}$$
   *
   * <p>
   * where:
   * </p>
   * <ul>
   * <li>$V_e$ = Erosional velocity (m/s)</li>
   * <li>$C$ = Empirical constant (typically 100-150 for continuous service)</li>
   * <li>$\rho_{mix}$ = Average mixture density (kg/m³)</li>
   * </ul>
   *
   * <p>
   * <b>C-Factor Guidelines (API RP 14E)</b>
   * </p>
   * <table>
   * <caption>API 14E C-factor recommendations</caption>
   * <tr>
   * <th>Service</th>
   * <th>C-factor (SI)</th>
   * </tr>
   * <tr>
   * <td>Continuous service</td>
   * <td>100-122</td>
   * </tr>
   * <tr>
   * <td>Intermittent service</td>
   * <td>122-183</td>
   * </tr>
   * <tr>
   * <td>Clean, non-corrosive service</td>
   * <td>122-152</td>
   * </tr>
   * <tr>
   * <td>Corrosive service (CO2, H2S)</td>
   * <td>75-100</td>
   * </tr>
   * </table>
   *
   * <p>
   * Reference: API RP 14E (2007), "Recommended Practice for Design and Installation of Offshore Production Platform
   * Piping Systems", Section 2.5.
   * </p>
   *
   * @param cFactor API 14E C-factor constant (SI units: m/s * sqrt(kg/m³))
   * @return Erosional velocity in m/s
   */
  public double getErosionalVelocity(double cFactor) {
    double rhoMix = getAverageMixtureDensity();
    if (rhoMix <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return cFactor / Math.sqrt(rhoMix);
  }

  /**
   * Calculate erosional velocity using default C-factor of 122.
   *
   * <p>
   * The default C-factor of 122 (SI) corresponds to the commonly used value for continuous service in non-corrosive
   * conditions.
   * </p>
   *
   * @return Erosional velocity in m/s
   */
  public double getErosionalVelocity() {
    return getErosionalVelocity(122.0);
  }

  /**
   * Get average mixture density in the pipe.
   *
   * <p>
   * Calculates volume-weighted average density from all pipe sections.
   * </p>
   *
   * @return Average mixture density in kg/m³
   */
  public double getAverageMixtureDensity() {
    if (sections == null || sections.length == 0) {
      return 0;
    }
    double totalMass = 0;
    double totalVolume = 0;

    for (TwoFluidSection sec : sections) {
      double A = sec.getArea();
      double L = sec.getLength();
      double V = A * L;

      double alphaG = sec.getGasHoldup();
      double alphaL = sec.getLiquidHoldup();
      double rhoG = sec.getGasDensity();
      double rhoL = sec.getLiquidDensity();

      // Handle zero density values with defaults
      if (rhoG <= 0) {
        rhoG = 1.0;
      }
      if (rhoL <= 0) {
        rhoL = 700.0;
      }

      double rhoMixSection = alphaG * rhoG + alphaL * rhoL;
      totalMass += rhoMixSection * V;
      totalVolume += V;
    }

    return totalVolume > 0 ? totalMass / totalVolume : 0;
  }

  /**
   * Get maximum mixture velocity in the pipe.
   *
   * <p>
   * Scans all sections to find the highest mixture velocity, which occurs where velocity is maximum.
   * </p>
   *
   * @return Maximum mixture velocity in m/s
   */
  public double getMaxMixtureVelocity() {
    if (sections == null || sections.length == 0) {
      return 0;
    }
    double maxVmix = 0;

    for (TwoFluidSection sec : sections) {
      double alphaG = sec.getGasHoldup();
      double alphaL = sec.getLiquidHoldup();
      double vG = sec.getGasVelocity();
      double vL = sec.getLiquidVelocity();

      // Mixture velocity weighted by volume fraction
      double vMix = alphaG * vG + alphaL * vL;
      maxVmix = Math.max(maxVmix, Math.abs(vMix));
    }
    return maxVmix;
  }

  /**
   * Check if mixture velocity exceeds erosional limit.
   *
   * @param cFactor API 14E C-factor constant (SI units)
   * @return true if maximum velocity exceeds erosional limit
   */
  public boolean isVelocityAboveErosionalLimit(double cFactor) {
    double vE = getErosionalVelocity(cFactor);
    double vMax = getMaxMixtureVelocity();
    return vMax > vE;
  }

  /**
   * Check if mixture velocity exceeds erosional limit using default C-factor.
   *
   * @return true if maximum velocity exceeds erosional limit (C=122)
   */
  public boolean isVelocityAboveErosionalLimit() {
    return isVelocityAboveErosionalLimit(122.0);
  }

  /**
   * Get erosional velocity margin.
   *
   * <p>
   * Calculates the ratio of actual maximum velocity to erosional velocity. Values greater than 1.0 indicate erosion
   * risk.
   * </p>
   *
   * @param cFactor API 14E C-factor constant (SI units)
   * @return Velocity margin (V_max / V_erosional). Values &gt; 1.0 indicate erosion risk.
   */
  public double getErosionalVelocityMargin(double cFactor) {
    double vE = getErosionalVelocity(cFactor);
    if (vE <= 0 || Double.isInfinite(vE)) {
      return 0;
    }
    return getMaxMixtureVelocity() / vE;
  }

  /**
   * Get erosion risk assessment.
   *
   * <p>
   * Returns a summary of erosion risk based on API 14E criteria.
   * </p>
   *
   * @param cFactor API 14E C-factor constant
   * @return Erosion risk assessment string
   */
  public String getErosionRiskAssessment(double cFactor) {
    double vE = getErosionalVelocity(cFactor);
    double vMax = getMaxMixtureVelocity();
    double margin = getErosionalVelocityMargin(cFactor);

    StringBuilder sb = new StringBuilder();
    sb.append("=== API 14E Erosion Assessment ===\n");
    sb.append(String.format("C-factor: %.0f (SI units)\n", cFactor));
    sb.append(String.format("Average mixture density: %.2f kg/m³\n", getAverageMixtureDensity()));
    sb.append(String.format("Erosional velocity (V_e): %.2f m/s\n", vE));
    sb.append(String.format("Maximum mixture velocity: %.2f m/s\n", vMax));
    sb.append(String.format("Velocity margin (V_max/V_e): %.2f\n", margin));

    if (margin < 0.7) {
      sb.append("Status: LOW RISK - Velocity well below erosional limit\n");
    } else if (margin < 0.9) {
      sb.append("Status: MEDIUM RISK - Approaching erosional limit\n");
    } else if (margin < 1.0) {
      sb.append("Status: HIGH RISK - Near erosional limit, monitor closely\n");
    } else {
      sb.append("Status: EXCEEDS LIMIT - Erosion damage likely, reduce velocity\n");
    }

    return sb.toString();
  }

  /**
   * Get flow quality analysis summary for comparison with literature data.
   *
   * <p>
   * Returns key dimensionless parameters used in published two-phase flow correlations.
   * </p>
   *
   * @return Flow analysis summary with dimensionless parameters
   */
  public String getFlowAnalysisSummary() {
    if (sections == null || sections.length == 0) {
      return "No data - run simulation first";
    }

    TwoFluidSection midSection = sections[sections.length / 2];
    double vSL = midSection.getSuperficialLiquidVelocity();
    double vSG = midSection.getSuperficialGasVelocity();
    double vMix = vSL + vSG;
    double lambdaL = vMix > 0 ? vSL / vMix : 0;
    double alphaL = midSection.getLiquidHoldup();
    double rhoL = midSection.getLiquidDensity();
    double rhoG = midSection.getGasDensity();
    double rhoMix = alphaL * rhoL + (1 - alphaL) * rhoG;
    double muL = midSection.getLiquidViscosity();
    double sigma = midSection.getSurfaceTension();

    // Froude number
    double Fr = vMix * vMix / (9.81 * diameter);

    // Weber number
    double We = rhoMix * vMix * vMix * diameter / sigma;

    // Liquid Reynolds number
    double ReL = rhoL * vSL * diameter / muL;

    // Superficial gas Reynolds
    double muG = midSection.getGasViscosity();
    double ReG = rhoG * vSG * diameter / muG;

    // Slip ratio
    double vG = midSection.getGasVelocity();
    double vL = midSection.getLiquidVelocity();
    double slip = vL > 0 ? vG / vL : 1.0;

    StringBuilder sb = new StringBuilder();
    sb.append("=== Two-Phase Flow Analysis (Mid-Pipe) ===\n");
    sb.append(String.format("Flow regime: %s\n", midSection.getFlowRegime()));
    sb.append("\n--- Velocities ---\n");
    sb.append(String.format("Superficial gas velocity (v_SG): %.3f m/s\n", vSG));
    sb.append(String.format("Superficial liquid velocity (v_SL): %.3f m/s\n", vSL));
    sb.append(String.format("Mixture velocity: %.3f m/s\n", vMix));
    sb.append(String.format("Actual gas velocity: %.3f m/s\n", vG));
    sb.append(String.format("Actual liquid velocity: %.3f m/s\n", vL));

    sb.append("\n--- Holdup ---\n");
    sb.append(String.format("No-slip holdup (λ_L): %.4f\n", lambdaL));
    sb.append(String.format("Actual liquid holdup (H_L): %.4f\n", alphaL));
    sb.append(String.format("Slip ratio (v_G/v_L): %.3f\n", slip));

    sb.append("\n--- Dimensionless Parameters (Literature Comparison) ---\n");
    sb.append(String.format("Froude number (Fr): %.3f\n", Fr));
    sb.append(String.format("Weber number (We): %.1f\n", We));
    sb.append(String.format("Liquid Reynolds (Re_SL): %.0f\n", ReL));
    sb.append(String.format("Gas Reynolds (Re_SG): %.0f\n", ReG));

    sb.append("\n--- Properties ---\n");
    sb.append(String.format("Gas density: %.2f kg/m³\n", rhoG));
    sb.append(String.format("Liquid density: %.2f kg/m³\n", rhoL));
    sb.append(String.format("Mixture density: %.2f kg/m³\n", rhoMix));
    sb.append(String.format("Surface tension: %.4f N/m\n", sigma));

    return sb.toString();
  }

  // ============ Junction/Bend Loss Methods ============

  /**
   * Add a local loss (K-factor) at a specific pipe position.
   *
   * <p>
   * K-factors represent minor losses from fittings, valves, and bends. The pressure drop is: ΔP = K × 0.5 × ρ × v²
   * </p>
   *
   * <p>
   * Typical K-factor values:
   * </p>
   * <table>
   * <caption>Typical K-factor values for pipe fittings</caption>
   * <tr>
   * <th>Fitting</th>
   * <th>K-factor</th>
   * </tr>
   * <tr>
   * <td>90° elbow (standard)</td>
   * <td>0.30</td>
   * </tr>
   * <tr>
   * <td>90° elbow (long radius)</td>
   * <td>0.20</td>
   * </tr>
   * <tr>
   * <td>45° elbow</td>
   * <td>0.17</td>
   * </tr>
   * <tr>
   * <td>Tee (straight through)</td>
   * <td>0.20</td>
   * </tr>
   * <tr>
   * <td>Tee (branch)</td>
   * <td>1.00</td>
   * </tr>
   * <tr>
   * <td>Gate valve (fully open)</td>
   * <td>0.10</td>
   * </tr>
   * <tr>
   * <td>Ball valve (fully open)</td>
   * <td>0.05</td>
   * </tr>
   * <tr>
   * <td>Check valve (swing)</td>
   * <td>2.00</td>
   * </tr>
   * <tr>
   * <td>Sudden expansion</td>
   * <td>1.00</td>
   * </tr>
   * <tr>
   * <td>Sudden contraction</td>
   * <td>0.50</td>
   * </tr>
   * </table>
   *
   * @param position Distance from inlet (m)
   * @param kFactor Loss coefficient (dimensionless)
   */
  public void addLocalLoss(double position, double kFactor) {
    if (position >= 0 && position <= length && kFactor >= 0) {
      localLossKFactors.put(position, kFactor);
    }
  }

  /**
   * Clear all local losses.
   */
  public void clearLocalLosses() {
    localLossKFactors.clear();
  }

  /**
   * Get local loss K-factor at a specific position.
   *
   * @param position Distance from inlet (m)
   * @return K-factor at that position, or 0 if none defined
   */
  public double getLocalLossKFactor(double position) {
    Double k = localLossKFactors.get(position);
    return (k != null) ? k : 0.0;
  }

  /**
   * Get total of all local loss K-factors.
   *
   * @return Sum of all K-factors
   */
  public double getTotalLocalLossKFactors() {
    double total = 0;
    for (Double k : localLossKFactors.values()) {
      total += k;
    }
    // Add entrance/exit losses
    total += inletLossCoefficient;
    total += outletLossCoefficient;
    // Add bend losses
    total += numberOf90DegreeBends * 0.30; // Standard K for 90° bend
    total += numberOf45DegreeBends * 0.17; // Standard K for 45° bend
    return total;
  }

  /**
   * Set number of 90-degree bends in the pipe.
   *
   * @param count Number of 90° bends
   */
  public void setNumberOf90DegreeBends(int count) {
    this.numberOf90DegreeBends = Math.max(0, count);
  }

  /**
   * Set number of 45-degree bends in the pipe.
   *
   * @param count Number of 45° bends
   */
  public void setNumberOf45DegreeBends(int count) {
    this.numberOf45DegreeBends = Math.max(0, count);
  }

  /**
   * Set inlet loss coefficient.
   *
   * @param kFactor Inlet K-factor (sharp-edge ~ 0.5, bell-mouth ~ 0.05)
   */
  public void setInletLossCoefficient(double kFactor) {
    this.inletLossCoefficient = Math.max(0, kFactor);
  }

  /**
   * Set outlet loss coefficient.
   *
   * @param kFactor Outlet K-factor (sudden expansion ~ 1.0)
   */
  public void setOutletLossCoefficient(double kFactor) {
    this.outletLossCoefficient = Math.max(0, kFactor);
  }

  /**
   * Set equivalent length of fittings.
   *
   * <p>
   * Alternative to specifying K-factors: add equivalent pipe length that produces the same friction loss as the
   * fittings.
   * </p>
   *
   * @param equivalentLength Equivalent length in meters
   */
  public void setEquivalentLengthFittings(double equivalentLength) {
    this.equivalentLengthFittings = Math.max(0, equivalentLength);
  }

  /**
   * Get equivalent length of fittings.
   *
   * @return Equivalent length in meters
   */
  public double getEquivalentLengthFittings() {
    return equivalentLengthFittings;
  }

  /**
   * Calculate total pressure drop from local losses.
   *
   * <p>
   * Uses the formula: ΔP = Σ(K × 0.5 × ρ_mix × v_mix²)
   * </p>
   *
   * @return Total pressure drop from fittings/bends in Pa
   */
  public double calculateLocalLossPressureDrop() {
    double totalK = getTotalLocalLossKFactors();
    if (totalK <= 0 || sections == null || sections.length == 0) {
      return 0;
    }

    // Use average conditions in pipe
    double rhoMix = getAverageMixtureDensity();
    double vMix = getMaxMixtureVelocity();

    // ΔP = K × 0.5 × ρ × v²
    return totalK * 0.5 * rhoMix * vMix * vMix;
  }

  /**
   * Calculate total pressure drop including local losses.
   *
   * @return Total pressure drop (friction + gravity + local losses) in bar
   */
  public double getTotalPressureDrop() {
    double pIn = getInletPressure();
    double pOut = getOutletPressure();
    double frictionAndGravityDrop = pIn - pOut; // bar

    // Add local losses
    double localLossDrop = calculateLocalLossPressureDrop() / 1e5; // Pa to bar

    return frictionAndGravityDrop + localLossDrop;
  }

  /**
   * Get a summary of local losses.
   *
   * @return Summary string of all local losses
   */
  public String getLocalLossSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Local Loss Summary ===\n");
    sb.append(String.format("90° bends: %d (K = %.2f each)\n", numberOf90DegreeBends, 0.30));
    sb.append(String.format("45° bends: %d (K = %.2f each)\n", numberOf45DegreeBends, 0.17));
    sb.append(String.format("Inlet K-factor: %.3f\n", inletLossCoefficient));
    sb.append(String.format("Outlet K-factor: %.3f\n", outletLossCoefficient));
    sb.append(String.format("Custom K-factors: %d locations\n", localLossKFactors.size()));
    for (java.util.Map.Entry<Double, Double> entry : localLossKFactors.entrySet()) {
      sb.append(String.format("  Position %.1f m: K = %.3f\n", entry.getKey(), entry.getValue()));
    }
    sb.append(String.format("Equivalent length: %.1f m\n", equivalentLengthFittings));
    sb.append(String.format("Total K-factor: %.3f\n", getTotalLocalLossKFactors()));
    sb.append(String.format("Local loss pressure drop: %.3f bar\n", calculateLocalLossPressureDrop() / 1e5));
    return sb.toString();
  }
}
