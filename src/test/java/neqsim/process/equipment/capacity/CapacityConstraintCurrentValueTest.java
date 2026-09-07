package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Regressions for explicit availability without changing the legacy zero-returning getter. */
class CapacityConstraintCurrentValueTest {
  @Test
  void explicitZeroIsDifferentFromTheLegacyUnsetDefault() {
    CapacityConstraint constraint = new CapacityConstraint("load");
    assertFalse(constraint.hasCurrentValue());
    assertEquals(0.0, constraint.getCurrentValue());
    assertFalse(constraint.hasCurrentValue());
    constraint.setCurrentValue(0.0);
    assertTrue(constraint.hasCurrentValue());
    assertEquals(0.0, constraint.getCurrentValue());
  }

  @Test
  void checkingAvailabilityDoesNotSampleAndCachedValueSurvivesSerialization() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CapacityConstraint constraint = new CapacityConstraint("load").setValueSupplier(() -> {
      calls.incrementAndGet();
      return 0.0;
    });
    assertTrue(constraint.hasCurrentValue());
    assertEquals(0, calls.get());
    assertEquals(0.0, constraint.getCurrentValue());
    assertEquals(1, calls.get());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(constraint);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      CapacityConstraint restored = (CapacityConstraint) input.readObject();
      assertTrue(restored.hasCurrentValue());
      assertEquals(0.0, restored.getCurrentValue());
    }
    assertEquals(1, calls.get());
  }
}
