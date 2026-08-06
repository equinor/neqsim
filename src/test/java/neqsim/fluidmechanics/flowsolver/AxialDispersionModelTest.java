package neqsim.fluidmechanics.flowsolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;

/** Tests the public Java/JPype-facing physical axial-dispersion API. */
class AxialDispersionModelTest {
  @Test
  void pipeUsesPureAdvectionByDefault() {
    PipeFlowSystem pipe = new PipeFlowSystem();

    assertSame(NoAxialDispersion.INSTANCE, pipe.getAxialDispersionModel());
    assertEquals("none", pipe.getAxialDispersionModel().getName());
    assertFalse(pipe.getAxialDispersionModel().isEnabled());
    assertEquals(0.0, pipe.getAxialDispersionModel().getCoefficientM2PerSecond(0, 100.0, 10.0, 1.0), 0.0);
  }

  @Test
  void pipeAcceptsValidatedConstantPhysicalCoefficient() {
    PipeFlowSystem pipe = new PipeFlowSystem();
    ConstantAxialDispersion model = new ConstantAxialDispersion(1.25);
    pipe.setAxialDispersionModel(model);

    assertSame(model, pipe.getAxialDispersionModel());
    assertEquals("constant", model.getName());
    assertTrue(model.isEnabled());
    assertEquals(1.25, model.getConstantCoefficientM2PerSecond(), 0.0);
    assertEquals(1.25, model.getCoefficientM2PerSecond(7, 25.0, 80.0, 3.0), 0.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.setAxialDispersionModel(null));
    assertThrows(IllegalArgumentException.class, () -> new ConstantAxialDispersion(-1.0));
    assertThrows(IllegalArgumentException.class, () -> new ConstantAxialDispersion(Double.NaN));
    assertFalse(new ConstantAxialDispersion(0.0).isEnabled());
  }

  @Test
  void pureAdvectionDefaultRetainsSingletonIdentityAfterSerialization() throws IOException, ClassNotFoundException {
    ByteArrayOutputStream serializedBytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(serializedBytes)) {
      output.writeObject(NoAxialDispersion.INSTANCE);
    }

    Object restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedBytes.toByteArray()))) {
      restored = input.readObject();
    }

    assertSame(NoAxialDispersion.INSTANCE, restored);
  }
}
