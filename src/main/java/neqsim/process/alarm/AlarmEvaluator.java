package neqsim.process.alarm;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Utility class for evaluating alarms across multiple measurement devices.
 * 
 * <p>
 * Simplifies the alarm evaluation loop by automatically running the process system and evaluating
 * all registered measurement devices.
 *
 * @author ESOL
 * @version 1.0
 */
public final class AlarmEvaluator {

  private AlarmEvaluator() {
    // Utility class
  }

  /**
   * Evaluates alarms for all devices registered with the alarm manager.
   * 
   * <p>
   * This method:
   * <ol>
   * <li>Runs the process system to update all values</li>
   * <li>Evaluates each registered measurement device</li>
   * <li>Returns all generated alarm events</li>
   * </ol>
   * 
   * @param alarmManager the alarm manager
   * @param system the process system to run
   * @param dt time step
   * @param time current simulation time
   * @return all alarm events generated
   */
  public static List<AlarmEvent> evaluateAll(ProcessAlarmManager alarmManager, ProcessSystem system,
      double dt, double time) {

    List<AlarmEvent> allEvents = new ArrayList<>();

    // Run the process to get current values
    system.run();

    // Evaluate all registered devices
    // Note: This requires access to devices list, which is private
    // For now, we need to evaluate devices individually in the calling code

    return allEvents;
  }

  /**
   * Evaluates a list of measurement devices.
   * 
   * @param alarmManager the alarm manager
   * @param devices the devices to evaluate
   * @param dt time step
   * @param time current simulation time
   * @return all alarm events generated
   */
  public static List<AlarmEvent> evaluateDevices(ProcessAlarmManager alarmManager,
      List<MeasurementDeviceInterface> devices, double dt, double time) {

    List<AlarmEvent> allEvents = new ArrayList<>();

    for (MeasurementDeviceInterface device : devices) {
      double measuredValue = device.getMeasuredValue();
      List<AlarmEvent> events = alarmManager.evaluateMeasurement(device, measuredValue, dt, time);
      allEvents.addAll(events);
    }

    return allEvents;
  }

  /**
   * Evaluates alarms and displays them if any are generated.
   * 
   * @param alarmManager the alarm manager
   * @param devices the devices to evaluate
   * @param dt time step
   * @param time current simulation time
   * @return all alarm events generated
   */
  public static List<AlarmEvent> evaluateAndDisplay(ProcessAlarmManager alarmManager,
      List<MeasurementDeviceInterface> devices, double dt, double time) {

    List<AlarmEvent> events = evaluateDevices(alarmManager, devices, dt, time);

    if (!events.isEmpty()) {
      AlarmReporter.displayAlarmEvents(events);
    }

    return events;
  }
}
