package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for controller ownership during transient process steps.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessSystemTransientControllerTest extends neqsim.NeqSimTest {
  /**
   * Controller that records every transient state update without requiring a transmitter.
   */
  private static final class CountingController extends ControllerDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    private final AtomicInteger executionCount = new AtomicInteger();
    private UUID lastTransientIdentifier;

    /**
     * Creates an active counting controller.
     *
     * @param name controller name
     */
    private CountingController(String name) {
      super(name);
      setActive(true);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double initResponse, double dt, UUID id) {
      executionCount.incrementAndGet();
      lastTransientIdentifier = id;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasRunTransient(UUID id) {
      return id != null && id.equals(lastTransientIdentifier);
    }

    /**
     * Returns the number of controller state updates.
     *
     * @return execution count
     */
    private int getExecutionCount() {
      return executionCount.get();
    }
  }

  /**
   * An equipment-owned controller must not run again when it is also registered for process discovery.
   */
  @Test
  public void embeddedAndStandaloneRegistrationRunsControllerOnce() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(10.0, "bara");
    valve.setCalculateSteadyState(false);

    CountingController controller = new CountingController("pressure controller");
    valve.setController(controller);

    ProcessSystem process = new ProcessSystem("controller ownership");
    process.add(feed);
    process.add(valve);
    process.add(controller);
    process.run();

    process.runTransient(1.0, UUID.randomUUID());

    assertEquals(1, controller.getExecutionCount());
  }

  /**
   * Attachment alone must not suppress the standalone phase when equipment does not execute its controller.
   */
  @Test
  public void attachedButNotEquipmentExecutedControllerRunsStandalone() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Heater heater = new Heater("heater", feed);

    CountingController controller = new CountingController("temperature controller");
    heater.setController(controller);

    ProcessSystem process = new ProcessSystem("controller attachment");
    process.add(feed);
    process.add(heater);
    process.add(controller);
    process.run();

    process.runTransient(1.0, UUID.randomUUID());

    assertEquals(1, controller.getExecutionCount());
  }

  /**
   * Repeated standalone registration of the same controller identity schedules one update per timestep.
   */
  @Test
  public void duplicateStandaloneRegistrationRunsControllerOnce() {
    CountingController controller = new CountingController("standalone controller");
    ProcessSystem process = new ProcessSystem("duplicate standalone controller");
    process.add(controller);
    process.add(controller);

    process.runTransient(1.0, UUID.randomUUID());

    assertEquals(1, controller.getExecutionCount());
  }

  /**
   * Distinct controller identities remain independent across repeated timesteps, even when their names match.
   */
  @Test
  public void distinctStandaloneControllersRunOncePerTimestep() {
    CountingController firstController = new CountingController("shared tag");
    CountingController secondController = new CountingController("shared tag");
    ProcessSystem process = new ProcessSystem("distinct standalone controllers");
    process.add(firstController);
    process.add(secondController);

    process.runTransient(1.0, UUID.randomUUID());
    process.runTransient(1.0, UUID.randomUUID());

    assertEquals(2, firstController.getExecutionCount());
    assertEquals(2, secondController.getExecutionCount());
  }
}
