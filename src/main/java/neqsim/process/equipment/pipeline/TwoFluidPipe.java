package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.twophasepipe.ConservativeEnergyTransport;
import neqsim.process.equipment.pipeline.twophasepipe.ConservativeThermodynamicRecovery;
import neqsim.process.equipment.pipeline.twophasepipe.FlowRegimeDetector;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic;
import neqsim.process.equipment.pipeline.twophasepipe.SlugFilmCoupling;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.NonEquilibriumFilmTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.HydrocarbonPoolTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.closure.BubbleSizeClosure;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterFlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.SlugForceBalance;
import neqsim.process.equipment.pipeline.twophasepipe.closure.SlugUnitModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AnchoredIsothermalDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.ConservativeStateLimiter;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.CoupledPressureMomentumSolver.GasDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PhaseDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitPublication;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.TwoFluidPipeResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;

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

  /**
   * Owner-bound result of advancing one complete transient interval on a detached pipe graph.
   *
   * <p>
   * A candidate can be inspected without changing the live pipe. It can be accepted once by its creating pipe, or
   * rolled back once after acceptance by a higher-level transaction coordinator. The owner rejects foreign, stale,
   * reused, and externally modified candidates.
   * </p>
   */
  public static final class TransientCandidate {
    private enum State {
      PREPARED, ACCEPTED, ROLLED_BACK
    }

    private final TwoFluidPipe owner;
    private final TwoFluidPipe candidate;
    private final byte[] baseState;
    private byte[] acceptedState;
    private State state = State.PREPARED;

    /** Create one private owner-bound candidate token. */
    private TransientCandidate(TwoFluidPipe owner, TwoFluidPipe candidate, byte[] baseState) {
      this.owner = owner;
      this.candidate = candidate;
      this.baseState = baseState;
    }

    /** @return candidate phase and total mass balance */
    public TwoFluidMassBalanceReport getMassBalanceReport() {
      return candidate.getLastMassBalanceReport();
    }

    /** @return candidate named-component balance, or {@code null} when component transport is disabled */
    public TwoFluidComponentConservationReport getComponentConservationReport() {
      return candidate.getLastComponentConservationReport();
    }

    /** @return candidate conservative-energy balance, or {@code null} when recovery is disabled */
    public TwoFluidConservativeEnergyReport getConservativeEnergyReport() {
      return candidate.getLastConservativeEnergyReport();
    }

    /** @return candidate pipe simulation time in seconds */
    public double getSimulationTimeSeconds() {
      return candidate.getSimulationTime();
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new TwoFluidPipeResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    TwoFluidPipeResponse response = new TwoFluidPipeResponse(this);
    response.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(response);
  }

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

  /** Geometric scan points used to bracket the thinnest stratified-film root. */
  private static final int STRATIFIED_ROOT_SCAN_POINTS = 60;

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
   * <p>
   * Off by default until the long-horizon liquid-rich and severe-slugging acceptance cases pass.
   */
  private boolean coupledPressureMomentumEnabled = false;

  /** True once the user has chosen the coupled pressure-momentum option explicitly. */
  private boolean coupledPressureMomentumExplicit = false;

  /** Whether any coupled nonlinear correction failed since the latest steady initialization. */
  private boolean transientCoupledPressureMomentumFailureDetected = false;

  /** Whether any coupled nonlinear pressure correction was limited since steady initialization. */
  private boolean transientCoupledPressureMomentumCorrectionLimited = false;

  /** Number of bounded nonlinear iterations, including rejected substeps, since steady initialization. */
  private long transientPressureLimitCount;
  /** First attempted substep start time with a bounded correction; NaN before any event. */
  private double firstTransientPressureLimitTime = Double.NaN;
  /** Smallest actual Newton damping among pressure-limit events. */
  private double minimumTransientPressureDamping = 1.0;

  /** Coupled nonlinear substeps rejected since the latest steady initialization. */
  private int transientCoupledPressureMomentumRejectedSubsteps = 0;

  /** Exact diagnostics for coupled nonlinear substeps rejected since steady initialization. */
  private String transientCoupledPressureMomentumFailureDiagnostic = "";

  /** Optional phase-resolved compressible volume supplying the inlet pressure boundary. */
  private UpstreamCompressibleVolume upstreamCompressibleVolume = null;

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

  /** Explicit elevations at the N+1 finite-volume faces (m), or null for the legacy section-indexed convention. */
  private double[] cellFaceElevationProfile;

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

  /** Maximum attempted substeps per call; zero retains the mode-dependent default. */
  private int maximumTransientSubsteps;

  /** Maximum simulation time (s). */
  private double maxSimulationTime = 3600;

  /** CFL number for time stepping (0 &lt; CFL &lt; 1). */
  private double cflNumber = 0.5;

  // ============ Sub-models ============

  /** Conservation equations solver. */
  private TwoFluidConservationEquations equations;

  /** Experimental pressure interpolation used only by independent unsplit preparation. */
  private boolean unsplitPressureInterpolationEnabled;

  /** Stage the legacy transient on an isolated serializable candidate; default false. */
  private boolean transactionalTransientEnabled;

  /** Opt-in conservative internal energy and volume thermodynamic recovery. */
  private boolean useConservativeEnergyRecovery;

  /** Accepted open-control-volume energy audit for conservative recovery. */
  private TwoFluidConservativeEnergyReport lastConservativeEnergyReport;

  /** Explicitly selected experimental unsplit solver, or null for the legacy integrator. */
  private UnsplitTransientSolver unsplitTransientSolver;

  /** Maximum attempted step in the explicitly selected unsplit route (s). */
  private double unsplitMaximumTimeStep;

  /** Persistent pressure-dependent EOS branches for consecutive accepted unsplit intervals. */
  private AnchoredIsothermalDensityModel unsplitDensityModel;

  /** Original pressure/temperature conditions defining the persistent frozen phase compositions. */
  private TwoFluidSection[] unsplitReferenceSections;

  /** Opt-in reduced shared mechanical slug closure; legacy correlations remain the default. */
  private boolean sharedSlugForceBalanceEnabled;

  /** Shared stateless mechanical evaluator. */
  private SlugForceBalance sharedSlugForceBalance = new SlugForceBalance();

  /** Steady slug-unit closure for the slug share of holdup and friction; on by default. */
  private boolean slugUnitClosureEnabled = true;

  /** Film holdup at which an annular film bridges the bore (Barnea 1986 blockage limit). */
  private static final double ANNULAR_FILM_BRIDGING_HOLDUP = 0.24;

  /** Half width of the bridging band, matching the flow-regime detector's level transition band. */
  private static final double SLUG_BRIDGING_BAND = 0.08;

  /** Inclination above which the stratified-layer bridging test no longer applies, radians. */
  private static final double SLUG_BRIDGING_MAX_INCLINATION = Math.toRadians(45.0);

  /** Stateless slug-unit evaluator. */
  private SlugUnitModel slugUnitModel = new SlugUnitModel();

  /** Time integrator. */
  private TimeIntegrator timeIntegrator;

  /** Flow regime detector. */
  private F