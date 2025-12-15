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

  /** Enable heat transfer from surroundings. */
  private boolean enableHeatTransfer = false;

  /** Surface temperature for heat transfer (K). */
  private double surfaceTemperature = 288.15;

  /** Heat transfer coefficient (W/(m²·K)). */
  private double heatTransferCoefficient = 0.0;

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

  /** Enable Joule-Thomson effect. */
  private boolean enableJouleThomson = true;

  /** Hydrate formation temperature (K). */
  private double hydrateFormationTemperature = 0.0;

  /** Wax appearance temperature (K). */
  private double waxAppearanceTemperature = 0.0;

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
          // Initialize interfacial properties before getting surface tension
          inletFluid.getInterphaseProperties().init(inletFluid);
          try {
            double sigmaCalc = inletFluid.getInterphaseProperties().getSurfaceTension(
                inletFluid.getPhaseIndex("gas"), inletFluid.getPhaseIndex("oil"));
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
            logger.warn(
                "Interfacial tension calculation failed. Using default gas-oil IFT: {} N/m. Error: {}",
                sigma, e.getMessage());
          }
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
          // Initialize interfacial properties before getting surface tension
          inletFluid.getInterphaseProperties().init(inletFluid);
          try {
            double sigmaCalc = inletFluid.getInterphaseProperties().getSurfaceTension(
                inletFluid.getPhaseIndex("gas"), inletFluid.getPhaseIndex(liqPhase));
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
            logger.warn(
                "Interfacial tension calculation failed. Using default gas-{} IFT: {} N/m. Error: {}",
                liqPhase, sigma, e.getMessage());
          }
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

    // Get inlet pressure - this is a boundary condition
    double P_inlet = getInletStream().getFluid().getPressure("Pa");

    // Fix inlet section pressure to boundary condition
    sections[0].setPressure(P_inlet);

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
        // Check if this is a three-phase system (both oil and water densities set)
        if (sec.getWaterDensity() > 0 && sec.getOilDensity() > 0) {
          // Always update water/oil holdups when we have liquid and three-phase properties
          if (alphaL_new > 0.001) {
            updateWaterOilHoldups(sec, prev, alphaL_new, area);
          } else {
            // No liquid: set water and oil holdups to zero
            sec.setWaterHoldup(0);
            sec.setOilHoldup(0);
            sec.setWaterCut(prev != null ? prev.getWaterCut() : sec.getWaterCut());
          }
        }

        // Update derived quantities
        sec.updateDerivedQuantities();
        sec.updateStratifiedGeometry();
      }

      // Update temperature profile if heat transfer is enabled
      if (enableHeatTransfer && heatTransferCoefficient > 0) {
        updateTemperatureProfile(massFlow, area);
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
   * Update temperature profile along the pipe accounting for heat transfer.
   *
   * <p>
   * Steady-state energy balance: m_dot * Cp * dT/dx = -h * π * D * (T - T_surface) - μ_JT * dP/dx
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
        // μ_JT = (1/Cp) * [T*(dV/dT)_P - V] ≈ (T*β - 1)*V/Cp for ideal gas approximation
        // For real gas, use thermodynamic calculation
        double kappa = inletFluid.getKappa(); // Cp/Cv
        if (kappa > 1.0 && kappa < 2.0) {
          double T = inletFluid.getTemperature();
          double Z = inletFluid.getZ();
          double MW = inletFluid.getMolarMass() * 1000; // kg/kmol to g/mol
          double R = 8.314; // J/(mol·K)
          // Simplified J-T coefficient for real gas: μ_JT ≈ (2a/RT - b) / Cp
          // For typical natural gas: 0.3-0.6 K/bar
          muJT = 0.4 / 1e5; // K/Pa (typical for natural gas)
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
      double dT_JT = muJT * dP; // Temperature change due to J-T effect

      // Heat transfer calculation with exponential solution
      double T_new;
      if (h > 0 && massFlow > 0 && Cp > 0) {
        // Exponential decay solution for segment:
        // T(x) = T_surface + (T_inlet - T_surface) * exp(-h*π*D*dx / (m_dot*Cp))
        double exponent = -h * pipePerimeter * dx / (massFlow * Cp);
        T_new = T_surface + (T_prev - T_surface) * Math.exp(exponent);
      } else {
        T_new = T_prev;
      }

      // Add Joule-Thomson effect
      T_new += dT_JT;

      // Ensure physical bounds
      if (h > 0) {
        if (T_prev > T_surface) {
          T_new = Math.max(T_new, T_surface); // Cannot cool below ambient
          T_new = Math.min(T_new, T_prev); // Cannot heat up when cooling
        } else {
          T_new = Math.min(T_new, T_surface); // Cannot heat above ambient
          T_new = Math.max(T_new, T_prev); // Cannot cool when heating
        }
      }
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
   * Update temperature profile for transient simulation including pipe wall thermal mass.
   *
   * <p>
   * Solves coupled fluid-wall energy equations:
   * <ul>
   * <li>Fluid: ρ_f * Cp_f * A * dT_f/dt = -m_dot * Cp_f * dT_f/dx - h_i * π * D * (T_f - T_w)</li>
   * <li>Wall: ρ_w * Cp_w * A_w * dT_w/dt = h_i * π * D * (T_f - T_w) - h_o * π * D_o * (T_w -
   * T_amb)</li>
   * </ul>
   *
   * @param massFlow Total mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   * @param dt Time step [s]
   */
  private void updateTransientTemperature(double massFlow, double area, double dt) {
    // Get mixture heat capacity from inlet fluid
    SystemInterface inletFluid = getInletStream().getFluid();
    double Cp = inletFluid.getCp("J/kgK");
    if (Cp <= 0 || Double.isNaN(Cp)) {
      Cp = 2000.0; // Default if not available
    }

    // Initialize wall temperature profile if needed
    if (wallTemperatureProfile == null || wallTemperatureProfile.length != numberOfSections) {
      wallTemperatureProfile = new double[numberOfSections];
      for (int i = 0; i < numberOfSections; i++) {
        wallTemperatureProfile[i] = sections[i].getTemperature();
      }
    }

    // Initialize hydrate/wax risk arrays
    if (hydrateRiskSections == null || hydrateRiskSections.length != numberOfSections) {
      hydrateRiskSections = new boolean[numberOfSections];
      waxRiskSections = new boolean[numberOfSections];
    }

    double pipePerimeter = Math.PI * diameter;
    double outerDiameter = diameter + 2 * wallThickness;
    double outerPerimeter = Math.PI * outerDiameter;

    // Wall cross-sectional area
    double wallArea = Math.PI * (outerDiameter * outerDiameter - diameter * diameter) / 4.0;
    double wallMassPerLength = wallArea * wallDensity; // kg/m

    // Fluid properties per unit length
    double fluidDensity = (inletFluid.getDensity("kg/m3"));
    double fluidMassPerLength = area * fluidDensity;

    // Get Joule-Thomson coefficient if enabled
    double muJT = 0.0;
    if (enableJouleThomson) {
      muJT = 0.4 / 1e5; // K/Pa (typical for natural gas)
    }

    for (int i = 1; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      TwoFluidSection prev = sections[i - 1];

      double T_fluid = sec.getTemperature();
      double T_wall = wallTemperatureProfile[i];

      // Get local heat transfer coefficient (profile or constant)
      double h_inner = heatTransferCoefficient;
      if (heatTransferProfile != null && i < heatTransferProfile.length) {
        h_inner = heatTransferProfile[i];
      }

      // Get local surface temperature (profile or constant)
      double T_ambient = surfaceTemperature;
      if (surfaceTemperatureProfile != null && i < surfaceTemperatureProfile.length) {
        T_ambient = surfaceTemperatureProfile[i];
      }

      // Outer heat transfer coefficient (including soil resistance if applicable)
      double h_outer = h_inner;
      if (soilThermalResistance > 0 && h_inner > 0) {
        h_outer = 1.0 / (1.0 / h_inner + soilThermalResistance);
      }

      // Heat transfer rates per unit length
      double Q_fluid_to_wall = h_inner * pipePerimeter * (T_fluid - T_wall); // W/m
      double Q_wall_to_ambient = h_outer * outerPerimeter * (T_wall - T_ambient); // W/m

      // Advection term: m_dot * Cp * (T_in - T_out) / dx
      double T_upstream = prev.getTemperature();
      double Q_advection = massFlow * Cp * (T_upstream - T_fluid) / dx; // W/m

      // Joule-Thomson cooling
      double dP = sec.getPressure() - prev.getPressure();
      double Q_JT = massFlow * Cp * muJT * dP / dx; // W/m (equivalent heat)

      // Update wall temperature (explicit Euler)
      double dTwall_dt =
          (Q_fluid_to_wall - Q_wall_to_ambient) / (wallMassPerLength * wallHeatCapacity);
      T_wall += dTwall_dt * dt;
      wallTemperatureProfile[i] = T_wall;

      // Update fluid temperature (explicit Euler with advection)
      double dTfluid_dt = (Q_advection - Q_fluid_to_wall + Q_JT) / (fluidMassPerLength * Cp);
      T_fluid += dTfluid_dt * dt;

      // Ensure physical bounds
      // The fluid temperature cannot go below ambient due to heat exchange alone
      // J-T cooling is separate but in real systems is limited
      // For subsea pipelines, the minimum temperature is the seabed temperature
      // unless there's very rapid expansion (which is not the case in steady pipeline flow)
      T_fluid = Math.max(T_fluid, T_ambient);
      T_fluid = Math.max(T_fluid, 100.0); // Absolute minimum: 100K
      sec.setTemperature(T_fluid);

      // Check hydrate/wax risk
      if (hydrateFormationTemperature > 0 && T_fluid < hydrateFormationTemperature) {
        hydrateRiskSections[i] = true;
      } else {
        hydrateRiskSections[i] = false;
      }
      if (waxAppearanceTemperature > 0 && T_fluid < waxAppearanceTemperature) {
        waxRiskSections[i] = true;
      } else {
        waxRiskSections[i] = false;
      }
    }
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
   *
   * <p>
   * Uses Haaland equation (explicit approximation to Colebrook-White) for friction factor,
   * consistent with AdiabaticPipe and PipeBeggsAndBrills.
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

    // Mixture density
    double rhoMix = alphaG * rhoG + alphaL * rhoL;

    // Mixture velocity and viscosity
    double vMix = alphaG * vG + alphaL * vL;
    double muMix = alphaG * sec.getGasViscosity() + alphaL * sec.getLiquidViscosity();

    // Reynolds number
    double Re = rhoMix * Math.abs(vMix) * diameter / muMix;

    // Calculate Darcy friction factor using Haaland equation (same as other pipe models)
    double f_Darcy;
    double relativeRoughness = roughness / diameter;

    if (Re < 1e-10) {
      f_Darcy = 0;
    } else if (Re < 2300) {
      // Laminar flow
      f_Darcy = 64.0 / Re;
    } else if (Re < 4000) {
      // Transition region - interpolate
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow(
          1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughness / 3.7, 1.11))), 2.0);
      f_Darcy = fLaminar + (fTurbulent - fLaminar) * (Re - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      f_Darcy = Math
          .pow(1.0 / (-1.8 * Math.log10(6.9 / Re + Math.pow(relativeRoughness / 3.7, 1.11))), 2.0);
    }

    // Darcy-Weisbach: dP/dx = f * rho * v^2 / (2 * D)
    double dPdx_fric = f_Darcy * rhoMix * vMix * Math.abs(vMix) / (2.0 * diameter);

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
  private void updateWaterOilHoldups(TwoFluidSection sec, TwoFluidSection prev, double alphaL,
      double area) {
    double rhoOil = sec.getOilDensity();
    double rhoWater = sec.getWaterDensity();
    double muOil = sec.getOilViscosity();
    double g = 9.81;
    double inclination = sec.getInclination();
    double sinTheta = Math.sin(inclination);
    double deltaRho = rhoWater - rhoOil; // Positive: water is heavier

    // Get previous water cut for continuity
    double prevWaterCut = (prev != null) ? prev.getWaterCut() : sec.getWaterCut();

    // Simplified model: water cut is advected from upstream without terrain-induced settling.
    // This assumes water and oil are well-mixed within the liquid phase.
    // A more sophisticated model would track water/oil separation dynamics explicitly,
    // but the terrain factor approach causes numerical instabilities.
    double waterCut = prevWaterCut;

    // Clamp water cut to valid range
    waterCut = Math.max(0.001, Math.min(0.999, waterCut)); // Keep small margin to avoid numerical
                                                           // issues

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

    // Calculate water-oil velocity slip
    // In steady state, conservation of mass gives:
    // m_dot_water = rho_w * alpha_w * A * v_w (constant)
    // m_dot_oil = rho_o * alpha_o * A * v_o (constant)
    // But with slip, v_w != v_o

    double vL = sec.getLiquidVelocity();
    double vOil = vL;
    double vWater = vL;

    if (isWaterOilSlipEnabled() && deltaRho > 0 && alphaL > 0.02 && alphaW > 0.005
        && alphaO > 0.005) {
      // Calculate slip velocity based on density difference and inclination
      // Use simplified drift-flux model for oil-water
      double dropletDiameter = 0.001; // 1 mm average droplet (reduced from 2mm)
      double stokesSettling = deltaRho * g * dropletDiameter * dropletDiameter / (18 * muOil);

      // Limit slip to a fraction of liquid velocity (physical constraint)
      // In turbulent flow, slip is limited by turbulent mixing
      double maxSlip = 0.3 * vL; // Maximum 30% of liquid velocity
      stokesSettling = Math.min(stokesSettling, maxSlip);

      // Slip is enhanced in inclined flow, but moderated
      double slipEnhancement = 1.0;
      if (Math.abs(sinTheta) > 0.01) {
        // In inclined flow, slip is enhanced by gravity component
        slipEnhancement = 1.0 + 0.3 * Math.abs(sinTheta);
      }

      double slipVelocity = stokesSettling * slipEnhancement;

      // In uphill flow: water slips back (slower than oil)
      // In downhill flow: water moves ahead (faster than oil due to density)
      if (sinTheta > 0) {
        // Uphill: oil faster, water slower
        vWater = vL - slipVelocity * (1.0 - waterCut);
        vOil = vL + slipVelocity * waterCut;
      } else if (sinTheta < 0) {
        // Downhill: water faster (gravity pulls heavier phase down), oil slower
        vWater = vL + slipVelocity * (1.0 - waterCut);
        vOil = vL - slipVelocity * waterCut;
      }

      // Ensure velocities stay positive for forward flow
      vWater = Math.max(0.1 * vL, vWater);
      vOil = Math.max(0.1 * vL, vOil);
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

      // 8. Update accumulation tracking and slug tracking
      if (enableSlugTracking) {
        accumulationTracker.updateAccumulation(sections, dtActual);

        // Check for terrain-induced slug initiation from accumulation zones
        for (LiquidAccumulationTracker.AccumulationZone zone : accumulationTracker
            .getAccumulationZones()) {
          LiquidAccumulationTracker.SlugCharacteristics slugChar =
              accumulationTracker.checkForSlugRelease(zone, sections);
          if (slugChar != null) {
            slugTracker.initializeTerrainSlug(slugChar, sections);
          }
        }

        // Set reference velocity for slug propagation (from inlet)
        double inletMixtureVelocity = sections[0].getMixtureVelocity();
        slugTracker.setReferenceVelocity(inletMixtureVelocity);

        // Advance existing slugs through the pipeline
        slugTracker.advanceSlugs(sections, dtActual);

        // Track slugs arriving at outlet
        trackOutletSlugs();
      }

      // 9. Update temperature profile if heat transfer is enabled
      if (enableHeatTransfer && heatTransferCoefficient > 0) {
        double massFlow = getInletStream().getFlowRate("kg/sec");
        double area = Math.PI * diameter * diameter / 4.0;
        updateTransientTemperature(massFlow, area, dtActual);
      }

      // 10. Advance time
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

      // Ensure mass variables are non-negative (indices 0, 1, 2 for gas, oil, water)
      U_new[i][0] = Math.max(0, U_new[i][0]); // Gas mass
      U_new[i][1] = Math.max(0, U_new[i][1]); // Oil mass
      if (U_new[i].length > 2) {
        U_new[i][2] = Math.max(0, U_new[i][2]); // Water mass
      }

      // Limit extreme rate of change to prevent blow-up (50% per sub-step max)
      // This is a safety limit, not for physics - allows transient dynamics
      double maxChangeRatio = 0.5;
      // Apply to all mass variables: gas (0), oil (1), water (2)
      int numMassVars = Math.min(3, U_new[i].length);
      for (int j = 0; j < numMassVars; j++) {
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
      if (diff > 0.01) {
        // Determine which source to trust
        if (sumOilWater > 0.001) {
          // We have oil and/or water holdups - use them as the liquid holdup
          double newLiqHL = sumOilWater;
          double newGasHL = Math.max(0, Math.min(1, 1.0 - newLiqHL));
          sec.setLiquidHoldup(newLiqHL);
          sec.setGasHoldup(newGasHL);
        } else if (sec.getLiquidHoldup() > 0.001) {
          // We have liquid holdup but no oil/water - distribute based on water cut
          double waterCut = sec.getWaterCut();
          if (waterCut <= 0 || waterCut >= 1) {
            waterCut = 0.5; // Default to 50/50 if no valid water cut
          }
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
        } else if (hasWater) {
          double rhoWater = flash.getPhase("aqueous").getDensity("kg/m3");
          sec.setLiquidDensity(rhoWater);
          sec.setWaterDensity(rhoWater);
          sec.setWaterViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
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
      // Use inlet stream properties - maintain pressure, temperature
      SystemInterface inFluid = getInletStream().getFluid();
      inlet.setPressure(inFluid.getPressure("Pa"));
      inlet.setTemperature(inFluid.getTemperature("K"));

      // Calculate target mass flow rates from inlet stream
      double massFlow = getInletStream().getFlowRate("kg/sec");
      double area = Math.PI * diameter * diameter / 4.0;

      // Get phase mass fractions from inlet stream (these define the BC)
      double gasMassFraction = 0.5;
      double oilMassFraction = 0.3;
      double waterMassFraction = 0.2;

      int numPhases = inFluid.getNumberOfPhases();
      if (numPhases >= 2) {
        double massTotal = inFluid.getFlowRate("kg/sec");
        if (massTotal > 0) {
          if (inFluid.hasPhaseType("gas")) {
            gasMassFraction = inFluid.getPhase("gas").getFlowRate("kg/sec") / massTotal;
          }
          if (inFluid.hasPhaseType("oil")) {
            oilMassFraction = inFluid.getPhase("oil").getFlowRate("kg/sec") / massTotal;
          } else {
            oilMassFraction = 0;
          }
          if (inFluid.hasPhaseType("aqueous")) {
            waterMassFraction = inFluid.getPhase("aqueous").getFlowRate("kg/sec") / massTotal;
          } else {
            waterMassFraction = 0;
          }
        }
      }

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
      double vG = 10.0; // Default gas velocity
      double vL = 2.0; // Default liquid velocity
      double vOil = vL;
      double vWater = vL;

      if (alphaG > 0.001 && rhoG > 0.1 && area > 0) {
        vG = mDotGas / (alphaG * rhoG * area);
        vG = Math.min(vG, 100.0); // Limit to reasonable velocity
      }
      if (alphaL > 0.001 && area > 0) {
        double rhoL = inlet.getLiquidDensity() > 100 ? inlet.getLiquidDensity() : 700.0;
        vL = mDotLiq / (alphaL * rhoL * area);
        vL = Math.min(vL, 50.0); // Limit to reasonable velocity
        vOil = vL;
        vWater = vL;
      }

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
          double volOil = inFluid.getPhase("oil").getVolume("m3");
          double volWater = inFluid.getPhase("aqueous").getVolume("m3");
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
      inlet.setWaterCut(inletWaterCut);
      inlet.setOilFractionInLiquid(1.0 - inletWaterCut);
      inlet.setWaterHoldup(alphaW_target);
      inlet.setOilHoldup(alphaO_target);

      // Update mass per length to be consistent with holdups
      inlet.setWaterMassPerLength(alphaW_target * rhoWater * area);
      inlet.setOilMassPerLength(alphaO_target * rhoOil * area);

      // Update momentum to be consistent with velocities
      // Note: We do NOT reset the mass per length here - that would violate mass conservation
      // The solver evolves the mass, we only set velocities for flux calculation
      inlet.setGasMomentumPerLength(inlet.getGasMassPerLength() * inlet.getGasVelocity());
      inlet.setOilMomentumPerLength(inlet.getOilMassPerLength() * inlet.getOilVelocity());
      inlet.setWaterMomentumPerLength(inlet.getWaterMassPerLength() * inlet.getWaterVelocity());
      inlet.setLiquidMomentumPerLength(inlet.getLiquidMassPerLength() * inlet.getLiquidVelocity());
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

    // Calculate outlet mass flow rate from section state
    double area = Math.PI * diameter * diameter / 4.0;
    double alphaG = outlet.getGasHoldup();
    double alphaL = outlet.getLiquidHoldup();
    double rhoG = outlet.getGasDensity();
    double rhoL = outlet.getLiquidDensity();
    double vG = outlet.getGasVelocity();
    double vL = outlet.getLiquidVelocity();

    // Mass flow = (gas mass flux + liquid mass flux) * area
    double massFlowOut = (alphaG * rhoG * vG + alphaL * rhoL * vL) * area;

    // Ensure positive flow
    if (massFlowOut > 0) {
      outFluid.setTotalFlowRate(massFlowOut, "kg/sec");
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
   * <p>
   * Calculates inventory from conservative mass per length, converted to volume using local liquid
   * density. This ensures consistency with the solver's mass tracking.
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
   * <p>
   * For consistency with oil and water holdups, the liquid holdup is calculated as the sum of oil
   * and water holdups.
   * </p>
   *
   * @return Holdup at each section (0-1)
   */
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
      // Use sum if it's reasonable, otherwise use stored liquid holdup
      if (sumOilWater > 0.001) {
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
   * Get oil velocity profile along the pipeline.
   *
   * <p>
   * When water-oil slip is enabled, this returns the independent oil velocity. Otherwise, it
   * returns the combined liquid velocity.
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
   * When water-oil slip is enabled, this returns the independent water velocity. Otherwise, it
   * returns the combined liquid velocity.
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
   * Returns the difference between oil and water velocities (vOil - vWater). Positive values
   * indicate oil is flowing faster than water.
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

  /**
   * Track slugs arriving at outlet and collect statistics. Each slug is only counted once when it
   * first reaches the outlet region.
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
    sb.append(String.format("Active slugs in pipe: %d\n", slugTracker.getSlugCount()));
    sb.append(String.format("Slugs generated: %d\n", slugTracker.getTotalSlugsGenerated()));
    sb.append(String.format("Slugs merged: %d\n", slugTracker.getTotalSlugsMerged()));
    sb.append(String.format("Slugs at outlet: %d\n", outletSlugCount));
    sb.append(String.format("Total slug volume at outlet: %.2f m³\n", totalSlugVolumeAtOutlet));
    sb.append(String.format("Max slug length at outlet: %.1f m\n", maxSlugLengthAtOutlet));
    sb.append(String.format("Max slug volume at outlet: %.3f m³\n", maxSlugVolumeAtOutlet));
    if (outletSlugCount > 0 && simulationTime > 0) {
      double avgFrequency = outletSlugCount / simulationTime;
      sb.append(String.format("Average slug frequency: %.4f Hz (%.1f min between slugs)\n",
          avgFrequency, avgFrequency > 0 ? 1.0 / (avgFrequency * 60) : 0));
    }
    return sb.toString();
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
   * @param cfl CFL number (0 &lt; cfl &lt; 1)
   */
  public void setCflNumber(double cfl) {
    this.cflNumber = Math.max(0.1, Math.min(0.9, cfl));
    if (timeIntegrator != null) {
      timeIntegrator.setCflNumber(cflNumber);
    }
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
   * Enables heat transfer modeling. The pipe loses/gains heat to reach this temperature.
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
      throw new IllegalArgumentException(
          "Unsupported temperature unit: " + unit + ". Use 'K' or 'C'.");
    }
    this.enableHeatTransfer = true;
    this.includeEnergyEquation = true;
    if (equations != null) {
      equations.setIncludeEnergyEquation(true);
      equations.setSurfaceTemperature(this.surfaceTemperature);
      equations.setEnableHeatTransfer(true);
    }
  }

  /**
   * Set heat transfer coefficient for convective heat transfer.
   *
   * <p>
   * Heat transfer rate: Q = h * A * (T_pipe - T_surface)<br>
   * where h = heat transfer coefficient (W/(m²·K))<br>
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
   * @param heatTransferCoefficient Heat transfer coefficient in W/(m²·K)
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient) {
    if (heatTransferCoefficient < 0) {
      throw new IllegalArgumentException(
          "Heat transfer coefficient must be non-negative: " + heatTransferCoefficient);
    }
    this.heatTransferCoefficient = heatTransferCoefficient;
    this.enableHeatTransfer = heatTransferCoefficient > 0;
    if (heatTransferCoefficient > 0) {
      this.includeEnergyEquation = true;
      if (equations != null) {
        equations.setIncludeEnergyEquation(true);
        equations.setHeatTransferCoefficient(heatTransferCoefficient);
        equations.setEnableHeatTransfer(true);
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
   * Enable water-oil velocity slip modeling.
   *
   * <p>
   * When enabled, uses 7-equation model with separate oil and water momentum equations, allowing
   * water and oil phases to flow at different velocities. This is important for:
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
   * Set thermodynamic update interval.
   *
   * @param interval Update every N time steps
   */
  public void setThermodynamicUpdateInterval(int interval) {
    this.thermodynamicUpdateInterval = Math.max(1, interval);
  }

  // ============ New Heat Transfer API Methods ============

  /**
   * Set insulation type using predefined U-values.
   *
   * <p>
   * This is a convenience method that sets appropriate heat transfer coefficient based on
   * insulation type. Automatically enables heat transfer modeling.
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
  public InsulationType getInsulationType() {
    return insulationType;
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
  public double getWallThickness() {
    return wallThickness;
  }

  /**
   * Set soil/burial thermal resistance.
   *
   * <p>
   * For buried pipelines, this adds thermal resistance between pipe outer wall and ambient. The
   * effective U-value becomes: U_eff = 1 / (1/U + R_soil)
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

  /**
   * Enable or disable Joule-Thomson effect.
   *
   * <p>
   * When enabled, temperature drops due to pressure reduction are calculated. This is important for
   * gas pipelines with significant pressure drop.
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
    return idx * dx;
  }
}
