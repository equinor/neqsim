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

  /** Multi-layer thermal calculator for OLGA-style radial heat transfer. */
  private MultilayerThermalCalculator thermalCalculator = null;

  /** Enable multi-layer thermal model (vs simple U-value). */
  private boolean useMultilayerThermalModel = false;

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

  // ============ OLGA-style model parameters ============

  /**
   * OLGA model type for holdup and flow regime calculations.
   *
   * <p>
   * Reference: Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA" SPE Production
   * Engineering, May 1991, pp. 171-180
   * </p>
   */
  public enum OLGAModelType {
    /**
     * Full OLGA model with momentum balance for all flow regimes. Most accurate but computationally
     * intensive.
     */
    FULL,
    /**
     * Simplified OLGA model with empirical correlations. Faster but less accurate for complex
     * terrain.
     */
    SIMPLIFIED,
    /**
     * Original NeqSim drift-flux model (pre-OLGA). For backward compatibility.
     */
    DRIFT_FLUX
  }

  /** Current OLGA model type. Default is FULL for best accuracy. */
  private OLGAModelType olgaModelType = OLGAModelType.FULL;

  /**
   * Base minimum liquid holdup for stratified flow (OLGA-style constraint).
   *
   * <p>
   * OLGA enforces a minimum holdup to prevent unrealistically low values at high gas velocities.
   * This is based on the observation that even at high velocities, a thin liquid film remains on
   * the pipe wall in stratified/annular flow.
   * </p>
   *
   * <p>
   * The actual minimum applied is the maximum of:
   * </p>
   * <ul>
   * <li>This base value (default 1%)</li>
   * <li>A multiple of the no-slip holdup (lambdaL * minimumSlipFactor)</li>
   * </ul>
   * <p>
   * This ensures the minimum is physically reasonable for both lean gas (low liquid loading) and
   * rich gas condensate (high liquid loading) systems.
   * </p>
   *
   * <p>
   * Reference: Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA" - SPE Production
   * Engineering
   * </p>
   */
  private double minimumLiquidHoldup = 0.01;

  /**
   * Slip factor applied to no-slip holdup to calculate adaptive minimum.
   *
   * <p>
   * The adaptive minimum holdup is calculated as: lambdaL * minimumSlipFactor. For gas-dominant
   * systems, typical slip ratios range from 1.5-3.0. Default value of 2.0 means minimum holdup is
   * twice the no-slip value, which accounts for liquid accumulation due to slip. This prevents the
   * minimum from being unrealistically high for lean gas systems with very low liquid loading.
   * </p>
   */
  private double minimumSlipFactor = 2.0;

  /**
   * Enable OLGA-style minimum slip constraint.
   *
   * <p>
   * When enabled (default), enforces a minimum liquid holdup in gas-dominant stratified flow,
   * matching OLGA behavior. When disabled, holdup can approach no-slip values at high velocities
   * (Beggs-Brill style).
   * </p>
   */
  private boolean enforceMinimumSlip = true;

  // ============ OLGA Annular Film Model Parameters ============

  /**
   * Minimum film thickness for annular flow (m).
   *
   * <p>
   * In high gas velocity annular flow, OLGA maintains a minimum liquid film on the pipe wall. This
   * prevents unrealistically low holdup predictions. Default 0.1mm based on typical measurements.
   * </p>
   */
  private double minimumFilmThickness = 0.0001; // 0.1 mm

  /**
   * Entrainment fraction in annular flow.
   *
   * <p>
   * Fraction of liquid entrained as droplets in the gas core. Affects the distribution between film
   * flow and droplet flow in annular regime. OLGA uses Ishii-Mishima correlation.
   * </p>
   */
  private double annularEntrainmentFraction = 0.0;

  /**
   * Enable OLGA-style annular film model.
   *
   * <p>
   * When enabled, uses OLGA's annular film model which accounts for: - Minimum film thickness on
   * pipe wall - Liquid entrainment in gas core - Wave formation and droplet deposition
   * </p>
   */
  private boolean enableAnnularFilmModel = true;

  // ============ OLGA Terrain Tracking Parameters ============

  /**
   * Enable full OLGA-style terrain tracking.
   *
   * <p>
   * When enabled, uses OLGA's terrain tracking algorithm which: - Identifies all low points and
   * high points - Tracks liquid accumulation in valleys - Models terrain-induced slugging - Handles
   * severe slugging in risers
   * </p>
   */
  private boolean enableTerrainTracking = true;

  /**
   * Critical holdup for terrain-induced slug initiation.
   *
   * <p>
   * When liquid holdup in a low point exceeds this value, a terrain-induced slug is initiated.
   * Default 0.6 based on OLGA recommendations.
   * </p>
   */
  private double terrainSlugCriticalHoldup = 0.6;

  /**
   * Liquid fallback coefficient for uphill sections.
   *
   * <p>
   * Controls how much liquid falls back in uphill sections when gas velocity is insufficient to
   * carry liquid upward. Higher values mean more liquid accumulation. OLGA default ~0.3.
   * </p>
   */
  private double liquidFallbackCoefficient = 0.3;

  /**
   * Enable severe slugging detection and modeling.
   *
   * <p>
   * Severe slugging occurs at riser bases when liquid periodically blocks gas flow. This cyclic
   * phenomenon can cause large pressure and flow oscillations.
   * </p>
   */
  private boolean enableSevereSlugModel = true;

  // ============ OLGA Flow Regime Map Parameters ============

  /**
   * Use OLGA flow regime map instead of Taitel-Dukler.
   *
   * <p>
   * OLGA's flow regime map differs from Taitel-Dukler in several ways: - Different transition
   * criteria for stratified wavy to slug - Accounts for pipe roughness effects - Better handling of
   * inclined flow - Hysteresis in regime transitions
   * </p>
   */
  private boolean useOLGAFlowRegimeMap = true;

  /**
   * Flow regime transition hysteresis factor.
   *
   * <p>
   * OLGA uses hysteresis to prevent rapid switching between flow regimes. A value of 0.1 means 10%
   * hysteresis band around transition boundaries.
   * </p>
   */
  private double flowRegimeHysteresis = 0.1;

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
      if (hasGas) {
        double volGas = inletFluid.getPhase("gas").getVolume("m3");
        double volTotal = inletFluid.getVolume("m3");
        alphaG = volGas / volTotal;
        alphaL = 1.0 - alphaG;
      } else if (hasOil && hasWater) {
        // Oil-water flow (no gas) - treat as two-phase liquid-liquid flow
        // alphaG represents oil (lighter liquid), alphaL represents water (heavier)
        double volOil = inletFluid.getPhase("oil").getVolume("m3");
        double volWater = inletFluid.getPhase("aqueous").getVolume("m3");
        double volTotal = volOil + volWater;
        // Use gas holdup as oil fraction, liquid holdup as water fraction for oil-water
        alphaG = 0.0; // No gas
        alphaL = 1.0; // All liquid
        // Track oil-water split internally
        inletWaterCut = volWater / volTotal;
        inletOilFraction = 1.0 - inletWaterCut;
        isThreePhase = true; // Use three-fluid tracking even without gas
        logger.info("Oil-water flow (no gas): water cut = {:.1f}%", inletWaterCut * 100);
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
      } else if (hasWater && !hasOil) {
        // Two-phase gas + aqueous (no oil) - all liquid is water
        sec.setWaterDensity(rhoL);
        sec.setWaterViscosity(muL);
        sec.setOilDensity(rhoL); // Dummy value, no oil present
        sec.setOilViscosity(muL);
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

    logger.info("TwoFluidPipe initialized: {} sections, dx={:.2f}m", numberOfSections, dx);
  }

  /**
   * Run steady-state initialization.
   */
  private void runSteadyState() {
    // Simple steady-state: iterate until pressure and holdup profiles converge
    int maxIter = 100;
    double tolerance = 1e-4;

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

    for (int iter = 0; iter < maxIter; iter++) {
      double maxChange = 0;

      // Update thermodynamics periodically to capture phase changes (condensation)
      // This is critical for systems where liquid condenses along the pipeline
      if (iter % 5 == 0 && referenceFluid != null) {
        updateThermodynamicsWithCondensation(massFlow, localMDotGas, localMDotLiq);
      }

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

        // Use LOCAL mass flow rates that account for condensation
        double localMDotG = localMDotGas[i];
        double localMDotL = localMDotLiq[i];

        // Update holdup using drift-flux model with terrain effects
        double[] newHoldups = calculateLocalHoldup(sec, prev, localMDotG, localMDotL, area);
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
          double vG = localMDotG / (area * alphaG_new * sec.getGasDensity());
          sec.setGasVelocity(vG);
        }
        if (alphaL_new > 0.001 && sec.getLiquidDensity() > 0) {
          double vL = localMDotL / (area * alphaL_new * sec.getLiquidDensity());
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
   * Update thermodynamics along pipe with condensation tracking.
   *
   * <p>
   * Performs TP-flash at each section to determine local phase fractions accounting for
   * condensation/vaporization. This is critical for gas systems with water where liquid may
   * condense as pressure drops and temperature decreases along the pipeline.
   * </p>
   *
   * @param massFlow Total mass flow rate [kg/s]
   * @param localMDotGas Array to store local gas mass flow rates [kg/s]
   * @param localMDotLiq Array to store local liquid mass flow rates [kg/s]
   */
  private void updateThermodynamicsWithCondensation(double massFlow, double[] localMDotGas,
      double[] localMDotLiq) {
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

        // Calculate local phase mass flow rates from flash results
        double volTotal = flash.getVolume("m3");
        double volGas = 0;
        double volLiq = 0;
        double massGas = 0;
        double massLiq = 0;

        if (flash.hasPhaseType("gas")) {
          volGas = flash.getPhase("gas").getVolume("m3");
          massGas = flash.getPhase("gas").getFlowRate("kg/sec");
        }

        // Handle liquid phases (oil, water, or both)
        boolean hasOil = flash.hasPhaseType("oil");
        boolean hasWater = flash.hasPhaseType("aqueous");

        if (hasOil) {
          double rhoOil = flash.getPhase("oil").getDensity("kg/m3");
          double volOil = flash.getPhase("oil").getVolume("m3");
          volLiq += volOil;
          massLiq += flash.getPhase("oil").getFlowRate("kg/sec");
          sec.setOilDensity(rhoOil);
          sec.setOilViscosity(flash.getPhase("oil").getViscosity("kg/msec"));
        }

        if (hasWater) {
          double rhoWater = flash.getPhase("aqueous").getDensity("kg/m3");
          double volWater = flash.getPhase("aqueous").getVolume("m3");
          volLiq += volWater;
          massLiq += flash.getPhase("aqueous").getFlowRate("kg/sec");
          sec.setWaterDensity(rhoWater);
          sec.setWaterViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
        }

        // Calculate mass fractions and update local mass flow rates
        double massTotalFlash = massGas + massLiq;
        if (massTotalFlash > 0) {
          double gasMassFraction = massGas / massTotalFlash;
          double liqMassFraction = massLiq / massTotalFlash;

          // Scale to actual mass flow rate
          localMDotGas[i] = massFlow * gasMassFraction;
          localMDotLiq[i] = massFlow * liqMassFraction;
        }

        // Update holdup based on local volumetric fractions
        if (volTotal > 0) {
          double alphaG = volGas / volTotal;
          double alphaL = volLiq / volTotal;
          sec.setGasHoldup(alphaG);
          sec.setLiquidHoldup(alphaL);

          // Update water/oil split if both are present
          if (hasOil && hasWater) {
            double volOil = flash.getPhase("oil").getVolume("m3");
            double volWater = flash.getPhase("aqueous").getVolume("m3");
            double waterCut = volWater / volLiq;
            sec.setWaterCut(waterCut);
            sec.setOilFractionInLiquid(1.0 - waterCut);
            sec.setWaterHoldup(alphaL * waterCut);
            sec.setOilHoldup(alphaL * (1.0 - waterCut));
          } else if (hasWater && !hasOil) {
            // Gas + water only - all liquid is water
            sec.setWaterCut(1.0);
            sec.setOilFractionInLiquid(0.0);
            sec.setWaterHoldup(alphaL);
            sec.setOilHoldup(0.0);
          } else if (hasOil && !hasWater) {
            // Gas + oil only - all liquid is oil
            sec.setWaterCut(0.0);
            sec.setOilFractionInLiquid(1.0);
            sec.setWaterHoldup(0.0);
            sec.setOilHoldup(alphaL);
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
        } else if (hasWater) {
          sec.setLiquidDensity(flash.getPhase("aqueous").getDensity("kg/m3"));
          sec.setLiquidViscosity(flash.getPhase("aqueous").getViscosity("kg/msec"));
          sec.setLiquidSoundSpeed(flash.getPhase("aqueous").getSoundSpeed());
          sec.setLiquidEnthalpy(flash.getPhase("aqueous").getEnthalpy("J/kg"));
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
   * <p>
   * If multi-layer thermal model is enabled, uses MultilayerThermalCalculator for accurate radial
   * heat transfer through multiple layers (steel, insulation, coatings, etc.).
   * </p>
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

    // Get Joule-Thomson coefficient if enabled
    double muJT = 0.0;
    if (enableJouleThomson) {
      muJT = 0.4 / 1e5; // K/Pa (typical for natural gas)
    }

    // Use multi-layer thermal model if enabled
    if (useMultilayerThermalModel && thermalCalculator != null) {
      updateTransientTemperatureMultilayer(massFlow, area, dt, Cp, muJT);
      return;
    }

    // Simple two-layer model (fluid + wall)
    double pipePerimeter = Math.PI * diameter;
    double outerDiameter = diameter + 2 * wallThickness;
    double outerPerimeter = Math.PI * outerDiameter;

    // Wall cross-sectional area
    double wallArea = Math.PI * (outerDiameter * outerDiameter - diameter * diameter) / 4.0;
    double wallMassPerLength = wallArea * wallDensity; // kg/m

    // Fluid properties per unit length
    double fluidDensity = inletFluid.getDensity("kg/m3");
    double fluidMassPerLength = area * fluidDensity;

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
      T_fluid = Math.max(T_fluid, T_ambient);
      T_fluid = Math.max(T_fluid, 100.0); // Absolute minimum: 100K
      sec.setTemperature(T_fluid);

      // Check hydrate/wax risk
      hydrateRiskSections[i] =
          (hydrateFormationTemperature > 0 && T_fluid < hydrateFormationTemperature);
      waxRiskSections[i] = (waxAppearanceTemperature > 0 && T_fluid < waxAppearanceTemperature);
    }
  }

  /**
   * Update temperature using multi-layer thermal model.
   *
   * <p>
   * This method implements OLGA-style radial heat transfer through multiple concentric layers. The
   * heat transfer calculation uses:
   * </p>
   * <ul>
   * <li>Inner convective resistance from fluid to pipe wall</li>
   * <li>Conductive resistance through each layer</li>
   * <li>Thermal mass storage in each layer for transient response</li>
   * <li>Outer convective/conductive resistance to ambient</li>
   * </ul>
   *
   * @param massFlow Total mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   * @param dt Time step [s]
   * @param Cp Fluid heat capacity [J/(kg·K)]
   * @param muJT Joule-Thomson coefficient [K/Pa]
   */
  private void updateTransientTemperatureMultilayer(double massFlow, double area, double dt,
      double Cp, double muJT) {
    double pipePerimeter = Math.PI * diameter;

    // Fluid properties per unit length
    SystemInterface inletFluid = getInletStream().getFluid();
    double fluidDensity = inletFluid.getDensity("kg/m3");
    double fluidMassPerLength = area * fluidDensity;

    // Calculate effective inner heat transfer coefficient based on flow regime
    double h_inner = calculateInnerHTC(massFlow, area);

    for (int i = 1; i < numberOfSections; i++) {
      TwoFluidSection sec = sections[i];
      TwoFluidSection prev = sections[i - 1];

      double T_fluid = sec.getTemperature();

      // Get local surface temperature (profile or constant)
      double T_ambient = surfaceTemperature;
      if (surfaceTemperatureProfile != null && i < surfaceTemperatureProfile.length) {
        T_ambient = surfaceTemperatureProfile[i];
      }

      // Configure thermal calculator for this section
      thermalCalculator.setFluidTemperature(T_fluid);
      thermalCalculator.setAmbientTemperature(T_ambient);
      thermalCalculator.setInnerHTC(h_inner);

      // Update thermal layers for this time step
      thermalCalculator.updateTransient(dt);

      // Get heat loss rate using overall thermal resistance
      double Q_loss = thermalCalculator.calculateHeatLossPerLength(); // W/m

      // Advection term: m_dot * Cp * (T_in - T_out) / dx
      double T_upstream = prev.getTemperature();
      double Q_advection = massFlow * Cp * (T_upstream - T_fluid) / dx; // W/m

      // Joule-Thomson cooling
      double dP = sec.getPressure() - prev.getPressure();
      double Q_JT = massFlow * Cp * muJT * dP / dx; // W/m

      // Update fluid temperature
      double dTfluid_dt = (Q_advection - Q_loss + Q_JT) / (fluidMassPerLength * Cp);
      T_fluid += dTfluid_dt * dt;

      // Ensure physical bounds
      T_fluid = Math.max(T_fluid, T_ambient);
      T_fluid = Math.max(T_fluid, 100.0);
      sec.setTemperature(T_fluid);

      // Store wall temperature (inner surface of first layer)
      if (thermalCalculator.getNumberOfLayers() > 0) {
        wallTemperatureProfile[i] = thermalCalculator.calculateInterfaceTemperature(0, false);
      }

      // Check hydrate/wax risk
      hydrateRiskSections[i] =
          (hydrateFormationTemperature > 0 && T_fluid < hydrateFormationTemperature);
      waxRiskSections[i] = (waxAppearanceTemperature > 0 && T_fluid < waxAppearanceTemperature);
    }
  }

  /**
   * Calculate inner (fluid-side) heat transfer coefficient based on flow conditions.
   *
   * <p>
   * Uses Dittus-Boelter correlation for turbulent flow, constant Nusselt for laminar.
   * </p>
   *
   * @param massFlow Mass flow rate [kg/s]
   * @param area Pipe cross-sectional area [m²]
   * @return Inner HTC in W/(m²·K)
   */
  private double calculateInnerHTC(double massFlow, double area) {
    if (massFlow <= 0 || area <= 0) {
      return heatTransferCoefficient; // Default
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
   * Calculate local liquid holdup using OLGA-style models with terrain effects.
   *
   * <p>
   * Supports multiple model types:
   * </p>
   * <ul>
   * <li>FULL OLGA: Momentum balance for stratified, film model for annular, Dukler for slug</li>
   * <li>SIMPLIFIED OLGA: Empirical correlations with minimum slip constraint</li>
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

    // Determine flow regime to select appropriate correlation
    FlowRegime regime = sec.getFlowRegime();

    // Get viscosities for momentum balance calculations
    double muL = sec.getLiquidViscosity();
    double muG = sec.getGasViscosity();

    double alphaL;

    // Use OLGA model type to determine calculation method
    if (olgaModelType == OLGAModelType.FULL) {
      // ========== FULL OLGA MODEL ==========
      // Use flow-regime-specific OLGA correlations

      if (regime == FlowRegime.ANNULAR) {
        // OLGA annular film model
        if (enableAnnularFilmModel) {
          double[] annularResult = calculateAnnularHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma,
              diameter, inclination);
          alphaL = annularResult[0];
        } else {
          // Simplified annular: minimum film constraint with adaptive minimum
          double filmHoldup = 4.0 * minimumFilmThickness / diameter;
          double adaptiveMin = Math.max(minimumLiquidHoldup, lambdaL * minimumSlipFactor);
          alphaL = Math.max(adaptiveMin, filmHoldup + lambdaL * 0.5);
        }

      } else if (regime == FlowRegime.SLUG || regime == FlowRegime.CHURN) {
        // OLGA slug flow model
        alphaL = calculateSlugHoldupOLGA(vsG, vsL, rhoG, rhoL, muL, sigma, diameter, inclination);

      } else if (regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY) {
        // OLGA stratified flow momentum balance
        alphaL = calculateStratifiedHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter,
            inclination);

      } else if (regime == FlowRegime.DISPERSED_BUBBLE || regime == FlowRegime.BUBBLE) {
        // Dispersed bubble: near-homogeneous flow
        // αL ≈ λL with small correction for bubble rise
        double vSlip = 1.53 * Math.pow(g * sigma * (rhoL - rhoG) / (rhoL * rhoL), 0.25);
        alphaL = vsL / (vsL + vsG + vSlip * (1.0 - lambdaL));
        alphaL = Math.max(lambdaL * 0.9, alphaL);

      } else {
        // Default to stratified momentum balance
        alphaL = calculateStratifiedHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter,
            inclination);
      }

      // Apply terrain accumulation enhancement
      alphaL = applyTerrainAccumulation(sec, prev, alphaL);

      // Apply adaptive minimum slip constraint with velocity-dependent slip ratio
      // For stratified flow, slip ratio increases at low velocities (liquid accumulation)
      // For annular flow, use minimum film thickness constraint
      if (enforceMinimumSlip) {
        double adaptiveMin;
        // Velocity-dependent slip for all flow regimes at low gas velocities
        // At low gas velocity, liquid accumulates regardless of flow regime
        double vsgRef = 8.0; // Reference gas superficial velocity [m/s]
        double velocityRatio = Math.max(0.5, Math.min(6.0, vsgRef / Math.max(vsG, 0.3)));

        if (regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY) {
          // Velocity-dependent slip ratio for stratified flow
          // At low gas velocity, liquid cannot be efficiently carried -> higher holdup
          // Slip factor increases as velocity decreases (inverse relationship)
          // Based on Beggs-Brill and field data, slip can be 10-30x at low velocities
          double baseSlip = 3.0; // Minimum slip at high velocity
          double maxSlip = 25.0; // Maximum slip at very low velocity
          double exponent = 0.85; // Controls how quickly slip increases

          double slipFactor = baseSlip * Math.pow(velocityRatio, exponent);
          slipFactor = Math.min(maxSlip, slipFactor);

          // Adaptive minimum: no-slip holdup multiplied by velocity-dependent slip
          adaptiveMin = lambdaL * slipFactor;
          // Use minimumLiquidHoldup as an absolute floor
          adaptiveMin = Math.max(minimumLiquidHoldup, adaptiveMin);
        } else if (regime == FlowRegime.ANNULAR) {
          // For annular flow, combine film thickness minimum with velocity-dependent slip
          // At low velocities, annular flow also accumulates more liquid
          double filmHoldup = 4.0 * minimumFilmThickness / diameter;
          double baseSlip = 4.0; // Higher base slip for annular (film drainage effect)
          double maxSlip = 20.0;
          double exponent = 0.75;

          double slipFactor = baseSlip * Math.pow(velocityRatio, exponent);
          slipFactor = Math.min(maxSlip, slipFactor);

          // Use maximum of film minimum and velocity-dependent slip
          double velocityBasedMin = lambdaL * slipFactor;
          adaptiveMin = Math.max(filmHoldup, velocityBasedMin);
          adaptiveMin = Math.max(minimumLiquidHoldup, adaptiveMin);
        } else {
          // For other regimes, use velocity-dependent adaptive minimum
          double baseSlip = 3.5;
          double maxSlip = 15.0;
          double exponent = 0.65;

          double slipFactor = baseSlip * Math.pow(velocityRatio, exponent);
          slipFactor = Math.min(maxSlip, slipFactor);

          adaptiveMin = Math.max(minimumLiquidHoldup, lambdaL * slipFactor);
        }
        if (alphaL < adaptiveMin) {
          alphaL = adaptiveMin;
        }
      }

    } else if (olgaModelType == OLGAModelType.SIMPLIFIED) {
      // ========== SIMPLIFIED OLGA MODEL ==========
      // Use empirical correlations with minimum slip

      // For gas-dominant systems, use stratified momentum balance
      boolean isStratified =
          (regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY);
      if (lambdaL < 0.1 || isStratified || regime == FlowRegime.ANNULAR) {
        alphaL = calculateStratifiedHoldupOLGA(vsG, vsL, rhoG, rhoL, muG, muL, sigma, diameter,
            inclination);
      } else {
        // Higher liquid loading: use drift-flux with terrain correction
        alphaL = calculateDriftFluxHoldup(vsG, vsL, rhoG, rhoL, sigma, inclination);
      }

      // Apply terrain accumulation
      if (enableTerrainTracking) {
        alphaL = applyTerrainAccumulation(sec, prev, alphaL);
      }

      // Apply velocity-dependent adaptive minimum slip (same as FULL model)
      if (enforceMinimumSlip) {
        double adaptiveMin;
        double vsgRef = 8.0;
        double velocityRatio = Math.max(0.5, Math.min(6.0, vsgRef / Math.max(vsG, 0.3)));

        if (isStratified) {
          // Velocity-dependent slip ratio for stratified flow
          // Match FULL model parameters for consistency
          double baseSlip = 3.0;
          double maxSlip = 25.0;
          double exponent = 0.85;

          double slipFactor = baseSlip * Math.pow(velocityRatio, exponent);
          slipFactor = Math.min(maxSlip, slipFactor);

          adaptiveMin = lambdaL * slipFactor;
          adaptiveMin = Math.max(minimumLiquidHoldup, adaptiveMin);
        } else if (regime == FlowRegime.ANNULAR) {
          // Velocity-dependent slip for annular flow
          double filmHoldup = 4.0 * minimumFilmThickness / diameter;
          double baseSlip = 4.0;
          double maxSlip = 20.0;
          double exponent = 0.75;

          double slipFactor = baseSlip * Math.pow(velocityRatio, exponent);
          slipFactor = Math.min(maxSlip, slipFactor);

          double velocityBasedMin = lambdaL * slipFactor;
          adaptiveMin = Math.max(filmHoldup, velocityBasedMin);
          adaptiveMin = Math.max(minimumLiquidHoldup, adaptiveMin);
        } else {
          // Velocity-dependent for other regimes
          double baseSlip = 3.5;
          double maxSlip = 15.0;
          double exponent = 0.65;

          double slipFactor = baseSlip * Math.pow(velocityRatio, exponent);
          slipFactor = Math.min(maxSlip, slipFactor);

          adaptiveMin = Math.max(minimumLiquidHoldup, lambdaL * slipFactor);
        }
        if (alphaL < adaptiveMin) {
          alphaL = adaptiveMin;
        }
      }

    } else {
      // ========== ORIGINAL DRIFT-FLUX MODEL ==========
      // For backward compatibility with original NeqSim behavior
      alphaL = calculateDriftFluxHoldup(vsG, vsL, rhoG, rhoL, sigma, inclination);
    }

    alphaL = Math.max(0.001, Math.min(0.999, alphaL));

    // Valley/peak terrain adjustments (existing logic)
    if (prev != null) {
      double inclinationChange = inclination - prev.getInclination();
      boolean isValley = prev.getInclination() < -0.05 && inclination > 0.05;
      boolean isPeak = prev.getInclination() > 0.05 && inclination < -0.05;

      if (isValley) {
        double valleyFactor = 1.0 + 0.3 * Math.min(Math.abs(inclinationChange), 0.2);
        alphaL = Math.min(0.8, alphaL * valleyFactor); // Allow up to 80% in valleys
      } else if (isPeak) {
        double peakFactor = 1.0 - 0.3 * Math.min(Math.abs(inclinationChange), 0.2);
        alphaL = Math.max(0.001, alphaL * peakFactor);
      }
    }

    return new double[] {alphaL, 1.0 - alphaL};
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
  private double calculateDriftFluxHoldup(double vsG, double vsL, double rhoG, double rhoL,
      double sigma, double inclination) {

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
    double alphaG = vsG / vG;
    alphaG = Math.max(0.001, Math.min(0.999, alphaG));

    return 1.0 - alphaG;
  }

  /**
   * Calculate stratified flow liquid holdup using OLGA-style momentum balance.
   *
   * <p>
   * This method implements the OLGA approach for stratified flow, where the liquid level is
   * determined by a momentum balance between the phases. The key principle is that at equilibrium,
   * the pressure gradient must be equal in both phases.
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
   * Reference: Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA: Theory and Application"
   * SPE Production Engineering, May 1991, pp. 171-180
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
  private double calculateStratifiedHoldupOLGA(double vsG, double vsL, double rhoG, double rhoL,
      double muG, double muL, double sigma, double D, double theta) {

    double g = 9.81;
    double A = Math.PI * D * D / 4.0; // Total cross-section area
    double vMix = vsG + vsL;

    // No-slip liquid holdup (input fraction)
    double lambdaL = vsL / vMix;

    // For very low liquid loading, use minimum holdup
    if (lambdaL < 0.001) {
      return minimumLiquidHoldup;
    }

    // Iterative solution for equilibrium liquid level
    // Start with an initial guess based on Taitel-Dukler
    double alphaL = lambdaL * 2.0; // Initial guess: 2x input fraction

    // Limit initial guess
    alphaL = Math.max(0.01, Math.min(0.5, alphaL));

    // Newton-Raphson iteration for equilibrium holdup
    // Solve: F(αL) = dP/dx_gas - dP/dx_liquid = 0
    for (int iter = 0; iter < 20; iter++) {
      double alphaG = 1.0 - alphaL;

      // Geometric parameters for stratified flow (flat interface approximation)
      // Liquid level: hL = D * (1 - cos(π * αL)) / 2 for small αL
      // This is simplified - full OLGA uses exact circular geometry
      double hL = D * alphaL; // Simplified liquid level

      // Hydraulic diameters
      // For stratified flow: perimeters and areas
      double AL = A * alphaL;
      double AG = A * alphaG;

      // Wetted perimeters (simplified)
      double SL = Math.PI * D * Math.sqrt(alphaL); // Liquid-wall contact
      double SG = Math.PI * D * (1.0 - Math.sqrt(alphaL) / 2.0); // Gas-wall contact
      double Si = D; // Interfacial width (simplified)

      // Hydraulic diameters
      double DL = 4.0 * AL / (SL + Si);
      double DG = 4.0 * AG / (SG + Si);

      // Actual phase velocities
      double vL = vsL / alphaL;
      double vG = vsG / alphaG;

      // Reynolds numbers
      double ReL = rhoL * Math.abs(vL) * DL / muL;
      double ReG = rhoG * Math.abs(vG) * DG / muG;

      // Friction factors (Blasius correlation for simplicity)
      double fL = (ReL < 2000) ? 16.0 / Math.max(ReL, 1.0) : 0.046 / Math.pow(ReL, 0.2);
      double fG = (ReG < 2000) ? 16.0 / Math.max(ReG, 1.0) : 0.046 / Math.pow(ReG, 0.2);

      // Interfacial friction factor (OLGA uses enhanced value due to waves)
      double fi = fG * (1.0 + 75.0 * alphaL); // Enhancement factor for wavy interface

      // Wall shear stresses
      double tauWL = fL * rhoL * vL * Math.abs(vL) / 2.0;
      double tauWG = fG * rhoG * vG * Math.abs(vG) / 2.0;

      // Interfacial shear stress (gas exerts stress on liquid)
      double vRel = vG - vL;
      double tauI = fi * rhoG * vRel * Math.abs(vRel) / 2.0;

      // Pressure gradients (momentum balance)
      // Gas: -dP/dx = τ_wG * S_G / A_G + τ_i * S_i / A_G + ρ_G * g * sin(θ)
      // Liquid: -dP/dx = τ_wL * S_L / A_L - τ_i * S_i / A_L + ρ_L * g * sin(θ)
      double dPdxG = tauWG * SG / AG + tauI * Si / AG + rhoG * g * Math.sin(theta);
      double dPdxL = tauWL * SL / AL - tauI * Si / AL + rhoL * g * Math.sin(theta);

      // Residual: pressure gradients should be equal at equilibrium
      double F = dPdxG - dPdxL;

      // Convergence check
      if (Math.abs(F) < 1.0) { // Converged (within 1 Pa/m)
        break;
      }

      // Numerical derivative for Newton-Raphson
      double dAlpha = 0.001;
      double alphaL2 = alphaL + dAlpha;
      double alphaG2 = 1.0 - alphaL2;

      double AL2 = A * alphaL2;
      double AG2 = A * alphaG2;
      double SL2 = Math.PI * D * Math.sqrt(alphaL2);
      double SG2 = Math.PI * D * (1.0 - Math.sqrt(alphaL2) / 2.0);
      double DL2 = 4.0 * AL2 / (SL2 + Si);
      double DG2 = 4.0 * AG2 / (SG2 + Si);

      double vL2 = vsL / alphaL2;
      double vG2 = vsG / alphaG2;
      double ReL2 = rhoL * Math.abs(vL2) * DL2 / muL;
      double ReG2 = rhoG * Math.abs(vG2) * DG2 / muG;
      double fL2 = (ReL2 < 2000) ? 16.0 / Math.max(ReL2, 1.0) : 0.046 / Math.pow(ReL2, 0.2);
      double fG2 = (ReG2 < 2000) ? 16.0 / Math.max(ReG2, 1.0) : 0.046 / Math.pow(ReG2, 0.2);
      double fi2 = fG2 * (1.0 + 75.0 * alphaL2);

      double tauWL2 = fL2 * rhoL * vL2 * Math.abs(vL2) / 2.0;
      double tauWG2 = fG2 * rhoG * vG2 * Math.abs(vG2) / 2.0;
      double vRel2 = vG2 - vL2;
      double tauI2 = fi2 * rhoG * vRel2 * Math.abs(vRel2) / 2.0;

      double dPdxG2 = tauWG2 * SG2 / AG2 + tauI2 * Si / AG2 + rhoG * g * Math.sin(theta);
      double dPdxL2 = tauWL2 * SL2 / AL2 - tauI2 * Si / AL2 + rhoL * g * Math.sin(theta);
      double F2 = dPdxG2 - dPdxL2;

      double dFdAlpha = (F2 - F) / dAlpha;

      // Newton-Raphson update with damping
      if (Math.abs(dFdAlpha) > 1e-10) {
        double deltaAlpha = -F / dFdAlpha;
        // Damping to prevent overshooting
        deltaAlpha = Math.max(-0.05, Math.min(0.05, deltaAlpha));
        alphaL = alphaL + 0.5 * deltaAlpha;
      }

      // Keep holdup in valid range - allow up to 80% for low velocity stratified flow
      alphaL = Math.max(0.01, Math.min(0.8, alphaL));
    }

    return alphaL;
  }

  /**
   * Calculate liquid holdup for annular flow using OLGA-style film model.
   *
   * <p>
   * In annular flow, liquid exists as a thin film on the pipe wall and as entrained droplets in the
   * gas core. OLGA models this using:
   * </p>
   * <ul>
   * <li>Film flow momentum balance</li>
   * <li>Entrainment/deposition equilibrium</li>
   * <li>Minimum film thickness constraint</li>
   * </ul>
   *
   * <p>
   * Reference: Bendiksen et al. (1991) and OLGA Technical Manual
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
  private double[] calculateAnnularHoldupOLGA(double vsG, double vsL, double rhoG, double rhoL,
      double muG, double muL, double sigma, double D, double theta) {

    double g = 9.81;
    double A = Math.PI * D * D / 4.0;

    // Calculate entrainment fraction using Ishii-Mishima correlation
    // E = tanh(7.25e-7 * We^1.25 * Re_L^0.25)
    // where We = ρ_G * v_SG² * D / σ (gas Weber number)
    // and Re_L = ρ_L * v_SL * D / μ_L (liquid Reynolds number)

    double WeG = rhoG * vsG * vsG * D / sigma;
    double ReL = rhoL * vsL * D / muL;

    // Entrainment fraction (OLGA uses modified Ishii-Mishima)
    double entrainment = 0.0;
    if (WeG > 0 && ReL > 0) {
      double entrainmentArg = 7.25e-7 * Math.pow(WeG, 1.25) * Math.pow(ReL, 0.25);
      entrainment = Math.tanh(entrainmentArg);
      entrainment = Math.min(0.95, entrainment); // Maximum 95% entrainment
    }

    // Film superficial velocity (liquid not entrained)
    double vsLF = vsL * (1.0 - entrainment);

    // Minimum film thickness based on OLGA constraint
    // Film area = π * D * δ for thin films
    double minFilmArea = Math.PI * D * minimumFilmThickness;
    double minFilmHoldup = minFilmArea / A;

    // Calculate film holdup from momentum balance
    // For thin film: τ_i = τ_wL where interfacial shear balances wall friction
    // This gives: δ/D = (f_L * ρ_L * v_LF²) / (f_i * ρ_G * v_G² * 4)

    double vG = vsG / 0.95; // Approximate gas core velocity
    double ReG = rhoG * vG * D / muG;
    double fG = (ReG < 2000) ? 16.0 / Math.max(ReG, 1.0) : 0.046 / Math.pow(ReG, 0.2);

    // Interfacial friction factor for annular flow (Wallis correlation)
    // f_i = f_G * (1 + 300 * δ/D)
    // Start with initial guess for film thickness
    double deltaOverD = 0.01; // Initial guess: 1% of diameter

    // Iterative solution for film thickness
    for (int iter = 0; iter < 10; iter++) {
      double filmHoldup = 4.0 * deltaOverD * (1.0 - deltaOverD);
      if (filmHoldup < 0.001) {
        filmHoldup = 0.001;
      }

      double vLF = vsLF / filmHoldup;
      double ReLF = rhoL * Math.abs(vLF) * (2.0 * deltaOverD * D) / muL;
      double fLF = (ReLF < 2000) ? 16.0 / Math.max(ReLF, 1.0) : 0.046 / Math.pow(ReLF, 0.2);

      // Interfacial friction with roughness correction
      double fi = fG * (1.0 + 300.0 * deltaOverD);

      // Momentum balance: τ_wL = τ_i
      // fLF * ρL * vLF² / 2 = fi * ρG * vG² / 2
      // Solve for δ/D
      double tauRatio = (fLF * rhoL * vLF * vLF) / (fi * rhoG * vG * vG + 1e-10);

      // Update film thickness estimate
      double newDeltaOverD = deltaOverD * Math.sqrt(tauRatio);
      newDeltaOverD = Math.max(minimumFilmThickness / D, Math.min(0.2, newDeltaOverD));

      if (Math.abs(newDeltaOverD - deltaOverD) < 1e-6) {
        break;
      }
      deltaOverD = 0.5 * deltaOverD + 0.5 * newDeltaOverD;
    }

    // Final film holdup
    double filmHoldup = 4.0 * deltaOverD * (1.0 - deltaOverD);
    filmHoldup = Math.max(minFilmHoldup, filmHoldup);

    // Entrained droplet holdup (homogeneous with gas core)
    // v_droplet ≈ v_gas (droplets carried by gas)
    double vsLE = vsL * entrainment;
    double dropletHoldup = vsLE / (vsG + vsLE + 1e-10);

    // Total liquid holdup
    double totalHoldup = filmHoldup + dropletHoldup * (1.0 - filmHoldup);
    totalHoldup = Math.max(minimumLiquidHoldup, Math.min(0.5, totalHoldup));

    // Store entrainment for diagnostic purposes
    this.annularEntrainmentFraction = entrainment;

    return new double[] {totalHoldup, filmHoldup, entrainment};
  }

  /**
   * Calculate liquid holdup for slug flow using OLGA model.
   *
   * <p>
   * OLGA models slug flow as a sequence of liquid slugs separated by gas bubbles (Taylor bubbles).
   * The average holdup is determined by:
   * </p>
   * <ul>
   * <li>Slug body holdup (typically 0.7-1.0)</li>
   * <li>Film holdup under Taylor bubble</li>
   * <li>Slug frequency and length</li>
   * </ul>
   *
   * @param vsG Gas superficial velocity [m/s]
   * @param vsL Liquid superficial velocity [m/s]
   * @param rhoG Gas density [kg/m³]
   * @param rhoL Liquid density [kg/m³]
   * @param muL Liquid dynamic viscosity [Pa·s]
   * @param sigma Surface tension [N/m]
   * @param D Pipe diameter [m]
   * @param theta Pipe inclination [radians]
   * @return Slug flow average liquid holdup [-]
   */
  private double calculateSlugHoldupOLGA(double vsG, double vsL, double rhoG, double rhoL,
      double muL, double sigma, double D, double theta) {

    double g = 9.81;
    double vMix = vsG + vsL;

    // Slug body holdup using Gregory correlation
    // H_LS = 1 / (1 + (v_m / 8.66)^1.39)
    double slugBodyHoldup = 1.0 / (1.0 + Math.pow(vMix / 8.66, 1.39));
    slugBodyHoldup = Math.max(0.5, Math.min(0.98, slugBodyHoldup));

    // Taylor bubble rise velocity using Bendiksen (1984)
    double dRho = rhoL - rhoG;
    double C0 = 1.2; // Distribution coefficient

    // Drift velocity for inclined pipes
    double vD0 = 0.35 * Math.sqrt(g * D * dRho / rhoL);
    double sinTheta = Math.sin(theta);
    double cosTheta = Math.cos(theta);

    // Inclination correction
    double vD;
    if (theta >= 0) {
      vD = vD0 * (cosTheta + 1.2 * sinTheta);
    } else {
      vD = vD0 * (cosTheta + 0.3 * Math.abs(sinTheta));
    }

    // Taylor bubble velocity
    double vTB = C0 * vMix + vD;

    // Film holdup under Taylor bubble using Barnea-Brauner correlation
    // Simplified: assume film holdup scales with liquid fraction
    double lambdaL = vsL / vMix;
    double filmHoldup = 0.1 * lambdaL; // Thin film under bubble

    // Slug unit composition using mass balance
    // Slug length ratio (Ls/Lu) from Dukler-Hubbard
    double slugLengthRatio = vsL / (vTB * (slugBodyHoldup - filmHoldup) + 1e-10);
    slugLengthRatio = Math.max(0.1, Math.min(0.9, slugLengthRatio));

    // Average holdup = Ls/Lu * H_LS + (1 - Ls/Lu) * H_film
    double avgHoldup = slugLengthRatio * slugBodyHoldup + (1.0 - slugLengthRatio) * filmHoldup;

    return Math.max(0.1, Math.min(0.9, avgHoldup));
  }

  /**
   * Calculate terrain-induced liquid accumulation enhancement using OLGA methodology.
   *
   * <p>
   * This implements the full OLGA terrain tracking algorithm which accounts for:
   * </p>
   * <ul>
   * <li><b>Low Point Accumulation:</b> Liquid pools in valleys due to gravity. The volume of
   * accumulated liquid depends on the depth of the valley and gas carrying capacity.</li>
   * <li><b>Uphill Liquid Fallback:</b> When gas velocity is below critical velocity, liquid falls
   * back. The critical velocity is based on Taitel-Dukler flooding criterion.</li>
   * <li><b>Downhill Drainage:</b> Liquid accelerates on downhill sections, reducing holdup.</li>
   * <li><b>Riser Base Accumulation:</b> Special treatment for transition from horizontal/downhill
   * to steep uphill (severe slugging potential).</li>
   * <li><b>Terrain-Induced Slugging:</b> When low-point holdup exceeds critical value, slugs form
   * and surge out.</li>
   * </ul>
   *
   * <p>
   * Reference: Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA: Theory and Application"
   * SPE Production Engineering, May 1991, pp. 171-180
   * </p>
   *
   * @param sec Current pipe section
   * @param prev Previous pipe section
   * @param baseHoldup Base holdup from flow regime correlation
   * @return Enhanced holdup accounting for terrain effects
   */
  private double applyTerrainAccumulation(TwoFluidSection sec, TwoFluidSection prev,
      double baseHoldup) {

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

    // ========== LOW POINT ACCUMULATION (OLGA Valley Model) ==========
    if (isLowPoint || sec.isLowPoint()) {
      // At low points, liquid accumulates due to gravity pooling
      // OLGA uses a modified Froude number criterion for accumulation

      // Elevation change into the low point
      double elevChange = (prev != null) ? Math.abs(sec.getElevation() - prev.getElevation()) : 0;

      // Gas Froude number - indicates ability to sweep liquid from low point
      // Fr = vG / sqrt(g * D * (rhoL - rhoG) / rhoG)
      double froudeG = vsG / Math.sqrt(g * diameter * dRho / Math.max(rhoG, 1.0));

      // Liquid accumulation factor increases as Froude decreases
      // Below Fr ~ 1.5, significant accumulation occurs
      double criticalFroude = 1.5;
      double accumulationFactor = 1.0;

      if (froudeG < criticalFroude) {
        // Accumulation increases non-linearly as velocity drops
        double froudeRatio = froudeG / criticalFroude;
        accumulationFactor = 1.0 + (1.0 - froudeRatio) * (1.0 - froudeRatio) * 3.0;

        // Additional factor for deep valleys
        double depthFactor = 1.0 + 0.5 * Math.min(elevChange / diameter, 10.0);
        accumulationFactor *= depthFactor;
      }

      enhancedHoldup = Math.min(terrainSlugCriticalHoldup + 0.2, baseHoldup * accumulationFactor);

      // Check for terrain-induced slug initiation
      if (enhancedHoldup > terrainSlugCriticalHoldup && enableSevereSlugModel) {
        // Liquid level high enough to bridge pipe - slug will form
        sec.setTerrainSlugPending(true);
      }
    }

    // ========== RISER BASE ACCUMULATION (Severe Slugging Model) ==========
    else if (isRiserBase && enableSevereSlugModel) {
      // Riser base is particularly prone to severe slugging
      // Use Pots severe slugging criterion: PI = (P_sep * L_riser) / (rho_L * g * H_riser)

      // Simplified criterion: gas velocity must exceed critical to prevent buildup
      double sinTheta = Math.sin(inclination);
      double vCritRiser = 1.5 * Math.sqrt(g * diameter * dRho * sinTheta / Math.max(rhoG, 1.0));

      if (vsG < vCritRiser) {
        // Severe slugging conditions - high accumulation
        double severityFactor = 1.0 + 2.0 * (1.0 - vsG / vCritRiser);
        enhancedHoldup = Math.min(0.85, baseHoldup * severityFactor);
        sec.setSevereSlugPotential(true);
      }
    }

    // ========== UPHILL LIQUID FALLBACK (OLGA Film Model) ==========
    else if (isUphill) {
      double sinTheta = Math.sin(inclination);
      double cosTheta = Math.cos(inclination);

      // Critical gas velocity for liquid carryover (Turner droplet model + film flow)
      // For film flow: vG_crit = C * sqrt(g * D * (rhoL - rhoG) * sin(theta) / rhoG)
      // where C ~ 0.8-1.2 depending on liquid loading
      double filmCarryFactor = 0.9 + 0.3 * Math.min(baseHoldup * 10.0, 1.0);
      double vCritFilm =
          filmCarryFactor * Math.sqrt(g * diameter * dRho * sinTheta / Math.max(rhoG, 1.0));

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

    // ========== DOWNHILL DRAINAGE (OLGA Film Model) ==========
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

    // Ensure physical bounds
    return Math.max(minimumLiquidHoldup, Math.min(0.95, enhancedHoldup));
  }

  /**
   * Check for and handle terrain-induced slug events.
   *
   * <p>
   * Called during transient simulation to detect when accumulated liquid in low points reaches
   * critical level and triggers a terrain-induced slug.
   * </p>
   *
   * @param dt Time step (s)
   */
  private void checkTerrainSlugEvents(double dt) {
    if (!enableTerrainTracking || !enableSevereSlugModel || sections == null) {
      return;
    }

    for (int i = 0; i < sections.length; i++) {
      TwoFluidSection sec = sections[i];
      if (sec.isTerrainSlugPending() && sec.getLiquidHoldup() > terrainSlugCriticalHoldup) {
        // Initiate terrain-induced slug
        if (slugTracker != null) {
          // Create a new slug starting at this position
          double slugVolume = sec.getArea() * sec.getLength() * sec.getLiquidHoldup();
          double slugLength = sec.getLength() * sec.getLiquidHoldup() / 0.7; // Assume 70% holdup in
                                                                             // slug

          // Log terrain slug event
          logger.debug(
              "Terrain-induced slug initiated at section {} (position {} m), " + "volume {} m³", i,
              sec.getPosition(), slugVolume);
        }
        sec.setTerrainSlugPending(false);
      }
    }
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

  // ============ OLGA-style Minimum Slip Methods ============

  /**
   * Set minimum liquid holdup for stratified flow (OLGA-style constraint).
   *
   * <p>
   * This parameter enforces a minimum liquid holdup in gas-dominant stratified flow, preventing
   * unrealistically low values at high gas velocities. OLGA uses a similar approach based on the
   * observation that a thin liquid film always remains on the pipe wall.
   * </p>
   *
   * <p>
   * Typical values:
   * </p>
   * <ul>
   * <li>0.005 (0.5%) - Default, suitable for gas-condensate systems</li>
   * <li>0.01 (1%) - Conservative estimate for wet gas</li>
   * <li>0.02 (2%) - High liquid loading or wavy stratified flow</li>
   * </ul>
   *
   * @param minHoldup Base minimum liquid holdup fraction (0-1), default 0.01
   */
  public void setMinimumLiquidHoldup(double minHoldup) {
    this.minimumLiquidHoldup = Math.max(0.0, Math.min(0.5, minHoldup));
  }

  /**
   * Get base minimum liquid holdup for stratified flow.
   *
   * @return Base minimum liquid holdup fraction (0-1)
   */
  public double getMinimumLiquidHoldup() {
    return minimumLiquidHoldup;
  }

  /**
   * Set the slip factor used for adaptive minimum holdup calculation.
   *
   * <p>
   * The adaptive minimum holdup is calculated as: lambdaL * minimumSlipFactor, where lambdaL is the
   * no-slip (input) liquid fraction. This ensures physically reasonable minimum holdup for systems
   * with varying liquid loading.
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
   * @param slipFactor Multiplier for no-slip holdup (1.0-5.0), default 2.0
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
   * Enable or disable OLGA-style minimum slip constraint.
   *
   * <p>
   * When enabled (default), enforces a minimum liquid holdup in gas-dominant stratified flow. This
   * matches OLGA behavior and prevents unrealistically low holdup at high velocities.
   * </p>
   *
   * <p>
   * When disabled, holdup can approach no-slip values at high Froude numbers, similar to the
   * original Beggs-Brill correlation behavior.
   * </p>
   *
   * @param enforce true to enforce minimum slip (OLGA-style, default), false for Beggs-Brill style
   */
  public void setEnforceMinimumSlip(boolean enforce) {
    this.enforceMinimumSlip = enforce;
  }

  /**
   * Check if OLGA-style minimum slip constraint is enabled.
   *
   * @return true if minimum slip is enforced (OLGA-style)
   */
  public boolean isEnforceMinimumSlip() {
    return enforceMinimumSlip;
  }

  // ============ OLGA Model Configuration Methods ============

  /**
   * Set the OLGA model type for holdup and flow regime calculations.
   *
   * <p>
   * Available model types:
   * </p>
   * <ul>
   * <li>FULL - Full OLGA model with momentum balance for all flow regimes (most accurate)</li>
   * <li>SIMPLIFIED - Simplified OLGA model with empirical correlations (faster)</li>
   * <li>DRIFT_FLUX - Original NeqSim drift-flux model (for backward compatibility)</li>
   * </ul>
   *
   * @param modelType the OLGA model type to use
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
   * Get the current OLGA model type.
   *
   * @return the current OLGA model type
   */
  public OLGAModelType getOLGAModelType() {
    return olgaModelType;
  }

  /**
   * Set minimum film thickness for annular flow model.
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
   * Enable or disable OLGA-style annular film model.
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
   * Enable or disable full terrain tracking.
   *
   * <p>
   * Terrain tracking identifies low points and models liquid accumulation in valleys. Required for
   * accurate liquid inventory prediction in undulating pipelines.
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
   * Controls liquid accumulation in uphill sections. Higher values mean more liquid falls back and
   * accumulates. OLGA default is approximately 0.3.
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
   * Enable or disable severe slugging model for risers.
   *
   * @param enable true to enable severe slugging detection (default true)
   */
  public void setEnableSevereSlugModel(boolean enable) {
    this.enableSevereSlugModel = enable;
  }

  /**
   * Check if severe slugging model is enabled.
   *
   * @return true if severe slugging model is enabled
   */
  public boolean isEnableSevereSlugModel() {
    return enableSevereSlugModel;
  }

  /**
   * Enable or disable OLGA flow regime map.
   *
   * <p>
   * When enabled, uses OLGA's flow regime transition criteria instead of Taitel-Dukler. OLGA's
   * criteria include roughness effects and better inclined flow handling.
   * </p>
   *
   * @param enable true to use OLGA flow regime map (default true)
   */
  public void setUseOLGAFlowRegimeMap(boolean enable) {
    this.useOLGAFlowRegimeMap = enable;
  }

  /**
   * Check if OLGA flow regime map is used.
   *
   * @return true if OLGA flow regime map is enabled
   */
  public boolean isUseOLGAFlowRegimeMap() {
    return useOLGAFlowRegimeMap;
  }

  /**
   * Set flow regime transition hysteresis factor.
   *
   * <p>
   * Prevents rapid switching between flow regimes near transition boundaries. A value of 0.1 means
   * 10% hysteresis band.
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
      } else if (type.toLowerCase().contains("pipe-in-pipe")
          || type.toLowerCase().contains("pip")) {
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

  // ============ Multi-layer Thermal Model API ============

  /**
   * Get or create the multi-layer thermal calculator.
   *
   * <p>
   * If not yet created, initializes with current pipe geometry. The calculator allows defining
   * multiple radial thermal layers (steel wall, insulation, coatings, etc.) for accurate heat
   * transfer calculations.
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
    this.useMultilayerThermalModel = (calculator != null);
    if (calculator != null) {
      enableHeatTransfer = true;
    }
  }

  /**
   * Enable multi-layer thermal model for OLGA-style radial heat transfer.
   *
   * <p>
   * When enabled, uses the MultilayerThermalCalculator for accurate heat transfer with proper
   * modeling of:
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
    if (enable) {
      enableHeatTransfer = true;
      getThermalCalculator(); // Ensure created
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
    calc.createSubseaPipeConfig(diameter, wallThickness, insulationThickness, concreteThickness,
        insulationMaterial);
    calc.setAmbientTemperature(surfaceTemperature);
    useMultilayerThermalModel = true;
    enableHeatTransfer = true;

    // Update the simple U-value to match for backwards compatibility
    heatTransferCoefficient = calc.calculateOverallUValue();
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
    useMultilayerThermalModel = true;
    enableHeatTransfer = true;

    heatTransferCoefficient = calc.calculateOverallUValue();
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
      thermalCalculator.setFluidTemperature(initialTemp);
      thermalCalculator.initializeLayerTemperaturesLinear();
      return thermalCalculator.calculateCooldownTime(targetK);
    }

    // Simple exponential decay estimate with U-value
    if (heatTransferCoefficient <= 0) {
      return Double.POSITIVE_INFINITY;
    }

    double fluidTemp = getInletStream().getTemperature("K");
    double ambientTemp = surfaceTemperature;

    // Thermal mass per unit length (fluid + wall)
    double fluidArea = Math.PI * diameter * diameter / 4.0;
    double wallArea =
        Math.PI * (Math.pow(diameter / 2 + wallThickness, 2) - Math.pow(diameter / 2, 2));
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
      sb.append(thermalCalculator.getSummary());
    } else {
      sb.append(String.format("  U-value: %.2f W/(m²·K)\n", heatTransferCoefficient));
      sb.append(String.format("  Insulation type: %s\n", insulationType));
      sb.append(String.format("  Surface temperature: %.1f °C\n", surfaceTemperature - 273.15));
    }

    if (hydrateFormationTemperature > 0) {
      sb.append(String.format("\nHydrate formation temperature: %.1f °C\n",
          hydrateFormationTemperature - 273.15));
      sb.append(
          String.format("Cooldown time to hydrate: %.1f hours\n", calculateHydrateCooldownTime()));
    }

    return sb.toString();
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
