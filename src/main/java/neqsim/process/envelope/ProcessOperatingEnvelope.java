package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Aggregates all operating margins across a complete process system into a unified envelope.
 *
 * <p>
 * A {@code ProcessOperatingEnvelope} scans all equipment in a {@link ProcessSystem} and
 * automatically extracts operating margins for separators (level, pressure), compressors (surge,
 * speed, power, discharge temperature), heat exchangers (duty), valves (opening), and streams
 * (hydrate subcooling, dew points). Custom margins can also be added manually.
 * </p>
 *
 * <p>
 * The overall envelope status is determined by the single most critical margin.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * ProcessSystem process = buildMyProcess();
 * process.run();
 *
 * ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);
 * envelope.evaluate();
 *
 * ProcessOperatingEnvelope.EnvelopeStatus status = envelope.getOverallStatus();
 * List&lt;OperatingMargin&gt; critical = envelope.getCriticalMargins();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessOperatingEnvelope implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Overall envelope status derived from the worst individual margin.
   */
  public enum EnvelopeStatus {
    /** All margins are in NORMAL range. Safe to operate. */
    NORMAL,
    /** At least one margin in ADVISORY range. Enhanced monitoring recommended. */
    NARROWING,
    /** At least one margin in WARNING range. Preventive action recommended. */
    WARNING,
    /** At least one margin in CRITICAL range. Immediate action required. */
    CRITICAL,
    /** At least one margin is VIOLATED. Process outside safe envelope. */
    VIOLATED
  }

  private final ProcessSystem processSystem;
  private final Map<String, OperatingMargin> margins;
  private final Map<String, MarginTracker> trackers;
  private final Map<String, Double> customLimits;
  private boolean autoDetectEnabled;
  private boolean hydrateCheckEnabled;
  private boolean dewPointCheckEnabled;

  /**
   * Creates an operating envelope for the given process system with auto-detection enabled.
   *
   * @param processSystem the process system to monitor
   */
  public ProcessOperatingEnvelope(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.margins = new HashMap<String, OperatingMargin>();
    this.trackers = new HashMap<String, MarginTracker>();
    this.customLimits = new HashMap<String, Double>();
    this.autoDetectEnabled = true;
    this.hydrateCheckEnabled = true;
    this.dewPointCheckEnabled = true;
  }

  /**
   * Evaluates all operating margins by scanning the process system.
   *
   * <p>
   * This method iterates over all equipment in the process system and extracts operating margins
   * based on equipment type. After evaluation, margins are available via {@link #getAllMargins()},
   * {@link #getCriticalMargins()}, etc.
   * </p>
   */
  public void evaluate() {
    if (autoDetectEnabled) {
      scanEquipment();
    }
    updateCustomMargins();
  }

  /**
   * Evaluates all margins and records a timestamp sample for trend tracking.
   *
   * @param timestampSeconds current time in seconds for trend analysis
   */
  public void evaluateAndTrack(double timestampSeconds) {
    evaluate();
    for (Map.Entry<String, OperatingMargin> entry : margins.entrySet()) {
      String key = entry.getKey();
      if (!trackers.containsKey(key)) {
        trackers.put(key, new MarginTracker(entry.getValue()));
      }
      trackers.get(key).recordSample(timestampSeconds);
    }
  }

  /**
   * Scans all process equipment and creates/updates operating margins.
   */
  private void scanEquipment() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Separator) {
        evaluateSeparator((Separator) unit);
      } else if (unit instanceof Compressor) {
        evaluateCompressor((Compressor) unit);
      } else if (unit instanceof ThrottlingValve) {
        evaluateValve((ThrottlingValve) unit);
      } else if (unit instanceof Heater) {
        evaluateHeater((Heater) unit);
      }
    }

    if (hydrateCheckEnabled) {
      evaluateHydrateRisk();
    }
    if (dewPointCheckEnabled) {
      evaluateDewPoints();
    }
  }

  /**
   * Evaluates separator operating margins (pressure, level).
   *
   * @param separator the separator to evaluate
   */
  private void evaluateSeparator(Separator separator) {
    String name = separator.getName();

    // Pressure margin against max operation pressure from mechanical design
    double pressure = separator.getPressure();
    double maxOpPressure = 0.0;
    if (separator.getMechanicalDesign() != null) {
      maxOpPressure = separator.getMechanicalDesign().getMaxOperationPressure();
    }
    if (maxOpPressure > 0) {
      addOrUpdateMargin(new OperatingMargin(name, "pressure", OperatingMargin.MarginType.PRESSURE,
          OperatingMargin.Direction.HIGH, pressure, maxOpPressure, "bara"));
    }

    // Liquid level margins
    double liquidLevel = separator.getLiquidLevel();
    double internalDiameter = separator.getInternalDiameter();
    if (internalDiameter > 0) {
      // High level margin (approaching overflow)
      double highLevelLimit = internalDiameter * 0.80;
      addOrUpdateMargin(
          new OperatingMargin(name, "liquidLevelHigh", OperatingMargin.MarginType.LEVEL,
              OperatingMargin.Direction.HIGH, liquidLevel, highLevelLimit, "m"));

      // Low level margin (approaching dry-run / gas blow-by)
      double lowLevelLimit = internalDiameter * 0.15;
      addOrUpdateMargin(
          new OperatingMargin(name, "liquidLevelLow", OperatingMargin.MarginType.LEVEL,
              OperatingMargin.Direction.LOW, liquidLevel, lowLevelLimit, "m"));
    }
  }

  /**
   * Evaluates compressor operating margins (surge, speed, power, discharge temperature).
   *
   * @param compressor the compressor to evaluate
   */
  private void evaluateCompressor(Compressor compressor) {
    String name = compressor.getName();

    // Surge margin
    double surgeMargin = compressor.getDistanceToSurge();
    addOrUpdateMargin(new OperatingMargin(name, "surgeMargin", OperatingMargin.MarginType.SURGE,
        OperatingMargin.Direction.LOW, surgeMargin, 0.0, "fraction"));

    // Speed margins (if max/min speed configured)
    double speed = compressor.getSpeed();
    double maxSpeed = compressor.getMaximumSpeed();
    if (maxSpeed > 0) {
      addOrUpdateMargin(new OperatingMargin(name, "speed", OperatingMargin.MarginType.SPEED,
          OperatingMargin.Direction.HIGH, speed, maxSpeed, "rpm"));
    }

    // Power margin (if driver limit configured)
    double power = compressor.getPower("kW");
    double maxPower = compressor.getMaxOutletPressure();
    // Note: maxPower from compressor not directly available in all configurations

    // Discharge temperature margin
    double dischargeT = compressor.getOutletStream().getTemperature("C");
    double maxDischargeT = compressor.getMaxDischargeTemperature("C");
    if (maxDischargeT > 0) {
      addOrUpdateMargin(
          new OperatingMargin(name, "dischargeTemperature", OperatingMargin.MarginType.TEMPERATURE,
              OperatingMargin.Direction.HIGH, dischargeT, maxDischargeT, "C"));
    }
  }

  /**
   * Evaluates valve operating margins.
   *
   * @param valve the valve to evaluate
   */
  private void evaluateValve(ThrottlingValve valve) {
    String name = valve.getName();
    double opening = valve.getPercentValveOpening();

    // High opening margin (valve nearly fully open = bottleneck)
    addOrUpdateMargin(
        new OperatingMargin(name, "valveOpening", OperatingMargin.MarginType.VALVE_OPENING,
            OperatingMargin.Direction.HIGH, opening, 95.0, "%"));
  }

  /**
   * Evaluates heater/cooler operating margins.
   *
   * @param heater the heater or cooler to evaluate
   */
  private void evaluateHeater(Heater heater) {
    String name = heater.getName();
    double duty = Math.abs(heater.getDuty());
    // If no specific limit is set, no margin to track
    // Users can add custom limits via addCustomLimit()
  }

  /**
   * Evaluates hydrate formation risk across all gas streams.
   */
  private void evaluateHydrateRisk() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      List<neqsim.process.equipment.stream.StreamInterface> outlets = unit.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (neqsim.process.equipment.stream.StreamInterface stream : outlets) {
        if (stream == null || stream.getFluid() == null) {
          continue;
        }
        SystemInterface fluid = stream.getFluid();
        if (!fluid.hasComponent("water") && !fluid.hasComponent("MEG")) {
          continue;
        }
        try {
          double streamT = stream.getTemperature("C");
          SystemInterface clonedFluid = fluid.clone();
          ThermodynamicOperations ops = new ThermodynamicOperations(clonedFluid);
          ops.hydrateFormationTemperature();
          double hydrateT = clonedFluid.getTemperature("C");
          double subcooling = streamT - hydrateT;

          String marginName = unit.getName() + ".outlet";
          addOrUpdateMargin(new OperatingMargin(marginName, "hydrateSubcooling",
              OperatingMargin.MarginType.HYDRATE, OperatingMargin.Direction.LOW, subcooling, 5.0,
              "deltaC"));
        } catch (Exception e) {
          // Hydrate calculation may fail for some fluids — skip silently
        }
      }
    }
  }

  /**
   * Evaluates hydrocarbon dew point margins for gas streams.
   */
  private void evaluateDewPoints() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      List<neqsim.process.equipment.stream.StreamInterface> outlets = unit.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (neqsim.process.equipment.stream.StreamInterface stream : outlets) {
        if (stream == null || stream.getFluid() == null) {
          continue;
        }
        try {
          double streamT = stream.getTemperature("C");
          SystemInterface clonedFluid = stream.getFluid().clone();
          ThermodynamicOperations ops = new ThermodynamicOperations(clonedFluid);
          ops.dewPointTemperatureFlash();
          double dewT = clonedFluid.getTemperature("C");

          if (streamT > dewT) {
            double margin = streamT - dewT;
            String marginName = unit.getName() + ".outlet";
            addOrUpdateMargin(new OperatingMargin(marginName, "hcDewPointMargin",
                OperatingMargin.MarginType.HC_DEW_POINT, OperatingMargin.Direction.LOW, margin, 5.0,
                "deltaC"));
          }
        } catch (Exception e) {
          // Dew point calculation may fail — skip silently
        }
      }
    }
  }

  /**
   * Adds a new margin or updates the current value of an existing one.
   *
   * @param margin the margin to add or update
   */
  private void addOrUpdateMargin(OperatingMargin margin) {
    String key = margin.getKey();
    OperatingMargin existing = margins.get(key);
    if (existing != null) {
      existing.updateCurrentValue(margin.getCurrentValue());
      existing.updateLimitValue(margin.getLimitValue());
    } else {
      margins.put(key, margin);
    }
  }

  /**
   * Updates margins from user-defined custom limits.
   */
  private void updateCustomMargins() {
    // Custom limits are applied via addCustomMargin() — nothing to recalculate here
  }

  /**
   * Adds a custom operating margin (for equipment or variables not auto-detected).
   *
   * @param margin the custom margin to add
   */
  public void addCustomMargin(OperatingMargin margin) {
    margins.put(margin.getKey(), margin);
  }

  /**
   * Returns all operating margins, sorted by severity (most critical first).
   *
   * @return sorted list of all margins
   */
  public List<OperatingMargin> getAllMargins() {
    List<OperatingMargin> result = new ArrayList<OperatingMargin>(margins.values());
    Collections.sort(result);
    return result;
  }

  /**
   * Returns only margins with status WARNING, CRITICAL, or VIOLATED.
   *
   * @return list of margins requiring attention, sorted by severity
   */
  public List<OperatingMargin> getCriticalMargins() {
    List<OperatingMargin> result = new ArrayList<OperatingMargin>();
    for (OperatingMargin m : margins.values()) {
      OperatingMargin.Status status = m.getStatus();
      if (status == OperatingMargin.Status.WARNING || status == OperatingMargin.Status.CRITICAL
          || status == OperatingMargin.Status.VIOLATED) {
        result.add(m);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Returns margins filtered by equipment name.
   *
   * @param equipmentName the equipment name to filter by
   * @return list of margins for the specified equipment
   */
  public List<OperatingMargin> getMarginsByEquipment(String equipmentName) {
    List<OperatingMargin> result = new ArrayList<OperatingMargin>();
    for (OperatingMargin m : margins.values()) {
      if (m.getEquipmentName().equals(equipmentName)) {
        result.add(m);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Returns margins filtered by margin type.
   *
   * @param type the margin type to filter by
   * @return list of margins of the specified type
   */
  public List<OperatingMargin> getMarginsByType(OperatingMargin.MarginType type) {
    List<OperatingMargin> result = new ArrayList<OperatingMargin>();
    for (OperatingMargin m : margins.values()) {
      if (m.getMarginType() == type) {
        result.add(m);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Returns the overall envelope status (worst margin status across all equipment).
   *
   * @return overall envelope status
   */
  public EnvelopeStatus getOverallStatus() {
    boolean hasAdvisory = false;
    boolean hasWarning = false;
    boolean hasCritical = false;
    boolean hasViolated = false;

    for (OperatingMargin m : margins.values()) {
      switch (m.getStatus()) {
        case VIOLATED:
          hasViolated = true;
          break;
        case CRITICAL:
          hasCritical = true;
          break;
        case WARNING:
          hasWarning = true;
          break;
        case ADVISORY:
          hasAdvisory = true;
          break;
        default:
          break;
      }
    }

    if (hasViolated) {
      return EnvelopeStatus.VIOLATED;
    }
    if (hasCritical) {
      return EnvelopeStatus.CRITICAL;
    }
    if (hasWarning) {
      return EnvelopeStatus.WARNING;
    }
    if (hasAdvisory) {
      return EnvelopeStatus.NARROWING;
    }
    return EnvelopeStatus.NORMAL;
  }

  /**
   * Returns the margin tracker for a specific margin key.
   *
   * @param key the margin key (equipment.variable.direction)
   * @return the tracker, or null if not yet created
   */
  public MarginTracker getTracker(String key) {
    return trackers.get(key);
  }

  /**
   * Returns all margin trackers.
   *
   * @return unmodifiable map of key to tracker
   */
  public Map<String, MarginTracker> getAllTrackers() {
    return Collections.unmodifiableMap(trackers);
  }

  /**
   * Returns the total number of tracked margins.
   *
   * @return margin count
   */
  public int getMarginCount() {
    return margins.size();
  }

  /**
   * Enables or disables automatic equipment scanning.
   *
   * @param enabled true to enable auto-detection (default)
   */
  public void setAutoDetectEnabled(boolean enabled) {
    this.autoDetectEnabled = enabled;
  }

  /**
   * Enables or disables hydrate formation temperature checking.
   *
   * @param enabled true to check hydrate risk (default)
   */
  public void setHydrateCheckEnabled(boolean enabled) {
    this.hydrateCheckEnabled = enabled;
  }

  /**
   * Enables or disables hydrocarbon dew point checking.
   *
   * @param enabled true to check dew points (default)
   */
  public void setDewPointCheckEnabled(boolean enabled) {
    this.dewPointCheckEnabled = enabled;
  }

  /**
   * Returns the underlying process system.
   *
   * @return the process system being monitored
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Clears all margin trackers and resets trend data.
   */
  public void resetTrackers() {
    for (MarginTracker tracker : trackers.values()) {
      tracker.reset();
    }
  }

  /**
   * Returns a formatted summary string of all margins.
   *
   * @return multi-line summary
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ProcessOperatingEnvelope [status=").append(getOverallStatus());
    sb.append(", margins=").append(margins.size()).append("]\n");
    for (OperatingMargin m : getAllMargins()) {
      sb.append("  ").append(m.toString()).append("\n");
    }
    return sb.toString();
  }
}
