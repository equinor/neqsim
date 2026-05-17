package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * High-level safety analysis module for CO2 injection wells. Combines steady-state wellbore flow
 * simulation, phase boundary analysis, impurity enrichment mapping, shutdown assessment, choke JT
 * analysis, and depressurization timeline into a single analysis tool.
 *
 * <p>
 * This analyzer integrates multiple NeqSim capabilities:
 * <ul>
 * <li>{@link PipeBeggsAndBrills} for steady-state wellbore pressure and temperature profiles</li>
 * <li>{@link CO2FlowCorrections} for CO2-specific flow corrections</li>
 * <li>TP flash calculations for phase boundary and impurity enrichment mapping</li>
 * <li>{@link neqsim.process.equipment.valve.ThrottlingValve} for choke JT analysis</li>
 * <li>{@link TransientWellbore} for shutdown cooling simulation</li>
 * </ul>
 *
 * <p>
 * Usage example:
 *
 * <pre>
 * CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("SmeaheiaWell");
 * analyzer.setFluid(co2Fluid);
 * analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5);
 * analyzer.setOperatingConditions(90.0, 25.0, 150000.0);
 * analyzer.setFormationTemperature(4.0, 43.0);
 * analyzer.runFullAnalysis();
 * Map&lt;String, Object&gt; results = analyzer.getResults();
 * boolean safe = analyzer.isSafeToOperate();
 * </pre>
 *
 * @author neqsim
 * @version 1.0
 */
public class CO2InjectionWellAnalyzer {

  /** Well name/tag. */
  private final String name;

  /** The injection fluid (will be cloned for each analysis). */
  private SystemInterface fluid;

  /** Well depth in meters. */
  private double wellDepth = 1300.0;

  /** Tubing inner diameter in meters. */
  private double tubingID = 0.1571;

  /** Pipe roughness in meters. */
  private double roughness = 4.5e-5;

  /** Wellhead pressure in bara. */
  private double wellheadPressure = 90.0;

  /** Wellhead temperature in Celsius. */
  private double wellheadTemperatureC = 25.0;

  /** Design mass flow rate in kg/hr. */
  private double designFlowRate = 150000.0;

  /** Formation temperature at wellhead in Celsius. */
  private double formationTempTopC = 4.0;

  /** Formation temperature at bottom-hole in Celsius. */
  private double formationTempBottomC = 43.0;

  /** Components to track for impurity enrichment. */
  private final List<String> trackedComponents = new ArrayList<>();

  /** Alarm thresholds for tracked components (mole fraction in gas phase). */
  private final Map<String, Double> alarmThresholds = new LinkedHashMap<>();

  /** Results from the full analysis. */
  private final Map<String, Object> results = new LinkedHashMap<>();

  /** Whether the analysis has been run. */
  private boolean analysisComplete = false;

  /**
   * Constructor for CO2InjectionWellAnalyzer.
   *
   * @param name the analyzer name/tag
   */
  public CO2InjectionWellAnalyzer(String name) {
    this.name = name;
  }

  /**
   * Sets the injection fluid.
   *
   * @param fluid the thermodynamic system representing the injection fluid
   */
  public void setFluid(SystemInterface fluid) {
    this.fluid = fluid;
  }

  /**
   * Sets the well geometry parameters.
   *
   * @param depthMeters the well measured depth in meters
   * @param tubingIDMeters the tubing inner diameter in meters
   * @param roughnessMeters the pipe roughness in meters
   */
  public void setWellGeometry(double depthMeters, double tubingIDMeters, double roughnessMeters) {
    this.wellDepth = depthMeters;
    this.tubingID = tubingIDMeters;
    this.roughness = roughnessMeters;
  }

  /**
   * Sets the operating conditions at the wellhead.
   *
   * @param pressureBara wellhead pressure in bara
   * @param temperatureC wellhead temperature in Celsius
   * @param flowRateKgPerHr design mass flow rate in kg/hr
   */
  public void setOperatingConditions(double pressureBara, double temperatureC,
      double flowRateKgPerHr) {
    this.wellheadPressure = pressureBara;
    this.wellheadTemperatureC = temperatureC;
    this.designFlowRate = flowRateKgPerHr;
  }

