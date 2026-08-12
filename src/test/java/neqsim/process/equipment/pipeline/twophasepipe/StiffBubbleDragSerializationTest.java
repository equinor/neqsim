package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    equations.getBubbleSizeClosure().setSurfaceTension(0.035);
    equations.getBubbleSizeClosure().setMaximumPipeDiameterFraction(0.15);
    equations.getBubbleSizeClosure().setUseLocalSurfaceTension(true);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(equations);
    output.close();

    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    TwoFluidConservationEquations copy = (TwoFluidConservationEquations) input.readObject();
    input.close();

    assertTrue(copy.isStiffBubbleDragEnabled());
    assertTrue(copy.getInterfacialFriction().isUseCorrectedBubbleDrag());
    assertEquals(0.035, copy.getBubbleSizeClosure().getSurfaceTension(), 0.0);
    assertEquals(0.15, copy.getBubbleSizeClosure().getMaximumPipeDiameterFraction(), 0.0);
    assertTrue(copy.getBubbleSizeClosure().isUseLocalSurfaceTension());
    copy.setEnableStiffBubbleDrag(false);
    assertFalse(copy.isStiffBubbleDragEnabled());
  }
}
