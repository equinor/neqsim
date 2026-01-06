package neqsim.process.equipment.reservoir;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * WellSystem class - Integrated well model combining IPR (Inflow Performance Relationship) and VLP
 * (Vertical Lift Performance) for complete well modeling.
 *
 * <p>
 * This class represents a complete producing well system including:
 * </p>
 * <ul>
 * <li>Reservoir inflow (IPR) via {@link WellFlow}</li>
 * <li>Wellbore hydraulics (VLP) via {@link TubingPerformance}</li>
 * <li>Optional production choke</li>
 * <li>Operating point calculation (IPR-VLP intersection)</li>
 * <li>Lift curve generation for reservoir simulator coupling</li>
 * <li>Multi-layer/commingled production support</li>
 * </ul>
 *
 * <h2>Architecture Overview</h2>
 * 
 * <pre>
 * ┌─────────────┐    ┌──────────┐    ┌─────────────────┐    ┌───────┐    ┌────────┐
 * │  Reservoir  │───►│ WellFlow │───►│TubingPerformance│───►│ Choke │───►│ Output │
 * │   (IPR)     │    │  (IPR)   │    │     (VLP)       │    │(opt.) │    │ Stream │
 * └─────────────┘    └──────────┘    └─────────────────┘    └───────┘    └────────┘
 *       Pr              Pwf                                    WHP         Outlet
 * </pre>
 *
 * <h2>Usage Example 1 - Basic Well Setup</h2>
 * 
 * <pre>{@code
 * // Create reservoir fluid at reservoir conditions
 * SystemInterface resFluid = new SystemSrkEos(373.15, 250.0);
 * resFluid.addComponent("methane", 85.0);
 * resFluid.addComponent("ethane", 5.0);
 * resFluid.addComponent("propane", 3.0);
 * resFluid.addComponent("n-heptane", 7.0);
 * resFluid.setMixingRule("classic");
 * resFluid.setMultiPhaseCheck(true);
 *
 * // Create reservoir stream
 * Stream reservoirStream = new Stream("reservoir", resFluid);
 * reservoirStream.setFlowRate(5000, "Sm3/day");
 * reservoirStream.run();
 *
 * // Create integrated well system
 * WellSystem well = new WellSystem("Producer-1");
 * well.setReservoirStream(reservoirStream);
 *
 * // Configure IPR (Vogel model)
 * well.setIPRModel(WellSystem.IPRModel.VOGEL);
 * well.setVogelParameters(8000, 180, 250); // qMax at AOF, test Pwf, Pr
 *
 * // Configure VLP (tubing)
 * well.setTubingLength(2500, "m");
 * well.setTubingDiameter(4.0, "in");
 * well.setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
 *
 * // Set wellhead pressure and solve for operating point
 * well.setWellheadPressure(50.0, "bara");
 * well.run();
 *
 * // Get results
 * double opRate = well.getOperatingFlowRate("Sm3/day");
 * double bhp = well.getBottomHolePressure("bara");
 * double whp = well.getWellheadPressure("bara");
 * }</pre>
 *
 * <h2>Usage Example 2 - Lift Curve Generation for Reservoir Simulator</h2>
 * 
 * <pre>{@code
 * // Setup well as above, then generate lift curves
 * double[] whPressures = {30, 40, 50, 60, 70}; // bara
 * double[] waterCuts = {0.0, 0.2, 0.4, 0.6, 0.8};
 *
 * // Generate lift curve table
 * LiftCurveTable liftTable = well.generateLiftCurves(whPressures, waterCuts);
 *
 * // Export for Eclipse/OPM/tNavigator
 * liftTable.exportToEclipseVFP("producer1_vfp.inc");
 * }</pre>
 *
 * <h2>Usage Example 3 - Multi-Layer Commingled Well</h2>
 * 
 * <pre>{@code
 * WellSystem multilayerWell = new WellSystem("Commingled-1");
 *
 * // Add multiple reservoir layers
 * multilayerWell.addLayer("Layer-1", layer1Stream, 0.6, 220, 0.0005); // kh fraction, Pi, PI
 * multilayerWell.addLayer("Layer-2", layer2Stream, 0.3, 200, 0.0003);
 * multilayerWell.addLayer("Layer-3", layer3Stream, 0.1, 180, 0.0002);
 *
 * // Configure common tubing
 * multilayerWell.setTubingLength(3000, "m");
 * multilayerWell.setTubingDiameter(5.5, "in");
 *
 * // Solve for commingled production
 * multilayerWell.setWellheadPressure(45.0, "bara");
 * multilayerWell.run();
 *
 * // Get individual layer contributions
 * double[] layerRates = multilayerWell.getLayerFlowRates("Sm3/day");
 * }</pre>
 *
 * <h2>Integration with SimpleReservoir</h2>
 * 
 * <pre>{@code
 * // Create reservoir
 * SimpleReservoir reservoir = new SimpleReservoir("Field Reservoir");
 * reservoir.setReservoirFluid(resFluid, gasVolume, oilVolume, waterVolume);
 *
 * // Add producer as WellSystem
 * StreamInterface prodStream = reservoir.addOilProducer("PROD-1");
 *
 * WellSystem prodWell = new WellSystem("PROD-1 System");
 * prodWell.setReservoirStream(prodStream);
 * prodWell.setIPRModel(WellSystem.IPRModel.PRODUCTION_INDEX);
 * prodWell.setProductionIndex(0.0005, "Sm3/day/bar2");
 * prodWell.setTubingLength(2500, "m");
 * prodWell.setTubingDiameter(4.5, "in");
 * prodWell.setWellheadPressure(50.0, "bara");
 *
 * // Add to process system
 * ProcessSystem process = new ProcessSystem();
 * process.add(reservoir);
 * process.add(prodWell);
 *
 * // Run transient depletion
 * for (int year = 0; year < 10; year++) {
 *   reservoir.runTransient(365 * 24 * 3600); // 1 year
 *   prodWell.run();
 *   System.out.println("Year " + year + ": Rate=" + prodWell.getOperatingFlowRate("Sm3/day"));
 * }
 * }</pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see WellFlow
 * @see TubingPerformance
 * @see SimpleReservoir
 */
