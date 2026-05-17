package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for simulation progress streaming and callback functionality.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessSystemProgressCallbackTest extends neqsim.NeqSimTest {

  private ProcessSystem process;
  private SystemSrkEos fluid;

  @BeforeEach
  public void setUp() {
    // Create a simple test fluid
    fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    // Build a simple process
    process = new ProcessSystem("Test Process");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Separator separator = new Separator("Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0);
    process.add(compressor);

    Cooler cooler = new Cooler("Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(25.0, "C");
    process.add(cooler);
  }

  /**
   * Test that runWithCallback invokes callback for each unit operation.
   */
  @Test
  public void testRunWithCallbackInvokesForEachUnit() {
    List<String> completedUnits = new ArrayList<>();

    Consumer<ProcessEquipmentInterface> callback = unit -> {
      completedUnits.add(unit.getName());
    };

    process.runWithCallback(callback);

    // Should have 4 units: Feed, Separator, Compressor, Cooler
    assertEquals(4, completedUnits.size(), "Should invoke callback for all 4 units");
    assertTrue(completedUnits.contains("Feed"), "Should include Feed");
    assertTrue(completedUnits.contains("Separator"), "Should include Separator");
    assertTrue(completedUnits.contains("Compressor"), "Should include Compressor");
    assertTrue(completedUnits.contains("Cooler"), "Should include Cooler");
  }

  /**
   * Test that runWithCallback works with null callback (should not throw).
   */
  @Test
  public void testRunWithNullCallbackDoesNotThrow() {
    // Should complete without throwing
    process.runWithCallback(null);

    // Verify simulation completed by checking outlet
    Cooler cooler = (Cooler) process.getUnit("Cooler");
    assertTrue(cooler.getOutletStream().getTemperature("C") < 30.0,
        "Cooler should have reduced temperature");
  }

  /**
   * Test the full SimulationProgressListener interface.
   */
  @Test
  public void testSimulationProgressListener() {
    AtomicInteger unitCompleteCount = new AtomicInteger(0);
    AtomicInteger iterationCompleteCount = new AtomicInteger(0);
    List<Integer> receivedIndices = new ArrayList<>();

    ProcessSystem.SimulationProgressListener listener =
        new ProcessSystem.SimulationProgressListener() {
          @Override
          public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
              int iterationNumber) {
            unitCompleteCount.incrementAndGet();
            receivedIndices.add(unitIndex);
          }

          @Override
          public void onIterationComplete(int iterationNumber, boolean converged,
              double recycleError) {
            iterationCompleteCount.incrementAndGet();
          }
        };

    process.setProgressListener(listener);
    process.runWithProgress(UUID.randomUUID());

    // Should have called onUnitComplete for each unit
    assertEquals(4, unitCompleteCount.get(), "Should call onUnitComplete 4 times");

    // Should have called onIterationComplete at least once
    assertTrue(iterationCompleteCount.get() >= 1, "Should call onIterationComplete at least once");

    // Indices should be sequential
    for (int i = 0; i < receivedIndices.size(); i++) {
      assertEquals(i, receivedIndices.get(i), "Indices should be sequential");
    }
  }

  /**
   * Test that listener receives correct total units count.
   */
  @Test
  public void testListenerReceivesTotalUnitsCount() {
    AtomicInteger receivedTotal = new AtomicInteger(0);

    ProcessSystem.SimulationProgressListener listener =
        new ProcessSystem.SimulationProgressListener() {
          @Override
          public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
              int iterationNumber) {
            receivedTotal.set(totalUnits);
          }
        };

    process.setProgressListener(listener);
    process.runWithProgress(UUID.randomUUID());

    assertEquals(4, receivedTotal.get(), "Should report 4 total units");
  }

  /**
   * Test that getProgressListener returns the set listener.
   */
  @Test
  public void testGetProgressListenerReturnsSetListener() {
    ProcessSystem.SimulationProgressListener listener =
        new ProcessSystem.SimulationProgressListener() {
          @Override
          public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
              int iterationNumber) {
            // No-op
          }
        };

    process.setProgressListener(listener);

    assertEquals(listener, process.getProgressListener(),
        "getProgressListener should return the set listener");
  }

  /**
   * Test that setting listener to null disables callbacks.
   */
  @Test
  public void testSetNullListenerDisablesCallbacks() {
    AtomicInteger callCount = new AtomicInteger(0);

    ProcessSystem.SimulationProgressListener listener =
        new ProcessSystem.SimulationProgressListener() {
          @Override
          public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
              int iterationNumber) {
            callCount.incrementAndGet();
          }
        };

    // Set then clear listener
    process.setProgressListener(listener);
    process.setProgressListener(null);

    process.runWithProgress(UUID.randomUUID());

    assertEquals(0, callCount.get(), "Should not call listener after setting to null");
  }

  /**
   * Test callback with ProcessModule.
   */
  @Test
  public void testProcessModuleCallback() {
    // Create a process module containing a process system
    ProcessModule module = new ProcessModule("Test Module");

    ProcessSystem subProcess = new ProcessSystem("Sub Process");

    SystemSrkEos subFluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    subFluid.addComponent("methane", 0.90);
    subFluid.addComponent("ethane", 0.10);
    subFluid.setMixingRule("classic");

    Stream subFeed = new Stream("Sub Feed", subFluid);
    subFeed.setFlowRate(5000.0, "kg/hr");
    subFeed.setTemperature(25.0, "C");
    subFeed.setPressure(60.0, "bara");
    subProcess.add(subFeed);

    Compressor subComp = new Compressor("Sub Compressor", subFeed);
    subComp.setOutletPressure(100.0);
    subProcess.add(subComp);

    module.add(subProcess);

    // Track callbacks
    List<String> moduleCallbacks = new ArrayList<>();

    Consumer<ProcessEquipmentInterface> callback = unit -> {
      moduleCallbacks.add(unit.getName());
    };

    module.runWithCallback(callback);

    // Should have received callbacks for sub-process units
    assertTrue(moduleCallbacks.size() >= 2, "Should receive callbacks from module units");
  }

  /**
   * Test that callbacks receive units in execution order.
   */
  @Test
  public void testCallbacksInExecutionOrder() {
    List<String> executionOrder = new ArrayList<>();

    Consumer<ProcessEquipmentInterface> callback = unit -> {
      executionOrder.add(unit.getName());
    };

    process.runWithCallback(callback);

    // Feed should be first, then downstream units
    assertEquals("Feed", executionOrder.get(0), "Feed should be first");

    // Cooler should be last (after Compressor)
    int compIndex = executionOrder.indexOf("Compressor");
    int coolerIndex = executionOrder.indexOf("Cooler");
    assertTrue(coolerIndex > compIndex, "Cooler should come after Compressor");
  }

  /**
   * Test error handling in listener.
   */
  @Test
  public void testListenerErrorDoesNotStopSimulation() {
    AtomicInteger callCount = new AtomicInteger(0);

    ProcessSystem.SimulationProgressListener faultyListener =
        new ProcessSystem.SimulationProgressListener() {
          @Override
          public void onUnitComplete(ProcessEquipmentInterface unit, int unitIndex, int totalUnits,
              int iterationNumber) {
            callCount.incrementAndGet();
            if (callCount.get() == 2) {
              throw new RuntimeException("Simulated listener error");
            }
          }
        };

    process.setProgressListener(faultyListener);

    // Should complete despite listener error
    process.runWithProgress(UUID.randomUUID());

    // Should have continued after error
    assertTrue(callCount.get() >= 3,
        "Should continue calling listener after error: got " + callCount.get());
  }
}
