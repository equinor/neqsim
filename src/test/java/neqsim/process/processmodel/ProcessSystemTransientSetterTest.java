package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.util.Setter;

/**
 * Regression tests for setter execution during transient process steps.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessSystemTransientSetterTest extends neqsim.NeqSimTest {
  /**
   * Setter that records how often its specification phase is applied.
   */
  private static final class CountingSetter extends Setter {
    private static final long serialVersionUID = 1000L;
    private final AtomicInteger executionCount = new AtomicInteger();

    /**
     * Creates a counting setter.
     *
     * @param name setter name
     */
    private CountingSetter(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      executionCount.incrementAndGet();
      setCalculationIdentifier(id);
    }

    /**
     * Returns the number of applied specification phases.
     *
     * @return execution count
     */
    private int getExecutionCount() {
      return executionCount.get();
    }
  }

  /**
   * A setter is a pre-step specification phase and must execute exactly once per explicit transient timestep.
   */
  @Test
  public void explicitTransientAppliesSetterOnce() {
    ProcessSystem process = new ProcessSystem("explicit-setter-step");
    CountingSetter setter = new CountingSetter("setter");
    process.add(setter);

    process.runTransient(2.0, UUID.randomUUID());
    process.runTransient(2.0, UUID.randomUUID());

    assertEquals(2, setter.getExecutionCount());
    assertEquals(4.0, setter.getTime(), 1.0e-12);
  }

  /**
   * The semi-implicit second equipment pass must not reapply timestep setters.
   */
  @Test
  public void semiImplicitTransientAppliesSetterOnce() {
    ProcessSystem process = new ProcessSystem("semi-implicit-setter-step");
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);
    CountingSetter setter = new CountingSetter("setter");
    process.add(setter);

    process.runTransient(2.0, UUID.randomUUID());

    assertEquals(1, setter.getExecutionCount());
    assertEquals(2.0, setter.getTime(), 1.0e-12);
  }

  /**
   * Parallel equipment scheduling must not reapply setters, including the semi-implicit second pass.
   */
  @Test
  public void parallelSemiImplicitTransientAppliesEachSetterOnce() {
    ProcessSystem process = new ProcessSystem("parallel-semi-implicit-setter-step");
    process.setParallelTransientEnabled(true);
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);
    CountingSetter firstSetter = new CountingSetter("first setter");
    CountingSetter secondSetter = new CountingSetter("second setter");
    process.add(firstSetter);
    process.add(secondSetter);

    process.runTransient(2.0, UUID.randomUUID());

    assertEquals(1, firstSetter.getExecutionCount());
    assertEquals(1, secondSetter.getExecutionCount());
    assertEquals(2.0, firstSetter.getTime(), 1.0e-12);
    assertEquals(2.0, secondSetter.getTime(), 1.0e-12);
  }
}
