package neqsim.process.equipment.pipeline;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.twophasepipe.FlowRegimeDetector;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Two-fluid transient multiphase pipe model.
 *
 * <p>
 * Implements a full two-fluid model for 1D transient multiphase pipeline flow. Unlike the
 * drift-flux based {@link neqsim.process.equipment.pipeline.twophasepipe.TransientPipe}, this model
 * solves separate momentum equations for each phase, providing more accurate predictions for:
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

  /** Spatial step size (m). */
  private double dx;

  // ============ Transient state ============

  /** Current simulation time (s). */
  private double simulationTime = 0;

  /** Maximum simulation time (s). */
  private double maxSimulationTime = 3600;

  /** CFL number for time stepping (0 < CFL < 1). */
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

  /** Slug tracker. */
  private SlugTracker slugTracker;

  // ============ Boundary conditions ============

  /** Boundary condition type. */
  public enum BoundaryCondition {
    /** Constant pressure. */
    CONSTANT_PRESSURE,
    /** Constant mass flow. */
    CONSTANT_FLOW,
    /** Connected to stream. */
    STREAM_CONNECTED
  }

  /** Inlet boundary condition type. */
  private BoundaryCondition inletBCType = BoundaryCondition.STREAM_CONNECTED;

  /** Outlet boundary condition type. */
  private BoundaryCondition outletBCType = BoundaryCondition.CONSTANT_PRESSURE;

  /** Outlet pressure (Pa). */
  private double outletPressure;

  /** Flag indicating if outlet pressure was explicitly set. */
  private boolean outletPressureSet = false;

  // ============ Settings ============

  /** Include energy equation. */
  private boolean includeEnergyEquation = false;

  /** Include mass transfer (flash/condensation). */
  private boolean includeMassTransfer = false;

  /** Enable slug tracking. */
  private boolean enableSlugTracking = true;

  /** Update thermodynamics every N steps. */
  private int thermodynamicUpdateInterval = 10;

  /** Current step count. */
  private int currentStep = 0;

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

    timeIntegrator.setCflNumber(cflNumber);
  }

  /**
   * Initialize pipe sections with inlet conditions.
   */
  private void initializeSections() {
    dx = length / numberOfSections;
    sections = new TwoFluidSection[numberOfSections];

    // Get inlet properties
    SystemInterface inletFluid = getInletStream().getFluid();
    double P_in = inletFluid.getPressure("Pa");
    double T_in = inletFluid.getTemperature("K");

    // Store reference fluid for flash calculations
    referenceFluid = inletFluid.clone();

    // Calculate inlet phase properties
    double rhoG = 1.0, rhoL = 800.0, muG = 1e-5, muL = 1e-3;
    double cG = 340, cL = 1200, hG = 0, hL = 0, sigma = 0.02;
    double alphaL = 0.1, alphaG = 0.9;

    if (inletFluid.hasPhaseType("gas")) {
      rhoG = inletFluid.getPhase("gas").getDensity("kg/m3");
      muG = inletFluid.getPhase("gas").getViscosity("kg/msec");
      cG = inletFluid.getPhase("gas").getSoundSpeed();
      hG = inletFluid.getPhase("gas").getEnthalpy("J/kg");
    }
    if (inletFluid.hasPhaseType("oil") || inletFluid.hasPhaseType("aqueous")) {
      String liqPhase = inletFluid.hasPhaseType("oil") ? "oil" : "aqueous";
      rhoL = inletFluid.getPhase(liqPhase).getDensity("kg/m3");
      muL = inletFluid.getPhase(liqPhase).getViscosity("kg/msec");
      cL = inletFluid.getPhase(liqPhase).getSoundSpeed();
      hL = inletFluid.getPhase(liqPhase).getEnthalpy("J/kg");
      sigma = inletFluid.getInterphaseProperties()
          .getSurfaceTension(inletFluid.getPhaseIndex("gas"), inletFluid.getPhaseIndex(liqPhase));
    }

    // Estimate holdup from inlet stream
    if (inletFluid.getNumberOfPhases() > 1) {
      double volGas = inletFluid.getPhase("gas").getVolume("m3");
      double volTotal = inletFluid.getVolume("m3");
      alphaG = volGas / volTotal;
      alphaL = 1.0 - alphaG;
    }

    // Create sections
    double area = Math.PI * diameter * diameter / 4.0;
    double massFlow = getInletStream().getFlowRate("kg/sec");
    double vMix = massFlow / (area * (alphaG * rhoG + alphaL * rhoL));

    for (int i = 0; i < numberOfSections; i++) {
      double position = (i + 0.5) * dx;
      double elevation =
          (elevationProfile != null && i < elevationProfile.length) ? elevationProfile[i] : 0.0;

      // Calculate inclination from elevation profile
      double inclination = 0;
      if (elevationProfile != null && i < elevationProfile.length - 1) {
        inclination = Math.atan2(elevationProfile[i + 1] - elevation, dx);
      }

      TwoFluidSection sec = new TwoFluidSection(position, dx, diameter, inclination);
      sec.setElevation(elevation);
      sec.setRoughness(roughness);

      // Initialize with inlet conditions (linear pressure drop estimate)
      double P = P_in - (i + 0.5) / numberOfSections * P_in * 0.1; // 10% pressure drop estimate
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

    logger.info("TwoFluidPipe initialized: {} sections, dx={:.2f}m", numberOfSections, dx);
  }

  /**
   * Run steady-state initialization.
   */
  private void runSteadyState() {
    // Simple steady-state: iterate until pressure profile converges
    int maxIter = 100;
    double tolerance = 1e-4;

    for (int iter = 0; iter < maxIter; iter++) {
      double maxChange = 0;

      // Update flow regimes
      for (TwoFluidSection sec : sections) {
        sec.setFlowRegime(flowRegimeDetector.detectFlowRegime(sec));
      }

      // Update pressures using momentum balance (simplified)
      for (int i = 1; i < numberOfSections; i++) {
        TwoFluidSection sec = sections[i];
        TwoFluidSection prev = sections[i - 1];

        // Pressure drop estimate (simplified steady-state)
        double dPdx = estimatePressureGradient(sec);
        double P_new = prev.getPressure() - dPdx * dx;
        P_new = Math.max(1e5, P_new); // Minimum 1 bar

        double change = Math.abs(P_new - sec.getPressure()) / sec.getPressure();
        maxChange = Math.max(maxChange, change);

        sec.setPressure(P_new);
      }

      if (maxChange < tolerance) {
        logger.info("Steady-state converged after {} iterations", iter);
        break;
      }
    }

    // Store initial profiles
    updateResultArrays();
  }

  /**
   * Estimate pressure gradient for steady-state initialization.
   */
  private double estimatePressureGradient(TwoFluidSection sec) {
    double alphaG = sec.getGasHoldup();
    double alphaL = sec.getLiquidHoldup();
    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    double vG = sec.getGasVelocity();
    double vL = sec.getLiquidVelocity();

    // Mixture density
    double rhoMix = alphaG * rhoG + alphaL * rhoL;

    // Friction gradient (simplified)
    double vMix = alphaG * vG + alphaL * vL;
    double Re = rhoMix * Math.abs(vMix) * diameter
        / (alphaG * sec.getGasViscosity() + alphaL * sec.getLiquidViscosity());
    double f = 0.079 / Math.pow(Math.max(Re, 1000), 0.25);
    double dPdx_fric = 2 * f * rhoMix * vMix * Math.abs(vMix) / diameter;

    // Gravity gradient
    double dPdx_grav = rhoMix * 9.81 * Math.sin(sec.getInclination());

    return dPdx_fric + dPdx_grav;
  }

  @Override
  public void run(UUID id) {
    // Initialize sections
    initializeSections();

    // Run steady-state
    runSteadyState();

    // Set up outlet stream
    updateOutletStream();

    setCalculationIdentifier(id);
  }

  /**
   * Run transient simulation for specified time step.
   *
   * @param dt Requested time step (s)
   * @param id Calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    // Calculate stable time step
    double dtStable = calcStableTimeStep();
    double dtActual = Math.min(dt, dtStable);

    // Number of sub-steps
    int subSteps = (int) Math.ceil(dt / dtActual);
    dtActual = dt / subSteps;

    for (int step = 0; step < subSteps; step++) {
      // 1. Update thermodynamic properties (periodically)
      currentStep++;
      if (currentStep % thermodynamicUpdateInterval == 0) {
        updateThermodynamics();
      }

      // 2. Calculate RHS and advance solution
      final double dtFinal = dtActual;
      double[][] U = equations.extractState(sections);

      TimeIntegrator.RHSFunction rhs = (state, t) -> {
        equations.applyState(sections, state);
        return equations.calcRHS(sections, dx);
      };

      double[][] U_new = timeIntegrator.step(U, rhs, dtFinal);
      equations.applyState(sections, U_new);

      // 3. Apply pressure gradient (semi-implicit for stability)
      double[][] dUdt = equations.calcRHS(sections, dx);
      equations.applyPressureGradient(sections, dUdt, dx);

      // 4. Apply boundary conditions
      applyBoundaryConditions();

      // 5. Update accumulation tracking
      if (enableSlugTracking) {
        accumulationTracker.updateAccumulation(sections, dtActual);
      }

      // 6. Advance time
      simulationTime += dtActual;
      timeIntegrator.advanceTime(dtActual);
    }

    // Update outlet stream and result arrays
    updateOutletStream();
    updateResultArrays();

    setCalculationIdentifier(id);
  }

  /**
   * Calculate stable time step using CFL condition.
   */
  private double calcStableTimeStep() {
    double maxSpeed = 1.0; // Minimum

    for (TwoFluidSection sec : sections) {
      double gasSpeed = Math.abs(sec.getGasVelocity()) + sec.getGasSoundSpeed();
      double liqSpeed = Math.abs(sec.getLiquidVelocity()) + sec.getLiquidSoundSpeed();
      maxSpeed = Math.max(maxSpeed, Math.max(gasSpeed, liqSpeed));
    }

    return cflNumber * dx / maxSpeed;
  }

  /**
   * Update thermodynamic properties using flash calculations.
   */
  private void updateThermodynamics() {
    for (TwoFluidSection sec : sections) {
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
        if (flash.hasPhaseType("oil")) {
          sec.setLiquidDensity(flash.getPhase("oil").getDensity("kg/m3"));
          sec.setLiquidViscosity(flash.getPhase("oil").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("oil").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("oil").getEnthalpy("J/kg"));
        } else if (flash.hasPhaseType("aqueous")) {
          sec.setLiquidDensity(flash.getPhase("aqueous").getDensity("kg/m3"));
          sec.setLiquidViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("aqueous").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("aqueous").getEnthalpy("J/kg"));
        }
      } catch (Exception e) {
        logger.warn("Flash calculation failed for section at position {}", sec.getPosition());
      }
    }
  }

  /**
   * Apply boundary conditions.
   */
  private void applyBoundaryConditions() {
    // Inlet boundary
    TwoFluidSection inlet = sections[0];
    if (inletBCType == BoundaryCondition.STREAM_CONNECTED) {
      // Use inlet stream properties
      SystemInterface inFluid = getInletStream().getFluid();
      inlet.setPressure(inFluid.getPressure("Pa"));
      inlet.setTemperature(inFluid.getTemperature("K"));
    }

    // Outlet boundary
    TwoFluidSection outlet = sections[numberOfSections - 1];
    if (outletBCType == BoundaryCondition.CONSTANT_PRESSURE) {
      outlet.setPressure(outletPressure);
    }
  }

  /**
   * Update outlet stream with current outlet conditions.
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

    getOutletStream().setFluid(outFluid);
  }

  /**
   * Update result arrays from section states.
   */
  private void updateResultArrays() {
    if (sections == null) {
      return;
    }

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
   * Get total liquid inventory in the pipe.
   *
   * @param unit Volume unit ("m3", "bbl", "L")
   * @return Liquid volume
   */
  public double getLiquidInventory(String unit) {
    double volume = 0;
    for (TwoFluidSection sec : sections) {
      volume += sec.getLiquidHoldup() * sec.getArea() * sec.getLength();
    }

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
  public double[] getPressureProfile() {
    return pressureProfile != null ? pressureProfile.clone() : new double[0];
  }

  /**
   * Get temperature profile.
   *
   * @return Temperature at each section (K)
   */
  public double[] getTemperatureProfile() {
    return temperatureProfile != null ? temperatureProfile.clone() : new double[0];
  }

  /**
   * Get liquid holdup profile.
   *
   * @return Holdup at each section (0-1)
   */
  public double[] getLiquidHoldupProfile() {
    return liquidHoldupProfile != null ? liquidHoldupProfile.clone() : new double[0];
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
   * Get position array for plotting.
   *
   * @return Position along pipe (m)
   */
  public double[] getPositionProfile() {
    double[] positions = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      positions[i] = (i + 0.5) * dx;
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
   * Get slug tracker for slug statistics.
   *
   * @return Slug tracker
   */
  public SlugTracker getSlugTracker() {
    return slugTracker;
  }

  // ============ Configuration methods ============

  /**
   * Set pipe length.
   *
   * @param length Length (m)
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get pipe length.
   *
   * @return Length (m)
   */
  public double getLength() {
    return length;
  }

  /**
   * Set pipe diameter.
   *
   * @param diameter Diameter (m)
   */
  public void setDiameter(double diameter) {
    this.diameter = diameter;
  }

  /**
   * Get pipe diameter.
   *
   * @return Diameter (m)
   */
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
  public void setOutletPressure(double pressure) {
    this.outletPressure = pressure;
    this.outletPressureSet = true;
  }

  /**
   * Set outlet pressure with unit.
   *
   * @param pressure Pressure value
   * @param unit Pressure unit ("Pa", "bara", "barg", "psia")
   */
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
   * Set CFL number for time stepping.
   *
   * @param cfl CFL number (0 < cfl < 1)
   */
  public void setCflNumber(double cfl) {
    this.cflNumber = Math.max(0.1, Math.min(0.9, cfl));
    if (timeIntegrator != null) {
      timeIntegrator.setCflNumber(cflNumber);
    }
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
   * Enable/disable mass transfer (flashing/condensation).
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
   * Enable/disable slug tracking.
   *
   * @param enable true to enable slug tracking
   */
  public void setEnableSlugTracking(boolean enable) {
    this.enableSlugTracking = enable;
  }

  /**
   * Set thermodynamic update interval.
   *
   * @param interval Update every N time steps
   */
  public void setThermodynamicUpdateInterval(int interval) {
    this.thermodynamicUpdateInterval = Math.max(1, interval);
  }
}
