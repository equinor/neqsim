package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests fail-closed whole-assay bulk property blending. */
public class RefineryAssayBlendTest {
  @Test
  public void publicOediEndpointsFollowIdealAdditiveVolumeRule() {
    double[] masses = { 60.0, 40.0 };
    double[] publishedSpecificGravities = { 0.847, 0.771 };
    RefineryAssayBlend blend = RefineryAssayBlend.fromBulkProperties(masses, publishedSpecificGravities);

    double expectedSpecificGravity = 1.0 / (0.6 / 0.847 + 0.4 / 0.771);
    assertEquals(expectedSpecificGravity, blend.getSpecificGravity(), 1.0e-15);
    assertEquals(141.5 / expectedSpecificGravity - 131.5, blend.getApiGravity(), 1.0e-13);
    assertEquals(expectedSpecificGravity * 999.016, blend.getDensityKgPerCubicMetreAt60F(), 1.0e-12);
    assertFalse(blend.hasAssayQualities());
    assertThrows(IllegalStateException.class, blend::getSulfurMassFraction);
  }

  @Test
  public void assayFactoryResolvesCompletePropertiesWithoutMutation() {
    SystemInterface firstSystem = new SystemSrkEos(298.15, 1.01325);
    SystemInterface secondSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation first = singleCutAssay(firstSystem, "First", 0.80, 0.01, 0.001);
    OilAssayCharacterisation second = singleCutAssay(secondSystem, "Second", 0.90, 0.03, 0.002);

    RefineryAssayBlend blend = RefineryAssayBlend.fromAssays(new OilAssayCharacterisation[] { first, second },
        new double[] { 2.0, 3.0 });

    double expectedSpecificGravity = 1.0 / (0.4 / 0.80 + 0.6 / 0.90);
    assertArrayEquals(new double[] { 0.4, 0.6 }, blend.getMassFractions(), 1.0e-15);
    assertEquals(expectedSpecificGravity, blend.getSpecificGravity(), 1.0e-15);
    assertEquals(0.022, blend.getSulfurMassFraction(), 1.0e-15);
    assertEquals(2.2, blend.getSulfurMassPercent(), 1.0e-14);
    assertEquals(0.0016, blend.getNitrogenMassFraction(), 1.0e-15);
    assertEquals(0.16, blend.getNitrogenMassPercent(), 1.0e-14);
    assertTrue(blend.hasAssayQualities());
    assertEquals(0, firstSystem.getNumberOfComponents());
    assertEquals(0, secondSystem.getNumberOfComponents());
  }

  @Test
  public void resultIsScaleOrderAndDefensiveCopyInvariant() {
    RefineryAssayBlend base = RefineryAssayBlend.fromBulkProperties(new double[] { 2.0, 3.0 },
        new double[] { 0.80, 0.90 }, new double[] { 0.01, 0.03 }, new double[] { 0.001, 0.002 });
    RefineryAssayBlend scaled = RefineryAssayBlend.fromBulkProperties(new double[] { 20.0, 30.0 },
        new double[] { 0.80, 0.90 }, new double[] { 0.01, 0.03 }, new double[] { 0.001, 0.002 });
    RefineryAssayBlend reversed = RefineryAssayBlend.fromBulkProperties(new double[] { 3.0, 2.0 },
        new double[] { 0.90, 0.80 }, new double[] { 0.03, 0.01 }, new double[] { 0.002, 0.001 });

    assertEquals(base.getSpecificGravity(), scaled.getSpecificGravity(), 0.0);
    assertEquals(base.getSpecificGravity(), reversed.getSpecificGravity(), 0.0);
    assertEquals(base.getSulfurMassFraction(), reversed.getSulfurMassFraction(), 0.0);
    assertEquals(base.getNitrogenMassFraction(), reversed.getNitrogenMassFraction(), 0.0);

    double[] returnedMassFractions = base.getMassFractions();
    returnedMassFractions[0] = 1.0;
    assertArrayEquals(new double[] { 0.4, 0.6 }, base.getMassFractions(), 1.0e-15);
  }

  @Test
  public void zeroMassSourceDoesNotRequireFabricatedProperties() {
    RefineryAssayBlend blend = RefineryAssayBlend.fromBulkProperties(new double[] { 1.0, 0.0 },
        new double[] { 0.82, Double.NaN }, new double[] { 0.004, Double.NaN }, new double[] { 0.001, Double.NaN });

    assertArrayEquals(new double[] { 1.0, 0.0 }, blend.getMassFractions(), 0.0);
    assertEquals(0.82, blend.getSpecificGravity(), 0.0);
    assertEquals(0.004, blend.getSulfurMassFraction(), 0.0);
    assertEquals(0.001, blend.getNitrogenMassFraction(), 0.0);
  }

  @Test
  public void invalidOrIncompleteInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromBulkProperties(null, new double[] { 0.8 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromBulkProperties(new double[] {}, new double[] {}));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromBulkProperties(new double[] { 1.0 }, new double[] { 0.8, 0.9 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromBulkProperties(new double[] { -1.0 }, new double[] { 0.8 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromBulkProperties(new double[] { 0.0 }, new double[] { 0.8 }));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromBulkProperties(new double[] { 1.0 }, new double[] { Double.NaN }));
    assertThrows(IllegalArgumentException.class, () -> RefineryAssayBlend.fromBulkProperties(new double[] { 1.0 },
        new double[] { 0.8 }, new double[] { 1.01 }, new double[] { 0.001 }));
    assertThrows(IllegalArgumentException.class, () -> RefineryAssayBlend.fromBulkProperties(new double[] { 1.0 },
        new double[] { 0.8 }, new double[] { 0.01 }, null));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryAssayBlend.fromAssays(new OilAssayCharacterisation[] { null }, new double[] { 1.0 }));
  }

  private static OilAssayCharacterisation singleCutAssay(SystemInterface system, String name, double specificGravity,
      double sulfurMassFraction, double nitrogenMassFraction) {
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.addCut(new AssayCut(name).withMassFraction(1.0).withSpecificGravity(specificGravity)
        .withSulfurMassFraction(sulfurMassFraction).withNitrogenMassFraction(nitrogenMassFraction));
    return assay;
  }
}