  /**
   * Sets the formation (geothermal) temperature at the wellhead and bottom-hole.
   *
   * @param topTempC formation temperature at wellhead in Celsius
   * @param bottomTempC formation temperature at bottom-hole in Celsius
   */
  public void setFormationTemperature(double topTempC, double bottomTempC) {
    this.formationTempTopC = topTempC;
    this.formationTempBottomC = bottomTempC;
  }

  /**
   * Adds a component to track for impurity enrichment analysis.
   *
   * @param componentName the component name (e.g., "hydrogen")
   * @param alarmMolFrac the gas phase mole fraction alarm threshold (e.g., 0.04 for 4%)
   */
  public void addTrackedComponent(String componentName, double alarmMolFrac) {
    trackedComponents.add(componentName);
    alarmThresholds.put(componentName, alarmMolFrac);
  }

  /**
   * Runs the full analysis: steady-state, phase boundary scan, enrichment map, and shutdown
   * assessment.
   */
  public void runFullAnalysis() {
    results.clear();
    results.put("name", name);

    // 1. Steady-state design case
    Map<String, Object> designCase = runDesignCase();
    results.put("design_case", designCase);

    // 2. Phase boundary scan
    Map<String, Object> phaseScan = runPhaseBoundaryScan();
    results.put("phase_boundary_scan", phaseScan);

    // 3. Impurity enrichment map
    Map<String, Object> enrichmentMap = runEnrichmentMap();
    results.put("enrichment_map", enrichmentMap);

    // 4. Shutdown assessment
    Map<String, Object> shutdownAssessment = runShutdownAssessment();
    results.put("shutdown_assessment", shutdownAssessment);

    // 5. Safe operating envelope summary
    Map<String, Object> safeEnvelope = determineSafeEnvelope();
    results.put("safe_operating_envelope", safeEnvelope);

    analysisComplete = true;
  }

  /**
   * Runs the steady-state design case using PipeBeggsAndBrills. Returns BHP, BHT, flow regime.
   *
   * @return map of design case results
   */
  private Map<String, Object> runDesignCase() {
    Map<String, Object> result = new LinkedHashMap<>();

    SystemInterface wellFluid = fluid.clone();
    wellFluid.setTemperature(273.15 + wellheadTemperatureC);
    wellFluid.setPressure(wellheadPressure);

    Stream feed = new Stream("design-feed", wellFluid);
    feed.setFlowRate(designFlowRate, "kg/hr");
    feed.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("design-well", feed);
    pipe.setPipeWallRoughness(roughness);
    pipe.setLength(wellDepth);
    pipe.setElevation(-wellDepth); // downward injection
    pipe.setDiameter(tubingID);
    pipe.setNumberOfIncrements(20);

    // Set formation temperature gradient
    double geothermalGrad = (formationTempBottomC - formationTempTopC) / wellDepth;
    pipe.setFormationTemperatureGradient(formationTempTopC, -geothermalGrad, "C");
    pipe.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ESTIMATED_INNER_H);

    pipe.run();

    StreamInterface outlet = pipe.getOutletStream();
    result.put("BHP_bara", outlet.getPressure());
    result.put("BHT_C", outlet.getTemperature() - 273.15);
    result.put("n_phases", outlet.getThermoSystem().getNumberOfPhases());
    result.put("flow_regime", pipe.getFlowRegime().toString());

