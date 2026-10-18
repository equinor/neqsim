package neqsim.process.mechanicaldesign.designstandards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for scrubber dimensional process requirements.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GasScrubberLookupTest extends neqsim.NeqSimTest {
  /** Clearance lengths in mm cannot overwrite the dimensionless design flow factor. */
  @Test
  void scrubberLengthsAndFlowFactorUseTheirOwnColumns() {
    GasScrubberDesignStandard standard = new GasScrubberDesignStandard("Statoil_TR1414", null);
    assertEquals(550.0, standard.lengthGasInletToMeshpad, 1e-12);
    assertEquals(500.0, standard.lengthGasInetToHHLL, 1e-12);
    assertEquals(550.0, standard.lengthMeshPadToDemistingCyclone, 1e-12);
    assertEquals(1.1, standard.getVolumetricDesignFactor(), 1e-12);
  }
}
