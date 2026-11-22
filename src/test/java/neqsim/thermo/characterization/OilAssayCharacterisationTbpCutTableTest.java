package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.system.SystemSrkEos;

/** Tests auditable round-trip export of refinery-assay TBP cut tables. */
class OilAssayCharacterisationTbpCutTableTest {
  private static final double[] D86_C = {36.5, 54.1, 76.9, 101.5, 131.0, 171.0, 186.5};
  private static final double[] SPECIFIC_GRAVITY = {0.70, 0.73, 0.76, 0.79, 0.82, 0.85, 0.88};

  @Test
  void exportsMassBasisAndRoundTripsThroughTbpBoundaries() {
    OilAssayCharacterisation source = assay();
    source.addCut(massCut("light", 0.40, 0.80, 300.0, 400.0));
    source.addCut(massCut("heavy", 0.60, 0.90, 400.0, 500.0));

    TbpCutTable table = source.exportTbpCutTable();

    assertEquals(2, table.getCutCount());
    assertArrayEquals(new double[] {0.0, 300.0 / 7.0, 100.0}, table.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(new double[] {300.0, 400.0, 500.0}, table.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(new double[] {26.85, 126.85, 226.85}, table.getBoilingPointCelsius(), 1.0e-12);
    assertArrayEquals(new double[] {0.80, 0.90}, table.getSpecificGravity(), 0.0);
    assertArrayEquals(new double[] {3.0 / 7.0, 4.0 / 7.0}, source.getResolvedVolumeFractions(), 1.0e-12);

    double[] mutableCopy = table.getCumulativeVolumePercent();
    mutableCopy[1] = 99.0;
    assertEquals(300.0 / 7.0, table.getCumulativeVolumePercent()[1], 1.0e-12);

    OilAssayCharacterisation roundTrip = assay();
    roundTrip.addTBPCutBoundariesKelvin("RoundTrip", table.getCumulativeVolumePercent(), table.getBoilingPointKelvin(),
        table.getSpecificGravity());

    assertArrayEquals(source.getResolvedMassFractions(), roundTrip.getResolvedMassFractions(), 1.0e-12);
    assertArrayEquals(source.getResolvedVolumeFractions(), roundTrip.getResolvedVolumeFractions(), 1.0e-12);
    assertArrayEquals(table.getBoilingPointKelvin(), roundTrip.exportTbpCutTable().getBoilingPointKelvin(), 0.0);
  }

  @Test
  void preservesQualifiedD86CumulativeYields() {
    OilAssayCharacterisation assay = assay();
    assay.addD86ReferencePointCutBoundariesCelsius("D86", D86_C, 225.0, SPECIFIC_GRAVITY);

    TbpCutTable table = assay.exportTbpCutTable();

    assertArrayEquals(new double[] {0.0, 10.0, 30.0, 50.0, 70.0, 90.0, 95.0, 100.0}, table.getCumulativeVolumePercent(),
        1.0e-12);
    assertArrayEquals(SPECIFIC_GRAVITY, table.getSpecificGravity(), 0.0);
    assertEquals(8, table.getBoilingPointCelsius().length);
    assertEquals(225.0, table.getBoilingPointCelsius()[7], 1.0e-12);
    assertEquals(7, assay.getCuts().size());
  }

  @Test
  void rejectsIncompleteOrNonContiguousTables() {
    OilAssayCharacterisation empty = assay();
    assertThrows(IllegalStateException.class, empty::exportTbpCutTable);

    OilAssayCharacterisation missingDensity = assay();
    missingDensity.addCut(new AssayCut("missingDensity").withMassFraction(1.0).withBoilingRangeKelvin(300.0, 400.0));
    assertThrows(IllegalStateException.class, missingDensity::exportTbpCutTable);

    OilAssayCharacterisation missingRange = assay();
    missingRange.addCut(new AssayCut("missingRange").withMassFraction(1.0).withSpecificGravity(0.80)
        .withAverageBoilingPointKelvin(350.0));
    assertThrows(IllegalStateException.class, missingRange::exportTbpCutTable);

    OilAssayCharacterisation gap = assay();
    gap.addCut(massCut("gap1", 0.5, 0.80, 300.0, 400.0));
    gap.addCut(massCut("gap2", 0.5, 0.90, 401.0, 500.0));
    assertThrows(IllegalStateException.class, gap::exportTbpCutTable);

    OilAssayCharacterisation overlap = assay();
    overlap.addCut(massCut("overlap1", 0.5, 0.80, 300.0, 400.0));
    overlap.addCut(massCut("overlap2", 0.5, 0.90, 399.0, 500.0));
    assertThrows(IllegalStateException.class, overlap::exportTbpCutTable);

    OilAssayCharacterisation zeroYield = assay();
    zeroYield.addCut(massCut("positive", 1.0, 0.80, 300.0, 400.0));
    zeroYield.addCut(massCut("zero", 0.0, 0.90, 400.0, 500.0));
    assertThrows(IllegalStateException.class, zeroYield::exportTbpCutTable);
  }

  private static OilAssayCharacterisation assay() {
    return new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
  }

  private static AssayCut massCut(String name, double massFraction, double specificGravity, double lowerKelvin,
      double upperKelvin) {
    return new AssayCut(name).withMassFraction(massFraction).withSpecificGravity(specificGravity)
        .withBoilingRangeKelvin(lowerKelvin, upperKelvin);
  }
}