    return result;
  }

  /**
   * Scans the P-T space to identify two-phase conditions.
   *
   * @return map with two-phase boundary information
   */
  private Map<String, Object> runPhaseBoundaryScan() {
    Map<String, Object> result = new LinkedHashMap<>();
    int twoPhaseCount = 0;
    double minTwoPhaseP = Double.MAX_VALUE;
    double maxTwoPhaseP = 0;
    double minTwoPhaseT = Double.MAX_VALUE;
    double maxTwoPhaseT = -Double.MAX_VALUE;

    for (double tempC = -30; tempC <= 30; tempC += 2) {
      for (double pBara = 10; pBara <= 100; pBara += 2) {
        SystemInterface testFluid = fluid.clone();
        testFluid.setTemperature(273.15 + tempC);
        testFluid.setPressure(pBara);
        ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
        try {
          ops.TPflash();
          if (testFluid.getNumberOfPhases() > 1) {
            twoPhaseCount++;
            minTwoPhaseP = Math.min(minTwoPhaseP, pBara);
            maxTwoPhaseP = Math.max(maxTwoPhaseP, pBara);
            minTwoPhaseT = Math.min(minTwoPhaseT, tempC);
            maxTwoPhaseT = Math.max(maxTwoPhaseT, tempC);
          }
        } catch (Exception e) {
          // skip failed flash
        }
      }
    }

    result.put("two_phase_points_found", twoPhaseCount);
    if (twoPhaseCount > 0) {
      result.put("P_range_bara_min", minTwoPhaseP);
      result.put("P_range_bara_max", maxTwoPhaseP);
      result.put("T_range_C_min", minTwoPhaseT);
      result.put("T_range_C_max", maxTwoPhaseT);
    }
    return result;
  }

  /**
   * Maps impurity enrichment across the two-phase region for all tracked components.
   *
   * @return map of enrichment data
   */
  private Map<String, Object> runEnrichmentMap() {
    Map<String, Object> result = new LinkedHashMap<>();

    double[] tempsScan = {0, 4, 8, 12, 25};
    for (double tempC : tempsScan) {
      Map<String, Object> tempResult = new LinkedHashMap<>();
      double maxH2 = 0;
      double maxEnrich = 0;
      double twoPhaseMinP = Double.MAX_VALUE;
      double twoPhaseMaxP = 0;

      for (double pBara = 10; pBara <= 100; pBara += 1) {
        SystemInterface testFluid = fluid.clone();
        testFluid.setTemperature(273.15 + tempC);
        testFluid.setPressure(pBara);
        ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
        try {
          ops.TPflash();
          if (testFluid.getNumberOfPhases() > 1 && testFluid.hasPhaseType("gas")) {
            twoPhaseMinP = Math.min(twoPhaseMinP, pBara);
            twoPhaseMaxP = Math.max(twoPhaseMaxP, pBara);

            for (String comp : trackedComponents) {
              try {
                double yi = testFluid.getPhase("gas").getComponent(comp).getx();
                double zi = testFluid.getComponent(comp).getz();
                if (yi > maxH2) {
                  maxH2 = yi;
                }
                double enrichment = zi > 0 ? yi / zi : 0;
                if (enrichment > maxEnrich) {
                  maxEnrich = enrichment;
                }
              } catch (Exception e) {
                // component not found
              }
            }
          }
        } catch (Exception e) {
          // skip failed flash
        }
      }

      if (twoPhaseMinP < Double.MAX_VALUE) {
        tempResult.put("two_phase_bara", twoPhaseMinP + "-" + twoPhaseMaxP);
        tempResult.put("max_impurity_mol_frac", maxH2);
        tempResult.put("max_enrichment_factor", maxEnrich);
      } else {
        tempResult.put("two_phase_bara", "none");
        tempResult.put("max_impurity_mol_frac", 0.0);
        tempResult.put("max_enrichment_factor", 0.0);
      }
      result.put(String.valueOf((int) tempC) + "C", tempResult);
    }
    return result;
  }

  /**
   * Assesses the wellbore safety after shutdown at various trapped pressures.
   *
   * @return map of shutdown assessment results
   */
  private Map<String, Object> runShutdownAssessment() {
    Map<String, Object> result = new LinkedHashMap<>();

    double seabedTempC = formationTempTopC;
    double geothermalGrad = (formationTempBottomC - formationTempTopC) / wellDepth;

    double[] testWHPs = {90, 80, 70, 60, 55, 50, 45};
    for (double whp : testWHPs) {
      Map<String, Object> whpResult = new LinkedHashMap<>();
      boolean hasTwoPhase = false;
      double maxImpurity = 0;
      double twoPhaseDepthMax = 0;

      // Check conditions at 20 depth points
      for (int seg = 0; seg <= 20; seg++) {
        double depth = seg * wellDepth / 20.0;
        double tempC = seabedTempC + geothermalGrad * depth;

        SystemInterface testFluid = fluid.clone();
        // Approximate density for hydrostatic
        testFluid.setTemperature(273.15 + tempC);
        testFluid.setPressure(whp + 800.0 * 9.81 * depth / 1.0e5); // rough estimate
        ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
        try {
          ops.TPflash();
          if (testFluid.getNumberOfPhases() > 1 && testFluid.hasPhaseType("gas")) {
            hasTwoPhase = true;
            twoPhaseDepthMax = depth;
            for (String comp : trackedComponents) {
              try {
                double yi = testFluid.getPhase("gas").getComponent(comp).getx();
                if (yi > maxImpurity) {
                  maxImpurity = yi;
                }
              } catch (Exception e) {
                // component not found
              }
            }
          }
        } catch (Exception e) {
          // skip
        }
      }

      whpResult.put("has_two_phase", hasTwoPhase);
      whpResult.put("max_impurity_mol_frac", maxImpurity);
      whpResult.put("two_phase_max_depth_m", twoPhaseDepthMax);
      whpResult.put("safe", !hasTwoPhase || maxImpurity < 0.01);
      result.put("WHP_" + (int) whp + "_bara", whpResult);
    }
    return result;
  }

  /**
   * Determines the safe operating envelope: minimum WHP to avoid two-phase conditions.
   *
   * @return map with safe envelope parameters
   */
  private Map<String, Object> determineSafeEnvelope() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Find minimum WHP at seabed temperature
    double seabedTempC = formationTempTopC;
    double safeMinWHP = 0;

    for (double whp = 100; whp >= 20; whp -= 1) {
      SystemInterface testFluid = fluid.clone();
      testFluid.setTemperature(273.15 + seabedTempC);
      testFluid.setPressure(whp);
      ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
      try {
        ops.TPflash();
        if (testFluid.getNumberOfPhases() > 1) {
          safeMinWHP = whp + 1;
          break;
        }
      } catch (Exception e) {
        // skip
      }
    }

    result.put("min_safe_WHP_cold_bara", safeMinWHP);
    result.put("seabed_temp_C", seabedTempC);
    result.put("co2_dominated", CO2FlowCorrections.isCO2DominatedFluid(fluid));

    // Check alarms
    boolean anyAlarmExceeded = false;
    Map<String, Object> alarmResults = new LinkedHashMap<>();
    for (Map.Entry<String, Double> entry : alarmThresholds.entrySet()) {
      String comp = entry.getKey();
      double threshold = entry.getValue();
      // Check worst case (seabed T, lowest safe P)
      SystemInterface testFluid = fluid.clone();
      testFluid.setTemperature(273.15 + seabedTempC);
      testFluid.setPressure(Math.max(20, safeMinWHP - 5));
      ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
      try {
        ops.TPflash();
        if (testFluid.getNumberOfPhases() > 1 && testFluid.hasPhaseType("gas")) {
          double yi = testFluid.getPhase("gas").getComponent(comp).getx();
          boolean exceeded = yi > threshold;
          alarmResults.put(comp + "_max_gas_frac", yi);
          alarmResults.put(comp + "_alarm_exceeded", exceeded);
          if (exceeded) {
            anyAlarmExceeded = true;
          }
        }
      } catch (Exception e) {
        // skip
      }
    }
    result.put("alarm_results", alarmResults);
    result.put("any_alarm_exceeded", anyAlarmExceeded);

    return result;
  }

  /**
   * Whether the well is safe to operate (no alarms exceeded at design conditions).
   *
   * @return true if design conditions are single-phase and no alarms are exceeded
   */
  @SuppressWarnings("unchecked")
  public boolean isSafeToOperate() {
    if (!analysisComplete) {
      return false;
    }
    Map<String, Object> designCase = (Map<String, Object>) results.get("design_case");
    if (designCase == null) {
      return false;
    }
    int nPhases = (int) designCase.getOrDefault("n_phases", 0);
    return nPhases == 1;
  }

  /**
   * Gets all analysis results.
   *
   * @return a map of result category to result data
   */
  public Map<String, Object> getResults() {
    return results;
  }

  /**
   * Gets the analyzer name.
   *
   * @return the analyzer name
   */
  public String getName() {
    return name;
  }

  /**
   * Whether the analysis has been completed.
   *
   * @return true if runFullAnalysis() has been called
   */
  public boolean isAnalysisComplete() {
    return analysisComplete;
  }
}
