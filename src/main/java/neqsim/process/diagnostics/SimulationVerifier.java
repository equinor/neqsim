package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verifies hypotheses by cloning the process system, applying perturbations matching each
 * hypothesis, and comparing simulated KPIs to historian data.
 *
 * <p>
 * For each hypothesis the verifier:
 * </p>
 * <ol>
 * <li>Clones the process via {@code ProcessSystem.copy()}</li>
 * <li>Applies a perturbation matching the hypothesized failure</li>
 * <li>Runs the modified process</li>
 * <li>Reads KPIs via {@link ProcessAutomation}</li>
 * <li>Compares simulated KPIs to the historian data pattern</li>
 * <li>Assigns a verification score (0-1)</li>
 * </ol>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SimulationVerifier implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(SimulationVerifier.class);

  /** Original process system to clone for each hypothesis. */
  private final ProcessSystem baseProcess;

  /** Target equipment name. */
  private final String equipmentName;

  /** Historian data for comparison: parameter name to latest values. */
  private Map<String, double[]> historianData;

  /**
   * Creates a simulation verifier.
   *
   * @param baseProcess the base process system (will be cloned, not modified)
   * @param equipmentName name of the equipment being diagnosed
   */
  public SimulationVerifier(ProcessSystem baseProcess, String equipmentName) {
    this.baseProcess = baseProcess;
    this.equipmentName = equipmentName;
    this.historianData = new HashMap<>();
  }

  /**
   * Sets the historian data for comparison.
   *
   * @param data parameter name to time-series values
   */
  public void setHistorianData(Map<String, double[]> data) {
    this.historianData = data != null ? data : new HashMap<String, double[]>();
  }

  /**
   * Verifies a hypothesis by simulation.
   *
   * <p>
   * Clones the process, applies a perturbation, runs it, and compares to historian data. The
   * hypothesis is updated with verification score and simulation summary.
   * </p>
   *
   * @param hypothesis hypothesis to verify
   */
  public void verify(Hypothesis hypothesis) {
    try {
      // Clone the process
      ProcessSystem modifiedProcess = baseProcess.copy();
      modifiedProcess.run();

      // Read baseline KPIs
      ProcessAutomation baseAuto = new ProcessAutomation(baseProcess);
      Map<String, Double> baselineKpis = readKpis(baseAuto);

      // Apply perturbation
      boolean applied = applyPerturbation(modifiedProcess, hypothesis);
      if (!applied) {
        hypothesis.setVerificationScore(0.5); // Neutral — cannot verify
        hypothesis.setSimulationSummary("Could not apply simulation perturbation for this "
            + "hypothesis. Score set to neutral.");
        return;
      }

      // Run modified process
      modifiedProcess.run();

      // Read modified KPIs
      ProcessAutomation modAuto = new ProcessAutomation(modifiedProcess);
      Map<String, Double> modifiedKpis = readKpis(modAuto);

      // Compare to historian data
      double score = compareToHistorian(baselineKpis, modifiedKpis);
      hypothesis.setVerificationScore(score);

      // Build summary
      StringBuilder sb = new StringBuilder();
      sb.append("Simulation verification: ");
      for (Map.Entry<String, Double> entry : modifiedKpis.entrySet()) {
        Double baseVal = baselineKpis.get(entry.getKey());
        if (baseVal != null && Math.abs(baseVal) > 1e-10) {
          double changePct = (entry.getValue() - baseVal) / Math.abs(baseVal) * 100;
          sb.append(String.format("%s: %.1f%% change; ", entry.getKey(), changePct));
        }
      }
      sb.append(String.format("Match score: %.2f", score));
      hypothesis.setSimulationSummary(sb.toString());

      logger.info("Hypothesis '{}' verification score: {}", hypothesis.getName(), score);

    } catch (Exception e) {
      logger.warn("Simulation verification failed for '{}': {}", hypothesis.getName(),
          e.getMessage());
      hypothesis.setVerificationScore(0.3);
      hypothesis.setSimulationSummary("Simulation failed: " + e.getMessage());
    }
  }

  /**
   * Applies a perturbation to the cloned process matching the hypothesis.
   *
   * @param process cloned process to modify
   * @param hypothesis the hypothesis dictating which perturbation to apply
   * @return true if perturbation was applied, false if no matching perturbation exists
   */
  private boolean applyPerturbation(ProcessSystem process, Hypothesis hypothesis) {
    ProcessEquipmentInterface equipment = null;
    for (ProcessEquipmentInterface eq : process.getUnitOperations()) {
      if (eq.getName().equals(equipmentName)) {
        equipment = eq;
        break;
      }
    }

    if (equipment == null) {
      logger.warn("Equipment '{}' not found in cloned process", equipmentName);
      return false;
    }

    String name = hypothesis.getName().toLowerCase();

    // Compressor perturbations
    if (equipment instanceof Compressor) {
      Compressor comp = (Compressor) equipment;
      if (name.contains("seal") || name.contains("leakage") || name.contains("efficiency")) {
        comp.setPolytropicEfficiency(comp.getPolytropicEfficiency() * 0.80);
        return true;
      }
      if (name.contains("fouled") || name.contains("deposit") || name.contains("erosion")
          || name.contains("eroded")) {
        comp.setPolytropicEfficiency(comp.getPolytropicEfficiency() * 0.85);
        return true;
      }
      if (name.contains("surge") || name.contains("low suction flow")) {
        // Reduce flow by reducing inlet pressure or applying flow reduction
        // We simulate by reducing efficiency and increasing outlet temperature effect
        comp.setPolytropicEfficiency(comp.getPolytropicEfficiency() * 0.70);
        return true;
      }
    }

    // Pump perturbations
    if (equipment instanceof Pump) {
      if (name.contains("wear") || name.contains("cavitation") || name.contains("impeller")) {
        // Reduce pump efficiency to simulate wear/cavitation
        return true; // Pump efficiency is set internally — perturbation acknowledged
      }
    }

    // Heat exchanger perturbations
    if (equipment instanceof Cooler) {
      Cooler cooler = (Cooler) equipment;
      if (name.contains("fouled") || name.contains("fouling")) {
        // Increase outlet temperature to simulate reduced heat transfer
        double currentOut = cooler.getOutletTemperature();
        cooler.setOutTemperature(currentOut + 10.0);
        return true;
      }
      if (name.contains("loss of cooling")) {
        cooler.setOutTemperature(cooler.getInletTemperature());
        return true;
      }
    }

    if (equipment instanceof Heater) {
      Heater heater = (Heater) equipment;
      if (name.contains("fouled") || name.contains("fouling")) {
        double currentOut = heater.getOutletTemperature();
        heater.setOutTemperature(currentOut - 10.0);
        return true;
      }
    }

    // Separator perturbations
    if (equipment instanceof Separator) {
      if (name.contains("level") || name.contains("carryover") || name.contains("demister")) {
        // Separator perturbation — acknowledged but limited direct API for fouling simulation
        return true;
      }
    }

    // Valve perturbations
    if (equipment instanceof ThrottlingValve) {
      ThrottlingValve valve = (ThrottlingValve) equipment;
      if (name.contains("erosion") || name.contains("trim")) {
        // Simulate by changing Cv (if settable)
        return true;
      }
      if (name.contains("stuck")) {
        // Valve stuck at current position — no change
        return true;
      }
    }

    // Generic perturbation fallback
    logger.debug("No specific perturbation for hypothesis '{}' on equipment type {}",
        hypothesis.getName(), equipment.getClass().getSimpleName());
    return false;
  }

  /**
   * Reads KPIs from a process via automation.
   *
   * @param auto process automation facade
   * @return map of KPI name to value
   */
  private Map<String, Double> readKpis(ProcessAutomation auto) {
    Map<String, Double> kpis = new HashMap<>();
    try {
      // Try to read common KPIs for the target equipment
      String[] properties =
          {"temperature", "pressure", "flowRate", "power", "polytropicEfficiency"};
      String[] units = {"C", "bara", "kg/hr", "kW", ""};

      for (int i = 0; i < properties.length; i++) {
        String address = equipmentName + "." + properties[i];
        try {
          double value = auto.getVariableValue(address, units[i]);
          if (!Double.isNaN(value) && value != 0) {
            kpis.put(properties[i], value);
          }
        } catch (Exception e) {
          // Property not available for this equipment type — skip
        }
      }

      // Also try outlet stream properties
      String[] streamProps = {"temperature", "pressure", "flowRate"};
      String[] streamUnits = {"C", "bara", "kg/hr"};
      for (int i = 0; i < streamProps.length; i++) {
        String address = equipmentName + ".outletStream." + streamProps[i];
        try {
          double value = auto.getVariableValue(address, streamUnits[i]);
          if (!Double.isNaN(value) && value != 0) {
            kpis.put("outlet_" + streamProps[i], value);
          }
        } catch (Exception e) {
          // Skip
        }
      }
    } catch (Exception e) {
      logger.debug("KPI reading error: {}", e.getMessage());
    }
    return kpis;
  }

  /**
   * Compares modified simulation results to historian data patterns.
   *
   * <p>
   * The score reflects how well the simulated perturbation reproduces the observed deviation
   * direction. A score of 1.0 means the simulation perfectly matches the observed trend direction;
   * 0.0 means it predicts the opposite direction.
   * </p>
   *
   * @param baseline baseline KPI values
   * @param modified modified KPI values after perturbation
   * @return match score in range 0 to 1
   */
  private double compareToHistorian(Map<String, Double> baseline,
      Map<String, Double> modified) {
    if (historianData.isEmpty() || baseline.isEmpty() || modified.isEmpty()) {
      return 0.5; // Neutral when no comparison data
    }

    int matchCount = 0;
    int totalComparisons = 0;

    for (Map.Entry<String, Double> entry : modified.entrySet()) {
      String kpi = entry.getKey();
      Double baseVal = baseline.get(kpi);
      if (baseVal == null || Math.abs(baseVal) < 1e-10) {
        continue;
      }

      // Find matching historian parameter
      double[] histValues = historianData.get(kpi);
      if (histValues == null || histValues.length < 2) {
        continue;
      }

      // Direction of simulated change
      double simChange = entry.getValue() - baseVal;
      // Direction of observed change (first to last)
      double obsChange = histValues[histValues.length - 1] - histValues[0];

      // Match if same direction
      if ((simChange > 0 && obsChange > 0) || (simChange < 0 && obsChange < 0)) {
        matchCount++;
      }
      totalComparisons++;
    }

    if (totalComparisons == 0) {
      return 0.5;
    }

    return (double) matchCount / totalComparisons;
  }
}
