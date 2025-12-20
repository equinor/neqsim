package neqsim.pvtsimulation.flowassurance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * De Boer plot-based screening for asphaltene precipitation risk.
 *
 * <p>
 * The De Boer plot is an empirical screening tool developed from field experience that correlates
 * asphaltene problems with two key parameters:
 * </p>
 * <ul>
 * <li>Undersaturation pressure: ΔP = P_reservoir - P_saturation (bubble point)</li>
 * <li>In-situ oil density (or API gravity)</li>
 * </ul>
 *
 * <p>
 * The plot divides the P-density space into regions of varying asphaltene precipitation risk. Light
 * oils (high API, low density) with high undersaturation are most prone to asphaltene problems.
 * </p>
 *
 * <p>
 * Reference: de Boer, R.B., Leerlooyer, K., Eigner, M.R.P., and van Bergen, A.R.D. (1995).
 * "Screening of Crude Oils for Asphalt Precipitation: Theory, Practice, and the Selection of
 * Inhibitors." SPE Production &amp; Facilities, February 1995.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening();
 * screening.setReservoirPressure(450.0); // bara
 * screening.setSaturationPressure(150.0); // bara
 * screening.setInSituDensity(750.0); // kg/m3
 * 
 * String risk = screening.evaluateRisk();
 * double riskIndex = screening.calculateRiskIndex();
 * </pre>
 *
 * @author ASMF
 * @version 1.0
 */
public class DeBoerAsphalteneScreening {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(DeBoerAsphalteneScreening.class);

  /** Reservoir pressure in bara. */
  private double reservoirPressure;

  /** Saturation (bubble point) pressure in bara. */
  private double saturationPressure;

  /** Reservoir temperature in Kelvin. */
  private double reservoirTemperature;

  /** In-situ oil density in kg/m3. */
  private double inSituDensity;

  /** Asphaltene content (weight fraction). */
  private double asphalteneContent = 0.0;

  /** Reference to thermodynamic system for property calculations. */
  private SystemInterface system;

  // De Boer correlation constants (derived from original plot)
  // These define the boundary lines between risk regions

  /** Slope of severe problem boundary line (bar per kg/m3). */
  private static final double SEVERE_SLOPE = 1.8;

  /** Intercept of severe problem boundary (bar). */
  private static final double SEVERE_INTERCEPT = -1100;

  /** Slope of slight problem boundary line. */
  private static final double SLIGHT_SLOPE = 1.2;

  /** Intercept of slight problem boundary. */
  private static final double SLIGHT_INTERCEPT = -750;

  /** Slope of no problem boundary line. */
  private static final double NO_PROBLEM_SLOPE = 0.8;

  /** Intercept of no problem boundary. */
  private static final double NO_PROBLEM_INTERCEPT = -500;

  /**
   * Risk level enumeration based on De Boer plot regions.
   */
  public enum DeBoerRisk {
    /** No asphaltene problems expected. */
    NO_PROBLEM("No Problem - Asphaltene precipitation unlikely"),

    /** Slight/possible problems. */
    SLIGHT_PROBLEM("Slight Problem - Minor asphaltene issues possible"),

    /** Moderate problems expected. */
    MODERATE_PROBLEM("Moderate Problem - Asphaltene management recommended"),

    /** Severe problems expected. */
    SEVERE_PROBLEM("Severe Problem - Significant asphaltene precipitation expected");

    private final String description;

