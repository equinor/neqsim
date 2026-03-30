package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.util.event.ProcessEvent;
import neqsim.process.util.event.ProcessEventBus;
import neqsim.process.util.event.ProcessEventListener;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for simulation lifecycle hooks: expanded SimulationProgressListener, ProcessEventBus
 * wiring, and auto-validation.
 *
 * @author ESOL
 * @version 1.0
 */
class SimulationHooksTest {

  /**
   * Builds a simple test process: feed stream into a separator.
   *
   * @return configured ProcessSystem
   */
  private ProcessSystem buildSimpleProcess() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Separator sep = new Separator("HP sep", feed);

    ProcessSystem process = new ProcessSystem("Test Process");
    process.add(feed);
    process.add(sep);
    return process;
  }

  /**
   * Test that the new before-hooks fire in the correct order during runWithProgress().
   */
  @Test
  void testBeforeHooksFiredInRunWithProgress() {
    ProcessSystem process = buildSimpleProcess();

    List<String> events = new ArrayList<>();

    process.setProgressListener(new ProcessSystem.SimulationProgressListener() {
      @Override
      public void onSimulationStart(int totalUnits) {
        events.add("SIM_START:" + totalUnits);
      }

      @Override
      public void onBeforeIteration(int iterationNumber) {
        events.add("BEFORE_ITER:" + iterationNumber);
      }

      @Override
      public void onBeforeUnit(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
          int iterationNumber) {
        events.add("BEFORE_UNIT:" + unit.getName());
      }

      @Override
      public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
          int iterationNumber) {
        events.add("AFTER_UNIT:" + unit.getName());
      }

      @Override
      public void onIterationComplete(int iterationNumber, boolean converged, double recycleError) {
        events.add("AFTER_ITER:" + iterationNumber + ":converged=" + converged);
      }

      @Override
      public void onSimulationComplete(int totalIterations, boolean converged) {
        events.add("SIM_COMPLETE:" + totalIterations + ":converged=" + converged);
      }
    });

    process.runWithProgress(java.util.UUID.randomUUID());

    // Verify the simulation started
    assertTrue(events.get(0).startsWith("SIM_START:"), "First event should be SIM_START");

    // Verify before-iteration fires
    assertTrue(events.contains("BEFORE_ITER:1"), "Should have before-iteration hook for iter 1");

    // Verify before-unit fires for both feed and separator
    assertTrue(events.stream().anyMatch(e -> e.equals("BEFORE_UNIT:feed")),
        "Should have before-unit for feed");
    assertTrue(events.stream().anyMatch(e -> e.equals("BEFORE_UNIT:HP sep")),
        "Should have before-unit for separator");

    // Verify after-unit fires
    assertTrue(events.stream().anyMatch(e -> e.equals("AFTER_UNIT:feed")),
        "Should have after-unit for feed");
    assertTrue(events.stream().anyMatch(e -> e.equals("AFTER_UNIT:HP sep")),
        "Should have after-unit for separator");

    // Verify simulation completed
    String lastEvent = events.get(events.size() - 1);
    assertTrue(lastEvent.startsWith("SIM_COMPLETE:"), "Last event should be SIM_COMPLETE");
    assertTrue(lastEvent.contains("converged=true"), "Should have converged");
  }

  /**
   * Test that before-unit fires before after-unit for each equipment.
   */
  @Test
  void testBeforeUnitFiresBeforeAfterUnit() {
    ProcessSystem process = buildSimpleProcess();

    List<String> events = new ArrayList<>();

    process.setProgressListener(new ProcessSystem.SimulationProgressListener() {
      @Override
      public void onBeforeUnit(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
          int iterationNumber) {
        events.add("BEFORE:" + unit.getName());
      }

      @Override
      public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
          int iterationNumber) {
        events.add("AFTER:" + unit.getName());
      }
    });

    process.runWithProgress(java.util.UUID.randomUUID());

    // For each unit, BEFORE should appear before AFTER
    int beforeFeedIdx = events.indexOf("BEFORE:feed");
    int afterFeedIdx = events.indexOf("AFTER:feed");
    assertTrue(beforeFeedIdx >= 0, "Should have BEFORE:feed");
    assertTrue(afterFeedIdx >= 0, "Should have AFTER:feed");
    assertTrue(beforeFeedIdx < afterFeedIdx, "BEFORE:feed should be before AFTER:feed");

    int beforeSepIdx = events.indexOf("BEFORE:HP sep");
    int afterSepIdx = events.indexOf("AFTER:HP sep");
    assertTrue(beforeSepIdx >= 0, "Should have BEFORE:HP sep");
    assertTrue(afterSepIdx >= 0, "Should have AFTER:HP sep");
    assertTrue(beforeSepIdx < afterSepIdx, "BEFORE:HP sep should be before AFTER:HP sep");
  }

  /**
   * Test that ProcessEventBus receives events when publishEvents is enabled.
   */
  @Test
  void testEventBusReceivesEventsWhenEnabled() {
    ProcessSystem process = buildSimpleProcess();
    process.setPublishEvents(true);

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> captured = new ArrayList<>();
    ProcessEventListener listener = new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        captured.add(event);
      }
    };

    bus.subscribe(listener);
    try {
      process.run();

      // Should have received at least start and complete events
      assertFalse(captured.isEmpty(), "Should have captured events via event bus");
      assertTrue(
          captured.stream()
              .anyMatch(e -> e.getType() == ProcessEvent.EventType.SIMULATION_COMPLETE),
          "Should have a SIMULATION_COMPLETE event");
    } finally {
      bus.unsubscribe(listener);
      bus.clearHistory();
    }
  }

  /**
   * Test that ProcessEventBus does NOT receive events when publishEvents is disabled (default).
   */
  @Test
  void testEventBusNoEventsWhenDisabled() {
    ProcessSystem process = buildSimpleProcess();
    // publishEvents is false by default
    assertFalse(process.isPublishEvents(), "publishEvents should be false by default");

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> captured = new ArrayList<>();
    ProcessEventListener listener = new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        captured.add(event);
      }
    };

    bus.subscribe(listener);
    try {
      process.run();

      // Should NOT have received events (publishEvents is off)
      assertTrue(captured.isEmpty(), "Should not capture events when publishEvents is disabled");
    } finally {
      bus.unsubscribe(listener);
      bus.clearHistory();
    }
  }

  /**
   * Test that auto-validation runs without errors on a valid process.
   */
  @Test
  void testAutoValidateRunsOnValidProcess() {
    ProcessSystem process = buildSimpleProcess();
    process.setAutoValidate(true);

    // Should run without issues — validation passes for properly configured equipment
    process.run();
    assertTrue(process.isAutoValidate(), "autoValidate should remain true");
  }

  /**
   * Test that runWithProgress with auto-validation enabled still converges.
   */
  @Test
  void testAutoValidateWithRunWithProgress() {
    ProcessSystem process = buildSimpleProcess();
    process.setAutoValidate(true);
    process.setPublishEvents(true);

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> captured = new ArrayList<>();
    bus.subscribe(new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        captured.add(event);
      }
    });

    process.runWithProgress(java.util.UUID.randomUUID());

    // Should have completed successfully
    assertTrue(
        captured.stream().anyMatch(e -> e.getType() == ProcessEvent.EventType.SIMULATION_COMPLETE),
        "Should have a SIMULATION_COMPLETE event");

    bus.clearHistory();
  }

  /**
   * Test event bus type-specific subscription.
   */
  @Test
  void testEventBusTypeSpecificSubscription() {
    ProcessSystem process = buildSimpleProcess();
    process.setPublishEvents(true);

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> completionEvents = new ArrayList<>();
    ProcessEventListener listener = new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        completionEvents.add(event);
      }
    };

    // Subscribe only to SIMULATION_COMPLETE events
    bus.subscribe(ProcessEvent.EventType.SIMULATION_COMPLETE, listener);
    try {
      process.run();

      // Should only have SIMULATION_COMPLETE events
      assertFalse(completionEvents.isEmpty(), "Should have captured SIMULATION_COMPLETE events");
      for (ProcessEvent event : completionEvents) {
        assertEquals(ProcessEvent.EventType.SIMULATION_COMPLETE, event.getType(),
            "All captured events should be SIMULATION_COMPLETE");
      }
    } finally {
      bus.unsubscribe(ProcessEvent.EventType.SIMULATION_COMPLETE, listener);
      bus.clearHistory();
    }
  }

  /**
   * Test that the default listener interface methods are backward-compatible (no-op defaults).
   */
  @Test
  void testBackwardCompatibilityWithMinimalListener() {
    ProcessSystem process = buildSimpleProcess();

    List<String> events = new ArrayList<>();

    // Only implement the required method — all new hooks should have defaults
    process.setProgressListener(new ProcessSystem.SimulationProgressListener() {
      @Override
      public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
          int iterationNumber) {
        events.add("COMPLETE:" + unit.getName());
      }
    });

    // Should run without error even without implementing new hooks
    process.runWithProgress(java.util.UUID.randomUUID());

    assertTrue(events.size() >= 2, "Should have completion events for feed and separator");
  }

  /**
   * Test publishEvents and autoValidate setters/getters.
   */
  @Test
  void testFlagSettersAndGetters() {
    ProcessSystem process = new ProcessSystem("Test");

    assertFalse(process.isPublishEvents(), "publishEvents default should be false");
    assertFalse(process.isAutoValidate(), "autoValidate default should be false");

    process.setPublishEvents(true);
    assertTrue(process.isPublishEvents(), "publishEvents should be true after set");

    process.setAutoValidate(true);
    assertTrue(process.isAutoValidate(), "autoValidate should be true after set");

    process.setPublishEvents(false);
    assertFalse(process.isPublishEvents(), "publishEvents should be false after reset");
  }
}
