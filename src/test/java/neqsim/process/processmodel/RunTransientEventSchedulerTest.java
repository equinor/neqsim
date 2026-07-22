package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.BDFIntegrator;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.ExplicitEulerIntegrator;
import neqsim.process.dynamics.IntegratorStrategy;
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

    UUID id = UUID.randomUUID();
    // Step 1 → t=0.5, Step 2 → t=1.0, Step 3 → t=1.5 (event due), Step 4 → t=2.0
    p.runTransient(0.5, id);
    assertEquals(0, count.get(), "Event must not fire before its time");
    p.runTransient(0.5, id);
    assertEquals(0, count.get(), "Event must not fire before its time");
    p.runTransient(0.5, id);
    assertEquals(1, count.get(), "Event must fire when current time reaches 1.5s");
    p.runTransient(0.5, id);
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
}
