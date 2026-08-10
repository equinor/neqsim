package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Regression tests for transactional event-scheduler bookkeeping snapshots. */
public class EventSchedulerSnapshotTest extends neqsim.NeqSimTest {
  /** Restoring a snapshot recovers pending/fired membership without pretending to undo action side effects. */
  @Test
  public void restoreRecoversBookkeepingButDoesNotUndoExternalActionEffects() {
    EventScheduler scheduler = new EventScheduler();
    AtomicInteger actionCount = new AtomicInteger();
    scheduler.scheduleEvent(1.0, "first", actionCount::incrementAndGet);
    scheduler.scheduleEvent(2.0, "second", actionCount::incrementAndGet);

    EventScheduler.Snapshot beforeStep = scheduler.snapshot();
    assertEquals(2, beforeStep.getPendingEventCount());
    assertEquals(0, beforeStep.getFiredEventCount());

    assertEquals(1, scheduler.fireDueEvents(1.0));
    assertEquals(1, actionCount.get());
    assertEquals(1, scheduler.getPendingEvents().size());
    assertEquals(1, scheduler.getFiredEvents().size());

    scheduler.restore(beforeStep);

    assertEquals(2, scheduler.getPendingEvents().size());
    assertEquals(0, scheduler.getFiredEvents().size());
    assertEquals(1, actionCount.get(), "scheduler rollback cannot undo an external Runnable side effect");

    assertEquals(1, scheduler.fireDueEvents(1.0));
    assertEquals(2, actionCount.get(), "restored scheduler state permits deterministic replay of the event");
  }

  /** A checkpoint taken after one event fires restores that exact pending/fired boundary. */
  @Test
  public void snapshotRestoresIntermediateEventBoundary() {
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleEvent(1.0, "first", new NoOpAction());
    scheduler.scheduleEvent(2.0, "second", new NoOpAction());
    scheduler.scheduleEvent(3.0, "third", new NoOpAction());

    assertEquals(1, scheduler.fireDueEvents(1.0));
    EventScheduler.Snapshot afterFirst = scheduler.snapshot();

    assertEquals(2, afterFirst.getPendingEventCount());
    assertEquals(1, afterFirst.getFiredEventCount());
    assertEquals(2, scheduler.fireDueEvents(3.0));
    assertEquals(0, scheduler.getPendingEvents().size());
    assertEquals(3, scheduler.getFiredEvents().size());

    scheduler.restore(afterFirst);

    assertEquals(2, scheduler.getPendingEvents().size());
    assertEquals("second", scheduler.getPendingEvents().get(0).getLabel());
    assertEquals("third", scheduler.getPendingEvents().get(1).getLabel());
    assertEquals(1, scheduler.getFiredEvents().size());
    assertEquals("first", scheduler.getFiredEvents().get(0).getLabel());
  }

  /** Event-boundary inspection is ordered and read-only so trial-step selection cannot mutate scheduler state. */
  @Test
  public void eventBoundaryInspectionDoesNotMutateScheduler() {
    EventScheduler scheduler = new EventScheduler();
    AtomicInteger actionCount = new AtomicInteger();
    scheduler.scheduleEvent(3.0, "third", actionCount::incrementAndGet);
    scheduler.scheduleEvent(1.0, "first", actionCount::incrementAndGet);
    scheduler.scheduleEvent(2.0, "second", actionCount::incrementAndGet);

    assertEquals(1.0, scheduler.getNextEventTime(), 0.0);
    List<EventScheduler.ScheduledEvent> due = scheduler.getDueEvents(2.0);

    assertEquals(2, due.size());
    assertEquals("first", due.get(0).getLabel());
    assertEquals("second", due.get(1).getLabel());
    assertEquals(3, scheduler.getPendingEvents().size());
    assertEquals(0, scheduler.getFiredEvents().size());
    assertEquals(0, actionCount.get());

    assertEquals(1, scheduler.fireDueEvents(1.0));
    assertEquals(2.0, scheduler.getNextEventTime(), 0.0);
    assertEquals(1, actionCount.get());

    scheduler.fireDueEvents(3.0);
    assertEquals(Double.POSITIVE_INFINITY, scheduler.getNextEventTime(), 0.0);
  }

  /** Null state is rejected before existing scheduler bookkeeping can be changed. */
  @Test
  public void nullSnapshotIsRejectedWithoutMutation() {
    EventScheduler scheduler = new EventScheduler();
    scheduler.scheduleEvent(1.0, "event", new NoOpAction());

    assertThrows(IllegalArgumentException.class, () -> scheduler.restore(null));
    assertEquals(1, scheduler.getPendingEvents().size());
    assertEquals(0, scheduler.getFiredEvents().size());
  }

  private static final class NoOpAction implements Runnable, java.io.Serializable {
    private static final long serialVersionUID = 1000L;

    @Override
    public void run() {
      // Intentional no-op test event.
    }
  }
}
