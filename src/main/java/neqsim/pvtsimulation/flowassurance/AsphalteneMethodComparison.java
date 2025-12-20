package neqsim.pvtsimulation.flowassurance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.characterization.AsphalteneCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compares De Boer screening with CPA-based thermodynamic asphaltene analysis.
 *
 * <p>
 * This class provides side-by-side comparison of two asphaltene risk assessment approaches:
 * </p>
 * <ol>
 * <li><b>De Boer Screening</b>: Empirical correlation based on undersaturation and density</li>
 * <li><b>CPA Thermodynamic</b>: Equation of state with association for onset prediction</li>
 * </ol>
 *
 * <p>
 * Comparison considerations:
 * </p>
 * <ul>
 * <li>De Boer is fast but empirical - good for initial screening</li>
 * <li>CPA is physics-based but requires proper characterization</li>
 * <li>SARA analysis provides independent stability indicators (CII, R/A)</li>
 * <li>Agreement between methods increases confidence</li>
 * <li>Disagreement suggests need for lab testing</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneMethodComparison {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(AsphalteneMethodComparison.class);

  /** The thermodynamic system for analysis. */
  private SystemInterface system;

  /** De Boer screening instance. */
  private DeBoerAsphalteneScreening deBoerScreening;

  /** CPA-based stability analyzer. */
  private AsphalteneStabilityAnalyzer cpaAnalyzer;

  /** SARA characterization data. */
  private AsphalteneCharacterization saraData;

  /** Reservoir pressure (bara). */
  private double reservoirPressure;

  /** Reservoir temperature (K). */
  private double reservoirTemperature;

  /** Calculated bubble point pressure. */
  private double bubblePointPressure = Double.NaN;

  /** Calculated in-situ density. */
  private double inSituDensity = Double.NaN;

  /** CPA onset pressure result. */
  private double cpaOnsetPressure = Double.NaN;

  /**
   * Constructor with thermodynamic system and conditions.
   *
   * @param system the thermodynamic system
   * @param reservoirPressure reservoir pressure (bara)
   * @param reservoirTemperature reservoir temperature (K)
   */
  public AsphalteneMethodComparison(SystemInterface system, double reservoirPressure,
      double reservoirTemperature) {
    this.system = system.clone();
    this.reservoirPressure = reservoirPressure;
    this.reservoirTemperature = reservoirTemperature;

    this.deBoerScreening = new DeBoerAsphalteneScreening();
    this.cpaAnalyzer = new AsphalteneStabilityAnalyzer(system);
    this.saraData = new AsphalteneCharacterization();
  }

  /**
   * Sets SARA fractions for both De Boer and CPA analysis.
   *
   * @param saturates weight fraction of saturates
   * @param aromatics weight fraction of aromatics
   * @param resins weight fraction of resins
   * @param asphaltenes weight fraction of asphaltenes
   */
  public void setSARAFractions(double saturates, double aromatics, double resins,
      double asphaltenes) {
    saraData.setSARAFractions(saturates, aromatics, resins, asphaltenes);
    cpaAnalyzer.setSARAFractions(saturates, aromatics, resins, asphaltenes);
    deBoerScreening.setAsphalteneContent(asphaltenes);
  }

  /**
   * Runs both screening methods and returns comparison results.
   *
   * @return comprehensive comparison report
   */
  public String runComparison() {
    StringBuilder report = new StringBuilder();
    report.append("╔══════════════════════════════════════════════════════════════╗\n");
    report.append("║        ASPHALTENE SCREENING METHOD COMPARISON                ║\n");
    report.append("╚══════════════════════════════════════════════════════════════╝\n\n");

    // Calculate common properties
    calculateFluidProperties();

    // Section 1: Input conditions
    report.append("┌────────────────────────────────────────────────────────────────┐\n");
    report.append("│ INPUT CONDITIONS                                               │\n");
    report.append("├────────────────────────────────────────────────────────────────┤\n");
    report.append(String.format("│ Reservoir Pressure:     %8.1f bara                         │%n",
        reservoirPressure));
    report.append(String.format("│ Reservoir Temperature:  %8.1f K (%.1f °C)                  │%n",
        reservoirTemperature, reservoirTemperature - 273.15));
    report.append(String.format("│ Bubble Point Pressure:  %8.1f bara                         │%n",
        bubblePointPressure));
    report.append(String.format("│ Undersaturation:        %8.1f bar                          │%n",
        reservoirPressure - bubblePointPressure));
    report.append(String.format("│ In-situ Density:        %8.1f kg/m³                        │%n",
        inSituDensity));
    report.append(String.format("│ API Gravity:            %8.1f                              │%n",
        deBoerScreening.getAPIGravity()));
    report.append("└────────────────────────────────────────────────────────────────┘\n\n");

    // Section 2: SARA Analysis (independent)
    report.append("┌────────────────────────────────────────────────────────────────┐\n");
    report.append("│ SARA ANALYSIS (Independent Indicator)                          │\n");
    report.append("├────────────────────────────────────────────────────────────────┤\n");
    double cii = saraData.getColloidalInstabilityIndex();
    double ra = saraData.getResinToAsphalteneRatio();
    if (!Double.isNaN(cii) && !Double.isInfinite(cii)) {
      report.append(
          String.format("│ Colloidal Instability Index (CII): %.3f                      │%n", cii));
      report.append(
          String.format("│ Resin/Asphaltene Ratio (R/A):      %.3f                      │%n", ra));
      report.append("│                                                                │\n");
      if (cii < 0.7) {
        report.append("│ SARA Verdict: STABLE (CII < 0.7)                               │\n");
      } else if (cii < 0.9) {
        report.append("│ SARA Verdict: MARGINAL (0.7 ≤ CII < 0.9)                        │\n");
      } else {
        report.append("│ SARA Verdict: UNSTABLE (CII ≥ 0.9)                              │\n");
      }
    } else {
      report.append("│ SARA data not available                                        │\n");
    }
    report.append("└────────────────────────────────────────────────────────────────┘\n\n");

    // Section 3: De Boer Screening
    report.append("┌────────────────────────────────────────────────────────────────┐\n");
    report.append("│ METHOD 1: DE BOER SCREENING (Empirical)                        │\n");
    report.append("├────────────────────────────────────────────────────────────────┤\n");
    DeBoerAsphalteneScreening.DeBoerRisk deBoerRisk = deBoerScreening.evaluateRisk();
    double deBoerIndex = deBoerScreening.calculateRiskIndex();
    report.append(String.format("│ Risk Level:  %-48s │%n", deBoerRisk.name()));
    report.append(String.format(
        "│ Risk Index:  %.2f                                              │%n", deBoerIndex));
    report.append(
        String.format("│ Description: %-48s │%n", truncate(deBoerRisk.getDescription(), 48)));
    report.append("│                                                                │\n");
    report.append("│ Basis: Field experience correlation (de Boer et al., 1995)    │\n");
    report.append("│ Inputs: Undersaturation pressure + in-situ density            │\n");
    report.append("│ Speed: FAST (no iteration required)                           │\n");
    report.append("└────────────────────────────────────────────────────────────────┘\n\n");

    // Section 4: CPA Thermodynamic
    report.append("┌────────────────────────────────────────────────────────────────┐\n");
    report.append("│ METHOD 2: CPA THERMODYNAMIC (Physics-Based)                    │\n");
    report.append("├────────────────────────────────────────────────────────────────┤\n");
    cpaOnsetPressure = cpaAnalyzer.calculateOnsetPressure(reservoirTemperature);
    AsphalteneStabilityAnalyzer.AsphalteneRisk cpaRisk = cpaAnalyzer.evaluateSARAStability();

    if (!Double.isNaN(cpaOnsetPressure)) {
      report.append(String.format(
          "│ Onset Pressure: %.1f bara                                     │%n", cpaOnsetPressure));
      double margin = reservoirPressure - cpaOnsetPressure;
      report.append(String
          .format("│ Safety Margin:  %.1f bar (reservoir - onset)                 │%n", margin));
      if (margin > 50) {
        report.append("│ Status: SAFE - Operating well above onset                     │\n");
      } else if (margin > 0) {
        report.append("│ Status: CAUTION - Close to onset pressure                     │\n");
      } else {
        report.append("│ Status: CRITICAL - Below onset pressure!                      │\n");
      }
    } else {
      report.append("│ Onset Pressure: Not found (no solid phase precipitation)      │\n");
      report.append("│ Status: No thermodynamic onset detected                       │\n");
    }
    report.append(String.format("│ SARA Risk:      %-46s │%n", cpaRisk.name()));
    report.append("│                                                                │\n");
    report.append("│ Basis: CPA EOS with solid phase stability                     │\n");
    report.append("│ Inputs: Full fluid composition + SARA characterization        │\n");
    report.append("│ Speed: MODERATE (iterative flash calculations)                │\n");
    report.append("└────────────────────────────────────────────────────────────────┘\n\n");

    // Section 5: Comparison & Recommendations
    report.append("┌────────────────────────────────────────────────────────────────┐\n");
    report.append("│ COMPARISON & RECOMMENDATIONS                                   │\n");
    report.append("├────────────────────────────────────────────────────────────────┤\n");

    // Determine agreement level
    boolean deBoerProblem = (deBoerRisk != DeBoerAsphalteneScreening.DeBoerRisk.NO_PROBLEM);
    boolean cpaProblem = (cpaRisk != AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE);
    boolean saraProblem = (!Double.isNaN(cii) && cii >= 0.7);

    int problemCount = (deBoerProblem ? 1 : 0) + (cpaProblem ? 1 : 0) + (saraProblem ? 1 : 0);

    if (problemCount == 0) {
      report.append("│ ✓ ALL METHODS AGREE: LOW RISK                                 │\n");
      report.append("│                                                                │\n");
      report.append("│ Confidence: HIGH                                              │\n");
      report.append("│ Recommendation: Standard operations, routine monitoring       │\n");
    } else if (problemCount == 3) {
      report.append("│ ⚠ ALL METHODS AGREE: HIGH RISK                                │\n");
      report.append("│                                                                │\n");
      report.append("│ Confidence: HIGH                                              │\n");
      report.append("│ Recommendation: Implement asphaltene management program       │\n");
      report.append("│   - Chemical inhibitor injection                              │\n");
      report.append("│   - Pressure management strategy                              │\n");
      report.append("│   - Frequent monitoring and intervention                      │\n");
    } else {
      report.append("│ ⚡ METHODS DISAGREE - FURTHER ANALYSIS NEEDED                 │\n");
      report.append("│                                                                │\n");
      report.append(String.format("│   De Boer: %-8s  CPA: %-8s  SARA: %-8s          │%n",
          deBoerProblem ? "PROBLEM" : "OK", cpaProblem ? "PROBLEM" : "OK",
          saraProblem ? "PROBLEM" : "OK"));
      report.append("│                                                                │\n");
      report.append("│ Confidence: MODERATE                                          │\n");
      report.append("│ Recommendation:                                               │\n");
      report.append("│   - Perform lab depressurization test                         │\n");
      report.append("│   - Verify SARA analysis                                      │\n");
      report.append("│   - Consider conservative approach until clarified            │\n");
    }
    report.append("└────────────────────────────────────────────────────────────────┘\n");

    return report.toString();
  }

  /**
   * Calculates bubble point and density from the thermodynamic system.
   */
  private void calculateFluidProperties() {
    if (system == null) {
      return;
    }

    try {
      // Calculate bubble point
      SystemInterface workSystem = system.clone();
      workSystem.setTemperature(reservoirTemperature);
      ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
      ops.bubblePointPressureFlash(false);
      bubblePointPressure = workSystem.getPressure();

      // Calculate in-situ density at reservoir conditions
      workSystem = system.clone();
      workSystem.setTemperature(reservoirTemperature);
      workSystem.setPressure(reservoirPressure);
      ops = new ThermodynamicOperations(workSystem);
      ops.TPflash();
      workSystem.initPhysicalProperties();

      if (workSystem.hasPhaseType("oil")) {
        inSituDensity = workSystem.getPhase("oil").getDensity("kg/m3");
      } else if (workSystem.getNumberOfPhases() > 0) {
        inSituDensity = workSystem.getPhase(0).getDensity("kg/m3");
      }

      // Update De Boer screening with calculated values
      deBoerScreening.setReservoirPressure(reservoirPressure);
      deBoerScreening.setSaturationPressure(bubblePointPressure);
      deBoerScreening.setInSituDensity(inSituDensity);

    } catch (Exception e) {
      logger.error("Failed to calculate fluid properties: {}", e.getMessage());
    }
  }

  /**
   * Returns a quick comparison summary as a single line.
   *
   * @return one-line summary of both methods
   */
  public String getQuickSummary() {
    DeBoerAsphalteneScreening.DeBoerRisk deBoerRisk = deBoerScreening.evaluateRisk();
    AsphalteneStabilityAnalyzer.AsphalteneRisk cpaRisk = cpaAnalyzer.evaluateSARAStability();

    return String.format("De Boer: %s | CPA/SARA: %s | Onset P: %.1f bara", deBoerRisk.name(),
        cpaRisk.name(), Double.isNaN(cpaOnsetPressure) ? -1 : cpaOnsetPressure);
  }

  /**
   * Truncates a string to specified length.
   */
  private String truncate(String s, int maxLength) {
    if (s == null) {
      return "";
    }
    return s.length() <= maxLength ? s : s.substring(0, maxLength - 3) + "...";
  }

  // Getters

  public double getBubblePointPressure() {
    return bubblePointPressure;
  }

  public double getInSituDensity() {
    return inSituDensity;
  }

  public double getCpaOnsetPressure() {
    return cpaOnsetPressure;
  }

  public DeBoerAsphalteneScreening getDeBoerScreening() {
    return deBoerScreening;
  }

  public AsphalteneStabilityAnalyzer getCpaAnalyzer() {
    return cpaAnalyzer;
  }
}
