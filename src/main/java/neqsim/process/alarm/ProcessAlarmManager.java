package neqsim.process.alarm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * Coordinates alarm evaluation across all measurement devices in a process system.
 * 
 * <p>
 * Supports automatic alarm-triggered actions through registered {@link AlarmActionHandler}
 * instances.
 */
public class ProcessAlarmManager implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final List<MeasurementDeviceInterface> devices = new ArrayList<>();
  private final List<AlarmEvent> history = new ArrayList<>();
  private final List<AlarmActionHandler> actionHandlers = new ArrayList<>();

  /**
   * Registers a measurement device for alarm supervision.
   *
   * @param device device to register
   */
  public void register(MeasurementDeviceInterface device) {
    if (device != null && !devices.contains(device)) {
      devices.add(device);
    }
  }

  /**
   * Registers multiple devices in one operation.
   *
   * @param measurementDevices devices to register
   */
  public void registerAll(List<MeasurementDeviceInterface> measurementDevices) {
    if (measurementDevices == null) {
      return;
    }
    for (MeasurementDeviceInterface device : measurementDevices) {
      register(device);
    }
  }

  /**
   * Evaluates alarms for a single measurement value.
   *
   * @param device measurement device producing the value
   * @param measuredValue current measured value
   * @param dt time step
   * @param time current simulation time
   * @return events generated during evaluation
   */
  public List<AlarmEvent> evaluateMeasurement(MeasurementDeviceInterface device,
      double measuredValue, double dt, double time) {
    if (device == null) {
      return Collections.emptyList();
    }
    List<AlarmEvent> events = device.evaluateAlarm(measuredValue, dt, time);
    if (!events.isEmpty()) {
      history.addAll(events);
      // Execute registered action handlers
      executeActionHandlers(events);
    }
    return events;
  }

  /**
   * Registers an action handler to respond to alarm events.
   * 
   * @param handler the action handler to register
   */
  public void registerActionHandler(AlarmActionHandler handler) {
    if (handler != null && !actionHandlers.contains(handler)) {
      actionHandlers.add(handler);
      // Sort by priority (higher priority first)
      actionHandlers.sort((h1, h2) -> Integer.compare(h2.getPriority(), h1.getPriority()));
    }
  }

  /**
   * Removes an action handler.
   * 
   * @param handler the action handler to remove
   */
  public void removeActionHandler(AlarmActionHandler handler) {
    actionHandlers.remove(handler);
  }

  /**
   * Gets all registered action handlers.
   * 
   * @return immutable list of action handlers
   */
  public List<AlarmActionHandler> getActionHandlers() {
    return Collections.unmodifiableList(actionHandlers);
  }

  /**
   * Executes all registered action handlers for the given events.
   * 
   * @param events alarm events to process
   */
  private void executeActionHandlers(List<AlarmEvent> events) {
    for (AlarmEvent event : events) {
      for (AlarmActionHandler handler : actionHandlers) {
        try {
          handler.handle(event);
        } catch (Exception e) {
          // Log but don't propagate exceptions from handlers
          System.err.println("Error executing alarm action handler: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Acknowledges alarms for all registered devices.
   *
   * @param time simulation time for the acknowledgement
   * @return list of acknowledgement events
   */
  public List<AlarmEvent> acknowledgeAll(double time) {
    List<AlarmEvent> events = new ArrayList<>();
    for (MeasurementDeviceInterface device : devices) {
      AlarmEvent event = device.acknowledgeAlarm(time);
      if (event != null) {
        history.add(event);
        events.add(event);
      }
    }
    return events;
  }

  /**
   * Returns an immutable view of the collected alarm history.
   *
   * @return alarm event history
   */
  public List<AlarmEvent> getHistory() {
    return Collections.unmodifiableList(history);
  }

  /**
   * Returns snapshots of all currently active alarms.
   *
   * @return list of active alarm snapshots
   */
  public List<AlarmStatusSnapshot> getActiveAlarms() {
    List<AlarmStatusSnapshot> active = new ArrayList<>();
    for (MeasurementDeviceInterface device : devices) {
      AlarmState state = device.getAlarmState();
      if (state != null && state.isActive()) {
        AlarmStatusSnapshot snapshot = state.snapshot(device.getName());
        if (snapshot != null) {
          active.add(snapshot);
        }
      }
    }
    return active;
  }

  /**
   * Removes all recorded alarm events.
   */
  public void clearHistory() {
    history.clear();
  }

  /**
   * Copies the content of another manager instance.
   *
   * @param source source manager to copy from
   * @param measurementDevices devices that should be supervised by this manager
   */
  public void applyFrom(ProcessAlarmManager source,
      List<MeasurementDeviceInterface> measurementDevices) {
    history.clear();
    if (source != null) {
      history.addAll(source.history);
    }
    devices.clear();
    if (measurementDevices != null) {
      devices.addAll(measurementDevices);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(history, devices);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ProcessAlarmManager other = (ProcessAlarmManager) obj;
    return Objects.equals(history, other.history) && Objects.equals(devices, other.devices);
  }
}
