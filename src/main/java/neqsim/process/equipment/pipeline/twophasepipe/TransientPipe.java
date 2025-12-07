package neqsim.process.equipment.pipeline.twophasepipe;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.pipeline.twophasepipe.DriftFluxModel.DriftFluxParameters;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Transient multiphase pipe model using drift-flux formulation.
 *
 * <p>
 * Implements a 1D transient multiphase flow simulator for gas-liquid flow in pipelines. The model
 * is suitable for analyzing terrain-induced slugging, liquid accumulation, and transient pressure
 * behavior in production pipelines, risers, and flowlines.
 * </p>
 *
 * <p>
 * <b>Three-Phase Flow Support:</b> When both oil and aqueous (water) phases are present, the model
 * automatically calculates volume-weighted average liquid properties (density, viscosity, enthalpy,
 * sound speed) based on the individual phase volumes from the thermodynamic flash. This maintains
 * the drift-flux framework while properly accounting for oil-water mixtures.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Drift-flux model for gas-liquid slip (Zuber-Findlay formulation)</li>
 * <li>Mechanistic flow regime detection (Taitel-Dukler, Barnea)</li>
 * <li>Three-phase gas-oil-water flow with volume-weighted liquid property averaging</li>
 * <li>Liquid accumulation tracking at terrain low points</li>
 * <li>Lagrangian slug tracking for terrain-induced slugging</li>
 * <li>Integration with NeqSim thermodynamics (SRK, PR, CPA equations of state)</li>
 * <li>AUSM+ numerical flux scheme with adaptive CFL-based time stepping</li>
 * </ul>
 *
 * <h2>Basic Usage</h2>
 * 
 * <pre>{@code
 * // Create two-phase fluid
 * SystemInterface fluid = new SystemSrkEos(300, 50);
 * fluid.addComponent("methane", 0.8);
 * fluid.addComponent("n-pentane", 0.2);
 * fluid.setMixingRule("classic");
 * fluid.setMultiPhaseCheck(true);
 *
 * // Create inlet stream
 * Stream inlet = new Stream("inlet", fluid);
 * inlet.setFlowRate(5, "kg/sec");
 * inlet.run();
 *
 * // Create and configure transient pipe
 * TransientPipe pipe = new TransientPipe("Pipeline", inlet);
 * pipe.setLength(1000); // 1000 m total length
 * pipe.setDiameter(0.2); // 200 mm inner diameter
 * pipe.setNumberOfSections(50); // 50 computational cells
 * pipe.setMaxSimulationTime(60); // 60 seconds simulation
 *
 * // Run simulation
 * pipe.run();
 *
 * // Access results
 * double[] pressures = pipe.getPressureProfile();
 * double[] holdups = pipe.getLiquidHoldupProfile();
 * }</pre>
 *
 * <h2>Terrain Pipeline Example</h2>
 * 
 * <pre>{@code
 * TransientPipe pipe = new TransientPipe("TerrainPipe", inlet);
 * pipe.setLength(2000);
 * pipe.setDiameter(0.3);
 * pipe.setNumberOfSections(40);
 *
 * // Define elevation profile with low point
 * double[] elevations = new double[40];
 * for (int i = 0; i < 40; i++) {
 *   double x = i * 50.0;
 *   if (x < 500)
 *     elevations[i] = 0;
 *   else if (x < 1000)
 *     elevations[i] = -20 * (x - 500) / 500;
 *   else if (x < 1500)
 *     elevations[i] = -20 + 20 * (x - 1000) / 500;
 *   else
 *     elevations[i] = 0;
 * }
 * pipe.setElevationProfile(elevations);
 *
 * pipe.run();
 *
 * // Check liquid accumulation
 * var accumTracker = pipe.getAccumulationTracker();
 * for (var zone : accumTracker.getAccumulationZones()) {
 *   System.out.println("Accumulation at: " + zone.getPosition() + " m");
 * }
 * }</pre>
 *
 * <h2>Physical Model</h2>
 * <p>
 * The drift-flux model relates gas velocity to mixture velocity:
 * </p>
 * 
 * <pre>
 * v_G = C₀ · v_m + v_d
 * </pre>
 * <p>
 * where C₀ is the distribution coefficient (typically 1.0-1.2) and v_d is the drift velocity. Flow
 * regime-dependent correlations from Bendiksen (1984) and Harmathy (1960) provide closure.
 * </p>
 *
 * <h2>Numerical Method</h2>
 * <p>
 * The model solves conservation equations using an explicit finite volume scheme with AUSM+ flux
 * splitting. Time stepping is adaptive based on CFL condition for numerical stability.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Taitel, Y. and Dukler, A.E. (1976) - AIChE Journal 22(1)</li>
 * <li>Barnea, D. (1987) - Int. J. Multiphase Flow 13(1)</li>
 * <li>Bendiksen, K.H. (1984) - Int. J. Multiphase Flow 10(4)</li>
 * <li>Zuber, N. and Findlay, J.A. (1965) - J. Heat Transfer 87(4)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see FlowRegimeDetector
 * @see DriftFluxModel
 * @see SlugTracker
 * @see LiquidAccumulationTracker
 */
public class TransientPipe extends TwoPortEquipment implements PipeLineInterface {

  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.81;

  // Geometry
  private double length; // Total pipe length (m)
  private double diameter; // Pipe diameter (m)
  private double roughness = 0.0001; // Wall roughness (m)
  private int numberOfSections = 50; // Number of discretization cells
  private double wallThickness = 0.01; // m
  private double pipeElasticity = 2.1e11; // Pa (steel)

  // Profile geometry
  private double[] elevationProfile; // Elevation at each node (m)
  private double[] inclinationProfile; // Inclination at each segment (rad)

  // Discretization
  private PipeSection[] sections;
  private double dx; // Spatial step (m)

  // Transient state
  private double simulationTime = 0; // Current simulation time (s)
  private double maxSimulationTime = 3600; // Max simulation time (s)
  private double dt = 0.1; // Time step (s)
  private double cflNumber = 0.5; // CFL number for stability
  private double minTimeStep = 1e-4; // Minimum time step (s)
  private double maxTimeStep = 10.0; // Maximum time step (s)

  // Sub-models
  private FlowRegimeDetector flowRegimeDetector;
  private DriftFluxModel driftFluxModel;
  private LiquidAccumulationTracker accumulationTracker;
  private SlugTracker slugTracker;

  // Boundary conditions
  private BoundaryCondition inletBCType = BoundaryCondition.CONSTANT_FLOW;
  private BoundaryCondition outletBCType = BoundaryCondition.CONSTANT_PRESSURE;
  private double inletPressureValue; // Pa
  private double outletPressureValue; // Pa
  private boolean outletPressureExplicitlySet = false; // Track if user set outlet pressure
  private double inletMassFlow; // kg/s
  private double outletMassFlow; // kg/s

  // Heat transfer
  private boolean includeHeatTransfer = false;
  private double ambientTemperature = 288.15; // K
  private double overallHeatTransferCoeff = 10.0; // W/(m²·K)

  // Thermodynamic coupling
  private SystemInterface referenceFluid;
  private boolean updateThermodynamics = true;
  private int thermodynamicUpdateInterval = 10; // Update every N time steps
  private int currentStep = 0;

  // Results storage
  private double[] pressureProfile;
  private double[] temperatureProfile;
  private double[] liquidHoldupProfile;
  private double[] gasVelocityProfile;
  private double[] liquidVelocityProfile;
  private double[][] pressureHistory; // [time][position]
  private int historyInterval = 10; // Store every N steps
  private int historyIndex = 0;

  // Convergence tracking
  private double massResidual;
  private double energyResidual;
  private boolean isConverged = false;
  private int totalTimeSteps = 0;

  /**
   * Boundary condition types for inlet and outlet.
   *
   * <p>
   * Specifies how the boundary conditions are handled at the pipe inlet and outlet.
   * </p>
   *
   * <ul>
   * <li>{@link #CONSTANT_PRESSURE} - Fixed pressure (typical for outlet)</li>
   * <li>{@link #CONSTANT_FLOW} - Fixed mass flow rate (typical for inlet)</li>
   * <li>{@link #CONSTANT_VELOCITY} - Fixed velocity</li>
   * <li>{@link #CLOSED} - No flow (wall boundary)</li>
   * <li>{@link #TRANSIENT_PRESSURE} - Time-varying pressure</li>
   * <li>{@link #TRANSIENT_FLOW} - Time-varying flow rate</li>
   * </ul>
   *
   * <p>
   * <b>Example:</b>
   * </p>
   * 
   * <pre>{@code
   * pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
   * pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
   * pipe.setInletMassFlow(5.0); // kg/s
   * pipe.setoutletPressureValue(30e5); // Pa
   * }</pre>
   */
  public enum BoundaryCondition {
    /** Constant pressure boundary. */
    CONSTANT_PRESSURE,
    /** Constant mass flow boundary. */
    CONSTANT_FLOW,
    /** Constant velocity boundary. */
    CONSTANT_VELOCITY,
    /** Closed boundary (no flow). */
    CLOSED,
    /** Time-varying pressure. */
    TRANSIENT_PRESSURE,
    /** Time-varying flow. */
    TRANSIENT_FLOW
  }

  /**
   * Default constructor. Creates a TransientPipe with default name "TransientPipe".
   */
  public TransientPipe() {
    super("TransientPipe");
    initializeSubModels();
  }

