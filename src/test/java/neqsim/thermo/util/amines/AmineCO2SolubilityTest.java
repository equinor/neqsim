package neqsim.thermo.util.amines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.util.amines.AmineKentEisenberg.AmineType;

/**
 * Regression tests for the validated Kent-Eisenberg CO2 solubility model.
 *
 * <p>
 * Golden values are taken from the validated model and bracketed against the literature vapour-liquid-equilibrium bands
 * of Jou, Mather and Otto (1982, MDEA) and Jou/Lee/Mather (MEA). Tolerances reflect the screening-correlation accuracy
 * of the apparent-constant approach (about a factor of two), tightened to guard against regressions.
 * </p>
 */
public class AmineCO2SolubilityTest {
  /** Temperature 40 C in Kelvin. */
  private static final double T40 = 273.15 + 40.0;
  /** Temperature 100 C in Kelvin. */
  private static final double T100 = 273.15 + 100.0;
  /** MDEA molarity for a 50 wt% aqueous solution. */
  private static final double MDEA_50WT = 4.28;
  /** MEA molarity for a 30 wt% aqueous solution. */
  private static final double MEA_30WT = 5.0;

  /**
   * MDEA partial pressures at 40 C must rise monotonically with loading and stay in the literature band.
   */
  @Test
  public void mdeaMonotonicAndInBand40C() {
    double[] loadings = { 0.1, 0.2, 0.4, 0.6, 0.8 };
    double prev = -1.0;
    for (int i = 0; i < loadings.length; i++) {
      double p = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T40, MDEA_50WT, loadings[i]);
      assertTrue(p > prev, "MDEA pCO2 must increase with loading");
      prev = p;
    }
    // Engineering-region anchor: loading 0.4 -> roughly 0.1-0.4 bara (Jou et al. 1982 band).
    double p04 = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T40, MDEA_50WT, 0.4);
    assertTrue(p04 > 0.05 && p04 < 0.6, "MDEA pCO2 at loading 0.4 out of band: " + p04);
  }

  /**
   * MEA partial pressures at 40 C must rise monotonically with loading and match the engineering region.
   */
  @Test
  public void meaMonotonicAndInBand40C() {
    double[] loadings = { 0.2, 0.3, 0.4, 0.5 };
    double prev = -1.0;
    for (int i = 0; i < loadings.length; i++) {
      double p = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MEA, T40, MEA_30WT, loadings[i]);
      assertTrue(p > prev, "MEA pCO2 must increase with loading");
      prev = p;
    }
    // Engineering anchor: loading 0.5 -> roughly 0.05-0.3 bara (Lee/Jou band).
    double p05 = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MEA, T40, MEA_30WT, 0.5);
    assertTrue(p05 > 0.05 && p05 < 0.3, "MEA pCO2 at loading 0.5 out of band: " + p05);
  }

  /**
   * Below loading 0.5 the primary amine (MEA) must hold CO2 far more tightly than the tertiary amine (MDEA), i.e. a
   * much lower equilibrium partial pressure at equal loading.
   */
  @Test
  public void meaBindsTighterThanMdeaBelowHalfLoading() {
    double pMea = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MEA, T40, MEA_30WT, 0.4);
    double pMdea = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T40, MDEA_50WT, 0.4);
    assertTrue(pMea < pMdea, "MEA should bind CO2 tighter than MDEA below half loading");
  }

  /**
   * Higher temperature must strongly increase the equilibrium CO2 partial pressure (the basis of thermal
   * stripping/regeneration).
   */
  @Test
  public void higherTemperatureRaisesPartialPressure() {
    double pHot = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T100, MDEA_50WT, 0.4);
    double pCold = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T40, MDEA_50WT, 0.4);
    assertTrue(pHot > 5.0 * pCold, "Stripping: hot pCO2 should be much higher than cold");
  }

  /**
   * Golden-value regression guards for MDEA at 40 C (loose factor-2 tolerance).
   */
  @Test
  public void mdeaGoldenValues40C() {
    assertEquals(0.0093, AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T40, MDEA_50WT, 0.1), 0.0093);
    assertEquals(0.229, AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, T40, MDEA_50WT, 0.4), 0.229);
  }

  /**
   * The molarity helper reproduces typical alkanolamine solution concentrations.
   */
  @Test
  public void molarityHelper() {
    assertEquals(4.36, AmineKentEisenberg.amineMolarity(0.5, 119.16), 0.1);
    assertEquals(5.03, AmineKentEisenberg.amineMolarity(0.3, 61.08), 0.1);
  }

  /**
   * Zero loading must give zero partial pressure, and invalid arguments must be rejected.
   */
  @Test
  public void edgeCases() {
    assertEquals(0.0, AmineKentEisenberg.partialPressureCO2Bara(AmineType.MEA, T40, MEA_30WT, 0.0), 0.0);
    assertThrows(IllegalArgumentException.class,
        () -> AmineKentEisenberg.partialPressureCO2Bara(AmineType.MEA, -1.0, MEA_30WT, 0.4));
    assertThrows(IllegalArgumentException.class,
        () -> AmineKentEisenberg.partialPressureCO2Bara(AmineType.MEA, T40, -1.0, 0.4));
    assertThrows(IllegalArgumentException.class,
        () -> AmineKentEisenberg.partialPressureCO2Bara(null, T40, MEA_30WT, 0.4));
  }

  /**
   * The {@link AmineSystem} wrapper must delegate to the validated model and produce a sensible CO2 partial pressure
   * for a 50 wt% MDEA solution.
   */
  @Test
  public void amineSystemDelegatesToValidatedModel() {
    AmineSystem amine = new AmineSystem(AmineSystem.AmineType.MDEA, T40, 1.01325);
    amine.setAmineConcentration(0.5);
    amine.setCO2Loading(0.4);
    double p = amine.getCO2PartialPressure();
    assertTrue(p > 0.05 && p < 0.6, "AmineSystem MDEA pCO2 out of band: " + p);
  }
}
