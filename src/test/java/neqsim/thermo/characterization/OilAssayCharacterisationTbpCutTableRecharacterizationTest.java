package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests direct recharacterization from immutable refinery TBP cut tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpCutTableRecharacterizationTest {
  /** Tests re-ingestion, pseudo-component generation, and exact assay-mass closure. */
  @Test
  void recharacterizesRelumpedTableOnAnotherSystem() {
    OilAssayCharacterisation source = assay(new SystemSrkEos(298.15, 1.01325));
    source.addCut(massCut("cut1", 0.07 / 0.90, 0.70, 300.0, 400.0));
    source.addCut(massCut("cut2", 0.16 / 0.90, 0.80, 400.0, 500.0));
    source.addCut(massCut("cut3", 0.27 / 0.90, 0.90, 500.0, 600.0));
    source.addCut(massCut("cut4", 0.40 / 0.90, 1.00, 600.0, 700.0));
    TbpCutTable relumped = source.exportTbpCutTable().relumpAdjacentCuts(2, 2);

    SystemInterface targetSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation target = assay(targetSystem);
    target.setTotalAssayMass(120.0);
    target.addTBPCutTable("Relumped", relumped);

    assertArrayEquals(relumped.getCumulativeVolumePercent(), target.exportTbpCutTable().getCumulativeVolumePercent(),
        1.0e-12);
    assertArrayEquals(relumped.getBoilingPointKelvin(), target.exportTbpCutTable().getBoilingPointKelvin(), 0.0);
    assertArrayEquals(relumped.getSpecificGravity(), target.exportTbpCutTable().getSpecificGravity(), 0.0);

    target.apply();

    assertEquals(2, targetSystem.getNumberOfComponents());
    assertEquals(120.0, componentMass(targetSystem.getComponent("Relumped1_PC"))
        + componentMass(targetSystem.getComponent("Relumped2_PC")), 1.0e-10);
    assertEquals(23.0 / 30.0, targetSystem.getComponent("Relumped1_PC").getNormalLiquidDensity(), 1.0e-12);
    assertEquals(67.0 / 70.0, targetSystem.getComponent("Relumped2_PC").getNormalLiquidDensity(), 1.0e-12);
  }

  /** Tests fail-closed null and invalid-prefix handling without partial assay mutation. */
  @Test
  void rejectsInvalidRecharacterizationInputsAtomically() {
    OilAssayCharacterisation target = assay(new SystemSrkEos(298.15, 1.01325));
    assertThrows(IllegalArgumentException.class, () -> target.addTBPCutTable("Invalid", null));
    assertEquals(0, target.getCuts().size());

    OilAssayCharacterisation source = assay(new SystemSrkEos(298.15, 1.01325));
    source.addCut(massCut("source", 1.0, 0.85, 350.0, 450.0));
    TbpCutTable table = source.exportTbpCutTable();

    assertThrows(IllegalArgumentException.class, () -> target.addTBPCutTable("", table));
    assertEquals(0, target.getCuts().size());
  }

  /**
   * Return the refinery-assay helper attached to one thermodynamic system.
   *
   * @param system thermodynamic system
   * @return empty attached assay helper
   */
  private static OilAssayCharacterisation assay(SystemInterface system) {
    return system.getOilAssayCharacterisation();
  }

  /**
   * Create one mass-basis assay cut.
   *
   * @param name cut name
   * @param massFraction mass fraction
   * @param specificGravity dimensionless specific gravity
   * @param lowerKelvin lower TBP boundary in K
   * @param upperKelvin upper TBP boundary in K
   * @return configured assay cut
   */
  private static AssayCut massCut(String name, double massFraction, double specificGravity, double lowerKelvin,
      double upperKelvin) {
    return new AssayCut(name).withMassFraction(massFraction).withSpecificGravity(specificGravity)
        .withBoilingRangeKelvin(lowerKelvin, upperKelvin);
  }

  /**
   * Calculate one generated component's represented mass.
   *
   * @param component generated petroleum pseudo-component
   * @return represented mass in kg
   */
  private static double componentMass(ComponentInterface component) {
    return component.getNumberOfmoles() * component.getMolarMass();
  }
}
