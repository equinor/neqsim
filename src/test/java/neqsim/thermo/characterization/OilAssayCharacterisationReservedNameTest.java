package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OilAssayCharacterisationReservedNameTest {
  @Test
  public void testReservedPseudoComponentMarkerInCutNameIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("Naphtha_PC_alias"));
  }

  @Test
  public void testReservedPseudoComponentMarkerInTbpPrefixIsRejectedBeforeMutation() {
    SystemInterface system = new SystemSrkEos(298.15, 10.0);
    OilAssayCharacterisation characterisation = system.getOilAssayCharacterisation();
    characterisation.clearCuts();

    assertThrows(IllegalArgumentException.class,
        () -> characterisation.addTBPCutBoundariesCelsius("TBP_PC", new double[] {0.0, 100.0},
            new double[] {90.0, 520.0}, new double[] {0.85}));
    assertTrue(characterisation.getCuts().isEmpty());
  }
}
