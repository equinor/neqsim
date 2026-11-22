package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for ordinary TP flash recovery of a dense ionic two-phase state.
 *
 * @author OpenAI
 * @version 1.0
 */
class TPflashDenseNaClRootRecoveryTest {
  /**
   * Preserves the measured 22 wt% NaCl state where the ordinary flash previously collapsed to a single-phase endpoint
   * while the independently constrained dual-root calculation closed.
   */
  @Test
  void ordinaryFlashRecoversDenseNaClOilAqueousSplit() {
    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference = new KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference(
        "MCB-NACL22-323-P572", "A10", 22.0, 0.0, 0.0, 323.15, 57.241, 1.18, 0.01);

    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result ordinary = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .calculate(reference, 10.0);
    KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result constrained = KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest
        .calculate(reference, 10.0, true);

    assertEquals("OIL+AQUEOUS", ordinary.phaseTypes);
    assertEquals(constrained.molecularMolePercent, ordinary.molecularMolePercent, 1.0e-8);
    assertTrue(ordinary.componentBalanceResidualMol < 1.0e-10);
    assertTrue(ordinary.molecularLogFugacityResidual < 1.0e-8);
  }
}