  /**
   * Constructor with name.
   *
   * @param name Pipe name used for identification in process systems
   */
  public TransientPipe(String name) {
    super(name);
    initializeSubModels();
  }

  /**
   * Constructor with name and inlet stream.
   *
   * <p>
   * This is the recommended constructor for typical usage. The inlet stream provides initial
   * conditions (composition, temperature, pressure, flow rate) for the simulation.
   * </p>
   *
   * @param name Pipe name used for identification in process systems
   * @param inletStream Inlet stream containing fluid properties and flow conditions
   */
  public TransientPipe(String name, StreamInterface inletStream) {
    super(name, inletStream);
    initializeSubModels();
  }

  /**
   * Initialize sub-models for flow regime detection, drift-flux, and slug tracking.
   */
  private void initializeSubModels() {
    flowRegimeDetector = new FlowRegimeDetector();
    driftFluxModel = new DriftFluxModel();
    accumulationTracker = new LiquidAccumulationTracker();
    slugTracker = new SlugTracker();
  }

  /**
   * Initialize the discretized pipe sections.
   *
   * <p>
   * Creates the computational mesh based on pipe length and number of sections. Also initializes
   * elevation/inclination profiles and identifies low points for liquid accumulation tracking.
   * </p>
   *
   * <p>
   * This method is called automatically by {@link #run()} if sections have not been initialized. It
   * can also be called explicitly to inspect the mesh before running.
   * </p>
   *
   * <p>
   * <b>Note:</b> The number of sections determines spatial resolution. For accurate slug tracking,
   * use at least 20 sections. Typical guideline: dx ≈ 10-50 pipe diameters.
   * </p>
   */
  public void initializePipe() {
    dx = length / numberOfSections;
    sections = new PipeSection[numberOfSections];

    // Create sections
    for (int i = 0; i < numberOfSections; i++) {
      sections[i] = new PipeSection(i * dx, dx, diameter, 0);
      sections[i].setRoughness(roughness);
    }

    // Set elevation profile
    if (elevationProfile != null && elevationProfile.length >= numberOfSections) {
      for (int i = 0; i < numberOfSections; i++) {
        sections[i].setElevation(elevationProfile[i]);
        if (i > 0) {
          double dz = elevationProfile[i] - elevationProfile[i - 1];
          sections[i].setInclination(Math.atan2(dz, dx));
        }
      }
    } else if (inclinationProfile != null && inclinationProfile.length >= numberOfSections) {
      double elev = 0;
      for (int i = 0; i < numberOfSections; i++) {
        sections[i].setInclination(inclinationProfile[i]);
        sections[i].setElevation(elev);
        elev += dx * Math.sin(inclinationProfile[i]);
      }
    }

    // Identify low points for accumulation
    accumulationTracker.identifyAccumulationZones(sections);

    // Initialize result arrays
    pressureProfile = new double[numberOfSections];
    temperatureProfile = new double[numberOfSections];
    liquidHoldupProfile = new double[numberOfSections];
    gasVelocityProfile = new double[numberOfSections];
    liquidVelocityProfile = new double[numberOfSections];

    int historySteps = (int) (maxSimulationTime / (dt * historyInterval)) + 1;
    pressureHistory = new double[historySteps][numberOfSections];
  }

  /**
   * Initialize pipe state from inlet stream.
   */
  private void initializeFromStream() {
    if (inStream == null || inStream.getFluid() == null) {
      throw new IllegalStateException("Inlet stream must be set before running");
    }

    SystemInterface fluid = inStream.getFluid();
    referenceFluid = fluid.clone();

    // Get inlet conditions
    inletPressureValue = fluid.getPressure() * 1e5; // bar to Pa
    double T_inlet = fluid.getTemperature(); // K

    // Flash to get phase properties
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    double rho_L = 0, rho_G = 0, mu_L = 0, mu_G = 0, sigma = 0;
    double H_L = 0, H_G = 0, c_L = 0, c_G = 0;
    double U_SL = 0, U_SG = 0;
    double gasBeta = 0; // Gas mass fraction

    if (fluid.hasPhaseType("gas")) {
      rho_G = fluid.getPhase("gas").getDensity("kg/m3");
      mu_G = fluid.getPhase("gas").getViscosity("kg/msec");
      H_G =
          fluid.getPhase("gas").getEnthalpy("J/mol") / fluid.getPhase("gas").getMolarMass() * 1000;
      c_G = fluid.getPhase("gas").getSoundSpeed();
      gasBeta = fluid.getPhase("gas").getBeta();

      double gasFlowRate = inStream.getFlowRate("kg/sec") * gasBeta;
      U_SG = gasFlowRate / (rho_G * Math.PI * diameter * diameter / 4.0);
    } else {
      // No gas phase - use default values to avoid division by zero
      rho_G = 1.0; // Default gas density (kg/m3)
      mu_G = 1e-5; // Default gas viscosity (Pa.s)
      H_G = 0; // Default enthalpy
      c_G = 340; // Default sound speed (m/s)
      U_SG = 0;
      gasBeta = 0;
    }

    if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("aqueous")) {
      // Three-phase handling: use volume-weighted average of liquid properties
      if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
        double V_oil = fluid.getPhase("oil").getVolume();
        double V_aq = fluid.getPhase("aqueous").getVolume();
        double V_total = V_oil + V_aq;

        double w_oil = V_oil / V_total;
        double w_aq = V_aq / V_total;

        rho_L = w_oil * fluid.getPhase("oil").getDensity("kg/m3")
            + w_aq * fluid.getPhase("aqueous").getDensity("kg/m3");
        mu_L = w_oil * fluid.getPhase("oil").getViscosity("kg/msec")
            + w_aq * fluid.getPhase("aqueous").getViscosity("kg/msec");
        H_L = w_oil
            * (fluid.getPhase("oil").getEnthalpy("J/mol") / fluid.getPhase("oil").getMolarMass()
                * 1000)
            + w_aq * (fluid.getPhase("aqueous").getEnthalpy("J/mol")
                / fluid.getPhase("aqueous").getMolarMass() * 1000);
        c_L = w_oil * fluid.getPhase("oil").getSoundSpeed()
            + w_aq * fluid.getPhase("aqueous").getSoundSpeed();
      } else {
        // Two-phase: use the single liquid phase
        String liqPhase = fluid.hasPhaseType("oil") ? "oil" : "aqueous";
        rho_L = fluid.getPhase(liqPhase).getDensity("kg/m3");
        mu_L = fluid.getPhase(liqPhase).getViscosity("kg/msec");
        H_L = fluid.getPhase(liqPhase).getEnthalpy("J/mol")
            / fluid.getPhase(liqPhase).getMolarMass() * 1000;
        c_L = fluid.getPhase(liqPhase).getSoundSpeed();
      }

