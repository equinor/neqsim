package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmStatusSnapshot;
import neqsim.process.alarm.ProcessAlarmManager;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorState;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.PSDValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.lifecycle.ProcessSystemState;

/**
 * Monitors a running process system for trip events during dynamic simulation.
 *
 * <p>
 * The detector watches for:
 * </p>
 * <ul>
 * <li>Compressors entering TRIPPED state</li>
 * <li>ESD/PSD valves closing (trip completed)</li>
 * <li>HIHI or LOLO alarm activations</li>
 * </ul>
 *
 * <p>
 * When a trip pattern is detected, the detector automatically:
 * </p>
 * <ul>
 * <li>Captures a {@link ProcessStateSnapshot} (last good state vs trip state)</li>
 * <li>Constructs a {@link TripEvent} with relevant alarms</li>
 * <li>Records the event in the {@link UnifiedEventTimeline}</li>
 * <li>Notifies registered {@link TripEventListener} instances</li>
 * </ul>
 *
 * <p>
 * Designed to be called at each timestep of a dynamic simulation (from
 * {@code ProcessSystem.runTransient()}) or polled periodically.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class TripEventDetector implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private final UnifiedEventTimeline timeline;
  private final List<TripEvent> detectedTrips = new ArrayList<>();
  private final transient List<TripEventListener> listeners = new ArrayList<>();

  /** Last known good state, captured periodically when system is stable. */
  private ProcessSystemState lastGoodState;
  /** Simulation time of the last good state capture. */
  private double lastGoodStateTime = 0.0;
  /** How often to refresh the last good state snapshot (seconds). */
  private double stateRefreshInterval = 30.0;
  /** Time since last state refresh. */
  private double timeSinceLastRefresh = 0.0;
  /** Previous compressor states for detecting TRIPPED transitions. */
  private final List<CompressorStateRecord> compressorStates = new ArrayList<>();
  /** Whether the detector has been initialised. */
  private boolean initialized = false;

  /**
   * Creates a trip event detector for a process system.
   *
   * @param processSystem the process system to monitor
   */
  public TripEventDetector(ProcessSystem processSystem) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem must not be null");
    this.timeline = new UnifiedEventTimeline();
  }

  /**
   * Creates a trip event detector with a shared timeline.
   *
   * @param processSystem the process system to monitor
   * @param timeline shared timeline instance
   */
  public TripEventDetector(ProcessSystem processSystem, UnifiedEventTimeline timeline) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem must not be null");
    this.timeline = Objects.requireNonNull(timeline, "timeline must not be null");
  }

  /**
   * Initialises the detector by scanning the current equipment and capturing the initial state.
   *
   * <p>
   * Should be called once before the first {@link #monitor(double, double)} call, typically at the
   * start of dynamic simulation.
   * </p>
   */
  public void initialize() {
    compressorStates.clear();
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Compressor) {
        Compressor comp = (Compressor) eq;
        compressorStates.add(new CompressorStateRecord(comp.getName(), comp.getOperatingState()));
      }
    }
    lastGoodState = ProcessSystemState.fromProcessSystem(processSystem);
    lastGoodStateTime = processSystem.getTime();
    initialized = true;
  }

  /**
   * Monitors the process system for trip events at the current timestep.
   *
   * <p>
   * Call this method from the dynamic simulation loop (e.g. at each timestep in
   * {@code runTransient}).
   * </p>
   *
   * @param currentTime current simulation time in seconds
   * @param dt timestep size in seconds
   * @return list of trip events detected during this timestep (usually empty or one element)
   */
  public List<TripEvent> monitor(double currentTime, double dt) {
    if (!initialized) {
      initialize();
    }

    List<TripEvent> newTrips = new ArrayList<>();

    // Refresh last good state periodically
    timeSinceLastRefresh += dt;
    if (timeSinceLastRefresh >= stateRefreshInterval && !hasActiveTrip()) {
      lastGoodState = ProcessSystemState.fromProcessSystem(processSystem);
      lastGoodStateTime = currentTime;
      timeSinceLastRefresh = 0.0;
    }

    // Check for compressor trips
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Compressor) {
        Compressor comp = (Compressor) eq;
        CompressorState currentState = comp.getOperatingState();
        CompressorStateRecord record = findCompressorRecord(comp.getName());
        if (record != null) {
          if (currentState == CompressorState.TRIPPED && record.previousState.isOperational()) {
            TripEvent trip = buildTripEvent(currentTime, comp.getName(), TripType.COMPRESSOR_SURGE,
                "Compressor " + comp.getName() + " tripped from " + record.previousState);
            newTrips.add(trip);
            timeline.addTrip(currentTime, comp.getName(),
                "Compressor tripped from " + record.previousState);
          }
          record.previousState = currentState;
        }
      }
    }

    // Check for ESD/PSD valve closures
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof ESDValve) {
        ESDValve esd = (ESDValve) eq;
        if (esd.hasTripCompleted() && !isAlreadyDetected(esd.getName(), currentTime)) {
          TripEvent trip = buildTripEvent(currentTime, esd.getName(), TripType.ESD_ACTIVATED,
              "ESD valve " + esd.getName() + " closure completed");
          newTrips.add(trip);
          timeline.addTrip(currentTime, esd.getName(), "ESD valve closure completed");
        }
      }
      if (eq instanceof PSDValve) {
        PSDValve psd = (PSDValve) eq;
        if (psd.getPercentValveOpening() < 1.0 && !isAlreadyDetected(psd.getName(), currentTime)) {
          TripEvent trip = buildTripEvent(currentTime, psd.getName(), TripType.HIGH_PRESSURE,
              "PSD valve " + psd.getName() + " closed");
          newTrips.add(trip);
          timeline.addTrip(currentTime, psd.getName(), "PSD valve closed");
        }
      }
    }

    // Check for HIHI/LOLO alarms
    ProcessAlarmManager alarmMgr = processSystem.getAlarmManager();
    if (alarmMgr != null) {
      List<AlarmStatusSnapshot> activeAlarms = alarmMgr.getActiveAlarms();
      for (AlarmStatusSnapshot snapshot : activeAlarms) {
        String level = snapshot.getLevel().name();
        if ("HIHI".equalsIgnoreCase(level) || "LOLO".equalsIgnoreCase(level)) {
          timeline.addAlarm(currentTime, snapshot.getSource(),
              level + " alarm: " + snapshot.getSource());
        }
      }
    }

    // Store and notify
    if (!newTrips.isEmpty()) {
      detectedTrips.addAll(newTrips);
      notifyListeners(newTrips);
    }

    return newTrips;
  }

  /**
   * Builds a TripEvent with a snapshot of the current process state.
   *
   * @param time simulation time
   * @param equipmentName tripping equipment
   * @param tripType type of trip
   * @param description description
   * @return constructed TripEvent
   */
  private TripEvent buildTripEvent(double time, String equipmentName, TripType tripType,
      String description) {
    ProcessSystemState tripState = ProcessSystemState.fromProcessSystem(processSystem);
    ProcessStateSnapshot snapshot = new ProcessStateSnapshot(lastGoodState, tripState, time);

    // Collect recent alarms from alarm manager
    List<AlarmEvent> recentAlarms = new ArrayList<>();
    ProcessAlarmManager alarmMgr = processSystem.getAlarmManager();
    if (alarmMgr != null) {
      List<AlarmEvent> history = alarmMgr.getHistory();
      for (AlarmEvent alarm : history) {
        if (alarm.getTimestamp() >= lastGoodStateTime) {
          recentAlarms.add(alarm);
        }
      }
    }

    return new TripEvent.Builder().eventId(UUID.randomUUID().toString()).timestamp(time)
        .initiatingEquipment(equipmentName).tripType(tripType).severity(TripEvent.Severity.HIGH)
        .description(description).addAlarms(recentAlarms).build();
  }

  /**
   * Returns whether there is an active (unresolved) trip in the current monitoring session.
   *
   * @return true if a trip has been detected
   */
  public boolean hasActiveTrip() {
    return !detectedTrips.isEmpty();
  }

  /**
   * Returns all detected trip events.
   *
   * @return unmodifiable list of trip events
   */
  public List<TripEvent> getDetectedTrips() {
    return Collections.unmodifiableList(detectedTrips);
  }

  /**
   * Returns the most recent trip event, or null if none.
   *
   * @return most recent TripEvent or null
   */
  public TripEvent getLastTrip() {
    if (detectedTrips.isEmpty()) {
      return null;
    }
    return detectedTrips.get(detectedTrips.size() - 1);
  }

  /**
   * Returns the unified event timeline.
   *
   * @return the timeline
   */
  public UnifiedEventTimeline getTimeline() {
    return timeline;
  }

  /**
   * Returns the last good process state snapshot.
   *
   * @return last good state, or null if not yet captured
   */
  public ProcessSystemState getLastGoodState() {
    return lastGoodState;
  }

  /**
   * Creates a process state snapshot for the most recent trip.
   *
   * @return snapshot comparing last good state to trip state, or null if no trip detected
   */
  public ProcessStateSnapshot getLatestSnapshot() {
    TripEvent lastTrip = getLastTrip();
    if (lastTrip == null) {
      return null;
    }
    ProcessSystemState tripState = ProcessSystemState.fromProcessSystem(processSystem);
    return new ProcessStateSnapshot(lastGoodState, tripState, lastTrip.getTimestamp());
  }

  /**
   * Registers a listener for trip events.
   *
   * @param listener listener to register
   */
  public void addTripEventListener(TripEventListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  /**
   * Removes a trip event listener.
   *
   * @param listener listener to remove
   */
  public void removeTripEventListener(TripEventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Sets the interval for refreshing the last good state snapshot.
   *
   * @param intervalSeconds interval in seconds (default 30)
   */
  public void setStateRefreshInterval(double intervalSeconds) {
    this.stateRefreshInterval = intervalSeconds;
  }

  /**
   * Clears all detected trips and resets the detector for a new monitoring session.
   */
  public void reset() {
    detectedTrips.clear();
    timeSinceLastRefresh = 0.0;
    initialized = false;
  }

  /**
   * Notifies all registered listeners of new trip events.
   *
   * @param newTrips list of new trip events
   */
  private void notifyListeners(List<TripEvent> newTrips) {
    for (TripEvent trip : newTrips) {
      for (TripEventListener listener : listeners) {
        try {
          listener.onTripDetected(trip);
        } catch (Exception e) {
          System.err.println("Error notifying trip listener: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Finds a compressor state record by name.
   *
   * @param name compressor name
   * @return the record, or null
   */
  private CompressorStateRecord findCompressorRecord(String name) {
    for (CompressorStateRecord record : compressorStates) {
      if (record.name.equals(name)) {
        return record;
      }
    }
    return null;
  }

  /**
   * Checks if a trip for this equipment at this time is already detected.
   *
   * @param equipmentName equipment name
   * @param time current time
   * @return true if already detected
   */
  private boolean isAlreadyDetected(String equipmentName, double time) {
    for (TripEvent trip : detectedTrips) {
      if (trip.getInitiatingEquipment().equals(equipmentName)
          && Math.abs(trip.getTimestamp() - time) < 1.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Internal record tracking the previous state of a compressor.
   */
  private static class CompressorStateRecord implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String name;
    CompressorState previousState;

    CompressorStateRecord(String name, CompressorState previousState) {
      this.name = name;
      this.previousState = previousState;
    }
  }

  /**
   * Listener interface for trip event notifications.
   *
   * @author esol
   * @version 1.0
   */
  public interface TripEventListener {
    /**
     * Called when a trip event is detected.
     *
     * @param event the detected trip event
     */
    void onTripDetected(TripEvent event);
  }
}
