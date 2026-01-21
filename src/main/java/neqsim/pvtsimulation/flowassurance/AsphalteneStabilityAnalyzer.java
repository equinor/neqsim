package neqsim.pvtsimulation.flowassurance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.characterization.AsphalteneCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetPressureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetTemperatureFlash;

/**
 * High-level API for asphaltene stability analysis.
 *
 * <p>
 * This class provides comprehensive asphaltene stability assessment including:
 * </p>
 * <ul>
 * <li>De Boer screening based on undersaturation pressure and density</li>
 * <li>SARA-based Colloidal Instability Index (CII) analysis</li>
 * <li>Thermodynamic calculation of onset pressure and temperature</li>
 * <li>Asphaltene precipitation envelope generation</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * SystemInterface fluid = new SystemSrkCPA(373.15, 300.0);
 * // Add components...
 *
 * AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(fluid);
 *
 * // Quick screening
 * String risk = analyzer.deBoerScreening(300.0, 150.0, 800.0);
 *
 * // SARA-based analysis
 * analyzer.setSARAFractions(0.45, 0.25, 0.20, 0.10);
 * double cii = analyzer.getColloidalInstabilityIndex();
 *
 * // Thermodynamic onset calculation
 * double onsetP = analyzer.calculateOnsetPressure(373.15);
 * </pre>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneStabilityAnalyzer {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(AsphalteneStabilityAnalyzer.class);

  /** The thermodynamic system to analyze. */
  private SystemInterface system;

  /** SARA characterization data. */
  private AsphalteneCharacterization saraData;

  /** Cached bubble point pressure. */
  private double bubblePointPressure = Double.NaN;

  /** Cached onset pressure. */
  private double onsetPressure = Double.NaN;

  /** Risk level enumeration. */
  public enum AsphalteneRisk {
    /** No significant risk. */
    STABLE("Stable - No asphaltene issues expected"),
    /** Low risk, monitoring recommended. */
    LOW_RISK("Low Risk - Minor precipitation possible"),
    /** Moderate risk, mitigation may be needed. */
    MODERATE_RISK("Moderate Risk - Consider asphaltene management"),
    /** High risk, mitigation required. */
    HIGH_RISK("High Risk - Significant precipitation expected"),
    /** Severe risk, major problems expected. */
    SEVERE_RISK("Severe Risk - Extensive mitigation required");

    private final String description;

    AsphalteneRisk(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system to analyze
   */
  public AsphalteneStabilityAnalyzer(SystemInterface system) {
    this.system = system.clone();
    this.saraData = new AsphalteneCharacterization();
  }

  /**
   * Default constructor.
   */
  public AsphalteneStabilityAnalyzer() {
    this.saraData = new AsphalteneCharacterization();
  }

  /**
   * Sets the thermodynamic system for analysis.
   *
   * @param system the thermodynamic system
   */
  public void setSystem(SystemInterface system) {
    this.system = system.clone();
    // Reset cached values
    bubblePointPressure = Double.NaN;
    onsetPressure = Double.NaN;
  }

  /**
   * Performs de Boer screening for asphaltene stability.
   *
   * <p>
   * The de Boer plot correlates asphaltene problems with:
   * </p>
   * <ul>
   * <li>Undersaturation pressure: ΔP = P_res - P_bubble</li>
   * <li>In-situ oil density</li>
   * </ul>
   *
   * <p>
   * Reference: de Boer, R.B. et al. (1995). SPE Production &amp; Facilities.
   * </p>
   *
   * @param reservoirPressure reservoir pressure (bara)
   * @param saturationPressure bubble point pressure (bara)
   * @param oilDensity in-situ oil density (kg/m3)
   * @return asphaltene risk assessment
   */
  public AsphalteneRisk deBoerScreening(double reservoirPressure, double saturationPressure,
      double oilDensity) {
    double undersaturation = reservoirPressure - saturationPressure;

    // De Boer correlation boundaries (approximate)
    // Based on field experience and the original de Boer plot

    // Convert density to specific gravity for correlation
    double sg = oilDensity / 1000.0;

    // Light oils (SG < 0.8) are more prone to asphaltene problems
    double densityFactor = 1.0;
    if (sg < 0.75) {
      densityFactor = 2.0; // Light oils - higher risk
    } else if (sg < 0.85) {
      densityFactor = 1.5;
    } else if (sg > 0.95) {
      densityFactor = 0.5; // Heavy oils often self-stabilizing
    }

    // Adjusted undersaturation threshold based on density
    double riskMetric = undersaturation * densityFactor;

    if (undersaturation < 0) {
      // Below bubble point - dissolution risk, not deposition
      return AsphalteneRisk.LOW_RISK;
    } else if (riskMetric < 50) {
      return AsphalteneRisk.STABLE;
    } else if (riskMetric < 150) {
      return AsphalteneRisk.LOW_RISK;
    } else if (riskMetric < 300) {
      return AsphalteneRisk.MODERATE_RISK;
    } else if (riskMetric < 500) {
      return AsphalteneRisk.HIGH_RISK;
    } else {
      return AsphalteneRisk.SEVERE_RISK;
    }
  }

  /**
   * Sets SARA fractions for colloidal analysis.
   *
   * @param saturates weight fraction of saturates (0-1)
   * @param aromatics weight fraction of aromatics (0-1)
   * @param resins weight fraction of resins (0-1)
   * @param asphaltenes weight fraction of asphaltenes (0-1)
   */
  public void setSARAFractions(double saturates, double aromatics, double resins,
      double asphaltenes) {
    saraData.setSARAFractions(saturates, aromatics, resins, asphaltenes);
  }

  /**
   * Gets the Colloidal Instability Index from SARA data.
   *
   * @return CII value, or NaN if SARA data not set
   */
  public double getColloidalInstabilityIndex() {
    return saraData.getColloidalInstabilityIndex();
  }

  /**
   * Gets the resin-to-asphaltene ratio from SARA data.
   *
   * @return R/A ratio
   */
  public double getResinToAsphalteneRatio() {
    return saraData.getResinToAsphalteneRatio();
  }

  /**
   * Evaluates stability based on SARA data.
   *
   * @return risk assessment based on CII
   */
  public AsphalteneRisk evaluateSARAStability() {
    double cii = saraData.getColloidalInstabilityIndex();

    if (Double.isNaN(cii) || Double.isInfinite(cii)) {
      logger.warn("Invalid CII - check SARA data");
      return AsphalteneRisk.MODERATE_RISK; // Conservative default
    }

    if (cii < 0.7) {
      return AsphalteneRisk.STABLE;
    } else if (cii < 0.9) {
      return AsphalteneRisk.MODERATE_RISK;
    } else if (cii < 1.1) {
      return AsphalteneRisk.HIGH_RISK;
    } else {
      return AsphalteneRisk.SEVERE_RISK;
    }
  }

  /**
   * Calculates the asphaltene onset pressure at a given temperature.
   *
   * <p>
   * This uses the thermodynamic solid phase model to find the pressure at which asphaltenes
   * precipitate.
   * </p>
   *
   * @param temperature temperature in Kelvin
   * @return onset pressure in bara, or NaN if not found
   */
  public double calculateOnsetPressure(double temperature) {
    if (system == null) {
      logger.error("System not set - cannot calculate onset pressure");
      return Double.NaN;
    }

    SystemInterface workSystem = system.clone();
    workSystem.setTemperature(temperature);

    AsphalteneOnsetPressureFlash onsetFlash = new AsphalteneOnsetPressureFlash(workSystem);
    onsetFlash.run();

    if (onsetFlash.isOnsetFound()) {
      onsetPressure = onsetFlash.getOnsetPressure();
      return onsetPressure;
    }

    return Double.NaN;
  }

  /**
   * Calculates the asphaltene onset temperature at a given pressure.
   *
   * @param pressure pressure in bara
   * @return onset temperature in Kelvin, or NaN if not found
   */
  public double calculateOnsetTemperature(double pressure) {
    if (system == null) {
      logger.error("System not set - cannot calculate onset temperature");
      return Double.NaN;
    }

    SystemInterface workSystem = system.clone();
    workSystem.setPressure(pressure);

    AsphalteneOnsetTemperatureFlash onsetFlash = new AsphalteneOnsetTemperatureFlash(workSystem);
    onsetFlash.run();

    if (onsetFlash.isOnsetFound()) {
      return onsetFlash.getOnsetTemperature();
    }

    return Double.NaN;
  }

  /**
   * Calculates the bubble point pressure for the system.
   *
   * @return bubble point pressure in bara
   */
  public double calculateBubblePointPressure() {
    if (system == null) {
      logger.error("System not set");
      return Double.NaN;
    }

    try {
      SystemInterface workSystem = system.clone();
      ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
      ops.bubblePointPressureFlash(false);
      bubblePointPressure = workSystem.getPressure();
      return bubblePointPressure;
    } catch (Exception e) {
      logger.error("Failed to calculate bubble point: {}", e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Generates an asphaltene precipitation envelope.
   *
   * <p>
   * Returns arrays of [temperature, onset pressure upper, onset pressure lower] defining the
   * asphaltene precipitation region.
   * </p>
   *
   * @param minTemp minimum temperature (K)
   * @param maxTemp maximum temperature (K)
   * @param numPoints number of points to calculate
   * @return 2D array: [0]=temperatures, [1]=upper onset P, [2]=lower onset P
   */
  public double[][] generatePrecipitationEnvelope(double minTemp, double maxTemp, int numPoints) {
    double[][] envelope = new double[3][numPoints];
    double tempStep = (maxTemp - minTemp) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double temp = minTemp + i * tempStep;
      envelope[0][i] = temp;

      double onsetP = calculateOnsetPressure(temp);
      envelope[1][i] = onsetP; // Upper onset (above bubble point)

      // Lower onset would require different algorithm
      // For now, use bubble point as lower bound
      if (Double.isNaN(bubblePointPressure)) {
        calculateBubblePointPressure();
      }
      envelope[2][i] = bubblePointPressure;
    }

    return envelope;
  }

  /**
   * Performs a comprehensive stability assessment.
   *
   * @param reservoirPressure reservoir pressure (bara)
   * @param reservoirTemperature reservoir temperature (K)
   * @param wellheadPressure wellhead pressure (bara)
   * @param wellheadTemperature wellhead temperature (K)
   * @return comprehensive assessment report
   */
  public String comprehensiveAssessment(double reservoirPressure, double reservoirTemperature,
      double wellheadPressure, double wellheadTemperature) {
    StringBuilder report = new StringBuilder();
    report.append("=== ASPHALTENE STABILITY ASSESSMENT ===\n\n");

    // 1. SARA-based screening
    report.append("1. SARA-Based Analysis:\n");
    double cii = getColloidalInstabilityIndex();
    if (!Double.isNaN(cii)) {
      report.append(String.format("   CII = %.3f%n", cii));
      report.append(String.format("   R/A = %.3f%n", getResinToAsphalteneRatio()));
      report.append("   Risk: ").append(evaluateSARAStability().getDescription()).append("\n");
    } else {
      report.append("   SARA data not available\n");
    }
    report.append("\n");

    // 2. Operating conditions
    report.append("2. Operating Conditions:\n");
    report.append(String.format("   Reservoir: %.1f bara, %.1f K (%.1f C)%n", reservoirPressure,
        reservoirTemperature, reservoirTemperature - 273.15));
    report.append(String.format("   Wellhead: %.1f bara, %.1f K (%.1f C)%n", wellheadPressure,
        wellheadTemperature, wellheadTemperature - 273.15));
    report.append(
        String.format("   Pressure drop: %.1f bar%n", reservoirPressure - wellheadPressure));
    report.append("\n");

    // 3. Thermodynamic calculations (if system available)
    if (system != null) {
      report.append("3. Thermodynamic Analysis:\n");

      double bubbleP = calculateBubblePointPressure();
      if (!Double.isNaN(bubbleP)) {
        report.append(String.format("   Bubble point: %.1f bara%n", bubbleP));
        report.append(String.format("   Undersaturation: %.1f bar%n", reservoirPressure - bubbleP));
      }

      double onsetP = calculateOnsetPressure(reservoirTemperature);
      if (!Double.isNaN(onsetP)) {
        report.append(String.format("   Onset pressure: %.1f bara%n", onsetP));

        // Check if production path crosses onset
        if (wellheadPressure < onsetP && reservoirPressure > onsetP) {
          report.append("   WARNING: Production path crosses asphaltene onset!\n");
        }
      } else {
        report.append("   Onset pressure: Not found in operating range\n");
      }
    }
    report.append("\n");

    // 4. Recommendations
    report.append("4. Recommendations:\n");
    AsphalteneRisk overallRisk = evaluateSARAStability();
    switch (overallRisk) {
      case STABLE:
        report.append("   - No special measures required\n");
        report.append("   - Routine monitoring recommended\n");
        break;
      case LOW_RISK:
        report.append("   - Monitor downhole and surface for deposits\n");
        report.append("   - Consider periodic sampling\n");
        break;
      case MODERATE_RISK:
        report.append("   - Install asphaltene monitoring system\n");
        report.append("   - Consider chemical inhibitor injection\n");
        report.append("   - Plan for periodic intervention\n");
        break;
      case HIGH_RISK:
        report.append("   - Continuous inhibitor injection recommended\n");
        report.append("   - Frequent well interventions may be needed\n");
        report.append("   - Consider production rate optimization\n");
        break;
      case SEVERE_RISK:
        report.append("   - Comprehensive asphaltene management program required\n");
        report.append("   - May need pressure maintenance strategy\n");
        report.append("   - Consider downhole/surface heating\n");
        report.append("   - Extensive chemical treatment program\n");
        break;
    }

    return report.toString();
  }

  /**
   * Gets the SARA characterization object.
   *
   * @return the SARA characterization
   */
  public AsphalteneCharacterization getSARAData() {
    return saraData;
  }
}
