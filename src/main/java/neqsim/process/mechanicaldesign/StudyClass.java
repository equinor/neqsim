package neqsim.process.mechanicaldesign;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Study classification for engineering deliverables.
 *
 * <p>
 * Defines which deliverables are required for each study class according to oil &amp; gas industry
 * practice. Class A studies (FEED/Detailed Design) require the full set of engineering documents
 * while Class B (Concept/Pre-FEED) requires a reduced set.
 * </p>
 *
 * <table>
 * <caption>Study class deliverable requirements</caption>
 * <tr>
 * <th>Deliverable</th>
 * <th>Class A</th>
 * <th>Class B</th>
 * <th>Class C</th>
 * </tr>
 * <tr>
 * <td>Thermal utility summary</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * <td>No</td>
 * </tr>
 * <tr>
 * <td>Process flow diagram</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * </tr>
 * <tr>
 * <td>Alarm/trip schedule</td>
 * <td>Yes</td>
 * <td>No</td>
 * <td>No</td>
 * </tr>
 * <tr>
 * <td>Spare parts inventory</td>
 * <td>Yes</td>
 * <td>No</td>
 * <td>No</td>
 * </tr>
 * <tr>
 * <td>Fire scenario assessment</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * <td>No</td>
 * </tr>
 * <tr>
 * <td>Noise assessment</td>
 * <td>Yes</td>
 * <td>No</td>
 * <td>No</td>
 * </tr>
 * <tr>
 * <td>Instrument schedule</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * <td>No</td>
 * </tr>
 * </table>
 *
 * @author esol
 * @version 1.0
 * @see EngineeringDeliverablesPackage
 */
public enum StudyClass {

  /**
   * Class A: Full FEED / Detailed Design study. All deliverables required.
   */
  CLASS_A("Class A (FEED/Detail)", allDeliverables()),

  /**
   * Class B: Concept / Pre-FEED study. Reduced deliverable set.
   */
  CLASS_B("Class B (Concept/Pre-FEED)",
      toSet(DeliverableType.PFD, DeliverableType.THERMAL_UTILITIES, DeliverableType.FIRE_SCENARIOS,
          DeliverableType.INSTRUMENT_SCHEDULE)),

  /**
   * Class C: Screening study. Minimal deliverable set.
   */
  CLASS_C("Class C (Screening)", toSet(DeliverableType.PFD));

  private final String displayName;
  private final Set<DeliverableType> requiredDeliverables;

  /**
   * Constructor.
   *
   * @param displayName human-readable name
   * @param requiredDeliverables set of required deliverable types
   */
  StudyClass(String displayName, Set<DeliverableType> requiredDeliverables) {
    this.displayName = displayName;
    this.requiredDeliverables = Collections.unmodifiableSet(requiredDeliverables);
  }

  /**
   * Get the display name of this study class.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get the set of required deliverable types for this study class.
   *
   * @return unmodifiable set of required deliverables
   */
  public Set<DeliverableType> getRequiredDeliverables() {
    return requiredDeliverables;
  }

  /**
   * Check if a specific deliverable type is required for this study class.
   *
   * @param type the deliverable type to check
   * @return true if the deliverable is required
   */
  public boolean requires(DeliverableType type) {
    return requiredDeliverables.contains(type);
  }

  /**
   * Enumeration of engineering deliverable types.
   */
  public enum DeliverableType {
    /** Process flow diagram (Graphviz DOT export). */
    PFD("Process Flow Diagram"),

    /** Thermal utility summary (cooling water, steam, fuel gas, instrument air). */
    THERMAL_UTILITIES("Thermal Utility Summary"),

    /** Alarm and trip setpoint schedule per IEC 61511 / NORSOK I-001. */
    ALARM_TRIP_SCHEDULE("Alarm / Trip Schedule"),

    /** Spare parts inventory for rotating and static equipment. */
    SPARE_PARTS("Spare Parts Inventory"),

    /** Fire scenario assessment (jet fire, BLEVE, pool fire). */
    FIRE_SCENARIOS("Fire Scenario Assessment"),

    /** Noise assessment with atmospheric attenuation per ISO 9613. */
    NOISE_ASSESSMENT("Noise Assessment"),

    /** Instrument schedule with ISA-5.1 tags, alarm setpoints, SIL ratings, and live devices. */
    INSTRUMENT_SCHEDULE("Instrument Schedule");

    private final String displayName;

    /**
     * Constructor.
     *
     * @param displayName human-readable name
     */
    DeliverableType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Build a set of all deliverable types.
   *
   * @return set containing all deliverable types
   */
  private static Set<DeliverableType> allDeliverables() {
    return new LinkedHashSet<DeliverableType>(Arrays.asList(DeliverableType.values()));
  }

  /**
   * Convenience helper to build an unmodifiable set from varargs.
   *
   * @param types deliverable types
   * @return set of types
   */
  private static Set<DeliverableType> toSet(DeliverableType... types) {
    return new LinkedHashSet<DeliverableType>(Arrays.asList(types));
  }

  @Override
  public String toString() {
    return displayName;
  }
}
