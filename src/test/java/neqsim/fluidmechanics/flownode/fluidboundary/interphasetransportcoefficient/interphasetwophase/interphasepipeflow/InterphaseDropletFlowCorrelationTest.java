package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Fast equation-level checks for the droplet transfer correlations. */
public class InterphaseDropletFlowCorrelationTest {
  @Test
  void testRanzMarshallSherwoodReferenceValue() {
    InterphaseDropletFlow model = new InterphaseDropletFlow();
    double reynoldsNumber = 100.0;
    double schmidtNumber = 0.70;
    double expected = 2.0 + 0.6 * Math.sqrt(reynoldsNumber) * Math.pow(schmidtNumber, 0.33);
    assertEquals(expected, model.calcSherwoodNumber(0, reynoldsNumber, schmidtNumber, null), 1.0e-12);
  }

  @Test
  void testRanzMarshallNusseltReferenceValue() {
    InterphaseDropletFlow model = new InterphaseDropletFlow();
    double reynoldsNumber = 40.0;
    double prandtlNumber = 0.72;
    double expected = 2.0 + 0.6 * Math.sqrt(reynoldsNumber) * Math.pow(prandtlNumber, 0.33);
    assertEquals(expected, model.calcNusseltNumber(0, reynoldsNumber, prandtlNumber, null), 1.0e-12);
  }

  @Test
  void testAbramzonSirignanoReferenceAndLimit() {
    InterphaseDropletFlow model = new InterphaseDropletFlow();
    assertEquals(1.0, model.calcAbramzonSirignanoF(0.0), 0.0);
    assertEquals(Math.pow(2.0, 0.7) * Math.log(2.0), model.calcAbramzonSirignanoF(1.0), 1.0e-12);
    assertTrue(model.calcAbramzonSirignanoF(5.0) > model.calcAbramzonSirignanoF(1.0));
  }
}
