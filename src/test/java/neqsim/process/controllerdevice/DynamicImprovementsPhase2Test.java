package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for Phase 2 dynamic simulation improvements: 2-DOF PID, valve stick-slip/hysteresis, SFC
 * sequencer, separator internals, dynamic heat exchanger, dynamic distillation, semi-implicit
 * integration, adaptive timestep, and parallel transient execution.
 */
class DynamicImprovementsPhase2Test {

  // ─── 2-DOF PID (setpoint weight) ───

  @Test
  void testSetpointWeightDefault() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-2dof");
    Assertions.assertEquals(1.0, pid.getSetpointWeight(), 1e-10);
  }

  @Test
  void testSetpointWeightClamped() {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass("PID-2dof2");
    pid.setSetpointWeight(0.5);
    Assertions.assertEquals(0.5, pid.getSetpointWeight(), 1e-10);
    pid.setSetpointWeight(-0.1);
    Assertions.assertEquals(0.0, pid.getSetpointWeight(), 1e-10);
    pid.setSetpointWeight(1.5);
    Assertions.assertEquals(1.0, pid.getSetpointWeight(), 1e-10);
  }

  @Test
  void testSetpointWeightZeroEliminatesProportionalKick() {
    ControllerDeviceBaseClass pid1 = new ControllerDeviceBaseClass("PID-b1");
    ControllerDeviceBaseClass pid2 = new ControllerDeviceBaseClass("PID-b0");

    // Use a stub transmitter
    DynamicImprovementsTest.StubTransmitter tx1 = new DynamicImprovementsTest.StubTransmitter("T1");
    DynamicImprovementsTest.StubTransmitter tx2 = new DynamicImprovementsTest.StubTransmitter("T2");
    tx1.setValue(50.0);
    tx2.setValue(50.0);

    pid1.setTransmitter(tx1);
    pid2.setTransmitter(tx2);
    pid1.setControllerSetPoint(50.0, "C");
    pid2.setControllerSetPoint(50.0, "C");
    pid1.setControllerParameters(2.0, 300.0, 0.0);
    pid2.setControllerParameters(2.0, 300.0, 0.0);

    pid1.setSetpointWeight(1.0); // standard PID
    pid2.setSetpointWeight(0.0); // no proportional action on SP change

    // Initial step at error=0
    pid1.runTransient(50.0, 1.0);
    pid2.runTransient(50.0, 1.0);

    // Step change in setpoint
    pid1.setControllerSetPoint(60.0, "C");
    pid2.setControllerSetPoint(60.0, "C");
    pid1.runTransient(50.0, 1.0);
    pid2.runTransient(50.0, 1.0);

    double resp1 = pid1.getResponse();
    double resp2 = pid2.getResponse();
    // With b=0, proportional kick from SP change is eliminated, so response should differ
    // b=1 should show larger immediate change than b=0
    Assertions.assertNotEquals(resp1, resp2, 0.001,
        "Setpoint weight should affect proportional response");
  }

  // ─── Valve stick-slip/hysteresis ───

  @Test
  void testValveDeadbandDefault() {
    ThrottlingValve valve = new ThrottlingValve("V-DB", createTestStream());
    Assertions.assertEquals(0.0, valve.getValveDeadband(), 1e-10);
    Assertions.assertEquals(0.0, valve.getValveStiction(), 1e-10);
    Assertions.assertEquals(0.0, valve.getValveHysteresis(), 1e-10);
  }

  @Test
  void testValveStictionSet() {
    ThrottlingValve valve = new ThrottlingValve("V-ST", createTestStream());
    valve.setValveStiction(5.0);
    Assertions.assertEquals(5.0, valve.getValveStiction(), 1e-10);
  }

  @Test
  void testValveHysteresisSet() {
    ThrottlingValve valve = new ThrottlingValve("V-HY", createTestStream());
    valve.setValveHysteresis(3.0);
    Assertions.assertEquals(3.0, valve.getValveHysteresis(), 1e-10);
  }

  @Test
  void testValveDeadbandSet() {
    ThrottlingValve valve = new ThrottlingValve("V-DB2", createTestStream());
    valve.setValveDeadband(2.0);
    Assertions.assertEquals(2.0, valve.getValveDeadband(), 1e-10);
  }

  // ─── Sequential Function Chart ───

  @Test
  void testSFCBasicSequence() {
    SequentialFunctionChart sfc = new SequentialFunctionChart("SFC-1");

    final boolean[] stepActions = {false, false, false};

    SequentialFunctionChart.SfcStep stepA = new SequentialFunctionChart.SfcStep("StepA");
    stepA.setEntryAction(new Runnable() {
      @Override
      public void run() {
        stepActions[0] = true;
      }
    });
    SequentialFunctionChart.SfcStep stepB = new SequentialFunctionChart.SfcStep("StepB");
    stepB.setEntryAction(new Runnable() {
      @Override
      public void run() {
        stepActions[1] = true;
      }
    });
    SequentialFunctionChart.SfcStep stepC = new SequentialFunctionChart.SfcStep("StepC");
    stepC.setEntryAction(new Runnable() {
      @Override
      public void run() {
        stepActions[2] = true;
      }
    });

    sfc.addStep(stepA);
    sfc.addStep(stepB);
    sfc.addStep(stepC);

    // Always-true guard transitions
    java.util.function.BooleanSupplier alwaysTrue = new java.util.function.BooleanSupplier() {
      @Override
      public boolean getAsBoolean() {
        return true;
      }
    };
    sfc.addTransition("StepA", "StepB", alwaysTrue);
    sfc.addTransition("StepB", "StepC", alwaysTrue);

    sfc.setInitialStep("StepA");
    sfc.start();
    Assertions.assertEquals("StepA", sfc.getActiveStepName());
    Assertions.assertTrue(stepActions[0], "StepA entry should have fired");

    sfc.runStep(1.0); // should transition to StepB
    Assertions.assertEquals("StepB", sfc.getActiveStepName());
    Assertions.assertTrue(stepActions[1], "StepB entry should have fired");

    sfc.runStep(1.0); // should transition to StepC
    Assertions.assertEquals("StepC", sfc.getActiveStepName());
    Assertions.assertTrue(stepActions[2], "StepC entry should have fired");
  }

  @Test
  void testSFCTimedTransition() {
    SequentialFunctionChart sfc = new SequentialFunctionChart("SFC-2");
    SequentialFunctionChart.SfcStep waitStep = new SequentialFunctionChart.SfcStep("WaitStep");
    SequentialFunctionChart.SfcStep doneStep = new SequentialFunctionChart.SfcStep("DoneStep");
    sfc.addStep(waitStep);
    sfc.addStep(doneStep);

    sfc.addTimedTransition("WaitStep", "DoneStep", 5.0);
    sfc.setInitialStep("WaitStep");
    sfc.start();

    sfc.runStep(2.0);
    Assertions.assertEquals("WaitStep", sfc.getActiveStepName(), "Should still be waiting");

    sfc.runStep(2.0);
    Assertions.assertEquals("WaitStep", sfc.getActiveStepName(), "Still waiting at t=4");

    sfc.runStep(2.0);
    Assertions.assertEquals("DoneStep", sfc.getActiveStepName(), "Should have transitioned at t=6");
  }

  // ─── Separator internals ───

  @Test
  void testSeparatorWeirDefaults() {
    Separator sep = new Separator("Sep-1");
    Assertions.assertEquals(0.0, sep.getWeirHeight(), 1e-10);
    Assertions.assertEquals(0.0, sep.getWeirLength(), 1e-10);
    Assertions.assertEquals(0.0, sep.getBootVolume(), 1e-10);
    Assertions.assertEquals(0.0, sep.getMistEliminatorDpCoeff(), 1e-10);
    Assertions.assertEquals(0.0, sep.getMistEliminatorThickness(), 1e-10);
  }

  @Test
  void testSeparatorWeirSetters() {
    Separator sep = new Separator("Sep-2");
    sep.setWeirHeight(0.3);
    sep.setWeirLength(1.5);
    sep.setBootVolume(2.0);
    sep.setMistEliminatorDpCoeff(100.0);
    sep.setMistEliminatorThickness(0.15);

    Assertions.assertEquals(0.3, sep.getWeirHeight(), 1e-10);
    Assertions.assertEquals(1.5, sep.getWeirLength(), 1e-10);
    Assertions.assertEquals(2.0, sep.getBootVolume(), 1e-10);
    Assertions.assertEquals(100.0, sep.getMistEliminatorDpCoeff(), 1e-10);
    Assertions.assertEquals(0.15, sep.getMistEliminatorThickness(), 1e-10);
  }

  @Test
  void testSeparatorNegativeWeirClamped() {
    Separator sep = new Separator("Sep-3");
    sep.setWeirHeight(-0.1);
    Assertions.assertEquals(0.0, sep.getWeirHeight(), 1e-10);
  }

  // ─── Dynamic HeatExchanger ───

  @Test
  void testHeatExchangerDynamicFieldsDefault() {
    HeatExchanger hx = new HeatExchanger("HX-1");
    Assertions.assertEquals(0.0, hx.getWallMass(), 1e-10);
    Assertions.assertEquals(500.0, hx.getWallCp(), 1e-10);
    Assertions.assertTrue(Double.isNaN(hx.getWallTemperature()));
    Assertions.assertEquals(0.0, hx.getHeatTransferArea(), 1e-10);
    Assertions.assertFalse(hx.isDynamicModelEnabled());
  }

  @Test
  void testHeatExchangerDynamicFieldsSetters() {
    HeatExchanger hx = new HeatExchanger("HX-2");
    hx.setWallMass(500.0);
    hx.setWallCp(480.0);
    hx.setWallTemperature(350.0);
    hx.setShellHoldupVolume(0.5);
    hx.setTubeHoldupVolume(0.3);
    hx.setShellSideHtc(800.0);
    hx.setTubeSideHtc(1200.0);
    hx.setHeatTransferArea(50.0);
    hx.setDynamicModelEnabled(true);

    Assertions.assertEquals(500.0, hx.getWallMass(), 1e-10);
    Assertions.assertEquals(480.0, hx.getWallCp(), 1e-10);
    Assertions.assertEquals(350.0, hx.getWallTemperature(), 1e-10);
    Assertions.assertEquals(0.5, hx.getShellHoldupVolume(), 1e-10);
    Assertions.assertEquals(0.3, hx.getTubeHoldupVolume(), 1e-10);
    Assertions.assertEquals(800.0, hx.getShellSideHtc(), 1e-10);
    Assertions.assertEquals(1200.0, hx.getTubeSideHtc(), 1e-10);
    Assertions.assertEquals(50.0, hx.getHeatTransferArea(), 1e-10);
    Assertions.assertTrue(hx.isDynamicModelEnabled());
  }

  @Test
  void testHeatExchangerNonNegativeClamping() {
    HeatExchanger hx = new HeatExchanger("HX-3");
    hx.setWallMass(-10.0);
    hx.setShellHoldupVolume(-1.0);
    hx.setTubeHoldupVolume(-1.0);
    hx.setHeatTransferArea(-5.0);
    Assertions.assertEquals(0.0, hx.getWallMass(), 1e-10);
    Assertions.assertEquals(0.0, hx.getShellHoldupVolume(), 1e-10);
    Assertions.assertEquals(0.0, hx.getTubeHoldupVolume(), 1e-10);
    Assertions.assertEquals(0.0, hx.getHeatTransferArea(), 1e-10);
  }

  // ─── Dynamic Distillation Column ───

  @Test
  void testDistillationColumnDynamicDefaults() {
    DistillationColumn col = new DistillationColumn("Col-1", 5, true, true);
    Assertions.assertFalse(col.isDynamicColumnEnabled());
    Assertions.assertEquals(0.05, col.getTrayWeirHeight(), 1e-10);
    Assertions.assertEquals(1.0, col.getTrayWeirLength(), 1e-10);
    Assertions.assertNull(col.getTrayLiquidHoldup());
  }

  @Test
  void testDistillationColumnDynamicSetters() {
    DistillationColumn col = new DistillationColumn("Col-2", 5, true, true);
    col.setDynamicColumnEnabled(true);
    col.setTrayWeirHeight(0.08);
    col.setTrayWeirLength(2.0);

    Assertions.assertTrue(col.isDynamicColumnEnabled());
    Assertions.assertEquals(0.08, col.getTrayWeirHeight(), 1e-10);
    Assertions.assertEquals(2.0, col.getTrayWeirLength(), 1e-10);
  }

  // ─── ProcessSystem Integration Method ───

  @Test
  void testProcessSystemIntegrationMethodDefault() {
    ProcessSystem process = new ProcessSystem();
    Assertions.assertEquals(ProcessSystem.IntegrationMethod.EXPLICIT_EULER,
        process.getIntegrationMethod());
  }

  @Test
  void testProcessSystemSemiImplicitSet() {
    ProcessSystem process = new ProcessSystem();
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);
    Assertions.assertEquals(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT,
        process.getIntegrationMethod());
  }

  // ─── Adaptive Timestep ───

  @Test
  void testAdaptiveTimestepDefaults() {
    ProcessSystem process = new ProcessSystem();
    Assertions.assertFalse(process.isAdaptiveTimestepEnabled());
    Assertions.assertEquals(0.001, process.getMinTimestep(), 1e-10);
    Assertions.assertEquals(10.0, process.getMaxTimestep(), 1e-10);
    Assertions.assertEquals(0.01, process.getAdaptiveTimestepTolerance(), 1e-10);
  }

  @Test
  void testAdaptiveTimestepSetters() {
    ProcessSystem process = new ProcessSystem();
    process.setAdaptiveTimestepEnabled(true);
    process.setMinTimestep(0.01);
    process.setMaxTimestep(5.0);
    process.setAdaptiveTimestepTolerance(0.005);

    Assertions.assertTrue(process.isAdaptiveTimestepEnabled());
    Assertions.assertEquals(0.01, process.getMinTimestep(), 1e-10);
    Assertions.assertEquals(5.0, process.getMaxTimestep(), 1e-10);
    Assertions.assertEquals(0.005, process.getAdaptiveTimestepTolerance(), 1e-10);
  }

  // ─── Parallel Transient ───

  @Test
  void testParallelTransientDefaults() {
    ProcessSystem process = new ProcessSystem();
    Assertions.assertFalse(process.isParallelTransientEnabled());
    Assertions.assertTrue(process.getTransientThreadPoolSize() >= 1);
  }

  @Test
  void testParallelTransientSetters() {
    ProcessSystem process = new ProcessSystem();
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(4);
    Assertions.assertTrue(process.isParallelTransientEnabled());
    Assertions.assertEquals(4, process.getTransientThreadPoolSize());
  }

  // ─── Integration Test: Dynamic HX with Process ───

  @Test
  void testDynamicHeatExchangerTransient() {
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 80.0, 10.0);
    hotFluid.addComponent("methane", 1.0);
    hotFluid.setMixingRule("classic");

    SystemInterface coldFluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    coldFluid.addComponent("methane", 1.0);
    coldFluid.setMixingRule("classic");

    Stream hotStream = new Stream("hot", hotFluid);
    hotStream.setFlowRate(1000.0, "kg/hr");
    hotStream.run();

    Stream coldStream = new Stream("cold", coldFluid);
    coldStream.setFlowRate(1000.0, "kg/hr");
    coldStream.run();

    HeatExchanger hx = new HeatExchanger("HX-dyn", hotStream, coldStream);
    hx.setWallMass(200.0);
    hx.setWallCp(500.0);
    hx.setHeatTransferArea(20.0);
    hx.setShellSideHtc(500.0);
    hx.setTubeSideHtc(800.0);
    hx.setDynamicModelEnabled(true);

    // Run initial steady state to set up streams
    hx.run();

    // Run a few dynamic steps
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      hx.runTransient(1.0, id);
    }

    // Wall temperature should be initialized and between hot and cold inlet temps
    double wallT = hx.getWallTemperature();
    Assertions.assertFalse(Double.isNaN(wallT), "Wall temperature should be initialized");
    Assertions.assertTrue(wallT > 273.15 + 20.0 && wallT < 273.15 + 80.0,
        "Wall T should be between inlet temps, got " + (wallT - 273.15) + " C");
  }

  // ─── Integration Test: Semi-implicit with process ───

  @Test
  void testSemiImplicitTransient() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");

    Separator sep = new Separator("sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);
    UUID id = UUID.randomUUID();
    // Should complete without errors
    process.runTransient(0.5, id);
    process.runTransient(0.5, id);
    Assertions.assertTrue(process.getTime() > 0);
  }

  // ─── Integration Test: Parallel transient ───

  @Test
  void testParallelTransientExecution() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");

    Separator sep = new Separator("sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);

    UUID id = UUID.randomUUID();
    // Should complete without errors
    process.runTransient(0.5, id);
    process.runTransient(0.5, id);
    Assertions.assertTrue(process.getTime() > 0);
  }

  // ─── Helper methods ───

  private static Stream createTestStream() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("test-stream", fluid);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    return stream;
  }
}