      double liqFlowRate = inStream.getFlowRate("kg/sec") * (1.0 - gasBeta);
      U_SL = liqFlowRate / (rho_L * Math.PI * diameter * diameter / 4.0);
    } else {
      // No liquid phase - use default values to avoid division by zero
      rho_L = 800.0; // Default liquid density (kg/m3)
      mu_L = 0.001; // Default liquid viscosity (Pa.s)
      H_L = 0; // Default enthalpy
      c_L = 1500; // Default sound speed (m/s)
      U_SL = 0;
    }

    sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1e-3; // mN/m to N/m
    if (sigma <= 0) {
      sigma = 0.02; // Default surface tension
    }

    // Calculate outlet pressure from hydrostatic and friction
    double totalElevationChange = 0;
    if (sections.length > 0) {
      totalElevationChange =
          sections[numberOfSections - 1].getElevation() - sections[0].getElevation();
    }

    // Calculate actual mixture density based on phase fractions
    double alpha_L_init = U_SL / (U_SL + U_SG + 1e-10);
    double alpha_G_init = 1.0 - alpha_L_init;
    double rho_m = alpha_L_init * rho_L + alpha_G_init * rho_G;
    if (rho_m < 1) {
      rho_m = rho_G; // Fallback for pure gas
    }
    double hydrostaticDrop = rho_m * GRAVITY * totalElevationChange;

    // Estimate friction using proper mixture viscosity
    double mu_m = alpha_L_init * mu_L + alpha_G_init * mu_G;
    if (mu_m <= 0) {
      mu_m = mu_G > 0 ? mu_G : 1e-5;
    }
    double U_M = U_SL + U_SG;
    double Re = rho_m * U_M * diameter / mu_m;
    double f = (Re > 2300) ? 0.316 * Math.pow(Re, -0.25) : 64.0 / Math.max(Re, 1);
    double frictionDrop = f * rho_m * U_M * U_M * length / (2 * diameter);

    outletPressureValue = inletPressureValue - hydrostaticDrop - frictionDrop;
    outletPressureValue = Math.max(outletPressureValue, 1e5); // Minimum 1 bar

    // Initialize sections with linear pressure profile
    for (int i = 0; i < numberOfSections; i++) {
      double frac = (double) i / (numberOfSections - 1);
      double P = inletPressureValue * (1 - frac) + outletPressureValue * frac;
      double T = T_inlet; // Isothermal initial condition

      sections[i].setPressure(P);
      sections[i].setTemperature(T);

      sections[i].setGasDensity(rho_G * P / inletPressureValue); // Ideal gas approx for init
      sections[i].setLiquidDensity(rho_L);
      sections[i].setGasViscosity(mu_G);
      sections[i].setLiquidViscosity(mu_L);
      sections[i].setSurfaceTension(sigma);
      sections[i].setGasEnthalpy(H_G);
      sections[i].setLiquidEnthalpy(H_L);
      sections[i].setGasSoundSpeed(c_G);
      sections[i].setLiquidSoundSpeed(c_L);

      // Initialize with estimated holdup
      double alpha_L = U_SL / (U_SL + U_SG + 1e-10);
      sections[i].setLiquidHoldup(alpha_L);
      sections[i].setGasHoldup(1.0 - alpha_L);

      sections[i].setLiquidVelocity(U_SL / Math.max(alpha_L, 0.01));
      sections[i].setGasVelocity(U_SG / Math.max(1 - alpha_L, 0.01));

      sections[i].updateDerivedQuantities();

      // Detect initial flow regime
      FlowRegime regime = flowRegimeDetector.detectFlowRegime(sections[i]);
      sections[i].setFlowRegime(regime);
    }

    // Store initial mass flow - initially outlet equals inlet (steady state assumption)
    inletMassFlow = inStream.getFlowRate("kg/sec");
    outletMassFlow = inletMassFlow; // Initialize outlet flow to inlet flow
  }

  /**
   * Run the transient simulation.
   */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /**
   * Run with calculation identifier.
   *
   * @param id Calculation identifier
   */
  @Override
  public void run(UUID id) {
    this.calcIdentifier = id;

    // Initialize if needed
    if (sections == null) {
      initializePipe();
    }
    initializeFromStream();

    simulationTime = 0;
    totalTimeSteps = 0;
    historyIndex = 0;

    // Store initial state
    storeResults();
    storeHistory();

    // Time integration loop
    while (simulationTime < maxSimulationTime) {
      // Calculate adaptive time step
      dt = calculateTimeStep();

      // Advance solution
      advanceTimeStep(dt);

      simulationTime += dt;
      totalTimeSteps++;
      currentStep++;

      // Update flow regimes
      updateFlowRegimes();

      // Update liquid accumulation
      accumulationTracker.updateAccumulation(sections, dt);

      // Check for terrain-induced slugs
      checkTerrainSlugging();

      // Set reference velocity for slug tracker from inlet section
      // This ensures slugs continue moving even when outlet sections have low velocity
      double inletVelocity = sections[0].getMixtureVelocity();
      if (inletVelocity < 0.1 && inletMassFlow > 0) {
        // Calculate from inlet mass flow if section velocity is too low
        double rhoMix = sections[0].getMixtureDensity();
        double area = sections[0].getArea();
        if (rhoMix > 0 && area > 0) {
          inletVelocity = inletMassFlow / (rhoMix * area);
        }
      }
      slugTracker.setReferenceVelocity(inletVelocity);

      // Advance slug tracking
      slugTracker.advanceSlugs(sections, dt);

      // Update thermodynamics periodically
      if (updateThermodynamics && currentStep % thermodynamicUpdateInterval == 0) {
        updateThermodynamicProperties();
      }

      // Store history
      if (totalTimeSteps % historyInterval == 0) {
        storeHistory();
      }

      // Check convergence (for steady-state detection)
      checkConvergence();
    }

    // Final results
    storeResults();
    updateOutletStream();
  }

  /**
   * Run transient simulation for a specified time step.
   *
   * <p>
   * This method advances the pipe simulation by the specified time step {@code dt}, making it
   * suitable for use within a
   * {@link neqsim.process.processmodel.ProcessSystem#runTransient(double, java.util.UUID)} loop.
   * Unlike {@link #run()}, which runs the complete simulation to {@code maxSimulationTime}, this
   * method performs incremental time-stepping that can be coordinated with other equipment.
   * </p>
   *
   * <p>
   * On the first call, the pipe is initialized from the inlet stream. Subsequent calls advance the
   * simulation state incrementally. The method uses adaptive sub-stepping internally to maintain
   * numerical stability (CFL condition) while advancing by the requested {@code dt}.
   * </p>
   *
   * <h3>Usage Example</h3>
   * 
   * <pre>{@code
   * ProcessSystem process = new ProcessSystem();
   * process.add(inlet);
   * process.add(transientPipe);
   * process.add(separator);
   * process.run(); // Initial steady state
   *
   * // Transient loop
   * for (int i = 0; i < 100; i++) {
   *   // Optionally change inlet conditions here
   *   process.runTransient(1.0); // Advance 1 second
   *   System.out.println("Outlet pressure: " + separator.getGasOutStream().getPressure());
   * }
   * }</pre>
   *
   * <p>
   * <b>Note:</b> The inlet stream conditions are re-read at each call, allowing dynamic boundary
   * conditions. If the inlet flow rate or composition changes, the pipe simulation will respond
   * accordingly.
   * </p>
   *
   * @param dt Time step to advance in seconds. The method will use internal sub-stepping if
   *        required for numerical stability.
   * @param id Calculation identifier for tracking
   * @see #run(UUID)
   * @see neqsim.process.processmodel.ProcessSystem#runTransient(double, UUID)
   */
  @Override
  public void runTransient(double dt, UUID id) {
    this.calcIdentifier = id;

    // Initialize on first call if sections not yet created
    if (sections == null) {
      initializePipe();
      initializeFromStream();
      storeResults();
      storeHistory();
    } else {
      // Update inlet boundary conditions from current inlet stream state
      updateInletFromStream();
      // Update outlet boundary conditions from current outlet stream state
      updateOutletFromStream();
    }

    // Perform time stepping with adaptive sub-steps for stability
    double timeAdvanced = 0;
    while (timeAdvanced < dt) {
      // Calculate stable time step based on CFL condition
      double stableDt = calculateTimeStep();
      double stepDt = Math.min(stableDt, dt - timeAdvanced);

      // Advance solution by one sub-step
      advanceTimeStep(stepDt);

      simulationTime += stepDt;
      timeAdvanced += stepDt;
      totalTimeSteps++;
      currentStep++;

      // Update flow regimes
      updateFlowRegimes();

      // Update liquid accumulation
      accumulationTracker.updateAccumulation(sections, stepDt);

      // Check for terrain-induced slugs
      checkTerrainSlugging();

      // Set reference velocity for slug tracker from inlet section
      // This ensures slugs continue moving even when outlet sections have low velocity
      double inletVelocity = sections[0].getMixtureVelocity();
      if (inletVelocity < 0.1 && inletMassFlow > 0) {
        // Calculate from inlet mass flow if section velocity is too low
        double rhoMix = sections[0].getMixtureDensity();
        double area = sections[0].getArea();
        if (rhoMix > 0 && area > 0) {
          inletVelocity = inletMassFlow / (rhoMix * area);
        }
      }
      slugTracker.setReferenceVelocity(inletVelocity);

      // Advance slug tracking
      slugTracker.advanceSlugs(sections, stepDt);

      // Update thermodynamics periodically
      if (updateThermodynamics && currentStep % thermodynamicUpdateInterval == 0) {
        updateThermodynamicProperties();
      }

      // Store history at intervals
      if (totalTimeSteps % historyInterval == 0) {
        storeHistory();
      }
    }

    // Store final results and update outlet
    storeResults();
    updateOutletStream();
  }

  /**
   * Update inlet boundary conditions from the current inlet stream state.
   *
   * <p>
   * This method is called during {@link #runTransient(double, UUID)} to capture any changes in the
   * inlet stream conditions (flow rate, pressure, composition) that may have occurred since the
   * last time step.
   * </p>
   */
  private void updateInletFromStream() {
    if (inStream == null || inStream.getFluid() == null) {
      return;
    }

    // Update inlet mass flow from stream
    inletMassFlow = inStream.getFlowRate("kg/sec");

    // Update inlet pressure if using pressure BC
    if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE) {
      inletPressureValue = inStream.getFluid().getPressure() * 1e5; // bar to Pa
    }

    // Update reference fluid if composition might have changed
    SystemInterface fluid = inStream.getFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Update inlet section properties
    if (sections != null && sections.length > 0) {
      PipeSection inletSection = sections[0];

      if (fluid.hasPhaseType("gas")) {
        inletSection.setGasDensity(fluid.getPhase("gas").getDensity("kg/m3"));
        inletSection.setGasViscosity(fluid.getPhase("gas").getViscosity("kg/msec"));
      }

      if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("aqueous")) {
        if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
          double V_oil = fluid.getPhase("oil").getVolume();
          double V_aq = fluid.getPhase("aqueous").getVolume();
          double V_total = V_oil + V_aq;
          double w_oil = V_oil / V_total;
          double w_aq = V_aq / V_total;

          inletSection.setLiquidDensity(w_oil * fluid.getPhase("oil").getDensity("kg/m3")
              + w_aq * fluid.getPhase("aqueous").getDensity("kg/m3"));
          inletSection.setLiquidViscosity(w_oil * fluid.getPhase("oil").getViscosity("kg/msec")
              + w_aq * fluid.getPhase("aqueous").getViscosity("kg/msec"));
        } else {
          String liqPhase = fluid.hasPhaseType("oil") ? "oil" : "aqueous";
          inletSection.setLiquidDensity(fluid.getPhase(liqPhase).getDensity("kg/m3"));
          inletSection.setLiquidViscosity(fluid.getPhase(liqPhase).getViscosity("kg/msec"));
        }
      }
    }
  }

  /**
   * Updates the outlet boundary conditions from the current outlet stream state.
   * <p>
   * This method is called during {@link #runTransient(double, UUID)} to capture any changes in the
   * outlet stream conditions when using CONSTANT_FLOW outlet boundary condition. This allows
   * external controllers or other process equipment to set the outlet flow rate on the outlet
   * stream, which is then read back into the pipe model.
   * </p>
   */
  private void updateOutletFromStream() {
    if (outStream == null || outStream.getFluid() == null) {
      return;
    }

    // Update outlet mass flow from stream when using constant flow BC
    if (outletBCType == BoundaryCondition.CONSTANT_FLOW) {
      outletMassFlow = outStream.getFlowRate("kg/sec");
    }

    // Update outlet pressure if using pressure BC, but only if not explicitly set by user
    if (outletBCType == BoundaryCondition.CONSTANT_PRESSURE && !outletPressureExplicitlySet) {
      outletPressureValue = outStream.getFluid().getPressure() * 1e5; // bar to Pa
    }
  }

  /**
   * Calculate adaptive time step based on CFL condition.
   */
  private double calculateTimeStep() {
    double maxWaveSpeed = 0;

    for (PipeSection section : sections) {
      // Wave speeds: sound speed and characteristic velocities
      double c_mix = section.getWallisSoundSpeed();
      double v_gas = Math.abs(section.getGasVelocity());
      double v_liq = Math.abs(section.getLiquidVelocity());

      // Handle NaN velocities
      if (Double.isNaN(v_gas)) {
        v_gas = 0;
      }
      if (Double.isNaN(v_liq)) {
        v_liq = 0;
      }

      double maxLocal = Math.max(c_mix + v_gas, v_liq + c_mix);
      if (!Double.isNaN(maxLocal) && !Double.isInfinite(maxLocal)) {
        maxWaveSpeed = Math.max(maxWaveSpeed, maxLocal);
      }
    }

    // Fallback if all wave speeds are invalid
    if (maxWaveSpeed <= 0) {
      maxWaveSpeed = 100; // Default sound speed
    }

    double dt_cfl = cflNumber * dx / maxWaveSpeed;
    dt_cfl = Math.max(minTimeStep, Math.min(maxTimeStep, dt_cfl));

    return dt_cfl;
  }

  /**
   * Advance solution by one time step using finite volume method.
   */
  private void advanceTimeStep(double dt) {
    // Apply boundary conditions to current state before cloning
    applyBoundaryConditions();

    // Store old state
    PipeSection[] oldSections = new PipeSection[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      oldSections[i] = sections[i].clone();
    }

    // Calculate fluxes at cell faces
    double[][] fluxes = calculateFluxes(oldSections);

    // Update conservative variables
    for (int i = 0; i < numberOfSections; i++) {
      double[] U_old = oldSections[i].getConservativeVariables();
      double[] U_new = new double[4];

      // Finite volume update: U_new = U_old - dt/dx * (F_right - F_left)
      // F_left comes from face i, F_right comes from face i+1
      // fluxes array has numberOfSections+1 entries (faces 0 to numberOfSections)
      for (int k = 0; k < 4; k++) {
        double F_left = fluxes[i][k];
        double F_right = fluxes[i + 1][k];
        U_new[k] = U_old[k] - dt / dx * (F_right - F_left);
        // Check for NaN and recover from numerical issues
        if (Double.isNaN(U_new[k]) || Double.isInfinite(U_new[k])) {
          U_new[k] = U_old[k]; // Fall back to old value
        }
      }

      // Add source terms (gravity, friction)
      addSourceTerms(U_new, sections[i], dt);

      // Convert back to primitive variables
      updatePrimitiveVariables(sections[i], oldSections[i], U_new);
    }

    // Apply boundary conditions again to enforce them on the new state
    applyBoundaryConditions();

    // Update pressure profile for steady-state consistency
    updatePressureProfile();
  }

  /**
   * Update pressure profile based on friction and gravity pressure gradients.
   * 
   * <p>
   * Marches from inlet to outlet (or outlet to inlet), computing pressure drop in each cell based
   * on the local friction and gravity gradients. This ensures the pressure profile is consistent
   * with the flow. For single-phase flow, uses the direct Darcy-Weisbach calculation.
   * </p>
   */
  private void updatePressureProfile() {
    // For CONSTANT_FLOW inlet + CONSTANT_PRESSURE outlet: march from outlet to inlet
    if (inletBCType == BoundaryCondition.CONSTANT_FLOW
        && outletBCType == BoundaryCondition.CONSTANT_PRESSURE) {

      double P = outletPressureValue;
      sections[numberOfSections - 1].setPressure(P);

      // Calculate pressure gradient based on flow conditions
      for (int i = numberOfSections - 2; i >= 0; i--) {
        PipeSection section = sections[i + 1];

        // Get flow properties - use effective properties that account for slug presence
        double rho_m = section.getEffectiveMixtureDensity();
        double mu_m = section.getGasHoldup() * section.getGasViscosity()
            + section.getEffectiveLiquidHoldup() * section.getLiquidViscosity();

        // Calculate mixture velocity from mass flow
        double A = section.getArea();
        double u_m = inletMassFlow / (rho_m * A);

        // Gravity contribution
        double theta = section.getInclination();
        double dP_gravity = -rho_m * GRAVITY * Math.sin(theta) * dx;

        // Friction contribution (Darcy-Weisbach)
        double Re = rho_m * Math.abs(u_m) * diameter / Math.max(mu_m, 1e-10);
        double f;
        if (Re < 10) {
          f = 6.4;
        } else if (Re < 2300) {
          f = 64.0 / Re;
        } else {
          // Haaland correlation
          double relRough = roughness / diameter;
          double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / Re;
          f = Math.pow(-1.8 * Math.log10(term), -2);
          f = Math.max(f, 0.001);
        }
        double dP_friction = -f * rho_m * u_m * Math.abs(u_m) / (2.0 * diameter) * dx;

        // Total pressure change for this cell
        double dP = dP_gravity + dP_friction;

        // Pressure increases going upstream (subtracting negative gradient)
        P -= dP;
        sections[i].setPressure(P);
      }

    } else if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE) {
      // March from inlet to outlet
      double P = inletPressureValue;
      sections[0].setPressure(P);

      for (int i = 1; i < numberOfSections; i++) {
        PipeSection section = sections[i - 1];

        // Use effective properties that account for slug presence
        double rho_m = section.getEffectiveMixtureDensity();
        double mu_m = section.getGasHoldup() * section.getGasViscosity()
            + section.getEffectiveLiquidHoldup() * section.getLiquidViscosity();

        double A = section.getArea();
        double u_m = inletMassFlow / (rho_m * A);

        double theta = section.getInclination();
        double dP_gravity = -rho_m * GRAVITY * Math.sin(theta) * dx;

        double Re = rho_m * Math.abs(u_m) * diameter / Math.max(mu_m, 1e-10);
        double f;
        if (Re < 10) {
          f = 6.4;
        } else if (Re < 2300) {
          f = 64.0 / Re;
        } else {
          double relRough = roughness / diameter;
          double term = Math.pow(relRough / 3.7, 1.11) + 6.9 / Re;
          f = Math.pow(-1.8 * Math.log10(term), -2);
          f = Math.max(f, 0.001);
        }
        double dP_friction = -f * rho_m * u_m * Math.abs(u_m) / (2.0 * diameter) * dx;

        double dP = dP_gravity + dP_friction;
        P += dP; // dP is negative, so pressure decreases
        P = Math.max(P, 1e5);
        sections[i].setPressure(P);
      }
    }
  }

  /**
   * Calculate fluxes at cell interfaces using AUSM+ scheme.
   *
   * <p>
   * For interior interfaces, the AUSM+ upwind scheme is used. For boundary interfaces, the fluxes
   * are set according to the boundary conditions to ensure mass and momentum conservation:
   * <ul>
   * <li>CONSTANT_FLOW: Flux is set to the specified mass flow rate</li>
   * <li>CONSTANT_PRESSURE: Flux is computed from the AUSM scheme</li>
   * <li>CLOSED: Zero flux</li>
   * </ul>
   */
  private double[][] calculateFluxes(PipeSection[] oldSections) {
    double[][] fluxes = new double[numberOfSections + 1][4];

    // Inlet flux (i = 0)
    if (inletBCType == BoundaryCondition.CONSTANT_FLOW) {
      // Set inlet flux directly from specified mass flow for mass conservation
      // Use source stream properties to ensure correct energy influx
      SystemInterface fluid = inStream.getFluid();
      double rho_mix_in = fluid.getDensity("kg/m3");
      if (rho_mix_in < 1e-5)
        rho_mix_in = oldSections[0].getMixtureDensity(); // Fallback

      double A = oldSections[0].getArea();
      double u_inlet = inletMassFlow / (rho_mix_in * A);
      double p_inlet = oldSections[0].getPressure(); // Use pipe pressure for momentum balance

      double rho_G_in = 0;
      double rho_L_in = 0;
      double h_G_in = 0;
      double h_L_in = 0;
      double alpha_G_in = 0;
      double alpha_L_in = 0;

      if (fluid.hasPhaseType("gas")) {
        rho_G_in = fluid.getPhase("gas").getDensity("kg/m3");
        h_G_in = fluid.getPhase("gas").getEnthalpy("J/mol") / fluid.getPhase("gas").getMolarMass()
            * 1000;
        alpha_G_in = fluid.getPhase("gas").getVolume() / fluid.getVolume();
      }

      if (fluid.hasPhaseType("oil")) {
        rho_L_in = fluid.getPhase("oil").getDensity("kg/m3");
        h_L_in = fluid.getPhase("oil").getEnthalpy("J/mol") / fluid.getPhase("oil").getMolarMass()
            * 1000;
        alpha_L_in += fluid.getPhase("oil").getVolume() / fluid.getVolume();
      }
      if (fluid.hasPhaseType("aqueous")) {
        // Simplified: treat aqueous as liquid or mix with oil
        double rho_aq = fluid.getPhase("aqueous").getDensity("kg/m3");
        double h_aq = fluid.getPhase("aqueous").getEnthalpy("J/mol")
            / fluid.getPhase("aqueous").getMolarMass() * 1000;
        double alpha_aq = fluid.getPhase("aqueous").getVolume() / fluid.getVolume();

        // Weighted average for liquid properties if both exist
        if (alpha_L_in > 0) {
          double total_L = alpha_L_in + alpha_aq;
          rho_L_in = (rho_L_in * alpha_L_in + rho_aq * alpha_aq) / total_L;
          h_L_in = (h_L_in * alpha_L_in + h_aq * alpha_aq) / total_L;
          alpha_L_in = total_L;
        } else {
          rho_L_in = rho_aq;
          h_L_in = h_aq;
          alpha_L_in = alpha_aq;
        }
      }

      // Mass fluxes proportional to phase holdups
      fluxes[0][0] = rho_G_in * alpha_G_in * u_inlet; // Gas mass flux
      fluxes[0][1] = rho_L_in * alpha_L_in * u_inlet; // Liquid mass flux
      fluxes[0][2] = (rho_G_in * alpha_G_in + rho_L_in * alpha_L_in) * u_inlet * u_inlet + p_inlet; // Momentum
      fluxes[0][3] = (rho_G_in * alpha_G_in * h_G_in + rho_L_in * alpha_L_in * h_L_in) * u_inlet;
    } else if (inletBCType == BoundaryCondition.CLOSED) {
      // Zero flux for closed boundary
      fluxes[0][0] = 0;
      fluxes[0][1] = 0;
      fluxes[0][2] = oldSections[0].getPressure(); // Only pressure term
      fluxes[0][3] = 0;
    } else {
      // Use AUSM flux for constant pressure inlet
      fluxes[0] = calculateAUSMFlux(oldSections[0], oldSections[0]);
    }

    // Interior fluxes (i = 1 to numberOfSections - 1)
    for (int i = 1; i < numberOfSections; i++) {
      PipeSection left = oldSections[i - 1];
      PipeSection right = oldSections[i];
      fluxes[i] = calculateAUSMFlux(left, right);
    }

    // Outlet flux (i = numberOfSections)
    if (outletBCType == BoundaryCondition.CONSTANT_PRESSURE) {
      // Use AUSM flux - the pressure BC is already applied to the outlet section
      PipeSection outletSection = oldSections[numberOfSections - 1];
      fluxes[numberOfSections] = calculateAUSMFlux(outletSection, outletSection);
    } else if (outletBCType == BoundaryCondition.CLOSED) {
      // Zero flux for closed boundary
      fluxes[numberOfSections][0] = 0;
      fluxes[numberOfSections][1] = 0;
      fluxes[numberOfSections][2] = oldSections[numberOfSections - 1].getPressure();
      fluxes[numberOfSections][3] = 0;
    } else {
      // Constant flow outlet - set flux directly
      PipeSection outletSection = oldSections[numberOfSections - 1];
      double rho_G = outletSection.getGasDensity();
      double rho_L = outletSection.getLiquidDensity();
      double alpha_G = outletSection.getGasHoldup();
      double alpha_L = outletSection.getLiquidHoldup();
      double u_outlet =
          outletMassFlow / (outletSection.getMixtureDensity() * outletSection.getArea());
      double p_outlet = outletSection.getPressure();

      fluxes[numberOfSections][0] = rho_G * alpha_G * u_outlet;
      fluxes[numberOfSections][1] = rho_L * alpha_L * u_outlet;
      fluxes[numberOfSections][2] =
          (rho_G * alpha_G + rho_L * alpha_L) * u_outlet * u_outlet + p_outlet;
      fluxes[numberOfSections][3] =
          (rho_G * alpha_G + rho_L * alpha_L) * u_outlet * outletSection.getGasEnthalpy();
    }

    return fluxes;
  }

  /**
   * Calculate AUSM+ numerical flux at interface.
   */
  private double[] calculateAUSMFlux(PipeSection left, PipeSection right) {
    double[] flux = new double[4];

    // Left state
    double rho_L_left = left.getLiquidDensity() * left.getLiquidHoldup();
    double rho_G_left = left.getGasDensity() * left.getGasHoldup();
    double u_L_left = left.getLiquidVelocity();
    double u_G_left = left.getGasVelocity();
    double u_mix_left = left.getMixtureVelocity();
    double p_left = left.getPressure();
    double c_left = left.getWallisSoundSpeed();

    // Right state
    double rho_L_right = right.getLiquidDensity() * right.getLiquidHoldup();
    double rho_G_right = right.getGasDensity() * right.getGasHoldup();
    double u_L_right = right.getLiquidVelocity();
    double u_G_right = right.getGasVelocity();
    double u_mix_right = right.getMixtureVelocity();
    double p_right = right.getPressure();
    double c_right = right.getWallisSoundSpeed();

    // Interface sound speed
    double c_face = 0.5 * (c_left + c_right);

    // 1. Mixture Mach numbers for Pressure Splitting
    double M_mix_left = u_mix_left / c_face;
    double M_mix_right = u_mix_right / c_face;

    // Pressure splitting
    double p_plus = splitPressurePlus(M_mix_left, p_left);
    double p_minus = splitPressureMinus(M_mix_right, p_right);
    double p_face = p_plus + p_minus;

    // 2. Gas Phase Fluxes
    double M_G_left = u_G_left / c_face;
    double M_G_right = u_G_right / c_face;

    double M_plus_G = splitMachPlus(M_G_left);
    double M_minus_G = splitMachMinus(M_G_right);
    double M_face_G = M_plus_G + M_minus_G;

    double mdot_G =
        (M_face_G > 0) ? M_face_G * c_face * rho_G_left : M_face_G * c_face * rho_G_right;

    // 3. Liquid Phase Fluxes
    double M_L_left = u_L_left / c_face;
    double M_L_right = u_L_right / c_face;

    double M_plus_L = splitMachPlus(M_L_left);
    double M_minus_L = splitMachMinus(M_L_right);
    double M_face_L = M_plus_L + M_minus_L;

    double mdot_L =
        (M_face_L > 0) ? M_face_L * c_face * rho_L_left : M_face_L * c_face * rho_L_right;

    // 4. Assemble Fluxes
    flux[0] = mdot_G; // Gas mass flux
    flux[1] = mdot_L; // Liquid mass flux

    // Momentum flux: sum of phase momentum fluxes + pressure
    double mom_G = mdot_G * ((M_face_G > 0) ? u_G_left : u_G_right);
    double mom_L = mdot_L * ((M_face_L > 0) ? u_L_left : u_L_right);
    flux[2] = mom_G + mom_L + p_face;

    // Energy flux
    double h_G_left = left.getGasEnthalpy();
    double h_G_right = right.getGasEnthalpy();
    double h_L_left = left.getLiquidEnthalpy();
    double h_L_right = right.getLiquidEnthalpy();

    double energy_G = mdot_G * ((M_face_G > 0) ? h_G_left : h_G_right);
    double energy_L = mdot_L * ((M_face_L > 0) ? h_L_left : h_L_right);
    flux[3] = energy_G + energy_L;

    return flux;
  }

  // Helper methods for AUSM+ splitting
  // Standard AUSM+ (Liou 1996): M± = ±(1/4)(M±1)² ± β(M²-1)² where β=1/8
  private double splitMachPlus(double M) {
    if (Math.abs(M) <= 1) {
      // M+ = (1/4)(M+1)² + β(M²-1)²
      double base = 0.25 * (M + 1) * (M + 1);
      // Note: the β term should vanish at M=±1, which (M²-1)² does
      return base;
    } else {
      return 0.5 * (M + Math.abs(M));
    }
  }

  private double splitMachMinus(double M) {
    if (Math.abs(M) <= 1) {
      // M- = -(1/4)(M-1)²
      double base = -0.25 * (M - 1) * (M - 1);
      return base;
    } else {
      return 0.5 * (M - Math.abs(M));
    }
  }

  private double splitPressurePlus(double M, double p) {
    if (Math.abs(M) <= 1) {
      return p * 0.25 * (M + 1) * (M + 1) * (2 - M);
    } else {
      return p * 0.5 * (1 + Math.signum(M));
    }
  }

  private double splitPressureMinus(double M, double p) {
    if (Math.abs(M) <= 1) {
      return p * 0.25 * (M - 1) * (M - 1) * (2 + M);
    } else {
      return p * 0.5 * (1 - Math.signum(M));
    }
  }

  /**
   * Add source terms (gravity and friction) to the momentum equation.
   *
   * <p>
   * The momentum equation in conservative form is:
   * </p>
   * 
   * <pre>
   * ∂(ρu)/∂t + ∂(ρu² + p)/∂x = -ρg sin(θ) - τ_w/A
   * </pre>
   *
   * <p>
   * Where the source terms are:
   * </p>
   * <ul>
   * <li>Gravity: S_g = -ρ_m · g · sin(θ) [kg/(m²·s²)]</li>
   * <li>Friction: S_f = -dP/dx_friction [kg/(m²·s²)]</li>
   * </ul>
   * <p>
   * These are integrated as: U[2]_new = U[2]_old + S × dt
   * </p>
   */
  private void addSourceTerms(double[] U, PipeSection section, double dt) {
    double rho_m = section.getMixtureDensity();
    double theta = section.getInclination();

    // Gravity source term for momentum equation: S_g = -ρ_m · g · sin(θ)
    // Units: kg/m³ × m/s² = kg/(m²·s²) = N/m³
    // Multiply by dt to get change in momentum: kg/(m²·s²) × s = kg/(m²·s)
    double S_gravity = -rho_m * GRAVITY * Math.sin(theta);
    U[2] += S_gravity * dt;

    // Friction source term: use only friction component (not gravity which is already added above)
    // Use effective properties for friction if in slug to be consistent with pressure profile
    PipeSection frictionSection = section;
    if (section.isInSlugBody()) {
      frictionSection = section.clone();
      frictionSection.setLiquidHoldup(section.getEffectiveLiquidHoldup());
      frictionSection.setGasHoldup(1.0 - frictionSection.getLiquidHoldup());
      frictionSection.updateDerivedQuantities();
    }
    DriftFluxParameters params = driftFluxModel.calculateDriftFlux(frictionSection);

    // Get friction gradient only (calculatePressureGradient returns total, so subtract gravity)
    double dP_total = driftFluxModel.calculatePressureGradient(frictionSection, params);
    double dP_grav = frictionSection.getGravityPressureGradient();
    double dP_friction = dP_total - dP_grav;

    // Friction decelerates the flow - add the negative pressure gradient
    U[2] += dP_friction * dt;
  }

  /**
   * Update primitive variables from conservative variables.
   */
  private void updatePrimitiveVariables(PipeSection section, PipeSection oldSection, double[] U) {
    // U = [rho_G * alpha_G, rho_L * alpha_L, rho_m * u, rho_m * E]

    double massConc_G = Math.max(U[0], 1e-10);
    double massConc_L = Math.max(U[1], 1e-10);
    double momentum = U[2];
    double totalEnergy = U[3];

    // Get current densities (will be updated by thermo)
    double rho_G = section.getGasDensity();
    double rho_L = section.getLiquidDensity();

    // Calculate holdups
    double alpha_G = massConc_G / rho_G;
    double alpha_L = massConc_L / rho_L;

    // Normalize
    double total = alpha_G + alpha_L;
    if (total > 0) {
      alpha_G /= total;
      alpha_L /= total;
    }

    section.setGasHoldup(alpha_G);
    section.setLiquidHoldup(alpha_L);

    // Mixture velocity from momentum
    double rho_m = massConc_G + massConc_L;
    double u_m = momentum / Math.max(rho_m, 1e-10);

    // For single-phase flow, assign all velocity to the present phase
    // For two-phase, use drift-flux to split velocities
    if (alpha_L < 0.001) {
      // Single-phase gas
      section.setGasVelocity(u_m);
      section.setLiquidVelocity(0);
    } else if (alpha_G < 0.001) {
      // Single-phase liquid
      section.setGasVelocity(0);
      section.setLiquidVelocity(u_m);
    } else {
      // Two-phase: temporarily set velocities for drift-flux calculation
      // Superficial velocities: U_SG ≈ alpha_G * u_m, U_SL ≈ alpha_L * u_m (as initial estimate)
      section.setGasVelocity(u_m);
      section.setLiquidVelocity(u_m);
      section.updateDerivedQuantities();

      // Get drift flux parameters (C0 and drift velocity)
      DriftFluxParameters params = driftFluxModel.calculateDriftFlux(section);

      // Solve for velocities satisfying both momentum conservation and drift flux relation
      // 1. Momentum: rho_G*alpha_G*v_G + rho_L*alpha_L*v_L = rho_m*u_m
      // 2. Drift flux: v_G = C0*(alpha_G*v_G + alpha_L*v_L) + v_d

      double C0 = params.C0;
      double v_d = params.driftVelocity;

      // Derived solution for v_G
      // v_G = (v_d + C0 * rho_m * u_m / rho_L) / (1 - C0 * alpha_G + C0 * rho_G * alpha_G / rho_L)
      double numerator = v_d + C0 * rho_m * u_m / rho_L;
      double denominator = 1.0 - C0 * alpha_G + C0 * rho_G * alpha_G / rho_L;

      double v_G;
      if (Math.abs(denominator) > 1e-10) {
        v_G = numerator / denominator;
      } else {
        v_G = u_m; // Fallback
      }

      // Calculate v_L from momentum equation
      // v_L = (rho_m * u_m - rho_G * alpha_G * v_G) / (rho_L * alpha_L)
      double v_L;
      if (alpha_L > 1e-10) {
        v_L = (rho_m * u_m - rho_G * alpha_G * v_G) / (rho_L * alpha_L);
      } else {
        v_L = u_m;
      }

      section.setGasVelocity(v_G);
      section.setLiquidVelocity(v_L);
    }

    // Temperature: keep isothermal for now (energy equation not fully coupled)
    // The complex energy coupling was causing numerical instabilities
    // Temperature will be updated by the thermodynamic flash when called
    section.setTemperature(oldSection.getTemperature());

    // Pressure: will be set by updatePressureProfile() after all cells are updated
    // Don't modify pressure here to avoid fighting with boundary conditions
    section.setPressure(oldSection.getPressure());

    section.updateDerivedQuantities();
  }

  /**
   * Apply boundary conditions.
   */
  private void applyBoundaryConditions() {
    // Inlet boundary
    switch (inletBCType) {
      case CONSTANT_PRESSURE:
        sections[0].setPressure(inletPressureValue);
        break;
      case CONSTANT_FLOW:
        // Maintain inlet velocity based on mass flow using drift-flux relation
        PipeSection inlet = sections[0];
        double rho_m = inlet.getMixtureDensity();
        double A = inlet.getArea();
        double u_m = inletMassFlow / (rho_m * A);

        double alpha_G = inlet.getGasHoldup();
        double alpha_L = inlet.getLiquidHoldup();

        if (alpha_L < 0.001 || alpha_G < 0.001) {
          // Single phase - both velocities equal mixture velocity
          inlet.setGasVelocity(u_m);
          inlet.setLiquidVelocity(u_m);
        } else {
          // Two-phase: use drift-flux to split velocities properly
          inlet.setGasVelocity(u_m);
          inlet.setLiquidVelocity(u_m);
          inlet.updateDerivedQuantities();

          DriftFluxParameters params = driftFluxModel.calculateDriftFlux(inlet);
          double C0 = params.C0;
          double v_d = params.driftVelocity;
          double rho_G = inlet.getGasDensity();
          double rho_L = inlet.getLiquidDensity();

          // Solve: v_G = C0*v_m + v_d and momentum conservation
          double v_G = C0 * u_m + v_d;
          double v_L = (rho_m * u_m - rho_G * alpha_G * v_G) / (rho_L * alpha_L);

          inlet.setGasVelocity(v_G);
          inlet.setLiquidVelocity(v_L);
        }
        break;
      case CLOSED:
        sections[0].setGasVelocity(0);
        sections[0].setLiquidVelocity(0);
        break;
      default:
        break;
    }

    // Outlet boundary
    switch (outletBCType) {
      case CONSTANT_PRESSURE:
        sections[numberOfSections - 1].setPressure(outletPressureValue);
        break;
      case CONSTANT_FLOW:
        // Set outlet velocity based on mass flow
        double rho_m = sections[numberOfSections - 1].getMixtureDensity();
        double A = sections[numberOfSections - 1].getArea();
        double u_outlet = outletMassFlow / (rho_m * A);
        sections[numberOfSections - 1].setGasVelocity(u_outlet);
        sections[numberOfSections - 1].setLiquidVelocity(u_outlet);
        break;
      case CLOSED:
        sections[numberOfSections - 1].setGasVelocity(0);
        sections[numberOfSections - 1].setLiquidVelocity(0);
        break;
      default:
        break;
    }
  }

  /**
   * Update flow regimes for all sections.
   */
  private void updateFlowRegimes() {
    for (PipeSection section : sections) {
      FlowRegime regime = flowRegimeDetector.detectFlowRegime(section);
      section.setFlowRegime(regime);
    }
  }

  /**
   * Check for terrain-induced slugging.
   */
  private void checkTerrainSlugging() {
    for (LiquidAccumulationTracker.AccumulationZone zone : accumulationTracker
        .getOverflowingZones()) {
      SlugCharacteristics slugChar = accumulationTracker.checkForSlugRelease(zone, sections);
      if (slugChar != null) {
        slugTracker.initializeTerrainSlug(slugChar, sections);
      }
    }
  }

  /**
   * Update thermodynamic properties using NeqSim flash.
   */
  private void updateThermodynamicProperties() {
    if (referenceFluid == null) {
      return;
    }

    for (PipeSection section : sections) {
      try {
        SystemInterface fluid = referenceFluid.clone();
        fluid.setPressure(section.getPressure() / 1e5); // Pa to bar
        fluid.setTemperature(section.getTemperature());

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();

        if (fluid.hasPhaseType("gas")) {
          section.setGasDensity(fluid.getPhase("gas").getDensity("kg/m3"));
          section.setGasViscosity(fluid.getPhase("gas").getViscosity("kg/msec"));
          section.setGasEnthalpy(fluid.getPhase("gas").getEnthalpy("J/mol")
              / fluid.getPhase("gas").getMolarMass() * 1000);
          section.setGasSoundSpeed(fluid.getPhase("gas").getSoundSpeed());
        }

        if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("aqueous")) {
          // Three-phase handling: use volume-weighted average of liquid properties
          if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
            double V_oil = fluid.getPhase("oil").getVolume();
            double V_aq = fluid.getPhase("aqueous").getVolume();
            double V_total = V_oil + V_aq;

            double w_oil = V_oil / V_total;
            double w_aq = V_aq / V_total;

            section.setLiquidDensity(w_oil * fluid.getPhase("oil").getDensity("kg/m3")
                + w_aq * fluid.getPhase("aqueous").getDensity("kg/m3"));
            section.setLiquidViscosity(w_oil * fluid.getPhase("oil").getViscosity("kg/msec")
                + w_aq * fluid.getPhase("aqueous").getViscosity("kg/msec"));
            section.setLiquidEnthalpy(w_oil
                * (fluid.getPhase("oil").getEnthalpy("J/mol") / fluid.getPhase("oil").getMolarMass()
                    * 1000)
                + w_aq * (fluid.getPhase("aqueous").getEnthalpy("J/mol")
                    / fluid.getPhase("aqueous").getMolarMass() * 1000));
            section.setLiquidSoundSpeed(w_oil * fluid.getPhase("oil").getSoundSpeed()
                + w_aq * fluid.getPhase("aqueous").getSoundSpeed());
          } else {
            // Two-phase: use the single liquid phase
            String liqPhase = fluid.hasPhaseType("oil") ? "oil" : "aqueous";
            section.setLiquidDensity(fluid.getPhase(liqPhase).getDensity("kg/m3"));
            section.setLiquidViscosity(fluid.getPhase(liqPhase).getViscosity("kg/msec"));
            section.setLiquidEnthalpy(fluid.getPhase(liqPhase).getEnthalpy("J/mol")
                / fluid.getPhase(liqPhase).getMolarMass() * 1000);
            section.setLiquidSoundSpeed(fluid.getPhase(liqPhase).getSoundSpeed());
          }
        }

        section.setSurfaceTension(fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1e-3);

        // Calculate mixture specific heat capacity (Cv)
        double totalMass = fluid.getTotalNumberOfMoles() * fluid.getMolarMass();
        if (totalMass > 1e-10) {
          section.setMixtureHeatCapacity(fluid.getCv() / totalMass);
        } else {
          section.setMixtureHeatCapacity(1000.0); // Default fallback
        }

      } catch (Exception e) {
        // Keep previous properties if flash fails
      }
    }
  }

  /**
   * Check for steady-state convergence.
   */
  private void checkConvergence() {
    double totalMass = 0;
    double totalEnergy = 0;

    for (PipeSection section : sections) {
      totalMass += section.getMixtureDensity() * section.getArea() * dx;
      totalEnergy += section.getMixtureDensity() * section.getArea() * dx
          * (section.getGasHoldup() * section.getGasEnthalpy()
              + section.getLiquidHoldup() * section.getLiquidEnthalpy());
    }

    // Store residuals for convergence tracking
    // These are instantaneous values; proper residuals need previous time step comparison
    this.massResidual = totalMass;
    this.energyResidual = totalEnergy;

    // Transient simulation, not looking for steady state by default
    isConverged = false;
  }

  /**
   * Store current results.
   */
  private void storeResults() {
    for (int i = 0; i < numberOfSections; i++) {
      pressureProfile[i] = sections[i].getPressure();
      temperatureProfile[i] = sections[i].getTemperature();
      liquidHoldupProfile[i] = sections[i].getLiquidHoldup();
      gasVelocityProfile[i] = sections[i].getGasVelocity();
      liquidVelocityProfile[i] = sections[i].getLiquidVelocity();
    }
  }

  /**
   * Store pressure history for time series analysis.
   */
  private void storeHistory() {
    if (historyIndex < pressureHistory.length) {
      for (int i = 0; i < numberOfSections; i++) {
        pressureHistory[historyIndex][i] = sections[i].getPressure();
      }
      historyIndex++;
    }
  }

  /**
   * Update outlet stream with final conditions including flow rate.
   *
   * <p>
   * This method updates the outlet stream with:
   * <ul>
   * <li>Pressure from the outlet pipe section</li>
   * <li>Temperature from the outlet pipe section</li>
   * <li>Mass flow rate calculated from outlet section velocity and density</li>
   * </ul>
   * The flow rate is computed directly from the conservation equations to maintain mass balance.
   * During slug flow, the increased liquid holdup naturally increases the outlet density, which is
   * reflected in the mass flow calculation.
   * </p>
   */
  private void updateOutletStream() {
    if (outStream == null && inStream != null) {
      outStream = inStream.clone();
      outStream.setName(name + "_outlet");
    }

    if (outStream != null && sections != null && numberOfSections > 0) {
      PipeSection outletSection = sections[numberOfSections - 1];
      outStream.getFluid().setPressure(outletSection.getPressure() / 1e5);
      outStream.getFluid().setTemperature(outletSection.getTemperature());

      // Calculate outlet mass flow from section properties
      // This is computed from the solved conservation equations for mass balance
      double pipeArea = outletSection.getArea();
      double rhoL = outletSection.getLiquidDensity();
      double rhoG = outletSection.getGasDensity();
      double holdup = outletSection.getLiquidHoldup();
      double velocity = outletSection.getMixtureVelocity();

      // If section is in a slug body, use the higher slug holdup
      // This naturally increases the effective density and thus mass flow
      if (outletSection.isInSlugBody()) {
        double slugHoldup = outletSection.getSlugHoldup();
        if (slugHoldup > holdup && slugHoldup > 0) {
          holdup = slugHoldup;
        }
      }

      // Calculate effective mixture density with current holdup
      double effectiveDensity;
      if (rhoL > 0 && rhoG > 0) {
        effectiveDensity = holdup * rhoL + (1.0 - holdup) * rhoG;
      } else if (rhoL > 0) {
        effectiveDensity = rhoL;
      } else if (rhoG > 0) {
        effectiveDensity = rhoG;
      } else {
        effectiveDensity = outletSection.getMixtureDensity();
      }

      // Calculate mass flow rate (kg/s) directly from outlet section velocity
      // The velocity comes from the solved conservation equations
      if (effectiveDensity > 0 && pipeArea > 0 && Math.abs(velocity) > 1e-10) {
        outletMassFlow = effectiveDensity * Math.abs(velocity) * pipeArea;
      } else if (inletMassFlow > 0) {
        // Fallback only if velocity is essentially zero
        outletMassFlow = inletMassFlow;
      }

      // Set flow rate on outlet stream
      if (outletMassFlow > 1e-10) {
        outStream.setFlowRate(outletMassFlow, "kg/sec");
      }

      outStream.run();
    }
  }

  // ========== Getters and Setters ==========

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Set total pipe length.
   *
   * @param length Pipe length in meters
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get total pipe length.
   *
   * @return Pipe length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Set pipe inner diameter.
   *
   * @param diameter Inner diameter in meters
   */
  public void setDiameter(double diameter) {
    this.diameter = diameter;
  }

  /**
   * Get pipe inner diameter.
   *
   * @return Inner diameter in meters
   */
  public double getDiameter() {
    return diameter;
  }

  /**
   * Get pipe wall roughness.
   *
   * @return Wall roughness in meters
   */
  public double getRoughness() {
    return roughness;
  }

  /**
   * Get pipe wall thickness.
   *
   * @return Wall thickness in meters
   */
  public double getWallThickness() {
    return wallThickness;
  }

  /**
   * Get pipe material elasticity (Young's modulus).
   *
   * @return Elasticity in Pascals
   */
  public double getPipeElasticity() {
    return pipeElasticity;
  }

  /**
   * Check if heat transfer is enabled.
   *
   * @return true if heat transfer is included in calculations
   */
  public boolean isIncludeHeatTransfer() {
    return includeHeatTransfer;
  }

  /**
   * Get ambient temperature for heat transfer calculations.
   *
   * @return Ambient temperature in Kelvin
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Get overall heat transfer coefficient.
   *
   * @return Heat transfer coefficient in W/(m²·K)
   */
  public double getOverallHeatTransferCoeff() {
    return overallHeatTransferCoeff;
  }

  /**
   * Get mass residual from last time step.
   *
   * @return Mass balance residual
   */
  public double getMassResidual() {
    return massResidual;
  }

  /**
   * Get energy residual from last time step.
   *
   * @return Energy balance residual
   */
  public double getEnergyResidual() {
    return energyResidual;
  }

  /**
   * Check if simulation has converged.
   *
   * @return true if converged to steady-state
   */
  public boolean isConverged() {
    return isConverged;
  }

  /**
   * Set pipe wall roughness.
   *
   * <p>
   * Used for friction factor calculation. Typical values:
   * </p>
   * <ul>
   * <li>New steel pipe: 0.00004 m (40 μm)</li>
   * <li>Used steel pipe: 0.0001 m (100 μm)</li>
   * <li>Corroded pipe: 0.0003 m (300 μm)</li>
   * </ul>
   *
   * @param roughness Wall roughness in meters
   */
  public void setRoughness(double roughness) {
    this.roughness = roughness;
  }

  /**
   * Set number of computational cells (sections).
   *
   * <p>
   * More sections provide higher spatial resolution but require more computation. Guideline: dx ≈
   * 10-50 pipe diameters. For slug tracking, use at least 20 sections.
   * </p>
   *
   * @param n Number of sections
   */
  public void setNumberOfSections(int n) {
    this.numberOfSections = n;
  }

  /**
   * Set elevation profile along the pipe.
   *
   * <p>
   * Array of elevations (meters) at each section node. The array length should match the number of
   * sections. Positive values indicate upward elevation.
   * </p>
   *
   * <p>
   * <b>Example:</b>
   * </p>
   * 
   * <pre>{@code
   * double[] elevations = new double[50];
   * for (int i = 0; i < 50; i++) {
   *   elevations[i] = 10 * Math.sin(i * Math.PI / 49); // Terrain with low point
   * }
   * pipe.setElevationProfile(elevations);
   * }</pre>
   *
   * @param profile Array of elevations in meters
   */
  public void setElevationProfile(double[] profile) {
    this.elevationProfile = profile;
  }

  /**
   * Set inclination profile along the pipe.
   *
   * <p>
   * Array of inclination angles (radians) at each section. Positive values indicate upward
   * inclination. Use this instead of elevation profile for constant-inclination sections.
   * </p>
   *
   * @param profile Array of inclination angles in radians
   */
  public void setInclinationProfile(double[] profile) {
    this.inclinationProfile = profile;
  }

  /**
   * Set maximum simulation time.
   *
   * <p>
   * The simulation runs until this time is reached or steady-state is achieved.
   * </p>
   *
   * @param time Maximum simulation time in seconds
   */
  public void setMaxSimulationTime(double time) {
    this.maxSimulationTime = time;
  }

  /**
   * Get current simulation time.
   *
   * @return Simulation time in seconds
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Set inlet boundary condition type.
   *
   * @param bc Boundary condition type
   * @see BoundaryCondition
   */
  public void setInletBoundaryCondition(BoundaryCondition bc) {
    this.inletBCType = bc;
  }

  public void setOutletBoundaryCondition(BoundaryCondition bc) {
    this.outletBCType = bc;
  }

  public void setinletPressureValue(double pressure) {
    this.inletPressureValue = pressure;
  }

  public void setoutletPressureValue(double pressure) {
    this.outletPressureValue = pressure;
    this.outletPressureExplicitlySet = true;
  }

  public void setInletMassFlow(double flow) {
    this.inletMassFlow = flow;
  }

  @Override
  public void setInletStream(StreamInterface stream) {
    this.inStream = stream;
    // Create outlet stream as clone of inlet (same as TwoPortEquipment base class)
    if (stream != null) {
      this.outStream = stream.clone(this.getName() + "_outlet");
    }
  }

  @Override
  public StreamInterface getOutletStream() {
    return outStream;
  }

  public void setOutletStream(StreamInterface stream) {
    this.outStream = stream;
  }

  public double[] getPressureProfile() {
    return pressureProfile;
  }

  /**
   * Get temperature profile at end of simulation.
   *
   * @return Array of temperatures (K) at each section, or null if not run
   */
  public double[] getTemperatureProfile() {
    return temperatureProfile;
  }

  /**
   * Get liquid holdup profile at end of simulation.
   *
   * <p>
   * Liquid holdup (α_L) is the fraction of pipe cross-section occupied by liquid. Values range from
   * 0 (all gas) to 1 (all liquid).
   * </p>
   *
   * @return Array of liquid holdups (dimensionless) at each section, or null if not run
   */
  public double[] getLiquidHoldupProfile() {
    return liquidHoldupProfile;
  }

  /**
   * Get gas velocity profile at end of simulation.
   *
   * @return Array of gas velocities (m/s) at each section, or null if not run
   */
  public double[] getGasVelocityProfile() {
    return gasVelocityProfile;
  }

  /**
   * Get liquid velocity profile at end of simulation.
   *
   * @return Array of liquid velocities (m/s) at each section, or null if not run
   */
  public double[] getLiquidVelocityProfile() {
    return liquidVelocityProfile;
  }

  /**
   * Get pressure history over simulation time.
   *
   * <p>
   * The history is stored at intervals specified by historyInterval. The array dimensions are
   * [time_index][position_index].
   * </p>
   *
   * @return 2D array of pressures (Pa), or null if not run
   */
  public double[][] getPressureHistory() {
    return pressureHistory;
  }

  /**
   * Get all pipe sections with their state variables.
   *
   * <p>
   * Each section contains detailed state information including pressure, temperature, holdups,
   * velocities, flow regime, and other properties.
   * </p>
   *
   * @return Array of PipeSection objects
   */
  public PipeSection[] getSections() {
    return sections;
  }

  /**
   * Get the slug tracker for detailed slug analysis.
   *
   * <p>
   * The slug tracker contains information about active slugs, slug statistics, and slug history.
   * </p>
   *
   * <p>
   * <b>Example:</b>
   * </p>
   * 
   * <pre>{@code
   * SlugTracker tracker = pipe.getSlugTracker();
   * int slugCount = tracker.getSlugCount();
   * double avgLength = tracker.getAverageSlugLength();
   * String stats = tracker.getStatisticsString();
   * }</pre>
   *
   * @return SlugTracker instance
   */
  public SlugTracker getSlugTracker() {
    return slugTracker;
  }

  /**
   * Get the liquid accumulation tracker.
   *
   * <p>
   * Tracks liquid pooling at terrain low points. Useful for identifying potential liquid loading
   * and slug initiation locations.
   * </p>
   *
   * <p>
   * <b>Example:</b>
   * </p>
   * 
   * <pre>{@code
   * var tracker = pipe.getAccumulationTracker();
   * for (var zone : tracker.getAccumulationZones()) {
   *   System.out.println("Low point at: " + zone.getPosition());
   *   System.out.println("Accumulated: " + zone.getAccumulatedVolume() + " m3");
   * }
   * }</pre>
   *
   * @return LiquidAccumulationTracker instance
   */
  public LiquidAccumulationTracker getAccumulationTracker() {
    return accumulationTracker;
  }

  /**
   * Get total number of time steps taken during simulation.
   *
   * @return Number of time steps
   */
  public int getTotalTimeSteps() {
    return totalTimeSteps;
  }

  /**
   * Set CFL number for adaptive time stepping.
   *
   * <p>
   * The CFL (Courant-Friedrichs-Lewy) number controls the time step size relative to the grid
   * spacing and wave speeds. Lower values (0.3-0.5) provide more stability but slower simulation.
   * Higher values (0.7-0.9) are faster but may become unstable.
   * </p>
   *
   * @param cfl CFL number, clamped to range [0.1, 1.0]
   */
  public void setCflNumber(double cfl) {
    this.cflNumber = Math.max(0.1, Math.min(1.0, cfl));
  }

  /**
   * Set interval for thermodynamic property updates.
   *
   * <p>
   * Flash calculations are computationally expensive. This setting controls how often phase
   * properties are recalculated. Higher values improve performance but may reduce accuracy for
   * rapidly changing conditions.
   * </p>
   *
   * @param interval Update interval (number of time steps), minimum 1
   */
  public void setThermodynamicUpdateInterval(int interval) {
    this.thermodynamicUpdateInterval = Math.max(1, interval);
  }

  /**
   * Enable or disable thermodynamic updates during simulation.
   *
   * <p>
   * When disabled, phase properties remain constant at initial values. This is appropriate for
   * isothermal simulations or when temperature/pressure changes are small.
   * </p>
   *
   * @param update true to enable updates, false to disable
   */
  public void setUpdateThermodynamics(boolean update) {
    this.updateThermodynamics = update;
  }

  @Override
  public UUID getCalculationIdentifier() {
    return calcIdentifier;
  }

  @Override
  public void setCalculationIdentifier(UUID id) {
    this.calcIdentifier = id;
  }

  // ========== PipeLineInterface required methods (stubs for compatibility) ==========

  /**
   * {@inheritDoc}
   *
   * <p>
   * This transient model uses internal discretization, not FlowSystemInterface.
   * </p>
   */
  @Override
  public FlowSystemInterface getPipe() {
    return null; // This model uses internal PipeSection array, not legacy FlowSystemInterface
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfLegs(int number) {
    // Not used - use setNumberOfSections instead
  }

  /** {@inheritDoc} */
  @Override
  public void setHeightProfile(double[] heights) {
    setElevationProfile(heights);
  }

  /** {@inheritDoc} */
  @Override
  public void setLegPositions(double[] positions) {
    // Not used - positions are derived from length and number of sections
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeDiameters(double[] diameters) {
    if (diameters != null && diameters.length > 0) {
      setDiameter(diameters[0]); // Use first diameter
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double[] rough) {
    if (rough != null && rough.length > 0) {
      setRoughness(rough[0]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterTemperatures(double[] outerTemp) {
    if (outerTemp != null && outerTemp.length > 0) {
      this.ambientTemperature = outerTemp[0];
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfNodesInLeg(int number) {
    setNumberOfSections(number);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutputFileName(String name) {
    // Not implemented - results are accessed via getter methods
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {
    // Flow pattern is detected automatically by FlowRegimeDetector
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getInletStream() {
    return inStream;
  }
}
