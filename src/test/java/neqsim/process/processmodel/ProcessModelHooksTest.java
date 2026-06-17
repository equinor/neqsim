package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.util.event.ProcessEvent;
import neqsim.process.util.event.ProcessEventBus;
import neqsim.process.util.event.ProcessEventListener;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessModel simulation lifecycle hooks: ModelProgressListener, ProcessEventBus wiring,
 * and auto-validation.
 *
 * @author ESOL
 * @version 1.0
 */
class ProcessModelHooksTest {

  /**
   * Builds a simple ProcessSystem: feed stream into a separator.
   *
   * @param name name for the process
   * @param feedName name for the feed stream
   * @return configured ProcessSystem
   */
  private ProcessSystem buildSimpleProcess(String name, String feedName) {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(feedName, fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Separator sep = new Separator(name + " sep", feed);

    ProcessSystem process = new ProcessSystem(name);
    process.add(feed);
    process.add(sep);
    return process;
  }

  /**
   * Test that model-level lifecycle hooks fire in correct order during run().
   */
  @Test
  void testModelLifecycleHooksFired() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    model.add("area2", buildSimpleProcess("Area2", "feed2"));

    List<String> events = new ArrayList<>();

    model.setProgressListener(new ProcessModel.ModelProgressListener() {
      @Override
      public void onModelStart(int totalAreas) {
        events.add("MODEL_START:" + totalAreas);
      }

      @Override
      public void onBeforeIteration(int iterationNumber) {
        events.add("BEFORE_ITER:" + iterationNumber);
      }

      @Override
      public void onBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("BEFORE_AREA:" + areaName + ":" + iterationNumber);
      }

      @Override
      public void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("AFTER_AREA:" + areaName + ":" + iterationNumber);
      }

      @Override
      public void onIterationComplete(int iterationNumber, boolean converged, double maxError) {
        events.add("AFTER_ITER:" + iterationNumber + ":converged=" + converged);
      }

      @Override
      public void onModelComplete(int totalIterations, boolean converged) {
        events.add("MODEL_COMPLETE:" + totalIterations + ":converged=" + converged);
      }
    });

    model.run();

    // Model start should be first
    assertTrue(events.get(0).startsWith("MODEL_START:2"),
        "First event should be MODEL_START with 2 areas");

    // Before-iteration should fire
    assertTrue(events.contains("BEFORE_ITER:1"), "Should have before-iteration for iter 1");

    // Before-area and after-area should fire for both areas
    assertTrue(events.stream().anyMatch(e -> e.startsWith("BEFORE_AREA:area1:")),
        "Should have before-area for area1");
    assertTrue(events.stream().anyMatch(e -> e.startsWith("BEFORE_AREA:area2:")),
        "Should have before-area for area2");
    assertTrue(events.stream().anyMatch(e -> e.startsWith("AFTER_AREA:area1:")),
        "Should have after-area for area1");
    assertTrue(events.stream().anyMatch(e -> e.startsWith("AFTER_AREA:area2:")),
        "Should have after-area for area2");

    // Model complete should be last
    String lastEvent = events.get(events.size() - 1);
    assertTrue(lastEvent.startsWith("MODEL_COMPLETE:"),
        "Last event should be MODEL_COMPLETE, got: " + lastEvent);
  }

  /**
   * Test that before-area fires before after-area for each process area.
   */
  @Test
  void testBeforeAreaFiresBeforeAfterArea() {
    ProcessModel model = new ProcessModel();
    model.add("upstream", buildSimpleProcess("Upstream", "feed_up"));

    List<String> events = new ArrayList<>();

    model.setProgressListener(new ProcessModel.ModelProgressListener() {
      @Override
      public void onBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("BEFORE:" + areaName);
      }

      @Override
      public void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("AFTER:" + areaName);
      }
    });

    model.run();

    int beforeIdx = events.indexOf("BEFORE:upstream");
    int afterIdx = events.indexOf("AFTER:upstream");
    assertTrue(beforeIdx >= 0, "Should have BEFORE:upstream");
    assertTrue(afterIdx >= 0, "Should have AFTER:upstream");
    assertTrue(beforeIdx < afterIdx, "BEFORE:upstream should fire before AFTER:upstream");
  }

  /**
   * Test that ProcessEventBus receives events when publishEvents is enabled on ProcessModel.
   */
  @Test
  void testEventBusReceivesEventsWhenEnabled() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    model.setPublishEvents(true);

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
      model.run();

      assertFalse(captured.isEmpty(), "Should have captured events via event bus");
      assertTrue(
          captured.stream()
              .anyMatch(e -> e.getType() == ProcessEvent.EventType.SIMULATION_COMPLETE),
          "Should have a SIMULATION_COMPLETE event from ProcessModel");
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
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    assertFalse(model.isPublishEvents(), "publishEvents should default to false");

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> captured = new ArrayList<>();
    ProcessEventListener listener = new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        // Filter to ProcessModel events only (ignore any from child ProcessSystem)
        if ("ProcessModel".equals(event.getSource())) {
          captured.add(event);
        }
      }
    };

    bus.subscribe(listener);
    try {
      model.run();

      assertTrue(captured.isEmpty(),
          "Should not have ProcessModel events when publishEvents is disabled");
    } finally {
      bus.unsubscribe(listener);
      bus.clearHistory();
    }
  }

  /**
   * Test that auto-validation runs without errors on a valid model.
   */
  @Test
  void testAutoValidateRunsOnValidModel() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    model.setAutoValidate(true);

    model.run();
    assertTrue(model.isAutoValidate(), "autoValidate should remain true");
    assertTrue(model.isModelConverged(), "Model should converge");
  }

  /**
   * Test that auto-validation with publishEvents publishes warning events for invalid processes.
   */
  @Test
  void testAutoValidateWithEventsEnabled() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    model.setAutoValidate(true);
    model.setPublishEvents(true);

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> captured = new ArrayList<>();
    bus.subscribe(new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        captured.add(event);
      }
    });

    model.run();

    // Should have completed successfully with at least start and complete events
    assertTrue(
        captured.stream().anyMatch(e -> e.getType() == ProcessEvent.EventType.SIMULATION_COMPLETE),
        "Should have SIMULATION_COMPLETE event");

    bus.clearHistory();
  }

  /**
   * Test that step mode also fires model hooks.
   */
  @Test
  void testStepModeFiresHooks() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    model.setRunStep(true);

    List<String> events = new ArrayList<>();

    model.setProgressListener(new ProcessModel.ModelProgressListener() {
      @Override
      public void onModelStart(int totalAreas) {
        events.add("MODEL_START:" + totalAreas);
      }

      @Override
      public void onBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("BEFORE_AREA:" + areaName);
      }

      @Override
      public void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("AFTER_AREA:" + areaName);
      }

      @Override
      public void onModelComplete(int totalIterations, boolean converged) {
        events.add("MODEL_COMPLETE:" + totalIterations);
      }
    });

    model.run();

    assertTrue(events.get(0).startsWith("MODEL_START:"), "Step mode should fire MODEL_START");
    assertTrue(events.stream().anyMatch(e -> e.startsWith("BEFORE_AREA:area1")),
        "Step mode should fire BEFORE_AREA");
    assertTrue(events.stream().anyMatch(e -> e.startsWith("AFTER_AREA:area1")),
        "Step mode should fire AFTER_AREA");
    assertTrue(events.stream().anyMatch(e -> e.startsWith("MODEL_COMPLETE:")),
        "Step mode should fire MODEL_COMPLETE");
  }

  /**
   * Test backward compatibility — default listener methods are no-ops.
   */
  @Test
  void testBackwardCompatibilityWithMinimalListener() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));

    List<String> events = new ArrayList<>();

    // Only implement the required method — all new hooks have defaults
    model.setProgressListener(new ProcessModel.ModelProgressListener() {
      @Override
      public void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("COMPLETE:" + areaName);
      }
    });

    model.run();

    assertTrue(events.stream().anyMatch(e -> e.startsWith("COMPLETE:area1")),
        "Should have completion event for area1");
  }

  /**
   * Test publishEvents and autoValidate setters/getters.
   */
  @Test
  void testFlagSettersAndGetters() {
    ProcessModel model = new ProcessModel();

    assertFalse(model.isPublishEvents(), "publishEvents default should be false");
    assertFalse(model.isAutoValidate(), "autoValidate default should be false");

    model.setPublishEvents(true);
    assertTrue(model.isPublishEvents(), "publishEvents should be true after set");

    model.setAutoValidate(true);
    assertTrue(model.isAutoValidate(), "autoValidate should be true after set");

    model.setPublishEvents(false);
    assertFalse(model.isPublishEvents(), "publishEvents should be false after reset");
  }

  /**
   * Test that event bus receives type-specific events from ProcessModel.
   */
  @Test
  void testEventBusTypeSpecificSubscription() {
    ProcessModel model = new ProcessModel();
    model.add("area1", buildSimpleProcess("Area1", "feed1"));
    model.setPublishEvents(true);

    ProcessEventBus bus = ProcessEventBus.getInstance();
    bus.clearHistory();

    List<ProcessEvent> completionEvents = new ArrayList<>();
    ProcessEventListener listener = new ProcessEventListener() {
      @Override
      public void onEvent(ProcessEvent event) {
        completionEvents.add(event);
      }
    };

    bus.subscribe(ProcessEvent.EventType.SIMULATION_COMPLETE, listener);
    try {
      model.run();

      assertFalse(completionEvents.isEmpty(), "Should have SIMULATION_COMPLETE events");
      for (ProcessEvent event : completionEvents) {
        assertEquals(ProcessEvent.EventType.SIMULATION_COMPLETE, event.getType(),
            "All captured events should be SIMULATION_COMPLETE");
      }
    } finally {
      bus.unsubscribe(ProcessEvent.EventType.SIMULATION_COMPLETE, listener);
      bus.clearHistory();
    }
  }
}
