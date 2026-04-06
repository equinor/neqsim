package neqsim.mcp.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provenance metadata for every MCP calculation result.
 *
 * <p>
 * Provides trust-relevant context that agents and humans need to assess the credibility of a
 * calculation result. Every MCP response envelope should include a provenance block answering:
 * </p>
 * <ul>
 * <li>Which thermodynamic model (EOS) was used?</li>
 * <li>What mixing rule was applied?</li>
 * <li>What key assumptions were made?</li>
 * <li>Was the result directly computed or interpreted?</li>
 * <li>Did the calculation converge? What quality checks passed?</li>
 * <li>What are the known limitations of this calculation?</li>
 * </ul>
 *
 * <p>
 * This addresses the need for a formal trust model in agentic engineering workflows. An agent
 * consuming NeqSim results can inspect provenance to decide whether to present results as-is, add
 * caveats, or request a more rigorous calculation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ResultProvenance {

  /** The computation engine and version. */
  private String engine = "NeqSim";

  /** The equation of state or thermodynamic model used. */
  private String thermodynamicModel;

  /** The mixing rule applied (e.g., "classic", "HV", "WS"). */
  private String mixingRule;

  /** The type of calculation performed (e.g., "TP flash", "process simulation"). */
  private String calculationType;

  /** Whether the result is a direct computation or derived/interpreted. */
  private String resultOrigin = "direct_computation";

  /** Whether the calculation converged successfully. */
  private boolean converged = true;

  /** Validation checks that were applied and their results. */
  private List<String> validationsPassed;

  /** Key assumptions made during the calculation. */
  private List<String> assumptions;

  /** Known limitations that apply to this result. */
  private List<String> limitations;

  /** Timestamp of the calculation (ISO-8601). */
  private String timestamp;

  /** Computation time in milliseconds. */
  private long computationTimeMs;

  /**
   * Creates a new provenance instance with default values.
   */
  public ResultProvenance() {
    this.validationsPassed = new ArrayList<String>();
    this.assumptions = new ArrayList<String>();
    this.limitations = new ArrayList<String>();
    this.timestamp = Instant.now().toString();
  }

  /**
   * Creates a provenance for a flash calculation.
   *
   * @param model the EOS model name (e.g., "SRK", "PR", "CPA")
   * @param flashType the flash type (e.g., "TP", "PH", "dewPointT")
   * @param mixingRule the mixing rule used
   * @return the configured provenance
   */
  public static ResultProvenance forFlash(String model, String flashType, String mixingRule) {
    ResultProvenance p = new ResultProvenance();
    p.thermodynamicModel = model;
    p.calculationType = flashType + " flash";
    p.mixingRule = mixingRule != null ? mixingRule : "classic";
    p.addAssumption("Ideal gas reference state");
    p.addAssumption("Van der Waals one-fluid mixing rules");

    if ("SRK".equalsIgnoreCase(model)) {
      p.addLimitation("SRK EOS may under-predict liquid densities by 5-15%");
      p.addLimitation("Less accurate for polar/associating compounds (use CPA instead)");
    } else if ("PR".equalsIgnoreCase(model)) {
      p.addLimitation("PR EOS may under-predict liquid densities by 3-10%");
      p.addLimitation("Volume translation not applied unless explicitly configured");
    } else if ("CPA".equalsIgnoreCase(model)) {
      p.addAssumption("CPA association parameters from NeqSim database");
      p.addLimitation("CPA is slower than cubic EOS; use SRK/PR for non-associating systems");
    } else if ("GERG2008".equalsIgnoreCase(model)) {
      p.addAssumption("GERG-2008 reference equation for natural gas");
      p.addLimitation("Valid only for natural gas components (C1-C10, N2, CO2, H2S, H2, He, Ar)");
      p.addLimitation("Temperature range: 60-700 K; Pressure range: up to 70 MPa");
    }

    return p;
  }

  /**
   * Creates a provenance for a process simulation.
   *
   * @param model the EOS model name
   * @param mixingRule the mixing rule used
   * @param equipmentCount the number of equipment units
   * @return the configured provenance
   */
  public static ResultProvenance forProcess(String model, String mixingRule, int equipmentCount) {
    ResultProvenance p = new ResultProvenance();
    p.thermodynamicModel = model;
    p.calculationType = "steady-state process simulation";
    p.mixingRule = mixingRule != null ? mixingRule : "classic";
    p.addAssumption("Steady-state operation");
    p.addAssumption("Thermodynamic equilibrium at each stage");
    p.addAssumption("Adiabatic equipment unless heat duty specified");
    p.addLimitation("Equipment " + equipmentCount + " units — verify mass/energy balance closure");
    return p;
  }

  /**
   * Creates a provenance for a property table calculation.
   *
   * @param model the EOS model name
   * @param sweepVariable the variable being swept (e.g., "temperature", "pressure")
   * @param pointCount the number of data points
   * @return the configured provenance
   */
  public static ResultProvenance forPropertyTable(String model, String sweepVariable,
      int pointCount) {
    ResultProvenance p = new ResultProvenance();
    p.thermodynamicModel = model;
    p.calculationType = "property table (" + sweepVariable + " sweep, " + pointCount + " points)";
    p.mixingRule = "classic";
    p.addAssumption("Each point is an independent equilibrium calculation");
    p.addAssumption("Phase identification at each point");
    p.addLimitation("Phase transitions may cause discontinuities in property profiles");
    return p;
  }

  /**
   * Creates a provenance for a phase envelope calculation.
   *
   * @param model the EOS model name
   * @return the configured provenance
   */
  public static ResultProvenance forPhaseEnvelope(String model) {
    ResultProvenance p = new ResultProvenance();
    p.thermodynamicModel = model;
    p.calculationType = "phase envelope (PT diagram)";
    p.mixingRule = "classic";
    p.addAssumption("Bubble and dew point curves traced by successive flash calculations");
    p.addLimitation("Cricondenbar and cricondentherm accuracy depends on grid resolution");
    p.addLimitation("Near-critical region may have reduced accuracy");
    return p;
  }

  /**
   * Adds an assumption to the provenance.
   *
   * @param assumption the assumption description
   */
  public void addAssumption(String assumption) {
    this.assumptions.add(assumption);
  }

  /**
   * Adds a limitation to the provenance.
   *
   * @param limitation the limitation description
   */
  public void addLimitation(String limitation) {
    this.limitations.add(limitation);
  }

  /**
   * Adds a passed validation check.
   *
   * @param validation the validation check name
   */
  public void addValidationPassed(String validation) {
    this.validationsPassed.add(validation);
  }

  /**
   * Sets the convergence status.
   *
   * @param converged true if the calculation converged
   */
  public void setConverged(boolean converged) {
    this.converged = converged;
  }

  /**
   * Sets the computation time.
   *
   * @param ms computation time in milliseconds
   */
  public void setComputationTimeMs(long ms) {
    this.computationTimeMs = ms;
  }

  /**
   * Sets the result origin.
   *
   * @param origin "direct_computation", "interpolated", or "extrapolated"
   */
  public void setResultOrigin(String origin) {
    this.resultOrigin = origin;
  }

  /**
   * Sets the thermodynamic model name.
   *
   * @param model the EOS model name
   */
  public void setThermodynamicModel(String model) {
    this.thermodynamicModel = model;
  }

  /**
   * Sets the mixing rule.
   *
   * @param rule the mixing rule name
   */
  public void setMixingRule(String rule) {
    this.mixingRule = rule;
  }

  /**
   * Sets the calculation type.
   *
   * @param type the calculation type description
   */
  public void setCalculationType(String type) {
    this.calculationType = type;
  }

  /**
   * Gets the engine name.
   *
   * @return the engine name
   */
  public String getEngine() {
    return engine;
  }

  /**
   * Gets the thermodynamic model.
   *
   * @return the model name
   */
  public String getThermodynamicModel() {
    return thermodynamicModel;
  }

  /**
   * Gets the calculation type.
   *
   * @return the calculation type description
   */
  public String getCalculationType() {
    return calculationType;
  }

  /**
   * Returns whether the calculation converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Gets the assumptions list.
   *
   * @return the assumptions
   */
  public List<String> getAssumptions() {
    return assumptions;
  }

  /**
   * Gets the limitations list.
   *
   * @return the limitations
   */
  public List<String> getLimitations() {
    return limitations;
  }

  /**
   * Gets the result origin.
   *
   * @return the result origin
   */
  public String getResultOrigin() {
    return resultOrigin;
  }

  /**
   * Gets the passed validations.
   *
   * @return the validations passed
   */
  public List<String> getValidationsPassed() {
    return validationsPassed;
  }

  /**
   * Gets the timestamp.
   *
   * @return the ISO-8601 timestamp
   */
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the computation time.
   *
   * @return the computation time in milliseconds
   */
  public long getComputationTimeMs() {
    return computationTimeMs;
  }

  /**
   * Gets the mixing rule.
   *
   * @return the mixing rule name
   */
  public String getMixingRule() {
    return mixingRule;
  }
}
