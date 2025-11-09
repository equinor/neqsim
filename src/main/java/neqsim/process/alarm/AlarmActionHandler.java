package neqsim.process.alarm;

import java.io.Serializable;
import java.util.List;
import neqsim.process.logic.ProcessLogic;

/**
 * Interface for handling alarm-triggered actions.
 * 
 * <p>
 * This interface allows automatic responses to alarm events, such as:
 * <ul>
 * <li>Control adjustments (valve throttling, flow reduction)</li>
 * <li>Safety logic activation (ESD, HIPPS)</li>
 * <li>Operator notifications</li>
 * <li>Logging and reporting</li>
 * </ul>
 * 
 * @author ESOL
 * @version 1.0
 */
@FunctionalInterface
public interface AlarmActionHandler extends Serializable {

  /**
   * Handles an alarm event and performs the configured action.
   * 
   * @param event the alarm event that triggered this handler
   * @return true if action was taken, false otherwise
   */
  boolean handle(AlarmEvent event);

  /**
   * Returns the priority of this handler (higher values execute first).
   * 
   * @return handler priority
   */
  default int getPriority() {
    return 0;
  }

  /**
   * Returns a description of the action this handler performs.
   * 
   * @return action description
   */
  default String getActionDescription() {
    return "Alarm action handler";
  }

  /**
   * Creates a handler that activates process logic when alarm conditions are met.
   * 
   * @param sourceName the alarm source name to match (e.g., "PT-101")
   * @param level the alarm level to match (HIHI, HI, LO, LOLO)
   * @param eventType the event type to match (ACTIVATED, CLEARED, ACKNOWLEDGED)
   * @param logic the process logic to activate
   * @return alarm action handler
   */
  static AlarmActionHandler activateLogic(String sourceName, AlarmLevel level,
      AlarmEventType eventType, ProcessLogic logic) {
    return new AlarmActionHandler() {
      private static final long serialVersionUID = 1000L;

      @Override
      public boolean handle(AlarmEvent event) {
        if (event.getSource().equals(sourceName) && event.getLevel() == level
            && event.getType() == eventType) {
          logic.activate();
          return true;
        }
        return false;
      }

      @Override
      public String getActionDescription() {
        return String.format("Activate %s on %s %s %s", logic.getName(), sourceName, level,
            eventType);
      }

      @Override
      public int getPriority() {
        return 100; // High priority for safety logic
      }
    };
  }

  /**
   * Creates a handler that activates process logic when HIHI alarm is activated.
   * 
   * @param sourceName the alarm source name to match
   * @param logic the process logic to activate
   * @return alarm action handler
   */
  static AlarmActionHandler activateLogicOnHIHI(String sourceName, ProcessLogic logic) {
    return activateLogic(sourceName, AlarmLevel.HIHI, AlarmEventType.ACTIVATED, logic);
  }

  /**
   * Creates a handler that activates process logic when LOLO alarm is activated.
   * 
   * @param sourceName the alarm source name to match
   * @param logic the process logic to activate
   * @return alarm action handler
   */
  static AlarmActionHandler activateLogicOnLOLO(String sourceName, ProcessLogic logic) {
    return activateLogic(sourceName, AlarmLevel.LOLO, AlarmEventType.ACTIVATED, logic);
  }

  /**
   * Creates a composite handler that executes multiple handlers in sequence.
   * 
   * @param handlers list of handlers to execute
   * @return composite alarm action handler
   */
  static AlarmActionHandler composite(List<AlarmActionHandler> handlers) {
    return new AlarmActionHandler() {
      private static final long serialVersionUID = 1000L;

      @Override
      public boolean handle(AlarmEvent event) {
        boolean anyHandled = false;
        for (AlarmActionHandler handler : handlers) {
          if (handler.handle(event)) {
            anyHandled = true;
          }
        }
        return anyHandled;
      }

      @Override
      public String getActionDescription() {
        return "Composite handler with " + handlers.size() + " actions";
      }
    };
  }
}
