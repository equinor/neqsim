package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public-data qualification of refinery-assay bulk density bookkeeping. */
public class OilAssayCharacterisationOediCoaTest {
  private static final double PUBLISHED_WHOLE_CRUDE_SPECIFIC_GRAVITY = 0.779;
  private static final double PUBLISHED_WHOLE_CRUDE_API_GRAVITY = 50.1;

  @Test
  public void testTurnerValleySample920BulkDensityAndApiGravity() {
    SystemInterface system = new SystemSrkEos(288.706, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();

    assay.addCut(new AssayCut("GasolineNaphtha").withVolumePercent(70.5).withSpecificGravity(0.754));
    assay.addCut(new AssayCut("Kerosene").withVolumePercent(13.2).withSpecificGravity(0.813));
    assay.addCut(new AssayCut("GasOil").withVolumePercent(3.7).withSpecificGravity(0.831));
    assay.addCut(new AssayCut("Residuum").withVolumePercent(12.6).withSpecificGravity(0.889));

    double expectedAdditiveVolumeSpecificGravity = 0.705 * 0.754 + 0.132 * 0.813 + 0.037 * 0.831 + 0.126 * 0.889;
    assertEquals(1.0, 0.705 + 0.132 + 0.037 + 0.126, 1e-12);
    assertEquals(0.781647, expectedAdditiveVolumeSpecificGravity, 1e-12);
    assertEquals(expectedAdditiveVolumeSpecificGravity, assay.getBulkSpecificGravity(), 1e-12);
    assertEquals(PUBLISHED_WHOLE_CRUDE_SPECIFIC_GRAVITY, assay.getBulkSpecificGravity(), 0.004);
    assertEquals(PUBLISHED_WHOLE_CRUDE_API_GRAVITY, assay.getBulkApiGravity(), 0.7);
    assertEquals(0, system.getNumberOfComponents());

    OilAssayCharacterisation reversed = new SystemSrkEos(288.706, 1.01325).getOilAssayCharacterisation();
    reversed.clearCuts();
    reversed.addCut(new AssayCut("Residuum").withVolumePercent(12.6).withSpecificGravity(0.889));
    reversed.addCut(new AssayCut("GasOil").withVolumePercent(3.7).withSpecificGravity(0.831));
    reversed.addCut(new AssayCut("Kerosene").withVolumePercent(13.2).withSpecificGravity(0.813));
    reversed.addCut(new AssayCut("GasolineNaphtha").withVolumePercent(70.5).withSpecificGravity(0.754));
    assertEquals(assay.getBulkSpecificGravity(), reversed.getBulkSpecificGravity(), 1e-12);
  }
}
