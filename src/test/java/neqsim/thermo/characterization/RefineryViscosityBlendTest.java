package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the fail-closed empirical refinery viscosity blend screen. */
public class RefineryViscosityBlendTest {
  @Test
  public void fixedBinaryArithmeticReferenceIsReproducible() {
    RefineryViscosityBlend blend = RefineryViscosityBlend.fromMassBasis(new double[] { 5000.0, 12000.0 },
        new double[] { 550.0, 375.0 }, 50.0);

    assertArrayEquals(new double[] { 5.0 / 17.0, 12.0 / 17.0 }, blend.getMassFractions(), 1.0e-15);
    assertEquals(50.0, blend.getTemperatureCelsius(), 0.0);
    assertEquals(37.110677920222024, blend.getViscosityBlendingNumber(), 1.0e-13);
    assertEquals(418.68738293612904, blend.getKinematicViscosityCSt(), 1.0e-12);

    double[] sourceBlendNumbers = blend.getSourceViscosityBlendingNumbers();
    assertEquals(RefineryViscosityBlend.calculateViscosityBlendingNumber(550.0), sourceBlendNumbers[0], 0.0);
    assertEquals(RefineryViscosityBlend.calculateViscosityBlendingNumber(375.0), sourceBlendNumbers[1], 0.0);
    assertEquals(blend.getKinematicViscosityCSt(),
        RefineryViscosityBlend.calculateKinematicViscosityCSt(blend.getViscosityBlendingNumber()), 0.0);
  }

  @Test
  public void resultIsScaleOrderPureSourceAndDefensiveCopyInvariant() {
    RefineryViscosityBlend base = RefineryViscosityBlend.fromMassBasis(new double[] { 2.0, 3.0 },
        new double[] { 10.0, 100.0 }, 40.0);
    RefineryViscosityBlend scaled = RefineryViscosityBlend.fromMassBasis(new double[] { 20.0, 30.0 },
        new double[] { 10.0, 100.0 }, 40.0);
    RefineryViscosityBlend reversed = RefineryViscosityBlend.fromMassBasis(new double[] { 3.0, 2.0 },
        new double[] { 100.0, 10.0 }, 40.0);
    RefineryViscosityBlend pure = RefineryViscosityBlend.fromMassBasis(new double[] { 7.0 }, new double[] { 42.0 },
        40.0);

    assertEquals(base.getViscosityBlendingNumber(), scaled.getViscosityBlendingNumber(), 0.0);
    assertEquals(base.getKinematicViscosityCSt(), scaled.getKinematicViscosityCSt(), 0.0);
    assertEquals(base.getKinematicViscosityCSt(), reversed.getKinematicViscosityCSt(), 1.0e-13);
    assertEquals(42.0, pure.getKinematicViscosityCSt(), 1.0e-13);
    assertTrue(base.getKinematicViscosityCSt() > 10.0);
    assertTrue(base.getKinematicViscosityCSt() < 100.0);

    double[] fractions = base.getMassFractions();
    double[] sourceBlendNumbers = base.getSourceViscosityBlendingNumbers();
    fractions[0] = 1.0;
    sourceBlendNumbers[0] = Double.NaN;
    assertArrayEquals(new double[] { 0.4, 0.6 }, base.getMassFractions(), 1.0e-15);
    assertTrue(Double.isFinite(base.getSourceViscosityBlendingNumbers()[0]));
  }

  @Test
  public void zeroMassSourceDoesNotRequireFabricatedViscosity() {
    RefineryViscosityBlend blend = RefineryViscosityBlend.fromMassBasis(new double[] { 1.0, 0.0 },
        new double[] { 25.0, Double.NaN }, 37.7);

    assertArrayEquals(new double[] { 1.0, 0.0 }, blend.getMassFractions(), 0.0);
    assertEquals(25.0, blend.getKinematicViscosityCSt(), 1.0e-13);
    assertTrue(Double.isNaN(blend.getSourceViscosityBlendingNumbers()[1]));
  }

