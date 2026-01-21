package neqsim.process.equipment.pipeline.twophasepipe;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * 
 * Implements a 1D transient multiphase flow simulator for gas-liquid flow in pipelines. The model
 * is suitable for analyzing terrain-induced slugging, liquid accumulation, and transient pressure
 * behavior in production pipelines, risers, and flowlines.
 *
 * <b>Three-Phase Flow Support:</b> When both oil and aqueous (water) phases are present, the model
 * automatically calculates volume-weighted average liquid properties (density, viscosity, enthalpy,
 * sound speed) based on the individual phase volumes from the thermodynamic flash. This maintains
 * the drift-flux framework while properly accounting for oil-water mixtures.
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
 * 
 * The drift-flux model relates gas velocity to mixture velocity:
 * 
 * <pre>
 * v_G = C₀ · v_m + v_d
 * </pre>
 * 
 * where C₀ is the distribution coefficient (typically 1.0-1.2) and v_d is the drift velocity. Flow
 * regime-dependent correlations from Bendiksen (1984) and Harmathy (1960) provide closure.
 *
 * <h2>Numerical Method</h2>
 * 
 * The model solves conservation equations using an explicit finite volume scheme with AUSM+ flux
 * splitting. Time stepping is adaptive based on CFL condition for numerical stability.
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
  private static final Logger logger = LogManager.getLogger(TransientPipe.class);
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
  private double jouleThomsonCoeff = 3e-6; // K/Pa (calculated from fluid thermodynamics)

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

  // Mechanical design fields
  private transient neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator mechanicalDesignCalculator;
  private double designPressure = 0.0;
  private double designTemperature = 273.15 + 50.0;
  private String materialGrade = "X65";
  private String designCode = "ASME_B31_8";
  private int locationClass = 1;
  private double corrosionAllowance = 0.003;

  /**
   * Boundary condition types for inlet and outlet.
   *
   * 
   * Specifies how the boundary conditions are handled at the pipe inlet and outlet.
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
   * 
   * <b>Example:</b>
   * 
   * <pre>{@code
   * pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
   * pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
   * pipe.setInletMassFlow(5.0); // kg/s
   * pipe.setOutletPressure(30.0); // bara
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
   * 
   * This is the recommended constructor for typical usage. The inlet stream provides initial
   * conditions (composition, temperature, pressure, flow rate) for the simulation.
   *
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
   * 
   * Creates the computational mesh based on pipe length and number of sections. Also initializes
   * elevation/inclination profiles and identifies low points for liquid accumulation tracking.
   *
   *
   * 
   * This method is called automatically by {@link #run()} if sections have not been initialized. It
   * can also be called explicitly to inspect the mesh before running.
   *
   *
   * 
   * <b>Note:</b> The number of sections determines spatial resolution. For accurate slug tracking,
   * use at least 20 sections. Typical guideline: dx ≈ 10-50 pipe diameters.
   *
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

      // Calculate Joule-Thomson coefficient from gas phase thermodynamics
      try {
        jouleThomsonCoeff = fluid.getPhase("gas").getJouleThomsonCoefficient("K/Pa");
        if (Double.isNaN(jouleThomsonCoeff) || Double.isInfinite(jouleThomsonCoeff)) {
          jouleThomsonCoeff = 3e-6; // Fallback to typical natural gas value
        }
      } catch (Exception ex) {
        jouleThomsonCoeff = 3e-6; // Fallback
      }
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

    // Initialize interface properties before getting surface tension
    // Determine liquid type for appropriate default IFT
    boolean hasWater = fluid.hasPhaseType("aqueous");
    boolean hasOil = fluid.hasPhaseType("oil");
    // Default IFT values: gas-water ~72 mN/m, gas-oil ~20 mN/m
    double defaultSigma = hasWater && !hasOil ? 0.072 : 0.020;
    try {
      fluid.getInterphaseProperties().init(fluid);
      sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1e-3; // mN/m to N/m
    } catch (Exception e) {
      sigma = 0; // Will be set to default below
    }
    if (sigma <= 0) {
      sigma = defaultSigma;
      logger.warn(
          "Interfacial tension calculation returned invalid value. Using default IFT: {} N/m ({})",
          sigma, hasWater && !hasOil ? "gas-water" : "gas-oil");
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
   * 
   * This method advances the pipe simulation by the specified time step {@code dt}, making it
   * suitable for use within a
   * {@link neqsim.process.processmodel.ProcessSystem#runTransient(double, java.util.UUID)} loop.
   * Unlike {@link #run()}, which runs the complete simulation to {@code maxSimulationTime}, this
   * method performs incremental time-stepping that can be coordinated with other equipment.
   *
   *
   * 
   * On the first call, the pipe is initialized from the inlet stream. Subsequent calls advance the
   * simulation state incrementally. The method uses adaptive sub-stepping internally to maintain
   * numerical stability (CFL condition) while advancing by the requested {@code dt}.
   *
   *
   * 
   * <b>Usage Example:</b>
   *
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
   * 
   * <b>Note:</b> The inlet stream conditions are re-read at each call, allowing dynamic boundary
   * conditions. If the inlet flow rate or composition changes, the pipe simulation will respond
   * accordingly.
   *
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
   * 
   * This method is called during {@link #runTransient(double, UUID)} to capture any changes in the
   * inlet stream conditions (flow rate, pressure, composition) that may have occurred since the
   * last time step.
   *
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
   * 
   * This method is called during {@link #runTransient(double, UUID)} to capture any changes in the
   * outlet stream conditions when using CONSTANT_FLOW outlet boundary condition. This allows
   * external controllers or other process equipment to set the outlet flow rate on the outlet
   * stream, which is then read back into the pipe model.
   *
   */
  private void updateOutletFromStream() {
    if (outStream == null || outStream.getFluid() == null) {
      return;
    }

    // Update outlet mass flow from stream when using constant flow BC
    if (outletBCType == BoundaryCondition.CONSTANT_FLOW) {
      double streamFlow = outStream.getFlowRate("kg/sec");
      if (streamFlow > 0) {
        outletMassFlow = streamFlow;
      }
    }

    // Update outlet pressure if using pressure BC, but only if not explicitly set by user
    if (outletBCType == BoundaryCondition.CONSTANT_PRESSURE && !outletPressureExplicitlySet) {
      outletPressureValue = outStream.getFluid().getPressure() * 1e5; // bar to Pa
    }
  }

  /**
   * Calculate adaptive time step based on CFL condition.
   *
   * <p>
   * Uses the full eigenvalue structure for hyperbolic systems. The characteristic wave speeds for
   * multiphase flow are approximately |u ± c| where u is the mixture velocity and c is the mixture
   * sound speed. We take the maximum of all four wave speeds: |u_G + c|, |u_G - c|, |u_L + c|, |u_L
   * - c| to ensure stability for both forward and backward propagating waves.
   * </p>
   *
   * @return time step (s)
   */
  private double calculateTimeStep() {
    double maxWaveSpeed = 0;
    int nanCount = 0;

    for (PipeSection section : sections) {
      // Wave speeds: sound speed and characteristic velocities
      double c_mix = section.getWallisSoundSpeed();
      double v_gas = section.getGasVelocity();
      double v_liq = section.getLiquidVelocity();
      double pressure = section.getPressure();

      // Handle NaN velocities and detect unstable state
      if (Double.isNaN(v_gas) || Double.isNaN(v_liq) || Double.isNaN(pressure)
          || Double.isNaN(c_mix)) {
        nanCount++;
        continue; // Skip this section for wave speed calculation
      }

      // Limit velocities to physically reasonable range
      v_gas = Math.max(-500, Math.min(500, v_gas));
      v_liq = Math.max(-100, Math.min(100, v_liq));

      // Ensure sound speed is reasonable (at least 10 m/s, at most 1000 m/s)
      c_mix = Math.max(10, Math.min(1000, c_mix));

      // Full eigenvalue structure: |u + c| and |u - c| for both phases
      // This captures both forward and backward propagating acoustic waves
      double maxLocalGas = Math.max(Math.abs(v_gas + c_mix), Math.abs(v_gas - c_mix));
      double maxLocalLiq = Math.max(Math.abs(v_liq + c_mix), Math.abs(v_liq - c_mix));
      double maxLocal = Math.max(maxLocalGas, maxLocalLiq);

      if (!Double.isNaN(maxLocal) && !Double.isInfinite(maxLocal)) {
        maxWaveSpeed = Math.max(maxWaveSpeed, maxLocal);
      }
    }

    // If too many sections have NaN, reduce time step significantly
    if (nanCount > numberOfSections / 4) {
      return minTimeStep; // Use minimum time step when unstable
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
   *
   * @param dt time step (s)
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

      // Convert back to primitive variables, passing flux info for pressure update
      double momentumFluxLeft = fluxes[i][2];
      double momentumFluxRight = fluxes[i + 1][2];
      updatePrimitiveVariables(sections[i], oldSections[i], U_new, i, dt, momentumFluxLeft,
          momentumFluxRight);
    }

    // Apply boundary conditions again to enforce them on the new state
    applyBoundaryConditions();

    // Update pressure profile only for boundary-adjacent sections to maintain consistency
    // Interior pressure comes from momentum equation to preserve conservation
    updateBoundaryPressures();
  }

  /**
   * Get the controlling mass flow rate for pressure profile calculation.
   * 
   * 
   * Returns the appropriate mass flow rate based on boundary conditions:
   * <ul>
   * <li>CONSTANT_FLOW inlet: use inlet mass flow</li>
   * <li>CONSTANT_FLOW outlet: use outlet mass flow</li>
   * <li>Both CONSTANT_PRESSURE: use inlet mass flow as fallback</li>
   * </ul>
   *
   * @return Mass flow rate in kg/s
   */
  private double getMassFlowForPressureProfile() {
    if (inletBCType == BoundaryCondition.CONSTANT_FLOW && inletMassFlow > 0) {
      return inletMassFlow;
    } else if (outletBCType == BoundaryCondition.CONSTANT_FLOW && outletMassFlow > 0) {
      return outletMassFlow;
    } else if (inletMassFlow > 0) {
      return inletMassFlow;
    } else if (outletMassFlow > 0) {
      return outletMassFlow;
    }
    // Fallback: estimate from inlet section velocity
    if (sections != null && sections.length > 0) {
      PipeSection inlet = sections[0];
      return inlet.getMixtureDensity() * inlet.getMixtureVelocity() * inlet.getArea();
    }
    return 1.0; // Default fallback
  }

  /**
   * Update pressure at boundary sections only to maintain boundary condition consistency.
   * 
   * <p>
   * Interior section pressures are computed from the momentum equation to preserve momentum
   * conservation. Only boundary-adjacent sections are updated to ensure proper pressure gradient at
   * boundaries.
   * </p>
   */
  private void updateBoundaryPressures() {
    // For CONSTANT_PRESSURE boundaries, pressure is already set by applyBoundaryConditions
    // For CONSTANT_FLOW boundaries, update pressure in boundary section based on neighbor

    if (inletBCType == BoundaryCondition.CONSTANT_FLOW && numberOfSections > 1) {
      // Update inlet pressure from adjacent section with gradient correction
      PipeSection inlet = sections[0];
      PipeSection next = sections[1];

      double rho_m = inlet.getEffectiveMixtureDensity();
      double theta = inlet.getInclination();
      double dP_gravity = rho_m * GRAVITY * Math.sin(theta) * dx;

      // Simple backward difference
      inlet.setPressure(next.getPressure() + dP_gravity);
    }

    if (outletBCType == BoundaryCondition.CONSTANT_FLOW && numberOfSections > 1) {
      // Update outlet pressure from adjacent section
      PipeSection outlet = sections[numberOfSections - 1];
      PipeSection prev = sections[numberOfSections - 2];

      double rho_m = outlet.getEffectiveMixtureDensity();
      double theta = outlet.getInclination();
      double dP_gravity = rho_m * GRAVITY * Math.sin(theta) * dx;

      outlet.setPressure(prev.getPressure() - dP_gravity);
    }
  }

  /**
   * Update pressure profile based on friction and gravity pressure gradients.
   * 
   * <p>
   * <b>Legacy method:</b> Marches from inlet to outlet (or outlet to inlet), computing pressure
   * drop in each cell based on the local friction and gravity gradients. This ensures the pressure
   * profile is consistent with the flow. For single-phase flow, uses the direct Darcy-Weisbach
   * calculation.
   * </p>
   * 
   * <p>
   * <b>Note:</b> This method is preserved for compatibility but is no longer called in transient
   * mode to avoid overriding momentum-conserving pressure from the conservative solver.
   * </p>
   */
  private void updatePressureProfile() {
    // Determine the controlling mass flow rate based on boundary conditions
    double massFlow = getMassFlowForPressureProfile();

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
        double u_m = massFlow / (rho_m * A);

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
    } else if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE
        && outletBCType == BoundaryCondition.CONSTANT_FLOW) {
      // CONSTANT_PRESSURE inlet + CONSTANT_FLOW outlet: march from inlet to outlet
      double P = inletPressureValue;
      sections[0].setPressure(P);

      for (int i = 1; i < numberOfSections; i++) {
        PipeSection section = sections[i - 1];

        // Use effective properties that account for slug presence
        double rho_m = section.getEffectiveMixtureDensity();
        double mu_m = section.getGasHoldup() * section.getGasViscosity()
            + section.getEffectiveLiquidHoldup() * section.getLiquidViscosity();

        double A = section.getArea();
        double u_m = massFlow / (rho_m * A);

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
    } else if (inletBCType == BoundaryCondition.CONSTANT_PRESSURE) {
      // March from inlet to outlet (default for CONSTANT_PRESSURE inlet)
      double P = inletPressureValue;
      sections[0].setPressure(P);

      for (int i = 1; i < numberOfSections; i++) {
        PipeSection section = sections[i - 1];

        // Use effective properties that account for slug presence
        double rho_m = section.getEffectiveMixtureDensity();
        double mu_m = section.getGasHoldup() * section.getGasViscosity()
            + section.getEffectiveLiquidHoldup() * section.getLiquidViscosity();

        double A = section.getArea();
        double u_m = massFlow / (rho_m * A);

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
   * 
   * For interior interfaces, the AUSM+ upwind scheme is used. For boundary interfaces, the fluxes
   * are set according to the boundary conditions to ensure mass and momentum conservation:
   * <ul>
   * <li>CONSTANT_FLOW: Flux is set to the specified mass flow rate</li>
   * <li>CONSTANT_PRESSURE: Flux is computed from the AUSM scheme</li>
   * <li>CLOSED: Zero flux</li>
   * </ul>
   *
   * @param oldSections array of pipe sections from the previous time step
   * @return 2D array of fluxes [section][conserved variable index]
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

      // Energy flux: (enthalpy + kinetic energy) * mass_flux + pressure_work
      double h_total_in = (rho_G_in * alpha_G_in * (h_G_in + 0.5 * u_inlet * u_inlet)
          + rho_L_in * alpha_L_in * (h_L_in + 0.5 * u_inlet * u_inlet));
      fluxes[0][3] = h_total_in * u_inlet + p_inlet * u_inlet;
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

      // Energy flux with kinetic energy
      double h_G = outletSection.getGasEnthalpy();
      double h_L = outletSection.getLiquidEnthalpy();
      double h_total_out = (rho_G * alpha_G * (h_G + 0.5 * u_outlet * u_outlet)
          + rho_L * alpha_L * (h_L + 0.5 * u_outlet * u_outlet));
      fluxes[numberOfSections][3] = h_total_out * u_outlet + p_outlet * u_outlet;
    }

    return fluxes;
  }

  /**
   * Calculate AUSM+ numerical flux at interface.
   *
   * @param left left pipe section
   * @param right right pipe section
   * @return flux array [mass_L, mass_G, momentum_L, momentum_G]
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

    // Energy flux: enthalpy + kinetic energy + pressure work
    double h_G_left = left.getGasEnthalpy();
    double h_G_right = right.getGasEnthalpy();
    double h_L_left = left.getLiquidEnthalpy();
    double h_L_right = right.getLiquidEnthalpy();

    // Total specific enthalpy including kinetic energy: h_total = h + 0.5*u^2
    double h_total_G_left = h_G_left + 0.5 * u_G_left * u_G_left;
    double h_total_G_right = h_G_right + 0.5 * u_G_right * u_G_right;
    double h_total_L_left = h_L_left + 0.5 * u_L_left * u_L_left;
    double h_total_L_right = h_L_right + 0.5 * u_L_right * u_L_right;

    double energy_G = mdot_G * ((M_face_G > 0) ? h_total_G_left : h_total_G_right);
    double energy_L = mdot_L * ((M_face_L > 0) ? h_total_L_left : h_total_L_right);

    // Add pressure work term: p * u
    double pressure_work = p_face * ((M_face_G + M_face_L > 0) ? u_mix_left : u_mix_right);

    flux[3] = energy_G + energy_L + pressure_work;

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
   * 
   * The momentum equation in conservative form is:
   *
   * 
   * <pre>
   * ∂(ρu)/∂t + ∂(ρu² + p)/∂x = -ρg sin(θ) - τ_w/A
   * </pre>
   *
   * 
   * Where the source terms are:
   *
   * <ul>
   * <li>Gravity: S_g = -ρ_m · g · sin(θ) [kg/(m²·s²)]</li>
   * <li>Friction: S_f = -dP/dx_friction [kg/(m²·s²)]</li>
   * </ul>
   * 
   * These are integrated as: U[2]_new = U[2]_old + S × dt
   *
   * @param U conserved variable array to be modified in-place
   * @param section pipe section for which to calculate source terms
   * @param dt time step size in seconds
   */
  private void addSourceTerms(double[] U, PipeSection section, double dt) {
    double rho_m = section.getMixtureDensity();
    double theta = section.getInclination();

    // Sanity check for density
    if (Double.isNaN(rho_m) || rho_m <= 0) {
      rho_m = 100.0; // Default fallback (gas-dominated mixture)
    }

    // Gravity source term for momentum equation: S_g = -ρ_m · g · sin(θ)
    // Units: kg/m³ × m/s² = kg/(m²·s²) = N/m³
    // Multiply by dt to get change in momentum: kg/(m²·s²) × s = kg/(m²·s)
    double S_gravity = -rho_m * GRAVITY * Math.sin(theta);
    if (!Double.isNaN(S_gravity)) {
      U[2] += S_gravity * dt;
    }

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

    // Limit friction contribution to prevent runaway
    double maxFriction = 1000.0; // Pa/m max friction gradient
    if (Double.isNaN(dP_friction)) {
      dP_friction = 0;
    } else {
      dP_friction = Math.max(-maxFriction, Math.min(maxFriction, dP_friction));
    }

    // Friction decelerates the flow - add the negative pressure gradient
    U[2] += dP_friction * dt;
  }

  /**
   * Update primitive variables from conservative variables.
   *
   * <p>
   * This method converts from conservative variables (densities, momentum, energy) back to
   * primitive variables (holdups, velocities, temperature, pressure). Interior cell pressures are
   * computed from the momentum equation residual to properly capture acoustic wave propagation.
   * </p>
   *
   * @param section Current section to update
   * @param oldSection Previous time step section state
   * @param U Conservative variables [ρ_G·α_G, ρ_L·α_L, ρ_m·u, ρ_m·E]
   * @param sectionIndex Index of this section (0 to numberOfSections-1)
   * @param dt Time step (s)
   * @param momentumFluxLeft Momentum flux at left face (Pa)
   * @param momentumFluxRight Momentum flux at right face (Pa)
   */
  private void updatePrimitiveVariables(PipeSection section, PipeSection oldSection, double[] U,
      int sectionIndex, double dt, double momentumFluxLeft, double momentumFluxRight) {
    // U = [rho_G * alpha_G, rho_L * alpha_L, rho_m * u, rho_m * E]

    double massConc_G = Math.max(U[0], 1e-10);
    double massConc_L = Math.max(U[1], 1e-10);
    double momentum = U[2];
    double totalEnergy = U[3];

    // Get current densities (will be updated by thermo)
    double rho_G = section.getGasDensity();
    double rho_L = section.getLiquidDensity();

    // Sanity check for densities - use fallback if invalid
    if (Double.isNaN(rho_G) || rho_G <= 0) {
      rho_G = oldSection.getGasDensity();
    }
    if (Double.isNaN(rho_L) || rho_L <= 0) {
      rho_L = oldSection.getLiquidDensity();
    }

    // Calculate holdups
    double alpha_G = massConc_G / rho_G;
    double alpha_L = massConc_L / rho_L;

    // Normalize and enforce strict bounds [0.001, 0.999] to prevent single-phase collapse
    double total = alpha_G + alpha_L;
    if (total > 0 && !Double.isNaN(total)) {
      alpha_G /= total;
      alpha_L /= total;
    } else {
      // Fallback to old values if calculation failed
      alpha_G = oldSection.getGasHoldup();
      alpha_L = oldSection.getLiquidHoldup();
    }

    // Enforce holdup bounds with relaxation toward old values for stability
    final double minHoldup = 0.0001;
    final double maxHoldup = 0.9999;
    alpha_G = Math.max(minHoldup, Math.min(maxHoldup, alpha_G));
    alpha_L = Math.max(minHoldup, Math.min(maxHoldup, alpha_L));

    // Re-normalize after bounds enforcement
    total = alpha_G + alpha_L;
    alpha_G /= total;
    alpha_L /= total;

    section.setGasHoldup(alpha_G);
    section.setLiquidHoldup(alpha_L);

    // Mixture velocity from momentum
    double rho_m = massConc_G + massConc_L;
    double u_m = momentum / Math.max(rho_m, 1e-10);

    // Limit mixture velocity to physically reasonable range
    final double maxVelocity = 200.0; // m/s - supersonic would be unrealistic
    if (Double.isNaN(u_m) || Math.abs(u_m) > maxVelocity) {
      u_m = oldSection.getMixtureVelocity(); // Fallback to old velocity
    }
    u_m = Math.max(-maxVelocity, Math.min(maxVelocity, u_m));

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

      // Sanity check on drift-flux parameters
      if (Double.isNaN(C0) || C0 < 0.9 || C0 > 1.5) {
        C0 = 1.0; // Default to no-slip
      }
      if (Double.isNaN(v_d) || Math.abs(v_d) > 10.0) {
        v_d = 0.0; // Default to no drift
      }

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

      // Limit phase velocities to physically reasonable range
      final double maxPhaseVelocity = 300.0; // m/s
      if (Double.isNaN(v_G) || Math.abs(v_G) > maxPhaseVelocity) {
        v_G = u_m;
      }
      if (Double.isNaN(v_L) || Math.abs(v_L) > maxPhaseVelocity) {
        v_L = u_m;
      }

      section.setGasVelocity(v_G);
      section.setLiquidVelocity(v_L);
    }

    // Temperature update using energy equation
    if (includeHeatTransfer) {
      DriftFluxParameters energyParams = driftFluxModel.calculateDriftFlux(section);
      // JT coefficient is calculated from gas phase thermodynamics
      DriftFluxModel.EnergyEquationResult energyResult =
          driftFluxModel.calculateEnergyEquation(section, energyParams, dt, dx, ambientTemperature,
              overallHeatTransferCoeff, jouleThomsonCoeff);
      section.setTemperature(energyResult.newTemperature);
    } else {
      // Keep isothermal when heat transfer is disabled
      section.setTemperature(oldSection.getTemperature());
    }

    // Pressure update: compute from momentum equation for interior cells
    // Boundary cells will be overridden by updateBoundaryPressures()
    //
    // The momentum flux contains pressure: F_mom = ρu² + p
    // For AUSM+, we can extract pressure contribution from the flux difference.
    // The pressure at cell center satisfies the momentum balance:
    // ∂(ρu)/∂t + ∂(ρu² + p)/∂x = S (gravity + friction)
    //
    // After the finite volume update, the momentum residual gives:
    // p_new ≈ p_old - c² × (ρ_new - ρ_old)
    // This is the acoustic relation for weakly compressible flow.
    //
    // For interior cells (not boundaries), use acoustic relation:
    boolean isBoundaryCell = (sectionIndex == 0 || sectionIndex == numberOfSections - 1);
    if (!isBoundaryCell) {
      double rho_m_old = oldSection.getMixtureDensity();
      double rho_m_new = massConc_G + massConc_L;
      double c_mix = section.getWallisSoundSpeed();

      // Sanity check for sound speed - use reasonable bounds
      if (Double.isNaN(c_mix) || c_mix < 10 || c_mix > 1000) {
        c_mix = 200.0; // Default to typical mixture sound speed
      }

      // Acoustic pressure-density relation: dp = c² × dρ
      double dRho = rho_m_new - rho_m_old;

      // Limit density change to avoid spurious pressure oscillations
      double maxDensityChange = 0.05 * rho_m_old; // Max 5% density change per step
      dRho = Math.max(-maxDensityChange, Math.min(maxDensityChange, dRho));

      double dP_acoustic = c_mix * c_mix * dRho;

      // Limit pressure change per time step for stability (relaxation)
      double oldPressure = oldSection.getPressure();
      if (Double.isNaN(oldPressure) || oldPressure <= 0) {
        oldPressure = 1e6; // Default to 10 bar if invalid
      }
      double maxDeltaP = 0.05 * oldPressure; // Max 5% change per step (reduced from 10%)
      dP_acoustic = Math.max(-maxDeltaP, Math.min(maxDeltaP, dP_acoustic));

      double newPressure = oldPressure + dP_acoustic;

      // Physical bounds (prevent negative or extreme pressure)
      newPressure = Math.max(1e5, Math.min(5e7, newPressure)); // 1 bar to 500 bar

      section.setPressure(newPressure);
    } else {
      // Boundary cells: use old pressure, will be overridden by updateBoundaryPressures()
      section.setPressure(oldSection.getPressure());
    }

    section.updateDerivedQuantities();
  }

  /**
   * Apply boundary conditions.
   */
  private void applyBoundaryConditions() {
    // Inlet temperature boundary condition (always maintain inlet stream temperature)
    if (inStream != null && inStream.getFluid() != null) {
      sections[0].setTemperature(inStream.getFluid().getTemperature());
    }

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

    for (int i = 0; i < sections.length; i++) {
      PipeSection section = sections[i];
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

          // Calculate Joule-Thomson coefficient from gas phase thermodynamics
          try {
            // Get JT coefficient directly in K/Pa from NeqSim thermodynamics
            double calculatedJT = fluid.getPhase("gas").getJouleThomsonCoefficient("K/Pa");
            // Ensure valid value for typical gases (cooling on expansion)
            if (!Double.isNaN(calculatedJT) && !Double.isInfinite(calculatedJT)) {
              jouleThomsonCoeff = calculatedJT;
            }
          } catch (Exception ex) {
            // Keep previous value if calculation fails
          }
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

        // Initialize interface properties before getting surface tension
        fluid.getInterphaseProperties().init(fluid);
        double sigmaCalc = fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1e-3;
        if (sigmaCalc > 1e-6) {
          section.setSurfaceTension(sigmaCalc);
        } else {
          // Use appropriate default based on liquid type
          // Gas-water: ~72 mN/m, Gas-oil: ~20 mN/m
          boolean isWaterOnly = fluid.hasPhaseType("aqueous") && !fluid.hasPhaseType("oil");
          double defaultSigma = isWaterOnly ? 0.072 : 0.020;
          section.setSurfaceTension(defaultSigma);
          logger.warn(
              "Interfacial tension calculation returned invalid value ({} N/m) in section {}. Using default: {} N/m",
              sigmaCalc, i, defaultSigma);
        }

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
   * 
   * This method updates the outlet stream with:
   *
   * <ul>
   * <li>Pressure from the outlet pipe section</li>
   * <li>Temperature from the outlet pipe section</li>
   * <li>Mass flow rate calculated from outlet section velocity and density</li>
   * </ul>
   * 
   * The flow rate is computed directly from the conservation equations to maintain mass balance.
   * During slug flow, the increased liquid holdup naturally increases the outlet density, which is
   * reflected in the mass flow calculation.
   *
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

      // For CONSTANT_FLOW inlet BC, use inlet mass flow as primary source
      // The outlet mass flow should equal inlet mass flow for mass conservation
      // Local velocity may be low due to momentum solver behavior with CONSTANT_PRESSURE outlet
      if (inletBCType == BoundaryCondition.CONSTANT_FLOW && inletMassFlow > 0) {
        // Use inlet mass flow - this ensures mass conservation
        // The outlet velocity can be derived: u_out = m_dot / (rho_eff * A)
        outletMassFlow = inletMassFlow;

        // Update outlet section velocity for consistency
        if (effectiveDensity > 0 && pipeArea > 0) {
          double outletVelocity = inletMassFlow / (effectiveDensity * pipeArea);
          outletSection.setGasVelocity(outletVelocity);
          outletSection.setLiquidVelocity(outletVelocity);
        }
      } else if (outletBCType == BoundaryCondition.CONSTANT_FLOW && outletMassFlow > 0) {
        // CONSTANT_FLOW outlet BC: use specified outlet mass flow
        // Update outlet section velocity for consistency
        if (effectiveDensity > 0 && pipeArea > 0) {
          double outletVelocity = outletMassFlow / (effectiveDensity * pipeArea);
          outletSection.setGasVelocity(outletVelocity);
          outletSection.setLiquidVelocity(outletVelocity);
        }
        // outletMassFlow is already set by setOutletMassFlow()
      } else if (effectiveDensity > 0 && pipeArea > 0 && Math.abs(velocity) > 0.01) {
        // Calculate mass flow rate (kg/s) from outlet section velocity
        outletMassFlow = effectiveDensity * Math.abs(velocity) * pipeArea;
      } else if (inletMassFlow > 0) {
        // Fallback to inlet mass flow
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
  @Override
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
  @Override
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
  @Override
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
   * Enable or disable heat transfer calculations.
   *
   * <p>
   * When enabled, the energy equation is solved to calculate temperature changes along the pipe due
   * to:
   * </p>
   * <ul>
   * <li>Heat transfer to/from surroundings (ambient)</li>
   * <li>Joule-Thomson effect (gas expansion cooling)</li>
   * <li>Friction heating (viscous dissipation)</li>
   * <li>Elevation work</li>
   * </ul>
   *
   * @param include true to enable heat transfer calculations
   */
  public void setIncludeHeatTransfer(boolean include) {
    this.includeHeatTransfer = include;
  }

  /** {@inheritDoc} */
  @Override
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setAmbientTemperature(double temperature) {
    this.ambientTemperature = temperature;
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
   * Set overall heat transfer coefficient.
   *
   * <p>
   * The overall heat transfer coefficient U accounts for all heat transfer resistances between the
   * fluid and surroundings. Typical values:
   * </p>
   * <ul>
   * <li>Bare steel pipe in still air: 5-10 W/(m²·K)</li>
   * <li>Bare steel pipe in seawater: 200-500 W/(m²·K)</li>
   * <li>Insulated pipe (50mm): 1-3 W/(m²·K)</li>
   * <li>Buried pipe: 2-5 W/(m²·K)</li>
   * </ul>
   *
   * @param coeff Heat transfer coefficient in W/(m²·K)
   */
  public void setOverallHeatTransferCoeff(double coeff) {
    this.overallHeatTransferCoeff = coeff;
  }

  /**
   * Get Joule-Thomson coefficient for temperature change during gas expansion.
   *
   * <p>
   * Returns the JT coefficient calculated from gas phase thermodynamics. This is automatically
   * updated during simulation based on the fluid properties.
   * </p>
   * 
   * <p>
   * Typical values (at ~300K, 50 bar):
   * </p>
   * <ul>
   * <li>Methane: ~4.5e-6 K/Pa (0.45 K/MPa)</li>
   * <li>Natural gas: 2-6e-6 K/Pa</li>
   * <li>CO2: ~1e-5 K/Pa</li>
   * <li>Hydrogen: ~-0.3e-6 K/Pa (warms on expansion)</li>
   * <li>Liquids: ~1e-8 K/Pa (very small)</li>
   * </ul>
   *
   * @return Joule-Thomson coefficient in K/Pa
   */
  public double getJouleThomsonCoeff() {
    return jouleThomsonCoeff;
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
   * 
   * Used for friction factor calculation. Typical values:
   *
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
   * 
   * More sections provide higher spatial resolution but require more computation. Guideline: dx ≈
   * 10-50 pipe diameters. For slug tracking, use at least 20 sections.
   *
   *
   * @param n Number of sections
   */
  public void setNumberOfSections(int n) {
    this.numberOfSections = n;
  }

  /**
   * Set elevation profile along the pipe.
   *
   * 
   * Array of elevations (meters) at each section node. The array length should match the number of
   * sections. Positive values indicate upward elevation.
   *
   *
   * 
   * <b>Example:</b>
   *
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
   * 
   * Array of inclination angles (radians) at each section. Positive values indicate upward
   * inclination. Use this instead of elevation profile for constant-inclination sections.
   *
   *
   * @param profile Array of inclination angles in radians
   */
  public void setInclinationProfile(double[] profile) {
    this.inclinationProfile = profile;
  }

  /**
   * Set maximum simulation time.
   *
   * 
   * The simulation runs until this time is reached or steady-state is achieved.
   *
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

  /**
   * Set the inlet pressure value.
   *
   * @param pressure Inlet pressure in bara (bar absolute)
   * @deprecated Use {@link #setInletPressure(double)} instead
   */
  @Deprecated
  public void setinletPressureValue(double pressure) {
    this.inletPressureValue = pressure * 1e5; // bara to Pa
  }

  /**
   * Set the inlet pressure value.
   *
   * @param pressure Inlet pressure in bara (bar absolute)
   */
  public void setInletPressure(double pressure) {
    this.inletPressureValue = pressure * 1e5; // bara to Pa
  }

  /**
   * Set the outlet pressure value.
   *
   * @param pressure Outlet pressure in bara (bar absolute)
   * @deprecated Use {@link #setOutletPressure(double)} instead
   */
  @Deprecated
  public void setoutletPressureValue(double pressure) {
    this.outletPressureValue = pressure * 1e5; // bara to Pa
    this.outletPressureExplicitlySet = true;
  }

  /**
   * Set the outlet pressure value.
   *
   * @param pressure Outlet pressure in bara (bar absolute)
   */
  public void setOutletPressure(double pressure) {
    this.outletPressureValue = pressure * 1e5; // bara to Pa
    this.outletPressureExplicitlySet = true;
  }

  public void setInletMassFlow(double flow) {
    this.inletMassFlow = flow;
  }

  /**
   * Set the outlet mass flow rate.
   * 
   * 
   * Use this when the outlet flow is controlled by downstream equipment (e.g., a valve). When using
   * CONSTANT_FLOW outlet boundary condition, this value is used to calculate the pressure profile
   * and outlet stream properties.
   *
   * 
   * 
   * This can be called before each runTransient() call to update the outlet flow from downstream
   * valve Cv calculations.
   *
   *
   * @param flow Outlet mass flow rate in kg/s
   */
  public void setOutletMassFlow(double flow) {
    this.outletMassFlow = flow;
  }

  /**
   * Get the current outlet mass flow rate.
   *
   * @return Outlet mass flow rate in kg/s
   */
  public double getOutletMassFlow() {
    return this.outletMassFlow;
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

  /** {@inheritDoc} */
  @Override
  public double[] getPressureProfile() {
    return pressureProfile;
  }

  /**
   * Get temperature profile at end of simulation.
   *
   * @return Array of temperatures (K) at each section, or null if not run
   */
  @Override
  public double[] getTemperatureProfile() {
    return temperatureProfile;
  }

  /**
   * Get liquid holdup profile at end of simulation.
   *
   * 
   * Liquid holdup (α_L) is the fraction of pipe cross-section occupied by liquid. Values range from
   * 0 (all gas) to 1 (all liquid).
   *
   * @return Array of liquid holdups (dimensionless) at each section, or null if not run
   */
  @Override
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
   * 
   * The history is stored at intervals specified by historyInterval. The array dimensions are
   * [time_index][position_index].
   *
   * @return 2D array of pressures (Pa), or null if not run
   */
  public double[][] getPressureHistory() {
    return pressureHistory;
  }

  /**
   * Get all pipe sections with their state variables.
   *
   * 
   * Each section contains detailed state information including pressure, temperature, holdups,
   * velocities, flow regime, and other properties.
   *
   * @return Array of PipeSection objects
   */
  public PipeSection[] getSections() {
    return sections;
  }

  /**
   * Get the slug tracker for detailed slug analysis.
   *
   * 
   * The slug tracker contains information about active slugs, slug statistics, and slug history.
   *
   * <b>Example:</b>
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
   * 
   * Tracks liquid pooling at terrain low points. Useful for identifying potential liquid loading
   * and slug initiation locations.
   *
   * <b>Example:</b>
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
   * 
   * The CFL (Courant-Friedrichs-Lewy) number controls the time step size relative to the grid
   * spacing and wave speeds. Lower values (0.3-0.5) provide more stability but slower simulation.
   * Higher values (0.7-0.9) are faster but may become unstable.
   *
   * @param cfl CFL number, clamped to range [0.1, 1.0]
   */
  public void setCflNumber(double cfl) {
    this.cflNumber = Math.max(0.1, Math.min(1.0, cfl));
  }

  /**
   * Set interval for thermodynamic property updates.
   *
   * 
   * Flash calculations are computationally expensive. This setting controls how often phase
   * properties are recalculated. Higher values improve performance but may reduce accuracy for
   * rapidly changing conditions.
   *
   * @param interval Update interval (number of time steps), minimum 1
   */
  public void setThermodynamicUpdateInterval(int interval) {
    this.thermodynamicUpdateInterval = Math.max(1, interval);
  }

  /**
   * Enable or disable thermodynamic updates during simulation.
   *
   * 
   * When disabled, phase properties remain constant at initial values. This is appropriate for
   * isothermal simulations or when temperature/pressure changes are small.
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
   * 
   * This transient model uses internal discretization, not FlowSystemInterface.
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

  // ========== Additional PipeLineInterface methods ==========

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double roughness) {
    this.roughness = roughness;
  }

  /** {@inheritDoc} */
  @Override
  public double getPipeWallRoughness() {
    return roughness;
  }

  /** {@inheritDoc} */
  @Override
  public void setElevation(double elevation) {
    // Set a linear elevation profile from 0 to elevation
    if (elevationProfile != null) {
      for (int i = 0; i < elevationProfile.length; i++) {
        elevationProfile[i] = elevation * i / (elevationProfile.length - 1);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getElevation() {
    if (elevationProfile != null && elevationProfile.length > 0) {
      return elevationProfile[elevationProfile.length - 1] - elevationProfile[0];
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void setInletElevation(double inletElevation) {
    // Adjust elevation profile
    if (elevationProfile != null && elevationProfile.length > 0) {
      double offset = inletElevation - elevationProfile[0];
      for (int i = 0; i < elevationProfile.length; i++) {
        elevationProfile[i] += offset;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getInletElevation() {
    return (elevationProfile != null && elevationProfile.length > 0) ? elevationProfile[0] : 0;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletElevation(double outletElevation) {
    // Adjust elevation profile
    if (elevationProfile != null && elevationProfile.length > 1) {
      double currentOutletElevation = elevationProfile[elevationProfile.length - 1];
      double scale =
          (outletElevation - elevationProfile[0]) / (currentOutletElevation - elevationProfile[0]);
      for (int i = 1; i < elevationProfile.length; i++) {
        elevationProfile[i] =
            elevationProfile[0] + (elevationProfile[i] - elevationProfile[0]) * scale;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletElevation() {
    return (elevationProfile != null && elevationProfile.length > 0)
        ? elevationProfile[elevationProfile.length - 1]
        : 0;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfLegs() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfIncrements(int numberOfIncrements) {
    setNumberOfSections(numberOfIncrements);
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfIncrements() {
    return numberOfSections;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutPressure(double pressure) {
    setOutletPressure(pressure);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature) {
    // Not directly supported - temperature is calculated
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureDrop() {
    if (pressureProfile != null && pressureProfile.length >= 2) {
      return (pressureProfile[0] - pressureProfile[pressureProfile.length - 1]) / 1e5;
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletPressure(String unit) {
    if (outStream != null) {
      return outStream.getPressure(unit);
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletTemperature(String unit) {
    if (outStream != null) {
      return outStream.getTemperature(unit);
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getVelocity() {
    if (gasVelocityProfile != null && gasVelocityProfile.length > 0) {
      return gasVelocityProfile[gasVelocityProfile.length - 1];
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getSuperficialVelocity(int phaseNumber) {
    if (phaseNumber == 0 && gasVelocityProfile != null && gasVelocityProfile.length > 0) {
      return gasVelocityProfile[gasVelocityProfile.length - 1];
    } else if (phaseNumber == 1 && liquidVelocityProfile != null
        && liquidVelocityProfile.length > 0) {
      return liquidVelocityProfile[liquidVelocityProfile.length - 1];
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public String getFlowRegime() {
    if (flowRegimeDetector != null && sections != null && sections.length > 0) {
      PipeSection.FlowRegime regime = sections[sections.length - 1].getFlowRegime();
      return regime != null ? regime.toString() : "UNKNOWN";
    }
    return "UNKNOWN";
  }

  /** {@inheritDoc} */
  @Override
  public double getLiquidHoldup() {
    if (liquidHoldupProfile != null && liquidHoldupProfile.length > 0) {
      return liquidHoldupProfile[liquidHoldupProfile.length - 1];
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getReynoldsNumber() {
    // Calculate average Reynolds number if available
    return 0; // Not tracked in this model
  }

  /** {@inheritDoc} */
  @Override
  public double getFrictionFactor() {
    // Return average friction factor
    return 0; // Not tracked in this model
  }

  /** {@inheritDoc} */
  @Override
  public void setHeatTransferCoefficient(double coefficient) {
    this.overallHeatTransferCoeff = coefficient;
    this.includeHeatTransfer = (coefficient > 0);
  }

  /** {@inheritDoc} */
  @Override
  public double getHeatTransferCoefficient() {
    return overallHeatTransferCoeff;
  }

  /** {@inheritDoc} */
  @Override
  public void setConstantSurfaceTemperature(double temperature) {
    this.ambientTemperature = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAdiabatic() {
    return !includeHeatTransfer;
  }

  /** {@inheritDoc} */
  @Override
  public void setAdiabatic(boolean adiabatic) {
    this.includeHeatTransfer = !adiabatic;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeSpecification(double nominalDiameter, String specification) {
    setDiameter(nominalDiameter / 1000.0);
  }

  /** {@inheritDoc} */
  @Override
  public void setWallThickness(double thickness) {
    this.wallThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getAngle() {
    if (inclinationProfile != null && inclinationProfile.length > 0) {
      return Math.toDegrees(inclinationProfile[0]);
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void setAngle(double angle) {
    double radians = Math.toRadians(angle);
    if (inclinationProfile == null || inclinationProfile.length == 0) {
      inclinationProfile = new double[numberOfSections];
    }
    for (int i = 0; i < inclinationProfile.length; i++) {
      inclinationProfile[i] = radians;
    }
  }

  // ============================================================================
  // MECHANICAL BUILDUP METHODS - Default implementations
  // ============================================================================

  /** Pipe material. */
  private String pipeMaterial = "carbon steel";
  /** Pipe schedule. */
  private String pipeSchedule = "STD";
  /** Pipe wall thermal conductivity in W/(m·K). */
  private double pipeWallConductivity = 45.0;
  /** Insulation thickness in meters. */
  private double insulationThickness = 0.0;
  /** Insulation thermal conductivity in W/(m·K). */
  private double insulationConductivity = 0.04;
  /** Insulation type. */
  private String insulationType = "none";
  /** Coating thickness in meters. */
  private double coatingThickness = 0.0;
  /** Coating thermal conductivity in W/(m·K). */
  private double coatingConductivity = 0.2;
  /** Inner heat transfer coefficient. */
  private double innerHeatTransferCoefficient = 1000.0;
  /** Outer heat transfer coefficient. */
  private double outerHeatTransferCoefficient = 10.0;
  /** Burial depth in meters. */
  private double burialDepth = 0.0;
  /** Soil thermal conductivity. */
  private double soilConductivity = 1.5;
  /** Flag for buried pipe. */
  private boolean buried = false;

  /** {@inheritDoc} */
  @Override
  public void setPipeMaterial(String material) {
    this.pipeMaterial = material;
  }

  /** {@inheritDoc} */
  @Override
  public String getPipeMaterial() {
    return pipeMaterial;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeSchedule(String schedule) {
    this.pipeSchedule = schedule;
  }

  /** {@inheritDoc} */
  @Override
  public String getPipeSchedule() {
    return pipeSchedule;
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationThickness(double thickness) {
    this.insulationThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getInsulationThickness() {
    return insulationThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationConductivity(double conductivity) {
    this.insulationConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getInsulationConductivity() {
    return insulationConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationType(String insulationType) {
    this.insulationType = insulationType;
  }

  /** {@inheritDoc} */
  @Override
  public String getInsulationType() {
    return insulationType;
  }

  /** {@inheritDoc} */
  @Override
  public void setCoatingThickness(double thickness) {
    this.coatingThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoatingThickness() {
    return coatingThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setCoatingConductivity(double conductivity) {
    this.coatingConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoatingConductivity() {
    return coatingConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallConductivity(double conductivity) {
    this.pipeWallConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getPipeWallConductivity() {
    return pipeWallConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterHeatTransferCoefficient(double coefficient) {
    this.outerHeatTransferCoefficient = coefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getOuterHeatTransferCoefficient() {
    return outerHeatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setInnerHeatTransferCoefficient(double coefficient) {
    this.innerHeatTransferCoefficient = coefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getInnerHeatTransferCoefficient() {
    return innerHeatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterHeatTransferCoefficients(double[] coefficients) {
    // Not supported - use setOuterHeatTransferCoefficient for uniform value
  }

  /** {@inheritDoc} */
  @Override
  public void setWallHeatTransferCoefficients(double[] coefficients) {
    // Not supported - use setInnerHeatTransferCoefficient for uniform value
  }

  /** {@inheritDoc} */
  @Override
  public void setAmbientTemperatures(double[] temperatures) {
    // Not supported - use setAmbientTemperature for uniform value
  }

  /** {@inheritDoc} */
  @Override
  public double calculateOverallHeatTransferCoefficient() {
    // Simple calculation - could be enhanced
    double ri = diameter / 2.0;
    double ro = ri + wallThickness;
    double rins = ro + insulationThickness;

    double R_inner = 1.0 / innerHeatTransferCoefficient;
    double R_wall = (wallThickness > 0) ? ri * Math.log(ro / ri) / pipeWallConductivity : 0.0;
    double R_insulation =
        (insulationThickness > 0) ? ri * Math.log(rins / ro) / insulationConductivity : 0.0;
    double R_outer =
        (outerHeatTransferCoefficient > 0) ? ri / (rins * outerHeatTransferCoefficient) : 0.0;

    double R_total = R_inner + R_wall + R_insulation + R_outer;
    return (R_total > 0) ? 1.0 / R_total : 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setBurialDepth(double depth) {
    this.burialDepth = depth;
    if (depth > 0) {
      this.buried = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getBurialDepth() {
    return burialDepth;
  }

  /** {@inheritDoc} */
  @Override
  public void setSoilConductivity(double conductivity) {
    this.soilConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoilConductivity() {
    return soilConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isBuried() {
    return buried;
  }

  /** {@inheritDoc} */
  @Override
  public void setBuried(boolean buried) {
    this.buried = buried;
  }

  // ============================================================================
  // MECHANICAL DESIGN
  // ============================================================================

  /**
   * Get or create the mechanical design calculator.
   *
   * @return the mechanical design calculator instance
   */
  private neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator getOrCreateCalculator() {
    if (mechanicalDesignCalculator == null) {
      mechanicalDesignCalculator =
          new neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator();
      mechanicalDesignCalculator.setOuterDiameter(diameter + 2 * wallThickness);
      mechanicalDesignCalculator.setDesignPressure(designPressure / 10.0); // bar to MPa
      mechanicalDesignCalculator.setDesignTemperature(designTemperature - 273.15); // K to C
      mechanicalDesignCalculator.setMaterialGrade(materialGrade);
      mechanicalDesignCalculator.setLocationClass(locationClass);
      mechanicalDesignCalculator.setCorrosionAllowance(corrosionAllowance);
    }
    return mechanicalDesignCalculator;
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator getMechanicalDesignCalculator() {
    return getOrCreateCalculator();
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignPressure(double pressure) {
    this.designPressure = pressure;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignPressure(pressure / 10.0); // bar to MPa
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDesignPressure() {
    return designPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignPressure(double pressure, String unit) {
    if ("MPa".equalsIgnoreCase(unit)) {
      this.designPressure = pressure * 10.0;
    } else if ("psi".equalsIgnoreCase(unit)) {
      this.designPressure = pressure * 0.0689476;
    } else {
      this.designPressure = pressure;
    }
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignPressure(this.designPressure / 10.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignTemperature(double temperature) {
    this.designTemperature = temperature;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignTemperature(temperature - 273.15);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDesignTemperature() {
    return designTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setMaterialGrade(grade);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getMaterialGrade() {
    return materialGrade;
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignCode(String code) {
    this.designCode = code;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignCode(code);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getDesignCode() {
    return designCode;
  }

  /** {@inheritDoc} */
  @Override
  public void setLocationClass(int locClass) {
    this.locationClass = Math.max(1, Math.min(4, locClass));
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setLocationClass(this.locationClass);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getLocationClass() {
    return locationClass;
  }

  /** {@inheritDoc} */
  @Override
  public void setCorrosionAllowance(double allowance) {
    this.corrosionAllowance = allowance;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setCorrosionAllowance(allowance);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMinimumWallThickness() {
    return getOrCreateCalculator().calculateMinimumWallThickness();
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMAOP() {
    return getOrCreateCalculator().calculateMAOP();
  }

  /** {@inheritDoc} */
  @Override
  public double getMAOP(String unit) {
    return getOrCreateCalculator().getMaop(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateTestPressure() {
    return getOrCreateCalculator().calculateTestPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double calculateHoopStress() {
    return getOrCreateCalculator().calculateHoopStress(designPressure / 10.0);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateVonMisesStress(double deltaT) {
    return getOrCreateCalculator().calculateVonMisesStress(designPressure / 10.0, deltaT, true);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMechanicalDesignSafe() {
    return getOrCreateCalculator().isDesignSafe();
  }

  /** {@inheritDoc} */
  @Override
  public String generateMechanicalDesignReport() {
    return getOrCreateCalculator().toJson();
  }
}
