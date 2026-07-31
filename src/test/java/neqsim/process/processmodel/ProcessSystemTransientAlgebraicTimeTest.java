package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for physical-time ownership during repeated algebraic transient evaluations.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessSystemTransientAlgebraicTimeTest extends neqsim.NeqSimTest {
  /**
   * Semi-implicit evaluation may refine an algebraic recycle twice, but both evaluations belong to one physical
   * timestep.
   */
  @Test
  public void semiImplicitRecycleAdvancesClockOnce() {
    Recycle recycle = createRecycle(100.0);
    ProcessSystem process = new ProcessSystem("semi-implicit recycle");
    process.add(recycle.getStream(0));
    process.add(recycle);
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);

    UUID id = UUID.randomUUID();
    process.runTransient(2.0, id);

    assertEquals(2, recycle.getIterations());
    assertEquals(2.0, process.getTime(), 0.0);
    assertEquals(2.0, recycle.getTime(), 0.0);
    assertEquals(id, recycle.getCalculationIdentifier());
  }

  /**
   * Reusing one identifier refines the same step without moving time; a new identifier owns a new timestep.
   */
  @Test
  public void calculationIdentifierOwnsAlgebraicClockAdvance() {
    Recycle recycle = createRecycle(100.0);
    UUID firstStep = UUID.randomUUID();

    recycle.runTransient(2.0, firstStep);
    recycle.runTransient(2.0, firstStep);
    recycle.runTransient(2.0, UUID.randomUUID());

    assertEquals(3, recycle.getIterations());
    assertEquals(4.0, recycle.getTime(), 0.0);
  }

  /**
   * A normal low-flow return must still record completion so a second algebraic evaluation cannot advance the local
   * clock again.
   */
  @Test
  public void lowFlowRecycleRecordsTimestepIdentifier() {
    Recycle recycle = createRecycle(0.0);
    ProcessSystem process = new ProcessSystem("low-flow recycle");
    process.add(recycle.getStream(0));
    process.add(recycle);
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);

    UUID id = UUID.randomUUID();
    process.runTransient(2.0, id);

    assertEquals(2, recycle.getIterations());
    assertEquals(2.0, recycle.getTime(), 0.0);
    assertEquals(id, recycle.getCalculationIdentifier());
  }

  /**
   * Null identifiers retain the legacy behavior: every evaluation advances time and no identifier is written by the
   * shared transient boundary.
   */
  @Test
  public void nullIdentifierPreservesPreviousIdentifierAndClockBehavior() {
    Recycle recycle = createRecycle(0.0);
    UUID previousIdentifier = UUID.randomUUID();
    UUID previousOutletIdentifier = UUID.randomUUID();
    recycle.setCalculationIdentifier(previousIdentifier);
    recycle.getOutletStream().setCalculationIdentifier(previousOutletIdentifier);

    recycle.runTransient(2.0, null);
    recycle.runTransient(2.0, null);

    assertEquals(2, recycle.getIterations());
    assertEquals(4.0, recycle.getTime(), 0.0);
    assertEquals(previousIdentifier, recycle.getCalculationIdentifier());
    assertEquals(previousOutletIdentifier, recycle.getOutletStream().getCalculationIdentifier());
  }

  private static Recycle createRecycle(double flowRate) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);

    Stream inlet = new Stream("recycle inlet", fluid);
    inlet.setFlowRate(flowRate, "kg/hr");
    Stream outlet = inlet.clone("recycle outlet");

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(outlet);
    return recycle;
  }
}
