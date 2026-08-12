package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import org.junit.jupiter.api.Test;

/** Public API regression for TwoFluidPipe bubble-size configuration. */
class BubbleSizeClosureConfigurationTest {

  @Test
  void publicPipeApiExposesCompatibleDefaultsAndExplicitLocalMode() {
    TwoFluidPipe pipe = new TwoFluidPipe("bubble-size configuration");

    assertEquals(0.02, pipe.getBubbleSurfaceTension(), 0.0);
    assertEquals(0.20, pipe.getMaximumBubbleDiameterFraction(), 0.0);
    assertFalse(pipe.isUseLocalBubbleSurfaceTension());

    pipe.setBubbleSurfaceTension(0.03);
    pipe.setMaximumBubbleDiameterFraction(0.15);
    pipe.setUseLocalBubbleSurfaceTension(true);

    assertEquals(0.03, pipe.getBubbleSurfaceTension(), 0.0);
    assertEquals(0.15, pipe.getMaximumBubbleDiameterFraction(), 0.0);
    assertTrue(pipe.isUseLocalBubbleSurfaceTension());
    assertTrue(pipe.getBubbleSizeClosure().isUseLocalSurfaceTension());
  }
}
