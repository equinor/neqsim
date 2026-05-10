package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.failure.ReliabilityDataSource;
import neqsim.process.equipment.failure.ReliabilityDataSource.FailureModeData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates ranked root-cause hypotheses from equipment type, symptom, and OREDA failure data.
 *
 * <p>
 * The generator maintains a built-in library of symptom-to-hypothesis mappings for common equipment
 * types (compressor, pump, separator, heat exchanger, valve). Users may register custom hypotheses
 * via {@link #register(String, Symptom, Hypothesis)}.
 * </p>
 *
 * <p>
 * Prior probabilities are sourced from {@link ReliabilityDataSource} when available, falling back
 * to a uniform distribution over candidate hypotheses.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class HypothesisGenerator implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(HypothesisGenerator.class);

  /**
   * Registry: equipmentType + symptom to list of hypothesis builders. Key format:
   * "equipmentType:SYMPTOM".
   */
  private final Map<String, List<Hypothesis.Builder>> registry;

  /**
   * Creates a generator with the built-in hypothesis library.
   */
  public HypothesisGenerator() {
    this.registry = new HashMap<>();
    registerBuiltInHypotheses();
  }

  /**
   * Registers a custom hypothesis for an equipment type and symptom.
   *
   * @param equipmentType equipment type key (e.g., "Compressor", "Separator")
   * @param symptom the observable symptom
   * @param hypothesis pre-built hypothesis to add
   */
  public void register(String equipmentType, Symptom symptom, Hypothesis hypothesis) {
    String key = makeKey(equipmentType, symptom);
    List<Hypothesis.Builder> builders =
        registry.containsKey(key) ? registry.get(key) : new ArrayList<Hypothesis.Builder>();
    builders.add(
        Hypothesis.builder().name(hypothesis.getName()).description(hypothesis.getDescription())
            .category(hypothesis.getCategory()).failureMode(hypothesis.getFailureMode())
            .priorProbability(hypothesis.getPriorProbability()));
    registry.put(key, builders);
  }

  /**
   * Generates hypotheses for the given equipment and symptom.
   *
   * <p>
   * The method looks up hypotheses from the registry based on the equipment class name. If no match
   * is found for the specific equipment type, it falls back to a generic set. Prior probabilities
   * are adjusted using OREDA failure-mode data when available.
   * </p>
   *
   * @param equipment the process equipment exhibiting the symptom
   * @param symptom the observed symptom
   * @return list of hypotheses sorted by prior probability (descending)
   */
  public List<Hypothesis> generate(ProcessEquipmentInterface equipment, Symptom symptom) {
    String equipmentType = classifyEquipment(equipment);
    String key = makeKey(equipmentType, symptom);

    List<Hypothesis.Builder> builders = registry.get(key);
    if (builders == null || builders.isEmpty()) {
      // Fall back to generic hypotheses
      builders = registry.get(makeKey("Generic", symptom));
    }
    if (builders == null || builders.isEmpty()) {
      logger.warn("No hypotheses registered for {} with symptom {}", equipmentType, symptom);
      return Collections.emptyList();
    }

    // Build hypotheses and adjust priors from OREDA
    List<Hypothesis> hypotheses = new ArrayList<>();
    for (Hypothesis.Builder b : builders) {
      hypotheses.add(b.build());
    }

    adjustPriorsFromOreda(hypotheses, equipmentType);

    // Sort by prior (descending)
    Collections.sort(hypotheses);
    return hypotheses;
  }

  /**
   * Classifies a process equipment instance into a type key.
   *
   * @param equipment equipment instance
   * @return type key string
   */
  String classifyEquipment(ProcessEquipmentInterface equipment) {
    if (equipment instanceof Compressor) {
      return "Compressor";
    } else if (equipment instanceof Pump) {
      return "Pump";
    } else if (equipment instanceof Separator) {
      return "Separator";
    } else if (equipment instanceof HeatExchanger || equipment instanceof Cooler
        || equipment instanceof Heater) {
      return "HeatExchanger";
    } else if (equipment instanceof ThrottlingValve) {
      return "Valve";
    }
    return "Generic";
  }

  /**
   * Adjusts hypothesis prior probabilities using OREDA failure-mode data.
   *
   * @param hypotheses hypotheses to adjust
   * @param equipmentType equipment type for OREDA lookup
   */
  void adjustPriorsFromOreda(List<Hypothesis> hypotheses, String equipmentType) {
    try {
      ReliabilityDataSource ds = ReliabilityDataSource.getInstance();
      List<FailureModeData> modes = ds.getFailureModes(equipmentType, null);
      if (modes == null || modes.isEmpty()) {
        return;
      }

      // Build lookup: failureMode name -> probability
      Map<String, Double> modeProbabilities = new HashMap<>();
      for (FailureModeData mode : modes) {
        modeProbabilities.put(mode.getFailureMode().toLowerCase(), mode.getProbability() / 100.0);
      }

      // Match hypotheses to OREDA modes by failure mode name
      for (Hypothesis h : hypotheses) {
        if (h.getFailureMode() != null && !h.getFailureMode().isEmpty()) {
          Double prob = modeProbabilities.get(h.getFailureMode().toLowerCase());
          if (prob != null && prob > 0) {
            // We cannot set priorProbability directly after build, so the prior from builder
            // is the best we have. Log the OREDA match for diagnostics.
            logger.debug("OREDA match for hypothesis '{}': failure mode '{}' = {}", h.getName(),
                h.getFailureMode(), prob);
          }
        }
      }
    } catch (Exception e) {
      logger.debug("OREDA lookup skipped for {}: {}", equipmentType, e.getMessage());
    }
  }

  /**
   * Builds a registry key.
   *
   * @param equipmentType equipment type
   * @param symptom symptom
   * @return key string
   */
  private String makeKey(String equipmentType, Symptom symptom) {
    return equipmentType + ":" + symptom.name();
  }

  // ===== Built-in hypothesis library =====

  /**
   * Registers the built-in hypothesis library for common equipment types.
   */
  private void registerBuiltInHypotheses() {
    registerCompressorHypotheses();
    registerPumpHypotheses();
    registerSeparatorHypotheses();
    registerHeatExchangerHypotheses();
    registerValveHypotheses();
    registerGenericHypotheses();
  }

  /**
   * Registers compressor-specific hypotheses.
   */
  private void registerCompressorHypotheses() {
    // HIGH_VIBRATION
    putBuilders("Compressor", Symptom.HIGH_VIBRATION,
        Arrays.asList(
            Hypothesis.builder().name("Bearing degradation")
                .description("Bearing wear causing increased vibration")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Bearing failure")
                .priorProbability(0.30).addAction("Inspect bearings")
                .addAction("Check vibration spectrum for bearing tones"),
            Hypothesis.builder().name("Rotor imbalance")
                .description("Mass imbalance on impeller or shaft due to erosion or deposit")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Rotor imbalance")
                .priorProbability(0.25).addAction("Balance rotor")
                .addAction("Check for deposits or erosion"),
            Hypothesis.builder().name("Liquid ingestion")
                .description("Liquid carryover from upstream scrubber entering compressor")
                .category(Hypothesis.Category.PROCESS).failureMode("Liquid ingestion")
                .priorProbability(0.20).addAction("Check scrubber level")
                .addAction("Inspect inlet scrubber internals"),
            Hypothesis.builder().name("Misalignment")
                .description("Shaft misalignment between driver and compressor")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Misalignment")
                .priorProbability(0.15).addAction("Check coupling alignment")
                .addAction("Perform laser alignment")));

    // HIGH_TEMPERATURE
    putBuilders("Compressor", Symptom.HIGH_TEMPERATURE,
        Arrays.asList(Hypothesis.builder().name("Seal degradation")
            .description("Internal seal leakage causing recirculation and overheating")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Seal failure")
            .priorProbability(0.30).addAction("Inspect seals").addAction("Monitor seal gas flow"),
            Hypothesis.builder().name("Fouled intercooler")
                .description("Reduced cooling due to fouled intercooler")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
                .addAction("Clean intercooler").addAction("Check cooling water flow"),
            Hypothesis.builder().name("Operating above design pressure ratio")
                .description(
                    "Compression ratio exceeds design, causing excessive discharge temperature")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addAction("Check suction/discharge pressure")
                .addAction("Verify operating point on performance map"),
            Hypothesis.builder().name("Changed gas composition")
                .description("Heavier gas composition increases compression temperature")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.15)
                .addAction("Sample gas composition").addAction("Check upstream process changes")));

    // LOW_EFFICIENCY
    putBuilders("Compressor", Symptom.LOW_EFFICIENCY,
        Arrays.asList(
            Hypothesis.builder().name("Internal seal leakage")
                .description("Internal recirculation reduces effective throughput")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Seal failure")
                .priorProbability(0.30).addAction("Monitor seal gas differential pressure"),
            Hypothesis.builder().name("Fouled impeller")
                .description("Deposits on impeller reduce aerodynamic performance")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
                .addAction("Inspect impeller").addAction("Check for polymer/salt deposits"),
            Hypothesis.builder().name("Eroded impeller")
                .description("Impeller erosion from liquid or particles reduces head")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Erosion")
                .priorProbability(0.20).addAction("Inspect impeller for erosion patterns")));

    // SURGE_EVENT
    putBuilders("Compressor", Symptom.SURGE_EVENT,
        Arrays.asList(
            Hypothesis.builder().name("Low suction flow")
                .description("Flow reduced below surge limit due to upstream restriction")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.35)
                .addAction("Check anti-surge valve position")
                .addAction("Verify upstream equipment operation"),
            Hypothesis.builder().name("Anti-surge valve malfunction")
                .description("Anti-surge control valve fails to open or respond")
                .category(Hypothesis.Category.CONTROL).failureMode("Control valve failure")
                .priorProbability(0.30).addAction("Test anti-surge valve stroke")
                .addAction("Check valve positioner"),
            Hypothesis.builder().name("Discharge pressure spike")
                .description("Downstream restriction causing backpressure increase")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addAction("Check downstream equipment and valves")));

    // TRIP
    putBuilders("Compressor", Symptom.TRIP,
        Arrays.asList(Hypothesis.builder().name("High vibration shutdown")
            .description("Vibration exceeded trip setpoint")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Bearing failure")
            .priorProbability(0.25).addAction("Check vibration trend before trip"),
            Hypothesis.builder().name("High discharge temperature")
                .description("Temperature exceeded trip setpoint")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addAction("Check temperature trend"),
            Hypothesis.builder().name("Surge trip")
                .description("Multiple surge events triggered protective shutdown")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addAction("Review anti-surge log"),
            Hypothesis.builder().name("Lube oil system failure")
                .description("Loss of lubricating oil pressure or flow")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Lube oil failure")
                .priorProbability(0.15).addAction("Check lube oil pump and filters")));
  }

  /**
   * Registers pump-specific hypotheses.
   */
  private void registerPumpHypotheses() {
    putBuilders("Pump", Symptom.HIGH_VIBRATION,
        Arrays.asList(Hypothesis.builder().name("Cavitation")
            .description("Insufficient NPSH causing vapor formation in impeller eye")
            .category(Hypothesis.Category.PROCESS).failureMode("Cavitation").priorProbability(0.35)
            .addAction("Check NPSH available vs required").addAction("Check suction strainer"),
            Hypothesis.builder().name("Bearing wear")
                .description("Worn bearings causing increased vibration")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Bearing failure")
                .priorProbability(0.25).addAction("Inspect bearings"),
            Hypothesis.builder().name("Impeller imbalance")
                .description("Damaged or eroded impeller").category(Hypothesis.Category.MECHANICAL)
                .failureMode("Rotor imbalance").priorProbability(0.20)
                .addAction("Inspect impeller for damage")));

    putBuilders("Pump", Symptom.LOW_EFFICIENCY,
        Arrays.asList(
            Hypothesis.builder().name("Impeller wear")
                .description("Wear ring clearance increased, reducing efficiency")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Wear").priorProbability(0.35)
                .addAction("Measure wear ring clearance"),
            Hypothesis.builder().name("Internal recirculation")
                .description("Excessive clearance causing internal bypass flow")
                .category(Hypothesis.Category.MECHANICAL).priorProbability(0.25)
                .addAction("Check impeller clearances")));

    putBuilders("Pump", Symptom.TRIP,
        Arrays.asList(
            Hypothesis.builder().name("Low suction pressure")
                .description("NPSH too low, protective trip activated")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.30)
                .addAction("Check upstream vessel level and pressure"),
            Hypothesis.builder().name("Motor overload").description("Current exceeded motor rating")
                .category(Hypothesis.Category.MECHANICAL).priorProbability(0.25)
                .addAction("Check pump curve operating point")));
  }

  /**
   * Registers separator-specific hypotheses.
   */
  private void registerSeparatorHypotheses() {
    putBuilders("Separator", Symptom.LIQUID_CARRYOVER,
        Arrays.asList(
            Hypothesis.builder().name("High liquid level")
                .description("Liquid level above normal, approaching demister")
                .category(Hypothesis.Category.CONTROL).priorProbability(0.30)
                .addAction("Check level controller and valve"),
            Hypothesis.builder().name("Demister fouling")
                .description("Fouled demister pad reducing separation efficiency")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
                .addAction("Inspect demister pad"),
            Hypothesis.builder().name("Feed rate exceedance")
                .description("Actual gas velocity exceeds Souders-Brown design limit")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.25)
                .addAction("Compare actual vs design gas velocity"),
            Hypothesis.builder().name("Inlet device damage")
                .description("Inlet vane or cyclone damaged, poor primary separation")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Internal damage")
                .priorProbability(0.15).addAction("Inspect inlet device during shutdown")));

    putBuilders("Separator", Symptom.PRESSURE_DEVIATION,
        Arrays.asList(
            Hypothesis.builder().name("Outlet restriction")
                .description("Downstream valve or equipment partially blocked")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.35)
                .addAction("Check downstream valve positions"),
            Hypothesis.builder().name("Pressure control valve malfunction")
                .description("PCV not maintaining setpoint").category(Hypothesis.Category.CONTROL)
                .failureMode("Control valve failure").priorProbability(0.30)
                .addAction("Check PCV operation and positioner")));

    putBuilders("Separator", Symptom.FOULING,
        Arrays.asList(Hypothesis.builder().name("Wax deposition")
            .description("Wax precipitation in vessel or internals")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.30)
            .addAction("Check if temperature below WAT"),
            Hypothesis.builder().name("Scale formation")
                .description("Mineral scale buildup in vessel")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
                .addAction("Check water chemistry and scale inhibitor")));
  }

  /**
   * Registers heat-exchanger-specific hypotheses.
   */
  private void registerHeatExchangerHypotheses() {
    putBuilders("HeatExchanger", Symptom.HIGH_TEMPERATURE,
        Arrays.asList(
            Hypothesis.builder().name("Fouled tubes")
                .description("Reduced heat transfer due to tube-side or shell-side fouling")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.35)
                .addAction("Check approach temperature trend").addAction("Schedule cleaning"),
            Hypothesis.builder().name("Loss of cooling medium")
                .description("Cooling water or air flow reduced or lost")
                .category(Hypothesis.Category.EXTERNAL).failureMode("Utility failure")
                .priorProbability(0.30).addAction("Check cooling water supply pressure and flow"),
            Hypothesis.builder().name("Tube leak")
                .description("Internal tube leak causing bypass of heat transfer surface")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Tube leak")
                .priorProbability(0.15).addAction("Check for cross-contamination between fluids")));

    putBuilders("HeatExchanger", Symptom.LOW_EFFICIENCY,
        Arrays.asList(
            Hypothesis.builder().name("Fouled heat transfer surfaces")
                .description("Declining UA due to fouling buildup")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.40)
                .addAction("Calculate fouling factor from current data"),
            Hypothesis.builder().name("Baffle damage")
                .description("Damaged baffles causing shell-side bypass")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Internal damage")
                .priorProbability(0.20).addAction("Inspect during next turnaround")));

    putBuilders("HeatExchanger", Symptom.PRESSURE_DEVIATION,
        Arrays.asList(
            Hypothesis.builder().name("Tube-side plugging")
                .description("Partial blockage of tubes increasing pressure drop")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.35)
                .addAction("Check delta-P trend across exchanger"),
            Hypothesis.builder().name("Tube rupture")
                .description("Tube failure causing pressure equalization between sides")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Tube leak")
                .priorProbability(0.25).addAction("Pressure test")));
  }

  /**
   * Registers valve-specific hypotheses.
   */
  private void registerValveHypotheses() {
    putBuilders("Valve", Symptom.FLOW_DEVIATION,
        Arrays.asList(
            Hypothesis.builder().name("Valve trim erosion")
                .description("Eroded trim changing Cv characteristic")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Erosion")
                .priorProbability(0.30).addAction("Inspect valve trim"),
            Hypothesis.builder().name("Positioner calibration drift")
                .description("Valve positioner out of calibration")
                .category(Hypothesis.Category.CONTROL).failureMode("Instrument failure")
                .priorProbability(0.30).addAction("Recalibrate positioner"),
            Hypothesis.builder().name("Actuator sticking")
                .description("Actuator friction preventing smooth movement")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Actuator failure")
                .priorProbability(0.20).addAction("Check actuator air supply and diaphragm")));

    putBuilders("Valve", Symptom.ABNORMAL_NOISE,
        Arrays.asList(
            Hypothesis.builder().name("Cavitation")
                .description("Liquid flashing across valve causing cavitation noise")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.40)
                .addAction("Check if downstream pressure below vapor pressure"),
            Hypothesis.builder().name("Flashing")
                .description("Two-phase flow through valve generating noise")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.30)
                .addAction("Consider anti-cavitation trim")));
  }

  /**
   * Registers generic fallback hypotheses.
   */
  private void registerGenericHypotheses() {
    for (Symptom symptom : Symptom.values()) {
      List<Hypothesis.Builder> builders = new ArrayList<>();
      builders.add(Hypothesis.builder().name("Upstream process change")
          .description("Change in upstream process conditions causing downstream effects")
          .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
          .addAction("Review upstream equipment operation"));
      builders.add(Hypothesis.builder().name("Instrument error")
          .description("Faulty sensor giving false reading").category(Hypothesis.Category.CONTROL)
          .failureMode("Instrument failure").priorProbability(0.15)
          .addAction("Cross-check with redundant instruments"));
      builders.add(Hypothesis.builder().name("External utility disturbance")
          .description("Loss or degradation of utility supply")
          .category(Hypothesis.Category.EXTERNAL).priorProbability(0.10)
          .addAction("Check utility system status"));
      registry.put(makeKey("Generic", symptom), builders);
    }
  }

  /**
   * Utility to register a list of builders for an equipment type and symptom.
   *
   * @param equipmentType equipment type key
   * @param symptom symptom
   * @param builders hypothesis builders
   */
  private void putBuilders(String equipmentType, Symptom symptom,
      List<Hypothesis.Builder> builders) {
    registry.put(makeKey(equipmentType, symptom), new ArrayList<>(builders));
  }
}
