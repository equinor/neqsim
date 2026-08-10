package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.BDFIntegrator;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.ExplicitEulerIntegrator;
import neqsim.process.dynamics.IntegratorStrategy;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that {@link ProcessSystem#runTransient(double, java.util.UUID)} fires events scheduled on an attached
 * {@link EventScheduler} at the correct simulation time, and that the pluggable {@link IntegratorStrategy} hook is
 * reachable via getter/setter.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class RunTransientEventSchedulerTest {

  private static final class RecordingProcessSystem extends ProcessSystem {
    private static final long serialVersionUID = 1000;
    private final List<String> executionOrder;
    private final String marker;

    private RecordingProcessSystem(String name, String marker, List<String> executionOrder) {
      super(name);
      this.marker = marker;
      this.executionOrder = executionOrder;
    }

    @Override
    public synchronized void runTransient(double dt, UUID id) {
      executionOrder.add(marker);
      super.runTransient(dt, id);
    }
  }

  /**
   * Builds a minimal ProcessSystem (feed stream + separator).
   *
   * @return a runnable process
   */
  private static ProcessSystem buildMinimalProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");
    Separator sep = new Separator("sep", feed);
    ProcessSystem p = new ProcessSystem();
    p.add(feed);
    p.add(sep);
    p.run();
    return p;
  }

  /**
   * Event scheduled at t=1.5s with dt=0.5s must fire exactly once and only after the third step (current time 1.5s).
   */
  @Test
  public void testEventFiresAtCorrectStep() {
    ProcessSystem p = buildMinimalProcess();
    EventScheduler s = new EventScheduler();
    p.setEventScheduler(s);

    final AtomicInteger count = new AtomicInteger(0);
    s.scheduleEvent(1.5, "trip", new Runnable() {
      @Override
      public void run() {
        count.incrementAndGet();
      }
    });

    // Step 1 → t=0.5, Step 2 → t=1.0, Step 3 → t=1.5 (event due), Step 4 → t=2.0.
    // Each physical step has its own identifier; one identifier is only reused for
    // refinement/evaluation work inside that physical step.
    p.runTransient(0.5, TransientStepIdentifier.deterministicPhysicalStep("event-fire", 0L));
    assertEquals(0, count.get(), "Event must not fire before its time");
    p.runTransient(0.5, TransientStepIdentifier.deterministicPhysicalStep("event-fire", 1L));
    assertEquals(0, count.get(), "Event must not fire before its time");
    p.runTransient(0.5, TransientStepIdentifier.deterministicPhysicalStep("event-fire", 2L));
    assertEquals(1, count.get(), "Event must fire when current time reaches 1.5s");
    p.runTransient(0.5, TransientStepIdentifier.deterministicPhysicalStep("event-fire", 3L));
    assertEquals(1, count.get(), "Event must fire only once");

    assertEquals(1, s.getFiredEvents().size());
    assertEquals(0, s.getPendingEvents().size());
  }

  /**
   * Event Runnable mutates an external flag — verifies the action actually runs inside the transient loop.
   */
  @Test
  public void testEventRunnableMutatesFlag() {
    ProcessSystem p = buildMinimalProcess();
    EventScheduler s = new EventScheduler();
    p.setEventScheduler(s);
    final boolean[] fired = new boolean[] { false };
    s.scheduleEvent(0.5, "esd", new Runnable() {
      @Override
      public void run() {
        fired[0] = true;
      }
    });
    p.runTransient(0.5, UUID.randomUUID());
    assertTrue(fired[0], "Runnable payload must execute inside runTransient");
  }

  /**
   * A failing safety/operator event must abort the ProcessSystem physical-step attempt before equipment and process
   * calculation identifiers are committed. The already-advanced process clock documents the remaining whole-step
   * transaction gap and should be rolled back by the future #2911 transaction layer.
   */
  @Test
  public void testEventFailureAbortsProcessStepBeforeEquipmentCommit() {
    ProcessSystem p = buildMinimalProcess();
    EventScheduler s = new EventScheduler();
    p.setEventScheduler(s);
    UUID previousProcessIdentifier = p.getCalculationIdentifier();
    UUID previousSeparatorIdentifier = p.getUnit("sep").getCalculationIdentifier();
    UUID failedPhysicalStep = TransientStepIdentifier.deterministicPhysicalStep("failed-event", 0L);

    s.scheduleEvent(0.5, "failed trip", new Runnable() {
      @Override
      public void run() {
        throw new IllegalStateException("trip actuator failed");
      }
    });

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> p.runTransient(0.5, failedPhysicalStep));

    assertEquals("trip actuator failed", failure.getMessage());
    assertEquals(1, s.getPendingEvents().size(), "failed event must remain retryable");
    assertEquals(0, s.getFiredEvents().size(), "failed event must not be recorded as successfully fired");
    assertEquals(previousProcessIdentifier, p.getCalculationIdentifier(),
        "failed physical step must not commit the ProcessSystem calculation identifier");
    assertEquals(previousSeparatorIdentifier, p.getUnit("sep").getCalculationIdentifier(),
        "event failure occurs before equipment execution and must not commit the separator identifier");
    assertEquals(0.5, p.getTime(), 0.0,
        "current ProcessSystem still advances its clock before event execution; whole-step rollback remains required");
  }

  /**
   * A shared failing event must propagate out of multi-area ProcessModel execution instead of allowing later areas to
   * continue. The first area's already-advanced clock records the remaining model-wide transaction requirement.
   */
  @Test
  public void testEventFailureStopsLaterProcessModelAreas() {
    ProcessSystem firstArea = new ProcessSystem("first area");
    ProcessSystem secondArea = new ProcessSystem("second area");
    ProcessModel plant = new ProcessModel();
    plant.add("first", firstArea);
    plant.add("second", secondArea);

    EventScheduler shared = new EventScheduler();
    plant.setEventScheduler(shared);
    shared.scheduleEvent(0.5, "failed shared trip", new Runnable() {
      @Override
      public void run() {
        throw new IllegalStateException("shared trip failed");
      }
    });

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> plant.runTransient(0.5, TransientStepIdentifier.deterministicPhysicalStep("failed-model-event", 0L)));

    assertEquals("shared trip failed", failure.getMessage());
    assertEquals(0.5, firstArea.getTime(), 0.0,
        "first area currently advances before the shared event failure; model-wide rollback remains required");
    assertEquals(0.0, secondArea.getTime(), 0.0, "later areas must not continue after a failed shared event");
    assertEquals(1, shared.getPendingEvents().size());
    assertEquals(0, shared.getFiredEvents().size());
  }

  /**
   * IntegratorStrategy default is ExplicitEulerIntegrator; setter+getter roundtrip; null restores default.
   */
  @Test
  public void testIntegratorStrategyAccessors() {
    ProcessSystem p = new ProcessSystem();
    IntegratorStrategy def = p.getIntegratorStrategy();
    assertNotNull(def, "default integrator must not be null");
    assertTrue(def instanceof ExplicitEulerIntegrator, "default must be Explicit Euler");

    BDFIntegrator bdf = new BDFIntegrator();
    p.setIntegratorStrategy(bdf);
    assertSame(bdf, p.getIntegratorStrategy());

    p.setIntegratorStrategy(null);
    assertTrue(p.getIntegratorStrategy() instanceof ExplicitEulerIntegrator,
        "null must restore default explicit Euler");
  }

  /**
   * Shared scheduler attached via ProcessModel propagates to all child ProcessSystems and fires during
   * ProcessModel.runTransient.
   */
  @Test
  public void testProcessModelPropagatesScheduler() {
    ProcessSystem p1 = buildMinimalProcess();
    ProcessSystem p2 = buildMinimalProcess();
    ProcessModel plant = new ProcessModel();
    plant.add("area1", p1);
    plant.add("area2", p2);

    EventScheduler shared = new EventScheduler();
    plant.setEventScheduler(shared);
    assertSame(shared, p1.getEventScheduler());
    assertSame(shared, p2.getEventScheduler());

    final AtomicInteger count = new AtomicInteger(0);
    shared.scheduleEvent(0.5, "ioa", new Runnable() {
      @Override
      public void run() {
        count.incrementAndGet();
      }
    });

    plant.runTransient(0.5, UUID.randomUUID());
    // Both areas advance to t=0.5; the first area fires the event, the second sees an empty queue.
    assertEquals(1, count.get(), "Shared event must fire exactly once across all areas");
  }

  /**
   * A shared event scheduler must not be evaluated against misaligned area clocks because the first area could run
   * before an event that a later area fires during the same model step.
   */
  @Test
  public void testProcessModelRejectsMisalignedAreaClocksBeforeMutation() {
    ProcessSystem earlyArea = buildMinimalProcess();
    ProcessSystem lateArea = buildMinimalProcess();
    lateArea.setTime(10.0);

    UUID earlyEquipmentIdentifier = UUID.randomUUID();
    UUID lateEquipmentIdentifier = UUID.randomUUID();
    UUID earlyAreaIdentifier = UUID.randomUUID();
    UUID lateAreaIdentifier = UUID.randomUUID();
    earlyArea.setCalculationIdentifier(earlyAreaIdentifier);
    lateArea.setCalculationIdentifier(lateAreaIdentifier);
    earlyArea.getUnit("sep").setCalculationIdentifier(earlyEquipmentIdentifier);
    lateArea.getUnit("sep").setCalculationIdentifier(lateEquipmentIdentifier);

    ProcessModel plant = new ProcessModel();
    plant.add("early", earlyArea);
    plant.add("late", lateArea);

    EventScheduler shared = new EventScheduler();
    plant.setEventScheduler(shared);
    AtomicInteger count = new AtomicInteger(0);
    shared.scheduleEvent(5.0, "shared trip", count::incrementAndGet);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> plant.runTransient(1.0, UUID.randomUUID()));

    assertTrue(exception.getMessage().contains("early"));
    assertTrue(exception.getMessage().contains("late"));
    assertTrue(exception.getMessage().contains("difference 10.0 s"));
    assertTrue(exception.getMessage().contains("tolerance 1.0E-9 s"));
    assertTrue(exception.getMessage().contains("reset or synchronize area clocks"));
    assertEquals(0.0, earlyArea.getTime(), 0.0);
    assertEquals(10.0, lateArea.getTime(), 0.0);
    assertEquals(earlyAreaIdentifier, earlyArea.getCalculationIdentifier());
    assertEquals(lateAreaIdentifier, lateArea.getCalculationIdentifier());
    assertEquals(earlyEquipmentIdentifier, earlyArea.getUnit("sep").getCalculationIdentifier());
    assertEquals(lateEquipmentIdentifier, lateArea.getUnit("sep").getCalculationIdentifier());
    assertEquals(0, count.get());
    assertEquals(1, shared.getPendingEvents().size());
    assertEquals(0, shared.getFiredEvents().size());
  }

  /** Tolerance-close clocks retain insertion-order stepping and shared-event behavior. */
  @Test
  public void testProcessModelAcceptsToleranceCloseAreaClocksInInsertionOrder() {
    List<String> executionOrder = new ArrayList<String>();
    ProcessSystem firstArea = new RecordingProcessSystem("first area", "first", executionOrder);
    ProcessSystem secondArea = new RecordingProcessSystem("second area", "second", executionOrder);
    double referenceTime = 1.0e6;
    double differenceWithinRelativeTolerance = 5.0e-7;
    firstArea.setTime(referenceTime);
    secondArea.setTime(referenceTime + differenceWithinRelativeTolerance);

    ProcessModel plant = new ProcessModel();
    plant.add("first", firstArea);
    plant.add("second", secondArea);

    EventScheduler shared = new EventScheduler();
    plant.setEventScheduler(shared);
    AtomicInteger count = new AtomicInteger(0);
    shared.scheduleEvent(referenceTime + 0.5, "shared trip", count::incrementAndGet);

    plant.runTransient(1.0, UUID.randomUUID());

    assertEquals(Arrays.asList("first", "second"), executionOrder);
    assertEquals(referenceTime + 1.0, firstArea.getTime(), 0.0);
    assertEquals(referenceTime + differenceWithinRelativeTolerance + 1.0, secondArea.getTime(), 0.0);
    assertEquals(1, count.get());
    assertEquals(0, shared.getPendingEvents().size());
    assertEquals(1, shared.getFiredEvents().size());

    executionOrder.clear();
    secondArea.setTime(firstArea.getTime() + 1.5e-6);
    assertThrows(IllegalStateException.class, () -> plant.runTransient(1.0, UUID.randomUUID()));
    assertTrue(executionOrder.isEmpty());
  }

  /** Non-finite clocks fail atomically before a shared event or either area can advance. */
  @Test
  public void testProcessModelRejectsNonFiniteAreaClocksBeforeMutation() {
    double[] nonFiniteTimes = new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
    for (double nonFiniteTime : nonFiniteTimes) {
      ProcessSystem finiteArea = new ProcessSystem("finite area");
      ProcessSystem nonFiniteArea = new ProcessSystem("non-finite area");
      nonFiniteArea.setTime(nonFiniteTime);

      ProcessModel plant = new ProcessModel();
      plant.add("finite", finiteArea);
      plant.add("non-finite", nonFiniteArea);

      EventScheduler shared = new EventScheduler();
      plant.setEventScheduler(shared);
      AtomicInteger count = new AtomicInteger(0);
      shared.scheduleEvent(0.5, "shared trip", count::incrementAndGet);

      IllegalStateException exception = assertThrows(IllegalStateException.class,
          () -> plant.runTransient(1.0, UUID.randomUUID()));

      assertTrue(exception.getMessage().contains("non-finite"));
      assertTrue(exception.getMessage().contains(Double.toString(nonFiniteTime)));
      assertTrue(exception.getMessage().contains("reset or synchronize area clocks"));
      assertEquals(0.0, finiteArea.getTime(), 0.0);
      assertEquals(Double.doubleToLongBits(nonFiniteTime), Double.doubleToLongBits(nonFiniteArea.getTime()));
      assertEquals(0, count.get());
      assertEquals(1, shared.getPendingEvents().size());
      assertEquals(0, shared.getFiredEvents().size());
    }
  }

  /** Empty and single-area models retain their previous transient behavior. */
  @Test
  public void testProcessModelEmptyAndSingleAreaBehaviorIsUnchanged() {
    ProcessModel emptyModel = new ProcessModel();
    emptyModel.runTransient(1.0, UUID.randomUUID());

    ProcessSystem onlyArea = new ProcessSystem("only area");
    onlyArea.setTime(4.0);
    ProcessModel singleAreaModel = new ProcessModel();
    singleAreaModel.add("only", onlyArea);
    EventScheduler scheduler = new EventScheduler();
    singleAreaModel.setEventScheduler(scheduler);
    AtomicInteger count = new AtomicInteger(0);
    scheduler.scheduleEvent(4.5, "single-area event", count::incrementAndGet);

    singleAreaModel.runTransient(0.5, UUID.randomUUID());

    assertEquals(4.5, onlyArea.getTime(), 0.0);
    assertEquals(1, count.get());
  }

  /** Repeated valid model steps keep exactly aligned clocks deterministic. */
  @Test
  public void testProcessModelRepeatedStepsKeepAreaClocksAligned() {
    ProcessSystem firstArea = new ProcessSystem("first area");
    ProcessSystem secondArea = new ProcessSystem("second area");
    ProcessModel plant = new ProcessModel();
    plant.add("first", firstArea);
    plant.add("second", secondArea);

    for (int step = 1; step <= 4; step++) {
      UUID physicalStepId = TransientStepIdentifier.deterministicPhysicalStep("aligned-model", step - 1L);
      plant.runTransient(0.25, physicalStepId);
      assertEquals(step * 0.25, firstArea.getTime(), 0.0);
      assertEquals(firstArea.getTime(), secondArea.getTime(), 0.0);
    }
  }
}
