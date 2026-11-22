package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests conservative adjacent-cut re-lumping of immutable refinery TBP cut tables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilAssayCharacterisationTbpCutTableRelumpingTest {
  private static final double[] D86_C = {36.5, 54.1, 76.9, 101.5, 131.0, 171.0, 186.5};
  private static final double[] D86_SPECIFIC_GRAVITY = {0.70, 0.73, 0.76, 0.79, 0.82, 0.85, 0.88};

  /** Tests retained boundaries plus exact ideal-volume and implied-mass closure. */
  @Test
  void relumpsAdjacentCutsAndPreservesVolumeAndMass() {
    OilAssayCharacterisation source = assay();
    source.addCut(massCut("cut1", 0.07 / 0.90, 0.70, 300.0, 400.0));
    source.addCut(massCut("cut2", 0.16 / 0.90, 0.80, 400.0, 500.0));
    source.addCut(massCut("cut3", 0.27 / 0.90, 0.90, 500.0, 600.0));
    source.addCut(massCut("cut4", 0.40 / 0.90, 1.00, 600.0, 700.0));

    TbpCutTable original = source.exportTbpCutTable();
    TbpCutTable relumped = original.relumpAdjacentCuts(2, 2);

    assertEquals(2, relumped.getCutCount());
    assertArrayEquals(new double[] {0.0, 30.0, 100.0}, relumped.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(new double[] {300.0, 500.0, 700.0}, relumped.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(new double[] {23.0 / 30.0, 67.0 / 70.0}, relumped.getSpecificGravity(), 1.0e-12);

    OilAssayCharacterisation roundTrip = assay();
    roundTrip.addTBPCutBoundariesKelvin("Relumped", relumped.getCumulativeVolumePercent(),
        relumped.getBoilingPointKelvin(), relumped.getSpecificGravity());

    assertEquals(1.0, sum(roundTrip.getResolvedVolumeFractions()), 1.0e-12);
    assertEquals(1.0, sum(roundTrip.getResolvedMassFractions()), 1.0e-12);
    assertEquals(source.getBulkSpecificGravity(), roundTrip.getBulkSpecificGravity(), 1.0e-12);

    TbpCutTable identity = original.relumpAdjacentCuts(1, 1, 1, 1);
    assertArrayEquals(original.getCumulativeVolumePercent(), identity.getCumulativeVolumePercent(), 0.0);
    assertArrayEquals(original.getBoilingPointKelvin(), identity.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(original.getSpecificGravity(), identity.getSpecificGravity(), 0.0);

    TbpCutTable collapsed = original.relumpAdjacentCuts(4);
    assertArrayEquals(new double[] {0.0, 100.0}, collapsed.getCumulativeVolumePercent(), 0.0);
    assertArrayEquals(new double[] {300.0, 700.0}, collapsed.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(new double[] {0.90}, collapsed.getSpecificGravity(), 1.0e-12);

    double[] mutableCopy = relumped.getSpecificGravity();
    mutableCopy[0] = 9.0;
    assertEquals(23.0 / 30.0, relumped.getSpecificGravity()[0], 1.0e-12);
  }

  /** Tests the published D86 recovery grid after adjacent whole-cut grouping. */
  @Test
  void relumpsQualifiedD86GridWithoutInterpolation() {
    OilAssayCharacterisation source = assay();
    source.addD86ReferencePointCutBoundariesCelsius("D86", D86_C, 225.0, D86_SPECIFIC_GRAVITY);
    TbpCutTable original = source.exportTbpCutTable();

    TbpCutTable relumped = original.relumpAdjacentCuts(1, 2, 2, 2);

    assertArrayEquals(new double[] {0.0, 10.0, 50.0, 90.0, 100.0}, relumped.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(new double[] {original.getBoilingPointKelvin()[0], original.getBoilingPointKelvin()[1],
        original.getBoilingPointKelvin()[3], original.getBoilingPointKelvin()[5], original.getBoilingPointKelvin()[7]},
        relumped.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(new double[] {0.70, 0.745, 0.805, 0.865}, relumped.getSpecificGravity(), 1.0e-12);
  }

  /** Tests fail-closed rejection of incomplete, excessive, or non-positive partitions. */
  @Test
  void rejectsInvalidAdjacentCutPartitions() {
    OilAssayCharacterisation source = assay();
    source.addCut(volumeCut("cut1", 0.25, 0.75, 300.0, 400.0));
    source.addCut(volumeCut("cut2", 0.25, 0.80, 400.0, 500.0));
    source.addCut(volumeCut("cut3", 0.25, 0.85, 500.0, 600.0));
    source.addCut(volumeCut("cut4", 0.25, 0.90, 600.0, 700.0));
    TbpCutTable table = source.exportTbpCutTable();

    assertThrows(IllegalArgumentException.class, () -> table.relumpAdjacentCuts());
    assertThrows(IllegalArgumentException.class, () -> table.relumpAdjacentCuts((int[]) null));
    assertThrows(IllegalArgumentException.class, () -> table.relumpAdjacentCuts(1, 0, 3));
    assertThrows(IllegalArgumentException.class, () -> table.relumpAdjacentCuts(-1, 5));
    assertThrows(IllegalArgumentException.class, () -> table.relumpAdjacentCuts(2, 1));
    assertThrows(IllegalArgumentException.class, () -> table.relumpAdjacentCuts(4, 1));
  }

  /**
   * Create an empty assay for one regression case.
   *
   * @return empty refinery assay bound to an SRK system
   */
  private static OilAssayCharacterisation assay() {
    return new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
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
   * Create one volume-basis assay cut.
   *
   * @param name cut name
   * @param volumeFraction liquid-volume fraction
   * @param specificGravity dimensionless specific gravity
   * @param lowerKelvin lower TBP boundary in K
   * @param upperKelvin upper TBP boundary in K
   * @return configured assay cut
   */
  private static AssayCut volumeCut(String name, double volumeFraction, double specificGravity, double lowerKelvin,
      double upperKelvin) {
    return new AssayCut(name).withVolumeFraction(volumeFraction).withSpecificGravity(specificGravity)
        .withBoilingRangeKelvin(lowerKelvin, upperKelvin);
  }

  /**
   * Sum one normalized fraction array.
   *
   * @param values fractions to sum
   * @return scalar sum
   */
  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