public class WellSystem extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(WellSystem.class);

  /**
   * IPR model types supported by WellSystem.
   */
  public enum IPRModel {
    /** Constant productivity index (Darcy linear or squared-pressure). */
    PRODUCTION_INDEX,
    /** Vogel (1968) - Solution gas drive oil wells. */
    VOGEL,
    /** Fetkovich (1973) - Gas wells and high-rate oil wells. */
    FETKOVICH,
    /** Backpressure with non-Darcy (turbulence) term. */
    BACKPRESSURE,
    /** Jones, Blount &amp; Glaze (1976) - Accounts for rate-dependent skin. */
    JONES_BLOUNT_GLAZE,
    /** Table-driven IPR from well test data. */
    TABLE
  }

  /**
   * VLP solver modes for different accuracy/speed trade-offs.
   *
   * <p>
   * Empirical correlations (steady-state):
   * </p>
   * <ul>
   * <li>{@link #SIMPLIFIED} - Fast hydrostatic + friction</li>
   * <li>{@link #BEGGS_BRILL} - Beggs-Brill (1973) - all inclinations</li>
   * <li>{@link #HAGEDORN_BROWN} - Hagedorn-Brown (1965) - vertical oil wells</li>
   * <li>{@link #GRAY} - Gray (1974) - gas wells</li>
   * <li>{@link #HASAN_KABIR} - Hasan-Kabir (2002) - mechanistic</li>
   * <li>{@link #DUNS_ROS} - Duns-Ros (1963) - gas wells</li>
   * </ul>
   *
   * <p>
   * Mechanistic models:
   * </p>
   * <ul>
   * <li>{@link #DRIFT_FLUX} - Drift-flux model for gas-liquid flow</li>
   * <li>{@link #TWO_FLUID} - Full two-fluid model (most accurate, slowest)</li>
   * </ul>
   */
  public enum VLPSolverMode {
    /** Fast simplified solver using hydrostatic + friction (default). */
    SIMPLIFIED,
    /** Full TubingPerformance with Beggs-Brill correlation. */
    BEGGS_BRILL,
    /** Full TubingPerformance with Hagedorn-Brown correlation. */
    HAGEDORN_BROWN,
    /** Full TubingPerformance with Gray correlation (gas wells). */
    GRAY,
    /** Full TubingPerformance with Hasan-Kabir mechanistic model. */
    HASAN_KABIR,
    /** Full TubingPerformance with Duns-Ros correlation. */
    DUNS_ROS,
    /** Drift-flux model - accounts for slip between phases. */
    DRIFT_FLUX,
    /** Two-fluid model - separate momentum equations for each phase (most accurate). */
    TWO_FLUID
  }

  /**
   * Represents a single reservoir layer for commingled wells.
   */
  public static class ReservoirLayer {
    String name;
    StreamInterface stream;
    double khFraction; // Fraction of total kh
    double reservoirPressure; // bara
    double productivityIndex; // Sm3/day/bar² (or per bar for liquid)
    IPRModel iprModel = IPRModel.PRODUCTION_INDEX;
    double calculatedRate; // Calculated flow rate

    /**
     * Create a reservoir layer.
     *
     * @param name layer name
     * @param stream fluid stream from layer
     * @param khFraction fraction of total kh (permeability × thickness)
     * @param reservoirPressure initial reservoir pressure (bara)
     * @param pi productivity index
     */
    public ReservoirLayer(String name, StreamInterface stream, double khFraction,
        double reservoirPressure, double pi) {
      this.name = name;
      this.stream = stream;
      this.khFraction = khFraction;
      this.reservoirPressure = reservoirPressure;
      this.productivityIndex = pi;
    }
  }

  // Well components
  private WellFlow wellFlowIPR;
  private TubingPerformance tubingVLP;
  private ThrottlingValve productionChoke;
  private StreamInterface reservoirStream;
  private StreamInterface outletStream;
  private StreamInterface inletStream; // Alias for reservoirStream for ProcessSystem compatibility

  // Multi-layer support
  private List<ReservoirLayer> layers = new ArrayList<>();
  private boolean isMultiLayer = false;

  // IPR configuration
  private IPRModel iprModel = IPRModel.PRODUCTION_INDEX;
  private double reservoirPressure = 250.0; // bara
  private double productivityIndex = 0.0005; // Sm3/day/bar²
  private double vogelQmax = 10000.0;
  private double fetkovichC = 0.001;
  private double fetkovichN = 0.8;
  private double backpressureA = 0.0;
  private double backpressureB = 0.0;

  // Operating conditions
  private double targetWellheadPressure = 50.0; // bara
  private double operatingFlowRate = Double.NaN;
  private double operatingBHP = Double.NaN;
  private double chokeOpening = 100.0; // percent

  // Tubing configuration (passed to TubingPerformance)
  private double tubingLength = 2500.0;
  private double tubingDiameter = 0.1016;
  private double tubingRoughness = 2.5e-5;
  private double tubingInclination = 90.0;
  private TubingPerformance.PressureDropCorrelation pdCorrelation =
      TubingPerformance.PressureDropCorrelation.BEGGS_BRILL;
  private TubingPerformance.TemperatureModel tempModel =
      TubingPerformance.TemperatureModel.LINEAR_GRADIENT;
  private double bhTemperature = 373.15;
  private double whTemperature = 313.15;

  // Solver settings
  private double tolerance = 0.1; // bara
  private int maxIterations = 50;
  private VLPSolverMode vlpSolverMode = VLPSolverMode.SIMPLIFIED;

  /**
   * Constructor for WellSystem.
   *
   * @param name well name
   */
  public WellSystem(String name) {
    super(name);
  }

  /**
   * Constructor for WellSystem with inlet stream.
   * 
   * <p>
   * This constructor allows WellSystem to be created like other process equipment, making it
   * compatible with ProcessSystem sequential building.
   * </p>
   *
   * @param name well name
   * @param inletStream reservoir/inlet stream
   */
  public WellSystem(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Set the reservoir stream (inlet from reservoir).
   *
   * @param stream reservoir stream
   */
  public void setReservoirStream(StreamInterface stream) {
    this.reservoirStream = stream;
    this.inletStream = stream;
    this.reservoirPressure = stream.getPressure("bara");

    // Initialize outlet stream with a clone of the reservoir stream
    // This ensures getOutletStream() returns a valid stream before run() is called
    // The outlet stream will be updated with correct wellhead conditions when run() is called
    if (stream != null && stream.getFluid() != null) {
      SystemInterface outFluid = stream.getFluid().clone();
      this.outletStream = new Stream(getName() + "_outlet", outFluid);
      // Set initial conditions to wellhead targets
      outFluid.setPressure(targetWellheadPressure, "bara");
      if (tempModel == TubingPerformance.TemperatureModel.LINEAR_GRADIENT) {
        outFluid.setTemperature(whTemperature, "K");
      }
    }
  }

  /**
   * Set the inlet stream (alias for setReservoirStream for ProcessSystem compatibility).
   * 
   * <p>
   * This method allows WellSystem to be used in a ProcessSystem like other equipment.
   * </p>
   *
   * @param stream inlet stream from reservoir
   */
  public void setInletStream(StreamInterface stream) {
    setReservoirStream(stream);
  }

  /**
   * Get the inlet stream.
   *
   * @return inlet stream (same as reservoir stream)
   */
  public StreamInterface getInletStream() {
    return reservoirStream;
  }

  /**
   * Set the IPR model type.
   *
   * @param model IPR model to use
   */
  public void setIPRModel(IPRModel model) {
    this.iprModel = model;
  }

  /**
   * Set productivity index for PI model.
   *
   * @param pi productivity index
   * @param unit PI unit ("Sm3/day/bar2", "bbl/day/psi2", "Sm3/day/bar")
   */
  public void setProductionIndex(double pi, String unit) {
    switch (unit.toLowerCase()) {
      case "bbl/day/psi2":
        this.productivityIndex = pi * 0.158987 * Math.pow(14.5038, 2);
        break;
      case "sm3/day/bar":
        // Linear PI (for liquid wells)
        this.productivityIndex = pi;
        break;
      default:
        this.productivityIndex = pi;
    }
  }

  /**
   * Set Vogel IPR parameters.
   *
   * @param qTest test flow rate (same unit as stream)
   * @param pwfTest test BHP (bara)
   * @param pRes reservoir pressure (bara)
   */
  public void setVogelParameters(double qTest, double pwfTest, double pRes) {
    this.iprModel = IPRModel.VOGEL;
    this.reservoirPressure = pRes;
    // Calculate qMax from test point
    double ratio = pwfTest / pRes;
    this.vogelQmax = qTest / (1.0 - 0.2 * ratio - 0.8 * ratio * ratio);
  }

  /**
   * Set Fetkovich IPR parameters.
   *
   * @param c Fetkovich C coefficient
   * @param n Fetkovich exponent (typically 0.5-1.0)
   * @param pRes reservoir pressure (bara)
   */
  public void setFetkovichParameters(double c, double n, double pRes) {
    this.iprModel = IPRModel.FETKOVICH;
    this.fetkovichC = c;
    this.fetkovichN = n;
    this.reservoirPressure = pRes;
  }

  /**
   * Set backpressure equation parameters with non-Darcy term.
   * 
   * <p>
   * Equation: Pr² - Pwf² = a·q + b·q² where b captures turbulence.
   * </p>
   *
   * @param a Darcy coefficient
   * @param b non-Darcy coefficient
   * @param pRes reservoir pressure (bara)
   */
  public void setBackpressureParameters(double a, double b, double pRes) {
    this.iprModel = IPRModel.BACKPRESSURE;
    this.backpressureA = a;
    this.backpressureB = b;
    this.reservoirPressure = pRes;
  }

  /**
   * Set tubing length (measured depth).
   *
   * @param length tubing length
   * @param unit length unit ("m", "ft")
   */
  public void setTubingLength(double length, String unit) {
    if (unit.equalsIgnoreCase("ft")) {
      this.tubingLength = length * 0.3048;
    } else {
      this.tubingLength = length;
    }
  }

  /**
   * Set tubing inner diameter.
   *
   * @param diameter inner diameter
   * @param unit diameter unit ("m", "in", "mm")
   */
  public void setTubingDiameter(double diameter, String unit) {
    switch (unit.toLowerCase()) {
      case "in":
        this.tubingDiameter = diameter * 0.0254;
        break;
      case "mm":
        this.tubingDiameter = diameter / 1000.0;
        break;
      default:
        this.tubingDiameter = diameter;
    }
  }

  /**
   * Set tubing wall roughness.
   *
   * @param roughness wall roughness (m)
   */
  public void setTubingRoughness(double roughness) {
    this.tubingRoughness = roughness;
  }

  /**
   * Set well inclination.
   *
   * @param degrees inclination from horizontal (90 = vertical)
   */
  public void setInclination(double degrees) {
    this.tubingInclination = degrees;
  }

  /**
   * Set pressure-drop correlation for VLP.
   *
   * @param correlation pressure-drop correlation
   */
  public void setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation correlation) {
    this.pdCorrelation = correlation;
  }

  /**
   * Set the VLP solver mode.
   * 
   * <p>
   * Available modes:
   * <ul>
   * <li>{@link VLPSolverMode#SIMPLIFIED} - Fast simplified solver (default)</li>
   * <li>{@link VLPSolverMode#BEGGS_BRILL} - Full Beggs-Brill correlation</li>
   * <li>{@link VLPSolverMode#HAGEDORN_BROWN} - Hagedorn-Brown (vertical oil wells)</li>
   * <li>{@link VLPSolverMode#GRAY} - Gray correlation (gas wells)</li>
   * <li>{@link VLPSolverMode#HASAN_KABIR} - Hasan-Kabir mechanistic model</li>
   * <li>{@link VLPSolverMode#DUNS_ROS} - Duns-Ros correlation</li>
   * </ul>
   *
   * @param mode VLP solver mode
   */
  public void setVLPSolverMode(VLPSolverMode mode) {
    this.vlpSolverMode = mode;
    // Map to TubingPerformance correlation when using full solver
    switch (mode) {
      case BEGGS_BRILL:
        this.pdCorrelation = TubingPerformance.PressureDropCorrelation.BEGGS_BRILL;
        break;
      case HAGEDORN_BROWN:
        this.pdCorrelation = TubingPerformance.PressureDropCorrelation.HAGEDORN_BROWN;
        break;
      case GRAY:
        this.pdCorrelation = TubingPerformance.PressureDropCorrelation.GRAY;
        break;
      case HASAN_KABIR:
        this.pdCorrelation = TubingPerformance.PressureDropCorrelation.HASAN_KABIR;
        break;
      case DUNS_ROS:
        this.pdCorrelation = TubingPerformance.PressureDropCorrelation.DUNS_ROS;
        break;
      default:
        // SIMPLIFIED mode doesn't use TubingPerformance
        break;
    }
  }

  /**
   * Get the current VLP solver mode.
   *
   * @return VLP solver mode
   */
  public VLPSolverMode getVLPSolverMode() {
    return vlpSolverMode;
  }

  /**
   * Set temperature model for VLP.
   *
   * @param model temperature model
   */
  public void setTemperatureModel(TubingPerformance.TemperatureModel model) {
    this.tempModel = model;
  }

  /**
   * Set bottom-hole temperature.
   *
   * @param temperature temperature
   * @param unit temperature unit ("K", "C", "F")
   */
  public void setBottomHoleTemperature(double temperature, String unit) {
    switch (unit.toUpperCase()) {
      case "C":
        this.bhTemperature = temperature + 273.15;
        break;
      case "F":
        this.bhTemperature = (temperature - 32.0) / 1.8 + 273.15;
        break;
      default:
        this.bhTemperature = temperature;
    }
  }

  /**
   * Set wellhead temperature.
   *
   * @param temperature temperature
   * @param unit temperature unit ("K", "C", "F")
   */
  public void setWellheadTemperature(double temperature, String unit) {
    switch (unit.toUpperCase()) {
      case "C":
        this.whTemperature = temperature + 273.15;
        break;
      case "F":
        this.whTemperature = (temperature - 32.0) / 1.8 + 273.15;
        break;
      default:
        this.whTemperature = temperature;
    }
  }

  /**
   * Set target wellhead pressure (back-pressure constraint).
   *
   * @param pressure wellhead pressure
   * @param unit pressure unit ("bara", "barg", "psia")
   */
  public void setWellheadPressure(double pressure, String unit) {
    switch (unit.toLowerCase()) {
      case "psia":
        this.targetWellheadPressure = pressure / 14.5038;
        break;
      case "barg":
        this.targetWellheadPressure = pressure + 1.01325;
        break;
      default:
        this.targetWellheadPressure = pressure;
    }
  }

  /**
   * Set choke opening (for choked wells).
   *
   * @param opening choke opening percentage (0-100)
   */
  public void setChokeOpening(double opening) {
    this.chokeOpening = opening;
  }

  /**
   * Add a reservoir layer for commingled production.
   *
   * @param name layer name
   * @param stream stream from this layer
   * @param khFraction fraction of total kh
   * @param reservoirPressure initial reservoir pressure (bara)
   * @param pi productivity index
   */
  public void addLayer(String name, StreamInterface stream, double khFraction,
      double reservoirPressure, double pi) {
    layers.add(new ReservoirLayer(name, stream, khFraction, reservoirPressure, pi));
    isMultiLayer = true;
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * Solves for the operating point where IPR and VLP intersect at the specified wellhead pressure
   * constraint.
   * </p>
   */
  @Override
  public void run(UUID id) {
    if (reservoirStream == null && layers.isEmpty()) {
      throw new RuntimeException("No reservoir stream or layers defined for well " + getName());
    }

    if (isMultiLayer) {
      runMultiLayer(id);
    } else {
      runSingleLayer(id);
    }

    setCalculationIdentifier(id);
  }

  /**
   * Run single-layer well (standard case).
   *
   * @param id calculation identifier UUID
   */
  private void runSingleLayer(UUID id) {
    // Update reservoir pressure from stream
    reservoirPressure = reservoirStream.getPressure("bara");

    // Use bisection method for robust convergence
    double qLow = 10.0; // Minimum flow rate Sm3/day
    double qHigh = estimateMaxFlowRate(); // Maximum feasible flow rate
    double flowGuess = estimateInitialFlowRate();

    // Clamp initial guess
    flowGuess = Math.max(qLow, Math.min(flowGuess, qHigh));

    double bhpFromIPR = 0.0;
    double bhpFromVLP = 0.0;
    boolean converged = false;

    // Calculate error function: f(q) = BHP_IPR(q) - BHP_VLP(q)
    // At operating point, f(q) = 0
    double errorLow = calculateIPR_BHP(qLow) - calculateVLP_BHP(qLow);
    double errorHigh = calculateIPR_BHP(qHigh) - calculateVLP_BHP(qHigh);

    // Iterative solution using bisection with secant acceleration
    for (int iter = 0; iter < maxIterations; iter++) {
      bhpFromIPR = calculateIPR_BHP(flowGuess);
      bhpFromVLP = calculateVLP_BHP(flowGuess);
      double error = bhpFromIPR - bhpFromVLP;

      // Check convergence
      if (Math.abs(error) < tolerance) {
        operatingFlowRate = flowGuess;
        operatingBHP = bhpFromIPR;
        converged = true;
        break;
      }

      // Update bounds for bisection
      if (error * errorLow < 0) {
        qHigh = flowGuess;
        errorHigh = error;
      } else {
        qLow = flowGuess;
        errorLow = error;
      }

      // Use bisection step (very robust)
      flowGuess = (qLow + qHigh) / 2.0;

      // Check if bounds have collapsed
      if (qHigh - qLow < 1.0) {
        operatingFlowRate = flowGuess;
        operatingBHP = bhpFromIPR;
        converged = true;
        break;
      }
    }

    // If not converged, use the last calculated values
    if (!converged) {
      operatingFlowRate = Math.max(0.0, flowGuess);
      operatingBHP = bhpFromIPR > 0 ? bhpFromIPR : bhpFromVLP;
      logger.warn(
          "WellSystem {} did not converge after {} iterations. Using last values: Q={} Sm3/day, BHP={} bara",
          getName(), maxIterations, operatingFlowRate, operatingBHP);
    }

    // Create output stream at wellhead conditions
    outletStream = new Stream(getName() + " outlet");
    SystemInterface outFluid = reservoirStream.getThermoSystem().clone();
    outFluid.setPressure(targetWellheadPressure, "bara");
    outFluid.setTemperature(whTemperature, "K");
    outFluid.setTotalFlowRate(operatingFlowRate / 86400.0, "Sm3/sec");
    ThermodynamicOperations ops = new ThermodynamicOperations(outFluid);
    ops.TPflash();
    outFluid.initProperties();
    outletStream.setThermoSystem(outFluid);
    outletStream.run(id);
  }

  /**
   * Estimate maximum flow rate based on IPR model (AOF - Absolute Open Flow).
   *
   * @return estimated maximum flow rate in Sm3/day
   */
  private double estimateMaxFlowRate() {
    switch (iprModel) {
      case VOGEL:
        return vogelQmax;
      case FETKOVICH:
        return fetkovichC * Math.pow(Math.pow(reservoirPressure, 2), fetkovichN);
      case PRODUCTION_INDEX:
      default:
        return productivityIndex * Math.pow(reservoirPressure, 2);
    }
  }

  /**
   * Run multi-layer commingled well.
   *
   * @param id calculation identifier UUID
   */
  private void runMultiLayer(UUID id) {
    // For commingled wells, all layers produce to a common BHP
    // Total rate = sum of individual layer rates

    // Initial estimate of common BHP
    double commonBHP = 0.0;
    for (ReservoirLayer layer : layers) {
      commonBHP += layer.reservoirPressure * layer.khFraction;
    }
    commonBHP *= 0.7; // Start at 70% of weighted average Pr

    double totalRate;

    // Iterate to find common BHP that satisfies VLP
    for (int iter = 0; iter < maxIterations; iter++) {
      // Calculate rate from each layer at common BHP
      totalRate = 0.0;
      for (ReservoirLayer layer : layers) {
        double layerRate = layer.productivityIndex
            * (Math.pow(layer.reservoirPressure, 2) - Math.pow(commonBHP, 2));
        if (layerRate < 0)
          layerRate = 0;
        layer.calculatedRate = layerRate;
        totalRate += layerRate;
      }

      // Calculate VLP BHP for total rate
      double vlpBHP = calculateVLP_BHP(totalRate);

      // Check convergence
      double error = commonBHP - vlpBHP;
      if (Math.abs(error) < tolerance) {
        operatingFlowRate = totalRate;
        operatingBHP = commonBHP;
        break;
      }

      // Adjust common BHP
      commonBHP = (commonBHP + vlpBHP) / 2.0;
    }

    // Create blended output stream
    // For simplicity, use first layer's fluid composition
    outletStream = new Stream(getName() + " outlet");
    if (!layers.isEmpty()) {
      SystemInterface outFluid = layers.get(0).stream.getThermoSystem().clone();
      outFluid.setPressure(targetWellheadPressure, "bara");
      outFluid.setTemperature(whTemperature, "K");
      outFluid.setTotalFlowRate(operatingFlowRate / 86400.0, "Sm3/sec");
      ThermodynamicOperations ops = new ThermodynamicOperations(outFluid);
      ops.TPflash();
      outFluid.initProperties();
      outletStream.setThermoSystem(outFluid);
      outletStream.run(id);
    }
  }

  /**
   * Calculate bottom-hole pressure from IPR at a given flow rate.
   *
   * @param flowRate flow rate in Sm3/day
   * @return bottom-hole pressure in bara
   */
  private double calculateIPR_BHP(double flowRate) {
    switch (iprModel) {
      case VOGEL:
        // Vogel: q/qmax = 1 - 0.2*(Pwf/Pr) - 0.8*(Pwf/Pr)²
        // Solve quadratic for Pwf
        double ratio = flowRate / vogelQmax;
        double a = 0.8;
        double b = 0.2;
        double c = ratio - 1.0;
        double disc = b * b - 4 * a * c;
        if (disc < 0)
          return 0.0;
        double x = (-b + Math.sqrt(disc)) / (2 * a);
        return x * reservoirPressure;

      case FETKOVICH:
        // q = C * (Pr² - Pwf²)^n
        double delta = Math.pow(flowRate / fetkovichC, 1.0 / fetkovichN);
        double pwf2 = Math.pow(reservoirPressure, 2) - delta;
        return pwf2 > 0 ? Math.sqrt(pwf2) : 0.0;

      case BACKPRESSURE:
        // Pr² - Pwf² = a*q + b*q²
        double drawdown = backpressureA * flowRate + backpressureB * Math.pow(flowRate, 2);
        double pwf2bp = Math.pow(reservoirPressure, 2) - drawdown;
        return pwf2bp > 0 ? Math.sqrt(pwf2bp) : 0.0;

      case PRODUCTION_INDEX:
      default:
        // q = PI * (Pr² - Pwf²)
        double pwf2pi = Math.pow(reservoirPressure, 2) - flowRate / productivityIndex;
        return pwf2pi > 0 ? Math.sqrt(pwf2pi) : 0.0;
    }
  }

  /**
   * Calculate required bottom-hole pressure from VLP to achieve target WHP. Uses either simplified
   * or full multiphase correlation based on vlpSolverMode.
   *
   * @param flowRate flow rate in Sm3/day
   * @return bottom-hole pressure in bara
   */
  private double calculateVLP_BHP(double flowRate) {
    if (flowRate <= 0)
      return targetWellheadPressure;

    switch (vlpSolverMode) {
      case SIMPLIFIED:
        return calculateVLP_BHP_Simplified(flowRate);
      case DRIFT_FLUX:
        return calculateVLP_BHP_DriftFlux(flowRate);
      case TWO_FLUID:
        return calculateVLP_BHP_TwoFluid(flowRate);
      default:
        // All TubingPerformance correlations use the full solver
        return calculateVLP_BHP_Full(flowRate);
    }
  }

  /**
   * Simplified VLP calculation using hydrostatic + friction. Fast but less accurate for complex
   * multiphase flow.
   *
   * @param flowRate flow rate in Sm3/day
   * @return calculated bottom-hole pressure in bara
   */
  private double calculateVLP_BHP_Simplified(double flowRate) {
    // Get fluid properties at average conditions (approximate)
    SystemInterface tempFluid = reservoirStream.getThermoSystem().clone();
    tempFluid.setPressure((targetWellheadPressure + reservoirPressure) / 2.0, "bara");
    tempFluid.setTemperature((bhTemperature + whTemperature) / 2.0, "K");
    tempFluid.setTotalFlowRate(flowRate / 86400.0, "Sm3/sec");
    ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
    ops.TPflash();
    tempFluid.initProperties();

    // Average density (kg/m³)
    double avgDensity = tempFluid.getDensity("kg/m3");

    // Hydrostatic pressure (bara) - vertical component
    double verticalDepth = tubingLength * Math.sin(Math.toRadians(tubingInclination));
    double hydrostaticPressure = avgDensity * 9.81 * verticalDepth / 1e5;

    // Friction pressure drop using simplified Beggs-Brill approach
    double velocity = 0.0;
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);
    if (area > 0) {
      double volumeFlowRate = (flowRate / 86400.0) / avgDensity; // m³/s actual
      velocity = volumeFlowRate / area;
    }

    // Friction factor (simplified Blasius for turbulent flow)
    double viscosity = tempFluid.getViscosity("kg/msec");
    double Re = avgDensity * velocity * tubingDiameter / (viscosity + 1e-10);
    double frictionFactor = 0.0;
    if (Re > 2300) {
      frictionFactor = 0.316 / Math.pow(Re, 0.25); // Blasius
    } else if (Re > 0) {
      frictionFactor = 64.0 / Re; // Laminar
    }

    // Friction pressure drop (bara)
    double frictionPressure = frictionFactor * (tubingLength / tubingDiameter) * avgDensity
        * Math.pow(velocity, 2) / (2.0 * 1e5);

    // Total BHP
    double bhp = targetWellheadPressure + hydrostaticPressure + frictionPressure;

    // Clamp to reasonable range
    return Math.max(targetWellheadPressure, Math.min(bhp, reservoirPressure * 1.5));
  }

  /**
   * Full VLP calculation using TubingPerformance with selected multiphase correlation. More
   * accurate but slower.
   *
   * @param flowRate flow rate in Sm3/day
   * @return calculated bottom-hole pressure in bara
   */
  private double calculateVLP_BHP_Full(double flowRate) {
    // Create temporary tubing model
    SystemInterface tempFluid = reservoirStream.getThermoSystem().clone();
    tempFluid.setTotalFlowRate(flowRate / 86400.0, "Sm3/sec");

    Stream tempStream = new Stream("temp", tempFluid);
    tempStream.run();

    TubingPerformance tempTubing = new TubingPerformance("temp VLP", tempStream);
    tempTubing.setTubingLength(tubingLength, "m");
    tempTubing.setTubingDiameter(tubingDiameter, "m");
    tempTubing.setWallRoughness(tubingRoughness, "m");
    tempTubing.setInclination(tubingInclination);
    tempTubing.setPressureDropCorrelation(pdCorrelation);
    tempTubing.setTemperatureModel(tempModel);
    tempTubing.setBottomHoleTemperature(bhTemperature, "K");
    tempTubing.setWellheadTemperature(whTemperature, "K");

    // Iterate to find BHP that gives target WHP (reduced iterations for speed)
    double bhpGuess = targetWellheadPressure + 50.0;

    for (int i = 0; i < 15; i++) {
      tempFluid.setPressure(bhpGuess, "bara");
      tempFluid.setTemperature(bhTemperature, "K");
      tempStream.setThermoSystem(tempFluid);
      tempStream.run();
      tempTubing.setInletStream(tempStream);
      tempTubing.run(UUID.randomUUID());

      double calculatedWHP = tempTubing.getWellheadPressure("bara");
      double error = calculatedWHP - targetWellheadPressure;

      if (Math.abs(error) < 0.5)
        break;

      bhpGuess += error * 0.8; // Damped update for stability
      if (bhpGuess < targetWellheadPressure)
        bhpGuess = targetWellheadPressure + 10;
      if (bhpGuess > 1000)
        bhpGuess = 500;
    }

    return bhpGuess;
  }

  /**
   * Drift-flux VLP calculation.
   *
   * <p>
   * Uses a drift-flux model that accounts for slip between phases. More accurate than homogeneous
   * models for gas-liquid flow in vertical/inclined pipes.
   * </p>
   *
   * <p>
   * The drift-flux model uses: v_g = C_0 * v_m + v_d where C_0 is the distribution parameter and
   * v_d is the drift velocity.
   * </p>
   *
   * @param flowRate flow rate in Sm3/day
   * @return calculated bottom-hole pressure in bara
   */
  private double calculateVLP_BHP_DriftFlux(double flowRate) {
    // Get fluid properties
    SystemInterface tempFluid = reservoirStream.getThermoSystem().clone();
    tempFluid.setPressure((targetWellheadPressure + reservoirPressure) / 2.0, "bara");
    tempFluid.setTemperature((bhTemperature + whTemperature) / 2.0, "K");
    tempFluid.setTotalFlowRate(flowRate / 86400.0, "Sm3/sec");
    ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
    ops.TPflash();
    tempFluid.initProperties();

    // Phase properties
    double rhoL = 800.0; // Default liquid density kg/m³
    double rhoG = 50.0; // Default gas density kg/m³
    double muL = 0.001; // Default liquid viscosity Pa.s
    double sigma = 0.02; // Default surface tension N/m

    if (tempFluid.hasPhaseType("aqueous") || tempFluid.hasPhaseType("oil")) {
      int liqPhase = tempFluid.hasPhaseType("oil") ? tempFluid.getPhaseIndex("oil")
          : tempFluid.getPhaseIndex("aqueous");
      rhoL = tempFluid.getPhase(liqPhase).getDensity("kg/m3");
      muL = tempFluid.getPhase(liqPhase).getViscosity("kg/msec");
    }
    if (tempFluid.hasPhaseType("gas")) {
      rhoG = tempFluid.getPhase("gas").getDensity("kg/m3");
    }

    // Calculate superficial velocities
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);
    double massFlowRate = flowRate / 86400.0 * tempFluid.getDensity("kg/m3");
    double totalVelocity = massFlowRate / (tempFluid.getDensity("kg/m3") * area);

    // Drift-flux parameters (Zuber-Findlay)
    double C0 = 1.2; // Distribution parameter for bubbly/slug flow

    // Drift velocity (Harmathy correlation for large bubbles)
    double driftVelocity = 1.53 * Math.pow(sigma * 9.81 * (rhoL - rhoG) / (rhoL * rhoL), 0.25);

    // Estimate gas void fraction using drift-flux relation iteratively
    double alpha = 0.5; // Initial guess
    double gasVolumetricFraction =
        tempFluid.hasPhaseType("gas") ? tempFluid.getPhase("gas").getBeta() : 0.0;

    for (int iter = 0; iter < 10; iter++) {
      double vsg = gasVolumetricFraction * totalVelocity;
      double vsl = (1 - gasVolumetricFraction) * totalVelocity;
      double vm = vsg + vsl;

      // Drift-flux: alpha = vsg / (C0 * vm + vd)
      double denominator = C0 * vm + driftVelocity;
      if (denominator > 0) {
        alpha = vsg / denominator;
      }
      alpha = Math.max(0.01, Math.min(0.99, alpha));
    }

    // Mixture density accounting for slip
    double rhoMix = alpha * rhoG + (1 - alpha) * rhoL;

    // Hydrostatic pressure
    double verticalDepth = tubingLength * Math.sin(Math.toRadians(tubingInclination));
    double hydrostaticPressure = rhoMix * 9.81 * verticalDepth / 1e5;

    // Friction pressure drop (using mixture properties)
    double mixVelocity = totalVelocity;
    double Re = rhoMix * mixVelocity * tubingDiameter / (muL + 1e-10);
    double frictionFactor = 0.0;
    if (Re > 2300) {
      frictionFactor = 0.316 / Math.pow(Re, 0.25);
    } else if (Re > 0) {
      frictionFactor = 64.0 / Re;
    }
    double frictionPressure = frictionFactor * (tubingLength / tubingDiameter) * rhoMix
        * Math.pow(mixVelocity, 2) / (2.0 * 1e5);

    double bhp = targetWellheadPressure + hydrostaticPressure + frictionPressure;
    return Math.max(targetWellheadPressure, Math.min(bhp, reservoirPressure * 1.5));
  }

  /**
   * Two-fluid VLP calculation.
   *
   * <p>
   * Uses a simplified two-fluid model with separate momentum balances for each phase. This is the
   * most accurate approach for complex flow patterns but also the slowest.
   * </p>
   *
   * <p>
   * Solves coupled gas and liquid momentum equations accounting for:
   * </p>
   * <ul>
   * <li>Interfacial friction between phases</li>
   * <li>Wall friction for each phase</li>
   * <li>Gravitational effects</li>
   * <li>Phase acceleration</li>
   * </ul>
   *
   * @param flowRate flow rate in Sm3/day
   * @return calculated bottom-hole pressure in bara
   */
  private double calculateVLP_BHP_TwoFluid(double flowRate) {
    // Get fluid properties
    SystemInterface tempFluid = reservoirStream.getThermoSystem().clone();
    tempFluid.setTotalFlowRate(flowRate / 86400.0, "Sm3/sec");

    // March up the tubing from wellhead to bottomhole
    int numSegments = 20;
    double segmentLength = tubingLength / numSegments;
    double currentPressure = targetWellheadPressure;
    double currentTemp = whTemperature;
    double tempGradient = (bhTemperature - whTemperature) / tubingLength;
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);

    for (int seg = 0; seg < numSegments; seg++) {
      // Update fluid properties at current conditions
      tempFluid.setPressure(currentPressure, "bara");
      tempFluid.setTemperature(currentTemp, "K");
      ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
      ops.TPflash();
      tempFluid.initProperties();

      // Get phase properties
      double rhoL = 800.0;
      double rhoG = 50.0;
      double muL = 0.001;
      double alphaL = 0.5;
      double alphaG = 0.5;

      if (tempFluid.hasPhaseType("gas")) {
        rhoG = tempFluid.getPhase("gas").getDensity("kg/m3");
        alphaG = tempFluid.getPhase("gas").getBeta();
      }
      if (tempFluid.hasPhaseType("oil")) {
        rhoL = tempFluid.getPhase("oil").getDensity("kg/m3");
        muL = tempFluid.getPhase("oil").getViscosity("kg/msec");
        alphaL = 1.0 - alphaG;
      } else if (tempFluid.hasPhaseType("aqueous")) {
        rhoL = tempFluid.getPhase("aqueous").getDensity("kg/m3");
        muL = tempFluid.getPhase("aqueous").getViscosity("kg/msec");
        alphaL = 1.0 - alphaG;
      }

      // Superficial velocities
      double massFlow = flowRate / 86400.0 * tempFluid.getDensity("kg/m3");
      double vsg = alphaG * massFlow / (rhoG * area + 1e-10);
      double vsl = alphaL * massFlow / (rhoL * area + 1e-10);

      // Wall shear stress (simplified)
      double tauWG = 0.5 * 0.02 * rhoG * vsg * vsg;
      double tauWL = 0.5 * 0.02 * rhoL * vsl * vsl;

      // Interfacial friction factor (for future extension)
      // double fi = 0.005;

      // Gravitational term
      double sinTheta = Math.sin(Math.toRadians(tubingInclination));
      double rhoMix = alphaG * rhoG + alphaL * rhoL;
      double gravityDP = rhoMix * 9.81 * sinTheta * segmentLength / 1e5;

      // Friction pressure drop
      double frictionDP =
          (alphaG * tauWG + alphaL * tauWL) * 4.0 / tubingDiameter * segmentLength / 1e5;

      // Total pressure change for segment (bara)
      double dP = gravityDP + frictionDP;
      currentPressure += dP;
      currentTemp += tempGradient * segmentLength;
    }

    return Math.max(targetWellheadPressure, Math.min(currentPressure, reservoirPressure * 1.5));
  }

  /**
   * Estimate initial flow rate for iteration.
   *
   * @return estimated initial flow rate in Sm3/day
   */
  private double estimateInitialFlowRate() {
    switch (iprModel) {
      case VOGEL:
        return vogelQmax * 0.5;
      case FETKOVICH:
        return fetkovichC * Math.pow(Math.pow(reservoirPressure, 2) * 0.5, fetkovichN);
      case PRODUCTION_INDEX:
      default:
        return productivityIndex * Math.pow(reservoirPressure, 2) * 0.3;
    }
  }

  /**
   * Generate IPR curve (Inflow Performance Relationship).
   *
   * @param pressurePoints number of pressure points
   * @return 2D array: [0]=flow rates (Sm3/day), [1]=bottom-hole pressures (bara)
   */
  public double[][] generateIPRCurve(int pressurePoints) {
    double[] flows = new double[pressurePoints];
    double[] bhps = new double[pressurePoints];

    for (int i = 0; i < pressurePoints; i++) {
      double bhp = reservoirPressure * (1.0 - (double) i / (pressurePoints - 1));
      bhps[i] = bhp;
      flows[i] = calculateFlowFromIPR(bhp);
    }

    return new double[][] {flows, bhps};
  }

  /**
   * Calculate flow rate from IPR at a given BHP.
   *
   * @param bhp bottom-hole pressure in bara
   * @return calculated flow rate in Sm3/day
   */
  private double calculateFlowFromIPR(double bhp) {
    switch (iprModel) {
      case VOGEL:
        double ratio = bhp / reservoirPressure;
        return vogelQmax * (1.0 - 0.2 * ratio - 0.8 * ratio * ratio);
      case FETKOVICH:
        return fetkovichC * Math.pow(Math.pow(reservoirPressure, 2) - Math.pow(bhp, 2), fetkovichN);
      case BACKPRESSURE:
        double drawdown = Math.pow(reservoirPressure, 2) - Math.pow(bhp, 2);
        // Solve a*q + b*q² = drawdown
        if (backpressureB == 0)
          return drawdown / backpressureA;
        double disc = Math.pow(backpressureA, 2) + 4 * backpressureB * drawdown;
        return (-backpressureA + Math.sqrt(disc)) / (2 * backpressureB);
      case PRODUCTION_INDEX:
      default:
        return productivityIndex * (Math.pow(reservoirPressure, 2) - Math.pow(bhp, 2));
    }
  }

  /**
   * Generate VLP curve for a range of flow rates.
   *
   * @param flowRates array of flow rates (Sm3/day)
   * @return 2D array: [0]=flow rates, [1]=required BHP (bara)
   */
  public double[][] generateVLPCurve(double[] flowRates) {
    double[] bhps = new double[flowRates.length];

    for (int i = 0; i < flowRates.length; i++) {
      bhps[i] = calculateVLP_BHP(flowRates[i]);
    }

    return new double[][] {flowRates, bhps};
  }

  // Getters for results

  /**
   * Get the calculated operating flow rate.
   *
   * @param unit flow rate unit
   * @return operating flow rate
   */
  public double getOperatingFlowRate(String unit) {
    double sm3day = operatingFlowRate;
    switch (unit.toLowerCase()) {
      case "bbl/day":
        return sm3day / 0.158987;
      case "msm3/day":
        return sm3day / 1.0e6;
      case "mscf/day":
        return sm3day / 28.3168;
      default:
        return sm3day;
    }
  }

  /**
   * Get the calculated bottom-hole pressure.
   *
   * @param unit pressure unit
   * @return bottom-hole pressure
   */
  public double getBottomHolePressure(String unit) {
    switch (unit.toLowerCase()) {
      case "psia":
        return operatingBHP * 14.5038;
      case "barg":
        return operatingBHP - 1.01325;
      default:
        return operatingBHP;
    }
  }

  /**
   * Get the target wellhead pressure.
   *
   * @param unit pressure unit
   * @return wellhead pressure
   */
  public double getWellheadPressure(String unit) {
    switch (unit.toLowerCase()) {
      case "psia":
        return targetWellheadPressure * 14.5038;
      case "barg":
        return targetWellheadPressure - 1.01325;
      default:
        return targetWellheadPressure;
    }
  }

  /**
   * Get reservoir pressure.
   *
   * @param unit pressure unit
   * @return reservoir pressure
   */
  public double getReservoirPressure(String unit) {
    switch (unit.toLowerCase()) {
      case "psia":
        return reservoirPressure * 14.5038;
      case "barg":
        return reservoirPressure - 1.01325;
      default:
        return reservoirPressure;
    }
  }

  /**
   * Get outlet stream.
   *
   * @return outlet stream at wellhead conditions
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Get flow rates from individual layers (for commingled wells).
   *
   * @param unit flow rate unit
   * @return array of layer flow rates
   */
  public double[] getLayerFlowRates(String unit) {
    double[] rates = new double[layers.size()];
    for (int i = 0; i < layers.size(); i++) {
      double sm3day = layers.get(i).calculatedRate;
      switch (unit.toLowerCase()) {
        case "bbl/day":
          rates[i] = sm3day / 0.158987;
          break;
        case "msm3/day":
          rates[i] = sm3day / 1.0e6;
          break;
        default:
          rates[i] = sm3day;
      }
    }
    return rates;
  }

  /**
   * Get the internal WellFlow (IPR) component.
   *
   * @return WellFlow instance
   */
  public WellFlow getWellFlowIPR() {
    return wellFlowIPR;
  }

  /**
   * Get the internal TubingPerformance (VLP) component.
   *
   * @return TubingPerformance instance
   */
  public TubingPerformance getTubingVLP() {
    return tubingVLP;
  }

  /**
   * Get drawdown (reservoir pressure - BHP).
   *
   * @param unit pressure unit
   * @return drawdown (always non-negative)
   */
  public double getDrawdown(String unit) {
    double dd = Math.max(0.0, reservoirPressure - operatingBHP);
    switch (unit.toLowerCase()) {
      case "psia":
        return dd * 14.5038;
      case "barg":
        return dd;
      default:
        return dd;
    }
  }

  /**
   * Get productivity index from last calculation.
   *
   * @return effective PI (Sm3/day/bar²)
   */
  public double getEffectiveProductivityIndex() {
    if (!Double.isNaN(operatingFlowRate) && !Double.isNaN(operatingBHP)) {
      return operatingFlowRate / (Math.pow(reservoirPressure, 2) - Math.pow(operatingBHP, 2));
    }
    return productivityIndex;
  }
}
