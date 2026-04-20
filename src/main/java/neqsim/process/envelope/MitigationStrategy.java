package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Library of pre-built mitigation playbooks for common process threat scenarios.
 *
 * <p>
 * A {@code MitigationStrategy} maps a threat type to one or more recommended
 * {@link MitigationAction} objects. The Operating Envelope Agent consults this library when margins
 * are degrading, looking up the appropriate playbook for the detected threat type.
 * </p>
 *
 * <p>
 * Built-in strategies cover the most common process threats:
 * </p>
 * <ul>
 * <li>HYDRATE_RISK — MEG injection, temperature increase, depressurization</li>
 * <li>COMPRESSOR_SURGE — anti-surge valve, speed reduction, recycle</li>
 * <li>SEPARATOR_HIGH_LEVEL — drain valve, inlet throttle, downstream rate increase</li>
 * <li>SEPARATOR_LOW_LEVEL — reduce throughput, check dump valve</li>
 * <li>COOLER_FOULING — reduce duty demand, schedule cleaning</li>
 * <li>COMPOSITION_DRIFT — blending, diversion, feed adjustment</li>
 * <li>HIGH_PRESSURE — vent, reduce inlet flow, open downstream valve</li>
 * <li>HIGH_TEMPERATURE — increase cooling, reduce duty</li>
 * <li>VALVE_SATURATION — check controller, switch to manual</li>
 * </ul>
 *
 * <p>
 * Custom strategies can be registered via {@link #registerStrategy(String, List)}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MitigationStrategy implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Pre-defined threat types with built-in playbooks.
   */
  public static final String HYDRATE_RISK = "HYDRATE_RISK";
  /** Compressor approaching surge line. */
  public static final String COMPRESSOR_SURGE = "COMPRESSOR_SURGE";
  /** Separator liquid level approaching high limit. */
  public static final String SEPARATOR_HIGH_LEVEL = "SEPARATOR_HIGH_LEVEL";
  /** Separator liquid level approaching low limit. */
  public static final String SEPARATOR_LOW_LEVEL = "SEPARATOR_LOW_LEVEL";
  /** Heat exchanger fouling reducing performance. */
  public static final String COOLER_FOULING = "COOLER_FOULING";
  /** Feed gas composition drift from design basis. */
  public static final String COMPOSITION_DRIFT = "COMPOSITION_DRIFT";
  /** Equipment pressure approaching design limit. */
  public static final String HIGH_PRESSURE = "HIGH_PRESSURE";
  /** Equipment temperature approaching design limit. */
  public static final String HIGH_TEMPERATURE = "HIGH_TEMPERATURE";
  /** Control valve near fully open or fully closed. */
  public static final String VALVE_SATURATION = "VALVE_SATURATION";

  private final Map<String, List<MitigationAction>> strategies;

  /**
   * Creates a MitigationStrategy with all built-in playbooks pre-loaded.
   */
  public MitigationStrategy() {
    strategies = new HashMap<String, List<MitigationAction>>();
    loadBuiltInStrategies();
  }

  /**
   * Loads all built-in mitigation playbooks.
   */
  private void loadBuiltInStrategies() {
    // Hydrate risk playbook
    List<MitigationAction> hydrateActions = new ArrayList<MitigationAction>();
    hydrateActions.add(new MitigationAction("Increase MEG/MeOH injection rate",
        "Chemical Injection", "inhibitorFlowRate", 1.5, "multiplier",
        MitigationAction.Priority.IMMEDIATE, "Shifts hydrate curve by 5-15 C depending on dosage",
        MitigationAction.Category.CHEMICAL_INJECTION));
    hydrateActions.add(new MitigationAction("Increase upstream heater outlet temperature",
        "Inlet Heater", "outletTemperature", 5.0, "C_increase", MitigationAction.Priority.SOON,
        "Increases subcooling margin by raising gas temperature"));
    hydrateActions.add(new MitigationAction("Reduce operating pressure if possible",
        "Pressure Controller", "setpoint", -5.0, "bar_decrease", MitigationAction.Priority.SOON,
        "Lower pressure shifts hydrate curve to higher temperature"));
    strategies.put(HYDRATE_RISK, hydrateActions);

    // Compressor surge playbook
    List<MitigationAction> surgeActions = new ArrayList<MitigationAction>();
    surgeActions.add(new MitigationAction("Open anti-surge recycle valve", "Anti-Surge Valve",
        "opening", 15.0, "%", MitigationAction.Priority.IMMEDIATE,
        "Increases flow through compressor, moves away from surge"));
    surgeActions.add(new MitigationAction("Reduce compressor speed", "Compressor", "speed", -5.0,
        "%_decrease", MitigationAction.Priority.SOON,
        "Shifts operating point away from surge at lower head"));
    surgeActions.add(
        new MitigationAction("Increase suction throttle valve opening", "Suction Valve", "opening",
            5.0, "%_increase", MitigationAction.Priority.SOON, "Increases suction flow volume"));
    strategies.put(COMPRESSOR_SURGE, surgeActions);

    // Separator high level
    List<MitigationAction> highLevelActions = new ArrayList<MitigationAction>();
    highLevelActions.add(new MitigationAction("Increase liquid drain/dump valve opening",
        "Dump Valve", "opening", 10.0, "%_increase", MitigationAction.Priority.IMMEDIATE,
        "Increases liquid outlet rate to reduce level"));
    highLevelActions
        .add(new MitigationAction("Throttle inlet flow if possible", "Inlet Valve", "opening", -5.0,
            "%_decrease", MitigationAction.Priority.SOON, "Reduces liquid ingress rate"));
    strategies.put(SEPARATOR_HIGH_LEVEL, highLevelActions);

    // Separator low level
    List<MitigationAction> lowLevelActions = new ArrayList<MitigationAction>();
    lowLevelActions.add(new MitigationAction("Reduce dump valve opening / increase level setpoint",
        "Dump Valve", "opening", -10.0, "%_decrease", MitigationAction.Priority.IMMEDIATE,
        "Reduces liquid drain rate to build level"));
    lowLevelActions.add(new MitigationAction("Check dump valve for passing or stuck-open condition",
        "Dump Valve", "inspection", 0.0, "", MitigationAction.Priority.SOON,
        "Diagnose potential valve malfunction", MitigationAction.Category.OPERATOR_INTERVENTION));
    strategies.put(SEPARATOR_LOW_LEVEL, lowLevelActions);

    // Cooler fouling
    List<MitigationAction> foulingActions = new ArrayList<MitigationAction>();
    foulingActions.add(new MitigationAction("Reduce heat duty demand if possible", "Cooler",
        "outletTemperature", 2.0, "C_increase", MitigationAction.Priority.SOON,
        "Relaxes duty requirement on fouled exchanger"));
    foulingActions
        .add(new MitigationAction("Schedule heat exchanger cleaning", "Cooler", "maintenance", 0.0,
            "", MitigationAction.Priority.MONITOR, "Restore original heat transfer coefficient",
            MitigationAction.Category.OPERATOR_INTERVENTION));
    strategies.put(COOLER_FOULING, foulingActions);

    // Composition drift
    List<MitigationAction> compDriftActions = new ArrayList<MitigationAction>();
    compDriftActions.add(new MitigationAction(
        "Adjust feed blending ratio to restore design composition", "Feed System", "blendRatio",
        0.0, "ratio", MitigationAction.Priority.SOON, "Brings composition closer to design basis",
        MitigationAction.Category.COMPOSITION_MANAGEMENT));
    compDriftActions.add(new MitigationAction("Review process setpoints for new composition",
        "Process", "review", 0.0, "", MitigationAction.Priority.MONITOR,
        "Ensure all safety boundaries are rechecked", MitigationAction.Category.MONITORING));
    strategies.put(COMPOSITION_DRIFT, compDriftActions);

    // High pressure
    List<MitigationAction> highPressActions = new ArrayList<MitigationAction>();
    highPressActions.add(new MitigationAction("Reduce inlet flow rate", "Inlet Valve", "opening",
        -10.0, "%_decrease", MitigationAction.Priority.IMMEDIATE, "Reduces pressure buildup"));
    highPressActions.add(new MitigationAction("Open downstream valve to increase flow-through",
        "Downstream Valve", "opening", 5.0, "%_increase", MitigationAction.Priority.SOON,
        "Increases outlet flow to relieve pressure"));
    strategies.put(HIGH_PRESSURE, highPressActions);

    // High temperature
    List<MitigationAction> highTempActions = new ArrayList<MitigationAction>();
    highTempActions.add(new MitigationAction("Increase cooling medium flow or fan speed", "Cooler",
        "coolingMediumFlow", 10.0, "%_increase", MitigationAction.Priority.IMMEDIATE,
        "Increases heat removal rate"));
    highTempActions
        .add(new MitigationAction("Reduce heat input or throughput", "Process", "throughput", -5.0,
            "%_decrease", MitigationAction.Priority.SOON, "Reduces heat generation"));
    strategies.put(HIGH_TEMPERATURE, highTempActions);

    // Valve saturation
    List<MitigationAction> valveSatActions = new ArrayList<MitigationAction>();
    valveSatActions
        .add(new MitigationAction("Check if controller is in auto and functioning correctly",
            "Controller", "inspection", 0.0, "", MitigationAction.Priority.IMMEDIATE,
            "Diagnose controller malfunction", MitigationAction.Category.OPERATOR_INTERVENTION));
    valveSatActions.add(new MitigationAction("Consider switching to manual control temporarily",
        "Controller", "mode", 0.0, "", MitigationAction.Priority.SOON, "Prevents further wind-up",
        MitigationAction.Category.OPERATOR_INTERVENTION));
    strategies.put(VALVE_SATURATION, valveSatActions);
  }

  /**
   * Looks up the mitigation playbook for a given threat type.
   *
   * @param threatType the threat type identifier (e.g., HYDRATE_RISK)
   * @return list of recommended actions, or empty list if no playbook exists
   */
  public List<MitigationAction> getPlaybook(String threatType) {
    List<MitigationAction> actions = strategies.get(threatType);
    if (actions == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(actions);
  }

  /**
   * Maps an operating margin type and direction to a threat type.
   *
   * @param marginType the margin type
   * @param direction the direction being approached
   * @return threat type string, or null if no mapping exists
   */
  public String mapToThreatType(OperatingMargin.MarginType marginType,
      OperatingMargin.Direction direction) {
    switch (marginType) {
      case HYDRATE:
        return HYDRATE_RISK;
      case SURGE:
        return COMPRESSOR_SURGE;
      case LEVEL:
        return direction == OperatingMargin.Direction.HIGH ? SEPARATOR_HIGH_LEVEL
            : SEPARATOR_LOW_LEVEL;
      case PRESSURE:
        return HIGH_PRESSURE;
      case TEMPERATURE:
        return direction == OperatingMargin.Direction.HIGH ? HIGH_TEMPERATURE : null;
      case VALVE_OPENING:
        return VALVE_SATURATION;
      case HC_DEW_POINT:
      case WATER_DEW_POINT:
      case COMPOSITION:
        return COMPOSITION_DRIFT;
      case DUTY:
        return COOLER_FOULING;
      default:
        return null;
    }
  }

  /**
   * Returns actions for a margin that is approaching its limit.
   *
   * <p>
   * This is a convenience method that maps the margin to a threat type and retrieves the
   * corresponding playbook.
   * </p>
   *
   * @param margin the operating margin that is degrading
   * @return recommended mitigation actions
   */
  public List<MitigationAction> getActionsForMargin(OperatingMargin margin) {
    String threatType = mapToThreatType(margin.getMarginType(), margin.getDirection());
    if (threatType == null) {
      return Collections.emptyList();
    }
    return getPlaybook(threatType);
  }

  /**
   * Registers a custom mitigation strategy for a new or existing threat type.
   *
   * @param threatType the threat type identifier
   * @param actions list of mitigation actions in priority order
   */
  public void registerStrategy(String threatType, List<MitigationAction> actions) {
    strategies.put(threatType, new ArrayList<MitigationAction>(actions));
  }

  /**
   * Returns all registered threat type identifiers.
   *
   * @return list of threat type strings
   */
  public List<String> getRegisteredThreatTypes() {
    return new ArrayList<String>(strategies.keySet());
  }

  /**
   * Returns the total number of registered strategies.
   *
   * @return strategy count
   */
  public int getStrategyCount() {
    return strategies.size();
  }
}