    DeBoerRisk(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * Default constructor.
   */
  public DeBoerAsphalteneScreening() {}

  /**
   * Constructor with key parameters.
   *
   * @param reservoirPressure reservoir pressure (bara)
   * @param saturationPressure bubble point pressure (bara)
   * @param inSituDensity in-situ oil density (kg/m3)
   */
  public DeBoerAsphalteneScreening(double reservoirPressure, double saturationPressure,
      double inSituDensity) {
    this.reservoirPressure = reservoirPressure;
    this.saturationPressure = saturationPressure;
    this.inSituDensity = inSituDensity;
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system thermodynamic system for property calculations
   * @param reservoirPressure reservoir pressure (bara)
   * @param reservoirTemperature reservoir temperature (K)
   */
  public DeBoerAsphalteneScreening(SystemInterface system, double reservoirPressure,
      double reservoirTemperature) {
    this.system = system.clone();
    this.reservoirPressure = reservoirPressure;
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Calculates the undersaturation pressure.
   *
   * @return undersaturation pressure ΔP = P_res - P_sat (bar)
   */
  public double getUndersaturationPressure() {
    return reservoirPressure - saturationPressure;
  }

  /**
   * Calculates the API gravity from in-situ density.
   *
   * @return API gravity
   */
  public double getAPIGravity() {
    // API = 141.5 / SG - 131.5
    // SG = density / 1000 (assuming water = 1000 kg/m3)
    double sg = inSituDensity / 1000.0;
    if (sg <= 0) {
      return Double.NaN;
    }
    return 141.5 / sg - 131.5;
  }

  /**
   * Calculates the De Boer risk index.
   *
   * <p>
   * The risk index is a normalized value indicating the position relative to the De Boer boundary
   * lines. Higher values indicate greater risk.
   * </p>
   *
   * @return risk index (0 = no risk, 1 = boundary of severe problems, &gt;1 = severe)
   */
  public double calculateRiskIndex() {
    double deltaP = getUndersaturationPressure();

    // Calculate the "critical" undersaturation for this density
    // This is the undersaturation at which problems begin
    double criticalDeltaP = NO_PROBLEM_SLOPE * inSituDensity + NO_PROBLEM_INTERCEPT;

    if (criticalDeltaP <= 0) {
      criticalDeltaP = 50.0; // Minimum threshold
    }

    // Risk index is ratio of actual to critical undersaturation
    double riskIndex = deltaP / criticalDeltaP;

    // Adjust for asphaltene content if known
    if (asphalteneContent > 0) {
      // Higher asphaltene content increases risk
      // Typical adjustment: 5% asphaltenes doubles risk
      double asphalteneMultiplier = 1.0 + (asphalteneContent - 0.02) / 0.05;
      if (asphalteneMultiplier < 0.5) {
        asphalteneMultiplier = 0.5;
      }
      riskIndex *= asphalteneMultiplier;
    }

    return Math.max(0, riskIndex);
  }

  /**
   * Evaluates the asphaltene risk using the De Boer plot correlation.
   *
   * @return risk level based on De Boer boundaries
   */
  public DeBoerRisk evaluateRisk() {
    double deltaP = getUndersaturationPressure();

    // Calculate boundary values for this density
    double severeThreshold = SEVERE_SLOPE * inSituDensity + SEVERE_INTERCEPT;
    double slightThreshold = SLIGHT_SLOPE * inSituDensity + SLIGHT_INTERCEPT;
    double noProblemsThreshold = NO_PROBLEM_SLOPE * inSituDensity + NO_PROBLEM_INTERCEPT;

    // Classify based on position relative to boundaries
    if (deltaP >= severeThreshold && severeThreshold > 0) {
      return DeBoerRisk.SEVERE_PROBLEM;
    } else if (deltaP >= slightThreshold && slightThreshold > 0) {
      return DeBoerRisk.MODERATE_PROBLEM;
    } else if (deltaP >= noProblemsThreshold && noProblemsThreshold > 0) {
      return DeBoerRisk.SLIGHT_PROBLEM;
    } else {
      return DeBoerRisk.NO_PROBLEM;
    }
  }

  /**
   * Calculates the bubble point pressure from the thermodynamic system.
   *
   * @return bubble point pressure (bara)
   */
  public double calculateSaturationPressure() {
    if (system == null) {
      logger.warn("No thermodynamic system set - cannot calculate saturation pressure");
      return Double.NaN;
    }

    try {
      SystemInterface workSystem = system.clone();
      workSystem.setTemperature(reservoirTemperature);
      ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
      ops.bubblePointPressureFlash(false);
      saturationPressure = workSystem.getPressure();
      return saturationPressure;
    } catch (Exception e) {
      logger.error("Failed to calculate bubble point: {}", e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Calculates the in-situ density from the thermodynamic system.
   *
   * @return in-situ oil density (kg/m3)
   */
  public double calculateInSituDensity() {
    if (system == null) {
      logger.warn("No thermodynamic system set - cannot calculate density");
      return Double.NaN;
    }

    try {
      SystemInterface workSystem = system.clone();
      workSystem.setTemperature(reservoirTemperature);
      workSystem.setPressure(reservoirPressure);
      ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
      ops.TPflash();
      workSystem.initPhysicalProperties();

      // Get oil phase density (usually phase 0 or 1 depending on conditions)
      if (workSystem.hasPhaseType("oil")) {
        inSituDensity = workSystem.getPhase("oil").getDensity("kg/m3");
      } else if (workSystem.getNumberOfPhases() > 0) {
        inSituDensity = workSystem.getPhase(0).getDensity("kg/m3");
      }
      return inSituDensity;
    } catch (Exception e) {
      logger.error("Failed to calculate density: {}", e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Performs complete screening using thermodynamic system for properties.
   *
   * @return comprehensive screening result
   */
  public String performScreening() {
    StringBuilder result = new StringBuilder();
    result.append("=== DE BOER ASPHALTENE SCREENING ===\n\n");

    // Calculate properties if system available
    if (system != null) {
      if (Double.isNaN(saturationPressure) || saturationPressure <= 0) {
        result.append("Calculating saturation pressure...\n");
        calculateSaturationPressure();
      }
      if (Double.isNaN(inSituDensity) || inSituDensity <= 0) {
        result.append("Calculating in-situ density...\n");
        calculateInSituDensity();
      }
    }

    // Input parameters
    result.append("INPUT PARAMETERS:\n");
    result.append(String.format("  Reservoir Pressure: %.1f bara%n", reservoirPressure));
    result.append(String.format("  Saturation Pressure: %.1f bara%n", saturationPressure));
    result.append(String.format("  Undersaturation: %.1f bar%n", getUndersaturationPressure()));
    result.append(String.format("  In-situ Density: %.1f kg/m3%n", inSituDensity));
    result.append(String.format("  API Gravity: %.1f%n", getAPIGravity()));
    if (asphalteneContent > 0) {
      result.append(String.format("  Asphaltene Content: %.1f wt%%%n", asphalteneContent * 100));
    }
    result.append("\n");

    // Risk assessment
    DeBoerRisk risk = evaluateRisk();
    double riskIndex = calculateRiskIndex();

    result.append("RISK ASSESSMENT:\n");
    result.append(String.format("  Risk Level: %s%n", risk.name()));
    result.append(String.format("  Risk Index: %.2f%n", riskIndex));
    result.append(String.format("  Description: %s%n", risk.getDescription()));
    result.append("\n");

    // Boundary analysis
    result.append("DE BOER PLOT POSITION:\n");
    double severeThreshold = SEVERE_SLOPE * inSituDensity + SEVERE_INTERCEPT;
    double slightThreshold = SLIGHT_SLOPE * inSituDensity + SLIGHT_INTERCEPT;
    result.append(
        String.format("  Severe problem boundary: %.1f bar%n", Math.max(0, severeThreshold)));
    result.append(
        String.format("  Slight problem boundary: %.1f bar%n", Math.max(0, slightThreshold)));
    result.append(
        String.format("  Current undersaturation: %.1f bar%n", getUndersaturationPressure()));

    return result.toString();
  }

  /**
   * Generates De Boer plot data for visualization.
   *
   * <p>
   * Returns arrays suitable for plotting the De Boer boundaries and the current operating point.
   * </p>
   *
   * @param minDensity minimum density for plot (kg/m3)
   * @param maxDensity maximum density for plot (kg/m3)
   * @param numPoints number of points per curve
   * @return 2D array: [0]=densities, [1]=no problem line, [2]=slight line, [3]=severe line
   */
  public double[][] generatePlotData(double minDensity, double maxDensity, int numPoints) {
    double[][] data = new double[4][numPoints];
    double step = (maxDensity - minDensity) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double density = minDensity + i * step;
      data[0][i] = density;
      data[1][i] = Math.max(0, NO_PROBLEM_SLOPE * density + NO_PROBLEM_INTERCEPT);
      data[2][i] = Math.max(0, SLIGHT_SLOPE * density + SLIGHT_INTERCEPT);
      data[3][i] = Math.max(0, SEVERE_SLOPE * density + SEVERE_INTERCEPT);
    }

    return data;
  }

  // Getters and setters

  public double getReservoirPressure() {
    return reservoirPressure;
  }

  public void setReservoirPressure(double reservoirPressure) {
    this.reservoirPressure = reservoirPressure;
  }

  public double getSaturationPressure() {
    return saturationPressure;
  }

  public void setSaturationPressure(double saturationPressure) {
    this.saturationPressure = saturationPressure;
  }

  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }

  public double getInSituDensity() {
    return inSituDensity;
  }

  public void setInSituDensity(double inSituDensity) {
    this.inSituDensity = inSituDensity;
  }

  public double getAsphalteneContent() {
    return asphalteneContent;
  }

  public void setAsphalteneContent(double asphalteneContent) {
    this.asphalteneContent = asphalteneContent;
  }

  public void setSystem(SystemInterface system) {
    this.system = system.clone();
  }
}
