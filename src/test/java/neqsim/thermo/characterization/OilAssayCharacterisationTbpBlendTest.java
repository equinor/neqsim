package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.TbpCutTable;
import neqsim.thermo.system.SystemSrkEos;

/** Tests mass-basis blending of complete refinery TBP cut tables. */
class OilAssayCharacterisationTbpBlendTest {
  @Test
  void blendsOffsetSourceCurvesOnUnionGridWithMassAndVolumeClosure() {
    TbpCutTable light = sourceTable("Light", new double[] {0.0, 50.0, 100.0}, new double[] {300.0, 400.0, 500.0},
        new double[] {0.80, 0.80});
    TbpCutTable heavy = sourceTable("Heavy", new double[] {0.0, 50.0, 100.0}, new double[] {350.0, 450.0, 550.0},
        new double[] {1.00, 1.00});

    TbpCutTable blend = TbpCutTable.blendByMass(new TbpCutTable[] {light, heavy}, new double[] {50.0, 50.0});

    assertArrayEquals(new double[] {300.0, 350.0, 400.0, 450.0, 500.0, 550.0}, blend.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(new double[] {0.0, 125.0 / 9.0, 350.0 / 9.0, 575.0 / 9.0, 800.0 / 9.0, 100.0},
        blend.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(new double[] {0.80, 8.0 / 9.0, 8.0 / 9.0, 8.0 / 9.0, 1.00}, blend.getSpecificGravity(), 1.0e-12);
    assertEquals(8.0 / 9.0, blend.getTotalSpecificGravityWeightedLiquidVolumePercent() / 100.0, 1.0e-12);
    assertEquals(62.5, blend.getCumulativeMassPercentAtBoilingPointKelvin(450.0), 1.0e-12);

    assertArrayEquals(new double[] {0.0, 50.0, 100.0}, light.getCumulativeVolumePercent(), 0.0);
    assertArrayEquals(new double[] {0.0, 50.0, 100.0}, heavy.getCumulativeVolumePercent(), 0.0);
  }

  @Test
  void preservesIdentityForEqualTablesAndScaledMasses() {
    TbpCutTable source = sourceTable("Source", new double[] {0.0, 20.0, 100.0}, new double[] {300.0, 400.0, 700.0},
        new double[] {0.75, 0.95});

    TbpCutTable blend = TbpCutTable.blendByMass(new TbpCutTable[] {source, source}, new double[] {2.0, 8.0});

    assertArrayEquals(source.getBoilingPointKelvin(), blend.getBoilingPointKelvin(), 0.0);
    assertArrayEquals(source.getCumulativeVolumePercent(), blend.getCumulativeVolumePercent(), 1.0e-12);
    assertArrayEquals(source.getSpecificGravity(), blend.getSpecificGravity(), 1.0e-12);
  }

  @Test
  void rejectsIncompleteBlendContracts() {
    TbpCutTable source = sourceTable("Source", new double[] {0.0, 100.0}, new double[] {300.0, 500.0},
        new double[] {0.85});

    assertThrows(IllegalArgumentException.class, () -> TbpCutTable.blendByMass(null, new double[] {1.0, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> TbpCutTable.blendByMass(new TbpCutTable[] {source}, new double[] {1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> TbpCutTable.blendByMass(new TbpCutTable[] {source, source}, new double[] {1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> TbpCutTable.blendByMass(new TbpCutTable[] {source, null}, new double[] {1.0, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> TbpCutTable.blendByMass(new TbpCutTable[] {source, source}, new double[] {1.0, 0.0}));
    assertThrows(IllegalArgumentException.class,
        () -> TbpCutTable.blendByMass(new TbpCutTable[] {source, source}, new double[] {1.0, Double.NaN}));
  }

  private static TbpCutTable sourceTable(String prefix, double[] cumulativeVolumePercent, double[] boilingPointKelvin,
      double[] specificGravity) {
    OilAssayCharacterisation assay = new SystemSrkEos(298.15, 1.01325).getOilAssayCharacterisation();
    assay.addTBPCutBoundariesKelvin(prefix, cumulativeVolumePercent, boilingPointKelvin, specificGravity);
    return assay.exportTbpCutTable();
  }
}
