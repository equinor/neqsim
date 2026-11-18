package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Numerical checks for the Rev. 2 bare-steel correlation and its legacy boundary. */
class NorsokM506Rev2Test {
  @Test
  void referenceCaseDistinguishesSpecifiedFugacityFromCalculatedFugacity() {
    NorsokM506CorrosionRate legacy = new NorsokM506CorrosionRate(60.0, 40.0, 0.01);
    legacy.setActualPH(4.0);
    legacy.setUseFlowCorrection(false);
    legacy.setUseScaleCorrection(false);
    legacy.calculate();
    assertEquals(3.138823, legacy.getCorrectedCorrosionRate(), 1.0e-6);
    assertEquals(NorsokM506CorrosionRate.LEGACY_RATE_MODEL,
        ((Map<?, ?>) legacy.toMap().get("modelInfo")).get("rateModel"));
    assertEquals(0.36138285158428934, NorsokM506Rev2.fugacityBar(60.0, 40.0, 0.01), 1.0e-14);
    assertEquals(6.309193994731726, legacy.calculateRev2UnmitigatedRate(4.0, 19.0), 1.0e-12);
    assertEquals(6.537947513998961, NorsokM506Rev2.rateMmPerYear(60.0, 0.38275, 4.0, 19.0), 1.0e-12);
    assertEquals(3.138823, legacy.getCorrectedCorrosionRate(), 1.0e-6);
  }

  @Test
  void rateUsesThePHCurveAtEachTabulatedTemperature() {
    double[] temperatures = {5, 15, 20, 40, 60, 80, 90, 120, 150};
    double[] expected = {0.295344, 0.917748, 2.5526701, 4.78531835, 4.0484585625, 3.1954971949482, 0.8194121022589,
        1.199590875, 0.192511};
    for (int knot = 0; knot < temperatures.length; knot++) {
      assertEquals(expected[knot], NorsokM506Rev2.rateMmPerYear(temperatures[knot], 1.0, 5.5, 19.0), 1.0e-11);
    }
  }

  @Test
  void lowTemperatureRatesUseSpecialExponentsAndInterpolateCompletedRates() {
    double fugacity = 0.4;
    double shear = 5.0;
    double phFactor = 2.0676 - 0.2309 * 4.0;
    double shearFactor = Math.pow(shear / 19.0, 0.146 + 0.0324 * Math.log10(fugacity));
    double five = 0.42 * Math.pow(fugacity, 0.36) * phFactor;
    double fifteen = 1.59 * Math.pow(fugacity, 0.36) * phFactor * shearFactor;
    double twenty = 4.762 * Math.pow(fugacity, 0.62) * phFactor * shearFactor;
    assertEquals(five, NorsokM506Rev2.rateMmPerYear(5, fugacity, 4, 150), 1.0e-12);
    assertEquals((five + fifteen) / 2, NorsokM506Rev2.rateMmPerYear(10, fugacity, 4, shear), 1.0e-12);
    assertEquals((fifteen + twenty) / 2, NorsokM506Rev2.rateMmPerYear(17.5, fugacity, 4, shear), 1.0e-12);
  }

  @Test
  void fugacityCapsOnlyThePressureUsedForTheCoefficient() {
    double at250 = NorsokM506Rev2.fugacityBar(50.0, 250.0, 0.01);
    assertEquals(2 * at250, NorsokM506Rev2.fugacityBar(50.0, 500.0, 0.01), 1.0e-14);
  }

  @Test
  void refusesInputsOutsideTheReferenceEnvelope() {
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.fugacityBar(60, 0, 0.01));
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.fugacityBar(60, 100, 1.1));
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.rateMmPerYear(151, 1, 4, 19));
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.rateMmPerYear(60, 0.01, 4, 19));
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.rateMmPerYear(60, 1, 6.6, 19));
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.rateMmPerYear(60, 1, Double.NaN, 19));
    assertThrows(IllegalArgumentException.class,
        () -> NorsokM506Rev2.rateMmPerYear(60, 1, 4, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> NorsokM506Rev2.rateMmPerYear(60, 1, 4, 0));
  }
}
