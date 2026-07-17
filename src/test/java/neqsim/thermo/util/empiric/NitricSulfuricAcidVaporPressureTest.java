package neqsim.thermo.util.empiric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure},
 * the
 * Taleb-Ponche-Mirabel (1996) Van Laar model for the H2O-HNO3-H2SO4 system.
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class NitricSulfuricAcidVaporPressureTest {

  /**
   * Pure water vapour pressure at 0 degC must reproduce the well-known physical
   * value of about 611
   * Pa (6.11 mbar), giving an independent anchor for the correlation.
   */
  @Test
  public void testPureWaterVaporPressure() {
    double p = NitricSulfuricAcidVaporPressure.pureVaporPressureWater(273.15);
    assertEquals(610.4, p, 3.0);
    // Known physical value of saturated water vapour at 0 degC is ~611 Pa.
    assertEquals(611.0, p, 5.0);
  }

  /**
   * Pure nitric acid vapour pressure at 273.15 K.
   *
   * <p>
   * With the updated Antoine parameters (A = 7.57628, B = 1470.385, C = 43 K)
   * the value is 15.40 torr = 2053 Pa. The parameters were fitted to reproduce
   * the experimental normal boiling point of HNO3 at 83 degC exactly and to
   * reduce the systematic 7 % under-prediction of Vandoni (1944) ternary peak
   * data at 273 K.
   * </p>
   */
  @Test
  public void testPureNitricAcidVaporPressure() {
    double p = NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(273.15);
    assertEquals(2053.0, p, 10.0);
  }

  /**
   * Pure sulfuric acid is essentially nonvolatile: its vapour pressure at 273.15
   * K is of the order
   * of 1e-4 Pa.
   */
  @Test
  public void testPureSulfuricAcidVaporPressure() {
    double p = NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(273.15);
    assertEquals(8.31e-5, p, 0.2e-5);
    assertTrue(p > 0.0, "sulfuric acid vapour pressure must be positive");
  }

  /**
   * In the binary water-sulfuric acid limit (x_HNO3 = 0) the ternary equation
   * (10a) must reduce to
   * the System II Van Laar form. The hand-computed water activity coefficient at
   * x_H2SO4 = 0.5 and
   * 298 K is 0.005122.
   */
  @Test
  public void testWaterActivityCoefficientBinarySulfuricLimit() {
    double gamma = NitricSulfuricAcidVaporPressure.activityCoefficientWater(0.5, 0.0, 0.5, 298.0);
    assertEquals(0.0051221, gamma, 1.0e-5);
  }

  /**
   * In the binary water-nitric acid limit (x_H2SO4 = 0) the ternary equation
   * (10a) must reduce to
   * the System I Van Laar form. The hand-computed water activity coefficient at
   * x_HNO3 = 0.25 and
   * 250 K is 0.42096.
   */
  @Test
  public void testWaterActivityCoefficientBinaryNitricLimit() {
    double gamma = NitricSulfuricAcidVaporPressure.activityCoefficientWater(0.75, 0.25, 0.0, 250.0);
    assertEquals(0.42096, gamma, 1.0e-4);
  }

  /**
   * As the mixture approaches pure water (x1 -&gt; 1) the water activity
   * coefficient must approach
   * unity, since the Van Laar numerator vanishes.
   */
  @Test
  public void testWaterActivityCoefficientDiluteAcidLimit() {
    double gamma = NitricSulfuricAcidVaporPressure.activityCoefficientWater(0.9999, 0.00005, 0.00005, 273.15);
    assertEquals(1.0, gamma, 1.0e-2);
  }

  /**
   * All three partial pressures in a representative ternary mixture must be
   * finite and positive, and
   * water must dominate the vapour phase while sulfuric acid is essentially
   * absent.
   */
  @Test
  public void testTernaryPartialPressuresArePhysical() {
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(60.0, 20.0, 20.0);
    double pWater = NitricSulfuricAcidVaporPressure.partialPressureWater(x[0], x[1], x[2], 273.15);
    double pNitric = NitricSulfuricAcidVaporPressure.partialPressureNitricAcid(x[0], x[1], x[2], 273.15);
    double pSulfuric = NitricSulfuricAcidVaporPressure.partialPressureSulfuricAcid(x[0], x[1], x[2], 273.15);
    assertTrue(pWater > 0.0 && !Double.isNaN(pWater), "water partial pressure must be positive");
    assertTrue(pNitric > 0.0 && !Double.isNaN(pNitric), "nitric partial pressure must be positive");
    assertTrue(pSulfuric >= 0.0 && !Double.isNaN(pSulfuric),
        "sulfuric partial pressure must be non-negative");
    assertTrue(pWater > pNitric, "water should dominate the vapour phase");
    assertTrue(pSulfuric < pNitric, "sulfuric acid is far less volatile than nitric acid");
  }

  /**
   * The composition helper converts a 50/0/50 weight-percent water/sulfuric
   * mixture to the expected
   * mole fractions.
   */
  @Test
  public void testMoleFractionsFromMassFractions() {
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(50.0, 0.0, 50.0);
    assertEquals(0.84482, x[0], 1.0e-4);
    assertEquals(0.0, x[1], 1.0e-12);
    assertEquals(0.15518, x[2], 1.0e-4);
    assertEquals(1.0, x[0] + x[1] + x[2], 1.0e-12);
  }

  /**
   * Consistency check: the ternary water activity coefficient evaluated with a
   * vanishingly small
   * sulfuric acid fraction must match the pure binary water-nitric acid value
   * (continuity of the
   * model across the binary boundary).
   */
  @Test
  public void testTernaryReducesContinuouslyToBinary() {
    double binary = NitricSulfuricAcidVaporPressure.activityCoefficientWater(0.70, 0.30, 0.0, 273.15);
    double nearBinary = NitricSulfuricAcidVaporPressure.activityCoefficientWater(0.70, 0.30 - 1.0e-6, 1.0e-6,
        273.15);
    assertEquals(binary, nearBinary, 1.0e-3);
  }

  /**
   * Reproduces the headline result of the paper (Figure 7, panel c): at a fixed
   * total HNO3 content
   * of 21.90 wt %, the nitric acid partial pressure over the H2O/HNO3/H2SO4
   * mixture at 273 K passes
   * through a maximum as sulfuric acid is added (H2SO4 first concentrates HNO3 by
   * displacing water,
   * then dilutes it). The Vandoni (1944) experimental peak is 5.2 torr near
   * 70 wt % H2SO4. With the updated Antoine parameters the model predicts
   * approximately 4.9 torr (closer to the Vandoni data than the original 4.6
   * torr).
   * The composition uses the total-mixture basis:
   * w_HNO3 is held fixed
   * and w_H2SO4 is swept, with water making up the balance.
   */
  @Test
  public void testNitricPartialPressureTernaryMaximum() {
    final double torrToPa = 133.322368421;
    final double wNitric = 21.90;
    double bestTorr = 0.0;
    double bestSulfuric = 0.0;
    // Sweep sulfuric acid wt % up to the water-free limit (100 - w_HNO3).
    for (int i = 0; i <= 1000; i++) {
      double wSulfuric = (100.0 - wNitric) * i / 1000.0;
      double wWater = 100.0 - wNitric - wSulfuric;
      double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(wWater, wNitric,
          wSulfuric);
      double pNitric = NitricSulfuricAcidVaporPressure.partialPressureNitricAcid(x[0], x[1], x[2], 273.15);
      double pTorr = pNitric / torrToPa;
      if (pTorr > bestTorr) {
        bestTorr = pTorr;
        bestSulfuric = wSulfuric;
      }
    }
    // Peak magnitude closer to Vandoni (1944) data (experimental ~5.2 torr).
    assertEquals(4.90, bestTorr, 0.20);
    // Peak location matches the paper (about 70 wt % H2SO4).
    assertEquals(70.0, bestSulfuric, 3.0);
  }
}
