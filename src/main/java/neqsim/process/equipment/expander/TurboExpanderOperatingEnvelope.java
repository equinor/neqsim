package neqsim.process.equipment.expander;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * TurboExpanderOperatingEnvelope (P6) generalises a single turndown line into a two-dimensional operating-envelope
 * sweep over inlet pressure and feed flow rate. For each grid point it runs the coupled {@link TurboExpanderCompressor}
 * model and records:
 *
 * <ul>
 * <li><b>Feasibility</b> &mdash; whether the power balance solved with the shaft speed inside its mechanical
 * limits.</li>
 * <li><b>Surge margin</b> &mdash; the compressor Q/N ratio relative to a configurable surge Q/N limit (proxy for
 * distance to surge).</li>
 * <li><b>Cold-end temperature</b> &mdash; the expander outlet temperature, the key cryogenic design constraint.</li>
 * <li><b>Hydrate margin</b> &mdash; expander outlet temperature minus the hydrate formation temperature at the outlet
 * pressure (only when the fluid carries water).</li>
 * </ul>
 *
 * <p>
 * The resulting contour maps are emitted through {@link #toJson()} so they can be consumed directly by the NeqSim
 * report generator and plotted as feasibility / surge / cold-end-T / hydrate-margin contour plots.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TurboExpanderOperatingEnvelope implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(TurboExpanderOperatingEnvelope.class);

  /** The machine to sweep. */
  private TurboExpanderCompressor machine = null;

  /** Inlet pressure grid in bara. */
  private double[] inletPressures = null;

  /** Feed flow grid in kg/hr. */
  private double[] flowRates = null;

  /** Compressor surge Q/N ratio limit (operating below this is surge). */
  private double surgeQnLimit = 0.6;

  /** Maximum allowable shaft speed in rpm for the feasibility check. */
  private double maxAllowableSpeed = 8950.0;

  /** Minimum allowable shaft speed in rpm for the feasibility check. */
  private double minAllowableSpeed = 1100.0;

  // --- Result grids [pressure index][flow index] ---
  /** Feasibility grid. */
  private boolean[][] feasibility = null;
  /** Surge-margin grid (dimensionless). */
  private double[][] surgeMargin = null;
  /** Cold-end (expander outlet) temperature grid in deg C. */
  private double[][] coldEndTemperature = null;
  /** Hydrate-margin grid in deg C. */
  private double[][] hydrateMargin = null;
  /** Shaft-speed grid in rpm. */
  private double[][] shaftSpeed = null;

  /**
   * Default constructor.
   */
  public TurboExpanderOperatingEnvelope() {
  }

  /**
   * Constructs an operating-envelope sweep for the given machine.
   *
   * @param machine the turbo-expander-compressor to sweep
   */
  public TurboExpanderOperatingEnvelope(TurboExpanderCompressor machine) {
    this.machine = machine;
  }

  /**
   * Set the inlet pressure and feed flow grids for the sweep.
   *
   * @param inletPressures inlet pressure grid in bara
   * @param flowRates      feed flow grid in kg/hr
   */
  public void setGrid(double[] inletPressures, double[] flowRates) {
    this.inletPressures = inletPressures == null ? null
	: java.util.Arrays.copyOf(inletPressures, inletPressures.length);
    this.flowRates = flowRates == null ? null : java.util.Arrays.copyOf(flowRates, flowRates.length);
  }

  /**
   * Run the two-dimensional sweep and populate the result grids. The machine inlet stream is set to each pressure/flow
   * combination, the model is run, and the feasibility, surge margin, cold-end temperature and hydrate margin are
   * recorded.
   *
   * @throws IllegalStateException if the machine or grid has not been configured
   */
  public void run() {
    if (machine == null) {
      throw new IllegalStateException("TurboExpanderOperatingEnvelope: machine not set");
    }
    if (inletPressures == null || flowRates == null) {
      throw new IllegalStateException("TurboExpanderOperatingEnvelope: grid not set");
    }
    int nP = inletPressures.length;
    int nF = flowRates.length;
    feasibility = new boolean[nP][nF];
    surgeMargin = new double[nP][nF];
    coldEndTemperature = new double[nP][nF];
    hydrateMargin = new double[nP][nF];
    shaftSpeed = new double[nP][nF];

    StreamInterface inlet = machine.getInletStream();
    StreamInterface compFeed = machine.getCompressorFeedStream();

    for (int i = 0; i < nP; i++) {
      for (int j = 0; j < nF; j++) {
	double pressure = inletPressures[i];
	double flow = flowRates[j];
	boolean feasible = false;
	double margin = Double.NaN;
	double coldT = Double.NaN;
	double hydMargin = Double.NaN;
	double speed = Double.NaN;
	try {
	  inlet.setPressure(pressure, "bara");
	  inlet.setFlowRate(flow, "kg/hr");
	  inlet.run();
	  if (compFeed != null && compFeed != inlet) {
	    compFeed.setPressure(compFeed.getPressure("bara"), "bara");
	    compFeed.run();
	  }
	  machine.run();
	  speed = machine.getSpeed();
	  feasible = speed > minAllowableSpeed && speed < maxAllowableSpeed;
	  double qnComp = machine.getQNratiocompressor();
	  margin = (qnComp - surgeQnLimit) / surgeQnLimit;
	  StreamInterface expOut = machine.getExpanderOutletStream();
	  coldT = expOut.getTemperature("C");
	  hydMargin = calcHydrateMargin(expOut);
	} catch (Exception ex) {
	  logger.debug("Envelope point P=" + pressure + " flow=" + flow + " failed", ex);
	}
	feasibility[i][j] = feasible;
	surgeMargin[i][j] = margin;
	coldEndTemperature[i][j] = coldT;
	hydrateMargin[i][j] = hydMargin;
	shaftSpeed[i][j] = speed;
      }
    }
  }

  /**
   * Compute the hydrate margin (stream temperature minus hydrate formation temperature) for the expander outlet stream.
   * Returns {@link Double#NaN} when the fluid carries no water or the hydrate calculation does not converge.
   *
   * @param stream the expander outlet stream
   * @return the hydrate margin in deg C, or {@link Double#NaN} if not available
   */
  private double calcHydrateMargin(StreamInterface stream) {
    try {
      SystemInterface fluid = stream.getFluid().clone();
      if (!fluid.getPhase(0).hasComponent("water")) {
	return Double.NaN;
      }
      double streamTempC = stream.getTemperature("C");
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();
      double hydrateTempC = fluid.getTemperature("C");
      return streamTempC - hydrateTempC;
    } catch (Exception ex) {
      logger.debug("Hydrate margin not available", ex);
      return Double.NaN;
    }
  }

  /**
   * Build a map of the operating-envelope results suitable for JSON export and reporting.
   *
   * @return an ordered map of the grid axes and result grids
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("inletPressures_bara", inletPressures);
    map.put("flowRates_kghr", flowRates);
    map.put("feasibility", feasibility);
    map.put("surgeMargin", surgeMargin);
    map.put("coldEndTemperature_C", coldEndTemperature);
    map.put("hydrateMargin_C", hydrateMargin);
    map.put("shaftSpeed_rpm", shaftSpeed);
    map.put("surgeQnLimit", surgeQnLimit);
    return map;
  }

  /**
   * Serialise the operating-envelope results to JSON.
   *
   * @return a pretty-printed JSON string of the envelope result grids
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  // --- Getters / setters ---

  /**
   * Get the feasibility grid.
   *
   * @return the feasibility grid [pressure index][flow index], or {@code null} if not yet run
   */
  public boolean[][] getFeasibility() {
    return feasibility;
  }

  /**
   * Get the surge-margin grid.
   *
   * @return the surge-margin grid [pressure index][flow index], or {@code null} if not yet run
   */
  public double[][] getSurgeMargin() {
    return surgeMargin;
  }

  /**
   * Get the cold-end temperature grid.
   *
   * @return the cold-end temperature grid in deg C, or {@code null} if not yet run
   */
  public double[][] getColdEndTemperature() {
    return coldEndTemperature;
  }

  /**
   * Get the hydrate-margin grid.
   *
   * @return the hydrate-margin grid in deg C, or {@code null} if not yet run
   */
  public double[][] getHydrateMargin() {
    return hydrateMargin;
  }

  /**
   * Get the shaft-speed grid.
   *
   * @return the shaft-speed grid in rpm, or {@code null} if not yet run
   */
  public double[][] getShaftSpeed() {
    return shaftSpeed;
  }

  /**
   * Set the machine to sweep.
   *
   * @param machine the turbo-expander-compressor
   */
  public void setMachine(TurboExpanderCompressor machine) {
    this.machine = machine;
  }

  /**
   * Set the compressor surge Q/N ratio limit used for the surge-margin calculation.
   *
   * @param surgeQnLimit the surge Q/N ratio limit
   */
  public void setSurgeQnLimit(double surgeQnLimit) {
    this.surgeQnLimit = surgeQnLimit;
  }

  /**
   * Set the allowable shaft-speed range for the feasibility check.
   *
   * @param minAllowableSpeed the minimum allowable speed in rpm
   * @param maxAllowableSpeed the maximum allowable speed in rpm
   */
  public void setSpeedLimits(double minAllowableSpeed, double maxAllowableSpeed) {
    this.minAllowableSpeed = minAllowableSpeed;
    this.maxAllowableSpeed = maxAllowableSpeed;
  }
}
