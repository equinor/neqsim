package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for automatic and capped transient sub-stepping in {@link ProcessSystem#runTransient(double, java.util.UUID)}.
 */
public class TransientSubsteppingTest extends neqsim.NeqSimTest {
  @Test
  public void largeStepsAreSubsteppedAndStayPhysical() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    assertTrue(process.isAutomaticTransientSubstepping());
    process.setTimeStep(100.0);

    for (int i = 0; i < 20; i++) {
      process.runTransient();
      double pressure = sheet.pressureBara();
      // The separator is fed through a valve from 9 bara and vents to 2 bara.
      assertTrue(pressure > 1.0 && pressure < 9.0, "pressure " + pressure + " bara at step " + i);
      double level = sheet.separator.getLiquidLevel();
      assertTrue(level > 0.0 && level < 1.0, "level " + level + " at step " + i);
    }
    assertTrue(process.getLastTransientSubstepCount() > 1);
    assertEquals(2000.0, process.getTime(), 1.0e-6);
    assertEquals(100.0, process.getTimeStep(), 0.0);
    assertEquals(20, process.getHistorySize(), "one history row per requested step");
    assertTrue(sheet.separator.getMaxStableTransientTimeStep() < 100.0);
    // A fixed 1 s step reference reaches 4.995 bara here; the pressure controller setpoint is 5 bara.
    assertEquals(4.995, sheet.pressureBara(), 0.05);
  }

  @Test
  public void largeStepsFailWithoutSubstepping() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    process.setAutomaticTransientSubstepping(false);
    process.setTimeStep(100.0);

    // A single 100 s explicit step overshoots the separator pressure above the 9 bara feed, after which the vessel
    // inventory has no VU solution.
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
      for (int i = 0; i < 5; i++) {
        process.runTransient();
      }
    });
    assertTrue(failure.getMessage().contains("rejected transient VU state"));
    assertEquals(1, process.getLastTransientSubstepCount());
  }

  @Test
  public void setpointStepThatOpensInletValveIsAbsorbed() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    process.setTimeStep(100.0);
    for (int i = 0; i < 10; i++) {
      process.runTransient();
    }
    double levelBefore = sheet.separator.getLiquidLevel();

    // The level controller can open the inlet valve to its limit within one sub-step chosen from the low-flow state;
    // such a sub-step is rolled back and retried with the stability limit of the high-flow state.
    sheet.levelController.setControllerSetPoint(0.7);
    for (int i = 0; i < 5; i++) {
      process.runTransient();
      double pressure = sheet.pressureBara();
      assertTrue(pressure > 1.0 && pressure < 9.0, "pressure " + pressure + " bara after setpoint step");
    }
    assertTrue(sheet.separator.getLiquidLevel() > levelBefore + 0.02);
  }

  @Test
  public void fixedSubstepCapSplitsStepEvenly() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    process.setAutomaticTransientSubstepping(false);
    process.setMaxTransientSubstep(2.0);
    assertFalse(process.isAutomaticTransientSubstepping());
    assertEquals(2.0, process.getMaxTransientSubstep(), 0.0);

    process.runTransient(10.0, java.util.UUID.randomUUID());

    assertEquals(5, process.getLastTransientSubstepCount());
    assertEquals(10.0, process.getTime(), 1.0e-9);
    assertEquals(10.0, process.getTimeStep(), 0.0);
    assertEquals(1, process.getHistorySize());

    process.setMaxTransientSubstep(0.0);
    assertEquals(Double.POSITIVE_INFINITY, process.getMaxTransientSubstep(), 0.0);
    process.runTransient(1.0, java.util.UUID.randomUUID());
    assertEquals(1, process.getLastTransientSubstepCount());
  }

  @Test
  public void substepCountIsBounded() {
    SeparatorControlFlowsheet sheet = new SeparatorControlFlowsheet();
    ProcessSystem process = sheet.process;
    process.setAutomaticTransientSubstepping(false);
    process.setMaxTransientSubstep(0.5);
    process.setMaxTransientSubstepsPerStep(4);
    assertEquals(4, process.getMaxTransientSubstepsPerStep());

    process.runTransient(10.0, java.util.UUID.randomUUID());

    assertEquals(4, process.getLastTransientSubstepCount());
    assertEquals(10.0, process.getTime(), 1.0e-9);
    process.setMaxTransientSubstepsPerStep(0);
    assertEquals(ProcessSystem.DEFAULT_MAX_TRANSIENT_SUBSTEPS, process.getMaxTransientSubstepsPerStep());
  }
}
