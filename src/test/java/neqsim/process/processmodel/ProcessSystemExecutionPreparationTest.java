package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that public process dispatchers prepare a flowsheet once before concrete execution.
 */
class ProcessSystemExecutionPreparationTest {
  /** Stable unit that counts active-state inspection calls. */
  private static final class CountingActiveStateUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private int lockedInactiveChecks;

    /**
     * Creates a stable counting unit.
     *
     * @param name unit name
     */
    CountingActiveStateUnit(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLockedInactive() {
      lockedInactiveChecks++;
      return super.isLockedInactive();
    }

    /** {@inheritDoc} */
    @Override
    public boolean needRecalculation() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** Resets the inspection count. */
    void resetLockedInactiveChecks() {
      lockedInactiveChecks = 0;
    }

    /**
     * Returns the active-state inspection count.
     *
     * @return number of calls to {@link #isLockedInactive()}
     */
    int getLockedInactiveChecks() {
      return lockedInactiveChecks;
    }
  }

  /**
   * Stream that records how often a flowsheet-wide property setting is applied.
   */
  private static final class CountingStream extends Stream {
    private static final long serialVersionUID = 1000L;
    private int propertyInitLevelApplications;

    /**
     * Creates a stream that counts property-initialization setting applications.
     *
     * @param name stream name
     * @param fluid stream fluid
     */
    CountingStream(String name, SystemInterface fluid) {
      super(name, fluid);
    }

    /** {@inheritDoc} */
    @Override
    public void setPropertyInitLevel(PropertyInitLevel level) {
      super.setPropertyInitLevel(level);
      propertyInitLevelApplications++;
    }

    /**
     * Returns the number of property-initialization setting applications.
     *
     * @return application count
     */
    int getPropertyInitLevelApplications() {
      return propertyInitLevelApplications;
    }

    /**
     * Resets the application count.
     */
    void resetPropertyInitLevelApplications() {
      propertyInitLevelApplications = 0;
    }
  }

  /**
   * Creates a representative feed stream.
   *
   * @return feed stream
   */
  private CountingStream createFeed() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    CountingStream feed = new CountingStream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    return feed;
  }

  /**
   * Runs the process through the selected dispatcher and verifies a single preparation pass.
   *
   * @param optimizedExecution whether {@link ProcessSystem#run()} uses optimized dispatch
   */
  private void assertSinglePreparation(boolean optimizedExecution) {
    CountingStream feed = createFeed();
    ProcessSystem process = new ProcessSystem("preparation");
    process.setUseOptimizedExecution(optimizedExecution);
    process.add(feed);
    process.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);
    feed.resetPropertyInitLevelApplications();

    process.run();

    assertEquals(1, feed.getPropertyInitLevelApplications(),
        "run() must apply flowsheet settings once before concrete execution");
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, feed.getPropertyInitLevel());
    assertTrue(process.getRunStatus().isSuccess());
  }

  /**
   * Verifies optimized dispatch prepares the flowsheet once.
   */
  @Test
  void optimizedDispatcherPreparesFlowsheetOnce() {
    assertSinglePreparation(true);
  }

  /**
   * Verifies sequential dispatch prepares the flowsheet once.
   */
  @Test
  void sequentialDispatcherPreparesFlowsheetOnce() {
    assertSinglePreparation(false);
  }

  /** Verifies nested optimized dispatch does not repeat the outer active-state scan. */
  @Test
  void optimizedDispatcherChecksActiveStateOnce() {
    CountingActiveStateUnit unit = new CountingActiveStateUnit("stable unit");
    ProcessSystem process = new ProcessSystem("active-state preparation");
    process.add(unit);

    unit.resetLockedInactiveChecks();
    process.run();

    assertEquals(1, unit.getLockedInactiveChecks(),
        "run() must not repeat the active-state scan in its nested concrete dispatcher");
    assertTrue(process.getRunStatus().isSuccess());
  }

  /** Verifies a direct optimized-dispatch call retains its required active-state scan. */
  @Test
  void directOptimizedDispatcherChecksActiveStateOnce() {
    CountingActiveStateUnit unit = new CountingActiveStateUnit("stable unit");
    ProcessSystem process = new ProcessSystem("direct active-state preparation");
    process.add(unit);

    unit.resetLockedInactiveChecks();
    process.runOptimized(UUID.randomUUID());

    assertEquals(1, unit.getLockedInactiveChecks(),
        "a direct runOptimized() call must still prepare active state before dispatch");
  }

  /**
   * Verifies the retained outer active-state reset reactivates an auto-bypassed unit after its feed changes.
   */
  @Test
  void optimizedDispatcherReactivatesAutoBypassedUnitAfterFeedChange() {
    CountingStream feed = createFeed();
    feed.setFlowRate(0.0, "kg/hr");
    Heater heater = new Heater("heater", feed);
    heater.setOutletTemperature(310.0);
    heater.setMinimumFlow(1.0);
    ProcessSystem process = new ProcessSystem("low-flow-reactivation");
    process.setUseOptimizedExecution(true);
    process.add(feed);
    process.add(heater);

    process.run();
    assertFalse(heater.isActive());

    feed.setFlowRate(1000.0, "kg/hr");
    process.run();

    assertTrue(heater.isActive());
    assertTrue(heater.getOutletStream().getFlowRate("kg/hr") > 1.0);
  }
}
