package neqsim.thermodynamicoperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Protects legacy phase-envelope entry points used by Java and reflective clients.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PhaseEnvelopeCompatibilityTest {
  /**
   * Verifies that each deprecated public alias delegates to the standard calculation.
   *
   * @param methodName legacy public method name
   * @throws ReflectiveOperationException if the compatibility method is missing or cannot be invoked
   */
  @ParameterizedTest
  @ValueSource(strings = {"calcPTphaseEnvelope2", "calcPTphaseEnvelopeNew"})
  void legacyEntryPointDelegates(String methodName) throws ReflectiveOperationException {
    Method method = ThermodynamicOperations.class.getMethod(methodName);
    assertEquals(void.class, method.getReturnType());
    assertTrue(method.isAnnotationPresent(Deprecated.class));
    RecordingOperations operations = new RecordingOperations();
    method.invoke(operations);
    assertEquals(1, operations.envelopeCalls);
  }

  /**
   * Records dispatch without running an unrelated numerical solver.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class RecordingOperations extends ThermodynamicOperations {
    private static final long serialVersionUID = 1000L;
    private int envelopeCalls;

    /** {@inheritDoc} */
    @Override
    public void calcPTphaseEnvelope() {
      envelopeCalls++;
    }
  }
}
