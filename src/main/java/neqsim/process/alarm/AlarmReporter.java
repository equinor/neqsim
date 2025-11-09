package neqsim.process.alarm;

import java.util.List;

/**
 * Utility class for formatting and displaying alarm information.
 * 
 * <p>
 * Provides consistent formatting for:
 * <ul>
 * <li>Alarm status displays</li>
 * <li>Alarm history reports</li>
 * <li>Alarm statistics</li>
 * <li>Individual alarm events</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public final class AlarmReporter {

  private AlarmReporter() {
    // Utility class
  }

  /**
   * Displays current alarm status in a formatted box.
   * 
   * @param alarmManager the alarm manager
   * @param context description of current context
   */
  public static void displayAlarmStatus(ProcessAlarmManager alarmManager, String context) {
    List<AlarmStatusSnapshot> activeAlarms = alarmManager.getActiveAlarms();

    System.out.println("\n  ┌─────────────────────────────────────────────────────────┐");
    System.out.println("  │ ALARM STATUS: " + String.format("%-42s", context) + "│");
    System.out.println("  ├─────────────────────────────────────────────────────────┤");

    if (activeAlarms.isEmpty()) {
      System.out.println("  │ ✓ No active alarms - All systems normal                │");
    } else {
      System.out.println("  │ Active Alarms: " + String.format("%-42d", activeAlarms.size()) + "│");
      System.out.println("  ├─────────────────────────────────────────────────────────┤");
      for (AlarmStatusSnapshot alarm : activeAlarms) {
        String ackStatus = alarm.isAcknowledged() ? "[ACK]" : "[NEW]";
        String info = String.format("%s %-4s - %-15s: %.2f", ackStatus, alarm.getLevel().toString(),
            alarm.getSource(), alarm.getValue());
        System.out.println("  │ " + String.format("%-55s", info) + "│");
      }
    }

    System.out.println("  └─────────────────────────────────────────────────────────┘");
  }

  /**
   * Displays complete alarm history in a formatted report.
   * 
   * @param alarmManager the alarm manager
   */
  public static void displayAlarmHistory(ProcessAlarmManager alarmManager) {
    displayAlarmHistory(alarmManager, 15);
  }

  /**
   * Displays complete alarm history in a formatted report.
   * 
   * @param alarmManager the alarm manager
   * @param maxRecentEvents maximum number of recent events to display
   */
  public static void displayAlarmHistory(ProcessAlarmManager alarmManager, int maxRecentEvents) {
    List<AlarmEvent> history = alarmManager.getHistory();

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                    ALARM HISTORY REPORT                        ║");
    System.out.println("╠════════════════════════════════════════════════════════════════╣");

    if (history.isEmpty()) {
      System.out.println("║  No alarm events recorded                                      ║");
    } else {
      System.out.println("║  Total Events: " + String.format("%-47d", history.size()) + "║");
      System.out.println("╠════════════════════════════════════════════════════════════════╣");

      // Display recent events
      int displayCount = Math.min(maxRecentEvents, history.size());
      System.out.println("║  Recent Events (last " + String.format("%-2d", displayCount)
          + "):                                  ║");

      for (int i = history.size() - displayCount; i < history.size(); i++) {
        AlarmEvent event = history.get(i);
        String eventStr = formatAlarmEventCompact(event);
        System.out.println("║  " + String.format("%-61s", eventStr) + "║");
      }
    }

    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Displays alarm statistics aggregated by type and level.
   * 
   * @param alarmManager the alarm manager
   */
  public static void displayAlarmStatistics(ProcessAlarmManager alarmManager) {
    List<AlarmEvent> history = alarmManager.getHistory();

    long activated = history.stream().filter(e -> e.getType() == AlarmEventType.ACTIVATED).count();
    long cleared = history.stream().filter(e -> e.getType() == AlarmEventType.CLEARED).count();
    long acknowledged =
        history.stream().filter(e -> e.getType() == AlarmEventType.ACKNOWLEDGED).count();

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                    ALARM STATISTICS                            ║");
    System.out.println("╠════════════════════════════════════════════════════════════════╣");
    System.out.println("║  Total Activations:     " + String.format("%-36d", activated) + "║");
    System.out.println("║  Total Clearances:      " + String.format("%-36d", cleared) + "║");
    System.out.println("║  Total Acknowledgements: " + String.format("%-35d", acknowledged) + "║");
    System.out.println("║                                                                ║");

    // Count by level
    long hihi = history.stream()
        .filter(e -> e.getType() == AlarmEventType.ACTIVATED && e.getLevel() == AlarmLevel.HIHI)
        .count();
    long hi = history.stream()
        .filter(e -> e.getType() == AlarmEventType.ACTIVATED && e.getLevel() == AlarmLevel.HI)
        .count();
    long lo = history.stream()
        .filter(e -> e.getType() == AlarmEventType.ACTIVATED && e.getLevel() == AlarmLevel.LO)
        .count();
    long lolo = history.stream()
        .filter(e -> e.getType() == AlarmEventType.ACTIVATED && e.getLevel() == AlarmLevel.LOLO)
        .count();

    System.out.println("║  By Level:                                                     ║");
    System.out.println("║    HIHI (Critical High): " + String.format("%-33d", hihi) + "║");
    System.out.println("║    HI (High):            " + String.format("%-33d", hi) + "║");
    System.out.println("║    LO (Low):             " + String.format("%-33d", lo) + "║");
    System.out.println("║    LOLO (Critical Low):  " + String.format("%-33d", lolo) + "║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Formats an alarm event for display with full details.
   * 
   * @param event the alarm event
   * @return formatted string
   */
  public static String formatAlarmEvent(AlarmEvent event) {
    String typeIcon = getEventTypeIcon(event.getType());

    return String.format("%s [t=%.1fs] %s %-15s %-4s: %.2f", typeIcon, event.getTimestamp(),
        event.getType(), event.getSource(), event.getLevel(), event.getValue());
  }

  /**
   * Formats alarm event in compact form for reports.
   * 
   * @param event the alarm event
   * @return formatted string
   */
  public static String formatAlarmEventCompact(AlarmEvent event) {
    String typeIcon = getEventTypeIcon(event.getType());

    return String.format("%s %.1fs %-12s %-15s %-4s %.2f", typeIcon, event.getTimestamp(),
        event.getType(), event.getSource(), event.getLevel(), event.getValue());
  }

  /**
   * Gets an icon representing the event type.
   * 
   * @param type the event type
   * @return icon character
   */
  private static String getEventTypeIcon(AlarmEventType type) {
    switch (type) {
      case ACTIVATED:
        return "⚠";
      case CLEARED:
        return "✓";
      case ACKNOWLEDGED:
        return "✋";
      default:
        return "?";
    }
  }

  /**
   * Prints a formatted scenario header.
   * 
   * @param title the scenario title
   */
  public static void printScenarioHeader(String title) {
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║  " + String.format("%-61s", title) + "║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Displays alarm events in a formatted box.
   * 
   * @param events the alarm events to display
   */
  public static void displayAlarmEvents(List<AlarmEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }

    System.out.println("\n  ╔═══════════════════════════════════════════════════════╗");
    System.out.println("  ║           ALARM EVENTS DETECTED                       ║");
    System.out.println("  ╠═══════════════════════════════════════════════════════╣");
    for (AlarmEvent event : events) {
      String formatted = formatAlarmEvent(event);
      // Truncate if too long
      if (formatted.length() > 53) {
        formatted = formatted.substring(0, 50) + "...";
      }
      System.out.println("  ║ " + String.format("%-53s", formatted) + "║");
    }
    System.out.println("  ╚═══════════════════════════════════════════════════════╝");
  }
}
