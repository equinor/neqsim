package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

/** Serialization regression for the stiff bubble-drag configuration. */
class StiffBubbleDragSerializationTest {

  @Test
  void stiffBubbleDragSettingSurvivesSerialization() throws Exception {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    assertFalse(equations.isStiffBubbleDragEnabled());
    equations.setEnableStiffBubbleDrag(true);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(equations);
    output.close();

    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    TwoFluidConservationEquations copy = (TwoFluidConservationEquations) input.readObject();
    input.close();

    assertTrue(copy.isStiffBubbleDragEnabled());
    assertTrue(copy.getInterfacialFriction().isUseCorrectedBubbleDrag());
    copy.setEnableStiffBubbleDrag(false);
    assertFalse(copy.isStiffBubbleDragEnabled());
  }
}
