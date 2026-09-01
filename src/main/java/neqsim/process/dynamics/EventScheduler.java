package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple priority-queue-backed event scheduler for dynamic process simulations and safety studies.
 *
 * <p>
 * Events are scheduled at absolute simulation time (seconds). At each integration step the caller invokes
 * {@link #fireDueEvents(double)} with the current simulation time; all events with {@code time <= now} are fired in
 * time order and removed from the queue.
 * </p>
 *
 * <p>
 * Typical use cases:
 * </p>
 * <ul>
 * <li>Initiating Operator Action (IOA) / Independent Operator Action (IOA) signals at a fixed post-trip time in safety
 * studies.</li>
 * <li>ESD trip sequences (close-shut valve at t=2 s, depressurize at t=5 s).</li>
 * <li>Setpoint changes for controller tuning studies.</li>
 * </ul>
 *
 * <p>
 * The scheduler itself is {@link Serializable}. It can therefore be serialized independently when every
 * {@link Runnable} payload is also serializable. The scheduler attached to {@code ProcessSystem} is intentionally a
 * transient runtime service and is not included automatically in a serialized process snapshot.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class EventScheduler implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(EventScheduler.class);

  /**
   * A scheduled event: trigger time, label, and payload.
   */
  public static final class ScheduledEvent implements Serializable, Comparable<ScheduledEvent> {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final double time;
    private final String label;
    private final Runnable action;
    private final List<String> transientStateIdentities;
    private final boolean transientStateScopeDeclared;

    /**
     * Constructor.
     *
     * @param time absolute simulation time in seconds (must be finite and {@code >= 0})
     * @param label short tag for diagnostics
     * @param action payload (must be non-null; must also be serializable when the scheduler itself is serialized)
     */
    public ScheduledEvent(double time, String label, Runnable action) {
      this(time, label, action, Collections.<String>emptyList(), false);
    }

    /**
     * Creates an event with an explicit transient-state mutation scope.
     *
     * @param time absolute simulation time in seconds
     * @param label short diagnostic label
     * @param action event payload
     * @param transientStateIdentities stable identities of every participant the action may mutate
     * @param transientStateScopeDeclared whether the action has declared a complete mutation scope
     */
    private ScheduledEvent(double time, String label, Runnable action, List<String> transientStateIdentities,
        boolean transientStateScopeDeclared) {
      if (Double.isNaN(time) || Double.isInfinite(time) || time < 0.0) {
        throw new IllegalArgumentException("time must be finite and >= 0, got " + time);
      }
      if (action == null) {
        throw new IllegalArgumentException("action must not be null");
      }
      this.time = time;
      this.label = (label == null) ? "" : label;
      this.action = action;
      this.transientStateIdentities = Collections.unmodifiableList(new ArrayList<String>(transientStateIdentities));
      this.transientStateScopeDeclared = transientStateScopeDeclared;
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

    /**
     * Returns whether this event declares a complete in-memory mutation scope for transient rollback.
     *
     * @return {@code true} only for events created by {@link EventScheduler#scheduleTransactionalEvent}
     */
    public boolean hasDeclaredTransientStateScope() {
      return transientStateScopeDeclared;
    }

    /**
     * Returns the stable identities of every transient-state participant the action promises it may mutate.
     *
     * @return immutable, non-empty identity list for a transaction-scoped event
     */
    public List<String> getTransientStateIdentities() {
      return Collections.unmodifiableList(new ArrayList<String>(transientStateIdentities));
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(ScheduledEvent other) {
      return Double.compare(this.time, other.time);
    }
  }

  /**
   * Immutable scheduler-bookkeeping snapshot used by transient step transactions.
   *
   * <p>
   * Scheduled events are immutable, so the snapshot can retain their identities while copying the pending/fired list
   * membership. Restoring this snapshot rolls back scheduler bookkeeping only. It cannot undo side effects already
   * performed by an event {@link Runnable}; rejected trial steps must therefore defer externally visible event actions
   * or restore every object mutated by those actions separately.
   * </p>
   */
  public static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final List<ScheduledEvent> pendingEvents;
    private final List<ScheduledEvent> firedEvents;

    private Snapshot(List<ScheduledEvent> pendingEvents, List<ScheduledEvent> firedEvents) {
      this.pendingEvents = new ArrayList<ScheduledEvent>(pendingEvents);
      this.firedEvents = new ArrayList<ScheduledEvent>(firedEvents);
    }

    /**
     * Number of pending events represented by this snapshot.
     *
     * @return pending event count
     */
    public int getPendingEventCount() {
      return pendingEvents.size();
    }

    /**
     * Number of already-fired events represented by this snapshot.
     *
     * @return fired event count
     */
    public int getFiredEventCount() {
      return firedEvents.size();
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
   * @param action payload
   * @return the scheduled event
   */
  public ScheduledEvent scheduleEvent(double time, String label, Runnable action) {
    ScheduledEvent e = new ScheduledEvent(time, label, action);
    queue.add(e);
    Collections.sort(queue);
    return e;
  }

  /**
   * Schedules an event whose action declares every in-memory transient-state participant it may mutate.
   *
   * <p>
   * A {@code ProcessSystem} transaction accepts pending events only through this API and only when every declared
   * identity belongs to a registered, completely covered participant. The declaration is a fail-closed orchestration
   * contract: the action must not mutate undeclared objects, perform external I/O, publish externally visible events,
   * or schedule an unscoped event. Those effects cannot be undone by participant snapshots.
   * </p>
   *
   * <p>
   * This method does not require the {@link Runnable} to be serializable for an in-memory transaction. Java checkpoint
   * serialization still requires a serializable payload, exactly as for
   * {@link #scheduleEvent(double, String, Runnable)}.
   * </p>
   *
   * @param time absolute simulation time in seconds
   * @param label short diagnostic label
   * @param action event payload
   * @param transientStateIdentities stable identities of every participant the action may mutate
   * @return the scheduled transaction-scoped event
   * @throws IllegalArgumentException if the identity array is null, empty, contains null/empty identities, or contains
   * duplicate identities
   */
  public ScheduledEvent scheduleTransactionalEvent(double time, String label, Runnable action,
      String... transientStateIdentities) {
    if (transientStateIdentities == null || transientStateIdentities.length == 0) {
      throw new IllegalArgumentException("transaction-scoped event must declare at least one transient state identity");
    }
    List<String> normalizedIdentities = new ArrayList<String>(transientStateIdentities.length);
    for (String identity : transientStateIdentities) {
      if (identity == null || identity.trim().isEmpty()) {
        throw new IllegalArgumentException("transient state identity must not be null or empty");
      }
      String normalized = identity.trim();
      if (normalizedIdentities.contains(normalized)) {
        throw new IllegalArgumentException("duplicate transient state identity '" + normalized + "'");
      }
      normalizedIdentities.add(normalized);
    }
    ScheduledEvent event = new ScheduledEvent(time, label, action, normalizedIdentities, true);
    queue.add(event);
    Collections.sort(queue);
    return event;
  }

  /**
   * Returns the earliest pending event time without mutating scheduler state.
   *
   * <p>
   * Adaptive/event-aware integrators can use this value to shorten a proposed timestep so an accepted physical step
   * lands exactly on an event boundary rather than stepping past a trip, setpoint change, or operator action.
   * </p>
   *
   * @return earliest pending absolute event time in seconds, or positive infinity when no event is pending
   */
  public double getNextEventTime() {
    return queue.isEmpty() ? Double.POSITIVE_INFINITY : queue.get(0).time;
  }

  /**
   * Returns pending events that are due at or before {@code now} without firing or removing them.
   *
   * <p>
   * The returned list is an immutable copy in scheduler order. This allows a transient transaction/event-localization
   * layer to inspect an event boundary without changing pending/fired bookkeeping or executing event actions.
   * </p>
   *
   * @param now absolute simulation time in seconds
   * @return immutable copy of due pending events
   */
  public List<ScheduledEvent> getDueEvents(double now) {
    List<ScheduledEvent> due = new ArrayList<ScheduledEvent>();
    for (ScheduledEvent event : queue) {
      if (event.time > now) {
        break;
      }
      due.add(event);
    }
    return Collections.unmodifiableList(due);
  }

  /**
   * Fires all events with {@code time <= now} in time order.
   *
   * <p>
   * An event is removed from the pending queue and appended to the fired log only after its action completes
   * successfully. If an action throws a {@link RuntimeException}, the failure is logged and rethrown immediately. The
   * failing event remains pending, later due events are not executed, and the caller can abort the physical-step
   * attempt instead of silently continuing after a failed trip, setpoint, or operator action.
   * </p>
   *
   * <p>
   * This fail-loud bookkeeping contract is not a whole-process transaction: an action that mutates external state and
   * then throws may already have produced side effects. Qualified rejected-step execution must still restore every
   * mutated object or defer side effects until step acceptance.
   * </p>
   *
   * @param now current simulation time in seconds
   * @return number of events fired successfully in this call
   * @throws RuntimeException if a due event action fails
   */
  public int fireDueEvents(double now) {
    int count = 0;
    while (!queue.isEmpty() && queue.get(0).time <= now) {
      ScheduledEvent e = queue.get(0);
      try {
        e.action.run();
      } catch (RuntimeException ex) {
        logger.error("EventScheduler event '{}' failed: {}", e.label, ex.getMessage(), ex);
        throw ex;
      }
      queue.remove(0);
      fired.add(e);
      count++;
    }
    return count;
  }

  /**
   * Captures the pending/fired scheduler bookkeeping for deterministic transient rollback.
   *
   * <p>
   * This operation does not execute, clone, or serialize event actions. The returned snapshot is independent of later
   * queue/list changes because it copies both event lists.
   * </p>
   *
   * @return immutable scheduler bookkeeping snapshot
   */
  public Snapshot snapshot() {
    return new Snapshot(queue, fired);
  }

  /**
   * Restores pending/fired scheduler bookkeeping from a previous {@link #snapshot()}.
   *
   * <p>
   * Restoring membership does not reverse side effects from actions that have already executed. A rejected transient
   * trial must not rely on this method alone when an event mutates external state.
   * </p>
   *
   * @param snapshot snapshot to restore
   * @throws IllegalArgumentException if snapshot is null
   */
  public void restore(Snapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("snapshot must not be null");
    }
    queue.clear();
    queue.addAll(snapshot.pendingEvents);
    Collections.sort(queue);
    fired.clear();
    fired.addAll(snapshot.firedEvents);
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
