package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.HashMap;
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
 * Verifies root-cause hypotheses by perturbing a cloned process model.
 *
 * <p>
 * The verifier is conservative: unsupported hypotheses receive a neutral score with an explicit
 * limitation rather than a false positive verification.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.1
 */
public class SimulationVerifier implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(SimulationVerifier.class);

  /** Original process system to clone for each hypothesis. */
  private final ProcessSystem baseProcess;

  /** Target equipment name. */
  private final String equipmentName;

  /** Historian data for comparison. */
  private Map<String, double[]> historianData;

  /**
   * Creates a simulation verifier.
   *
   * @param baseProcess the base process system, cloned before perturbation
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
   * @param hypothesis hypothesis to verify
   */
  public void verify(Hypothesis hypothesis) {
    try {
      ProcessSystem modifiedProcess = baseProcess.copy();
      modifiedProcess.run();

      ProcessAutomation baseAuto = new ProcessAutomation(baseProcess);
      Map<String, Double> baselineKpis = readKpis(baseAuto);

      PerturbationResult perturbation = applyPerturbation(modifiedProcess, hypothesis);
      if (!perturbation.isApplied()) {
        hypothesis.setVerificationScore(0.5);
        hypothesis.setSimulationSummary("Simulation verification neutral: "
            + perturbation.getDescription());
        return;
      }

      modifiedProcess.run();
      ProcessAutomation modifiedAuto = new ProcessAutomation(modifiedProcess);
      Map<String, Double> modifiedKpis = readKpis(modifiedAuto);

      double score = compareToHistorian(baselineKpis, modifiedKpis);
      hypothesis.setVerificationScore(score);
      hypothesis.setSimulationSummary(buildSummary(perturbation, baselineKpis, modifiedKpis, score));
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
   * @param hypothesis hypothesis dictating which perturbation to apply
   * @return perturbation result with status and explanation
   */
  private PerturbationResult applyPerturbation(ProcessSystem process, Hypothesis hypothesis) {
    ProcessEquipmentInterface equipment = findEquipment(process);
    if (equipment == null) {
      return PerturbationResult.notApplied("equipment '" + equipmentName
          + "' was not found in the cloned process");
    }

    String failureMode = hypothesis.getFailureMode() != null
        ? hypothesis.getFailureMode().toLowerCase() : "";
    Hypothesis.Category category = hypothesis.getCategory();

    if (equipment instanceof Compressor) {
      return perturbCompressor((Compressor) equipment, failureMode, category);
    } else if (equipment instanceof Pump) {
      return perturbPump((Pump) equipment, failureMode, category);
    } else if (equipment instanceof Cooler) {
      return perturbCooler((Cooler) equipment, failureMode, category);
    } else if (equipment instanceof Heater) {
      return perturbHeater((Heater) equipment, failureMode, category);
    } else if (equipment instanceof HeatExchanger) {
      return perturbHeatExchanger((HeatExchanger) equipment, failureMode, category);
    } else if (equipment instanceof Separator) {
      return perturbSeparator((Separator) equipment, failureMode, category);
    } else if (equipment instanceof ThrottlingValve) {
      return perturbValve((ThrottlingValve) equipment, failureMode, category);
    }
    return PerturbationResult.notApplied("no supported perturbation strategy for equipment type "
        + equipment.getClass().getSimpleName());
  }

  /**
   * Finds the diagnosed equipment in a process.
   *
   * @param process process system
   * @return equipment, or null if not found
   */
  private ProcessEquipmentInterface findEquipment(ProcessSystem process) {
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment.getName().equals(equipmentName)) {
        return equipment;
      }
    }
    return null;
  }

  /**
   * Applies a compressor perturbation with graduated severity based on failure mode.
   *
   * @param compressor compressor to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbCompressor(Compressor compressor, String failureMode,
      Hypothesis.Category category) {
    double oldEfficiency = compressor.getPolytropicEfficiency();
    double factor;
    if (failureMode.contains("surge") || failureMode.contains("flow")) {
      factor = 0.70;
    } else if (failureMode.contains("seal") || failureMode.contains("leakage")) {
      factor = 0.80;
    } else if (failureMode.contains("foul") || failureMode.contains("deposit")
        || failureMode.contains("erosion") || failureMode.contains("erode")) {
      factor = 0.85;
    } else if (category == Hypothesis.Category.MECHANICAL) {
      factor = 0.85;
    } else {
      factor = 0.90;
    }
    double newEfficiency = Math.max(0.05, Math.min(0.95, oldEfficiency * factor));
    if (Math.abs(newEfficiency - oldEfficiency) < 1e-9) {
      return PerturbationResult.notApplied("compressor efficiency perturbation produced no change");
    }
    compressor.setPolytropicEfficiency(newEfficiency);
    return PerturbationResult.applied("polytropicEfficiency",
        String.format("reduced compressor polytropic efficiency from %.3f to %.3f", oldEfficiency,
            newEfficiency));
  }

  /**
   * Applies a pump perturbation.
   *
   * @param pump pump to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbPump(Pump pump, String failureMode,
      Hypothesis.Category category) {
    if (failureMode.contains("wear") || failureMode.contains("cavitation")
        || failureMode.contains("impeller") || failureMode.contains("recirculation")
        || category == Hypothesis.Category.MECHANICAL) {
      double oldEfficiency = pump.getIsentropicEfficiency();
      double newEfficiency = Math.max(0.05, Math.min(0.95, oldEfficiency * 0.75));
      pump.setIsentropicEfficiency(newEfficiency);
      return PerturbationResult.applied("isentropicEfficiency",
          String.format("reduced pump isentropic efficiency from %.3f to %.3f", oldEfficiency,
              newEfficiency));
    }
    return PerturbationResult.notApplied("pump hypothesis has no supported efficiency/head change");
  }

  /**
   * Applies a cooler perturbation.
   *
   * @param cooler cooler to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbCooler(Cooler cooler, String failureMode,
      Hypothesis.Category category) {
    double oldTemperature = cooler.getOutletTemperature();
    if (failureMode.contains("utility") || failureMode.contains("loss")
        || category == Hypothesis.Category.EXTERNAL) {
      cooler.setOutTemperature(cooler.getInletTemperature());
      return PerturbationResult.applied("outletTemperature",
          String.format("set cooler outlet temperature from %.2f K to inlet temperature %.2f K",
              oldTemperature, cooler.getInletTemperature()));
    }
    if (failureMode.contains("foul") || failureMode.contains("fouling")
        || failureMode.contains("plugging")) {
      cooler.setOutTemperature(oldTemperature + 10.0);
      return PerturbationResult.applied("outletTemperature",
          String.format("increased cooler outlet temperature from %.2f K to %.2f K", oldTemperature,
              oldTemperature + 10.0));
    }
    return PerturbationResult.notApplied("cooler hypothesis has no supported thermal perturbation");
  }

  /**
   * Applies a heater perturbation.
   *
   * @param heater heater to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbHeater(Heater heater, String failureMode,
      Hypothesis.Category category) {
    if (failureMode.contains("foul") || failureMode.contains("fouling")
        || failureMode.contains("plugging")) {
      double oldTemperature = heater.getOutletTemperature();
      double newTemperature = oldTemperature - 10.0;
      heater.setOutTemperature(newTemperature);
      return PerturbationResult.applied("outletTemperature",
          String.format("reduced heater outlet temperature from %.2f K to %.2f K", oldTemperature,
              newTemperature));
    }
    return PerturbationResult.notApplied("heater hypothesis has no supported thermal perturbation");
  }

  /**
   * Applies a heat exchanger perturbation.
   *
   * <p>
   * When the equipment is a generic HeatExchanger (not Cooler or Heater), applies fouling or
   * tube-leak perturbations by adjusting UA values or flow parameters.
   * </p>
   *
   * @param heatExchanger heat exchanger to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbHeatExchanger(HeatExchanger heatExchanger, String failureMode,
      Hypothesis.Category category) {
    if (failureMode.contains("foul") || failureMode.contains("plugging")
        || failureMode.contains("baffle")) {
      double oldUA = heatExchanger.getUAvalue();
      if (oldUA > 0.0) {
        double newUA = oldUA * 0.60;
        heatExchanger.setUAvalue(newUA);
        return PerturbationResult.applied("UAvalue",
            String.format("reduced HX UA from %.2f to %.2f (fouling simulation)", oldUA, newUA));
      }
    }
    if (failureMode.contains("tube leak") || failureMode.contains("tube rupture")
        || category == Hypothesis.Category.MECHANICAL) {
      double oldUA = heatExchanger.getUAvalue();
      if (oldUA > 0.0) {
        double newUA = oldUA * 0.50;
        heatExchanger.setUAvalue(newUA);
        return PerturbationResult.applied("UAvalue",
            String.format("reduced HX UA from %.2f to %.2f (tube failure simulation)",
                oldUA, newUA));
      }
    }
    return PerturbationResult.notApplied(
        "heat exchanger hypothesis has no supported UA/thermal perturbation");
  }

  /**
   * Applies a separator perturbation.
   *
   * @param separator separator to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbSeparator(Separator separator, String failureMode,
      Hypothesis.Category category) {
    if (failureMode.contains("foul") || failureMode.contains("damage")
        || failureMode.contains("internal") || category == Hypothesis.Category.PROCESS) {
      separator.setEfficiency(0.70);
      return PerturbationResult.applied("efficiency",
          "reduced separator efficiency to 0.70 to represent carryover/internal degradation");
    }
    if (failureMode.contains("control") || failureMode.contains("level")
        || category == Hypothesis.Category.CONTROL) {
      separator.setLiquidLevel(0.9);
      return PerturbationResult.applied("liquidLevel",
          "set separator liquid level high to represent level-control excursion");
    }
    return PerturbationResult.notApplied("separator hypothesis has no supported separation change");
  }

  /**
   * Applies a valve perturbation.
   *
   * @param valve valve to perturb
   * @param failureMode lower-case failure mode text
   * @param category hypothesis category
   * @return perturbation result
   */
  private PerturbationResult perturbValve(ThrottlingValve valve, String failureMode,
      Hypothesis.Category category) {
    if (failureMode.contains("erosion") || failureMode.contains("trim")) {
      double oldCv = valve.getCv();
      if (oldCv > 0.0) {
        valve.setCv(oldCv * 1.30);
        return PerturbationResult.applied("Cv",
            String.format("increased valve Cv from %.3f to %.3f", oldCv, oldCv * 1.30));
      }
      double oldOpening = valve.getPercentValveOpening();
      double newOpening = Math.min(100.0, oldOpening + 20.0);
      valve.setPercentValveOpening(newOpening);
      return PerturbationResult.applied("percentValveOpening",
          String.format("increased valve opening from %.1f%% to %.1f%%", oldOpening,
              newOpening));
    }
    if (failureMode.contains("actuator") || failureMode.contains("positioner")
        || failureMode.contains("sticking") || failureMode.contains("instrument")) {
      double oldOpening = valve.getPercentValveOpening();
      double newOpening = Math.max(0.0, oldOpening * 0.5);
      valve.setPercentValveOpening(newOpening);
      return PerturbationResult.applied("percentValveOpening",
          String.format("reduced valve opening from %.1f%% to %.1f%%", oldOpening, newOpening));
    }
    return PerturbationResult.notApplied("valve hypothesis has no supported Cv/opening change");
  }

  /**
   * Reads common KPIs from a process via automation.
   *
   * @param auto process automation facade
   * @return map of KPI name to value
   */
  private Map<String, Double> readKpis(ProcessAutomation auto) {
    Map<String, Double> kpis = new HashMap<>();
    String[] properties = {"temperature", "pressure", "flowRate", "power",
        "polytropicEfficiency", "isentropicEfficiency", "efficiency"};
    String[] units = {"C", "bara", "kg/hr", "kW", "", "", ""};
    for (int i = 0; i < properties.length; i++) {
      readKpi(auto, equipmentName + "." + properties[i], properties[i], units[i], kpis);
    }

    String[] streamProps = {"temperature", "pressure", "flowRate"};
    String[] streamUnits = {"C", "bara", "kg/hr"};
    for (int i = 0; i < streamProps.length; i++) {
      readKpi(auto, equipmentName + ".outletStream." + streamProps[i],
          "outlet_" + streamProps[i], streamUnits[i], kpis);
      readKpi(auto, equipmentName + ".gasOutStream." + streamProps[i],
          "gasOut_" + streamProps[i], streamUnits[i], kpis);
      readKpi(auto, equipmentName + ".liquidOutStream." + streamProps[i],
          "liquidOut_" + streamProps[i], streamUnits[i], kpis);
    }
    return kpis;
  }

  /**
   * Reads one KPI and stores it if available.
   *
   * @param auto process automation facade
   * @param address automation address
   * @param key output key
   * @param unit requested unit, or empty for native unit
   * @param kpis target KPI map
   */
  private void readKpi(ProcessAutomation auto, String address, String key, String unit,
      Map<String, Double> kpis) {
    try {
      double value = auto.getVariableValue(address, unit);
      if (!Double.isNaN(value) && Math.abs(value) > 1e-12) {
        kpis.put(key, value);
      }
    } catch (Exception e) {
      logger.debug("Skipping unavailable KPI {}: {}", address, e.getMessage());
    }
  }

  /**
   * Compares modified simulation results to historian data patterns.
   *
   * @param baseline baseline KPI values
   * @param modified modified KPI values after perturbation
   * @return match score in range 0 to 1
   */
  private double compareToHistorian(Map<String, Double> baseline, Map<String, Double> modified) {
    if (historianData.isEmpty() || baseline.isEmpty() || modified.isEmpty()) {
      return 0.5;
    }

    int matchCount = 0;
    int totalComparisons = 0;
    for (Map.Entry<String, Double> entry : modified.entrySet()) {
      String kpi = entry.getKey();
      Double baseVal = baseline.get(kpi);
      if (baseVal == null || Math.abs(baseVal) < 1e-10) {
        continue;
      }
      double[] historianValues = findHistorianValuesForKpi(kpi);
      if (historianValues == null || historianValues.length < 2) {
        continue;
      }

      double simulationChange = entry.getValue() - baseVal;
      double observedChange = historianValues[historianValues.length - 1] - historianValues[0];
      if (Math.abs(simulationChange) < 1e-12 || Math.abs(observedChange) < 1e-12) {
        continue;
      }
      if (Math.signum(simulationChange) == Math.signum(observedChange)) {
        matchCount++;
      }
      totalComparisons++;
    }
    return totalComparisons == 0 ? 0.5 : (double) matchCount / totalComparisons;
  }

  /**
   * Finds historian values matching a simulated KPI key.
   *
   * @param kpi simulated KPI key
   * @return historian values, or null if no alias matches
   */
  private double[] findHistorianValuesForKpi(String kpi) {
    double[] exact = historianData.get(kpi);
    if (exact != null) {
      return exact;
    }
    String normalizedKpi = normalize(kpi);
    for (Map.Entry<String, double[]> entry : historianData.entrySet()) {
      String normalizedTag = normalize(entry.getKey());
      if (normalizedTag.contains(normalizedKpi) || normalizedKpi.contains(normalizedTag)
          || areKnownAliases(normalizedKpi, normalizedTag)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Checks known historian aliases for common process KPIs.
   *
   * @param normalizedKpi normalized KPI name
   * @param normalizedTag normalized historian tag name
   * @return true if the names are known aliases
   */
  private boolean areKnownAliases(String normalizedKpi, String normalizedTag) {
    if (normalizedKpi.contains("temperature") && normalizedTag.contains("temp")) {
      return true;
    }
    if (normalizedKpi.contains("pressure") && normalizedTag.contains("press")) {
      return true;
    }
    if (normalizedKpi.contains("flowrate") && normalizedTag.contains("flow")) {
      return true;
    }
    if (normalizedKpi.contains("polytropicefficiency") && normalizedTag.contains("eff")) {
      return true;
    }
    if (normalizedKpi.contains("isentropicefficiency") && normalizedTag.contains("eff")) {
      return true;
    }
    return normalizedKpi.contains("power") && (normalizedTag.contains("power")
        || normalizedTag.contains("current"));
  }

  /**
   * Builds the simulation verification summary.
   *
   * @param perturbation perturbation that was applied
   * @param baseline baseline KPI values
   * @param modified modified KPI values
   * @param score verification score
   * @return summary text
   */
  private String buildSummary(PerturbationResult perturbation, Map<String, Double> baseline,
      Map<String, Double> modified, double score) {
    StringBuilder summary = new StringBuilder();
    summary.append("Applied ").append(perturbation.getChangedVariable()).append(": ")
        .append(perturbation.getDescription()).append(". KPI changes: ");
    for (Map.Entry<String, Double> entry : modified.entrySet()) {
      Double baseValue = baseline.get(entry.getKey());
      if (baseValue != null && Math.abs(baseValue) > 1e-10) {
        double changePct = (entry.getValue() - baseValue) / Math.abs(baseValue) * 100.0;
        summary.append(String.format("%s %.1f%%; ", entry.getKey(), changePct));
      }
    }
    summary.append(String.format("direction match score %.2f", score));
    return summary.toString();
  }

  /**
   * Normalizes text for alias matching.
   *
   * @param text text to normalize
   * @return lower-case alphanumeric text
   */
  private String normalize(String text) {
    return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "");
  }

  /** Result object for a simulation perturbation attempt. */
  private static class PerturbationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean applied;
    private final String changedVariable;
    private final String description;

    /**
     * Creates a perturbation result.
     *
     * @param applied true if the process was changed
     * @param changedVariable changed variable name
     * @param description human-readable description
     */
    private PerturbationResult(boolean applied, String changedVariable, String description) {
      this.applied = applied;
      this.changedVariable = changedVariable;
      this.description = description;
    }

    /**
     * Creates an applied result.
     *
     * @param changedVariable changed variable name
     * @param description human-readable description
     * @return applied perturbation result
     */
    private static PerturbationResult applied(String changedVariable, String description) {
      return new PerturbationResult(true, changedVariable, description);
    }

    /**
     * Creates a not-applied result.
     *
     * @param description limitation description
     * @return not-applied perturbation result
     */
    private static PerturbationResult notApplied(String description) {
      return new PerturbationResult(false, "none", description);
    }

    /**
     * Checks whether a perturbation was applied.
     *
     * @return true if applied
     */
    private boolean isApplied() {
      return applied;
    }

    /**
     * Gets the changed variable name.
     *
     * @return changed variable name
     */
    private String getChangedVariable() {
      return changedVariable;
    }

    /**
     * Gets the result description.
     *
     * @return result description
     */
    private String getDescription() {
      return description;
    }
  }
}
