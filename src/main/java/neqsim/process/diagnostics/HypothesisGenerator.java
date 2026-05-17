package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.diagnostics.Hypothesis.ExpectedBehavior;
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
 * Generates ranked root-cause hypotheses from equipment type, symptom, and reliability data.
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
    Hypothesis.Builder builder =
        Hypothesis.builder().name(hypothesis.getName()).description(hypothesis.getDescription())
            .category(hypothesis.getCategory()).failureMode(hypothesis.getFailureMode())
            .priorProbability(hypothesis.getPriorProbability());
    for (Hypothesis.ExpectedSignal signal : hypothesis.getExpectedSignals()) {
      builder.addExpectedSignal(signal.getParameterPattern(), signal.getExpectedBehavior(),
          signal.getWeight(), signal.getRationale());
    }
    for (String action : hypothesis.getRecommendedActions()) {
      builder.addAction(action);
    }
    builders.add(builder);
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

    // Build hypotheses from fresh copies to avoid state accumulation
    List<Hypothesis> hypotheses = new ArrayList<>();
    for (Hypothesis.Builder b : builders) {
      hypotheses.add(b.copy().build());
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

      // Match hypotheses to OREDA modes by failure mode name and apply the matched prior.
      for (Hypothesis h : hypotheses) {
        if (h.getFailureMode() != null && !h.getFailureMode().isEmpty()) {
          Double prob = modeProbabilities.get(h.getFailureMode().toLowerCase());
          if (prob == null) {
            prob = findFuzzyModeProbability(modeProbabilities, h.getFailureMode());
          }
          if (prob != null && prob > 0) {
            h.setPriorProbability(prob.doubleValue());
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
   * Finds a fuzzy OREDA mode probability when exact naming differs from the hypothesis library.
   *
   * <p>
   * Uses a three-tier matching strategy: (1) substring containment, (2) token overlap with at least
   * 50% match, (3) Levenshtein edit distance within 3 characters. Returns the first confident match
   * found.
   * </p>
   *
   * @param modeProbabilities normalized failure mode probability map
   * @param failureMode hypothesis failure mode
   * @return matched probability, or null if no reliable match is found
   */
  private Double findFuzzyModeProbability(Map<String, Double> modeProbabilities,
      String failureMode) {
    String normalizedFailureMode = normalize(failureMode);

    // Tier 1: substring containment (existing behavior)
    for (Map.Entry<String, Double> entry : modeProbabilities.entrySet()) {
      String normalizedMode = normalize(entry.getKey());
      if (normalizedMode.contains(normalizedFailureMode)
          || normalizedFailureMode.contains(normalizedMode)) {
        return entry.getValue();
      }
    }

    // Tier 2: token overlap >= 50%
    String[] queryTokens = failureMode.toLowerCase().split("[^a-z0-9]+");
    Double bestTokenMatch = null;
    double bestTokenScore = 0.0;
    for (Map.Entry<String, Double> entry : modeProbabilities.entrySet()) {
      String[] modeTokens = entry.getKey().split("[^a-z0-9]+");
      int matchCount = 0;
      for (String qt : queryTokens) {
        if (qt.isEmpty()) {
          continue;
        }
        for (String mt : modeTokens) {
          if (mt.isEmpty()) {
            continue;
          }
          if (qt.equals(mt) || qt.contains(mt) || mt.contains(qt)) {
            matchCount++;
            break;
          }
        }
      }
      int maxTokens = Math.max(queryTokens.length, modeTokens.length);
      if (maxTokens > 0) {
        double score = (double) matchCount / maxTokens;
        if (score >= 0.5 && score > bestTokenScore) {
          bestTokenScore = score;
          bestTokenMatch = entry.getValue();
        }
      }
    }
    if (bestTokenMatch != null) {
      return bestTokenMatch;
    }

    // Tier 3: Levenshtein edit distance <= 3
    Double bestEditMatch = null;
    int bestEditDistance = Integer.MAX_VALUE;
    for (Map.Entry<String, Double> entry : modeProbabilities.entrySet()) {
      String normalizedMode = normalize(entry.getKey());
      int distance = levenshteinDistance(normalizedFailureMode, normalizedMode);
      if (distance <= 3 && distance < bestEditDistance) {
        bestEditDistance = distance;
        bestEditMatch = entry.getValue();
      }
    }
    return bestEditMatch;
  }

  /**
   * Computes the Levenshtein edit distance between two strings.
   *
   * @param a first string
   * @param b second string
   * @return minimum single-character edits (insert, delete, replace) needed
   */
  private int levenshteinDistance(String a, String b) {
    int lenA = a.length();
    int lenB = b.length();
    if (lenA == 0) {
      return lenB;
    }
    if (lenB == 0) {
      return lenA;
    }
    // Use single-row DP for memory efficiency
    int[] prev = new int[lenB + 1];
    for (int j = 0; j <= lenB; j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= lenA; i++) {
      int[] curr = new int[lenB + 1];
      curr[0] = i;
      for (int j = 1; j <= lenB; j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      prev = curr;
    }
    return prev[lenB];
  }

  /**
   * Normalizes a phrase for fuzzy failure-mode matching.
   *
   * @param text text to normalize
   * @return lower-case alphanumeric text
   */
  private String normalize(String text) {
    return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "");
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
    putBuilders("Compressor", Symptom.HIGH_VIBRATION, Arrays.asList(
        Hypothesis.builder().name("Bearing degradation")
            .description("Bearing wear causing increased vibration")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Bearing failure")
            .priorProbability(0.30)
            .addExpectedSignal("vibration|bearingvibration|velocity", ExpectedBehavior.INCREASE,
                3.0, "bearing defects normally lift vibration before trip")
            .addExpectedSignal("bearingtemperature|bearingtemp", ExpectedBehavior.INCREASE, 2.0,
                "frictional heat rises as bearing condition degrades")
            .addExpectedSignal("lubeoilpressure|oilpressure", ExpectedBehavior.LOW_LIMIT, 1.5,
                "poor lubrication accelerates bearing wear")
            .addAction("Inspect bearings").addAction("Check vibration spectrum for bearing tones"),
        Hypothesis.builder().name("Rotor imbalance")
            .description("Mass imbalance on impeller or shaft due to erosion or deposit")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Rotor imbalance")
            .priorProbability(0.25)
            .addExpectedSignal("vibration|1x|shaftvibration", ExpectedBehavior.INCREASE, 3.0,
                "rotor imbalance produces rising synchronous vibration")
            .addExpectedSignal("power|current", ExpectedBehavior.INCREASE, 1.0,
                "imbalance can increase mechanical losses")
            .addAction("Balance rotor").addAction("Check for deposits or erosion"),
        Hypothesis.builder().name("Liquid ingestion")
            .description("Liquid carryover from upstream scrubber entering compressor")
            .category(Hypothesis.Category.PROCESS).failureMode("Liquid ingestion")
            .priorProbability(0.20)
            .addExpectedSignal("vibration|flowinstability", ExpectedBehavior.STEP_CHANGE, 2.5,
                "liquid slugs create abrupt vibration and flow disturbances")
            .addExpectedSignal("scrubberlevel|separatorlevel|level", ExpectedBehavior.HIGH_LIMIT,
                3.0, "upstream high level is the strongest liquid-ingestion precursor")
            .addExpectedSignal("liquidcarryover|mist|demisterdp", ExpectedBehavior.INCREASE, 2.0,
                "carryover indicators should rise before ingestion")
            .addAction("Check scrubber level").addAction("Inspect inlet scrubber internals"),
        Hypothesis.builder().name("Misalignment")
            .description("Shaft misalignment between driver and compressor")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Misalignment")
            .priorProbability(0.15)
            .addExpectedSignal("vibration|2x|axialvibration", ExpectedBehavior.INCREASE, 2.5,
                "misalignment normally appears in axial and harmonic vibration")
            .addExpectedSignal("bearingtemperature|couplingtemperature", ExpectedBehavior.INCREASE,
                1.5, "misalignment adds bearing and coupling heat")
            .addAction("Check coupling alignment").addAction("Perform laser alignment")));

    // HIGH_TEMPERATURE
    putBuilders("Compressor", Symptom.HIGH_TEMPERATURE, Arrays.asList(
        Hypothesis.builder().name("Seal degradation")
            .description("Internal seal leakage causing recirculation and overheating")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Seal failure")
            .priorProbability(0.30)
            .addExpectedSignal("sealflow|sealgas|differentialpressure", ExpectedBehavior.INCREASE,
                2.5, "seal leakage changes seal-gas flow or differential pressure")
            .addExpectedSignal("dischargetemperature|outlettemperature|temperature",
                ExpectedBehavior.INCREASE, 2.0,
                "internal recirculation raises discharge temperature")
            .addExpectedSignal("efficiency|polytropicefficiency", ExpectedBehavior.DECREASE, 2.0,
                "internal leakage reduces effective compression efficiency")
            .addAction("Inspect seals").addAction("Monitor seal gas flow"),
        Hypothesis.builder().name("Fouled intercooler")
            .description("Reduced cooling due to fouled intercooler")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
            .addExpectedSignal("coolerapproach|approachtemperature", ExpectedBehavior.INCREASE, 3.0,
                "fouling raises approach temperature")
            .addExpectedSignal("coolingwaterflow|cwflow", ExpectedBehavior.LOW_LIMIT, 1.5,
                "low cooling-medium flow creates the same thermal symptom")
            .addExpectedSignal("dischargetemperature|outlettemperature|temperature",
                ExpectedBehavior.INCREASE, 2.0,
                "reduced cooling raises compressor outlet temperature")
            .addAction("Clean intercooler").addAction("Check cooling water flow"),
        Hypothesis.builder().name("Operating above design pressure ratio")
            .description(
                "Compression ratio exceeds design, causing excessive discharge temperature")
            .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
            .addExpectedSignal("dischargepressure|outletpressure|pressureratio",
                ExpectedBehavior.INCREASE, 3.0,
                "higher pressure ratio directly raises discharge temperature")
            .addExpectedSignal("dischargetemperature|outlettemperature|temperature",
                ExpectedBehavior.INCREASE, 2.0, "compression temperature rises with pressure ratio")
            .addAction("Check suction/discharge pressure")
            .addAction("Verify operating point on performance map"),
        Hypothesis.builder().name("Changed gas composition")
            .description("Heavier gas composition increases compression temperature")
            .category(Hypothesis.Category.PROCESS).priorProbability(0.15)
            .addExpectedSignal("molecularweight|heavies|composition", ExpectedBehavior.INCREASE,
                2.0, "heavier gas shifts compressor operating point")
            .addExpectedSignal("dischargetemperature|outlettemperature|temperature",
                ExpectedBehavior.INCREASE, 1.5,
                "changed gas properties alter discharge temperature")
            .addAction("Sample gas composition").addAction("Check upstream process changes")));

    // LOW_EFFICIENCY
    putBuilders("Compressor", Symptom.LOW_EFFICIENCY, Arrays.asList(
        Hypothesis.builder().name("Internal seal leakage")
            .description("Internal recirculation reduces effective throughput")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Seal failure")
            .priorProbability(0.30)
            .addExpectedSignal("efficiency|polytropicefficiency", ExpectedBehavior.DECREASE, 3.0,
                "internal leakage lowers apparent efficiency")
            .addExpectedSignal("power|current", ExpectedBehavior.INCREASE, 1.5,
                "more power is required for the same useful head")
            .addExpectedSignal("sealflow|sealgas|differentialpressure", ExpectedBehavior.INCREASE,
                2.0, "seal leakage should be visible in seal-system signals")
            .addAction("Monitor seal gas differential pressure"),
        Hypothesis.builder().name("Fouled impeller")
            .description("Deposits on impeller reduce aerodynamic performance")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
            .addExpectedSignal("efficiency|polytropicefficiency", ExpectedBehavior.DECREASE, 3.0,
                "deposits reduce aerodynamic efficiency")
            .addExpectedSignal("flow|throughput", ExpectedBehavior.DECREASE, 1.5,
                "fouling reduces effective flow capacity")
            .addExpectedSignal("vibration", ExpectedBehavior.INCREASE, 1.0,
                "uneven deposits can also increase vibration")
            .addAction("Inspect impeller").addAction("Check for polymer/salt deposits"),
        Hypothesis.builder().name("Eroded impeller")
            .description("Impeller erosion from liquid or particles reduces head")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Erosion").priorProbability(0.20)
            .addExpectedSignal("efficiency|polytropicefficiency", ExpectedBehavior.DECREASE, 2.5,
                "erosion reduces head and efficiency")
            .addExpectedSignal("dischargepressure|head", ExpectedBehavior.DECREASE, 2.0,
                "eroded blades produce less head at the same speed")
            .addAction("Inspect impeller for erosion patterns")));

    // SURGE_EVENT
    putBuilders("Compressor", Symptom.SURGE_EVENT,
        Arrays.asList(
            Hypothesis.builder().name("Low suction flow")
                .description("Flow reduced below surge limit due to upstream restriction")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.35)
                .addExpectedSignal("suctionflow|inletflow|flow", ExpectedBehavior.DECREASE, 3.0,
                    "surge risk rises as flow approaches the surge line")
                .addExpectedSignal("antisurgevalve|recyclevalve|valveopening",
                    ExpectedBehavior.INCREASE, 2.0, "anti-surge valve should respond by opening")
                .addExpectedSignal("pressurepulsation|vibration", ExpectedBehavior.STEP_CHANGE, 2.0,
                    "surge produces oscillatory pressure and vibration")
                .addAction("Check anti-surge valve position")
                .addAction("Verify upstream equipment operation"),
            Hypothesis.builder().name("Anti-surge valve malfunction")
                .description("Anti-surge control valve fails to open or respond")
                .category(Hypothesis.Category.CONTROL).failureMode("Control valve failure")
                .priorProbability(0.30).addAction("Test anti-surge valve stroke")
                .addExpectedSignal("antisurgevalve|recyclevalve|valveopening",
                    ExpectedBehavior.LOW_LIMIT, 3.0,
                    "failed anti-surge valve does not open when required")
                .addExpectedSignal("suctionflow|flow", ExpectedBehavior.DECREASE, 1.5,
                    "low flow remains uncorrected")
                .addAction("Check valve positioner"),
            Hypothesis.builder().name("Discharge pressure spike")
                .description("Downstream restriction causing backpressure increase")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addExpectedSignal("dischargepressure|outletpressure", ExpectedBehavior.INCREASE,
                    3.0, "downstream restriction raises discharge pressure")
                .addExpectedSignal("flow|suctionflow", ExpectedBehavior.DECREASE, 1.5,
                    "higher backpressure moves the compressor toward surge")
                .addAction("Check downstream equipment and valves")));

    // TRIP
    putBuilders("Compressor", Symptom.TRIP,
        Arrays.asList(
            Hypothesis.builder().name("High vibration shutdown")
                .description("Vibration exceeded trip setpoint")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Bearing failure")
                .priorProbability(0.25)
                .addExpectedSignal("vibration|bearingvibration", ExpectedBehavior.HIGH_LIMIT, 3.0,
                    "trip sequence should show vibration crossing shutdown threshold")
                .addAction("Check vibration trend before trip"),
            Hypothesis.builder().name("High discharge temperature")
                .description("Temperature exceeded trip setpoint")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addExpectedSignal("dischargetemperature|outlettemperature|temperature",
                    ExpectedBehavior.HIGH_LIMIT, 3.0,
                    "temperature trip should exceed high-high threshold")
                .addAction("Check temperature trend"),
            Hypothesis.builder().name("Surge trip")
                .description("Multiple surge events triggered protective shutdown")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.20)
                .addExpectedSignal("surgecounter|pressurepulsation|vibration",
                    ExpectedBehavior.STEP_CHANGE, 3.0,
                    "surge trip should have oscillatory precursor signals")
                .addAction("Review anti-surge log"),
            Hypothesis.builder().name("Lube oil system failure")
                .description("Loss of lubricating oil pressure or flow")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Lube oil failure")
                .priorProbability(0.15)
                .addExpectedSignal("lubeoilpressure|oilpressure", ExpectedBehavior.LOW_LIMIT, 3.0,
                    "lube oil failure should show low oil pressure or flow")
                .addExpectedSignal("bearingtemperature|bearingtemp", ExpectedBehavior.INCREASE, 1.5,
                    "poor lubrication increases bearing temperature")
                .addAction("Check lube oil pump and filters")));
  }

  /**
   * Registers pump-specific hypotheses.
   */
  private void registerPumpHypotheses() {
    putBuilders("Pump", Symptom.HIGH_VIBRATION, Arrays.asList(
        Hypothesis.builder().name("Cavitation")
            .description("Insufficient NPSH causing vapor formation in impeller eye")
            .category(Hypothesis.Category.PROCESS).failureMode("Cavitation").priorProbability(0.35)
            .addExpectedSignal("suctionpressure|npsh", ExpectedBehavior.LOW_LIMIT, 3.0,
                "pump cavitation requires low suction head or low NPSH margin")
            .addExpectedSignal("vibration|noise", ExpectedBehavior.INCREASE, 2.5,
                "cavitation produces vibration and noise")
            .addExpectedSignal("flow|dischargepressure", ExpectedBehavior.DECREASE, 1.5,
                "cavitation reduces delivered flow or head")
            .addAction("Check NPSH available vs required").addAction("Check suction strainer"),
        Hypothesis.builder().name("Bearing wear")
            .description("Worn bearings causing increased vibration")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Bearing failure")
            .priorProbability(0.25)
            .addExpectedSignal("vibration|bearingvibration", ExpectedBehavior.INCREASE, 3.0,
                "bearing wear causes rising vibration")
            .addExpectedSignal("bearingtemperature|bearingtemp", ExpectedBehavior.INCREASE, 2.0,
                "bearing friction increases temperature")
            .addAction("Inspect bearings"),
        Hypothesis.builder().name("Impeller imbalance").description("Damaged or eroded impeller")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Rotor imbalance")
            .priorProbability(0.20)
            .addExpectedSignal("vibration|1x", ExpectedBehavior.INCREASE, 2.5,
                "impeller imbalance raises synchronous vibration")
            .addAction("Inspect impeller for damage")));

    putBuilders("Pump", Symptom.LOW_EFFICIENCY, Arrays.asList(
        Hypothesis.builder().name("Impeller wear")
            .description("Wear ring clearance increased, reducing efficiency")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Wear").priorProbability(0.35)
            .addExpectedSignal("efficiency|pumpEfficiency", ExpectedBehavior.DECREASE, 3.0,
                "wear ring leakage lowers pump efficiency")
            .addExpectedSignal("flow|dischargepressure|head", ExpectedBehavior.DECREASE, 2.0,
                "impeller wear reduces delivered head")
            .addAction("Measure wear ring clearance"),
        Hypothesis.builder().name("Internal recirculation")
            .description("Excessive clearance causing internal bypass flow")
            .category(Hypothesis.Category.MECHANICAL).priorProbability(0.25)
            .addExpectedSignal("efficiency|flow", ExpectedBehavior.DECREASE, 2.5,
                "internal recirculation reduces useful flow and efficiency")
            .addExpectedSignal("power|current", ExpectedBehavior.INCREASE, 1.0,
                "recirculation can increase power for the same duty")
            .addAction("Check impeller clearances")));

    putBuilders("Pump", Symptom.TRIP,
        Arrays.asList(
            Hypothesis.builder().name("Low suction pressure")
                .description("NPSH too low, protective trip activated")
                .category(Hypothesis.Category.PROCESS).priorProbability(0.30)
                .addExpectedSignal("suctionpressure|npsh", ExpectedBehavior.LOW_LIMIT, 3.0,
                    "low suction pressure trips pumps on cavitation protection")
                .addAction("Check upstream vessel level and pressure"),
            Hypothesis.builder().name("Motor overload").description("Current exceeded motor rating")
                .category(Hypothesis.Category.MECHANICAL).priorProbability(0.25)
                .addExpectedSignal("current|power", ExpectedBehavior.HIGH_LIMIT, 3.0,
                    "motor overload must show high current or power")
                .addAction("Check pump curve operating point")));
  }

  /**
   * Registers separator-specific hypotheses.
   */
  private void registerSeparatorHypotheses() {
    putBuilders("Separator", Symptom.LIQUID_CARRYOVER, Arrays.asList(
        Hypothesis.builder().name("High liquid level")
            .description("Liquid level above normal, approaching demister")
            .category(Hypothesis.Category.CONTROL).priorProbability(0.30)
            .addExpectedSignal("level|liquidlevel", ExpectedBehavior.HIGH_LIMIT, 3.0,
                "high separator level increases carryover risk")
            .addExpectedSignal("liquidcarryover|downstreamliquid", ExpectedBehavior.INCREASE, 2.0,
                "carryover indicators should rise")
            .addAction("Check level controller and valve"),
        Hypothesis.builder().name("Demister fouling")
            .description("Fouled demister pad reducing separation efficiency")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
            .addExpectedSignal("demisterdp|differentialpressure", ExpectedBehavior.INCREASE, 3.0,
                "fouled demisters raise differential pressure")
            .addExpectedSignal("liquidcarryover|mist", ExpectedBehavior.INCREASE, 2.5,
                "fouled demister lowers mist removal efficiency")
            .addAction("Inspect demister pad"),
        Hypothesis.builder().name("Feed rate exceedance")
            .description("Actual gas velocity exceeds Souders-Brown design limit")
            .category(Hypothesis.Category.PROCESS).priorProbability(0.25)
            .addExpectedSignal("gasflow|feedflow|velocity", ExpectedBehavior.HIGH_LIMIT, 3.0,
                "excess gas velocity overloads separator capacity")
            .addExpectedSignal("liquidcarryover|mist", ExpectedBehavior.INCREASE, 2.0,
                "carryover increases when gas load exceeds design")
            .addAction("Compare actual vs design gas velocity"),
        Hypothesis.builder().name("Inlet device damage")
            .description("Inlet vane or cyclone damaged, poor primary separation")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Internal damage")
            .priorProbability(0.15)
            .addExpectedSignal("liquidcarryover|slugging", ExpectedBehavior.INCREASE, 2.5,
                "damaged inlet internals reduce primary separation")
            .addAction("Inspect inlet device during shutdown")));

    putBuilders("Separator", Symptom.PRESSURE_DEVIATION, Arrays.asList(
        Hypothesis.builder().name("Outlet restriction")
            .description("Downstream valve or equipment partially blocked")
            .category(Hypothesis.Category.PROCESS).priorProbability(0.35)
            .addExpectedSignal("pressure|vesselpressure", ExpectedBehavior.INCREASE, 2.5,
                "outlet restriction raises vessel pressure")
            .addExpectedSignal("flow|outletflow", ExpectedBehavior.DECREASE, 2.0,
                "restriction reduces outlet flow")
            .addAction("Check downstream valve positions"),
        Hypothesis.builder().name("Pressure control valve malfunction")
            .description("PCV not maintaining setpoint").category(Hypothesis.Category.CONTROL)
            .failureMode("Control valve failure").priorProbability(0.30)
            .addExpectedSignal("pcvposition|valveopening", ExpectedBehavior.ANY_CHANGE, 2.0,
                "PCV malfunction appears as abnormal position or response")
            .addExpectedSignal("pressure|vesselpressure", ExpectedBehavior.ANY_CHANGE, 2.5,
                "pressure deviates when PCV control is lost")
            .addAction("Check PCV operation and positioner")));

    putBuilders("Separator", Symptom.FOULING, Arrays.asList(
        Hypothesis.builder().name("Wax deposition")
            .description("Wax precipitation in vessel or internals")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.30)
            .addExpectedSignal("temperature", ExpectedBehavior.LOW_LIMIT, 2.0,
                "wax deposition is triggered below wax appearance temperature")
            .addExpectedSignal("differentialpressure|dp", ExpectedBehavior.INCREASE, 2.5,
                "wax deposits increase pressure drop")
            .addAction("Check if temperature below WAT"),
        Hypothesis.builder().name("Scale formation").description("Mineral scale buildup in vessel")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.25)
            .addExpectedSignal("watercut|chloride|scalingindex", ExpectedBehavior.INCREASE, 2.0,
                "scaling risk follows produced-water chemistry")
            .addExpectedSignal("differentialpressure|dp", ExpectedBehavior.INCREASE, 2.0,
                "scale buildup increases hydraulic resistance")
            .addAction("Check water chemistry and scale inhibitor")));
  }

  /**
   * Registers heat-exchanger-specific hypotheses.
   */
  private void registerHeatExchangerHypotheses() {
    putBuilders("HeatExchanger", Symptom.HIGH_TEMPERATURE, Arrays.asList(
        Hypothesis.builder().name("Fouled tubes")
            .description("Reduced heat transfer due to tube-side or shell-side fouling")
            .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.35)
            .addExpectedSignal("approachtemperature|outlettemperature|temperature",
                ExpectedBehavior.INCREASE, 3.0, "fouling raises approach or outlet temperature")
            .addExpectedSignal("ua|heattransfercoefficient", ExpectedBehavior.DECREASE, 3.0,
                "fouling reduces UA")
            .addExpectedSignal("differentialpressure|dp", ExpectedBehavior.INCREASE, 1.5,
                "deposits often increase pressure drop")
            .addAction("Check approach temperature trend").addAction("Schedule cleaning"),
        Hypothesis.builder().name("Loss of cooling medium")
            .description("Cooling water or air flow reduced or lost")
            .category(Hypothesis.Category.EXTERNAL).failureMode("Utility failure")
            .priorProbability(0.30)
            .addExpectedSignal("coolingwaterflow|cwflow|utilityflow", ExpectedBehavior.LOW_LIMIT,
                3.0, "utility loss should show reduced cooling-medium flow")
            .addExpectedSignal("outlettemperature|temperature", ExpectedBehavior.INCREASE, 2.5,
                "lost cooling raises process outlet temperature")
            .addAction("Check cooling water supply pressure and flow"),
        Hypothesis.builder().name("Tube leak")
            .description("Internal tube leak causing bypass of heat transfer surface")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Tube leak")
            .priorProbability(0.15)
            .addExpectedSignal("pressure|differentialpressure", ExpectedBehavior.ANY_CHANGE, 2.0,
                "tube leaks disturb pressure balance between sides")
            .addExpectedSignal("contamination|conductivity|hydrocarbon", ExpectedBehavior.INCREASE,
                2.5, "cross-contamination confirms tube leakage")
            .addAction("Check for cross-contamination between fluids")));

    putBuilders("HeatExchanger", Symptom.LOW_EFFICIENCY,
        Arrays.asList(
            Hypothesis.builder().name("Fouled heat transfer surfaces")
                .description("Declining UA due to fouling buildup")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.40)
                .addExpectedSignal("ua|efficiency|duty", ExpectedBehavior.DECREASE, 3.0,
                    "fouling lowers heat-transfer performance")
                .addExpectedSignal("approachtemperature", ExpectedBehavior.INCREASE, 2.5,
                    "approach temperature rises as fouling increases")
                .addAction("Calculate fouling factor from current data"),
            Hypothesis.builder().name("Baffle damage")
                .description("Damaged baffles causing shell-side bypass")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Internal damage")
                .priorProbability(0.20)
                .addExpectedSignal("ua|efficiency", ExpectedBehavior.DECREASE, 2.5,
                    "shell-side bypass reduces effective heat-transfer area")
                .addExpectedSignal("differentialpressure|dp", ExpectedBehavior.DECREASE, 1.5,
                    "baffle bypass can reduce shell-side pressure drop")
                .addAction("Inspect during next turnaround")));

    putBuilders("HeatExchanger", Symptom.PRESSURE_DEVIATION,
        Arrays.asList(
            Hypothesis.builder().name("Tube-side plugging")
                .description("Partial blockage of tubes increasing pressure drop")
                .category(Hypothesis.Category.PROCESS).failureMode("Fouling").priorProbability(0.35)
                .addExpectedSignal("differentialpressure|dp", ExpectedBehavior.INCREASE, 3.0,
                    "tube plugging increases pressure drop")
                .addExpectedSignal("flow", ExpectedBehavior.DECREASE, 1.5,
                    "plugging reduces flow for the same driving pressure")
                .addAction("Check delta-P trend across exchanger"),
            Hypothesis.builder().name("Tube rupture")
                .description("Tube failure causing pressure equalization between sides")
                .category(Hypothesis.Category.MECHANICAL).failureMode("Tube leak")
                .priorProbability(0.25)
                .addExpectedSignal("pressure|differentialpressure", ExpectedBehavior.STEP_CHANGE,
                    3.0, "tube rupture causes sudden pressure disturbance")
                .addExpectedSignal("contamination|conductivity", ExpectedBehavior.INCREASE, 2.0,
                    "fluid mixing indicates rupture")
                .addAction("Pressure test")));
  }

  /**
   * Registers valve-specific hypotheses.
   */
  private void registerValveHypotheses() {
    putBuilders("Valve", Symptom.FLOW_DEVIATION, Arrays.asList(
        Hypothesis.builder().name("Valve trim erosion")
            .description("Eroded trim changing Cv characteristic")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Erosion").priorProbability(0.30)
            .addExpectedSignal("flow|cv", ExpectedBehavior.INCREASE, 2.5,
                "trim erosion increases effective Cv")
            .addExpectedSignal("position|valveopening", ExpectedBehavior.ANY_CHANGE, 1.5,
                "observed flow deviates from expected position")
            .addAction("Inspect valve trim"),
        Hypothesis.builder().name("Positioner calibration drift")
            .description("Valve positioner out of calibration")
            .category(Hypothesis.Category.CONTROL).failureMode("Instrument failure")
            .priorProbability(0.30)
            .addExpectedSignal("position|valveopening", ExpectedBehavior.ANY_CHANGE, 3.0,
                "positioner drift shows command-position mismatch")
            .addExpectedSignal("flow", ExpectedBehavior.ANY_CHANGE, 2.0,
                "flow deviates from requested opening")
            .addAction("Recalibrate positioner"),
        Hypothesis.builder().name("Actuator sticking")
            .description("Actuator friction preventing smooth movement")
            .category(Hypothesis.Category.MECHANICAL).failureMode("Actuator failure")
            .priorProbability(0.20)
            .addExpectedSignal("position|valveopening", ExpectedBehavior.STEP_CHANGE, 3.0,
                "stiction creates stick-slip position movement")
            .addExpectedSignal("instrumentair|airsupply", ExpectedBehavior.LOW_LIMIT, 1.5,
                "low air supply can cause actuator sticking")
            .addAction("Check actuator air supply and diaphragm")));

    putBuilders("Valve", Symptom.ABNORMAL_NOISE, Arrays.asList(
        Hypothesis.builder().name("Cavitation")
            .description("Liquid flashing across valve causing cavitation noise")
            .category(Hypothesis.Category.PROCESS).priorProbability(0.40)
            .addExpectedSignal("noise|vibration", ExpectedBehavior.INCREASE, 2.5,
                "valve cavitation is noisy and vibratory")
            .addExpectedSignal("downstreampressure|pressure", ExpectedBehavior.LOW_LIMIT, 2.0,
                "cavitation occurs when local pressure drops below vapor pressure")
            .addAction("Check if downstream pressure below vapor pressure"),
        Hypothesis.builder().name("Flashing")
            .description("Two-phase flow through valve generating noise")
            .category(Hypothesis.Category.PROCESS).priorProbability(0.30)
            .addExpectedSignal("vaporfraction|twophase|temperature", ExpectedBehavior.ANY_CHANGE,
                2.0, "flashing produces two-phase flow after the valve")
            .addExpectedSignal("noise", ExpectedBehavior.INCREASE, 1.5,
                "flashing generates elevated acoustic noise")
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
          .addExpectedSignal("upstream|feed|flow|pressure|temperature", ExpectedBehavior.ANY_CHANGE,
              2.0, "upstream changes should be visible in feed conditions")
          .addAction("Review upstream equipment operation"));
      builders.add(Hypothesis.builder().name("Instrument error")
          .description("Faulty sensor giving false reading").category(Hypothesis.Category.CONTROL)
          .failureMode("Instrument failure").priorProbability(0.15)
          .addExpectedSignal("instrument|sensor|transmitter", ExpectedBehavior.ANY_CHANGE, 2.0,
              "instrument errors require tag disagreement or abnormal sensor behavior")
          .addAction("Cross-check with redundant instruments"));
      builders.add(Hypothesis.builder().name("External utility disturbance")
          .description("Loss or degradation of utility supply")
          .category(Hypothesis.Category.EXTERNAL).priorProbability(0.10)
          .addExpectedSignal("utility|cooling|power|air|steam", ExpectedBehavior.ANY_CHANGE, 2.0,
              "utility disturbance should be reflected in support-system tags")
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
