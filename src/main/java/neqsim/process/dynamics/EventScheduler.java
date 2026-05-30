package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple priority-queue-backed event scheduler for dynamic process simulations and safety studies.
 *
 * <p>
 * Events are scheduled at absolute simulation time (seconds). At each integration step the caller
 * invokes {@link #fireDueEvents(double)} with the current simulation time; all events with
 * {@code time <= now} are fired in time order and removed from the queue.
 * </p>
 *
 * <p>
 * Typical use cases:
 * </p>
 * <ul>
 * <li>Initiating Operator Action (IOA) / Independent Operator Action (IOA) signals at a fixed
 * post-trip time in safety studies.</li>
 * <li>ESD trip sequences (close-shut valve at t=2 s, depressurize at t=5 s).</li>
 * <li>Setpoint changes for controller tuning studies.</li>
 * </ul>
 *
 * <p>
 * Events are {@link Serializable} so a scheduler instance can be carried inside a serialized
 * {@code ProcessSystem} snapshot. The {@link Runnable} payload must therefore also be serializable;
 * agent code typically uses a tiny static class or a lambda over serializable fields.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class EventScheduler implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * A scheduled event: trigger time, label, and payload.
   */
  public static final class ScheduledEvent implements Serializable, Comparable<ScheduledEvent> {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final double time;
    private final String label;
    private final Runnable action;

    /**
     * Constructor.
     *
     * @param time absolute simulation time in seconds (must be finite and {@code >= 0})
     * @param label short tag for diagnostics
     * @param action payload (must be non-null and serializable)
     */
    public ScheduledEvent(double time, String label, Runnable action) {
      if (Double.isNaN(time) || Double.isInfinite(time) || time < 0.0) {
        throw new IllegalArgumentException("time must be finite and >= 0, got " + time);
      }
      if (action == null) {
        throw new IllegalArgumentException("action must not be null");
      }
      this.time = time;
      this.label = (label == null) ? "" : label;
      this.action = action;
    }

    /**
     * Returns the scheduled time in seconds.
     *
     * @return time
     */
    public double getTime() {
      return time;
    }

    /**
     * Returns the event label.
     *
     * @return label
     */
    public String getLabel() {
      return label;
    }

    /**
     * Returns the payload.
     *
     * @return action
     */
    public Runnable getAction() {
      return action;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(ScheduledEvent other) {
      return Double.compare(this.time, other.time);
    }
  }

  private final List<ScheduledEvent> queue = new ArrayList<ScheduledEvent>();
  private final List<ScheduledEvent> fired = new ArrayList<ScheduledEvent>();

  /**
   * Default constructor; empty queue.
   */
  public EventScheduler() {
    // empty
  }

  /**
   * Schedules an event at absolute time {@code time}.
   *
   * @param time absolute simulation time in seconds
   * @param label short tag (may be null)
   * @param action serializable payload
   * @return the scheduled event
   */
  public ScheduledEvent scheduleEvent(double time, String label, Runnable action) {
    ScheduledEvent e = new ScheduledEvent(time, label, action);
    queue.add(e);
    Collections.sort(queue);
    return e;
  }

  /**
   * Fires all events with {@code time <= now} in time order. Each event is removed from the pending
   * queue and appended to the fired log.
   *
   * @param now current simulation time in seconds
   * @return number of events fired in this call
   */
  public int fireDueEvents(double now) {
    int count = 0;
    while (!queue.isEmpty() && queue.get(0).time <= now) {
      ScheduledEvent e = queue.remove(0);
      try {
        e.action.run();
      } catch (RuntimeException ex) {
        // surface but do not propagate — dynamic loop must keep running
        System.err.println("EventScheduler: event '" + e.label + "' threw: " + ex.getMessage());
      }
      fired.add(e);
      count++;
    }
    return count;
  }

  /**
   * Returns the pending queue (read-only view).
   *
   * @return pending events sorted by time
   */
  public List<ScheduledEvent> getPendingEvents() {
    return Collections.unmodifiableList(queue);
  }

  /**
   * Returns the log of fired events in firing order.
   *
   * @return fired events
   */
  public List<ScheduledEvent> getFiredEvents() {
    return Collections.unmodifiableList(fired);
  }

  /**
   * Removes all pending and fired events.
   */
  public void clear() {
    queue.clear();
    fired.clear();
  }
}
