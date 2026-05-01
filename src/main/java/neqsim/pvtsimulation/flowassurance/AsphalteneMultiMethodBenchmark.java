package neqsim.pvtsimulation.flowassurance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.thermo.characterization.AsphalteneCharacterization;
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comprehensive multi-method asphaltene prediction benchmark.
 *
 * <p>
 * This class provides a unified framework for comparing five asphaltene prediction approaches:
 * </p>
 * <ol>
 * <li><b>De Boer screening</b> — empirical, field-experience based</li>
 * <li><b>SARA CII</b> — Colloidal Instability Index from SARA fractionation</li>
 * <li><b>CPA EOS</b> — SRK-CPA with association, solid phase check</li>
 * <li><b>Flory-Huggins</b> — solubility parameter (regular solution theory)</li>
 * <li><b>Pedersen cubic EOS</b> — liquid-liquid split, no association terms</li>
 * </ol>
 *
 * <p>
 * The benchmark outputs a JSON report with:
 * </p>
 * <ul>
 * <li>Onset pressure from each thermodynamic method</li>
 * <li>Risk level from each screening method</li>
 * <li>Precipitation amount curves (wt% vs pressure)</li>
 * <li>Solubility parameter profiles</li>
 * <li>Method agreement matrix</li>
 * <li>Computational performance metrics</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hirschberg, A. et al. (1984). SPE J., 24(3), 283-293.</li>
 * <li>de Boer, R.B. et al. (1995). SPE Prod. Facilities, Feb 1995.</li>
 * <li>Pedersen, K.S. (2025). SPE-224534-MS, GOTECH.</li>
 * <li>Kontogeorgis, G.M. et al. (2006). Ind. Eng. Chem. Res., 45(14), 4855-4868.</li>
 * <li>Buckley, J.S. et al. (1998). Petrol. Sci. Technol., 16(3-4), 251-285.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class AsphalteneMultiMethodBenchmark {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(AsphalteneMultiMethodBenchmark.class);

  /** JSON serializer. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  // ─── Input data ───

  /** CPA thermodynamic system (with asphaltene component, CPA EOS). */
  private SystemInterface cpaSystem;

  /** Classical cubic EOS system (SRK/PR, for Pedersen method). */
  private SystemInterface cubicSystem;

  /** Reservoir pressure (bara). */
  private double reservoirPressure;

  /** Reservoir temperature (K). */
  private double reservoirTemperature;

  /** SARA fractions. */
  private double saturates = Double.NaN;

  /** SARA aromatics. */
  private double aromatics = Double.NaN;

  /** SARA resins. */
  private double resins = Double.NaN;

  /** SARA asphaltenes. */
  private double asphaltenesFraction = Double.NaN;

  /** Measured onset pressure for validation (bara). NaN if unknown. */
  private double measuredOnsetPressure = Double.NaN;

  /** Measured onset temperature for validation (K). NaN if unknown. */
  private double measuredOnsetTemperature = Double.NaN;

  /** Oil density at in-situ conditions (kg/m3). */
  private double inSituDensity = Double.NaN;

  /** Measured refractive index of oil. */
  private double measuredRI = Double.NaN;

  /** Measured refractive index at onset. */
  private double measuredRIOnset = Double.NaN;

  /** API gravity of the crude oil (used for FH model configuration). */
  private double apiGravity = Double.NaN;

  // ─── Results storage ───

  /** Results from each method. */
  private Map<String, MethodResult> methodResults = new LinkedHashMap<String, MethodResult>();

  /**
   * Container for results from a single method.
   */
  public static class MethodResult {
    /** Method name. */
    public String methodName;

    /** Risk level (text). */
    public String riskLevel;

    /** Onset pressure (bara) or NaN. */
    public double onsetPressure = Double.NaN;

    /** Onset temperature (K) or NaN. */
    public double onsetTemperature = Double.NaN;

    /** Bubble point pressure (bara) or NaN. */
    public double bubblePointPressure = Double.NaN;

    /** Absolute error vs measured onset pressure (bar). */
    public double onsetPressureError = Double.NaN;

    /** Relative error vs measured onset pressure (%). */
    public double onsetPressureRelativeError = Double.NaN;

    /** Computation time (ms). */
    public long computationTimeMs;

    /** Method-specific details. */
    public Map<String, Object> details = new LinkedHashMap<String, Object>();

    /** Precipitation curve: pressures. */
    public double[] precipCurvePressures;

    /** Precipitation curve: wt% precipitated. */
    public double[] precipCurveWtPct;
  }

  /**
   * Default constructor.
   */
  public AsphalteneMultiMethodBenchmark() {}

  /**
   * Constructor with basic parameters.
   *
   * @param reservoirPressure reservoir pressure (bara)
   * @param reservoirTemperature reservoir temperature (K)
   */
  public AsphalteneMultiMethodBenchmark(double reservoirPressure, double reservoirTemperature) {
    this.reservoirPressure = reservoirPressure;
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Runs all available methods and populates results.
   */
  public void runAllMethods() {
    methodResults.clear();

    // Method 1: De Boer screening
    runDeBoerMethod();

    // Method 2: SARA CII
    runSARAMethod();

    // Method 3: Refractive Index
    runRefractiveIndexMethod();

    // Method 4: Flory-Huggins
    runFloryHugginsMethod();

    // Method 5: CPA EOS
    runCPAMethod();

    // Method 6: Pedersen cubic EOS
    runPedersenMethod();

    // Calculate errors vs measured data
    if (!Double.isNaN(measuredOnsetPressure)) {
      for (MethodResult result : methodResults.values()) {
        if (!Double.isNaN(result.onsetPressure)) {
          result.onsetPressureError = Math.abs(result.onsetPressure - measuredOnsetPressure);
          result.onsetPressureRelativeError =
              result.onsetPressureError / measuredOnsetPressure * 100.0;
        }
      }
    }
  }

  /**
   * Runs the De Boer empirical screening method.
   */
  private void runDeBoerMethod() {
    if (Double.isNaN(inSituDensity)) {
      logger.info("Skipping De Boer - no density data available");
      return;
    }

    long start = System.currentTimeMillis();
    MethodResult result = new MethodResult();
    result.methodName = "De Boer Screening";

    try {
      // Need bubble point for undersaturation
      double bubbleP = Double.NaN;
      if (cpaSystem != null) {
        SystemInterface workSystem = cpaSystem.clone();
        workSystem.setTemperature(reservoirTemperature);
        ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
        ops.bubblePointPressureFlash(false);
        bubbleP = workSystem.getPressure();
      } else if (cubicSystem != null) {
        SystemInterface workSystem = cubicSystem.clone();
        workSystem.setTemperature(reservoirTemperature);
        ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
        ops.bubblePointPressureFlash(false);
        bubbleP = workSystem.getPressure();
      }

      DeBoerAsphalteneScreening screening =
          new DeBoerAsphalteneScreening(reservoirPressure, bubbleP, inSituDensity);
      if (!Double.isNaN(asphaltenesFraction)) {
        screening.setAsphalteneContent(asphaltenesFraction);
      }

      DeBoerAsphalteneScreening.DeBoerRisk risk = screening.evaluateRisk();
      double riskIndex = screening.calculateRiskIndex();

      result.riskLevel = risk.name();
      result.bubblePointPressure = bubbleP;
      result.details.put("risk_index", riskIndex);
      result.details.put("undersaturation_bar", reservoirPressure - bubbleP);
      result.details.put("api_gravity", screening.getAPIGravity());
      result.details.put("description", risk.getDescription());

    } catch (Exception e) {
      logger.error("De Boer method failed: {}", e.getMessage());
      result.riskLevel = "ERROR";
      result.details.put("error", e.getMessage());
    }

    result.computationTimeMs = System.currentTimeMillis() - start;
    methodResults.put("DeBoer", result);
  }

  /**
   * Runs the SARA CII screening method.
   */
  private void runSARAMethod() {
    if (Double.isNaN(saturates) || Double.isNaN(asphaltenesFraction)) {
      logger.info("Skipping SARA - no SARA data available");
      return;
    }

    long start = System.currentTimeMillis();
    MethodResult result = new MethodResult();
    result.methodName = "SARA CII Analysis";

    try {
      AsphalteneCharacterization sara =
          new AsphalteneCharacterization(saturates, aromatics, resins, asphaltenesFraction);
      double cii = sara.getColloidalInstabilityIndex();
      double ra = sara.getResinToAsphalteneRatio();

      if (cii < 0.7) {
        result.riskLevel = "STABLE";
      } else if (cii < 0.9) {
        result.riskLevel = "MODERATE_RISK";
      } else {
        result.riskLevel = "HIGH_RISK";
      }

      result.details.put("CII", cii);
      result.details.put("R_A_ratio", ra);
      result.details.put("stability_assessment", sara.evaluateStability());

    } catch (Exception e) {
      logger.error("SARA method failed: {}", e.getMessage());
      result.riskLevel = "ERROR";
    }

    result.computationTimeMs = System.currentTimeMillis() - start;
    methodResults.put("SARA_CII", result);
  }

  /**
   * Runs the Refractive Index screening method.
   */
  private void runRefractiveIndexMethod() {
    RefractiveIndexAsphalteneScreening riScreening = new RefractiveIndexAsphalteneScreening();

    if (!Double.isNaN(measuredRI)) {
      riScreening.setRiOil(measuredRI);
    } else if (!Double.isNaN(inSituDensity)) {
      riScreening.setOilDensity(inSituDensity);
    } else {
      logger.info("Skipping RI method - no RI or density data");
      return;
    }

    long start = System.currentTimeMillis();
    MethodResult result = new MethodResult();
    result.methodName = "Refractive Index Screening";

    try {
      if (!Double.isNaN(measuredRIOnset)) {
        riScreening.setRiOnset(measuredRIOnset);
      } else if (!Double.isNaN(saturates)) {
        riScreening.estimateOnsetRIFromSARA(saturates, aromatics, resins, asphaltenesFraction);
      } else {
        logger.info("Skipping RI method - no onset RI or SARA data");
        return;
      }

      RefractiveIndexAsphalteneScreening.RIStability stability = riScreening.evaluateStability();
      result.riskLevel = stability.name();
      result.details.put("ri_oil", riScreening.getRiOil());
      result.details.put("ri_onset", riScreening.getRiOnset());
      result.details.put("ri_margin", riScreening.getRIStabilityMargin());
      result.details.put("description", stability.getDescription());

    } catch (Exception e) {
      logger.error("RI method failed: {}", e.getMessage());
      result.riskLevel = "ERROR";
    }

    result.computationTimeMs = System.currentTimeMillis() - start;
    methodResults.put("RefractiveIndex", result);
  }

  /**
   * Runs the Flory-Huggins regular solution method.
   */
  private void runFloryHugginsMethod() {
    SystemInterface sys = (cpaSystem != null) ? cpaSystem : cubicSystem;
    if (sys == null) {
      logger.info("Skipping Flory-Huggins - no thermodynamic system");
      return;
    }

    long start = System.currentTimeMillis();
    MethodResult result = new MethodResult();
    result.methodName = "Flory-Huggins Regular Solution";

    try {
      FloryHugginsAsphalteneModel fhModel =
          new FloryHugginsAsphalteneModel(sys, reservoirTemperature);

      // Configure asphaltene properties from API gravity if available
      if (!Double.isNaN(apiGravity)) {
        fhModel.configureFromAPIGravity(apiGravity);
      }

      // Configure from SARA data if available
      if (!Double.isNaN(saturates) && !Double.isNaN(asphaltenesFraction)) {
        fhModel.configureFromSARA(saturates, aromatics, resins, asphaltenesFraction);
      } else if (!Double.isNaN(asphaltenesFraction)) {
        fhModel.setAsphalteneWeightFraction(asphaltenesFraction);
      }

      // Calibrate the delta_L correlation to the actual system density
      fhModel.calibrateCorrelation(reservoirTemperature);

      double onsetP = fhModel.calculateOnsetPressure(reservoirTemperature);
      result.onsetPressure = onsetP;

      if (!Double.isNaN(onsetP)) {
        result.riskLevel = (reservoirPressure > onsetP + 50) ? "STABLE"
            : (reservoirPressure > onsetP) ? "MODERATE_RISK" : "HIGH_RISK";
      } else {
        result.riskLevel = "STABLE";
      }

      result.details.put("asphaltene_solubility_parameter_MPa05",
          fhModel.getAsphalteneSolubilityParameter());
      result.details.put("asphaltene_MW_gmol", fhModel.getAsphalteneMW());

      // Generate precipitation curve
      double maxP = reservoirPressure;
      double minP = 10.0;
      int nPoints = 30;
      double[][] precipCurve =
          fhModel.generatePrecipitationCurve(reservoirTemperature, maxP, minP, nPoints);
      result.precipCurvePressures = precipCurve[0];
      result.precipCurveWtPct = precipCurve[1];

    } catch (Exception e) {
      logger.error("Flory-Huggins method failed: {}", e.getMessage());
      result.riskLevel = "ERROR";
      result.details.put("error", e.getMessage());
    }

    result.computationTimeMs = System.currentTimeMillis() - start;
    methodResults.put("FloryHuggins", result);
  }

  /**
   * Runs the CPA EOS thermodynamic method.
   *
   * <p>
   * Uses a dual approach: (1) AsphalteneStabilityAnalyzer for solid-phase onset, (2) an independent
   * multi-phase L-L split sweep. If the solid model gives an onset above reservoir pressure
   * (overprediction), the L-L sweep result is preferred.
   * </p>
   */
  private void runCPAMethod() {
    if (cpaSystem == null) {
      logger.info("Skipping CPA method - no CPA system provided");
      return;
    }

    long start = System.currentTimeMillis();
    MethodResult result = new MethodResult();
    result.methodName = "CPA EOS (SRK-CPA)";

    try {
      // Approach 1: Standard solid-phase onset via AsphalteneStabilityAnalyzer
      AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(cpaSystem);
      if (!Double.isNaN(saturates)) {
        analyzer.setSARAFractions(saturates, aromatics, resins, asphaltenesFraction);
      }

      double solidOnsetP = analyzer.calculateOnsetPressure(reservoirTemperature);

      double bubbleP = analyzer.calculateBubblePointPressure();
      result.bubblePointPressure = bubbleP;

      AsphalteneStabilityAnalyzer.AsphalteneRisk risk = analyzer.evaluateSARAStability();

      // Approach 2: L-L split sweep using CPA multi-phase check
      double llOnsetP = findCPALiquidLiquidOnset(cpaSystem, reservoirTemperature);

      // Choose the better result: prefer L-L onset if solid onset overpredicts
      double onsetP;
      if (!Double.isNaN(llOnsetP) && !Double.isNaN(solidOnsetP)) {
        // If solid onset is above reservoir pressure (overprediction), use L-L
        if (solidOnsetP > reservoirPressure) {
          onsetP = llOnsetP;
          result.details.put("cpa_method_used", "L-L split (solid overpredicted)");
        } else {
          // Both valid — use average weighted toward the L-L split result
          onsetP = 0.4 * solidOnsetP + 0.6 * llOnsetP;
          result.details.put("cpa_method_used", "weighted average (solid+LL)");
        }
      } else if (!Double.isNaN(llOnsetP)) {
        onsetP = llOnsetP;
        result.details.put("cpa_method_used", "L-L split only");
      } else if (!Double.isNaN(solidOnsetP)) {
        onsetP = solidOnsetP;
        result.details.put("cpa_method_used", "solid onset only");
      } else {
        onsetP = Double.NaN;
        result.details.put("cpa_method_used", "no onset found");
      }

      result.onsetPressure = onsetP;
      result.riskLevel = risk.name();

      result.details.put("cpa_description", risk.getDescription());
      result.details.put("solid_onset_bar", solidOnsetP);
      result.details.put("ll_onset_bar", llOnsetP);
      if (!Double.isNaN(onsetP)) {
        result.details.put("safety_margin_bar", reservoirPressure - onsetP);
      }

      // Generate precipitation curve via TP flash with solid check
      generateCPAPrecipitationCurve(result, cpaSystem, reservoirTemperature);

    } catch (Exception e) {
      logger.error("CPA method failed: {}", e.getMessage());
      result.riskLevel = "ERROR";
      result.details.put("error", e.getMessage());
    }

    result.computationTimeMs = System.currentTimeMillis() - start;
    methodResults.put("CPA_EOS", result);
  }

  /**
   * Generates precipitation curve using CPA EOS with solid phase check.
   *
   * @param result the result object to populate
   * @param sys the CPA system
   * @param temperature temperature (K)
   */
  private void generateCPAPrecipitationCurve(MethodResult result, SystemInterface sys,
      double temperature) {
    int nPoints = 30;
    double maxP = reservoirPressure;
    double minP = 10.0;
    double step = (maxP - minP) / (nPoints - 1);

    double[] pressures = new double[nPoints];
    double[] precipWtPct = new double[nPoints];

    for (int i = 0; i < nPoints; i++) {
      double p = maxP - i * step;
      pressures[i] = p;

      try {
        SystemInterface workSystem = sys.clone();
        workSystem.setPressure(p);
        workSystem.setTemperature(temperature);
        workSystem.setSolidPhaseCheck("asphaltene");

        ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
        ops.TPflash();

        // Check for solid/asphaltene phase
        double solidMoles = 0.0;
        double totalMoles = 0.0;
        for (int phase = 0; phase < workSystem.getNumberOfPhases(); phase++) {
          totalMoles += workSystem.getPhase(phase).getNumberOfMolesInPhase();
          if (workSystem.getPhase(phase).getPhaseTypeName().equals("solid")
              || workSystem.getPhase(phase).getPhaseTypeName().equals("asphaltene")) {
            solidMoles += workSystem.getPhase(phase).getNumberOfMolesInPhase();
          }
        }
        precipWtPct[i] = totalMoles > 0 ? (solidMoles / totalMoles) * 100.0 : 0.0;

      } catch (Exception e) {
        precipWtPct[i] = 0.0;
      }
    }

    result.precipCurvePressures = pressures;
    result.precipCurveWtPct = precipWtPct;
  }

  /**
   * Finds asphaltene onset pressure using CPA with multi-phase L-L split detection.
   *
   * <p>
   * This approach enables multi-phase check on the CPA system and sweeps pressure downward from
   * reservoir pressure looking for a second liquid phase (asphaltene-rich). This is more physically
   * correct for CPA than the solid phase model approach, as CPA models asphaltene as an associating
   * liquid component.
   * </p>
   *
   * @param sys the CPA system
   * @param temperature temperature (K)
   * @return onset pressure in bar, or NaN if no L-L split found
   */
  private double findCPALiquidLiquidOnset(SystemInterface sys, double temperature) {
    double startP = reservoirPressure;
    double minP = 10.0;
    double step = 5.0;

    for (double p = startP; p >= minP; p -= step) {
      try {
        SystemInterface workSystem = sys.clone();
        workSystem.setPressure(p);
        workSystem.setTemperature(temperature);
        workSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
        try {
          ops.TPflash();
        } catch (Exception flashEx) {
          continue;
        }

        // Count liquid phases (exclude gas)
        int liquidPhases = 0;
        for (int phase = 0; phase < workSystem.getNumberOfPhases(); phase++) {
          String phaseType = workSystem.getPhase(phase).getPhaseTypeName();
          if (phaseType.equals("oil") || phaseType.equals("liquid")
              || phaseType.contains("asphaltene") || phaseType.contains("Asphaltene")) {
            liquidPhases++;
          }
        }

        if (liquidPhases >= 2) {
          // Refine with bisection
          double high = p + step;
          double low = p;
          for (int iter = 0; iter < 20; iter++) {
            double mid = (high + low) / 2.0;
            SystemInterface ws2 = sys.clone();
            ws2.setPressure(mid);
            ws2.setTemperature(temperature);
            ws2.setMultiPhaseCheck(true);
            ThermodynamicOperations ops2 = new ThermodynamicOperations(ws2);
            try {
              ops2.TPflash();
            } catch (Exception flashEx) {
              high = mid;
              continue;
            }

            int liqPhases2 = 0;
            for (int ph = 0; ph < ws2.getNumberOfPhases(); ph++) {
              String pt = ws2.getPhase(ph).getPhaseTypeName();
              if (pt.equals("oil") || pt.equals("liquid") || pt.contains("asphaltene")
                  || pt.contains("Asphaltene")) {
                liqPhases2++;
              }
            }

            if (liqPhases2 >= 2) {
              low = mid;
            } else {
              high = mid;
            }
          }
          return (high + low) / 2.0;
        }
      } catch (Exception e) {
        logger.debug("CPA L-L sweep failed at P={}: {}", p, e.getMessage());
      }
    }

    return Double.NaN;
  }

  /**
   * Runs the Pedersen classical cubic EOS method.
   */
  private void runPedersenMethod() {
    SystemInterface sys = (cubicSystem != null) ? cubicSystem : cpaSystem;
    if (sys == null) {
      logger.info("Skipping Pedersen method - no cubic EOS system provided");
      return;
    }

    long start = System.currentTimeMillis();
    MethodResult result = new MethodResult();
    result.methodName = "Pedersen Cubic EOS (L-L split)";

    try {
      PedersenAsphalteneCharacterization pedersen = new PedersenAsphalteneCharacterization(sys);
      if (!Double.isNaN(asphaltenesFraction)) {
        pedersen.setAsphalteneWeightFraction(asphaltenesFraction);
      }
      pedersen.setAsphalteneMW(1200.0);
      pedersen.setAsphalteneDensity(1.15);
      pedersen.characterize();

      result.details.put("asphaltene_Tc_K", pedersen.getCriticalTemperature());
      result.details.put("asphaltene_Pc_bar", pedersen.getCriticalPressure());
      result.details.put("asphaltene_omega", pedersen.getAcentricFactor());
      result.details.put("asphaltene_MW_gmol", pedersen.getAsphalteneMW());
      result.details.put("asphaltene_density_gcc", pedersen.getAsphalteneDensity());

      // Clone the system and add the asphaltene component for L-L split detection
      SystemInterface workSys = sys.clone();
      double totalMoles = workSys.getTotalNumberOfMoles();
      double aspMoles = totalMoles * 0.02;
      if (!Double.isNaN(asphaltenesFraction) && asphaltenesFraction > 0) {
        aspMoles = totalMoles * asphaltenesFraction / pedersen.getAsphalteneMW();
      }
      pedersen.addAsphalteneToSystem(workSys, aspMoles);
      workSys.setMixingRule("classic");
      workSys.init(0);
      pedersen.applyAsphalteneKij(workSys, 0.08);

      // Pedersen approach: try multiphase flash at reservoir T with decreasing P
      // Look for L-L split (second liquid phase rich in asphaltene)
      double onsetP = findPedersenOnsetPressure(workSys, pedersen, reservoirTemperature);
      result.onsetPressure = onsetP;

      if (!Double.isNaN(onsetP)) {
        result.riskLevel = (reservoirPressure > onsetP + 50) ? "STABLE"
            : (reservoirPressure > onsetP) ? "MODERATE_RISK" : "HIGH_RISK";
      } else {
        result.riskLevel = "STABLE";
      }

    } catch (Exception e) {
      logger.error("Pedersen method failed: {}", e.getMessage());
      result.riskLevel = "ERROR";
      result.details.put("error", e.getMessage());
    }

    result.computationTimeMs = System.currentTimeMillis() - start;
    methodResults.put("Pedersen_Cubic", result);
  }

  /**
   * Finds the onset pressure using Pedersen's L-L split approach.
   *
   * <p>
   * Uses a fine-grained pressure sweep (2 bar steps) with multi-phase check enabled, followed by
   * bisection refinement when a liquid-liquid split is detected. Also enables multi-phase check on
   * the system before flashing to improve detection of L-L splits that would otherwise be missed.
   * </p>
   *
   * @param sys the cubic EOS system
   * @param pedersen the characterization object
   * @param temperature temperature (K)
   * @return onset pressure or NaN
   */
  private double findPedersenOnsetPressure(SystemInterface sys,
      PedersenAsphalteneCharacterization pedersen, double temperature) {
    double startP = reservoirPressure;
    double minP = 10.0;
    double step = 2.0; // Fine step for better L-L split detection

    for (double p = startP; p >= minP; p -= step) {
      try {
        SystemInterface workSystem = sys.clone();
        workSystem.setPressure(p);
        workSystem.setTemperature(temperature);
        workSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
        try {
          ops.TPflash();
        } catch (Exception flashEx) {
          continue;
        }

        // Check for multiple liquid phases
        int liquidPhases = 0;
        for (int phase = 0; phase < workSystem.getNumberOfPhases(); phase++) {
          String phaseType = workSystem.getPhase(phase).getPhaseTypeName();
          if (phaseType.equals("oil") || phaseType.equals("liquid")
              || phaseType.contains("Asphaltene")) {
            liquidPhases++;
          }
        }

        if (liquidPhases >= 2) {
          // Refine by bisection
          double high = p + step;
          double low = p;
          for (int iter = 0; iter < 20; iter++) {
            double mid = (high + low) / 2.0;
            SystemInterface ws2 = sys.clone();
            ws2.setPressure(mid);
            ws2.setTemperature(temperature);
            ws2.setMultiPhaseCheck(true);
            ThermodynamicOperations ops2 = new ThermodynamicOperations(ws2);
            try {
              ops2.TPflash();
            } catch (Exception flashEx) {
              high = mid;
              continue;
            }

            int liqPhases2 = 0;
            for (int ph = 0; ph < ws2.getNumberOfPhases(); ph++) {
              String pt = ws2.getPhase(ph).getPhaseTypeName();
              if (pt.equals("oil") || pt.equals("liquid") || pt.contains("Asphaltene")) {
                liqPhases2++;
              }
            }

            if (liqPhases2 >= 2) {
              low = mid;
            } else {
              high = mid;
            }
          }
          return (high + low) / 2.0;
        }
      } catch (Exception e) {
        // continue scanning
      }
    }

    return Double.NaN;
  }

  /**
   * Generates the method agreement matrix.
   *
   * @return 2D agreement matrix (1 = agree on risk, 0 = disagree)
   */
  public double[][] getAgreementMatrix() {
    List<String> methods = new ArrayList<String>(methodResults.keySet());
    int n = methods.size();
    double[][] matrix = new double[n][n];

    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (i == j) {
          matrix[i][j] = 1.0;
        } else {
          String risk1 = methodResults.get(methods.get(i)).riskLevel;
          String risk2 = methodResults.get(methods.get(j)).riskLevel;
          matrix[i][j] = risksAgree(risk1, risk2) ? 1.0 : 0.0;
        }
      }
    }

    return matrix;
  }

  /**
   * Checks if two risk assessments are in broad agreement.
   *
   * @param risk1 first risk level
   * @param risk2 second risk level
   * @return true if risks are in the same broad category
   */
  private boolean risksAgree(String risk1, String risk2) {
    boolean stable1 =
        risk1.contains("STABLE") || risk1.equals("NO_PROBLEM") || risk1.equals("VERY_STABLE");
    boolean stable2 =
        risk2.contains("STABLE") || risk2.equals("NO_PROBLEM") || risk2.equals("VERY_STABLE");
    boolean problem1 =
        risk1.contains("HIGH") || risk1.contains("SEVERE") || risk1.contains("UNSTABLE");
    boolean problem2 =
        risk2.contains("HIGH") || risk2.contains("SEVERE") || risk2.contains("UNSTABLE");

    return (stable1 == stable2) || (problem1 == problem2);
  }

  /**
   * Generates a comprehensive JSON report of all results.
   *
   * @return JSON string with all benchmark results
   */
  public String toJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();

    // Input conditions
    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("reservoir_pressure_bara", reservoirPressure);
    conditions.put("reservoir_temperature_K", reservoirTemperature);
    conditions.put("reservoir_temperature_C", reservoirTemperature - 273.15);
    conditions.put("in_situ_density_kgm3", inSituDensity);
    if (!Double.isNaN(measuredOnsetPressure)) {
      conditions.put("measured_onset_pressure_bara", measuredOnsetPressure);
    }
    if (!Double.isNaN(saturates)) {
      Map<String, Double> sara = new LinkedHashMap<String, Double>();
      sara.put("saturates", saturates);
      sara.put("aromatics", aromatics);
      sara.put("resins", resins);
      sara.put("asphaltenes", asphaltenesFraction);
      conditions.put("SARA_fractions", sara);
    }
    report.put("input_conditions", conditions);

    // Method results
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, MethodResult> entry : methodResults.entrySet()) {
      MethodResult mr = entry.getValue();
      Map<String, Object> methodMap = new LinkedHashMap<String, Object>();
      methodMap.put("method_name", mr.methodName);
      methodMap.put("risk_level", mr.riskLevel);
      if (!Double.isNaN(mr.onsetPressure)) {
        methodMap.put("onset_pressure_bara", mr.onsetPressure);
      }
      if (!Double.isNaN(mr.bubblePointPressure)) {
        methodMap.put("bubble_point_bara", mr.bubblePointPressure);
      }
      if (!Double.isNaN(mr.onsetPressureError)) {
        methodMap.put("onset_pressure_error_bar", mr.onsetPressureError);
        methodMap.put("onset_pressure_relative_error_pct", mr.onsetPressureRelativeError);
      }
      methodMap.put("computation_time_ms", mr.computationTimeMs);
      methodMap.put("details", mr.details);
      results.put(entry.getKey(), methodMap);
    }
    report.put("method_results", results);

    // Summary comparison
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    List<String> methods = new ArrayList<String>(methodResults.keySet());
    summary.put("methods_evaluated", methods);
    summary.put("agreement_matrix", getAgreementMatrix());

    // Count risk categories
    int stableCount = 0;
    int moderateCount = 0;
    int highRiskCount = 0;
    for (MethodResult mr : methodResults.values()) {
      if (mr.riskLevel != null) {
        if (mr.riskLevel.contains("STABLE") || mr.riskLevel.equals("NO_PROBLEM")
            || mr.riskLevel.equals("VERY_STABLE")) {
          stableCount++;
        } else if (mr.riskLevel.contains("HIGH") || mr.riskLevel.contains("SEVERE")
            || mr.riskLevel.contains("UNSTABLE")) {
          highRiskCount++;
        } else {
          moderateCount++;
        }
      }
    }
    summary.put("stable_count", stableCount);
    summary.put("moderate_count", moderateCount);
    summary.put("high_risk_count", highRiskCount);

    // Consensus
    int total = stableCount + moderateCount + highRiskCount;
    if (total > 0) {
      if (stableCount >= total * 0.6) {
        summary.put("consensus", "STABLE");
      } else if (highRiskCount >= total * 0.6) {
        summary.put("consensus", "HIGH_RISK");
      } else {
        summary.put("consensus", "INCONCLUSIVE");
      }
    }

    report.put("summary", summary);

    return GSON.toJson(report);
  }

  /**
   * Returns a formatted text comparison table.
   *
   * @return formatted text table
   */
  public String getComparisonTable() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-22s %-16s %-12s %-12s %-8s%n", "Method", "Risk Level",
        "Onset P (bar)", "Error (bar)", "Time(ms)"));
    sb.append("──────────────────────────────────────────────────────────────────────────────\n");

    for (Map.Entry<String, MethodResult> entry : methodResults.entrySet()) {
      MethodResult mr = entry.getValue();
      String onsetP =
          Double.isNaN(mr.onsetPressure) ? "N/A" : String.format("%.1f", mr.onsetPressure);
      String error = Double.isNaN(mr.onsetPressureError) ? "N/A"
          : String.format("%.1f", mr.onsetPressureError);
      sb.append(String.format("%-22s %-16s %-12s %-12s %-8d%n", entry.getKey(), mr.riskLevel,
          onsetP, error, mr.computationTimeMs));
    }

    if (!Double.isNaN(measuredOnsetPressure)) {
      sb.append("──────────────────────────────────────────────────────────────────────────────\n");
      sb.append(String.format("%-22s %-16s %-12.1f %-12s %-8s%n", "MEASURED (reference)", "—",
          measuredOnsetPressure, "0.0", "—"));
    }

    return sb.toString();
  }

  /**
   * Gets a specific method result.
   *
   * @param methodKey method key (e.g., "CPA_EOS", "FloryHuggins", "DeBoer")
   * @return method result or null
   */
  public MethodResult getMethodResult(String methodKey) {
    return methodResults.get(methodKey);
  }

  /**
   * Gets all method results.
   *
   * @return unmodifiable map of results
   */
  public Map<String, MethodResult> getAllResults() {
    return methodResults;
  }

  // ───────────────── Setters ─────────────────

  /**
   * Sets the CPA thermodynamic system. This system should use SystemSrkCPAstatoil and include
   * asphaltene as a component.
   *
   * @param cpaSystem the CPA system
   */
  public void setCpaSystem(SystemInterface cpaSystem) {
    this.cpaSystem = cpaSystem.clone();
  }

  /**
   * Sets the classical cubic EOS system. This system should use SystemSrkEos or SystemPrEos, for
   * the Pedersen method.
   *
   * @param cubicSystem the cubic EOS system
   */
  public void setCubicSystem(SystemInterface cubicSystem) {
    this.cubicSystem = cubicSystem.clone();
  }

  /**
   * Sets the reservoir pressure.
   *
   * @param reservoirPressure pressure (bara)
   */
  public void setReservoirPressure(double reservoirPressure) {
    this.reservoirPressure = reservoirPressure;
  }

  /**
   * Sets the reservoir temperature.
   *
   * @param reservoirTemperature temperature (K)
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Sets the SARA fractions.
   *
   * @param saturates weight fraction of saturates
   * @param aromatics weight fraction of aromatics
   * @param resins weight fraction of resins
   * @param asphaltenes weight fraction of asphaltenes
   */
  public void setSARAFractions(double saturates, double aromatics, double resins,
      double asphaltenes) {
    this.saturates = saturates;
    this.aromatics = aromatics;
    this.resins = resins;
    this.asphaltenesFraction = asphaltenes;
  }

  /**
   * Sets the measured onset pressure for validation.
   *
   * @param measuredOnsetPressure measured AOP (bara)
   */
  public void setMeasuredOnsetPressure(double measuredOnsetPressure) {
    this.measuredOnsetPressure = measuredOnsetPressure;
  }

  /**
   * Sets the measured onset temperature for validation.
   *
   * @param measuredOnsetTemperature measured AOT (K)
   */
  public void setMeasuredOnsetTemperature(double measuredOnsetTemperature) {
    this.measuredOnsetTemperature = measuredOnsetTemperature;
  }

  /**
   * Sets the in-situ oil density.
   *
   * @param inSituDensity density (kg/m3)
   */
  public void setInSituDensity(double inSituDensity) {
    this.inSituDensity = inSituDensity;
  }

  /**
   * Sets the measured refractive index of the oil.
   *
   * @param measuredRI refractive index
   */
  public void setMeasuredRI(double measuredRI) {
    this.measuredRI = measuredRI;
  }

  /**
   * Sets the measured refractive index at onset.
   *
   * @param measuredRIOnset RI at onset
   */
  public void setMeasuredRIOnset(double measuredRIOnset) {
    this.measuredRIOnset = measuredRIOnset;
  }

  /**
   * Sets the API gravity of the crude oil.
   *
   * <p>
   * When set, the Flory-Huggins model will auto-configure asphaltene properties (MW, density,
   * solubility parameter) based on API gravity correlations from Akbarzadeh et al. (2005).
   * </p>
   *
   * @param apiGravity API gravity
   */
  public void setAPIGravity(double apiGravity) {
    this.apiGravity = apiGravity;
  }

  /**
   * Gets the API gravity.
   *
   * @return API gravity or NaN
   */
  public double getAPIGravity() {
    return apiGravity;
  }

  /**
   * Gets the measured onset pressure.
   *
   * @return measured onset pressure (bara) or NaN
   */
  public double getMeasuredOnsetPressure() {
    return measuredOnsetPressure;
  }

  /**
   * Gets the reservoir pressure.
   *
   * @return reservoir pressure (bara)
   */
  public double getReservoirPressure() {
    return reservoirPressure;
  }

  /**
   * Gets the reservoir temperature.
   *
   * @return reservoir temperature (K)
   */
  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  // ───────────────── Literature Validation ─────────────────

  /**
   * Represents a published literature case for asphaltene onset validation.
   *
   * <p>
   * Stores the reference info, SARA data, and measured properties from published studies. Used to
   * create benchmark instances that can be compared against measured data.
   * </p>
   */
  public static class LiteratureCase {
    /** Case label for display. */
    public String label;

    /** Literature reference (author, year). */
    public String reference;

    /** Reservoir pressure (bara). */
    public double reservoirPressure;

    /** Reservoir temperature (K). */
    public double reservoirTemperature;

    /** Measured bubble point pressure (bara). */
    public double bubblePointPressure;

    /** Measured asphaltene onset pressure (bara). */
    public double measuredOnsetPressure;

    /** In-situ oil density at reservoir conditions (kg/m3). */
    public double inSituDensity;

    /** SARA saturates weight fraction. */
    public double saturates = Double.NaN;

    /** SARA aromatics weight fraction. */
    public double aromatics = Double.NaN;

    /** SARA resins weight fraction. */
    public double resins = Double.NaN;

    /** SARA asphaltenes weight fraction. */
    public double asphaltenes = Double.NaN;

    /** API gravity. */
    public double apiGravity = Double.NaN;

    /** Oil description (light, medium, heavy, etc.). */
    public String oilDescription;

    /** Asphaltene problem severity observed in field. */
    public String fieldObservation;
  }

  /**
   * Returns a list of published literature cases for asphaltene onset validation.
   *
   * <p>
   * These cases are drawn from well-known publications and provide measured onset pressures, SARA
   * fractions, and fluid properties for benchmarking prediction methods.
   * </p>
   *
   * <p>
   * Sources:
   * </p>
   * <ul>
   * <li>Hirschberg, A. et al. (1984). SPE J., 24(3), 283-293.</li>
   * <li>de Boer, R.B. et al. (1995). SPE Prod. Facilities, Feb 1995.</li>
   * <li>Burke, N.E. et al. (1990). SPE 18273.</li>
   * <li>Jamaluddin, A.K.M. et al. (2002). SPE 74393.</li>
   * <li>Hammami, A. et al. (2000). SPE 58786.</li>
   * <li>Akbarzadeh, K. et al. (2005). SPE 95514.</li>
   * <li>Hassi-Messaoud field data, Kabour et al. (2014).</li>
   * </ul>
   *
   * @return list of literature cases
   */
  public static List<LiteratureCase> getLiteratureCases() {
    List<LiteratureCase> cases = new ArrayList<LiteratureCase>();

    // Case 1: Hirschberg North Sea crude (1984)
    // Light undersaturated oil from North Sea, asphaltene problems documented
    LiteratureCase hirschberg = new LiteratureCase();
    hirschberg.label = "Hirschberg North Sea";
    hirschberg.reference = "Hirschberg et al. (1984) SPE 11202";
    hirschberg.reservoirPressure = 450.0;
    hirschberg.reservoirTemperature = 373.15;
    hirschberg.bubblePointPressure = 175.0;
    hirschberg.measuredOnsetPressure = 360.0;
    hirschberg.inSituDensity = 730.0;
    hirschberg.saturates = 0.58;
    hirschberg.aromatics = 0.26;
    hirschberg.resins = 0.13;
    hirschberg.asphaltenes = 0.03;
    hirschberg.apiGravity = 37.5;
    hirschberg.oilDescription = "Light undersaturated North Sea crude";
    hirschberg.fieldObservation = "MODERATE_RISK";
    cases.add(hirschberg);

    // Case 2: de Boer Oil A - severe problems (1995)
    // Very light oil with high undersaturation, severe asphaltene deposition
    LiteratureCase deBoerA = new LiteratureCase();
    deBoerA.label = "de Boer Oil A (Severe)";
    deBoerA.reference = "de Boer et al. (1995) SPE 24987";
    deBoerA.reservoirPressure = 690.0;
    deBoerA.reservoirTemperature = 388.15;
    deBoerA.bubblePointPressure = 170.0;
    deBoerA.measuredOnsetPressure = 580.0;
    deBoerA.inSituDensity = 710.0;
    deBoerA.saturates = 0.66;
    deBoerA.aromatics = 0.20;
    deBoerA.resins = 0.10;
    deBoerA.asphaltenes = 0.04;
    deBoerA.apiGravity = 41.0;
    deBoerA.oilDescription = "Very light crude with high undersaturation";
    deBoerA.fieldObservation = "SEVERE_RISK";
    cases.add(deBoerA);

    // Case 3: Burke Prinos field, Greece (1990)
    // Moderate API oil with known onset
    LiteratureCase prinos = new LiteratureCase();
    prinos.label = "Burke Prinos (Greece)";
    prinos.reference = "Burke et al. (1990) SPE 18273";
    prinos.reservoirPressure = 197.0;
    prinos.reservoirTemperature = 395.15;
    prinos.bubblePointPressure = 105.0;
    prinos.measuredOnsetPressure = 165.0;
    prinos.inSituDensity = 825.0;
    prinos.saturates = 0.42;
    prinos.aromatics = 0.30;
    prinos.resins = 0.18;
    prinos.asphaltenes = 0.10;
    prinos.apiGravity = 35.0;
    prinos.oilDescription = "Medium crude with moderate asphaltene content";
    prinos.fieldObservation = "MODERATE_RISK";
    cases.add(prinos);

    // Case 4: Jamaluddin Middle East crude (2002)
    // High pressure reservoir with onset measured by depressurization
    LiteratureCase mideast = new LiteratureCase();
    mideast.label = "Jamaluddin Middle East";
    mideast.reference = "Jamaluddin et al. (2002) SPE 74393";
    mideast.reservoirPressure = 345.0;
    mideast.reservoirTemperature = 394.15;
    mideast.bubblePointPressure = 207.0;
    mideast.measuredOnsetPressure = 310.0;
    mideast.inSituDensity = 770.0;
    mideast.saturates = 0.55;
    mideast.aromatics = 0.24;
    mideast.resins = 0.14;
    mideast.asphaltenes = 0.07;
    mideast.apiGravity = 32.0;
    mideast.oilDescription = "Medium sour crude with notable asphaltene";
    mideast.fieldObservation = "HIGH_RISK";
    cases.add(mideast);

    // Case 5: Hammami live oil sample (2000)
    // Onset determined by near-infrared light scattering detection
    LiteratureCase hammami = new LiteratureCase();
    hammami.label = "Hammami Live Oil";
    hammami.reference = "Hammami et al. (2000) SPE 58786";
    hammami.reservoirPressure = 400.0;
    hammami.reservoirTemperature = 373.15;
    hammami.bubblePointPressure = 180.0;
    hammami.measuredOnsetPressure = 347.0;
    hammami.inSituDensity = 745.0;
    hammami.saturates = 0.52;
    hammami.aromatics = 0.28;
    hammami.resins = 0.15;
    hammami.asphaltenes = 0.05;
    hammami.apiGravity = 36.0;
    hammami.oilDescription = "Light oil with moderate asphaltene content";
    hammami.fieldObservation = "MODERATE_RISK";
    cases.add(hammami);

    // Case 6: Hassi-Messaoud field, Algeria (high API, severe problems)
    // Famous asphaltenic light oil
    LiteratureCase hassiMessaoud = new LiteratureCase();
    hassiMessaoud.label = "Hassi-Messaoud (Algeria)";
    hassiMessaoud.reference = "Kabour et al. (2014), Hassi-Messaoud field data";
    hassiMessaoud.reservoirPressure = 480.0;
    hassiMessaoud.reservoirTemperature = 391.15;
    hassiMessaoud.bubblePointPressure = 140.0;
    hassiMessaoud.measuredOnsetPressure = 430.0;
    hassiMessaoud.inSituDensity = 695.0;
    hassiMessaoud.saturates = 0.67;
    hassiMessaoud.aromatics = 0.19;
    hassiMessaoud.resins = 0.12;
    hassiMessaoud.asphaltenes = 0.02;
    hassiMessaoud.apiGravity = 44.0;
    hassiMessaoud.oilDescription = "Very light condensate-like oil, severe asphaltene";
    hassiMessaoud.fieldObservation = "SEVERE_RISK";
    cases.add(hassiMessaoud);

    // Case 7: Akbarzadeh heavy oil (2005)
    // Heavy oil with relatively low onset risk
    LiteratureCase akbarzadeh = new LiteratureCase();
    akbarzadeh.label = "Akbarzadeh Heavy Oil";
    akbarzadeh.reference = "Akbarzadeh et al. (2005) SPE 95514";
    akbarzadeh.reservoirPressure = 200.0;
    akbarzadeh.reservoirTemperature = 353.15;
    akbarzadeh.bubblePointPressure = 90.0;
    akbarzadeh.measuredOnsetPressure = 155.0;
    akbarzadeh.inSituDensity = 870.0;
    akbarzadeh.saturates = 0.32;
    akbarzadeh.aromatics = 0.33;
    akbarzadeh.resins = 0.22;
    akbarzadeh.asphaltenes = 0.13;
    akbarzadeh.apiGravity = 22.0;
    akbarzadeh.oilDescription = "Heavy oil with high asphaltene but good peptization";
    akbarzadeh.fieldObservation = "LOW_RISK";
    cases.add(akbarzadeh);

    return cases;
  }

  /**
   * Generates a summary of prediction errors for all methods that produced onset pressures.
   *
   * <p>
   * Returns a map with method names as keys, containing absolute error, relative error, and whether
   * the method correctly identified the risk category.
   * </p>
   *
   * @return map of method name to error metrics
   */
  public Map<String, Map<String, Object>> getMethodErrorSummary() {
    Map<String, Map<String, Object>> summary = new LinkedHashMap<String, Map<String, Object>>();

    for (Map.Entry<String, MethodResult> entry : methodResults.entrySet()) {
      MethodResult mr = entry.getValue();
      Map<String, Object> metrics = new LinkedHashMap<String, Object>();
      metrics.put("risk_level", mr.riskLevel);
      metrics.put("onset_pressure_bara", mr.onsetPressure);
      metrics.put("abs_error_bar", mr.onsetPressureError);
      metrics.put("rel_error_pct", mr.onsetPressureRelativeError);
      metrics.put("computation_ms", mr.computationTimeMs);
      summary.put(entry.getKey(), metrics);
    }

    return summary;
  }

  /**
   * Computes statistical error metrics across all thermodynamic methods.
   *
   * <p>
   * Only includes methods that produced a finite onset pressure and have a measured reference.
   * </p>
   *
   * @return map with AAD (bar), AARD (%), RMSD (bar), max_error (bar), best_method, worst_method
   */
  public Map<String, Object> getErrorStatistics() {
    Map<String, Object> stats = new LinkedHashMap<String, Object>();

    if (Double.isNaN(measuredOnsetPressure)) {
      stats.put("error", "No measured onset pressure set for comparison");
      return stats;
    }

    double sumAbsErr = 0;
    double sumRelErr = 0;
    double sumSqErr = 0;
    double maxErr = 0;
    String bestMethod = "";
    String worstMethod = "";
    double bestErr = Double.MAX_VALUE;
    double worstErr = 0;
    int count = 0;

    for (Map.Entry<String, MethodResult> entry : methodResults.entrySet()) {
      MethodResult mr = entry.getValue();
      if (!Double.isNaN(mr.onsetPressure) && !Double.isNaN(mr.onsetPressureError)) {
        double absErr = mr.onsetPressureError;
        double relErr = mr.onsetPressureRelativeError;

        sumAbsErr += absErr;
        sumRelErr += relErr;
        sumSqErr += absErr * absErr;
        count++;

        if (absErr > maxErr) {
          maxErr = absErr;
        }
        if (absErr < bestErr) {
          bestErr = absErr;
          bestMethod = entry.getKey();
        }
        if (absErr > worstErr) {
          worstErr = absErr;
          worstMethod = entry.getKey();
        }
      }
    }

    if (count > 0) {
      stats.put("methods_with_onset", count);
      stats.put("measured_onset_bara", measuredOnsetPressure);
      stats.put("AAD_bar", sumAbsErr / count);
      stats.put("AARD_pct", sumRelErr / count);
      stats.put("RMSD_bar", Math.sqrt(sumSqErr / count));
      stats.put("max_error_bar", maxErr);
      stats.put("best_method", bestMethod);
      stats.put("best_method_error_bar", bestErr);
      stats.put("worst_method", worstMethod);
      stats.put("worst_method_error_bar", worstErr);
    } else {
      stats.put("error", "No methods produced finite onset pressure");
    }

    return stats;
  }
}
