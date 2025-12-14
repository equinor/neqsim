package neqsim.process.util.event;

/**
 * Listener interface for process events.
 *
 * @author ESOL
 * @version 1.0
 */
@FunctionalInterface
public interface ProcessEventListener {

  /**
   * Called when an event is published.
   *
   * @param event the process event
   */
  void onEvent(ProcessEvent event);
}
