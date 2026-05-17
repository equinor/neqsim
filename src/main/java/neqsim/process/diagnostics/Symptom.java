package neqsim.process.diagnostics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Observable symptoms that trigger root cause analysis.
 *
 * <p>
 * Each symptom maps to a set of related hypothesis categories that are likely causes. The mapping
 * is used by diagnostic hypothesis-generation logic to produce candidate root causes ranked by
 * prior probability from reliability data (IOGP/SINTEF, CCPS, IEEE 493, Lees, OREDA).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum Symptom {

  /** Equipment has tripped (emergency shutdown). */
  TRIP("Equipment trip", Arrays.asList("MECHANICAL", "CONTROL", "PROCESS", "EXTERNAL")),

  /** Abnormal vibration detected on rotating equipment. */
  HIGH_VIBRATION("High vibration", Arrays.asList("MECHANICAL", "PROCESS")),

  /** Seal leakage detected. */
  SEAL_FAILURE("Seal failure", Arrays.asList("MECHANICAL", "PROCESS")),

  /** Outlet or body temperature above normal range. */
  HIGH_TEMPERATURE("High temperature", Arrays.asList("PROCESS", "MECHANICAL", "EXTERNAL")),

  /** Efficiency below expected value. */
  LOW_EFFICIENCY("Low efficiency", Arrays.asList("MECHANICAL", "PROCESS", "CONTROL")),

  /** Pressure deviating from setpoint or design. */
  PRESSURE_DEVIATION("Pressure deviation", Arrays.asList("PROCESS", "CONTROL", "MECHANICAL")),

  /** Flow rate deviating from expected value. */
  FLOW_DEVIATION("Flow deviation", Arrays.asList("PROCESS", "CONTROL", "MECHANICAL")),

  /** Power consumption above expected value. */
  HIGH_POWER("High power consumption", Arrays.asList("MECHANICAL", "PROCESS")),

  /** Compressor surge event detected. */
  SURGE_EVENT("Surge event", Arrays.asList("PROCESS", "CONTROL")),

  /** Equipment fouling or plugging. */
  FOULING("Fouling or plugging", Arrays.asList("PROCESS", "EXTERNAL")),

  /** Abnormal noise from equipment. */
  ABNORMAL_NOISE("Abnormal noise", Arrays.asList("MECHANICAL", "PROCESS")),

  /** Liquid carryover from separator or scrubber. */
  LIQUID_CARRYOVER("Liquid carryover", Arrays.asList("PROCESS", "CONTROL"));

  /** Human-readable description. */
  private final String description;

  /** Related hypothesis categories ordered by typical relevance. */
  private final List<String> relatedCategories;

  /**
   * Constructs a symptom.
   *
   * @param description human-readable description
   * @param relatedCategories hypothesis categories related to this symptom
   */
  Symptom(String description, List<String> relatedCategories) {
    this.description = description;
    this.relatedCategories = Collections.unmodifiableList(relatedCategories);
  }

  /**
   * Gets the human-readable description.
   *
   * @return description text
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the related hypothesis categories ordered by typical relevance.
   *
   * @return unmodifiable list of category names
   */
  public List<String> getRelatedCategories() {
    return relatedCategories;
  }
}
