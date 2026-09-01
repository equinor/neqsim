package neqsim.process.equipment.reactor.digestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.BioFeedstock;

/** Tests for pluggable anaerobic-digestion models and their conservation ledger. */
class AnaerobicDigestionModelTest {

  @Test
  void empiricalModelMatchesDocumentedYieldEquationAndClosesBalances() {
    BioFeedstock feedstock = BioFeedstock.library("food_residue");
    AnaerobicDigestionInput input = new AnaerobicDigestionInput(feedstock, 100.0, 20.0, 310.15, Double.NaN, Double.NaN,
        Double.NaN, 0.10);

    AnaerobicDigestionResult result = new EmpiricalYieldDigestionModel().calculate(input);

    double expectedMethane = 100.0 * 24.0 * 0.25 * 0.92 * 0.80 * 0.45;
    assertEquals(expectedMethane, result.getMethaneNm3PerDay(), 1.0e-10);
    assertEquals(1.0, result.getMassClosureFraction(), 1.0e-12);
    assertEquals(1.0, result.getCarbonClosureFraction(), 1.0e-12);
    assertTrue(result.getHydrogenSulfideNm3PerDay() > 0.0);
  }

  @Test
  void firstOrderModelRespondsToResidenceTimeAndRespectsMaximumConversion() {
    BioFeedstock feedstock = BioFeedstock.library("crop_residue");
    FirstOrderHydrolysisDigestionModel model = new FirstOrderHydrolysisDigestionModel();
    AnaerobicDigestionResult shortResidence = model.calculate(
        new AnaerobicDigestionInput(feedstock, 100.0, 5.0, 308.15, Double.NaN, Double.NaN, Double.NaN, 0.10));
    AnaerobicDigestionResult longResidence = model.calculate(
        new AnaerobicDigestionInput(feedstock, 100.0, 30.0, 308.15, Double.NaN, Double.NaN, Double.NaN, 0.10));

    double expectedLongConversion = feedstock.getMaximumVsDestruction()
        * (1.0 - Math.exp(-feedstock.getHydrolysisRatePerDay() * 30.0));
    assertTrue(longResidence.getMethaneNm3PerDay() > shortResidence.getMethaneNm3PerDay());
    assertEquals(expectedLongConversion, longResidence.getVsDestruction(), 1.0e-12);
    assertTrue(longResidence.getVsDestruction() <= feedstock.getMaximumVsDestruction());
    assertEquals(ModelFidelity.ENGINEERING, longResidence.getFidelity());
  }

  @Test
  void hydrogenSulfideScalesWithFeedSulfurInsteadOfMethaneProduction() {
    BioFeedstock feedstock = BioFeedstock.library("manure");
    EmpiricalYieldDigestionModel model = new EmpiricalYieldDigestionModel();
    AnaerobicDigestionResult noRelease = model.calculate(
        new AnaerobicDigestionInput(feedstock, 100.0, 20.0, 308.15, Double.NaN, Double.NaN, Double.NaN, 0.0));
    AnaerobicDigestionResult halfRelease = model.calculate(
        new AnaerobicDigestionInput(feedstock, 100.0, 20.0, 308.15, Double.NaN, Double.NaN, Double.NaN, 0.5));

    assertEquals(0.0, noRelease.getHydrogenSulfideNm3PerDay(), 1.0e-12);
    assertTrue(halfRelease.getHydrogenSulfideNm3PerDay() > 0.0);
    assertEquals(noRelease.getMethaneNm3PerDay(), halfRelease.getMethaneNm3PerDay(), 1.0e-12);
  }
}
