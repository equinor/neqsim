package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for finite-positive transient timestep validation. */
public class ProcessSystemTransientTimestepValidationTest extends neqsim.NeqSimTest {
  private static final double[] INVALID_TIMESTEPS = new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY,
      Double.NEGATIVE_INFINITY };

  /**
   * Invalid direct process steps must fail before clocks, identifiers, or algebraic equipment state change.
   */
  @Test
  public void processSystemRejectsInvalidTimestepsBeforeMutation() {
    for (double invalidTimestep : INVALID_TIMESTEPS) {
      Recycle recycle = createRecycle();
      ProcessSystem process = createProcess("invalid process step", recycle);
      UUID previousIdentifier = UUID.randomUUID();
      recycle.setCalculationIdentifier(previousIdentifier);
      double configuredTimestep = process.getTimeStep();

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
          () -> process.runTransient(invalidTimestep, UUID.randomUUID()));

      assertTrue(exception.getMessage().contains("finite and greater than zero"));
      assertEquals(0.0, process.getTime(), 0.0);
      assertEquals(configuredTimestep, process.getTimeStep(), 0.0);
      assertEquals(0, recycle.getIterations());
      assertEquals(0.0, recycle.getTime(), 0.0);
      assertEquals(previousIdentifier, recycle.getCalculationIdentifier());
    }
  }

  /**
   * Adaptive stepping must reject invalid requests rather than silently clamping them to the minimum timestep.
   */
  @Test
  public void adaptiveTransientRejectsInvalidTimestepsBeforeMutation() {
    for (double invalidTimestep : INVALID_TIMESTEPS) {
      Recycle recycle = createRecycle();
      ProcessSystem process = createProcess("invalid adaptive step", recycle);
      process.setAdaptiveTimestepEnabled(true);

      assertThrows(IllegalArgumentException.class,
          () -> process.runTransientAdaptive(invalidTimestep, UUID.randomUUID()));

      assertEquals(0.0, process.getTime(), 0.0);
      assertEquals(0, recycle.getIterations());
      assertEquals(0.0, recycle.getTime(), 0.0);
    }
  }

  /**
   * Model-level validation must happen before the first area advances, preserving atomic preflight across areas.
   */
  @Test
  public void processModelRejectsInvalidTimestepsBeforeAnyAreaAdvances() {
    for (double invalidTimestep : INVALID_TIMESTEPS) {
      Recycle firstRecycle = createRecycle();
      Recycle secondRecycle = createRecycle();
      ProcessSystem firstArea = createProcess("first area", firstRecycle);
      ProcessSystem secondArea = createProcess("second area", secondRecycle);
      ProcessModel model = new ProcessModel();
      model.add("first", firstArea);
      model.add("second", secondArea);

      assertThrows(IllegalArgumentException.class, () -> model.runTransient(invalidTimestep, UUID.randomUUID()));

      assertEquals(0.0, firstArea.getTime(), 0.0);
      assertEquals(0.0, secondArea.getTime(), 0.0);
      assertEquals(0, firstRecycle.getIterations());
      assertEquals(0, secondRecycle.getIterations());
    }
  }

  /**
   * Nearby valid steps retain normal process and equipment time evolution.
   */
  @Test
  public void validTimestepsAdvanceProcessAndEquipmentState() {
    Recycle recycle = createRecycle();
    ProcessSystem process = createProcess("valid steps", recycle);

    process.runTransient(0.25, UUID.randomUUID());
    process.runTransient(0.50, UUID.randomUUID());

    assertEquals(0.75, process.getTime(), 0.0);
    assertEquals(0.75, recycle.getTime(), 0.0);
    assertEquals(2, recycle.getIterations());
  }

  private static ProcessSystem createProcess(String name, Recycle recycle) {
    ProcessSystem process = new ProcessSystem(name);
    process.add(recycle.getStream(0));
    process.add(recycle);
    return process;
  }

  private static Recycle createRecycle() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);

    Stream inlet = new Stream("recycle inlet", fluid);
    inlet.setFlowRate(100.0, "kg/hr");
    Stream outlet = inlet.clone("recycle outlet");

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(outlet);
    return recycle;
  }
}
