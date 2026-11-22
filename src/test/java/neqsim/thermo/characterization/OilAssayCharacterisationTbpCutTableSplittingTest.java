package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests conservative splitting of immutable refinery TBP cut tables. */
class OilAssayCharacterisationTbpCutTableSplittingTest {
  /** Tests linear volume-yield splitting, mass closure, relumping, and recharacterization. */
  @Test
  void splitsIntervalsAndPreservesQualifiedBookkeepingBasis() {
    TbpCutTable source = sourceTable();
    TbpCutTable split = source.splitAtBoilingPointsKelvin(350.0, 450.0, 600.0);

    assertEquals(6, split.getCutCount());
    assertArrayEquals(new double[] {0.0, 10.0, 20.0, 45.0, 70.0, 85.0, 100.0}, split.getCumulativeVolumePercent(),
        1.0e-12);
    assertArrayEquals(new double[] {300.0, 350.0, 400.0, 450.0, 500.0, 600.0, 700.0}, split.getBoilingPointKelvin(),
        0.0);
    assertArrayEquals(new double[] {0.70, 0.70, 0.80, 0.80, 1.00, 1.00}, split.getSpecificGravity(), 0.0);
    assertEquals(impliedMass(source), impliedMass(split), 1.0e-12);

    TbpCutTable relumped = split.relumpAdjacentCuts(2, 2, 2);
    assertArrayEquals(source.getCumulativeVolumePercent(), relumped.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(source.getBoilingPointKelvin(), relumped.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(source.getSpecificGravity(), relumped.getSpecificGravity(), 1.0e-12);

    SystemInterface targetSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation target = targetSystem.getOilAssayCharacterisation();
    target.setTotalAssayMass(150.0);
    target.addTBPCutTable("Split", split);
    target.apply();

    assertEquals(6, targetSystem.getNumberOfComponents());
    double representedMass = 0.0;
    for (int i = 1; i <= split.getCutCount(); i++) {
      representedMass += componentMass(targetSystem.getComponent("Split" + i + "_PC"));
    }
    assertEquals(150.0, representedMass, 1.0e-10);
  }

  /** Tests the Celsius convenience API and fail-closed split-boundary validation. */
  @Test
  void rejectsInvalidSplitBoundariesWithoutChangingSourceTable() {
    TbpCutTable source = sourceTable();
    double[] sourceBoundaries = source.getBoilingPointKelvin();
    double[] sourceYields = source.getCumulativeVolumePercent();

    TbpCutTable celsiusSplit = source.splitAtBoilingPointsCelsius(76.85);
    assertArrayEquals(new double[] {300.0, 350.0, 400.0, 500.0, 700.0}, celsiusSplit.getBoilingPointKelvin(), 1.0e-12);

    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin((double[]) null));
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin());
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin(300.0));
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin(700.0));
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin(400.0));
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin(450.0, 350.0));
    assertThrows(IllegalArgumentException.class, () -> source.splitAtBoilingPointsKelvin(350.0, 350.0));

    assertArrayEquals(sourceBoundaries, source.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(sourceYields, source.getCumulativeVolumePercent(), 0.0);
  }

  private static TbpCutTable sourceTable() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin("Source", new double[] {0.0, 20.0, 70.0, 100.0},
        new double[] {300.0, 400.0, 500.0, 700.0}, new double[] {0.70, 0.80, 1.00});
    return assay.exportTbpCutTable();
  }

  private static double impliedMass(TbpCutTable table) {
    double[] cumulativeVolumePercent = table.getCumulativeVolumePercent();
    double[] specificGravity = table.getSpecificGravity();
    double impliedMass = 0.0;
    for (int i = 0; i < specificGravity.length; i++) {
      impliedMass += (cumulativeVolumePercent[i + 1] - cumulativeVolumePercent[i]) * specificGravity[i];
    }
    return impliedMass;
  }

  private static double componentMass(ComponentInterface component) {
    return component.getNumberOfmoles() * component.getMolarMass();
  }
}
