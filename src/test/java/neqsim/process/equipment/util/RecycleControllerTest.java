package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for RecycleController with simultaneous modular solving.
 */
class RecycleControllerTest {

  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkEos(298.0, 50.0);
    testFluid.addComponent("methane", 0.8);
    testFluid.addComponent("ethane", 0.15);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");
  }

  @Nested
  @DisplayName("Basic RecycleController Tests")
  class BasicTests {
    private RecycleController controller;

    @BeforeEach
    void setUp() {
      controller = new RecycleController();
    }

    @Test
    @DisplayName("Initial state is correct")
    void testInitialState() {
      assertEquals(0, controller.getRecycleCount());
      assertFalse(controller.isUseCoordinatedAcceleration());
      assertNull(controller.getCoordinatedAccelerator());
    }

    @Test
    @DisplayName("Adding recycles works correctly")
    void testAddRecycle() {
      Recycle recycle1 = new Recycle("recycle1");
      Recycle recycle2 = new Recycle("recycle2");

      controller.addRecycle(recycle1);
      controller.addRecycle(recycle2);

      assertEquals(2, controller.getRecycleCount());
      List<Recycle> recycles = controller.getRecycles();
      assertTrue(recycles.contains(recycle1));
      assertTrue(recycles.contains(recycle2));
    }

    @Test
    @DisplayName("Setting coordinated acceleration creates accelerator")
    void testSetCoordinatedAcceleration() {
      controller.setUseCoordinatedAcceleration(true);

      assertTrue(controller.isUseCoordinatedAcceleration());
      assertNotNull(controller.getCoordinatedAccelerator());
    }

    @Test
    @DisplayName("Clear removes all recycles")
    void testClear() {
      Recycle recycle = new Recycle("recycle");
      controller.addRecycle(recycle);
      assertEquals(1, controller.getRecycleCount());

      controller.clear();
      assertEquals(0, controller.getRecycleCount());
    }

    @Test
    @DisplayName("Setting acceleration method for all recycles")
    void testSetAccelerationMethodAll() {
      Recycle recycle1 = new Recycle("recycle1");
      Recycle recycle2 = new Recycle("recycle2");
      controller.addRecycle(recycle1);
      controller.addRecycle(recycle2);

      controller.setAccelerationMethod(AccelerationMethod.BROYDEN);

      assertEquals(AccelerationMethod.BROYDEN, recycle1.getAccelerationMethod());
      assertEquals(AccelerationMethod.BROYDEN, recycle2.getAccelerationMethod());
    }
  }

  @Nested
  @DisplayName("Priority Level Tests")
  class PriorityTests {

    @Test
    @DisplayName("Priority levels are initialized correctly")
    void testPriorityLevels() {
      RecycleController controller = new RecycleController();

      Recycle recycle1 = new Recycle("low priority");
      recycle1.setPriority(50);

      Recycle recycle2 = new Recycle("high priority");
      recycle2.setPriority(100);

      controller.addRecycle(recycle1);
      controller.addRecycle(recycle2);
      controller.init();

      // Current priority should be the minimum
      assertEquals(50, controller.getCurrentPriorityLevel());
    }

    @Test
    @DisplayName("getRecyclesAtCurrentPriority returns correct subset")
    void testGetRecyclesAtCurrentPriority() {
      RecycleController controller = new RecycleController();

      Recycle recycle1 = new Recycle("priority 50");
      recycle1.setPriority(50);

      Recycle recycle2 = new Recycle("priority 100");
      recycle2.setPriority(100);

      Recycle recycle3 = new Recycle("priority 50 also");
      recycle3.setPriority(50);

      controller.addRecycle(recycle1);
      controller.addRecycle(recycle2);
      controller.addRecycle(recycle3);
      controller.init();

      List<Recycle> currentPriorityRecycles = controller.getRecyclesAtCurrentPriority();
      assertEquals(2, currentPriorityRecycles.size());
      assertTrue(currentPriorityRecycles.contains(recycle1));
      assertTrue(currentPriorityRecycles.contains(recycle3));
    }
  }

  @Nested
  @DisplayName("Simultaneous Modular Solving Tests")
  class SimultaneousSolvingTests {

    @Test
    @DisplayName("Convergence diagnostics provides useful information")
    void testConvergenceDiagnostics() {
      RecycleController controller = new RecycleController();

      // Create a properly configured recycle with streams
      Stream inletStream = new Stream("inlet", testFluid.clone());
      inletStream.setFlowRate(100.0, "kg/hr");
      inletStream.run();

      Stream outletStream = new Stream("outlet", testFluid.clone());
      outletStream.setFlowRate(100.0, "kg/hr");
      outletStream.run();

      Recycle recycle = new Recycle("test recycle");
      recycle.addStream(inletStream);
      recycle.setOutletStream(outletStream);
      recycle.setPriority(100);
      controller.addRecycle(recycle);
      controller.init();

      String diagnostics = controller.getConvergenceDiagnostics();

      assertNotNull(diagnostics);
      assertTrue(diagnostics.contains("RecycleController Diagnostics"));
      assertTrue(diagnostics.contains("Total recycles: 1"));
      assertTrue(diagnostics.contains("test recycle"));
    }

    @Test
    @DisplayName("getTotalIterations returns sum across all recycles")
    void testGetTotalIterations() {
      RecycleController controller = new RecycleController();

      // Create recycles - can't easily set iterations without running
      Recycle recycle1 = new Recycle("recycle1");
      Recycle recycle2 = new Recycle("recycle2");

      controller.addRecycle(recycle1);
      controller.addRecycle(recycle2);

      // Initially should be 0
      assertEquals(0, controller.getTotalIterations());
    }

    @Test
    @DisplayName("resetAll resets all recycles and accelerator")
    void testResetAll() {
      RecycleController controller = new RecycleController();

      Recycle recycle = new Recycle("recycle");
      controller.addRecycle(recycle);
      controller.setUseCoordinatedAcceleration(true);
      controller.init();

      controller.resetAll();

      assertEquals(0, controller.getTotalIterations());
    }
  }

  @Nested
  @DisplayName("Integration Tests with Process System")
  class IntegrationTests {

    @Test
    @DisplayName("RecycleController works with process system")
    void testWithProcessSystem() {
      ProcessSystem process = new ProcessSystem("Recycle Test");

      // Create a simple process with recycle
      Stream feed = new Stream("feed", testFluid.clone());
      feed.setFlowRate(1000.0, "kg/hr");
      feed.run();

      // Create a recycle inlet stream
      Stream recycleInlet = new Stream("recycle inlet", testFluid.clone());
      recycleInlet.setFlowRate(100.0, "kg/hr");
      recycleInlet.run();

      Mixer mixer = new Mixer("mixer");
      mixer.addStream(feed);
      mixer.addStream(recycleInlet);
      mixer.run();

      Heater heater = new Heater("heater", mixer.getOutletStream());
      heater.setOutTemperature(350.0, "K");
      heater.run();

      Separator separator = new Separator("separator", heater.getOutletStream());
      separator.run();

      Recycle recycle = new Recycle("main recycle");
      recycle.addStream(separator.getGasOutStream());
      recycle.setOutletStream(recycleInlet);
      recycle.setTolerance(1e-2);

      process.add(feed);
      process.add(recycleInlet);
      process.add(mixer);
      process.add(heater);
      process.add(separator);
      process.add(recycle);

      // The RecycleController is created internally by ProcessSystem
      // Just verify the process can be set up
      assertNotNull(process);
      assertEquals(6, process.getUnitOperations().size());
    }

    @Test
    @DisplayName("Coordinated acceleration can be enabled via RecycleController")
    void testCoordinatedAccelerationSetup() {
      RecycleController controller = new RecycleController();

      // Create two recycles at same priority
      Recycle recycle1 = new Recycle("recycle1");
      recycle1.setPriority(100);

      Recycle recycle2 = new Recycle("recycle2");
      recycle2.setPriority(100);

      controller.addRecycle(recycle1);
      controller.addRecycle(recycle2);
      controller.setUseCoordinatedAcceleration(true);
      controller.init();

      assertTrue(controller.isUseCoordinatedAcceleration());
      assertNotNull(controller.getCoordinatedAccelerator());

      // Both recycles should be at current priority
      List<Recycle> current = controller.getRecyclesAtCurrentPriority();
      assertEquals(2, current.size());
    }
  }
}
