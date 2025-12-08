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

    // Determine phase fractions based on number of phases
    int numPhases = inletFluid.getNumberOfPhases();

    if (numPhases == 1) {
      // Single-phase flow - determine if gas or liquid
      if (inletFluid.hasPhaseType("gas")) {
        alphaG = 1.0;
        alphaL = 0.0;
        rhoG = inletFluid.getPhase("gas").getDensity("kg/m3");
        muG = inletFluid.getPhase("gas").getViscosity("kg/msec");
        cG = inletFluid.getPhase("gas").getSoundSpeed();
        hG = inletFluid.getPhase("gas").getEnthalpy("J/kg");
        // Set liquid properties to dummy values (won't be used)
        rhoL = rhoG;
        muL = muG;
      } else if (inletFluid.hasPhaseType("oil") || inletFluid.hasPhaseType("aqueous")) {
        alphaG = 0.0;
        alphaL = 1.0;
        String liqPhase = inletFluid.hasPhaseType("oil") ? "oil" : "aqueous";
        rhoL = inletFluid.getPhase(liqPhase).getDensity("kg/m3");
        muL = inletFluid.getPhase(liqPhase).getViscosity("kg/msec");
        cL = inletFluid.getPhase(liqPhase).getSoundSpeed();
        hL = inletFluid.getPhase(liqPhase).getEnthalpy("J/kg");
        // Set gas properties to dummy values (won't be used)
        rhoG = rhoL;
        muG = muL;
      }
    } else {
      // Two-phase or multi-phase flow
      if (inletFluid.hasPhaseType("gas")) {
        rhoG = inletFluid.getPhase("gas").getDensity("kg/m3");
        muG = inletFluid.getPhase("gas").getViscosity("kg/msec");
        cG = inletFluid.getPhase("gas").getSoundSpeed();
        hG = inletFluid.getPhase("gas").getEnthalpy("J/kg");
      }

      // Handle three-phase (gas + oil + water) or two-phase with liquid
      boolean hasOil = inletFluid.hasPhaseType("oil");
      boolean hasWater = inletFluid.hasPhaseType("aqueous");

      if (hasOil && hasWater) {
        // Three-phase flow: combine oil and water as effective liquid
        isThreePhase = true;
        rhoOil = inletFluid.getPhase("oil").getDensity("kg/m3");
        rhoWater = inletFluid.getPhase("aqueous").getDensity("kg/m3");
        muOil = inletFluid.getPhase("oil").getViscosity("kg/msec");
        muWater = inletFluid.getPhase("aqueous").getViscosity("kg/msec");
        double volOil = inletFluid.getPhase("oil").getVolume("m3");
        double volWater = inletFluid.getPhase("aqueous").getVolume("m3");
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
          sigma = inletFluid.getInterphaseProperties()
              .getSurfaceTension(inletFluid.getPhaseIndex("gas"), inletFluid.getPhaseIndex("oil"));
        }

        logger.info("Three-phase flow detected: water cut = {:.1f}%, oil fraction = {:.1f}%",
            inletWaterCut * 100, inletOilFraction * 100);

      } else if (hasOil || hasWater) {
        // Two-phase with single liquid type
        String liqPhase = hasOil ? "oil" : "aqueous";
        rhoL = inletFluid.getPhase(liqPhase).getDensity("kg/m3");
        muL = inletFluid.getPhase(liqPhase).getViscosity("kg/msec");
        cL = inletFluid.getPhase(liqPhase).getSoundSpeed();
        hL = inletFluid.getPhase(liqPhase).getEnthalpy("J/kg");
        if (inletFluid.hasPhaseType("gas")) {
          sigma = inletFluid.getInterphaseProperties().getSurfaceTension(
              inletFluid.getPhaseIndex("gas"), inletFluid.getPhaseIndex(liqPhase));
        }
      }

      // Calculate holdup from volumetric phase fractions
      if (inletFluid.hasPhaseType("gas")) {
        double volGas = inletFluid.getPhase("gas").getVolume("m3");
        double volTotal = inletFluid.getVolume("m3");
        alphaG = volGas / volTotal;
        alphaL = 1.0 - alphaG;
      }
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

      // Three-phase specific initialization
      if (isThreePhase) {
        sec.setOilDensity(rhoOil);
        sec.setWaterDensity(rhoWater);
        sec.setOilViscosity(muOil);
        sec.setWaterViscosity(muWater);
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

    logger.info("TwoFluidPipe initialized: {} sections, dx={:.2f}m", numberOfSections, dx);
  }

  /**
   * Run steady-state initialization.
   */
  private void runSteadyState() {
    // Simple steady-state: iterate until pressure and holdup profiles converge
    int maxIter = 100;
    double tolerance = 1e-4;

    // Get total mass flow rates for each phase (constant along pipe in steady state)
    double massFlow = getInletStream().getFlowRate("kg/sec");
    double area = Math.PI * diameter * diameter / 4.0;

    // Get inlet phase fractions from first section
    double inletAlphaL = sections[0].getLiquidHoldup();
    double inletAlphaG = sections[0].getGasHoldup();
    double inletRhoG = sections[0].getGasDensity();
    double inletRhoL = sections[0].getLiquidDensity();

    // Calculate phase mass flow rates (conserved in steady state)
    double rhoMixInlet = inletAlphaG * inletRhoG + inletAlphaL * inletRhoL;
    double gasQualityInlet = inletAlphaG * inletRhoG / rhoMixInlet;
    double mDotGas = massFlow * gasQualityInlet;
    double mDotLiq = massFlow * (1.0 - gasQualityInlet);

    for (int iter = 0; iter < maxIter; iter++) {
      double maxChange = 0;

      // Update flow regimes
      for (TwoFluidSection sec : sections) {
        sec.setFlowRegime(flowRegimeDetector.detectFlowRegime(sec));
      }

      // Update pressures and holdups using momentum balance
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

        // Update holdup using drift-flux model with terrain effects
        double[] newHoldups = calculateLocalHoldup(sec, prev, mDotGas, mDotLiq, area);
        double alphaL_new = newHoldups[0];
        double alphaG_new = newHoldups[1];

        // Track holdup change for convergence
        double holdupChange = Math.abs(alphaL_new - sec.getLiquidHoldup());
        maxChange = Math.max(maxChange, holdupChange);

        // Apply new holdups
        sec.setLiquidHoldup(alphaL_new);
        sec.setGasHoldup(alphaG_new);

        // Update velocities based on new holdups
        if (alphaG_new > 0.001 && sec.getGasDensity() > 0) {
          double vG = mDotGas / (area * alphaG_new * sec.getGasDensity());
          sec.setGasVelocity(vG);
        }
        if (alphaL_new > 0.001 && sec.getLiquidDensity() > 0) {
          double vL = mDotLiq / (area * alphaL_new * sec.getLiquidDensity());
          sec.setLiquidVelocity(vL);
        }

        // Update water and oil holdups for three-phase flow
        if (sec.getWaterDensity() > 0 && sec.getOilDensity() > 0 && sec.getWaterCut() > 0) {
          updateWaterOilHoldups(sec, prev, alphaL_new, area);
        }

        // Update derived quantities
        sec.updateDerivedQuantities();
        sec.updateStratifiedGeometry();
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
   * Calculate local liquid holdup using drift-flux model with terrain effects.
   *
   * <p>
   * Uses the drift-flux model: v_G = C_0 * v_m + v_gj where:
   * <ul>
   * <li>C_0 = distribution coefficient (~1.0-1.2 for stratified flow)</li>
   * <li>v_gj = drift velocity (gas rises relative to mixture)</li>
   * </ul>
   * Terrain effects modify slip velocity based on inclination.
   *
   * @param sec Current section
   * @param prev Previous section (upstream)
   * @param mDotGas Gas mass flow rate [kg/s]
   * @param mDotLiq Liquid mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   * @return Array with [liquidHoldup, gasHoldup]
   */
  private double[] calculateLocalHoldup(TwoFluidSection sec, TwoFluidSection prev, double mDotGas,
      double mDotLiq, double area) {

    double rhoG = sec.getGasDensity();
    double rhoL = sec.getLiquidDensity();
    double sigma = sec.getSurfaceTension();
    double inclination = sec.getInclination(); // radians
    double g = 9.81;

    // Handle single-phase cases
    if (mDotLiq < 1e-10) {
      return new double[] {0.0, 1.0}; // Pure gas
    }
    if (mDotGas < 1e-10) {
      return new double[] {1.0, 0.0}; // Pure liquid
    }

    // Superficial velocities (based on total area)
    double vsG = mDotGas / (area * rhoG);
    double vsL = mDotLiq / (area * rhoL);
    double vMix = vsG + vsL;

    // No-slip holdup (input liquid fraction)
    double lambdaL = vsL / vMix;

    // Drift-flux parameters
    // Distribution coefficient C0 depends on flow regime
    double C0 = 1.2; // Typical for stratified/slug flow in pipes

    // Drift velocity based on Bendiksen (1984) correlation for inclined pipes
    // v_gj = v_gj0 * f(theta) where f(theta) accounts for inclination
    double dRho = rhoL - rhoG;

    // Terminal rise velocity (Harmathy, 1960)
    double vGj0 = 1.53 * Math.pow(g * sigma * dRho / (rhoL * rhoL), 0.25);

    // Inclination correction factor
    // For uphill (positive inclination): gas rises faster -> higher slip -> more holdup
    // For downhill (negative inclination): gravity pulls liquid down -> less holdup
    double sinTheta = Math.sin(inclination);
    double cosTheta = Math.cos(inclination);

    // Bendiksen inclination factor for drift velocity
    double fTheta;
    if (inclination >= 0) {
      // Uphill: enhanced drift velocity (liquid tends to accumulate)
      fTheta = cosTheta + 1.2 * sinTheta;
    } else {
      // Downhill: reduced drift velocity (liquid drains)
      fTheta = cosTheta + 0.3 * Math.abs(sinTheta);
    }
    fTheta = Math.max(0.1, fTheta); // Minimum factor

    double vGj = vGj0 * fTheta;

    // Pipe diameter effect on drift velocity (larger pipes -> higher drift)
    double Eo = g * dRho * diameter * diameter / sigma; // Eötvös number
    if (Eo > 40) {
      // Large pipe correction (slug flow in large pipes)
      vGj = 0.35 * Math.sqrt(g * diameter * dRho / rhoL) * fTheta;
    }

    // Froude number effect - at high velocities, slip decreases
    double Fr = vMix / Math.sqrt(g * diameter);
    double slipReduction = 1.0 / (1.0 + 0.1 * Fr);
    vGj *= slipReduction;

    // Calculate gas holdup from drift-flux relation
    // v_G = C0 * v_m + v_gj
    // alpha_G * v_G = vsG
    // Solving: alpha_G = vsG / (C0 * vMix + vGj)
    double alphaG = vsG / (C0 * vMix + vGj);
    alphaG = Math.max(0.001, Math.min(0.999, alphaG));
    double alphaL = 1.0 - alphaG;

    // Terrain accumulation effect for valleys
    // In valleys (going from downhill to uphill), liquid accumulates
    if (prev != null) {
      double prevInclination = prev.getInclination();
      double inclinationChange = inclination - prevInclination;

      // Transition from downhill to uphill -> valley -> liquid accumulates
      if (inclinationChange > 0.01 && prevInclination < -0.01) {
        // Valley effect: increase liquid holdup
        double valleyFactor = 1.0 + 0.5 * Math.min(inclinationChange, 0.2);
        alphaL = Math.min(0.999, alphaL * valleyFactor);
        alphaG = 1.0 - alphaL;
      }

      // Transition from uphill to downhill -> peak -> liquid drains
      if (inclinationChange < -0.01 && prevInclination > 0.01) {
        // Peak effect: decrease liquid holdup
        double peakFactor = 1.0 - 0.3 * Math.min(Math.abs(inclinationChange), 0.2);
        alphaL = Math.max(0.001, alphaL * peakFactor);
        alphaG = 1.0 - alphaL;
      }
    }

    return new double[] {alphaL, alphaG};
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

  /**
   * Update water and oil holdups for three-phase flow with terrain effects.
   *
   * <p>
   * Water is denser than oil, so it tends to accumulate in valleys (low spots) more than oil. This
   * method calculates the local water cut which can vary along the pipe based on:
   * <ul>
   * <li>Gravity segregation: water settles faster in low-velocity regions</li>
   * <li>Terrain effects: water accumulates more in valleys</li>
   * <li>Slip between oil and water phases</li>
   * </ul>
   * </p>
   *
   * @param sec Current section
   * @param prev Previous section (upstream)
   * @param alphaL Total liquid holdup
   * @param area Pipe cross-sectional area
   */
  private void updateWaterOilHoldups(TwoFluidSection sec, TwoFluidSection prev, double alphaL,
      double area) {
    double rhoOil = sec.getOilDensity();
    double rhoWater = sec.getWaterDensity();
    double muOil = sec.getOilViscosity();
    double muWater = sec.getWaterViscosity();
    double g = 9.81;

    // Get previous water cut for continuity
    double prevWaterCut = (prev != null) ? prev.getWaterCut() : sec.getWaterCut();
    double inclination = sec.getInclination();
    double sinTheta = Math.sin(inclination);

    // Base water cut from previous section (advected with liquid)
    double waterCut = prevWaterCut;

    // Density difference drives water settling
    double deltaRho = rhoWater - rhoOil; // Positive: water is heavier

    if (deltaRho > 0 && alphaL > 0.01) {
      // Stokes settling velocity for water droplets in oil (or vice versa)
      // Simplified model: settling depends on local holdup and inclination
      double dropletDiameter = 0.001; // 1 mm typical droplet size
      double stokesVelocity = deltaRho * g * dropletDiameter * dropletDiameter / (18 * muOil);

      // Terrain effect on water accumulation
      // Negative inclination (downhill) + transition to uphill = valley = water accumulates
      double terrainFactor = 1.0;

      if (prev != null) {
        double prevInclination = prev.getInclination();
        double inclinationChange = inclination - prevInclination;

        // Valley detection: transition from downhill to uphill
        if (inclinationChange > 0.005 && prevInclination < -0.005) {
          // Strong valley effect for water - water accumulates more than oil
          terrainFactor = 1.0 + 0.3 * Math.min(inclinationChange, 0.3);
        }

        // Peak detection: transition from uphill to downhill
        if (inclinationChange < -0.005 && prevInclination > 0.005) {
          // Water drains from peaks faster than oil
          terrainFactor = 1.0 - 0.2 * Math.min(Math.abs(inclinationChange), 0.3);
        }
      }

      // In uphill sections, water tends to slip back more than oil
      if (sinTheta > 0.01) {
        // Uphill: water accumulates (higher water cut)
        double upfactor = 1.0 + 0.1 * sinTheta * (stokesVelocity / sec.getLiquidVelocity());
        terrainFactor *= Math.min(upfactor, 1.5);
      } else if (sinTheta < -0.01) {
        // Downhill: water flows faster (lower local water cut as it drains ahead)
        double downFactor = 1.0 - 0.05 * Math.abs(sinTheta);
        terrainFactor *= Math.max(downFactor, 0.7);
      }

      // Apply terrain factor to water cut
      waterCut = prevWaterCut * terrainFactor;
    }

    // Clamp water cut to valid range
    waterCut = Math.max(0, Math.min(1, waterCut));

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

    // Update combined liquid properties based on new water cut
    sec.updateThreePhaseProperties();
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

    // Number of sub-steps (use more sub-steps for stability)
    int subSteps = (int) Math.ceil(dt / dtActual);
    subSteps = Math.max(subSteps, 2); // At least 2 sub-steps for stability
    dtActual = dt / subSteps;

    for (int step = 0; step < subSteps; step++) {
      // 1. Update thermodynamic properties (periodically)
      currentStep++;
      if (currentStep % thermodynamicUpdateInterval == 0) {
        updateThermodynamics();
      }

      // 2. Store previous state for stability check
      double[][] U_prev = equations.extractState(sections);

      // 3. Calculate RHS and advance solution
      final double dtFinal = dtActual;

      TimeIntegrator.RHSFunction rhs = (state, t) -> {
        equations.applyState(sections, state);
        return equations.calcRHS(sections, dx);
      };

      double[][] U_new = timeIntegrator.step(U_prev, rhs, dtFinal);

      // 4. Validate and correct new state before applying
      validateAndCorrectState(U_new, U_prev);

      equations.applyState(sections, U_new);

      // 5. Apply pressure gradient (semi-implicit for stability)
      double[][] dUdt = equations.calcRHS(sections, dx);
      equations.applyPressureGradient(sections, dUdt, dx);

      // 6. Apply boundary conditions
      applyBoundaryConditions();

      // 7. Validate section states and fix any issues
      validateSectionStates();

      // 8. Update accumulation tracking
      if (enableSlugTracking) {
        accumulationTracker.updateAccumulation(sections, dtActual);
      }

      // 9. Advance time
      simulationTime += dtActual;
      timeIntegrator.advanceTime(dtActual);
    }

    // Update outlet stream and result arrays
    updateOutletStream();
    updateResultArrays();

    setCalculationIdentifier(id);
  }

  /**
   * Validate and correct state vector to prevent numerical instabilities.
   * 
   * @param U_new New state to validate
   * @param U_prev Previous state for fallback
   */
  private void validateAndCorrectState(double[][] U_new, double[][] U_prev) {
    for (int i = 0; i < U_new.length; i++) {
      for (int j = 0; j < U_new[i].length; j++) {
        // Check for NaN or Inf
        if (Double.isNaN(U_new[i][j]) || Double.isInfinite(U_new[i][j])) {
          U_new[i][j] = U_prev[i][j]; // Revert to previous value
        }
      }

      // Ensure mass variables are non-negative
      U_new[i][0] = Math.max(0, U_new[i][0]); // Gas mass
      U_new[i][1] = Math.max(0, U_new[i][1]); // Liquid mass

      // Limit extreme rate of change to prevent blow-up (50% per sub-step max)
      // This is a safety limit, not for physics - allows transient dynamics
      double maxChangeRatio = 0.5;
      for (int j = 0; j < 2; j++) { // Only for mass variables
        if (U_prev[i][j] > 1e-10) {
          double ratio = U_new[i][j] / U_prev[i][j];
          if (ratio > 1 + maxChangeRatio) {
            U_new[i][j] = U_prev[i][j] * (1 + maxChangeRatio);
          } else if (ratio < 1 - maxChangeRatio && ratio > 0) {
            U_new[i][j] = U_prev[i][j] * (1 - maxChangeRatio);
          }
        }
      }
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
      // Ensure holdups are valid
      double alphaL = sec.getLiquidHoldup();
      double alphaG = sec.getGasHoldup();

      if (Double.isNaN(alphaL) || alphaL < 0 || alphaL > 1) {
        alphaL = 0.3; // Default holdup
      }
      if (Double.isNaN(alphaG) || alphaG < 0 || alphaG > 1) {
        alphaG = 1.0 - alphaL;
      }

      // Normalize
      double total = alphaL + alphaG;
      if (total > 0) {
        sec.setLiquidHoldup(alphaL / total);
        sec.setGasHoldup(alphaG / total);
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

        // Handle liquid phases (oil, water, or both)
        boolean hasOil = flash.hasPhaseType("oil");
        boolean hasWater = flash.hasPhaseType("aqueous");

        if (hasOil && hasWater) {
          // Three-phase: combine oil and water as effective liquid
          double rhoOil = flash.getPhase("oil").getDensity("kg/m3");
          double rhoWater = flash.getPhase("aqueous").getDensity("kg/m3");
          double muOil = flash.getPhase("oil").getViscosity("kg/msec");
          double muWater = flash.getPhase("aqueous").getViscosity("kg/msec");
          double volOil = flash.getPhase("oil").getVolume("m3");
          double volWater = flash.getPhase("aqueous").getVolume("m3");
          double volLiquid = volOil + volWater;

          double waterCut = volLiquid > 0 ? volWater / volLiquid : 0;
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
        } else if (hasWater) {
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
   * Get water cut profile along the pipeline.
   *
   * <p>
   * For three-phase flow, water cut can vary along the pipeline as water accumulates in low spots
   * (valleys) due to its higher density compared to oil.
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