  @Test
  public void publishedTransformAndInverseRoundTripAcrossDomain() {
    double[] viscositiesCSt = { 0.200001, 1.0, 10.0, 100.0, 10000.0 };
    for (double viscosityCSt : viscositiesCSt) {
      double blendNumber = RefineryViscosityBlend.calculateViscosityBlendingNumber(viscosityCSt);
      double roundTrip = RefineryViscosityBlend.calculateKinematicViscosityCSt(blendNumber);
      assertEquals(viscosityCSt, roundTrip, Math.max(1.0e-12, viscosityCSt * 1.0e-12));
    }
  }

  @Test
  public void binaryTargetPlannerRecoversMassFractionsAndTarget() {
    double targetViscosityCSt = 418.68738293612904;
    RefineryViscosityBlend planned = RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(550.0, 375.0,
        targetViscosityCSt, 50.0);
    RefineryViscosityBlend reversed = RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(375.0, 550.0,
        targetViscosityCSt, 50.0);

    assertArrayEquals(new double[] {5.0 / 17.0, 12.0 / 17.0}, planned.getMassFractions(), 1.0e-14);
    assertArrayEquals(new double[] {12.0 / 17.0, 5.0 / 17.0}, reversed.getMassFractions(), 1.0e-14);
    assertEquals(targetViscosityCSt, planned.getKinematicViscosityCSt(), 1.0e-12);
    assertEquals(targetViscosityCSt, reversed.getKinematicViscosityCSt(), 1.0e-12);
    assertEquals(50.0, planned.getTemperatureCelsius(), 0.0);

    RefineryViscosityBlend reconstructed = RefineryViscosityBlend.fromMassBasis(planned.getMassFractions(),
        new double[] {550.0, 375.0}, planned.getTemperatureCelsius());
    assertEquals(targetViscosityCSt, reconstructed.getKinematicViscosityCSt(), 1.0e-12);
  }

  @Test
  public void binaryTargetPlannerAcceptsExactEndpoints() {
    RefineryViscosityBlend first = RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 100.0, 10.0,
        40.0);
    RefineryViscosityBlend second = RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 100.0, 100.0,
        40.0);

    assertArrayEquals(new double[] {1.0, 0.0}, first.getMassFractions(), 0.0);
    assertArrayEquals(new double[] {0.0, 1.0}, second.getMassFractions(), 0.0);
    assertEquals(10.0, first.getKinematicViscosityCSt(), 1.0e-13);
    assertEquals(100.0, second.getKinematicViscosityCSt(), 1.0e-12);
  }

  @Test
  public void binaryTargetPlannerFailsClosedForUnsupportedInputs() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 10.0, 10.0, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 100.0, 5.0, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 100.0, 200.0, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(Double.NaN, 100.0, 50.0, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 100.0, 0.2, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromBinaryTargetKinematicViscosity(10.0, 100.0, 50.0, Double.NaN));
  }

  @Test
  public void invalidOrIncompleteInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(null, new double[] { 10.0 }, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] {}, new double[] {}, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] { 1.0 }, new double[] { 10.0, 20.0 }, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] { -1.0 }, new double[] { 10.0 }, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] { Double.NaN }, new double[] { 10.0 }, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] { 0.0 }, new double[] { 10.0 }, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] { 1.0 }, new double[] { 0.2 }, 40.0));
    assertThrows(IllegalArgumentException.class, () -> RefineryViscosityBlend.fromMassBasis(new double[] { 1.0 },
        new double[] { Double.POSITIVE_INFINITY }, 40.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.fromMassBasis(new double[] { 1.0 }, new double[] { 10.0 }, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> RefineryViscosityBlend.calculateViscosityBlendingNumber(0.2));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.calculateKinematicViscosityCSt(Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryViscosityBlend.calculateKinematicViscosityCSt(Double.MAX_VALUE));
  }
}
